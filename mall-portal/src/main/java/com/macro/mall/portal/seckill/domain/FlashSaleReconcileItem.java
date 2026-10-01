package com.macro.mall.portal.seckill.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 对账单项：一次对账运行中一个（活动,场次,商品）三元组的守恒检查结果
 * Created by jiechu555 on 2026/10/01.
 */
@Data
public class FlashSaleReconcileItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(title = "秒杀活动id")
    private Long promotionId;
    @Schema(title = "场次id")
    private Long sessionId;
    @Schema(title = "商品id")
    private Long productId;
    @Schema(title = "秒杀总量")
    private Integer total;
    @Schema(title = "Redis 实时余量")
    private Long remaining;
    @Schema(title = "已生成订单件数（status=1）")
    private Long sold;
    @Schema(title = "受理中件数（status=0）")
    private Long pending;
    @Schema(title = "差异 = 期望余量 - 实际余量；>0 表示 Redis 偏少（少卖风险）")
    private Long difference;
    @Schema(title = "场次是否已结束（仅已结束场次允许自动回补）")
    private Boolean sessionEnded;
    @Schema(title = "处置动作：NONE / REFUNDED / ALARM_ONLY_ACTIVE_SESSION / ALARM_OVER / STUCK_CLOSED")
    private String action;
}
