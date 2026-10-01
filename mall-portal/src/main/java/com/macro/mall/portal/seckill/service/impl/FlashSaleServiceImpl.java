package com.macro.mall.portal.seckill.service.impl;

import com.macro.mall.common.exception.Asserts;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.SmsFlashPromotionMapper;
import com.macro.mall.mapper.SmsFlashPromotionOrderMapper;
import com.macro.mall.mapper.SmsFlashPromotionProductRelationMapper;
import com.macro.mall.mapper.SmsFlashPromotionSessionMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.SmsFlashPromotion;
import com.macro.mall.model.SmsFlashPromotionExample;
import com.macro.mall.model.SmsFlashPromotionOrder;
import com.macro.mall.model.SmsFlashPromotionOrderExample;
import com.macro.mall.model.SmsFlashPromotionProductRelation;
import com.macro.mall.model.SmsFlashPromotionProductRelationExample;
import com.macro.mall.model.SmsFlashPromotionSession;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.seckill.component.FlashSaleOrderSender;
import com.macro.mall.portal.seckill.domain.FlashSaleOrderMessage;
import com.macro.mall.portal.seckill.domain.FlashSaleOrderResult;
import com.macro.mall.portal.seckill.domain.FlashSaleSessionResult;
import com.macro.mall.portal.seckill.domain.FlashSaleTicketPayload;
import com.macro.mall.portal.seckill.service.FlashSaleService;
import com.macro.mall.portal.seckill.service.FlashSaleStockService;
import com.macro.mall.portal.service.UmsMemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 秒杀同步链路实现
 * 防刷三层递进：用户级频控（挡脚本重放）→ 一次性令牌 SREM 核销（挡裸请求）→
 * Lua 内限购 + DB 唯一键（挡黄牛囤货，消费端兜底）。同步阶段一次都不落库。
 * Created by jiechu555 on 2026/10/01.
 */
