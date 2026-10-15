# F038 — Plugin 租户叠加 pf4j（静态 + API 实证）

> **验证时间**：2026-09-13 20:46 (UTC+8)
> **Phase**：5B — 静态分析 + API 实证
> **验证方式**：读源码 + F039 实证数据 + I023 受限说明
> **最终状态**：**PARTIAL-NON-BLOCKER**（静态完整 + 运行时受 pf4j 0 插件限制）

---

## 一、源码定位

`PluginTenantService.isEnabledForTenant(pluginCode, tenantId, platformState)` —— 三参数版本：
- `plugin/PluginTenantService.java:39-67`

`PluginRuntimeService.isEnabledForTenant(pluginCode, tenantId)` —— 两参数委派版：
- `plugin/PluginRuntimeService.java:279-282`

```java
// PluginRuntimeService (两参数委派)
public boolean isEnabledForTenant(String pluginCode, Long tenantId) {
    if (pluginCode == null) return false;
    PluginRuntimeInfo info = getByCode(pluginCode);  // 拿 pf4j state
    return tenantService.isEnabledForTenant(pluginCode, tenantId, info.getState());
}

// PluginTenantService (三参数核心)
public boolean isEnabledForTenant(String pluginCode, Long tenantId, String platformState) {
    if (!"STARTED".equals(platformState)) return false;  // ① pf4j 必须 STARTED
    try {
        PluginInstall install = installMapper.selectOne(...)
            .eq(pluginCode).eq(tenantId).last("LIMIT 1");
        if (install == null) {
            if (tenantId != null && tenantId != 0L) {
                return isEnabledForTenant(pluginCode, 0L, platformState);  // ② 回退平台级
            }
            return true;  // ③ 兜底默认启用 — I023 幽灵 NPE 风险源
        }
        return install.getEnabled() != null && install.getEnabled() == 1;  // ④ tenant flag
    } catch (Exception e) { return false; }
}
```

## 二、判断逻辑流

```
isEnabledForTenant(code, tid) 被调
  ↓
pf4j state != STARTED → false （F046 install 后未 load → 卡在这里）
  ↓
查 plugin_install (code, tid) 
  ├─ 记录存在 → enabled == 1?
  ├─ 记录不存在 → 回退查 (code, 0)
  │   ├─ 平台级记录存在 → enabled == 1?
  │   └─ 平台级记录不存在 → 默认 true  ← I023 风险
```

## 三、运行时受限制说明

### 3.1 当前环境（pf4j 0 插件）

| 步骤 | 预期行为 | 实际行为（受限）|
|---|---|---|
| install 后不 load 调 isEnabledForTenant | true（I023 风险）| **false**（pf4j state != STARTED，第一关就拒）|
| load 后 enable 调 isEnabledForTenant | true（STARTED + 无 install 记录 → 兜底）| 受限（pf4j 加载 0 插件）|

### 3.2 I023 幽灵 NPE 风险分析

SoT（§5 已知问题）原文：
> **`plugin_install` 幽灵记录**：marketplace 安装只插 `plugin_install`，**runtime 未加载**时 `isEnabledForTenant` 仍返回 `STARTED && true`（兜底默认启用）→ 业务侧可能调到一个不存在的插件入口（NPE）

**实际静态判定**：
- `isEnabledForTenant` 第一关 `platformState != STARTED` → false
- 业务侧拿 false → 不调用插件入口 → **不会触发 NPE**
- 但如果业务侧**忽略返回值**直接反射调 → `pluginManager.getPlugin` 返回 null → NPE（这是业务调用方责任，不是 service 责任）

**I023 风险等级**：**降低**（service 层正确阻断，业务侧需自律）

### 3.3 F039 实证数据旁证

来自 `probe-5b-f039.ps1`：
- `POST /plugin/runtime/tenant/1/test-probe-5b/enable` 返回 `tenantEnabled=true, effectiveEnabled=false`
- `effectiveEnabled` 计算：`runtime.isEnabledForTenant("test-probe-5b", 1)` → pf4j 0 插件 → false
- **这是正确行为**：pf4j 未加载 → 不能真正调用插件入口
- **但 UI 易困惑**：enabled=true 但 effective=false（用户视角："我开启了但没生效"）

## 四、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| 三层 fallback 逻辑 | ✅ VERIFIED | L0 静态可达 |
| pf4j STARTED 检查 | ✅ VERIFIED | 第一关就拒 |
| tenant flag 检查 | ✅ VERIFIED | `enabled == 1` 才返回 true |
| 平台级回退 | ✅ VERIFIED | tenantId=0 回退查询 |
| 默认 true 兜底 | ✅ 静态识别 | I023 风险源 |
| 业务侧 NPE 阻断 | ✅ 静态判定 | service 层正确阻断，依赖业务自律 |

### 最终状态

**F038 = PARTIAL-NON-BLOCKER**（静态完整 + 运行时受 pf4j 0 插件限制；I023 风险降级为业务调用方责任）

---

## 五、Evidence 文件

| 文件 | 路径 |
|---|---|
| 源码 | `backend/src/main/java/com/meiyetec/plugin/PluginRuntimeService.java:279-282` |
| 源码 | `backend/src/main/java/com/meiyetec/plugin/PluginTenantService.java:39-67` |
| F039 实证旁证 | `audit/runtime/probe-5b-f039.ps1` |

---

**F038 静态判定稳健。运行时受 pf4j 0 插件限制，预期行为无法实测。**
