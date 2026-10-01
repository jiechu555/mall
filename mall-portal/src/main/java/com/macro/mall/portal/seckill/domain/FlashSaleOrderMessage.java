package com.macro.mall.portal.seckill.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀订单消息体：同步链路预扣成功后发给 MQ，由消费端落库
 * Created by jiechu555 on 2026/10/01.
 */
@Data
public class FlashSaleOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 秒杀活动id
     */
    private Long promotionId;
    /**
     * 秒杀场次id
     */
    private Long sessionId;
    /**
     * 商品id
     */
    private Long productId;
    /**
     * 商品sku编号
     */
    private Long productSkuId;
    /**
     * 会员id
     */
    private Long memberId;
    /**
     * 收货地址id（消费端创建 oms_order 时使用）
     */
    private Long memberReceiveAddressId;
    /**
     * 购买数量
     */
    private Integer quantity;
    /**
     * 成交秒杀价（服务端定价，来自 sms_flash_promotion_product_relation）
     */
    private BigDecimal flashPromotionPrice;
    /**
     * 支付方式：0->货到付款；1->在线支付
     */
    private Integer payType;
    /**
     * 受理号（用户用它轮询下单结果）
     */
    private String ticket;
}
