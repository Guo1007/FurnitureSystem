-- ============================================================
-- 购物车模块
-- 用途：把购物车从浏览器 localStorage 迁到服务端，使购物车跨设备可用；
--       价格与库存一律实时查库，不再使用加购时的快照
-- 依赖：需在 furniture-system.sql 之后执行（引用 furniture / sku 的主键）
-- ============================================================

CREATE TABLE IF NOT EXISTS `cart_item`  (
  `id`           bigint   NOT NULL AUTO_INCREMENT COMMENT '购物车项ID（自增主键）',
  `user_id`      bigint   NOT NULL                COMMENT '所属用户ID',
  `furniture_id` bigint   NOT NULL                COMMENT '家具ID',
  `sku_id`       bigint   NOT NULL DEFAULT 0      COMMENT 'SKU ID；无规格商品存 0，不可用 NULL（见文件头说明）',
  `quantity`     int      NOT NULL DEFAULT 1      COMMENT '购买数量',
  `checked`      tinyint  NOT NULL DEFAULT 1      COMMENT '结算勾选状态(0未勾选1勾选)',
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入购物车时间',
  `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_furniture_sku`(`user_id`, `furniture_id`, `sku_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '购物车项' ROW_FORMAT = Dynamic;

-- ------------------------------------------------------------
-- 为什么 sku_id 用 0 而不是 NULL：
--   MySQL 唯一索引对 NULL 不去重，(user_id, furniture_id, NULL) 可以重复插入任意多行，
--   同一个无规格商品会被反复加购成多行。用 0 占位才能让 uk 真正挡住重复。
-- ------------------------------------------------------------
