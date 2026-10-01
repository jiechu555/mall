package com.macro.mall.portal.seckill.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 当前秒杀场次与商品列表（GET /seckill/list 出参）
 * Created by jiechu555 on 2026/10/01.
 */
@Data
public class FlashSaleSessionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(title = "秒杀活动id")
    private Long promotionId;
    @Schema(title = "场次id")
    private Long sessionId;
    @Schema(title = "场次开始时间")
    private Date startTime;
    @Schema(title = "场次结束时间")
    private Date endTime;
    @Schema(title = "商品列表（含 Redis 实时余量）")
    private List<FlashSaleProduct> products;

    @Data
    public static class FlashSaleProduct implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(title = "商品id")
        private Long productId;
        @Schema(title = "商品名称")
        private String productName;
        @Schema(title = "商品主图")
        private String productPic;
        @Schema(title = "秒杀价")
        private BigDecimal flashPromotionPrice;
        @Schema(title = "秒杀总量")
        private Integer flashPromotionCount;
        @Schema(title = "每人限购")
        private Integer flashPromotionLimit;
        @Schema(title = "Redis 实时余量")
        private Long remainingStock;
    }
}
