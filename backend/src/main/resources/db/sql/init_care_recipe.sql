-- ============================================================
-- CARE 集群 6 张表 (P2 验证补漏 / 2026-08-27)
-- 说明:
--   - 与以下 6 个实体严格对齐 (@TableName 值):
--       com.meiyetec.entity.CareRecipe         -> care_recipe
--       com.meiyetec.entity.CareRecipeItem     -> care_recipe_item
--       com.meiyetec.entity.CareSkinAssessment -> care_skin_assessment
--       com.meiyetec.entity.CareFormula        -> care_formula
--       com.meiyetec.entity.CareFormulaApply   -> care_formula_apply
--       com.meiyetec.entity.CarePlan           -> care_plan
--   - 这 6 张表之前在 backend/src/main/resources/db/sql/ 下完全没有 DDL,
--     运行时调用 /care/recipe/*, /crm/care/suggestion/*, /care/assessment,
--     /care/recommend 等接口都会触发 "Table doesn't exist"。
--   - 字段类型与实体 Java 类型一一对应:
--     Long     -> BIGINT
--     String   -> VARCHAR/TEXT (按长度推断)
--     Integer  -> INT
--     BigDecimal-> DECIMAL(18,2) (金额)
--     LocalDate -> DATE
--     LocalDateTime -> DATETIME
--     JSON 列  -> TEXT (实体里是 String, 业务侧序列化 JSON)
--   - 主键 id 统一 BIGINT AUTO_INCREMENT, 与 MyBatis-Plus IdType.AUTO 一致。
--   - 本 DDL 不引入 deleted 逻辑删除列 (实体未声明 @TableLogic), 后续若需软删请补列。
-- 关联表:
--   - care_recipe_item.recipe_id        -> care_recipe.id
--   - care_skin_assessment.recommended_recipe_id -> care_recipe.id
--   - care_formula_apply.formula_id     -> care_formula.id
--   - care_formula_apply.booking_id     -> booking.id (M06 预约, 跨模块)
--   - care_formula_apply.service_id     -> biz_service.id (M05 服务, 跨模块)
--   - care_formula_apply.technician_id  -> employee.id (M02 员工, 跨模块)
--   - care_plan.member_id               -> member.id
-- ============================================================

-- ============================================================
-- 1. 护理配方主表 care_recipe
-- ============================================================
CREATE TABLE IF NOT EXISTS care_recipe (
  id                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK',
  tenant_id             BIGINT        NULL     COMMENT '所属租户ID',
  recipe_code           VARCHAR(64)   NULL     COMMENT '配方编码 (业务唯一)',
  recipe_name           VARCHAR(128)  NULL     COMMENT '配方名称',
  skin_type             VARCHAR(32)   NULL     COMMENT '适用肤质 ALL/DRY/OILY/MIXED/SENSITIVE/MATURE',
  concerns              VARCHAR(255)  NULL     COMMENT '针对问题 (逗号分隔, e.g. WHITENING,DULL)',
  description           VARCHAR(1024) NULL     COMMENT '配方简介',
  steps_json            TEXT          NULL     COMMENT '护理步骤 (JSON 数组)',
  session_count         INT           NULL     DEFAULT 0 COMMENT '建议次数',
  interval_days         INT           NULL     DEFAULT 0 COMMENT '每次间隔(天)',
  estimated_cost        DECIMAL(18,2) NULL     COMMENT '预估费用',
  expected_improvement  VARCHAR(512)  NULL     COMMENT '预期效果',
  cover_image           VARCHAR(512)  NULL     COMMENT '封面图 URL',
  tag                   VARCHAR(32)   NULL     COMMENT '标签 HOT/NEW/RECOMMEND/SEASONAL',
  applicable_season     VARCHAR(16)   NULL     COMMENT '适用季节 SPRING/SUMMER/AUTUMN/WINTER/ALL',
  gender                VARCHAR(16)   NULL     COMMENT '适用性别 MALE/FEMALE/ALL',
  age_min               INT           NULL     COMMENT '适用最小年龄',
  age_max               INT           NULL     COMMENT '适用最大年龄',
  score                 INT           NULL     DEFAULT 0 COMMENT '推荐评分 (0-100)',
  use_count             INT           NULL     DEFAULT 0 COMMENT '被使用次数',
  status                VARCHAR(16)   NULL     DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  created_at            DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_care_recipe_tenant (tenant_id),
  KEY idx_care_recipe_skin (skin_type),
  KEY idx_care_recipe_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='护理配方主表 (CareRecipe)';

-- ============================================================
-- 2. 配方步骤明细表 care_recipe_item
-- ============================================================
CREATE TABLE IF NOT EXISTS care_recipe_item (
  id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
  recipe_id           BIGINT       NULL     COMMENT '所属配方ID -> care_recipe.id',
  service_id          BIGINT       NULL     COMMENT '关联服务ID -> biz_service.id (可选)',
  item_type           VARCHAR(32)  NULL     COMMENT '类型 STEP/PRODUCT/NOTE',
  item_name           VARCHAR(128) NULL     COMMENT '步骤/产品名称',
  description         VARCHAR(512) NULL     COMMENT '说明',
  usage_qty           VARCHAR(64)  NULL     COMMENT '用量 (e.g. 5ml/2滴)',
  sequence            INT          NULL     DEFAULT 0 COMMENT '步骤顺序',
  estimated_minutes   INT          NULL     DEFAULT 0 COMMENT '预计耗时(分钟)',
  PRIMARY KEY (id),
  KEY idx_cri_recipe (recipe_id),
  KEY idx_cri_service (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='护理配方步骤明细 (CareRecipeItem)';

-- ============================================================
-- 3. 肤质评估表 care_skin_assessment
-- ============================================================
CREATE TABLE IF NOT EXISTS care_skin_assessment (
  id                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
  tenant_id                 BIGINT       NULL     COMMENT '所属租户ID',
  member_id                 BIGINT       NULL     COMMENT '会员ID -> member.id',
  assessment_no             VARCHAR(64)  NULL     COMMENT '评估单号 (业务唯一)',
  skin_type                 VARCHAR(32)  NULL     COMMENT '判定肤质',
  oil_level                 INT          NULL     COMMENT '油分等级 (1-5)',
  moisture_level            INT          NULL     COMMENT '水分等级 (1-5)',
  elasticity_level          INT          NULL     COMMENT '弹性等级 (1-5)',
  pigment_level             INT          NULL     COMMENT '色斑等级 (1-5)',
  pore_level                INT          NULL     COMMENT '毛孔等级 (1-5)',
  wrinkle_level             INT          NULL     COMMENT '皱纹等级 (1-5)',
  concerns_json             TEXT         NULL     COMMENT '问题列表 (JSON 数组)',
  skin_score                INT          NULL     COMMENT '综合肤质评分',
  recommended_recipe_id     BIGINT       NULL     COMMENT '推荐配方ID -> care_recipe.id (主推)',
  recommended_recipe_ids    VARCHAR(255) NULL     COMMENT '推荐配方ID列表 (逗号分隔, Top-N)',
  analysis_json             TEXT         NULL     COMMENT '完整分析结果 (JSON)',
  source                    VARCHAR(32)  NULL     COMMENT '来源 DEVICE/MANUAL/IMPORT',
  operator_id               BIGINT       NULL     COMMENT '操作人ID',
  assessment_date           DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '评估时间',
  next_assessment_date      DATETIME     NULL     COMMENT '建议下次评估时间',
  PRIMARY KEY (id),
  KEY idx_csa_tenant (tenant_id),
  KEY idx_csa_member (member_id),
  KEY idx_csa_no (assessment_no),
  KEY idx_csa_recipe (recommended_recipe_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='肤质评估记录 (CareSkinAssessment)';

-- ============================================================
-- 4. 护理配方(成分)表 care_formula
--   与 care_recipe 区分: recipe 是"面向客户的整套方案",
--   formula 是"技师用的成分/操作配方", 见 CareFormulaController.
-- ============================================================
CREATE TABLE IF NOT EXISTS care_formula (
  id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK',
  tenant_id           BIGINT        NULL     COMMENT '所属租户ID',
  formula_code        VARCHAR(64)   NULL     COMMENT '配方成分编码 (业务唯一)',
  formula_name        VARCHAR(128)  NULL     COMMENT '成分配方名称',
  formula_type        VARCHAR(32)   NULL     COMMENT 'SKIN/HAIR/NAIL/BODY',
  applicable_service  VARCHAR(64)   NULL     COMMENT '适用服务编码 (e.g. FACIAL_CLEAN)',
  skin_type           VARCHAR(32)   NULL     COMMENT '适用肤质',
  hair_type           VARCHAR(32)   NULL     COMMENT '适用发质',
  ingredients_json    TEXT          NULL     COMMENT '成分清单 (JSON 数组, [{name,ratio}])',
  steps_json          TEXT          NULL     COMMENT '操作步骤 (JSON 数组)',
  duration_min        INT           NULL     DEFAULT 0 COMMENT '耗时(分钟)',
  caution             VARCHAR(512)  NULL     COMMENT '注意事项',
  tags                VARCHAR(255)  NULL     COMMENT '标签 (逗号分隔)',
  is_premium          INT           NULL     DEFAULT 0 COMMENT '是否高端配方 0否 1是',
  version             VARCHAR(32)   NULL     DEFAULT 'v1.0' COMMENT '版本号',
  use_count           INT           NULL     DEFAULT 0 COMMENT '使用次数',
  description         VARCHAR(1024) NULL     COMMENT '配方描述',
  created_at          DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cf_tenant (tenant_id),
  KEY idx_cf_type (formula_type),
  KEY idx_cf_skin (skin_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='护理成分配方表 (CareFormula)';

-- ============================================================
-- 5. 配方应用记录表 care_formula_apply
--   记录技师在某次预约/订单里实际用了哪个配方。
-- ============================================================
CREATE TABLE IF NOT EXISTS care_formula_apply (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'PK',
  tenant_id       BIGINT       NULL     COMMENT '所属租户ID',
  formula_id      BIGINT       NULL     COMMENT '配方ID -> care_formula.id',
  member_id       BIGINT       NULL     COMMENT '会员ID -> member.id',
  booking_id      BIGINT       NULL     COMMENT '预约ID -> booking.id',
  service_id      BIGINT       NULL     COMMENT '服务ID -> biz_service.id',
  technician_id   BIGINT       NULL     COMMENT '技师ID -> employee.id',
  applied_at      DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '应用时间',
  feedback        VARCHAR(512) NULL     COMMENT '客户反馈',
  rating          INT          NULL     COMMENT '评分 1-5',
  effect_json     TEXT         NULL     COMMENT '效果记录 (JSON)',
  created_at      DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cfa_tenant (tenant_id),
  KEY idx_cfa_formula (formula_id),
  KEY idx_cfa_member (member_id),
  KEY idx_cfa_booking (booking_id),
  KEY idx_cfa_tech (technician_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='配方应用记录 (CareFormulaApply)';

-- ============================================================
-- 6. 护理方案表 care_plan
--   为某个会员定制一段时间的护理规划。
-- ============================================================
CREATE TABLE IF NOT EXISTS care_plan (
  id                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PK',
  tenant_id             BIGINT        NULL     COMMENT '所属租户ID',
  member_id             BIGINT        NULL     COMMENT '会员ID -> member.id',
  plan_code             VARCHAR(64)   NULL     COMMENT '方案编码 (业务唯一)',
  plan_name             VARCHAR(128)  NULL     COMMENT '方案名称',
  goal                  VARCHAR(512)  NULL     COMMENT '护理目标',
  start_date            DATE          NULL     COMMENT '开始日期',
  end_date              DATE          NULL     COMMENT '结束日期',
  cycle_days            INT           NULL     DEFAULT 0 COMMENT '周期(天)',
  sessions_total        INT           NULL     DEFAULT 0 COMMENT '总次数',
  sessions_per_week     DECIMAL(5,2)  NULL     COMMENT '每周次数 (e.g. 1.5)',
  estimated_cost        DECIMAL(18,2) NULL     COMMENT '预估总费用',
  steps_json            TEXT          NULL     COMMENT '方案步骤 (JSON)',
  reason_json           TEXT          NULL     COMMENT '制定依据 (JSON)',
  treatment_plan_id     BIGINT        NULL     COMMENT '关联诊疗方案ID (跨模块)',
  service_ids           VARCHAR(512)  NULL     COMMENT '包含服务ID (逗号分隔)',
  product_ids           VARCHAR(512)  NULL     COMMENT '包含产品ID (逗号分隔)',
  status                VARCHAR(16)   NULL     DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/DONE/CANCELLED',
  adherence_score       DECIMAL(5,2)  NULL     COMMENT '客户依从性评分 0-100',
  created_at            DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME      NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cp_tenant (tenant_id),
  KEY idx_cp_member (member_id),
  KEY idx_cp_status (status),
  KEY idx_cp_treatment (treatment_plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='会员护理方案 (CarePlan)';
