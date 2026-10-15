-- ============================================
-- R2.1-B：资源管理与候补爽约 - 数据库初始化
-- 2026-08-09
-- ============================================

-- 1. 资源字典（房间/床位/设备 三类）
DROP TABLE IF EXISTS booking_resource;
CREATE TABLE booking_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    resource_code VARCHAR(64) NOT NULL COMMENT '资源编码',
    resource_name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(16) NOT NULL COMMENT 'ROOM/BED/DEVICE',
    capacity INT DEFAULT 1 COMMENT '同时容纳人数',
    location VARCHAR(128) COMMENT '位置描述',
    equipment VARCHAR(500) COMMENT '配套设备/说明',
    default_duration INT DEFAULT 60 COMMENT '默认服务时长(分钟)',
    available_from TIME DEFAULT '09:00:00' COMMENT '可用时段起',
    available_to TIME DEFAULT '21:00:00' COMMENT '可用时段止',
    status TINYINT DEFAULT 1 COMMENT '1启用 0停用 2维护中',
    sort_order INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_code (tenant_id, resource_code),
    INDEX idx_type (resource_type),
    INDEX idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约资源';

-- 2. 资源与项目绑定（哪些项目可以用哪些资源）
DROP TABLE IF EXISTS booking_resource_link;
CREATE TABLE booking_resource_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    resource_id BIGINT NOT NULL COMMENT 'booking_resource.id',
    service_item_id BIGINT NOT NULL COMMENT 'biz_service_item.id',
    is_required TINYINT DEFAULT 1 COMMENT '是否必选资源',
    priority INT DEFAULT 0 COMMENT '推荐优先级（大者优先）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_res_svc (resource_id, service_item_id),
    INDEX idx_service (service_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源-项目绑定';

-- 3. 预约资源（一张预约可绑定多个资源）
DROP TABLE IF EXISTS booking_booking_resource;
CREATE TABLE booking_booking_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL COMMENT 'biz_booking.id',
    resource_id BIGINT NOT NULL COMMENT 'booking_resource.id',
    resource_code VARCHAR(64),
    resource_name VARCHAR(128),
    resource_type VARCHAR(16),
    quantity INT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_booking_resource (booking_id, resource_id),
    INDEX idx_resource (resource_id),
    INDEX idx_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约占用资源';

-- 4. 候补队列
DROP TABLE IF EXISTS booking_waitlist;
CREATE TABLE booking_waitlist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    waitlist_no VARCHAR(64) NOT NULL,
    member_id BIGINT NOT NULL,
    member_name VARCHAR(64),
    member_phone VARCHAR(32),
    service_item_id BIGINT COMMENT 'biz_service_item.id',
    service_item_name VARCHAR(128),
    employee_id BIGINT COMMENT '指定技师',
    employee_name VARCHAR(64),
    preferred_date DATE COMMENT '期望日期',
    preferred_time_from TIME,
    preferred_time_to TIME,
    priority INT DEFAULT 0 COMMENT '优先级（数字大者优先）',
    status TINYINT DEFAULT 1 COMMENT '1等待 2已通知 3已转预约 4已取消 5已过期',
    notify_at DATETIME COMMENT '最后通知时间',
    notify_count INT DEFAULT 0 COMMENT '通知次数',
    expired_at DATETIME COMMENT '过期时间',
    operator_id BIGINT,
    operator_name VARCHAR(64),
    remark VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_waitlist (tenant_id, waitlist_no),
    INDEX idx_member (member_id),
    INDEX idx_store_status (store_id, status, deleted),
    INDEX idx_preferred (preferred_date, preferred_time_from),
    INDEX idx_priority (priority, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候补队列';

-- 5. 候补通知日志
DROP TABLE IF EXISTS booking_waitlist_notify;
CREATE TABLE booking_waitlist_notify (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    waitlist_id BIGINT NOT NULL,
    notify_type VARCHAR(16) NOT NULL COMMENT 'SMS/APP/PUSH',
    target VARCHAR(128) COMMENT '短信/推送号码',
    content VARCHAR(500),
    response VARCHAR(128) COMMENT '会员反馈',
    response_at DATETIME,
    succeeded TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_waitlist (waitlist_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候补通知日志';

-- 6. 爽约规则（按租户/门店可配置）
DROP TABLE IF EXISTS booking_no_show_rule;
CREATE TABLE booking_no_show_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    store_id BIGINT DEFAULT 1,
    name VARCHAR(64) NOT NULL,
    no_show_count_threshold INT DEFAULT 3 COMMENT '累计爽约次数阈值',
    lock_days INT DEFAULT 30 COMMENT '锁定天数',
    credit_penalty INT DEFAULT 10 COMMENT '信用分扣除',
    fee_penalty DECIMAL(10,2) DEFAULT 0 COMMENT '扣费金额',
    description VARCHAR(255),
    enabled TINYINT DEFAULT 1,
    sort_order INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_store (store_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爽约规则';

-- 7. 客户信用分与爽约记录
DROP TABLE IF EXISTS member_credit_log;
CREATE TABLE member_credit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    member_id BIGINT NOT NULL,
    change_type VARCHAR(16) NOT NULL COMMENT 'NO_SHOW/ON_TIME/CANCEL_LATE/MANUAL',
    change_value INT NOT NULL COMMENT '正为加分，负为扣分',
    credit_after INT DEFAULT 0 COMMENT '调整后分数',
    booking_id BIGINT COMMENT '关联预约',
    order_id BIGINT COMMENT '关联订单',
    fee_penalty DECIMAL(10,2) DEFAULT 0 COMMENT '扣费',
    lock_until DATETIME DEFAULT NULL COMMENT '锁定截止',
    operator_id BIGINT,
    operator_name VARCHAR(64),
    remark VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_member (member_id),
    INDEX idx_booking (booking_id),
    INDEX idx_type (change_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员信用分日志';

-- 8. 会员扩展字段（在 crm_member 上加 credit 字段；这里独立表兼容旧库）
DROP TABLE IF EXISTS member_credit;
CREATE TABLE member_credit (
    member_id BIGINT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    credit INT DEFAULT 100 COMMENT '当前信用分',
    no_show_count INT DEFAULT 0 COMMENT '累计爽约次数',
    total_bookings INT DEFAULT 0 COMMENT '累计预约次数',
    locked_until DATETIME DEFAULT NULL COMMENT '被锁定截止时间',
    last_no_show_at DATETIME DEFAULT NULL COMMENT '最近一次爽约',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员信用汇总';

-- 9. 演示数据：5 个资源
INSERT IGNORE INTO booking_resource (tenant_id, store_id, resource_code, resource_name, resource_type, capacity, location, equipment, default_duration, available_from, available_to, status, sort_order) VALUES
    (1, 1, 'R-VIP-01', 'VIP1号房', 'ROOM', 1, '3楼东', '按摩床+浴缸+音响', 90, '10:00:00', '22:00:00', 1, 1),
    (1, 1, 'R-VIP-02', 'VIP2号房', 'ROOM', 1, '3楼西', '按摩床+浴缸', 90, '10:00:00', '22:00:00', 1, 2),
    (1, 1, 'R-STD-03', '标准3号房', 'ROOM', 1, '2楼东', '按摩床', 60, '09:00:00', '21:00:00', 1, 3),
    (1, 1, 'R-BED-01', '足疗床位1号', 'BED', 1, '1楼北', '足疗椅+小屏', 60, '10:00:00', '23:00:00', 1, 4),
    (1, 1, 'R-DEV-01', '射频仪 A', 'DEVICE', 1, '3楼 VIP1', '射频+冰敷', 90, '10:00:00', '20:00:00', 1, 5);

-- 10. 资源绑定到服务项目（已有项目编码）
INSERT IGNORE INTO booking_resource_link (tenant_id, resource_id, service_item_id, is_required, priority)
SELECT 1, r.id, i.id, 1, 10
FROM booking_resource r, biz_service_item i
WHERE r.deleted = 0 AND i.deleted = 0
  AND (
    (r.resource_code = 'R-VIP-01' AND i.service_code IN ('SI-FACE-002', 'SI-ANTI-001'))
    OR (r.resource_code = 'R-VIP-02' AND i.service_code IN ('SI-FACE-001', 'SI-FACE-002'))
    OR (r.resource_code = 'R-STD-03' AND i.service_code IN ('SI-FACE-001', 'SI-BODY-001'))
    OR (r.resource_code = 'R-BED-01' AND i.service_code IN ('SI-BODY-001'))
    OR (r.resource_code = 'R-DEV-01' AND i.service_code IN ('SI-ANTI-001'))
  );

-- 11. 默认爽约规则
INSERT IGNORE INTO booking_no_show_rule (tenant_id, store_id, name, no_show_count_threshold, lock_days, credit_penalty, fee_penalty, description, enabled, sort_order)
VALUES
    (1, 1, '标准爽约规则', 3, 30, 10, 20.00, '3 次爽约锁定 30 天，每次扣 10 分，扣 20 元', 1, 1),
    (1, 1, '严格规则', 2, 60, 20, 50.00, '2 次爽约锁定 60 天，每次扣 20 分，扣 50 元', 0, 2);

-- 12. 已有演示会员初始化信用分
INSERT IGNORE INTO member_credit (member_id, tenant_id, credit, no_show_count, total_bookings)
SELECT id, tenant_id, 100, 0, 0 FROM crm_member WHERE deleted = 0;

-- 13. RBAC 权限
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'biz:booking:resource', '预约资源管理', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') t LIMIT 1), 60;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'biz:booking:waitlist', '候补队列', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') t LIMIT 1), 61;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'biz:booking:no-show', '爽约管理', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='setting') t LIMIT 1), 62;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN')
  AND p.permission_code IN ('biz:booking:resource','biz:booking:waitlist','biz:booking:no-show');

-- 14. 验证
SELECT 'booking_resource' tbl, COUNT(*) cnt FROM booking_resource
UNION ALL SELECT 'booking_resource_link', COUNT(*) FROM booking_resource_link
UNION ALL SELECT 'booking_no_show_rule', COUNT(*) FROM booking_no_show_rule
UNION ALL SELECT 'member_credit', COUNT(*) FROM member_credit;
