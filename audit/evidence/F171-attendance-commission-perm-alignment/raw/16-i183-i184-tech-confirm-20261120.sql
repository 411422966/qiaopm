-- 证据采集脚本（只读；非正式工具；不写库、不含任何 INSERT/UPDATE/DELETE/DDL）
-- 用途：I183/I184 绑定方案「阶段 1 技术确认 T1–T3」的实时取证
-- 执行时间：2026-11-20
-- 执行方式：docker exec -i meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec < 本文件
-- 说明：全部为当前库实时查询，非 11-17 02:00 污染备份

SELECT '== T1 GROUP_CONCAT(DISTINCT ... ORDER BY role_id) 顺序实证 ==' AS section;
-- 目的：证明 V2 的 actual_roles 分支使用 ORDER BY role_id，集合相同则字符串全等
SELECT p.permission_code,
       GROUP_CONCAT(DISTINCT rp.role_id ORDER BY rp.role_id) AS actual_roles,
       GROUP_CONCAT(DISTINCT rp.role_id ORDER BY rp.role_id) <=> '2,4,5,6,7,8' AS matches_literal
FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code = 'employee:list'
GROUP BY p.permission_code;

SELECT '== T2 无 ORDER BY 对照组 ==' AS section;
-- 目的：若去掉 ORDER BY，两分支字符串顺序可能不一致 -> V2 会误判 FAIL
SELECT GROUP_CONCAT(DISTINCT rp.role_id) AS no_order,
       GROUP_CONCAT(DISTINCT rp.role_id ORDER BY rp.role_id) AS with_order,
       GROUP_CONCAT(DISTINCT rp.role_id) <=> GROUP_CONCAT(DISTINCT rp.role_id ORDER BY rp.role_id) AS same_string
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.permission_code = 'employee:list';

SELECT '== T3 group_concat_max_len 与 MySQL 版本 ==' AS section;
-- 目的：确认 GROUP_CONCAT 截断阈值足够容纳 33 码的 role 串（本方案最长串约 11 字符）
SELECT @@group_concat_max_len AS gcml, @@version AS mysql_version;

SELECT '== T4 33 码 permission_id 复核（供正向/回滚 SQL 常量比对） ==' AS section;
SELECT p.permission_code, p.id AS permission_id, p.permission_type
FROM sys_permission p
WHERE p.permission_code IN (
  'attendance:check','attendance:list','attendance:stat','attendance:edit',
  'attendance:config:list','attendance:config:edit','attendance:leave:submit',
  'attendance:leave:list','attendance:leave:approve',
  'commission:rule:add','commission:rule:edit','commission:rule:delete','commission:rule:status',
  'commission:rule:detail','commission:rule:stat','commission:calculate','commission:record:stat',
  'commission:salary:add','commission:salary:stat','commission:salary:adjust','commission:salary:confirm',
  'commission:salary:pay','commission:salary:batch-confirm','commission:salary:batch-pay','commission:salary:export',
  'commission:settlement:add','commission:settlement:detail','commission:settlement:stat',
  'commission:settlement:confirm','commission:settlement:pay','commission:settlement:batch-confirm',
  'commission:settlement:batch-pay','commission:settlement:export')
ORDER BY p.permission_code;
