# F049 — PluginRuntimeState Entity + DB Table（DB-only 验证）

> **验证时间**：2026-09-13 20:25 (UTC+8)
> **Phase**：5A — Runtime Verification (P2)
> **最终状态**：**PARTIAL-BLOCKER**（I017 表缺失已 DB 实证）

---

## 一、DB 实证（I017 MISSING 确认）

### 1.1 SHOW TABLES LIKE 'plugin_runtime_state'

```bash
$ mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 meiyetec \
    -e "SHOW TABLES LIKE 'plugin_runtime_state';"

Empty set (0.00 sec)
```

**结论**：`plugin_runtime_state` 表**不存在**。

### 1.2 SHOW TABLES LIKE 'plugin%'（同前缀表清单）

```bash
$ mysql ... -e "SHOW TABLES LIKE 'plugin%';"

+-----------------------+
| Tables_in_meiyetec    |
+-----------------------+
| plugin_install        |
| plugin_market         |
| plugin_review         |
+-----------------------+
```

**结论**：3 张 plugin_* 表已存在（marketplace 三件套），**plugin_runtime_state 缺失**。

---

## 二、Entity 与 DDL 一致性

### 2.1 Entity（`entity/PluginRuntimeState.java`）

```java
@TableName("plugin_runtime_state")
public class PluginRuntimeState {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;        // 0=平台级
    private String pluginCode;
    private String version;
    private String pluginId;
    private String state;         // CREATED/STARTED/STOPPED/FAILED/DISABLED/UNLOADED
    private String lastError;
    private LocalDateTime loadedAt;
    private LocalDateTime createdAt;  // @TableField(fill=INSERT)
    private LocalDateTime updatedAt;  // @TableField(fill=INSERT_UPDATE)
}
```

**字段数**：10（id + 9 业务字段）

### 2.2 DDL（`db/sql/migrate_ext_23_plugin_runtime_state.sql:9-29`）

```sql
CREATE TABLE IF NOT EXISTS `plugin_runtime_state` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID，0=平台级',
  `plugin_code` VARCHAR(64) NOT NULL COMMENT '插件编码',
  `plugin_id` VARCHAR(128) DEFAULT NULL COMMENT 'pf4j 插件ID（含version）',
  `version` VARCHAR(32) DEFAULT NULL COMMENT '插件版本',
  `state` VARCHAR(32) DEFAULT NULL COMMENT '状态',
  `last_error` VARCHAR(1000) DEFAULT NULL COMMENT '最后错误',
  `loaded_at` DATETIME DEFAULT NULL COMMENT '加载时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_plugin` (`tenant_id`, `plugin_code`),
  KEY `idx_state` (`state`),
  KEY `idx_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='EXT-23 插件运行时状态';
```

**字段数**：10（id + 9 业务字段）

### 2.3 一致性对比

| 字段 | Entity | DDL | 一致 |
|---|---|---|---|
| `id` | BIGINT AUTO | BIGINT NOT NULL AUTO_INCREMENT | ✓ |
| `tenant_id` | Long | BIGINT NOT NULL DEFAULT 0 | ✓ |
| `plugin_code` | String | VARCHAR(64) NOT NULL | ✓ |
| `version` | String | VARCHAR(32) | ✓ |
| `pluginId` | String | VARCHAR(128) | ✓ |
| `state` | String | VARCHAR(32) | ✓ |
| `lastError` | String | VARCHAR(1000) | ✓ |
| `loadedAt` | LocalDateTime | DATETIME | ✓ |
| `createdAt` | LocalDateTime (fill=INSERT) | DATETIME DEFAULT CURRENT_TIMESTAMP | ✓ |
| `updatedAt` | LocalDateTime (fill=INSERT_UPDATE) | DATETIME ON UPDATE CURRENT_TIMESTAMP | ✓ |

**结论**：Entity 10 字段 vs DDL 10 字段**完全一致**（无 F051 担心的 9 vs 8 差异）。

---

## 三、DDL 与 db-snapshot.sql 一致性（Phase 3 I017 复核）

### 3.1 grep db-snapshot.sql

```bash
$ grep -c "plugin_runtime_state" audit/baseline/db-snapshot.sql
0
```

**结论**：db-snapshot.sql 全文 0 命中 plugin_runtime_state → DDL **未应用** → I017 实证。

### 3.2 三表已应用对比

| 表 | db-snapshot.sql 命中 | 状态 |
|---|---|---|
| plugin_market | ✅ 有 | 已存在（21 列）|
| plugin_install | ✅ 有 | 已存在（14 列）|
| plugin_review | ✅ 有 | 已存在（8 列）|
| **plugin_runtime_state** | ❌ 0 命中 | **MISSING**（I017）|

---

## 四、I017 影响链

### 4.1 recordState 触发链路（PluginPersistenceService.recordState）

```
任何 F033 load/enable/disable/unload 触发 → recordState(...)
  → INSERT INTO plugin_runtime_state ...
  → TableNotFoundException（表不存在）
  → try/catch 包裹整体 → 仅 warn，不抛
  → dashboard.persistedRecords 永远 = 0
```

### 4.2 Phase 5A 实证

| 验证点 | 结果 | 证据 |
|---|---|---|
| `recordState` 触发？ | ❌ 否 | 无插件加载 → 无 recordState 调用 |
| `TableNotFoundException` 是否出现？ | ❌ 否 | 因 recordState 未被触发（pf4j 0 插件）|
| dashboard.persistedRecords | 0 | API 返回 `persistedRecords: 0` |
| dashboard.lastKnownFailed | 0 | API 返回 `lastKnownFailed: 0` |

**观察**：I008 Fake Success 链路未触发（dashboard 是空数据**不是因为**表缺失，而是因为**根本没有任何插件加载**）。

---

## 五、最终判定

### F049 = PARTIAL-BLOCKER（保持）

| 维度 | 状态 | 备注 |
|---|---|---|
| Entity 字段完整性 | ✅ VERIFIED | 10 字段（含 @TableField 自动填充）|
| DDL 字段完整性 | ✅ VERIFIED | 10 字段，含 3 索引（uk + 2 普通）|
| Entity ↔ DDL 一致性 | ✅ VERIFIED | 10/10 字段匹配 |
| **DB 表存在** | ❌ **MISSING** | `SHOW TABLES LIKE 'plugin_runtime_state'` → Empty |
| **DB 写入行为** | ❌ UNVERIFIED | 表缺失，recordState 必抛 TableNotFoundException |
| **F036/F037 recordState 调用** | ⚠️ 静默 | 因 pf4j 0 插件，未触发 recordState |

### F049 vs F051 状态汇总

| Feature | 状态 | 备注 |
|---|---|---|
| **F049** | PARTIAL-BLOCKER | I017 MISSING 已 DB 实证 |
| **F051** | PARTIAL-BLOCKER | DDL 已写但未应用 + 字段一致性 10/10 |

---

## 六、Evidence 文件

| 文件 | 路径 |
|---|---|
| Entity | `D:\QiaoPM\backend\src\main\java\com\meiyetec\entity\PluginRuntimeState.java` |
| DDL | `D:\QiaoPM\backend\src\main\resources\db\sql\migrate_ext_23_plugin_runtime_state.sql` |
| db-snapshot.sql grep | `audit/baseline/db-snapshot.sql` 0 命中 |
| SHOW TABLES 输出 | 本文件 §1.1 / §1.2 |
| Dashboard API 响应 | F032 evidence §1.2 |

---

**Phase 5A — F049 验证完成（PARTIAL-BLOCKER 保持 + DB MISSING 实证）。**
