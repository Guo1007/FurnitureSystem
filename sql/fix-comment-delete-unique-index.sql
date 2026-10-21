-- =====================================================================
-- 修复：评价删除后无法重新评价 / 追评序号冲突
--
-- 背景：
--   goods_comment 的唯一索引 uk_order_user_goods(order_id, user_id, goods_id)
--   不含删除标记，而删除走的是逻辑删（deleted=1 / user_deleted=1）。
--   逻辑删除后唯一键仍被占用，用户重新评价必然撞索引，永远提示「您已评价过该商品」。
--
-- 方案：
--   新增生成列 uk_key，仅在「未删除」时才有值；唯一索引建在 uk_key 上。
--   已删除记录 uk_key 为 NULL，MySQL 唯一索引允许多个 NULL，约束自然释放。
--
-- 执行须知：
--   1. 先在测试库执行，确认 AUTO_INCREMENT 与数据量无异常后再上生产。
--   2. 建议低峰期执行；goods_comment 数据量小时为秒级。
--   3. 本脚本不删除任何业务数据，只改索引结构。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 步骤 0：前置检查（应当返回 0 行）
-- 如果返回了数据，说明已存在未删除的重复评价，需先人工处理，否则第 2 步会失败。
-- ---------------------------------------------------------------------
SELECT order_id, user_id, goods_id, COUNT(*) AS cnt
FROM goods_comment
WHERE deleted = 0
  AND user_deleted = 0
GROUP BY order_id, user_id, goods_id
HAVING cnt > 1;

-- ---------------------------------------------------------------------
-- 步骤 1：新增生成列（不影响现有数据，也不影响 MyBatis-Plus 的 insert）
-- ---------------------------------------------------------------------
ALTER TABLE `goods_comment`
  ADD COLUMN `uk_key` VARCHAR(64)
    GENERATED ALWAYS AS (
      IF(`deleted` = 0 AND `user_deleted` = 0,
         CONCAT(`order_id`, '-', `user_id`, '-', `goods_id`),
         NULL)
    ) STORED
    COMMENT '唯一约束键：仅未删除时参与唯一约束，逻辑删除后置 NULL 以释放索引';

-- ---------------------------------------------------------------------
-- 步骤 2：先建新索引（此时旧索引还在，约束不会出现空窗期）
-- ---------------------------------------------------------------------
ALTER TABLE `goods_comment`
  ADD UNIQUE INDEX `uk_order_user_goods_v2` (`uk_key`);

-- ---------------------------------------------------------------------
-- 步骤 3：删除旧唯一索引
-- ---------------------------------------------------------------------
ALTER TABLE `goods_comment`
  DROP INDEX `uk_order_user_goods`;

-- ---------------------------------------------------------------------
-- 步骤 4：验收
--   (a) 生成列已建：应当能看到 uk_key
--   (b) 索引已替换：应当只有 uk_order_user_goods_v2
-- ---------------------------------------------------------------------
SHOW COLUMNS FROM `goods_comment` LIKE 'uk_key';
SHOW INDEX FROM `goods_comment` WHERE Key_name IN ('uk_order_user_goods', 'uk_order_user_goods_v2');


-- =====================================================================
-- 回滚脚本（仅在需要时执行）
-- =====================================================================
-- ALTER TABLE `goods_comment`
--   ADD UNIQUE INDEX `uk_order_user_goods` (`order_id`, `user_id`, `goods_id`);
-- ALTER TABLE `goods_comment` DROP INDEX `uk_order_user_goods_v2`;
-- ALTER TABLE `goods_comment` DROP COLUMN `uk_key`;
-- 注意：回滚前必须确认没有「同一订单同一商品存在多条未删除评价」的数据，
-- 否则重建旧索引会失败。
