package com.macro.mall.portal.seckill;

import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.portal.dao.PortalOrderDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * updateSkuStock 库存守卫回归测试（存量漏洞修复的验收）：
 * 修复前：stock 小于购买数量时 SQL 照样执行，stock 扣成负数（超卖）；
 * 修复后：守卫条件使该行不参与更新，配合调用方影响行数校验触发事务回滚。
 * 使用既有 SKU 98 做现场修改与恢复，不留测试数据。
 */
@SpringBootTest
class UpdateSkuStockGuardTest {

    private static final Long SKU_ID = 98L;

    @Autowired
    private PortalOrderDao portalOrderDao;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;

    private Integer originalStock;
    private Integer originalLockStock;

    @BeforeEach
    void setUp() {
        PmsSkuStock sku = skuStockMapper.selectByPrimaryKey(SKU_ID);
        originalStock = sku.getStock();
        originalLockStock = sku.getLockStock();
        // 现场改为小库存，构造守卫触发条件
        setSku(5, 5);
    }

    @AfterEach
    void tearDown() {
        setSku(originalStock, originalLockStock);
    }

    @Test
    void 库存不足时该行不被更新_库存永不入负() {
        PmsSkuStock sku = skuStockMapper.selectByPrimaryKey(SKU_ID);
        assertEquals(Integer.valueOf(5), sku.getStock());

        // 购买 10 件但库存只有 5：守卫生效，影响行数应为 0
        int count = portalOrderDao.updateSkuStock(List.of(item(10)));
        assertEquals(0, count, "库存不足时该 SKU 行不应被更新");

        sku = skuStockMapper.selectByPrimaryKey(SKU_ID);
        assertEquals(Integer.valueOf(5), sku.getStock(), "stock 不得被扣成负数");
        assertEquals(Integer.valueOf(5), sku.getLockStock(), "未被更新的行 lock_stock 也不应变化");
    }

    @Test
    void 库存充足时正常扣减() {
        int count = portalOrderDao.updateSkuStock(List.of(item(3)));
        assertEquals(1, count, "库存充足时应正常更新");

        PmsSkuStock sku = skuStockMapper.selectByPrimaryKey(SKU_ID);
        assertEquals(Integer.valueOf(2), sku.getStock());
        assertEquals(Integer.valueOf(2), sku.getLockStock());
    }

    private OmsOrderItem item(int quantity) {
        OmsOrderItem item = new OmsOrderItem();
        item.setProductSkuId(SKU_ID);
        item.setProductQuantity(quantity);
        return item;
    }

    private void setSku(int stock, int lockStock) {
        PmsSkuStock update = new PmsSkuStock();
        update.setId(SKU_ID);
        update.setStock(stock);
        update.setLockStock(lockStock);
        skuStockMapper.updateByPrimaryKeySelective(update);
    }
}
