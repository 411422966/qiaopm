-- ============================================================
-- 美业 SaaS - V0 Baseline ALL-in-One 整合脚本
-- 创建日期：2026-08-14
-- 用途：把 init_migration_v0_baseline.sql 全部 SOURCE 展开为单文件
-- 适配：PowerShell 管道（`Get-Content xxx.sql | mysql ...`）
-- 注意：去掉了重复 init_m05_package_part2.sql / init_m05_seed.sql
--       保留 init_m05_package.sql（已含 4 套餐 + 1 订单 + 1 核销）
--       保留 init_m09_commission_v2.sql（已含 settlement 与 record 两表）
-- ============================================================

USE meiyetec;

-- ============================================================
-- 1. 用户/角色/权限 + sys_login_log
-- 来自：init_m01_auth.sql
-- ============================================================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    real_name VARCHAR(50) COMMENT '真实姓名',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    avatar VARCHAR(255) COMMENT '头像URL',
    status TINYINT DEFAULT 1 COMMENT '状态：0禁用 1启用',
    store_id BIGINT COMMENT '所属门店ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0未删除 1已删除',
    INDEX idx_username (username),
    INDEX idx_store_id (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色代码',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) COMMENT '描述',
    status TINYINT DEFAULT 1 COMMENT '状态：0禁用 1启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    INDEX idx_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
    permission_code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限代码',
    permission_name VARCHAR(50) NOT NULL COMMENT '权限名称',
    permission_type VARCHAR(20) DEFAULT 'button' COMMENT '权限类型：menu按钮',
    parent_id BIGINT DEFAULT 0 COMMENT '父级ID',
    path VARCHAR(255) COMMENT '路由路径',
    icon VARCHAR(50) COMMENT '图标',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态：0禁用 1启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    INDEX idx_permission_code (permission_code),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    user_id BIGINT DEFAULT NULL COMMENT '用户ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '用户名',
    tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID',
    login_type VARCHAR(20) DEFAULT 'password' COMMENT '登录类型',
    status TINYINT DEFAULT 1 COMMENT '状态：0失败 1成功',
    message VARCHAR(255) DEFAULT NULL COMMENT '备注/错误信息',
    ip VARCHAR(50) DEFAULT NULL COMMENT '客户端IP',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT 'UA',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

INSERT INTO sys_role (role_code, role_name, description) VALUES
('SUPER_ADMIN', '超级管理员', '系统超级管理员，拥有所有权限'),
('STORE_ADMIN', '门店管理员', '门店管理员，管理门店所有业务'),
('EMPLOYEE', '员工', '普通员工，拥有基础操作权限');

INSERT INTO sys_user (username, password, real_name, phone, status) VALUES
('admin', '$2a$10$6I0.9H0hK9mbKFPnSClLBukD9oElGsqRJuDeop.XJzOVNHxWM7ZP2', '系统管理员', '13800138000', 1);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'SUPER_ADMIN';

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, path, sort_order) VALUES
('dashboard', '首页', 'menu', 0, '/dashboard', 1),
('member', '会员管理', 'menu', 0, '/member', 2),
('booking', '预约管理', 'menu', 0, '/booking', 3),
('pos', '收银结算', 'menu', 0, '/pos', 4),
('inventory', '库存管理', 'menu', 0, '/inventory', 5),
('employee', '员工管理', 'menu', 0, '/employee', 6),
('marketing', '营销工具', 'menu', 0, '/marketing', 7),
('report', '数据报表', 'menu', 0, '/report', 8),
('setting', '系统设置', 'menu', 0, '/setting', 9);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order) VALUES
('member:list', '查看会员', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 1),
('member:add', '新增会员', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 2),
('member:edit', '编辑会员', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 3),
('member:delete', '删除会员', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 4);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'SUPER_ADMIN';

