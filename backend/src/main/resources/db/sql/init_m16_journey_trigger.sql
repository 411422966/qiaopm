-- ================================================================
-- M16 营销旅程事件触发器全链
-- 建表时间：2026-09-04
-- ================================================================

-- 1. 触发器规则
DROP TABLE IF EXISTS mkt_journey_trigger;
CREATE TABLE mkt_journey_trigger (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL DEFAULT 1,
    trigger_code    VARCHAR(64) NOT NULL COMMENT '触发器编码',
    trigger_name    VARCHAR(128) NOT NULL COMMENT '触发器名称',
    event_type      VARCHAR(64) NOT NULL COMMENT '事件类型：MEMBER_REGISTER/ORDER_PLACED/RECHARGE/BOOKING/CONSUME/BIRTHDAY',
    filter_conditions JSON COMMENT '过滤条件 JSON',
    journey_id      BIGINT NOT NULL COMMENT '关联旅程 ID',
    delay_minutes   INT DEFAULT 0 COMMENT '延时触发（分钟），0=立即触发',
    delay_window_hours INT DEFAULT 0 COMMENT '延时执行窗口（小时），用于批量归集',
    daily_limit_per_member INT DEFAULT 1 COMMENT '每会员每日最多触发 1 次（防骚扰）',
    batch_limit     INT DEFAULT 1000 COMMENT '每个事件最多触发会员数',
    status          INT DEFAULT 1 COMMENT '1草稿 2启用 3暂停',
    priority        INT DEFAULT 100 COMMENT '优先级，数字越小越优先',
    match_count     INT DEFAULT 0 COMMENT '匹配次数',
    enter_count     INT DEFAULT 0 COMMENT '入旅程次数',
    last_trigger_at DATETIME DEFAULT NULL,
    description     VARCHAR(500) DEFAULT NULL,
    operator_name   VARCHAR(50) DEFAULT NULL,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, trigger_code, deleted),
    INDEX idx_event_type (event_type),
    INDEX idx_status (status),
    INDEX idx_journey (journey_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销旅程事件触发器规则';

-- 2. 触发器执行日志
DROP TABLE IF EXISTS mkt_journey_trigger_log;
CREATE TABLE mkt_journey_trigger_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT NOT NULL DEFAULT 1,
    trigger_id          BIGINT NOT NULL COMMENT '触发的规则 ID',
    trigger_code        VARCHAR(64) COMMENT '触发器编码',
    event_type          VARCHAR(64) COMMENT '事件类型',
    event_id            VARCHAR(128) COMMENT '业务事件唯一 ID（幂等）',
    member_id           BIGINT COMMENT '会员 ID',
    member_name         VARCHAR(64) COMMENT '会员姓名',
    journey_id          BIGINT COMMENT '关联旅程',
    journey_instance_id BIGINT COMMENT '生成的旅程实例 ID',
    status              VARCHAR(16) NOT NULL DEFAULT 'MATCHED' COMMENT 'MATCHED/ENTERED/SKIPPED/FAILED',
    match_detail        VARCHAR(500) COMMENT '匹配详情',
    event_payload       TEXT COMMENT '事件载荷',
    latency_ms          BIGINT DEFAULT 0 COMMENT '从事件到执行延迟 ms',
    error_message       VARCHAR(500) DEFAULT NULL,
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trigger (trigger_id),
    INDEX idx_member (member_id),
    INDEX idx_event_type (event_type),
    INDEX idx_status (status),
    INDEX idx_create (create_time),
    INDEX idx_tenant_event (tenant_id, event_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='触发器执行日志';

-- 3. 权限
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('mkt:trigger:list', '触发器列表', 'mkt', 'menu', 1, 0, NOW()),
    ('mkt:trigger:create', '创建触发器', 'mkt', 'menu', 1, 0, NOW()),
    ('mkt:trigger:log', '触发器日志', 'mkt', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 4. 示例数据（每个事件类型 1 个）
INSERT INTO mkt_journey_trigger
(tenant_id, trigger_code, trigger_name, event_type, filter_conditions, journey_id, delay_minutes, daily_limit_per_member, status, priority, description, operator_name)
VALUES
    (1, 'TRIG_NEW_MEMBER_WELCOME', '新客欢迎旅程', 'MEMBER_REGISTER',
     '{"memberTag":"NEW","firstOrderOnly":false}',
     1, 0, 1, 2, 10, '新会员注册立即进入欢迎旅程，发送优惠券', 'system'),
    (1, 'TRIG_ORDER_POST_PURCHASE', '下单后推荐旅程', 'ORDER_PLACED',
     '{"minOrderAmount":100}',
     2, 60, 1, 2, 20, '下单 1 小时后推荐相关服务', 'system'),
    (1, 'TRIG_RECHARGE_RETENTION', '高额充值关怀', 'RECHARGE',
     '{"minRechargeAmount":1000}',
     3, 0, 1, 2, 30, '充值 ≥1000 立即触发高净值会员关怀', 'system'),
    (1, 'TRIG_BOOKING_REMINDER', '预约提醒旅程', 'BOOKING',
     '{"reminderBeforeMin":120}',
     4, 120, 1, 2, 40, '预约 2 小时前发送到店提醒', 'system'),
    (1, 'TRIG_LOST_CUSTOMER_RETURN', '流失挽回旅程', 'CONSUME',
     '{"daysSinceLastConsume":{"gt":30}}',
     5, 0, 1, 2, 50, '距上次消费 >30 天流失会员进入挽回旅程', 'system'),
    (1, 'TRIG_BIRTHDAY_CARE', '生日特权旅程', 'BIRTHDAY',
     '{"daysFromBirthday":0}',
     6, 0, 1, 2, 60, '生日当天进入生日特权旅程', 'system');
