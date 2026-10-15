# I013 — syncSchedule 字段 dead code（定义但无任何代码读取）

| 维度 | 值 |
|---|---|
| **Issue ID** | I013 |
| **创建时间** | 2026-09-13 00:55 (UTC+8) |
| **Phase** | 5B — Runtime Verification |
| **类别** | dead code（Entity 字段 + DDL 列均定义，但无任何代码读取）|
| **严重度** | 🟡 中（功能层面无影响，但维护层面误导）|
| **影响 Feature** | F030（sync 配置）|
| **关联 Issue** | 无（独立问题）|
| **修复阶段** | **审计后** |

---

## 1. 现象

### 1.1 Entity 字段定义

```java
// backend/src/main/java/com/meiyetec/entity/CrmIntegration.java:70
private String syncSchedule;   // cron 表达式（默认 "0 */30 * * * *"）
```

### 1.2 DDL 列定义

```sql
-- backend/src/main/resources/db/sql/migrate_ext_15_crm_real.sql:10-12
ADD COLUMN sync_token      VARCHAR(255) DEFAULT NULL,
ADD COLUMN sync_schedule   VARCHAR(64)  DEFAULT '0 */30 * * * *',
ADD COLUMN sync_enabled    TINYINT      DEFAULT 1,
```

### 1.3 代码使用情况

**全代码库 grep 结果**（5B F030 实测）：

```bash
grep -r "getSyncSchedule\|cfg.syncSchedule\|crm_integration.sync_schedule" backend/src/main/java
→ 0 匹配
```

→ **`syncSchedule` 字段定义但无任何代码读取！**

---

## 2. 实际排程机制

### 2.1 当前实现的 tick

```java
// backend/src/main/java/com/meiyetec/crm/CrmSyncScheduler.java:43-46
@Scheduled(fixedDelayString = "${crm.sync.tick-interval-ms:300000}",
           initialDelay = 60_000L)
public void tick() { ... }
```

→ **tick 间隔硬编码为 5 分钟（300000 ms）**

### 2.2 配置文件

```yaml
# backend/src/main/resources/application.yml
crm:
  sync:
    tick-interval-ms: ${CRM_TICK_INTERVAL_MS:300000}    # 默认 5 分钟
```

→ **tick 间隔全局配置，**与 `cfg.syncSchedule` 无关**

### 2.3 设计意图 vs 实测

| 维度 | 设计意图（plan §0.4）| 实测代码 |
|---|---|---|
| 自动排程 | `@Scheduled(fixedDelay=...)` → 每 5 分钟扫 | ✅ 一致 |
| cron 表达式 | `syncSchedule` 字段（per-integration）| ❌ **dead code** |
| 窗口判断 | `lastIncrementalSyncAt + syncWindowMinutes ≤ now` | ✅ 代码层正确 |
| syncEnabled 启用 | "排程是否启用（status=ACTIVE ∧ syncEnabled=1）"| ✅ 一致 |

→ **plan 与代码的差距：syncSchedule 字段定义但未实际使用**

---

## 3. 严重度评估

| 标准 | I013 命中？ |
|---|---|
| 🟢 LOW | ❌ |
| 🟡 **MEDIUM** | ✅（功能无影响但维护误导）|
| 🟠 HIGH | ❌ |
| 🔴 CRITICAL | ❌ |

### 3.1 影响维度

| 维度 | 影响 |
|---|---|
| 功能正确性 | ✅ 无（当前实现用全局配置）|
| 性能 | ✅ 无 |
| 运维 | ⚠️ 误导：以为可以 per-integration 配 cron |
| 数据一致性 | ✅ 无 |
| 测试覆盖 | ⚠️ 字段声明了却没测（dead code）|

### 3.2 维护风险

- 新人看到 `syncSchedule` 字段会以为可以配置 per-integration cron
- 但实际是 `tick-interval-ms` 全局配置
- → 文档与代码不一致，未来重构可能引入 bug

---

## 4. 修复建议（**审计后**）

| 方案 | 改动 | 优点 | 缺点 |
|---|---|---|---|
| **① 实现 per-integration cron** | 解析 `cfg.syncSchedule` 用 `TaskScheduler.schedule(Runnable, Trigger)` 动态排程 | 真正 per-integration cron | 实现复杂 + 资源开销 |
| **② 删除字段 + 列** | Entity + DDL 同步移除 | 消除误导 | 若未来需要 per-integration cron，需重新加回 |
| **③ 改用固定值** | tick 间隔改读 cfg.syncSchedule（仅作为分钟数解析）| 简单 | 不是真正的 cron |
| **④ 接受现状** | 不改（标记 dead code + 注释说明）| 不动 | 误导持续 |

**推荐**：②（删除字段 + 列），理由：
- 当前全局配置够用
- per-integration cron 不是核心需求
- 减少 Entity 字段 = 减少维护负担

### 4.1 推荐实现

```java
// CrmIntegration.java — 移除 syncSchedule 字段
// migrate_ext_15_crm_real.sql — 移除 sync_schedule 列
// application.yml — 保留 crm.sync.tick-interval-ms 全局配置
// CrmSyncScheduler.java — 保留 @Scheduled(fixedDelayString = ...) 不变
```

---

## 5. Phase 5B 验证策略

| 验证方式 | 是否适用 |
|---|---|
| **Static-Only** | ✅ 关键。grep 全文确认 `syncSchedule` 无任何引用 |
| **API-only（curl）** | ❌ 不适用（无 API 读写 syncSchedule）|
| **DB-only** | ❌ 当前 I002 阻塞，表无 sync_schedule 列 |
| **Playwright E2E** | ❌ 不适用 |

### 5.1 Phase 5B 已做的

- ✅ Entity 字段审查（`CrmIntegration.java:70`）
- ✅ DDL 列审查（`migrate_ext_15_crm_real.sql:11`）
- ✅ 全代码库 grep（0 匹配）
- ✅ tick 代码审查（`CrmSyncScheduler.java:43-46` 用 `@Scheduled` 而非 cfg.syncSchedule）

---

## 6. 关联

- `audit/issues/issue-register.md` §十三 I013 新登记
- `audit/evidence/F030-sync-config/result.md`（F030 验证完整报告 + I013 关键证据）
- `audit/runtime/plan.md` §一.2 F030 + §0.4 排程描述
- `audit/runtime/report-5B.md` §2.8 F030 章节（PARTIAL-BLOCKER + syncSchedule dead code）
- `backend/src/main/java/com/meiyetec/entity/CrmIntegration.java:70`
- `backend/src/main/java/com/meiyetec/crm/CrmSyncScheduler.java:43-46`
- `backend/src/main/resources/db/sql/migrate_ext_15_crm_real.sql:11`

---

**I013 登记完成。修复优先级 P2（无功能影响但消除维护误导）。**
