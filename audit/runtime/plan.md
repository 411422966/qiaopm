# Phase 4 — Runtime Verification Plan（Batch 1.1）

> **创建时间**：2026-09-12 22:04 (UTC+8)
> **Phase**：4 — Runtime Verification Plan
> **范围**：Batch 1.1 — EXT-24 CRM 集成（30 个 Feature）
> **Batch 决策回顾**：
> - D1 = 不加 dashboard tab / edit 按钮（I006/I007 登记）
> - D2 = 不修 integrationId 漂移（I003 保留）
> - D3 = 不修 enum/force/表单字段漂移（I005 保留）
> - D4 = I008/I009 都不修（登记）
> - D5 = I002 维持 PARTIAL-BLOCKER（不跑 `migrate_ext_15_crm_real.sql`）
> - **Batch 1.1 非重构批次** → Phase 3.5 Diff Audit 跳过（Batch 1.5 MKT 重构再做）
> - **Playwright MCP 未配置** → 标 "Playwright E2E" 的 Feature 在 Phase 5 降级为三步走（详见 §四）
> - **不受保护页面干扰**：Batch 1.1 不涉及 `/dashboard` / `/mh/visual` / `/mh/consent`
> - **规则**：审计阶段不修改业务代码（规则 6）→ Phase 4 仅记录验证策略，不进 Phase 5 执行

---

## 〇、运行时验证总览

### 0.1 验证工具矩阵

| 工具 | 适用 | 状态 |
|---|---|---|
| **curl / PowerShell `Invoke-RestMethod`** | API-only 验证 | ✅ 可用（Phase 5 用户跑）|
| **MySQL CLI** / **MySQL MCP** | DB-only 验证 | ✅ 可用（按 preflight §8.3 配置）|
| **Playwright MCP** | UI E2E | ❌ **未配置** → 降级方案 |
| **Vitest / JUnit** | 单元测试 | ⏸️ 仅 L2 不进 Phase 4/5 计划 |
| **手动浏览器（用户执行）** | UI E2E 降级 | ✅ Phase 5 用户跑 Playwright 脚本 |

### 0.2 降级方案（Playwright MCP 未配置）

对任何标 "Playwright E2E" 的 Feature，Phase 5 降级为：

1. **Cursor 生成 Playwright 脚本** → `audit/runtime/playwright/` 目录
2. **用户手动跑**（在本地配置 Playwright 的环境执行 `.spec.ts` 文件）
3. **结果贴回** → `audit/runtime/playwright/<feature-id>.result.md`（用户记录 pass/fail + 截图）
4. **静态抓 HTML + curl API + Vitest** 作为补充证据（不替代 E2E，但提供 L4 的部分覆盖）

### 0.3 验证方式 vs Feature 类型映射

| Feature 类型 | 主验证 | 辅验证 |
|---|---|---|
| **REST endpoint（黑盒）** | API-only（curl） | DB 断言（写后读）|
| **行为分支（toggle / 跳过 / 拒绝）**| API-only（带参数）| DB 断言（状态变化）|
| **CRUD + DB 写** | API-only + DB 断言（行存在 / 字段值）| — |
| **前端 UI** | Playwright E2E（降级）| API-only（直调后台）|
| **Adapter（外部 HTTP）**| 仅 healthCheck API | HealthStatus 字段 |
| **排程 / 后台 Job** | DB 断言（tick 写库副作用）| 观察日志 |
| **Entity / DDL** | L0/L1/L2 静态已覆盖 | Phase 4 不需要 DB |
| **CONFLICT（I001）**| Phase 5 **只读不判** | 静态 SoT 比对完成 |

### 0.4 优先级规则

| 优先级 | 适用 Feature 类型 | Phase 5 必测 |
|---|---|---|
| **P0** | EXT-24 核心链路（配置 CRUD + 手动同步 + 自动同步 + known-ids + 跨租户权限） | ✅ |
| **P1** | Adapter / Router / 状态机 / Entity | ✅ |
| **P2** | 前端 UI / Dashboard / 排程配置 | ⏸️ 降级后非强制 |

---

## 一、Batch 1.1 — Feature 运行时验证计划（30 个 Feature）

> **表字段说明**：
> - **验证方式**：API-only / API+DB / DB-only / E2E-降级 / Static-Only / CONFLICT-只读
> - **需 E2E**：✓ = 需要（Playwright）/ ✗ = 不需要
> - **需 DB 断言**：✓ = 需要（DB 写后读 + 状态断言）/ ✗ = 不需要
> - **理由**：为什么选这个组合

