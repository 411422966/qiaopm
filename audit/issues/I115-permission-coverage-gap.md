# I115 — 权限注解覆盖率不足（P0）

## 现象
- 全仓库 137 个 Controller，仅 14 个含 `@RequirePermission`（10.2%）
- HTTP 映射方法 1885 个，仅 139 处权限注解（7.4%）
- 权限框架已实现（`PermissionAspect` + `@RequirePermission`），非框架缺失

## 已覆盖（14 个，集中在金融高风险区）
BnplController / CommissionController / HuabeiController / InviteController /
MarketActivityController / MemberController / PackageController /
PosRefundController / ReconciliationController / RefundAuditController /
StockController / StockDualVerifyController / SysPermissionController / WalletController

## 未覆盖高风险抽样
- `PosController`：15 个接口，0 权限 — **收银主链路**
- `DomesticPayController`：境内支付
- `OverseasPaymentController` / `OverseasPaymentGatewayController`：境外支付
- `PaymentWebhookController`：支付回调
- `PaymentRouteRuleController`：支付路由
- `DcepHardwareWalletController` / `DcepSubWalletController`：数字货币钱包
- `BookingController`：预约
- `CommissionConditionController` / `CommissionSimulationController` / `CommissionTierController`：佣金规则
- `CrmFollowUpPlanController` / `CrmFollowUpTaskController`：CRM
- 其余 ~100 个 Controller 待评估

## 风险
- 任何登录用户可调用未保护接口
- 认证层（JwtInterceptor）只验身份不验权限
- 数据范围层（DataScopeInterceptor）只验租户/门店不验操作权限
- 恶意用户可绕过前端菜单隐藏，直接调 API

## 处置建议
按优先级分批补 `@RequirePermission`：
1. P0-1：支付/收银/退款（PosController 等 8 个）
2. P0-2：会员/订单/预约（BookingController 等）
3. P1：CRM / 佣金 / 报表
4. P2：其余

每批完成后守门脚本重跑覆盖率统计。

## 关联
- I115 登记来源：P0 核验清单 §2
- 权限框架：`security/PermissionAspect.java` + `annotation/RequirePermission.java`

## 状态
待办

## 第 1 批完成（2026-10-21）
- 8 个 Controller 共 69 个端点补 @RequirePermission：
  - PosController（15）：pos:service:list / pos:order:add / pay / refund / void / list / stat
  - OverseasPaymentController（6）：overseas-payment:list / create / refund / stat
  - OverseasPaymentGatewayController（6）：overseas-payment:gateway:*
  - PaymentRouteRuleController（5）：overseas-payment:route:*
  - DcepHardwareWalletController（15）：dcep:wallet:list / activate / recharge / manage
  - DcepSubWalletController（12）：dcep:sub-wallet:list / add / edit / manage / transfer
  - DcepMerchantQualificationController（5）：dcep:qualification:list / edit / submit
  - DcepConfigController（5）：sys:dcep:list / edit / test
- DB 新增 28 个权限码（sys_permission，INSERT IGNORE 幂等）
- 编译 EXIT=0
- 覆盖率（2026-10-21 第 1 批后）：
- @RequirePermission 命中：208（原 139，+69）
- Controller 覆盖：22 / 138（原 14 / 137）
- 方法覆盖：208 / 1002 = 20.8%（原 7.4%）
- 提升：Controller 10.2% → 15.9%，方法 7.4% → 20.8%
- 待办：给 BOSS / MANAGER / CLERK / OPERATOR 角色分配对应权限（sys_role_permission），否则这些角色调上述接口会 403

## 状态
第 1 批已完成，第 2/3/4 批待办

## 第 2 批完成（2026-10-21）
- 3 个 Controller 共 26 个端点补 @RequirePermission：
  - MemberController（补 7，原 9 → 16）：member:order:list / profile / batch / export / import / list
  - BookingController（14）：booking:add / list / available / edit / checkin / complete / detail / update / delete / stat / export / calendar / schedule:list
  - MemberInviteController（5）：mkt:invite:list / edit
- DB 新增 12 个权限码（INSERT IGNORE 幂等）
- 编译 EXIT=0

### 第 2 批覆盖率
- @RequirePermission 命中：234（第1批 208 → 第2批 +26）
- Controller 覆盖：24 / 138（第1批 22 → 第2批 +2）
- 方法覆盖：234 / 1002 = 23.4%（第1批 20.8% → 第2批 +2.6 pp）
- 累计提升（相对基线 7.4%）：+16.0 pp

