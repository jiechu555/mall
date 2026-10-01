package com.macro.mall.portal.seckill;

import com.macro.mall.mapper.SmsFlashPromotionMapper;
import com.macro.mall.mapper.SmsFlashPromotionOrderMapper;
import com.macro.mall.mapper.SmsFlashPromotionProductRelationMapper;
import com.macro.mall.mapper.SmsFlashPromotionSessionMapper;
import com.macro.mall.model.SmsFlashPromotion;
import com.macro.mall.model.SmsFlashPromotionOrder;
import com.macro.mall.model.SmsFlashPromotionOrderExample;
import com.macro.mall.model.SmsFlashPromotionProductRelation;
import com.macro.mall.model.SmsFlashPromotionProductRelationExample;
import com.macro.mall.model.SmsFlashPromotionSession;
import com.macro.mall.portal.seckill.domain.FlashSaleReconcileItem;
import com.macro.mall.portal.seckill.service.FlashSaleReconcileService;
import com.macro.mall.portal.seckill.service.FlashSaleStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 对账任务验收（06 讲 commit 6）：
 * ① 人为删一半 Redis 库存 → 已结束场次自动告警并回补；
 * ② 卡死超时的受理单（status=0 超过 10 分钟）→ 回补库存并关闭为 status=2。
 * 注意：mbg 的 generatedKey 使 insert 忽略预设的自增 id，一律用 insert 后回填的生成 id。
 */
@SpringBootTest
class FlashSaleReconcileTest {

    private static final Long SESSION_SEED = 1L;
    private static final Long PRODUCT = 27L;

    @Autowired
    private FlashSaleReconcileService reconcileService;
    @Autowired
    private FlashSaleStockService stockService;
    @Autowired
    private SmsFlashPromotionMapper promotionMapper;
    @Autowired
    private SmsFlashPromotionSessionMapper sessionMapper;
    @Autowired
    private SmsFlashPromotionProductRelationMapper relationMapper;
    @Autowired
    private SmsFlashPromotionOrderMapper acceptanceMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Long promotionId;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();

        SmsFlashPromotion promotion = new SmsFlashPromotion();
        promotion.setTitle("commit6-reconcile-check");
        promotion.setStatus(1);
        promotion.setStartDate(todayStart);
        promotion.setEndDate(new Date(todayStart.getTime() + 3 * 24 * 3600_000L));
        promotionMapper.insert(promotion);
        promotionId = promotion.getId();

        // 已结束场次：今天 00:10-00:30（当前时刻必然已过）
        SmsFlashPromotionSession session = new SmsFlashPromotionSession();
        session.setName("commit6-reconcile-session");
        session.setStartTime(new java.sql.Time(todayStart.getTime() + 10 * 60_000L));
        session.setEndTime(new java.sql.Time(todayStart.getTime() + 30 * 60_000L));
        session.setStatus(1);
        sessionMapper.insert(session);
        sessionId = session.getId();

        SmsFlashPromotionProductRelation relation = new SmsFlashPromotionProductRelation();
        relation.setFlashPromotionId(promotionId);
        relation.setFlashPromotionSessionId(sessionId);
        relation.setProductId(PRODUCT);
        relation.setFlashPromotionPrice(new BigDecimal("1999.00"));
        relation.setFlashPromotionCount(100);
        relation.setFlashPromotionLimit(2);
        relationMapper.insert(relation);

        stockService.warmUp(promotionId, sessionId);
    }

    @AfterEach
    void cleanUp() {
        SmsFlashPromotionOrderExample example = new SmsFlashPromotionOrderExample();
        example.createCriteria().andFlashPromotionIdEqualTo(promotionId);
        acceptanceMapper.deleteByExample(example);
        SmsFlashPromotionProductRelationExample relationExample = new SmsFlashPromotionProductRelationExample();
        relationExample.createCriteria().andFlashPromotionIdEqualTo(promotionId);
        relationMapper.deleteByExample(relationExample);
        sessionMapper.deleteByPrimaryKey(sessionId);
        promotionMapper.deleteByPrimaryKey(promotionId);
        redisTemplate.delete(List.of(stockKey(), boughtKey()));
    }

    @Test
    void 人为删一半库存_已结束场次告警并自动回补() {
        // 模拟 Redis 库存意外丢一半（如误删、故障）
        redisTemplate.opsForValue().set(stockKey(), 50);

        List<FlashSaleReconcileItem> report = reconcileService.reconcileOnce();
        FlashSaleReconcileItem item = findOurs(report);
        assertNotNull(item, "对账报告应包含我们的三元组");
        assertEquals(50L, item.getDifference(), "应检出 50 件差异");
        assertEquals(Boolean.TRUE, item.getSessionEnded(), "本场次应判定为已结束");
        assertEquals("REFUNDED", item.getAction(), "已结束场次应自动回补");
        assertEquals(100L, stockService.getRemainingStock(promotionId, sessionId, PRODUCT), "回补后余量应恢复 100");
    }

    @Test
    void 受理单卡死超时_回补库存并关闭() {
        // 场景：预扣成功、受理单已插（status=0），但消费端一直没落库（超过 10 分钟）
        redisTemplate.opsForValue().set(stockKey(), 99); // 与受理单一致的余量
        SmsFlashPromotionOrder stuck = new SmsFlashPromotionOrder();
        stuck.setFlashPromotionId(promotionId);
        stuck.setFlashPromotionSessionId(sessionId);
        stuck.setProductId(PRODUCT);
        stuck.setMemberId(13L);
        stuck.setQuantity(1);
        stuck.setStatus(0);
        stuck.setCreateTime(new Date(System.currentTimeMillis() - 20 * 60_000L));
        acceptanceMapper.insert(stuck);

        reconcileService.reconcileOnce();

        SmsFlashPromotionOrderExample example = new SmsFlashPromotionOrderExample();
        example.createCriteria().andFlashPromotionIdEqualTo(promotionId);
        List<SmsFlashPromotionOrder> rows = acceptanceMapper.selectByExample(example);
        assertEquals(1, rows.size());
        assertEquals(Integer.valueOf(2), rows.get(0).getStatus(), "卡死受理单应被关闭为 status=2");
        assertEquals(100L, stockService.getRemainingStock(promotionId, sessionId, PRODUCT),
                "关闭时应回补 1 件库存，余量恢复 100");
    }

    private FlashSaleReconcileItem findOurs(List<FlashSaleReconcileItem> report) {
        for (FlashSaleReconcileItem item : report) {
            if (promotionId.equals(item.getPromotionId()) && sessionId.equals(item.getSessionId())
                    && PRODUCT.equals(item.getProductId()) && !"STUCK_CLOSED".equals(item.getAction())) {
                return item;
            }
        }
        return null;
    }

    private String stockKey() {
        return "mall:sms:flashSaleStock:" + promotionId + ":" + sessionId + ":" + PRODUCT;
    }

    private String boughtKey() {
        return "mall:sms:flashSaleBought:" + promotionId + ":" + sessionId + ":" + PRODUCT;
    }
}
