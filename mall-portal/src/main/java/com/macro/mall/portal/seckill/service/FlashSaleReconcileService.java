package com.macro.mall.portal.seckill.service;

import com.macro.mall.portal.seckill.domain.FlashSaleReconcileItem;

import java.util.List;

/**
 * 秒杀对账服务：守恒式校验与失败受理单清扫
 * Created by jiechu555 on 2026/10/01.
 */
public interface FlashSaleReconcileService {

    /**
     * 执行一轮对账：
     * ① 清扫卡死的受理单（status=0 超过阈值 → 回补库存并置 status=2）
     * ② 守恒校验每个（活动,场次,商品）：Redis 余量 + status=1 件数 + status=0 件数 = 总量
     *    差异 > 0 且场次已结束 → 自动回补；进行中场次只告警（避免在途请求窗口误回补）
     *
     * @return 本轮对账明细（含无差异项，便于留档）
     */
    List<FlashSaleReconcileItem> reconcileOnce();
}
