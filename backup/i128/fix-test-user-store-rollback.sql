-- =============================================
-- I128-d 回滚：测试账号 sys_user.store_id 置回 NULL
-- 2026-11-05
-- 说明：修复前 4 个测试账号的 store_id 本就是 NULL，
--       故"回滚"= 恢复到修复前状态（NULL → DataScope 过滤回退为 -9999）。
--       本脚本仅供应急回退使用，执行后非 admin 角色将再次查不到门店数据。
-- 编码：UTF-8 无 BOM
-- 幂等：可重复执行（第二次 affected = 0）
--
-- 执行方式（PowerShell）：
--   [System.IO.File]::ReadAllText('backup\i128\fix-test-user-store-rollback.sql', [System.Text.Encoding]::UTF8) | `
--     mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 --default-character-set=utf8mb4 meiyetec
-- =============================================
SET NAMES utf8mb4;
START TRANSACTION;

SELECT '=== PRE-ROLLBACK ===' AS phase;
SELECT id, username, store_id FROM sys_user
WHERE username IN ('clerk01','manager01','boss01','operator01')
ORDER BY id;

UPDATE sys_user
SET store_id = NULL
WHERE tenant_id = 1
  AND deleted = 0
  AND username IN ('clerk01','manager01','boss01','operator01');
SELECT 'rolled_back' AS phase, ROW_COUNT() AS affected;
-- 期望 affected = 4（已是 NULL 时为 0，属正常幂等）

COMMIT;

SELECT '=== POST-ROLLBACK ===' AS phase;
SELECT id, username, store_id FROM sys_user
WHERE username IN ('clerk01','manager01','boss01','operator01','admin')
ORDER BY id;
