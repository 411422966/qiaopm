-- ============================================================
-- M06 预约日历模块 - 数据库初始化
-- 更新日期：2026-08-14 (P0-DB-001-G 回填 biz_booking / biz_employee_schedule / biz_room)
-- ============================================================
-- 历史来源：fix_booking_calendar_tables.sql
-- 上下文：BookingController / ScheduleController / RoomController
-- 注意：使用 CREATE TABLE IF NOT EXISTS + 保留 DELETE 清理，确保可重放
-- ============================================================

USE meiyetec;

-- ============================================================
-- 1. 房间表 biz_room
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '房间ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    store_id BIGINT DEFAULT NULL COMMENT '门店ID',
    room_name VARCHAR(50) NOT NULL COMMENT '房间名',
    room_type VARCHAR(20) DEFAULT NULL COMMENT '房间类型',
    capacity INT DEFAULT 1 COMMENT '容纳人数',
    equipment VARCHAR(255) DEFAULT NULL COMMENT '设备配置',
    status TINYINT DEFAULT 1 COMMENT '状态：1可用 2维修 3停用',
    sort_no INT DEFAULT 0 COMMENT '排序',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_tenant (tenant_id),
    INDEX idx_store (store_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间表';

-- ============================================================
-- 2. 技师排班表 biz_employee_schedule
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_employee_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '排班ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    employee_id BIGINT NOT NULL COMMENT '技师ID',
    employee_name VARCHAR(50) DEFAULT NULL COMMENT '技师姓名',
    work_date DATE NOT NULL COMMENT '工作日期',
    shift_type TINYINT DEFAULT 4 COMMENT '班次：1早班 2中班 3晚班 4全天',
    start_time TIME DEFAULT NULL COMMENT '开始时间',
    end_time TIME DEFAULT NULL COMMENT '结束时间',
    max_bookings INT DEFAULT 8 COMMENT '最大预约数',
    status TINYINT DEFAULT 1 COMMENT '状态：1正常 2请假 3休息',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_employee (employee_id),
    INDEX idx_date (work_date),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技师排班表';

-- ============================================================
-- 3. 预约表 biz_booking
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_booking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '预约ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    booking_no VARCHAR(32) NOT NULL COMMENT '预约编号',
    member_id BIGINT NOT NULL COMMENT '会员ID',
    member_name VARCHAR(50) DEFAULT NULL COMMENT '会员姓名',
    member_phone VARCHAR(20) DEFAULT NULL COMMENT '会员手机',
    employee_id BIGINT NOT NULL COMMENT '技师ID',
    employee_name VARCHAR(50) DEFAULT NULL COMMENT '技师姓名',
    service_item VARCHAR(100) DEFAULT NULL COMMENT '服务项目',
    room_id BIGINT DEFAULT NULL COMMENT '房间ID',
    room_name VARCHAR(50) DEFAULT NULL COMMENT '房间名',
    booking_date DATE DEFAULT NULL COMMENT '预约日期',
    start_time DATETIME DEFAULT NULL COMMENT '开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    status TINYINT DEFAULT 1 COMMENT '状态：1待确认 2已确认 3已签到 4已完成 5已取消 6��约',
    source TINYINT DEFAULT 1 COMMENT '来源：1会员自助 2前台代约 3电话',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    operator_id BIGINT DEFAULT NULL COMMENT '操作人',
    checkin_time DATETIME DEFAULT NULL COMMENT '签到时间',
    complete_time DATETIME DEFAULT NULL COMMENT '完成时间',
    cancel_time DATETIME DEFAULT NULL COMMENT '取消时间',
    cancel_reason VARCHAR(500) DEFAULT NULL COMMENT '取消原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_member (member_id),
    INDEX idx_employee (employee_id),
    INDEX idx_room (room_id),
    INDEX idx_date (booking_date),
    INDEX idx_status (status),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约表';

-- ============================================================
-- 清掉之前的脏数据
-- ============================================================
DELETE FROM biz_booking;
DELETE FROM biz_employee_schedule;
DELETE FROM biz_room;
ALTER TABLE biz_booking AUTO_INCREMENT = 1;
ALTER TABLE biz_employee_schedule AUTO_INCREMENT = 1;
ALTER TABLE biz_room AUTO_INCREMENT = 1;

-- ============================================================
-- 房间 (4)
-- ============================================================
INSERT INTO biz_room (tenant_id, store_id, room_name, room_type, capacity, equipment, status, sort_no) VALUES
(1, 1, 'VIP1号房',  'VIP',     1, '按摩床+浴缸+音响', 1, 1),
(1, 1, 'VIP2号房',  'VIP',     1, '按摩床+浴缸',      1, 2),
(1, 1, '标准3号房', '单人间',  1, '按摩床',           1, 3),
(1, 1, '标准4号房', '单人间',  1, '按摩床',           1, 4);

-- ============================================================
-- 技师排班：先拿在职技师，再 CROSS JOIN 7 天
-- ============================================================
INSERT INTO biz_employee_schedule
  (tenant_id, employee_id, employee_name, work_date, shift_type, start_time, end_time, max_bookings, status)
SELECT
  1, e.id, e.employee_name,
  DATE_ADD(CURDATE(), INTERVAL d.n DAY),
  4, '09:00:00', '18:00:00', 8, 1
FROM pos_employee e
JOIN (
  SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3
  UNION SELECT 4 UNION SELECT 5 UNION SELECT 6
) d
WHERE e.deleted = 0 AND e.status = 1;

-- ============================================================
-- 预约样本（6 条覆盖各状态）
-- ============================================================
INSERT INTO biz_booking
  (tenant_id, booking_no, member_id, member_name, member_phone, employee_id, employee_name,
   service_item, room_id, room_name, booking_date, start_time, end_time, status, source, remark, operator_id)
VALUES
(1, 'BK20260801100001', 1, '李雅琳', '13800138001', 4, '王雪梅', '面部清洁',
   1, 'VIP1号房', CURDATE(),
   TIMESTAMP(CURDATE(),'10:00:00'), TIMESTAMP(CURDATE(),'11:00:00'),
   4, 1, '已完成的服务', 1),
(1, 'BK20260801110001', 1, '李雅琳', '13800138001', 5, '赵晓彤', '肩颈按摩',
   2, 'VIP2号房', CURDATE(),
   TIMESTAMP(CURDATE(),'11:00:00'), TIMESTAMP(CURDATE(),'12:00:00'),
   3, 1, '已签到未完成', 1),
(1, 'BK20260801140001', 1, '李雅琳', '13800138001', 6, '孙丽华', '身体经络',
   3, '标准3号房', CURDATE(),
   TIMESTAMP(CURDATE(),'14:00:00'), TIMESTAMP(CURDATE(),'15:00:00'),
   2, 1, '已确认预约', 1),
(1, 'BK20260801150001', 2, '张雨涵', '13800138002', 4, '王雪梅', '面部护理',
   1, 'VIP1号房', DATE_ADD(CURDATE(), INTERVAL 1 DAY),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY),'15:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY),'16:00:00'),
   1, 1, '明天下午', 1),
