package com.macro.mall.portal.seckill.service.impl;

import com.macro.mall.mapper.SmsFlashPromotionMapper;
import com.macro.mall.mapper.SmsFlashPromotionOrderMapper;
import com.macro.mall.mapper.SmsFlashPromotionProductRelationMapper;
import com.macro.mall.mapper.SmsFlashPromotionSessionMapper;
import com.macro.mall.model.SmsFlashPromotion;
import com.macro.mall.model.SmsFlashPromotionExample;
import com.macro.mall.model.SmsFlashPromotionOrder;
import com.macro.mall.model.SmsFlashPromotionOrderExample;
import com.macro.mall.model.SmsFlashPromotionProductRelation;
import com.macro.mall.model.SmsFlashPromotionProductRelationExample;
import com.macro.mall.model.SmsFlashPromotionSession;
import com.macro.mall.portal.seckill.domain.FlashSaleReconcileItem;
import com.macro.mall.portal.seckill.service.FlashSaleReconcileService;
import com.macro.mall.portal.seckill.service.FlashSaleStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 秒杀对账服务实现
 * 守恒式：Redis 实时余量 + 受理单 status=1 件数 + 受理单 status=0 件数 = flash_promotion_count
 * （status=2 已回补、余量已加回，不计入）。差异处理策略：
 * - 差异>0 且场次已结束：自动回补 Redis（少卖风险消除）
 * - 差异>0 且场次进行中：只告警不动手——预扣成功与受理单落库之间存在毫秒级在途窗口，
 *   此时回补会造成超量退款（等于变相超卖）
 * - 差异<0：Redis 偏多（人工误操作或统计口径），只告警
 * Created by jiechu555 on 2026/10/01.
 */
