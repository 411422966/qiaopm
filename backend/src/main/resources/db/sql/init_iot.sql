-- ============================================
-- IoT设备联动
-- P2-001：IoT设备联动与数据回写
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. IoT 设备档案
-- ============================================
DROP TABLE IF EXISTS biz_iot_device;
CREATE TABLE biz_iot_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    device_code VARCHAR(64) NOT NULL COMMENT '设备编号',
    device_name VARCHAR(128) COMMENT '设备名称',
    device_type VARCHAR(32) DEFAULT 'skin_analyzer' COMMENT '设备类型',
    device_model VARCHAR(64) COMMENT '设备型号',
    brand VARCHAR(64) COMMENT '品牌',
    firmware_version VARCHAR(32) COMMENT '固件版本',
    software_version VARCHAR(32) COMMENT '软件版本',
    mac_address VARCHAR(64) COMMENT 'MAC地址',
    serial_number VARCHAR(128) COMMENT '序列号',
    protocol VARCHAR(16) DEFAULT 'mqtt' COMMENT '通信协议',
    ip_address VARCHAR(64) COMMENT 'IP地址',
    mqtt_topic VARCHAR(256) COMMENT 'MQTT Topic',
    room_id BIGINT COMMENT '关联房间ID',
    room_name VARCHAR(128) COMMENT '房间名称',
    location VARCHAR(256) COMMENT '安装位置',
    capabilities TEXT COMMENT '设备能力JSON',
    status VARCHAR(16) DEFAULT 'offline' COMMENT '状态',
    last_online_time DATETIME COMMENT '最后在线时间',
    last_data_time DATETIME COMMENT '最后上报时间',
    activated TINYINT DEFAULT 0 COMMENT '是否激活',
    activated_time DATETIME COMMENT '激活时间',
    purchase_date DATETIME COMMENT '采购日期',
    warranty_expires DATETIME COMMENT '保修截止',
    total_reports BIGINT DEFAULT 0 COMMENT '总上报次数',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_code (device_code),
    INDEX idx_device_type (device_type),
    INDEX idx_status (status),
    INDEX idx_room_id (room_id),
    INDEX idx_protocol (protocol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT设备档案';

-- ============================================
-- 2. IoT 设备数据采集记录
-- ============================================
DROP TABLE IF EXISTS biz_iot_device_data;
CREATE TABLE biz_iot_device_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    device_id BIGINT NOT NULL COMMENT '设备ID',
    device_code VARCHAR(64) COMMENT '设备编号',
    device_type VARCHAR(32) COMMENT '设备类型',
    member_id BIGINT COMMENT '客户ID',
    member_name VARCHAR(128) COMMENT '客户姓名',
    order_id BIGINT COMMENT '关联订单ID',
    data_type VARCHAR(32) DEFAULT 'detection' COMMENT '数据类型',
    metrics TEXT COMMENT '指标JSON',
    primary_metric DECIMAL(15,4) COMMENT '主指标',
    secondary_metric DECIMAL(15,4) COMMENT '辅助指标',
    image_url VARCHAR(500) COMMENT '检测图片',
    report_url VARCHAR(500) COMMENT '检测报告',
    source VARCHAR(32) DEFAULT 'device' COMMENT '数据来源',
    collect_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间',
    device_report_time DATETIME COMMENT '设备上报时间',
    valid TINYINT DEFAULT 1 COMMENT '是否有效',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_id (device_id),
    INDEX idx_member_id (member_id),
    INDEX idx_data_type (data_type),
    INDEX idx_collect_time (collect_time),
    INDEX idx_device_collect (device_id, collect_time),
    INDEX idx_member_type_time (member_id, data_type, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT设备数据采集记录';

-- ============================================
-- 3. IoT 联动触发规则
-- ============================================
DROP TABLE IF EXISTS biz_iot_trigger_rule;
CREATE TABLE biz_iot_trigger_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    device_type VARCHAR(32) COMMENT '设备类型',
    data_type VARCHAR(32) COMMENT '数据类型',
    trigger_condition TEXT COMMENT '触发条件JSON',
    trigger_action TEXT COMMENT '触发动作JSON',
    action_type VARCHAR(32) DEFAULT 'send_coupon' COMMENT '动作类型',
    priority INT DEFAULT 5 COMMENT '优先级',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    trigger_count INT DEFAULT 0 COMMENT '触发次数',
    last_trigger_time DATETIME COMMENT '最后触发时间',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_device_type (device_type),
    INDEX idx_data_type (data_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT联动触发规则';

-- ============================================
-- 4. IoT 联动触发日志
-- ============================================
DROP TABLE IF EXISTS biz_iot_trigger_log;
CREATE TABLE biz_iot_trigger_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    rule_id BIGINT COMMENT '规则ID',
    rule_name VARCHAR(128) COMMENT '规则名称',
    device_id BIGINT COMMENT '设备ID',
    data_id BIGINT COMMENT '数据ID',
    member_id BIGINT COMMENT '客户ID',
    trigger_condition TEXT COMMENT '触发条件',
    actual_metric TEXT COMMENT '实际指标',
    status VARCHAR(16) DEFAULT 'pending' COMMENT '执行状态',
    result VARCHAR(500) COMMENT '执行结果',
    error_message VARCHAR(1000) COMMENT '错误信息',
    execution_ms BIGINT DEFAULT 0 COMMENT '执行耗时',
    trigger_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rule_id (rule_id),
    INDEX idx_status (status),
    INDEX idx_member_id (member_id),
    INDEX idx_trigger_time (trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT联动触发日志';

-- ============================================
-- 5. IoT 设备告警
-- ============================================
DROP TABLE IF EXISTS biz_iot_device_alert;
CREATE TABLE biz_iot_device_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    device_id BIGINT COMMENT '设备ID',
    device_name VARCHAR(128) COMMENT '设备名称',
    alert_level VARCHAR(16) DEFAULT 'warning' COMMENT '告警级别',
    alert_type VARCHAR(32) DEFAULT 'error' COMMENT '告警类型',
    title VARCHAR(256) COMMENT '标题',
    message VARCHAR(1000) COMMENT '详情',
    status VARCHAR(16) DEFAULT 'pending' COMMENT '状态',
    handler_id BIGINT COMMENT '处理人ID',
    handler_name VARCHAR(64) COMMENT '处理人姓名',
    handle_time DATETIME COMMENT '处理时间',
    handle_note VARCHAR(500) COMMENT '处理备注',
    alert_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_device_id (device_id),
    INDEX idx_alert_level (alert_level),
    INDEX idx_status (status),
    INDEX idx_alert_time (alert_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IoT设备告警';

-- ============================================
-- 6. RBAC 权限
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('iot:device:manage', 'IoT设备管理', 'iot', 'menu', 1, 0, NOW()),
    ('iot:data', 'IoT数据采集', 'iot', 'menu', 1, 0, NOW()),
    ('iot:rule', 'IoT联动规则', 'iot', 'menu', 1, 0, NOW()),
    ('iot:alert', 'IoT告警中心', 'iot', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 7. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/iot' AND deleted = 0 LIMIT 1),
    'IotManagement', '/iot/management', 'IoT设备联动', 'Cpu', 1, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/iot/management' AND deleted = 0
);

-- ============================================
-- 8. 示例设备数据
-- ============================================
INSERT INTO biz_iot_device (device_code, device_name, device_type, device_model, brand, protocol, location, status, activated, activated_time, total_reports, deleted, create_time)
VALUES
    ('SKIN-001', 'VISIA皮肤检测仪', 'skin_analyzer', 'VISIA Gen7', 'Canfield', 'mqtt', '1号检测室', 'online', 1, NOW(), 580, 0, NOW()),
    ('MIRROR-001', '智能美容镜', 'smart_mirror', 'SM-2024', '美业智镜', 'mqtt', 'VIP接待区', 'online', 1, NOW(), 320, 0, NOW()),
    ('BED-001', '智能按摩床', 'massage_bed', 'MB-Pro', '荣泰', 'http', 'SPA 1号房', 'online', 1, NOW(), 156, 0, NOW()),
    ('LOCK-001', '智能门锁-VIP', 'smart_lock', 'SL-V1', '云丁', 'http', 'VIP 1号房', 'online', 1, NOW(), 890, 0, NOW()),
    ('LED-001', 'LED红光治疗仪', 'led_lamp', 'LED-R', '科医人', 'mqtt', '皮肤治疗室', 'offline', 1, NOW(), 0, 0, NOW());

-- ============================================
-- 9. 示例触发规则
-- ============================================
INSERT INTO biz_iot_trigger_rule (rule_name, device_type, data_type, trigger_condition, action_type, priority, enabled, deleted, create_time)
VALUES
    ('水分低自动发券', 'skin_analyzer', 'detection', '{"operator":"lt","metric":"水分","value":30}', 'send_coupon', 9, 1, 0, NOW()),
    ('弹性差提醒跟进', 'skin_analyzer', 'detection', '{"operator":"lt","metric":"弹性","value":0.6}', 'create_followup', 8, 1, 0, NOW()),
    ('油分过高打标签', 'skin_analyzer', 'detection', '{"operator":"gt","metric":"油分","value":50}', 'update_member_tag', 7, 1, 0, NOW()),
    ('治疗数据超标通知', 'massage_bed', 'treatment', '{"operator":"gt","metric":"力度","value":80}', 'notify_employee', 6, 1, 0, NOW()),
    ('智能锁离线告警', 'smart_lock', 'heartbeat', '{"operator":"gt","metric":"offline_minutes","value":10}', 'send_sms', 10, 1, 0, NOW());