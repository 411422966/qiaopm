-- ============================================
-- 耗材绑定配置模块
-- P0-M06-004：耗材绑定配置
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 服务耗材用量分组表
-- ============================================
DROP TABLE IF EXISTS biz_service_material_group;
CREATE TABLE biz_service_material_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_id BIGINT NOT NULL COMMENT '关联服务ID',
    group_name VARCHAR(128) NOT NULL COMMENT '分组名称(场景)',
    dimension VARCHAR(32) NOT NULL COMMENT '分组维度: employee_level/customer_type/service_duration',
    dimension_value TEXT COMMENT '维度值(JSON)',
    priority INT DEFAULT 0 COMMENT '优先级',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_service_id (service_id),
    INDEX idx_dimension (dimension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务耗材用量分组';

-- ============================================
-- 2. 耗材替代规则表
-- ============================================
DROP TABLE IF EXISTS biz_material_substitute;
CREATE TABLE biz_material_substitute (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    main_product_id BIGINT NOT NULL COMMENT '主耗材ID',
    substitute_product_id BIGINT NOT NULL COMMENT '替代耗材ID',
    substitute_ratio DECIMAL(10,4) DEFAULT 1 COMMENT '替代比例（1替代耗材=多少主耗材）',
    trigger_condition VARCHAR(32) DEFAULT 'stock_low' COMMENT '触发条件：stock_low/price_high/manual',
    stock_threshold INT COMMENT '最低库存阈值',
    priority INT DEFAULT 0 COMMENT '优先级',
    status TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_main_product (main_product_id),
    INDEX idx_substitute_product (substitute_product_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='耗材替代规则';

-- ============================================
-- 3. 耗材消耗记录表
-- ============================================
DROP TABLE IF EXISTS biz_material_consumption_log;
CREATE TABLE biz_material_consumption_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT COMMENT '关联订单ID',
    order_no VARCHAR(64) COMMENT '订单号',
    order_item_id BIGINT COMMENT '订单明细ID',
    service_id BIGINT COMMENT '服务ID',
    service_name VARCHAR(128) COMMENT '服务名称',
    product_id BIGINT NOT NULL COMMENT '耗材ID',
    product_name VARCHAR(128) COMMENT '耗材名称',
    spec VARCHAR(128) COMMENT '耗材规格',
    quantity DECIMAL(15,2) DEFAULT 0 COMMENT '消耗数量',
    unit_cost DECIMAL(15,2) DEFAULT 0 COMMENT '单位成本',
    total_cost DECIMAL(15,2) DEFAULT 0 COMMENT '总成本',
    is_substitute TINYINT DEFAULT 0 COMMENT '是否使用替代耗材',
    original_product_id BIGINT COMMENT '替代的原始耗材ID',
    employee_id BIGINT COMMENT '技师ID',
    employee_name VARCHAR(64) COMMENT '技师姓名',
    member_id BIGINT COMMENT '客户ID',
    store_id BIGINT COMMENT '门店ID',
    consumption_date DATE COMMENT '消耗日期',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    INDEX idx_service_id (service_id),
    INDEX idx_employee_id (employee_id),
    INDEX idx_store_id (store_id),
    INDEX idx_consumption_date (consumption_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='耗材消耗记录';

-- ============================================
-- 4. 服务耗材成本表（用于成本分析缓存）
-- ============================================
DROP TABLE IF EXISTS biz_service_material_cost;
CREATE TABLE biz_service_material_cost (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_id BIGINT NOT NULL COMMENT '服务ID',
    service_name VARCHAR(128) COMMENT '服务名称',
    service_price DECIMAL(15,2) DEFAULT 0 COMMENT '服务售价',
    material_cost DECIMAL(15,2) DEFAULT 0 COMMENT '单次耗材成本',
    cost_ratio DECIMAL(5,2) DEFAULT 0 COMMENT '成本占比(%)',
    item_count INT DEFAULT 0 COMMENT '包含耗材数量',
    required_count INT DEFAULT 0 COMMENT '必需耗材数量',
    stat_month VARCHAR(8) COMMENT '统计周期(YYYY-MM)',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_service_id (service_id),
    INDEX idx_stat_month (stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务耗材成本表';

-- ============================================
-- 5. biz_service_material 表增强（如果需要）
-- ============================================
-- ALTER TABLE biz_service_material ADD COLUMN group_id BIGINT COMMENT '用量分组ID' AFTER service_id;
-- ALTER TABLE biz_service_material ADD COLUMN is_substitute TINYINT DEFAULT 0 COMMENT '是否替代耗材' AFTER is_required;
-- ALTER TABLE biz_service_material ADD INDEX idx_group_id (group_id);

-- ============================================
-- 6. RBAC 权限
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('stock:material:binding', '耗材绑定配置', 'stock', 'menu', 1, 0, NOW()),
    ('stock:material:stat', '耗材消耗统计', 'stock', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 7. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    0,
    'MaterialManagement', '/material', '耗材管理', 'Box', 70, 'directory', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/material' AND deleted = 0
);

-- 子菜单
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/material' AND deleted = 0 LIMIT 1),
    'MaterialBinding', '/material/binding', '耗材绑定', 'Connection', 1, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/material/binding' AND deleted = 0
);

INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/material' AND deleted = 0 LIMIT 1),
    'MaterialStatistics', '/material/statistics', '消耗统计', 'PieChart', 2, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/material/statistics' AND deleted = 0
);