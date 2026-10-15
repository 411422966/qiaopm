# T13_PHASE2_PRECHECK_REPORT

> **T13 状态：🟡 Phase 2 PRECHECK — 商业依赖 Reality Check（不实施）**
> **生成日期**：2026-08-15 23:55 UTC+8
> **上游文档**：T13_PRECHECK_REPORT.md · T13_DECISION_MATRIX.md · T13_IMPLEMENTATION_PLAN.md · T13_PHASE1_COMPLETION_REPORT.md
> **下游文档**：T13_PHASE2_SECURITY_DECISION.md → T13 Phase 2 IMPLEMENTATION（**⏸ BLOCKED**, 等待外部依赖）

---

## 0 · Phase 1 冻结确认（🔒）

| 状态 | 项目 |
|---|---|
| ✅ T13 Phase 1 Adapter Runtime | 66/66 PASS · 0 regression |
| 🔒 | `DcepService` MOCK / non-MOCK 分流 |
| 🔒 | T12 安全边界（mockPrepay / verifySign / env_mode CHECK / dcepRuntimeReady） |
| 🔒 | Adapter Contract（DcepChannelAdapter / AbstractDcepAdapter / DcepAdapterFactory） |
| 🔒 | 业务链路（Booking / Order / Payment / Wallet / Commission / Frontend） |
| 🔒 | 数据库 schema |

**Phase 1 已 frozen，不在 Phase 2 范围。**

---

## 1 · Phase 2 候选路径（Reality Re-Check）

依据 T13_PRECHECK_REPORT.md 已确认的 4 条路径：

| 路径 | 描述 | 当前判断 |
|---|---|---|
| **A** | 签约 1 家（CCB）做 POC，半年可上线 | ⏸ 等银行 BD 流程 |
| **B** | 接入聚合支付平台（银联商务 / 通通联） | ⏸ 等聚合商协议 + 凭据 |
| **C** | 维持现状（dev mock）接受不可生产 | ✅ 当前路径，Phase 1 已就绪 |
| **D** | 与现有微信/支付宝同源（数币 API 走第三方聚合） | ⏸ 复用 Phase 1 + 选聚合商 |

**Phase 2 范围（A 直接路径）已 BLOCKED**：必须先把外部商业凭据下发到位，否则 Phase 2 实施等同于"Cursor 自行模拟真银行支付"——**违反 Reality-First 治理原则**。

---

## 2 · CCB Sandbox Reality Check（实事实查）

### 2.1 必需凭据矩阵 vs 当前仓库情况

| 必需凭据 | 当前仓库 | 判定 | 影响 |
|---|---|---|---|
| CCB SDK jar（CCB-Java-SDK） | ❌ `pom.xml` 无任何 `ccb-` / `njcb-` / `dcep-` 依赖 | **BLOCKED** | Phase 2 无法实施 |
| API 文档 / 协议 | 📜 仅有 CcbDcepAdapter 注释中提及「`https://store.ccb.com/ 申请后下载`」 | **NONE_IN_REPO** | 需 BD / 银行侧提供 |
| 商户号（merchantId） | ❌ `pay_dcep_channel.merchant_id` 列存在但 0 行真实值 | **NO_REAL_VALUE** | 必须由银行下发 |
| 客户端证书 (.pfx) | ❌ 无 | **NO_CERT** | 必须由银行下发 |
| 银行公钥 | ❌ 无 | **NO_KEY** | 必须由银行下发 |
| 签名算法规范（RSA / SM2） | ❌ 无文档 | **NO_SPEC** | 需银行接口文档明确 |
| Sandbox endpoint URL | ❌ `application.yml` 只配 `app.dcep.mode: dev` | **NO_URL** | 需银行侧提供 |
| 同城专线 | ❌ 不在工程范围 | **OUT_OF_SCOPE** | 涉及网络基础设施 |
| 对公数字钱包开户 | ❌ `dcep_merchant_qualification` 表 schema 在，但只承载资质材料图片 | **BD_REQUIRED** | 涉及运营 / 财务 / 法务流程 |

### 2.2 工作区检查（Path 1 — pom.xml）

```xml
<dependencies>
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-boot-starter-validation</dependency>
    <dependency>spring-boot-starter-aop</dependency>
    <dependency>spring-boot-starter-jdbc</dependency>
    <dependency>mysql-connector-j</dependency>
    <dependency>mybatis-plus-boot-starter</dependency>
    <dependency>spring-boot-starter-data-redis</dependency>
    <dependency>jjwt-api / jjwt-impl / jjwt-jackson</dependency>
    <dependency>lombok</dependency>
    <dependency>poi-ooxml</dependency>
    <dependency>spring-security-crypto</dependency>
    <dependency>spring-boot-starter-test</dependency>
</dependencies>
```

