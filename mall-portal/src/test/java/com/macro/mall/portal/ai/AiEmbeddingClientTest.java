package com.macro.mall.portal.ai;

import com.macro.mall.portal.ai.client.AiEmbeddingClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 嵌入客户端 commit 2 验收（设计文档 08 讲第五节定案）：
 * ① ai.enabled=false（默认）时 OpenAiApi/AiEmbeddingClient 完全不装配（降级路径的地基）；
 * ② wrapper 正常路径：委托 EmbeddingModel 返回向量；
 * ③ wrapper 快速失败：嵌入服务返回空向量时抛出 IllegalStateException 而非静默传递。
 * 全部离线——EmbeddingModel 用桩实现，不碰外部 API，CI 不需要 key。
 */
@SpringBootTest
class AiEmbeddingClientTest {

    /** ① 默认关闭时 Bean 不存在 */
    @Autowired(required = false)
    private AiEmbeddingClient conditionalClient;

    @Test
    void 默认关闭时_嵌入客户端Bean不装配() {
        assertNull(conditionalClient, "ai.enabled=false 时 AiEmbeddingClient 不应存在");
    }

    @Test
    void 正常路径_委托嵌入模型返回向量() {
        float[] expected = new float[1024];
        expected[0] = 0.5f;
        AiEmbeddingClient client = new AiEmbeddingClient(stub(expected));
        assertArrayEquals(expected, client.embed("你好"));
    }

    @Test
    void 快速失败_空向量时抛异常() {
        AiEmbeddingClient emptyClient = new AiEmbeddingClient(stub(new float[0]));
        assertThrows(IllegalStateException.class, () -> emptyClient.embed("你好"));
    }

    private EmbeddingModel stub(float[] vector) {
        return new EmbeddingModel() {
            @Override
            public float[] embed(org.springframework.ai.document.Document document) {
                return vector;
            }

            @Override
            public org.springframework.ai.embedding.EmbeddingResponse call(
                    org.springframework.ai.embedding.EmbeddingRequest request) {
                // embed(String) 默认实现委托 call()：返回单条结果即可
                return new org.springframework.ai.embedding.EmbeddingResponse(
                        java.util.List.of(new org.springframework.ai.embedding.Embedding(vector, 0)));
            }
        };
    }
}
