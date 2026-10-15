# Phase 2.25 — I005-C2 Fix Report

> **修复时刻**：2026-09-29 23:58 (UTC+8)
> **Commit**：`67d9b6a`
> **执行人**：Cursor Assistant
> **关联 Issue**：I005 Component 2（CRM 同步 force 参数 + UX 区分）
> **优先级**：P2（PARTIAL-NON-BLOCKER，UX 问题）
> **验收标准**：vue-tsc --noEmit exit 0；代码 review

---

## 1. 问题描述

### 1.1 根因（I005-C2）

**现状代码**（改前）：

```typescript
// api/crm-integration.ts
syncNow: (id: number) => request.post(`/crm-integration/${id}/sync`)
```

```vue
<!-- views/crm-integration/index.vue -->
async function sync(id: number) {
  await crmIntegrationApi.syncNow(id)
  ElMessage.success('已触发同步')  // ← 不论后端实际返回什么都 success
  loadLogs()
}
```

**问题 1**：`syncNow(id)` 未传 `?force=1`，窗口外触发返回 `skipped=true`。
**问题 2**：前端无论后端什么结果（skipped / SUCCESS / FAILED）都弹 `ElMessage.success('已触发同步')`，**用户完全被误导**，以为数据已同步。
**问题 3**：按钮无 loading 状态（请求期间用户可重复点击）。

**用户感知**：点击「同步」→ 立刻 success 提示 → 但看日志发现 skipped 或什么都没同步。

---

## 2. 修复内容

### 2.1 API 签名扩展

**文件**：`frontend/meiyetec-web/src/api/crm-integration.ts`

```typescript
// 改前
syncNow: (id: number) => request.post(`/crm-integration/${id}/sync`)

// 改后（EXT-24 §0.1 对齐）
syncNow: (id: number, force?: boolean) => {
  const url = force
    ? `/crm-integration/${id}/sync?force=1`
    : `/crm-integration/${id}/sync`
  return request.post(url)
}
```

### 2.2 前端 UI 增强

**文件**：`frontend/meiyetec-web/src/views/crm-integration/index.vue`

#### a. 行级 Loading 状态（2 个独立 ref）

```typescript
const syncLoading = ref<Record<number, boolean>>({})
const forceLoading = ref<Record<number, boolean>>({})
```

#### b. 表格操作列（2 个按钮，列宽 220px）

```html
<el-button size="small" type="success" :loading="syncLoading[row.id]" @click="sync(row)">同步</el-button>
<el-button size="small" type="danger"   :loading="forceLoading[row.id]" plain @click="forceSync(row)">强制同步</el-button>
```

#### c. 普通同步（`sync`）

```typescript
async function sync(row: any) {
  syncLoading.value[row.id] = true
  try {
    const res = await crmIntegrationApi.syncNow(row.id)
    handleSyncResult(res?.data, '同步')
    loadLogs()
  } catch { /* 错误已在 request 拦截器处理 */ }
  finally { syncLoading.value[row.id] = false }
}
```

#### d. 强制同步（`forceSync`，独立按钮）

```typescript
async function forceSync(row: any) {
  try {
    await ElMessageBox.confirm(
      '强制同步将忽略 syncWindowMinutes 窗口限制，立即执行一次全量同步，可能消耗较多 API 配额。是否继续？',
      '强制同步确认',
      { type: 'warning', confirmButtonText: '确认强制', cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger' }
    )
  } catch { return }  // 用户取消

  forceLoading.value[row.id] = true
  try {
    const res = await crmIntegrationApi.syncNow(row.id, true)
    handleSyncResult(res?.data, '强制同步')
    loadLogs()
  } catch { /* 错误已在 request 拦截器处理 */ }
  finally { forceLoading.value[row.id] = false }
}
```

#### e. 结果处理（`handleSyncResult`）

```typescript
function handleSyncResult(data: any, prefix: string) {
  if (!data) { ElMessage.warning(`${prefix}：响应为空`); return }

  if (data.skipped === true) {
    // EXT-24 §0.1：窗口外 skipped
    const reason = data.reason ? `（${data.reason}）` : ''
    ElMessage.warning(`${prefix}已跳过${reason}。如需立即执行，请使用「强制同步」按钮`)
    return
  }
  if (data.status === 'SUCCESS') {
    const total = data.recordsTotal ?? data.successCount ?? 0
    const dur   = data.durationMs ? `，耗时 ${data.durationMs}ms` : ''
    const forced = data.forced ? '（强制）' : ''
    ElMessage.success(`${prefix}成功${forced}：${total} 条记录${dur}`)
    return
  }
  if (data.status === 'FAILED') {
    ElMessage.error(`${prefix}失败：${data.errorMessage || '未知错误'}`)
    return
  }
  ElMessage.info(`${prefix}已提交，状态未知`)
}
```

