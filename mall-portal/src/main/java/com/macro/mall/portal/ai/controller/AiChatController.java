package com.macro.mall.portal.ai.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.ai.domain.AiChatResponse;
import com.macro.mall.portal.ai.service.AiChatService;
import com.macro.mall.portal.ai.service.AiKbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * AI 客服接口
 * 不进安全白名单：/ai/** 天然要求会员 JWT，memberId 从登录态取。
 * Controller 与 BM25 检索常驻装配（ai.enabled 仅条件装配 LLM Bean——设计第五节开关语义）。
 * Created by jiechu555 on 2026/10/01.
 */
@Controller
@Tag(name = "AiChatController", description = "AI 智能客服")
@RequestMapping("/ai")
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;
    @Autowired
    private AiKbService aiKbService;

    @Operation(summary = "智能客服问答（检索+LLM生成，降级时返回关键词摘要）")
    @RequestMapping(value = "/chat", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<AiChatResponse> chat(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return CommonResult.failed("问题不能为空");
        }
        return CommonResult.success(aiChatService.chat(question));
    }

    @Operation(summary = "重建 AI 知识库（全量：商品卡片+FAQ 种子）")
    @RequestMapping(value = "/kb/rebuild", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<Integer> rebuild() {
        return CommonResult.success(aiKbService.rebuild(), "知识库重建完成");
    }

    @Operation(summary = "知识库状态")
    @RequestMapping(value = "/kb/status", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<Map<String, Object>> status() {
        return CommonResult.success(Map.of(
                "docCount", aiKbService.docCount()
        ));
    }
}
