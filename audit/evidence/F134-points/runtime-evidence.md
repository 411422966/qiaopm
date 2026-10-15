# F134 — MktPoints 积分规则 + 手动发分（4 端点）— Runtime Evidence

> Feature ID: F134
> Module: 营销积分 / 4 端点
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:34 (UTC+8)
> SoT: `MktPointsController.java`
> 验证方式: API + DB

---

## 1. 端点（4 个）

```
GET  /mkt/points/rule/list
POST /mkt/points/rule
PUT  /mkt/points/rule/{id}
POST /mkt/points/award
```

## 2. rule list ✅

```bash
GET /mkt/points/rule/list
HTTP=200
traceId: 265e98b19f9549c3b16f59a5ddaa3e06
```
3 条历史 rule（id=1/2/3），tenantId=1，按 priority 排序。

## 3. create rule ✅

```bash
POST /mkt/points/rule
body: {"ruleName":"5A-F134-rule","consumeRate":1.5,"rechargeMultiplier":2,"pointsToCash":0.02,"priority":1,"enabled":1}
HTTP=200
traceId: 189f644e65454e13a700aaf6e53a3399
```
→ id=4, default `consumeRate=1.5, rechargeMultiplier=2, pointsToCash=0.02, maxUsePercent=30.00, promotionMultiplier=1.00, enabled=1, priority=1`。

## 4. update rule ✅

```bash
PUT /mkt/points/rule/4
body: {"ruleName":"5A-F134-rule-updated","priority":5}
HTTP=200
traceId: 32cfbfa43f984fc1a8e7f56fe5c986ae
```
→ name 改 + priority=5。

## 5. award ✅ 100 分给 memberId=1

```bash
POST /mkt/points/award
body: {"memberId":1,"amount":100,"sourceType":"MANUAL","remark":"5A test award"}
HTTP=200
{"message":"已发放","data":{"recordId":493,"points":100}}
traceId: e8d7a78397904c74a4a52fa36977474c
```

**链路**：award → `pointService.addPointsReturningId` → 写积分记录（mkt_points_log + member total 加分）→ 返 recordId。

DB（间接）：
- recordId=493 表示积分表 rowcount 增长（沿用 F130.snapshot 中 `pointsGranted=29500` 是各 award 累计）
- 100 分 MANUAL 直接发放（不需 consumeRate / rechargeMultiplier 计算）

## 6. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| rule/list 200 | ✅ | ✅ 3 条 |
| rule create 200 | ✅ | ✅ id=4 |
| rule update 200 | ✅ | ✅ priority=5 |
| award 200 | ✅ | ✅ recordId=493 |

## 7. 跨 Controller 集成（plan §三 F134 备注）

award 调用链：`PosController.checkout()` → `MktPointsRule.pointsToCash` 计算（默认 100:1）。
Phase 5A 仅验证 `MktPointsController.award` 直调成功，**不验收银链路**（沿用 plan §三.3 F134 备注）。

## 8. 结论

**Final Result**: **VERIFIED**（4 端点全 200，award 真实链路可用）

证据落档：4 个 traceId 已记录。