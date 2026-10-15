# T13_PRECHECK_REPORT

> **T13 状态：🟡 PRECHECK ONLY（不实施）**
> **生成日期**：2026-08-15 23:19 UTC+8
> **plan §T13 标记**：BLOCKED / DECISION REQUIRED

---

## 1 · T13 任务目标回顾

### 1.1 plan 原始定义

> T13 — 真实银行 SDK 接入（CCB / ABC / ICBC）—— **BLOCKED / DECISION REQUIRED**

**4 条候选路径**：
- **路径 A**：签约 1 家（如建行 CCB）做 POC，半年可上线
- **路径 B**：接入聚合支付平台（银联商务 / 通通联支付）作为统一前置
- **路径 C**：维持现状（dev mock）接受不可生产
- **路径 D**：与现有微信/支付宝同源（数币 API 走第三方聚合）

**plan 阻塞原因**：
- DCEP 真实接入需要**运营机构 / 央行网关资质**
- 需要**商户号 + 公私钥 + 双向证书 + 同城专线**
- 需要**与每家银行签订支付服务协议**
- CCB/ABC/ICBC 真实 SDK 是商业资源，部分需要在 NDA 下

### 1.2 与 T01-T12 关系

| Task | 与 T13 关系 |
|---|---|
| T01-T11 | 全部 🔒 FROZEN，与 T13 独立 |
| T12 | T13 的前置：已建立 dev/prod 隔离 + CHECK 约束；T13 在 T12 框架内接入真实银行 |
| **T13** | **BLOCKED — 必须先选路径** |
| T14 | P0 终极回归，T13 是其前置之一 |

**T13 是 P0 阶段唯一受外部商业 / 监管约束的任务**，不是纯工程任务。

---

## 2 · 当前 Reality 检查（仅事实，不修复）

### 2.1 DCEP Adapter 架构现状

| 文件 | 行数 | 状态 |
|---|---|---|
| `dcep/DcepChannelAdapter.java` | 173 | **接口完整**（5 method + 5 DTO + Builder） |
| `dcep/AbstractDcepAdapter.java` | 137 | **基类工具齐备**（computeSign/parseJson/mapError/isTimestampValid/failPrepay） |
| `dcep/DcepAdapterFactory.java` | 49 | **自动注入**所有 DcepChannelAdapter Bean；按 channelCode 路由 |
| `dcep/impl/CcbDcepAdapter.java` | 98 | 5 method 全部 NOT_IMPLEMENTED |
| `dcep/impl/AbcDcepAdapter.java` | 83 | 5 method 全部 NOT_IMPLEMENTED |
| `dcep/impl/IcbcDcepAdapter.java` | 85 | 5 method 全部 NOT_IMPLEMENTED |

**核心发现 — 15 处 NOT_IMPLEMENTED**（plan 描述说 "12 处"，实际含 closeOrder 是 15）：
- CcbDcepAdapter: 5 method × NOT_IMPLEMENTED
- AbcDcepAdapter: 5 method × NOT_IMPLEMENTED
- IcbcDcepAdapter: 5 method × NOT_IMPLEMENTED

### 2.2 架构连通性 — ⚠️ **关键缺口**

| 调用方 | 是否调 Adapter | 备注 |
|---|---|---|
| `DcepService.prepay()` → `mockPrepay()` | ❌ 不调 | 走自己的简化逻辑，直接返回 OK / FAIL |
| `DcepService.notify()` → `verifySign()` | ❌ 不调 | 走自己的 SHA-256 验签，不用 `adapter.parseAndVerifyNotify()` |
| `DcepService.refundOrder()` | ❌ 不调 | 直接 update status=4，不调 `adapter.refund()` |
| `DcepService.closeOrder()` | ❌ 不调 | 直接 update status=3，不调 `adapter.closeOrder()` |
| `DcepService.healthCheck()` | ❌ 不调 | 走 `pingHealth()` URL prefix 检查，不用 adapter |
| `DcepAdapterFactory.getAdapter()` | ✅ 已实现 | 但 DcepService 全程**未调用** |

