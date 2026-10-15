-- ============================================
-- 房间+技师双资源排程
-- P0-M06-010：房间+技师双资源排程
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 资源冲突日志
-- ============================================
DROP TABLE IF EXISTS biz_resource_conflict_log;
CREATE TABLE biz_resource_conflict_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    conflict_type VARCHAR(32) DEFAULT 'room_conflict' COMMENT '冲突类型',
    resource_type VARCHAR(16) DEFAULT 'room' COMMENT '资源类型',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    resource_name VARCHAR(128) COMMENT '资源名称',
    new_schedule_id BIGINT COMMENT '新排程ID',
    existing_schedule_id BIGINT COMMENT '已有排程ID',
    conflict_start DATETIME COMMENT '冲突开始',
    conflict_end DATETIME COMMENT '冲突结束',
    resolve_action VARCHAR(32) DEFAULT 'manual_resolve' COMMENT '处理方式',
    resolve_by BIGINT COMMENT '处理人',
    resolve_time DATETIME COMMENT '处理时间',
    detect_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '检测时间',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_store_id (store_id),
    INDEX idx_conflict_type (conflict_type),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_detect_time (detect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源冲突日志';

-- ============================================
-- 2. 资源利用率统计
-- ============================================
DROP TABLE IF EXISTS biz_resource_utilization;
CREATE TABLE biz_resource_utilization (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    resource_type VARCHAR(16) DEFAULT 'room' COMMENT '资源类型',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    resource_name VARCHAR(128) COMMENT '资源名称',
    stat_date DATE NOT NULL COMMENT '统计日期',
    total_available_minutes INT DEFAULT 0 COMMENT '总可用时长(分钟)',
    booked_minutes INT DEFAULT 0 COMMENT '已预约时长',
    actual_service_minutes INT DEFAULT 0 COMMENT '实际服务时长',
    idle_minutes INT DEFAULT 0 COMMENT '空闲时长',
    utilization_rate DECIMAL(5,2) DEFAULT 0 COMMENT '利用率(0-100)',
    order_count INT DEFAULT 0 COMMENT '订单数',
    cancel_count INT DEFAULT 0 COMMENT '取消数',
    no_show_count INT DEFAULT 0 COMMENT '爽约数',
    revenue DECIMAL(15,2) DEFAULT 0 COMMENT '收入',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_resource_date (resource_type, resource_id, stat_date),
    INDEX idx_store_id (store_id),
    INDEX idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源利用率统计';

-- ============================================
-- 3. 资源匹配规则
-- ============================================
DROP TABLE IF EXISTS biz_resource_match_rule;
CREATE TABLE biz_resource_match_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    service_id BIGINT NOT NULL COMMENT '服务ID',
    service_name VARCHAR(128) COMMENT '服务名称',
    required_room_types VARCHAR(500) COMMENT '所需房间类型JSON',
    min_room_level INT DEFAULT 1 COMMENT '最低房间等级',
    required_skills VARCHAR(500) COMMENT '所需技师技能JSON',
    min_technician_level INT DEFAULT 1 COMMENT '最低技师等级',
    duration_minutes INT DEFAULT 60 COMMENT '单次服务时长',
    required_technician_count INT DEFAULT 1 COMMENT '所需技师人数',
    required_room_capacity INT DEFAULT 1 COMMENT '房间使用人数',
    price DECIMAL(15,2) DEFAULT 0 COMMENT '服务价格',
    need_dual_technician TINYINT DEFAULT 0 COMMENT '是否需要双技师',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_service_id (service_id),
    INDEX idx_store_id (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源匹配规则';

-- ============================================
-- 4. 增强 Booking 表索引
-- ============================================
-- 检查并添加索引
ALTER TABLE biz_booking ADD INDEX idx_employee_date (employee_id, booking_date);
ALTER TABLE biz_booking ADD INDEX idx_room_date (room_id, booking_date);

-- ============================================
-- 5. RBAC 权限
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('resource:schedule', '排程管理', 'resource', 'menu', 1, 0, NOW()),
    ('resource:utilization', '资源利用率', 'resource', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 6. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/resource' AND deleted = 0 LIMIT 1),
    'ResourceSchedule', '/resource/schedule', '双资源排程', 'Calendar', 1, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/resource/schedule' AND deleted = 0
);

-- ============================================
-- 7. 默认匹配规则示例
-- ============================================
INSERT INTO biz_resource_match_rule (service_name, required_room_types, required_skills, duration_minutes, required_technician_count, min_room_level, need_dual_technician, deleted, create_time)
VALUES
    ('面部护理', '["facial","vip"]', '["facial","skincare"]', 60, 1, 2, 0, 0, NOW()),
    ('身体按摩', '["massage"]', '["massage"]', 90, 1, 1, 0, 0, NOW()),
    ('豪华SPA', '["vip","spa"]', '["spa","facial"]', 120, 2, 3, 1, 0, NOW()),
    ('美甲服务', '["nail"]', '["nail_art"]', 60, 1, 1, 0, 0, NOW()),
    ('美睫服务', '["eyelash"]', '["eyelash"]', 90, 1, 1, 0, 0, NOW());