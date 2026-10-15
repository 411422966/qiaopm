# Phase 5B — Runtime Verification Report

> **创建时间**：2026-09-13 00:55 (UTC+8)
> **Phase**：5B — Runtime Verification Execution
> **范围**：Batch 1.1 P1 — 16 个 Feature（含 5 个继承 + 3 个 CONFLICT + 8 个实际跑）
> **执行人**：Cursor Agent
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`（已 `git rev-parse HEAD` 验证）
> **DB snapshot**：`audit/evidence/db-snapshot.md`
> **Env**：`audit/evidence/env.md`

---

## 0. 执行摘要

### 0.1 16 个 P1 Feature 结果速查

| Feature ID | 名称 | 优先级 | 验证方式 | **最终状态** | 与 5A/plan 关系 | 核心负向证据 |
|---|---|---|---|---|---|---|
| **F003** | 更新集成 | P1 | API-only+DB | **PARTIAL-BLOCKER** | 5A 未测 / plan 标 PARTIAL | 🆕 Fake Failure + I002 + I007 |
| **F004** | toggle | P1 | API+DB | **PARTIAL-BLOCKER** | 5A 未测 / plan 标 I005 C1 | I005 C1 SQL 旁路 + I002 阻塞 |
| **F006** | 同步日志 | P1 | API-only | **BROKEN (inherited)** | 继承 5A F002/F005 | I002 阻塞 + I003 漂移 |
| **F007** | Dashboard | P1 | API-only | **BROKEN (inherited)** | 继承 5A F002 + 强化 I008 | I002 + I008 Fake Success |
| **F011** | shouldRun | P1 | API-only（间接）| **BROKEN (inherited)** | 继承 5A F008 | I002 阻塞 selectById 前置 |
| **F013** | Adapter 契约 | P1 | Static-Only | **VERIFIED** | 新增实证 I001 | 5 方法 + 5 DTO + 4 adapter 全完整 |
| **F014** | Salesforce 适配器 | P1 | API-only（受限）| **UNVERIFIED** | 与 plan 一致 | 无 healthCheck API + I002 阻塞 |
| **F015** | Mock 适配器 | P1 | API-only | **PARTIAL-NON-BLOCKER** | ⚠ plan 标 — | 🆕 I012 激活冲突 |
| **F016** | HubSpot 适配器 | P1 | CONFLICT-只读 | **CONFLICT** | 5A 继承 + 5B F013 强化 | I001 SoT 漂移 |
| **F017** | Zoho 适配器 | P1 | CONFLICT-只读 | **CONFLICT** | 5A 继承 + 5B F013 强化 | I001 SoT 漂移 |
| **F018** | Router 分发 | P1 | CONFLICT-只读 | **CONFLICT + 🆕 I012** | 5A 继承 + 5B F015 强化 | I001 + 🆕 I012 激活冲突 |
| **F019** | CrmIntegration Entity | P1 | Static + DB | **PARTIAL-NON-BLOCKER** | 5A 未测 / plan 标 I002 | 22/22 核心匹配 + 9 EXT-15 缺列 |
| **F020** | CrmContactMap Entity | P1 | DB-only | **BROKEN (inherited)** | 继承 5A F012 | 表不存在 + I002 阻塞 |
| **F021** | CrmSyncLog Entity | P1 | Static + API 间接 | **BROKEN (inherited)** | 继承 5A F005 | 缺 3 列 + I002 阻塞 |
| **F029** | status 状态机 | P1 | API+DB | **BROKEN** | 5A 未测 / plan 标 I005 + I002 | I002 完全阻塞状态机 |
| **F030** | sync 配置 | P1 | API-only（间接）| **PARTIAL-BLOCKER** | 5A 未测 / plan 标 I002 | I002 阻塞 + 🆕 syncSchedule dead code |

### 0.2 统计

| 状态 | 数量 | Feature |
|---|---|---|
| ✅ VERIFIED | **1** | F013 |
| ⚠ PARTIAL-NON-BLOCKER | **2** | F015, F019 |
| ⚠ PARTIAL-BLOCKER | **3** | F003, F004, F030 |
| ❌ BROKEN | **6** | F006 (inh), F007 (inh), F011 (inh), F020 (inh), F021 (inh), F029 |
| ❓ UNVERIFIED | **1** | F014 |
| ⚠ CONFLICT | **3** | F016, F017, F018 |

> **未达 BROKEN 的 8 个实际跑 Feature**：F003 (PARTIAL-BLOCKER), F004 (PARTIAL-BLOCKER), F013 (VERIFIED), F014 (UNVERIFIED), F015 (PARTIAL-NON-BLOCKER), F019 (PARTIAL-NON-BLOCKER), F029 (BROKEN), F030 (PARTIAL-BLOCKER)

### 0.3 关键发现

#### 🆕 新发现（5B 首次登记）

1. **I011 — Fake Failure（API 报错但 DB 已写）** ⭐ CRITICAL
   - F003 实证：`updateById` 成功 → `selectById` 抛 I002 → API 返回 500
   - 比 I008 Fake Success **更隐蔽**：用户看到失败但实际数据已改
   - 不可事务回滚（UPDATE 已提交）
   - 详细：`audit/evidence/F003-update-integration/result.md` §3

2. **I012 — Mock 适配器激活条件冲突** 🟠 中-高
   - `MockCrmAdapter.@ConditionalOnMissingBean(name="salesforceCrmAdapter")` → SF 已存在时 Mock **不激活**
   - 当前生产环境 platform=MOCK 集成 → Router.getAdapter("MOCK") → null → **NPE**
   - 详细：`audit/evidence/F015-mock-adapter/result.md` §2

3. **syncSchedule dead code（F030 发现）**
   - `CrmIntegration.syncSchedule` 字段定义但**无任何代码读取**
   - 实际 tick 间隔硬编码 `@Scheduled(fixedDelayString = "${crm.sync.tick-interval-ms:300000}")`
   - 与 plan §0.4 描述的 "cron 表达式" 语义不符

#### 严重度校正

| Feature | 5B 实测 | plan 标 | 校正 |
|---|---|---|---|
| F003 | PARTIAL-BLOCKER + 🆕 I011 CRITICAL | I002 + I007 PARTIAL | **严重低估**（Fake Failure 是 P0 级风险）|
| F029 | BROKEN | I005 C1 + I002 PARTIAL | **上调**（实测 I002 完全阻塞状态机，错误捕获都到不了）|
| F015 | PARTIAL-NON-BLOCKER + 🆕 I012 | — | 新增严重度 |
| F030 | PARTIAL-BLOCKER + dead code | I002 PARTIAL | **上调**（+ syncSchedule 设计未实现）|

#### I002 CRITICAL 持续确认

- F003 / F004 / F019 / F029 / F030 全程依赖 I002 修复
- 与 5A 同根因，5B 新增：F003 触发 `selectById` 在 `updateById` 之后 → Fake Failure

---

## 1. 继承项处理

### 1.1 5 个 BROKEN (inherited)

| Feature | 继承来源 | evidence 路径 | 状态码 |
|---|---|---|---|
| F006 | 5A F002/F005 | `audit/evidence/F006-sync-log/result.md` | ❌ BROKEN |
| F007 | 5A F002 + I008 联动 | `audit/evidence/F007-dashboard/result.md` | ❌ BROKEN + ⚠ SHADOW (I006) |
| F011 | 5A F008 | `audit/evidence/F011-should-run/result.md` | ❌ BROKEN |
| F020 | 5A F012 | `audit/evidence/F020-crm-contact-map/result.md` | ❌ BROKEN |
| F021 | 5A F005 | `audit/evidence/F021-crm-sync-log/result.md` | ❌ BROKEN |

### 1.2 3 个 CONFLICT (per I001, 5B F013 confirmed)

| Feature | 冲突维度 | evidence 路径 | 状态码 |
|---|---|---|---|
| F016 | HubSpot adapter 实测完整 vs SoT 标"未实现" | `audit/evidence/F016-hubspot-adapter/result.md` | ⚠ CONFLICT |
| F017 | Zoho adapter 实测完整 vs SoT 标"未实现" | `audit/evidence/F017-zoho-adapter/result.md` | ⚠ CONFLICT |
| F018 | Router fallback 实测有 MOCK fallback vs SoT 标"NPE" | `audit/evidence/F018-router-fallback/result.md` | ⚠ CONFLICT + 🆕 I012 |

---

## 2. 实际跑 8 个 Feature 详情

### 2.1 F003 — 更新集成（PARTIAL-BLOCKER + 🆕 I011 CRITICAL）

**完整证据**：`audit/evidence/F003-update-integration/`

| 维度 | 结果 | 证据文件 |
|---|---|---|
| API 调用 | HTTP 200 + code=500（Fake Failure）| `raw-response-update.txt` |
| DB 副作用 | ✅ sync_direction 已改 BIDIRECTIONAL（UPDATE 已提交）| `db-after-update.txt` |
| DB reset | ✅ 已还原 PULL | `db-reset.txt` |
| 后端日志 | selectById 抛 `Unknown column 'sync_token'` | `log-stack-trace.txt` + `log-context.txt` |

**🆕 I011 — Fake Failure 关键证据**：

```java
// CrmIntegrationService.java:58-66
public Result<CrmIntegration> update(@PathVariable Long id, @RequestBody CrmIntegration i) {
    i.setId(id);
    i.setTenantId(TenantContextHolder.get().getTenantId());
    integrationMapper.updateById(i);                                   // ← 步骤 1：成功
    return Result.success("已更新", integrationMapper.selectById(id));   // ← 步骤 2：抛 I002
}
```

**实测现象**：
1. ✅ updateById 已成功执行（DB 中 `sync_direction` 从 PULL → BIDIRECTIONAL）
2. ❌ selectById 抛 I002 → GlobalExceptionHandler → code=500
3. ❌ 前端看到"失败"，不会重试
4. ⚠️ **数据已悄然变更，但用户被告知失败**

**与 I008 Fake Success 对比**：

| 维度 | I008 Fake Success | I011 Fake Failure |
|---|---|---|
| API 返回 | `code=200, success=true` | `code=500, message="数据库异常"` |
| 实际状态 | DB 操作失败但 API 报告成功 | DB 操作成功但 API 报告失败 |
| 用户感知 | "成功"（最坏）| "失败"（次坏 — 但数据已改）|
| 重试行为 | 不重试 | 不重试（重试 = 重复写入）|
| **隐蔽性** | 低（用户无感）| **高**（数据漂移 + 前端不刷新）|

**严重度评估**：🔴 **CRITICAL**（数据漂移 + 用户感知失败 — 双重风险）

---

### 2.2 F004 — toggle（PARTIAL-BLOCKER + I005 C1 暴露）

**完整证据**：`audit/evidence/F004-toggle-enum-drift/`

**测试矩阵（3 个状态）**：

| 状态 | HTTP | code | 后端异常 |
|---|---|---|---|
| DISABLED | 200 | 500 | BadSqlGrammarException |
| PAUSED | 200 | 500 | BadSqlGrammarException |
| ERROR | 200 | 500 | BadSqlGrammarException |

3 个 traceId 全部抛同一异常：`Unknown column 'sync_token'`。

**I005 C1 — enum 漂移 SQL 旁路证明**：

```sql
UPDATE crm_integration SET status='PAUSED' WHERE id=2;
SELECT id, status FROM crm_integration WHERE id=2;
-- id status
-- 2  PAUSED     ← 写入成功
```

完整 SQL 旁路：`db-side-channel-PAUSED.txt` → **SQL 允许写入 PAUSED**

**代码层无校验**（`CrmIntegrationService.java:73`）：
```java
i.setStatus((String) body.get("status"));  // 直接接受任意字符串
```

**跨模块 enum 不一致**：
- PluginTenantService: ACTIVE/DISABLED
- MemberLifecycleService: ACTIVE/**PAUSED**
- PricingExperimentService: DRAFT/RUNNING/**PAUSED**/COMPLETED
- SubscriptionServiceImpl: **PAUSED**
- CarePlanService: **PAUSED**

→ **5+ 模块使用不同 enum 集合**（F029 enum 一致性检查可继续深挖）

---

### 2.3 F013 — Adapter 接口契约（VERIFIED）

**完整证据**：`audit/evidence/F013-adapter-contract/`

| 元素 | 行号 | 验证 |
|---|---|---|
| `CrmAdapter.java` | 26-49 | 5 abstract method |
| `AuthResult / SyncCursor / ExternalContact / PushResult / HealthStatus` | 57-145 | 5 DTO 完整 |

**4 个 adapter 全完整实现**：

| Adapter | platformCode | authenticate | fetchContactsIncremental | upsertContact | healthCheck |
|---|---|---|---|---|---|
| Salesforce | SALESFORCE ✓ | ✓ | ✓ | ✓ | ✓ |
| HubSpot | HUBSPOT ✓ | ✓ | ✓ | ✓ | ✓ |
| Zoho | ZOHO ✓ | ✓ | ✓ | ✓ | ✓ |
| Mock | MOCK ✓ | ✓ | ✓ | ✓ | ✓ |

→ **4/4 adapter 完整无 stub** → 与 EXT-24 SoT 4 处冲突（I001 实证）

---

### 2.4 F014 — Salesforce 适配器（UNVERIFIED）

**完整证据**：`audit/evidence/F014-salesforce-adapter/`

| 维度 | 状态 |
|---|---|
| SF 适配器静态实现 | ✅ 完整（OAuth2 JWT + SOQL + upsert + health）|
| 与 EXT-24 §0.6 一致性 | ✅ 完全一致 |
| e2e 测试可执行性 | ❌ 无 healthCheck API + I002 阻塞 |
| 真实 SF 凭据 | ❌ 未提供 |

**F014 修复路径（不实施）**：
1. 跑 `migrate_ext_15_crm_real.sql` → I002 解除
2. 添加 `POST /crm-integration/{id}/health` endpoint
3. 提供真实 SF 沙箱凭据 → 联调验证

---

### 2.5 F015 — Mock 适配器（PARTIAL-NON-BLOCKER + 🆕 I012）

**完整证据**：`audit/evidence/F015-mock-adapter/`

**🆕 I012 激活冲突关键证据**：

```java
@Component
@ConditionalOnMissingBean(name = "salesforceCrmAdapter")   // ← SF 存在时 Mock 不注册
public class MockCrmAdapter implements CrmAdapter { ... }
```

| 场景 | SalesforceCrmAdapter | MockCrmAdapter | Router 注入 |
|---|---|---|---|
| **当前（默认）** | ✅ 注册 | ❌ 不注册 | 3 个（SF + HubSpot + Zoho），**无 Mock** |
| 关闭 SF | ❌ 不注册 | ✅ 注册 | 4 个（含 Mock）|

**NPE 风险**：
```java
// CrmAdapterRouter.java:31-35
if (a == null) {
    return adapters.get("MOCK");   // ← MOCK 没注册时，这里返回 null → NPE
}
```

→ **生产环境 platform=MOCK 集成 = NPE**（SF 默认存在）

---

### 2.6 F019 — CrmIntegration Entity（PARTIAL-NON-BLOCKER）

**完整证据**：`audit/evidence/F019-crm-integration-entity/`

**Entity ↔ DB 列比对**：

| 类别 | 数量 | 匹配 |
|---|---|---|
| Entity 核心字段 | 22 | ✅ 22/22 |
| Entity EXT-15 字段 | 9 | ❌ 9/9 声明但 DB 缺 |
| transient 字段 | 1 | ✅ 配置正确 |

**DB 缺 9 列**（完整列出）：
- sync_token, sync_schedule, sync_enabled, last_incremental_sync_at
- private_key, username, login_url, object_mappings, sync_window_minutes

→ **I002 完整验证**（与 issue-register §十 一致）

---

### 2.7 F029 — Status 状态机（BROKEN）

**完整证据**：`audit/evidence/F029-status-state-machine/`

**测试场景：ERROR → sync → ?**

| 步骤 | 操作 | 结果 |
|---|---|---|
| 1 | SQL 手动设 id=2 status='ERROR' | ✅ |
| 2 | POST `/crm-integration/2/sync?force=1` | ❌ code=500（I002）|
| 3 | SQL 查 id=2 状态 | ⚠ 仍 ERROR（同步链路完全到不了）|

**I002 阻塞链路**：
```
tick:47 selectList → I002 → catch 吞 → return
syncNow:88 selectById → I002 → 抛错 → 状态机失效
```

**运维侧盲区**（无任何场景能切 ERROR 状态）：
- Salesforce 凭据错误、OAuth2 失败、网络超时、限流 OVER_QUERY_LIMIT 全部捕获不到
- F007 dashboard catch 吞异常 → 即便 status=ERROR 也只显示空 data

---

### 2.8 F030 — sync 配置（PARTIAL-BLOCKER + syncSchedule dead code）

**完整证据**：`audit/evidence/F030-sync-config/`

**sync 配置字段完整度**：

| 字段 | DDL 定义 | Entity 声明 | 代码使用 | 当前生效 |
|---|---|---|---|---|
| syncSchedule | ✅ migrate_ext_15 | ✅ Entity | ❌ **dead code** | ❌ 无效 |
| syncEnabled | ✅ migrate_ext_15 | ✅ Entity | ✅ tick:50 筛选 | ❌ I002 阻塞 |
| syncWindowMinutes | ✅ migrate_ext_15 | ✅ Entity | ✅ shouldRun:73 | ❌ I002 阻塞 |

**🆕 dead code 发现**：

```java
// CrmSyncScheduler.java:43-46
@Scheduled(fixedDelayString = "${crm.sync.tick-interval-ms:300000}",
           initialDelay = 60_000L)
