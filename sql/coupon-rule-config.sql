-- ============================================================
-- 优惠券叠加规则配置表
-- 用途：把下单时的券叠加限制从代码中抽离为后台可配置项
--   max_stack_count    单笔订单最多可叠加使用的优惠券张数
--   max_discount_ratio 多张券叠加时，总抵扣金额占商品总额的上限百分比
--                      （如 80 表示最多抵扣 80%，用户实付不低于 20%）
-- 说明：读不到配置时后端回落到默认值（3 张 / 80%），因此本表未创建也不会影响下单。
-- ============================================================

CREATE TABLE IF NOT EXISTS `coupon_rule_config`  (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '配置ID（自增主键）',
  `rule_key`    varchar(50)  NOT NULL                COMMENT '规则标识：max_stack_count-最大叠加张数、max_discount_ratio-总抵扣上限百分比',
  `rule_value`  varchar(100) NOT NULL                COMMENT '规则值（字符串存储，按规则语义解析）',
  `enabled`     tinyint      NOT NULL DEFAULT 1      COMMENT '是否启用该限制(0否1是)',
  `remark`      varchar(200)      NULL DEFAULT NULL  COMMENT '规则说明（后台展示用）',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_rule_key`(`rule_key` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '优惠券叠加规则配置表' ROW_FORMAT = DYNAMIC;

-- 初始数据（ON DUPLICATE KEY UPDATE 保证重复执行脚本不会报错或覆盖已调整的值）
INSERT INTO `coupon_rule_config` (`rule_key`, `rule_value`, `enabled`, `remark`) VALUES
  ('max_stack_count',    '3',  1, '单笔订单最多可叠加使用的优惠券张数（1~10）'),
  ('max_discount_ratio', '80', 1, '多张券叠加时总抵扣占商品总额的上限百分比（1~100，填100表示不限制）')
ON DUPLICATE KEY UPDATE `rule_key` = VALUES(`rule_key`);
