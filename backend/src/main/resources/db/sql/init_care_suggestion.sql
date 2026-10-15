-- ============================================
-- 护理建议生成模块
-- P0-M02-005：护理建议生成
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 护理建议模板表
-- ============================================
DROP TABLE IF EXISTS biz_care_suggestion_template;
CREATE TABLE biz_care_suggestion_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
    template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
    template_type VARCHAR(32) DEFAULT 'skin' COMMENT '模板类型: skin/body/hair/nail/custom',
    target_tags VARCHAR(500) COMMENT '适用人群标签(逗号分隔)',
    title VARCHAR(256) NOT NULL COMMENT '建议标题',
    content TEXT NOT NULL COMMENT '建议内容(支持变量如 {member_name} {skin_type})',
    recommended_services TEXT COMMENT '推荐服务JSON: [{serviceId, serviceName, reason}]',
    recommended_products TEXT COMMENT '推荐产品JSON',
    priority INT DEFAULT 5 COMMENT '优先级: 1-10',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-停用, 1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_template_code (template_code),
    INDEX idx_template_type (template_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='护理建议模板';

-- ============================================
-- 2. 护理建议规则表
-- ============================================
DROP TABLE IF EXISTS biz_care_suggestion_rule;
CREATE TABLE biz_care_suggestion_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    rule_code VARCHAR(64) NOT NULL COMMENT '规则编码',
    condition_type VARCHAR(32) NOT NULL COMMENT '条件类型: tag/skin_type/age_range/season/service_history',
    condition_value TEXT COMMENT '条件值(JSON)',
    template_id BIGINT NOT NULL COMMENT '关联模板ID',
    suggestion_action VARCHAR(32) DEFAULT 'recommend' COMMENT '建议动作: recommend/remind/upsell/cross_sell',
    cooldown_days INT DEFAULT 7 COMMENT '冷却期(天)',
    priority INT DEFAULT 5 COMMENT '优先级',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-停用, 1-启用',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_rule_code (rule_code),
    INDEX idx_condition_type (condition_type),
    INDEX idx_template_id (template_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='护理建议规则';

-- ============================================
-- 3. 护理建议日志表
-- ============================================
DROP TABLE IF EXISTS biz_care_suggestion_log;
CREATE TABLE biz_care_suggestion_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    member_id BIGINT NOT NULL COMMENT '客户ID',
    member_name VARCHAR(64) COMMENT '客户姓名',
    rule_id BIGINT COMMENT '命中的规则ID',
    rule_name VARCHAR(128) COMMENT '规则名称',
    template_id BIGINT COMMENT '关联模板ID',
    title VARCHAR(256) COMMENT '建议标题',
    content TEXT COMMENT '建议内容',
    recommended_services TEXT COMMENT '推荐服务JSON',
    recommended_products TEXT COMMENT '推荐产品JSON',
    trigger_reason VARCHAR(255) COMMENT '触发条件描述',
    matched_tags VARCHAR(500) COMMENT '触发的客户标签',
    skin_type VARCHAR(32) COMMENT '触发的肤质',
    suggestion_type VARCHAR(16) DEFAULT 'manual' COMMENT '建议类型: auto/manual',
    status VARCHAR(16) DEFAULT 'pending' COMMENT '状态: pending/sent/viewed/accepted/rejected/expired',
    send_channel VARCHAR(16) DEFAULT 'system' COMMENT '发送渠道: sms/wechat/system',
    send_time DATETIME COMMENT '发送时间',
    view_time DATETIME COMMENT '查看时间',
    accept_time DATETIME COMMENT '采纳时间',
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(64) COMMENT '操作人姓名',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_member_id (member_id),
    INDEX idx_rule_id (rule_id),
    INDEX idx_template_id (template_id),
    INDEX idx_status (status),
    INDEX idx_suggestion_type (suggestion_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='护理建议记录';

-- ============================================
-- 4. 客户肌肤档案表
-- ============================================
DROP TABLE IF EXISTS biz_member_skin_profile;
CREATE TABLE biz_member_skin_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    member_id BIGINT NOT NULL COMMENT '客户ID',
    member_name VARCHAR(64) COMMENT '客户姓名',
    skin_type VARCHAR(32) COMMENT '肤质类型: dry/oily/combination/sensitive/normal',
    skin_concerns TEXT COMMENT '肌肤问题JSON',
    sensitivity_score INT DEFAULT 0 COMMENT '敏感度评分 1-10',
    moisture_score INT DEFAULT 0 COMMENT '水分含量',
    oil_score INT DEFAULT 0 COMMENT '油脂含量',
    pigmentation_score INT DEFAULT 0 COMMENT '色素沉着评分',
    wrinkle_score INT DEFAULT 0 COMMENT '皱纹评分',
    pore_score INT DEFAULT 0 COMMENT '毛孔评分',
    last_check_time DATETIME COMMENT '最后检测时间',
    check_store_id BIGINT COMMENT '检测门店ID',
    check_device VARCHAR(64) COMMENT '检测设备',
    check_images TEXT COMMENT '检测图片JSON',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_member_id (member_id),
    INDEX idx_skin_type (skin_type),
    INDEX idx_last_check_time (last_check_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户肌肤档案';

-- ============================================
-- 5. RBAC 权限
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('crm:care:suggestion', '护理建议生成', 'crm', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 6. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/crm/care' AND deleted = 0 LIMIT 1),
    'CrmCareSuggestion', '/crm/care/suggestion', '护理建议', 'MagicStick', 4, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/crm/care/suggestion' AND deleted = 0
);

-- ============================================
-- 7. 初始模板示例
-- ============================================
INSERT INTO biz_care_suggestion_template (template_name, template_code, template_type, title, content, priority, status, deleted, create_time) VALUES
('敏感肌春季护理', 'skin_sensitive_spring', 'skin',
 '春季敏感肌专属护理方案',
 '亲爱的{member_name}，春季气温变化大，敏感肌需要特别呵护。建议您选择温和无刺激的修护系列，配合深层补水面膜。',
 5, 1, 0, NOW()),
('夏季油性肌护理', 'skin_oily_summer', 'skin',
 '夏季油性肌控油方案',
 '亲爱的{member_name}，夏季油脂分泌旺盛，您的{skin_type}肤质需要清爽控油。推荐使用控油爽肤水和清爽乳液。',
 6, 1, 0, NOW()),
('熟龄肌抗衰建议', 'skin_aging', 'skin',
 '熟龄肌抗衰紧致方案',
 '亲爱的{member_name}，肌肤开始进入熟龄期，建议使用含精华液和胶原蛋白的抗衰产品，配合定期的紧致护理。',
 7, 1, 0, NOW()),
('基础补水建议', 'skin_basic_hydration', 'skin',
 '肌肤补水基础方案',
 '亲爱的{member_name}，保持肌肤水润是美丽的基础。建议每周1-2次补水面膜，日常做好基础保湿。',
 3, 1, 0, NOW());

-- ============================================
-- 8. 初始规则示例
-- ============================================
INSERT INTO biz_care_suggestion_rule (rule_name, rule_code, condition_type, condition_value, template_id, suggestion_action, cooldown_days, priority, status, deleted, create_time) VALUES
('敏感肌春季护理规则', 'rule_sensitive_spring', 'skin_type', '["sensitive"]',
 (SELECT id FROM biz_care_suggestion_template WHERE template_code = 'skin_sensitive_spring' LIMIT 1),
 'recommend', 7, 8, 1, 0, NOW()),
('夏季油性肌推荐', 'rule_oily_summer', 'skin_type', '["oily"]',
 (SELECT id FROM biz_care_suggestion_template WHERE template_code = 'skin_oily_summer' LIMIT 1),
 'recommend', 14, 7, 1, 0, NOW()),
('熟龄肌抗衰提醒', 'rule_aging_recommend', 'age_range', '30-50',
 (SELECT id FROM biz_care_suggestion_template WHERE template_code = 'skin_aging' LIMIT 1),
 'upsell', 30, 6, 1, 0, NOW()),
('基础补水常驻', 'rule_basic_hydration', 'tag', '["new_customer"]',
 (SELECT id FROM biz_care_suggestion_template WHERE template_code = 'skin_basic_hydration' LIMIT 1),
 'remind', 7, 5, 1, 0, NOW());