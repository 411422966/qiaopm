# F119 — MktConversion / 转化漏斗（Phase 5A 验证）

> **状态**：✅ VERIFIED  
> **验证时间**：2026-09-16 02:06 (UTC+8)  
> **验证方式**：API + DB  
> **Issue 关联**：无新 Issue

---

## 1. API 调用

```
GET http://localhost:8080/mkt/conversion/funnel
Authorization: Bearer <admin-token>
```

- **HTTP 状态**：200
- **traceId**：`f9871809a0fd407cb840682206d31222`
- **响应 data**：

```json
{
  "reach": 980,
  "sent": 980,
  "read": 491,
  "converted": 1,
  "sentRate": 100.0,
  "readRate": 50.1,
  "convertRate": 0.2
}
```

证据：`raw-funnel.txt`

---

## 2. DB 断言

| 表 | 行数 | 说明 |
|---|---|---|
| `marketing_touch_log` | 981 | 触达明细（含 deleted=1） |
| `marketing_touch_conversion` | 1 | 转化记录 |

证据：`db-counts.txt`

---

## 3. 静态分析（MarketingAutomationService.java:600-622）

```java
public Result<Map<String, Object>> conversionFunnel(...) {
    Long tenantId = TenantContextHolder.get().getTenantId();
    String sql = "SELECT COUNT(*) AS reach, " +
            "  SUM(CASE WHEN l.status >= 2 THEN 1 ELSE 0 END) AS sent, " +
            "  SUM(CASE WHEN l.status >= 4 THEN 1 ELSE 0 END) AS read_, " +
            "  SUM(CASE WHEN l.status = 5 THEN 1 ELSE 0 END) AS converted " +
            "FROM marketing_touch_log l WHERE l.deleted = 0 AND l.tenant_id = ? ...";
    Map<String, Object> row = jdbc.queryForMap(sql, args.toArray());
    ...
}
```

✅ 强制 tenantId 过滤 ✅ `l.deleted = 0` ✅ 4 段漏斗聚合 ✅ 比率计算（含 0 保护）

---

## 4. Final Result

| 维度 | 状态 | 说明 |
|---|---|---|
| HTTP 状态码 | ✅ | 200 |
| 4 段漏斗数据 | ✅ | reach/sent/read/converted 都有 |
| 比率计算 | ✅ | sentRate=100%, readRate=50.1%, convertRate=0.2% |
| tenantId 过滤 | ✅ | WHERE l.tenant_id=? |
| 数据一致性 | ✅ | DB 行数 980+ 对应 reach=980 |

**F119 — VERIFIED** ✅

---

证据清单：
- `raw-funnel.txt` — funnel 原始响应
- `db-counts.txt` — touch_log + conversion 行数
