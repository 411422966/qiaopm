-- ============================================
-- R3.0-H P1.4 服务 SOP 步骤化补强
--  1. biz_treatment_phase_step（阶段内步骤，颗粒度比 phase 更细）
--  2. step_group 用于实现"并行/串行"控制
--  3. member_treatment_step_log（会员疗程步骤执行轨迹）
-- 2026-08-14
-- ============================================

DROP TABLE IF EXISTS biz_treatment_phase_step;
CREATE TABLE biz_treatment_phase_step (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    phase_id        BIGINT       NOT NULL COMMENT '所属阶段',
    plan_id         BIGINT       NOT NULL COMMENT '冗余 plan_id 便于查询',
    step_no         INT          NOT NULL DEFAULT 1 COMMENT '步骤序号（同一 group 内或全局）',
    step_group      VARCHAR(64)  DEFAULT 'G1' COMMENT '步骤分组：并行步骤相同 group；串行各 step 自成一组 G1/G2/G3...',
    step_type       VARCHAR(16)  DEFAULT 'SERIAL' COMMENT 'SERIAL=串行 / PARALLEL=并行（实际并行性由 group 决定，type 仅语义标签）',
    step_name       VARCHAR(128) NOT NULL COMMENT '步骤名（如：清洁 / 热敷 / 仪器导入 / 面膜 / 头肩按摩）',
    duration_minutes INT         DEFAULT 10 COMMENT '单次执行时长（分钟）',
    product_id      BIGINT       DEFAULT NULL COMMENT '关联耗材/产品（可选）',
    product_qty     DECIMAL(10,2) DEFAULT NULL COMMENT '单次耗材用量',
    tool_required   VARCHAR(128) DEFAULT NULL COMMENT '所需工具（如：热喷机 / 超声波 / 射频仪）',
    recipe_text     TEXT         DEFAULT NULL COMMENT '配方/操作要点（如：精油配比、温度、力度、节奏）',
    remark          VARCHAR(500) DEFAULT NULL,
    sort_order      INT          DEFAULT 0,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      DEFAULT 0,
    INDEX idx_phase (phase_id, sort_order),
    INDEX idx_plan (plan_id),
    INDEX idx_group (phase_id, step_group, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='疗程阶段步骤（含并行/串行）';

-- 会员执行步骤日志（每次到店做哪几步、用什么产品、用了多久）
DROP TABLE IF EXISTS member_treatment_step_log;
CREATE TABLE member_treatment_step_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    progress_id     BIGINT       NOT NULL COMMENT '关联 member_treatment_progress',
    session_id      BIGINT       DEFAULT NULL COMMENT '关联 member_treatment_session（可选）',
    step_id         BIGINT       NOT NULL COMMENT '关联 biz_treatment_phase_step',
    phase_id        BIGINT       NOT NULL,
    plan_id         BIGINT       NOT NULL,
    member_id       BIGINT       NOT NULL,
    step_name       VARCHAR(128) DEFAULT NULL,
    step_group      VARCHAR(64)  DEFAULT NULL,
    duration_minutes INT         DEFAULT 0 COMMENT '本次实际执行时长',
    product_id      BIGINT       DEFAULT NULL,
    product_qty     DECIMAL(10,2) DEFAULT NULL,
    operator_id     BIGINT       DEFAULT NULL,
    operator_name   VARCHAR(64)  DEFAULT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    started_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    finished_at     DATETIME     DEFAULT NULL,
    status          INT          DEFAULT 1 COMMENT '1=执行中 / 2=已完成 / 3=跳过',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_progress (progress_id, status),
    INDEX idx_session (session_id),
    INDEX idx_step (step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员疗程步骤执行日志';

-- ============================================
-- 验证
-- ============================================
SELECT
    (SELECT COUNT(*) FROM biz_treatment_phase_step) AS step_count,
    (SELECT COUNT(*) FROM member_treatment_step_log) AS log_count,
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_name='biz_treatment_phase_step') AS step_table_exists;