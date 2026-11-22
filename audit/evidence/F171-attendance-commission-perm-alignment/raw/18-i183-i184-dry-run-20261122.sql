-- ============================================================
-- I183/I184 权限绑定 — DRY RUN（D1 执行前可执行性验证）
-- 用途: 在事务内执行 §1.3.2 + §1.3.4，核验行数后 ROLLBACK，库不留痕
-- 非正式工具: 一次性验证脚本，不入守门序列，不进 CI
-- 执行方式: docker exec -i meiyetec-mysql mysql -uroot -proot123456 meiyetec < 本文件
-- 注意: 本脚本末尾 ROLLBACK，任何情况都不提交
-- ============================================================

SET autocommit=0;
START TRANSACTION;

-- 阶段 2a（D5a）: 新增 commission:simulation 权限码
INSERT INTO sys_permission
  (permission_code, permission_name, permission_type, parent_id, sort_order, status, create_time)
VALUES
  ('commission:simulation', 'commission·simulation', 'API', 0, 0, 1, NOW());

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
VALUES (5, LAST_INSERT_ID()), (6, LAST_INSERT_ID()), (8, LAST_INSERT_ID());

-- 阶段 2b（D1）: 33 码 / 100 个绑定
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1453), (4, 1453), (5, 1453), (6, 1453), (8, 1453);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1452), (8, 1452);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1451), (5, 1451), (6, 1451), (8, 1451);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1456), (8, 1456);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1458), (5, 1458), (6, 1458), (8, 1458);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1459), (4, 1459), (5, 1459), (6, 1459), (8, 1459);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1457), (4, 1457), (5, 1457), (6, 1457), (8, 1457);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1454), (4, 1454), (5, 1454), (6, 1454), (8, 1454);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 1455), (5, 1455), (6, 1455), (8, 1455);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1328), (6, 1328), (8, 1328);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1330), (6, 1330), (8, 1330);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1120), (6, 1120), (8, 1120);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1122), (6, 1122), (8, 1122);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1331), (6, 1331), (8, 1331);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1121), (6, 1121), (8, 1121);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1332), (6, 1332), (8, 1332);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1329), (6, 1329), (8, 1329);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1335), (6, 1335), (8, 1335);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1336), (6, 1336), (8, 1336);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1339), (8, 1339);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1337), (8, 1337);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1125), (8, 1125);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1333), (6, 1333), (8, 1333);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1126), (8, 1126);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1334), (6, 1334), (8, 1334);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1345), (6, 1345), (8, 1345);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1327), (8, 1327);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1341), (8, 1341);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1344), (8, 1344);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1343), (6, 1343), (8, 1343);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1342), (6, 1342), (8, 1342);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (6, 1340), (8, 1340);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (5, 1338), (6, 1338), (8, 1338);

-- ==== V1 总量与分角色计数 ====
SELECT 'V1_total' AS chk, COUNT(*) AS val FROM sys_role_permission;
SELECT 'V1_byrole' AS chk, rp.role_id, r.role_code, COUNT(*) AS val
FROM sys_role_permission rp LEFT JOIN sys_role r ON r.id = rp.role_id
GROUP BY rp.role_id, r.role_code ORDER BY rp.role_id;

-- ==== V5 逐码绑定覆盖（本批次 34 码）====
SELECT 'V5' AS chk, p.permission_code, p.id AS permission_id, COUNT(rp.id) AS bind_cnt
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
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
  'commission:settlement:stat','commission:simulation')
GROUP BY p.permission_code, p.id ORDER BY p.permission_code;

-- ==== 孤儿码确认未被误绑 ====
SELECT 'orphan' AS chk, p.permission_code, p.id, COUNT(rp.id) AS bind_cnt
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code IN ('commission:list','commission:settlement:audit')
GROUP BY p.permission_code, p.id;

-- ==== ROLLBACK：库不留痕 ====
ROLLBACK;
