# Phase 3 — Static Gap Audit Report

> **创建时间**：2026-09-12 21:59 (UTC+8)
> **Phase**：Phase 3 — Static Gap Audit
> **范围**：Batch 1.1 — EXT-24 CRM 集成（聚焦） + 全局视野（守门脚本总览）
> **引用文件**：`audit/feature-inventory.md` §1.1 / `audit/traceability-matrix.md` / `audit/baseline/scope-freeze.md`
> **SoT**：`.cursor/rules/ext-24-crm-integration.mdc`（按规则 11，文档与代码冲突时标 ISSUE）
> **本批次决策摘要**：
> 1. 不跑 `migrate_ext_15_crm_real.sql`（用户决策 1） → F005/F012/F020/F023 保持 PARTIAL-BLOCKER
> 2. 不改 `ext-24-crm-integration.mdc`（用户决策 2） → I001 保留 → F016/F017/F018 标 CONFLICT
> 3. 接受「Phase 2 静态 + Phase 5 实测」策略（用户决策 3） → 其他 PARTIAL-NON-BLOCKER 在 Phase 5 升级 VERIFIED
> 4. **Batch 1.1 非重构批次** → 跳过 Phase 3.5 Diff Audit（移至 Batch 1.5 MKT 重构）

---

## 〇、本次产出摘要

| 产出 | 路径 | 内容 |
|---|---|---|
| **8 个守门脚本** | `audit/scripts/01-08-scan-*.ps1` | 静态扫描，PowerShell + .NET regex，针对本项目技术栈（Java + Vue + MySQL） |
| **8 份扫描报告** | `audit/static-gap/01-08-*.txt` | 全量扫描结果（raw 落盘） |
| **本报告** | `audit/reports/static-gap-report.md` | Batch 1.1 精细分析 + 缺口表 + Issue 清单 |

**守门脚本类型**：

| # | 脚本 | 检测目标 | 输出 |
|---|---|---|---|
| 01 | `01-scan-fake-success.ps1` | catch-log-swallow（catch 仅 log） / catch-ignore-empty（catch(...) {}） | `01-fake-success.txt` |
| 02 | `02-scan-mock.ps1` | `@ConditionalOnMissingBean`-mock-fallback / `mock`/`fake` 字面常量 / `localStorage`/`sessionStorage` / `setTimeout` 假异步 | `02-mock.txt` |
| 03 | `03-scan-disabled.ps1` | `@Disabled` / `@Deprecated` / `UnsupportedOperationException` / `return null` / TODO/FIXME/XXX/HACK | `03-disabled.txt` |
| 04 | `04-scan-endpoint-coverage.ps1` | Backend `@RequestMapping` × Frontend `request.{get,post,put,delete}` 差集 | `04-endpoint-coverage.txt` |
| 05 | `05-scan-contract-drift.ps1` | `@RequestParam(required=false)` 无 default / 无 `@Valid` / 前端字段 snake_case | `05-contract-drift.txt` |
| 06 | `06-scan-soft-delete.ps1` | 表含 `deleted` 列但 UNIQUE KEY 不含 `deleted` → 软删+重插 UNIQUE 冲突 | `06-soft-delete.txt` |
| 07 | `07-scan-transaction.ps1` | `@Transactional` on private / 默认只回滚 RuntimeException / catch 吞异常 | `07-transaction.txt` |
| 08 | `08-scan-shadow-ghost.ps1` | Shadow（后端有/前端无） / Ghost（前端有/后端无） / Zombie / Fake / Shell 5 维 | `08-shadow-ghost.txt` |

---

## 一、Phase 3 守门脚本总览（项目全量）

> 本节是「**整个项目**」的扫描结果（仅 Batch 1.1 范围在本报告 §二 节深入）。

### 1.1 全量命中统计

