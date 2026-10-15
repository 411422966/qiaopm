-- P4.1 AI智能排班：客流预测特征表
CREATE TABLE IF NOT EXISTS `booking_forecast_feature` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `store_id` BIGINT DEFAULT NULL COMMENT '门店ID',
  `feature_date` DATE NOT NULL COMMENT '特征日期',
  `feature_hour` TINYINT DEFAULT NULL COMMENT '小时（0-23），NULL表示按天聚合',
  `employee_id` BIGINT DEFAULT NULL COMMENT '技师ID，NULL表示按门店聚合',

  -- 时序特征
  `booking_count` INT NOT NULL DEFAULT 0 COMMENT '预约数',
  `checkin_count` INT NOT NULL DEFAULT 0 COMMENT '签到数',
  `complete_count` INT NOT NULL DEFAULT 0 COMMENT '完成数',
  `noshow_count` INT NOT NULL DEFAULT 0 COMMENT '爽约数',
  `cancel_count` INT NOT NULL DEFAULT 0 COMMENT '取消数',

  -- 派生特征
  `noshow_rate` DECIMAL(5,2) DEFAULT 0.00 COMMENT '爽约率%',
  `checkin_rate` DECIMAL(5,2) DEFAULT 0.00 COMMENT '到店率%',
  `avg_service_duration_min` INT DEFAULT 0 COMMENT '平均服务时长(分钟)',
  `peak_hour` TINYINT DEFAULT 0 COMMENT '峰值时段(0-23)',

  -- 时间特征
  `day_of_week` TINYINT DEFAULT 0 COMMENT '星期几(1=周一)',
  `is_weekend` TINYINT DEFAULT 0 COMMENT '是否周末',
  `is_holiday` TINYINT DEFAULT 0 COMMENT '是否节假日',
  `is_payday` TINYINT DEFAULT 0 COMMENT '是否发薪日',
  `is_member_day` TINYINT DEFAULT 0 COMMENT '是否会员日',
  `days_before_holiday` INT DEFAULT 0 COMMENT '距节假日天数',
  `days_after_holiday` INT DEFAULT 0 COMMENT '节假日后天数',

  -- 历史同期
  `same_dow_avg7` DECIMAL(10,2) DEFAULT 0 COMMENT '过去7个同星期平均',
  `same_dow_avg30` DECIMAL(10,2) DEFAULT 0 COMMENT '过去30天同星期平均',

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX `idx_tenant_date` (`tenant_id`, `feature_date`),
  INDEX `idx_tenant_store_date` (`tenant_id`, `store_id`, `feature_date`),
  INDEX `idx_tenant_emp_date` (`tenant_id`, `employee_id`, `feature_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客流预测特征表';

-- 客流预测模型结果表
CREATE TABLE IF NOT EXISTS `booking_forecast_result` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `store_id` BIGINT DEFAULT NULL,
  `forecast_date` DATE NOT NULL COMMENT '预测日期',
  `forecast_hour` TINYINT DEFAULT NULL COMMENT 'NULL=按天预测',
  `forecast_type` VARCHAR(32) NOT NULL DEFAULT 'BOOKING_COUNT' COMMENT 'BOOKING_COUNT/CHECKIN_COUNT/REVENUE',

  `predicted_value` DECIMAL(10,2) NOT NULL COMMENT '预测值',
  `lower_bound` DECIMAL(10,2) DEFAULT 0 COMMENT '95%置信下界',
  `upper_bound` DECIMAL(10,2) DEFAULT 0 COMMENT '95%置信上界',
  `confidence` DECIMAL(5,4) DEFAULT 0.95 COMMENT '置信度',

  `algorithm` VARCHAR(64) NOT NULL DEFAULT 'EWMA' COMMENT 'EWMA/PROPHET/SARIMA/ARIMA/HYBRID',
  `model_version` VARCHAR(32) DEFAULT 'v1' COMMENT '模型版本',
  `base_window_days` INT DEFAULT 90 COMMENT '训练窗口(天)',

  `actual_value` DECIMAL(10,2) DEFAULT NULL COMMENT '实际值（事后填入）',
  `error_pct` DECIMAL(8,4) DEFAULT NULL COMMENT '误差百分比%',
  `mae` DECIMAL(10,2) DEFAULT NULL COMMENT '绝对误差',

  `generated_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,

  INDEX `idx_tenant_date` (`tenant_id`, `forecast_date`),
  INDEX `idx_tenant_store_date` (`tenant_id`, `store_id`, `forecast_date`),
  UNIQUE KEY `uk_forecast` (`tenant_id`, `store_id`, `forecast_type`, `forecast_date`, `forecast_hour`, `algorithm`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客流预测结果表';

-- 预测准确度评估表
CREATE TABLE IF NOT EXISTS `forecast_accuracy_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `store_id` BIGINT DEFAULT NULL,
  `algorithm` VARCHAR(64) NOT NULL,
  `model_version` VARCHAR(32) DEFAULT 'v1',
  `eval_date` DATE NOT NULL,
  `window_days` INT DEFAULT 30 COMMENT '评估窗口',

  `mae` DECIMAL(10,2) DEFAULT NULL COMMENT '平均绝对误差',
  `mape` DECIMAL(8,4) DEFAULT NULL COMMENT '平均绝对百分比误差%',
  `rmse` DECIMAL(10,2) DEFAULT NULL COMMENT '均方根误差',
  `smape` DECIMAL(8,4) DEFAULT NULL COMMENT '对称MAPE%',

  `weekday_mae` DECIMAL(10,2) DEFAULT NULL COMMENT '工作日MAE',
  `weekend_mae` DECIMAL(10,2) DEFAULT NULL COMMENT '周末MAE',

  `conclusion` VARCHAR(128) DEFAULT NULL COMMENT 'GOOD/FAIR/POOR',
  `recommendation` VARCHAR(256) DEFAULT NULL COMMENT '优化建议',

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,

  INDEX `idx_tenant_date` (`tenant_id`, `eval_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预测准确度评估日志';

-- 智能排班建议表
CREATE TABLE IF NOT EXISTS `schedule_recommendation` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `store_id` BIGINT NOT NULL,
  `schedule_date` DATE NOT NULL COMMENT '建议排班日期',

  `recommendation_json` TEXT COMMENT '排班建议JSON',
  `coverage_rate` DECIMAL(5,2) DEFAULT 0 COMMENT '覆盖率%',
  `utilization_rate` DECIMAL(5,2) DEFAULT 0 COMMENT '利用率%',
  `score` DECIMAL(5,2) DEFAULT 0 COMMENT '综合评分(0-100)',

  `algorithm` VARCHAR(64) DEFAULT 'GREEDY_V1' COMMENT 'GREEDY/ILP/RL',
  `model_version` VARCHAR(32) DEFAULT 'v1',

  `status` VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/APPLIED/REJECTED',
  `applied_at` DATETIME DEFAULT NULL,

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,

  INDEX `idx_tenant_date_status` (`tenant_id`, `schedule_date`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能排班建议表';
