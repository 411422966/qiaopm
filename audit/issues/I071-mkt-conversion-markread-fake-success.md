# I071 — `MktConversionController.markRead` 假成功（Fake Success 沿用 I008 模式）

> **发现时间**：2026-09-16 02:45 (UTC+8)
> **Phase**：5A — Runtime Verification
> **关联 Feature**：F123（MktConversion / 标记已读）
> **严重度**：🔴 **CRITICAL**（HTTP 200 但 DB 0 行更新 → 监控失效 + 业务方误判）
> **类别**：Fake Success（I008 / I011 / I032 同模式）
> **修复阶段**：**Post-Audit**（Phase 7）

---

## 1. 摘要

`MarketingAutomationService.markRead()` 调用 `UPDATE marketing_touch_log SET status=4, read_at=NOW() WHERE id=? AND status=2`，但实际数据 `id=1` 当前 `status=1`（不是 2），导致 0 行更新；方法仍然返回 `Result.success("已标记", null)` → 调用方收到 200 已标记但 DB 完全未更新。

| 维度 | 值 |
|---|---|
| **触发条件** | `POST /mkt/conversion/mark-read` body `{"id":1}`（log 当前 status≠2）|
| **失败现象** | HTTP 200 / code 200 / message="已标记" — **但 DB 0 行更新** |
| **根本原因** | `markRead` 不校验 `UPDATE rows > 0`，无论更新生效与否都返回 success |
| **影响范围** | F123 全部 mark-read 调用 — 静默失败 |
| **同模式沿用** | I008（Dashboard catch 吞异常）/ I011（CRM Fake Failure）/ I032（Google Maps 无 log） |
| **批次元数据** | Batch 1.5（Phase 5A 实证发现） |
| **修复阶段** | **Post-Audit**（Phase 7）|
| **修复状态** | ✅ **FIXED**（2026-09-17 commit `3d94c56` / Post-Audit 2.2-A6）|

---

## 2. 实证证据（Phase 5A）

### 2.1 运行时实证

```
POST http://localhost:8080/mkt/conversion/mark-read
Body: {"id":1}
→ HTTP 200 / code 200 / message: "已标记"
→ traceId b6a287a9f44243c1b8ab8a2aa3f377b9
→ timestamp 2026-09-16 02:45
```

### 2.2 DB 实证

```sql
mysql> SELECT id, status, read_at, tenant_id FROM marketing_touch_log WHERE id=1;
+----+--------+----------+------------+
| id | status | read_at  | tenant_id  |
+----+--------+----------+------------+
|  1 |      1 | NULL     | 2          |
+----+--------+----------+------------+

-- 5A 调用 markRead(id=1) 前：status=1 read_at=NULL
-- 5A 调用 markRead(id=1) 后：status=1 read_at=NULL（0 行更新）
-- 但 HTTP 200 success 已返回
```

### 2.3 代码位置

`backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java:762-768`：

```java
public Result<Void> markRead(@PathVariable Long id) {
    jdbc.update("UPDATE marketing_touch_log SET status = 4, read_at = NOW() WHERE id = ? AND status = 2", id);
    jdbc.update("UPDATE marketing_touch_task SET read_count = read_count + 1 WHERE id = " +
            "(SELECT task_id FROM marketing_touch_log WHERE id = ?)", id);
    return Result.success("已标记", null);  // ← 不校验 UPDATE 影响行数
}
```

→ 两处 UPDATE 均无 rows > 0 校验 → 静默失败但仍返回 success

---

## 3. N10 I057 关联

F122 trackConversion 走业务前置校验（touchLogId 999 不存在 → 404），未实测跨租户写；但 F123 markRead **同样无 tenantId 过滤**：

```java
// markRead(id) 没有 @RequestHeader / TenantContextHolder 检查
// 任何租户用户都能 markRead 任何 id → 跨租户写风险
```

| 维度 | 风险等级 |
|---|---|
| 跨租户写（无 tenantId 过滤）| 🟠 中-高（沿用 I057）|
| Fake Success（无 rows > 0 校验）| 🔴 CRITICAL（I071 NEW）|

---

## 4. 影响 Feature

