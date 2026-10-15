# PRD 功能实现审计报告

> 审计范围：M16/M17/M18 + 支付方式 + 子行业专属配置
> 审计日期：2026-09-07
> 原则：TODO / Mock / stub / disabled = 未完成

---

## M16 — 自动化营销旅程

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| 触发器规则（mkt_journey_trigger 表） | ✅ | `init_m16_journey_trigger.sql` |
| 触发器执行日志（mkt_journey_trigger_log 表） | ✅ | `init_m16_journey_trigger.sql` |
| JourneyTriggerService（事件驱动引擎） | ✅ | `backend/.../service/JourneyTriggerService.java` |
| JourneyTriggerController（REST API） | ✅ | `backend/.../controller/JourneyTriggerController.java` |
| MarketingEvent + MarketingEventPublisher | ✅ | `backend/.../mkt/event/` |
| 会员生命周期规则引擎（member_lifecycle_rule 表） | ✅ | `migrate_p4_02_member_lifecycle.sql` |
| MemberLifecycleService + Controller | ✅ | `backend/.../service/MemberLifecycleService.java` |
| 前端 lifecycle 页面 | ✅ | `frontend/.../views/lifecycle/index.vue` |
| 营销旅程前端（mkt/journey） | ✅ | `frontend/.../views/mkt/journey.vue` |

### ⚠️ 关键 Bug

**`MarketingEventPublisher` 是孤儿代码 — 旅程触发无法自动触发**

- `MarketingEventPublisher` 和 `MarketingEventTypes` 定义完整，`JourneyTriggerService.onMarketingEvent()` 也已实现 `@Async @EventListener` 监听
- **但没有任何业务服务注入 `MarketingEventPublisher` 并调用** — 搜索 `MarketingEventPublisher` 在 `backend/src/main/java` 中仅出现 2 次（自身定义 + 自身），零次注入调用
- 实际业务：会员注册/下单/充值/预约**不会发布事件**，旅程触发器**只能手动 `POST /mkt/journey/trigger/{id}/run` 逐条测试**，无法自动运行
- 对比表：

| 事件类型 | `MarketingEventPublisher` 方法 | 业务侧是否调用 |
|---|---|---|
| `memberRegistered` | ✅ 有 | ❌ 无 |
| `orderPlaced` | ✅ 有 | ❌ 无 |
| `consume` | ✅ 有 | ❌ 无 |
| `recharge` | ✅ 有 | ❌ 无 |
| `bookingCreated` | ✅ 有 | ❌ 无 |
| `birthday` | ✅ 有 | ❌ 无 |

**修复路径**：在 `MemberService.createMember()` / `PosOrderService.pay()` / `WalletService.recharge()` / `BookingService.create()` 中注入 `MarketingEventPublisher` 并调用对应方法。

---

## M17 — 视觉档案与AI比对

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| CV 模型注册表（cv_model） | ✅ | `init_m17_ai_vision.sql` |
| CV 识别日志表（cv_recognition_log） | ✅ | `init_m17_ai_vision.sql` |
| CvModelClient 接口（SPI 抽象层） | ✅ | `backend/.../ai/CvModelClient.java` |
| AIVisionService（REST API） | ✅ | `backend/.../service/AIVisionService.java` |
| 腾讯云 CV Provider（inferSkin/Face/Nail） | 🟡 | `backend/.../ai/TencentCvProvider.java` |
| 阿里云 CV Provider | 🟡 | `backend/.../ai/AliyunCvProvider.java` |
| 本地 CV Provider | 🟡 | `backend/.../ai/LocalCvProvider.java` |
| 百度 CV Provider | 🔴 缺失 | `CvInferenceProvider.java` 注释声明但无实现 |
| VisualArchiveService（档案 CRUD + AI 对比） | ✅ | `backend/.../service/VisualArchiveService.java` |
| 视觉档案前端（ai-vision/index.vue） | ✅ | `frontend/.../views/ai-vision/index.vue` |
| 视觉档案前端（mh/visual.vue） | ✅ 已验证通过 | `frontend/.../views/mh/visual.vue` |

### ⚠️ 细节问题

