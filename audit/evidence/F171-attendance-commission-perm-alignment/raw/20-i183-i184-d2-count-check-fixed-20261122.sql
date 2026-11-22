-- ============================================================
-- I183/I184 D2 计数复核 — 修正 17-*.sql 的 D5a 逐角色增量算错
-- 缺陷: 17-*.sql 核算 2/3 用 CASE WHEN role_id IN (5,6,8) THEN 3 ELSE 0 END
--       D5a 只产生 1 个新码 commission:simulation，绑 5,6,8 三角色 = 3 行 = 每角色 +1
--       故 THEN 3 应为 THEN 1（逐角色），而总量口径 +3 才是对的（3 行）
-- 本脚本：只读，不写库。逐角色 +1 修正后的预期值
-- 数据来源: 当前库实时（2026-11-22 复核）
-- ============================================================

DROP TEMPORARY TABLE IF EXISTS tmp_w;
CREATE TEMPORARY TABLE tmp_w (
  permission_code VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL,
  role_id INT NOT NULL
);
INSERT INTO tmp_w (permission_code, role_id) VALUES
  ('attendance:check',2),('attendance:check',4),('attendance:check',5),('attendance:check',6),('attendance:check',8),
  ('attendance:list',2),('attendance:list',4),('attendance:list',5),('attendance:list',6),('attendance:list',8),
  ('attendance:stat',2),('attendance:stat',5),('attendance:stat',6),('attendance:stat',8),
  ('attendance:edit',5),('attendance:edit',8),
  ('attendance:config:list',2),('attendance:config:list',5),('attendance:config:list',6),('attendance:config:list',8),
  ('attendance:config:edit',5),('attendance:config:edit',8),
  ('attendance:leave:submit',2),('attendance:leave:submit',4),('attendance:leave:submit',5),('attendance:leave:submit',6),('attendance:leave:submit',8),
  ('attendance:leave:list',2),('attendance:leave:list',4),('attendance:leave:list',5),('attendance:leave:list',6),('attendance:leave:list',8),
  ('attendance:leave:approve',2),('attendance:leave:approve',5),('attendance:leave:approve',6),('attendance:leave:approve',8),
  ('commission:rule:add',5),('commission:rule:add',6),('commission:rule:add',8),
  ('commission:rule:edit',5),('commission:rule:edit',6),('commission:rule:edit',8),
  ('commission:rule:delete',5),('commission:rule:delete',6),('commission:rule:delete',8),
  ('commission:rule:status',5),('commission:rule:status',6),('commission:rule:status',8),
  ('commission:rule:detail',5),('commission:rule:detail',6),('commission:rule:detail',8),
  ('commission:rule:stat',5),('commission:rule:stat',6),('commission:rule:stat',8),
  ('commission:calculate',5),('commission:calculate',6),('commission:calculate',8),
  ('commission:record:stat',5),('commission:record:stat',6),('commission:record:stat',8),
  ('commission:salary:add',5),('commission:salary:add',6),('commission:salary:add',8),
  ('commission:salary:stat',5),('commission:salary:stat',6),('commission:salary:stat',8),
  ('commission:salary:adjust',5),('commission:salary:adjust',6),('commission:salary:adjust',8),
  ('commission:salary:confirm',6),('commission:salary:confirm',8),
  ('commission:salary:pay',6),('commission:salary:pay',8),
  ('commission:salary:batch-confirm',6),('commission:salary:batch-confirm',8),
  ('commission:salary:batch-pay',6),('commission:salary:batch-pay',8),
  ('commission:salary:export',5),('commission:salary:export',6),('commission:salary:export',8),
  ('commission:settlement:add',5),('commission:settlement:add',6),('commission:settlement:add',8),
  ('commission:settlement:detail',5),('commission:settlement:detail',6),('commission:settlement:detail',8),
  ('commission:settlement:stat',5),('commission:settlement:stat',6),('commission:settlement:stat',8),
  ('commission:settlement:confirm',6),('commission:settlement:confirm',8),
  ('commission:settlement:pay',6),('commission:settlement:pay',8),
  ('commission:settlement:batch-confirm',6),('commission:settlement:batch-confirm',8),
  ('commission:settlement:batch-pay',6),('commission:settlement:batch-pay',8),
  ('commission:settlement:export',5),('commission:settlement:export',6),('commission:settlement:export',8);

-- 核算 1：码数 / 绑定数
SELECT 'chk1_codes_bindings' AS chk, COUNT(DISTINCT permission_code) AS codes, COUNT(*) AS bindings FROM tmp_w;

-- 核算 2（修正）：逐角色增量，D5a 每角色 +1
SELECT 'chk2_delta_by_role_FIXED' AS chk, role_id,
       COUNT(*) AS delta_d1_only,
       COUNT(*) + CASE WHEN role_id IN (5,6,8) THEN 1 ELSE 0 END AS delta_with_d5a_FIXED
FROM tmp_w GROUP BY role_id ORDER BY role_id;

-- 核算 3（修正）：当前绑定 + D1 增量 + D5a 逐角色增量 = 绑定后合计
DROP TEMPORARY TABLE IF EXISTS tmp_d;
CREATE TEMPORARY TABLE tmp_d AS
SELECT role_id, COUNT(*) AS delta FROM tmp_w GROUP BY role_id;
SELECT 'chk3_after_cnt_FIXED' AS chk, r.id AS role_id, r.role_code,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id = r.id) AS current_cnt,
       COALESCE(d.delta, 0) AS delta_d1_only,
       COALESCE(d.delta, 0) + CASE WHEN r.id IN (5,6,8) THEN 1 ELSE 0 END AS delta_with_d5a_FIXED,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id = r.id)
         + COALESCE(d.delta, 0)
         + CASE WHEN r.id IN (5,6,8) THEN 1 ELSE 0 END AS after_cnt_FIXED,
       -- 对照：17-*.sql 原错误口径
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id = r.id)
         + COALESCE(d.delta, 0)
         + CASE WHEN r.id IN (5,6,8) THEN 3 ELSE 0 END AS after_cnt_OLD_BUGGY
FROM sys_role r LEFT JOIN tmp_d d ON d.role_id = r.id
ORDER BY r.id;

-- 核算 4：总量（总量口径 +3 是对的：1 码 × 3 角色 = 3 行）
SELECT 'chk4_total' AS chk,
       (SELECT COUNT(*) FROM sys_role_permission) AS total_now,
       (SELECT COUNT(*) FROM sys_role_permission) + (SELECT COUNT(*) FROM tmp_w) + 3 AS total_after;

-- 核算 5：33 码是否全为 0 绑定（防重复绑定）
DROP TEMPORARY TABLE IF EXISTS tmp_c;
CREATE TEMPORARY TABLE tmp_c (
  permission_code VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL PRIMARY KEY
);
INSERT INTO tmp_c (permission_code) SELECT DISTINCT permission_code FROM tmp_w;
SELECT 'chk5_already_bound_nonzero' AS chk, c.permission_code, p.id AS permission_id,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id = p.id) AS already_bound
FROM tmp_c c
LEFT JOIN sys_permission p ON p.permission_code = c.permission_code
WHERE (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id = p.id) <> 0;

-- 核算 6：commission:simulation 现状
SELECT 'chk6_simulation' AS chk,
       (SELECT COUNT(*) FROM sys_permission WHERE permission_code = 'commission:simulation') AS simulation_exists,
       (SELECT MAX(id) FROM sys_permission) AS max_permission_id,
       (SELECT COUNT(*) FROM sys_permission) AS perm_total;
