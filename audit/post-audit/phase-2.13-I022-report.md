# Phase 2.13 — I022 disablePlugin 清 lastError 修复报告

> **修复时间**：2026-09-24 23:21 (UTC+8)
> **修复人**：Cursor Agent
> **范围**：Post-Audit 二期 P1 第 2 项
> **commit**：`8c256c9` — `fix(I022): disablePlugin 保留旧 lastError 不覆写`
> **修复方案**：方案 B（disablePlugin 前查旧 lastError，条件覆写）
> **改动量**：2 文件 +32 行（1 个 helper 方法 +13 行 disablePlugin 内联逻辑 + 1 行注释 + 1 行变更）

---

## 一、根因

`PluginPersistenceService.recordState`（`backend/src/main/java/com/meiyetec/plugin/PluginPersistenceService.java:60`）写入语义：

> `lastError` **始终刷新**（即使是 null 也要清空，让"成功"覆盖"失败"）

`PluginRuntimeService.disablePlugin`（`plugin/PluginRuntimeService.java:178-191`）调 `recordState` 时，`info.getErrorMessage()` 在 disable 场景下为 **null**（无运行时错误）→ 上一次 `FAILED` 状态的 `lastError` 被覆盖成 null → **审计线索丢失**。

### 影响路径

```
插件启动失败 → recordState 写 state=FAILED + errorMsg="NoClassDefFoundError: ..."
          ↓
运维手动禁用插件 → disablePlugin → info.getErrorMessage() = null
          ↓
recordState(..., state=DISABLED, errorMsg=null) → state 表里 lastError 被清空
          ↓
dashboard "lastKnownFailed" 计数因 lastError 为空而无法溯源
```

---

## 二、修复方案（确认采纳 B）

### 2.1 改动 1：新增 `PluginPersistenceService.getLastError`

`backend/src/main/java/com/meiyetec/plugin/PluginPersistenceService.java:76-94`

```java
/**
 * 取某租户某插件的 lastError（无记录 → null）。
 * <p>用于：disablePlugin 路径保留旧错误（I022）— 防止把 FAILED 错误被空值清掉。
 */
public String getLastError(Long tenantId, String pluginCode) {
    if (pluginCode == null || pluginCode.isEmpty()) return null;
    try {
        PluginRuntimeState existing = stateMapper.selectOne(new LambdaQueryWrapper<PluginRuntimeState>()
                .eq(PluginRuntimeState::getTenantId, tenantId == null ? 0L : tenantId)
                .eq(PluginRuntimeState::getPluginCode, pluginCode)
                .last("LIMIT 1"));
        return existing == null ? null : existing.getLastError();
    } catch (Exception e) {
        log.warn("[PLUGIN-PERSIST] getLastError failed: tenant={} plugin={} err={}",
                tenantId, pluginCode, e.getMessage());
        return null;
    }
}
```

设计要点：
- 异常仅 warn 不抛（与 `recordState` 保持一致，避免持久化层故障传播到业务主流程）
- `tenantId=null` → 平台级（与 `recordState` 同语义）
- 无记录 → 返回 null（首次 disable 无旧错误，传给 recordState 走默认清空语义）

### 2.2 改动 2：`disablePlugin` 条件覆写

`backend/src/main/java/com/meiyetec/plugin/PluginRuntimeService.java:178-220`

```java
PluginRuntimeInfo info = toInfo(pm.getPlugin(pluginId));
// ⭐ I022 fix：disable 时若旧 lastError 非空，保留旧错误（避免覆盖 FAILED 错误的审计线索）
// 仅在新 state=DISABLED 时生效；其他状态保持 recordState 默认语义
String preservedError = info.getErrorMessage();
if ("DISABLED".equals(info.getState())) {
    String oldError = persistenceService.getLastError(0L, pluginCode);
    if (oldError != null && !oldError.isEmpty()) {
        preservedError = oldError;
        log.info("[PLUGIN] disablePlugin 保留旧 lastError（I022 审计保留）：pluginCode={} oldError={}",
                pluginCode, oldError);
    }
}
persistenceService.recordState(0L, pluginCode, info.getVersion(), info.getPluginId(),
        info.getState(), preservedError);
return info;
```

设计要点：
- **仅 DISABLED 路径触发** — 其他状态（STARTED/CREATED/FAILED/STOPPED/NOT_FOUND/RUNTIME_DISABLED）保持 recordState 默认语义不变
- **首次 disable**（无旧记录）→ `oldError=null` → `preservedError = info.getErrorMessage() = null` → 行为与修复前完全一致
- **disable after FAILED**（旧 lastError 非空）→ `preservedError = oldError` → FAILED 错误被保留
- **disable after STARTED**（旧 lastError 通常为 null）→ `preservedError = null` → 与修复前一致

---

## 三、diff 总览

