# I007 — CRM Update UI Shadow Feature

| 维度 | 值 |
|---|---|
| **Issue ID** | I007 |
| **创建时间** | 2026-09-12 22:03 (UTC+8) |
| **Phase** | 3 — Static Gap Audit |
| **类别** | Shadow Feature（后端 endpoint 有，前端无 UI 入口）|
| **严重度** | 🟡 中 |
| **影响 Feature** | F003（CRM 集成 / 更新集成）|
| **修复阶段** | **审计后**（用户决策 D1 = 不修，本 Phase 4 不动）|

---

## 1. 现象

| 层级 | 实际 |
|---|---|
| 后端 endpoint | `backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java:58-66` `update(@PathVariable Long id, @RequestBody CrmIntegration i)` → `POST /crm-integration/{id}` |
| 前端 API 封装 | `frontend/meiyetec-web/src/api/crm-integration.ts:8` `update: (id, data) => request.post(\`/crm-integration/${id}\`, data)` — **已封装** |
| 前端 UI | `frontend/meiyetec-web/src/views/crm-integration/index.vue:12-22` 表只有 `id / platform / shopName / syncDirection / lastSyncAt / status / 操作[同步]` — **无 edit 按钮** |
| Handler 调用 | **`update()` 未被任何 Vue 组件 / Handler 调用** |

---

## 2. 代码证据（精确行号）

```vue
<!-- views/crm-integration/index.vue:12-22 (el-table) -->
<el-table-column prop="id" label="ID" width="60"/>
<el-table-column prop="platform" label="平台" width="120"/>
<el-table-column prop="shopName" label="店铺"/>
<el-table-column prop="syncDirection" label="方向" width="100"/>
<el-table-column prop="lastSyncAt" label="最近同步" width="180"/>
<el-table-column prop="status" label="状态" width="100">
  <template #default="{ row }">
    <el-switch v-model="row.status" active-value="ACTIVE" inactive-value="PAUSED" @change="toggle(row)" />
  </template>
</el-table-column>
<el-table-column label="操作" width="120">
  <template #default="{ row }">
    <el-button size="small" type="success" @click="sync(row.id)">同步</el-button>
    <!-- ← 此处缺 edit 按钮 -->
  </template>
</el-table-column>
```

```typescript
// api/crm-integration.ts:8
update: (id: number, data: any) => request.post(`/crm-integration/${id}`, data),

// (整个前端 src 树 grep `crmIntegrationApi.update\|crmIntegrationApi(.*).update` → 0 hit)
```

```java
// service/CrmIntegrationService.java:58-66
@PostMapping("/{id}")
public Result<CrmIntegration> update(@PathVariable Long id, @RequestBody CrmIntegration i) {
    Long tenantId = TenantContextHolder.get().getTenantId();
    i.setId(id);
    i.setTenantId(tenantId);   // ← 强制覆盖为当前 tenant（防越权写）
    integrationMapper.updateById(i);
    return Result.success("已更新", integrationMapper.selectById(id));
}
```

---

## 3. 影响

- **业务闭环缺失**：凭据（`clientId` / `clientSecret` / `privateKey` 等）首次配置错误后**只能删集成重建**（前端无 delete，但 toggle 可设 DISABLED）
- **运维痛点**：Salesforce 私钥轮换无法通过 UI 触发，必须直连 SQL
- **代码可用性 0**：后端 endpoint 写完后**无人调用**，相当于 dead code

---

## 4. 修复建议（**审计后**）

| 方案 | 改动 | 优点 | 缺点 |
|---|---|---|---|
| **① 加 edit 按钮 + 对话框** | 操作列加 `<el-button @click="edit(row)">编辑</el-button>` + `<el-dialog>` + editForm（I005 C4 字段也合并到此）| UI 闭环 + 修 I005 | 新增 ~80 行 Vue |
| **② 加 edit JSON 调试面板** | admin-only 高级页面，原文 JSON 编辑 `objectMappings` 等 | 改动小 | UI 不友好 |
| **③ 接受现状** | API 留着供未来"修改凭据"场景；文档化 | 不改代码 | UI 缺位 |
| **④ 删 endpoint** | 同时删后端 + 前端封装 + 相关 test | 不留 dead code | 失去未来扩展基础 |

**推荐**：①（与 I005 C4 一并修）。

---

## 5. Phase 5 验证策略

| 验证方式 | 是否适用 |
|---|---|
| **API-only（curl）** | ✅ 适用。手动 `POST /crm-integration/{id}` body=`{shopName:'updated'}` 验证更新成功 + 返回新对象 |
| **DB-only** | ✅ 可验证 update 后 DB 行字段实际变化（`SELECT * FROM crm_integration WHERE id=?`）|
| **Playwright E2E** | ❌ **不适用**（无前端入口可触发），降级方案详 `audit/runtime/plan.md` |
| **实际期望** | API 返回 200 + 更新后的 `CrmIntegration` 对象；**有/无 UI 消费均通过**（因 Shadow 已登记）|

---

## 6. 关联

- `audit/issues/issue-register.md` §七
- `audit/feature-inventory.md` §1.1.2 row 3（F003）
- `audit/traceability-matrix.md` §F003
- `audit/reports/static-gap-report.md` §2.1（F003 Shadow）+ §2.2 C5
