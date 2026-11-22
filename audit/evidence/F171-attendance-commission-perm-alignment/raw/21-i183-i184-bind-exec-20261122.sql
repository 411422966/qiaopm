-- ============================================================
-- I183/I184 权限绑定 — 正式执行脚本（D1 已批准，2026-11-22）
--
-- 执行范围:
--   阶段 2a (D5a): 新增 commission:simulation 权限码 + 绑 role 5,6,8
--   阶段 2b (D1) : 33 个码 / 100 个 (role_id, permission_id) 绑定
--
-- 表结构:
--   sys_role_permission(id, role_id, permission_id, create_time)
--   唯一键 uk_role_permission(role_id, permission_id)
--   sys_permission(id, permission_code, permission_name, permission_type, parent_id,
--                  path, icon, sort_order, status, create_time, update_time, deleted)
--
-- 数据来源: 当前开发库实时查询（2026-11-20 首查 / 2026-11-22 复核），
--           非 backup/meiyetec_20261117_0200*.sql 污染备份
--
-- 预期影响:
--   sys_permission        509 -> 510 行（+1）
--   sys_role_permission   1164 -> 1267 行（+103 = 100 + 3）
--
-- 逐角色预期（2026-11-22 dry-run 实测修正值）:
--   role 2 STORE_ADMIN  40  -> 47   (+7)
--   role 4 CLERK         50  -> 54   (+4)
--   role 5 MANAGER      278  -> 304  (+25 +1)
--   role 6 BOSS         192  -> 224  (+31 +1)
--   role 8 ADMIN        381  -> 415  (+33 +1)
--   role 1/3/7 不变
--
-- 执行方式:
--   docker exec -i meiyetec-mysql mysql -uroot -proot123456 meiyetec < 本文件
--   （单连接执行，保证 LAST_INSERT_ID() 跨步骤有效）
--
-- 回滚脚本: 21-i183-i184-rollback-20261122.sql
-- ============================================================

-- ---------- 前置检查 0：确认 33 码当前全为 0 绑定（防重复绑定）----------
SELECT 'PRECHECK_bound_nonzero' AS chk, p.permission_code, p.id AS permission_id,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id = p.id) AS already_bound
FROM sys_permission p
WHERE p.permission_code IN (
  'attendance:check','attendance:config:edit','attendance:config:list','attendance:edit',
  'attendance:leave:approve','attendance:leave:list','attendance:leave:submit','attendance:list',
  'attendance:stat','commission:calculate','commission:record:stat','commission:rule:add',
  'commission:rule:delete','commission:rule:detail','commission:rule:edit','commission:rule:stat',
  'commission:rule:status','commission:salary:add','commission:salary:adjust',
  'commission:salary:batch-confirm','commission:salary:batch-pay','commission:salary:confirm',
  'commission:salary:export','commission:salary:pay','commission:salary:stat','commission:settlement:add',
  'commission:settlement:batch-confirm','commission:settlement:batch-pay','commission:settlement:confirm',
  'commission:settlement:detail','commission:settlement:export','commission:settlement:pay',
  'commission:settlement:stat')
  AND (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id = p.id) <> 0;
-- 期望：无结果行（全部 0 绑定）。若有行则停止，不执行后续。

-- ---------- 前置检查 1：确认 commission:simulation 尚不存在 ----------
SELECT 'PRECHECK_simulation_exists' AS chk, COUNT(*) AS cnt
FROM sys_permission WHERE permission_code = 'commission:simulation';
-- 期望：0

-- ---------- 前置检查 2：确认基线总数 ----------
SELECT 'PRECHECK_total_now' AS chk, COUNT(*) AS total FROM sys_role_permission;
-- 期望：1164

-- ---------- 前置检查 3：确认 permission_id 常量仍对应预期权限码 ----------
SELECT 'PRECHECK_permission_ids' AS chk, permission_code, id
FROM sys_permission
WHERE id IN (1120,1121,1122,1125,1126,1327,1328,1329,1330,1331,1332,1333,1334,1335,1336,
             1337,1339,1340,1341,1342,1343,1344,1345,1451,1452,1453,1454,1455,1456,1457,1458,1459)
