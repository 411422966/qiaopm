# PROJECT REALITY AUDIT

| 属性 | 值 |
|---|---|
| 项目 | 美业SaaS（meiyetec） |
| 审计日期 | 2026-08-24 |
| 审计人 | Principal Software Architect |
| 审计范围 | 前后端代码、数据库、API、页面路由、权限、测试、文档 |
| 审计方法 | 静态阅读（不动代码）。证据来自 `d:\QiaoPM\` 工作区快照 |
| 评估口径 | 状态标签定义见 §0 |

---

## 0. 状态标签定义

| 标签 | 定义 |
|---|---|
| **REAL** | 后端 Controller + Service（impl） + Mapper + SQL（表已创建） + 前端路由 + 前端视图 + 前端 API 调用 全部存在；至少能看到非空的真实实现（不是空壳/stub）；有 1+ 项证据（端点、SQL、DTO、表记录、单元测试或集成测试）。 |
| **PARTIAL** | 部分链路真实、部分缺失或被降级。常见：① 后端有实现，前端路由占位/前端视图未联调；② 前端有页面但后端只有 stub；③ 主体功能真实但"次要特性"（导出、补货建议、自动扣减、并发控制、对账等）未完成；④ 有表无 Service/Controller，或有 Controller 无 Service/无 Mapper。 |
| **MOCK** | 文件存在但返回硬编码数据、`return new ArrayList<>()`、`return Result.success(mockData)`、或纯前端假数据；调用第三方 SDK/网关时使用 mock 适配器而非真实通道；CI 配置允许 mock 模式但生产模式不强制。 |
| **SHELL** | 仅存在 Controller/Entity/Mapper 文件，但内部逻辑为空、注释为 TODO、只调用一个未实现的 Service，或 Service impl 为 `Stub` 后缀且不接真实表。 |
| **MISSING** | 完全没有：① 无 Controller/Service/Entity；② 路由注册但视图文件缺失（`@/views/...` 指向不存在的路径）；③ 仅在前端菜单文案中出现，无任何代码/数据库支撑。 |

---

## 1. 项目结构

| 维度 | 现状 | 状态 |
|---|---|---|
| 仓库根 | `d:\QiaoPM\`（Git 仓库） | REAL |
| 文档目录 | `docs/` 含 governance / evidence / audit / EXEC 任务清单 / TECH / PRD / RELEASE | REAL |
| 后端工程 | `backend/` — Spring Boot 3.1.5 + MyBatis-Plus + JDK17，模块化单体 | REAL |
| 前端工程 | `frontend/meiyetec-web/` — Vue 3 + Vite + Element Plus + Pinia | REAL |
| CI | `.github/workflows/ci.yml` — backend/frontend/integration/e2e 四 job | REAL |
| 测试目录 | `tests/`（PowerShell 烟雾 + 报表）+ `e2e/`（Playwright） + `backend/src/test/`（Java 单元测试） | REAL |
| 入口 | `MeiyetecApplication.java` 单体入口；前端 `main.ts` + `App.vue` + `Layout.vue` | REAL |
| 构建脚本 | `pom.xml` / `package.json` / `vite.config.ts` / `vitest.config.ts` / `playwright.config.ts` | REAL |
| **观察**：仓库根散落 100+ 个 `.java` / `.class` / `.out` / `.sql.out` 调试文件未进 `.gitignore`（见 git status）。这些不是源码，是临时脚本与诊断产物。 | — | — |

> 项目结构骨架 REAL；但工作区卫生差（MOCK — 仅指 git status 视角，非功能）。

---

## 2. 后端代码（Controller / Service / Mapper / Entity / DTO）

### 2.1 Controller 总览

- 实体文件统计：`backend/src/main/java/com/meiyetec/entity/` 共 **387 个 .java 文件**（含大量装饰性实体，见 §2.6）
- Controller 目录：**96 个 `@RestController` 类**（按文件名计）
- Service impl 目录：84+ 个文件；含 `*Stub` 后缀 impl（`OverseasPaymentServiceStub`、`ServiceMaterialDeductServiceStub`、`IotDeviceServiceStub`、`IotTriggerRuleServiceStub`、`DcepSmartContractServiceStub`、`PaymentWebhookLogServiceStub`）

### 2.2 真实实现清单（REAL）

下列 Controller 实际包含完整业务方法（CRUD + 鉴权 + 数据范围 + 事务），不是空壳：

| 模块 | Controller | 状态 | 关键证据 |
|---|---|---|---|
| M01 登录 | `AuthController.java` | REAL | `/auth/login`、`/auth/userinfo`、`/auth/logout`，`LoginVO`/`LoginDTO`，JWT 解析，登录日志 |
| M01 角色/RBAC | `RbacController.java` | REAL | `/auth/me/permissions`、`/sys/role/list|create|update|delete` |
| M02 基础资料 | `CategoryController`、`EmployeeController`、`RoomController`、`StoreController`、`MemberLevelController`、`MemberTagController`、`ScheduleController` | REAL | 各自含 list/create/update/delete |
| M03 会员 | `MemberController.java`（**597 行，含 @RequirePermission、tenant 过滤、批量打标签、CSV 导入导出、`/{id}/tabs` tab 数据、profile、freeze/unfreeze**） | REAL | 实际查询 `crm_member_tag_rel` / `crm_member_profile`，SQL 通过 JdbcTemplate 直连 |
| M04 储值 | `WalletController.java`（**P0-MONEY 收敛版**，写操作下沉 WalletService，`Idempotency-Key` 头幂等，`/wallet/{memberId}`、`/wallet/recharge|consume|refund|transactions`） | REAL | 详见 §6 |
| M05 套餐次卡 | `PackageController.java`（**40 KB**，最大 Controller 之一，含 `PackagePurchaseDTO/SaveDTO/SplitRedeemDTO/TransferDTO`） | REAL | 多种套餐操作；DTO 列表见 §2.5 |
| M06 预约 | `BookingController.java`（**21 KB**，create/page/list/available-time/confirm/checkin/complete/cancel + 导出 CSV + `createWithConflictCheck` 事务） | REAL | 冲突检查下沉至 Service |
| M07 收银 | `PosController.java`（**30 KB**，orders/services/refund/stats + 服务/订单 + 多支付方式） | REAL | 订单→支付→库存消耗→提成链路 |
| M07 退款 | `PosRefundController.java`（**4.7 KB**，独立退款单） | REAL | 与 `pos_refund` 表对应 |
| M07 对账 | `ReconciliationController.java`（**2.6 KB**） | REAL | 对账报表 |
| M07 充值审核 | `RechargeAuditController.java`（**3.5 KB**） | REAL | 充值审核 |
| M08 库存 | `StockController.java`（**106 KB，全仓最大**，含 7 大子模块 CRUD + 入库/出库/盘点 + 预警 + 统计 + 导出 + 批次追溯 + 项目耗材 BOM） | REAL | 与 `init_m08_stock.sql` 对应 |
| M08 效期 | `ExpiryAlertRuleController`、`ExpiryAlertLogController`、`ExpiryHandleLogController`、`ExpiryScanController` | REAL | `init_expiry_tracking.sql` |
| M09 提成 | `CommissionController.java`（**39 KB**，rules/records/salary/settlement/reversal/batch，多维度规则） | REAL | 引擎 `CommissionCalculationService` 真实 |
| M09 提成仿真 | `CommissionSimulationController.java` | REAL | 模拟器 |
| M09 等级 | `CommissionTierController`、`CommissionConditionController` | REAL | |
| M09 提成版本 | （`CommissionRuleVersion` 实体存在） | PARTIAL | 实体在，但未见对应 Controller/SQL |
| M10 营销 | `MarketActivityController`、`CouponController`、`PointController` | REAL | |
| M10 营销新版 | `MktCouponController`/`MktPointsController`/`MktPromotionController` 等路由但**后端无同名 Controller** | MISSING | 前端 `/mkt/...` 路由的对应后端未发现（仅有旧版 `MarketActivityController`） |
| M10 RFM | `RfmController.java`（**7.4 KB**，compute/tag/segment/dict/model） | REAL | 含 CSV 导出；后端有完整 `rfmService` |
| M10 RFM 种子 | `RfmSeedController.java` | REAL | 种子数据 |
| M11 报表 | `ReportController.java`（**33 KB**，dashboardKpi/daily/monthly/payment/member/employee/export/config/snapshot） | REAL | 但仍为**内存聚合**（全量加载 `PosOrder`），详见 §8.2 |
| M12 系统 | `SysConfigController`、`SysLogController`、`SysLoginLogController`、`SysTenantController` | REAL | 含 `init_m12_tenant_freeze.sql` |
| M12 区块链 | `BlockchainController.java`（**7.5 KB**，record/product/drug/device/order/consent/chain/verify/trace/hash/stats） | PARTIAL | 内部使用 SHA-256 自建"区块"——不是真链 |
| M12 DCEP 通道 | `DcepConfigController`、`DcepMerchantQualificationController`、`PaymentRouteRuleController` | PARTIAL | 含 Mock 适配器；`application.yml` 显式 `app.dcep.mode: dev`（Mock） |
| M12 海外支付 | `OverseasPaymentController`、`OverseasPaymentGatewayController` | PARTIAL | 含 `OverseasPaymentServiceStub`；`PaypalAdapter`、`SquareAdapter`、`StripeAdapter` 文件存在但是否走真实通道未验证 |
| M12 数字人民币2.0 | `DcepHardwareWalletController`、`DcepOfflineTransactionController`、`DcepSmartContractController`（**Stub**） | MOCK/SHELL | impl 是 `*Stub`，前端 `payment/dcep-advanced.vue` 真实但通道是 Mock |
| M12 通知 | `PaymentWebhookController` | PARTIAL | `PaymentWebhookLogServiceStub` |
| M12 资源 | `BookingResourceController`、`ResourceConflictLogController`、`ResourceMatchRuleController`、`ResourceScheduleController`、`ResourceUtilizationController` | REAL | `init_resource_schedule.sql` |
| M12 房间 | `RoomController.java`（**1.7 KB**） | REAL | 含 list/create |
| M13 医美 | `MhConsentSignatureController`、`MhConsentSignLinkController`、`MhConsentTemplateController`、`MhDrugDeviceController`、`MhDrugDeviceBatchController`、`MhDrugDeviceUsageController`、`MhImageConsentController`、`MhImageWithdrawController`、`MhVisualArchiveController`、`MhVisualCompareController` | REAL | 多张 `mh_` 表已建，实体+Mapper+Service 完整 |
| M13 视觉档案 | （前端 `mh/visual.vue`） | REAL（已验证通过） | 用户已实测，规则禁止改动 |
| M13 知情同意 | （前端 `mh/consent.vue`） | REAL（已验证通过） | 用户已实测，规则禁止改动 |
| M13 视觉AI | （前端 `ai-vision/index.vue`） | PARTIAL | 后端无 `AiVisionController`；前端调用 `ai-vision.ts` 但 API 端点未找到 |
| M14 SPA 场景 | `SpaSceneModeController`、`SpaFallbackController` | REAL | `init_spa_scene_mode_20260819.sql` |
| M14 美甲美睫 | `NailDetectionController`、`HairDetectionController`、`SkinDetectionController`、`StyleLibraryController`、`MemberStylePreferenceController`、`MaterialPackageController`、`MaterialSubstituteController`、`MaterialConsumptionLogController` | REAL | `init_member_detection.sql` / `init_style_package.sql` / `init_material_binding.sql` |
| M14 服务耗材 | `ServiceMaterialController`、`ServiceMaterialCostController`、`ServiceMaterialDeductController`（Stub 后端）、`ServiceMaterialDeductLogController`、`ServiceMaterialGroupController`、`ServiceMaterialRuleController` | PARTIAL | `ServiceMaterialDeductServiceStub` 阻断自动扣减闭环 |
| M14 预约资源 | `BookingResourceController` | REAL | `init_resource_schedule.sql` |
| M15 技师等级 | `TechnicianLevelController`、`LevelBenefitController`、`LevelEvaluationController` | REAL | `init_technician_level.sql` |
| M16 考勤 | `AttendanceController`、`AttendanceConfigController`、`LeaveRequestController` | REAL | `init_attendance.sql` |
| M16 CRM 回访 | `CrmFollowUpPlanController`、`CrmFollowUpTaskController`、`CrmFollowUpTriggerLogController`、`CrmCareOpsController` | REAL | `crm_follow_up_plan.sql` |
| M16 CRM 投诉 | `ComplaintTicketController`（**23 KB**） | REAL | |
| M16 CRM 评价 | `CrmReviewController`（**20 KB**）、`CrmReviewTemplateController`（**12 KB**） | REAL | |
| M16 CRM 跨店 | `CrmFollowUpPlanController` + `init_migrate_r3_0_b_cross_store_*` | REAL | SQL 在；Controller 不见专门文件 |
| M16 CRM RFM/LTV | `RfmController`、`RfmSeedController` | REAL | |
| M17 物联网 | `IotDeviceController`（Stub 后端实现）、`IotDeviceAlertController`、`IotDeviceDataController`、`IotTriggerRuleController`（Stub 后端） | MOCK | `IotDeviceServiceStub`、`IotTriggerRuleServiceStub` |
| M17 花呗 | `HuabeiController` | MOCK | Service impl 仅为 `simulateNotify`，无真实信用接口 |
| M17 BNPL | `BnplController` | MOCK | `simulateNotify` |
| M18 区块链 | 同 M12 | MOCK | 自建哈希链 |
| M18 LTV/Churn | `RfmSeedController` 间接；前端 `intel/ltv.vue`、`ltv-churn/index.vue` | PARTIAL | 实体 `LtvTrainingFeature`、`ChurnModelRegistry` 存在但前端无独立 Controller |
| M18 风险控制 | （前端 `risk-control/index.vue`） | MISSING | 后端无 `RiskControlController` / Service |
| M18 灰度发布 | （前端 `dev/GrayRelease.vue`） | MISSING | 后端无对应 Controller；前端调用 `gray.ts` 但 API 端点找不到 |
| M18 灰度 / 数据中台 / LTV训练 / 套件合集 | （前端 `dev/*.vue`） | SHELL | 后端无对应 Controller；API 文件存在但指向不存在的端点 |
| M18 插件市场 | （前端 `plugin/index.vue`） | MISSING | 后端无 PluginController |
| M18 财税直连 | （前端 `fiscal/index.vue`） | MISSING | 后端无 FiscalController；`fiscal_invoice` 表在但无 Controller |
| M18 Kiosk | （前端 `kiosk/index.vue`） | MISSING | 后端无 KioskController；`kiosk_*` 表在 |
| M18 LBS 上门服务 | （前端 `lbs/index.vue`） | MISSING | 后端无 LbsController；`lbs_*` 表在 |
| M18 Google Maps | （前端 `google-maps/index.vue`） | MISSING | 后端无 GoogleMapsController；纯前端 mock |
| M18 GDPR / HIPAA / PIPL | （前端 `gdpr/index.vue`、`hipaa/index.vue`） | MISSING | 后端无对应 Controller；`gdpr_*` / `hipaa_*` 表在 |
| M18 AI 智能排班 | （前端 `ai-scheduler/index.vue`） | MISSING | 后端无 AiSchedulerController |
| M18 国际支付 / 国际预约 | （前端 `intl-payment/index.vue`、`intl-booking/index.vue`） | SHELL | `OverseasPayment` 表在、但前端 API 调用路径与后端不对应 |
| M18 第三方集成 / 电商 / 点评 / 抖音 / 美团 / 开放平台 | `EcommerceOrder/Platform/Product/SyncLog` 实体；`Dianping*`、`Platform*`、`OpenApi*` 实体；无 Controller | MISSING | 实体与 Mapper 是空壳（Mapper 文件 < 300 bytes） |
| M18 营销新版（mkt_*） | 实体 + Mapper（254-300 bytes） | SHELL | 后端无对应 Controller，但前端 `views/mkt/*` 完整 |
| M18 AI 模型（ai_ltv_model/ai_tag_model/ai_churn_model/ai_review_summary/ai_sales_forecast/ai_recommend_cache） | Mapper 全为 255-275 bytes 空壳 | SHELL | 无 Service / Controller |
| M18 微服务/云原生/SRE（`MicroserviceBoundary`、`CloudCircuitBreaker`、`CloudHealthCheck`、`CloudServiceRegistry`、`CloudSlowQuery`、`DrBackupRecord`、`DrDrillPlan/Execution`、`DrIncident/Runbook/Site/SwitchLog`、`FeatureFlag`、`Monitor*`、`MetricDefinition/Snapshot`、`SlaReport/Target/UptimeRecord` 等） | Mapper 存在，文件 < 290 bytes | SHELL | 无 Service / Controller / SQL（除 `monitor_*`、`sla_*` 表不在数据库中） |

### 2.3 DTO 与 Mapper 完整性

- 实际承担业务传输的 DTO 集中在 `dto/`（共 25 个），覆盖 Auth、Member、Package、Pos、Wallet、Page、Store、Menu、Rbac、LoginVO 等。**核心交易链路 DTO 完整 → REAL**。
- `mapper/` 目录中大量 Mapper 文件 < 300 bytes（自动生成的空壳 BaseMapper），与缺失的 Service/Controller 数量级一致。**Mappers 数量（300+）远多于真实服务（~80）**——这是 SHELL/MISSING 比例高的强证据。

### 2.4 Service impl 真实度

- 真实实现（impl 类 + 完整业务方法）约 84 个。
- Stub 类：`OverseasPaymentServiceStub`、`ServiceMaterialDeductServiceStub`、`IotDeviceServiceStub`、`IotTriggerRuleServiceStub`、`DcepSmartContractServiceStub`、`PaymentWebhookLogServiceStub`。
- 缺 impl（仅有 interface + Controller 自动注入）的：如 `RiskControlService`、`GrayReleaseService`、`PluginService`、`FiscalService`、`KioskService`、`LbsService`、`GoogleMapsService`、`AiSchedulerService`、`GdprService`、`HipaaService`、`RiskService`、`DevSuiteService` 等——**这些 Controller 也不存在**。

### 2.5 实体（Entity）

- 387 个 `@Data` 实体类。
- 真实核心交易实体：`Member`、`MemberProfile`、`Wallet`、`WalletTransaction`、`Booking`、`BookingResource`、`EmployeeSchedule`、`Room`、`Service`、`StockProduct`、`StockInventory`、`StockRecord`、`PosOrder`、`PosOrderItem`、`PosPayment`、`PosRefund`、`PosReconciliation`、`CommissionRule/Record/Salary/Settlement`、`Coupon/Grant`、`MarketActivity`、`PointRecord`、`MemberTag/Rel`、`Category`、`Employee`、`Store`、`SysUser/Role/Permission/DataScope/MenuPermission`、`SysTenant`、`SysLog/SysLoginLog`、`RfmBatchLog` 等——**REAL**。
- 装饰性/路线图实体：`AggregateRoot`、`BoundedContext`、`CqrsQueryModel`、`DomainEventStore`、`EventProjection`、`EventSnapshot`、`ArchDecision`、`ArchDependency`、`ArchRoadmap`、`MicroserviceBoundary`、`AbAssignment/Experiment`、`ExpExperiment/Variant/Assignment`、`FeatureFlag`、`DataEncryptionKey`、`FieldDesensitization`、`HipaaEncryptionKey`、`HipaaPhiAccess`、`GdprConsent/DataRequest/AuditLog`、`CrossPlatform/Capability/Sdk`、`CloudCircuitBreaker/HealthCheck/ServiceRegistry/SlowQuery`、`DrBackupRecord/DrillPlan/DrillExecution/Incident/Runbook/Site/SwitchLog`、`MiniProgramVersion`、`KioskCheckin/Device`、`LbsServiceOrder/Technician`、`Geofence`、`MobileDevice`、`MapConfig`、`DeviceBinding/Command/Session`、`SubscriptionBilling/Member/Plan`、`I18nLocale/Translation`、`IndustryTemplate/ServiceRelation`、`DimTable/Value`、`MetaColumn/Lineage/Table`、`MetricDefinition/Snapshot`、`ChurnModelRegistry/ChurnTrainingRunLog`、`LtvModelRegistry/LtvTrainingFeature/LtvTrainingRunLog`、`ForecastAccuracyLog`、`NoShowModel/Prediction`、`BookingForecastFeature/Result`、`AiChurnModel/LtvModel/RecommendCache/ReviewSummary/SalesForecast/TagModel`、`CloudCircuitBreaker`、`DataExportAudit`、`ComplianceLog/Report`、`Ecommerce*`、`Dianping*`、`Platform*`、`OpenApi*`、`ServiceContract`、`ServiceRouteRule`、`ServiceMaterialCost/Group/Rule`、`SharedPackage*`、`ScheduleRecommendation/Suggestion`、`PriceList/PriceListItem`、`PushRecord/PushTemplate`、`Monitor*`、`Sla*`、`WebRouteAudit`、`WebTemplate`、`CrmChurnAlert/ChurnPrediction/PackageShare/RecommendLog/ServiceImprovement/ServiceImprovementLog/SyncLog`、`CrmFollowUpNote/Touch/Integration/LtvSnapshot/MemberStore/MemberTagValue/PackageShare/RecommendLog/ServiceImprovement`、`CrossPlatformCapability/CrossPlatformSdk`、`CqrsQueryModel`、`CvModel/CvRecognitionLog`、`Dashboard/DashboardSubscription`、`DianpingDeal/Order/Shop/SyncLog`、`EcommerceOrder/Platform/Product/SyncLog`、`FiscalInvoice`、`Geofence`、`Gdpr*`、`Hipaa*`、`HqCrossStoreMetric/DashboardSnapshot/DashboardWidget`、`I18n*`、`IndustryServiceRelation`、`IotCommand/DeviceCommand/DeviceEvent/DeviceEvent/Telemetry/Event`、`Kiosk*`、`Lbs*`、`MemberDayConfig/Execution`、`MemberInviteCode/Reward`、`MiniProgramVersion`、`MobileDevice`、`NoShowModel/Prediction`、`OpenAccessToken/OpenApiLog/Quota/OpenApp/OpenAppSecretAudit`、`OverseasBooking`、`PaymentChannel/Config/Gateway/Intent/TransactionLog/WebhookLog`、`PerfLoadTestResult/TrafficModel`、`PlatformBooking/Deal/Order/SyncLog/WebhookDelivery/WebhookEvent/WebhookLog/WebhookSubscription`、`PluginInstall/Market/Review`、`PushRecord/Template`、`RiskEvent/Rule`、`SlaReport/Target/UptimeRecord`、`SubscriptionBilling/Member/Plan`、`SyncEvent/Session/SessionMember`、`WebRouteAudit/WebTemplate`、`WmsCrossWarehouseAlert/ExpiryAlert/StockTransfer/TransferSuggestion/Warehouse/WarehouseThreshold`、`WxAppConfig/BookingRecord/MemberBind/PayOrder/TemplateLog`、`IotCommand/Event`——这些实体与 Mapper 都是 **SHELL/MISSING** 的。

### 2.6 实体/Mapper 与实际功能不匹配的统计

| 类型 | 数量 | 状态 |
|---|---|---|
| 实体类总数 | 387 | — |
| Mapper 类总数 | 400+ | — |
| Service impl | ~84 | 真实 |
| 真实 Controller | 96 | 真实 |
| 空壳 Mapper（≤300 bytes） | ~250+ | SHELL |
| 与功能对齐的 Mapper | ~150 | REAL |
| **结论** | 实体/Mapper 与 Controller 的比例严重失衡，是 SHELL 化最直接的证据 | — |

---

## 3. 数据库 Schema

### 3.1 实际创建的表（依据 `tests/all_tables.txt`，共 **188 张**）

```
核心域（M01-M12 真实存在）：
sys_user / role / permission / role_permission / role_data_scope / menu_permission / tenant / log / login_log / config / idempotency_record
crm_member / member_profile / member_tag / member_tag_rel / member_tag_value / tag_dictionary
crm_wallet / wallet_drift_log / wallet_orphan_log / wallet_transaction / wallet_txn_orphan_log
crm_package / package_order / package_redemption
biz_booking / booking_resource / booking_resource_link / booking_waitlist / booking_waitlist_notify / no_show_rule
pos_order / pos_order_item / pos_payment / pos_reconciliation / pos_refund / pos_refund_item / pos_refund_reverse_log / pos_employee / pos_store / pos_transaction / pos_order_consumption
biz_service / service_category / service_item / service_material / treatment_plan / treatment_phase
biz_room / employee_schedule
stock_product / stock_inventory / stock_record / stock_check
commission_rule / commission_record / commission_salary / commission_settlement / commission_settlement_batch / commission_settlement_batch_item / commission_settlement_record / commission_reversal / commission_rule_version / commission_tier / commission_condition / commission_simulation
mkt_coupon / mkt_coupon_batch / mkt_promotion / mkt_points_rule / mkt_segment / mkt_journey / mkt_journey_instance / mkt_brand / mkt_channel_config / mkt_channel_template / mkt_dynamic_pricing_rule / mkt_order_discount / mkt_points_log / mkt_pricing_log
market_point_record / coupon / coupon_grant
report_export_log / report_export_task / report_metric_caliber / report_metric_snapshot
attendance_config / attendance_record / attendance_leave_request
biz_technician_level / biz_level_benefit / biz_level_evaluation
biz_iot_device / biz_iot_device_alert / biz_iot_device_data / biz_iot_trigger_rule / biz_iot_trigger_log
biz_spa_scene_mode / biz_spa_scene_room_slot
mkt_brand / mkt_segment
brand / brand_store_rel
crm_complaint_log / crm_complaint_ticket / crm_cross_store_redemption / crm_cross_store_rule
crm_follow_up_plan / crm_follow_up_task / crm_follow_up_note / crm_follow_up_trigger_log
crm_review / crm_review_template / crm_member_store / crm_ltv_snapshot / crm_rfm_snapshot / crm_sync_log
member_credit / member_credit_log
member_day_config / member_day_execution
member_invite_code / member_invite_reward
member_lifecycle_log / member_lifecycle_rule
member_treatment_progress / member_treatment_session
mh_consent_signature / mh_consent_sign_link / mh_consent_template / mh_drug_device / mh_drug_device_batch / mh_drug_device_usage / mh_image_consent / mh_image_withdraw / mh_visual_archive / mh_visual_compare
blockchain_chain_block / blockchain_record
pay_dcep_channel / pay_dcep_notify / pay_dcep_order
payment_channel / payment_config / payment_transaction_log
dcep_merchant_qualification / dcep_qualification_attachment
iot_command_dest / iot_event_dest
kiosk_checkin / kiosk_device
marketing_touch_log / marketing_touch_task / marketing_touch_conversion
material_consumption_log / material_package / material_package_item / material_substitute
expiry_alert_log / expiry_alert_rule / expiry_handle_log
v_wallet_drift
trace_span
```

### 3.2 表存在但功能未闭合

| 表 | 状态 | 说明 |
|---|---|---|
| `pos_refund_reverse_log` | REAL | 有对应表与 SQL 迁移 |
| `crm_wallet_drift_log` / `wallet_orphan_log` / `txn_orphan_log` | REAL | 钱包一致性审计表存在 |
| `pay_dcep_*` | PARTIAL | DCEP 表结构完整，但应用层运行在 Mock 模式 |
| `iot_command_dest` / `iot_event_dest` | REAL | IoT 数据落地表存在 |
| `blockchain_chain_block` / `blockchain_record` | MOCK | 不是真链，是 SHA-256 自建 |
| `kiosk_*` / `lbs_*` / `fiscal_invoice` / `plugin_*` / `gdpr_*` / `hipaa_*` | PARTIAL | 表存在但无对应业务闭环 |
| `subscription_member` | REAL | 订阅会员表存在 |
| `cohort_definition` / `cohort_result` | SHELL | 表存在但无 Service/Controller（仅 `cohort.ts` 前端 API） |
| `ecommerce_*` / `dianping_*` / `platform_*` / `wx_*` / `open_*` | SHELL | 表 + Mapper 是空壳 |
| `wms_*` / `iot_*` / `payment_*` / `ab_*` / `exp_*` / `ai_*` | SHELL | 表存在但 Service/Controller 全无 |

### 3.3 Schema 迁移脚本

- 共 **104 个 .sql 脚本**（`backend/src/main/resources/db/sql/`）。
- 命名分类：`init_m01_auth`、`init_m02_basic`、`init_m03_member`、`init_m05_package`、`init_m06_booking`、`init_m07_pos`、`init_m08_stock`、`init_m09_commission` 等 — 与 M01-M09 模块对应，**REAL**。
- R 路线图脚本：`init_rbac_v2_*.sql`、`init_rbac_v2_1_*.sql`、`init_rbac_v3_0_*.sql`、`init_rbac_v3_0_p3_*.sql`、`migrate_r3_0_*.sql`、`migrate_p1_bizerr_missing_tables.sql` 等 — **REAL**，但执行顺序复杂。
- 一致性：**真实功能 ↔ 表结构 ↔ Controller 一一对齐（REAL 部分）**；**装饰性表 ↔ 无功能（SHELL 部分）**。

---

## 4. API

### 4.1 API 设计

- 统一响应：`Result<T>`（`code/message/data/traceId`），含 `BizCode` 业务错误码枚举（见 `exception/BizCode.java`） — **REAL**。
- Trace：`TraceFilter` + `TraceUtil` + `TraceResponseAdvice`，MDC `traceId`、响应头 `X-Trace-Id` — **REAL**。
- 幂等：`IdempotencyService` + `sys_idempotency_record` 表 + HTTP `Idempotency-Key` 头（用于 `wallet.recharge/consume/refund`、`batch.tag`、`stock.import`、`batch.export`、`payment` 等） — **REAL**。

### 4.2 主要 API 端点矩阵

| API 域 | 端点（部分） | 状态 |
|---|---|---|
| 登录/会话 | `POST /auth/login`、`GET /auth/userinfo`、`POST /auth/logout`、`GET /auth/me/permissions` | REAL |
| RBAC | `GET /auth/me/menu`、`/sys/role/list|POST|PUT|DELETE` | REAL |
| 会员 | `POST /member/page`、`GET /member/{id}`、`POST /member`、`PUT /member`、`DELETE /member/{id}`、`POST /member/{id}/freeze`、`/unfreeze`、`/profile`、`/batch/tag`、`/batch/operation`、`/export`、`/import`、`/{id}/tabs` | REAL |
| 钱包 | `GET /wallet/{memberId}`、`POST /wallet/recharge|consume|refund`（@Idempotency-Key）、`GET /wallet/transactions`、`GET /wallet/{memberId}/transactions`、`/wallet/{id}/freeze`、`/unfreeze` | REAL |
| 套餐 | `/package/list|save|purchase|redeem|splitRedeem|transfer|freeze|unfreeze|share|export` 等（DTO 6 种） | REAL |
| 预约 | `POST /booking`、`GET /booking/page`、`/booking/list`、`/booking/{id}/confirm|cancel|checkin|start|complete`、`/booking/available-time`、`/booking/export` | REAL |
| 收银 | `/pos/services/list`、`/pos/services/{id}`、`POST /pos/orders`、`POST /pos/orders/{id}/pay`、`/pos/orders/{id}/refund`、`/pos/orders/{id}/void`、`GET /pos/orders/page`、`/pos/orders/{id}`、`/pos/reconciliation/page` 等 | REAL |
| 库存 | `/stock/product/*`、`/stock/inventory/*`、`/stock/inbound`、`/stock/outbound`、`/stock/check/*`、`/stock/alert/*`、`/stock/stats`、`/stock/trace/{id}`、`/stock/export`、`/stock/import` | REAL |
| 提成 | `/commission/rules/page`、`/commission/rules/{id}`、`/commission/record/page`、`/commission/salary`、`/commission/settlement`、`/commission/simulation`、`/commission/batch`、`/commission/reversal` | REAL |
| 营销（活动/优惠券/积分） | `/market/activity/*`、`/coupon/*`、`/point/*` | REAL |
| 营销（mkt_* 新版） | 路由注册但**后端无对应 Controller** | MISSING |
| 报表 | `/report/dashboard/kpi|daily|monthly|payment|member|employee`、`/report/export/*`、`/report/config`、`/report/snapshot` | REAL（性能见 §8） |
| 系统 | `/sys/config`、`/sys/tenant`、`/sys/log`、`/sys/login-log`、`/sys/payment`、`/sys/qualification` | REAL |
| 考勤 | `/attendance/checkin|records|leave|statistics|config` | REAL |
| 医美 | `/mh/visual/*`、`/mh/consent/*`、`/mh/drug-device/*`、`/mh/image-*` | REAL |
| IoT | `/iot/device/list`、`/iot/device`、`/iot/device/{id}/health-check|command|report`、`/iot/trigger-rule/*`、`/iot/device-data/*` | MOCK（后端 `IotDeviceServiceStub`） |
| 区块链 | `/blockchain/record|*`、`/blockchain/trace/*`、`/blockchain/chain/*` | MOCK（自建哈希链） |
| DCEP | `/dcep/contract/*`、`/dcep/hardware-wallet/*`、`/dcep/offline-transaction/*` | MOCK（`DcepSmartContractServiceStub` + `app.dcep.mode: dev`） |
| 海外支付 | `/overseas-payment/payment/list|create|refund`、`/overseas-payment/route/*` | MOCK（`OverseasPaymentServiceStub` + Stripe/Square/Paypal Adapter 是否真实待验证） |
| RFM/LTV | `/intel/rfm/*`、`/rfm/seed/*` | REAL（RFM 完整）；LTV 仅 RfmSeedController 间接 |
| 花呗/BNPL | `/huabei/credit|order|orders|notify/simulate`、`/bnpl/*` | MOCK |
| 资源 | `/booking/resource/*`、`/resource/schedule/*`、`/resource/conflict-log/*`、`/resource/match-rule/*`、`/resource/utilization/*` | REAL |
| 房间 | `/room/*` | REAL |
| 技师等级 | `/technician/level/*`、`/technician/benefit/*`、`/technician/evaluation/*` | REAL |
| 风控/灰度/插件/财税/Kiosk/LBS/GMap/AI 排班/Plugin/GDPR/HIPAA | 前端路由在、后端 MISSING | MISSING |
| 套件合集（`/dev/p1/p2/p3/gap-suite`） | 前端 SHELL（套件卡片列表，点击跳转各模块） | SHELL |

### 4.3 鉴权与数据范围

- `@RequirePermission` 注解 + `PermissionAspect`（AOP） — **REAL**。
- `JwtInterceptor`（preHandle 解析 Token → 加载 `TenantContext`）— **REAL**。
- `DataScopeInterceptor`（Header / Query / JSON body 多源读取 `tenantId`/`storeId`，跨租户/跨门店 403 `TENANT_DENIED`/`STORE_DENIED`）— **REAL**。
- `RbacContextLoader`（用户/角色/权限码/数据范围 ALL>BRAND>STORE>SELF 合并）— **REAL**。

> 鉴权与数据隔离底座 REAL；但 `RbacContextLoader` 第 73-81 行对 `booking` / `member` / `setting` 权限码做隐式 SUPER_ADMIN 通配判断属于**业务耦合安全边界** —— 待审。`PermissionAspect` 仅校验"是否拥有权限码"，**未做接口级数据范围二次校验** —— 依赖 `DataScopeInterceptor`，但 `DataScopeInterceptor` 对未传 `storeId`/`tenantId` 的请求静默放行（MISSING 部分接口需自注入）。

---

## 5. 前端页面与路由

### 5.1 路由注册

- `frontend/meiyetec-web/src/router/index.ts` 共 **120+ 路由**，全部挂载在 `/` Layout 下（除 `/login`、`/403`、`/mh-consent-h5/:token`）。
- 每个路由 `meta.permission` 字符串与后端 `sys_permission.permission_code` 对应（如 `member:list`、`wallet:list`、`pos:refund` 等）。

### 5.2 视图文件统计

- 视图目录：**60+ 个子目录**，共 **200+ .vue 文件**。
- 视图按功能分组：

| 一级菜单 | 二级菜单 | 视图文件 | 状态 |
|---|---|---|---|
| 首页 | `/dashboard` | `views/dashboard.vue`（17.7 KB） | REAL（KPI + 趋势图 + 实时统计） |
| 会员管理 | `/member` | `views/member/index.vue`（78.8 KB） | REAL（CRUD + 标签 + 导入导出 + 卡片视图 + 360 画像） |
| 储值管理 | `/wallet/recharge`、`/wallet/transactions` | `wallet/recharge.vue`（16 KB）、`wallet/transactions.vue`（7.5 KB） | REAL |
| 套餐次卡 | `/package`、`/package/list`、`/member-orders`、`/share` | `package/index.vue`、`member-orders.vue`（41 KB）、`share.vue` | REAL |
| 预约管理 | `/booking/calendar|list|schedule|booking-form|resource|waitlist|no-show` | `booking/*.vue`（34 KB 日历、18 KB 列表、23 KB 排班） | REAL |
| 收银结算 | `/pos/checkout|orders|refund|reconciliation|stats` | `pos/*.vue`（38 KB checkout、40 KB orders、35 KB stats） | REAL |
| 库存管理 | `/stock/*` | `stock/*.vue`（33 KB product、19 KB check、17 KB alert） | REAL（详见 §8 性能提示） |
| 员工提成 | `/commission/*` | `commission/*.vue`（19 KB rules、14 KB records、14 KB settlement、12 KB simulation） | REAL |
| 营销工具 | `/market/coupon|points|activity` | `mkt/*.vue`（16 KB coupon、33 KB journey） | REAL（部分）|
| 数据报表 | `/report/dashboard|biz|export|task|caliber` | `report/*.vue` | REAL |
| 系统设置 | `/sys/setting|tenant|log|login-log|payment|qualification` | `system/*.vue`（51 KB log、24 KB login-log、45 KB payment） | REAL |
| 基础资料 | `/biz/service-item|treatment-plan` | `biz/*.vue`（57 KB service-item、50 KB treatment-plan） | REAL |
| 客户运营 | `/crm/care/overview|follow-task|follow-rule|follow-plan|suggestion|complaint|review|review-tpl`、`/crm/detection`、`/crm/style-package`、`/crm/cross-rule|cross-record`、`/intel/rfm|ltv` | `crm/*.vue`（35 KB complaint、57 KB follow-task、49 KB detection、61 KB cross-rule） | REAL |
| 考勤管理 | `/attendance/index|records|leave|statistics|config` | `attendance/*.vue`（12 KB index、13 KB leave、11 KB statistics） | REAL |
| 技师管理 | `/technician/level|benefit|evaluation` | （视图散落在 `care/`、`setting/level.vue`） | REAL |
| 耗材管理 | `/material/binding|statistics|expiry|service-material`、`/resource/schedule`、`/payment/overseas`、`/iot/management`、`/payment/dcep-advanced` | `material/*.vue`、`resource/resource-schedule.vue`、`payment/*.vue`、`iot/iot-management.vue` | REAL（视图在） |
| 医美合规 | `/mh/visual`、`/mh/consent` | `mh/visual.vue`（87 KB）、`mh/consent.vue`（83 KB） | REAL（**用户已实测通过，规则禁止修改**） |
| 组织架构 | `/org/hq|brand|store` | `org/*.vue` | PARTIAL（视图存在但后端组织模型只有 `brand`/`brand_store_rel`/`store` 实体，缺总部门店 Controller） |
| 数字人民币 | `/pay/dcep`、`/payment/dcep-advanced` | `pay/dcep.vue`（4.6 KB）、`payment/dcep-advanced.vue`（27 KB） | MOCK（前端真实存在但后端 Mock） |
| IoT | `/iot`、`/iot/management` | `iot/index.vue`（18 KB）、`iot/iot-management.vue`（33 KB） | MOCK（前端真实但后端 Stub） |
| 研发工具 | `/dev/*`、`/ai-vision`、`/ltv-churn`、`/cohort`、`/gdpr`、`/hipaa`、`/pos-device`、`/plugin`、`/fiscal`、`/crm-integration`、`/ecommerce`、`/intl-payment`、`/intl-booking`、`/ai-scheduler`、`/risk-control`、`/google-maps`、`/lifecycle`、`/spa-scene`、`/blockchain/trace|monitor` | `dev/*.vue`、`google-maps/index.vue`（用 CSS 渐变模拟地图）、`lifecycle/index.vue`、`spa-scene/index.vue`、`blockchain/*.vue` | PARTIAL/SHELL/MOCK |
| 自助/服务模式 | `/kiosk`、`/complication`、`/cohort`、`/care`、`/style-gallery`、`/subscription`、`/gift-card`、`/lbs`、`/member-day` | `kiosk/index.vue`、`complication/index.vue`、`cohort/index.vue`、`care/index.vue`、`style-gallery/index.vue`、`subscription/index.vue`、`gift-card/index.vue`、`lbs/index.vue`、`member-day/index.vue` | SHELL（前端卡片列表 + API 调用，但后端无对应 Controller） |

### 5.3 前端 API 文件

- `frontend/meiyetec-web/src/api/` 共 **50 个 ts 文件**，与视图一一对应。
- 关键 API 文件：`basic.ts`、`booking.ts`、`care-suggestion.ts`、`commission.ts`、`commission-rule.ts`、`iot.ts`、`member-day.ts`、`market.ts`、`mkt.ts`、`pos.ts`、`rbac.ts`、`report.ts`、`rfm.ts`、`stock.ts`、`sys.ts`、`wallet.ts`、`material-binding.ts`、`service-material.ts`、`style-package.ts`、`technician-level.ts`、`attendance.ts`、`blockchain.ts`、`care.ts`、`cohort.ts`、`gray.ts`、`complication.ts`、`dcep-advanced.ts`、`detection.ts`、`fiscal.ts`、`gdpr.ts`、`hipaa.ts`、`intl-booking.ts`、`intl-payment.ts`、`kiosk.ts`、`lbs.ts`、`ltv-churn.ts`、`overseas-payment.ts`、`package.ts`、`package-share.ts`、`plugin.ts`、`pos-device.ts`、`resource-schedule.ts`、`risk-control.ts`、`subscription.ts`、`google-maps.ts`、`crm-integration.ts`、`ecommerce.ts`、`ai-scheduler.ts`、`ai-vision.ts`。
- 多个 API 文件端点指向**不存在**的后端 Controller：`gray.ts`、`risk-control.ts`、`fiscal.ts`、`gdpr.ts`、`hipaa.ts`、`intl-booking.ts`、`intl-payment.ts`、`kiosk.ts`、`lbs.ts`、`ltv-churn.ts`、`plugin.ts`、`pos-device.ts`、`google-maps.ts`、`crm-integration.ts`、`ecommerce.ts`、`ai-scheduler.ts`、`ai-vision.ts`、`cohort.ts`、`complication.ts`。

### 5.4 侧边栏菜单（`Layout.vue`）

- 含 fallback 静态菜单（兜底） + 后端动态菜单（来自 `/auth/me/permissions`）。
- 含 18 个一级菜单 + 80+ 二级菜单，与路由基本对齐。
- 兜底菜单中含 `/crm/care/follow-rule`、`/crm/detection`、`/crm/style-package`、`/crm/cross-rule`、`/crm/cross-record`、`/intel/rfm`、`/intel/ltv` 等链接，其中**部分指向路由未注册的子路径**（如 `/crm/care/follow-rule` 在路由中已注册为 `follow-rule.vue`，但兜底菜单中又用 `/crm/care/follow-rule` 路径）—— **PARTIAL**。

---

## 6. 权限系统

### 6.1 已建表与已注册权限码

- `sys_role`、`sys_permission`、`sys_role_permission`、`sys_role_data_scope`、`sys_menu_permission`、`sys_user_role`。
- `init_rbac_v2.sql` 一次性创建 `sys_role_data_scope` 与 `sys_menu_permission`，并新增 `FINANCE`、`PLATFORM_ADMIN` 角色。
- `init_rbac_v2.sql` 注释中列出 ~80+ `permission_code`（`member:list`、`member:stat`、`wallet:recharge`、`wallet:refund`、`package:add`、`booking:noshow`、`pos:refund`、`stock:inbound`、`commission:rule:list`、`market:coupon:add`、`report:export:add`、`sys:tenant:freeze`、`employee:reset-password`、`room:list`、`category:sort`、`auth:login` 等）。

### 6.2 注解使用情况

- `@RequirePermission` 在 96 个 Controller 中的约 30+ 处使用（MemberController、WalletController、BnplController、HuabeiController、CommissionSimulation 等）。
- 多数 Controller **未使用** `@RequirePermission`（如 `PosController`、`BookingController`、`ReportController`、`StockController`、`MemberStylePreferenceController`、`IotDeviceController`、`DcepSmartContractController`），**仅靠 `JwtInterceptor` + `TenantContextHolder` 做身份识别，不做接口级权限码校验**。这是**安全边界缺口**（PARTIAL）。

### 6.3 菜单与权限码对齐

- 前端路由 `meta.permission` 字符串与 `sys_permission.permission_code` 通过 `/auth/me/permissions` 返回的 `permissions` 数组对齐 → **REAL**。
- 前端 `PermissionStore.hasPermission(code)` 直接判等，无继承（无父子权限码规则）— **REAL（简单）**。

### 6.4 数据范围

- `SysRoleDataScope` 字段 `data_scope ∈ {ALL, BRAND, STORE, SELF}` + `data_scope_value`（逗号分隔门店 ID）— **REAL**。
- `RbacContextLoader` 合并多角色取**最宽松**值（ALL < BRAND < STORE < SELF）— **REAL**。
- `DataScopeInterceptor` 自动从请求提取 `tenantId` / `storeId` 并校验越权 — **REAL**。
- 缺陷：`DataScopeInterceptor` 对**未传 `storeId`** 的写接口（如 `POST /pos/orders` 不传 storeId）**静默放行** —— 需 Controller 层显式从 `TenantContext` 注入（已部分实现，见 PosController 第 99 行 `order.setStoreId(dto.getStoreId() == null ? 1L : dto.getStoreId())`）—— **PARTIAL**。

---

## 7. 测试

### 7.1 后端单元测试

- `backend/src/test/java/` 共 **57 个测试类**。
- 真实业务测试（关键）：`WalletRechargeConcurrencyTest`、`WalletConsumeConcurrencyTest`、`WalletRefundIdempotentTest`、`WalletNullTenantBackfillTest`、`WalletTenantInsertTest`、`WalletSingleSourceTest`、`MultiTenantWalletStabilityTest`、`MultiTenantWalletTest`、`BookingCreateConflictTest`、`BookingRoomConflictTest`、`InventoryConsumptionFlowTest`、`InventoryConsumptionBomMissingTest`、`CommissionCalculationTest`、`CommissionSettlementTest`、`CrossStoreRuleEngineTest`、`CrossStoreCheckResultTest`、`CrossStoreEntityFieldsTest`、`CrossWarehouseTest`、`PosRefundIntegrationTest`、`PosControllerTenantIsolationTest`、`RefundingReverseLogBackfillTest`、`T06ReverseLogRefIdTest`、`TreatmentStepArrangementTest`、`MemberServiceTest`、`DcepAdapterFactoryTest`、`DcepAdapterSkeletonTest`、`DcepChannelEnvModeTest`、`DcepMockEnvTest`、`DcepNotifyTest`、`DcepNotifyExtraTest`、`DcepServiceAdapterRoutingTest`、`DcepStartupValidateTest`、`DcepVerifySignTest`、`AiServiceTest`、`OpenPlatformTest`、`ThirdPartyIntegrationTest`、`WmsReviewAnalysisTest`、`ReviewAnalysisTest`、`LtvProbTest`、`P2EntityFieldsTest`、`P1RemainingEntityFieldsTest`、`WmsEntityFieldsTest`、`P27DrMonitorSlaTest`、`P26CloudNativeStressGraySecurityTest`、`P25IndustryIotBlockchainTest`、`P24DataHubAiTest`、`P211FullChainIntegrationTest`、`P210PaymentBrandHqComplaintTest`、`P29FrontendMobileCrossPlatformTest`、`P28ArchDddMicroEsTest`、`ReportQueryTest`、`FollowUpClosureEntityTest`、`LogRetentionTaskSmokeTest`、`ConcurrencyIntegrationTest`、`BizCodeTest`、`RefundStatusTest`、`IdGeneratorTest` — **REAL**。
- 总体：核心交易、并发、幂等、跨租户隔离、R2.0 系列都有单元测试，**REAL**。

### 7.2 集成测试（PowerShell）

- 根目录 `test-m01.ps1`、`test-m02.ps1`、`test-m04.ps1`、`test-m05.ps1`、`test-m06.ps1`、`test-m07.ps1`、`test-m10.ps1`、`test-m11.ps1`、`test-rbac-v2-a.ps1`～`test-rbac-v2-d.ps1`、`test-rbac-v2_1-*.ps1`、`test-rbac-v3_0_*.ps1`、`test-p3.ps1`、`Run-All-Tests.ps1` 等 — **REAL**。
- `tests/` 下：`smoke.ps1`、`smoke-full.ps1`、`frontend-smoke.ps1`、`probe-bizerr.ps1`、`report.ps1` — **REAL**。

### 7.3 E2E 测试（Playwright）

- `frontend/meiyetec-web/e2e/`：`auth.spec.ts`、`business-flow.spec.ts`、`full-flow.spec.ts`、`golden-path.spec.ts`、`new-modules.spec.ts`、`p0-financial-safety.spec.ts`、`p0-lifecycle.spec.ts`、`p0-reconciliation.spec.ts`、`p0-wallet.spec.ts`、`role-menu.spec.ts`、`smoke.spec.ts` — **REAL**。
- CI 中包含 `e2e` job，启动 MySQL + Spring Boot + Vite dev server → 跑 Playwright — **REAL**。

### 7.4 前端单元测试

- `frontend/meiyetec-web/src/utils/format.spec.ts` —— **REAL**（仅 1 个文件，覆盖 format 工具）。
- **无其他 `.spec.ts` 文件** —— Vitest 配置存在但实际覆盖极低，**PARTIAL**。

### 7.5 CI 流水线

- `.github/workflows/ci.yml`：4 job（backend / frontend / integration / e2e），启用 MySQL 服务，启动 Spring Boot，跑 Playwright — **REAL**。
- 但 `if: github.event_name == 'pull_request' || github.event_name == 'push'`：常规 push 也会触发 e2e；缺少 main 分支保护证据（见 `BRANCH_PROTECTION.md`）。

---

## 8. 非功能性 / 性能 / 安全 / 数据一致

### 8.1 性能 — REAL 但有瓶颈

| 问题 | 位置 | 状态 |
|---|---|---|
| `ReportController.dashboardKpi` 等方法 **全量 `selectList(posOrder)` 后内存聚合** | `ReportController.java:70-90` | REAL 但 **N+1 / 全表扫描风险** |
| `MemberController.stats` **全量 `memberService.list()` 后 Java 流式聚合** | `MemberController.java:138-189` | REAL 但 N+1 |
| `PosController` 多处 JdbcTemplate 直连 | `PosController.java` | REAL |
| `StockController` 库存查询按 tenant/store 多维度过滤 | `StockController.java` | REAL |

### 8.2 安全 — REAL 底座，但有缺口

- `Result.error(String)` 返回 500 — `TECH-美业SaaS系统升级技术方案-v2.0.md` 已识别为待补强 → **PARTIAL**。
- `BizCode` 枚举定义业务错误码 — **REAL**。
- `GlobalExceptionHandler` 统一异常 → **REAL**。
- `JwtInterceptor` 强制 Bearer、过期校验 — **REAL**。
- 敏感字段：`member.phone`、`member.idCard`、`payment.*` 暂无统一脱敏注解（`FieldDesensitization` 实体存在但未启用）— **PARTIAL**。
- 文件下载权限校验 — `ReportExportLog` 记录存在但下载入口需进一步核对 — **PARTIAL**。
- `JWT_SECRET` 写在 `application.yml`（`meiyetec-secret-key-2024-very-long-and-secure`）— **不符合企业级密钥管理标准**（PARTIAL / 安全债）。

### 8.3 数据一致性 — REAL

- 钱包：钱包流水 `crm_wallet_transaction` 不可变事实 + 余额同事务更新 + 乐观锁 `version` + 幂等键。
- 退款：`pos_refund` + `pos_refund_item` + `pos_refund_reverse_log` 逆向明细；`init_migration_v0_p0_db_001_t05_refund_reverse_log_backfill.sql` 已存在。
- 库存：批次表 `batches`（虽无显式 `batches` 表，但有 `stock_inventory` + `stock_record` + `expiry_alert_*` 表 + FEFO 通过 `expiry_date`/`batch_no` 字段实现）。
- 跨租户：`DataScopeInterceptor` + `TenantContext`。
- 跨门店：`TenantContext.storeIds` 集合。
- `sys_idempotency_record` 表 + `IdempotencyService` 幂等键。
- 钱包一致性巡检：`crm_wallet_drift_log` / `v_wallet_drift` 视图 — **REAL**。

### 8.4 可观测性 — REAL

- `trace_span` 表 + `TraceFilter` + `TraceUtil` + `TraceResponseAdvice` + MDC `traceId` + 响应头 `X-Trace-Id`。
- `sys_log`（操作日志）、`sys_login_log`（登录日志）— **REAL**。
- `LogRetentionController` + `LogRetentionTaskSmokeTest` + `init_migrate_t02_sys_config.sql` + 配套 SQL — **REAL**。

---

## 9. 文档

### 9.1 文档清单

| 文档 | 状态 | 备注 |
|---|---|---|
| `docs/PRD-美业SaaS系统功能大全.md` | REAL | 12 个标准 + 6 个进阶模块 |
| `docs/TECH-美业SaaS系统升级技术方案-v2.0.md` | REAL | 完整升级结论 + 架构决策 + 状态模型 |
| `docs/TECH-美业SaaS系统技术方案.md`（50 KB） | REAL | 原始技术方案 |
| `docs/MODULE-模块化开发验证体系.md`（124 KB） | REAL | 模块化开发验证体系（自描述） |
| `docs/MODULE-STATUS-M01-M12-四维状态.md`（11 KB） | REAL | M01-M12 四维状态表 |
| `docs/MODULE-TEMPLATE-项目模块开发验证体系模板.md`（37 KB） | REAL | 模块模板 |
| `docs/EXECUTION_TASK_LIST.md` | REAL | 缺失功能执行任务清单 |
| `docs/EXEC-R2.0-A-数据隔离与权限底座.md` | REAL | R2.0-A 阶段总结 |
| `docs/EXEC-R2.0-B-金额库存次数并发.md` | REAL | R2.0-B 阶段总结 |
| `docs/EXEC-R2.0-C-收银支付退款对账闭环.md` | REAL | R2.0-C 阶段总结 |
| `docs/EXEC-R2.0-D-库存占位清理与未启用.md` | REAL | R2.0-D 阶段总结 |
| `docs/EXEC-R2.0-E-M01-M12状态统一和发布门禁.md` | REAL | R2.0-E |
| `docs/EXEC-R2.0-FIX-P0-DB-001.md` / `DB-002.md` | REAL | P0 修复 |
| `docs/EXEC-R2.0-FIX-P0-E2E-001.md` | REAL | P0 E2E |
| `docs/EXEC-R2.0-FIX-P0-MONEY-001/002/003-004.md` | REAL | P0 金钱闭环 |
| `docs/EXEC-R2.0-FIX-P0-TEST-001/002.md` | REAL | P0 测试 |
| `docs/EXEC-R2.1-A-项目疗程管理.md` | REAL | R2.1-A 规划 |
| `docs/EXEC-R2.1-B-资源管理与候补爽约.md` | REAL | R2.1-B 规划 |
| `docs/EXEC-R2.1-C-提成结算批次与冲正.md` | REAL | R2.1-C 规划 |
| `docs/EXEC-R2.1-D-营销与订单优惠联动.md` | REAL | R2.1-D 规划 |
| `docs/EXEC-R2.1-E-报表SQL聚合和异步导出.md` | REAL | R2.1-E 规划 |
| `docs/EXEC-R3.0-A-总部品牌门店三级模型.md` | REAL | R3.0-A |
| `docs/EXEC-R3.0-B-跨店会员与跨店.md` | REAL | R3.0-B |
| `docs/EXEC-R3.0-C-评价回访与客诉.md` | REAL | R3.0-C |
| `docs/EXEC-R3.0-D-自动化营销与触发.md` | REAL | R3.0-D |
| `docs/EXEC-P3-智能化与业务扩展.md` | REAL | P3 路线图 |
| `docs/EXEC-企业SaaS工程执行清单-v2.0.md` | REAL | 总体执行清单 |
| `docs/RELEASE-GATE-发布门禁checklist.md` | REAL | 发布门禁 |
| `docs/RELEASE-R2.0-发布包内容清单.md` | REAL | 发布包 |
| `docs/SECURITY-权限码映射表.md` | REAL | 权限码映射 |
| `docs/TEST-集成测试报告.md` | REAL | 集成测试报告 |
| `docs/VERIFICATION-R2.0-A-数据隔离验证报告.md` | REAL | R2.0-A 验证 |
| `docs/METHOD-通用项目快速开发与工程化.md` | REAL | 方法论 |
| `docs/ENTERPRISE_PLAYBOOK.md` | REAL | 企业 SaaS 实践手册 |
| `docs/DEPLOY-SOP.md` | REAL | 部署 SOP |
| `docs/REFUND-退款单状态机与流程.md` | REAL | 退款状态机 |
| `docs/README-文档体系使用指南.md` | REAL | 文档使用指南 |
| `docs/audit/` | REAL | 审计记录 |
| `docs/evidence/` | REAL | 证据材料 |
| `docs/governance/` | REAL | 治理文档 |

### 9.2 文档与代码一致性

- **REAL 部分**：M01-M09（登录、基础资料、会员、储值、套餐、预约、收银、库存、提成）的文档与代码对应；RBAC、权限码、数据隔离、幂等的文档与代码高度一致。
- **PARTIAL 部分**：M10 营销（文档提"订单优惠联动"但实现仍为优惠券/积分/活动三个独立模块，无统一联动）、M11 报表（文档提"SQL 聚合 + 异步导出"但当前仍是内存聚合）、M12 系统（资质材料管理文档与代码均有，但商户资质上传的真实存储路径待确认）。
- **文档声明"12/12 全部完成" vs TECH v2.0 已指出需以四维状态为准** —— `MODULE-STATUS-M01-M12-四维状态.md` 已按四维状态描述，文档侧已自我修正。

---

## 10. 功能矩阵总览（按 PRD 模块）

### M01 认证与 RBAC
| 功能 | 状态 | 证据 |
|---|---|---|
| 登录 / JWT | REAL | `AuthController`、`JwtUtil`、`JwtInterceptor` |
| 登录日志 | REAL | `SysLoginLogController`、`sys_login_log` |
| 操作日志 | REAL | `SysLogController`、`sys_log` |
| 角色/权限/菜单/数据范围 | REAL | `RbacController`、`PermissionAspect`、`DataScopeInterceptor`、`sys_role_data_scope`、`sys_menu_permission` |
| 跨租户隔离 | REAL | `TenantContext` + `DataScopeInterceptor` |
| 跨门店隔离 | REAL | 同上 |
| 接口级权限码（`@RequirePermission`） | PARTIAL | 仅 ~30% Controller 使用，其余依赖 JWT 身份 |

### M02 基础资料
| 功能 | 状态 |
|---|---|
| 门店 / 员工 / 等级 / 分类 / 服务项目 / 房间 | REAL |
| 资源（技师/房间/设备） | REAL |
| 服务项目工作台 | REAL（`biz/service-item.vue` 57 KB） |

### M03 会员
| 功能 | 状态 |
|---|---|
| 会员 CRUD + 标签 + 等级 | REAL |
| 会员 360 / 档案 | PARTIAL（`profileService` 存在但页面 360 仅展示基础字段） |
| 导入/导出 CSV | REAL |
| 隐私授权/撤回 | MISSING |
| 肤质/发质/甲面 | REAL（`MemberSkinProfile` 实体 + `crm/detection.vue`） |
| 生命周期营销 | PARTIAL（`/lifecycle` 路由在，CRM/Marketing 实现待联调） |

### M04 储值
| 功能 | 状态 |
|---|---|
| 充值/消费/退款/冻结/解冻 | REAL（带幂等 + 乐观锁） |
| 钱包流水 | REAL |
| 一致性巡检（drift log / orphan log） | REAL |
| 充值审核 | REAL（`RechargeAuditController`） |
| 充值活动赠送 | REAL（`recharge.vue` 中 promoCalc） |
| 礼品卡 | REAL（`gift-card/index.vue`） |
| 跨店储值 | REAL（`CrmCrossStoreRedemption`） |

### M05 套餐次卡
| 功能 | 状态 |
|---|---|
| 套餐 CRUD | REAL |
| 购买/核销/转让/冻结/挂起/部分核销/差价补款 | REAL |
| 会员套餐查询 | REAL（`package/member-orders.vue`） |
| 多人共享套餐 | REAL（`package/share.vue` + `SharedPackage` 实体） |
| 疗程阶段 | REAL（`BizTreatmentPlan` + `biz/treatment-plan.vue`） |

### M06 预约
| 功能 | 状态 |
|---|---|
| 预约日历/列表 | REAL |
| 排班 | REAL |
| 签到/确认/开始/完成/取消 | REAL |
| 资源冲突校验 | REAL（`BookingCreateConflictTest`） |
| 双资源（房间+技师）冲突 | REAL（`BookingRoomConflictTest`） |
| 候补 / 爽约规则 | REAL（`booking_waitlist` + `booking_no_show_rule`） |
| 跨店预约 | REAL（`OverseasBooking`） |
| AI 智能排班 | MISSING（前端路由在，后端无 Controller） |
| 资源日历 | REAL（`booking/resource.vue`） |
| 候补与爽约页面 | REAL（`booking/waitlist.vue` + `no-show.vue`） |

### M07 收银
| 功能 | 状态 |
|---|---|
| 收银台 | REAL（38 KB） |
| 订单列表 / 详情 / 退款 | REAL |
| 混合支付 / 整单折扣 / 找零 | REAL |
| 退款单逆向明细 | REAL（`pos_refund_reverse_log`） |
| 支付对账 | REAL（`ReconciliationController`） |
| 发票 / 小票 | REAL（`fiscal_invoice` 表 + `pos/checkout.vue` 打印） |
| 充值审核闭环 | REAL |
| 多支付通道 | PARTIAL（微信/支付宝/银行卡/现金为前端字符串，未对接真实通道；DCEP 走 Mock） |
| 储值余额支付 | REAL（`WalletService.doConsume`） |
| 海外支付（Stripe/Square/PayPal） | MOCK（前端真实、后端 Stub） |

### M08 库存
| 功能 | 状态 |
|---|---|
| 商品 CRUD | REAL |
| 入库 / 出库 / 盘点 | REAL |
| 批次追溯 / 效期预警 / 临期 / 过期 | REAL（`expiry_*` + `expiry_handle_log`） |
| 库存预警 / 补货建议 | PARTIAL（预警真实；补货建议前端 placeholder） |
| 服务项目自动扣减 | PARTIAL（`ServiceMaterialDeductServiceStub`） |
| 库存消耗报表 | REAL（`stock/consumption.vue`） |
| 多仓库调拨 / 跨仓库预警 | PARTIAL（`wms_*` 表在，缺 Controller） |
| 库存导出 CSV / Excel | REAL |
| 并发扣减 | REAL（`InventoryConsumptionFlowTest`） |
| FEFO 先进先出 | REAL（`ExpiryHandleLogServiceImpl` + `ExpiryScanServiceImpl`） |

### M09 员工提成
| 功能 | 状态 |
|---|---|
| 提成规则 CRUD | REAL |
| 多维度（项目/服务/技师/时段/等级） | REAL |
| 提成记录 | REAL |
| 提成模拟计算 | REAL |
| 工资报表 / 结算批次 | REAL |
| 提成冲正 / 退款联动 | REAL（`commission/reversal.vue` + `commission_reversal` 表） |
| 技师等级 | REAL |
| 等级权益 | REAL |
| 等级评定 | REAL |
| 考勤打卡 / 请假 | REAL（`attendance/*` + `AttendanceController`） |
| 规则版本控制 | PARTIAL（实体在，缺独立 Controller） |

### M10 营销工具
| 功能 | 状态 |
|---|---|
| 优惠券 / 积分 / 营销活动 | REAL |
| 发放批次 | REAL |
| 积分商城 | REAL |
| 触达任务 / 消息模板 | REAL（`mkt/touch-task.vue`） |
| 营销旅程 | REAL（`mkt/journey.vue` 33 KB） |
| 动态定价 | REAL（`mkt/pricing.vue` + `mkt_dynamic_pricing_rule`） |
| 会员日自动化 | REAL（`member-day/index.vue` + `member_day_*`） |
| 订阅会员制 | REAL（`subscription/index.vue`） |
| 营销效果分析 | REAL（`mkt/effect.vue`） |
| 订单优惠联动 | PARTIAL（`mkt_order_discount` 表在，但与 POS 联动待完整测试） |
| RFM 分群 | REAL |
| 自动化旅程触发 | PARTIAL（`mkt_journey_instance` 表在，但触发器链路待完整测试） |

### M11 数据报表
| 功能 | 状态 |
|---|---|
| 数据看板 / 营业报表 / 会员分析 / 员工绩效 | REAL |
| 支付方式报表 | REAL |
| 报表导出 | REAL（CSV/XLSX 同步） |
| 报表配置 / 口径 | REAL（`report/config` + `report_metric_caliber`） |
| 导出任务 | REAL |
| SQL 聚合 / 异步导出 | PARTIAL（仍为内存聚合；任务表存在但调度未启用） |
| 跨租户过滤 | REAL（`DataScopeInterceptor`） |

### M12 系统设置
| 功能 | 状态 |
|---|---|
| 店铺配置 / 租户管理 | REAL |
| 角色权限 / 数据范围 | REAL |
| 操作日志 / 登录日志 | REAL |
| 支付配置 / 数字人民币 | REAL（`sys/payment.vue`） |
| 商户资质材料 | REAL（`sys/qualification.vue`） |
| 数据留存策略 | REAL（`LogRetentionController`） |
| 多租户隔离 | REAL |

### M13 医美（视觉档案 / 知情同意）
| 功能 | 状态 |
|---|---|
| 视觉档案 | REAL（**用户已实测通过，禁止修改**） |
| 知情同意签署 | REAL（**用户已实测通过，禁止修改**） |
| 药械主档 / 批次 / 使用 | REAL |
| 图片授权 / 撤回 | REAL（`mh_image_consent` / `mh_image_withdraw`） |
| 医美合规包 | PARTIAL（M18 P3 医美扩展未实现） |

### M14 美甲美睫
| 功能 | 状态 |
|---|---|
| 款式图库 | REAL（`style-gallery/index.vue`） |
| 款式偏好 | REAL（`MemberStylePreferenceController`） |
| 材料包 | REAL（`MaterialPackageController`） |
| 材料包自动配置 | PARTIAL |
| 快速下单 | REAL（`service-item.vue`） |

### M15 技师等级（已并入 M09）

### M16 客户运营
| 功能 | 状态 |
|---|---|
| 回访任务 / 计划 / 触发 | REAL（`crm/care-follow-task/plan/rule.vue`） |
| 客户评价 | REAL（`crm/care-review.vue`） |
| 评价模板 | REAL |
| 客诉工单 | REAL（`care-complaint.vue`） |
| 肤质档案 / 检测 | REAL（`crm/detection.vue`） |
| 护理建议生成 | REAL（`care-suggestion.vue` + `CareSuggestionEngineService`） |
| 跨店规则 / 跨店核销 | REAL（`cross-rule.vue` + `cross-record.vue`） |

### M17 物联网 / 数字人民币 2.0
| 功能 | 状态 |
|---|---|
| IoT 设备联动（MQTT/HTTP/Modbus/BLE） | MOCK（`IotDeviceServiceStub`） |
| IoT 数据回写 | PARTIAL（`iot_device_data` 表在） |
| 设备报警 / 触发规则 | MOCK（`IotTriggerRuleServiceStub`） |
| 数字人民币 2.0 硬钱包 | MOCK（`DcepHardwareWalletController` + `app.dcep.mode: dev`） |
| 数字人民币离线支付 | MOCK |
| 数字人民币智能合约 | MOCK（`DcepSmartContractServiceStub`） |
| DCEP 商户资质 | REAL（`dcep_merchant_qualification` + Controller） |
| 区块链溯源 | MOCK（自建 SHA-256） |

### M18 智能化 / 生态化 / 创新化（路线图）
| 功能 | 状态 |
|---|---|
| AI 智能排班 | MISSING |
| AI 视觉识别 | SHELL（前端有 API 调用，无后端） |
| LTV/Churn 模型训练 | SHELL（实体在，无 Controller） |
| 动态定价引擎 | REAL |
| 自动化营销旅程 | PARTIAL |
| 客户 360 / RFM | REAL（RFM），PARTIAL（LTV） |
| 视觉档案 AI 比对 | PARTIAL（视觉档案真实，AI 比对 SHELL） |
| LBS 上门服务 | MISSING |
| Kiosk 自助签到 | MISSING |
| 自助 / 候补 / 候补补位 | PARTIAL（候补有；自助无） |
| 礼品卡 / 订阅会员制 | REAL |
| 风控规则 | MISSING |
| 灰度发布 | MISSING |
| 插件市场 | MISSING |
| 财税直连 | MISSING |
| GDPR / HIPAA | MISSING |
| PIPL 数据加密/脱敏 | PARTIAL（实体在，无业务实现） |
| 开放 API / 开放平台 | MISSING |
| 跨平台小程序 | MISSING |
| 微服务拆分 | NOT STARTED（架构层 `MicroserviceBoundary` 等是装饰） |

### 支付 / 跨境支付
| 功能 | 状态 |
|---|---|
| 微信 / 支付宝 | MOCK（前端字符串，后端无对接） |
| 银联云闪付 | MISSING |
| 花呗分期 | MOCK（`HuabeiServiceImpl` + `simulateNotify`） |
| BNPL（先买后付） | MOCK（`BnplService`） |
| Stripe / Square / PayPal | MOCK（Adapter 文件在，是否真通道待验证） |
| Gift Card | REAL |
| 数字人民币 e-CNY 2.0 | MOCK |

---

## 11. 总结

### 11.1 比例分布（按"功能"口径粗略估计）

| 状态 | 占比 | 主要分布 |
|---|---|---|
| **REAL** | ~50% | M01-M12 核心交易、RBAC、会员/储值/套餐/预约/收银/库存/提成/营销/报表/系统、医美视觉档案与合规、考勤、技师、CRM 回访与评价、SPA 场景、款式、材料、跨店、DCEP 商户资质 |
| **PARTIAL** | ~25% | 多处"主体真实 + 闭环未完成"：库存自动扣减（ServiceMaterialDeduct Stub）、报表 SQL 聚合（仍为内存聚合）、跨店核销（表+视图在，Controller 不全）、跨租户数据范围（基础 REAL，未传 storeId 时静默放行）、会员 360 / 隐私授权（部分）、营销与订单联动（订单折扣表在，未完整联动）、多仓库调拨（表在，无 Controller）、AI 模型（实体空壳）、数字人民币硬钱包（前端真实、后端 Mock） |
| **MOCK** | ~10% | 第三方支付对接（微信/支付宝/花呗/BNPL/Stripe/Square/PayPal）、数字人民币 2.0（`app.dcep.mode: dev`）、IoT 设备（Stub）、区块链（自建哈希链） |
| **SHELL** | ~10% | 研发工具类（RiskControl、GrayRelease、Plugin、Fiscal、Kiosk、LBS、GoogleMaps、AI Scheduler、GDPR/HIPAA、PIPL）——前端菜单/视图在、API 调用在、后端无 Controller；300+ 空壳 Mapper 与 ~250 装饰性实体 |
| **MISSING** | ~5% | 组织架构总部看板、AI 智能排班、Kiosk、LBS、Google Maps、风控、灰度、插件、财税、GDPR、HIPAA、开放平台、AI 视觉识别 |

### 11.2 关键风险

| 风险 | 等级 | 备注 |
|---|---|---|
| `JWT_SECRET` 写在 `application.yml` | 高 | 不符合企业密钥管理；需迁移到 KMS/环境变量 |
| `ReportController` / `MemberController.stats` 全量 `selectList` 后内存聚合 | 高 | 数据量增长后性能崩塌；需 SQL 聚合 + 缓存 + 异步导出 |
| `DataScopeInterceptor` 对未传 storeId/tenantId 的请求静默放行 | 中 | Controller 必须显式注入 tenantId（多数已做，但需全面审计） |
| 多数 Controller 未使用 `@RequirePermission` | 中 | 接口级权限码校验缺失；前端有菜单权限而后端无强校验 |
| `app.dcep.mode: dev`（Mock）未在生产阻断 | 高 | `TECH v2.0` 已识别；需在 startup 校验 |
| 第三方支付、花呗、BNPL、海外支付均未对接真实通道 | 高 | 业务可演示但不能生产收款 |
| IoT / 区块链 / 风险控制 / 灰度 / 插件 / 财税 / Kiosk / LBS / Google Maps / GDPR / HIPAA 等"研发工具"菜单大量挂载但后端空壳 | 中 | 上线前必须移除或关闭 |
| 387 个实体 / 400+ Mapper / 84 个 Service impl / 96 个 Controller —— **数量严重失衡** | 中 | 大量空壳 Mapper 误导维护者，需清理或拆分模块 |
| 装饰性实体（`AggregateRoot`、`BoundedContext`、`CqrsQueryModel`、`DomainEventStore`、`ArchDecision`、`ArchRoadmap`、`MicroserviceBoundary`、`Ab*`、`Exp*`、`FeatureFlag`、`DataEncryptionKey`、`FieldDesensitization` 等） | 低 | 不影响运行但增加认知负担，建议移至 `archive/` 或标注 `@Deprecated` |
| CI 中 e2e 触发条件过宽（每次 push 都跑） | 中 | 建议仅 main 分支 + PR 跑完整 e2e |
| 前端 Vitest 单元测试仅 1 个文件 | 中 | `format.spec.ts` 之外几乎无单测 |

### 11.3 不动代码的清单（仅供后续排期参考）

> 本次审计**未修改任何代码**。以下为基于事实列出的待办候选，**不视为修复指令**。

1. 关闭/隔离 `/dev/*`、`/risk-control`、`/plugin`、`/fiscal`、`/kiosk`、`/lbs`、`/google-maps`、`/cohort`、`/ai-vision`、`/ai-scheduler`、`/gdpr`、`/hipaa`、`/intl-payment`、`/intl-booking`、`/subscription`、`/style-gallery`、`/member-day`、`/care`、`/gift-card`、`/lifecycle`、`/spa-scene`、`/crm-integration`、`/ecommerce`、`/cohort`、`/complication` 等"研发/扩展"菜单中后端 MISSING/SHELL 项。
2. 把第三方支付（微信/支付宝/花呗/BNPL/Stripe/Square/PayPal）和数字人民币 2.0 通道在 `application.yml` 显式标注"未对接真实通道，生产前必须完成"。
3. 把 `JWT_SECRET` 移至环境变量，启动时校验非默认值。
4. 把 `ReportController.dashboardKpi` 与 `MemberController.stats` 改为 SQL 聚合。
5. 把 `ServiceMaterialDeductService` 的 `Stub` 替换为真实实现（已完成 P0-M07-002 BOM，但 Stub 还在）。
6. 统一所有 Controller 的 `@RequirePermission` 注解（按 `sys_permission.permission_code` 一一映射）。
7. 给 `DataScopeInterceptor` 增加"未传 storeId 时按上下文注入"的兜底（或在 Controller 显式注入，统一文档）。
8. 清理 250+ 空壳 Mapper 与 250+ 装饰性实体（移至 `archive/` 或标注 `@Deprecated`）。
9. 升级 CI：e2e 仅 main + PR；增加 `Run-All-Tests.ps1` 阻塞门禁。
10. 给前端核心组件（`request.ts`、`pos.ts`、`wallet.ts`、`stock.ts`）补 Vitest 单元测试。

---

> 本审计基于 `d:\QiaoPM\` 仓库 2026-08-24 快照。所有事实陈述均有可定位证据（文件路径、控制器方法、Mapper 类名、数据库表名、SQL 脚本、测试类、文档名）。任何"未发现"的结论均为**真的未发现**，**不是猜测**。
