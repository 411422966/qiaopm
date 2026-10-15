# T13_DECISION_MATRIX

> **T13 状态：🟡 DECISION FROZEN — 等待人工决策**
> **生成日期**：2026-08-15 23:25 UTC+8
> **上游文档**：T13_PRECHECK_REPORT.md
> **计划下游**：T13_IMPLEMENTATION（待批准）

---

## 1 · Reality Baseline（🔒 不可改）

> 以下结论仅来自 T01-T12 实施后实测，禁止通过本决策冻结反推修改。

### 1.1 接口与基类（可复用资产 · 0 改动）

| 文件 | 行数 | 状态 | 可复用 |
|---|---|---|---|
| `dcep/DcepChannelAdapter.java` | 173 | 完整接口 | ✅ 5 method + 5 DTO + Builder 已定义 |
| `dcep/AbstractDcepAdapter.java` | 137 | 工具基类 | ✅ 5 helper：computeSign/parseJson/mapError/isTimestampValid/failPrepay |
| `dcep/DcepAdapterFactory.java` | 49 | 自动注入 | ✅ channelCode → adapter 路由 |

**结论**：任何 Route 都不需要修改上述 3 个文件。

### 1.2 三个银行 Adapter（占位 · 待 Route 决策后实施）

| 类 | channelCode | 5 method 状态 |
|---|---|---|
| `CcbDcepAdapter` | `DCEP-CCB` | ❌ 全 NOT_IMPLEMENTED |
| `AbcDcepAdapter` | `DCEP-ABC` | ❌ 全 NOT_IMPLEMENTED |
| `IcbcDcepAdapter` | `DCEP-ICBC` | ❌ 全 NOT_IMPLEMENTED |

**合计**：**15 处** NOT_IMPLEMENTED（5 method × 3 adapter）

### 1.3 DcepService 路径不连通（🔴 关键缺口）

| 调用方 | 是否调 Adapter | 备注 |
|---|---|---|
| `DcepService.prepay() → mockPrepay()` | ❌ | 走私有简化逻辑，不调 `dcepAdapterFactory.getAdapter().prepay()` |
| `DcepService.notify() → verifySign()` | ❌ | 走私有 SHA-256 验签，不调 `adapter.parseAndVerifyNotify()` |
| `DcepService.refundOrder()` | ❌ | 直接 update status=4 |
| `DcepService.closeOrder()` | ❌ | 直接 update status=3 |
| `DcepService.healthCheck()` | ❌ | 走 `pingHealth()` URL 前缀检查 |

**架构空转**：Adapter 资产完整但被业务层旁路，**任何 Route 都必须连同 DcepService 主体改造一起做**。

### 1.4 T12 安全边界（🔒 本决策冻结不破）

| 边界 | 状态 | 与本决策的关系 |
|---|---|---|
| `app.dcep.mode` 强制 ∈ {dev, prod, sandbox} | 🔒 | **不破** |
| dev 模式 `verifySign` 仅接受 MOCK- 前缀 | 🔒 | **不破** |
| `pay_dcep_channel.env_mode` CHECK ∈ {MOCK, SANDBOX, PROD} | 🔒 | **不破** |
| `dcepRuntimeReady` 启动能力检查 | 🔒 | **不破** —— Route A/B/D 实施后此检查会因 adapter 不再全 NOT_IMPLEMENTED 而**自然升级**到 runtime=true |
| 26 行 `env_mode=MOCK` 通道（含历史 21 行修复） | 🔒 | **不破** |

---

## 2 · 四种 Route 比较

### Route A · CCB 单银行 POC

#### 范围

- ✅ 仅接入 **建设银行（CCB）DCEP**
- ❌ ABC / ICBC 保持占位 NOT_IMPLEMENTED
- ✅ DcepService 主体重构走 adapter（CCB 分支）
- ✅ pay_dcep_channel 新增 1 条 `env_mode=SANDBOX` / `env_mode=PROD` 的 CCB 通道

