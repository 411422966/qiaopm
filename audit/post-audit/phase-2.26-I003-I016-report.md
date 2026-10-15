# Phase 2.26 — I003 + I016 Fix Report

> **修复时刻**：2026-09-30 22:25 (UTC+8)
> **Commit**：`cf9f54e`
> **执行人**：Cursor Assistant
> **关联 Issue**：I003（CRM 同步日志 integrationId 漂移）+ I016（同步日志 tab 缺 platform 列）
> **优先级**：P3（PARTIAL-NON-BLOCKER，UX）
> **验收标准**：vue-tsc --noEmit exit 0；F026 E2E 反向断言已修正为正向

---

## 1. 问题描述

### 1.1 根因

**现状代码**（改前）：

```vue
<!-- views/crm-integration/index.vue L26-31 -->
<el-table :data="logs" style="width:100%;">
  <el-table-column prop="id" label="ID" width="60"/>
  <el-table-column prop="integrationId" label="集成" width="80"/>   <!-- I003：恒空 -->
  <el-table-column prop="syncType" label="类型" width="100"/>
  <el-table-column prop="status" label="状态" width="100"/>
  <el-table-column prop="syncAt" label="同步时间" width="180"/>       <!-- I003：恒空 -->
  <el-table-column prop="remark" label="备注"/>                       <!-- I003：恒空 -->
</el-table>
```

| 列 prop | 后端字段 | 实际值 | Issue |
|---|---|---|---|
| `id` | ✅ `id` | ✅ 正常 | — |
| `integrationId` | ❌ 无 | ⚠️ 恒空 | **I003** |
| `syncType` | ✅ `syncType` | ✅ 正常 | — |
| `status` | ✅ `status` | ✅ 正常 | — |
| `syncAt` | ❌ 无（实体用 `createdAt`） | ⚠️ 恒空 | **I003** |
| `remark` | ❌ 无 | ⚠️ 恒空 | **I003** |
| **`platform`** | ✅ `platform`（由 `CrmSyncScheduler.runIncremental` L102 写入） | **❌ 未渲染** | **I016** |

### 1.2 用户感知

- 打开「同步日志」tab → 看到 6 列 → 集成/同步时间/备注 3 列全空
- 看不出每条 log 属于哪个 CRM 平台
- E2E 反向断言（`F026-sync-log-tab.spec.ts:46, 68`）明示这是**契约漂移**

---

## 2. 修复内容（选项 ①：列替换）

### 2.1 前端 `index.vue` L26-31

```vue
<!-- 改后 -->
<el-table :data="logs" style="width:100%;">
  <el-table-column prop="id" label="ID" width="60"/>
  <el-table-column prop="platform" label="平台" width="100"/>      <!-- 新增：I016 -->
  <el-table-column prop="syncType" label="类型" width="100"/>
  <el-table-column prop="status" label="状态" width="100"/>
  <el-table-column prop="createdAt" label="同步时间" width="180"/>  <!-- I003：syncAt→createdAt -->
</el-table>
```

**净变化**：
- L27: `integrationId → platform`（一改两用：解决 I003 恒空 + 解决 I016 缺列）
- L30: `syncAt → createdAt`（对齐后端实体 `CrmSyncLog.createdAt`）
- L31: 删除整行（`remark` 字段不存在，避免再次恒空漂移）

### 2.2 E2E `F026-sync-log-tab.spec.ts`

```diff
- expect(headers.some((h) => h.includes('集成'))).toBe(true)            // I003 反向
- expect(headers.some((h) => h.toLowerCase().includes('platform'))).toBe(false)  // I016 反向
+ expect(headers.some((h) => h.includes('平台'))).toBe(true)             // I016 正向
+ expect(headers.some((h) => h.toLowerCase().includes('platform'))).toBe(true)   // I016 正向
- expect(firstRow).not.toHaveProperty('integrationId')                  // I003 反向
+ expect(firstRow).toHaveProperty('platform')                            // I016 正向
+ expect(firstRow).toHaveProperty('createdAt')                          // I003 正向
```

文件头注释同步更新：`F026 — 同步日志 tab E2E 验证（I003 + I016 已修复）`

---

## 3. 决策记录

| 候选 | 决策 | 理由 |
|---|---|---|
| **① 列替换**（最终选） | ✅ | 最小改动，前后端语义对齐；不动后端，保持 EXT-24 §0.5 platform 唯一键 |
| ② 完整新增 `integration_id` 列 | ❌ | 破坏 EXT-24 §0.5；引入冗余维度；改动面更大 |
| ③ ① + direction / recordsTotal 列 | ⏸️ 延后 | 等运维有需求时再加；当前仅修复漂移 |

---

## 4. 验证

| 检查项 | 命令 | 结果 |
|---|---|---|
| TypeScript 类型检查 | `vue-tsc --noEmit` | ✅ Exit 0 |
| E2E 修正（反向→正向） | 手动跑 `F026-sync-log-tab.spec.ts` | ⏳ 需前后端运行（用户手动跑） |
| Staged 文件数 | `git status --short` | 仅 2 个 `M `（crm-integration/index.vue + F026 spec.ts） |
| 946 EOL 漂移污染 | F025/F027 等 spec + e2e-result.md | ✅ 全部 unstaged，未混入 |
| 真业务改动行数 | `git diff --cached` | -3 行（删 remark + 改两列 prop/label） |

---

## 5. 改动文件清单

| # | 文件 | 改动类型 | 行变化 |
|---|---|---|---|
| 1 | `frontend/meiyetec-web/src/views/crm-integration/index.vue` | Q1-Q3: 列对齐 | +2 -3 |
| 2 | `audit/scripts/e2e/F026-sync-log-tab.spec.ts` | E2E 断言更新 | 反向→正向 |
| 3 | `audit/issues/issue-register.md` | I003/I016 OPEN → FIXED | — |
| 4 | `audit/post-audit/phase-2.26-I003-I016-report.md` | 本报告 | 新建 |

---

## 6. 未涉及范围

| 项目 | 状态 |
|---|---|
| I005-C1（toggle enum DISABLED） | OPEN（未动） |
| I005-C4（新建集成缺字段输入） | OPEN（未动） |
| I008 / I011 / 其他 CRM 漂移 | OPEN（未动） |
| 后端 `CrmIntegrationService.listSyncLogs` | 已就绪，未动 |
| 数据库 schema `crm_sync_log` | 未动（不引入 `integration_id` 列） |
| EXT-24 §0.5 platform 唯一键约束 | 保持 |

---

## 7. 下一步（按优先级）

| # | Item | P | 说明 |
|---|---|---|---|
| 1 | I005-C1 toggle enum | P3 | `inactive-value="DISABLED"` |
| 2 | I005-C4 集成表单字段 | P3 | 凭据 + objectMappings + syncWindowMinutes |
| 3 | I008 dashboard catch swallow | P2 | CRITICAL 异常吞没 |
| 4 | I011 CRM 假失败 | P1 | select-after-write |
| 5 | EOL 批量清理 | — | 等所有 P0-P3 落地 |

---

## 8. 决策签字

- **代码作者**：Cursor Assistant
- **Fix Commit**：`cf9f54e`
- **Fix 时间**：2026-09-30 22:25 (UTC+8)
- **Type Check**：✅ vue-tsc --noEmit exit 0
- **EOL 政策**：✅ 仅精确 add 2 个业务文件；F025/F027 等 946 EOL 文件留在 working tree
- **待验收**：F026 E2E 手动跑确认 `headers.includes('平台')` 和 `toHaveProperty('platform'/'createdAt')` 通过
