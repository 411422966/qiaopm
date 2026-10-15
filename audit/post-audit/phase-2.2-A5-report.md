# Post-Audit Phase 2.2-A5 — I047 IG webhook catch 提升为 error

| 维度 | 值 |
|---|---|
| **Issue ID** | I047 |
| **Commit** | `f0c73d8` fix(intl): I047 IG webhook catch 提升为 error + Result.error |
| **修复阶段** | Post-Audit 2.2-A5 |
| **修复时间** | 2026-09-17 22:08 (UTC+8) |
| **状态** | ✅ OPEN → FIXED |
| **关联** | I008（CRM dashboard 同模式）/ I037（webhook path exclude — 已 A1 修） |

---

## 1. 修复动作

| 维度 | 内容 |
|---|---|
| **修改文件** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java` |
| **修改位置** | `instagramWebhook()` 方法 line 311-360 |
| **+5 / -2** | 单文件单方法改动 |
| **方案** | I047 §5 推荐方案 ① — catch 改为 log.error + Result.error |

### 1.1 代码 diff

```diff
 } catch (Exception e) {
-    log.warn("[INSTAGRAM WEBHOOK] parse failed: {}", e.getMessage());
+    log.error("[INSTAGRAM WEBHOOK] parse failed: {}", e.getMessage(), e);
+    return Result.error(BizCode.SYSTEM_ERROR, "IG webhook 解析失败: " + e.getMessage());
 }
-return Result.success(Map.of("code", "NOOP", "message", "no parseable message"));
+// payload 合法但无 parseable message（如 IG 推送 sticker / 已读回执）— 合法 NOOP
+return Result.success(Map.of("code", "NOOP", "message", "no parseable message"));
```

---

## 2. 编译验证

```powershell
$ mvn compile
[INFO] BUILD SUCCESS
exit_code: 0
```

> ⚠️ 修复首版缺 NOOP fallback 导致编译报错（line 359 缺少 return），第二次修复补上 NOOP 路径作为合法空负载处理。

---

## 3. 一修一验清单

- [x] mvn compile 通过（exit 0）
- [x] git commit 独立 `f0c73d8`
- [x] I047 issue file 创建（`I047-intl-ig-webhook-catch-swallow.md`）+ 状态 OPEN → ✅ FIXED
- [x] 本报告落档
- [ ] curl 验证（**阶段 2.2 末尾统一跑**）

---

## 4. 影响

| 场景 | 修复前 | 修复后 |
|---|---|---|
| IG DM 含 text + overseas_booking 表缺失（I038） | **200 NOOP（静默丢数据）** | **500 + log.error（Meta 重试 + 运维告警）** |
| IG 推 sticker / 已读回执（合法空负载）| 200 NOOP | 200 NOOP（**保留** — 单独路径）|
| IG 推正常 text + DB OK | 200 SUCCESS | 200 SUCCESS（不变）|
| 跨租户 IG webhook（演示模式 tenantId=1L 硬编码）| 200 NOOP | 500（演示模式不会写库）|

---

**I047 修复完成（Post-Audit 2.2-A5）。**
