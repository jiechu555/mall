package com.macro.mall.portal.ai.service;

import com.macro.mall.portal.ai.domain.AiChatResponse;

/**
 * AI 客服问答服务
 * Created by jiechu555 on 2026/10/01.
 */
public interface AiChatService {

    /**
     * 会员提问 → 检索 → LLM 生成回答（含引用）
     * 频控超限抛 ApiException；LLM 不可用时降级返回 BM25 检索摘要（degraded=true）
     */
    AiChatResponse chat(String question);
}
