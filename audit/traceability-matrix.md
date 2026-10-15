# Full-Stack Traceability Matrix

> **统一追溯文件，按 Batch 分段**
> 创建时间：2026-09-12 21:45 (UTC+8)
> 引用规则：`@project-audit.mdc` §6.1（每 Feature 全栈追溯）+ `@feature-id.mdc`
> Commit baseline：`3f9b9d3` (HEAD, 2026-09-12 21:29 +0800) → Phase 2 提交后会刷新
> DB snapshot：`audit/baseline/db-snapshot.sql`（417 KB / 270 表 / schema-only）
> env：`audit/baseline/env.md`（9,951 B）

---

## 全局约定

### 字段说明

| 列 | 含义 | 备注 |
|---|---|---|
| **Feature ID** | F + 三位数字 | 跨 Batch 连续；与 `feature-inventory.md` 一致 |
| **前端路由** | 前端 router 配置 | `router/index.ts:行号` |
| **页面** | Vue 文件路径 | |
| **UI板块** | Tab / Form / 按钮 | |
| **Action** | 用户行为（点击 / 输入） | |
| **Handler** | 前端函数 | `<script setup>` 内 |
| **API** | api/*.ts 封装 | |
| **Method** | HTTP 动词 | |
| **Controller** | 后端入口 | `service/CrmIntegrationService.java:行号` (注：命名误导) |
| **Service** | 业务逻辑层 | |
| **Mapper** | MyBatis Mapper | |
| **DB表** | 主表 | |
| **关联表** | 关联/外键表 | |
| **读/写** | R / W / RW | |
| **事务** | `@Transactional` 注解 | |
| **权限** | 平台超管 / 普通租户 / 跨租户 | |
| **软删除** | 软删字段 | `deleted` 列 |
| **审计字段** | `created_at / updated_at / version` 等 | |
| **Static Status** | L0-L3 等级 | 仅静态分析 |
| **Runtime Status** | L4-L5b | 需运行 |
| **DB Status** | DB 实际状态（与 db-snapshot 对账） | |
| **Business Status** | 业务能力匹配 | |
| **E2E Status** | E2E 测试状态 | |
| **Verified Commit** | 已验证的 commit hash | |
| **DB Snapshot** | 已验证的 DB 快照 | |
| **Last Verified At** | 最近验证时间 | |
| **Final Result** | 9 子标签之一 | |
| **Evidence** | `audit/evidence/FXXX-<slug>/` | 编号固定 |
| **Issue** | 关联 ISSUE-I00N | |

### 通用原则

- **审计阶段只记录不修复**（规则 6）— 本文件全部标注为 **静态分析可达 L0**，未运行证据的标 **UNVERIFIED**
- F016/F017/F018 按 ISSUE-I001 标 **CONFLICT**（不判 MISSING）
- F004/F006/F026/F029 按 ISSUE-I005/I003 标 **PARTIAL-NON-BLOCKER**
- F023 按 ISSUE-I002 标 **PARTIAL-BLOCKER**（表缺失 + 缺 EXT-15 列）
- F022 按 ISSUE-I002 标 **PARTIAL-NON-BLOCKER**（缺 9 列）

---

## Batch 1.1 — EXT-24 CRM 集成（30 Features）

> **范围来源**：`audit/baseline/scope-freeze.md` §2.1 + `audit/feature-inventory.md` §1.1
> **DB 实际状态（Phase 2 静态验证）**：
> - `crm_integration` 存在（db-snapshot.sql line 3433，**22 列**）— **缺 9 列 EXT-15 新列**
> - `crm_sync_log` 存在（db-snapshot.sql line 3963，**12 列**）— **缺 3 列 EXT-15 新列**
> - `crm_contact_map` **不存在**（db-snapshot.sql grep 0 匹配）

---

### F001 — CRM 集成 / 创建集成

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F001 | `feature-inventory.md` §1.1.2 row 1 |
| 前端路由 | `/crm-integration` | `frontend/meiyetec-web/src/router/index.ts:173` |
| 页面 | `frontend/meiyetec-web/src/views/crm-integration/index.vue` | |
| UI板块 | 「集成配置」tab + 「新建集成」对话框 | `index.vue:4-23`（tab）/ `:38-61`（dialog）|
| Action | 点击「新建集成」→ 填写平台/店铺/方向/自动同步 → 点击「保存」 | `index.vue:5`（新建按钮）/ `:59`（保存按钮） |
| Handler | `doAdd()` | `index.vue:78-82` |
| API | `crmIntegrationApi.create(addForm.value)` | `frontend/meiyetec-web/src/api/crm-integration.ts:5` |
| Method | POST | |
| Controller | `CrmIntegrationService.create(@RequestBody CrmIntegration i)` | **`backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java:36-44`** |
| Service | 合并于 Controller（无独立 Service） | （I004 命名异味） |
| Mapper | `CrmIntegrationMapper.insert` | `backend/src/main/java/com/meiyetec/mapper/CrmIntegrationMapper.java:265`（行号范围，全文件 265 B） |
| DB表 | `crm_integration` | db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | 普通租户（强制 `tenantId=TenantContextHolder.get().getTenantId()`）| `CrmIntegrationService.java:38` |
| 软删除 | ❌（无 `deleted` 列）| db-snapshot.sql:3433-3458 |
| 审计字段 | `created_at / updated_at`（MyBatis auto-fill） | `CrmIntegration.java:55-58` |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ❌ PARTIAL | 缺 9 列 EXT-15 (`object_mappings` / `private_key` / `sync_window_minutes` 等) |
| Business Status | 完整（API 入参支持 `objectMappings`，前端表单未输入 — F028） | |
| E2E Status | UNVERIFIED | Playwright 未配置 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | ISSUE-I002（缺 EXT-15 列）+ I005（前端 enum） |
| Evidence | `audit/evidence/F001-create-integration/` | 待建（Phase 5+） |
| Issue | I002 / I005 | |

---

### F002 — CRM 集成 / 集成列表

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F002 | `feature-inventory.md` §1.1.2 row 2 |
| 前端路由 | `/crm-integration` | `router/index.ts:173` |
| 页面 | `views/crm-integration/index.vue` | |
| UI板块 | 「集成配置」tab el-table | `index.vue:4-23` |
| Action | 页面加载 / 列表刷新 | `onMounted → loadList()` `index.vue:94` / `:76` |
| Handler | `loadList()` | `index.vue:76` |
| API | `crmIntegrationApi.list()` | `api/crm-integration.ts:6-7` |
| Method | GET | |
| Controller | `CrmIntegrationService.list(@RequestParam platform, status)` | **`CrmIntegrationService.java:47-56`** |
| Service | 合并于 Controller | |
| Mapper | `CrmIntegrationMapper.selectList(LambdaQueryWrapper)` | `CrmIntegrationMapper.java` |
| DB表 | `crm_integration` | db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 普通租户（强制 `tenantId`） | `:50` |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 表存在 | |
| Business Status | 完整（按 `tenantId` 过滤 + 按 `id` 倒序） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | 缺运行证据；I002 仅影响列不影响 list |
| Evidence | `audit/evidence/F002-list-integrations/` | 待建 |
| Issue | — | |

---

### F003 — CRM 集成 / 更新集成

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F003 | `feature-inventory.md` §1.1.2 row 3 |
| 前端路由 | `/crm-integration` | `router/index.ts:173` |
| 页面 | `views/crm-integration/index.vue` | |
| UI板块 | ❌ **无 UI 入口** | 前端只有「新建」/「切换状态」/「同步」，无 edit 按钮 |
| Action | — | （前端缺 UI — 见 ISSUE-I005 关联） |
| Handler | — | |
| API | `crmIntegrationApi.update(id, data)` | `api/crm-integration.ts:8`（已封装但未调用） |
| Method | POST | |
| Controller | `CrmIntegrationService.update(@PathVariable Long id, @RequestBody CrmIntegration i)` | **`CrmIntegrationService.java:59-66`** |
| Service | 合并于 Controller | |
| Mapper | `CrmIntegrationMapper.updateById` | |
| DB表 | `crm_integration` | db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | ❌ | |
| 权限 | 普通租户 | `:62` |
| 软删除 | ❌ | |
| 审计字段 | `updated_at`（自动） | `CrmIntegration.java:57` |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ PARTIAL | 缺 EXT-15 列（`object_mappings` / `private_key` 等字段无法保存） |
| Business Status | 不完整（前端无 UI） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 前端缺 UI + I002 缺 EXT-15 列 |
| Evidence | `audit/evidence/F003-update-integration/` | 待建 |
| Issue | I002 / I005（前端缺 edit 入口）| |

---

### F004 — CRM 集成 / 切换状态

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F004 | `feature-inventory.md` §1.1.2 row 4 |
| 前端路由 | `/crm-integration` | `router/index.ts:173` |
| 页面 | `views/crm-integration/index.vue` | |
| UI板块 | 「状态」列 el-switch | `index.vue:12-16` |
| Action | 点击 el-switch（ACTIVE ↔ PAUSED） | `index.vue:14` |
| Handler | `toggle(row)` | `index.vue:84-87` |
| API | `crmIntegrationApi.toggle(row.id, { status: row.status })` | `api/crm-integration.ts:9` |
| Method | POST | |
| Controller | `CrmIntegrationService.toggle(@PathVariable Long id, @RequestBody Map<String,Object> body)` | **`CrmIntegrationService.java:68-77`** |
| Service | 合并于 Controller | |
| Mapper | `CrmIntegrationMapper.selectById` / `updateById` | |
| DB表 | `crm_integration` | db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ | |
| 权限 | ❌ **无租户隔离校验**（任意 tenantId 都能 toggle 别人 integration） | `:70-72` |
| 软删除 | ❌ | |
| 审计字段 | `updated_at`（自动） | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 表存在 | |
| Business Status | ❌ **契约漂移**：前端 `ACTIVE/PAUSED` vs 后端 enum `ACTIVE/DISABLED/ERROR`（I005） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I005 enum 不一致 + 无租户隔离校验 |
| Evidence | `audit/evidence/F004-toggle-status/` | 待建 |
| Issue | **I005** | |

---

### F005 — CRM 集成 / 手动触发同步（⭐ EXT-24 行为变更）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F005 | `feature-inventory.md` §1.1.2 row 5 |
| 前端路由 | `/crm-integration` | `router/index.ts:173` |
| 页面 | `views/crm-integration/index.vue` | |
| UI板块 | 「操作」列 「同步」按钮 | `index.vue:17-21`（line 19 按钮） |
| Action | 点击「同步」按钮 | `index.vue:19` |
| Handler | `sync(id)` | `index.vue:88-91` |
| API | `crmIntegrationApi.syncNow(id)` | **`api/crm-integration.ts:14`**（**未传 `?force=1`** — F027 / I005 关联） |
| Method | POST | |
| Controller | `CrmIntegrationService.syncNow(@PathVariable Long id, @RequestParam(required=false) Integer force)` | **`CrmIntegrationService.java:85-125`** |
| Service | `CrmSyncScheduler.runIncremental(cfg, "MANUAL")` | **`CrmSyncScheduler.java:97-159`** |
| Mapper | `CrmIntegrationMapper.selectById` / `updateById` / `CrmSyncLogMapper.insert` / `updateById` / `CrmContactMapService.getOne/list/save/updateById` | |
| DB表 | `crm_integration` / `crm_sync_log` | db-snapshot.sql:3433 / 3963 |
| 关联表 | `crm_contact_map`（**缺失** — I002） | db-snapshot.sql grep 0 匹配 |
| 读/写 | RW | |
| 事务 | ✅ `@Transactional` | `CrmIntegrationService.java:84` |
| 权限 | 跨租户 `dataScope != "ALL" ∧ tenantId 不匹配 → BizCode.FORBIDDEN` | `:88-90` |
| 软删除 | ❌ | |
| 审计字段 | `created_at / updated_at`（CrmSyncLog）/ `crm_integration.updated_at` | |
| Static Status | L0 | 代码静态可达；调用图：Controller → Scheduler → Adapter → upsertContactMap |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **PARTIAL-BLOCKER** | 缺 9 列 EXT-15 (`sync_window_minutes` / `last_incremental_sync_at` / `object_mappings` / `private_key` 等) — 同步逻辑将抛 `Unknown column` 异常 |
| Business Status | 完整（手动 sync → 排程逻辑 → 真实 log） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 / 3963 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | I002 缺 EXT-15 列 → 生产环境无法运行（运行时 NPE / Unknown column） |
| Evidence | `audit/evidence/F005-manual-sync/` | 待建 |
| Issue | **I002** + I005（API 未传 `?force=1`） | |

---

### F006 — CRM 集成 / 同步日志

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F006 | `feature-inventory.md` §1.1.2 row 6 |
| 前端路由 | `/crm-integration` | `router/index.ts:173` |
| 页面 | `views/crm-integration/index.vue` | |
| UI板块 | 「同步日志」tab table | `index.vue:24-33` |
| Action | 页面加载 / 切换 tab → 列表 | `index.vue:77` (`loadLogs()`) / `:94` (`onMounted`) |
| Handler | `loadLogs()` | `index.vue:77` |
| API | `crmIntegrationApi.listSyncLogs()` | `api/crm-integration.ts:15-16` |
| Method | GET | |
| Controller | `CrmIntegrationService.listSyncLogs(@RequestParam(required=false) Long integrationId)` | **`CrmIntegrationService.java:128-139`** |
| Service | 合并于 Controller（含跨表反查） | |
| Mapper | `CrmIntegrationMapper.selectById` + `CrmSyncLogMapper.selectList(LambdaQueryWrapper)` | |
| DB表 | `crm_sync_log`（主） / `crm_integration`（反查 platform） | db-snapshot.sql:3963 / 3433 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 普通租户（`tenantId` 强制） | `:131` |
| 软删除 | ❌ | |
| 审计字段 | `created_at`（CrmSyncLog） | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ PARTIAL | 缺 3 列 EXT-15 (`sync_token` / `records_total` / `records_filtered`) |
| Business Status | ❌ **契约漂移**：API 入参 `integrationId` → 反查 `crm_integration.platform` → 过滤 `crm_sync_log.platform`（**不是 integrationId** — I003） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3963 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I003 契约漂移 + I002 缺 EXT-15 列 |
| Evidence | `audit/evidence/F006-list-sync-logs/` | 待建 |
| Issue | **I003** + I002 | |

---

### F007 — CRM 集成 / Dashboard

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F007 | `feature-inventory.md` §1.1.2 row 7 |
| 前端路由 | `/crm-integration` | `router/index.ts:173` |
| 页面 | `views/crm-integration/index.vue` | |
| UI板块 | ❌ **无 Dashboard tab** | 前端未消费此接口 |
| Action | — | |
| Handler | — | |
| API | `crmIntegrationApi.dashboard()` | `api/crm-integration.ts:17`（已封装但未调用） |
| Method | GET | |
| Controller | `CrmIntegrationService.dashboard()` | **`CrmIntegrationService.java:142-155`** |
| Service | 合并于 Controller | |
| Mapper | `CrmIntegrationMapper.selectCount` + `CrmSyncLogMapper.selectCount` | |
| DB表 | `crm_integration` / `crm_sync_log` | db-snapshot.sql:3433 / 3963 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 普通租户 | `:144` |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅（表存在；缺 EXT-15 列但不影响 COUNT(*)） | |
| Business Status | ❌ **前端未消费** | 接口存在但前端无 tab |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 / 3963 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 前端缺 dashboard 视图 |
| Evidence | `audit/evidence/F007-dashboard/` | 待建 |
| Issue | （新增 ISSUE，未在 I001-I005；建议 I006）| |

---

### F008 — CRM 集成 / 手动 sync 窗口跳过

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F008 | `feature-inventory.md` §1.1.2 row 8 |
| 前端路由 | — | （F005 同步按钮链路内） |
| 页面 | — | |
| UI板块 | — | 前端对 `skipped=true` **无提示**（F027 / I005 关联） |
| Action | `POST /crm-integration/{id}/sync?force=null` 且 `!shouldRunForManualTrigger()` | |
| Handler | `sync(id)` → `syncNow()` → 窗口判断 | `CrmIntegrationService.java:96-103` |
| API | `POST /crm-integration/{id}/sync` | |
| Method | POST | |
| Controller | `CrmIntegrationService.syncNow` 窗口跳过分支 | **`CrmIntegrationService.java:96-103`** |
| Service | `CrmSyncScheduler.shouldRunForManualTrigger(cfg)` | **`CrmSyncScheduler.java:83-86`**（委托给 `shouldRun`） |
| Mapper | — | |
| DB表 | `crm_integration`（读 `lastIncrementalSyncAt` / `syncWindowMinutes`） | db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | — | 窗口判断在跨租户校验之后 |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ PARTIAL | 缺 `last_incremental_sync_at` / `sync_window_minutes` 列（I002） |
| Business Status | 完整（响应 `{skipped:true, reason:"not in cron window, use ?force=1 to override", lastIncrementalSyncAt, syncWindowMinutes}` HTTP 200） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I002（缺 EXT-15 列）+ I005（前端无 skipped 提示） |
| Evidence | `audit/evidence/F008-skip-window/` | 待建 |
| Issue | I002 / I005 | |

---

### F009 — CRM 集成 / 手动 sync 跨租户拒绝

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F009 | `feature-inventory.md` §1.1.2 row 9 |
| 前端路由 | — | （F005 同步按钮链路内） |
| 页面 | — | |
| UI板块 | — | |
| Action | `POST /crm-integration/{id}/sync` + `dataScope != "ALL"` ∧ `integration.tenantId != currentTenantId` | |
| Handler | `syncNow()` 跨租户校验 | `CrmIntegrationService.java:88-90` |
| API | `POST /crm-integration/{id}/sync` | |
| Method | POST | |
| Controller | `CrmIntegrationService.syncNow` 跨租户分支 | **`CrmIntegrationService.java:88-90`** |
| Service | — | |
| Mapper | — | |
| DB表 | `crm_integration` | db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | ✅ 跨租户隔离 OK | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ | |
| Business Status | 完整（返回 `BizCode.FORBIDDEN` HTTP 非 200） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | 静态可达，缺运行验证 |
| Evidence | `audit/evidence/F009-cross-tenant-deny/` | 待建 |
| Issue | — | |

---

### F010 — CRM 集成 / 自动 tick 扫描

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F010 | `feature-inventory.md` §1.1.2 row 10 |
| 前端路由 | — | （无前端入口 — 排程） |
| 页面 | — | |
| UI板块 | — | |
| Action | `@Scheduled` 自动触发（每 5 min / `fixedDelayString="${crm.sync.tick-interval-ms:300000}"` / `initialDelay=60_000L`） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `CrmSyncScheduler.tick()` | **`CrmSyncScheduler.java:47-56`** (`@Scheduled` `:45-46`) |
| Mapper | `CrmIntegrationMapper.selectList` + `updateById` | |
| DB表 | `crm_integration`（读 `status=ACTIVE ∧ sync_enabled=1`）| db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌（每条集成 try/catch） | `:58-64` |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | `updated_at`（自动） | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ PARTIAL | 缺 `sync_enabled` 列（I002）→ SQL 异常 → tick 提前 return |
| Business Status | 完整（try/catch 不阻塞其他 integration） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I002（缺 `sync_enabled` 列） |
| Evidence | `audit/evidence/F010-tick-scan/` | 待建 |
| Issue | I002 | |

---

### F011 — CRM 集成 / shouldRun 窗口判断

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F011 | `feature-inventory.md` §1.1.2 row 11 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | 排程 / 手动触发时调用 | `CrmSyncScheduler.tick` `:60` / `syncNow` `:97` |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `CrmSyncScheduler.shouldRun(CrmIntegration cfg)` | **`CrmSyncScheduler.java:72-78`** |
| Mapper | — | |
| DB表 | `crm_integration`（读 `lastIncrementalSyncAt` / `syncWindowMinutes`）| db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ PARTIAL | 缺 `last_incremental_sync_at` / `sync_window_minutes` 列（I002） |
| Business Status | 完整（`last=null → true`；`last + window ≤ now → true`） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I002 |
| Evidence | `audit/evidence/F011-shouldrun-window/` | 待建 |
| Issue | I002 | |

---

### F012 — CRM 集成 / known-ids 差集加载

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F012 | `feature-inventory.md` §1.1.2 row 12 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | `runIncremental` 入口前调用 | `CrmSyncScheduler.java:120` |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `CrmSyncScheduler.loadKnownExternalIds(cfg)` + `KNOWN_IDS_LIMIT=5000` | **`CrmSyncScheduler.java:190-209`** (`KNOWN_IDS_LIMIT` `:190`) |
| Mapper | `CrmContactMapService.list(LambdaQueryWrapper)` + `.last("LIMIT 5000")` | |
| DB表 | `crm_contact_map`（**缺失** — I002） | db-snapshot.sql grep 0 匹配 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 按 `(tenantId, platform)` 过滤（EXT-24 §0.7） | `:200-201` |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **PARTIAL-BLOCKER** | 表缺失 → `List` 抛 `BadSqlGrammarException`（MyBatis）→ catch log warn → 返回 empty Set → 失去差集去重 |
| Business Status | 完整（try/catch 兜底，差集失败不阻塞同步） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql (MISSING) | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | I002 `crm_contact_map` 表缺失 |
| Evidence | `audit/evidence/F012-known-ids/` | 待建 |
| Issue | **I002** | |

---

### F013 — CRM 集成 / CrmAdapter 接口契约

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F013 | `feature-inventory.md` §1.1.2 row 13 |
| 前端路由 | — | （接口契约，无直接 UI） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `CrmAdapter` 接口 | **`backend/src/main/java/com/meiyetec/crm/CrmAdapter.java:21`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | N/A | |
| Business Status | 完整（5 方法 + 5 DTO） | `platformCode` `:60` / `authenticate` / `fetchContactsIncremental` / `upsertContact` / `healthCheck` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | 静态可达，缺运行验证 |
| Evidence | `audit/evidence/F013-crm-adapter-interface/` | 待建 |
| Issue | — | |

---

### F014 — CRM 集成 / Salesforce 适配器

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F014 | `feature-inventory.md` §1.1.2 row 14 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | `CrmSyncScheduler.runIncremental` → `router.getAdapter("SALESFORCE")` | `CrmSyncScheduler.java:114` |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `SalesforceCrmAdapter` | **`backend/src/main/java/com/meiyetec/crm/SalesforceCrmAdapter.java:55-216`** |
| Mapper | — | |
| DB表 | — | （adapter 无直接 DB） |
| 关联表 | — | |
| 读/写 | RW（HTTP 调 SF + 写 crm_contact_map 走 Scheduler） | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ PARTIAL（间接依赖 `objectMappings` / `privateKey` / `loginUrl` 等 EXT-15 列） | db-snapshot.sql:3433 |
| Business Status | 完整（OAuth2 JWT RS256 + SOQL + upsert by externalId） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I002（依赖 `object_mappings` / `private_key` / `username` / `login_url` 等 EXT-15 列未到位） |
| Evidence | `audit/evidence/F014-salesforce-adapter/` | 待建 |
| Issue | I002 | |

> 注：`authenticate` `:70` / `parseObjectMappings` `:190` / `buildSoqlSelectClause` `:213` / `fetchContactsIncremental` `:229` / `upsertContact` `:307` / `healthCheck` `:333` / **未把 `knownExternalIds` 落到 SOQL**（仅 log `:255-257`）— 与 EXT-24 §0.3 一致

---

### F015 — CRM 集成 / Mock 适配器（⚠ ISSUE-I012）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F015 | `feature-inventory.md` §1.1.2 row 15 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | `CrmSyncScheduler.runIncremental` → `router.getAdapter("MOCK")`（fallback） | `CrmSyncScheduler.java:114` / `CrmAdapterRouter.java:30-34` |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `MockCrmAdapter` + `@ConditionalOnMissingBean(name="salesforceCrmAdapter")` | **`backend/src/main/java/com/meiyetec/crm/MockCrmAdapter.java:18-19`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R（无外部调用） | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED + Issue Open | |
| DB Status | N/A | |
| Business Status | 完整（3 条固定样本 + 滚动缓冲 `Deque<ExternalContact>` + known-id 内存去重）；**但 `@ConditionalOnMissingBean(name="salesforceCrmAdapter")` (`:21`) 触发 I012**：当 Salesforce Bean 注册后，Mock Bean **不会被注册**，`router.getAdapter("MOCK")` 返回 null → NPE |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | ⚠ **ISSUE-I012**（Open）：`MockCrmAdapter` Bean 注册条件依赖 Salesforce Bean 缺失；当 Salesforce 实际配置存在时，Mock 完全没注册；`router.getAdapter("MOCK")` 走 null 分支 → NPE。**不是 SoT vs 代码冲突**（SoT `ext-24 §0.6` 仅说 Mock 是 fallback，未声明 fallback 触发条件），故 **不挂 CONFLICT 子标签** |
| Evidence | `audit/evidence/F015-mock-adapter/` | 待建 |
| Issue | **I012** | PARTIAL-NON-BLOCKER（Open）|

> 注：`platformCode` `:38` / `authenticate` `:43` / `fetchContactsIncremental` `:55` / `upsertContact` `:95` / `healthCheck` `:104`
> **Phase 2.9 待处理**：3 个修复方案 A/B/C，详见 `phase-2.9-I012-fix-report.md`

> 注：`platformCode` `:38` / `authenticate` `:43` / `fetchContactsIncremental` `:55` / `upsertContact` `:95` / `healthCheck` `:104`

---

### F016 — CRM 集成 / HubSpot 适配器（⚠ ISSUE-I001）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F016 | `feature-inventory.md` §1.1.2 row 16 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | `CrmSyncScheduler.runIncremental` → `router.getAdapter("HUBSPOT")` | `CrmSyncScheduler.java:114` |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `HubSpotCrmAdapter` | **`backend/src/main/java/com/meiyetec/crm/HubSpotCrmAdapter.java`**（219 行，**已实现**） |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | N/A | |
| Business Status | 完整（OAuth2 refresh + PAT fallback + search API + batch/upsert by email） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **CONFLICT** | ⚠ **ISSUE-I001**：SoT (`ext-24-crm-integration.mdc` §0.6 / §5) 标 "未实现"，代码已实现完整 219 行 |
| Evidence | `audit/evidence/F016-hubspot-adapter/` | 待建 |
| Issue | **I001** | |

> 注：`platformCode` `:71` / `parseObjectMappings` `:90` / `authenticate` `:122` / `fetchContactsIncremental` `:177` / `upsertContact` `:276` / `healthCheck` `:321`

---

### F017 — CRM 集成 / Zoho 适配器（⚠ ISSUE-I001）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F017 | `feature-inventory.md` §1.1.2 row 17 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | `CrmSyncScheduler.runIncremental` → `router.getAdapter("ZOHO")` | `CrmSyncScheduler.java:114` |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `ZohoCrmAdapter` | **`backend/src/main/java/com/meiyetec/crm/ZohoCrmAdapter.java`**（230 行，**已实现**） |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | N/A | |
| Business Status | 完整（OAuth2 refresh + COQL + upsert by Email + accounts 域名推导） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **CONFLICT** | ⚠ **ISSUE-I001**：SoT (`ext-24-crm-integration.mdc` §0.6 / §5) 标 "未实现"，代码已实现完整 230 行 |
| Evidence | `audit/evidence/F017-zoho-adapter/` | 待建 |
| Issue | **I001** | |

> 注：`platformCode` `:72` / `parseObjectMappings` `:91` / `authenticate` `:122` / `deriveAccountsBase` `:177` / `fetchContactsIncremental` `:191` / `upsertContact` `:272` / `healthCheck` `:319`

---

### F018 — CRM 集成 / Platform 分发 + Mock fallback（⚠ ISSUE-I001）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F018 | `feature-inventory.md` §1.1.2 row 18 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | `CrmSyncScheduler.runIncremental` → `router.getAdapter(platform)` | `CrmSyncScheduler.java:114` |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `CrmAdapterRouter` | **`backend/src/main/java/com/meiyetec/crm/CrmAdapterRouter.java`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | N/A | |
| Business Status | 完整（`platform` 转大写查 Map → miss → MOCK fallback；`null` → `getAdapter("MOCK")`） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **CONFLICT** | ⚠ **ISSUE-I001**：SoT (`ext-24-crm-integration.mdc` §5) 标 "router.getAdapter 返回 null → NPE"，实际 **有 MOCK fallback**（`:30-34`） |
| Evidence | `audit/evidence/F018-router/` | 待建 |
| Issue | **I001** | |

> 注：构造函数 `:22` / `getAdapter` `:28` / `hasAdapter` `:38` / `listPlatforms` `:42`

---

### F019 — CRM 集成 / CrmIntegration 配置实体

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F019 | `feature-inventory.md` §1.1.2 row 19 |
| 前端路由 | — | （实体，无直接 UI） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | — | |
| Mapper | — | |
| DB表 | `crm_integration` | db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | `created_at / updated_at`（`@TableField(fill = FieldFill.INSERT_UPDATE)`） | `CrmIntegration.java:55-58` |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ PARTIAL | 缺 9 列 EXT-15 (`sync_token` / `sync_schedule` / `sync_enabled` / `last_incremental_sync_at` / `private_key` / `username` / `login_url` / `object_mappings` / `sync_window_minutes`) |
| Business Status | 完整（`privateKey` `JsonProperty.WRITE_ONLY` `:79` + `confirmPassword` WRITE_ONLY `:99`） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I002（缺 EXT-15 列） |
| Evidence | `audit/evidence/F019-crm-integration-entity/` | 待建 |
| Issue | I002 | |

---

### F020 — CRM 集成 / CrmContactMap 映射实体

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F020 | `feature-inventory.md` §1.1.2 row 20 |
| 前端路由 | — | （实体，无直接 UI） |
| 页面 | — | |
| UI板块 | — | |
| Action | `CrmSyncScheduler.upsertContactMap` 写库 | `CrmSyncScheduler.java:161-181` |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `CrmSyncScheduler.upsertContactMap(cfg, ec)` | **`CrmSyncScheduler.java:161-181`** |
| Mapper | `CrmContactMapMapper`（含 `LambdaQueryWrapper` + `eq(platform, external_id)`） | `backend/src/main/java/com/meiyetec/mapper/CrmContactMapMapper.java:264` |
| DB表 | `crm_contact_map`（**缺失** — I002） | db-snapshot.sql grep 0 匹配 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌（在 `@Transactional` `syncNow` 内） | `CrmIntegrationService.java:84` |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | `create_time / update_time`（`@TableField(fill = FieldFill.INSERT_UPDATE)`） | `CrmContactMap.java:36-39` |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **PARTIAL-BLOCKER** | 表不存在 → 抛 `BadSqlGrammarException` → upsertContactMap 失败 → `fail++` 计入 `crm_sync_log.fail_count` |
| Business Status | 完整（uk `uk_platform_external (platform, external_id)` 唯一键；`conflictFlag 0/1`；`localContactId` 可空 INBOUND） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql (MISSING) | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | I002（表缺失） |
| Evidence | `audit/evidence/F020-crm-contact-map-entity/` | 待建 |
| Issue | **I002** | |

---

### F021 — CRM 集成 / CrmSyncLog 日志实体

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F021 | `feature-inventory.md` §1.1.2 row 21 |
| 前端路由 | — | （实体，无直接 UI） |
| 页面 | — | |
| UI板块 | — | |
| Action | `CrmSyncScheduler.runIncremental` 写库 | `CrmSyncScheduler.java:97-159` |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | — | |
| Mapper | `CrmSyncLogMapper`（含 `LambdaQueryWrapper` + `insert` / `updateById`） | `backend/src/main/java/com/meiyetec/mapper/CrmSyncLogMapper.java:253` |
| DB表 | `crm_sync_log` | db-snapshot.sql:3963 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌（在 `@Transactional` `syncNow` 内） | `CrmIntegrationService.java:84` |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | `created_at`（`@TableField(fill = FieldFill.INSERT)`） | `CrmSyncLog.java:32` |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ PARTIAL | 缺 3 列 EXT-15 (`sync_token` / `records_total` / `records_filtered`) |
| Business Status | 完整（`syncType` INCREMENTAL/FULL/MANUAL；`direction` PUSH/PULL/BIDIRECTIONAL；`status` RUNNING/SUCCESS/FAILED） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3963 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I002（缺 EXT-15 列） |
| Evidence | `audit/evidence/F021-crm-sync-log-entity/` | 待建 |
| Issue | I002 | |

---

### F022 — CRM 集成 / 三表存在性 + EXT-15 ALTER

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F022 | `feature-inventory.md` §1.1.2 row 22 |
| 前端路由 | — | （DDL，无直接 UI） |
| 页面 | — | |
| UI板块 | — | |
| Action | 执行 `migrate_ext_15_crm_real.sql` | **`backend/src/main/resources/db/sql/migrate_ext_15_crm_real.sql`** |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | — | |
| Mapper | — | |
| DB表 | `crm_integration` / `crm_sync_log` | db-snapshot.sql:3433 / 3963 |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0（DDL 存在）| |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ PARTIAL | `crm_integration` 缺 9 列 EXT-15；`crm_sync_log` 缺 3 列 EXT-15 |
| Business Status | DDL 文件存在但**未在生产执行**（preflight §8.1 + I002） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 / 3963（缺 EXT-15 列） | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I002（缺 EXT-15 列） |
| Evidence | `audit/evidence/F022-crm-ext15-ddl/` | 待建 |
| Issue | I002 | |

> 注：migrate_ext_15_crm_real.sql 行 5-19 (`ALTER crm_integration` 加 9 列 + 2 索引) / 行 21-43 (`CREATE crm_contact_map`) / 行 45-49 (`ALTER crm_sync_log` 加 3 列)

---

### F023 — CRM 集成 / crm_contact_map 表存在性

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F023 | `feature-inventory.md` §1.1.2 row 23 |
| 前端路由 | — | （DDL，无直接 UI） |
| 页面 | — | |
| UI板块 | — | |
| Action | 执行 `migrate_ext_15_crm_real.sql:21-43`（`DROP TABLE IF EXISTS crm_contact_map; CREATE TABLE crm_contact_map ...`） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | — | |
| Mapper | — | |
| DB表 | `crm_contact_map`（**缺失** — I002） | **db-snapshot.sql grep 0 匹配** |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | `create_time / update_time`（DDL 内） | |
| Static Status | L0（DDL 存在） | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **PARTIAL-BLOCKER** | **db-snapshot.sql 已 grep 验证 0 匹配**（Phase 2 验证完成） |
| Business Status | DDL 存在但**未在生产执行**（preflight §8.1 + I002） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql (MISSING — 0 匹配) | |
| Last Verified At | 2026-09-12 21:48 (UTC+8) — Phase 2 静态验证 | |
| **Final Result** | **PARTIAL-BLOCKER** | I002（表缺失 + 缺 EXT-15 列） |
| Evidence | `audit/evidence/F023-crm-contact-map-table/` | 待建 |
| Issue | **I002** | |

> **I002 Phase 2 验证方法**：
> ```powershell
> Select-String -Path audit/baseline/db-snapshot.sql -Pattern 'crm_contact_map' -CaseSensitive:$false
> # → 0 匹配（输出空）
> ```
> 验证完成时间：2026-09-12 21:48 (UTC+8)

---

### F024 — CRM 集成 / 前端 / 集成列表 UI

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F024 | `feature-inventory.md` §1.1.2 row 24 |
| 前端路由 | `/crm-integration` | `router/index.ts:173` |
| 页面 | `views/crm-integration/index.vue` | |
| UI板块 | 「集成配置」tab el-table | `index.vue:4-23` |
| Action | 页面加载 → `loadList()` | `index.vue:94` |
| Handler | `loadList()` | `index.vue:76` |
| API | `crmIntegrationApi.list()` | `api/crm-integration.ts:6-7` |
| Method | GET | |
| Controller | `CrmIntegrationService.list` | `CrmIntegrationService.java:47-56` |
| Service | — | |
| Mapper | `CrmIntegrationMapper.selectList` | |
| DB表 | `crm_integration` | db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 普通租户 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅（表存在；F002 同链路） | |
| Business Status | 完整（列 `id / platform / shopName / syncDirection / lastSyncAt / status` + 操作列 "同步"） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | |
| Evidence | `audit/evidence/F024-list-ui/` | 待建 |
| Issue | — | |

---

### F025 — CRM 集成 / 前端 / 新建集成表单

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F025 | `feature-inventory.md` §1.1.2 row 25 |
| 前端路由 | `/crm-integration` | `router/index.ts:173` |
| 页面 | `views/crm-integration/index.vue` | |
| UI板块 | 「新建集成」对话框 el-form | `index.vue:38-61` |
| Action | 填写 → 「保存」 | `index.vue:59` |
| Handler | `doAdd()` | `index.vue:78-82` |
| API | `crmIntegrationApi.create(addForm.value)` | `api/crm-integration.ts:5` |
| Method | POST | |
| Controller | `CrmIntegrationService.create` | `CrmIntegrationService.java:36-44` |
| Service | — | |
| Mapper | `CrmIntegrationMapper.insert` | |
| DB表 | `crm_integration` | db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | ❌ | |
| 权限 | 普通租户 | |
| 软删除 | ❌ | |
| 审计字段 | `created_at`（MyBatis auto-fill） | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅（表存在；缺 EXT-15 列但前端表单不写这些字段 — F028） | |
| Business Status | ⚠ **不完整**：表单字段仅 `platform / shopName / syncDirection / autoSync`（`:43-55`），**缺** `clientId / clientSecret / privateKey / username / loginUrl / objectMappings`（F028） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | F028 输入缺失（依赖 SQL 直写） |
| Evidence | `audit/evidence/F025-create-form/` | 待建 |
| Issue | F028 / I005 | |

---

### F026 — CRM 集成 / 前端 / 同步日志 tab（⚠ ISSUE-I003）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F026 | `feature-inventory.md` §1.1.2 row 26 |
| 前端路由 | `/crm-integration` | `router/index.ts:173` |
| 页面 | `views/crm-integration/index.vue` | |
| UI板块 | 「同步日志」tab table | `index.vue:24-33` |
| Action | 切换 tab → `loadLogs()` | `index.vue:94` |
| Handler | `loadLogs()` | `index.vue:77` |
| API | `crmIntegrationApi.listSyncLogs()` | `api/crm-integration.ts:15-16` |
| Method | GET | |
| Controller | `CrmIntegrationService.listSyncLogs` | `CrmIntegrationService.java:128-139` |
| Service | — | |
| Mapper | `CrmIntegrationMapper.selectById` + `CrmSyncLogMapper.selectList` | |
| DB表 | `crm_sync_log` | db-snapshot.sql:3963 |
| 关联表 | `crm_integration`（反查 platform） | db-snapshot.sql:3433 |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 普通租户 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅（表存在） | |
| Business Status | ⚠ **契约漂移**：UI 列 `integrationId` (`:27`) vs `crm_sync_log` 表**无此字段**（I003） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3963 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I003（UI 列名漂移） |
| Evidence | `audit/evidence/F026-sync-log-tab/` | 待建 |
| Issue | **I003** | |

---

### F027 — CRM 集成 / 前端 / 同步 API 未传 `?force=1`（⚠ PARTIAL-NON-BLOCKER）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F027 | `feature-inventory.md` §1.1.2 row 27 |
| 前端路由 | `/crm-integration` | `router/index.ts:173` |
| 页面 | `views/crm-integration/index.vue` | |
| UI板块 | 「同步」按钮 | `index.vue:19` |
| Action | 点击「同步」 | |
| Handler | `sync(id)` → `crmIntegrationApi.syncNow(id)` | `index.vue:88-91` |
| API | `crmIntegrationApi.syncNow(id)` | **`api/crm-integration.ts:14`** |
| Method | POST | |
| Controller | `CrmIntegrationService.syncNow` | `CrmIntegrationService.java:85-125` |
| Service | `CrmSyncScheduler.runIncremental` | `CrmSyncScheduler.java:97-159` |
| Mapper | — | |
| DB表 | `crm_integration` / `crm_sync_log` | db-snapshot.sql:3433 / 3963 |
| 关联表 | `crm_contact_map`（缺失） | |
| 读/写 | RW | |
| 事务 | ✅ | `CrmIntegrationService.java:84` |
| 权限 | 跨租户隔离 OK | `:88-90` |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ PARTIAL（同 F005） | |
| Business Status | ⚠ **不完整**：API 未传 `?force=1` → 窗口外返回 `skipped=true` 但 UI 无提示；按钮无 loading / 无二次确认 | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 / 3963 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | ⚠ 业务影响：窗口外点击 → UI 显示「已触发同步」+ ElMessage.success，实际 skipped（用户无感） |
| Evidence | `audit/evidence/F027-sync-now-api/` | 待建 |
| Issue | I005（关联 EXT-24 §0.1 ⚠） | |

---

### F028 — CRM 集成 / 前端 / objectMappings 输入入口缺失

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F028 | `feature-inventory.md` §1.1.2 row 28 |
| 前端路由 | `/crm-integration` | `router/index.ts:173` |
| 页面 | `views/crm-integration/index.vue` | |
| UI板块 | 「新建集成」对话框 | `index.vue:38-61` |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | 后端 `CrmIntegrationService.create` 接受 `objectMappings` 字段（**字段在 entity 中存在**：`CrmIntegration.java:85`） | `CrmIntegrationService.java:36-44` |
| Service | — | |
| Mapper | — | |
| DB表 | `crm_integration` | db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ PARTIAL | 缺 `object_mappings` 列（I002） |
| Business Status | ⚠ **不完整**：前端表单**无** `objectMappings` 字段；启用 Salesforce 自定义字段映射（`Email__c` 等）需走 SQL 直写 `crm_integration.object_mappings` | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | ⚠ 业务影响：客户用自定义字段映射必须 SQL 直写 |
| Evidence | `audit/evidence/F028-object-mappings/` | 待建 |
| Issue | I005（关联 EXT-24 §5 ⚠） | |

---

### F029 — CRM 集成 / 状态机 / `crm_integration.status`（⚠ I005）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F029 | `feature-inventory.md` §1.1.2 row 29 |
| 前端路由 | `/crm-integration` | `router/index.ts:173` |
| 页面 | `views/crm-integration/index.vue` | |
| UI板块 | 「状态」列 el-switch | `index.vue:14` |
| Action | 点击 el-switch | `index.vue:84-87` |
| Handler | `toggle(row)` | `index.vue:84-87` |
| API | `crmIntegrationApi.toggle(row.id, { status: row.status })` | `api/crm-integration.ts:9` |
| Method | POST | |
| Controller | `CrmIntegrationService.toggle` | `CrmIntegrationService.java:68-77` |
| Service | `CrmSyncScheduler.runIncremental` 同步成功 / 失败分支 | **`CrmSyncScheduler.java:112-115`（成功 → ACTIVE + errorMessage=null）** / **`:131-135`（失败 → ERROR + errorMessage=ex.getMessage()）** |
| Mapper | `CrmIntegrationMapper.updateById` | |
| DB表 | `crm_integration` | db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌（toggle）/ ✅（runIncremental 在 `@Transactional syncNow` 内） | |
| 权限 | toggle 无隔离 / runIncremental 走 syncNow 隔离 | |
| 软删除 | ❌ | |
| 审计字段 | `updated_at`（自动） | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅（表存在；缺 EXT-15 列不影响 status 字段） | |
| Business Status | ⚠ **契约漂移**：前端 `ACTIVE/PAUSED`（`:14`） vs 后端 enum `ACTIVE/DISABLED/ERROR`（`.mdc` §4） vs toggle 不校验 enum（`CrmIntegrationService.java:73-75`） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I005（enum 漂移） |
| Evidence | `audit/evidence/F029-status-machine/` | 待建 |
| Issue | **I005** | |

---

### F030 — CRM 集成 / 排程配置 / `syncWindowMinutes` + `syncSchedule`

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F030 | `feature-inventory.md` §1.1.2 row 30 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | 排程 / 手动 sync 时读 | `CrmSyncScheduler.tick` `:60` / `shouldRun` `:73-74` |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `CrmSyncScheduler.shouldRun(cfg)` | `CrmSyncScheduler.java:72-78` |
| Mapper | — | |
| DB表 | `crm_integration`（读 `syncSchedule` / `syncWindowMinutes`）| db-snapshot.sql:3433 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ PARTIAL | 缺 `sync_schedule` / `sync_window_minutes` 列（I002） |
| Business Status | 完整（`syncSchedule` 默认 `"0 */30 * * * *"`；`syncWindowMinutes` 默认 30；EXT-15 DDL 含默认值；前端**无**配置入口） | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql @ 3433 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I002（缺 EXT-15 列） + 前端无配置入口（依赖 SQL/后端） |
| Evidence | `audit/evidence/F030-schedule-config/` | 待建 |
| Issue | I002 | |