**架构不连通 — T13 即使 3 个 adapter 实现完毕，DcepService 也不会自动用上**。

### 2.3 T12 后状态确认

| 项 | 状态 |
|---|---|
| `app.dcep.mode` 默认 | `dev` (application.yml) |
| `DcepConfigProperties.validate()` | ✅ 强校验 dev/prod/sandbox |
| `DcepService.verifySign` dev 模式 | ✅ 仅接受 MOCK- 前缀 |
| `pay_dcep_channel.env_mode` CHECK | ✅ ck_pay_dcep_channel_env_mode 已注册 |
| DB 中 26 行 `env_mode=MOCK` | ✅ 历史 21 行 dev 已修复为 MOCK |
| `dcepRuntimeReady` 标志 | ✅ T12 引入，prod + 全 NOT_IMPLEMENTED → log ERROR + flag=false |

### 2.4 pay_dcep_channel 实体字段

| 字段 | 现有 | T13 是否够用 |
|---|---|---|
| merchantId | ✅ | 够用 |
| appId | ✅ | 够用 |
| appSecret | ✅ | 够用 |
| privateKeyPath / publicKeyPath | ✅ | 够用 |
| apiBaseUrl / notifyUrl | ✅ | 够用 |
| envMode | ✅ | 够用 |
| **缺：rsa_sign_algorithm** | ❌ | 需扩展（RSA-SHA256/SM2-SM3） |
| **缺：client_cert_path** | ❌ | 需扩展（双向证书 .pfx） |
| **缺：channel_cert_serial** | ❌ | 需扩展（证书序列号） |
| **缺：rate_limit_per_min** | ✅ | 已存在 |

**实体字段基本够用，仅缺 3 个字段（需 schema 变更）**。

### 2.5 现行测试覆盖

| 测试 | 验证 |
|---|---|
| `DcepAdapterSkeletonTest` | 3 adapter 占位返回 NOT_IMPLEMENTED（默认安全态） |
| `DcepStartupValidateTest` (T12) | prod + 全 NOT_IMPLEMENTED → runtime=false（方案 A 不抛） |
| `DcepChannelEnvModeTest` (T12) | env_mode CHECK 约束 |

**T13 测试应覆盖**：bank sandbox 真实调用（需银行沙箱环境）。

---

## 3 · 修改范围分析

### 3.1 允许修改（方案 B/A/D 一旦决策后）

| 文件 | 修改性质 |
|---|---|
| `dcep/impl/CcbDcepAdapter.java` | 真实 CCB SDK 调用（5 method 全部实现） |
| `dcep/impl/AbcDcepAdapter.java` | 真实 ABC SDK 调用（5 method 全部实现） |
| `dcep/impl/IcbcDcepAdapter.java` | 真实 ICBC SDK 调用（5 method 全部实现） |
| `dcep/AbstractDcepAdapter.java` | **可能扩展**：RSA / SM2 签名验签；可加新方法 |
| `dcep/DcepChannelAdapter.java` | **可能扩展**：DTO 字段（rsa_sign_algorithm） |
| `service/DcepService.java` | **必须扩展**：mockPrepay 改成 `adapterFactory.getAdapter().prepay()`；verifySign 改成 `adapter.parseAndVerifyNotify()`；refundOrder/closeOrder 走 adapter |
| `entity/PayDcepChannel.java` | **可能新增字段**：rsa_sign_algorithm / client_cert_path / channel_cert_serial |
| 新增 `dcep/sign/RsaSigner.java` / `Sm2Signer.java` | **新增** |
| 新增 SQL `migrate_v*_pay_dcep_channel_cert_fields.sql` | **新增 DDL**（如需扩展实体） |
| `application.yml` | **新增** app.dcep.{sign-algo, sandbox-base-url} |
| 新增测试 `DcepCcbSandboxTest.java` 等 | **新增** |

### 3.2 禁止修改（T01-T12 冻结）

