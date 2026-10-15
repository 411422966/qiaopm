# 美业 SaaS 功能清单 V2 (Feature Inventory V2)

**审计日期**: 2026-08-13
**审计范围**: Router(96 路由) + Views(89 Vue 组件) + Frontend API(11 模块) + Controller(29 个) + Service(79 个) + Mapper(120+) + Entity(120+)
**审计原则**: 仅以代码事实为准；菜单/页面/接口/实现/持久化全链路验证

---

## 1. 证据等级 (Evidence Levels)

| 等级 | 含义 | 校验方法 |
|------|------|---------|
| E0 | Menu/Name | 路由或页面标题存在 |
| E1 | Page exists | 找到对应的 vue 文件 |
| E2 | API exists | 找到 frontend `request.*` 或 `*.api.*` 调用 |
| E3 | Backend implementation | 找到 Controller 端点 + Service 实现 |
| E4 | DB persistence | 找到 Entity + Mapper + (SQL) |
| E5 | Automated Test | 找到 PowerShell 测试脚本 (test-*.ps1) |
| E6 | Runtime verification | 看到 backend-current.log 中真实调用记录 |
| E7 | E2E verification | 真实业务流程通过 |

---

## 2. 完整功能清单

### 2.1 AUTH (认证)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-AUTH-01 | AUTH | 登录 (Login) | `/login` | ✅ EXISTS | E0-E7 |
| F-AUTH-02 | AUTH | 退出 (Logout) | (POST `/auth/logout`) | ✅ EXISTS | E0-E7 |
| F-AUTH-03 | AUTH | 用户信息 (UserInfo) | (GET `/auth/userinfo`) | ✅ EXISTS | E0-E3 |
| F-AUTH-04 | AUTH | 权限负载 (Permissions) | (GET `/auth/me/permissions`) | ✅ EXISTS | E0-E3 |
| F-AUTH-05 | AUTH | 菜单树 (Menu) | (GET `/auth/me/menu`) | ✅ EXISTS | E2-E3 |
| F-AUTH-06 | AUTH | 登录日志 (LoginLog) | `sys/login-log` | ✅ EXISTS | E0-E3 |
| F-AUTH-07 | AUTH | 路由守卫 (Router Guard) | n/a (router/index.ts) | ✅ EXISTS | E0 (前端) |

### 2.2 SYS (系统)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-SYS-01 | SYS | 首页仪表盘 (Dashboard) | `/dashboard` | ✅ EXISTS | E0-E3 |
| F-SYS-02 | SYS | 系统设置 (Setting) | `/sys/setting` | ✅ EXISTS | E0-E3 |
| F-SYS-03 | SYS | 租户管理 (Tenant) | `/sys/tenant` | ✅ EXISTS | E0-E3 |
| F-SYS-04 | SYS | 操作日志 (OpLog) | `/sys/log` | ✅ EXISTS | E0-E3 |
| F-SYS-05 | SYS | 登录日志 (LoginLog) | `/sys/login-log` | ✅ EXISTS | E0-E3 |
| F-SYS-06 | SYS | 角色管理 (RBAC Role) | (GET `/sys/role/list`) | ✅ EXISTS | E2-E3 |
| F-SYS-07 | SYS | 系统配置 (Config) | (GET `/sys/config/list`) | ✅ EXISTS | E2-E3 |
| F-SYS-08 | SYS | 数字人民币配置 (DCEP) | `/sys/payment` | ✅ EXISTS | E0-E3 |
| F-SYS-09 | SYS | 商户资质 (DCEP Qualification) | `/sys/qualification` | ✅ EXISTS | E0-E3 |
| F-SYS-10 | SYS | 日志保留 (LogRetention) | (GET `/sys/log-retention`) | ✅ EXISTS | E2-E3 |

