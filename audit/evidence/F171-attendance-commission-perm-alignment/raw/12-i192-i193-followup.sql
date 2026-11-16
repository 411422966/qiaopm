-- ============================================================
-- 非正式工具（NOT a formal tool / 非守门脚本）
-- 用途：I192（sys_user ↔ pos_employee 无关联）/ I193（pos_employee.tenant_id 为 NULL）的补充只读核验
-- 场景：batch2-C 第四轮 —— I186 第 1 批启动前的两项架构前置核实
-- 参数：MySQL 只读账号；执行方：mysql -h127.0.0.1 -P3307 -uroot -p*** --batch --raw meiyetec < 本文件
-- 退出码：0 = 全部查询成功；非 0 = 连接错误或语法错误
-- 失败处理：连接失败则不出结论，标 UNVERIFIED
-- 数据安全：**全部 SELECT / SHOW**，无任何写操作；不修改任何数据
-- ============================================================
SET NAMES utf8mb4;

SELECT '=== A. pos_employee 按 tenant/store/status/deleted 分布 + UT 码计数 ===' AS s;
SELECT tenant_id, store_id, status, deleted, COUNT(*) AS cnt,
       MIN(id) AS min_id, MAX(id) AS max_id,
       SUM(employee_code LIKE 'UT%') AS ut_codes,
       SUM(entry_date IS NULL) AS null_entry_date
FROM pos_employee
GROUP BY tenant_id, store_id, status, deleted
ORDER BY cnt DESC;

SELECT '=== B. 存活行（deleted=0）中 tenant_id IS NULL 的数量 ===' AS s;
SELECT COUNT(*) AS live_with_null_tenant FROM pos_employee WHERE deleted = 0 AND tenant_id IS NULL;

SELECT '=== C. 存活行按 tenant_id 分布 ===' AS s;
SELECT tenant_id, COUNT(*) AS live_cnt FROM pos_employee WHERE deleted = 0 GROUP BY tenant_id;

SELECT '=== D. tenant_id IS NULL 行样本（前 5）===' AS s;
SELECT id, employee_code, employee_name, phone, tenant_id, store_id, status, deleted, create_time, entry_date, salary
FROM pos_employee WHERE tenant_id IS NULL ORDER BY id LIMIT 5;

SELECT '=== E. tenant_id IS NULL 行的 create_time 区间（判定是否批量生成）===' AS s;
SELECT MIN(create_time) AS min_ct, MAX(create_time) AS max_ct, COUNT(*) AS c
FROM pos_employee WHERE tenant_id IS NULL;

SELECT '=== F. 存活且 tenant_id=1 的员工全量 ===' AS s;
SELECT id, employee_code, employee_name, phone, tenant_id, store_id, status, create_time
FROM pos_employee WHERE deleted = 0 AND tenant_id = 1 ORDER BY id;

SELECT '=== G. 业务表行数（是否有人引用 UT 员工）===' AS s;
SELECT (SELECT COUNT(*) FROM commission_salary) AS commission_salary,
       (SELECT COUNT(*) FROM commission_settlement) AS commission_settlement,
       (SELECT COUNT(*) FROM commission_settlement_record) AS commission_settlement_record,
       (SELECT COUNT(*) FROM attendance_record) AS attendance_record,
       (SELECT COUNT(*) FROM attendance_leave_request) AS attendance_leave_request,
       (SELECT COUNT(*) FROM commission_record) AS commission_record;

SELECT '=== H. commission_record 的员工引用分布 ===' AS s;
SELECT COUNT(*) AS total, COUNT(DISTINCT employee_id) AS distinct_emps,
       SUM(employee_id BETWEEN 219 AND 423) AS ut_range_refs,
       SUM(tenant_id IS NULL) AS null_tenant_rows,
       COUNT(DISTINCT store_id) AS stores
FROM commission_record;

SELECT '=== I. 存活员工按门店分布 ===' AS s;
SELECT store_id, COUNT(*) AS live_emps, SUM(tenant_id IS NULL) AS null_tenant, SUM(tenant_id = 1) AS tenant1
FROM pos_employee WHERE deleted = 0 GROUP BY store_id;

SELECT '=== J. pos_store 列表（门店维度是否有第二个租户）===' AS s;
SELECT id, store_name, tenant_id FROM pos_store ORDER BY id LIMIT 10;

SELECT '=== K. sys_user 是否存在员工关联列 ===' AS s;
SELECT column_name, data_type FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'sys_user'
  AND (column_name LIKE '%employee%' OR column_name LIKE '%staff%' OR column_name LIKE '%pos%')
ORDER BY column_name;

SELECT '=== L. pos_employee 是否存在用户关联列 ===' AS s;
SELECT column_name, data_type FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'pos_employee'
  AND (column_name LIKE '%user%' OR column_name LIKE '%account%' OR column_name LIKE '%login%')
ORDER BY column_name;

SELECT '=== M. sys_user 全量（账号性质判定）===' AS s;
SELECT id, username, real_name, phone, tenant_id, store_id, status, deleted FROM sys_user ORDER BY id;
