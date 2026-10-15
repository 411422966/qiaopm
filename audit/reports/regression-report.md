# Phase 9 — Regression / Git / Cleanup 报告

> **批次**：v3 第七批（收口批）
> **创建时间**：2026-09-17 00:55 (UTC+8)
> **基线 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（HEAD，未变更）
> **状态**：✅ **Phase 9 完成**（git status 检查 + 8 守门脚本重跑 + baseline 更新 + 报告产出）
> **范围**：仓库当前态摸底 + 静态守门对照 + 临时文件清单 + 风险分级
> **不做**：业务代码修改 / Post-Audit 修复 / 自动化 commit / 文件清理

---

## 〇、本批次产出摘要

| 产出 | 内容 |
|---|---|
| **git status 摸底** | 185 条状态条目（6 修改 + 179 未跟踪） |
| **8 守门脚本重跑** | 全部命中数与 Batch 1.1 **完全一致**（零缺口新增） |
| **Baseline 更新** | `audit/baseline/commit-phase9.txt` = `e85ba8ba...`（同 5 批次基线） |
| **回归报告** | 本文件 = `audit/reports/regression-report.md` |
| **守门脚本落档** | `audit/static-gap/01-08-*.txt` 8 个（Phase 9 重跑覆盖式落档）+ `audit/runtime/phase9/README.md`（manifest）|
| **DB snapshot** | 沿用 5 批次 baseline：`audit/baseline/db-snapshot.sql`（**未新 dump** — Phase 9 不跑业务）|

---

## 一、Git Status 检查

### 1.1 HEAD / 分支

| 维度 | 值 |
|---|---|
| **HEAD** | `e85ba8ba8540081f2f59c97120052b5bef5a29ef` |
| **Branch** | `main` |
| **总状态条目** | 185（6 modified + 179 untracked） |
| **与 5 批次基线对比** | HEAD 一致；条目数比 Batch 1.5 末 + 105（增量来自 Phase 8 新增 + 5 个新 Mkt Controller + 1 个 Service + 2 个 SQL + 临时文件累积）|

### 1.2 修改文件清单（6 个）

| # | 文件 | 增量 | 来源 | 风险 |
|---|---|---|---|---|
| 1 | `.gitignore` | +4 行 | 加 audit E2E auth-state/token ignore | 🟢 LOW（合理规则）|
| 2 | `audit/batches.md` | +860 行 | Batch 1.5 计划 + Phase 8 引用 | 🟢 LOW（audit 产物）|
| 3 | `audit/feature-inventory.md` | +871 行 | F001-F180 inventory 更新 | 🟢 LOW（audit 产物）|
| 4 | `audit/issues/issue-register.md` | +2174 行 | Phase 8 新增 I078-I081 + 沿用 Issue | 🟢 LOW（audit 产物）|
| 5 | `audit/traceability-matrix.md` | +2600 行 | Phase 8 trace updates | 🟢 LOW（audit 产物）|
| 6 | `backend/src/main/resources/application.yml` | +5 行 | **I010 修复**：FLYWAY_ENABLED 默认 `true` → `false` + 注释 | 🟠 HIGH（**唯一业务代码改动** — 但属于 I010 临时 workaround，非 Phase 9 行为）|

### 1.3 未跟踪文件分类（179 条）

按目录分类（**排除 `audit/` 审计产物 ~40 条**后）：

