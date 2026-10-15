# Phase 5A — Runtime Verification Report

> **创建时间**：2026-09-12 23:21 (UTC+8)
> **Phase**：5A — Runtime Verification Execution
> **范围**：Batch 1.1 P0 核心链路 — 7 个 Feature
> **执行人**：Cursor Agent
> **Commit hash**：**`12a97ea57a98d268c864fc7c010b82b305d7bb93`**（5A 起点）— ⚠ 2026-09-12 23:50 修正：项目**是** git 仓库，原"非 git 仓库"判断错误
> **DB snapshot**：`audit/evidence/db-snapshot.md`
> **Env**：`audit/evidence/env.md`

---

## 0. 执行摘要

### 0.1 7 个 Feature 结果速查

| Feature ID | 名称 | 优先级 | 验证方式 | **最终状态** | 与 plan §三 预期 | 核心负向证据 |
|---|---|---|---|---|---|---|
| **F001** | 创建集成 | P0 | API-only | **PARTIAL-NON-BLOCKER** | 一致（I002 silent）| 10 列 silent-ignored |
| **F002** | 集成列表 | P0 | API-only | **BROKEN** | ⚠ 超出（plan 标 silent，实测抛 500）| `Unknown column 'sync_token'` |
| **F005** ⭐ | 手动触发同步 | P0 | API+DB | **BROKEN** | ⚠ 部分超出（plan 标 PARTIAL-BLOCKER，实测 BROKEN）| `syncNow:88` selectById 抛错 |
| **F008** | 手动 sync 窗口跳过 | P0 | API+DB | **BROKEN** | ⚠ 超出（plan 标 PARTIAL-NON-BLOCKER，实测 BROKEN）| shouldRun 不可达 |
| **F009** | 跨租户拒绝 | P0 | API-only | **UNVERIFIED** | ⚠ 超出（plan 标 — 不依赖，实测强依赖）| I002 阻塞 e2e 验证 |
| **F010** | 自动 tick 扫描 | P0 | API+DB（观察）| **BROKEN** | 一致（catch 吞）| 7 次 tick 全失败，静默 |
| **F012** | known-ids 差集加载 | P0 | DB-only | **BROKEN** | 一致（F023 PARTIAL-BLOCKER）| `crm_contact_map` 不存在 + Runtime 不可达 |

### 0.2 统计

| 状态 | 数量 | Feature |
|---|---|---|
| ✅ VERIFIED | **0** | — |
| ⚠ PARTIAL-NON-BLOCKER | **1** | F001 |
| ⚠ PARTIAL-BLOCKER | **0** | — |
| ❌ BROKEN | **5** | F002, F005, F008, F010, F012 |
| ❓ UNVERIFIED | **1** | F009 |
| ❌ MISSING / MOCK / SHELL | **0** | — |

### 0.3 关键发现

1. **I002 DDL 缺列的严重程度超出 plan §三 预期**
   - plan 评估：缺 9 列 silent
   - 实测：缺 `sync_token` 触发 `BadSqlGrammarException`，**整个 SELECT 失败**
   - 影响：MyBatis-Plus 自动生成的 SELECT 含 9 个缺列，**任何一个缺列都抛 500**
   - 范围：F002 / F005 / F008 / F009 / F010 / F012 全部受影响

2. **I008 catch 吞异常导致静默失败**
   - F010 tick：5 分钟 × 7 次 = 35 分钟连续失败，**无 ERROR 日志、无 metric、无告警**
   - F002/F005/F008：HTTP 200 + code=500 + message="数据库异常"（模糊）

3. **I009 事务回滚生效（正面证据）**
   - F005/F008 触发后 `crm_sync_log` 0 行（事务回滚生效）
   - `crm_integration.status` 保持 ACTIVE（异常太早，未切 ERROR）

4. **静态 vs Runtime 严重度差距**
   - F012 静态可达 L0，运行时不可达
   - F009 跨租户判断代码存在但被 I002 阻塞无法 e2e 验证

5. **Plan 严重度低估**
   - F002/F005/F008：plan 标 silent/PARTIAL，实测 BROKEN
   - F009：plan 标不依赖 I002，实测强依赖

---

## 1. F001 — 创建集成

**最终状态：⚠ PARTIAL-NON-BLOCKER**

| 维度 | 结果 |
|---|---|
| HTTP 行为 | ✅ 200 + code=200 |
| DB 行写入 | ✅ id=2 已创建 |
| 数据一致性 | ⚠ 10 列 silent-ignored |
| 证据路径 | `audit/evidence/F001-create-integration/` |

