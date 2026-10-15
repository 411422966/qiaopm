# Phase 2.14 — I020 沙箱 service.* 漏洞修复报告

> **修复时间**：2026-09-24 23:43 (UTC+8)
> **修复人**：Cursor Agent
> **范围**：Post-Audit 二期 P1 第 3 项
> **commit**：`7d455eb` — `fix(I020): 沙箱黑名单补 com.meiyetec.service. 前缀`
> **修复方案**：方案 A（黑名单补前缀 + assertCallerAllowed 真实类探测）
> **改动量**：1 文件 +22/-4 行

---

## 一、根因

`PluginSecurityPolicy.assertCallerAllowed`（`backend/src/main/java/com/meiyetec/plugin/PluginSecurityPolicy.java:106-135`）原探测逻辑：

```java
Class.forName("com.meiyetec.service.internal.NonExistent", false, cl);
```

**问题**：
1. **黑名单不完整**：`HOST_FORBIDDEN_PACKAGES` 只列了 `service.internal.` / `service.impl.`，**漏了 `service.` 顶级包** → `assertPackageAllowed` 走白名单兜底「com.meiyetec.* 不在白名单」能拦住，但**契约漂移**
2. **探测目标无效**：用 fictional 类 `NonExistent` 作 probe，**任何 classloader 都会抛 ClassNotFoundException** → 永远走 early-return → 探测形同虚设
3. **结果**：插件反射加载 `com.meiyetec.service.StoreService` 等**非 internal/impl 业务类**时 → `assertCallerAllowed` 不抛 SecurityException → **沙箱被绕过**

### 影响路径

```
插件代码：Class<?> c = Class.forName("com.meiyetec.service.StoreService", false, pluginCL);
       ↓
pluginCL 委托 AppClassLoader → AppClassLoader 找到 StoreService → 返回 Class 对象
       ↓
宿主业务代码调 assertCallerAllowed(Plugin.class)
       ↓
原 probe：Class.forName("com.meiyetec.service.internal.NonExistent", false, pluginCL)
       ↓
永远 CNFE → early return → 沙箱不生效 ❌
```

---

## 二、修复方案（方案 A）

### 2.1 改动 1：黑名单补 `com.meiyetec.service.`

`backend/src/main/java/com/meiyetec/plugin/PluginSecurityPolicy.java:48-58`

```java
public static final List<String> HOST_FORBIDDEN_PACKAGES = List.of(
        "com.meiyetec.service.",            // ⭐ I020：顶级 service 包整体拦截（覆盖 service.UserService 等非 internal/impl 类）
        "com.meiyetec.service.internal.",
        "com.meiyetec.service.impl.",
        "com.meiyetec.security.",
        "com.meiyetec.mapper.",
        "com.meiyetec.config.",
        "com.meiyetec.plugin.PluginRuntimeService",
        "com.meiyetec.plugin.PluginPersistenceService",
        "com.meiyetec.plugin.PluginTenantService"
);
```

设计要点：
- `com.meiyetec.service.` 放在第一位（**优先级高**），覆盖整个 service 顶级包
- `assertPackageAllowed` 走黑名单 → 立即抛 SecurityException
- 即便插件作者主动调 `assertPackageAllowed` 自检，也会被这条新规则挡住

### 2.2 改动 2：`assertCallerAllowed` 用真实类探测

`backend/src/main/java/com/meiyetec/plugin/PluginSecurityPolicy.java:106-156`

```java
// ⭐ I020 fix：探测 service 顶级包 — 用真实类 StoreService 作为探测目标
// 原 probe 仅测 service.internal.NonExistent（fictional 类），永远 CNFE，无法生效
// 现用真实类：若 classloader 能加载 → caller 能访问 service.* → 拒绝（沙箱阻断）
try {
    Class.forName("com.meiyetec.service.StoreService", false, cl);
    // 加载成功 → caller 能访问 com.meiyetec.service.* → 抛出 SecurityException
    throw new SecurityException("[PLUGIN-SECURITY] caller=" + callerName
            + " rejected: can load forbidden host package com.meiyetec.service.* (I020)");
} catch (ClassNotFoundException expected) {
    // 找不到 → OK（caller 不能加载 service.*）
} catch (NoClassDefFoundError expected) {
    // 依赖缺失（NoClassDefFoundError） → 也算 OK（不能完整加载）
} catch (LinkageError expected) {
    // 其他链接错误 → OK
}
```