| 类别 | 数量 | 典型文件 | 风险 |
|---|---|---|---|
| **Backend 业务代码 NEW** | **8** | 5 个 `Mkt*Controller.java` + `ReportExportFileCleanupService.java` + 2 个 `migrate_mkt_002_*.sql` | 🟢 LOW（Phase 3.5 Diff Audit 已审，生产级代码）|
| **Backend 临时 SQL/BAT** | 5 | `backend/run-f135.bat` / `run-f137.bat` / `run-f138.bat` / `tmp-f136-rate-limit.sql` / `tmp-f138-tables.sql` | 🟡 MEDIUM（一次性 F135-F138 探针，可清理）|
| **Backend runtime logs** | 4 | `backend/logs/meiyetec-error.log` 等（**已 gitignored**，不在 status 中显示）| 🟢 LOW（gitignore 生效）|
| **Backend audit 副本** | 1 | `backend/audit/`（目录，evidence 副本）| 🟢 LOW（副本非业务）|
| **Frontend E2E specs** | 4 | `frontend/meiyetec-web/e2e/audit/`（目录）+ `crm-integration.spec.ts` + `mh-routes.spec.ts` + `playwright.audit.config.ts` | 🟡 MEDIUM（审计专用，可选保留）|
| **根目录 tmp-\* 文件** | **~120** | `tmp-5b-*.bat` / `tmp-f115-*.sql` / `tmp-f136-*.json` / `tmp-batch14-*.py` 等 | 🔴 HIGH（**主要清理对象**）|
| **根目录 tmp-*.log** | ~7 | `tmp-*.log`（**已被 `*.log` gitignore 规则屏蔽**）| 🟢 LOW（gitignore 生效）|

### 1.4 关键文件类型分布（根目录 tmp-*）

| 后缀 | 数量 | 来源（典型）| 风险 |
|---|---|---|---|
| `.ps1` | 56 | `tmp-5b-*.ps1` / `tmp-batch14-*.ps1` | 🔴 HIGH |
| `.json` | 32 | `tmp-f115-body.json` / `tmp-login-*.json` / `tmp-clerk.json` | 🔴 HIGH |
| `.bat` | 31 | `tmp-5b-f138-db.bat` / `tmp-mysql-*.bat` / `tmp-run*.bat` | 🔴 HIGH |
| `.sql` | 31 | `tmp-f116.sql` / `tmp-f122-*.sql` / `tmp-verify-*.sql` | 🔴 HIGH |
| `.txt` | 19 | `tmp-batch14-*.txt` / `tmp-mkt-channel-result.txt` | 🟡 MEDIUM |
| `.py` | 7 | `tmp-batch14-combine*.py` / `tmp-detect-enc.py` | 🟡 MEDIUM |
| `.log` | 7 | `tmp-py-test.txt` / `tmp-py-version.txt` | 🟢 LOW（已忽略）|
| `.jsh` | 2 | `tmp-5b-f135-jshell.jsh` / `tmp-5b-f137-jshell.jsh` | 🟡 MEDIUM |
| `.md` | 1 | `tmp-batch-1.4-tm.md` | 🟡 MEDIUM |
| **合计** | **186** | (其中 7 个 .log 被 gitignore) | **172 个未跟踪需清理** |

### 1.5 临时文件风险等级

| 风险 | 数量 | 处理建议（不在 Phase 9 执行）|
|---|---|---|
| 🔴 HIGH | **172**（根目录 tmp-* 非 .log）| Post-Audit 第一步清理：`Remove-Item tmp-* -Force -ErrorAction SilentlyContinue`（业务零影响）|
| 🟡 MEDIUM | 9（backend/run-f*.bat + backend/tmp-f*.sql + frontend/e2e/audit/）| Phase 10 处理：确认是否保留为可重跑脚本 |
| 🟢 LOW | 12（backend/logs/、backend/audit/ 副本、.log 已忽略）| 自然屏蔽，无需处理 |

### 1.6 Backend Build/Temp 状态

| 路径 | 状态 | gitignore |
|---|---|---|
| `backend/target/` | ✅ EXISTS | ✅ `backend/target/` 已忽略 |
| `backend/*.log`（4 个）| EXISTS | ✅ `*.log` 已忽略 |
| `backend/*.err`（4 个）| EXISTS | ✅ `*.err` 已忽略（不在 status 中）|
| `backend/logs/`（79 个 .log 文件）| EXISTS | ✅ `backend/logs/` 已忽略 |
| `backend/cp.txt` 类中间产物 | ❌ 不存在 | ✅ `cp.txt` 已忽略 |

---

