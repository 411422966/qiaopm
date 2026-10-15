# I006 — CRM Dashboard Shadow Feature

| 维度 | 值 |
|---|---|
| **Issue ID** | I006 |
| **创建时间** | 2026-09-12 22:03 (UTC+8) |
| **Phase** | 3 — Static Gap Audit |
| **类别** | Shadow Feature（后端 endpoint 有，前端无 tab 消费）|
| **严重度** | 🟡 中 |
| **影响 Feature** | F007（CRM 集成 / Dashboard）|
| **关联 Issue** | I008（dashboard catch 吞异常 — 联动但非因果）|
| **修复阶段** | **审计后**（用户决策 D1 = 不修，本 Phase 4 不动）|

---

## 1. 现象

| 层级 | 实际 |
|---|---|
| 后端 endpoint | `backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java:142-155` `dashboard()` → `GET /crm-integration/dashboard` 返回 `{totalIntegrations, totalSyncLogs}` |
| 前端 API 封装 | `frontend/meiyetec-web/src/api/crm-integration.ts:17` `dashboard: () => request.get('/crm-integration/dashboard')` — **已封装** |
| 前端 UI | `frontend/meiyetec-web/src/views/crm-integration/index.vue:2-3` 只 2 个 tab：集成配置 / 同步日志 — **无 Dashboard tab** |
| Handler 调用 | **`onMounted → loadList() / loadLogs()`（index.vue:91-92）；`dashboard()` 未被任何 Vue 组件调用** |

---

## 2. 代码证据（精确行号）

```vue
<!-- views/crm-integration/index.vue:2-3 -->
<el-tabs v-model="tab">
  <el-tab-pane label="集成配置" name="list">  <!-- tab 1 -->
  <el-tab-pane label="同步日志" name="log">   <!-- tab 2，无 dash -->

<!-- index.vue:91-92 -->
onMounted(() => { loadList(); loadLogs() })   <!-- 无 loadDashboard() -->
```

```java
// service/CrmIntegrationService.java:142-155
@GetMapping("/dashboard")
public Result<Map<String, Object>> dashboard() {
    ...
}
```

---

## 3. 影响

- **业务闭环缺失**：dashboard 数据计算（totalIntegrations + totalSyncLogs）后端实现完毕，但无人消费
- **运维盲区**：管理员无法通过 UI 看到当前租户的 CRM 集成数量 + 同步日志数量
- **数值沉淀无意义**：`/crm-integration/dashboard` HTTP 路径仍可访问，但**实际无 call site**

---

## 4. 修复建议（**审计后**）

| 方案 | 改动 | 优点 | 缺点 |
|---|---|---|---|
| **① 加 dashboard tab** | `<el-tab-pane label="Dashboard" name="dash">` + 简单 KPI 卡片展示 `totalIntegrations` / `totalSyncLogs` | UI 闭合 + 业务闭环 | 新增 ~30 行 Vue |
| **② 加 KPI mini-card 到现有 tab** | 在「集成配置」tab 顶部加一行统计 | 改动最小 | 体验弱 |
| **③ 接受现状** | API 留着供未来总览页使用；文档化「仅 API 暴露」 | 不改代码 | UI 缺位 |
| **④ 删 endpoint** | 同时删后端 + 前端封装 + test | 不留 dead code | 失去未来扩展基础 |

**推荐**：①（最直观）。

---

## 5. Phase 5 验证策略

| 验证方式 | 是否适用 |
|---|---|
| **API-only（curl）** | ✅ 适用。手动 `GET /crm-integration/dashboard` 验证响应结构 |
| **DB-only** | ❌ 不适用（endpoint 计算 COUNT(*) 而非单表读）|
| **Playwright E2E** | ❌ **不适用**（无前端入口），降级方案详 `audit/runtime/plan.md` |
| **实际期望** | API 返回 200 + `{totalIntegrations: int, totalSyncLogs: int}`；**有/无 UI 消费均通过**（因 Shadow 已登记）|

---

## 6. 关联

- `audit/issues/issue-register.md` §六
- `audit/feature-inventory.md` §1.1.2 row 7（F007）
- `audit/traceability-matrix.md` §F007
- `audit/reports/static-gap-report.md` §2.1（F007 Shadow）
