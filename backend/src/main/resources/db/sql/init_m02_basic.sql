-- =============================================
-- 美业SaaS系统 - M02 基础数据模块数据库初始化
-- =============================================

USE meiyetec;

-- =============================================
-- 1. 门店表 pos_store
-- =============================================
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

-- =============================================
-- 2. 员工表 pos_employee
-- =============================================
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

-- =============================================
-- 3. 会员等级表 mem_level
-- =============================================
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

-- =============================================
-- 4. 分类表 sys_category
-- =============================================
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

-- =============================================
-- 5. 初始化数据
-- =============================================

-- 插入默认门店
INSERT INTO pos_store (store_name, store_code, contact_person, contact_phone, address, province, city, district, business_hours, status) VALUES
('美业SaaS示范店', 'STORE001', '管理员', '13800138001', '北京市朝阳区建国路88号', '北京市', '北京市', '朝阳区', '09:00-21:00', 1);

-- 插入会员等级（从低到高）
INSERT INTO mem_level (level_code, level_name, min_points, max_points, discount_rate, level_color, sort_order, description) VALUES
('LEVEL_PURPLE', '紫钻会员', 0, 999, 0.95, '#9B59B6', 1, '新注册会员，享受95折优惠'),
('LEVEL_GOLD', '金卡会员', 1000, 4999, 0.90, '#F1C40F', 2, '累计消费1000积分，享受9折优惠'),
('LEVEL_BLACK', '黑金会员', 5000, 9999, 0.85, '#2C3E50', 3, '累计消费5000积分，享受85折优惠'),
('LEVEL_DIAMOND', '钻石会员', 10000, NULL, 0.80, '#3498DB', 4, '累计消费10000积分，享受8折优惠，生日双倍积分');

-- 插入商品分类
INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type) VALUES
('GOODS', '服务项目', 0, 1, '0', 1, 'goods'),
('PRODUCT', '商品', 0, 1, '0', 2, 'product');

-- 服务项目子分类
INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type) 
SELECT 'HAIR', '美发', id, 2, CONCAT('0,', id), 1, 'goods' FROM sys_category WHERE category_code = 'GOODS';

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type) 
SELECT 'NAIL', '美甲', id, 2, CONCAT('0,', id), 2, 'goods' FROM sys_category WHERE category_code = 'GOODS';

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type) 
SELECT 'SKIN', '美容护肤', id, 2, CONCAT('0,', id), 3, 'goods' FROM sys_category WHERE category_code = 'GOODS';

-- 商品子分类
INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type) 
SELECT 'COSMETICS', '化妆品', id, 2, CONCAT('0,', id), 1, 'product' FROM sys_category WHERE category_code = 'PRODUCT';

INSERT INTO sys_category (category_code, category_name, parent_id, level, path, sort_order, category_type) 
SELECT 'SKIN_CARE', '护肤品', id, 2, CONCAT('0,', id), 2, 'product' FROM sys_category WHERE category_code = 'PRODUCT';

-- =============================================
-- 6. 操作日志表 sys_log（P0-DB-001-E 回填）
-- =============================================
-- 历史来源：fix_missing_tables.sql:10-32
-- 上下文：LogAspect 写入 / SysLogController 读取
-- 注意：SysLog Entity 暂未含 tenantId 字段（out-of-scope），列先建好等待 Java 端补齐
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

-- =============================================
-- 验证查询
-- =============================================
SELECT '=== 门店表 ===' AS '';
SELECT * FROM pos_store;

SELECT '=== 会员等级表 ===' AS '';
SELECT * FROM mem_level;

SELECT '=== 分类表 ===' AS '';
SELECT * FROM sys_category;

SELECT '=== 操作日志表 ===' AS '';
SHOW TABLES LIKE 'sys_log';