ORDER BY id;
-- 期望：32 行，permission_code 与 §1.3.2 注释逐一对应

-- ============================================================
-- 阶段 2a（D5a）：新增 commission:simulation + 绑 5,6,8
-- ============================================================
INSERT INTO sys_permission
  (permission_code, permission_name, permission_type, parent_id, sort_order, status, create_time)
VALUES
  ('commission:simulation', 'commission·simulation', 'API', 0, 0, 1, NOW());

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
VALUES (5, LAST_INSERT_ID()), (6, LAST_INSERT_ID()), (8, LAST_INSERT_ID());

-- ============================================================
-- 阶段 2b（D1）：33 码 / 100 个绑定
-- ============================================================
-- attendance:check  (permission_id=1453)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1453), (4, 1453), (5, 1453), (6, 1453), (8, 1453);
-- attendance:config:edit  (permission_id=1452)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1452), (8, 1452);
-- attendance:config:list  (permission_id=1451)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1451), (5, 1451), (6, 1451), (8, 1451);
-- attendance:edit  (permission_id=1456)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1456), (8, 1456);
-- attendance:leave:approve  (permission_id=1458)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1458), (5, 1458), (6, 1458), (8, 1458);
-- attendance:leave:list  (permission_id=1459)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1459), (4, 1459), (5, 1459), (6, 1459), (8, 1459);
-- attendance:leave:submit  (permission_id=1457)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1457), (4, 1457), (5, 1457), (6, 1457), (8, 1457);
-- attendance:list  (permission_id=1454)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1454), (4, 1454), (5, 1454), (6, 1454), (8, 1454);
-- attendance:stat  (permission_id=1455)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1455), (5, 1455), (6, 1455), (8, 1455);
-- commission:calculate  (permission_id=1328)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1328), (6, 1328), (8, 1328);
-- commission:record:stat  (permission_id=1330)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1330), (6, 1330), (8, 1330);
-- commission:rule:add  (permission_id=1120)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1120), (6, 1120), (8, 1120);
-- commission:rule:delete  (permission_id=1122)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1122), (6, 1122), (8, 1122);
-- commission:rule:detail  (permission_id=1331)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1331), (6, 1331), (8, 1331);
-- commission:rule:edit  (permission_id=1121)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1121), (6, 1121), (8, 1121);
-- commission:rule:stat  (permission_id=1332)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1332), (6, 1332), (8, 1332);
-- commission:rule:status  (permission_id=1329)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1329), (6, 1329), (8, 1329);
-- commission:salary:add  (permission_id=1335)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1335), (6, 1335), (8, 1335);
-- commission:salary:adjust  (permission_id=1336)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1336), (6, 1336), (8, 1336);
-- commission:salary:batch-confirm  (permission_id=1339)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1339), (8, 1339);
-- commission:salary:batch-pay  (permission_id=1337)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1337), (8, 1337);
-- commission:salary:confirm  (permission_id=1125)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1125), (8, 1125);
-- commission:salary:export  (permission_id=1333)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1333), (6, 1333), (8, 1333);
-- commission:salary:pay  (permission_id=1126)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1126), (8, 1126);
-- commission:salary:stat  (permission_id=1334)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1334), (6, 1334), (8, 1334);
-- commission:settlement:add  (permission_id=1345)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1345), (6, 1345), (8, 1345);
-- commission:settlement:batch-confirm  (permission_id=1327)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1327), (8, 1327);
-- commission:settlement:batch-pay  (permission_id=1341)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1341), (8, 1341);
-- commission:settlement:confirm  (permission_id=1344)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1344), (8, 1344);
-- commission:settlement:detail  (permission_id=1343)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1343), (6, 1343), (8, 1343);
-- commission:settlement:export  (permission_id=1342)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1342), (6, 1342), (8, 1342);
-- commission:settlement:pay  (permission_id=1340)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1340), (8, 1340);
-- commission:settlement:stat  (permission_id=1338)
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1338), (6, 1338), (8, 1338);
