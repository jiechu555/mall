package com.macro.mall.portal.seckill.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀下单结果轮询出参（GET /seckill/result）
 * Created by jiechu555 on 2026/10/01.
 */
@Data
public class FlashSaleOrderResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(title = "受理状态：WAITING 受理中 / SUCCESS 已生成订单 / FAIL 失败")
    private String status;
    @Schema(title = "订单编号（仅 SUCCESS 时有值）")
    private String orderSn;

    public static FlashSaleOrderResult waiting() {
        FlashSaleOrderResult r = new FlashSaleOrderResult();
        r.setStatus("WAITING");
        return r;
    }

    public static FlashSaleOrderResult success(String orderSn) {
        FlashSaleOrderResult r = new FlashSaleOrderResult();
        r.setStatus("SUCCESS");
        r.setOrderSn(orderSn);
        return r;
    }

    public static FlashSaleOrderResult fail() {
        FlashSaleOrderResult r = new FlashSaleOrderResult();
        r.setStatus("FAIL");
        return r;
    }
}
