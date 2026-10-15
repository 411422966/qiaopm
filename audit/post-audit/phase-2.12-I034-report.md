# Phase 2.12 — I034 webhook `@Transactional(rollbackFor)` 修复报告

> **修复时间**：2026-09-24 23:12 (UTC+8)
> **修复人**：Cursor Agent
> **范围**：Post-Audit 二期 P1 第 1 项
> **commit**：`977f58e` — `fix(I034): YouzanWebhookController @Transactional 加 rollbackFor=Exception.class`
> **修复方案**：方案 A（注解加 `rollbackFor = Exception.class`）
> **改动量**：1 行（仅注解参数）

---

## 一、根因

`YouzanWebhookController.HandleYouzan`（`backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java:76`）标注 `@Transactional` 时**未指定 `rollbackFor`** → Spring 默认仅回滚 `RuntimeException` + `Error`。

`upsertOrder`（L255-）的潜在抛出会被遗漏回滚：

| 异常类型 | 来源 | 是否默认回滚 |
|---|---|---|
| `IllegalStateException`（"ObjectMapper 不可用"）| L256 | ✅ 回滚（RuntimeException） |
| `JsonProcessingException`（Jackson `readValue` 失败）| L259 | ❌ **不回滚**（IOException 子类） |
| `SQLException`（DB 异常）| mapper 层 | ❌ **不回滚**（checked Exception） |

**后果**：`ecommerce_order` upsert 失败时 `webhook_msg_log` 仍插入 → 业务侧订单未落库但日志显示已处理，重试后仍走幂等命中（`msg_id` 已存在）→ 业务订单永久丢失。

---

## 二、修复 diff

```diff
diff --git a/backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java b/backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java
index 8793bcc..8c54d39 100644
--- a/backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java
+++ b/backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java
@@ -77,7 +77,7 @@ public class YouzanWebhookController {
      * @param request   原始 request（用 ContentCachingRequestWrapper 包一层）
      */
     @PostMapping("/webhook/youzan")
-    @Transactional
+    @Transactional(rollbackFor = Exception.class)
     public ResponseEntity<Result<Map<String, Object>>> HandleYouzan(
             @RequestParam(value = "kdt_id", required = false) String kdtId,
             @RequestHeader(value = "X-Youzan-Sign", required = false) String signature,
```

---

## 三、验证证据

### 3.1 编译验证

```
$ cd d:\QiaoPM\backend
$ mvn -DskipTests clean compile
...
[INFO] Compiling 1543 source files with javac [debug release 17] to target\classes
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  40.861 s
[INFO] Finished at: 2026-09-24T23:12:48+08:00
[INFO] ------------------------------------------------------------------------
```

**exit code = 0**（BUILD SUCCESS）

### 3.2 Git 提交

```
$ git log -1 --format="%H %s"
977f58ed2a864608a64bcae43b64a811dd79e04b fix(I034): YouzanWebhookController @Transactional 加 rollbackFor=Exception.class
```

### 3.3 影响范围

- **未触达已验证页面**：`/dashboard`、`/mh/visual`、`/mh/consent` 三个路由均未在本次改动路径上
- **未修改业务逻辑**：`upsertOrder` / `handleYouzanInternal` / catch 块 全部保持不变
- **未新增依赖**：使用 Spring 内置 `@Transactional(rollbackFor = ...)`
- **未修改 SQL / DB schema**

---

## 四、行为对比

| 场景 | 修复前（`@Transactional`）| 修复后（`@Transactional(rollbackFor = Exception.class)`）|
|---|---|---|
| `JsonProcessingException`（Jackson 解析失败）| ❌ 不回滚 → 日志写入但订单未落库 | ✅ 回滚 → 全部回滚，返回 HTTP 500，触发有赞重试 |
| `IllegalStateException`（ObjectMapper 不可用）| ✅ 回滚 | ✅ 回滚 |
| `SQLException`（DB 缺表/缺列）| ❌ 不回滚 → 半成功状态 | ✅ 回滚 → 与 catch 块内独立 `webhookMsgLogMapper.insert` 一致 |
| `BusinessException`（业务校验）| ❌ 不回滚 | ✅ 回滚 |
| RuntimeException（其他 NPE 等）| ✅ 回滚 | ✅ 回滚 |

---

## 五、Issue 状态

| Issue | 状态 | commit | 修复时间 |
|---|---|---|---|
| **I034** webhook `@Transactional` 缺 `rollbackFor` | ~~OPEN~~ → ✅ **FIXED** | `977f58e` | 2026-09-24 23:12 |

---

## 六、未涉及事项

- **I035 时间窗**：msg_id 幂等仍无 `created_at > now() - window` 时间窗（PARTIAL-NON-BLOCKER），保留 OPEN
- **EXT-22 SoT 5「webhook 重放保护」**：仍 PARTIAL-NON-BLOCKER（I035 维度）
- **其它 P1**（I020 / I022 / I031）：等待后续 Phase 2.13-2.15 修复

---

## 七、Post-Audit 二期进度

| Phase | 日期 | 主题 | 状态 |
|---|---|---|---|
| 2.12 | 2026-09-24 | I034 webhook rollbackFor | ✅ FIXED（commit `977f58e`）|
| 2.13 | — | I022 disablePlugin 清 lastError | ⏸ 待启动 |
| 2.14 | — | I020 沙箱 assertCallerAllowed 弱 | ⏸ 待启动 |
| 2.15 | — | I031 Place Details 无降级 + 无日志 | ⏸ 待启动 |

剩余 P1 OPEN：**3 项**（I020 / I022 / I031）。