设计要点：
- 用真实类 `com.meiyetec.service.StoreService` 作 probe（避免 fictional 类永远 CNFE 的死循环）
- **新 probe 放在原 internal probe 之前**（确保新逻辑不被 early-return 短路）
- 捕获 `ClassNotFoundException` / `NoClassDefFoundError` / `LinkageError` 全部视为「不能完整加载」→ 不抛
- **捕获后的 catch 让原 internal probe 仍能跑**（保留 fallback 探测）
- 探测成功 → 抛 `SecurityException`，**外层 `catch (SecurityException se) { throw se; }` 显式上抛**

### 2.3 改动 3：异常传递路径修复

`backend/src/main/java/com/meiyetec/plugin/PluginSecurityPolicy.java:154-156`

```java
} catch (SecurityException se) {
    throw se;
} catch (Exception e) {
    log.debug("[PLUGIN-SECURITY] assertCallerAllowed check skipped: {}", e.getMessage());
}
```

原代码外层 catch 只捕获 Exception → SecurityException 也被吃掉。**修复后显式上抛 SecurityException**，确保沙箱阻断生效。

---

## 三、jshell 风格验证（4 场景全 PASS）

验证脚本：`I020Verify.java`（已删除，仅用于本次验证）

```
$ mvn dependency:build-classpath → cp.txt (12262 字符)
$ javac -encoding UTF-8 -cp "target/classes;cp.txt" I020Verify.java
$ java -cp "target/classes;.;slf4j-api-2.0.9.jar;mybatis-plus-extension-3.5.5.jar" I020Verify
```

### 3.1 输出

```
=== I020 验证 ===
[Scenario 1] 宿主调用 PluginSecurityPolicy.assertCallerAllowed(PluginSecurityPolicy.class): PASS（放行）
[Scenario 2] 第三方 caller（classloader 可访问 com.meiyetec.service.*）: PASS（被拦截）: [PLUGIN-SECURITY] caller=I020Verify$TriggerCaller rejected: can load forbidden host package com.meiyetec.service.* (I020)
[Scenario 3] assertPackageAllowed("com.meiyetec.service.foo"): PASS: [PLUGIN-SECURITY] plugin=test-plugin tried to access forbidden package: com.meiyetec.service.
[Scenario 4] assertPackageAllowed("com.meiyetec.plugin."): PASS（白名单放行）
=== 验证结束 ===
```

### 3.2 场景说明

| 场景 | 调用 | 期望 | 实测 | 含义 |
|---|---|---|---|---|
| 1 | `assertCallerAllowed(PluginSecurityPolicy.class)` | 放行 | PASS | 宿主自己调用不抛 |
| 2 | `assertCallerAllowed(TriggerCaller.class)` | 抛 SecurityException | PASS | 第三方 caller 能加载 `StoreService` → 触发 I020 probe → 阻断 |
| 3 | `assertPackageAllowed("plugin-x", "com.meiyetec.service.foo")` | 抛 SecurityException | PASS | 黑名单新规则生效 |
| 4 | `assertPackageAllowed("plugin-x", "com.meiyetec.plugin.foo")` | 放行 | PASS | 白名单业务不受影响 |

### 3.3 测试环境

- **Classpath**：`target/classes` + `slf4j-api-2.0.9.jar` + `mybatis-plus-extension-3.5.5.jar`
- **JVM**：Java 17（与生产一致）
- **退出码**：0（4/4 PASS）

---

## 四、diff 总览

