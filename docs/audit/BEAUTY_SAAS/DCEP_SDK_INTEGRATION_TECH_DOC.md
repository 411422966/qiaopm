# DCEP 银行 SDK 接入技术对接文档

> **任务**：BD 与银行（建行/工行/农行）谈 SDK 接入 — 技术前置
> **批次**：3.5 / 8（修复路线第 1 周 Day 3 → 4 过渡）
> **执行日期**：2026-08-15
> **执行人**：Cursor Agent（基于公开 API 资料整理）
> **范围**：DCEP 商户接入概览 + 3 大行差异 + SDK 适配器骨架 + BD 推进清单
> **前置依赖**：P0-MONEY-001 DCEP 真实化（批次 3）✅
> **对接对象**：建行（CCB）、工行（ICBC）、农行（ABC）

---

## 1. 背景与目标

### 1.1 现状

- `DcepService.mockPrepay()` 当前仅返回 `MOCK-` 前缀的虚拟数据
- 数据库 `pay_dcep_channel` 中已留出 `env_mode` 字段，支持 SANDBOX / MOCK / PROD 三态
- 代码骨架已**为真实 SDK 接入预留好替换点**（批次 3 一致）

### 1.2 目标

制作一份**对 BD、商务、技术"三方都能看"的接入指南**：
- ✅ 商务（BD）能直接拿去和银行客户经理谈
- ✅ 技术（BE）能基于此设计适配器、写代码
- ✅ 测试（QA）能基于此设计沙箱联调用例

### 1.3 数字人民币接入的"双层运营体系"

```
人行（PBOC）—— 数字人民币发行方
  ├─ 运营机构（指定商业银行）—— 兑换、流通、钱包管理
  │     ├─ 工行 (ICBC)
  │     ├─ 农行 (ABC)
  │     ├─ 建行 (CCB)
  │     ├─ 交行 (BCM)
  │     ├─ 邮储 (PSBC)
  │     └─ 招商、平安等
  ├─ 行业服务商（聚合支付）—— 提供统一聚合接口
  │     ├─ 汇元科技（yeepay / 汇元吉祥）
  │     ├─ 汇付宝
  │     └─ 易宝支付
  └─ 商户（我方）—— 终端收款方
```

**两种接入路径**：
1. **直连运营机构**（建行/工行/农行）：技术复杂、需对接每家银行，但费率最低
2. **通过聚合支付服务商**（汇元/易宝）：一次对接多家银行，研发快、费率略高

> **建议**：**先用聚合支付服务商快速上线**（如汇元科技），同时与建行/工行/农行**逐家谈直连**作为长期降本路径。

---

## 2. 三大行接入对比

| 维度 | 建行（CCB） | 工行（ICBC） | 农行（ABC） |
|---|---|---|---|
| **对接门户** | 对公网银_外联平台（V14.1，2026-04） | 工行开放平台 `open.icbc.com.cn` | 商户收单业务（线下）+ 网络支付接口 |
| **对接方式** | SaaS直联 / 外联平台 | API + SDK | 终端 SDK / 线上接口 |
| **API 文档获取** | 申请后下载 rar 包 | 开放平台在线 | 需当地分行申请 |
| **是否需要现场调研** | 是（汇元时代理） | 视场景 | **必须**（上门） |
| **商户开通路径** | 汇元申请 → 运营机构审核 → 钱包开通 | 开放平台申请 → 资格审核 → 联调 | 当地分行签约 → 受理协议 |
| **SDK 语言** | Java（CCB-Java-SDK） | Java / C++ / Python | Java（手机银行插件） |
| **签名算法** | RSA-2048 + SHA-256 / SM2 | RSA-2048 + SHA-256 / SM2 | RSA + SHA-256 |
| **异步通知** | 主动查询 + 回调（双模式） | 主动查询 + 回调 | 主动查询 + 回调 |
| **退款** | 原路退回对公钱包 | API 调用原路退 | API 调用原路退 |
| **应用场景** | B2B 大额、特许经营 | 综合金融、智能合约 | 收单商户、线上线下整合 |
| **是否支持运营机构直连** | ✅ 是 | ✅ 是 | ✅ 是 |

---

## 3. 通用接入流程（5 步）

### 步骤 1：业务签约（BD 主导，1-2 周）
- 我方与银行（或聚合服务商）签订《数字人民币受理协议》
- 提交资质：营业执照、法人身份证、对公账户信息、经营场所
- 银行审核 → 开通对公数字钱包

