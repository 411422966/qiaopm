# 美业 SaaS 未开发功能 — 可执行开发任务清单

> **生成日期**：2026-08-16  
> **上游文档**：`FEATURE_COMPARISON_MATRIX.md`  
> **覆盖率**：25 个未开发功能 → 拆分为 9 个批次，**预计 22 周完成**  
> **执行方式**：每个批次遵循 IMPLEMENTATION_PLAN → COMPLETION_REPORT 流程

---

## 0 · 任务总览

| 批次 | 主题 | 任务数 | 工时 | 依赖 | 优先级 |
|------|------|--------|------|------|--------|
| **P4.1** | AI 智能排班 + LTV 真实验证 | 12 | 2 周 | 无 | P0 |
| **P4.2** | 配方/护理建议 + 多人共享套餐 | 10 | 2 周 | P4.1 | P0 |
| **P4.3** | 会员日自动化 + 生日营销 | 8 | 1.5 周 | P4.1 | P0 |
| **P4.4** | 美甲款式图库 + 上门服务（LBS） | 14 | 3 周 | P4.2 | P1 |
| **P4.5** | Gift Card + 订阅会员制 | 12 | 2.5 周 | P4.2 | P1 |
| **P4.6** | 境外支付 + Google/IG 国际预约 | 15 | 3 周 | P4.5 | P1 |
| **P4.7** | 自助签到 Kiosk + 医美并发症 SOP | 10 | 2 周 | 无 | P1 |
| **P4.8** | 独立资金风控 + Cohort 分析 | 12 | 2 周 | P4.3 | P1 |
| **P4.9** | 国际化合规（GDPR/HIPAA）+ 电商集成 | 14 | 3 周 | P4.6 | P2 |

**合计**：107 个可分配任务，预计 22 周（5.5 个月）

---

## P4.1 · AI 智能排班 + LTV/Churn 真实验证（2 周）

> **目标**：核心 AI 能力从 Mock 升级为真实可用模型

### 阶段 1：客流预测模型（3 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T1.1 | 客流历史数据 ETL 脚本 | 后端 | 0.5d | `etl/booking_history_etl.sql` |
| T1.2 | 时序特征工程（按小时/技师/项目分组） | 后端 | 0.5d | `BookingFeatureService.java` |
| T1.3 | 简易 Prophet/SARIMA 预测模型 | 后端 | 1d | `BookingForecastService.java` |
| T1.4 | 预测准确度评估（MAE/MAPE） | 后端 | 0.5d | `ForecastAccuracyService.java` |
| T1.5 | 前端客流热力图组件 | 前端 | 0.5d | `views/intel/forecast.vue` |

### 阶段 2：AI 排班生成器（4 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T1.6 | 排班约束建模（技师可用性/技能/工时） | 后端 | 1d | `ScheduleConstraintService.java` |
| T1.7 | 贪心+回溯排班算法 | 后端 | 1.5d | `AiScheduleGenerator.java` |
| T1.8 | 排班方案评分（资源利用率/客户偏好） | 后端 | 0.5d | `ScheduleScorer.java` |
| T1.9 | 排班推荐 API + 一键应用 | 后端 | 1d | `/ai/schedule/recommend` |

### 阶段 3：LTV/Churn 模型真实化（3 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T1.10 | LTV 训练数据准备 + 特征工程 | 后端 | 1d | `LtvModelService.java` 重构 |
| T1.11 | Churn 模型（XGBoost/LightGBM） | 后端 | 1d | `ChurnPredictService.java` |
| T1.12 | 模型 A/B 验证 + 准确度报告 | 后端 | 1d | `DataGovernanceService` 扩展 |

**完成标准**：
- ✅ 客流预测 MAPE < 25%
- ✅ 排班生成覆盖 80% 工作时段
- ✅ LTV 模型 MAPE < 30%
- ✅ Churn 模型 AUC > 0.7

---

## P4.2 · 配方/护理建议 + 多人共享套餐（2 周）