---

## 3. 契约对齐

| 后端响应字段 | 前端处理 | 消息类型 |
|---|---|---|
| `skipped === true` | `ElMessage.warning` + reason + 引导文案 | ⚠️ Warning |
| `status === 'SUCCESS'` | `ElMessage.success` + recordsTotal + durationMs + forced 标记 | ✅ Success |
| `status === 'FAILED'` | `ElMessage.error` + errorMessage | ❌ Error |
| 响应空 / 结构异常 | `ElMessage.warning / info` | ℹ️ Info |
| HTTP 异常（非 200） | `request` 拦截器统一弹 `ElMessage.error` | ❌ Error |

---

## 4. 验证

| 检查项 | 命令 | 结果 |
|---|---|---|
| TypeScript 类型检查 | `vue-tsc --noEmit` | ✅ Exit 0 |
| diff 行数 | `git diff --cached --stat` | **2 files, +201 -128**（不对称，排除 EOL 漂移） |
| staged 文件 | `git status --short` | 仅 `M `（staged）2 个文件，无 `M`（unstaged）混入 |
| EOL 政策遵守 | F019 audit 文件未被 staged | ✅ F019 留在 working tree（EOL 漂移，不 commit） |

---

## 5. 改动文件清单

| # | 文件 | 改动类型 | 行变化 |
|---|---|---|---|
| 1 | `frontend/meiyetec-web/src/api/crm-integration.ts` | Q1: API 签名扩展 | +37 -9 |
| 2 | `frontend/meiyetec-web/src/views/crm-integration/index.vue` | Q2/Q3/Q4: UI 增强 | +164 -119 |
| 3 | `audit/issues/issue-register.md` | I005-C2 OPEN → FIXED | — |
| 4 | `audit/post-audit/phase-2.25-I005-C2-report.md` | 本报告 | 新建 |

---

## 6. 未涉及范围

以下 **I005 其他组件 / 其他 Issue** 均未改动：

| 项目 | 状态 | 说明 |
|---|---|---|
| I005-C1（toggle enum 不对齐） | OPEN | 未动 |
| I005-C4（新建集成缺字段输入） | OPEN | 未动 |
| I003（CRM 列表列不对齐） | OPEN | 未动 |
| I016（CRM sync-log 表缺 platform 列） | OPEN | 未动 |
| I008（dashboard catch swallow） | OPEN | 未动 |
| I011（CRM 假失败） | OPEN | 未动 |
| 后端 `CrmIntegrationService.syncNow` | 已就绪 | 未动（EXT-24 已修复） |

---

## 7. 下一步（按优先级）

| # | Item | P | 说明 |
|---|---|---|---|
| 1 | I005-C1 toggle enum DISABLED | P3 | `inactive-value="DISABLED"` |
| 2 | I005-C4 集成表单字段 | P3 | 凭据 + objectMappings + syncWindowMinutes |
| 3 | I003 CRM 列表列对齐 | P3 | CRM dashboard UI |
| 4 | I016 sync-log 缺 platform 列 | P3 | 后端加列 |
| 5 | I008 dashboard catch swallow | P2 | CRITICAL 异常吞没 |
| 6 | I011 CRM 假失败 | P1 | select-after-write |
| 7 | EOL 批量清理 | — | 所有 P0-P3 落地后一次性 git add -A + commit |

---

## 8. 决策签字

- **代码作者**：Cursor Assistant
- **Fix Commit**：`67d9b6a`
- **Fix 时间**：2026-09-29 23:58 (UTC+8)
- **Type Check**：✅ vue-tsc --noEmit exit 0
- **EOL 政策**：✅ 仅精确 add 2 个业务文件；F019 等 946 EOL 文件留在 working tree
- **待验收**：前端手动验证 skipped / SUCCESS / FAILED 三种场景的 UI 提示是否符合预期
