-- =============================================================
-- F171 考勤 + 员工提成「权限对齐」专项 —— 只读 SQL 合集
-- 非正式工具（NOT a production tool）
--   * 仅含 SELECT / SHOW，无 INSERT / UPDATE / DELETE / DDL
--   * 不进入 audit/scripts/ 正式守门序列，不接入 CI
--   * 执行方式：
--       Get-Content 00-readonly-sql-batch.sql -Encoding UTF8 |
--         docker exec -i meiyetec-mysql mysql -uroot -p*** meiyetec --table
--   * 基线：HEAD 56d59a2；调查日期 2026-11-16
-- =============================================================

-- ---------- batch2c-readonly-01.sql ----------
-- batch2-C 只读调查 · 步骤 1：表结构与基线计数（全部只读）
SELECT '--- mkt_channel_config 有效行 ---' AS section;
SELECT id, tenant_id, brand_id, channel_type, rate_limit_per_min, rate_limit_per_day, status, deleted
FROM mkt_channel_config ORDER BY tenant_id, channel_type, id;

SELECT '--- marketing_touch_log 状态分布 ---' AS section;
SELECT tenant_id, channel_type, status, COUNT(*) AS cnt,
       SUM(sent_at IS NULL) AS sent_at_null,
       SUM(retry_count) AS retry_sum
FROM marketing_touch_log
WHERE deleted = 0
GROUP BY tenant_id, channel_type, status
ORDER BY tenant_id, channel_type, status;

SELECT '--- 失败行(未计配额) 汇总 ---' AS section;
SELECT tenant_id, channel_type,
       COUNT(*) AS fail_rows,
       SUM(retry_count) AS retry_total,
       MAX(retry_count) AS retry_max
FROM marketing_touch_log
WHERE deleted = 0 AND status = 3
GROUP BY tenant_id, channel_type;

SELECT '--- 成功行按日 ---' AS section;
SELECT DATE(sent_at) AS d, tenant_id, channel_type, COUNT(*) AS ok_rows
FROM marketing_touch_log
WHERE deleted = 0 AND sent_at IS NOT NULL
GROUP BY DATE(sent_at), tenant_id, channel_type
ORDER BY d DESC LIMIT 20;

SELECT '--- birthday_send_log 结构与量 ---' AS section;
SHOW COLUMNS FROM birthday_send_log;

SELECT '--- birthday_send_log 计数 ---' AS section;
SELECT tenant_id, send_year, gift_type, COUNT(*) AS cnt
FROM birthday_send_log
GROUP BY tenant_id, send_year, gift_type
ORDER BY tenant_id, send_year;

SELECT '--- member_message 计数(站内信落地) ---' AS section;
SHOW TABLES LIKE 'member_message';
SELECT COUNT(*) AS member_message_rows FROM member_message;

SELECT '--- 海外预约(EXT-19 确认发送) 计数 ---' AS section;
SHOW TABLES LIKE 'overseas_booking';
SELECT COUNT(*) AS overseas_booking_rows FROM overseas_booking;

SELECT '--- kiosk 设备与告警相关表 ---' AS section;
SHOW TABLES LIKE 'kiosk%';


-- ---------- batch2c-readonly-02.sql ----------
-- batch2-C 只读调查 · 步骤 2：发送速率 vs 配置额度、任务品牌分布、告警/生日路径现状
SELECT '--- SMS 成功发送按分钟峰值(验证 per_min=3/5 是否生效) ---' AS section;
SELECT MINUTE(sent_at) AS m, COUNT(*) AS cnt
FROM marketing_touch_log
WHERE deleted = 0 AND sent_at IS NOT NULL AND channel_type = 'SMS'
GROUP BY HOUR(sent_at), MINUTE(sent_at)
ORDER BY cnt DESC LIMIT 10;

SELECT '--- 按小时峰值(验证 per_day=5 是否生效) ---' AS section;
SELECT HOUR(sent_at) AS h, COUNT(*) AS cnt
FROM marketing_touch_log
WHERE deleted = 0 AND sent_at IS NOT NULL AND channel_type = 'SMS'
GROUP BY HOUR(sent_at) ORDER BY cnt DESC LIMIT 10;

SELECT '--- 触达任务品牌分布 ---' AS section;
SELECT tenant_id, brand_id, channel_type, status, COUNT(*) AS tasks
FROM marketing_touch_task
WHERE deleted = 0
GROUP BY tenant_id, brand_id, channel_type, status
ORDER BY tenant_id, brand_id, channel_type;

SELECT '--- SMS 成功行对应任务品牌(关联口径复核) ---' AS section;
SELECT tt.brand_id, COUNT(*) AS sent_rows
FROM marketing_touch_log t
JOIN marketing_touch_task tt ON tt.id = t.task_id AND tt.deleted = 0
WHERE t.deleted = 0 AND t.sent_at IS NOT NULL AND t.channel_type = 'SMS'
GROUP BY tt.brand_id;

