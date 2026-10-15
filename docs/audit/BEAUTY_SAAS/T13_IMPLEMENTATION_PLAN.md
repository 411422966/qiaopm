# T13_IMPLEMENTATION_PLAN

> **T13 状态：🟡 IMPLEMENTATION PLAN — 等待人工批准**
> **生成日期**：2026-08-15 23:31 UTC+8
> **上游文档**：T13_DECISION_MATRIX.md（已批准 A+B 分层）
> **计划下游**：T13 IMPLEMENTATION → T13_COMPLETION_REPORT → T14 FINAL REGRESSION

---

## 0 · 批准的基础架构方向

**已批准**：

> **A+B 分层方案**：Dcep Runtime 从 Demo-only 升级为 Production-ready Architecture

**目标架构**：

```
DcepService
      │
      │  (DcepService 主体改造，走 adapter 路径)
      ▼
DcepAdapterFactory
      │
      │  (Factory 不动，按 channelCode 路由)
      ▼
DcepChannelAdapter  (interface · 现有不动)
      │
      ├── CcbDcepAdapter          → CCB SDK 真实接入（Route A 主路径）
      ├── AggregatorDcepAdapter   → 聚合支付商（Route B 回退）
      └── Future Providers        → 扩展点（Route D 长期）
```

**T12 安全边界（🔒 不可改变）**：

| 边界 | 保留机制 |
|---|---|
| `app.dcep.mode` | dev / prod / sandbox 三选一；非法值抛 `BeanInitializationException` |
| `verifySign` dev 分支 | **仅接受 MOCK- 前缀**；真银行签名 fail-fast（T12 🔒） |
| `pay_dcep_channel.env_mode` CHECK | MOCK / SANDBOX / PROD；违规 INSERT/UPDATE 拒绝 |
| `dcepRuntimeReady` | 启动能力检查保留；Route A/B 实施后自动升级到 runtime=true |
| 历史 21 行 dev→MOCK 修复 | 不回滚；0 行 env_mode='dev' |

---

## 1 · Architecture Delta（架构增量）

### 1.1 当前状态（Demo-only）

```
DcepService.prepay()    → mockPrepay()   私有方法：MOCK通道直接返回OK，非MOCK返回FAIL
DcepService.notify()    → verifySign()   私有方法：dev接受SHA-256，prod拒绝MOCK-
DcepService.refund()    → UPDATE status=4  直接DB写，不调adapter
DcepService.close()     → UPDATE status=3  直接DB写，不调adapter
DcepService.healthCheck() → URL prefix    不调adapter
```

**Adapter 资产完全空转**（虽然接口 + 基类 + 工厂已就绪）。

### 1.2 目标状态（Production-ready）

```
DcepService.prepay()    → 路由：MOCK分支走旧逻辑 / SANDBOX+PROD分支走adapter.prepay()
DcepService.notify()    → 路由：dev走verifySign()/prod走adapter.parseAndVerifyNotify()
DcepService.refund()    → adapter.refund()  (含幂等)
DcepService.close()     → adapter.closeOrder()
DcepService.healthCheck() → adapter.probe()  (或保留URL检查)
DcepService.query()      → adapter.query()   (新增 /order/{id}/query)
```

### 1.3 职责迁移表

| 职责 | 当前持有者 | 目标持有者 | 迁移性质 |
|---|---|---|---|
| prepay 路由 | `mockPrepay()` 私有方法 | `adapter.prepay()` | **重构**（保留 MOCK 分支兼容） |
| notify 验签 | `verifySign()` 私有方法 | `adapter.parseAndVerifyNotify()` | **重构**（保留 dev MOCK- 分支） |
| refund 执行 | 直接 UPDATE status=4 | `adapter.refund()` | **新增真实调用** |
| close 执行 | 直接 UPDATE status=3 | `adapter.closeOrder()` | **新增真实调用** |
| query 状态 | 无 | `adapter.query()` | **新增端点** |
| health check | URL 前缀检查 | adapter probe + URL 检查 | **扩展** |
| 幂等控制 | notifyId 缓存 | 继承 + adapter 层重放防护 | **增强** |
| 错误码映射 | 私有 mapError | `AbstractDcepAdapter.mapError()` | **复用现有** |

---

## 2 · 文件影响分析