**核心负向证据**：

```
HTTP/1.1 200
{"code":200,"message":"已创建","data":{"id":2, ... ,"syncToken":null,"syncWindowMinutes":null,"objectMappings":null,...}}
```

DB 实际写入字段：`tenant_id=1, platform=MOCK, sync_direction=PULL, auto_sync=1, status=ACTIVE` — **已存在的列正确写入**。
API 响应中但表中无列的字段：shopName, syncWindowMinutes, objectMappings, privateKey, syncEnabled, lastIncrementalSyncAt, syncToken, syncSchedule, username, loginUrl — **10 个字段全为 null**。

**I002 silent** — MyBatis-Plus insert 对缺失列默认 `silent-ignored`，前端传字段看似成功但实际没落库。

---

## 2. F002 — 集成列表

**最终状态：❌ BROKEN**

| 维度 | 结果 |
|---|---|
| HTTP 行为 | ❌ code=500 |
| 端点可用性 | ❌ 完全不可用 |
| 证据路径 | `audit/evidence/F002-list-integration/` |

**核心负向证据**：

```
HTTP/1.1 200
{"code":500,"message":"数据库异常","data":null,"traceId":"f589a40454e8479c8b678effb821827a"}
```

后端日志：
```
at com.meiyetec.service.CrmIntegrationService.list(CrmIntegrationService.java:55)
SQL: SELECT id,tenant_id,...,sync_token,sync_schedule,sync_enabled,
    last_incremental_sync_at,private_key,username,login_url,object_mappings,
    sync_window_minutes FROM crm_integration WHERE (tenant_id = ?) ORDER BY id DESC
Cause: java.sql.SQLSyntaxErrorException: Unknown column 'sync_token' in 'field list'
```

**超出 plan §三 预期**：plan §1.1 标 "I002（缺 9 列 silent）"，实测 = MyBatis-Plus selectList 抛 `BadSqlGrammarException` → BROKEN。
整个 list 端点不可用，F024（列表 UI）也无法工作。

---

## 3. F005 ⭐ — 手动触发同步

**最终状态：❌ BROKEN**

| 维度 | 结果 |
|---|---|
| HTTP 行为 | ❌ code=500（force=null 和 force=1 都抛） |
| syncNow 可用性 | ❌ 完全不可用 |
| 窗口判断 (shouldRun) | ❌ 不可达（selectById 前置抛错） |
| 真实同步链路 | ❌ 不可达 |
| `crm_sync_log` 副作用 | ✅ 0 行（事务回滚生效）|
| 证据路径 | `audit/evidence/F005-manual-sync/` |

**核心负向证据**：

```
at com.meiyetec.service.CrmIntegrationService.syncNow(CrmIntegrationService.java:88)
at $Proxy314.selectById
SQL: SELECT id,tenant_id,...,sync_token,...,sync_window_minutes
    FROM crm_integration WHERE id=?
Cause: java.sql.SQLSyntaxErrorException: Unknown column 'sync_token' in 'field list'
```

**force=1 也无法短路** — selectById 在 shouldRun 之前抛错，force 参数永远到不了。

**DB 副作用**：
- `crm_integration.id=2`: status=ACTIVE, last_sync_at=NULL（未变）
- `crm_sync_log`: 0 行（事务回滚生效 — **I009 正面证据**）

**plan §三 偏差**：plan §1.1 标 `I002` + `I009`，实测 = I002 + I008 catch 吞 + I009 事务回滚生效（plan §三预期 `crm_sync_log` 有 FAILED 行，实测 0 行）。

---

## 4. F008 — 手动 sync 窗口跳过

**最终状态：❌ BROKEN**

| 维度 | 结果 |
|---|---|
| HTTP 行为 | ❌ code=500 |
| 窗口判断 (shouldRunForManualTrigger) | ❌ 不可达 |
| 窗口外跳过返回 skipped=true | ❌ 不可达 |
| `last_sync_at` 副作用 | ✅ 不变（事务回滚） |
| 证据路径 | `audit/evidence/F008-sync-skip-window/` |

**核心负向证据**：

测试条件：SQL 手动设置 `crm_integration.id=2.last_sync_at = NOW()` 模拟窗口内。
调用 POST sync（无 force）→ HTTP 200 + code=500。

后端日志：与 F005 完全一致（`syncNow:88` selectById 抛错），`shouldRunForManualTrigger`（line 96-103）**永远到不了**。

