-- ============================================================
-- 索引优化脚本（可重复执行）
--
-- 背景：furniture 表除 type_id 外没有任何二级索引，按 stock / sale_count /
--       brand / is_recommended 查询（含排序）都会全表扫 + filesort；
--       order 表缺 (user_id, deleted, user_deleted, create_time) 复合索引，
--       「我的订单」分页需先回表过滤再排序。
--       数据量小时不明显，数据量上来后这两处是主要慢查询来源。
--
-- 用法：在 furniture-system 库中执行本脚本即可，已存在的索引会自动跳过。
-- ============================================================

-- ---------- furniture：配合逻辑删除的过滤/排序索引 ----------

-- 库存筛选（如「仅看有货」）与库存排序
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'furniture' AND INDEX_NAME = 'idx_del_stock');
SET @sql := IF(@exist = 0, 'CREATE INDEX idx_del_stock ON furniture(deleted, stock)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 销量榜 / 热销排序
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'furniture' AND INDEX_NAME = 'idx_del_sale');
SET @sql := IF(@exist = 0, 'CREATE INDEX idx_del_sale ON furniture(deleted, sale_count DESC)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 按品牌筛选
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'furniture' AND INDEX_NAME = 'idx_del_brand');
SET @sql := IF(@exist = 0, 'CREATE INDEX idx_del_brand ON furniture(deleted, brand)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 首页「编辑推荐」
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'furniture' AND INDEX_NAME = 'idx_del_rec');
SET @sql := IF(@exist = 0, 'CREATE INDEX idx_del_rec ON furniture(deleted, is_recommended)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- order：我的订单分页 ----------

-- 覆盖「按用户查 + 排除已删 + 按下单时间倒序」，避免回表后再 filesort
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order' AND INDEX_NAME = 'idx_user_del_ctime');
SET @sql := IF(@exist = 0,
               'CREATE INDEX idx_user_del_ctime ON `order`(user_id, deleted, user_deleted, create_time DESC)',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 执行完成后可用下面语句确认索引已建立：
-- SHOW INDEX FROM furniture;
-- SHOW INDEX FROM `order`;
