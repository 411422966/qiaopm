# T13_PHASE2_DECISION_GATE

> **T13 状态：🟡 Phase 2 DECISION GATE — 准入条件清单**
> **生成日期**：2026-08-16 00:15 UTC+8
> **上游文档**：T13_PHASE2_PRECHECK_REPORT.md · T13_PHASE2_SECURITY_DECISION.md · T13_EXTERNAL_DEPENDENCY_TRACKER.md · T13_SIGNATURE_INFRA_ASSESSMENT.md · T13_ADAPTER_CONTRACT_TEST_ASSESSMENT.md

---

## 0 · Gate 治理原则

> **本 Gate 是 T13 Phase 2 IMPLEMENTATION 启动前的最后一道人工审批关卡。**
>
> 所有 checkbox 必须由**人工**勾选。Cursor **不允许**自行勾选。
> 任何一项未通过 → 禁止启动 Phase 2 IMPLEMENTATION。

---

## 1 · 准入条件清单（Implementation Gate）

### 1.1 银行侧外部依赖（**7 项 · 全部必需**）

| # | 项 | Owner | 状态 | 文档来源 |
|---|---|---|---|---|
| ☐ | CCB 真实 SDK jar 在仓库中（`pom.xml` 可编译） | 银行 → BD | ❌ | T13_EXTERNAL_DEPENDENCY_TRACKER B-1 |
| ☐ | CCB 接口文档已下载并文档化（字段映射 / 错误码 / 协议） | 银行 → 后端 | ❌ | T13_EXTERNAL_DEPENDENCY_TRACKER B-2 |
| ☐ | 商户号（merchantId）已知并写入配置 | BD → 后端 | ❌ | T13_EXTERNAL_DEPENDENCY_TRACKER B-3 |
| ☐ | 客户端 .pfx 证书已通过 K8s Secret/Vault 注入 | 银行 → DevOps | ❌ | T13_EXTERNAL_DEPENDENCY_TRACKER B-4 |
| ☐ | 银行公钥已获取并配置 | 银行 → 后端 | ❌ | T13_EXTERNAL_DEPENDENCY_TRACKER B-5 |
| ☐ | Sandbox endpoint URL 已知并写入 `application-sandbox.yml` | 银行 → 后端 | ❌ | T13_EXTERNAL_DEPENDENCY_TRACKER B-6 |
| ☐ | 签名算法规范已确认（RSA / SM2 / 双算法） | 银行 → 后端 | ❌ | T13_EXTERNAL_DEPENDENCY_TRACKER B-7 |

### 1.2 内部基础设施（**1 项**）

| # | 项 | Owner | 状态 | 文档来源 |
|---|---|---|---|---|
| ☐ | K8s Secret + Vault 双层密钥管理已部署（生产强制） | DevOps | 🟡 Pending | T13_EXTERNAL_DEPENDENCY_TRACKER I-1 · T13_PHASE2_SECURITY_DECISION D3 |

### 1.3 运营 / 法务（**2 项**）

| # | 项 | Owner | 状态 | 文档来源 |
|---|---|---|---|---|
| ☐ | 对公数字钱包开户（汇元）完成 | BD + 银行 | ❌ | T13_EXTERNAL_DEPENDENCY_TRACKER O-1 |
| ☐ | 同城专线开通 | 网络 + 银行 | ❌ | T13_EXTERNAL_DEPENDENCY_TRACKER O-2 |

### 1.4 决策批准（**2 项**）

| # | 项 | Owner | 状态 | 文档来源 |
|---|---|---|---|---|
| ☐ | D3 密钥管理方案已批准（推荐 K8s+Vault 双层） | 人工（CTO/安全负责人） | ⏸ | T13_PHASE2_SECURITY_DECISION §D3 |
| ☐ | D4 签名算法方案已批准（推荐 RSA+SM2 双算法） | 人工（CTO/技术负责人） | ⏸ | T13_PHASE2_SECURITY_DECISION §D4 |

### 1.5 Phase 路径选择（**1 项**）