SELECT '--- kiosk_device 现状 ---' AS section;
SHOW COLUMNS FROM kiosk_device;
SELECT tenant_id, status, COUNT(*) AS devices,
       SUM(last_heartbeat IS NULL) AS never_heartbeat
FROM kiosk_device
GROUP BY tenant_id, status;

SELECT '--- kiosk_device 心跳时间分布 ---' AS section;
SELECT MIN(last_heartbeat) AS min_hb, MAX(last_heartbeat) AS max_hb, NOW() AS now_ts FROM kiosk_device;

SELECT '--- crm_member 是否有品牌/租户列(生日路径上下文) ---' AS section;
SHOW COLUMNS FROM crm_member LIKE 'brand%';
SHOW COLUMNS FROM crm_member LIKE 'tenant%';

SELECT '--- member_message 结构 ---' AS section;
SHOW COLUMNS FROM member_message;

SELECT '--- 现有 sender 实现清单(渠道) ---' AS section;
SELECT COUNT(*) AS member_message_senders FROM member_message;


-- ---------- batch2c-readonly-03.sql ----------
-- batch2-C 只读调查 · 步骤 3：各发送路径的品牌/租户上下文可获得性、告警路径量级
SELECT '--- birthday_member 规模 ---' AS section;
SELECT tenant_id, auto_send, COUNT(*) AS cnt
FROM birthday_member
GROUP BY tenant_id, auto_send;

SELECT '--- overseas_booking 结构(预约确认路径) ---' AS section;
SHOW COLUMNS FROM overseas_booking;

SELECT '--- store 表是否有 brand_id(告警路径 store_id→brand) ---' AS section;
SHOW TABLES LIKE 'crm_store';
SHOW TABLES LIKE 'pos_store';
SHOW COLUMNS FROM crm_store LIKE 'brand%';

SELECT '--- kiosk_device store_id 分布 ---' AS section;
SELECT tenant_id, store_id, COUNT(*) AS devices FROM kiosk_device GROUP BY tenant_id, store_id;

SELECT '--- 预约确认是否有落库日志表 ---' AS section;
SHOW TABLES LIKE '%confirmation%';
SHOW TABLES LIKE '%booking%';

SELECT '--- IN_APP 有效配置 configJson(告警接收人来源) ---' AS section;
SELECT id, channel_type, brand_id, LEFT(config_json, 200) AS cfg FROM mkt_channel_config WHERE deleted = 0 AND channel_type = 'IN_APP';

SELECT '--- 触达日志按 task 聚合(单任务最大发送量) ---' AS section;
SELECT task_id, COUNT(*) AS rows_cnt, SUM(sent_at IS NOT NULL) AS sent_cnt
FROM marketing_touch_log
WHERE deleted = 0
GROUP BY task_id ORDER BY rows_cnt DESC LIMIT 10;


-- ---------- batch2c-readonly-04.sql ----------
SELECT '--- pos_store 结构(告警路径 store→brand 可获得性) ---' AS section;
SHOW COLUMNS FROM pos_store;

SELECT '--- kiosk_device store_id 分布 ---' AS section;
SELECT tenant_id, store_id, COUNT(*) AS devices FROM kiosk_device GROUP BY tenant_id, store_id;

SELECT '--- 预约确认/生日 相关表 ---' AS section;
SHOW TABLES LIKE '%confirmation%';
SHOW TABLES LIKE '%booking%';
SHOW TABLES LIKE '%birthday%';

SELECT '--- IN_APP 有效配置 configJson ---' AS section;
SELECT id, channel_type, brand_id, LEFT(config_json, 300) AS cfg FROM mkt_channel_config WHERE deleted = 0 AND channel_type = 'IN_APP';

SELECT '--- 触达日志按 task 聚合(单任务发送量) ---' AS section;
SELECT task_id, COUNT(*) AS rows_cnt, SUM(sent_at IS NOT NULL) AS sent_cnt
FROM marketing_touch_log
WHERE deleted = 0
GROUP BY task_id ORDER BY rows_cnt DESC LIMIT 10;

SELECT '--- task 表 brand/brand 相关列 ---' AS section;
SHOW COLUMNS FROM marketing_touch_task LIKE 'brand%';


-- ---------- batch2c-q1.sql ----------
-- batch2-C attendance+commission permission alignment : READ-ONLY
SELECT '=== A. sys_permission attendance% / employee:attendance% / employee:leave% ===' AS s;
SELECT id, permission_code, permission_name, permission_type, status, parent_id, deleted
FROM sys_permission
WHERE permission_code LIKE 'attendance%'
   OR permission_code LIKE 'employee:attendance%'
   OR permission_code LIKE 'employee:leave%'
