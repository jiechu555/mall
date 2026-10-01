package com.macro.mall.portal.ai;

import com.macro.mall.portal.ai.client.AiEmbeddingClient;
import com.macro.mall.portal.ai.config.AiClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 嵌入客户端 commit 2 验收（设计文档 08 讲第五节定案）：
 * ① ai.enabled=false 时 AiEmbeddingClient 完全不装配（降级路径的地基）——
 *    用 ApplicationContextRunner 独立迷你上下文验证 @ConditionalOnProperty，
 *    不受外部 -Dai.enabled / 环境变量注入影响（真实 key 接入后全量测试仍可跑默认关闭场景）；
 * ② wrapper 正常路径：委托 EmbeddingModel 返回向量；
 * ③ wrapper 快速失败：嵌入服务返回空向量时抛出 IllegalStateException 而非静默传递。
 * 全部离线——EmbeddingModel 用桩实现，不碰外部 API，CI 不需要 key。
 */
class AiEmbeddingClientTest {

    @Test
    void 默认关闭时_嵌入客户端Bean不装配() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "ai.enabled=false",
                        "ai.base-url=https://open.bigmodel.cn/api/paas/v4",
                        "ai.completions-path=/chat/completions",
                        "ai.embeddings-path=/embeddings",
                        "ai.embedding-model=embedding-2",
                        "ai.chat-model=glm-4.5-air")
                .withUserConfiguration(AiClientConfig.class)
                .run(ctx -> assertNull(ctx.getBeanProvider(AiEmbeddingClient.class).getIfAvailable(),
                        "ai.enabled=false 时 AiEmbeddingClient 不应存在"));
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