#### 修改文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `dcep/impl/CcbDcepAdapter.java` | **真实实现 5 method** |
| 2 | `service/DcepService.java` | **重构 mockPrepay/verifySign/refund/close/healthCheck 走 adapter** |
| 3 | `dcep/sign/CcbSigner.java` | **新增**（RSA/SM2 选择） |
| 4 | `application.yml` | 新增 `app.dcep.ccb.{api-base-url, sign-algo, public-key-path}` |
| 5 | `entity/PayDcepChannel.java` | 可选扩展字段（rsa_sign_algorithm / client_cert_path） |
| 6 | 新增 SQL 迁移（若扩展实体） | ALTER TABLE |
| 7 | `test/.../DcepCcbSandboxIntegrationTest.java` | **新增**（需银行沙箱环境） |
| 8 | `docs/audit/BEAUTY_SAAS/T13_COMPLETION_REPORT.md` | 新增 |

#### 时间 / 风险 / 后续扩展

| 维度 | 内容 |
|---|---|
| **商业周期** | 半年（开户 4-6 月 + 联调 1-2 月） |
| **监管要求** | 央行 DCEP 运营机构资质 + CCB 协议 |
| **工程量** | 🟡 中（5 method × 1 adapter + DcepService 主体改造） |
| **失败模式** | 🔴 单点失败 —— CCB 一家停服，T13 不可用 |
| **后续扩展** | 可继续 Route B 接聚合作为 ABC/ICBC 回退；或直接接 ABC/ICBC 单银行 |

#### T12 兼容

- ✅ 启动能力检查保留 —— adapter.probe() 不再全 NOT_IMPLEMENTED → runtime=true
- ✅ Mode 隔离 / 签名隔离 / CHECK 约束全部保留
- ⚠️ verifySign dev 路径不变（仍仅接受 MOCK- 前缀），仅 prod 路径走 adapter 公钥验签

#### 工程复杂度

```
CCB 实体接口成熟度  ★★★☆☆
DcepService 重构     ★★☆☆☆（仅添加 adapter 路由分支）
商业资质              ★★★★★
```

---

### Route B · 聚合支付 Adapter

#### 范围

- ✅ 接入 1 家**聚合支付商**（银联商务 / 通联支付 / 汇付天下）
- ✅ 聚合商作为统一前置，对接 CCB / ABC / ICBC 三家银行 DCEP
- ✅ DcepService 主体重构走 adapter（聚合 branch）
- ❌ 不直接对接 DCEP 银行 SDK（依赖聚合商）

#### Adapter 抽象是否复用

**完全可复用 DcepChannelAdapter 接口** —— 聚合商本身暴露一个类似 5-method 的统一 API（prepay / query / refund / close / notify），完全对应 DcepChannelAdapter 设计。**实现只需要 1 个新 adapter**：

```java
@Component("aggregatorDcepAdapter")
public class AggregatorDcepAdapter extends AbstractDcepAdapter {
    public String channelCode() { return "DCEP-AGGREGATOR"; }
    // 5 method 全部映射到聚合商 API
}
```

#### 修改文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `dcep/impl/AggregatorDcepAdapter.java` | **新增**（聚合商 SDK 包装） |
| 2 | `service/DcepService.java` | **重构走 adapter**（与 Route A 同性质） |
| 3 | `dcep/sign/AggregatorSigner.java` | **新增** |
| 4 | `application.yml` | 新增 `app.dcep.aggregator.{api-base-url, sign-algo}` |
| 5 | `test/.../DcepAggregatorSandboxTest.java` | **新增**（聚合商沙箱） |
| 6 | `docs/audit/BEAUTY_SAAS/T13_COMPLETION_REPORT.md` | 新增 |

**注：CCB/ABC/ICBC 三个原始 Adapter 可保持 NOT_IMPLEMENTED —— Route B 完全旁路它们**

#### 第三方依赖 / 合规风险

| 维度 | 内容 |
|---|---|
| **商业周期** | 2-4 月（聚合商签约 + API 接入） |
| **监管要求** | 聚合商承担央行合规，我方责任较小 |
| **聚合商风险** | 🟡 聚合商本身未对接 DCEP（技术可行性待评估） |
| **费率风险** | 🟡 聚合商加收 0.1-0.3% 通道费 |
| **数据主权** | 🔴 交易数据过聚合商（敏感） |
| **单点依赖** | 🔴 聚合商停服 = T13 不可用 |

