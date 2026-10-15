# F126 — MktJourney 详情/更新/发布/暂停 — Runtime Evidence

> Feature ID: F126
> Module: 营销旅程 / 4 端点聚合
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:33 (UTC+8)
> SoT: `MktJourneyController.java:63-83`
> 验证方式: API + DB

---

## 1. 端点

```
GET  /mkt/journey/{id}            # 详情
PUT  /mkt/journey/{id}            # 更新
POST /mkt/journey/{id}/publish    # 发布
POST /mkt/journey/{id}/pause      # 暂停
```

测试目标: id=5（5A 创建的 journey）。

## 2. 详情 ✅

```bash
GET /mkt/journey/5
HTTP=200
traceId: c04295b33cc64d7cab7f4e61ec444ae8
```

返回 `journey` (完整字段) + `nodes` (解析为 JSON) + `edges` (解析为 JSON)。

## 3. 更新 ⚠ PARTIAL-NON-BLOCKER

```bash
PUT /mkt/journey/5
body: {"journeyName":"5A-F124-journey-updated","description":"updated desc"}
HTTP=200
traceId: 4827583450b441cbaa36a26c506d0d64
```

```json
{
  "id": 5,
  "journeyName": "5A-F124-journey-updated",
  "description": "updated desc",
  "nodesJson": null,         ← **隐患**
  "edgesJson": null,         ← **隐患**
  "status": 1
}
```

**关键发现**：
- `updateJourney()` (`MarketingAutomationService.java:445-456`) 无条件调 `old.setNodesJson(toJson(body.get("nodes")))`
- `toJson(null)` 返回 null → `nodesJson` / `edgesJson` 列被**清空**
- ⚠ 若 body 不传 nodes → nodesJson 被覆盖为 null → **前端再获取时 nodes 数组为空**

## 4. 发布 ✅

```bash
POST /mkt/journey/5/publish
HTTP=200
{"message":"已发布"}
traceId: 466877acc3974f29ac9a0bda1e73d8ae
```

DB: `status=2, published_at='2026-09-16 02:33:24'`（`UPDATE mkt_journey SET status=2, published_at=NOW() WHERE id=?`）。

## 5. 暂停 ✅

```bash
POST /mkt/journey/5/pause
HTTP=200
{"message":"已暂停"}
traceId: 382102937a414f2bafd0c917851ac142
```

DB: `status=3`（`UPDATE mkt_journey SET status=3 WHERE id=?`）。

## 6. 结论

| 维度 | 结果 |
|---|---|
| 详情 | ✅ 200 |
| 更新 | ⚠ PARTIAL-NON-BLOCKER（nodesJson/edgesJson 被吞）|
| 发布 | ✅ 200 |
| 暂停 | ✅ 200 |

**Final Result**: **PARTIAL-NON-BLOCKER** — 4 端点 200 但 update 有 nodes/edges 覆盖隐患。

证据落档：traceId c04295b33cc64d7cab7f4e61ec444ae8 (detail) + 4827583450b441cbaa36a26c506d0d64 (update) + 466877acc3974f29ac9a0bda1e73d8ae (publish) + 382102937a414f2bafd0c917851ac142 (pause)。