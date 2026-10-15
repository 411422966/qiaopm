> ⚠️ **本文件为历史快照，已过时。**
> **最新状态见：`audit/ARCHIVE.md` + `audit/post-audit/final-post-audit-report.md`**
> **本文件保留作为审计轨迹，不反映当前状态。**

# Phase 2.6 E2E 冒烟报告

> **执行时间**：2026-09-21 17:05 (UTC+8)
> **执行人**：Cursor Agent（Phase 2.6）
> **范围**：Playwright E2E 冒烟（选 B）
> **后端**：`127.0.0.1:3307 / meiyetec`（MySQL 8.0.46）
> **前端**：http://localhost:5173
> **后端**：http://localhost:8080

---

## 一、执行摘要

| 测试套件 | 总数 | 通过 | 失败 | 通过率 |
|---|---|---|---|---|
| F007/F024/F025/F026/F027/F028（审计脚本）| 12 | 1 | 11 | 8% |
| `mh-routes.spec.ts`（MH 路由）| 4 | 4 | 0 | **100%** |
| `crm-integration.spec.ts`（CRM 字段+连接）| 9 | 0 | 9 | 0% |
| **合计** | **25** | **5** | **20** | **20%** |

**核心发现**：
- ✅ MH 模块（`/mh/visual`、`/mh/consent`、`/complication`）4/4 路由可达，无 5xx
- ❌ 所有需要登录态的 CRM 页面测试失败（`.crm-int-page` 渲染超时 → 重定向登录页）
- ❌ API 直调测试 401（未携带 Authorization header）
- ⚠️ F028 `objectMappings` 字段缺失验证：1 PASS（后端 API 测试），1 FAIL（前端 UI 测试，需登录）

---

## 二、启动状态

| 服务 | 端口 | 状态 |
|---|---|---|
| Spring Boot 后端 | 8080 | ✅ Listen（PID 3264）|
| Vite 前端 | 5173 | ✅ Listen（PID 19872）|

---

## 三、详细测试结果

### 3.1 MH 路由测试（`mh-routes.spec.ts`）

| 测试 | 结果 | 说明 |
|---|---|---|
| `/mh/visual` 路由可达 + 视觉档案页面渲染 | ✅ PASS | 路由正常停留，DOM 加载完成 |
| `/mh/consent` 路由可达 + 合规页面渲染 | ✅ PASS | 路由正常停留，DOM 加载完成 |
| `/complication` 路由可达（依赖 mh:consent 权限）| ✅ PASS | 路由正常停留，DOM 加载完成 |
| MH 模块路由无 5xx 错误 | ✅ PASS | 三个路由均无 500/502/503 |

**结论**：MH 医美模块路由全部可达，页面正常渲染，无服务端错误。

---

### 3.2 审计脚本（F007-F028，配置 `playwright.audit.config.ts`）

#### F007 — Dashboard tab Shadow Feature

| 测试 | 结果 | 说明 |
|---|---|---|
| I006: 应无 dashboard tab（UI 验证）| ❌ FAIL | `.el-tabs__nav` 超时 10s 未出现 → 页面未加载（未登录重定向）|
| I008 Fake Success 链：dashboard API | ❌ FAIL | HTTP 401 未授权（无 Bearer token）|

#### F024 — 集成列表 UI

| 测试 | 结果 | 说明 |
|---|---|---|
| 集成列表渲染 + 列名 + 状态 enum | ❌ FAIL | `.el-tabs__nav` 超时 → 未登录重定向 |

#### F025 — 新建集成表单

| 测试 | 结果 | 说明 |
|---|---|---|
| 对话框渲染 + I005 C4 字段缺失 | ❌ FAIL | `.el-tabs__nav` 超时 → 未登录重定向 |
| 填表保存 → 列表新增行 | ❌ FAIL | 同上 |

#### F026 — 同步日志 tab

| 测试 | 结果 | 说明 |
|---|---|---|
| 切到同步日志 tab + 验证 integrationId 列 | ❌ FAIL | `.el-tabs__nav` 超时 → 未登录重定向 |
| sync-log/list API 响应（I003 漂移实证）| ❌ FAIL | HTTP 401 未授权 |

