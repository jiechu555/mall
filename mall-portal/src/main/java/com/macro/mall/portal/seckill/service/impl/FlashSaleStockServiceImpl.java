package com.macro.mall.portal.seckill.service.impl;

import com.macro.mall.mapper.SmsFlashPromotionProductRelationMapper;
import com.macro.mall.mapper.SmsFlashPromotionSessionMapper;
import com.macro.mall.model.SmsFlashPromotionProductRelation;
import com.macro.mall.model.SmsFlashPromotionProductRelationExample;
import com.macro.mall.portal.seckill.service.FlashSaleStockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

/**
 * 秒杀库存服务实现
 * 不硬改 mall-common 的 RedisService（它没有脚本执行方法），仿 CancelOrderSender
 * 直接注入底层客户端的做法，这里直接注入 RedisTemplate 执行 DefaultRedisScript。
 * Created by jiechu555 on 2026/10/01.
 */
@Service
@ConditionalOnProperty(name = "seckill.enabled", havingValue = "true", matchIfMissing = true)
public class FlashSaleStockServiceImpl implements FlashSaleStockService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SmsFlashPromotionProductRelationMapper relationMapper;
    private final SmsFlashPromotionSessionMapper sessionMapper;
    private final String redisDatabase;
    private final String stockKeyPrefix;
    private final String boughtKeyPrefix;
    private final DefaultRedisScript<Long> deductScript;

    public FlashSaleStockServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                     SmsFlashPromotionProductRelationMapper relationMapper,
                                     SmsFlashPromotionSessionMapper sessionMapper,
                                     @Value("${redis.database}") String redisDatabase,
                                     @Value("${redis.key.flashSaleStock}") String stockKeyPrefix,
                                     @Value("${redis.key.flashSaleBought}") String boughtKeyPrefix) {
        this.redisTemplate = redisTemplate;
        this.relationMapper = relationMapper;
        this.sessionMapper = sessionMapper;
        this.redisDatabase = redisDatabase;
        this.stockKeyPrefix = stockKeyPrefix;
        this.boughtKeyPrefix = boughtKeyPrefix;
        this.deductScript = new DefaultRedisScript<>();
        this.deductScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("luascript/seckill_deduct.lua")));
        this.deductScript.setResultType(Long.class);
    }

    @Override
    public void warmUp(Long promotionId, Long sessionId) {
        SmsFlashPromotionProductRelationExample example = new SmsFlashPromotionProductRelationExample();
        example.createCriteria().andFlashPromotionIdEqualTo(promotionId)
                .andFlashPromotionSessionIdEqualTo(sessionId);
        List<SmsFlashPromotionProductRelation> relations = relationMapper.selectByExample(example);
        long ttlSeconds = ttlUntilSessionEnd(sessionId);
        for (SmsFlashPromotionProductRelation relation : relations) {
            String stockKey = stockKey(promotionId, sessionId, relation.getProductId());
            // SET NX + TTL：key 已存在时不重置，保证重复预热不会凭空补库存
            Boolean seeded = redisTemplate.opsForValue().setIfAbsent(
                    stockKey, relation.getFlashPromotionCount(), Duration.ofSeconds(ttlSeconds));
            if (Boolean.TRUE.equals(seeded)) {
                redisTemplate.expire(stockKey, Duration.ofSeconds(ttlSeconds));
            }
        }
    }

    @Override
    public Long deduct(Long promotionId, Long sessionId, Long productId, Long memberId, Integer quantity, Integer perLimit) {
        // 参数统一用数值类型传入：Jackson2Json 对 Long/Integer 生成纯数字，Lua 端 tonumber() 归一后参与运算
        return redisTemplate.execute(deductScript,
                List.of(stockKey(promotionId, sessionId, productId), boughtKey(promotionId, sessionId, productId)),
                memberId, quantity, perLimit);
    }

    @Override
    public Long getRemainingStock(Long promotionId, Long sessionId, Long productId) {
        Object value = redisTemplate.opsForValue().get(stockKey(promotionId, sessionId, productId));
        return value == null ? -1L : ((Number) value).longValue();
    }

    private String stockKey(Long promotionId, Long sessionId, Long productId) {
        return redisDatabase + ":" + stockKeyPrefix + ":" + promotionId + ":" + sessionId + ":" + productId;
    }

    private String boughtKey(Long promotionId, Long sessionId, Long productId) {
        return redisDatabase + ":" + boughtKeyPrefix + ":" + promotionId + ":" + sessionId + ":" + productId;
    }

    private long ttlUntilSessionEnd(Long sessionId) {
        long fallback = Duration.ofHours(25).getSeconds();
        if (sessionMapper == null) {
            return fallback;
        }
        try {
            Date endTime = sessionMapper.selectByPrimaryKey(sessionId).getEndTime();
            LocalTime end = new java.sql.Time(endTime.getTime()).toLocalTime();
            LocalDateTime expireAt = LocalDateTime.now().toLocalDate().atTime(end).plusHours(1);
            return Math.max(Duration.between(LocalDateTime.now(), expireAt).getSeconds(), Duration.ofHours(1).getSeconds());
        } catch (Exception e) {
            // 场次时间取不到时退化为 25 小时：宁可多留，不可提前过期
            return fallback;
        }
    }
}
