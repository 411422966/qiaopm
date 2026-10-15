-- ============================================
-- 项目自动扣减完善
-- P0-M06-007：项目自动扣减完善
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 项目耗材绑定规则
-- ============================================
DROP TABLE IF EXISTS biz_service_material_rule;
CREATE TABLE biz_service_material_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    service_id BIGINT NOT NULL COMMENT '服务ID',
    product_id BIGINT NOT NULL COMMENT '耗材ID',
    standard_quantity DECIMAL(15,2) DEFAULT 0 COMMENT '标准用量',
    unit VARCHAR(16) COMMENT '单位(ml/g/片等)',
    waste_rate DECIMAL(5,2) DEFAULT 0.05 COMMENT '损耗率(0-1)',
    required TINYINT DEFAULT 1 COMMENT '是否必用: 0-否, 1-是',
    fefo_enabled TINYINT DEFAULT 1 COMMENT '是否启用FEFO',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_service_product (service_id, product_id),
    INDEX idx_service_id (service_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目耗材绑定规则';

-- ============================================
-- 2. 项目自动扣减审计日志
-- ============================================
DROP TABLE IF EXISTS biz_service_material_deduct_log;
CREATE TABLE biz_service_material_deduct_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    order_id BIGINT COMMENT '关联订单ID',
    order_no VARCHAR(64) COMMENT '订单号',
    order_item_id BIGINT COMMENT '订单明细ID',
    service_id BIGINT NOT NULL COMMENT '服务ID',
    service_name VARCHAR(128) COMMENT '服务名称',
    product_id BIGINT NOT NULL COMMENT '耗材ID',
    product_name VARCHAR(128) COMMENT '耗材名称',
    inventory_id BIGINT NOT NULL COMMENT '批次ID',
    batch_no VARCHAR(64) COMMENT '批次号',
    planned_quantity DECIMAL(15,2) DEFAULT 0 COMMENT '计划用量',
    actual_quantity DECIMAL(15,2) DEFAULT 0 COMMENT '实际扣减数量',
    unit_cost DECIMAL(15,2) DEFAULT 0 COMMENT '单位成本',
    total_cost DECIMAL(15,2) DEFAULT 0 COMMENT '总成本',
    deduct_strategy VARCHAR(32) DEFAULT 'fefo' COMMENT '扣减策略: fefo/fifo/manual/substitute',
    before_stock INT DEFAULT 0 COMMENT '扣减前批次库存',
    after_stock INT DEFAULT 0 COMMENT '扣减后批次库存',
    is_substitute TINYINT DEFAULT 0 COMMENT '是否使用替代',
    original_product_id BIGINT COMMENT '替代的原始耗材ID',
    status VARCHAR(16) DEFAULT 'success' COMMENT '扣减状态: success/failed/partial',
    failure_reason VARCHAR(500) COMMENT '失败原因',
    employee_id BIGINT COMMENT '技师ID',
    employee_name VARCHAR(64) COMMENT '技师姓名',
    member_id BIGINT COMMENT '客户ID',
    retry_count INT DEFAULT 0 COMMENT '乐观锁重试次数',
    transaction_id VARCHAR(64) COMMENT '事务ID',
    deduct_time DATETIME COMMENT '扣减时间',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_order_id (order_id),
    INDEX idx_service_id (service_id),
    INDEX idx_product_id (product_id),
    INDEX idx_inventory_id (inventory_id),
    INDEX idx_store_id (store_id),
    INDEX idx_status (status),
    INDEX idx_deduct_time (deduct_time),
    INDEX idx_transaction_id (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目自动扣减日志';

-- ============================================
-- 3. 库存不足告警
-- ============================================
DROP TABLE IF EXISTS biz_stock_shortage_log;
CREATE TABLE biz_stock_shortage_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    order_id BIGINT COMMENT '订单ID',
    order_no VARCHAR(64) COMMENT '订单号',
    service_id BIGINT COMMENT '服务ID',
    service_name VARCHAR(128) COMMENT '服务名称',
    product_id BIGINT NOT NULL COMMENT '耗材ID',
    product_name VARCHAR(128) COMMENT '耗材名称',
    required_quantity DECIMAL(15,2) DEFAULT 0 COMMENT '需求量',
    current_stock DECIMAL(15,2) DEFAULT 0 COMMENT '当前库存',
    shortage_quantity DECIMAL(15,2) DEFAULT 0 COMMENT '缺口数量',
    substituted TINYINT DEFAULT 0 COMMENT '是否已替代',
    substitute_product_id BIGINT COMMENT '替代耗材ID',
    alert_level VARCHAR(16) DEFAULT 'warning' COMMENT '告警级别: critical/warning',
    status VARCHAR(16) DEFAULT 'pending' COMMENT '状态: pending/resolved',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    INDEX idx_store_id (store_id),
    INDEX idx_status (status),
    INDEX idx_alert_level (alert_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存不足告警';

-- ============================================
-- 4. RBAC 权限
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('stock:service-material:rule', '项目耗材规则', 'stock', 'menu', 1, 0, NOW()),
    ('stock:service-material:deduct', '项目自动扣减', 'stock', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 5. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/material' AND deleted = 0 LIMIT 1),
    'ServiceMaterial', '/material/service-material', '项目耗材', 'SetUp', 4, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/material/service-material' AND deleted = 0
);

-- ============================================
-- 6. 示例数据
-- ============================================
-- 注：实际项目耗材规则需要根据真实 service_id 和 product_id 关联
-- 这里仅提供 INSERT 模板
-- INSERT INTO biz_service_material_rule (service_id, product_id, standard_quantity, unit, waste_rate, required, fefo_enabled, remark)
-- VALUES (1, 1, 5.0, 'ml', 0.05, 1, 1, '标准面部护理');