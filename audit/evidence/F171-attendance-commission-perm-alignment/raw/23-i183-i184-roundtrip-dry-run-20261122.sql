-- ============================================================
-- I183/I184 绑定 + 回滚 —— 双向闭环 DRY RUN（不落痕）
-- 用途: 验证 21-*.sql（正向）与 22-*.sql（回滚）可互相完全还原
-- 非正式工具: 一次性验证脚本，不入守门序列，不进 CI
-- 末尾 ROLLBACK，库不留痕
-- ============================================================

SET autocommit=0;
START TRANSACTION;

-- ===== 阶段 2a (D5a) =====
INSERT INTO sys_permission
  (permission_code, permission_name, permission_type, parent_id, sort_order, status, create_time)
VALUES
  ('commission:simulation', 'commission·simulation', 'API', 0, 0, 1, NOW());
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
VALUES (5, LAST_INSERT_ID()), (6, LAST_INSERT_ID()), (8, LAST_INSERT_ID());

-- ===== 阶段 2b (D1) =====
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

SELECT 'STEP1_after_bind' AS chk, COUNT(*) AS rp_total FROM sys_role_permission;
SELECT 'STEP1_byrole' AS chk, rp.role_id, COUNT(*) AS cnt
FROM sys_role_permission rp GROUP BY rp.role_id ORDER BY rp.role_id;
SELECT 'STEP1_perm_total' AS chk, COUNT(*) AS cnt FROM sys_permission;

-- ===== 模拟执行 22-*.sql 回滚 =====
DELETE FROM sys_role_permission
WHERE permission_id = (SELECT id FROM sys_permission WHERE permission_code = 'commission:simulation')
  AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1453 AND role_id IN (2, 4, 5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1452 AND role_id IN (5, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1451 AND role_id IN (2, 5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1456 AND role_id IN (5, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1458 AND role_id IN (2, 5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1459 AND role_id IN (2, 4, 5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1457 AND role_id IN (2, 4, 5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1454 AND role_id IN (2, 4, 5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1455 AND role_id IN (2, 5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1328 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1330 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1120 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1122 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1331 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1121 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1332 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1329 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1335 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1336 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1339 AND role_id IN (6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1337 AND role_id IN (6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1125 AND role_id IN (6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1333 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1126 AND role_id IN (6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1334 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1345 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1327 AND role_id IN (6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1341 AND role_id IN (6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1344 AND role_id IN (6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1343 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1342 AND role_id IN (5, 6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1340 AND role_id IN (6, 8);
DELETE FROM sys_role_permission WHERE permission_id = 1338 AND role_id IN (5, 6, 8);
DELETE FROM sys_permission WHERE permission_code = 'commission:simulation';

SELECT 'STEP2_after_rollback' AS chk, COUNT(*) AS rp_total FROM sys_role_permission;
SELECT 'STEP2_byrole' AS chk, rp.role_id, COUNT(*) AS cnt
FROM sys_role_permission rp GROUP BY rp.role_id ORDER BY rp.role_id;
SELECT 'STEP2_perm_total' AS chk, COUNT(*) AS cnt FROM sys_permission;
SELECT 'STEP2_simulation' AS chk, COUNT(*) AS cnt FROM sys_permission WHERE permission_code='commission:simulation';

ROLLBACK;