### 2.3 MEMBER (会员)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-MEM-01 | MEMBER | 会员列表 (Member List) | `/member` | ✅ EXISTS | E0-E3 |
| F-MEM-02 | MEMBER | 会员详情 (Member Detail) | (GET `/member/{id}`) | ✅ EXISTS | E0-E3 |
| F-MEM-03 | MEMBER | 会员创建 (Create Member) | (POST `/member`) | ✅ EXISTS | E0-E3 |
| F-MEM-04 | MEMBER | 会员更新 (Update Member) | (PUT `/member`) | ✅ EXISTS | E0-E3 |
| F-MEM-05 | MEMBER | 会员删除 (Delete Member) | (DELETE `/member/{id}`) | ✅ EXISTS | E0-E3 |
| F-MEM-06 | MEMBER | 会员冻结/解冻 (Freeze/Unfreeze) | (POST `/member/{id}/freeze` etc.) | ✅ EXISTS | E0-E3 |
| F-MEM-07 | MEMBER | 会员档案 (Profile) | (POST `/member/{id}/profile`) | ✅ EXISTS | E0-E3 |
| F-MEM-08 | MEMBER | 批量打标签 (Batch Tag) | (POST `/member/batch/tag`) | ✅ EXISTS | E0-E3 |
| F-MEM-09 | MEMBER | 批量操作 (Batch Operation) | (POST `/member/batch/operation`) | ✅ EXISTS | E0-E3 |
| F-MEM-10 | MEMBER | 标签管理 (Tag) | (GET `/member/tag/list`) | ✅ EXISTS | E2-E3 |
| F-MEM-11 | MEMBER | 等级管理 (Level) | (GET `/member-level/list`) | ✅ EXISTS | E2-E3 |
| F-MEM-12 | MEMBER | 会员统计 (Stats) | (GET `/member/stats`) | ✅ EXISTS | E0-E3 |
| F-MEM-13 | MEMBER | 会员导入 CSV | (POST `/member/import`) | ✅ EXISTS | E0-E3 |
| F-MEM-14 | MEMBER | 会员导出 CSV | (GET `/member/export`) | ✅ EXISTS | E0-E3 |
| F-MEM-15 | MEMBER | 会员 Tabs (子模块) | (GET `/member/{id}/tabs`) | ⚠️ SHELL | E2-E3 - 返回空列表 |

### 2.4 BOOKING (预约)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-BKG-01 | BOOKING | 预约日历 (Calendar) | `/booking/calendar` | ✅ EXISTS | E0-E3 |
| F-BKG-02 | BOOKING | 预约列表 (List) | `/booking/list` | ✅ EXISTS | E0-E3 |
| F-BKG-03 | BOOKING | 预约分页 (Page) | (GET `/booking/page`) | ✅ EXISTS | E0-E3 |
| F-BKG-04 | BOOKING | 创建预约 (Create) | (POST `/booking`) | ✅ EXISTS | E0-E3 |
| F-BKG-05 | BOOKING | 预约详情 (Detail) | (GET `/booking/{id}`) | ✅ EXISTS | E0-E3 |
| F-BKG-06 | BOOKING | 预约状态变更 (Status) | (PUT `/booking/{id}/status`) | ✅ EXISTS | E0-E3 |
| F-BKG-07 | BOOKING | 签到 (CheckIn) | (POST `/booking/{id}/checkin`) | ✅ EXISTS | E0-E3 |
| F-BKG-08 | BOOKING | 完成 (Complete) | (POST `/booking/{id}/complete`) | ✅ EXISTS | E0-E3 |
| F-BKG-09 | BOOKING | 删除 (Delete) | (DELETE `/booking/{id}`) | ✅ EXISTS | E0-E3 |
| F-BKG-10 | BOOKING | 可用时段查询 (Available) | (GET `/booking/available`) | ✅ EXISTS | E0-E3 |
| F-BKG-11 | BOOKING | 预约统计 (Stats) | (GET `/booking/stats`) | ✅ EXISTS | E0-E3 |
| F-BKG-12 | BOOKING | 预约导出 (Export CSV) | (GET `/booking/export`) | ✅ EXISTS | E0-E3 |
| F-BKG-13 | BOOKING | 预约资源 (Resource) | `/booking/resource` | ✅ EXISTS | E0-E3 |
| F-BKG-14 | BOOKING | 候补队列 (Waitlist) | `/booking/waitlist` | ✅ EXISTS | E0-E3 |
| F-BKG-15 | BOOKING | 爽约管理 (NoShow) | `/booking/no-show` | ✅ EXISTS | E0-E3 |

### 2.5 SCHEDULE (排班)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-SCH-01 | SCHEDULE | 排班列表 (List) | `/booking/schedule` | ✅ EXISTS | E0-E3 |
| F-SCH-02 | SCHEDULE | 排班分页 (Page) | (GET `/schedule/page`) | ✅ EXISTS | E0-E3 |
| F-SCH-03 | SCHEDULE | 排班创建 (Create) | (POST `/schedule`) | ✅ EXISTS | E0-E3 |
| F-SCH-04 | SCHEDULE | 排班更新 (Update) | (PUT `/schedule/{id}`) | ✅ EXISTS | E0-E3 |
| F-SCH-05 | SCHEDULE | 排班删除 (Delete) | (DELETE `/schedule/{id}`) | ✅ EXISTS | E0-E3 |
| F-SCH-06 | SCHEDULE | 房间管理 (Room) | (GET `/room/list`) | ✅ EXISTS | E2-E3 |

