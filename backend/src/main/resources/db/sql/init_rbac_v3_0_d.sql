-- ============================================
-- R3.0-D：自动化营销与触达
-- 2026-08-09
-- ============================================

-- ============================================
-- 1. 渠道配置（短信/微信/订阅消息/小程序/公众号）
-- ============================================
DROP TABLE IF EXISTS mkt_channel_config;
CREATE TABLE mkt_channel_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    -- 类型
    channel_type VARCHAR(32) NOT NULL COMMENT 'SMS短信/MINI_PROGRAM订阅消息/MP公众号/WECHAT_WORK企业微信/IN_APP站内信/EMAIL邮件/PUSH推送',
    channel_name VARCHAR(64) NOT NULL,
    -- 提供商
    provider VARCHAR(64) COMMENT '阿里云/腾讯云/华为云/微信',
    -- 配置（JSON）
    config_json TEXT COMMENT '凭证/密钥等',
    -- 限流
    rate_limit_per_min INT DEFAULT 60 COMMENT '每分钟限流',
    rate_limit_per_day INT DEFAULT 5000 COMMENT '每日限额',
    -- 签名/模板前缀
    signature VARCHAR(64) COMMENT '短信签名',
    template_prefix VARCHAR(64),
    -- 健康状态
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用',
    last_health_at DATETIME,
    last_health_msg VARCHAR(255),
    -- 重试
    retry_max INT DEFAULT 3,
    retry_interval_sec INT DEFAULT 60,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_type_brand (tenant_id, channel_type, brand_id, deleted),
    INDEX idx_tenant (tenant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道配置';

-- ============================================
-- 2. 渠道模板（短信模板/微信模板/订阅消息模板）
-- ============================================
DROP TABLE IF EXISTS mkt_channel_template;
CREATE TABLE mkt_channel_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    -- 关联
    channel_type VARCHAR(32) NOT NULL,
    channel_config_id BIGINT,
    -- 模板
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    -- 外部模板 ID
    external_template_id VARCHAR(128),
    -- 场景
    scene VARCHAR(64) NOT NULL COMMENT 'ORDER_PAYED/CARD_EXPIRE/BIRTHDAY/EVALUATION/MARKETING/COUPON...',
    -- 内容
    subject VARCHAR(255) COMMENT '标题/主题',
    content VARCHAR(2000) NOT NULL,
    -- 变量：${name}, ${amount}, ${date}...
    variables VARCHAR(500) COMMENT '可用变量 CSV',
    -- 跳转链接
    link_url VARCHAR(500),
    link_type VARCHAR(32) DEFAULT 'MINI' COMMENT 'MINI小程序/MINI_PAGE/H5/IMAGE_NOPAGE',
    -- 状态
    audit_status TINYINT DEFAULT 1 COMMENT '1待审核 2已审核 3驳回',
    audit_remark VARCHAR(255),
    enabled TINYINT DEFAULT 1,
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, template_code),
    INDEX idx_scene (channel_type, scene, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道模板';

-- ============================================
-- 3. 营销触达任务
-- ============================================
DROP TABLE IF EXISTS marketing_touch_task;
CREATE TABLE marketing_touch_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    store_id BIGINT,
    task_no VARCHAR(64) NOT NULL,
    -- 来源
    source_type VARCHAR(32) COMMENT 'JOURNEY旅程/MANUAL手动/SCHEDULE定时/TRIGGER触发器',
    journey_id BIGINT COMMENT '关联旅程',
    journey_node_id BIGINT COMMENT '旅程节点',
    -- 内容
    campaign_name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    template_id BIGINT,
    template_snapshot VARCHAR(2000) COMMENT '模板快照',
    -- 触发条件
    trigger_event VARCHAR(64) COMMENT 'ORDER_PAYED/ORDER_COMPLETED/CARD_DELIVERED/BIRTHDAY/EXPIRE_30D/MEMBER_REGISTER/INACTIVE_30D',
    trigger_offset_minutes INT DEFAULT 0 COMMENT '相对触发偏移（负数=前）',
    -- 目标人群
    audience_filter TEXT COMMENT '人群过滤条件（JSON）',
    -- 调度
    scheduled_at DATETIME NOT NULL,
    started_at DATETIME,
    finished_at DATETIME,
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1待执行 2执行中 3已完成 4已暂停 5已失败 6已取消',
    -- 统计
    target_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    read_count INT DEFAULT 0,
    convert_count INT DEFAULT 0,
    -- 限制
    max_reach_per_day INT COMMENT '每日最大触达',
    -- 备注
    remark VARCHAR(500),
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_task (tenant_id, task_no),
    INDEX idx_status (status, scheduled_at),
    INDEX idx_journey (journey_id),
    INDEX idx_event (trigger_event, scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销触达任务';

-- ============================================
-- 4. 触达执行明细（每个会员一条）
-- ============================================
DROP TABLE IF EXISTS marketing_touch_log;
CREATE TABLE marketing_touch_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    task_id BIGINT NOT NULL,
    -- 目标
    member_id BIGINT,
    member_name VARCHAR(64),
    member_phone VARCHAR(20),
    -- 内容快照
    channel_type VARCHAR(32),
    content VARCHAR(2000),
    -- 执行结果
    status TINYINT DEFAULT 1 COMMENT '1待发送 2已发送 3失败 4已读 5已转化',
    error_msg VARCHAR(500),
    -- 时间
    scheduled_at DATETIME,
    sent_at DATETIME,
    read_at DATETIME,
    convert_at DATETIME,
    -- 关联订单
    related_order_id BIGINT,
    -- 重试
    retry_count INT DEFAULT 0,
    -- 外部消息ID
    external_msg_id VARCHAR(128),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_task (task_id, status),
    INDEX idx_member (member_id, status),
    INDEX idx_status_time (status, scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='触达执行明细';

-- ============================================
-- 5. 营销旅程（画布）
-- ============================================
DROP TABLE IF EXISTS mkt_journey;
CREATE TABLE mkt_journey (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    brand_id BIGINT,
    journey_code VARCHAR(64) NOT NULL,
    journey_name VARCHAR(128) NOT NULL,
    -- 类型
    journey_type VARCHAR(32) DEFAULT 'MARKETING' COMMENT 'MARKETING营销/CARE关怀/RETENTION挽回/ONBOARD新客',
    description VARCHAR(500),
    -- 节点（JSON 数组）
    nodes_json TEXT COMMENT '旅程节点（JSON 数组）',
    -- 边/连接
    edges_json TEXT COMMENT '节点连接（JSON 数组）',
    -- 触发
    trigger_event VARCHAR(64) COMMENT 'ORDER_PAYED/CARD_DELIVERED/...',
    trigger_filter TEXT COMMENT '过滤条件',
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1草稿 2已发布 3已暂停 4已结束',
    published_at DATETIME,
    -- 统计
    enter_count INT DEFAULT 0,
    complete_count INT DEFAULT 0,
    exit_count INT DEFAULT 0,
    -- 操作
    operator_id BIGINT,
    operator_name VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, journey_code),
    INDEX idx_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销旅程';

-- ============================================
-- 6. 旅程执行实例
-- ============================================
DROP TABLE IF EXISTS mkt_journey_instance;
CREATE TABLE mkt_journey_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    journey_id BIGINT NOT NULL,
    -- 会员
    member_id BIGINT NOT NULL,
    member_name VARCHAR(64),
    -- 进度
    current_node_id VARCHAR(64),
    -- 状态
    status TINYINT DEFAULT 1 COMMENT '1进行中 2已完成 3已退出',
    entered_at DATETIME,
    completed_at DATETIME,
    -- 上下文（用于条件判断）
    context_json TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_journey (journey_id, status),
    INDEX idx_member (member_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='旅程执行实例';

-- ============================================
-- 7. 触达效果转化（关联订单/核销/优惠券使用）
-- ============================================
DROP TABLE IF EXISTS marketing_touch_conversion;
CREATE TABLE marketing_touch_conversion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    touch_log_id BIGINT NOT NULL,
    task_id BIGINT,
    -- 转化动作
    conversion_type VARCHAR(32) NOT NULL COMMENT 'ORDER订单/REDEMPTION核销/COUPON优惠券/MEMBER_REGISTER注册',
    -- 关联
    related_id BIGINT NOT NULL,
    related_no VARCHAR(64),
    -- 金额
    amount DECIMAL(12,2) DEFAULT 0,
    -- 时间
    converted_at DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log (touch_log_id),
    INDEX idx_task (task_id, conversion_type),
    INDEX idx_type (conversion_type, amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='触达转化明细';

-- ============================================
-- 8. 演示数据
-- ============================================
INSERT IGNORE INTO mkt_channel_config
(tenant_id, brand_id, channel_type, channel_name, provider, signature, rate_limit_per_min, rate_limit_per_day, status, operator_name)
VALUES
(1, 1, 'SMS', '阿里云短信', '阿里云', '美业科技', 30, 2000, 1, 'system'),
(1, 1, 'MINI_PROGRAM', '小程序订阅消息', '微信', NULL, 100, 5000, 1, 'system'),
(1, 1, 'MP', '公众号模板消息', '微信', '美业科技', 50, 3000, 1, 'system'),
(1, NULL, 'IN_APP', '站内信', NULL, NULL, 1000, 99999, 1, 'system');

INSERT IGNORE INTO mkt_channel_template
(tenant_id, brand_id, channel_type, template_code, template_name, scene, subject, content, variables, external_template_id, audit_status, enabled, operator_name)
VALUES
(1, 1, 'SMS', 'TPL-SMS-BIRTHDAY', '生日祝福短信', 'BIRTHDAY',
 '生日祝福', '尊敬的${name}，${date}是您的生日，美业科技祝您生日快乐，特赠送${amount}元优惠券！', 'name,date,amount', 'aliyun-birthday-001', 2, 1, 'system'),
(1, 1, 'SMS', 'TPL-SMS-EXPIRE', '套餐到期提醒', 'EXPIRE',
 '到期提醒', '尊敬的${name}，您的套餐将于${date}到期，请及时预约使用。', 'name,date', 'aliyun-expire-001', 2, 1, 'system'),
(1, 1, 'MINI_PROGRAM', 'TPL-MP-EVAL', '评价邀请', 'EVALUATION',
 '评价邀请', '请对本次服务进行评价', 'name,orderNo', 'mp-eval-001', 2, 1, 'system'),
(1, 1, 'SMS', 'TPL-SMS-MARKETING', '活动营销', 'MARKETING',
 '活动邀请', '尊敬的${name}，${date}美业科技举办${campaign}活动，${discount}元体验等您来！', 'name,date,campaign,discount', 'aliyun-mkt-001', 2, 1, 'system');

INSERT IGNORE INTO mkt_journey
(tenant_id, brand_id, journey_code, journey_name, journey_type, description,
 nodes_json, edges_json, trigger_event, status, operator_name)
VALUES
(1, 1, 'JOURNEY-001', '新客 7 日关怀旅程', 'ONBOARD',
 '新客注册后 7 日内自动触达',
 '[{"id":"n1","type":"START","name":"新客进入"},{"id":"n2","type":"WAIT","hours":24,"name":"等待 1 天"},{"id":"n3","type":"SMS","template":"TPL-SMS-MARKETING","name":"发送欢迎短信"},{"id":"n4","type":"WAIT","hours":72,"name":"等待 3 天"},{"id":"n5","type":"SMS","template":"TPL-SMS-MARKETING","name":"发送优惠券"},{"id":"n6","type":"END","name":"结束"}]',
 '[{"from":"n1","to":"n2"},{"from":"n2","to":"n3"},{"from":"n3","to":"n4"},{"from":"n4","to":"n5"},{"from":"n5","to":"n6"}]',
 'MEMBER_REGISTER', 2, 'system');

-- ============================================
-- 9. 权限码
-- ============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:channel', '渠道配置', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:campaign') t LIMIT 1), 20;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:template', '消息模板', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:campaign') t LIMIT 1), 21;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:touch-task', '触达任务', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:campaign') t LIMIT 1), 22;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:journey', '营销旅程', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:campaign') t LIMIT 1), 23;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:conversion', '转化统计', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:campaign') t LIMIT 1), 24;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN','MARKETING')
  AND p.permission_code IN ('mkt:channel','mkt:template','mkt:touch-task','mkt:journey','mkt:conversion');

-- ============================================
-- 10. 验证
-- ============================================
SELECT 'mkt_channel_config' tbl, COUNT(*) cnt FROM mkt_channel_config WHERE deleted=0
UNION ALL SELECT 'mkt_channel_template', COUNT(*) FROM mkt_channel_template WHERE deleted=0
UNION ALL SELECT 'marketing_touch_task', COUNT(*) FROM marketing_touch_task WHERE deleted=0
UNION ALL SELECT 'marketing_touch_log', COUNT(*) FROM marketing_touch_log WHERE deleted=0
UNION ALL SELECT 'mkt_journey', COUNT(*) FROM mkt_journey WHERE deleted=0
UNION ALL SELECT 'mkt_journey_instance', COUNT(*) FROM mkt_journey_instance WHERE deleted=0
UNION ALL SELECT 'marketing_touch_conversion', COUNT(*) FROM marketing_touch_conversion;
