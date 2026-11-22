-- ============================================================
-- I183/I184 绑定后验证 — V1 / V2 / V3 / V4 / V5（2026-11-22，D1 执行后）
-- 只读查询，不写库
--
-- 判定标准: 不是「绑定数 > 0 就算过」，而是「每个码的 role_id 集合恰好等于预期集合」
--
-- 预期结果:
--   V1: total = 1267；分角色 = 1:118 2:47 4:54 5:304 6:224 7:105 8:415
--   V2: 34 个码全部 PASS（实际 role_id 集合 == 预期集合）
--   V3: 「预期该绑但没绑」检查 = 0 行
--   V4: 「绑了但不在预期内」检查 = 0 行
--   V5: 34 个码每个绑定数 > 0，且等于该码预期角色数
-- ============================================================

-- ===== V1 总量与分角色计数 =====
SELECT 'V1_total' AS chk, COUNT(*) AS val FROM sys_role_permission;
SELECT 'V1_byrole' AS chk, rp.role_id, r.role_code, COUNT(*) AS val
FROM sys_role_permission rp LEFT JOIN sys_role r ON r.id = rp.role_id
GROUP BY rp.role_id, r.role_code ORDER BY rp.role_id;

-- ===== V2 每个码的 role_id 集合恰好等于预期集合（集合全等，非计数）=====
SELECT 'V2' AS chk, e.permission_code,
       e.expected_roles,
       a.actual_roles,
       CASE WHEN a.actual_roles <=> e.expected_roles THEN 'PASS' ELSE 'FAIL' END AS verdict
FROM (
  SELECT permission_code,
         GROUP_CONCAT(DISTINCT role_id ORDER BY role_id) AS expected_roles
  FROM (VALUES
    ROW('attendance:check',2), ROW('attendance:check',4), ROW('attendance:check',5), ROW('attendance:check',6), ROW('attendance:check',8),
    ROW('attendance:list',2), ROW('attendance:list',4), ROW('attendance:list',5), ROW('attendance:list',6), ROW('attendance:list',8),
    ROW('attendance:stat',2), ROW('attendance:stat',5), ROW('attendance:stat',6), ROW('attendance:stat',8),
    ROW('attendance:edit',5), ROW('attendance:edit',8),
    ROW('attendance:config:list',2), ROW('attendance:config:list',5), ROW('attendance:config:list',6), ROW('attendance:config:list',8),
    ROW('attendance:config:edit',5), ROW('attendance:config:edit',8),
    ROW('attendance:leave:submit',2), ROW('attendance:leave:submit',4), ROW('attendance:leave:submit',5), ROW('attendance:leave:submit',6), ROW('attendance:leave:submit',8),
    ROW('attendance:leave:list',2), ROW('attendance:leave:list',4), ROW('attendance:leave:list',5), ROW('attendance:leave:list',6), ROW('attendance:leave:list',8),
    ROW('attendance:leave:approve',2), ROW('attendance:leave:approve',5), ROW('attendance:leave:approve',6), ROW('attendance:leave:approve',8),
    ROW('commission:rule:add',5), ROW('commission:rule:add',6), ROW('commission:rule:add',8),
    ROW('commission:rule:edit',5), ROW('commission:rule:edit',6), ROW('commission:rule:edit',8),
    ROW('commission:rule:delete',5), ROW('commission:rule:delete',6), ROW('commission:rule:delete',8),
    ROW('commission:rule:status',5), ROW('commission:rule:status',6), ROW('commission:rule:status',8),
    ROW('commission:rule:detail',5), ROW('commission:rule:detail',6), ROW('commission:rule:detail',8),
    ROW('commission:rule:stat',5), ROW('commission:rule:stat',6), ROW('commission:rule:stat',8),
    ROW('commission:calculate',5), ROW('commission:calculate',6), ROW('commission:calculate',8),
    ROW('commission:record:stat',5), ROW('commission:record:stat',6), ROW('commission:record:stat',8),
    ROW('commission:salary:add',5), ROW('commission:salary:add',6), ROW('commission:salary:add',8),
    ROW('commission:salary:stat',5), ROW('commission:salary:stat',6), ROW('commission:salary:stat',8),
    ROW('commission:salary:adjust',5), ROW('commission:salary:adjust',6), ROW('commission:salary:adjust',8),
    ROW('commission:salary:confirm',6), ROW('commission:salary:confirm',8),
    ROW('commission:salary:pay',6), ROW('commission:salary:pay',8),
    ROW('commission:salary:batch-confirm',6), ROW('commission:salary:batch-confirm',8),
    ROW('commission:salary:batch-pay',6), ROW('commission:salary:batch-pay',8),
    ROW('commission:salary:export',5), ROW('commission:salary:export',6), ROW('commission:salary:export',8),
    ROW('commission:settlement:add',5), ROW('commission:settlement:add',6), ROW('commission:settlement:add',8),
    ROW('commission:settlement:detail',5), ROW('commission:settlement:detail',6), ROW('commission:settlement:detail',8),
    ROW('commission:settlement:stat',5), ROW('commission:settlement:stat',6), ROW('commission:settlement:stat',8),
    ROW('commission:settlement:confirm',6), ROW('commission:settlement:confirm',8),
    ROW('commission:settlement:pay',6), ROW('commission:settlement:pay',8),
    ROW('commission:settlement:batch-confirm',6), ROW('commission:settlement:batch-confirm',8),
    ROW('commission:settlement:batch-pay',6), ROW('commission:settlement:batch-pay',8),
    ROW('commission:settlement:export',5), ROW('commission:settlement:export',6), ROW('commission:settlement:export',8),
    ROW('commission:simulation',5), ROW('commission:simulation',6), ROW('commission:simulation',8)
  ) AS t(permission_code, role_id)
  GROUP BY permission_code
) e
LEFT JOIN (
  SELECT p.permission_code,
         GROUP_CONCAT(DISTINCT rp.role_id ORDER BY rp.role_id) AS actual_roles
  FROM sys_permission p
  JOIN sys_role_permission rp ON rp.permission_id = p.id
  WHERE p.permission_code IN (
    'attendance:check','attendance:list','attendance:stat','attendance:edit',
    'attendance:config:list','attendance:config:edit','attendance:leave:submit',
    'attendance:leave:list','attendance:leave:approve','commission:rule:add','commission:rule:edit',
    'commission:rule:delete','commission:rule:status','commission:rule:detail','commission:rule:stat',
    'commission:calculate','commission:record:stat','commission:salary:add','commission:salary:stat',
    'commission:salary:adjust','commission:salary:confirm','commission:salary:pay',
    'commission:salary:batch-confirm','commission:salary:batch-pay','commission:salary:export',
    'commission:settlement:add','commission:settlement:detail','commission:settlement:stat',
    'commission:settlement:confirm','commission:settlement:pay','commission:settlement:batch-confirm',
    'commission:settlement:batch-pay','commission:settlement:export','commission:simulation')
  GROUP BY p.permission_code
) a ON a.permission_code = e.permission_code
ORDER BY e.permission_code;

