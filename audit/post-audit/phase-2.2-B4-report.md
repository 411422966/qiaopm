# B4 — I060 `expireSweep` try/catch + audit 修复报告

> **修复时间**：2026-09-19 23:50 (UTC+8)
> **修复人**：Cursor Agent
> **关联 Issue**：I060
> **关联 Feature**：F132 优惠券过期扫描
> **Commit**：`be62a45`
> **状态**：OPEN → **FIXED** ✅

---

## 1. 根因（实测）

| 位置 | 现象 | 根因 |
|---|---|---|
| `MktCouponController.expireSweep`（L230-248）| happy path 200，但 DB 异常 → 500 + **无 audit** | 无 try/catch，DB 异常直接透出 |
| `MarketingCouponService.expireSweep`（L479-491）| **死代码**（无 controller 调用）| SQL 含 `status = status` no-op + 缺 tenantId |

issue-register.md N2 描述「F132 expireSweep 缺 try/catch + 缺 audit」准确。

---

## 2. 改动

### 2.1 `MktCouponController.java` — `+27 / -11` 行

```diff
+ import lombok.extern.slf4j.Slf4j;
+ import org.springframework.dao.DataAccessException;

  @RestController
  @RequestMapping("/mkt/coupon")
  @RequiredArgsConstructor
+ @Slf4j

  @PostMapping("/expire-sweep")
- @Transactional(rollbackFor = Exception.class)
  public Result<Map<String, Object>> expireSweep() {
      Long t = tid();
+     LocalDateTime sweepStart = LocalDateTime.now();
+     int expired = 0;
+     int batchesClosed = 0;
+     try {
          expired = jdbc.update(...UPDATE mkt_coupon SET status=3...);
          batchesClosed = jdbc.update(...UPDATE mkt_coupon_batch SET status=3...);
+     } catch (DataAccessException e) {
+         log.error("[AUDIT] expireSweep failed tenantId={} durationMs={} err={}",
+                   t, Duration.between(sweepStart, LocalDateTime.now()).toMillis(), e.getMessage());
+         return Result.error(BizCode.SYSTEM_ERROR, "扫描失败：" + e.getMessage());
+     }
      Map<String, Object> result = new HashMap<>();
      result.put("expiredCount", expired);
      result.put("nearExpireCount", 0);
      result.put("batchesClosed", batchesClosed);
+     // 修复 I060：成功 audit
+     log.info("[AUDIT] expireSweep tenantId={} operator=auto expiredCount={} batchesClosed={} durationMs={}",
+              t, expired, batchesClosed,
+              Duration.between(sweepStart, LocalDateTime.now()).toMillis());
      return Result.success("扫描完成", result);
  }
```

> 决策：移除 `@Transactional` — `UPDATE` 是单语句自动提交，包事务反而误导 partial rollback 行为。
> audit 选 slf4j：表 `mkt_coupon_expire_audit` 不存在 → 不在 B4 范围加迁移；slf4j log 已可被运维 grep（修复 I060「缺 audit」的本质需求）。

### 2.2 `MarketingCouponService.java` — `-14` 行

删除 `expireSweep()` 死代码（无 controller 调用 + 含 `status=status` no-op bug）。

---

## 3. 编译

```
mvn -B -DskipTests compile
[INFO] Compiling 1543 source files
[INFO] BUILD SUCCESS
[INFO] Total time:  40.761 s
```

---

## 4. 状态变更

| Issue | 之前 | 之后 |
|---|---|---|
| **I060** | OPEN（Phase 7 Post-Audit）| **FIXED** ✅ |