ORDER BY permission_code;

SELECT '=== B. sys_permission commission% ===' AS s;
SELECT id, permission_code, permission_name, permission_type, status, parent_id, deleted
FROM sys_permission
WHERE permission_code LIKE 'commission%'
ORDER BY permission_code;

SELECT '=== C. role bindings for those codes ===' AS s;
SELECT rp.role_id, r.role_code, r.role_name, rp.permission_id, p.permission_code
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.permission_code LIKE 'attendance%'
   OR p.permission_code LIKE 'employee:attendance%'
   OR p.permission_code LIKE 'employee:leave%'
   OR p.permission_code LIKE 'commission%'
ORDER BY p.permission_code, rp.role_id;

SELECT '=== D. all roles ===' AS s;
SELECT id, role_code, role_name, data_scope, status, deleted FROM sys_role ORDER BY id;

SELECT '=== E. role_permission counts ===' AS s;
SELECT r.role_code, COUNT(*) perm_cnt FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id GROUP BY r.role_code ORDER BY perm_cnt DESC;

SELECT '=== F. users + roles ===' AS s;
SELECT u.id, u.username, u.tenant_id, u.store_id, u.status, r.role_code
FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id=u.id LEFT JOIN sys_role r ON r.id=ur.role_id
ORDER BY u.id;

SELECT '=== G. sys_menu with attendance/commission route paths ===' AS s;
SELECT id, menu_name, path, component, parent_id, permission_id, visible, status FROM sys_menu
WHERE path LIKE '%attendance%' OR path LIKE '%commission%' OR path LIKE '%leave%' ORDER BY id;

SELECT '=== H. sys_menu_permission for attendance/commission ===' AS s;
SELECT smp.id, smp.permission_id, smp.route_path, p.permission_code
FROM sys_menu_permission smp JOIN sys_permission p ON p.id=smp.permission_id
WHERE smp.route_path LIKE '%attendance%' OR smp.route_path LIKE '%commission%' ORDER BY smp.route_path;

SELECT '=== I. table existence ===' AS s;
SELECT table_name, table_rows FROM information_schema.tables
WHERE table_schema='meiyetec' AND table_name IN (
 'attendance_record','attendance_config','attendance_leave_request',
 'commission_rule','commission_record','commission_salary','commission_settlement',
 'commission_settlement_batch','commission_settlement_batch_item','commission_reversal',
 'biz_commission_tier','biz_commission_condition','biz_commission_simulation',
 'commission_rule_version','commission_settlement_record','pos_employee') ORDER BY table_name;

SELECT '=== J. row counts ===' AS s;
SELECT 'attendance_record' t, COUNT(*) c FROM attendance_record
UNION ALL SELECT 'attendance_config', COUNT(*) FROM attendance_config
UNION ALL SELECT 'attendance_leave_request', COUNT(*) FROM attendance_leave_request
UNION ALL SELECT 'commission_rule', COUNT(*) FROM commission_rule
UNION ALL SELECT 'commission_record', COUNT(*) FROM commission_record
UNION ALL SELECT 'commission_salary', COUNT(*) FROM commission_salary
UNION ALL SELECT 'commission_settlement', COUNT(*) FROM commission_settlement
UNION ALL SELECT 'commission_settlement_batch', COUNT(*) FROM commission_settlement_batch
UNION ALL SELECT 'commission_settlement_batch_item', COUNT(*) FROM commission_settlement_batch_item
UNION ALL SELECT 'commission_reversal', COUNT(*) FROM commission_reversal
UNION ALL SELECT 'commission_settlement_record', COUNT(*) FROM commission_settlement_record
UNION ALL SELECT 'pos_employee', COUNT(*) FROM pos_employee;

SELECT '=== K. attendance_record tenant/store distribution ===' AS s;
SELECT tenant_id, store_id, COUNT(*) cnt, MIN(check_date) min_d, MAX(check_date) max_d
FROM attendance_record GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== L. attendance_leave_request distribution ===' AS s;
SELECT tenant_id, store_id, status, COUNT(*) cnt FROM attendance_leave_request GROUP BY tenant_id, store_id, status ORDER BY cnt DESC LIMIT 20;

SELECT '=== M. commission_record distribution ===' AS s;
SELECT tenant_id, store_id, COUNT(*) cnt, MIN(salary_month) min_m, MAX(salary_month) max_m
FROM commission_record GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== N. commission_salary distribution ===' AS s;
SELECT tenant_id, store_id, status, COUNT(*) cnt FROM commission_salary GROUP BY tenant_id, store_id, status ORDER BY cnt DESC LIMIT 20;

