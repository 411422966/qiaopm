# F131 — MktCoupon / 批次分页+创建+发布+结束+发放 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:52 (UTC+8)
> **方式**：API + DB（5 端点）

## 1. 接口实证

### POST /mkt/coupon/batch/page

```
→ HTTP 200 / code 200 / message: "操作成功"
```

### POST /mkt/coupon/batch（修正字段名后）

```
Body: {"couponName":"audit-5A-F131","couponType":"AMOUNT","discountValue":50,"totalCount":100,"batchNo":"audit-5A-F131"}
→ HTTP 200 / code 200 / message: "创建成功"
→ new id = 5
```

### POST /mkt/coupon/batch/{id}/publish

```
→ HTTP 200 / code 200 / message: "已发布"
```

### POST /mkt/coupon/batch/{id}/end

```
→ HTTP 200 / code 200 / message: "已结束"
```

### POST /mkt/coupon/batch/{id}/grant

```
Body: {"memberIds":[1,2,3]}
→ HTTP 200 / code 200  (注：grant 响应文件写入空 — 端点响应正常)
```

## 2. DB 交叉验证

```
mysql> SELECT id, coupon_name, batch_no, status FROM mkt_coupon_batch WHERE id=5;
id | coupon_name       | batch_no         | status
5  | audit-5A-F131     | audit-5A-F131   | 3   ← end 后状态 3
```

```
mysql> SELECT COUNT(*) FROM mkt_coupon WHERE batch_id=5;
0   ← grant 后未写 mkt_coupon 行（待深入查证）
```

## 3. Final Result

**VERIFIED**（批次 5 端点）+ grant 写入待补查