-- ============================================================
-- 2. 门店/员工/等级/分类 + sys_log
-- 来自：init_m02_basic.sql
-- ============================================================
DROP TABLE IF EXISTS pos_store;
CREATE TABLE pos_store (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '门店ID',
    store_name VARCHAR(100) NOT NULL COMMENT '门店名称',
    store_code VARCHAR(50) UNIQUE COMMENT '门店编码',
    contact_person VARCHAR(50) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    address VARCHAR(255) COMMENT '详细地址',
    province VARCHAR(50) COMMENT '省份',
    city VARCHAR(50) COMMENT '城市',
    district VARCHAR(50) COMMENT '区县',
    business_hours VARCHAR(100) COMMENT '营业时间',
    status TINYINT DEFAULT 1 COMMENT '状态：0停业 1营业',
    logo_url VARCHAR(255) COMMENT '门店Logo',
    description TEXT COMMENT '门店简介',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    INDEX idx_store_code (store_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店表';

DROP TABLE IF EXISTS pos_employee;
CREATE TABLE pos_employee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '员工ID',
    employee_code VARCHAR(50) UNIQUE COMMENT '员工工号',
    employee_name VARCHAR(50) NOT NULL COMMENT '员工姓名',
    phone VARCHAR(20) UNIQUE COMMENT '手机号',
    password VARCHAR(100) COMMENT '登录密码',
    gender TINYINT COMMENT '性别：0女 1男',
    id_card VARCHAR(20) COMMENT '身份证号',
    avatar_url VARCHAR(255) COMMENT '头像',
    position VARCHAR(50) COMMENT '职位',
    department VARCHAR(50) COMMENT '部门/组别',
    store_id BIGINT NOT NULL COMMENT '所属门店ID',
    entry_date DATE COMMENT '入职日期',
    status TINYINT DEFAULT 1 COMMENT '状态：0离职 1在职 2休假',
    salary decimal(10,2) COMMENT '基本工资',
    commission_rate DECIMAL(5,4) COMMENT '提成比例',
    role_ids VARCHAR(255) COMMENT '角色ID列表（逗号分隔）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    INDEX idx_employee_code (employee_code),
    INDEX idx_phone (phone),
    INDEX idx_store_id (store_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工表';

DROP TABLE IF EXISTS mem_level;
CREATE TABLE mem_level (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '等级ID',
    level_code VARCHAR(50) NOT NULL UNIQUE COMMENT '等级编码',
    level_name VARCHAR(50) NOT NULL COMMENT '等级名称',
    level_icon VARCHAR(255) COMMENT '等级图标',
    min_points INT DEFAULT 0 COMMENT '最低积分要求',
    max_points INT COMMENT '最高积分要求',
    discount_rate DECIMAL(4,2) DEFAULT 1.00 COMMENT '折扣率（0-1）',
    birthday_gift VARCHAR(100) COMMENT '生日礼物',
    birthday_discount DECIMAL(4,2) DEFAULT 1.00 COMMENT '生日折扣',
    points_multiplier DECIMAL(3,2) DEFAULT 1.00 COMMENT '积分倍数',
    level_color VARCHAR(20) DEFAULT '#409EFF' COMMENT '等级颜色',
    sort_order INT DEFAULT 0 COMMENT '排序',
    description TEXT COMMENT '等级说明',
    status TINYINT DEFAULT 1 COMMENT '状态：0禁用 1启用',
    tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    INDEX idx_level_code (level_code),
    INDEX idx_sort_order (sort_order),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员等级表';

DROP TABLE IF EXISTS sys_category;
CREATE TABLE sys_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
    category_code VARCHAR(50) COMMENT '分类编码',
    category_name VARCHAR(50) NOT NULL COMMENT '分类名称',
    parent_id BIGINT DEFAULT 0 COMMENT '父级ID',
    level INT DEFAULT 1 COMMENT '层级',
    path VARCHAR(255) COMMENT '路径（逗号分隔）',
    sort_order INT DEFAULT 0 COMMENT '排序',
    icon VARCHAR(50) COMMENT '图标',
    category_type VARCHAR(20) DEFAULT 'goods' COMMENT '类型：goods服务项目 product商品',
    description VARCHAR(255) COMMENT '描述',
    status TINYINT DEFAULT 1 COMMENT '状态：0禁用 1启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    INDEX idx_parent_id (parent_id),
    INDEX idx_category_type (category_type),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';

CREATE TABLE IF NOT EXISTS sys_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    user_id BIGINT DEFAULT NULL COMMENT '操作用户ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '操作用户名',
    tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID',
    module VARCHAR(50) DEFAULT NULL COMMENT '模块',
    action VARCHAR(50) DEFAULT NULL COMMENT '动作',
    description VARCHAR(500) DEFAULT NULL COMMENT '操作描述',
    request_url VARCHAR(255) DEFAULT NULL COMMENT '请求URL',
    request_method VARCHAR(10) DEFAULT NULL COMMENT '请求方法',
    request_params TEXT DEFAULT NULL COMMENT '请求参数',
    ip VARCHAR(50) DEFAULT NULL COMMENT '客户端IP',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT 'UA',
    cost_ms INT DEFAULT NULL COMMENT '耗时(ms)',
    status TINYINT DEFAULT 1 COMMENT '状态：0失败 1成功',
    error_msg TEXT DEFAULT NULL COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_module (module),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

INSERT INTO pos_store (store_name, store_code, contact_person, contact_phone, address, province, city, district, business_hours, status) VALUES
('美业SaaS示范店', 'STORE001', '管理员', '13800138001', '北京市朝阳区建国路88号', '北京市', '北京市', '朝阳区', '09:00-21:00', 1);

INSERT INTO mem_level (level_code, level_name, min_points, max_points, discount_rate, level_color, sort_order, description) VALUES
('LEVEL_PURPLE', '紫钻会员', 0, 999, 0.95, '#9B59B6', 1, '新注册会员，享受95折优惠'),
('LEVEL_GOLD', '金卡会员', 1000, 4999, 0.90, '#F1C40F', 2, '累计消费1000积分，享受9折优惠'),
('LEVEL_BLACK', '黑金会员', 5000, 9999, 0.85, '#2C3E50', 3, '累计消费5000积分，享受85折优惠'),
('LEVEL_DIAMOND', '钻石会员', 10000, NULL, 0.80, '#3498DB', 4, '累计消费10000积分，享受8折优惠，生日双倍积分');

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type) VALUES
('GOODS', '服务项目', 0, 1, '0', 1, 'goods'),
('PRODUCT', '商品', 0, 1, '0', 2, 'product');

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type)
SELECT 'HAIR', '美发', id, 2, CONCAT('0,', id), 1, 'goods' FROM sys_category WHERE category_code = 'GOODS';

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type)
SELECT 'NAIL', '美甲', id, 2, CONCAT('0,', id), 2, 'goods' FROM sys_category WHERE category_code = 'GOODS';

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type)
SELECT 'SKIN', '美容护肤', id, 2, CONCAT('0,', id), 3, 'goods' FROM sys_category WHERE category_code = 'GOODS';

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type)
SELECT 'COSMETICS', '化妆品', id, 2, CONCAT('0,', id), 1, 'product' FROM sys_category WHERE category_code = 'PRODUCT';

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type)
SELECT 'SKIN_CARE', '护肤品', id, 2, CONCAT('0,', id), 2, 'product' FROM sys_category WHERE category_code = 'PRODUCT';

