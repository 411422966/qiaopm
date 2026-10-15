-- ============================================
-- R3.0-A：总部/品牌/门店三级模型
-- 2026-08-09
-- ============================================

-- ============================================
-- 1. 品牌表（新增）
-- ============================================
DROP TABLE IF EXISTS mkt_brand;
CREATE TABLE mkt_brand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '所属租户',
    brand_code VARCHAR(64) NOT NULL COMMENT '品牌编码',
    brand_name VARCHAR(128) NOT NULL COMMENT '品牌名称',
    brand_type VARCHAR(16) DEFAULT 'CHAIN' COMMENT 'CHAIN直营/FRANCHISE加盟/FLAGSHIP旗舰店',
    logo_url VARCHAR(255) COMMENT '品牌Logo',
    contact_person VARCHAR(64) COMMENT '品牌联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    province VARCHAR(50),
    city VARCHAR(50),
    district VARCHAR(50),
    address VARCHAR(255),
    -- 策略配置
    member_share_scope VARCHAR(16) DEFAULT 'BRAND' COMMENT 'BRAND品牌共享/STORE门店独立',
    points_share_scope VARCHAR(16) DEFAULT 'BRAND' COMMENT 'BRAND品牌积分/STORE门店积分',
    balance_share_scope VARCHAR(16) DEFAULT 'BRAND' COMMENT 'BRAND储值共享/STORE门店储值',
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
    priority INT DEFAULT 0 COMMENT '排序',
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, brand_code),
    INDEX idx_tenant (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌';

-- ============================================
-- 2. pos_store 扩展：增加 tenant_id / brand_id（ALTER，不重建表）
-- ============================================
-- 新增字段
ALTER TABLE pos_store
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '所属租户' AFTER id,
    ADD COLUMN brand_id BIGINT COMMENT '所属品牌' AFTER tenant_id,
    ADD COLUMN store_type VARCHAR(16) DEFAULT 'DIRECT' COMMENT 'DIRECT直营/FRANCHISE加盟/FLAGSHIP旗舰店',
    ADD COLUMN region_code VARCHAR(32) COMMENT '区域编码（如华东-001）',
    ADD COLUMN is_hq TINYINT DEFAULT 0 COMMENT '是否总部（1=总部）' AFTER status,
    ADD COLUMN manager_employee_id BIGINT COMMENT '门店负责人',
    ADD COLUMN manager_name VARCHAR(64) COMMENT '负责人姓名',
    ADD COLUMN open_date DATE COMMENT '开业日期',
    ADD COLUMN brand_priority INT DEFAULT 0 COMMENT '品牌内排序' AFTER priority,
    ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER create_time;

-- 为 pos_store 的 tenant_id 建索引
ALTER TABLE pos_store ADD INDEX idx_tenant (tenant_id);
ALTER TABLE pos_store ADD INDEX idx_brand (brand_id);
ALTER TABLE pos_store ADD INDEX idx_tenant_status (tenant_id, status, deleted);

-- ============================================
-- 3. 员工-门店 多对多映射（一个员工可在多个门店工作）
-- ============================================
DROP TABLE IF EXISTS pos_employee_store;
CREATE TABLE pos_employee_store (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    brand_id BIGINT COMMENT '冗余用于快速过滤',
    tenant_id BIGINT NOT NULL,
    role_in_store VARCHAR(32) DEFAULT 'STAFF' COMMENT '店长/副店长/技师/顾问/前台',
    is_primary TINYINT DEFAULT 0 COMMENT '是否主店',
    status TINYINT DEFAULT 1 COMMENT '1在职 0离职',
    join_date DATE COMMENT '入职日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_emp_store (employee_id, store_id),
    INDEX idx_employee (employee_id, status),
    INDEX idx_store (store_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工-门店关联';

-- ============================================
-- 4. 角色升级：增加品牌级 / 总部级角色
-- ============================================
-- sys_role 新增角色
INSERT IGNORE INTO sys_role (role_code, role_name, role_level, tenant_id, data_scope, remark, status)
SELECT 'HQ_ADMIN', '总部管理员', 'HQ', 1, 'ALL', '总部管理员，可查看所有品牌和门店', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'HQ_ADMIN' AND deleted = 0);

INSERT IGNORE INTO sys_role (role_code, role_name, role_level, tenant_id, data_scope, remark, status)
SELECT 'BRAND_ADMIN', '品牌管理员', 'BRAND', 1, 'BRAND', '品牌管理员，可查看品牌下所有门店', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'BRAND_ADMIN' AND deleted = 0);

INSERT IGNORE INTO sys_role (role_code, role_name, role_level, tenant_id, data_scope, remark, status)
SELECT 'BRAND_STAFF', '品牌员工', 'BRAND', 1, 'BRAND', '品牌员工，可查看品牌下所有门店数据', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'BRAND_STAFF' AND deleted = 0);