| 文件 | 当前状态 | 计划修改 | 修改类型 |
|---|---|---|---|
| `service/DcepService.java` | mock path + 私有 verifySign | **重构 prepay/notify/refund/close 走 adapter**；保留 dev MOCK- 分支 | 重构 |
| `dcep/DcepAdapterFactory.java` | 自动注入，channelCode 路由 | **不动**（已就绪） | 无 |
| `dcep/DcepChannelAdapter.java` | 完整接口，5 method | **不动**（向后兼容） | 无 |
| `dcep/AbstractDcepAdapter.java` | 5 helper | **不动**（基类复用） | 无 |
| `dcep/impl/CcbDcepAdapter.java` | 15 NOT_IMPLEMENTED | **5 method 真实 CCB SDK 实现** | 实现 |
| `dcep/impl/AbcDcepAdapter.java` | 15 NOT_IMPLEMENTED | **不动**（Route A 范围外） | 无 |
| `dcep/impl/IcbcDcepAdapter.java` | 15 NOT_IMPLEMENTED | **不动**（Route A 范围外） | 无 |
| `dcep/impl/AggregatorDcepAdapter.java` | **不存在** | **新增**（Route B 聚合支付包装） | 新增 |
| `dcep/sign/DcepSigner.java` | **不存在** | **新增**（SignatureProvider 策略接口） | 新增 |
| `dcep/sign/RsaSigner.java` | **不存在** | **新增**（RSA-2048 实现） | 新增 |
| `dcep/sign/Sm2Signer.java` | **不存在** | **新增**（SM2/SM3 国密实现） | 新增 |
| `dcep/sign/SignatureProvider.java` | **不存在** | **新增**（策略工厂） | 新增 |
| `entity/PayDcepChannel.java` | 现有字段 | **可选扩展**：rsa_sign_algorithm / client_cert_path（需审批） | 可选 |
| `application.yml` | 现有 app.dcep.mode | **新增** `app.dcep.ccb.{api-url,sign-algo}` / `app.dcep.aggregator.{api-url}` | 新增配置 |
| `PayDcepOrder.java` | 现有 | **不动** | 无 |
| `PayDcepNotify.java` | 现有 | **不动** | 无 |
| `config/DcepConfigProperties.java` | T12 🔒 | **不动**（mode 校验保留） | 无 |
| `config/DcepChannelProperties.java` | **不存在** | **可选新增**（每个 channel 的密钥路径配置） | 可选 |

**T01-T12 🔒 完全不动**：entity/Booking/PosOrder/Wallet/所有 T01-T11 业务代码

### 2.1 DcepService 改造明细

**原则**：MOCK 分支完整保留（dev demo 路径不变）；SANDBOX/PROD 分支走 adapter。

```java
// prepay() 改造策略（伪代码）
@PostMapping("/prepay")
public Result<Map<String,Object>> prepay(...) {
    PayDcepChannel channel = ...;

    // === T12 行为保留：MOCK 通道永远走 mockPrepay ===
    if ("MOCK".equalsIgnoreCase(channel.getEnvMode())) {
        return Result.success(mockPrepay(channel, order));  // 现有代码不动
    }

    // === T13 新增：SANDBOX / PROD 走 adapter ===
    DcepChannelAdapter adapter = dcepAdapterFactory.getAdapter(channel.getChannelCode());
    PrepayRequest req = PrepayRequest.builder()
        .merchantId(channel.getMerchantId())
        .appId(channel.getAppId())
        .privateKeyPath(channel.getPrivateKeyPath())
        .bankPublicKey(channel.getPublicKeyPath())
        .channelCode(channel.getChannelCode())
        .dcepOrderNo(order.getDcepOrderNo())
        .amountCents(order.getAmountCents())
        .notifyUrl(channel.getNotifyUrl())
        .envMode(channel.getEnvMode())
        ...build();
    PrepayResult result = adapter.prepay(req);
    return result.isSuccess() ? Result.success(toMap(result)) : Result.error(...);
}

// notify() 改造策略（伪代码）
@PostMapping("/notify/{channelCode}")
public Result<Map<String,Object>> notify(...) {
    // === T12 行为保留：dev 走 verifySign（仅认 MOCK- 前缀）===
    if (dcepConfig.isMockEnabled()) {
        boolean signOk = verifySign(channelCode, payload, sign);  // 现有代码不动
        if (!signOk) return Result.error(403, "invalid sign");
        // ... 处理逻辑不动 ...
        return Result.success(...);
    }

    // === T13 新增：prod 走 adapter.parseAndVerifyNotify() ===
    DcepChannelAdapter adapter = dcepAdapterFactory.getAdapter(channelCode);
    NotifyResult nr = adapter.parseAndVerifyNotify(rawBody, headers);
    if (!nr.isSignatureValid()) return Result.error(403, "invalid sign");
    // ... 处理逻辑不动（订单更新逻辑保留）...
    return Result.success(...);
}
```

