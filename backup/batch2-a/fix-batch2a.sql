-- =============================================================================
-- QIAOPM 权限体系对齐 批次 A：CRM 客户运营 + 数据洞察
-- 依据：audit/baseline/I124-batch2-investigation.md / 用户 2026-11-04 拍板
-- 执行方式（PowerShell）：
--   Get-Content backup\batch2-a\fix-batch2a.sql -Raw -Encoding UTF8 |
--     docker exec -i meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec
-- 验证：fix-batch2a-verify.sql
-- 回滚：fix-batch2a-rollback.sql
--
-- 范围说明：
--   * 本批目标码共 55 个 = 后端注解码 43 个（crm:* 40 + report:* 3）
--     + 前端路由守卫码 12 个（当前 sys_permission 中不存在，需新建）
--   * 新建 12 个守卫码为 permission_type='button'，parent_id=1206（crm 菜单）
--   * 不动任何点号菜单码（crm.* ）、不删任何历史绑定、纯 INSERT 追加
--
-- 幂等性：NOT EXISTS 子查询 + 派生表包装（规避 MySQL 1093 目标表限制），
--          重复执行影响 0 行。
--
-- 本文件不含 START TRANSACTION / COMMIT，事务边界由执行命令控制。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- §0 前置检查（只读，结果需人工确认后再执行 §1）
-- -----------------------------------------------------------------------------

-- §0.1 四个目标角色存在性检查：期望返回 4 行
SELECT id, role_code, role_name, tenant_id, deleted
FROM sys_role
WHERE role_code IN ('ADMIN','BOSS','MANAGER','CLERK','OPERATOR')
ORDER BY id;

-- §0.2 后端注解码存在性检查：期望返回 43（crm:* 40 + report:* 3）
SELECT COUNT(*) AS should_be_43_backend_codes
FROM sys_permission
WHERE deleted = 0
  AND permission_code IN (
    'crm:care:complaint:edit','crm:care:complaint:handle','crm:care:complaint:list',
    'crm:care:complaint:reply','crm:care:follow:plan:list','crm:care:follow:task:edit',
    'crm:care:follow:task:list','crm:care:ops:view','crm:care:recipe:edit',
    'crm:care:recipe:list','crm:care:review:edit','crm:care:review:list',
    'crm:care:review:tpl:edit','crm:care:review:tpl:list','crm:care:suggestion:engine:edit',
    'crm:care:suggestion:engine:view','crm:care:suggestion:log:edit','crm:care:suggestion:log:list',
    'crm:care:suggestion:rule:edit','crm:care:suggestion:rule:list','crm:care:suggestion:tpl:edit',
    'crm:care:suggestion:tpl:list','crm:cohort:edit','crm:cohort:list','crm:cross:redemption:edit',
    'crm:cross:redemption:list','crm:cross:rule:edit','crm:cross:rule:list',
    'crm:detection:diagnosis:view','crm:detection:hair:edit','crm:detection:hair:list',
    'crm:detection:nail:edit','crm:detection:nail:list','crm:detection:report:edit',
    'crm:detection:report:list','crm:detection:skin:edit','crm:detection:skin:list',
    'crm:detection:solution:edit','crm:detection:solution:list','crm:follow-rule',
    'report:price-sensitivity:view','report:repurchase:view','report:rfm:batch:run'
  );

-- §0.3 前端 12 个守卫码存在性检查：期望返回 0（全部缺失，需 §1 新建）
SELECT COUNT(*) AS should_be_0_missing_guard_codes
FROM sys_permission
WHERE deleted = 0
  AND permission_code IN (
    'crm:follow-up','crm:follow-plan','crm:care:suggestion','crm:detection',
    'crm:style:library','crm:complaint','crm:review','crm:review-tpl',
    'crm:cross-rule','crm:cross-record','crm:rfm','crm:tag'
  );

-- §0.4 修复前本批目标码的角色绑定快照（用于回滚对账）
SELECT r.role_code, COUNT(*) AS bound_in_batch_scope
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code IN ('ADMIN','BOSS','MANAGER','CLERK','OPERATOR')
  AND p.deleted = 0
  AND p.permission_code IN (
    'crm:care:complaint:edit','crm:care:complaint:handle','crm:care:complaint:list',
    'crm:care:complaint:reply','crm:care:follow:plan:list','crm:care:follow:task:edit',
    'crm:care:follow:task:list','crm:care:ops:view','crm:care:recipe:edit',
    'crm:care:recipe:list','crm:care:review:edit','crm:care:review:list',
    'crm:care:review:tpl:edit','crm:care:review:tpl:list','crm:care:suggestion:engine:edit',
    'crm:care:suggestion:engine:view','crm:care:suggestion:log:edit','crm:care:suggestion:log:list',
    'crm:care:suggestion:rule:edit','crm:care:suggestion:rule:list','crm:care:suggestion:tpl:edit',
    'crm:care:suggestion:tpl:list','crm:cohort:edit','crm:cohort:list','crm:cross:redemption:edit',
    'crm:cross:redemption:list','crm:cross:rule:edit','crm:cross:rule:list',
    'crm:detection:diagnosis:view','crm:detection:hair:edit','crm:detection:hair:list',
    'crm:detection:nail:edit','crm:detection:nail:list','crm:detection:report:edit',
    'crm:detection:report:list','crm:detection:skin:edit','crm:detection:skin:list',
    'crm:detection:solution:edit','crm:detection:solution:list','crm:follow-rule',
    'report:price-sensitivity:view','report:repurchase:view','report:rfm:batch:run',
    'crm:follow-up','crm:follow-plan','crm:care:suggestion','crm:detection',
    'crm:style:library','crm:complaint','crm:review','crm:review-tpl',
    'crm:cross-rule','crm:cross-record','crm:rfm','crm:tag'
  )