| # | 项 | Owner | 状态 | 备注 |
|---|---|---|---|---|
| ☐ | Phase 2 路径已选（路径 A 路径 B 路径 C 路径 D） | 人工（业务/技术） | ⏸ | T13_PHASE2_PRECHECK §1 |

### 1.6 T12 / Phase 1 回归验证（**2 项**）

| # | 项 | Owner | 状态 | 文档来源 |
|---|---|---|---|---|
| ☐ | T12 baseline 回归确认（62 测试仍 PASS） | Cursor 自动 + 人工 review | ⏸ | T13_PHASE1_COMPLETION_REPORT |
| ☐ | T13 Phase 1 回归确认（66 测试仍 PASS） | Cursor 自动 + 人工 review | ⏸ | T13_PHASE1_COMPLETION_REPORT |

### 1.7 风险接受（**1 项**）

| # | 项 | Owner | 状态 | 备注 |
|---|---|---|---|---|
| ☐ | 风险登记（5 项风险 R-1~R-5）已审阅并接受 | 人工（CTO/PM） | ⏸ | T13_EXTERNAL_DEPENDENCY_TRACKER §5 |

---

## 2 · Gate 通过判定

### 2.1 通过条件

| 类别 | 通过 | 阻塞 |
|---|---|---|
| 银行侧（7 项） | **7/7** | 任一 ❌ → BLOCKED |
| 内部基础设施（1 项） | **1/1** | ❌ → BLOCKED |
| 运营法务（2 项） | **2/2** | 任一 ❌ → BLOCKED |
| 决策批准（2 项） | **2/2** | 任一 ❌ → BLOCKED |
| 路径选择（1 项） | **1/1** | ❌ → BLOCKED |
| 回归（2 项） | **2/2** | 任一 ❌ → BLOCKED |
| 风险接受（1 项） | **1/1** | ❌ → BLOCKED |

### 2.2 当前 Gate 总览

```
银行侧:      0/7 ❌
内部基础:    0/1 ❌
运营法务:    0/2 ❌
决策批准:    0/2 ❌
路径选择:    0/1 ❌
回归:        待跑（2 项）
风险接受:    0/1 ❌
─────────────────────
总通过:      0/16 → BLOCKED
```

---

## 3 · 路径分流决策

### 3.1 路径 A：CCB 直联（推荐）

| 项 | 状态 |
|---|---|
| 通过 Gate | 需 7/7 银行依赖 + 2/2 决策批准 + 2/2 运营 |
| 优势 | 直连 DCEP 监管；长期合规 |
| 劣势 | BD 流程 1-3 月；硬门槛多 |
| 当前适用 | ❌ 0/16 已通过 |

### 3.2 路径 B：聚合支付（备选）

| 项 | 状态 |
|---|---|
| 通过 Gate | 需 4 项聚合商依赖 + 决策批准 |
| 优势 | 1-2 周；技术门槛低；规避 SM2 强制 |
| 劣势 | 增加聚合商费用；失去 DCEP 原生监管 |
| 当前适用 | ❌ 0/16 已通过 |

### 3.3 路径 C：维持现状（dev-only）

| 项 | 状态 |
|---|---|
| 通过 Gate | 0 项外部依赖；已具备 |
| 优势 | 立即可接受 T13 在 P0 范围内为 demo-only |
| 劣势 | 无法生产部署 |
| 当前适用 | ✅ P0 可接受 |

### 3.4 路径 D：复用微信/支付宝聚合

| 项 | 状态 |
|---|---|
| 通过 Gate | 视聚合商 |
| 优势 | 数币 API 走第三方；无需 SM2 自研 |
| 劣势 | 失去数币战略；类似路径 B |
| 当前适用 | ❌ 0/16 已通过 |

---

## 4 · Phase 2 子阶段切分（决策后）

当 Gate 通过后，Phase 2 IMPLEMENTATION 拆分为 4 个子阶段：