SELECT '=== O. commission_settlement distribution ===' AS s;
SELECT tenant_id, store_id, status, COUNT(*) cnt FROM commission_settlement GROUP BY tenant_id, store_id, status ORDER BY cnt DESC LIMIT 20;

SELECT '=== P. pos_employee distribution ===' AS s;
SELECT tenant_id, store_id, COUNT(*) cnt FROM pos_employee GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== Q. attendance_config distribution ===' AS s;
SELECT tenant_id, store_id, COUNT(*) cnt FROM attendance_config GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== R. sys_role_data_scope ===' AS s;
SELECT * FROM sys_role_data_scope ORDER BY role_id;


-- ---------- batch2c-q2.sql ----------
SELECT '=== D. all roles ===' AS s;
SELECT id, role_code, role_name, status, deleted, tenant_id FROM sys_role ORDER BY id;

SELECT '=== E. role_permission counts ===' AS s;
SELECT r.role_code, COUNT(*) perm_cnt FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id GROUP BY r.role_code ORDER BY perm_cnt DESC;

SELECT '=== F. users + roles ===' AS s;
SELECT u.id, u.username, u.tenant_id, u.store_id, u.status, r.role_code
FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id=u.id LEFT JOIN sys_role r ON r.id=ur.role_id
ORDER BY u.id;

SELECT '=== R. sys_role_data_scope ===' AS s;
SELECT s.id, s.role_id, r.role_code, s.data_scope, s.data_scope_value FROM sys_role_data_scope s LEFT JOIN sys_role r ON r.id=s.role_id ORDER BY s.role_id;

SELECT '=== S. menu-like tables ===' AS s;
SELECT table_name FROM information_schema.tables WHERE table_schema='meiyetec' AND table_name LIKE '%menu%' ORDER BY table_name;

SELECT '=== T. sys_menu_permission schema ===' AS s;
SHOW COLUMNS FROM sys_menu_permission;

SELECT '=== U. tables existence for attendance/commission ===' AS s;
SELECT table_name, table_rows FROM information_schema.tables
WHERE table_schema='meiyetec' AND (table_name LIKE 'attendance%' OR table_name LIKE 'commission%' OR table_name LIKE '%commission%') ORDER BY table_name;

SELECT '=== V. row counts ===' AS s;
SELECT 'attendance_record' t, COUNT(*) c FROM attendance_record
UNION ALL SELECT 'attendance_config', COUNT(*) FROM attendance_config
UNION ALL SELECT 'attendance_leave_request', COUNT(*) FROM attendance_leave_request
UNION ALL SELECT 'commission_rule', COUNT(*) FROM commission_rule
UNION ALL SELECT 'commission_record', COUNT(*) FROM commission_record
UNION ALL SELECT 'commission_salary', COUNT(*) FROM commission_salary
UNION ALL SELECT 'commission_settlement', COUNT(*) FROM commission_settlement
UNION ALL SELECT 'commission_settlement_batch', COUNT(*) FROM commission_settlement_batch
UNION ALL SELECT 'commission_settlement_batch_item', COUNT(*) FROM commission_settlement_batch_item
UNION ALL SELECT 'commission_reversal', COUNT(*) FROM commission_reversal
UNION ALL SELECT 'commission_settlement_record', COUNT(*) FROM commission_settlement_record
UNION ALL SELECT 'pos_employee', COUNT(*) FROM pos_employee;

SELECT '=== K. attendance_record tenant/store distribution ===' AS s;
SELECT tenant_id, store_id, COUNT(*) cnt, MIN(check_date) min_d, MAX(check_date) max_d
FROM attendance_record GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== L. attendance_leave_request distribution ===' AS s;
SELECT tenant_id, store_id, status, COUNT(*) cnt FROM attendance_leave_request GROUP BY tenant_id, store_id, status ORDER BY cnt DESC LIMIT 20;

SELECT '=== M. commission_record distribution ===' AS s;
SELECT tenant_id, store_id, COUNT(*) cnt, MIN(salary_month) min_m, MAX(salary_month) max_m
FROM commission_record GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== N. commission_salary distribution ===' AS s;
SELECT tenant_id, store_id, status, COUNT(*) cnt FROM commission_salary GROUP BY tenant_id, store_id, status ORDER BY cnt DESC LIMIT 20;

SELECT '=== O. commission_settlement distribution ===' AS s;
SELECT tenant_id, store_id, status, COUNT(*) cnt FROM commission_settlement GROUP BY tenant_id, store_id, status ORDER BY cnt DESC LIMIT 20;

SELECT '=== P. pos_employee distribution ===' AS s;
SELECT tenant_id, store_id, COUNT(*) cnt FROM pos_employee GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== Q. attendance_config distribution ===' AS s;
SELECT tenant_id, store_id, COUNT(*) cnt FROM attendance_config GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;