1. **`TencentCvProvider.inferSkin` 实际调用的是 `DetectFace`（人脸检测）**，不是皮肤分析 API — 两套能力混用了，`TencentCvProvider.inferNailTryon` 也是直接 mock，没有腾讯美甲 API
2. **`BaiduCvProvider` 缺失** — `CvInferenceProvider.java` 注释明确声明 `BAIDU → BaiduCvProvider`，但实际只有 TENCENT / ALIYUN / LOCAL 三个实现
3. 所有 provider 未配置凭证时均降级到 mock（确定性哈希）— 适合开发，但生产需真实 API key

---

## M18 — 电子知情同意（医美）

> ⚠️ 前端 `mh/consent.vue` 已由用户验证通过，**禁止主动修改**；后端审计不受此限制。

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| 知情同意模板（mh_consent_template 表） | ✅ | `init_complication_sop.sql`（共生）|
| 电子签名（mh_consent_signature 表） | ✅ | 表存在 |
| 药械主档（mh_drug_device 表） | ✅ | 表存在 |
| 批次追溯（mh_drug_device_batch 表） | ✅ | 表存在 |
| 使用记录（mh_drug_device_usage 表） | ✅ | 表存在 |
| ConsentAndDrugDeviceService | ✅ | `backend/.../service/ConsentAndDrugDeviceService.java` |
| 前端 `mh/consent.vue` | ✅ 已验证通过 | `frontend/.../views/mh/consent.vue` |
| 前端 `mh/consent-h5.vue` | ✅ | `frontend/.../views/mh/consent-h5.vue` |
| 后端 H5 签署端点 | 🔴 disabled | `MhConsentH5Controller.java.disabled` |
| Blockchain 自动存证钩子 | 🟡 | `backend/.../service/BlockchainAutoRecordHook.java`（未启用）|
| BlockchainController | 🔴 disabled | `backend/.../controller/BlockchainController.java.disabled` |

### ⚠️ 关键问题

1. **`MhConsentH5Controller.java.disabled`** — H5 端签署链路（患者通过手机链接签署）完全不可用
2. **`BlockchainController.java.disabled`** — 区块链存证链不可用；但 `BlockchainAutoRecordHook.java` 存在，说明有 hook 但未激活
3. `init_complication_sop.sql` 建了 `mh_consent_template` 表，但 ConsentAndDrugDeviceService 的 consent 写入/查询逻辑需要验证完整性

---

## 支付方式全览

### 微信支付 / 支付宝 / 银联云闪付

| 功能点 | 状态 | 备注 |
|---|---|---|
| 国内支付通道 | 🟡 | 通过统一 `PosPayDTO` + `PosPayment` 表路由，未见 `WechatPayAdapter` / `AlipayAdapter` 专用 adapter |
| POS 收银台支付 | ✅ | `PosService` + `PosController` |
| `PosPayment` 表 | ✅ | `init_m02_basic.sql` |

### 🔴 缺口：微信支付 / 支付宝无专用 adapter

搜索 `WechatPay` / `Alipay` / `UnionPay` 在 backend 中零结果。国内三大支付无独立 adapter，通过统一 `PosPayment` 路由表记录。

---

### 数字人民币 e-CNY 2.0

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| DCEP 配置表（dcep_config） | ✅ | `init_dcep_config.sql` |
| DCEP 商户资质表（dcep_merchant_qualification） | ✅ | `init_m12_dcep_qualification.sql` |
| 硬钱包表（biz_dcep_hardware_wallet） | ✅ | `init_dcep_advanced.sql` |
| 双离线交易表（biz_dcep_offline_transaction） | ✅ | `init_dcep_advanced.sql` |
| 子钱包主表（biz_dcep_sub_wallet） | ✅ | `init_dcep05_sub_wallet.sql` |
| 子钱包账本流水（biz_dcep_sub_wallet_ledger） | ✅ | `init_dcep05_sub_wallet.sql` |
| DcepChannelAdapter 接口（5 大能力） | ✅ | `backend/.../dcep/DcepChannelAdapter.java` |
| 建设银行 DCEP Adapter | ✅ | `backend/.../dcep/impl/CcbDcepAdapter.java` |
| 工商银行 DCEP Adapter | ✅ | `backend/.../dcep/impl/IcbcDcepAdapter.java` |
| 农业银行 DCEP Adapter | ✅ | `backend/.../dcep/impl/AbcDcepAdapter.java` |
| 沙箱 DCEP Adapter | ✅ | `backend/.../dcep/impl/SandboxDcepAdapter.java` |
| DCEP 硬件钱包 NFC 读卡器 | ✅ | `backend/.../dcep/hardware/BluetoothNfcReader.java` |
| DCEP 双花检测器 | ✅ | `backend/.../dcep/offline/DoubleSpendDetector.java` |
| DCEP 离线/在线对账 | ✅ | `backend/.../dcep/offline/OfflineOnlineReconciler.java` |
| 智能合约 DSL 验证器 | ✅ | `backend/.../dcep/smartcontract/ContractDslValidator.java` |
| DCEP 硬件钱包 Controller | ✅ | `backend/.../controller/DcepHardwareWalletController.java` |
| DCEP 子钱包 Controller | ✅ | `backend/.../controller/DcepSubWalletController.java` |
| DCEP 智能合约 Controller | 🟡 | `DcepSmartContractController.java`（小文件，需验完整度）|
| DCEP 前端（dcep-advanced.ts） | ✅ | `frontend/.../api/dcep-advanced.ts` |
| DCEP 前端 UI | ✅ | `views/m12/dcep-advanced.vue` |

