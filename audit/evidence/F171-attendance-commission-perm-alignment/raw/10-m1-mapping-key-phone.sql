-- ============================================================
-- 非正式工具（NOT a formal tool / 非守门脚本）
-- 用途：M1（sys_user.employee_id 映射）候选映射键「手机号」可行性只读核验
-- 场景：I186 §X.4 N11 / I191 / I189 决策 Q2 的前置查询
-- 参数：MySQL 只读账号；执行方式 mysql -h127.0.0.1 -P3307 -uroot -p*** --batch --raw meiyetec < 本文件
-- 退出码：0 = 全部查询成功；非 0 = 客户端/连接错误
-- 失败处理：连接失败则不出结论，标 UNVERIFIED，不得以空结果推断「无交集」
-- 数据安全：**全部为 SELECT / SHOW**，无任何写操作；无 DELETE/UPDATE/INSERT/DDL
-- ============================================================
SET NAMES utf8mb4;

SELECT '=== 0. 表结构 ===' AS s;
SHOW COLUMNS FROM pos_employee;
SHOW COLUMNS FROM sys_user;

SELECT '=== 1. 规模与 phone 覆盖率 ===' AS s;
SELECT 'sys_user' AS t, COUNT(*) AS total,
       SUM(CASE WHEN phone IS NOT NULL AND TRIM(phone) <> '' THEN 1 ELSE 0 END) AS with_phone
FROM sys_user WHERE deleted = 0
UNION ALL
SELECT 'pos_employee' AS t, COUNT(*) AS total,
       SUM(CASE WHEN phone IS NOT NULL AND TRIM(phone) <> '' THEN 1 ELSE 0 END) AS with_phone
FROM pos_employee WHERE deleted = 0;

SELECT '=== 2. 交集（sys_user.phone = pos_employee.phone）===' AS s;
SELECT COUNT(*) AS matched_rows,
       COUNT(DISTINCT u.id) AS distinct_users,
       COUNT(DISTINCT e.id) AS distinct_employees,
       COUNT(DISTINCT u.phone) AS distinct_phones,
       SUM(CASE WHEN u.tenant_id <> e.tenant_id THEN 1 ELSE 0 END) AS cross_tenant_rows,
       SUM(CASE WHEN u.store_id <> e.store_id THEN 1 ELSE 0 END) AS cross_store_rows
FROM sys_user u
JOIN pos_employee e ON e.phone = u.phone AND e.deleted = 0
WHERE u.deleted = 0 AND u.phone IS NOT NULL AND TRIM(u.phone) <> '';

SELECT '=== 3. 冲突 A：一个 phone 对多条 pos_employee ===' AS s;
SELECT e.phone, COUNT(*) AS emp_cnt, GROUP_CONCAT(e.id ORDER BY e.id) AS employee_ids,
       GROUP_CONCAT(e.employee_name ORDER BY e.id) AS employee_names
FROM pos_employee e
WHERE e.deleted = 0 AND e.phone IS NOT NULL AND TRIM(e.phone) <> ''
GROUP BY e.phone HAVING emp_cnt > 1 ORDER BY emp_cnt DESC, e.phone LIMIT 50;

SELECT '=== 4. 冲突 B：一个 phone 对多条 sys_user ===' AS s;
SELECT u.phone, COUNT(*) AS user_cnt, GROUP_CONCAT(u.id ORDER BY u.id) AS user_ids,
       GROUP_CONCAT(u.username ORDER BY u.id) AS usernames
FROM sys_user u
WHERE u.deleted = 0 AND u.phone IS NOT NULL AND TRIM(u.phone) <> ''
GROUP BY u.phone HAVING user_cnt > 1 ORDER BY user_cnt DESC, u.phone LIMIT 50;

