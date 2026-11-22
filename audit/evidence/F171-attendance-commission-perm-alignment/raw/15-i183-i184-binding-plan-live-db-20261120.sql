-- 证据采集脚本（只读；非正式工具；不写库、不含任何 INSERT/UPDATE/DELETE/DDL）
-- 用途：I183/I184 权限绑定方案（阶段 1）实时基线取证
-- 执行时间：2026-11-20
-- 执行方式：docker exec -i meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec < 本文件
-- 说明：参考数据全部来自当前库实时查询，不是 11-17 02:00 污染备份

SELECT '== A1 角色字典 ==' AS section;
SELECT id, role_code, role_name, status, deleted FROM sys_role ORDER BY id;

SELECT '== A2 绑定总数与分角色计数 ==' AS section;
SELECT COUNT(*) AS total FROM sys_role_permission;
SELECT rp.role_id, COUNT(*) AS cnt FROM sys_role_permission rp GROUP BY rp.role_id ORDER BY rp.role_id;

SELECT '== A3 考勤/提成码当前绑定（bind_cnt=0 即 A 类） ==' AS section;
SELECT p.id, p.permission_code, p.permission_type,
  (SELECT GROUP_CONCAT(rp.role_id ORDER BY rp.role_id) FROM sys_role_permission rp WHERE rp.permission_id=p.id) AS roles,
  (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id=p.id) AS bind_cnt
FROM sys_permission p
WHERE p.permission_code LIKE 'attendance%' OR p.permission_code LIKE 'commission%'
ORDER BY p.permission_code;

SELECT '== A4 参考码实时 role 集合 ==' AS section;
SELECT p.permission_code, GROUP_CONCAT(DISTINCT rp.role_id ORDER BY rp.role_id) AS roles
FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id
WHERE p.permission_code IN ('stock:check','stock:alert','stock:export','pos:order:list','pos:order:add',
  'pos:order:pay','pos:refund','pos:order:void','pos:refund:audit:store','booking:detail','booking:list',
  'booking:stat','booking:edit','booking:delete','booking:checkin','booking:export','employee:list',
  'commission:rule:list','commission:record:list','commission:salary:list','commission:settlement:list',
  'crm:follow-up','biz:service-item:edit','report:export:list','sys:config:edit','sys:log:export')
GROUP BY p.permission_code ORDER BY p.permission_code;

SELECT '== A5 白名单 x 库内状态交叉核对（预期：33 码、already_bound 全 0） ==' AS section;
SELECT p.permission_code, p.id AS permission_id,
       GROUP_CONCAT(t.role_id ORDER BY t.role_id) AS expected_roles, COUNT(*) AS n,
       SUM(CASE WHEN rp.id IS NOT NULL THEN 1 ELSE 0 END) AS already_bound
