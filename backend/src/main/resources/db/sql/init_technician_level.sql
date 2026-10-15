-- ============================================
-- 技师等级管理模块
-- P0-M08-002：技师等级管理
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 技师等级表
-- ============================================
DROP TABLE IF EXISTS biz_technician_level;
CREATE TABLE biz_technician_level (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    level_code VARCHAR(32) NOT NULL COMMENT '等级编码，如L1/L2/L3',
    level_name VARCHAR(64) NOT NULL COMMENT '等级名称',
    level_icon VARCHAR(255) COMMENT '等级图标URL',
    level_color VARCHAR(16) DEFAULT '#409EFF' COMMENT '等级颜色 #RRGGBB',
    sort_order INT DEFAULT 1 COMMENT '等级排序，数字越小等级越高',
    discount_rate DECIMAL(5,2) DEFAULT 0 COMMENT '服务折扣比例%',
    commission_bonus DECIMAL(5,2) DEFAULT 0 COMMENT '提成加成比例%',
    promotion_sales DECIMAL(15,2) COMMENT '晋升要求业绩(元)',
    promotion_rating DECIMAL(5,2) COMMENT '晋升要求好评率',
    promotion_months INT COMMENT '晋升要求最低服务月数',
    privileges VARCHAR(1000) COMMENT '等级特权描述',
    service_categories VARCHAR(500) COMMENT '关联项目分类(JSON数组)',
    status TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_level_code (level_code, deleted),
    INDEX idx_status (status),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技师等级表';

-- ============================================
-- 2. 等级权益表
-- ============================================
DROP TABLE IF EXISTS biz_level_benefit;
CREATE TABLE biz_level_benefit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    level_id BIGINT NOT NULL COMMENT '等级ID',
    benefit_type VARCHAR(32) NOT NULL COMMENT '权益类型：discount/commission/priority/exclusive/training',
    benefit_name VARCHAR(128) NOT NULL COMMENT '权益名称',
    benefit_value VARCHAR(255) COMMENT '权益值',
    description VARCHAR(500) COMMENT '权益说明',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_level_id (level_id),
    INDEX idx_benefit_type (benefit_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技师等级权益表';

-- ============================================
-- 3. 等级评定记录表
-- ============================================
DROP TABLE IF EXISTS biz_level_evaluation;
CREATE TABLE biz_level_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL COMMENT '员工ID',
    from_level_id BIGINT COMMENT '原等级ID',
    to_level_id BIGINT NOT NULL COMMENT '评定等级ID',
    evaluation_type VARCHAR(32) DEFAULT 'auto' COMMENT '评定类型：auto/manual',
    result VARCHAR(32) NOT NULL COMMENT '评定结果：upgrade/downgrade/keep',
    evaluation_date DATE NOT NULL COMMENT '评定日期',
    total_sales DECIMAL(15,2) DEFAULT 0 COMMENT '当前累计业绩',
    rating_score DECIMAL(5,2) DEFAULT 0 COMMENT '当前好评率',
    service_months INT DEFAULT 0 COMMENT '当前服务月数',
    complaint_rate DECIMAL(5,2) DEFAULT 0 COMMENT '投诉率',
    evaluation_note VARCHAR(1000) COMMENT '评定说明',
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(64) COMMENT '操作人姓名',
    status VARCHAR(32) DEFAULT 'active' COMMENT '状态：pending/active/reverted',
    effective_time DATETIME COMMENT '生效时间',
    store_id BIGINT COMMENT '门店ID',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_employee_id (employee_id),
    INDEX idx_store_id (store_id),
    INDEX idx_status (status),
    INDEX idx_evaluation_date (evaluation_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技师等级评定记录表';

-- ============================================
-- 4. pos_employee 表新增 employee_level 字段(关联等级)
-- ============================================
-- ALTER TABLE pos_employee ADD COLUMN employee_level BIGINT COMMENT '技师等级ID' AFTER position;
-- ALTER TABLE pos_employee ADD INDEX idx_employee_level (employee_level);

-- ============================================
-- 5. 默认等级数据
-- ============================================
INSERT INTO biz_technician_level
(level_code, level_name, level_color, sort_order, discount_rate, commission_bonus,
 promotion_sales, promotion_rating, promotion_months, privileges, status)
VALUES
('L4', '首席技师', '#F56C6C', 1, 20, 10, 200000, 95, 36, '享受顶级客户优先权、专属培训机会、年度奖金资格', 1),
('L3', '高级技师', '#E6A23C', 2, 15, 7, 100000, 90, 24, '享受高级客户优先权、专业培训、内部晋升推荐', 1),
('L2', '中级技师', '#67C23A', 3, 10, 5, 50000, 85, 12, '享受中级客户预约权、基础培训', 1),
('L1', '初级技师', '#409EFF', 4, 5, 2, 10000, 80, 3, '享受初级客户预约、新人培训', 1),
('T1', '实习技师', '#909399', 5, 0, 0, 0, 0, 0, '新人培训阶段', 1);

-- ============================================
-- 6. 默认等级权益
-- ============================================
INSERT INTO biz_level_benefit (level_id, benefit_type, benefit_name, benefit_value, description, sort_order, status)
SELECT id, 'discount', '服务折扣', CONCAT(discount_rate, '% OFF'), '享受服务价格折扣', 1, 1
FROM biz_technician_level WHERE deleted = 0;

INSERT INTO biz_level_benefit (level_id, benefit_type, benefit_name, benefit_value, description, sort_order, status)
SELECT id, 'commission', '提成加成', CONCAT('+', commission_bonus, '%'), '在原提成基础上额外加成', 2, 1
FROM biz_technician_level WHERE deleted = 0 AND commission_bonus > 0;

INSERT INTO biz_level_benefit (level_id, benefit_type, benefit_name, benefit_value, description, sort_order, status)
SELECT id, 'priority', '优先预约权', 'VIP', '享受VIP客户优先预约权', 3, 1
FROM biz_technician_level WHERE deleted = 0 AND sort_order <= 3;

INSERT INTO biz_level_benefit (level_id, benefit_type, benefit_name, benefit_value, description, sort_order, status)
SELECT id, 'training', '专业培训', '年度', '享受公司组织的专业技能培训', 4, 1
FROM biz_technician_level WHERE deleted = 0 AND sort_order <= 2;

-- ============================================
-- 7. RBAC 权限配置
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('employee:level', '技师等级', 'employee', 'menu', 1, 0, NOW()),
    ('employee:level:benefit', '等级权益配置', 'employee', 'menu', 1, 0, NOW()),
    ('employee:level:evaluation', '等级评定', 'employee', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 8. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    0,
    'TechnicianManagement', '/technician', '技师管理', 'Medal', 51, 'directory', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/technician' AND deleted = 0
);

-- 子菜单
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/technician' AND deleted = 0 LIMIT 1),
    'TechnicianLevel', '/technician/level', '技师等级', 'Medal', 1, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/technician/level' AND deleted = 0
);

INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/technician' AND deleted = 0 LIMIT 1),
    'TechnicianBenefit', '/technician/benefit', '等级权益', 'Present', 2, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/technician/benefit' AND deleted = 0
);

INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/technician' AND deleted = 0 LIMIT 1),
    'TechnicianEvaluation', '/technician/evaluation', '等级评定', 'Trophy', 3, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/technician/evaluation' AND deleted = 0
);
