# Runtime Environment — Phase 5A

> 创建时间：2026-09-12 23:06 (UTC+8)
> Phase：5A — Runtime Verification (P0 × 7)
> 不绑定具体 commit hash（项目**非 git 仓库**，依据 preflight §0）

---

## 1. 运行时栈

| 维度 | 值 | 来源 / 命令 |
|---|---|---|
| OS | Windows 10.0.26200 | User Info |
| Shell | PowerShell | User Info |
| Workspace | `D:\QiaoPM` | User Info |
| **Git repo** | ✅ **是**（`git status` / `git log` 验证通过）| `git status` + `git log --oneline -5` |
| **Branch** | `main` | `git branch --show-current` |
| **起点 commit hash** | **`12a97ea57a98d268c864fc7c010b82b305d7bb93`**（5A 验证时） | `git rev-parse HEAD` |
| **起点 commit message** | `audit(batch-1.1-phase2): EXT-24 CRM 集成 Full-Stack Traceability Matrix` | `git log --oneline -1` |
| **working tree 状态** | ⚠ dirty（2 modified + 大量 untracked）| `git status` |

## 2. 后端服务（K1）

| 项 | 值 |
|---|---|
| 服务名 | meiyetec-backend |
| 端口 | **8080**（用户输入） |
| 状态 | ✅ 已启动（Flyway 已禁用） |
| 日志路径 | `D:\QiaoPM\backend\logs\meiyetec.2026-09-10.0.log`（最近日志） |
| 实测可达 | 待 §5 验证（curl /auth/login 或 ping） |

## 3. 前端服务（K1）

| 项 | 值 |
|---|---|
| 服务名 | meiyetec-web |
| 端口 | **5173**（Vite dev server） |
| 状态 | ✅ 已启动 |
| 路径 | `D:\QiaoPM\frontend\meiyetec-web` |

## 4. 数据库（K2）

| 项 | 值 |
|---|---|
| 引擎 | MySQL 8.0（Docker） |
| Host | `127.0.0.1` |
| Port | **3307**（非默认 3306） |
| Database | `meiyetec` |
| User / Password | `root / root123456` |
| 编码 | `utf8mb4` |
| **EXT-15 迁移状态** | ❌ **未跑**（用户决策 D5：保留 PARTIAL-BLOCKER） |
| **EXT-24 关键表状态** | 见 `db-snapshot-*.txt`（`crm_integration` ✅ / `crm_sync_log` ✅ / `crm_contact_map` ❌ 缺失） |

## 5. 测试 Token（K3）

| 角色 | 描述 | 用户名 | userId | Token |
|---|---|---|---|---|
| 平台超管 | 实际是 **admin**（userId=1）| `admin` | 1 | `eyJhbGciOiJIUzUxMiJ9.eyJ1c2VySWQiOjEsImp0aSI6IjI0MDA3MDU3NDU0NjAwLTQiLCJ1c2VybmFtZSI6ImFkbWluIiwic3ViIjoiYWRtaW4iLCJpYXQiOjE3ODkyMjUzMjksImV4cCI6MTc4OTMxMTcyOX0.EO7vMxSADfjDiwrW0vNNd_yZbq1ZB-7d_OmEhoIIfivzfRSrv8kPDikWJGPs3yO3xJqfH3P6KjXGhxKMUHhJPQ` |
| 普通租户 / 店员 | 实际是 **clerk01**（userId=900003）| `clerk01` | 900003 | `eyJhbGciOiJIUzUxMiJ9.eyJ1c2VySWQiOjkwMDAwMywianRpIjoiMjM5MjE3MzUzNDQ2MDAtMyIsInVzZXJuYW1lIjoiY2xlcmswMSIsInN1YiI6ImNsZXJrMDEiLCJpYXQiOjE3ODkyMjUyNDMsImV4cCI6MTc4OTMxMTY0M30.soamZPuUlHfFXmLny62Ziw842_VJ0Hz-xnYYCcjXMFWXIH9s118VFOjtzVz_lvhGUqEGd4Pf2TGzzUXMoX8D7Q` |

> ⚠ **用户标签 vs token 内容**：用户输入"超管 token"对应 `clerk01`，"普通租户 token"对应 `admin` — 与 token payload 实际相反。本 Phase 5A **以 token 内容为准**：
> - 超管（ALL 范围）= `admin`（userId=1）
> - 普通租户（限定范围）= `clerk01`（userId=900003）

## 6. Phase 5 启动条件对照

| # | 启动条件 | 状态 | 备注 |
|---|---|---|---|
| K1 | 后端 8080 + 前端 5173 已启动 | ✅ | 用户输入 |
| K2 | MySQL 3307/meiyetec 可连，未跑 EXT-15 | ✅ | 用户输入 + db-snapshot 确认 |
| K3 | 平台超管 + 普通租户 token 各一份 | ✅ | 见 §5（以 token 内容为准）|
| K4 | Playwright MCP 未配置 → E2E 降级 | ✅ | 用户输入：6 个 E2E 降级为手动 |
| K5 | 用户授权启动 Phase 5A（P0 7 个） | ✅ | 本次会话 |

## 7. 不会做的事（规则 6）

- ❌ 不修改任何业务代码
- ❌ 不执行 `migrate_ext_15_crm_real.sql`（D5 决策）
- ❌ 不修 `.cursor/rules/ext-24-crm-integration.mdc`（D2 决策）
- ❌ 不主动跑 Playwright（K4 未配 MCP）
- ❌ 不替代人工验收（规则 9）

## 8. 编码 / 输出约定

- PowerShell：`Set-Content -Encoding UTF8` 写入文本
- MySQL CLI：`--default-character-set=utf8mb4` 防乱码
- curl：`Invoke-RestMethod` 或 `curl.exe`（优先 `curl.exe -i` 看 header）
- 时间戳：UTC+8（中国时间）
- Evidence 路径：`audit/evidence/FXXX-<slug>/`