### 1.1 P0 — EXT-24 核心链路（7 个 Feature）

| Feature ID | Priority | 验证方式 | 需 E2E | 需 DB | 理由 |
|---|---|---|---|---|---|
| **F001** 创建集成 | **P0** | **API-only** | ✗ | ✗* | POST `/crm-integration` 走 `@PostMapping` (CrmIntegrationService.java:35)。写库后 SELECT 验证行存在 = 静态可达 L0。DB 断言：可选（不强制）|
| **F002** 集成列表 | **P0** | **API-only** | ✗ | ✗* | GET `/crm-integration/list` (CrmIntegrationService.java:46)。**先 F001 创一条** → 再 F002 读 → 验证 id / platform / status。DB 断言：可选 |
| **F005** 手动触发同步 ⭐ | **P0** | **API+DB** | ✗ | ✓ | POST `/crm-integration/{id}/sync?force=0|1` (CrmIntegrationService.java:79)。**预期 Phase 5 暴露 I002**：`selectCount` / `updateById` 抛 `Unknown column 'sync_window_minutes'`。**DB 断言**：F005 失败时 `crm_integration.last_incremental_sync_at` 应保持不变（F029 验证）+ `crm_sync_log` 应有 status=FAILED 行 |
| **F008** 手动 sync 窗口跳过 | **P0** | **API-only** | ✗ | ✓** | POST sync 带 `force=null` + 上次 sync 时间在窗口内 → 应返回 `success + skipped=true + reason="not in cron window, use ?force=1 to override"` (CrmIntegrationService.java:96-103)。**DB**：验证 `crm_integration.last_incremental_sync_at` 不更新。⚠ 同样依赖 I002（读 `syncWindowMinutes` 列），**预期抛 I002 后窗口判断失效** — 这本身是审计可见的负向证据 |
| **F009** 手动 sync 跨租户拒绝 | **P0** | **API-only** | ✗ | ✗ | POST sync + 数据 scope != "ALL" + integration.tenantId 不匹配 → `BizCode.FORBIDDEN` (CrmIntegrationService.java:88-90)。**Phase 5 用 2 个不同租户的 token 测**：平台超管（`dataScope=ALL`）可调，普通租户调别人 integrationId 应 403 |
| **F010** 自动 tick 扫描 | **P0** | **API+DB（观察）** | ✗ | ✓ | `@Scheduled` 5min 一次扫 (CrmSyncScheduler.java:47-56)。**Phase 5 不可加速排程**：通过 DB 断言（创建 `status=ACTIVE + sync_enabled=1` 集成 → 等下一轮 tick → 验证 `crm_integration.last_sync_at` 被更新 / 或 `crm_sync_log` 新增 INCREMENTAL 行）。⚠ I002：缺 `sync_enabled` 列 → tick 时 SQL 异常被 catch 吞 → **预期 Phase 5 验证 catch (Exception) 静默（RowLogger 留痕）** |
| **F012** known-ids 差集加载 | **P0** | **DB-only** | ✗ | ✓ | `CrmSyncScheduler.loadKnownExternalIds` (CrmSyncScheduler.java:174-194)。**预期 Phase 5 暴露 I002/F023**：表 `crm_contact_map` 缺失 → `List` 抛 `BadSqlGrammarException` → catch 吞 → 返回 empty Set → 失去差集去重。**DB 断言**：调用 tick / 同步后 `crm_contact_map` 行数 = 0（不会写入）|

### 1.2 P1 — Adapter / Router / 状态机 / Entity（15 个 Feature）

