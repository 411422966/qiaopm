# T13_EXTERNAL_DEPENDENCY_TRACKER

> **T13 状态：🟡 Phase 2 BLOCKED — 外部依赖跟踪**
> **生成日期**：2026-08-16 00:05 UTC+8
> **上游文档**：T13_PHASE2_PRECHECK_REPORT.md · T13_PHASE2_SECURITY_DECISION.md
> **下游文档**：T13_PHASE2_DECISION_GATE.md

---

## 0 · 治理原则

> 本文档**仅事实记录**，禁止编造任何"已对接""已获得""已批准"状态。
> 每条依赖都标注 **Owner**（谁负责） / **Status**（当前真实状态） / **Blocking**（是否阻塞 Phase 2 IMPLEMENTATION）。
> 状态变更需人工在文档中显式更新——Cursor **不允许**自动篡改。

---

## 1 · 9 项外部依赖跟踪矩阵

### 1.1 银行侧（不可控 · 全部 BLOCKING）

| # | Dependency | Owner | Status | Blocking | 备注 |
|---|---|---|---|---|---|
| **B-1** | CCB 真实 SDK jar（CCB-Java-SDK） | 银行 | ❌ Missing | ✅ YES | `pom.xml` 0 CCB jar；来源未明；需银行 BD 下发 |
| **B-2** | CCB 接口文档 / 协议规范（含 NWXM13/14/15/16 字段映射） | 银行 | ❌ Missing | ✅ YES | CcbDcepAdapter 注释只说"申请后下载"；`https://store.ccb.com/` 凭据未到位 |
| **B-3** | 商户号（merchantId / walletNo） | BD → 银行 | ❌ Missing | ✅ YES | `pay_dcep_channel.merchant_id` 列存在但 0 行真实值 |
| **B-4** | 客户端 .pfx 证书 + 私钥 | 银行 | ❌ Missing | ✅ YES | 无任何证书文件入库 |
| **B-5** | 银行公钥（用于验签） | 银行 | ❌ Missing | ✅ YES | `pay_dcep_channel.public_key_path` 列存在但 0 行真实值 |
| **B-6** | Sandbox endpoint URL | 银行 | ❌ Missing | ✅ YES | `application.yml` 只配 `app.dcep.mode: dev`；无任何银行 URL |
| **B-7** | 签名算法规范（RSA-SHA256 / SM2 / 双算法） | 银行 | ❌ Missing | ✅ YES | 央行 DCEP 协议偏 SM2，但建行实际要求未确认 |

### 1.2 内部侧（可控 · 1 项 BLOCKING）

| # | Dependency | Owner | Status | Blocking | 备注 |
|---|---|---|---|---|---|
| **I-1** | K8s Secret + Vault 双层密钥管理 | DevOps | 🟡 Pending | ❌ NO（不阻塞代码评审） | 当前无 Vault 部署；K8s Secret 单独可用作临时方案但不推荐生产 |

### 1.3 运营 / 法务侧（不可控 · 全部 BLOCKING）

| # | Dependency | Owner | Status | Blocking | 备注 |
|---|---|---|---|---|---|
| **O-1** | 对公数字钱包开户（汇元） | BD + 银行 | ❌ Missing | ✅ YES | CcbDcepAdapter prepay 注释明确指向「BD-2 完成开户」 |
| **O-2** | 同城专线（专用网络接入） | 网络 + 银行 | ❌ Missing | ✅ YES | 涉及物理网络建设；预计 2-4 周 |

---

## 2 · 依赖路径分类

### 2.1 路径 A（CCB 直联 · BLOCKED）

**全部 9 项依赖必须到位**：

| 必到位 | 项 |
|---|---|
| 银行侧 7/7 | B-1, B-2, B-3, B-4, B-5, B-6, B-7 |
| 内部侧 1/1 | I-1（K8s + Vault，可 Phase 2 后并行推进） |
| 运营侧 2/2 | O-1, O-2 |

### 2.2 路径 B（聚合支付 · 中转）

**减少到 4 项依赖**（聚合商代替银行直接对接）：

| 必到位 | 项 | 备注 |
|---|---|---|
| 聚合商 SDK | 类似 B-1 | 改为聚合商 jar |
| 聚合商 API 文档 | 类似 B-2 | 通常聚合商文档公开度更高 |
| 聚合商商户号 | 类似 B-3 | 聚合商下发的 API Key / Secret |
| 签名算法 | 由聚合商规定 | 一般 HTTPS + API Key；不需 SM2 |

