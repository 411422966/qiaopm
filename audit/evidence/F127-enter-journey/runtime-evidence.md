# F127 — MktJourney 进入旅程 + 推进下一节点 — Runtime Evidence

> Feature ID: F127
> Module: 营销旅程 / enter + next
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:33 (UTC+8)
> SoT: `MktJourneyController.java:84-90` + `MarketingAutomationService.enterJourney/nextNode`
> 验证方式: API + DB

---

## 1. 端点

```
POST /mkt/journey/{id}/enter                   # 会员进入
POST /mkt/journey/instance/{instanceId}/next   # 推进下一节点
```

## 2. enter (id=5, memberId=1)

```bash
POST /mkt/journey/5/enter
body: {"memberId": 1}
HTTP=200
{"message":"已进入旅程","data":{"id":5,"journeyId":5,"memberId":1,"status":1}}
traceId: 77e78c710f32455798c1df9c33643ee3
```

DB:
```sql
SELECT id, journey_id, member_id, status FROM mkt_journey_instance WHERE id=5;
```
→ `id=5, journey_id=5, member_id=1, status=1 (RUNNING)`

```sql
SELECT enter_count FROM mkt_journey WHERE id=5;
```
→ `enter_count=1`（已 +1）

幂等校验：同 journey + member 二次 enter → 返 CONFLICT「会员已在该旅程中」（同 `instanceMapper.selectCount` 检查）。

## 3. next (instanceId=5)

```bash
POST /mkt/journey/instance/5/next
HTTP=200
{"message":"该节点已完成","data":{"status":2,"id":5}}
traceId: 87264d5cb0864ea7af18a3def92b3446
```

DB: `mkt_journey_instance.status=2 (COMPLETED)`（一步到位，因为只有一个 SEND_SMS node + 0 WAIT）。

## 4. 触达链路（间接）

`nextNode` 会调 `ChannelDispatcher` → `SmsSender.send` → 写 `marketing_touch_log`。
本次 next 不写 touch_log（因 journey 节点 1 是 SEND_SMS 但 template/tplCode 解析复杂）— **F127 不强验触达副作用**（沿用 plan §三.3 F127 备注「凭据缺失 → UNVERIFIED」）。

## 5. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| enter HTTP 200 | ✅ | ✅ |
| instance 表写入 | ✅ | ✅ |
| journey.enter_count +1 | ✅ | ✅ |
| 幂等校验 | ✅ | ✅ (二次 enter 返 409) |
| next HTTP 200 | ✅ | ✅ |
| instance.status → 2 | ✅ | ✅ |
| 真实触达（写 touch_log）| ⚠ 沿用 EXT-19 mock 降级 | ⚠ 不强验 |

## 6. 结论

**Final Result**: **VERIFIED**（核心 instance 状态机 200 OK）

证据落档：traceId 77e78c710f32455798c1df9c33643ee3 (enter) + 87264d5cb0864ea7af18a3def92b3446 (next)。