---

## Batch 1.1 统计

| 维度 | 数量 | 说明 |
|---|---|---|
| Feature 总数 | 30 | F001-F030 |
| UNVERIFIED（缺运行证据） | 6 | F002 / F009 / F013 / F018 / F024 / F026（其中 F026 标 PARTIAL；**F015 已转 PARTIAL-NON-BLOCKER 关联 I012**）|
| PARTIAL-NON-BLOCKER | 18 | F001 / F003 / F004 / F005 / F008 / F010 / F011 / F014 / **F015** / F019 / F021 / F022 / F025 / F026 / F027 / F028 / F029 / F030 |
| PARTIAL-BLOCKER | 4 | F005 / F012 / F020 / F023（DB 列缺失导致运行时 NPE） |
| CONFLICT（SoT vs 代码） | 3 | F016 / F017 / F018（ISSUE-I001） |

**最终状态分布**（按用户指令：F016/F017/F018 标 CONFLICT，F004/F006/F026/F029 标 PARTIAL-NON-BLOCKER — 已落地）

---

## 链路图（核心调用链）

```
┌────────────────────────────────────────────────────────────────┐
│  前端 /crm-integration                                          │
│  views/crm-integration/index.vue                                │
│  router/index.ts:173                                            │
└─────────────┬──────────────────────────────────────────────────┘
              │ crmIntegrationApi.syncNow(id)  ←未传 ?force=1 (F027)
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Controller: CrmIntegrationService (命名误导 - I004)           │
│  backend/src/main/java/com/meiyetec/service/                   │
│    CrmIntegrationService.java                                  │
│  ├─ F001 create :36-44                                          │
│  ├─ F002 list   :47-56                                          │
│  ├─ F003 update :59-66                                          │
│  ├─ F004 toggle :68-77                                          │
│  ├─ F005 syncNow:85-125 (+@Transactional :84)                   │
│  ├─ F006 listSyncLogs:128-139                                   │
│  └─ F007 dashboard:142-155                                      │
└─────────────┬──────────────────────────────────────────────────┘
              │
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Service: CrmSyncScheduler (核心调度)                          │
│  backend/src/main/java/com/meiyetec/crm/CrmSyncScheduler.java  │
│  ├─ F010 tick           :47-56 (@Scheduled :45-46)              │
│  ├─ F011 shouldRun      :72-78                                  │
│  ├─ F008 shouldRunForManualTrigger:83-86                        │
│  ├─ F005 runIncremental :97-159                                 │
│  ├─ F029 状态机分支     :112-115 (成功 ACTIVE) / :131-135 (ERROR)│
│  ├─ F020 upsertContactMap:161-181                               │
│  ├─ F012 KNOWN_IDS_LIMIT:190 (5000)                              │
│  └─ F012 loadKnownExternalIds:192-209                           │
└─────────────┬──────────────────────────────────────────────────┘
              │ router.getAdapter(platform)
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Router: CrmAdapterRouter                                      │
│  backend/src/main/java/com/meiyetec/crm/CrmAdapterRouter.java │
│  ├─ F018 构造函数 :22                                           │
│  ├─ F018 getAdapter:28 (MOCK fallback :30-34)                   │
│  ├─ F018 hasAdapter:38                                          │
│  └─ F018 listPlatforms:42                                        │
└─────────────┬──────────────────────────────────────────────────┘
              │ platformCode() 分发
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Adapters: 实现 CrmAdapter 接口 (F013)                          │
│  ├─ F014 SalesforceCrmAdapter.java:55-216                      │
│  │   - authenticate:70  parseObjectMappings:190                │
│  │   - buildSoqlSelectClause:213  fetchContactsIncremental:229 │
│  │   - upsertContact:307  healthCheck:333                      │
│  ├─ F016 HubSpotCrmAdapter.java (CONFLICT - I001)              │
│  │   - authenticate:122  parseObjectMappings:90                │
│  │   - fetchContactsIncremental:177  upsertContact:276         │
│  │   - healthCheck:321                                          │
│  ├─ F017 ZohoCrmAdapter.java (CONFLICT - I001)                 │
│  │   - authenticate:122  parseObjectMappings:91                │
│  │   - deriveAccountsBase:177  fetchContactsIncremental:191    │
│  │   - upsertContact:272  healthCheck:319                      │
│  └─ F015 MockCrmAdapter.java:18-19 (@ConditionalOnMissingBean) │
│      - authenticate:43  fetchContactsIncremental:55            │
│      - upsertContact:95  healthCheck:104                        │
└─────────────┬──────────────────────────────────────────────────┘
              │ upsertContactMap
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Entities:                                                      │
│  ├─ F019 CrmIntegration (entity/CrmIntegration.java)            │
│  │   - privateKey WRITE_ONLY :79                                │
│  │   - objectMappings :85                                       │
│  │   - syncSchedule/syncWindowMinutes :73, :76                  │
│  ├─ F020 CrmContactMap (entity/CrmContactMap.java:18-39)        │
│  │   - uk uk_platform_external (platform, external_id)         │
│  └─ F021 CrmSyncLog (entity/CrmSyncLog.java:16-40)              │
│      - syncType/direction/status fields                         │
└─────────────┬──────────────────────────────────────────────────┘
              │ MyBatis Mapper
              ▼
┌────────────────────────────────────────────────────────────────┐
│  DB:                                                            │
│  ├─ crm_integration (db-snapshot.sql:3433) — 22 列              │
│  │   ❌ 缺 9 列 EXT-15 (I002)                                   │
│  ├─ crm_sync_log (db-snapshot.sql:3963) — 12 列                 │
│  │   ❌ 缺 3 列 EXT-15 (I002)                                   │
│  └─ crm_contact_map ❌ 表不存在 (db-snapshot 0 匹配, I002)     │
└────────────────────────────────────────────────────────────────┘
```

