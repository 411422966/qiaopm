# F027 — sync 按钮 force 未传 E2E 结果（Phase 5B 第三轮）

## 0. 基本信息

| 字段 | 值 |
|---|---|
| **Feature ID** | F027（sync 按钮 force 传参）|
| **结果提交时间** | 2026-09-13 17:44 (UTC+8) |
| **起点 commit hash** | `12a97ea57a98d268c864fc7c010b82b305d7bb93` |
| **跑测框架** | Playwright MCP（手动触发）|
| **Spec 文件** | `audit/scripts/e2e/F027-sync-button.spec.ts` |
| **关联证据** | `audit/evidence/F005-manual-sync/result.md`（Phase 5B API 验证）|

## 1. Test Cases 汇总

| # | Test name | 期望 | 实际 | 状态 | 对应 Issue / 验证点 |
|---|---|---|---|---|---|
| 1 | 点击同步按钮 → URL 不带 `?force=1` | URL 包含 `?force=1` | URL **不带** `?force=1` | ❌ **FAIL** | I005 C2（前端 force 未传）|
| 2 | API 直调验证 force 未传 | 端点行为正确（窗口外 skipped）| ❌ HTTP 500 | ❌ **FAIL** | I002（DB 缺列）+ I005 C2 |
| 3 | API 直调 force=1 验证 | 端点行为正确（强制执行）| ❌ HTTP 500 | ❌ **FAIL** | I002（DB 缺列）|

**汇总**：0 PASS / 3 FAIL

## 2. FAIL 详情

### 2.1 点击同步按钮 → URL 不带 `?force=1` → FAIL

- **断言**：`expect(syncRequests[0].url).toContain('force=1')`
- **实测**：URL = `http://127.0.0.1:8080/crm-integration/{id}/sync`（**无 query string**）
- **根因**：I005 C2 — `crmIntegrationApi.syncNow(id)` 未传 `?force=1`
  - 代码位置：`frontend/meiyetec-web/src/api/crm-integration.ts`（syncNow 封装）
  - 后果：窗口外请求返回 `skipped=true`（HTTP 200）→ UI 无感知
- **trace.zip**：`frontend/meiyetec-web/test-results/F027-sync-button-F027-—-sy-7240d--未传-点击同步按钮-→-URL-不带-force-1/trace.zip`
- **截图**：`frontend/meiyetec-web/test-results/F027-sync-button-F027-—-sy-7240d--未传-点击同步按钮-→-URL-不带-force-1/test-failed-1.png`
- **error-context.md**：同目录

### 2.2 API 直调验证 force 未传 → FAIL

- **断言**：
  - `expect(response.status()).toBe(200)`
  - `expect(body.code).toBe(200)`
  - `expect(body.data).toBeTruthy()`
- **实测**：
  - ❌ HTTP 500（I002 阻塞 syncNow 端点）
- **根因**：I002 — `CrmIntegrationService.syncNow` 内部 `integrationMapper.selectById(id)` 抛 `BadSqlGrammarException`（缺 EXT-15 列）
- **trace.zip**：`frontend/meiyetec-web/test-results/F027-sync-button-F027-—-sync-按钮-force-未传-API-直调验证-force-未传/trace.zip`
- **error-context.md**：同目录

### 2.3 API 直调 force=1 验证 → FAIL

- **断言**：同 2.2，但 URL 带 `?force=1`
- **实测**：❌ HTTP 500（同样 I002 阻塞）
- **根因**：同 2.2（无论 force 参数，selectById 先抛错）
- **trace.zip**：`frontend/meiyetec-web/test-results/F027-sync-button-F027-—-sync-按钮-force-未传-API-直调-force-1-验证/trace.zip`
- **error-context.md**：同目录

### 2.4 负向证据（合法）

- I002 阻塞 syncNow 端点 → API 永远 500
- I005 C2 前端未传 force → UI 无 loading + 无错误提示
- 两个问题独立存在，本次 E2E 用 3 个测试分别捕获

## 3. I005 C2 详细说明

### 3.1 现状

```typescript
// frontend/meiyetec-web/src/api/crm-integration.ts
syncNow(id: number) {
  return request.post(`/crm-integration/${id}/sync`)  // ← 无 query
}
```

### 3.2 期望行为

```typescript
syncNow(id: number, force: boolean = false) {
  return request.post(`/crm-integration/${id}/sync?force=${force ? 1 : 0}`)
}
```

### 3.3 后端行为（force 不同时）

| 参数 | 行为 | 返回 |
|---|---|---|
| `force=null` | 尊重窗口（`shouldRunForManualTrigger`）| `skipped=true` + reason（窗口外）|
| `force=0` | 同 force=null | 同上 |
| `force=1` | 跳过窗口检查 | 真实同步结果（`recordsTotal > 0`）|

### 3.4 用户感知

| 场景 | 用户点击同步 | 实际结果 | UI 反馈 |
|---|---|---|---|
| 窗口内 | syncNow(id) | 触发同步 | ElMessage.success("已触发") |
| 窗口外 | syncNow(id) | skipped=true | **ElMessage.success("已触发")**（**误导**）|
| 强制执行 | syncNow(id, force=true) | 触发同步 | 应有 loading + 结果反馈 |

## 4. ✅ 最终状态

**F027 E2E = ❌ PARTIAL-BLOCKER（I005 C2 + I002）**

| 维度 | 状态 |
|---|---|
| 前端 force 传参 | ❌ I005 C2 |
| API 端点可访问 | ❌ I002（全部 3 个测试都因此 FAIL）|
| 用户感知同步结果 | ❌ 无 loading + 误导提示 |

## 5. 交叉引用

- **Phase 5A**：F005 sync API 验证 → I002 PARTIAL-BLOCKER
- **Phase 5B 第一轮**：F005 evidence result.md 标记 FAIL
- **Phase 5B 第三轮（本轮）**：F027 用 3 个测试独立捕获 I005 C2 + I002
- **关联 Issue**：I005 C2 / I002 / EXT-24 Operational Notes §0.1