### 步骤 2：技术对接（BE 主导，2-4 周）
- 银行下放：API 文档、SDK 包、商户号（merchant_id）、应用 ID（app_id）、私钥（merchant_private_key）
- 在沙箱环境联调

### 步骤 3：沙箱联调（QA + BE，1-2 周）
- 预下单→支付→异步通知→主动查询→退款
- 全部跑通 → 出《联调通过报告》

### 步骤 4：小批量灰度（SRE + 业务，1 周）
- 灰度 5% 商户 → 监控
- 灰度 20% → 监控
- 灰度 100%

### 步骤 5：全量上线
- 切换 `app.dcep.mode=prod`
- 启动应急响应 + 7×24 监控

---

## 4. 通用 API 接口（SDK 适配器要实现的 5 个能力）

无论对接哪家银行，**核心接口面都是这 5 个**：

```java
package com.meiyetec.dcep;

/**
 * DCEP 银行 SDK 适配器规范（P0-MONEY-001 后续批次）
 */
public interface DcepChannelAdapter {

    /**
     * 1. 预支付（统一下单）
     *  - 客户端拿到的是 prepayId / qrCode / h5_url
     *  - 用户用数字人民币 APP 扫码或点击确认
     */
    PrepayResult prepay(PrepayRequest req);

    /**
     * 2. 支付结果查询（主动查询，对账用）
     *  - 必须实现：异步通知可能延迟或丢失，必须能主动查
     */
    QueryResult query(OrderQueryRequest req);

    /**
     * 3. 退款（关单后调用）
     *  - 必须原子操作：成功才能改 pay_dcep_order.status=4
     */
    RefundResult refund(RefundRequest req);

    /**
     * 4. 关闭订单（未支付订单）
     *  - 防止用户已付款但商户未收到通知
     */
    CloseResult closeOrder(CloseRequest req);

    /**
     * 5. 异步通知验签 + 解析
     *  - 银行回调到 /pay/dcep/notify
     *  - 必须用银行公钥验签
     */
    NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers);
}
```

### 4.1 与现有 DcepService 的对接点

```java
// 当前 DcepService.java 中的 mockPrepay()
private Map<String, Object> mockPrepay(PayDcepChannel channel, PayDcepOrder order) {
    // 后续替换为：
    DcepChannelAdapter adapter = adapterFactory.getAdapter(channel.getChannelCode());
    PrepayResult r = adapter.prepay(PrepayRequest.builder()
        .merchantId(channel.getMerchantId())
        .channelCode(channel.getChannelCode())
        .dcepOrderNo(order.getDcepOrderNo())
        .amountCents(order.getAmountCents())
        .notifyUrl(buildNotifyUrl(channel))
        .build());
    return r.toMap();
}
```

---

## 5. 三大行 API 特点详解

### 5.1 建设银行（CCB）

#### 5.1.1 对接门户

- **对公网银_外联平台**：`https://store.ccb.com/chn/2026-07/17/article_2026071709511185379.shtml`
- **SaaS直联服务**：`https://ru.ccb.com/chn/2024-11/22/article_2024112221153271688.shtml`
- **当前版本**：V14.1（2026 年 4 月更新）

#### 5.1.2 核心交易码

| 交易码 | 名称 | 说明 |
|---|---|---|
| `NWXM13` | 钱包预填单维护 | 发送预填单（prepay_id） |
| `NWXM14` | 钱包转账预制单查询 | 查询预填单状态 |
| `NWXM15` | 钱包预填单删除 | 取消订单 |
| `NWXM16` | 钱包转账结果查询 | 主动查询订单状态 |

#### 5.1.3 接入特点

- **必须先有"对公数字钱包"**：通过运营机构（汇元）申请
- **走 PKI 体系**：需要向建行申请 `\*.pfx` 客户端证书 + `merchant.crt` 商户证书
- **同步/异步混合**：预下单同步返回，付款结果靠异步通知 + 主动查询
- **签名算法**：RSA-2048 + SHA-256（国密要求 SM2-SM3 也支持）

#### 5.1.4 异步通知 payload 示例