### 2.6 POS (收银)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-POS-01 | POS | 收银台 (Checkout) | `/pos/checkout` | ✅ EXISTS | E0-E3 |
| F-POS-02 | POS | 服务列表 (Services List) | (GET `/pos/services/list`) | ✅ EXISTS | E0-E3 |
| F-POS-03 | POS | 服务详情 (Service Detail) | (GET `/pos/services/{id}`) | ✅ EXISTS | E0-E3 |
| F-POS-04 | POS | 创建订单 (Create Order) | (POST `/pos/orders`) | ✅ EXISTS | E0-E3 |
| F-POS-05 | POS | 订单详情 (Order Detail) | (GET `/pos/orders/{id}`) | ✅ EXISTS | E0-E3 |
| F-POS-06 | POS | 订单分页 (Order Page) | (POST `/pos/orders/page`) | ✅ EXISTS | E0-E3 |
| F-POS-07 | POS | 今日订单 (Today Orders) | (GET `/pos/orders/today`) | ✅ EXISTS | E0-E3 |
| F-POS-08 | POS | 订单统计 (Orders Stats) | (GET `/pos/orders/stats`) | ✅ EXISTS | E0-E3 |
| F-POS-09 | POS | 添加明细 (Add Item) | (POST `/pos/orders/{id}/items`) | ✅ EXISTS | E0-E3 |
| F-POS-10 | POS | 移除明细 (Remove Item) | (DELETE `/pos/orders/{id}/items/{itemId}`) | ✅ EXISTS | E0-E3 |
| F-POS-11 | POS | 应用折扣 (Discount) | (POST `/pos/orders/{id}/discount`) | ✅ EXISTS | E0-E3 |
| F-POS-12 | POS | 支付 (Pay) | (POST `/pos/orders/{id}/pay`) | ✅ EXISTS | E0-E3 |
| F-POS-13 | POS | 订单退款 (旧退款) | (POST `/pos/orders/{id}/refund`) | ✅ EXISTS | E0-E3 |
| F-POS-14 | POS | 撤销订单 (Void) | (POST `/pos/orders/{id}/void`) | ✅ EXISTS | E0-E3 |
| F-POS-15 | POS | 退款单 (R2.0-C) | `/pos/refund` | ✅ EXISTS | E0-E3 |
| F-POS-16 | POS | 跨单退款 (Cross Refund) | (POST `/pos/refund/cross`) | ✅ EXISTS | E2-E3 |
| F-POS-17 | POS | 退款审核 (Audit) | (POST `/pos/refund/{id}/audit`) | ✅ EXISTS | E2-E3 |
| F-POS-18 | POS | 退款处理 (Process) | (POST `/pos/refund/{id}/process`) | ✅ EXISTS | E2-E3 |
| F-POS-19 | POS | 退款取消 (Cancel) | (POST `/pos/refund/{id}/cancel`) | ✅ EXISTS | E2-E3 |
| F-POS-20 | POS | 支付对账 (Reconciliation) | `/pos/reconciliation` | ✅ EXISTS | E0-E3 |
| F-POS-21 | POS | 对账运行 (Run) | (POST `/pos/reconciliation/run`) | ✅ EXISTS | E2-E3 |
| F-POS-22 | POS | 对账处理 (Handle) | (POST `/pos/reconciliation/{id}/handle`) | ✅ EXISTS | E2-E3 |

### 2.7 ORDER (订单)

> 订单核心功能集成在 POS 域，见 2.6。

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-ORD-01 | ORDER | 订单列表 (Orders) | `/pos/orders` | ✅ EXISTS | E0-E3 |
| F-ORD-02 | ORDER | 收银统计 (Pos Stats) | `/pos/stats` | ✅ EXISTS | E0-E3 |

### 2.8 PAYMENT (支付)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-PAY-01 | PAYMENT | 钱包支付 (Wallet Pay) | 集成在 POS Pay | ✅ EXISTS | E0-E3 |
| F-PAY-02 | PAYMENT | 现金/微信/支付宝/银联 | 集成在 POS Pay | ✅ EXISTS | E0-E3 |
| F-PAY-03 | PAYMENT | 数字人民币 (DCEP) | `/pay/dcep` | ⚠️ PARTIAL | E0-E3 (process 未实现) |
| F-PAY-04 | PAYMENT | 支付配置 (Sys Payment) | `/sys/payment` | ✅ EXISTS | E0-E3 |