-- ---------- batch2c-q3.sql ----------
SELECT '=== W1. SUPER_ADMIN role1 codes of interest ===' AS s;
SELECT rp.role_id, r.role_code, p.permission_code FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id
WHERE rp.role_id=1 AND p.permission_code IN ('booking','member','setting','*') ;

SELECT '=== W2. does SUPER_ADMIN hold attendance/commission codes? ===' AS s;
SELECT r.role_code, p.permission_code FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id
WHERE r.role_code='SUPER_ADMIN' ORDER BY p.permission_code LIMIT 40;

SELECT '=== W3. any role bound to attendance codes ===' AS s;
SELECT COUNT(*) cnt FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id
WHERE p.permission_code LIKE 'attendance%' OR p.permission_code LIKE 'employee:attendance%' OR p.permission_code LIKE 'employee:leave%';

SELECT '=== W4. employee:attendance / employee:leave / commission:rule / commission:simulation / commission:rule:config existence ===' AS s;
SELECT code, cnt FROM (
 SELECT 'employee:attendance' code, COUNT(*) cnt FROM sys_permission WHERE permission_code='employee:attendance'
 UNION ALL SELECT 'employee:attendance:list', COUNT(*) FROM sys_permission WHERE permission_code='employee:attendance:list'
 UNION ALL SELECT 'employee:attendance:stat', COUNT(*) FROM sys_permission WHERE permission_code='employee:attendance:stat'
 UNION ALL SELECT 'employee:attendance:config', COUNT(*) FROM sys_permission WHERE permission_code='employee:attendance:config'
 UNION ALL SELECT 'employee:leave', COUNT(*) FROM sys_permission WHERE permission_code='employee:leave'
 UNION ALL SELECT 'commission:rule', COUNT(*) FROM sys_permission WHERE permission_code='commission:rule'
 UNION ALL SELECT 'commission:rule:config', COUNT(*) FROM sys_permission WHERE permission_code='commission:rule:config'
 UNION ALL SELECT 'commission:simulation', COUNT(*) FROM sys_permission WHERE permission_code='commission:simulation'
 UNION ALL SELECT 'commission:settlement:audit', COUNT(*) FROM sys_permission WHERE permission_code='commission:settlement:audit'
 UNION ALL SELECT 'attendance:check', COUNT(*) FROM sys_permission WHERE permission_code='attendance:check'
) t;

SELECT '=== W5. sys_menu_permission attendance/commission routes ===' AS s;
SELECT smp.id, smp.permission_id, smp.route_path, smp.hidden, p.permission_code
FROM sys_menu_permission smp JOIN sys_permission p ON p.id=smp.permission_id
WHERE smp.route_path LIKE '%attendance%' OR smp.route_path LIKE '%commission%' OR smp.route_path LIKE '%leave%' ORDER BY smp.route_path;

SELECT '=== W6. all sys_menu_permission route->perm for reference count ===' AS s;
SELECT COUNT(*) total FROM sys_menu_permission;

SELECT '=== W7. permission_type distribution ===' AS s;
SELECT permission_type, COUNT(*) cnt FROM sys_permission GROUP BY permission_type;

SELECT '=== W8. API-type permission codes bound to zero roles (top 40) ===' AS s;
SELECT p.permission_code, p.permission_type FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id=p.id
WHERE rp.permission_id IS NULL AND p.permission_type='API' AND p.deleted=0 ORDER BY p.permission_code LIMIT 40;

SELECT '=== W9. attendance/commission API-type unbound ===' AS s;
SELECT p.id, p.permission_code, p.permission_type, p.status,
 (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id=p.id) bound_roles
FROM sys_permission p
WHERE (p.permission_code LIKE 'attendance%' OR p.permission_code LIKE 'commission%') AND p.deleted=0
ORDER BY p.permission_code;


-- ---------- batch2c-q4b.sql ----------
SELECT 'X4 无角色绑定的 attendance/commission 权限码' AS s;
SELECT p.permission_code, p.permission_type, p.create_time
FROM sys_permission p LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE rp.permission_id IS NULL
  AND (p.permission_code LIKE 'attendance%' OR p.permission_code LIKE 'commission%')
ORDER BY p.permission_type, p.permission_code;

SELECT 'X5 sys_menu_permission 中 attendance/commission 路由' AS s;
SELECT id, permission_id, route_path, hidden FROM sys_menu_permission WHERE route_path LIKE '%attendance%' OR route_path LIKE '%commission%' ORDER BY route_path;

SELECT 'X6 attendance_config 样本' AS s;
SELECT id, tenant_id, store_id, config_name FROM attendance_config;

SELECT 'X7 commission_record 样本' AS s;
SELECT id, tenant_id, store_id, employee_id, salary_month, commission_amount, status FROM commission_record ORDER BY id LIMIT 3;