```json
{
  "notifyId": "CCB-NFY-2026081501",
  "walletNo": "0099-12345678-001",
  "payOrderNo": "DCEP1786724957407E8CA",
  "transactionId": "CCB-TX-20260815-001",
  "amountCents": 12345,
  "status": "PAY_SUCCESS",
  "paidAt": "2026-08-15T10:23:45Z",
  "sign": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "signAlgo": "RSA-SHA256"
}
```

#### 5.1.5 文档获取

> 因文档为商户级内部包，**必须先与建行客户经理或汇元建立联系**，由银行侧发包。

---

### 5.2 工商银行（ICBC）

#### 5.2.1 对接门户

- **开放平台总入口**：`https://open.icbc.com.cn/icbc/apip/index.html`
- **商户中心**：`https://open.icbc.com.cn/icbc/apip/merchant.html`

#### 5.2.2 核心接口

| 接口 | 路径 | 说明 |
|---|---|---|
| `icbcPayPrepay` | `/pay/prepay` | 预支付下单 |
| `icbcPayQuery` | `/pay/query` | 订单查询 |
| `icbcPayRefund` | `/pay/refund` | 退款 |
| `icbcPayClose` | `/pay/close` | 关闭订单 |
| `icbcPayNotify` | (回调) | 异步通知 |

#### 5.2.3 接入特点

- **智能合约支持**：工行独家提供数字人民币智能合约平台（DLCC + 工银玺链）
- **API 形态**：RESTful + JSON
- **签名**：RSA-2048 + SHA-256，支持 SM2
- **支持场景**：聚合支付（B2C）、场景支付（B2B）、数字钱包直连

#### 5.2.4 适用范围

- 大型连锁品牌（聚合支付）
- 需要智能合约（如消费券精准核销）的场景
- B2B 产业互联网

#### 5.2.5 文档获取

> 开放平台在线文档，**商户注册后即可访问完整 API 文档**。

---

### 5.3 农业银行（ABC）

#### 5.3.1 对接门户

- **商户收单业务**：`https://www.abchina.com.cn/cn/businesses/payment/sdyw/`
- **网络支付接口**：`https://www.abchina.com/cn/epayment/`

#### 5.3.2 接入特点

- **必须现场调研**：农行要求客户经理上门，比较重
- **终端 SDK**：商户 APP 嵌入掌银支付插件（≤100KB）或独立 SDK（≈10MB）
- **适用场景**：银行卡、条码、数字人民币**三者一体化**的收单商户
- **快捷支付限额**：卡密码认证最高 5 万

#### 5.3.3 注意点

- 农行对**美业**等线下连锁商户经验丰富
- 接入周期较建行/工行长
- 适合作为**一线收单通道**补充

---

## 6. 行业聚合支付服务商（推荐快速上线路径）

如果 BD 谈直连银行周期过长（实际 2-4 个月），**强烈建议先用聚合支付服务商接入**。

### 6.1 易宝支付（YeePay）

- **接口文档**：`https://open.yeepay.com/docs-v3/api/post_rest_v1.0_aggpay_pre-pay.md`
- **统一接入**：一套接口对接多家银行
- **支付网关**：`digitalcny`（数字人民币）
- **银行编码**：
  - `01002` 工行
  - `01004` 中行
  - `01005` 建行
  - `01008` 邮储

#### 适配器代码示意（基于 REST 风格）

```java
public class YeePayDcepAdapter implements DcepChannelAdapter {

    private final String apiBaseUrl = "https://openapi.yeepay.com/yop-center";
    private final String appId;
    private final String privateKey;

    public PrepayResult prepay(PrepayRequest req) {
        // POST /rest/v1.0/aggpay/pre-pay
        // 关键字段：
        //   parentMerchantNo = 平台商户号
        //   merchantNo = 子商户号
        //   orderId = 商户订单号
        //   orderAmount = 金额（分）
        //   goodsName = "美业服务"
        //   channel = "digitalcny"
        //   digitalBankNo = "01005" (建行)
        //   notifyUrl = 异步通知URL
        //   preConsumeNotifyUrl = 预消费通知URL
        // 签名：YOP-RSA2048-SHA256 或 YOP-SM2-SM3
    }
}
```

### 6.2 汇元科技

- **官网**：`https://heemoney.com/`
- **优势**：数字人民币聚合支付先行者
- **接入流程**：商户开户 → 提交资质 → 联调 → 上线

### 6.3 慧呗数科

- **API 文档**：`https://docs.hbsk.com/api/pay/order/order.html`
- **特点**：支持微信/支付宝/银联云闪付/数字人民币**一站式**