-- ===== V3 「预期该绑但没绑」检查（应 0 行）=====
SELECT 'V3_missing' AS chk, e.permission_code, e.role_id
FROM (
  SELECT permission_code, role_id FROM (VALUES
    ROW('attendance:check',2), ROW('attendance:check',4), ROW('attendance:check',5), ROW('attendance:check',6), ROW('attendance:check',8),
    ROW('attendance:list',2), ROW('attendance:list',4), ROW('attendance:list',5), ROW('attendance:list',6), ROW('attendance:list',8),
    ROW('attendance:stat',2), ROW('attendance:stat',5), ROW('attendance:stat',6), ROW('attendance:stat',8),
    ROW('attendance:edit',5), ROW('attendance:edit',8),
    ROW('attendance:config:list',2), ROW('attendance:config:list',5), ROW('attendance:config:list',6), ROW('attendance:config:list',8),
    ROW('attendance:config:edit',5), ROW('attendance:config:edit',8),
    ROW('attendance:leave:submit',2), ROW('attendance:leave:submit',4), ROW('attendance:leave:submit',5), ROW('attendance:leave:submit',6), ROW('attendance:leave:submit',8),
    ROW('attendance:leave:list',2), ROW('attendance:leave:list',4), ROW('attendance:leave:list',5), ROW('attendance:leave:list',6), ROW('attendance:leave:list',8),
    ROW('attendance:leave:approve',2), ROW('attendance:leave:approve',5), ROW('attendance:leave:approve',6), ROW('attendance:leave:approve',8),
    ROW('commission:rule:add',5), ROW('commission:rule:add',6), ROW('commission:rule:add',8),
    ROW('commission:rule:edit',5), ROW('commission:rule:edit',6), ROW('commission:rule:edit',8),
    ROW('commission:rule:delete',5), ROW('commission:rule:delete',6), ROW('commission:rule:delete',8),
    ROW('commission:rule:status',5), ROW('commission:rule:status',6), ROW('commission:rule:status',8),
    ROW('commission:rule:detail',5), ROW('commission:rule:detail',6), ROW('commission:rule:detail',8),
    ROW('commission:rule:stat',5), ROW('commission:rule:stat',6), ROW('commission:rule:stat',8),
    ROW('commission:calculate',5), ROW('commission:calculate',6), ROW('commission:calculate',8),
    ROW('commission:record:stat',5), ROW('commission:record:stat',6), ROW('commission:record:stat',8),
    ROW('commission:salary:add',5), ROW('commission:salary:add',6), ROW('commission:salary:add',8),
    ROW('commission:salary:stat',5), ROW('commission:salary:stat',6), ROW('commission:salary:stat',8),
    ROW('commission:salary:adjust',5), ROW('commission:salary:adjust',6), ROW('commission:salary:adjust',8),
    ROW('commission:salary:confirm',6), ROW('commission:salary:confirm',8),
    ROW('commission:salary:pay',6), ROW('commission:salary:pay',8),
    ROW('commission:salary:batch-confirm',6), ROW('commission:salary:batch-confirm',8),
    ROW('commission:salary:batch-pay',6), ROW('commission:salary:batch-pay',8),
    ROW('commission:salary:export',5), ROW('commission:salary:export',6), ROW('commission:salary:export',8),
    ROW('commission:settlement:add',5), ROW('commission:settlement:add',6), ROW('commission:settlement:add',8),
    ROW('commission:settlement:detail',5), ROW('commission:settlement:detail',6), ROW('commission:settlement:detail',8),
    ROW('commission:settlement:stat',5), ROW('commission:settlement:stat',6), ROW('commission:settlement:stat',8),
    ROW('commission:settlement:confirm',6), ROW('commission:settlement:confirm',8),
    ROW('commission:settlement:pay',6), ROW('commission:settlement:pay',8),
    ROW('commission:settlement:batch-confirm',6), ROW('commission:settlement:batch-confirm',8),
    ROW('commission:settlement:batch-pay',6), ROW('commission:settlement:batch-pay',8),
    ROW('commission:settlement:export',5), ROW('commission:settlement:export',6), ROW('commission:settlement:export',8),
    ROW('commission:simulation',5), ROW('commission:simulation',6), ROW('commission:simulation',8)
  ) AS t(permission_code, role_id)
) e
LEFT JOIN sys_permission p ON p.permission_code = e.permission_code
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.role_id = e.role_id
WHERE rp.id IS NULL
ORDER BY e.permission_code, e.role_id;