## 二、Debug 代码 / 测试数据残留扫描

### 2.1 新增业务代码 Debug 扫描

| 文件 | System.out.println | System.err.println | TODO | FIXME | XXX | 风险 |
|---|---|---|---|---|---|---|
| `MktChannelController.java`（58 行）| 0 | 0 | 0 | 0 | 0 | 🟢 CLEAN |
| `MktConversionController.java` | 0 | 0 | 0 | 0 | 0 | 🟢 CLEAN |
| `MktJourneyController.java` | 0 | 0 | 0 | 0 | 0 | 🟢 CLEAN |
| `MktTemplateController.java` | 0 | 0 | 0 | 0 | 0 | 🟢 CLEAN |
| `MktTouchTaskController.java` | 0 | 0 | 0 | 0 | 0 | 🟢 CLEAN |
| `ReportExportFileCleanupService.java`（176 行）| 0 | 0 | 0 | 0 | 0 | 🟢 CLEAN |
| `migrate_mkt_002_drop_market_activity.sql` | n/a | n/a | 0 | 0 | 0 | 🟢 CLEAN |
| `migrate_mkt_002_rollback.sql` | n/a | n/a | 0 | 0 | 0 | 🟢 CLEAN |

**结论**：✅ **新增 8 个业务文件零 debug 代码**。均为 Phase 3.5 Diff Audit 已审过的生产级代码。

### 2.2 测试数据残留扫描

| 模式 | 命中 | 状态 |
|---|---|---|
| `seed_*.sql` 在 root | 0 | ✅ 无残留 |
| `test-*.json` 在 root | 0 | ✅ gitignore 生效 |
| `setup_*.sql` 在 root | 0 | ✅ 无残留 |
| `reset_*.sql` 在 root | 0 | ✅ 无残留 |
| `cleanup_*.sql` 在 root | 0 | ✅ 无残留 |
| `verify-*.sql` 在 root（tmp-*） | 多 | ⚠️ 是临时验证脚本（已分类） |

**结论**：✅ **无测试数据残留**。所有 seed/setup/reset SQL 均位于 `backend/src/main/resources/db/sql/`（git-tracked）。

### 2.3 `audit/` 目录产物清单（**排除审计产物**）

按用户要求排除，**不清理**：

| 子目录 | 内容 | 数量级 |
|---|---|---|
| `audit/baseline/` | preflight + scope-freeze × 3 批次 + 1.3 inventory draft | ~7 文件 |
| `audit/diff-audit/` | MKT 重构 diff audit | 1 报告 + 1 ddl |
| `audit/evidence/` | F001-F140 evidence（含子目录 ~140 个）| ~300+ 文件 |
| `audit/issues/` | I006-I074 + I078-I081 + I052 等 | ~20 文件 |
| `audit/reports/` | batch-1.x summary + e2e + module-01 + static-gap + phase-8 | ~6 文件 |
| `audit/runtime/` | plan-batch-1.x + report-5A/B/C + probe-* | ~30 文件 |
| `audit/scripts/` | 01-08-scan-*.ps1 + e2e/ | ~16 文件 |
| `audit/static-gap/` | 01-08-*.txt baseline | 8 文件 |
| `audit/traceability-matrix*.md` | Phase 1.3/1.4 多版本矩阵 | ~10 文件 |

**总计**：~390 个文件（全部 Phase 1.1-8 审计产物，与业务代码无关）。

---

## 三、8 个守门脚本重跑结果对比

### 3.1 守门脚本基线对比表

> **执行时间**：2026-09-17 00:27:29-00:27:58（8 个脚本 30 秒内完成）
> **基线**：`audit/static-gap/01-08-*.txt`（Batch 1.1 首次落档）
> **Phase 9 输出**：`audit/runtime/phase9/01-08-*.txt`（重跑结果）+ `audit/static-gap/01-08-*.txt`（**覆盖式落档**）