| 子阶段 | 范围 | 是否依赖银行 |
|---|---|---|
| **Phase 2.1** Signature Infra Bootstrap | 引入 BouncyCastle + SignatureProvider / RsaSigner / Sm2Signer + 单测 | ❌ 否 |
| **Phase 2.2** CcbDcepAdapter.prepay | 调银行 HTTPS + Sign + Map response | ✅ 是 |
| **Phase 2.3** CcbDcepAdapter.parseAndVerifyNotify | Sign verify + 防重放 | ✅ 是 |
| **Phase 2.4** Retry + Notify Replay Defense | Spring Retry + Redis notifyId TTL | ⚠️ 部分依赖（仅 Redis） |

**Phase 2.1 可独立启动**（见 `T13_SIGNATURE_INFRA_ASSESSMENT.md`），其他 3 个阶段必须等 Gate 通过。

---

## 5 · Gate 失败处理

### 5.1 阻塞时 Cursor 行为

| 状态 | Cursor 应做 | Cursor 禁止做 |
|---|---|---|
| ❌ Gate 未通过 | 等待人工 | 自行勾选 checkbox |
| ⏸ 决策待定 | 输出决策对比表 | 替代人工决策 |
| 🟡 部分依赖到位 | 推进 Phase 2.1（如人工批准） | 推进 Phase 2.2+ |

### 5.2 阻塞时人工行为

- 维护 `T13_EXTERNAL_DEPENDENCY_TRACKER.md` 状态
- 在本 Gate 文档勾选已通过的项
- 触发 Phase 2 子阶段审批

---

## 6 · STOP RULE（强制）

执行 Phase 2 BLOCKED PREPARATION 期间：

| 触发 | 立即停止 |
|---|---|
| 需要 SDK | ✅ |
| 需要证书 | ✅ |
| 需要真实接口 | ✅ |
| 需要数据库字段 | ✅ |
| 需要修改 T12 | ✅ |
| 需要修改 Phase 1 frozen 代码 | ✅ |
| 需要勾选本 Gate checkbox | ✅ |
| 需要伪造任何"已对接"状态 | ✅ |

**当前状态：0/16 Gate 通过 → Phase 2 IMPLEMENTATION 禁止启动 → Cursor 仅做治理文档**

---

## 7 · P0 状态最终

```
T01-T10  ✅ 🔒 FROZEN
T11      ✅ 🔒 FROZEN
T12      ✅ 🔒 FROZEN
T13 P1   ✅ 🔒 FROZEN    — Adapter Runtime 已连通
T13 P2   🟡 BLOCKED PREPARATION  — 治理文档完成
         Gate: 0/16 通过
T13 P3   ⏸ 等 T13 P2 Gate 通过 + Phase 2.2 实施
T13 P4   ⏸ 等 T13 P3
T14      ⏸ P0 终极回归
```

---

## 8 · 已交付的治理文档清单

| # | 文档 | 内容 | 状态 |
|---|---|---|---|
| 1 | `T13_PHASE1_COMPLETION_REPORT.md` | Phase 1 验收 + 架构变化 + 测试证据 | ✅ |
| 2 | `T13_PHASE2_PRECHECK_REPORT.md` | Phase 2 商业依赖 Reality Check | ✅ |
| 3 | `T13_PHASE2_SECURITY_DECISION.md` | D3 + D4 决策推荐 | ✅ |
| 4 | `T13_EXTERNAL_DEPENDENCY_TRACKER.md` | 9 项外部依赖跟踪矩阵 | ✅ |
| 5 | `T13_SIGNATURE_INFRA_ASSESSMENT.md` | Phase 2.1 签名基础设施评估 | ✅ |
| 6 | `T13_ADAPTER_CONTRACT_TEST_ASSESSMENT.md` | 16 已有测试覆盖评估 | ✅ |
| 7 | `T13_PHASE2_DECISION_GATE.md`（本文件） | 16 项准入条件 Gate | ✅ |

**Phase 2 BLOCKED PREPARATION 完成，零代码改动，零回归风险。**

---

*END OF T13_PHASE2_DECISION_GATE — Generated 2026-08-16 00:15 UTC+8 — 0/16 Gate 通过 — Phase 2 IMPLEMENTATION 禁止启动*