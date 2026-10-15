-- ============================================================
-- COMP-003 并发症 SOP 两张表 (P3 验证补漏 / 2026-08-27)
-- 说明:
--   - 与以下 2 个实体严格对齐 (@TableName 值):
--       com.meiyetec.entity.ComplicationSop   -> complication_sop
--       com.meiyetec.entity.ComplicationEvent -> complication_event
--   - 这是 P3 验证发现的"链路断裂"项: 实体/Mapper/Controller(@RestController Service,
--     ComplicationService @RequestMapping("/complication")) 全在,
--     但 backend/ 下完全没有这两张表的 DDL。
--   - SOP = Standard Operating Procedure 标准作业流程, 一条记录 = 一份应对某类并发症的预案。
--   - Event = 实际触发的并发症事件记录, 关联到 SOP + 会员 + 订单, 跟踪处置过程。
-- 关联表:
--   - complication_sop.tenant_id       -> sys_tenant.id
--   - complication_event.sop_id        -> complication_sop.id
--   - complication_event.member_id     -> member.id
--   - complication_event.order_id      -> pos_order.id
--   - complication_event.service_id    -> biz_service.id
--   - complication_event.handler_id    -> employee.id
-- ============================================================

-- ============================================================
-- 1. SOP 预案主表 complication_sop
-- ============================================================
CREATE TABLE IF NOT EXISTS complication_sop (
  id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
  tenant_id              BIGINT       NULL     COMMENT '所属租户ID',
  sop_code               VARCHAR(64)  NULL     COMMENT 'SOP 编码 (业务唯一, e.g. SKIN_REDNESS_FACIAL)',
  sop_name               VARCHAR(128) NULL     COMMENT 'SOP 名称',
  category               VARCHAR(32)  NULL     COMMENT '分类 SKIN_HAIR_NAIL_BODY_GENERAL',
  severity               VARCHAR(16)  NULL     COMMENT '严重度 LOW/MEDIUM/HIGH/CRITICAL',
  trigger_symptom        VARCHAR(255) NULL     COMMENT '触发症状描述',
  trigger_timing         VARCHAR(64)  NULL     COMMENT '触发时机 DURING/IMMEDIATELY_AFTER/HOURS_AFTER/DAYS_AFTER',
  steps_json             TEXT         NULL     COMMENT '处置步骤 (JSON 数组, [{order, action, expectMin, note}])',
  medications            VARCHAR(1024) NULL    COMMENT '推荐用药 / 护理品 (逗号分隔或 JSON)',
  equipment              VARCHAR(512) NULL     COMMENT '所需设备',
  escalation_rule        VARCHAR(512) NULL     COMMENT '升级规则 (触发条件 + 升级路径)',
  response_deadline_min  INT          NULL     DEFAULT 0 COMMENT '响应时限(分钟)',
  responsible_role       VARCHAR(32)  NULL     COMMENT '责任角色 THERAPIST/DOCTOR/MANAGER/CLINIC',
  version                VARCHAR(16)  NULL     DEFAULT 'v1.0' COMMENT '版本号',
  status                 VARCHAR(16)  NULL     DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DRAFT/DEPRECATED',
  created_at             DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cs_tenant (tenant_id),
  KEY idx_cs_code (sop_code),
  KEY idx_cs_category (category),
  KEY idx_cs_severity (severity),
  KEY idx_cs_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='并发症 SOP 预案 (ComplicationSop)';

-- ============================================================
-- 2. 并发症事件处置记录 complication_event
-- ============================================================
CREATE TABLE IF NOT EXISTS complication_event (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
  tenant_id       BIGINT       NULL     COMMENT '所属租户ID',
  event_no        VARCHAR(64)  NULL     COMMENT '事件单号 (业务唯一)',
  sop_id          BIGINT       NULL     COMMENT '触发 SOP -> complication_sop.id',
  sop_code        VARCHAR(64)  NULL     COMMENT 'SOP 编码 (冗余, 便于查询)',
  member_id       BIGINT       NULL     COMMENT '涉事会员ID -> member.id',
  order_id        BIGINT       NULL     COMMENT '关联订单ID -> pos_order.id',
  service_id      BIGINT       NULL     COMMENT '关联服务ID -> biz_service.id',

  severity        VARCHAR(16)  NULL     COMMENT '实际严重度 LOW/MEDIUM/HIGH/CRITICAL',
  symptoms        VARCHAR(1024) NULL    COMMENT '实际症状描述',
  trigger_time    DATETIME     NULL     COMMENT '触发时间',
  response_time   DATETIME     NULL     COMMENT '首次响应时间',
  resolved_time   DATETIME     NULL     COMMENT '处置完成时间',
  handler_id      BIGINT       NULL     COMMENT '处置人ID -> employee.id',
  handler_name    VARCHAR(64)  NULL     COMMENT '处置人姓名 (冗余)',
  status          VARCHAR(16)  NULL     DEFAULT 'OPEN' COMMENT 'OPEN/RESPONDED/RESOLVED/ESCALATED/CLOSED',
  execution_log   TEXT         NULL     COMMENT '执行日志 (JSON 或 Markdown)',
  outcome         VARCHAR(512) NULL     COMMENT '处置结果描述',

  created_at      DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ce_tenant (tenant_id),
  KEY idx_ce_event_no (event_no),
  KEY idx_ce_sop (sop_id),
  KEY idx_ce_member (member_id),
  KEY idx_ce_order (order_id),
  KEY idx_ce_status (status),
  KEY idx_ce_severity (severity),
  KEY idx_ce_trigger_time (trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='并发症事件处置记录 (ComplicationEvent)';