# 15_MONEY_SAFETY_AUDIT_V3

> **基线建立日期**: 2026-08-13
> **审计目的**: 对涉及资金的核心调用链进行安全审计，定位 Mock / 伪造 ID / 弱随机等可能在生产路径中触发的风险

---

## 0. 资金相关调用链总览

### 0.1 涉及的模块

| 模块 | 路径 | 风险等级 |
|------|------|----------|
| 钱包充值 / 消费 / 退款 | `controller/WalletController.java` | P0 |
| POS 订单支付 | `controller/PosController.java` | P0 |
| POS 退款 | `service/PosRefundService.java` + `controller/PosRefundController.java` | P0 |
| DCEP 数字人民币 | `service/DcepService.java` | P0 |
| 套餐核销 | `controller/PosController.java#redeemPackage` | P1 |
| 提成结算 | `service/CommissionSettlementService.java` | P1 |
| 会员积分 | `service/impl/PointServiceImpl.java` | P1 |

### 0.2 涉及的资金标识符

- `transactionId`（支付通道交易号）
- `paymentNo`（业务支付流水号）
- `refundNo` / `refundId`（退款单号）
- `dcepOrderNo`（DCEP 订单号）
- `outTradeNo`（外部交易号）
- `prepayId`（预支付 ID）
- `sign` / `signature`（验签）

---

## 1. MONEY_SAFETY_MATRIX

| # | Function | File:Line | 标识符生成方式 | 进入生产路径 | 幂等性 | 风险等级 | 备注 |
|---|----------|-----------|----------------|--------------|--------|----------|------|
| 1 | PosController.processOnePayment (cash/wallet/package) | `PosController.java:265-335` | transactionId 由前端传入或 `ECNY-` + `Math.random()` | ⚠️ 部分进 | ✅ Idempotency | P0 | **e_cny_soft/hard 走 Math.random** |
| 2 | PosController.createOrder (生成订单号) | `PosController.java:595` | `"O" + yyyyMMddHHmmss + String.format("%06d", new Random().nextInt(999999))` | ⚠️ 进 | 通过 `UNIQUE` 兜底 | P0 | 重复概率 = 1/999999，但与时间戳组合碰撞仍可能 |
| 3 | PosController (生成订单号 alt) | `PosController.java:600` | `"O" + yyyyMMddHHmmss + String.format("%04d", new Random().nextInt(9999))` | ⚠️ 进 | — | P0 | 1/9999 碰撞率高 |
| 4 | PosRefundService.createRefund (生成 refundNo) | `PosRefundService.java:603-604` | `"RF" + yyyyMMddHHmmss + (int)(Math.random() * 1000)` | ⚠️ 进 | ✅ Idempotency | P0 | 1/1000 碰撞率 |
| 5 | PosRefundService (channelTxnId) | `PosRefundService.java:278` | `"CH-" + ts + (int)(Math.random() * 10000)` | ⚠️ 进 | — | P1 | 仅用于子项记录 |
| 6 | PosRefundService (子单号) | `PosRefundService.java:210` | `"RFI" + ts + (int)(Math.random() * 1000)` | ⚠️ 进 | — | P1 | |
| 7 | DcepService.prepay (dcepOrderNo) | `DcepService.java:133` | `"DCEP" + ts + UUID.randomUUID().substring(0,4)` | ✅ 进 | UNIQUE 兜底 | P1 | UUID 段安全 |
| 8 | DcepService.mockPrepay (prepayId) | `DcepService.java:170-183` | `"PREPAY-" + order.getDcepOrderNo()`（确定性，非随机） | ✅ 进 | — | P1 | |
| 9 | DcepService.verifySign | `DcepService.java:337-352` | `sign.startsWith("MOCK-")` 即通过 | ✅ 进 | — | **P0** | **MOCK 通道验签完全可绕过** |
| 10 | DcepService.notify (tenantId) | `DcepService.java:255-280` | 从 `TenantContextHolder.get().getTenantId()` 取，**未对 payload 中的 tenant_id 做交叉校验** | ✅ 进 | — | P1 | 跨租户风险 |
| 11 | WalletController.recharge (钱包交易) | `WalletController.java:115-163` | 走 `WalletTransaction` 实体 + `txnService.save` | ✅ 进 | ✅ Idempotency | LOW | REAL |
| 12 | WalletController.consume (钱包扣款) | `WalletController.java:165-209` | 同上 + `memberService.updateById` 同步 member.balance | ✅ 进 | ✅ Idempotency | P0 | **双轨写 crm_member.balance 与 crm_wallet.balance** |
| 13 | WalletController.refund | `WalletController.java:211-264` | 同上 + 钱包 CAS | ✅ 进 | ✅ Idempotency | LOW | REAL |
| 14 | PosController.wallet path (order 消费) | `PosController.java:286-301` | 直接 JDBC `INSERT INTO crm_wallet_transaction`，硬编码 tenantId=1L | ✅ 进 | — | **P0** | **租户隔离破坏** + 双轨 |
| 15 | PosController.wallet refund path | `PosController.java:391-396` | 同上硬编码 1L | ✅ 进 | — | **P0** | 同上 |
| 16 | CommissionSettlementService.generateBatchNo | `CommissionSettlementService.java:169` | `String.format("%04d", (int)(Math.random() * 10000))` | ⚠️ 进 | — | P1 | 批次号非资金号 |
| 17 | ConsentAndDrugDeviceService.signatureId | `ConsentAndDrugDeviceService.java:142` | `"SIG" + ts + Random().nextInt(1000)` | ⚠️ 进 | — | P2 | 非资金 |
| 18 | MemberController.memberNo | `MemberController.java:283,511` | `DateFormat + "%04d" Random.nextInt(9999)` | ✅ 进 | UNIQUE 兜底 | P1 | 1/9999 碰撞 |

