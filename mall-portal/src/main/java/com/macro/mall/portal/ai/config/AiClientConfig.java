package com.macro.mall.portal.ai.config;

import com.macro.mall.portal.ai.client.AiEmbeddingClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 客户端装配：仅在 ai.enabled=true 时创建外部 API Bean（设计文档 08 讲第五节定案）。
 * 不用 Spring AI starter 自动装配——装配粒度由本类精确控制：
 * 关闭时 OpenAiApi / AiEmbeddingClient 完全不存在，BM25 检索照常工作（降级路径）。
 * 路径拼接：智谱 v4 端点不带 /v1 前缀，completions-path/embeddings-path 由配置显式指定（实测确认）。
 * Created by jiechu555 on 2026/10/01.
 */
@Configuration
public class AiClientConfig {

    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public OpenAiApi openAiApi(@Value("${ai.base-url}") String baseUrl,
                               @Value("${ai.api-key:}") String apiKey,
                               @Value("${ai.completions-path}") String completionsPath,
                               @Value("${ai.embeddings-path}") String embeddingsPath) {
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(completionsPath)
                .embeddingsPath(embeddingsPath)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public AiEmbeddingClient aiEmbeddingClient(OpenAiApi openAiApi,
                                               @Value("${ai.embedding-model}") String model) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)
                .build();
        EmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
        return new AiEmbeddingClient(embeddingModel);
    }
}