| Feature ID | Priority | 验证方式 | 需 E2E | 需 DB | 理由 |
|---|---|---|---|---|---|
| **F003** 更新集成 | **P1** | **API-only** | ✗ | ✗* | POST `/crm-integration/{id}` (CrmIntegrationService.java:58)。**Shadow 已登记 I007** — Phase 5 用 curl 直调验证。DB 断言可选（验证 row 字段更新）。**预期 Phase 5 暴露 I002**：缺 9 列（如 `object_mappings`）时 updateById 不报错（MyBatis 容忍 NULL 列），但表本身缺列 → `update` 不更新缺列字段 |
| **F004** 切换状态 | **P1** | **API+DB** | ✗ | ✓ | POST `/crm-integration/{id}/toggle` body=`{status: "ACTIVE"\|"DISABLED"\|"PAUSED"}` (CrmIntegrationService.java:68)。**枚举不一致 I005** — Phase 5 验证：(a) `ACTIVE ↔ DISABLED` 正常切换；(b) `PAUSED` 写入但后端不校验。**DB 断言**：`SELECT status FROM crm_integration WHERE id=?` |
| **F006** 同步日志 | **P1** | **API-only** | ✗ | ✗* | GET `/crm-integration/sync-log/list?integrationId=?` (CrmIntegrationService.java:130)。**契约漂移 I003** — Phase 5 验证：(a) 不传 integrationId 返回全部；(b) 传 `integrationId=X` 实际按 platform 过滤；(c) 同 platform 多 integration 边界情况。DB 断言可选 |
| **F007** Dashboard | **P1** | **API-only** | ✗ | ✗ | GET `/crm-integration/dashboard` (CrmIntegrationService.java:142)。**Shadow 已登记 I006** + **catch 吞异常 I008**。**预期 Phase 5 暴露**：`success=true + data=null` = I002 (selectCount 抛) → I008 catch 吞 = Fake Success 链证据 |
| **F011** shouldRun 窗口判断 | **P1** | **API-only**（间接）| ✗ | ✓ | `CrmSyncScheduler.shouldRun` (CrmSyncScheduler.java:72)。**不直接暴露 API** — 通过 F005 syncNow 行为验证：返回 skipped=true 即 shouldRun=false。**预期 Phase 5 暴露 I002**：`shouldRun` 读 `lastIncrementalSyncAt` / `syncWindowMinutes` 列 → Unknown column 异常 |
| **F013** Adapter 接口契约 | **P1** | **Static-Only** | ✗ | ✗ | `CrmAdapter` 接口 5 方法 + 5 DTO（CrmAdapter.java）。L0 静态可达，**Phase 5 不需要黑盒验证**（接口本身无法跑） |
| **F014** Salesforce 适配器 | **P1** | **API-only**（受限）| ✗ | ✗* | `SalesforceCrmAdapter` (SalesforceCrmAdapter.java)。**真实 SF 沙箱未必有凭据**。**降级为 healthCheck**：`POST /crm-integration/{id}/health`（如果有）或通过 tick 触发 + log 观察。**预期 Phase 5 暴露 I002**：`authenticate` 读 `privateKey`/`username`/`loginUrl` 列 → Unknown column。**DB 断言可选**：观察 `crm_sync_log.error_message` 含 SF 相关异常 |
| **F015** Mock 适配器 | **P1** | **API-only** | ✗ | ✗ | `MockCrmAdapter` (MockCrmAdapter.java)。**MOCK-INTENTIONAL**（02-mock 已标）。Phase 5 通过 F005 syncNow 链验证：Mock 模拟 3 条样本 + 滚动缓冲 + known-id 内存去重 |
| **F016** HubSpot 适配器 | **P1** | **CONFLICT-只读** | ✗ | ✗ | HubSpotCrmAdapter 完整实现但 SoT `.mdc` §0.6 误标。**Phase 5 不验证功能**（审计已通过代码审查验证实现完整性）；仅核对 `ext-24.mdc` 与代码 gap。**用户决策 2**：不动 .mdc，本 Plan 仅登记 |
| **F017** Zoho 适配器 | **P1** | **CONFLICT-只读** | ✗ | ✗ | ZohoCrmAdapter 完整实现但 SoT `.mdc` §0.6 误标。同 F016 |
| **F018** Router 分发 + Mock fallback | **P1** | **CONFLICT-只读** | ✗ | ✗ | CrmAdapterRouter 实际有 MOCK fallback 但 SoT `.mdc` §5 误标"NPE"。同 F016 |
| **F019** CrmIntegration Entity | **P1** | **Static-Only + API 间接** | ✗ | ✗* | `entity/CrmIntegration.java`。**L0 静态可达**。Phase 5 通过 F001/F003 间接验证字段可达。**预期 Phase 5 暴露 I002**：写库时 `private_key`/`object_mappings` 等缺列 → MyBatis `insert`/`update` 部分列 silent-ignored（仅已存在列写入）|
| **F020** CrmContactMap Entity | **P1** | **DB-only** | ✗ | ✓ | `entity/CrmContactMap.java` uk=`(platform, external_id)`。**Phase 5 通过 F005 触发**：sync 写库时 `crm_contact_map` 表不存在 → `BadSqlGrammarException` → upsertContactMap fail_count++。**DB 断言**：`SELECT COUNT(*) FROM crm_contact_map` = 0（永远没写入）|
| **F021** CrmSyncLog Entity | **P1** | **Static-Only + API 间接** | ✗ | ✗* | `entity/CrmSyncLog.java`。L0 + 通过 F005 触发写库。**预期 Phase 5 暴露 I002**：写库时 `sync_token` / `records_total` / `records_filtered` 缺列 → 写不入（MyBatis partial update silent）— Phase 5 DB 断言 `SELECT * FROM crm_sync_log WHERE tenant_id=?` 看字段 |
| **F029** status 状态机 | **P1** | **API+DB** | ✗ | ✓ | `crm_integration.status` 状态机 (CrmSyncScheduler.java:114/134)。**Phase 5 验证**：(a) F004 toggle 手动切；(b) F005 sync 失败 → status=ERROR；(c) sync 成功 → status=ACTIVE。**DB**：执行 sync 前 `UPDATE ... SET status='ACTIVE'` → 触发同步 → 验证 status 是否变为 ERROR（F005 失败）。**预期 Phase 5 暴露 I002**：写 status 时 `error_message` 列存疑 |
| **F030** sync 配置 | **P1** | **API-only**（间接）| ✗ | ✗* | `syncSchedule` / `syncWindowMinutes` 配置（CrmIntegration.java）。**实际通过 F008 验证**：`syncWindowMinutes` 缺列时窗口判断抛 I002 |

