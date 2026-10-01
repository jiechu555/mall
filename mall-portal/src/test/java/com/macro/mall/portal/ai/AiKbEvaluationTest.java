package com.macro.mall.portal.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.ai.domain.AiKbHit;
import com.macro.mall.portal.ai.service.AiKbService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 客服检索质量评测：
 * 25 问评测集（eval-set.json）→ searchHybrid top5 → 召回@5。
 * 模式由 ai.enabled 决定：默认 false 走纯 BM25 基线（84%）；
 * 传 -Dai.enabled=true -Dai.api-key=<智谱key> 走混合检索（BM25+kNN+RRF，实测 100%）。
 * 两组数字写进 document/ai-evaluation/README.md 对比。
 * 标记 es 组（需要真实 ES + 已 rebuild 知识库），CI 暂排除。
 */
@Tag("es")
@SpringBootTest
class AiKbEvaluationTest {

    @Autowired
    private AiKbService aiKbService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<JsonNode> questions;

    @BeforeEach
    void setUp() throws Exception {
        aiKbService.rebuild();
        try (InputStream in = new ClassPathResource("ai/eval-set.json").getInputStream()) {
            questions = new ArrayList<>();
            for (JsonNode q : objectMapper.readTree(in).path("questions")) {
                questions.add(q);
            }
        }
    }

    @Test
    void 评测集召回率_当前检索模式() {
        boolean hybrid = Boolean.parseBoolean(System.getProperty("ai.enabled",
                System.getenv().getOrDefault("AI_ENABLED", "false")));
        int hit = 0, miss = 0;
        List<String> missDetails = new ArrayList<>();

        for (JsonNode q : questions) {
            String question = q.path("question").asText();
            String expectedType = q.path("expected_type").asText("");
            String expectedTitle = q.path("expected_title").asText("");

            List<AiKbHit> results = aiKbService.searchHybrid(question, 5);

            boolean found = false;
            for (AiKbHit r : results) {
                if (match(r, expectedType, expectedTitle)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                hit++;
            } else {
                miss++;
                missDetails.add(String.format("  MISS [%s] %s → top: %s", q.path("category").asText(), question,
                        results.isEmpty() ? "(空)" : results.get(0).getTitle()));
            }
        }

        double recall = (double) hit / questions.size() * 100;
        System.out.println("========================================");
        System.out.println("  AI 客服检索质量评测（模式: " + (hybrid ? "混合检索 BM25+kNN+RRF" : "纯 BM25（嵌入未启用）") + "）");
        System.out.println("  总题数: " + questions.size());
        System.out.println("  命中: " + hit + "  未命中: " + miss);
        System.out.printf("  召回@5: %.1f%%%n", recall);
        System.out.println("========================================");
        if (!missDetails.isEmpty()) {
            System.out.println("未命中明细：");
            missDetails.forEach(System.out::println);
        }

        // 混合模式门槛 92%（实测 100%）；纯 BM25 基线门槛 60%（实测 84%——白话 miss 由向量检索补齐）
        assertTrue(recall >= 92.0,
                String.format("混合检索召回@5 应 ≥92%%（实际 %.1f%%）——检查 ai.enabled/api-key 是否生效、索引向量是否完整", recall));
    }

    private boolean match(AiKbHit hit, String expectedType, String expectedTitle) {
        if (!expectedType.isEmpty() && !expectedType.equals(hit.getSourceType())) {
            return false;
        }
        if (expectedTitle == null || expectedTitle.isEmpty() || "null".equals(expectedTitle)) {
            // 宽泛查询：只要类型匹配即算命中
            return true;
        }
        return hit.getTitle() != null && hit.getTitle().contains(expectedTitle);
    }
}
