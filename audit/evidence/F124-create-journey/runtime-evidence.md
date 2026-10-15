# F124 — MktJourney 创建旅程 — Runtime Evidence

> Feature ID: F124
> Module: 营销旅程 / 创建
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:33 (UTC+8)
> SoT: `MktJourneyController.java:31-34` + `MarketingAutomationService.createJourney`
> 验证方式: API + DB

---

## 1. 端点

```
POST /mkt/journey
```

## 2. 正确字段名（plan §3.1 F124 文档漂移）

- 必填: `journeyCode` + `journeyName`（不是 `name`）
- 可选: `journeyType` / `triggerEvent` / `triggerFilter` / `description` / `nodes` / `edges` / `operatorName` / `brandId`

## 3. 真实响应

```bash
POST /mkt/journey
body: {
  "journeyCode": "J-5A-001",
  "journeyName": "5A-F124-journey",
  "journeyType": "MARKETING",
  "triggerEvent": "manual",
  "description": "5A test",
  "nodes": [{"step":1,"type":"SEND_SMS","tplCode":"BOOKING_CONFIRMATION","delayMinutes":0}],
  "edges": [{"from":1,"to":2}]
}
```

```json
{
  "code": 200, "message": "已创建",
  "data": {
    "id": 5, "tenantId": 1,
    "journeyCode": "J-5A-001",
    "journeyName": "5A-F124-journey",
    "journeyType": "MARKETING",
    "nodesJson": "[{\"step\":1,...}]",
    "edgesJson": "[{\"from\":1,\"to\":2}]",
    "status": 1, "operatorName": "admin",
    "createTime": "2026-09-16T02:33:18"
  },
  "traceId": "c9a30f8baae442b9b5252e9d0ebbb7dc"
}
```

## 4. DB 断言

```sql
SELECT id, journey_code, journey_name, journey_type, status, deleted FROM mkt_journey WHERE id=5;
```
→ `id=5, journey_code='J-5A-001', journey_name='5A-F124-journey', status=1, deleted=0`

## 5. 关键发现

- 唯一性校验：`createJourney` 检查 `journeyCode` 重复（同 tenant）→ 二次调用返 CONFLICT 409
- nodes/edges 序列化为 JSON 字符串存 `nodesJson` / `edgesJson` 列
- 默认 status=1（草稿）
- 默认 operatorName='admin'（body 不传时）

## 6. 结论

**Final Result**: **VERIFIED**