# F140 Diff Audit 归档确认

**生成时间**：2026-09-16 22:05 (UTC+8)
**Phase**：5B（P2 — 重构批次特有 Phase 3.5 Diff Audit 归档）
**依据**：`audit/diff-audit/mkt-refactor.md`（Phase 3.5 已产出）
**关联 Issue**：I055（SoT 反向）+ I067（重构中间态）+ I068（SoT 字段不一致）
**F140 性质**：归档确认，不重复跑新验证。

---

## 一、Phase 3.5 Diff Audit 8 项已产出（沿用）

| # | 审计项 | 结论 | 证据位置 |
|---|---|---|---|
| 1 | 旧 `MarketActivity*` 5 个类是否被注入？ | ❌ **ACTIVE 6 个调用点**（与 SoT 反向） | mkt-refactor.md §2 #1 |
| 2 | 旧 `MarketActivityController` 路由与新 9 Mkt*Controller 冲突？ | ❌ **不冲突**（旧 /market/activity/* vs 新 /mkt/*） | §2 #2 |
| 3 | 旧 `/market-activity/*` 端点前端是否引用？ | ❌ **前端已迁移** | §2 #3 |
| 4 | `BusinessPolicyDispatchServiceImpl` 注释 vs 代码一致性？ | ❌ **不一致**（注释"已无读写"，实际 applyActivity 读写） | §2 #4 |
| 5 | 9 Mkt*Controller 注册到 Spring 是否成功？ | ✅ **9 Controller 全部注册成功**（Phase 5A 21 端点 100% 可达） | §2 #5 + Phase 5A N1 |
| 6 | `migrate_mkt_002_drop_market_activity.sql` ↔ `migrate_mkt_002_rollback.sql` 对称性？ | ✅ **完全对称**（归档表名 `market_activity__archived_20260910` 三处一致） | §2 #6 |
| 7 | `docs/ROLLBACK-6.2-mkt_ddl.md` 描述归档表名与 SQL 一致？ | ✅ **完全一致** | §2 #7 |
| 8 | 旧 Controller 端点是否仍在 Spring 路由表？ | ⚠️ **仍在路由表**（5B F139 curl 实测 200 响应） | §2 #8 + Phase 5B F139 |

---

## 二、5B 复测与 3.5 一致性

| 维度 | Phase 3.5 结论 | 5B 复测 | 一致性 |
|---|---|---|---|
| 旧 MarketActivityController 路由 | 仍在路由表 | curl /market/activity/{id}/page/stats 全部 200 | ✅ 一致 |
| market_activity 11 列 | 已 DESCRIBE 验证 | 5B 复测：11 列 + 1 行历史 | ✅ 一致 |
| 6 个活跃调用点 | Phase 3.5 已量化 | 5B 复测：BusinessPolicyDispatchServiceImpl×3 + MktEffectController×2 + MarketActivityServiceImpl×10+ | ✅ 一致 |
| SoT vs 代码 CONFLICT | I055 登记 | 5B 复测：applyActivity 仍主动 insert → DROP 表后 NPE | ✅ 一致 |

---

## 三、归档与决策

### 3.1 F140 归档状态

| 项 | 状态 |
|---|---|
| Phase 3.5 Diff Audit 产出 | ✅ `audit/diff-audit/mkt-refactor.md`（已落档） |
| F140 在 Phase 5 不涉及新验证 | ✅ 仅做归档确认（plan §三 3.3 F140 验证方式 = 静态） |
| Issue 关联 | I055 / I067 / I068 已在 §2 登记 |
| Fix Phase | **Post-Audit**（沿用 Q3 第六次统一口径） |

### 3.2 F140 Result

| Result | 标签 | 实证 |
|---|---|---|
| **VERIFIED** | Phase 3.5 已完成 | 8 项审计 + I055/I067/I068 关联 + 5B 复测一致 |

---

## 四、跨 Feature 联动（仅记录，不重复跑）

| 联动 Feature | 依赖 F140 的方式 | 5B 状态 |
|---|---|---|
| F139 重构中间态 | 复用 §2 #1/#8 + Phase 3.5 SoT 反向证据 | ✅ 已复用 |
| F138 BusinessPolicyDispatchController | §2 #4 applyActivity 是 m12 dispatch 的真实调用链 | ✅ 已复用 |

---

**F140 归档完成。Phase 3.5 Diff Audit 8 项 + I055-I068 关联全部落档。F140 不涉及 5B 新验证，仅做归档确认。**