```diff
diff --git a/.../plugin/PluginPersistenceService.java b/.../plugin/PluginPersistenceService.java
+    /**
+     * 取某租户某插件的 lastError（无记录 → null）。
+     * <p>用于：disablePlugin 路径保留旧错误（I022）...
+     */
+    public String getLastError(Long tenantId, String pluginCode) {
+        if (pluginCode == null || pluginCode.isEmpty()) return null;
+        try {
+            PluginRuntimeState existing = stateMapper.selectOne(new LambdaQueryWrapper<PluginRuntimeState>()
+                    .eq(PluginRuntimeState::getTenantId, tenantId == null ? 0L : tenantId)
+                    .eq(PluginRuntimeState::getPluginCode, pluginCode)
+                    .last("LIMIT 1"));
+            return existing == null ? null : existing.getLastError();
+        } catch (Exception e) {
+            log.warn("[PLUGIN-PERSIST] getLastError failed: ...");
+            return null;
+        }
+    }
```

```diff
diff --git a/.../plugin/PluginRuntimeService.java b/.../plugin/PluginRuntimeService.java
         PluginRuntimeInfo info = toInfo(pm.getPlugin(pluginId));
+        // ⭐ I022 fix：disable 时若旧 lastError 非空，保留旧错误
+        // 仅在新 state=DISABLED 时生效；其他状态保持 recordState 默认语义
+        String preservedError = info.getErrorMessage();
+        if ("DISABLED".equals(info.getState())) {
+            String oldError = persistenceService.getLastError(0L, pluginCode);
+            if (oldError != null && !oldError.isEmpty()) {
+                preservedError = oldError;
+                log.info("[PLUGIN] disablePlugin 保留旧 lastError（I022 审计保留）...");
+            }
+        }
         persistenceService.recordState(0L, pluginCode, info.getVersion(), info.getPluginId(),
-                info.getState(), info.getErrorMessage());
+                info.getState(), preservedError);
         return info;
```

---

## 四、验证证据

### 4.1 编译验证

```
$ cd d:\QiaoPM\backend
$ mvn -DskipTests clean compile
[INFO] Compiling 1543 source files with javac [debug release 17] to target\classes
...
[INFO] BUILD SUCCESS
[INFO] Total time:  42.153 s
[INFO] Finished at: 2026-09-24T23:20:59+08:00
```

**exit code = 0**（BUILD SUCCESS）

### 4.2 Git 提交

```
$ git log -1 --format="%H %s"
8c256c9e31292c7f1bcd40c7c1a66c499028d11c fix(I022): disablePlugin 保留旧 lastError 不覆写
```

```
$ git diff --stat HEAD~1 HEAD
backend/src/main/java/com/meiyetec/plugin/PluginPersistenceService.java | 19 +++++++++++++++++++
backend/src/main/java/com/meiyetec/plugin/PluginRuntimeService.java     | 13 +++++++++++-
2 files changed, 31 insertions(+), 1 deletion(-)
```

### 4.3 影响范围

- **未触达已验证页面**：`/dashboard`、`/mh/visual`、`/mh/consent` 三个路由均不在本次改动路径上
- **未修改 recordState 默认语义**：其余调用方（`enablePlugin` / `loadPlugin` / `run()` 启动期 autoload 等）的语义完全保持
- **未新增 SQL / DB schema**：`plugin_runtime_state` 表结构不变
- **未新增依赖**
- **PF4j PluginManager 生命周期** 完全不变（仅多读一次 query，不改 state）

---

## 五、行为对比（按运行时状态分支）

| 场景 | 修复前 `lastError` | 修复后 `lastError` | 备注 |
|---|---|---|---|
| 首次 disable（无历史记录）| null | null | 行为不变 |
| disable after **STARTED** | null → null | null | 无 FAILED 错误，行为不变 |
| disable after **FAILED** | **null（**覆盖丢失**）** | **保留原 FAILED 错误** | ✅ 审计线索保留 |
| disable after **STOPPED** | null → null | null | 通常无错误，行为不变 |
| disable after **DISABLED** | null → null | null（没有旧错误）或旧错误（若有）| ⚠️ 若 FAILED 后因任何原因变 STOPPED → 本规则兜底 |
| enable 路径（含 start 失败→record FAILED→ 后续 enable） | recordState 默认刷 | recordState 默认刷 | 行为不变（仅 DISABLED 路径受影响）|

---

## 六、未涉及事项

- **I020 沙箱**：asssertCallerAllowed 仅探测 `internal.NonExistent`，service.* 实际不拦截 → 仍 PARTIAL-NON-BLOCKER 等待 Phase 2.14 修复
- **I031 Place Details**：无降级 + 无日志 → 仍 PARTIAL-NON-BLOCKER 等待 Phase 2.15 修复
- **EXT-23 EXT-SoT**：仅 recordState 写入语义文档「成功覆盖失败」需补充「DISABLED 路径除外」一句话注释（建议在 recordState 文档头加一行 cross-ref `disablePlugin` 行为例外）

---

## 七、Post-Audit 二期进度

| Phase | 日期 | 主题 | 状态 |
|---|---|---|---|
| 2.12 | 2026-09-24 | I034 webhook rollbackFor | ✅ FIXED（commit `977f58e`）|
| 2.13 | 2026-09-24 | **I022 disablePlugin 保留旧 lastError** | ✅ **FIXED**（commit `8c256c9`）|
| 2.14 | — | I020 沙箱 assertCallerAllowed 弱 | ⏸ 待启动 |
| 2.15 | — | I031 Place Details 无降级 + 无日志 | ⏸ 待启动 |

剩余 P1 OPEN：**2 项**（I020 / I031）。
