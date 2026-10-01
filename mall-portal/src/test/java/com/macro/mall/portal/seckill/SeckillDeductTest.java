package com.macro.mall.portal.seckill;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.macro.mall.portal.seckill.service.impl.FlashSaleStockServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 秒杀 Lua 预扣核心测试：直连本机 Redis（与 dev 环境同实例），
 * 手工复刻 BaseRedisConfig 的序列化器，保证与生产线 ARGV 行为一致。
 * 验收标准（06 讲 commit 2）：1000 并发抢 100 库存，成功恰好 100、超卖 0、超限购拒绝。
 */
class SeckillDeductTest {

    private static final Long PROMOTION_A = 9901L;
    private static final Long PROMOTION_B = 9902L;
    private static final Long PROMOTION_C = 9903L;
    private static final Long SESSION = 1L;
    private static final Long PRODUCT = 27L;

    private static LettuceConnectionFactory factory;
    private static RedisTemplate<String, Object> redisTemplate;
    private static FlashSaleStockServiceImpl service;

    @BeforeAll
    static void init() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);
        // 与 mall BaseRedisConfig 保持一致的序列化器组合
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(serializer);
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(serializer);
        redisTemplate.afterPropertiesSet();
        // warmUp 依赖的 mapper 在预扣测试中用不到，传 null（构造器不触碰）
        service = new FlashSaleStockServiceImpl(redisTemplate, null, null,
                "mall", "sms:flashSaleStock", "sms:flashSaleBought");
    }

    @AfterAll
    static void cleanUp() {
        List<String> keys = new ArrayList<>();
        for (Long p : List.of(PROMOTION_A, PROMOTION_B, PROMOTION_C)) {
            keys.add("mall:sms:flashSaleStock:" + p + ":" + SESSION + ":" + PRODUCT);
            keys.add("mall:sms:flashSaleBought:" + p + ":" + SESSION + ":" + PRODUCT);
        }
        redisTemplate.delete(keys);
        factory.stop();
    }

    @Test
    void 并发一千次抢一百件库存_成功恰好一百且不超卖() throws Exception {
        String stockKey = "mall:sms:flashSaleStock:" + PROMOTION_A + ":" + SESSION + ":" + PRODUCT;
        String boughtKey = "mall:sms:flashSaleBought:" + PROMOTION_A + ":" + SESSION + ":" + PRODUCT;
        redisTemplate.delete(List.of(stockKey, boughtKey));
        redisTemplate.opsForValue().set(stockKey, 100);

        int threads = 100;
        int perThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Future<Void>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final long memberId = t + 1;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                int ok = 0;
                for (int i = 0; i < perThread; i++) {
                    Long r = service.deduct(PROMOTION_A, SESSION, PRODUCT, memberId, 1, 10);
                    if (r != null && r == 1L) {
                        ok++;
                    }
                }
                success.addAndGet(ok);
                return null;
            }));
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "任务应在超时前全部完成");
        for (Future<Void> f : futures) {
            f.get();
        }

        assertEquals(100, success.get(), "成功数应恰好等于库存数");
        Object left = redisTemplate.opsForValue().get(stockKey);
        assertEquals(0L, ((Number) left).longValue(), "库存应恰好扣到 0，不允许出现负数（超卖）");
        Map<Object, Object> bought = redisTemplate.opsForHash().entries(boughtKey);
        long totalBought = bought.values().stream()
                .mapToLong(v -> ((Number) v).longValue()).sum();
        assertEquals(100L, totalBought, "已购 Hash 的总件数应等于成功件数");
        bought.values().forEach(v ->
                assertTrue(((Number) v).longValue() <= 10, "任何用户的已购数不得超过限购 10"));
    }

    @Test
    void 同一用户超过限购数_第三次被拒() {
        String stockKey = "mall:sms:flashSaleStock:" + PROMOTION_B + ":" + SESSION + ":" + PRODUCT;
        String boughtKey = "mall:sms:flashSaleBought:" + PROMOTION_B + ":" + SESSION + ":" + PRODUCT;
        redisTemplate.delete(List.of(stockKey, boughtKey));
        redisTemplate.opsForValue().set(stockKey, 100);

        assertEquals(1L, service.deduct(PROMOTION_B, SESSION, PRODUCT, 42L, 1, 2));
        assertEquals(1L, service.deduct(PROMOTION_B, SESSION, PRODUCT, 42L, 1, 2));
        assertEquals(-1L, service.deduct(PROMOTION_B, SESSION, PRODUCT, 42L, 1, 2), "第三次应因超出限购被拒");
    }

    @Test
    void 活动未预热_返回负二() {
        String stockKey = "mall:sms:flashSaleStock:" + PROMOTION_C + ":" + SESSION + ":" + PRODUCT;
        String boughtKey = "mall:sms:flashSaleBought:" + PROMOTION_C + ":" + SESSION + ":" + PRODUCT;
        redisTemplate.delete(List.of(stockKey, boughtKey));

        assertEquals(-2L, service.deduct(PROMOTION_C, SESSION, PRODUCT, 42L, 1, 2), "未预热的库存 key 应返回 -2");
    }
}