### 阶段 1：服务配方表（3 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T2.1 | `service_formula` + `service_formula_item` 表 | 后端 | 0.5d | 2 个 Entity + Mapper |
| T2.2 | 配方 CRUD API（项目 + 耗材 + 比例） | 后端 | 1d | `ServiceFormulaService.java` |
| T2.3 | 配方-服务绑定（自动扣减耗材） | 后端 | 1d | `BizService` 扩展 |
| T2.4 | 配方管理页面 | 前端 | 0.5d | `views/biz/formula.vue` |

### 阶段 2：护理建议引擎（3 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T2.5 | `care_suggestion_template` 表 | 后端 | 0.5d | Entity + Mapper |
| T2.6 | 基于肤质/发质/甲面的规则引擎 | 后端 | 1d | `CareSuggestionService.java` |
| T2.7 | 服务完成后自动推送护理建议 | 后端 | 0.5d | `MarketingAutomationService` 钩子 |
| T2.8 | 护理建议前端展示 | 前端 | 1d | `views/biz/care-suggestion.vue` |

### 阶段 3：多人共享套餐（3 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T2.9 | `package_share` + `package_shares` 表 | 后端 | 0.5d | Entity + Mapper |
| T2.10 | 共享套餐核销 API（多人分账/分次） | 后端 | 1.5d | `PackageShareService.java` |
| T2.11 | 共享套餐前端页面 | 前端 | 1d | `views/package/share.vue` |

**完成标准**：
- ✅ 配方管理完整闭环（CRUD + 自动扣减）
- ✅ 护理建议覆盖率 100%（服务完成后）
- ✅ 多人共享套餐支持 2-10 人

---

## P4.3 · 会员日自动化 + 生日营销（1.5 周）

### 阶段 1：会员日规则引擎（3 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T3.1 | `member_day_rule` 表（按月/周/自定义） | 后端 | 0.5d | Entity + Mapper |
| T3.2 | 会员日规则 CRUD API | 后端 | 1d | `MemberDayService.java` |
| T3.3 | 定时触发 + 优惠券自动发放 | 后端 | 1d | `@Scheduled` 钩子 |
| T3.4 | 会员日配置页面 | 前端 | 0.5d | `views/market/member-day.vue` |

### 阶段 2：生日营销自动化（4 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T3.5 | `birthday_campaign` + `birthday_coupon` 表 | 后端 | 0.5d | Entity + Mapper |
| T3.6 | 生日提前 N 天触达规则 | 后端 | 1d | `BirthdayMarketingService.java` |
| T3.7 | 生日礼包（优惠券/积分/次卡）组合 | 后端 | 1d | `BirthdayGiftService.java` |
| T3.8 | 生日营销管理页面 | 前端 | 1.5d | `views/market/birthday.vue` |

**完成标准**：
- ✅ 会员日规则生效（自动发券）
- ✅ 生日触达 100%（提前 7/3/1 天）
- ✅ 生日礼包配置灵活

---

## P4.4 · 美甲款式图库 + 上门服务 LBS（3 周）

### 阶段 1：美甲款式图库（4 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T4.1 | `nail_style` + `nail_style_image` 表 | 后端 | 0.5d | Entity + Mapper |
| T4.2 | 款式 CRUD + OSS 图片上传 | 后端 | 1d | `NailStyleService.java` |
| T4.3 | 款式-项目绑定（快速下单） | 后端 | 1d | `NailStyleQuickOrderService.java` |
| T4.4 | 款式图库前端（瀑布流 + 标签筛选） | 前端 | 1.5d | `views/nail/gallery.vue` |

### 阶段 2：上门服务调度（7 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T4.5 | `technician_location` + `dispatch_order` 表 | 后端 | 1d | 2 个 Entity + Mapper |
| T4.6 | 高德地图集成（地理编码/路径规划） | 后端 | 1.5d | `AmapLocationService.java` 扩展 |
| T4.7 | 技师实时定位上报（移动端 API） | 后端 | 1d | `/mobile/location/upload` |
| T4.8 | LBS 智能派单算法（距离+评分+技能） | 后端 | 2d | `LbsDispatchService.java` |
| T4.9 | 调度管理页面（地图可视化） | 前端 | 1.5d | `views/dispatch/board.vue` |
| T4.10 | 客户预约上门服务页面 | 前端 | 1d | `views/booking/home-service.vue` |
| T4.11 | 上门服务订单流程（出发/到达/服务/完成） | 后端 | 1.5d | `HomeServiceOrderService.java` |