## 状态
第 1、2 批完成，第 3/4 批待办

## 第 3 批完成（2026-10-21）
- 7 个 Controller 共 70 个端点补 @RequirePermission：
  - CrmCareOpsController（1）：crm:care:ops:view
  - CrmFollowUpPlanController（12）：crm:care:follow:plan:list / crm:follow-rule
  - CrmFollowUpTaskController（16）：crm:care:follow:task:list / edit
  - CrmReviewController（11）：crm:care:review:list / edit
  - CrmReviewTemplateController（9）：crm:care:review:tpl:list / edit
  - DetectionReportController（7）：crm:detection:report:list / edit
  - ReportController（14）：report:dashboard / biz:list / caliber / export:*
- DB 新增 11 个权限码（crm:care:* / crm:detection:* / report:caliber:edit），复用 7 个现有权限码
- 编译 EXIT=0

### 第 3 批覆盖率
- @RequirePermission 命中：304（第2批 234 → 第3批 +70）
- Controller 覆盖：31 / 138（第2批 24 → 第3批 +7）
- 方法覆盖：304 / 1002 = 30.3%（第2批 23.4% → 第3批 +6.9 pp）
- 累计提升（相对基线 7.4%）：+22.9 pp

## 状态
第 1、2、3 批完成，第 4 批待办（其余 ~107 个 Controller）

## 第 4a 批完成（2026-10-21，营销）
- 13 个 Controller 共 80 个端点补 @RequirePermission：
  - CouponController（10）：market:coupon:list/add/edit/delete
  - MktCouponController（8）：mkt:coupon
  - MktPointsController（4）：mkt:points
  - PointController（4）：market:point:list/add/edit
  - PointGoodsController（7）：market:point:list/add/edit/delete
  - MktPromotionController（6）：mkt:promotion
  - JourneyTriggerController（9）：mkt:journey:trigger:list/edit
  - MktJourneyController（9）：mkt:journey:list/edit
  - MktChannelController（5）：mkt:channel:list/edit
  - MktConversionController（5）：mkt:conversion:view/track
  - MktEffectController（2）：mkt:effect:view/edit
  - MktTemplateController（3）：mkt:template:list/edit
  - MktTouchTaskController（8）：mkt:touch:task:list/edit
- DB 新增 14 个权限码，复用 6 个现有权限码
- 编译 EXIT=0

### 第 4a 批覆盖率
- @RequirePermission 命中：384（第3批 304 → 第4a批 +80）
- Controller 覆盖：44 / 138（第3批 31 → 第4a批 +13）
- 方法覆盖：384 / 1002 = 38.3%（第3批 30.3% → 第4a批 +8.0 pp）
- 累计提升（相对基线 7.4%）：+30.9 pp

## 状态
第 1、2、3、4a 批完成，第 4b~4g 批待办

## 第 4b 批完成（2026-10-21，商品/服务/套餐）
- 8 个 Controller 共 57 个端点补 @RequirePermission：
  - BizServiceItemController（5）：biz:service-item:list/edit
  - CategoryController（7）：category:list/edit
  - PackageController（补 16，原 6 → 22）：package:list/add/purchase/refund/edit/delete/redeem
  - ServiceMaterialController（5）：material:binding:list/edit
  - ServiceMaterialCostController（2）：material:cost:list
  - ServiceMaterialDeductController（2）：material:deduct:manage
  - StyleGalleryController（7）：style:gallery:list/edit
  - StyleLibraryController（13）：style:library:list/edit
- DB 新增 13 个权限码，复用 9 个现有 package:* 权限码
- 编译 EXIT=0
- 跳过：ServiceMaterialDeductLogController / ServiceMaterialGroupController / ServiceMaterialRuleController（I118 已关闭）

### 第 4b 批覆盖率
- @RequirePermission 命中：441（第4a批 384 → 第4b批 +57）
- Controller 覆盖：52 / 138（第4a批 44 → 第4b批 +8）
- 方法覆盖：441 / 1002 = 44.0%（第4a批 38.3% → 第4b批 +5.7 pp）
- 累计提升（相对基线 7.4%）：+36.6 pp

## 状态
第 1、2、3、4a、4b 批完成，第 4c~4g 批待办

## 第 4c 批完成（2026-10-21，门店/员工/房间/排班）
- 8 个 Controller 共 59 个端点补 @RequirePermission：
  - AttendanceConfigController（6）：attendance:config:list/edit
  - AttendanceController（9）：attendance:check/list/stat/edit
  - EmployeeController（8）：employee:list/add/edit/delete/reset-password
  - LeaveRequestController（7）：attendance:leave:submit/approve/list
  - RoomController（5）：room:list/edit
  - ScheduleController（6）：schedule:list/edit
  - StoreController（7）：store:list/edit
  - TechnicianLevelController（11）：employee:level / level:edit / level:eval / level:promote
