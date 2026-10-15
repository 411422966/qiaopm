-- ============================================
-- R2.1-A：项目/疗程管理 - 数据库初始化
-- 2026-08-08
-- ============================================

-- 1. 项目分类
DROP TABLE IF EXISTS biz_service_category;
CREATE TABLE biz_service_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    category_code VARCHAR(64) NOT NULL COMMENT '分类编码',
    category_name VARCHAR(128) NOT NULL COMMENT '分类名称',
    parent_id BIGINT DEFAULT 0 COMMENT '父级分类（0=根）',
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
    remark VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_category (tenant_id, category_code),
    INDEX idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务项目分类';

-- 2. 服务项目（更完整的实体，比 biz_service 多字段）
DROP TABLE IF EXISTS biz_service_item;
CREATE TABLE biz_service_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    service_code VARCHAR(64) NOT NULL COMMENT '项目编码',
    service_name VARCHAR(128) NOT NULL COMMENT '项目名称',
    category_id BIGINT COMMENT '分类ID',
    unit_price DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '标准价',
    member_price DECIMAL(10,2) COMMENT '会员价',
    cost_price DECIMAL(10,2) DEFAULT 0 COMMENT '成本价（耗材加权）',
    duration INT DEFAULT 0 COMMENT '服务时长(分钟)',
    unit VARCHAR(16) DEFAULT '次' COMMENT '单位',
    description VARCHAR(500),
    image_url VARCHAR(500),
    sop TEXT COMMENT 'SOP 操作流程',
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
    sort_order INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, service_code),
    INDEX idx_category (category_id),
    INDEX idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务项目';

-- 3. 疗程方案（针对某个项目组合的套餐化方案）
DROP TABLE IF EXISTS biz_treatment_plan;
CREATE TABLE biz_treatment_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    plan_code VARCHAR(64) NOT NULL COMMENT '方案编码',
    plan_name VARCHAR(128) NOT NULL COMMENT '方案名称（如：美白亮肤 8 次）',
    service_id BIGINT NOT NULL COMMENT '对应服务项目 biz_service_item.id',
    category_id BIGINT COMMENT '分类ID（冗余）',
    total_sessions INT NOT NULL DEFAULT 1 COMMENT '总次数',
    cycle_days INT DEFAULT 7 COMMENT '建议周期（天）',
    plan_price DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '方案总价',
    member_price DECIMAL(10,2) COMMENT '会员价',
    description VARCHAR(500),
    sop TEXT COMMENT '整体 SOP',
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_plan_code (tenant_id, plan_code),
    INDEX idx_service (service_id),
    INDEX idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='疗程方案';