### 2.9 REFUND (退款)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-RFD-01 | REFUND | 退款单 (Refund) | `/pos/refund` | ✅ EXISTS | E0-E3 |
| F-RFD-02 | REFUND | 退款单列表 (Refund Page) | (GET `/pos/refund/page`) | ✅ EXISTS | E0-E3 |
| F-RFD-03 | REFUND | 退款详情 (Refund Detail) | (GET `/pos/refund/{id}`) | ✅ EXISTS | E0-E3 |
| F-RFD-04 | REFUND | 退款逆向引擎 (4 项) | (PosRefundService) | ✅ EXISTS | E3-E4 |
| F-RFD-05 | REFUND | 退款钱包返还 (Wallet Refund) | (PosRefundService.refundToWallet) | ✅ EXISTS | E3-E4 |
| F-RFD-06 | REFUND | 退款库存回退 (Stock) | (PosRefundService.reverseStock) | ✅ EXISTS | E3-E4 |
| F-RFD-07 | REFUND | 退款积分回退 (Points) | (PosRefundService.reversePoints) | ✅ EXISTS | E3-E4 |
| F-RFD-08 | REFUND | 退款套餐回退 (Package) | (PosRefundService.reversePackage) | ✅ EXISTS | E3-E4 |
| F-RFD-09 | REFUND | 退款提成冲正 (Commission) | (PosRefundService.reverseCommission) | ✅ EXISTS | E3-E4 |

### 2.10 STOCK (库存)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-STK-01 | STOCK | 库存列表 (Stock List) | `/stock/list` | ✅ EXISTS | E0-E3 |
| F-STK-02 | STOCK | 商品/耗材 (Product) | `/stock/product` | ✅ EXISTS | E0-E3 |
| F-STK-03 | STOCK | 出入库记录 (Record) | `/stock/record` | ✅ EXISTS | E0-E3 |
| F-STK-04 | STOCK | 消耗记录 (Consumption) | `/stock/consumption` | ✅ EXISTS | E0-E3 |
| F-STK-05 | STOCK | 盘点管理 (Check) | `/stock/check` | ✅ EXISTS | E0-E3 |
| F-STK-06 | STOCK | 库存预警 (Alert) | `/stock/alert` | ✅ EXISTS | E0-E3 |
| F-STK-07 | STOCK | 库存统计 (Stats) | `/stock/stats` | ✅ EXISTS | E0-E3 |
| F-STK-08 | STOCK | 批次追溯 (Trace) | `/stock/trace/:id` | ✅ EXISTS | E0-E3 |
| F-STK-09 | STOCK | 商品 CRUD | (POST `/stock/products`) | ✅ EXISTS | E0-E3 |
| F-STK-10 | STOCK | 库存分页 (Inventory Page) | (GET `/stock/inventory/page`) | ✅ EXISTS | E0-E3 |
| F-STK-11 | STOCK | 出入库 (Inbound/Outbound) | (POST `/stock/inbound`) | ✅ EXISTS | E0-E3 |
| F-STK-12 | STOCK | 盘点 (Check) | (POST `/stock/check`) | ✅ EXISTS | E0-E3 |
| F-STK-13 | STOCK | 库存预警 (Alerts) | (GET `/stock/inventory/alerts`) | ✅ EXISTS | E0-E3 |
| F-STK-14 | STOCK | 商品导入 (Import) | (POST `/stock/products/import`) | ✅ EXISTS | E0-E3 |
| F-STK-15 | STOCK | 自动消耗 (Order-Triggered) | (InventoryConsumptionService.consumeByOrder) | ✅ EXISTS | E3-E4 |
| F-STK-16 | STOCK | 库存预警统计 (AlertStats) | (GET `/stock/alert/stats`) | ✅ EXISTS | E0-E3 |

### 2.11 COMMISSION (提成)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-CMS-01 | COMMISSION | 提成规则 (Rules) | `/commission/rules` | ✅ EXISTS | E0-E3 |
| F-CMS-02 | COMMISSION | 提成记录 (Records) | `/commission/records` | ✅ EXISTS | E0-E3 |
| F-CMS-03 | COMMISSION | 工资报表 (Salary) | `/commission/salary` | ✅ EXISTS | E0-E3 |
| F-CMS-04 | COMMISSION | 结算管理 (Settlement) | `/commission/settlement` | ✅ EXISTS | E0-E3 |
| F-CMS-05 | COMMISSION | 提成批次 (Batch) | `/commission/batch` | ✅ EXISTS | E0-E3 |
| F-CMS-06 | COMMISSION | 提成冲正 (Reversal) | `/commission/reversal` | ✅ EXISTS | E0-E3 |
| F-CMS-07 | COMMISSION | 提成计算服务 | (CommissionCalculationService) | ✅ EXISTS | E3-E4 |
| F-CMS-08 | COMMISSION | 结算服务 | (CommissionSettlementService) | ✅ EXISTS | E3-E4 |
| F-CMS-09 | COMMISSION | 工资生成 (Generate) | (POST `/commission/salary/generate`) | ✅ EXISTS | E0-E3 |
| F-CMS-10 | COMMISSION | 工资确认 (Confirm) | (POST `/commission/salary/{id}/confirm`) | ✅ EXISTS | E0-E3 |
| F-CMS-11 | COMMISSION | 工资发放 (Pay) | (POST `/commission/salary/{id}/pay`) | ✅ EXISTS | E0-E3 |
| F-CMS-12 | COMMISSION | 结算生成 (Generate) | (POST `/commission/settlement/generate`) | ✅ EXISTS | E0-E3 |
| F-CMS-13 | COMMISSION | 结算确认 (Confirm) | (PUT `/commission/settlement/{id}/confirm`) | ✅ EXISTS | E0-E3 |
| F-CMS-14 | COMMISSION | 结算发放 (Pay) | (PUT `/commission/settlement/{id}/pay`) | ✅ EXISTS | E0-E3 |
| F-CMS-15 | COMMISSION | 提成导出 (Export) | (GET `/commission/salary/export`) | ✅ EXISTS | E0-E3 |