**判定**：✅ DB 基础设施完整、❌ 无任何 CCB / DCEP 相关 jar。

### 2.3 工作区检查（Path 2 — application.yml）

```yaml
app:
  dcep:
    mode: dev
```

**判定**：仅 dev 占位，**无任何**：
- CCB sandbox URL
- 商户号占位
- 证书路径
- 签名算法选择
- 私钥路径
- 回调白名单

### 2.4 工作区检查（Path 3 — DcepChannelAdapter / AbstractDcepAdapter）

当前接口：

```java
public interface DcepChannelAdapter {
    PrepayResult prepay(PrepayRequest req);
    QueryResult query(OrderQueryRequest req);
    RefundResult refund(RefundRequest req);
    CloseResult closeOrder(CloseRequest req);
    NotifyResult parseAndVerifyNotify(String rawBody, Map<String,String> headers);
    String channelCode();
}
```

**已就绪**：5 method 接口 + DTO + 抽象基类（SHA-256 placeholder + mapError + isTimestampValid + failPrepay）✅

**判定**：Adapter contract 不需要任何修改（🔒 Phase 1 已 frozen）。

### 2.5 工作区检查（Path 4 — CcbDcepAdapter 现状）

```java
@Component("ccbDcepAdapter")
public class CcbDcepAdapter extends AbstractDcepAdapter implements DcepChannelAdapter {
    public PrepayResult prepay(PrepayRequest req) {
        log.warn("[DCEP-CCB] prepay() not yet implemented ...");
        return failPrepay("NOT_IMPLEMENTED", "建行 DCEP 适配器尚未对接真实 SDK，等待 BD-2 完成开户");
    }
    // ... 4 method 同上
}
```

**判定**：5 method 全 NOT_IMPLEMENTED，且注释明确指向「等 BD-2 + SDK-2」。

### 2.6 工作区检查（Path 5 — RSA / SM2 签名基础设施）

| 工具 | 当前仓库 | 判定 |
|---|---|---|
| `SignatureProvider` 策略接口 | ❌ 不存在（计划中提） | **MISSING** |
| `RsaSigner` | ❌ 不存在 | **MISSING** |
| `Sm2Signer` | ❌ 不存在 | **MISSING** |
| BouncyCastle 依赖 | ❌ pom.xml 无 | **MISSING** |
| JDK 原生 `java.security.Signature` 支持 | JDK 自带，RSA ✅ | **AVAILABLE** |
| 国密算法（SM2/SM3） | 需 BouncyCastle | **MISSING（依赖未引入）** |
| CCB 实际算法要求 | ❓ 无银行协议明确 | **UNKNOWN** |

**判定**：签名基础设施完全未建——**Phase 2 之前必须先建**或明确"Phase 2 仅 mock 签名接入"。

---

## 3 · CCB Adapter Gap Analysis

### 3.1 当前 CcbDcepAdapter 状态

```java
prepay()          →  failPrepay(NOT_IMPLEMENTED, ...)
query()           →  QueryResult.success=false, errorCode=NOT_IMPLEMENTED
refund()          →  RefundResult.success=false, errorCode=NOT_IMPLEMENTED
closeOrder()      →  CloseResult.success=false, errorCode=NOT_IMPLEMENTED
parseAndVerifyNotify() →  signatureValid=false, timestampValid=false
```

### 3.2 真实 SDK 所需能力 vs 缺口

| 能力域 | 真实 SDK 所需 | 当前仓库 | 缺口 |
|---|---|---|---|
| **HTTP Client** | HTTPS 调用（双向 TLS） | JDK `HttpClient` / `HttpURLConnection`（抽象基类未引入） | 需要引入 RestTemplate / WebClient |
| **Request Mapping** | JSON 序列化、字段映射 | ObjectMapper（已有） | ✅ 够用 |
| **Signature Generator** | 按银行算法规范生成签名 | AbstractDcepAdapter.computeSign() 仅有 SHA-256 | ❌ 需 RSA/SM2 |
| **Certificate Loader** | .pfx 文件加载 + 证书链验证 | ❌ 无 | ❌ 需构建 |
| **Private Key Storage** | K8s Secret / Vault | ❌ 无基础设施 | ❌ 需外部分发 |
| **Public Key Verification** | 银行公钥 + verify | ❌ 无 | ❌ 需构建 |
| **Error Mapping** | 银行返回码→业务错误码 | AbstractDcepAdapter.mapError()（已有） | ✅ 够用（可扩展） |
| **Retry Strategy** | 网络异常 / 超时重试 | ❌ 无 | 需 Spring Retry / Resilience4j |
| **Timestamp Validation** | 5 分钟偏差 | AbstractDcepAdapter.isTimestampValid()（已有） | ✅ 够用 |
| **Notify Replay Defense** | notifyId Redis 30 天缓存 | ❌ 无 | ❌ 需引入 Redis Service |
| **MDC / Logging** | 全链路 traceId | T11 已引入（来自 OrderPaidEvent） | ✅ |
| **Sandbox Endpoint** | 测试 URL | ❌ application.yml 不存在 | ❌ 需银行侧 |
| **Production Endpoint** | 生产 URL | ❌ application.yml 不存在 | ❌ 需银行侧 |

