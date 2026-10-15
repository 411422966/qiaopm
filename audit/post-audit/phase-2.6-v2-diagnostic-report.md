# Phase 2.6 v2 诊断报告（只读）

> **诊断时间**：2026-09-21 17:30 (UTC+8)
> **执行人**：Cursor Agent
> **范围**：Playwright E2E 认证链路诊断（不改业务代码）
> **依据**：用户指令"Cursor 诊断 → 报告 → 用户确认 → Cursor 修"

---

## 一、诊断结论（5 项关键发现）

### 1. ✅ E2E 脚本结构完整

| 检查项 | 状态 | 说明 |
|---|---|---|
| `audit/scripts/e2e/global-setup.ts` | ✅ 存在 | 登录 → 写 `.auth-state.json` + `.token.json` |
| `audit/scripts/e2e/playwright.config.ts` | ✅ 存在 | `globalSetup: require.resolve('./global-setup')` 已配 |
| `audit/scripts/e2e/auth-token.ts` | ✅ 存在 | 从 `.token.json` 读 token 供 API specs 使用 |
| 9 个 spec 文件 | ✅ 全部存在 | F007/F024/F025/F026/F027/F028 + F111-F112 |
| 配置使用绝对路径 | ✅ | `__dirname` + `resolve()`，cwd 无关 |

**结论 1**：E2E 脚本基础设施完整可用，无需新增文件。

### 2. ❌ globalSetup 配置未在 Phase 2.6 v1 报告的 config 中启用

| 配置位置 | globalSetup | storageState | 状态 |
|---|---|---|---|
| `audit/scripts/e2e/playwright.config.ts`（正确配置）| ✅ 启用 | ✅ 绝对路径 | 正确 |
| `frontend/meiyetec-web/playwright.audit.config.ts`（v1 跑）| ❌ 未启用 | ❌ 未设 | **缺失** |

**结论 2**：Phase 2.6 v1 跑的 `playwright.audit.config.ts` **没有引用 `globalSetup`**，这就是 v1 报告 11/12 失败的根因。**但正确的 config 是 `audit/scripts/e2e/playwright.config.ts`（已含 globalSetup）— v1 用错了配置。**

### 3. ❌ JWT Token 已过期（**根因 #1**）

| 字段 | 值 |
|---|---|
| 文件 | `audit/scripts/e2e/.token.json` |
| mtime | 2026-09-13 17:44:29 |
| userId | 1（admin）|
| JWT payload | `{"userId":1,"jti":"...","username":"admin","sub":"admin","iat":1789292669,"exp":1789379069}` |
| **iat 解码** | **2026-09-13 09:44:29 UTC**（17:44:29 UTC+8）|
| **exp 解码** | **2026-09-14 17:44:29 UTC+8** |
| **当前时间** | **2026-09-21 17:30 UTC+8** |
| **过期时长** | **≈ 7 天前过期** |

**结论 3**：`.auth-state.json` 里的 token 7 天前已过期。Playwright 加载 storageState 时浏览器 localStorage 里的 token 是过期的，所有受保护 API 会返回 401。

### 4. ❌ 后端服务已停止（**根因 #2**）

| 检查项 | 结果 |
|---|---|
| 8080 端口监听 | ❌ 否（Phase 2.6 v1 时 PID 3264 在监听）|
| 5173 端口监听 | ❌ 否（Phase 2.6 v1 时 PID 19872 在监听）|
| Java 进程 | ❌ 无 |
| Node 进程 | ✅ 4 个（无 java）|

**结论 4**：后端进程已被停止（VM/容器可能发生过重启，或用户手动停止）。Phase 2.6 v1 跑时服务还活着，所以 8080/5173 都可达；现在服务停了。

### 5. ⚠️ 登录接口无法直接验证（依赖运行中的后端）

- 由于后端已停止，**`/auth/login` 接口无法直接命中**
- `global-setup.ts` 配置的接口期望：
  ```
  POST /auth/login
  body: { username, password }
  resp: { code: 200, data: { token, id, username, realName } }
  ```
- `global-setup.ts` 看到的期望字段是 `body.data.token` / `body.data.id` / `body.data.username` / `body.data.realName`
- 需后端启动后才能验证 login 接口契约是否变化

