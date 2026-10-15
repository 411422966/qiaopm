-- ============================================
-- R3.0-D：多仓管理（最小骨架）
-- 2026-08-14
-- 目的：
--   1. 新增 wms_warehouse 表（仓库主数据）
--   2. stock_inventory 加 warehouse_id 字段（保持向后兼容：默认 = storeId 对应的"主仓"）
--   3. 新增 wms_stock_transfer 表（调拨单）
--   4. 演示数据：每个门店默认一个"主仓"
-- ============================================

DROP TABLE IF EXISTS wms_warehouse;
CREATE TABLE wms_warehouse (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    brand_id        BIGINT       DEFAULT NULL,
    store_id        BIGINT       DEFAULT NULL COMMENT '归属门店（NULL=总部中央仓）',
    warehouse_code  VARCHAR(32)  NOT NULL COMMENT '仓库编码',
    warehouse_name  VARCHAR(64)  NOT NULL COMMENT '仓库名称',
    warehouse_type  VARCHAR(16)  DEFAULT 'STORE' COMMENT 'STORE门店仓/CENTRAL中央仓/TRANSIT中转仓',
    location        VARCHAR(255) DEFAULT NULL COMMENT '地址',
    manager_id      BIGINT       DEFAULT NULL COMMENT '仓库管理员',
    manager_name    VARCHAR(64)  DEFAULT NULL,
    enabled         TINYINT      DEFAULT 1,
    remark          VARCHAR(500) DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, warehouse_code),
    INDEX idx_store (store_id, enabled, deleted),
    INDEX idx_brand (brand_id, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓库主数据';

-- stock_inventory 加 warehouse_id（向后兼容：已有库存视作"主仓"）
ALTER TABLE stock_inventory
    ADD COLUMN warehouse_id BIGINT DEFAULT NULL COMMENT '仓库ID' AFTER store_id,
    ADD KEY idx_warehouse (warehouse_id);

DROP TABLE IF EXISTS wms_stock_transfer;
CREATE TABLE wms_stock_transfer (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    transfer_no     VARCHAR(64)  NOT NULL COMMENT '调拨单号',
    product_id      BIGINT       NOT NULL,
    product_name    VARCHAR(128) DEFAULT NULL,
    batch_no        VARCHAR(64)  DEFAULT NULL,
    quantity        INT          NOT NULL,
    from_warehouse_id BIGINT     NOT NULL COMMENT '源仓库',
    to_warehouse_id   BIGINT     NOT NULL COMMENT '目标仓库',
    from_store_id   BIGINT       DEFAULT NULL,
    to_store_id     BIGINT       DEFAULT NULL,
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/SHIPPED/RECEIVED/CANCELLED',
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    shipped_at      DATETIME     DEFAULT NULL,
    received_at     DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    UNIQUE KEY uk_tenant_transfer_no (tenant_id, transfer_no),
    INDEX idx_product (product_id, status),
    INDEX idx_from (from_warehouse_id, status),
    INDEX idx_to (to_warehouse_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓库调拨单';

-- 演示数据：每个已有门店默认一个门店仓
INSERT INTO wms_warehouse (tenant_id, brand_id, store_id, warehouse_code, warehouse_name, warehouse_type, manager_name, enabled, operator_name)
SELECT 1, brand_id, id, CONCAT('WH-STORE-', id), CONCAT(store_name, '-主仓'), 'STORE', manager_name, 1, 'system'
FROM pos_store
WHERE deleted = 0
ON DUPLICATE KEY UPDATE warehouse_name = VALUES(warehouse_name);

-- 总部中央仓
INSERT IGNORE INTO wms_warehouse (tenant_id, brand_id, store_id, warehouse_code, warehouse_name, warehouse_type, manager_name, enabled, operator_name)
VALUES (1, NULL, NULL, 'WH-CENTRAL-001', '总部中央仓', 'CENTRAL', '总部物管', 1, 'system');

-- 库存回填：默认指向门店主仓
UPDATE stock_inventory si
INNER JOIN wms_warehouse w ON w.store_id = si.store_id AND w.warehouse_type = 'STORE'
SET si.warehouse_id = w.id
WHERE si.warehouse_id IS NULL;

-- 权限
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'wms:warehouse', '仓库管理', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='biz:inventory') t LIMIT 1), 60;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'wms:transfer', '调拨管理', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='biz:inventory') t LIMIT 1), 61;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN')
  AND p.permission_code IN ('wms:warehouse','wms:transfer');

-- 验证
SELECT
    (SELECT COUNT(*) FROM wms_warehouse WHERE deleted=0) AS warehouse_count,
    (SELECT COUNT(*) FROM stock_inventory WHERE warehouse_id IS NOT NULL) AS inventory_with_warehouse;