| 区域 | 不动原因 |
|---|---|
| `dcep/DcepChannelAdapter.java` 接口签名 | 不扩展接口（向后兼容） |
| `dcep/DcepAdapterFactory.java` | 工厂已就绪 |
| `DcepConfigProperties.java` | T12 已 🔒 |
| `DcepService.verifySign` 主流程 | T12 已 🔒（dev 收紧方向不可逆） |
| `pay_dcep_channel.env_mode` CHECK | T12 已 🔒 |
| 所有 T01-T11 已冻结代码 | 不动 |
| `frontend/meiyetec-web/**` | 不动 |
| E2E 链路 | 不动 |

### 3.3 ⚠️ T13 与 DcepService 的耦合

**当前 DcepService.mockPrepay 走自己逻辑**，T13 接入后必须改成：

```java
// 当前（mockPrepay 私有方法）
if (prod) return FAIL;
if (MOCK) return OK;
return FAIL;

// T13 目标（走 adapter）
DcepChannelAdapter adapter = dcepAdapterFactory.getAdapter(channelCode);
PrepayRequest req = PrepayRequest.builder()
    .merchantId(channel.getMerchantId())
    .privateKeyPath(channel.getPrivateKeyPath())
    .publicKeyPath(channel.getPublicKeyPath())
    .apiBaseUrl(channel.getApiBaseUrl())
    ...
    .build();
PrepayResult result = adapter.prepay(req);
```

**这是 T13 的必要前置工作 — DcepService 主体改造属于 T13 范围**。

---

## 4 · 风险分析

### 4.1 商业 / 监管风险

| 风险 | 概率 | 影响 |
|---|---|---|
| 央行 / 运营机构资质未取得 | 🟡 高 | T13 完全无法启动 |
| 银行 SDK NDA 限制 | 🟡 高 | CCB/ABC/ICBC 部分资源需商业协议 |
| 商户号 / 双向证书下发周期 | 🟡 中 | 通常 2-6 个月 |
| 同城专线部署 | 🟡 中 | 物理网络要求 |

### 4.2 架构风险

| 风险 | 概率 | 影响 |
|---|---|---|
| DcepService 架构不连通（mockPrepay 旁路） | 🔴 已知 | T13 必须先重构 DcepService 才能让 adapter 真正生效 |
| 真实银行回调 IP 校验缺失 | 🟡 中 | 需新增 IP 白名单 |
| 重放攻击防护依赖 notifyId 缓存 | 🟡 中 | Redis TTL 30 天需保证 |
| RSA/SM2 双算法支持 | 🟡 中 | 国密合规要求 SM2 + 国际兼容要求 RSA |
| 银行 API 版本兼容（每家独立版本管理） | 🟡 中 | 需独立 Sandbox 测试 |

### 4.3 测试风险

| 风险 | 概率 | 影响 |
|---|---|---|
| 银行沙箱环境非本地依赖 | 🟡 高 | CI 无法运行真实联调 |
| Mock 测试无法覆盖银行真实错误码 | 🟡 中 | 银行上线后才发现错误码映射不全 |
| notify 验签测试需真实公钥 | 🟡 中 | 沙箱/生产公钥不一致 |

### 4.4 数据风险

| 风险 | 概率 | 影响 |
|---|---|---|
| 私钥 / 证书明文落库 | 🔴 高 | 必须改用 K8s Secret / Vault / JCEKS |
| 测试 sandbox 通道混入生产 | 🟡 中 | T12 已加 CHECK 约束，但仍需业务层校验 |
| pay_dcep_channel 字段扩展（DDL） | 🟢 低 | T12 历史经验已验证 ALTER TABLE 安全 |

---

## 5 · 实施方案候选

### 方案 A · **单银行 POC（CCB）**