FROM (VALUES ROW('attendance:check',2), ROW('attendance:check',4), ROW('attendance:check',5), ROW('attendance:check',6), ROW('attendance:check',8), ROW('attendance:list',2), ROW('attendance:list',4), ROW('attendance:list',5), ROW('attendance:list',6), ROW('attendance:list',8), ROW('attendance:stat',2), ROW('attendance:stat',5), ROW('attendance:stat',6), ROW('attendance:stat',8), ROW('attendance:edit',2), ROW('attendance:edit',5), ROW('attendance:edit',8), ROW('attendance:config:list',2), ROW('attendance:config:list',5), ROW('attendance:config:list',8), ROW('attendance:config:edit',2), ROW('attendance:config:edit',5), ROW('attendance:config:edit',8), ROW('attendance:leave:submit',2), ROW('attendance:leave:submit',4), ROW('attendance:leave:submit',5), ROW('attendance:leave:submit',6), ROW('attendance:leave:submit',8), ROW('attendance:leave:list',2), ROW('attendance:leave:list',4), ROW('attendance:leave:list',5), ROW('attendance:leave:list',6), ROW('attendance:leave:list',8), ROW('attendance:leave:approve',2), ROW('attendance:leave:approve',5), ROW('attendance:leave:approve',6), ROW('attendance:leave:approve',8), ROW('commission:rule:add',5), ROW('commission:rule:add',6), ROW('commission:rule:add',8), ROW('commission:rule:edit',5), ROW('commission:rule:edit',6), ROW('commission:rule:edit',8), ROW('commission:rule:delete',5), ROW('commission:rule:delete',6), ROW('commission:rule:delete',8), ROW('commission:rule:status',5), ROW('commission:rule:status',6), ROW('commission:rule:status',8), ROW('commission:rule:detail',5), ROW('commission:rule:detail',6), ROW('commission:rule:detail',8), ROW('commission:rule:stat',5), ROW('commission:rule:stat',6), ROW('commission:rule:stat',8), ROW('commission:calculate',5), ROW('commission:calculate',6), ROW('commission:calculate',8), ROW('commission:record:stat',5), ROW('commission:record:stat',6), ROW('commission:record:stat',8), ROW('commission:salary:add',5), ROW('commission:salary:add',6), ROW('commission:salary:add',8), ROW('commission:salary:stat',5), ROW('commission:salary:stat',6), ROW('commission:salary:stat',8), ROW('commission:salary:adjust',5), ROW('commission:salary:adjust',8), ROW('commission:salary:confirm',5), ROW('commission:salary:confirm',8), ROW('commission:salary:pay',5), ROW('commission:salary:pay',8), ROW('commission:salary:batch-confirm',5), ROW('commission:salary:batch-confirm',8), ROW('commission:salary:batch-pay',5), ROW('commission:salary:batch-pay',8), ROW('commission:salary:export',5), ROW('commission:salary:export',6), ROW('commission:salary:export',7), ROW('commission:salary:export',8), ROW('commission:settlement:add',5), ROW('commission:settlement:add',6), ROW('commission:settlement:add',8), ROW('commission:settlement:detail',5), ROW('commission:settlement:detail',6), ROW('commission:settlement:detail',8), ROW('commission:settlement:stat',5), ROW('commission:settlement:stat',6), ROW('commission:settlement:stat',8), ROW('commission:settlement:confirm',5), ROW('commission:settlement:confirm',8), ROW('commission:settlement:pay',5), ROW('commission:settlement:pay',8), ROW('commission:settlement:batch-confirm',5), ROW('commission:settlement:batch-confirm',8), ROW('commission:settlement:batch-pay',5), ROW('commission:settlement:batch-pay',8), ROW('commission:settlement:export',5), ROW('commission:settlement:export',6), ROW('commission:settlement:export',7), ROW('commission:settlement:export',8)) AS t(permission_code, role_id)
JOIN sys_permission p ON p.permission_code=t.permission_code
LEFT JOIN sys_role_permission rp ON rp.permission_id=p.id AND rp.role_id=t.role_id
GROUP BY p.permission_code, p.id ORDER BY p.permission_code;

SELECT '== A6 表结构与唯一键 ==' AS section;
SHOW COLUMNS FROM sys_role_permission;
SHOW INDEX FROM sys_role_permission;
SELECT VERSION() AS mysql_version;

SELECT '== A7 孤儿码确认（后端无 @RequirePermission 引用） ==' AS section;
SELECT id, permission_code, permission_type FROM sys_permission
WHERE permission_code IN ('commission:list','commission:settlement:audit');

SELECT '== A8 前端路由码缺失比对（前端 94 码，缺 23） ==' AS section;
SELECT COUNT(*) AS sys_permission_total FROM sys_permission;
SELECT fe.permission_code AS frontend_code_missing_in_db
FROM (
  SELECT 'biz:room' AS permission_code UNION ALL SELECT 'biz:service-item' UNION ALL SELECT 'commission:rule'
  UNION ALL SELECT 'commission:rule:config' UNION ALL SELECT 'commission:simulation' UNION ALL SELECT 'dcep:hardware-wallet'
  UNION ALL SELECT 'dev:access' UNION ALL SELECT 'employee:attendance' UNION ALL SELECT 'employee:attendance:config'
  UNION ALL SELECT 'employee:attendance:list' UNION ALL SELECT 'employee:attendance:stat' UNION ALL SELECT 'employee:leave'
  UNION ALL SELECT 'employee:level:evaluation' UNION ALL SELECT 'iot:device:manage' UNION ALL SELECT 'mh:consent'
  UNION ALL SELECT 'mh:visual' UNION ALL SELECT 'org:brand' UNION ALL SELECT 'org:hq' UNION ALL SELECT 'org:store'
  UNION ALL SELECT 'pay:dcep' UNION ALL SELECT 'pay:order' UNION ALL SELECT 'payment:overseas:gateway'
  UNION ALL SELECT 'resource:schedule'
) fe(permission_code)
LEFT JOIN sys_permission p ON p.permission_code = fe.permission_code
WHERE p.id IS NULL
ORDER BY fe.permission_code;