(1, 'BK20260801090001', 3, '陈思琪', '13800138003', 7, '周敏', '足部护理',
   4, '标准4号房', CURDATE(),
   TIMESTAMP(CURDATE(),'09:00:00'), TIMESTAMP(CURDATE(),'10:00:00'),
   5, 2, '客户临时取消', 1),
(1, 'BK20260802090001', 4, '黄梦瑶', '13800138004', 4, '王雪梅', '面部清洁',
   2, 'VIP2号房', DATE_ADD(CURDATE(), INTERVAL 2 DAY),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY),'09:00:00'),
   TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY),'10:00:00'),
   1, 1, '新人体验', 1);

-- ============================================================
-- 验证（用 ASCII 列名避免 console 编码问题）
-- ============================================================
SELECT 'biz_room'           AS t, COUNT(*) AS cnt FROM biz_room WHERE deleted=0
UNION ALL SELECT 'biz_employee_schedule', COUNT(*) FROM biz_employee_schedule WHERE deleted=0
UNION ALL SELECT 'biz_booking total',   COUNT(*) FROM biz_booking WHERE deleted=0
UNION ALL SELECT 'status_1_pending',    COUNT(*) FROM biz_booking WHERE deleted=0 AND status=1
UNION ALL SELECT 'status_2_confirmed',  COUNT(*) FROM biz_booking WHERE deleted=0 AND status=2
UNION ALL SELECT 'status_3_arrived',    COUNT(*) FROM biz_booking WHERE deleted=0 AND status=3
UNION ALL SELECT 'status_4_done',       COUNT(*) FROM biz_booking WHERE deleted=0 AND status=4
UNION ALL SELECT 'status_5_cancel',     COUNT(*) FROM biz_booking WHERE deleted=0 AND status=5
UNION ALL SELECT 'status_6_miss',       COUNT(*) FROM biz_booking WHERE deleted=0 AND status=6;
