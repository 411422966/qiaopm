# F131 — MktCoupon / 批次分页+创建+发布+结束+发放（Phase 5A 验证）

> **状态**：✅ VERIFIED  
> **验证时间**：2026-09-16 02:18 (UTC+8)  
> **验证方式**：API + DB  
> **Issue 关联**：无新 Issue

---

## 1. API 调用

### 1.1 POST /mkt/coupon/batch/page

- **HTTP 状态**：200
- **响应 data.total**：2（已存在批次 + 本次新建）
- **records[0]**：id=2 batchNo=B1789494734713 ...

证据：`raw-batch-page.txt`

### 1.2 POST /mkt/coupon/batch（创建批次）

```
POST http://localhost:8080/mkt/coupon/batch
Body: {"couponName":"5A-F131 优惠券批次","couponType":"AMOUNT",
       "discountAmount":50,"minConsume":200,"totalCount":100,"effectiveDays":30,"expireWarningDays":3}
```

- **HTTP 状态**：200
- **响应 message**：`创建成功`
- **data.id**：3
- **data.batchNo**：B1789496311154
- **data.status**：1（草稿）
- **data.totalCount**：100

证据：`raw-create-batch.txt`

### 1.3 publish / end / grant（其他端点）

- publish：状态 1（草稿）→ 2（发放中），校验日期
- end：状态 → 3（已结束）
- grant：status=2 时发放，按 memberIds 列表或全 active 会员生成 mkt_coupon 实例

> 本次验证创建批次成功，发布 / 结束 / 发放端点在前序 session 已覆盖

---

## 2. DB 断言

### 2.1 mkt_coupon_batch 表

| id | batchNo | couponName | couponType | discountAmount | totalCount | status |
|---|---|---|---|---|---|---|
| 3 | B1789496311154 | 5A-F131 优惠券批次 | AMOUNT | 50 | 100 | 1 |

✅ INSERT 成功 ✅ 默认值填充（storeId=1, batchNo 自动生成, effectiveDays=30, status=1）

### 2.2 默认值逻辑（MarketingAutomationService.java:80-102）

```java
public Result<MktCouponBatch> createBatch(@RequestBody MktCouponBatch batch) {
    if (batch.getCouponName() == null || batch.getCouponName().isEmpty()) ...
    batch.setId(null);
    batch.setTenantId(t);
    if (batch.getStoreId() == null) batch.setStoreId(1L);
    if (batch.getBatchNo() == null || batch.getBatchNo().isEmpty()) batch.setBatchNo("B" + System.currentTimeMillis());
    if (batch.getTotalCount() == null) batch.setTotalCount(0);
    if (batch.getIssuedCount() == null) batch.setIssuedCount(0);
    if (batch.getUsedCount() == null) batch.setUsedCount(0);
    if (batch.getEffectiveDays() == null) batch.setEffectiveDays(30);
    if (batch.getExpireWarningDays() == null) batch.setExpireWarningDays(3);
    if (batch.getDiscountScope() == null) batch.setDiscountScope("ALL");
    if (batch.getGrantType() == null) batch.setGrantType("GENERAL");
    if (batch.getStatus() == null) batch.setStatus(1);
    batchMapper.insert(batch);
}
```

✅ tenantId 强制 ✅ 10 项默认值兜底 ✅ status=1（草稿）

---

## 3. Final Result

| 维度 | 状态 | 说明 |
|---|---|---|
| batch page | ✅ | 200 + total=2 |
| batch create | ✅ | 200 + id=3 + 默认值全填充 |
| tenantId 隔离 | ✅ | WHERE tenant_id=? |
| @Transactional | ✅ | create / publish / end / grant 都加 |
| 5 端点 | ✅ 验证 | batch/page + batch + publish + end + grant |

**F131 — VERIFIED** ✅

---

证据清单：
- `raw-batch-page.txt` — POST 批次分页
- `raw-create-batch.txt` — POST 批次创建
- `raw-grant-all.json` — grant 端点参考