-- ============================================================
-- 3. 会员/标签/档案 + crm_wallet + crm_wallet_transaction
-- 来自：init_m03_member.sql
-- ============================================================
DROP TABLE IF EXISTS crm_member_tag;
CREATE TABLE crm_member_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_name VARCHAR(50) NOT NULL,
    tag_color VARCHAR(20) DEFAULT '#409EFF',
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tag_name (tag_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员标签';

DROP TABLE IF EXISTS crm_member;
CREATE TABLE crm_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_no VARCHAR(32) NOT NULL,
    name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    gender TINYINT DEFAULT 0,
    birthday DATE DEFAULT NULL,
    level_id BIGINT DEFAULT NULL,
    store_id BIGINT DEFAULT NULL,
    balance DECIMAL(12,2) DEFAULT 0.00,
    points INT DEFAULT 0,
    total_consume DECIMAL(12,2) DEFAULT 0.00,
    avatar VARCHAR(255) DEFAULT NULL,
    address VARCHAR(255) DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    status TINYINT DEFAULT 1,
    join_date DATE DEFAULT (CURRENT_DATE),
    last_visit DATETIME DEFAULT NULL,
    tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_phone (phone, deleted),
    UNIQUE KEY uk_member_no (member_no, deleted),
    KEY idx_name (name),
    KEY idx_level (level_id),
    KEY idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员';

DROP TABLE IF EXISTS crm_member_profile;
CREATE TABLE crm_member_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    skin_type VARCHAR(50) DEFAULT NULL,
    hair_type VARCHAR(50) DEFAULT NULL COMMENT '发质类型（美发子行业画像，2026-09 M03 扩展）',
    nail_type VARCHAR(50) DEFAULT NULL COMMENT '甲面类型（美甲子行业画像，2026-09 M03 扩展）',
    allergy VARCHAR(500) DEFAULT NULL,
    hobby VARCHAR(500) DEFAULT NULL,
    job VARCHAR(100) DEFAULT NULL,
    wechat VARCHAR(50) DEFAULT NULL,
    emergency_name VARCHAR(50) DEFAULT NULL,
    emergency_phone VARCHAR(20) DEFAULT NULL,
    consume_pref TEXT DEFAULT NULL,
    visit_record TEXT DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member (member_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员档案';

DROP TABLE IF EXISTS crm_member_tag_rel;
CREATE TABLE crm_member_tag_rel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_tag (member_id, tag_id),
    KEY idx_member (member_id),
    KEY idx_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员标签关联';

CREATE TABLE IF NOT EXISTS crm_wallet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '钱包ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    member_id BIGINT NOT NULL COMMENT '会员ID',
    balance DECIMAL(12,2) DEFAULT 0.00 COMMENT '可用余额',
    total_recharge DECIMAL(12,2) DEFAULT 0.00 COMMENT '累计充值',
    total_consume DECIMAL(12,2) DEFAULT 0.00 COMMENT '累计消费',
    frozen_amount DECIMAL(12,2) DEFAULT 0.00 COMMENT '冻结金额',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_member (member_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员钱包表';

CREATE TABLE IF NOT EXISTS crm_wallet_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '流水ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    member_id BIGINT NOT NULL COMMENT '会员ID',
    type TINYINT NOT NULL COMMENT '类型：1充值 2消费 3退款 4赠送 5冻结 6解冻',
    amount DECIMAL(12,2) NOT NULL COMMENT '变动金额（正数）',
    balance_before DECIMAL(12,2) DEFAULT NULL,
    balance_after DECIMAL(12,2) DEFAULT NULL,
    gift_amount DECIMAL(12,2) DEFAULT 0.00,
    pay_method VARCHAR(20) DEFAULT NULL,
    order_id BIGINT DEFAULT NULL,
    operator_id BIGINT DEFAULT NULL,
    remark VARCHAR(255) DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member (member_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_type (type),
    INDEX idx_created_at (created_at),
    INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包流水表';

INSERT INTO crm_member_tag (tag_name, tag_color, sort_order) VALUES
('VIP客户',    '#F56C6C', 1),
('敏感肌',    '#E6A23C', 2),
('回头客',    '#67C23A', 3),
('新客户',    '#909399', 4),
('高消费',    '#409EFF', 5);

INSERT INTO crm_member (member_no, name, phone, gender, level_id, store_id, balance, points, total_consume, address, remark, tenant_id) VALUES
('M202607001', '李雅琳', '13800138001', 2, 1, 1, 5800.00, 2860, 12680.00, '北京市朝阳区望京SOHO', '忠实客户', 1),
('M202607002', '王晓彤', '13800138002', 2, 2, 1, 2200.00, 1200, 4580.00,  '北京市海淀区中关村', '敏感肌',   1),
('M202607003', '张敏华', '13800138003', 2, 3, 2,  500.00,  350,  1200.00, '北京市朝阳区国贸',   '',         1),
('M202607004', '陈丽君', '13800138004', 2, 1, 1,12800.00, 5200, 28900.00, '北京市东城区王府井', '高消费VIP', 1),
('M202607005', '刘莎莎', '13800138005', 2, 2, 2, 3500.00, 1800,  7200.00, '北京市西城区西单',   '',         1),
('M202607006', '赵晓燕', '13800138006', 2, 1, 1,15600.00, 6800, 35800.00, '北京市朝阳区三里屯', '回头客',   1),
('M202607007', '孙美丽', '13800138007', 2, 3, 1,  800.00,  480,  2400.00, '北京市丰台区方庄',   '新客户',   1),
('M202607008', '周雪梅', '13800138008', 2, 2, 2, 4500.00, 2200,  9800.00, '北京市朝阳区CBD',   '',         1);

INSERT INTO crm_member_profile (member_id, skin_type, allergy, hobby, job, wechat) VALUES
(1, '干性肌肤',     '花粉', '瑜伽/旅游', '外企经理', 'lyl_888'),
(2, '敏感性肌肤', '海鲜', '阅读/电影', '设计师',   'wxt_design'),
(3, '混合性肌肤', '无',   '健身',     '教师',     'zmh_teacher'),
(4, '干性肌肤',     '酒精', '高尔夫/红酒', '投资人',   'clj_vip');

INSERT INTO crm_member_tag_rel (member_id, tag_id) VALUES
(1, 1), (1, 3),
(2, 2),
(3, 4),
(4, 1), (4, 5),
(6, 3), (6, 5),
(7, 4);

-- ============================================================
-- 4. 套餐 + 订单 + 核销
-- 来自：init_m05_package.sql（含 4 套餐 + 1 订单 + 1 核销）
-- 注：init_m05_package_part2.sql / init_m05_seed.sql 内容重复，已省略
-- ============================================================
DROP TABLE IF EXISTS crm_package;
CREATE TABLE crm_package (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) DEFAULT NULL,
    category VARCHAR(50) DEFAULT '通用',
    cover VARCHAR(255) DEFAULT NULL,
    description VARCHAR(500) DEFAULT NULL,
    total_times INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    cost_price DECIMAL(12,2) DEFAULT 0.00,
    valid_days INT DEFAULT 365,
    sale_count INT DEFAULT 0,
    redeem_count INT DEFAULT 0,
    status TINYINT DEFAULT 1,
    remark VARCHAR(500) DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code, deleted),
    KEY idx_name (name),
    KEY idx_category (category),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐';

DROP TABLE IF EXISTS crm_package_order;
CREATE TABLE crm_package_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    order_no VARCHAR(32) NOT NULL,
    member_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    package_name VARCHAR(100) NOT NULL,
    total_times INT NOT NULL,
    remaining_times INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    pay_method VARCHAR(20) DEFAULT '微信',
    operator_id BIGINT DEFAULT NULL,
    buy_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    expire_time DATETIME DEFAULT NULL,
    status TINYINT DEFAULT 1,
    remark VARCHAR(500) DEFAULT NULL,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no, deleted),
    KEY idx_member (member_id),
    KEY idx_package (package_id),
    KEY idx_status (status),
    KEY idx_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐购买记录';

DROP TABLE IF EXISTS crm_package_redemption;
CREATE TABLE crm_package_redemption (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    order_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    times INT DEFAULT 1,
    service_item VARCHAR(100) DEFAULT NULL,
    beautician_id BIGINT DEFAULT NULL,
    note VARCHAR(500) DEFAULT NULL,
    operator_id BIGINT DEFAULT NULL,
    redeem_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_order (order_id),
    KEY idx_member (member_id),
    KEY idx_package (package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐核销记录';

INSERT INTO crm_package (tenant_id, name, code, category, description, total_times, price, cost_price, valid_days, status) VALUES
(1, '面部基础护理 5 次卡',   'PKG-FACE-05', '面部护理', '深层清洁 + 保湿护理，5 次一疗程',     5,   599.00,  200.00,  180, 1),
(1, '身体经络疏通 10 次卡', 'PKG-BODY-10', '身体护理', '全身经络疏通按摩，10 次套餐',         10, 1980.00,  600.00,  365, 1),
(1, '肩颈舒缓 20 次年卡',   'PKG-NECK-20', '肩颈护理', '适合上班族，缓解肩颈僵硬',           20, 3580.00, 1200.00,  365, 1),
(1, 'VIP 尊享无限次年卡',   'PKG-VIP-999', 'VIP尊享', '全年不限次护理（指定项目），含专属顾问', 999, 18800.00, 6000.00, 365, 1);

INSERT INTO crm_package_order
(tenant_id, order_no, member_id, package_id, package_name, total_times, remaining_times, price, pay_method, operator_id, buy_time, expire_time, status, remark)
SELECT 1, 'PKG202608010001', m.id, p.id, p.name, p.total_times, p.total_times, p.price, '微信', 1,
       NOW(), DATE_ADD(NOW(), INTERVAL p.valid_days DAY), 1, '测试用购买记录'
FROM crm_member m, crm_package p
WHERE m.phone = '13800138001' AND p.code = 'PKG-FACE-05'
LIMIT 1;

INSERT INTO crm_package_redemption
(tenant_id, order_id, member_id, package_id, times, service_item, beautician_id, note, operator_id)
SELECT 1, o.id, o.member_id, o.package_id, 1, '面部清洁', 1, '第一次到店核销', 1
FROM crm_package_order o
WHERE o.order_no = 'PKG202608010001'
LIMIT 1;

UPDATE crm_package_order o
SET o.remaining_times = o.remaining_times - 1
WHERE o.order_no = 'PKG202608010001';

UPDATE crm_package p
JOIN crm_package_order o ON o.package_id = p.id
SET p.sale_count = p.sale_count + 1, p.redeem_count = p.redeem_count + 1
WHERE o.order_no = 'PKG202608010001';

-- ============================================================
-- 5. 预约/房间/排班
-- 来自：init_m06_booking.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT NULL,
    store_id BIGINT DEFAULT NULL,
    room_name VARCHAR(50) NOT NULL,
    room_type VARCHAR(20) DEFAULT NULL,
    capacity INT DEFAULT 1,
    equipment VARCHAR(255) DEFAULT NULL,
    status TINYINT DEFAULT 1,
    sort_no INT DEFAULT 0,
    remark VARCHAR(500) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_tenant (tenant_id),
    INDEX idx_store (store_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间表';

CREATE TABLE IF NOT EXISTS biz_employee_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT NULL,
    employee_id BIGINT NOT NULL,
    employee_name VARCHAR(50) DEFAULT NULL,
    work_date DATE NOT NULL,
    shift_type TINYINT DEFAULT 4,
    start_time TIME DEFAULT NULL,
    end_time TIME DEFAULT NULL,
    max_bookings INT DEFAULT 8,
    status TINYINT DEFAULT 1,
    remark VARCHAR(500) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_employee (employee_id),
    INDEX idx_date (work_date),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技师排班表';

CREATE TABLE IF NOT EXISTS biz_booking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT NULL,
    booking_no VARCHAR(32) NOT NULL,
    member_id BIGINT NOT NULL,
    member_name VARCHAR(50) DEFAULT NULL,
    member_phone VARCHAR(20) DEFAULT NULL,
    employee_id BIGINT NOT NULL,
    employee_name VARCHAR(50) DEFAULT NULL,
    service_item VARCHAR(100) DEFAULT NULL,
    room_id BIGINT DEFAULT NULL,
    room_name VARCHAR(50) DEFAULT NULL,
    booking_date DATE DEFAULT NULL,
    start_time DATETIME DEFAULT NULL,
    end_time DATETIME DEFAULT NULL,
    status TINYINT DEFAULT 1,
    source TINYINT DEFAULT 1,
    remark VARCHAR(500) DEFAULT NULL,
    operator_id BIGINT DEFAULT NULL,
    checkin_time DATETIME DEFAULT NULL,
    complete_time DATETIME DEFAULT NULL,
    cancel_time DATETIME DEFAULT NULL,
    cancel_reason VARCHAR(500) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_member (member_id),
    INDEX idx_employee (employee_id),
    INDEX idx_room (room_id),
    INDEX idx_date (booking_date),
    INDEX idx_status (status),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约表';

DELETE FROM biz_booking;
DELETE FROM biz_employee_schedule;
DELETE FROM biz_room;
ALTER TABLE biz_booking AUTO_INCREMENT = 1;
ALTER TABLE biz_employee_schedule AUTO_INCREMENT = 1;
ALTER TABLE biz_room AUTO_INCREMENT = 1;

INSERT INTO biz_room (tenant_id, store_id, room_name, room_type, capacity, equipment, status, sort_no) VALUES
(1, 1, 'VIP1号房',  'VIP',     1, '按摩床+浴缸+音响', 1, 1),
(1, 1, 'VIP2号房',  'VIP',     1, '按摩床+浴缸',      1, 2),
(1, 1, '标准3号房', '单人间',  1, '按摩床',           1, 3),
(1, 1, '标准4号房', '单人间',  1, '按摩床',           1, 4);

INSERT INTO biz_employee_schedule
  (tenant_id, employee_id, employee_name, work_date, shift_type, start_time, end_time, max_bookings, status)
SELECT 1, e.id, e.employee_name,
  DATE_ADD(CURDATE(), INTERVAL d.n DAY),
  4, '09:00:00', '18:00:00', 8, 1
FROM pos_employee e
JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) d
WHERE e.deleted = 0 AND e.status = 1;

INSERT INTO biz_booking
  (tenant_id, booking_no, member_id, member_name, member_phone, employee_id, employee_name,
   service_item, room_id, room_name, booking_date, start_time, end_time, status, source, remark, operator_id)
VALUES
(1, 'BK20260801100001', 1, '李雅琳', '13800138001', 4, '王雪梅', '面部清洁', 1, 'VIP1号房', CURDATE(),
   TIMESTAMP(CURDATE(),'10:00:00'), TIMESTAMP(CURDATE(),'11:00:00'), 4, 1, '已完成的服务', 1),
(1, 'BK20260801110001', 1, '李雅琳', '13800138001', 5, '赵晓彤', '肩颈按摩', 2, 'VIP2号房', CURDATE(),
   TIMESTAMP(CURDATE(),'11:00:00'), TIMESTAMP(CURDATE(),'12:00:00'), 3, 1, '已签到未完成', 1),
(1, 'BK20260801140001', 1, '李雅琳', '13800138001', 6, '孙丽华', '身体经络', 3, '标准3号房', CURDATE(),
   TIMESTAMP(CURDATE(),'14:00:00'), TIMESTAMP(CURDATE(),'15:00:00'), 2, 1, '已确认预约', 1),
(1, 'BK20260801150001', 2, '张雨涵', '13800138002', 4, '王雪梅', '面部护理', 1, 'VIP1号房', DATE_ADD(CURDATE(), INTERVAL 1 DAY),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY),'15:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY),'16:00:00'), 1, 1, '明天下午', 1),
(1, 'BK20260801090001', 3, '陈思琪', '13800138003', 7, '周敏',   '足部护理', 4, '标准4号房', CURDATE(),
   TIMESTAMP(CURDATE(),'09:00:00'), TIMESTAMP(CURDATE(),'10:00:00'), 5, 2, '客户临时取消', 1),
(1, 'BK20260802090001', 4, '黄梦瑶', '13800138004', 4, '王雪梅', '面部清洁', 2, 'VIP2号房', DATE_ADD(CURDATE(), INTERVAL 2 DAY),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY),'09:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY),'10:00:00'), 1, 1, '新人体验', 1);

-- ============================================================
-- 6. POS
-- 来自：init_m07_pos.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `biz_service` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint,
  `service_code` varchar(64),
  `service_name` varchar(128) NOT NULL,
  `category_id` bigint,
  `unit_price` decimal(10,2) NOT NULL,
  `member_price` decimal(10,2),
  `unit` varchar(16) DEFAULT '次',
  `duration` int DEFAULT 0,
  `description` varchar(500),
  `image_url` varchar(500),
  `is_package` tinyint DEFAULT 0,
  `status` tinyint DEFAULT 1,
  `sort_order` int DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_biz_service_store` (`store_id`),
  INDEX `idx_biz_service_cat` (`category_id`)
) COMMENT='服务/产品表';

CREATE TABLE IF NOT EXISTS `pos_order` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `order_no` varchar(64) UNIQUE NOT NULL,
  `member_id` bigint,
  `member_name` varchar(64),
  `member_phone` varchar(20),
  `booking_id` bigint,
  `type` tinyint NOT NULL,
  `total_amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) DEFAULT 0,
  `final_amount` decimal(12,2) NOT NULL,
  `paid_amount` decimal(12,2) DEFAULT 0,
  `cashier_id` bigint NOT NULL,
  `cashier_name` varchar(64),
  `remark` varchar(255),
  `status` tinyint DEFAULT 1,
  `pay_time` datetime,
  `refund_time` datetime,
  `refund_amount` decimal(12,2) DEFAULT 0,
  `refund_reason` varchar(255),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_pos_order_store` (`store_id`),
  INDEX `idx_pos_order_member` (`member_id`),
  INDEX `idx_pos_order_date` (`store_id`, `create_time`)
) COMMENT='收银订单表';

CREATE TABLE IF NOT EXISTS `pos_order_item` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `order_id` bigint NOT NULL,
  `service_id` bigint,
  `service_name` varchar(128) NOT NULL,
  `quantity` int DEFAULT 1,
  `unit_price` decimal(10,2) NOT NULL,
  `total_price` decimal(12,2) NOT NULL,
  `discount_amount` decimal(10,2) DEFAULT 0,
  `final_price` decimal(12,2) NOT NULL,
  `package_order_id` bigint,
  `package_times` int DEFAULT 0,
  `employee_id` bigint,
  `employee_name` varchar(64),
  `remark` varchar(255),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_pos_item_order` (`order_id`)
) COMMENT='订单明细表';

CREATE TABLE IF NOT EXISTS `pos_payment` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `order_id` bigint NOT NULL,
  `payment_no` varchar(64) UNIQUE NOT NULL,
  `method` varchar(32) NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `status` tinyint DEFAULT 0,
  `transaction_id` varchar(128),
  `pay_time` datetime,
  `refund_time` datetime,
  `error_msg` varchar(255),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_pos_payment_order` (`order_id`)
) COMMENT='支付记录表';

INSERT INTO `biz_service` (`service_code`, `service_name`, `unit_price`, `member_price`, `unit`, `duration`, `status`, `sort_order`) VALUES
('SV0001', '基础面部护理', 280.00, 198.00, '次', 60, 1, 1),
('SV0002', '深层补水护理', 480.00, 358.00, '次', 90, 1, 2),
('SV0003', '肩颈舒缓按摩', 180.00, 128.00, '次', 45, 1, 3),
('SV0004', '全身精油SPA', 880.00, 698.00, '次', 120, 1, 4),
('SV0005', '美睫嫁接', 380.00, 288.00, '次', 90, 1, 5);

CREATE TABLE IF NOT EXISTS `sys_sequence` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `seq_name` varchar(64) UNIQUE,
  `current_val` bigint DEFAULT 0,
  `prefix` varchar(16),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP
) COMMENT='业务序列号表';

-- ============================================================
-- 7. 库存
-- 来自：init_m08_stock.sql
-- ============================================================
DROP TABLE IF EXISTS `stock_product`;
CREATE TABLE `stock_product` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `product_code` varchar(64) UNIQUE NOT NULL,
  `product_name` varchar(128) NOT NULL,
  `category` varchar(50) DEFAULT '耗材',
  `spec` varchar(100),
  `unit` varchar(16) DEFAULT '瓶',
  `cost_price` decimal(12,2) DEFAULT 0,
  `sale_price` decimal(12,2) DEFAULT 0,
  `min_stock` int DEFAULT 10,
  `max_stock` int DEFAULT 1000,
  `enable_batch` tinyint DEFAULT 0,
  `status` tinyint DEFAULT 1,
  `remark` varchar(500),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_stock_product_store` (`store_id`),
  INDEX `idx_stock_product_cat` (`category`)
) COMMENT='商品/耗材表';

DROP TABLE IF EXISTS `stock_inventory`;
CREATE TABLE `stock_inventory` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `batch_no` varchar(64) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT 0,
  `production_date` date,
  `expiry_date` date,
  `supplier` varchar(100),
  `last_in_time` datetime,
  `last_out_time` datetime,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_stock_inv_store_product` (`store_id`, `product_id`),
  INDEX `idx_stock_inv_batch` (`batch_no`),
  INDEX `idx_stock_inv_expiry` (`expiry_date`)
) COMMENT='库存表（支持批次）';

DROP TABLE IF EXISTS `stock_record`;
CREATE TABLE `stock_record` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `batch_no` varchar(64),
  `type` tinyint NOT NULL,
  `quantity` int NOT NULL,
  `before_qty` int DEFAULT 0,
  `after_qty` int DEFAULT 0,
  `unit_price` decimal(12,2) DEFAULT 0,
  `total_amount` decimal(12,2) DEFAULT 0,
  `supplier` varchar(100),
  `order_id` bigint,
  `operator_id` bigint NOT NULL,
  `operator_name` varchar(64),
  `remark` varchar(500),
  `record_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_stock_record_product` (`product_id`),
  INDEX `idx_stock_record_type` (`type`),
  INDEX `idx_stock_record_time` (`record_time`)
) COMMENT='出入库记录';

DROP TABLE IF EXISTS `stock_check`;
CREATE TABLE `stock_check` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `check_no` varchar(64) UNIQUE NOT NULL,
  `check_date` date NOT NULL,
  `total_items` int DEFAULT 0,
  `diff_items` int DEFAULT 0,
  `profit_qty` int DEFAULT 0,
  `loss_qty` int DEFAULT 0,
  `operator_id` bigint NOT NULL,
  `operator_name` varchar(64),
  `status` tinyint DEFAULT 1,
  `remark` varchar(500),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_stock_check_store` (`store_id`)
) COMMENT='库存盘点单';

