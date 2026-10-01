-- 秒杀模块二开：新增秒杀订单记录表（基于 mall 二次开发）
-- 执行前提：已导入 document/sql/mall.sql
-- 设计说明见仓库二开文档；唯一键四列（活动,场次,商品,会员）同时承担：
--   限购的 DB 硬约束 / MQ 消费幂等键 / 对账任务账本 三重职责
CREATE TABLE IF NOT EXISTS `sms_flash_promotion_order`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '编号',
  `flash_promotion_id` bigint(20) NOT NULL COMMENT '秒杀活动id',
  `flash_promotion_session_id` bigint(20) NOT NULL COMMENT '秒杀场次id',
  `product_id` bigint(20) NOT NULL COMMENT '商品id',
  `product_sku_id` bigint(20) NULL DEFAULT NULL COMMENT '商品sku编号',
  `member_id` bigint(20) NOT NULL COMMENT '会员id',
  `member_username` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '会员账号',
  `order_id` bigint(20) NULL DEFAULT NULL COMMENT '关联oms_order订单id',
  `order_sn` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '订单编号',
  `quantity` int(11) NOT NULL DEFAULT 1 COMMENT '购买数量',
  `flash_promotion_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '成交秒杀价',
  `status` int(1) NOT NULL DEFAULT 0 COMMENT '状态：0->受理中；1->已生成订单；2->落库失败已回补库存；3->超时关闭',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_promotion_session_product_member` (`flash_promotion_id`, `flash_promotion_session_id`, `product_id`, `member_id`) COMMENT '同活动同场次同商品同人仅一单，限购硬约束'
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '秒杀订单记录表' ROW_FORMAT = DYNAMIC;