#### F027 — sync 按钮 force 未传

| 测试 | 结果 | 说明 |
|---|---|---|
| 点击同步按钮 → URL 不带 `?force=1`（网络拦截）| ❌ FAIL | `.el-tabs__nav` 超时 → 未登录重定向 |
| API 直调验证 force 未传 | ❌ FAIL | HTTP 401 未授权 |
| API 直调 `force=1` 验证 | ❌ FAIL | HTTP 401 未授权 |

#### F028 — objectMappings UI 缺失

| 测试 | 结果 | 说明 |
|---|---|---|
| 新建表单无 objectMappings 字段（I005 C4）| ❌ FAIL | `.el-tabs__nav` 超时 → 未登录重定向 |
| 后端 API + DB 验证 `object_mappings` 持久化为 NULL | ✅ PASS | API 返回 code=200（数据库列已由 Phase 2.3 Batch 4 apply）|

> ⚠️ **F028 部分通过意义重大**：后端 `object_mappings` 列已存在（Phase 2.3 Batch 4 apply 成功），API `code=200` 证明 I002 DB 阻塞已解除。

---

### 3.3 CRM 集成测试（`crm-integration.spec.ts`）

| 测试 | 结果 | 说明 |
|---|---|---|
| 默认平台 SALESFORCE 显示 JWT 字段组 | ❌ FAIL | `.crm-int-page` 元素未找到（未登录重定向）|
| 切换到 HUBSPOT 显示 Private App 字段组 | ❌ FAIL | 同上 |
| 切换到 ZOHO 显示 OAuth2 字段组 | ❌ FAIL | 同上 |
| 切换到 MOCK 不显示凭据字段 | ❌ FAIL | 同上 |
| Mock 集成「测试连接」按钮 → 健康检查 | ❌ FAIL | 同上 |
| 未配置凭据的 Salesforce 集成 → 连接失败 | ❌ FAIL | API POST 返回非预期 |
| 跨租户访问 → 403/Forbidden | ❌ FAIL | 返回"未登录"而非权限错误 |
| 集成列表显示 ID/平台/店铺/状态/操作列 | ❌ FAIL | `.crm-int-page` 元素未找到 |
| 「新建集成」按钮可点击并打开对话框 | ❌ FAIL | 同上 |

---

## 四、失败根因分析

### 4.1 认证缺失（主要阻塞）

`playwright.audit.config.ts` 和 `crm-integration.spec.ts` 均**未配置全局登录流程**：

```
❌ 无 globalSetup → 无 Bearer token → 所有 API 401
❌ 前端页面 → 未登录 → 重定向 login → .el-tabs__nav / .crm-int-page 找不到
```

**已存在的认证资产**：
- `audit/scripts/e2e/.token.json`：含有效 JWT token（admin 用户，PID 1）
- `audit/scripts/e2e/global-setup.ts`：登录流程脚本（但 audit config 未引用）

**对比 `mh-routes.spec.ts`**：MH 路由通过，因为浏览器自动持有登录 cookie，无需显式 Bearer token。

### 4.2 根本原因：E2E 认证配置缺失

| 配置项 | `playwright.audit.config.ts` | `playwright.config.ts`（全局）|
|---|---|---|
| `globalSetup` | ❌ 未配置 | ✅ 配置 |
| `storageState` | ❌ 未配置 | ✅ 配置 |
| `use: { baseURL }` | ✅ | ✅ |
| 引用 `global-setup.ts` | ❌ | ✅ |

---

## 五、Post-Audit 修改验证（F028）

| 验证项 | 结果 | 说明 |
|---|---|---|
| `crm_integration.object_mappings` 列存在 | ✅ PASS | Phase 2.3 Batch 4 已 apply |
| `crm_integration/list` API code | ✅ 200 | I002 DB 阻塞已解除 |
| 前端 `objectMappings` UI 字段 | ❌ 未验证 | 需登录态（预期缺失，I005 C4）|
| `crm_sync_log` 列（含 platform）| ❌ 未验证 | 需登录态 |

---

