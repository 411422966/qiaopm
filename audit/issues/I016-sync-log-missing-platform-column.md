# I016 — 同步日志 tab 缺失 platform 列（API 过滤维度未渲染）

| 字段 | 值 |
|---|---|
| **Issue ID** | I016 |
| **严重度** | 🟡 中 |
| **模块** | CRM 集成（前端 UI） |
| **发现阶段** | Phase 5B E2E 列头断言（第二轮修复） |
| **状态** | OPEN |
| **类型** | UX / 契约可观测性 |

---

## 1. 现象

`frontend/meiyetec-web/src/views/crm-integration/index.vue` 的「同步日志」tab 列定义（line 26-31）：

```vue
<el-table-column prop="id" label="ID" width="60"/>
<el-table-column prop="integrationId" label="集成" width="80"/>
<el-table-column prop="syncType" label="类型" width="100"/>
<el-table-column prop="status" label="状态" width="100"/>
<el-table-column prop="syncAt" label="同步时间" width="180"/>
<el-table-column prop="remark" label="备注"/>
```

**列头实测**（F026 E2E）：`['ID', '集成', '类型', '状态', '同步时间', '备注']`

**实际**：**未渲染 `platform` 列**。

---

## 2. 根因

| 层级 | 实际 |
|---|---|
| **后端 SQL 过滤维度** | `CrmIntegrationService.listSyncLogs`（`service/CrmIntegrationService.java:130-141`）按 `crm_sync_log.platform` 过滤（不是 integrationId）|
| **前端展示** | 只渲染 `integrationId` 列（实际恒空，见 I003），**没有 platform 列** |
| **用户影响** | 看同步日志时无法直观看到「这条 log 属于哪个 CRM 平台」（SALESFORCE / HUBSPOT / MOCK / ZOHO），只能从 `integrationId` 反查 `crm_integration` 表才能知道 — 但该列恒空，反查也无门 |

---

## 3. 修复建议

| 方案 | 改动 | 优点 | 缺点 |
|---|---|---|---|
| **① 前端补列** | 同步日志 tab 加 `<el-table-column prop="platform" label="平台" />` | 最小改动，前后端语义对齐 | 仅展示，不解决 I003 |
| **② I003 一并修** | I016 + I003 一起处理：前端删 `integrationId` 列、改 `platform` 列，后端 SQL 保持 `platform` 过滤 | 一致性最佳 | 改动较大，需同时更新 EXT-24 文档 |

**推荐**：方案 ①（最小可行，与 I003 独立追踪）。

---

## 4. E2E 验证点

`audit/scripts/e2e/F026-sync-log-tab.spec.ts:46-47` 反向断言（修复前为 false = I016 负向证据）：

```ts
// ⚠️ I016（新建）：同步日志 tab 未渲染 platform 列，但后端按 platform 过滤
expect(headers.some((h) => h.toLowerCase().includes('platform'))).toBe(false)
```

修复后该断言应改为 `toBe(true)`。

---

## 5. 与 I003 关系

- **I003**：列 `prop=integrationId` 但数据恒空（前端标签叫「集成」）
- **I016**：列 `platform` 完全未渲染（用户根本看不到这一维度）
- 二者**根因部分重叠**（都源于「后端按 platform 过滤」vs「前端按 integrationId 渲染」的契约不一致），但**修复路径独立**：I003 是数据缺失，I016 是列缺失

---

## 6. 状态

- [x] 现象已记录
- [x] 根因已定位
- [ ] 修复方案选定（待用户决策）
- [ ] 前端代码修复（不动业务代码前提下，需用户决策 D3）