### ⚠️ 重要缺口

1. **无数字人民币预付费智能合约实际执行** — `ContractDslValidator.java` 是 DSL 验证器，但未见实际调用链（`DcepSmartContractController.java` 约 2KB，可能只是桩）
2. **`DcepConfigController`** 存在但凭据字段（`privateKey` / `publicKey`）加密存储需确认；生产环境需 KMS 集成
3. **离线支付对账是"事后对账"而非"实时确认"** — 依赖 `sync_status` 轮询，联网后才知道是否双花成功

---

### 花呗 / 芝麻信用分期

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| 花呗订单表（huabei_order） | ✅ | 表存在 |
| HuabeiServiceImpl | 🟡 | `backend/.../service/impl/HuabeiServiceImpl.java` |
| HuabeiController | 🔴 disabled | `HuabeiController.java.disabled` |
| 芝麻信用评分 | ⚠️ | `HuabeiServiceImpl` 仅用 `phone.hashCode()` 模拟分，无真实蚂蚁金服 API |
| 前端 `bnpl-huabei.ts` | ✅ | `frontend/.../api/bnpl-huabei.ts` |

**`HuabeiServiceImpl.queryCreditScore()` 实现**：
```java
int score = 350 + Math.abs(phone.hashCode() % 600); // 伪随机，非真实芝麻分
```
→ 完全 mock，生产不可用。

---

### 储值余额 / 积分抵扣

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| WalletController（余额/充值/消费） | ✅ | `backend/.../controller/WalletController.java` |
| WalletService | ✅ | 完整 CRUD + 冻结/解冻 |
| 积分表（member_points / points_transaction） | ✅ | `migrate_points_001_add_tenant_and_goods.sql` |
| PointController | ✅ | `backend/.../controller/PointController.java` |
| 前端钱包页面 | ✅ | `frontend/.../views/wallet/` |
| 前端积分 API | ✅ | `frontend/.../api/point.ts` |

---

### Stripe / Square / PayPal

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| StripeAdapter（Payment Intents） | ✅ | `backend/.../payment/StripeAdapter.java` |
| SquareAdapter | ✅ | `backend/.../payment/SquareAdapter.java` |
| GooglePayAdapter | ✅ | `backend/.../payment/GooglePayAdapter.java` |
| ApplePayAdapter | ✅ | `backend/.../payment/ApplePayAdapter.java` |
| PayPalAdapter | ✅ | `backend/.../payment/PaypalAdapter.java` |
| OverseasPaymentController | ✅ | `backend/.../controller/OverseasPaymentController.java` |
| 前端 overseas-payment | ✅ | `frontend/.../views/intl-payment/` |

---

### Gift Card

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| GiftCardService（购卡/充值/消费） | ✅ | `backend/.../service/GiftCardService.java` |
| 礼品卡表（gift_card / gift_card_transaction） | ✅ | 表存在 |
| 前端 gift-card 页面 | ✅ | `frontend/.../views/gift-card/` |
| Seed 数据 | ✅ | `seed_gift_card_transaction.sql` |

---

