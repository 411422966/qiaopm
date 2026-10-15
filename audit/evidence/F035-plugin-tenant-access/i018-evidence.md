# F035 — `isEnabledForTenant` 业务入口 + I018 CONFLICT 实证

> **验证时间**：2026-09-13 20:38 (UTC+8)
> **Phase**：5B — Runtime Verification（API + 静态 + CONFLICT 实证）
> **验证方式**：跨租户访问测试（curl）+ 静态代码复核 + DB 实证
> **最终状态**：**CONFLICT**（I018 SoT 漂移 实证确认，行为正确但文档错）

---

## 一、I018 CONFLICT 实证 — `assertTenantAccess` 实际行为

### 1.1 实证目的

SoT 文档（EXT-23 Operational Notes §5）原文：
> ⚠️ `PluginRuntimeController` **未加租户隔离校验**（直接 `@PathVariable Long tenantId`），平台超管 + 任何租户都能调。

**问题**：SoT 声称"任何租户能调任何 tenantId 的接口"，但 `PluginRuntimeController.assertTenantAccess()`（`PluginRuntimeController.java:58-69`）实际已实现 `dataScope=ALL || currentTenantId==pathTenantId` 校验。

**本节目标**：用真实 token 跨租户调用，确认实际行为（403 vs 200）。

### 1.2 测试 Token 与角色

| Token | Username | userId | tenantId | 角色 | dataScope |
|---|---|---|---|---|---|
| `$adminToken` | `admin` | 1 | 1 | SUPER_ADMIN + ADMIN | **ALL** |
| `$clerkToken` | `clerk01` | 900003 | 1 | CLERK | **STORE** |

### 1.3 跨租户访问实测结果

| # | 角色 | 端点 | tid | 预期 | 实际 |
|---|---|---|---|---|---|
| 1 | clerk01 (STORE) | `GET /plugin/runtime/tenant/2/list` | 2（跨租户）| 403 | **403 ✅**{"code":403,"message":"权限不足：跨租户访问被拒绝"} |
| 2 | clerk01 (STORE) | `POST /plugin/runtime/tenant/2/demo-hello/enable` | 2 | 403 | **403 ✅** 同上 |
| 3 | clerk01 (STORE) | `POST /plugin/runtime/tenant/2/demo-hello/disable` | 2 | 403 | **403 ✅** 同上 |
| 4 | clerk01 (STORE) | `GET /plugin/runtime/persisted?tenantId=2` | 2 | 403 | **403 ✅** 同上 |
| 5 | clerk01 (STORE) | `GET /plugin/runtime/tenant/1/list` | 1（同租户）| 200 | **200 ✅**{"code":200,"data":[]} |
| 6 | admin (ALL) | `GET /plugin/runtime/tenant/2/list` | 2 | 200 | **200 ✅**{"code":200,"data":[]} |
| 7 | admin (ALL) | `GET /plugin/runtime/persisted?tenantId=2` | 2 | 200 | **200 ✅**{"code":200,"data":[]} |
| 8 | admin (ALL) | `GET /plugin/runtime/tenant/999/list` | 999 | 200 | **200 ✅**{"code":200,"data":[]} |

### 1.4 实证结论

**`assertTenantAccess` 实际工作正常**：
- ✅ 普通租户（STORE）跨租户访问 → **全部 403**（4/4）
- ✅ 普通租户同租户访问 → **200**
- ✅ 平台超管（ALL）跨租户访问 → **全部 200**（3/3）

**SoT vs 代码对比**：

| 维度 | SoT（EXT-23 Operational Notes §5）| 实际代码 |
|---|---|---|
| 跨租户访问校验 | "未加租户隔离校验" | ✅ 已实现（`PluginRuntimeController.java:58-69`）|
| 错误消息 | "任何租户都能调" | "跨租户访问被拒绝"（BizCode.FORBIDDEN）|

**判定**：
- 代码行为**正确**（租户隔离已生效）
- SoT 文档描述**错误**（断言"未加校验"与事实不符）
- **I018 CONFLICT 证实**：保留 CONFLICT 标记，**不改业务代码**（D8 决策），但应在 Post-Audit 阶段同步更新 `.cursor/rules/ext-23-plugin-runtime.mdc`（不在本次审计范围）

### 1.5 Evidence 文件

- 完整测试脚本：`audit/runtime/probe-5b-i018.ps1`
- 原始响应 JSON：`audit/runtime/probe-5b-i018.json`
- 控制器源码：`backend/src/main/java/com/meiyetec/controller/PluginRuntimeController.java:58-69`

---

## 二、`isEnabledForTenant` 业务入口（F035 主链）

