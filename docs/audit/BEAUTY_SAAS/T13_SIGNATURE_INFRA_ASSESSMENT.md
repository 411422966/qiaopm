# T13_SIGNATURE_INFRA_ASSESSMENT

> **T13 状态：🟡 评估中 — 不实现**
> **生成日期**：2026-08-16 00:08 UTC+8
> **上游文档**：T13_PHASE2_SECURITY_DECISION.md · T13_EXTERNAL_DEPENDENCY_TRACKER.md

---

## 0 · 评估目标

回答 4 个问题（**仅分析，禁止实施**）：

1. Java 17 + Spring Boot 3.1.5 是否兼容 BouncyCastle 国密？
2. 是否可安全新增 `dcep/sign/` 包 + 3 类（`SignatureProvider` / `RsaSigner` / `Sm2Signer`）？
3. Maven dependency 影响是什么？
4. 是否影响 T12 / Phase 1 frozen 代码？

---

## 1 · Java 版本兼容性分析

### 1.1 当前 Java 版本

- `pom.xml` 第 22 行：`<java.version>17</java.version>`
- Spring Boot 3.1.5（JDK 17 baseline）

### 1.2 BouncyCastle 国密支持矩阵

| BouncyCastle 版本 | Java 17 | SM2/SM3/SM4 | 国密 API | 状态 |
|---|---|---|---|---|
| **bcprov-jdk18on** 1.78+ | ✅ | ✅ | ✅ | 当前推荐 |
| bcprov-jdk15on 1.70+ | ✅ | ✅ | ✅ | 旧版兼容 |
| BouncyCastle Java 1.x | ❌ | ⚠️ 部分 | ⚠️ 部分 | 已 EOL |

### 1.3 关键问题：JDK 默认 SM2 支持？

- JDK 17 **不**含 SM2/SM3/SM4 实现
- 必须引入 BouncyCastle（或华为 JDK / 阿里 Dragonwell 等国密 JDK）

### 1.4 兼容性结论

✅ **Java 17 + BouncyCastle 1.78+ 完全兼容，无版本冲突**

---

## 2 · BouncyCastle 当前存在性检查

### 2.1 工作区证据

| 检查 | 结果 |
|---|---|
| `pom.xml` 是否有 `org.bouncycastle:*` 依赖 | ❌ 无 |
| `pom.xml` 是否有任何 `*crypto*` 国密相关依赖 | ❌ 无 |
| 是否存在 `dcep/sign/` 包 | ❌ 无 |
| 是否存在 `SignatureProvider` / `RsaSigner` / `Sm2Signer` 类 | ❌ 无 |

**结论**：当前仓库**零**国密基础设施。引入 BouncyCastle 是**新增**依赖。

---

## 3 · Maven Dependency 影响评估

### 3.1 最小依赖集（仅签名基础设施所需）

```xml
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78.1</version>
    <scope>compile</scope>
</dependency>
```

**影响**：

| 维度 | 评估 |
|---|---|
| jar 大小 | ~7 MB（无 transitive） |
| 编译期影响 | 0（新增 API） |
| 启动期影响 | 小（首次调用 BouncyCastleProvider 时注册） |
| 与现有依赖冲突 | 0（bcprov 不依赖 Spring；不与现有 jwt/security-crypto/poi 冲突） |
| 安全审计 | ⚠️ 需引入新的第三方依赖；需记录到 OSS Inventory |

### 3.2 不需要引入的依赖

| 不需要 | 原因 |
|---|---|
| `bcpkix-jdk18on` | 证书解析（KeyStore load）— **Phase 2.1 之后** |
| `bcpg-jdk18on` / `bcmail-jdk18on` | OpenPGP / S/MIME — 不相关 |
| 国密 JDK | 仅在需替代 OpenJDK 时 |

**最小集**：仅 `bcprov-jdk18on` 即可支撑 RSA + SM2 签名基础设施。

### 3.3 与 Spring Security crypto 关系

当前 `spring-security-crypto:6.2.0` 已含密码编码器；不涉及 SM2。引入 BouncyCastle **不**冲突。

---

## 4 · 安全评估

### 4.1 引入 BouncyCastle 的合规风险

| 风险 | 评估 |
|---|---|
| 国家出口管制 | BouncyCastle 是国际开源项目，含 SM2 实现 — **出口合规** |
| 中国国密合规 | GB/T 32918-2016 / GB/T 32905-2016 — BouncyCastle 1.78+ 实现合规 ✅ |
| 加密算法专利 | SM2/SM3/SM4 已无专利问题 ✅ |
| License | BouncyCastle License（MIT 兼容）✅ |
| 后门风险 | BouncyCastle 由 Legion of the Bouncy Castle 维护，无已知后门 ✅ |

### 4.2 不能引入私钥进入代码

- ❌ 私钥永不入库（任何字段）
- ❌ 测试 key 不复用生产 key
- ❌ log 不打印私钥
- ❌ 即使 sandbox 测试 key 也不入 Git

---

## 5 · 是否影响 T12 frozen 代码

### 5.1 兼容性矩阵

| T12 frozen 文件 | 影响 |
|---|---|
| `DcepConfigProperties.validate()` | ❌ 0 影响 |
| `DcepService.verifySign()` dev MOCK-* fail-fast | ❌ 0 影响 |
| `pay_dcep_channel.env_mode` CHECK | ❌ 0 影响 |
| `dcepRuntimeReady` | ❌ 0 影响 |

### 5.2 Phase 1 frozen 代码

