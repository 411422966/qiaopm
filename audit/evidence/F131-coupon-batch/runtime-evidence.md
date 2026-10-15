# F131 — MktCoupon 批次全流程（5 端点，含 grant ❌）— Runtime Evidence

> Feature ID: F131
> Module: 营销卡券批次 / 5 端点
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:34 (UTC+8)
> SoT: `MktCouponController.java`
> 验证方式: API + DB

---

## 1. 端点（5 个）

```
POST /mkt/coupon/batch/page
POST /mkt/coupon/batch
POST /mkt/coupon/batch/{id}/publish
POST /mkt/coupon/batch/{id}/end
POST /mkt/coupon/batch/{id}/grant        ← ❌ 500
```

## 2. batch page ✅

```bash
POST /mkt/coupon/batch/page body={"pageNum":1,"pageSize":5}
HTTP=200
traceId: 8e2658aa48024750a9849189db939b04
```
3 条历史 batch（id=1/2/3），全部 tenantId=1。

## 3. create ✅

```bash
POST /mkt/coupon/batch
body: {"couponName":"5A-coupon-batch","couponType":"FULL_REDUCE","discountAmount":50,"minConsume":200,"totalCount":10,"effectiveDays":30,"discountScope":"ALL"}
HTTP=200
traceId: d6492cd8e9e74d72b83aef021caad4a5
```
→ id=4, status=1 (草稿)。

## 4. publish ✅

```bash
POST /mkt/coupon/batch/4/publish
HTTP=200
traceId: b3982ac3701d4e0cb55a688c0c5b1afc
```
→ status=2 (发放中)。

## 5. ❌ grant 失败 — 表名错误 `member` 应为 `crm_member`

```bash
POST /mkt/coupon/batch/4/grant
body: {"memberIds":[1,2,3],"countPerMember":1}
HTTP=500
{"code":500,"message":"数据库异常","data":null,"traceId":"fad28014b0b04016936da0bd7f9aad85"}
```

**Caused by**: `org.springframework.jdbc.BadSqlGrammarException: bad SQL grammar [SELECT id, member_name FROM member WHERE tenant_id = ? AND deleted = 0 AND status = 1 AND id IN (?,?,?)]`

**根因**: `MktCouponController.java:155` (grantBatch) 写的是：
```java
StringBuilder memberSql = new StringBuilder("SELECT id, member_name FROM member WHERE ... ")
```
但实际表名是 `crm_member`（F129 task 创建时用的就是 `crm_member`，已实证）。

**新触达 Issue: I072 NEW**（与 F122/F131 复合 — 表名/字段名漂移）。

## 6. end ✅

```bash
POST /mkt/coupon/batch/4/end
HTTP=200
traceId: 6819760d17924f228d5920b79acc04d0
```
→ status=3 (已结束)。

## 7. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| page 200 | ✅ | ✅ |
| create 200 | ✅ | ✅ id=4 |
| publish 200 | ✅ | ✅ status=2 |
| grant 200 | ✅ | ❌ 500 (表名错) |
| end 200 | ✅ | ✅ status=3 |

## 8. 结论

**Final Result**: **PARTIAL-NON-BLOCKER + I072 NEW 实证** — 4/5 端点 200，grant 端点 BROKEN。

Post-Audit 修复：`MktCouponController.java:155` `FROM member` → `FROM crm_member`，列 `member_name` → `name`（crm_member.name 已被 F129 实证）。

证据落档：6 个 traceId 已记录。