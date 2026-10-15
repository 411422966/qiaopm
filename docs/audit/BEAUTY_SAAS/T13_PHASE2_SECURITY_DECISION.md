# T13_PHASE2_SECURITY_DECISION

> **T13 状态：🟡 Phase 2 DECISION — D3 + D4 待人工批准**
> **生成日期**：2026-08-15 23:55 UTC+8
> **上游文档**：T13_PHASE2_PRECHECK_REPORT.md
> **下游文档**：T13 Phase 2 IMPLEMENTATION（**⏸ BLOCKED**）

---

## D3 · 密钥管理决策

### 候选对比

| # | 方案 | 优点 | 缺点 | 评级 |
|---|---|---|---|---|
| 1 | **JCEKS Keystore**（JDK 自带） | 简单；JDK 原生；无新增依赖 | 文件系统依赖；权限管控弱；轮换困难；无审计；不符合金融合规 | ❌ 不推荐 |
| 2 | **K8s Secret 单独** | 简单；etcd at-rest 加密 | 无审计日志；轮换不优雅；单层防护 | ⚠️ 临时方案 |
| 3 | **K8s Secret + Vault 双层** | 双层防护：K8s 注入 + Vault 审计 + 动态密钥 + RBAC | 需 Vault 基础设施；学习成本 | ✅ **推荐** |
| 4 | **AWS KMS / 阿里云 KMS** | 云原生；HSM 支持；自动轮换 | 云厂商锁定；成本 | ⚠️ 视云厂商 |
| 5 | **HashiCorp Vault 单独** | 集中管理；审计；动态密钥 | 需自管 Vault；高可用复杂 | ⚠️ 单 Vault 不如方案 3 |

### 推荐：D3-3 · **K8s Secret + Vault 双层**（推荐）

**架构**：

```
Keys layer 1 (K8s Secret) ── namespace-scoped, encrypted at rest, mounted as env
       │
       └── Vault (audit log + RBAC + dynamic creds + rotation)
              │
              ├── secrets/data/meiyetec/dcep/ccb/merchant_pkey    → CcbDcepAdapter
              ├── secrets/data/meiyetec/dcep/ccb/client_cert     → CcbDcepAdapter
              ├── secrets/data/meiyetec/dcep/ccb/bank_pubkey     → CcbDcepAdapter (verify)
              ├── secrets/data/meiyetec/dcep/aggregator/api_key  → AggregatorDcepAdapter
              └── secrets/data/meiyetec/dcep/prod/sign_algo     → RsaSigner / Sm2Signer
```

**禁止项**：
- ❌ 私钥落库（任何字段都不存私钥明文）
- ❌ 私钥写入 application.yml
- ❌ 私钥提交到 Git（即使 sandbox 测试 key 也禁止）
- ❌ 私钥写入日志（slf4j 必须 mask）
- ❌ 测试环境复用生产 key

**Vault 角色划分**：
- `dcep-readonly`：CI/容器启动读
- `dcep-rotate`：密钥轮换专用
- `dcep-audit`：审计日志读

### 轮换策略

| Key | 轮换周期 | 影响 |
|---|---|---|
| 商户私钥 | 90 天 | 需重建签名；notify 验签用旧公钥可兼容 7 天 |
| 客户端证书 (.pfx) | 365 天 | 提前 30 天告警 |
| 银行公钥 | 永久（除非银行变更） | 通知时热更新 |
| Aggregator API Key | 180 天 | 需灰度新 key 双跑 24h |

### 决策表

| 选项 | 适用场景 | 当前 Phase 2 推荐 |
|---|---|---|
| **D3-A: JCEKS** | 仅 dev demo | ❌ 不推荐 |
| **D3-B: K8s Secret 单层** | 内部测试 | ❌ 不生产 |
| **D3-C: K8s Secret + Vault 双层** | 生产 | ✅ **推荐（生产强制）** |
| **D3-D: 云 KMS** | 已有云 KMS | ⚠️ 视云厂商 |
| **D3-E: Vault 单独** | 已有 Vault 高可用 | ⚠️ 双层更稳 |

### 人工决策点 D3

- [ ] **人工最终决策 D3-3 推荐方案（K8s Secret + Vault 双层）**
- [ ] 或选择 D3-D（云 KMS）—— 需明确云厂商
- [ ] 或选择 D3-E（Vault 单独）—— 需明确 Vault 高可用方案

---

## D4 · 签名算法决策

### 候选对比

| # | 算法 | 标准 | 兼容性 | 性能 | 监管方向 |
|---|---|---|---|---|---|
| 1 | **RSA-SHA256** | RFC 8017 / PKCS#1 v2.2 | 99% 银行 | 中（签名慢） | 国际通用 |
| 2 | **RSA-SHA1** | 旧的 | 兼容老 | 快 | ⚠️ 已不安全 |
| 3 | **SM2 + SM3** | GM/T 0003-2012 + GM/T 0004-2012 | 央行指定方向；建行/工行/农行均要求 | 快（ECDSA） | 🇨🇳 国密（**DCEP 强烈推荐**） |
| 4 | **ECDSA-P256** | NIST / FIPS 186-4 | 国际 | 快 | 国际通用 |

### 国密方向事实