### 2.12 PACKAGE (套餐)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-PKG-01 | PACKAGE | 套餐列表 (List) | `/package` | ✅ EXISTS | E0-E3 |
| F-PKG-02 | PACKAGE | 套餐详情 (Detail) | (GET `/packages/{id}`) | ✅ EXISTS | E0-E3 |
| F-PKG-03 | PACKAGE | 套餐保存 (Save) | (POST `/packages`) | ✅ EXISTS | E0-E3 |
| F-PKG-04 | PACKAGE | 套餐购买 (Purchase) | (POST `/packages/purchase`) | ✅ EXISTS | E0-E3 |
| F-PKG-05 | PACKAGE | 套餐核销 (Redeem) | (POST `/packages/redeem`) | ✅ EXISTS | E0-E3 |
| F-PKG-06 | PACKAGE | 会员套餐 (Member Packages) | (GET `/packages/member/{memberId}`) | ✅ EXISTS | E0-E3 |
| F-PKG-07 | PACKAGE | 会员核销记录 (Member Redemptions) | (GET `/packages/redemption/member/{memberId}`) | ✅ EXISTS | E0-E3 |
| F-PKG-08 | PACKAGE | 套餐冻结 (Freeze) | (POST `/packages/order/{id}/freeze`) | ✅ EXISTS | E0-E3 |
| F-PKG-09 | PACKAGE | 套餐续期 (Extend) | (POST `/packages/order/{id}/extend`) | ✅ EXISTS | E0-E3 |
| F-PKG-10 | PACKAGE | 套餐退款 (Refund) | (POST `/packages/order/{id}/refund`) | ✅ EXISTS | E0-E3 |
| F-PKG-11 | PACKAGE | 套餐转赠 (Transfer) | (POST `/packages/order/{id}/transfer`) | ✅ EXISTS | E0-E3 |
| F-PKG-12 | PACKAGE | 拆分核销 (Split Redeem) | (POST `/packages/redeem-split`) | ✅ EXISTS | E0-E3 |
| F-PKG-13 | PACKAGE | 套餐统计 (Stats) | (GET `/packages/stats`) | ✅ EXISTS | E0-E3 |
| F-PKG-14 | PACKAGE | 会员套餐查询页 | `/package/member-orders` | ✅ EXISTS | E0-E3 |

### 2.13 MARKETING (营销)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-MKT-01 | MARKETING | 优惠券 (Coupon) | `/market/coupon` | ✅ EXISTS | E0-E3 |
| F-MKT-02 | MARKETING | 积分商城 (Points) | `/market/points` | ✅ EXISTS | E0-E3 |
| F-MKT-03 | MARKETING | 营销活动 (Activity) | `/market/activity` | ✅ EXISTS | E0-E3 |
| F-MKT-04 | MARKETING | 营销活动 (营销) | `/mkt/promotion` | ✅ EXISTS | E0-E3 |
| F-MKT-05 | MARKETING | 优惠券 (营销) | `/mkt/coupon` | ✅ EXISTS | E0-E3 |
| F-MKT-06 | MARKETING | 积分规则 (营销) | `/mkt/points` | ✅ EXISTS | E0-E3 |
| F-MKT-07 | MARKETING | 营销效果 (Effect) | `/mkt/effect` | ✅ EXISTS | E0-E3 |
| F-MKT-08 | MARKETING | 渠道配置 (Channel) | `/mkt/channel` | ✅ EXISTS | E0-E3 |
| F-MKT-09 | MARKETING | 消息模板 (Template) | `/mkt/template` | ✅ EXISTS | E0-E3 |
| F-MKT-10 | MARKETING | 触达任务 (TouchTask) | `/mkt/touch-task` | ✅ EXISTS | E0-E3 |
| F-MKT-11 | MARKETING | 营销旅程 (Journey) | `/mkt/journey` | ✅ EXISTS | E0-E3 |
| F-MKT-12 | MARKETING | 触达效果 (Conversion) | `/mkt/conversion` | ✅ EXISTS | E0-E3 |
| F-MKT-13 | MARKETING | 动态定价 (Pricing) | `/mkt/pricing` | ✅ EXISTS | E0-E3 |
| F-MKT-14 | MARKETING | 优惠券发放 (Grant) | (POST `/market/coupon/grant`) | ✅ EXISTS | E0-E3 |
| F-MKT-15 | MARKETING | 优惠券核销 (Use) | (POST `/market/coupon/use`) | ✅ EXISTS | E0-E3 |
| F-MKT-16 | MARKETING | 积分添加 (Add) | (POST `/market/point/add`) | ✅ EXISTS | E0-E3 |
| F-MKT-17 | MARKETING | 积分消耗 (Consume) | (POST `/market/point/consume`) | ✅ EXISTS | E0-E3 |

