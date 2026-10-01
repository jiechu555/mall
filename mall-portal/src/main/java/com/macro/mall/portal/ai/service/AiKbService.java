package com.macro.mall.portal.ai.service;

import com.macro.mall.portal.ai.domain.AiKbHit;

import java.util.List;

/**
 * AI 客服知识库：文档构建、索引管理与关键词检索（本 commit 仅 BM25，向量化在后续 commit）
 * Created by jiechu555 on 2026/10/01.
 */
public interface AiKbService {

    /**
     * 全量重建知识库（幂等：删索引→建 mapping→批量写入→刷新）
     *
     * @return 写入文档数
     */
    int rebuild();

    /**
     * 当前文档总数
     */
    long docCount();

    /**
     * BM25 关键词检索（title 两倍权重 + content）
     *
     * @return 按相关度排序的命中列表
     */
    List<AiKbHit> searchByKeyword(String keyword, int topK);
}