### 1.1 资金 Mock 检测

| 调用 | 文件:行 | Mock 内容 | 生产路径 |
|------|---------|-----------|----------|
| `DcepService.mockPrepay()` | `DcepService.java:170` | 硬编码 1×1 像素 PNG QR (Base64) + `PREPAY-` 前缀 prepayId | ✅ **生产路径**（MOCK 模式默认开启） |
| `DcepService.verifySign` | `DcepService.java:348` | `sign.startsWith("MOCK-")` 即通过 | ✅ **生产路径** |
| `DcepService.notify` 业务侧回调 | `DcepService.java:322-325` | 直接 `UPDATE crm_order SET pay_method='DCEP'`（无内部订单状态校验） | ✅ 进 |
| `PosController.processOnePayment (e_cny_*)` | `PosController.java:318-323` | `Math.random()` 伪造 transactionId + TODO 注释 | ✅ 进 |

### 1.2 ID 生成风险分级

| 风险 | 调用 | 原因 |
|------|------|------|
| **P0** | `DcepService.mockPrepay` | 真实生产路径无银行 SDK |
| **P0** | `DcepService.verifySign` | `MOCK-` 前缀绕过验签 |
| **P0** | `PosController processOnePayment (e_cny)` | `Math.random()` 假交易号 |
| **P0** | `PosController wallet path INSERT` | 硬编码 tenantId=1L 破坏多租户 |
| P0 | `PosController.createOrder (orderNo)` | 1/999999 碰撞率 + 时间戳精度风险 |
| P0 | `PosRefundService refundNo` | 1/1000 碰撞率 |
| P1 | `DcepService.dcepOrderNo` | UUID 安全 |
| P1 | `MemberController.memberNo` | 1/9999 + UNIQUE 兜底 |

---

## 2. 完整资金调用链验证

### 2.1 充值 → 消费 → 退款 链路

```
前端 wallet/recharge
↓
WalletController.recharge (idempotency-key)
↓
WalletController.doRecharge (@Transactional)
  ├─ ensureWallet(memberId) - 查 crm_wallet
  ├─ casUpdate - 乐观锁 + 重试 3 次
  ├─ WalletTransaction save
  └─ crm_member.balance 同步更新 ← ⚠️ 双轨
↓
DB commit
↓
前端 wallet/consume
↓
WalletController.consume → doConsume
  ├─ 检查 member.status != frozen
  ├─ casUpdate
  ├─ WalletTransaction save
  └─ crm_member.balance 同步更新 ← ⚠️ 双轨

退款反向链路：
WalletController.refund → doRefund
  ├─ 检查 orderId 是否已退（txnService.count）
  ├─ casUpdate
  ├─ WalletTransaction save (type=3)
  └─ crm_member.balance 同步更新
```

**链路判定**：
- ✅ 幂等（Idempotency-Key + tx 重复检查）
- ✅ 乐观锁（version CAS）
- ✅ 事务边界（@Transactional）
- 🟠 **双轨一致性问题**：crm_wallet.balance 与 crm_member.balance 同时更新，任何一处失败将导致漂移（虽然事务回滚兜底，但回滚后两表均回滚，问题在于冗余）

### 2.2 POS 订单支付链路