> **DCEP（央行数字人民币）协议使用 SM2/SM3/SM4 系列国密算法**。
> 银行适配器必须支持 SM2 是监管要求，而非选项。

来源：央行《数字人民币（DCEP）技术架构》、建行/工行/农行对公数字钱包接入规范。

### Phase 2 实际约束

| 维度 | 现状 |
|---|---|
| CCB 协议文档 | ❌ 未到位（银行 BD 流程） |
| 必须算法 | ❓ **仍未知**——必须银行侧明确 |
| BouncyCastle 依赖 | ❌ 未引入 |

### 推荐：D4-C · **SM2 + RSA 双算法支持**

**Phase 2 阶段策略**（分阶段）：

| 阶段 | 范围 |
|---|---|
| **Phase 2.0**（当前 PRECHECK） | 不实施真实签名；保持 SHA-256 占位 |
| **Phase 2.1**（签名基础设施） | 引入 BouncyCastle；写 `SignatureProvider` 策略 + `RsaSigner` + `Sm2Signer`（**仅基础设施，不调银行**） |
| **Phase 2.2**（CCB 协议） | 根据银行文档配置使用哪种算法；优先 **SM2/SM3**（合规），如银行文档要求 RSA 则 RSA |
| **Phase 3.0**（生产） | 切换到对应算法生产 key |

**架构**：

```java
public interface SignatureProvider {
    String sign(String payload);
    boolean verify(String payload, String sign);
    String algorithm();
}

@Component
@ConditionalOnProperty(prefix="app.dcep.sign", name="algo", havingValue="RSA")
public class RsaSigner implements SignatureProvider { ... }

@Component
@ConditionalOnProperty(prefix="app.dcep.sign", name="algo", havingValue="SM2")
public class Sm2Signer implements SignatureProvider { ... }

@Configuration
public class SignatureConfig {
    @Bean @ConditionalOnMissingBean
    public SignatureProvider signatureProvider(...) {
        // 工厂根据 app.dcep.sign.algo 选择
    }
}
```

### 决策表

| 选项 | 适用场景 | 当前 Phase 2 推荐 |
|---|---|---|
| **D4-A: RSA-SHA256** | 国际 | ✅ 必须支持（基础兼容） |
| **D4-B: SM2/SM3** | 国密合规（DCEP 强制方向） | ✅ **必须支持（合规底线）** |
| **D4-C: 双算法** | 兼容国际 + 国密（推荐） | ✅ **推荐** |
| **D4-D: ECDSA-P256** | 国际 ECC 优化 | ⚠️ 视具体需求 |

### 人工决策点 D4

- [ ] **人工最终决策 D4-C 推荐方案（双算法兼容）**
- [ ] 或选择 **D4-B（仅 SM2）**——如果确认 CCB 只要求 SM2（最快落地）
- [ ] 或选择 **D4-A（仅 RSA）**——仅作为兼容，不推荐（不合规）

---

## 综合决策（待人工批准）

### 推荐组合

| 决策 | 推荐 | 关键依据 |
|---|---|---|
| **D3** | **D3-C · K8s Secret + Vault 双层** | 生产合规；轮换友好；审计完整 |
| **D4** | **D4-C · RSA-SHA256 + SM2/SM3 双算法** | 国密合规 + 国际兼容 |

### 待人工确认

```
1. ☐ D3: 密钥管理方案
   选 [A: JCEKS | B: K8s Secret 单层 | C: K8s Secret + Vault | D: 云 KMS | E: Vault 单独]

2. ☐ D4: 签名算法方案
   选 [A: RSA | B: SM2 | C: 双算法 | D: ECDSA]

3. ☐ Phase 2 路径
   选 [路径 A: 直接对接 CCB | 路径 B: 聚合支付商中转 | 路径 D: 复用微信/支付宝聚合]
   
4. ☐ 外部依赖进展决策
   选 [等所有 9 项 | 仅先动签名基础设施 | 选其他方案]
```

---

## 与现有 T12/T13 Phase 1 的关系

| 项目 | Phase 1 已 frozen | Phase 2 影响 |
|---|---|---|
| `verifySign()` dev MOCK- 失败路径 | 🔒 不动 | 不影响 |
| `AbstractDcepAdapter.computeSign()` SHA-256 | 🔒 不动 | Phase 2.1 旁边加 `SignatureProvider`，**不替换**占位 |
| Adapter contract | 🔒 不动 | 不扩 method |
| `dcepRuntimeReady` 启动检查 | 🔒 不动 | 仍检查 prod + adapter NOT_IMPLEMENTED |
| `DcepService.prepayViaAdapter()` 等 | 🔒 不动 | 透明消费 adapter |

---

## STOP Conditions 已确认

| Trigger | 是否触发 |
|---|---|
| 无银行协议明确签名算法 | ✅ 触发 |
| 无商户号 / 证书 | ✅ 触发 |
| 无 K8s / Vault 部署 | ✅ 触发 |
| 需要修改 T12 边界 | ❌ |
| 需要修改 Phase 1 frozen 代码 | ❌ |

**即便批准 D3 + D4，Phase 2 IMPLEMENTATION 仍需等待外部 SDK / 证书 / 凭据**。

---

*END OF T13_PHASE2_SECURITY_DECISION — Generated 2026-08-15 23:55 UTC+8 — 等待人工批准 D3 + D4*
