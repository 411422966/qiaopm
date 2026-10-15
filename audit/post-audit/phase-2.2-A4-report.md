# Post-Audit Phase 2.2-A4 — I011 update selectById 移除

| 维度 | 值 |
|---|---|
| **Issue ID** | I011 |
| **Commit** | `0b5607e` fix(crm): I011 update selectById 移除（消除 Fake Failure） |
| **修复阶段** | Post-Audit 2.2-A4 |
| **修复时间** | 2026-09-17 22:05 (UTC+8) |
| **状态** | ✅ OPEN → FIXED |

---

## 1. 修复动作

| 维度 | 内容 |
|---|---|
| **修改文件** | `backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java` |
| **修改位置** | `update()` 方法 line 58-66 |
| **+3 / -1** | 单文件单方法改动 |
| **方案** | I011 §6.1 推荐方案 ① — 不调 selectById，用入参 i 作为 data |

### 1.1 代码 diff

```diff
 @PostMapping("/{id}")
 public Result<CrmIntegration> update(@PathVariable Long id, @RequestBody CrmIntegration i) {
     Long tenantId = TenantContextHolder.get().getTenantId();
     i.setId(id);
     i.setTenantId(tenantId);
     integrationMapper.updateById(i);
-    return Result.success("已更新", integrationMapper.selectById(id));
+    // 不调 selectById — 避免 I002 缺列抛错导致 Fake Failure（数据已 UPDATE 但 API 报 500）。
+    // UPDATE 已成功提交，用入参 i（含本次提交字段）作为返回 data。
+    return Result.success("已更新", i);
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
- [x] git commit 独立 `0b5607e`
- [x] I011 状态更新：OPEN → ✅ FIXED
- [x] 本报告落档
- [ ] curl 验证（**阶段 2.2 末尾统一跑**）

---

## 4. 影响

| 场景 | 修复前 | 修复后 |
|---|---|---|
| F003 update 正常 | 200 + DB 更新 + 返回 DB 最新值 | 200 + DB 更新 + **返回入参** |
| F003 update DB 缺列（I002 触发）| **500 + DB 已变（数据漂移）** | **200 + DB 已变（无漂移）** |
| F001 create（待修）| 同模式 → Fake Failure | 未改（不在本次范围）|

---

**I011 修复完成（Post-Audit 2.2-A4）。**