| Phase 1 frozen 文件 | 影响 |
|---|---|
| `DcepService` 5 个 adapter 私有方法 | ❌ 0 影响（不调 SignatureProvider） |
| `DcepServiceAdapterRoutingTest` 4 用例 | ❌ 0 影响 |
| `DcepChannelAdapter` 接口 | ❌ 0 影响（接口不动） |
| `AbstractDcepAdapter` 基类 | ❌ 0 影响（占位 SHA-256 不动） |

### 5.3 抽象基类 `computeSign()` 注释冲突？

> AbstractDcepAdapter 第 35 行注释：算法支持 "SHA256" / "RSA-SHA256" / "SM3"
> 但实现仅 SHA-256。
>
> 这是**注释超前实现**，但不影响功能。

**结论**：✅ T12 / Phase 1 frozen 代码**完全不受**新增签名基础设施影响。

---

## 6 · 是否需要单独 Phase

### 6.1 推荐拆分

| 阶段 | 内容 | 是否依赖银行 |
|---|---|---|
| **Phase 2.1**（独立可启动） | 引入 BouncyCastle + 写 SignatureProvider / RsaSigner / Sm2Signer + 单元测试（mock 用例） | ❌ 否 |
| **Phase 2.2**（依赖外部） | CcbDcepAdapter.prepay 接入 Signer + 调银行 HTTPS | ✅ 是 |
| **Phase 2.3**（依赖外部） | CcbDcepAdapter.parseAndVerifyNotify 接入 Signer verify | ✅ 是 |
| **Phase 2.4**（依赖外部） | retry strategy + notify replay defense（Redis） | ✅ 是（仅 Redis） |

### 6.2 Phase 2.1 安全性评估

| 风险 | 评估 |
|---|---|
| 引入 BouncyCastle 是否破坏 Phase 1 | ❌ 否（仅新增 jar + 新增类） |
| 是否会被误用调银行 | ❌ 否（Signer 仅签名，不发 HTTP） |
| 是否会污染现有 AbstractDcepAdapter.computeSign() | ❌ 否（保留旧方法） |

**结论**：✅ **Phase 2.1 独立可启动** —— 仅引入 BouncyCastle + 3 类 + 单元测试，**完全不依赖银行**。

### 6.3 Phase 2.1 与 Phase 1 边界

| 项 | Phase 1（冻结） | Phase 2.1（新增） |
|---|---|---|
| 包路径 | `com.meiyetec.dcep.*` | **新增** `com.meiyetec.dcep.sign.*` |
| 修改 | 已有接口 / 类不动 | **仅新增**，不修改 Phase 1 |
| 测试 | `DcepServiceAdapterRoutingTest` | **新增** `Sm2SignerTest` / `RsaSignerTest`（mock 自签） |
| 默认激活 | MOCK 通道走 `mockPrepay()` | 现有路径**完全不调** Signer |

**安全**：Phase 2.1 是"绿色"新增——所有调用方暂时不引用，即使写出 bug 也不影响 T12 / Phase 1 链路。

---

## 7 · 总体评估结论

| 维度 | 结论 |
|---|---|
| Java 17 兼容性 | ✅ |
| BouncyCastle 安全性 | ✅ |
| Maven 依赖影响 | ⚠️ 小（仅新增 7MB jar） |
| T12 frozen 影响 | ✅ 0 影响 |
| Phase 1 frozen 影响 | ✅ 0 影响 |
| 是否可独立 Phase 2.1 | ✅ 是 |
| 是否需要外部银行 | ❌ 否（Phase 2.1 仅本地算法） |
| 推荐 Phase 命名 | **Phase 2.1 — Signature Infra Bootstrap** |

---

## 8 · 强制 STOP 条件

Phase 2.1（**仅当**未来人工批准时）必须遵守：

| 禁止 | 说明 |
|---|---|
| ❌ 修改 `pom.xml` 之外的代码文件 | 严禁 |
| ❌ 调任何银行 endpoint | 严禁 |
| ❌ 写入任何真实 key / 私钥 | 严禁 |
| ❌ 启用 `app.dcep.mode=prod` | 严禁 |
| ❌ 启用 BouncyCastle 默认 provider（可能影响全局） | 必须 `Security.addProvider(new BouncyCastleProvider())` 显式注册 |
| ❌ 修改 T12 frozen | 严禁 |
| ❌ 修改 Phase 1 frozen 代码 | 严禁 |
| ✅ 允许 | 仅新增 `dcep/sign/*.java` + `Sign*Test.java` + pom.xml 新增 bcprov 依赖 |

---

## 9 · Phase 2.1 入口门槛

| 项 | 条件 |
|---|---|
| 依赖 B-7 | 银行确认实际算法（否则 Phase 2.1 写双算法，留 Phase 2.2 切换） |
| 依赖 D4 | 双算法 / 仅 RSA / 仅 SM2（推荐：双算法） |
| D3 已确认 | Vault/K8s 已规划（Phase 2.1 不直接用，但代码要预留接口） |
| 人工批准 | T13_PHASE2_DECISION_GATE.md 全 checkbox 通过 |

---

## 10 · Phase 2.1 不实施（当前）

> **当前 BLOCKED PREPARATION 阶段不引入 BouncyCastle、不新增 `dcep/sign/` 包。**
>
> 本文档仅为 Phase 2.1 准备评估。实施时机：人工批准 + Phase 2 IMPLEMENTATION 准入条件满足。

---

*END OF T13_SIGNATURE_INFRA_ASSESSMENT — Generated 2026-08-16 00:08 UTC+8 — Phase 2.1 可独立启动；当前不实施*