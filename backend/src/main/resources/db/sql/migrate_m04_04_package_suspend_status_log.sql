-- ===================================================================
-- M04-04 套餐转让/冻结/挂起 — 状态日志表 + 挂起/恢复字段
-- ===================================================================
--
-- 适用版本：基于现有 crm_package_order（含 version 乐观锁）
-- 兼容 MySQL 5.7+ / 8.0+
--
-- 变更：
--   1. 新增 crm_package_status_log 表（冻结/解冻/挂起/恢复 审计日志）
--   2. crm_package_order 扩展状态枚举（status=8=已挂起，注释更新）
--   3. 不修改已有字段，幂等可重入
--
-- 使用：
--   SOURCE /your/sql/path/migrate_m04_04_package_suspend_status_log.sql;
--
-- 回滚：
--   DROP TABLE IF EXISTS crm_package_status_log;
--   -- status=8 数据回滚：UPDATE crm_package_order SET status=1 WHERE status=8;

SET NAMES utf8mb4;

-- -------------------------------------------------------------------
-- 1. 套餐状态变更审计日志表
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS `crm_package_status_log`;
CREATE TABLE `crm_package_status_log` (
    `id`              BIGINT       AUTO_INCREMENT PRIMARY KEY                              COMMENT '主键',
    `tenant_id`       BIGINT       DEFAULT 1                                              COMMENT '租户ID',
    `order_id`        BIGINT       NOT NULL                                               COMMENT '套餐订单ID',
    `order_no`        VARCHAR(32)  DEFAULT NULL                                           COMMENT '订单号(冗余)',
    `member_id`       BIGINT       DEFAULT NULL                                           COMMENT '会员ID',
    `package_id`      BIGINT       DEFAULT NULL                                           COMMENT '套餐ID',
    `action`          VARCHAR(16)  NOT NULL                                               COMMENT '动作:FREEZE/UNFREEZE/SUSPEND/RESUME',
    `before_status`   TINYINT      DEFAULT NULL                                           COMMENT '变更前状态',
    `after_status`    TINYINT      NOT NULL                                               COMMENT '变更后状态',
    `reason`          VARCHAR(500) DEFAULT NULL                                           COMMENT '原因/备注',
    `suspend_until`   DATETIME     DEFAULT NULL                                           COMMENT '挂起到期时间(NULL=永久挂起)',
    `operator_id`     BIGINT       DEFAULT NULL                                           COMMENT '操作人ID',
    `operator_name`   VARCHAR(64)  DEFAULT NULL                                           COMMENT '操作人姓名',
    `client_ip`       VARCHAR(64)  DEFAULT NULL                                           COMMENT '客户端IP',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP                              COMMENT '创建时间',
    KEY `idx_order` (`order_id`),
    KEY `idx_member` (`member_id`),
    KEY `idx_action` (`action`),
    KEY `idx_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐状态变更审计日志';

-- -------------------------------------------------------------------
-- 2. crm_package_order 状态枚举扩展（不改 schema，仅注释）
--    status:1=正常 2=已用完 3=已过期 4=已冻结 5=已退款 6=已转赠 7=已升级 8=已挂起
-- -------------------------------------------------------------------

-- 验证表结构
SELECT '=== M04-04 migration completed ===' AS info;
SELECT 'crm_package_status_log created' AS step;
SHOW COLUMNS FROM crm_package_status_log;

SELECT '=== Existing data sanity check ===' AS step;
SELECT
    status,
    CASE status
        WHEN 1 THEN '正常'
        WHEN 2 THEN '已用完'
        WHEN 3 THEN '已过期'
        WHEN 4 THEN '已冻结'
        WHEN 5 THEN '已退款'
        WHEN 6 THEN '已转赠'
        WHEN 7 THEN '已升级'
        ELSE '其他/待扩展'
    END AS status_label,
    COUNT(*) AS cnt
FROM crm_package_order
GROUP BY status
ORDER BY status;
