-- ====================================================================
-- QPM-006 增补: RFM 批量执行历史日志
-- Migration: migrate_qpm_006_rfm_batch_log.sql
-- 说明：RFM 页面"批量 / 示例数据" tab 增加执行历史表格 + 删除示例能力
--       不替代异步任务系统，仅做轻量审计
-- ====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `rfm_batch_log`;
CREATE TABLE `rfm_batch_log` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     BIGINT          NOT NULL COMMENT '租户',
    `action`        VARCHAR(16)     NOT NULL COMMENT 'BATCH / SEED',
    `brand_id`      BIGINT          DEFAULT NULL COMMENT '品牌ID，可空',
    `total`         INT             NOT NULL DEFAULT 0 COMMENT '总数',
    `success`       INT             NOT NULL DEFAULT 0 COMMENT '成功',
    `fail`          INT             NOT NULL DEFAULT 0 COMMENT '失败',
    `duration_ms`   INT             NOT NULL DEFAULT 0 COMMENT '耗时(ms)',
    `operator_name` VARCHAR(64)     DEFAULT NULL COMMENT '操作人',
    `remark`        VARCHAR(255)    DEFAULT NULL COMMENT '备注/失败样例',
    `create_time`   DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_create` (`tenant_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='RFM 批量执行日志';

SET FOREIGN_KEY_CHECKS = 1;