**完成标准**：
- ✅ 款式图库可下单
- ✅ 上门服务从下单到服务完成全流程
- ✅ 派单算法自动选择最近 3 名技师

---

## P4.5 · Gift Card + 订阅会员制（2.5 周）

### 阶段 1：礼品卡（4 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T5.1 | `gift_card` + `gift_card_transaction` 表 | 后端 | 0.5d | Entity + Mapper |
| T5.2 | 礼品卡 CRUD（购买/赠送/兑换） | 后端 | 1d | `GiftCardService.java` |
| T5.3 | 礼品卡余额管理 + 退款规则 | 后端 | 1d | `GiftCardTransactionService.java` |
| T5.4 | 礼品卡支付集成（收银台可选） | 后端 | 0.5d | `PayController` 扩展 |
| T5.5 | 礼品卡管理页面 | 前端 | 1d | `views/market/gift-card.vue` |

### 阶段 2：订阅会员制（6 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T5.6 | `subscription_plan` + `subscription_instance` 表 | 后端 | 1d | Entity + Mapper |
| T5.7 | 订阅计划 CRUD（月/季/年/自定义） | 后端 | 1d | `SubscriptionPlanService.java` |
| T5.8 | 自动续费 + 支付网关集成（hook） | 后端 | 1.5d | `SubscriptionBillingService.java` |
| T5.9 | 订阅权益发放（项目次数/折扣/积分） | 后端 | 1d | `SubscriptionBenefitService.java` |
| T5.10 | 订阅会员生命周期（升级/降级/暂停） | 后端 | 0.5d | `SubscriptionLifecycleService.java` |
| T5.11 | 订阅管理页面 | 前端 | 1d | `views/market/subscription.vue` |
| T5.12 | 订阅卡支付集成 | 后端 | 1d | `PayController` 扩展 |

**完成标准**：
- ✅ 礼品卡全生命周期
- ✅ 订阅自动续费（微信/支付宝）
- ✅ 订阅权益自动发放

---

## P4.6 · 境外支付 + Google/IG 国际预约（3 周）

### 阶段 1：Stripe/Square 支付（5 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T6.1 | `pay_stripe_config` + `pay_square_config` 表 | 后端 | 0.5d | Entity + Mapper |
| T6.2 | Stripe SDK 集成（PaymentIntent） | 后端 | 1.5d | `StripePayService.java` |
| T6.3 | Square SDK 集成 | 后端 | 1.5d | `SquarePayService.java` |
| T6.4 | 支付方式配置页面 | 前端 | 0.5d | `views/sys/payment-global.vue` |
| T6.5 | 收银台多支付方式（境内+境外） | 前端 | 1d | `views/pos/checkout.vue` 扩展 |

### 阶段 2：BNPL 分期（3 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T6.6 | `bnpl_plan` 表（花呗/Afterpay/Klarna） | 后端 | 0.5d | Entity + Mapper |
| T6.7 | BNPL 申请 + 审核 + 还款计划 | 后端 | 1.5d | `BnplService.java` |
| T6.8 | BNPL 订单收银台集成 | 前端 | 1d | `views/pos/checkout.vue` 扩展 |

### 阶段 3：Google/Instagram 预约（5 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T6.9 | `booking_channel` 表（Google/IG/FB） | 后端 | 0.5d | Entity + Mapper |
| T6.10 | Google Business Profile API 集成 | 后端 | 2d | `GoogleReserveService.java` |
| T6.11 | Instagram DM 预约机器人（Webhook） | 后端 | 1d | `InstagramBookingService.java` |
| T6.12 | Facebook Booking 集成 | 后端 | 0.5d | `FacebookBookingService.java` |
| T6.13 | 多渠道预约统一接收（统一 API） | 后端 | 0.5d | `UnifiedBookingService.java` |
| T6.14 | 国际渠道管理页面 | 前端 | 0.5d | `views/booking/channels.vue` |
| T6.15 | i18n 字段补全（en/ja/ko） | 前端 | 1d | `i18n/*.json` |