## 六、结论与建议

### 6.1 已验证（无需登录态）

| 验证项 | 级别 | 结果 |
|---|---|---|
| MH 模块路由可达（3 路由）| L4 路由可达 | ✅ 4/4 PASS |
| 后端 `crm_integration.object_mappings` 列存在 | L2 DB schema | ✅ 列已到位 |
| 后端 `crm_integration/list` API code=200 | L3 API | ✅ 200 |

### 6.2 需登录态才能验证（L5b 未覆盖）

| 验证项 | 阻塞原因 | 修复方案 |
|---|---|---|
| F006/F007 Dashboard tab UI | 无 `globalSetup` | 给 audit config 加 `globalSetup` |
| F024 集成列表 UI | 同上 | 同上 |
| F025 新建表单 UI | 同上 | 同上 |
| F026 同步日志 tab | 同上 | 同上 |
| F027 sync 按钮 force 未传 | 同上 | 同上 |
| F028 objectMappings UI | 同上 | 同上 |
| CRM 集成字段动态切换 | 同上 | 同上 |
| CRM 测试连接端点 | 无 Bearer token | 加 Authorization header |

### 6.3 建议修复（不加登录态的影响）

**立即可做**（不改业务代码）：
1. 给 `playwright.audit.config.ts` 加 `globalSetup` 引用
2. 给 `crm-integration.spec.ts` 加登录流程或复用 `storageState`
3. 重新跑 E2E，获取 L5b 级别证据

**修复后的预期通过率**：
- F007（Shadow）：预期 PASS（验证 tab 缺失，无 dashboard）
- F024（列表）：预期 PASS（验证列名 + 状态 enum）
- F025（新建表单）：预期 PASS（验证字段缺失）
- F026（同步日志）：预期 PASS（验证 integrationId 列存在 + platform 列缺失）
- F027（sync 按钮）：预期 PASS（验证 URL 无 `force=1`）
- F028（objectMappings）：预期 PASS（验证字段缺失 + DB 列为 null）
- CRM 字段切换：预期 PASS（验证平台切换 + 测试连接）

---

## 七、Phase 2.6 与历史结果对比

| 指标 | Phase 5B（2026-09-13）| Phase 2.6（2026-09-21）| Delta |
|---|---|---|---|
| MH 路由（/mh/visual/consent）| 未测试 | ✅ 4/4 PASS | 新增 |
| CRM 页面 UI | ❌（未登录）| ❌（未登录）| 无变化 |
| CRM API（无 auth）| ❌ 401 | ❌ 401 | 无变化 |
| `crm_integration.object_mappings` 列 | ❌ 缺失（I002）| ✅ 已到位 | **改善** |
| `crm_integration/list` code | ❌ 500（I002）| ✅ 200 | **改善** |

**改善确认**：Phase 2.3 Batch 4（`crm_integration` 9 列 + `crm_sync_log` 3 列）已 apply，I002 DB 阻塞已解除。

---

## 八、剩余 Issue 与 E2E 证据缺口

| Issue | E2E 证据 | 状态 |
|---|---|---|
| **I006**（Dashboard tab 缺失）| ❌ 缺 UI E2E（需登录）| UNVERIFIED |
| **I005-C1**（toggle enum PAUSED vs DISABLED）| ❌ 缺 UI E2E（需登录）| UNVERIFIED |
| **I005-C2**（syncNow 未传 `force=1`）| ❌ 缺 UI E2E（需登录）| UNVERIFIED |
| **I005-C4**（新建表单缺字段）| ❌ 缺 UI E2E（需登录）| UNVERIFIED |
| **I003**（integrationId 列漂移）| ❌ 缺 API 验证（需登录）| UNVERIFIED |
| **I016**（sync log 缺 platform 列）| ❌ 缺 UI E2E（需登录）| UNVERIFIED |
| **I082**（InAppSender `member_id` 违例）| ❌ 需运行期日志验证 | UNVERIFIED |

---

**本报告为只读冒烟结果，不执行任何操作。E2E 证据缺口（需登录态）建议通过修复 Playwright 配置或手动浏览器验收补足。**
