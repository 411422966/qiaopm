-- =============================================================================
-- 批次 A 回滚脚本：撤销 fix-batch2a.sql 的全部写入
-- 执行方式：
--   Get-Content backup\batch2-a\fix-batch2a-rollback.sql -Raw -Encoding UTF8 |
--     docker exec -i meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec
--
-- 回滚策略：
--   1) 先删本批新建的角色绑定（限定 role_code + 本批 55 码范围）
--   2) 再删本批新建的 12 个守卫码本体
--   3) 不触碰任何历史绑定、不触碰点号菜单码（crm.*）、不触碰 crm / crm:follow-rule
--
-- 注意：§1 删除范围限定在 ADMIN/BOSS/MANAGER/CLERK/OPERATOR 五个角色，
--       其他角色若历史上绑定过这 12 个新码不会被误删（脚本执行时这 12 个码刚由
--       本批创建，除五角色外无其他绑定）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- R0 回滚前快照（留存证据）
-- -----------------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM sys_permission WHERE deleted = 0) AS permission_total,
  (SELECT COUNT(*) FROM sys_role_permission)               AS role_permission_total;

-- -----------------------------------------------------------------------------
-- R1 删除本批角色绑定（§2.1 / §2.2 / §2.3 / §2.4 写入的部分）
-- -----------------------------------------------------------------------------
DELETE rp
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
  );

-- -----------------------------------------------------------------------------
-- R2 删除本批新建的 12 个守卫码本体
--    仅删 permission_type='button' 且 parent_id=1206 的新建码，
--    避免误删同名历史记录。
-- -----------------------------------------------------------------------------
DELETE FROM sys_permission
WHERE permission_type = 'button'
  AND parent_id = 1206
  AND deleted = 0
  AND permission_code IN (
    'crm:follow-up','crm:follow-plan','crm:care:suggestion','crm:detection',
    'crm:style:library','crm:complaint','crm:review','crm:review-tpl',
    'crm:cross-rule','crm:cross-record','crm:rfm','crm:tag'
  );

-- -----------------------------------------------------------------------------
-- R3 回滚自检
-- -----------------------------------------------------------------------------

-- R3.1 12 个守卫码应已不存在：期望 0
SELECT COUNT(*) AS r31_guard_codes_should_be_0
FROM sys_permission
WHERE deleted = 0
  AND permission_code IN (
    'crm:follow-up','crm:follow-plan','crm:care:suggestion','crm:detection',
    'crm:style:library','crm:complaint','crm:review','crm:review-tpl',
    'crm:cross-rule','crm:cross-record','crm:rfm','crm:tag'
  );

-- R3.2 五角色本批范围绑定计数：期望全部 0
SELECT r.role_code, COUNT(DISTINCT p.permission_code) AS bound_should_be_0
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

-- R3.3 点号菜单码仍完整：期望 10 行，deleted 全为 0
SELECT id, permission_code, permission_type, parent_id, deleted
FROM sys_permission
WHERE permission_code IN (
  'crm.care-overview','crm.follow-task','crm.follow-plan','crm.complaint',
  'crm.review','crm.review-tpl','crm.cross-rule','crm.cross-record','crm.rfm','crm.ltv'
)
ORDER BY id;