**完成标准**：
- ✅ Stripe/Square 真实下单成功
- ✅ Google Reserve with Google 集成
- ✅ Instagram 预约可正常接收

---

## P4.7 · 自助签到 Kiosk + 医美并发症 SOP（2 周）

### 阶段 1：自助签到（4 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T7.1 | `kiosk_device` + `kiosk_check_in` 表 | 后端 | 0.5d | Entity + Mapper |
| T7.2 | 设备注册 + 心跳上报 API | 后端 | 1d | `KioskService.java` |
| T7.3 | 自助签到流程（扫码/手输/会员卡） | 后端 | 1.5d | `KioskCheckInService.java` |
| T7.4 | Kiosk 前端页面（大字号/触摸友好） | 前端 | 1d | `views/kiosk/check-in.vue` |

### 阶段 2：医美并发症 SOP（4 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T7.5 | `complication_sop` + `sop_step` 表 | 后端 | 0.5d | Entity + Mapper |
| T7.6 | SOP CRUD API（含应对步骤） | 后端 | 1d | `ComplicationSopService.java` |
| T7.7 | 并发症上报 + SOP 启动 | 后端 | 1d | `ComplicationReportService.java` |
| T7.8 | 术后回访任务流（关联 SOP） | 后端 | 0.5d | `FollowUpClosureService` 扩展 |
| T7.9 | SOP 管理页面 + 应急流程 | 前端 | 1d | `views/mh/sop.vue` |

### 阶段 3：术后跟踪升级（2 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T7.10 | 术后回访多阶段模板 | 后端 | 1d | `PostTreatmentFollowUpService.java` |
| T7.11 | 术后跟踪前端页面 | 前端 | 1d | `views/mh/post-treatment.vue` |

**完成标准**：
- ✅ Kiosk 自助签到 < 5 秒
- ✅ SOP 启动后自动联动回访

---

## P4.8 · 独立资金风控 + Cohort 分析（2 周）

### 阶段 1：风控规则引擎（5 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T8.1 | `risk_rule` + `risk_event` + `risk_blacklist` 表 | 后端 | 0.5d | Entity + Mapper |
| T8.2 | 规则引擎（Drools 或自研 DSL） | 后端 | 1.5d | `RiskControlService.java` |
| T8.3 | 大额退款自动拦截 | 后端 | 1d | `PosRefundService` 钩子 |
| T8.4 | 储值消费风控（异常频次/金额） | 后端 | 1d | `WalletService` 钩子 |
| T8.5 | 黑名单管理 + 命中告警 | 后端 | 0.5d | `BlacklistService.java` |
| T8.6 | 风控管理页面 | 前端 | 0.5d | `views/risk/console.vue` |

### 阶段 2：Cohort 分析（4 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T8.7 | `cohort_definition` + `cohort_snapshot` 表 | 后端 | 0.5d | Entity + Mapper |
| T8.8 | Cohort 计算引擎（按月/按周分组） | 后端 | 1.5d | `CohortAnalysisService.java` |
| T8.9 | 留存率/复购率统计 | 后端 | 1d | `CohortRetentionService.java` |
| T8.10 | Cohort 表格可视化（ECharts 热力图） | 前端 | 1d | `views/intel/cohort.vue` |

### 阶段 3：审计日志增强（1 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T8.11 | 资金变动审计（每笔记录可追溯） | 后端 | 0.5d | `AuditLogService.java` |
| T8.12 | 审计日志前端查询 | 前端 | 0.5d | `views/sys/audit-log.vue` |

**完成标准**：
- ✅ 风控规则命中率 > 80%
- ✅ Cohort 留存分析可查询任意周期

---

## P4.9 · 国际化合规（GDPR/HIPAA）+ 电商集成（3 周）