---

## 3 · 数据模型影响

### 3.1 数据库 Schema

**结论：不需要 Schema 变更（No Database Schema Change）**

理由：
- `pay_dcep_channel` 现有字段（merchantId / appId / appSecret / privateKeyPath / publicKeyPath / apiBaseUrl / notifyUrl）已够用
- 新增密钥管理通过 `application.yml` 或 K8s Secret（不落库）
- `pay_dcep_order` / `pay_dcep_notify` 现有字段足够

### 3.2 可选字段扩展（需人工批准）

| 字段 | 表 | 类型 | 必要性 | 状态 |
|---|---|---|---|---|
| rsa_sign_algorithm | pay_dcep_channel | VARCHAR(16) | 可选（默认 RSA-SHA256） | **需 D4 决策后批准** |
| client_cert_path | pay_dcep_channel | VARCHAR(255) | 可选（CCB .pfx 证书路径） | **需 D3 决策后批准** |
| channel_cert_serial | pay_dcep_channel | VARCHAR(64) | 可选 | **需 D3 决策后批准** |

**默认方案**：通过 `application.yml` 或 `DcepChannelProperties.java` 配置，不改 schema。

### 3.3 新增测试数据

| 数据 | 位置 | 性质 |
|---|---|---|
| CCB sandbox 通道配置 | application.yml（test profile） | 测试配置，不入生产 DB |
| Aggregator sandbox 通道 | application.yml（test profile） | 测试配置 |
| 无新增 DB 表 | — | — |

---

## 4 · Security Design

### 4.1 密钥管理方案比较

| 方案 | 优点 | 缺点 | 推荐 |
|---|---|---|---|
| **K8s Secret** | 原生集成；RBAC 控制；加密静态 | 仅 K8s 环境可用；运维复杂度 | ✅ **推荐** |
| **Vault (HashiCorp)** | 集中密钥管理；审计日志；动态密钥 | 额外基础设施；学习成本 | ✅ **推荐（K8s Secret + Vault 双层）** |
| **AWS KMS / Azure Key Vault** | 云原生；HSM 支持 | 云厂商锁定 | 中（若已用云） |
| **JCEKS Keystore** | Java 原生；简单 | 文件系统依赖；无 RBAC；轮换困难 | ❌ 不推荐 |

### 4.2 推荐密钥管理设计

```
密钥层级：
  Tier 1: K8s Secret (namespace 隔离，etcd 加密)
    │
    └── Vault (审计日志 + 动态密钥 + RBAC)
           │
           ├── CCB merchant private key  → CcbDcepAdapter
           ├── CCB client certificate    → CcbDcepAdapter
           ├── Aggregator API key       → AggregatorDcepAdapter
           └── Bank public keys (RO)    → 所有 adapter

禁止：
  ❌ 私钥 / 证书明文落库
  ❌ 私钥写进 application.yml
  ❌ 私钥提交到 Git
```

### 4.3 签名算法设计

**决策 D4**：RSA-2048 + SM2 双算法（推荐）

```
SignatureProvider (interface)
    │
    ├── RsaSigner    implements SignatureProvider
    └── Sm2Signer    implements SignatureProvider

工厂：
SignatureProviderFactory
  .forAlgorithm("RSA-SHA256")  → RsaSigner
  .forAlgorithm("SM2-SM3")    → Sm2Signer

使用位置：
  - CcbDcepAdapter.prepay()      → 调用签名
  - CcbDcepAdapter.notify()      → 调用验签
  - AbstractDcepAdapter.computeSign() → 保留（SHA-256 工具）
```

