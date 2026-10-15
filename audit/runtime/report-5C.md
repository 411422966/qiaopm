# Phase 5C — DDL & EXT-15 迁移状态报告

> **Phase**：5C — Static DDL Verification
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`
> **提交时间**：2026-09-13 18:01 (UTC+8)
> **范围**：F022（DDL 检查）+ F023（EXT-15 迁移检查）+ F024-F028（引用 E2E 报告）
> **不动业务代码 + 不跑迁移**（严格遵守 D5 决策）

---

## 1. 验证方法

| 维度 | 值 |
|---|---|
| DB 连接 | `127.0.0.1:3307`（本地 dev MySQL 8.0 Docker，**非生产**）|
| 账号 | `root/root123456`（来自 `audit/evidence/env.md` §4）|
| 字符集 | `utf8mb4 / utf8mb4_bin` |
| 命令工具 | mysql CLI（`mysql --version` 已可用）|
| 对比基准 | `backend/src/main/resources/db/sql/migrate_ext_15_crm_real.sql` |

### 1.1 执行的 SQL

```bash
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 --default-character-set=utf8mb4 \
  -e "SHOW CREATE TABLE crm_integration\G" meiyetec
mysql ... -e "SHOW CREATE TABLE crm_sync_log\G" meiyetec
mysql ... -e "SHOW TABLES LIKE 'crm_contact_map';" meiyetec
mysql ... -e "SHOW TABLES LIKE 'crm%';" meiyetec
```

→ 4 条 mysql 命令，全部成功执行。

---

## 2. Feature 级结果

| Feature ID | DB-only 验证 | 结果 | Evidence |
|---|---|---|---|
| **F022** | DDL 实际 vs 期望对比 | ❌ **PARTIAL-BLOCKER**（crm_integration 缺 9 列 + 2 索引 / crm_sync_log 缺 3 列 / crm_contact_map 表完全缺失）| `audit/evidence/F022-crm-ddl-check/` |
| **F023** | EXT-15 迁移完成度 | ❌ **BROKEN**（EXT-15 迁移 0/15 已执行，符合 D5 决策）| `audit/evidence/F023-ext15-migration-check/` |
| **F024** | E2E（不重做） | ❌ **PARTIAL-BLOCKER**（I002 阻塞）| `audit/evidence/F024-list-ui/e2e-result.md` |
| **F025** | E2E（不重做） | ⚠️ **PARTIAL-BLOCKER + I011 二次实证**（dialog 不关 + DB 已 INSERT）| `audit/evidence/F025-create-form/e2e-result.md` |
| **F026** | E2E（不重做） | ⚠️ **PARTIAL-NON-BLOCKER**（I003 + I016 + I002）| `audit/evidence/F026-sync-log-tab/e2e-result.md` |
| **F027** | E2E（不重做） | ❌ **PARTIAL-BLOCKER**（I005 C2 + I002）| `audit/evidence/F027-sync-button/e2e-result.md` |
| **F028** | E2E（不重做） | ⚠️ **PARTIAL-NON-BLOCKER**（I005 C4 + I002）| `audit/evidence/F028-object-mappings/e2e-result.md` |

---

## 3. F022 — DDL 检查详细

### 3.1 实际 DDL 抓取

| 表 | 来源命令 | 抓取结果 |
|---|---|---|
| `crm_integration` | `SHOW CREATE TABLE crm_integration\G` | 22 列 + 索引 `idx_tenant` + AUTO_INCREMENT=6 |
| `crm_sync_log` | `SHOW CREATE TABLE crm_sync_log\G` | 12 列 + 索引 `idx_tenant` |
| `crm_contact_map` | `SHOW TABLES LIKE 'crm_contact_map'` | **0 行（表不存在）** |
| `crm_*` 全景 | `SHOW TABLES LIKE 'crm%'` | 32 个表 |

**Evidence**：
- `audit/evidence/F022-crm-ddl-check/ddl-crm_integration.txt`（实测 DDL）
- `audit/evidence/F022-crm-ddl-check/ddl-crm_sync_log.txt`（实测 DDL）
- `audit/evidence/F022-crm-ddl-check/ddl-crm_contact_map.txt`（empty set）
- `audit/evidence/F022-crm-ddl-check/ddl-crm-tables-list.txt`（32 个 crm_* 表清单）

### 3.2 与 EXT-15 期望对比

| 维度 | 期望（EXT-15 迁移后）| 实际（5C 抓取）| 差异 |
|---|---|---|---|
| `crm_integration` 列数 | 31（22 + 9 新增）| 22 | **-9 列** |
| `crm_integration` 索引 | 3（PRIMARY + idx_tenant + idx_sync_enabled + idx_platform_status = 4）| 2（PRIMARY + idx_tenant）| **-2 索引** |
| `crm_integration` Engine / Charset | InnoDB / utf8mb4_bin | InnoDB / utf8mb4_bin | ✅ 一致 |
| `crm_sync_log` 列数 | 15（12 + 3 新增）| 12 | **-3 列** |
| `crm_contact_map` | 存在（13 列 + 4 索引）| **不存在** | **-1 表 + -4 索引** |

### 3.3 F022 状态

**❌ PARTIAL-BLOCKER（继承 I002）**

| 项 | 评估 |
|---|---|
| DDL 完整性 | 22+12 = 34 列 / 期望 46 列 = **73.9%** |
| 索引完整性 | 2 / 期望 8（含 4 个 crm_contact_map）= **25%** |
| 字符集 / 引擎 | ✅ 100% 一致 |
| 表存在性 | 2 / 3 = **66.7%** |

→ 缺 EXT-15 全部新增结构，与 5A/5B 一致（D5 决策保留）

完整证据：`audit/evidence/F022-crm-ddl-check/ddl-compare.md`

---

## 4. F023 — EXT-15 迁移检查

### 4.1 迁移完成度矩阵

| 表 | 期望变更 | 已完成 | 完成度 |
|---|---|---|---|
| `crm_integration` | 9 列 + 2 索引 = 11 项 | 0 | **0%** |
| `crm_contact_map` | CREATE TABLE = 1 项 | 0 | **0%** |
| `crm_sync_log` | 3 列 = 3 项 | 0 | **0%** |
| **合计** | **15 项** | **0** | **0%** |

### 4.2 与用户决策一致性

| 决策 | 内容 | 状态 |
|---|---|---|
| **D5** | 保留 PARTIAL-BLOCKER / **不跑 EXT-15** | ✅ 严格遵守 |
| **D2** | 不修 `.cursor/rules/ext-24-crm-integration.mdc` | ✅ 严格遵守 |
| **D3** | 不动业务代码 | ✅ 严格遵守 |
| **D4** | I011 / I008 修复延后 | ✅ 严格遵守 |

→ **F023 = BROKEN 是设计意图**（与 D5 决策完全一致）

### 4.3 F023 状态

**❌ BROKEN（EXT-15 迁移 0% 完成）** — 但属于「设计意图 BROKEN」，非事故

完整证据：`audit/evidence/F023-ext15-migration-check/migration-status.md`

---

## 5. F024-F028 — E2E 结果引用（不重做）

按用户决策，本 Phase 直接引用 Phase 5B 第三轮 E2E 汇总报告 `audit/reports/e2e-summary.md`。

| Feature | 状态 | 主要 Issue | 详细 |
|---|---|---|---|
| F024 | ❌ PARTIAL-BLOCKER | I002 | `audit/evidence/F024-list-ui/e2e-result.md` |
| F025 | ❌ PARTIAL-BLOCKER + **I011 二次实证** | I002 + I011 | `audit/evidence/F025-create-form/e2e-result.md` |
| F026 | ⚠️ PARTIAL-NON-BLOCKER | I003 + I016 + I002 | `audit/evidence/F026-sync-log-tab/e2e-result.md` |
| F027 | ❌ PARTIAL-BLOCKER | I005 C2 + I002 | `audit/evidence/F027-sync-button/e2e-result.md` |
| F028 | ⚠️ PARTIAL-NON-BLOCKER | I005 C4 + I002 | `audit/evidence/F028-object-mappings/e2e-result.md` |

E2E 12 测试汇总：**5 PASS + 7 FAIL**（无假 PASS）

---

## 6. 与 Phase 5A / 5B 一致性

| 维度 | 5A | 5B | 5C | 一致？ |
|---|---|---|---|---|
| `crm_integration` 列数 | 22 | 22 | 22 | ✅ |
| `crm_sync_log` 列数 | 12 | 12 | 12 | ✅ |
| `crm_contact_map` 表 | 不存在 | 不存在 | 不存在 | ✅ |
| EXT-15 缺失列 | 9 + 3 = 12 | 9 + 3 = 12 | 9 + 3 = 12 | ✅ |
| EXT-15 缺失索引 | 2 + 4 = 6 | 2 + 4 = 6 | 2 + 4 = 6 | ✅ |
| Flyway 状态 | 禁用 | 禁用 | 禁用 | ✅ |

→ **DDL 在 5A→5B→5C 期间无变化**（D5 决策严格保留 PARTIAL-BLOCKER 状态）

---

## 7. 严重度矩阵

| Feature | 状态 | 阻塞 EXT-24? | 解锁方式 |
|---|---|---|---|
| **F022** | ❌ PARTIAL-BLOCKER | ✅ 阻塞 | 跑 EXT-15 |
| **F023** | ❌ BROKEN（设计意图）| ✅ 阻塞 | 跑 EXT-15 |
| F024 | ❌ PARTIAL-BLOCKER | ✅ 阻塞 | 跑 EXT-15 |
| F025 | ❌ PARTIAL-BLOCKER + I011 | ✅ 阻塞 | 跑 EXT-15 + I011 重构 |
| F026 | ⚠️ PARTIAL-NON-BLOCKER | ⚠️ 部分 | 跑 EXT-15 + I003/I016 重构 |
| F027 | ❌ PARTIAL-BLOCKER | ✅ 阻塞 | 跑 EXT-15 + I005 C2 修 |
| F028 | ⚠️ PARTIAL-NON-BLOCKER | ⚠️ 部分 | 跑 EXT-15 + I005 C4 表单补 |

**根因占比**：
- 6 / 7 个 Feature 的根因包含 I002（DB 缺列）
- 全部 7 个 Feature 解锁需要跑 EXT-15 迁移

---

## 8. Phase 5C 总结

### 8.1 验证贡献

1. **F022**：用 mysql CLI 实证 DDL 缺口（22+12 列 / 期望 46 列 = 73.9%）
2. **F023**：静态 SQL 对比确认 EXT-15 0/15 完成（D5 设计意图）
3. **F024-F028**：不重做 E2E，直接引用 Phase 5B 第三轮汇总
4. **5A→5B→5C 一致性**：DB DDL 期间无变化（D5 严格保留）

### 8.2 关键观察

- ✅ 字符集 / 引擎一致（InnoDB / utf8mb4_bin）
- ❌ 全部 EXT-15 新增结构缺失
- ❌ crm_contact_map 表完全缺失
- ❌ crm_integration / crm_sync_log 缺 EXT-15 列

### 8.3 未确认项

| 项 | 原因 | 影响 |
|---|---|---|
| 迁移 SQL 实际可执行性 | 不执行迁移（D5）| 未知（一旦跑可能索引冲突）|
| 数据兼容性 | 不验证（D5）| 未知（已有 5 行数据 AUTO_INCREMENT=6）|

---

## 9. 交叉引用

- **Phase 5A**：F019 entity DDL + I002 PARTIAL-BLOCKER
- **Phase 5B 第一轮**：F020 + F012 实测 EXT-15 缺失影响
- **Phase 5B 第三轮**：F024-F028 E2E 12 测试（5 PASS + 7 FAIL）
- **Phase 5C（本 Phase）**：F022 + F023 静态 DDL 实证
- **Issue Register**：I002 / I003 / I005 C2 / I005 C4 / I008 / I011 / I016
- **关联报告**：`audit/reports/e2e-summary.md` / `audit/runtime/report-5A.md` / `audit/runtime/report-5B.md`
- **决策变更**：D2 / D3 / D4 / D5（保持不修）

---

## 10. 下一阶段建议

按用户决策，Phase 5C 已完成验证任务。下一阶段选项：

| 选项 | 内容 | 阻塞 |
|---|---|---|
| **A. 维持现状** | D5 保留 PARTIAL-BLOCKER，进入 Phase 6 上线预演 | 无 |
| **B. 跑 EXT-15** | 实际执行迁移 → F024-F028 E2E 全 PASS | 需用户撤销 D5 |
| **C. 单点修复** | 修 I005 C2（force 参数）| 不需撤销 D5 |
| **D. I011 重构** | select-after-write 重构 → 消除 Fake Failure | 不需撤销 D5 |

⏸ **等待用户决策**
