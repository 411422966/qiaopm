-- ============================================
-- R3.0-C：评价回访与客诉
-- 2026-08-09
-- ============================================

-- ============================================
-- 1. 回访任务（疗程后/生日/到期）
-- ============================================
DROP TABLE IF EXISTS crm_follow_up_task;
CREATE TABLE crm_follow_up_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    store_id BIGINT NOT NULL,
    -- 任务
    task_no VARCHAR(64) NOT NULL,
    task_type VARCHAR(32) NOT NULL COMMENT 'POST_TREATMENT疗程后 / BIRTHDAY生日 / EXPIRE到期 / COMPLAINT投诉回访 / CUSTOM自定义',
    plan_id BIGINT COMMENT '关联回访计划',
    -- 关联对象
    member_id BIGINT NOT NULL,
    member_name VARCHAR(64),
    member_phone VARCHAR(20),
    -- 触发来源
    source_type VARCHAR(32) COMMENT 'ORDER订单/CARD套餐/MEMBER会员/COMPLAINT工单',
    source_id BIGINT,
    source_no VARCHAR(64),
    -- 计划
    planned_at DATETIME NOT NULL COMMENT '计划回访时间',
    sla_expire_at DATETIME COMMENT 'SLA 截止时间',
    sla_minutes INT DEFAULT 1440 COMMENT 'SLA 阈值（分钟）',
    -- 优先级
    priority TINYINT DEFAULT 1 COMMENT '1低 2中 3高',
    -- 内容
    subject VARCHAR(255) NOT NULL COMMENT '回访主题',
    content TEXT COMMENT '回访要点',
    suggested_script TEXT COMMENT '建议话术',
    -- 分配
    owner_id BIGINT COMMENT '处理人',
    owner_name VARCHAR(64),
    dept_id BIGINT,
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1待处理 2处理中 3已完成 4已关闭 5已逾期',
    started_at DATETIME,
    finished_at DATETIME,
    -- 关联结果
    result_type VARCHAR(32) COMMENT 'SATISFIED满意 / NORMAL一般 / UNSATISFIED不满意 / COMPLAINT投诉',
    result_note VARCHAR(500),
    rating INT COMMENT '评分 1-5',
    -- 触达
    touch_count INT DEFAULT 0,
    last_touch_at DATETIME,
    -- 操作
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_task (tenant_id, task_no),
    INDEX idx_member (member_id, status),
    INDEX idx_owner (owner_id, status),
    INDEX idx_status (status, planned_at),
    INDEX idx_store (store_id, status, planned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回访任务';

-- ============================================
-- 2. 回访计划模板
-- ============================================
DROP TABLE IF EXISTS crm_follow_up_plan;
CREATE TABLE crm_follow_up_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    plan_code VARCHAR(64) NOT NULL,
    plan_name VARCHAR(128) NOT NULL,
    plan_type VARCHAR(32) NOT NULL COMMENT 'POST_TREATMENT/BIRTHDAY/EXPIRE/CUSTOM',
    -- 触发条件
    trigger_offset_days INT DEFAULT 0 COMMENT '相对触发日偏移天数（+3=3天后）',
    trigger_event VARCHAR(64) COMMENT 'ORDER_COMPLETED/CARD_DELIVERED/BIRTHDAY/EXPIRE_30D',
    -- 模板
    subject_template VARCHAR(255),
    content_template TEXT,
    script_template TEXT,
    -- 优先级
    priority TINYINT DEFAULT 1,
    -- SLA
    sla_minutes INT DEFAULT 1440,
    -- 默认归属
    default_owner_id BIGINT,
    default_owner_name VARCHAR(64),
    -- 状态
    enabled TINYINT DEFAULT 1,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, plan_code),
    INDEX idx_type (plan_type, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回访计划模板';

-- ============================================
-- 3. 评价
-- ============================================
DROP TABLE IF EXISTS crm_review;
CREATE TABLE crm_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    store_id BIGINT NOT NULL,
    -- 来源
    source_type VARCHAR(32) NOT NULL COMMENT 'ORDER订单/STAFF员工/SERVICE服务/STORE门店',
    source_id BIGINT NOT NULL,
    source_no VARCHAR(64),
    -- 评价人
    member_id BIGINT,
    member_name VARCHAR(64),
    member_phone VARCHAR(20),
    -- 评价（开放题+维度评分）
    overall_rating INT NOT NULL COMMENT '综合评分 1-5',
    service_rating INT COMMENT '服务 1-5',
    skill_rating INT COMMENT '技术 1-5',
    environment_rating INT COMMENT '环境 1-5',
    price_rating INT COMMENT '性价比 1-5',
    -- 标签
    tags VARCHAR(255) COMMENT '标签 CSV',
    -- 文本
    content VARCHAR(2000),
    images VARCHAR(2000) COMMENT '图片 URL CSV',
    anonymous TINYINT DEFAULT 0 COMMENT '是否匿名',
    -- 渠道
    channel VARCHAR(32) DEFAULT 'INTERNAL' COMMENT 'INTERNAL内部/MINI_PROGRAM小程序/SMS短信/OTHER其他',
    -- 回复
    reply_content VARCHAR(2000),
    reply_at DATETIME,
    reply_by VARCHAR(64),
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1待审核 2已发布 3已隐藏 4已申诉',
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_source (source_type, source_id),
    INDEX idx_member (member_id),
    INDEX idx_store (store_id, status, create_time),
    INDEX idx_rating (overall_rating, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价';

-- ============================================
-- 4. 评价模板（按品牌）
-- ============================================
DROP TABLE IF EXISTS crm_review_template;
CREATE TABLE crm_review_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    -- 触发
    trigger_event VARCHAR(64) COMMENT 'ORDER_COMPLETED/CARD_DELIVERED',
    -- 模板（JSON：标签维度 + 引导语）
    dimensions VARCHAR(500) COMMENT '评价维度（CSV: 服务,技术,环境,价格）',
    tags_pool VARCHAR(1000) COMMENT '标签池（CSV）',
    intro_text VARCHAR(500) COMMENT '引导语',
    share_image VARCHAR(500) COMMENT '分享图',
    -- 触达
    touch_channel VARCHAR(32) DEFAULT 'MINI_PROGRAM',
    touch_delay_minutes INT DEFAULT 60,
    enabled TINYINT DEFAULT 1,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, template_code),
    INDEX idx_event (trigger_event, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价模板';

-- ============================================
-- 5. 投诉工单（状态机）
-- ============================================
DROP TABLE IF EXISTS crm_complaint_ticket;
CREATE TABLE crm_complaint_ticket (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    store_id BIGINT NOT NULL,
    ticket_no VARCHAR(64) NOT NULL,
    -- 投诉人
    member_id BIGINT,
    member_name VARCHAR(64),
    member_phone VARCHAR(20),
    -- 类型
    complaint_type VARCHAR(32) NOT NULL COMMENT 'SERVICE服务/STAFF员工/PRODUCT产品/PRICE价格/ENVIRONMENT环境/OTHER其他',
    level TINYINT DEFAULT 1 COMMENT '1一般 2较严重 3严重 4紧急',
    -- 内容
    subject VARCHAR(255) NOT NULL,
    content TEXT,
    evidence VARCHAR(2000) COMMENT '证据 URL CSV',
    -- 关联
    source_type VARCHAR(32) COMMENT 'ORDER/CARD/MEMBER/FOLLOW_UP/REVIEW',
    source_id BIGINT,
    source_no VARCHAR(64),
    -- 处理
    handler_id BIGINT COMMENT '处理人',
    handler_name VARCHAR(64),
    dept_id BIGINT,
    -- SLA
    sla_expire_at DATETIME,
    sla_minutes INT DEFAULT 1440,
    -- 状态机
    status TINYINT DEFAULT 1 COMMENT '1待分配 2已分配 3处理中 4等待客户 5已解决 6已关闭 7已升级',
    -- 解决
    solution VARCHAR(2000),
    solution_type VARCHAR(32) COMMENT 'REFUND退款/EXCHANGE更换/APOLOGY道歉/COMPENSATION赔偿/NONE无需处理',
    refund_amount DECIMAL(12,2) DEFAULT 0,
    resolved_at DATETIME,
    -- 满意度
    satisfaction_rating INT COMMENT '客户满意度 1-5',
    satisfaction_note VARCHAR(500),
    -- 升级链
    parent_id BIGINT COMMENT '上级工单',
    escalate_count INT DEFAULT 0,
    -- 操作
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_ticket (tenant_id, ticket_no),
    INDEX idx_member (member_id),
    INDEX idx_handler (handler_id, status),
    INDEX idx_status (status, sla_expire_at),
    INDEX idx_store (store_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投诉工单';

-- ============================================
-- 6. 投诉工单日志（状态机轨迹）
-- ============================================
DROP TABLE IF EXISTS crm_complaint_log;
CREATE TABLE crm_complaint_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    ticket_id BIGINT NOT NULL,
    -- 操作
    action_type VARCHAR(32) NOT NULL COMMENT 'CREATE创建/ASSIGN分配/REPLY回复/RESOLVE解决/CLOSE关闭/REOPEN重启/ESCALATE升级/REFUND退款',
    from_status TINYINT,
    to_status TINYINT,
    -- 内容
    content VARCHAR(2000),
    -- 操作人
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ticket (ticket_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投诉工单日志';

-- ============================================
-- 7. 演示数据
-- ============================================
INSERT IGNORE INTO crm_follow_up_plan
(tenant_id, brand_id, plan_code, plan_name, plan_type, trigger_offset_days, trigger_event,
 subject_template, content_template, sla_minutes, priority, enabled, operator_name)
VALUES
(1, 1, 'PLAN-PT-001', '疗程完成后 3 天回访', 'POST_TREATMENT', 3, 'ORDER_COMPLETED',
 '疗程后回访', '请回访会员是否对本次服务满意、皮肤状态是否改善。', 1440, 2, 1, 'system'),
(1, 1, 'PLAN-BD-001', '会员生日关怀', 'BIRTHDAY', 0, 'BIRTHDAY',
 '生日祝福', '发送生日祝福短信/优惠券。', 2880, 1, 1, 'system'),
(1, 1, 'PLAN-EX-001', '套餐到期前 30 天提醒', 'EXPIRE', -30, 'EXPIRE_30D',
 '套餐到期提醒', '提醒会员套餐即将到期。', 1440, 3, 1, 'system');

INSERT IGNORE INTO crm_review_template
(tenant_id, brand_id, template_code, template_name, trigger_event, dimensions,
 tags_pool, intro_text, touch_channel, touch_delay_minutes, enabled, operator_name)
VALUES
(1, 1, 'TPL-ORDER-001', '服务订单评价', 'ORDER_COMPLETED',
 '服务,技术,环境,价格', '专业,细致,热情,手法好,环境好',
 '感谢您选择美业，您的反馈是我们前进的动力！', 'MINI_PROGRAM', 60, 1, 'system');

-- ============================================
-- 8. 权限码
-- ============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'crm:follow-up', '回访任务', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 40;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'crm:follow-plan', '回访计划', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 41;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'crm:review', '客户评价', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 42;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'crm:review-tpl', '评价模板', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 43;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'crm:complaint', '投诉工单', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='crm:member') t LIMIT 1), 44;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN')
  AND p.permission_code IN ('crm:follow-up','crm:follow-plan','crm:review','crm:review-tpl','crm:complaint');

-- ============================================
-- 9. 验证
-- ============================================
SELECT 'crm_follow_up_task' tbl, COUNT(*) cnt FROM crm_follow_up_task WHERE deleted=0
UNION ALL SELECT 'crm_follow_up_plan', COUNT(*) FROM crm_follow_up_plan WHERE deleted=0
UNION ALL SELECT 'crm_review', COUNT(*) FROM crm_review WHERE deleted=0
UNION ALL SELECT 'crm_review_template', COUNT(*) FROM crm_review_template WHERE deleted=0
UNION ALL SELECT 'crm_complaint_ticket', COUNT(*) FROM crm_complaint_ticket WHERE deleted=0
UNION ALL SELECT 'crm_complaint_log', COUNT(*) FROM crm_complaint_log;