#### 后续扩展

- 切换聚合商 → 仅需替换 Adapter 实现（Factory 自动路由）
- 接入第二家聚合商 → 新增 Adapter Bean（增强可用性）

#### 工程复杂度

```
聚合商 SDK 接入    ★★★☆☆
DcepService 重构   ★★☆☆☆
商业资质           ★★☆☆☆（聚合商承担）
```

---

### Route C · 保持 Demo-only

#### 范围

- ❌ 不接入任何真实银行 SDK
- ✅ 维持当前 Adapter 占位 NOT_IMPLEMENTED
- ✅ DcepService mockPrepay 不变（继续私有简化逻辑）
- ✅ T12 全部隔离规则保留

#### 当前能力

- ✅ dev 模式 + MOCK 通道 → 完整 demo 闭环（mockPrepay 返回 OK + qrCode）
- ✅ dev 模式 + 真 SHA-256 签名 → fail-fast（T12 收紧）
- ✅ prod 模式 → adapter 全部 NOT_IMPLEMENTED，runtime=false，log ERROR
- ❌ **生产 DCEP 支付 100% 不可用**

#### 不可生产原因

| 失败项 | 影响 |
|---|---|
| 真银行 API 调用 | 无 → 所有 prepay 走 mockPrepay fail |
| 真实签名验签 | 无 → verifySign 仅认 MOCK- 前缀 |
| 真实退款 | 无 → refundOrder 仅 update status |
| 真实关闭 | 无 → closeOrder 仅 update status |
| 央行合规 | 无 → 商户号未申请 |

**生产回归 T14 必失败**，与 P0 终极目标冲突。

#### 适用阶段

- ✅ 本地开发 / 演示 / CI 单测
- ✅ 投资人 demo
- ❌ **不可生产**

#### 工程复杂度

```
工程量    0
商业资质  0
监管      0
```

#### ⚠️ 决策限制

若选 Route C：P0 进度冻结在 **12/14（86%）**，T14 终极回归**无法完成**。**这会打破 P0 整体验收**。

---

### Route D · 多渠道支付 Runtime

#### 范围

- ✅ 完整支付中间件：CCB / ABC / ICBC / 微信 / 支付宝 / 聚合商**任选**
- ✅ DcepService 升级为通用 **PaymentGatewayService**（不只 DCEP）
- ✅ Adapter Factory + Channel Code 路由 + 运行时策略选择

#### 长期架构

```
PaymentGatewayService          ← PaymentGatewayController
    |
PaymentGatewayFactory          ← by channelCode
    |
PaymentChannelAdapter          ← 抽象接口
    |
├── DcepChannelAdapter         ← DCEP 5 method
├── WechatChannelAdapter       ← 微信支付
├── AlipayChannelAdapter       ← 支付宝
├── AggregatorChannelAdapter   ← 聚合商
└── ...
```

#### 修改文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `service/PaymentGatewayService.java` | **新增**（DcepService 升级为通用） |
| 2 | `controller/PaymentGatewayController.java` | **新增** |
| 3 | `payment/PaymentChannelAdapter.java` | **新增**（比 DcepChannelAdapter 更宽） |
| 4 | `dcep/impl/{Ccb,Abc,Icbc}DcepAdapter.java` | 真实实现 |
| 5 | `wechat/impl/WechatChannelAdapter.java` | 新增 |
| 6 | `alipay/impl/AlipayChannelAdapter.java` | 新增 |
| 7 | `aggregator/...` | 新增 |
| 8 | `application.yml` | 大幅扩展 |
| 9 | `docs/...` | 新增 |

#### 成本 / 收益

| 维度 | 内容 |
|---|---|
| **商业周期** | 1-2 年（多渠道协议 + 多 SDK 接入 + 多沙箱验证） |
| **工程量** | 🔴 大（6+ adapter × 5 method + 通用服务） |
| **风险** | 🟡 中（每个渠道独立风险；可降级切换） |
| **收益** | 🟢 **长期**：支付无关性 + 业务可降级 + 第三方聚合优势 |
| **不破坏** | 🟢 与 DCEP 设计不冲突（DcepChannelAdapter 是 PaymentChannelAdapter 子集） |

