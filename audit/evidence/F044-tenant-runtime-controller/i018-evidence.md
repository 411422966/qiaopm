# F044 — Plugin Runtime Controller 租户级 API（×4 端点 + I018 CONFLICT 实证）

> **验证时间**：2026-09-13 20:38 (UTC+8)
> **Phase**：5B — API 实证 + I018 CONFLICT 实证
> **验证方式**：clerk01 (STORE) + admin (ALL) 跨租户访问对比
> **最终状态**：**CONFLICT → 升级为 PARTIAL-BLOCKER**（I018 实证 SoT 漂移确证，行为正确但 SoT 文档错）

---

## 一、I018 CONFLICT 实证

### 1.1 关键发现

SoT（EXT-23 Operational Notes §5）原文：
> ⚠️ `PluginRuntimeController` **未加租户隔离校验**（直接 `@PathVariable Long tenantId`），平台超管 + 任何租户都能调。

**实证结论**：SoT **描述错误**。实际代码 `PluginRuntimeController.assertTenantAccess`（行 58-69）已实现 `dataScope=ALL || currentTenantId==pathTenantId` 校验。

### 1.2 实证矩阵（来自 `probe-5b-i018.ps1`）

| # | 角色 | dataScope | 端点 | path tenantId | 预期 | 实际 |
|---|---|---|---|---|---|---|
| 1 | clerk01 | STORE | `GET /plugin/runtime/tenant/2/list` | 2 | 403 | **403 ✅** |
| 2 | clerk01 | STORE | `POST /plugin/runtime/tenant/2/demo-hello/enable` | 2 | 403 | **403 ✅** |
| 3 | clerk01 | STORE | `POST /plugin/runtime/tenant/2/demo-hello/disable` | 2 | 403 | **403 ✅** |
| 4 | clerk01 | STORE | `GET /plugin/runtime/persisted?tenantId=2` | 2 | 403 | **403 ✅** |
| 5 | clerk01 | STORE | `GET /plugin/runtime/tenant/1/list` | 1 | 200 | **200 ✅** |
| 6 | admin | ALL | `GET /plugin/runtime/tenant/2/list` | 2 | 200 | **200 ✅** |
| 7 | admin | ALL | `GET /plugin/runtime/persisted?tenantId=2` | 2 | 200 | **200 ✅** |
| 8 | admin | ALL | `GET /plugin/runtime/tenant/999/list` | 999 | 200 | **200 ✅** |

**结果**：
- 普通租户（STORE）跨租户 → **4/4 全 403** ✅
- 普通租户同租户 → **1/1 全 200** ✅
- 平台超管（ALL）跨租户 → **3/3 全 200** ✅

### 1.3 错误消息

```
{"code":403,"message":"权限不足：跨租户访问被拒绝","traceId":"...","timestamp":"..."}
```

来自 `PluginRuntimeController.java:65-69`：
```java
log.warn("[PLUGIN-RUNTIME] 跨租户访问被拒绝: pathTenantId={}, currentTenantId={}, dataScope={}", ...);
return Result.error(BizCode.FORBIDDEN, "跨租户访问被拒绝");
```

## 二、I018 CONFLICT 标记处理

### 2.1 Phase 3 → Phase 5B 状态变化

| 阶段 | 状态 | 理由 |
|---|---|---|
| Phase 3 | CONFLICT | SoT 描述"未加校验" vs 代码已加校验 |
| Phase 5A | CONFLICT | 同上，未实测 |
| **Phase 5B** | **PARTIAL-BLOCKER** | **实证确认 SoT 漂移：代码行为正确，SoT 文档错** |

### 2.2 处置策略（D8 + 实证更新）

- ✅ 代码：保持原样（行为正确）
- ⏸️ SoT（`.cursor/rules/ext-23-plugin-runtime.mdc`）：D8 决策"不改 .mdc"，保留 CONFLICT 历史标记
- 📋 报告：本 evidence 文件明确记录 SoT 漂移方向（SoT 错，代码对）
- 🔄 Post-Audit：建议在下次 .mdc 维护窗口同步更新描述

## 三、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| 4 端点路由可达 | ✅ VERIFIED | curl 全部响应（403 因跨租户，符合预期）|
| assertTenantAccess 跨租户拦截 | ✅ 实证确认 | 4/4 跨租户 → 403 |
| assertTenantAccess 同租户放行 | ✅ 实证确认 | clerk01 tid=1 → 200 |
| assertTenantAccess ALL 放行 | ✅ 实证确认 | admin tid=2/999 → 200 |
| I018 SoT vs 代码漂移 | ✅ 实证确认 | **SoT 错，代码对** |
| I024 Shadow Feature（4 租户端点）| ✅ 静态识别 | grep 前端 0 命中 runtime |

### 最终状态

**F044 = PARTIAL-BLOCKER**（从 CONFLICT 升级，I018 实证 SoT 漂移）

> 之前 Phase 3 标 CONFLICT（SoT vs 代码矛盾未实证）。Phase 5B 用真实 token 实证后，**确认代码行为正确**——I018 实质是 **SoT 文档漂移**（应在 Post-Audit 修复 .mdc，不在本审计范围）。

---

## 四、Evidence 文件

| 文件 | 路径 |
|---|---|
| 测试脚本 | `audit/runtime/probe-5b-i018.ps1` |
| 原始响应 | `audit/runtime/probe-5b-i018.json` |
| 控制器 | `backend/src/main/java/com/meiyetec/controller/PluginRuntimeController.java:58-69, 140-200` |

---

**F044 I018 实证完成。代码行为正确，SoT 文档需 Post-Audit 同步。**