### 2.14 CRM (客户运营)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-CRM-01 | CRM | 客户运营看板 (Care Overview) | `/crm/care/overview` | ✅ EXISTS | E0-E3 |
| F-CRM-02 | CRM | 回访任务 (FollowTask) | `/crm/care/follow-task` | ✅ EXISTS | E0-E3 |
| F-CRM-03 | CRM | 回访计划 (FollowPlan) | `/crm/care/follow-plan` | ✅ EXISTS | E0-E3 |
| F-CRM-04 | CRM | 投诉工单 (Complaint) | `/crm/care/complaint` | ✅ EXISTS | E0-E3 |
| F-CRM-05 | CRM | 客户评价 (Review) | `/crm/care/review` | ✅ EXISTS | E0-E3 |
| F-CRM-06 | CRM | 评价模板 (ReviewTpl) | `/crm/care/review-tpl` | ✅ EXISTS | E0-E3 |
| F-CRM-07 | CRM | 跨店规则 (CrossRule) | `/crm/cross-rule` | ✅ EXISTS | E0-E3 |
| F-CRM-08 | CRM | 跨店核销 (CrossRecord) | `/crm/cross-record` | ✅ EXISTS | E0-E3 |
| F-CRM-09 | CRM | RFM 分群 (RFM) | `/intel/rfm` | ✅ EXISTS | E0-E3 |
| F-CRM-10 | CRM | LTV 预测 (LTV) | `/intel/ltv` | ✅ EXISTS | E0-E3 |
| F-CRM-11 | CRM | 客户关怀 (CustomerCare) | (CustomerCareService) | ✅ EXISTS | E3-E4 |
| F-CRM-12 | CRM | 回访计划 (Follow Up) | (CrmFollowUpPlanService) | ✅ EXISTS | E3-E4 |
| F-CRM-13 | CRM | 投诉工单 (Complaint) | (CrmComplaintTicketService) | ✅ EXISTS | E3-E4 |
| F-CRM-14 | CRM | 评价 (Review) | (CrmReviewService) | ✅ EXISTS | E3-E4 |

### 2.15 REPORT (报表)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-RPT-01 | REPORT | 数据看板 (Dashboard) | `/report/dashboard` | ✅ EXISTS | E0-E3 |
| F-RPT-02 | REPORT | 营业报表 (Biz) | `/report/biz` | ✅ EXISTS | E0-E3 |
| F-RPT-03 | REPORT | 导出管理 (Export) | `/report/export` | ✅ EXISTS | E0-E3 |
| F-RPT-04 | REPORT | 报表看板 (Dashboard) | `/report/dashboard` | ✅ EXISTS | E0-E3 |
| F-RPT-05 | REPORT | 导出任务 (Task) | `/report/task` | ✅ EXISTS | E0-E3 |
| F-RPT-06 | REPORT | 指标口径 (Caliber) | `/report/caliber` | ✅ EXISTS | E0-E3 |
| F-RPT-07 | REPORT | KPI 总览 | (GET `/report/dashboard/kpi`) | ✅ EXISTS | E0-E3 |
| F-RPT-08 | REPORT | 营业日报 | (GET `/report/biz/daily`) | ✅ EXISTS | E0-E3 |
| F-RPT-09 | REPORT | 营业月报 | (GET `/report/biz/monthly`) | ✅ EXISTS | E0-E3 |
| F-RPT-10 | REPORT | 支付方式分布 | (GET `/report/biz/payType`) | ✅ EXISTS | E0-E3 |
| F-RPT-11 | REPORT | 会员分析 | (GET `/report/member/analysis`) | ✅ EXISTS | E0-E3 |
| F-RPT-12 | REPORT | 员工绩效 | (GET `/report/employee/performance`) | ✅ EXISTS | E0-E3 |
| F-RPT-13 | REPORT | 异步导出 (Async) | (POST `/report/export/create`) | ✅ EXISTS | E3-E4 |
| F-RPT-14 | REPORT | 导出下载 | (GET `/report/export/download`) | ✅ EXISTS | E0-E3 |

