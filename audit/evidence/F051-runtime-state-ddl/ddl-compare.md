# F051 — Plugin DB `migrate_ext_23` DDL 字段差 review

> **验证时间**：2026-09-13 20:48 (UTC+8)
> **Phase**：5B — Static-Only + 字段差复核
> **验证方式**：逐字段对比 DDL vs Entity 字段类型/长度/默认值
> **最终状态**：**PARTIAL-BLOCKER → 升级为 PARTIAL-NON-BLOCKER**（DDL 与 Entity 完全对齐，Phase 3 的"9 vs 8"为计数错误）

---

## 一、字段对比

### 1.1 DDL（10 字段）

`backend/src/main/resources/db/sql/migrate_ext_23_plugin_runtime_state.sql:9-22`

| # | 字段 | 类型 | 默认 | 约束 |
|---|---|---|---|---|
| 1 | id | BIGINT | AUTO_INCREMENT | PRIMARY KEY |
| 2 | tenant_id | BIGINT | NOT NULL DEFAULT 0 | |
| 3 | plugin_code | VARCHAR(64) | NOT NULL | |
| 4 | version | VARCHAR(32) | DEFAULT NULL | |
| 5 | plugin_id | VARCHAR(96) | DEFAULT NULL | |
| 6 | state | VARCHAR(16) | DEFAULT NULL | COMMENT 'CREATED/STARTED/STOPP/FAILED/DISABLED/UNLOADED' |
| 7 | last_error | VARCHAR(500) | DEFAULT NULL | |
| 8 | loaded_at | DATETIME | DEFAULT NULL | |
| 9 | created_at | DATETIME | CURRENT_TIMESTAMP | |
| 10 | updated_at | DATETIME | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | |
| - | UNIQUE KEY | uk_tenant_plugin | (tenant_id, plugin_code) | |
| - | INDEX | idx_state | (state) | |
| - | INDEX | idx_updated | (updated_at) | |

### 1.2 Entity（10 字段）

`backend/src/main/java/com/meiyetec/entity/PluginRuntimeState.java:27-54`

| # | 字段 | 类型 | 自动填充 |
|---|---|---|---|
| 1 | id | Long | @TableId(AUTO) |
| 2 | tenantId | Long | |
| 3 | pluginCode | String | |
| 4 | version | String | |
| 5 | pluginId | String | |
| 6 | state | String | |
| 7 | lastError | String | |
| 8 | loadedAt | LocalDateTime | |
| 9 | createdAt | LocalDateTime | @TableField(INSERT) |
| 10 | updatedAt | LocalDateTime | @TableField(INSERT_UPDATE) |

### 1.3 字段对应表（10 vs 10）

| DDL 字段 | Entity 字段 | 类型匹配 | 默认值匹配 | 备注 |
|---|---|---|---|---|
| id | id | ✅ BIGINT ↔ Long | ✅ | PK |
| tenant_id | tenantId | ✅ BIGINT ↔ Long | ✅ default 0 | |
| plugin_code | pluginCode | ✅ VARCHAR(64) ↔ String | ✅ NOT NULL | |
| version | version | ✅ VARCHAR(32) ↔ String | ✅ | |
| plugin_id | pluginId | ✅ VARCHAR(96) ↔ String | ✅ | |
| state | state | ✅ VARCHAR(16) ↔ String | ✅ | UNLOADED 容纳（8 char ≤ 16）|
| last_error | lastError | ✅ VARCHAR(500) ↔ String | ✅ | |
| loaded_at | loadedAt | ✅ DATETIME ↔ LocalDateTime | ✅ | |
| created_at | createdAt | ✅ DATETIME ↔ LocalDateTime | ✅ CURRENT_TIMESTAMP | INSERT 自动填充 |
| updated_at | updatedAt | ✅ DATETIME ↔ LocalDateTime | ✅ ON UPDATE CURRENT_TIMESTAMP | INSERT_UPDATE 自动填充 |

### 1.4 索引约束对比