@Service
@ConditionalOnProperty(name = "seckill.enabled", havingValue = "true", matchIfMissing = true)
public class FlashSaleServiceImpl implements FlashSaleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlashSaleServiceImpl.class);

    /** 用户级频控：每秒最多 10 次下单请求 */
    private static final long RATE_LIMIT_PER_SECOND = 10;
    /** 受理号有效期（秒） */
    private static final long TICKET_EXPIRE_SECONDS = 300;

    private final FlashSaleStockService flashSaleStockService;
    private final FlashSaleOrderSender flashSaleOrderSender;
    private final RedisService redisService;
    private final UmsMemberService umsMemberService;
    private final SmsFlashPromotionMapper promotionMapper;
    private final SmsFlashPromotionSessionMapper sessionMapper;
    private final SmsFlashPromotionProductRelationMapper relationMapper;
    private final SmsFlashPromotionOrderMapper acceptanceMapper;
    private final PmsProductMapper productMapper;

    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Value("${redis.key.flashSaleToken}")
    private String TOKEN_KEY_PREFIX;
    @Value("${redis.key.flashSaleRate}")
    private String RATE_KEY_PREFIX;
    @Value("${redis.key.flashSaleTicket}")
    private String TICKET_KEY_PREFIX;

    public FlashSaleServiceImpl(FlashSaleStockService flashSaleStockService,
                                FlashSaleOrderSender flashSaleOrderSender,
                                RedisService redisService,
                                UmsMemberService umsMemberService,
                                SmsFlashPromotionMapper promotionMapper,
                                SmsFlashPromotionSessionMapper sessionMapper,
                                SmsFlashPromotionProductRelationMapper relationMapper,
                                SmsFlashPromotionOrderMapper acceptanceMapper,
                                PmsProductMapper productMapper) {
        this.flashSaleStockService = flashSaleStockService;
        this.flashSaleOrderSender = flashSaleOrderSender;
        this.redisService = redisService;
        this.umsMemberService = umsMemberService;
        this.promotionMapper = promotionMapper;
        this.sessionMapper = sessionMapper;
        this.relationMapper = relationMapper;
        this.acceptanceMapper = acceptanceMapper;
        this.productMapper = productMapper;
    }

    @Override
    public FlashSaleSessionResult listCurrentSession() {
        FlashSaleSessionResult result = new FlashSaleSessionResult();
        Date now = new Date();
        SmsFlashPromotion promotion = findActivePromotion(now);
        if (promotion == null) {
            return result;
        }
        List<SmsFlashPromotionProductRelation> relations = relationsOf(promotion.getId());
        SmsFlashPromotionSession currentSession = findCurrentSession(relations);
        if (currentSession == null) {
            result.setPromotionId(promotion.getId());
            return result;
        }
        result.setPromotionId(promotion.getId());
        result.setSessionId(currentSession.getId());
        result.setStartTime(currentSession.getStartTime());
        result.setEndTime(currentSession.getEndTime());
        List<FlashSaleSessionResult.FlashSaleProduct> products = new ArrayList<>();
        for (SmsFlashPromotionProductRelation relation : relations) {
            if (!relation.getFlashPromotionSessionId().equals(currentSession.getId())) {
                continue;
            }
            FlashSaleSessionResult.FlashSaleProduct vo = new FlashSaleSessionResult.FlashSaleProduct();
            vo.setProductId(relation.getProductId());
            PmsProduct product = productMapper.selectByPrimaryKey(relation.getProductId());
            if (product != null) {
                vo.setProductName(product.getName());
                vo.setProductPic(product.getPic());
            }
            vo.setFlashPromotionPrice(relation.getFlashPromotionPrice());
            vo.setFlashPromotionCount(relation.getFlashPromotionCount());
            vo.setFlashPromotionLimit(relation.getFlashPromotionLimit());
            vo.setRemainingStock(flashSaleStockService.getRemainingStock(
                    promotion.getId(), currentSession.getId(), relation.getProductId()));
            products.add(vo);
        }
        result.setProducts(products);
        return result;
    }

    @Override
    public String placeOrder(Long promotionId, Long sessionId, Long productId,
                             Long memberReceiveAddressId, String token) {
        UmsMember member = umsMemberService.getCurrentMember();
        if (member == null) {
            Asserts.fail("会员未登录");
        }

        // 第一层：用户级频控（挡脚本重放），INCR + 首次 EXPIRE 1 秒
        String rateKey = REDIS_DATABASE + ":" + RATE_KEY_PREFIX + ":" + member.getId();
        Long count = redisService.incr(rateKey, 1);
        if (count != null && count == 1) {
            redisService.expire(rateKey, 1);
        }
        if (count != null && count > RATE_LIMIT_PER_SECOND) {
            Asserts.fail("操作过于频繁，请稍后再试");
        }

        // 第二层：一次性令牌，只认 SREM 返回 1——删到才算拥有，天然防同一令牌重放
        String tokenKey = REDIS_DATABASE + ":" + TOKEN_KEY_PREFIX + ":" + promotionId + ":" + sessionId;
        Long removed = redisService.sRemove(tokenKey, token);
        if (removed == null || removed < 1) {
            Asserts.fail("秒杀令牌无效");
        }

        // 第三层：Lua 原子预扣（限购在脚本内判断，DB 唯一键兜底）
        SmsFlashPromotionProductRelation relation = getRelation(promotionId, sessionId, productId);
        if (relation == null) {
            Asserts.fail("商品未参加本场秒杀");
        }
        Integer perLimit = relation.getFlashPromotionLimit() == null ? 1 : relation.getFlashPromotionLimit();
        Long r = flashSaleStockService.deduct(promotionId, sessionId, productId,
                member.getId(), 1, perLimit);
        if (r == null || r == -1) {
            Asserts.fail("超出限购数量");
        }
        if (r == -2) {
            Asserts.fail("活动未开始或已结束");
        }
        if (r == 0) {
            Asserts.fail("已售罄");
        }

        // 预扣成功：发 MQ（落库后移到消费端），受理号落 Redis 5 分钟
        String ticket = UUID.randomUUID().toString();
        FlashSaleOrderMessage message = new FlashSaleOrderMessage();
        message.setPromotionId(promotionId);
        message.setSessionId(sessionId);
        message.setProductId(productId);
        // mall 的秒杀关系表不含 sku 维度，productSkuId 留空：消费端跳过 SKU 锁定。
        // 若后续把关系表扩展出 sku 列，此处回填即可恢复锁定语义
        message.setProductSkuId(null);
        message.setMemberId(member.getId());
        message.setMemberReceiveAddressId(memberReceiveAddressId);
        message.setQuantity(1);
        message.setFlashPromotionPrice(relation.getFlashPromotionPrice());
        message.setPayType(0);
        message.setTicket(ticket);
        flashSaleOrderSender.sendMessage(message);

        FlashSaleTicketPayload payload = new FlashSaleTicketPayload();
        payload.setPromotionId(promotionId);
        payload.setSessionId(sessionId);
        payload.setProductId(productId);
        payload.setMemberId(member.getId());
        redisService.set(ticketKey(ticket), payload, TICKET_EXPIRE_SECONDS);

        LOGGER.info("秒杀下单受理: ticket={}, memberId={}, productId={}", ticket, member.getId(), productId);
        return ticket;
    }

    @Override
    public FlashSaleOrderResult queryResult(String ticket) {
        Object cached = redisService.get(ticketKey(ticket));
        if (!(cached instanceof FlashSaleTicketPayload)) {
            Asserts.fail("受理号无效或已过期");
        }
        FlashSaleTicketPayload payload = (FlashSaleTicketPayload) cached;
        SmsFlashPromotionOrderExample example = new SmsFlashPromotionOrderExample();
        example.createCriteria()
                .andFlashPromotionIdEqualTo(payload.getPromotionId())
                .andFlashPromotionSessionIdEqualTo(payload.getSessionId())
                .andProductIdEqualTo(payload.getProductId())
                .andMemberIdEqualTo(payload.getMemberId());
        List<SmsFlashPromotionOrder> rows = acceptanceMapper.selectByExample(example);
        if (rows.isEmpty()) {
            return FlashSaleOrderResult.fail();
        }
        SmsFlashPromotionOrder acceptance = rows.get(0);
        if (acceptance.getStatus() == 0) {
            return FlashSaleOrderResult.waiting();
        }
        if (acceptance.getStatus() == 1) {
            return FlashSaleOrderResult.success(acceptance.getOrderSn());
        }
        return FlashSaleOrderResult.fail();
    }

    @Override
    public List<String> issueTokens(Long promotionId, Long sessionId, int count) {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tokens.add(UUID.randomUUID().toString());
        }
        String tokenKey = REDIS_DATABASE + ":" + TOKEN_KEY_PREFIX + ":" + promotionId + ":" + sessionId;
        // 令牌集随场次过期兜底（25 小时，略长于任何场次周期）
        redisService.sAdd(tokenKey, 25 * 3600, tokens.toArray());
        return tokens;
    }

    private String ticketKey(String ticket) {
        return REDIS_DATABASE + ":" + TICKET_KEY_PREFIX + ":" + ticket;
    }

    private SmsFlashPromotion findActivePromotion(Date now) {
        SmsFlashPromotionExample example = new SmsFlashPromotionExample();
        example.createCriteria().andStatusEqualTo(1);
        List<SmsFlashPromotion> promotions = promotionMapper.selectByExample(example);
        for (SmsFlashPromotion promotion : promotions) {
            if (promotion.getStartDate() != null && promotion.getEndDate() != null
                    && !now.before(promotion.getStartDate()) && !now.after(promotion.getEndDate())) {
                return promotion;
            }
        }
        return null;
    }

    private List<SmsFlashPromotionProductRelation> relationsOf(Long promotionId) {
        SmsFlashPromotionProductRelationExample example = new SmsFlashPromotionProductRelationExample();
        example.createCriteria().andFlashPromotionIdEqualTo(promotionId);
        return relationMapper.selectByExample(example);
    }

    private SmsFlashPromotionSession findCurrentSession(List<SmsFlashPromotionProductRelation> relations) {
        List<Long> sessionIds = relations.stream()
                .map(SmsFlashPromotionProductRelation::getFlashPromotionSessionId)
                .distinct().collect(Collectors.toList());
        LocalTime nowTime = LocalTime.now();
        for (Long sessionId : sessionIds) {
            SmsFlashPromotionSession session = sessionMapper.selectByPrimaryKey(sessionId);
            if (session == null || session.getStartTime() == null || session.getEndTime() == null) {
                continue;
            }
            LocalTime start = new java.sql.Time(session.getStartTime().getTime()).toLocalTime();
            LocalTime end = new java.sql.Time(session.getEndTime().getTime()).toLocalTime();
            // 场次是每日时段字典，不跨午夜；now 已落在活动日期区间内（findActivePromotion 保证）
            if (!nowTime.isBefore(start) && nowTime.isBefore(end)) {
                return session;
            }
        }
        return null;
    }

    private SmsFlashPromotionProductRelation getRelation(Long promotionId, Long sessionId, Long productId) {
        SmsFlashPromotionProductRelationExample example = new SmsFlashPromotionProductRelationExample();
        example.createCriteria()
                .andFlashPromotionIdEqualTo(promotionId)
                .andFlashPromotionSessionIdEqualTo(sessionId)
                .andProductIdEqualTo(productId);
        List<SmsFlashPromotionProductRelation> relations = relationMapper.selectByExample(example);
        return relations.isEmpty() ? null : relations.get(0);
    }
}