#### T12 兼容

- ✅ 完全兼容（T12 隔离规则保留）
- ✅ Adapter 抽象可平滑扩展
- ⚠️ 但**规模远超 T13 范围**，需拆分为独立 P1 任务

---

## 3 · Route 对比矩阵

| 维度 | A: CCB POC | B: 聚合前置 | C: Demo-only | D: 多渠道 |
|---|---|---|---|---|
| **商业资源** | CCB 协议 | 聚合商协议 | 无 | 多渠道协议 |
| **监管资质** | 🟡 央行 | 🟢 聚合商承担 | 🟢 无 | 🟡 央行 |
| **上线周期** | 🟡 半年 | 🟢 2-4 月 | 🟢 即时 | 🔴 1-2 年 |
| **单点风险** | 🔴 单银行 | 🔴 聚合商 | 🔴 **业务不可生产** | 🟢 可降级 |
| **T13 工程量** | 🟡 中（1 adapter） | 🟡 中（1 聚合 adapter） | 🟢 0 | 🔴 大（6+ adapters） |
| **生产 DCEP** | 🟢 CCB only | 🟢 多银行（聚合依赖） | 🔴 0% | 🟢 全银行 |
| **P0 终结可行性** | 🟢 可终结 | 🟢 可终结 | 🔴 不可终结 | 🟢 可终结 |
| **QiaoLifeOS 长期性** | 🟡 单渠道 | 🟡 单聚合 | 🔴 无支付 | 🟢 平台级 |
| **data sovereignty** | 🟢 自接 | 🔴 过聚合 | 🟢 无 | 🟡 取决于渠道 |

### 评分（★ 1-5，越高越好）

| 维度 | A | B | C | D |
|---|---|---|---|---|
| 上线速度 | ★★☆☆☆ | ★★★★☆ | ★★★★★ | ★☆☆☆☆ |
| 商业可行性 | ★★★☆☆ | ★★★★☆ | ★☆☆☆☆ | ★★★☆☆ |
| 单点风险 | ★★☆☆☆ | ★★☆☆☆ | ☆☆☆☆☆ | ★★★★★ |
| 长期可扩展 | ★★★☆☆ | ★★★★☆ | ☆☆☆☆☆ | ★★★★★ |
| 监管路径清晰 | ★★★☆☆ | ★★★★★ | ★★★★★ | ★★★☆☆ |
| T13 范围适配 | ★★★★★ | ★★★★★ | ★★★★★ | ★★☆☆☆ |
| **综合** | **★★★☆☆** | **★★★★☆** | **★☆☆☆☆** | **★★★☆☆** |

---

## 4 · 推荐架构（冻结）

> 基于 QiaoLifeOS 当前架构 + T12 边界 + 工程可行性的推荐。

### 4.1 推荐方案

**Route A（CCB POC）作为 T13 落地首选**，**Route B（聚合）作为 Route A 的 1-2 月内回退补充**：

```
Tier 1：Route A — 主路径（建行真实接入）
Tier 2：Route B — 旁路（聚合支付商，作为 CCB 异常时回退）
Tier 3：Route C — dev/demo 阶段保留（永远可用）
```

**理由**：
1. **满足 T14 终极回归**（须有真实支付路径）
2. **P0 终结**（12/14 → 13/14）
3. **T12 兼容**（不破 mode isolation / signature boundary）
4. **可降级**（聚合回退 + demo fallback 始终存在）
5. **可扩展**（未来 Route D 多渠道已设计好接口抽象）

### 4.2 推荐架构图（T13 实施后，不修改本图定义）