### 3.3 缺口量化

| 类别 | 数量 |
|---|---|
| ✅ 已就绪 | 6（接口、DTO、Jackson、mapError、isTimestampValid、T11 链路） |
| ⚠️ 需要新增但不依赖外部 | 5（HTTP client / RSA/SM2 signer / Retry / Cert loader / Notify replay） |
| 🚨 依赖外部凭据 | 6（商户号 / 客户端证书 / 公钥 / Sandbox URL / 生产 URL / 签名算法规范） |

---

## 4 · Security Decision 现状

详见 `T13_PHASE2_SECURITY_DECISION.md`（同批生成）。摘要：

### 4.1 D3 · 密钥管理候选

| 方案 | 优点 | 缺点 | 当前状态 |
|---|---|---|---|
| **K8s Secret + Vault** | 双层：K8s 注入 + Vault 审计；轮换；RBAC | 需 Vault 基础设施 | ⏸ Vault 未部署 |
| **JCEKS Keystore** | JDK 自带；简单 | 文件系统依赖；无 RBAC；轮换困难 | ❌ 不推荐 |
| **K8s Secret 单独** | 简单；etcd 加密 | 无审计；轮换复杂 | ✅ 临时可，但**不生产** |
| **AWS KMS / 阿里云 KMS** | 云原生；HSM | 云厂商锁定 | ⚠️ 视云厂商 |

**推荐（需 D3 决策）**：**K8s Secret + Vault**（双层），Phase 2 上线时强制启用。

### 4.2 D4 · 签名算法候选

| 算法 | 用途 | 兼容性 | 当前状态 |
|---|---|---|---|
| **RSA-SHA256** | 国际通用；成熟 | 99% 银行支持 | ❌ 无实现 |
| **SM2 + SM3** | 国密（GM/T 0003-2012） | 央行指定方向；建行要求 | ❌ 无 BouncyCastle 依赖 |
| **双算法** | 兼容 RSA + SM2 | 全面覆盖 | ❌ 缺两者 |

**推荐（需 D4 决策）**：**RSA-SHA256 主签名 + SM2/SM3 渐进接入**（Phase 2 先 RSA，Phase 3 视建行要求加 SM2）。

---

## 5 · Phase 2 BLOCKED Reason（最高优先级）

### 5.1 阻塞结论

**Phase 2 实施需 9 项外部依赖，未到位则禁止 Cursor 自行实施**：

| # | 外部依赖 | 等待谁 | SLA 估计 |
|---|---|---|---|
| 1 | CCB 真实 SDK jar | 银行 | **不可控** |
| 2 | CCB 接口文档 / 协议规范 | 银行 | **不可控** |
| 3 | 商户号（merchantId） | 银行 | **不可控** |
| 4 | 客户端 .pfx 证书 | 银行 | **不可控** |
| 5 | 银行公钥 | 银行 | **不可控** |
| 6 | Sandbox endpoint URL | 银行 | **不可控** |
| 7 | 签名算法规范（RSA only / SM2 only / 双算法） | 银行 | **不可控** |
| 8 | K8s Secret + Vault 部署 | DevOps | 内部可控（待启动） |
| 9 | 对公数字钱包开户 + 同城专线 | BD + 法务 | **不可控** |

**所有 9 项均不在 Cursor 实施范围。**

### 5.2 强行实施的后果

| 风险 | 后果 | 等级 |
|---|---|---|
| ❌ Cursor 自行模拟 CCB SDK 行为 | 伪造支付成功，等同真实金融诈骗 | 🔴 致命 |
| ❌ 写虚构 HTTPS endpoint URL | 测试通过但生产 fail | 🔴 失败 |
| ❌ 写虚构证书文件 | KeyStore 错误，无法对接 | 🔴 失败 |
| ❌ 写虚构 RSA/SM2 实现 | 与真实银行协议不一致 | 🔴 失败 |
| ❌ 启用 production mode 切真 | dcepRuntimeReady=false，**必须禁止** | 🔴 致命 |

**唯一允许的做法**：等待 9 项外部依赖到位 + 人工批准 → 才进入 Phase 2 IMPLEMENTATION。

---

## 6 · Phase 2 IMPLEMENTATION Boundary（🔒 不动）

Phase 2 PRECHECK **不修改任何文件**，仅生成文档：