SELECT 'X8 用户与角色' AS s;
SELECT u.id, u.username, u.tenant_id, u.store_id, r.role_code FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id=u.id LEFT JOIN sys_role r ON r.id=ur.role_id ORDER BY u.id;


-- ---------- batch2c-q5.sql ----------
SELECT 'V1' AS tag, p.permission_code, p.permission_type, p.create_time,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id = p.id) AS role_bindings,
       (SELECT GROUP_CONCAT(r.role_code) FROM sys_role_permission rp JOIN sys_role r ON r.id = rp.role_id WHERE rp.permission_id = p.id) AS roles
FROM sys_permission p
WHERE p.permission_code IN (
 'commission','commission:list','commission:rule:list','commission:rule:add','commission:rule:edit','commission:rule:delete',
 'commission:record:list','commission:salary:list','commission:salary:confirm','commission:salary:pay',
 'commission:settlement:list','commission:settlement:audit','commission:batch','commission:reversal',
 'commission:rule:detail','commission:rule:stat','commission:rule:status','commission:calculate',
 'commission:record:stat','commission:salary:add','commission:salary:adjust','commission:salary:stat','commission:salary:export',
 'commission:salary:batch-confirm','commission:salary:batch-pay','commission:settlement:add','commission:settlement:detail',
 'commission:settlement:confirm','commission:settlement:pay','commission:settlement:stat','commission:settlement:export',
 'commission:settlement:batch-confirm','commission:settlement:batch-pay',
 'attendance:check','attendance:list','attendance:stat','attendance:edit',
 'attendance:config:list','attendance:config:edit','attendance:leave:submit','attendance:leave:approve','attendance:leave:list',
 'employee:attendance','employee:attendance:list','employee:attendance:stat','employee:attendance:config',
 'commission:rule','commission:rule:config','commission:simulation'
)
ORDER BY p.permission_code;

SELECT 'V2' AS tag, p.permission_code, p.permission_type, p.create_time, p.deleted
FROM sys_permission p
WHERE p.permission_code LIKE 'attendance%' OR p.permission_code LIKE 'employee:attendance%'
   OR p.permission_code LIKE 'commission%' OR p.permission_code LIKE 'employee:leave%';

SELECT 'V3' AS tag, COUNT(*) AS admin_role_perm_cnt FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id WHERE r.role_code='ADMIN';
SELECT 'V4' AS tag, COUNT(*) AS manager_role_perm_cnt FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id WHERE r.role_code='MANAGER';
SELECT 'V5' AS tag, r.role_code, COUNT(*) AS cnt FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id
 JOIN sys_permission p ON p.id=rp.permission_id WHERE p.permission_code LIKE 'commission%' OR p.permission_code LIKE 'attendance%' GROUP BY r.role_code;
SELECT 'V6' AS tag, COUNT(*) AS super_admin_commission_attendance FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id
 JOIN sys_permission p ON p.id=rp.permission_id WHERE r.role_code='SUPER_ADMIN' AND (p.permission_code LIKE 'commission%' OR p.permission_code LIKE 'attendance%');


-- ---------- batch2c-q6.sql ----------
SELECT 'S1' AS tag, store_id, COUNT(*) AS cnt, COUNT(DISTINCT employee_id) AS emps FROM commission_record WHERE deleted=0 GROUP BY store_id;
SELECT 'S2' AS tag, COUNT(*) AS pos_store_cnt FROM pos_store;
SELECT 'S3' AS tag, id, store_name FROM pos_store LIMIT 10;
SELECT 'S4' AS tag, id, store_id, employee_name, status FROM pos_employee WHERE id IN (4) ;
SELECT 'S5' AS tag, COUNT(*) AS emp_cnt FROM pos_employee WHERE deleted=0;
SELECT 'S6' AS tag, store_id, COUNT(*) FROM pos_employee WHERE deleted=0 GROUP BY store_id ORDER BY cnt DESC LIMIT 10;
SELECT 'S7' AS tag, salary_month, store_id, employee_id, status, COUNT(*) FROM commission_record WHERE deleted=0 GROUP BY salary_month,store_id,employee_id,status;
SELECT 'S8' AS tag, COUNT(*) AS cfg_cnt FROM attendance_config;
SELECT 'S9' AS tag, id, tenant_id, store_id, config_name FROM attendance_config;


-- ---------- batch2c-attendance-commission-readonly.sql ----------
-- batch2-C (attendance + commission permission alignment) READ-ONLY queries
-- 非正式工具：不入 CI，不产生写操作

SELECT '=== A. sys_permission: attendance / employee:attendance / employee:leave ===' AS section;
SELECT permission_code, permission_type, status, parent_id
FROM sys_permission
WHERE permission_code LIKE 'attendance%'
   OR permission_code LIKE 'employee:attendance%'
   OR permission_code LIKE 'employee:leave%'