---

## 已知约束（来自 Issue Register）

| Issue ID | 影响 Feature | Phase 2 Result |
|---|---|---|
| **I001** | F016 / F017 / F018 | **CONFLICT** |
| **I002** | F001 / F003 / F005 / F008 / F010 / F011 / F012 / F019 / F020 / F021 / F022 / F023 / F025 / F028 / F030 | F005 / F012 / F020 / F023 → **PARTIAL-BLOCKER**；其他 → **PARTIAL-NON-BLOCKER** |
| **I003** | F006 / F026 | **PARTIAL-NON-BLOCKER** |
| **I004** | F001-F008 + F029 | Backlog（不阻塞 Phase 2） |
| **I005** | F004 / F027 / F029 | **PARTIAL-NON-BLOCKER** |

---

## 验证范围声明

**本文件仅完成 Phase 2 静态追溯（Phase 2 — DB Schema Truth + Full-Stack Traceability Matrix）**：

1. ✅ **I002 Phase 2 验证**：`db-snapshot.sql` 已 grep 验证 `crm_contact_map` 0 匹配（确认缺失）
2. ✅ **调用图完整**：前端 → API → Controller → Service → Adapter → Entity → DB 全栈可达（L0）
3. ✅ **静态 Status**：所有 Feature 标 L0（代码存在）
4. ✅ **DB Status**：除 F005/F012/F020/F023 标 PARTIAL-BLOCKER 外，其他依赖 `crm_integration`/`crm_sync_log` 的 Feature 也标 PARTIAL-NON-BLOCKER（因缺 EXT-15 列）
5. ✅ **CONFLICT 标注**：F016/F017/F018 按 I001 标 CONFLICT（不判 MISSING）
6. ✅ **PARTIAL-NON-BLOCKER 标注**：F004/F006/F026/F029 按 I005/I003 标 PARTIAL-NON-BLOCKER

**不**包括：
- ❌ Phase 3.5 Diff Audit（重构类变更 Phase 4 前做）
- ❌ Phase 4 运行时计划（待 Phase 2 完成后启动）
- ❌ Phase 5 L4/L5a/L5b 验证（需运行项目）
- ❌ 修改任何业务代码（规则 6：审计阶段只记录不修复）

---

---

## 链路图（核心调用链）

```
┌────────────────────────────────────────────────────────────────┐
│  前端 /crm-integration                                          │
│  views/crm-integration/index.vue                                │
│  router/index.ts:173                                            │
└─────────────┬──────────────────────────────────────────────────┘
              │ crmIntegrationApi.syncNow(id)  ←未传 ?force=1 (F027)
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Controller: CrmIntegrationService (命名误导 - I004)           │
│  backend/src/main/java/com/meiyetec/service/                   │
│    CrmIntegrationService.java                                  │
│  ├─ F001 create :36-44                                          │
│  ├─ F002 list   :47-56                                          │
│  ├─ F003 update :59-66                                          │
│  ├─ F004 toggle :68-77                                          │
│  ├─ F005 syncNow:85-125 (+@Transactional :84)                   │
│  ├─ F006 listSyncLogs:128-139                                   │
│  └─ F007 dashboard:142-155                                      │
└─────────────┬──────────────────────────────────────────────────┘
              │
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Service: CrmSyncScheduler (核心调度)                          │
│  backend/src/main/java/com/meiyetec/crm/CrmSyncScheduler.java  │
│  ├─ F010 tick           :47-56 (@Scheduled :45-46)              │
│  ├─ F011 shouldRun      :72-78                                  │
│  ├─ F008 shouldRunForManualTrigger:83-86                        │
│  ├─ F005 runIncremental :97-159                                 │
│  ├─ F029 状态机分支     :112-115 (成功 ACTIVE) / :131-135 (ERROR)│
│  ├─ F020 upsertContactMap:161-181                               │
│  ├─ F012 KNOWN_IDS_LIMIT:190 (5000)                              │
│  └─ F012 loadKnownExternalIds:192-209                           │
└─────────────┬──────────────────────────────────────────────────┘
              │ router.getAdapter(platform)
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Router: CrmAdapterRouter                                      │
│  backend/src/main/java/com/meiyetec/crm/CrmAdapterRouter.java │
│  ├─ F018 构造函数 :22                                           │
│  ├─ F018 getAdapter:28 (MOCK fallback :30-34)                   │
│  ├─ F018 hasAdapter:38                                          │
│  └─ F018 listPlatforms:42                                        │
└─────────────┬──────────────────────────────────────────────────┘
              │ platformCode() 分发
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Adapters: 实现 CrmAdapter 接口 (F013)                          │
│  ├─ F014 SalesforceCrmAdapter.java:55-216                      │
│  ├─ F016 HubSpotCrmAdapter.java (CONFLICT - I001)              │
│  ├─ F017 ZohoCrmAdapter.java (CONFLICT - I001)                 │
│  └─ F015 MockCrmAdapter.java:18-19 (@ConditionalOnMissingBean) │
└─────────────┬──────────────────────────────────────────────────┘
              │ upsertContactMap
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Entities:                                                      │
│  ├─ F019 CrmIntegration (entity/CrmIntegration.java)            │
│  ├─ F020 CrmContactMap (entity/CrmContactMap.java:18-39)        │
│  └─ F021 CrmSyncLog (entity/CrmSyncLog.java:16-40)              │
└─────────────┬──────────────────────────────────────────────────┘
              │ MyBatis Mapper
              ▼
┌────────────────────────────────────────────────────────────────┐
│  DB:                                                            │
│  ├─ crm_integration (db-snapshot.sql:3433) — 22 列              │
│  │   ❌ 缺 9 列 EXT-15 (I002)                                   │
│  ├─ crm_sync_log (db-snapshot.sql:3963) — 12 列                 │
│  │   ❌ 缺 3 列 EXT-15 (I002)                                   │
│  └─ crm_contact_map ❌ 表不存在 (db-snapshot 0 匹配, I002)     │
└────────────────────────────────────────────────────────────────┘
```

---

## 已知约束（来自 Issue Register）

| Issue ID | 影响 Feature | Phase 2 Result |
|---|---|---|
| **I001** | F016 / F017 / F018 | **CONFLICT** |
| **I002** | F001 / F003 / F005 / F008 / F010 / F011 / F012 / F019 / F020 / F021 / F022 / F023 / F025 / F028 / F030 | F005 / F012 / F020 / F023 → **PARTIAL-BLOCKER**；其他 → **PARTIAL-NON-BLOCKER** |
| **I003** | F006 / F026 | **PARTIAL-NON-BLOCKER** |
| **I004** | F001-F008 + F029 | Backlog（不阻塞 Phase 2） |
| **I005** | F004 / F027 / F029 | **PARTIAL-NON-BLOCKER** |

---

## 验证范围声明

**本文件仅完成 Phase 2 静态追溯（Phase 2 — DB Schema Truth + Full-Stack Traceability Matrix）**：

1. ✅ **I002 Phase 2 验证**：`db-snapshot.sql` 已 grep 验证 `crm_contact_map` 0 匹配（确认缺失）
2. ✅ **调用图完整**：前端 → API → Controller → Service → Adapter → Entity → DB 全栈可达（L0）
3. ✅ **静态 Status**：所有 Feature 标 L0（代码存在）
4. ✅ **DB Status**：除 F005/F012/F020/F023 标 PARTIAL-BLOCKER 外，其他依赖 `crm_integration`/`crm_sync_log` 的 Feature 也标 PARTIAL-NON-BLOCKER（因缺 EXT-15 列）
5. ✅ **CONFLICT 标注**：F016/F017/F018 按 I001 标 CONFLICT（不判 MISSING）
6. ✅ **PARTIAL-NON-BLOCKER 标注**：F004/F006/F026/F029 按 I005/I003 标 PARTIAL-NON-BLOCKER

**不**包括：
- ❌ Phase 3.5 Diff Audit（重构类变更 Phase 4 前做）
- ❌ Phase 4 运行时计划（待 Phase 2 完成后启动）
- ❌ Phase 5 L4/L5a/L5b 验证（需运行项目）
- ❌ 修改任何业务代码（规则 6：审计阶段只记录不修复）

---

---

## Batch 1.4 — EXT-19 多语言预约确认（33 Features）

> **范围来源**：`audit/baseline/scope-freeze-batch-1.4.md` §2.1 + `audit/feature-inventory.md` §Batch 1.4（F081-F113）
> **DB 实际状态（Phase 2 静态验证）**：
> - `overseas_booking` **不存在**于 db-snapshot.sql（grep 0 匹配）→ **MISSING（I038）**
> - `member_message` **不存在**于 db-snapshot.sql（grep 0 匹配）→ **MISSING（I045）**
> - `mkt_channel_config` 存在（db-snapshot.sql grep 匹配）→ 存在
> - `pos_store` 存在（db-snapshot.sql grep 匹配）→ 存在
> **Issue 决策来源**：用户 2026-09-14 16:00 确认
> - I038 → MISSING，不跑迁移（**第 4 次强调**：统一 Post-Audit 跑批）
> - I039 → CONFLICT 中立标（沿用 I001/I018/I026 模式）
> - I040/I041/I042 → Shadow，Post-Audit
> - I043 → CONFLICT 中立标
> - I044 → backlog 统一整改（**第 4 次命名异味**：I004/I019/I029 同模式）
> - I045 → MISSING，不跑迁移（**第 4 次强调**）
> **Batch 起点**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`
> **Feature Inventory 编号一致性**：本段编号与 `feature-inventory.md` §Batch 1.4（F081-F113）严格对齐

---


### F081 鈥?娴峰棰勭害 / OverseasBooking 瀹炰綋锛堝惈 miniProgramOpenId锛?

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F081 | `feature-inventory.md` 搂Batch 1.4 row 1 |
| 鍓嶇璺敱 | 鈥?| 锛堝疄浣擄紝鏃犵洿鎺?UI锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鈥?| |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | 鈥?| |
| Mapper | `OverseasBookingMapper` | `backend/src/main/java/com/meiyetec/mapper/OverseasBookingMapper.java` |
| DB琛?| `overseas_booking`锛?*缂哄け** 鈥?I038锛?| **db-snapshot.sql grep 0 鍖归厤** |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| RW | |
| 浜嬪姟 | 鈥?| |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| 鉂?| 锛圖DL 涓棤 `deleted` 鍒楋級 |
| 瀹¤瀛楁 | `created_at / updated_at`锛坄@TableField(fill=FieldFill.INSERT/INSERT_UPDATE)`锛?| `OverseasBooking.java:49-51` |
| Static Status | L0 | 浠ｇ爜瀛樺湪锛?6 鍒?+ 4 绱㈠紩 entity锛?|
| Runtime Status | **MISSING** | 琛ㄤ笉瀛樺湪锛岃縼绉绘湭璺?|
| DB Status | 鉂?**MISSING** | db-snapshot.sql 0 鍖归厤锛圥hase 2 闈欐€侀獙璇侊級 |
| Business Status | 瀹屾暣锛坄@TableName("overseas_booking")` + 26 瀛楁鍚?`miniProgramOpenId` L17锛?| `entity/OverseasBooking.java:11-51` |
| E2E Status | UNVERIFIED | Playwright 鏈厤缃?|
| Verified Commit | 鈥?| Phase 5+ |
| DB Snapshot | db-snapshot.sql (MISSING) | |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 闈欐€侀獙璇?|
| **Final Result** | **MISSING** | **I038**锛坄overseas_booking` 琛ㄦ湭璺戣縼绉伙紱绗?4 娆″己璋冧笉璺戯紝Post-Audit 缁熶竴璺戞壒锛?|
| Evidence | `audit/evidence/F081-overseas-booking-entity/` | 寰呭缓 |
| Issue | **I038** | |