public void tick() { ... }
```

→ **tick 间隔硬编码 5 分钟**（与 cfg.syncSchedule 无关）
→ **`syncSchedule` 字段定义但无任何代码读取**（grep 全代码库 0 命中）

---

## 3. DB 副作用汇总

### 3.1 Phase 5B 起点（5A 终点）

| 表 | 行数 | 状态 |
|---|---|---|
| `crm_integration` | 3 | id=1(SF), id=2(MOCK), id=3(MOCK) |
| `crm_sync_log` | 0 | — |
| `crm_contact_map` | 不存在 | — |

### 3.2 Phase 5B 终点（验证已还原）

| 表 | 行数 | 变化 | 备注 |
|---|---|---|---|
| `crm_integration` | **3** | 0 | ✅ 已还原（5B F003 db-reset / F004 db-reset-ACTIVE / F029 db-reset 全部成功）|
| `crm_sync_log` | **0** | 0 | ✅ |
| `crm_contact_map` | 仍不存在 | — | ✅ |

**验证命令**：
```sql
SELECT id, tenant_id, platform, sync_direction, status, error_message FROM crm_integration ORDER BY id;
-- 1 | 1 | SALESFORCE | PUSH         | ACTIVE | NULL
-- 2 | 1 | MOCK       | PULL         | ACTIVE | NULL   ← F004/F029 已 reset
-- 3 | 2 | MOCK       | PULL         | ACTIVE | NULL
```

---

## 4. E2E 状态（6 个脚本）

> Playwright MCP 未配置 → 降级为脚本生成 + 用户手动跑 + 结果贴回

| Feature | 脚本路径 | 用户跑命令 | 结果贴回 |
|---|---|---|---|
| **F007** Dashboard tab Shadow | `audit/scripts/e2e/F007-no-dashboard-tab.spec.ts` | `npx playwright test ../audit/scripts/e2e/F007-no-dashboard-tab.spec.ts` | `audit/evidence/F007-dashboard/e2e-result.md` |
| **F024** 列表 UI 渲染 | `audit/scripts/e2e/F024-list-ui.spec.ts` | `npx playwright test ../audit/scripts/e2e/F024-list-ui.spec.ts` | `audit/evidence/F024-list-ui/e2e-result.md` |
| **F025** 新建集成表单 | `audit/scripts/e2e/F025-create-form.spec.ts` | `npx playwright test ../audit/scripts/e2e/F025-create-form.spec.ts` | `audit/evidence/F025-create-form/e2e-result.md` |
| **F026** 同步日志 tab | `audit/scripts/e2e/F026-sync-log-tab.spec.ts` | `npx playwright test ../audit/scripts/e2e/F026-sync-log-tab.spec.ts` | `audit/evidence/F026-sync-log-tab/e2e-result.md` |
| **F027** sync 按钮 force | `audit/scripts/e2e/F027-sync-button.spec.ts` | `npx playwright test ../audit/scripts/e2e/F027-sync-button.spec.ts` | `audit/evidence/F027-sync-button/e2e-result.md` |
| **F028** objectMappings UI | `audit/scripts/e2e/F028-object-mappings.spec.ts` | `npx playwright test ../audit/scripts/e2e/F028-object-mappings.spec.ts` | `audit/evidence/F028-object-mappings/e2e-result.md` |

**关键验证点**：
- F007：tab 应只有"集成配置"+"同步日志"，无 Dashboard
- F024：状态列显示 ACTIVE/DISABLED/PAUSED（I005 C1 漂移实证）
- F025：表单只有 4 字段，无 objectMappings / clientId / clientSecret / privateKey
- F026：同步日志表有 integrationId 列（I003 漂移）
- F027：sync 按钮请求 URL 不含 ?force=1（I005 C2）
- F028：表单无 objectMappings 字段（I005 C4）

---

## 5. Issue 触发清单

| Issue | 触发 Feature | 严重度 | 5B 状态 |
|---|---|---|---|
| **I001** SoT 漂移 | F013, F016, F017, F018 | 🟡 中 | 5B 强化（4 处冲突代码层验证）|
| **I002** DDL 缺 9 列 | F003, F004, F019, F029, F030, F006 (inh), F007 (inh), F011 (inh), F020 (inh), F021 (inh) | 🔴 **CRITICAL** | 5B 持续暴露（7 个新 Feature 受影响）|
| **I003** 集成 ID 漂移 | F006 (inh), F026 (E2E 待跑) | 🟡 中 | 5B 强化（F026 E2E 验证渲染列）|
| **I005 C1** toggle enum | F004, F029 | 🟡 中 | 5B 实证（SQL 旁路 + 代码层）|
| **I005 C2** force 未传 | F027 (E2E 待跑) | 🟡 中 | 5B 脚本已生成待用户跑 |
| **I005 C4** 表单字段缺失 | F025, F028 (E2E 待跑) | 🟡 中 | 5B 脚本已生成待用户跑 |
| **I006** Dashboard tab 缺失 | F007 | 🟡 中 | 5B 继承 |
| **I007** update UI 缺失 | F003 | 🟡 中 | 5B 暴露（Fake Failure 加剧）|
| **I008** catch 吞异常 | F007 (inh), F010 (5A) | 🔴 **CRITICAL** | 5B 强化（F010 实证）|
| **I009** rollbackFor | F005 (5A) | 🟡 中 | 5A 验证事务回滚生效 |
| **🆕 I011** Fake Failure | F003 | 🔴 **CRITICAL** | **5B 新登记**（API 报错但 DB 已写）|
| **🆕 I012** Mock 激活冲突 | F015, F018 | 🟠 中-高 | **5B 新登记**（默认 Mock 不激活 → NPE）|

---

## 6. plan.md 严重度校正

| Feature | plan §三 标注 | 5B 实测状态 | 建议校正 |
|---|---|---|---|
| F003 | I007 Shadow + I002 silent | PARTIAL-BLOCKER + 🆕 I011 | **严重低估**（新登记 CRITICAL Issue）|
| F004 | I005 C1 + I002 | PARTIAL-BLOCKER + I005 C1 SQL 旁路实证 | 一致（轻微上调）|
| F006 | I003 漂移 | BROKEN (inherited from I002 via 5A F002/F005) | 与 5A 一致 |
| F007 | I006 Shadow + I002 + I008 | BROKEN (inherited) | 与 5A 一致 |
| F011 | I002 阻塞 | BROKEN (inherited) | 与 5A 一致 |
| F013 | — | VERIFIED | 保持 |
| F014 | "API-only（受限）"| UNVERIFIED | 保持（无法 e2e 验证）|
| F015 | — | PARTIAL-NON-BLOCKER + 🆕 I012 | **新增严重度**（I012 需登记）|
| F016 | I001 | CONFLICT | 与 5A 一致 |
| F017 | I001 | CONFLICT | 与 5A 一致 |
| F018 | I001 | CONFLICT + 🆕 I012 | **新增 I012** |
| F019 | I002 silent | PARTIAL-NON-BLOCKER | 与 5A 一致 |
| F020 | I002 / F023 | BROKEN (inherited) | 与 5A 一致 |
| F021 | I002 | BROKEN (inherited) | 与 5A 一致 |
| F029 | I005 C1 + I002 | BROKEN | **上调**（I002 完全阻塞状态机，错误捕获都到不了）|
| F030 | I002 | PARTIAL-BLOCKER + syncSchedule dead code | **上调**（+ 设计意图未实现）|

---

## 7. 修复路径（不实施，仅登记）

### 7.1 🛠 一键修复（最高 ROI）

```bash
mysql -uroot -proot123456 -P3307 meiyetec < migrate_ext_15_crm_real.sql
```

**预计恢复**：
- F003 update ✅ 恢复（selectById 可达 → 不再 Fake Failure）
- F004 toggle ✅ 恢复（selectById 可达 → setStatus 可达）
- F006 sync log list ✅ 恢复（5A F002 推论）
- F007 dashboard ✅ 恢复（但 I008 Fake Success 仍需单独修）
- F011 shouldRun ✅ 恢复
- F019/F020/F021 Entity 写入恢复
- F029 status 状态机 ✅ 恢复
- F030 syncWindowMinutes / syncEnabled 读取恢复

**预计仍遗留**：
- F013 已 VERIFIED — 与 I002 无关
- F014 UNVERIFIED — 仍需 healthCheck API + 真实 SF 凭据
- F015 PARTIAL-NON-BLOCKER → 仍需修 I012 Mock 激活条件
- F016/F017/F018 CONFLICT — 仅 SoT 文档需更新（D2 决策）
- **🆕 I011 Fake Failure** — 需重构 service.update() 把 selectById 改 Optional 处理
- **🆕 I012 Mock 激活** — 需改 `@ConditionalOnMissingBean` 为 `@ConditionalOnProperty` 或 `@Profile("dev")`
- **🆕 syncSchedule dead code** — 需确认意图（删除或实现）

### 7.2 🛠 配合监控

- 加 `crm_scheduler_tick_failed_total` Prometheus metric
- GlobalExceptionHandler 把 `BadSqlGrammarException` 单独归类
- CrmSyncScheduler WARN → ERROR 级别
- **🆕** I011 防御：service.update / service.toggle 完成后用 `try-catch` 包 selectById，**不让 SELECT 失败把 UPDATE 抹黑**

### 7.3 🛠 防御性加固

- MyBatis-Plus `@Interceptor` 启动期校验 entity 字段 ↔ schema 列对齐
- L0 静态校验加 entity schema mismatch check（CI 守门）
- **🆕** I012 防御：Router.getAdapter null fallback 改抛 `BizCode.NOT_FOUND` 而非返回 null

---

## 8. 关联文件

```
audit/
├── evidence/
│   ├── F003-update-integration/    ← 8 个文件 + result.md（Fake Failure + I002 + I007）
│   ├── F004-toggle-enum-drift/     ← 11 个文件 + result.md（I005 C1 SQL 旁路 + I002）
│   ├── F006-sync-log/              ← result.md（继承 BROKEN）
│   ├── F007-dashboard/             ← result.md（继承 BROKEN + I006 Shadow）
│   ├── F011-should-run/            ← result.md（继承 BROKEN）
│   ├── F013-adapter-contract/      ← result.md（VERIFIED + I001 强化）
│   ├── F014-salesforce-adapter/    ← result.md（UNVERIFIED）
│   ├── F015-mock-adapter/          ← 4 log 文件 + result.md（I012 🆕）
│   ├── F016-hubspot-adapter/       ← result.md（CONFLICT）
│   ├── F017-zoho-adapter/          ← result.md（CONFLICT）
│   ├── F018-router-fallback/       ← result.md（CONFLICT + I012）
│   ├── F019-crm-integration-entity/ ← db-columns.txt + result.md
│   ├── F020-crm-contact-map/       ← result.md（继承 BROKEN）
│   ├── F021-crm-sync-log/          ← result.md（继承 BROKEN）
│   ├── F029-status-state-machine/  ← 6 个文件 + result.md
│   └── F030-sync-config/           ← result.md（+ syncSchedule dead code）
├── scripts/
│   └── e2e/
│       ├── F007-no-dashboard-tab.spec.ts
│       ├── F024-list-ui.spec.ts
│       ├── F025-create-form.spec.ts
│       ├── F026-sync-log-tab.spec.ts
│       ├── F027-sync-button.spec.ts
│       └── F028-object-mappings.spec.ts
├── runtime/
│   ├── plan.md                     ← Phase 4 计划
│   ├── report-5A.md                ← Phase 5A 报告
│   └── report-5B.md                ← ← 本文件
└── issues/
    ├── issue-register.md           ← I001-I010 主索引（5B 后建议加 I011/I012）
    ├── I001-ext24-sot-conflict.md
    ├── I002-crm-ddl-missing.md
    ├── I003-integration-id-drift.md
    ├── I004-refactor-backlog.md
    ├── I005-enum-drift.md
    ├── I006-crm-dashboard-shadow.md
    ├── I007-crm-update-shadow.md
    ├── I008-dashboard-catch-swallow.md
    ├── I009-transactional-rollbackfor.md
    └── I010-flyway-v1-encoding.md
