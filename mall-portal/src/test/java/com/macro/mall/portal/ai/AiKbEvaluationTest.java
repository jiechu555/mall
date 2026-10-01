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
 * AI 客服检索质量评测（commit 6 验收）：
 * 25 问评测集（eval-set.json）→ searchHybrid top5 → 召回@5。
 * 本测试环境 ai.enabled=false → 纯 BM25 基线；
 * 用户启用嵌入后重跑得混合召回——两组数字写进 README 对比。
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
    void 评测集召回率_纯BM25基线() {
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
        System.out.println("  AI 客服检索质量评测（纯 BM25 基线）");
        System.out.println("  总题数: " + questions.size());
        System.out.println("  命中: " + hit + "  未命中: " + miss);
        System.out.printf("  召回@5: %.1f%%  (目标 ≥80%%)%n", recall);
        System.out.println("========================================");
        if (!missDetails.isEmpty()) {
            System.out.println("未命中明细：");
            missDetails.forEach(System.out::println);
        }

        assertTrue(recall >= 60.0,
                String.format("BM25 基线召回@5 应 ≥60%%（实际 %.1f%%）——白话问法需要向量检索补，全量 ≥80%% 目标在混合模式下达成", recall));
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