-- 角色菜单权限：总部管理员拥有所有菜单
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r, sys_menu m
WHERE r.role_code = 'HQ_ADMIN' AND m.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- 品牌管理员的菜单（暂不细化，逐步授权）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r, sys_menu m
WHERE r.role_code = 'BRAND_ADMIN' AND m.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- ============================================
-- 5. 数据范围过滤器（BE 层使用）
-- ============================================
-- sys_user 扩展字段
ALTER TABLE sys_user
    ADD COLUMN brand_id BIGINT DEFAULT NULL COMMENT '所属品牌（品牌管理员）' AFTER store_id,
    ADD COLUMN data_scope VARCHAR(16) DEFAULT 'STORE' COMMENT 'STORE本店/BRAND品牌/HQ总部' AFTER brand_id;

-- ============================================
-- 6. 演示数据
-- ============================================
-- 品牌
INSERT IGNORE INTO mkt_brand
(id, tenant_id, brand_code, brand_name, brand_type, contact_person, contact_phone, province, city, status, operator_name)
VALUES
(1, 1, 'BRAND-001', '美业科技直营品牌', 'CHAIN', '张三', '13800138000', '广东', '深圳', 1, 'system');

-- 更新 pos_store 加 brand_id（默认归属 brand-001）
UPDATE pos_store SET brand_id = 1 WHERE deleted = 0 AND brand_id IS NULL;

-- 新增总部门店（is_hq = 1）
INSERT IGNORE INTO pos_store
(id, tenant_id, brand_id, store_name, store_code, store_type, is_hq, contact_person, contact_phone, province, city, status, operator_name)
SELECT 999, 1, 1, '美业科技总部', 'HQ-001', 'FLAGSHIP', 1, '张三', '13800138000', '广东', '深圳', 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM pos_store WHERE id = 999 AND deleted = 0);

-- ============================================
-- 7. RBAC 权限码
-- ============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'org:brand', '品牌管理', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') t LIMIT 1), 85;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'org:store', '门店管理', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='org:brand') t LIMIT 1), 10;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'org:employee', '员工管理', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='org:brand') t LIMIT 1), 11;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'org:hq', '总部看板', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='org:brand') t LIMIT 1), 12;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','HQ_ADMIN','BRAND_ADMIN')
  AND p.permission_code IN ('org:brand','org:store','org:employee','org:hq');

-- ============================================
-- 8. 验证
-- ============================================
SELECT 'mkt_brand' tbl, COUNT(*) cnt FROM mkt_brand WHERE deleted=0
UNION ALL SELECT 'pos_store (brand_id)', COUNT(*) FROM pos_store WHERE deleted=0 AND brand_id IS NOT NULL
UNION ALL SELECT 'pos_employee_store', COUNT(*) FROM pos_employee_store WHERE deleted=0
UNION ALL SELECT 'HQ_ADMIN role', COUNT(*) FROM sys_role WHERE role_code='HQ_ADMIN' AND deleted=0
UNION ALL SELECT 'BRAND_ADMIN role', COUNT(*) FROM sys_role WHERE role_code='BRAND_ADMIN' AND deleted=0;
