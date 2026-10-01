package com.macro.mall.portal.ai.client;

import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 嵌入客户端：把文本变成定长向量（与 ES ai_kb 的 dense_vector dims 一致）。
 * 仅在 ai.enabled=true 时装配（由 AiClientConfig 条件装配）。
 * Created by jiechu555 on 2026/10/01.
 */
public class AiEmbeddingClient {

    private final EmbeddingModel embeddingModel;

    public AiEmbeddingClient(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * @return 定长语义向量
     * @throws IllegalStateException 嵌入服务返回空结果时快速失败
     */
    public float[] embed(String text) {
        float[] result = embeddingModel.embed(text);
        if (result == null || result.length == 0) {
            throw new IllegalStateException("嵌入服务返回空向量");
        }
        return result;
    }

    public int dimensions() {
        return embeddingModel.dimensions();
    }
}
