package com.macro.mall.portal.seckill.service;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.seckill.domain.FlashSaleOrderMessage;

/**
 * 秒杀订单落库服务：消费 MQ 消息后创建正式订单
 * Created by jiechu555 on 2026/10/01.
 */
public interface FlashSaleOrderService {

    /**
     * 创建受理单（独立事务）：写入 sms_flash_promotion_order，status=0 受理中
     *
     * @return 受理单主键；同活动同场次同商品同人重复投递时返回 null（唯一键幂等）
     */
    Long createAcceptance(FlashSaleOrderMessage message);

    /**
     * 落库生成正式订单（事务）：oms_order（order_type=1）+ oms_order_item + 原子锁定 SKU 库存
     * + 受理单回填 order_id/order_sn/status=1
     *
     * @return 生成的订单（含 id 与 orderSn）
     */
    OmsOrder createOrder(FlashSaleOrderMessage message, Long acceptanceId);
}