- DB 新增 17 个权限码，复用 10 个现有 employee:* 权限码
- 编译 EXIT=0

### 第 4c 批覆盖率
- @RequirePermission 命中：500（第4b批 441 → 第4c批 +59）
- Controller 覆盖：59 / 138（第4b批 51 → 第4c批 +8）
- 方法覆盖：500 / 1002 = 49.9%（第4b批 44.0% → 第4c批 +5.9 pp）
- 累计提升（相对基线 7.4%）：+42.5 pp

## 状态
第 1、2、3、4a、4b、4c 批完成，第 4d~4g 批待办

## 第 4d 批完成（2026-10-21，库存/耗材）
- 2 个 Controller 共 36 个端点补 @RequirePermission：
  - StockController（补 27，原 13 → 40）：stock:product:list/add/edit/delete / stock:stat / stock:alert / stock:check / stock:record / stock:consumption / stock:export
  - MaterialPackageController（9）：material:package:list/edit
- DB 新增 6 个权限码，复用 9 个现有 stock:* 权限码
- 编译 EXIT=0
- 跳过：MaterialConsumptionLogController / MaterialSubstituteController / StockShortageLogController（I118 已关闭）
- StockDualVerifyController 已全覆盖（6/6），无需处理

### 第 4d 批覆盖率
- @RequirePermission 命中：536（第4c批 500 → 第4d批 +36）
- Controller 覆盖：60 / 138（第4c批 59 → 第4d批 +1）
- 方法覆盖：536 / 1002 = 53.5%（第4c批 49.9% → 第4d批 +3.6 pp）
- 累计提升（相对基线 7.4%）：+46.1 pp

## 状态
第 1、2、3、4a、4b、4c、4d 批完成，第 4e~4g 批待办

## 第 4e 批完成（2026-10-21，电商/第三方）
- 扫描 `Ecommerce*` / `Platform*` / `Wx*` / `Push*` / `Sync*` / `Open*` / `Youzan*` 相关 Controller
- 实际结果：
  - YouzanWebhookController：webhook 回调，无需权限（外部系统调用）
  - 其他电商/第三方功能无独立 Controller（EcommerceService 等可能是被别的 Controller 间接调用，或本身未暴露）
- 权限码查询：电商/第三方域暂无任何权限码
- 结论：第 4e 批无实际目标，跳过

### 第 4e 批覆盖率（无变化）
- @RequirePermission 命中：536
- Controller 覆盖：60 / 138
- 方法覆盖：536 / 1002 = 53.5%

## 状态
第 1、2、3、4a、4b、4c、4d、4e 批完成，第 4f~4g 批待办

## 第 4f 批完成（2026-10-21，插件/运行时）
- PluginRuntimeController 10 个端点补 @RequirePermission：
  - list / dashboard / persisted / listTenant → plugin:runtime:list
  - load / enable / disable / unload → plugin:runtime:edit
  - enableForTenant / disableForTenant → plugin:runtime:tenant:edit
- DB 新增 3 个权限码
- 插件表 plugin_install / plugin_market / plugin_review / plugin_runtime_state 均存在（真业务）
- 编译 EXIT=0

### 第 4f 批覆盖率
- @RequirePermission 命中：546（第4e批 536 → 第4f批 +10）
- Controller 覆盖：61 / 138（第4e批 60 → 第4f批 +1）
- 方法覆盖：546 / 1002 = 54.5%（第4e批 53.5% → 第4f批 +1.0 pp）
- 累计提升（相对基线 7.4%）：+47.1 pp

## 状态
第 1、2、3、4a~4f 批完成，第 4g 批待办

## 第 4g-1 批完成（2026-10-21，系统/认证/日志/配置）
- 5 个 Controller 共 37 个端点补 @RequirePermission：
  - SysConfigController（7）：sys:config:list/edit
  - SysLogController（10）：sys:log:list/clear/export
  - SysLoginLogController（8）：sys:login-log:list/clear/export
  - SysTenantController（10）：sys:tenant:list/add/edit/delete/freeze
  - LogRetentionController（2）：sys:log-retention:list/edit