| 类别 | 状态 |
|---|---|
| ❌ `CcbDcepAdapter.java` | 不动（仍 5 method 占位） |
| ❌ `AbcDcepAdapter.java` / `IcbcDcepAdapter.java` | 不动 |
| ❌ `DcepService.java`（Phase 1 frozen） | 不动 |
| ❌ `application.yml` | 不动 |
| ❌ 数据库 | 不动 |
| ❌ pom.xml | 不动 |
| ❌ DcepConfigProperties（Phase 1 frozen） | 不动 |
| ❌ DcepChannelAdapter / AbstractDcepAdapter / DcepAdapterFactory（Phase 1 frozen） | 不动 |
| ❌ Booking / Order / Payment / Wallet / Commission 业务链路 | 不动 |
| ❌ 前端 `meiyetec-web/**` | 不动 |
| ✅ 仅允许新增 | `docs/audit/BEAUTY_SAAS/T13_PHASE2_PRECHECK_REPORT.md` + `T13_PHASE2_SECURITY_DECISION.md` |

---

## 7 · STOP Conditions 已全部命中

| Trigger | 命中？ | 处置 |
|---|---|---|
| 无 CCB Sandbox 账号 | ✅ 命中 | BLOCKED |
| 无 SDK | ✅ 命中 | BLOCKED |
| 无证书规范 | ✅ 命中 | BLOCKED |
| 无签名算法规范 | ✅ 命中 | BLOCKED |
| 需要修改数据库 | ❌ | — |
| 需要破坏 T12 | ❌ | — |

**STOP Condition 5/6 命中** → **必须停止 Phase 2 实施**，等待外部依赖 + 人工批准。

---

## 8 · 建议的人工行动项

### 8.1 立即可执行（不需要外部凭据）

| # | 行动项 | 谁 | 预计时长 |
|---|---|---|---|
| A1 | 启动 K8s Secret + Vault 评估 | DevOps | 1 周 |
| A2 | 引入 BouncyCastle + 写 RsaSigner/Sm2Signer（**仅签名基础设施，不调银行**） | 后端 | 1 周（独立） |
| A3 | 建 CcbDcepAdapter 单元测试 fixture（mock adapter.prepay return ok） | 后端 | 1 天 |
| A4 | 写 CcbDcepAdapter ICCD（integration contract test），确认接口契约无回归 | 后端 | 2 天 |

### 8.2 必须等外部依赖（不可执行）

| # | 行动项 | 谁 | 预计时长 |
|---|---|---|---|
| B1 | 与 CCB 签合作协议 | BD + 法务 | 1-3 月 |
| B2 | 申请商户号 + 数字钱包开户 | BD | 2-6 周 |
| B3 | 银行下发 SDK + 证书 | 银行 | 2-4 周 |
| B4 | 同城专线开通 | 网络 | 2-4 周 |
| B5 | 接入 sandbox | 后端 | 1 周 |
| B6 | 生产接入 | 后端 | 2 周 |

**总估计 Phase 2 → Phase 3 完成**：**2-4 个月**（受银行流程约束）。

---

## 9 · P0 进度更新

```
T01-T10  ✅ PASS 🔒 FROZEN
T11      ✅ PASS 🔒 FROZEN
T12      ✅ PASS 🔒 FROZEN
T13 P1   ✅ COMPLETE 🔒 Adapter Runtime 已连通
T13 P2   🟡 PRECHECK COMPLETE · ⏸ BLOCKED（等待外部商业依赖）
T13 P3   ⏸ 等 T13 P2 完成
T13 P4   ⏸ 等聚合商协议
T14      ⏸ P0 终极回归
```

**P0 进度仍 12/14 完成；T13 暂停在 PRECHECK，等待外部依赖 / 人工输入**。

---

## 10 · 退出条件

**T13_PHASE2_PRECHECK 阶段完成于**：

```
T13 Phase 2 PRECHECK COMPLETE
⛔ BLOCKED — Phase 2 IMPLEMENTATION 不允许启动
⏸ 等待外部依赖 + 人工审批
```

**进入 T13 Phase 2 IMPLEMENTATION 前必须满足**：
- [ ] 9 项外部依赖至少 7 项到位（SDK / 文档 / 商户号 / 证书 / 公钥 / URL / 算法规范）
- [ ] K8s Secret + Vault 部署完成
- [ ] 人工批准本 PRECHECK 报告
- [ ] 人工确认 D3（密钥管理）+ D4（签名算法）
- [ ] T12 baseline 回归确认（62 测试仍 PASS + 66 测试 Phase 1 仍 PASS）

---

*END OF T13_PHASE2_PRECHECK_REPORT — Generated 2026-08-15 23:55 UTC+8 — BLOCKED on 9 项外部依赖*