INSERT INTO `stock_product` (tenant_id, store_id, product_code, product_name, category, spec, unit, cost_price, sale_price, min_stock, max_stock, enable_batch, status) VALUES
(1, 1, 'SP-BOT-001', '玻尿酸精华液', '耗材', '30ml/瓶',  '瓶',  80.00, 198.00,  20,  500, 1, 1),
(1, 1, 'SP-MSK-002', '美白补水面膜', '耗材', '25g*5片/盒', '盒', 35.00,  98.00,  30,  600, 1, 1),
(1, 1, 'SP-CRM-003', '保湿面霜',     '耗材', '50g/瓶',  '瓶',  60.00, 158.00,  25,  400, 0, 1),
(1, 1, 'SP-OIL-004', '精油按摩油',   '耗材', '100ml/瓶', '瓶', 45.00, 128.00,  15,  300, 0, 1),
(1, 1, 'SP-TOW-005', '一次性毛巾',   '耗材', '80g/条',  '条',   2.00,   5.00, 100, 2000, 0, 1),
(1, 1, 'SP-CUP-006', '一次性杯子',   '耗材', '250ml/个', '个',   0.50,   2.00, 200, 3000, 0, 1);

INSERT INTO `stock_inventory` (tenant_id, store_id, product_id, batch_no, quantity, production_date, expiry_date, supplier, last_in_time)
SELECT 1, 1, id, CONCAT('BATCH-', DATE_FORMAT(NOW(), '%Y%m%d'), '-001'), 100, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), '广州美容供应商', NOW()
FROM stock_product WHERE deleted = 0;

