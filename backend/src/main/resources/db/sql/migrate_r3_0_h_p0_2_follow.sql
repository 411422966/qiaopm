-- ============================================
-- R3.0-H P0.2 回访补强
--  1. crm_follow_up_touch（触达日志）
--  2. crm_follow_up_note（任务备注/操作日志）
--  3. crm_follow_up_closure_view（统计视图）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS crm_follow_up_touch;
CREATE TABLE crm_follow_up_touch (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    task_id         BIGINT       NOT NULL,
    touch_type      VARCHAR(32)  NOT NULL COMMENT 'PHONE/SMS/WECHAT/WX_WORK/EMAIL/FACE',
    touch_status    VARCHAR(16)  DEFAULT 'SENT' COMMENT 'SENT/REPLIED/FAILED/NO_ANSWER',
    contact         VARCHAR(128) DEFAULT NULL,
    content         TEXT         DEFAULT NULL,
    reply_content   TEXT         DEFAULT NULL,
    reply_at        DATETIME     DEFAULT NULL,
    duration_seconds INT         DEFAULT NULL COMMENT '通话时长（秒）',
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    touched_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task (task_id, touched_at),
    INDEX idx_type (touch_type, touch_status, touched_at),
    INDEX idx_member_status (operator_id, touch_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回访触达日志';

DROP TABLE IF EXISTS crm_follow_up_note;
CREATE TABLE crm_follow_up_note (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    task_id         BIGINT       NOT NULL,
    action_type     VARCHAR(32)  NOT NULL COMMENT 'CREATE/ASSIGN/START/FINISH/CLOSE/REOPEN/EDIT',
    from_status     INT          DEFAULT NULL,
    to_status       INT          DEFAULT NULL,
    content         VARCHAR(1000) DEFAULT NULL,
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task (task_id, create_time),
    INDEX idx_action (action_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回访操作日志';

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM crm_follow_up_touch) AS touch_count,
    (SELECT COUNT(*) FROM crm_follow_up_note) AS note_count,
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_name='crm_follow_up_task') AS task_table_exists;