package com.macro.mall.portal.ai;

import com.macro.mall.portal.ai.domain.AiKbHit;
import com.macro.mall.portal.ai.service.AiKbService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识库 commit 1 验收：索引重建（38 商品 + 16 FAQ）与 BM25 关键词检索。
 * 走本机真实 ES（7.17.3 + ik），rebuild 幂等可重复执行。
 * 标记 es 组：CI 暂排除（runner 上 ES 镜像拉取约 9 分钟且内存不稳），本地必跑。
 */
@Tag("es")
@SpringBootTest
class AiKbIndexAndSearchTest {

    @Autowired
    private AiKbService aiKbService;

    @BeforeEach
    void rebuildOnce() {
        aiKbService.rebuild();
    }

    @Test
    void 重建知识库_文档覆盖商品与FAQ() {
        long count = aiKbService.docCount();
        // 上架商品（publish_status=1）约 20 篇 + FAQ 16 篇；断言下限 36，商品上架数只增不减
        assertTrue(count >= 36, "文档数应覆盖上架商品+16 FAQ，实际 " + count);
    }

    @Test
    void BM25检索_型号词命中商品文档() {
        List<AiKbHit> hits = aiKbService.searchByKeyword("小米", 5);
        assertTrue(hits.size() > 0, "小米 应命中商品");
        assertTrue(hits.stream().anyMatch(h -> "PRODUCT".equals(h.getSourceType()) && h.getTitle().contains("小米")),
                "应命中小米系商品，实际 " + hits);
    }

    @Test
    void BM25检索_白话问法命中FAQ() {
        List<AiKbHit> hits = aiKbService.searchByKeyword("退货", 3);
        assertTrue(hits.stream().anyMatch(h -> "FAQ".equals(h.getSourceType())),
                "退货 应命中售后 FAQ，实际 " + hits);
    }

    @Test
    void BM25检索_秒杀规则问答() {
        List<AiKbHit> hits = aiKbService.searchByKeyword("秒杀 受理", 3);
        assertTrue(hits.stream().anyMatch(h -> "FAQ".equals(h.getSourceType()) && h.getTitle().contains("受理")),
                "秒杀受理中 应命中 FAQ，实际 " + hits);
        assertEquals(3, hits.size(), "topK 应生效");
    }

    @Test
    void 混合检索_嵌入不可用时降级为纯BM25() {
        List<AiKbHit> hybrid = aiKbService.searchHybrid("小米", 5);
        List<AiKbHit> bm25 = aiKbService.searchByKeyword("小米", 5);
        assertTrue(hybrid.size() > 0, "降级后仍应有 BM25 结果");
        assertEquals(bm25.get(0).getSourceId(), hybrid.get(0).getSourceId(),
                "降级时第一名应与纯 BM25 一致");
    }
}
