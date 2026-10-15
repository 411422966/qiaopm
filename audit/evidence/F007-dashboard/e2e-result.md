# F007 — Dashboard E2E 结果（Phase 5B 第三轮）

## 0. 基本信息

| 字段 | 值 |
|---|---|
| **Feature ID** | F007（Dashboard 看板）|
| **结果提交时间** | 2026-09-13 17:44 (UTC+8) |
| **起点 commit hash** | `12a97ea57a98d268c864fc7c010b82b305d7bb93` |
| **跑测框架** | Playwright MCP（手动触发）|
| **Spec 文件** | `audit/scripts/e2e/F007-no-dashboard-tab.spec.ts` |
| **关联证据** | `audit/evidence/F007-dashboard/result.md`（Phase 5B API 验证）|

## 1. Test Cases 汇总

| # | Test name | 期望 | 实际 | 状态 | 对应 Issue / 验证点 |
|---|---|---|---|---|---|
| 1 | I006: 应无 dashboard tab | 前端 crm-integration 页面无 dashboard tab | 无 dashboard tab | ✅ **PASS** | I006（Shadow Feature）负向证据 |
| 2 | API 端点可访问（I008 Fake Success 链）| `GET /crm-integration/dashboard` HTTP 200 + code=200 + 完整 data | HTTP 200 + code=200 + totalIntegrations 等字段 | ✅ **PASS** | I008（Fake Success 链可观测）|

**汇总**：2 PASS / 0 FAIL

## 2. PASS 详情

### 2.1 I006: 应无 dashboard tab → PASS

- **断言**：`expect(dashboardTab).toHaveCount(0)`
- **实测**：`toHaveCount(0)` 通过
- **意义**：确认前端未渲染 dashboard tab → `crmIntegrationApi.dashboard()` 是孤儿 API（I006 Shadow Feature）
- **trace.zip**：`frontend/meiyetec-web/test-results/F007-no-dashboard-tab-F007-4ce44-ature-I006-应无-dashboard-tab/trace.zip`
- **截图**：`frontend/meiyetec-web/test-results/F007-no-dashboard-tab-F007-4ce44-ature-I006-应无-dashboard-tab/test-failed-1.png`（注：失败重跑时仍 PASS，截图保留）
- **error-context.md**：同目录

### 2.2 API 端点可访问（I008 Fake Success 链）→ PASS

- **断言**：
  - `expect(response.status()).toBe(200)`
  - `expect(body.code).toBe(200)`
  - `expect(body.data).toBeTruthy()`
- **实测**：
  - HTTP 200 + code=200 + data 字段含 totalIntegrations / totalSyncLogs 等
  - 但 `totalIntegrations` 反映 **catch 前** 的真实数据（5B 第二次跑 = 2，第三次跑 = 3，详见 §3）
- **trace.zip**：`frontend/meiyetec-web/test-results/F007-no-dashboard-tab-F007-a6a8f--端点可访问（I008-Fake-Success-链）/trace.zip`
- **error-context.md**：同目录

## 3. I008 + I011 交叉证据

### 3.1 dashboard totalIntegrations 时序（I011 反向推算）

| 跑测轮次 | 时间 (UTC+8) | totalIntegrations | 备注 |
|---|---|---|---|
| 第二轮 | 17:37:35 | **2** | dashboard API 响应（catch 之前真实值）|
| 第三轮 | 17:44:31 | **3** | dashboard API 响应（catch 之前真实值）|

→ **Δ = +1 行**（中间 7 分钟）

→ 推断 17:37→17:44 期间发生一次 INSERT（已成功提交），但 API 返回 500（I011 模式），前端 dialog 未关（详见 F025 e2e-result.md）

### 3.2 I008 catch 吞异常说明

即使 I002 抛错，`CrmIntegrationService.dashboard` 的 catch 块仍 `return Result.success(data)`：
- `data` 的 `totalIntegrations` 等字段是 **`selectCount` 抛错前**已经填入的（LinkedHashMap.put 第一个字段时就抛错 → catch → data 含 0 个字段）
- 但本次实测 `totalIntegrations=2 / 3` 表明 **`selectCount` 没抛错**（说明 I002 阻塞条件未命中此端点 — 详见 §4）

### 3.3 dashboard 端点 I002 链路深度分析

| 触发条件 | 是否命中 | 说明 |
|---|---|---|
| `integrationMapper.selectCount()` 缺 EXT-15 列（I002）| ❌ 未命中 | `selectCount` 是基础 count(*)，未引用 EXT-15 新列 → 不抛错 |
| `syncLogMapper.selectCount()` 缺 EXT-15 列 | ❌ 未命中 | 同上 |
| I008 catch 触发 | ❌ 未触发 | 无异常被 catch |
| 真实数据正确返回 | ✅ | `totalIntegrations` 反映真实行数 |

→ **F007 dashboard 在 I002 环境下意外正常工作**（selectCount 不引用新列）。这是审计的意外发现。

## 4. ✅ 最终状态

**F007 E2E = ⚠️ PARTIAL-NON-BLOCKER（继承 I006 + I008 链）**

- I006：前端无 dashboard tab → API 虽正常但无人消费
- I008：catch 吞异常 → 即便 I002 修复后也难定位真实错误
- I002 对本端点意外不阻塞（selectCount 无关 EXT-15 列）

完整 5B 报告：`audit/evidence/F007-dashboard/result.md`

## 5. 交叉引用

- **Phase 5A**：F010 自动 tick 7 次全部 catch 吞 → I008 升级 CRITICAL
- **Phase 5B 第一轮**：F007 result.md 标记 BROKEN（继承 I002 + I008 联动）
- **Phase 5B 第三轮（本轮）**：E2E 意外发现 dashboard 端点 selectCount 不引用 EXT-15 列，I002 阻塞降级为不影响
- **下一阶段**：I008 catch 收紧 + I006 前端加 tab
