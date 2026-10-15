# F042 — Plugin 沙箱 `assertCallerAllowed` 反射探测（静态 + jshell 实证）

> **验证时间**：2026-09-13 20:41 (UTC+8)
> **Phase**：5B — 静态分析 + jshell 实证
> **验证方式**：直接调用 `PluginSecurityPolicy.assertCallerAllowed` + I020 合作式沙箱逻辑评审
> **最终状态**：**PARTIAL-NON-BLOCKER**（静态 + 行为路径分支确认；运行时 I020 实证受限）

---

## 一、源码定位

`PluginSecurityPolicy.assertCallerAllowed`：`PluginSecurityPolicy.java:122-148`

```java
public static void assertCallerAllowed(Class<?> caller) {
    if (caller == null) return;
    String callerName = caller.getName();
    if (!callerName.startsWith("com.meiyetec.") && !callerName.startsWith("org.pf4j.")) {
        // 第三方/插件调用方分支
        try {
            ClassLoader cl = caller.getClassLoader();
            if (cl != null) {
                try {
                    Class.forName("com.meiyetec.service.internal.NonExistent", false, cl);
                } catch (ClassNotFoundException expected) {
                    return; // OK：classloader 找不到宿主内部类
                } catch (Exception ignored) {
                    return; // OK：SecurityException/NoClassDefFoundError
                }
                log.warn("[PLUGIN-SECURITY] caller {} can load host internal classes; verify plugin trust level", callerName);
            }
        } catch (Exception e) {
            log.debug("[PLUGIN-SECURITY] assertCallerAllowed check skipped: {}", e.getMessage());
        }
    }
}
```

## 二、行为分支判定

### 2.1 jshell 实证（2 个 Case）

| # | Caller | 预期分支 | 实际 |
|---|---|---|---|
| 1 | `java.lang.String` | 不在 `com.meiyetec.*` 或 `org.pf4j.*` → 走检查分支（Class.forName 抛 ClassNotFoundException → return）| ✅ OK |
| 2 | `PluginSecurityPolicy` | 在 `com.meiyetec.*` → 直接 return（不检查）| ✅ OK |

### 2.2 I020 合作式沙箱 — 静态判定

**关键发现**：`assertCallerAllowed` 只对调用方（caller）的 classloader 做"是否能加载宿主内部类"的探测。但探测的 class 是 `com.meiyetec.service.internal.NonExistent`（永远不存在），所以：

- 如果 caller 的 classloader 是主应用 classpath → `Class.forName` 找不到 → return OK
- 如果 caller 的 classloader 是 plugin classloader → 同样找不到 → return OK
- 实际**没有任何 classpath 配置能让 `NonExistent` 被找到** → 分支内的 `log.warn` 永远不会触发

**I020 合作式沙箱实证**：

| 调用模式 | 是否被拦截 |
|---|---|
| plugin 调用 `PluginSecurityPolicy.assertPackageAllowed("code", "com.meiyetec.service.UserService")` | ✅ 拦截（Case 12） |
| plugin 调用 `Class.forName("com.meiyetec.service.UserService", false, pluginClassLoader)` | ❌ 不拦截（plugin 没调 assertPackageAllowed） |
| plugin 调用 `Class.forName("com.meiyetec.service.internal.NonExistent", false, pluginClassLoader)` | ❌ 不拦截（NonExistent 永远找不到） |

**结论**：合作式沙箱的本质——**插件作者主动调用 `assertPackageAllowed` 才有效**。直接反射加载 `com.meiyetec.service.*`（非 internal）不被拦截。

### 2.3 I025 孤儿代码 实证

`PluginSecurityPolicy.deniedPackages()`（`PluginSecurityPolicy.java:150-160`）：

```java
public static Set<String> deniedPackages() {
    return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "java.lang.reflect.*",
        "sun.misc.*",
        "com.meiyetec.service.internal.*",
        "com.meiyetec.security.*"
    )));
}
```

**jshell 输出**：`deniedPackages() = [com.meiyetec.service.internal.*, sun.misc.*, java.lang.reflect.*, com.meiyetec.security.*]`

**grep 验证**：`grep -r "deniedPackages" backend/src/main` 仅 1 命中（即定义本身，0 调用方）→ **孤儿代码实证确认**（I025）。

## 三、运行时受限制说明

| 步骤 | 实证内容 | 受限原因 |
|---|---|---|
| demo-hello onStart 调 assertCallerAllowed(getClass()) | 实际行为 | demo-hello 未编译 → 无法实测 |
| plugin 反射加载 `service.UserService` 不被拦 | I020 | 需要 demo-hello 编译 + 真实 plugin ClassLoader → 受限 |

但**静态判定已足够确认沙箱行为**：
- `assertCallerAllowed` 只探测 `NonExistent` 类，**实际上永远找不到** → 分支逻辑"防君子不防小人"
- 真正的拦截依赖 `assertPackageAllowed`（由 plugin 主动调用）

## 四、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| 调用方分支判定正确 | ✅ VERIFIED | 2 Case jshell 实证 |
| I020 合作式沙箱 | ✅ VERIFIED | 静态判定 + F041 Case 11/12 旁证 |
| I025 `deniedPackages` 孤儿 | ✅ VERIFIED | grep 0 调用 |
| 运行时反射加载 `service.*` | ⚠️ 受限 | demo-hello 未编译，无法实测 |

### 最终状态

**F042 = PARTIAL-NON-BLOCKER**（静态 + 行为路径分支确认，运行时受限链阻断）

---

## 五、Evidence 文件

| 文件 | 路径 |
|---|---|
| 源码 | `backend/src/main/java/com/meiyetec/plugin/PluginSecurityPolicy.java:122-160` |
| 测试脚本 | `audit/runtime/probe-5b-sandbox.jsh` |
| I025 实证 | grep 输出 |

---

**F042 静态判定稳健。运行时受 demo-hello 编译限制。**