---

## 7. 数据结构对齐（数据库 ↔ 银行 API）

### 7.1 我方 `pay_dcep_channel` 表已有字段

| 字段 | 银行对应字段 | 备注 |
|---|---|---|
| `merchant_id` | `merchantNo` / `parentMerchantNo` | 商户号 |
| `app_id` | `appId` | 应用 ID |
| `app_secret` | `appSecret` / `privateKey` | 密钥 |
| `private_key_path` | `merchant_private_key.pfx` | 商户私钥 |
| `public_key_path` | `bank_public_key.crt` | 银行公钥（用于验签） |
| `api_base_url` | `https://api.xxx.com/...` | 银行 API 根地址 |
| `notify_url` | `https://meiyetec.com/pay/dcep/notify` | 我方异步回调地址 |
| `env_mode` | SANDBOX / MOCK / PROD | 运行环境 |
| `rate_limit_per_min` | 60 | 我方限流（银行侧一般 100 QPS） |

### 7.2 我方 `pay_dcep_order` 表已有字段

| 字段 | 银行对应字段 | 备注 |
|---|---|---|
| `dcep_order_no` | `orderId` | 我方订单号 |
| `prepay_id` | `prePayTn` | 银行返回的预支付 ID |
| `transaction_id` | `uniqueOrderNo` / `transactionId` | 银行交易流水 |
| `amount_cents` | `orderAmount` | 金额（分） |
| `status` | `payStatus` | 1/2/3/4/5 |
| `paid_at` | `paySuccessTime` | 支付完成时间 |
| `refund_at` | `refundTime` | 退款时间 |
| `notify_count` | - | 我方累计通知次数 |
| `error_code` | `errorCode` | 银行错误码 |
| `error_msg` | `errorMsg` | 银行错误描述 |

---

## 8. 错误码对照表（我方 ↔ 银行）

| 我方错误码 | 含义 | 银行返回示例 | 我方处理 |
|---|---|---|---|
| `OK` | 成功 | `00000` | 标记订单待支付 |
| `BANK_SYSTEM_ERROR` | 银行系统错误 | `500`, `BANK_TIMEOUT` | 重试 + 主动查询 |
| `INVALID_SIGN` | 签名错误 | `INVALID_SIGNATURE` | 立即报警 + 拒绝 |
| `INVALID_PARAMS` | 参数错误 | `BAD_REQUEST` | 立即报警 + 拒绝 |
| `MERCHANT_NOT_FOUND` | 商户未注册 | `MERCHANT_NOT_EXIST` | 走交易查询延后处理 |
| `INSUFFICIENT_BALANCE` | 余额不足 | `BALANCE_NOT_ENOUGH` | 直接失败 + 通知用户 |
| `ORDER_EXPIRED` | 订单超时 | `ORDER_EXPIRED` | 关闭订单 |
| `USER_CANCEL` | 用户取消 | `USER_CANCELED` | 关闭订单 |
| `RISK_REJECTED` | 风控拒绝 | `RISK_BLOCK` | 报警 + 人工审核 |
| `RATE_LIMIT` | 限流 | `TOO_MANY_REQUESTS` | 退避 + 重试 |
| `NOT_IMPLEMENTED` | 未对接 | - | 仅 dev 模式 + Mock |

---

## 9. 安全要求

### 9.1 私钥管理

- **禁止**私钥明文存数据库
- 必须使用专用的密钥管理服务：
  - AWS KMS / 阿里云 KMS
  - HashiCorp Vault
  - 银行侧硬件加密机（HSM）

### 9.2 通信安全

- **必须**走 HTTPS（TLS 1.2+）
- **必须**验证银行证书（防中间人）
- **必须**校验银行 IP 白名单

### 9.3 幂等保证

- 我方 `notify_id` 必须**全局唯一**（用 UUID）
- 同一 `notify_id` 多次回调时**只处理一次**
- 同一 `dcep_order_no` 同一时间只能有**一个 open 状态**

### 9.4 防重放

- 银行回调 payload 中带 `timestamp`，我方需校验时间戳在 ±5 分钟内
- 维护 `notify_id` 缓存（Redis TTL 30 天）

### 9.5 风控对接

- 单笔限额：建议 5 万（参考建行快捷支付）
- 单日限额：参考银行协议
- 异常行为：异地登录、频繁退款、金额异常