### 1.3 P2 — 前端 UI / Dashboard / 排程配置（8 个 Feature）

| Feature ID | Priority | 验证方式 | 需 E2E | 需 DB | 理由 |
|---|---|---|---|---|---|
| **F022** EXT-15 DDL | **P2** | **Static-Only** | ✗ | ✗ | `migrate_ext_15_crm_real.sql` 文件存在 (db/sql/)。**用户决策 1：不执行**。Phase 5 仅核对 DDL 文件 + 列名比对 entity 字段声明（已 L0 验证）|
| **F023** crm_contact_map 表存在性 | **P2** | **DB-only** | ✗ | ✓ | db-snapshot.sql grep 0 命中已确认。**Phase 5 通过查询生产 DB**：`SHOW TABLES LIKE 'crm_contact_map'` / `DESCRIBE crm_contact_map` — **预期不存在** |
| **F024** 集成列表 UI | **P2** | **E2E-降级** | ✓* | ✗ | `views/crm-integration/index.vue:12-22` 表。**Playwright 缺失降级**：生成 `spec.ts` 脚本（含点击 / 翻页 / 表格渲染断言）+ 用户手动跑。**预期 Phase 5**：列表加载 + 至少 1 行数据；I005 enum 漂移不影响展示 |
| **F025** 新建集成表单 | **P2** | **E2E-降级** | ✓* | ✗ | `index.vue:38-61` 对话框。**降级同 F024**。**预期 Phase 5**：填 4 字段 → 保存 → 列表新增行；**I005 C4 已知缺字段**（验证对象仅 4 字段）|
| **F026** 同步日志 tab | **P2** | **E2E-降级** | ✓* | ✗ | `index.vue:24-33`。**降级同 F024**。**预期 Phase 5**：切到同步日志 tab → 表里 `integrationId` 列显示（**I003 漂移**：`crm_sync_log` 无此字段 → 列值=undefined）|
| **F027** sync API 未传 `?force=1` | **P2** | **API-only** | ✗ | ✗ | `api/crm-integration.ts:14` 不传 `?force=1`。**降级为 curl 直调**：调 sync 时不传 force → 窗口外 → 返回 `skipped=true`（**I005 C2** 已确认）|
| **F028** objectMappings UI 缺失 | **P2** | **E2E-降级** | ✓* | ✗ | `index.vue:43-55` 表单字段。**降级同 F024**。**预期 Phase 5**：表单**无 `objectMappings` 输入** — 验证 I005 C4；触发保存 → DB 表 `object_mappings` 列为 NULL（I002）|
| **F007** Dashboard tab | **P1** | **E2E-降级 + API-only** | ✓* | ✗ | 已计入 P1。**降级**：生成 Playwright 脚本尝试点击 dashboard 按钮；预期无按钮（**I006 Shadow**）；同时 curl `GET /crm-integration/dashboard` 验 API |

---

## 二、Feature 验证计划汇总表（30 个 Feature 紧凑视图）