-- 4. 疗程阶段
DROP TABLE IF EXISTS biz_treatment_phase;
CREATE TABLE biz_treatment_phase (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    plan_id BIGINT NOT NULL COMMENT '疗程方案ID',
    phase_no INT NOT NULL DEFAULT 1 COMMENT '阶段序号（从1开始）',
    phase_name VARCHAR(128) NOT NULL COMMENT '阶段名称（如：导入期、强化期、维护期）',
    session_count INT NOT NULL DEFAULT 1 COMMENT '本阶段次数',
    sop TEXT COMMENT '阶段 SOP',
    expected_duration INT DEFAULT 0 COMMENT '预计时长(分钟)',
    sort_order INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_plan_phase (plan_id, phase_no),
    INDEX idx_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='疗程阶段';

-- 5. 会员疗程进度（每个会员购买了某个方案后的实例）
DROP TABLE IF EXISTS member_treatment_progress;
CREATE TABLE member_treatment_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    member_id BIGINT NOT NULL COMMENT '会员ID',
    plan_id BIGINT NOT NULL COMMENT '疗程方案ID',
    service_id BIGINT NOT NULL COMMENT '服务项目ID',
    package_order_id BIGINT DEFAULT NULL COMMENT '关联的套餐订单ID',
    total_sessions INT NOT NULL DEFAULT 1 COMMENT '总次数',
    used_sessions INT NOT NULL DEFAULT 0 COMMENT '已用次数',
    remaining_sessions INT NOT NULL DEFAULT 1 COMMENT '剩余次数',
    current_phase_no INT DEFAULT 1 COMMENT '当前阶段序号',
    started_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    last_session_at DATETIME DEFAULT NULL COMMENT '上次服务时间',
    expected_finish_at DATETIME DEFAULT NULL COMMENT '预计完成时间',
    finished_at DATETIME DEFAULT NULL COMMENT '实际完成时间',
    status TINYINT DEFAULT 1 COMMENT '1进行中 2已完成 3已停用',
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_member (member_id),
    INDEX idx_plan (plan_id),
    INDEX idx_status (status),
    INDEX idx_member_status (member_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员疗程进度';

-- 6. 会员疗程服务记录（每次到店做服务记一笔）
DROP TABLE IF EXISTS member_treatment_session;
CREATE TABLE member_treatment_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    progress_id BIGINT NOT NULL COMMENT 'member_treatment_progress.id',
    member_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    phase_no INT NOT NULL,
    session_no INT NOT NULL COMMENT '本阶段内的第几次',
    order_id BIGINT DEFAULT NULL COMMENT '关联订单',
    employee_id BIGINT DEFAULT NULL COMMENT '技师ID',
    employee_name VARCHAR(64),
    session_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '服务时间',
    remark VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_progress (progress_id),
    INDEX idx_member (member_id),
    INDEX idx_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员疗程服务记录';

-- 7. RBAC：项目/疗程权限码（挂在 setting 父菜单下，没有 biz 父菜单）
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'biz:service:item', '服务项目管理', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') t LIMIT 1),
       50;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'biz:service:item:export', '服务项目导出', 'button',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='biz:service:item') t LIMIT 1),
       10;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'biz:treatment:plan', '疗程方案管理', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') t LIMIT 1),
       51;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'biz:treatment:plan:export', '疗程方案导出', 'button',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='biz:treatment:plan') t LIMIT 1),
       10;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN')
  AND p.permission_code IN ('biz:service:item','biz:service:item:export','biz:treatment:plan','biz:treatment:plan:export');

-- 8. 演示数据：3 个项目分类
INSERT IGNORE INTO biz_service_category (tenant_id, category_id, store_id, category_code, category_name, sort_order, status)
VALUES
    (1, NULL, 1, 'CAT-FACE', '面部护理', 1, 1),
    (1, NULL, 1, 'CAT-BODY', '身体护理', 2, 1),
    (1, NULL, 1, 'CAT-ANTI', '抗衰疗程', 3, 1)
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);

-- 9. 演示数据：4 个服务项目
INSERT IGNORE INTO biz_service_item (tenant_id, store_id, service_code, service_name, category_id, unit_price, member_price, duration, unit, description, sop, status, sort_order)
VALUES
    (1, 1, 'SI-FACE-001', '面部护理SPA', (SELECT id FROM biz_service_category WHERE category_code='CAT-FACE' LIMIT 1),
     298.00, 198.00, 90, '次', '深层清洁 + 补水 SPA', '① 卸妆洁面 → ② 热敷打开毛孔 → ③ 玻尿酸精华导入 → ④ 面膜修复 → ⑤ 保湿霜收尾', 1, 1),
    (1, 1, 'SI-FACE-002', '美白亮肤护理', (SELECT id FROM biz_service_category WHERE category_code='CAT-FACE' LIMIT 1),
     498.00, 398.00, 120, '次', '美白淡斑，均匀肤色', '① 洁面 → ② 美白精华按摩 → ③ 光电导入 → ④ 美白面膜 → ⑤ 防晒收尾', 1, 2),
    (1, 1, 'SI-BODY-001', '全身舒缓按摩', (SELECT id FROM biz_service_category WHERE category_code='CAT-BODY' LIMIT 1),
     398.00, 298.00, 90, '次', '舒缓放松，肩颈腰背', '① 精油开背 → ② 推拿按摩 → ③ 热敷 → ④ 拉伸', 1, 1),
    (1, 1, 'SI-ANTI-001', '抗皱紧致疗程', (SELECT id FROM biz_service_category WHERE category_code='CAT-ANTI' LIMIT 1),
     1280.00, 980.00, 150, '次', '射频紧致，淡化细纹', '① 洁面 → ② 射频探头 → ③ 紧致面膜 → ④ 防晒', 1, 1)
