-- =============================================
-- I128 战役：修复验证脚本
-- 生成日期：2026-11-05
-- 用法（必须通过 cmd，禁用 PowerShell 管道/重定向）：
--   cmd /c "mysql --default-character-set=utf8mb4 -uroot -proot123456 -P3307 meiyetec < backup\i128\fix-i128-verify.sql"
--
-- 判定标准：
--   A 段 —— 逐表核对，期望全部为正确中文
--   B 段 —— 残留字面 ? 扫描，核心 10 项期望全部 0 hits（无输出即通过）
--   C 段 —— 残留 GBK 误解码扫描，核心 6 项期望全部 0 hits（无输出即通过）
--   D 段 —— 全库残留总览（供 I128-b 后续批次跟进，不作为本批阻塞项）
-- =============================================

USE meiyetec;

-- ---------- A. 修复后逐表核对 ----------
SELECT '=== sys_user ===' AS '';
SELECT id, username, tenant_id, real_name, HEX(real_name) AS hex_real_name
  FROM sys_user
 WHERE username IN ('admin','clerk01','manager01','boss01','operator01')
 ORDER BY id;

SELECT '=== sys_role ===' AS '';
SELECT id, role_code, role_name, description FROM sys_role ORDER BY id;

SELECT '=== sys_permission (11 target rows) ===' AS '';
SELECT id, permission_code, permission_name, HEX(permission_name) AS hex_name
  FROM sys_permission
 WHERE permission_code IN ('pos:refund:audit:l1','pos:refund:audit:l2','pos:refund:audit:l3',
                           'pos:refund:audit:rule','employee:level','employee:level:benefit',
                           'employee:level:eval','material:binding','material:binding:add',
                           'material:binding:edit','material:binding:del')
 ORDER BY id;

SELECT '=== sys_role_data_scope ===' AS '';
SELECT s.id, r.role_code, s.data_scope, s.remark
  FROM sys_role_data_scope s LEFT JOIN sys_role r ON r.id = s.role_id
 ORDER BY s.id;

SELECT '=== pos_refund_audit_rule ===' AS '';
SELECT id, tenant_id, audit_level, audit_level_name
  FROM pos_refund_audit_rule ORDER BY tenant_id, audit_level;

SELECT '=== complaint_category ===' AS '';
SELECT id, tenant_id, category_code, category_name FROM complaint_category ORDER BY id;

SELECT '=== payment_channel ===' AS '';
SELECT id, channel_code, channel_name, channel_type FROM payment_channel ORDER BY id;

SELECT '=== hq_dashboard_widget ===' AS '';
SELECT id, tenant_id, widget_code, widget_name FROM hq_dashboard_widget ORDER BY id;

SELECT '=== attendance_config ===' AS '';
SELECT id, tenant_id, store_id, config_name FROM attendance_config ORDER BY id;

-- ---------- B. 残留字面 ? 扫描（核心表，期望无任何输出） ----------
SELECT '=== 残留问号扫描（核心表，期望无输出）===' AS '';
SELECT 'sys_user.real_name' AS loc, COUNT(*) AS hits FROM sys_user
 WHERE CAST(real_name AS CHAR) LIKE '%?%' HAVING hits > 0;
SELECT 'sys_role.role_name' AS loc, COUNT(*) AS hits FROM sys_role
 WHERE CAST(role_name AS CHAR) LIKE '%?%' HAVING hits > 0;
SELECT 'sys_role.description' AS loc, COUNT(*) AS hits FROM sys_role
 WHERE CAST(description AS CHAR) LIKE '%?%' HAVING hits > 0;
SELECT 'sys_role_data_scope.remark' AS loc, COUNT(*) AS hits FROM sys_role_data_scope
 WHERE CAST(remark AS CHAR) LIKE '%?%' HAVING hits > 0;
SELECT 'sys_permission.permission_name' AS loc, COUNT(*) AS hits FROM sys_permission
 WHERE CAST(permission_name AS CHAR) LIKE '%?%' HAVING hits > 0;
SELECT 'pos_refund_audit_rule.audit_level_name' AS loc, COUNT(*) AS hits FROM pos_refund_audit_rule
 WHERE CAST(audit_level_name AS CHAR) LIKE '%?%' HAVING hits > 0;
SELECT 'complaint_category.category_name' AS loc, COUNT(*) AS hits FROM complaint_category
 WHERE CAST(category_name AS CHAR) LIKE '%?%' HAVING hits > 0;
SELECT 'payment_channel.channel_name' AS loc, COUNT(*) AS hits FROM payment_channel
 WHERE CAST(channel_name AS CHAR) LIKE '%?%' HAVING hits > 0;
SELECT 'hq_dashboard_widget.widget_name' AS loc, COUNT(*) AS hits FROM hq_dashboard_widget
 WHERE CAST(widget_name AS CHAR) LIKE '%?%' HAVING hits > 0;
SELECT 'attendance_config.config_name' AS loc, COUNT(*) AS hits FROM attendance_config
 WHERE CAST(config_name AS CHAR) LIKE '%?%' HAVING hits > 0;