```
前端 pos/orders/{id}/pay
↓
PosController.pay (参数: list<PosPayDTO.PaymentItem>)
↓
PosController.processOnePayment (循环每条 PaymentItem)
  ├─ case "wallet": 
  │   ├─ 查 member.balance
  │   ├─ member.balance -= amount
  │   ├─ memberService.updateById(m)
  │   ├─ JDBC INSERT INTO crm_wallet_transaction (硬编码 tenant_id=1L) ← ⚠️ 多租户破坏
  │   └─ jdbc update m2: NOT IN THIS CASE - 仅更新 member.balance
  ├─ case "package":
  │   ├─ redeemPackage(order, packageOrderId)
  │   ├─ UPDATE crm_package_order SET remaining_times = remaining_times - 1
  │   └─ INSERT INTO crm_package_redemption
  ├─ case "e_cny_soft", "e_cny_hard":
  │   └─ ⚠️ 如果 transactionId == null: Math.random() 生成 ← P0
  ├─ case "cash"/"wechat"/"alipay"/"unionpay":
  │   └─ 仅记录，无通道对接（占位）
  └─ default: 抛异常
↓
order.paidAmount +=
↓
if (paidAmount >= finalAmount || hasPackage) {
  order.status = 2 (paid)
  orderMapper.updateById(order)
  commissionCalculationService.calculateOrder(order.id) ← P1 真实业务
  inventoryConsumptionService.consumeByOrder(order.id) ← ✅ REAL 链路
  eventPublisher.publishEvent(OrderPaidEvent) ← 异步
}
```

### 2.3 DCEP 链路

```
前端 pay/dcep/prepay (POST)
↓
DcepService.prepay
  ├─ 查通道 (envMode=MOCK 默认)
  ├─ 插入 pay_dcep_order (dcepOrderNo = "DCEP" + ts + UUID.substring(0,4))
  ├─ mockPrepay() ← ⚠️ MOCK 返回硬编码 QR + "PREPAY-" + dcepOrderNo
  └─ 返回 prepayId / qrCode

前端 pay/dcep/notify/{channelCode} (POST) ← ⚠️ **外部可调**
↓
DcepService.notify
  ├─ verifySign() ← ⚠️ MOCK- 前缀绕过
  ├─ 防重 (pay_dcep_notify.notify_id)
  ├─ 查 pay_dcep_order
  ├─ 写 pay_dcep_notify (signVerified)
  ├─ 更新 pay_dcep_order (status=2 paid)
  └─ **直接 JDBC UPDATE crm_order** ← ⚠️ 跨模块直写
```

### 2.4 退款链路（已实现 REAL）

```
PosController.refund
↓
PosRefundController / PosRefundService
  ├─ 创建 PosRefund (策略 ORIGINAL/PART/WALLET/MIXED)
  ├─ audit (审核)
  ├─ process (执行)
  │   ├─ case cash: PosPayment.status=3 + 现金记账
  │   ├─ case wallet: wallet.balance += (调用 WalletController.doRefund 反向？或直接 JDBC)
  │   ├─ case wechat/alipay: 第三方子单
  │   └─ case package: reverseStock() + crm_package_order.remaining_times +=
  └─ SUCCESS 标记
```

---

## 3. 关键安全风险总结

### 3.1 P0 风险（生产环境禁止开启）

| ID | 风险 | 证据 | 修复 |
|----|------|------|------|
| MS-001 | DCEP Mock 通道直接返回硬编码 QR，绕过真实通道 | `DcepService.java:170-183` | 替换为银行 SDK 实现；MOCK 模式仅 dev 环境开启 |
| MS-002 | DCEP 验签 `sign.startsWith("MOCK-")` 通过 | `DcepService.java:348` | 强制使用 RSA/SM2 公钥验签 |
| MS-003 | e_cny 路径用 Math.random() 生成 transactionId | `PosController.java:321` | 强制要求前端传入 transactionId；或 DCEP 通道返回 |
| MS-004 | DCEP notify 直接 JDBC UPDATE crm_order | `DcepService.java:323-325` | 通过 OrderService 业务方法更新，触发完整业务流（库存/提成/事件） |
| MS-005 | PosController wallet path INSERT 硬编码 tenant_id=1L | `PosController.java:300` | 从 TenantContextHolder 取 |
| MS-006 | crm_wallet.balance 与 crm_member.balance 双写 | `WalletController.java:152,197,250` + `PosController.java:294-301` | 选择一个为权威源（推荐 crm_wallet），删除冗余字段或改为只读视图 |

### 3.2 P1 风险

| ID | 风险 | 证据 | 修复 |
|----|------|------|------|
| MS-007 | 订单号生成 1/999999 碰撞 | `PosController.java:595` | 改用雪花算法或 UUID |
| MS-008 | 退款单号 1/1000 碰撞 | `PosRefundService.java:604` | 同上 |
| MS-009 | DCEP notify 不校验 payload 中的 tenant_id 与订单 tenant_id 一致 | `DcepService.java:255-280` | 增加 tenant_id 交叉校验 |

---

## 4. 结论

- **资金链路整体有幂等 + 乐观锁 + 事务兜底**，基础安全机制完整
- **DCEP 完全 Mock 是最大风险**：4 个 P0 风险（MS-001~004）集中在 DCEP 通道
- **多租户破坏 2 处**：PosController wallet path 硬编码 1L
- **双轨一致性问题**：crm_member.balance 与 crm_wallet.balance 必须收敛
- **ID 生成弱随机 3 处**：PosController orderNo/RefundService refundNo/MemberController memberNo

---

**审计完成时间**: 2026-08-13 23:10
**文档版本**: 3.0