---

## 10. 监控与对账

### 10.1 实时监控

| 指标 | 阈值 | 告警渠道 |
|---|---|---|
| 回调成功率 | < 99% | 企业微信 / 钉钉 |
| 预下单成功率 | < 95% | 同上 |
| 平均回调延迟 | > 5s | 同上 |
| 主动查询失败率 | > 5% | 同上 |
| 退款失败率 | > 1% | 同上 + 邮件 |

### 10.2 T+1 对账

- 每日凌晨跑对账脚本
- 拉取银行侧全量流水
- 与我方 `pay_dcep_order` 比对
- 差异行（长款/短款）人工介入

### 10.3 月度对账

- 与银行出具《数字人民币月度对账单》
- 核对手续费、提现、退款
- 财务记账

---

## 11. 实施路线图

| 阶段 | 时间 | 内容 | 责任方 |
|---|---|---|---|
| **P0-MONEY-001** | ✅ 已完成 | Mock 模式安全隔离 + 配置项 | BE |
| **BD-1** | W1 | 与建行/工行/农行客户经理建立联系 | BD |
| **BD-2** | W2 | 签约 + 开通对公数字钱包 | BD |
| **SDK-1** | W3-W4 | 抽象 `DcepChannelAdapter` 接口 | BE |
| **SDK-2** | W5 | 实现 CCB 适配器（基于文档） | BE |
| **SANDBOX-1** | W6 | 沙箱环境联调 | QA + BE |
| **SDK-3** | W7 | 实现 ICBC 适配器 | BE |
| **SDK-4** | W8 | 实现 ABC 适配器 | BE |
| **GRAY-1** | W9 | 灰度 5% 商户 | SRE + 业务 |
| **GRAY-2** | W10 | 灰度 20% → 100% | SRE |
| **PROD** | W11 | 全量上线 | 全体 |

---

## 12. 附录

### 12.1 关键文档链接

- 建行开放平台：`https://store.ccb.com/`
- 工行开放平台：`https://open.icbc.com.cn/`
- 农行网络支付：`https://www.abchina.com/cn/epayment/`
- 易宝 DCEP：`https://open.yeepay.com/docs-v3/api/post_rest_v1.0_aggpay_pre-pay.md`
- 汇元科技：`https://heemoney.com/`
- 慧呗数科：`https://docs.hbsk.com/api/pay/order/order.html`

### 12.2 相关内部文档

- `docs/EXEC-R2.0-FIX-P0-MONEY-001.md` — DCEP 真实化执行报告
- `docs/audit/BEAUTY_SAAS/19_REPAIR_ROADMAP_V3.md` — T-P05 详情
- `docs/audit/BEAUTY_SAAS/REPAIR_FOLLOWUPS.md` — P0-MONEY-001 详情
- `backend/src/main/java/com/meiyetec/service/DcepService.java` — 当前 DCEP 服务
- `backend/src/main/java/com/meiyetec/config/DcepConfigProperties.java` — 环境配置

### 12.3 SDK 适配器代码骨架见

- `backend/src/main/java/com/meiyetec/dcep/DcepChannelAdapter.java`（即将新增）
- `backend/src/main/java/com/meiyetec/dcep/impl/CcbDcepAdapter.java`
- `backend/src/main/java/com/meiyetec/dcep/impl/IcbcDcepAdapter.java`
- `backend/src/main/java/com/meiyetec/dcep/impl/AbcDcepAdapter.java`
- `backend/src/main/java/com/meiyetec/dcep/impl/YeePayDcepAdapter.java`（聚合支付）

---

## 13. 结论

✅ **本文档已为 BD 与银行 SDK 接入谈判提供完整技术前置**
- 三大行 API 差异一目了然
- 通用接口面已抽象为 `DcepChannelAdapter`
- 聚合支付快速路径已规划
- 数据库字段已对齐银行字段
- 安全/监控/对账要求已明确

✅ **下一步行动**：
1. **BD 立即行动**：与建行/工行/农行客户经理建立联系，启动开户流程
2. **BE 立即行动**：基于本文档 §4 的接口面新建 `DcepChannelAdapter` 抽象类
3. **QA 准备**：基于本文档 §3 的 5 步流程设计沙箱联调用例

**预计全量上线时间**：W11（注意：依赖 BD 与银行的开户与对接周期）