| Feature ID | Priority | 验证方式 | 需 E2E | 需 DB | Phase 5 预期暴露 Issue |
|---|---|---|---|---|---|
| F001 | P0 | API-only | ✗ | ✗* | I002（缺 9 列 silent）|
| F002 | P0 | API-only | ✗ | ✗* | — |
| F005 ⭐ | P0 | API+DB | ✗ | ✓ | **I002** (`Unknown column 'sync_window_minutes'`)、I009 |
| F008 | P0 | API+DB | ✗ | ✓ | I002 (列缺导致窗口失败)|
| F009 | P0 | API-only | ✗ | ✗ | — (跨租户拒绝独立)|
| F010 | P0 | API+DB（观察）| ✗ | ✓ | I002 (`sync_enabled` 缺列 → tick 抛 + catch 吞)|
| F012 | P0 | DB-only | ✗ | ✓ | **I002 / F023** (`crm_contact_map` 缺失 → empty Set)|
| F003 | P1 | API-only | ✗ | ✗* | I007 (Shadow) + I002 |
| F004 | P1 | API+DB | ✗ | ✓ | **I005 C1** (toggle enum)|
| F006 | P1 | API-only | ✗ | ✗* | **I003** (platform 反查)|
| F007 | P1 | API-only | ✗ | ✗ | **I006 (Shadow) + I002 + I008** (Fake Success 链)|
| F011 | P1 | API-only（间接）| ✗ | ✓ | I002 |
| F013 | P1 | Static-Only | ✗ | ✗ | — |
| F014 | P1 | API-only（受限）| ✗ | ✗* | I002 (`privateKey` 缺列)|
| F015 | P1 | API-only（间接）| ✗ | ✗ | — (MOCK-INTENTIONAL)|
| F016 | P1 | CONFLICT-只读 | ✗ | ✗ | I001 |
| F017 | P1 | CONFLICT-只读 | ✗ | ✗ | I001 |
| F018 | P1 | CONFLICT-只读 | ✗ | ✗ | I001 |
| F019 | P1 | Static + API 间接 | ✗ | ✗* | I002 |
| F020 | P1 | DB-only | ✗ | ✓ | **I002 / F023**（同步写表失败 → fail_count++）|
| F021 | P1 | Static + API 间接 | ✗ | ✗* | I002 (缺 3 列 silent)|
| F022 | P2 | Static-Only | ✗ | ✗ | — (DDL 文件存在)|
| F023 | P2 | DB-only | ✗ | ✓ | **I002**（表不存在）|
| F024 | P2 | E2E-降级 | ✓* | ✗ | I005 C1 (列展示)|
| F025 | P2 | E2E-降级 | ✓* | ✗ | I005 C4 (字段缺失)|
| F026 | P2 | E2E-降级 | ✓* | ✗ | I003 (列名)|
| F027 | P2 | API-only | ✗ | ✗ | **I005 C2** (force 未传)|
| F028 | P2 | E2E-降级 | ✓* | ✗ | I005 C4 + I002 |
| F029 | P1 | API+DB | ✗ | ✓ | I005 C1 + I002 (status 翻转)|
| F030 | P1 | API-only（间接）| ✗ | ✗* | I002 |

> **统计**：30 个 Feature 中，**6 个 E2E-降级**（Playwright 缺失）+ **3 个 CONFLICT-只读**（F016/F017/F018）+ **2 个 Static-Only**（F013/F022）+ **其余 19 个 API-only / API+DB / DB-only**。

---

## 三、Phase 5 负向证据预期清单（重要）

> **原则**：Phase 5 看到的 "失败" 不是 bug 终结，而是审计需要捕获的合法负向证据。
> 修代码绕过这些失败 = **审计失败**（违反规则 6）。

