package com.macro.mall.portal.ai;

import com.macro.mall.portal.ai.domain.AiKbHit;
import com.macro.mall.portal.ai.service.impl.AiKbServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 混合检索 commit 4 验收：
 * ① RRF 融合算法：双路命中的文档应排在单路命中前面（共识信号）；
 * ② 嵌入不可用时自动降级为纯 BM25（ai.enabled=false 是测试环境默认值）。
 */
@SpringBootTest
class AiHybridSearchTest {

    @Autowired
    private com.macro.mall.portal.ai.service.AiKbService aiKbService;

    // ==================== ① RRF 融合算法单元测试 ====================

    @Test
    void RRF融合_双路命中排前_单路按排名排后() {
        AiKbHit a1 = hit("PRODUCT", 1L, "BM25第一名");  // BM25 rank=1, vector rank=3
        AiKbHit a2 = hit("PRODUCT", 2L, "BM25第二名");  // BM25 rank=2, 不在 vector
        AiKbHit a3 = hit("PRODUCT", 3L, "BM25第三名");  // 不在 BM25, vector rank=1
        AiKbHit a4 = hit("FAQ", 10L, "BM25第四名");     // BM25 rank=3, vector rank=2

        List<AiKbHit> bm25 = List.of(a1, a2, a4);      // rank 1,2,3
        List<AiKbHit> vector = List.of(a3, a4, a1);    // rank 1,2,3

        List<AiKbHit> fused = AiKbServiceImpl.rrfFuse(bm25, vector);

        // a1: 1/61 + 1/63 = 0.03227（双路，含 BM25 rank1）
        // a4: 1/63 + 1/62 = 0.03200（双路，排名较均衡）
        // a3: 1/61 = 0.01639（单路 vector rank1）
        // a2: 1/62 = 0.01613（单路 BM25 rank2）
        // 双路命中 > 单路命中；双路之间 BM25 rank1 的 a1 略胜
        assertEquals("PRODUCT:1", fused.get(0).getSourceType() + ":" + fused.get(0).getSourceId(),
                "双路命中且含 BM25 rank1 的 a1 应排第一（RRF=0.03227 > a4 的 0.03200）");
        assertEquals("FAQ:10", fused.get(1).getSourceType() + ":" + fused.get(1).getSourceId(),
                "双路命中的 a4 应排第二");
        // 双路命中（a1、a4）的分数都应高于任何单路命中（a3、a2）
        assertTrue(fused.get(0).getScore() > fused.get(2).getScore(),
                "双路命中分数应高于单路命中");
        assertTrue(fused.get(1).getScore() > fused.get(2).getScore(),
                "双路命中分数应高于单路命中");
        assertEquals(4, fused.size(), "融合后应无去重丢失");
    }

    @Test
    void RRF融合_分数等于两路倒数排名之和() {
        AiKbHit shared = hit("PRODUCT", 1L, "shared");
        AiKbHit bm25Only = hit("FAQ", 2L, "bm25-only");

        List<AiKbHit> bm25 = List.of(shared, bm25Only);
        List<AiKbHit> vector = List.of(shared);

        List<AiKbHit> fused = AiKbServiceImpl.rrfFuse(bm25, vector);

        // shared: 1/(60+1) + 1/(60+1) = 2/61
        double expected = 2.0 / 61.0;
        assertEquals(expected, fused.get(0).getScore(), 0.0001,
                "双路 rank1 的 RRF 分数应为 2/61");
    }

    // ==================== ② 降级行为集成测试 ====================

    @Test
    void 嵌入不可用时_混合检索降级为纯BM25() {
        // 测试环境 ai.enabled=false（默认），embeddingClient=null
        List<AiKbHit> hybrid = aiKbService.searchHybrid("小米", 5);
        List<AiKbHit> bm25 = aiKbService.searchByKeyword("小米", 5);

        // 降级时 hybrid 应等价于 BM25（同样的前几条）
        assertTrue(hybrid.size() > 0, "降级后仍应有 BM25 结果");
        assertEquals(bm25.get(0).getSourceId(), hybrid.get(0).getSourceId(),
                "降级时第一名应与纯 BM25 一致");
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
