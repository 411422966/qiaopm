-- ====================================================================
-- QPM-007: RFM 批量任务实时进度表
-- Migration: migrate_qpm_007_rfm_batch_task.sql
-- 说明：
--   * 配合 RfmBatchExecutor.createTask/runBatch/runSeed 使用
--   * 区别于 qpm_006_rfm_batch_log（那是审计历史，写一行/次）
--   * 本表用于：
--       - 主线程 INSERT (status='RUNNING') → 立即返回 taskId 给前端
--       - 异步线程每 50 条 UPDATE processed/success/fail（Q4）
--       - 取消时 UPDATE status='CANCELLED'，executor 每条循环检测
--       - 前端轮询 GET /batch/progress/{taskId} 拿实时进度
-- ====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `rfm_batch_task`;
CREATE TABLE `rfm_batch_task` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     BIGINT          NOT NULL COMMENT '租户',
    `action`        VARCHAR(16)     NOT NULL COMMENT 'BATCH / SEED',
    `brand_id`      BIGINT          DEFAULT NULL COMMENT '品牌ID，可空',
    `operator_name` VARCHAR(64)     DEFAULT NULL COMMENT '操作人',

    `status`        VARCHAR(16)     NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING / DONE / FAILED / CANCELLED',
    `total`         INT             NOT NULL DEFAULT 0 COMMENT '计划处理总数',
    `processed`     INT             NOT NULL DEFAULT 0 COMMENT '已处理数',
    `success`       INT             NOT NULL DEFAULT 0 COMMENT '成功数',
    `fail`          INT             NOT NULL DEFAULT 0 COMMENT '失败数',

    `start_time`    DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    `end_time`      DATETIME        DEFAULT NULL COMMENT '结束时间',
    `remark`        VARCHAR(255)    DEFAULT NULL COMMENT '失败样例 / 备注',

    PRIMARY KEY (`id`),
    KEY `idx_tenant_status_start` (`tenant_id`, `status`, `start_time`),
    KEY `idx_tenant_action_start` (`tenant_id`, `action`, `start_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='RFM 批量任务实时进度';

SET FOREIGN_KEY_CHECKS = 1;