### 2.1 代码路径

`PluginRuntimeService.isEnabledForTenant`（`PluginRuntimeService.java:256-259`）：
```java
public boolean isEnabledForTenant(String code, Long tenantId) {
    return tenantService.isEnabledForTenant(code, tenantId);
}
```

委派至 `PluginTenantService.isEnabledForTenant`（`plugin/PluginTenantService.java:43-66`）：
```java
public boolean isEnabledForTenant(String pluginCode, Long tenantId) {
    PluginWrapper wrapper = pluginManager.getPlugin(pluginCode);
    if (wrapper == null || wrapper.getPluginState() != PluginState.STARTED) return false;
    PluginInstall install = installMapper.selectOne(
        new LambdaQueryWrapper<PluginInstall>()
            .eq(PluginInstall::getPluginCode, pluginCode)
            .eq(PluginInstall::getTenantId, tenantId));
    if (install == null) {
        // 回退查平台级（tenantId=0）
        PluginInstall platformInstall = installMapper.selectOne(
            new LambdaQueryWrapper<PluginInstall>()
                .eq(PluginInstall::getPluginCode, pluginCode)
                .eq(PluginInstall::getTenantId, 0L));
        if (platformInstall != null) {
            return platformInstall.getEnabled() != null && platformInstall.getEnabled() == 1;
        }
        return true;  // 兜底默认启用 — I023 幽灵 NPE 风险
    }
    return install.getEnabled() != null && install.getEnabled() == 1;
}
```

### 2.2 运行时受限制说明

**Phase 5B 受限链**：
- `pluginManager.getPlugin("xxx")` → pf4j 0 插件 → `wrapper == null` → 返回 `false`
- 业务入口 `isEnabledForTenant` 调用 → 实际**直接返回 false**（不会 NPE，因为前置 null check）

**I023 幽灵 NPE 风险**（来自 SoT §5）：
- 场景：marketplace 安装 → `install` 记录写入 → **不调 runtime load** → pf4j `getPlugin` 返回 null
- `isEnabledForTenant` 三层判断：
  - ① pf4j `wrapper == null` → 返回 **false**（不会到业务入口）
- 所以 I023 风险在 `isEnabledForTenant` 层被**阻断**，业务入口不会触发 NPE
- **但**：SoT 文档说"无任何 plugin_install 记录 → 默认启用（保持历史兼容）"，只有当 pf4j 已加载且无 install 记录才走默认 true — 这条路径在当前环境下**不能触发**（pf4j 0 插件）

### 2.3 Phase 5B 不可实测原因

| 步骤 | 实证内容 | 受限原因 |
|---|---|---|
| install 后不 load | `isEnabledForTenant` 返回 true | pf4j 0 插件 → wrapper null → 直接返回 false（不进入 I023 路径）|
| 业务入口 NPE | 反射调 `wrapper.getPlugin().hello("x")` | pf4j 0 插件 → 无 wrapper 可获取 |

### 2.4 静态判定

| 维度 | 状态 | 备注 |
|---|---|---|
| `isEnabledForTenant` 代码完整 | ✅ L0 静态可达 | `PluginTenantService.java:43-66` 全部 L0 命中 |
| 三层 fallback 逻辑 | ✅ 正确实现 | pf4j STARTED → install 记录 → 平台级 → 默认 true |
| 跨租户访问拦截（I018）| ✅ 实证确认 | 见 §一（clerk01 跨租户全部 403）|
| 幽灵 NPE 风险（I023）| ⚠️ 静态识别 + 受限链阻断 | 运行时无法实测，但静态代码已阻断 |

### 2.5 最终判定

**F035 = CONFLICT**
- I018 SoT vs 代码漂移：**实证确认代码正确、SoT 错误**
- I023 幽灵 NPE：**静态识别 + 当前环境不可触发（pf4j 0 插件阻断）**
- 运行时受 pf4j 0 插件限制，无法实测业务入口真值

---

## 三、Evidence 文件清单

| 文件 | 路径 |
|---|---|
| 测试脚本 | `audit/runtime/probe-5b-i018.ps1` |
| 原始响应 | `audit/runtime/probe-5b-i018.json` |
| 控制器源码 | `backend/src/main/java/com/meiyetec/controller/PluginRuntimeController.java` |
| Service 源码 | `backend/src/main/java/com/meiyetec/plugin/PluginRuntimeService.java` |
| Tenant Service 源码 | `backend/src/main/java/com/meiyetec/plugin/PluginTenantService.java` |

---

**F035 不依赖运行时（除跨租户访问）可独立判定 CONFLICT。I018 实证完成。**