ORDER BY permission_code;

SELECT '=== B. sys_permission: commission% ===' AS section;
SELECT permission_code, permission_type, status, parent_id
FROM sys_permission
WHERE permission_code LIKE 'commission%'
ORDER BY permission_code;

SELECT '=== C. sys_role_permission bindings for attendance/commission codes ===' AS section;
SELECT rp.role_id, r.role_code, r.role_name, rp.permission_id, p.permission_code
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.permission_code LIKE 'attendance%'
   OR p.permission_code LIKE 'employee:attendance%'
   OR p.permission_code LIKE 'employee:leave%'
   OR p.permission_code LIKE 'commission%'
ORDER BY p.permission_code, rp.role_id;

SELECT '=== D. attendance_record sample + tenant/store distribution ===' AS section;
SELECT COUNT(*) total, COUNT(DISTINCT tenant_id) tenants, COUNT(DISTINCT store_id) stores,
       COUNT(DISTINCT employee_id) employees, MIN(check_date) min_d, MAX(check_date) max_d
FROM attendance_record;

SELECT tenant_id, store_id, COUNT(*) cnt FROM attendance_record GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== E. leave_request ===' AS section;
SELECT COUNT(*) total, COUNT(DISTINCT tenant_id) tenants FROM leave_request;
SELECT tenant_id, store_id, status, COUNT(*) cnt FROM leave_request GROUP BY tenant_id, store_id, status ORDER BY cnt DESC LIMIT 20;

SELECT '=== F. commission tables row counts ===' AS section;
SELECT 'commission_rule' t, COUNT(*) c FROM commission_rule
UNION ALL SELECT 'commission_record', COUNT(*) FROM commission_record
UNION ALL SELECT 'commission_salary', COUNT(*) FROM commission_salary
UNION ALL SELECT 'commission_settlement', COUNT(*) FROM commission_settlement
UNION ALL SELECT 'commission_condition', COUNT(*) FROM commission_condition
UNION ALL SELECT 'commission_tier', COUNT(*) FROM commission_tier
UNION ALL SELECT 'pos_employee', COUNT(*) FROM pos_employee;

SELECT '=== G. commission_record tenant/store distribution ===' AS section;
SELECT tenant_id, store_id, COUNT(*) cnt, MIN(salary_month) min_m, MAX(salary_month) max_m
FROM commission_record GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== H. commission_salary tenant/store distribution ===' AS section;
SELECT tenant_id, store_id, status, COUNT(*) cnt FROM commission_salary GROUP BY tenant_id, store_id, status ORDER BY cnt DESC LIMIT 20;

SELECT '=== I. commission_settlement tenant/store ===' AS section;
SELECT tenant_id, store_id, status, COUNT(*) cnt FROM commission_settlement GROUP BY tenant_id, store_id, status ORDER BY cnt DESC LIMIT 20;

SELECT '=== J. pos_employee tenant/store ===' AS section;
SELECT tenant_id, store_id, COUNT(*) cnt FROM pos_employee GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== K. attendance_config ===' AS section;
SELECT tenant_id, store_id, COUNT(*) cnt FROM attendance_config GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;


-- ---------- batch2c-attendance-commission-readonly-2.sql ----------
-- batch2-C part 2 READ-ONLY
SELECT '=== L. sys_role list ===' AS section;
SELECT id, role_code, role_name, status, tenant_id FROM sys_role ORDER BY id;

SELECT '=== M. all role_permission counts per permission for attendance ===' AS section;
SELECT p.permission_code, p.permission_type, p.parent_id,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id=p.id) AS role_bindings
FROM sys_permission p
WHERE p.permission_code LIKE 'attendance%' OR p.permission_code LIKE 'employee:leave%'
ORDER BY p.permission_code;

SELECT '=== N. commission codes with ZERO role bindings ===' AS section;
SELECT p.permission_code, p.permission_type FROM sys_permission p
WHERE p.permission_code LIKE 'commission%' AND p.permission_code NOT LIKE 'commission.%'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.permission_id=p.id)
ORDER BY p.permission_code;

SELECT '=== O. commission codes WITH bindings (summary) ===' AS section;
SELECT p.permission_code, p.permission_type, COUNT(rp.role_id) AS bindings
FROM sys_permission p LEFT JOIN sys_role_permission rp ON rp.permission_id=p.id
WHERE p.permission_code LIKE 'commission%' AND p.permission_code NOT LIKE 'commission.%'
GROUP BY p.id, p.permission_code, p.permission_type ORDER BY bindings DESC, p.permission_code;