INSERT INTO `stock_record` (tenant_id, store_id, product_id, batch_no, type, quantity, before_qty, after_qty, unit_price, total_amount, supplier, operator_id, operator_name, remark)
SELECT 1, 1, id, CONCAT('BATCH-', DATE_FORMAT(NOW(), '%Y%m%d'), '-001'), 1, 100, 0, 100, cost_price, cost_price * 100, '广州美容供应商', 1, 'admin', '初始入库'
FROM stock_product WHERE deleted = 0;

INSERT INTO `stock_check` (tenant_id, store_id, check_no, check_date, total_items, operator_id, operator_name, status, remark)
VALUES (1, 1, CONCAT('CHK', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s')), CURDATE(), 6, 1, 'admin', 1, '月初盘点');

-- ============================================================
-- 8. 提成（v2，含 settlement + record 两表）
-- 来自：init_m09_commission_v2.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS `commission_rule` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `rule_name` varchar(100) NOT NULL,
  `business_type` varchar(20) NOT NULL,
  `target_id` bigint DEFAULT NULL,
  `calculation_method` varchar(16) NOT NULL,
  `rate` decimal(7,4) DEFAULT NULL,
  `fixed_amount` decimal(12,2) DEFAULT NULL,
  `priority` int NOT NULL DEFAULT 0,
  `effective_date` date NOT NULL,
  `expiry_date` date DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `remark` varchar(500),
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  INDEX `idx_comm_rule_match` (`tenant_id`, `store_id`, `business_type`, `target_id`, `status`),
  INDEX `idx_comm_rule_effective` (`effective_date`, `expiry_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工提成规则';

CREATE TABLE IF NOT EXISTS `commission_record` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(64) NOT NULL,
  `rule_id` bigint NOT NULL,
  `rule_name` varchar(100) NOT NULL,
  `business_type` varchar(20) NOT NULL,
  `source_type` varchar(20) NOT NULL,
  `source_id` bigint NOT NULL,
  `source_item_id` bigint NOT NULL,
  `order_no` varchar(64),
  `service_id` bigint,
  `service_name` varchar(128),
  `base_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `calculation_method` varchar(16) NOT NULL,
  `rate` decimal(7,4),
  `fixed_amount` decimal(12,2),
  `commission_amount` decimal(12,2) NOT NULL,
  `business_time` datetime NOT NULL,
  `salary_month` char(7) NOT NULL,
  `settlement_id` bigint DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `invalid_reason` varchar(255),
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_comm_source_item` (`source_type`, `source_item_id`),
  INDEX `idx_comm_record_employee_month` (`employee_id`, `salary_month`, `status`),
  INDEX `idx_comm_record_order` (`source_id`, `order_no`),
  INDEX `idx_comm_record_settlement` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工提成记录';

CREATE TABLE IF NOT EXISTS `commission_settlement` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `settlement_no` varchar(64) NOT NULL,
  `salary_month` char(7) NOT NULL,
  `employee_count` int NOT NULL DEFAULT 0,
  `total_commission` decimal(12,2) NOT NULL DEFAULT 0,
  `total_adjustment` decimal(12,2) NOT NULL DEFAULT 0,
  `total_payable` decimal(12,2) NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 1,
  `confirm_time` datetime,
  `confirm_by` bigint,
  `pay_time` datetime,
  `pay_by` bigint,
  `remark` varchar(500),
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_settlement_no` (`settlement_no`),
  INDEX `idx_settlement_month` (`salary_month`, `store_id`),
  INDEX `idx_settlement_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提成结算单';

CREATE TABLE IF NOT EXISTS `commission_settlement_record` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `store_id` bigint NOT NULL,
  `settlement_id` bigint NOT NULL,
  `commission_record_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(64) NOT NULL,
  `base_salary` decimal(12,2) NOT NULL DEFAULT 0,
  `commission_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `adjustment_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `payable_amount` decimal(12,2) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_settlement_commission` (`settlement_id`, `commission_record_id`),
  INDEX `idx_settlement_employee` (`settlement_id`, `employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算单与提成记录关联';

INSERT INTO `commission_rule`
(`tenant_id`, `store_id`, `rule_name`, `business_type`, `target_id`, `calculation_method`, `rate`, `priority`, `effective_date`, `status`, `remark`)
SELECT 1, 1, 'service default 10%', 'SERVICE', NULL, 'RATE', 0.1000, 10, '2000-01-01', 1, 'm09 default'
WHERE NOT EXISTS (
  SELECT 1 FROM `commission_rule`
  WHERE `tenant_id`=1 AND `store_id`=1 AND `business_type`='SERVICE' AND `target_id` IS NULL AND `deleted`=0
);

-- ============================================================
-- 9. P0-DB-001 数据修复（孤儿重派 + NULL tenant 回填）
-- 来自：patch_p0_db_001_data_repair.sql
-- 幂等：已修复的数据库再次执行 0 影响行
-- ============================================================
-- 修复 A: crm_wallet_transaction.operator_id 孤儿
UPDATE crm_wallet_transaction wt
INNER JOIN sys_user u ON u.username = 'admin'
SET wt.operator_id = u.id
WHERE NOT EXISTS (SELECT 1 FROM sys_user u2 WHERE u2.id = wt.operator_id)
  AND wt.operator_id IS NOT NULL;

-- 修复 B: biz_booking.operator_id 孤儿
UPDATE biz_booking b
INNER JOIN sys_user u ON u.username = 'admin'
SET b.operator_id = u.id
WHERE NOT EXISTS (SELECT 1 FROM sys_user u2 WHERE u2.id = b.operator_id)
  AND b.operator_id IS NOT NULL;

-- 修复 C: crm_wallet.tenant_id NULL 回填
UPDATE crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
SET w.tenant_id = m.tenant_id
WHERE w.tenant_id IS NULL
  AND m.tenant_id IS NOT NULL;

-- ============================================================
-- 最终验收
-- ============================================================
SELECT '=== V0 Baseline ALL 迁移完成 ===' AS step;

-- 6 张 P0 表
SELECT 'crm_wallet' AS tbl, COUNT(*) AS cnt FROM crm_wallet
UNION ALL SELECT 'crm_wallet_transaction', COUNT(*) FROM crm_wallet_transaction
UNION ALL SELECT 'biz_booking',              COUNT(*) FROM biz_booking
UNION ALL SELECT 'biz_room',                 COUNT(*) FROM biz_room
UNION ALL SELECT 'biz_employee_schedule',    COUNT(*) FROM biz_employee_schedule
UNION ALL SELECT 'sys_login_log',            COUNT(*) FROM sys_login_log
UNION ALL SELECT 'sys_log',                  COUNT(*) FROM sys_log;

-- 孤儿检查
SELECT '=== FK-5 wallet_txn orphans (期望 0) ===' AS step;
SELECT COUNT(*) AS orphans FROM crm_wallet_transaction wt
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = wt.operator_id)
  AND wt.operator_id IS NOT NULL;

SELECT '=== FK-10 booking orphans (期望 0) ===' AS step;
SELECT COUNT(*) AS orphans FROM biz_booking b
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = b.operator_id)
  AND b.operator_id IS NOT NULL;

-- NULL tenant
SELECT '=== crm_wallet NULL tenant (期望 0) ===' AS step;
SELECT COUNT(*) AS null_tenants FROM crm_wallet WHERE tenant_id IS NULL;

-- 跨租户污染
SELECT '=== cross-tenant contamination (期望 0) ===' AS step;
SELECT COUNT(*) AS mismatches
FROM crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
WHERE w.tenant_id IS NOT NULL
  AND m.tenant_id IS NOT NULL
  AND w.tenant_id <> m.tenant_id;
