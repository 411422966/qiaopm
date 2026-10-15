-- =====================================================
-- R2.0-A 跨门店数据隔离验证 seed（ASCII 版，避免 GBK 乱码）
-- =====================================================

USE meiyetec;

-- 1. 门店 B（不存在则插入）
INSERT INTO pos_store (id, store_name, store_code, contact_person, contact_phone, address, status)
SELECT 2, 'Store B', 'STORE002', 'AdminB', '13800138002', 'Shanghai', 1
WHERE NOT EXISTS (SELECT 1 FROM pos_store WHERE id = 2);

-- 2. 角色：保证有 SUPER_ADMIN / STORE_ADMIN / EMPLOYEE
INSERT IGNORE INTO sys_role (id, role_code, role_name, description) VALUES
(1, 'SUPER_ADMIN', 'Super Admin', 'platform'),
(2, 'STORE_ADMIN', 'Store Admin',  'single store'),
(3, 'EMPLOYEE',    'Employee',     'basic');

-- 3. 数据范围
INSERT IGNORE INTO sys_role_data_scope (id, role_id, data_scope, data_scope_value) VALUES
(1, 1, 'ALL',   NULL),
(2, 2, 'STORE', NULL),
(3, 3, 'SELF',  NULL);

-- 4. 测试用户（密码 123456 的 BCrypt；新装库请用 123456 登录）
INSERT IGNORE INTO sys_user (id, username, password, real_name, phone, status, store_id, tenant_id) VALUES
(100, 'store_admin_a', '$2a$10$6I0.9H0hK9mbKFPnSClLBukD9oElGsqRJuDeop.XJzOVNHxWM7ZP2', 'AdminA', '13900000001', 1, 1, 1),
(101, 'store_admin_b', '$2a$10$6I0.9H0hK9mbKFPnSClLBukD9oElGsqRJuDeop.XJzOVNHxWM7ZP2', 'AdminB', '13900000002', 1, 2, 1);

-- 5. 用户角色
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (100, 2), (101, 2);

-- 6. 测试会员：门店 A / B 各 2 个
INSERT IGNORE INTO crm_member (id, member_no, name, phone, store_id, tenant_id, balance, status) VALUES
(9001, 'M9001', 'CustA-1', '13800009001', 1, 1, 1000.00, 1),
(9002, 'M9002', 'CustA-2', '13800009002', 1, 1, 2000.00, 1),
(9003, 'M9003', 'CustB-1', '13800009003', 2, 1, 3000.00, 1),
(9004, 'M9004', 'CustB-2', '13800009004', 2, 1, 4000.00, 1);

SELECT 'R2.0-A verification seed applied' AS done;