GROUP BY r.role_code
ORDER BY r.role_code;

-- -----------------------------------------------------------------------------
-- §1 新建前端 12 个路由守卫码
--    这些码在前端 router/index.ts 的 meta.permission 中被精确匹配
--    （frontend/meiyetec-web/src/stores/permission.ts:64），库中不存在即 403。
-- -----------------------------------------------------------------------------

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order, status, deleted)
SELECT src.permission_code, src.permission_name, 'button', 1206, src.sort_order, 1, 0
FROM (
    SELECT 'crm:follow-up'    AS permission_code, '客户运营与回访任务' AS permission_name, 101 AS sort_order
    UNION ALL SELECT 'crm:follow-plan',    '回访计划与任务规则', 102
    UNION ALL SELECT 'crm:care:suggestion','护理建议生成',     103
    UNION ALL SELECT 'crm:detection',      '肤质与检测档案',   104
    UNION ALL SELECT 'crm:style:library',  '款式偏好图库',     105
    UNION ALL SELECT 'crm:complaint',      '投诉工单',         106
    UNION ALL SELECT 'crm:review',         '客户评价',         107
    UNION ALL SELECT 'crm:review-tpl',     '评价模板',         108
    UNION ALL SELECT 'crm:cross-rule',     '跨店规则',         109
    UNION ALL SELECT 'crm:cross-record',   '跨店核销',         110
    UNION ALL SELECT 'crm:rfm',            'RFM 分群',         111
    UNION ALL SELECT 'crm:tag',            '会员标签与 LTV',   112
) src
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT permission_code FROM sys_permission) p
    WHERE p.permission_code = src.permission_code
);

-- -----------------------------------------------------------------------------
-- §2 角色绑定矩阵
--
-- 只读码（22 个后端 list/view + crm:follow-rule 菜单码 + 11 个前端守卫码）
--   = 34 个，BOSS 绑定全部
-- 写操作码（19 个后端 edit/handle/reply + report:rfm:batch:run）
--   = 20 个，MANAGER 在只读基础上追加
-- ADMIN = 34 + 20 + crm:rfm = 55（全量）
-- CLERK = 4 个（投诉工单：进入 + 列表 + 处理 + 回复）
-- OPERATOR = 0（本批次不绑定任何 CRM 码）
--
-- 注：crm:rfm 不绑定给 BOSS/MANAGER —— 前端 /intel/rfm 与 /cohort 路由已按用户
--     决策摘除（后端 RFM 业务接口不存在），授权一个不可达页面无意义。
-- -----------------------------------------------------------------------------

-- §2.1 只读码集合（BOSS / MANAGER / ADMIN 共用，34 个）
--   只读后端码 22 个
--   + crm:follow-rule（回访任务规则，只读）
--   + 前端守卫码 11 个（crm:rfm 除外）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT src.role_id, src.permission_id
FROM (
    SELECT r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p
      ON p.deleted = 0
     AND p.permission_code IN (
        -- 只读后端码
        'crm:care:ops:view','crm:care:follow:plan:list','crm:care:follow:task:list',
        'crm:care:review:list','crm:care:review:tpl:list','crm:care:complaint:list',
        'crm:care:recipe:list','crm:care:suggestion:engine:view','crm:care:suggestion:log:list',
        'crm:care:suggestion:rule:list','crm:care:suggestion:tpl:list','crm:cohort:list',
        'crm:cross:rule:list','crm:cross:redemption:list','crm:detection:diagnosis:view',
        'crm:detection:hair:list','crm:detection:nail:list','crm:detection:report:list',
        'crm:detection:skin:list','crm:detection:solution:list',
        'report:repurchase:view','report:price-sensitivity:view',
        -- 回访任务规则（菜单型，只读）
        'crm:follow-rule',
        -- 前端路由守卫码
        'crm:follow-up','crm:follow-plan','crm:care:suggestion','crm:detection',
        'crm:style:library','crm:complaint','crm:review','crm:review-tpl',
        'crm:cross-rule','crm:cross-record','crm:tag'
     )
    WHERE r.role_code IN ('ADMIN','BOSS','MANAGER') AND r.deleted = 0
) src
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, permission_id FROM sys_role_permission) rp
    WHERE rp.role_id = src.role_id AND rp.permission_id = src.permission_id
);

