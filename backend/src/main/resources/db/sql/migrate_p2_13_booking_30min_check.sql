-- P2-13 IND-15 预约30分钟精度
-- 为 biz_booking.start_time 和 end_time 添加 CHECK 约束
-- MySQL 8.0.16+ 强制执行 CHECK 约束；旧版本仅解析但依赖服务层校验

-- 添加开始时间 30 分钟精度 CHECK 约束
ALTER TABLE biz_booking
ADD CONSTRAINT chk_booking_start_time_half_hour
CHECK (MINUTE(start_time) IN (0, 30));

-- 添加结束时间 30 分钟精度 CHECK 约束
ALTER TABLE biz_booking
ADD CONSTRAINT chk_booking_end_time_half_hour
CHECK (MINUTE(end_time) IN (0, 30));
