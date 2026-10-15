-- =============================================
-- 美业SaaS系统 - M01 登录认证模块数据库初始化
-- =============================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS meiyetec CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE meiyetec;

-- =============================================
-- 1. 系统用户表
-- =============================================
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

-- =============================================
-- 2. 系统角色表
-- =============================================
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

-- =============================================
-- 3. 权限表
-- =============================================
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

-- =============================================
-- 4. 用户角色关联表
-- =============================================
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

-- =============================================
-- 5. 角色权限关联表
-- =============================================
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

-- =============================================
-- 6. 初始化数据
-- =============================================

-- 插入默认角色
INSERT INTO sys_role (role_code, role_name, description) VALUES
('SUPER_ADMIN', '超级管理员', '系统超级管理员，拥有所有权限'),
('STORE_ADMIN', '门店管理员', '门店管理员，管理门店所有业务'),
('EMPLOYEE', '员工', '普通员工，拥有基础操作权限');

-- 插入默认管理员用户（密码：123456）
-- BCrypt 加密后的密码：$2a$10$6I0.9H0hK9mbKFPnSClLBukD9oElGsqRJuDeop.XJzOVNHxWM7ZP2
INSERT INTO sys_user (username, password, real_name, phone, status) VALUES
('admin', '$2a$10$6I0.9H0hK9mbKFPnSClLBukD9oElGsqRJuDeop.XJzOVNHxWM7ZP2', '系统管理员', '13800138000', 1);

-- 绑定管理员角色
INSERT INTO sys_user_role (user_id, role_id) 
SELECT u.id, r.id FROM sys_user u, sys_role r 
WHERE u.username = 'admin' AND r.role_code = 'SUPER_ADMIN';

-- 插入基础权限
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

-- 插入权限的按钮级权限
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order) VALUES
('member:list', '查看会员', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 1),
('member:add', '新增会员', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 2),
('member:edit', '编辑会员', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 3),
('member:delete', '删除会员', 'button', (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='member') AS t), 4);

-- 给超级管理员分配所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'SUPER_ADMIN';

-- =============================================
-- 7. 登录日志表 sys_login_log（P0-DB-001-D 回填）
-- =============================================
-- 历史来源：fix_missing_tables.sql:37-52
-- 上下文：AuthController 登录写入 / SysLoginLogController 读取
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

-- =============================================
-- 验证查询
-- =============================================
SELECT '=== 用户表数据 ===' AS '';
SELECT id, username, real_name, status FROM sys_user;

SELECT '=== 角色表数据 ===' AS '';
SELECT * FROM sys_role;

SELECT '=== 权限表数据 ===' AS '';
SELECT * FROM sys_permission;

SELECT '=== 登录日志表 ===' AS '';
SHOW TABLES LIKE 'sys_login_log';
