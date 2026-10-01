package com.macro.mall.portal.ai;

import com.macro.mall.portal.ai.domain.AiKbHit;
import com.macro.mall.portal.ai.service.impl.AiKbServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RRF 融合算法单元测试（纯离线，不需要 ES/Spring 上下文——CI 可跑）。
 * 降级行为的集成测试在 AiKbIndexAndSearchTest（@Tag("es")，仅本地跑）。
 */
class AiHybridSearchTest {

    @Test
    void RRF融合_双路命中排前_单路按排名排后() {
        AiKbHit a1 = hit("PRODUCT", 1L, "BM25第一名");
        AiKbHit a2 = hit("PRODUCT", 2L, "BM25第二名");
        AiKbHit a3 = hit("PRODUCT", 3L, "BM25第三名");
        AiKbHit a4 = hit("FAQ", 10L, "BM25第四名");

        List<AiKbHit> bm25 = List.of(a1, a2, a4);
        List<AiKbHit> vector = List.of(a3, a4, a1);

        List<AiKbHit> fused = AiKbServiceImpl.rrfFuse(bm25, vector);

        assertEquals("PRODUCT:1", key(fused.get(0)), "双路含 BM25 rank1 的应排第一");
        assertEquals("FAQ:10", key(fused.get(1)), "双路均衡的应排第二");
        assertTrue(fused.get(0).getScore() > fused.get(2).getScore(), "双路 > 单路");
        assertEquals(4, fused.size());
    }

    @Test
    void RRF融合_分数等于两路倒数排名之和() {
        AiKbHit shared = hit("PRODUCT", 1L, "shared");
        AiKbHit bm25Only = hit("FAQ", 2L, "bm25-only");

        List<AiKbHit> fused = AiKbServiceImpl.rrfFuse(List.of(shared, bm25Only), List.of(shared));

        assertEquals(2.0 / 61.0, fused.get(0).getScore(), 0.0001, "双路 rank1 = 2/61");
    }

    private String key(AiKbHit h) {
        return h.getSourceType() + ":" + h.getSourceId();
    }

    private AiKbHit hit(String sourceType, Long sourceId, String title) {
        AiKbHit h = new AiKbHit();
        h.setSourceType(sourceType);
        h.setSourceId(sourceId);
        h.setTitle(title);
        h.setScore(1.0);
        return h;
    }
}
