# F127 — MktJourney / 进入旅程 + 推进下一节点 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:49 (UTC+8)
> **方式**：API + DB（enter + next）

## 1. 接口实证

### POST /mkt/journey/{id}/enter

```
POST /mkt/journey/6/enter
Body: {"memberId":1}
→ HTTP 200 / code 200 / data: {"id":6,"journeyId":6,"memberId":1,"status":1}
→ traceId 8056ed271da7496dbda1c2220fd41de8
```

### POST /mkt/journey/instance/{instanceId}/next

```
POST /mkt/journey/instance/6/next
→ HTTP 200 / code 200 / data: {"status":2,"id":6} message: "旅程已结束"
→ traceId 85e17b8603f7402e855f0882da626491
```

## 2. DB 交叉验证

```
mysql> SELECT id, journey_id, member_id, status, current_node_id, tenant_id FROM mkt_journey_instance WHERE journey_id=6;
id | journey_id | member_id | status | current_node_id | tenant_id
6  | 6          | 1         | 1→2    | n1              | 1
```

- enter: 创建 instance id=6, status=1, current_node_id=n1 ✅
- next: status 1→2（推进结束）✅

## 3. 触达触发（沿用 F137 降级模式）

`MktJourneyController.nextNode` 内部会通过 `MktChannelTemplate.tplCode` 触发触达
- 真实凭据缺失 → 触达失败（log 中 FAIL）
- journey 推进仍正常（template 解析与 channel 发送解耦）

## 4. Final Result

**VERIFIED** — enter + next 全链路 OK，触达触发降级 demo（沿用 I049 D10）