---

## 二、Phase 2.6 v1 失败的根本原因（重审）

Phase 2.6 v1 报告「11/12 FAIL」的原因为：

1. ❌ **用了错误的 config**：`frontend/meiyetec-web/playwright.audit.config.ts` 该 config 没有 `globalSetup`，导致没有 token、UI 测试无法登录
2. ❌ **正确 config 存在但未使用**：`audit/scripts/e2e/playwright.config.ts` 是设计正确的（含 globalSetup + 绝对路径 storageState），但 v1 没跑这个
3. ⚠️ **额外风险（即便切到正确 config）**：`.token.json` 中的 JWT 已过期 7 天，需要 `global-setup.ts` 重跑一次才能刷新 — 但重跑需要后端可访问

---

## 三、修复路径（待用户确认）

按"加固流程：Cursor 诊断 → 报告 → 用户确认 → Cursor 修 → 报告"，**修复阶段需用户确认**，下方 3 个修复步骤：

### 修复 A：启动后端服务（启动 + 健康检查）

| 步骤 | 操作 | 预计耗时 |
|---|---|---|
| 1 | 启动 Spring Boot（`mvn spring-boot:run` 或 jar）| 30 秒（启动耗时已测 16.5 秒）|
| 2 | 验证 `127.0.0.1:3307 / meiyetec` MySQL 可达 | 5 秒 |
| 3 | 验证 8080 监听 + `/crm-integration/dashboard` 返回 200 | 10 秒 |

### 修复 B：登录接口契约验证

| 步骤 | 操作 | 预计耗时 |
|---|---|---|
| 1 | `curl POST /auth/login {username:admin, password:123456}` | 5 秒 |
| 2 | 检查返回字段：`code / data.token / data.id / data.username / data.realName` | 立即 |
| 3 | 如果字段缺失或变化 → 更新 `global-setup.ts` | 5-10 分钟（如需）|
| 4 | 重跑 `globalSetup`（Playwright 自动跑一次）| 10 秒 |

### 修复 C：用正确 config 重跑 E2E

| 步骤 | 操作 | 预计耗时 |
|---|---|---|
| 1 | `cd d:\QiaoPM\frontend\meiyetec-web` | — |
| 2 | `npx playwright test --config=../../audit/scripts/e2e/playwright.config.ts` | 3-5 分钟 |
| 3 | 收集全部 spec 通过/失败计数 | — |
| 4 | 对比 v1，输出 v2 报告 `phase-2.6-e2e-report-v2.md` | — |

---

## 四、修复工作清单

| # | 任务 | 类型 | 依赖 | 预计耗时 |
|---|---|---|---|---|
| 1 | **启动后端**（必须）| 重启 | 无 | 30-60 秒 |
| 2 | **健康检查** — `/auth/login` 返回 code=200 | 验证 | #1 | 5 秒 |
| 3 | **重新生成 `.token.json` + `.auth-state.json`**（删除旧文件 + 让 global-setup 重跑）| 修复 | #2 | 5 秒 |
| 4 | **确认 global-setup.ts 字段契约未变**（或更新）| 检查 | #2 | 5 分钟（如需更新）|
| 5 | **重跑 E2E 用正确 config** `audit/scripts/e2e/playwright.config.ts` | 跑测 | #1-4 | 3-5 分钟 |
| 6 | **输出 v2 报告** `phase-2.6-e2e-report-v2.md` | 报告 | #5 | — |

**不动业务代码**。仅启动后端服务（重启服务 ≠ 改代码）+ 修改 E2E 脚本（如需要）。

---

## 五、风险与提示

| 风险 | 说明 |
|---|---|
| **R1**: 启动后端可能与上次测试残留状态冲突 | 建议启动前确认无残留 java 进程（已确认：无） |
| **R2**: 数据库表可能被其他用户/agent 改动过 | 启动后会跑 Flyway baseline check（已存在 flyway_schema_history 跳过）|
| **R3**: `.auth-state.json` 删除后若 globalSetup 失败，Playwright 会拒绝启动 | 失败原因会清晰显示（如 login 接口 401 / 5xx） |

---

**诊断完成。等用户确认后进入修复阶段。**

**本报告不含修复动作，仅交付诊断结论。**