**现有代码兼容性**：
- `AbstractDcepAdapter.computeSign()` 保留（SHA-256 工具，供非银行场景使用）
- `DcepService.verifySign()` 保留（dev MOCK- 分支；prod 改走 adapter）

### 4.4 回调安全

| 威胁 | 防护措施 |
|---|---|
| 签名伪造 | `adapter.parseAndVerifyNotify()` + 银行公钥验签 |
| 时间戳重放 | `AbstractDcepAdapter.isTimestampValid()`（±5 分钟） |
| notifyId 重放 | `pay_dcep_notify.notify_id UNIQUE` 约束（已存在）+ Redis TTL 30天 |
| IP 伪造 | **新增**：bank_ip_whitelist 配置；生产环境校验回调来源 IP |
| 金额篡改 | 业务层校验 `notify.amountCents == order.amountCents`（已有逻辑） |

---

## 5 · Provider Adapter 设计

### 5.1 DcepProviderAdapter 能力接口

基于现有 `DcepChannelAdapter` 接口，**无需新增接口文件**：

```java
// 复用 DcepChannelAdapter（现有不动）
public interface DcepChannelAdapter {
    PrepayResult  prepay(PrepayRequest req);
    QueryResult   query(OrderQueryRequest req);
    RefundResult  refund(RefundRequest req);
    CloseResult   closeOrder(CloseRequest req);
    NotifyResult  parseAndVerifyNotify(String rawBody, Map<String,String> headers);
    String        channelCode();  // 路由键
}
```

### 5.2 CcbDcepAdapter 实现（Route A）

**5 个 method 实现策略**：

| method | CCB API | 签名 | 关键点 |
|---|---|---|---|
| `prepay()` | NWXM13（预填单维护） | RSA-SM2 私钥签名 | 商户号 + 钱包号 + 金额 + 超时时间 |
| `query()` | NWXM14（结果查询） | RSA-SM2 私钥签名 | 轮询策略：5min/15min/60min |
| `refund()` | CCB 退款接口 | RSA-SM2 私钥签名 | 原路退回对公钱包 |
| `closeOrder()` | NWXM15（删除预填单） | RSA-SM2 私钥签名 | 超时自动关闭 |
| `parseAndVerifyNotify()` | CCB 回调解析 | CCB 公钥验签 | 提取 eventType + transactionId + amountCents |

### 5.3 AggregatorDcepAdapter 实现（Route B）

**Route B 回退策略**：

| method | 聚合商 API | 备注 |
|---|---|---|
| `prepay()` | 聚合商统一下单 API | 商户号 + 金额 + 回调地址 |
| `query()` | 聚合商订单查询 | 按订单号查询 |
| `refund()` | 聚合商退款 API | 退款单号 + 金额 |
| `closeOrder()` | 聚合商关闭 API | 关闭预订单 |
| `parseAndVerifyNotify()` | 聚合商回调解析 | 聚合商公钥验签 |

**channelCode 路由策略**：

```java
// DcepService.prepay() 中
DcepChannelAdapter adapter = dcepAdapterFactory.getAdapter(channelCode);
// channelCode = "DCEP-CCB"      → CcbDcepAdapter
// channelCode = "DCEP-AGG"      → AggregatorDcepAdapter
// channelCode = "DCEP-ABC"      → 仍 NOT_IMPLEMENTED（待后续扩展）
```

### 5.4 Future Provider 扩展点

未来扩展无需修改现有代码：
1. 新增 `dcep/impl/NewBankDcepAdapter.java`
2. 添加 `@Component` Bean
3. `DcepAdapterFactory` 自动注册
4. `pay_dcep_channel` 新增对应 `channel_code`

---

## 6 · Testing Plan

### 6.1 单元测试（Unit）

| 测试类 | 验证内容 | 新增/修改 |
|---|---|---|
| `CcbDcepAdapterTest` | CCB SDK 调用；签名生成；错误码映射 | 新增 |
| `AggregatorDcepAdapterTest` | 聚合商 API 调用 | 新增 |
| `SignatureProviderTest` | RSA / SM2 签名 + 验签交叉验证 | 新增 |
| `DcepSignerTest` | RsaSigner / Sm2Signer 边界条件 | 新增 |
| `DcepServiceAdapterRoutingTest` | SANDBOX/PROD 路由到 adapter；MOCK 走旧逻辑 | 新增 |
| `DcepServiceDevSignTest` | dev MOCK- 分支 verifySign 不变 | 复用 T12 |
| `DcepChannelEnvModeTest` | env_mode CHECK 约束保留 | 复用 T12 |