-- ===== V4 「绑了但不在预期内」检查（应 0 行）=====
SELECT 'V4_extra' AS chk, rp.role_id, p.permission_code
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.permission_code IN (
    'attendance:check','attendance:list','attendance:stat','attendance:edit',
    'attendance:config:list','attendance:config:edit','attendance:leave:submit',
    'attendance:leave:list','attendance:leave:approve','commission:rule:add','commission:rule:edit',
    'commission:rule:delete','commission:rule:status','commission:rule:detail','commission:rule:stat',
    'commission:calculate','commission:record:stat','commission:salary:add','commission:salary:stat',
    'commission:salary:adjust','commission:salary:confirm','commission:salary:pay',
    'commission:salary:batch-confirm','commission:salary:batch-pay','commission:salary:export',
    'commission:settlement:add','commission:settlement:detail','commission:settlement:stat',
    'commission:settlement:confirm','commission:settlement:pay','commission:settlement:batch-confirm',
    'commission:settlement:batch-pay','commission:settlement:export','commission:simulation')
  AND NOT EXISTS (
    SELECT 1 FROM (VALUES
      ROW('attendance:check',2), ROW('attendance:check',4), ROW('attendance:check',5), ROW('attendance:check',6), ROW('attendance:check',8),
      ROW('attendance:list',2), ROW('attendance:list',4), ROW('attendance:list',5), ROW('attendance:list',6), ROW('attendance:list',8),
      ROW('attendance:stat',2), ROW('attendance:stat',5), ROW('attendance:stat',6), ROW('attendance:stat',8),
      ROW('attendance:edit',5), ROW('attendance:edit',8),
      ROW('attendance:config:list',2), ROW('attendance:config:list',5), ROW('attendance:config:list',6), ROW('attendance:config:list',8),
      ROW('attendance:config:edit',5), ROW('attendance:config:edit',8),
      ROW('attendance:leave:submit',2), ROW('attendance:leave:submit',4), ROW('attendance:leave:submit',5), ROW('attendance:leave:submit',6), ROW('attendance:leave:submit',8),
      ROW('attendance:leave:list',2), ROW('attendance:leave:list',4), ROW('attendance:leave:list',5), ROW('attendance:leave:list',6), ROW('attendance:leave:list',8),
      ROW('attendance:leave:approve',2), ROW('attendance:leave:approve',5), ROW('attendance:leave:approve',6), ROW('attendance:leave:approve',8),
      ROW('commission:rule:add',5), ROW('commission:rule:add',6), ROW('commission:rule:add',8),
      ROW('commission:rule:edit',5), ROW('commission:rule:edit',6), ROW('commission:rule:edit',8),
      ROW('commission:rule:delete',5), ROW('commission:rule:delete',6), ROW('commission:rule:delete',8),
      ROW('commission:rule:status',5), ROW('commission:rule:status',6), ROW('commission:rule:status',8),
      ROW('commission:rule:detail',5), ROW('commission:rule:detail',6), ROW('commission:rule:detail',8),
      ROW('commission:rule:stat',5), ROW('commission:rule:stat',6), ROW('commission:rule:stat',8),
      ROW('commission:calculate',5), ROW('commission:calculate',6), ROW('commission:calculate',8),
      ROW('commission:record:stat',5), ROW('commission:record:stat',6), ROW('commission:record:stat',8),
      ROW('commission:salary:add',5), ROW('commission:salary:add',6), ROW('commission:salary:add',8),
      ROW('commission:salary:stat',5), ROW('commission:salary:stat',6), ROW('commission:salary:stat',8),
      ROW('commission:salary:adjust',5), ROW('commission:salary:adjust',6), ROW('commission:salary:adjust',8),
      ROW('commission:salary:confirm',6), ROW('commission:salary:confirm',8),
      ROW('commission:salary:pay',6), ROW('commission:salary:pay',8),
      ROW('commission:salary:batch-confirm',6), ROW('commission:salary:batch-confirm',8),
      ROW('commission:salary:batch-pay',6), ROW('commission:salary:batch-pay',8),
      ROW('commission:salary:export',5), ROW('commission:salary:export',6), ROW('commission:salary:export',8),
      ROW('commission:settlement:add',5), ROW('commission:settlement:add',6), ROW('commission:settlement:add',8),
      ROW('commission:settlement:detail',5), ROW('commission:settlement:detail',6), ROW('commission:settlement:detail',8),
      ROW('commission:settlement:stat',5), ROW('commission:settlement:stat',6), ROW('commission:settlement:stat',8),
      ROW('commission:settlement:confirm',6), ROW('commission:settlement:confirm',8),
      ROW('commission:settlement:pay',6), ROW('commission:settlement:pay',8),
      ROW('commission:settlement:batch-confirm',6), ROW('commission:settlement:batch-confirm',8),
      ROW('commission:settlement:batch-pay',6), ROW('commission:settlement:batch-pay',8),
      ROW('commission:settlement:export',5), ROW('commission:settlement:export',6), ROW('commission:settlement:export',8),
      ROW('commission:simulation',5), ROW('commission:simulation',6), ROW('commission:simulation',8)
    ) AS t(permission_code, role_id)
    WHERE t.permission_code = p.permission_code AND t.role_id = rp.role_id
  )