ON DUPLICATE KEY UPDATE service_name = VALUES(service_name);

-- 10. 演示：2 个疗程方案
INSERT IGNORE INTO biz_treatment_plan (tenant_id, store_id, plan_code, plan_name, service_id, category_id, total_sessions, cycle_days, plan_price, member_price, description, sop, status)
VALUES
    (1, 1, 'TP-WHITE-08', '美白亮肤 8 次套餐', (SELECT id FROM biz_service_item WHERE service_code='SI-FACE-002' LIMIT 1),
     (SELECT id FROM biz_service_category WHERE category_code='CAT-FACE' LIMIT 1), 8, 14,
     3980.00, 2980.00, '8 次美白，14 天/次，约 3 个月一个疗程', '每周 1~2 次，连续 4 周 → 强化 2 周 → 维护 2 周', 1),
    (1, 1, 'TP-ANTI-12', '抗皱紧致 12 次方案', (SELECT id FROM biz_service_item WHERE service_code='SI-ANTI-001' LIMIT 1),
     (SELECT id FROM biz_service_category WHERE category_code='CAT-ANTI' LIMIT 1), 12, 14,
     14800.00, 11800.00, '12 次抗皱紧致，约 6 个月', '密集 6 周 → 修复 4 周 → 维护 2 周', 1)
ON DUPLICATE KEY UPDATE plan_name = VALUES(plan_name);

-- 11. 演示：TP-WHITE-08 三个阶段
INSERT IGNORE INTO biz_treatment_phase (tenant_id, plan_id, phase_no, phase_name, session_count, sop, expected_duration, sort_order)
SELECT 1, p.id, 1, '导入期', 2, '前 2 次以小剂量低频导入，让肌肤建立耐受', 120, 1
FROM biz_treatment_plan p WHERE p.plan_code = 'TP-WHITE-08';

INSERT IGNORE INTO biz_treatment_phase (tenant_id, plan_id, phase_no, phase_name, session_count, sop, expected_duration, sort_order)
SELECT 1, p.id, 2, '强化期', 4, '3~6 次强化美白，可叠加光电导入', 120, 2
FROM biz_treatment_plan p WHERE p.plan_code = 'TP-WHITE-08';

INSERT IGNORE INTO biz_treatment_phase (tenant_id, plan_id, phase_no, phase_name, session_count, sop, expected_duration, sort_order)
SELECT 1, p.id, 3, '维护期', 2, '7~8 次以巩固为主', 90, 3
FROM biz_treatment_plan p WHERE p.plan_code = 'TP-WHITE-08';

-- 12. 把已存在的 biz_service_material 演示数据迁移到 biz_service_item（如果存在）
-- SVC-001 在 M07 数据中是演示服务，biz_service_material 也演示绑定 SP-BOT-001
-- 这里不强制迁移，因为 biz_service_item 与 biz_service 是两个不同表
-- 业务上保持兼容：service_item 是新版"项目"，biz_service 是旧版"服务"

-- 验证
SELECT 'biz_service_category' tbl, COUNT(*) cnt FROM biz_service_category
UNION ALL SELECT 'biz_service_item', COUNT(*) FROM biz_service_item
UNION ALL SELECT 'biz_treatment_plan', COUNT(*) FROM biz_treatment_plan
UNION ALL SELECT 'biz_treatment_phase', COUNT(*) FROM biz_treatment_phase;
