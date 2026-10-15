# F030 — sync 配置（API-only 间接 + I002 阻塞）

> **Phase**：5B Runtime Verification
> **Feature ID**：F030
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`

---

## 1. sync 配置字段 L0

Entity 文件：`backend/src/main/java/com/meiyetec/entity/CrmIntegration.java`

| 字段 | 行号 | 用途 |
|---|---|---|
| `syncSchedule` | line 70 | cron 表达式（默认 "0 */30 * * * *"）|
| `syncEnabled` | line 73 | 0=禁用 1=启用 |
| `syncWindowMinutes` | line 85 | 增量同步窗口（分钟），默认 30 |

DB DDL：`backend/src/main/resources/db/sql/migrate_ext_15_crm_real.sql:10-18`

```sql
ADD COLUMN sync_token              VARCHAR(255) DEFAULT NULL
ADD COLUMN sync_schedule           VARCHAR(64)  DEFAULT '0 */30 * * * *'
ADD COLUMN sync_enabled            TINYINT      DEFAULT 1
ADD COLUMN last_incremental_sync_at DATETIME    DEFAULT NULL
...
ADD COLUMN sync_window_minutes     INT          DEFAULT 30
```

→ **3 个 sync 配置字段全在 EXT-15 DDL 中**

## 2. F030 行为分析（间接）

F030 通过 F008 syncNow 验证：
- `syncWindowMinutes` 缺列 → shouldRun 抛 I002 → 返回 500 而非 `skipped=true`
- `syncEnabled` 缺列 → tick:47 selectList 抛 I002 → catch 吞 → tick 静默失败
- `syncSchedule` 缺列 → 当前代码未实际使用（plan §0.4 描述的 cron 表达式仅在注释）

### 2.1 syncSchedule 实际使用情况

```java
// CrmSyncScheduler.java:43-46
@Scheduled(fixedDelayString = "${crm.sync.tick-interval-ms:300000}",
           initialDelay = 60_000L)
public void tick() {
    ...
}
```

→ **tick 间隔硬编码为 `fixedDelayString = "${crm.sync.tick-interval-ms:300000}"`（默认 5 分钟）**
→ **`syncSchedule` 字段实际未使用**（与计划"cron 表达式"语义不符）

### 2.2 syncEnabled 实际使用情况

```java
// CrmSyncScheduler.java:48-51
enabled = integrationMapper.selectList(new LambdaQueryWrapper<CrmIntegration>()
        .eq(CrmIntegration::getStatus, "ACTIVE")
        .eq(CrmIntegration::getSyncEnabled, 1));      // ← 读 sync_enabled 列
```

→ **syncEnabled 在 tick 阶段使用**（筛选 ACTIVE + syncEnabled=1）

### 2.3 syncWindowMinutes 实际使用情况

```java
// CrmSyncScheduler.java:72-79
public boolean shouldRun(CrmIntegration cfg) {
    Integer window = cfg.getSyncWindowMinutes() == null ? 30 : cfg.getSyncWindowMinutes();
    LocalDateTime last = cfg.getLastIncrementalSyncAt();
    if (last == null) return true;
    return last.plusMinutes(window).isBefore(LocalDateTime.now());
}
```

→ **syncWindowMinutes 在 shouldRun 阶段使用**（与 lastIncrementalSyncAt 配合）

## 3. 实测（间接）

### 3.1 F008 syncNow → syncWindowMinutes 缺列

5A F008 已测：POST sync 不带 force → `Unknown column 'sync_window_minutes'` → 500

### 3.2 F010 tick → syncEnabled 缺列

5A F010 已测：tick 7 次失败，catch 吞，**sync_enabled 列缺导致 tick 整链路失效**

### 3.3 当前 schedule 行为

实际 tick 间隔：**5 分钟（300000 ms）**（与 `crm.sync.tick-interval-ms:300000` 配置一致）

```bash
# backend/src/main/resources/application.yml
crm:
  sync:
    tick-interval-ms: ${CRM_TICK_INTERVAL_MS:300000}
```

→ **当前 schedule 是全局配置，非单集成 schedule**
→ **`syncSchedule` 字段定义但未在代码中使用**（dead code）

## 4. ⚠️ sync 配置完整度

| 字段 | DDL 定义 | Entity 声明 | 代码使用 | 当前生效 |
|---|---|---|---|---|
| syncSchedule | ✅ migrate_ext_15 | ✅ Entity | ❌ 未使用（注释说"cron 表达式"但实际 tick 用 fixedDelay）| ❌ dead code |
| syncEnabled | ✅ migrate_ext_15 | ✅ Entity | ✅ tick:50 筛选 | ❌ I002 阻塞 |
| syncWindowMinutes | ✅ migrate_ext_15 | ✅ Entity | ✅ shouldRun:73 | ❌ I002 阻塞 |

## 5. ✅/⚠ 验证结论

| 维度 | 状态 |
|---|---|
| sync 配置 DDL 完整性 | ✅ 3/3 字段在 DDL |
| Entity 声明完整性 | ✅ 3/3 字段在 Entity |
| syncSchedule 代码使用 | ❌ dead code |
| syncEnabled 代码使用 | ✅ tick 筛选 |
| syncWindowMinutes 代码使用 | ✅ shouldRun 窗口判断 |
| 运行时可达性 | ❌ I002 阻塞 tick + syncNow |
| 配置生效性 | ❌ I002 阻塞 + syncSchedule dead code |

### **最终状态**：⚠ **PARTIAL-BLOCKER**

（DDL/Entity/部分代码使用完整 / I002 阻塞运行时 / syncSchedule dead code）

## 6. ⚠️ 同步配置与设计的差距

| 维度 | plan §0.4 描述 | 实测代码 |
|---|---|---|
| 自动排程 | `@Scheduled(fixedDelay=...)` → 每 5 分钟扫 | ✅ 一致 |
| cron 表达式 | "syncSchedule 字段" | ❌ **dead code**（无任何代码读取 cfg.getSyncSchedule()）|
| 窗口判断 | `lastIncrementalSyncAt + syncWindowMinutes ≤ now` | ✅ 代码层正确 |
| syncEnabled 启用 | "排程是否启用（status=ACTIVE ∧ syncEnabled=1）"| ✅ 一致 |

→ **plan 与代码的差距：syncSchedule 字段定义但未实际使用**（可能 Phase 1 设计意图未实现）

## 7. 5B 继承式策略影响

- F030 = PARTIAL-BLOCKER
- 不被继承
- 与 F005 / F008 / F010 同根因（I002）

## 8. 关联

- `audit/issues/issue-register.md` §十 I002
- `audit/issues/I002-crm-ddl-missing.md`（待建）
- `audit/runtime/plan.md` §一.2 F030 + §0.4 排程描述
- `audit/runtime/report-5A.md` §4 F008 + §6 F010（I002 阻塞）