```
┌────────────────────────────────────────────────────────────────┐
│ Frontend / Customer-facing App                                  │
│   PayDcepChannel.createChannel() → admin UI → /pay/dcep/prepay │
└────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│ DcepService (controller + service)             ← T12: mode 隔离 │
│   ├─ prepay()       ──┐                                         │
│   ├─ notify()       ──┤  ALL routes now dispatch via           │
│   ├─ refundOrder()  ──┤  dcepAdapterFactory.getAdapter()        │
│   ├─ closeOrder()   ──┤                                         │
│   └─ healthCheck()  ──┘                                         │
└────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│ DcepAdapterFactory                ← 现有不动                     │
│   getAdapter(channelCode) → DcepChannelAdapter                  │
└────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────┐
│ DcepChannelAdapter (interface)     ← 现有不动                     │
│   ├─ prepay() / query() / refund() / close() / parseAndVerify() │
│   └─ channelCode()                                                   │
└────────────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ CcbDcepAdapter│    │AbcDcepAdapter│    │IcbcDcepAdapter│  ← Route A
│  (Route A)    │    │  (Route B?)  │    │  (Route B?)  │
│  CCB SDK 真实 │    │ 占位/聚合转接│    │ 占位/聚合转接│
└──────────────┘    └──────────────┘    └──────────────┘
        │                   │                   │
        ▼                   ▼                   ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  CCB Real SDK │    │ Aggregator   │    │ Aggregator   │  ← Route B
│  NWXM13/14   │    │  ApiGW       │    │  ApiGW       │
└──────────────┘    └──────────────┘    └──────────────┘

┌────────────────────────────────────────────────────────────────┐
│ ABSTRACT DCEP ADAPTER (基类 · 现成)                            │
│   - computeSign(SHA-256)  ← T12 fixture                        │
│   - parseJson / mapError / isTimestampValid / failPrepay       │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│ SECURITY BOUNDARY (T12 🔒)                                      │
│   - app.dcep.mode ∈ {dev, prod, sandbox}                       │
│   - pay_dcep_channel.env_mode ∈ {MOCK, SANDBOX, PROD}          │
│   - verifySign dev 仅接受 MOCK- 前缀                            │
│   - dcepRuntimeReady 启动能力检查（失败不阻断）                  │
└────────────────────────────────────────────────────────────────┘
```

### 4.3 不破 T12 的具体保证

| T12 边界 | 推荐方案下保留机制 |
|---|---|
| `app.dcep.mode` 校验 | T12 `@PostConstruct` 不动 ✅ |
| `verifySign` dev 收紧 | DcepService 中 dev 分支不动 ✅ |
| `pay_dcep_channel.env_mode` CHECK | T12 SQL 迁移不动 ✅ |
| `dcepRuntimeReady` | T12 `validateRuntimeCapability()` 不动；Route A/B/D 实施后自动升级 ✅ |
| Notify 路径 `MOCK-` 前缀 + 商户 fake call | DcepService.notify() dev 分支不动 ✅ |

### 4.4 DcepService 改造最小必要范围

| 方法 | 当前实现 | T13 后实现 |
|---|---|---|
| `prepay()` → `mockPrepay()` | 私有返回 OK/FAIL | 调用 `adapter.prepay()` |
| `notify()` → `verifySign()` | 私有 SHA-256 验签 | dev: 仍走私有; prod: 走 `adapter.parseAndVerifyNotify()` |
| `refundOrder()` | 直接 update status=4 | 调用 `adapter.refund()` |
| `closeOrder()` | 直接 update status=3 | 调用 `adapter.closeOrder()` |
| `healthCheck()` | URL 前缀检查 | 调用 `adapter.healthCheck()`（如有） 或保留 URL 检查 |

**最小改造 = 仅在现有方法体内"分三路"增加 `if (channel.getEnvMode().equals("MOCK")) { 旧逻辑 } else { adapter.x() }`**。可分阶段提交。

---

## 5 · 人工决策表

> ⚠️ 5 个 Decision 全部需人工确认。任何一项缺失，T13 实施必然 BLOCKED。

