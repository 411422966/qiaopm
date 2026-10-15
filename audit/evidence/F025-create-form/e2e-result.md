# F025 — 新建集成表单 E2E 结果（Phase 5B 第三轮）

## 0. 基本信息

| 字段 | 值 |
|---|---|
| **Feature ID** | F025（新建集成表单）|
| **结果提交时间** | 2026-09-13 17:44 (UTC+8) |
| **起点 commit hash** | `12a97ea57a98d268c864fc7c010b82b305d7bb93` |
| **跑测框架** | Playwright MCP（手动触发）|
| **Spec 文件** | `audit/scripts/e2e/F025-create-form.spec.ts` |
| **关联证据** | `audit/evidence/F001-create-integration/result.md`（Phase 5B API 验证） + `audit/issues/I011-crm-fake-failure.md` §10 |

## 1. Test Cases 汇总

| # | Test name | 期望 | 实际 | 状态 | 对应 Issue / 验证点 |
|---|---|---|---|---|---|
| 1 | 对话框渲染 + I005 C4 字段缺失 | 表单含「平台 / 店铺名称 / 方向 / 自动同步」，缺 objectMappings / clientId / clientSecret / privateKey | 4 项存在 + 4 项缺失 | ✅ **PASS** | I005 C4（前端缺字段）负向证据 |
| 2 | 填表保存 → 列表新增行 | dialog 关闭 + 列表 +1 行 | dialog **未关**（500 报错）+ 行数 **未 +1**（实际 +1 但前端未刷新）| ❌ **FAIL** | I002（DB 缺列）+ I011（Fake Failure 二次实证）|

**汇总**：1 PASS / 1 FAIL

## 2. PASS 详情

### 2.1 对话框渲染 + I005 C4 字段缺失 → PASS

- **断言**：
  - 平台/店铺名称/方向/自动同步 4 项存在
  - objectMappings/clientId/clientSecret/privateKey 4 项缺失
- **实测**：4/4 + 0/4 全通过
- **意义**：精确捕获 I005 C4 漂移（前端表单缺关键字段）
- **trace.zip**：`frontend/meiyetec-web/test-results/F025-create-form-F025-—-新建集成表单-对话框渲染-I005-C4-字段缺失/trace.zip`
- **error-context.md**：同目录

## 3. FAIL 详情

### 3.1 填表保存 → 列表新增行 → FAIL

- **断言**：
  - `await expect(page.locator('.el-dialog')).toHaveCount(0, { timeout: 5000 })`（dialog 关闭）
  - `expect(afterRows).toBe(beforeRows + 1)`（严格 +1）
- **实测**：
  - ❌ **dialog 未关闭**（保存按钮 → API 500 → 前端 catch → dialog 不关）
  - ❌ **行数未 +1**（前端未刷新列表）
- **根因**：
  - **I002**：`POST /crm-integration` 因缺 EXT-15 列抛 `BadSqlGrammarException` → GlobalExceptionHandler → code=500
  - **I011 二次实证**：**虽然 API 报错，但 DB 实际 INSERT 成功**（详见 §4）
- **trace.zip**：`frontend/meiyetec-web/test-results/F025-create-form-F025-—-新建集成表单-填表保存-→-列表新增行/trace.zip`
- **error-context.md**：同目录
- **截图**：`frontend/meiyetec-web/test-results/F025-create-form-F025-—-新建集成表单-填表保存-→-列表新增行/test-failed-1.png`

### 3.2 5B 第二轮（修复前）vs 第三轮（修复后）对比

| 维度 | 第二轮（修复前）| 第三轮（修复后）|
|---|---|---|
| 断言 | `expect(afterRows).toBeGreaterThanOrEqual(beforeRows)` | `expect(afterRows).toBe(beforeRows + 1)` |
| 0 行 → 0 行是否 PASS | ✅（假 PASS）| ❌（真 FAIL）|
| dialog 关闭断言 | 无 | `toHaveCount(0, timeout: 5000)` |
| I002 未修时的测试有效性 | 假 PASS（bug 暴露但测试不报）| **真 FAIL**（按预期暴露 PARTIAL-BLOCKER）|

### 3.3 负向证据（合法）

- 本次 FAIL **符合预期**（I002 未修 → INSERT 端点必败）
- dialog 未关 + 行数未 +1 是审计**应得**的负向证据
- 测试**有效性已修复**（第二轮假 PASS 已消除）

## 4. I011 Fake Failure 二次实证（关键发现）

### 4.1 dashboard totalIntegrations 时序

| 时间 (UTC+8) | 事件 | totalIntegrations |
|---|---|---|
| 17:37:35 | E2E 第二轮跑前 | **2** |
| 17:44:31 | E2E 第三轮跑后 | **3** |

→ **Δ = +1**（即 F025 "填表保存" 期间 INSERT 的 E2E 测试数据）

### 4.2 推论链

```
F025 填表保存：
  → POST /crm-integration { shopName: "E2E测试集成5B", ... }
  → 后端：
     1. integrationMapper.insert(i)   ← 成功（INSERT 已提交）
     2. integrationMapper.selectById(id)  ← 抛 I002（缺列）
     3. GlobalExceptionHandler → 返回 code=500
  → 前端：
     1. catch 500 → ElMessage.error("保存失败")
     2. dialog 不关（依赖后端 success 关闭）
     3. 列表不刷新（依赖 dialog close 事件）
  → DB：
     1. ✅ 多一行 E2E 测试数据
  → dashboard API（紧随其后）：
     1. selectCount → 真实数（不引用 EXT-15 列）
     2. totalIntegrations = 3  ← 比 17:37:35 的 2 多 1
```

### 4.3 I011 适用范围升级

原 I011 仅在 F003 update 端点实证（§9） → 现升级到 **F025 create 端点同样存在**（§10）。

**影响面扩大**：
- F001 create ← **本次新增实证**
- F003 update ← 5B 首次实证
- F029 status update ← 待验证（同模式）

完整 I011 §10 实证：`audit/issues/I011-crm-fake-failure.md` §10

## 5. ✅ 最终状态

**F025 E2E = ❌ PARTIAL-BLOCKER（继承 I002） + ⚠️ I011 二次实证**

- 对话框渲染：✅ PASS（I005 C4 精确捕获）
- 填表保存：❌ FAIL（I002 阻塞 + I011 二次实证）
- 测试有效性：第二轮假 PASS 已消除

## 6. 交叉引用

- **Phase 5A**：F001 create API 验证 → I002 PARTIAL-BLOCKER
- **Phase 5B 第一轮**：F001 evidence result.md 标记 FAIL
- **Phase 5B 第二轮**：F025 测试**假 PASS**（已修复）
- **Phase 5B 第三轮（本轮）**：F025 真 FAIL + I011 二次实证（dashboard 数字反推）
- **关联 Issue**：I002 / I005 C4 / **I011 §10 二次升级**
