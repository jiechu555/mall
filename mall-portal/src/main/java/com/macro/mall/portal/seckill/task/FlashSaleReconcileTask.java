package com.macro.mall.portal.seckill.task;

import com.macro.mall.portal.seckill.domain.FlashSaleReconcileItem;
import com.macro.mall.portal.seckill.service.FlashSaleReconcileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀对账定时任务：每 5 分钟跑一轮守恒校验与失败受理单清扫
 * Created by jiechu555 on 2026/10/01.
 */
@Component
@ConditionalOnProperty(name = "seckill.enabled", havingValue = "true", matchIfMissing = true)
public class FlashSaleReconcileTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlashSaleReconcileTask.class);

    private final FlashSaleReconcileService flashSaleReconcileService;

    public FlashSaleReconcileTask(FlashSaleReconcileService flashSaleReconcileService) {
        this.flashSaleReconcileService = flashSaleReconcileService;
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void reconcile() {
        try {
            List<FlashSaleReconcileItem> report = flashSaleReconcileService.reconcileOnce();
            long abnormal = report.stream()
                    .filter(item -> !"NONE".equals(item.getAction()) && !"STUCK_CLOSED".equals(item.getAction()))
                    .count();
            if (abnormal > 0) {
                LOGGER.warn("秒杀对账完成：{} 项中 {} 项存在差异或已处置", report.size(), abnormal);
            } else {
                LOGGER.info("秒杀对账完成：{} 项全部守恒", report.size());
            }
        } catch (Exception e) {
            // 定时任务绝不能把异常抛给调度器
            LOGGER.error("秒杀对账任务执行失败", e);
        }
    }
}
