-- ============================================
-- P2-11 IND-05 双人核销建模 — 数据库迁移
-- 2026-09-07
-- 包含：
--   1. stock_dual_verify_record 表（双人核销记录）
--   2. stock_record.dual_verify_status 列（核销状态追踪）
--   3. sys_config 双人核销阈值配置（默认 1000 元）
-- 幂等：所有 ADD COLUMN / INSERT IGNORE 加了 IF NOT EXISTS 兜底
-- ============================================

-- 1. 双人核销记录主表
CREATE TABLE IF NOT EXISTS `stock_dual_verify_record` (
  `id`                   bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id`            bigint NOT NULL DEFAULT 1,
  `store_id`             bigint NOT NULL,
  `verify_type`         varchar(32)  NOT NULL COMMENT '核销类型: CHECK=盘点 OUTBOUND=出库 CONSUMPTION=消耗',
  `record_type`         varchar(32)  NOT NULL NULL COMMENT '对应 stock_record.type 含义: 3=盘点 4=领用 2=出库 5=报损',
  `stock_record_id`     bigint COMMENT '关联的 stock_record.id',
  `stock_check_id`      bigint COMMENT '关联的 stock_check.id（仅盘点场景）',
  `main_verifier_id`    bigint NOT NULL COMMENT '主核销人ID',
  `main_verifier_name`  varchar(64) NOT NULL COMMENT '主核销人姓名',
  `reviewer_id`         bigint COMMENT '复核人ID（NULL=待复核 PENDING）',
  `reviewer_name`       varchar(64) COMMENT '复核人姓名',
  `reviewer_gps`        varchar(255) COMMENT '复核人GPS/指纹JSON: {"lat":,"lng":,"fingerprint":,"ts":}',
  `review_time`         datetime COMMENT '复核时间',
  `verify_status`       varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING待复核 VERIFIED已复核 REJECTED已驳回',
  `total_amount`        decimal(14,2) COMMENT '核销金额（用于阈值判断）',
  `threshold`           decimal(14,2) COMMENT '当时阈值（快照）',
  `remark`              varchar(500),
  `create_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`             tinyint NOT NULL DEFAULT 0,
  INDEX `idx_dvr_tenant`      (`tenant_id`),
  INDEX `idx_dvr_store`       (`store_id`),
  INDEX `idx_dvr_record`      (`stock_record_id`),
  INDEX `idx_dvr_check`       (`stock_check_id`),
  INDEX `idx_dvr_status`      (`verify_status`),
  INDEX `idx_dvr_reviewer`    (`reviewer_id`)
) COMMENT='双人核销记录表';

-- 2. stock_record 增加核销状态追踪列
-- 出库/消耗完成后，高值操作被标记 PENDING，复核后更新为 VERIFIED/REJECTED
ALTER TABLE `stock_record`
  ADD COLUMN IF NOT EXISTS `dual_verify_status` varchar(16) DEFAULT 'NOT_REQUIRED' COMMENT '双人核销状态: NOT_REQUIRED无需 VERIFIED已复核 PENDING待复核 REJECTED已驳回' AFTER `remark`;

-- 3. stock_check 增加主核销人/复核人字段（盘点场景专用）
-- confirmed_by/confirmed_by_name/confirmed_time 保留不动（盘点提交人）
-- 主核销人 = 盘点主操作员（可能与 confirmed_by 相同或不同）
-- 复核人 = 双人复核中的复核人
ALTER TABLE `stock_check`
  ADD COLUMN IF NOT EXISTS `main_verifier_id`    bigint DEFAULT NULL COMMENT '主核销人ID' AFTER `confirmed_time`,
  ADD COLUMN IF NOT EXISTS `main_verifier_name` varchar(64) DEFAULT NULL COMMENT '主核销人姓名' AFTER `main_verifier_id`,
  ADD COLUMN IF NOT EXISTS `reviewer_id`        bigint DEFAULT NULL COMMENT '复核人ID' AFTER `main_verifier_name`,
  ADD COLUMN IF NOT EXISTS `reviewer_name`       varchar(64) DEFAULT NULL COMMENT '复核人姓名' AFTER `reviewer_id`,
  ADD COLUMN IF NOT EXISTS `reviewer_gps`        varchar(255) DEFAULT NULL COMMENT '复核人GPS JSON' AFTER `reviewer_name`,
  ADD COLUMN IF NOT EXISTS `review_time`         datetime DEFAULT NULL COMMENT '复核时间' AFTER `reviewer_gps`,
  ADD COLUMN IF NOT EXISTS `dual_verify_status`  varchar(16) DEFAULT 'NOT_REQUIRED' COMMENT '双人核销状态' AFTER `review_time`;

-- 4. sys_config 全局阈值配置（可按租户覆盖）
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `config_type`, `default_value`, `remark`, `create_time`)
VALUES ('stock_dual_verify_threshold', '1000', 'NUMBER', '1000',
        '双人核销阈值（元），total_amount > 此值时触发双人复核，默认1000', NOW());

-- 验证
SELECT 'stock_dual_verify_record' AS tbl, COUNT(*) AS cnt FROM stock_dual_verify_record
UNION ALL
SELECT 'stock_record.dual_verify_status col',
       COUNT(*) FROM information_schema.columns
       WHERE table_schema = DATABASE()
         AND table_name = 'stock_record'
         AND column_name = 'dual_verify_status'
UNION ALL
SELECT 'sys_config threshold',
       COUNT(*) FROM sys_config WHERE config_key = 'stock_dual_verify_threshold';