| Feature / 路径 | Phase 5 预期失败模式 | 审计意义 |
|---|---|---|
| F005 syncNow | 抛 `Unknown column 'sync_window_minutes' / 'object_mappings' / 'private_key'` | 反向证明 I002 / F005 PARTIAL-BLOCKER 判定 |
| F008 窗口跳过 | shouldRunForManualTrigger 抛 → 返回异常（非 `skipped=true`）| 反向证明 I002 / F008 PARTIAL-NON-BLOCKER |
| F010 tick | 抛 `Unknown column 'sync_enabled'` → catch 吞 → tick 静默失败 | 反向证明 I002 + 验证 01-fake-success 的 catch-log-swallow 模式 |
| F012 known-ids | 表不存在 → empty Set → 失去差集去重 | 反向证明 F023 PARTIAL-BLOCKER |
| F020 upsert contact map | 表不存在 → `BadSqlGrammarException` → fail_count++ | 反向证明 F020 PARTIAL-BLOCKER |
| F023 表存在性 | `SHOW TABLES LIKE 'crm_contact_map'` → 0 行 | 反向证明 F023 PARTIAL-BLOCKER |
| F022 DDL | 不跑迁移，确认 DDL 文件含 EXT-15 9 列 + crm_contact_map DDL | **正向证据**（DDL 存在，未跑而已）|
| F007 dashboard | `success=true + data=null` | **关键负向证据**：I002 + I008 Fake Success 链 |
| F016/017/018 | 不测（CONFLICT-只读）| **正向证据**：Phase 4 已核对 SoT 与代码 gap |
| F004 toggle | 传 PAUSED 写入成功（无 enum 校验）| **关键负向证据**：I005 C1 |

---

## 四、Playwright 降级方案详（E2E-降级 Feature）

### 4.1 适用 Feature

| Feature | 验证内容 | 脚本路径（待 Phase 5 生成）|
|---|---|---|
| F024 | 集成列表 UI 渲染 + 列名 | `audit/runtime/playwright/F024-list-ui.spec.ts` |
| F025 | 新建对话框 + 4 字段保存 | `audit/runtime/playwright/F025-create-form.spec.ts` |
| F026 | 同步日志 tab 切 + 列名 `integrationId` | `audit/runtime/playwright/F026-sync-log-tab.spec.ts` |
| F027 | sync 按钮点击 + 验证 force 未传 | `audit/runtime/playwright/F027-sync-button.spec.ts` |
| F028 | 新建表单缺字段验证（I005 C4）| `audit/runtime/playwright/F028-object-mappings-missing.spec.ts` |
| F007 | 无 dashboard tab（I006）| `audit/runtime/playwright/F007-no-dashboard-tab.spec.ts` |

### 4.2 Phase 5 三步走

1. **Cursor 生成**脚本（模板已规划，Phase 5 编写）
   - 每个 `.spec.ts` 含：环境准备（启动前端 + 后端 mock 或真实 server）+ 操作步骤 + 断言
   - 兼容 Playwright + Element Plus 选择器
2. **用户手动跑**
   - `npx playwright test audit/runtime/playwright/F024-list-ui.spec.ts`
   - 或开发机配置 Playwright 环境后执行
3. **结果贴回**
   - `audit/runtime/playwright/F024-list-ui.result.md` — pass/fail + 截图 + 实测 data
   - 缺 result.md = UNVERIFIED（按规则 3）

### 4.3 E2E 降级补充证据

Phase 5 Playwright 跑通后，可附加：

- **HTML 抓包**：`curl /crm-integration` 返回 HTML → grep 渲染行
- **直 curl API**：绕过 UI 直调后端（已在 §一/§二 API-only 中覆盖大部分）
- **Vitest**：若 `frontend/meiyetec-web` 已配 Vitest，可为 `crm-integration/index.vue` 加 component test

### 4.4 Audit E2E 独立 Playwright 配置（2026-09-13 新增）

**问题**：前端项目的 `playwright.config.ts` 的 testMatch 不匹配 audit 脚本（命名规则 `F007/F024-F028` vs 前端 `golden-path/smoke/full-flow`），从前端项目根目录直接跑会 `Total: 0 tests`。

**解决方案（不动前端项目代码）**：

| 文件 | 类型 | 作用 |
|---|---|---|
| `audit/scripts/e2e/playwright.config.ts` | 独立配置 | testDir='.'，testMatch=`/\.spec\.ts$/`，headless 默认 true，baseURL=localhost:5173 |
| `audit/scripts/e2e/node_modules` | Junction 符号链接 | 指向 `frontend/meiyetec-web/node_modules`，让 `@playwright/test` 可解析 |

**运行命令**（从 `frontend/meiyetec-web/`）：

```bash
# 列测试
npx playwright test --config=../../audit/scripts/e2e/playwright.config.ts --list

# 跑全部（默认 headless）
npx playwright test --config=../../audit/scripts/e2e/playwright.config.ts

# 单 spec
npx playwright test --config=../../audit/scripts/e2e/playwright.config.ts F007-no-dashboard-tab.spec.ts

# 有头模式
HEADED=1 npx playwright test --config=../../audit/scripts/e2e/playwright.config.ts

# 自定义 baseURL
FRONTEND_URL=http://localhost:5174 npx playwright test --config=../../audit/scripts/e2e/playwright.config.ts
```