### 阶段 1：GDPR 合规（5 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T9.1 | `gdpr_consent` + `gdpr_request` 表 | 后端 | 0.5d | Entity + Mapper |
| T9.2 | Cookie 同意管理 | 后端 | 1d | `GdprConsentService.java` |
| T9.3 | 数据导出（Right to Access） | 后端 | 1d | `DataExportService.java` |
| T9.4 | 数据删除（Right to Erasure） | 后端 | 1d | `DataDeleteService.java` |
| T9.5 | GDPR 配置页面 | 前端 | 0.5d | `views/compliance/gdpr.vue` |

### 阶段 2：HIPAA 完整合规（5 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T9.6 | 医美数据加密（字段级 AES-256） | 后端 | 1.5d | `MedicalDataEncryptor.java` |
| T9.7 | 访问审计日志（PHI 访问全记录） | 后端 | 1d | `PhiAccessAuditService.java` |
| T9.8 | 最小权限控制（按角色/场景） | 后端 | 1d | `HipaaAccessControlService.java` |
| T9.9 | HIPAA 风险评估报告 | 后端 | 0.5d | `HipaaRiskReportService.java` |
| T9.10 | HIPAA 配置页面 | 前端 | 1d | `views/compliance/hipaa.vue` |

### 阶段 3：电商集成（4 天）

| # | 任务 | 类型 | 估时 | 交付物 |
|---|------|------|------|--------|
| T9.11 | `ecom_sync` 表（微店/有赞/Shopify） | 后端 | 0.5d | Entity + Mapper |
| T9.12 | 商品同步（双向） | 后端 | 1.5d | `EcomSyncService.java` |
| T9.13 | 订单同步 + 库存同步 | 后端 | 1.5d | `EcomOrderSyncService.java` |
| T9.14 | 电商对接配置页面 | 前端 | 0.5d | `views/integration/ecom.vue` |

**完成标准**：
- ✅ GDPR 数据导出/删除 7 天内响应
- ✅ HIPAA PHI 访问 100% 审计
- ✅ 电商订单可双向同步

---

## 附录 A · 任务依赖图

```
P4.1 (AI/LTV) ──┬─→ P4.2 (配方/共享) ──┬─→ P4.5 (Gift Card/订阅) ──┬─→ P4.6 (境外支付)
                │                       │                              │
                └─→ P4.3 (会员日) ──────┴─→ P4.8 (风控/Cohort) ───────┴─→ P4.9 (GDPR/HIPAA)

P4.7 (Kiosk/医美SOP) 独立并行

P4.4 (LBS/上门) 依赖 P4.2
```

## 附录 B · 资源/预算估算

| 类别 | 数量 | 备注 |
|------|------|------|
| 后端 Service | 25+ | 全部新增 |
| 数据库表 | 35+ | 全部新增 |
| 前端页面 | 30+ | 全部新增 |
| 第三方 SDK | 8 | Stripe/Square/BNPL/Google/IG/FB/高德 |
| 预计工作量 | 22 周 | 1 名高级 + 1 名前端 |

## 附录 C · 风险与缓解

| 风险 | 影响 | 缓解策略 |
|------|------|----------|
| 第三方 SDK 接入延期 | 高 | 准备 Mock 兜底，预留 adapter 抽象 |
| AI 模型准确度不达标 | 中 | 采用简单规则 + 渐进学习，预留人工干预 |
| 法规合规需求变动 | 中 | 模块化设计，配置化更新 |
| LBS 服务成本 | 中 | 高德按调用付费，需控制调用频次 |
| 上门服务合规（技师人身安全/医疗风险）| 高 | 必买保险 + 紧急按钮 + 实时定位 |

---

## 执行说明

1. **优先级**：P0 → P1 → P2 串行（除 P4.7 可并行）
2. **每个批次**：遵循 IMPLEMENTATION_PLAN → COMPLETION_REPORT → Reality 验证流程
3. **代码规范**：每个新 Service 需有单元测试（覆盖率 ≥ 70%）
4. **文档规范**：每个新功能需更新 `FEATURE_COMPARISON_MATRIX.md`

---

*本清单由 Cursor AI Agent 于 2026-08-16 生成*