**优势**：避免 B-4 / B-5 / B-6 / O-1 / O-2 — 聚合商已屏蔽银行复杂流程。
**劣势**：增加聚合商交易费 + 失去 DCEP 原生监管优势。

### 2.3 路径 C（维持现状）

**0 项新依赖**：

| 状态 | 项 |
|---|---|
| ✅ 已就绪 | Phase 1 Adapter Runtime；MOCK 通道；DcepService 路由 |
| ⚠️ 已知限制 | prod 模式仍依赖 dcepRuntimeReady=false（仅 dev/sandbox 可用） |

**结论**：P0 终极回归可在此状态完成。T13 标记为「P0 范围内接受 dev-only」。

---

## 3 · 关键路径事实摘要

| 项 | 路径 A（CCB 直联） | 路径 B（聚合） | 路径 C（维持） |
|---|---|---|---|
| 商业签约周期 | 1-3 月 | 1-2 周 | 0 |
| 商户号获取 | 银行 BD | 聚合商 | 不需要 |
| 证书管理 | 自管 + 银行公钥 | 聚合商托管 | 不需要 |
| SM2 强制要求 | ✅（DCEP 监管） | ❌（聚合商处理） | ❌ |
| 同城专线 | 必需 | 不需要 | 不需要 |
| DCEP 原生监管 | ✅ 直连监管 | ⚠️ 经聚合商 | ❌ demo-only |
| 估计 Phase 2-3 完成时长 | 2-4 月 | 2-4 周 | 0（已完成） |
| 当前 BLOCKED 数 | 9 项 | 4 项 | 0 项 |

---

## 4 · 跟踪矩阵（更新规则）

### 4.1 状态字段定义

| 状态 | 含义 |
|---|---|
| ❌ Missing | 仓库无任何凭据/凭证/Spec 痕迹 |
| 🟡 Pending | 流程已启动但未完成 |
| 🟢 Available | 已到位（仓库内可见或外部可调用） |
| 🚫 Cancelled | 明确放弃 |

### 4.2 更新流程

1. **谁可以更新**：仅人工（BD / DevOps / 银行对接人 / Cursor 操作员）
2. **Cursor 行为**：禁止自动修改本文件状态
3. **更新触发**：
   - 银行下发 SDK → B-1 → 🟢 Available
   - BD 完成签约 → B-3 → 🟡 Pending（银行流程开始）→ 🟢 Available
   - 文档下载 → B-2 → 🟢 Available
4. **审阅周期**：每 2 周人工 review 一次依赖矩阵

---

## 5 · 风险登记

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| **R-1** | 银行 BD 流程不可控（央行政策、协议变更） | 路径 A 长期 BLOCKED | 准备路径 B 备份 |
| **R-2** | Cursor 自行模拟 SDK 行为（虚假支付） | 🔴 致命（金融诈骗） | 强化 STOP RULE；冻结边界 |
| **R-3** | 签名算法不统一（RSA vs SM2 vs 双算法） | Phase 2.2 重新适配 | D4 双算法预留 |
| **R-4** | 私钥泄露（log/yml/git 残留） | 🔴 致命（合规） | K8s + Vault + 强审计 |
| **R-5** | 文档被实际银行协议不符 | 重新适配代码 | 早期与银行 IT 联调 |

---

## 6 · 当前 BLOCKED 总览

```
银行侧 (B) : 7/7 Missing  → 全部 BLOCKING
内部侧 (I) : 1/1 Pending  → 不阻塞代码评审
运营侧 (O) : 2/2 Missing  → 全部 BLOCKING
─────────────────────
总 BLOCKED: 9 项（路径 A）；4 项（路径 B）；0 项（路径 C）
```

---

## 7 · 下一步

- **Task A2** → 评估签名基础设施（BouncyCastle / SignatureProvider）准备度
- **Task A3** → 评估 Adapter Contract 测试覆盖
- **Task A4** → 生成 T13_PHASE2_DECISION_GATE.md（合并所有 Phase 2 准入条件）

---

*END OF T13_EXTERNAL_DEPENDENCY_TRACKER — Generated 2026-08-16 00:05 UTC+8 — 9 项外部依赖全部 BLOCKED（路径 A）*