| 项 | 内容 |
|---|---|
| **范围** | 仅接入 CCB SDK；ABC/ICBC 保持占位 |
| **前置** | 1. BD 完成对公钱包开户（汇元）<br>2. CCB 下发 SDK + 客户端证书 + 商户号<br>3. 商业协议签署 |
| **周期** | 半年（资质 4-6 月 + 联调 2 月） |
| **风险** | 单一银行，单点失败 |
| **T13 范围** | CcbDcepAdapter 完整实现；DcepService 走 adapter；新增 CCB 测试 |
| **优点** | 路径清晰；可分阶段验证 |
| **缺点** | 业务不可降级到 ABC/ICBC |

### 方案 B · **聚合支付前置**

| 项 | 内容 |
|---|---|
| **范围** | 接入银联商务 / 通联支付等聚合商；由聚合商对接 DCEP 银行 |
| **前置** | 1. 与聚合商签约<br>2. 聚合商下发 SDK / API 文档<br>3. 商户号由聚合商分配 |
| **周期** | 2-4 月 |
| **风险** | 聚合商本身未对接 DCEP 或仅部分银行支持 |
| **T13 范围** | 新增 `AggregatedDcepAdapter`（统一前置）；DcepService 改造 |
| **优点** | 一次接入，多银行覆盖 |
| **缺点** | 依赖第三方稳定性 + 费率 |

### 方案 C · **维持现状（dev mock）**

| 项 | 内容 |
|---|---|
| **范围** | 保持 NOT_IMPLEMENTED；T13 不实际启动 |
| **验收** | 不可能达到生产 P0 终极回归（T14 必失败） |
| **风险** | DCEP 在生产**完全不可用** |
| **优点** | 零商业资源需求 |
| **缺点** | **业务不可生产** — 与 P0 整体目标冲突 |

### 方案 D · **微信/支付宝同源聚合**

| 项 | 内容 |
|---|---|
| **范围** | 数币 API 走第三方聚合（与微信/支付宝共用通道） |
| **前置** | 1. 现有聚合商支持数币<br>2. 商业协议扩展 |
| **周期** | 1-2 月（基于现有通道扩展） |
| **风险** | 数币合规性需监管审批 |
| **T13 范围** | 复用现有 `mkt_channel_config` 路径；DcepService 改造 |
| **优点** | 最快上线 |
| **缺点** | 监管风险；非主流方案 |

### 方案对比矩阵

| 维度 | A（单银行） | B（聚合前置） | C（维持现状） | D（聚合扩展） |
|---|---|---|---|---|
| 商业资源 | 🟡 需签约 CCB | 🟡 需签聚合商 | 🟢 无 | 🟡 需扩展协议 |
| 监管资质 | 🟡 央行审批 | 🟢 聚合商承担 | 🟢 无 | 🟡 监管审批 |
| 上线周期 | 🟡 半年 | 🟢 2-4 月 | 🟢 即时 | 🟢 1-2 月 |
| 风险 | 🟡 单点失败 | 🟡 第三方依赖 | 🔴 业务不可用 | 🟡 合规风险 |
| T13 工程量 | 🟡 中（1 adapter） | 🟡 中（1 聚合 adapter） | 🟢 0 | 🟡 中 |
| 生产覆盖 | 🟡 CCB only | 🟢 多银行 | 🔴 无 | 🟢 全 |
| 商业可行性 | 🟢 主流 | 🟢 主流 | 🔴 不可接受 | 🟡 待评估 |

---

## 6 · 推荐方案

### 短期路径（满足 P0 终结）：**方案 A + 后续扩展**

**理由**：
- P0 阶段必须有"至少 1 家银行真实接入"才能终结（T14 终极回归）
- CCB 是 DCEP POC 主流（央行指定的数币运营机构之一）
- 商业协议清晰，监管路径明确
- T13 工程量可控

**T13 实施前需先决策**：
1. **D1**：选哪条路径（A/B/C/D）？
2. **D2**：是否接受单银行风险（半年内 ABC/ICBC 不可用）？
3. **D3**：是否引入聚合方案作为 ABC/ICBC 的回退？
4. **D4**：私钥 / 证书存储方案（K8s Secret vs Vault vs JCEKS）？
5. **D5**：是否需要 RSA + SM2 双算法支持？

