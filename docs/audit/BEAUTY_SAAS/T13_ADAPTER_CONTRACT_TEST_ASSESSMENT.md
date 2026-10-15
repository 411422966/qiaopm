# T13_ADAPTER_CONTRACT_TEST_ASSESSMENT

> **T13 状态：🟡 评估中 — 不实施新增测试**
> **生成日期**：2026-08-16 00:12 UTC+8
> **上游文档**：T13_PHASE2_PRECHECK_REPORT.md · T13_SIGNATURE_INFRA_ASSESSMENT.md

---

## 0 · 评估目标

回答 1 个核心问题：

> 当前是否需要新增 `DcepAdapterContractTest`？

---

## 1 · 现状检查

### 1.1 已有 Adapter 测试覆盖

| 测试类 | 用例数 | 路径 |
|---|---|---|
| `DcepAdapterFactoryTest` | 4 | `backend/src/test/java/com/meiyetec/dcep/DcepAdapterFactoryTest.java` |
| `DcepAdapterSkeletonTest` | 12 | `backend/src/test/java/com/meiyetec/dcep/DcepAdapterSkeletonTest.java` |
| **合计** | **16** | |

### 1.2 5 method contract 覆盖矩阵

| Method | 已覆盖 | 来自 |
|---|---|---|
| `prepay()` | ✅ | ADAPTER-2/3/4 + FACTORY-4 |
| `query()` | ✅ | ADAPTER-5 |
| `refund()` | ✅ | ADAPTER-6 |
| `closeOrder()` | ✅ | ADAPTER-7 |
| `parseAndVerifyNotify()` | ✅ | ADAPTER-8 |

### 1.3 AbstractDcepAdapter helper 覆盖

| Helper | 已覆盖 | 来自 |
|---|---|---|
| `mapError()` | ✅ | ADAPTER-9 |
| `isTimestampValid()` | ✅ | ADAPTER-10 |
| `computeSign()` | ✅ | ADAPTER-11 |
| `parseJson()` | ✅ | ADAPTER-12 |
| `failPrepay()` | ⚠️ 隐式（通过 prepay NOT_IMPLEMENTED 验证） | — |

### 1.4 工厂契约覆盖

| 工厂契约 | 已覆盖 | 来自 |
|---|---|---|
| 注册数量（3 个银行） | ✅ | FACTORY-1 |
| channelCode 路由 | ✅ | FACTORY-2 |
| 未知 channelCode 异常 | ✅ | FACTORY-3 |
| 端到端 prepay | ✅ | FACTORY-4 |

---

## 2 · 是否需要新增 DcepAdapterContractTest

### 2.1 评估结论

> ❌ **不需要新增独立的 `DcepAdapterContractTest`**。
>
> 已有 `DcepAdapterFactoryTest` (4) + `DcepAdapterSkeletonTest` (12) = 16 用例已经覆盖全部 5 method contract + 4 helper + 工厂契约。

### 2.2 重复新增的风险

| 风险 | 影响 |
|---|---|
| 与现有测试重叠 | 维护成本增加；重复断言 |
| 测试套件膨胀 | CI 时间变长 |
| 误以为"新增了" | 治理视角混乱 |

### 2.3 但仍存在的小缺口（**不阻塞**）

| 缺口 | 说明 |
|---|---|
| `failPrepay()` 无独立测试 | 仅隐式覆盖；新增 1 用例可补 |
| `parseAndVerifyNotify` 时间戳校验无独立验证 | 当前仅断言 signatureValid=false / timestampValid=false；未测"真实时间戳下 timestampValid=true" |
| 3 个银行 notify 的对称性测试 | 当前只测 CCB |

**这些缺口是 nice-to-have，不阻塞 Phase 2**。评估是否新增属于"Phase 2.2 实施期"任务。

---

## 3 · 未来如需新增（仅方案，不实施）

### 3.1 范围建议（最小化）

如果未来要补缺口，建议：

| # | 测试名 | 验证 |
|---|---|---|
| 1 | `ADAPTER-13: failPrepay() 字段完整性` | errorCode / errorMsg / success 三字段非空 |
| 2 | `ADAPTER-14: 3 个银行 notify 时间戳有效场景` | 用当前 ts 调用，timestampValid=true |
| 3 | `ADAPTER-15: 3 个银行 notify 字段对称性` | 3 个 adapter 的 NotifyResult 形状一致 |

### 3.2 实施条件

- ❌ 当前 Phase 2 BLOCKED PREPARATION 阶段：**不实施**
- ✅ 建议时机：Phase 2 IMPLEMENTATION 准入后、CCB adapter 实现前，作为前置回归

### 3.3 强制 STOP 条件

| 禁止 | 说明 |
|---|---|
| ❌ 修改现有 Adapter 测试 | 保留 16 用例不动 |
| ❌ 修改 DcepChannelAdapter 接口 | 接口冻结 |
| ❌ 修改 AbstractDcepAdapter 行为 | 基类冻结 |
| ❌ 修改 DcepAdapterFactory | 工厂冻结 |
| ❌ 写真实银行调用 | 严禁 |

---

## 4 · 结论

| 决策 | 结论 |
|---|---|
| 是否新增 DcepAdapterContractTest | ❌ **不需要**（现有 16 用例已覆盖） |
| 是否补 3 个 nice-to-have 用例 | ⏸ Phase 2 实施期判断 |
| BLOCKED PREPARATION 阶段是否实施 | ❌ 否 |
| 评估报告归档 | ✅ |

---

*END OF T13_ADAPTER_CONTRACT_TEST_ASSESSMENT — Generated 2026-08-16 00:12 UTC+8 — 现有 16 用例已覆盖 5 method contract；不需要新增*