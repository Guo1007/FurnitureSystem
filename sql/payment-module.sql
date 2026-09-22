-- ----------------------------
-- 支付流水表 (payment) —— 支付宝支付
-- 数据库：furniture-system  字符集：utf8mb4
-- 说明：本文件为新增表，可单独执行，不影响已有 furniture-system.sql
-- ----------------------------

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `payment`;
CREATE TABLE `payment`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '支付流水ID（自增主键）',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `user_id` bigint NOT NULL COMMENT '下单用户ID',
  `pay_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商户业务单号(支付宝out_trade_no，唯一)',
  `trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付宝交易号(trade_no，回调后回填)',
  `total_amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '支付金额（元）',
  `channel` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'alipay' COMMENT '支付渠道：alipay',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-待支付，1-已支付，2-已关闭',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `pay_time` datetime NULL DEFAULT NULL COMMENT '支付完成时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删/1已删）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_pay_no`(`pay_no`) USING BTREE,
  INDEX `idx_order_id`(`order_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付流水表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;