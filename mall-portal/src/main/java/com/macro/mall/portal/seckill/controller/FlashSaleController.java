package com.macro.mall.portal.seckill.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.portal.seckill.domain.FlashSaleOrderResult;
import com.macro.mall.portal.seckill.domain.FlashSaleSessionResult;
import com.macro.mall.portal.seckill.service.FlashSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 秒杀模块接口
 * 不进安全白名单：/seckill/** 天然要求会员 JWT，memberId 一律从登录态取，禁止前端传入。
 * Created by jiechu555 on 2026/10/01.
 */
@Controller
@Tag(name = "FlashSaleController", description = "秒杀模块接口")
@RequestMapping("/seckill")
public class FlashSaleController {

    @Autowired
    private FlashSaleService flashSaleService;

    @Operation(summary = "当前场次与商品列表（含 Redis 实时余量）")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<FlashSaleSessionResult> list() {
        return CommonResult.success(flashSaleService.listCurrentSession());
    }

    @Operation(summary = "秒杀下单（同步受理：限流→令牌核销→Lua原子预扣→发MQ）")
    @RequestMapping(value = "/order", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<String> order(@RequestParam Long promotionId,
                                      @RequestParam Long sessionId,
                                      @RequestParam Long productId,
                                      @RequestParam(required = false) Long memberReceiveAddressId,
                                      @RequestParam String token) {
        try {
            String ticket = flashSaleService.placeOrder(promotionId, sessionId, productId,
                    memberReceiveAddressId, token);
            return CommonResult.success(ticket, "受理中，请凭受理号轮询结果");
        } catch (ApiException e) {
            return CommonResult.failed(e.getMessage());
        }
    }

    @Operation(summary = "凭受理号轮询下单结果")
    @RequestMapping(value = "/result", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<FlashSaleOrderResult> result(@RequestParam String ticket) {
        try {
            return CommonResult.success(flashSaleService.queryResult(ticket));
        } catch (ApiException e) {
            return CommonResult.failed(e.getMessage());
        }
    }
}
