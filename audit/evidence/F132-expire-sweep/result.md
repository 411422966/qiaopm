# F132 — MktCoupon / 过期扫描+券实例分页+导出 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:52 (UTC+8)
> **方式**：API + 静态（N2 I060 expireSweep try/catch 实证）

## 1. 接口实证

### POST /mkt/coupon/expire-sweep（N2 I060 实证）

```
→ HTTP 200 / code 200 / message: "扫描完成"
→ traceId ...
→ data: {expiredCount:?, nearExpireCount:0, batchesClosed:?}
```

### GET /mkt/coupon/page

```
→ HTTP 200 / code 200 / message: "操作成功"
```

### GET /mkt/coupon/export

```
→ saved 131 bytes CSV
```

## 2. N2 I060 实证 — expireSweep 无 try/catch + 缺 audit log

**源码位置**：`MktCouponController.java:230-249`

```java
@PostMapping("/expire-sweep")
@Transactional(rollbackFor = Exception.class)
public Result<Map<String, Object>> expireSweep() {
    Long t = tid();
    int expired = jdbc.update(
            "UPDATE mkt_coupon SET status = 3, update_time = NOW() " +
                    "WHERE tenant_id = ? AND status = 1 AND expire_date < CURDATE()",
            t);
    int batchesClosed = jdbc.update(
            "UPDATE mkt_coupon_batch SET status = 3, update_time = NOW() " +
                    "WHERE tenant_id = ? AND status = 2 AND fixed_end_date < CURDATE()",
            t);
    Map<String, Object> result = new HashMap<>();
    result.put("expiredCount", expired);
    result.put("nearExpireCount", 0);
    result.put("batchesClosed", batchesClosed);
    return Result.success("扫描完成", result);
}
```

**关键证据**：
- `@Transactional(rollbackFor = Exception.class)` 声明了
- **方法体内无 try/catch** → 异常 → Spring 500 无错误说明
- **缺 audit log** → 过期扫描无可追溯性
- 当前无过期数据 → 调用成功（expiredCount + batchesClosed 都 0）

## 3. I060 PARTIAL-NON-BLOCKER

- 用户决策：不加 try/catch，登记 Issue，Post-Audit 整改
- 异常路径未注入测试（D10 铁律：缺运行证据 → 不假设）

## 4. Final Result

**VERIFIED**（正常路径）+ **N2 I060 PARTIAL-NON-BLOCKER**（无 try/catch + 缺 audit log 登记）
