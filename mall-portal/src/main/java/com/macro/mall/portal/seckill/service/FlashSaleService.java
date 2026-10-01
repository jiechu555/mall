package com.macro.mall.portal.seckill.service;

import com.macro.mall.portal.seckill.domain.FlashSaleOrderResult;
import com.macro.mall.portal.seckill.domain.FlashSaleSessionResult;

import java.util.List;

/**
 * 秒杀同步链路：限流 → 令牌核销 → Lua 原子预扣 → 发 MQ（同步阶段只碰 Redis/MQ，不落库）
 * Created by jiechu555 on 2026/10/01.
 */
public interface FlashSaleService {

    /**
     * 当前场次与商品列表（含 Redis 实时余量）；无进行中活动时返回空场次结构
     */
    FlashSaleSessionResult listCurrentSession();

    /**
     * 秒杀下单（同步受理）
     *
     * @return 受理号 ticket（5 分钟有效，用于轮询结果）
     * @throws com.macro.mall.common.exception.ApiException 被限流 / 令牌无效 / 售罄 / 超限购 / 未预热
     */
    String placeOrder(Long promotionId, Long sessionId, Long productId,
                      Long memberReceiveAddressId, String token);

    /**
     * 凭受理号轮询下单结果
     *
     * @throws com.macro.mall.common.exception.ApiException 受理号无效或已过期
     */
    FlashSaleOrderResult queryResult(String ticket);

    /**
     * 预生成秒杀令牌（活动开始前调用；生产上由预热任务触发，测试与演示直接调用）
     *
     * @return 生成的令牌列表
     */
    List<String> issueTokens(Long promotionId, Long sessionId, int count);
}
