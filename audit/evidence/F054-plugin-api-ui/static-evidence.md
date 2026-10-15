# F054 — Plugin 前端 api/plugin.ts 封装（Static + Playwright）

> **验证时间**：2026-09-13 20:50 (UTC+8)
> **Phase**：5B — Static + Playwright 降级
> **验证方式**：读源码 + Playwright 脚本 + I024 Shadow 实证
> **最终状态**：**PARTIAL-NON-BLOCKER**（I024 Shadow 实证 + 降级方案）

---

## 一、源码定位

`frontend/meiyetec-web/src/api/plugin.ts`（完整 19 行）

```typescript
import request from './request'

/**
 * P3-22 插件市场（后端 PluginService @ /plugin）
 */
export const pluginApi = {
  // 上架管理
  publish: (data: any) => request.post('/plugin/market', data),
  listMarket: (params?: { category?: string; status?: string }) =>
    request.get('/plugin/market/list', { params }),
  updatePlugin: (id: number, data: any) => request.post(`/plugin/market/${id}`, data),

  // 安装
  install: (data: { pluginId: number; version?: string }) => request.post('/plugin/install', data),
  uninstall: (id: number) => request.post(`/plugin/install/${id}/uninstall`),
  listInstalls: () => request.get('/plugin/install/list'),

  // 评价
  review: (data: { pluginId: number; rating: number; comment?: string }) =>
    request.post('/plugin/review', data),
  listReviews: (pluginId: number) => request.get(`/plugin/review/list/${pluginId}`)
}
```

## 二、I024 Shadow Feature 静态识别

### 2.1 方法集分析

| 类别 | 方法 | 路由 | 后端端点 |
|---|---|---|---|
| 上架管理 | publish | POST /plugin/market | `PluginService.publish` |
| 上架管理 | listMarket | GET /plugin/market/list | `PluginService.listMarket` |
| 上架管理 | updatePlugin | POST /plugin/market/{id} | `PluginService.updatePlugin` |
| 安装 | install | POST /plugin/install | `PluginService.install` |
| 安装 | uninstall | POST /plugin/install/{id}/uninstall | `PluginService.uninstall` |
| 安装 | listInstalls | GET /plugin/install/list | `PluginService.listInstalls` |
| 评价 | review | POST /plugin/review | `PluginService.review` |
| 评价 | listReviews | GET /plugin/review/list/{pluginId} | `PluginService.listReviews` |

**总计 8 个方法，全部 marketplace 类别**。

### 2.2 runtime 方法缺失

| 应有方法 | 后端端点 | 前端封装 |
|---|---|---|
| listRuntime | GET /plugin/runtime/list | ❌ 缺失 |
| dashboard | GET /plugin/runtime/dashboard | ❌ 缺失 |
| loadPlugin | POST /plugin/runtime/{code}/load | ❌ 缺失 |
| enablePlugin | POST /plugin/runtime/{code}/enable | ❌ 缺失 |
| disablePlugin | POST /plugin/runtime/{code}/disable | ❌ 缺失 |
| unloadPlugin | POST /plugin/runtime/{code}/unload | ❌ 缺失 |
| listTenantPlugins | GET /plugin/runtime/tenant/{tid}/list | ❌ 缺失 |
| enableForTenant | POST /plugin/runtime/tenant/{tid}/{code}/enable | ❌ 缺失 |
| disableForTenant | POST /plugin/runtime/tenant/{tid}/{code}/disable | ❌ 缺失 |
| persisted | GET /plugin/runtime/persisted | ❌ 缺失 |

**结论**：plugin.ts 完全无 runtime 相关方法（I024 Shadow 实证 — 后端 10 个 runtime 端点 100% 无前端消费）。

## 三、Playwright 降级脚本

### 3.1 脚本文件

`frontend/meiyetec-web/e2e/audit/F054-plugin-api.spec.ts`（生成）

### 3.2 用户手动运行

```powershell
cd D:\QiaoPM\frontend\meiyetec-web
npx playwright test --config=playwright.audit.config.ts e2e/audit/F054-plugin-api.spec.ts
```

### 3.3 测试目标

1. **静态验证**：读 plugin.ts 源码 → 提取 8 个方法名 → 验证方法集 + grep 无 runtime 关键字
2. **运行时验证**：拦截 `/plugin/market/list` 和 `/plugin/install/list` 请求，验证 UI 触发调用

## 四、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| 8 个 marketplace 方法 | ✅ Static-Only | 源码 L0 可达 |
| runtime 方法缺失 | ✅ Static-Only | I024 Shadow 实证 |
| 后端 10 个 runtime 端点无前端消费 | ✅ Static-Only | grep 验证 |
| E2E API 拦截验证 | ⏸️ Playwright 降级 | 脚本已生成 |

### 最终状态

**F054 = PARTIAL-NON-BLOCKER**（I024 Shadow 实证 + 降级方案）

---

## 五、Evidence 文件

| 文件 | 路径 |
|---|---|
| 源码 | `frontend/meiyetec-web/src/api/plugin.ts` |
| Playwright 脚本 | `frontend/meiyetec-web/e2e/audit/F054-plugin-api.spec.ts` |

---

**F054 静态判定稳健。I024 Shadow 100% 确认。**