SELECT '=== 5. 唯一性判定（每个 sys_user 命中几条员工）===' AS s;
SELECT hit_cnt, COUNT(*) AS user_cnt FROM (
  SELECT u.id, (SELECT COUNT(*) FROM pos_employee e WHERE e.phone = u.phone AND e.deleted = 0) AS hit_cnt
  FROM sys_user u
  WHERE u.deleted = 0 AND u.phone IS NOT NULL AND TRIM(u.phone) <> ''
) t GROUP BY hit_cnt ORDER BY hit_cnt;

SELECT '=== 6. 覆盖度（按用户）===' AS s;
SELECT COUNT(*) AS users_with_phone,
       SUM(CASE WHEN EXISTS (SELECT 1 FROM pos_employee e WHERE e.phone = u.phone AND e.deleted = 0) THEN 1 ELSE 0 END) AS users_matched,
       SUM(CASE WHEN NOT EXISTS (SELECT 1 FROM pos_employee e WHERE e.phone = u.phone AND e.deleted = 0) THEN 1 ELSE 0 END) AS users_unmatched
FROM sys_user u
WHERE u.deleted = 0 AND u.phone IS NOT NULL AND TRIM(u.phone) <> '';

SELECT '=== 7. 交集明细（逐条）===' AS s;
SELECT u.id AS user_id, u.username, u.tenant_id AS user_tenant, u.store_id AS user_store,
       u.phone, e.id AS employee_id, e.employee_code, e.employee_name,
       e.tenant_id AS emp_tenant, e.store_id AS emp_store, e.status AS emp_status,
       (u.tenant_id = e.tenant_id) AS same_tenant, (u.store_id = e.store_id) AS same_store
FROM sys_user u
JOIN pos_employee e ON e.phone = u.phone AND e.deleted = 0
WHERE u.deleted = 0 AND u.phone IS NOT NULL AND TRIM(u.phone) <> ''
ORDER BY u.id, e.id;

SELECT '=== 8. 未命中用户清单（phone 指向不存在员工）===' AS s;
SELECT u.id AS user_id, u.username, u.tenant_id, u.store_id, u.phone, u.status
FROM sys_user u
WHERE u.deleted = 0 AND u.phone IS NOT NULL AND TRIM(u.phone) <> ''
  AND NOT EXISTS (SELECT 1 FROM pos_employee e WHERE e.phone = u.phone AND e.deleted = 0)
ORDER BY u.id;

SELECT '=== 9. 无 phone 的 sys_user（无法用 phone 映射）===' AS s;
SELECT u.id AS user_id, u.username, u.tenant_id, u.store_id, u.real_name, u.status
FROM sys_user u
WHERE u.deleted = 0 AND (u.phone IS NULL OR TRIM(u.phone) = '')
ORDER BY u.id;

SELECT '=== 10. 备用映射键抽样：employee_code 与 username 是否同域 ===' AS s;
SELECT COUNT(*) AS emp_total,
       SUM(CASE WHEN employee_code IS NOT NULL AND TRIM(employee_code) <> '' THEN 1 ELSE 0 END) AS with_code,
       SUM(CASE WHEN employee_code IN (SELECT username FROM sys_user WHERE deleted = 0) THEN 1 ELSE 0 END) AS code_eq_username
FROM pos_employee WHERE deleted = 0;

SELECT '=== 11. sys_user 是否已存在 employee/user 关联列（若有则 M1 可免 DDL）===' AS s;
SELECT column_name, data_type FROM information_schema.columns
WHERE table_schema = 'meiyetec' AND table_name = 'sys_user'
  AND (column_name LIKE '%employee%' OR column_name LIKE '%staff%' OR column_name LIKE '%pos%')
ORDER BY column_name;

SELECT '=== 12. pos_employee 是否已存在反向关联列 ===' AS s;
SELECT column_name, data_type FROM information_schema.columns
WHERE table_schema = 'meiyetec' AND table_name = 'pos_employee'
  AND (column_name LIKE '%user%' OR column_name LIKE '%account%' OR column_name LIKE '%login%')
ORDER BY column_name;
