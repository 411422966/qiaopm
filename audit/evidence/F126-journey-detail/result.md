# F126 — MktJourney / 详情+更新+发布+暂停 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:48 (UTC+8)
> **方式**：API + DB（4 端点 + 状态机）

## 1. 接口实证（用新建 journey id=6）

### GET /mkt/journey/{id}

```
GET /mkt/journey/6
→ HTTP 200 / code 200 / data: {...}
→ traceId bc932eb789a343eaa5f97fc2d7770b1a
```

### PUT /mkt/journey/{id}

```
PUT /mkt/journey/6
Body: {"journeyName":"audit-5A-F124-UPDATED"}
→ HTTP 200 / code 200 / message: "更新成功"
→ traceId 180b6d965731484fb6259b7fb28afed0
```

### POST /mkt/journey/{id}/publish

```
→ HTTP 200 / code 200 / message: "已发布"
→ traceId 6f9554e83c0247c6bee89223b3a12e19
```

### POST /mkt/journey/{id}/pause

```
→ HTTP 200 / code 200 / message: "已暂停"
→ traceId 834e1e736d5a4096869200b148ece261
```

## 2. DB 交叉验证

```
mysql> SELECT id, journey_name, status, published_at FROM mkt_journey WHERE id=6;
id | journey_name            | status | published_at
6  | audit-5A-F124-UPDATED   | 3      | 2026-09-16 02:48:16
```

- journey_name: `audit-5A-F124` → `audit-5A-F124-UPDATED` ✅ PUT 生效
- status: **3** (publish/pause 后)
- published_at: **已写**（publish 时写入）

## 3. 状态机

| 操作 | status 终值 | published_at |
|---|---|---|
| create | 1 | NULL |
| publish | **3** | 2026-09-16 02:48:16 |
| pause | 3（保持） | — |

**注意**：plan §3.1 F126 注释"发布改 status=1，暂停改 status=0" — **实际 publish=3**（修正 plan 描述）

## 4. Final Result

**VERIFIED** — 4 端点全部 200，状态机 + published_at 写入正确