| Feature ID | Module / 功能点 | 当前 Result | 修复后 Result |
|---|---|---|---|
| **F123** | MktConversion / 标记已读 | **PARTIAL-NON-BLOCKER + Fake Success** | VERIFIED（修复 `if (rows > 0)` + 加 `AND tenant_id = ?` 后） |

---

## 5. 修复建议（Post-Audit）

```java
public Result<Void> markRead(@PathVariable Long id) {
    int rows = jdbc.update(
        "UPDATE marketing_touch_log SET status = 4, read_at = NOW() WHERE id = ? AND tenant_id = ? AND status = 2",
        id, TenantContextHolder.get().getTenantId()
    );
    if (rows == 0) {
        return Result.error(BizCode.NOT_FOUND, "触达记录不存在或已标记");
    }
    // ... 后续 UPDATE 同理加 rows 校验 + tenantId 过滤
    return Result.success("已标记", null);
}
```

---

## 6. 关联文档

- `audit/evidence/F123-mark-read/raw-response.json` — 200 success 实证
- `audit/evidence/F123-mark-read/result.md` — F123 Phase 5A 验证报告
- `audit/runtime/report-5A-batch-1.5.md` §一 F123 + §三 I071 NEW
- `audit/runtime/plan-batch-1.5.md` §三.3.1 F123 + §五.5 N10

---

## 7. Post-Audit 修复实录（批次 2.2-A6）

### 7.1 修复动作

| 维度 | 内容 |
|---|---|
| **commit** | `3d94c56` fix(mkt): I071 markRead 加 rows 校验 + tenantId 过滤 |
| **修改文件** | `MarketingAutomationService.java` 单文件，+11 / -2 行 |
| **改动** | UPDATE 加 `AND tenant_id = ?` + 接收 `int logRows` 校验 + 0 行返回 `Result.error(NOT_FOUND)`；task UPDATE 也加 tenantId 过滤 |
| **mvn compile** | ✅ BUILD SUCCESS（exit code 0）|
| **运行时验证** | ✅ markRead 不存在的 id 返回 404（修复前 200），跨租户 id 不再串写 |

### 7.2 修复前后对比

```java
// ❌ 修复前：Fake Success
public Result<Void> markRead(@PathVariable Long id) {
    jdbc.update("UPDATE marketing_touch_log SET status = 4, read_at = NOW() WHERE id = ? AND status = 2", id);
    jdbc.update("UPDATE marketing_touch_task SET read_count = read_count + 1 WHERE id = " +
            "(SELECT task_id FROM marketing_touch_log WHERE id = ?)", id);
    return Result.success("已标记", null);  // ← 不校验 rows
}

// ✅ 修复后：rows 校验 + tenantId 过滤
public Result<Void> markRead(@PathVariable Long id) {
    Long t = TenantContextHolder.get().getTenantId();
    int logRows = jdbc.update(
        "UPDATE marketing_touch_log SET status = 4, read_at = NOW() WHERE id = ? AND tenant_id = ? AND status = 2",
        id, t);
    if (logRows == 0) {
        return Result.error(BizCode.NOT_FOUND, "触达记录不存在或非待读状态");
    }
    jdbc.update("UPDATE marketing_touch_task SET read_count = read_count + 1 WHERE id = " +
            "(SELECT task_id FROM marketing_touch_log WHERE id = ? AND tenant_id = ?)",
            id, t);
    return Result.success("已标记", null);
}
```

### 7.3 影响

| 场景 | 修复前 | 修复后 |
|---|---|---|
| markRead 存在且 status=2 的 log | 200 success + DB 更新 | 200 success + DB 更新（不变）|
| markRead 不存在 / status≠2 | **200 success（Fake Success）** | **404 NOT_FOUND** |
| 跨租户 markRead | **200 success + 串写** | **404 NOT_FOUND（被 tenantId 过滤掉）** |
| task read_count 自增 | 无条件执行 | 仅 logRows > 0 时执行 |

### 7.4 一修一验清单

- [x] mvn compile 通过
- [x] git commit 独立（`3d94c56`）
- [x] I071 状态更新：OPEN → ✅ FIXED
- [x] A6 报告落档：`audit/post-audit/phase-2.2-A6-report.md`

---

**I071 修复完成（Post-Audit 2.2-A6）。**