ORDER BY p.permission_code, rp.role_id;

-- ===== V5 逐码绑定覆盖（确认无「一个都没绑」的码，且等于该码预期角色数）=====
SELECT 'V5' AS chk, p.permission_code, p.id AS permission_id, COUNT(rp.id) AS bind_cnt
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code IN (
    'attendance:check','attendance:list','attendance:stat','attendance:edit',
    'attendance:config:list','attendance:config:edit','attendance:leave:submit',
    'attendance:leave:list','attendance:leave:approve','commission:rule:add','commission:rule:edit',
    'commission:rule:delete','commission:rule:status','commission:rule:detail','commission:rule:stat',
    'commission:calculate','commission:record:stat','commission:salary:add','commission:salary:stat',
    'commission:salary:adjust','commission:salary:confirm','commission:salary:pay',
    'commission:salary:batch-confirm','commission:salary:batch-pay','commission:salary:export',
    'commission:settlement:add','commission:settlement:detail','commission:settlement:stat',
    'commission:settlement:confirm','commission:settlement:pay','commission:settlement:batch-confirm',
    'commission:settlement:batch-pay','commission:settlement:export','commission:simulation')
GROUP BY p.permission_code, p.id
ORDER BY p.permission_code;

-- ===== 附加：孤儿码确认仍未绑定（D3 口径）=====
SELECT 'orphan_check' AS chk, p.permission_code, p.id, COUNT(rp.id) AS bind_cnt
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code IN ('commission:list','commission:settlement:audit')
GROUP BY p.permission_code, p.id;

-- ===== 附加：确认 role 1/3/7 未被本批次污染 =====
SELECT 'untouched_roles' AS chk, rp.role_id, COUNT(*) AS cnt
FROM sys_role_permission rp WHERE rp.role_id IN (1,3,7)
GROUP BY rp.role_id ORDER BY rp.role_id;
