# Feature Inventory（汇总）

> **单一汇总文件，按 Batch 分段**（用户决策 7）
> 创建时间：2026-09-12 21:23 (UTC+8)
> 参考规则：`@feature-id.mdc` + `@project-audit.mdc`
> 整体计划：`audit/batches.md`（**待补 — Phase 0 后续动作**）
> Commit baseline：`3f9b9d3` (HEAD, 2026-09-12 21:29 +0800)
> DB snapshot：`audit/baseline/db-snapshot.sql`（417 KB / 270 表 / schema-only）
> env：`audit/baseline/env.md`（9,951 B）

---

## 全局约定

- **Feature ID**：`F` + 三位数字，从 `F001` 起顺序编号（跨 Batch 连续）
- **证据目录**：`audit/evidence/FXXX-<slug>/`（编号固定，缺一张即 UNVERIFIED）
- **L 等级标注**：每个 Feature 标注当前静态可达性（L0=代码存在 / L1=编译过 / L3=调用图通；L4-L5b 须运行证据 — Phase 2+）
- **Result 子标签**：9 种之一，缺运行证据只标 UNVERIFIED
- **Phase 1 边界**：本文件**不**输出 Evidence/Result/SoT 比对；只列 Feature 清单 + 触点（文件:行号 / 类名.方法 / API / 表名）+ 已知 Issue

---

## Batch 1.1 — EXT-24 CRM 集成（v3 第一批）

**范围来源**：`audit/baseline/scope-freeze.md` §2.1 + `audit/baseline/preflight.md` §8.1
**EXT-* Operational Notes**：`.cursor/rules/ext-24-crm-integration.mdc`（**SoT**，但与代码多处矛盾 — 见 §Issue 清单）

### 1.1.1 范围涉及文件（已静态扫描）

| 类别 | 文件 | 行数 | 备注 |
|---|---|---|---|
| Adapter 接口 | `backend/src/main/java/com/meiyetec/crm/CrmAdapter.java` | 145 | 5 方法 + 4 DTO（AuthResult / SyncCursor / ExternalContact / PushResult / HealthStatus） |
| Router | `backend/src/main/java/com/meiyetec/crm/CrmAdapterRouter.java` | 47 | platform → adapter；MOCK fallback |
| Scheduler | `backend/src/main/java/com/meiyetec/crm/CrmSyncScheduler.java` | 194 | `@Scheduled` + `runIncremental` + `loadKnownExternalIds` + `KNOWN_IDS_LIMIT=5000` |
| Adapter | `backend/src/main/java/com/meiyetec/crm/SalesforceCrmAdapter.java` | 219 | OAuth2 JWT + SOQL + parseObjectMappings |
| Adapter | `backend/src/main/java/com/meiyetec/crm/HubSpotCrmAdapter.java` | 219 | OAuth2 refresh + search API + batch/upsert by email |
| Adapter | `backend/src/main/java/com/meiyetec/crm/ZohoCrmAdapter.java` | 230 | OAuth2 refresh + COQL + upsert by Email + 域名推导 |
| Adapter | `backend/src/main/java/com/meiyetec/crm/MockCrmAdapter.java` | 109 | `@ConditionalOnMissingBean(name="salesforceCrmAdapter")` + 滚动缓冲 |
| Service / Controller | `backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java` | 156 | **`@RestController` 命名误导**（不是 Service） |
| Entity | `backend/src/main/java/com/meiyetec/entity/CrmIntegration.java` | 105 | privateKey `JsonProperty.WRITE_ONLY` |
| Entity | `backend/src/main/java/com/meiyetec/entity/CrmContactMap.java` | 36 | uk `uk_platform_external (platform, external_id)` |
| Entity | `backend/src/main/java/com/meiyetec/entity/CrmSyncLog.java` | 39 | syncType/recordsTotal/recordsFiltered/syncToken |
| Frontend 页面 | `frontend/meiyetec-web/src/views/crm-integration/index.vue` | 70 | 集成配置 tab + 同步日志 tab + 新建对话框 |
| Frontend API | `frontend/meiyetec-web/src/api/crm-integration.ts` | 17 | `syncNow(id)` **未传 `?force=1`** |
| DDL | `backend/src/main/resources/db/sql/migrate_ext_15_crm_real.sql` | 51 | EXT-15 加 9 列 + DROP/CREATE crm_contact_map + ALTER crm_sync_log 加 3 列 |
| DDL（参考） | `backend/src/main/resources/db/sql/create_missing_tables_b4b5.sql` | 230+ | **不含 `crm_contact_map`** |
| DDL（不在范围） | `backend/src/main/resources/db/sql/crm_follow_up_plan.sql` | — | 属 CrmFollowUpPlan 模块（**与 EXT-24 无关**，用户 touchpoint 列表误标） |

### 1.1.2 Feature 清单（30 项）

> 列：Feature ID / Module / 功能点 / Priority / 需求来源 / 前端入口 / 后端入口 / DB表 / 静态可达性 / 备注

| Feature ID | Module | 功能点 | Priority | 需求来源 | 前端入口 | 后端入口 | DB表 | 静态可达性 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| **F001** | CRM 集成 / 端点 | 创建集成 | P3 | EXT-24 §3 | `crm-integration/index.vue` 新建对话框 (addDialog) | `POST /crm-integration` → `CrmIntegrationService.create` (`service/CrmIntegrationService.java:35-44`) | `crm_integration` | L0 | 强制 `tenantId=TenantContextHolder.get().getTenantId()`；`platform` 必填；默认 `status=ACTIVE` |
| **F002** | CRM 集成 / 端点 | 集成列表 | P3 | EXT-24 §3 | `crm-integration/index.vue` "集成配置" tab table | `GET /crm-integration/list?platform=&status=` → `CrmIntegrationService.list` (`service/CrmIntegrationService.java:46-56`) | `crm_integration` | L0 | 强制按 `tenantId` 过滤；按 `id` 倒序 |
| **F003** | CRM 集成 / 端点 | 更新集成 | P3 | EXT-24 §3 | `crm-integration/index.vue` **未提供更新入口**（无 edit 按钮） | `POST /crm-integration/{id}` → `CrmIntegrationService.update` (`service/CrmIntegrationService.java:58-66`) | `crm_integration` | L0 | 后端可调；前端缺 UI — 见 ISSUE-I005 |
| **F004** | CRM 集成 / 端点 | 切换状态 | P3 | EXT-24 §3 | `crm-integration/index.vue` "状态" 列 el-switch | `POST /crm-integration/{id}/toggle` → `CrmIntegrationService.toggle` (`service/CrmIntegrationService.java:68-77`) | `crm_integration` | L0 | body={status}；不校验 status 合法性；**前端用 ACTIVE/PAUSED，后端 enum 是 ACTIVE/DISABLED/ERROR — 见 ISSUE-I005** |
| **F005** | CRM 集成 / 端点 | 手动触发同步 | P3 | **EXT-24 §0.1 ⭐ 行为变更** | `crm-integration/index.vue` "操作" 列 "同步" 按钮 | `POST /crm-integration/{id}/sync?force=0|1` → `CrmIntegrationService.syncNow` (`service/CrmIntegrationService.java:79-128`) | `crm_integration` + `crm_sync_log` | L0 | 直接调 `CrmSyncScheduler.runIncremental(i, "MANUAL")` 返回真实日志；`force=null` 尊重窗口；`force=1` 强制执行 |
| **F006** | CRM 集成 / 端点 | 同步日志 | P3 | EXT-24 §3 | `crm-integration/index.vue` "同步日志" tab table | `GET /crm-integration/sync-log/list?integrationId=` → `CrmIntegrationService.listSyncLogs` (`service/CrmIntegrationService.java:130-141`) | `crm_sync_log` | L0 | **契约漂移**：`integrationId` 入参实际用作"反查 integration.platform → 过滤 crm_sync_log.platform"（不是 integrationId） — 见 ISSUE-I003 |
| **F007** | CRM 集成 / 端点 | Dashboard | P3 | EXT-24 §3 | **前端无 dashboard tab**（独立接口未消费） | `GET /crm-integration/dashboard` → `CrmIntegrationService.dashboard` (`service/CrmIntegrationService.java:143-156`) | `crm_integration` + `crm_sync_log` | L0 | 返回 `{totalIntegrations, totalSyncLogs}`；try/catch 吞所有异常 |
| **F008** | CRM 集成 / 行为 | 手动 sync 窗口跳过 | P3 | EXT-24 §0.1 | (F005 同步按钮链路) | `CrmIntegrationService.syncNow` (`service/CrmIntegrationService.java:90-101`) → `crmSyncScheduler.shouldRunForManualTrigger(i)` (`crm/CrmSyncScheduler.java:79-82`) | `crm_integration` | L0 | 返回 `Result.success + skipped=true + reason="not in cron window, use ?force=1 to override" + lastIncrementalSyncAt + syncWindowMinutes`；HTTP 200 |
| **F009** | CRM 集成 / 行为 | 手动 sync 跨租户拒绝 | P3 | EXT-24 §0.7 | (F005 同步按钮链路) | `CrmIntegrationService.syncNow` (`service/CrmIntegrationService.java:84-89`) | `crm_integration` | L0 | `dataScope != "ALL"` ∧ `integration.tenantId != currentTenantId` → `BizCode.FORBIDDEN`；HTTP 非 200 |
| **F010** | CRM 集成 / 排程 | 自动 tick 扫描 | P3 | EXT-24 §0.4 | (无前端入口) | `@Scheduled(fixedDelayString="${crm.sync.tick-interval-ms:300000}", initialDelay=60_000L)` → `CrmSyncScheduler.tick` (`crm/CrmSyncScheduler.java:54-77`) | `crm_integration` | L0 | 每 5min 扫 `status=ACTIVE ∧ syncEnabled=1`；try/catch 不阻塞其他 integration |
| **F011** | CRM 集成 / 排程 | shouldRun 窗口判断 | P3 | EXT-24 §0.4 | (无前端入口) | `CrmSyncScheduler.shouldRun` (`crm/CrmSyncScheduler.java:80-86`) | `crm_integration` | L0 | `lastIncrementalSyncAt + syncWindowMinutes(默认 30) ≤ now`；`lastIncrementalSyncAt=null` → 立即跑 |
| **F012** | CRM 集成 / 排程 | known-ids 差集加载 | P3 | EXT-24 §0.3 | (无前端入口) | `CrmSyncScheduler.loadKnownExternalIds` (`crm/CrmSyncScheduler.java:174-194`) + `KNOWN_IDS_LIMIT=5000` (`crm/CrmSyncScheduler.java:166`) | `crm_contact_map` | L0 | 硬截断 `LIMIT 5000`（直接 SQL 拼接，非分页）；按 `(tenantId, platform)` 过滤 |
| **F013** | CRM 集成 / Adapter | CrmAdapter 接口契约 | P3 | EXT-24 §0 + scope-freeze §2.1 | (无前端入口) | `CrmAdapter` 接口 (`crm/CrmAdapter.java`) — 5 方法 + 5 DTO | — | L0 | `platformCode / authenticate / fetchContactsIncremental / upsertContact / healthCheck` |
| **F014** | CRM 集成 / Adapter | Salesforce 适配器 | P3 | EXT-24 §0.2 + §0.6 | (无前端入口) | `SalesforceCrmAdapter` (`crm/SalesforceCrmAdapter.java`) | — | L0 | OAuth2 JWT (RS256) + `parseObjectMappings` (`:111-141`) + `buildSoqlSelectClause` (`:146-156`) + `authenticate` (`:55-99`) + `healthCheck` (`:200-216`)；**未把 `knownExternalIds` 落到 SOQL**（仅 log）— 与 EXT-24 §0.3 一致 |
| **F015** | CRM 集成 / Adapter | Mock 适配器 | P3 | EXT-24 §0.6 | (无前端入口) | `MockCrmAdapter` (`crm/MockCrmAdapter.java`) `@ConditionalOnMissingBean(name="salesforceCrmAdapter")` (`:21`) | — | L0 | 3 条固定样本 (`:26-30`) + 滚动缓冲 `Deque<ExternalContact>` (`:37-39`)；`known.id` 内存去重 (`:65`)；`firstSync` 全量；max 50 条滚动缓冲 — **⚠ ISSUE-I012（Open / PARTIAL-NON-BLOCKER）**：主 Result = PARTIAL-NON-BLOCKER；不含 CONFLICT 子标签（I012 是代码 Bug，非 SoT vs 代码冲突）|
| **F016** | CRM 集成 / Adapter | HubSpot 适配器 | P3 | **与 EXT-24 §0.6 + §5 矛盾（文档标"未实现"）** | (无前端入口) | `HubSpotCrmAdapter` (`crm/HubSpotCrmAdapter.java`) | — | L0 | OAuth2 refresh (`:107-156`) + PAT fallback (`:148-156`)；search API (`:170-218`) + batch/upsert by email (`:230-280`)；healthCheck (`:289-322`) — **⚠ ISSUE-I001：文档必须更新** |
| **F017** | CRM 集成 / Adapter | Zoho 适配器 | P3 | **与 EXT-24 §0.6 + §5 矛盾（文档标"未实现"）** | (无前端入口) | `ZohoCrmAdapter` (`crm/ZohoCrmAdapter.java`) | — | L0 | OAuth2 refresh (`:120-170`) + COQL (`:185-237`) + upsert by Email (`:250-300`) + accounts 域名推导 `deriveAccountsBase` (`:172-180`) — **⚠ ISSUE-I001：文档必须更新** |
| **F018** | CRM 集成 / Router | Platform 分发 + Mock fallback | P3 | EXT-24 §0.6 + §5 | (无前端入口) | `CrmAdapterRouter` (`crm/CrmAdapterRouter.java`) | — | L0 | `getAdapter(platform)` (`:21-29`)：`platform` 上转大写查 Map；miss → MOCK fallback；null → `getAdapter("MOCK")`；`hasAdapter` + `listPlatforms` 方法存在 — **⚠ ISSUE-I001：与 EXT-24 §5 "NPE" 矛盾** |
| **F019** | CRM 集成 / Entity | CrmIntegration 配置实体 | P3 | EXT-24 §0.6 + scope-freeze §2.1 | (前端通过 API 间接) | `CrmIntegration` (`entity/CrmIntegration.java`) | `crm_integration` | L0 | `privateKey` `JsonProperty.WRITE_ONLY`（`:69`）；`objectMappings`/`syncToken`/`syncSchedule`/`syncWindowMinutes`/`lastIncrementalSyncAt`/`username`/`loginUrl`/`clientId`/`clientSecret`/`accessToken`/`refreshToken`/`tokenExpireAt` 字段 |
| **F020** | CRM 集成 / Entity | CrmContactMap 映射实体 | P3 | EXT-24 §0.5 | (无前端入口) | `CrmContactMap` (`entity/CrmContactMap.java`) | `crm_contact_map` | L0 | 唯一键 `uk_platform_external (platform, external_id)`；`conflictFlag 0/1`；`localContactId` 可空（INBOUND 时还没反向绑定） |
| **F021** | CRM 集成 / Entity | CrmSyncLog 日志实体 | P3 | EXT-24 §0.4 | (前端通过 F006 间接) | `CrmSyncLog` (`entity/CrmSyncLog.java`) | `crm_sync_log` | L0 | `syncType` INCREMENTAL/FULL/MANUAL；`direction` PUSH/PULL/BIDIRECTIONAL；`status` RUNNING/SUCCESS/FAILED；`recordsTotal`/`recordsFiltered`/`syncToken`（EXT-15 新增） |
| **F022** | CRM 集成 / DB | 三表存在性 + EXT-15 ALTER | P3 | EXT-24 §0.6 + scope-freeze §九.1 | (无前端入口) | DDL `migrate_ext_15_crm_real.sql` (`db/sql/migrate_ext_15_crm_real.sql`) | `crm_integration` / `crm_sync_log` | L0（DDL 存在） | `b4b5 SQL` 含 `CREATE TABLE IF NOT EXISTS`（兜底）；EXT-15 加 9 列（`sync_token`/`sync_schedule`/`sync_enabled`/`last_incremental_sync_at`/`private_key`/`username`/`login_url`/`object_mappings`/`sync_window_minutes`）+ 2 索引（`idx_sync_enabled`/`idx_platform_status`）；`crm_sync_log` 加 3 列（`sync_token`/`records_total`/`records_filtered`） |
| **F023** | CRM 集成 / DB | crm_contact_map 表存在性 | P3 | EXT-24 §0.5 + scope-freeze §九.1 | (无前端入口) | DDL `migrate_ext_15_crm_real.sql` (`:17-37`)：`DROP TABLE IF EXISTS crm_contact_map; CREATE TABLE crm_contact_map (...)` | `crm_contact_map` | L0（DDL 存在） / **db-snapshot 未验证** | ⚠ **ISSUE-I002**：`b4b5 SQL` **不含** `crm_contact_map`；`migrate_ext_15_crm_real.sql` 必须执行；preflight §8.1 标 MISSING — Phase 2 跑 `db-snapshot.sql` 实际查询验证 |
| **F024** | CRM 集成 / 前端 | 集成列表 UI | P3 | scope-freeze §2.1 | `crm-integration/index.vue` "集成配置" tab table | (无新后端) GET `/crm-integration/list` | — | L0 | el-table 显示 `platform/shopName/syncDirection/lastSyncAt/status`；状态列 el-switch 触发 toggle；操作列 "同步" 按钮触发 F005 |
| **F025** | CRM 集成 / 前端 | 新建集成表单 | P3 | scope-freeze §2.1 | `crm-integration/index.vue` "新建集成" 对话框 | (无新后端) POST `/crm-integration` | — | L0 | 表单字段：`platform` / `shopName` / `syncDirection` / `autoSync`；⚠ **F028 输入缺失**（缺 clientId/clientSecret/privateKey/username/loginUrl/objectMappings 等） |
| **F026** | CRM 集成 / 前端 | 同步日志 tab | P3 | scope-freeze §2.1 | `crm-integration/index.vue` "同步日志" tab table | (无新后端) GET `/crm-integration/sync-log/list` | — | L0 | 显示 `id/integrationId/syncType/status/syncAt/remark`；⚠ **ISSUE-I003 UI 契约漂移**：`integrationId` 列在前端但 `crm_sync_log` 无此字段 |
| **F027** | CRM 集成 / 前端 | 同步 API 未传 ?force=1 | P3 | **EXT-24 §0.1 ⚠ PARTIAL-NON-BLOCKER** | `crm-integration/api` → `syncNow(id)` | (无新后端) POST `/crm-integration/{id}/sync` | — | L0 | 当前实现 `crmIntegrationApi.syncNow(id)` (`api/crm-integration.ts:14`) POST 不带 query params；窗口外返回 `skipped=true` 但 UI 无提示；按钮无 loading / 无二次确认 |
| **F028** | CRM 集成 / 前端 | objectMappings 输入入口缺失 | P3 | **EXT-24 §5 ⚠ PARTIAL-NON-BLOCKER** | `crm-integration/index.vue` 新建对话框 | (无新后端) POST `/crm-integration`（后端 `CrmIntegrationService.create` 接受 `objectMappings` 字段） | — | L0 | 前端表单**无** `objectMappings` 字段；启用 Salesforce 自定义字段映射（`Email__c` 等）需走 SQL 直写 `crm_integration.object_mappings` |
| **F029** | CRM 集成 / 状态机 | crm_integration.status 状态机 | P3 | EXT-24 §4 + scope-freeze §2.1 | `crm-integration/index.vue` 状态列 el-switch | `CrmSyncScheduler.runIncremental` (`crm/CrmSyncScheduler.java:104-115, 132-135`) + `CrmIntegrationService.toggle` (`service/CrmIntegrationService.java:68-77`) | `crm_integration` | L0 | 同步成功 → `status=ACTIVE` + `errorMessage=null`；同步失败 → `status=ERROR` + `errorMessage=ex.getMessage()`（**不**更新 `lastIncrementalSyncAt`）；手动 toggle 写入 `body.status`（不校验 enum） |
| **F030** | CRM 集成 / 排程配置 | syncWindowMinutes + syncSchedule | P3 | EXT-24 §0.4 + §0.6 | (无前端入口) | `CrmIntegration` 字段 (`entity/CrmIntegration.java:73, 76`) + `CrmSyncScheduler.shouldRun` (`crm/CrmSyncScheduler.java:81`) | `crm_integration` | L0 | `syncSchedule` 默认 `"0 */30 * * * *"`（每 30min）；`syncWindowMinutes` 默认 30；EXT-15 DDL 含默认值；前端**无**配置入口（依赖 SQL/后端） |

### 1.1.3 Feature 数量统计

| 分类 | 数量 | Feature IDs |
|---|---|---|
| REST 端点（CrmIntegrationService） | 7 | F001-F007 |
| 行为分支 / 排程 | 5 | F008-F012 |
| Adapter / Router | 6 | F013-F018 |
| Entity | 3 | F019-F021 |
| DB / DDL | 2 | F022-F023 |
| 前端 | 5 | F024-F028 |
| 状态机 / 配置 | 2 | F029-F030 |
| **合计** | **30** | F001-F030 |

### 1.1.4 Phase 1 Issue 清单（**不进 Phase 2 前必须决策**）

| Issue ID | 类别 | 描述 | 影响 Feature | 建议处理 |
|---|---|---|---|---|
| **ISSUE-I001** | 文档/事实不一致 | `ext-24-crm-integration.mdc` §0.6 标 HubSpot/Zoho Adapter "未实现"；§5 标 "router.getAdapter 返回 null → NPE" — **与代码不符**。HubSpotCrmAdapter 219 行 / ZohoCrmAdapter 230 行实际完整实现；CrmAdapterRouter.getAdapter 有 Mock fallback 不会 NPE。 | F016 / F017 / F018 | **必修**：更新 `.mdc` §0.6 + §5 + §6（或写新 `ext-24b-hubspot-zoho.mdc`） |
| **ISSUE-I002** | 表存在性待验证 | `crm_contact_map` 表 DDL 仅在 `migrate_ext_15_crm_real.sql`（DROP+CREATE）；`create_missing_tables_b4b5.sql` **不包含**；preflight §8.1 标 MISSING。需 Phase 2 实际查询 `db-snapshot.sql` 确认是否落地。 | F023 | Phase 2：grep `db-snapshot.sql` 找 `CREATE TABLE.*crm_contact_map`；若无 → 触发 migrate_ext_15 跑批 |
| **ISSUE-I003** | UI 契约漂移 | 前端同步日志 tab 显示 `integrationId` 列；`crm_sync_log` 表**无** `integrationId` 字段；后端 F006 用 `integrationId → 反查 CrmIntegration.platform → 过滤 crm_sync_log.platform`（不是 integrationId）。 | F006 / F026 | 决策：① 后端补 `crm_sync_log.integration_id` 列；② 前端改显示 `platform` 列；③ 接受"按 platform 聚合"语义并文档化 |
| **ISSUE-I004** | 命名/架构异味 | `CrmIntegrationService.java` 文件名是 `*Service`，实际是 `@RestController`，挂在 `service/` 包下（项目其他 Controller 在 `controller/` 包）。7 个 REST endpoint 全在这一个文件里。 | F001-F008 + F029 | 不阻塞 Phase 2；但应记入后续重构 backlog。**Phase 1 标注不动**。 |
| **ISSUE-I005** | 前端 toggle enum 不一致 | `crm-integration/index.vue` el-switch 用 `ACTIVE/PAUSED`；后端 enum 是 `ACTIVE/DISABLED/ERROR`；`toggle(id, {status: "PAUSED"})` 会写入未识别状态。 | F004 / F029 | 决策：① 前端改成 `ACTIVE/DISABLED`；② 后端 `toggle` 加 enum 校验 → 非法返回 400 |

### 1.1.5 与 EXT-* Operational Notes 的冲突点（SoT 比对）

> 按 `project-audit.mdc` 规则 11：审计结论与 EXT-* Operational Notes 冲突时，**以 EXT-* 为准并标 ISSUE**。
> 但本次审计的发现是 **EXT-* 文档本身与代码不一致**，因此需要在 Issue 清单（§1.1.4）中显式标记并提请更新 EXT-*.mdc。

| EXT-* 章节 | 文档声称 | 代码事实 | 处理 |
|---|---|---|---|
| §0.6 平台与凭据表 | `HUBSPOT` "未实现（前端可选会 500）" | `HubSpotCrmAdapter.java` 完整实现 219 行 | ISSUE-I001 |
| §0.6 平台与凭据表 | `ZOHO` "未实现（前端可选会 500）" | `ZohoCrmAdapter.java` 完整实现 230 行 | ISSUE-I001 |
| §5 已知问题 | `router.getAdapter 返回 null → NPE` | `CrmAdapterRouter.java:24-27` 实际 fallback 到 MOCK | ISSUE-I001 |
| §1 Touchpoints | 列了 `SalesforceCrmAdapter` / `MockCrmAdapter`（未列 HubSpot/Zoho） | HubSpot/Zoho 已实现但未在 EXT-*.mdc §1 注册 | ISSUE-I001（补 §1 引用） |
| §6 新增 adapter checklist | 描述 HubSpot 字段映射 `hs_object_id / firstname / lastname / email / phone / jobtitle / hs_lastmodifieddate` | 与 `HubSpotCrmAdapter.DEFAULT_FIELD_MAP` 一致 | OK（验证） |

### 1.1.6 未确认项（标记"未确认"，禁止猜测）

| # | 项 | 当前信息 | 待补来源 |
|---|---|---|---|
| 1 | `db-snapshot.sql` 中 `crm_contact_map` 是否存在 | preflight §8.1 标 MISSING；DDL 在 migrate_ext_15 | Phase 2 grep `db-snapshot.sql` |
| 2 | `crm_integration` 是否在生产已 migrate EXT-15 9 列 | 仅 DDL 文件存在 | Phase 2 `DESCRIBE crm_integration` |
| 3 | 前端 `/crm-integration` 路由是否在 router 注册 | `index.vue` 存在；`router/index.ts` 未读 | Phase 2 grep router 配置 |
| 4 | `dataScope` 字段来源（`TenantContextHolder.get().getDataScope()`） | 推断为"ALL 表示平台超管" | Phase 2 读 `TenantContextHolder` 实现 |
| 5 | Salesforce Token 缓存失效逻辑（`tokenExpireAt > now()+2min`） | 已确认在 F014 内 | OK |
| 6 | MySQL MCP 是否真连接上目标库 | scope-freeze §8.3 标 ✅已配置 | Phase 5 实测 |

---

**Phase 1 产出（Batch 1.1）完成。本批次 30 个 Feature 已落 `audit/feature-inventory.md`。**

**待用户决策**（不进 Phase 2 前）：

1. ISSUE-I001：是否授权更新 `.cursor/rules/ext-24-crm-integration.mdc` 修正 HubSpot/Zoho Adapter 描述？
2. ISSUE-I002：Phase 2 是否先 grep `db-snapshot.sql` 验证 `crm_contact_map` 存在性，再决定是否触发 `migrate_ext_15_crm_real.sql`？
3. ISSUE-I003：UI 契约漂移修复路径选哪个（① 后端补列 ② 前端改列 ③ 接受现状）？
4. ISSUE-I004：重构 backlog 登记，不进本批次。
5. ISSUE-I005：前端 enum 修复路径（① 前端改 ② 后端加校验）？

---

## Batch 1.2 — EXT-23 Plugin Runtime（v3 第二批）