-- ---------- C. 残留 GBK 误解码扫描（核心表，期望无任何输出） ----------
SELECT '=== 残留 GBK 误解码扫描（核心表，期望无输出）===' AS '';
SELECT 'sys_permission.permission_name' AS loc, COUNT(*) AS hits FROM sys_permission
 WHERE CONVERT(CAST(CONVERT(permission_name USING gbk) AS BINARY) USING utf8mb4) IS NOT NULL
   AND BINARY permission_name <> BINARY CONVERT(CAST(CONVERT(permission_name USING gbk) AS BINARY) USING utf8mb4)
HAVING hits > 0;
SELECT 'pos_refund_audit_rule.audit_level_name' AS loc, COUNT(*) AS hits FROM pos_refund_audit_rule
 WHERE CONVERT(CAST(CONVERT(audit_level_name USING gbk) AS BINARY) USING utf8mb4) IS NOT NULL
   AND BINARY audit_level_name <> BINARY CONVERT(CAST(CONVERT(audit_level_name USING gbk) AS BINARY) USING utf8mb4)
HAVING hits > 0;
SELECT 'complaint_category.category_name' AS loc, COUNT(*) AS hits FROM complaint_category
 WHERE CONVERT(CAST(CONVERT(category_name USING gbk) AS BINARY) USING utf8mb4) IS NOT NULL
   AND BINARY category_name <> BINARY CONVERT(CAST(CONVERT(category_name USING gbk) AS BINARY) USING utf8mb4)
HAVING hits > 0;
SELECT 'payment_channel.channel_name' AS loc, COUNT(*) AS hits FROM payment_channel
 WHERE CONVERT(CAST(CONVERT(channel_name USING gbk) AS BINARY) USING utf8mb4) IS NOT NULL
   AND BINARY channel_name <> BINARY CONVERT(CAST(CONVERT(channel_name USING gbk) AS BINARY) USING utf8mb4)
HAVING hits > 0;
SELECT 'hq_dashboard_widget.widget_name' AS loc, COUNT(*) AS hits FROM hq_dashboard_widget
 WHERE CONVERT(CAST(CONVERT(widget_name USING gbk) AS BINARY) USING utf8mb4) IS NOT NULL
   AND BINARY widget_name <> BINARY CONVERT(CAST(CONVERT(widget_name USING gbk) AS BINARY) USING utf8mb4)
HAVING hits > 0;
SELECT 'attendance_config.config_name' AS loc, COUNT(*) AS hits FROM attendance_config
 WHERE CONVERT(CAST(CONVERT(config_name USING gbk) AS BINARY) USING utf8mb4) IS NOT NULL
   AND BINARY config_name <> BINARY CONVERT(CAST(CONVERT(config_name USING gbk) AS BINARY) USING utf8mb4)
HAVING hits > 0;

-- 全库 GBK 误解码兜底扫描（覆盖所有表/列，含本次修复的 9 张表，
-- 防止"不含 ? 的纯误解码"被前面的针对性检查漏掉）
SELECT '=== 全库 GBK 误解码兜底扫描 ===' AS '';
SET SESSION group_concat_max_len = 4194304;
SELECT GROUP_CONCAT(
  CONCAT('SELECT ''', TABLE_NAME, '.', COLUMN_NAME, ''' AS loc, COUNT(*) AS hits FROM `', TABLE_NAME, '` WHERE CAST(`', COLUMN_NAME, '` AS CHAR) NOT LIKE ''%?%'' AND CONVERT(CAST(CONVERT(`', COLUMN_NAME, '` USING gbk) AS BINARY) USING utf8mb4) IS NOT NULL AND BINARY `', COLUMN_NAME, '` <> BINARY CONVERT(CAST(CONVERT(`', COLUMN_NAME, '` USING gbk) AS BINARY) USING utf8mb4) HAVING hits > 0')
  SEPARATOR ' UNION ALL '
) INTO @q2
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'meiyetec'
  AND DATA_TYPE IN ('varchar','char','text','mediumtext','longtext','tinytext');

PREPARE s2 FROM @q2;
EXECUTE s2;
DEALLOCATE PREPARE s2;

-- ---------- D. 全库残留总览（I128-b 跟进项，不阻塞本批） ----------
SELECT '=== D. 全库残留问号总览（I128-b 跟进）===' AS '';
SET SESSION group_concat_max_len = 4194304;
SELECT GROUP_CONCAT(
  CONCAT('SELECT ''', TABLE_NAME, '.', COLUMN_NAME, ''' AS loc, COUNT(*) AS hits FROM `', TABLE_NAME, '` WHERE CAST(`', COLUMN_NAME, '` AS CHAR) LIKE ''%?%'' HAVING hits > 0')
  SEPARATOR ' UNION ALL '
) INTO @q
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'meiyetec'
  AND DATA_TYPE IN ('varchar','char','text','mediumtext','longtext','tinytext');

PREPARE s FROM @q;
EXECUTE s;
DEALLOCATE PREPARE s;
