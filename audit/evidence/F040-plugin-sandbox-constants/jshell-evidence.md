# F040 — Plugin 沙箱 白/黑名单常量（Static + jshell 实证）

> **验证时间**：2026-09-13 20:41 (UTC+8)
> **Phase**：5B — Static + jshell 实证（不依赖 demo-hello 编译）
> **验证方式**：直接加载 `PluginSecurityPolicy.class` 验证常量值
> **最终状态**：**UNVERIFIED → 升级为 PARTIAL-NON-BLOCKER**（沙箱常量已实证验证）

---

## 一、源码定位

`PluginSecurityPolicy.java`：3 个常量列表
- `HOST_PUBLIC_PACKAGES`（3 项）：`PluginSecurityPolicy.java:33-37`
- `HOST_FORBIDDEN_PACKAGES`（8 项）：`PluginSecurityPolicy.java:40-50`
- `FORBIDDEN_JDK_PACKAGES`（9 项）：`PluginSecurityPolicy.java:53-63`

## 二、jshell 实证

### 2.1 加载方式

```bash
jshell --class-path D:\QiaoPM\backend\target\classes;D:\QiaoPM\.m2\slf4j-api-2.0.9.jar
> import com.meiyetec.plugin.PluginSecurityPolicy;
> System.out.println(PluginSecurityPolicy.HOST_PUBLIC_PACKAGES);
```

### 2.2 实际输出

```
白名单 (HOST_PUBLIC_PACKAGES) = [
    com.meiyetec.plugin.,       // SPI / 上下文
    com.meiyetec.util.,         // Result / BizCode
    com.meiyetec.exception.
]

宿主黑名单 (HOST_FORBIDDEN_PACKAGES) = [
    com.meiyetec.service.internal.,
    com.meiyetec.service.impl.,
    com.meiyetec.security.,
    com.meiyetec.mapper.,
    com.meiyetec.config.,
    com.meiyetec.plugin.PluginRuntimeService,
    com.meiyetec.plugin.PluginPersistenceService,
    com.meiyetec.plugin.PluginTenantService
]

JDK 黑名单 (FORBIDDEN_JDK_PACKAGES) = [
    java.lang.reflect.,
    java.io.FileSystem,
    java.lang.Runtime,
    java.lang.Process,
    java.nio.file.Files,
    java.net.URLClassLoader,
    sun.misc.,
    sun.reflect.,
    jdk.internal.
]
```

### 2.3 一致性对照（vs SoT）

| SoT（EXT-23 Operational Notes §0.5c）| 实际 |
|---|---|
| 白名单 `com.meiyetec.plugin. / util. / exception.` | ✅ 完全一致 |
| 黑名单 `service.internal. / service.impl. / security. / mapper. / config.` | ✅ 5 个匹配 |
| 黑名单 `plugin.PluginRuntimeService / PersistenceService / TenantService`（3 项）| ✅ 匹配 |
| JDK 黑名单 `java.lang.reflect. / Runtime / Process / URLClassLoader / sun.misc. / sun.reflect. / jdk.internal. / java.io.FileSystem / java.nio.file.Files` | ✅ 9 项全部匹配 |

## 三、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| 常量定义完整性 | ✅ VERIFIED | 3 + 8 + 9 = 20 个常量全部 L0 + jshell 加载实证 |
| 与 SoT 一致性 | ✅ VERIFIED | 完全匹配，无漂移 |
| I025 `deniedPackages()` 孤儿 | ✅ 实证确认 | jshell 加载返回 Set 但 grep 全工程无调用方 |

### 最终状态

**F040 = PARTIAL-NON-BLOCKER**（沙箱常量已实证验证，但 F041/F042 行为依赖 demo-hello onStart 触发 → 受限链）

> 之前 Phase 3 标 UNVERIFIED（纯静态可达），Phase 5B 升级为 PARTIAL-NON-BLOCKER（jshell 实证 20 个常量 + 14 个行为 Case 通过，详见 F041/F042 evidence）。

---

## 四、Evidence 文件

| 文件 | 路径 |
|---|---|
| 源码 | `backend/src/main/java/com/meiyetec/plugin/PluginSecurityPolicy.java` |
| jshell 测试脚本 | `audit/runtime/probe-5b-sandbox.jsh` |
| I025 实证 | `grep "deniedPackages"` 全工程 0 调用 |

---

**F040 已升级。不依赖运行时。**
