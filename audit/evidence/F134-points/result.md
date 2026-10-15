# F134 — MktPoints / 积分规则列表+创建+更新+手动发分 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:54 (UTC+8)
> **方式**：API + DB（4 端点 + plan 字段名修正）

## 1. 接口实证

### GET /mkt/points/rule/list

```
→ HTTP 200 / code 200 / message: "操作成功"
```

### POST /mkt/points/rule

```
Body: {"ruleName":"audit-5A-F134","consumeRate":1.5,"rechargeMultiplier":2.0,"priority":10}
→ HTTP 200 / code 200 / message: "创建成功"
→ new id = 5
```

### PUT /mkt/points/rule/{id}

```
PUT /mkt/points/rule/5
Body: {"ruleName":"audit-5A-F134-UPDATED","priority":20}
→ HTTP 200 / code 200 / message: "更新成功"
```

### POST /mkt/points/award

```
Body: {"memberId":1,"amount":100,"sourceType":"MANUAL","remark":"audit-5A-F134"}
→ HTTP 200 / code 200 / message: "已发放"
→ data: {recordId:494, points:100}
```

## 2. DB 交叉验证（plan 字段名修正）

**plan §3.1 F134 注释写**："前后 diff `mkt_points_rule` + `mkt_points_log`"
**Phase 5A 实证**：award 实际写入 `market_point_record` 表，**不是** `mkt_points_log`

```
mysql> SELECT id, member_id, point_value, balance_before, balance_after, source, remark FROM market_point_record WHERE remark='audit-5A-F134';
id | member_id | point_value | balance_before | balance_after | source  | remark
494| 1         | 100         | 3310            | 3410          | manual  | audit-5A-F134
```

```
mysql> SELECT id, rule_name, priority FROM mkt_points_rule WHERE id=5;
id | rule_name                | priority
5  | audit-5A-F134-UPDATED   | 20
```

- rule 表更新：priority 10 → 20 ✅
- award 写入：`market_point_record.id=494` + balance 3310→3410 ✅

## 3. 链路分析

`MktPointsController.award` → `pointService.addPointsReturningId` → `casPointsWithRetry` → `memberMapper.casPointsWithVersion` → `writePointRecordReturningId` → `INSERT INTO market_point_record`

## 4. Final Result

**VERIFIED** — 4 端点全部 200 + award 写入 market_point_record 实证
- plan §3.1 F134 字段名修正：`mkt_points_log` → `market_point_record`
