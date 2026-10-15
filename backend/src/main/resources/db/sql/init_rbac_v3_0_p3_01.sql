-- ============================================
-- P3 · T-P01：RFM 标签模型与会员分群
-- 2026-08-09
-- ============================================

-- ============================================
-- 1. 会员标签（动态标签，如 RFM 分群）
-- ============================================
DROP TABLE IF EXISTS crm_member_tag_value;
CREATE TABLE crm_member_tag_value (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    member_id BIGINT NOT NULL,
    -- 标签
    tag_code VARCHAR(64) NOT NULL COMMENT '标签编码：RFM-CHAMPION, RFM-LOST, ...',
    tag_name VARCHAR(128) NOT NULL,
    tag_category VARCHAR(32) NOT NULL COMMENT 'RFM/LIFECYCLE/VALUE/PREFERENCE/AI',
    -- 来源
    source_type VARCHAR(32) NOT NULL COMMENT 'AUTO自动/MANUAL人工/AI智能',
    model_id BIGINT COMMENT '关联模型',
    -- 标签值
    tag_value VARCHAR(255),
    score DECIMAL(8,2) COMMENT '评分 0-100',
    -- 关联对象（如标签对应的会员ID/订单ID）
    related_type VARCHAR(32),
    related_id BIGINT,
    -- 有效期
    effective_at DATETIME,
    expire_at DATETIME,
    -- 操作
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_member_tag (tenant_id, member_id, tag_code),
    INDEX idx_category (tag_category, tag_code),
    INDEX idx_tag (tag_code, score),
    INDEX idx_score (score DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员标签值';

-- ============================================
-- 2. RFM 评分快照（每次计算留痕）
-- ============================================
DROP TABLE IF EXISTS crm_rfm_snapshot;
CREATE TABLE crm_rfm_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    member_id BIGINT NOT NULL,
    -- RFM 三维度
    recency_days INT NOT NULL COMMENT '最近一次消费距今天数',
    frequency INT NOT NULL COMMENT '消费频次（次）',
    monetary DECIMAL(12,2) NOT NULL COMMENT '消费金额（元）',
    -- 评分（1-5）
    r_score TINYINT,
    f_score TINYINT,
    m_score TINYINT,
    rfm_score VARCHAR(8) COMMENT '如 "555" / "135"',
    -- 分群
    segment VARCHAR(32) COMMENT 'CHAMPION/LOYAL/POTENTIAL/NEW/AT_RISK/LOST/...',
    segment_name VARCHAR(64),
    -- 解释
    explanation VARCHAR(500),
    -- 计算元数据
    snapshot_date DATE NOT NULL,
    calc_mode VARCHAR(16) DEFAULT 'ONLINE' COMMENT 'ONLINE实时/BATCH批量',
    calc_duration_ms INT,
    operator_name VARCHAR(64) DEFAULT 'system',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member (member_id),
    INDEX idx_segment (segment, snapshot_date),
    INDEX idx_brand (brand_id, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RFM 评分快照';

-- ============================================
-- 3. 标签模型定义
-- ============================================
DROP TABLE IF EXISTS ai_tag_model;
CREATE TABLE ai_tag_model (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    model_code VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    model_type VARCHAR(32) NOT NULL COMMENT 'RFM/RULE/ML/AI_VISION',
    -- 配置
    rule_json TEXT COMMENT '规则/模型参数',
    -- 调度
    schedule_cron VARCHAR(32),
    enabled TINYINT DEFAULT 1,
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
    description VARCHAR(500),
    -- 指标
    auc DECIMAL(5,4),
    recall DECIMAL(5,4),
    precision_score DECIMAL(5,4),
    -- 操作
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, model_code),
    INDEX idx_type (model_type, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签模型';

-- ============================================
-- 4. 人群（分群结果）
-- ============================================
DROP TABLE IF EXISTS mkt_segment;
CREATE TABLE mkt_segment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    segment_code VARCHAR(64) NOT NULL,
    segment_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    -- 过滤条件
    filter_json TEXT,
    -- 会员数
    member_count INT DEFAULT 0,
    -- 来源
    source_type VARCHAR(32) COMMENT 'RFM/RULE/AI/MANUAL',
    source_model_id BIGINT,
    -- 状态
    enabled TINYINT DEFAULT 1,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, segment_code),
    INDEX idx_source (source_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人群';

-- ============================================
-- 5. 标签字典（标准标签清单 + 可解释）
-- ============================================
DROP TABLE IF EXISTS crm_tag_dictionary;
CREATE TABLE crm_tag_dictionary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    tag_code VARCHAR(64) NOT NULL,
    tag_name VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL,
    description VARCHAR(500) COMMENT '标签解释',
    rule VARCHAR(500) COMMENT '计算规则可解释',
    color VARCHAR(16),
    icon VARCHAR(64),
    sort_order INT DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tag (tag_code),
    INDEX idx_category (category, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签字典';

-- ============================================
-- 6. 演示数据：标准 RFM 分群字典
-- ============================================
INSERT IGNORE INTO crm_tag_dictionary (tag_code, tag_name, category, description, rule, color, sort_order) VALUES
('RFM-CHAMPION', '冠军客户', 'RFM', '近期频繁高额消费，核心 VIP', 'R=4-5 且 F=4-5 且 M=4-5', '#f56c6c', 1),
('RFM-LOYAL', '忠诚客户', 'RFM', '高频消费，金额中上', 'F=4-5 且 R=3-5', '#e6a23c', 2),
('RFM-POTENTIAL', '潜力客户', 'RFM', '近期消费过，频次低，金额高', 'R=4-5 且 M=4-5 且 F<=3', '#67c23a', 3),
('RFM-NEW', '新客户', 'RFM', '注册或首次消费 30 天内', 'R<=1 且 F<=1', '#409eff', 4),
('RFM-AT_RISK', '流失风险', 'RFM', '有过消费但近期未到店', 'R>=3 且 F>=2', '#909399', 5),
('RFM-LOST', '流失客户', 'RFM', '长期未消费，挽回困难', 'R>=4 且 F<=1', '#c0c4cc', 6),
('RFM-CANT_LOSE', '重点挽留', 'RFM', '曾是高价值客户，近期流失', '历史 M>=4 且 R>=4', '#f56c6c', 7),
('RFM-HIBERNATING', '冬眠客户', 'RFM', '低频低额，需激活', 'R>=3 且 F<=2 且 M<=2', '#909399', 8);

-- ============================================
-- 7. 演示模型
-- ============================================
INSERT IGNORE INTO ai_tag_model (tenant_id, model_code, model_name, model_type, rule_json, description, enabled, status, operator_name)
VALUES
(1, NULL, 'RFM-BASE', 'RFM 基础分群', 'RULE',
 '{"thresholds":{"recency":[30,90,180,365],"frequency":[3,5,8,15],"monetary":[500,1500,5000,15000]}}',
 '基于最近消费距今天数、消费频次、消费金额的三维分群', 1, 1, 'system');

-- ============================================
-- 8. 权限码
-- ============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'crm:tag', '会员标签', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 50;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'crm:segment', '会员分群', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 51;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'crm:rfm', 'RFM 模型', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 52;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN','MARKETING')
  AND p.permission_code IN ('crm:tag','crm:segment','crm:rfm');

-- ============================================
-- 9. 验证
-- ============================================
SELECT 'crm_member_tag_value' tbl, COUNT(*) cnt FROM crm_member_tag_value WHERE deleted=0
UNION ALL SELECT 'crm_rfm_snapshot', COUNT(*) FROM crm_rfm_snapshot
UNION ALL SELECT 'ai_tag_model', COUNT(*) FROM ai_tag_model WHERE deleted=0
UNION ALL SELECT 'mkt_segment', COUNT(*) FROM mkt_segment WHERE deleted=0
UNION ALL SELECT 'crm_tag_dictionary', COUNT(*) FROM crm_tag_dictionary;