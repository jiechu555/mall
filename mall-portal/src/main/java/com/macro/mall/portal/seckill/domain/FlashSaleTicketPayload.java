package com.macro.mall.portal.seckill.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 受理号在 Redis 中的载荷：轮询结果时反查受理单
 * Created by jiechu555 on 2026/10/01.
 */
@Data
public class FlashSaleTicketPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long promotionId;
    private Long sessionId;
    private Long productId;
    private Long memberId;
}