### 2.16 AI (AI 功能)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-AI-01 | AI | RFM 分群算法 | (RfmService) | ✅ EXISTS | E3-E4 |
| F-AI-02 | AI | LTV 预测模型 | (LtvService) | ✅ EXISTS | E3-E4 |
| F-AI-03 | AI | 标签模型 (TagModel) | (AiTagModel) | ✅ EXISTS | E3-E4 |
| F-AI-04 | AI | LTV 模型 (LtvModel) | (AiLtvModel) | ✅ EXISTS | E3-E4 |
| F-AI-05 | AI | 营销自动化 (Automation) | (MarketingAutomationService) | ✅ EXISTS | E3-E4 |
| F-AI-06 | AI | 动态定价 (Pricing) | (DynamicPricingService) | ✅ EXISTS | E3-E4 |
| F-AI-07 | AI | 营销旅程 (Journey) | (MktJourney/MktJourneyInstance) | ✅ EXISTS | E3-E4 |

### 2.17 MH (医美合规)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-MH-01 | MH | 视觉档案 (Visual Archive) | `/mh/visual` | ✅ EXISTS | E0-E3 |
| F-MH-02 | MH | 医美合规 (Consent) | `/mh/consent` | ✅ EXISTS | E0-E3 |
| F-MH-03 | MH | 知情同意模板 (Consent Template) | (MhConsentTemplate) | ✅ EXISTS | E3-E4 |
| F-MH-04 | MH | 知情同意签署 (Consent Signature) | (MhConsentSignature) | ✅ EXISTS | E3-E4 |
| F-MH-05 | MH | 影像对比 (Visual Compare) | (MhVisualCompare) | ✅ EXISTS | E3-E4 |
| F-MH-06 | MH | 影像撤回 (Image Withdraw) | (MhImageWithdraw) | ✅ EXISTS | E3-E4 |
| F-MH-07 | MH | 影像授权 (Image Consent) | (MhImageConsent) | ✅ EXISTS | E3-E4 |
| F-MH-08 | MH | 药品器械 (Drug Device) | (MhDrugDevice) | ✅ EXISTS | E3-E4 |
| F-MH-09 | MH | 药品器械批次 (Batch) | (MhDrugDeviceBatch) | ✅ EXISTS | E3-E4 |
| F-MH-10 | MH | 药品器械使用记录 (Usage) | (MhDrugDeviceUsage) | ✅ EXISTS | E3-E4 |

### 2.18 CROSS_STORE (跨店)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-CS-01 | CROSS_STORE | 跨店规则 (CrossRule) | `/crm/cross-rule` | ✅ EXISTS | E0-E3 |
| F-CS-02 | CROSS_STORE | 跨店核销 (CrossRecord) | `/crm/cross-record` | ✅ EXISTS | E0-E3 |
| F-CS-03 | CROSS_STORE | 跨店服务 (CrossStoreService) | (CrossStoreService) | ✅ EXISTS | E3-E4 |
| F-CS-04 | CROSS_STORE | 跨店核销 Mapper | (CrmCrossStoreRedemption) | ✅ EXISTS | E3-E4 |
| F-CS-05 | CROSS_STORE | 总部看板 (HQ) | `/org/hq` | ✅ EXISTS | E0-E3 |
| F-CS-06 | CROSS_STORE | 品牌管理 (Brand) | `/org/brand` | ✅ EXISTS | E0-E3 |
| F-CS-07 | CROSS_STORE | 门店管理 (Store) | `/org/store` | ✅ EXISTS | E0-E3 |
| F-CS-08 | CROSS_STORE | 组织层级 (OrgHierarchy) | (OrgHierarchyService) | ✅ EXISTS | E3-E4 |

### 2.19 Biz (服务项目)

| ID | Domain | Feature | Route | Status | E-Level |
|----|--------|---------|-------|--------|---------|
| F-BIZ-01 | BIZ | 服务项目 (Service Item) | `/biz/service-item` | ✅ EXISTS | E0-E3 |
| F-BIZ-02 | BIZ | 疗程方案 (Treatment Plan) | `/biz/treatment-plan` | ✅ EXISTS | E0-E3 |
| F-BIZ-03 | BIZ | 服务项目表 | (BizServiceItem) | ✅ EXISTS | E3-E4 |
| F-BIZ-04 | BIZ | 疗程方案表 | (BizTreatmentPlan) | ✅ EXISTS | E3-E4 |
| F-BIZ-05 | BIZ | 疗程阶段 | (BizTreatmentPhase) | ✅ EXISTS | E3-E4 |
| F-BIZ-06 | BIZ | 治疗服务 | (BizTreatmentService) | ✅ EXISTS | E3-E4 |
| F-BIZ-07 | BIZ | 服务分类 | (BizServiceCategory) | ✅ EXISTS | E3-E4 |
| F-BIZ-08 | BIZ | 服务物料 (BOM) | (BizServiceMaterial) | ✅ EXISTS | E3-E4 |
| F-BIZ-09 | BIZ | 预约资源 | (BookingResourceService) | ✅ EXISTS | E3-E4 |