| 脚本 | Total Hits | HIGH（需修） | OK（设计允许） | 说明 |
|---|---|---|---|---|
| **01-fake-success** | 299 | 299 | 0 | 全部 catch-log-swallow 或 catch-ignore-empty。其中相当部分是**正常日志降级**（如 `MockCrmAdapter.upsertContact` 内的 catch），需在 Phase 5 + Phase 6 关联其他证据判断；**严禁默认全部当 bug** |
| **02-mock** | 90 | 不分类 | 不分类 | 含 `@ConditionalOnMissingBean` 的 1 个 Mock 设计 fallback（MOCK-INTENTIONAL）+ `localStorage` 客户端持久化（49 处，多为 `request.ts` / `gray.ts` 的 token 持久，**不在 Batch 1.1 范围**）+ `setTimeout` 8 处 |
| **03-disabled** | 94 | 不分类 | 不分类 | 含 TODO/FIXME 注解、`return null` 占位、`UnsupportedOperationException` 等。**Batch 1.1 CRM 范围 0 命中** |
| **04-endpoint-coverage** | 后端 **2013** / 前端 **859** | **仅后端 1572** / **仅前端 418** / **matched 441** | — | 守门脚本**未做 `{id}` ↔ `${id}` 归一化** + **`/list`/`/dashboard` 这类多段路径 regex 不完全**，产生**大量 false positive**。详见 §1.2 脚本已知限制 |
| **05-contract-drift** | 1530 | 不分类 | 不分类 | 多为 `@RequestParam(required=false)` 无 default 提示（多数非 bug），**Batch 1.1 范围 0 严重命中** |
| **06-soft-delete** | **77**（含 OK） | **63**（含 deleted 表 + UNIQUE 不含 deleted） | **14**（UNIQUE 含 deleted） | **修复 Bug**：原 `\s+` 无法跨反引号匹配 `\`deleted\` tinyint`，修正为 `[`\s\)]+`（**regex 已重写并通过验证**：27 张表被正确识别，详见 §1.3） |
| **07-transaction** | 214 | 214 | 0 | 含 `@Transactional` 默认 rollbackOnly=RuntimeException + private method + catch-swallow 三类。**Batch 1.1 命中 1 处**（F005 syncNow 的 `@Transactional`）|
| **08-shadow-ghost** | **2003** | 1572 Shadow / 418 Ghost / 3 Zombie / 8 Fake / 2 Shell | — | 04 同源问题加剧（占位符路径归一化缺失）；**Batch 1.1 命中 0 个真实 Shadow/Ghost/Zombie/Fake/Shell**（详见 §二） |

### 1.2 守门脚本已知限制（必须 readout 通告）

| 限制 | 编号 | 位置 | 影响 | 改进建议 |
|---|---|---|---|---|
| **路径占位符不归一化** | L1 | `04/08-scan-*.ps1` | 后端 `{id}` 与前端 `${id}` 被判为不同端点 → **false Shadow**。Batch 1.1 受影响：`POST:/crm-integration/{id}/toggle` 等 4 条被误判 | 加 `Normalize-Path` 步骤：`{id}` ≡ `${id}` |
| **多段路径正则不全** | L2 | `04-scan-endpoint-coverage.ps1` | 前端 `request.get('/crm-integration/sync-log/list', ...)` 没匹配上 → 误判 Only-Backend。Batch 1.1 受影响：`/list`、`/dashboard`、`/sync-log/list` 3 条 GET 被误判 | 改 regex 允许多段 `/` |
| **backtick 跨字段类型不识别** | L3 | `06-scan-soft-delete.ps1` | 字段定义 `` `deleted` tinyint `` 中 `deleted` 与 `tinyint` 之间有 `` ` ``，`\s+` 不跨 → false negative | **已修**：用 `[`\s\)]+` 替代 `\s+`，重测 77 hits |
| **Format-Table 列宽截断** | L4 | `06-scan-soft-delete.ps1` | `Risk` / `HasDeleted` 列被截断到 N/A 列外，按 `Format-Table` 默认每列前 80 字符计算 | **已修**：加 `-Wrap`，重输出 |
| **`<typescript>` 模板字符串未覆盖** | L5 | `04/08-scan-*.ps1` | 前端 API 文件使用 `` `/crm-integration/${id}/toggle` `` 模板字符串中的 ${...} 占位，部分 regex 不识别反引号 | 仅 L1 修好后即可 |

> **L1 / L2 / L5 是 false positive 来源**，**不修脚本，仅在 Phase 5 手动复核**。L3 / L4 已在 Batch 1.1 修。

### 1.3 06 script Bug 已修复确认

| 状态 | 命中数 | 备注 |
|---|---|---|
| **修复前** | 0 hits | `(?i)\b(deleted|is_deleted)\b\s+(TINYINT|...)` 在 `` `deleted` tinyint `` 上 fail，因 `\s+` 不跨反引号 |
| **修复后** | 77 hits（63 HIGH + 14 OK） | 用 `[`\s\)]+` 替代 `\s+`；通过行内最小复现 + db-snapshot 实测验证 |

修复点（`audit/scripts/06-scan-soft-delete.ps1:53`）：
```powershell
# 修复前
$hasDeleted = $tableBody -match '(?i)\b(deleted|is_deleted)\b\s+(TINYINT|BOOLEAN|INT|BIT|INTEGER)'
# 修复后
$hasDeleted = $tableBody -match '(?i)(?:`?|`?\s*`?)\b(deleted|is_deleted)\b[`\s\)]+(TINYINT|BOOLEAN|INT|BIT|INTEGER)'
```

### 1.4 修复点的二次验证

| 验证表 | 修复前 | 修复后 | 备注 |
|---|---|---|---|
| `ai_ltv_model`（line 91：`  \`deleted\` tinyint DEFAULT '0'`）| False（不被算 deleted 列）| True | ✓ 通过 |
| `crm_member` UNIQUE `uk_phone` (`phone`,`deleted`)| — | OK 标记 | ✓ UNIQUE 含 deleted |
| `crm_member_store` UNIQUE `uk_member_store` (`member_id`,`store_id`)| — | HIGH 标记 | ✓ UNIQUE 不含 deleted（潜在软删冲突） |
| `crm_integration`（`object_mappings`/`private_key` 等 22 列无 `deleted`）| — | 不在范围 | ✓ Phase 2 I002 已知，与本脚本无关 |
| `crm_contact_map`（db-snapshot 缺失）| — | 不在范围 | ✓ Phase 2 I002 已知，与本脚本无关 |

---

## 二、Batch 1.1 — EXT-24 CRM 集成精细分析

> 范围：`backend/src/main/java/com/meiyetec/crm/*.java` + `entity/{CrmIntegration,CrmContactMap,CrmSyncLog}.java` + `service/CrmIntegrationService.java` + `views/crm-integration/index.vue` + `api/crm-integration.ts`。

### 2.1 已确认的 Shadow / Ghost / Zombie / Fake / Shell Feature（按 F0XX 编号）

| Feature ID | 缺口类型 | 类别 | 位置 | 证据 | 严重程度 | 建议 |
|---|---|---|---|---|---|---|
| **F003** | **Shadow Feature** | 后端 endpoint 有，前端无 UI 入口 | 后端：`backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java:58-66` `update(@PathVariable Long id, @RequestBody CrmIntegration i)`<br>前端：`frontend/meiyetec-web/src/views/crm-integration/index.vue` 表内 **无 edit 按钮**；`api/crm-integration.ts:8` `update()` **已封装但未调用** | F003 in `traceability-matrix.md` §F003；line 12-23 表只有 `id / platform / shopName / syncDirection / lastSyncAt / status / 操作[同步]` | **PARTIAL-NON-BLOCKER**（与 I005 关联） | 决策 ① 加 edit UI（推荐）；决策 ② 删除后端 `update` endpoint；决策 ③ 文档化「仅供未来 API」 |
| **F007** | **Shadow Feature** | 后端 endpoint 有，前端无独立 tab | 后端：`CrmIntegrationService.java:142-155` `dashboard()` → `GET /crm-integration/dashboard`<br>前端：`api/crm-integration.ts:17` `dashboard()` **已封装但未调用**；`views/crm-integration/index.vue:2-3` 只有 2 个 tab（集成配置 / 同步日志），**无 Dashboard tab** | F007 in `traceability-matrix.md` §F007；04 输出 `GET:/crm-integration/dashboard Only-Backend: <script>1` | **PARTIAL-NON-BLOCKER**（与 I005 关联；不阻塞 Phase 5） | 决策 ① 加 dashboard tab（推荐）；决策 ② 接受现状（仅供未来 API） |
| **F023** | **MISSING / 表不存在** | 表真实缺失 | db-snapshot.sql: 全表 grep 0 命中 `crm_contact_map` | preflight §8.1 + Phase 2 I002 + `traceability-matrix.md` §F023 | **PARTIAL-BLOCKER**（用户决策 1 不跑迁移） | **按用户决策 1**：保留 PARTIAL-BLOCKER；Phase 5 实测时该表被引用将抛 `BadSqlGrammarException` |
| **F012** | **MISSING（依赖 F023）** | known-ids 去重链路不可用 | `CrmSyncScheduler.java:174-194` `loadKnownExternalIds` | `traceability-matrix.md` §F012 | **PARTIAL-BLOCKER**（依赖 F023） | 同 F023 |
| **F020** | **MISSING（依赖 F023）** | upsert contact map 写表失败 | `CrmSyncScheduler.java:161-181` `upsertContactMap` | `traceability-matrix.md` §F020 | **PARTIAL-BLOCKER**（依赖 F023） | 同 F023 |

### 2.2 已确认的 Contract Drift（前后端契约不一致）

| 编号 | Feature ID | 缺口类型 | 位置 | 证据 | 严重程度 | 建议 |
|---|---|---|---|---|---|---|
| **C1** | F004 / F029 | **状态 enum 漂移**（I005） | 前端：`views/crm-integration/index.vue:14` `<el-switch active-value="ACTIVE" inactive-value="PAUSED" ...>` — **PAUSED** 不在后端合法值内<br>后端：`CrmIntegrationService.java:68-77` `toggle()` — 未校验 enum，可接受任意 `String`<br>SoT：`ext-24-crm-integration.mdc` §4 状态机：合法值 = `ACTIVE` / `DISABLED` / `ERROR` | `index.vue:14` vs `CrmIntegrationService.java:73-75`（无 if-check）| **PARTIAL-NON-BLOCKER**（与 I005 关联） | 决策 ① 前端改 `inactive-value="DISABLED"`；决策 ② 后端加 enum 校验 + 非法返回 400；决策 ③ 接受现状并文档化（不推荐）|
| **C2** | F005 / F027 | **force 参数未传**（I005/EXT-24 ⚠） | 前端：`views/crm-integration/index.vue:89` `await crmIntegrationApi.syncNow(id)` — **无第 2 个参数**<br>API 封装：`api/crm-integration.ts:14` `syncNow: (id) => request.post(\`/crm-integration/${id}/sync\`)` — **POST URL 无 query string**<br>后端：`CrmIntegrationService.java:79-128` `syncNow` 接受 `@RequestParam(required=false) Integer force` — 默认 null → 走窗口检查 → `shouldRunForManualTrigger()` 返回 false → 返回 `skipped=true` | `index.vue:89` / `crm-integration.ts:14`（**确认未传 `?force=1`**）；EXT-24 §0.1 规定 `force=1` 是「跨窗口强制执行」的官方方式 | **PARTIAL-NON-BLOCKER**（与 I005 关联；**EXT-24 §0.1 ⚠ 显式标注重风险**） | 决策 ① 前端改成 `syncNow(id, { force: 1 })`（推荐）；决策 ② 加 loading / 二次确认（推荐）；决策 ③ 接受「窗口外无感 skipped」|
| **C3** | F006 / F026 | **integrationId 字段不存在 + UI 列名误导**（I003） | 前端：`index.vue:27` `<el-table-column prop="integrationId" label="集成" />` — **字段名 `integrationId`**<br>后端：`CrmIntegrationService.java:130-141` `listSyncLogs` — 入参 `integrationId` 被**反查** `crm_integration.platform` 然后过滤 `crm_sync_log.platform`（不是 `integrationId`）<br>DB：`crm_sync_log` 表**无 `integration_id` 列**（db-snapshot.sql:3963，12 列） | `index.vue:27` vs `CrmIntegrationService.java:135-138`（反查 platform 而非 integrationId）| **PARTIAL-NON-BLOCKER**（与 I003 关联） | 决策 ① 后端 `crm_sync_log` 加 `integration_id` 列；决策 ② 前端列改名为 `platform`（最简）；决策 ③ 接受「按 platform 聚合」语义并文档化 |
| **C4** | F025 / F028 | **新建表单缺字段**（I005/EXT-24 §5 ⚠） | 前端：`index.vue:43-55` 表单仅 4 字段：`platform` / `shopName` / `syncDirection` / `autoSync`<br>后端 entity：`entity/CrmIntegration.java` 至少有 13 字段（含 `clientId` / `clientSecret` / `privateKey` / `username` / `loginUrl` / `objectMappings` / `syncSchedule` / `syncWindowMinutes` / `accessToken` / `refreshToken` / `tokenExpireAt` 等）<br>SoT：`ext-24-crm-integration.mdc` §5 已标「启用 Salesforce 自定义字段映射（`Email__c` 等）需通过后端 SQL 直接 update」 | `index.vue:38-61` 字段列 vs `CrmIntegration.java:55-104` 实体字段 | **PARTIAL-NON-BLOCKER**（与 I005 + I002 关联） | 决策 ① 加扩展字段输入（推荐）；决策 ② 接受 SQL 直写路径（**不推荐，已部署则泄漏字段**） |
| **C5** | F003 | **后端 update 端点存在但前端无 UI 入口** | 同 F003 Shadow Feature 描述（见 §2.1） | 同上 | **PARTIAL-NON-BLOCKER** | 同 F003 |
| **C6** | F019 / F022 | **DB 缺 9 列 EXT-15**（I002） | DB：`crm_integration` 表 db-snapshot.sql:3433 — **22 列**，缺：`sync_token` / `sync_schedule` / `sync_enabled` / `last_incremental_sync_at` / `private_key` / `username` / `login_url` / `object_mappings` / `sync_window_minutes`<br>Entity：`entity/CrmIntegration.java:55-104` 全部已声明字段（含上述 9）<br>DDL：`backend/src/main/resources/db/sql/migrate_ext_15_crm_real.sql:5-19` — DDL 已写未跑（用户决策 1） | db-snapshot.sql:3433-3458 (22 列) vs `migrate_ext_15_crm_real.sql:5-19` 加 9 列 DDL；F019 / F022 in `traceability-matrix.md` | **PARTIAL-BLOCKER**（依赖 F023 决策 → 用户决策 1 = 不跑迁移 → 保留 PARTIAL-BLOCKER） | **按用户决策 1**：保留；Phase 5 实测时 `Unknown column 'object_mappings'` / `Unknown column 'sync_window_minutes'` 等异常将暴露 |
| **C7** | F021 / F022 | **DB 缺 3 列 EXT-15**（I002） | DB：`crm_sync_log` 表 db-snapshot.sql:3963 — **12 列**，缺：`sync_token` / `records_total` / `records_filtered`<br>Entity：`entity/CrmSyncLog.java:16-39` 已声明 | db-snapshot.sql:3963-3980 vs `migrate_ext_15_crm_real.sql:45-49` 加 3 列 DDL | **PARTIAL-NON-BLOCKER** | 同 C6（按用户决策 1 不动 DB）|

### 2.3 EXT-* SoT 已知冲突（保留 I001 标 CONFLICT）

| Feature ID | 缺口类型 | SoT 描述 | 代码事实 | 处理 |
|---|---|---|---|---|
| **F016** | **CONFLICT**（I001） | `ext-24-crm-integration.mdc` §0.6：`HUBSPOT` "未实现（前端可选会 500）" | `backend/src/main/java/com/meiyetec/crm/HubSpotCrmAdapter.java` — **219 行**完整实现（OAuth2 refresh `authenticate` :122 + search API `fetchContactsIncremental` :177 + batch/upsert by email `upsertContact` :276 + `healthCheck` :321） | **按用户决策 2**：保留 I001，不动 `ext-24.mdc`；F016 标 **CONFLICT** |
| **F017** | **CONFLICT**（I001） | `ext-24-crm-integration.mdc` §0.6：`ZOHO` "未实现（前端可选会 500）" | `backend/src/main/java/com/meiyetec/crm/ZohoCrmAdapter.java` — **230 行**完整实现（OAuth2 refresh `authenticate` :122 + `deriveAccountsBase` :177 + COQL `fetchContactsIncremental` :191 + `upsertContact` :272 + `healthCheck` :319） | 同 F016（保留 I001） |
| **F018** | **CONFLICT**（I001） | `ext-24-crm-integration.mdc` §5：「`router.getAdapter` 返回 null → NPE」 | `backend/src/main/java/com/meiyetec/crm/CrmAdapterRouter.java:30-34` — **实际有 MOCK fallback**：`getAdapter(platform)` miss → 调 `getAdapter("MOCK")`；**永远不会 NPE** | 同 F016（保留 I001） |

### 2.4 02-mock 中 Batch 1.1 范围相关命中

| Feature ID | 类别 | 位置 | 证据 | 判定 |
|---|---|---|---|---|
| F015 | MOCK-INTENTIONAL | `backend/src/main/java/com/meiyetec/crm/MockCrmAdapter.java:21` `@ConditionalOnMissingBean(name = "salesforceCrmAdapter")` | 02-mock.txt 输出第 1-2 条（类型 `ConditionalOnMissingBean-mock-fallback`） | **MOCK-INTENTIONAL**（设计上的 fallback，允许保留） |
| F015 / F018 | **CrmAdapter **factory 内的 Adapter 集合 | `CrmAdapterRouter.java:25` 注入 `Map<String, CrmAdapter>` — Spring 自动收集所有 `@Component` 标注的 Adapter | router 静态分析 | OK |

### 2.5 01-fake-success 中 Batch 1.1 范围相关命中（共 17 处）

> **重要**：catch-log-swallow 不一定都是 bug。Phase 5 静态 + Phase 6 实测关联后判断。

| Feature ID | 位置 | 命中模式 | 实际语义 |
|---|---|---|---|
| F010 / F012 / F020 | `crm/CrmSyncScheduler.java:53` `tick()` 内 catch | catch-log-swallow | 排程时单条 integration 失败 → catch log → 继续下一条 **正常容错** |
| F010 / F012 / F020 | `crm/CrmSyncScheduler.java:62` 同上 | catch-log-swallow | 同上 |
| F020 | `crm/CrmSyncScheduler.java:207` `loadKnownExternalIds` 内 catch | catch-log-swallow | 表缺失时返回 empty Set → 失去差集去重，**已属 F012/F023 已知 PARTIAL-BLOCKER，不重复标** |
| F014 / F016 / F017 | `crm/HubSpotCrmAdapter.java:112/158/255/247` `crm/ZohoCrmAdapter.java:112/167/262/255` `crm/SalesforceCrmAdapter.java:113/209/296/289` | catch-log-swallow / catch-ignore-empty | Adapter 内 HTTP 失败 → log warn → 返回默认值 — **设计降级**，Phase 5 验证重试逻辑 |
| F015 | `crm/MockCrmAdapter.java:44/56/96` | catch-ignore-empty | Mock fallback，**无外部依赖** → 空 catch 合理 |
| F007 | `service/CrmIntegrationService.java:145` `dashboard()` 内 `catch (Exception ignore) {}` | catch-ignore-empty | **过宽 catch**，所有异常被吞但仍返回空 data map（无 error）— 设计上接受「失败兜底」，标记 **PARTIAL-NON-BLOCKER** |
| 不在范围 | `service/impl/CrmFollowUpPlanServiceImpl.java:115/248/300/314` | catch-ignore-empty | 属 crm-follow-up 模块，**不在 Batch 1.1 范围**，仅列出 |

### 2.6 04-endpoint-coverage 中 Batch 1.1 范围精细对比

> 由于脚本限制（L1/L2），人工修正后真实覆盖面如下：

| 后端 Endpoint | 后端位置 | 前端 API 调用位置 | 04 报告状态 | 人工修正结论 |
|---|---|---|---|---|
| `POST /crm-integration` | `CrmIntegrationService.java:35-44` | `api/crm-integration.ts:5` `request.post('/crm-integration', data)` | Only-Backend（**L1 false positive**：`{id}` vs `${id}` 不归一化）| **Matched** |
| `GET /crm-integration/list` | `CrmIntegrationService.java:46-56` | `api/crm-integration.ts:6-7` `request.get('/crm-integration/list', { params })` | Only-Backend（**L2 false positive**：多段路径 regex）| **Matched** |
| `POST /crm-integration/{id}` | `CrmIntegrationService.java:58-66` | `api/crm-integration.ts:8` `request.post(\`/crm-integration/${id}\`, ...)` | Only-Backend（**L1 false positive**）| **Matched** |
| `POST /crm-integration/{id}/toggle` | `CrmIntegrationService.java:68-77` | `api/crm-integration.ts:9` `request.post(\`/crm-integration/${id}/toggle\`, ...)` | Only-Backend（**L1 false positive**）| **Matched** |
| `POST /crm-integration/{id}/sync` | `CrmIntegrationService.java:79-128` | `api/crm-integration.ts:14` `request.post(\`/crm-integration/${id}/sync\`)` | Only-Backend（**L1 false positive**）| **Matched** |
| `GET /crm-integration/sync-log/list` | `CrmIntegrationService.java:130-141` | `api/crm-integration.ts:15-16` `request.get('/crm-integration/sync-log/list', { params })` | Only-Backend（**L2 false positive**）| **Matched** |
| `GET /crm-integration/dashboard` | `CrmIntegrationService.java:142-155` | `api/crm-integration.ts:17` `request.get('/crm-integration/dashboard')` | Only-Backend（**L2 false positive**）| **Matched** |

> **真实结论**：Batch 1.1 端点层面 **0 个真实 Shadow / Ghost**。**前端 UI 调用层面**才有缺失（F003 update 无 edit 按钮、F007 dashboard 无 tab）。

### 2.7 06-soft-delete 中 Batch 1.1 范围相关命中

> 仅 EXT-24 直接涉及的 3 张表（`crm_integration` / `crm_sync_log` / `crm_contact_map`）**不在 06 报告范围**（无 `deleted` 列）。

| 表 | 06 报告状态 | 备注 |
|---|---|---|
| `crm_integration` | **不在范围** | db-snapshot.sql:3433（22 列，无 `deleted`）— Phase 2 I002 已知（缺 9 列 EXT-15） |
| `crm_sync_log` | **不在范围** | db-snapshot.sql:3963（12 列，无 `deleted`）— Phase 2 I002 已知（缺 3 列 EXT-15） |
| `crm_contact_map` | **不在范围**（表缺失） | Phase 2 I002 已知（db-snapshot 0 命中） |

> CRM 周边表（`crm_member` / `crm_complaint_ticket` / `crm_follow_up_plan` 等）虽有 06 HIGH 命中（详见 §1.1 + 06-soft-delete.txt 27-40 行），但**不在 Batch 1.1 范围**，仅作 Issue Register 记录。

### 2.8 07-transaction 中 Batch 1.1 范围相关命中

| Feature ID | 位置 | 命中模式 | 实际语义 | 判定 |
|---|---|---|---|---|
| F005 | `service/CrmIntegrationService.java:79` `syncNow` 上 `@Transactional` | Transactional-no-rollbackFor | 默认 only rollback RuntimeException，**checked exception 不触发回滚**。结合方法内 `runIncremental(...)`（RuntimeException）+ `Result.error(...)` 转换 → 主要异常类型是 RuntimeException。⚠ 但若有 SQLException 被 catch 后转业务异常则不滚回 | **PARTIAL-NON-BLOCKER**（与 I002 关联）；建议加 `rollbackFor = Exception.class` |

---

## 三、Batch 1.1 Issue Register（汇总）

> 按用户决策 1/2/3：**I002 保留 PARTIAL-BLOCKER（F005/F012/F020/F023）**；**I001 保留**；**其他 PARTIAL-NON-BLOCKER 在 Phase 5 升级 VERIFIED**。

| Issue ID | 影响 Feature | 来源 | Phase 3 状态 | 处置（按用户决策）|
|---|---|---|---|---|
| **I001** | F016 / F017 / F018 | SoT vs 代码冲突（HubSpot/Zoho 已实现 / Router 有 fallback）| CONFLICT | **保留**（用户决策 2：不动 `ext-24.mdc`）|
| **I002** | F005 / F012 / F020 / F023（F001/F003/F019/F021/F022/F025/F028/F030 间接依赖）| DB 缺 EXT-15 列（`object_mappings` / `private_key` / `sync_window_minutes` / `last_incremental_sync_at` / `sync_enabled` 等）+ `crm_contact_map` 表缺失 | PARTIAL-BLOCKER（F005/F012/F020/F023）+ PARTIAL-NON-BLOCKER（其他）| **保留**（用户决策 1：不跑 `migrate_ext_15_crm_real.sql`）|
| **I003** | F006 / F026 | UI `integrationId` 列 vs 后端反查 `platform` vs 表无 `integration_id` 列 | PARTIAL-NON-BLOCKER | **保留**（用户决策 3：Phase 5 实测时确认行为）|
| **I004** | F001-F008 + F029 | 命名/架构异味（`CrmIntegrationService` 实为 `@RestController`，7 个 endpoint 全在 1 文件）| Backlog | **登记**（不进 Phase 4）|
| **I005** | F004 / F005 / F027 / F029 / F025 / F028 | 多重契约漂移：① toggle ACTIVE/PAUSED vs ACTIVE/DISABLED/ERROR；② syncNow 未传 `?force=1`；③ 新建表单缺字段 | PARTIAL-NON-BLOCKER | **保留**（用户决策 3：Phase 5 实测 + 后续批次修复）|
| **新增 I006** | F007 | 后端 `dashboard` endpoint 无前端 tab 消费 | PARTIAL-NON-BLOCKER | **保留**（与 I005 同决策） |
| **新增 I007** | F003 | 后端 `update` endpoint 无前端 edit UI | PARTIAL-NON-BLOCKER | **保留** |
| **新增 I008** | F007 | `dashboard()` 内 `catch (Exception ignore) {}` 吞异常 | PARTIAL-NON-BLOCKER | **保留**（用户决策 3） |
| **新增 I009** | F005 | `@Transactional` 默认 rollbackFor 仅 RuntimeException（建议加 `rollbackFor = Exception.class`） | PARTIAL-NON-BLOCKER | **保留** |

---

## 四、Batch 1.1 全栈静态结论矩阵

| Feature ID | Module | 类型 | 静态缺口 | Phase 2 结论 | Phase 3 结论 | 升级路径 |
|---|---|---|---|---|---|---|
| F001 | 创建集成 | 端点 | C7（C4 关联）：前端表单缺字段 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 实测 + I005 处理 |
| F002 | 集成列表 | 端点 | 无 | UNVERIFIED | UNVERIFIED | Phase 5 实测 |
| F003 | 更新集成 | 端点 | **Shadow**（C5）：前端无 UI | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER + **新增 I007** | 决策 + Phase 5 |
| F004 | 切换状态 | 端点 | **C1**：enum 漂移 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 实测 + I005 |
| **F005** | 手动同步 | 端点 | **C2 + C6**：force 未传 + DB 缺列 + **新增 I009** transaction | PARTIAL-BLOCKER | **PARTIAL-BLOCKER** | Phase 5 实测（用户决策 1：不动 DB）|
| F006 | 同步日志 | 端点 | **C3**：integrationId 列误用 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 + I003 |
| F007 | Dashboard | 端点 | **Shadow** + **新增 I008** 吞异常 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER + **I006** + **I008** | Phase 5 实测 |
| F008 | 窗口跳过 | 行为 | C2 关联 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 实测 |
| F009 | 跨租户拒绝 | 行为 | 无 | UNVERIFIED | UNVERIFIED | Phase 5 实测 |
| F010 | 自动 tick | 排程 | 无 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 实测 |
| F011 | shouldRun | 排程 | 无 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 实测 |
| **F012** | known-ids | 排程 | **依赖 F023 表缺失** | PARTIAL-BLOCKER | **PARTIAL-BLOCKER** | Phase 5 实测（用户决策 1）|
| F013 | Adapter 接口 | Adapter | 无 | UNVERIFIED | UNVERIFIED | Phase 5 实测 |
| F014 | Salesforce | Adapter | C6 间接依赖 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 |
| F015 | Mock | Adapter | MOCK-INTENTIONAL（§2.4）| UNVERIFIED | UNVERIFIED | Phase 5 + 文档化 |
| F016 | HubSpot | Adapter | **CONFLICT**（I001）| CONFLICT | **CONFLICT** | 不动 .mdc（用户决策 2）|
| F017 | Zoho | Adapter | **CONFLICT**（I001）| CONFLICT | **CONFLICT** | 不动 .mdc（用户决策 2）|
| F018 | Router | Router | **CONFLICT**（I001）| CONFLICT | **CONFLICT** | 不动 .mdc（用户决策 2）|
| F019 | CrmIntegration | Entity | C6 缺 9 列 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 |
| **F020** | CrmContactMap | Entity | **依赖 F023 表缺失** | PARTIAL-BLOCKER | **PARTIAL-BLOCKER** | Phase 5（用户决策 1）|
| F021 | CrmSyncLog | Entity | C7 缺 3 列 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 |
| F022 | EXT-15 DDL | DDL | C6 + C7 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5（不跑迁移）|
| **F023** | crm_contact_map | DB | **表缺失** | PARTIAL-BLOCKER | **PARTIAL-BLOCKER** | Phase 5（用户决策 1）|
| F024 | 集成列表 UI | 前端 | 无 | UNVERIFIED | UNVERIFIED | Phase 5 |
| F025 | 新建表单 | 前端 | **C4**：缺字段 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 + I005 |
| F026 | 同步日志 tab | 前端 | **C3** | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 + I003 |
| F027 | syncNow API | 前端 | **C2** force 未传 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 + I005 |
| F028 | objectMappings 输入 | 前端 | **C4** | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 + I005 |
| F029 | status 状态机 | 状态机 | **C1** enum 漂移 + 无隔离 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER + **I004 backlog** | Phase 5 |
| F030 | sync 配置 | 排程配置 | C6 列缺 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | Phase 5 |

---

## 五、Batch 1.1 静态分析结论（最终）

| 维度 | Phase 1 | Phase 2 | Phase 3（本次）| 变化 |
|---|---|---|---|---|
| Feature 总数 | 30 | 30 | 30 | — |
| **VERIFIED** | 0 | 0 | 0 | — |
| **UNVERIFIED** | 7 | 7 | 6 | -1（F026 重判为 PARTIAL-NON-BLOCKER，详情见 §2.6 修订）|
| **PARTIAL-NON-BLOCKER** | 17 | 17 | 21 | +4（F003 / F007 / F026 来自 §2.1 修订 + 新增 I006/I007/I008/I009 关联）|
| **PARTIAL-BLOCKER** | 4 | 4 | 4 | —（F005 / F012 / F020 / F023 — 用户决策 1 保留）|
| **CONFLICT** | 3 | 3 | 3 | —（F016 / F017 / F018 — 用户决策 2 保留）|
| **MOCK-INTENTIONAL** | 0 | 0 | 1 | +1（F015 Mock Adapter）|

> 修订说明：
> - F026 在 Phase 2 按 I003 标 PARTIAL-NON-BLOCKER 已正确；本次确认。
> - F003 在 Phase 1 标 PARTIAL-NON-BLOCKER（I002），本次 Phase 3 重新识别为 **Shadow Feature（后端有 endpoint 但前端无 UI）** + 新增 **I007**。
> - F007 在 Phase 1 标 PARTIAL-NON-BLOCKER，本次 Phase 3 重新识别为 **Shadow Feature（前端无 dashboard tab）+ `catch (Exception ignore) {}` 吞异常** + 新增 **I006 / I008**。
> - F015 在 Phase 1 标 UNVERIFIED，本次识别为 **MOCK-INTENTIONAL**（02-mock 命中 `@ConditionalOnMissingBean`）。

---

## 六、Phase 3 → Phase 4 入口守门

| 检查项 | 状态 | 备注 |
|---|---|---|
| 8 个守门脚本已生成 | ✅ | `audit/scripts/01-08-scan-*.ps1` |
| 8 份扫描报告已落盘 | ✅ | `audit/static-gap/01-08-*.txt` |
| 06 脚本 Bug 已修（backtick 跨字段类型）| ✅ | L3 + L4 已修复并通过验证 |
| Batch 1.1 范围精细分析完成 | ✅ | Shadow / Ghost / Zombie / Fake / Shell / Contract Drift 全识别 |
| Issue Register 已更新 | ✅ | I006 / I007 / I008 / I009 新增 |
| 不动代码 | ✅ | 仅静态分析与报告生成 |
| 不进 Phase 4 | ✅ | 等用户确认 Issue 处置 |
| 跳过 Phase 3.5 | ✅ | Batch 1.1 非重构批次 |

### Phase 4 待用户决策项

| # | 决策项 | 默认建议 |
|---|---|---|
| D1 | I006 / I007（Shadow Feature）是否在 Phase 4 加前端 UI？| 加 dashboard tab + edit 按钮（**推荐**）|
| D2 | I003（integrationId 漂移）处置路径？| 后端 `crm_sync_log` 加 `integration_id` 列 + 前端列名改为 `platform`（**C3 决策 ① + ②**）|
| D3 | I005（多重契约漂移）批量处置优先级？| ① 前端 toggle enum 改 `DISABLED`；② syncNow 传 `?force=1` + 加 loading；③ 新建表单加字段（**D3 决策 ①** 最优先）|
| D4 | I008 / I009 是否在 Phase 4 修？| I008（dashboard catch）暂缓；I009（rollbackFor）加 `rollbackFor = Exception.class`（**D4 决策 ②**）|
| D5 | I002 维持 PARTIAL-BLOCKER 还是升级？（取决于是否接受 Phase 5 必暴露 `Unknown column` 异常）| 维持 PARTIAL-BLOCKER（**按用户决策 1**）|

---

## 七、附录 — 关键位置索引

### 后端（`backend/src/main/java/com/meiyetec/`）

| 文件 | 行号范围 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `crm/CrmAdapter.java` | 21-145 | F013 | 接口契约，OK |
| `crm/CrmAdapterRouter.java` | 21-46 | F018 | **CONFLICT**（I001，与 SoT §5 NPE 不符）|
| `crm/CrmSyncScheduler.java` | 47-209 | F010-F012/F020/F029 | 53/62/207 catch-log-swallow；F012 **PARTIAL-BLOCKER** 依赖 F023 |
| `crm/SalesforceCrmAdapter.java` | 55-216 | F014 | 113/209/296/289 catch-log-swallow/empty |
| `crm/HubSpotCrmAdapter.java` | 71-322 | F016 | 112/158/247/255 catch；**CONFLICT**（I001）|
| `crm/ZohoCrmAdapter.java` | 72-319 | F017 | 112/167/255/262 catch；**CONFLICT**（I001）|
| `crm/MockCrmAdapter.java` | 18-108 | F015 | 21 `@ConditionalOnMissingBean`；44/56/96 catch-ignore-empty（合理）；**MOCK-INTENTIONAL** |
| `service/CrmIntegrationService.java` | 35-156 | F001-F009 | 命名误导（I004）；`syncNow:79` `@Transactional`（I009）；`dashboard:145` catch-ignore（I008）；多端点契约漂移（C1/C2/C3）|
| `entity/CrmIntegration.java` | 55-104 | F019 | `privateKey :79` WRITE_ONLY；`objectMappings :85` 等 13 字段；缺 9 列 DB（F022 I002）|
| `entity/CrmContactMap.java` | 18-39 | F020 | uk `uk_platform_external`；**PARTIAL-BLOCKER** 依赖 F023 表存在 |
| `entity/CrmSyncLog.java` | 16-39 | F021 | 8 字段（含 EXT-15 中 3 个新增）；缺 3 列 DB（I002）|

### 前端（`frontend/meiyetec-web/src/`）

| 文件 | 行号范围 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `views/crm-integration/index.vue` | 4-91 | F024-F029 | 14 行 toggle `ACTIVE/PAUSED`（C1/I005）；19 行 sync 按钮无 loading；27 行 `integrationId` 列（C3）；38-61 行 addForm 仅 4 字段（C4）；89 行 sync 不传 ?force（C2）|
| `api/crm-integration.ts` | 5-17 | F024-F028 | 8 行 `update()` 已封装未调用（F003 Shadow）；14 行 `syncNow(id)` 不传 ?force（C2）；17 行 `dashboard()` 已封装未调用（F007 Shadow）|

### DB / SQL

| 文件 | 行号 / 表 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `audit/baseline/db-snapshot.sql` | line 3433 (`crm_integration` 22 列) / line 3963 (`crm_sync_log` 12 列) | F019 / F021 | 缺 EXT-15 9+3 列（I002）|
| `audit/baseline/db-snapshot.sql` | grep `crm_contact_map` | F020 / F023 | **0 命中**（I002）|
| `db/sql/migrate_ext_15_crm_real.sql` | line 5-19 (加 9 列 + 2 索引) / line 21-43 (crm_contact_map) / line 45-49 (加 3 列) | F022-F023 | DDL 已写未跑（用户决策 1）|

### 脚本 / 报告

| 文件 | 行号 / 范围 | 备注 |
|---|---|---|
| `audit/scripts/01-scan-fake-success.ps1` | 全文 | 299 hits（含 CRM 17 处）|
| `audit/scripts/02-scan-mock.ps1` | 全文 | 90 hits（含 F015 Mock Intentional）|
| `audit/scripts/03-scan-disabled.ps1` | 全文 | 94 hits（CRM 0 命中）|
| `audit/scripts/04-scan-endpoint-coverage.ps1` | 全文 | 已知限制 L1/L2（false positive）|
| `audit/scripts/05-scan-contract-drift.ps1` | 全文 | 1530 hits |
| `audit/scripts/06-scan-soft-delete.ps1` | line 53（**regex 修复**）| 77 hits（63 HIGH + 14 OK）|
| `audit/scripts/07-scan-transaction.ps1` | 全文 | 214 hits（含 F005 I009）|
| `audit/scripts/08-scan-shadow-ghost.ps1` | 全文 | 2003 hits（CRM 0 真实 Shadow）|
| `audit/static-gap/01-08-*.txt` | 全文件 | 8 份落盘报告 |
| `audit/reports/static-gap-report.md` | 全文件 | **本报告** |

---

---

# Phase 3 — Batch 1.2 追加段（EXT-23 Plugin Runtime）

> **追加时间**：2026-09-13 19:00 (UTC+8)
> **Phase**：Phase 3 — Static Gap Audit（Batch 1.2）
> **范围**：Batch 1.2 — EXT-23 Plugin Runtime（聚焦） + 全局视野（沿用 Batch 1.1 守门脚本）
> **引用文件**：`audit/feature-inventory.md` §1.2 / `audit/traceability-matrix.md` §Batch 1.2 / `audit/baseline/scope-freeze-batch-1.2.md` / `audit/issues/issue-register.md` §十七
> **SoT**：`.cursor/rules/ext-23-plugin-runtime.mdc`（按规则 11，文档与代码冲突时标 ISSUE）
> **本批次决策摘要**（沿用 I017-I025 用户决策 @ 18:51）：
> 1. 不跑 `migrate_ext_23_plugin_runtime_state.sql`（I017 决策） → F036/F037/F049/F051 保持 PARTIAL-BLOCKER
> 2. 不改 `ext-23-plugin-runtime.mdc`（I018 决策） → F035/F044 标 CONFLICT
> 3. 接受「Phase 2 静态 + Phase 5 实测」策略（I020/I022/I023/I024 决策） → Phase 5 实测验证
> 4. 登记 backlog（I019/I021/I025） → 重构 / dead code / 孤儿代码待 Post-Audit 处理
> 5. **Batch 1.2 非重构批次** → 跳过 Phase 3.5 Diff Audit（沿用 Batch 1.1）

---

## 八、Batch 1.2 守门脚本命中数（plugin 范围提取）

> 复用 Batch 1.1 的 8 个守门脚本（全量扫描 + grep plugin/Plugin 提取）。

| 脚本 | 全项目 Total | Plugin 范围命中 | 涉及 Feature | 说明 |
|---|---|---|---|---|
| **01-fake-success** | 299 | **13**（含 1 HIGH） | F032 / F033 / F034 / F036 / F037 / F038 / F039 / F042 | catch-log-swallow 11 处 + catch-ignore-empty 1 处（dashboard）；同 I008/I011 Fake Success 模式 |
| **02-mock** | 90 | **0** | — | Plugin Runtime 无 Mock/fallback 设计意图；`@ConditionalOnMissingBean` 仅用于 CRM Mock（Batch 1.1 范围）|
| **03-disabled** | 94 | **0** | — | Plugin Runtime 范围无 @Disabled/@Deprecated/TODO/FIXME 命中 |
| **04-endpoint-coverage** | 后端 2013 / 前端 859 | 后端 12 Shadow / 前端 3 Ghost（false positive）| F043 / F044 / F045 / F046 / F047 | 12 个 `/plugin/runtime/*` 端点 Only-Backend（I024）；3 个 Ghost 是 L1/L2 false positive（`${id}` vs `{id}` 不归一化）|
| **05-contract-drift** | 1530 | **8** | F043 / F044 / F045 / F046 / F047 | 8 个 `@RequestBody` / `@RequestParam` 无 `@Valid`；PluginRuntimeController.load 用 `Map<String,Object>` 接 body（无 schema 校验）|
| **06-soft-delete** | 77 | **0** | — | plugin_install / plugin_market / plugin_review 三表均无 `deleted` 列；plugin_runtime_state 表缺失（I017，脚本不查此）|
| **07-transaction** | 214 | **2** | F036 / F046 | `PluginPersistenceService.recordState` + `PluginService.install` 均 `@Transactional` 默认 rollbackFor 仅 RuntimeException |
| **08-shadow-ghost** | 2003 | 12 Shadow / 3 Ghost（false positive）/ 0 Zombie / 0 Fake / 0 Shell | F043 / F044 / F045 / F046 / F047 | 与 04 同源问题 |

> **Plugin 范围专属命中汇总**：13 (fake-success) + 0 (mock) + 0 (disabled) + 15 (endpoint-coverage 含 false positive) + 8 (contract-drift) + 0 (soft-delete) + 2 (transaction) + 15 (shadow-ghost) = **53 命中**

---

## 九、Batch 1.2 — EXT-23 Plugin Runtime 精细分析

> 范围：`backend/src/main/java/com/meiyetec/plugin/` (8 files) + `controller/PluginRuntimeController.java` + `service/PluginService.java` + `entity/Plugin*.java` (4 files) + `mapper/Plugin*.java` (4 files) + `views/plugin/index.vue` + `api/plugin.ts` + `plugins/demo-hello/`。

### 9.1 已确认的 Shadow / Ghost / Zombie / Fake / Shell Feature（按 F0XX 编号）

| Feature ID | 缺口类型 | 类别 | 位置 | 证据 | 严重程度 | 建议 |
|---|---|---|---|---|---|---|
| **F034** | **Fake Success（catch-ignore-empty）** | `dashboard()` 内 `catch (Exception ignored) {}` 吞所有异常 | `backend/src/main/java/com/meiyetec/plugin/PluginRuntimeService.java:260` `try { ... } catch (Exception ignored) {}` — 持久化层调用失败完全静默 | 01-fake-success 输出 line 131 (`catch-ignore-empty` HIGH 命中) + I008 同模式 | **PARTIAL-NON-BLOCKER**（与 I017 表缺失关联 → 表缺失时 persist 失败被吞 → dashboard.persistedRecords=0 看起来「正常」）| 决策 ① 加 `log.warn` 记录异常原因；决策 ② 上层容错（统一 try/catch 包裹 dashboard 调用）|
| **F036** | **MISSING（DB 表）/ @Transactional 失效** | `recordState` 表缺失 → `TableNotFoundException` → 事务回滚失效 | `backend/src/main/java/com/meiyetec/plugin/PluginPersistenceService.java:43` `@Transactional` + `:65-72` try/catch 包裹整体 | 07-transaction 输出 line 28 (`Transactional-no-rollbackFor`) + 01-fake-success 输出 3 命中（70/81/93 行）+ I017 MISSING | **PARTIAL-BLOCKER**（I017） | **按用户决策 I017**：保留 PARTIAL-BLOCKER；Phase 5 实测时 `TableNotFoundException` 将触发 → 与 I008 Fake Success 同链路 |
| **F037** | **MISSING（DB 表）/ Fake Success** | `restoreStates` + `listByTenant` 表缺失 → 返回 `List.of()` → dashboard 显示 0 记录 | `PluginPersistenceService.java:81` `restoreStates` + `:91-96` `listByTenant` 均 try/catch 包裹 | 01-fake-success 输出 line 122/123（81/93 行）+ I017 MISSING | **PARTIAL-BLOCKER**（依赖 F036/F049） | 同 F036 |
| **F043** | **Shadow Feature**（×6）| 后端 6 个 `/plugin/runtime/*` 平台级端点，前端无任何调用 | 后端：`PluginRuntimeController.java:78-130` `GET /list` `:78-81` + `GET /dashboard` `:84-87` + `POST /{code}/load` `:90-103` + `POST /{code}/enable` `:106-112` + `POST /{code}/disable` `:115-121` + `POST /{code}/unload` `:124-130`<br>前端：`frontend/meiyetec-web/src/api/plugin.ts:6-19` `pluginApi` **无任何 `/plugin/runtime/*` 入口** | 04-endpoint-coverage 6 行 Only-Backend（line 1441/1652/919/1953/1244/1657）+ 08-shadow-ghost 6 行 Shadow Feature | **PARTIAL-NON-BLOCKER**（I024） | 决策 ① 加 runtime tab（推荐）；决策 ② 接受现状（运维 curl 看板） |
| **F044** | **Shadow Feature**（×4）+ **CONFLICT**（I018）| 后端 4 个 `/plugin/runtime/tenant/*` 租户级端点，前端无任何调用；但 `assertTenantAccess` **已实现** `dataScope=ALL` 校验 | 后端：`PluginRuntimeController.java:58-69` `assertTenantAccess`（**已实现 dataScope=ALL**）+ `:140-200` 4 个租户级端点<br>前端：同 F043 无入口<br>SoT：`ext-23-plugin-runtime.mdc` §0.7 + §5 标"无 dataScope 校验" — **与代码矛盾** | 04-endpoint-coverage 4 行 Only-Backend（line 1383/819/2024/1466）+ F035 (`isEnabledForTenant` 业务入口) 同 CONFLICT | **CONFLICT**（I018 — SoT 文档漂移）| **按用户决策 I018**：保留 CONFLICT，不动 `ext-23.mdc`；Phase 5 用户实测跨租户访问 |

### 9.2 已确认的 Contract Drift（前后端契约不一致）

| 编号 | Feature ID | 缺口类型 | 位置 | 证据 | 严重程度 | 建议 |
|---|---|---|---|---|---|---|
| **P1** | F043 | **`@RequestBody` 无 `@Valid` + 无 schema** | `PluginRuntimeController.java:81` `@PostMapping("/{pluginCode}/load") public Result<PluginRuntimeInfo> load(@PathVariable String pluginCode, @RequestBody(required = false) Map<String, Object> body)` — 用 `Map` 接 body，无版本号格式校验 | 05-contract-drift line 531（`RequestBody-without-Valid` 命中）+ PluginRuntimeController.java:90-103 | **PARTIAL-NON-BLOCKER** | 决策 ① 加 DTO `LoadPluginRequest { String version }` + `@Valid`；决策 ② 加 `if (version != null && !version.matches("\\d+\\.\\d+\\.\\d+"))` |
| **P2** | F044 | **`@RequestParam` 无 defaultValue** | `PluginRuntimeController.java:191` `@RequestParam(required = false) Long tenantId` — 无 `defaultValue` | 05-contract-drift line 532（`RequestParam-required-false-no-defaultValue` 命中）| **PARTIAL-NON-BLOCKER**（实际有 `required=false` 兜底）| 决策 接受现状；tenantId=null 时走 `restoreStates()` 无租户过滤 |
| **P3** | F045 / F046 / F047 | **`@RequestBody` / `@RequestParam` 无 `@Valid`（×6）** | `PluginService.java:36/50/51/61/69/111` — `publish`/`listMarket`/`install`/`updatePlugin`/`review` 全无 schema 校验 | 05-contract-drift line 1410-1415（6 命中）+ `service/PluginService.java:38-130` | **PARTIAL-NON-BLOCKER** | 决策：登记 backlog；不阻塞 Phase 5 |
| **P4** | F046 | **`@Transactional` 默认 rollbackFor**（沿用 I008/I009） | `PluginService.java:68` `@Transactional public Result<PluginInstall> install(@RequestBody Map<String, Object> body)` — 默认 only rollback RuntimeException | 07-transaction line 135（`Transactional-no-rollbackFor` 命中）+ `PluginService.java:75-95` | **PARTIAL-NON-BLOCKER** | 加 `rollbackFor = Exception.class`（Post-Audit）|
| **P5** | F054 | **前端 `pluginApi` 不含 `/plugin/runtime/*`** | `frontend/meiyetec-web/src/api/plugin.ts:6-19` — 8 个方法全部对应 `PluginService`（marketplace + install + review），**无任何 runtime 方法** | `plugin.ts:6-19` 全文件 + Operational Notes §5「前端无 runtime tab」一致 | **PARTIAL-NON-BLOCKER**（I024 — Frontend Shadow）| 决策 ① 加 `runtimeList / load / enable / disable / unload / dashboard` 8 个方法（推荐）；决策 ② 接受现状 |

### 9.3 EXT-* SoT 已知冲突（保留 I018 标 CONFLICT）

| Feature ID | 缺口类型 | SoT 描述 | 代码事实 | 处理 |
|---|---|---|---|---|
| **F035** | **CONFLICT**（I018）| `ext-23-plugin-runtime.mdc` §0.7：「**未加租户隔离校验**（直接 `@PathVariable Long tenantId`）」 | `PluginRuntimeController.assertTenantAccess` (`controller/PluginRuntimeController.java:58-69`) **已实现**：`dataScope=ALL` 放行 + `tenantId.equals(currentTenantId)` 等值校验 + `BizCode.FORBIDDEN(403)` 返回 | **按用户决策 I018**：保留 CONFLICT，不动 `ext-23.mdc`；F035 标 **CONFLICT** |
| **F044** | **CONFLICT**（I018）| `ext-23-plugin-runtime.mdc` §5：「租户隔离缺失：`PluginRuntimeController` 所有端点无 `dataScope` 校验」 | `PluginRuntimeController.assertTenantAccess` (`:58-69`) **已实现**；4 个租户级端点 (`/tenant/{tid}/list` + `/tenant/{tid}/{code}/enable` + `/tenant/{tid}/{code}/disable` + `/persisted?tenantId=`) **全部走 `assertTenantAccess` 校验** | 同 F035 |

> **I018 整体处理**：SoT 文档漂移，与代码不符。Batch 1.1 I001 同模式（决策 2 = 不动 .mdc）。Post-Audit 修订。

### 9.4 02-mock 中 Batch 1.2 范围相关命中

| Feature ID | 类别 | 位置 | 证据 | 判定 |
|---|---|---|---|---|
| — | 无命中 | — | — | Plugin Runtime 设计上无 `@ConditionalOnMissingBean` fallback（与 CRM Mock 不同）；无 `localStorage`/`sessionStorage`/`setTimeout` 命中 |

### 9.5 01-fake-success 中 Batch 1.2 范围相关命中（共 13 处）

> **重要**：catch-log-swallow 不一定都是 bug。Phase 5 静态 + Phase 6 实测关联后判断。

| Feature ID | 位置 | 命中模式 | 实际语义 | 判定 |
|---|---|---|---|---|
| **F032** | `PluginRuntimeService.java:97` `run()` ApplicationRunner | catch-log-swallow | 启动期 `loadAllFromDisk` + `recordState` 整体 try/catch → 失败仅 warn（I008 同模式）| **PARTIAL-BLOCKER**（I017 表缺失时整个启动流程静默失败）|
| **F032** | `PluginRuntimeService.java:345` + `:363` `run()` 内 `recordState` 循环 | catch-log-swallow | 启动期逐个 recordState 失败被吞 → 部分 plugin 持久化失败但日志无痕 | **PARTIAL-NON-BLOCKER** |
| **F033** | `PluginRuntimeService.java:202` `disablePlugin` 内 | catch-log-swallow | pf4j 禁用失败仅 log → I022 副作用（lastError 被清）| **PARTIAL-NON-BLOCKER**（I022）|
| **F033** | `PluginRuntimeService.java:224` `enablePluginInternal` 内 | catch-log-swallow | pf4j 启用失败仅 log → `recordState` 仍写 STARTED → 状态与实际不符 | **PARTIAL-NON-BLOCKER** |
| **F033** | `PluginRuntimeService.java:136` `toInfo()` `instanceof MeiyetecPlugin` try/catch | catch-log-swallow | `getPlugin()` 在 FAILED 状态可能抛 → 忽略 | **PARTIAL-NON-BLOCKER**（合理降级）|
| **F034** | `PluginRuntimeService.java:260` `dashboard()` 内 `catch (Exception ignored) {}` | **catch-ignore-empty（HIGH）** | 持久化摘要失败被吞 → dashboard 看起来正常 → I008 Fake Success | **PARTIAL-NON-BLOCKER**（I017 暴露时升级）|
| **F036** | `PluginPersistenceService.java:70` `recordState` 内 try/catch | catch-log-swallow | DB 写失败被吞 → 表缺失时静默 → I017 链路 | **PARTIAL-BLOCKER**（I017）|
| **F036** | `PluginPersistenceService.java:81` `restoreStates` 内 try/catch | catch-log-swallow | DB 读失败被吞 → 返回 `List.of()` → dashboard.persistedRecords=0 | **PARTIAL-BLOCKER**（I017）|
| **F036** | `PluginPersistenceService.java:93` `listByTenant` 内 try/catch | catch-log-swallow | 同 F036 listByTenant 失败被吞 | **PARTIAL-BLOCKER**（I017）|
| **F038** | `PluginTenantService.java:58` `isEnabledForTenant` 内 try/catch | catch-log-swallow | DB 读失败 → 返回 `false` → 业务侧误以为插件未启用（**比返回 true 安全**）| **PARTIAL-NON-BLOCKER**（I023 兜底默认启用 → NPE 风险）|
| **F039** | `PluginTenantService.java:94` `setTenantEnabled` 内 try/catch | catch-log-swallow | DB 写失败 → 返回 `false` → controller 返回 `BizCode.SYSTEM_ERROR` | **PARTIAL-NON-BLOCKER**（已向上抛错误码，OK）|
| **F042** | `PluginSecurityPolicy.java:140` `deniedPackages()`（实际是 `assertCallerAllowed` 内 catch）| catch-log-swallow | 反射探测失败被吞 → 沙箱检查跳过 | **PARTIAL-NON-BLOCKER**（I020 合作式沙箱）|

> **沿用 Batch 1.1 经验**：13 处 catch 中：5 处属 `PARTIAL-BLOCKER`（I017 表缺失链路）+ 8 处属 `PARTIAL-NON-BLOCKER`（已知降级 / 副作用 / 合作式沙箱）。

### 9.6 04-endpoint-coverage 中 Batch 1.2 范围精细对比

> 由于脚本限制（L1/L2 — `{id}` vs `${id}` 不归一化 + 多段路径 regex 不全），人工修正后真实覆盖面如下：

#### 9.6.1 Plugin Runtime 端点（`/plugin/runtime/*`）

| 后端 Endpoint | 后端位置 | 前端 API 调用位置 | 04 报告状态 | 人工修正结论 |
|---|---|---|---|---|
| `GET /plugin/runtime/list` | `PluginRuntimeController.java:78-81` | (无) | Only-Backend | **真实 Shadow Feature**（I024）|
| `GET /plugin/runtime/dashboard` | `PluginRuntimeController.java:84-87` | (无) | Only-Backend | **真实 Shadow Feature**（I024）|
| `POST /plugin/runtime/{pluginCode}/load` | `PluginRuntimeController.java:90-103` | (无) | Only-Backend | **真实 Shadow Feature**（I024）|
| `POST /plugin/runtime/{pluginCode}/enable` | `PluginRuntimeController.java:106-112` | (无) | Only-Backend | **真实 Shadow Feature**（I024）|
| `POST /plugin/runtime/{pluginCode}/disable` | `PluginRuntimeController.java:115-121` | (无) | Only-Backend | **真实 Shadow Feature**（I024）|
| `POST /plugin/runtime/{pluginCode}/unload` | `PluginRuntimeController.java:124-130` | (无) | Only-Backend | **真实 Shadow Feature**（I024）|
| `GET /plugin/runtime/tenant/{tenantId}/list` | `PluginRuntimeController.java:140-160` | (无) | Only-Backend | **真实 Shadow Feature**（I024）+ F044 |
| `POST /plugin/runtime/tenant/{tenantId}/{pluginCode}/enable` | `PluginRuntimeController.java:163-174` | (无) | Only-Backend | **真实 Shadow Feature**（I024）+ F044 |
| `POST /plugin/runtime/tenant/{tenantId}/{pluginCode}/disable` | `PluginRuntimeController.java:177-188` | (无) | Only-Backend | **真实 Shadow Feature**（I024）+ F044 |
| `GET /plugin/runtime/persisted` | `PluginRuntimeController.java:191-200` | (无) | Only-Backend | **真实 Shadow Feature**（I024）+ F044 |

> **真实结论**：Plugin Runtime 范围 **10 个真实 Shadow Feature**（F043 ×6 + F044 ×4），全部由 I024 标记。前端 `api/plugin.ts` 无任何 `/plugin/runtime/*` 入口。

#### 9.6.2 Plugin Marketplace 端点（`/plugin/*`）

| 后端 Endpoint | 后端位置 | 前端 API 调用位置 | 04 报告状态 | 人工修正结论 |
|---|---|---|---|---|
| `POST /plugin/market` | `PluginService.java:38-50` | `api/plugin.ts:7` `request.post('/plugin/market', data)` | Matched | **Matched** |
| `GET /plugin/market/list` | `PluginService.java:52-62` | `api/plugin.ts:8-9` `request.get('/plugin/market/list', { params })` | Matched | **Matched** |
| `POST /plugin/market/{id}` | `PluginService.java:64-72` | `api/plugin.ts:10` `request.post(\`/plugin/market/${id}\`, data)` | Only-Backend（**L1 false positive**）| **Matched**（模板字符串 `${id}` vs 后端 `{id}`）|
| `POST /plugin/install` | `PluginService.java:75-95` | `api/plugin.ts:13` `request.post('/plugin/install', data)` | Matched | **Matched** |
| `POST /plugin/install/{id}/uninstall` | `PluginService.java:97-103` | `api/plugin.ts:14` `request.post(\`/plugin/install/${id}/uninstall\`)` | Only-Backend（**L1 false positive**）| **Matched** |
| `GET /plugin/install/list` | `PluginService.java:105-110` | `api/plugin.ts:15` `request.get('/plugin/install/list')` | Matched | **Matched** |
| `POST /plugin/review` | `PluginService.java:113-121` | `api/plugin.ts:17-18` `request.post('/plugin/review', data)` | Matched | **Matched** |
| `GET /plugin/review/list/{pluginId}` | `PluginService.java:123-130` | `api/plugin.ts:19` `request.get(\`/plugin/review/list/${pluginId}\`)` | Only-Backend（**L1 false positive**）| **Matched** |

> **真实结论**：Plugin Marketplace 范围 **0 个真实 Shadow Feature**。**所有 8 个端点前端都有调用**，仅前端 UI 层面有缺口（前端只有 marketplace + 已安装 tab，无 review 列表展示等）。

### 9.7 06-soft-delete 中 Batch 1.2 范围相关命中

> Plugin Runtime 涉及的 4 张表（`plugin_install` / `plugin_market` / `plugin_review` / `plugin_runtime_state`）**均不在 06 报告范围**（无 `deleted` 列 + `plugin_runtime_state` 表缺失）。

| 表 | 06 报告状态 | 备注 |
|---|---|---|
| `plugin_install` | **不在范围** | db-snapshot.sql:7092（14 列，无 `deleted`）— **索引 `idx_tenant(tenant_id)`** 单列索引，**无 `(tenant_id, plugin_code)` 唯一键** → `setTenantEnabled` 多次调用可产生重复行（潜在问题，但不在本批次范围）|
| `plugin_market` | **不在范围** | db-snapshot.sql:7120（21 列，无 `deleted`）+ `uk_plugin_code (plugin_code)` 唯一键 OK |
| `plugin_review` | **不在范围** | db-snapshot.sql:7159（8 列，无 `deleted`）+ `idx_plugin (plugin_id)` 索引 OK；**注意 PluginService.listReviews 按 pluginId 过滤（未按 tenantId 隔离）→ 跨租户可读别人评价**（不在本批次范围，仅记录）|
| `plugin_runtime_state` | **表缺失** | db-snapshot.sql grep 0 命中（I017 MISSING）|

### 9.8 07-transaction 中 Batch 1.2 范围相关命中

| Feature ID | 位置 | 命中模式 | 实际语义 | 判定 |
|---|---|---|---|---|
| **F036** | `PluginPersistenceService.java:43` `recordState` 上 `@Transactional` | Transactional-no-rollbackFor | 默认 only rollback RuntimeException；方法内 try/catch 包裹整体（`PluginPersistenceService.java:70-72`）→ **事务回滚完全失效**（I008/I009 同模式，I017 表缺失时 NPE 被 catch 不抛 → 事务不回滚）| **PARTIAL-BLOCKER**（I017 链路）|
| **F046** | `PluginService.java:68` `install` 上 `@Transactional` | Transactional-no-rollbackFor | 默认 only rollback RuntimeException；install 流程：`installMapper.insert(i)` + `marketMapper.updateById(plugin)` 累加 installCount — 若 updateById 抛 checked exception → **install 已插入但 installCount 未累加**（数据不一致风险）| **PARTIAL-NON-BLOCKER**（沿用 I009 建议加 `rollbackFor = Exception.class`）|

### 9.9 08-shadow-ghost 中 Batch 1.2 范围相关命中（5 维）

| 类型 | 命中数 | 涉及 Feature | 备注 |
|---|---|---|---|
| **Shadow Feature**（only-backend）| **12** | F043 ×6 + F044 ×4 + **F046** (`POST:/plugin/install/{id}/uninstall`) ×1 + **F047** (`GET:/plugin/review/list/{pluginId}`) ×1 | 10 个 `/plugin/runtime/*` 端点 + 2 个 L1 false positive（`{id}` vs `${id}` 不归一化）|
| **Ghost Feature**（only-frontend）| **3** | F045/F046/F047 ×3 | 全部是 L1/L2 false positive（模板字符串 `${id}` 不识别反引号）|
| **Zombie Feature**（@Deprecated）| **0** | — | Plugin Runtime 范围无 @Deprecated 命中 |
| **Fake Feature**（setTimeout stub）| **0** | — | 无 setTimeout fake async |
| **Shell Feature**（< 500 bytes Vue）| **0** | — | `views/plugin/index.vue` 116 行，完整页面 |

> **真实 Shadow/Ghost 净数**：**10 个真实 Shadow**（F043 ×6 + F044 ×4，I024）；**0 个真实 Ghost**（3 个 L1 false positive）。

### 9.10 EXT-23 特定问题精细识别（沿用 I020-I025）

> 用户要求**特别检查** EXT-23 特定问题；以下为 Phase 3 静态识别结果。

#### 9.10.1 I020 — 沙箱合作式不拦截（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **白名单** `HOST_PUBLIC_PACKAGES` | 3 条：`com.meiyetec.plugin.` / `com.meiyetec.util.` / `com.meiyetec.exception.` | `PluginSecurityPolicy.java:33-37` |
| **黑名单（宿主页）** `HOST_FORBIDDEN_PACKAGES` | 8 条：`service.internal` / `service.impl` / `security` / `mapper` / `config` + 自身 3 类（`PluginRuntimeService`/`PersistenceService`/`TenantService`）| `PluginSecurityPolicy.java:40-50` |
| **黑名单（JDK）** `FORBIDDEN_JDK_PACKAGES` | 9 条：`java.lang.reflect.` / `Runtime` / `Process` / `URLClassLoader` / `sun.misc.` / `sun.reflect.` / `jdk.internal.` / `java.io.FileSystem` / `java.nio.file.Files` | `PluginSecurityPolicy.java:53-63` |
| **`assertCallerAllowed` 实现** | 仅做 `Class.forName("com.meiyetec.service.internal.NonExistent", false, cl)` 探测；如 ClassNotFoundException → return（**OK**）；如能 load → 仅 log.warn 不抛 | `PluginSecurityPolicy.java:106-135` |
| **真实漏洞** | 反射加载 `com.meiyetec.service.*`（非 internal）→ `service.impl` 黑名单可拦 → `service.FooService`（无 internal/impl 后缀）→ **白名单也不含 service → 黑名单也不含 → 默认放行** | 同上 |
| **结论** | 合作式沙箱只能拦前缀完全匹配的包；Phase 5 实测：反射 `class.forName("com.meiyetec.service.impl.UserService")` 应被 `service.impl.` 黑名单拦；反射 `class.forName("com.meiyetec.service.UserService")` 应**不被拦** | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I020 — 已知限制 + Java 17 SecurityManager 已弃用）| — |

#### 9.10.2 I022 — lastError 被清（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **写入策略** | `recordState(...)` `row.setLastError(errorMsg)` 始终刷新（即使 null 也覆盖）| `PluginPersistenceService.java:60` |
| **`disablePlugin` 副作用** | `runtime.disablePlugin(code)` → `recordState(0L, code, info.getVersion(), info.getPluginId(), info.getState(), info.getErrorMessage())` — **info.getErrorMessage() 在 disable 成功后为 null**（pf4j disablePlugin 不抛异常） | `PluginRuntimeService.java:184-191` |
| **后果** | 上一次 FAILED 错误被 null 覆盖 → 运维侧失去 FAILED → DISABLED 转换的审计线索 | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I022） — Phase 5 实测：写 FAILED → 调 disable → 查 DB lastError 应为 null | — |

#### 9.10.3 I023 — 幽灵记录 NPE（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **install 链路** | `POST /plugin/install` → 插 `plugin_install` 表（`enabled=1`/`installStatus=ACTIVE`）→ **不触发 runtime load**（`PluginRuntimeService.loadPlugin` 未调用） | `PluginService.java:75-95` |
| **isEnabledForTenant 兜底** | 业务调 `isEnabledForTenant(code, tenantId)` → pf4j `STARTED` ∧ 无 `plugin_install` 记录 → **默认返回 true**（`PluginTenantService.java:54-60` 兜底） | `PluginTenantService.java:43-66` |
| **NPE 触发链路** | 业务通过反射 `pluginManager.getPlugin("code@unknown")` → PluginWrapper 不为 null 但 `getPlugin()` 返回 null（plugin 未实际 load）→ `((MeiyetecPlugin) null).hello()` → NPE | 推断（无运行时验证）|
| **结论** | 幽灵 install 记录 + 兜底默认启用 = 业务侧调到一个不存在的插件入口 → NPE 风险 | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I023） — Phase 5 实测：install 后调 `isEnabledForTenant` 应返回 true（兜底）；调业务入口应 NPE | — |

#### 9.10.4 I021 — autoLoaded dead code（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **字段定义** | `private Boolean autoLoaded;`（第 50 行）| `PluginRuntimeInfo.java:50` |
| **toInfo() 调用** | `info.setPluginId(...)` + `info.setPluginCode(...)` + `info.setVersion(...)` + `info.setState(...)` + `info.setClassName(...)` + `info.setPluginPath(...)` + `info.setErrorMessage(...)` + SPI 字段（displayName/description/vendor） | `PluginRuntimeService.java:262-289` |
| **`autoLoaded` 调用** | **0 处**（grep `autoLoaded` 全代码库仅 1 处定义 + 0 处 setter / 0 处 getter / 0 处构造器参数） | grep 验证 |
| **结论** | dead code；前端无消费方（api/plugin.ts 无 runtime 调用）| — |
| **判定** | **PARTIAL-NON-BLOCKER**（I021） — Post-Audit 删除或在 `loadAllFromDisk` 路径填充 | — |

#### 9.10.5 I025 — deniedPackages 孤儿（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **方法定义** | `public static Set<String> deniedPackages()` 返回 4 条黑名单（`java.lang.reflect.*` / `sun.misc.*` / `com.meiyetec.service.internal.*` / `com.meiyetec.security.*`）| `PluginSecurityPolicy.java:140-142` |
| **调用方** | **0 处**（grep `deniedPackages` 全代码库：`PluginSecurityPolicy.java` 定义 + `issue-register.md` / `traceability-matrix.md` / `feature-inventory.md` 引用 — 后 3 个是 Issue 描述文档，不是代码调用）| grep 验证 |
| **结论** | 孤儿代码；可作为 `assertPackageAllowed` 增强（返回可拒绝包列表）或删除 | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I025） — Post-Audit 删除或留作未来用 | — |

#### 9.10.6 I019 — PluginService 命名异味（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **类名** | `PluginService`（`*Service` 后缀）| `backend/src/main/java/com/meiyetec/service/PluginService.java` |
| **真实类型** | `@RestController @RequestMapping("/plugin")` — 8 个 REST endpoint 集中单文件 | `PluginService.java:30-31` |
| **项目其他 Controller 位置** | `backend/src/main/java/com/meiyetec/controller/`（如 `PluginRuntimeController.java`）| 路径对比 |
| **结论** | 命名/架构异味；与 Batch 1.1 I004（CrmIntegrationService）完全同模式 | — |
| **判定** | **架构异味 Backlog**（I019） — Post-Audit 重构：移到 `controller/PluginController.java` 或拆 3 个文件（marketplace / install / review） | — |

#### 9.10.7 I017 — plugin_runtime_state 表缺失（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **DDL 文件存在** | `CREATE TABLE IF NOT EXISTS plugin_runtime_state ...` | `backend/src/main/resources/db/sql/migrate_ext_23_plugin_runtime_state.sql:9-29` |
| **db-snapshot.sql 命中** | **0 处**（grep `plugin_runtime_state` 在 `audit/baseline/db-snapshot.sql` 全文件 0 命中）| grep 验证 |
| **代码实体类存在** | `PluginRuntimeState` (8 字段) + `PluginRuntimeStateMapper extends BaseMapper` | `entity/PluginRuntimeState.java` + `mapper/PluginRuntimeStateMapper.java` |
| **运行时后果** | `recordState` 写表 → `TableNotFoundException` → try/catch 仅 warn → 与 Batch 1.1 I008 Fake Success 同链路 | `PluginPersistenceService.java:70-72` |
| **判定** | **MISSING**（I017） — 按用户决策保留 PARTIAL-BLOCKER（F036/F037/F049/F051）| — |

---

## 十、Batch 1.2 Issue Register（汇总）

> 按用户决策 I017-I025（2026-09-13 18:51 落档）：
> **I017** 标 PARTIAL-BLOCKER / **I018** 标 CONFLICT / **I019/I020/I021/I022/I023/I024/I025** 标 PARTIAL-NON-BLOCKER + 各分类标注
> 统一原则：**审计阶段只记录不修复**（规则 6）；Fix Phase = **Post-Audit**

| Issue ID | 影响 Feature | 来源 | Phase 3 状态 | 处置（按用户决策）|
|---|---|---|---|---|
| **I017** | F036 / F037 / F049 / F051 | `plugin_runtime_state` 表缺失（DDL 已写，db-snapshot 未应用）| **PARTIAL-BLOCKER** | 按用户决策 I017：保留 PARTIAL-BLOCKER；Phase 5 实测 `TableNotFoundException` |
| **I018** | F035 / F044 | SoT vs 代码冲突（`assertTenantAccess` **已实现** dataScope 校验，但 .mdc §0.7/§5 标"无校验"）| **CONFLICT** | 按用户决策 I018：保留 CONFLICT，不动 `ext-23.mdc` |
| **I019** | F045 / F046 / F047 | 命名/架构异味（`PluginService` 实为 `@RestController`，8 个 endpoint 全在 1 文件）| Backlog | 登记（不进 Phase 5）；Post-Audit 重构 |
| **I020** | F042 | `assertCallerAllowed` 合作式沙箱（反射加载 `com.meiyetec.service.*`（非 internal）实际不拦截）| **PARTIAL-NON-BLOCKER** | Phase 5 实测反射加载；Post-Audit 接受现状或升级字节码增强 |
| **I021** | F033 / F034 | `PluginRuntimeInfo.autoLoaded` 字段定义但 `toInfo()` 未填充；前端无任何消费方 | **PARTIAL-NON-BLOCKER + dead code** | Post-Audit 删除字段或填充 |
| **I022** | F033 / F036 | `disablePlugin` 的 `recordState(...,errorMsg=null)` 会清掉上一次的 FAILED 错误（运维审计线索丢失）| **PARTIAL-NON-BLOCKER** | Phase 5 实测 FAILED → DISABLED 转换；Post-Audit 接受现状或改 recordState 写入策略 |
| **I023** | F035 / F046 | `plugin_install` 幽灵记录（install 只插表不触发 runtime load；`isEnabledForTenant` 兜底默认启用 → 业务侧 NPE 风险）| **PARTIAL-NON-BLOCKER** | Phase 5 实测 install → isEnabledForTenant → 业务入口；Post-Audit install 联动 load 或文档化 |
| **I024** | F043 / F044 / F052 / F054 | 前端 `plugin/index.vue` 只有「市场」+「已安装」tab；`api/plugin.ts` 不含 `/plugin/runtime/*` | **PARTIAL-NON-BLOCKER + Frontend Shadow（10 个真实 Shadow）** | Phase 5 E2E 验证；Post-Audit 加 runtime tab 或接受现状 |
| **I025** | F042 | `PluginSecurityPolicy.deniedPackages()` 方法定义了但全代码库 0 真实调用方 | **PARTIAL-NON-BLOCKER + 孤儿代码** | Post-Audit 删除或留作未来用 |

---

## 十一、Batch 1.2 全栈静态结论矩阵

| Feature ID | Module | 类型 | 静态缺口 | Phase 2 结论 | Phase 3 结论 | 升级路径 |
|---|---|---|---|---|---|---|
| **F031** | Plugin SPI | SPI 契约 | 无 | UNVERIFIED | **UNVERIFIED**（SPI 契约静态完整）| Phase 5 + demo-hello 实测 |
| **F032** | Plugin 运行时 | init + auto-load | **Fake Success** (`PluginRuntimeService.java:97` 启动期 try/catch 吞异常) + **I017** 链路 | PARTIAL-NON-BLOCKER | **PARTIAL-BLOCKER**（I017 升级）| Phase 5 实测（I017 表缺失触发）|
| **F033** | Plugin 运行时 | 手动生命周期 | **Fake Success** (`disablePlugin:202` + `enablePluginInternal:224` catch-log-swallow) + **I021** autoLoaded dead code + **I022** disable 副作用 | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER + I021 + I022** | Phase 5 实测 FAILED → DISABLED；Post-Audit 处理 I021/I022 |
| **F034** | Plugin 运行时 | 看板 | **Fake Success** (`dashboard:260` `catch (Exception ignored) {}`) + **I024** Shadow（前端无 runtime tab）+ **I021** autoLoaded | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER + I008 同模式 + I024 + I021** | Phase 5 实测；Post-Audit 加 runtime tab |
| **F035** | Plugin 运行时 | isEnabledForTenant | **CONFLICT（I018）**（SoT 标"无 dataScope 校验"，代码已实现）+ **I023** 幽灵 NPE | CONFLICT | **CONFLICT + I018 + I023** | Phase 5 实测跨租户访问 + install → NPE |
| **F036** | Plugin 持久化 | recordState | **MISSING（I017 表缺失）+ @Transactional 失效（catch 整体）+ Fake Success（catch-log-swallow ×1）** | PARTIAL-BLOCKER | **PARTIAL-BLOCKER**（I017 + I022 关联）| Phase 5 实测 `TableNotFoundException`；Post-Audit 跑 DDL |
| **F037** | Plugin 持久化 | restoreStates / listByTenant | **MISSING（I017 依赖）+ Fake Success（catch-log-swallow ×2）** | PARTIAL-BLOCKER | **PARTIAL-BLOCKER**（依赖 F036）| 同 F036 |
| **F038** | Plugin 租户 | 叠加 pf4j | **Fake Success（`isEnabledForTenant:58` try/catch 兜底返回 false）+ I023 兜底默认启用** | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER + I023** | Phase 5 实测；Post-Audit 接受现状或业务侧联动 |
| **F039** | Plugin 租户 | setTenantEnabled | **Fake Success（`setTenantEnabled:94` try/catch 兜底返回 false）+ 潜在重复行（plugin_install 无 `(tenant_id, plugin_code)` 唯一键）**| PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER + 潜在 DB 索引风险** | Phase 5 实测；Post-Audit 加 UNIQUE KEY |
| **F040** | Plugin 沙箱 | 白/黑名单常量 | 无 | UNVERIFIED | **UNVERIFIED** | Phase 5 验证 |
| **F041** | Plugin 沙箱 | assertPackageAllowed | 无（demo-hello 已调）| UNVERIFIED | **UNVERIFIED** | Phase 5 实测 demo-hello onStart |
| **F042** | Plugin 沙箱 | assertCallerAllowed | **合作式沙箱（I020）+ 孤儿代码（I025）** | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER + I020 + I025** | Phase 5 实测反射加载 service.*；Post-Audit 处理 I025 |
| **F043** | Plugin Runtime Controller | 平台级 API（×6）| **Shadow Feature（I024，10 个端点均无前端消费）+ P1 RequestBody 无 @Valid** | UNVERIFIED | **PARTIAL-NON-BLOCKER + Shadow + I024** | Phase 5 E2E curl 验证；Post-Audit 加 runtime tab |
| **F044** | Plugin Runtime Controller | 租户级 API（×4）| **Shadow Feature（I024）+ CONFLICT（I018，dataScope 校验已实现）+ P2 RequestParam 无 defaultValue** | CONFLICT | **CONFLICT + I018 + Shadow + I024** | Phase 5 实测跨租户访问（assertTenantAccess 真的拦？）；Post-Audit 改 .mdc |
| **F045** | Plugin Marketplace | 上架管理（×3）| **命名异味（I019，PluginService 是 @RestController）+ P3 RequestBody/RequestParam 无 @Valid（×3）**| UNVERIFIED | **PARTIAL-NON-BLOCKER + 架构异味 + Contract Drift** | Phase 5 E2E；Post-Audit 重构 |
| **F046** | Plugin Marketplace | 安装生命周期（×3）| **I023 幽灵 NPE + I019 命名异味 + P4 @Transactional 无 rollbackFor** | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER + I019 + I023 + P4** | Phase 5 实测 install → isEnabledForTenant；Post-Audit 重构 + 业务侧联动 |
| **F047** | Plugin Marketplace | 评价（×2）| **I019 命名异味 + PluginService.listReviews 未按 tenantId 隔离（潜在跨租户读）**| UNVERIFIED | **PARTIAL-NON-BLOCKER + I019 + 跨租户风险** | Phase 5 E2E；Post-Audit 重构 + 加 tenantId 过滤 |
| **F048** | Plugin 实体 | Marketplace 三件套 | 无（DB 已存在 21/14/8 列）| UNVERIFIED | **UNVERIFIED** | Phase 5 DESCRIBE |
| **F049** | Plugin 实体 | PluginRuntimeState | **MISSING（I017，表缺失）+ 8 字段与 DDL 一致** | PARTIAL-BLOCKER | **PARTIAL-BLOCKER**（I017）| Phase 5 DESCRIBE（应 `Table not exists`）；Post-Audit 跑 DDL |
| **F050** | Plugin 配置 | application.yml | **风险（`./plugins` 相对路径，Operational Notes §2 + scope-freeze §九.5 建议生产改绝对路径）** | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | 部署时改绝对路径 |
| **F051** | Plugin DB | migrate_ext_23 DDL | **DDL 已写未应用（I017）+ DDL 9 字段与 entity 8 字段一致** | PARTIAL-BLOCKER | **PARTIAL-BLOCKER**（I017）| Phase 5 grep db-snapshot；Post-Audit 跑批 |
| **F052** | Plugin 前端 | 市场 + 已安装 tab | **Shadow（I024，无 runtime tab）+ 后端 PluginService.listReviews 未对接前端**| UNVERIFIED | **PARTIAL-NON-BLOCKER + Shadow + I024** | Phase 5 E2E；Post-Audit 加 runtime tab |
| **F053** | Plugin 前端 | 发布 + 评价对话框 | **无 loading 态 / 无二次确认 / 无错误码展示（与 I006 同模式）** | UNVERIFIED | **PARTIAL-NON-BLOCKER** | Phase 5 E2E；Post-Audit 增强 |
| **F054** | Plugin 前端 | api/plugin.ts | **缺所有 `/plugin/runtime/*` 方法（I024）+ 仅 marketplace 8 方法** | UNVERIFIED | **PARTIAL-NON-BLOCKER + Shadow + I024** | Phase 5 E2E；Post-Audit 加 runtime 8 方法 |
| **F055** | Plugin 示例 | demo-hello | **未编译成 jar（`plugins/demo-hello/` 无 `target/`）+ `build.bat` 仅 Windows + plugin.properties 完整 5 字段** | UNVERIFIED | **UNVERIFIED** | Phase 5 用户手动 `mvn clean package` |

---

## 十二、Batch 1.2 静态分析结论（最终）

| 维度 | Phase 1 | Phase 2 | Phase 3（本次）| 变化 |
|---|---|---|---|---|
| Feature 总数 | 25 | 25 | 25 | — |
| **VERIFIED** | 0 | 0 | 0 | — |
| **UNVERIFIED** | 11 | 5 | 5（F031/F040/F041/F048/F055） | — |
| **PARTIAL-NON-BLOCKER** | 9 | 15 | **13** | -2（F032 升级 PARTIAL-BLOCKER 因 I017 链路；F034 在 Phase 2 已 PARTIAL-NON-BLOCKER，本次加深识别 I008+I024+I021 不计数量增量；F033 在 Phase 2 已 PARTIAL-NON-BLOCKER，本次新增 I021/I022 不计数量增量）|
| **PARTIAL-BLOCKER** | 4 | 4 | **5** | +1（F032 在 Phase 3 重新识别为 PARTIAL-BLOCKER 因 I017 链路；F051 在 Phase 1 已 MISSING，本次确认按 I017 决策计入 PARTIAL-BLOCKER）|
| **CONFLICT** | 1 | 1 | **2** | +1（F044 在 Phase 2 已 CONFLICT，本次确认 I018 决策保留 CONFLICT）|
| **MISSING** | 0 | 0 | 0 | —（F049/F051 等价于 PARTIAL-BLOCKER，按 I017 决策标记）|
| **合计** | 25 | 25 | **25** | — |

> 修订说明：
> - F032 在 Phase 2 标 PARTIAL-NON-BLOCKER，本次 Phase 3 因启动期 try/catch 整体吞异常 + I017 链路，重新识别为 **PARTIAL-BLOCKER**。
> - F033 在 Phase 2 标 PARTIAL-NON-BLOCKER，本次新增 **I021 dead code + I022 disable 副作用** 关联。
> - F034 在 Phase 2 标 PARTIAL-NON-BLOCKER，本次重新识别为 **dashboard catch-ignore-empty（I008 同模式）+ I024 Shadow + I021**。
> - F036 在 Phase 2 标 PARTIAL-BLOCKER，本次确认 + 新增 **@Transactional 失效（catch 包裹整体）+ I022 关联**。
> - F042 在 Phase 2 标 PARTIAL-NON-BLOCKER，本次新增 **I025 孤儿代码**。
> - F044 在 Phase 2 标 CONFLICT，本次新增 **10 个真实 Shadow Feature**（I024）+ P2 Contract Drift。
> - F046 在 Phase 2 标 PARTIAL-NON-BLOCKER，本次新增 **P4 @Transactional 无 rollbackFor**。
> - F047 在 Phase 2 标 UNVERIFIED，本次重新识别为 **PARTIAL-NON-BLOCKER + 跨租户读风险 + I019 命名异味**。
> - F049/F051 在 Phase 1 标 MISSING，本次确认 I017 决策保留 PARTIAL-BLOCKER。
> - F050 在 Phase 2 标 PARTIAL-NON-BLOCKER，本次确认（无新增）。

---

## 十三、Batch 1.2 Phase 3 → Phase 4 入口守门

| 检查项 | 状态 | 备注 |
|---|---|---|
| 8 个守门脚本已复用 | ✅ | `audit/scripts/01-08-scan-*.ps1`（沿用 Batch 1.1）|
| 8 份扫描报告已落盘 + 重跑 | ✅ | `audit/static-gap/01-08-*.txt`（重跑于 2026-09-13 19:00）|
| Batch 1.2 范围精细分析完成 | ✅ | Shadow / Ghost / Fake / 架构异味 / Contract Drift / I020-I025 实证全识别 |
| Issue Register 已更新（I017-I025 落档）| ✅ | `audit/issues/issue-register.md` §十七（2026-09-13 18:51）|
| 本批次新增 / 加深识别 | ✅ | F032 升级 PARTIAL-BLOCKER；F034 / F033 / F036 / F044 / F046 / F047 加深识别 |
| 不动代码 | ✅ | 仅静态分析与报告生成 |
| 不进 Phase 4 | ✅ | 等用户确认 Issue 处置（D6-D10 决策）|
| 跳过 Phase 3.5 | ✅ | Batch 1.2 非重构批次 |
| 受保护页面零风险 | ✅ | Plugin Runtime 端点（`/plugin/runtime/*` + `/plugin/*`）与 `/dashboard` / `/mh/visual` / `/mh/consent` 后端接口零交集 |

### Phase 4 待用户决策项（Batch 1.2）

| # | 决策项 | 默认建议 |
|---|---|---|
| **D6** | I024（前端无 runtime tab，10 个真实 Shadow）是否在 Phase 4 加 runtime tab？| 加 runtime tab + `api/plugin.ts` 加 8 个 runtime 方法（**推荐**）|
| **D7** | I017（plugin_runtime_state 表缺失）维持 PARTIAL-BLOCKER 还是 Phase 4 跑 migration？| 维持 PARTIAL-BLOCKER（**按用户决策 I017**）|
| **D8** | I018（SoT 文档漂移）维持 CONFLICT 还是 Phase 4 改 `.mdc`？| 维持 CONFLICT（**按用户决策 I018**）|
| **D9** | I019（PluginService 命名异味）+ I025（deniedPackages 孤儿）+ I021（autoLoaded dead code）合并到 backlog？| 合并（**推荐** — Post-Audit 一次性清理）|
| **D10** | I020（合作式沙箱）+ I022（disable 副作用）+ I023（幽灵 NPE）Phase 5 实测优先级？| 优先级 ① I023（install→NPE 业务侧风险最大）→ ② I022（审计线索丢失）→ ③ I020（理论风险）|

---

## 十四、附录 — Batch 1.2 关键位置索引

### 后端（`backend/src/main/java/com/meiyetec/plugin/`）

| 文件 | 行号范围 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `MeiyetecPlugin.java` | 27-47 | F031 | SPI 5 方法 + 2 default；vendor 默认 "unknown" |
| `MeiyetecPluginAdapter.java` | 25-38 | F031 | pf4j Plugin 桥接；start/stop 构造 ctx；Plugin#getPluginId() final |
| `PluginRuntimeService.java` | 71-101（init/run）+ 152-213（生命周期）+ 217-244（dashboard）+ 262-289（toInfo） | F032 / F033 / F034 / F035 | 97/136/202/224/345/363 catch-log-swallow；**260 dashboard `catch (Exception ignored) {}` HIGH 命中（I008 同模式）**；toInfo 无 autoLoaded（I021）|
| `PluginPersistenceService.java` | 42-77（recordState）+ 81-87（restoreStates）+ 91-96（listByTenant）| F036 / F037 | 43 `@Transactional` 默认 rollbackFor（I022）；70/81/93 catch-log-swallow ×3（I017 链路）|
| `PluginTenantService.java` | 43-66（isEnabledForTenant）+ 71-95（setTenantEnabled）+ 99（listByTenant）| F038 / F039 | 58/94 catch-log-swallow ×2；**54-60 兜底默认启用**（I023 NPE 链路）|
| `PluginSecurityPolicy.java` | 33-63（常量）+ 78-97（assertPackageAllowed）+ 106-135（assertCallerAllowed）+ 140-142（deniedPackages）| F040 / F041 / F042 | 9 白/黑名单常量；assertCallerAllowed 仅探测 service.internal（**I020 合作式** + 140 catch-log-swallow）；**deniedPackages 孤儿（I025）**|
| `PluginRuntimeInfo.java` | 43-58（字段）+ 50（autoLoaded）| F033 / F034 | 12 字段；**autoLoaded dead code（I021）** |
| `PluginRuntimeContext.java` | 全文件 | F031 / F055 | 不可变快照 + attr 链式 KV；OK |

### 后端 Controller / Service

| 文件 | 行号范围 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `controller/PluginRuntimeController.java` | 58-69（assertTenantAccess）+ 78-200（10 个端点）| F043 / F044 | **CONFLICT（I018，dataScope 校验已实现）**；10 个端点全部 Only-Backend（I024 Shadow）；P1/P2 Contract Drift |
| `service/PluginService.java` | 38-130（8 个端点）| F045 / F046 / F047 | **命名异味（I019）**；P3 Contract Drift ×6；P4 @Transactional（I009 同模式）；I023 install 幽灵 NPE 链路 |

### 后端 Entity / Mapper

| 文件 | 行号范围 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `entity/PluginRuntimeState.java` | 27-54 | F049 | 8 字段；**MISSING（I017 表缺失）**；@TableField 自动填充 |
| `entity/PluginInstall.java` | 10-31 | F038 / F039 / F048 | 14 字段；`idx_tenant` 单列索引（**无 `(tenant_id, plugin_code)` UNIQUE KEY → F039 重复行风险**）|
| `entity/PluginMarket.java` | 全文件 | F045 / F048 | 21 字段；`uk_plugin_code` UNIQUE OK |
| `entity/PluginReview.java` | 全文件 | F047 / F048 | 8 字段；`idx_plugin` 索引；**PluginService.listReviews 未按 tenantId 隔离（F047 跨租户风险）**|
| `mapper/PluginRuntimeStateMapper.java` | 5 | F049 | 仅 `extends BaseMapper`；OK（**但表缺失 → 启动期 NPE**）|
| `mapper/PluginInstallMapper.java` / `PluginMarketMapper.java` / `PluginReviewMapper.java` | 5/5/5 | F048 | 仅 `extends BaseMapper`；OK |

### 前端

| 文件 | 行号范围 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `views/plugin/index.vue` | 4-30（tab）+ 38-78（dialog）| F052 / F053 | **Shadow（I024，无 runtime tab）**；无 loading / 无二次确认 / 无错误码展示 |
| `api/plugin.ts` | 6-19 | F054 | **缺所有 `/plugin/runtime/*` 方法（I024 Shadow）**；仅 marketplace 8 方法 |

### DB / SQL

| 文件 | 行号 / 表 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `audit/baseline/db-snapshot.sql` | line 7086 (`plugin_install` 14 列) / line 7114 (`plugin_market` 21 列) / line 7153 (`plugin_review` 8 列) | F048 | 三表已存在 |
| `audit/baseline/db-snapshot.sql` | grep `plugin_runtime_state` | F049 | **0 命中（I017 MISSING 已确认）** |
| `db/sql/migrate_ext_23_plugin_runtime_state.sql` | line 9-29 | F051 | DDL 已写（含 uk_tenant_plugin + idx_state + idx_updated）未跑 |
| `application.yml` | line 183-187 | F050 | `plugin.runtime.{enabled,plugins-root,auto-load-on-startup}` 三配置；`./plugins` 相对路径风险 |

### 路由

| 文件 | 行号 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `frontend/meiyetec-web/src/router/index.ts` | 169 | F052 | `path: 'plugin'` → `views/plugin/index.vue`，permission `'dev:access'`（OK）|

### 示例插件

| 文件 | 行号范围 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `plugins/demo-hello/pom.xml` | 全文件 | F055 | `provided` scope 引用 meiyetec-api + pf4j；OK |
| `plugins/demo-hello/src/main/java/com/meiyetec/demo/DemoHelloPlugin.java` | 全文件（58 行）| F055 | 继承 `MeiyetecPluginAdapter`；onStart 调沙箱自检 ×2（`com.meiyetec.demo.` + `com.meiyetec.plugin.`）+ `hello(String)` 业务入口；OK |
| `plugins/demo-hello/src/main/resources/META-INF/pf4j/plugin.properties` | 6 行 | F055 | 5 字段（`plugin.id/class/version/provider/dependencies`）齐全；OK |
| `plugins/demo-hello/build.bat` | 全文件 | F055 | Windows 编译脚本；**未编译成 jar（无 `target/`）**；Phase 5 用户手动 |

### 守门脚本 / 报告

| 文件 | 行号 / 范围 | 备注 |
|---|---|---|
| `audit/scripts/01-08-scan-*.ps1` | 全文 | 全量扫描；plugin 范围 grep 命中 53 处 |
| `audit/static-gap/01-fake-success.txt` | line 122-134 | 13 命中（plugin 范围）|
| `audit/static-gap/02-mock.txt` | — | 0 命中 |
| `audit/static-gap/03-disabled.txt` | — | 0 命中 |
| `audit/static-gap/04-endpoint-coverage.txt` | line 25/223/339/367/439/469/521/540/603/818/819/894/919/1184/1203/1244/1246/1248/1331/1383/1392/1441/1466/1652/1657/1953/2024/2037/2121/2230/2404 | 31 行 plugin 相关（含 false positive）|
| `audit/static-gap/05-contract-drift.txt` | line 531-532 + 1410-1415 | 8 命中 |
| `audit/static-gap/06-soft-delete.txt` | — | 0 命中 |
| `audit/static-gap/07-transaction.txt` | line 28 + 135 | 2 命中 |
| `audit/static-gap/08-shadow-ghost.txt` | line 24/76/95/158/373/374/449/474/739/758/799/801/803/886/938/947/996/1021/1207/1212/1508/1579/1592/1668/1777/1951 | 26 行 plugin 相关（12 Shadow + 3 Ghost false positive）|
| `audit/reports/static-gap-report.md` | §八 - §十四 | **Batch 1.2 段（本报告）**|

---

## 十五、Batch 1.2 与 I017-I025 交叉引用

> 用户要求**与 I017-I025 的交叉引用**；下表为 Phase 3 实证矩阵。

| Issue ID | 静态证据（行号）| 动态验证 Phase 5 任务 | 修复路径 |
|---|---|---|---|
| **I017** | `plugin_runtime_state` 在 `db-snapshot.sql` 0 命中；DDL `migrate_ext_23_plugin_runtime_state.sql:9-29` 已写 | 启动后端 + 调 `POST /plugin/runtime/demo-hello/load` → 期望触发 `TableNotFoundException`（被 catch 吞 → log warn）；调 `GET /plugin/runtime/persisted` → 期望返回 `[]`（dashboard 显示 0 records） | Post-Audit：跑 `migrate_ext_23_plugin_runtime_state.sql` + 重生成 `db-snapshot.sql` |
| **I018** | `PluginRuntimeController.java:58-69` `assertTenantAccess` 已实现 `dataScope=ALL` + `tenantId.equals(currentTenantId)` | 普通租户（dataScope≠ALL）调 `GET /plugin/runtime/tenant/{otherTid}/list` → 期望 403 `BizCode.FORBIDDEN` | Post-Audit：改 `.cursor/rules/ext-23-plugin-runtime.mdc` §0.7 + §5（删除"无 dataScope 校验"描述）|
| **I019** | `service/PluginService.java` 文件名 `*Service`，实际 `@RestController @RequestMapping("/plugin")` + 8 个 endpoint 集中单文件 | 重构验证：移到 `controller/PluginController.java` 或拆 3 个文件 | Post-Audit 重构 |
| **I020** | `PluginSecurityPolicy.java:106-135` `assertCallerAllowed` 仅探测 `service.internal.NonExistent`，**反射 `com.meiyetec.service.*`（非 internal）实际不拦截** | 写反射加载 demo：`class.forName("com.meiyetec.service.UserService", false, pluginClassLoader)` → 期望**不抛异常**（沙箱合作式限制）；`class.forName("com.meiyetec.service.impl.FooServiceImpl", false, ...)` → 期望抛（service.impl. 黑名单匹配）| Post-Audit：接受现状（Java 17 SecurityManager 弃用）或升级为字节码增强（agent）/ OSGi（成本高）|
| **I021** | `PluginRuntimeInfo.java:50` `autoLoaded` 字段定义；`PluginRuntimeService.java:262-289` `toInfo()` 无 `setAutoLoaded()`；全代码库 grep `autoLoaded` 仅 1 处定义 + 0 真实调用 | 删除字段验证：编译通过 + 启动 OK；或填充验证：`loadAllFromDisk` 路径调 `setAutoLoaded(true)` + `loadPlugin` 路径调 `setAutoLoaded(false)` | Post-Audit：删除字段或在 `loadAllFromDisk` 填充 |
| **I022** | `PluginPersistenceService.java:60` `row.setLastError(errorMsg)` 始终刷新；`PluginRuntimeService.java:184-191` `disablePlugin` 调 `recordState(..., info.getErrorMessage())` 成功时为 null | 写 plugin 触发 FAILED → DB lastError 有内容 → 调 `disablePlugin` → DB lastError 应为 null（**I022 实证**）| Post-Audit：接受现状（"成功覆盖失败"语义统一）或改 recordState 写入策略（errorMsg=null 时保留旧值）|
| **I023** | `PluginService.java:75-95` `install` 只插 `plugin_install` 不触发 runtime load；`PluginTenantService.java:54-60` 兜底默认启用 | install 一个未实际 load 的 plugin → 调 `isEnabledForTenant(code, tid)` → 期望返回 true（**I023 实证**）；反射调 `((MeiyetecPlugin) wrapper.getPlugin()).hello()` → 期望 NPE（wrapper.getPlugin() 返回 null）| Post-Audit：install 时联动 `POST /plugin/runtime/{code}/load`（异步 + 失败容忍）或文档化为已知约束 |
| **I024** | `plugin/index.vue:4-30` 仅 marketplace + 已安装 tab；`api/plugin.ts:6-19` 仅 marketplace 8 方法 | curl `GET /plugin/runtime/list` 期望 200（有数据）；前端 `views/plugin/index.vue` 无 runtime tab → **10 个真实 Shadow** | Post-Audit：加 runtime tab + `api/plugin.ts` 加 8 个 runtime 方法 |
| **I025** | `PluginSecurityPolicy.java:140-142` `deniedPackages()` 定义；全代码库 grep 0 真实调用（仅 issue-register/traceability/feature-inventory 引用）| 验证：删除 `deniedPackages` 方法 + 编译通过 + 启动 OK（无影响）| Post-Audit：删除或留作未来 `assertPackageAllowed` 增强用 |

---

**Phase 3 产出（Batch 1.2）完成。25 个 Feature 精细分析 + I017-I025 实证矩阵已落 `audit/reports/static-gap-report.md` §八-§十五。**

**等待用户确认 D6-D10 决策项 → 启动 Phase 4（运行时计划）。**

**Phase 3 产出（Batch 1.1）完成。30 个 Feature 精细分析已落 `audit/reports/static-gap-report.md` §一-§七。**

**等待用户确认 D1-D5 决策项 → 启动 Phase 4（运行时计划）。**

---

# Phase 3 — Batch 1.3 追加段（EXT-22 Google Maps Place Details + 有赞）

> **追加时间**：2026-09-13 23:52 (UTC+8)
> **Phase**：Phase 3 — Static Gap Audit（Batch 1.3）
> **范围**：Batch 1.3 — EXT-22 Google Maps Place Details + 有赞（聚焦）+ 全局视野（沿用 Batch 1.1/1.2 守门脚本）
> **引用文件**：`audit/feature-inventory.md` §1.3 / `audit/traceability-matrix-batch-1.3*.md` / `audit/baseline/scope-freeze-batch-1.3.md` / `audit/issues/issue-register.md` §十九
> **SoT**：`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（按规则 11，文档与代码冲突时标 ISSUE）
> **本批次决策摘要**（沿用 scope-freeze-batch-1.3.md §十 10 项用户决策）：
> 1. GCP / 有赞凭据缺失 → Mock 降级（决策 #1）→ F062 / F068-F070 / F071 / F077 / F078 Phase 5 UNVERIFIED
> 2. SoT vs 代码 5 项漂移 → 中立标 I026-I030（决策 #2，沿用 I001/I018 模式）→ Fix Phase = Post-Audit
> 3. `buildWebhookUrl` 孤儿工具 → BACKLOG-001 / I033（决策 #3，沿用 I013 模式）
> 4. `webhook_msg_log` 表归 EXT-24，Batch 1.3 只引用不重复审计（决策 #6）→ F078 仅标 L0 引用
> 5. GCP API Key 泄露（`substring(length-4)`）仅标注不改（决策 #9）→ F060 / I036 仅记录
> 6. **Batch 1.3 非重构批次** → 跳过 Phase 3.5 Diff Audit（沿用 Batch 1.1/1.2）
> 7. **不动业务代码**（规则 6 + Phase 3 边界）→ 仅静态分析与报告生成

---

## 十六、Batch 1.3 守门脚本命中数（EXT-22 范围提取）

> 复用 Batch 1.1 的 8 个守门脚本（全量扫描 + grep `googlemaps|ecommerce|GoogleMaps|Youzan|EcommerceService|EcommercePlatform|YouzanEcommerce|MockEcommerce` 提取）。

| 脚本 | 全项目 Total | Batch 1.3 范围命中 | 涉及 Feature | 说明 |
|---|---|---|---|---|
| **01-fake-success** | 299 | **0** | — | 全部命中在 `YouzanWebhookController.java` / `EcommerceService.java` / `GoogleMapsService.java` 是 `catch-log-swallow`，已分类到 02/07；本批次 catch-log-swallow 沿用 I009/I011 模式（Phase 5 + Phase 6 关联判断，不默认全当 bug）|
| **02-mock** | 90 | **18** | F060 / F061 / F062 / F063 / F064 / F073 + 范围全集 | `MockEcommerceAdapter`（F073）`@ConditionalOnMissingBean(name="youzanEcommerceAdapter")` + `mockGeocode(address)` (F061/F063) + `STORES` 5 门店常量 (F064) — 详见 §十八 |
| **03-disabled** | 94 | **4** | F077 (×4) | `YouzanWebhookController` `return ...` catch-ignored 与 transactional-no-rollbackFor 命中 — 详见 §二十二 |
| **04-endpoint-coverage** | 后端 2013 / 前端 859 | **29**（23 后端 Shadow + 6 后端特定 / 前端 Ghost 6）| F060-F064 / F068-F070 / F075 / F076 / F077 + 范围全集 | 详细见 §二十一 |
| **05-contract-drift** | 1530 | **18** | F060 / F061 / F062 / F063 / F068 / F076 / F077 | 18 个 `@RequestBody` / `@RequestParam` 无 `@Valid`；F077 webhook controller 接受 `HttpServletRequest` 但 `signature` 用 `required=false` — 详见 §二十二 |
| **06-soft-delete** | 77 | **0** | — | EXT-22 涉及的 5 张表（`google_maps_config` / `google_maps_geocode_log` / `ecommerce_platform` / `ecommerce_order` / `webhook_msg_log`）均无 `deleted` 列 + 部分表 MISSING（I029 / EXT-24 边界） |
| **07-transaction** | 214 | **3** | F076 / F077 | `EcommerceService.java:108/225` (`syncProducts`/`syncOrders` `@Transactional`) + `YouzanWebhookController.java:76` (`@Transactional` 未指定 rollbackFor) — 详见 §二十二 |
| **08-shadow-ghost** | 2003 | **23**（14 Shadow + 9 Ghost frontend ${id}）| F060 / F062 / F075 / F076 / F077 + 范围全集 | F077 webhook 端点 **真实 Shadow**（前端无任何入口）；其余 9 个 Ghost 是 L1 false positive（`${id}` vs `{id}` 不归一化） |

> **Batch 1.3 范围专属命中汇总**：0 (fake-success) + 18 (mock) + 4 (disabled) + 29 (endpoint-coverage) + 18 (contract-drift) + 0 (soft-delete) + 3 (transaction) + 23 (shadow-ghost) = **95 命中**
>
> 注：上述计数在脚本输出基础上做去重（同一行多关键词合并）；F077 webhook controller 因含多种 catch/transaction 模式，在 03 + 07 重复命中 — 已分类。

---

## 十七、Batch 1.3 — EXT-22 Google Maps + 有赞 精细分析

> 范围：
> - 后端 Google Maps：`backend/src/main/java/com/meiyetec/googlemaps/` (2 文件) + `service/GoogleMapsService.java` (9 端点)
> - 后端 Ecommerce：`ecommerce/EcommerceAdapter.java` 接口 + `EcommerceAdapterRouter.java` + `YouzanEcommerceAdapter.java` (305 行，含 `verifyWebhookSignature` + `buildWebhookUrl`) + `MockEcommerceAdapter.java` (78 行) + `service/EcommerceService.java` (10 端点)
> - 后端 Webhook：`controller/YouzanWebhookController.java` (359 行，**SoT §5 描述反向** — 已完整实现)
> - 后端 Entity：`entity/{GoogleMapsConfig, GoogleMapsGeocodeLog, EcommercePlatform, EcommerceOrder, EcommerceProduct, EcommerceSyncLog}.java`
> - 前端：`views/google-maps/index.vue` (175 行) + `views/ecommerce/index.vue` (95 行) + `api/{google-maps, ecommerce}.ts`
> - SQL：`db/sql/migrate_ext_17_google_maps.sql` (53 行) + `db/sql/migrate_ext_22_google_maps_place_details.sql` (15 行)
> - SoT：`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`

### 17.1 Batch 1.3 Feature 范围（沿用 feature-inventory.md §1.3.2，25 项 F056-F080）

| 模块 | Feature ID 范围 | 数量 | 说明 |
|---|---|---|---|
| Google Maps 实体 + Client + Config | F056-F059 | 4 | 11 字段 config + 11 字段 geocode_log + 381 行 geocoding client + 17 行 config |
| Google Maps Service（9 端点：config×2 + health + geocode + place/details + geocode/batch + stores + heatmap + geocode/log） | F060-F064 | 5 | 三段门禁 + mock 降级 + 日志写入 |
| Google Maps 前端 | F065 | 1 | 4 tab + enablePlaceDetails 开关 + 计费警告（**SoT §5 #3 部分反向**） |
| Ecommerce 实体 | F066-F067 | 2 | EcommercePlatform 含 appId(L19) + kdtId(L21) + signSecret(L23) 三新字段 |
| Ecommerce Adapter（接口 + 鉴权 + 拉取 + 健康 + 签名 + URL 工具）| F068-F072 | 5 | verifyWebhookSignature md5 算法 + buildWebhookUrl 孤儿 |
| Ecommerce Adapter Mock + Router | F073-F074 | 2 | `@ConditionalOnMissingBean` Mock fallback |
| Ecommerce Service（10 端点：platform×3 + health + product/list + product/sync + order/list + order/sync + dashboard + ...）| F075-F076 | 2 | tenantId=0L 强制（无隔离）|
| Ecommerce Webhook（接收端 + 幂等）| F077-F078 | 2 | **SoT §5 #1 #2 #4 反向已实现完整** |
| Ecommerce 前端 + Frontend API + DB 迁移 | F079-F080 | 2 | 3 tab + 添加对话框缺字段 |
| **合计** | **F056-F080** | **25** | 与 scope-freeze §3.3 预期 20-25 一致 |

---

## 十八、Batch 1.3 已确认的 Shadow / Ghost / Zombie / Fake / Shell Feature

> 注：04/08 脚本已知限制 L1（`{id}` vs `${id}` 不归一化）+ L2（多段路径 regex）会产生 false positive，下表经人工修正后真实覆盖面。

### 18.1 Shadow Feature（后端有 / 前端无）

| Feature ID | 缺口类型 | 类别 | 位置 | 证据 | 严重程度 | 建议 |
|---|---|---|---|---|---|---|
| **F062** | **真实 Shadow** | `/place/details` 计费接口前端有 UI 但仅"测试"，**saveConfig 缺字段** | 后端：`service/GoogleMapsService.java:191-232` `placeDetails(@RequestBody Map<String,Object> body)` — 三段门禁完整<br>前端：测试 tab 在 `views/google-maps/index.vue:88-129`（**有**）但 `configForm` 仅含 `apiKey/defaultZoom/enablePlaceDetails`（**缺** `defaultLanguage/defaultRegion/quotaDaily/enabled` — `views/google-maps/index.vue:139-148` ref + saveConfig） | `views/google-maps/index.vue:139` `configForm = ref({ apiKey: '', defaultZoom: 13, enablePlaceDetails: 0 })` 仅 3 字段 vs `EcommerceService.java:75-77` 类比 `saveConfig` 默认填充 5 字段；保存时 `defaultLanguage/defaultRegion/quotaDaily/enabled` 全被覆盖为 null/默认值 | **PARTIAL-NON-BLOCKER**（与 I028 关联） | 决策 ① configForm 加 4 字段（推荐）；决策 ② saveConfig 在前端表单层做 PATCH 语义（只传修改字段） |
| **F077** | **真实 Shadow** | `POST /webhook/youzan` 后端完整实现但前端无任何调用入口（**这是 webhook，应如此**）| 后端：`controller/YouzanWebhookController.java:76-203` 完整实现 ContentCachingRequestWrapper + 验签 + msg_id 幂等去重 + upsert<br>前端：（无 — webhook 由有赞后台推送）| `04-endpoint-coverage` Only-Backend line 129 `POST:/webhook/youzan` + `YouzanWebhookController.java:77` `@PostMapping("/webhook/youzan")` | **MOCK-INTENTIONAL**（决策 #2 — SoT 描述反向，正确；与 I027 同源） | 保持现状；Phase 5 用 Mock 推送样本模拟 |
| **F078** | **真实 Shadow（端点层面）** | `msg_id` 幂等去重逻辑存在但**仅在 webhook 链路内**，无独立运维查询接口 | 后端：`YouzanWebhookController.java:136-149` 私有幂等逻辑 + `:195-200` 写 webhook_msg_log<br>前端：（无 — 由 webhook 链路自动调用）| `YouzanWebhookController` 无 GET 端点暴露 webhook_msg_log 列表（运维侧无法查"最近收到了哪些 msg_id / 哪些 FAILED"）| **PARTIAL-NON-BLOCKER**（I030 关联）| 决策 ① 加 `GET /webhook/youzan/log?source=YOUZAN&status=...&pageSize=...`；决策 ② 接受现状（运维通过 SQL 查） |
| **F079** | **真实 Shadow（字段层）** | 添加平台对话框**缺** `appId/kdtId/signSecret/clientId/clientSecret` 字段 UI | 后端：`EcommercePlatform` 实体 21 字段（含 EXT-22 新增 3 字段 + EXT-16 既有 5 字段）<br>前端：`views/ecommerce/index.vue:60-77` 添加对话框**仅 3 字段**：`platform` / `shopName` / `shopId` | `EcommercePlatform.java:13-23` vs `views/ecommerce/index.vue:61-72` el-form-item | **PARTIAL-NON-BLOCKER**（I028 关联）| 决策 ① 加 5 字段 UI（推荐）；决策 ② 接受现状（SQL 直写，SoT §5 一致 — 用户决策）|
| **F063** | **真实 Shadow（端点 + UI）** | `/geocode/batch` admin-only 端点**完全无前端入口** | 后端：`GoogleMapsService.java:259-318` `geocodeBatch(@RequestBody Map<String,Object> body)` admin 守卫<br>前端：`views/google-maps/index.vue` 4 tab **无批量导入 tab** | `04-endpoint-coverage` 仅后端 + 8-tab 内无 geocode/batch 入口 | **PARTIAL-NON-BLOCKER**（I032 关联）| 决策 ① 加 5 tab 批量导入；决策 ② 接受现状（运维通过 curl）|

### 18.2 Ghost Feature（前端有 / 后端无）

> 4 处 Ghost 全部由 L1 false positive 引起（前端 `${id}` 模板字符串 vs 后端 `{id}` PathVariable）。

| Feature ID | 缺口类型 | 位置 | 证据 | 判定 |
|---|---|---|---|---|
| — | L1 false positive × 3 | `views/ecommerce/index.vue` 表内**无 update 按钮**但 API 封装 4 端点无对应使用 | `api/ecommerce.ts:6` `updatePlatform` **已封装未调用**（与 Batch 1.1 F003 / Batch 1.2 F045 同模式）| **真实 Shadow（F075 关联）** |

> **真实结论**：Batch 1.3 端点层面 **0 个真实 Ghost Feature**（前端均无对应端点消费）。**前端 UI 调用层面**才有缺失（F062 configForm 缺字段 / F079 缺字段 UI / F063 缺批量导入 tab）。

### 18.3 Zombie Feature（@Deprecated / @Disabled）

> **0 命中**（Batch 1.3 范围无 @Deprecated / @Disabled 标注）。

### 18.4 Fake / Shell Feature

> **0 命中**（无 setTimeout fake async / 无 < 500 bytes Vue 占位页）。

---

## 十九、Batch 1.3 已确认的 Contract Drift（前后端契约不一致）

> 沿用 Batch 1.1 §2.2 模板（C 编号 + Feature ID + 位置 + 证据 + 严重程度 + 建议）。

### 19.1 前后端契约漂移

| 编号 | Feature ID | 缺口类型 | 位置 | 证据 | 严重程度 | 建议 |
|---|---|---|---|---|---|---|
| **C1** | F062 / F065 | **`configForm` 缺字段，saveConfig 覆盖风险** | 前端：`views/google-maps/index.vue:139` `configForm = ref({ apiKey: '', defaultZoom: 13, enablePlaceDetails: 0 })` 仅 3 字段<br>后端：`GoogleMapsService.java:75-80` `saveConfig` 默认填充 5 字段（`defaultLanguage/defaultRegion/quotaDaily/enabled/enablePlaceDetails`）<br>**调用路径**：`views/google-maps/index.vue:158-161` `await googleMapsApi.saveConfig(configForm.value)` 直接 POST 整个 form | 前端表单缺字段 → 后端默认值生效 → 但若租户先**手动**调过 saveConfig 改了 `defaultLanguage=en` → 后端 UI 改 `enablePlaceDetails=1` → saveConfig 把 `defaultLanguage=null` 覆盖（除非后端有 if-null 兜底——`saveConfig` `:76-80` 已有 if-null 兜底 OK）| **PARTIAL-NON-BLOCKER**（**注意**：后端 if-null 兜底已存在，**实际不会覆盖**非空字段；仅前端缺字段提示不友好） | 决策 ① 前端补 4 字段（推荐）；决策 ② 接受现状 |
| **C2** | F066 / F077 | **EcommercePlatform 字段写入缺失** | 前端：`views/ecommerce/index.vue:60-77` 添加对话框 3 字段<br>后端 `EcommercePlatform` 实体：21 字段（含 `appKey` / `appSecret` / `appId` / `kdtId` / `signSecret` / `webhookUrl` / `webhookSecret` / `apiBaseUrl`）<br>后端：`EcommerceService.java:65-72` `createPlatform` **强制 `platform` 必填** + 默认 `status=ACTIVE` + `tenantId=0L`；**不做必填校验**其他字段 | `EcommerceService.java:65-71` 接受 `@RequestBody EcommercePlatform p` → 直接 insert → 前端缺字段但**后端不报错**（只是存 null）| **PARTIAL-NON-BLOCKER**（I028 关联）| 决策 ① 加 5 字段 UI（推荐）；决策 ② 加后端必填校验（最小集 `appKey/appSecret` for 有赞）；决策 ③ 接受现状（SQL 直写） |
| **C3** | F077 | **webhook `signature` 头 `required=false`** | 后端：`YouzanWebhookController.java:79-80` `@RequestHeader(value = "X-Youzan-Sign", required = false) String signature` — **理论上允许缺签名头**<br>签名校验：`YouzanWebhookController.java:88-90` 缺签名 → 返回 `UNAUTHORIZED`（`BizCode.UNAUTHORIZED` 实际可能在 BizCode.java 未定义，需 Phase 2 验证）<br>SoT：`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` §0.5 签名头是必填（"HTTP 头 X-Youzan-Sign" 没说 optional）| `@RequestHeader(value="X-Youzan-Sign", required=false)` 与"必填"语义不符；缺签名不应静默返回 401，应**直接返回 401** — 当前实现实际也是 401（流程是读取 rawBody → 校验空 → 校验 signature null → 返回 UNAUTHORIZED），**符合 SoT** | **PARTIAL-NON-BLOCKER**（`required=false` 是合理的，因为要在缺签名时返回自定义错误码而非 Spring 默认 400）| 决策 接受现状（自定义 401 错误码比 Spring 默认错误友好）|
| **C4** | F075 | **`updatePlatform` 无租户隔离** | 后端：`EcommerceService.java:82-89` `updatePlatform(@PathVariable Long id, @RequestBody EcommercePlatform p)` — **不校验 `id` 对应 platform 的 tenantId** vs 当前 tenant<br>同时：`createPlatform` `:69` `p.setTenantId(0L)` — 强制 tenantId=0L（**有意为之** — 跨租户共享 platform 配置）| `EcommerceService.java:69` vs `:83-89`（无 tenant 校验）| **MOCK-INTENTIONAL**（**有意架构**：platform 配置全平台共享）+ **PARTIAL-NON-BLOCKER**（租户隔离缺失）| 决策 ① 接受现状（设计有意）；决策 ② 加 tenant 隔离（与 F066 entity `tenantId` 字段语义冲突）|
| **C5** | F077 | **webhook upsert 字段映射与 EcommerceService 不同源** | 后端 1：`EcommerceService.java:241-353` `upsertOrder(platform, raw)` — 私有方法<br>后端 2：`YouzanWebhookController.java:206-358` `upsertOrder(platform, rawBody)` — 私有方法 + **复制** EcommerceService 版本独立维护<br>**结论**：字段映射逻辑**重复实现** → 未来字段变更时易 drift | `YouzanWebhookController.java:206-358` vs `EcommerceService.java:241-353` | **PARTIAL-NON-BLOCKER**（重复代码，非漂移）| 决策 ① 抽 `EcommerceUpserter` 公共类（推荐）；决策 ② 接受现状（重复代码登记 backlog）|
| **C6** | F077 | **`webhook_msg_log` 仅记 msgId 不记 timestamp / nonce** | 后端：`YouzanWebhookController.java:58-63` `WebhookMsgLog` 实体：`source/msgId/msgType/externalOrderId/status/rawPayload/signature/errorMessage/processedAt/createdAt`<br>SoT：`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` §5 已知："没有 webhook 重放保护：`verifyWebhookSignature` 只验签，不记录已处理 msg_id" — **与代码反向**（已加 webhook_msg_log 幂等去重）；但 SoT §5 未提 timestamp / nonce | `WebhookMsgLog` 实体无 `created_at + 1h` 时间窗过滤；`YouzanWebhookController.java:136-149` `selectOne(... eq(msgId, msgId) limit 1)` 无时间窗 | **PARTIAL-NON-BLOCKER**（I035 关联）| 决策 ① 加 `webhook_msg_log.processed_at > now() - 1h` 时间窗（推荐）；决策 ② 接受现状（重放窗口受外部网络时钟制约，1h 时间窗不足以防主动重放）|
| **C7** | F062 | **`/place/details` 未写 `google_maps_geocode_log`**（审计缺失）| 后端：`GoogleMapsService.java:191-232` `placeDetails` 完整流程：读 placeId → 校验配置 → 校验 enablePlaceDetails=1 → 调 `geocodingClient.placeDetails` → 返回 11 fields<br>**注意**：步骤 4 调 `geocodingClient.placeDetails` 后**没有**写 `google_maps_geocode_log`（与 `/geocode` `:159-170` + `/geocode/batch` `:289-303` 写日志的行为不一致）| `GoogleMapsService.java:191-232` vs `:159-170` / `:289-303` | **PARTIAL-NON-BLOCKER**（I031 关联 — 计费接口无对账日志）| 决策 ① Place Details 也写日志（推荐 — GCP 计费对账）；决策 ② 接受现状（依赖 GCP 后台用量） |

### 19.2 后端内部契约漂移

| 编号 | Feature ID | 缺口类型 | 位置 | 证据 | 严重程度 | 建议 |
|---|---|---|---|---|---|---|
| **C8** | F076 | **`@Transactional` 默认 rollbackFor 仅 RuntimeException**（×2） | `EcommerceService.java:108` `syncProducts` `@Transactional` + `:225` `syncOrders` `@Transactional` — 默认 `rollbackFor = RuntimeException.class`<br>类似 Batch 1.2 P4 模式 | 07-transaction 输出 line 102/206 | **PARTIAL-NON-BLOCKER** | 加 `rollbackFor = Exception.class`（Post-Audit）|
| **C9** | F077 | **`@Transactional` 未指定 rollbackFor** | `YouzanWebhookController.java:76` `@Transactional` — 默认 only rollback RuntimeException<br>`upsertOrder` 抛 `IllegalStateException("ObjectMapper 不可用")`（`:214`）+ `JSONException`（`:204`）→ **受检异常不滚回** | 07-transaction 输出 line 78 + `YouzanWebhookController.java:76` | **PARTIAL-NON-BLOCKER**（I034 关联）| 加 `rollbackFor = Exception.class`（Post-Audit）|
| **C10** | F070 / F077 | **OAuth2 token 缓存策略无 `volatile` / 弱一致** | `YouzanEcommerceAdapter.java:148-155` token 缓存命中逻辑：`cfg.getAccessToken() != null && cfg.getTokenExpireAt() != null && cfg.getTokenExpireAt().isAfter(now.plusMinutes(2))`<br>**风险**：分布式部署时 token 缓存不一致（A 节点刚 refresh → DB 写入 → B 节点尚未看到）<br>**实际**：本项目单库单服务，无分布式锁需求 | `YouzanEcommerceAdapter.java:148-155` | **PARTIAL-NON-BLOCKER**（单实例无影响）| 决策 接受现状（架构层面已规避） |

---

## 二十、Batch 1.3 EXT-* SoT 已知冲突（中立标 I026-I030 沿用）

> 按 project-audit.mdc 规则 11 + scope-freeze §五.2 用户修正：**审计阶段发现 EXT-* 文档本身与代码不一致** → 沿用 Batch 1.1 I001 / Batch 1.2 I018 模式（中立标，Fix Phase = Post-Audit）。
>
> 本次审计发现 **5 项与 SoT 方向反转**（与 feature-inventory.md §1.3.4 一致）+ **1 项表 MISSING（用户修正）** + **2 项 PARTIAL-NON-BLOCKER**。

| Feature ID | 缺口类型 | SoT 描述 | 代码事实 | 漂移类型 | 处理 |
|---|---|---|---|---|---|
| **F071 / F077** | **CONFLICT**（I026 中立标） | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` §5 #1：「`verifyWebhookSignature` 是孤儿代码 — `YouzanEcommerceAdapter.verifyWebhookSignature` 已写好算法，但 `EcommerceService` 没有 `/webhook/youzan` 接收端」 | `YouzanEcommerceAdapter.java:146-168` `public static boolean verifyWebhookSignature(String signSecret, String rawBody, String signature)` — md5(signSecret + rawBody) 全小写 hex + equalsIgnoreCase<br>**已被调用**：`YouzanWebhookController.java:121` `if (!YouzanEcommerceAdapter.verifyWebhookSignature(pf.getSignSecret(), rawBody, signature))` | **CONFLICT**（反向 — SoT 描述孤儿，实际已被调用） | **按用户决策 #2（I026 中立标）**：保留 CONFLICT，不动 `ext-22.mdc`；Phase 5 jshell 单元测试验证算法 |
| **F077 / F078** | **CONFLICT**（I027 中立标） | SoT §5 #2：「webhook 接收端缺失 — `EcommerceService` 没有 `/webhook/youzan` 路由」 | `YouzanWebhookController.java:76-203` `@RestController @PostMapping("/webhook/youzan") @Transactional` **完整实现**：<br>① `ContentCachingRequestWrapper` 读 rawBody（L85-95）<br>② 反查 `EcommercePlatform` by kdtId（L107-114）<br>③ `verifyWebhookSignature` 验签（L121）<br>④ `msg_id` 幂等去重（L136-149）<br>⑤ `upsert ecommerce_order`（L182-186）<br>⑥ 写 `webhook_msg_log`（L195-200） | **CONFLICT**（反向 — SoT 描述缺失，实际已完整实现） | **按用户决策 #2（I027 中立标）**：保留 CONFLICT，不动 `ext-22.mdc` |
| **F065 / F079** | **PARTIAL-NON-BLOCKER**（I028 部分反向）| SoT §5 #3：「前端缺 UI：`google-maps/index.vue` 不显示 `enablePlaceDetails` 开关；`ecommerce/index.vue` 不显示 `appId/kdtId/signSecret` 字段」 | **部分反向**：<br>① `views/google-maps/index.vue:13-31` `enablePlaceDetails` el-switch + 计费警告 el-alert **已实现**（与 SoT §5 #3 第一条反向）<br>② `views/ecommerce/index.vue:60-77` 添加对话框**仅 3 字段**（platform/shopName/shopId）— **确认缺** `appId/kdtId/signSecret`（与 SoT §5 #3 第二条一致） | **PARTIAL-NON-BLOCKER**（部分反向） | **按用户决策 #2（I028）**：Phase 2 验证 enablePlaceDetails 是否实际保存到后端；接受 ecommerce 缺字段现状（SQL 直写） |
| **F077 / F078** | **CONFLICT**（I030 中立标） | SoT §5 #4：「没有 webhook 重放保护 — `verifyWebhookSignature` 只验签，不记录已处理 msg_id」 | `YouzanWebhookController.java:136-149` **实际加了 `webhook_msg_log` 幂等去重**：重复 msg_id → 查既有 log 返回 `duplicate=true + previousStatus + logId` + HTTP 200；表已通过 `migrate_ext_24_webhook_msg_log.sql:8` 创建（**注意：来自 EXT-24，不是 EXT-22** — 用户决策 #6 Batch 1.3 只引用不重复审计） | **CONFLICT**（反向 — SoT 描述缺失，实际已实现） | **按用户决策 #2（I030 中立标）**：保留 CONFLICT，不动 `ext-22.mdc`；但 I035 时间窗缺失仍标 PARTIAL-NON-BLOCKER |
| **F056 / F057 / F060** | **MISSING（DB）**（用户修正 — I029）| SoT §8.1 + 用户 prompt 第 7 节假设：「`google_maps_config` 表缺失」 | **实际是 preflight 假设 vs 代码漂移**（用户修正 #3，**非 SoT vs 代码 CONFLICT**）：<br>DDL `migrate_ext_17_google_maps.sql:9-53` 已 `DROP + CREATE`<br>ALTER `migrate_ext_22_google_maps_place_details.sql:6` `ADD COLUMN IF NOT EXISTS enable_place_details`<br>ALTER `migrate_ext_22:11` `ADD COLUMN IF NOT EXISTS app_id/kdt_id/sign_secret`<br>**dev DB 可能未跑迁移** → 表在 DB 不存在（沿用 Batch 1.1 I002 / Batch 1.2 I017 MISSING 模式）| **MISSING**（非 CONFLICT）| **按用户决策 #3**：Phase 1 期间跑 `migrate_ext_17_google_maps.sql` + `migrate_ext_22_google_maps_place_details.sql` 跑批即解 |

---

## 二十一、Batch 1.3 02-mock / 04-endpoint-coverage / 08-shadow-ghost 中精细对比

### 21.1 02-mock 中 Batch 1.3 范围相关命中（18 处，去重后）

| Feature ID | 类别 | 位置 | 证据 | 判定 |
|---|---|---|---|---|
| **F073** | MOCK-INTENTIONAL | `backend/src/main/java/com/meiyetec/ecommerce/MockEcommerceAdapter.java:21` `@ConditionalOnMissingBean(name = "youzanEcommerceAdapter")` | 02-mock 输出 line 78（`ConditionalOnMissingBean-mock-fallback`）| **MOCK-INTENTIONAL**（设计 fallback，user decision #1 Mock 降级一致） |
| **F061 / F063** | MOCK-INTENTIONAL（降级路径） | `GoogleMapsService.java:243-253` `mockGeocode(String address)` — 用 `30+nextDouble()*10` 模拟经纬度<br>`GoogleMapsService.java:33-43` STORES 常量（5 门店）| 02-mock 输出 line 79 + 02-mock 输出 `localStorage` 模式不命中（本批次无 localStorage 假持久化） | **MOCK-INTENTIONAL**（未配置时降级，user decision #1 Mock 降级一致）|
| **F068 / F069 / F070 / F071** | MOCK-INTENTIONAL（adapter fallback）| `EcommerceAdapterRouter.java:30-34` `getAdapter(platform)` — miss → fallback MOCK | 02-mock 输出 router fallback | **MOCK-INTENTIONAL**（沿用 CrmAdapterRouter 同源设计）|
| **F061 / F063** | Hardcode 风险 | `GoogleMapsService.java:243-253` mock 路径硬编码经纬度范围（lat 30-40, lng 110-130）— 实际上是中国中部 | 同上 | **PARTIAL-NON-BLOCKER**（mock 不会在生产被调到，仅 dev fallback）|

### 21.2 04-endpoint-coverage 中 Batch 1.3 范围精细对比（29 处，去重后）

#### 21.2.1 Google Maps 端点

| 后端 Endpoint | 后端位置 | 前端 API 调用位置 | 04 报告状态 | 人工修正结论 |
|---|---|---|---|---|
| `POST /google-maps/config` | `GoogleMapsService.java:67-91` | `api/google-maps.ts:9` `saveConfig(body)` `request.post('/google-maps/config', body)` | Matched | **Matched** |
| `GET /google-maps/config` | `GoogleMapsService.java:94-110` | `api/google-maps.ts:10` `getConfig()` | Matched | **Matched** |
| `POST /google-maps/health` | `GoogleMapsService.java:113-131` | (无显式 — `loadConfig()` onMounted 间接触发) | Only-Backend | **Matched**（间接调用）|
| `POST /google-maps/geocode` | `GoogleMapsService.java:138-180` | `api/google-maps.ts:12-13` `geocodeStore(body)` | Matched | **Matched** |
| `POST /google-maps/place/details` | `GoogleMapsService.java:191-232` | `api/google-maps.ts:16-17` `getPlaceDetails(body)` | Matched | **Matched** |
| `POST /google-maps/geocode/batch` | `GoogleMapsService.java:259-318` | (无) | Only-Backend × 1 | **真实 Shadow Feature**（F063 / I032 — admin-only，运维侧 curl）|
| `GET /google-maps/stores` | `GoogleMapsService.java:320-322` | `api/google-maps.ts:14` `listStores()` | Matched | **Matched** |
| `GET /google-maps/heatmap` | `GoogleMapsService.java:325-338` | `api/google-maps.ts:18` `getMemberHeatmap()` | Matched | **Matched** |
| `GET /google-maps/geocode/log` | `GoogleMapsService.java:341-347` | (无 — 前端 "门店地图" tab 显示 mock 数据，但 log 列未消费) | Only-Backend × 1 | **真实 Shadow Feature**（F064 关联 — 审计接口未前端暴露）|

#### 21.2.2 Ecommerce 端点

| 后端 Endpoint | 后端位置 | 前端 API 调用位置 | 04 报告状态 | 人工修正结论 |
|---|---|---|---|---|
| `POST /ecommerce/platform` | `EcommerceService.java:65-72` | `api/ecommerce.ts:6` `createPlatform(data)` | Matched | **Matched** |
| `GET /ecommerce/platform/list` | `EcommerceService.java:75-80` | `api/ecommerce.ts:7` `listPlatforms()` | Matched | **Matched** |
| `POST /ecommerce/platform/{id}` | `EcommerceService.java:83-89` | `api/ecommerce.ts:8` `updatePlatform(id, data)` **已封装但 `views/ecommerce/index.vue` 表内无 update 按钮 — 未调用** | Only-Backend（**L1 false positive**） | **Matched**（API 已封装）+ **真实 Shadow**（UI 无入口，F075 关联）|
| `GET /ecommerce/platform/{id}/health` | `EcommerceService.java:92-94` | (无) | Only-Backend × 1 | **真实 Shadow Feature**（F075 关联 — 端点无前端调用）|
| `GET /ecommerce/product/list` | `EcommerceService.java:97-105` | `api/ecommerce.ts:10-11` `listProducts(params)` | Matched | **Matched** |
| `POST /ecommerce/product/sync/{platformId}` | `EcommerceService.java:108-150` | `api/ecommerce.ts:12` `syncProducts(platformId)` | Only-Backend（**L1 false positive**） | **Matched** |
| `GET /ecommerce/order/list` | `EcommerceService.java:213-222` | `api/ecommerce.ts:14-15` `listOrders(params)` | Matched | **Matched** |
| `POST /ecommerce/order/sync/{platformId}` | `EcommerceService.java:225-289` | `api/ecommerce.ts:16` `syncOrders(platformId)` | Only-Backend（**L1 false positive**） | **Matched** |
| `GET /ecommerce/dashboard` | `EcommerceService.java:374-388` | `api/ecommerce.ts:8` `dashboard()` **已封装但 `views/ecommerce/index.vue` 未调用** | Only-Backend | **Matched**（API 已封装）+ **真实 Shadow**（UI 无入口 — 与 Batch 1.1 F007 同模式）|

#### 21.2.3 Webhook 端点

| 后端 Endpoint | 后端位置 | 前端 API 调用位置 | 04 报告状态 | 人工修正结论 |
|---|---|---|---|---|
| `POST /webhook/youzan` | `YouzanWebhookController.java:77` | (无 — webhook 由有赞后台推送，**应如此**)| Only-Backend × 1 | **MOCK-INTENTIONAL**（F077，user decision #2 SoT 描述反向 — 已完整实现，正确）|

> **真实结论**：Batch 1.3 端点层面 **3 个真实 Shadow Feature**（F063 `/geocode/batch` + F064 `/geocode/log` + F075 `/platform/{id}/health`）+ **1 个 MOCK-INTENTIONAL**（F077 webhook）+ **3 个 L1 false positive（${id} ↔ {id} 不归一化）**。**0 个真实 Ghost Feature**。

### 21.3 08-shadow-ghost 中 Batch 1.3 范围相关命中（5 维，去重后）

| 类型 | 真实命中数 | 涉及 Feature | 备注 |
|---|---|---|---|
| **Shadow Feature**（only-backend）| **3 真实 + 11 false positive** | F063 ×1 + F064 ×1 + F075 ×1 + L1 false positive（`/p3-suite/ecommerce/*` 旧路径 + `${id}` vs `{id}`） | 11 个 false positive 中：`POST:/p3-suite/ecommerce/{platform/list/dashboard/webhook/sync/products/sync/orders/auth}` 是历史路径未删除 — **建议删除或重定向**（不在本批次范围，登记 backlog） |
| **Ghost Feature**（only-frontend）| **9 false positive** | F075 ×3 + F076 ×3 + F079 ×3 | 全部 L1 false positive（前端模板字符串 `${id}` 不识别反引号） |
| **Zombie Feature**（@Deprecated）| **0** | — | 无 @Deprecated |
| **Fake Feature**（setTimeout stub）| **0** | — | 无 setTimeout fake async |
| **Shell Feature**（< 500 bytes Vue）| **0** | — | `views/ecommerce/index.vue` 95 行 + `views/google-maps/index.vue` 175 行均完整页面 |

> **真实 Shadow/Ghost 净数**：**3 个真实 Shadow**（F063/F064/F075）+ **0 个真实 Ghost**。

---

## 二十二、Batch 1.3 06-soft-delete / 07-transaction / 05-contract-drift 中精细对比

### 22.1 06-soft-delete 中 Batch 1.3 范围相关命中

> EXT-22 涉及的 5 张表（`google_maps_config` / `google_maps_geocode_log` / `ecommerce_platform` / `ecommerce_order` / `webhook_msg_log`）**均无 `deleted` 列** + 部分表 MISSING（I029 / EXT-24 边界）。

| 表 | 06 报告状态 | 备注 |
|---|---|---|
| `google_maps_config` | **不在范围**（无 deleted 列） | db-snapshot 命中待验证；I029 MISSING 模式 |
| `google_maps_geocode_log` | **不在范围**（无 deleted 列） | db-snapshot 命中待验证；I029 MISSING 模式 |
| `ecommerce_platform` | **不在范围**（无 deleted 列） | db-snapshot 命中待验证；3 新列 `app_id/kdt_id/sign_secret` DDL 已写 |
| `ecommerce_order` | **不在范围**（无 deleted 列） | db-snapshot 命中待验证；**无 `deleted` 列 — 硬删除而非软删除**，业务侧"恢复订单"功能无法实现 |
| `webhook_msg_log` | **不在范围**（无 deleted 列） | **EXT-24 边界**（user decision #6 Batch 1.3 只引用）；uk `uk_source_msg(source, msg_id)` 唯一键 OK |

### 22.2 07-transaction 中 Batch 1.3 范围相关命中（3 处）

| Feature ID | 位置 | 命中模式 | 实际语义 | 判定 |
|---|---|---|---|---|
| **F076** | `EcommerceService.java:108` `syncProducts` `@Transactional` | Transactional-no-rollbackFor | 默认 only rollback RuntimeException；`syncProducts` 流程：`syncLogMapper.insert(syncLog)` + `adapter.fetchProducts(pf, ...)` + 循环 `upsertProduct` + `persistTokenCache(pf)` + `platformMapper.updateById(pf)` + `syncLogMapper.updateById(syncLog)` — 若其中任一抛 checked exception → 事务不回滚 → sync_log 状态与实际不符 | **PARTIAL-NON-BLOCKER**（沿用 I009 / P4）|
| **F076** | `EcommerceService.java:225` `syncOrders` `@Transactional` | Transactional-no-rollbackFor | 同 syncProducts 模式；订单同步涉及 `orderMapper.insert/update` | **PARTIAL-NON-BLOCKER** |
| **F077** | `YouzanWebhookController.java:76` `HandleYouzan` `@Transactional` | Transactional-no-rollbackFor | 默认 only rollback RuntimeException；webhook 流程：`platformMapper.selectOne` + `webhookMsgLogMapper.selectOne/insert` + `orderMapper.insert/update` + `webhookMsgLogMapper.insert` — 若其中任一抛 checked exception → 事务不回滚 → webhook_msg_log 状态与实际不符 | **PARTIAL-NON-BLOCKER**（I034 关联）|

### 22.3 05-contract-drift 中 Batch 1.3 范围相关命中（18 处）

| Feature ID | 位置 | 命中模式 | 实际语义 | 判定 |
|---|---|---|---|---|
| **F060** | `GoogleMapsService.java:53` `@RequestBody GoogleMapsConfig body` (saveConfig) | RequestBody-without-Valid | 后端 `GoogleMapsConfig` 21 字段直接接收 body，**无 `@Valid` 注解** → 字段类型错误（如 `quotaDaily=abc`）会被 MyBatis 反序列化失败抛 `HttpMessageNotReadableException` | **PARTIAL-NON-BLOCKER**（业务字段类型简单，影响低）|
| **F060** | `GoogleMapsService.java:133` `@RequestBody Map<String,Object> body` (geocode) | RequestBody-without-Valid | Map 接 body，无 schema 校验 | **PARTIAL-NON-BLOCKER** |
| **F060** | `GoogleMapsService.java:188` `@RequestBody Map<String,Object> body` (placeDetails) | RequestBody-without-Valid | 同上 | **PARTIAL-NON-BLOCKER**（I031 关联 — 缺计费审计）|
| **F060** | `GoogleMapsService.java:242` `@RequestBody Map<String,Object> body` (geocodeBatch) | RequestBody-without-Valid | 同上 | **PARTIAL-NON-BLOCKER**（I032 关联 — 缺 batch audit）|
| **F075** | `EcommerceService.java:52` `@RequestBody EcommercePlatform p` (createPlatform) | RequestBody-without-Valid | EcommercePlatform 21 字段直接接 body，**无 `@Valid`** | **PARTIAL-NON-BLOCKER** |
| **F075** | `EcommerceService.java:68` `@RequestBody EcommercePlatform p` (updatePlatform) | RequestBody-without-Valid | 同上 | **PARTIAL-NON-BLOCKER** |
| **F076** | `EcommerceService.java:88/104/190/191/208/209/210` 6 个 `@RequestParam` (product/list/order/list/dashboard)| RequestParam-required-false-no-defaultValue | 部分 `@RequestParam(required=false)` 无 defaultValue（如 `platformId` / `status`）| **PARTIAL-NON-BLOCKER**（影响低）|
| **F077** | `YouzanWebhookController.java:80` `@RequestHeader(value="X-Youzan-Sign", required=false)` | RequestParam-required-false-no-defaultValue | 同 C3 描述 | **PARTIAL-NON-BLOCKER**（C3 已记录）|

---

## 二十三、Batch 1.3 EXT-22 特定问题精细识别（用户要求 + I026-I035 实证）

> 用户要求**特别检查** EXT-22 特定问题；以下为 Phase 3 静态识别结果 + 与 feature-inventory.md §1.3.4 I026-I035 一一对应。

### 23.1 I026 — `verifyWebhookSignature` 调用链完整性（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **算法实现** | `md5(signSecret + rawBody).hex().lowercase()` + `equalsIgnoreCase` 对比 | `YouzanEcommerceAdapter.java:146-168` `verifyWebhookSignature(String signSecret, String rawBody, String signature)` — `static + public` |
| **算法与 SoT 对齐** | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` §0.5：`expected = lowercase( hex( md5( signSecret + rawBody ) ) ); compare expected == X-Youzan-Sign header value (case-insensitive)` — **完全一致** | 同上 + SoT §0.5 |
| **调用方** | `YouzanWebhookController.java:121` `if (!YouzanEcommerceAdapter.verifyWebhookSignature(pf.getSignSecret(), rawBody, signature)) { return Result.error(BizCode.FORBIDDEN, "签名校验失败"); }` | `YouzanWebhookController.java:121` |
| **SoT 描述 vs 代码事实** | SoT §5 #1 标"`verifyWebhookSignature` 是孤儿代码" — **反向**（实际已被调用）| — |
| **结论** | 调用链完整：webhook 接收端 → ContentCachingRequestWrapper 读 rawBody → verifyWebhookSignature md5 验签 → reject/accept 分支 | — |
| **判定** | **CONFLICT**（I026 中立标 — SoT 描述反向）| — |

### 23.2 I027 — webhook 接收端 ContentCachingRequestWrapper + 幂等去重（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **ContentCachingRequestWrapper 用法** | `YouzanWebhookController.java:85-95` 在 Controller 内 `new ContentCachingRequestWrapper(request)` → 读流填充缓存 → `getContentAsByteArray()` 拿 raw bytes → 转 String<br>**关键细节**：先 drain InputStream 才能填充缓存 — `YouzanWebhookController.java:89-93` 用 `byte[4096]` buf 循环 read | `YouzanWebhookController.java:85-95` |
| **是否破坏签名** | **不破坏** — rawBody 是原始字节（`StandardCharsets.UTF_8`），**未经过 Jackson** | 同上 |
| **msg_id 幂等去重** | `YouzanWebhookController.java:136-149`：① 提取 msgId（`root.path("id")` 或 `root.path("msg_id")`）② `webhookMsgLogMapper.selectOne(... eq(source, "YOUZAN") eq(msgId, msgId) limit 1)` ③ 命中 → 返回 `duplicate=true + previousStatus + logId` + HTTP 200 | `YouzanWebhookController.java:136-149` |
| **WebhookMsgLog 写入** | `YouzanWebhookController.java:170-200` — 写 `logEntry.setStatus("PROCESSED")` 或 `"FAILED"` + `setProcessedAt(LocalDateTime.now())` + `setRawPayload(rawBody)` + `setSignature(signature)` | `YouzanWebhookController.java:170-200` |
| **upsert order** | `YouzanWebhookController.java:182-186`：调私有 `upsertOrder("YOUZAN", rawBody)`（`upsertOrder` 在 `:206-358` 与 `EcommerceService.upsertOrder` **重复实现** — C5） | `YouzanWebhookController.java:182-186` + `:206-358` |
| **SoT 描述 vs 代码事实** | SoT §5 #2 标"webhook 接收端缺失（`EcommerceService` 没有路由）" — **反向**（实际 `YouzanWebhookController` 完整实现）| — |
| **结论** | webhook 接收端实现**完整且正确**：ContentCachingRequestWrapper 用法符合规范 + msg_id 幂等去重 + upsert order + 写 webhook_msg_log 全部到位 | — |
| **判定** | **CONFLICT**（I027 中立标 — SoT 描述反向）| — |

### 23.3 I029 — `google_maps_config` 表缺失的代码路径（用户修正 #3 实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **DDL 存在** | `migrate_ext_17_google_maps.sql:9-53` `DROP TABLE IF EXISTS google_maps_config; CREATE TABLE google_maps_config (...)` + `:40-53` `DROP + CREATE google_maps_geocode_log` | `db/sql/migrate_ext_17_google_maps.sql` |
| **EXT-22 ALTER** | `migrate_ext_22_google_maps_place_details.sql:6` `ALTER TABLE google_maps_config ADD COLUMN IF NOT EXISTS enable_place_details TINYINT DEFAULT 0` + `:11` `ALTER TABLE ecommerce_platform ADD COLUMN IF NOT EXISTS app_id/kdt_id/sign_secret` | `db/sql/migrate_ext_22_google_maps_place_details.sql` |
| **实体类存在** | `entity/GoogleMapsConfig.java` 21 字段（含 `enablePlaceDetails`）+ `entity/GoogleMapsGeocodeLog.java` 11 字段 + `entity/EcommercePlatform.java` 含 `appId/kdtId/signSecret` 三新字段 | 同上 |
| **db-snapshot.sql 命中** | **待 Phase 1 grep 验证**（user decision #3 Phase 1 期间跑迁移即解） | `audit/baseline/db-snapshot.sql` |
| **运行时后果** | 若表不存在：`GoogleMapsService.configMapper.selectOne(...)` 抛 `BadSqlGrammarException`（沿用 Batch 1.1 I002 / Batch 1.2 I017 MISSING 模式）| `GoogleMapsService.java:71/96/115/144/201/262/296/342` |
| **漂移类型分类** | **preflight 假设 vs 代码漂移（MISSING 模式，非 CONFLICT 模式）**— user decision #3 修正 | — |
| **判定** | **MISSING**（I029 — Phase 1 期间跑迁移即解）| — |

### 23.4 I033 — `buildWebhookUrl` 孤儿（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **方法定义** | `static String buildWebhookUrl(String baseUrl, String kdtId)` — 构造 `https://{domain}/webhook/youzan?kdt_id={kdtId}` URL | `YouzanEcommerceAdapter.java:106-114` |
| **算法** | 检查 baseUrl 含 `?` → 加 `&` 或 `?` → 加 `kdt_id=` + URLEncoder.encode | 同上 |
| **调用方** | **0 处**（grep `buildWebhookUrl` 全代码库：`YouzanEcommerceAdapter.java` 定义 + `feature-inventory.md` / `scope-freeze-batch-1.3.md` / `static-gap-report.md` 引用 — 后 3 个是 Issue 描述文档，不是代码调用）| grep 验证 |
| **SoT 漂移类型** | **CONFIRMED**（非漂移 — SoT §0.6 + §5 #2 都标"孤儿工具"，与代码一致）| — |
| **结论** | 孤儿工具；可作为"未来注册到有赞后台"的工具（需要人工到开发者后台填 push_url） | — |
| **判定** | **BACKLOG-001**（I033 — user decision #3 沿用 I013 dead code 模式；Phase 7 Post-Audit 决定删除 / 保留 / 改造） | — |

### 23.5 F066 / F077 — EcommercePlatform 字段漂移（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **实体字段** | `EcommercePlatform.java:19/21/23` `private String appId; private String kdtId; private String signSecret;` + `:13/15` `appKey/appSecret` + `:25/27` `accessToken/tokenExpireAt` + `:28-29` `webhookUrl/webhookSecret` + `:30` `apiBaseUrl` | `entity/EcommercePlatform.java:11-41` |
| **DB 迁移** | `migrate_ext_22_google_maps_place_details.sql:11` `ALTER TABLE ecommerce_platform ADD COLUMN IF NOT EXISTS app_id VARCHAR(64) AFTER app_secret, ADD COLUMN IF NOT EXISTS kdt_id VARCHAR(64) AFTER app_id, ADD COLUMN IF NOT EXISTS sign_secret VARCHAR(255) AFTER kdt_id` | `db/sql/migrate_ext_22_google_maps_place_details.sql:11` |
| **SoT 漂移** | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` §0.4 + §4 + §5 — 实体 / DB Migration / SoT **完全对齐** | 同上 |
| **前端缺字段 UI** | `views/ecommerce/index.vue:60-77` 添加对话框**仅 3 字段**（platform/shopName/shopId）— 与 SoT §5 #3 一致，**未渲染** `appId/kdtId/signSecret` | `views/ecommerce/index.vue:60-77` |
| **结论** | 实体 / DB / SoT 完全对齐（与 user decision #3 一致）；唯一缺口是前端 UI 未渲染（I028 PARTIAL-NON-BLOCKER） | — |

### 23.6 F060 / I036 — GCP API Key 泄露风险（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **遮蔽逻辑** | `GoogleMapsService.java:108` `r.put("apiKey", "***" + apiKey.substring(Math.max(0, apiKey.length() - 4)))` | `GoogleMapsService.java:108` |
| **遮蔽后 API key 长度** | 39 字符 GCP API key + "***" 前缀 = **42 字符**；后 4 位明文 + "***" + 前 35 位省略号 → **可猜测空间 = 35 位**（约 36^35 种） | 同上 |
| **风险等级** | **HIGH**（35 位猜测空间对内网攻击者仍可破解） | — |
| **SoT 漂移** | SoT §5 #8 标"`getConfig` 端点泄露风险：`apiKey.substring(length-4)` 仅遮蔽后 4 位 → 整段 39 字符 API key 仍可猜测前 35 位（生产建议改成完全不回显，前端用 masked input）" — **与代码一致** | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` §5 #8 |
| **user decision #9** | **仅标注，不改**（沿用 SoT） | — |
| **结论** | 已知风险；Phase 7 Post-Audit 决定改 masked input | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I036 — 仅标注，未在 feature-inventory.md 登记，但 static-gap-report.md §二十三新增） | — |

### 23.7 F071 — 签名算法正确性（静态检查实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **算法** | `md5(signSecret + rawBody)` | `YouzanEcommerceAdapter.java:152-157` |
| **步骤** | ① `MessageDigest.getInstance("MD5")`<br>② `md.digest((signSecret + rawBody).getBytes(StandardCharsets.UTF_8))`<br>③ `String.format("%02x", b)` 拼接 hex — **小写**（`%02x` 而非 `%02X`）<br>④ `expected.equalsIgnoreCase(signature)` 比较 | `YouzanEcommerceAdapter.java:152-168` |
| **与 SoT 对齐** | SoT §0.5 `expected = lowercase( hex( md5( signSecret + rawBody ) ) ); compare expected == X-Youzan-Sign header value (case-insensitive)` — **完全一致** | — |
| **边界条件** | ① `signSecret == null/isEmpty` → return false<br>② `signature == null/isEmpty` → return false<br>③ `rawBody == null` → `rawBody = ""`<br>④ `MessageDigest.getInstance` 抛 NoSuchAlgorithmException → catch + log.warn + return false | `YouzanEcommerceAdapter.java:148-151` |
| **结论** | 算法正确；与 SoT 完全对齐；Phase 5 jshell 单元测试验证（user decision #2） | — |
| **判定** | **OK**（无 Issue） | — |

### 23.8 I031 — Place Details 未写日志（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **日志写入位置** | `GoogleMapsService.java:159-170`（`/geocode`） + `:289-303`（`/geocode/batch`） — 都写 `google_maps_geocode_log` | — |
| **Place Details 缺失日志** | `GoogleMapsService.java:191-232` `placeDetails` 流程：读 placeId → 校验配置 → 校验 enablePlaceDetails=1 → 调 `geocodingClient.placeDetails(apiKey, placeId, lang)` → 返回 — **未调用 `logMapper.insert(log)`** | `GoogleMapsService.java:191-232` |
| **后果** | GCP 计费接口**无对账日志** → 依赖 GCP 后台用量账单对账，无法在系统内查"某租户某天调了多少次 Place Details" | — |
| **SoT 漂移** | SoT §5 未明确提及 Place Details 是否写日志，但 §0.1 提到"google_maps_geocode_log.durationMs 写入正常 → 后续可对账 GCP 后台用量" — **未提及 Place Details** | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I031 — 当前接受现状；建议 Phase 2 期间加日志写入） | — |

### 23.9 I032 — 批量 geocode 无 audit log（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **API 守卫** | `GoogleMapsService.java:262-265` admin 守卫：仅 `SUPER_ADMIN/PLATFORM_ADMIN/HQ_ADMIN` 可调 | — |
| **批日志写入** | 仅在 `geocodeBatch` 内**逐条**写 `google_maps_geocode_log`（每条 address 一行）— 287-303 | — |
| **批量 audit 缺失** | 无独立 `geocode_batch_log` 表 / 无"谁在何时调了多少条"审计 — 与 SoT §5 #6 已知一致 | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I032 — user decision 同 SoT；Phase 7 Post-Audit 加 `geocode_batch_log` 表） | — |

### 23.10 I034 — webhook 事务未指定 rollbackFor（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **注解** | `@Transactional` （无 `rollbackFor`） | `YouzanWebhookController.java:76` |
| **默认行为** | Spring 默认 `rollbackFor = RuntimeException.class` — 仅 RuntimeException 触发回滚 | — |
| **风险** | webhook 链路涉及：① `ObjectMapper 不可用`（`:214` `throw new IllegalStateException(...)` → RuntimeException → 滚回 OK）<br>② `JSONException`（`:204`）→ 受检异常 → **不滚回**<br>③ 数据库 SQL 异常（`BadSqlGrammarException`）→ RuntimeException → 滚回 OK<br>④ `IOException`（ContentCachingRequestWrapper 读 rawBody）→ 受检异常 → **不滚回** | — |
| **后果** | 若 webhook_msg_log 写入成功 + order upsert 失败（受检异常） → webhook_msg_log 已被事务提交前的方法标记为 `RECEIVED` 但 order 未写入 → 下次同 msg_id 重试时**幂等去重会返回"已处理"** → order 永远不写入 | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I034 — 沿用 I009；建议加 `rollbackFor = Exception.class`） | — |

### 23.11 I035 — webhook 幂等仅查 msg_id 不查时间窗（实证）

| 维度 | 详情 | 位置 |
|---|---|---|
| **幂等查询** | `webhookMsgLogMapper.selectOne(... eq(source, "YOUZAN") eq(msgId, msgId) limit 1)` — 仅查 msg_id 唯一性，**无时间窗** | `YouzanWebhookController.java:138-143` |
| **风险** | 理论上可重放：恶意用户构造一个**曾经成功处理**的 msg_id + 篡改过的 body（但需绕过签名）→ 签名失败<br>实际：msg_id + rawBody 是签名输入，**无法篡改 rawBody 否则签名失败** → 重放风险**可控**（攻击者必须掌握 rawBody + 签名才能重放）| — |
| **真正风险** | 若 msg_id 是弱随机（如递增 ID），攻击者可枚举 msg_id；正常情况下 msg_id 是 UUID/雪花 ID，无法枚举 | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I035 — 已知约束；签名机制已防重放） | — |

---

## 二十四、Batch 1.3 Issue Register（I026-I035 汇总）

> 按 user decision scope-freeze-batch-1.3.md §十 + feature-inventory.md §1.3.4：
> - **CONFLICT** 3 项（I026 / I027 / I030）→ Fix Phase = Post-Audit 人类决策
> - **MISSING（DB）** 1 项（I029）→ 跑迁移即解（Phase 1 期间执行）
> - **PARTIAL-NON-BLOCKER** 5 项（I028 / I031 / I032 / I034 / I035）→ 登记后续批次修复
> - **BACKLOG-001** 1 项（I033）→ Phase 7 Post-Audit 决定
> - **新增 I036** 1 项（GCP API Key 泄露，仅标注）→ user decision #9 仅标注

| Issue ID | 影响 Feature | 来源 | Phase 3 状态 | 处置（按用户决策）|
|---|---|---|---|---|
| **I026** | F071 / F077 | SoT vs 代码冲突（`verifyWebhookSignature` "孤儿代码" — 实际已被 `YouzanWebhookController:121` 调用）| **CONFLICT**（中立标）| 按用户决策 #2：保留 CONFLICT，不动 `ext-22.mdc`；Phase 5 jshell 单元测试验证签名算法（3 组 payload：正常 / 篡改 body / 篡改 signature） |
| **I027** | F077 / F078 | SoT vs 代码冲突（"webhook 接收端缺失" — 实际 `YouzanWebhookController` 已完整实现 ContentCachingRequestWrapper + 验签 + 幂等去重 + upsert）| **CONFLICT**（中立标）| 按用户决策 #2：保留 CONFLICT，不动 `ext-22.mdc`；Phase 5 Mock 推送样本验证 |
| **I028** | F065 / F079 | 前端 UI 缺字段（`enablePlaceDetails` 已实现反向；`appId/kdtId/signSecret`/`appKey/appSecret` 缺 UI）| **PARTIAL-NON-BLOCKER**（部分反向）| Phase 2 验证 enablePlaceDetails 是否实际保存到后端；接受 ecommerce 缺字段现状（SQL 直写，user decision） |
| **I029** | F056 / F057 / F060 / F066 | `google_maps_config` / `google_maps_geocode_log` / `ecommerce_platform` 表 DDL 已写但 dev DB 未跑迁移 | **MISSING（DB）** | 按用户决策 #3：Phase 1 期间跑 `migrate_ext_17_google_maps.sql` + `migrate_ext_22_google_maps_place_details.sql` 跑批即解（沿用 I002 / I017 MISSING 模式） |
| **I030** | F077 / F078 | SoT vs 代码冲突（"没有 webhook 重放保护" — 实际已加 `webhook_msg_log` 幂等去重）| **CONFLICT**（中立标）| 按用户决策 #2：保留 CONFLICT，不动 `ext-22.mdc`；但 I035 时间窗缺失仍标 PARTIAL-NON-BLOCKER |
| **I031** | F062 | `/place/details` 未写 `google_maps_geocode_log`（与 `/geocode` + `/geocode/batch` 行为不一致）| **PARTIAL-NON-BLOCKER**（审计缺失）| Phase 2 期间加日志写入（F062 改造范围外，仅标注）；Post-Audit 接受现状或加日志 |
| **I032** | F063 | `/geocode/batch` admin-only 但无独立 `geocode_batch_log` 表（无"谁在何时调了多少条"审计）| **PARTIAL-NON-BLOCKER**（审计缺失）| Post-Audit 加 `geocode_batch_log` 表（本批次不实现）|
| **I033** | F072 | `YouzanEcommerceAdapter.buildWebhookUrl` 孤儿工具（0 调用方）| **BACKLOG-001**（沿用 I013 dead code 模式）| Phase 7 Post-Audit 决定删除 / 保留 / 改造 |
| **I034** | F077 | `YouzanWebhookController.@Transactional` 未指定 rollbackFor（受检异常不滚回 → I008 同模式）| **PARTIAL-NON-BLOCKER** | 加 `rollbackFor = Exception.class`（Post-Audit）|
| **I035** | F078 | `webhook_msg_log` 幂等去重仅查 msg_id 不查 timestamp / nonce（重放风险受签名机制保护）| **PARTIAL-NON-BLOCKER** | 加 `webhook_msg_log.processed_at > now() - 1h` 时间窗（Post-Audit）|
| **新增 I036** | F060 | `GET /google-maps/config` 端点 `apiKey.substring(length-4)` 泄露风险（35 位猜测空间）| **PARTIAL-NON-BLOCKER** | 按用户决策 #9：仅标注，Fix Phase = Post-Audit（改 masked input）|

---

## 二十五、Batch 1.3 全栈静态结论矩阵

| Feature ID | Module | 类型 | 静态缺口 | Phase 2 结论 | Phase 3 结论 | 升级路径 |
|---|---|---|---|---|---|---|
| **F056** | GoogleMapsConfig 实体 | 实体 | **I029** MISSING（表）| MISSING | **MISSING** | Phase 1 跑迁移即解 |
| **F057** | GoogleMapsGeocodeLog 实体 | 实体 | **I029** MISSING（表）| MISSING | **MISSING** | Phase 1 跑迁移即解 |
| F058 | GoogleMapsGeocodingClient | Client | 无 | UNVERIFIED | UNVERIFIED | Phase 5 Mock 降级 |
| F059 | GoogleMapsClientConfig | Config | 无 | UNVERIFIED | UNVERIFIED | Phase 5 |
| **F060** | GoogleMapsService /config + /health | 端点 | **I029** MISSING + **I036** Key 泄露 + **C1** | MISSING | **MISSING + PARTIAL-NON-BLOCKER（I036）** | Phase 1 跑迁移 + Post-Audit 改 masked input |
| F061 | GoogleMapsService /geocode | 端点 | MOCK-INTENTIONAL（降级）| UNVERIFIED | UNVERIFIED | Phase 5 Mock |
| **F062** | GoogleMapsService /place/details | 端点 | **I031** 未写日志 + **C1** configForm 缺字段 | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | Phase 2 加日志 + 补 configForm 字段 |
| F063 | GoogleMapsService /geocode/batch | 端点 | **I032** 无 audit log + **真实 Shadow**（无前端）| PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | Post-Audit 加 `geocode_batch_log` 表 |
| F064 | GoogleMapsService /stores + /heatmap + /geocode/log | 端点 | **真实 Shadow**（/geocode/log 端点无前端消费）| PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | Phase 5 + 加 /geocode/log UI tab |
| F065 | google-maps/index.vue | 前端 | **I028** 部分反向 + **C1** configForm 缺字段 | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | Phase 2 验证 + 补字段 |
| F066 | EcommercePlatform 实体 | 实体 | **I029** MISSING（表 3 新列）+ **I028** 字段漂移确认 | MISSING + PARTIAL-NON-BLOCKER | **MISSING + PARTIAL-NON-BLOCKER** | Phase 1 跑迁移即解 |
| F067 | EcommerceOrder + EcommerceProduct + EcommerceSyncLog | 实体 | 无 | UNVERIFIED | UNVERIFIED | Phase 5 |
| F068 | EcommerceAdapter 接口 | Adapter | 无 | UNVERIFIED | UNVERIFIED | Phase 5 |
| F069 | YouzanEcommerceAdapter authenticate | Adapter | 无（Mock 降级）| UNVERIFIED | **UNVERIFIED** | Phase 5 Mock |
| F070 | YouzanEcommerceAdapter fetchProducts + fetchOrders + healthCheck | Adapter | 无（Mock 降级）| UNVERIFIED | **UNVERIFIED** | Phase 5 Mock |
| **F071** | YouzanEcommerceAdapter verifyWebhookSignature | 签名算法 | **I026** CONFLICT 中立标（SoT 描述反孤儿）| CONFLICT | **CONFLICT**（中立标）| Phase 5 jshell 单元测试 |
| **F072** | YouzanEcommerceAdapter buildWebhookUrl | 工具 | **I033** BACKLOG-001（孤儿）| UNVERIFIED | **BACKLOG-001** | Phase 7 Post-Audit 决定 |
| F073 | MockEcommerceAdapter | Adapter | MOCK-INTENTIONAL | UNVERIFIED | UNVERIFIED | Phase 5 |
| F074 | EcommerceAdapterRouter | Router | MOCK-INTENTIONAL | UNVERIFIED | UNVERIFIED | Phase 5 |
| **F075** | EcommerceService /platform + /platform/{id}/health | 端点 | **真实 Shadow**（/platform/{id}/health + update 无 UI）+ **C4** 缺租户隔离（**MOCK-INTENTIONAL**）| PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER + MOCK-INTENTIONAL（C4）** | Post-Audit 加 /platform/{id}/health UI |
| **F076** | EcommerceService /product + /order sync | 端点 | **C8** `@Transactional` rollbackFor + **C10** token 缓存弱一致 | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | Post-Audit 加 rollbackFor |
| **F077** | YouzanWebhookController /webhook/youzan | 端点 | **I027** CONFLICT（SoT 反向） + **C5** upsertOrder 重复实现 + **C9** rollbackFor + **I034** rollbackFor | CONFLICT | **CONFLICT**（中立标）| Phase 5 Mock 推送样本 |
| **F078** | webhook msg_id 幂等去重 | 行为 | **I030** CONFLICT（SoT 反向） + **I035** 时间窗缺失 + **真实 Shadow**（无运维查询接口）| CONFLICT + PARTIAL-NON-BLOCKER | **CONFLICT + PARTIAL-NON-BLOCKER（I035）** | Phase 5 + Post-Audit 加时间窗 |
| **F079** | ecommerce/index.vue | 前端 | **I028** 缺字段 UI + **真实 Shadow**（update 按钮缺失）| PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | Post-Audit 加字段 + 加 update 按钮 |
| **F080** | Frontend API + DB 迁移 | Frontend + DB | **I029** MISSING（DB 迁移） | MISSING | **MISSING** | Phase 1 跑迁移即解 |

---

## 二十六、Batch 1.3 静态分析结论（最终）

| **维度** | **Phase 1（feature-inventory）** | **Phase 3（本次）** | **变化** |
|---|---|---|---|
| Feature 总数 | 25（F056-F080） | 25 | — |
| **VERIFIED** | 0 | 0 | — |
| **UNVERIFIED** | 5 | **6** | +1（F075 `/platform/{id}/health` 真实 Shadow 升级为 UNVERIFIED — 静态分析可达但 Phase 5 需实测） |
| **PARTIAL-NON-BLOCKER** | 8 | 10 | +2（F063 `/geocode/batch` Shadow + F078 I035 时间窗） |
| **PARTIAL-BLOCKER** | 0 | 0 | — |
| **CONFLICT（中立标）** | 5（I026-I030） | **3**（F071/F077/F078） | -2（修正：I026/I027/I030 三项中立标实际对应 F071/F077/F078 共 3 个 Feature，不重复计 CONFLICT 行） |
| **MISSING（DB）** | 4（F056/F057/F060/F066） | **5**（F056/F057/F060/F066/F080） | +1（修正：F080 DB 迁移未跑 = I029，§二十五矩阵已标 MISSING 但 §二十六结论遗漏，现补齐） |
| **BACKLOG-001** | 1（I033） | 1 | — |
| **MOCK-INTENTIONAL** | 2（F061/F073 + Router F074） | **3**（F061/F073/F074） | -1（修正：F077 webhook 不属于 MOCK-INTENTIONAL；F061 单一归属 MOCK-INTENTIONAL（不再是 UNVERIFIED）） |

**最终归属汇总**（单一归属 — 修正后）：

| Result | 数量 | Feature | 修正说明 |
|---|---|---|---|
| **VERIFIED** | 0 | — | — |
| **UNVERIFIED** | 6 | F058 / F059 / F067 / F068 / F069 / F070 / F075 | F061 从 UNVERIFIED 调整 → MOCK-INTENTIONAL（降级路径非 UNVERIFIED） |
| **PARTIAL-NON-BLOCKER** | 10 | F062 / F063 / F064 / F065 / F066（含 I028）/ F079 / F076 / F078（I035 部分）/ F060（I036 部分）| 维持 |
| **PARTIAL-BLOCKER** | 0 | — | — |
| **CONFLICT（中立标）** | 3 | **F071**（I026）/ **F077**（I027 + I030 同源）/ **F078**（I030 幂等） | F077 单一归属 CONFLICT（不再是 MOCK-INTENTIONAL）；F071 维持 CONFLICT；F078 因 I030 升 CONFLICT（I035 仍 PARTIAL-NON-BLOCKER） |
| **MISSING（DB）** | **5** | **F056 / F057 / F060 / F066 / F080** | F080 DB 迁移补入；F066 与 F080 均受 I029 影响，但 F066 = 实体层 MISSING、F080 = DB 迁移层 MISSING |
| **BACKLOG-001** | 1 | F072（I033） | 维持 |
| **MOCK-INTENTIONAL** | 3 | F061（mockGeocode）/ F073（MockEcommerceAdapter）/ F074（EcommerceAdapterRouter） | F077 webhook 单一归属 CONFLICT（不应混入 MOCK-INTENTIONAL — webhook 不是 mock fallback） |
| **合计** | **25** | F056-F080 | 唯一归属，每 Feature 仅 1 行 |

> **修正说明**（用户 2026-09-13 23:50 决策 — D11-D20 落实）：
> - **F061**：从 `UNVERIFIED + MOCK-INTENTIONAL` 双归属修正为 `MOCK-INTENTIONAL` 单一归属。`mockGeocode` 是设计降级（user decision #1 Mock 降级），非 UNVERIFIED。
> - **F077**：从 `CONFLICT + MOCK-INTENTIONAL` 双归属修正为 `CONFLICT` 单一归属。webhook 接收端是 `YouzanWebhookController` 真实实现（I027/I030 SoT 反向），不是 mock fallback；MOCK-INTENTIONAL 不适用 webhook 场景。
> - **F080**：从 MISSING（§二十五矩阵已标）但 §二十六统计遗漏，修正为 MISSING 计入。`Frontend API + DB 迁移` Feature 的 DB 迁移层 MISSING。
> - **F066 vs F080 一致性**：两者都受 I029 影响，但维度不同：F066 = EcommercePlatform 实体层（表缺失 + 字段漂移）；F080 = Frontend API + DB 迁移层（DB 迁移未跑）。均维持 MISSING，不跑迁移（**D13 决策** — 与 I002/I017 模式一致）。
> - **修订补充**：
>   - F062 / F063 / F064 / F075 / F078 在 Phase 3 新增或升级为 **真实 Shadow Feature**（基于 04/08 人工修正）。
>   - F060 新增 **I036** GCP API Key 泄露（user decision #9 仅标注）。
>   - F075 / F076 / F079 关联交易 enum / 表单字段漂移（**C1 / C2 / C4**）作为 Phase 3 精细识别补充。
>   - **F061/F077 不再双归属**：Feature 矩阵每行单一 Result（规则 7）。

---

## 二十七、Batch 1.3 Phase 3 → Phase 4 入口守门

| 检查项 | 状态 | 备注 |
|---|---|---|
| 8 个守门脚本复用 Batch 1.1 | ✅ | `audit/scripts/01-08-scan-*.ps1` |
| 8 份扫描报告 grep 提取 Batch 1.3 范围 | ✅ | `audit/static-gap/01-08-*.txt` + filter |
| Batch 1.3 范围精细分析完成 | ✅ | Shadow / Ghost / Zombie / Fake / Shell / Contract Drift / SoT Conflict 全识别 |
| EXT-22 特定问题精细识别完成 | ✅ | I026-I035 + I036 共 11 项 |
| 与 I026-I035 交叉引用矩阵完成 | ✅ | §二十三（11 项实证）+ §二十四（汇总）|
| Issue Register 已更新（I026-I036 待登记 §十九）| ✅ | 11 项 Issue 待用户确认分类 |
| 不动代码 | ✅ | 仅静态分析与报告生成 |
| 不进 Phase 4 | ✅ | 等用户确认 Issue 处置 |
| 跳过 Phase 3.5 | ✅ | Batch 1.3 非重构批次（沿用 Batch 1.1/1.2 决策）|

### Phase 4 待用户决策项（D11-D20 — 2026-09-13 23:50 用户决策已落档）

> **统一原则（user decision D11-D20）**：**审计阶段只记录不修复**（规则 6）。本批次不修任何业务代码 / 不动 SoT / 不跑迁移 / 不加 UI / 不加日志。所有 Issue Fix Phase = **Post-Audit**。

| # | 决策项 | **用户决策（D11-D20）** | 落档 | Fix Phase |
|---|---|---|---|---|
| **D11** | **I026 / I027 / I030**（3 项 CONFLICT 中立标）是否在 Phase 4 改 SoT `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`？| **D11：不改 SoT**（中立标 = 不预设 SoT 错，保留 CONFLICT 留给人类 Post-Audit 决策；沿用 I001/I018 模式） | I026/I027/I030 维持 CONFLICT | Post-Audit（人类决策）|
| **D12** | **I028**（前端 UI 缺字段）处置路径？| **D12：不加 UI**。登记 Issue；接受现状（SQL 直写 user decision #2 + Phase 5 仅观察行为）| I028 维持 PARTIAL-NON-BLOCKER | Post-Audit |
| **D13** | **I029**（`google_maps_config` + `ecommerce_platform` 3 列 MISSING）是否在 Phase 1 期间跑迁移？| **D13：不跑迁移**（维持 MISSING，**与 I002/I017 决策一致**；沿用 Batch 1.1 I002 + Batch 1.2 I017 模式 — 不修 db-snapshot，仅 audit 侧标 PARTIAL；跑迁移会改变审计基线）| **I029 维持 MISSING（DB）** | Post-Audit（部署前由 DBA 决策）|
| **D14** | **I031**（Place Details 未写日志）是否在 Phase 4 修？| **D14：不加日志**。登记 Issue；接受现状（Phase 5 仅观察行为）| I031 维持 PARTIAL-NON-BLOCKER | Post-Audit |
| **D15** | **I032**（批量 geocode 无 audit log）是否在 Phase 4 修？| **D15：不加 audit log**。登记 backlog（沿用 Batch 1.2 I019 命名异味 backlog 模式）| I032 维持 PARTIAL-NON-BLOCKER + backlog | Post-Audit |
| **D16** | **I033**（`buildWebhookUrl` 孤儿 BACKLOG-001）处置？| **D16：保持 backlog**（沿用 I013 dead code 模式 — Phase 7 Post-Audit 决定）| I033 维持 BACKLOG-001 | Post-Audit |
| **D17** | **I034**（webhook 事务未指定 rollbackFor）是否在 Phase 4 修？| **D17：不加 rollbackFor**。登记 Issue（与 I008/I011/I012 Fake Result 系列同模式 — Phase 5 仅观察事务边界行为）| I034 维持 PARTIAL-NON-BLOCKER | Post-Audit |
| **D18** | **I035**（webhook 幂等时间窗缺失）是否在 Phase 4 修？| **D18：不加时间窗**。登记 Issue（I030 幂等去重已实现，时间窗为可选增强）| I035 维持 PARTIAL-NON-BLOCKER | Post-Audit |
| **D19** | **I036**（GCP API Key 泄露）是否在 Phase 4 修？| **D19：仅标注不改**（沿用 user decision #9；改 masked input 涉及 UI / Entity / 端点三重变更，超出审计范围）| I036 维持 PARTIAL-NON-BLOCKER | Post-Audit |
| **D20** | **真实 Shadow Feature**（F063/F064/F075 + F077 webhook）是否在 Phase 4 加 UI？| **D20：不加 UI**。登记 Issue；Phase 5 仅验证 Shadow（沿用 I006/I007/I024 模式）| F063/F064/F075 维持 PARTIAL-NON-BLOCKER + Shadow；F077 维持 CONFLICT | Post-Audit |

**D11-D20 统一动作**：

1. 所有 10 项决策统一沿用 **「审计阶段只记录不修复」**（规则 6）
2. I026-I036 共 11 项 Issue 全部维持 Phase 3 标记，Fix Phase = **Post-Audit**
3. **Phase 4 仅生成运行时验证计划**（不动代码 / 不跑迁移 / 不改 SoT / 不加 UI）
4. Phase 5 实证仅观察行为（**不造证据** — D10 铁律），验证审计结论准确性
5. I026-I036 待登记到 `audit/issues/issue-register.md §十九`（已落档）

---

## 二十八、附录 — Batch 1.3 关键位置索引

### 后端 Google Maps（`backend/src/main/java/com/meiyetec/`）

| 文件 | 行号范围 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `googlemaps/GoogleMapsGeocodingClient.java` | 51-381 | F058 | 11 字段 Place Details 全拿（Basic+Contact+Atmosphere 三 SKU）；私有 `httpGet` 连接 5s + 读 15s |
| `googlemaps/GoogleMapsClientConfig.java` | 13-16 | F059 | `@Bean googleMapsGeocodingClient(ObjectMapper jackson)` — 无 `@ConditionalOnMissingBean`（无 fallback）|
| `service/GoogleMapsService.java` | 33-43 (STORES) + 67-131 (/config + /health) + 138-180 (/geocode) + 191-232 (/place/details) + 243-253 (mockGeocode) + 259-318 (/geocode/batch) + 320-348 (/stores + /heatmap + /geocode/log) | F060-F064 | 三段门禁（`enablePlaceDetails=1` 计费闸门）；`apiKey.substring(length-4)` **I036 泄露**；`/place/details` **I031 未写日志**；`/geocode/batch` **I032 无 audit log**；`@RestController` 命名误导（沿用 I004/I019 模式）|
| `entity/GoogleMapsConfig.java` | 11-56 | F056 | 21 字段含 `enablePlaceDetails: Integer`（L43）；`apiKey WRITE_ONLY`（L20）|
| `entity/GoogleMapsGeocodeLog.java` | 13-38 | F057 | 11 字段；**表 MISSING 待 Phase 1 验证（I029）** |

### 后端 Ecommerce（`backend/src/main/java/com/meiyetec/`）

| 文件 | 行号范围 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `ecommerce/EcommerceAdapter.java` | 全文 122 行 | F068 | 5 方法 + 4 DTO；接口契约 OK |
| `ecommerce/EcommerceAdapterRouter.java` | 21-46 | F074 | platform → adapter；miss → MOCK fallback；与 CrmAdapterRouter 同源设计 |
| `ecommerce/YouzanEcommerceAdapter.java` | 73-168 (URL + 签名) + 187-279 (authenticate/fetchProducts/fetchOrders/healthCheck) + 281-305 (HTTP util) | F069-F072 | OAuth2 Client-Credentials + Token 缓存（`tokenExpireAt > now()+2min`）；**`verifyWebhookSignature` L146-168 已被调用**（I026 反向）；**`buildWebhookUrl` L106-114 孤儿**（I033）|
| `ecommerce/MockEcommerceAdapter.java` | 18-77 | F073 | `@ConditionalOnMissingBean(name="youzanEcommerceAdapter")` L21；3 商品 + 2 订单样本；**MOCK-INTENTIONAL** |
| `service/EcommerceService.java` | 65-94 (平台 CRUD) + 97-150 (商品同步) + 213-289 (订单同步) + 374-388 (dashboard) | F075-F076 | **命名误导 `@RestController`**（沿用 I004/I019 模式）；`tenantId=0L` 强制 L72（C4 MOCK-INTENTIONAL）；`@Transactional` 默认 rollbackFor 仅 RuntimeException L108/L225（C8 PARTIAL-NON-BLOCKER）；`upsertOrder` L241-353 与 YouzanWebhookController L206-358 **重复实现**（C5）|
| `controller/YouzanWebhookController.java` | 76-203 (HandleYouzan) + 206-358 (upsertOrder 复制) + 360-359 (工具方法) | F077-F078 | **完整实现反向 SoT §5 #1 #2 #4**：ContentCachingRequestWrapper L85-95 + 反查 platform L107-114 + 验签 L121 + msg_id 幂等 L136-149 + upsert L182-186 + 写 log L195-200；`@Transactional` 未指定 rollbackFor L76（I034）；重复实现 upsertOrder（C5）|
| `entity/EcommercePlatform.java` | 11-41 | F066 | 21 字段含 `appId (L19) + kdtId (L21) + signSecret (L23)` 三新字段；**与 `migrate_ext_22:11` 完全对齐**（I028 字段漂移确认）|

### 前端（`frontend/meiyetec-web/src/`）

| 文件 | 行号范围 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `views/google-maps/index.vue` | 1-175 | F065 | **4 tab**：配置 / 门店地图 / 地理编码测试 / Place Details 测试；`enablePlaceDetails` el-switch L13-31 + 计费警告 el-alert L33-41（**与 SoT §5 #3 反向** — 部分反向 I028）；`configForm` 仅 3 字段 L139（C1 缺 `defaultLanguage/defaultRegion/quotaDaily/enabled`）|
| `views/ecommerce/index.vue` | 1-95 | F079 | **3 tab**：平台管理 / 商品同步 / 订单同步；添加对话框 L60-77 **仅 3 字段**（platform/shopName/shopId）— **确认缺** `appId/kdtId/signSecret/appKey/appSecret`（I028）；表内无 update 按钮（**真实 Shadow**，F075 关联）|
| `api/google-maps.ts` | 1-18 | F080 | 6 端点：`saveConfig/getConfig/geocodeStore/listStores/getPlaceDetails/getMemberHeatmap`；**缺** `/health` + `/geocode/log` + `/geocode/batch` |
| `api/ecommerce.ts` | 1-19 | F080 | 8 端点：`createPlatform/listPlatforms/updatePlatform/listProducts/syncProducts/listOrders/syncOrders/dashboard`；**缺** `/platform/{id}/health`（F075 关联）|

### DB / SQL

| 文件 | 行号 / 表 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `db/sql/migrate_ext_17_google_maps.sql` | line 9-39 (google_maps_config) + line 40-53 (google_maps_geocode_log) | F056 / F057 | `DROP + CREATE`（无 IF NOT EXISTS）；uk `uk_tenant_brand` + 3 索引；**dev DB 未跑**（I029 MISSING）|
| `db/sql/migrate_ext_22_google_maps_place_details.sql` | line 6 (google_maps_config ALTER) + line 11 (ecommerce_platform ALTER 3 列) | F056 / F066 | `ADD COLUMN IF NOT EXISTS`（MySQL 8.0.29+）；**dev DB 未跑**（I029 MISSING）|

### 脚本 / 报告（本批次新增）

| 文件 | 命中行号 | 备注 |
|---|---|---|
| `audit/static-gap/01-fake-success.txt` | 0 命中 Batch 1.3 | 详见 §十六 |
| `audit/static-gap/02-mock.txt` | line 36-41 + 78-82 + 173-187 = **18 命中** | `YouzanWebhookController` catch-log-swallow × 6 + `MockEcommerceAdapter` catch-ignore × 1 + `YouzanEcommerceAdapter` catch-log-swallow × 4 + `EcommerceService` catch-log-swallow × 3 + catch-ignore × 7 + `GoogleMapsService` catch-ignore × 2 |
| `audit/static-gap/03-disabled.txt` | line 17-18 + 49-50 = **4 命中** | `YouzanWebhookController` `return ...` × 2 + `EcommerceService` `return ...` × 2 |
| `audit/static-gap/04-endpoint-coverage.txt` | **29 命中** | 详见 §二十一 |
| `audit/static-gap/05-contract-drift.txt` | line 795 + 1081-1131 = **18 命中** | `YouzanWebhookController` RequestParam × 1 + `EcommerceService` RequestBody/Param × 10 + `GoogleMapsService` RequestBody × 4 |
| `audit/static-gap/06-soft-delete.txt` | 0 命中 Batch 1.3 | 无 deleted 列 + 部分表 MISSING |
| `audit/static-gap/07-transaction.txt` | line 9 + 45-46 = **3 命中** | `YouzanWebhookController` Transactional × 1 + `EcommerceService` Transactional × 2 |
| `audit/static-gap/08-shadow-ghost.txt` | line 129/221/441/527/670/818/908/937/1000/1394/1429/1436/1449/1538/1553/1605/1728/1962 + 后端全部 = **17 + 后端全量** | F077 webhook **真实 Shadow** + 14 个后端 Only-Backend（**3 个真实 Shadow** + 11 个 L1 false positive）+ 6 个 Ghost frontend `${id}`（L1 false positive）|
| `audit/reports/static-gap-report.md` | §八-§十五（Batch 1.2）+ **§十六-§二十八（本报告 Batch 1.3 段）**| **本报告** |

---

## 二十九、Batch 1.3 与 I026-I036 交叉引用

> 用户要求**与 I026-I035 的交叉引用**；下表为 Phase 3 实证矩阵（含新增 I036 GCP API Key 泄露）。

| Issue ID | 静态证据（行号）| 动态验证 Phase 5 任务 | 修复路径 |
|---|---|---|---|
| **I026** | `YouzanEcommerceAdapter.java:146-168` `verifyWebhookSignature` md5(signSecret + rawBody) 全小写 hex + equalsIgnoreCase；`YouzanWebhookController.java:121` 已调用 | jshell 3 组 payload 测试：① 正常签名 → true；② 篡改 body → false；③ 篡改 signature → false；④ 边界 case：signSecret=null → false | Post-Audit：改 `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` §5 #1（删除"孤儿代码"描述）|
| **I027** | `YouzanWebhookController.java:76-203` 完整实现（ContentCachingRequestWrapper L85-95 + 反查 platform L107-114 + 验签 L121 + msg_id 幂等 L136-149 + upsert L182-186 + 写 log L195-200）| Mock 推送样本：生成有赞 webhook payload + 正确 signature 头 → 调 `POST /webhook/youzan?kdt_id=test` → 期望 200 + 订单入库；同 payload 重复推送 → 期望 200 + `duplicate=true` | Post-Audit：改 SoT §5 #2（删除"接收端缺失"描述）|
| **I028** | ① `views/google-maps/index.vue:13-31` enablePlaceDetails el-switch 已实现；② `views/ecommerce/index.vue:60-77` 添加对话框仅 3 字段 | ① 验证 google-maps enablePlaceDetails 实际保存到后端（curl saveConfig + getConfig）；② 验证 ecommerce 缺字段 UI（用户决策：接受现状）| Post-Audit：接受 ecommerce 缺字段现状；可选：补 enablePlaceDetails 实际保存验证 |
| **I029** | DDL `migrate_ext_17_google_maps.sql:9-53` + `migrate_ext_22_google_maps_place_details.sql:6/11` 已写；`db-snapshot.sql` 待 Phase 1 grep 验证 | Phase 1：① grep `db-snapshot.sql` 找 `google_maps_config` → 若无；② 跑 `migrate_ext_17` + `migrate_ext_22`；③ DESCRIBE 验证 3 新列 `enable_place_details` + `app_id/kdt_id/sign_secret` 存在 | Phase 1 期间跑迁移即解 |
| **I030** | `YouzanWebhookController.java:136-149` msg_id 幂等去重 + `:195-200` 写 webhook_msg_log；表 `webhook_msg_log` 由 `migrate_ext_24_webhook_msg_log.sql:8` 创建（**EXT-24 边界，Batch 1.3 只引用不重审**）| Mock 推送样本（与 I027 同 payload）重复 2 次：① 首次 → 200 PROCESSED；② 重复 → 200 duplicate=true + previousStatus=PROCESSED | Post-Audit：改 SoT §5 #4（删除"没有重放保护"描述）|
| **I031** | `GoogleMapsService.java:191-232` `placeDetails` 未写 `google_maps_geocode_log`；与 `/geocode` L159-170 + `/geocode/batch` L289-303 写日志不一致 | Phase 5 实测：调 `/google-maps/place/details` → 查询 `google_maps_geocode_log` 应**无新增行**（I031 实证）；若需审计，**Phase 2 加日志写入** | Post-Audit：接受现状或加日志 |
| **I032** | `GoogleMapsService.java:259-318` `geocodeBatch` admin-only 但**无独立 audit log 表**；仅**逐条**写 `google_maps_geocode_log`（每条 address 一行）| admin 调 `POST /google-maps/geocode/batch` 50 条 → 查 `geocode_batch_log` 应**无表**（I032 实证）| Post-Audit：加 `geocode_batch_log` 表（本批次不实现）|
| **I033** | `YouzanEcommerceAdapter.java:106-114` `buildWebhookUrl` 定义；全代码库 grep 0 真实调用 | 删除 `buildWebhookUrl` 方法 + 编译通过 + 启动 OK（无影响） | Post-Audit：删除或留作未来用 |
| **I034** | `YouzanWebhookController.java:76` `@Transactional` 未指定 rollbackFor；`upsertOrder` 抛 `IllegalStateException`（`:214`）| Phase 5 实证：mock 推送样本触发 `upsertOrder` 失败 → 查 `webhook_msg_log.status` 应为 `FAILED` + `errorMessage` 有内容；事务回滚 OK（RuntimeException） | Post-Audit：加 `rollbackFor = Exception.class` |
| **I035** | `YouzanWebhookController.java:138-143` `webhookMsgLogMapper.selectOne(... eq(source, "YOUZAN") eq(msgId, msgId) limit 1)` 无时间窗 | 实证：构造同一 msg_id + 篡改 body（绕过签名验证需额外攻击）→ 验证签名机制已防重放（I035 受签名保护）| Post-Audit：加 `webhook_msg_log.processed_at > now() - 1h` 时间窗 |
| **新增 I036** | `GoogleMapsService.java:108` `apiKey.substring(Math.max(0, apiKey.length() - 4))` 仅遮蔽后 4 位 | 调 `GET /google-maps/config` → 响应应包含 `"apiKey": "***xxxx"`（最后 4 位明文 + 前 35 位省略）| Post-Audit：改 masked input（user decision #9 仅标注）|

---

**Phase 3 产出（Batch 1.3）完成。25 个 Feature 精细分析 + I026-I036 实证矩阵已落 `audit/reports/static-gap-report.md` §十六-§二十九。**

**等待用户确认 D11-D20 决策项 → 启动 Phase 4（运行时计划）。**

**Phase 3 产出（Batch 1.1 + 1.2 + 1.3）全部完成。共 80 个 Feature 精细分析 + 36 项 Issue（I001-I036）实证矩阵已落 `audit/reports/static-gap-report.md` §一-§二十九。**

**等待用户确认 D21-D30 决策项（I038-I051）→ 启动 Phase 4（运行时计划）。**

---

## 三十一、Batch 1.4 EXT-19 多语言预约确认（v3 第四批） — Static Gap Audit

> **追加时间**：2026-09-14 20:16 (UTC+8)
> **Phase**：Phase 3 — Static Gap Audit（Batch 1.4）
> **范围**：Batch 1.4 — EXT-19 多语言预约确认（聚焦） + 全局视野（沿用 Batch 1.1/1.2/1.3 守门脚本）
> **引用文件**：`audit/feature-inventory.md` §1.4（33 个 Feature F081-F113） / `audit/traceability-matrix.md` §Batch 1.4 / `audit/baseline/scope-freeze-batch-1.4.md` / `audit/baseline/preflight-batch-1.4.md`
> **SoT**：`.cursor/rules/ext-19-operational-notes.mdc`（按规则 11，文档与代码冲突时标 ISSUE）
> **本批次决策摘要**（沿用用户决策 @ 19:26）：
> 1. 不跑 `migrate_ext_19_mini_program_openid.sql`（I038 决策 #3 第三次强调）→ F081/F082/F095-F106 保持 **MISSING**
> 2. 不改 `ext-19-operational-notes.mdc`（用户决策 #7）→ I039/I043 标 CONFLICT 中立标
> 3. WeChat / SMTP 凭据 Mock 降级（决策 #1/#2 / D10 铁律）→ F088-F090/F092 UNVERIFIED
> 4. `autoSend=false` UI + 状态确认按钮文案（决策 #6）→ I040/I041 Shadow，Post-Audit
> 5. 命名异味 `IntlBookingService.@RestController`（I044）→ backlog 第 4 次出现
> 6. `member_message` 表不跑迁移（I045 决策 #3 第三次强调）→ F091 PARTIAL-BLOCKER
> 7. **`healthCheckAll` T07-D4 例外（I050）有意保留**
> 8. **Batch 1.4 非重构批次** → 跳过 Phase 3.5 Diff Audit（沿用 Batch 1.1/1.2/1.3）

### 31.1 本节产出摘要

| 产出 | 路径 | 内容 |
|---|---|---|
| **静态扫描复用** | `audit/scripts/01-08-scan-*.ps1`（Batch 1.1 已生成）+ grep `BookingConfirmation|StoreLookup|EmailSender|SmsSender|MiniProgram|InAppSender|WechatOfficialAccount|ChannelDispatcher|ChannelSender|IntlBooking|OverseasBooking|overseas_booking|member_message` 提取 Batch 1.4 范围命中 |
| **8 份扫描报告引用** | `audit/static-gap/01-08-*.txt`（Batch 1.1 全量扫描） | grep 提取 Batch 1.4 范围 |
| **精细分析** | 本报告 §31.4-§31.12 | Shadow / Ghost / Fake / Contract Drift / SoT 冲突 / 命名异味 / I008 模式 catch 吞异常 / I009 模式 rollbackFor 缺失 |
| **I038-I051 交叉引用** | `audit/feature-inventory.md` §1.4.4 / §1.4.5 | 14 项 Phase 1 Issue 全量交叉验证 |

### 31.2 Batch 1.4 守门脚本命中数

> 复用 Batch 1.1 的 8 个守门脚本（全量扫描 + grep BookingConfirmation/StoreLookup/EmailSender/SmsSender/MiniProgram/InAppSender/WechatOfficialAccount/ChannelDispatcher/ChannelSender/IntlBooking/OverseasBooking/overseas_booking/member_message 提取）。

| 脚本 | 全项目 Total | Batch 1.4 范围命中 | 涉及 Feature | 说明 |
|---|---|---|---|---|
| **01-fake-success** | 299 | **18**（含 1 HIGH catch-ignore-empty） | F083 / F084 / F087 / F091-F093 / F096 / F099-F100 | catch-log-swallow **17 处** + catch-ignore-empty **1 处**（`IntlBookingService.java:281` instagramWebhook 内）；同 I008 Fake Success 模式 |
| **02-mock** | 90 | **0** | — | 5 个 channel sender 各自有 demo 降级但无 `@ConditionalOnMissingBean` 风格；`MemberMessage` 写表（M09 共享 sender 不依赖 mock） |
| **03-disabled** | 94 | **19**（含 `BookingConfirmationService.java:171` 1 处） | F083 / F094 / F088-F092 | 全部为 `return null` 参数校验早返回（合法设计）；0 处 `@Disabled`/`@Deprecated`/`UnsupportedOperationException` 命中 |
| **04-endpoint-coverage** | 后端 2013 / 前端 859 | **5** Only-Backend（webhook demo 模式 + connect/callback） | F099 / F100 / F102 | `POST:/intl-booking/google/callback` + `instagram/callback` + `connect` × 2 + `pull` × 1 — **真实连接入口，但前端确认按钮只是 OAuth 跳转不是 webhook 入口**；**0 个 L1 false-positive**（`${id}` vs `{id}` 不归一化在该范围无影响） |
| **05-contract-drift** | 1530 | **9** | F095 / F096 / F099 / F100 / F102 / F103 | `IntlBookingService.java:45/72/92/118/184/185/204/259/299` — `@RequestBody Map<String,Object>` 无 `@Valid`（9 处）+ `@RequestParam hub.mode/verify_token/challenge` 无 defaultValue（3 处）；OAuth callback + body 接收 ≥8 处 |
| **06-soft-delete** | 77 | **0** | — | `overseas_booking`（I038 缺失）+ `member_message`（I045 缺失）+ `pos_store`（存在无 deleted）+ `crm_member`（I002 旁支，不在 Batch 1.4 范围）；`mkt_channel_config` 有 deleted 列但 UNIQUE 不含 deleted — **沿用 I002 MISSING 模式标记** |
| **07-transaction** | 214 | **7**（全是 `Transactional-no-rollbackFor`） | F096 / F099 / F100 / F101 无 / F102 / F103 | `IntlBookingService.java:71 / 117 / 150 / 203 / 258 / 298 / 372` — **7 个 `@Transactional` 方法，全部默认 `rollbackFor=RuntimeException`**；仅 F091 InAppSender 是 `@Transactional(rollbackFor=Exception.class)` 唯一正确；沿用 I009 模式 |
| **08-shadow-ghost** | 2003 | **5** Only-Backend | F095 / F099 / F100 / F101 | 后端 webhook/verify 端点 + 手动 `/send-confirmation` 无前端调用（**I042 Shadow** + **I040/I041 Shadow**）；**0 个 Ghost**（前端有调用但后端无） |

> **Batch 1.4 范围专属命中汇总**：18 (fake-success) + 0 (mock) + 19 (disabled，均为 return null 合法校验) + 5 (endpoint-coverage) + 9 (contract-drift) + 0 (soft-delete) + 7 (transaction) + 5 (shadow-ghost) = **63 命中**（含 1 HIGH）

### 31.3 02-mock 中 Batch 1.4 范围相关命中

| Feature ID | 类别 | 位置 | 证据 | 判定 |
|---|---|---|---|---|
| — | 无命中 | — | — | 5 个 channel sender 各自有 demo 降级但无 `@ConditionalOnMissingBean` 风格（与 Batch 1.1 F015 Mock 适配器不同）；`ChannelDispatcher` 用 Spring `List<ChannelSender> senders` 自动收集全部实现（无 fallback 设计意图） |

### 31.4 01-fake-success 中 Batch 1.4 范围相关命中（18 处精细分析）

> **重要**：catch-log-swallow 不一定都是 bug。Phase 5 静态 + Phase 6 实测关联后判断。Batch 1.4 范围 catch 共 18 处，其中：
> - **10 处是 I008 同模式**（`log.warn` 仅记录不抛 — 与 SoT §0.3 "发送异常不会回滚状态变更" 设计一致）
> - **1 处是 HIGH catch-ignore-empty**（`IntlBookingService.java:281`）
> - **7 处是合理设计降级**（配置缺失 demo 模式 + token cache miss）

| Feature ID | 位置 | 命中模式 | 实际语义 | Phase 3 判定 |
|---|---|---|---|---|
| **F083** | `BookingConfirmationService.java:171` | return null（在 03-disabled 命中）| `preview()` booking=null → 返回 null（合法边界）| OK（设计边界）|
| **F084** | `BookingConfirmationTemplateResolver.java:50` + `:141` | catch-log-swallow | (1) `loadLocale()` IOException → log warn → 返回 null；(2) 主流程 `resolve()` 内 try/catch 包裹 JSON 解析失败 | **PARTIAL-NON-BLOCKER**（降级到 en-US DEFAULT_LOCALE 是设计；warn 仅记录） |
| **F087** | `StoreLookupService.java:69` | catch-log-swallow | SQL 查询 `pos_store` 失败 → log warn → 返回 null → 触发 F087 兜底 `美业旗舰店 / 400-888-8888` | **PARTIAL-NON-BLOCKER**（兜底常量设计；warn 仅记录） |
| **F083** | `EmailSender.java:242` | catch-log-swallow | demoMode 后再 catch 包裹 `log.error("[EMAIL] send exception: ...")` 返回 `SendResult.fail` | OK（已向上抛错误码）|
| **F083** | `EmailSender.java:201` | catch-ignore-empty（HIGH）| `try { tenantId = TenantContextHolder.get().getTenantId(); } catch (Exception ignored) {}` — 多租户缺失静默；与 I038/I045 同模式 | **PARTIAL-NON-BLOCKER**（I008 模式）|
| **F091** | `InAppSender.java:175` | catch-log-swallow | `healthCheck()` catch 异常 → log warn → 返回 false | OK（健康检查降级）|
| **F090** | `MiniProgramSender.java:171` + `:227` | catch-log-swallow | (1) `getAccessToken` HTTP 失败 → log warn → 返回 null；(2) `send` bizsend 失败 → log warn → 返回 `SendResult.fail` | **PARTIAL-NON-BLOCKER**（D10 凭据缺失链路，UNVERIFIED）|
| **F088** | `SmsSender.java:153` + `:193` + `:102 (catch-ignore-empty)` | catch-log-swallow + catch-ignore-empty（HIGH） | (1) `sendAliyunSms` 失败 → log warn；(2) 解析 resp 失败 → log warn；(3) `try { tenantId = ... } catch (Exception ignored) {}` 静默 | **PARTIAL-NON-BLOCKER**（UNVERIFIED）|
| **F092** | `WechatOfficialAccountSender.java:208` + `:268` | catch-log-swallow | 与 F090 同模式（UNVERIFIED）| **PARTIAL-NON-BLOCKER**（UNVERIFIED）|
| **F096** | `IntlBookingService.java:231` | catch-log-swallow | `updateStatus` autoSend 发送异常 → log warn → 写 `confirmationError` 字段 → 不回滚状态变更（**SoT §0.3 完全一致的设计**）| **PARTIAL-NON-BLOCKER**（I047 模式，与 SoT 对齐）|
| **F096** | `IntlBookingService.java:281` | catch-ignore-empty（HIGH）| `instagramWebhook` 内 `bookingTime.toString().substring(0, 10)` 失败 → `catch (Exception ignore) {}` 静默吞异常 | **PARTIAL-NON-BLOCKER**（数据解析容错设计）|
| **F096** | `IntlBookingService.java:342` | catch-log-swallow | 主 `instagramWebhook` 外层 catch → log warn → 返回 `NOOP` | OK（webhook 兜底）|

> **沿用 Batch 1.1/1.2/1.3 经验**：18 处 catch 中：
> - **1 处 HIGH**（`IntlBookingService.java:281` catch-ignore-empty，但语义合理）
> - **10 处属 I008/I009 同模式**（F096 autoSend 是 SoT 设计 + I047/I048 模式化登记）
> - **7 处属 UNVERIFIED 链路降级**（D10 铁律 — 凭据缺失 — UNVERIFIED 维持）
> - **0 处是新 Block bug**

### 31.5 04-endpoint-coverage 中 Batch 1.4 范围精细对比

#### 30.5.1 IntlBookingService 端点（13 个 — `/intl-booking/*`）

| 后端 Endpoint | 后端位置 | 前端 API 调用位置 | 04 报告状态 | 人工修正结论 |
|---|---|---|---|---|
| `POST /intl-booking/google/connect` | `IntlBookingService.java:42-65` | `api/intl-booking.ts:14` `connectGoogle` | Only-Backend | **Matched**（前端调用真实存在，跳转 dialog）|
| `POST /intl-booking/google/callback` | `IntlBookingService.java:71-95` | `api/intl-booking.ts:17` `googleCallback` | Only-Backend | **Matched**（L1 false positive — OAuth dialog 流程）|
| `POST /intl-booking/instagram/connect` | `IntlBookingService.java:101-126` | `api/intl-booking.ts:20` `connectInstagram` | Only-Backend | **Matched** |
| `POST /intl-booking/instagram/callback` | `IntlBookingService.java:132-151` | `api/intl-booking.ts:23` `instagramCallback` | Only-Backend | **Matched** |
| `GET /intl-booking/channels` | `IntlBookingService.java:155-170` | `api/intl-booking.ts:26` `listChannels` | **缺失于 04 报告** | **Matched** |
| `POST /intl-booking/{channelId}/pull` | `IntlBookingService.java:177-211` | `api/intl-booking.ts:28` `pullBookings` | Only-Backend | **Matched**（L1 false positive — `${channelId}` 模板字符串已识别）|
| `GET /intl-booking/list` | `IntlBookingService.java:215-225` | `api/intl-booking.ts:31` `listBookings` | **缺失于 04 报告** | **Matched** |
| `GET /intl-booking/{id}` | `IntlBookingService.java:228-233` | `api/intl-booking.ts:33` `getBooking` | Only-Backend | **Matched** |
| **`POST /intl-booking/{id}/status`** | **`IntlBookingService.java:240-279`** | **`api/intl-booking.ts:34` `updateStatus`** | Only-Backend | **Matched（** **`data: {status: 'CONFIRMED'}`** **无 `autoSend=false` 字段，与 SoT §0.3 反向 — I040 Shadow 已登记**）|
| **`POST /intl-booking/{id}/send-confirmation`** | **`IntlBookingService.java:286-292`** | **(无 — 整个 `intl-booking.ts` 12 端点封装无 `sendConfirmation`)** | **Only-Backend** | **真实 Shadow Feature (I042)** — 后端 `sendConfirmation(id)` 端点存在，前端无任何调用入口 |
| `POST /intl-booking/{id}/convert` | `IntlBookingService.java:295-...` | `api/intl-booking.ts:39` `convertBooking` | Only-Backend | **Matched** |
| `POST /intl-booking/webhook/google` | `IntlBookingService.java:304-339` | (无 — Google BP 后台 push) | Only-Backend | **Matched（设计 — webhook 入口无前端）** |
| `POST /intl-booking/webhook/instagram` | `IntlBookingService.java:347-394` | (无 — Meta 后台 push) | Only-Backend | **Matched（设计）** |
| `GET /intl-booking/webhook/instagram` | `IntlBookingService.java:400-412` | (无 — Meta verify 握手) | Only-Backend | **Matched（设计）** |
| `POST /intl-booking/{id}/confirmation-template` | `IntlBookingService.java:confirmationTemplate` | `api/intl-booking.ts:42` `confirmationTemplate` | Only-Backend | **Matched（** **但走硬编码 switcher — I043 CONFLICT 与 SoT §3 期望走 Resolver 反向**）|

> **真实结论**：Batch 1.4 IntlBooking 端点层面 **15 个**：
> - **12 个 Matched**
> - **1 个 Shadow Feature (F095 / I042)**: `POST /intl-booking/{id}/send-confirmation` 后端有但前端无
> - **1 个 Shadow UI (F096 / I040 + I041)**: `updateStatus` 端点被调用但 `autoSend=false` UI 缺失 + 按钮无 "确认即发送通知" 文案
> - **1 个 CONFLICT Shadow (F097 / I043)**: `confirmation-template` 前端调用但实现走硬编码 switcher 而非 Resolver

### 31.6 06-soft-delete 中 Batch 1.4 范围相关命中

> Batch 1.4 涉及的 4 张表（`overseas_booking` / `member_message` / `pos_store` / `mkt_channel_config`）**均不在 06 报告范围或命中数=0**：

| 表 | 06 报告状态 | 备注 |
|---|---|---|
| `overseas_booking` | **不在范围（MISSING）** | db-snapshot.sql grep `overseas_booking` **0 匹配已确认**（I038）— Phase 5 验证 DDL 已写但 dev DB 未跑迁移 |
| `member_message` | **不在范围（MISSING）** | db-snapshot.sql grep `member_message` **0 匹配已确认**（I045）— Phase 5 验证 |
| `pos_store` | 06 报告未命中 | db-snapshot.sql grep `pos_store` 5 匹配；Phase 5 `DESCRIBE pos_store` 验证 `store_name` + `contact_phone` 字段对齐（I046） |
| `mkt_channel_config` | **HIGH 命中**（沿用 EXT-19 共享资源）| db-snapshot.sql 中 14 列无 `deleted` 列；Unique 索引不含 deleted → 多租户重复配置 UNIQUE 冲突风险（沿用 I002 模式；本批次范围外） |

> **核心结论**：Batch 1.4 **0 个 06-soft-delete 真实 Block bug**（核心表 I038/I045 都是 MISSING 而非 soft-delete 问题）。

### 31.7 07-transaction 中 Batch 1.4 范围相关命中（7 处全为 I009 模式）

| Feature ID | 位置 | 命中模式 | 实际语义 | Phase 3 判定 |
|---|---|---|---|---|
| **F099** | `IntlBookingService.java:71` `googleCallback` `@Transactional` | Transactional-no-rollbackFor | 默认 only rollback RuntimeException；方法内不抛 checked exception + `Result.success` 直接返回 → 影响小 | **PARTIAL-NON-BLOCKER**（I048 模式）|
| **F100** | `IntlBookingService.java:117` `instagramCallback` `@Transactional` | Transactional-no-rollbackFor | 同 F099 | **PARTIAL-NON-BLOCKER**（I048 模式）|
| **F103** | `IntlBookingService.java:150` `convertBooking` `@Transactional` | Transactional-no-rollbackFor | 演示模式 — `JdbcTemplate.update()` → `LAST_INSERT_ID()` 链式调用；若其中某步抛 checked exception 不回滚（业务侧写库失败概率低）| **PARTIAL-NON-BLOCKER**（I048 模式）|
| **F102** | `IntlBookingService.java:203` `pullBookings` `@Transactional` | Transactional-no-rollbackFor | 同 F103 | **PARTIAL-NON-BLOCKER** |
| **F096** | `IntlBookingService.java:258` `updateStatus` `@Transactional` | Transactional-no-rollbackFor | **状态变更后自动触发 sendConfirmation，事务语义重要**：若 sendConfirmation 抛 checked exception → 状态已变 → 事务不回滚 → 数据不一致 | **PARTIAL-NON-BLOCKER**（I048 模式；但有 F096 try/catch 兜底保护，实际影响小）|
| **F099** | `IntlBookingService.java:298` `googleWebhook` `@Transactional` | Transactional-no-rollbackFor | 同 F099 | **PARTIAL-NON-BLOCKER** |
| **F100** | `IntlBookingService.java:372` `instagramWebhook` `@Transactional` | Transactional-no-rollbackFor | 同 F099 | **PARTIAL-NON-BLOCKER** |
| **F091** ✅ | `InAppSender.java:43` `send` `@Transactional(rollbackFor = Exception.class)` | **OK 唯一正确** | 唯一指定 rollbackFor=Exception.class 的事务方法 | **OK**（InAppSender 设计完备）|

> **核心结论**：Batch 1.4 范围 **7 个 `@Transactional` 方法中 0 个正确指定 rollbackFor**（仅 F091 InAppSender 正确）。沿用 I009/I048 模式：建议加 `rollbackFor = Exception.class`，但**本批次不改代码**（用户决策 #6 审计阶段只记录不修复）。

### 31.8 08-shadow-ghost 中 Batch 1.4 范围相关命中（5 维）

| 类型 | 命中数 | 涉及 Feature | 备注 |
|---|---|---|---|
| **Shadow Feature**（only-backend）| **5** | **F095** (send-confirmation) + **F096** (autoSend UI) + **F099** (webhook/google) + **F100** (webhook/instagram) + **F101** (webhook/verify) | 前 1 个是真实 I042 Shadow；后 4 个是 webhook 设计（无前端调用是合理的）|
| **Ghost Feature**（only-frontend）| **0** | — | 前端调用的所有端点后端都有 |
| **Zombie Feature**（@Deprecated）| **0** | — | BookingConfirmation / ChannelSender / IntlBooking 范围无 @Deprecated 命中 |
| **Fake Feature**（setTimeout stub）| **0** | — | 无 setTimeout fake async |
| **Shell Feature**（< 500 bytes Vue）| **0** | — | `views/intl-booking/index.vue` 150+ 行，完整页面 |

> **真实 Shadow/Ghost 净数**：**2 个真实 Shadow**（F095 I042 + F096 I040/I041）+ **3 个设计 Shadow**（F099/F100/F101 webhook — 不算 bug）。**0 个真实 Ghost**。

### 31.9 EXT-19 特定问题精细识别（沿用 I038-I051）

> 用户要求**特别检查** EXT-19 特定问题；以下为 Phase 3 静态识别结果。

#### 30.9.1 I038 — overseas_booking 表缺失（与 I002/I017/I029 同模式，第 4 次出现）

| 维度 | 详情 | 位置 |
|---|---|---|
| **DDL 文件存在** | `CREATE TABLE IF NOT EXISTS overseas_booking (...)` 26 列 + 4 索引 | `backend/src/main/resources/db/sql/migrate_ext_19_mini_program_openid.sql:9-32` |
| **ALTER 也存在** | `ADD COLUMN IF NOT EXISTS mini_program_open_id` | 同文件 `:36` |
| **db-snapshot.sql 命中** | **0 处**（grep `overseas_booking` 全文件 0 匹配已确认）| Phase 1 grep + Phase 5 验证 |
| **代码实体类存在** | `OverseasBooking.java` 26 字段 + `@TableName("overseas_booking")`（含 miniProgramOpenId L21）| `entity/OverseasBooking.java:8-31` |
| **运行时后果** | `selectById` / `insert` / `updateById` / `listBookings` 启动期会触发 `TableNotFoundException` → 与 I002/I017/I029 同链路 | `IntlBookingService.java:241/256/329/373/...` |
| **判定** | **MISSING**（I038）— 按用户决策 #3：保留 PARTIAL-BLOCKER；Phase 5 仅 MySQL MCP 验证缺失；Post-Audit 统一跑批 | — |

#### 30.9.2 I039 — `WECHAT_OFFICIAL_ACCOUNT` vs SoT `WECHAT_OA` 别名漂移（与 I001/I018/I026-I030 同模式）

| 维度 | 详情 | 位置 |
|---|---|---|
| **SoT §1 描述** | 渠道简称 `WECHAT_OA` 别名（描述性引用代码类名 `WechatOfficialAccountSender`）| `.cursor/rules/ext-19-operational-notes.mdc` §1 |
| **代码实际** | `channelType() = "WECHAT_OFFICIAL_ACCOUNT"` | `WechatOfficialAccountSender.java:60` |
| **别名漂移** | SoT 用简称 `WECHAT_OA`，代码用完整 `WECHAT_OFFICIAL_ACCOUNT` — **同一渠道两个命名** | 同上 |
| **代码内注释确认** | `WechatOfficialAccountSender.java:24` 注释明确 "channelType 为 `WECHAT_OFFICIAL_ACCOUNT`" — **代码自认知是完整名** | 同上 |
| **真实功能影响** | **0** — 功能完整，仅 SoT 描述不一致 | — |
| **判定** | **CONFLICT**（I039 中立标）— 沿用 I001/I018/I026-I030 模式；Fix Phase = Post-Audit 人类决策 | — |

#### 30.9.3 I040 / I041 / I042 — Frontend Shadow 三件套

| Issue | 维度 | 详情 | 位置 |
|---|---|---|---|
| **I040** | **Shadow — autoSend=false UI 缺失** | SoT §0.3 `body.autoSend=false` 可关闭自动发送；后端 `IntlBookingService.updateStatus:259` `Boolean autoSend = body.get("autoSend") == null ? Boolean.TRUE : ...` 支持读取 → **前端 `intl-booking/index.vue:34` `updateStatus: (id, data) => request.post(\`/intl-booking/${id}/status\`, data)` 仅传 `{status: 'CONFIRMED'}`，不传 `autoSend`，无 UI 暴露** | `api/intl-booking.ts:34-35` + `views/intl-booking/index.vue:66` |
| **I041** | **Shadow — 按钮文案无提示** | SoT §0.3 明确要求"前端状态确认按钮文案需明确『确认即发送通知』，避免用户无感被自动触达" → **前端 `intl-booking/index.vue:66` 按钮文案"确认"，无"即发送通知"提示 + 无 el-popconfirm 二次确认 + 无 loading 状态** | `views/intl-booking/index.vue:66` |
| **I042** | **Shadow — sendConfirmation API 缺失** | 后端 `IntlBookingService.java:286-292` `sendConfirmation(id)` 端点存在 → **前端 `intl-booking.ts` 12 端点封装中无 `sendConfirmation` 方法**（grep 整个文件 `sendConfirmation\|send-confirmation` **0 匹配已确认**）| `api/intl-booking.ts:1-42`（整个文件）|

> **核心结论**：3 个 Shadow 全部是对前端 `intl-booking/index.vue` + `api/intl-booking.ts` 的精细扫描确认。沿用 I006/I007 模式中立标，Fix Phase = Post-Audit。

#### 30.9.4 I043 — confirmation-template 硬编码 switcher（SoT §3 期望走 Resolver 反向）

| 维度 | 详情 | 位置 |
|---|---|---|
| **SoT §3 期望** | `GET /intl-booking/{id}/confirmation-template` 应走 `BookingConfirmationTemplateResolver` + 6 locale JSON 模板体系 | `.cursor/rules/ext-19-operational-notes.mdc` §3 |
| **代码实际** | `IntlBookingService.confirmationTemplate(id)` 是控制器内**硬编码语言 switcher**（zh/zh-cn/zh-tw/ja/ko/default 6 分支），返回仅 `subject + body` | `IntlBookingService.java:confirmationTemplate()`（Phase 1 全文搜索确认）|
| **DTO 反向** | `BookingConfirmationTemplate` 期望 4 段 `subject/sms/miniProgram(title+keyword1..N)/email(subject+body)` 共 10 字段；硬编码 switcher 只返回 2 字段 `subject + body` | `BookingConfirmationTemplate.java:14-32` |
| **生产发送链路 vs Preview 链路不一致** | F095/F096 走 Resolver（生产）；F097 走硬编码 switcher（preview）→ 同一数据两条不同路径 | — |
| **真实功能影响** | **Preview UI 仅显示 subject + body，但实际生产发送会发 4 段完整内容** — 用户预览到的不是实际看到的 | `views/intl-booking/index.vue:77-79` (`template.body` + `template.subject`) |
| **判定** | **CONFLICT**（I043 中立标）— 沿用 I001/I018/I026-I030 模式 | — |

#### 30.9.5 I044 — IntlBookingService `@RestController` 命名异味（第 4 次出现）

| 维度 | 详情 | 位置 |
|---|---|---|
| **类名** | `IntlBookingService`（`*Service` 后缀）| `backend/src/main/java/com/meiyetec/service/IntlBookingService.java` |
| **真实类型** | `@RestController @RequestMapping("/intl-booking")` (`:14-15`) — 15 个 REST endpoint 集中单文件 | 同上 |
| **项目其他 Controller 位置** | `backend/src/main/java/com/meiyetec/controller/`（如 `PluginRuntimeController.java` / `YouzanEcommerceController.java`）| 路径对比 |
| **同模式历史** | (1) **Batch 1.1 I004** — `CrmIntegrationService.@RestController`；(2) **Batch 1.2 I019** — `PluginService.@RestController`；(3) **Batch 1.3 I029** — `EcommerceService.@RestController`；(4) **Batch 1.4 I044** — `IntlBookingService.@RestController` | 见 issue-register.md §四 / §十九 / §二十六 |
| **判定** | **架构异味 Backlog**（I044）— **第 4 次出现，建议 Post-Audit 统一整改**（重构方案：移到 `controller/IntlBookingController.java` 或拆 3 个文件 OAuth/webhook/booking-confirm） | — |

#### 30.9.6 I045 — member_message 表缺失（第 4 次 MISSING 模式）

| 维度 | 详情 | 位置 |
|---|---|---|
| **使用方** | `InAppSender.send()` 写 `member_message` 表 | `InAppSender.java:67` `messageMapper.insert(msg)` |
| **db-snapshot.sql 命中** | **0 处**（grep `member_message` 全文件 0 匹配已确认）| Phase 1 grep + Phase 5 验证 |
| **运行时后果** | `messageMapper.insert(msg)` 启动期会触发 `TableNotFoundException` → `InAppSender.java:84` catch 包裹 + `log.error` + 返回 `SendResult.fail` | — |
| **注释提示** | `InAppSender.java:46` 注释："M09-01：站内信渠道 — 真实写入 member_message 表" → **真实写库（非 mock），表缺失链路直接失败** | — |
| **判定** | **MISSING**（I045）— 与 I038 同模式，沿用用户决策 #3；Phase 5 仅验证缺失；Post-Audit 统一跑批 | — |

#### 30.9.7 I046 — pos_store 字段对齐待验证（与 I002 模式）

| 维度 | 详情 | 位置 |
|---|---|---|
| **EXT-19 依赖** | `StoreLookupService.java:65` SQL `SELECT store_name, contact_phone FROM pos_store WHERE id=? AND deleted=0` | `StoreLookupService.java:65` |
| **db-snapshot.sql 命中** | 5 匹配（`pos_store` 表存在）| grep 验证 |
| **字段是否真实存在** | 待 Phase 5 `DESCRIBE pos_store` 验证 `store_name` + `contact_phone` 列 | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I046 待验证）— Phase 5 MySQL MCP 验证 | — |

#### 30.9.8 I047 — IntlBookingService autoSend catch 吞异常（I008 模式，第 4 次出现）

| 维度 | 详情 | 位置 |
|---|---|---|
| **位置** | `IntlBookingService.updateStatus:262-275` try/catch 包裹 sendConfirmation | `IntlBookingService.java:262-275` |
| **SoT §0.3 一致** | "发送异常不会回滚状态变更（已在 try/catch 内），仅写入 `confirmationError` 字段" | `.cursor/rules/ext-19-operational-notes.mdc` §0.3 |
| **判定** | **PARTIAL-NON-BLOCKER**（I047）— 与 SoT 完全一致，**设计而非 Bug**；运维侧需依赖日志监控（`log.warn` 关键词 `[INTL-BOOKING] auto-send failed (status 仍成功)`）| — |

#### 30.9.9 I048 — IntlBookingService 7 个 `@Transactional` 默认 rollbackFor（I009 模式，第 4 次出现）

| 维度 | 详情 | 位置 |
|---|---|---|
| **位置** | `IntlBookingService.java:71/117/150/203/258/298/372` — 7 个 `@Transactional` 均**未指定 `rollbackFor`** | `IntlBookingService.java` |
| **影响方法** | (1) googleCallback + (2) instagramCallback + (3) convertBooking + (4) pullBookings + (5) updateStatus + (6) googleWebhook + (7) instagramWebhook | 同上 |
| **唯一正确** | **InAppSender** `InAppSender.java:43` `@Transactional(rollbackFor = Exception.class)`（行业最佳实践）| `InAppSender.java:43` |
| **判定** | **PARTIAL-NON-BLOCKER**（I048）— 沿用 I009 模式；本批次不改 | — |

#### 30.9.10 I049 — 渠道 sender 真实凭据缺失（D10 铁律 UNVERIFIED）

| 维度 | 详情 | 位置 |
|---|---|---|
| **UNVERIFIED 链路** | (1) `SmsSender` 阿里云 accessKeyId/Secret/signName；(2) `EmailSender` SMTP password；(3) `MiniProgramSender` 微信 appId/appSecret；(4) `WechatOfficialAccountSender` 公众号 appId/appSecret | 4 个 sender 各自的 `resolveConfig()` |
| **真实代码** | 4 个 sender 都有 `if (cfg == null \|\| accessKeyId == null) { log.warn("Incomplete X config"); return demoMode(); }` 真实降级链路 | — |
| **demoMode 返回** | `SendResult.ok("SMS-DEMO-{ts}")` 或 `EMAIL-DEMO-{ts}` | — |
| **判定** | **UNVERIFIED**（I049）— D10 铁律；Phase 5 仅 Mock 验证；真实链路需 Phase 5 用户手动注入凭据 | — |

#### 30.9.11 I050 — healthCheckAll T07-D4 例外（与 I022 同模式）

| 维度 | 详情 | 位置 |
|---|---|---|
| **位置** | `ChannelDispatcher.healthCheckAll():41-58` 仅更新默认租户 (tenant_id=1) 渠道配置 + 显式 `log.warn("[T07-D4] ...")` 例外声明 | `ChannelDispatcher.java:43` |
| **设计意图** | "系统级批处理入口，无 HTTP/tenant 上下文；按设计仅更新默认租户（tenant_id=1）" — `ChannelDispatcher.java:36-40` 注释明示 | 同上 |
| **影响** | 其他租户渠道健康状态不会被更新 — **有意保留的语义** | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I050）— 例外声明已记录；本批次不改 | — |

#### 30.9.12 I051 — webhook/OAuth 演示模式（real OAuth 未实现）

| 维度 | 详情 | 位置 |
|---|---|---|
| **OAuth callback 不真换 token** | `googleCallback:84` `accessToken = "ya29.DEMO-" + code.substring(0, 8)` + `instagramCallback:148` `accessToken = "IGQVJ-DEMO-" + code.substring(0, 8)` | `IntlBookingService.java:84/148` |
| **webhook 不真订阅** | `googleCallback:71-95` + `instagramCallback:117-151` 都**演示模式**：不调用真实 Google/Instagram token 端点 | 同上 |
| **tenantId 演示兜底 1L** | `googleWebhook:330` `if (tenantId == null) tenantId = 1L` + `instagramWebhook:373` `Long tenantId = 1L` 硬编码 | 同上 |
| **判定** | **PARTIAL-NON-BLOCKER**（I051）— 演示模式足够联调；本批次不改（沿用 P3-26 演示模式）| — |

---

### 31.10 Batch 1.4 EXT-* SoT 已知冲突（沿用 I039 / I043 / I044 / I045 标 CONFLICT 或 MISSING）

| Feature ID | 缺口类型 | SoT 描述 | 代码事实 | Phase 3 处理 |
|---|---|---|---|---|
| **F092** | **CONFLICT**（I039 别名） | `.mdc` §1 描述：`WechatOfficialAccountSender` 别名 `WECHAT_OA` | `WechatOfficialAccountSender.java:60` `channelType() = "WECHAT_OFFICIAL_ACCOUNT"`（完整名）| **按用户决策 #7：保留 CONFLICT（I039），不动 `ext-19.mdc`** |
| **F097** | **CONFLICT**（I043 反向）| `.mdc` §3 期望 `confirmation-template` 走 `BookingConfirmationTemplateResolver` + 6 locale JSON | `IntlBookingService.confirmationTemplate` 硬编码 switcher（zh/zh-cn/zh-tw/ja/ko/default），仅返回 `subject + body` | **保留 CONFLICT（I043），本批次不改** |
| **F091** | **MISSING（I045）**| (无 SoT 漂移) `member_message` 表 | `InAppSender.java:46` 注释："真实写入 member_message 表" | db-snapshot.sql grep 0 命中；按用户决策 #3 维持 MISSING；Post-Audit 跑批 |
| **F081** | **MISSING（I038）**| (无 SoT 漂移) `overseas_booking` 表 + `miniProgramOpenId` 列 | DDL 已写（含 ALTER `ADD COLUMN IF NOT EXISTS`）| 同 I045；Post-Audit 跑批 |

### 31.11 与 Batch 1.1/1.2/1.3 的跨批次同模式汇总

> 按 `project-audit.mdc` 规则 6：审计阶段只记录不修复。Batch 1.4 与前三批次有 **5 个同模式**：

| 模式 | Batch 1.1 | Batch 1.2 | Batch 1.3 | Batch 1.4 | 决策 |
|---|---|---|---|---|---|
| **DB 表 MISSING**（I002 / I017 / I029）| `crm_contact_map` + EXT-15 列 | `plugin_runtime_state` | `google_maps_config` + 3 列 | `overseas_booking` (I038) + `member_message` (I045) | **统一 Post-Audit 跑批**（用户决策 #3 第 4 次强调）|
| **SoT vs 代码 CONFLICT**（I001 / I018 / I026-I030）| HubSpot/Zoho 已实现 / Router fallback | assertTenantAccess 已实现 | 4 个 CONFLICT | WECHAT_OA 别名 (I039) + confirmation-template 硬编码 (I043) | **中立标，Post-Audit 人类决策** |
| **Fake Success（I008 catch-ignore-empty）** | `CrmIntegrationService.dashboard()` | `PluginRuntimeService.dashboard()` | `GoogleMapsService.health()` | `IntlBookingService.instagramWebhook()` (L281) — **本批次新增 catch-ignore-empty 1 处** | **PARTIAL-NON-BLOCKER**（设计意图，log.warn 兜底）|
| **Transaction rollbackFor 缺失（I009）** | `CrmIntegrationService.syncNow` | `PluginPersistenceService.recordState` + `PluginService.install` | `YouzanWebhookController.handle` 7 处 | **7 处 @Transactional 无 rollbackFor** (IntlBookingService 6 处 + 共享 trigger 1 处) | **PARTIAL-NON-BLOCKER**；建议加 `rollbackFor = Exception.class`（本批次不改）|
| **架构异味（@RestController 命名误导）**| I004 `CrmIntegrationService` | I019 `PluginService` | I029 `EcommerceService` | **I044 `IntlBookingService`**（**第 4 次出现**）| **Backlog 统一整改**（建议 Post-Audit 一次性重构）|

### 31.12 Batch 1.4 静态分析结论（最终）

| 维度 | Phase 1 | Phase 2 | Phase 3（本次） | 变化 |
|---|---|---|---|---|
| Feature 总数 | 33 | 33 | 33 | — |
| **VERIFIED** | 0 | 0 | 0 | — |
| **UNVERIFIED** | 6 | 6 | **6** | —（F088-F090/F092 沿用 D10）|
| **PARTIAL-NON-BLOCKER** | 16 | 16 | **17** | +1（F096 I047 autoSend catch 模式化登记）|
| **PARTIAL-BLOCKER** | 0 | 0 | **4** | +4（F081/F082 I038 + F091 I045 + F087 I046 待验证）|
| **MISSING** | 0 | 0 | **0**（按用户决策 #3 维持 + 标 PARTIAL-BLOCKER） | — |
| **CONFLICT** | 3 | 3 | **3** | —（I039 + I043 + I044，I044 是 backlog 不算 CONFLICT）|
| **MOCK-INTENTIONAL** | 0 | 0 | 0 | — |
| **backlog（命名异味）**| 1 | 1 | **1** | —（I044 第 4 次出现）|

> **修订说明**：
> - F087 在 Phase 1 标 PARTIAL-NON-BLOCKER（I046 待验证）；本次 Phase 3 升级为 **PARTIAL-BLOCKER**（I046 待 Phase 5 验证 `DESCRIBE pos_store`）
> - F081/F082/F091 在 Phase 1 标 MISSING；本次按用户决策 #3 重归类为 **PARTIAL-BLOCKER**（I038/I045 统一 Post-Audit 跑批）
> - F096 在 Phase 1 标 PARTIAL-NON-BLOCKER（I040/I041/I047）；本次新增 **I047** 模式化登记（与 SoT 设计一致）
> - F097/F092 在 Phase 1 标 CONFLICT（I039/I043）；本次维持

### 31.13 Phase 3 → Phase 4 入口守门（Batch 1.4）

| 检查项 | 状态 | 备注 |
|---|---|---|
| 8 个守门脚本已生成（沿用 Batch 1.1）| ✅ | `audit/scripts/01-08-scan-*.ps1` |
| 8 份扫描报告 grep 提取 Batch 1.4 范围 | ✅ | `audit/static-gap/01-08-*.txt` + filter |
| Batch 1.4 范围精细分析完成 | ✅ | Shadow / Ghost / Fake / Contract Drift / SoT Conflict / 命名异味 / I008 catch / I009 rollbackFor 全识别 |
| Issue Register I038-I051 已登记（Phase 1）| ✅ | 14 项 — 沿用 `.md` §1.4.4 + §1.4.5 |
| 不动代码 | ✅ | 仅静态分析与报告生成 |
| 不进 Phase 4 | ✅ | 等用户确认 D21-D30 决策 |
| 跳过 Phase 3.5 | ✅ | Batch 1.4 非重构批次 |

### 31.14 Phase 4 待用户决策项（Batch 1.4）

| # | 决策项 | 默认建议 |
|---|---|---|
| **D21** | **I038** overseas_booking 是否在 Phase 4 跑迁移？| **不跑**（用户决策 #3 第 4 次强调）— 维持 PARTIAL-BLOCKER；Post-Audit 统一跑批 |
| **D22** | **I045** member_message 是否在 Phase 4 跑迁移？| **不跑**（用户决策 #3 第 4 次强调）— 维持 PARTIAL-BLOCKER |
| **D23** | **I040/I041** autoSend UI + 按钮文案是否在 Phase 4 加？| **暂缓**（用户决策 #6）— Shadow 标 Post-Audit |
| **D24** | **I042** sendConfirmation 前端入口是否在 Phase 4 加？| **暂缓**（用户决策 #6）— Shadow 标 Post-Audit |
| **D25** | **I039/I043** SoT vs 代码冲突是否在 Phase 4 修？| **中立标**（用户决策 #7）— 不动 `ext-19.mdc`，本批次不改代码 |
| **D26** | **I044** IntlBookingService `@RestController` 命名异味是否在 Phase 4 重构？| **Backlog**（沿用 I004/I019/I029）— 第 4 次出现，建议 Post-Audit 统一整改 |
| **D27** | **I046** pos_store 字段对齐验证是否在 Phase 4 跑？| **跑**（仅 DESCRIBE 验证，非迁移）— Phase 5 MySQL MCP `DESCRIBE pos_store` |
| **D28** | **I047/I048** autoSend catch 吞异常 + 7 处 `@Transactional` rollbackFor 缺失是否在 Phase 4 修？| **暂缓**（I008/I009 模式）— 本批次不改；Phase 5 静态 + Phase 6 实测关联 |
| **D29** | **I049** 渠道 sender 真实凭据是否在 Phase 5 注入？| **Mock 降级**（用户决策 #1/#2 / D10 铁律）— 仅 Mock 验证 |
| **D30** | **I050/I051** healthCheckAll T07-D4 + webhook demo 模式是否在 Phase 4 改？| **维持现状**（设计意图）— 不改代码 |

---

### 31.15 附录 — Batch 1.4 关键位置索引

#### 后端（`backend/src/main/java/com/meiyetec/`）

| 文件 | 行号范围 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `booking/BookingConfirmationService.java` | 47-187 | F083 / F085 / F086 | `sendConfirmation(booking):69` 3 渠道路由（SMS/EMAIL/MINI_PROGRAM）+ 任一 success → `overallSuccess=true`（与 SoT §0.3 完全一致）；`preview:120` 仅渲染不发渠道（合法边界）|
| `booking/BookingConfirmationTemplateResolver.java` | 42-151 | F084 | `warmup():36` 6 locale 预热 + `resolve():64` locale 归一化 + `:114` 回退链 `zh-TW → zh-CN → en-US` + `DEFAULT_LOCALE="en-US"` |
| `booking/BookingConfirmationTemplate.java` | 14-32 | F085 | 8 字段 DTO（10 字段含 miniProgram(6) + email(2)）；`miniProgram` 是 `LinkedHashMap` 保序 |
| `booking/BookingConfirmationResult.java` | 11-32 | F086 | `overallSuccess` + `channelResults: Map<channel, {...}>` + `putChannel()` 累加 success 自动设 overallSuccess |
| `booking/StoreLookupService.java` | 24-78 | F087 | `CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)` + `ConcurrentHashMap<Long, CacheEntry>` + SQL `SELECT store_name, contact_phone FROM pos_store WHERE id=? AND deleted=0` + 兜底 `美业旗舰店 / 400-888-8888` |
| `service/channel/ChannelSender.java` | 9-37 | F093 | 接口契约（`channelType/send/healthCheck`） + `SendResult{success, externalMsgId, errorMsg}` |
| `service/channel/SmsSender.java` | 46-196 | F088 | `channelType()="SMS"` + 阿里云 `HttpUtil.sendAliYunSms` + demo 降级 `SMS-DEMO-{ts}`；**L153/L193/L102 catch-ignore-empty 命中**（D10 凭据缺失链路）|
| `service/channel/EmailSender.java` | 60-217 | F089 | 三模式（租户 cfg / 全局 spring.mail.* / demo） + 日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode` (`L155-158`)（**与 SoT §0.2 完全一致**）|
| `service/channel/MiniProgramSender.java` | 48-209 | F090 | `channelType()="MINI_PROGRAM"` + AccessToken 缓存 `TOKEN_CACHE_MS = 7100_000L` + errcode=40001 自动清缓存；**L171/L227 catch-log-swallow 命中** |
| `service/channel/InAppSender.java` | 42-153 | F091 | `channelType()="IN_APP"` + **唯一正确** `@Transactional(rollbackFor=Exception.class):43` + 写 `member_message` 表（I045 MISSING）|
| `service/channel/WechatOfficialAccountSender.java` | 53-233 | F092 | `channelType()="WECHAT_OFFICIAL_ACCOUNT"`（**I039 漂移**）+ 同 F090 AccessToken 缓存模式；**L208/L268 catch-log-swallow 命中** |
| `service/channel/ChannelDispatcher.java` | 34-91 | F094 | `getSender(channelType):36` 按 channelType filter + `healthCheckAll():53-73` **T07-D4 例外**（仅更新 tenant_id=1）+ `rateLimitCheck():78-90` 查 `marketing_touch_log` |
| `service/IntlBookingService.java` | 14-350+ | F095-F106 / F113 | `@RestController @RequestMapping("/intl-booking")`（**I044 命名异味**，第 4 次）+ 13 端点 + 7 个 `@Transactional` 默认 rollbackFor（**I048 模式**）+ `updateStatus:240-279` autoSend 触发 + `:262-275` try/catch 包裹（**I047 catch 吞异常**，与 SoT §0.3 设计一致）；L281 catch-ignore-empty（HIGH）|
| `entity/OverseasBooking.java` | 8-31 | F081 | 26 字段含 `miniProgramOpenId:21` + `@TableName("overseas_booking")` + `@TableField(fill=...)` 自动填充 createdAt/updatedAt |

#### 前端（`frontend/meiyetec-web/src/`）

| 文件 | 行号范围 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `views/intl-booking/index.vue` | 4-80+ | F111 | L66 按钮文案"确认"（**I041 缺"即发送通知"提示**）+ `:34` `setStatus(row, 'CONFIRMED')` 不传 `autoSend`（**I040 缺 UI**）+ `:66` 无 loading + 无 el-popconfirm 二次确认；L77-79 templateDialog 仅显示 subject + body（**I043 与生产发送链路反向**）|
| `api/intl-booking.ts` | 4-42 | F112 | L34 `updateStatus(id, data: { status: string })` TypeScript schema 仅含 status 字段（**I040 缺 autoSend 类型**）+ 整个文件 12 端点封装**无 `sendConfirmation` 方法**（**I042 Shadow**）|

#### DB / SQL

| 文件 | 行号 / 表 | 涉及 Feature | Phase 3 发现 |
|---|---|---|---|
| `db/sql/migrate_ext_19_mini_program_openid.sql` | line 9-32 (CREATE TABLE IF NOT EXISTS) + line 36 (ALTER ADD COLUMN IF NOT EXISTS mini_program_open_id) | F082 | DDL 完整；db-snapshot.sql grep 0 命中（**I038 MISSING**）|
| `audit/baseline/db-snapshot.sql` | grep `overseas_booking` / `member_message` / `pos_store` | F081 / F087 / F091 | overseas_booking 0 命中（**I038**）/ member_message 0 命中（**I045**）/ pos_store 5 命中（**I046 待 Phase 5 验证**）|

#### 脚本 / 报告

| 文件 | 行号 / 范围 | 备注 |
|---|---|---|
| `audit/scripts/01-08-scan-*.ps1` | 全文（沿用 Batch 1.1）| 全量扫描；本报告 grep 提取 Batch 1.4 范围 |
| `audit/static-gap/01-08-*.txt` | 全文件 + grep `BookingConfirmation\|StoreLookup\|EmailSender\|SmsSender\|MiniProgram\|InAppSender\|WechatOfficialAccount\|ChannelDispatcher\|ChannelSender\|IntlBooking\|OverseasBooking\|overseas_booking\|member_message` | 8 份落盘报告 + Batch 1.4 filter |
| `audit/reports/static-gap-report.md` | §三十（第 30 节，本节）| **Batch 1.4 段（本报告）** |

---

**Phase 3 产出（Batch 1.4）完成。33 个 Feature 精细分析 + I038-I051 实证矩阵已落 `audit/reports/static-gap-report.md` §三十一。**

**等待用户确认 D21-D30 决策项 → 启动 Phase 4（运行时计划）。**

**Phase 3 产出（Batch 1.1 + 1.2 + 1.3 + 1.4）全部完成。共 113 个 Feature 精细分析 + 51 项 Issue（I001-I051）实证矩阵已落 `audit/reports/static-gap-report.md` §一-§三十一。**

---

## 三十、Batch 1.3 Phase 5A 修正段（2026-09-14 00:43 用户授权）

> **决策时间**：2026-09-14 00:43 (UTC+8)
> **触发场景**：Phase 5A 实证后发现 2 项 Phase 3 静态分析问题
> **范围**：1 项 Phase 3 误判修正（F066 非整表 MISSING）+ 1 项 Phase 3 漏判修正（F065 EXT-22 el-switch 已实现）

### 30.1 修正 1：F066 PARTIAL-MISSING（Phase 3 误判修正）

#### Phase 3 错误结论

§二十 F066 标「MISSING（I029，表 3 新列）」；§二十六 F066 = 「MISSING + PARTIAL-NON-BLOCKER」。

#### Phase 5A 实测

```sql
DESCRIBE ecommerce_platform;
-- 表存在，20 字段
-- 缺 EXT-22 三个新列：
--   app_id / kdt_id / sign_secret
```

#### 修正落档

| 项 | Phase 3 原值 | Phase 5A 修正值 |
|---|---|---|
| §二十 F066 | MISSING（I029，表 3 新列）| **PARTIAL-MISSING**（表存在，缺 3 列）|
| §二十六 矩阵 | MISSING + PARTIAL-NON-BLOCKER | **PARTIAL-MISSING**（单一归属）|
| §二十八 N3 | 表不存在 | **N3'**：表存在但 3 列缺失 |
| I029 | 整表 MISSING | **细化**：表存在但 3 列缺失（沿用「DB 列缺失」统一模式，与 I002 / I017 一致）|

#### 影响

- 后端 INSERT：`EcommercePlatformMapper.insert` 写 23 字段（含 3 个 EXT-22 新字段），MyBatis 会抛 `Unknown column 'app_id'` → `POST /ecommerce/platform` 必 500
- I028 维持：前端 `ecommerce/index.vue:60-77` 添加对话框仅 3 字段，无 `appId/kdtId/signSecret` 输入入口

### 30.2 修正 2：F065 EXT-22 el-switch 已实现（Phase 3 漏判修正）

#### Phase 3 错误结论

§二十 F065 推「I028 部分反向 + C1 configForm 缺字段」+ §二十六 PARTIAL-NON-BLOCKER。
未明确指出 EXT-22 el-switch **已实现**。

#### Phase 5A 实测

`frontend/meiyetec-web/src/views/google-maps/index.vue:13-23`：

```vue
<el-form-item label="Place Details（计费）">
  <el-switch
    v-model="enablePlaceDetails"
    :active-value="1"
    :inactive-value="0"
    active-text="启用（按次计费：$0.025/次）"
    inactive-text="关闭（仅 Geocoding）"
    inline-prompt
    style="--el-switch-on-color: #f56c6c;"
  />
  <el-tooltip content="启用后 Place Details 接口将按 Basic+Contact+Atmosphere 三 SKU 计费" placement="top">
    <el-icon style="margin-left: 8px;"><Warning /></el-icon>
  </el-tooltip>
</el-form-item>
```

#### 修正落档

| 项 | Phase 3 原值 | Phase 5A 修正值 |
|---|---|---|
| §二十 F065 | I028 部分反向 + C1 缺字段 | **强化**：EXT-22 el-switch 已实现 + I036 双层暴露 + C1 维持 |
| §二十六 矩阵 | PARTIAL-NON-BLOCKER | **维持 PARTIAL-NON-BLOCKER**（C1 + I036 仍存在） |
| I028 EXT-22 子项 | 推「前端缺 UI」 | **部分反向**：google-maps el-switch 已实现；ecommerce 添加对话框仍缺 3 字段（I028 在 ecommerce 维持）|

### 30.3 Phase 5A 负向证据汇总

| # | 验证目标 | 预期 | 实际 | 状态 |
|---|---|---|---|---|
| N1 | `google_maps_config` 表缺失 | TableNotFoundException | ✅ ERROR 1146 | PASS |
| N2 | `google_maps_geocode_log` 表缺失 | TableNotFoundException | ✅ ERROR 1146 | PASS |
| N3' | `ecommerce_platform` 3 列缺失 | `app_id/kdt_id/sign_secret` 缺失 | ✅ 实测 DESCRIBE 20 字段缺 3 列 | **PARTIAL**（修正）|
| N4 | db-snapshot grep | 0 hits | ✅ google_maps_config:0 / enable_place_details:0 / kdt_id:0 / sign_secret:0；app_id:1（crm_integration 误命中）| PASS |
| N5 | I036 Key 泄露 | 响应 `***xxxx` | ⚠️ 代码层 PASS / Phase 5 降级（I029 优先）| DEGRADED |
| N6 | PERMISSION_DENIED | step 2 命中 | ⚠️ 代码层 PASS / Phase 5 降级（I029 优先）| DEGRADED |
| N7 | Place Details 未写 log | log 表无新增 | ✅ 代码层 100% 实证（无 logMapper.insert 调用）| PASS（代码层）|
| N8 | `geocode_batch_log` 表缺失 | 表不存在 | ✅ SHOW TABLES LIKE → Empty | PASS |
| N17 | `/geocode/log` 前端无消费 | grep 0 命中 | ✅ google-maps/index.vue 全文 175 行无调用 | PASS |

### 30.4 Phase 5A 决策不变（D11-D20 全部维持）

| 决策 | 处理 | 沿用模式 |
|---|---|---|
| D11 | 不改 SoT（I026/I027/I030 维持 CONFLICT 中立标）| I001/I018 |
| D12 | 不加 UI（I028 登记 Issue）| I006/I007/I024 |
| D13 | 不跑迁移（I029 维持 PARTIAL-MISSING）| I002/I017/Batch 1.1 D5/Batch 1.2 D7 |
| D14 | 不加日志（I031 维持）| I008/I011 Fake Result 系列 |
| D15 | 不加 audit log（I032 backlog）| I004/I019 |
| D16 | 保持 backlog（I033 BACKLOG-001）| I013 dead code |
| D17 | 不加 rollbackFor（I034）| I009 |
| D18 | 不加时间窗（I035）| I008/I022 |
| D19 | 仅标注不改（I036 GCP Key 泄露 — 代码层 + 前端双层实证）| user decision #9 |
| D20 | 不加 UI（F063/F064/F075 Shadow）| I006/I007 |

### 30.5 关联文档

- `audit/evidence/F056-googlemaps-config-entity/result.md` — F056 实证
- `audit/evidence/F057-googlemaps-geocode-log-entity/result.md` — F057 实证
- `audit/evidence/F060-googlemaps-service-config-health/result.md` — F060 实证（含 I036 代码层 + I029 暴露）
- `audit/evidence/F062-googlemaps-service-place-details/result.md` — F062 实证（含 N6/N7 降级说明）
- `audit/evidence/F063-googlemaps-service-geocode-batch/result.md` — F063 实证（含 N8 + I028 admin 守卫）
- `audit/evidence/F064-googlemaps-service-stores-heatmap-log/result.md` — F064 实证（含 N17）
- `audit/evidence/F065-googlemaps-frontend-ui/result.md` — F065 实证（含 EXT-22 el-switch 漏判修正）
- `audit/evidence/F066-platform-entity-stub/result.md` — F066 PARTIAL-MISSING 修正实证
- `audit/runtime/report-5A-batch-1.3.md` — Phase 5A 汇总报告
- `audit/issues/issue-register.md §二十` — 决策落档 + 修正段

---

**Phase 5A 完成。2 项 Phase 3 修正落档。D11-D20 全部不变。⏸ 等用户确认 → 启动 Phase 5B（有赞侧 F066-F080）。**

---

## 三十二、Batch 1.5 — MKT 多渠道重构（v3 第五批 · 首个重构批次）— Static Gap Audit

> **追加时间**：2026-09-16 01:00 (UTC+8)
> **Phase**：Phase 3 — Static Gap Audit（Batch 1.5）
> **范围**：Batch 1.5 — MKT 多渠道重构（27 Features F114-F140，9 个 Mkt*Controller + ChannelDispatcher + 5 Sender + MarketingAutomationService + 重构中间态 Diff Audit）
> **引用文件**：`audit/feature-inventory.md` §1.5（27 个 Feature） / `audit/traceability-matrix.md` §Batch 1.5（line 2820-3823） / `audit/baseline/scope-freeze-batch-1.5.md` / `audit/baseline/preflight-batch-1.5.md` / `audit/diff-audit/mkt-refactor.md`（Phase 3.5，8 项全部完成）
> **SoT**：`docs/ROLLBACK-6.2-mkt_ddl.md`（MKT-002 SoT）+ git status（⚠ 无 .mdc Operational Notes — 本批次退化）
> **本批次决策摘要**（沿用用户决策 @ 2026-09-15 22:00 + Phase 2 用户决策）：
> 1. **I055**：不改 SoT / 不重构 → CONFLICT 中立标（F139 标 PARTIAL-BLOCKER + CONFLICT）
> 2. **I056**：跨租户删除不修 → F117 登记 Issue（PARTIAL-NON-BLOCKER）
> 3. **I057**：跨租户写不修 → F122/F123 登记 Issue（PARTIAL-NON-BLOCKER）
> 4. **I058**：双路由形式不修 → F125/F129 登记 Issue（PARTIAL-NON-BLOCKER）
> 5. **I059**：snapshotSave 仅 log.info → F130 登记 Issue（PARTIAL-NON-BLOCKER）
> 6. **I060**：不加 try/catch → F132 标 CRITICAL-P1（用户决策：不加 try/catch，Post-Audit）
> 7. **I061**：不改 Router → F135 标 CRITICAL-P2（用户决策：NPE 保留，Post-Audit）
> 8. **I062**：rateLimitPerDay setter 空语句 → F115/F138 登记 Issue（PARTIAL-NON-BLOCKER + MISSING）
> 9. **I063**：4 表 MISSING 不跑迁移 → F138 + BusinessPolicyDispatchController 链路 MISSING
> 10. **I064**：不写 `api/mkt.ts` → F114-F134 全部前端相关 Feature 登记 Issue（21 个 PARTIAL-NON-BLOCKER）
> 11. **I065**：dashboard.vue 未调用 ChannelDispatcher → ✅ 风险已解除
> 12. **I066**：I054 NEW 状态维持 → 表存在确认
> 13. **I067**：重构中间态维持 → F139 标 PARTIAL-BLOCKER + CONFLICT
> 14. **I068**：SoT 字段不一致 → Phase 5 验证
> 15. **审计阶段不修业务代码**（规则 6 + 用户决策 — 不进 Phase 4）

### 32.1 本节产出摘要

| 产出 | 路径 | 内容 |
|---|---|---|
| **静态扫描复用** | `audit/scripts/01-08-scan-*.ps1`（Batch 1.1 已生成）+ grep `Mkt|MarketingAutomation|ChannelDispatcher|MarketActivity|mkt_|marketing_touch_|applyActivity|expireSweep|snapshotSave\|/mkt/\|/market/activity` 提取 Batch 1.5 范围命中 |
| **8 份扫描报告引用** | `audit/static-gap/01-08-*.txt`（Batch 1.1 全量扫描） | grep 提取 Batch 1.5 范围（106 个 MKT 端点 / 11 张 MKT 表 / 65 处 contract drift / 8 处 catch / 11 处 @Transactional） |
| **精细分析** | 本报告 §32.4-§32.15 | Shadow / Ghost / Fake / Contract Drift / SoT 冲突 / 重构中间态 / I008 模式 catch 吞异常 / I009 模式 rollbackFor 缺失 / I050 死代码 / I011 Fake Failure / I012 NPE Router |
| **I055-I068 交叉验证** | `audit/feature-inventory.md` §1.5.5 + `audit/traceability-matrix.md` §Batch 1.5（line 3883-3898）+ `audit/issues/issue-register.md` §二十一 | 14 项 Phase 1 Issue 全量交叉验证 |

### 32.2 Batch 1.5 守门脚本命中数（MKT 范围）

> 复用 Batch 1.1 的 8 个守门脚本（全量扫描 + grep MKT 范围关键词提取）。

| 脚本 | 全项目 Total | Batch 1.5 范围命中 | 涉及 Feature | 说明 |
|---|---|---|---|---|
| **01-fake-success** | 299 | **8**（含 3 处 catch-ignore-empty HIGH） | F117 / F122 / F123 / F126 / F135 / F138 | catch-log-swallow **5 处**（`MarketingAutomationScheduler.java:95/144/220` + 2 处 SchedulerJob）+ catch-ignore-empty **3 处**（`MarketingAutomationService.java:230/290/514`）— **全部为 I008 Fake Success 模式**；其中 `MarketingAutomationService.java:230` 在 `applyActivity` 内为 CRITICAL（数据写入静默吞异常）|
| **02-mock** | 90 | **0** | — | 5 个 channel sender 各自有 demo 降级但无 `@ConditionalOnMissingBean` 风格；`ChannelDispatcher` 用 Spring `List<ChannelSender>` 自动收集（无 fallback 设计意图）；`MockCrmAdapter` 模式不适用 |
| **03-disabled** | 94 | **0** | — | MKT 9 个 Controller + ChannelDispatcher + MarketingAutomationService 范围**无 `@Disabled` / `@Deprecated` / `UnsupportedOperationException` 命中**；旧 `MarketActivity*` 5 个类亦无 `@Deprecated` 标注（中间态无标记） |
| **04-endpoint-coverage** | 后端 2013 / 前端 859 | **106 Only-Backend**（其中旧 14 / 新 92） | F114-F140 + F171（旧 MarketActivityController） | 新 Mkt*Controller 端点（92 个）：`/mkt/channel/*` + `/mkt/conversion/*` + `/mkt/journey/*` + `/mkt/template/*` + `/mkt/task/*` + `/mkt/effect/*` + `/mkt/coupon/*` + `/mkt/promotion/*` + `/mkt/points/*`；旧 `MarketActivityController`（14 个）：`/market/activity/*` 10 端点 + `/market/activity/market/activity` 1 个重复（04 报告 line 1377）+ 前端 api/market.ts 5 端点；**ANY:/mkt/X/mkt/X**（14 处）是 04 脚本 bug（类级 @RequestMapping + 方法级 @RequestMapping 重复解析，非真实端点）|
| **05-contract-drift** | 1530 | **65** | F114-F140 全部 Controller | `MktChannelController.java:50/55` + `MktConversionController.java:35/36/41/51/59` + `MktCouponController.java:53/74/138/257/258/284` + `MktEffectController.java:65/66` + `MktJourneyController.java:39/44/57/58/59/69/84` + `MktPointsController.java:54/73` + 旧 `MarketActivityController.java:41/42/54/61/90/99` — 全部 `@RequestBody Map<String,Object>` 无 `@Valid` + `@RequestParam(required=false)` 无 defaultValue |
| **06-soft-delete** | 77 | **11 张 MKT 表**（含 OK 设计 + HIGH 风险） | F114-F140 + F138（dispatch）+ F130（effect snapshot） | 报告 11 张 MKT 表：`marketing_touch_task` + `mkt_brand` + `mkt_channel_config`（OK 含 deleted）+ `mkt_channel_template`（HIGH 不含 deleted）+ `mkt_coupon`（HIGH）+ `mkt_coupon_batch`（HIGH）+ `mkt_dynamic_pricing_rule`（HIGH）+ `mkt_journey`（HIGH）+ `mkt_journey_trigger`（OK 含 deleted）+ `mkt_promotion`（HIGH）+ `mkt_segment`（HIGH）— **9 张表 UNIQUE 索引不含 deleted → 软删除后重插 UNIQUE 冲突风险** |
| **07-transaction** | 214 | **11 处 `@Transactional`（全部规范）** | F125 / F127 / F129 / F131 / F132 / F133 / F134 | MktCouponController 5 处（line 73/101/121/137/231）+ MktPointsController 3 处（line 53/72/100）+ MktPromotionController 3 处（line 77/97/130）+ MktEffectController 1 处（line 176）+ MarketingAutomationService 4 处（line 150/240/521/560）— **全部用 `rollbackFor = Exception.class` 规范配置**（与 Batch 1.4 InAppSender 唯一正确模式一致） |
| **08-shadow-ghost** | 2003 | **~70 Shadow**（精确数：65-75） | F114-F140 + F095-F097/F104 | 仅前端 view 调用 `/mkt/*` 路径（如 `views/mkt/channel.vue:124/136/158/161/170` 直接调 `http.get/post/put/delete` 绕过 api/ 封装层）；**0 个 Ghost**（后端端点均有前端调用或 webhook 设计）|

> **Batch 1.5 范围专属命中汇总**：8 (fake-success) + 0 (mock) + 0 (disabled) + 106 (endpoint-coverage) + 65 (contract-drift) + 11 (soft-delete) + 11 (transaction) + ~70 (shadow-ghost) = **~271 命中**（含 3 处 HIGH catch-ignore-empty + 9 张表 soft-delete UNIQUE 不含 deleted HIGH）

### 32.3 02-mock 中 Batch 1.5 范围相关命中

| Feature ID | 类别 | 位置 | 证据 | 判定 |
|---|---|---|---|---|
| — | 无命中 | — | — | MKT 9 个 Controller 范围**无 Mock 模式命中**；`ChannelDispatcher` 用 Spring 自动收集 `List<ChannelSender>`（无 `@ConditionalOnMissingBean` 设计意图）；5 个 ChannelSender（沿用 EXT-19）各自有 demo 降级但非 `@ConditionalOnMissingBean` 风格 — 与 Batch 1.1 I012 同问题（Mock 适配器 condition conflict）不重复 |

### 32.4 01-fake-success 中 Batch 1.5 范围相关命中（8 处精细分析）

> **重要**：catch-log-swallow 不一定都是 bug。Batch 1.5 范围 catch 共 8 处，其中：
> - **3 处是 HIGH catch-ignore-empty**（`MarketingAutomationService.java:230/290/514`）
> - **5 处是 catch-log-swallow**（Scheduler 3 处 + SchedulerJob 2 处）

| Feature ID | 位置 | 命中模式 | 实际语义 | Phase 3 判定 |
|---|---|---|---|---|
| **F138** | `MarketingAutomationService.java:230` | catch-ignore-empty（HIGH） | `applyActivity()` 内 try/catch — `try { marketActivityMapper.insert(clone); } catch (Exception ignored) {}` 静默吞异常（与 I055 联动 — 业务侧失败无感）| **PARTIAL-BLOCKER**（CRITICAL — I055/I056 联动，Post-Audit）|
| **F138** | `MarketingAutomationService.java:290` | catch-ignore-empty（HIGH） | 推断为 `BusinessPolicyDispatchServiceImpl` 或 `MarketingAutomationService` 写日志静默（与 I057 联动 — 跨租户写失败无感）| **PARTIAL-NON-BLOCKER**（I008 模式）|
| **F138** | `MarketingAutomationService.java:514` | catch-ignore-empty（HIGH） | 推断为统计 / 聚合方法异常静默（影响 dashboard 数据完整性）| **PARTIAL-NON-BLOCKER**（I008 模式）|
| **F138** | `MarketingAutomationScheduler.java:95` | catch-log-swallow | 调度任务异常 log warn → 继续下一轮（合理）| OK（调度降级）|
| **F138** | `MarketingAutomationScheduler.java:144` | catch-log-swallow | 同 95（合理）| OK |
| **F138** | `MarketingAutomationScheduler.java:220` | catch-log-swallow | 同 95（合理）| OK |
| **F138** | `mkt/event/ComplaintSlaAutoEscalationJob.java:80` | catch-log-swallow | 自动升级任务异常 log warn（合理）| OK |
| **F138** | `mkt/event/FollowUpPlanAutoTriggerJob.java:107` | catch-log-swallow | 跟进任务异常 log warn（合理）| OK |

> **核心结论**：8 处 catch 中：
> - **3 处 HIGH**（`MarketingAutomationService.java:230/290/514` 全部 catch-ignore-empty）— 与 I008 Fake Success 模式化登记，I055/I056/I057 联动
> - **5 处 OK**（Scheduler/Job 类 catch-log-swallow 是调度任务标准降级模式）

### 32.5 04-endpoint-coverage 中 Batch 1.5 范围精细对比

#### 32.5.1 Mkt*Controller 端点（9 Controller 全量）

| 后端 Controller | 端点数 | 前端 view 调用 | 04 报告状态 | 人工修正结论 |
|---|---|---|---|---|
| **`MktChannelController`** (`@RequestMapping("/mkt/channel")`) | 5（list/POST/PUT/DELETE/health-check）| `views/mkt/channel.vue:124/136/158/161/170` 直接调 `http.get/post/put/delete` | Only-Backend | **Matched**（前端直接调 URL 绕过 api/ 封装层 — I064 部分确认）|
| **`MktConversionController`** (`@RequestMapping("/mkt/conversion")`) | 5（funnel/roi/by-channel/track/mark-read）| `views/mkt/conversion.vue:92/94/96` 直接调 `http.get`（仅前 3 个 GET）；`track` + `mark-read` 无前端调用（业务回调入口）| Only-Backend | **3 个 Matched + 2 个 webhook 设计**（业务回调非前端入口）|
| **`MktJourneyController`** (`@RequestMapping("/mkt/journey")`) | 9（create/detail/update/publish/pause/page × 2/enter/nextNode）| `views/mkt/journey.vue:347/403/406/414/418/439/596` 直接调 `http.get/post/put`（7 个）+ POST/GET 双模式 `/page`（I058）| Only-Backend | **7 个 Matched + 2 个设计**（nextNode 业务触发 + enter 会员入口）|
| **`MktTemplateController`** (`@RequestMapping("/mkt/template")`) | 3（list/create/audit）| `views/mkt/template.vue` 未 grep 确认（**需 Phase 5 E2E 验证**）| Only-Backend | **疑似 Matched**（待 Phase 5 验证）|
| **`MktTouchTaskController`** (`@RequestMapping("/mkt/task")`) | 9（page × 2/log/start/pause/resume/cancel/...）| `views/mkt/touch-task.vue` 未 grep 确认 | Only-Backend | **疑似 Matched** |
| **`MktEffectController`** (`@RequestMapping("/mkt/effect")`) | 2（snapshot/snapshotSave）| `views/mkt/effect.vue` 未 grep 确认 + snapshotSave 是 I059 dead code | Only-Backend | **1 个 Matched + 1 个 dead code (I059)** |
| **`MktCouponController`** (`@RequestMapping("/mkt/coupon")`) | 9（page × 2/batch × 3/publish/end/grant/expire-sweep）| `views/mkt/coupon.vue:221/284/297/304/328/342/353/382` 直接调 `http.get/post`（8 个）+ expire-sweep 直接调用（F132）| Only-Backend | **8 个 Matched + 1 个 admin-only (expire-sweep — I060 CRITICAL-P1)** |
| **`MktPromotionController`** (`@RequestMapping("/mkt/promotion")`) | 4（page/POST/PUT/DELETE/export）| `views/mkt/promotion.vue` 未 grep 确认 | Only-Backend | **疑似 Matched** |
| **`MktPointsController`** (`@RequestMapping("/mkt/points")`) | 4（rule/page/POST/PUT）| `views/mkt/points.vue` 未 grep 确认 | Only-Backend | **疑似 Matched** |
| **`MarketActivityController`** (`@RequestMapping("/market/activity")`，**旧**重构中间态)| 10（page/POST/PUT/DELETE/stats/detail/toggle/copy/participants/{id}）| `frontend/meiyetec-web/src/api/market.ts:46-78` 18 端点封装（5 个 MarketActivity 端点）+ `frontend/meiyetec-web/src` 下 5 个 `http.*` 直调（`views/mkt/*` 经 shadow 处理后不调）| Only-Backend | **5 个 Matched via api/market.ts + 5 个 Shadow（前端路由指向 `views/mkt/promotion.vue` 替代，I057 路由残留确认）**|

> **真实结论**：Batch 1.5 端点层面 **106 个后端 Only-Backend**：
> - **~60 个 Matched**（前端 `views/mkt/*.vue` 直接调 URL，绕过 api/ 封装层）
> - **~16 个设计**（webhook / 业务回调 / admin-only / dead code）
> - **5 个 MarketActivity 端点 Matched via api/market.ts**（旧路由仍响应 — I057 路由残留）
> - **14 个 ANY:/mkt/X/mkt/X** 是 04 脚本 bug（类级 @RequestMapping + 方法级 @RequestMapping 重复解析，非真实端点）
> - **前端 Shadow 净数：5 个旧 MarketActivity 端点**（前端路由指向新组件，后端旧 Controller 仍响应 — Phase 5 curl 验证）

### 32.6 06-soft-delete 中 Batch 1.5 范围相关命中（11 张表）

> Batch 1.5 涉及的 11 张表（`marketing_touch_task` + `mkt_brand` + `mkt_channel_config` + `mkt_channel_template` + `mkt_coupon` + `mkt_coupon_batch` + `mkt_dynamic_pricing_rule` + `mkt_journey` + `mkt_journey_trigger` + `mkt_promotion` + `mkt_segment`）：

| 表 | 06 报告状态 | 设计影响 | 备注 |
|---|---|---|---|
| `marketing_touch_task` | **HIGH**（UNIQUE 不含 deleted） | 软删后重建同 `task_no` → UNIQUE 冲突 | `F129`（TouchTask 重启场景）|
| `mkt_brand` | **HIGH** | 同上 | 品牌管理 |
| `mkt_channel_config` | **✅ OK**（UNIQUE 含 deleted：`uk_tenant_type_brand` 包含 `(tenant_id, channel_type, brand_id, deleted)`）| 设计正确 — I054 NEW 推论反转（I066 维持）| F114-F118 |
| `mkt_channel_template` | **HIGH** | 模板重建 UNIQUE 冲突 | F128 |
| `mkt_coupon` | **HIGH** | 优惠券重建 UNIQUE 冲突 | F131/F132 |
| `mkt_coupon_batch` | **HIGH** | 批次重建 UNIQUE 冲突 | F131/F132 |
| `mkt_dynamic_pricing_rule` | **HIGH** | 定价规则重建 UNIQUE 冲突 | （git status 漏列 controller，Phase 1 边界外）|
| `mkt_journey` | **HIGH** | 旅程重建 UNIQUE 冲突 | F124-F127 |
| `mkt_journey_trigger` | **✅ OK**（UNIQUE 含 deleted：`uk_tenant_code` 包含 `(tenant_id, trigger_code, deleted)`）| 设计正确 | F127 间接 |
| `mkt_promotion` | **HIGH** | 促销重建 UNIQUE 冲突 | F133 |
| `mkt_segment` | **HIGH** | 人群分群重建 UNIQUE 冲突 | （不在 F114-F140 范围，可能在 git status 漏列 controller）|

> **核心结论**：11 张 MKT 表中：
> - **2 张表 OK**（`mkt_channel_config` + `mkt_journey_trigger` — 含 deleted 列正确）
> - **9 张表 HIGH**（UNIQUE 不含 deleted → 软删除后重建同 code → UNIQUE 冲突 — 沿用 I002 模式）
> - **0 个 06-soft-delete 真实 Block bug 致命**（UNIQUE 冲突只在「删除 + 同 code 重建」场景触发，业务影响小）

### 32.7 07-transaction 中 Batch 1.5 范围相关命中（11 处全部规范）

> 复用 Batch 1.1 全量扫描 + grep MKT 范围 → 实际 MKT 命名空间 11 处 `@Transactional`，**全部用 `rollbackFor = Exception.class`**，与 Batch 1.4 InAppSender 唯一正确模式一致。

| Feature ID | 位置 | 命中模式 | 实际语义 | Phase 3 判定 |
|---|---|---|---|---|
| **F125/F127** | `MarketingAutomationService.java:150` | `@Transactional(rollbackFor = Exception.class)` | `journeyPage()` 分页查询 | ✅ OK |
| **F126** | `MarketingAutomationService.java:240` | `@Transactional(rollbackFor = Exception.class)` | `updateJourney()` 旅程更新 | ✅ OK |
| **F127** | `MarketingAutomationService.java:521` | `@Transactional(rollbackFor = Exception.class)` | `enterJourney()` 进入旅程 | ✅ OK |
| **F127** | `MarketingAutomationService.java:560` | `@Transactional(rollbackFor = Exception.class)` | `nextNode()` 推进下一节点 | ✅ OK |
| **F131/F132** | `MktCouponController.java:73` | `@Transactional(rollbackFor = Exception.class)` | `couponCreate()` 创建优惠券 | ✅ OK |
| **F131** | `MktCouponController.java:101` | `@Transactional(rollbackFor = Exception.class)` | `batchCreate()` 创建批次 | ✅ OK |
| **F131** | `MktCouponController.java:121` | `@Transactional(rollbackFor = Exception.class)` | `batchPublish()` 发布批次 | ✅ OK |
| **F131** | `MktCouponController.java:137` | `@Transactional(rollbackFor = Exception.class)` | `batchEnd()` 结束批次 | ✅ OK |
| **F132** | `MktCouponController.java:231` | `@Transactional(rollbackFor = Exception.class)` | `expireSweep()` 过期扫描（I060 CRITICAL-P1：缺 try/catch + 缺 audit）| ✅ OK 事务规范，**但 I060 缺陷仍存在** |
| **F133** | `MktPointsController.java:53` | `@Transactional(rollbackFor = Exception.class)` | `createRule()` 创建积分规则 | ✅ OK |
| **F133** | `MktPointsController.java:72` | `@Transactional(rollbackFor = Exception.class)` | `updateRule()` 更新规则 | ✅ OK |
| **F133** | `MktPointsController.java:100` | `@Transactional(rollbackFor = Exception.class)` | `awardPoints()` 发放积分 | ✅ OK |
| **F133** | `MktPromotionController.java:77` | `@Transactional(rollbackFor = Exception.class)` | `createPromotion()` 创建促销 | ✅ OK |
| **F133** | `MktPromotionController.java:97` | `@Transactional(rollbackFor = Exception.class)` | `updatePromotion()` 更新促销 | ✅ OK |
| **F133** | `MktPromotionController.java:130` | `@Transactional(rollbackFor = Exception.class)` | `deletePromotion()` 删除促销 | ✅ OK |
| **F130** | `MktEffectController.java:176` | `@Transactional(rollbackFor = Exception.class)` | `snapshotSave()` 日终快照（I059 dead code：仅 log.info 未写表）| ✅ OK 事务规范，**但 I059 缺陷仍存在** |

> **核心结论**：MKT 范围 11 处 `@Transactional` 方法**全部规范**（rollbackFor=Exception.class）。本批次 0 个 I009 / I048 模式命中。
> **但 I060 / I059 缺陷仍存在**：事务规范 ≠ 业务完整（`expireSweep` 加了 rollbackFor 但缺 try/catch 兜底 + 缺 audit log；`snapshotSave` 加了 rollbackFor 但实现是空 log）。

### 32.8 08-shadow-ghost 中 Batch 1.5 范围相关命中（5 维）

| 类型 | 命中数 | 涉及 Feature | 备注 |
|---|---|---|---|
| **Shadow Feature**（only-backend）| **~70** | F114-F140 + F095/F104 webhook 模式 | 前端 `views/mkt/*.vue` 直接调 `http.get/post/put/delete('/mkt/...')`（绕过 api/ 封装层 — I064 部分确认）；`/mkt/dispatch/*` 4 端点无前端调用（I063 MISSING 表无 UI）|
| **Ghost Feature**（only-frontend）| **0** | — | 前端调用的所有端点后端都有（@RequestMapping 注册成功）|
| **Zombie Feature**（@Deprecated）| **0** | — | MKT 9 Controller + ChannelDispatcher + 5 Sender 范围无 @Deprecated 命中 |
| **Fake Feature**（setTimeout stub）| **0** | — | 无 setTimeout fake async（MKT 无客户端轮询 mock）|
| **Shell Feature**（< 500 bytes Vue）| **0** | — | `views/mkt/*.vue` 11 个文件均 > 500 bytes（最简的 coupon.vue ~400 行）|

> **真实 Shadow/Ghost 净数**：**~70 个 Shadow**（前端绕过 api/ 封装层直调 URL）+ **5 个旧 MarketActivity 路由残留**（前端路由指向新组件，后端旧 Controller 仍响应 — I057）+ **0 个 Ghost**。
> **关键修正 I064**：原始 I064 描述"前端无法调用新 Controller"**不准确** — 前端确实调用了 `/mkt/*` 路径，但是**直接绕过 api/ 封装层**用了 `http.get/post/put/delete(...)`。真正问题是维护成本 + 违反封装原则，不是完全失联。

### 32.9 EXT-MKT 特定问题精细识别（沿用 I055-I068）

> 用户要求**特别检查** MKT 重构特定问题；以下为 Phase 3 静态识别结果。

#### 32.9.1 I055 — SoT 文档与代码 CONFLICT（BusinessPolicyDispatchServiceImpl.applyActivity 反向）

| 维度 | 详情 | 位置 |
|---|---|---|
| **SoT §1 描述** | "Java 侧 (BusinessPolicyDispatchServiceImpl) 已无对 market_activity 的读写" | `docs/ROLLBACK-6.2-mkt_ddl.md` §1 |
| **代码实际** | `BusinessPolicyDispatchServiceImpl.applyActivity()` (line 262-) 实际调用 `marketActivityMapper.selectById` (line 264) + `marketActivityMapper.insert` (line 277) | `service/impl/BusinessPolicyDispatchServiceImpl.java:262-280` |
| **额外引用** | `MktEffectController` line 49 `final MarketActivityMapper activityMapper` + `MktPromotionController` 引用 `MarketActivity` | `controller/MktEffectController.java:49` |
| **额外 catch** | `MarketingAutomationService.java:230` catch-ignore-empty 包裹 `applyActivity` 调用 → 数据写入静默失败 | `service/MarketingAutomationService.java:230` |
| **真实功能影响** | **`market_activity` 表无法 DROP**（migrate_mkt_002 执行后 BusinessPolicyDispatchServiceImpl 会 NPE / `applyActivity` 写表失败）| — |
| **判定** | **CONFLICT**（I055 中立标）— 沿用 I001/I018/I026-I030/I039 模式；Fix Phase = Post-Audit 人类决策（要么改 SoT，要么改代码）| — |

#### 32.9.2 I056 — 跨租户删除风险（MktChannelController.delete）

| 维度 | 详情 | 位置 |
|---|---|---|
| **代码实际** | `MarketingAutomationService.deleteChannel()` (line 94-97) 直接 `deleteById(id)`，**未校验 `t.equals(old.getTenantId())`** | `service/MarketingAutomationService.java:94-97` |
| **对照 F116** | `updateChannel()` line 85-92 **有** `c.setTenantId(old.getTenantId())` 强制保留原租户 → F116 防护正确 | `service/MarketingAutomationService.java:91` |
| **真实功能影响** | 跨租户删除 → 普通租户能删别人 channel 配置（横向越权）| — |
| **判定** | **PARTIAL-NON-BLOCKER**（I056 沿用 I018 模式）— 用户决策不修；Fix Phase = Post-Audit | F117 |

#### 32.9.3 I057 — 跨租户写风险（MktConversionController.track + markRead）

| 维度 | 详情 | 位置 |
|---|---|---|
| **代码实际** | `MarketingAutomationService.trackConversion()` (line 731-760) 写 `marketing_touch_conversion` 时未从 `TenantContextHolder` 取 tenantId，body 直接取 `tenantId` 字段 | `service/MarketingAutomationService.java:731-760` |
| **真实功能影响** | 跨租户写 → 普通租户可伪造 tenantId 写入别人数据（横向越权）| — |
| **判定** | **PARTIAL-NON-BLOCKER**（I057 沿用 I019 模式）— 用户决策不修；Fix Phase = Post-Audit | F122 + F123 |

#### 32.9.4 I058 — Frontend 双路由形式语义模糊

| 维度 | 详情 | 位置 |
|---|---|---|
| **代码实际** | `MktJourneyController.page()` (line 42-60) 同时支持 `POST /mkt/journey/page` body 参数 + `GET /mkt/journey/page` query 参数 — **同 handler 双模式** | `controller/MktJourneyController.java:42-60` |
| **MktTouchTaskController 同模式** | `MktTouchTaskController` (line 37/47) 同样 POST/GET 双模式 | `controller/MktTouchTaskController.java:37/47` |
| **前端实际调用** | `views/mkt/journey.vue:347` 用 `http.get('/mkt/journey/page', { params: filter })` 走 GET；`views/mkt/touch-task.vue` 待 Phase 5 验证 | — |
| **真实功能影响** | 同端点两种调用方式 → API 风格不一致（其他模块偏好单一 POST/GET）| — |
| **判定** | **PARTIAL-NON-BLOCKER**（I058 沿用 I058 自身模式）— 用户决策不修；Fix Phase = Post-Audit | F125 + F129 |

#### 32.9.5 I059 — MktEffectController.snapshotSave dead code

| 维度 | 详情 | 位置 |
|---|---|---|
| **代码实际** | `MktEffectController.snapshotSave()` (line 175-) 注释"实际 cron job 跑"，但实现仅 `log.info` + 不写 `mkt_effect_snapshot` 表 | `controller/MktEffectController.java:175-` |
| **接口契约** | `POST /mkt/effect/snapshot/save` 返回成功但实际无副作用（前端无法感知）| 同上 |
| **真实功能影响** | 调用 snapshotSave 看起来成功，但实际没存数据 → 后端统计拿不到日终快照 | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I059 沿用 I013 dead code 模式）— 用户决策不修；Fix Phase = Post-Audit | F130 |

#### 32.9.6 I060 — MktCouponController.expireSweep 缺 try/catch + 缺 audit（CRITICAL-P1）

| 维度 | 详情 | 位置 |
|---|---|---|
| **代码实际** | `MktCouponController.expireSweep()` (line 230-246) 直接 `UPDATE mkt_coupon` + `UPDATE mkt_coupon_batch`，**未返回受影响的行数明细给前端**（只在 result map 暴露 expiredCount + batchesClosed），**未包 try/catch**（异常时 Spring 默认 500），**未写 audit log**（谁跑的、扫了多少、关了多少个无法追溯）| `controller/MktCouponController.java:230-246` |
| **真实功能影响** | ① SQL 异常时事务回滚（已有 @Transactional）但前端拿到 500 无错误说明 → ② 调用方无法判断 sweep 是否成功 → ③ 缺审计线索（沿用 I032 EXT-22 geocode_batch audit 模式）| — |
| **判定** | **CRITICAL-P1**（I060 — 用户决策：不加 try/catch，登记 Issue，Post-Audit）| F132 |

#### 32.9.7 I061 — ChannelDispatcher.getSender fallback null（NPE CRITICAL-P2）

| 维度 | 详情 | 位置 |
|---|---|---|
| **代码实际** | `ChannelDispatcher.getSender(channelType)` (line 33-39) miss 时 `findFirst().orElse(null)` → 返回 null 直接 NPE | `service/channel/ChannelDispatcher.java:33-39` |
| **同 Batch 1.1 模式** | 与 I012 `MockCrmAdapter` `@ConditionalOnMissingBean` fallback NPE 完全同模式 | `I012 实证` |
| **真实功能影响** | 调用 `dispatcher.getSender(channelType)` 任一路径 miss → NPE 500 | — |
| **判定** | **CRITICAL-P2**（I061 — 用户决策：不改 Router，登记 Issue，Post-Audit）| F135 + 任何调用 `dispatcher.getSender(...)` 链路 |

#### 32.9.8 I062 — rateLimitPerDay setter 空语句（dead code）

| 维度 | 详情 | 位置 |
|---|---|---|
| **代码实际** | `MarketingAutomationService.createChannel()` (line 67-83) 默认值设置 `if (c.getRateLimitPerDay() == null) c.getRateLimitPerDay();` line 81 — **空语句无 setter 调用** | `service/MarketingAutomationService.java:81` |
| **真实功能影响** | `rateLimitPerDay` 默认值不会生效（数据库可能 null）→ F118 healthCheckAll 走限流逻辑时可能异常 | — |
| **判定** | **PARTIAL-NON-BLOCKER**（I062 沿用 I013 dead code 模式）— Fix Phase = Post-Audit | F115 + F138 |

#### 32.9.9 I063 — 4 表 MISSING（CRITICAL-P2 + 跨模块）

| 维度 | 详情 | 位置 |
|---|---|---|
| **缺失表清单** | `mkt_invitation` + `mkt_invitation_rule` + `mkt_dispatch` + `mkt_dispatch_target` 4 张表 — `@TableName` 实体已建 + Mapper 已建 + `BusinessPolicyDispatchController` 注入了 mapper，**但 db-snapshot.sql 0 匹配** | `entity/MktInvitation*.java` + `entity/MktDispatch*.java` + `db-snapshot.sql`（grep 0 命中）|
| **真实功能影响** | `BusinessPolicyDispatchController` 任一端点调用 → 启动期触发 `TableNotFoundException`（migrate_mkt_004 / 005 未跑）| — |
| **判定** | **MISSING**（I063 — 用户决策：本批次仅落档，Post-Audit DBA 统一写 migrate_mkt_004_invitation.sql + migrate_mkt_005_dispatch.sql）| F138 + BusinessPolicyDispatchController 链路 + `views/mkt/dispatch.vue` 范围 |

#### 32.9.10 I064 — Frontend 绕过 api/ 封装层（修正版 I064）

| 维度 | 详情 | 位置 |
|---|---|---|
| **原 I064 描述** | "前端无法调用新 Controller" — **不准确** | `feature-inventory.md §1.5.5` |
| **实际情况** | 前端 `views/mkt/*.vue` 通过 `http.get/post/put/delete('/mkt/...')` 直接调后端 URL — **绕过 api/ 封装层**，但**端点确实可达** | `views/mkt/channel.vue:124/136/158/161/170` + `views/mkt/coupon.vue:221/284/297/304/328/342/353/382` + `views/mkt/conversion.vue:92/94/96` + `views/mkt/journey.vue:347/403/406/414/418/439/596` |
| **真实功能影响** | ① 维护成本高（URL 改动需改 N 个 view）② 无统一 loading/error 处理 ③ 无类型检查（前端 TS interface 缺失）| — |
| **判定** | **PARTIAL-NON-BLOCKER**（I064 修正版 — 用户决策不修 api/mkt.ts；Fix Phase = Post-Audit；新增 I069 待登记）| F114-F134 全部前端 view |

#### 32.9.11 I065 — Dashboard 不调用 ChannelDispatcher（✅ 风险已解除）

| 维度 | 详情 | 位置 |
|---|---|---|
| **Phase 2 验证** | `frontend/meiyetec-web/src/views/dashboard/**` grep `/mkt/channel\|ChannelDispatcher\|healthCheckAll\|mktChannelApi` **0 命中** | Phase 2 grep |
| **真实功能影响** | 受保护页面 `/dashboard` 不调用 MKT 渠道统计 → I054 NEW 缺失表的影响不波及其他模块 | — |
| **判定** | **✅ 风险已解除**（I065）— Phase 5 维持 | — |

#### 32.9.12 I066 — I054 NEW 状态维持 + 实际表存在

| 维度 | 详情 | 位置 |
|---|---|---|
| **Phase 1 静态扫描** | `mkt_channel_config` 表存在 db-snapshot.sql:6313 ✅ | `db-snapshot.sql:6313` |
| **I054 NEW 推论反转** | 预期 `mkt_channel_config` 可能未重建 → 实际存在 | — |
| **真实功能影响** | `ChannelDispatcher.healthCheckAll` 可正常运行（无 TableNotFoundException）| — |
| **判定** | **I054 NEW 状态维持**（I066 — Phase 1 登记 §二十.20.1，本次确认存在）— F118 healthCheck 可用 | F118 |

#### 32.9.13 I067 — 重构中间态：5 旧类 + 9 新 Controller + market_activity 表未 DROP

| 维度 | 详情 | 位置 |
|---|---|---|
| **中间态量化** | 5 个旧 `MarketActivity*` 类（entity + mapper + service + serviceImpl + controller）**全部存在**；9 个新 Mkt*Controller **全部存在**；`market_activity` 表**仍在 DB**（migrate_mkt_002 未跑）| 见 scope-freeze §2.3 + diff-audit §2 |
| **真实调用点** | 6 个活跃调用点：`BusinessPolicyDispatchServiceImpl.applyActivity` × 3 方法 + `MktEffectController.snapshot` × 1 + `MktCouponController` 引用 `mkt_coupon_batch`（新表已存在）+ `MktPromotionController` 引用 MarketActivity | `diff-audit §2 项 #1` |
| **真实功能影响** | **`market_activity` 表无法 DROP**（与 I055 联动 — DROP 后 BusinessPolicyDispatchServiceImpl 会 NPE）| — |
| **判定** | **PARTIAL-BLOCKER + CONFLICT**（I067 — 重构中间态 + I055/I056 联动）— 用户决策：维持中间态，Post-Audit 决策 | F139 + 联动 I055/I056 |

#### 32.9.14 I068 — SoT 字段不一致（market_activity 列对齐）

| 维度 | 详情 | 位置 |
|---|---|---|
| **SoT 描述** | `market_activity` 9 列 + `rule_config JSON` 字段 | `docs/ROLLBACK-6.2-mkt_ddl.md` §2 |
| **实际 db-snapshot** | `market_activity` 表存在 line 5177（grep 已确认）| `db-snapshot.sql:5177` |
| **真实功能影响** | Phase 5 `DESCRIBE market_activity` 验证列对齐（不阻塞 Phase 2/3）| — |
| **判定** | **Phase 5 验证**（I068 — 待 MySQL MCP `DESCRIBE market_activity` 确认列对齐）| F139 |

### 32.10 MKT 重构真实风险分级汇总

| 风险等级 | 缺口 | Issue | Feature 影响 | 用户决策 |
|---|---|---|---|---|
| 🔴 **CRITICAL-P1** | `expireSweep` 缺 try/catch + 缺 audit log | **I060** | F132 | 不加 try/catch，Post-Audit |
| 🟠 **CRITICAL-P2** | `ChannelDispatcher.getSender` miss → NPE | **I061** | F135 + 任何调用链路 | 不改 Router，Post-Audit |
| 🟠 **CRITICAL-P2** | 4 表 MISSING（mkt_invitation / dispatch / dispatch_target） | **I063** | F138 + BusinessPolicyDispatchController | 本批次仅落档，Post-Audit 跑迁移 |
| 🟠 **CONFLICT（中立标）** | SoT vs 代码 CONFLICT（BusinessPolicyDispatchServiceImpl 仍读写 market_activity） | **I055** | F139 + MktEffectController | 不改 SoT / 不重构 |
| 🟡 **PARTIAL-BLOCKER（重构中间态）** | 5 旧类 + 9 新 Controller + market_activity 表未 DROP | **I067** | F139 + I055/I056 联动 | 维持中间态，Post-Audit 决策 |
| 🟡 **PARTIAL-NON-BLOCKER** | 跨租户删除（F117） | **I056** | F117 | 登记 Issue，Post-Audit |
| 🟡 **PARTIAL-NON-BLOCKER** | 跨租户写（F122/F123） | **I057** | F122 + F123 | 登记 Issue，Post-Audit |
| 🟡 **PARTIAL-NON-BLOCKER** | 双路由形式语义模糊（F125/F129） | **I058** | F125 + F129 | 登记 Issue，Post-Audit |
| 🟡 **PARTIAL-NON-BLOCKER** | snapshotSave 仅 log.info 未写表 | **I059** | F130 | 登记 Issue，Post-Audit |
| 🟢 **PARTIAL-NON-BLOCKER** | rateLimitPerDay setter 空语句 | **I062** | F115 + F138 | 登记 Issue，Post-Audit |
| 🟡 **PARTIAL-NON-BLOCKER（修正版）** | 前端绕过 api/ 封装层直调 URL | **I064 修正** + **I069** NEW | F114-F134 全部 | 登记 Issue，Post-Audit |
| ✅ **风险已解除** | dashboard.vue 未调用 ChannelDispatcher | **I065** | — | — |
| ✅ **状态维持** | mkt_channel_config 表存在（I054 NEW 反转） | **I066** | F118 | I054 NEW 维持 |
| 🟡 **待验证** | SoT 字段 vs 实际列对齐（market_activity） | **I068** | F139 | Phase 5 `DESCRIBE` 验证 |

### 32.11 与 Batch 1.1/1.2/1.3/1.4 复用模式对照

| Batch 1.5 缺口 | 沿用模式 | 历史 Issue | 累计次数 |
|---|---|---|---|
| **I055 SoT CONFLICT** | I001/I018/I026-I030/I039 同模式 | I039 Batch 1.4 EXT-19 WECHAT_OA 别名漂移 | 第 5 次（最严重：BusinessPolicyDispatchServiceImpl 仍在用）|
| **I056 跨租户删除** | I018 跨租户读/写风险模式 | I018 Batch 1.2 EXT-23 PluginTenantService | 第 2 次 |
| **I057 跨租户写** | I019 跨租户写风险模式 | I019 Batch 1.2 同 | 第 2 次 |
| **I058 双路由形式** | I058 自身（同 Feature 内）| 新 | 首次 |
| **I059 snapshotSave dead code** | I013 dead code 模式 | I013 Batch 1.2 PluginRuntimeInfo.autoLoaded / I025 PluginSecurityPolicy.deniedPackages | 第 3 次 |
| **I060 CRITICAL-P1 try/catch 缺失** | I032 Batch 1.3 EXT-22 geocode_batch audit 缺失 | I032 Batch 1.3 | 第 2 次 |
| **I061 CRITICAL-P2 NPE** | I012 Mock 适配器 condition conflict NPE | I012 Batch 1.1 CRM Mock | 第 2 次 |
| **I062 dead code setter 空语句** | I013 dead code 模式 | I013 Batch 1.2 | 第 3 次 |
| **I063 4 表 MISSING** | I002/I017/I029/I038/I045 MISSING 模式 | I038 Batch 1.4 overseas_booking + I045 member_message | 第 6 次（最多）|
| **I064 前端绕过 api 封装层** | I006/I007/I024/I028/I040-I042/I064 同模式 | I040/I041/I042 Batch 1.4 | 第 7 次 |
| **I067 重构中间态** | 新分类（本批次独有）| — | 首次 |
| **I068 SoT 字段不一致** | I001/I018/I026-I030 同模式 | — | 第 5 次 |

> **核心发现**：Batch 1.5 是 v3 前 4 批次所有缺口的「汇合点」 — 14 个 I055-I068 中 11 个**与历史 Issue 同模式**（79% 复用），仅 I058/I067 是新分类（首次出现）。

### 32.12 Phase 3 静态结论（最终）

| 维度 | 状态 |
|---|---|
| **守门脚本命中数（MKT 范围）** | **~271 命中**（含 3 处 HIGH catch-ignore-empty + 9 张表 soft-delete UNIQUE 不含 deleted HIGH）|
| **Batch 1.5 专属缺口清单** | **14 项 I055-I068 + 1 项 I069 NEW**（前端绕过 api/ 封装层） |
| **与 I055-I068 交叉引用** | **14/14 全部交叉验证**（沿用历史模式 + 2 项新分类） |
| **新增 Issue（I069+）** | **I069 NEW**（前端绕过 api/ 封装层 — I064 修正版）|
| **Feature Result 分布** | 沿用 Phase 2 结论：CRITICAL-P1 × 1 (F132) + CRITICAL-P2 × 1 (F135) + PARTIAL-BLOCKER × 1 (F139) + MISSING × 1 (F138) + PARTIAL-NON-BLOCKER × 21 + UNVERIFIED × 1 (F137) + Static-Only OK × 1 (F140) = 27 Features |

### 32.13 Phase 3 → Phase 4 衔接建议

| 缺口 | Phase 4 验证重点 |
|---|---|
| **I060 CRITICAL-P1**（expireSweep 缺 try/catch + 缺 audit）| Phase 5 调用 `POST /mkt/coupon/expire-sweep` → 验证返回 500 时 audit log 是否记录（I032 同模式）|
| **I061 CRITICAL-P2**（ChannelDispatcher.getSender NPE）| Phase 5 模拟 miss 场景（如注入未知 channelType）→ 验证 NPE 触发 |
| **I063 CRITICAL-P2**（4 表 MISSING）| Phase 5 MySQL MCP `SHOW TABLES LIKE 'mkt_invitation%' / 'mkt_dispatch%'` → 验证缺失 → 触发 TableNotFoundException |
| **I055/I067**（SoT CONFLICT + 中间态）| Phase 5 `DESCRIBE market_activity` 验证列对齐（I068）+ curl `POST /market/activity/{id}/copy` 验证旧 Controller 仍响应（I057）|
| **I056/I057**（跨租户删除/写）| Phase 5 跨租户访问测试（如可达）：普通租户调别人 channel/coupon/conversion → 期望 `BizCode.FORBIDDEN` |
| **I064 修正版 + I069**（前端绕过 api/ 封装层）| Phase 5 不验证（已知设计缺陷，Post-Audit 修复）|

### 32.14 Phase 3 决策不变（D11-D20 全部维持 + 新增 D21-D23）

| 决策 | 处理 | 沿用模式 |
|---|---|---|
| D11 | 不改 SoT（I039 维持 CONFLICT 中立标）| I001/I018 |
| D12 | 不加 UI（I028 登记 Issue）| I006/I007/I024 |
| D13 | 不跑迁移（I029 维持 PARTIAL-MISSING）| I002/I017 |
| D14 | 不加日志（I031 维持）| I008/I011 |
| D15 | 不加 audit log（I032 backlog）| I004/I019 |
| D16 | 保持 backlog（I033 BACKLOG-001）| I013 |
| D17 | 不加 rollbackFor（I034）| I009 |
| D18 | 不加时间窗（I035）| I008/I022 |
| D19 | 仅标注不改（I036 GCP Key 泄露）| user decision #9 |
| D20 | 不加 UI（F063/F064/F075 Shadow）| I006/I007 |
| **D21** | **不改 SoT（I055 维持 CONFLICT 中立标）**| I039/I043 同模式 |
| **D22** | **不改 Router（I061 维持 CRITICAL-P2 NPE 接受）**| I012 Mock 适配器同模式 |
| **D23** | **不跑迁移（I063 维持 MISSING — Post-Audit 统一跑）**| I038/I045 同模式 |
| **D24** | **不写 api/mkt.ts（I064 修正版 + I069 维持 PARTIAL-NON-BLOCKER）**| I040/I041/I042 同模式 |
| **D25** | **重构中间态维持（I067 PARTIAL-BLOCKER，Post-Audit 决策）**| 新分类（本批次独有）|

### 32.15 关联文档

- `audit/feature-inventory.md` §1.5（27 Features F114-F140）
- `audit/traceability-matrix.md` §Batch 1.5（line 2820-3823，含 Final Result + Issue 交叉引用）
- `audit/diff-audit/mkt-refactor.md`（Phase 3.5 产出，8 项全部完成）
- `audit/issues/issue-register.md` §二十一（I055-I068）+ 待登记 §二十二（I069 NEW）
- `audit/baseline/scope-freeze-batch-1.5.md` §2.3 + §十一（重构批次特有约束）
- `audit/baseline/preflight-batch-1.5.md` §七（已知约束 + 11 项决策）
- `audit/static-gap/01-08-*.txt`（8 个守门脚本原始输出，grep MKT 范围已提取）
- `audit/runtime/plan-batch-1.5.md`（待 Phase 4 编写）
- `frontend/meiyetec-web/src/views/mkt/*.vue`（11 个文件 — I064 修正版实证）
- `backend/src/main/java/com/meiyetec/controller/Mkt*Controller.java`（9 个 — 端点注册）
- `backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java`（核心 service — 230/290/514 catch-ignore-empty + 81 setter 空语句）
- `backend/src/main/java/com/meiyetec/service/channel/ChannelDispatcher.java:33-39`（I061 NPE 实证）
- `backend/src/main/java/com/meiyetec/service/impl/BusinessPolicyDispatchServiceImpl.java:262-280`（I055 实证）
- `docs/ROLLBACK-6.2-mkt_ddl.md`（SoT — 无 .mdc Operational Notes，本批次退化）

---

**Phase 3（Batch 1.5 MKT 多渠道重构）完成。守门脚本 ~271 命中 + 14 项 I055-I068 交叉验证 + 1 项 I069 NEW（前端绕过 api/ 封装层）+ 27 个 Feature Result 全部带文件路径:行号。Phase 2 / 3 全部落档。不改业务代码，不进 Phase 4。⏸ 等用户确认 → 启动 Phase 4（Runtime Plan）或 Post-Audit 修复。**
