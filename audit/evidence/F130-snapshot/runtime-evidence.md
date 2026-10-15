# F130 — MktEffect 效果快照 + 生成快照 — Runtime Evidence（含 N8 / I059 实证）

> Feature ID: F130
> Module: 营销效果 / snapshot + save
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:34 (UTC+8)
> SoT: `MktEffectController.java:54-179`
> 验证方式: API + DB

---

## 1. 端点

```
GET  /mkt/effect/snapshot       # 聚合指标
POST /mkt/effect/snapshot/save  # 生成当日快照
```

## 2. snapshot ✅ 真实聚合

```bash
GET /mkt/effect/snapshot
HTTP=200
traceId: 77c4d0a60a664210b02fe908d5b5b974
```

```json
{
  "totalReach": 1472,
  "touchSuccess": 490,
  "touchConverted": 2,
  "usedCoupon": 0,
  "couponUsageRate": 0,
  "pointsGranted": 29500,
  "journeyEnterCount": 5,
  "totalOrders": 3,
  "totalDiscount": 399.48,
  "totalActivities": 0,
  "totalPromotions": 2,
  "promotions": [
    {"id":2,"promotionName":"audit-test-promotion-2-updated","promotionType":"FULL_REDUCE","discountTotal":60.00,"orderCount":3},
    {"id":4,"promotionName":"5A-F133 ????V2","promotionType":"FULL_REDUCE","discountTotal":150.00,"orderCount":3}
  ],
  "queryDate": "2026-09-16"
}
```

聚合来源：8 张表（market_activity / mkt_promotion / marketing_touch_log / marketing_touch_conversion / mkt_coupon / point_record / mkt_journey_instance / mkt_channel_template）。

## 3. N8 / I059 实证 — `saveSnapshot` dead code

```bash
POST /mkt/effect/snapshot/save
HTTP=200
traceId: 7f819dc0dbc54491858630e38c225cc4
```

返回完全相同的 snapshot 数据 — 但**实际**：

**`MktEffectController.java:175-179` 实现**:
```java
@PostMapping("/snapshot/save")
@Transactional(rollbackFor = Exception.class)
public Result<Map<String, Object>> saveSnapshot() {
    Map<String, Object> snap = snapshot(null, null).getData();
    return Result.success("快照已记录", snap);   // ← 注释：「真实业务可写 mkt_effect_snapshot 表」
}
```

- 仅 `snapshot()` 内部聚合 + 返回，**未写任何表**
- `mkt_effect_snapshot` 表本身在 db-snapshot.sql **0 匹配**（已确认 MISSING — I059 沿用）
- `@Transactional` 包裹无实际意义（无 INSERT/UPDATE 触发）

**这是 dead code**：端点存在但不产生副作用。前端调此端点期望落库 → **实际零落库**。

## 4. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| snapshot 200 | ✅ | ✅ 8 表聚合 |
| save 200 | ✅ | ✅ 200（但 dead） |
| save 写 mkt_effect_snapshot | ✅ | ❌ **N8/I059 实证**（无写表）|

## 5. 结论

**Final Result**: **PARTIAL-NON-BLOCKER + I059 dead code 实证** — snapshot 工作正常；save dead。

Post-Audit 修复模板：
```java
@PostMapping("/snapshot/save")
@Transactional(rollbackFor = Exception.class)
public Result<Map<String, Object>> saveSnapshot() {
    Map<String, Object> snap = snapshot(null, null).getData();
    // 1. 加 mkt_effect_snapshot 表（schema migration）
    // 2. INSERT INTO mkt_effect_snapshot (...) VALUES (...)
    jdbc.update("INSERT INTO mkt_effect_snapshot (...) VALUES (...)", ...);
    return Result.success("快照已记录", snap);
}
```

证据落档：traceId 77c4d0a60a664210b02fe908d5b5b974 (snapshot) + 7f819dc0dbc54491858630e38c225cc4 (save)。