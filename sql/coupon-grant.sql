-- ============================================================
-- 定向发放优惠券 · 表结构迁移（可重复执行）
--
-- 背景：券原先只能由用户在领券中心主动领取（claim）。新增「定向发放」后，
--       管理员可以把券直接发到指定用户账下（补偿/关怀），并通知用户。
--
-- 两个新列：
--   coupon.issue_type      发放方式：1-公开领取，2-定向发放
--   user_coupon.source     来源：0-用户领取，1-管理员发放
--
-- 为什么「定向发放」要做成券模板的属性，而不是对任意券的动作：
--   发券的限量/限领靠 Redis 两个计数器（coupon:count:{券ID}、coupon:user:{券ID}:{用户ID}）
--   维护，而这两个 key 只在「不存在时」才从数据库回种。若绕过 claim 直接插 user_coupon，
--   计数器不会增加 —— 后果不是显示不准，而是**能超发**：
--     活动限量 100、已发 50 → 直插 10 张 → 计数器仍是 50 → 用户还能再领 50 → 实际发出 110 张
--   把定向券做成独立类型后，它不进公开领取流程，也就不碰这两个计数器。
--
-- 用法：在 furniture-system 库中执行本脚本即可，已存在的列会自动跳过。
-- ============================================================

-- ---------- coupon：发放方式 ----------

SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon' AND COLUMN_NAME = 'issue_type');
SET @sql := IF(@exist = 0,
               'ALTER TABLE `coupon` ADD COLUMN `issue_type` tinyint NOT NULL DEFAULT 1 COMMENT ''发放方式：1-公开领取(可被用户在领券中心领取)，2-定向发放(只能由管理员发放)'' AFTER `stackable`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- user_coupon：来源 ----------

-- 用途：区分「用户自己领的」与「管理员发的」。这是查询「我补偿过谁」的前提，
--       也让未来任何按来源区分的逻辑（比如统计补偿成本）有据可依。
-- 默认 0 对存量数据语义正确：此前所有记录都是用户领取产生的。
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_coupon' AND COLUMN_NAME = 'source');
SET @sql := IF(@exist = 0,
               'ALTER TABLE `user_coupon` ADD COLUMN `source` tinyint NOT NULL DEFAULT 0 COMMENT ''来源：0-用户领取，1-管理员定向发放'' AFTER `order_id`',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 执行完成后可用下面语句确认列已建立：
-- SHOW COLUMNS FROM coupon LIKE 'issue_type';
-- SHOW COLUMNS FROM user_coupon LIKE 'source';