```

---

## 9. Phase 5B 结论

| 维度 | 结论 |
|---|---|
| 16 个 P1 Feature 验证 | ✅ 完成（16/16 — 含继承 + CONFLICT + 实际跑）|
| plan.md §三 负向证据捕获率 | ✅ 100% 命中 + 2 个新 Issue（I011 / I012）+ 1 个 dead code（syncSchedule）|
| 代码修改（规则 6）| ✅ **零修改**（仅 SQL 操作：F004/F029 db-reset，已确认还原）|
| DB 副作用 | ✅ **0 净增**（5B 跑完后与 5A 终点一致）|
| E2E 脚本生成 | ✅ **6/6 完成**（F007/F024/F025/F026/F027/F028）|
| Production 阻塞项 | ❌ **6 BROKEN + 1 UNVERIFIED + 5 PARTIAL-BLOCKER**（I002 + I011 修复前不可上线）|
| **Phase 5B 状态** | ✅ **完成 — 等用户确认进入 Phase 5C** |

---

## 10. Phase 5C 启动建议

> 等用户确认后再启动

**Phase 5C 范围（建议）**：

| 类别 | Feature 数 | 重点 |
|---|---|---|
| P2 待测 | 8 | 前端 UI / Dashboard / 排程配置（含 6 个 E2E-降级 — 脚本已生成待用户跑）|
| E2E 结果回填 | 6 | 用户跑完 Playwright 后回填 result |
| 🆕 新 Issue 登记 | 2 | I011 (Fake Failure CRITICAL) + I012 (Mock 激活冲突) |
| 修复决策 | — | I002 一键修复 vs 渐进修复 / I011 service 重构 / I012 Mock 条件化 |

**Phase 5C 预期**：
- E2E 6 个回填 → 完整 30 个 Feature 覆盖
- 静态 / 继承 / 实际跑 / E2E 四类证据齐全
- I011/I012 写独立 issue 文件
- 决策：是否在 5D 之前先跑 `migrate_ext_15_crm_real.sql` 修复 I002

---

**Phase 5B 完成 — 等待用户确认进入 Phase 5C**