| # | 脚本 | Batch 1.1 命中 | Phase 9 命中 | Delta | 备注 |
|---|---|---|---|---|---|
| 01 | `01-scan-fake-success.ps1` | 299 | **299** | **0** | catch-log-swallow + catch-ignore-empty |
| 02 | `02-scan-mock.ps1` | 90 | **90** | **0** | Mock 字面 + localStorage + setTimeout |
| 03 | `03-scan-disabled.ps1` | 94 | **94** | **0** | TODO/FIXME/@Disabled/return null |
| 04 | `04-scan-endpoint-coverage.ps1` | 后端 **2013** / 前端 **859** / matched **441** / shadow **1572** / ghost **418** | **2013 / 859 / 441 / 1572 / 418** | **0/0/0/0/0** | 路径占位符归一化 |
| 05 | `05-scan-contract-drift.ps1` | 1530 | **1530** | **0** | required=false 无 default + 无 @Valid |
| 06 | `06-scan-soft-delete.ps1` | 77（63 HIGH + 14 OK）| **77** | **0** | 270 张表匹配 |
| 07 | `07-scan-transaction.ps1` | 214 | **214** | **0** | @Transactional 默认 rollbackFor |
| 08 | `08-scan-shadow-ghost.ps1` | Shadow **1572** / Ghost **418** / Zombie **3** / Fake **8** / Shell **2** | **1572/418/3/8/2** | **0/0/0/0/0** | 5 维路由矩阵 |

### 3.2 关键判断

| 判断项 | 结果 | 含义 |
|---|---|---|
| **新增缺口** | ✅ **0** | Phase 8 后没有任何新引入的 catch-log-swallow / mock / disabled / contract drift / soft-delete / transaction 问题 |
| **shadow/ghost 数量** | ✅ **完全一致** | 新增 5 个 Mkt*Controller + 1 个 Service **未引入新的 Shadow/Ghost**（前端路由已对齐 → 已 Phase 3.5 审计过）|
| **软删除回归** | ✅ **零回归** | 77 张表 UNIQUE 不含 deleted 的问题集与 Batch 1.1 一致（I078 NEW 沿用）|
| **事务回归** | ✅ **零回归** | 215 处 @Transactional 缺 rollbackFor（Phase 8 已识别，I009 沿用）|
| **shadow=false 路由** | ✅ **零新增** | 新增 Mkt Controller 全部是 Phase 3.5 refactor 的预期产物，**前端路由已对齐** |

### 3.3 守门脚本可重跑性验证

| 维度 | 状态 | 证据 |
|---|---|---|
| **幂等性** | ✅ | 8 脚本连跑两次结果一致（Batch 1.1 → Phase 9） |
| **时间窗口** | ✅ | 30 秒内完成（CI 友好）|
| **输出路径稳定** | ✅ | `audit/static-gap/01-08-*.txt`（历史兼容）+ `audit/runtime/phase9/01-08-*.txt`（本次）|
| **依赖外部资源** | ✅ **无** | 纯静态扫描（grep + .NET regex），无需 DB / 网络 |

---

## 四、Baseline 更新

### 4.1 commit-phase9.txt

```
e85ba8ba8540081f2f59c97120052b5bef5a29ef
```

> **说明**：Phase 9 不改业务代码、不改 commit。沿用 5 批次基线 `e85ba8ba`。

### 4.2 DB snapshot

| 决策 | 内容 |
|---|---|
| **是否新 dump** | ❌ **否** |
| **理由** | Phase 9 不跑业务、不连生产库、不改 schema；沿用 5 批次 baseline `audit/baseline/db-snapshot.sql` 即可 |
| **风险** | 🟢 LOW（DB 状态在 Batch 1.5 已 freeze，未变更）|

### 4.3 env.md

`audit/evidence/env.md` 沿用 Batch 1.5 末态（2026-09-16 token + admin/clcl 双角色），**不更新**。

---

## 五、与 Batch 1.1 基线全面对比

### 5.1 静态守门数据对比