### 6.2 集成测试（Integration）

| 测试类 | 验证内容 | 依赖 |
|---|---|---|
| `DcepCcbSandboxIntegrationTest` | CCB 沙箱 prepay → notify → 状态更新 | CCB 沙箱环境 |
| `DcepAggregatorSandboxIntegrationTest` | 聚合商沙箱完整流程 | 聚合商沙箱环境 |
| `DcepStartupValidateTest` | prod + adapter NOT_IMPLEMENTED → runtime=false | 复用 T12 |

**CI 注意事项**：
- 沙箱集成测试需 `@IfIntegrationTest` 条件注解（CI 默认跳过）
- 本地 / pre-prod 环境手动触发

### 6.3 E2E 测试

**T11 E2E 保持不动**：

```
Member → Booking → Checkin → Order → Payment → Commission
```

**T13 不修改**：
- `frontend/meiyetec-web/e2e/full-flow.spec.ts`
- `frontend/meiyetec-web/**`
- 任何 booking / order / payment 业务逻辑

**T13 新增 E2E（如需）**：
- 独立 `e2e/dcep-sandbox.spec.ts`（CCB 沙箱支付流程）
- 独立 `e2e/dcep-aggregator.spec.ts`（聚合商沙箱支付流程）
- **不修改** T11 现有 spec

---

## 7 · Migration Strategy

### Phase 1：Adapter Runtime 连通（基础设施）

**目标**：DcepService 主体改造，使 adapter 路径真正可用

**改动**：
- `DcepService.prepay()` 增加 SANDBOX/PROD → adapter 路由（MOCK 分支不动）
- `DcepService.notify()` 增加 prod → adapter 路由（dev MOCK- 分支不动）
- `DcepService.refund()` 增加 adapter 路由
- `DcepService.closeOrder()` 增加 adapter 路由
- `DcepService.query()` 新增端点

**测试**：单元测试覆盖路由逻辑

**风险**：低（新增分支，不改动现有 MOCK 逻辑）

**回滚**：注释掉 adapter 路由分支即可恢复 T12 状态

---

### Phase 2：CCB Sandbox（Route A 主路径）

**目标**：CCB 沙箱真实支付

**改动**：
- `CcbDcepAdapter` 5 method 真实实现（NWXM13/14/15 + 退款接口）
- `dcep/sign/RsaSigner.java` + `Sm2Signer.java`
- `application.yml` 增加 `app.dcep.ccb.*` 配置

**测试**：
- 单元测试
- 沙箱集成测试

**风险**：中（CCB SDK 接入未知变量）

---

### Phase 3：Production Credential（Route A 生产）

**目标**：生产环境切换

**改动**：
- `application.yml` 切换沙箱 URL → 生产 URL
- K8s Secret 注入生产密钥（不发到 Git）
- 灰度策略：先 5% 流量切 CCB

**风险**：高（真钱）

**回滚**：切回 MOCK 通道；回滚 application.yml

---

### Phase 4：Aggregator Fallback（Route B 回退）

**目标**：聚合支付商接入，作为 CCB 异常时回退

**改动**：
- `AggregatorDcepAdapter` 完整实现
- `pay_dcep_channel` 新增 `channel_code='DCEP-AGG'` 通道配置
- DcepService 路由增加 Aggregator 分支

**测试**：沙箱集成测试

**风险**：中（聚合商 API 兼容性）

---

## 8 · Risk Matrix

