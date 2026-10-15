-- ============================================
-- 员工考勤打卡模块
-- P0-M08-001：员工考勤打卡模块
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 考勤配置表
-- ============================================
DROP TABLE IF EXISTS attendance_config;
CREATE TABLE attendance_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT COMMENT '门店ID，null表示通用配置',
    config_name VARCHAR(128) NOT NULL COMMENT '配置名称',
    work_start_time TIME NOT NULL COMMENT '上班时间',
    work_end_time TIME NOT NULL COMMENT '下班时间',
    allow_late_minutes INT DEFAULT 5 COMMENT '允许迟到分钟数',
    flexible_enabled TINYINT DEFAULT 0 COMMENT '是否启用弹性打卡：0-否，1-是',
    flexible_start_time TIME COMMENT '弹性上班开始时间',
    flexible_end_time TIME COMMENT '弹性上班结束时间',
    early_leave_minutes INT DEFAULT 30 COMMENT '早退警告分钟数',
    location_enabled TINYINT DEFAULT 0 COMMENT '是否启用位置打卡：0-否，1-是',
    location_config TEXT COMMENT '打卡位置配置(JSON)',
    face_enabled TINYINT DEFAULT 0 COMMENT '是否启用人脸识别：0-否，1-是',
    status TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_store_id (store_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤配置表';

-- ============================================
-- 2. 考勤打卡记录表
-- ============================================
DROP TABLE IF EXISTS attendance_record;
CREATE TABLE attendance_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL COMMENT '员工ID',
    check_date DATE NOT NULL COMMENT '打卡日期',
    check_in_time DATETIME COMMENT '上班打卡时间',
    check_out_time DATETIME COMMENT '下班打卡时间',
    check_in_method VARCHAR(32) COMMENT '上班打卡方式：app/pad/face',
    check_out_method VARCHAR(32) COMMENT '下班打卡方式',
    check_in_location VARCHAR(255) COMMENT '上班打卡地点',
    check_out_location VARCHAR(255) COMMENT '下班打卡地点',
    check_in_status VARCHAR(32) COMMENT '上班打卡状态：normal/late/early/absence',
    check_out_status VARCHAR(32) COMMENT '下班打卡状态：normal/early/absence',
    is_late TINYINT DEFAULT 0 COMMENT '是否迟到：0-否，1-是',
    late_minutes INT DEFAULT 0 COMMENT '迟到分钟数',
    is_early_leave TINYINT DEFAULT 0 COMMENT '是否早退：0-否，1-是',
    early_leave_minutes INT DEFAULT 0 COMMENT '早退分钟数',
    is_absence TINYINT DEFAULT 0 COMMENT '是否旷工：0-否，1-是',
    work_minutes INT DEFAULT 0 COMMENT '出勤时长(分钟)',
    remark VARCHAR(500) COMMENT '备注',
    config_id BIGINT COMMENT '考勤配置ID',
    store_id BIGINT COMMENT '门店ID',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_employee_date (employee_id, check_date, deleted),
    INDEX idx_store_id (store_id),
    INDEX idx_check_date (check_date),
    INDEX idx_employee_id (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤打卡记录表';

-- ============================================
-- 3. 请假申请表
-- ============================================
DROP TABLE IF EXISTS attendance_leave_request;
CREATE TABLE attendance_leave_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL COMMENT '员工ID',
    leave_type VARCHAR(32) NOT NULL COMMENT '请假类型：annual/sick/personal/marriage/maternity/funeral/other',
    start_date DATE NOT NULL COMMENT '开始日期',
    end_date DATE NOT NULL COMMENT '结束日期',
    leave_days INT DEFAULT 1 COMMENT '请假时长(天数)',
    reason VARCHAR(1000) COMMENT '请假原因',
    status VARCHAR(32) DEFAULT 'pending' COMMENT '申请状态：pending/approved/rejected/cancelled',
    approver_id BIGINT COMMENT '审批人ID',
    approve_time DATETIME COMMENT '审批时间',
    approve_remark VARCHAR(500) COMMENT '审批意见',
    store_id BIGINT COMMENT '申请门店ID',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_employee_id (employee_id),
    INDEX idx_store_id (store_id),
    INDEX idx_status (status),
    INDEX idx_start_date (start_date),
    INDEX idx_end_date (end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假申请表';

-- ============================================
-- 4. 默认考勤配置
-- ============================================
INSERT INTO attendance_config (
    store_id, config_name, work_start_time, work_end_time,
    allow_late_minutes, early_leave_minutes,
    flexible_enabled, location_enabled, face_enabled, status
) VALUES (
    NULL, '通用考勤配置', '09:00:00', '18:00:00',
    5, 30,
    0, 0, 0, 1
);

-- ============================================
-- 5. RBAC 权限配置
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('employee:attendance', '考勤打卡', 'employee', 'menu', 1, 0, NOW()),
    ('employee:attendance:list', '考勤记录', 'employee', 'menu', 1, 0, NOW()),
    ('employee:leave', '请假管理', 'employee', 'menu', 1, 0, NOW()),
    ('employee:attendance:stat', '考勤统计', 'employee', 'menu', 1, 0, NOW()),
    ('employee:attendance:config', '考勤规则配置', 'employee', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 6. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM (SELECT id FROM sys_menu WHERE title='系统设置' AND parent_id=0 LIMIT 1) AS tmp),
    'AttendanceManagement', '/attendance', '考勤管理', 'Timer', 50, 'directory', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/attendance' AND deleted = 0
);

-- 子菜单
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/attendance' AND deleted = 0 LIMIT 1),
    'AttendanceIndex', '/attendance/index', '考勤打卡', 'Position', 1, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/attendance/index' AND deleted = 0
);

INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/attendance' AND deleted = 0 LIMIT 1),
    'AttendanceRecords', '/attendance/records', '考勤记录', 'Document', 2, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/attendance/records' AND deleted = 0
);

INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/attendance' AND deleted = 0 LIMIT 1),
    'AttendanceLeave', '/attendance/leave', '请假管理', 'Calendar', 3, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/attendance/leave' AND deleted = 0
);

INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/attendance' AND deleted = 0 LIMIT 1),
    'AttendanceStatistics', '/attendance/statistics', '考勤统计', 'DataAnalysis', 4, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/attendance/statistics' AND deleted = 0
);

INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/attendance' AND deleted = 0 LIMIT 1),
    'AttendanceConfig', '/attendance/config', '考勤规则', 'Setting', 5, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/attendance/config' AND deleted = 0
);