**plan §三 偏差**：plan §1.1 标 "I002 列缺导致窗口失败"，plan §三 标 "PARTIAL-NON-BLOCKER"。实测整个 syncNow 入口 BROKEN，窗口判断 / mock adapter / Salesforce adapter / 跨租户判断 全部不可达。

---

## 5. F009 — 跨租户拒绝

**最终状态：❓ UNVERIFIED**

| 维度 | 结果 |
|---|---|
| HTTP 行为 | ❌ 三场景都 code=500 |
| 跨租户拒绝路径 | ⚠ 代码存在但不可达 |
| 超管跨租户允许 | ⚠ 代码存在但不可达 |
| 端到端 e2e 验证 | ❌ 无法在当前环境下完成 |
| 证据路径 | `audit/evidence/F009-sync-cross-tenant/` |

**核心负向证据**：

测试矩阵：

| 场景 | token | integration | 实测 |
|---|---|---|---|
| A 同租户 admin → id=2(tenant=1) | admin | id=2 (tenant=1) | ❌ code=500 |
| B **跨租户** clerk01 → id=3(tenant=2) | clerk01 | id=3 (tenant=2) | ❌ code=500 |
| C **超管跨租户** admin → id=3(tenant=2) | admin | id=3 (tenant=2) | ❌ code=500 |

3/3 都抛同一异常（`Unknown column 'sync_token'`），**无法 e2e 区分跨租户拒绝 vs DB 异常**。

**用户标签不一致**：用户输入"超管 token"实际是 clerk01（tenantId=1），"普通租户 token"实际是 admin（tenantId=1, dataScope=ALL）。两个 token 都是 tenant_id=1 → 需 SQL 插入 tenant_id=2 测试数据（id=3）。

**plan §三 偏差**：plan §1.1 标 "—" 表示 F009 不依赖 I002 修复。**实测强依赖 I002** — syncNow 第 88 行 selectById 抛错后，跨租户判断（第 90 行）不可达。

---

## 6. F010 — 自动 tick 扫描

**最终状态：❌ BROKEN**

| 维度 | 结果 |
|---|---|
| 排程触发 | ✅ VERIFIED（5 分钟间隔，7 次连续） |
| 排程执行成功 | ❌ BROKEN（全部失败） |
| 异常可观测 | ⚠ PARTIAL（WARN 日志，但无 traceId/无 metric） |
| 静默失败风险 | ❌ **生产风险高** |
| 证据路径 | `audit/evidence/F010-auto-tick-scan/` |

**核心负向证据**：

7 次 tick 失败记录（间隔严格 5 分钟）：

```
22:49:26.468  WARN  com.meiyetec.crm.CrmSyncScheduler [CRM-Scheduler] 拉取集成列表失败：
22:54:26.483  WARN  com.meiyetec.crm.CrmSyncScheduler [CRM-Scheduler] 拉取集成列表失败：
22:59:26.507  WARN  com.meiyetec.crm.CrmSyncScheduler [CRM-Scheduler] 拉取集成列表失败：
23:04:26.524  WARN  com.meiyetec.crm.CrmSyncScheduler [CRM-Scheduler] 拉取集成列表失败：
23:09:26.533  WARN  com.meiyetec.crm.CrmSyncScheduler [CRM-Scheduler] 拉取集成列表失败：
23:14:26.551  WARN  com.meiyetec.crm.CrmSyncScheduler [CRM-Scheduler] 拉取集成列表失败：
23:19:26.568  WARN  com.meiyetec.crm.CrmSyncScheduler [CRM-Scheduler] 拉取集成列表失败：
```

异常详情：
```
SQL: SELECT id,tenant_id,...,sync_token,...,sync_window_minutes
    FROM crm_integration WHERE (status = ? AND sync_enabled = ?)
Cause: java.sql.SQLSyntaxErrorException: Unknown column 'sync_token'
```

**I008 catch 吞异常**：`CrmSyncScheduler.runIncremental` 内部 catch `Exception`，仅 WARN 日志，无 traceId、无 metric、无告警。

**plan §三 偏差**：plan §三 标"反向证明 I002"。实测**反向证明 I002 + I008 catch 吞** — 比 plan 预期更严重。

---

## 7. F012 — known-ids 差集加载

**最终状态：❌ BROKEN**

| 维度 | 结果 |
|---|---|
| DB 表存在性 | ❌ **MISSING**（3/3 查询确认 0 行） |
| L0 静态可达 | ✅ |
| Runtime 实际可达 | ❌ 不可达（I002 前置阻塞）|
| 差集去重功能 | ❌ **完全失效** |
| 证据路径 | `audit/evidence/F012-known-ids-load/` |