| 风险 | 等级 | 触发条件 | 缓解措施 |
|---|---|---|---|
| CCB SDK 接入失败 | 🟡 中 | SDK 文档缺失 / NDA 限制 | Phase 1 先做 Adapter Runtime（不依赖 SDK）；Phase 2 沙箱接入失败可回退到 Phase 1 |
| 证书配置错误 | 🟡 中 | .pfx 密码错误 / 证书过期 | Vault 审计日志；证书有效期监控告警 |
| 签名算法不匹配 | 🟡 中 | CCB 要求 SM2 但实现 RSA | Phase 2 前确认算法要求；双算法 SignatureProvider 可切换 |
| 银行回调 IP 不在白名单 | 🟡 中 | 生产网络策略未配置 | Phase 3 前提前配置 IP 白名单；回调地址可配置 |
| 生产切换后订单状态不一致 | 🔴 高 | notify 先到 / query 后到 | 幂等 + 最终一致性；订单状态以 notify 为准 |
| Aggregator 路由降级 | 🟡 中 | 聚合商 API 不可用 | Phase 1 适配器架构支持动态降级；自动切回 MOCK |
| T12 边界被意外破坏 | 🟢 低 | 误改 DcepService dev MOCK- 分支 | 单元测试 + 回归测试保护 |
| 密钥泄漏 | 🔴 高 | 私钥写入 Git / 日志 | K8s Secret + Vault；禁止私钥明文落库；日志脱敏 |
| 沙箱联调 CI 不可跑 | 🟢 低 | 银行沙箱非公开 | @IfIntegrationTest 跳过；本地手动触发 |

---

## 9 · Freeze Boundary（🔒 不可突破）

**T13 实施禁止修改**：

| 类别 | 文件/区域 | 禁止原因 |
|---|---|---|
| ❌ frontend | `meiyetec-web/**` | T11 🔒 冻结 |
| ❌ 业务链路 | `BookingServiceImpl` / `PosController` / `WalletServiceImpl` | T01-T11 🔒 冻结 |
| ❌ T12 安全规则 | `DcepConfigProperties.validate()` / `verifySign` dev MOCK- 分支 / `env_mode` CHECK 约束 / `dcepRuntimeReady` | T12 🔒 冻结 |
| ❌ 数据库 schema | `pay_dcep_order` / `pay_dcep_notify` 结构 | 无需变更 |
| ❌ DcepChannelAdapter 接口 | 接口方法签名不变 | 向后兼容 |
| ❌ DcepAdapterFactory | 自动注入逻辑不动 | 已就绪 |
| ❌ AbstractDcepAdapter helper | 基类方法不动 | 复用基础 |

**T13 可以修改**：

| 类别 | 文件/区域 | 说明 |
|---|---|---|
| ✅ DcepService 主体 | `prepay()` / `notify()` / `refundOrder()` / `closeOrder()` / 新增 `query()` | 重构走 adapter |
| ✅ CcbDcepAdapter | 5 method 真实实现 | Route A |
| ✅ AggregatorDcepAdapter | 新增 | Route B |
| ✅ dcep/sign/* | 新增 | 签名策略 |
| ✅ application.yml | 新增 `app.dcep.ccb.*` / `app.dcep.aggregator.*` | 配置 |
| ✅ 新增 entity 字段（需批准） | `pay_dcep_channel` 可选扩展 | D3/D4 决策后 |
| ✅ 新增测试 | CcbDcepAdapterTest / AggregatorDcepAdapterTest 等 | T13 范围 |
| ✅ 新增 E2E spec（独立） | `e2e/dcep-*.spec.ts`（不修改 T11） | T13 范围 |

---

## 10 · P0 状态

```
T01-T10  ✅ PASS 🔒 FROZEN
T11      ✅ PASS 🔒 FROZEN
T12      ✅ PASS 🔒 FROZEN
T13      🟡 IMPLEMENTATION PLAN — 等待人工批准
─────────────────────────
P0 进度：12/14 完成（86%）
```

---

## 11 · 退出条件

**T13 IMPLEMENTATION PLAN 阶段完成于**：

```
T13 IMPLEMENTATION PLAN COMPLETE
⏸️  STOPPED — 等待人工批准后进入 T13 IMPLEMENTATION
```

**进入 T13 IMPLEMENTATION 前必须满足**：
- [ ] 人工批准本计划（T13_IMPLEMENTATION_PLAN.md）
- [ ] 人工批准 D3（密钥管理：K8s Secret + Vault）
- [ ] 人工批准 D4（签名算法：RSA + SM2 双算法）
- [ ] T12 baseline 回归确认（62 测试仍 PASS）
- [ ] CCB 沙箱凭据就绪（或 Aggregator 沙箱凭据就绪）

---

*END OF T13_IMPLEMENTATION_PLAN — Generated 2026-08-15 23:31 UTC+8 — PLAN ONLY*