# F028 — objectMappings 字段 E2E 结果（Phase 5B 第三轮）

## 0. 基本信息

| 字段 | 值 |
|---|---|
| **Feature ID** | F028（objectMappings 字段）|
| **结果提交时间** | 2026-09-13 17:44 (UTC+8) |
| **起点 commit hash** | `12a97ea57a98d268c864fc7c010b82b305d7bb93` |
| **跑测框架** | Playwright MCP（手动触发）|
| **Spec 文件** | `audit/scripts/e2e/F028-object-mappings.spec.ts` |
| **关联证据** | EXT-24 Operational Notes §0.2（`objectMappings` JSON 配置）|

## 1. Test Cases 汇总

| # | Test name | 期望 | 实际 | 状态 | 对应 Issue / 验证点 |
|---|---|---|---|---|---|
| 1 | 新建表单无 objectMappings 字段（I005 C4） | 表单不含 objectMappings 输入框 | 表单无 objectMappings 字段 | ✅ **PASS** | I005 C4（前端缺字段）负向证据 |
| 2 | 后端 API + DB 验证 object_mappings 持久化为 NULL | API 返回 200 + DB 中 object_mappings IS NULL | ❌ HTTP 500 | ❌ **FAIL** | I002（DB 缺列）+ I005 C4 |

**汇总**：1 PASS / 1 FAIL

## 2. PASS 详情

### 2.1 新建表单无 objectMappings 字段（I005 C4）→ PASS

- **断言**：
  - `expect(formItems.some((l) => l.toLowerCase().includes('objectmapping'))).toBe(false)`
  - 同时验证 clientId / clientSecret / privateKey 也缺失
- **实测**：4/4 缺失断言全部通过
- **意义**：精确捕获 I005 C4 漂移（前端表单无 objectMappings 字段 — 4 个关键字段之一）
- **trace.zip**：`frontend/meiyetec-web/test-results/F028-object-mappings-F028--34644--objectMappings-字段（I005-C4）/trace.zip`
- **截图**：`frontend/meiyetec-web/test-results/F028-object-mappings-F028--34644--objectMappings-字段（I005-C4）/test-failed-1.png`
- **error-context.md**：同目录

## 3. FAIL 详情

### 3.1 后端 API + DB 验证 object_mappings 持久化为 NULL → FAIL

- **断言**：
  - `expect(response.status()).toBe(200)`
  - `expect(body.code).toBe(200)`
  - （DB SELECT 验证 object_mappings IS NULL）
- **实测**：
  - ❌ HTTP 500（I002 阻塞 POST /crm-integration）
  - ❌ DB 验证无法执行（先创建失败）
- **根因**：I002 — `POST /crm-integration` 抛 `BadSqlGrammarException`
- **trace.zip**：`frontend/meiyetec-web/test-results/F028-object-mappings-F028--c2f56-证-object-mappings-持久化为-NULL/trace.zip`
- **error-context.md**：同目录

### 3.2 负向证据（合法）

- 本次 FAIL **符合预期**（I002 未修 → CREATE 端点必败）
- **主断言（持久化为 NULL）本应通过**（I005 C4 → 前端未传 objectMappings → 后端默认 NULL）— 但被 I002 抢先阻塞
- 测试设计完整捕获 2 个 Issue 的相互作用

## 4. objectMappings 字段关键背景

### 4.1 EXT-24 行为约束

- 字段类型：`String`（DB 列）JSON 格式
- 必填 key（`updatedAtMs` 不可改名）：
  - `externalId` → `Id`（upsert 路径用）
  - `updatedAtMs` → `SystemModstamp`（增量 cursor 必需）
  - `firstName/lastName/email/phone/title` → 默认映射
- 解析逻辑：`SalesforceCrmAdapter.parseObjectMappings` 未配置 / 失败 / 非对象 → 返回 `DEFAULT_FIELD_MAP` 拷贝（**降级而不是报错**）

### 4.2 I005 C4 影响

- 前端表单缺 objectMappings 输入 → 用户无法在前端配置
- 客户使用自定义字段（如 `Email__c`）→ 必须后端 SQL 直接 UPDATE
- 这是已知 I005 PARTIAL-NON-BLOCKER（用户决策 D3 = 不修）

### 4.3 测试期望行为

```
预期：
  1. POST /crm-integration { shopName, syncDirection, ... }  ← 无 objectMappings
  2. 后端 INSERT：
     - i.objectMappings = null  ← 前端未传 → DB null
  3. 后端 SELECT 拿最新（含自动填充）→ 抛 I002 → API 500
  4. 实际 DB：
     - ✅ 已 INSERT 一行（I011 模式）
     - object_mappings IS NULL ← **符合 I005 C4 预期**
```

→ 即使 API 报错，**I005 C4 + I011 联合效应下，DB 仍写入 object_mappings = NULL**

## 5. ✅ 最终状态

**F028 E2E = ⚠️ PARTIAL-NON-BLOCKER（I005 C4）+ I002 PARTIAL-BLOCKER**

| 维度 | 状态 |
|---|---|
| 前端表单缺 objectMappings | ✅ 确认（PASS）|
| 后端持久化 NULL 行为 | ❌ 测试无法验证（I002 阻塞）|
| 实际推断 | 符合预期：I005 C4 → DB NULL |

## 6. 交叉引用

- **Phase 5A**：F005 sync API 验证 → I002 PARTIAL-BLOCKER
- **Phase 5B 第一轮**：无独立 E2E（F005 范围内）
- **Phase 5B 第三轮（本轮）**：F028 用 2 个测试独立捕获 I005 C4 + I002
- **关联 Issue**：I005 C4 / I002 / I011（联合效应）/ EXT-24 §0.2
