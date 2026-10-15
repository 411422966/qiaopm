# Post-Audit Phase 2.2-A6 — I071 markRead 加 rows 校验 + tenantId 过滤

| 维度 | 值 |
|---|---|
| **Issue ID** | I071 |
| **Commit** | `3d94c56` fix(mkt): I071 markRead 加 rows 校验 + tenantId 过滤 |
| **修复阶段** | Post-Audit 2.2-A6 |
| **修复时间** | 2026-09-17 22:10 (UTC+8) |
| **状态** | ✅ OPEN → FIXED |
| **关联** | I057（跨租户写 N10）/ I008（同模式）|

---

## 1. 修复动作

| 维度 | 内容 |
|---|---|
| **修改文件** | `backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java` |
| **修改位置** | `markRead()` 方法 line 762-768 |
| **+11 / -2** | 单文件单方法改动 |
| **方案** | I071 §5 推荐 — rows 校验 + tenantId 过滤 |

### 1.1 代码 diff

```diff
 public Result<Void> markRead(@PathVariable Long id) {
-    jdbc.update("UPDATE marketing_touch_log SET status = 4, read_at = NOW() WHERE id = ? AND status = 2", id);
-    jdbc.update("UPDATE marketing_touch_task SET read_count = read_count + 1 WHERE id = " +
-            "(SELECT task_id FROM marketing_touch_log WHERE id = ?)", id);
-    return Result.success("已标记", null);
+    Long t = TenantContextHolder.get().getTenantId();
+    // I071 修复：加 tenantId 过滤（防跨租户写）+ 校验 rows 影响数（消除 Fake Success）
+    int logRows = jdbc.update(
+            "UPDATE marketing_touch_log SET status = 4, read_at = NOW() " +
+            "WHERE id = ? AND tenant_id = ? AND status = 2",
+            id, t);
+    if (logRows == 0) {
+        return Result.error(BizCode.NOT_FOUND, "触达记录不存在或非待读状态");
+    }
+    jdbc.update("UPDATE marketing_touch_task SET read_count = read_count + 1 WHERE id = " +
+            "(SELECT task_id FROM marketing_touch_log WHERE id = ? AND tenant_id = ?)",
+            id, t);
+    return Result.success("已标记", null);
 }
```

---

## 2. 编译验证

```powershell
$ mvn compile
[INFO] BUILD SUCCESS
exit_code: 0
```

---

## 3. 一修一验清单

- [x] mvn compile 通过（exit 0）
- [x] git commit 独立 `3d94c56`
- [x] I071 状态更新：OPEN → ✅ FIXED
- [x] 本报告落档
- [ ] curl 验证（**阶段 2.2 末尾统一跑**）

---

## 4. 影响

| 场景 | 修复前 | 修复后 |
|---|---|---|
| markRead 存在且 status=2 | 200 + DB 更新 | 200 + DB 更新（不变）|
| markRead 不存在 / status≠2 | **200 success（Fake Success）** | **404 NOT_FOUND** |
| 跨租户 markRead | **200 success + 串写** | **404 NOT_FOUND（被 tenantId 过滤掉）** |
| task read_count 自增 | 无条件执行 | **仅 logRows > 0 时执行** |

---

**I071 修复完成（Post-Audit 2.2-A6）。**
