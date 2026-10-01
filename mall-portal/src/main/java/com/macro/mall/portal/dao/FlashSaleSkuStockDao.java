package com.macro.mall.portal.dao;

import org.apache.ibatis.annotations.Param;

/**
 * 秒杀模块 SKU 库存自定义 DAO
 * Created by jiechu555 on 2026/10/01.
 */
public interface FlashSaleSkuStockDao {

    /**
     * 原子递增锁定库存：单条 UPDATE 自增，避免 mall 原有读-改-写丢更新竞态
     *
     * @return 影响行数（0 = sku 不存在）
     */
    int increaseLockStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
