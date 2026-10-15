# I072 — `MarketingAutomationService.conversionRoi` 空数据 / taskId 过滤时 500 数据库异常

> **发现时间**：2026-09-16 01:48 (UTC+8)
> **Phase**：5A — Runtime Verification
> **关联 Feature**：F120（MktConversion / ROI 统计）
> **严重度**：🟠 **HIGH**（ROI 端点 100% 500 — 阻塞看板 + 营销决策）
> **类别**：空数据兜底缺失 + SQL 参数异常
> **修复阶段**：**Post-Audit**（Phase 7）

---

## 1. 摘要

`MarketingAutomationService.conversionRoi()` 在 `marketing_touch_conversion` 表 0 行匹配（taskId 过滤）/ 空 amount 聚合时抛 500 数据库异常。可能是除 0 / 参数个数不匹配 / NULL 聚合未兜底。

| 维度 | 值 |
|---|---|
| **触发条件** | `GET /mkt/conversion/roi?taskId=...`（taskId 在 conversion 表 0 行）|
| **失败现象** | HTTP 200 / code 500 / message="数据库异常" |
| **根本原因** | `queryForMap` / `queryForObject` 在 0 行 + 参数化 SQL 异常 |
| **影响范围** | F120 ROI 看板 + 营销效果分析 + 转化成本计算 |
| **批次元数据** | Batch 1.5（Phase 5A 实证发现） |

---

## 2. 实证证据（Phase 5A）

### 2.1 运行时实证

```
GET http://localhost:8080/mkt/conversion/roi
→ HTTP 200 / code 500 / message: "数据库异常"
→ traceId 8fd3c2ec17ef49268467a063fff630ca
→ timestamp 2026-09-16 01:48:47
```

证据文件：`audit/evidence/F120-roi/raw-response.json`

### 2.2 DB 实证

```sql
mysql> SELECT COUNT(*) FROM marketing_touch_conversion;
1
-- 仅有 1 行（touch_log_id=2）— taskId 过滤时可能 0 行 → queryForMap 抛 EmptyResultDataAccessException

mysql> SELECT task_id, amount FROM marketing_touch_conversion;
-- taskId 多为 NULL，amount 有 NULL 值 → SUM/AVG 异常
```

### 2.3 代码位置（推测）

`backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java:conversionRoi()`：

```java
public Result<Map<String, Object>> conversionRoi(...) {
    // 可能用 jdbc.queryForMap 或 queryForObject，0 行时抛 EmptyResultDataAccessException
    Map<String, Object> agg = jdbc.queryForMap(
        "SELECT SUM(amount) AS revenue, COUNT(*) AS conversions FROM marketing_touch_conversion WHERE task_id = ?",
        taskId
    );
    // SUM(amount) 在 amount 全 NULL 时返回 NULL → 除法计算时可能 NPE / 类型转换异常
}
```

---

## 3. 同模式沿用

- **F125 journeyPage**：COUNT 查询复用 SELECT 的 args（pageSize + offset 多余参数）→ SQLException → 500（I073 同因）
- **I008 Fake Success**：dashboard catch 吞异常 → HTTP 200 但实际查询失败
- **建议统一**：所有 `queryForMap` / `queryForObject` 调用需加 `try/catch EmptyResultDataAccessException` 兜底为空 Map / 0

---

## 4. 影响 Feature

| Feature ID | Module / 功能点 | 当前 Result | 修复后 Result |
|---|---|---|---|
| **F120** | MktConversion / ROI 统计 | **UNVERIFIED**（500 — 跑不通）| VERIFIED（加 try/catch + 默认值兜底） |

---

## 5. 修复建议（Post-Audit）

```java
public Result<Map<String, Object>> conversionRoi(...) {
    try {
        Map<String, Object> agg = jdbc.queryForMap(
            "SELECT COALESCE(SUM(amount), 0) AS revenue, COUNT(*) AS conversions " +
            "FROM marketing_touch_conversion WHERE task_id = ?",
            taskId
        );
        // ... 后续 ROI 计算加 BigDecimal.ZERO 兜底
    } catch (EmptyResultDataAccessException e) {
        // 返回空 ROI
        return Result.success("操作成功", Map.of("revenue", 0, "cost", 0, "roi", 0));
    }
}
```

---

## 6. 关联文档

- `audit/evidence/F120-roi/raw-response.json` — 500 数据库异常
- `audit/runtime/report-5A-batch-1.5.md` §一 F120 + §三 I072 NEW
- `audit/runtime/plan-batch-1.5.md` §三.3.1 F120