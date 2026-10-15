# F122 — MktConversion 记录转化（业务回调）— Runtime Evidence（❌ BROKEN + I057 / N10 实证）

> Feature ID: F122
> Module: 营销转化 / track 业务回调
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:32 (UTC+8)
> SoT: `MktConversionController.java:51-54` + `MarketingAutomationService.trackConversion`
> 验证方式: API + DB

---

## 1. 端点

```
POST /mkt/conversion/track
body: {touchLogId, tenantId, conversionType, revenue}
```

## 2. ❌ 失败响应（HTTP 500 — DB Error）

```bash
$ admin POST /mkt/conversion/track
body: {"touchLogId":1,"tenantId":1,"conversionType":"BOOKING","revenue":99.99}
HTTP=500
{"code":500,"message":"数据库异常","data":null,"traceId":"ce3d7c0da62645c2af4965a343a03942"}
```

```bash
$ clerk01 POST /mkt/conversion/track (跨租户)
body: {"touchLogId":1,"tenantId":1,"conversionType":"BOOKING","revenue":199.99}
HTTP=500
{"code":500,"message":"数据库异常","data":null,"traceId":"14fbd402c8d24bb2ae00fbefb51c4307"}
```

## 3. 根因 — `MarketingTouchConversionMapper.insert` 缺 `related_id` 列

**Caused by**: `java.sql.SQLException: Field 'related_id' doesn't have a default value`

**SQL**（MyBatis-Plus 自动生成）:
```sql
INSERT INTO marketing_touch_conversion
  ( tenant_id, touch_log_id, task_id, conversion_type, amount, converted_at, create_time )
VALUES ( ?, ?, ?, ?, ?, ?, ? )
```

**`marketing_touch_conversion` 表实际结构**:
- **有 `related_id` 列（NOT NULL，无 DEFAULT）**
- INSERT 没设 related_id → MySQL 抛 SQLException

**新触达 Issue: I071 NEW（与 I057 模式复合）**

## 4. N10 / I057 实证 — 跨租户写 body 直接取 tenantId

虽然 INSERT 失败（500），但**代码层已确认** `trackConversion` 接收 body.tenantId **直接赋给 entity**（`MarketingAutomationService.java:731-760`）：
```java
MarketingTouchConversion c = new MarketingTouchConversion();
c.setTenantId(body.tenantId);  // ← 直接取 body，忽略当前用户 tenantId
```

- 当前行为：clerk01 POST body 含 tenantId=1 → 即使接口正常也会写入 tenant=1（**I057 实证**）
- 风险等级：PARTIAL-NON-BLOCKER（如果 INSERT 修好后会立刻变成跨租户污染）

## 5. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| HTTP 200 (有 relatedId) | ✅ | ❌ 当前无法构造（relatedId 未在 body 字段） |
| HTTP 200 (无 relatedId) | ⚠ 应缺字段报错 | ❌ 500 SQL 错 |
| 跨租户防护 | ❌ body 直接取 | **N10/I057 实证**（同 F117 模式）|

## 6. 结论

**Final Result**: **BROKEN + I057 实证** — 端点不可用；**新触达 I071 NEW**（related_id 字段缺失）+ I057 跨租户写风险。

Post-Audit 修复路径：
1. INSERT 需补 `related_id`（或加 DEFAULT `0` 或设 NULL）
2. 或实体 @TableField 标记 `relatedId = null` 并改表为可空
3. 跨租户防护：`c.setTenantId(currentTenantId)` 强制覆盖 body

证据落档：traceId ce3d7c0da62645c2af4965a343a03942 (admin) + 14fbd402c8d24bb2ae00fbefb51c4307 (clerk01)。