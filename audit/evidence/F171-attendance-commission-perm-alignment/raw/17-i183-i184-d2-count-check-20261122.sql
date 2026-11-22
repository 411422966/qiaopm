-- 非正式工具 —— 阶段 2 前一次性只读核算（不入库、不接入 CI、无退出码契约）
-- 用途：按用户 D2 拍板口径核算 33 码绑定总条数与分角色增量，供人工复核
-- 适用场景：仅 D2 拍板后的方案文档更新核对
-- 说明：本文件只含 SELECT / SHOW，不含任何 INSERT / UPDATE / DELETE / DDL
-- 单一事实来源：下方 W（白名单 VALUES），所有数字由 W 推导，勿手工维护别处

-- ============ W：D2 拍板后的 33 码 × role 明细 ============
DROP TEMPORARY TABLE IF EXISTS tmp_w;
CREATE TEMPORARY TABLE tmp_w (
  permission_code VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL,
  role_id INT NOT NULL
);
INSERT INTO tmp_w (permission_code, role_id) VALUES
  -- 考勤 9 码（D2 A 组）
  ('attendance:check',2),('attendance:check',4),('attendance:check',5),('attendance:check',6),('attendance:check',8),
  ('attendance:list',2),('attendance:list',4),('attendance:list',5),('attendance:list',6),('attendance:list',8),
  ('attendance:stat',2),('attendance:stat',5),('attendance:stat',6),('attendance:stat',8),
  ('attendance:edit',5),('attendance:edit',8),
  ('attendance:config:list',2),('attendance:config:list',5),('attendance:config:list',6),('attendance:config:list',8),
  ('attendance:config:edit',5),('attendance:config:edit',8),
  ('attendance:leave:submit',2),('attendance:leave:submit',4),('attendance:leave:submit',5),('attendance:leave:submit',6),('attendance:leave:submit',8),
  ('attendance:leave:list',2),('attendance:leave:list',4),('attendance:leave:list',5),('attendance:leave:list',6),('attendance:leave:list',8),
  ('attendance:leave:approve',2),('attendance:leave:approve',5),('attendance:leave:approve',6),('attendance:leave:approve',8),
  -- 提成 24 码（D2 B 组 + 无差异码）
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

-- ============ 核算 1：码数 / 条数 ============
SELECT COUNT(DISTINCT permission_code) AS codes, COUNT(*) AS bindings FROM tmp_w;

-- ============ 核算 2：分角色增量（含 D5a commission:simulation → 5,6,8） ============
SELECT role_id,
       COUNT(*) AS delta_with_d5a,
       COUNT(*) + CASE WHEN role_id IN (5,6,8) THEN 3 ELSE 0 END AS delta_d1_only
FROM tmp_w GROUP BY role_id ORDER BY role_id;

-- ============ 核算 3：绑定后分角色合计（含 D5a；单次扫描 tmp_w 避免 MySQL 1137） ============
DROP TEMPORARY TABLE IF EXISTS tmp_d;
CREATE TEMPORARY TABLE tmp_d AS
SELECT role_id, COUNT(*) AS delta FROM tmp_w GROUP BY role_id;
SELECT r.id AS role_id, r.role_code, r.role_name,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id = r.id) AS current_cnt,
       COALESCE(d.delta, 0) AS delta_d1_only,
       COALESCE(d.delta, 0) + CASE WHEN r.id IN (5,6,8) THEN 3 ELSE 0 END AS delta_with_d5a,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id = r.id)
         + COALESCE(d.delta, 0)
         + CASE WHEN r.id IN (5,6,8) THEN 3 ELSE 0 END AS after_cnt
FROM sys_role r LEFT JOIN tmp_d d ON d.role_id = r.id
ORDER BY r.id;

-- ============ 核算 4：总行数（含 D5a） ============
SELECT (SELECT COUNT(*) FROM sys_role_permission) AS total_now,
       (SELECT COUNT(*) FROM sys_role_permission) + (SELECT COUNT(*) FROM tmp_w) + 3 AS total_after;

-- ============ 核算 5：33 码在库内是否仍全部 0 绑定（防止重复绑定） ============
DROP TEMPORARY TABLE IF EXISTS tmp_c;
CREATE TEMPORARY TABLE tmp_c (
  permission_code VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL PRIMARY KEY
);
INSERT INTO tmp_c (permission_code) SELECT DISTINCT permission_code FROM tmp_w;
SELECT c.permission_code, p.id AS permission_id,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id = p.id) AS already_bound
FROM tmp_c c
LEFT JOIN sys_permission p ON p.permission_code = c.permission_code
ORDER BY c.permission_code;
-- ============ 核算 6：commission:simulation 是否已存在 + permission_id 上限 ============
SELECT COUNT(*) AS simulation_exists FROM sys_permission WHERE permission_code = 'commission:simulation';
SELECT MAX(id) AS max_permission_id, COUNT(*) AS perm_total FROM sys_permission;
