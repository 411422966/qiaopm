-- =============================================================================
-- 批次 A 验证脚本：fix-batch2a.sql 执行后逐项核对
-- 执行方式：
--   Get-Content backup\batch2-a\fix-batch2a-verify.sql -Raw -Encoding UTF8 |
--     docker exec -i meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec
--
-- 判定标准：每项 expected 与 actual 一致即为 PASS；不一致需回滚排查。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- V1 前端 12 个守卫码存在性：期望 12
-- -----------------------------------------------------------------------------
SELECT COUNT(*) AS v1_guard_codes_exist
FROM sys_permission
WHERE deleted = 0
  AND permission_code IN (
    'crm:follow-up','crm:follow-plan','crm:care:suggestion','crm:detection',
    'crm:style:library','crm:complaint','crm:review','crm:review-tpl',
    'crm:cross-rule','crm:cross-record','crm:rfm','crm:tag'
  );
-- 期望：12

-- -----------------------------------------------------------------------------
-- V2 后端 43 个注解码存在性：期望 43
-- -----------------------------------------------------------------------------
SELECT COUNT(*) AS v2_backend_codes_exist
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
-- 期望：43

-- -----------------------------------------------------------------------------
-- V3 五角色在本批范围内的绑定计数矩阵
--     期望：ADMIN 55 / BOSS 34 / MANAGER 54 / CLERK 4 / OPERATOR 0
-- -----------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------
-- V4 明细清单：按角色展开本批绑定到权限码
-- -----------------------------------------------------------------------------
SELECT r.role_code, p.permission_code, p.permission_type
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code IN ('ADMIN','BOSS','MANAGER','CLERK','OPERATOR')
  AND p.deleted = 0
  AND (p.permission_code LIKE 'crm:%' OR p.permission_code LIKE 'report:repurchase%'
       OR p.permission_code LIKE 'report:rfm%' OR p.permission_code LIKE 'report:price%')
ORDER BY r.id, p.permission_code;

-- -----------------------------------------------------------------------------
-- V5 OPERATOR 反查：期望 0 行
-- -----------------------------------------------------------------------------
SELECT r.role_code, p.permission_code
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_code = 'OPERATOR'
  AND p.deleted = 0
  AND (p.permission_code LIKE 'crm:%' OR p.permission_code LIKE 'report:repurchase%'
       OR p.permission_code LIKE 'report:rfm%' OR p.permission_code LIKE 'report:price%');
-- 期望：0 行

-- -----------------------------------------------------------------------------
-- V6 幂等性检查：重跑 fix-batch2a.sql 后，下面的总绑定数应与首次执行后相同
-- -----------------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM sys_permission WHERE deleted = 0) AS permission_total,
  (SELECT COUNT(*) FROM sys_role_permission)               AS role_permission_total;

-- -----------------------------------------------------------------------------
-- V7 遗留点号菜单码未被破坏（期望 10 条，全部仍存在）
-- -----------------------------------------------------------------------------
SELECT id, permission_code, permission_type, parent_id, deleted
FROM sys_permission
WHERE permission_code IN (
  'crm.care-overview','crm.follow-task','crm.follow-plan','crm.complaint',
  'crm.review','crm.review-tpl','crm.cross-rule','crm.cross-record','crm.rfm','crm.ltv'
)
ORDER BY id;
-- 期望：10 行，deleted 全为 0