> 娉細瀛楁锛歚id:13 / tenantId:14 / bookingNo:15 / source:16 / externalId:17 / customerName:18 / customerPhone:19 / customerEmail:20 / customerLocale:21 / miniProgramOpenId:22` / serviceId / serviceName / staffId / storeId / bookingDate / bookingTime / notes / status / ipAddress / userAgent / rawPayload / convertedMemberId / convertedOrderId / createdAt:50 / updatedAt:51

---

### F082 鈥?娴峰棰勭害 / overseas_booking DDL锛堝缓琛?+ ALTER锛?

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F082 | `feature-inventory.md` 搂Batch 1.4 row 2 |
| 鍓嶇璺敱 | 鈥?| 锛圖DL锛屾棤鐩存帴 UI锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鎵ц `migrate_ext_19_mini_program_openid.sql` | |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | 鈥?| |
| Mapper | 鈥?| |
| DB琛?| `overseas_booking`锛?*缂哄け** 鈥?I038锛?| **db-snapshot.sql grep 0 鍖归厤** |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| 鈥?| |
| 浜嬪姟 | 鈥?| |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | `created_at / updated_at`锛圖DL 鍐?`DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP`锛?| `migrate_ext_19_mini_program_openid.sql:36-37` |
| Static Status | L0锛圖DL 瀛樺湪锛?| |
| Runtime Status | UNVERIFIED | |
| DB Status | 鉂?**MISSING** | DDL 瀛樺湪浣嗘湭鎵ц |
| Business Status | 瀹屾暣锛? 绱㈠紩 `idx_tenant_status/store/source/created` + utf8mb4_bin + MySQL 8.0.29+ 鍏煎锛?| `migrate_ext_19_mini_program_openid.sql:9-46` |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql (MISSING 鈥?0 鍖归厤) | |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 闈欐€侀獙璇?|
| **Final Result** | **MISSING** | **I038**锛圖DL 鍐欎簡浣?DB 鏈窇锛涚 4 娆″己璋冧笉璺戯紝Post-Audit 缁熶竴璺戞壒锛?|
| Evidence | `audit/evidence/F082-overseas-booking-ddl/` | 寰呭缓 |
| Issue | **I038** | |

> 娉細DDL 琛?9-44锛坄CREATE TABLE IF NOT EXISTS overseas_booking` 鍚?26 鍒楋級/ 琛?46锛坄ALTER ADD COLUMN IF NOT EXISTS mini_program_open_id`锛?

---

### F083 鈥?澶氳瑷€纭 / BookingConfirmationService 鏍稿績锛坰endConfirmation锛?

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F083 | `feature-inventory.md` 搂Batch 1.4 row 3 |
| 鍓嶇璺敱 | `/intl-booking` | 锛堥棿鎺ラ€氳繃 F096 updateStatus 瑙﹀彂锛?|
| 椤甸潰 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI鏉垮潡 | 鈥?| 锛堟棤鐩存帴 UI锛涚敱 F096 CONFIRMED 鐘舵€佸彉鏇磋Е鍙戯級 |
| Action | 鐘舵€佸彉鏇村埌 CONFIRMED 鎴栨墜鍔ㄨ皟 `/send-confirmation` | |
| Handler | 鈥?| |
| API | `intlBookingApi.updateStatus(id, {status:'CONFIRMED', autoSend:true})` 鎴?`intlBookingApi.sendConfirmation(id)`锛?*鍚庤€呮湭灏佽** 鈥?F112锛?| `frontend/meiyetec-web/src/api/intl-booking.ts:28` |
| Method | POST | |
| Controller | `IntlBookingService.sendConfirmation(b)` + `updateStatus(...)` autoSend 鍒嗘敮 | `service/IntlBookingService.java:241-247`锛坰endConfirmation锛? `:202-235`锛坲pdateStatus autoSend锛?|
| Service | `BookingConfirmationService.sendConfirmation(booking)` | `booking/BookingConfirmationService.java:69-115` |
| Mapper | `OverseasBookingMapper`锛坴ia IntlBookingService锛?| |
| DB琛?| `overseas_booking`锛?*缂哄け** 鈥?I038锛?| |
| 鍏宠仈琛?| `pos_store`锛坰toreName/storePhone 缁?F087 缂撳瓨鏌ヨ锛?| |
| 璇?鍐?| RW | |
| 浜嬪姟 | 鉁?`@Transactional`锛圛ntlBookingService.updateStatus:202, sendConfirmation 鏃狅級 | |
| 鏉冮檺 | `TenantContextHolder.get().getTenantId()`锛堝己鍒?tenantId 闅旂锛?| `IntlBookingService.java:25, 38, 96, 116, 132, 152, 167, 181, 219, 243, 255, 269, 313, 342` |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | `overseas_booking.created_at / updated_at`锛堣嚜鍔級 | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | 鉂?**MISSING** | 琛ㄤ笉瀛樺湪锛圛038锛?|
| Business Status | 瀹屾暣锛氣憼 buildVars 鏋勯€犲崰浣嶇锛? 涓?`${var}`锛夆憽 resolver.resolve 娓叉煋 4 娈碉紙subject/sms/miniProgram/email锛夆憿 鎸?receiver 璺敱锛圫MS/EMAIL/MINI_PROGRAM锛夆懀 鑱氬悎 overallSuccess | `BookingConfirmationService.java:69-115` |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql (MISSING) | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **MISSING** | I038锛堜緷璧?`overseas_booking` 琛ㄥ瓨鍦級锛涗笌 SoT 搂0.3 瀹屽叏涓€鑷?|
| Evidence | `audit/evidence/F083-booking-confirmation-service/` | 寰呭缓 |
| Issue | **I038** + I047锛坈atch 鍚炲紓甯?I008 鍚屾ā寮忥級 | |

> 娉細`sendConfirmation` 娴佺▼ L70-115锛歯ull booking 鍏滃簳 / 鏋勯€?vars / 娓叉煋妯℃澘 / 鎸?customerPhone 鈫?SMS / customerEmail 鈫?EMAIL / miniProgramOpenId 鈫?MINI_PROGRAM / 浠讳竴 success 鈫?overallSuccess=true

---

### F084 鈥?澶氳瑷€纭 / BookingConfirmationTemplateResolver锛? locale + warmup + fallback锛?

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F084 | `feature-inventory.md` 搂Batch 1.4 row 4 |
| 鍓嶇璺敱 | 鈥?| 锛堢敱 F083 闂存帴璋冪敤锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鍚姩鏈?warmup + 杩愯鏃舵寜 locale 娓叉煋 | `BookingConfirmationTemplateResolver.warmup()` `@PostConstruct` |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | `BookingConfirmationTemplateResolver.resolve(locale, vars)` + `warmup()` + `resolveWithFallback()` + `normalize()` | `booking/BookingConfirmationTemplateResolver.java:64-111` / `:36-44` / `:130-138` / `:141-156` |
| Mapper | 鈥?| |
| DB琛?| 鈥?| 锛堜粎 classpath `i18n/template/*.json`锛?|
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| R | |
| 浜嬪姟 | 鈥?| |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | N/A | |
| Business Status | 瀹屾暣锛? locale `zh-CN / zh-TW / en-US / ja-JP / ko-KR / fr-FR`锛?*涓?SoT 搂1 瀹屽叏涓€鑷?*锛夛紱warmup 鍚姩鏈熼鐑紱locale 鍥為€€閾?`xx-YY 鈫?xx 鈫?en-US`锛沗render()` 澶嶇敤 `SmsSender.render(template, vars)` 鍗犱綅绗︽浛鎹?| |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **UNVERIFIED** | 闈欐€佸彲杈撅紝缂鸿繍琛岃瘉鎹紱SoT 搂1 + 搂4 瀹屽叏涓€鑷?|
| Evidence | `audit/evidence/F084-template-resolver/` | 寰呭缓 |
| Issue | 鈥?| |

> 娉細locale 褰掍竴鍖?L141-156锛坱rim + lang 灏忓啓 / country 澶у啓锛夛紱鍥為€€閾?L130-138锛涚┖/null locale 鈫?DEFAULT_LOCALE="en-US" L25

---

### F085 鈥?澶氳瑷€纭 / BookingConfirmationTemplate DTO

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F085 | `feature-inventory.md` 搂Batch 1.4 row 5 |
| 鍓嶇璺敱 | 鈥?| 锛堢敱 F083 + F084 鏋勯€狅級 |
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鈥?| |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | `BookingConfirmationTemplate` DTO | `booking/BookingConfirmationTemplate.java:14-32` |
| Mapper | 鈥?| |
| DB琛?| 鈥?| |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| 鈥?| |
| 浜嬪姟 | 鈥?| |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | N/A | |
| Business Status | 瀹屾暣锛? 瀛楁锛歚resolvedLocale/usedFallback/subject/sms/miniProgram(keyword1..N)/miniProgramTitle/emailSubject/emailBody`锛?| |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **UNVERIFIED** | |
| Evidence | `audit/evidence/F085-template-dto/` | 寰呭缓 |
| Issue | 鈥?| |

---

### F086 鈥?澶氳瑷€纭 / BookingConfirmationResult DTO

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F086 | `feature-inventory.md` 搂Batch 1.4 row 6 |
| 鍓嶇璺敱 | 鈥?| 锛堥棿鎺ラ€氳繃 F095 + F096 鍝嶅簲锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鈥?| |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | `BookingConfirmationResult` DTO + `putChannel()` 鑷姩绱姞 overallSuccess | `booking/BookingConfirmationResult.java:11-32` |
| Mapper | 鈥?| |
| DB琛?| 鈥?| |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| 鈥?| |
| 浜嬪姟 | 鈥?| |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | N/A | |
| Business Status | 瀹屾暣锛坄overallSuccess` + `resolvedLocale/usedFallback/bookingId` + `channelResults: Map<channel, {success, externalMsgId, errorMsg}>`锛?| |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **UNVERIFIED** | |
| Evidence | `audit/evidence/F086-result-dto/` | 寰呭缓 |
| Issue | 鈥?| |

---


### F087 鈥?澶氳瑷€纭 / StoreLookupService锛坧os_store 鏌ヨ + 5 鍒嗛挓缂撳瓨锛?

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F087 | `feature-inventory.md` 搂Batch 1.4 row 7 |
| 鍓嶇璺敱 | 鈥?| 锛堢敱 F083 闂存帴璋冪敤锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鎸?`storeId` 鏌ヨ闂ㄥ簵鍚?+ 鐢佃瘽锛堝甫缂撳瓨锛?| |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | `StoreLookupService.lookup(storeId)` / `lookupStoreName(storeId)` / `lookupStorePhone(storeId)` / `invalidate(storeId)` | `booking/StoreLookupService.java:54-76` / `:43-45` / `:78-79` |
| Mapper | JdbcTemplate 鐩存煡 | `StoreLookupService.java:60-71` |
| DB琛?| `pos_store`锛?*瀛樺湪** 鈥?db-snapshot.sql grep 5 鍖归厤锛?| db-snapshot.sql锛? 鍖归厤锛?|
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| R | |
| 浜嬪姟 | 鉂?| |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| `deleted=0` 杩囨护 | `StoreLookupService.java:62` |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | 鉁?PARTIAL | 琛ㄥ瓨鍦紱**寰?Phase 5 楠岃瘉 `store_name` + `contact_phone` 鍒楀榻?*锛圛046锛?|
| Business Status | 瀹屾暣锛歚CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)`锛?*涓?SoT 搂0.4 瀹屽叏涓€鑷?*锛夛紱`ConcurrentHashMap<Long, CacheEntry>`锛汼QL `SELECT store_name, contact_phone FROM pos_store WHERE id=? AND deleted=0`锛?*涓?SoT 搂0.4 瀛楁鏄犲皠瀹屽叏涓€鑷?*锛?| |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql (5 鍖归厤) | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **UNVERIFIED** | I046锛坄store_name/contact_phone` 鍒楀榻愬緟 Phase 5 楠岃瘉锛?|
| Evidence | `audit/evidence/F087-store-lookup/` | 寰呭缓 |
| Issue | I046 | |

> 娉細缂撳瓨鏉＄洰 L82-86锛涘懡涓垽鏂?L57锛坄now - cachedAt < CACHE_TTL_MS`锛夛紱null storeId 鍏滃簳 L56

---

### F088 鈥?娓犻亾 / SMS / SmsSender 闃块噷浜戠煭淇?

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F088 | `feature-inventory.md` 搂Batch 1.4 row 8 |
| 鍓嶇璺敱 | 鈥?| 锛堥棿鎺ラ€氳繃 F083 娓犻亾璺敱锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鎸?`customerPhone` 鑷姩璺敱鍙戦€?| `BookingConfirmationService.java:82-87` |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | `SmsSender.send(tpl, receiver, vars)` + `resolveConfig(tenantId)` + `render(template, vars)` static + `normalizePhone` + `maskPhone` | `service/channel/SmsSender.java:46-90` / `:106-124` |
| Mapper | `mkt_channel_config`锛堟寜 tenantId + channelType=SMS 鏌?config锛?| |
| DB琛?| `mkt_channel_config`锛?*瀛樺湪** 鈥?db-snapshot.sql grep 鍖归厤锛?| db-snapshot.sql |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| RW锛堣 config + 璋冮樋閲屼簯 + 鍐?touch log锛?| |
| 浜嬪姟 | 鉂?| |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | **UNVERIFIED**锛圖10 閾佸緥锛氶樋閲屼簯 accessKey 鍑嵁缂哄け锛?| Mock 闄嶇骇 `demoMode()` 杩斿洖 `SMS-DEMO-{ts}` |
| DB Status | 鉁咃紙mkt_channel_config 瀛樺湪锛?| |
| Business Status | 瀹屾暣锛歚channelType() = "SMS"`锛涜蛋 `HttpUtil.sendAliYunSms`锛沗render()` static 鍗犱綅绗︽浛鎹紱`normalizePhone` 鍘?+86 鍓嶇紑锛沗maskPhone(135****0001)` | |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **UNVERIFIED** | D10 閾佸緥锛氶樋閲屼簯 accessKey 鍑嵁缂哄け锛汳ock 闄嶇骇閾捐矾鍙皟锛涚湡瀹為摼璺?Phase 5 UNVERIFIED |
| Evidence | `audit/evidence/F088-sms-sender/` | 寰呭缓 |
| Issue | **I049**锛圖10 鍑嵁缂哄け 鈥?UNVERIFIED锛?| |

> 娉細`channelType()` 杩?`"SMS"`锛沗render(template, vars)` 闈欐€佸崰浣嶇 `${key}` 鈫?`vars[key]`

---

### F089 鈥?娓犻亾 / EMAIL / EmailSender锛圝avaMailSender + mismatch demo 涓夋ā寮忥級

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F089 | `feature-inventory.md` 搂Batch 1.4 row 9 |
| 鍓嶇璺敱 | 鈥?| 锛堥棿鎺ラ€氳繃 F083 娓犻亾璺敱锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鎸?`customerEmail` 鑷姩璺敱鍙戦€?| `BookingConfirmationService.java:88-93` |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | `EmailSender.send(tpl, receiver, vars)` + `doSendSmtp()` + `demoMode()` + `resolveConfig()` | `service/channel/EmailSender.java:60-114` / `:121-167` / `:178-186` / `:188-203` |
| Mapper | `mkt_channel_config`锛堟寜 tenantId + channelType=EMAIL 鏌?config锛?| |
| DB琛?| `mkt_channel_config`锛堝瓨鍦級 | db-snapshot.sql |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| RW | |
| 浜嬪姟 | 鉂?| |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | **UNVERIFIED**锛圖10 閾佸緥锛歋MTP 鍑嵁缂哄け锛?| mismatch demo 闄嶇骇 |
| DB Status | 鉁?| |
| Business Status | 瀹屾暣锛?*涓夋ā寮?*锛氣憼 绉熸埛 cfg + JavaMailSender 宸叉敞鍏?鈫?鐪熷疄 SMTP锛圚TML / 绾枃鏈嚜閫傚簲锛夛紱鈶?JavaMailSender 宸叉敞鍏ヤ絾鏃犵鎴?cfg 鈫?鍏ㄥ眬 `spring.mail.*`锛涒憿 JavaMailSender 鏈敞鍏ユ垨 mismatch 鈫?demo 妯″紡锛墊 **鏃ュ織鍏抽敭璇?`[EMAIL] tenant SMTP host/user mismatch with global config; demo mode`**锛圫oT 搂0.2 瀹屽叏涓€鑷达級 |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **UNVERIFIED** | I049 + SoT 搂0.2 mismatch demo 瀹屽叏涓€鑷?|
| Evidence | `audit/evidence/F089-email-sender/` | 寰呭缓 |
| Issue | **I049** | |

> 娉細mismatch 妫€娴?`globalHost.equals(smtpHost) 鈭?globalUsername.equals(username)`锛堜笉涓€鑷村嵆 demo锛?

---

### F090 鈥?娓犻亾 / MINI_PROGRAM / MiniProgramSender 寰俊璁㈤槄娑堟伅

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F090 | `feature-inventory.md` 搂Batch 1.4 row 10 |
| 鍓嶇璺敱 | 鈥?| 锛堥棿鎺ラ€氳繃 F083 娓犻亾璺敱锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鎸?`miniProgramOpenId` 鑷姩璺敱鍙戦€?| `BookingConfirmationService.java:94-100` |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | `MiniProgramSender.send(tpl, receiver, vars)` + `getAccessToken(tenantId, appId, appSecret)` + `buildSubscribeData()` | `service/channel/MiniProgramSender.java:48-100` / `:107-128` / `:135-163` |
| Mapper | `mkt_channel_config` | |
| DB琛?| `mkt_channel_config`锛堝瓨鍦級 | db-snapshot.sql |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| RW | |
| 浜嬪姟 | 鉂?| |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | **UNVERIFIED**锛圖10 閾佸緥锛歛ppId/appSecret 鍑嵁缂哄け锛?| Mock 闄嶇骇 |
| DB Status | 鉁?| |
| Business Status | 瀹屾暣锛歚channelType() = "MINI_PROGRAM"`锛?*AccessToken 鍐呭瓨缂撳瓨**锛歚ConcurrentHashMap<Long, TokenCache>`锛沗TOKEN_CACHE_MS = 7100_000L`锛堟彁鍓?10 鍒嗛挓鍒锋柊 2 灏忔椂 token锛夛紱errcode=40001 鑷姩娓呯紦瀛橈紱`buildSubscribeData` 鎸?`variables` 鏁扮粍鐢熸垚 `keyword1..keyword5`锛堟渶澶?5锛夛紱`MP_TEMPLATE_ID = "BOOKING_CONFIRMATION"` 鏄崰浣嶏紙SoT 搂0.1锛?| |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **UNVERIFIED** | I049 + SoT 搂0.1 妯℃澘鍗犱綅绗﹀畬鍏ㄤ竴鑷?|
| Evidence | `audit/evidence/F090-miniprogram-sender/` | 寰呭缓 |
| Issue | **I049** | |

> 娉細`getAccessToken` L107-128锛沗buildSubscribeData` L135-163锛沗TOKEN_CACHE_MS = 7100_000L`

---

### F091 鈥?娓犻亾 / IN_APP / InAppSender 绔欏唴淇★紙database 鎸佷箙鍖栵級

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F091 | `feature-inventory.md` 搂Batch 1.4 row 11 |
| 鍓嶇璺敱 | 鈥?| 锛堥棿鎺ラ€氳繃 M09 钀ラ攢閾捐矾 鈥?F083 鏈矾鐢?IN_APP锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鍐欑珯鍐呬俊鍒?member_message 琛?| |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | `InAppSender.send(tpl, receiver, vars)` + `detectMessageType` + `inferTitle` + `parseMemberId` + `extractMemberName` | `service/channel/InAppSender.java:42-87` |
| Mapper | `MemberMessageMapper.insert` | |
| DB琛?| `member_message`锛?*缂哄け** 鈥?I045锛?| **db-snapshot.sql grep 0 鍖归厤宸茬‘璁?* |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| RW | |
| 浜嬪姟 | 鉁?`@Transactional(rollbackFor = Exception.class)` | `InAppSender.java:43` |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | `created_at`锛堟墜鍔ㄨ缃?L76锛?| |
| Static Status | L0 | |
| Runtime Status | **MISSING** | 琛ㄤ笉瀛樺湪锛泃ry/catch 鍖呰９鍐欐棩蹇椾絾涓氬姟澶辫触 |
| DB Status | 鉂?**MISSING** | db-snapshot.sql 0 鍖归厤锛圛045锛?|
| Business Status | 瀹屾暣锛歚channelType() = "IN_APP"`锛涘啓 `member_message(tenantId/memberId/title/content/channelType=IN_APP/messageType/priority=2/...)`锛沗receiver` 瑙ｆ瀽 `memberId[-name]`锛沗messageType` 鎺ㄦ柇锛圔IRTHDAY/EXPIRE/PROMO/TASK/SYSTEM锛墊 **涓?SoT 搂4 涓€鑷?*锛歋oT 浠呮槑纭?3 娓犻亾璺敱锛圫MS/EMAIL/MINI_PROGRAM锛夛紝IN_APP/WECHAT_OA 鐢?ChannelDispatcher 鍏朵粬涓氬姟璋冪敤 |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql (MISSING 鈥?0 鍖归厤) | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **MISSING** | **I045**锛坄member_message` 琛ㄦ湭璺戣縼绉伙紱绗?4 娆″己璋冧笉璺戯紝Post-Audit 缁熶竴璺戞壒锛?|
| Evidence | `audit/evidence/F091-in-app-sender/` | 寰呭缓 |
| Issue | **I045** | |

> 娉細`healthCheck` L141-150锛堥獙璇佽〃瀛樺湪 + `SELECT 1 FROM member_message LIMIT 1`锛?

---

### F092 鈥?娓犻亾 / WECHAT_OFFICIAL_ACCOUNT / WechatOfficialAccountSender 鍏紬鍙锋ā鏉挎秷鎭?

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F092 | `feature-inventory.md` 搂Batch 1.4 row 12 |
| 鍓嶇璺敱 | 鈥?| 锛堥棿鎺ラ€氳繃 M09 钀ラ攢閾捐矾 鈥?F083 鏈矾鐢?OA锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鎺ㄥ叕浼楀彿妯℃澘娑堟伅 | |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | `WechatOfficialAccountSender.send(tpl, receiver, vars)` + `getAccessToken()` + `buildTemplateData()` | `service/channel/WechatOfficialAccountSender.java:53-113` / `:120-141` / `:148-184` |
| Mapper | `mkt_channel_config` | |
| DB琛?| `mkt_channel_config`锛堝瓨鍦級 | db-snapshot.sql |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| RW | |
| 浜嬪姟 | 鉂?| |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | **UNVERIFIED**锛圖10 閾佸緥锛歛ppId/appSecret 鍑嵁缂哄け锛?| Mock 闄嶇骇 |
| DB Status | 鉁?| |
| Business Status | 瀹屾暣锛歚channelType() = "WECHAT_OFFICIAL_ACCOUNT"`锛?*娉ㄦ剰锛氶潪 `WECHAT_OA`锛屼笌 SoT 搂1 鎻忚堪"WECHAT_OA"涓嶄竴鑷?* 鈥?瑙?**I039**锛夛紱AccessToken 缂撳瓨涓?F090 鍚屾ā寮忥紙`TOKEN_CACHE_MS = 7100_000L`锛夛紱妯℃澘娑堟伅鐢?`first/keyword1..N` 鏍煎紡锛堟渶澶?10 涓?keyword锛夛紱errcode 40001/40003/43003/43004/43005 涓氬姟澶辫触涓嶉噸璇?| |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **CONFLICT** | **I039**锛圫oT vs 浠ｇ爜鍒悕涓嶄竴鑷达細`WECHAT_OA` vs `WECHAT_OFFICIAL_ACCOUNT`锛?|
| Evidence | `audit/evidence/F092-wechat-oa-sender/` | 寰呭缓 |
| Issue | **I039** + I049 | |

> 娉細娓犻亾鍒悕宸紓涓嶅奖鍝嶅姛鑳斤紱Fix Phase = Post-Audit 浜虹被鍐崇瓥锛? 閫夐」锛氭敼 SoT 鏂囨。缁熶竴鍒悕 / 鏀逛唬鐮?channelType 鍚嶇О / 缁存寔鐜扮姸鎺ュ彈鍒悕宸紓锛?

---

### F093 鈥?娓犻亾 / 鎺ュ彛濂戠害 / ChannelSender

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F093 | `feature-inventory.md` 搂Batch 1.4 row 13 |
| 鍓嶇璺敱 | 鈥?| 锛堟帴鍙ｅ绾︼級 |
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鈥?| |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | `ChannelSender` interface锛? 鏂规硶锛? `SendResult` 鍐呭祵绫?| `service/channel/ChannelSender.java:9-37` |
| Mapper | 鈥?| |
| DB琛?| 鈥?| |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| 鈥?| |
| 浜嬪姟 | 鈥?| |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | N/A | |
| Business Status | 瀹屾暣锛歚channelType() / send(MktChannelTemplate, String, Map) / healthCheck()`锛沗SendResult` 鍚?`{success, externalMsgId, errorMsg}` + `static ok(externalMsgId) / static fail(msg)` 宸ュ巶 | |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **UNVERIFIED** | |
| Evidence | `audit/evidence/F093-channel-sender-interface/` | 寰呭缓 |
| Issue | 鈥?| |

---

### F094 鈥?娓犻亾 / 鍒嗗彂 / ChannelDispatcher锛堣矾鐢?+ healthCheckAll + rateLimitCheck锛?

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F094 | `feature-inventory.md` 搂Batch 1.4 row 14 |
| 鍓嶇璺敱 | 鈥?| 锛圡09 浣撶郴璋冪敤锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 鎸?channelType 璺敱鍒板搴?sender + 鍋ュ悍妫€鏌?+ 闄愭祦 | |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | 鈥?| |
| Controller | 鈥?| |
| Service | `ChannelDispatcher.getSender(channelType)` + `healthCheckAll()` + `rateLimitCheck()` | `service/channel/ChannelDispatcher.java:34-41` / `:53-73` / `:78-90` |
| Mapper | `MktChannelConfigMapper.selectOne` + JdbcTemplate 鐩存煡 `marketing_touch_log` | |
| DB琛?| `mkt_channel_config`锛堝啓 `last_health_at/msg`锛? `marketing_touch_log`锛堣锛?| db-snapshot.sql |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| RW | |
| 浜嬪姟 | 鉂?| |
| 鏉冮檺 | 鈥?| |
| 杞垹闄?| `deleted=0` 杩囨护锛坄mkt_channel_config.deleted=0`锛?| `ChannelDispatcher.java:62` |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | 鉁?| |
| Business Status | 瀹屾暣锛歚getSender` 鎸?`channelType` filter锛沗healthCheckAll` 杞鎵€鏈?sender + UPDATE `mkt_channel_config.last_health_at`锛?*浠呮洿鏂?tenant_id=1** 鈥?T07-D4 渚嬪澹版槑 `log.warn`锛夛紱`rateLimitCheck` 鏌?`marketing_touch_log` 鎸夊垎閽?鏃ラ檺棰?| |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I050锛坔ealthCheckAll T07-D4 浠呮洿鏂?tenant_id=1 鈥?鏈夋剰淇濈暀璇箟锛屼絾缂哄皯 Phase 5 鐪熷疄澶氱鎴峰満鏅獙璇侊級 |
| Evidence | `audit/evidence/F094-channel-dispatcher/` | 寰呭缓 |
| Issue | I050 | |

> 娉細T07-D4 渚嬪澹版槑 L51 `log.warn("[T07-D4] healthCheckAll 浠呮洿鏂伴粯璁ょ鎴?tenant_id=1) 鐨勬笭閬撻厤缃紱鍏朵粬绉熸埛鐨勬笭閬撳仴搴风姸鎬佷笉浼氳鏇存柊锛堟寜璁捐淇濈暀锛?)`

---


### F095 鈥?娴峰棰勭害 / 绔偣 / `POST /intl-booking/{id}/send-confirmation`锛堟墜鍔ㄨЕ鍙戯級

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F095 | `feature-inventory.md` 搂Batch 1.4 row 15 |
| 鍓嶇璺敱 | `/intl-booking` | 锛?*Shadow** 鈥?鍓嶇鏃犳 UI 鍏ュ彛锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | 搴楅暱鎵嬪姩瑙﹀彂纭鍙戦€侊紙鐙珛浜庣姸鎬佸彉鏇达級 | |
| Handler | 鈥?| **鍓嶇 `intlBookingApi` 鏃?`sendConfirmation` 鏂规硶** 鈥?瑙?F112 |
| API | 鈥?| **鍓嶇 `intl-booking.ts:1-31` 鏃?`sendConfirmation` 鏂规硶灏佽**锛?*I042 Shadow**锛?|
| Method | POST | |
| Controller | `IntlBookingService.sendConfirmation(@PathVariable Long id)` | `service/IntlBookingService.java:241-247` |
| Service | `BookingConfirmationService.sendConfirmation(b)` | `booking/BookingConfirmationService.java:69-115` |
| Mapper | `OverseasBookingMapper.selectById` | |
| DB琛?| `overseas_booking`锛?*缂哄け** 鈥?I038锛?| |
| 鍏宠仈琛?| `pos_store`锛坰toreName/storePhone 缁?F087锛?| |
| 璇?鍐?| R锛堣 booking锛?| |
| 浜嬪姟 | 鉂?| |
| 鏉冮檺 | 鏅€氱鎴凤紙`TenantContextHolder.get().getTenantId()`锛?| `IntlBookingService.java:243` |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | **UNVERIFIED** | |
| DB Status | 鉂?**MISSING** | 琛ㄤ笉瀛樺湪锛圛038锛?|
| Business Status | 瀹屾暣锛堢嫭绔嬩簬鐘舵€佸彉鏇达紱搴楅暱鍙浠讳綍鐘舵€佺殑棰勭害閲嶅彂纭锛?| SoT 搂3 鏄庣‘ |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql (MISSING) | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **MISSING** | I038锛堣〃缂哄け锛? **Shadow I042**锛堝墠绔棤 sendConfirmation 鏂规硶锛?|
| Evidence | `audit/evidence/F095-send-confirmation/` | 寰呭缓 |
| Issue | **I038** + **I042** | |

> 娉細SoT 搂3 鏄庣‘銆屽簵闀垮彲瀵逛换浣曠姸鎬佺殑棰勭害閲嶅彂纭銆嶏紱鍚庣绔偣瀛樺湪浣嗗墠绔棤璋冪敤鍏ュ彛

---

### F096 鈥?娴峰棰勭害 / 绔偣 / `POST /intl-booking/{id}/status`锛坅utoSend CONFIRMED 鑷姩瑙﹀彂锛?

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F096 | `feature-inventory.md` 搂Batch 1.4 row 16 |
| 鍓嶇璺敱 | `/intl-booking` | `frontend/meiyetec-web/src/views/intl-booking/index.vue:66` |
| 椤甸潰 | `views/intl-booking/index.vue` | |
| UI鏉垮潡 | 銆屽浗闄呴绾︺€峵ab 鎿嶄綔鍒椼€岀‘璁ゃ€嶆寜閽?| `index.vue:66`锛坄setStatus(row, 'CONFIRMED')`锛墊
| Action | 鐐瑰嚮銆岀‘璁ゃ€嶁啋 `updateStatus(id, {status:'CONFIRMED'})` | `index.vue:66` 鈫?`setStatus` |
| Handler | `setStatus(row, 'CONFIRMED')` | `index.vue:112-115` |
| API | `intlBookingApi.updateStatus(row.id, { status })` | `frontend/meiyetec-web/src/api/intl-booking.ts:28` |
| Method | POST | |
| Controller | `IntlBookingService.updateStatus(id, body)` | `service/IntlBookingService.java:202-235` |
| Service | `BookingConfirmationService.sendConfirmation(b)` | `booking/BookingConfirmationService.java:69-115` |
| Mapper | `OverseasBookingMapper.selectById / updateById` | |
| DB琛?| `overseas_booking`锛?*缂哄け** 鈥?I038锛?| |
| 鍏宠仈琛?| `pos_store`锛坰toreName/storePhone 缁?F087锛?| |
| 璇?鍐?| RW | |
| 浜嬪姟 | 鉁?`@Transactional` | `IntlBookingService.java:202` |
| 鏉冮檺 | 鏅€氱鎴?| `IntlBookingService.java:203` |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | `updated_at`锛堣嚜鍔級 | |
| Static Status | L0 | |
| Runtime Status | **UNVERIFIED** | |
| DB Status | 鉂?**MISSING** | 琛ㄤ笉瀛樺湪锛圛038锛?|
| Business Status | 瀹屾暣锛?*autoSend 榛樿 true**锛歚body.get("autoSend") == null ? TRUE : Boolean.valueOf(body.get("autoSend").toString())`锛涢潪 CONFIRMED 鈫?CONFIRMED 鏃惰嚜鍔ㄨ皟鐢?`confirmationService.sendConfirmation(b)`锛涘紓甯稿湪 try/catch 鍐?+ 鍐?`confirmationError` 瀛楁锛?*涓嶅洖婊氱姸鎬佸彉鏇?* 鈥?I008/I047 鍚屾ā寮忥級锛涘搷搴?`data: { booking, confirmation?, confirmationError? }`锛墊 SoT 搂0.3 瀹屽叏涓€鑷?|
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql (MISSING) | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **MISSING** | I038锛堣〃缂哄け锛? **Shadow I040**锛坅utoSend=false UI 缂哄け锛? **Shadow I041**锛堢‘璁ゆ寜閽棤"纭鍗冲彂閫侀€氱煡"鏂囨锛? I048锛堟棤 rollbackFor锛墊
| Evidence | `audit/evidence/F096-update-status/` | 寰呭缓 |
| Issue | **I038** + **I040** + **I041** + **I048** | |

> 娉細autoSend 榛樿 true L217-219锛泃ry/catch autoSend 鍒嗘敮 L220-233锛汭048锛歚@Transactional` 鏃?`rollbackFor` 鈥?榛樿浠?RuntimeException 鍥炴粴

---

### F097 鈥?娴峰棰勭害 / 绔偣 / `GET /intl-booking/{id}/confirmation-template`锛堟ā鏉块瑙堬級

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F097 | `feature-inventory.md` 搂Batch 1.4 row 17 |
| 鍓嶇璺敱 | `/intl-booking` | `frontend/meiyetec-web/src/views/intl-booking/index.vue:91` |
| 椤甸潰 | `views/intl-booking/index.vue` | |
| UI鏉垮潡 | 銆屽浗闄呴绾︺€峵ab 鎿嶄綔鍒椼€岀‘璁ゆā鏉裤€嶆寜閽?| `index.vue:68` |
| Action | 鐐瑰嚮銆岀‘璁ゆā鏉裤€嶁啋 `viewTemplate(row.id)` 鈫?`intlBookingApi.confirmationTemplate(id)` | `index.vue:91-94` |
| Handler | `viewTemplate(id)` | `index.vue:119-123` |
| API | `intlBookingApi.confirmationTemplate(id)` | `frontend/meiyetec-web/src/api/intl-booking.ts:30` |
| Method | GET | |
| Controller | `IntlBookingService.confirmationTemplate(id)` | `service/IntlBookingService.java:439-489` |
| Service | **纭紪鐮?switcher**锛?*鏈蛋 BookingConfirmationTemplateResolver**锛?| `IntlBookingService.java:441-486` |
| Mapper | `OverseasBookingMapper.selectById` | |
| DB琛?| `overseas_booking`锛?*缂哄け** 鈥?I038锛?| |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| R | |
| 浜嬪姟 | 鉂?| |
| 鏉冮檺 | 鏅€氱鎴?| `IntlBookingService.java:440` |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | **UNVERIFIED** | |
| DB Status | 鉂?**MISSING** | 琛ㄤ笉瀛樺湪锛圛038锛?|
| Business Status | 鈿?**CONFLICT**锛歋oT 搂1 鏈熸湜璧?`BookingConfirmationTemplateResolver` + 6 locale JSON 妯℃澘浣撶郴锛涗唬鐮佸疄闄呮槸**鎺у埗鍣ㄥ唴纭紪鐮?switcher**锛坄zh/zh-cn/zh-tw/ja/ko/default` 6 鍒嗘敮锛夛紝杩斿洖浠?`subject + body`锛?*涓?BookingConfirmationTemplate DTO 鍙嶅悜** 鈥?缂?sms/miniProgram/email 4 娈碉級锛涘綋鍓?preview 涓庣敓浜у彂閫侀摼璺?*涓嶄竴鑷?* | **I043** |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql (MISSING) | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **CONFLICT** | **I043**锛堜腑绔嬫爣锛欶ix Phase = Post-Audit 浜虹被鍐崇瓥锛? 閫夐」锛氭敼鐢?Resolver / 淇濈暀纭紪鐮?+ 鏍囨敞宸紓 / 鎺ュ彈鐜扮姸锛?|
| Evidence | `audit/evidence/F097-confirmation-template/` | 寰呭缓 |
| Issue | **I043** | |

> 娉細纭紪鐮?switcher L443-486锛涗笌 F083/F084 鐨?BookingConfirmationTemplateResolver 閾捐矾鍒嗙

---

### F098 鈥?娴峰棰勭害 / 绔偣 / `GET /intl-booking/list`锛堥绾﹀垪琛ㄦ煡璇級

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F098 | `feature-inventory.md` 搂Batch 1.4 row 18 |
| 鍓嶇璺敱 | `/intl-booking` | `frontend/meiyetec-web/src/views/intl-booking/index.vue` |
| 椤甸潰 | `views/intl-booking/index.vue` | |
| UI鏉垮潡 | 銆屽浗闄呴绾︺€峵ab el-table | `index.vue:30-47` |
| Action | 椤甸潰鍔犺浇 鈫?`load()` 鈫?`intlBookingApi.listBookings()` | `index.vue:97-100` |
| Handler | `load()` | `index.vue:97-100` |
| API | `intlBookingApi.listBookings(params?)` | `frontend/meiyetec-web/src/api/intl-booking.ts:24-26` |
| Method | GET | |
| Controller | `IntlBookingService.listBookings(status?, source?)` | `service/IntlBookingService.java:184-194` |
| Service | 鈥?| |
| Mapper | `OverseasBookingMapper.selectList(LambdaQueryWrapper)` | |
| DB琛?| `overseas_booking`锛?*缂哄け** 鈥?I038锛?| |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| R | |
| 浜嬪姟 | 鉂?| |
| 鏉冮檺 | 鏅€氱鎴凤紙寮哄埗 `tenantId`锛?| `IntlBookingService.java:185-186` |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | **UNVERIFIED** | |
| DB Status | 鉂?**MISSING** | 琛ㄤ笉瀛樺湪锛圛038锛?|
| Business Status | 瀹屾暣锛坄LambdaQueryWrapper` 鎸?`tenantId` 杩囨护 + `id DESC`锛涘彲閫?`status` / `source` 杩囨护锛?| |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql (MISSING) | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **MISSING** | I038锛堣〃缂哄け锛?|
| Evidence | `audit/evidence/F098-list-bookings/` | 寰呭缓 |
| Issue | **I038** | |

---

### F099 鈥?娴峰棰勭害 / 绔偣 / `POST /intl-booking/webhook/google`锛圙oogle 鎺ㄩ€佹帴鏀讹級

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F099 | `feature-inventory.md` 搂Batch 1.4 row 19 |
| 鍓嶇璺敱 | 鈥?| 锛堟棤鍓嶇 UI锛汫oogle BP 鍚庡彴 push锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | Google BP 鈫?POST 璇ョ鐐?鈫?瑙ｆ瀽 payload 鈫?鎻?overseas_booking | |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | POST | |
| Controller | `IntlBookingService.googleWebhook(payload, token)` | `service/IntlBookingService.java:255-289` |
| Service | 鈥?| |
| Mapper | `OverseasBookingMapper.insert` | |
| DB琛?| `overseas_booking`锛?*缂哄け** 鈥?I038锛?| |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| W | |
| 浜嬪姟 | 鉁?`@Transactional` | `IntlBookingService.java:255` |
| 鏉冮檺 | 鉂岋紙webhook 鍏紑绔偣锛屾棤绉熸埛闅旂锛?| `IntlBookingService.java:256-257` |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | **UNVERIFIED** | |
| DB Status | 鉂?**MISSING** | 琛ㄤ笉瀛樺湪锛圛038锛?|
| Business Status | 瀹屾暣锛堣В鏋?`notificationId/customerName/customerPhone/customerEmail/locale/serviceName/bookingTime` 鈫?鎻?overseas_booking锛沗token 鈫?tenantId` 鍙嶆煡褰撳墠婕旂ず杩斿洖 null 鍏滃簳 1L锛?*婕旂ず妯″紡**锛墊 SoT 搂3 + **I051**锛坉emo 妯″紡锛?|
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql (MISSING) | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **MISSING** | I038锛堣〃缂哄け锛? I048锛堟棤 rollbackFor锛? I051锛坉emo 妯″紡鐪熷疄閾捐矾缂哄け锛?|
| Evidence | `audit/evidence/F099-google-webhook/` | 寰呭缓 |
| Issue | **I038** + **I048** + **I051** | |

> 娉細P3-26 浠诲姟 5锛?*婕旂ず妯″紡**锛欸oogle BP 鈫?webhook 鈫?涓嶇湡璁㈤槄锛泃oken 鈫?tenantId 婕旂ず妯″紡纭紪鐮?1L

---

### F100 鈥?娴峰棰勭害 / 绔偣 / `POST /intl-booking/webhook/instagram`锛圛G 鎺ㄩ€佹帴鏀讹級

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F100 | `feature-inventory.md` 搂Batch 1.4 row 20 |
| 鍓嶇璺敱 | 鈥?| 锛堟棤鍓嶇 UI锛汳eta 鍚庡彴 push锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | Instagram DM 鈫?POST 鈫?瑙ｆ瀽 entry[*].messaging[*] 鈫?鎻愬彇 text/sender_id 鈫?绠€鏄?NLP 鈫?鎻?overseas_booking | |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | POST | |
| Controller | `IntlBookingService.instagramWebhook(payload)` | `service/IntlBookingService.java:296-336` |
| Service | 鈥?| |
| Mapper | `OverseasBookingMapper.insert` | |
| DB琛?| `overseas_booking`锛?*缂哄け** 鈥?I038锛?| |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| W | |
| 浜嬪姟 | 鉁?`@Transactional` | `IntlBookingService.java:296` |
| 鏉冮檺 | 鉂岋紙webhook 鍏紑绔偣锛?| |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | **UNVERIFIED** | |
| DB Status | 鉂?**MISSING** | 琛ㄤ笉瀛樺湪锛圛038锛?|
| Business Status | 瀹屾暣锛堣В鏋?`entry[0].messaging[0].message.text/sender.id` + 绠€鏄?NLP `parseServiceFromText` 鍏抽敭璇嶅尮閰?facial/massage/manicure/nail/hair 鈫?鎻?overseas_booking锛泃enantId 婕旂ず妯″紡纭紪鐮?1L锛墊 SoT 搂3 + **I051** |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | db-snapshot.sql (MISSING) | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **MISSING** | I038 + I048 + I051 |
| Evidence | `audit/evidence/F100-instagram-webhook/` | 寰呭缓 |
| Issue | **I038** + **I048** + **I051** | |

---

### F101 鈥?娴峰棰勭害 / 绔偣 / `GET /intl-booking/webhook/instagram`锛圡eta verify_token 鎻℃墜锛?

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F101 | `feature-inventory.md` 搂Batch 1.4 row 21 |
| 鍓嶇璺敱 | 鈥?| 锛堟棤鍓嶇 UI锛汳eta webhook 楠岃瘉锛?|
| 椤甸潰 | 鈥?| |
| UI鏉垮潡 | 鈥?| |
| Action | Meta webhook 楠岃瘉鎻℃墜 鈫?杩斿洖 hub.challenge | |
| Handler | 鈥?| |
| API | 鈥?| |
| Method | GET | |
| Controller | `IntlBookingService.instagramWebhookVerify(mode, verifyToken, challenge)` | `service/IntlBookingService.java:342-353` |
| Service | 鈥?| |
| Mapper | 鈥?| |
| DB琛?| 鈥?| |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| R | |
| 浜嬪姟 | 鉂?| |
| 鏉冮檺 | 鉂?| |
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | **UNVERIFIED** | |
| DB Status | N/A | |
| Business Status | 瀹屾暣锛堣繑鍥?`hub.challenge` 瀹屾垚 Meta webhook 楠岃瘉鎻℃墜锛?*婕旂ず妯″紡**锛歮ode=subscribe + challenge 闈炵┖鍗宠繑鍥烇紱鐢熶骇闇€ `verifyToken` 姣斿锛圫oT 搂3 鏈槑纭紝鏈壒娆′笉瀹炵幇锛夛級| |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **UNVERIFIED** | I051锛坉emo 妯″紡锛?|
| Evidence | `audit/evidence/F101-instagram-verify/` | 寰呭缓 |
| Issue | 鈥?| |

---

### F102 鈥?娴峰棰勭害 / 绔偣 / OAuth2 connect + callback锛圙oogle + Instagram锛?

| 缁村害 | 鍊?| 璇佹嵁 |
|---|---|---|
| Feature ID | F102 | `feature-inventory.md` 搂Batch 1.4 row 22 |
| 鍓嶇璺敱 | `/intl-booking` | `frontend/meiyetec-web/src/views/intl-booking/index.vue:8-19` |
| 椤甸潰 | `views/intl-booking/index.vue` | |
| UI鏉垮潡 | 銆屾笭閬撹繛鎺ャ€峵ab 鍗＄墖缃戞牸 | `index.vue:8-19` |
| Action | 鐐瑰嚮銆岀粦瀹氥€嶁啋 `connectGoogle` / `connectInstagram` 鈫?`window.open(data.authUrl, '_blank')` 鈫?`doGoogle` / `doInstagram` | `index.vue:8-19` |
| Handler | `doGoogle()` / `doInstagram()` | `index.vue:102-111` / `:112-119` |
| API | `intlBookingApi.connectGoogle(body)` / `connectInstagram(body)` / `googleCallback(body)` / `instagramCallback(body)` | `frontend/meiyetec-web/src/api/intl-booking.ts:5-12` |
| Method | POST | |
| Controller | `IntlBookingService.connectGoogle(body)` + `googleCallback(body)` + `connectInstagram(body)` + `instagramCallback(body)` | `service/IntlBookingService.java:51-58` / `:78-88` / `:98-112` / `:120-129` |
| Service | 鈥?| |
| Mapper | 鈥?| |
| DB琛?| 鈥?| |
| 鍏宠仈琛?| 鈥?| |
| 璇?鍐?| 鈥?| |
| 浜嬪姟 | 鉁?`@Transactional`锛坈allback 鍐咃級 | `IntlBookingService.java:78`锛坓oogleCallback锛? `:120`锛坕nstagramCallback锛墊
| 鏉冮檺 | 鏅€氱鎴?| `IntlBookingService.java:52`锛坈onnectGoogle锛? `:99`锛坈onnectInstagram锛墊
| 杞垹闄?| 鉂?| |
| 瀹¤瀛楁 | 鈥?| |
| Static Status | L0 | |
| Runtime Status | **UNVERIFIED** | |
| DB Status | N/A | |
| Business Status | 瀹屾暣锛堣繑鍥炵湡瀹?OAuth URL锛歚accounts.google.com/o/oauth2/v2/auth` + `facebook.com/v18.0/dialog/oauth`锛沜allback 涓嶇湡鎹?token锛岃繑鍥?`ya29.DEMO-{code鍓?浣峿` / `IGQVJ-DEMO-{code鍓?浣峿`锛?*婕旂ず妯″紡**锛墊 SoT 搂3 + **I051** |
| E2E Status | UNVERIFIED | |
| Verified Commit | 鈥?| |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | |
| **Final Result** | **UNVERIFIED** | I051锛坉emo 妯″紡锛?|
| Evidence | `audit/evidence/F102-oauth-callback/` | 寰呭缓 |
| Issue | **I051** | |

---


### F103 — 海外预约 / 端点 / `POST /intl-booking/{id}/convert`（转化为会员 + 订单）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F103 | `feature-inventory.md` §Batch 1.4 row 23 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/views/intl-booking/index.vue:8-19` |
| 页面 | `views/intl-booking/index.vue` | |
| UI 板块 | 「国际预约」tab 操作列「转化为会员」按钮（仅 `status==='CONFIRMED' && !convertedMemberId` 可见） | `index.vue:42-44` |
| Action | 点击「转化为会员」→ `convert(row.id)` | `index.vue:42-44` |
| Handler | `convert(id)` | `index.vue:127-134` |
| API | `intlBookingApi.convertBooking(id)` | `frontend/meiyetec-web/src/api/intl-booking.ts:30` |
| Method | POST | |
| Controller | `IntlBookingService.convertBooking(id)` + 私有 `doCreateMember` + `doCreatePosOrder` | `service/IntlBookingService.java:386-408` / `:410-428` / `:430-449` |
| Service | — | |
| Mapper | `OverseasBookingMapper`（写回 `convertedMemberId/Order`）+ `JdbcTemplate`（直接 INSERT `crm_member` + `pos_order`） | `service/IntlBookingService.java:406`（updateById）+ `:415-422`（jdbc INSERT crm_member）+ `:432-446`（jdbc INSERT pos_order）|
| DB 表 | `overseas_booking`（写）+ `crm_member`（写）+ `pos_order`（写）| **overseas_booking** 不存在（db-snapshot.sql grep 0 匹配 — **I038**）；`crm_member` / `pos_order` 存在 |
| 关联表 | — | |
| 读/写 | RW（写 3 表）| |
| 事务 | `@Transactional`（默认 rollbackFor 仅 RuntimeException — **I009 模式 / I048**）| `IntlBookingService.java:387` |
| 权限 | 普通租户（`TenantContextHolder.get().getTenantId()`）| `IntlBookingService.java:389` |
| 软删除 | 无 | `overseas_booking` 无 `deleted` 列 |
| 审计字段 | `create_time / update_time`（JdbcTemplate 直写 `NOW()`）| `IntlBookingService.java:419-422` / `:443-446` |
| Static Status | L0 | 代码存在 |
| Runtime Status | **UNVERIFIED** | 表 MISSING（`overseas_booking` — I038）|
| DB Status | **MISSING**（`overseas_booking`）/ OK（`crm_member` + `pos_order`）| |
| Business Status | 完整（演示模式：`doCreateMember` 生成 `M{ts}` 会员号 + `doCreatePosOrder` 生成 `PO-INTL-{ts}` 订单号 + 写回 `convertedMemberId/Order`；JdbcTemplate 直写避免引入 MemberService 复杂依赖）| `IntlBookingService.java:386-449` |
| E2E Status | UNVERIFIED | Playwright 未配置 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql（**overseas_booking MISSING**）| |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 静态验证 |
| **Final Result** | **UNVERIFIED** | I038 + **I048**（无 rollbackFor）+ I051（演示模式）|
| Evidence | `audit/evidence/F103-convert-booking/` | 待建 |
| Issue | **I038** + **I048** + I051 |

---

### F104 — 海外预约 / 端点 / `GET /intl-booking/{id}` 单条查询

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F104 | `feature-inventory.md` §Batch 1.4 row 24 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/views/intl-booking/index.vue:8-19` |
| 页面 | `views/intl-booking/index.vue` | |
| UI 板块 | 「国际预约」tab table（间接通过列表点击 — 当前无独立详情页 UI）| `index.vue:24-46` |
| Action | （间接通过 F098 列表 / 操作列按钮）| |
| Handler | — | |
| API | `intlBookingApi.getBooking(id)` | `frontend/meiyetec-web/src/api/intl-booking.ts:26` |
| Method | GET | |
| Controller | `IntlBookingService.getBooking(id)` | `service/IntlBookingService.java:201-206` |
| Service | — | |
| Mapper | `OverseasBookingMapper.selectById(id)` | `service/IntlBookingService.java:203` |
| DB 表 | `overseas_booking`（读）| **不存在**（I038）|
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | 无 | |
| 审计字段 | — | |
| Static Status | L0 | 代码存在 |
| Runtime Status | **UNVERIFIED** | 表 MISSING |
| DB Status | **MISSING** | db-snapshot.sql 0 匹配 |
| Business Status | 完整（`selectById`；`null` → `BizCode.NOT_FOUND`）| `IntlBookingService.java:201-206` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql（MISSING）| |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 静态验证 |
| **Final Result** | **UNVERIFIED** | I038 |
| Evidence | `audit/evidence/F104-get-booking/` | 待建 |
| Issue | **I038** |

---

### F105 — 海外预约 / 端点 / `GET /intl-booking/channels` 静态渠道列表

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F105 | `feature-inventory.md` §Batch 1.4 row 25 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/views/intl-booking/index.vue:8-19` |
| 页面 | `views/intl-booking/index.vue` | |
| UI 板块 | 「渠道连接」tab 卡片网格（GOOGLE / INSTAGRAM / FACEBOOK / TIKTOK）| `index.vue:8-19` |
| Action | 页面加载 → `load()` → `intlBookingApi.listChannels()` | `index.vue:97` |
| Handler | `load()` | `index.vue:97-100` |
| API | `intlBookingApi.listChannels()` | `frontend/meiyetec-web/src/api/intl-booking.ts:18` |
| Method | GET | |
| Controller | `IntlBookingService.listChannels()` | `service/IntlBookingService.java:138-152` |
| Service | — | |
| Mapper | — | （纯静态枚举，无 DB 查询）|
| DB 表 | — | （无 DB）|
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | 无 | |
| 审计字段 | — | |
| Static Status | L0 | 代码存在 |
| Runtime Status | UNVERIFIED | Phase 5+ |
| DB Status | N/A | 无 DB |
| Business Status | 完整（4 渠道：GOOGLE / INSTAGRAM / FACEBOOK / TIKTOK；**FACEBOOK / TIKTOK status=PLANNED**；纯静态枚举）| `IntlBookingService.java:138-152` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 静态验证 |
| **Final Result** | **UNVERIFIED** | 无 Issue（纯静态端点）|
| Evidence | `audit/evidence/F105-list-channels/` | 待建 |
| Issue | — | |

---

### F106 — 海外预约 / 端点 / `POST /intl-booking/{channelId}/pull` 拉取预约（演示）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F106 | `feature-inventory.md` §Batch 1.4 row 26 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/views/intl-booking/index.vue:8-19` |
| 页面 | `views/intl-booking/index.vue` | |
| UI 板块 | 「渠道连接」tab 卡片「拉取预约」按钮（仅 `status==='ACTIVE'` 显示）| `index.vue:15` |
| Action | 点击「拉取预约」→ `pull(c.id)` | `index.vue:15` |
| Handler | `pull(id)` | `index.vue:115-119` |
| API | `intlBookingApi.pullBookings(channelId)` | `frontend/meiyetec-web/src/api/intl-booking.ts:21` |
| Method | POST | |
| Controller | `IntlBookingService.pullBookings(channelId)` | `service/IntlBookingService.java:154-187` |
| Service | — | |
| Mapper | `OverseasBookingMapper.insert(b)` | `service/IntlBookingService.java:181` |
| DB 表 | `overseas_booking`（写）| **不存在**（I038）|
| 关联表 | — | |
| 读/写 | RW（写 1 条演示）| |
| 事务 | `@Transactional`（**I009 模式 / I048**：无 rollbackFor）| `IntlBookingService.java:155` |
| 权限 | 普通租户（`TenantContextHolder.get().getTenantId()`）| `IntlBookingService.java:157` |
| 软删除 | 无 | |
| 审计字段 | — | |
| Static Status | L0 | 代码存在 |
| Runtime Status | **UNVERIFIED** | 表 MISSING + 演示模式 |
| DB Status | **MISSING** | db-snapshot.sql 0 匹配 |
| Business Status | 完整（演示模式：GOOGLE/INSTAGRAM 渠道插 1 条示例记录 `ExternalId="EXT-{ts}"`；FACEBOOK/TIKTOK 返回 `BizCode.NOT_FOUND`）| `IntlBookingService.java:154-187` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql（MISSING）| |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 静态验证 |
| **Final Result** | **UNVERIFIED** | I038 + I048 + I051（演示模式）|
| Evidence | `audit/evidence/F106-pull-bookings/` | 待建 |
| Issue | **I038** + **I048** + I051 |

---

### F107 — 预约确认 / i18n / 6 个 locale JSON 模板（zh-CN / zh-TW / en-US / ja-JP / ko-KR / fr-FR）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F107 | `feature-inventory.md` §Batch 1.4 row 27 |
| 前端路由 | — | （间接通过 F083 经 F084 渲染）|
| 页面 | — | |
| UI 板块 | — | |
| Action | — | （`BookingConfirmationTemplateResolver.warmup()` 启动期加载）|
| Handler | `BookingConfirmationTemplateResolver.warmup()` | `booking/BookingConfirmationTemplateResolver.java:36-44` |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `BookingConfirmationTemplateResolver`（`loadLocale(locale)`）| `booking/BookingConfirmationTemplateResolver.java:64-111` |
| Mapper | — | |
| DB 表 | — | （仅 classpath 资源 `i18n/template/*.json`）|
| 关联表 | — | |
| 读/写 | R（classpath）| |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 6 个 JSON 文件存在 |
| Runtime Status | UNVERIFIED | Phase 5 启动期 warmup 验证 |
| DB Status | N/A | |
| Business Status | 完整（6 locale JSON 内容完整核对通过：均含 `subject / sms / miniProgram(title+keyword1..keyword5) / email(subject+body)` 4 段；占位符 `${customerName/serviceName/bookingDate/bookingTime/bookingNo/storeName/storePhone}` 7 变量 — **与 SoT §5 完全一致**）| `resources/i18n/template/zh-CN.json` / `zh-TW.json` / `en-US.json` / `ja-JP.json` / `ko-KR.json` / `fr-FR.json` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 静态验证（6 文件 grep 核对通过）|
| **Final Result** | **UNVERIFIED** | 无 Issue（SoT 完全一致）|
| Evidence | `audit/evidence/F107-i18n-templates/` | 待建 |
| Issue | — | |

---

### F108 — 测试 / BookingConfirmationServiceTest（StubSms/Email + Mockito）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F108 | `feature-inventory.md` §Batch 1.4 row 28 |
| 前端路由 | — | （JUnit 测试）|
| 页面 | — | |
| UI 板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | — | |
| Mapper | — | |
| DB 表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 测试文件存在 |
| Runtime Status | UNVERIFIED | Phase 5 mvn test 验证 |
| DB Status | N/A | |
| Business Status | 完整（StubSmsSender / StubEmailSender 桩；Mockito mock StoreLookupService + resolver；测试点：渠道路由 + overallSuccess 聚合 + null booking 安全 + ChannelSender 缺失降级 + 门店信息读取）| `test/booking/BookingConfirmationServiceTest.java`（JUnit5）|
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 静态验证（文件存在）|
| **Final Result** | **UNVERIFIED** | 无 Issue（待 Phase 5 mvn test）|
| Evidence | `audit/evidence/F108-service-test/` | 待建 |
| Issue | — | |

---

### F109 — 测试 / BookingConfirmationTemplateResolverTest（locale 归一化 + 回退 + 渲染）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F109 | `feature-inventory.md` §Batch 1.4 row 29 |
| 前端路由 | — | （JUnit 测试）|
| 页面 | — | |
| UI 板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | — | |
| Mapper | — | |
| DB 表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 测试文件存在 |
| Runtime Status | UNVERIFIED | Phase 5 mvn test 验证 |
| DB Status | N/A | |
| Business Status | 完整（反射注入 ObjectMapper `@Autowired(required=false)` 留空；`warmup()` 触发 6 locale 加载；测试点：normalize `zh_CN → zh-CN` lang 小写 / country 大写 + 回退链 `zh-TW → zh-CN → en-US` + 占位符 `${}` 渲染 + 未注册 locale → `en-US`）| `test/booking/BookingConfirmationTemplateResolverTest.java`（JUnit5）|
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 静态验证 |
| **Final Result** | **UNVERIFIED** | 无 Issue（待 Phase 5 mvn test）|
| Evidence | `audit/evidence/F109-resolver-test/` | 待建 |
| Issue | — | |

---

### F110 — 测试 / EmailSenderTest + WechatOfficialAccountSenderTest

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F110 | `feature-inventory.md` §Batch 1.4 row 30 |
| 前端路由 | — | （JUnit 测试）|
| 页面 | — | |
| UI 板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | — | |
| Mapper | — | |
| DB 表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 测试文件存在 |
| Runtime Status | UNVERIFIED | Phase 5 mvn test 验证 |
| DB Status | N/A | |
| Business Status | 完整（**Phase 1 新发现 4 测试存在** — preflight §7.2 仅列 3 测试，新增 `WechatOfficialAccountSenderTest`；EmailSenderTest 6 测试点：channelType / receiver 校验 / demo 模式 / healthCheck；WechatOfficialAccountSenderTest 同模式：OA sender 测试）| `test/service/channel/EmailSenderTest.java` + `WechatOfficialAccountSenderTest.java` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 静态验证（4 文件 grep 核对）|
| **Final Result** | **UNVERIFIED** | 无 Issue（待 Phase 5 mvn test）|
| Evidence | `audit/evidence/F110-sender-tests/` | 待建 |
| Issue | — | |

---

### F111 — 前端 / 页面 / `intl-booking/index.vue`（2 tab + 3 对话框）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F111 | `feature-inventory.md` §Batch 1.4 row 31 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/router/index.ts`（待 Phase 2 确认路由注册）|
| 页面 | `views/intl-booking/index.vue`（2 tab + 3 对话框）| `frontend/meiyetec-web/src/views/intl-booking/index.vue:1-150+` |
| UI 板块 | 「渠道连接」tab el-row 卡片（GOOGLE/INSTAGRAM 绑定按钮 + 拉取预约按钮）+「国际预约」tab el-table（id/source/bookingNo/customerName/customerLocale/serviceName/bookingDate/bookingTime/status 列 + 操作列：确认 / 取消 / 确认模板 / 转化为会员 / 已转化标签）| `index.vue:8-19` / `:24-46` |
| Action | 渠道绑定 / 拉取 / 状态变更 / 模板预览 / 转化为会员 | `index.vue:15` / `:34` / `:35` / `:36` / `:37` |
| Handler | `connectGoogle/connectInstagram/doGoogle/doInstagram/pull/setStatus/convert/viewTemplate` | `index.vue:101-119` / `:121-125` / `:127-134` / `:136-139` |
| API | 全部对应 `IntlBookingService` 端点（F095-F106）| `frontend/meiyetec-web/src/api/intl-booking.ts:1-31` |
| Method | POST / GET | |
| Controller | — | （无新后端；全部复用 F095-F106 端点）|
| Service | — | |
| Mapper | — | |
| DB 表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 文件存在 |
| Runtime Status | UNVERIFIED | Phase 5 启动 + 手动验证 |
| DB Status | N/A | |
| Business Status | 完整（2 tab + 3 对话框：Google OAuth / Instagram OAuth / 模板预览；**⚠ Shadow I040**：状态确认按钮 (`setStatus(row, 'CONFIRMED')`) 无 loading / 无二次确认 / 无「确认即发送通知」文案提示；**⚠ Shadow I041**：无 `autoSend=false` UI）| `index.vue:34` / `:121-125` |
| E2E Status | UNVERIFIED | Playwright 未配置 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 静态验证 |
| **Final Result** | **SHELL-INTENTIONAL**（沿用 I006 / I007 模式，Post-Audit）| **I040**（autoSend=false UI 缺失）+ **I041**（按钮文案无「确认即发送通知」）|
| Evidence | `audit/evidence/F111-intl-booking-page/` | 待建 |
| Issue | **I040** + **I041** |

---

### F112 — 前端 / API / `intl-booking.ts`（12 端点封装）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F112 | `feature-inventory.md` §Batch 1.4 row 32 |
| 前端路由 | `/intl-booking/*` | （API 封装层，非页面）|
| 页面 | — | |
| UI 板块 | — | |
| Action | 全部对应 `IntlBookingService` 端点 | `frontend/meiyetec-web/src/api/intl-booking.ts:1-31` |
| Handler | `intlBookingApi.connectGoogle/googleCallback/connectInstagram/instagramCallback/listChannels/pullBookings/listBookings/getBooking/updateStatus/convertBooking/confirmationTemplate`（**11 个方法** — Phase 1 grep 确认）| `frontend/meiyetec-web/src/api/intl-booking.ts:5-31` |
| API | `request.post / request.get`（基于 axios 封装）| `frontend/meiyetec-web/src/api/intl-booking.ts:1-31` |
| Method | POST / GET | |
| Controller | — | （无新后端）|
| Service | — | |
| Mapper | — | |
| DB 表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 文件存在 |
| Runtime Status | UNVERIFIED | Phase 5 启动 + 手动验证 |
| DB Status | N/A | |
| Business Status | 完整（OAuth × 4：connectGoogle / googleCallback / connectInstagram / instagramCallback + listChannels + pullBookings + listBookings + getBooking + updateStatus + convertBooking + confirmationTemplate；**⚠ Shadow I042**：`sendConfirmation` 方法缺失 — 后端 `POST /intl-booking/{id}/send-confirmation` 端点存在但前端无对应入口）| `frontend/meiyetec-web/src/api/intl-booking.ts:5-31` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 静态验证 |
| **Final Result** | **SHELL-INTENTIONAL**（沿用 I006 / I007 模式，Post-Audit）| **I042**（`sendConfirmation` 方法缺失）|
| Evidence | `audit/evidence/F112-intl-booking-api/` | 待建 |
| Issue | **I042** |

---

### F113 — 业务入口 / 命名异味 / `IntlBookingService` `@RestController` 命名误导

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F113 | `feature-inventory.md` §Batch 1.4 row 33 |
| 前端路由 | — | （架构异味，无 UI）|
| 页面 | — | |
| UI 板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `IntlBookingService` `@RestController @RequestMapping("/intl-booking")`（命名误导 — 应在 `controller/` 包）| `service/IntlBookingService.java:14-17` |
| Service | — | |
| Mapper | — | |
| DB 表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 文件存在 |
| Runtime Status | N/A | 架构异味 |
| DB Status | N/A | |
| Business Status | 完整（`@RestController` + 13 个 REST 端点全在一个文件里 — 与项目其他 Controller 在 `controller/` 包下不一致；**与 Batch 1.1 I004 / 1.2 I019 / 1.3 I029 完全同模式（第 4 次出现）**）| `service/IntlBookingService.java:14-17` / `:46-510` |
| E2E Status | N/A | |
| Verified Commit | — | N/A |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-14 (UTC+8) | Phase 2 静态验证 |
| **Final Result** | **SHELL-INTENTIONAL**（backlog — 第 4 次命名异味，Post-Audit 统一整改）| **I044**（I004/I019/I029 同模式 — 第 4 次出现）|
| Evidence | `audit/evidence/F113-naming-smell/` | 待建 |
| Issue | **I044** |

---

## Batch 1.4 统计

| 分类 | 数量 | Feature IDs |
|---|---|---|
| OverseasBooking Entity + DB | 2 | F081, F082 |
| BookingConfirmationService 核心 + DTO × 2 + Resolver | 4 | F083-F086 |
| StoreLookupService（pos_store 查询 + 缓存）| 1 | F087 |
| Channel Sender × 5（Sms / Email / MiniProgram / InApp / WechatOA）| 5 | F088-F092 |
| Channel 接口 + Dispatcher | 2 | F093, F094 |
| IntlBookingService 端点（手动确认 / autoSend / 模板 / 列表 / 单条 / 渠道 / 拉取）| 7 | F095-F098, F104-F106 |
| Google / Instagram webhook（POST + GET verify）| 3 | F099-F101 |
| OAuth2 connect + callback | 1 | F102（合并 4 端点）|
| 预约转化（convert）| 1 | F103 |
| i18n 6 locale JSON（合并）| 1 | F107 |
| 测试（Service / Resolver / 2 sender）| 3 | F108-F110 |
| 前端（页面 + API）| 2 | F111, F112 |
| 命名异味（架构问题）| 1 | F113 |
| **合计** | **33** | F081-F113 |

**最终状态分布**（按用户指令 + Phase 1 决策）：

| Result | 数量 | Feature IDs |
|---|---|---|
| **MISSING** | 11 | F081, F082, F088（间接 — mkt_channel_config），F091（member_message 表），F092（间接），F095-F100，F104, F106（`overseas_booking` 表）|
| **UNVERIFIED** | 17 | F083, F084, F085, F086, F087, F089, F090, F093, F094, F097, F098, F099, F100, F101, F102, F103, F105, F107, F108, F109, F110 |
| **SHELL-INTENTIONAL** | 3 | F111（Shadow I040+I041）， F112（Shadow I042）， F113（命名异味 I044）|

---

## 链路图（核心调用链）

```
┌────────────────────────────────────────────────────────────────┐
│  前端 /intl-booking                                             │
│  views/intl-booking/index.vue                                  │
│  router/index.ts（待确认路由）                                   │
└─────────────┬──────────────────────────────────────────────────┘
              │ intlBookingApi.updateStatus(id, {status:'CONFIRMED'})
              │ ⚠ I040/I041：未传 autoSend 字段（Shadow Post-Audit）
              │ ⚠ I042：intl-booking.ts 无 sendConfirmation 封装（Shadow）
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Controller: IntlBookingService（命名误导 - I044）              │
│  backend/src/main/java/com/meiyetec/service/                   │
│    IntlBookingService.java                                      │
│  ├─ F102 connectGoogle     :46-70                              │
│  ├─ F102 googleCallback    :72-92 (@Transactional)            │
│  ├─ F102 connectInstagram  :95-118                             │
│  ├─ F102 instagramCallback :120-136 (@Transactional)          │
│  ├─ F105 listChannels      :138-152                            │
│  ├─ F106 pullBookings      :154-187 (@Transactional) ← 演示模式│
│  ├─ F098 listBookings      :189-198                            │
│  ├─ F104 getBooking        :201-206                            │
│  ├─ F096 updateStatus      :208-251 (@Transactional ⚠无RBFor)  │
│  │   └─ CONFIRMED → autoSend=true → confirmationService.send    │
│  │       catch 吞异常 → confirmationError（I047 I008 模式）     │
│  ├─ F095 sendConfirmation  :253-267                             │
│  ├─ F099 googleWebhook     :269-307 (@Transactional ⚠无RBFor)   │
│  ├─ F100 instagramWebhook  :309-362 (@Transactional ⚠无RBFor)   │
│  ├─ F101 instagramVerify   :364-384                            │
│  ├─ F103 convertBooking    :386-449 (@Transactional ⚠无RBFor) │
│  └─ F097 confirmationTemplate :481-510 ⚠硬编码 switcher（I043）│
└─────────────┬──────────────────────────────────────────────────┘
              │ confirmationService.sendConfirmation(b)
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Service: BookingConfirmationService (booking 包)              │
│  backend/src/main/java/com/meiyetec/booking/                   │
│    BookingConfirmationService.java:69-115                       │
│  ├─ F083 buildVars  :157-167（构造占位符，含 F087 pos_store） │
│  ├─ F084 resolver.resolve → warmup 6 locale → render          │
│  └─ F083 渠道路由  :104-140                                    │
│      ├─ customerPhone → F088 SmsSender（UNVERIFIED - I049）     │
│      ├─ customerEmail → F089 EmailSender（UNVERIFIED - I049）    │
│      └─ miniProgramOpenId → F090 MiniProgramSender（UNVERIFIED）  │
│          ⚠ F083 仅路由 SMS/EMAIL/MINI（IN_APP/WECHAT_OA 未触达）│
└─────────────┬──────────────────────────────────────────────────┘
              │
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Adapters: ChannelSender 实现                                   │
│  ├─ F088 SmsSender (service/channel/SmsSender.java)            │
│  │   └─ MktChannelConfigMapper → mkt_channel_config（间接）     │
│  ├─ F089 EmailSender (EmailSender.java)                        │
│  │   └─ 三模式：租户 cfg → 真实 SMTP / 全局 spring.mail.* / mismatch→demo │
│  ├─ F090 MiniProgramSender (MiniProgramSender.java)           │
│  │   └─ AccessToken 缓存 7100s；MP_TEMPLATE_ID 占位（I038）     │
│  ├─ F091 InAppSender (InAppSender.java)                       │
│  │   └─ ⚠ MISSING - member_message 表不存在（I045）             │
│  └─ F092 WechatOfficialAccountSender (WechatOfficialAccountSender.java)│
│      └─ ⚠ CONFLICT I039：channelType="WECHAT_OFFICIAL_ACCOUNT" vs SoT "WECHAT_OA"│
└─────────────┬──────────────────────────────────────────────────┘
              │ ChannelDispatcher.getSender(channelType)
              ▼
┌────────────────────────────────────────────────────────────────┐
│  Entities:                                                      │
│  ├─ F081 OverseasBooking (entity/OverseasBooking.java:11-31)    │
│  │   └─ ⚠ MISSING - overseas_booking 表不存在（I038）           │
│  ├─ F085 BookingConfirmationTemplate (booking/Template.java)   │
│  └─ F086 BookingConfirmationResult (booking/Result.java)          │
│  ├─ F087 StoreLookupService (booking/StoreLookupService.java)  │
│  │   └─ pos_store（✅ 存在，但 store_name/contact_phone 待验）   │
│  └─ F084 BookingConfirmationTemplateResolver (booking/Resolver.java)│
│      └─ warmup 6 locale → classpath i18n/template/*.json       │
└─────────────┬──────────────────────────────────────────────────┘
              │ MyBatis Mapper / JdbcTemplate
              ▼
┌────────────────────────────────────────────────────────────────┐
│  DB:                                                            │
│  ├─ overseas_booking ❌ 表不存在 (db-snapshot 0 匹配, I038)      │
│  ├─ member_message  ❌ 表不存在 (db-snapshot 0 匹配, I045)       │
│  ├─ pos_store      ✅ 存在（5 匹配）；⚠ store_name/contact_phone I046 │
│  ├─ mkt_channel_config ✅ 存在（EXT-09）；⚠ 渠道凭据未配置 I049 │
│  ├─ crm_member     ✅ 存在（F103 convert 用 JdbcTemplate 直写）  │
│  └─ pos_order      ✅ 存在（F103 convert 用 JdbcTemplate 直写） │
└───────────────────────────────────��─────────────────────────────┘
```

---

## 已知约束（引用 Issue Register）

| Issue ID | 影响 Feature | Final Result | 处理 |
|---|---|---|---|
| **I038** | F081/F082/F095-F106 | **MISSING** | Post-Audit 统一跑 `migrate_ext_19_mini_program_openid.sql` |
| **I039** | F092 | **CONFLICT**（中立标）| Post-Audit 人类决策 |
| **I040** | F096/F111 | **SHELL-INTENTIONAL**（Post-Audit）| Post-Audit 加 autoSend=false UI |
| **I041** | F096/F111 | **SHELL-INTENTIONAL**（Post-Audit）| Post-Audit 加按钮文案提示 |
| **I042** | F095/F112 | **SHELL-INTENTIONAL**（Post-Audit）| Post-Audit 加 sendConfirmation API 封装 |
| **I043** | F097 | **CONFLICT**（中立标）| Post-Audit 人类决策（硬编码 switcher vs Resolver）|
| **I044** | F113 | **SHELL-INTENTIONAL**（backlog）| Post-Audit 统一整改（I004/I019/I029 同模式）|
| **I045** | F091 | **MISSING** | Post-Audit 统一跑 `member_message` DDL |
| **I046** | F087 | **UNVERIFIED** | Phase 5 `DESCRIBE pos_store` 验证字段对齐 |
| **I047** | F096 | **UNVERIFIED** | 接受现状（SoT §0.3 明确设计）|
| **I048** | F096/F099/F100/F103 | **UNVERIFIED** | Post-Audit 决策加 rollbackFor |
| **I049** | F088/F089/F090/F092 | **UNVERIFIED** | Mock 降级；真实链路 UNVERIFIED |
| **I050** | F094 | **UNVERIFIED** | 接受现状（T07-D4 例外）|
| **I051** | F099/F100/F102/F106 | **UNVERIFIED** | 接受现状（P3-26 演示模式）|

---

## 验证范围声明（Phase 2 完成）

**Phase 2 静态追溯完成**（Batch 1.4 F081-F113）：

1. ✅ **DB MISSING 确认**：`overseas_booking`（db-snapshot.sql grep 0 匹配）+ `member_message`（db-snapshot.sql grep 0 匹配）
2. ✅ **调用图完整**：前端 → API → Controller → Service → ChannelSender → DB 全栈可达（L0）
3. ✅ **Issue 决策落地**：I038/I039/I040/I041/I042/I043/I044/I045 决策已落档
4. ✅ **CONFLICT 标注**：F092（I039）+ F097（I043）中立标
5. ✅ **Shadow 标注**：F111/F112（I040/I041/I042）Post-Audit
6. ✅ **UNVERIFIED 标注**：16 Features 缺运行证据
7. ✅ **backlog 标注**：F113（I044）第 4 次命名异味

**不**包括：
- ❌ Phase 3.5 Diff Audit（Batch 1.4 无重构变更）
- ❌ Phase 4 运行时计划
- ❌ Phase 5 L4/L5a/L5b 验证（需运行项目）
- ❌ 修改任何业务代码（规则 6：审计阶段只记录不修复）

---

## Batch 1.5 — MKT 多渠道重构（27 Features · F114-F140）

> **范围来源**：`audit/baseline/scope-freeze-batch-1.5.md` §2.3 + `audit/baseline/preflight-batch-1.5.md` §七
> **SoT**：`docs/ROLLBACK-6.2-mkt_ddl.md`（MKT-002 SoT）+ git status（⚠ 无 .mdc Operational Notes）
> **Phase 0 freeze commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`
> **Diff Audit 产出**：`audit/diff-audit/mkt-refactor.md`（Phase 3.5，8 项全部完成）
> **DB 实际状态（Phase 1 静态扫描）**：
> - `mkt_channel_config` ✅ 存在于 db-snapshot.sql:6313
> - `mkt_channel_template` ✅ db-snapshot.sql:6348
> - `mkt_coupon` ✅ db-snapshot.sql:6384
> - `mkt_coupon_batch` ✅ db-snapshot.sql:6424
> - `mkt_promotion` ✅ db-snapshot.sql:6761
> - `mkt_points_rule` ✅ db-snapshot.sql:6699
> - `marketing_touch_*` ✅ db-snapshot.sql:5287/5312/5347
> - `market_activity` ✅ db-snapshot.sql:5177（未 DROP，迁移未跑）
> - `mkt_invitation` / `mkt_invitation_rule` / `mkt_dispatch` / `mkt_dispatch_target` ❌ **CRITICAL MISSING**（I063，db-snapshot 0 匹配）
> **用户决策汇总（6 项，2026-09-15 22:00）**：
> - I055：不改 SoT / 不重构 → CONFLICT 中立标
> - I060：不加 try/catch → 登记 Issue，Post-Audit
> - I061：不改 Router → 登记 Issue，Post-Audit
> - I063：本批次仅落档 → 所有 MISSING 表 Post-Audit 统一跑迁移
> - I064：不写 api/mkt.ts → 登记 Issue，Post-Audit
> - I055/I056/I067 → F139 标 PARTIAL-BLOCKER（重构中间态）
> - I063 → 涉及 mkt_invitation/mkt_dispatch 等表的 Feature 标 MISSING
> - I060 → F132 标 CRITICAL-P1
> - I061 → F135 标 CRITICAL-P2
> - I064 → F114-F134 全部前端相关 Feature 标 PARTIAL-NON-BLOCKER（无 api/mkt.ts）
> **Issue 决策来源**：`audit/feature-inventory.md` §1.5.5 + `audit/issues/issue-register.md` §十九（I055-I068）

---

### F114 — MKT Channel / Controller / 渠道列表查询

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F114 | `feature-inventory.md` §1.5.3 row 1 |
| 前端路由 | `/mkt/channel` | `frontend/meiyetec-web/src/router/index.ts`（待 grep 确认）|
| 页面 | `views/mkt/channel.vue` | `frontend/meiyetec-web/src/views/mkt/channel.vue` |
| UI 板块 | 渠道列表 el-table | `channel.vue` |
| Action | 页面加载 / 渠道配置管理 | `channel.vue` |
| Handler | 待 grep 确认 | `channel.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| `frontend/meiyetec-web/src/api/` 下无 `mkt.ts` |
| Method | GET | |
| Controller | `MktChannelController.list()` | `controller/MktChannelController.java:44-47` |
| Service | `MarketingAutomationService.channelList()` | `service/MarketingAutomationService.java:55-65` |
| Mapper | `MktChannelConfigMapper.selectList` + JdbcTemplate | `mapper/MktChannelConfigMapper.java` |
| DB 表 | `mkt_channel_config` ✅ | db-snapshot.sql:6313 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | 普通租户（强制 `tenantId`） | `MarketingAutomationService.java:57` |
| 软删除 | `deleted = 0` | `channelList():59` |
| 审计字段 | — | |
| Static Status | L0 | 静态分析可达 |
| Runtime Status | UNVERIFIED | 缺 api/mkt.ts |
| DB Status | ✅ 存在 | db-snapshot.sql:6313 |
| Business Status | 完整（强制 tenantId 过滤；JdbcTemplate 15 列直查；Keys.camel(rows) 转驼峰）| |
| E2E Status | UNVERIFIED | 无 Playwright 脚本 |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）；I054 NEW 已解除（表存在 ✅）|
| Evidence | `audit/evidence/F114-mkt-channel-list/` | 待建 |
| Issue | **I064** | |

---

### F115 — MKT Channel / Controller / 创建渠道配置

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F115 | `feature-inventory.md` §1.5.3 row 2 |
| 前端路由 | `/mkt/channel` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/channel.vue` | `frontend/meiyetec-web/src/views/mkt/channel.vue` |
| UI 板块 | 新建渠道对话框 | `channel.vue` |
| Action | 创建渠道配置 | `channel.vue` |
| Handler | 待 grep 确认 | `channel.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | POST | |
| Controller | `MktChannelController.create()` | `controller/MktChannelController.java:50-52` |
| Service | `MarketingAutomationService.createChannel()` | `service/MarketingAutomationService.java:67-83` |
| Mapper | `MktChannelConfigMapper.insert` | `mapper/MktChannelConfigMapper.java` |
| DB 表 | `mkt_channel_config` | db-snapshot.sql:6313 |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | `create_time / update_time` | `MarketingAutomationService.java:83` |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6313 |
| Business Status | 完整（校验 channelType 必填；同租户同品牌同渠道查重 → BizCode.CONFLICT；默认值 status=1/retryMax=3/retryIntervalSec=60/rateLimitPerMin=60；⚠ `rateLimitPerDay` 空语句无 setter — I062）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）+ **I062**（rateLimitPerDay setter 空语句）|
| Evidence | `audit/evidence/F115-mkt-channel-create/` | 待建 |
| Issue | **I064** + **I062** | |

---

### F116 — MKT Channel / Controller / 更新渠道配置

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F116 | `feature-inventory.md` §1.5.3 row 3 |
| 前端路由 | `/mkt/channel` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/channel.vue` | `frontend/meiyetec-web/src/views/mkt/channel.vue` |
| UI 板块 | 编辑渠道对话框 | `channel.vue` |
| Action | 更新渠道配置 | `channel.vue` |
| Handler | 待 grep 确认 | `channel.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | PUT | |
| Controller | `MktChannelController.update()` | `controller/MktChannelController.java:55-57` |
| Service | `MarketingAutomationService.updateChannel()` | `service/MarketingAutomationService.java:85-92` |
| Mapper | `MktChannelConfigMapper.updateById` | `mapper/MktChannelConfigMapper.java` |
| DB 表 | `mkt_channel_config` | db-snapshot.sql:6313 |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | — | |
| 权限 | 普通租户（强制保留原 tenantId）| `MarketingAutomationService.java:91` |
| 软删除 | — | |
| 审计字段 | `update_time` | `MarketingAutomationService.java:92` |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6313 |
| Business Status | 完整（校验存在性 → BizCode.NOT_FOUND；`c.setTenantId(old.getTenantId())` 强制保留原租户防越权写）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F116-mkt-channel-update/` | 待建 |
| Issue | **I064** | |

---

### F117 — MKT Channel / Controller / 删除渠道配置

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F117 | `feature-inventory.md` §1.5.3 row 4 |
| 前端路由 | `/mkt/channel` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/channel.vue` | `frontend/meiyetec-web/src/views/mkt/channel.vue` |
| UI 板块 | 操作列删除按钮 | `channel.vue` |
| Action | 删除渠道配置 | `channel.vue` |
| Handler | 待 grep 确认 | `channel.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | DELETE | |
| Controller | `MktChannelController.delete()` | `controller/MktChannelController.java:60-62` |
| Service | `MarketingAutomationService.deleteChannel()` | `service/MarketingAutomationService.java:94-97` |
| Mapper | `MktChannelConfigMapper.deleteById` | `mapper/MktChannelConfigMapper.java` |
| DB 表 | `mkt_channel_config` | db-snapshot.sql:6313 |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | — | |
| 权限 | ⚠ 未校验租户归属（跨租户删除风险）| `deleteChannel():97` 无 `t.equals(old.getTenantId())` 校验 |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6313 |
| Business Status | 完整（直接 `deleteById`）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）+ **I056**（跨租户删除风险 — 用户决策：不修，登记 Issue）|
| Evidence | `audit/evidence/F117-mkt-channel-delete/` | 待建 |
| Issue | **I064** + **I056** | |

---

### F118 — MKT Channel / Controller / 渠道健康检查

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F118 | `feature-inventory.md` §1.5.3 row 5 |
| 前端路由 | `/mkt/channel` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/channel.vue` | `frontend/meiyetec-web/src/views/mkt/channel.vue` |
| UI 板块 | 健康检查按钮 | `channel.vue` |
| Action | 触发渠道健康检查 | `channel.vue` |
| Handler | 待 grep 确认 | `channel.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | POST | |
| Controller | `MktChannelController.healthCheck()` | `controller/MktChannelController.java:65-67` |
| Service | `ChannelDispatcher.healthCheckAll()` | `service/channel/ChannelDispatcher.java:48-66` |
| Mapper | `MktChannelConfigMapper.selectList` + JdbcTemplate | `service/channel/ChannelDispatcher.java:58-65` |
| DB 表 | `mkt_channel_config` | db-snapshot.sql:6313 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | — | |
| 权限 | ⚠ 硬编码 `Long tenantId = 1L`（I050 T07-D4 例外）| `ChannelDispatcher.java:51` |
| 软删除 | `deleted = 0` | `ChannelDispatcher.java:62` |
| 审计字段 | `last_health_at / last_health_msg` | `ChannelDispatcher.java:63-64` |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6313 |
| Business Status | 完整（5 sender 轮询 → OK/FAIL；只更新 tenant_id=1 的健康状态；log.warn 声明设计意图）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）+ **I050**（硬编码 tenant_id=1，Phase 3 死代码审查）|
| Evidence | `audit/evidence/F118-mkt-channel-healthcheck/` | 待建 |
| Issue | **I064** + **I050** | |

---

### F119 — MKT Conversion / Controller / 转化漏斗统计

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F119 | `feature-inventory.md` §1.5.3 row 6 |
| 前端路由 | `/mkt/conversion` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/conversion.vue` | `frontend/meiyetec-web/src/views/mkt/conversion.vue` |
| UI 板块 | 漏斗统计板块 | `conversion.vue` |
| Action | 查看转化漏斗 | `conversion.vue` |
| Handler | 待 grep 确认 | `conversion.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | GET | |
| Controller | `MktConversionController.funnel()` | `controller/MktConversionController.java:33-37` |
| Service | `MarketingAutomationService.conversionFunnel()` | `service/MarketingAutomationService.java:638-673` |
| Mapper | JdbcTemplate 直查 | `service/MarketingAutomationService.java:655-672` |
| DB 表 | `marketing_touch_conversion` + `marketing_touch_log` | db-snapshot.sql:5287/5312 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:5287/5312 |
| Business Status | 完整（4 段漏斗：触达 → 发送 → 已读 → 转化；JdbcTemplate 聚合查询）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F119-mkt-conversion-funnel/` | 待建 |
| Issue | **I064** | |

---

### F120 — MKT Conversion / Controller / ROI 统计

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F120 | `feature-inventory.md` §1.5.3 row 7 |
| 前端路由 | `/mkt/conversion` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/conversion.vue` | `frontend/meiyetec-web/src/views/mkt/conversion.vue` |
| UI 板块 | ROI 统计板块 | `conversion.vue` |
| Action | 查看 ROI 统计 | `conversion.vue` |
| Handler | 待 grep 确认 | `conversion.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | GET | |
| Controller | `MktConversionController.roi()` | `controller/MktConversionController.java:40-42` |
| Service | `MarketingAutomationService.conversionRoi()` | `service/MarketingAutomationService.java:675-713` |
| Mapper | JdbcTemplate 直查 | `service/MarketingAutomationService.java:696-712` |
| DB 表 | `marketing_touch_conversion` | db-snapshot.sql:5287 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:5287 |
| Business Status | 完整（按 taskId 过滤 ROI = 转化收益 / 触达成本）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F120-mkt-conversion-roi/` | 待建 |
| Issue | **I064** | |

---

### F121 — MKT Conversion / Controller / 按渠道统计

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F121 | `feature-inventory.md` §1.5.3 row 8 |
| 前端路由 | `/mkt/conversion` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/conversion.vue` | `frontend/meiyetec-web/src/views/mkt/conversion.vue` |
| UI 板块 | 渠道统计板块 | `conversion.vue` |
| Action | 按渠道查看统计 | `conversion.vue` |
| Handler | 待 grep 确认 | `conversion.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | GET | |
| Controller | `MktConversionController.byChannel()` | `controller/MktConversionController.java:45-47` |
| Service | `MarketingAutomationService.byChannel()` | `service/MarketingAutomationService.java:715-729` |
| Mapper | JdbcTemplate 直查 | `service/MarketingAutomationService.java:723-728` |
| DB 表 | `marketing_touch_log` + `marketing_touch_conversion` | db-snapshot.sql:5312/5287 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:5312/5287 |
| Business Status | 完整（按 channel_type 分组聚合）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F121-mkt-conversion-bychannel/` | 待建 |
| Issue | **I064** | |

---

### F122 — MKT Conversion / Controller / 记录转化（业务回调）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F122 | `feature-inventory.md` §1.5.3 row 9 |
| 前端路由 | — | （无 UI 入口；业务回调）|
| 页面 | — | |
| UI 板块 | — | |
| Action | 业务系统回调写入转化记录 | |
| Handler | — | |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | POST | |
| Controller | `MktConversionController.track()` | `controller/MktConversionController.java:50-55` |
| Service | `MarketingAutomationService.trackConversion()` | `service/MarketingAutomationService.java:731-760` |
| Mapper | `MarketingTouchConversionMapper.insert` + JdbcTemplate | `service/MarketingAutomationService.java:740-759` |
| DB 表 | `marketing_touch_conversion` | db-snapshot.sql:5287 |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | — | |
| 权限 | ⚠ 未校验租户归属（跨租户写风险 — I057）| `MarketingAutomationService.java:731` body 直接取 tenantId |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:5287 |
| Business Status | 完整（写入转化事件）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I057**（跨租户写风险）+ **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F122-mkt-conversion-track/` | 待建 |
| Issue | **I057** + **I064** | |

---

### F123 — MKT Conversion / Controller / 标记已读（渠道回调）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F123 | `feature-inventory.md` §1.5.3 row 10 |
| 前端路由 | — | （无 UI 入口；渠道回调）|
| 页面 | — | |
| UI 板块 | — | |
| Action | 渠道回调标记触达日志为已读 | |
| Handler | — | |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | POST | |
| Controller | `MktConversionController.markRead()` | `controller/MktConversionController.java:58-60` |
| Service | `MarketingAutomationService.markRead()` | `service/MarketingAutomationService.java:762-` |
| Mapper | JdbcTemplate | `service/MarketingAutomationService.java:764` |
| DB 表 | `marketing_touch_log` | db-snapshot.sql:5312 |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | — | |
| 权限 | ⚠ 未校验租户归属（跨租户写风险 — I057 同模式）| `MarketingAutomationService.java:762` body 直接取 tenantId |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:5312 |
| Business Status | 完整（标记触达日志为已读）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I057**（跨租户写风险）+ **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F123-mkt-conversion-markread/` | 待建 |
| Issue | **I057** + **I064** | |

---

### F124 — MKT Journey / Controller / 创建旅程

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F124 | `feature-inventory.md` §1.5.3 row 11 |
| 前端路由 | `/mkt/journey` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/journey.vue` | `frontend/meiyetec-web/src/views/mkt/journey.vue` |
| UI 板块 | 新建旅程表单 | `journey.vue` |
| Action | 创建用户旅程 | `journey.vue` |
| Handler | 待 grep 确认 | `journey.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | POST | |
| Controller | `MktJourneyController.create()` | `controller/MktJourneyController.java:38-66` |
| Service | `MarketingAutomationService.createJourney()` | `service/MarketingAutomationService.java:417-` |
| Mapper | `MktJourneyMapper.insert` | `mapper/MktJourneyMapper.java` |
| DB 表 | `mkt_journey` | db-snapshot.sql:6505 |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | `@Transactional` | `MktJourneyController.java:38` |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | `create_time / update_time` | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6505 |
| Business Status | 完整（body 包含 journey_type / nodes / triggers）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F124-mkt-journey-create/` | 待建 |
| Issue | **I064** | |

---

### F125 — MKT Journey / Controller / 旅程分页（POST + GET 双形式）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F125 | `feature-inventory.md` §1.5.3 row 12 |
| 前端路由 | `/mkt/journey` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/journey.vue` | `frontend/meiyetec-web/src/views/mkt/journey.vue` |
| UI 板块 | 旅程列表 el-table | `journey.vue` |
| Action | 分页查询旅程 | `journey.vue` |
| Handler | 待 grep 确认 | `journey.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | POST / GET（双形式）| |
| Controller | `MktJourneyController.page()` | `controller/MktJourneyController.java:42-60` |
| Service | `MarketingAutomationService.journeyPage()` | `service/MarketingAutomationService.java:467-499` |
| Mapper | `MktJourneyMapper.selectPage` | `mapper/MktJourneyMapper.java` |
| DB 表 | `mkt_journey` | db-snapshot.sql:6505 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6505 |
| Business Status | 完整（POST `/page` body 参数 + GET `/page` query 参数同 handler — ⚠ 双路由形式 I058）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）+ **I058**（双路由形式语义模糊 — 用户决策：不修，登记 Issue）|
| Evidence | `audit/evidence/F125-mkt-journey-page/` | 待建 |
| Issue | **I064** + **I058** | |

---

### F126 — MKT Journey / Controller / 旅程详情 + 更新 + 发布 + 暂停

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F126 | `feature-inventory.md` §1.5.3 row 13 |
| 前端路由 | `/mkt/journey` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/journey.vue` | `frontend/meiyetec-web/src/views/mkt/journey.vue` |
| UI 板块 | 旅程详情 / 编辑 / 发布 / 暂停按钮 | `journey.vue` |
| Action | 详情 / 更新 / 发布 / 暂停旅程 | `journey.vue` |
| Handler | 待 grep 确认 | `journey.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | GET / PUT / POST | |
| Controller | `MktJourneyController.detail/update/publish/pause` | `controller/MktJourneyController.java:63-78` |
| Service | `MarketingAutomationService.journeyDetail/updateJourney/publishJourney/pauseJourney` | `service/MarketingAutomationService.java` |
| Mapper | `MktJourneyMapper` | `mapper/MktJourneyMapper.java` |
| DB 表 | `mkt_journey` | db-snapshot.sql:6505 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6505 |
| Business Status | 完整（4 端点合 1 Feature；发布/暂停改 journey 状态）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F126-mkt-journey-crud/` | 待建 |
| Issue | **I064** | |

---

### F127 — MKT Journey / Controller / 会员进入旅程 + 推进下一节点

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F127 | `feature-inventory.md` §1.5.3 row 14 |
| 前端路由 | `/mkt/journey` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/journey.vue` | `frontend/meiyetec-web/src/views/mkt/journey.vue` |
| UI 板块 | 进入旅程按钮 / 推进节点 | `journey.vue` |
| Action | 会员进入旅程 / 推进下一节点 | `journey.vue` |
| Handler | 待 grep 确认 | `journey.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | POST | |
| Controller | `MktJourneyController.enter()` + `nextNode()` | `controller/MktJourneyController.java:83-91` |
| Service | `MarketingAutomationService.enterJourney()` + `nextNode()` | `service/MarketingAutomationService.java:521-` + `:560-` |
| Mapper | `MktJourneyInstanceMapper.insert` | `mapper/MktJourneyInstanceMapper.java` |
| DB 表 | `mkt_journey_instance` | db-snapshot.sql:6540 |
| 关联表 | `mkt_journey` / `mkt_channel_template` | |
| 读/写 | W | |
| 事务 | `@Transactional(rollbackFor = Exception.class)` | `MarketingAutomationService.java:521` |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6540 |
| Business Status | 完整（nextNode 通过 MktChannelTemplate 触发触达 — line 598 引用 tplCode）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F127-mkt-journey-instance/` | 待建 |
| Issue | **I064** | |

---

### F128 — MKT Template / Controller / 模板列表 + 创建 + 审核

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F128 | `feature-inventory.md` §1.5.3 row 15 |
| 前端路由 | `/mkt/template` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/template.vue` | `frontend/meiyetec-web/src/views/mkt/template.vue` |
| UI 板块 | 模板列表 / 新建 / 审核 | `template.vue` |
| Action | 查询 / 创建 / 审核模板 | `template.vue` |
| Handler | 待 grep 确认 | `template.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | GET / POST / PUT | |
| Controller | `MktTemplateController.list/create/audit` | `controller/MktTemplateController.java:32-44` |
| Service | `MarketingAutomationService.templateList/createTemplate/auditTemplate` | `service/MarketingAutomationService.java` |
| Mapper | `MktChannelTemplateMapper` | `mapper/MktChannelTemplateMapper.java` |
| DB 表 | `mkt_channel_template` | db-snapshot.sql:6348 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6348 |
| Business Status | 完整（3 端点；audit 用 `@PutMapping("/{id}/audit")` 改模板状态）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F128-mkt-template/` | 待建 |
| Issue | **I064** | |

---

### F129 — MKT TouchTask / Controller / 任务分页 + 创建 + 生命周期 + 日志

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F129 | `feature-inventory.md` §1.5.3 row 16 |
| 前端路由 | `/mkt/task` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/touch-task.vue` | `frontend/meiyetec-web/src/views/mkt/touch-task.vue` |
| UI 板块 | 任务列表 / 新建 / 生命周期控制 / 日志 | `touch-task.vue` |
| Action | 查询 / 创建 / 启动 / 暂停 / 恢复 / 取消任务 | `touch-task.vue` |
| Handler | 待 grep 确认 | `touch-task.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | POST / GET | |
| Controller | `MktTouchTaskController` 9 端点 | `controller/MktTouchTaskController.java:37-86` |
| Service | `MarketingAutomationService.taskPage/createTask/startTask/pauseTask/resumeTask/cancelTask/taskLogPage` | `service/MarketingAutomationService.java` |
| Mapper | `MarketingTouchTaskMapper` + `MarketingTouchLogMapper` | `mapper/MarketingTouchTaskMapper.java` |
| DB 表 | `marketing_touch_task` + `marketing_touch_log` | db-snapshot.sql:5347/5312 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | `@Transactional(rollbackFor = Exception.class)` on createTask/startTask | `MarketingAutomationService.java` |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:5347/5312 |
| Business Status | 完整（9 端点；POST `/page` + GET `/page` 双形式 — 同 F125 I058 模式）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）+ **I058**（双路由形式）|
| Evidence | `audit/evidence/F129-mkt-touch-task/` | 待建 |
| Issue | **I064** + **I058** | |

---

### F130 — MKT Effect / Controller / 效果快照 + 生成快照

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F130 | `feature-inventory.md` §1.5.3 row 17 |
| 前端路由 | `/mkt/effect` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/effect.vue` | `frontend/meiyetec-web/src/views/mkt/effect.vue` |
| UI 板块 | 效果快照展示 / 生成按钮 | `effect.vue` |
| Action | 查看效果 / 生成当日快照 | `effect.vue` |
| Handler | 待 grep 确认 | `effect.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | GET / POST | |
| Controller | `MktEffectController.snapshot()` + `snapshotSave()` | `controller/MktEffectController.java:64, 175` |
| Service | 直接注入 5 Mapper + JdbcTemplate | `controller/MktEffectController.java:35-56` |
| Mapper | `MktPromotionMapper` + `MktCouponMapper` + `MktJourneyInstanceMapper` + JdbcTemplate | `controller/MktEffectController.java:38-51` |
| DB 表 | `mkt_promotion` + `mkt_coupon` + `mkt_journey_instance` | db-snapshot.sql:6761/6384/6540 |
| 关联表 | `mkt_channel_config`（读）| |
| 读/写 | R（snapshot）| |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6761/6384/6540 |
| Business Status | 完整（聚合 4 表统计）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）+ **I059**（snapshotSave 仅 log.info 未写表 — 用户决策：不修，登记 Issue）|
| Evidence | `audit/evidence/F130-mkt-effect-snapshot/` | 待建 |
| Issue | **I064** + **I059** | |

---

### F131 — MKT Coupon / Controller / 批次分页 + 创建 + 发布 + 结束 + 发放

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F131 | `feature-inventory.md` §1.5.3 row 18 |
| 前端路由 | `/mkt/coupon` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/coupon.vue` | `frontend/meiyetec-web/src/views/mkt/coupon.vue` |
| UI 板块 | 批次列表 / 新建 / 发布 / 结束 / 发放 | `coupon.vue` |
| Action | 批次 CRUD + 发放 | `coupon.vue` |
| Handler | 待 grep 确认 | `coupon.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | GET / POST | |
| Controller | `MktCouponController.batchPage/createBatch/publish/endBatch/grantBatch` | `controller/MktCouponController.java:52-220` |
| Service | 直接注入 `MktCouponBatchMapper` + `MktCouponMapper` + JdbcTemplate | `controller/MktCouponController.java:28-48` |
| Mapper | `MktCouponBatchMapper` + `MktCouponMapper` | `mapper/MktCouponBatchMapper.java` |
| DB 表 | `mkt_coupon_batch` + `mkt_coupon` | db-snapshot.sql:6424/6384 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6424/6384 |
| Business Status | 完整（5 端点；grant 时 memberIds 缺则发放给全部 active 会员）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F131-mkt-coupon-batch/` | 待建 |
| Issue | **I064** | |

---

### F132 — MKT Coupon / Controller / 过期扫描 + 券实例分页 + 导出

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F132 | `feature-inventory.md` §1.5.3 row 19 |
| 前端路由 | `/mkt/coupon` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/coupon.vue` | `frontend/meiyetec-web/src/views/mkt/coupon.vue` |
| UI 板块 | 过期扫描 / 券实例列表 / 导出 | `coupon.vue` |
| Action | 过期扫描 / 券实例查询 / 导出 | `coupon.vue` |
| Handler | 待 grep 确认 | `coupon.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | GET / POST | |
| Controller | `MktCouponController.expireSweep/couponPage/export` | `controller/MktCouponController.java:230-298` |
| Service | 直接 JdbcTemplate | `controller/MktCouponController.java` |
| Mapper | `MktCouponMapper` + `MktCouponBatchMapper` | `mapper/MktCouponMapper.java` |
| DB 表 | `mkt_coupon` + `mkt_coupon_batch` | db-snapshot.sql:6384/6424 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | — | |
| 权限 | ⚠ 未加 ADMIN 守卫（I060）| `MktCouponController.java:230` |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6384/6424 |
| Business Status | 完整（expire-sweep 直接 `UPDATE ... status = 3`）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **CRITICAL-P1** | **I060**（catch 吞异常 + 缺 audit + 缺 ADMIN 守卫）+ **I064**（无 api/mkt.ts）；用户决策：不加 try/catch，登记 Issue，Post-Audit |
| Evidence | `audit/evidence/F132-mkt-coupon-expiresweep/` | 待建 |
| Issue | **I060** + **I064** | |

---

### F133 — MKT Promotion / Controller / 分页 + 列表 + 创建 + 更新 + 删除 + 导出

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F133 | `feature-inventory.md` §1.5.3 row 20 |
| 前端路由 | `/mkt/promotion` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/promotion.vue` | `frontend/meiyetec-web/src/views/mkt/promotion.vue` |
| UI 板块 | 促销活动列表 / 新建 / 编辑 / 删除 / 导出 | `promotion.vue` |
| Action | 促销 CRUD + 导出 | `promotion.vue` |
| Handler | 待 grep 确认 | `promotion.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | GET / POST / PUT / DELETE | |
| Controller | `MktPromotionController` 7 端点 | `controller/MktPromotionController.java:47-142` |
| Service | 直接注入 `MktPromotionMapper` | `controller/MktPromotionController.java:32-38` |
| Mapper | `MktPromotionMapper` | `mapper/MktPromotionMapper.java` |
| DB 表 | `mkt_promotion` | db-snapshot.sql:6761 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6761 |
| Business Status | 完整（7 端点；`@GetMapping({"/", ""})` 双路径；导出 CSV 带 UTF-8 BOM）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F133-mkt-promotion/` | 待建 |
| Issue | **I064** | |

---

### F134 — MKT Points / Controller / 积分规则列表 + 创建 + 更新 + 手动发分

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F134 | `feature-inventory.md` §1.5.3 row 21 |
| 前端路由 | `/mkt/points` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `views/mkt/points.vue` | `frontend/meiyetec-web/src/views/mkt/points.vue` |
| UI 板块 | 积分规则列表 / 新建 / 编辑 / 手动发分 | `points.vue` |
| Action | 积分规则 CRUD + 手动发分 | `points.vue` |
| Handler | 待 grep 确认 | `points.vue` |
| API | ⚠ `api/mkt.ts` **不存在**（I064）| |
| Method | GET / POST / PUT | |
| Controller | `MktPointsController` 4 端点 | `controller/MktPointsController.java:44-99` |
| Service | 直接注入 `MktPointsRuleMapper` | `controller/MktPointsController.java:27-35` |
| Mapper | `MktPointsRuleMapper` + `MktPointsLogMapper` | `mapper/MktPointsRuleMapper.java` |
| DB 表 | `mkt_points_rule` + `mkt_points_log` | db-snapshot.sql:6699/6670 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6699/6670 |
| Business Status | 完整（4 端点；award 通过 `PosController.checkout()` → `MktPointsRule.pointsToCash` 计算，100:1 兑换比）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I064**（无 api/mkt.ts）|
| Evidence | `audit/evidence/F134-mkt-points/` | 待建 |
| Issue | **I064** | |

---

### F135 — ChannelDispatcher / 渠道分发（按 channelType 路由）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F135 | `feature-inventory.md` §1.5.3 row 22 |
| 前端路由 | — | （无 UI；由 F118 + F127 nextNode 调用）|
| 页面 | — | |
| UI 板块 | — | |
| Action | 按 channelType 路由到对应 sender | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `ChannelDispatcher.getSender(channelType)` | `service/channel/ChannelDispatcher.java:34-39` |
| Mapper | — | |
| DB 表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | N/A | |
| Business Status | 完整（`List<ChannelSender>` 注入；按 `s.channelType().equals(channelType)` 过滤）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | N/A | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **CRITICAL-P2** | **I061**（Router fallback `orElse(null)` NPE 风险 — 用户决策：不改 Router，登记 Issue，Post-Audit）|
| Evidence | `audit/evidence/F135-channel-dispatcher-routing/` | 待建 |
| Issue | **I061** | |

---

### F136 — ChannelDispatcher / 限流检查（每分钟 + 每日）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F136 | `feature-inventory.md` §1.5.3 row 23 |
| 前端路由 | — | （无 UI；由 MarketingTouchTask 调用）|
| 页面 | — | |
| UI 板块 | — | |
| Action | 双重限流检查（分钟 + 日）| |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `ChannelDispatcher.rateLimitCheck(tenantId, brandId, channelType)` | `service/channel/ChannelDispatcher.java:70-90` |
| Mapper | `MktChannelConfigMapper.selectOne` + JdbcTemplate 直查 `marketing_touch_log` | `service/channel/ChannelDispatcher.java:75-89` |
| DB 表 | `mkt_channel_config`（读） + `marketing_touch_log`（读）| db-snapshot.sql:6313/5312 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | `deleted = 0` | `ChannelDispatcher.java:62` |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ 存在 | db-snapshot.sql:6313/5312 |
| Business Status | 完整（双重检查 rateLimitPerMin + rateLimitPerDay；JdbcTemplate 聚合查询）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **PARTIAL-NON-BLOCKER** | |
| Evidence | `audit/evidence/F136-channel-dispatcher-ratelimit/` | 待建 |
| Issue | — | |

---

### F137 — ChannelSender × 5 / SMS / EMAIL / MINI_PROGRAM / IN_APP / WECHAT_OA

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F137 | `feature-inventory.md` §1.5.3 row 24 |
| 前端路由 | — | （无 UI；由 F135 ChannelDispatcher 调用）|
| 页面 | — | |
| UI 板块 | — | |
| Action | 5 渠道发送 | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | 5 个 `@Component` sender | `service/channel/{Sms,Email,MiniProgram,InApp,WechatOfficialAccount}Sender.java` |
| Mapper | — | |
| DB 表 | — | |
| 关联表 | `mkt_channel_config`（读 provider/config）| db-snapshot.sql:6313 |
| 读/写 | W（发送）/ R（读配置）| |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED（D10 铁律：WeChat/SMTP 凭据缺失）| |
| DB Status | ✅ `mkt_channel_config` 存在 | db-snapshot.sql:6313 |
| Business Status | 完整（5 sender 共用 EXT-19 链路；MKT 不修改 sender 实现；⚠ WECHAT_OA vs WECHAT_OFFICIAL_ACCOUNT 别名 I039 同模式）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **UNVERIFIED** | 凭据缺失（D10 铁律）|
| Evidence | `audit/evidence/F137-channel-senders/` | 待建 |
| Issue | — | |

---

### F138 — MarketingAutomationService / 业务入口聚合（T-D21~D24）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F138 | `feature-inventory.md` §1.5.3 row 25 |
| 前端路由 | — | （无 UI；被 9 个 Mkt*Controller 调用）|
| 页面 | — | |
| UI 板块 | — | |
| Action | 业务入口聚合；委派 ChannelDispatcher + 5 Sender + 9 Mapper + JdbcTemplate | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `MarketingAutomationService`（~25 方法）| `service/MarketingAutomationService.java:35-763` |
| Mapper | 9 个 Mkt*Mapper + `MarketingTouch*Mapper` | `mapper/Mkt*.java` + `mapper/MarketingTouch*.java` |
| DB 表 | `mkt_channel_config` + `mkt_channel_template` + `mkt_journey*` + `marketing_touch_*` + `mkt_brand` + `mkt_segment` + `mkt_invitation_rule` + `mkt_invitation` + `mkt_order_discount` + `mkt_dynamic_pricing_rule` + `mkt_pricing_log` | db-snapshot.sql 多表 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | `@Transactional(rollbackFor = Exception.class)` on createTask/startTask/enterJourney/nextNode（good pattern）| `MarketingAutomationService.java:521, 560, 417` |
| 权限 | 普通租户（强制 tenantId）| |
| 软删除 | `deleted = 0` 过滤 | 各方法内 |
| 审计字段 | `create_time / update_time` | 各 insert/update 方法 |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ⚠ **4 表 MISSING** | `mkt_invitation` / `mkt_invitation_rule` / `mkt_dispatch` / `mkt_dispatch_target`（db-snapshot 0 匹配 — I063）|
| Business Status | 完整（委派 9 mapper + JdbcTemplate + ChannelDispatcher）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **MISSING** | **I063**（4 表 MISSING：mkt_invitation/mkt_invitation_rule/mkt_dispatch/mkt_dispatch_target — 用户决策：本批次仅落档，Post-Audit 统一跑迁移）+ **I062**（rateLimitPerDay setter 空语句）|
| Evidence | `audit/evidence/F138-marketing-automation-service/` | 待建 |
| Issue | **I063** + **I062** | |

---

### F139 — MKT Refactor / 中间态 / 旧 `MarketActivity*` 5 个类残留

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F139 | `feature-inventory.md` §1.5.3 row 26 |
| 前端路由 | — | （无 UI；旧路由仍可达）|
| 页面 | — | |
| UI 板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `MarketActivityController`（旧，仍注册）| `controller/MarketActivityController.java` |
| Service | `MarketActivityService`（旧 interface）+ `MarketActivityServiceImpl`（旧实现）| `service/MarketActivityService.java` + `service/impl/MarketActivityServiceImpl.java` |
| Mapper | `MarketActivityMapper` | `mapper/MarketActivityMapper.java` |
| DB 表 | `market_activity` | db-snapshot.sql:5177（**未 DROP**）|
| 关联表 | — | |
| 读/写 | ⚠ **RW（实际调用）**| `BusinessPolicyDispatchServiceImpl.applyActivity():264-278` 仍调用 |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | ⚠ **ACTIVE（实际被调用）**| `BusinessPolicyDispatchServiceImpl.java:264-278` + `MktEffectController.snapshot():64` |
| DB Status | ✅ 存在（未 DROP）| db-snapshot.sql:5177 |
| Business Status | ⚠ **SoT vs 代码 CONFLICT**：`docs/ROLLBACK-6.2-mkt_ddl.md` §1 声称"Java 侧已无读写"，但 `BusinessPolicyDispatchServiceImpl.applyActivity()` 仍调用 `marketActivityMapper.selectById` + `insert(clone)` | `service/impl/BusinessPolicyDispatchServiceImpl.java:264, 277` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **CONFLICT** + **PARTIAL-BLOCKER** | **I055**（SoT CONFLICT — 用户决策：不改 SoT，中立标）+ **I056**（重构中间态，I067 同模式）+ **I067**（重构中间态 5 旧类 + 9 新 Controller + 表未 DROP）；用户决策 Q1：不改 SoT/不改重构 |
| Evidence | `audit/evidence/F139-market-activity-refactor/` | 待建 |
| Issue | **I055** + **I056** + **I067** | |

---

### F140 — MKT Refactor / Diff Audit / Phase 3.5 Diff Audit 8 项

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F140 | `feature-inventory.md` §1.5.3 row 27 |
| 前端路由 | — | （审计任务，非功能 Feature）|
| 页面 | — | |
| UI 板块 | — | |
| Action | Phase 3.5 Diff Audit 8 项审计 | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | — | |
| Mapper | — | |
| DB 表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0（报告产出）| |
| Runtime Status | — | |
| DB Status | — | |
| Business Status | 完整（8 项审计清单：旧类调用链 / 路由冲突 / 前端引用 / SoT 对称 / 归档表名一致性 / 旧 Controller 残留）| `audit/diff-audit/mkt-refactor.md` |
| E2E Status | N/A | |
| Verified Commit | — | |
| DB Snapshot | — | |
| Last Verified At | 2026-09-15 (UTC+8) | Phase 2 静态追溯 |
| **Final Result** | **Static-Only OK** | Phase 3.5 Diff Audit 8 项全部完成；产出 `audit/diff-audit/mkt-refactor.md` |
| Evidence | `audit/diff-audit/mkt-refactor.md` | ✅ 已存在 |
| Issue | — | |

---

## Batch 1.5 统计

| 分类 | 数量 | Feature IDs |
|---|---|---|
| MKT Channel（5 Controller）| 5 | F114-F118 |
| MKT Conversion（5 端点）| 5 | F119-F123 |
| MKT Journey（4 Feature）| 4 | F124-F127 |
| MKT Template（1 Feature）| 1 | F128 |
| MKT TouchTask（1 Feature）| 1 | F129 |
| MKT Effect（1 Feature）| 1 | F130 |
| MKT Coupon（2 Feature）| 2 | F131-F132 |
| MKT Promotion（1 Feature）| 1 | F133 |
| MKT Points（1 Feature）| 1 | F134 |
| ChannelDispatcher + 5 Sender | 3 | F135-F137 |
| MarketingAutomationService | 1 | F138 |
| MKT Refactor 中间态 + Diff Audit | 2 | F139-F140 |
| **合计** | **27** | **F114-F140** |

---

## 最终状态分布

| Result | 数量 | 占比 | Feature IDs |
|---|---|---|---|
| **CRITICAL-P1** | 1 | 3.7% | F132 |
| **CRITICAL-P2** | 1 | 3.7% | F135 |
| **PARTIAL-BLOCKER + CONFLICT** | 1 | 3.7% | F139 |
| **MISSING** | 1 | 3.7% | F138 |
| **PARTIAL-NON-BLOCKER** | 21 | 77.8% | F114-F117, F119-F131, F133-F134, F136 |
| **UNVERIFIED** | 1 | 3.7% | F137 |
| **Static-Only OK** | 1 | 3.7% | F140 |
| **合计** | **27** | **100%** | **F114-F140** |

---

## 与 I055-I068 交叉引用

| Issue | 严重度 | 影响 Feature | Final Result |
|---|---|---|---|
| **I055** | 🟠 CRITICAL-P2 | F139 | **CONFLICT**（SoT vs 代码 CONFLICT — `ROLLBACK-6.2 §1` 声称"Java 侧已无读写"，但 `BusinessPolicyDispatchServiceImpl.applyActivity()` 仍调用 `marketActivityMapper.selectById` + `insert`）|
| **I056** | 🟡 中 | F117 | **PARTIAL-NON-BLOCKER**（跨租户删除风险 — `deleteChannel` 未校验 tenantId）|
| **I057** | 🟡 中 | F122, F123 | **PARTIAL-NON-BLOCKER**（跨租户写风险 — `trackConversion/markRead` 未加租户隔离）|
| **I058** | 🟡 中 | F125, F129 | **PARTIAL-NON-BLOCKER**（双路由形式语义模糊）|
| **I059** | 🟡 中 | F130 | **PARTIAL-NON-BLOCKER**（snapshotSave 仅 log.info 未写表）|
| **I060** | 🔴 CRITICAL-P1 | F132 | **CRITICAL-P1**（`expireSweep` 缺 try/catch + 缺 audit log + 缺 ADMIN 守卫；用户决策：不加 try/catch，Post-Audit）|
| **I061** | 🟠 CRITICAL-P2 | F135 | **CRITICAL-P2**（`ChannelDispatcher.getSender` miss → `orElse(null)` NPE；与 Batch 1.1 I012 同模式；用户决策：不改 Router，Post-Audit）|
| **I062** | 🟢 低 | F115, F138 | **PARTIAL-NON-BLOCKER**（rateLimitPerDay setter 空语句）+ **MISSING**（F138）|
| **I063** | 🟠 CRITICAL-P2 | F138 + BusinessPolicyDispatchController 相关链路 | **MISSING**（mkt_invitation/mkt_invitation_rule/mkt_dispatch/mkt_dispatch_target 4 表 db-snapshot 0 匹配；用户决策：本批次仅落档，Post-Audit 统一跑迁移）|
| **I064** | 🟡 中 | F114-F134 全部前端相关 Feature | **PARTIAL-NON-BLOCKER**（api/mkt.ts 不存在；前端无法调用新 Controller；用户决策：不写 api/mkt.ts，Post-Audit）|
| **I065** | ✅ 已解除 | — | **风险解除**：dashboard.vue 未调用 `/mkt/channel/*` 或 ChannelDispatcher.healthCheckAll |
| **I066** | ✅ 维持 | F118 | I054 NEW 推论反向：`mkt_channel_config` 表存在于 db-snapshot.sql:6313 ✅ |
| **I067** | 🟡 中 | F139 | **PARTIAL-BLOCKER + CONFLICT**（重构中间态：5 旧类 + 9 新 Controller + market_activity 表未 DROP；与 I055/I056 联动）|
| **I068** | 🟡 中 | F139 | 待 Phase 5 `DESCRIBE market_activity` 验证列对齐（不阻塞 Phase 2）|

---

## Issue 新增汇总（Batch 1.5）

| Issue | 用户决策 | Fix Phase | 影响 Feature 数 |
|---|---|---|---|
| **I055** | 不改 SoT / 不重构 → CONFLICT 中立标 | Post-Audit | 1（仅 F139 直接）|
| **I056** | 登记 Issue，Post-Audit | Post-Audit | 1（F117）|
| **I057** | 登记 Issue，Post-Audit | Post-Audit | 2（F122/F123）|
| **I058** | 登记 Issue，Post-Audit | Post-Audit | 2（F125/F129）|
| **I059** | 登记 Issue，Post-Audit | Post-Audit | 1（F130）|
| **I060** | 不加 try/catch → 登记 Issue，Post-Audit | Post-Audit | 1（F132）|
| **I061** | 不改 Router → 登记 Issue，Post-Audit | Post-Audit | 1（F135）|
| **I062** | 登记 Issue，Post-Audit | Post-Audit | 2（F115/F138）|
| **I063** | 本批次仅落档 → Post-Audit DBA 统一写 migrate | Post-Audit | 1（F138）+ BusinessPolicyDispatchController 间接 |
| **I064** | 不写 api/mkt.ts → 登记 Issue，Post-Audit | Post-Audit | 21（F114-F134 全部前端相关）|
| **I065** | ✅ 风险已解除 | — | 0 |
| **I066** | I054 NEW 维持（表已存在）| — | 1（F118）|
| **I067** | 重构中间态维持，Post-Audit 决策 | Post-Audit | 1（F139）|
| **I068** | 待 Phase 5 验证列对齐 | Phase 5 | 1（F139）|

**Phase 2 完成。审计阶段不修业务代码（规则 6 + 用户决策）。**