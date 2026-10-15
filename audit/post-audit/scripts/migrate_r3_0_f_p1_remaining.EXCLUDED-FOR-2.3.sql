-- ============================================
-- Phase 2.3 暂不 apply 的剔除段（来源：migrate_r3_0_f_p1_remaining.sql）
-- ============================================
-- 来源脚本 : backend/src/main/resources/db/sql/migrate_r3_0_f_p1_remaining.sql
-- 原始行号 : §2 schedule_suggestion（DROP+CREATE）→ L62-80
--          + §3 biz_forecast_demand（DROP+CREATE）→ L82-100
--          + §4 crm_recommend_log（DROP+CREATE）→ L102-122
--          + 末尾 sys_permission 3 条 + sys_role_permission 1 组 INSERT → L124-146
-- 暂不 apply 原因 : 严格遵守 Phase 2.3 范围，仅保留 mkt_dispatch + mkt_dispatch_target
-- 后续决策入口 : 待 Phase 2.4 / 后续批次单独评审，决定是否补 apply
-- 对应子集脚本 : audit/post-audit/scripts/migrate_mkt_dispatch_subset.sql
-- 文件用途 : 仅作为「完整原始内容存档」+「后续批次评审参考」
--          **严禁**在 Phase 2.3 中 source 此文件
-- ============================================

-- ============================================
-- 2. 智能排班：建议结果（schedule_suggestion）— 暂不 apply
-- ============================================
DROP TABLE IF EXISTS schedule_suggestion;
CREATE TABLE schedule_suggestion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    store_id        BIGINT       NOT NULL,
    work_date       DATE         NOT NULL,
    employee_id     BIGINT       NOT NULL,
    employee_name   VARCHAR(64)  DEFAULT NULL,
    shift_code      VARCHAR(16)  DEFAULT 'DAY' COMMENT 'DAY/MIDDLE/NIGHT',
    start_time      TIME         DEFAULT NULL,
    end_time        TIME         DEFAULT NULL,
    expected_load   INT          DEFAULT 0 COMMENT '预测当班客流 0-100',
    confidence      DECIMAL(5,2) DEFAULT 0.00 COMMENT '0-1',
    reason          VARCHAR(500) DEFAULT NULL COMMENT '推荐理由（JSON）',
    status          VARCHAR(16)  DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/EXPIRED',
    accepted_at     DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    INDEX idx_store_date (store_id, work_date, status),
    INDEX idx_employee (employee_id, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 排班建议';

-- ============================================
-- 3. 需求预测（biz_forecast_demand）— 暂不 apply
-- ============================================
DROP TABLE IF EXISTS biz_forecast_demand;
CREATE TABLE biz_forecast_demand (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    store_id        BIGINT       NOT NULL,
    forecast_date   DATE         NOT NULL,
    forecast_type   VARCHAR(32)  DEFAULT 'BOOKING' COMMENT 'BOOKING/ORDER/STOCK',
    target_id       BIGINT       DEFAULT NULL COMMENT '服务/商品/资源 ID',
    target_name     VARCHAR(128) DEFAULT NULL,
    predicted_value DECIMAL(18,2) DEFAULT 0,
    actual_value    DECIMAL(18,2) DEFAULT 0,
    error_rate      DECIMAL(5,2) DEFAULT 0,
    model_version   VARCHAR(32)  DEFAULT 'BASE-1',
    confidence      DECIMAL(5,2) DEFAULT 0.00,
    operator_name   VARCHAR(64)  DEFAULT 'system',
    forecast_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_store_date (store_id, forecast_date, forecast_type),
    INDEX idx_target (target_id, forecast_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求预测';

-- ============================================
-- 4. 推荐算法日志（crm_recommend_log）— 暂不 apply
-- ============================================
DROP TABLE IF EXISTS crm_recommend_log;
CREATE TABLE crm_recommend_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    member_id       BIGINT       NOT NULL,
    recommend_type  VARCHAR(32)  NOT NULL COMMENT 'COUPON/PACKAGE/SERVICE/PUNCH/IMAGE',
    target_id       BIGINT       DEFAULT NULL,
    target_name     VARCHAR(255) DEFAULT NULL,
    score           DECIMAL(5,4) DEFAULT 0,
    reason          VARCHAR(500) DEFAULT NULL,
    channel         VARCHAR(32)  DEFAULT 'INTERNAL',
    shown           TINYINT      DEFAULT 0,
    clicked         TINYINT      DEFAULT 0,
    converted       TINYINT      DEFAULT 0,
    exposed_at      DATETIME     DEFAULT NULL,
    clicked_at      DATETIME     DEFAULT NULL,
    converted_at    DATETIME     DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member (member_id, recommend_type, create_time),
    INDEX idx_type (recommend_type, converted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐结果日志';

-- ============================================
-- 权限码 INSERT（mkt:dispatch / intel:forecast / intel:recommend）— 暂不 apply
-- ============================================
-- 注：mkt:dispatch 与本次保留的 mkt_dispatch / mkt_dispatch_target 表相关，但
--     业务侧尚未启用菜单入口（前端 dispatch tab 未上线），故一并暂不 apply。
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'mkt:dispatch', '总部下发', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='mkt:channel') t LIMIT 1), 90;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'intel:forecast', '需求预测', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='intel:ltv') t LIMIT 1), 50;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'intel:recommend', '智能推荐', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='intel:ltv') t LIMIT 1), 51;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN','BRAND_ADMIN','HQ_ADMIN')
  AND p.permission_code IN ('mkt:dispatch','intel:forecast','intel:recommend');
