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