@Service
@ConditionalOnProperty(name = "seckill.enabled", havingValue = "true", matchIfMissing = true)
public class FlashSaleReconcileServiceImpl implements FlashSaleReconcileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlashSaleReconcileServiceImpl.class);

    /** 受理单超过该分钟数仍为 status=0，判定为消费失败遗留 */
    private static final int STUCK_ACCEPTANCE_MINUTES = 10;

    private final SmsFlashPromotionMapper promotionMapper;
    private final SmsFlashPromotionSessionMapper sessionMapper;
    private final SmsFlashPromotionProductRelationMapper relationMapper;
    private final SmsFlashPromotionOrderMapper acceptanceMapper;
    private final FlashSaleStockService flashSaleStockService;

    public FlashSaleReconcileServiceImpl(SmsFlashPromotionMapper promotionMapper,
                                         SmsFlashPromotionSessionMapper sessionMapper,
                                         SmsFlashPromotionProductRelationMapper relationMapper,
                                         SmsFlashPromotionOrderMapper acceptanceMapper,
                                         FlashSaleStockService flashSaleStockService) {
        this.promotionMapper = promotionMapper;
        this.sessionMapper = sessionMapper;
        this.relationMapper = relationMapper;
        this.acceptanceMapper = acceptanceMapper;
        this.flashSaleStockService = flashSaleStockService;
    }

    @Override
    public List<FlashSaleReconcileItem> reconcileOnce() {
        List<FlashSaleReconcileItem> report = new ArrayList<>();
        // ① 先清扫卡死的受理单：回补库存并关闭（对守恒式是中性操作：pending-1、余量+1）
        sweepStuckAcceptances(report);
        // ② 守恒校验
        Date now = new Date();
        for (SmsFlashPromotion promotion : findActivePromotions(now)) {
            SmsFlashPromotionProductRelationExample relationExample = new SmsFlashPromotionProductRelationExample();
            relationExample.createCriteria().andFlashPromotionIdEqualTo(promotion.getId());
            List<SmsFlashPromotionProductRelation> relations = relationMapper.selectByExample(relationExample);
            for (SmsFlashPromotionProductRelation relation : relations) {
                Long promotionId = promotion.getId();
                Long sessionId = relation.getFlashPromotionSessionId();
                Long productId = relation.getProductId();
                Long remaining = flashSaleStockService.getRemainingStock(promotionId, sessionId, productId);
                if (remaining == null || remaining < 0) {
                    continue; // 未预热，无从对账
                }
                int total = relation.getFlashPromotionCount() == null ? 0 : relation.getFlashPromotionCount();
                long sold = sumQuantity(promotionId, sessionId, productId, 1);
                long pending = sumQuantity(promotionId, sessionId, productId, 0);
                long expected = (long) total - sold - pending;
                long difference = expected - remaining;
                boolean sessionEnded = isSessionEnded(sessionId);

                FlashSaleReconcileItem item = new FlashSaleReconcileItem();
                item.setPromotionId(promotionId);
                item.setSessionId(sessionId);
                item.setProductId(productId);
                item.setTotal(total);
                item.setRemaining(remaining);
                item.setSold(sold);
                item.setPending(pending);
                item.setDifference(difference);
                item.setSessionEnded(sessionEnded);

                if (difference > 0) {
                    if (sessionEnded) {
                        flashSaleStockService.refundStock(promotionId, sessionId, productId, (int) difference);
                        item.setAction("REFUNDED");
                        LOGGER.warn("秒杀对账：场次已结束且 Redis 余量偏少 {}，已自动回补。promotion={}, session={}, product={}",
                                difference, promotionId, sessionId, productId);
                    } else {
                        item.setAction("ALARM_ONLY_ACTIVE_SESSION");
                        LOGGER.warn("秒杀对账：场次进行中 Redis 余量偏少 {}，只告警不回补（在途请求窗口）。promotion={}, session={}, product={}",
                                difference, promotionId, sessionId, productId);
                    }
                } else if (difference < 0) {
                    item.setAction("ALARM_OVER");
                    LOGGER.warn("秒杀对账：Redis 余量比账面多 {}，请人工核查。promotion={}, session={}, product={}",
                            -difference, promotionId, sessionId, productId);
                } else {
                    item.setAction("NONE");
                }
                report.add(item);
            }
        }
        return report;
    }

    private void sweepStuckAcceptances(List<FlashSaleReconcileItem> report) {
        Date threshold = new Date(System.currentTimeMillis() - STUCK_ACCEPTANCE_MINUTES * 60_000L);
        SmsFlashPromotionOrderExample example = new SmsFlashPromotionOrderExample();
        example.createCriteria().andStatusEqualTo(0).andCreateTimeLessThan(threshold);
        List<SmsFlashPromotionOrder> stuck = acceptanceMapper.selectByExample(example);
        for (SmsFlashPromotionOrder acceptance : stuck) {
            int quantity = acceptance.getQuantity() == null ? 1 : acceptance.getQuantity();
            flashSaleStockService.refundStock(acceptance.getFlashPromotionId(),
                    acceptance.getFlashPromotionSessionId(), acceptance.getProductId(), quantity);
            SmsFlashPromotionOrder update = new SmsFlashPromotionOrder();
            update.setId(acceptance.getId());
            update.setStatus(2);
            acceptanceMapper.updateByPrimaryKeySelective(update);

            FlashSaleReconcileItem item = new FlashSaleReconcileItem();
            item.setPromotionId(acceptance.getFlashPromotionId());
            item.setSessionId(acceptance.getFlashPromotionSessionId());
            item.setProductId(acceptance.getProductId());
            item.setDifference(0L);
            item.setSessionEnded(true);
            item.setAction("STUCK_CLOSED");
            report.add(item);
            LOGGER.warn("秒杀对账：受理单卡死超 {} 分钟已关闭并回补库存。acceptanceId={}",
                    STUCK_ACCEPTANCE_MINUTES, acceptance.getId());
        }
    }

    private List<SmsFlashPromotion> findActivePromotions(Date now) {
        SmsFlashPromotionExample example = new SmsFlashPromotionExample();
        example.createCriteria().andStatusEqualTo(1);
        List<SmsFlashPromotion> active = new ArrayList<>();
        for (SmsFlashPromotion promotion : promotionMapper.selectByExample(example)) {
            if (promotion.getStartDate() != null && promotion.getEndDate() != null
                    && !now.before(promotion.getStartDate()) && !now.after(promotion.getEndDate())) {
                active.add(promotion);
            }
        }
        return active;
    }

    private long sumQuantity(Long promotionId, Long sessionId, Long productId, int status) {
        SmsFlashPromotionOrderExample example = new SmsFlashPromotionOrderExample();
        example.createCriteria()
                .andFlashPromotionIdEqualTo(promotionId)
                .andFlashPromotionSessionIdEqualTo(sessionId)
                .andProductIdEqualTo(productId)
                .andStatusEqualTo(status);
        long sum = 0;
        for (SmsFlashPromotionOrder order : acceptanceMapper.selectByExample(example)) {
            sum += order.getQuantity() == null ? 1 : order.getQuantity();
        }
        return sum;
    }

    private boolean isSessionEnded(Long sessionId) {
        SmsFlashPromotionSession session = sessionMapper.selectByPrimaryKey(sessionId);
        if (session == null || session.getEndTime() == null) {
            return false;
        }
        LocalTime end = new java.sql.Time(session.getEndTime().getTime()).toLocalTime();
        return LocalTime.now().isAfter(end);
    }
}
