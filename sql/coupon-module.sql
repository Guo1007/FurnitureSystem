-- ----------------------------
-- 优惠券模块建表脚本 (coupon / user_coupon)
-- 数据库：furniture-system  字符集：utf8mb4
-- 说明：本文件为新增表，可单独执行，不影响已有 furniture-system.sql
-- ----------------------------

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 优惠券模板表
-- ----------------------------
DROP TABLE IF EXISTS `coupon`;
CREATE TABLE `coupon`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '优惠券ID（自增主键）',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '券名称',
  `type` tinyint NOT NULL COMMENT '券类型：1-满减券，2-折扣券，3-无门槛券',
  `min_threshold` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛金额（满xx可用，0表示无门槛）',
  `amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '减免金额（满减/无门槛券使用）',
  `discount` decimal(3, 2) NULL DEFAULT NULL COMMENT '折扣率（折扣券使用，如0.80表示8折）',
  `cap_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '折扣券最高优惠上限（可空）',
  `scope` tinyint NOT NULL DEFAULT 0 COMMENT '适用范围：0-全场，1-按分类',
  `type_id` bigint NULL DEFAULT NULL COMMENT 'scope=1时的家具分类ID（可空）',
  `total_count` int NULL DEFAULT NULL COMMENT '发放总量（NULL表示不限）',
  `per_user_limit` int NOT NULL DEFAULT 1 COMMENT '每人限领数',
  `claim_start` datetime NULL DEFAULT NULL COMMENT '领取开始时间（NULL表示不限领取时间）',
  `claim_end` datetime NULL DEFAULT NULL COMMENT '领取结束时间（NULL表示不限领取时间）',
  `valid_type` tinyint NOT NULL DEFAULT 1 COMMENT '有效期模式：1-固定有效期，2-领取后N天',
  `valid_start` datetime NULL DEFAULT NULL COMMENT '固定有效期开始（valid_type=1时使用）',
  `valid_end` datetime NULL DEFAULT NULL COMMENT '固定有效期结束（valid_type=1时使用）',
  `valid_days` int NULL DEFAULT NULL COMMENT '领取后有效天数（valid_type=2时使用）',
  `target_type` tinyint NOT NULL DEFAULT 0 COMMENT '领取人群：0-不限，1-新用户，2-老用户',
  `target_days` int NULL DEFAULT NULL COMMENT '新/老用户判定天数阈值',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删/1已删）',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '优惠券模板表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- 用户领取记录表
-- ----------------------------
DROP TABLE IF EXISTS `user_coupon`;
CREATE TABLE `user_coupon`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID（自增主键）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `coupon_id` bigint NOT NULL COMMENT '优惠券模板ID',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-未用，1-已用，2-已过期',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '过期时间（领券时按有效期模式算好）',
  `got_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  `use_time` datetime NULL DEFAULT NULL COMMENT '使用时间（可空）',
  `order_id` bigint NULL DEFAULT NULL COMMENT '使用的订单ID（可空）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_coupon`(`user_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_coupon_id`(`coupon_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户领取优惠券记录表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- 订单表增加优惠券相关字段（用于下单抵扣与归券）
-- ----------------------------
ALTER TABLE `order`
  ADD COLUMN `coupon_id` bigint NULL COMMENT '使用的优惠券模板ID' AFTER `total_price`,
  ADD COLUMN `coupon_discount` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '优惠金额' AFTER `coupon_id`;