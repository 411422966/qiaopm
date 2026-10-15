# F122 — MktConversion / 记录转化 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:45 (UTC+8)
> **方式**：API + DB + 静态（I057 沿用）

## 1. 接口实证（修正参数后）

```
POST /mkt/conversion/track
Body: {"touchLogId":2,"conversionType":"PURCHASE","amount":99.9,"relatedNo":"audit-5A-F122"}
→ HTTP 200 / code 500 / message: "数据库异常"
→ traceId 7230c5a7d8d740f7981d6849df796329
```

## 2. DB 实证

```
mysql> SELECT * FROM marketing_touch_conversion WHERE related_no='audit-5A-F122';
0 行 — INSERT 失败
```

## 3. 失败根因（源码层）

`MarketingAutomationService.java:731-760`

- L739：`conv.setTenantId(lg.getTenantId())` — 从 touchLogId 反查触达记录，再用其 tenantId
- 这种"间接 tenantId 取值"**当前安全**（无法 body 越权写入）
- 但 INSERT 500 仍未排查根因（列约束 / 数据类型？）

## 4. I057 实证（与 F123 形成对照）

- **F122 trackConversion**：tenantId 取自触达记录（**安全**）
- **F123 markRead**：`UPDATE marketing_touch_log ... WHERE id = ?` 无 tenantId 过滤（**N10 实证**）

## 5. Final Result

**PARTIAL-NON-BLOCKER**（INSERT 500 失败 — DB 写入未生效；I057 安全）