**核心负向证据**：

DB 断言（3 个独立查询全部 0 行）：
```
SHOW TABLES LIKE 'crm_contact_map'                          → 0 rows
SELECT COUNT(*) FROM information_schema.tables WHERE ...    → 0
DESCRIBE crm_contact_map                                    → ERROR 1146 (42S02)
                                                    Table 'meiyetec.crm_contact_map' doesn't exist
```

日志观察（`loadKnownExternalIds` 调用痕迹）：
```
loadKnown / knownIds / known_ids / 差集 / known_external_ids   → 0 行匹配
```

→ 运行时**完全没机会执行**（被 I002 在更早阶段阻塞）

**plan §三 一致**：plan §三 标"反向证明 F023 PARTIAL-BLOCKER"。实测 = MISSING + 不可达 → **升级为 BROKEN**（plan 严重度仍低估）。

---

## 8. DB 副作用汇总

### 8.1 Phase 5A 起点（已记录于 db-snapshot.md）

| 表 | 行数 |
|---|---|
| `crm_integration` | 1 |
| `crm_sync_log` | 0 |
| `crm_contact_map` | 不存在 |

### 8.2 Phase 5A 终点

| 表 | 行数 | 变化 | 备注 |
|---|---|---|---|
| `crm_integration` | **3** | +2 | F001 创建 id=2 (tenant=1) + F009 SQL 创建 id=3 (tenant=2) |
| `crm_sync_log` | **0** | 0 | F005/F008 触发均事务回滚 |
| `crm_contact_map` | 仍不存在 | — | F012 验证 |

### 8.3 现有测试用 integrationId

| id | tenant_id | platform | 创建方式 | 状态 |
|---|---|---|---|---|
| 1 | 1 | SALESFORCE | 历史 init | ACTIVE, last_sync_at=NULL |
| 2 | 1 | MOCK | F001 API 创建 | ACTIVE, last_sync_at=2026-09-12 23:13:08（F008 手动设的）|
| 3 | 2 | MOCK | F009 SQL 创建 | ACTIVE, last_sync_at=NULL |

---

## 9. Issue 触发清单

| Issue | 触发 Feature | 严重程度 | plan §三 是否预期 |
|---|---|---|---|
| **I002** DDL 缺 9 列 | F001, F002, F005, F008, F009, F010, F012 | **CRITICAL**（实测比 plan 预期更严重） | ⚠ 预期 silent，实测抛 500 |
| **I008** catch 吞异常 | F002, F005, F008, F010 | HIGH | ✅ 预期 |
| **I009** 事务回滚生效 | F005, F008 | POSITIVE | ⚠ 预期副作用，实测生效 |
| **F023** `crm_contact_map` 表缺失 | F012, F020 | BLOCKER | ✅ 预期 PARTIAL-BLOCKER，实测 BROKEN |

---

## 10. plan.md 严重度校正建议

> 标注 **⚠ CALIBRATION** — 后续 Batch / Phase 5B 应考虑以下校正

| Feature | plan §三 标注 | 实测状态 | 建议校正 |
|---|---|---|---|
| F002 | I002 silent | **BROKEN** | 上调 |
| F005 | PARTIAL-BLOCKER (I002) | **BROKEN** | 上调 |
| F008 | PARTIAL-NON-BLOCKER (I002) | **BROKEN** | 上调 |
| F009 | — （不依赖 I002）| **UNVERIFIED**（实测强依赖）| 改写依赖关系 |
| F010 | I002 catch 吞 | **BROKEN**（一致）| 保持 |
| F012 | F023 PARTIAL-BLOCKER | **BROKEN** | 上调 |

---

## 11. 修复路径（不实施，仅登记）

按 **修复成本 vs 影响范围** 排序：

### 11.1 🛠 一键修复（最高 ROI）

```bash
mysql -uroot -proot123456 -P3307 meiyetec < migrate_ext_15_crm_real.sql
```

**预计恢复**：
- F002 list ✅ 恢复（补 9 列）
- F005 syncNow ✅ selectById 恢复
- F008 shouldRun ✅ 窗口判断可达
- F010 tick ✅ selectList 恢复
- F012 known-ids ✅ 表存在 + loadKnown 可达

**预计仍遗留**：
- F001 PARTIAL-NON-BLOCKER → 升级为 VERIFIED（10 列补齐后能写入）
- F009 UNVERIFIED → 待 I002 修复后 e2e 验证跨租户拒绝

### 11.2 🛠 配合监控