SELECT '=== P. tables existence ===' AS section;
SELECT table_name, table_rows FROM information_schema.tables
WHERE table_schema='meiyetec' AND (table_name LIKE '%attendance%' OR table_name LIKE '%leave%' OR table_name LIKE 'commission%')
ORDER BY table_name;

SELECT '=== Q. attendance_record columns ===' AS section;
SHOW COLUMNS FROM attendance_record;

SELECT '=== R. attendance_record data ===' AS section;
SELECT COUNT(*) total FROM attendance_record;
SELECT COUNT(*) total FROM attendance_config;
SELECT COUNT(*) total FROM leave_request;

SELECT '=== S. commission_* counts ===' AS section;
SELECT 'commission_rule' t, COUNT(*) c FROM commission_rule
UNION ALL SELECT 'commission_record', COUNT(*) FROM commission_record
UNION ALL SELECT 'commission_salary', COUNT(*) FROM commission_salary
UNION ALL SELECT 'commission_settlement', COUNT(*) FROM commission_settlement
UNION ALL SELECT 'commission_condition', COUNT(*) FROM commission_condition
UNION ALL SELECT 'commission_tier', COUNT(*) FROM commission_tier
UNION ALL SELECT 'pos_employee', COUNT(*) FROM pos_employee;

SELECT '=== T. commission_record tenant/store ===' AS section;
SELECT tenant_id, store_id, COUNT(*) cnt FROM commission_record GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== U. commission_salary tenant/store/status ===' AS section;
SELECT tenant_id, store_id, status, COUNT(*) cnt FROM commission_salary GROUP BY tenant_id, store_id, status ORDER BY cnt DESC LIMIT 20;

SELECT '=== V. commission_settlement tenant/store/status ===' AS section;
SELECT tenant_id, store_id, status, COUNT(*) cnt FROM commission_settlement GROUP BY tenant_id, store_id, status ORDER BY cnt DESC LIMIT 20;

SELECT '=== W. pos_employee tenant/store ===' AS section;
SELECT tenant_id, store_id, COUNT(*) cnt FROM pos_employee GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== X. attendance_config tenant/store ===' AS section;
SELECT tenant_id, store_id, COUNT(*) cnt FROM attendance_config GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== Y. menu for employee module (attendance/leave children) ===' AS section;
SELECT id, permission_code, permission_name, parent_id, path, permission_type FROM sys_permission
WHERE permission_code IN ('employee','employee:attendance','employee:attendance:list','employee:attendance:stat','employee:attendance:config','employee:leave');

SELECT '=== Z. sys_user role bindings (which roles users have) ===' AS section;
SELECT r.role_code, COUNT(ur.user_id) users FROM sys_role r LEFT JOIN sys_user_role ur ON ur.role_id=r.id GROUP BY r.id, r.role_code ORDER BY r.id;


-- ---------- batch2c-attendance-commission-readonly-3.sql ----------
-- batch2-C part 3 READ-ONLY
SELECT '=== R2. attendance_leave_request count + attendance_config rows ===' AS section;
SELECT COUNT(*) leave_rows FROM attendance_leave_request;
SELECT * FROM attendance_config;

SELECT '=== S. commission_* counts ===' AS section;
SELECT 'commission_rule' t, COUNT(*) c FROM commission_rule
UNION ALL SELECT 'commission_record', COUNT(*) FROM commission_record
UNION ALL SELECT 'commission_salary', COUNT(*) FROM commission_salary
UNION ALL SELECT 'commission_settlement', COUNT(*) FROM commission_settlement
UNION ALL SELECT 'pos_employee', COUNT(*) FROM pos_employee;

SELECT '=== T. commission_record tenant/store ===' AS section;
SELECT tenant_id, store_id, COUNT(*) cnt FROM commission_record GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== U. pos_employee tenant/store ===' AS section;
SELECT tenant_id, store_id, COUNT(*) cnt FROM pos_employee GROUP BY tenant_id, store_id ORDER BY cnt DESC LIMIT 20;

SELECT '=== V. employee:attendance* / employee:leave in sys_permission ===' AS section;
SELECT id, permission_code, permission_name, parent_id, path, permission_type, status
FROM sys_permission
WHERE permission_code IN ('employee','employee:attendance','employee:attendance:list','employee:attendance:stat','employee:attendance:config','employee:leave');

SELECT '=== W. users per role ===' AS section;
SELECT r.id, r.role_code, r.role_name, (SELECT COUNT(*) FROM sys_user_role ur WHERE ur.role_id=r.id) users
FROM sys_role r ORDER BY r.id;

SELECT '=== X. sys_user table columns ===' AS section;
SHOW COLUMNS FROM sys_user;

SELECT '=== Y. attendance_leave_request columns ===' AS section;
SHOW COLUMNS FROM attendance_leave_request;

SELECT '=== Z. commission_record columns ===' AS section;
SHOW COLUMNS FROM commission_record;