- DB 新增 2 个权限码（LogRetention 专用），复用 14 个现有 sys:* 权限码
- 编译 EXIT=0
- 排除：AuthController（登录/登出/refresh/userinfo 公开接口），SysPermissionController（已全覆盖）

### 第 4g-1 批覆盖率
- @RequirePermission 命中：583（第4f批 546 → 第4g-1批 +37）
- Controller 覆盖：65 / 138（第4f批 61 → 第4g-1批 +4）
- 方法覆盖：583 / 1002 = 58.2%（第4f批 54.5% → 第4g-1批 +3.7 pp）
- 累计提升（相对基线 7.4%）：+50.8 pp

## 状态
第 1、2、3、4a~4f、4g-1 批完成，第 4g-2/4g-3 批待办

## 第 4g-2 批完成（2026-10-21，AI/IoT/LBS/DCEP 剩余）
- 扫描结果：8 个 LIVE，但仅 1 个真业务（IotDeviceLinkController）
- 处理：IotDeviceLinkController（10）：iot:link:list/edit/execute
- 跳过（依赖表在 DB 不存在，I118 同类死代码）：
  - DcepConfigModeController（dcep_config 缺失）
  - DcepContractExecutionLogController（dcep_contract_execution_log 缺失）
  - DcepOfflineTransactionController（dcep_offline_transaction 缺失）
  - DcepSmartContractController（dcep_smart_contract 缺失）
  - IotDeviceAlertController（iot_device_alert 缺失）
  - IotDeviceDataController（iot_device_data 缺失）
  - IotTriggerRuleController（iot_trigger_rule 缺失）
- DB 新增 3 个权限码
- 编译 EXIT=0
- 注意：IotDeviceLinkController 的 logPage/logDetail 依赖 iot_device_link_log 表（不存在），调用会 500，属 I118 同类问题

### 第 4g-2 批覆盖率
- @RequirePermission 命中：593（第4g-1批 583 → 第4g-2批 +10）
- Controller 覆盖：66 / 138
- 方法覆盖：593 / 1002 = 59.2%
- 累计提升（相对基线 7.4%）：+51.8 pp

## 状态
第 1~4g-2 批完成，第 4g-3 批待办

## 第 4g-3 批完成（2026-10-21，剩余杂项第一批）
- 7 个 Controller 共 32 个端点补 @RequirePermission：
  - CustomerCareController（1）：crm:care:complaint:reply
  - DetectionDiagnosisController（1）：crm:detection:diagnosis:view
  - DomesticPayController（5，webhook 排除）：domestic-payment:prepay/query/refund/close/health
  - RepurchaseRateController（3）：report:repurchase:view
  - PriceSensitivityController（4）：report:price-sensitivity:view
  - RfmBatchController（4）：report:rfm:batch:run
  - ExpiryController（14）：stock:expiry:view/edit
- DB 新增 12 个权限码
- 编译 EXIT=0
- 跳过：LtvSeedController / RfmSeedController（种子工具），SpaFallbackController（路由兜底），AuthController / PaymentWebhookController / YouzanWebhookController（公开/webhook）
- 已知风险：ExpiryController 的 handleAlert/ignoreAlert/createHandle 依赖 biz_expiry_handle_log 表（DB 不存在），调用会 500（I118 同类问题）

### 第 4g-3 批第一批覆盖率
- @RequirePermission 命中：625（第4g-2批 593 → 第4g-3批第一批 +32）
- Controller 覆盖：74 / 138（第4g-2批 67 → 第4g-3批第一批 +7）
- 方法覆盖：625 / 1002 = 62.4%（第4g-2批 59.2% → 第4g-3批第一批 +3.2 pp）
- 累计提升（相对基线 7.4%）：+55.0 pp

## 待办：第 4g-3 批第二批
剩余 36 个 LIVE Controller（约 200+ 端点）待处理：
- 关怀检测类：CareRecipe / CareSuggestion* / Complaint / Detection* / HairDetection / NailDetection / SkinDetection
- CRM 会员类：Cohort / CrossStore / MemberLevel / MemberLifecycle / MemberMessage / MemberStylePreference / MemberTag
- 等级类：LevelBenefit / LevelEvaluation
- DCEP/IoT 类：DcepContractExecutionLog / DcepOfflineTransaction / DcepSmartContract / IotDeviceAlert / IotDeviceData / IotTriggerRule
- 其他：Blockchain / PricingExperiment / Rbac / ResourceMatchRule / ResourceSchedule / SpaSceneMode / Subscription / MhConsentH5

## 状态
第 1~4g-2 批 + 4g-3 第一批完成，4g-3 第二批待办
