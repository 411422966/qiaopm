-- ============================================
-- 肤质/发质/甲面检测
-- P0-M02-008：肤质/发质/甲面记录
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 肤质检测
-- ============================================
DROP TABLE IF EXISTS biz_skin_detection;
CREATE TABLE biz_skin_detection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    member_id BIGINT NOT NULL COMMENT '客户ID',
    detection_no VARCHAR(64) NOT NULL COMMENT '检测编号',
    skin_type VARCHAR(32) DEFAULT 'normal' COMMENT '肤质类型',
    overall_score DECIMAL(5,2) DEFAULT 0 COMMENT '综合得分',
    moisture_score DECIMAL(5,2) DEFAULT 0 COMMENT '水分',
    oil_score DECIMAL(5,2) DEFAULT 0 COMMENT '油脂',
    elasticity_score DECIMAL(5,2) DEFAULT 0 COMMENT '弹性',
    pore_score DECIMAL(5,2) DEFAULT 0 COMMENT '毛孔',
    pigmentation_score DECIMAL(5,2) DEFAULT 0 COMMENT '色素',
    wrinkle_score DECIMAL(5,2) DEFAULT 0 COMMENT '皱纹',
    sensitivity_score DECIMAL(5,2) DEFAULT 0 COMMENT '敏感度',
    acne_level INT DEFAULT 0 COMMENT '痘痘等级',
    dark_circle_level INT DEFAULT 0 COMMENT '黑眼圈等级',
    spot_count INT DEFAULT 0 COMMENT '色斑数量',
    fine_line_count INT DEFAULT 0 COMMENT '细纹数量',
    main_concerns VARCHAR(500) COMMENT '主诉问题JSON',
    diagnosis_advice TEXT COMMENT '诊断建议',
    detection_device VARCHAR(32) DEFAULT 'visia' COMMENT '检测设备',
    device_serial VARCHAR(64) COMMENT '设备序列号',
    detection_images TEXT COMMENT '检测图片JSON',
    check_store_id BIGINT COMMENT '检测门店ID',
    check_employee_id BIGINT COMMENT '检测技师ID',
    check_employee_name VARCHAR(64) COMMENT '检测技师姓名',
    detection_date DATE NOT NULL COMMENT '检测日期',
    next_check_date DATE COMMENT '下次复检日期',
    status VARCHAR(16) DEFAULT 'pending' COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_member_id (member_id),
    INDEX idx_store_id (store_id),
    INDEX idx_skin_type (skin_type),
    INDEX idx_detection_date (detection_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='肤质检测记录';

-- ============================================
-- 2. 发质检测
-- ============================================
DROP TABLE IF EXISTS biz_hair_detection;
CREATE TABLE biz_hair_detection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    member_id BIGINT NOT NULL COMMENT '客户ID',
    detection_no VARCHAR(64) NOT NULL COMMENT '检测编号',
    hair_type VARCHAR(32) DEFAULT 'normal' COMMENT '发质类型',
    overall_score DECIMAL(5,2) DEFAULT 0 COMMENT '综合得分',
    moisture_score DECIMAL(5,2) DEFAULT 0 COMMENT '水分',
    oil_score DECIMAL(5,2) DEFAULT 0 COMMENT '油脂',
    elasticity_score DECIMAL(5,2) DEFAULT 0 COMMENT '弹性',
    diameter VARCHAR(16) COMMENT '直径',
    density VARCHAR(16) COMMENT '密度',
    damage_score DECIMAL(5,2) DEFAULT 0 COMMENT '损伤',
    breakage_risk DECIMAL(5,2) DEFAULT 0 COMMENT '断裂风险',
    dandruff_level INT DEFAULT 0 COMMENT '头屑等级',
    scalp_sensitivity DECIMAL(5,2) DEFAULT 0 COMMENT '头皮敏感度',
    hair_loss_risk DECIMAL(5,2) DEFAULT 0 COMMENT '脱发风险',
    split_end_score DECIMAL(5,2) DEFAULT 0 COMMENT '分叉',
    dye_count INT DEFAULT 0 COMMENT '染色次数',
    perm_count INT DEFAULT 0 COMMENT '烫发次数',
    main_concerns VARCHAR(500) COMMENT '主诉问题JSON',
    diagnosis_advice TEXT COMMENT '诊断建议',
    detection_device VARCHAR(32) DEFAULT 'microscope' COMMENT '检测设备',
    detection_images TEXT COMMENT '检测图片JSON',
    check_store_id BIGINT COMMENT '检测门店ID',
    check_employee_id BIGINT COMMENT '检测技师ID',
    check_employee_name VARCHAR(64) COMMENT '检测技师姓名',
    detection_date DATE NOT NULL COMMENT '检测日期',
    next_check_date DATE COMMENT '下次复检日期',
    status VARCHAR(16) DEFAULT 'pending' COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_member_id (member_id),
    INDEX idx_store_id (store_id),
    INDEX idx_hair_type (hair_type),
    INDEX idx_detection_date (detection_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发质检测记录';

-- ============================================
-- 3. 甲面检测
-- ============================================
DROP TABLE IF EXISTS biz_nail_detection;
CREATE TABLE biz_nail_detection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    member_id BIGINT NOT NULL COMMENT '客户ID',
    detection_no VARCHAR(64) NOT NULL COMMENT '检测编号',
    nail_type VARCHAR(32) DEFAULT 'strong' COMMENT '指甲类型',
    overall_score DECIMAL(5,2) DEFAULT 0 COMMENT '综合得分',
    hardness_score DECIMAL(5,2) DEFAULT 0 COMMENT '硬度',
    gloss_score DECIMAL(5,2) DEFAULT 0 COMMENT '光泽',
    moisture_score DECIMAL(5,2) DEFAULT 0 COMMENT '水分',
    periungual_score DECIMAL(5,2) DEFAULT 0 COMMENT '甲周皮肤',
    surface_smoothness DECIMAL(5,2) DEFAULT 0 COMMENT '平整度',
    toughness_score DECIMAL(5,2) DEFAULT 0 COMMENT '韧性',
    paronychia_risk DECIMAL(5,2) DEFAULT 0 COMMENT '甲沟炎风险',
    fungal_risk DECIMAL(5,2) DEFAULT 0 COMMENT '真菌风险',
    vertical_cracks INT DEFAULT 0 COMMENT '纵裂数量',
    horizontal_ridges INT DEFAULT 0 COMMENT '横纹数量',
    hangnail_count INT DEFAULT 0 COMMENT '倒刺数量',
    break_history INT DEFAULT 0 COMMENT '断裂历史',
    frequent_manicure INT DEFAULT 0 COMMENT '频繁美甲',
    gel_manicure_count INT DEFAULT 0 COMMENT '光疗甲次数',
    main_concerns VARCHAR(500) COMMENT '主诉问题JSON',
    diagnosis_advice TEXT COMMENT '诊断建议',
    detection_device VARCHAR(32) DEFAULT 'manual' COMMENT '检测设备',
    detection_images TEXT COMMENT '检测图片JSON',
    check_store_id BIGINT COMMENT '检测门店ID',
    check_employee_id BIGINT COMMENT '检测技师ID',
    check_employee_name VARCHAR(64) COMMENT '检测技师姓名',
    detection_date DATE NOT NULL COMMENT '检测日期',
    next_check_date DATE COMMENT '下次复检日期',
    status VARCHAR(16) DEFAULT 'pending' COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_member_id (member_id),
    INDEX idx_store_id (store_id),
    INDEX idx_nail_type (nail_type),
    INDEX idx_detection_date (detection_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='甲面检测记录';

-- ============================================
-- 4. 检测报告
-- ============================================
DROP TABLE IF EXISTS biz_detection_report;
CREATE TABLE biz_detection_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    member_id BIGINT NOT NULL COMMENT '客户ID',
    report_no VARCHAR(64) NOT NULL COMMENT '报告编号',
    report_type VARCHAR(32) DEFAULT 'comprehensive' COMMENT '报告类型',
    skin_detection_id BIGINT COMMENT '肤质检测ID',
    hair_detection_id BIGINT COMMENT '发质检测ID',
    nail_detection_id BIGINT COMMENT '甲面检测ID',
    overall_score DECIMAL(5,2) DEFAULT 0 COMMENT '综合评分',
    overall_assessment TEXT COMMENT '综合评估',
    priority_concerns VARCHAR(2000) COMMENT '优先级问题',
    recommended_services VARCHAR(2000) COMMENT '推荐项目',
    recommended_products VARCHAR(2000) COMMENT '推荐产品',
    home_care_advice TEXT COMMENT '居家护理建议',
    treatment_cycle INT DEFAULT 0 COMMENT '治疗周期(天)',
    expected_outcome VARCHAR(500) COMMENT '预期效果',
    report_pdf_url VARCHAR(500) COMMENT 'PDF路径',
    report_images VARCHAR(2000) COMMENT '报告图片JSON',
    customer_viewed TINYINT DEFAULT 0 COMMENT '客户是否查看',
    customer_feedback VARCHAR(500) COMMENT '客户反馈',
    check_employee_id BIGINT COMMENT '检测技师ID',
    check_employee_name VARCHAR(64) COMMENT '检测技师姓名',
    generate_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    status VARCHAR(16) DEFAULT 'draft' COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_member_id (member_id),
    INDEX idx_store_id (store_id),
    INDEX idx_report_type (report_type),
    INDEX idx_status (status),
    INDEX idx_generate_time (generate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检测报告';

-- ============================================
-- 5. 检测方案
-- ============================================
DROP TABLE IF EXISTS biz_detection_solution;
CREATE TABLE biz_detection_solution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    solution_name VARCHAR(128) NOT NULL COMMENT '方案名称',
    solution_code VARCHAR(64) COMMENT '方案编码',
    apply_type VARCHAR(32) DEFAULT 'skin' COMMENT '适用类型',
    trigger_conditions VARCHAR(500) COMMENT '触发条件JSON',
    score_threshold INT DEFAULT 80 COMMENT '分数阈值',
    priority INT DEFAULT 5 COMMENT '优先级',
    recommended_services VARCHAR(2000) COMMENT '推荐项目JSON',
    recommended_products VARCHAR(2000) COMMENT '推荐产品JSON',
    home_care_advice TEXT COMMENT '居家护理建议',
    treatment_cycle INT DEFAULT 0 COMMENT '治疗周期(天)',
    expected_outcome VARCHAR(500) COMMENT '预期效果',
    status TINYINT DEFAULT 1 COMMENT '状态',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_apply_type (apply_type),
    INDEX idx_status (status),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检测方案';

-- ============================================
-- 6. RBAC 权限
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('crm:detection', '肤质检测管理', 'crm', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 7. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/crm' AND deleted = 0 LIMIT 1),
    'CrmDetection', '/crm/detection', '肤质档案', 'MagicStick', 5, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/crm/detection' AND deleted = 0
);

-- ============================================
-- 8. 默认方案示例
-- ============================================
INSERT INTO biz_detection_solution (solution_name, solution_code, apply_type, score_threshold, priority, treatment_cycle, status, deleted, create_time)
VALUES
('敏感肌修复方案', 'skin_sensitive_repair', 'skin', 70, 8, 30, 1, 0, NOW()),
('油性肌控油方案', 'skin_oily_control', 'skin', 70, 7, 28, 1, 0, NOW()),
('干性肌深润方案', 'skin_dry_deep_hydration', 'skin', 70, 7, 35, 1, 0, NOW()),
('脱发紧急护理', 'hair_loss_emergency', 'hair', 60, 9, 60, 1, 0, NOW()),
('重度受损修护', 'hair_damage_repair', 'hair', 50, 8, 45, 1, 0, NOW()),
('指甲基础强化', 'nail_basic_strength', 'nail', 70, 6, 30, 1, 0, NOW()),
('甲面深层护理', 'nail_deep_care', 'nail', 60, 7, 30, 1, 0, NOW());