### 修改文件清单（方案 A 决策后）

| # | 文件 | 类型 |
|---|---|---|
| 1 | `dcep/impl/CcbDcepAdapter.java` | **改 5 method 真实实现** |
| 2 | `service/DcepService.java` | **改 mockPrepay/verifySign/refundOrder/closeOrder/healthCheck 走 adapter** |
| 3 | `dcep/sign/CcbRsaSigner.java` 或 `dcep/sign/CcbSm2Signer.java` | **新增** |
| 4 | `application.yml` | 新增 app.dcep.ccb.{api-base-url, sign-algo, ...} |
| 5 | `entity/PayDcepChannel.java` | 可选扩展字段 |
| 6 | 新增 SQL 迁移（若扩展实体） | **新增** |
| 7 | `test/.../DcepCcbSandboxIntegrationTest.java` | **新增**（需银行沙箱环境） |
| 8 | `docs/audit/BEAUTY_SAAS/T13_COMPLETION_REPORT.md` | **新增** |

### 验收标准（方案 A 决策后）

| 项 | 标准 |
|---|---|
| CcbDcepAdapter.prepay() | 调用 CCB 沙箱，真实 prepayId + qrCode |
| CcbDcepAdapter.parseAndVerifyNotify() | CCB 公钥 RSA/SM2 验签通过 |
| CcbDcepAdapter.query/refund/closeOrder | 沙箱联调通过 |
| DcepService.prepay() | 走 adapter.prepay()（非 mockPrepay 私有逻辑） |
| DcepService.notify() | 走 adapter.parseAndVerifyNotify() |
| sandbox 端到端 | 1 笔测试交易从 prepay → notify → 状态更新成功 |
| T13 完成不影响 T01-T12 baseline | 62 个 DCEP 测试仍 PASS |

---

## 7 · 是否影响 T01-T12 冻结

**T13 实施必须扩展 DcepService 主体**，但**不会影响** T01-T11 业务代码。

**T12 边界**：
- ✅ T12 校验规则**保持不变**（dev/prod/sandbox）
- ✅ T12 CHECK 约束**保持不变**
- ⚠️ T12 启动能力检查（validateRuntimeCapability）会因真实 SDK 接入而**自然升级**（adapter.probe() 不再全部 NOT_IMPLEMENTED → runtime=true）

**T12 不被破，反而 T13 让 T12 的逻辑更有意义**。

---

## 8 · 决策点（待人工确认）

T13 **不是工程任务，是商业资源 + 监管路径决策**：

| # | 决策点 | 选项 |
|---|---|---|
| **D1** | 路径选择 | A（CCB POC）/ B（聚合前置）/ C（维持）/ D（聚合扩展） |
| **D2** | 周期 | 半年 / 4 月 / 即时 / 2 月 |
| **D3** | 风险接受 | 单银行 / 多银行 / 业务不可生产 |
| **D4** | 密钥存储 | K8s Secret / Vault / JCEKS / DB 加密列 |
| **D5** | 算法支持 | RSA-2048 only / SM2 only / 双算法 |

**T13 必须先有商业决策才能进入工程实施**。

---

## 9 · 当前 P0 状态

```
T01-T10  ✅ PASS 🔒 FROZEN
T11      ✅ PASS 🔒 FROZEN
T12      ✅ PASS 🔒 FROZEN
T13      🟡 BLOCKED / PRECHECK ONLY
─────────────────────────
P0 进度：12/14 完成（86%）
```

---

## 10 · 停止 — 等待人工确认

**T13 PRECHECK COMPLETE**

报告已生成在 `docs/audit/BEAUTY_SAAS/T13_PRECHECK_REPORT.md`。

**未修改任何代码、未触碰任何冻结文件、未改动数据库**。

**等决策 D1（路径选择）后才能进入 T13 工程实施**。

---

*END OF T13_PRECHECK_REPORT — Generated 2026-08-15 23:19 UTC+8 — PRECHECK ONLY*