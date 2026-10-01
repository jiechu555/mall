package com.macro.mall.portal.seckill.service;

/**
 * 秒杀库存服务：Redis 库存预热与 Lua 原子预扣
 * Created by jiechu555 on 2026/10/01.
 */
public interface FlashSaleStockService {

    /**
     * 库存预热：从秒杀商品关系表读取总量，用 SET NX 灌入库存 key
     * 已存在则不重置（保证重复预热安全），TTL 为场次结束时间 + 1 小时
     */
    void warmUp(Long promotionId, Long sessionId);

    /**
     * 原子预扣：限购判断 + 库存扣减 + 已购记录在一个 Lua 脚本内完成
     *
     * @return 1 预扣成功；0 售罄；-1 超出限购；-2 活动未预热
     */
    Long deduct(Long promotionId, Long sessionId, Long productId, Long memberId, Integer quantity, Integer perLimit);

    /**
     * 查询 Redis 实时余量（商品列表接口用）
     *
     * @return 剩余数量；活动未预热时返回 -1
     */
    Long getRemainingStock(Long promotionId, Long sessionId, Long productId);
}
