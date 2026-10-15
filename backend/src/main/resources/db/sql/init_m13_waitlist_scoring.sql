-- =============================================================================
-- M13 候补智能补位评分算法 (Booking Waitlist Smart-Fill Scoring)
-- 创建日期: 2026-09-05
-- 关联模块: M01 预约 / M13 AI 排班
-- 关联实体: booking_waitlist / booking_waitlist_notify
-- 设计目标:
--   1. 5 维度评分（期望匹配/会员价值/爽约风险/等待时长/响应率）
--   2. 评分权重按租户可配置
--   3. 每次评分写日志（explainable AI）
--   4. 在 booking_waitlist 上落最后一次评分（前端可读）
-- =============================================================================

-- 1. 候补评分日志（每次评分都留痕，方便回溯/审计/调参）
DROP TABLE IF EXISTS booking_waitlist_score_log;
CREATE TABLE booking_waitlist_score_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    store_id BIGINT NOT NULL DEFAULT 1,
    waitlist_id BIGINT NOT NULL COMMENT '候补 ID',
    member_id BIGINT NOT NULL COMMENT '会员 ID',
    service_item_id BIGINT COMMENT '项目 ID',
    preferred_date DATE COMMENT '期望日期',
    total_score DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '综合得分（0-100）',
    breakdown_json JSON COMMENT '各维度明细（dim=score）',
    ranking INT COMMENT '本次排序名次',
    trigger_source VARCHAR(32) NOT NULL DEFAULT 'AUTO_FILL' COMMENT '触发来源：AUTO_FILL / PREVIEW / BATCH',
    decision VARCHAR(16) NOT NULL DEFAULT 'NOTIFIED' COMMENT '处置：NOTIFIED/SKIPPED/EXPIRED',
    reason VARCHAR(255) COMMENT '跳过/通知原因',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_waitlist (tenant_id, waitlist_id),
    INDEX idx_member (member_id),
    INDEX idx_service (service_item_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候补评分日志（explainable AI 审计）';

-- 2. 评分权重配置（按租户 + 门店；未配置 → 用系统默认权重）
DROP TABLE IF EXISTS booking_waitlist_score_config;
CREATE TABLE booking_waitlist_score_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    store_id BIGINT NOT NULL DEFAULT 1 COMMENT '0=租户级',
    weight_preference DECIMAL(4,2) NOT NULL DEFAULT 0.30 COMMENT '期望匹配度权重',
    weight_value DECIMAL(4,2) NOT NULL DEFAULT 0.25 COMMENT '会员价值权重',
    weight_no_show DECIMAL(4,2) NOT NULL DEFAULT 0.20 COMMENT '爽约风险权重（反向）',
    weight_wait_time DECIMAL(4,2) NOT NULL DEFAULT 0.15 COMMENT '等待时长权重',
    weight_response DECIMAL(4,2) NOT NULL DEFAULT 0.10 COMMENT '响应率权重',
    -- 候补阈值与策略
    min_score_to_notify DECIMAL(5,2) NOT NULL DEFAULT 30.00 COMMENT '通知最低分（<则跳过）',
    max_notify_per_round INT NOT NULL DEFAULT 5 COMMENT '单轮最多通知人数',
    cooldown_minutes INT NOT NULL DEFAULT 60 COMMENT '同一候补两次通知最小间隔（分钟）',
    wait_time_cap_hours INT NOT NULL DEFAULT 8 COMMENT '等待时长封顶小时',
    -- 业务策略
    locked_member_action VARCHAR(16) NOT NULL DEFAULT 'SKIP' COMMENT '锁定会员处置：SKIP/NOTIFY',
    expired_action VARCHAR(16) NOT NULL DEFAULT 'SKIP' COMMENT '过期候补处置：SKIP/NOTIFY',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '启用评分（0=回退到老的 FIFO+priority 排序）',
    operator_name VARCHAR(64),
    remark VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_store (tenant_id, store_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候补评分权重配置';

-- 3. 在 booking_waitlist 上落最后一次评分（前端可读，无需关联 score_log）
-- 用 ALTER + IF NOT EXISTS 风格（MySQL 8.0.29+ 支持）
ALTER TABLE booking_waitlist
    ADD COLUMN IF NOT EXISTS last_score DECIMAL(6,2) DEFAULT NULL COMMENT '最后一次评分',
    ADD COLUMN IF NOT EXISTS last_score_at DATETIME DEFAULT NULL COMMENT '最后一次评分时间',
    ADD COLUMN IF NOT EXISTS last_score_json JSON DEFAULT NULL COMMENT '最后一次评分明细';

-- 4. 默认配置（租户 1 / 门店 1）
INSERT IGNORE INTO booking_waitlist_score_config
    (tenant_id, store_id, weight_preference, weight_value, weight_no_show,
     weight_wait_time, weight_response, min_score_to_notify, max_notify_per_round,
     cooldown_minutes, wait_time_cap_hours, locked_member_action, expired_action, enabled,
     operator_name, remark)
VALUES
    (1, 1, 0.30, 0.25, 0.20, 0.15, 0.10, 30.00, 5, 60, 8, 'SKIP', 'SKIP', 1, 'system',
     '默认配置：期望匹配 30% + 会员价值 25% + 爽约风险 20% + 等待时长 15% + 响应率 10%');

-- 5. RBAC 权限（候补评分/预览）
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'biz:booking:waitlist-score', '候补智能补位评分', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='biz:booking:waitlist') t LIMIT 1),
       10;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN')
  AND p.permission_code = 'biz:booking:waitlist-score';

-- 6. 验证
SELECT 'booking_waitlist_score_log' tbl, COUNT(*) cnt FROM booking_waitlist_score_log
UNION ALL SELECT 'booking_waitlist_score_config', COUNT(*) FROM booking_waitlist_score_config;