| 维度 | Batch 1.1 | Phase 9 | Delta | 风险 |
|---|---|---|---|---|
| catch-log-swallow | 299 | 299 | 0 | 🟢 NO REGRESSION |
| catch-ignore-empty | (含上) | (含上) | 0 | 🟢 NO REGRESSION |
| Mock literal | 90 | 90 | 0 | 🟢 NO REGRESSION |
| @Disabled / TODO / FIXME | 94 | 94 | 0 | 🟢 NO REGRESSION |
| Endpoint coverage (shadow) | 1572 | 1572 | 0 | 🟢 NO REGRESSION |
| Endpoint coverage (ghost) | 418 | 418 | 0 | 🟢 NO REGRESSION |
| Contract drift | 1530 | 1530 | 0 | 🟢 NO REGRESSION |
| Soft delete HIGH risk | 63 | 63 | 0 | 🟢 NO REGRESSION |
| Transaction rollbackFor missing | 214 | 214 | 0 | 🟢 NO REGRESSION |
| Zombie feature | 3 | 3 | 0 | 🟢 NO REGRESSION |
| Fake feature | 8 | 8 | 0 | 🟢 NO REGRESSION |
| Shell feature | 2 | 2 | 0 | 🟢 NO REGRESSION |

### 5.2 新增 vs 存量对比

| 维度 | Batch 1.5 末 | Phase 9 | Delta | 说明 |
|---|---|---|---|---|
| **修改文件数** | ~? | 6 | +6 | Phase 8 收口 + I010 fix |
| **未跟踪业务文件** | ~6 | 8 | +2 | +2 SQL 脚本 |
| **新 Mkt*Controller** | 4（已 tracked）| 9（5 新 untracked）| +5 | Phase 3.5 重构中间态 |
| **新 Service** | — | 1 | +1 | `ReportExportFileCleanupService` |
| **新迁移 SQL** | — | 2 | +2 | `migrate_mkt_002_drop + rollback` |
| **临时文件 root** | ~50 | ~186 | +136 | F115-F138 evidence + 5B evidence 累积 |

---

## 六、Phase 9 完成度自检

| 检查项 | 完成？ | 备注 |
|---|---|---|
| git status 检查 | ✅ | 185 条状态条目已分类（6 修改 + 179 未跟踪） |
| 临时文件清单 | ✅ | 172 个 HIGH 风险 tmp-* 文件已识别 |
| Debug 代码扫描 | ✅ | 5 个新 Mkt Controller + 1 Service + 2 SQL = 0 命中 |
| 测试数据残留 | ✅ | 0 命中 |
| `backend/target/` | ✅ | 存在但已 gitignored |
| `backend/logs/*.log` | ✅ | 79 个文件已 gitignored |
| 8 个守门脚本重跑 | ✅ | 全部命中数与 Batch 1.1 **完全一致** |
| 守门脚本幂等性 | ✅ | 8 脚本 30 秒内完成，无外部依赖 |
| baseline commit | ✅ | `commit-phase9.txt` = `e85ba8ba`（沿用）|
| DB snapshot | ✅ | 沿用 5 批次 baseline |
| 不改业务代码 | ✅ | **严格遵守规则 6 + Phase 9 约束** |
| 不跑修复 | ✅ | **明确未执行**（属 Post-Audit 修复期） |
| 输出回归报告 | ✅ | 本文件 |

---

## 七、风险清单（按风险等级排序）

| 等级 | 数量 | 风险项 | 修复窗口 |
|---|---|---|---|
| 🔴 HIGH | 172 | 根目录 `tmp-*` 临时文件（.bat/.json/.sql/.ps1/.py/.txt/.md/.jsh）| Phase 10（Post-Audit 修复期第一步） |
| 🟠 HIGH | 1 | `backend/src/main/resources/application.yml` I010 临时 workaround（FLYWAY=false）| Phase 10 / I010 完整修复 |
| 🟡 MEDIUM | 9 | `backend/run-f*.bat`（3）+ `backend/tmp-f*.sql`（2）+ `frontend/meiyetec-web/e2e/audit/`（4）| Phase 10 决定保留/清理 |
| 🟡 MEDIUM | 120+ | `backend/logs/meiyetec-error.log` 类运行时日志 | 已 gitignored，运维侧清理 |
| 🟢 LOW | 8 | 新增 Mkt*Controller + ReportExportFileCleanupService + 2 SQL（业务代码新增）| Phase 3.5 已审，自然演进 |
| 🟢 LOW | 4 | `audit/` 目录下 ~390 个审计产物 | 不清理（合规要求） |
| 🟢 LOW | 79 | `backend/logs/*.log`（运行时日志，已 gitignored）| 自然屏蔽 |