| DDL 索引 | Entity 注解 | 一致 |
|---|---|---|
| PRIMARY KEY (id) | @TableId(type = IdType.AUTO) | ✅ |
| UNIQUE KEY uk_tenant_plugin (tenant_id, plugin_code) | **无 @TableLogic / @UniqueKey** 注解 | ⚠️ MyBatis-Plus 不强制，但 DDL 约束会生效 |
| INDEX idx_state (state) | 无 | ⚠️ 仅 DDL 维护 |
| INDEX idx_updated (updated_at) | 无 | ⚠️ 仅 DDL 维护 |

**结论**：DDL 包含 1 个 UNIQUE KEY + 2 个辅助 INDEX，Entity 无对应注解（MyBatis-Plus 不感知，但 DDL 约束由 DB 层强制）。

## 二、Phase 3 计数错误修正

### 2.1 Phase 3 原判定

> Phase 4 计划：`DDL 9 字段 vs entity 8 字段（差 1 字段需 review）`

### 2.2 修正后判定

**实际**：DDL **10** 字段，Entity **10** 字段，**完全对齐**。

Phase 3 漏算了 `updated_at`（auto-fill 字段）— Phase 5B 复核发现对齐无误。

### 2.3 状态升级

| 阶段 | 状态 | 理由 |
|---|---|---|
| Phase 3 | PARTIAL-BLOCKER | 误判"9 vs 8" 字段差 |
| **Phase 5B** | **PARTIAL-NON-BLOCKER** | **DDL 10 vs Entity 10 完全对齐，无差** |

## 三、状态机支持（UNLOADED）

### 3.1 DDL state VARCHAR(16) 容纳值

| 状态值 | 字符数 | ≤ 16? |
|---|---|---|
| CREATED | 7 | ✅ |
| STARTED | 7 | ✅ |
| STOPPED | 7 | ✅ |
| FAILED | 6 | ✅ |
| DISABLED | 8 | ✅ |
| UNLOADED | 8 | ✅ |

**结论**：所有状态值都 ≤ 8 字符，远小于 VARCHAR(16)，无截断风险。

### 3.2 代码实际写 UNLOADED

`PluginRuntimeService.unloadPlugin()`（行 222）：
```java
pm.unloadPlugin(pluginId);
persistenceService.recordState(0L, pluginCode, null, pluginId, "UNLOADED", null);
```

**确认**：unloadPlugin 成功路径会写 `state=UNLOADED`，DDL 已容纳。

## 四、最终判定

| 维度 | 状态 | 备注 |
|---|---|---|
| DDL 字段对齐 | ✅ VERIFIED | 10 vs 10 完全匹配 |
| 类型匹配 | ✅ VERIFIED | VARCHAR/BIGINT/DATETIME ↔ Java 类型对应 |
| 默认值匹配 | ✅ VERIFIED | NOT NULL / DEFAULT NULL / CURRENT_TIMESTAMP 一致 |
| 索引约束 | ✅ VERIFIED | UNIQUE KEY + 2 INDEX 仅 DDL 维护（MyBatis-Plus 不感知）|
| UNLOADED 状态支持 | ✅ VERIFIED | DDL state VARCHAR(16) 容纳 + 代码实际写 UNLOADED |
| 字段差修正 | ✅ 已修正 | Phase 3 "9 vs 8" 为计数错误 |

### 最终状态

**F051 = PARTIAL-NON-BLOCKER**（DDL 与 Entity 完全对齐）

---

## 五、Evidence 文件

| 文件 | 路径 |
|---|---|
| DDL | `backend/src/main/resources/db/sql/migrate_ext_23_plugin_runtime_state.sql` |
| Entity | `backend/src/main/java/com/meiyetec/entity/PluginRuntimeState.java` |
| recordState 调用方 | grep "persistenceService.recordState" 全工程 |

---

**F051 已升级为 PARTIAL-NON-BLOCKER。Phase 3 字段差误判已修正。**