### BNPL（先买后付）

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| BNPL 订单表 | ✅ | `init_bnpl.sql` |
| KlarnaBnplProvider | ✅ 真实 API | `backend/.../payment/bnpl/KlarnaBnplProvider.java` |
| BnplController | ✅ | `backend/.../controller/BnplController.java` |
| 前端 BNPL API | ✅ | `frontend/.../api/bnpl-huabei.ts` |

---

## 子行业专属配置

### 3.1 美容院

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| 护理配方档案（care_recipe 等 6 表） | ✅ 表 + 🟡 服务 | `init_care_recipe.sql` |
| CareRecipeController | 🔴 disabled | `CareRecipeController.java.disabled` |
| CareSuggestionEngineController | 🔴 disabled | `CareSuggestionEngineController.java.disabled` |
| 皮肤检测（member_detection） | 🟡 | `init_member_detection.sql` |
| AI 前后对比照 | ✅ M17 层 | 见 M17 |
| 术后提醒（M16 旅程触发） | ⚠️ 框架在，事件发布缺失 | 见 M16 |
| 双人核销（stock_dual_verify_record） | ✅ | `migrate_p2_11_dual_verify.sql` |
| 前端 care 页面 | ✅ | `frontend/.../views/care/index.vue` |

### 🔴 关键缺口

- **所有 Care 控制器均为 `.disabled`**：`CareRecipeController.java.disabled` / `CareSuggestionEngineController.java.disabled` 等 5 个
- 前端 `care/index.vue`（21KB）存在，但后端 API 完全不存在 — 前端调用会 404
- `CustomerCareController`（`/crm/care`）存在但属于 CRM 客情，不是美容配方档案

---

### 3.2 美发店

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| 排队/候补系统（M13 waitlist） | ✅ | `init_m13_waitlist_scoring.sql` + `WaitlistService` |
| 动态定价实验（M14 pricing_experiment） | ✅ | `init_m14_pricing_experiment.sql` + `IntelPricingService` |
| 染护配方档案 | 🔴 | 无专门实体/服务 |
| 技师专属预约 | 🟡 | `BookingService` 有 `employeeId`，但无"锁定热门技师"专属逻辑 |
| 套餐组合快速下单 | 🟡 | M04 套餐/次卡有基础 CRUD |
| 前端 m09-invite（邀请码裂变） | ✅ | `frontend/.../views/m09-invite/index.vue` |

### 🔴 缺口

- 染护配方档案（发质记录）无独立实体/服务 — PRD 要求但未实现
- 技师专属预约的"锁定"逻辑未实现（排班冲突时无明确的"锁定稀缺技师"机制）

---

### 3.3 美甲美睫

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| 款式图库（style_library 表 + CRUD） | ✅ | `StyleLibraryController.java` + `StyleLibraryService` |
| 款式图库前端（图库 + 饼图 + 快速下单） | ✅ | `frontend/.../views/style-gallery/index.vue` |
| 款式图库 API | ✅ | `frontend/.../api/style-gallery.ts` |
| 材料包管理（style_package） | ✅ 表 + 🟡 服务 | `init_material_binding.sql` 有 style_package |
| StylePackageService | ❓ 未确认 | 需查 `StylePackage` 实体 |
| 会员款式偏好 | 🔴 disabled | `MemberStylePreferenceController.java.disabled` |
| 30分钟预约 | ✅ | `migrate_p2_13_booking_30min_check.sql` 有 slot=30min 配置 |

### 🔴 缺口

- `MemberStylePreferenceController.java.disabled` — 会员款式偏好记录不可用

---

### 3.4 SPA/会所

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| 场景模式主表（biz_spa_scene_mode） | ✅ | `init_spa_scene_mode_20260819.sql` |
| 场景-房间组合（biz_spa_scene_room_slot） | ✅ | 同上 |
| SpaSceneModeService + Impl | ✅ | `backend/.../service/impl/SpaSceneModeServiceImpl.java` |
| 前端 spa-scene 页面 | ✅ | `frontend/.../views/spa-scene/` |
| 房间+技师双资源排程 | 🟡 | `SpaSceneRoomSlotMapper` 有，资源调度逻辑需验 |
| 会员私密档案权限 | ❓ | 无专门权限控制实体 |
| 自助签到屏（kiosk） | ✅ | `frontend/.../views/kiosk/` + `kiosk.ts` API |
| 前端 kiosk 页面 | ✅ | `views/kiosk/index.vue`（约 10KB）|
| SpaFallbackController | 🔴 disabled | `SpaFallbackController.java.disabled` |
| SpaSceneModeController | 🔴 disabled | `SpaSceneModeController.java.disabled` |

