# F121 — MktConversion / 按渠道统计（Phase 5A 验证）

> **状态**：✅ VERIFIED  
> **验证时间**：2026-09-16 02:06 (UTC+8)  
> **验证方式**：API + DB  
> **Issue 关联**：无新 Issue

---

## 1. API 调用

```
GET http://localhost:8080/mkt/conversion/by-channel
Authorization: Bearer <admin-token>
```

- **HTTP 状态**：200
- **traceId**：`498307a931da4031a2edbe33b53addca`
- **响应 data**：

```json
[
  {"channelType":"SMS","sent":489,"converted":1,"reach":980}
]
```

证据：`raw-by-channel.txt`

---

## 2. 静态分析（MarketingAutomationService.java:683-693）

```java
public Result<List<Map<String, Object>>> byChannel() {
    Long tenantId = TenantContextHolder.get().getTenantId();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT l.channel_type, COUNT(*) AS reach, " +
        "       SUM(CASE WHEN l.status = 2 THEN 1 ELSE 0 END) AS sent, " +
        "       SUM(CASE WHEN l.status = 5 THEN 1 ELSE 0 END) AS converted " +
        "FROM marketing_touch_log l WHERE l.deleted = 0 AND l.tenant_id = ? " +
        "GROUP BY l.channel_type", tenantId);
    return Result.success(Keys.camel(rows));
}
```

✅ tenantId 强制过滤 ✅ l.deleted 0 ✅ GROUP BY 渠道 ✅ 驼峰转换

---

## 3. Final Result

| 维度 | 状态 | 说明 |
|---|---|---|
| HTTP 状态码 | ✅ | 200 |
| 按渠道分组聚合 | ✅ | 1 条 SMS 数据 |
| tenantId 过滤 | ✅ | WHERE l.tenant_id=? |
| 驼峰字段 | ✅ | channelType/sent/converted/reach |

**F121 — VERIFIED** ✅

---

证据清单：
- `raw-by-channel.txt` — by-channel 原始响应