| Decision | Options | Recommendation | 决策 |
|---|---|---|---|
| **D1：支付路线** | A（CCB POC）/ B（聚合前置）/ C（Demo-only）/ D（多渠道） | **A+B 分层**：A 主路径 + B 聚合回退 | 🟡 **待人工** |
| **D2：商业周期** | 半年 / 2-4 月 / 即时 / 1-2 年 | 与 D1 绑定；选 A 则接受半年；选 B 则 2-4 月 | 🟡 **待人工** |
| **D3：密钥管理** | K8s Secret / Vault / KMS / JCEKS / DB 加密列 | **K8s Secret + Vault（双层）**：RBAC 控制 + 加密静态 | 🟡 **待人工** |
| **D4：签名算法** | RSA-2048 only / SM2 only / 双算法（RSA + SM2） | **双算法（RSA + SM2）**：国际兼容 + 国密合规 | 🟡 **待人工** |
| **D5：Provider 选择** | 建行（CCB）/ 农行（ABC）/ 工行（ICBC）/ 聚合商 / 微信+支付宝 | **A 决策下：CCB（B 不限）** | 🟡 **待人工** |

### 5.1 决策依赖

```
D1 (路线) ──┐
            ├─→ D2 (周期) ──→ D5 (具体 provider)
D3 (密钥) ←─┤
D4 (算法) ←─┘
D5 由 D1+D2 决定，剩余正交
```

### 5.2 最小决策组合（推荐）

| Combination | 含 D1-D5 | 风险 |
|---|---|---|
| **MIN-RISK** | A + 半年 + K8s Secret/Vault + RSA only + CCB | 🟢 单银行 + 单一算法，简化接入 |
| **BALANCED（推荐）** | A+B（主+回退）+ 半年/4 月（并行）+ K8s Secret/Vault + RSA+SM2 双算法 + CCB 主 + 聚合 | 🟡 双轨工程量 + 全合规 |
| **AGGRESSIVE** | D + 1.5 年 + Vault + SM2 only + 多渠道 | 🟢 最长期但最慢 |
| **PASSIVE** | C + 0 + N/A + N/A + N/A | 🔴 P0 不可终结 |

---

## 6 · T13 实施前置条件检查（CHECKLIST）

进入 T13 IMPLEMENTATION 前，必须满足：

- [ ] **D1-D5 全部人工决策确认**（不可空缺）
- [ ] **商业协议状态确认**（CCB / 聚合商哪家）
- [ ] **银行沙箱环境凭据**（API base URL + 商户号占位 + 测试证书）
- [ ] **DcepService 主体改造计划批准**（mockPrepay 走 adapter）
- [ ] **T12 62 测试 baseline 锁定**（实施前 re-run 验证 T12 仍 PASS）

---

## 7 · 明确禁止修改

**T13 决策冻结阶段禁止**：

| 类别 | 禁止 |
|---|---|
| ❌ **DcepService.java** | 完全不动——直到 T13 IMPLEMENTATION 决策批准 |
| ❌ **Adapter 实现** | Ccb/Abc/Icbc 三个 impl 保持 NOT_IMPLEMENTED |
| ❌ **database** | 不增删表/字段/约束 |
| ❌ **application.yml** | 不改 app.dcep.* 配置 |
| ❌ **tests** | T12 的 6 个测试不动，不新增测试 |
| ❌ **frontend** | meiyetec-web 完全不动 |
| ❌ **DDL/SQL 迁移** | 不新增 |
| ❌ **PayDcepChannel 实体** | 不扩展字段 |

**允许操作**（决策矩阵本身）：

- ✅ 仅生成 / 修改本文件 `T13_DECISION_MATRIX.md`

---

## 8 · 当前 P0 状态

```
T01-T10  ✅ PASS 🔒 FROZEN
T11      ✅ PASS 🔒 FROZEN
T12      ✅ PASS 🔒 FROZEN
T13      🟡 BLOCKED — DECISION MATRIX FROZEN（待人工决策 D1-D5）
─────────────────────────
P0 进度：12/14 完成（86%）
```

---

## 9 · 退出条件

**T13 DECISION MATRIX 阶段完成于**：

```
T13 DECISION MATRIX COMPLETE
⏸️  STOPPED — 等待人工决策 D1-D5
```

**不进入 T13 IMPLEMENTATION 直到人工批准 D1-D5**。

---

*END OF T13_DECISION_MATRIX — Generated 2026-08-15 23:25 UTC+8 — DECISION FROZEN*