- 加 `crm_scheduler_tick_failed_total` Prometheus metric
- GlobalExceptionHandler 把 `BadSqlGrammarException` 单独归类（不只"数据库异常"）
- CrmSyncScheduler WARN → ERROR 级别（业务上是失败）

### 11.3 🛠 防御性加固

- MyBatis-Plus `@Interceptor` 启动期校验 entity 字段 ↔ schema 列对齐
- L0 静态校验加 entity schema mismatch check（CI 守门）

---

## 12. 关联文件

```
audit/
├── evidence/
│   ├── env.md                              # 运行时环境 + Token
│   ├── db-snapshot.md                      # DB 起点 schema
│   ├── db-snapshot-crm_integration.txt
│   ├── db-snapshot-crm_sync_log.txt
│   ├── db-snapshot-crm_contact_map-and-counts.txt
│   ├── F001-create-integration/
│   │   ├── raw-response.txt
│   │   ├── body.json
│   │   ├── db-assert-after-insert.txt
│   │   ├── db-actual-row.txt
│   │   ├── db-shop-name-column-check.txt
│   │   └── result.md
│   ├── F002-list-integration/
│   │   ├── raw-response.txt
│   │   ├── log-trace-context.txt
│   │   └── result.md
│   ├── F005-manual-sync/
│   │   ├── raw-response-force-null.txt
│   │   ├── raw-response-force1.txt
│   │   ├── log-context-force-null.txt
│   │   ├── db-before-sync.txt
│   │   ├── db-after-sync.txt
│   │   ├── tenant-relation.txt
│   │   └── result.md
│   ├── F008-sync-skip-window/
│   │   ├── db-setup-last-sync.txt
│   │   ├── raw-response.txt
│   │   ├── log-context.txt
│   │   ├── db-after-sync.txt
│   │   └── result.md
│   ├── F009-sync-cross-tenant/
│   │   ├── db-setup-tenant2.txt
│   │   ├── raw-response-clerk-tenant3.txt
│   │   ├── log-context-clerk.txt
│   │   ├── raw-response-admin-tenant3-force1.txt
│   │   ├── db-after-sync.txt
│   │   └── result.md
│   ├── F010-auto-tick-scan/
│   │   ├── log-crm-scheduler-all.txt
│   │   ├── log-crm-scheduler-context.txt
│   │   ├── db-after-tick.txt
│   │   └── result.md
│   └── F012-known-ids-load/
│       ├── db-table-check.txt
│       ├── log-known-method-search.txt
│       └── result.md
├── runtime/
│   ├── plan.md                             # 验证方案（已存在）
│   └── report-5A.md                        # ← 本文件
└── issues/
    └── issue-register.md                   # I001-I009 主索引
```

---

## 13. Phase 5A 结论

| 维度 | 结论 |
|---|---|
| 7 个 P0 Feature 验证 | ✅ 完成（7/7）|
| plan.md §三 负向证据捕获率 | ✅ 100% 命中 + 校正严重度 |
| 代码修改（规则 6）| ✅ **零修改**（仅 SQL 操作：F008 模拟窗口 + F009 创建测试集成）|
| DB 副作用 | ⚠ +2 行 integration（id=2, id=3，作为测试证据保留）|
| Production 阻塞项 | ❌ **5 BROKEN + 1 UNVERIFIED**（I002 修复前不可上线）|
| **Phase 5A 状态** | ✅ **完成 — 等用户确认进入 Phase 5B** |

---

## 14. Phase 5B 启动建议

> 等用户确认后再启动

**Phase 5B 范围（按 plan.md 顺序）**：

| 优先级 | Feature 数 | 重点 |
|---|---|---|
| P1 | 15 | Adapter / Router / 状态机 / Entity |
| P2 | 8 | 前端 UI / Dashboard / 排程配置（含 6 个 E2E-降级）|
| 静态 + CONFLICT | 5 | 仅核对，不跑 |

**Phase 5B 预期**：
- 大部分 P1 在 I002 未修复前也会抛 500（selectById / selectList 同根因）
- 静态 L0 维度：F013/F016/F017/F018/F019/F022 可达
- E2E-降级（6 个）：需要 Playwright 环境 + 用户手动跑

**用户决策待确认**：
1. 是否在 Phase 5B 之前先跑 `migrate_ext_15_crm_real.sql` 修复 I002？
2. E2E-降级 6 个 Feature 是否在 Phase 5B 内执行（需 Playwright 环境）？

---

**Phase 5A 完成 — 等待用户确认进入 Phase 5B**
