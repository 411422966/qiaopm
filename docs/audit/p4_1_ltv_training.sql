-- P4.1 LTV 训练特征 + 真实模型管理
CREATE TABLE IF NOT EXISTS `ltv_training_feature` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `member_id` BIGINT NOT NULL,
  `feature_date` DATE NOT NULL COMMENT '特征快照日期',

  -- RFM 基础
  `recency_days` INT DEFAULT 0 COMMENT '距离上次消费天数',
  `frequency_total` INT DEFAULT 0 COMMENT '历史消费频次',
  `frequency_90d` INT DEFAULT 0 COMMENT '近90天频次',
  `monetary_total` DECIMAL(12,2) DEFAULT 0 COMMENT '历史GMV',
  `monetary_90d` DECIMAL(12,2) DEFAULT 0 COMMENT '近90天GMV',
  `aov` DECIMAL(10,2) DEFAULT 0 COMMENT '客单价',
  `avg_interval_days` INT DEFAULT 0 COMMENT '平均消费间隔(天)',

  -- 长期特征
  `tenure_days` INT DEFAULT 0 COMMENT '入会时长(天)',
  `member_age_group` VARCHAR(16) DEFAULT NULL COMMENT 'NEW/REGULAR/MATURE/OLD',
  `lifecycle_stage` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DORMANT/CHURNED/NEW',

  -- 消费模式
  `service_category_count` INT DEFAULT 0 COMMENT '消费过的类别数',
  `preferred_category_id` BIGINT DEFAULT NULL,
  `preferred_employee_id` BIGINT DEFAULT NULL,
  `preferred_store_id` BIGINT DEFAULT NULL,
  `preferred_hour` TINYINT DEFAULT 0 COMMENT '偏好时段(0-23)',
  `weekend_ratio` DECIMAL(5,2) DEFAULT 0 COMMENT '周末消费占比%',
  `holiday_ratio` DECIMAL(5,2) DEFAULT 0 COMMENT '节假日消费占比%',

  -- 钱包/套餐
  `wallet_balance` DECIMAL(12,2) DEFAULT 0 COMMENT '储值余额',
  `active_packages` INT DEFAULT 0 COMMENT '有效套餐数',
  `total_package_balance` DECIMAL(12,2) DEFAULT 0 COMMENT '套餐剩余价值',
  `package_consumption_rate` DECIMAL(5,2) DEFAULT 0 COMMENT '套餐消耗率%',

  -- 行为/互动
  `booking_count` INT DEFAULT 0 COMMENT '预约数',
  `noshow_count` INT DEFAULT 0 COMMENT '爽约数',
  `noshow_rate` DECIMAL(5,2) DEFAULT 0 COMMENT '爽约率%',
  `review_count` INT DEFAULT 0 COMMENT '评价数',
  `avg_rating` DECIMAL(5,2) DEFAULT 0 COMMENT '平均评分',
  `complaint_count` INT DEFAULT 0 COMMENT '投诉数',
  `coupon_used_count` INT DEFAULT 0 COMMENT '用券数',
  `point_balance` BIGINT DEFAULT 0 COMMENT '积分余额',

  -- 价值度
  `member_level` VARCHAR(16) DEFAULT 'NORMAL' COMMENT 'NORMAL/SILVER/GOLD/DIAMOND',
  `rfm_segment` VARCHAR(32) DEFAULT NULL COMMENT 'RFM分群',
  `churn_risk_score` DECIMAL(5,2) DEFAULT 0 COMMENT '流失风险分(0-100)',
  `ltv_score` DECIMAL(10,2) DEFAULT 0 COMMENT 'LTV评分',

  -- 标签（用作模型的 label/feature）
  `lifecycle_label` VARCHAR(16) DEFAULT NULL COMMENT 'TRAINING_LABEL: ACTIVE/CHURNED',
  `next_90d_revenue` DECIMAL(12,2) DEFAULT NULL COMMENT '未来90天实际GMV（LTV训练标签）',
  `next_90d_active` TINYINT DEFAULT NULL COMMENT '未来90天是否活跃（0/1）',

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY `uk_member_date` (`tenant_id`, `member_id`, `feature_date`),
  INDEX `idx_tenant_lifecycle` (`tenant_id`, `lifecycle_label`),
  INDEX `idx_tenant_date` (`tenant_id`, `feature_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LTV 训练特征表';

-- LTV 真实模型（替代 mock）
CREATE TABLE IF NOT EXISTS `ltv_model_registry` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `model_code` VARCHAR(64) NOT NULL COMMENT '模型编码',
  `model_name` VARCHAR(128) NOT NULL,
  `model_type` VARCHAR(32) NOT NULL DEFAULT 'LTV_REGRESSION' COMMENT 'LTV_REGRESSION/CHURN_CLASSIFICATION/CLV_REGRESSION',
  `algorithm` VARCHAR(32) DEFAULT 'MLR' COMMENT 'MLR/RIDGE/GBM/XGBOOST',

  -- 训练参数
  `feature_window_days` INT DEFAULT 180 COMMENT '特征窗口(天)',
  `label_window_days` INT DEFAULT 90 COMMENT '标签窗口(天)',
  `training_samples` INT DEFAULT 0 COMMENT '训练样本数',
  `feature_count` INT DEFAULT 0 COMMENT '特征数',

  -- 评估指标
  `train_mae` DECIMAL(10,4) DEFAULT NULL COMMENT '训练 MAE',
  `train_rmse` DECIMAL(10,4) DEFAULT NULL COMMENT '训练 RMSE',
  `train_mape` DECIMAL(10,4) DEFAULT NULL COMMENT '训练 MAPE',
  `train_r2` DECIMAL(10,4) DEFAULT NULL COMMENT '训练 R²',
  `val_mae` DECIMAL(10,4) DEFAULT NULL COMMENT '验证 MAE',
  `val_rmse` DECIMAL(10,4) DEFAULT NULL COMMENT '验证 RMSE',
  `val_mape` DECIMAL(10,4) DEFAULT NULL COMMENT '验证 MAPE',
  `val_r2` DECIMAL(10,4) DEFAULT NULL COMMENT '验证 R²',
  `val_auc` DECIMAL(10,4) DEFAULT NULL COMMENT '验证 AUC（分类）',

  -- 特征重要性
  `feature_importance_json` TEXT COMMENT '特征重要性JSON {feature: weight}',

  -- 状态
  `is_active` TINYINT DEFAULT 0 COMMENT '1=激活',
  `status` TINYINT DEFAULT 1 COMMENT '1=草稿 2=已训练 3=已部署 4=已下线',
  `trained_at` DATETIME DEFAULT NULL,
  `deployed_at` DATETIME DEFAULT NULL,
  `version` VARCHAR(16) DEFAULT 'v1',
  `model_formula` TEXT COMMENT 'MLR 公式：w1*f1 + w2*f2 + ... + bias',

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX `idx_tenant_status` (`tenant_id`, `status`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LTV 模型注册表';

-- LTV 训练运行日志
CREATE TABLE IF NOT EXISTS `ltv_training_run_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `model_id` BIGINT DEFAULT NULL,
  `run_type` VARCHAR(32) DEFAULT 'TRAIN' COMMENT 'TRAIN/EVAL/PREDICT',
  `step` VARCHAR(32) NOT NULL COMMENT 'ETL/FEATURE/TRAIN/EVAL/DEPLOY',
  `start_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `end_at` DATETIME DEFAULT NULL,
  `duration_ms` INT DEFAULT NULL,
  `samples` INT DEFAULT 0,
  `metric` VARCHAR(64) DEFAULT NULL,
  `metric_value` DECIMAL(10,4) DEFAULT NULL,
  `message` TEXT,
  `error` TEXT,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,

  INDEX `idx_tenant_run` (`tenant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LTV 训练运行日志';