**范围来源**：`audit/baseline/scope-freeze-batch-1.2.md` §2.1 + `audit/baseline/preflight-batch-1.2.md` §7
**EXT-* Operational Notes**：`.cursor/rules/ext-23-plugin-runtime.mdc`（**SoT**，与代码多处矛盾 — 见 §1.2.5）
**Phase 0 freeze commit**：`96b00025783ed71cbafba5cf450cee7275ef2b8d`
**Batch 起点**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`（Batch 1.1 终点）

### 1.2.1 范围涉及文件（已静态扫描）

| 类别 | 文件 | 行数 | 备注 |
|---|---|---|---|
| 业务 SPI | `backend/src/main/java/com/meiyetec/plugin/MeiyetecPlugin.java` | 47 | 5 业务方法（`pluginCode/version/displayName/description/vendor/onStart/onStop`）+ `vendor` default |
| pf4j 桥接 | `backend/src/main/java/com/meiyetec/plugin/MeiyetecPluginAdapter.java` | 38 | `extends Plugin implements MeiyetecPlugin` + `start/stop` 钩子构造 ctx |
| 核心服务 | `backend/src/main/java/com/meiyetec/plugin/PluginRuntimeService.java` | 271 | `@PostConstruct init` + `ApplicationRunner run` + 懒加载 DCL 单例 + loadAllFromDisk + loadPlugin/enable/disable/unload + dashboard + getByCode + isEnabledForTenant |
| 持久化 | `backend/src/main/java/com/meiyetec/plugin/PluginPersistenceService.java` | 89 | `recordState`（`@Transactional` 但 try/catch 包裹整个方法）+ `restoreStates` + `listByTenant` |
| 租户级 | `backend/src/main/java/com/meiyetec/plugin/PluginTenantService.java` | 99 | `isEnabledForTenant`（叠加 pf4j state + `plugin_install.enabled`）+ `setTenantEnabled` + `listByTenant`；**无 `dataScope` 校验**（直接读 `plugin_install` 表）|
| 沙箱 | `backend/src/main/java/com/meiyetec/plugin/PluginSecurityPolicy.java` | 142 | `HOST_PUBLIC_PACKAGES` / `HOST_FORBIDDEN_PACKAGES` / `FORBIDDEN_JDK_PACKAGES` 常量 + `assertPackageAllowed` + `assertCallerAllowed`（合作式探测）+ `deniedPackages` |
| DTO 1 | `backend/src/main/java/com/meiyetec/plugin/PluginRuntimeInfo.java` | 59 | 12 字段（`pluginId/pluginCode/version/displayName/description/vendor/state/className/pluginPath/errorMessage/autoLoaded`）+ `notFound/disabled` factory |
| DTO 2 | `backend/src/main/java/com/meiyetec/plugin/PluginRuntimeContext.java` | 47 | 上下文 + `attr(key,value)` 链式 KV |
| Controller | `backend/src/main/java/com/meiyetec/controller/PluginRuntimeController.java` | 195 | 10 端点（`list/dashboard/load/enable/disable/unload/listTenant/tenantEnable/tenantDisable/persisted`）+ `assertTenantAccess` 私有校验 |
| Service / Controller | `backend/src/main/java/com/meiyetec/service/PluginService.java` | 130 | **⚠ `@RestController` 命名误导**（不是 Service）— 同 Batch 1.1 I004 模式；marketplace 8 端点（market CRUD/install lifecycle/review）|
| Entity × 4 | `backend/src/main/java/com/meiyetec/entity/{PluginRuntimeState,PluginInstall,PluginMarket,PluginReview}.java` | 33/40/36/21 | RuntimeState `TableField(fill=...)` 自动填充；其余 BaseEntity 风格 |
| Mapper × 4 | `backend/src/main/java/com/meiyetec/mapper/{PluginRuntimeState,PluginInstall,PluginMarket,PluginReview}Mapper.java` | 5/5/5/5 | 全部 `extends BaseMapper` + `@Mapper` |
| DDL | `backend/src/main/resources/db/sql/migrate_ext_23_plugin_runtime_state.sql` | 29 | `CREATE TABLE IF NOT EXISTS plugin_runtime_state` + `uk_tenant_plugin` + `idx_state` + `idx_updated` |
| 配置 | `backend/src/main/resources/application.yml` line 183-187 | — | `plugin.runtime.{enabled,plugins-root,auto-load-on-startup}` 三配置 |
| Frontend 页面 | `frontend/meiyetec-web/src/views/plugin/index.vue` | 116 | **只有 marketplace + 已安装 tab，无 runtime tab**（Operational Notes §5 一致）|
| Frontend API | `frontend/meiyetec-web/src/api/plugin.ts` | 19 | `pluginApi` 封装 8 端点（**不含 `/plugin/runtime/*`**）|
| Router | `frontend/meiyetec-web/src/router/index.ts:169` | — | `path: 'plugin'` → `views/plugin/index.vue`，`permission: 'dev:access'` |
| 示例插件 | `plugins/demo-hello/{pom.xml,DemoHelloPlugin.java,META-INF/pf4j/plugin.properties,README.md,build.bat}` | 53/58/6/55/— | 完整示例：SPI 接入 + 沙箱自检 + hello() 业务入口 |
| SoT | `.cursor/rules/ext-23-plugin-runtime.mdc` | — | EXT-23 Operational Notes（§0-§6 完整）|

### 1.2.2 Feature 清单（25 项）

> 列：Feature ID / Module / 功能点 / Priority / 需求来源 / 前端入口 / 后端入口 / DB表 / 静态可达性 / 备注
> KNOWN-CONSTRAINT 标签说明：
> - **⚠ MISSING**：`plugin_runtime_state` 表缺失（DDL 已写但 `db-snapshot.sql` 未应用）— F036/F037/F049/F051
> - **⚠ PARTIAL-BLOCKER**：跨租户访问控制相关（Operational Notes §5 已知）
> - **⚠ PARTIAL-NON-BLOCKER**：合作式沙箱 / 副作用 / dead code / Frontend Shadow

| Feature ID | Module | 功能点 | Priority | 需求来源 | 前端入口 | 后端入口 | DB表 | 静态可达性 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| **F031** | Plugin SPI | 业务 SPI + pf4j 桥接 | P1 | EXT-23 §0.1 / §0.2 | (无前端入口) | `MeiyetecPlugin` 接口 (`plugin/MeiyetecPlugin.java:27-47`) + `MeiyetecPluginAdapter extends Plugin` (`plugin/MeiyetecPluginAdapter.java:25-38`) | — | L0 | 5 方法（`pluginCode/version/displayName/description`）+ `vendor default="unknown"` + `onStart/onStop default={}`；pf4j 3.x `getPluginId()` final，不能 override，pluginId 由 `META-INF/pf4j/plugin.properties` 的 `plugin.id` 提供 |
| **F032** | Plugin 运行时 | 启动期 init + auto-load | P1 | EXT-23 §0.3 / §0.6 | (无前端入口) | `PluginRuntimeService.init` `@PostConstruct` (`plugin/PluginRuntimeService.java:71-86`) + `PluginRuntimeService.run` `ApplicationRunner` (`:88-101`) + `ensureManager` DCL (`:104-116`) + `loadAllFromDisk` (`:118-143`) | `plugin_runtime_state`（启动期 `recordState`） | L0 | 启动期自动 mkdir plugins-root；auto-load-on-startup=true 时 `loadAllFromDisk` → `enablePluginInternal`；**try/catch 包裹整体** (`run:94-100`) → 失败仅 warn（与 I008 模式同）|
| **F033** | Plugin 运行时 | 手动生命周期 (load/enable/disable/unload) | P1 | EXT-23 §0.3 | (无前端入口) | `PluginRuntimeService.loadPlugin` (`:152-163`) + `enablePlugin` (`:167-174`) + `disablePlugin` (`:178-191`) + `unloadPlugin` (`:195-213`) | `plugin_runtime_state` | L0 | 每次操作都调 `recordState`；⚠ **`disablePlugin` 的 `recordState(...,errorMsg=null)` 会清掉上一次的 FAILED 错误**（Operational Notes §5）— 见 I022；⚠ **`PluginRuntimeInfo.autoLoaded` 字段定义了但 `toInfo()` 未填充** — 见 I021 |
| **F034** | Plugin 运行时 | 看板 (list + dashboard + getByCode) | P1 | EXT-23 §0.3 / §3 | (无前端入口) | `PluginRuntimeService.listPlugins` (`:147-150`) + `dashboard` (`:217-244`) + `getByCode` (`:248-252`) + `toInfo` 包装 (`:262-289`) | `plugin_runtime_state`（dashboard 叠加 `persistedRecords/lastKnownFailed`）| L0 | dashboard 字段：`runtimeEnabled/pluginsRoot/total/started/created/disabled/failed/stopped/persistedRecords/lastKnownFailed`；getByCode 包装 `resolvePluginId`（pluginCode@unknown fallback，见 Operational Notes §5）|
| **F035** | Plugin 运行时 | isEnabledForTenant 业务入口 | P1 | EXT-23 §0.5b / §6 | (无前端入口；前端**无任何调用入口**) | `PluginRuntimeService.isEnabledForTenant` (`plugin/PluginRuntimeService.java:256-259`) → 委派 `PluginTenantService.isEnabledForTenant` (`plugin/PluginTenantService.java:43-66`) | `plugin_install` | L0 | pf4j `STARTED` ∧ `plugin_install.enabled==1`（**无 install 记录默认 true** — 兜底）；tenantId=0L 回退递归查平台级；try/catch 吞异常 → false |
| **F036** | Plugin 持久化 | recordState 写入 | P1 | EXT-23 §0.5a | (间接通过 F033/F034) | `PluginPersistenceService.recordState` (`plugin/PluginPersistenceService.java:42-77`) | `plugin_runtime_state` | L0（代码） / **⚠ MISSING（表）** | `@Transactional` 但 try/catch 包裹整个方法 → **事务回滚失效**（与 I008 模式同）；**lastError 始终刷新**（即使 null 也覆盖 FAILED 错误）→ 见 I022；`loadedAt` 仅 STARTED/CREATED 时刷新 |
| **F037** | Plugin 持久化 | restoreStates + listByTenant 读取 | P2 | EXT-23 §0.5a | (间接通过 F034 dashboard) | `PluginPersistenceService.restoreStates` (`plugin/PluginPersistenceService.java:81-87`) + `listByTenant` (`:91-96`) | `plugin_runtime_state` | L0（代码） / **⚠ MISSING（表）** | try/catch 包裹；`restoreStates` 用于启动期 `dashboard.persistedRecords` 显示；表不存在时返回 `List.of()` → dashboard `persistedRecords=0/lastKnownFailed=0` |
| **F038** | Plugin 租户 | 叠加 pf4j 租户级判断 | P1 | EXT-23 §0.5b | (间接通过 F035) | `PluginTenantService.isEnabledForTenant` (`plugin/PluginTenantService.java:43-66`) | `plugin_install` | L0 | 三层判断：① pf4j state ≠ STARTED → false；② install 记录不存在 → 递归查平台级 (tenantId=0L)；③ 仍无 → 默认 true；⚠ **install 记录存在且 enabled=0 → 即便 pf4j STARTED 也拒绝** |
| **F039** | Plugin 租户 | setTenantEnabled 租户级启停 | P1 | EXT-23 §0.5b / §3 | (无前端入口) | `PluginTenantService.setTenantEnabled` (`plugin/PluginTenantService.java:71-95`) | `plugin_install` | L0 | upsert by `(pluginCode, tenantId)`：新建写 `version="latest"` + `installStatus=ACTIVE/DISABLED` + `useCount=0L`；更新只刷 `enabled/installStatus`；**不刷 `installedAt` / `pluginId`**（与 `POST /plugin/install` 链路不同）|
| **F040** | Plugin 沙箱 | 白/黑名单常量 | P0 | EXT-23 §0.5c / §2 | (无前端入口) | `PluginSecurityPolicy.HOST_PUBLIC_PACKAGES` (`plugin/PluginSecurityPolicy.java:33-37`) + `HOST_FORBIDDEN_PACKAGES` (`:40-50`) + `FORBIDDEN_JDK_PACKAGES` (`:53-63`) | — | L0 | 白名单 `com.meiyetec.plugin.` / `util.` / `exception.`；黑名单 `service.internal` / `service.impl` / `security` / `mapper` / `config` + 自身 `PluginRuntimeService/PersistenceService/TenantService`；JDK 黑名单 9 条 |
| **F041** | Plugin 沙箱 | assertPackageAllowed 主动自检 | P1 | EXT-23 §0.5c | (无前端入口；`demo-hello` 在 `onStart` 调) | `PluginSecurityPolicy.assertPackageAllowed` (`plugin/PluginSecurityPolicy.java:78-97`) | — | L0 | 顺序：黑名单 → JDK 黑名单 → 白名单（仅对 `com.meiyetec.*` 生效，其它业务包默认放行 — 插件自有 classloader）；不在白名单 → `SecurityException` |
| **F042** | Plugin 沙箱 | assertCallerAllowed 反射探测 | P1 | EXT-23 §0.5c / §5 | (无前端入口) | `PluginSecurityPolicy.assertCallerAllowed` (`plugin/PluginSecurityPolicy.java:106-135`) | — | L0 | **合作式沙箱 ⚠ PARTIAL-NON-BLOCKER**：`Class.forName("com.meiyetec.service.internal.NonExistent", false, cl)` 探测，**反射加载 `com.meiyetec.service.*`（非 internal）实际不拦截**（Operational Notes §5 + preflight §8.4.4）— 见 I020 |
| **F043** | Plugin Runtime Controller | 平台级 API (list/dashboard/load/enable/disable/unload) | P1 | EXT-23 §3 | (前端无直接入口；**无 runtime tab**) | `PluginRuntimeController` (`controller/PluginRuntimeController.java:76-130`) — `GET /list` `:78-81` + `GET /dashboard` `:84-87` + `POST /{code}/load` `:90-103` + `POST /{code}/enable` `:106-112` + `POST /{code}/disable` `:115-121` + `POST /{code}/unload` `:124-130` | `plugin_runtime_state`（load/enable 后 `recordState`）| L0 | NOT_FOUND → `BizCode.NOT_FOUND(404)`；**不传 `version` 时挑最大版本号目录**（字典序倒序，`PluginRuntimeService.resolvePluginDir` (`:227-237`)）— Operational Notes §5 已知目录选择策略简陋 |
| **F044** | Plugin Runtime Controller | 租户级 API (listTenant/tenantEnable/tenantDisable) + persisted + 跨租户校验 | P1 | EXT-23 §3 / §0.7 | (前端无任何 runtime 入口) | `PluginRuntimeController.assertTenantAccess` (`:58-69`) + `GET /tenant/{tid}/list` (`:140-160`) + `POST /tenant/{tid}/{code}/enable` (`:163-174`) + `POST /tenant/{tid}/{code}/disable` (`:177-188`) + `GET /persisted` (`:191-200`) | `plugin_install` + `plugin_runtime_state` | L0 | **`assertTenantAccess` 已实现 `dataScope=ALL` 校验**（与 Operational Notes §5 "无 dataScope 校验" 矛盾）— 见 I018；tenantId 不一致 → `BizCode.FORBIDDEN(403)` |
| **F045** | Plugin 业务 / Marketplace | 上架管理 (publish/listMarket/updatePlugin) | P3 | FEATURE_COMPARISON_MATRIX 280-287 | `plugin/index.vue` "插件市场" tab + "发布插件" 按钮 + `pubDialog` 对话框（`plugin/index.vue:40-57`）| `PluginService` (`service/PluginService.java:38-72`) — `POST /plugin/market` `:39-50` + `GET /plugin/market/list` `:52-62` + `POST /plugin/market/{id}` `:64-72` | `plugin_market` | L0 | publish 默认 `status=PUBLISHED` / `version=1.0.0` / `installCount=0` / `rating=ZERO` / `tenantId=0L`（平台级）；listMarket 默认过滤 `status=PUBLISHED`，按 `installCount` 倒序 |
| **F046** | Plugin 业务 / Marketplace | 安装生命周期 (install/uninstall/listInstalls) | P3 | FEATURE_COMPARISON_MATRIX 280-287 | `plugin/index.vue` "已安装" tab + "安装" 按钮（`plugin/index.vue:23`）| `PluginService` (`service/PluginService.java:74-103`) — `POST /plugin/install` `:75-95` + `POST /plugin/install/{id}/uninstall` `:97-103` + `GET /plugin/install/list` `:105-110` | `plugin_install` | L0 | `@Transactional` install；累加 `plugin_market.installCount`；⚠ **install 只插 `plugin_install`，不触发 runtime load**（Operational Notes §5 + preflight §8.6）→ `isEnabledForTenant` 兜底默认启用 → 业务侧 NPE 风险 — 见 I023 |
| **F047** | Plugin 业务 / Marketplace | 评价 (review/listReviews) | P3 | FEATURE_COMPARISON_MATRIX 280-287 | `plugin/index.vue` "评价" 按钮 + `reviewDialog` 对话框（`plugin/index.vue:63-74`）| `PluginService` (`service/PluginService.java:112-130`) — `POST /plugin/review` `:113-121` + `GET /plugin/review/list/{pluginId}` `:123-130` | `plugin_review` | L0 | review 默认 `memberId=0L`（平台级）+ `title="用户评价"`；listReviews 按 `pluginId` 过滤（**未按 `tenantId` 隔离** — 跨租户可读别人评价） |
| **F048** | Plugin 实体 | Marketplace 三件套 (PluginMarket / PluginInstall / PluginReview) | P3 | Operational Notes §1 | (前端通过 API 间接) | `PluginMarket` (`entity/PluginMarket.java:13-32`) 21 字段 + `PluginInstall` (`:10-31`) 14 字段 + `PluginReview` (`:10-21`) 8 字段；Mapper × 3 全部 `@Mapper extends BaseMapper` | `plugin_market` + `plugin_install` + `plugin_review` | L0 | `db-snapshot.sql:7092/7120/7159` 三表已存在；PluginInstall 含 `enabled` (Integer, 1=启用) — F038/F039 依赖；PluginMarket 含 `installCount/rating/reviewCount`（自维护，无触发器）|
| **F049** | Plugin 实体 | PluginRuntimeState + Mapper | P1 | EXT-23 §0.5a | (无前端入口；前端通过 F037 间接) | `PluginRuntimeState` (`entity/PluginRuntimeState.java:27-54`) 8 字段 + `PluginRuntimeStateMapper` (`mapper/PluginRuntimeStateMapper.java:5`) | `plugin_runtime_state` | L0（代码） / **⚠ MISSING（表）** | `@TableName("plugin_runtime_state")` + `@TableId AUTO` + `@TableField(fill=FieldFill.INSERT/INSERT_UPDATE)`（`createdAt/updatedAt` 自动填充）；Mapper 仅 `extends BaseMapper<PluginRuntimeState>` |
| **F050** | Plugin 配置 | application.yml plugin.runtime | P1 | EXT-23 §0.6 / §2 | (无前端入口) | `application.yml:183-187` — `plugin.runtime.{enabled,plugins-root,auto-load-on-startup}` | — | L0 | `enabled: ${PLUGIN_RUNTIME_ENABLED:true}`；`plugins-root: ${PLUGIN_PLUGINS_ROOT:./plugins}`（**默认相对路径，生产建议绝对路径** — Operational Notes §2 + scope-freeze §九.5）；`auto-load-on-startup: ${PLUGIN_AUTOLOAD:true}`（**生产首启建议关闭** — Operational Notes §2）|
| **F051** | Plugin DB | migrate_ext_23_plugin_runtime_state.sql DDL | P1 | EXT-23 §0.5a | (无前端入口) | DDL `db/sql/migrate_ext_23_plugin_runtime_state.sql:9-29` | `plugin_runtime_state` | L0（DDL 存在） / **⚠ MISSING（db-snapshot.sql 未应用）** | `CREATE TABLE IF NOT EXISTS`（MySQL 兼容）；唯一键 `uk_tenant_plugin(tenant_id, plugin_code)`；索引 `idx_state` + `idx_updated`；COMMENT 标 EXT-23；**Phase 2 必须 grep db-snapshot.sql 验证是否实际落地**（preflight §8.1）|
| **F052** | Plugin 前端 | 市场 + 已安装 tab | P3 | FEATURE_COMPARISON_MATRIX 280-287 | `plugin/index.vue:5-30` "插件市场" tab (el-row 卡片网格) + "已安装" tab (el-table) | (无新后端) `GET /plugin/market/list` + `GET /plugin/install/list` | — | L0 | 市场 tab 显示 `pluginName/category/version/shortDesc/rating/installCount` + "安装" + "评价" 按钮；已安装 tab 显示 `id/pluginCode/version/installedAt/installStatus` + "卸载" 按钮；⚠ **无 runtime tab**（无 pf4j 状态可见性）— 见 I024 |
| **F053** | Plugin 前端 | 发布 + 评价对话框 | P3 | FEATURE_COMPARISON_MATRIX 280-287 | `plugin/index.vue:34-59` `pubDialog`（5 字段）+ `:63-78` `reviewDialog`（2 字段）| (无新后端) `POST /plugin/market` + `POST /plugin/review` | — | L0 | pubForm 默认 `pluginCode: 'P'+Date.now()` / `category: 'MARKETING'` / `version: '1.0.0'`；reviewForm 默认 `rating: 5`；⚠ 无 loading 态 / 无二次确认 / 无错误码展示 |
| **F054** | Plugin 前端 | api/plugin.ts 封装 | P3 | FEATURE_COMPARISON_MATRIX 280-287 | `api/plugin.ts:6-19` `pluginApi` 8 方法 | (无新后端) — 全部对应 `PluginService` 端点 | — | L0 | ⚠ **`pluginApi` 不含任何 `/plugin/runtime/*`**（仅 marketplace CRUD/install/review）；前端无法触达 F031-F044 runtime 能力 |
| **F055** | Plugin 示例 | demo-hello + 目录约定 | P1 | EXT-23 §0.2 / §4 | (无前端入口；通过 `POST /plugin/runtime/demo-hello/load` 调用) | `plugins/demo-hello/pom.xml`（`provided` scope 引用 meiyetec-api + pf4j）+ `DemoHelloPlugin extends MeiyetecPluginAdapter` + `META-INF/pf4j/plugin.properties`（`plugin.id/class/version/provider`）| — | L0 | 目录约定 `${plugins-root}/{pluginCode}/{version}/*.jar`（Operational Notes §0.2）；`onStart` 调 `PluginSecurityPolicy.assertPackageAllowed(pluginCode, "com.meiyetec.demo.")` + `"com.meiyetec.plugin."`；业务入口 `hello(String)` 反射调用；**未编译成 jar**（需用户手动 `mvn clean package`）|

### 1.2.3 Feature 数量统计

| 分类 | 数量 | Feature IDs |
|---|---|---|
| Plugin 业务 SPI / 桥接 | 1 | F031 |
| Plugin 运行时核心（生命周期 / 看板 / 业务入口）| 4 | F032-F035 |
| Plugin 持久化（写 / 读）| 2 | F036-F037 |
| Plugin 租户级（叠加 / 启停）| 2 | F038-F039 |
| Plugin 沙箱（常量 / 自检 / 探测）| 3 | F040-F042 |
| Plugin Runtime Controller | 2 | F043-F044 |
| Plugin 业务 / Marketplace | 3 | F045-F047 |
| Plugin 实体（marketplace / runtime）| 2 | F048-F049 |
| Plugin 配置 / DB | 2 | F050-F051 |
| 前端（tab / dialog / API）| 3 | F052-F054 |
| 示例插件 + 目录约定 | 1 | F055 |
| **合计** | **25** | F031-F055 |

### 1.2.4 Phase 1 Issue 清单（**不进 Phase 2 前必须决策**）

| Issue ID | 类别 | 描述 | 影响 Feature | 建议处理 |
|---|---|---|---|---|
| **ISSUE-I017** | **MISSING（DB）** | `plugin_runtime_state` 表 DDL 文件 `migrate_ext_23_plugin_runtime_state.sql` 已存在（**Phase 1 期间新发现**，与 preflight §8.1 一致）；但 `db-snapshot.sql` 全文件 grep `plugin_runtime_state` 0 匹配 → **baseline DB 未跑该 migration**。`PluginPersistenceService.recordState` 启动期写入会触发 `TableNotFoundException` → try/catch 仅 warn → 与 Batch 1.1 I008 类似 Fake Success 链路 | F036 / F037 / F049 / F051 | Phase 2：① grep `db-snapshot.sql` 确认缺失；② 触发 `migrate_ext_23_plugin_runtime_state.sql` 跑批；③ 写 `audit/evidence/F036-plugin-persistence-record/db-evidence.sql` 验证写入 |
| **ISSUE-I018** | SoT 文档漂移 | Operational Notes §5 "租户隔离缺失：`PluginRuntimeController` 所有端点无 `dataScope` 校验" — **与代码不符**。`PluginRuntimeController.assertTenantAccess` (`controller/PluginRuntimeController.java:58-69`) **已实现** `dataScope=ALL` 校验 + tenantId 等值校验 + `BizCode.FORBIDDEN(403)`。但 §0.7 "无 `dataScope` 校验" 也错 | F044 | **必修**：更新 `.mdc` §0.7 + §5（删除 "无 dataScope 校验" 描述，改为 "已实现但需 Phase 5 验证"） |
| **ISSUE-I019** | 命名/架构异味 | `PluginService.java` 文件名是 `*Service`，实际是 `@RestController` `@RequestMapping("/plugin")`，挂在 `service/` 包下（项目其他 Controller 在 `controller/` 包）。8 个 REST endpoint 全在这一个文件里 — **与 Batch 1.1 I004 完全同模式** | F045 / F046 / F047 | 不阻塞 Phase 2；但应记入后续重构 backlog。**Phase 1 标注不动** |
| **ISSUE-I020** | **⚠ PARTIAL-NON-BLOCKER（沙箱合作式限制）** | `PluginSecurityPolicy.assertCallerAllowed` (`plugin/PluginSecurityPolicy.java:106-135`) 仅做 `Class.forName("com.meiyetec.service.internal.NonExistent", false, cl)` 探测 — **反射加载 `com.meiyetec.service.*`（非 internal）实际不拦截**（Operational Notes §5 + preflight §8.4.4 一致）。Java 17 已弃用 SecurityManager，本沙箱是合作式 | F042 | 决策：① 接受现状，文档化为已知限制；② Post-Audit 升级为字节码增强（agent）或 OSGi（成本高） |
| **ISSUE-I021** | dead code | `PluginRuntimeInfo.autoLoaded` 字段（`plugin/PluginRuntimeInfo.java:43`）定义了但 `toInfo()` (`plugin/PluginRuntimeService.java:262-289`) **未填充** — Operational Notes §5 "loadPlugin 不写 autoLoaded=true" 一致；前端**无任何消费方** | F033 / F034 | 决策：① 删除字段；② 在 `loadAllFromDisk` 路径填充 `autoLoaded=true`（区分手动 vs 自动加载） |
| **ISSUE-I022** | **⚠ PARTIAL-NON-BLOCKER（副作用）** | `PluginPersistenceService.recordState` 写入策略（`plugin/PluginPersistenceService.java:60`）：`lastError` **始终刷新**（即使是 null 也覆盖成功）。`disablePlugin` (`PluginRuntimeService.java:178-191`) 调用时 `errorMsg=null` → **会清掉上一次的 FAILED 错误** — 与 Operational Notes §5 "disablePlugin 副作用" 一致；运维侧失去 FAILED → DISABLED 转换的审计线索 | F033 / F036 | 决策：① 接受现状（"成功覆盖失败"语义统一）；② recordState 增加 `errorMsg` 入参 null 时**保留旧值**策略 |
| **ISSUE-I023** | **⚠ PARTIAL-NON-BLOCKER（幽灵记录 NPE）** | marketplace `POST /plugin/install` (`service/PluginService.java:75-95`) **只插 `plugin_install` 表，不触发 runtime load** — Operational Notes §5 + preflight §8.6 一致；`isEnabledForTenant` 兜底默认启用 → 业务侧调到一个不存在的插件入口 → **NPE** | F046 / F035 | 决策：① install 时联动 `POST /plugin/runtime/{code}/load`（需异步 + 失败容忍）；② 文档化为已知约束，依赖业务方先调 load |
| **ISSUE-I024** | Frontend Shadow | `plugin/index.vue` 只有「市场」+「已安装」tab（`plugin/index.vue:4-30`），**无 runtime tab** — Operational Notes §5 + scope-freeze §九.6 一致；看不到 pf4j 状态机（STARTED/STOPPED/FAILED/DISABLED）；前端无任何 `/plugin/runtime/*` 调用入口（`api/plugin.ts:6-19` 仅 marketplace）| F052 / F054 | 决策：① 加 runtime tab 调用 F043-F044；② 接受现状（运维侧通过 curl 看板）；③ 与 I023 联动：runtime 状态可见后才允许 install |
| **ISSUE-I025** | **⚠ UNVERIFIED（dead code 候选）** | `PluginSecurityPolicy.deniedPackages()` 方法（`plugin/PluginSecurityPolicy.java:140-142`）定义了 `Set<String>` 返回 4 个黑名单条目，但**无任何代码路径调用** — 孤儿工具 | F042 | 决策：① 删除；② 留作未来 `assertPackageAllowed` 增强用（返回可拒绝包列表） |

### 1.2.5 与 EXT-* Operational Notes 的冲突点（SoT 比对）

> 按 `project-audit.mdc` 规则 11：审计结论与 EXT-* Operational Notes 冲突时，**以 EXT-* 为准并标 ISSUE**。
> 本次审计发现 **EXT-* 文档本身与代码不一致**（同 Batch 1.1 I001 模式），需在 Issue 清单显式标记。

| EXT-* 章节 | 文档声称 | 代码事实 | 处理 |
|---|---|---|---|
| §0.7 跨租户访问 | "无 `dataScope` 校验" | `PluginRuntimeController.assertTenantAccess` (`:58-69`) **已实现** | ISSUE-I018 |
| §5 已知问题 | "租户隔离缺失" | 同上 | ISSUE-I018 |
| §5 已知问题 | "`autoLoaded` 字段定义了但实际未填充" | `PluginRuntimeInfo.autoLoaded` (`:43`) 定义，`toInfo()` 未填 — **一致** | OK（验证） |
| §5 已知问题 | "`disablePlugin` 副作用覆盖 FAILED 错误" | `PluginPersistenceService.recordState` (`:60`) `lastError` 始终刷新 — **一致** | ISSUE-I022 |
| §5 已知问题 | "`plugin_install` 幽灵记录（marketplace install 不触发 runtime load）" | `PluginService.install` (`:75-95`) 仅插 `plugin_install` — **一致** | ISSUE-I023 |
| §5 已知问题 | "前端无 runtime tab" | `plugin/index.vue` 仅 marketplace + 已安装 tab；`api/plugin.ts` 无 `/plugin/runtime/*` — **一致** | ISSUE-I024 |
| §5 已知问题 | "合作式沙箱" | `assertCallerAllowed` (`:106-135`) 仅探测 `service.internal.NonExistent` — **一致** | ISSUE-I020 |
| §0.5a `PluginPersistenceService` | "`plugin_runtime_state` 表缺失" | DDL 已写 `migrate_ext_23_plugin_runtime_state.sql`；`db-snapshot.sql` 未应用 — **Phase 1 新发现**：DDL 已就位但未跑 | ISSUE-I017 |
| §0.1 业务 SPI | "`pluginCode` 与 `plugin_market.plugin_code` 对齐" | `MeiyetecPlugin.pluginCode()` (`MeiyetecPlugin.java:27`) — **一致** | OK |
| §0.2 目录布局 | "`${plugins-root}/{pluginCode}/{version}/*.jar`" | `PluginRuntimeService.resolvePluginDir` (`:227-237`) — **一致**；但**字典序倒序**（Operational Notes §5 自承认"非真正的 semver 比较"）| OK（已知约束）|
| §0.6 配置 | "`plugin.runtime.plugins-root: ./plugins`（生产建议绝对路径）" | `application.yml:184` — **一致** | OK |
| §0.5b `PluginTenantService` | "`plugin_install.enabled == 1`" | `PluginTenantService.isEnabledForTenant` (`:43-66`) — **一致** | OK |
| §1 Touchpoints | 列了 `PluginPersistenceService` / `PluginRuntimeStateMapper` | `mapper/PluginRuntimeStateMapper.java` 存在 — **一致** | OK |

### 1.2.6 未确认项（标记"未确认"，禁止猜测）

| # | 项 | 当前信息 | 待补来源 |
|---|---|---|---|
| 1 | `db-snapshot.sql` 中 `plugin_runtime_state` 是否存在 | Phase 1 grep **0 匹配**（MISSING 已确认）；DDL 在 `migrate_ext_23_plugin_runtime_state.sql:9-29` 已写 | Phase 2 触发 migration + `DESCRIBE plugin_runtime_state` |
| 2 | `migrate_ext_23_plugin_runtime_state.sql` 是否在生产已跑 | 仅 DDL 文件存在；`db-snapshot.sql` 未含 | Phase 2 实际查询 |
| 3 | `plugin_runtime_state` 的 8 列字段与实体类一致 | DDL: `tenant_id/plugin_code/version/plugin_id/state/last_error/loaded_at/created_at/updated_at`；实体 `PluginRuntimeState.java:27-54` — **静态一致** | OK |
| 4 | 前端 `/plugin` 路由已注册 | `router/index.ts:169` 确认 `path: 'plugin'` + `permission: 'dev:access'` | OK |
| 5 | `TenantContextHolder.getDataScope()` 返回 "ALL" 的语义 | `TenantContextHolder.java:62-64` 已确认 — `admin` 用户兜底设 "ALL"，其它由 `RbacContextLoader.load(Long)` 完整路径注入 | OK |
| 6 | `BizCode.FORBIDDEN(403)` / `NOT_FOUND(404)` / `SYSTEM_ERROR(500)` | `exception/BizCode.java:21-22, 68` 确认 | OK |
| 7 | `demo-hello` 插件 jar 是否实际编译 | `plugins/demo-hello/` 仅源码 + `build.bat`；无 `target/` 目录 — **未编译** | Phase 5 用户手动 `mvn clean package` |
| 8 | `plugin_install` 兜底默认启用（无记录时 true）| `PluginTenantService.java:54-60` 已确认 | OK |
| 9 | `PluginSecurityPolicy.deniedPackages()` 是否有调用方 | Phase 1 grep 全代码 0 匹配 | OK（确认孤儿）|

---

**Phase 1 产出（Batch 1.2）完成。本批次 25 个 Feature 已落 `audit/feature-inventory.md`，与 scope-freeze §3.3 预期 20-25 完全一致。**

**已知约束标记汇总**（KNOW-CONSTRAINT，强制关注）：
- ⚠ **MISSING（DB）**：`plugin_runtime_state` 表 DDL 已就位但 `db-snapshot.sql` 未应用（DDL 在 `db/sql/migrate_ext_23_plugin_runtime_state.sql`）— 触发 F036/F037/F049/F051 全部 → **强制 MISSING / PARTIAL-BLOCKER**
- ⚠ **PARTIAL-BLOCKER**：跨租户访问控制（F044）— 实际 `assertTenantAccess` 已实现（与 SoT §5 矛盾）
- ⚠ **PARTIAL-NON-BLOCKER**：沙箱合作式（F042）/ 副作用（F033+F036）/ 幽灵记录（F035+F046）/ Frontend Shadow（F052+F054）

**待用户决策**（不进 Phase 2 前）：

1. **ISSUE-I017**：Phase 2 是否先 grep `db-snapshot.sql` 验证 `plugin_runtime_state` 缺失，并触发 `migrate_ext_23_plugin_runtime_state.sql` 跑批？
2. **ISSUE-I018**：是否授权更新 `.cursor/rules/ext-23-plugin-runtime.mdc` §0.7 + §5（删除 "无 dataScope 校验" 描述）？
3. **ISSUE-I019**：重构 backlog 登记，不进本批次。
4. **ISSUE-I020**：`assertCallerAllowed` 合作式沙箱限制 — ① 接受现状 ② Post-Audit 升级为字节码增强？
5. **ISSUE-I021**：`PluginRuntimeInfo.autoLoaded` dead code — ① 删除 ② 填充？
6. **ISSUE-I022**：`recordState` lastError 覆盖 FAILED — ① 接受 ② 改写入策略？
7. **ISSUE-I023**：`plugin_install` 幽灵记录 — ① install 时联动 load ② 文档化为已知约束？
8. **ISSUE-I024**：前端 runtime tab 缺失 — ① 加 tab ② 接受现状？
9. **ISSUE-I025**：`PluginSecurityPolicy.deniedPackages()` 孤儿 — ① 删除 ② 留作未来用？


---



## Batch 1.3 — EXT-22 Google Maps Place Details + 有赞（v3 第三批）

**范围来源**：`audit/baseline/scope-freeze-batch-1.3.md` §2.1 + `audit/baseline/preflight-batch-1.3.md` §七
**EXT-* Operational Notes**：`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（**SoT**，**5 项与代码方向反转 + 1 项表 MISSING** — 见 §1.3.5）
**Batch 起点**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2 freeze commit）
**Feature 编号策略**：接续 Batch 1.2 的 F031-F055 → 本批次 **F056-F080（25 Features）**

### 1.3.1 范围涉及文件（已静态扫描）

| 类别 | 文件 | 行数 | 备注 |
|---|---|---|---|
| Google Maps Client | `backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsGeocodingClient.java` | 381 | `geocode` + `findPlace` + `placeDetails` 三方法 + `GeocodeResult` / `PlaceDetailResult` 两个 DTO + `buildGeocodeUrl` / `buildFindPlaceUrl` / `buildPlaceDetailsUrl` 三个 URL 构造工具 + 私有 `httpGet` |
| Google Maps Config | `backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsClientConfig.java` | 17 | `@Configuration` + `@Bean` 注册 `GoogleMapsGeocodingClient`（无状态，单例）|
| Google Maps Service | `backend/src/main/java/com/meiyetec/service/GoogleMapsService.java` | 281 | **⚠ `@RestController` 命名误导**（不是 Service）— 同 Batch 1.1 I004 / Batch 1.2 I019 模式；含 9 端点（`/config` ×2 + `/health` `/geocode` `/geocode/batch` `/place/details` `/stores` `/heatmap` `/geocode/log`）+ 5 个 mock 演示门店（STORES 常量）|
| Google Maps Entity | `backend/src/main/java/com/meiyetec/entity/GoogleMapsConfig.java` | 56 | 11 字段 + `enablePlaceDetails: Integer`（L43，1=启用计费 / 0=默认）+ `apiKey` `JsonProperty.WRITE_ONLY` |
| Google Maps Entity | `backend/src/main/java/com/meiyetec/entity/GoogleMapsGeocodeLog.java` | 38 | 11 字段（`tenantId/address/formattedAddress/lat/lng/placeId/status/errorMessage/durationMs/requestAt`）|
| Ecommerce Adapter 接口 | `backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapter.java` | 122 | 5 方法（`platformCode/authenticate/fetchProducts/fetchOrders/healthCheck`）+ 4 DTO（`AuthResult/ProductPage/OrderPage/HealthStatus`）|
| Ecommerce Router | `backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapterRouter.java` | 41 | platform → adapter；MISS fallback 到 MOCK（与 CrmAdapterRouter 同源设计）|
| Adapter 实现 - 有赞 | `backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java` | 305 | `@Component`；OAuth2 Client-Credentials + Token 缓存（`tokenExpireAt > now()+2min`）+ 商品/订单拉取 + 健康检查 + **`verifyWebhookSignature`**（L146-168 md5 算法）+ **`buildWebhookUrl`**（L106-114）|
| Adapter 实现 - Mock | `backend/src/main/java/com/meiyetec/ecommerce/MockEcommerceAdapter.java` | 78 | `@ConditionalOnMissingBean(name="youzanEcommerceAdapter")` + 3 样本商品 + 2 样本订单 + sleep 模拟延迟 |
| Ecommerce Service | `backend/src/main/java/com/meiyetec/service/EcommerceService.java` | 355 | **⚠ `@RestController` 命名误导**（同 F060 模式）— 10 端点（`/platform` ×3 + `/platform/{id}/health` + `/product/list` + `/product/sync/{pid}` + `/order/list` + `/order/sync/{pid}` + `/dashboard`）+ upsertProduct / upsertOrder 私有方法 + persistTokenCache 私有方法 |
| Webhook Controller | `backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java` | 359 | `@RestController @PostMapping("/webhook/youzan") @Transactional`（**未指定 rollbackFor** — 与 I009 模式同）+ 读 rawBody → 反查 platform → 验签 → msg_id 幂等去重 → upsert order → 写 webhook_msg_log；upsertOrder 复制 EcommerceService 私有版本独立维护 |
| Entity × 4 | `backend/src/main/java/com/meiyetec/entity/{EcommercePlatform,EcommerceOrder,EcommerceProduct,EcommerceSyncLog}.java` | 41/47/—/19 | EcommercePlatform 含 `appId`(L19) + `kdtId`(L21) + `signSecret`(L23) 三新字段（EXT-22 字段漂移）|
| Entity × 1（EXT-24 边界）| `backend/src/main/java/com/meiyetec/entity/WebhookMsgLog.java` | 27 | `source/msgId/msgType/externalOrderId/status/rawPayload/signature/errorMessage/processedAt/createdAt`；uk `uk_source_msg(source, msg_id)` — **EXT-24 边界，Batch 1.3 只引用不重复审计** |
| Mapper × 5 | `backend/src/main/java/com/meiyetec/mapper/{GoogleMapsConfigMapper,GoogleMapsGeocodeLogMapper,EcommercePlatformMapper,EcommerceProductMapper,EcommerceOrderMapper,EcommerceSyncLogMapper,WebhookMsgLogMapper}.java` | — | 全部 `extends BaseMapper` + `@Mapper` |
| DDL | `backend/src/main/resources/db/sql/migrate_ext_17_google_maps.sql` | 53 | DROP + CREATE `google_maps_config` + `google_maps_geocode_log`；uk `uk_tenant_brand` + 索引 `idx_tenant` / `idx_tenant_request` / `idx_status` |
| DDL | `backend/src/main/resources/db/sql/migrate_ext_22_google_maps_place_details.sql` | 15 | ALTER `google_maps_config ADD COLUMN IF NOT EXISTS enable_place_details` + ALTER `ecommerce_platform ADD COLUMN IF NOT EXISTS app_id/kdt_id/sign_secret`（MySQL 8.0.29+）|
| DDL（EXT-24 边界，引用不重审）| `backend/src/main/resources/db/sql/migrate_ext_24_webhook_msg_log.sql` | 24 | CREATE `webhook_msg_log` + uk `uk_source_msg` + 3 索引 |
| DDL（参考）| `backend/src/main/resources/db/sql/create_missing_tables_b4b5.sql` | 230+ | 含 `ecommerce_platform`(L372) + `ecommerce_order`(L411) |
| Frontend 页面 - Google Maps | `frontend/meiyetec-web/src/views/google-maps/index.vue` | 175 | **4 tab（配置/门店地图/地理编码测试/Place Details 测试）**；**`enablePlaceDetails` 开关 UI 已实现**（L13-31，与 SoT §5 描述反向）+ Place Details 测试表单 + 计费警告 el-alert |
| Frontend API - Google Maps | `frontend/meiyetec-web/src/api/google-maps.ts` | 18 | `googleMapsApi` 6 端点（`saveConfig/getConfig/geocodeStore/listStores/getPlaceDetails/getMemberHeatmap`）|
| Frontend 页面 - Ecommerce | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | 95 | **3 tab（平台管理/商品同步/订单同步）** + 添加平台对话框（**无 `appId/kdtId/signSecret` 字段 UI** — 与 SoT §5 一致，缺 3 字段 UI — 见 I028）|
| Frontend API - Ecommerce | `frontend/meiyetec-web/src/api/ecommerce.ts` | 19 | `ecommerceApi` 8 端点（`createPlatform/listPlatforms/updatePlatform/listProducts/syncProducts/listOrders/syncOrders/dashboard`）|
| SoT | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` | — | EXT-22 Operational Notes（§0-§6 完整）|

### 1.3.2 Feature 清单（25 项）

> 列：Feature ID / Module / 功能点 / Priority / 需求来源 / 前端入口 / 后端入口 / DB表 / 静态可达性 / 备注
> KNOWN-CONSTRAINT 标签说明（沿用 Batch 1.1/1.2）：
> - **⚠ MISSING**：表 DDL 已写但 dev DB 未跑迁移（用户决策 #3：Phase 1 期间跑迁移即解）
> - **⚠ CONFLICT**：SoT vs 代码描述方向不一致（中立标 I026-I030，沿用 I001 / I018 模式）
> - **⚠ PARTIAL-NON-BLOCKER**：shadow UI / 孤儿工具 / 字段漂移风险 / 合作式降级

| Feature ID | Module | 功能点 | Priority | 需求来源 | 前端入口 | 后端入口 | DB表 | 静态可达性 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| **F056** | Google Maps / 实体 | GoogleMapsConfig 实体（含 `enablePlaceDetails`）| P1 | EXT-22 §0.2 + §4 | (前端通过 API 间接) | `entity/GoogleMapsConfig.java:11-56` | `google_maps_config` | L0 / **⚠ MISSING（表）** | 11 字段：`tenantId/brandId/apiKey(defaultLanguage/defaultRegion/quotaDaily/rateLimitPerMin/enabled/enablePlaceDetails/lastHealthAt/lastHealthMsg/configJson/operatorId/operatorName)`；`apiKey` `JsonProperty.WRITE_ONLY`（L20，输出仅回显后 4 位）；`enablePlaceDetails: Integer`（L43，1=启用计费 / 0=默认）；uk `uk_tenant_brand(tenant_id, brand_id, deleted)` — **跑迁移即解** |
| **F057** | Google Maps / 实体 + 日志 | GoogleMapsGeocodeLog 实体 + 写入 | P1 | EXT-22 §4 + §0.1 | (前端通过 F064 `/geocode/log` 间接) | `entity/GoogleMapsGeocodeLog.java:13-38` + `service/GoogleMapsService.java:159-170`（`/geocode` 写入）+ `:225-237`（`/geocode/batch` 写入）| `google_maps_geocode_log` | L0 / **⚠ MISSING（表）** | 11 字段（`tenantId/address/formattedAddress/lat/lng/placeId/status/errorMessage/durationMs/requestAt`）；每次 geocode 调用写一行；try/catch 包裹写日志（失败仅 warn，不阻塞返回）— **跑迁移即解** |
| **F058** | Google Maps / Client | Geocoding + Places HTTP 客户端（3 方法）| P1 | EXT-22 §0.1 + §0.3 + scope-freeze §2.3 | (无前端入口；由 F060-F063 调用) | `googlemaps/GoogleMapsGeocodingClient.java` — `geocode(apiKey, address, language, region)` (`:97-145`) + `findPlace(apiKey, query, language)` (`:155-194`) + **`placeDetails(apiKey, placeId, language)`** (`:209-292`) + `buildGeocodeUrl/FindPlaceUrl/PlaceDetailsUrl` (`:51-72`) | — | L0 | 三 URL 常量：`GEOCODE_URL` / `PLACES_URL` / `PLACE_DETAILS_URL`；Place Details 一次性取 11 字段（`name,formatted_address,formatted_phone_number,international_phone_number,website,opening_hours,rating,user_ratings_total,business_status,geometry,types`）→ 始终命中 3 SKU（Basic+Contact+Atmosphere）；HTTP 客户端私有 `httpGet(url, connectTimeout=5000, readTimeout=15000)` |
| **F059** | Google Maps / Config | Spring Bean 注册（无状态单例）| P1 | EXT-22 §1 + scope-freeze §2.3 | (无前端入口；F058 依赖) | `googlemaps/GoogleMapsClientConfig.java:13-16` — `@Configuration` + `@Bean googleMapsGeocodingClient(ObjectMapper jackson)` | — | L0 | 注入 `ObjectMapper` 用于 JSON 解析；无 `@ConditionalOnMissingBean`（直接注册，无 fallback adapter）|
| **F060** | Google Maps / Service | `/config` + `/health` 配置 CRUD + 健康检查 | P1 | EXT-22 §0.2 + §3 | `google-maps/index.vue` "配置" tab (`googleMapsApi.saveConfig/getConfig`) | `service/GoogleMapsService.java:67-115` — `POST /config` (`:67-91`) + `GET /config` (`:94-110`) + `POST /health` (`:113-131`) | `google_maps_config` | L0 / **⚠ MISSING（表）** | `POST /config` 强制 `apiKey` 必填 + 默认值（`defaultLanguage=zh-CN`/`defaultRegion=cn`/`quotaDaily=10000`/`enabled=1`/`enablePlaceDetails=0`）；`GET /config` 输出 `apiKey="***"+substring(0, length-4)`（**仅遮蔽后 4 位，39 字符 apiKey 仍可猜前 35 位** — 与 SoT §5 一致）；`/health` 用 `geocode("Beijing")` 写回 `lastHealthAt/lastHealthMsg` |
| **F061** | Google Maps / Service | `/geocode` 端点 + mock 降级 + 日志 | P1 | EXT-22 §0.1 + §3 | `google-maps/index.vue` "地理编码测试" tab (`googleMapsApi.geocodeStore`) | `service/GoogleMapsService.java:138-180` — `POST /geocode` | `google_maps_geocode_log` | L0 | 三门禁：① `address` 必填 ② cfg 存在 + apiKey 非空 → 真实链路 ③ 否则 mock 降级（`mockGeocode` L243-253 用 `30+nextDouble()*10` 模拟经纬度）；每次写日志；返回 `source: GOOGLE_MAPS/MOCK` |
| **F062** | Google Maps / Service | `/place/details` 计费闸门（EXT-22 ⭐）| P1 | **EXT-22 §0.3 ⭐ 计费闸门** | `google-maps/index.vue` "Place Details 测试" tab (`googleMapsApi.getPlaceDetails`) | `service/GoogleMapsService.java:191-232` — `POST /place/details` | (间接写 `google_maps_geocode_log` — **注意：当前实现未写！仅 `/geocode` 写日志**) | L0 | **三段门禁**：① `placeId` 必填 → 否则 `BAD_REQUEST`；② cfg 存在 + apiKey 非空 → 否则 `NOT_FOUND`；③ **`enablePlaceDetails == 1` → 否则 `PERMISSION_DENIED`**（**唯一计费闸门** — SoT §0.3 Step 2）；调用 `geocodingClient.placeDetails(apiKey, placeId, lang)`；返回 11 fields；**⚠ PARTIAL-NON-BLOCKER**：未写 `google_maps_geocode_log`（仅 `/geocode` 写日志）— 见 I031 |
| **F063** | Google Maps / Service | `/geocode/batch` admin-only 批量解析 | P2 | EXT-22 §3 + §5 | (无前端入口) | `service/GoogleMapsService.java:259-318` — `POST /geocode/batch` | `google_maps_geocode_log`（每条写）| L0 | admin 守卫：仅 `SUPER_ADMIN/PLATFORM_ADMIN/HQ_ADMIN` 可调（否则 `PERMISSION_DENIED`）；单次最多 50 条；复用 `/geocode` 配置 + mock 降级逻辑；响应 `{total, ok, fail, durationMs, source, results[]}`；**⚠ PARTIAL-NON-BLOCKER**：admin-only 但无 audit log（SoT §5 "批量 geocode 无 audit log" 已知问题）— 见 I032 |
| **F064** | Google Maps / Service | `/stores` + `/heatmap` + `/geocode/log` 演示与审计 | P3 | EXT-22 §3 + scope-freeze §2.3 | `google-maps/index.vue` "门店地图" tab (`googleMapsApi.listStores/getMemberHeatmap`) | `service/GoogleMapsService.java:320-348` — `GET /stores` (`:320-322`, 5 mock 门店) + `GET /heatmap` (`:325-338`, 200 模拟点) + `GET /geocode/log` (`:341-347`, 最近 100 条) | (只读) `google_maps_geocode_log` | L0 | 演示数据：5 门店（北京三里屯/上海徐汇/深圳南山/纽约/东京）；heat map 200 点用 random 偏移 + weight；`/geocode/log` 按 `tenantId` 过滤 + `id DESC LIMIT 100` |
| **F065** | Google Maps / 前端 | Google Maps 配置 + Place Details 测试 UI | P1 | EXT-22 §0.2 + §3 | `google-maps/index.vue` (`views/google-maps/index.vue:1-175`) | (无新后端) `GET /google-maps/config` + `POST /google-maps/config` + `POST /google-maps/place/details` | — | L0 | **4 tab**：配置 / 门店地图 / 地理编码测试 / Place Details 测试；`enablePlaceDetails` el-switch + 计费警告 el-alert **已实现**（L13-31）；**与 SoT §5 "前端缺 UI" 描述反向** — 见 I028 部分反向；⚠ **configForm 未传 `defaultLanguage/defaultRegion/quotaDaily/enabled`**（前端表单只含 apiKey + defaultZoom + enablePlaceDetails） |
| **F066** | Ecommerce / 实体 | EcommercePlatform 实体（含 `appId/kdtId/signSecret` 三新字段）| P1 | **EXT-22 §0.4 + §4 ⚠ 字段漂移风险** | (前端通过 F079/F080 间接) | `entity/EcommercePlatform.java:11-41` | `ecommerce_platform` | L0 / **⚠ MISSING（DDL 未在 ext_17/22 包含 base 表）** | 26 字段，新增 3 字段（L19/L21/L23）：`appId`(EXT-22) + `kdtId`(EXT-22) + `signSecret`(EXT-22)；注意：`appKey` ≠ `appId`（OAuth2 client_id vs 有赞 AppID，SoT §0.4 强调）；`signSecret` 用于 webhook 验签；⚠ `tenantId=0L` 默认（`EcommerceService.createPlatform` L73）|
| **F067** | Ecommerce / 实体 | EcommerceOrder + EcommerceProduct + EcommerceSyncLog 三件套 | P1 | EXT-22 §1 + scope-freeze §2.3 | (前端通过 F080 间接) | `entity/EcommerceOrder.java:11-44` + `EcommerceSyncLog.java:11-28` | `ecommerce_order` + `ecommerce_sync_log` | L0 | `ecommerce_order` 19 字段含 `externalOrderId/orderNo/buyerName/buyerPhone/amount/shippingFee/totalAmount/address/trackingNo/itemsJson/rawPayload/orderTime`；`ecommerce_sync_log` 9 字段含 `successCount/failCount/durationMs/status/errorMessage`；共用于 F080 同步链路 |
| **F068** | Ecommerce / Adapter | EcommerceAdapter 接口契约 | P1 | EXT-22 §0 + scope-freeze §2.3 | (无前端入口) | `ecommerce/EcommerceAdapter.java` — 接口 (`platformCode/authenticate/fetchProducts/fetchOrders/healthCheck`) + 4 DTO (`AuthResult/ProductPage/OrderPage/HealthStatus`) | — | L0 | 与 `CrmAdapter` 同源设计；屏蔽有赞/微店/淘宝/Mock 实现差异；Runtime 由 Spring 注入指定实现（`YouzanEcommerceAdapter`/`MockEcommerceAdapter`）|
| **F069** | Ecommerce / Adapter | YouzanEcommerceAdapter.authenticate (OAuth2 + Token 缓存) | P1 | EXT-22 §0.4 + scope-freeze §2.3 | (无前端入口) | `ecommerce/YouzanEcommerceAdapter.java:170-211` — `authenticate(EcommercePlatform cfg)` + Token 缓存 (`tokenExpireAt > now()+2min`) + 缓存写回 `cfg.setAccessToken/setTokenExpireAt` | (写回) `ecommerce_platform` | L0 | OAuth2 Client-Credentials：`POST /api/oauthentry/grant/token` body=`client_id={appKey}&client_secret={appSecret}&grant_type=client_credential`；缓存失效重签发；`buildTokenRequestBody` (L59-63) + `DEFAULT_API_BASE` 常量；HTTP POST 5000ms connect / 15000ms read |
| **F070** | Ecommerce / Adapter | YouzanEcommerceAdapter 商品 / 订单拉取 + 健康检查 | P1 | EXT-22 §3 + scope-freeze §2.3 | (无前端入口) | `ecommerce/YouzanEcommerceAdapter.java` — `fetchProducts(cfg, pageNo, pageSize)` (`:213-244`) + `fetchOrders(cfg, startTime, endTime, pageNo, pageSize)` (`:248-284`) + `healthCheck(cfg)` (`:286-303`) | — | L0 | 商品：`GET /items/list?page_no=1&page_size=50&access_token=...`；订单：`GET /trades/sold_get?start_update_time=...&end_update_time=...`；pageSize 上限 100；**响应总条数 = `root.total_results`**；`buildItemsListUrl` (L66-71) + `buildTradesSoldGetUrl` (L74-83) |
| **F071** | Ecommerce / Adapter | YouzanEcommerceAdapter.verifyWebhookSignature（md5 签名算法）| P0 | **EXT-22 §0.5 ⭐ 签名算法** | (无前端入口；F077 调用) | `ecommerce/YouzanEcommerceAdapter.java:146-168` — `public static boolean verifyWebhookSignature(String signSecret, String rawBody, String signature)` | — | L0 | 算法：`expected = lowercase( hex( md5( signSecret + rawBody ) ) )`；比较 `equalsIgnoreCase`（防御性）；`null/empty` 任一入参 → false；异常 catch → false；与 SoT §0.5 完全一致；**已有调用方**（F077 `YouzanWebhookController.java:121`）— **与 SoT §5 "是孤儿代码" 描述反向** — 见 I026 |
| **F072** | Ecommerce / Adapter | YouzanEcommerceAdapter.buildWebhookUrl（**孤儿工具 BACKLOG-001**）| P3 | EXT-22 §0.6 + §5 | (无前端入口；无任何调用方) | `ecommerce/YouzanEcommerceAdapter.java:106-114` — `static String buildWebhookUrl(String baseUrl, String kdtId)` | — | L0 | 构造 `https://{domain}/webhook/youzan?kdt_id={kdtId}`；**Phase 1 grep 全代码 0 匹配调用方**（仅声明 + 实现）；⚠ **BACKLOG-001**（沿用 I013 dead code 模式）— 见 I033；SoT §5 描述"是孤儿工具"确认一致 |
| **F073** | Ecommerce / Adapter | MockEcommerceAdapter `@ConditionalOnMissingBean` fallback | P1 | EXT-22 §1 + §3 | (无前端入口；F074 fallback) | `ecommerce/MockEcommerceAdapter.java:18-78` — `@Component @ConditionalOnMissingBean(name="youzanEcommerceAdapter")` | — | L0 | 3 样本商品（L21-25）+ 2 样本订单（L27-31）；`authenticate` 返回 `MOCK-TOKEN-{UUID}`；`sleep(50ms/80ms)` 模拟延迟；`healthCheck` 返回 `MOCK_OK`；**激活条件**：`@ConditionalOnMissingBean(name="youzanEcommerceAdapter")`（**注意 Bean 名是 youzanEcommerceAdapter** — 与 I012 模式同 Bean 条件冲突风险）|
| **F074** | Ecommerce / Router | EcommerceAdapterRouter platform 分发 + Mock fallback | P1 | EXT-22 §1 + scope-freeze §2.3 | (无前端入口；F079/F080 调用) | `ecommerce/EcommerceAdapterRouter.java:19-37` — `getAdapter(platform)` + `hasAdapter(platform)` + `listPlatforms()` | — | L0 | 与 `CrmAdapterRouter` 同源设计；`platform` toUpperCase 查 Map；miss → fallback `getAdapter("MOCK")`；null → fallback；`listPlatforms` 返回 sorted List |
| **F075** | Ecommerce / Service | 平台 CRUD + Health（4 端点）| P1 | EXT-22 §3 + scope-freeze §2.3 | `ecommerce/index.vue` "平台管理" tab (`ecommerceApi.createPlatform/listPlatforms/updatePlatform`) | `service/EcommerceService.java:62-94` — `POST /platform` (`:65-72`) + `GET /platform/list` (`:75-80`) + `POST /platform/{id}` (`:83-89`) + `GET /platform/{id}/health` (`:92-94`) | `ecommerce_platform` | L0 | `createPlatform` 强制 `platform` 必填；`tenantId=0L` 默认（**⚠ 不隔离** — 同 Batch 1.1 I019 模式）；`updatePlatform` 用 `setId(id) + updateById`；health 走 adapter.healthCheck |
| **F076** | Ecommerce / Service | 商品 / 订单 sync（4 端点 + upsert）| P1 | EXT-22 §3 + scope-freeze §2.3 | `ecommerce/index.vue` "商品同步" + "订单同步" tab (`ecommerceApi.syncProducts/syncOrders/listProducts/listOrders`) | `service/EcommerceService.java` — `GET /product/list` (`:97-105`) + `POST /product/sync/{pid}` (`:108-150`) + `GET /order/list` (`:213-222`) + `POST /order/sync/{pid}` (`:225-289`) + 私有 `upsertProduct` (`:152-200`) + 私有 `upsertOrder` (`:291-372`) | `ecommerce_product` + `ecommerce_order` + `ecommerce_sync_log` | L0 | sync `@Transactional`；upsert by `(platform, external_product_id/external_order_id)`；写入 `ecommerce_sync_log(syncType=PRODUCT/ORDER, status=RUNNING→SUCCESS/PARTIAL/FAILED, successCount/failCount/durationMs)`；`persistTokenCache` 仅刷 `accessToken/tokenExpireAt` 不覆盖其他字段；订单字段映射兼容有赞 + 微店（`receiver_*` / `buyer_*`）|
| **F077** | Ecommerce / Webhook | YouzanWebhookController `/webhook/youzan`（**I027 反向**）| P0 | **EXT-22 §0.5 + §6 ⭐ Webhook 接收端** | (无前端入口；有赞后台 push) | `controller/YouzanWebhookController.java:76-203` — `@PostMapping("/webhook/youzan") @Transactional` | `ecommerce_platform` (read) + `webhook_msg_log` (write) + `ecommerce_order` (upsert) | L0 | **完整处理流程**（L83-200）：① ContentCachingRequestWrapper 读 rawBody（L85-95）② 反查 platform by `kdt_id`(L107-114) ③ `verifyWebhookSignature` 验签(L121) ④ msg_id 幂等去重(L137-149) ⑤ upsert `ecommerce_order`(L182-186) ⑥ 写 `webhook_msg_log`(L195-200)；**SoT §5 描述"未实现"反向** — 见 I027；`@Transactional` **未指定 rollbackFor** — 与 I009 模式同 — 见 I034 |
| **F078** | Ecommerce / Webhook | msg_id 幂等去重（`webhook_msg_log` 表）| P0 | **EXT-22 §0.5 + I030 反向** | (无前端入口；F077 链路) | `controller/YouzanWebhookController.java:136-149` — 重复 msg_id → 返回 `duplicate=true + previousStatus + logId` (HTTP 200) | `webhook_msg_log` (EXT-24 边界，**只引用**) | L0 | 查询 `selectOne(... eq(source, "YOUZAN") eq(msgId, msgId) limit 1)`；命中 → 返回"已处理（幂等）" + 既有 logId；**SoT §5 "没有 webhook 重放保护" 描述反向** — 见 I030；**webhook_msg_log 表归 EXT-24**（用户决策 #6 — Batch 1.3 只引用不重复审计）；⚠ **未加重放 nonce 校验**（仅查 msg_id 不查 timestamp / nonce 防止过期重放）— 见 I035 |
| **F079** | Ecommerce / 前端 | 3 tab + 添加平台对话框（**I028 部分反向**）| P1 | EXT-22 §3 + scope-freeze §2.3 | `ecommerce/index.vue` (`views/ecommerce/index.vue:1-95`) | (无新后端) — 全部对应 `EcommerceService` 端点 | — | L0 | **3 tab**：平台管理（`id/platform/shopName/shopId/lastSyncAt/status` el-table）+ 商品同步（`id/platformId/productId/productName/price/stock`）+ 订单同步（`id/platformId/orderNo/amount/status`）；添加对话框（`platform: WEIDIAN/YOUZAN/TAOBAO/DOUYIN` + `shopName` + `shopId`）；⚠ **缺 `appId/kdtId/signSecret` 字段 UI** — 与 SoT §5 一致 — 见 I028；⚠ 缺 `clientId/clientSecret/webhookUrl` 等配置字段 |
| **F080** | Frontend API + DB 迁移 | ecommerce.ts + google-maps.ts API 封装 + EXT-17/22/24 DDL | P1 | EXT-22 §1 + §3 + scope-freeze §2.3 | `frontend/.../api/ecommerce.ts:1-19` + `google-maps.ts:1-18` | DDL `db/sql/migrate_ext_17_google_maps.sql` + `migrate_ext_22_google_maps_place_details.sql` + `migrate_ext_24_webhook_msg_log.sql`（EXT-24 边界引用）| `google_maps_config` / `google_maps_geocode_log` / `ecommerce_platform` / `webhook_msg_log` | L0 / **⚠ MISSING（dev DB 未跑迁移）** | `ecommerceApi` 8 端点（`createPlatform/listPlatforms/updatePlatform/listProducts/syncProducts/listOrders/syncOrders/dashboard`）；`googleMapsApi` 6 端点（`saveConfig/getConfig/geocodeStore/listStores/getPlaceDetails/getMemberHeatmap`）；DDL 三个迁移脚本 — **Phase 1 期间跑迁移即解**（用户决策 #3） |

### 1.3.3 Feature 数量统计

| 分类 | 数量 | Feature IDs |
|---|---|---|
| Google Maps 实体 + Client + Config | 4 | F056-F059 |
| Google Maps Service（含 /config /geocode /place/details /geocode/batch /stores /heatmap /geocode/log）| 5 | F060-F064 |
| Google Maps 前端 | 1 | F065 |
| Ecommerce 实体（EcommercePlatform + Order + Product + SyncLog）| 2 | F066-F067 |
| Ecommerce Adapter 接口 + 鉴权 + 拉取 + 健康 | 3 | F068-F070 |
| Ecommerce Adapter 签名算法 + 孤儿工具 | 2 | F071-F072 |
| Ecommerce Adapter Mock + Router | 2 | F073-F074 |
| Ecommerce Service（CRUD + Sync + Dashboard）| 2 | F075-F076 |
| Ecommerce Webhook（接收端 + 幂等）| 2 | F077-F078 |
| Ecommerce 前端 | 1 | F079 |
| Frontend API + DB 迁移（合并）| 1 | F080 |
| **合计** | **25** | F056-F080 |

### 1.3.4 Phase 1 Issue 清单（**不进 Phase 2 前必须决策**）

| Issue ID | 类别 | 描述 | 影响 Feature | 建议处理 |
|---|---|---|---|---|
| **ISSUE-I026** | **CONFLICT**（中立标） | SoT §5 "`verifyWebhookSignature` 是孤儿代码 — `YouzanEcommerceAdapter.verifyWebhookSignature` 已写好算法，但 `EcommerceService` 没有 `/webhook/youzan` 接收端" — **与代码反向**。`YouzanWebhookController:121` 已调用 `YouzanEcommerceAdapter.verifyWebhookSignature(pf.getSignSecret(), rawBody, signature)`；且 `YouzanWebhookController` 是**独立** controller（不在 `EcommerceService` 内）。沿用 Batch 1.1 I001 / Batch 1.2 I018 模式 — **中立标**，Fix Phase = Post-Audit 人类决策（3 选项：改 SoT / 改代码 / 维持现状） | F071 / F077 | Phase 2：jshell 单元测试验证签名算法（3 组 payload：正常 / 篡改 body / 篡改 signature）；**不改 SoT / 不改代码** |
| **ISSUE-I027** | **CONFLICT**（中立标） | SoT §5 "webhook 接收端缺失 — `EcommerceService` 没有 `/webhook/youzan` 路由" — **与代码反向**。`YouzanWebhookController.HandleYouzan` (`controller/YouzanWebhookController.java:76-203`) **已完整实现**：ContentCachingRequestWrapper 读 rawBody + 反查 platform + 验签 + msg_id 幂等去重 + upsert order + 写 webhook_msg_log；**`@RequestMapping` 是 `controller/YouzanWebhookController.java`，不是 `EcommerceService`**（SoT 描述的是 service 内嵌路由，与实际分离式 controller 架构不符） | F077 / F078 | **中立标**，Fix Phase = Post-Audit 人类决策（3 选项）|
| **ISSUE-I028** | **PARTIAL-NON-BLOCKER**（Frontend Shadow）| SoT §5 "前端缺 UI：`google-maps/index.vue` 不显示 `enablePlaceDetails` 开关；`ecommerce/index.vue` 不显示 `appId/kdtId/signSecret` 字段" — **部分反向**。**① google-maps/index.vue `enablePlaceDetails` 开关 UI 已实现**（L13-31，el-switch + 计费警告 el-alert；用户决策前需 Phase 2 确认是否真保存到后端）；**② ecommerce/index.vue 缺 `appId/kdtId/signSecret` 字段 UI 确认一致**（添加对话框 L60-77 仅含 platform/shopName/shopId）| F065 / F079 | 决策：① 接受 ecommerce 缺 UI 现状（SQL 直写，SoT §5 一致）— 不修复；② Phase 2 验证 google-maps enablePlaceDetails 开关实际保存到后端（当前 `configForm` 不传 `defaultLanguage/defaultRegion/quotaDaily/enabled`，仅传 apiKey/defaultZoom/enablePlaceDetails）|
| **ISSUE-I029** | **MISSING（DB）** | SoT §8.1 + 用户 prompt 第 7 节假设 "`google_maps_config` 表缺失" — **实际是 preflight 假设 vs 代码漂移**（DDL 写了 + dev DB 迁移未跑 = 表在 DB 不存在）。`migrate_ext_17_google_maps.sql:9` 已用 `DROP + CREATE` 建表；`migrate_ext_22_google_maps_place_details.sql:6` 用 `ALTER ADD COLUMN IF NOT EXISTS enable_place_details`。**沿用 Batch 1.1 I017 + I002 模式**：跑迁移即解（I002 / I017 MISSING 模式）| F056 / F057 / F060 | Phase 1 期间执行：① grep `db-snapshot.sql` 验证表不存在；② 跑 `migrate_ext_17_google_maps.sql` + `migrate_ext_22_google_maps_place_details.sql`；③ 写 `audit/evidence/F056-google-maps-config/db-evidence.sql` 验证 `DESCRIBE google_maps_config` + `DESCRIBE ecommerce_platform` 显示 3 新列 |
| **ISSUE-I030** | **CONFLICT**（中立标）| SoT §5 "没有 webhook 重放保护 — `verifyWebhookSignature` 只验签，不记录已处理 msg_id" — **与代码反向**。`YouzanWebhookController.java:136-149` **实际加了 `webhook_msg_log` 幂等去重**：重复 msg_id → 查既有 log 返回 `duplicate=true + previousStatus + logId`；且 `webhook_msg_log` 表已通过 `migrate_ext_24_webhook_msg_log.sql:8` 创建（**注意来自 EXT-24，不是 EXT-22**）| F078 | **中立标**，Fix Phase = Post-Audit 人类决策（3 选项）|
| **ISSUE-I031** | **PARTIAL-NON-BLOCKER**（审计缺失）| `GoogleMapsService.placeDetails` (`service/GoogleMapsService.java:191-232`) **未写 `google_maps_geocode_log`**（仅 `/geocode` (L159-170) + `/geocode/batch` (L225-237) 写日志）— Place Details 是 GCP 计费接口但**无计费审计日志** → 与 GCP 后台用量对账困难 | F062 | 决策：① 接受现状（仅日志依赖 GCP 后台）；② Phase 2 期间加日志写入（F062 改造范围外，仅标注）|
| **ISSUE-I032** | **PARTIAL-NON-BLOCKER**（审计缺失）| `GoogleMapsService.geocodeBatch` (`service/GoogleMapsService.java:259-318`) admin-only 但**无 audit log**（"谁在何时调了多少条"无审计）— SoT §5 已知问题一致 | F063 | 决策：① 接受现状；② 建议后续加 `geocode_batch_log` 表（**本批次不实现**，Phase 7 Post-Audit 决策）|
| **ISSUE-I033** | **BACKLOG-001**（孤儿工具）| `YouzanEcommerceAdapter.buildWebhookUrl` (`ecommerce/YouzanEcommerceAdapter.java:106-114`) 声明 + 实现，无任何调用方 — **Phase 1 grep 全 backend 仅声明 + 实现，无任何调用方**；与 SoT §5 "buildWebhookUrl 是孤儿工具" 描述一致（非漂移）| F072 | 决策：① 删除；② 留作未来"注册到有赞后台"的工具（**Phase 1 不删，Phase 7 Post-Audit 决定**）|
| **ISSUE-I034** | **PARTIAL-NON-BLOCKER**（事务）| `YouzanWebhookController.HandleYouzan` (`controller/YouzanWebhookController.java:76`) 加 `@Transactional` 但**未指定 `rollbackFor`** → 与 I009 模式同（默认仅 RuntimeException 回滚）；HTTP 异常 / 受检异常可能不回滚 | F077 | 决策：① 接受现状（webhook 写库失败概率低）；② 加 `rollbackFor = Exception.class`（本批次不改）|
| **ISSUE-I035** | **PARTIAL-NON-BLOCKER**（重放）| `webhook_msg_log` 幂等去重（F078）**仅查 msg_id 不查 timestamp / nonce** → 理论上可重放（同一 msg_id 永久幂等）；SoT §5 已知问题一致 | F078 | 决策：① 接受现状；② 加 `webhook_msg_log.created_at > now() - 1h` 时间窗（**本批次不改**）|

### 1.3.5 与 EXT-* Operational Notes 的冲突点（SoT 比对）

> 按 `project-audit.mdc` 规则 11：审计结论与 EXT-* Operational Notes 冲突时，**以 EXT-* 为准并标 ISSUE**。
> 本次审计发现 **EXT-* 文档本身与代码不一致**（同 Batch 1.1 I001 / Batch 1.2 I018 模式）。沿用 scope-freeze §五.2 修正分类原则：
> - 表 MISSING 类（DDL 写了但 DB 没建）→ I002 / I017 模式（跑迁移即解，Phase 1 期间执行）
> - SoT vs 代码 CONFLICT 类（webhook 代码 vs notes 描述不一致）→ I001 / I018 模式（中立标，Fix Phase = Post-Audit 人类决策）
> - buildWebhookUrl 孤儿工具 → BACKLOG-001（沿用 I013 dead code 模式）

| EXT-* 章节 | 文档声称 | 代码事实 | 漂移类型 | 处理 |
|---|---|---|---|---|
| §0.5 webhook 签名算法 | `md5(signSecret + rawBody)` 全小写 | `YouzanEcommerceAdapter.verifyWebhookSignature` (`:146-168`) — **一致** | (无漂移) | OK（验证）|
| §0.6 buildWebhookUrl | `https://your-domain/webhook/youzan?kdt_id={kdtId}` | `YouzanEcommerceAdapter.buildWebhookUrl` (`:106-114`) — **一致**；**无调用方** | (无漂移) | BACKLOG-001 / I033 |
| §5 已知问题 #1 | "`verifyWebhookSignature` 是孤儿代码" | `YouzanWebhookController:121` **已调用** | **CONFLICT**（反向）| **I026 中立标** |
| §5 已知问题 #2 | "webhook 接收端缺失（`EcommerceService` 没有路由）" | `YouzanWebhookController` **已完整实现**（不在 `EcommerceService` 内）| **CONFLICT**（反向）| **I027 中立标** |
| §5 已知问题 #3 | "前端缺 UI：google-maps 不显示 enablePlaceDetails 开关" | `google-maps/index.vue:13-31` **el-switch + 计费警告已实现** | **CONFLICT**（部分反向）| **I028 PARTIAL-NON-BLOCKER** |
| §5 已知问题 #3 | "前端缺 UI：ecommerce 不显示 appId/kdtId/signSecret 字段" | `ecommerce/index.vue:60-77` 添加对话框**仅含 platform/shopName/shopId** | (无漂移，确认) | **I028 PARTIAL-NON-BLOCKER** |
| §5 已知问题 #4 | "没有 webhook 重放保护" | `YouzanWebhookController:136-149` **已加 `webhook_msg_log` 幂等去重** | **CONFLICT**（反向）| **I030 中立标** |
| §5 已知问题 #5 | "Place Details 无降级" | `GoogleMapsService.placeDetails` (`:202-217`) 三段门禁：未配置 → NOT_FOUND + `enablePlaceDetails != 1` → PERMISSION_DENIED；**无降级到 mock**（仅 PERMISSION_DENIED）| (无漂移，确认) | OK |
| §5 已知问题 #6 | "批量 geocode 无 audit log" | `GoogleMapsService.geocodeBatch` admin-only 但无 batch_audit_log 表 | (无漂移，确认) | **I032 PARTIAL-NON-BLOCKER** |
| §5 已知问题 #7 | "SQL 缺 `CREATE TABLE IF NOT EXISTS`" | `migrate_ext_17` 用 DROP+CREATE（**无 IF NOT EXISTS**）；`migrate_ext_22` 用 ADD COLUMN IF NOT EXISTS（MySQL 8.0.29+）| (无漂移，确认) | OK |
| §5 已知问题 #8 | "`getConfig` 端点泄露 API key（仅遮蔽后 4 位）" | `GoogleMapsService.java:108` `apiKey.substring(Math.max(0, apiKey.length() - 4))` | (无漂移，确认) | OK（用户决策 #9：仅标注）|
| §5 已知问题 #9 | "Place Details 字段全拿（11 字段 → 三 SKU）" | `GoogleMapsGeocodingClient.buildPlaceDetailsUrl` (`:67-71`) 11 字段全拿 | (无漂移，确认) | OK（已知约束）|
| §0.1 Google Maps Place Details 计费 | "Basic Data $17 / Contact Data $3 / Atmosphere Data $5 / 默认 enable_place_details = 0" | `GoogleMapsConfig.java:43` 字段 + `GoogleMapsService.java:215-217` 三段门禁；`saveConfig` L86 默认 `enablePlaceDetails = 0` | (无漂移，确认) | OK |
| §0.2 enable_place_details 写入策略 | "前端无对应开关 UI" | `google-maps/index.vue:13-31` el-switch **已实现**（**与 §5 #3 描述反向**）| **CONFLICT**（部分反向）| **I028 PARTIAL-NON-BLOCKER** |
| §0.3 placeDetails 行为 | "门禁顺序 1) 配置存在 2) enablePlaceDetails=1 3) 调 GCP" | `GoogleMapsService.java:202-229` **三段门禁完全一致** | (无漂移，确认) | OK |
| §0.4 有赞 appKey ≠ appId | "appKey = OAuth2 client_id；appId = 有赞开放平台 AppID；kdtId = 店铺 ID；signSecret = webhook 验签" | `EcommercePlatform.java` 4 字段独立（L13/L15/L19/L21/L23）+ SoT 一致 | (无漂移，确认) | OK |
| §0.5 webhook 签名算法 | `md5(signSecret + rawBody)` 全小写 + equalsIgnoreCase | `YouzanEcommerceAdapter.verifyWebhookSignature` (`:146-168`) **完全一致** | (无漂移，确认) | OK |
| §0.7 跨租户访问 | "`dataScope != ALL` → FORBIDDEN（仅 CRM 集成约束）"| `EcommerceService` / `YouzanWebhookController` / `GoogleMapsService` **未声明任何 dataScope 校验**（webhook 是 push 端无需校验；其它端点未隔离）| (无漂移，确认) | ⚠ 跨租户隔离缺失（**未标独立 Issue** — Phase 2 静态扫描时再确认）|
| §1 Touchpoints | 列 `entity/EcommercePlatform.java` 等 | **完全一致**（git status 中未出现但已在 HEAD 中）| (无漂移，确认) | OK |
| §3 Endpoints | Google Maps 5 个 + Ecommerce 6 个 + webhook `/webhook/youzan` | 全部已实现（`GoogleMapsService.java` 9 端点 + `EcommerceService.java` 10 端点 + `YouzanWebhookController` 1 端点）| (无漂移，确认) | OK |
| §4 字段映射 | `google_maps_config.enable_place_details` + `ecommerce_platform.app_id/kdt_id/sign_secret` + `google_maps_geocode_log.place_id` | `migrate_ext_22_google_maps_place_details.sql:6/11` **完全一致** | (无漂移，确认) | OK |

### 1.3.6 未确认项（标记"未确认"，禁止猜测）

| # | 项 | 当前信息 | 待补来源 |
|---|---|---|---|
| 1 | dev DB 中 `google_maps_config` / `google_maps_geocode_log` 表是否实际存在 | DDL 在 `migrate_ext_17_google_maps.sql` 已写；`db-snapshot.sql` 是否含待验证；用户决策 #3：Phase 1 期间跑迁移即解 | Phase 1 跑迁移 + `DESCRIBE google_maps_config` + `DESCRIBE google_maps_geocode_log` |
| 2 | dev DB 中 `ecommerce_platform` 3 新列（`app_id/kdt_id/sign_secret`）是否存在 | DDL `migrate_ext_22_google_maps_place_details.sql:11` 已写；`db-snapshot.sql` 是否含待验证 | Phase 1 跑迁移 + `DESCRIBE ecommerce_platform` |
| 3 | `webhook_msg_log` 表（**EXT-24 边界，Batch 1.3 只引用**）| DDL `migrate_ext_24_webhook_msg_log.sql:8` 已写；`db-snapshot.sql` 是否含待验证 | Phase 2 实际查询（归 EXT-24 审计范围）|
| 4 | dev DB 中 `ecommerce_order` 表是否存在 | DDL `create_missing_tables_b4b5.sql:411` 已写 | Phase 2 实际查询 |
| 5 | `EcommercePlatform` 实体 L19/L21/L23 三字段与 DB 列对齐 | DDL `migrate_ext_22:11` 含 `app_id/kdt_id/sign_secret`；实体 `EcommercePlatform.java:19/21/23` 含对应 — **静态一致** | OK |
| 6 | `GoogleMapsConfig.enablePlaceDetails` 实体字段 vs DB `enable_place_details` 列 | DDL `migrate_ext_22:6` + 实体 L43 — **静态一致** | OK |
| 7 | 前端 `enablePlaceDetails` 开关实际保存到后端 | `google-maps/index.vue:148-150` `loadConfig` + `saveConfig`；**但 `configForm` 仅含 apiKey/defaultZoom/enablePlaceDetails，不含 defaultLanguage/defaultRegion/quotaDaily/enabled**（更新时可能把这些字段覆盖为 null/默认值）| Phase 2 实际调用验证 |
| 8 | 有赞真实 webhook 推送链路（用户决策 #2 push_url 未注册）| 仅 Mock 推送样本（Phase 5 降级方案）| Phase 5 用户手动推送 |
| 9 | GCP Places API 真实调用（用户决策 #1 凭据缺失）| 仅 Mock 降级 + jshell 单元测试 | Phase 5 用户手动注入 GCP API Key |
| 10 | `BizCode.FORBIDDEN(403)` / `NOT_FOUND(404)` / `PERMISSION_DENIED` / `BAD_REQUEST` / `UNAUTHORIZED` / `SYSTEM_ERROR` 枚举值 | `exception/BizCode.java` 已确认（沿用 Batch 1.1 F030 模板）| OK |
| 11 | `EcommerceService.createPlatform` 强制 `tenantId=0L` 是否有意（跨租户共享 platform）| L72 静态确认；与 Batch 1.1 I019 命名异味同模式 | OK（已知约束）|
| 12 | `YouzanWebhookController` 是 controller/ 包，与 SoT §5 假设"在 EcommerceService 内"架构不同 | `controller/YouzanWebhookController.java` 已确认 | OK |

### 1.3.7 与 preflight §八 已知约束的交叉引用

| preflight §八 | Phase 1 处理 | 对应 Feature | 对应 Issue |
|---|---|---|---|
| §八.1 `google_maps_config` 表存在性 | F056/F057/F060 标 **MISSING**；Phase 1 期间跑 `migrate_ext_17` + `migrate_ext_22` 即解 | F056 / F057 / F060 | **I029**（I002 / I017 MISSING 模式）|
| §八.2 有赞 webhook 接收端 | F077/F078 标 "已实现但 SoT 未更新"；中立标 CONFLICT | F077 / F078 | **I027**（I001 / I018 CONFLICT 模式）|
| §八.3 `EcommercePlatform` 字段变更 | F066 标字段漂移风险；entity / migration / SoT 对齐确认 | F066 | OK（SoT 一致）|
| §八.5 EXT-22 Operational Notes SoT | F056-F080 全部以 SoT 为骨架；§5 已知问题逐项比对（§1.3.5）| F056-F080 | I026-I030 + I028 + I033 + I034 + I035 |
| §八.6 有赞 webhook 签名算法 | F071 静态确认 + 算法一致；Phase 5 jshell 单元测试（3 组 payload） | F071 | OK（验证）|
| §八.7 GCP Places API 计费闸门 | F062 标三段门禁（CFG → enablePlaceDetails=1 → 调 GCP）；SoT 一致 | F062 | OK + **I031**（Place Details 未写日志）|
| §八.8 受保护页面零风险 | Phase 2 静态扫描确认 EXT-22 与 `/dashboard` / `/mh/visual` / `/mh/consent` 后端接口无间接依赖 | (待 Phase 2) | (待登记) |
| §八.11 共用 entity/table 边界 | F066/F067 共用实体边界明确：`webhook_msg_log` 归 EXT-24（只引用不重审）；`ecommerce_platform` / `ecommerce_order` 按 EXT-22 视角 | F066 / F067 / F078 | (用户决策 #6) |

### 1.3.8 跨 EXT-* 引用规则（用户决策 #6）

| 共享资源 | 所属 EXT-* | Batch 1.3 Feature 视角 | 备注 |
|---|---|---|---|
| `EcommercePlatform` entity + `ecommerce_platform` 表 | EXT-22 (webhook 接收端反查) + EXT-16 (marketplace CRUD) | F066 标字段漂移（EXT-22 视角）；F075 CRUD 走 EXT-22 视角 | Phase 1 期间明确边界；Feature ID 不加 EXT 前缀 |
| `EcommerceOrder` entity + `ecommerce_order` 表 | EXT-22 (webhook upsert) + EXT-16 (order sync) | F067 实体（共用）；F076 sync (EXT-22 视角)；F077 webhook upsert (EXT-22 视角) | 同上 |
| `WebhookMsgLog` entity + `webhook_msg_log` 表 | **EXT-24 (CRM 集成) + EXT-22 (webhook 幂等去重引用)** | F078 引用（**只引用不重复审计**） | **EXT-24 边界**；Batch 1.3 只引用不重审 |

---

**Phase 1 产出（Batch 1.3）完成。本批次 25 个 Feature 已落 `audit/feature-inventory.md`，与 scope-freeze §3.3 预期 20-25 完全一致。**

**已知约束标记汇总**（KNOW-CONSTRAINT，强制关注）：
- ⚠ **MISSING（DB）**：`google_maps_config` / `google_maps_geocode_log` 表 DDL 已就位但 dev DB 未跑迁移（DDL 在 `migrate_ext_17_google_maps.sql` + `migrate_ext_22` — 触发 F056/F057/F060 全部 → **强制 MISSING / PARTIAL-BLOCKER**）；`ecommerce_platform` 3 新列（`app_id/kdt_id/sign_secret`）ALTER 在 `migrate_ext_22:11` — Phase 1 期间跑迁移即解
- ⚠ **CONFLICT（中立标）**：webhook 接收端（I027）+ verifyWebhookSignature 孤儿代码（I026）+ webhook 重放保护（I030）— 3 项与 SoT 方向反向 → Fix Phase = Post-Audit 人类决策
- ⚠ **PARTIAL-NON-BLOCKER**：前端 UI 缺字段（I028）/ Place Details 无日志（I031）/ 批量 geocode 无 audit log（I032）/ 事务未指定 rollbackFor（I034）/ 重放无时间窗（I035）
- ⚠ **BACKLOG-001**：`YouzanEcommerceAdapter.buildWebhookUrl` 孤儿工具（I033）— 沿用 I013 dead code 模式，Phase 7 Post-Audit 决定

**待用户决策**（不进 Phase 2 前）：

1. **ISSUE-I026**：`verifyWebhookSignature` 孤儿代码 CONFLICT 中立标 — 接受？
2. **ISSUE-I027**：webhook 接收端 CONFLICT 中立标 — 接受？
3. **ISSUE-I028**：前端 UI 缺字段（ecommerce 缺 appId/kdtId/signSecret；google-maps enablePlaceDetails 已实现但 configForm 缺字段）— 接受现状？
4. **ISSUE-I029**：Phase 1 期间跑 `migrate_ext_17_google_maps.sql` + `migrate_ext_22_google_maps_place_details.sql` 迁移？实际执行迁移并写 `audit/evidence/F056-google-maps-config/db-evidence.sql`？
5. **ISSUE-I030**：webhook 重放保护 CONFLICT 中立标 — 接受？
6. **ISSUE-I031**：Place Details 未写日志 — 接受现状 / Phase 2 加日志？
7. **ISSUE-I032**：批量 geocode 无 audit log — 接受现状 / 后续加 `geocode_batch_log` 表？
8. **ISSUE-I033**：`buildWebhookUrl` 孤儿工具 BACKLOG-001 — 接受 / Phase 7 决定删/留？
9. **ISSUE-I034**：webhook 事务未指定 rollbackFor — 接受现状 / 加 `rollbackFor = Exception.class`？
10. **ISSUE-I035**：webhook 幂等仅查 msg_id 不查时间窗 — 接受现状 / 加时间窗？


---

## Batch 1.4 — EXT-19 多语言预约确认（v3 第四批）

**范围来源**：`audit/baseline/scope-freeze-batch-1.4.md` §2.3 + `audit/baseline/preflight-batch-1.4.md` §七
**EXT-* Operational Notes**：`.cursor/rules/ext-19-operational-notes.mdc`（**SoT**，**Phase 1 全文核实 1 项反向 + 1 项缺失 + 3 项 Shadow**，详见 §1.4.4 / §1.4.5）
**Batch 起点**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2/1.3 freeze commit 沿用）
**Feature 编号策略**：接续 Batch 1.3 的 F056-F080 → 本批次 **F081-F110（30 Features）**

### 1.4.1 范围涉及文件（已静态扫描）

| 类别 | 文件 | 行数 | 备注 |
|---|---|---|---|
| Entity | `backend/src/main/java/com/meiyetec/entity/OverseasBooking.java` | 31 | `miniProgramOpenId: String`（L21，EXT-19 新增）；26 列 + `@TableField(fill=...)` 自动填充 createdAt/updatedAt |
| Mapper | `backend/src/main/java/com/meiyetec/mapper/OverseasBookingMapper.java` | 6 | `@Mapper extends BaseMapper<OverseasBooking>` |
| booking 核心 | `backend/src/main/java/com/meiyetec/booking/BookingConfirmationService.java` | 187 | `sendConfirmation(booking)` (`:69-115`) + `preview` (`:120-122`)；3 渠道路由（SMS/EMAIL/MINI_PROGRAM，`IN_APP` / `WECHAT_OA` 未触达）；**⚠ I008 模式**：异常在 catch 内吞掉（SoT §0.3） |
| booking 模板 | `backend/src/main/java/com/meiyetec/booking/BookingConfirmationTemplate.java` | 33 | 8 字段 DTO（resolvedLocale/usedFallback/subject/sms/miniProgram/title/emailSubject/emailBody） |
| booking 解析器 | `backend/src/main/java/com/meiyetec/booking/BookingConfirmationTemplateResolver.java` | 151 | `warmup()` (`:36-44`，6 locale: zh-CN/zh-TW/en-US/ja-JP/ko-KR/fr-FR) + `resolve(locale, vars)` (`:64-111`) + locale 回退链（`xx-YY → xx → en`）|
| booking 结果 | `backend/src/main/java/com/meiyetec/booking/BookingConfirmationResult.java` | 33 | `overallSuccess` + `channelResults: Map<channel, {success, externalMsgId, errorMsg}>` + `putChannel()` 累加 success 自动设 overallSuccess |
| booking 查询 | `backend/src/main/java/com/meiyetec/booking/StoreLookupService.java` | 78 | `lookup(storeId)` (`:54-76`) + `lookupStoreName/lookupStorePhone` + `invalidate(storeId)`；5 分钟缓存（`CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)`）|
| channel 接口 | `backend/src/main/java/com/meiyetec/service/channel/ChannelSender.java` | 39 | 3 方法（`channelType/send/healthCheck`）+ `SendResult {success, externalMsgId, errorMsg}` 内嵌类 |
| channel 短信 | `backend/src/main/java/com/meiyetec/service/channel/SmsSender.java` | 196 | `channelType() = "SMS"` + 阿里云 `HttpUtil.sendAliYunSms` + `render(var)` 占位符替换（`${key}` → vars[key]）|
| channel 邮件 | `backend/src/main/java/com/meiyetec/service/channel/EmailSender.java` | 217 | `channelType() = "EMAIL"` + 三模式（租户 cfg / 全局 spring.mail.* / demo）；**日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode`**（SoT §0.2 完全一致）|
| channel 小程序 | `backend/src/main/java/com/meiyetec/service/channel/MiniProgramSender.java` | 209 | `channelType() = "MINI_PROGRAM"` + AccessToken 内存缓存（`TOKEN_CACHE_MS = 7100_000L`，提前 10 分钟刷新）；errcode=40001 自动清缓存 |
| channel 站内信 | `backend/src/main/java/com/meiyetec/service/channel/InAppSender.java` | 153 | `channelType() = "IN_APP"` + 写 `member_message` 表 + `@Transactional(rollbackFor = Exception.class)`；`receiver` 解析 `memberId[-name]` 格式 |
| channel 公众号 | `backend/src/main/java/com/meiyetec/service/channel/WechatOfficialAccountSender.java` | 233 | `channelType() = "WECHAT_OFFICIAL_ACCOUNT"`（**注：非 `WECHAT_OA`**）— SoT §1 描述"WECHAT_OA" 与代码实际不一致（**I039 候选**）；公众号模板消息（`first/keyword1..N`）|
| channel 分发 | `backend/src/main/java/com/meiyetec/service/channel/ChannelDispatcher.java` | 91 | `getSender(channelType)` 路由 + `healthCheckAll()` 仅更新默认租户(tenant_id=1)（**T07-D4 例外声明**）|
| 业务入口 | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java` | 350+ | **⚠ `@RestController` 命名异味**（与 Batch 1.1 I004 / 1.2 I019 / 1.3 I029 同模式）— 文件名是 `*Service` 但实际 `@RestController @RequestMapping("/intl-booking")`；13 端点（OAuth × 2 + callback × 2 + channels + pull + list + get + status + send-confirmation + webhook/google + webhook/instagram + convert + confirmation-template）|
| i18n 模板 | `backend/src/main/resources/i18n/template/zh-CN.json` | 22 | 简体中文（subject + sms + miniProgram + email 4 段）|
| i18n 模板 | `backend/src/main/resources/i18n/template/zh-TW.json` | 22 | 繁体中文 |
| i18n 模板 | `backend/src/main/resources/i18n/template/en-US.json` | 22 | 英文 |
| i18n 模板 | `backend/src/main/resources/i18n/template/ja-JP.json` | 22 | 日文 |
| i18n 模板 | `backend/src/main/resources/i18n/template/ko-KR.json` | 22 | 韩文 |
| i18n 模板 | `backend/src/main/resources/i18n/template/fr-FR.json` | 22 | 法文 |
| SQL 迁移 | `backend/src/main/resources/db/sql/migrate_ext_19_mini_program_openid.sql` | 32 | `CREATE TABLE IF NOT EXISTS overseas_booking` (`:9-32`) + `ALTER TABLE overseas_booking ADD COLUMN IF NOT EXISTS mini_program_open_id` (`:36`)；MySQL 8.0.29+ |
| 测试 | `backend/src/test/java/com/meiyetec/booking/BookingConfirmationServiceTest.java` | 200+ | StubSmsSender / StubEmailSender 桩 + Mockito mock StoreLookupService + resolver；渠道路由 + 聚合结果 + null 安全 |
| 测试 | `backend/src/test/java/com/meiyetec/booking/BookingConfirmationTemplateResolverTest.java` | 200+ | locale 归一化（zh_CN → zh-CN）+ locale 回退链 + 3 locale 加载 + 占位符 ${} 渲染 + 未注册 locale 降级 en-US |
| 测试 | `backend/src/test/java/com/meiyetec/service/channel/EmailSenderTest.java` | 88 | channelType=EMAIL + receiver 邮箱校验 + demo 模式（mapper null 时仍 success）+ healthCheck=false |
| 测试 | `backend/src/test/java/com/meiyetec/service/channel/WechatOfficialAccountSenderTest.java` | — | **Phase 1 新发现**（preflight §7.2 仅列 3 测试；实际有 4 测试）|
| 前端页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | 150+ | 2 tab（"渠道连接" + "国际预约"）+ 3 对话框（Google OAuth / Instagram OAuth / 模板预览）；**⚠ 缺 `autoSend=false` UI + 状态确认按钮文案无"确认即发送通知"提示**（I040 + I041 候选）|
| 前端 API | `frontend/meiyetec-web/src/api/intl-booking.ts` | 31 | 12 端点封装（OAuth × 4 + listChannels + pullBookings + listBookings + getBooking + updateStatus + convertBooking + confirmationTemplate）；**⚠ 缺 `sendConfirmation` 方法**（后端有 `/intl-booking/{id}/send-confirmation` 但前端无对应入口 — I040 + I042 候选）|
| SoT | `.cursor/rules/ext-19-operational-notes.mdc` | — | EXT-19 Operational Notes（§0-§6 完整）|

### 1.4.2 Feature 清单（30 项）

> 列：Feature ID / Module / 功能点 / Priority / 需求来源 / 前端入口 / 后端入口 / DB表 / 静态可达性 / 备注
> KNOWN-CONSTRAINT 标签说明（沿用 Batch 1.1/1.2/1.3）：
> - **⚠ MISSING**：表 DDL 已写但 dev DB 未跑迁移（I002/I017/I029 模式 — Phase 1 不跑，Post-Audit 统一跑）
> - **⚠ UNVERIFIED**：真实凭据缺失（WeChat appId/appSecret / SMTP password），Mock 降级
> - **⚠ Shadow**：SoT 描述 vs 前端实现缺失（沿用 I006/I007 模式）
> - **⚠ PARTIAL-NON-BLOCKER**：catch 吞异常（I008 模式）/ rollbackFor 缺失（I009 模式）/ 命名异味

| Feature ID | Module | 功能点 | Priority | 需求来源 | 前端入口 | 后端入口 | DB表 | 静态可达性 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| **F081** | 海外预约 / Entity | OverseasBooking 实体（含 `miniProgramOpenId`）| P2 | EXT-19 §1 + scope-freeze §2.3 | (前端通过 API 间接) | `entity/OverseasBooking.java:11-31` | `overseas_booking` | L0 / **⚠ MISSING（表）** | 26 字段：`id/tenantId/bookingNo/source/externalId/customerName/customerPhone/customerEmail/customerLocale/miniProgramOpenId/serviceId/serviceName/staffId/storeId/bookingDate/bookingTime/notes/status/ipAddress/userAgent/rawPayload/convertedMemberId/convertedOrderId/createdAt/updatedAt`；`miniProgramOpenId` 在 L21（SoT §0 提及）；uk 无（仅索引）；**Phase 1 不跑迁移（用户决策 #3 第三次强调），Phase 5 仅 MySQL MCP 验证缺失**（I002/I017/I029 模式）|
| **F082** | 海外预约 / DB | overseas_booking DDL（建表 + ALTER）| P2 | EXT-19 §0.5 + scope-freeze §2.3 | (无前端入口) | DDL `db/sql/migrate_ext_19_mini_program_openid.sql:9-32`（CREATE TABLE IF NOT EXISTS）+ `:36`（ALTER ADD COLUMN IF NOT EXISTS mini_program_open_id）| `overseas_booking` | L0（DDL 存在） / **⚠ MISSING（db-snapshot.sql 0 匹配已确认）** | 4 索引（`idx_tenant_status/store/source/created`）；utf8mb4_bin；MySQL 8.0.29+（`ADD COLUMN IF NOT EXISTS`）；**ddl-snapshot Phase 1 grep 确认 0 匹配 → 跑迁移即解**（统一 Post-Audit）|
| **F083** | 预约确认 / Service | BookingConfirmationService 核心发送 | P0 | **EXT-19 §0.3 + §0.4 ⭐ 核心入口** | (间接通过 F097 status 自动触发 + F095 send-confirmation 手动触发) | `service/BookingConfirmationService.java:69-115` `sendConfirmation(OverseasBooking)` | `overseas_booking`（间接 `storeName/storePhone` 来自 `pos_store` 经 F087 缓存）| L0 | 步骤：① buildVars 构造占位符 ② resolver.resolve 渲染模板 ③ 按可用渠道路由（SMS/EMAIL/MINI_PROGRAM）④ 聚合 `BookingConfirmationResult`；任一渠道 success → `overallSuccess=true`；**⚠ I008 模式**：F097 status 调用时异常在 try/catch 内（SoT §0.3 "发送异常不会回滚状态变更" — 一致设计）|
| **F084** | 预约确认 / i18n | BookingConfirmationTemplateResolver（6 locale + warmup + fallback）| P0 | EXT-19 §0.5 + §1 + §4 | (间接通过 F083) | `booking/BookingConfirmationTemplateResolver.java:64-111` `resolve(locale, vars)` + `:36-44` `warmup()` 启动期预热 6 locale + `:114-126` `resolveWithFallback`（`xx-YY → xx → en-US`）+ `:130-138` `normalize`（trim + lang 小写 / country 大写）| (仅 classpath 资源 `i18n/template/*.json`) | L0 | 6 locale：`zh-CN/zh-TW/en-US/ja-JP/ko-KR/fr-FR`（**与 Operational Notes §1 完全一致**）；复用 `SmsSender.render(template, vars)` 占位符替换；空/null locale → DEFAULT_LOCALE="en-US"；**6 locale JSON 内容完整核对通过**（§1.4.6 详细交叉）|
| **F085** | 预约确认 / DTO | BookingConfirmationTemplate 渲染结果 | P2 | EXT-19 §1 | (无前端入口；由 F083 + F084 构造) | `booking/BookingConfirmationTemplate.java:14-32` | — | L0 | 8 字段：`resolvedLocale/usedFallback/subject/sms/miniProgram(title+keyword1..N)/miniProgramTitle/emailSubject/emailBody`；`miniProgram` 是 `LinkedHashMap<String,String>`（保留顺序）|
| **F086** | 预约确认 / DTO | BookingConfirmationResult 聚合结果 | P2 | EXT-19 §1 | (间接通过 F095 + F097) | `booking/BookingConfirmationResult.java:11-32` | — | L0 | `overallSuccess` + `resolvedLocale/usedFallback/bookingId` + `channelResults: Map<channel, {success, externalMsgId, errorMsg}>`；`putChannel()` 自动累加 overallSuccess |
| **F087** | 预约确认 / 查询 | StoreLookupService（pos_store 查询 + 5min 缓存）| P1 | EXT-19 §0.4 + §1 | (无前端入口；由 F083 调用) | `booking/StoreLookupService.java:54-76` `lookup(storeId)` + `:43-45` `lookupStoreName/lookupStorePhone` + `:78-79` `invalidate(storeId)` | `pos_store`（间接）| L0 | `CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)`（**与 SoT §0.4 完全一致**）；`ConcurrentHashMap<Long, CacheEntry>`；SQL `SELECT store_name, contact_phone FROM pos_store WHERE id=? AND deleted=0`（**与 SoT §0.4 "字段映射 store_name + contact_phone" 完全一致**）；**⚠ `pos_store` 表可能未跑迁移（与 I002/I017/I029 同模式）** — Phase 5 验证 |
| **F088** | 渠道 / SMS | SmsSender 阿里云短信 | P1 | EXT-19 §1 + §4 | (间接通过 F083) | `service/channel/SmsSender.java:46-90` `send()` + `:106-124` `resolveConfig()`（按 `tenantId + channelType=SMS` 查 `mkt_channel_config`）| `mkt_channel_config`（间接）| L0 / **⚠ UNVERIFIED（无真实阿里云 accessKey）** | `channelType() = "SMS"`；走 `HttpUtil.sendAliYunSms`；缺凭据时 `demoMode()` 返回 `SMS-DEMO-{ts}`；`render(template, vars)` 静态方法（`${key}` → vars[key]）；`normalizePhone` 自动去 +86 前缀；`maskPhone(135****0001)` |
| **F089** | 渠道 / EMAIL | EmailSender JavaMailSender + mismatch demo | P1 | **EXT-19 §0.2 ⭐ 三模式降级** | (间接通过 F083) | `service/channel/EmailSender.java:60-114` `send()` + `:121-167` `doSendSmtp()` + `:178-186` `demoMode()` + `:188-203` `resolveConfig()` | `mkt_channel_config`（间接）| L0 / **⚠ UNVERIFIED（无真实 SMTP 凭据）** | `channelType() = "EMAIL"`；**三模式**：① 租户 cfg + JavaMailSender 已注入 → 真实 SMTP（HTML / 纯文本自适应）；② JavaMailSender 已注入但无租户 cfg → 全局 `spring.mail.*`；③ JavaMailSender 未注入或 mismatch → demo 模式；**日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode`**（SoT §0.2 完全一致）；mismatch 检测：`globalHost.equals(smtpHost) ∧ globalUsername.equals(username)`（不一致即 demo）|
| **F090** | 渠道 / MINI_PROGRAM | MiniProgramSender 微信订阅消息 | P1 | EXT-19 §0.1 + §1 | (间接通过 F083) | `service/channel/MiniProgramSender.java:48-100` `send()` + `:107-128` `getAccessToken(tenantId, appId, appSecret)` + `:135-163` `buildSubscribeData()` | `mkt_channel_config`（间接）| L0 / **⚠ UNVERIFIED（无真实 appId/appSecret）** | `channelType() = "MINI_PROGRAM"`；**AccessToken 内存缓存**：`ConcurrentHashMap<Long, TokenCache>`；`TOKEN_CACHE_MS = 7100_000L`（提前 10 分钟刷新 2 小时 token）；errcode=40001 自动清缓存；`buildSubscribeData` 按 `variables` 数组生成 `keyword1..keyword5`（最多 5）；`MP_TEMPLATE_ID = "BOOKING_CONFIRMATION"` 是占位（SoT §0.1）|
| **F091** | 渠道 / IN_APP | InAppSender 站内信（database 持久化）| P2 | EXT-19 §1 + §4 | (间接通过 M09 营销链路 — F083 未路由 IN_APP)| `service/channel/InAppSender.java:42-87` `send()` + `@Transactional(rollbackFor = Exception.class)` (`:43`) | `member_message`（写）| L0 / **⚠ MISSING（`member_message` 表 db-snapshot.sql 0 匹配已确认）** | `channelType() = "IN_APP"`；写 `member_message(tenantId/memberId/title/content/channelType=IN_APP/messageType/priority=2/...)`；`receiver` 解析 `memberId[-name]`；`messageType` 推断（BIRTHDAY/EXPIRE/PROMO/TASK/SYSTEM）；**⚠ F083 `sendConfirmation` 未路由 IN_APP**（与 SoT §4 一致 — SoT 仅明确 3 渠道路由：SMS/EMAIL/MINI_PROGRAM，"任一无对应 receiver → skipped"），但 ChannelDispatcher.getSender("IN_APP") 仍可被其他业务调用 |
| **F092** | 渠道 / WECHAT_OFFICIAL_ACCOUNT | WechatOfficialAccountSender 公众号模板消息 | P2 | EXT-19 §1 + §4 | (间接通过 M09 — F083 未路由 OA) | `service/channel/WechatOfficialAccountSender.java:53-113` `send()` + `:120-141` `getAccessToken()` + `:148-184` `buildTemplateData()` | `mkt_channel_config`（间接）| L0 / **⚠ UNVERIFIED（无真实 appId/appSecret）** | `channelType() = "WECHAT_OFFICIAL_ACCOUNT"`（**注意：非 `WECHAT_OA`，与 SoT §1 描述"WECHAT_OA"不一致 — 见 I039**）；AccessToken 缓存与 F090 同模式（`TOKEN_CACHE_MS = 7100_000L`）；模板消息用 `first/keyword1..N` 格式（最多 10 个 keyword）；errcode 40001/40003/43003/43004/43005 业务失败不重试 |
| **F093** | 渠道 / 接口 | ChannelSender 接口契约 | P1 | EXT-19 §1 + scope-freeze §2.3 | (无前端入口) | `service/channel/ChannelSender.java` 接口（`:9-37`）— 3 方法：`channelType()` / `send(MktChannelTemplate, String, Map)` / `healthCheck()` | — | L0 | `SendResult` 内嵌类：`{success, externalMsgId, errorMsg}` + `static ok(externalMsgId)` / `static fail(msg)` 工厂 |
| **F094** | 渠道 / 分发 | ChannelDispatcher 路由 + healthCheckAll | P2 | EXT-19 §1 + scope-freeze §2.3 | (无前端入口；M09 体系调用) | `service/channel/ChannelDispatcher.java:34-41` `getSender(channelType)` + `:53-73` `healthCheckAll()`（**仅更新 tenant_id=1** — T07-D4 例外声明）+ `:78-90` `rateLimitCheck()` | `mkt_channel_config`（写 `last_health_at/msg`）+ `marketing_touch_log`（读）| L0 | `getSender` 按 `channelType` filter；`healthCheckAll` 轮询所有 sender + UPDATE `mkt_channel_config.last_health_at`；`rateLimitCheck` 查 `marketing_touch_log` 按分钟/日限额 |
| **F095** | 预约确认 / 端点 | `POST /intl-booking/{id}/send-confirmation` 手动触发 | P1 | EXT-19 §1 + §3 | (无前端调用 — **⚠ Shadow I042**：前端 `intl-booking.ts` 无 `sendConfirmation` 方法) | `service/IntlBookingService.java` `sendConfirmation(@PathVariable Long id)` (独立方法) | `overseas_booking`（读）| L0 | 独立于状态变更；店长可对任何状态的预约重发确认（SoT §3 明确）；返回 `BookingConfirmationResult` |
| **F096** | 预约确认 / 端点 | `POST /intl-booking/{id}/status` + autoSend 自动触发 | P0 | **EXT-19 §0.3 ⭐ 行为变更** | `intl-booking/index.vue` "操作" 列 "确认" 按钮（**⚠ Shadow I040/I041：未传 `autoSend=false`，无 loading/二次确认/文案提示**）| `service/IntlBookingService.java` `updateStatus(id, body)` | `overseas_booking`（读+写）| L0 | 行为：非 CONFIRMED → CONFIRMED 时**自动调用** `confirmationService.sendConfirmation(b)`；`body.autoSend=false` 可关闭（**与 SoT §0.3 完全一致**）；发送异常在 try/catch 内 + 写 `confirmationError` 字段（**不回滚状态变更 — I008 模式对齐**）；响应 `data: { booking, confirmation?, confirmationError? }`（**与 SoT §0.3 完全一致**）|
| **F097** | 预约确认 / 端点 | `GET /intl-booking/{id}/confirmation-template` 模板预览 | P2 | EXT-19 §1 + §3 | `intl-booking/index.vue` "操作" 列 "确认模板" 按钮（`viewTemplate(row.id)` L91-94 + `templateDialog`）| `service/IntlBookingService.java` `confirmationTemplate(id)` | `overseas_booking`（读）| L0 | **⚠ I043 候选**：当前实现是**控制器内硬编码语言 switcher**（zh/zh-cn/zh-tw/ja/ko/default），**未走 BookingConfirmationTemplateResolver + 6 locale JSON**（与 SoT §1 描述反向 — SoT 期望走 i18n 模板体系）；仅含 `subject + body`，缺 `sms/miniProgram/email` 4 段（与 BookingConfirmationTemplate DTO 反向）|
| **F098** | 预约 / 端点 | `GET /intl-booking/list` 列表查询 | P2 | EXT-19 §1 + §3 | `intl-booking/index.vue` "国际预约" tab table（`load()` + `intlBookingApi.listBookings()`）| `service/IntlBookingService.java` `listBookings(status?, source?)` | `overseas_booking` | L0 / **⚠ MISSING** | `LambdaQueryWrapper<OverseasBooking>` 按 `tenantId` 过滤 + `id DESC`；可选 `status` / `source` 过滤 |
| **F099** | 预约 / Webhook | `POST /intl-booking/webhook/google` Google 推送 | P2 | EXT-19 §1 + §3 | (无前端入口；Google BP 后台 push) | `service/IntlBookingService.java` `googleWebhook(payload, token)` (P3-26) | `overseas_booking`（写）| L0 | **演示模式**：解析 `payload.notificationId/customerName/customerPhone/customerEmail/locale/serviceName/bookingTime` → 插 `overseas_booking`；`@Transactional` 但**未指定 rollbackFor**（**I009 模式**：默认仅 RuntimeException 回滚）；`token → tenantId` 反查当前演示返回 `null` 兜底 1L |
| **F100** | 预约 / Webhook | `POST /intl-booking/webhook/instagram` IG 推送 | P2 | EXT-19 §1 + §3 | (无前端入口；Meta 后台 push) | `service/IntlBookingService.java` `instagramWebhook(payload)` (P3-26) | `overseas_booking`（写）| L0 | **演示模式**：解析 `entry[0].messaging[0].message.text/sender.id` + 简易 NLP（`parseServiceFromText` 关键词匹配 facial/massage/manicure/nail/hair）→ 插 `overseas_booking`；`@Transactional` 但**未指定 rollbackFor**（**I009 模式**）；tenantId 演示模式硬编码 1L |
| **F101** | 预约 / Webhook 验证 | `GET /intl-booking/webhook/instagram` Meta verify_token 握手 | P3 | EXT-19 §1 + §3 | (无前端入口；Meta webhook 验证握手) | `service/IntlBookingService.java` `instagramWebhookVerify(mode, verifyToken, challenge)` | — | L0 | 返回 `hub.challenge` 完成 Meta webhook 验证握手；**演示模式**：mode=subscribe + challenge 非空即返回；生产需 `verifyToken` 比对（SoT §3 未明确，本批次不实现）|
| **F102** | 预约 / OAuth2 | Google / Instagram connect + callback | P2 | EXT-19 §1 + §3 | `intl-booking/index.vue` "渠道连接" tab + Google/Instagram 绑定对话框 | `service/IntlBookingService.java` `connectGoogle/body` + `googleCallback/body` + `connectInstagram/body` + `instagramCallback/body` | — | L0 | **演示模式**：返回真实 OAuth URL（`accounts.google.com/o/oauth2/v2/auth` + `facebook.com/v18.0/dialog/oauth`）但 callback 不真换 token，返回 `ya29.DEMO-{code前8位}`；前端 `window.open(data.authUrl, '_blank')` 跳转 |
| **F103** | 预约 / 转化 | `POST /intl-booking/{id}/convert` 转化为会员 + 订单 | P2 | EXT-19 §1（间接）| `intl-booking/index.vue` "操作" 列 "转化为会员" 按钮（`convert(row.id)`，仅 `status === 'CONFIRMED' && !row.convertedMemberId` 可见）| `service/IntlBookingService.java` `convertBooking(id)` + `doCreateMember` + `doCreatePosOrder` | `crm_member`（写）+ `pos_order`（写）| L0 | **演示模式**：JdbcTemplate 直接 INSERT `crm_member` + `pos_order`（避免引入 MemberService 复杂依赖）；写回 `convertedMemberId/convertedOrderId`；`@Transactional`（默认 rollbackFor 仅 RuntimeException — I009 模式）|
| **F104** | 预约确认 / 端点 | `GET /intl-booking/{id}` 单条查询 | P2 | EXT-19 §1 | (间接通过 F098 列表点开) | `service/IntlBookingService.java` `getBooking(id)` | `overseas_booking` | L0 | `selectById`；`null` → `BizCode.NOT_FOUND` |
| **F105** | 预约 / 渠道列表 | `GET /intl-booking/channels` 静态渠道列表 | P3 | EXT-19 §1 | `intl-booking/index.vue` "渠道连接" tab `channels` 卡片网格 | `service/IntlBookingService.java` `listChannels()` | — | L0 | 4 渠道：`GOOGLE/INSTAGRAM/FACEBOOK/TIKTOK`（**FACEBOOK/TIKTOK status=PLANNED** — 非 PLANNED 但显示）；纯静态枚举无 DB 查询 |
| **F106** | 预约 / 拉取 | `POST /intl-booking/{channelId}/pull` 拉取预约（演示）| P3 | EXT-19 §1 | `intl-booking/index.vue` "操作" 列 "拉取预约" 按钮 | `service/IntlBookingService.java` `pullBookings(channelId)` | `overseas_booking`（写）| L0 | **演示模式**：GOOGLE/INSTAGRAM 渠道插 1 条示例记录（`ExternalId="EXT-{ts}"` + 演示客户 + 演示服务）；FACEBOOK/TIKTOK 返回 `BizCode.NOT_FOUND` |
| **F107** | 预约确认 / i18n | 6 个 locale JSON 模板（zh-CN / zh-TW / en-US / ja-JP / ko-KR / fr-FR）| P0 | **EXT-19 §1 + §4 + §5 占位符变量**| (间接通过 F083 经 F084 渲染) | 6 资源文件 `resources/i18n/template/*.json` | — | L0 | **6 locale JSON 内容完整核对通过**（§1.4.6 详细交叉表）；均含 `subject/sms/miniProgram(title+keyword1..keyword5)/email(subject+body)` 4 段；占位符 `${customerName/serviceName/bookingDate/bookingTime/bookingNo/storeName/storePhone}` 7 变量（**与 SoT §5 完全一致**）|
| **F108** | 测试 | BookingConfirmationServiceTest（StubSms/Email + Mockito StoreLookup）| P1 | EXT-19 §1 | (无；JUnit 测试) | `test/booking/BookingConfirmationServiceTest.java`（JUnit5）| — | L0 | StubSmsSender / StubEmailSender 桩；Mockito mock StoreLookupService + resolver；测试点：渠道路由 + overallSuccess 聚合 + null booking 安全 + ChannelSender 缺失降级 + 门店信息读取 |
| **F109** | 测试 | BookingConfirmationTemplateResolverTest（locale 归一化 + 回退 + 渲染）| P1 | EXT-19 §1 | (无；JUnit 测试) | `test/booking/BookingConfirmationTemplateResolverTest.java`（JUnit5）| — | L0 | 反射注入 ObjectMapper（`@Autowired(required=false)` 留空）；`warmup()` 触发 6 locale 加载；测试点：normalize（zh_CN → zh-CN，lang 小写 / country 大写）+ 回退链（zh-TW → zh-CN → en-US）+ 占位符 ${} 渲染 + 未注册 locale → en-US |
| **F110** | 测试 | EmailSenderTest + WechatOfficialAccountSenderTest | P1 | EXT-19 §1 | (无；JUnit 测试) | `test/service/channel/EmailSenderTest.java` + `WechatOfficialAccountSenderTest.java`（**Phase 1 新发现 4 测试存在**，preflight §7.2 仅列 3 测试）| — | L0 | EmailSenderTest 6 测试点（channelType/receiver 校验/demo 模式/healthCheck）；WechatOfficialAccountSenderTest 同模式（OA sender 测试）|
| **F111** | 前端 / 页面 | intl-booking/index.vue（2 tab + 3 对话框）| P2 | EXT-19 §1 + scope-freeze §2.3 | `views/intl-booking/index.vue:1-150+`（"渠道连接" tab + "国际预约" tab table）| (无新后端) — 全部对应 `IntlBookingService` 端点 | — | L0 | "渠道连接" tab el-row 卡片（GOOGLE/INSTAGRAM 绑定按钮 + 拉取预约按钮）；"国际预约" tab el-table（id/source/bookingNo/customerName/customerLocale/serviceName/bookingDate/bookingTime/status 列 + 操作列：确认/取消/确认模板/转化为会员/已转化标签）；3 对话框（Google OAuth / Instagram OAuth / 模板预览）；**⚠ Shadow I040**：状态确认按钮无 loading / 无二次确认 / 无"确认即发送通知"文案提示；**⚠ Shadow I041**：无 `autoSend=false` UI（后端支持但前端未暴露）|
| **F112** | 前端 / API | intl-booking.ts（12 端点封装）| P2 | EXT-19 §1 + scope-freeze §2.3 | `api/intl-booking.ts:1-31`（12 方法）| (无新后端) — 全部对应 `IntlBookingService` 端点 | — | L0 | OAuth × 4（connectGoogle/googleCallback/connectInstagram/instagramCallback）+ listChannels + pullBookings + listBookings + getBooking + updateStatus + convertBooking + confirmationTemplate；**⚠ Shadow I042**：`sendConfirmation` 方法缺失（后端 `POST /intl-booking/{id}/send-confirmation` 端点存在但前端无对应入口；当前只能通过 `updateStatus(id, {status: 'CONFIRMED'})` 间接触发自动发送，无法独立手动重发）|
| **F113** | 业务入口 / 命名异味 | IntlBookingService `@RestController` 命名误导 | P3 | (架构异味，沿用 Batch 1.1 I004 / 1.2 I019 / 1.3 I029 模式) | (无) | `service/IntlBookingService.java:14-17` `@RestController @RequestMapping("/intl-booking")` 但文件名是 `*Service` | — | L0 | 与项目其他 Controller 在 `controller/` 包下不一致；13 REST 端点全在一个文件里；**不阻塞 Phase 2**，记入后续重构 backlog（**I044 候选 — 同模式第 4 次出现**）|

### 1.4.3 Feature 数量统计

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

### 1.4.4 Phase 1 Issue 清单（**不进 Phase 2 前必须决策**）

| Issue ID | 类别 | 描述 | 影响 Feature | 建议处理 |
|---|---|---|---|---|
| **ISSUE-I038** | **MISSING（DB）** | `overseas_booking` 表 DDL 在 `migrate_ext_19_mini_program_openid.sql:9` 已写（含 26 列 + 4 索引 + `ADD COLUMN IF NOT EXISTS mini_program_open_id`），但 `db-snapshot.sql` 全文件 grep `overseas_booking` **0 匹配已确认** → **baseline DB 未跑该 migration**。`IntlBookingService` 的 `selectById/insert/update/listById` 启动期会触发 `TableNotFoundException` → 与 Batch 1.1 I002 / 1.2 I017 / 1.3 I029 同模式（跑迁移即解，**统一 Post-Audit 跑批 — 用户决策 #3 第三次强调**）| F081 / F082 / F095-F106 | Phase 5：① grep `db-snapshot.sql` 确认缺失（已做）；② 触发 `migrate_ext_19_mini_program_openid.sql` 跑批；③ 写 `audit/evidence/F081-overseas-booking-entity/db-evidence.sql` 验证 `DESCRIBE overseas_booking` 显示 26 列 |
| **ISSUE-I039** | **CONFLICT**（中立标）| SoT §1 列渠道时使用 `WECHAT_OA` 别名（`WechatOfficialAccountSender`）；代码实际 `channelType() = "WECHAT_OFFICIAL_ACCOUNT"`（`WechatOfficialAccountSender.java:60`）— **别名不一致**（功能完整，仅名称漂移）| F092 / F094 | 沿用 I001/I018/I026/I027/I030 模式 — **中立标**，Fix Phase = Post-Audit 人类决策（3 选项：改 SoT 文档统一别名 / 改代码 channelType 名称 / 维持现状接受别名差异） |
| **ISSUE-I040** | **Shadow**（Frontend UI 缺失 — autoSend=false）| SoT §0.3 `body.autoSend=false` 可关闭自动发送；后端 `IntlBookingService.updateStatus` 支持读取 `body.get("autoSend")`；**前端 `intl-booking/index.vue` 状态确认按钮 (`setStatus(row, 'CONFIRMED')`) 直接传 `{status: 'CONFIRMED'}`，不传 `autoSend` 字段，无 UI 暴露** — 用户无感被自动触达 | F096 / F111 | 沿用 I006/I007 模式（SoT vs 前端反向）；**中立标**，Fix Phase = Post-Audit 人类决策（用户决策 #6）|
| **ISSUE-I041** | **Shadow**（Frontend UI 缺失 — 按钮文案）| SoT §0.3 明确要求"前端状态确认按钮文案需明确『确认即发送通知』，避免用户无感被自动触达"；前端 `intl-booking/index.vue` 按钮文案是"确认"（L66），**无"即发送通知"提示 + 无 el-popconfirm 二次确认 + 无 loading 状态** | F096 / F111 | 同 I040 模式；**中立标**，Fix Phase = Post-Audit |
| **ISSUE-I042** | **Shadow**（Frontend API 缺失）| 后端有 `POST /intl-booking/{id}/send-confirmation` 端点（`IntlBookingService.sendConfirmation(id)`）支持独立手动重发；前端 `intl-booking.ts:1-31` **12 端点封装中无 `sendConfirmation` 方法**（Phase 1 grep `send-confirmation\|sendConfirmation` 0 匹配已确认）— 店长无 UI 触发手动重发（仅能依赖 `updateStatus(CONFIRMED)` 间接自动触发）| F095 / F112 | **中立标**，Fix Phase = Post-Audit |
| **ISSUE-I043** | **CONFLICT**（中立标）| SoT §1 期望 `/confirmation-template` 端点走 `BookingConfirmationTemplateResolver` + 6 locale JSON 模板体系；代码实际 `IntlBookingService.confirmationTemplate(id)` 是**控制器内硬编码 switcher**（`zh/zh-cn/zh-tw/ja/ko/default` 6 分支），返回仅 `subject + body`（**与 BookingConfirmationTemplate DTO 反向** — 缺 sms/miniProgram/email 4 段）— 当前 preview 与生产发送链路**不一致**（F095/F096 走 Resolver，F097 走硬编码 switcher）| F097 | 沿用 I001/I018/I026/I027/I030 模式；**中立标**，Fix Phase = Post-Audit 人类决策（3 选项：改用 Resolver / 保留硬编码 + 标注差异 / 接受现状）|
| **ISSUE-I044** | **架构异味（命名）**| `IntlBookingService.java` 文件名是 `*Service`，实际 `@RestController @RequestMapping("/intl-booking")`（`:14-17`），挂在 `service/` 包下（项目其他 Controller 在 `controller/` 包）；13 个 REST endpoint 全在这一个文件里 — **与 Batch 1.1 I004 / 1.2 I019 / 1.3 I029 完全同模式（第 4 次出现）**| F113 | 不阻塞 Phase 2；记入后续重构 backlog（**第 4 次出现，建议 Post-Audit 统一整改**）|
| **ISSUE-I045** | **MISSING（DB）** | `member_message` 表被 `InAppSender`（F091）写入使用；`db-snapshot.sql` grep `member_message` **0 匹配已确认** → **baseline DB 未跑该 migration**；`InAppSender.send` `@Transactional(rollbackFor = Exception.class)` 启动期会触发 `TableNotFoundException` → try/catch 包裹写日志但业务失败 | F091 | Phase 5：① grep `db-snapshot.sql` 确认缺失（已做）；② 触发 `member_message` DDL 跑批；③ 写 `audit/evidence/F091-in-app-sender/db-evidence.sql` 验证 |
| **ISSUE-I046** | **MISSING（DB）** | `pos_store` 表被 `StoreLookupService`（F087）查询；`db-snapshot.sql` grep `pos_store` **5 匹配已确认**（存在）；但 **EXT-19 依赖的 `store_name` + `contact_phone` 字段是否对齐** 待 Phase 5 验证 — 沿用 I002 模式 | F087 | Phase 5：`DESCRIBE pos_store` 验证 `store_name/contact_phone` 列存在 + 类型对齐 |
| **ISSUE-I047** | **⚠ PARTIAL-NON-BLOCKER**（catch 吞异常 — I008 模式）| `IntlBookingService.updateStatus`（F096）中 autoSend 发送异常在 try/catch 内（`:200-210`）— **与 SoT §0.3 "发送异常不会回滚状态变更" 完全一致**（设计而非 Bug）；但 `log.warn` 仅记录，运维侧需依赖日志监控才能感知发送失败（无结构化错误码）| F096 | 决策：① 接受现状（SoT 明确）；② 升级为 `data.put("confirmationError", e.getMessage())` 结构化错误（**当前已实现 — I047 仅作为模式对齐标注**）|
| **ISSUE-I048** | **⚠ PARTIAL-NON-BLOCKER**（rollbackFor — I009 模式）| `IntlBookingService.updateStatus` + `googleWebhook` + `instagramWebhook` + `convertBooking` 加了 `@Transactional` 但**未指定 `rollbackFor`** → 默认仅 RuntimeException 回滚；HTTP 异常 / 受检异常可能不回滚 | F096 / F099 / F100 / F103 | 决策：① 接受现状（业务侧写库失败概率低）；② 加 `rollbackFor = Exception.class`（本批次不改）|
| **ISSUE-I049** | **⚠ UNVERIFIED**（凭据缺失 — D10 铁律）| `MiniProgramSender` + `EmailSender` + `WechatOfficialAccountSender` + `SmsSender` 真实凭据缺失 → Mock 降级 + jshell 单元测试；真实链路 Phase 5 维持 UNVERIFIED | F088 / F089 / F090 / F092 | 沿用用户决策 #1 / #2；Phase 5 仅验证 mock + jshell 单元测试；真实链路 UNVERIFIED |
| **ISSUE-I050** | **⚠ PARTIAL-NON-BLOCKER**（dead code — `healthCheckAll` T07-D4 例外）| `ChannelDispatcher.healthCheckAll`（`:53-73`）仅更新默认租户(tenant_id=1)的渠道配置，其他租户渠道健康状态不会被更新 — **有意保留的语义**（T07-D4 例外声明 `log.warn`）；但缺少 Phase 5 真实多租户场景验证 | F094 | 决策：① 接受现状（T07-D4 例外声明已记录）；② 升级为多租户批量更新（**本批次不改**）|
| **ISSUE-I051** | **⚠ PARTIAL-NON-BLOCKER**（webhook demo 模式 — 真实链路缺失）| `IntlBookingService.googleCallback` + `instagramCallback`（F102）+ `googleWebhook`（F099）+ `instagramWebhook`（F100）均**演示模式**：返回 `ya29.DEMO-` / `IGQVJ-DEMO-` token，不真换；webhook 不真订阅；**token → tenantId 反查返回 null 兜底 1L** — 与 SoT §3 "Google / Instagram webhook 端点" 列存在但未明确真实链路要求 | F099 / F100 / F102 | 决策：① 接受现状（演示模式足够联调）；② 升级为真实 OAuth + webhook 订阅（**本批次不改 — 沿用 P3-26 演示模式**）|

### 1.4.5 与 EXT-* Operational Notes 的冲突点（SoT 比对）

> 按 `project-audit.mdc` 规则 11：审计结论与 EXT-* Operational Notes 冲突时，**以 EXT-* 为准并标 ISSUE**。
> 本次审计发现 **EXT-* 文档部分与代码不一致**（沿用 Batch 1.1 I001 / 1.2 I018 / 1.3 I026-I030 模式）。沿用 scope-freeze §五.2 修正分类原则：
> - 表 MISSING 类 → I002/I017/I029 模式（跑迁移即解，**统一 Post-Audit 跑批**）
> - SoT vs 代码 CONFLICT 类 → I001/I018/I026-I030 模式（中立标，Fix Phase = Post-Audit 人类决策）
> - Frontend Shadow → I006/I007 模式（中立标，Post-Audit 决定）

| EXT-* 章节 | 文档声称 | 代码事实 | 漂移类型 | 处理 |
|---|---|---|---|---|
| §0.1 WeChat 模板 ID 是占位符 | `MP_TEMPLATE_ID = "BOOKING_CONFIRMATION"` 真实使用前必须替换 | `BookingConfirmationService.java:25` `MP_TEMPLATE_ID = "BOOKING_CONFIRMATION"` **确认** | (无漂移) | OK |
| §0.1 `MP_VARIABLES_JSON` keyword 顺序 | `["customerName","serviceName","bookingDate","bookingTime","bookingNo"]` 顺序匹配官方模板 | `BookingConfirmationService.java:28` 完全一致 | (无漂移) | OK |
| §0.2 EmailSender mismatch demo 模式 | "租户 configJson 与 spring.mail.* 不一致时降级 demo 模式" + 日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode` | `EmailSender.java:155-158` `if (!globalMatch && (cfg == null || !cfg.getConfigJson().contains("smtpHost"))) { log.warn("[EMAIL] tenant SMTP host/user mismatch with global config; demo mode to={}", to); ... }` — **完全一致** | (无漂移) | OK |
| §0.3 CONFIRMED 自动触发 sendConfirmation | "非 CONFIRMED → CONFIRMED 时自动调用 `confirmationService.sendConfirmation(b)` + `body.autoSend=false` 可关闭 + 异常不回滚 + 响应 `{booking, confirmation?, confirmationError?}`" | `IntlBookingService.updateStatus` (`:186-210`) **完全一致**：① 非 CONFIRMED → CONFIRMED 检测 ② autoSend 读取 ③ try/catch 包裹 ④ 写 `confirmationError` 字段 | (无漂移) | OK |
| §0.3 响应结构变化 | "data 从单个 OverseasBooking 改为 `{ booking, confirmation?, confirmationError? }`" | `IntlBookingService.updateStatus` (`:191-210`) **完全一致** | (无漂移) | OK |
| §0.4 StoreLookupService 字段映射 | "查 pos_store.store_name + contact_phone + 5 分钟缓存 + 兜底 `美业旗舰店 / 400-888-8888`" | `StoreLookupService.java:60-66` SQL `SELECT store_name, contact_phone FROM pos_store WHERE id=? AND deleted=0` + `CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)` (`:24`) + `BookingConfirmationService.java:34-35` `FALLBACK_STORE_NAME = "美业旗舰店"` / `FALLBACK_STORE_PHONE = "400-888-8888"` — **完全一致** | (无漂移) | OK |
| §0.5 SQL 迁移兼容 | "ADD COLUMN IF NOT EXISTS（MySQL 8.0.29+）" | `migrate_ext_19_mini_program_openid.sql:36` 使用 `ADD COLUMN IF NOT EXISTS` | (无漂移) | OK |
| §1 Touchpoints 后端控制器 | 列 `service/IntlBookingService.java` | `service/IntlBookingService.java` 存在 | (无漂移) | OK |
| §1 Touchpoints channel sender | 列 `SmsSender` / `EmailSender` / `MiniProgramSender` / `InAppSender` / `WechatOfficialAccountSender` | 全部存在 | (无漂移) | OK |
| §1 Touchpoints channel sender 别名 | 描述渠道时使用 `WECHAT_OA` 别名 | 代码实际 `channelType() = "WECHAT_OFFICIAL_ACCOUNT"`（`WechatOfficialAccountSender.java:60`）— **别名漂移** | **CONFLICT**（别名）| **I039 中立标** |
| §1 Touchpoints 测试 | 列 3 测试（`BookingConfirmationServiceTest` / `BookingConfirmationTemplateResolverTest` / `EmailSenderTest`）| 实际 **4 测试**（`BookingConfirmationTemplateResolverTest` / `BookingConfirmationServiceTest` / `EmailSenderTest` / **`WechatOfficialAccountSenderTest`** — **Phase 1 新发现**）| (无漂移 — SoT 不完整) | OK（SoT 增补）|
| §3 Endpoints | `POST /intl-booking/{id}/send-confirmation` | `IntlBookingService.sendConfirmation(id)` 已实现 | (无漂移) | OK |
| §3 Endpoints | `POST /intl-booking/{id}/status` | `IntlBookingService.updateStatus(id, body)` 已实现 | (无漂移) | OK |
| §3 Endpoints | `GET /intl-booking/{id}/confirmation-template` | `IntlBookingService.confirmationTemplate(id)` 已实现，但**硬编码 switcher**（**与 SoT 期望走 Resolver 体系反向** — 详见 §1.4.6 / I043）| **CONFLICT**（实现方式反向）| **I043 中立标** |
| §3 Endpoints | `GET /intl-booking/list` | `IntlBookingService.listBookings(status?, source?)` 已实现 | (无漂移) | OK |
| §3 Endpoints | `POST /intl-booking/webhook/google` | `IntlBookingService.googleWebhook(payload, token)` 已实现（演示模式）| (无漂移 — SoT 未明确真实链路) | OK |
| §3 Endpoints | `POST /intl-booking/webhook/instagram` | `IntlBookingService.instagramWebhook(payload)` 已实现（演示模式）| (无漂移 — SoT 未明确真实链路) | OK |

---



## Batch 1.5 — MKT 多渠道重构（v3 第五批 · 首个重构批次）

**范围来源**：`audit/baseline/scope-freeze-batch-1.5.md` §2.3 + `audit/baseline/preflight-batch-1.5.md` §七
**SoT**：`docs/ROLLBACK-6.2-mkt_ddl.md`（MKT-002 SoT）+ git status（⚠ **无 .mdc Operational Notes** — 本批次退化）
**Phase 0 freeze commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（沿用 Batch 1.2/1.3/1.4 freeze commit）
**Batch 起点**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（与起点一致）
**Feature 编号策略**：接续 Batch 1.4 的 F081-F113 → 本批次 **F114-F140（27 Features）**
**重构标记**：⭐⭐⭐ **首个重构批次**（@project-audit.mdc 规则 14 强制触发 Phase 3.5 Diff Audit — 见 `audit/diff-audit/mkt-refactor.md`）

### 1.5.1 范围涉及文件（已静态扫描）

| 类别 | 文件 | 行数 / 端点数 | 备注 |
|---|---|---|---|
| **新 Mkt*Controller（9 个）** | `controller/MktChannelController.java` | **5 端点** | `@RequestMapping("/mkt/channel")` — `/list` `POST /` `PUT /{id}` `DELETE /{id}` `POST /health-check` |
| 新 Controller | `controller/MktConversionController.java` | **5 端点** | `@RequestMapping("/mkt/conversion")` — `/funnel` `/roi` `/by-channel` `POST /track` `POST /mark-read` |
| 新 Controller | `controller/MktJourneyController.java` | **10 端点** | `@RequestMapping("/mkt/journey")` — `POST /` `POST /page` `GET /page` `GET /{id}` `PUT /{id}` `POST /{id}/publish` `POST /{id}/pause` `POST /{id}/enter` `POST /instance/{instanceId}/next` |
| 新 Controller | `controller/MktTemplateController.java` | **3 端点** | `@RequestMapping("/mkt/template")` — `/list` `POST /` `PUT /{id}/audit` |
| 新 Controller | `controller/MktTouchTaskController.java` | **9 端点** | `@RequestMapping("/mkt/task")` — `POST /page` `GET /page` `POST /` `POST /{id}/start` `POST /{id}/pause` `POST /{id}/resume` `POST /{id}/cancel` `GET /{id}/log` |
| 新 Controller | `controller/MktEffectController.java` | **2 端点** | `@RequestMapping("/mkt/effect")` — `GET /snapshot` `POST /snapshot/save` |
| 新 Controller（git status 漏列）| `controller/MktCouponController.java` | **9 端点** | `@RequestMapping("/mkt/coupon")` — `/batch/page` `POST /batch` `POST /batch/{id}/publish` `POST /batch/{id}/end` `POST /batch/{id}/grant` `POST /expire-sweep` `GET /page` `GET /export` |
| 新 Controller（git status 漏列）| `controller/MktPromotionController.java` | **7 端点** | `@RequestMapping("/mkt/promotion")` — `GET /page` `GET /` `POST /` `PUT /{id}` `DELETE /{id}` `GET /export` |
| 新 Controller（git status 漏列）| `controller/MktPointsController.java` | **4 端点** | `@RequestMapping("/mkt/points")` — `/rule/list` `POST /rule` `PUT /rule/{id}` `POST /award` |
| **新 Controller（非 9 大，但已存在）**| `controller/BusinessPolicyDispatchController.java` | **9 端点** | `@RequestMapping("/m12/dispatch")` — `POST /` `POST /{id:\\d+}/publish` `POST /{id:\\d+}/publish-async` `POST /{id:\\d+}/cancel` `GET /page` `GET /{id:\\d+}` `GET /{id:\\d+}/targets` `GET /{id:\\d+}/logs` `GET /stats` — 使用 `MktDispatch` + `MktDispatchTarget` 新实体；⚠ **仍在 `applyActivity()` (`:264-278`) 中调用旧 `MarketActivityMapper`** |
| **新 Service / Controller** | `service/ReportV2Service.java` | **12 端点** | `@RequestMapping("/report/v2")` — `GET /dashboard` `GET /sales/daily` `GET /product/top` `GET /employee/commission` `POST /task` `GET /task/page` `GET /task/{id}` `POST /task/{id}/cancel` `GET /task/{id}/download` `POST /snapshot/run` `GET /snapshot/list` `GET /caliber/list` `GET /caliber/{key}/query` — **不在 MKT 9 Controller 范围但属于 MKT 重构 Batch 邻近范围**；标记为 **相关非覆盖** |
| 新 Service / 业务核心 | `service/MarketingAutomationService.java` | **~25 方法** | `@Service`（非 `@RestController`，但类内部使用 `@RequestMapping`/`@PathVariable`/`@RequestBody` import 注解在部分方法上 — 无害但冗余）；委派 `ChannelDispatcher` + 5 Sender + `marketing_touch_*` 表 |
| **channel 分发** | `service/channel/ChannelDispatcher.java` | `getSender()` + `healthCheckAll()` + `rateLimitCheck()` | `@Autowired List<ChannelSender>` + `JdbcTemplate`；⚠ **`healthCheckAll()` 硬编码 `Long tenantId = 1L`** — 沿用 I050（T07-D4 例外声明）；`rateLimitCheck` 查 `marketing_touch_log` 表（限流依赖）|
| **channel 接口** | `service/channel/ChannelSender.java` | interface | `String channelType()` + `SendResult send(MktChannelTemplate, String receiver, Map<String,Object> vars)` |
| **channel SMS** | `service/channel/SmsSender.java` | `@Component` | `channelType() = "SMS"`（沿用 EXT-19）|
| **channel EMAIL** | `service/channel/EmailSender.java` | `@Component` | `channelType() = "EMAIL"` + mismatch demo 模式 |
| **channel MP** | `service/channel/MiniProgramSender.java` | `@Component` | `channelType() = "MINI_PROGRAM"` |
| **channel IN_APP** | `service/channel/InAppSender.java` | `@Component` | `channelType() = "IN_APP"` |
| **channel WECHAT_OA** | `service/channel/WechatOfficialAccountSender.java` | `@Component` | `channelType() = "WECHAT_OFFICIAL_ACCOUNT"`（**与 I039 别名 CONFLICT — 同一渠道别名模式沿用**）|
| 实体（核心 MKT 18 个）| `entity/MktChannelConfig.java` | 21 字段 | `id/tenantId/brandId/channelType/channelName/provider/configJson/rateLimitPerMin/rateLimitPerDay/signature/templatePrefix/status/lastHealthAt/lastHealthMsg/retryMax/retryIntervalSec/operatorId/operatorName/createTime/updateTime/deleted` |
| 实体（其他 17 个 Mkt*）| `entity/{MktChannelTemplate,MktPromotion,MktCoupon,MktCouponBatch,MktPointsRule,MktPointsLog,MktJourney,MktJourneyInstance,MktSegment,MktInvitation,MktInvitationRule,MktOrderDiscount,MktDynamicPricingRule,MktPricingLog,MktBrand,MktDispatch,MktDispatchTarget}.java` | 17 entity | 全部 `@TableName("mkt_*")`；其中 `MktInvitation` / `MktInvitationRule` / `MktDispatch` / `MktDispatchTarget` 4 表 **db-snapshot.sql 0 匹配**（详见 §1.5.7）|
| 实体（MarketingTouch*）| `entity/{MarketingTouchTask,MarketingTouchLog,MarketingTouchConversion}.java` | 3 entity | 表 `marketing_touch_*` 在 db-snapshot.sql line 5312/5347/5287 已存在 ✅ |
| 实体（refactor leftover）| `entity/MarketActivity.java` | 旧 | ⚠ **未删** — `BusinessPolicyDispatchServiceImpl.applyActivity()` 仍引用 |
| Mapper | `mapper/Mkt*.java` (19 个) + `mapper/MarketingTouch*.java` (3 个) + `mapper/MarketActivityMapper.java` (旧) | 23 个 | 全部 `extends BaseMapper<>` + `@Mapper`；**MktInvitationMapper / MktInvitationRuleMapper / MktDispatchMapper / MktDispatchTargetMapper 4 个 mapper 依赖不存在的表 → 运行时会 NPE / TableNotFoundException** |
| Service / 旧实现 | `service/MarketActivityService.java` (interface, 10 抽象方法) + `impl/MarketActivityServiceImpl.java` | 旧 | ⚠ **未删** — 旧端点仍编译通过 |
| 业务实现（含 refactor leftover）| `service/impl/BusinessPolicyDispatchServiceImpl.java` | `createDispatch` + `publishAndDispatch` + `applyActivity` + `applyCouponBatch` + `applyExperiment` + `validateSource` + `resolveTargets` | ⚠ **`applyActivity()` line 264-278 仍调用 `marketActivityMapper.selectById(d.getSourceId())` + `marketActivityMapper.insert(clone)`** — **与 SoT §1 "Java 侧已无读写" 矛盾（重大事实漂移）** — 见 I055 |
| SQL 迁移（前置）| `db/sql/migrate_mkt_001_add_tenant.sql` | ✅ HEAD | `market_activity.tenant_id` + `idx_ma_tenant_id` |
| SQL 迁移（主动作）| `db/sql/migrate_mkt_002_drop_market_activity.sql` | 49 行 | ⚠ **未跑**（Q3 第 6 次强调）— `db-snapshot.sql:5177` 仍有 `CREATE TABLE market_activity` |
| SQL 迁移（回滚）| `db/sql/mkt_002_rollback.sql` | 47 行 | ⚠ **未跑**（SoT 预案）；存储过程 `migrate_mkt_002_rollback()` 探测归档表 → 反向 RENAME |
| SoT 文档 | `docs/ROLLBACK-6.2-mkt_ddl.md` | 100+ 行 | 已落档；归档表名 `market_activity__archived_20260910` 与 SQL 严格一致 ✅ |
| Frontend 页面 | `frontend/meiyetec-web/src/views/mkt/{channel,journey,effect,coupon,conversion,points,promotion,template,touch-task}.vue` (9 个 Mkt) | 9 文件 | ⚠ **无 mkt/mkt*.ts API 文件**；前端走 `api/market.ts`（**旧 CouponController 路径**）|
| Frontend API | `frontend/meiyetec-web/src/api/market.ts` | 1 文件 | ⚠ **API 全部走 `/market/coupon/*` + `/market/point/*` + `/market/activity/*`** — 与新 `/mkt/*` 路由**完全脱节**；前端视图 `views/mkt/*.vue` 调用需 Phase 2 grep 确认实际路径 |
| 受保护页面 | `frontend/meiyetec-web/src/views/dashboard.vue` | 1 文件 | ⚠ Phase 2 必查（preflight §八.10）；**Phase 1 静态扫描结果：仅引用 `/market/coupon`（旧 CouponController 路径）+ `/mkt/points`（新路由 push）** — **未调用 `/mkt/channel/*` 或 ChannelDispatcher.healthCheckAll**；dashboard 渠道统计间接风险解除 |

### 1.5.2 9 个 Mkt*Controller 端点清单（汇总）

| Controller | 端点数 | 前缀 | 端点详情 | 触达实体 / 表 | 引用 Service |
|---|---|---|---|---|---|
| **MktChannelController** | **5** | `/mkt/channel` | `GET /list`、`POST /`、`PUT /{id}`、`DELETE /{id}`、`POST /health-check` | `MktChannelConfig` + `mkt_channel_config` | `MarketingAutomationService.channelList/createChannel/updateChannel/deleteChannel/healthCheck` + `ChannelDispatcher.healthCheckAll()` |
| **MktConversionController** | **5** | `/mkt/conversion` | `GET /funnel`、`GET /roi`、`GET /by-channel`、`POST /track`、`POST /mark-read` | `marketing_touch_conversion` | `MarketingAutomationService.conversionFunnel/conversionRoi/byChannel/trackConversion/markRead` |
| **MktJourneyController** | **10** | `/mkt/journey` | `POST /`、`POST /page`、`GET /page`、`GET /{id}`、`PUT /{id}`、`POST /{id}/publish`、`POST /{id}/pause`、`POST /{id}/enter`、`POST /instance/{instanceId}/next` | `MktJourney` + `MktJourneyInstance` + `mkt_journey*` | `MarketingAutomationService.createJourney/updateJourney/publishJourney/pauseJourney/enterJourney/nextNode/journeyPage/journeyDetail` |
| **MktTemplateController** | **3** | `/mkt/template` | `GET /list`、`POST /`、`PUT /{id}/audit` | `MktChannelTemplate` + `mkt_channel_template` | `MarketingAutomationService.templateList/createTemplate/auditTemplate` |
| **MktTouchTaskController** | **9** | `/mkt/task` | `POST /page`、`GET /page`、`POST /`、`POST /{id}/start`、`POST /{id}/pause`、`POST /{id}/resume`、`POST /{id}/cancel`、`GET /{id}/log` | `MarketingTouchTask` + `MarketingTouchLog` + `marketing_touch_*` | `MarketingAutomationService.taskPage/createTask/startTask/pauseTask/resumeTask/cancelTask/taskLogPage` |
| **MktEffectController** | **2** | `/mkt/effect` | `GET /snapshot`、`POST /snapshot/save` | `MktPromotion` + `MktCoupon` + `MktJourneyInstance` + `mkt_promotion` + `mkt_coupon` | 直接注入 5 mapper（JdbcTemplate + 4 mapper）|
| **MktCouponController** | **9** | `/mkt/coupon` | `POST /batch/page`、`POST /batch`、`POST /batch/{id}/publish`、`POST /batch/{id}/end`、`POST /batch/{id}/grant`、`POST /expire-sweep`、`GET /page`、`GET /export` | `MktCouponBatch` + `MktCoupon` + `mkt_coupon_batch` + `mkt_coupon` | 直接注入 `MktCouponBatchMapper` + `MktCouponMapper` + `JdbcTemplate` |
| **MktPromotionController** | **7** | `/mkt/promotion` | `GET /page`、`GET /`（含空路径）、`POST /`、`PUT /{id}`、`DELETE /{id}`、`GET /export` | `MktPromotion` + `mkt_promotion` | 直接注入 `MktPromotionMapper` |
| **MktPointsController** | **4** | `/mkt/points` | `GET /rule/list`、`POST /rule`、`PUT /rule/{id}`、`POST /award` | `MktPointsRule` + `mkt_points_rule` | 直接注入 `MktPointsRuleMapper`；award 通过 `PosController` 积分扣减逻辑（`PosController.java:81` 引用 `MktPointsRuleMapper` + `MarketingEventPublisher`）|
| **合计** | **54 端点** | — | — | — | — |

### 1.5.3 Feature 清单（27 项 — F114-F140）

> 列：Feature ID / Module / 功能点 / Priority / 需求来源 / 前端入口 / 后端入口 / DB表 / 静态可达性 / 备注
> KNOWN-CONSTRAINT 标签说明（沿用 Batch 1.1-1.4）：
> - **⚠ MISSING**：表 DDL 已写但 db-snapshot.sql 未应用（用户决策 #A 第六次统一不跑迁移）
> - **⚠ CONFLICT**：SoT vs 代码描述方向不一致（中立标 I055+）
> - **⚠ PARTIAL-NON-BLOCKER**：refactor leftover / dead code / Frontend Shadow / 跨租户读风险
> - **⚠ PARTIAL-BLOCKER**：重构中间态（旧类残留 + 新类已建 + 表未迁移）

| Feature ID | Module | 功能点 | Priority | 需求来源 | 前端入口 | 后端入口 | DB表 | 静态可达性 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| **F114** | MKT Channel / Controller | 渠道列表查询 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/channel.vue`（路由 `/mkt/channel`） | `MktChannelController.list()` (`controller/MktChannelController.java:44-47`) → `MarketingAutomationService.channelList()` (`service/MarketingAutomationService.java:55-65`) | `mkt_channel_config` | L0 | 强制 `tenant_id=TenantContextHolder.get().getTenantId()` 过滤；JdbcTemplate 直查 15 列；`Keys.camel(rows)` 转驼峰 |
| **F115** | MKT Channel / Controller | 创建渠道配置 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/channel.vue` | `MktChannelController.create()` (`controller/MktChannelController.java:50-52`) → `MarketingAutomationService.createChannel()` (`:67-83`) | `mkt_channel_config` | L0 | 校验 `channelType` 必填；同租户同品牌同渠道查重 → `BizCode.CONFLICT`；默认值 `status=1/retryMax=3/retryIntervalSec=60/rateLimitPerMin=60`；⚠ `rateLimitPerDay` 缺少 setter 调用（line 81）|
| **F116** | MKT Channel / Controller | 更新渠道配置 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/channel.vue` | `MktChannelController.update()` (`controller/MktChannelController.java:55-57`) → `MarketingAutomationService.updateChannel()` (`:85-92`) | `mkt_channel_config` | L0 | 校验存在性 → `BizCode.NOT_FOUND`；`c.setTenantId(old.getTenantId())` 强制保留原租户（防越权写）|
| **F117** | MKT Channel / Controller | 删除渠道配置 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/channel.vue` | `MktChannelController.delete()` (`controller/MktChannelController.java:60-62`) → `MarketingAutomationService.deleteChannel()` (`:94-97`) | `mkt_channel_config` | L0 | 直接 `deleteById(id)`，**未校验租户归属** — 跨租户删除风险 ⚠（与 I018 同模式）— 见 I056 |
| **F118** | MKT Channel / Controller | 渠道健康检查 | P2 | scope-freeze §2.3 + controller 注释 + EXT-19 §0.5 | `views/mkt/channel.vue`（待 Phase 2 grep） | `MktChannelController.healthCheck()` (`controller/MktChannelController.java:65-67`) → `ChannelDispatcher.healthCheckAll()` (`service/channel/ChannelDispatcher.java:48-66`) | `mkt_channel_config` | L0 | **⚠ 沿用 I050 T07-D4 例外**：硬编码 `Long tenantId = 1L`；log.warn 声明设计意图；只更新 tenant=1 的 `last_health_at`/`last_health_msg`；5 sender 轮询 → OK/FAIL |
| **F119** | MKT Conversion / Controller | 转化漏斗统计 | P3 | scope-freeze §2.3 + controller 注释 | `views/mkt/conversion.vue` | `MktConversionController.funnel()` (`controller/MktConversionController.java:33-37`) → `MarketingAutomationService.conversionFunnel()` (`service/MarketingAutomationService.java:638-673`) | `marketing_touch_conversion` + `marketing_touch_log` | L0 | 4 段漏斗：触达 → 发送 → 已读 → 转化；JdbcTemplate 聚合查询 |
| **F120** | MKT Conversion / Controller | ROI 统计 | P3 | scope-freeze §2.3 + controller 注释 | `views/mkt/conversion.vue` | `MktConversionController.roi()` (`controller/MktConversionController.java:40-42`) → `MarketingAutomationService.conversionRoi()` (`:675-713`) | `marketing_touch_conversion` | L0 | 按 taskId 过滤 ROI = 转化收益 / 触达成本 |
| **F121** | MKT Conversion / Controller | 按渠道统计 | P3 | scope-freeze §2.3 + controller 注释 | `views/mkt/conversion.vue` | `MktConversionController.byChannel()` (`controller/MktConversionController.java:45-47`) → `MarketingAutomationService.byChannel()` (`:715-729`) | `marketing_touch_log` + `marketing_touch_conversion` | L0 | 按 `channel_type` 分组聚合 |
| **F122** | MKT Conversion / Controller | 记录转化（业务回调）| P3 | scope-freeze §2.3 + controller 注释 | (无 UI 入口；业务回调用) | `MktConversionController.track()` (`controller/MktConversionController.java:50-55`) → `MarketingAutomationService.trackConversion()` (`:731-760`) | `marketing_touch_conversion` | L0 | 写入转化事件；**未加租户隔离校验**（line 731）— 跨租户写风险 ⚠ — 见 I057 |
| **F123** | MKT Conversion / Controller | 标记已读（渠道回调）| P3 | scope-freeze §2.3 + controller 注释 | (无 UI 入口；渠道回调用) | `MktConversionController.markRead()` (`controller/MktConversionController.java:58-60`) → `MarketingAutomationService.markRead()` (`:762-`) | `marketing_touch_log` | L0 | 标记触达日志为已读 |
| **F124** | MKT Journey / Controller | 创建旅程 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/journey.vue` | `MktJourneyController.create()` (`controller/MktJourneyController.java:38-66`) → `MarketingAutomationService.createJourney()` (`:417-`) | `mkt_journey` | L0 | body 包含 journey_type / nodes / triggers；`@Transactional` |
| **F125** | MKT Journey / Controller | 旅程分页（POST + GET 双形式）| P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/journey.vue` | `MktJourneyController.page()` (`controller/MktJourneyController.java:42-60`) → `MarketingAutomationService.journeyPage()` (`:467-499`) | `mkt_journey` | L0 | POST `/page` body 参数 + GET `/page` query 参数**双路由形式**（同 handler）— 见 I058 |
| **F126** | MKT Journey / Controller | 旅程详情 + 更新 + 发布 + 暂停 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/journey.vue` | `MktJourneyController.detail/update/publish/pause` (`:63-78`) → `MarketingAutomationService` 4 方法 | `mkt_journey` | L0 | 4 端点合 1 Feature；发布/暂停不改 journey 状态机（需 Phase 3 全文核实）|
| **F127** | MKT Journey / Controller | 会员进入旅程 + 推进下一节点 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/journey.vue` | `MktJourneyController.enter()` + `nextNode()` (`:83-91`) → `MarketingAutomationService.enterJourney()` (`:521-`) + `nextNode()` (`:560-`) | `mkt_journey_instance` | L0 | `@Transactional(rollbackFor = Exception.class)`；nextNode 通过 `MktChannelTemplate` 触发触达（line 598 引用 tplCode）|
| **F128** | MKT Template / Controller | 模板列表 + 创建 + 审核 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/template.vue` | `MktTemplateController.list/create/audit` (`:32-44`) → `MarketingAutomationService` 3 方法 | `mkt_channel_template` | L0 | 3 端点；audit 用 `@PutMapping("/{id}/audit")` 改模板状态 |
| **F129** | MKT TouchTask / Controller | 任务分页 + 创建 + 启动/暂停/恢复/取消 + 日志 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/touch-task.vue` | `MktTouchTaskController` 9 端点 (`controller/MktTouchTaskController.java:37-86`) → `MarketingAutomationService` 7 方法 | `marketing_touch_task` + `marketing_touch_log` | L0 | 9 端点；POST `/page` + GET `/page` 双形式（同 F125 模式）|
| **F130** | MKT Effect / Controller | 效果快照 + 生成快照 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/effect.vue` | `MktEffectController.snapshot()` + `snapshotSave()` (`controller/MktEffectController.java:64, 175`) | `mkt_promotion` + `mkt_coupon` + `mkt_journey_instance` | L0 | 聚合 4 表统计；snapshot/save 注释说"实战 cron job 用"，**save 仅 print 日志未真正写表** ⚠ — 见 I059 |
| **F131** | MKT Coupon / Controller | 批次分页 + 创建 + 发布 + 结束 + 发放 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/coupon.vue` | `MktCouponController.batchPage/createBatch/publish/endBatch/grantBatch` (`:52-220`) | `mkt_coupon_batch` + `mkt_coupon` | L0 | 5 端点；grant 时 memberIds 缺则发放给全部 active 会员（line 140+）|
| **F132** | MKT Coupon / Controller | 过期扫描 + 券实例分页 + 导出 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/coupon.vue` | `MktCouponController.expireSweep/couponPage/export` (`:230-298`) | `mkt_coupon` + `mkt_coupon_batch` | L0 | expire-sweep 直接 `UPDATE ... status = 3`（⚠ 缺 try/catch — 与 I047 同模式但更严重）— 见 I060 |
| **F133** | MKT Promotion / Controller | 分页 + 列表 + 创建 + 更新 + 删除 + 导出 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/promotion.vue` | `MktPromotionController` 7 端点 (`controller/MktPromotionController.java:47-142`) | `mkt_promotion` | L0 | 7 端点；`@GetMapping({"/", ""})` 双路径（list）；导出 CSV 带 UTF-8 BOM |
| **F134** | MKT Points / Controller | 积分规则列表 + 创建 + 更新 + 手动发分 | P2 | scope-freeze §2.3 + controller 注释 | `views/mkt/points.vue` + `PosController` 收银链路 (`:81, 408-410`) | `MktPointsController` 4 端点 (`controller/MktPointsController.java:44-99`) | `mkt_points_rule` | L0 | 4 端点；award 调用链：`PosController.checkout()` → `MktPointsRule.pointsToCash` 计算（默认 100:1）|
| **F135** | ChannelDispatcher | 渠道分发（按 channelType 路由） | P2 | EXT-19 §0.5 + scope-freeze §2.3 | (无 UI；由 F118 + F127 nextNode 调用) | `ChannelDispatcher.getSender(channelType)` (`service/channel/ChannelDispatcher.java:34-39`) | — | L0 | `List<ChannelSender> senders` 注入；按 `s.channelType().equals(channelType)` 过滤；**miss 返回 null（与 I012 同模式 — Router fallback 风险）** — 见 I061 |
| **F136** | ChannelDispatcher | 限流检查（每分钟 + 每日） | P2 | scope-freeze §2.3 + Batch 1.5 新增 | (无 UI；由 MarketingTouchTask 调用) | `ChannelDispatcher.rateLimitCheck(tenantId, brandId, channelType)` (`:70-90`) | `mkt_channel_config` + `marketing_touch_log` | L0 | 双重检查：`rateLimitPerMin` + `rateLimitPerDay`；JdbcTemplate 聚合 |
| **F137** | ChannelSender × 5 | SMS / EMAIL / MINI_PROGRAM / IN_APP / WECHAT_OA | P2 | EXT-19 §1 + scope-freeze §2.3 | (无 UI；由 F135 调用) | 5 个 `@Component` 实现类 (`service/channel/*Sender.java`) | — | L0 | 5 Sender 共用 EXT-19 的发送链（**MKT 不修改 sender 实现，仅复用** — scope-freeze §九.9）；⚠ **WECHAT_OA vs WECHAT_OFFICIAL_ACCOUNT 别名**（沿用 I039 CONFLICT）|
| **F138** | MarketingAutomationService | 业务入口聚合（T-D21~D24） | P2 | scope-freeze §2.3 + EXT-19 §1 | (无 UI；被 9 个 Mkt*Controller 调用) | `MarketingAutomationService` (`service/MarketingAutomationService.java:35-763`) — ~25 方法 | `mkt_channel_config` + `mkt_channel_template` + `mkt_journey*` + `marketing_touch_*` + `mkt_brand` + `mkt_segment` + `mkt_invitation_rule` + `mkt_invitation` + `mkt_order_discount` + `mkt_dynamic_pricing_rule` + `mkt_pricing_log` | L0 | `@Service`（非 `@RestController`，**虽然 import 了 web.bind.annotation.* 但实际只在方法签名上用 `@PathVariable`/`@RequestBody` 等装饰**）；委派 9 mapper + JdbcTemplate + ChannelDispatcher；⚠ **`createChannel` 缺 `rateLimitPerDay` setter**（line 81 编译能过但没设置）；⚠ **`@Transactional(rollbackFor = Exception.class)`** 在 createTask/startTask/enterJourney/nextNode 4 方法上 — **good pattern** |
| **F139** | MKT Refactor / 中间态 | 旧 `MarketActivity*` 5 个类残留 | P0 | docs/ROLLBACK-6.2 §1（SoT） + scope-freeze §九.2 | (无 UI；旧路由仍可达) | `entity/MarketActivity.java` + `mapper/MarketActivityMapper.java` + `service/MarketActivityService.java` + `service/impl/MarketActivityServiceImpl.java` + `controller/MarketActivityController.java` | `market_activity`（**db-snapshot.sql:5177 仍在**） | L0（旧类）/ **⚠ PARTIAL-BLOCKER**（中间态 + 实际引用）| ⚠ **重大事实漂移**：SoT §1 注释 "Java 侧已无读写"，但 `BusinessPolicyDispatchServiceImpl.applyActivity()` (`:264-278`) 实际仍调用 `marketActivityMapper.selectById` + `insert(clone)` → 见 **I055**（中立标 + SoT 反向证据）|
| **F140** | MKT Refactor / Diff Audit | Phase 3.5 Diff Audit 8 项 | P1 | scope-freeze §十一 + preflight §七.5 | (无 UI；本批次审计任务) | `audit/diff-audit/mkt-refactor.md`（待 Phase 1 末写） | — | L0（报告产出）| 8 项审计清单（详见 §1.5.5 + diff-audit 报告）|

### 1.5.4 Feature 数量统计

| 分类 | 数量 | Feature IDs |
|---|---|---|
| MKT Channel | 5 | F114-F118 |
| MKT Conversion | 5 | F119-F123 |
| MKT Journey | 4 | F124-F127 |
| MKT Template | 1 | F128 |
| MKT TouchTask | 1 | F129 |
| MKT Effect | 1 | F130 |
| MKT Coupon | 2 | F131-F132 |
| MKT Promotion | 1 | F133 |
| MKT Points | 1 | F134 |
| ChannelDispatcher + 5 Sender | 3 | F135-F137 |
| MarketingAutomationService | 1 | F138 |
| MKT Refactor 中间态 + Diff Audit | 2 | F139-F140 |
| **合计** | **27** | **F114-F140** |

> ⚠ **预估偏差**：scope-freeze §3.3 估算 42-50 Features，本批次实际 27 Features（**节省 15-23 个**）— 原因：① 部分 controller 端点聚合到 1 Feature（F129 把 9 端点合 1 Feature） ② MarketingAutomationService 聚合到 1 Feature（F138）③ 跨表 mapper / entity 不单独列 Feature ④ 不重复 EXT-19 已审计 sender

### 1.5.5 Phase 1 Issue 清单（不进 Phase 2 前必须决策）

| Issue ID | 类别 | 描述 | 影响 Feature | 建议处理 |
|---|---|---|---|---|
| **I055** ⭐ NEW | **CONFLICT（SoT 反向证据）🔴 CRITICAL-P2** | `docs/ROLLBACK-6.2-mkt_ddl.md` §1 注释 "Java 侧 (BusinessPolicyDispatchServiceImpl) 已无对 market_activity 的读写" — **与代码不符**。`BusinessPolicyDispatchServiceImpl.applyActivity()` (`service/impl/BusinessPolicyDispatchServiceImpl.java:264-278`) 实际调用 `marketActivityMapper.selectById(d.getSourceId())` (line 264) + `marketActivityMapper.insert(clone)` (line 277)；`marketActivityMapper` 字段注入 (line 47)。**SoT 描述方向反向**（声称无引用 → 实际有强引用）。若按 SoT 描述执行 `migrate_mkt_002_drop_market_activity.sql` → DISPATCH ACTIVITY 子类型运行时 NPE/异常 | F139 + 任何调用 `M12-05 /m12/dispatch` ACTVITY 子类型的链路 | **必修 SoT**：① 删 SoT §1 "已无读写" 注释；② 或在 `migrate_mkt_002_drop` 前先重构 `BusinessPolicyDispatchServiceImpl` 移除 `applyActivity()`；③ **统一 Post-Audit 决策**；本批次**不跑迁移** |
| **I056** ⭐ NEW | 跨租户删除（PARTIAL-NON-BLOCKER）| `MktChannelController.delete()` (`controller/MktChannelController.java:60-62`) 直接 `channelConfigMapper.deleteById(id)`，**未校验 `old.tenantId == currentTenantId`** — 跨租户删除风险 ⚠（沿用 I018 模式） | F117 | 决策：① 加 `t.equals(old.getTenantId())` 校验；② 接受现状（依赖 SQL 层兜底） |
| **I057** ⭐ NEW | 跨租户写（PARTIAL-NON-BLOCKER）| `MktConversionController.track()` + `markRead()` (`controller/MktConversionController.java:50-60`) 写入 `marketing_touch_conversion` / `marketing_touch_log` 时**未加 tenantId 过滤/校验**（`MarketingAutomationService.trackConversion` `:731-760` body 直接取 `tenantId` 而非从 TenantContextHolder 取）— 跨租户写风险 ⚠（沿用 I019 模式） | F122 + F123 | 决策：① `trackConversion` 强制 `tenantId = TenantContextHolder.get().getTenantId()`；② 接受现状（依赖业务方传入） |
| **I058** ⭐ NEW | Frontend 双路由形式（PARTIAL-NON-BLOCKER）| `MktJourneyController.page()` (`controller/MktJourneyController.java:42-60`) 同时支持 `POST /mkt/journey/page` body 参数 + `GET /mkt/journey/page` query 参数 — **同 handler 双形式**；`MktTouchTaskController` 同样模式（line 37/47）。可能让前端误判协议；语义模糊 | F125 + F129 | 决策：① 拆为两个独立 endpoint（POST body + GET query）；② 接受现状（前端已用其中之一） |
| **I059** ⭐ NEW | dead code（snapshot/save 仅 print 日志）| `MktEffectController.snapshotSave()` (`controller/MktEffectController.java:175-`) 实现 "生成当日快照"，注释说 "实战 cron job 用"，但实现仅 `log.info` + 不写任何表 ⚠（与 Operational Notes §0.5a PluginPersistenceService 缺 autoLoaded 填充同模式 — 未真正持久化）| F130 | 决策：① 写 `mkt_effect_snapshot` 表；② 删 endpoint |
| **I060** ⭐ NEW | **CRITICAL-P1 catch 吞异常 + 缺 audit** | `MktCouponController.expireSweep()` (`controller/MktCouponController.java:230-246`) 直接 `UPDATE mkt_coupon ... status = 3` + `UPDATE mkt_coupon_batch ... status = 3`，**未返回受影响行数给前端，仅记 result map**；**未加 try/catch** — 异常时 Spring 默认 500 但 SQL 部分已执行；**未写 audit log**（谁跑的、扫了多少条、改了多少条不可追溯）| F132 | 决策：① 加 audit log 表 `coupon_expire_sweep_log`；② 加 try/catch 失败回滚；③ 加 ADMIN 守卫（沿用 I032 模式）|
| **I061** ⭐ NEW | **CRITICAL-P2 Router fallback null** | `ChannelDispatcher.getSender(channelType)` (`service/channel/ChannelDispatcher.java:34-39`) miss 时 `findFirst().orElse(null)` — **NPE 风险**（与 Batch 1.1 I012 Mock fallback NPE 完全同模式 — Router 不会 fallback，调用方拿到 null 直接 NPE）| F135 + 任何调用 `dispatcher.getSender(...)` 的链路 | 决策：① 改 `orElseThrow(...)` + `BizCode.NOT_FOUND`；② 加 mock fallback ChannelSender（沿用 I012 Mock 模式）|
| **I062** ⭐ NEW | dead code（rateLimitPerDay setter 缺失）| `MarketingAutomationService.createChannel()` (`service/MarketingAutomationService.java:67-83`) 设置默认值时，line 81 是 `if (c.getRateLimitPerDay() == null) c.getRateLimitPerDay();` — **空语句**（无 setter 调用），编译能过但**实际未设默认值**。默认 `rateLimitPerDay` 应为某个值（如 10000）| F115 + F138 | 决策：① 改为 `c.setRateLimitPerDay(10000)`；② 删除空语句（依赖 entity 默认值）|
| **I063** ⭐ NEW | **CRITICAL-P2 表 MISSING（4 张）** | `MktInvitation` / `MktInvitationRule` / `MktDispatch` / `MktDispatchTarget` 4 张表 `@TableName` 实体类已建 + Mapper 已建 + `BusinessPolicyDispatchController` 注入使用，但 `db-snapshot.sql` 0 匹配（**Phase 1 静态扫描发现**）— **运行时会 TableNotFoundException** — **未跑迁移** ⚠ | F138（间接）+ 任何调用 `BusinessPolicyDispatchController` 的链路 + I063 自身 | **统一 Post-Audit 跑批**（用户决策 #A 第六次统一不跑迁移）；`migrate_mkt_004_invitation.sql` + `migrate_mkt_005_dispatch.sql` 待 DBA 写 |
| **I064** ⭐ NEW | Frontend Shadow / API 完全脱节 | `api/market.ts` 18 端点全部走 `/market/coupon/*` + `/market/point/*` + `/market/activity/*`（旧路径），**与 9 个新 Mkt*Controller 路由 `/mkt/*` 完全脱节**；`api/` 目录无 `mkt.ts` 文件；前端 `views/mkt/*.vue` 调用需 Phase 2 grep 确认实际路径。**前端若直接调新 Controller → 全部失败** | F114-F134 全部前端 UI 入口 | 决策：① 写 `api/mkt.ts` 封装 `/mkt/*` 调用；② 接受现状（旧路径仍可走 `CouponController` 但仅覆盖部分功能） |
| **I065** ⭐ NEW | 受保护页面 dashboard 间接风险解除 | preflight §八.10 + scope-freeze §九.10 标记 `dashboard.vue` 渠道统计可能调用 `/mkt/channel/health-check`。Phase 1 静态扫描 `dashboard.vue` 实际只引用 `/market/coupon`（旧 CouponController）+ `/mkt/points`（新路由 push）— **未调用 `/mkt/channel/*` 或 ChannelDispatcher.healthCheckAll** | dashboard | ✅ **风险解除**；dashboard 不显式调用 MKT Channel；登记决策已落实 |
| **I066** ⭐ NEW | I054 NEW 状态维持 + 实际表存在 | preflight §九.3 + §0.1 标记 `mkt_channel_config` 表缺失（I054 NEW）。Phase 1 db-snapshot.sql line 6313 grep 已确认 **表存在** ✅ — I054 NEW 推论**部分反向**，但 ChannelDispatcher.healthCheckAll 沿用 I050 单租户问题仍在 | F118 | 决策：① 关闭 I054 NEW 段落登记（表已存在）；② 保留 I050 ChannelDispatcher 单租户硬编码（沿用）|
| **I067** ⭐ NEW | 重构中间态（5 旧类 + 9 新 Controller + 表未 DROP）| scope-freeze §九.2 标记的 5 个旧 `MarketActivity*` 类 + 9 个新 Mkt*Controller + `market_activity` 表未 DROP + `MktCouponController` 仍引用 `mkt_coupon_batch`（新表，已存在）。**重构中间态特征明显**，与 SoT 描述"完全重构"反向 | F114-F140 全部 | 决策：① 接受中间态 + Phase 3.5 Diff Audit 必跑；② 本批次不修业务代码 |
| **I068** ⭐ NEW | SoT 字段不一致（统计字段缺失）| `docs/ROLLBACK-6.2-mkt_ddl.md` §2 描述 `market_activity` 为 "9 列 + rule_config JSON"；db-snapshot.sql line 5177 实际表结构待 Phase 5 验证列数 | F139 | Phase 5：`DESCRIBE market_activity` 验证列对齐；不阻塞 Phase 2 |

### 1.5.6 与 MKT-002 SoT 的冲突点（SoT 比对）

> 按 `project-audit.mdc` 规则 11：审计结论与 SoT Operational Notes / 文档冲突时，**以 SoT 为准并标 ISSUE**。
> 本批次 SoT 为 `docs/ROLLBACK-6.2-mkt_ddl.md`（**⚠ 无 .mdc Operational Notes — 退化路径**）。

| SoT 章节 / 文档 | 文档声称 | 代码事实 | 处理 |
|---|---|---|---|
| `ROLLBACK-6.2 §1` 决策依据 | "Java 端引用: 无（BusinessPolicyDispatchServiceImpl 已注释说明）"| `BusinessPolicyDispatchServiceImpl.applyActivity()` (`:264-278`) 实际调用 `marketActivityMapper.selectById` + `insert` | **I055 CRITICAL-P2（反向证据）** |
| `ROLLBACK-6.2 §1` 决策依据 | "替代表: `mkt_promotion`（30+ 列展开式）" | `entity/MktPromotion.java` @TableName mkt_promotion + db-snapshot.sql line 6761 表存在 ✅ | OK |
| `ROLLBACK-6.2 §2` 主动作 | "探测表存在 → 探测行数 → RENAME → DROP" | `migrate_mkt_002_drop_market_activity.sql` 严格按此顺序 ✅ | OK |
| `ROLLBACK-6.2 §3` 回滚预案 | "归档表 `market_activity__archived_20260910` 探测 → 反向 RENAME" | `migrate_mkt_002_rollback.sql` 严格按此逻辑 ✅ | OK |
| `ROLLBACK-6.2 §3` 当前限制 | "选项 A 路径下，归档表已被立即 DROP，回滚失效；真正可用回滚是 mysqldump + binlog" | SQL 脚本 line 65 `DROP TABLE market_activity__archived_20260910` ✅ | OK |
| `ROLLBACK-6.2 §5` 验证步骤 | "`migrate_mkt_002_drop_market_activity.sql` 已存在但未跑" | `git status` + Phase 1 静态确认 ✅ | OK |
| `ROLLBACK-6.2 §7` 风险缓解 | "DROP 后下游系统报错: 上线前做 grep 全代码库 `market_activity` 引用核查（已做: 0 处业务代码）" | Phase 1 grep 找到 `BusinessPolicyDispatchServiceImpl.applyActivity()` 实际引用 — **与"0 处"矛盾** | **I055** |
| preflight §九.3 + §0.1 I054 NEW | `mkt_channel_config` 表缺失 | db-snapshot.sql line 6313 表已存在 ✅ | **I066 状态更新** |
| preflight §八.8 dashboard 间接风险 | dashboard 可能调用 ChannelDispatcher | dashboard.vue 静态扫描仅引用 `/market/coupon` + `/mkt/points` — **未调用 ChannelDispatcher** | **I065 风险解除** |

### 1.5.7 与 db-snapshot.sql 的表存在性交叉引用（preflight §八交叉引用）

> 用户决策：交叉引用 preflight §八.8（市场活动相关表清查）

| 表名 | 实体类存在？ | Mapper 存在？ | db-snapshot.sql line | Controller 使用？ | 状态 |
|---|---|---|---|---|---|
| `market_activity` | ✅ `MarketActivity.java` | ✅ `MarketActivityMapper.java` | **5177** | `MarketActivityController` + `BusinessPolicyDispatchServiceImpl.applyActivity()` | ⚠ **未 DROP**（I055 重大引用 + I067 中间态） |
| `mkt_channel_config` | ✅ `MktChannelConfig.java` | ✅ `MktChannelConfigMapper.java` | **6313** | `MktChannelController` + `MarketingAutomationService` + `ChannelDispatcher.rateLimitCheck` | ✅ 存在（I054 NEW 推论反向 — 见 I066） |
| `mkt_channel_template` | ✅ `MktChannelTemplate.java` | ✅ `MktChannelTemplateMapper.java` | **6348** | `MktTemplateController` + `MktEffectController` + `MarketingAutomationService.nextNode` | ✅ 存在 |
| `mkt_coupon` | ✅ `MktCoupon.java` | ✅ `MktCouponMapper.java` | **6384** | `MktCouponController` + `MktEffectController` | ✅ 存在 |
| `mkt_coupon_batch` | ✅ `MktCouponBatch.java` | ✅ `MktCouponBatchMapper.java` | **6424** | `MktCouponController` | ✅ 存在 |
| `mkt_dynamic_pricing_rule` | ✅ `MktDynamicPricingRule.java` | ✅ `MktDynamicPricingRuleMapper.java` | **6463** | `BusinessPolicyDispatchServiceImpl.applyExperiment` 旁路 | ✅ 存在 |
| `mkt_journey` | ✅ `MktJourney.java` | ✅ `MktJourneyMapper.java` | **6505** | `MktJourneyController` + `MarketingAutomationService` | ✅ 存在 |
| `mkt_journey_instance` | ✅ `MktJourneyInstance.java` | ✅ `MktJourneyInstanceMapper.java` | **6540** | `MarketingAutomationService.nextNode` + `MktEffectController` | ✅ 存在 |
| `mkt_journey_trigger` | ✅ `JourneyTriggerRule.java` | ✅ `JourneyTriggerRuleMapper.java` | **6567** | `JourneyTriggerController` | ✅ 存在 |
| `mkt_journey_trigger_log` | ✅ `JourneyTriggerLog.java` | ✅ `JourneyTriggerLogMapper.java` | **6604** | `JourneyTriggerController` | ✅ 存在 |
| `mkt_order_discount` | ✅ `MktOrderDiscount.java` | ✅ `MktOrderDiscountMapper.java` | **6638** | (无直接 Controller；间接 MarketingAutomationService) | ✅ 存在 |
| `mkt_points_log` | ✅ `MktPointsLog.java` | ✅ `MktPointsLogMapper.java` | **6670** | `MktPointsController.award`（间接）+ PosController 收银 | ✅ 存在 |
| `mkt_points_rule` | ✅ `MktPointsRule.java` | ✅ `MktPointsRuleMapper.java` | **6699** | `MktPointsController` + PosController | ✅ 存在 |
| `mkt_pricing_log` | ✅ `MktPricingLog.java` | ✅ `MktPricingLogMapper.java` | **6729** | (PricingExperimentController) | ✅ 存在 |
| `mkt_promotion` | ✅ `MktPromotion.java` | ✅ `MktPromotionMapper.java` | **6761** | `MktPromotionController` + `MktEffectController` | ✅ 存在 |
| `mkt_segment` | ✅ `MktSegment.java` | ✅ `MktSegmentMapper.java` | **6803** | (无直接 Controller；间接 MarketingAutomationService.byChannel/funnel) | ✅ 存在 |
| `mkt_brand` | ✅ `MktBrand.java` | ✅ `MktBrandMapper.java` | **6277** | (无直接 Controller；MktChannelConfig.brandId 引用) | ✅ 存在 |
| **`mkt_invitation`** | ✅ `MktInvitation.java` | ✅ `MktInvitationMapper.java` | ❌ **0 匹配** | (无直接 Controller；实体定义但未跑迁移) | 🔴 **CRITICAL MISSING — I063** |
| **`mkt_invitation_rule`** | ✅ `MktInvitationRule.java` | ✅ `MktInvitationRuleMapper.java` | ❌ **0 匹配** | (无直接 Controller；实体定义但未跑迁移) | 🔴 **CRITICAL MISSING — I063** |
| **`mkt_dispatch`** | ✅ `MktDispatch.java` | ✅ `MktDispatchMapper.java` | ❌ **0 匹配** | `BusinessPolicyDispatchController` 直接注入并写入 | 🔴 **CRITICAL MISSING — I063** |
| **`mkt_dispatch_target`** | ✅ `MktDispatchTarget.java` | ✅ `MktDispatchTargetMapper.java` | ❌ **0 匹配** | `BusinessPolicyDispatchController` 注入并写入 | 🔴 **CRITICAL MISSING — I063** |
| `marketing_touch_task` | ✅ `MarketingTouchTask.java` | ✅ `MarketingTouchTaskMapper.java` | **5347** | `MktTouchTaskController` + `MarketingAutomationService` | ✅ 存在 |
| `marketing_touch_log` | ✅ `MarketingTouchLog.java` | ✅ `MarketingTouchLogMapper.java` | **5312** | `ChannelDispatcher.rateLimitCheck` + `MktTouchTaskController.log` + `MarketingAutomationService` | ✅ 存在 |
| `marketing_touch_conversion` | ✅ `MarketingTouchConversion.java` | ✅ `MarketingTouchConversionMapper.java` | **5287** | `MktConversionController` + `MarketingAutomationService` | ✅ 存在 |

**统计**：22 个 Mkt*/MarketingTouch* 表存在，**4 个表 CRITICAL MISSING（I063）** — 全部为 MKT 重构新建表但未跑迁移