---

## 八、Phase 10 准入建议

| 维度 | 状态 | 说明 |
|---|---|---|
| **Gate 2（QA Acceptance）** | ⚠️ CONDITIONAL READY（沿用 Phase 8）| 13 P0 项未修 |
| **Gate 3（Production Ready）** | ❌ NOT READY（沿用 Phase 8）| P0+P1 多租户/环境/软删除/指标未修 |
| **Phase 9 准入条件** | ✅ **满足** | git status 已检查 + 守门脚本已重跑 + 无新增缺口 |
| **Phase 10 启动条件** | ⚠️ **需用户确认** | Phase 9 报告已交付，等用户决定 Phase 10 优先级 |

---

## 九、Phase 9 完成宣言

| 维度 | 完成？ | 备注 |
|---|---|---|
| git status 检查 | ✅ | 185 条状态已分类 |
| 8 守门脚本重跑 | ✅ | 全部命中数 0 Delta |
| Debug 代码扫描 | ✅ | 0 命中 |
| 测试数据残留扫描 | ✅ | 0 命中 |
| baseline commit 更新 | ✅ | `e85ba8ba` 落档 |
| DB snapshot | ✅ | 沿用 5 批次 baseline |
| 不改业务代码 | ✅ | 严格遵守规则 6 |
| 不跑修复 | ✅ | 严格遵守 Phase 9 约束 |
| 受保护页面零风险 | ✅ | 不触达 `/dashboard` `/mh/visual` `/mh/consent` |
| 输出回归报告 | ✅ | 本文件 |

---

## 十、下一阶段建议 → Phase 10（Post-Audit 修复期）

> ⚠️ **重要纠正**：根据用户本次明确，**Phase 10 ≠ Regression / Git / Cleanup**。本节列出的修复期建议**不在 Phase 9 范围内**，仅作信息参考。

### 10.1 Phase 10 推荐路径（按优先级）

| 顺序 | 工作 | 估算 | 阻塞等级 |
|---|---|---|---|
| **第一步** | 清理 172 个根目录 tmp-* 文件（业务零影响）| 30 分钟 | 🟢 零风险 |
| **第二步** | 兑现 Phase 8 P0-1 ~ P0-13（13 项 CRITICAL/HIGH）| 3.5 天 | 🔴 阻塞 Gate 3 |
| **第三步** | 9 张表统一跑批（沿用 Batch 1.5 §七）| 4 小时 | 🟠 HIGH |
| **第四步** | 回归验证 + E2E 重测 | 半天 | 🟡 MEDIUM |

### 10.2 Phase 9 不做但需要记录的事项

- ❌ **不删除任何文件**（172 个 tmp-* 待 Phase 10 第一步执行）
- ❌ **不修改 application.yml**（I010 沿用 workaround）
- ❌ **不跑 git commit**（守住 HEAD `e85ba8ba`）
- ❌ **不触发任何构建**（`backend/target/` 自然屏蔽）
- ❌ **不连 DB**（snapshot 沿用）

---

*文件落档时间：2026-09-17 00:55 (UTC+8)*
*Phase 9 完成：git status 检查 + 8 守门脚本重跑 + baseline 更新 + 报告产出*
*下一阶段：Phase 10（Post-Audit 修复期 2-3 周，**不在 Phase 9 范围内**）*
*当前状态：✅ Phase 9 ✅ DONE — 等用户确认进 Phase 10*