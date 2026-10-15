-- =============================================
-- 5 角色测试用户 seed（用于 role-menu.spec.ts）
--
-- ★ 项目配置（application.yml 真实值）：
--   url      = jdbc:mysql://localhost:3307/meiyetec
--   username = root
--   password = root123456
--   port     = 3307（不是默认 3306）
--
-- ★ PowerShell 编码坑：默认 UTF-16LE 读取会乱码
--   必须显式 UTF-8：
--     [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8) | `
--       mysql -uroot -proot123456 -P3307 meiyetec
--
-- ★ 执行前必须先执行：migrate_r3_0_c_menu_role.sql
--   否则 5 个角色（CLERK/MANAGER/BOSS/OPERATOR/ADMIN）不存在，
--   本文件下半段的角色绑定会全部失败。
--
-- ★ 密码哈希说明：
--   本文件使用 admin 账号的真实 BCrypt 哈希（动态获取），
--   保证 4 个测试账号（clerk01/manager01/boss01/operator01）密码都是 '123456'。
-- =============================================

USE meiyetec;

-- ① 复用 admin 的真实 BCrypt hash 作为 4 个测试账号的密码
SET @BCRYPT_123456 = (SELECT password FROM sys_user WHERE username = 'admin' LIMIT 1);

-- 默认租户 id
SET @TENANT_ID = 1;

-- ② 插入 4 个测试用户
-- 密码统一为 '123456'（与 admin 账号共用 hash，方便 e2e 登录）
INSERT IGNORE INTO sys_user (tenant_id, username, password, real_name, phone, status, create_time, update_time)
VALUES
(@TENANT_ID, 'clerk01',    @BCRYPT_123456, '测试店员', '13800000001', 1, NOW(), NOW()),
(@TENANT_ID, 'manager01',  @BCRYPT_123456, '测试店长', '13800000002', 1, NOW(), NOW()),
(@TENANT_ID, 'boss01',     @BCRYPT_123456, '测试老板', '13800000003', 1, NOW(), NOW()),
(@TENANT_ID, 'operator01', @BCRYPT_123456, '测试运营', '13800000004', 1, NOW(), NOW());

-- ③ 绑定角色（CLERK / MANAGER / BOSS / OPERATOR）
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u, sys_role r
WHERE u.username = 'clerk01'    AND r.role_code = 'CLERK';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u, sys_role r
WHERE u.username = 'manager01'  AND r.role_code = 'MANAGER';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u, sys_role r
WHERE u.username = 'boss01'     AND r.role_code = 'BOSS';
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u, sys_role r
WHERE u.username = 'operator01' AND r.role_code = 'OPERATOR';

-- ④ 把 admin 账号也绑定到 ADMIN 角色（确保 ADMIN 测试用例能看到完整菜单）
-- 前提：admin 当前可能绑定的是 SUPER_ADMIN，本 INSERT IGNORE 不影响原绑定，只新增
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u, sys_role r
WHERE u.username = 'admin'      AND r.role_code = 'ADMIN';

-- ⑤ 验证
SELECT '=== 测试用户 + 角色 ===' AS '';
SELECT u.username, u.real_name, r.role_code
FROM sys_user u
LEFT JOIN sys_user_role ur ON ur.user_id = u.id
LEFT JOIN sys_role r ON r.id = ur.role_id
WHERE u.username IN ('clerk01', 'manager01', 'boss01', 'operator01', 'admin')
ORDER BY u.username, r.role_code;