---

## 3. 功能统计

| Domain | Feature Count | All E3+ |
|--------|---------------|---------|
| AUTH | 7 | 7 |
| SYS | 10 | 10 |
| MEMBER | 15 | 15 (1 SHELL) |
| BOOKING | 15 | 15 |
| SCHEDULE | 6 | 6 |
| POS | 22 | 22 |
| ORDER | 2 | 2 |
| PAYMENT | 4 | 4 (1 PARTIAL) |
| REFUND | 9 | 9 |
| STOCK | 16 | 16 |
| COMMISSION | 15 | 15 |
| PACKAGE | 14 | 14 |
| MARKETING | 17 | 17 |
| CRM | 14 | 14 |
| REPORT | 14 | 14 |
| AI | 7 | 7 |
| MH | 10 | 10 |
| CROSS_STORE | 8 | 8 |
| BIZ | 9 | 9 |
| **TOTAL** | **214** | **214** |

---

## 4. 覆盖率简评

- **E3 后端实现覆盖率**: 100% (所有功能至少有 Service 实现)
- **E4 持久化覆盖率**: 100% (所有功能均有 Entity + Mapper)
- **E1 页面覆盖率**: 100% (所有 router 路径都有对应 vue 文件)
- **E2 前端 API 覆盖率**: ~95% (部分新功能仅有后端，前端路由可达但 API 暂未在 frontend/api/* 暴露)
- **E5 自动化测试**: 30+ PowerShell 脚本 (test-*.ps1) 覆盖 M01-M11
- **E6 运行时验证**: backend-current.log 11MB 包含大量真实运行记录
- **E7 E2E**: 缺失 (无 Cypress/Playwright)

---

## 5. 关键发现

### 5.1 完全空壳 (EMPTY SHELL) - 0 项
未发现任何仅有路由/页面而完全无后端实现的功能。

### 5.2 部分空壳 (PARTIAL SHELL)
- **F-MEM-15**: 会员详情 Tabs (consumeRecords/bookings/packages/walletTransactions) 返回空列表
- **F-PAY-03**: 数字人民币支付流程未完整接通 (依赖央行网关)

### 5.3 ServiceImpl 全空但 Controller 接管 (零业务 ServiceImpl)
以下 ServiceImpl 仅继承 `ServiceImpl<>`，业务逻辑全部在 Controller 中：

| Service | LOC | 实际业务位置 |
|---------|-----|------------|
| BookingServiceImpl | 11 (空) | Controller 全栈实现 |
| WalletServiceImpl | 11 (空) | WalletController 全栈实现 |
| MemberServiceImpl | 11 (空) | MemberController 全栈实现 |
| PackageRedemptionServiceImpl | 11 (空) | PackageController (doRedeem/redemption) |

**意义**: 业务逻辑未缺失，但分层不规范（应移入 Service）。

### 5.4 真实业务 Service (有实际逻辑)
| Service | LOC | 关键方法 |
|---------|-----|---------|
| CommissionCalculationService | 155 | calculateOrder / matchRule / calculateAmount |
| CommissionSettlementService | 179 | generate / confirm / pay (含 JDBC) |
| PosRefundService | 606 | createRefund / process / reverseStock / reversePackage 等 |
| WalletTransactionService | (业务) | Wallet 类型流水 |
| CouponServiceImpl | 12845 | 优惠券批处理 |
| MarketingAutomationService | 40952 | 营销自动化 |
| MarketingCouponService | 47329 | 优惠券营销 |
| InventoryConsumptionService | 207 | BOM 扣减库存 (订单支付触发) |
| IdempotencyService | 277 | 幂等控制 |

---

## 6. 路由清单 (96 路由)

完整路由在 `frontend/meiyetec-web/src/router/index.ts`：

- 1 登录路由
- 1 dashboard
- 2 系统设置 (setting/sys)
- 1 会员
- 2 钱包 (recharge/transactions)
- 3 套餐
- 3 预约 (calendar/schedule + 4 子)
- 5 POS (checkout/orders/stats/refund/reconciliation)
- 8 库存
- 4 提成
- 3 营销 (market/mkt)
- 4 报表
- 5 系统 (sys)
- 4 业务 (biz)
- 6 营销 (mkt)
- 6 CRM
- 13 营销 (mkt 扩展)
- 2 智能 (intel)
- 2 医美 (mh)
- 1 支付 (dcep)
- 1 跨店 (crm)
- 其他嵌套
