# F120 — MktConversion / ROI 统计（Phase 5A 验证）

> **状态**：❌ BROKEN — **HTTP 500 数据库异常**  
> **验证时间**：2026-09-16 02:06 (UTC+8)  
> **验证方式**：API + DB + 后端 log + 静态  
> **Issue 关联**：**🆕 I070 NEW — marketing_touch_conversion 表 schema 缺 `deleted` 列，但代码 SQL 引用 `c.deleted`**

---

## 1. API 调用（两次均 500）

### 1.1 无 taskId

```
GET http://localhost:8080/mkt/conversion/roi
Authorization: Bearer <admin-token>
```

- **HTTP 状态**：**500**
- **traceId**：`1dfddd6e8c234c18b26f67b224fb437f`
- **响应 message**：`数据库异常`

证据：`raw-roi-no-taskId.txt`

### 1.2 带 taskId=1

```
GET http://localhost:8080/mkt/conversion/roi?taskId=1
```

- **HTTP 状态**：**500**
- **traceId**：`432bb4336ed744bbbe1be07258358395`
- **响应 message**：`数据库异常`

证据：`raw-roi-with-taskId.txt`

---

## 2. DB 断言

### 2.1 marketing_touch_conversion schema 实测

```
COLUMN_NAME       | COLUMN_TYPE   | COLUMN_DEFAULT | IS_NULLABLE
id                | bigint        | NULL           | NO
tenant_id         | bigint        | 1              | NO
touch_log_id      | bigint        | NULL           | NO
task_id           | bigint        | NULL           | YES
conversion_type   | varchar(32)   | NULL           | NO
related_id        | bigint        | NULL           | NO
related_no        | varchar(64)   | NULL           | YES
amount            | decimal(12,2) | 0.00           | YES
converted_at      | datetime      | NULL           | YES
create_time       | datetime      | CURRENT_TIMESTAMP | YES
```

**关键发现**：**无 `deleted` 列**

证据：information_schema.COLUMNS 查询（2026-09-16 02:07）

### 2.2 DB 行数

| 表 | 行数 |
|---|---|
| marketing_touch_conversion | 1 |

证据：`db-counts.txt`

---

## 3. 后端 log 实证（meiyetec-error.log）

```
2026-09-16 02:06:53 ERROR ... [traceId=432bb4336ed744bbbe1be07258358395]
org.springframework.jdbc.BadSqlGrammarException: PreparedStatementCallback; 
  bad SQL grammar [SELECT COALESCE(SUM(amount), 0) AS total_revenue, COUNT(*) AS convert_count 
                   FROM marketing_touch_conversion c  WHERE c.deleted = 0 AND c.tenant_id = ?  AND c.task_id = ? ]
Caused by: java.sql.SQLSyntaxErrorException: Unknown column 'c.deleted' in 'where clause'
```

证据：`backend-error-grep.txt`

---

## 4. 静态分析（MarketingAutomationService.java:622-636）

```java
public Result<Map<String, Object>> conversionRoi(@RequestParam(required = false) Long taskId) {
    Long tenantId = TenantContextHolder.get().getTenantId();
    StringBuilder where = new StringBuilder(" WHERE c.deleted = 0 AND c.tenant_id = ? ");  // ← c.deleted 不存在
    ...
    String sql = "SELECT COALESCE(SUM(amount), 0) AS total_revenue, COUNT(*) AS convert_count " +
            "FROM marketing_touch_conversion c " + where;
    Map<String, Object> row = jdbc.queryForMap(sql, args.toArray());
    ...
}
```

### 4.1 缺陷对比

| 维度 | 代码期望 | DB 实际 |
|---|---|---|
| `marketing_touch_conversion.deleted` | 存在（用于软删过滤） | **不存在** |
| `marketing_touch_log.deleted` | 存在 | 存在（DEFAULT 0） |

### 4.2 根因

- 表 `marketing_touch_conversion` schema 缺 `deleted` 列
- 但代码多处用 `WHERE c.deleted = 0`（conversionRoi 第 624 行 + trackConversion 内部也可能用）
- **MySQL 不识别 `c.deleted` 列** → `BadSqlGrammarException` → Spring 500

---

## 5. 🆕 I070 NEW — 登记

| 字段 | 值 |
|---|---|
| **编号** | **I070 NEW** |
| **标题** | `marketing_touch_conversion` 表 schema 缺 `deleted` 列，导致 `conversionRoi` 500 |
| **严重度** | **CRITICAL-P1**（ROI 统计全量 500，影响营销决策数据） |
| **影响 Feature** | **F120**（F122 trackConversion 是否也有类似风险需进一步验证） |
| **Fix Phase** | **Post-Audit**（沿用 Q3 第六次统一口径） |
| **修复方向** | `ALTER TABLE marketing_touch_conversion ADD COLUMN deleted TINYINT DEFAULT 0` + 或代码去掉 `c.deleted = 0` 条件 |

---

## 6. Final Result

| 维度 | 状态 | 说明 |
|---|---|---|
| HTTP 状态码 | ❌ | **500 数据库异常** |
| 后端 log Caused by | ✅ | `Unknown column 'c.deleted' in 'where clause'` |
| 表 schema 完整性 | ❌ | `marketing_touch_conversion` 缺 deleted 列 |
| I070 登记 | ✅ | CRITICAL-P1 |

**F120 — BROKEN**（I070 NEW — 表 schema 与代码 SQL 不一致）

---

证据清单：
- `raw-roi-no-taskId.txt` — 无 taskId 调用原始响应（HTTP 500）
- `raw-roi-with-taskId.txt` — 带 taskId 调用原始响应（HTTP 500）
- `db-counts.txt` — DB 行数
- `backend-error-grep.txt` — 后端 meiyetec-error.log 中 conversionRoi 相关 BadSqlGrammarException