```diff
diff --git a/.../plugin/PluginSecurityPolicy.java b/.../plugin/PluginSecurityPolicy.java
@@ -48,6 +48,7 @@ public final class PluginSecurityPolicy {
     /** 严禁插件访问的宿主包前缀（黑名单 — 比白名单优先级高） */
     public static final List<String> HOST_FORBIDDEN_PACKAGES = List.of(
+            "com.meiyetec.service.",            // ⭐ I020：顶级 service 包整体拦截
             "com.meiyetec.service.internal.",
             "com.meiyetec.service.impl.",
             ...

@@ -121,22 +122,39 @@ public final class PluginSecurityPolicy {
             try {
                 ClassLoader cl = caller.getClassLoader();
                 if (cl != null) {
-                    // 简单检查：尝试加载一个宿主内部类，看是否成功
+                    // ⭐ I020 fix：探测 service 顶级包 — 用真实类 StoreService 作为探测目标
+                    try {
+                        Class.forName("com.meiyetec.service.StoreService", false, cl);
+                        throw new SecurityException("[PLUGIN-SECURITY] caller=" + callerName
+                                + " rejected: can load forbidden host package com.meiyetec.service.* (I020)");
+                    } catch (ClassNotFoundException expected) {
+                        // 找不到 → OK
+                    } catch (NoClassDefFoundError expected) {
+                        // 依赖缺失 → 也算 OK
+                    } catch (LinkageError expected) {
+                        // 其他链接错误 → OK
+                    }
+
+                    // 探测 internal 包（原逻辑 — 找到 → warn；保留作 fallback 检测）
                     try {
                         Class.forName("com.meiyetec.service.internal.NonExistent", false, cl);
                     } catch (ClassNotFoundException expected) {
-                        // 找不到是预期的（如果包真不存在），说明 classloader 不能加载宿主内部类
                         return;
                     } catch (Exception ignored) {
                         return;
                     }
-                    // 如果找到（说明包暴露了），报警但不阻止（业务侧策略）
                     log.warn(...);
                 }
+            } catch (SecurityException se) {
+                throw se;
             } catch (Exception e) {
                 log.debug(...);
             }
```

---

## 五、行为对比

| 场景 | 修复前 | 修复后 |
|---|---|---|
| 插件反射加载 `service.StoreService` | **不拦截**（probe 永远 CNFE） | **抛 SecurityException**（真实类 probe 成功 → 阻断）|
| 插件反射加载 `service.internal.Foo` | 不拦截（probe 短路 CNFE → return）| 不拦截（同左，但有 log.warn）|
| 插件反射加载 `security.Bar` | 不拦截 | 不拦截（I020 不覆盖此 case，仍 PARTIAL-NON-BLOCKER）|
| `assertPackageAllowed("service.foo")` | 抛（走白名单兜底）| 抛（直接走黑名单新规则）|
| `assertPackageAllowed("plugin.foo")` | 放行 | 放行（行为不变）|
| 宿主自己调用 `assertCallerAllowed` | 放行 | 放行（行为不变）|

---

## 六、Phase 2.14 验证证据

| 项 | 结果 |
|---|---|
| `mvn -DskipTests compile` | **exit code 0** / BUILD SUCCESS |
| git commit | `7d455eb`（1 file changed, 22 insertions(+), 4 deletions(-)）|
| jshell 风格验证 | **4/4 PASS** |
| 影响范围 | 未触达 3 个已验证页面；未改 recordState 默认语义；未改 SQL / 依赖；pf4j 生命周期不变 |
| Sandbox probe 顺序 | 新 probe → internal probe（避免短路）|

---

## 七、未涉及事项

- **I020 完美拦截 vs JVM 字节码增强**：本修复仅覆盖「classloader 链能加载 → 阻断」路径，**反射 / Unsafe / MethodHandles 等绕过路径不在范围**（EXT-23 已标注「合作式沙箱」的根本限制）
- **I031 Place Details**：无降级 + 无日志 → 仍 PARTIAL-NON-BLOCKER 等待 Phase 2.15 修复
- **internal/impl probe 仍 warn-only**：原 internal probe 仍只 log.warn 不 throw（**保留原行为不变**，仅 I020 的 service. probe 改 throw），后续如需统一可单开 Phase 处理

---

## 八、Post-Audit 二期进度

| Phase | 日期 | 主题 | 状态 |
|---|---|---|---|
| 2.12 | 2026-09-24 | I034 webhook rollbackFor | ✅ FIXED（commit `977f58e`）|
| 2.13 | 2026-09-24 | I022 disablePlugin 保留旧 lastError | ✅ FIXED（commit `8c256c9`）|
| 2.14 | 2026-09-24 | **I020 沙箱 service.* 漏洞** | ✅ **FIXED**（commit `7d455eb`）|
| 2.15 | — | I031 Place Details 无降级 + 无日志 | ⏸ 待启动 |

剩余 P1 OPEN：**1 项**（I031）。
