# Post-Audit Phase 2.2-A3 — I008 dashboard catch 吞异常

| 维度 | 值 |
|---|---|
| **Issue ID** | I008 |
| **Commit** | `776e8be` fix(crm): I008 dashboard catch swallow (log.error) |
| **修复阶段** | Post-Audit 2.2-A3 |
| **修复时间** | 2026-09-17 22:02 (UTC+8) |
| **状态** | ✅ OPEN → FIXED |

---

## 1. 修复动作

| 维度 | 内容 |
|---|---|
| **修改文件** | `backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java` |
| **修改位置** | `dashboard()` 方法 line 142-155 |
| **+2 / -1** | 单文件单方法改动 |
| **方案** | I008 §4 推荐方案 ③ — `catch (Exception ignore) {}` → `catch (Exception e) { log.error(...); }` |

### 1.1 代码 diff

```diff
 @GetMapping("/dashboard")
 public Result<Map<String, Object>> dashboard() {
     Long tenantId = TenantContextHolder.get().getTenantId();
     Map<String, Object> data = new LinkedHashMap<>();
     try {
         data.put("totalIntegrations", integrationMapper.selectCount(...));
         data.put("totalSyncLogs", syncLogMapper.selectCount(...));
-    } catch (Exception ignore) {}
+    } catch (Exception e) {
+        log.error("[CRM-DASHBOARD] count failed for tenantId={}", tenantId, e);
+    }
     return Result.success(data);
 }
```

---

## 2. 编译验证

```powershell
$ mvn compile -q
[INFO] BUILD SUCCESS
exit_code: 0
```

---

## 3. 一修一验清单

- [x] mvn compile 通过（exit 0）
- [x] git commit 独立 `776e8be`
- [x] I008 状态更新：OPEN → ✅ FIXED
- [x] 本报告落档
- [ ] curl 验证（**阶段 2.2 末尾统一跑**）

---

## 4. 影响

| 场景 | 修复前 | 修复后 |
|---|---|---|
| Dashboard 正常 | 200 + 正常 data | 200 + 正常 data（不变）|
| Dashboard DB 缺列（I002 触发）| 200 + data=null（静默）| **200 + data=null（log.error 包含 stacktrace + tenantId）** |
| Dashboard 网络异常 | 200 + 空 data（静默）| 200 + 空 data（log.error）|
| 运维可观测性 | 无 | **有（ERROR 日志可被 ELK / AlertManager 检测）**|

---

**I008 修复完成（Post-Audit 2.2-A3）。**