-- §2.2 写操作码集合（MANAGER + ADMIN，20 个）
--   含 report:rfm:batch:run（用户 2026-11-04 拍板：MANAGER 需要批处理权限）
--   注意 BOSS 不绑定本集合 —— 用户拍板 BOSS 保持只读，不需要 crm:care:follow:task:edit
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT src.role_id, src.permission_id
FROM (
    SELECT r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p
      ON p.deleted = 0
     AND p.permission_code IN (
        'crm:care:follow:task:edit','crm:care:complaint:edit','crm:care:complaint:handle',
        'crm:care:complaint:reply','crm:care:review:edit','crm:care:review:tpl:edit',
        'crm:care:recipe:edit','crm:care:suggestion:engine:edit','crm:care:suggestion:log:edit',
        'crm:care:suggestion:rule:edit','crm:care:suggestion:tpl:edit','crm:cohort:edit',
        'crm:cross:rule:edit','crm:cross:redemption:edit','crm:detection:report:edit',
        'crm:detection:solution:edit','crm:detection:hair:edit','crm:detection:nail:edit',
        'crm:detection:skin:edit','report:rfm:batch:run'
     )
    WHERE r.role_code IN ('ADMIN','MANAGER') AND r.deleted = 0
) src
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, permission_id FROM sys_role_permission) rp
    WHERE rp.role_id = src.role_id AND rp.permission_id = src.permission_id
);

-- §2.3 ADMIN 专属：crm:rfm（RFM 分群守卫码，仅超管保留）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT src.role_id, src.permission_id
FROM (
    SELECT r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.deleted = 0 AND p.permission_code = 'crm:rfm'
    WHERE r.role_code = 'ADMIN' AND r.deleted = 0
) src
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, permission_id FROM sys_role_permission) rp
    WHERE rp.role_id = src.role_id AND rp.permission_id = src.permission_id
);

-- §2.4 CLERK —— 投诉工单 4 码（进入页面 + 列表 + 处理 + 回复）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT src.role_id, src.permission_id
FROM (
    SELECT r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p
      ON p.deleted = 0
     AND p.permission_code IN (
        'crm:complaint','crm:care:complaint:list','crm:care:complaint:handle','crm:care:complaint:reply'
     )
    WHERE r.role_code = 'CLERK' AND r.deleted = 0
) src
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT role_id, permission_id FROM sys_role_permission) rp
    WHERE rp.role_id = src.role_id AND rp.permission_id = src.permission_id
);

-- §2.5 OPERATOR —— 本批次显式不绑定任何 CRM 码（保持空集）
--     保留语句仅为对称说明，期望影响 0 行。

-- -----------------------------------------------------------------------------
-- §3 执行后即时快照
-- -----------------------------------------------------------------------------

-- §3.1 五角色在本批 55 码范围内的绑定计数
--     期望：ADMIN 55 / BOSS 34 / MANAGER 54 / CLERK 4 / OPERATOR 0
SELECT r.role_code, COUNT(DISTINCT p.permission_code) AS bound_in_batch_scope
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code IN ('ADMIN','BOSS','MANAGER','CLERK','OPERATOR')
  AND p.deleted = 0
  AND p.permission_code IN (
    'crm:care:complaint:edit','crm:care:complaint:handle','crm:care:complaint:list',
    'crm:care:complaint:reply','crm:care:follow:plan:list','crm:care:follow:task:edit',
    'crm:care:follow:task:list','crm:care:ops:view','crm:care:recipe:edit',
    'crm:care:recipe:list','crm:care:review:edit','crm:care:review:list',
    'crm:care:review:tpl:edit','crm:care:review:tpl:list','crm:care:suggestion:engine:edit',
    'crm:care:suggestion:engine:view','crm:care:suggestion:log:edit','crm:care:suggestion:log:list',
    'crm:care:suggestion:rule:edit','crm:care:suggestion:rule:list','crm:care:suggestion:tpl:edit',
    'crm:care:suggestion:tpl:list','crm:cohort:edit','crm:cohort:list','crm:cross:redemption:edit',
    'crm:cross:redemption:list','crm:cross:rule:edit','crm:cross:rule:list',
    'crm:detection:diagnosis:view','crm:detection:hair:edit','crm:detection:hair:list',
    'crm:detection:nail:edit','crm:detection:nail:list','crm:detection:report:edit',
    'crm:detection:report:list','crm:detection:skin:edit','crm:detection:skin:list',
    'crm:detection:solution:edit','crm:detection:solution:list','crm:follow-rule',
    'report:price-sensitivity:view','report:repurchase:view','report:rfm:batch:run',
    'crm:follow-up','crm:follow-plan','crm:care:suggestion','crm:detection',
    'crm:style:library','crm:complaint','crm:review','crm:review-tpl',
    'crm:cross-rule','crm:cross-record','crm:rfm','crm:tag'
  )
GROUP BY r.role_code
ORDER BY r.role_code;

-- §3.2 sys_permission / sys_role_permission 总数快照
SELECT
  (SELECT COUNT(*) FROM sys_permission WHERE deleted = 0) AS permission_total,
  (SELECT COUNT(*) FROM sys_role_permission)               AS role_permission_total;
