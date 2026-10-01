package com.macro.mall.portal.ai.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 客服问答出参（POST /ai/chat）
 * Created by jiechu555 on 2026/10/01.
 */
@Data
public class AiChatResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(title = "回答文本（degraded 时为降级摘要话术）")
    private String answer;
    @Schema(title = "引用来源（检索命中的文档）")
    private List<AiKbHit> citations;
    @Schema(title = "true = 检索可用但 LLM 不可用/超时，返回 BM25 摘要而非模型生成")
    private boolean degraded;
    @Schema(title = "降级原因：LLM_UNAVAILABLE / LLM_TIMEOUT / LLM_ERROR；非降级时为 null")
    private String degradeReason;
}