### ⚠️ 细节问题

- `SpaSceneModeServiceImpl` 存在但对应 Controller 均为 disabled；前端通过哪个 API 通讯需确认

---

### 3.5 医美

| 功能点 | 状态 | 文件/路径 |
|---|---|---|
| 电子知情同意书 | ✅ | M18 层 |
| 药械批次追溯 | ✅ | `ConsentAndDrugDeviceService` 有 drug_device / batch / usage |
| 并发症 SOP 管理（complication_sop 表） | ✅ | `init_complication_sop.sql` |
| ComplicationService | ✅ | `backend/.../service/ComplicationService.java` |
| 术后跟踪回访计划 | 🟡 | `CustomerCareController`（`/crm/care`）有 follow_task，但非医美专项 |
| 照片/视频病历档案 | 🟡 | `MhVisualArchive` 有 image 管理，但无视频类型支持 |
| PIPL 合规 | 🟡 | `GdprService` + `P2ComplianceSuiteService` 有框架，PHI 加密未验 |
| 区块链存证 | 🔴 | `BlockchainController.java.disabled` |
| 员工培训记录 | ❓ | 无专门实体/Controller |
| 前端 complication 页面 | ✅ | `frontend/.../views/complication/` |

---

## 综合评分

| 区域 | 完成度 | 说明 |
|---|---|---|
| M16 自动化营销旅程 | 🟡 60% | 框架完整但事件发布链路断裂 |
| M17 视觉档案与AI比对 | 🟡 75% | 核心功能有，百度 Provider 缺失，腾讯调用错误 API |
| M18 电子知情同意 | 🟡 70% | 核心在，H5 端 disabled，区块链存证 disabled |
| 微信/支付宝/银联 | 🔴 40% | 无专用 adapter，靠统一 PosPayment 表 |
| 数字人民币 e-CNY | ✅ 85% | 硬/软/子钱包/离线/智能合约框架完整 |
| 花呗/芝麻信用 | 🔴 30% | HuabeiController disabled，芝麻分纯 mock |
| 储值/积分 | ✅ 95% | 完整实现 |
| Stripe/Square/PayPal | ✅ 90% | 真实 adapter 实现 |
| Gift Card | ✅ 90% | 完整 |
| BNPL | ✅ 80% | Klarna 真实 API，Afterpay 需确认 |
| 美容院 care | 🔴 40% | SQL 有但所有 Controller disabled |
| 美发店 | 🟡 55% | 候补/动态定价有，染护配方/技师锁定缺 |
| 美甲美睫 | 🟡 75% | 款式图库完整，偏好记录 disabled |
| SPA/会所 | 🟡 65% | 场景模式有，Controller 部分 disabled |
| 医美 | 🟡 70% | SOP/同意书/追溯有，区块链 disabled |

---

## Top 10 优先修复项

1. **[M16]** `MarketingEventPublisher` 未被任何业务服务调用 — 旅程触发完全依赖手动
2. **[支付]** 国内微信/支付宝无专用 adapter — 聚合支付不可靠
3. **[支付]** `HuabeiController.java.disabled` — 花呗分期链路不可用，芝麻分纯 mock
4. **[美容院]** 所有 Care 控制器 `.disabled` — 护理配方档案无法使用
5. **[M18]** `MhConsentH5Controller.java.disabled` — H5 端签署链路断
6. **[M18]** `BlockchainController.java.disabled` — 区块链合规存证不可用
7. **[M17]** `BaiduCvProvider` 缺失 — CvInferenceProvider SPI 不完整
8. **[M17]** `TencentCvProvider.inferSkin` 调用 `DetectFace` 而非皮肤分析 API
9. **[SPA]** `SpaSceneModeController.java.disabled` — 场景模式 API 不可达
10. **[医美]** PIPL 数据加密/脱敏实际执行未验证（`P2ComplianceSuiteService` 有框架但需验真）
