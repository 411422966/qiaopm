-- ============================================================
-- 非正式工具（NOT a formal tool / 非守门脚本）
-- 用途：M1 映射键「phone 交集为 0」后的备用键只读核验
-- 场景：I186 §X.4 N11 / Q2 决策的前置查询（补充 10-m1-mapping-key-phone.sql）
-- 参数：MySQL 只读账号；执行方式 mysql -h127.0.0.1 -P3307 -uroot -p*** --batch --raw meiyetec < 本文件
-- 退出码：0 = 全部查询成功；非 0 = 客户端/连接错误
-- 失败处理：连接失败则不出结论，标 UNVERIFIED
-- 数据安全：**全部为 SELECT / SHOW**，无任何写操作
-- ============================================================
SET NAMES utf8mb4;

SELECT '=== A. sys_user 全量（5 条）===' AS s;
SELECT id, username, real_name, phone, tenant_id, store_id, status FROM sys_user WHERE deleted = 0 ORDER BY id;

SELECT '=== B. pos_employee 抽样（store_id=1，10 条）===' AS s;
SELECT id, employee_code, employee_name, phone, tenant_id, store_id, status
FROM pos_employee WHERE deleted = 0 AND store_id = 1 ORDER BY id LIMIT 10;

SELECT '=== C. 备用键 1：real_name = employee_name 交集 ===' AS s;
SELECT COUNT(*) AS matched_rows, COUNT(DISTINCT u.id) AS distinct_users, COUNT(DISTINCT e.id) AS distinct_employees
FROM sys_user u JOIN pos_employee e ON e.employee_name = u.real_name AND e.deleted = 0
WHERE u.deleted = 0 AND u.real_name IS NOT NULL AND TRIM(u.real_name) <> '';

SELECT '=== D. 备用键 2：username = employee_code 交集 ===' AS s;
SELECT COUNT(*) AS matched_rows FROM sys_user u JOIN pos_employee e ON e.employee_code = u.username AND e.deleted = 0
WHERE u.deleted = 0;

SELECT '=== E. 备用键 3：real_name LIKE employee_name 交集 ===' AS s;
SELECT COUNT(*) AS matched_rows FROM sys_user u JOIN pos_employee e ON e.employee_name LIKE CONCAT('%', u.real_name, '%') AND e.deleted = 0
WHERE u.deleted = 0 AND u.real_name IS NOT NULL AND TRIM(u.real_name) <> '';

SELECT '=== F. sys_user.phone 与 pos_employee.phone 值域形态抽样 ===' AS s;
SELECT 'sys_user' AS src, phone FROM sys_user WHERE deleted = 0 ORDER BY id;
SELECT 'pos_employee' AS src, phone FROM pos_employee WHERE deleted = 0 ORDER BY id LIMIT 12;

SELECT '=== G. phone 唯一性（各表内）===' AS s;
SELECT 'sys_user' AS t, COUNT(*) AS rows_with_phone, COUNT(DISTINCT phone) AS distinct_phone
FROM sys_user WHERE deleted = 0 AND phone IS NOT NULL AND TRIM(phone) <> ''
UNION ALL
SELECT 'pos_employee' AS t, COUNT(*) AS rows_with_phone, COUNT(DISTINCT phone) AS distinct_phone
FROM pos_employee WHERE deleted = 0 AND phone IS NOT NULL AND TRIM(phone) <> '';

SELECT '=== H. pos_employee 按 tenant/store 分布 ===' AS s;
SELECT tenant_id, store_id, COUNT(*) AS emp_cnt FROM pos_employee WHERE deleted = 0 GROUP BY tenant_id, store_id ORDER BY emp_cnt DESC;

SELECT '=== I. 结论口径：按 (tenant_id, phone) 交集再算一次 ===' AS s;
SELECT COUNT(*) AS matched_rows_tp
FROM sys_user u JOIN pos_employee e ON e.phone = u.phone AND e.tenant_id = u.tenant_id AND e.deleted = 0
WHERE u.deleted = 0 AND u.phone IS NOT NULL AND TRIM(u.phone) <> '';