**实测 list 结果**（2026-09-13 14:43，Node.js 已就绪 + Chromium 1.62.1 已装）：

```
F007-no-dashboard-tab.spec.ts:25:7  F007 › Dashboard tab Shadow Feature › I006: 应无 dashboard tab
F007-no-dashboard-tab.spec.ts:47:7  F007 › Dashboard tab Shadow Feature › API 端点可访问（I008 Fake Success 链）
F024-list-ui.spec.ts:22:7           F024 › 集成列表 UI › 集成列表渲染 + 列名 + 状态 enum
F025-create-form.spec.ts:27:7       F025 › 新建集成表单 › 对话框渲染 + I005 C4 字段缺失
F025-create-form.spec.ts:54:7       F025 › 新建集成表单 › 填表保存 → 列表新增
F026-sync-log-tab.spec.ts:25:7      F026 › 同步日志 tab › 切到同步日志 tab + 验证 integrationId
F026-sync-log-tab.spec.ts:46:7      F026 › 同步日志 tab › 同步日志 API 响应（I003 漂移实证）
F027-sync-button.spec.ts:25:7       F027 › sync 按钮 force 未传 › 点击同步按钮 → URL 不带 ?force=1
F027-sync-button.spec.ts:70:7       F027 › sync 按钮 force 未传 › API 直调验证 force 未传
F027-sync-button.spec.ts:83:7       F027 › sync 按钮 force 未传 › API 直调 force=1 验证
F028-object-mappings.spec.ts:27:7   F028 › objectMappings UI 缺失 › 新建表单无 objectMappings 字段（I005 C4）
F028-object-mappings.spec.ts:57:7   F028 › objectMappings UI 缺失 › 后端 API + DB 验证 object_mappings 持久化为 NULL

Total: 12 tests in 6 files
```

**未登记 I014**：本节是审计工具链配置（独立 config + 符号链接），非项目本身的代码缺陷。前端项目 testMatch 不匹配属于设计选择（不期望 audit 脚本混入 CI）。

---

## 五、Phase 5 必跑 SQL 断言（DB-only Feature）

```sql
-- F012 / F020 / F023 必跑（I002 / F023 暴露）
SHOW TABLES LIKE 'crm_contact_map';
-- 预期：0 行

DESCRIBE crm_integration;
-- 预期：缺 sync_window_minutes / object_mappings / private_key / sync_enabled /
--       last_incremental_sync_at / sync_token / sync_schedule / username / login_url

DESCRIBE crm_sync_log;
-- 预期：缺 sync_token / records_total / records_filtered

-- F005 触发后
SELECT id, status, error_message, last_incremental_sync_at
FROM crm_integration WHERE tenant_id = ? ORDER BY id DESC LIMIT 5;
-- 预期：sync 失败后 status=ERROR / last_incremental_sync_at 不变（验证 I009）

SELECT id, sync_type, status, records_total, records_filtered, sync_token
FROM crm_sync_log WHERE tenant_id = ? ORDER BY id DESC LIMIT 5;
-- 预期：sync 触发后有 FAILED 行，但 records_total / records_filtered / sync_token 列为空
-- （I002 silent 写库）

SELECT COUNT(*) FROM crm_contact_map;
-- 预期：0（永远 0，F023 表缺失）

-- F004 toggle 验证
SELECT id, status FROM crm_integration WHERE id = ?;
-- 预期：传入 PAUSED 后 status='PAUSED'（I005 C1 写入未识别状态）

-- F007 dashboard 触发后（无 UI 时）
-- 通过 curl 触发，预期：HTTP 200 + body `{code:0, success:true, data:null}`
-- 这是 I008 catch 吞异常的 Fake Success 证据
```

---

## 六、Phase 5 必跑 curl 命令模板

