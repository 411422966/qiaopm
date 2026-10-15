-- =============================================================================
-- M06 — 疗程效果报告（TreatmentReport）初始化脚本
-- 用途：持久化"疗程效果报告"快照，是美容行业决策依据的核心数据源
--      （PRD_FEATURE_VERIFICATION_REPORT § M06 缺口）
-- 数据库版本：MySQL 8.0+ 推荐（< 8.0.29 需先 SHOW COLUMNS 检查列存在再 ALTER）
-- =============================================================================

CREATE TABLE IF NOT EXISTS treatment_report (
    id                        BIGINT          NOT NULL AUTO_INCREMENT          COMMENT '主键',
    tenant_id                 BIGINT          NOT NULL                        COMMENT '租户隔离键（多租户）',
    member_id                 BIGINT          NOT NULL                        COMMENT '会员 ID',
    treatment_plan_id         BIGINT          NOT NULL                        COMMENT '疗程方案 ID（biz_treatment_plan.id）',
    plan_name                 VARCHAR(255)    DEFAULT NULL                    COMMENT '方案名称快照',
    total_sessions            INT             NOT NULL DEFAULT 0               COMMENT '方案规定总次数',
    completed_sessions        INT             NOT NULL DEFAULT 0               COMMENT '已完成 session 数',
    abandoned_sessions        INT             NOT NULL DEFAULT 0               COMMENT '已废弃 progress 数',
    ongoing_sessions          INT             NOT NULL DEFAULT 0               COMMENT '进行中 = total - completed - abandoned',
    session_ids_json          TEXT            DEFAULT NULL                    COMMENT '已完成 session id 列表 JSON',
    phases_summary_json       TEXT            DEFAULT NULL                    COMMENT '阶段汇总 JSON',
    care_suggestions_total    INT             NOT NULL DEFAULT 0               COMMENT '报告期内护理建议总数',
    care_suggestions_followed INT             NOT NULL DEFAULT 0               COMMENT '采纳（accepted）数',
    care_compliance_rate      DECIMAL(5,4)    DEFAULT NULL                    COMMENT '护理遵守率 0~1',
    effect_score_before       DECIMAL(6,2)    DEFAULT NULL                    COMMENT '疗程前肌肤综合分（0~100）',
    effect_score_after        DECIMAL(6,2)    DEFAULT NULL                    COMMENT '疗程后肌肤综合分',
    effect_score_delta        DECIMAL(6,2)    DEFAULT NULL                    COMMENT '差值 = after - before',
    recommendation            TEXT            DEFAULT NULL                    COMMENT '启发式生成的文本建议（CJK）',
    generated_by              VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM'       COMMENT '生成者',
    generated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    remark                    VARCHAR(255)    DEFAULT NULL                    COMMENT '运营备注',
    create_time               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审计',
    update_time               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '审计',
    deleted                   TINYINT         NOT NULL DEFAULT 0               COMMENT '软删（0 正常 / 1 已删）',
    PRIMARY KEY (id),
    KEY idx_tenant_member       (tenant_id, member_id),
    KEY idx_tenant_plan         (tenant_id, treatment_plan_id),
    KEY idx_tenant_generated_at (tenant_id, generated_at),
    KEY idx_member_plan         (member_id, treatment_plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='疗程效果报告（M06）';

-- 兼容性检查（< 8.0.29 报错）：如果之前手建过本表但缺 remark 列，补一下
-- ALTER TABLE treatment_report ADD COLUMN IF NOT EXISTS remark VARCHAR(255) DEFAULT NULL;
