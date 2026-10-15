# F130 — MktEffect / 效果快照+生成快照（Phase 5A 验证）

> **状态**：⚠ PARTIAL-NON-BLOCKER — **I059 dead code 实证：snapshotSave 不写表**  
> **验证时间**：2026-09-16 02:18 (UTC+8)  
> **验证方式**：API + DB + 静态  
> **Issue 关联**：**I059（snapshotSave 仅 log.info）+ I070（不在本 Feature 范围）**

---

## 1. API 调用

### 1.1 GET /mkt/effect/snapshot

- **HTTP 状态**：200
- **响应 data**：聚合快照

```json
{
    "totalPromotions": 1,
    "journeyEnterCount": 3,
    "promotions": [
        {"promotionName":"audit-test-promotion-2-updated","promotionType":"FULL_REDUCE",
         "discountTotal":40.00,"orderCount":2,"id":2}
    ],
    "touchConverted": 2,
    "queryDate": "2026-09-16",
    "pointsGranted": 29500,
    "couponUsageRate": 0,
    "totalDiscount": 299.49,
    "totalReach": 1469,
    "totalOrders": 2,
    "touchSuccess": 487,
    "totalActivities": 0,
    "usedCoupon": 0
}
```

证据：`raw-snapshot.txt`

---

## 2. DB 断言 — ⚠ I059 dead code 实证

### 2.1 mkt_effect_snapshot 表是否存在

```
cmd /c 'set MYSQL_PWD=root123456& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" ...
SHOW TABLES LIKE 'mkt_effect%';
```

输出：**空集**（无 mkt_effect_snapshot 表）

证据：`db-table-check.txt`

### 2.2 POST /mkt/effect/snapshot/save 行为分析

```java
@PostMapping("/snapshot/save")
@Transactional(rollbackFor = Exception.class)
public Result<Map<String, Object>> saveSnapshot() {
    Map<String, Object> snap = snapshot(null, null).getData();
    return Result.success("快照已记录", snap);
}
```

- 调用 `snapshot(null, null).getData()` → 返回聚合数据
- **未 INSERT / 未写 mkt_effect_snapshot 表**
- 注释明确：`// 真实业务可写 mkt_effect_snapshot 表`（即当前实现是占位）

> **I059 dead code 实证**：调用 `saveSnapshot` 返回 200，但 DB 中**无任何持久化记录** — 与 plan §三 N8 预期负向证据完全一致

---

## 3. 静态分析（MktEffectController.java）

| 行 | 内容 |
|---|---|
| 56-141 | `snapshot()` 聚合：totalActivities / totalPromotions / totalReach / touchSuccess / touchConverted / usedCoupon / couponUsageRate / pointsGranted / totalDiscount / promotions 列表 |
| 145-152 | `saveSnapshot()`：仅 `snapshot(null, null).getData()` 返回，**无 INSERT** |
| import | 注入了 8 个 Mapper，但 saveSnapshot 未使用任何一个 Mapper 写库 |

---

## 4. Final Result

| 维度 | 状态 | 说明 |
|---|---|---|
| GET snapshot | ✅ | 200 + 聚合 12 字段 |
| POST save | ✅（HTTP）但 ❌（写库）| 返回 200，但**无 mkt_effect_snapshot 表写入** |
| I059 dead code | ⚠ PARTIAL-NON-BLOCKER | 实证 `saveSnapshot()` 是占位实现 |
| tenantId 强制 | ✅ | tid() 调 TenantContextHolder |

**F130 — PARTIAL-NON-BLOCKER**（功能正常但 snapshotSave 是 dead code，与 I059 一致）

---

证据清单：
- `raw-snapshot.txt` — GET /snapshot 响应
- `db-table-check.txt` — mkt_effect_snapshot 表 0 匹配
- `raw-snapshot-save.txt` — POST /snapshot/save 响应（仅返回聚合，未写表）