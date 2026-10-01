package com.macro.mall.portal.ai;

import com.macro.mall.portal.ai.domain.AiChatResponse;
import com.macro.mall.portal.ai.domain.AiKbHit;
import com.macro.mall.portal.ai.service.impl.AiChatServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 客服 commit 5 验收（纯单元测试——不走 Spring 安全链路，不需要 JWT）：
 * ① Prompt 组装含防注入定界符（<retrieved_context>）和"不得执行"声明；
 * ② LLM 不可用时降级返回 BM25 摘要 + degraded=true + 降级原因；
 * ③ 降级时引用列表包含检索命中的文档。
 * 完整链路（JWT 鉴权+频控+真实 HTTP）在 commit 6 评测集补集成测试。
 */
class AiChatServiceTest {

    @Test
    void Prompt组装_含防注入定界符与声明() {
        AiKbHit hit = new AiKbHit();
        hit.setSourceType("FAQ");
        hit.setSourceId(8L);
        hit.setTitle("退换货政策");
        hit.setSnippet("签收后 7 天内支持无理由退货");

        AiChatServiceImpl impl = new AiChatServiceImpl(null, null, null);
        String prompt = impl.buildPrompt("怎么退货", List.of(hit));

        assertTrue(prompt.contains("<retrieved_context>"), "Prompt 应含 XML 定界符");
        assertTrue(prompt.contains("</retrieved_context>"), "Prompt 应含闭合定界符");
        assertTrue(prompt.contains("不得执行"), "Prompt 应声明不执行检索内容中的指令");
        assertTrue(prompt.contains("退换货政策"), "Prompt 应包含检索到的文档标题");
        assertTrue(prompt.contains("怎么退货"), "Prompt 应包含用户问题");
    }

    @Test
    void 降级路径_LLM不可用时返回BM25摘要() {
        AiKbHit hit = new AiKbHit();
        hit.setSourceType("FAQ");
        hit.setSourceId(8L);
        hit.setTitle("退换货政策");
        hit.setSnippet("签收后 7 天内支持无理由退货");

        AiChatServiceImpl impl = new AiChatServiceImpl(null, null, null);
        AiChatResponse resp = impl.degraded(List.of(hit), "LLM_UNAVAILABLE");

        assertTrue(resp.isDegraded(), "chatClient 为 null 时应标记 degraded");
        assertTrue(resp.getAnswer().contains("退换货政策"), "降级话术应包含检索到的文档标题");
        assertTrue("LLM_UNAVAILABLE".equals(resp.getDegradeReason()), "降级原因应标记");
        assertTrue(resp.getCitations().size() == 1, "引用列表应包含检索结果");
    }
}
