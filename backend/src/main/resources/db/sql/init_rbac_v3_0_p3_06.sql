-- ============================================
-- P3 · T-P06：医美电子知情同意与药械追溯
-- 2026-08-09
-- ============================================

-- ============================================
-- 1. 知情同意书模板（合规字段）
-- ============================================
DROP TABLE IF EXISTS mh_consent_template;
CREATE TABLE mh_consent_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    -- 类型
    template_type VARCHAR(32) NOT NULL COMMENT 'GENERAL通用/SURGERY手术/INJECTION注射/LASER激光/SKINCARE护肤/PHOTO影像',
    -- 关联项目
    project_id BIGINT,
    project_name VARCHAR(128),
    -- 内容（富文本，支持 Markdown）
    content_md TEXT NOT NULL,
    -- 必勾选条款
    required_items VARCHAR(2000) COMMENT '必勾选条款（JSON 数组）',
    -- 风险告知
    risks_md TEXT COMMENT '风险告知',
    -- 适用法律
    legal_basis VARCHAR(500) COMMENT '法规依据',
    -- 版本
    version VARCHAR(16) DEFAULT '1.0',
    -- 状态
    audit_status TINYINT DEFAULT 1 COMMENT '1待审核 2已审核 3驳回',
    enabled TINYINT DEFAULT 1,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, template_code, version),
    INDEX idx_type (template_type, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知情同意书模板';

-- ============================================
-- 2. 知情同意签署（带签名）
-- ============================================
DROP TABLE IF EXISTS mh_consent_signature;
CREATE TABLE mh_consent_signature (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    store_id BIGINT,
    member_id BIGINT NOT NULL,
    -- 模板
    template_id BIGINT NOT NULL,
    template_code VARCHAR(64),
    template_version VARCHAR(16),
    -- 关联
    project_id BIGINT,
    project_name VARCHAR(128),
    order_id BIGINT,
    order_no VARCHAR(64),
    -- 签署
    signature_id VARCHAR(64) NOT NULL,
    -- 文档
    sign_doc_url VARCHAR(500) NOT NULL COMMENT '签署 PDF',
    sign_doc_hash VARCHAR(128) NOT NULL COMMENT '文档 SHA-256（防篡改）',
    -- 签署人
    signer_name VARCHAR(64) NOT NULL,
    signer_id_no VARCHAR(32) COMMENT '身份证（脱敏）',
    signer_relation VARCHAR(32) DEFAULT 'SELF' COMMENT 'SELF本人/PARENT监护人/AGENT代理人',
    -- 见证人（医美必须医师见证）
    witness_id BIGINT,
    witness_name VARCHAR(64),
    witness_license_no VARCHAR(64) COMMENT '医师执业证号',
    -- 必勾选条款（JSON）
    checked_items VARCHAR(2000),
    -- 备注
    remark VARCHAR(500),
    -- 时间
    effective_at DATETIME NOT NULL,
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1已签署 2已撤回 3已过期',
    revoked_at DATETIME,
    revoked_reason VARCHAR(500),
    -- IP / 设备
    sign_ip VARCHAR(64),
    sign_device VARCHAR(128),
    -- 操作
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_signature_id (signature_id),
    INDEX idx_member (member_id, status),
    INDEX idx_template (template_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知情同意签署';

-- ============================================
-- 3. 药械追溯主档
-- ============================================
DROP TABLE IF EXISTS mh_drug_device;
CREATE TABLE mh_drug_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    -- 基础信息
    item_code VARCHAR(64) NOT NULL COMMENT '物料编码',
    item_name VARCHAR(128) NOT NULL,
    item_type VARCHAR(32) NOT NULL COMMENT 'DRUG药品/DEVICE器械/CONSUMABLE耗材',
    -- 注册证号（药监局）
    registration_no VARCHAR(64) NOT NULL COMMENT '国药准字/注册证号',
    -- 规格
    specification VARCHAR(128),
    manufacturer VARCHAR(128),
    -- 类别
    category VARCHAR(64) COMMENT 'BOTOX肉毒/FILLER玻尿酸/SOLDIER线雕/LASER激光/...',
    -- 储存条件
    storage_condition VARCHAR(128),
    -- 效期
    shelf_life_days INT,
    -- 状态
    enabled TINYINT DEFAULT 1,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, item_code),
    INDEX idx_reg (registration_no),
    INDEX idx_type (item_type, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药械主档';

-- ============================================
-- 4. 药械批次/库存
-- ============================================
DROP TABLE IF EXISTS mh_drug_device_batch;
CREATE TABLE mh_drug_device_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    drug_device_id BIGINT NOT NULL,
    -- 批次
    batch_no VARCHAR(64) NOT NULL COMMENT '生产批号',
    -- 序列号（器械）
    serial_no VARCHAR(128),
    -- 生产/效期
    production_date DATE,
    expire_date DATE,
    -- 供应商
    supplier_id BIGINT,
    supplier_name VARCHAR(128),
    purchase_order_no VARCHAR(64),
    -- 入库
    inbound_qty INT NOT NULL DEFAULT 0,
    used_qty INT DEFAULT 0,
    remaining_qty INT DEFAULT 0,
    -- 单价
    unit_price DECIMAL(12,2),
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1正常 2冻结 3过期 4召回',
    freeze_reason VARCHAR(500),
    recall_at DATETIME,
    recall_reason VARCHAR(500),
    -- 门店
    store_id BIGINT,
    -- 位置
    location VARCHAR(64),
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_drug_batch (drug_device_id, batch_no),
    INDEX idx_expire (expire_date, status),
    INDEX idx_store (store_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药械批次';

-- ============================================
-- 5. 药械使用追溯（链接订单/会员/医生）
-- ============================================
DROP TABLE IF EXISTS mh_drug_device_usage;
CREATE TABLE mh_drug_device_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    -- 关联
    batch_id BIGINT NOT NULL,
    drug_device_id BIGINT NOT NULL,
    item_name VARCHAR(128),
    -- 使用
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64),
    member_id BIGINT NOT NULL,
    member_name VARCHAR(64),
    -- 医师
    doctor_id BIGINT,
    doctor_name VARCHAR(64),
    doctor_license_no VARCHAR(64),
    -- 数量
    qty INT NOT NULL DEFAULT 1,
    -- 部位
    body_part VARCHAR(64),
    -- 序列号（器械）
    serial_no VARCHAR(128),
    -- 时间
    used_at DATETIME,
    -- 不良反应
    adverse_reaction VARCHAR(1000),
    has_adverse TINYINT DEFAULT 0,
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1已使用 2已撤回',
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_order (order_id),
    INDEX idx_member (member_id),
    INDEX idx_batch (batch_id),
    INDEX idx_doctor (doctor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药械使用追溯';

-- ============================================
-- 6. 演示数据：药械
-- ============================================
INSERT IGNORE INTO mh_drug_device (tenant_id, brand_id, item_code, item_name, item_type, registration_no,
    specification, manufacturer, category, storage_condition, shelf_life_days, enabled, operator_name)
VALUES
(1, 1, 'BOTOX-100U', '保妥适 100U', 'DRUG', '国药准字J20171001', '100U/瓶',
 'Allergan', 'BOTOX肉毒', '2-8℃冷藏', 730, 1, 'system'),
(1, 1, 'FILLER-JV-1ML', '乔雅登 1mL', 'DEVICE', '国械注准20153130001', '1mL/支',
 '艾尔建', 'FILLER玻尿酸', '室温', 1095, 1, 'system'),
(1, 1, 'LINE-PDO-19G', 'PDO 线雕 19G', 'DEVICE', '国械注准20183130002', '10根/盒',
 '韩国某厂商', 'SOLDIER线雕', '室温', 1825, 1, 'system');

INSERT IGNORE INTO mh_drug_device_batch (tenant_id, drug_device_id, batch_no, production_date, expire_date,
    supplier_name, purchase_order_no, inbound_qty, remaining_qty, unit_price, status, store_id, location, operator_name)
VALUES
(1, 1, 'BTX-2026-001', '2026-01-15', '2028-01-14', '国药控股', 'PO-2026-001', 100, 95, 1500.00, 1, 1, 'A1-冷藏柜', 'system'),
(1, 2, 'JV-2026-001', '2026-03-10', '2029-03-09', '国药控股', 'PO-2026-002', 50, 48, 2000.00, 1, 1, 'B2-常温架', 'system');

INSERT IGNORE INTO mh_consent_template (tenant_id, brand_id, template_code, template_name, template_type,
    content_md, required_items, risks_md, legal_basis, version, audit_status, enabled, operator_name)
VALUES
(1, 1, 'CONS-BOTOX-V1', '保妥适注射知情同意书', 'INJECTION',
 '# 保妥适注射知情同意书\n\n您将接受保妥适（A型肉毒毒素）注射治疗。请仔细阅读以下内容。\n\n## 一、治疗目的\n...\n## 二、可能的风险\n...',
 '["了解肉毒毒素的作用机理","已知可能的副作用：局部红肿、淤青、过敏等","同意术后复诊"]',
 '## 已知风险\n- 局部淤青（5-10%）\n- 表情僵硬（一过性）\n- 过敏反应（罕见）',
 '《医疗美容服务管理办法》《医疗机构管理条例》',
 '1.0', 2, 1, 'system');

-- ============================================
-- 7. 权限码
-- ============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mh:consent', '知情同意', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 70;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mh:drug-device', '药械追溯', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 71;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN','DOCTOR','NURSE')
  AND p.permission_code IN ('mh:consent','mh:drug-device');

-- ============================================
-- 8. 验证
-- ============================================
SELECT 'mh_consent_template' tbl, COUNT(*) cnt FROM mh_consent_template WHERE deleted=0
UNION ALL SELECT 'mh_consent_signature', COUNT(*) FROM mh_consent_signature WHERE deleted=0
UNION ALL SELECT 'mh_drug_device', COUNT(*) FROM mh_drug_device WHERE deleted=0
UNION ALL SELECT 'mh_drug_device_batch', COUNT(*) FROM mh_drug_device_batch WHERE deleted=0
UNION ALL SELECT 'mh_drug_device_usage', COUNT(*) FROM mh_drug_device_usage WHERE deleted=0;