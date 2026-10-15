# 5 角色菜单 E2E 测试一键执行

本目录提供从 DB 初始化到跑通 Playwright 测试的一站式脚本。

## 一、PowerShell 编码坑（必看）

PowerShell 默认用 **UTF-16LE** 读取文件，导致含中文的 SQL 文件报错：

```
ERROR 1064 (42000) ... near '?????????????????????????, 1)' at line 6
```

正确做法：

```powershell
# 写法 1：使用 .NET API 显式指定 UTF-8
$path = 'd:\QiaoPM\backend\src\main\resources\db\sql\migrate_r3_0_c_menu_role.sql'
[System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8) | mysql -uroot -proot123456 -P3307 meiyetec

# 写法 2：先转编码再执行（永久方案）
$path = 'd:\QiaoPM\backend\src\main\resources\db\sql\migrate_r3_0_c_menu_role.sql'
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$content | mysql -uroot -proot123456 -P3307 meiyetec
```

## 二、完整执行流程

```powershell
# 假设当前目录：D:\QiaoPM\frontend\meiyetec-web

# 1) 执行 migrate（创建 5 个角色 + 绑定权限 + 更新菜单名称）
$path = 'd:\QiaoPM\backend\src\main\resources\db\sql\migrate_r3_0_c_menu_role.sql'
[System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8) | mysql -uroot -proot123456 -P3307 meiyetec

# 2) 执行 seed（创建 4 个测试用户 + 绑定角色）
$path = 'd:\QiaoPM\backend\src\main\resources\db\sql\seed_test_roles.sql'
[System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8) | mysql -uroot -proot123456 -P3307 meiyetec

# 3) 验证（应该看到 5 个角色 + 5 个测试账号的 role_code 都有值）
mysql -uroot -proot123456 -P3307 meiyetec --execute "SELECT u.username, r.role_code FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id=u.id LEFT JOIN sys_role r ON r.id=ur.role_id WHERE u.username IN ('admin','clerk01','manager01','boss01','operator01') ORDER BY u.username"

# 4) 拿 admin 的真实 BCrypt hash（用作 clerk01 等 4 个账号的密码）
#    由于 seed 中的占位 hash 与项目实际 hash 不一致，clerk01 等账号目前无法登录。
#    解决方案：把 admin 的 hash 复制到 seed 文件中，再重跑 seed：
$hash = mysql -uroot -proot123456 -P3307 meiyetec --skip-column-names --execute "SELECT password FROM sys_user WHERE username='admin'"
# 把 $hash 替换到 seed_test_roles.sql 第 23 行的 @BCRYPT_123456，再重跑第 2 步

# 5) 跑 Playwright 测试
npx playwright test --project=role-menu
```

## 三、验证 DB 已生效

执行完 migrate 后，下面这条 SQL 必须返回 5 行：

```sql
SELECT role_code, role_name FROM sys_role
WHERE role_code IN ('CLERK','MANAGER','BOSS','OPERATOR','ADMIN')
ORDER BY FIELD(role_code, 'CLERK','MANAGER','BOSS','OPERATOR','ADMIN');
```

预期输出：

```
CLERK    | 店员
MANAGER  | 店长
BOSS     | 老板
OPERATOR | 运营
ADMIN    | 系统管理员
```

如果只有 `ADMIN`（或更少），说明 migrate 还没跑成功。

## 四、测试结果解读

```
Running 15 tests
  ✓ 5 个 L1 基础验证（登录 + 菜单非空）
  ✓ 5 个 L2 严格验证（菜单矩阵匹配 — 仅当 migrate 已执行）
  ✓ 5 个 L3 前端渲染验证（菜单标题匹配率 ≥ 30%）
```

**跳过原因（正常情况）**：
- 测试账号不存在 → 执行 seed
- migrate 未执行 → L2 自动 skip

**期望最终结果**：5 passed（L1）+ 5 passed（L2）+ 5 passed（L3）= **15 passed, 0 failed**
