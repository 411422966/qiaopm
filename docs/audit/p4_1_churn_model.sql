-- P4.1 T1.11 Churn 模型注册表
CREATE TABLE IF NOT EXISTS `churn_model_registry` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `model_code` VARCHAR(64) NOT NULL,
  `model_name` VARCHAR(128) NOT NULL,
  `model_type` VARCHAR(32) DEFAULT 'CHURN_CLASSIFICATION',
  `algorithm` VARCHAR(32) DEFAULT 'LR_TREE' COMMENT 'LR/LOGISTIC_REGRESSION/GBDT/RANDOM_FOREST',

  `feature_window_days` INT DEFAULT 180,
  `churn_horizon_days` INT DEFAULT 90 COMMENT '流失定义窗口',
  `churn_threshold` INT DEFAULT 90 COMMENT '超过N天不来=流失',
  `training_samples` INT DEFAULT 0,
  `positive_samples` INT DEFAULT 0 COMMENT '流失样本数',
  `negative_samples` INT DEFAULT 0 COMMENT '留存样本数',

  -- 评估指标
  `train_auc` DECIMAL(10,4) DEFAULT NULL,
  `train_logloss` DECIMAL(10,4) DEFAULT NULL,
  `train_accuracy` DECIMAL(10,4) DEFAULT NULL,
  `train_precision` DECIMAL(10,4) DEFAULT NULL,
  `train_recall` DECIMAL(10,4) DEFAULT NULL,
  `train_f1` DECIMAL(10,4) DEFAULT NULL,
  `val_auc` DECIMAL(10,4) DEFAULT NULL,
  `val_logloss` DECIMAL(10,4) DEFAULT NULL,
  `val_accuracy` DECIMAL(10,4) DEFAULT NULL,
  `val_precision` DECIMAL(10,4) DEFAULT NULL,
  `val_recall` DECIMAL(10,4) DEFAULT NULL,
  `val_f1` DECIMAL(10,4) DEFAULT NULL,

  -- 特征重要性
  `feature_importance_json` TEXT,

  -- 系数（Logistic Regression）
  `lr_coefficients_json` TEXT COMMENT 'Logistic Regression 系数 {feature: coef}',

  `is_active` TINYINT DEFAULT 0,
  `status` TINYINT DEFAULT 1 COMMENT '1=草稿 2=已训练 3=已部署 4=已下线',
  `trained_at` DATETIME DEFAULT NULL,
  `deployed_at` DATETIME DEFAULT NULL,
  `version` VARCHAR(16) DEFAULT 'v1',

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX `idx_tenant_status` (`tenant_id`, `status`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Churn 模型注册表';

-- Churn 训练运行日志
CREATE TABLE IF NOT EXISTS `churn_training_run_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `model_id` BIGINT DEFAULT NULL,
  `run_type` VARCHAR(32) DEFAULT 'TRAIN',
  `step` VARCHAR(32) NOT NULL,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Churn 训练日志';
