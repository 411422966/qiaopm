# F041 — Plugin 沙箱 `assertPackageAllowed` 自检（jshell 实证）

> **验证时间**：2026-09-13 20:41 (UTC+8)
> **Phase**：5B — jshell 实证（不依赖 demo-hello 编译）
> **验证方式**：直接调用 `PluginSecurityPolicy.assertPackageAllowed` 静态方法
> **最终状态**：**UNVERIFIED → 升级为 PARTIAL-NON-BLOCKER**（14 Case 全部符合预期）

---

## 一、jshell 实证

### 1.1 测试脚本：`audit/runtime/probe-5b-sandbox.jsh`

```
import com.meiyetec.plugin.PluginSecurityPolicy;
... // 14 个 Case，见下表
/exit
```

### 1.2 14 个 Case 实证结果

| # | pkgPrefix | 预期 | 实际 |
|---|---|---|---|
| 1 | `com.meiyetec.plugin.` | 通过（白名单） | ✅ OK 通过 |
| 2 | `com.meiyetec.util.` | 通过（白名单） | ✅ OK 通过 |
| 3 | `com.meiyetec.exception.` | 通过（白名单） | ✅ OK 通过 |
| 4 | `com.meiyetec.service.internal.` | 抛 SecurityException | ✅ 抛：`tried to access forbidden package: com.meiyetec.service.internal.` |
| 5 | `com.meiyetec.service.impl.` | 抛 SecurityException | ✅ 抛：`tried to access forbidden package: com.meiyetec.service.impl.` |
| 6 | `com.meiyetec.security.` | 抛 SecurityException | ✅ 抛：`tried to access forbidden package: com.meiyetec.security.` |
| 7 | `com.meiyetec.mapper.` | 抛 SecurityException | ✅ 抛：`tried to access forbidden package: com.meiyetec.mapper.` |
| 8 | `com.meiyetec.config.` | 抛 SecurityException | ✅ 抛：`tried to access forbidden package: com.meiyetec.config.` |
| 9 | `java.lang.reflect.` | 抛 SecurityException | ✅ 抛：`tried to use forbidden JDK API: java.lang.reflect.` |
| 10 | `java.lang.Runtime` | 抛 SecurityException | ✅ 抛：`tried to use forbidden JDK API: java.lang.Runtime` |
| 11 | `com.example.demoplugin.`（第三方）| 通过（合作式放行）| ✅ OK 放行 |
| 12 | `com.meiyetec.service.`（非 internal/impl）| 抛 SecurityException（不在白名单）| ✅ 抛：`host package not in whitelist: com.meiyetec.service.` |
| 13 | `null` | 通过（early return）| ✅ OK 通过 |
| 14 | `""`（空字符串）| 通过（early return）| ✅ OK 通过 |

### 1.3 关键发现

1. **黑名单优先于白名单**：验证通过 Case 4-10
2. **白名单仅对 `com.meiyetec.*` 生效**：Case 11（第三方包）放行 → 合作式
3. **`com.meiyetec.service.` 抛 SecurityException**（不在白名单也不在黑名单）：但**plugin 不调用 assertPackageAllowed 就不会被拦截** → I020 合作式沙箱
4. **`com.meiyetec.service.internal.` 抛 SecurityException**（黑名单优先）：无论是否调用都拦

## 二、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| 白名单生效 | ✅ VERIFIED | Case 1-3 通过 |
| 宿主黑名单生效 | ✅ VERIFIED | Case 4-8 抛 SecurityException |
| JDK 黑名单生效 | ✅ VERIFIED | Case 9-10 抛 SecurityException |
| 黑名单优先 | ✅ VERIFIED | Case 12 `service.` 抛（不在白名单），但 `service.internal.` 抛是黑名单优先 |
| 合作式放行（第三方）| ✅ VERIFIED | Case 11 第三方包放行 |
| 边界处理 | ✅ VERIFIED | Case 13/14 null/空字符串 early return |

### 最终状态

**F041 = PARTIAL-NON-BLOCKER**（14 Case 全部实证通过，行为符合 SoT 文档描述）

> 之前 Phase 3 标 UNVERIFIED（demo-hello 未编译），Phase 5B 经 jshell 直接调用类静态方法绕过 demo-hello 编译依赖，14 个 Case 全部确认。

---

## 三、Evidence 文件

| 文件 | 路径 |
|---|---|
| 源码 | `backend/src/main/java/com/meiyetec/plugin/PluginSecurityPolicy.java:78-120` |
| 测试脚本 | `audit/runtime/probe-5b-sandbox.jsh` |

---

**F041 已升级。assertPackageAllowed 静态行为完整验证。**