```bash
# F001 创建
curl -X POST http://localhost:8080/crm-integration \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"platform":"MOCK","shopName":"测试集成","syncDirection":"PULL","autoSync":1}'

# F002 列表
curl http://localhost:8080/crm-integration/list \
  -H "Authorization: Bearer $TOKEN"

# F003 更新（Shadow - 仅验证 API 行为）
curl -X POST http://localhost:8080/crm-integration/$ID \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"shopName":"Updated Name"}'

# F004 toggle
curl -X POST http://localhost:8080/crm-integration/$ID/toggle \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"DISABLED"}'   # 或 "PAUSED" 触发 I005 C1

# F005 手动同步（无 force）
curl -X POST http://localhost:8080/crm-integration/$ID/sync \
  -H "Authorization: Bearer $TOKEN"
# 预期 Phase 5：HTTP 500 + body 含 `Unknown column 'sync_window_minutes'`（I002）

# F005 强制同步
curl -X POST "http://localhost:8080/crm-integration/$ID/sync?force=1" \
  -H "Authorization: Bearer $TOKEN"
# 预期 Phase 5：HTTP 500（I002）— **不要**为了通过而用 force=1 短路

# F006 同步日志
curl "http://localhost:8080/crm-integration/sync-log/list?integrationId=$ID" \
  -H "Authorization: Bearer $TOKEN"

# F007 dashboard（I006 Shadow + I008 Fake Success）
curl http://localhost:8080/crm-integration/dashboard \
  -H "Authorization: Bearer $TOKEN"
# 预期：HTTP 200 + body data=null（I008 catch 吞）
# 或 HTTP 500（I002 selectCount 抛、catch 未到）

# F009 跨租户拒绝
# 用 tenant1 的 token 调 tenant2 的 integrationId
curl -X POST http://localhost:8080/crm-integration/$OTHER_TENANT_INTEGRATION_ID/sync \
  -H "Authorization: Bearer $TENANT1_TOKEN"
# 预期：HTTP 403 / body code=FORBIDDEN
```

---

## 七、Phase 4 不做的事（规则确认）

| 不做 | 原因 |
|---|---|
| ❌ 不执行 Phase 5 任何 curl / SQL / Playwright | 本 Phase 4 仅**计划**，执行留给 Phase 5 |
| ❌ 不写 Playwright 脚本 | Phase 5 才生成 |
| ❌ 不跑 `migrate_ext_15_crm_real.sql` | 用户决策 1 |
| ❌ 不改 `ext-24-crm-integration.mdc` | 用户决策 2 |
| ❌ 不修任何业务代码 | 规则 6 |
| ❌ 不重复 Phase 3 已生成的内容 | 本文件仅 Phase 4 视角 |

---

## 八、Phase 4 → Phase 5 入口守门

| 检查项 | 状态 | 备注 |
|---|---|---|
| 30 个 Feature 优先级 + 验证方式已定 | ✅ | 本文件 §一/§二 |
| 9 个 Issue（I001-I009）已登记 + 4 个独立文件已建 | ✅ | `audit/issues/` |
| **E2E Feature 降级方案**已规划（6 个 Playwright spec）| ✅ | 本文件 §四 |
| **DB-only 断言 SQL**已规划 | ✅ | 本文件 §五 |
| **API-only curl 模板**已规划 | ✅ | 本文件 §六 |
| **不修代码**已确认 | ✅ | 本文件 §七 |
| 不进 Phase 5 | ✅ | 等用户启动 |

### Phase 5 启动条件（用户确认）

| # | 启动条件 |
|---|---|
| K1 | 用户启动后端服务（本地或 staging）+ 前端 dev server |
| K2 | MySQL 真实库可连（DB 已 migrate 完 EXT-15？— **预期未 migrate**）|
| K3 | 平台超管 token + 普通租户 token 各一份 |
| K4 | Playwright 环境用户本地配置（仅 E2E Feature） |
| K5 | 用户决策启动 Phase 5 |

---

## 九、关联

- `audit/feature-inventory.md` §1.1.2（30 个 Feature 详细列表）
- `audit/traceability-matrix.md` Batch 1.1 段（每个 Feature 的全栈追溯 + Final Result）
- `audit/reports/static-gap-report.md`（Phase 3 静态差距分析 + Issue I006-I009 立项）
- `audit/issues/issue-register.md`（I001-I009 主索引）
- `audit/issues/I001-ext24-sot-conflict.md` / `I004-refactor-backlog.md` / `I006-crm-dashboard-shadow.md` / `I007-crm-update-shadow.md` / `I008-dashboard-catch-swallow.md` / `I009-transactional-rollbackfor.md`（独立 issue 文件）
- `.cursor/rules/ext-24-crm-integration.mdc`（SoT — F016/F017/F018 CONFLICT 来源）

---

**Phase 4 Runtime Verification Plan 完成（Batch 1.1 段）。30 个 Feature 全部规划完毕。**

**等待用户确认 K1-K5 启动条件 → 进入 Phase 5（Runtime Verification Execution）。**