### 1.5.8 未确认项（标记"未确认"，禁止猜测）

| # | 项 | 当前信息 | 待补来源 |
|---|---|---|---|
| 1 | `BusinessPolicyDispatchServiceImpl.applyActivity()` 是否真的调用 — SoT 是否更新 | grep 已确认调用 | Phase 5 跑 `BusinessPolicyDispatchServiceImpl` 单元测试 |
| 2 | `migrate_mkt_002_drop_market_activity.sql` 实际跑批结果 | 未跑 | Phase 5 MySQL MCP 验证；本批次**不跑**（Q3 第 6 次）|
| 3 | 4 张 CRITICAL MISSING 表（mkt_invitation/mkt_invitation_rule/mkt_dispatch/mkt_dispatch_target）的 DDL 文件 | grep `init_*.sql` / `migrate_*.sql` 0 匹配 → **DDL 不存在** | DBA 补写 DDL；本批次不跑 |
| 4 | MarketingAutomationService 25+ 方法的完整调用链 | 已识别主调用方（9 Controller）；MarketingEventPublisher 间接链路未深挖 | Phase 3 全文 grep |
| 5 | `MktCouponController.expire-sweep` 的 ADMIN 守卫 | 当前实现无守卫 — I060 | Phase 3 加 ADMIN 守卫 |
| 6 | `MktEffectController.snapshot/save` 真正写入的表 | 注释说 mkt_effect_snapshot；但 db-snapshot.sql 0 匹配 — I059 | DBA 补 DDL；本批次不跑 |
| 7 | `ReportV2Service` 是否属于 MKT Batch 1.5 范围 | 12 端点涉及 report/v2/* 但**不属于 MKT 9 Controller**；属相邻范围 | 用户决策：本批次仅审计 MKT 9 + ChannelDispatcher + 旧类；ReportV2 不纳入范围（见 F 列说明） |
| 8 | 前端 `views/mkt/*.vue` 实际调用的 API 路径 | 9 个 vue 文件存在；api/market.ts 走旧路径 | Phase 2 grep 所有 `views/mkt/*.vue` 的 `request.*` 调用 |
| 9 | I054 NEW `mkt_channel_config` 表实际状态 | db-snapshot.sql line 6313 表存在 ✅ | **状态更新**（I066）|

### 1.5.9 与 Batch 1.1/1.2/1.3/1.4 独立性 + 重叠检查

| 检查项 | 结果 |
|---|---|
| **代码包共享** | ⚠ `service/channel/ChannelSender` × 5 + `ChannelDispatcher` 在 Batch 1.4 EXT-19 与本批次共用 — 但 MKT 不修改 sender 实现（scope-freeze §九.9）|
| **DB 表共享** | ⚠ `mkt_channel_config` 与 EXT-19 EmailSender/SmsSender 共用（Batch 1.4 I050 已标注 tenant_id=1L 硬编码）|
| **配置共享** | ⚠ `spring.mail.*` 全局配置 — 同 EXT-19 mismatch demo 模式 |
| **业务依赖** | ❌ MKT 9 Controller 与 EXT-19/22/23/24 业务逻辑不直接依赖 |
| **审计可分离性** | ✅ Phase 1 Feature Inventory 27 个完全独立编号 F114-F140 |
| **Issue ID 编号** | 接续 I054（沿用 NEW 状态）→ I055-I068（14 个新 Issue）|

---

**Phase 1 产出（Batch 1.5）完成。本批次 27 个 Feature 已落 `audit/feature-inventory.md`（节省 15-23 个 vs scope-freeze §3.3 估算 42-50）。**

**已知约束标记汇总**（KNOW-CONSTRAINT，强制关注）：

- 🔴 **CRITICAL-P2 SoT 反向证据**：I055 `BusinessPolicyDispatchServiceImpl.applyActivity()` 仍引用 MarketActivity — **与 SoT §1 "Java 侧已无读写" 矛盾**
- 🔴 **CRITICAL-P2 Router NPE 风险**：I061 `ChannelDispatcher.getSender()` miss 返回 null（沿用 I012 同模式）
- 🔴 **CRITICAL-P1 catch 吞 + 缺 audit**：I060 `MktCouponController.expireSweep()` 缺 audit + 异常处理
- 🔴 **CRITICAL-P2 4 表 MISSING**：I063 `mkt_invitation` / `mkt_invitation_rule` / `mkt_dispatch` / `mkt_dispatch_target` — entity + mapper 已建但 db-snapshot.sql 0 匹配
- ⚠ **PARTIAL-BLOCKER**：重构中间态（I067 — 5 旧类残留 + 9 新 Controller + 表未 DROP）
- ⚠ **PARTIAL-NON-BLOCKER**：跨租户删除（I056）/ 跨租户写（I057）/ 双路由形式（I058）/ dead code snapshot（I059）/ rateLimitPerDay 空语句（I062）/ Frontend Shadow（I064）
- ✅ **风险解除**：I065 dashboard 不调用 ChannelDispatcher；I066 I054 NEW 状态更新（`mkt_channel_config` 表已存在）

**Phase 3.5 Diff Audit**：⭐⭐⭐ **首个重构批次** — 已完成 8 项审计（详见 `audit/diff-audit/mkt-refactor.md`），阻塞 Phase 2 / 5。

**待用户决策**（不进 Phase 2 前）：

1. **I055 CRITICAL-P2 SoT 反向证据**：`BusinessPolicyDispatchServiceImpl.applyActivity()` 仍引用 MarketActivity — ① 修 SoT 文档（删"已无读写"）② 重构 `applyActivity()` 走 `MktPromotion`（不修业务代码延后）？
2. **I056 I057 跨租户风险**：MktChannel.delete + MktConversion.track/markRead 未校验 tenantId — 接受现状 / 加守卫？
3. **I058 双路由形式**：MktJourney.page + MktTouchTask.page 同时支持 POST body + GET query — 拆 / 接受？
4. **I059 I062 dead code**：MktEffect.snapshotSave 仅 print 日志 + createChannel rateLimitPerDay 空语句 — 修 / 删？
5. **I060 CRITICAL-P1 catch + audit**：MktCoupon.expireSweep 缺 audit + 异常处理 — 接受 / 加 audit 表？
6. **I061 CRITICAL-P2 Router fallback**：ChannelDispatcher.getSender miss 返回 null — 改 orElseThrow / 加 mock fallback？
7. **I063 CRITICAL-P2 4 表 MISSING**：mkt_invitation/mkt_invitation_rule/mkt_dispatch/mkt_dispatch_target 表未跑迁移 — **统一 Post-Audit 跑批**？
8. **I064 Frontend Shadow**：api/market.ts 走旧路径，与新 /mkt/* 完全脱节 — 写 api/mkt.ts / 接受？
9. **I067 重构中间态**：5 旧类残留 + 表未 DROP — 接受中间态 / Phase 3.5 量化影响？
10. **I068 SoT 字段不一致**：market_activity 9 列 vs 实际待验证 — Phase 5 DESCRIBE 验证？

---
| §3 Endpoints | `POST /intl-booking/{id}/send-confirmation`（独立手动触发）| 已实现 — 但**前端 intl-booking.ts 无 sendConfirmation 方法**（**I042 Shadow**）| **Shadow**（前端缺失）| **I042 中立标** |
| §4 Channel Routing | "customerPhone → SMS / customerEmail → EMAIL / miniProgramOpenId → MINI_PROGRAM / 任一渠道无对应 receiver → skipped" | `BookingConfirmationService.sendConfirmation` (`:104-140`) 仅路由 3 渠道（SMS/EMAIL/MINI_PROGRAM），IN_APP/WECHAT_OA 未触达 — **与 SoT §4 完全一致**（SoT 仅明确 3 路由，"任一无 receiver → skipped"）| (无漂移) | OK |
| §5 占位符变量 | 9 个 `${var}`：customerName/customerPhone/customerEmail/serviceName/bookingDate/bookingTime/bookingNo/storeName/storePhone | `BookingConfirmationService.buildVars` (`:157-167`) 8 变量（缺 customerPhone/customerEmail — **仅在有 receiver 时才路由 SMS/EMAIL，不需在 vars** — 设计合理）；6 locale JSON 中占位符覆盖 customerName/serviceName/bookingDate/bookingTime/bookingNo/storeName/storePhone **7 变量**（**与 SoT §5 完全一致**）| (无漂移) | OK |

### 1.4.6 6 locale JSON 模板完整性核对（**与 SoT §1 + §5 占位符交叉验证**）

| Locale | subject | sms | miniProgram (title + keyword1..5) | email (subject + body) | 占位符覆盖 | 与 SoT §5 一致 |
|---|---|---|---|---|---|---|
| **zh-CN** | "您的预约已确认" | "尊敬的 ${customerName}，您预约的 ${serviceName} 已确认。时间：${bookingDate} ${bookingTime}。预约号：${bookingNo}。门店：${storeName}。" | title="预约确认通知" + keyword1-5: bookingNo/serviceName/bookingDate+bookingTime/storeName/storePhone | subject="您的预约已确认 — 预约号 ${bookingNo}" + body 含 7 占位符 | ✅ 7 vars | ✅ |
| **zh-TW** | "您的預約已確認" | "尊敬的 ${customerName} 您好，您預約的 ${serviceName} 已確認..." | title="預約確認通知" + keyword1-5: 預約編號/服務項目/預約時間/門市/聯絡電話 | subject="您的預約已確認 — 預約編號 ${bookingNo}" + body 含 7 占位符 | ✅ 7 vars | ✅ |
| **en-US** | "Booking Confirmed" | "Dear ${customerName}, your booking for ${serviceName} is confirmed..." | title="Booking Confirmation" + keyword1-5: BookingNo/Service/Time/Store/Contact | subject="Your Booking is Confirmed — Booking No. ${bookingNo}" + body 含 7 占位符 | ✅ 7 vars | ✅ |
| **ja-JP** | "ご予約確認" | "${customerName}様、${serviceName} のご予約を承りました..." | title="ご予約確認" + keyword1-5: 予約番号/サービス/日時/店舗/お問い合わせ | subject="ご予約確認 — 予約番号 ${bookingNo}" + body 含 7 占位符 | ✅ 7 vars | ✅ |
| **ko-KR** | "예약 확인" | "${customerName}님, ${serviceName} 예약이 확인되었습니다..." | title="예약 확인 알림" + keyword1-5: 예약 번호/서비스/일시/매장/연락처 | subject="예약 확인 — 예약 번호 ${bookingNo}" + body 含 7 占位符 | ✅ 7 vars | ✅ |
| **fr-FR** | "Réservation confirmée" | "Bonjour ${customerName}, votre réservation pour ${serviceName} est confirmée..." | title="Confirmation de réservation" + keyword1-5: N° de réservation/Service/Date et heure/Salon/Contact | subject="Votre réservation est confirmée — N° ${bookingNo}" + body 含 7 占位符 | ✅ 7 vars | ✅ |

**结论**：
- ✅ **6 locale JSON 全部存在 + 内容完整**（与 SoT §1 列 6 locale 完全对齐）
- ✅ **每 locale 均含 subject/sms/miniProgram(6 字段)/email(2 字段) 共 10 字段**
- ✅ **占位符覆盖与 SoT §5 9 变量中 7 一致**（缺 customerPhone/customerEmail — **设计合理**：仅在有 receiver 时才路由 SMS/EMAIL，不在 vars 中传递）
- ✅ **每个 locale 的 `customerName` 占位符均存在**（SoT §5 必填）
- ✅ **每个 locale 的 `serviceName` 占位符均存在**（SoT §5 必填）
- ✅ **每个 locale 的 `bookingDate` + `bookingTime` 占位符均存在**（SoT §5 必填）
- ✅ **每个 locale 的 `bookingNo` 占位符均存在**（SoT §5 必填）
- ✅ **每个 locale 的 `storeName` + `storePhone` 占位符均存在**（SoT §5 必填）

### 1.4.7 未确认项（标记"未确认"，禁止猜测）

| # | 项 | 当前信息 | 待补来源 |
|---|---|---|---|
| 1 | dev DB 中 `overseas_booking` 表是否实际存在 | DDL 在 `migrate_ext_19_mini_program_openid.sql` 已写；`db-snapshot.sql` grep `overseas_booking` **0 匹配已确认** | Phase 5 实际跑迁移 + `DESCRIBE overseas_booking` 验证 26 列 |
| 2 | `migrate_ext_19_mini_program_openid.sql` 是否在生产已跑 | 仅 DDL 文件存在；`db-snapshot.sql` 未含 | Phase 5 实际查询 |
| 3 | dev DB 中 `member_message` 表是否实际存在（InAppSender 依赖）| `db-snapshot.sql` grep `member_message` **0 匹配已确认** | Phase 5 跑迁移 + `DESCRIBE member_message` 验证 |
| 4 | dev DB 中 `pos_store.store_name` + `contact_phone` 字段对齐 | `db-snapshot.sql` grep `pos_store` 5 匹配；`StoreLookupService` SQL 已确认 | Phase 5 `DESCRIBE pos_store` |
| 5 | 6 locale JSON 在生产 classpath 下存在 | `resources/i18n/template/` 6 文件存在；warmup() 6 locale | Phase 5 启动日志 + warmup 验证 |
| 6 | `BookingConfirmationTemplateResolver` 启动期 warmup 是否真跑 | `@PostConstruct warmup()` + `try { loadLocale(l) } catch (Exception e) { log.warn(...) }` — **会跑** | Phase 5 启动日志 `[I18N-TPL] warmup done, locales=...` |
| 7 | `StoreLookupService` 5 分钟缓存是否真实现 | `CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)` + `ConcurrentHashMap<Long, CacheEntry>` + 过期判断 `now - hit.cachedAt < CACHE_TTL_MS` — **完整实现** | OK（无需 Phase 5） |
| 8 | `autoSend` 关闭路径是否真生效 | `IntlBookingService.updateStatus` (`:193-196`) `Boolean autoSend = body.get("autoSend") == null ? Boolean.TRUE : Boolean.valueOf(body.get("autoSend").toString());` + `if (autoSend && ...)` — **完整实现** | OK |
| 9 | `miniProgramOpenId` 写入路径 | 实体 `OverseasBooking.java:21` 字段 + `migrate_ext_19` DDL ALTER — 但**前端无 UI 设置该字段**；只能通过 SQL 直写 / `OverseasBooking` mapper.updateById 间接设置 | Phase 2 静态扫描确认是否真无前端写入路径 |
| 10 | `EmailSender` mismatch 检测的边界 | `globalMatch = globalHost.equals(smtpHost) && globalUsername.equals(username)` + 额外 `cfg == null || !cfg.getConfigJson().contains("smtpHost")` — **逻辑完备** | OK（无需 Phase 5） |
| 11 | `MiniProgramSender` AccessToken 缓存边界 | `TOKEN_CACHE_MS = 7100_000L`（提前 10 分钟刷新 2 小时 token）；errcode=40001 自动清缓存 — **完整实现** | OK |
| 12 | `BizCode.BAD_REQUEST/NOT_FOUND/CONFLICT` 枚举值 | `exception/BizCode.java` 已确认（沿用 Batch 1.1 F030 模板）| OK |
| 13 | `OverseasBooking` 实体与 `overseas_booking` 表 26 列完全对齐 | 实体 26 字段 + DDL 26 列 + 4 索引 — **静态一致** | OK |
| 14 | `IntlBookingService.confirmationTemplate` 端点是否走 Resolver（SoT §3 期望）| 实际**硬编码 switcher**（与 SoT 期望反向）— I043 中立标 | OK（已登记 ISSUE） |
| 15 | WeChat MP 真实凭据注入路径（如有）| 用户决策 #1 — Mock 降级 | Phase 5 用户手动注入 |
| 16 | SMTP 真实凭据注入路径（如有）| 用户决策 #2 — 沿用 SoT §0.2 mismatch demo 模式 | Phase 5 用户手动注入 |
| 17 | 前端 `intl-booking/index.vue` 是否触发 `autoSend=false` UI | grep 整个文件 `autoSend` **0 匹配**（已确认无 UI）| OK（已登记 I040） |
| 18 | 前端 `intl-booking.ts` 是否调用 `/intl-booking/{id}/send-confirmation` | grep 整个文件 `send-confirmation\|sendConfirmation` **0 匹配**（已确认无前端调用）| OK（已登记 I042） |
| 19 | `IntlBookingService.googleWebhook` + `instagramWebhook` 演示模式下 token → tenantId 反查 | `resolveTenantFromToken` (`:328-330`) 永远返回 `null` 兜底 `1L` — 演示模式 | OK（已登记 I051） |

### 1.4.8 与 preflight §八 已知约束的交叉引用

| preflight §八 | Phase 1 处理 | 对应 Feature | 对应 Issue |
|---|---|---|---|
| §八.1 `overseas_booking` 表存在性 | F081/F082 标 **MISSING**；Phase 5 验证 + Post-Audit 跑迁移 | F081 / F082 / F095-F106 | **I038**（I002/I017/I029 MISSING 模式）|
| §八.2 WeChat 模板 ID 占位符 | F090 标 UNVERIFIED；SoT §0.1 完全一致 | F090 | OK（SoT 已知）|
| §八.3 SMTP mismatch demo 模式 | F089 三模式 + 日志关键词核对通过 | F089 | OK（SoT 一致）|
| §八.4 autoSend 默认 true 行为 | F096 行为变更 + `body.autoSend=false` 关闭路径 + 异常不回滚 — 与 SoT §0.3 完全一致 | F096 | OK（SoT 一致）|
| §八.5 EXT-19 Operational Notes SoT | F081-F110 全部以 SoT 为骨架；§5 已知问题逐项比对（§1.4.5）| F081-F113 | I038-I051 |
| §八.6 EXT-19 Operational Notes 加载 | 已确认加载；Phase 1 全文核实 6 项 §0 描述均在代码中找到对应 | (整体) | OK |
| §八.7 共用 entity/table 边界 | F081/OverseasBooking 共用实体边界明确：BookingConfirmationService 仅读 + 写 confirmationError；前端通过 F098 列表 + F095 send-confirmation 间接操作 | F081 | OK（用户决策 #8）|
| §八.8 受保护页面零风险 | `/dashboard` / `/mh/visual` / `/mh/consent` 与 EXT-19 无直接依赖（`overseas_booking` 不被受保护页面后端接口引用）| (无) | OK（Phase 2 静态扫描二次确认）|
| §八.9 `miniProgramOpenId` 写入路径 | F081 字段定义 + F082 ALTER；但**前端无 UI 写入路径**（待 Phase 2 静态确认）| F081 | (待 Phase 2 静态扫描)|
| §八.10 `StoreLookupService` 5 分钟缓存 | F087 `CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)` + `ConcurrentHashMap` + 过期判断 — **完整实现** | F087 | OK |
| §八.11 测试覆盖 | preflight §7.2 列 3 测试；Phase 1 全文 grep 确认 **4 测试**（新增 `WechatOfficialAccountSenderTest`）| F108-F110 | OK（SoT 增补）|
| §八.12 `confirmation-template` 端点 | F097 实际硬编码 switcher，与 SoT §3 期望走 Resolver 体系反向 | F097 | **I043 CONFLICT 中立标** |

### 1.4.9 跨 EXT-* 引用规则（沿用 Batch 1.1/1.2/1.3 用户决策 #6）

| 共享资源 | 所属 EXT-* | Batch 1.4 Feature 视角 | 备注 |
|---|---|---|---|
| `OverseasBooking` entity + `overseas_booking` 表 | **EXT-15 (P3-25 国际支付 / P3-26 深化 — BookingConfirmationService 用) + EXT-19 (CONFIRMED 自动触发 + 5 渠道发送)** | F081 实体（共用）；F083 核心发送（EXT-19 视角）；F095-F106 业务端点（EXT-19 视角）| Phase 1 期间明确边界；Feature ID 不加 EXT 前缀 |
| `MktChannelConfig` entity + `mkt_channel_config` 表 | EXT-09 (M09-01 营销渠道 — InAppSender/SmsSender/EmailSender 等共用) + EXT-19 (5 channel sender 复用) | F088-F092 channel sender（EXT-19 视角）；F094 ChannelDispatcher（EXT-19 视角，rateLimit 用 marketing_touch_log）| Feature ID 按 EXT-19 视角命名 |
| `MemberMessage` entity + `member_message` 表 | EXT-09 (M09-01 InAppSender 写入) + EXT-19 (InAppSender 共用) | F091 InAppSender（EXT-19 视角，channel sender 实现）| 同上 |
| `pos_store` 表 | EXT-03 (基线已建) + EXT-19 (StoreLookupService 复用) | F087 StoreLookupService（EXT-19 视角）| 同上 |
| `crm_member` + `pos_order` 表 | EXT-03 (基线已建) + EXT-19 (IntlBookingService.convert 复用) | F103 convert（EXT-19 视角）| 同上 |
| `marketing_touch_log` 表 | EXT-09 (M09-01 营销渠道 — ChannelDispatcher.rateLimit 复用) + EXT-19 (ChannelDispatcher 共用) | F094 ChannelDispatcher.rateLimit（EXT-19 视角）| 同上 |

---

**Phase 1 产出（Batch 1.4）完成。本批次 33 个 Feature 已落 `audit/feature-inventory.md`，超出 scope-freeze §3.3 预期 25-30 上限（+3 个 Feature — 命名异味 + 4 测试 vs 3 测试 + 1 个 webhook 验证握手拆分）**。

**已知约束标记汇总**（KNOW-CONSTRAINT，强制关注）：
- ⚠ **MISSING（DB）**：`overseas_booking` 表 DDL 已就位但 dev DB 未跑迁移（DDL 在 `db/sql/migrate_ext_19_mini_program_openid.sql:9-32`）— 触发 F081/F082/F095-F106 全部 → **强制 MISSING / PARTIAL-BLOCKER**（**统一 Post-Audit 跑批 — 用户决策 #3 第三次强调**）
- ⚠ **MISSING（DB）**：`member_message` 表 db-snapshot.sql 0 匹配 — 触发 F091 → **统一 Post-Audit 跑批**
- ⚠ **UNVERIFIED（凭据缺失）**：WeChat MP / SMTP / OA / 阿里云 SMS 真实凭据缺失 → Mock 降级（用户决策 #1/#2 — D10 铁律）
- ⚠ **CONFLICT（中立标）**：WECHAT_OA 别名 vs WECHAT_OFFICIAL_ACCOUNT（I039）+ confirmation-template 硬编码 switcher vs Resolver 期望（I043）— Fix Phase = Post-Audit 人类决策
- ⚠ **Shadow（前端缺失）**：autoSend=false UI（I040）+ 状态确认按钮文案（I041）+ sendConfirmation API（I042）— Fix Phase = Post-Audit 人类决策
- ⚠ **PARTIAL-NON-BLOCKER**：catch 吞异常（I047 — SoT 明确设计）+ rollbackFor 缺失（I048 — I009 模式）+ healthCheckAll 单租户（I050 — T07-D4 例外声明）+ webhook demo 模式（I051）
- ⚠ **架构异味（命名）**：`IntlBookingService.java` `@RestController` 命名误导（I044 — 与 Batch 1.1 I004 / 1.2 I019 / 1.3 I029 完全同模式，**第 4 次出现**）

**待用户决策**（不进 Phase 2 前）：

1. **ISSUE-I038**：`overseas_booking` 表 MISSING — Phase 5 跑迁移 + 写 `audit/evidence/F081-overseas-booking-entity/db-evidence.sql`？（**统一 Post-Audit 跑批 — 用户决策 #3**）
2. **ISSUE-I039**：`WECHAT_OA` vs `WECHAT_OFFICIAL_ACCOUNT` 别名 CONFLICT 中立标 — 接受？
3. **ISSUE-I040**：`autoSend=false` 前端 UI 缺失 Shadow — Post-Audit 决定？
4. **ISSUE-I041**：状态确认按钮文案无"确认即发送通知" Shadow — Post-Audit 决定？
5. **ISSUE-I042**：`/send-confirmation` 前端 API 缺失 Shadow — Post-Audit 决定？
6. **ISSUE-I043**：`confirmation-template` 硬编码 switcher vs Resolver 期望 CONFLICT 中立标 — 接受 / 改 Resolver？
7. **ISSUE-I044**：IntlBookingService `@RestController` 命名异味 — 重构 backlog 登记（第 4 次出现）？
8. **ISSUE-I045**：`member_message` 表 MISSING — Phase 5 跑迁移 + 写 `audit/evidence/F091-in-app-sender/db-evidence.sql`？（**统一 Post-Audit 跑批**）
9. **ISSUE-I046**：`pos_store.store_name/contact_phone` 字段对齐 — Phase 5 `DESCRIBE pos_store` 验证？
10. **ISSUE-I047**：autoSend catch 吞异常 — 接受现状（SoT 明确）？
11. **ISSUE-I048**：webhook/convert `@Transactional` 无 rollbackFor — 接受现状 / 加 `rollbackFor = Exception.class`？
12. **ISSUE-I049**：WeChat MP / SMTP / OA / 阿里云 SMS 凭据缺失 — 接受 UNVERIFIED（Mock 降级）？
13. **ISSUE-I050**：`healthCheckAll` 仅更新 tenant_id=1（T07-D4 例外）— 接受现状？
14. **ISSUE-I051**：webhook demo 模式 — 接受现状（演示足够联调）？



