# F026 — 同步日志 tab E2E 结果（Phase 5B 第三轮）

## 0. 基本信息

| 字段 | 值 |
|---|---|
| **Feature ID** | F026（同步日志 tab）|
| **结果提交时间** | 2026-09-13 17:44 (UTC+8) |
| **起点 commit hash** | `12a97ea57a98d268c864fc7c010b82b305d7bb93` |
| **跑测框架** | Playwright MCP（手动触发）|
| **Spec 文件** | `audit/scripts/e2e/F026-sync-log-tab.spec.ts` |
| **关联证据** | `audit/evidence/F006-sync-log/result.md`（Phase 5B API 验证） + `audit/issues/I003-*.md`（描述补正）+ `audit/issues/I016-*.md`（新建）|

## 1. Test Cases 汇总

| # | Test name | 期望 | 实际 | 状态 | 对应 Issue / 验证点 |
|---|---|---|---|---|---|
| 1 | 切到同步日志 tab + 验证 integrationId 列 | 列头含「集成」+ 不含 platform | 列头 = `['ID','集成','类型','状态','同步时间','备注']` | ✅ **PASS** | I003（prop 漂移）+ I016（缺 platform 列）|
| 2 | 同步日志 API 响应（I003 漂移实证）| code=200 + data 是数组 | code=500 + data 不存在 | ❌ **FAIL** | I002（DB 缺列）|

**汇总**：1 PASS / 1 FAIL

## 2. PASS 详情

### 2.1 切到同步日志 tab + 验证 integrationId 列 → PASS

- **断言**：
  - `expect(headers.some((h) => h.includes('集成'))).toBe(true)`（列头中文）
  - `expect(headers.some((h) => h.toLowerCase().includes('platform'))).toBe(false)`（反向断言 I016）
- **实测**：
  - 列头实测：`['ID','集成','类型','状态','同步时间','备注']`
  - 中文「集成」✅ 存在
  - platform ❌ 不存在（I016 实证）
- **第二轮（修复前）vs 第三轮对比**：
  - 第二轮断言方向错（`h.includes('integrationid')` 永远 false）
  - 第三轮断言方向正确（中文「集成」 + platform 反向）
- **trace.zip**：`frontend/meiyetec-web/test-results/F026-sync-log-tab-F026-—-同-157ec-同步日志-tab-验证-integrationId-列/trace.zip`
- **error-context.md**：同目录

### 2.2 I003 + I016 关键发现

| 发现 | 说明 |
|---|---|
| **I003 真漂移（数据层面）**| 列 `prop="integrationId"` 但 `crm_sync_log` 表无 `integration_id` 列 → 「集成」列恒空 |
| **I016 真漂移（展示层面）**| 列 `platform` 完全未渲染 → 用户看不到 log 属于哪个 CRM 平台 |
| **I003 描述补正** | 已写入 `audit/issues/issue-register.md` §三（精确化为 prop/label 区分）|
| **I016 新建** | 已写入 `audit/issues/I016-sync-log-missing-platform-column.md` |

## 3. FAIL 详情

### 3.1 同步日志 API 响应（I003 漂移实证）→ FAIL

- **断言**：
  - `expect(response.status()).toBe(200)`
  - `expect(body.code).toBe(200)`
  - `expect(Array.isArray(body.data)).toBe(true)`
  - （I003 主断言 `expect(firstRow).not.toHaveProperty('integrationId')` —— 期待无法执行）
- **实测**：
  - ❌ HTTP 500（I002 阻塞 listSyncLogs）
  - ❌ body.code ≠ 200
  - ❌ body.data 不存在或非数组
- **根因**：I002 — `CrmIntegrationService.listSyncLogs` 因缺 EXT-15 列抛 `BadSqlGrammarException`
- **trace.zip**：`frontend/meiyetec-web/test-results/F026-sync-log-tab-F026-—-同步日志-tab-同步日志-API-响应（I003-漂移实证）/trace.zip`
- **error-context.md**：同目录

### 3.2 负向证据（合法）

- I002 阻塞 listSyncLogs API → 前端拿不到 sync log 数据
- **I003 主断言（数据 API 层）本次无法执行**（端点先 500 死了）
- 列头断言（UI 层）通过 → 与 I003 数据缺失形成完整证据链

## 4. ✅ 最终状态

**F026 E2E = ⚠️ PARTIAL-NON-BLOCKER（I003 + I016 + I002）**

| 维度 | 状态 |
|---|---|
| UI 列头渲染 | ✅ 正确（中文「集成」 + 缺 platform）|
| API 数据返回 | ❌ I002 阻塞 |
| I003 漂移证据 | ✅ 列头断言通过 + 数据断言待 I002 修复后执行 |
| I016 漂移证据 | ✅ 反向断言通过 |

## 5. 交叉引用

- **Phase 5A**：F006 sync log API 验证 → I002 PARTIAL-BLOCKER
- **Phase 5B 第一轮**：F006 evidence result.md 标记 FAIL
- **Phase 5B 第二轮**：F026 测试**断言方向错**（已修复为中文「集成」+ I016 反向）
- **Phase 5B 第三轮（本轮）**：列头 PASS + API FAIL
- **关联 Issue**：I003（描述已补正）/ I016（新建）/ I002（API 阻塞）
