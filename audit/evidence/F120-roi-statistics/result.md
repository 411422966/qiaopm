# F120 — MktConversion / ROI 统计 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:44 (UTC+8)
> **方式**：API-only + 源码分析

## 1. 接口实证

```
GET /mkt/conversion/roi?taskId=1
→ HTTP 200 / code 500 / message: "数据库异常"
→ traceId 51635de0fda542c0a378f65c769d5314
```

## 2. 失败根因（源码层）

`MarketingAutomationService.java:684`

```java
Map<String, Object> row = jdbc.queryForMap(sql, args.toArray());
```

- `jdbc.queryForMap` 当 SQL 返回 0 行时抛 `EmptyResultDataAccessException`
- 当前 marketing_touch_conversion 0 行匹配 taskId=1 → 异常 → Spring 500
- 应改用 `jdbc.queryForList` + `Optional.ofNullable(rows.get(0))`

## 3. Final Result

**PARTIAL-NON-BLOCKER**（空数据时崩；功能完整但缺少空集容错）
