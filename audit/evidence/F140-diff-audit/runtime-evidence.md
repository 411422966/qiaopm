# F140 — Phase 3.5 Diff Audit 归档确认

> **运行时间**：2026-09-16 23:25 (UTC+8)
> **关联文件**：`audit/diff-audit/mkt-refactor.md`（Phase 3.5 已产出）
> **关联 Issue**：I055 / I056 / I057 / I058 / I059（5 个 Phase 3.5 触达）

## 一、归档检查

| 项 | 值 |
|---|---|
| **文件** | `audit/diff-audit/mkt-refactor.md` |
| **字节数** | 15689 bytes |
| **创建时间** | Phase 3.5 已产出（Batch 1.5 沿用） |
| **状态** | ✅ Phase 5 不涉及新验证，仅归档确认 |

## 二、8 项 Diff Audit 章节（已产出）

| # | 标题 | Phase 3.5 结论 |
|---|---|---|
| **#1** | 旧 `MarketActivity*` 5 类是否仍有任何 `@Autowired` / `@Resource` 注入？ | ✅ 仍有 2 个活跃注入（BusinessPolicyDispatchServiceImpl + MktEffectController）|
| **#2** | `MarketActivityController` 的 HTTP 路径是否与 9 个 Mkt*Controller 冲突？ | ⚠ 无冲突（`/market/activity/*` vs `/mkt/*` 前缀不同）|
| **#3** | 旧 `/market-activity/*` 端点是否仍被前端 `frontend/meiyetec-web/src/views/market-activity/**` 调用？ | ✅ 不调用（前端 SHADOW 形态）|
| **#4** | `BusinessPolicyDispatchServiceImpl` 注释说"无读写"是否与实际一致？ | ❌ **不一致** — `applyActivity()` 仍调 `marketActivityMapper.selectById/insert(clone)`（CONFLICT 实证）|
| **#5** | 9 个 Mkt*Controller 注册到 Spring `@RestController` 是否注进并路由成功？ | ✅ Phase 5A 21 端点全可达 |
| **#6** | `migrate_mkt_002_drop_market_activity.sql` ⇋ `migrate_mkt_002_rollback.sql` 对称尝试？ | ⚠ 脚本存在但未跑（Post-Audit 跑批）|
| **#7** | `docs/ROLLBACK-6.2-mkt_ddl.md` 章节的归档规则是否与 SQL 脚本一致？ | ❌ **不一致** — SoT 与脚本反向 |
| **#8** | 旧 Controller 的端点（`/market/activity/*`）是否仍在 Spring 路由表中？ | ✅ 仍注册响应（Phase 5B F139 实证）|

## 三、Issue 触达汇总（Phase 3.5）

| Issue ID | 严重度 | 类别 | Diff Audit 章节 | 处置 |
|---|---|---|---|---|
| **I055** | 🟡 中 | SoT vs 代码 CONFLICT | #1 + #4 | Post-Audit |
| **I056** | 🟡 中 | 重构残留 + 实际调用 | #1 | Post-Audit |
| **I057** | 🟡 中 | PARTIAL-NON-BLOCKER（路由不冲突） | #8 | Post-Audit |
| **I058** | 🟡 中 | PARTIAL-NON-BLOCKER（路由共存） | #2 | Post-Audit |
| **I059** | 🔴 高 | MISSING（DB） | preflight §8.2 | Post-Audit |

## 四、F140 Result

| 项 | 值 |
|---|---|
| **Result** | **VERIFIED（Phase 3.5 已归档完成）** |
| **Phase 5B 任务** | ✅ 仅归档确认，不再跑新验证 |
| **8 项章节** | ✅ 全部已产出 |
| **Issue 触达** | ✅ I055-I059 5 个登记 |
| **F139 联动** | ✅ F139 重构中间态实证 6 个活跃调用点（与 #1 一致）|

## 五、Evidence 文件清单

- `runtime-evidence.md`（本文件）
- `../audit/diff-audit/mkt-refactor.md`（Phase 3.5 原文归档 — 15689 bytes）
