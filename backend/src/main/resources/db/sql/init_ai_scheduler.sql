-- ============================================================
-- AI-001 AI 智能排班表 (P3 验证补漏 / 2026-08-27)
-- 说明:
--   - 与实体 com.meiyetec.entity.AiSchedulePlan (@TableName("ai_schedule_plan")) 对齐
--   - 这是 P3 验证发现的"链路断裂"项: 实体/Mapper/Controller(@RestController Service,
--     AiSchedulerService @RequestMapping("/ai-scheduler")) 全在,
--     但 backend/ 下完全没有 CREATE TABLE ai_schedule_plan 的 DDL。
--   - 一条记录 = AI 算法针对"某门店某一周"生成的一份排班方案。
--   - planJson 存完整排班结果 (按天×员工×班次的二维结构, JSON 数组),
--     与实体 String 字段一致; 业务侧自行序列化/反序列化。
-- 关联表:
--   - ai_schedule_plan.tenant_id -> sys_tenant.id
--   - ai_schedule_plan.store_id  -> store.id
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_schedule_plan (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
  tenant_id         BIGINT       NULL     COMMENT '所属租户ID',
  store_id          BIGINT       NULL     COMMENT '所属门店ID',
  week_start        DATE         NULL     COMMENT '排班周起始日 (周一)',
  staff_count       INT          NULL     DEFAULT 0 COMMENT '当周参与员工数',
  expected_bookings INT          NULL     DEFAULT 0 COMMENT '当周预计预约量',
  plan_json         TEXT         NULL     COMMENT '完整排班方案 (JSON, [{date, employeeId, shift, slots}])',
  status            VARCHAR(16)  NULL     DEFAULT 'GENERATED' COMMENT 'GENERATED/ADJUSTED/APPLIED/EXPIRED',
  created_at        DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_asp_tenant (tenant_id),
  KEY idx_asp_store (store_id),
  KEY idx_asp_week (week_start),
  KEY idx_asp_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI 智能排班方案 (AiSchedulePlan)';