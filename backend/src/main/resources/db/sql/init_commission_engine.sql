-- ============================================
-- 多维提成规则引擎
-- P0-M08-003：多维提成规则引擎
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 提成阶梯表
-- ============================================
DROP TABLE IF EXISTS biz_commission_tier;
CREATE TABLE biz_commission_tier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT NOT NULL COMMENT '关联提成规则ID',
    tier_start DECIMAL(15,2) DEFAULT 0 COMMENT '阶梯起始金额',
    tier_end DECIMAL(15,2) COMMENT '阶梯结束金额（null表示不封顶）',
    calculation_method VARCHAR(32) DEFAULT 'rate' COMMENT '计算方式：rate/fixed',
    rate DECIMAL(10,4) DEFAULT 0 COMMENT '比例或金额（按calculationMethod区分）',
    description VARCHAR(255) COMMENT '阶梯说明',
    sort_order INT DEFAULT 0 COMMENT '排序',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_rule_id (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提成阶梯表';

-- ============================================
-- 2. 提成条件表
-- ============================================
DROP TABLE IF EXISTS biz_commission_condition;
CREATE TABLE biz_commission_condition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT NOT NULL COMMENT '关联提成规则ID',
    condition_type VARCHAR(32) NOT NULL COMMENT '条件类型: service/service_category/employee_level/customer_type/member_level/min_amount/max_amount/time_slot/weekday',
    operator VARCHAR(16) DEFAULT 'eq' COMMENT '操作符: eq/in/gte/lte',
    condition_value TEXT COMMENT '条件值（JSON或字符串）',
    condition_label VARCHAR(128) COMMENT '显示名',
    sort_order INT DEFAULT 0 COMMENT '排序',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_rule_id (rule_id),
    INDEX idx_condition_type (condition_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提成规则多维度条件';

-- ============================================
-- 3. 提成模拟计算记录表
-- ============================================
DROP TABLE IF EXISTS biz_commission_simulation;
CREATE TABLE biz_commission_simulation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT COMMENT '员工ID',
    employee_name VARCHAR(64) COMMENT '员工姓名',
    employee_level_id BIGINT COMMENT '技师等级ID',
    service_id BIGINT COMMENT '服务ID',
    service_amount DECIMAL(15,2) DEFAULT 0 COMMENT '服务金额',
    customer_type VARCHAR(32) COMMENT '客户类型',
    simulation_date VARCHAR(32) COMMENT '模拟日期',
    matched_rule_id BIGINT COMMENT '命中规则ID',
    matched_rule_name VARCHAR(128) COMMENT '命中规则名',
    matched_conditions TEXT COMMENT '命中条件JSON',
    calculation_method VARCHAR(32) COMMENT '计算方式',
    rate DECIMAL(10,4) COMMENT '比例',
    fixed_amount DECIMAL(15,2) COMMENT '固定金额',
    commission_amount DECIMAL(15,2) DEFAULT 0 COMMENT '提成金额',
    calculation_detail TEXT COMMENT '计算明细',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_employee_id (employee_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提成模拟记录';

-- ============================================
-- 4. commission_rule 表新增 calculation_method 支持 'TIER'
-- ============================================
-- ALTER TABLE commission_rule MODIFY COLUMN calculation_method VARCHAR(32) DEFAULT 'RATE'
--     COMMENT '计算方式：RATE/FIXED/TIER';

-- ============================================
-- 5. RBAC 权限
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('commission:rule', '提成规则', 'commission', 'menu', 1, 0, NOW()),
    ('commission:rule:config', '提成规则配置', 'commission', 'menu', 1, 0, NOW()),
    ('commission:simulation', '提成模拟计算', 'commission', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 6. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    0,
    'CommissionManagement', '/commission', '提成管理', 'Money', 60, 'directory', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/commission' AND deleted = 0
);

-- 子菜单
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/commission' AND deleted = 0 LIMIT 1),
    'CommissionRuleList', '/commission/rule-list', '规则列表', 'List', 1, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/commission/rule-list' AND deleted = 0
);

INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/commission' AND deleted = 0 LIMIT 1),
    'CommissionRules', '/commission/rules', '规则配置', 'Setting', 2, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/commission/rules' AND deleted = 0
);

INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/commission' AND deleted = 0 LIMIT 1),
    'CommissionSimulation', '/commission/simulation', '模拟计算', 'Aim', 3, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/commission/simulation' AND deleted = 0
);
