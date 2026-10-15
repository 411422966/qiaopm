-- ============================================================
-- AI-002 客流预测两张表 (P3 验证补漏 / 2026-08-27)
-- 说明:
--   - 与以下 2 个实体严格对齐 (@TableName 值):
--       com.meiyetec.entity.BookingForecastFeature -> booking_forecast_feature
--       com.meiyetec.entity.BookingForecastResult  -> booking_forecast_result
--   - 这是 P3 验证发现的"链路断裂"项: 实体/Mapper/Controller(@RestController Service,
--     BookingForecastService @RequestMapping("/ai/forecast")) 全在,
--     但 backend/ 下完全没有这两张表的 DDL。
--   - 训练数据/特征工程写入 booking_forecast_feature (每条 = 一个 (门店, 日期, 小时, 员工) 的特征向量);
--     模型输出与精度评估写入 booking_forecast_result。
-- 关联表:
--   - booking_forecast_feature.tenant_id  -> sys_tenant.id
--   - booking_forecast_feature.employee_id -> employee.id
--   - booking_forecast_result.tenant_id   -> sys_tenant.id
-- ============================================================

-- ============================================================
-- 1. 预测特征表 booking_forecast_feature
-- ============================================================
CREATE TABLE IF NOT EXISTS booking_forecast_feature (
  id                       BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK',
  tenant_id                BIGINT        NULL     COMMENT '所属租户ID',
  store_id                 BIGINT        NULL     COMMENT '所属门店ID',
  feature_date             DATE          NULL     COMMENT '特征对应日期',
  feature_hour             INT           NULL     COMMENT '特征对应小时 (0-23, NULL=全天汇总)',
  employee_id              BIGINT        NULL     COMMENT '对应员工ID (NULL=全员工汇总)',

  -- 时序统计
  booking_count            INT           NULL     DEFAULT 0 COMMENT '预约数',
  checkin_count            INT           NULL     DEFAULT 0 COMMENT '签到数',
  complete_count           INT           NULL     DEFAULT 0 COMMENT '完成数',
  noshow_count             INT           NULL     DEFAULT 0 COMMENT '未到数',
  cancel_count             INT           NULL     DEFAULT 0 COMMENT '取消数',

  -- 派生比率
  noshow_rate              DECIMAL(5,4)  NULL     COMMENT '未到率 0.0000-1.0000',
  checkin_rate             DECIMAL(5,4)  NULL     COMMENT '签到率',
  avg_service_duration_min INT           NULL     COMMENT '平均服务耗时(分钟)',
  peak_hour                INT           NULL     COMMENT '高峰小时 (0-23)',

  -- 时间特征
  day_of_week              INT           NULL     COMMENT '周几 (1=周一 ... 7=周日)',
  is_weekend               INT           NULL     DEFAULT 0 COMMENT '是否周末 0否 1是',
  is_holiday               INT           NULL     DEFAULT 0 COMMENT '是否节假日 0否 1是',
  is_payday                INT           NULL     DEFAULT 0 COMMENT '是否发薪日 0否 1是',
  is_member_day            INT           NULL     DEFAULT 0 COMMENT '是否会员日 0否 1是',
  days_before_holiday      INT           NULL     COMMENT '距节假日天数',
  days_after_holiday       INT           NULL     COMMENT '距节假日后几天',

  -- 历史同期
  same_dow_avg_7           DECIMAL(10,2) NULL     COMMENT '同周次近7天均值',
  same_dow_avg_30          DECIMAL(10,2) NULL     COMMENT '同周次近30天均值',

  created_at               DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP,
  updated_at               DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_bff_tenant (tenant_id),
  KEY idx_bff_store_date (store_id, feature_date),
  KEY idx_bff_employee (employee_id),
  KEY idx_bff_date_hour (feature_date, feature_hour),
  UNIQUE KEY uk_bff_unique (tenant_id, store_id, feature_date, feature_hour, employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='客流预测特征 (BookingForecastFeature)';

-- ============================================================
-- 2. 预测结果表 booking_forecast_result
-- ============================================================
CREATE TABLE IF NOT EXISTS booking_forecast_result (
  id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK',
  tenant_id         BIGINT        NULL     COMMENT '所属租户ID',
  store_id          BIGINT        NULL     COMMENT '所属门店ID',
  forecast_date     DATE          NULL     COMMENT '预测目标日期',
  forecast_hour     INT           NULL     COMMENT '预测目标小时 (NULL=全天汇总)',
  forecast_type     VARCHAR(32)   NULL     COMMENT 'BOOKING_COUNT/REVENUE/STAFF_LOAD/OCCUPANCY',

  predicted_value   DECIMAL(18,4) NULL     COMMENT '预测值',
  lower_bound       DECIMAL(18,4) NULL     COMMENT '预测下限 (置信区间下界)',
  upper_bound       DECIMAL(18,4) NULL     COMMENT '预测上限 (置信区间上界)',
  confidence        DECIMAL(5,4)  NULL     COMMENT '置信度 0.0000-1.0000',

  algorithm         VARCHAR(64)   NULL     COMMENT '算法名 (PROPHET/LSTM/XGBOOST/MOVING_AVG)',
  model_version     VARCHAR(32)   NULL     COMMENT '模型版本',
  base_window_days  INT           NULL     COMMENT '训练窗口天数',

  actual_value      DECIMAL(18,4) NULL     COMMENT '实际值 (回填用于精度评估)',
  error_pct         DECIMAL(8,4)  NULL     COMMENT '误差百分比',
  mae               DECIMAL(18,4) NULL     COMMENT '平均绝对误差',

  generated_at      DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '预测生成时间',
  created_at        DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_bfr_tenant (tenant_id),
  KEY idx_bfr_store_date (store_id, forecast_date),
  KEY idx_bfr_type (forecast_type),
  KEY idx_bfr_algo (algorithm)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='客流预测结果与精度评估 (BookingForecastResult)';