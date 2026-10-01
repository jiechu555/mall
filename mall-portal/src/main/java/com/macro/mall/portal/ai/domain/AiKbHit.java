package com.macro.mall.portal.ai.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识库检索单条命中
 * Created by jiechu555 on 2026/10/01.
 */
@Data
public class AiKbHit implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源类型：PRODUCT / FAQ */
    private String sourceType;
    /** 来源 id：商品 id 或 FAQ 序号 */
    private Long sourceId;
    /** 文档标题（商品名 / FAQ 问题） */
    private String title;
    /** 相关度得分 */
    private Double score;
    /** 内容摘要（前 120 字） */
    private String snippet;
}
