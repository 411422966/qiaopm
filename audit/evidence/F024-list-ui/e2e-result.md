# F024 — 集成列表 UI E2E 结果（Phase 5B 第三轮）

## 0. 基本信息

| 字段 | 值 |
|---|---|
| **Feature ID** | F024（集成列表 UI）|
| **结果提交时间** | 2026-09-13 17:44 (UTC+8) |
| **起点 commit hash** | `12a97ea57a98d268c864fc7c010b82b305d7bb93` |
| **跑测框架** | Playwright MCP（手动触发）|
| **Spec 文件** | `audit/scripts/e2e/F024-list-ui.spec.ts` |
| **关联证据** | `audit/evidence/F002-list-integration/result.md`（Phase 5B API 验证）|

## 1. Test Cases 汇总

| # | Test name | 期望 | 实际 | 状态 | 对应 Issue / 验证点 |
|---|---|---|---|---|---|
| 1 | 集成列表渲染 + 列名 + 状态 enum | 列表至少 1 行 + 列名含「平台 / 店铺 / 方向 / 最近同步 / 状态」+ 状态 enum 为合法值 | 列名通过；但**行数 = 0**（I002 阻塞 list API → 前端空状态）| ❌ **FAIL** | I002（DB 缺列）→ 前端 list 永远为空 |

**汇总**：0 PASS / 1 FAIL

## 2. FAIL 详情

### 2.1 集成列表渲染 + 列名 + 状态 enum → FAIL

- **断言**：
  - `expect(rowCount).toBeGreaterThanOrEqual(1)`
  - `expect(columns.some(c => c.includes('平台'))).toBe(true)`
  - `expect(columns.some(c => c.includes('店铺'))).toBe(true)`
  - `expect(columns.some(c => c.includes('方向'))).toBe(true)`
  - `expect(columns.some(c => c.includes('最近同步'))).toBe(true)`
  - `expect(columns.some(c => c.includes('状态'))).toBe(true)`
  - `expect(['ACTIVE','DISABLED','ERROR']).toContain(status)`
- **实测**：
  - 列名断言 ✅ 全部通过（前端模板固定）
  - **行数断言 ❌ FAIL**：`rowCount = 0`（API 500 → 前端表格渲染空状态）
  - 状态 enum 断言：**未执行**（行数 0 时该断言跳过）
- **根因**：I002 — `GET /crm-integration/list` 因缺 EXT-15 列抛 `BadSqlGrammarException` → GlobalExceptionHandler → code=500 → 前端拿到空 data → 不渲染行
- **trace.zip**：`frontend/meiyetec-web/test-results/F024-list-ui-F024-—-集成列表-UI-集成列表渲染-列名-状态-enum/trace.zip`
- **error-context.md**：同目录
- **截图**：`frontend/meiyetec-web/test-results/F024-list-ui-F024-—-集成列表-UI-集成列表渲染-列名-状态-enum/test-failed-1.png`

### 2.2 负向证据（合法）

I002 是审计已知的 PARTIAL-BLOCKER，本次 E2E **符合预期暴露**：
- 后端 list API 返回 500
- 前端 catch 错误 → 表格不渲染行
- 用户视觉：「列表为空」+ 控制台报「数据库异常」

→ 这是审计的**合法负向证据**，不视为测试问题

## 3. ✅ 最终状态

**F024 E2E = ❌ PARTIAL-BLOCKER（继承 I002）**

- I002 阻塞是已知问题
- 本次 E2E 确认前端消费链路完全失效
- 修复路径：跑 `migrate_ext_15_crm_real.sql`

## 4. 交叉引用

- **Phase 5A**：F002 list API 验证 → I002 PARTIAL-BLOCKER
- **Phase 5B 第一轮**：F002 evidence result.md 标记 FAIL
- **Phase 5B 第三轮（本轮）**：F024 E2E 确认前端 list 同样因 I002 失效
- **关联 Issue**：I002（DB 表 / 列缺失）/ I005（enum 漂移，状态字段未渲染）
