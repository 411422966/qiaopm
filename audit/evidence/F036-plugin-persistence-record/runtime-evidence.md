# F036 — Plugin 持久化 recordState（DB + Runtime 实证）

> **验证时间**：2026-09-13 20:25 (UTC+8)
> **Phase**：5A — Runtime Verification (P0)
> **最终状态**：**PARTIAL-BLOCKER**（保持 + I017 实证强化）

---

## 一、I017 MISSING 实证（DB-only）

参见 F049 evidence §1.1：
- `SHOW TABLES LIKE 'plugin_runtime_state'` → **Empty set**
- `SHOW TABLES LIKE 'plugin%'` → 仅 `plugin_install / plugin_market / plugin_review`

**结论**：`plugin_runtime_state` 表不存在 → `recordState` 任何写入尝试必抛 `TableNotFoundException`。

---

## 二、Runtime 实证

### 2.1 Dashboard API 响应（persistedRecords 字段）

```
GET /plugin/runtime/dashboard
→ {
    "persistedRecords": 0,    ← 不管表是否存在都是 0
    "lastKnownFailed": 0
  }
```

### 2.2 Persisted API 响应

```
GET /plugin/runtime/persisted
→ { "code": 200, "data": [] }
```

### 2.3 I024 推断实证（catch 吞异常）

```
$ grep -i "plugin\|pf4j\|TableNotFoundException" backend/logs/meiyetec-error.log
（0 命中）
```

即使 `TableNotFoundException` 被吞，error.log 也不应有任何 plugin 异常记录（因 try/catch 整体包裹 + 仅 log.warn）。**但本次未实测触发 recordState → 无 TableNotFoundException 抛过**。

---

## 三、未验证的功能点

| 验证目标 | 状态 | 原因 |
|---|---|---|
| `recordState` 写表行为 | ❌ UNVERIFIED | pf4j 0 插件加载 → 无 recordState 调用 |
| `TableNotFoundException` 触发 | ❌ UNVERIFIED | 无 recordState 触发 |
| catch-log-swallow 行为 | ⚠️ 推断成立（与 F032 同模式）| 无 trigger 实证 |
| `@Transactional` 失效（catch 整体包住）| ⚠️ 静态已识别 | 无 trigger 实证 |
| I022 副作用（lastError 始终刷新）| ⚠️ 静态已识别 | 无 trigger 实证 |

---

## 四、Phase 5A 决策

| Feature | Phase 3 | Phase 5A | 变化 |
|---|---|---|---|
| F036 recordState 写入 | PARTIAL-BLOCKER | **PARTIAL-BLOCKER** | 保持 |

**理由**：
- ✅ Phase 3 静态判定稳健（I017 MISSING + @Transactional 失效 + Fake Success）
- ✅ Phase 5A DB-only 实证 I017（表确实缺失）
- ❌ Phase 5A Runtime 受限（无 recordState trigger）
- ⚠️ I022 / I008 / I024 静态已识别，运行时未实测

---

## 五、最终判定

### F036 = PARTIAL-BLOCKER（保持 + DB 实证强化）

| 维度 | 状态 |
|---|---|
| I017 表缺失（DB）| ✅ VERIFIED（DB DESCRIBE 实证）|
| recordState 写表行为 | ❌ UNVERIFIED（pf4j 0 插件）|
| @Transactional 失效 | ⚠️ 静态已识别（PluginPersistenceService.java:43 + catch:65-72）|
| I022 lastError 始终刷新 | ⚠️ 静态已识别 |
| I008 Fake Success（catch-ignore-empty）| ⚠️ 静态已识别 |

---

## 六、Evidence 文件

| 文件 | 路径 |
|---|---|
| PluginPersistenceService.java | `D:\QiaoPM\backend\src\main\java\com\meiyetec\plugin\PluginPersistenceService.java:42-77` |
| PluginRuntimeState Entity | `D:\QiaoPM\backend\src\main\java\com\meiyetec\entity\PluginRuntimeState.java` |
| DDL | `D:\QiaoPM\backend\src\main\resources\db\sql\migrate_ext_23_plugin_runtime_state.sql` |
| DB 实证 | F049 evidence §1.1 |
| Dashboard API 响应 | F032 evidence §1.2 |

---

**Phase 5A — F036 验证完成（PARTIAL-BLOCKER 保持 + I017 实证强化）。**
