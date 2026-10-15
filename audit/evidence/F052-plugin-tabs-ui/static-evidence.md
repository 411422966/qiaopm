# F052 — Plugin 前端 市场 + 已安装 tab（Playwright 降级）

> **验证时间**：2026-09-13 20:50 (UTC+8)
> **Phase**：5B — Static + Playwright 降级
> **验证方式**：读源码 + 生成 Playwright 脚本（用户手动跑）
> **最终状态**：**PARTIAL-NON-BLOCKER**（静态识别 I024 Shadow + 降级方案）

---

## 一、源码定位

`frontend/meiyetec-web/src/views/plugin/index.vue:1-30`

页面结构（已 L0 静态可达）：
- `el-tabs` v-model="tab"
- `<el-tab-pane label="插件市场" name="market">` — 市场 tab
- `<el-tab-pane label="已安装" name="install">` — 已安装 tab
- **无 runtime tab** → I024 Shadow 实证（前端无运行时视图）

## 二、I024 Shadow Feature 静态识别

### 2.1 grep 前端引用

```bash
$ grep -r "plugin/runtime\|runtime/list\|runtime/dashboard" frontend/meiyetec-web/src/
# 0 命中（无任何 runtime 消费）
```

### 2.2 前端 api/plugin.ts 方法集

```typescript
// frontend/meiyetec-web/src/api/plugin.ts（完整文件）
export const pluginApi = {
  publish, listMarket, updatePlugin,    // 上架管理 (3)
  install, uninstall, listInstalls,     // 安装 (3)
  review, listReviews                   // 评价 (2)
}
// 总计 8 个方法，全部 marketplace，无 runtime 方法
```

## 三、Playwright 降级脚本

### 3.1 脚本文件

`frontend/meiyetec-web/e2e/audit/F052-plugin-tabs.spec.ts`（生成）

### 3.2 用户手动运行

```powershell
cd D:\QiaoPM\frontend\meiyetec-web
npx playwright test --config=playwright.audit.config.ts e2e/audit/F052-plugin-tabs.spec.ts
```

### 3.3 测试目标

1. 访问 `/plugin` → 验证 tab 数 = 2（市场 + 已安装）
2. 验证无 runtime tab（I024 Shadow）
3. 验证 pubDialog 5 字段（编码/名称/类别/版本/简介）
4. 验证已安装 tab 表格列头

### 3.4 前置条件

- 后端 8080 运行中（已确认 ✅）
- 前端 5173 运行中（用户确认）
- 至少 1 个 tenant 1 的用户在 plugin_market 里有数据

## 四、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| tab 结构 (2 tabs) | ✅ Static-Only | 源码 L0 可达 |
| 无 runtime tab | ✅ Static-Only | I024 Shadow 实证 |
| pubDialog 5 字段 | ✅ Static-Only | 源码 L0 可达 |
| 已安装 tab 表格 | ✅ Static-Only | 源码 L0 可达 |
| E2E 实际行为 | ⏸️ Playwright 降级 | 脚本已生成，等用户手动跑 |

### 最终状态

**F052 = PARTIAL-NON-BLOCKER**（静态识别 + Playwright 降级方案已提供）

---

## 五、Evidence 文件

| 文件 | 路径 |
|---|---|
| 源码 | `frontend/meiyetec-web/src/views/plugin/index.vue` |
| Playwright 脚本 | `frontend/meiyetec-web/e2e/audit/F052-plugin-tabs.spec.ts` |

---

**F052 静态判定稳健。运行时 E2E 降级为用户本地 Playwright。**
