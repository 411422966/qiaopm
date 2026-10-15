-- ============================================
-- P3 · T-P03：视觉档案与 AI 对比（合规优先）
-- 2026-08-09
-- ============================================

-- ============================================
-- 1. 视觉档案（皮肤/面部档案）
-- ============================================
DROP TABLE IF EXISTS mh_visual_archive;
CREATE TABLE mh_visual_archive (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    store_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    -- 档案
    archive_code VARCHAR(64) NOT NULL,
    archive_type VARCHAR(32) NOT NULL COMMENT 'FACE面部/SKIN皮肤/BODY体态/HAIR发质/SCAR瘢痕/OTHER',
    -- 部位/项目
    body_part VARCHAR(64) COMMENT '如：面部左颊、颈部',
    project_id BIGINT COMMENT '关联服务项目',
    project_name VARCHAR(128),
    -- 拍照
    image_url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    -- 元数据
    image_hash VARCHAR(128) NOT NULL COMMENT 'SHA-256，用于防篡改',
    -- 脱敏信息
    face_masked TINYINT DEFAULT 1 COMMENT '是否脱敏（0=原始 1=脱敏）',
    face_mask_type VARCHAR(32) COMMENT 'BLUR/EYE/MOSAIC/REPLACE',
    -- AI 分析
    ai_analyzed TINYINT DEFAULT 0,
    ai_features_json TEXT COMMENT 'AI 特征（JSON）',
    ai_tags VARCHAR(500) COMMENT 'AI 标签 CSV：色斑、毛孔、皱纹',
    ai_score DECIMAL(5,2) COMMENT 'AI 综合分 0-100',
    -- 关联订单
    related_order_id BIGINT,
    related_order_no VARCHAR(64),
    -- 拍摄人/环境
    photographer_id BIGINT,
    photographer_name VARCHAR(64),
    device VARCHAR(128) COMMENT '拍摄设备型号',
    env_info VARCHAR(255) COMMENT '环境（灯光/角度）',
    -- 备注
    remark VARCHAR(500),
    -- 撤回（合规）
    revoked TINYINT DEFAULT 0,
    revoked_at DATETIME,
    revoked_reason VARCHAR(500),
    -- 操作
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, archive_code),
    INDEX idx_member (member_id, archive_type),
    INDEX idx_store (store_id, create_time),
    INDEX idx_hash (image_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视觉档案';

-- ============================================
-- 2. AI 对比记录（前后对比）
-- ============================================
DROP TABLE IF EXISTS mh_visual_compare;
CREATE TABLE mh_visual_compare (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    member_id BIGINT NOT NULL,
    -- 对比
    before_id BIGINT NOT NULL COMMENT '前图 archive_id',
    after_id BIGINT NOT NULL COMMENT '后图 archive_id',
    -- 类型
    compare_type VARCHAR(32) NOT NULL COMMENT 'SAME_DAY同日/CROSS_DAY跨日/CROSS_TREATMENT跨疗程',
    -- AI 结果
    similarity DECIMAL(5,4) COMMENT '相似度 0-1',
    diff_score DECIMAL(5,2) COMMENT '差异分 0-100',
    -- 维度
    skin_diff VARCHAR(1000) COMMENT '皮肤维度差异 JSON',
    -- 结论
    ai_summary VARCHAR(2000),
    improvement_areas VARCHAR(500) COMMENT '改善项',
    regression_areas VARCHAR(500) COMMENT '退步项',
    -- 人工复核
    manual_reviewed TINYINT DEFAULT 0,
    reviewer_id BIGINT,
    reviewer_name VARCHAR(64),
    review_note VARCHAR(1000),
    reviewed_at DATETIME,
    -- 模型
    model_version VARCHAR(32),
    operator_name VARCHAR(64) DEFAULT 'system',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member (member_id, create_time),
    INDEX idx_before_after (before_id, after_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 对比记录';

-- ============================================
-- 3. 图像授权（合规）
-- ============================================
DROP TABLE IF EXISTS mh_image_consent;
CREATE TABLE mh_image_consent (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    member_id BIGINT NOT NULL,
    -- 授权范围
    consent_type VARCHAR(32) NOT NULL COMMENT 'STORAGE存储/AI_ANALYSIS分析/ANONYMIZED_PUB匿名公开/STAFF_INTERNAL员工内部',
    -- 授权细节
    scope VARCHAR(500) COMMENT 'JSON：可访问门店、用途、期限',
    -- 授权方式
    sign_method VARCHAR(32) NOT NULL COMMENT 'ELECTRONIC电子签名/HANDWRITTEN手写/WET_INK印泥/OFFLINE_OFFLINE线下',
    -- 签名（电子签名哈希）
    signature_hash VARCHAR(128) COMMENT '电子签名 SHA-256',
    -- 授权人
    signer_name VARCHAR(64),
    signer_id_no VARCHAR(32) COMMENT '身份证后四位（脱敏）',
    signer_relation VARCHAR(32) COMMENT 'SELF本人/PARENT监护人/AGENT代理人',
    -- 有效期
    effective_at DATETIME NOT NULL,
    expire_at DATETIME,
    -- 文件
    consent_doc_url VARCHAR(500) COMMENT '电子授权书 PDF',
    -- 撤回
    revoked TINYINT DEFAULT 0,
    revoked_at DATETIME,
    revoked_reason VARCHAR(500),
    -- 操作
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_member (member_id, consent_type),
    INDEX idx_expire (expire_at, revoked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图像授权';

-- ============================================
-- 4. 撤回申请（合规：会员随时撤回）
-- ============================================
DROP TABLE IF EXISTS mh_image_withdraw;
CREATE TABLE mh_image_withdraw (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    member_id BIGINT NOT NULL,
    -- 撤回范围
    withdraw_scope VARCHAR(32) NOT NULL COMMENT 'ALL全部/AI_TRAINING仅AI训练/STAFF_VIEW仅员工可见',
    target_type VARCHAR(32) COMMENT 'ARCHIVE档案/COMPARE对比/CONSENT授权',
    target_ids VARCHAR(2000) COMMENT '关联 ID CSV',
    -- 原因
    reason VARCHAR(500),
    -- 处理
    status TINYINT DEFAULT 1 COMMENT '1待处理 2已处理 3已驳回',
    handler_id BIGINT,
    handler_name VARCHAR(64),
    handle_remark VARCHAR(500),
    handled_at DATETIME,
    -- 操作
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member (member_id, status),
    INDEX idx_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图像撤回申请';

-- ============================================
-- 5. 权限码
-- ============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mh:visual', '视觉档案', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 60;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mh:visual-compare', 'AI 对比', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 61;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mh:visual-consent', '图像授权', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 62;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN','DOCTOR')
  AND p.permission_code IN ('mh:visual','mh:visual-compare','mh:visual-consent');

-- ============================================
-- 6. 验证
-- ============================================
SELECT 'mh_visual_archive' tbl, COUNT(*) cnt FROM mh_visual_archive WHERE deleted=0
UNION ALL SELECT 'mh_visual_compare', COUNT(*) FROM mh_visual_compare
UNION ALL SELECT 'mh_image_consent', COUNT(*) FROM mh_image_consent WHERE deleted=0
UNION ALL SELECT 'mh_image_withdraw', COUNT(*) FROM mh_image_withdraw;