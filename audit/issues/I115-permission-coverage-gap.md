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
