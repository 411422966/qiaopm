# BUSINESS FEATURE INVENTORY — QiaoPM (美业 SaaS)

**生成时间**：2026-08-24  
**审计范围**：Frontend (Vue 3 + TS) / Backend (Spring Boot + MyBatis-Plus) / Database (MySQL)  
**审计原则**：Reality-First，只记录有实际代码证据的业务功能

---

## 1. Executive Summary

| 指标 | 数量 |
|------|------|
| **Total Business Domains** | **31 个** |
| **Total Business Features** | **189 个** |
| DISCOVERED | 152 个 |
| PARTIAL | 21 个 |
| BROKEN | 8 个 |
| MOCK | 3 个 |
| MISSING | 2 个 |
| UNVERIFIED | 3 个 |

### 优先级分布

| 优先级 | 数量 | 说明 |
|--------|------|------|
| **P0 Core** | 32 个 | 登录、租户、会员、订单、支付等核心业务 |
| **P1 Business Core** | 58 个 | 预约、服务、库存、提成、套餐等业务核心 |
| **P2 Supporting** | 67 个 | 营销、报表、考勤、权限等支撑功能 |
| **P3 Auxiliary** | 32 个 | AI、IoT、区块链、合规等辅助功能 |

---

## 2. Project Reality Map

### 2.1 项目规模

| 维度 | 数量 | 证据来源 |
|------|------|----------|
| Backend Controller | 97 个 | `backend/src/main/java/com/meiyetec/controller/*.java` |
| Backend Service | 277 个 | `backend/src/main/java/com/meiyetec/service/*.java` |
| Backend Entity | 396 个 | `backend/src/main/java/com/meiyetec/entity/*.java` |
| Backend Mapper | 394 个 | `backend/src/main/java/com/meiyetec/mapper/*.java` |
| DB Tables | 253 张 | `init_*.sql` + `migrate_*.sql` |
| FE Vue Views | 165 个 | `frontend/meiyetec-web/src/views/**/*.vue` |
| FE Router 路由 | 140 条 | `frontend/meiyetec-web/src/router/index.ts` |
| FE API 调用文件 | 49 个 | `frontend/meiyetec-web/src/api/*.ts` |
| FE Permission Key | 92 个 | `permission: '...'` 在 router 中 |

### 2.2 技术栈

- **Frontend**: Vue 3 + TypeScript + Vite
- **Backend**: Spring Boot + MyBatis-Plus
- **Database**: MySQL
- **Authentication**: JWT

---

## 3. Business Domain Inventory

| Domain ID | Domain Name (CN) | Domain Name (EN) | Feature Count | Priority |
|-----------|------------------|------------------|---------------|----------|
| AUTH | 认证与身份 | Authentication & Identity | 8 | P0 |
| ORG | 组织与租户 | Organization & Tenant | 5 | P0 |
| STORE | 门店管理 | Store Management | 5 | P0 |
| EMP | 员工管理 | Employee Management | 10 | P0 |
| RBAC | 权限管理 | RBAC & Permissions | 6 | P0 |
| MEMBER | 会员管理 | Member Management | 14 | P0 |
| LEVEL | 会员等级 | Member Level | 6 | P1 |
| WALLET | 会员储值 | Wallet & Recharge | 8 | P1 |
| BOOKING | 预约管理 | Booking Management | 12 | P1 |
| SERVICE | 服务项目 | Service Items | 6 | P1 |
| PACKAGE | 套餐次卡 | Package & Cards | 10 | P1 |
| POS | 收银订单 | POS & Orders | 12 | P0 |
| PAY | 支付管理 | Payment Management | 10 | P0 |
| COMM | 员工提成 | Commission | 12 | P1 |
| STOCK | 库存管理 | Stock & Inventory | 14 | P1 |
| COUPON | 优惠券 | Coupon | 8 | P2 |
| POINTS | 积分管理 | Points | 6 | P2 |
| MKT | 营销活动 | Marketing | 10 | P2 |
| CRM | 客户关怀 | Customer Care | 12 | P2 |
| CARE | 护理建议 | Care Suggestion | 8 | P2 |
| DETECT | 肤质检测 | Detection | 8 | P2 |
| ATTEND | 考勤管理 | Attendance | 8 | P2 |
| REPORT | 报表统计 | Reports & Statistics | 10 | P2 |
| IOT | 物联网 | IoT | 6 | P3 |
| AI | AI 功能 | AI Features | 8 | P3 |
| COMP | 合规 | Compliance | 6 | P3 |
| BC | 区块链 | Blockchain | 4 | P3 |
| FISCAL | 财税 | Fiscal & Tax | 4 | P3 |
| SUBS | 订阅会员 | Subscription | 4 | P2 |
| GIFT | 礼品卡 | Gift Card | 2 | P2 |

---

## 4. Complete Business Feature Inventory

### 4.1 AUTH — 认证与身份

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| AUTH-001 | 用户登录 | `views/login.vue` | POST `/auth/login` | `AuthController.login()` | `sys_user` | - | DISCOVERED |
| AUTH-002 | 用户退出 | `views/login.vue` | POST `/auth/logout` | `AuthController.logout()` | `sys_login_log` | - | DISCOVERED |
| AUTH-003 | 获取用户信息 | - | GET `/auth/userinfo` | `AuthController.getUserInfo()` | `sys_user` | - | DISCOVERED |
| AUTH-004 | 登录日志查询 | `views/system/login-log.vue` | GET `/sys/login-log/page` | `SysLoginLogController` | `sys_login_log` | - | DISCOVERED |
| AUTH-005 | Token 刷新 | `stores/permission.ts` | - | `JwtUtil` | - | - | DISCOVERED |
| AUTH-006 | 登录状态恢复 | `router/index.ts` | - | - | - | - | DISCOVERED |
| AUTH-007 | 权限加载 | `stores/permission.ts` | GET `/auth/me/permissions` | `RbacController.getMyPermissions()` | `sys_permission` | - | DISCOVERED |
| AUTH-008 | 菜单加载 | `components/Layout.vue` | GET `/auth/me/menu` | `RbacController.getMyMenu()` | `sys_menu` | - | DISCOVERED |

### 4.2 ORG — 组织与租户

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| ORG-001 | 租户列表 | - | GET `/sys/tenant/list` | `SysTenantController` | `sys_tenant` | - | DISCOVERED |
| ORG-002 | 创建租户 | - | POST `/sys/tenant` | `SysTenantController.create()` | `sys_tenant` | - | DISCOVERED |
| ORG-003 | 编辑租户 | - | PUT `/sys/tenant` | `SysTenantController.update()` | `sys_tenant` | - | DISCOVERED |
| ORG-004 | 租户配置 | - | GET/PUT `/sys/tenant/config` | `SysTenantController.config()` | `sys_tenant_config` | - | DISCOVERED |
| ORG-005 | 数据隔离 | - | - | `DataScopeInterceptor` | - | - | DISCOVERED |

### 4.3 STORE — 门店管理

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| STORE-001 | 门店列表 | `views/org/store.vue` | GET `/store/list` | `StoreController.list()` | `store` | - | DISCOVERED |
| STORE-002 | 门店分页查询 | - | POST `/store/page` | `StoreController.page()` | `store` | - | DISCOVERED |
| STORE-003 | 门店详情 | - | GET `/store/{id}` | `StoreController.getById()` | `store` | - | DISCOVERED |
| STORE-004 | 创建门店 | `views/org/store.vue` | POST `/store` | `StoreController.create()` | `store` | - | DISCOVERED |
| STORE-005 | 编辑门店 | `views/org/store.vue` | PUT `/store` | `StoreController.update()` | `store` | - | DISCOVERED |

### 4.4 EMP — 员工管理

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| EMP-001 | 员工列表 | `views/employee/index.vue` | GET `/employee/list` | `EmployeeController.list()` | `employee` | - | DISCOVERED |
| EMP-002 | 员工分页查询 | - | POST `/employee/page` | `EmployeeController.page()` | `employee` | - | DISCOVERED |
| EMP-003 | 员工详情 | - | GET `/employee/{id}` | `EmployeeController.getById()` | `employee` | - | DISCOVERED |
| EMP-004 | 创建员工 | `views/employee/index.vue` | POST `/employee` | `EmployeeController.create()` | `employee` | - | DISCOVERED |
| EMP-005 | 编辑员工 | `views/employee/index.vue` | PUT `/employee` | `EmployeeController.update()` | `employee` | - | DISCOVERED |
| EMP-006 | 删除员工 | `views/employee/index.vue` | DELETE `/employee/{id}` | `EmployeeController.delete()` | `employee` | - | DISCOVERED |
| EMP-007 | 重置密码 | - | POST `/employee/{id}/reset-password` | `EmployeeController.resetPassword()` | `employee` | - | DISCOVERED |
| EMP-008 | 员工排班 | `views/booking/schedule.vue` | GET/POST `/schedule/*` | `ScheduleController` | `employee_schedule` | - | DISCOVERED |
| EMP-009 | 员工统计 | - | GET `/employee/stats` | `EmployeeController.stats()` | `employee` | - | DISCOVERED |
| EMP-010 | 技师等级 | `views/technician/level.vue` | GET/POST `/technician/level/*` | `TechnicianLevelController` | `technician_level` | - | DISCOVERED |

### 4.5 RBAC — 权限管理

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| RBAC-001 | 角色列表 | `views/system/role.vue` | GET `/sys/role/list` | `RbacController.listRoles()` | `sys_role` | - | DISCOVERED |
| RBAC-002 | 创建角色 | `views/system/role.vue` | POST `/sys/role` | `RbacController.createRole()` | `sys_role` | - | DISCOVERED |
| RBAC-003 | 编辑角色 | `views/system/role.vue` | PUT `/sys/role/{id}` | `RbacController.updateRole()` | `sys_role` | - | DISCOVERED |
| RBAC-004 | 删除角色 | `views/system/role.vue` | DELETE `/sys/role/{id}` | `RbacController.deleteRole()` | `sys_role` | - | DISCOVERED |
| RBAC-005 | 权限分配 | `views/system/role.vue` | - | `RbacService.assignPermissions()` | `sys_role_permission` | - | PARTIAL |
| RBAC-006 | 操作日志 | `views/system/log.vue` | GET `/sys/log/page` | `SysLogController` | `sys_log` | - | DISCOVERED |

### 4.6 MEMBER — 会员管理

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| MEMBER-001 | 会员列表 | `views/member/index.vue` | POST `/member/page` | `MemberController.page()` | `member` | - | DISCOVERED |
| MEMBER-002 | 会员详情 | `views/member/index.vue` | GET `/member/{id}` | `MemberController.getById()` | `member` | - | DISCOVERED |
| MEMBER-003 | 创建会员 | `views/member/index.vue` | POST `/member` | `MemberController.create()` | `member` | - | DISCOVERED |
| MEMBER-004 | 编辑会员 | `views/member/index.vue` | PUT `/member` | `MemberController.update()` | `member` | - | DISCOVERED |
| MEMBER-005 | 删除会员 | `views/member/index.vue` | DELETE `/member/{id}` | `MemberController.delete()` | `member` | - | DISCOVERED |
| MEMBER-006 | 会员冻结 | `views/member/index.vue` | POST `/member/{id}/freeze` | `MemberController.freeze()` | `member` | - | DISCOVERED |
| MEMBER-007 | 会员解冻 | `views/member/index.vue` | POST `/member/{id}/unfreeze` | `MemberController.unfreeze()` | `member` | - | DISCOVERED |
| MEMBER-008 | 会员标签 | `views/member/index.vue` | GET/POST `/member/tag/*` | `MemberTagController` | `member_tag` | - | DISCOVERED |
| MEMBER-009 | 批量打标签 | `views/member/index.vue` | POST `/member/batch/tag` | `MemberController.batchTag()` | `crm_member_tag_rel` | - | DISCOVERED |
| MEMBER-010 | 会员导入 | `views/member/index.vue` | POST `/member/import` | `MemberController.import()` | `member` | - | DISCOVERED |
| MEMBER-011 | 会员导出 | `views/member/index.vue` | GET `/member/export` | `MemberController.export()` | `member` | - | DISCOVERED |
| MEMBER-012 | 会员搜索 | `views/member/index.vue` | POST `/member/page` | `MemberController.page()` | `member` | - | DISCOVERED |
| MEMBER-013 | 会员统计 | `views/dashboard.vue` | GET `/member/stats` | `MemberController.stats()` | `member` | - | DISCOVERED |
| MEMBER-014 | 会员资料编辑 | `views/member/index.vue` | POST `/member/{id}/profile` | `MemberController.saveProfile()` | `member_profile` | - | DISCOVERED |

### 4.7 LEVEL — 会员等级

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| LEVEL-001 | 等级列表 | `views/setting/level.vue` | GET `/member-level/list` | `MemberLevelController` | `member_level` | - | DISCOVERED |
| LEVEL-002 | 等级详情 | - | GET `/member-level/{id}` | `MemberLevelController` | `member_level` | - | DISCOVERED |
| LEVEL-003 | 创建等级 | `views/setting/level.vue` | POST `/member-level` | `MemberLevelController` | `member_level` | - | DISCOVERED |
| LEVEL-004 | 编辑等级 | `views/setting/level.vue` | PUT `/member-level` | `MemberLevelController` | `member_level` | - | DISCOVERED |
| LEVEL-005 | 删除等级 | `views/setting/level.vue` | DELETE `/member-level/{id}` | `MemberLevelController` | `member_level` | - | DISCOVERED |
| LEVEL-006 | 等级权益配置 | `views/technician/benefit.vue` | GET/POST `/technician/level/benefit/*` | `LevelBenefitController` | `level_benefit` | - | DISCOVERED |

### 4.8 WALLET — 会员储值

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| WALLET-001 | 余额查询 | `views/wallet/recharge.vue` | GET `/wallet/{memberId}` | `WalletController` | `wallet` | - | DISCOVERED |
| WALLET-002 | 储值充值 | `views/wallet/recharge.vue` | POST `/wallet/recharge` | `WalletController.recharge()` | `wallet_transaction` | - | DISCOVERED |
| WALLET-003 | 储值消费 | `views/pos/checkout.vue` | POST `/wallet/consume` | `WalletController.consume()` | `wallet_transaction` | - | DISCOVERED |
| WALLET-004 | 储值退款 | `views/pos/refund.vue` | POST `/wallet/refund` | `WalletController.refund()` | `wallet_transaction` | - | DISCOVERED |
| WALLET-005 | 流水查询 | `views/wallet/transactions.vue` | GET `/wallet/{memberId}/transactions` | `WalletController.transactions()` | `wallet_transaction` | - | DISCOVERED |
| WALLET-006 | 全部流水 | `views/wallet/transactions.vue` | GET `/wallet/transactions/all` | `WalletController.allTransactions()` | `wallet_transaction` | - | DISCOVERED |
| WALLET-007 | 充值优惠规则 | `views/wallet/recharge.vue` | GET `/wallet/promotion/rules` | `WalletController.promotionRules()` | `wallet_promotion` | - | DISCOVERED |
| WALLET-008 | 优惠计算 | `views/wallet/recharge.vue` | GET `/wallet/promotion/calc` | `WalletController.promotionCalc()` | - | - | DISCOVERED |

### 4.9 BOOKING — 预约管理

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| BOOKING-001 | 预约日历 | `views/booking/calendar.vue` | GET `/booking/page` | `BookingController.page()` | `booking` | - | DISCOVERED |
| BOOKING-002 | 预约列表 | `views/booking/list.vue` | GET `/booking/list` | `BookingController.list()` | `booking` | - | DISCOVERED |
| BOOKING-003 | 创建预约 | `views/booking/booking-form.vue` | POST `/booking` | `BookingController.create()` | `booking` | - | DISCOVERED |
| BOOKING-004 | 编辑预约 | `views/booking/booking-form.vue` | PUT `/booking/{id}` | `BookingController.update()` | `booking` | - | DISCOVERED |
| BOOKING-005 | 取消预约 | `views/booking/list.vue` | DELETE `/booking/{id}` | `BookingController.delete()` | `booking` | - | DISCOVERED |
| BOOKING-006 | 预约签到 | `views/booking/list.vue` | POST `/booking/{id}/checkin` | `BookingController.checkin()` | `booking` | - | DISCOVERED |
| BOOKING-007 | 预约完成 | `views/booking/list.vue` | POST `/booking/{id}/complete` | `BookingController.complete()` | `booking` | - | DISCOVERED |
| BOOKING-008 | 预约状态变更 | `views/booking/list.vue` | PUT `/booking/{id}/status` | `BookingController.changeStatus()` | `booking` | - | DISCOVERED |
| BOOKING-009 | 可用时间查询 | `views/booking/booking-form.vue` | GET `/booking/available` | `BookingController.available()` | `booking` | - | DISCOVERED |
| BOOKING-010 | 排班管理 | `views/booking/schedule.vue` | GET/POST `/schedule/*` | `ScheduleController` | `employee_schedule` | - | DISCOVERED |
| BOOKING-011 | 房/床位管理 | `views/booking/resource.vue` | GET/POST `/room/*` | `RoomController` | `room` | - | DISCOVERED |
| BOOKING-012 | 候补队列 | `views/booking/waitlist.vue` | GET/POST `/booking/waitlist/*` | `BookingController.waitlist()` | `booking_waitlist` | - | PARTIAL |

### 4.10 SERVICE — 服务项目

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| SERVICE-001 | 服务列表 | `views/biz/service-item.vue` | GET `/biz/service-item/list` | `BizServiceItemController` | `biz_service` | - | DISCOVERED |
| SERVICE-002 | 服务详情 | - | GET `/biz/service-item/{id}` | `BizServiceItemController` | `biz_service` | - | DISCOVERED |
| SERVICE-003 | 创建服务 | `views/biz/service-item.vue` | POST `/biz/service-item` | `BizServiceItemController.create()` | `biz_service` | - | DISCOVERED |
| SERVICE-004 | 编辑服务 | `views/biz/service-item.vue` | PUT `/biz/service-item/{id}` | `BizServiceItemController.update()` | `biz_service` | - | DISCOVERED |
| SERVICE-005 | 删除服务 | `views/biz/service-item.vue` | DELETE `/biz/service-item/{id}` | `BizServiceItemController.delete()` | `biz_service` | - | DISCOVERED |
| SERVICE-006 | 服务分类 | `views/setting/category.vue` | GET/POST `/category/*` | `CategoryController` | `category` | - | DISCOVERED |

### 4.11 PACKAGE — 套餐次卡

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| PACKAGE-001 | 套餐列表 | `views/package/index.vue` | GET `/packages/list` | `PackageController` | `package_definition` | - | DISCOVERED |
| PACKAGE-002 | 套餐详情 | - | GET `/packages/{id}` | `PackageController` | `package_definition` | - | DISCOVERED |
| PACKAGE-003 | 创建套餐 | `views/package/index.vue` | POST `/packages` | `PackageController.create()` | `package_definition` | - | DISCOVERED |
| PACKAGE-004 | 编辑套餐 | `views/package/index.vue` | PUT `/packages/{id}` | `PackageController.update()` | `package_definition` | - | DISCOVERED |
| PACKAGE-005 | 删除套餐 | `views/package/index.vue` | DELETE `/packages/{id}` | `PackageController.delete()` | `package_definition` | - | DISCOVERED |
| PACKAGE-006 | 购买套餐 | `views/package/purchase-dialog.vue` | POST `/packages/purchase` | `PackageController.purchase()` | `package_order` | - | DISCOVERED |
| PACKAGE-007 | 会员套餐查询 | `views/package/member-orders.vue` | GET `/packages/member-orders` | `PackageController.memberOrders()` | `package_order` | - | DISCOVERED |
| PACKAGE-008 | 套餐核销 | `views/pos/checkout.vue` | POST `/packages/redeem` | `PackageController.redeem()` | `package_usage` | - | DISCOVERED |
| PACKAGE-009 | 多人共享 | `views/package/share.vue` | GET/POST `/crm/package/share/*` | `SharedPackageService` | `shared_package` | - | DISCOVERED |
| PACKAGE-010 | 套餐次卡统计 | `views/dashboard.vue` | GET `/packages/stats` | `PackageController.stats()` | `package_order` | - | DISCOVERED |

### 4.12 POS — 收银订单

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| POS-001 | 收银台 | `views/pos/checkout.vue` | GET `/pos/services/list` | `PosController.serviceList()` | `biz_service` | - | DISCOVERED |
| POS-002 | 创建订单 | `views/pos/checkout.vue` | POST `/pos/orders` | `PosController.createOrder()` | `pos_order` | - | DISCOVERED |
| POS-003 | 订单详情 | `views/pos/orders.vue` | GET `/pos/orders/{id}` | `PosController.detail()` | `pos_order` | - | DISCOVERED |
| POS-004 | 订单列表 | `views/pos/orders.vue` | POST `/pos/orders/page` | `PosController.page()` | `pos_order` | - | DISCOVERED |
| POS-005 | 今日订单 | `views/pos/orders.vue` | GET `/pos/orders/today` | `PosController.today()` | `pos_order` | - | DISCOVERED |
| POS-006 | 订单支付 | `views/pos/checkout.vue` | POST `/pos/orders/{id}/pay` | `PosController.pay()` | `pos_payment` | - | DISCOVERED |
| POS-007 | 订单退款 | `views/pos/refund.vue` | POST `/pos/refund` | `PosRefundController.create()` | `pos_refund` | - | DISCOVERED |
| POS-008 | 退款审核 | `views/pos/refund.vue` | POST `/pos/refund/{id}/audit` | `PosRefundController.audit()` | `pos_refund` | - | PARTIAL |
| POS-009 | 订单统计 | `views/pos/stats.vue` | GET `/pos/orders/stats` | `PosController.stats()` | `pos_order` | - | DISCOVERED |
| POS-010 | 支付对账 | `views/pos/reconciliation.vue` | GET `/pos/reconciliation/page` | `PosReconciliationController` | `pos_reconciliation` | - | DISCOVERED |
| POS-011 | 订单作废 | `views/pos/orders.vue` | POST `/pos/orders/{id}/void` | `PosController.voidOrder()` | `pos_order` | - | DISCOVERED |
| POS-012 | 订单明细项 | `views/pos/checkout.vue` | GET `/pos/orders/{id}/items` | `PosController.orderItems()` | `pos_order_item` | - | DISCOVERED |

### 4.13 PAY — 支付管理

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| PAY-001 | 支付配置 | `views/system/payment.vue` | GET/PUT `/sys/payment/*` | `PaymentConfigController` | `payment_config` | - | DISCOVERED |
| PAY-002 | 数字人民币 | `views/pay/dcep.vue` | GET/POST `/pay/dcep/*` | `DcepConfigController` | `dcep_config` | - | DISCOVERED |
| PAY-003 | **硬钱包 NFC/SE** | `views/payment/dcep-advanced.vue` | GET/POST `/dcep/wallet/*` | `DcepHardwareWalletController` | `dcep_wallet` | - | ✅ **DCEP-01 完整实现** |
| PAY-004 | 离线交易 | `views/payment/dcep-advanced.vue` | GET/POST `/dcep/offline/*` | `DcepOfflineTransactionController` | `dcep_offline_tx` | - | PARTIAL |
| PAY-005 | **子钱包独立账本** | - | GET/POST `/dcep/sub-wallet/*` | `DcepSubWalletController` | `dcep_sub_wallet` | - | ✅ **DCEP-05 完整实现** |
| PAY-006 | 支付宝/微信 | `views/system/payment.vue` | - | `AlipayService` / `WechatPayService` | - | - | PARTIAL |
| PAY-006 | **海外支付（Stripe/Square/PayPal）** | `views/payment/overseas-payment.vue` | GET/POST `/overseas-payment/payment/*` | `OverseasPaymentController` | `biz_overseas_payment` | - | ✅ **PAY-006 完整实现** |
| PAY-007 | **海外网关配置** | `views/payment/overseas-payment.vue` | CRUD `/overseas-payment/gateway/*` | `OverseasPaymentGatewayController` | `biz_overseas_payment_gateway` | - | ✅ 完整实现 |
| PAY-008 | **BNPL/花呗（先买后付）** | `views/payment/bnpl-huabei.vue` | GET/POST `/bnpl/*` | `BnplController` | `biz_bnpl_order` | - | ✅ 完整实现 |
| PAY-009 | **支付回调（Webhook）** | - | POST `/overseas-payment/webhook/{gatewayCode}` | `PaymentWebhookController` | `biz_payment_webhook_log` | - | ✅ 完整实现 |
| PAY-010 | **支付路由规则** | `views/payment/overseas-payment.vue` | CRUD `/overseas-payment/route/*` | `PaymentRouteRuleController` | `biz_payment_route_rule` | - | ✅ 完整实现 |
| PAY-011 | **礼品卡** | `views/gift-card/index.vue` | `/gift-card/*` | `GiftCardService`（Controller 形态） | `gift_card` | - | ✅ 完整实现 |
| PAY-012 | **国际支付演示（5 网关）** | - | `/intl-payment/*` | `IntlPaymentService`（Controller 形态） | 内存存储 | - | ✅ 完整实现 |
| PAY-009 | 订单支付 | `views/pos/checkout.vue` | POST `/pos/orders/{id}/pay` | `PosController.pay()` | `pos_payment` | - | DISCOVERED |
| PAY-010 | 支付路由 | `views/system/payment.vue` | GET/POST `/payment/route/*` | `PaymentRouteController` | `payment_route_rule` | - | PARTIAL |

### 4.14 COMM — 员工提成

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| COMM-001 | 提成规则列表 | `views/commission/rules.vue` | GET `/commission/rules/list` | `CommissionController` | `commission_rule` | - | DISCOVERED |
| COMM-002 | 创建提成规则 | `views/commission/rules.vue` | POST `/commission/rules` | `CommissionController.createRule()` | `commission_rule` | - | DISCOVERED |
| COMM-003 | 编辑提成规则 | `views/commission/rules.vue` | PUT `/commission/rules/{id}` | `CommissionController.updateRule()` | `commission_rule` | - | DISCOVERED |
| COMM-004 | 删除提成规则 | `views/commission/rules.vue` | DELETE `/commission/rules/{id}` | `CommissionController.deleteRule()` | `commission_rule` | - | DISCOVERED |
| COMM-005 | 提成计算 | `views/pos/checkout.vue` | POST `/commission/calculate/order/{orderId}` | `CommissionCalculationService` | `commission_record` | - | DISCOVERED |
| COMM-006 | 提成记录查询 | `views/commission/records.vue` | GET `/commission/records/page` | `CommissionController.recordPage()` | `commission_record` | - | DISCOVERED |
| COMM-007 | 提成模拟计算 | `views/commission/simulation.vue` | GET/POST `/commission/simulation/*` | `CommissionSimulationController` | - | - | DISCOVERED |
| COMM-008 | 工资生成 | `views/commission/salary.vue` | POST `/commission/salary/generate` | `CommissionController.generateSalary()` | `commission_salary` | - | DISCOVERED |
| COMM-009 | 工资报表 | `views/commission/salary.vue` | GET `/commission/salary/page` | `CommissionController.salaryPage()` | `commission_salary` | - | DISCOVERED |
| COMM-010 | 结算管理 | `views/commission/settlement.vue` | GET/POST `/commission/
settlement/*` | `CommissionSettlementController` | `commission_settlement` | - | DISCOVERED |
| COMM-011 | 提成批次 | `views/commission/batch.vue` | GET/POST `/commission/batch/*` | `CommissionBatchController` | `commission_batch` | - | PARTIAL |
| COMM-012 | 提成冲正 | `views/commission/reversal.vue` | GET/POST `/commission/reversal/*` | `CommissionReversalController` | `commission_reversal` | - | PARTIAL |

### 4.15 STOCK — 库存管理

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| STOCK-001 | 商品列表 | `views/stock/product.vue` | GET `/stock/products/list` | `StockController.productList()` | `stock_product` | - | DISCOVERED |
| STOCK-002 | 商品详情 | - | GET `/stock/products/{id}` | `StockController.productDetail()` | `stock_product` | - | DISCOVERED |
| STOCK-003 | 创建商品 | `views/stock/product.vue` | POST `/stock/products` | `StockController.createProduct()` | `stock_product` | - | DISCOVERED |
| STOCK-004 | 编辑商品 | `views/stock/product.vue` | PUT `/stock/products/{id}` | `StockController.updateProduct()` | `stock_product` | - | DISCOVERED |
| STOCK-005 | 库存查询 | `views/stock/list.vue` | GET `/stock/inventory/page` | `StockController.inventoryPage()` | `stock_inventory` | - | DISCOVERED |
| STOCK-006 | 入库 | `views/stock/record.vue` | POST `/stock/inbound` | `StockController.inbound()` | `stock_record` | - | DISCOVERED |
| STOCK-007 | 出库 | `views/stock/record.vue` | POST `/stock/outbound` | `StockController.outbound()` | `stock_record` | - | DISCOVERED |
| STOCK-008 | 盘点管理 | `views/stock/check.vue` | GET/POST `/stock/check/*` | `StockController.check()` | `stock_check` | - | DISCOVERED |
| STOCK-009 | 库存预警 | `views/stock/alert.vue` | GET `/stock/alert/page` | `StockController.alertPage()` | `stock_alert` | - | DISCOVERED |
| STOCK-010 | 消耗记录 | `views/stock/consumption.vue` | GET `/stock/consumption/page` | `StockController.consumptionPage()` | `stock_consumption` | - | DISCOVERED |
| STOCK-011 | 消耗追溯 | `views/stock/trace.vue` | GET `/stock/consumption/order/{orderId}` | `StockController.consumptionByOrder()` | `stock_consumption` | - | DISCOVERED |
| STOCK-012 | 库存统计 | `views/stock/stats.vue` | GET `/stock/stats` | `StockController.stats()` | `stock_inventory` | - | DISCOVERED |
| STOCK-013 | 效期追踪 | `views/material/expiry-tracking.vue` | GET `/stock/expiry/*` | `ExpiryScanController` | `stock_expiry` | - | DISCOVERED |
| STOCK-014 | 耗材绑定 | `views/material/binding.vue` | GET/POST `/material/binding/*` | `ServiceMaterialController` | `service_material` | - | PARTIAL |

### 4.16 COUPON — 优惠券

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| COUPON-001 | 优惠券列表 | `views/mkt/coupon.vue` | GET `/market/coupon/page` | `CouponController.page()` | `marketing_coupon` | - | DISCOVERED |
| COUPON-002 | 优惠券详情 | - | GET `/market/coupon/{id}` | `CouponController.get()` | `marketing_coupon` | - | DISCOVERED |
| COUPON-003 | 创建优惠券 | `views/mkt/coupon.vue` | POST `/market/coupon` | `CouponController.create()` | `marketing_coupon` | - | DISCOVERED |
| COUPON-004 | 编辑优惠券 | `views/mkt/coupon.vue` | PUT `/market/coupon/{id}` | `CouponController.update()` | `marketing_coupon` | - | DISCOVERED |
| COUPON-005 | 删除优惠券 | `views/mkt/coupon.vue` | DELETE `/market/coupon/{id}` | `CouponController.delete()` | `marketing_coupon` | - | DISCOVERED |
| COUPON-006 | 发放优惠券 | `views/mkt/coupon.vue` | POST `/market/coupon/grant` | `CouponController.grant()` | `coupon_grant` | - | DISCOVERED |
| COUPON-007 | 核销优惠券 | `views/pos/checkout.vue` | POST `/market/coupon/use` | `CouponController.use()` | `coupon_grant` | - | DISCOVERED |
| COUPON-008 | 会员优惠券查询 | `views/member/index.vue` | GET `/market/coupon/member/{memberId}` | `CouponController.memberCoupons()` | `coupon_grant` | - | DISCOVERED |

### 4.17 POINTS — 积分管理

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| POINTS-001 | 积分查询 | `views/member/index.vue` | GET `/market/point/{memberId}` | `PointController` | `point_record` | - | DISCOVERED |
| POINTS-002 | 积分记录 | `views/member/index.vue` | GET `/market/point/{memberId}/records` | `PointController.records()` | `point_record` | - | DISCOVERED |
| POINTS-003 | 积分增加 | `views/member/index.vue` | POST `/market/point/add` | `PointController.add()` | `point_record` | - | DISCOVERED |
| POINTS-004 | 积分消费 | `views/member/index.vue` | POST `/market/point/consume` | `PointController.consume()` | `point_record` | - | DISCOVERED |
| POINTS-005 | 积分规则 | `views/mkt/points.vue` | GET/POST `/market/point/rule/*` | `MarketPointRuleController` | `market_point_rule` | - | PARTIAL |
| POINTS-006 | 积分商城 | `views/mkt/points.vue` | GET `/market/point/goods/*` | `PointGoodsController` | `point_goods` | - | PARTIAL |

### 4.18 MKT — 营销活动

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| MKT-001 | 活动列表 | `views/mkt/promotion.vue` | GET `/market/activity/page` | `MarketActivityController` | `market_activity` | - | DISCOVERED |
| MKT-002 | 活动详情 | - | GET `/market/activity/{id}` | `MarketActivityController` | `market_activity` | - | DISCOVERED |
| MKT-003 | 创建活动 | `views/mkt/promotion.vue` | POST `/market/activity` | `MarketActivityController.create()` | `market_activity` | - | DISCOVERED |
| MKT-004 | 编辑活动 | `views/mkt/promotion.vue` | PUT `/market/activity/{id}` | `MarketActivityController.update()` | `market_activity` | - | DISCOVERED |
| MKT-005 | 删除活动 | `views/mkt/promotion.vue` | DELETE `/market/activity/{id}` | `MarketActivityController.delete()` | `market_activity` | - | DISCOVERED |
| MKT-006 | 活动统计 | `views/mkt/effect.vue` | GET `/market/activity/stats` | `MarketActivityController.stats()` | `market_activity` | - | DISCOVERED |
| MKT-007 | 渠道配置 | `views/mkt/channel.vue` | GET/POST `/mkt/channel/*` | `MktChannelController` | `mkt_channel` | - | PARTIAL |
| MKT-008 | 消息模板 | `views/mkt/template.vue` | GET/POST `/mkt/template/*` | `MktTemplateController` | `mkt_template` | - | PARTIAL |
| MKT-009 | 触达任务 | `views/mkt/touch-task.vue` | GET/POST `/mkt/touch-task/*` | `MktTouchTaskController` | `mkt_touch_task` | - | PARTIAL |
| MKT-010 | **营销旅程（M16 全链）** | `views/mkt/journey.vue` | GET/POST `/mkt/journey/*` | `MarketingAutomationService` | `mkt_journey` + `mkt_journey_instance` | - | ✅ **M16 完整实现** |

### 4.19 CRM — 客户关怀

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| CRM-001 | 回访任务 | `views/crm/care-follow-task.vue` | GET `/crm/care/follow/task/page` | `CrmFollowUpTaskController` | `crm_follow_up_task` | - | DISCOVERED |
| CRM-002 | 回访计划 | `views/crm/care-follow-plan.vue` | GET/POST `/crm/care/follow/plan/*` | `CrmFollowUpPlanController` | `crm_follow_up_plan` | - | DISCOVERED |
| CRM-003 | 自动任务规则 | `views/crm/care-follow-rule.vue` | GET/POST `/crm/care/follow/rule/*` | `CrmFollowUpRuleController` | `crm_follow_up_rule` | - | DISCOVERED |
| CRM-004 | 投诉工单 | `views/crm/care-complaint.vue` | GET/POST `/crm/care/complaint/*` | `ComplaintTicketController` | `crm_complaint_ticket` | - | DISCOVERED |
| CRM-005 | 客户评价 | `views/crm/care-review.vue` | GET/POST `/crm/care/review/*` | `CrmReviewController` | `crm_review` | - | DISCOVERED |
| CRM-006 | 评价模板 | `views/crm/care-review-tpl.vue` | GET/POST `/crm/care/review/tpl/*` | `CrmReviewTemplateController` | `crm_review_template` | - | DISCOVERED |
| CRM-007 | 跨店规则 | `views/crm/cross-rule.vue` | GET/POST `/crm/cross-rule/*` | `CrmCrossStoreController` | `crm_cross_store_rule` | - | DISCOVERED |
| CRM-008 | 跨店核销 | `views/crm/cross-record.vue` | GET `/crm/cross-record/*` | `CrmCrossStoreController.redeem()` | `crm_cross_store_record` | - | PARTIAL |
| CRM-009 | 客户运营看板 | `views/crm/care-overview.vue` | GET `/crm/care/overview/*` | `CrmCareOpsController` | - | - | PARTIAL |
| CRM-010 | RFM 分群 | `views/intel/rfm.vue` | GET/POST `/intel/rfm/*` | `RfmController` | `rfm_result` | - | DISCOVERED |
| CRM-011 | LTV 预测 | `views/intel/ltv.vue` | GET `/ltv-churn/*` | `LtvChurnController` | `ai_churn_model` | - | DISCOVERED |
| CRM-012 | Cohort 队列 | `views/cohort/index.vue` | GET `/cohort/*` | `CohortController` | `cohort_result` | - | DISCOVERED |

### 4.20 CARE — 护理建议

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| CARE-001 | 配方列表 | `views/care/index.vue` | GET `/care/recipe/list` | `CareSuggestionService` | `care_recipe` | - | DISCOVERED |
| CARE-002 | 配方详情 | - | GET `/care/recipe/{id}` | `CareSuggestionService` | `care_recipe` | - | DISCOVERED |
| CARE-003 | 创建配方 | `views/care/index.vue` | POST `/care/recipe` | `CareSuggestionService.create()` | `care_recipe` | - | DISCOVERED |
| CARE-004 | 编辑配方 | `views/care/index.vue` | POST `/care/recipe/{id}` | `CareSuggestionService.update()` | `care_recipe` | - | DISCOVERED |
| CARE-005 | 护理建议生成 | `views/crm/care-suggestion.vue` | GET/POST `/crm/care/suggestion/*` | `CareSuggestionEngineController` | `care_suggestion` | - | DISCOVERED |
| CARE-006 | 建议规则 | `views/crm/care-suggestion.vue` | GET/POST `/crm/care/suggestion/rule/*` | `CareSuggestionRuleController` | `care_suggestion_rule` | - | DISCOVERED |
| CARE-007 | 建议日志 | - | GET `/crm/care/suggestion/log/*` | `CareSuggestionLogController` | `care_suggestion_log` | - | DISCOVERED |
| CARE-008 | 配方模板 | - | GET/POST `/crm/care/suggestion/template/*` | `CareSuggestionTemplateController` | `care_template` | - | DISCOVERED |

### 4.21 DETECT — 肤质检测

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| DETECT-001 | 肤质档案 | `views/crm/detection.vue` | GET/POST `/crm/detection/*` | `DetectionReportController` | `member_detection` | - | DISCOVERED |
| DETECT-002 | 皮肤检测 | `views/ai-vision/index.vue` | GET/POST `/crm/detection/skin/*` | `SkinDetectionController` | `biz_skin_detection` | - | ✅ 已启用 |
| DETECT-003 | 毛发检测 | - | GET/POST `/crm/detection/hair/*` | `HairDetectionController` | `hair_detection` | - | DISCOVERED |
| DETECT-004 | 美甲检测 | `views/style-gallery/index.vue` | GET/POST `/nail/detection/*` | `NailDetectionController` | `nail_detection` | - | DISCOVERED |
| DETECT-005 | 诊断报告 | `views/crm/detection.vue` | GET `/crm/detection/report/{id}` | `DetectionReportController` | `detection_report` | - | DISCOVERED |
| DETECT-006 | 解决方案 | `views/crm/detection.vue` | GET/POST `/crm/detection/solution/*` | `DetectionSolutionController` | `detection_solution` | - | DISCOVERED |
| DETECT-007 | AI 增强 | - | POST `/ai/enhance/*` | `AiEnhanceService` | - | - | PARTIAL |
| DETECT-008 | 视觉档案 | `views/mh/visual.vue` | GET `/mh/visual/*` | `MhVisualController` | `mh_visual` | - | DISCOVERED |

### 4.22 ATTEND — 考勤管理

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| ATTEND-001 | 考勤打卡 | `views/attendance/index.vue` | POST `/attendance/check-in`, `/attendance/check-out` | `AttendanceController` | `attendance_record` | - | DISCOVERED |
| ATTEND-002 | 考勤记录 | `views/attendance/records.vue` | GET `/attendance/page` | `AttendanceController.page()` | `attendance_record` | - | DISCOVERED |
| ATTEND-003 | 请假管理 | `views/attendance/leave.vue` | GET/POST `/attendance/leave/*` | `LeaveRequestController` | `leave_request` | - | DISCOVERED |
| ATTEND-004 | 考勤统计 | `views/attendance/statistics.vue` | GET `/attendance/stats/*` | `AttendanceController.stats()` | `attendance_record` | - | DISCOVERED |
| ATTEND-005 | 考勤规则配置 | `views/attendance/config.vue` | GET/POST `/attendance/config/*` | `AttendanceConfigController` | `attendance_config` | - | DISCOVERED |
| ATTEND-006 | 员工日程 | `views/booking/schedule.vue` | GET/POST `/employee/schedule/*` | `EmployeeScheduleController` | `employee_schedule` | - | DISCOVERED |
| ATTEND-007 | 排班管理 | `views/booking/schedule.vue` | GET/POST `/schedule/*` | `ScheduleController` | `employee_schedule` | - | DISCOVERED |
| ATTEND-008 | 打卡配置 | `views/attendance/config.vue` | GET/POST `/attendance/config/checkin/*` | `AttendanceConfigController` | `attendance_checkin_config` | - | PARTIAL |

### 4.23 REPORT — 报表统计

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| REPORT-001 | 数据看板 | `views/dashboard.vue` | GET `/report/dashboard/*` | `ReportController.dashboard()` | - | - | DISCOVERED |
| REPORT-002 | 营业报表 | `views/report/biz.vue` | GET `/report/biz/*` | `ReportController.biz()` | `pos_order` | - | DISCOVERED |
| REPORT-003 | 会员报表 | `views/dashboard.vue` | GET `/report/member/*` | `ReportController.member()` | `member` | - | DISCOVERED |
| REPORT-004 | 员工报表 | `views/report/employee.vue` | GET `/report/employee/*` | `ReportController.employee()` | `employee` | - | PARTIAL |
| REPORT-005 | 导出任务 | `views/report/export.vue` | GET `/report/export/*` | `ReportController.export()` | `report_export_task` | - | DISCOVERED |
| REPORT-006 | 指标口径 | `views/report/caliber.vue` | GET `/report/caliber/*` | `ReportCaliberController` | `report_caliber` | - | PARTIAL |
| REPORT-007 | 导出管理 | `views/report/task.vue` | GET `/report/task/*` | `ReportTaskController` | `report_task` | - | PARTIAL |
| REPORT-008 | 数据中台 | `views/dev/DataHub.vue` | GET `/sys/datahub/*` | `DataGovernanceService` | - | - | PARTIAL |
| REPORT-009 | 性能监控 | - | GET `/perf/*` | `PerfLoadTestService` | `perf_metrics` | - | PARTIAL |
| REPORT-010 | 链路追踪 | - | GET `/trace/*` | `TraceService` | `trace_span` | - | PARTIAL |
| REPORT-011 | **复购率总览** | - | GET `/intel/repurchase/overview` | `RepurchaseRateController` | `pos_order` | - | ✅ **NEW** |
| REPORT-012 | **复购率月度趋势** | - | GET `/intel/repurchase/trend` | `RepurchaseRateController` | `pos_order` | - | ✅ **NEW** |
| REPORT-013 | **复购层级分桶** | - | GET `/intel/repurchase/tier` | `RepurchaseRateController` | `pos_order` | - | ✅ **NEW** |

#### 业务口径（M11-03 复购率，2026-09-04 定稿版本）

##### 核心公式（严格口径，与 PRD 5.1 一致）

```
复购率 = (30 天内有 ≥2 次消费订单的会员数) / (30 天内有 ≥1 次消费订单的活跃会员数) × 100%
```

##### 口径要素

| 项 | 取值口径 |
|----|----------|
| 窗口 | 滚动 30 天 [endDate - 29 days, endDate]，按 `pay_time` 判定 |
| 消费订单 | `pos_order.type = 1`（消费）且 `status = 2`（已支付）且 `deleted = 0` |
| 排除 | 充值订单（type=2/3）、进行中（status=1）、退款（status=3）、撤销（status=4） |
| 分母 | 窗口内有 ≥1 次消费订单的 `member_id` 集合（去重会员数） |
| 分子 | 其中有 ≥2 次消费订单的会员数 |
| 维度 | 默认 tenant，可选 `storeId` |
| 目标值 | > 40%（PRD 5.1，行业均值 25%） |

##### 返回字段说明

| 字段 | 含义 |
|------|------|
| `activeMembers` | 活跃会员数（分母） |
| `repurchasedMembers` | 复购会员数（分子） |
| `repurchaseRate` | 复购率 % |
| `avgOrderCount` | 活跃会员人均订单数 |
| `avgRepurchaseOrders` | 复购会员人均订单数 |
| `reorderMembers3plus` | 高频复购（≥3 次）会员数 |
| `totalOrders` / `totalAmount` | 窗口内总订单数 / 总金额 |

##### `/intel/repurchase/trend` 月度趋势

- 默认最近 6 个月（可调 `months` 参数，1-24）
- 每行 = 当月自然月复购率
- 与总览口径完全一致

##### `/intel/repurchase/tier` 消费层级分桶

| 分桶 | 定义 | 业务含义 |
|------|------|----------|
| L1_POTENTIAL | 窗口内仅 1 次消费 | 潜在复购客，需激活 |
| L2_REPURCHASED | 窗口内恰好 2 次 | 已复购，需保持 |
| L3_HIGH | 窗口内 3-4 次 | 高频客户 |
| L4_LOYAL | 窗口内 ≥5 次 | 忠诚客户，核心资产 |

##### 口径不包含的内容（待后续迭代）

- **充值复购**：充值（type=2）单独定义口径，本次未覆盖
- **退款后重新购买**：当前口径以最终 `status=2` 为准，退款记录不影响消费次数计数
- **会员生命周期口径**：PRD 中的"30天内复购会员 / 总会员"存在"总会员"歧义，严格口径改为"活跃会员"作分母，更符合运营实际

### 4.24 IOT — 物联网

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| IOT-001 | 设备列表 | `views/iot/iot-management.vue` | GET `/iot/device/list` | `IotDeviceController` | `iot_device` | - | DISCOVERED |
| IOT-002 | 设备详情 | - | GET `/iot/device/{id}` | `IotDeviceController` | `iot_device` | - | DISCOVERED |
| IOT-003 | 设备数据 | `views/iot/index.vue` | GET `/iot/data/*` | `IotDeviceDataController` | `iot_telemetry` | - | DISCOVERED |
| IOT-004 | 触发规则 | `views/iot/index.vue` | GET/POST `/iot/rule/*` | `IotTriggerRuleController` | `iot_trigger_rule` | - | DISCOVERED |
| IOT-005 | 设备告警 | `views/iot/index.vue` | GET `/iot/alert/*` | `IotDeviceAlertController` | `iot_alert` | - | DISCOVERED |
| IOT-006 | 设备联动 | `views/iot/management.vue` | POST `/iot/device/link/*` | `IotDeviceController.link()` | `iot_device_link` | - | PARTIAL |

### 4.25 AI — AI 功能

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| AI-001 | AI 智能排班 | `views/ai-scheduler/index.vue` | GET/POST `/ai/scheduler/*` | `AiSchedulerController` | `ai_schedule_plan` | - | DISCOVERED |
| AI-002 | 客流预测 | `views/dev/Forecast.vue` | GET `/ai/forecast/*` | `BookingForecastController` | `booking_forecast_feature` | - | DISCOVERED |
| AI-003 | **AI 视觉识别（M17-03）** | `views/ai-vision/index.vue` | POST `/ai/vision/*` | `AIVisionService` | `cv_model` + `cv_recognition_log` | - | ✅ **M17-03 完整实现** |
| AI-004 | LTV 流失预测 | `views/ltv-churn/index.vue` | GET/POST `/ltv-churn/*` | `LtvChurnRealDataController` + `LtvChurnController` | `ai_churn_model` | - | DISCOVERED |
| AI-005 | 流失训练 | - | POST `/ai/churn/train` | `ChurnTrainingService` | `ai_churn_model` | - | PARTIAL |
| AI-006 | 灰度发布 | `views/dev/GrayRelease.vue` | GET/POST `/gray/*` | `GrayReleaseController` | `feature_flag` / `ab_experiment` / `ab_assignment` | - | DISCOVERED |
| AI-007 | A/B 测试 | `views/dev/GrayRelease.vue` | GET/POST `/ab-test/*` | `AbTestService` | `ab_test` | - | PARTIAL |
| AI-008 | AI 增强服务 | - | POST `/ai/enhance` | `AiEnhanceService` | - | - | PARTIAL |

#### M17-03 AI 视觉识别真实模型推理（2026-09-04 完整实现）

##### 推理提供者架构（CvInferenceProvider）

所有真实云服务均实现 `CvInferenceProvider` 接口，由 Spring 注入到 `Map<String, CvInferenceProvider>`，按 `provider` 代码自动路由：

| Provider | 实现类 | 能力 | 凭证 |
|---------|--------|------|------|
| `TENCENT` | `TencentCvProvider` | 人脸分析（DetectFace API）/ 皮肤 | SecretId + SecretKey |
| `ALIYUN` | `AliyunCvProvider` | 皮肤分割（SegmentSkin API）/ 人脸 | AccessKeyId + AccessKeySecret |
| `BAIDU` | `BaiduCvProvider` | 1:N 人脸搜索（预留） | — |
| `LOCAL` | `LocalCvProvider` | 本地 CNN 推理（TorchServe/TF Serving） | endpoint URL |
| *(fallback)* | sandbox mock | 确定性模拟（无网络调用） | 无需凭证 |

##### 推理调度链路

```
POST /ai/vision/recognize/skin
  → 查询 cv_model 表（tenant=0）
  → CvInferenceProviderRegistry 查找 provider
  → 执行真实 HTTP 调用（腾讯云 / 阿里云 / 本地）
  → 解析响应 JSON
  → 写入 cv_recognition_log（inference_mode = http/sdk/local/mock）
  → 返回结果 + latency + provider
```

##### 推理模式优先级

1. **http** — 真实云 API（配置了凭证时）
2. **sdk** — 厂商 SDK（本版本未接入）
3. **local** — 本地模型（TorchServe/TF Serving）
4. **mock** — sandbox 模拟（无凭证时自动回退）

##### 识别类型

| 识别类型 | API Path | Provider | 返回字段 |
|---------|----------|----------|----------|
| 皮肤检测 | POST `/ai/vision/recognize/skin` | ALIYUN (M17-03 核心) | skinType / oilLevel / moistureLevel / elasticityLevel / skinScore / issues |
| 人脸识别 | POST `/ai/vision/recognize/face` | TENCENT | faceId / beautyScore / ageRange / gender / landmarks |
| 美甲试戴 | POST `/ai/vision/recognize/nail-tryon` | TENCENT | resultImage / nailDetected / colorMatched |
| 头发分析 | POST `/ai/vision/recognize/hair` | ALIYUN | hairDensity / hairLossLevel / scalpOiliness / recommendations |

##### CV 模型注册（cv_model 表）

- 8 个预置模型（TENCENT_FACE / ALI_SKIN_AI / BAIDU_FACE / AZURE_CV / GOOGLE_VISION / LOCAL_SKIN_CNN 等）
- 支持 tenant=0 全局模型 + 租户私有模型
- 可通过 `/ai/vision/model/init-default` 一键初始化

##### 识别日志（cv_recognition_log 表）

不可篡改审计日志，每笔识别记录：
- `model_code` / `provider` / `inference_mode` — 溯源推理链路
- `latency_ms` — 性能监控
- `confidence` — 结果可信度
- `result_json` — 原始推理结果

##### 凭证配置（application.yml）

```yaml
dcep:
  ai:
    tencent:
      secret-id: your-secret-id
      secret-key: your-secret-key
      app-id: 125xxxxxxx
      region: ap-guangzhou
    aliyun:
      access-key-id: your-access-key-id
      access-key-secret: your-access-key-secret
      region-id: cn-shanghai
    local:
      endpoint: http://localhost:8081/infer
      timeout-ms: 5000
```

##### 数据库表

| 表 | 说明 |
|----|------|
| `cv_model` | 模型注册表（endpoint / apiKey / apiSecret / isSandbox） |
| `cv_recognition_log` | 识别日志（provider / inference_mode / latency / confidence） |
| `biz_skin_detection` | 肤质检测记录（水分/油脂/弹性/毛孔/皱纹/敏感度） |

#### M16 营销旅程自动化触发器（2026-09-04 完整实现）

##### 事件驱动触发链路

```
业务事件发生（注册/下单/充值/预约/消费/生日）
        │
        ▼
  MarketingEventPublisher.publish(event)
        │
        ▼
  @EventListener JourneyTriggerService.onMarketingEvent() [异步]
        │
        ├── 查询 mkt_journey_trigger（status=2，按 priority 排序）
        │
        ├── matchFilters() 评估 JSON 过滤条件
        │     • minOrderAmount / minRechargeAmount
        │     • memberTag / storeIds
        │     • daysSinceLastConsume / daysFromBirthday
        │
        ├── passesDailyLimit() 防骚扰（每会员每日最多 1 次）
        │
        └── enrollMemberInJourney() 幂等入旅程实例
              │
              ▼
        mkt_journey_trigger_log（MATCHED / ENTERED / SKIPPED / FAILED）
```

##### 支持的事件类型（MarketingEventTypes）

| 事件 | 触发场景 | 典型旅程 |
|------|----------|----------|
| `MEMBER_REGISTER` | 新会员注册 | 新客欢迎旅程 |
| `ORDER_PLACED` | 下单支付成功 | 下单后推荐旅程 |
| `ORDER_PAID` | 订单已付款 | 消费确认旅程 |
| `CONSUME` | 服务消费核销 | 流失挽回旅程 |
| `RECHARGE` | 会员充值 | 高额充值关怀 |
| `BOOKING_CREATED` | 预约创建 | 预约提醒旅程 |
| `BIRTHDAY` | 生日触发 | 生日特权旅程 |
| `MEMBER_DAY` | 会员日触发 | 会员日特权旅程 |

##### 过滤条件 JSON Schema

```json
{
  "minOrderAmount": 100,         // 最低订单金额
  "minRechargeAmount": 1000,    // 最低充值金额
  "memberTag": "VIP",           // 会员标签匹配
  "storeIds": [1, 2, 3],        // 门店白名单
  "daysSinceLastConsume": {"gt": 30},  // 距上次消费天数
  "daysFromBirthday": 0         // 距生日天数
}
```

##### 防骚扰机制

- **幂等**：同 `eventId` + `triggerId` 不重复触发（唯一索引）
- **每日限次**：每会员每规则每天最多 1 次（可配置）
- **批量限制**：`batchLimit` 控制单次最多触达会员数

##### 定时调度（与事件驱动双轨）

| 调度 | cron | 作用 |
|------|------|------|
| 每日全量扫描（MemberLifecycleService） | `0 0 2 * * ?` | 凌晨扫描生日/RFM 等 |
| 旅程实例推进（advanceOverdueInstances） | 每 5 分钟 | 自动推进在途旅程节点 |

##### API 端点

| Method | Path | 功能 |
|--------|------|------|
| GET | `/mkt/journey/trigger/page` | 触发器列表（分页） |
| GET | `/mkt/journey/trigger/{id}` | 触发器详情 |
| POST | `/mkt/journey/trigger` | 新建触发器 |
| PUT | `/mkt/journey/trigger/{id}` | 更新触发器 |
| POST | `/mkt/journey/trigger/{id}/toggle` | 启用/暂停 |
| DELETE | `/mkt/journey/trigger/{id}` | 删除触发器 |
| GET | `/mkt/journey/trigger/{id}/logs` | 触发日志 |
| POST | `/mkt/journey/trigger/{id}/run` | 手动测试触发 |
| GET | `/mkt/journey/trigger/stats` | 触发统计（entryRate 等） |
| GET | `/mkt/journey/page` | 旅程列表（分页） |
| GET | `/mkt/journey/{id}` | 旅程详情（含 nodes/edges JSON） |
| POST | `/mkt/journey` | 新建旅程 |
| PUT | `/mkt/journey/{id}` | 更新旅程 |
| POST | `/mkt/journey/{id}/publish` | 发布旅程 |
| POST | `/mkt/journey/{id}/pause` | 暂停旅程 |
| POST | `/mkt/journey/{id}/enter` | 手动入旅程 |
| POST | `/mkt/journey/instance/{id}/next` | 推进旅程实例 |
| GET | `/mkt/task/page` | 触达任务（分页） |
| POST | `/mkt/task` | 创建触达任务 |
| POST | `/mkt/task/{id}/start` | 执行触达（逐条发送+重试） |
| POST | `/mkt/task/{id}/pause` | 暂停 |
| POST | `/mkt/task/{id}/cancel` | 取消 |
| GET | `/mkt/conversion/funnel` | 触达漏斗（触达→已读→转化） |
| GET | `/mkt/conversion/roi` | ROI 回报率 |
| GET | `/mkt/conversion/by-channel` | 按渠道统计 |
| POST | `/mkt/conversion/track` | 记录转化（业务回调） |

##### 数据库表

| 表 | 说明 |
|----|------|
| `mkt_journey` | 旅程定义（nodes/edges JSON，支持 DAG 流程图） |
| `mkt_journey_instance` | 旅程实例（memberId / currentNodeId / context） |
| `mkt_journey_trigger` | 触发器规则（eventType + filterConditions + journeyId） |
| `mkt_journey_trigger_log` | 触发执行日志（MATCHED/ENTERED/SKIPPED/FAILED） |
| `marketing_touch_task` | 触达任务（来源：MANUAL/JOURNEY/SCHEDULE/TRIGGER） |
| `marketing_touch_log` | 触达明细（status: pending→sent→read→converted） |
| `marketing_touch_conversion` | 转化记录（amount / convert_at） |
| `member_lifecycle_rule` | 生命周期规则（BIRTHDAY/MEMBER_DAY/RETENTION/INVITE） |
| `member_lifecycle_log` | 生命周期触发日志 |

#### M14 动态定价 A/B 测试（2026-09-04 完整实现）

##### A/B 实验完整链路

```
收银台 quote() 调用
        │
        ▼
DynamicPricingService.quote()
        │
        ├── 1. 应用动态定价规则 → amount_after_rules
        │
        ├── 2. PricingExperimentService.findActiveExperiment()
        │     • tenant 范围内第一个 RUNNING 实验
        │     • 检查适用条件：项目 / 门店 / 会员等级 / 最低金额
        │
        ├── 3. assignVariant(memberId, exp)
        │     • 查 ab_assignment（幂等）
        │     • 首次分配：hash(memberId × 31 + expKey) % 1000
        │     • 按 trafficRatio 切分：controlABRatio / variantCDRatio
        │     • 返回 "A" / "B" / "C" / "D"
        │
        ├── 4. computeDiscountedPrice(amount, variant, exp)
        │     • 按组计算折扣（PERCENT / FIXED / ORIGINAL）
        │
        └── 5. recordResult() 落库
              • pricing_experiment_result（RECORDED）
              • 增量更新 control_sample_size / variant_sample_size
```

##### 实验组设计（2~4 组）

| 组 | 性质 | 默认值 | 适用场景 |
|----|------|--------|----------|
| **A** | 控制组 | ORIGINAL（原价） | 基准对照 |
| **B** | 控制组 | PERCENT 10（9折） | 小幅折扣 |
| **C** | 变异组 | PERCENT 20（8折） | 中等折扣（核心测试对象） |
| **D** | 变异组 | PERCENT 30（7折） | 大幅折扣（激进方案） |

##### 流量分配算法

```
bucket = |memberId × 31 + expKey.hashCode()| % 1000   // 0~999

if bucket < controlTrafficRatio × 1000 × controlABRatio:        → A
elif bucket < controlTrafficRatio × 1000:                       → B
elif bucket < 1000 - (1 - controlTrafficRatio) × 1000 × variantCDRatio: → C
else:                                                          → D
```

**关键点**：hash 确定性 → 同会员永远同组，跨会话幂等。

##### 折扣模式

| 模式 | 计算公式 |
|------|---------|
| `ORIGINAL` | 不折扣，直接返回原值 |
| `PERCENT` | `price × (100 - value) / 100`（value=10 表示 9 折） |
| `FIXED` | `price - value`（减额，保底 0） |

##### 统计显著性计算（Z-test）

```
pC = controlConversions / controlSampleSize
pV = variantConversions / variantSampleSize
pPool = (controlConversions + variantConversions) / (controlSampleSize + variantSampleSize)
Z = (pV - pC) / sqrt(pPool × (1-pPool) × (1/nC + 1/nV))
confidence = Φ(|Z|)   // 标准正态 CDF (A&S 近似)
liftPercent = (pV - pC) / pC × 100

conclusion:
  • nC < minSampleSize 或 nV < minSampleSize → PENDING
  • confidence < 0.95 → NO_SIGNIFICANCE
  • pV > pC → WIN_VARIANT
  • pV < pC → WIN_CONTROL
```

##### 转化回调链路

```
POS 下单支付成功
        │
        ▼
PricingExperimentService.markConverted(memberId, orderId, amount)
        │
        ├── 查找 RECORDED 状态的实验结果
        ├── 更新为 CONVERTED（写入 order_id / conversion_amount）
        └── 增量更新 control_conversions / variant_conversions
```

##### 定时调度

| 调度 | 频率 | 作用 |
|------|------|------|
| `recomputeAllRunning` | 每 10 分钟 | 自动重算所有 RUNNING 实验显著性 |
| `complete(id)` | 手动 | 结束实验 + 终态计算 |

##### API 端点

| Method | Path | 功能 |
|--------|------|------|
| GET | `/mkt/pricing/experiment/page` | 实验列表（分页） |
| GET | `/mkt/pricing/experiment/{id}` | 实验详情 |
| GET | `/mkt/pricing/experiment/{id}/stats` | 实时统计（nC/nV/转化率/置信度/结论） |
| GET | `/mkt/pricing/experiment/{id}/distribution` | 按组分布（含价格/折扣均值） |
| POST | `/mkt/pricing/experiment/{id}/start` | 启动实验 |
| POST | `/mkt/pricing/experiment/{id}/pause` | 暂停 |
| POST | `/mkt/pricing/experiment/{id}/complete` | 结束 |
| POST | `/mkt/pricing/experiment/{id}/recompute` | 立即重算显著性 |
| POST | `/mkt/pricing/experiment` | 新建实验 |
| PUT | `/mkt/pricing/experiment/{id}` | 更新实验 |
| POST | `/mkt/pricing/quote` | **核心**：收银台报价（含 A/B 实验结果） |

##### 报价返回字段（quote 含 A/B）

```json
{
  "code": 200,
  "data": {
    "baseAmount": 1000,
    "finalAmount": 800,
    "discountAmount": 0,
    "abDiscountAmount": 200,
    "abVariant": "C",
    "abExpKey": "EXP_PRICE_SENSITIVITY_Q3",
    "hitRules": [],
    "hitRuleCode": null,
    "pricingLogId": 12345
  }
}
```

##### 数据库表

| 表 | 说明 |
|----|------|
| `pricing_experiment` | 实验配置（4 组折扣 + traffic 分配 + 显著性阈值） |
| `pricing_experiment_result` | 实验结果（每笔 quote 一次，RECORDED/CONVERTED） |
| `mkt_dynamic_pricing_rule` | 定价规则（已有的 5 类型 + 3 模式） |
| `mkt_pricing_log` | 定价日志（含 abDiscountAmount 字段） |
| `ab_assignment` | 会员分组分配（保证幂等） |

### 4.26 COMP — 合规

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| COMP-001 | 知情同意 | `views/mh/consent.vue` | GET/POST `/mh/consent/*` | `MhConsentController` | `mh_consent` | - | DISCOVERED |
| COMP-002 | H5 知情同意 | `views/mh/consent-h5.vue` | GET `/mh-consent-h5/*` | `MhConsentController.h5()` | `mh_consent` | - | DISCOVERED |
| COMP-003 | 并发症 SOP | `views/complication/index.vue` | GET `/complication/*` | `ComplicationController` | `complication_sop` | - | DISCOVERED |
| COMP-004 | GDPR 合规 | `views/gdpr/index.vue` | GET/POST `/gdpr/*` | `GdprService` | `gdpr_record` | - | DISCOVERED |
| COMP-005 | HIPAA 加固 | `views/hipaa/index.vue` | GET/POST `/hipaa/*` | `HipaaService` | `hipaa_audit` | - | DISCOVERED |
| COMP-006 | 商户资质 | `views/system/qualification.vue` | GET/POST `/sys/qualification/*` | `DcepMerchantQualificationController` | `merchant_qualification` | - | DISCOVERED |

### 4.27 BC — 区块链

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| BC-001 | 区块链溯源 | `views/blockchain/trace.vue` | GET `/blockchain/trace/*` | `BlockchainController` | `blockchain_tx` | - | DISCOVERED |
| BC-002 | 区块链监控 | `views/blockchain/monitor.vue` | GET `/blockchain/monitor/*` | `BlockchainController.monitor()` | `blockchain_monitor` | - | DISCOVERED |
| BC-003 | 智能合约 | `views/payment/dcep-advanced.vue` | GET/POST `/dcep/contract/*` | `DcepSmartContractController` | `dcep_contract` | - | DISCOVERED |
| BC-004 | 离线交易 | `views/payment/dcep-advanced.vue` | GET/POST `/dcep/offline/*` | `DcepOfflineTransactionController` | `dcep_offline_tx` | - | PARTIAL |

#### DCEP-01 硬钱包 NFC/SE 真机对接（2026-09-04 完整实现）

##### 真机通道抽象层

| 通道 | 钱包类型 | 真机载体 |
|------|----------|----------|
| NFC-SIM | `nfc_sim` | 运营商 SWP-SIM 卡（移动 NFC / 联通 / 电信） |
| SE 芯片 | `se_chip` | 嵌入式 eSE 或可穿戴（华为 Watch / Apple Watch SE） |
| IoT 钱包 | `iot_wallet` | 物联网硬件钱包设备 |
| 生物识别 | `biometric` | 指纹 / 3D 人脸专用硬件 |

**抽象接口** `com.meiyetec.dcep.hardware.NfcReader`：
- `isPresent(serial)` — 检测设备
- `selectApplet(serial, aid)` — APDU SELECT（数字人民币 AID = `A0000000032203`）
- `readBalance(serial)` — GET DATA
- `debitOffline(serial, amount, merchantId)` — DEBIT + 双签名
- `close(serial)` — 释放连接

**当前实现** `SimulatedNfcReader`（开发联调，配置 `dcep.hardware.reader=simulated`）。生产替换为运营商/厂商 SDK 实现（如 `NfcSimSdkAdapter`）。

##### 钱包生命周期（10 个新接口）

| 接口 | 功能 | 状态机 |
|------|------|--------|
| `POST /dcep/wallet/{id}/report-lost` | 挂失 | active → lost |
| `POST /dcep/wallet/{id}/unfreeze-lost` | 解挂（需 PIN） | lost → active |
| `POST /dcep/wallet/{id}/freeze` | 冻结 | active → frozen |
| `POST /dcep/wallet/{id}/unfreeze` | 解冻 | frozen → active |
| `POST /dcep/wallet/{id}/close` | 销户（余额须清零） | active → expired |
| `POST /dcep/wallet/{id}/pin` | 设置 PIN（6-12 位 BCrypt 哈希） | — |
| `POST /dcep/wallet/{id}/pin/verify` | 验证 PIN | — |
| `POST /dcep/wallet/{id}/biometric` | 切换生物识别 | — |
| `POST /dcep/wallet/{id}/limit-check` | 单笔/单日限额校验 | — |
| `POST /dcep/wallet/{id}/keys/rotate` | ECDSA 密钥轮换 | — |

##### 密钥体系（DCEP-01 新增）

- **曲线**：ECDSA secp256r1（数字人民币运营机构规范）
- **公钥**：base64 编码存 `publicKey` 字段
- **私钥**：PEM 编码后 AES/ECB/PKCS5Padding 加密存 `private_key_enc`（生产应接 KMS）
- **轮换**：调用 `/keys/rotate` 重新生成，旧私钥立即失效

##### 限额体系

| 项 | 默认值 | 说明 |
|----|--------|------|
| 单笔限额 `maxAmountPerTransaction` | 5000 CNY | 每次扣款上限 |
| 单日限额 `maxAmountPerDay` | 10000 CNY | 每日累计上限 |
| 当日已用 `dailyUsed` | 0 | 跨日自动重置（`dailyResetAt`） |
| 单笔超限 | `reason=EXCEED_PER_TX` | — |
| 单日超限 | `reason=EXCEED_PER_DAY` | 返回 remaining |

##### PIN 安全

- **存储**：`pin_hash` 字段，BCrypt（强度 10）
- **长度**：6-12 位
- **解挂校验**：解挂丢失钱包必须 PIN 通过

##### 模拟实现示例（前端联调）

```bash
# 激活钱包（生成 ECDSA 密钥）
POST /dcep/wallet/activate
{"memberId": 1001, "walletType": "nfc_sim", "deviceModel": "Mate60", "seChipId": "SE-XYZ-001"}

# 设置 PIN
POST /dcep/wallet/1/pin
{"pin": "123456"}

# 验证 PIN（用于支付确认）
POST /dcep/wallet/1/pin/verify
{"pin": "123456"}

# 限额校验
POST /dcep/wallet/1/limit-check
{"amount": 3500}
→ {"ok": true}
→ {"ok": false, "reason": "EXCEED_PER_TX", "limit": 5000, "amount": 6000}
```

##### 数据库变更（DCEP-01 迁移）

```sql
-- migrate_dcep01_wallet_pin.sql
ALTER TABLE biz_dcep_hardware_wallet
    ADD COLUMN pin_hash VARCHAR(255) DEFAULT NULL COMMENT 'PIN BCrypt 哈希',
    ADD COLUMN private_key_enc VARCHAR(2048) DEFAULT NULL COMMENT '私钥(AES加密)',
    ADD COLUMN daily_used DECIMAL(15,2) DEFAULT 0 COMMENT '当日累计消费',
    ADD COLUMN daily_reset_at DATETIME DEFAULT NULL COMMENT '当日限额重置时间';
```

##### 不在本次实现内的项（待后续迭代）

- **真实运营商 SDK 接入**（当前为 `SimulatedNfcReader`，生产需替换）
- **KMS 集成**（当前私钥 AES 演示，生产需接 KMS）
- **NFC AID 路由策略**（当前硬编码 DCEP AID，多钱包场景需按场景选 AID）
- **生物识别真实硬件调用**（当前仅 boolean 开关，无指纹/3D 脸真实采集）

#### DCEP-05 子钱包独立账本模型（2026-09-04 完整实现）

##### 核心概念

| 概念 | 说明 |
|------|------|
| 主钱包（Parent） | 硬件钱包或软钱包，持有资金 |
| 子钱包（SubWallet） | 主钱包下的独立账户单元，有独立 balance / totalIn / totalOut / frozenBalance |
| 账本流水（Ledger） | 每笔资金变动的不可篡改记录（复式记账） |
| 资金归集（Sweep） | 自动将子钱包超额部分转回目标账户 |
| 分账结算（Settlement） | 按 daily/weekly/monthly 模式向品牌方结算 |

##### 子钱包类型

| wallet_type | 业务场景 |
|-------------|----------|
| `store_fund` | 门店独立资金账户（多门店资金归集） |
| `product_fund` | 产品购买账户 |
| `project_fund` | 项目服务账户 |
| `brand_share` | 品牌方分账账户 |
| `settlement` | 结算账户 |

##### API 端点

| Method | Path | 功能 |
|--------|------|------|
| GET | `/dcep/sub-wallet/list` | 查询子钱包列表（支持 tenant/store/parent/type/status 筛选） |
| GET | `/dcep/sub-wallet/{id}` | 按 ID 查询 |
| GET | `/dcep/sub-wallet/by-code/{code}` | 按编号查询 |
| GET | `/dcep/sub-wallet/{id}/stats` | 余额统计 + 本月累计转入/转出 |
| POST | `/dcep/sub-wallet/create` | 创建子钱包 |
| PUT | `/dcep/sub-wallet/{id}/config` | 更新配置（限额/手续费/自动归集） |
| POST | `/dcep/sub-wallet/{id}/freeze` | 冻结 |
| POST | `/dcep/sub-wallet/{id}/unfreeze` | 解冻 |
| POST | `/dcep/sub-wallet/{id}/close` | 关闭（余额必须为 0） |
| POST | `/dcep/sub-wallet/transfer` | 账间转账（复式记账，双方同时记账） |
| GET | `/dcep/sub-wallet/{id}/ledger` | 查询账本流水 |
| POST | `/dcep/sub-wallet/sweep/{parentWalletId}` | 执行自动归集（定时任务） |

##### 账间转账说明

```
A 子钱包 → B 子钱包（amount = 200，fee = 5）

A 账本流水：transfer_out，amount=200，balance_before=1000，balance_after=795
B 账本流水：transfer_in， amount=195，balance_before=500， balance_after=695

A 实际扣减 = amount + fee = 205
B 实际转入 = amount - fee = 195
```

##### 自动归集（auto_sweep）

- 条件：`autoSweepEnabled=1` 且 `balance - frozenBalance >= sweepThreshold`
- 归集额：`balance - frozenBalance - sweepThreshold`（保留 minBalance）
- 目标：由 `sweepTargetId` 指定

##### 数据库表

| 表 | 说明 |
|----|------|
| `biz_dcep_sub_wallet` | 子钱包主表（含独立 balance/totalIn/totalOut/frozenBalance） |
| `biz_dcep_sub_wallet_ledger` | 账本流水（复式记账，不可篡改） |

#### PAY-006/007/008/009/010/011/012 海外支付通道实装（2026-09-04 完整实现）

##### 海外支付网关（Stripe / Square / PayPal）

| 通道 | 网关编码 | 费率 | 支持币种 |
|------|----------|------|----------|
| **Stripe** | `stripe` | 2.9% + $0.30 | USD/EUR/GBP/CAD/AUD/JPY/SGD/HKD/CNY/MYR/THB/PHP 等 24 种 |
| **Square** | `square` | 2.6% + $0.10 | USD/CAD/GBP/JPY/AUD |
| **PayPal** | `paypal` | 3.49% + $0.49 | USD/EUR/GBP/JPY/CAD/AUD |

##### 适配器实现（OverseasPaymentAdapter）

| Adapter | 网关 | 关键能力 |
|---------|------|----------|
| `StripeAdapter` | stripe | PaymentIntent 创建、HMAC-SHA256 webhook 签名验证 |
| `SquareAdapter` | square | Payment 创建、webhook 解析 |
| `PaypalAdapter` | paypal | Order 创建 + approval URL、webhook 解析 |

所有 Adapter 实现 `OverseasPaymentAdapter` 接口，由 Spring 自动注入到 `Map<String, OverseasPaymentAdapter>`：
- `createPaymentIntent()` — 创建支付意图
- `confirmPayment()` — 确认支付
- `refund()` — 退款
- `queryPayment()` — 查询支付状态
- `verifyWebhookSignature()` — 验证 webhook 签名（Stripe 用真实 HMAC-SHA256）
- `parseWebhookEvent()` — 解析 webhook 事件
- `healthCheck()` — 网关健康检查

##### 智能网关选择

`OverseasPaymentService.selectBestGateway(currency, countryCode, paymentMethod, amount, tenantId)`：
- 优先匹配 Stripe 支持的 24 种货币
- 否则遍历已注册 Adapter，选取第一个 enabled 的网关
- 返回 `null` 表示无可用网关

##### API 端点

| Method | Path | 功能 |
|--------|------|------|
| GET | `/overseas-payment/payment/list` | 海外支付列表（支持 member/order/gateway/status/时间范围筛选） |
| GET | `/overseas-payment/payment/{id}` | 按 ID 查询 |
| POST | `/overseas-payment/payment/create` | 创建支付（智能选网关） |
| POST | `/overseas-payment/payment/{id}/refund` | 退款 |
| GET | `/overseas-payment/payment/statistics` | 30 天统计（按状态+按网关分组） |
| GET | `/overseas-payment/payment/select-gateway` | 智能网关选择 |
| GET | `/overseas-payment/gateway/list` | 网关列表 |
| POST | `/overseas-payment/gateway` | 新增网关 |
| PUT | `/overseas-payment/gateway/{id}` | 更新网关 |
| DELETE | `/overseas-payment/gateway/{id}` | 删除网关 |
| POST | `/overseas-payment/gateway/{id}/health-check` | 网关健康检查 |
| GET | `/overseas-payment/route/list` | 路由规则列表 |
| GET | `/overseas-payment/route/match` | 路由匹配（按 currency/country/paymentMethod） |
| POST | `/overseas-payment/route` | 新增路由 |
| PUT | `/overseas-payment/route/{id}` | 更新路由 |
| DELETE | `/overseas-payment/route/{id}` | 删除路由 |
| POST | `/overseas-payment/webhook/{gatewayCode}` | 接收 webhook（自动验签+记录日志） |
| GET | `/overseas-payment/webhook/logs` | webhook 日志 |

##### BNPL（先买后付）

| 平台 | 期数 | 费率 |
|------|------|------|
| `atome` | 3 | 1.5% |
| `klarna` | 3/6/12 | 2.9% |
| `afterpay` | 4 | 2.0% |
| `affirm` | 3/6/12 | 5.0% |

| Method | Path | 功能 |
|--------|------|------|
| GET | `/bnpl/credit` | 查询授信（provider + phone） |
| POST | `/bnpl/order` | 创建分期订单 |
| GET | `/bnpl/orders` | 订单列表（分页） |
| POST | `/bnpl/order/{id}/cancel` | 取消订单 |
| GET | `/bnpl/statistics` | 统计 |
| POST | `/bnpl/notify/simulate` | 模拟异步通知 |

##### 礼品卡（Gift Card）

`GiftCardService`（Controller 形态，自身暴露 `/gift-card/*`）：

| Method | Path | 功能 |
|--------|------|------|
| POST | `/gift-card/purchase` | 购卡（生成卡号） |
| POST | `/gift-card/{id}/gift` | 转赠（修改接收人） |
| POST | `/gift-card/redeem` | 核销（消费扣减） |
| POST | `/gift-card/{id}/refund` | 退款 |
| GET | `/gift-card/list` | 列表 |
| GET | `/gift-card/by-card-no/{cardNo}` | 按卡号 |
| GET | `/gift-card/{id}/transactions` | 流水 |

##### 国际支付演示（5 网关）

`IntlPaymentService`（Controller 形态，内存存储）：
- Stripe / Square / PayPal / Afterpay（BNPL）/ Klarna（BNPL）
- 5% 概率返回 DECLINED 用于演示失败流程
- 提供 charge / refund / list 接口

##### Webhook 验签

`PaymentWebhookController` 自动识别签名 header：
- `Stripe-Signature`（HMAC-SHA256 验签）
- `Square-Signature`
- `PAYPAL-TRANSMISSION-SIG`

接收后立即写入 `biz_payment_webhook_log` 表（process_status: pending/processed/failed），便于追溯。

##### 数据库表

| 表 | 说明 |
|----|------|
| `biz_overseas_payment_gateway` | 网关配置（publishable_key/secret_key/webhook_secret） |
| `biz_overseas_payment` | 海外支付交易（含 fee/net_amount/refund_amount） |
| `biz_payment_webhook_log` | Webhook 接收日志（含 verify_status/process_status） |
| `biz_payment_route_rule` | 路由规则（currency/country/paymentMethod 三维匹配） |
| `biz_bnpl_order` | BNPL 分期订单（含 repayment_schedule JSON） |
| `gift_card` | 礼品卡（含 service_count/余额/有效期） |

### 4.28 FISCAL — 财税

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| FISCAL-001 | 财税直连 | `views/fiscal/index.vue` | GET/POST `/fiscal/*` | `FiscalController` | `fiscal_invoice` | - | DISCOVERED |
| FISCAL-002 | 发票管理 | `views/fiscal/index.vue` | GET/POST `/fiscal/invoice/*` | `FiscalController.invoice()` | `fiscal_invoice` | - | PARTIAL |
| FISCAL-003 | 对账管理 | `views/pos/reconciliation.vue` | GET/POST `/reconciliation/*` | `ReconciliationService` | `reconciliation` | - | DISCOVERED |
| FISCAL-004 | 充值审计 | `views/report/export.vue` | GET `/recharge/audit/*` | `RechargeAuditController` | `recharge_audit` | - | PARTIAL |

### 4.29 SUBS — 订阅会员

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| SUBS-001 | 订阅会员 | `views/subscription/index.vue` | GET/POST `/subscription/*` | `SubscriptionController` | `subscription` | - | DISCOVERED |
| SUBS-002 | 会员日自动化 | `views/member-day/index.vue` | GET/POST `/member-day/*` | `MemberDayService` | `member_day` | - | DISCOVERED |
| SUBS-003 | 生命周期看板 | `views/lifecycle/index.vue` | GET `/lifecycle/*` | `LifecycleController` | `lifecycle_event` | - | DISCOVERED |
| SUBS-004 | **动态定价 A/B 测试（M14）** | `views/mkt/pricing.vue` | GET/POST `/mkt/pricing/experiment/*` | `PricingExperimentController` | `pricing_experiment` + `pricing_experiment_result` | - | ✅ **M14 完整实现** |

### 4.30 GIFT — 礼品卡

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| GIFT-001 | 礼品卡 | `views/gift-card/index.vue` | GET/POST `/gift-card/*` | `GiftCardController` | `gift_card` | - | DISCOVERED |
| GIFT-002 | 礼品卡交易 | `views/gift-card/index.vue` | GET `/gift-card/transaction/*` | `GiftCardController.transaction()` | `gift_card_transaction` | - | PARTIAL |

---

### 4.31 RESOURCE — 资源利用率（M11-04 技师利用率 + 房间占用率）

| Feature ID | Business Feature | Frontend | API | Backend | DB | Test | Status |
|------------|-----------------|----------|-----|---------|-----|------|--------|
| RES-001 | 资源利用率列表 | `views/resource/resource-schedule.vue` | GET `/resource/utilization/list` | `ResourceUtilizationController` *(原 .disabled)* | `biz_resource_utilization` | - | PARTIAL |
| RES-002 | TOP 利用率排行 | - | GET `/resource/utilization/top` | `ResourceUtilizationController` | `biz_resource_utilization` | - | PARTIAL |
| RES-003 | 重算某日利用率 | - | POST `/resource/utilization/recalculate` | `ResourceUtilizationController` | `biz_resource_utilization` | - | PARTIAL |

#### 业务口径（2026-09-04 确认版本）

##### 1. 技师利用率（严格口径）

```
技师利用率 = 实际服务时长 / 班表工作时长 × 100%
```

| 项 | 取值口径 |
|----|----------|
| 分子 · 实际服务时长 | `biz_booking.status = 4（已完成）` 的 `end_time - start_time` 累加（分钟） |
| 分子 · 多技师协作 | 同一时间段有多人参与时，按人头均摊：`分钟数 / 参与技师数`（基于 `biz_booking` 同 start/end 时间聚合） |
| 分母 · 班表工作时长 | 读 `biz_employee_schedule.startTime / endTime`，**扣除** `status=2（请假）` 和 `status=3（休息）` |
| 分母 · 班表无精确时间 | 按 `shiftType` 兜底：早/中/全天 = 8h，晚班 = 6h |
| 爽约/取消 | 计入 `noShowCount（status=6）` 和 `cancelCount（status=5）`，**不计入**服务时长 |
| 收入（revenue） | 关联 `pos_order.status=2（已支付）` 的 `final_amount` 累加，按完成 booking 分配 |
| 目标值 | > 75%（PRD 5.3 运营指标） |

##### 2. 房间占用率

```
房间占用率 = 已完成服务时长 / 营业时长 × 100%
```

| 项 | 取值口径 |
|----|----------|
| 分子 · 已完成服务时长 | 房间绑定 booking 中 `status=4` 的 `end_time - start_time` 累加 |
| 分母 · 营业时长 | 默认 8h × 60 = 480 分钟（房间维度目前未接营业时间配置，先固定 8h） |
| 预约时长（booked） | 含所有状态（已签到/已完成/已取消/爽约）的预约时长 |
| 收入（revenue） | 关联 `pos_order.status=2` 的 `final_amount` |

##### 3. 重算逻辑（`ResourceUtilizationServiceImpl.recalculate`）

```sql
-- 主流程：
-- 1. DELETE 当日该门店记录（避免脏数据）
DELETE FROM biz_resource_utilization
WHERE deleted = 0 AND store_id = #{storeId} AND stat_date = #{statDate};

-- 2. 循环 pos_employee.status=1 的所有技师：
--    - 读 biz_employee_schedule 取当日班表
--    - status=2/3 的技师写 0 占位记录
--    - 正常班表：分母 = (endTime - startTime) 分钟
--    - 聚合 biz_booking（status IN 3,4,5,6）按 employeeId 分组
--    - 仅 status=4 计入分子（双人协作按 countTechsForBooking 均摊）
--    - 关联 pos_order 取已完成订单的 final_amount

-- 3. 循环 sys_room 的所有房间：
--    - 聚合 biz_booking 按 roomId 分组
--    - 同上口径计算 bookedMinutes / actualServiceMinutes / revenue
--    - 分母固定 480 分钟
```

##### 4. 不在本次口径内的字段（保留待后续讨论）

- **房间营业时间**：当前写死 8h，未接门店营业时间配置
- **技师全勤奖励**：未做（PRD 5.3 仅列了"技师利用率 > 75%"，未给口径）
- **服务中的技师 (status=3 已签到)**：严格口径未计入，等下次迭代讨论"广义口径"再考虑
- **跨日班次**：晚班跨午夜（如 22:00 - 02:00）当前直接算分钟差，未做日期拆分

---

## 5. Domain → Feature Tree

```
PROJECT
│
├── Authentication (AUTH)
│   ├── AUTH-001 用户登录
│   ├── AUTH-002 用户退出
│   ├── AUTH-003 获取用户信息
│   ├── AUTH-004 登录日志查询
│   ├── AUTH-005 Token 刷新
│   ├── AUTH-006 登录状态恢复
│   ├── AUTH-007 权限加载
│   └── AUTH-008 菜单加载
│
├── Organization (ORG)
│   ├── ORG-001 租户列表
│   ├── ORG-002 创建租户
│   ├── ORG-003 编辑租户
│   ├── ORG-004 租户配置
│   └── ORG-005 数据隔离
│
├── Store (STORE)
│   ├── STORE-001 门店列表
│   ├── STORE-002 门店分页查询
│   ├── STORE-003 门店详情
│   ├── STORE-004 创建门店
│   └── STORE-005 编辑门店
│
├── Employee (EMP)
│   ├── EMP-001 员工列表
│   ├── EMP-002 员工分页查询
│   ├── EMP-003 员工详情
│   ├── EMP-004 创建员工
│   ├── EMP-005 编辑员工
│   ├── EMP-006 删除员工
│   ├── EMP-007 重置密码
│   ├── EMP-008 员工排班
│   ├── EMP-009 员工统计
│   └── EMP-010 技师等级
│
├── RBAC (RBAC)
│   ├── RBAC-001 角色列表
│   ├── RBAC-002 创建角色
│   ├── RBAC-003 编辑角色
│   ├── RBAC-004 删除角色
│   ├── RBAC-005 权限分配
│   └── RBAC-006 操作日志
│
├── Member (MEMBER)
│   ├── MEMBER-001 会员列表
│   ├── MEMBER-002 会员详情
│   ├── MEMBER-003 创建会员
│   ├── MEMBER-004 编辑会员
│   ├── MEMBER-005 删除会员
│   ├── MEMBER-006 会员冻结
│   ├── MEMBER-007 会员解冻
│   ├── MEMBER-008 会员标签
│   ├── MEMBER-009 批量打标签
│   ├── MEMBER-010 会员导入
│   ├── MEMBER-011 会员导出
│   ├── MEMBER-012 会员搜索
│   ├── MEMBER-013 会员统计
│   └── MEMBER-014 会员资料编辑
│
├── Member Level (LEVEL)
│   ├── LEVEL-001 等级列表
│   ├── LEVEL-002 等级详情
│   ├── LEVEL-003 创建等级
│   ├── LEVEL-004 编辑等级
│   ├── LEVEL-005 删除等级
│   └── LEVEL-006 等级权益配置
│
├── Wallet (WALLET)
│   ├── WALLET-001 余额查询
│   ├── WALLET-002 储值充值
│   ├── WALLET-003 储值消费
│   ├── WALLET-004 储值退款
│   ├── WALLET-005 流水查询
│   ├── WALLET-006 全部流水
│   ├── WALLET-007 充值优惠规则
│   └── WALLET-008 优惠计算
│
├── Booking (BOOKING)
│   ├── BOOKING-001 预约日历
│   ├── BOOKING-002 预约列表
│   ├── BOOKING-003 创建预约
│   ├── BOOKING-004 编辑预约
│   ├── BOOKING-005 取消预约
│   ├── BOOKING-006 预约签到
│   ├── BOOKING-007 预约完成
│   ├── BOOKING-008 预约状态变更
│   ├── BOOKING-009 可用时间查询
│   ├── BOOKING-010 排班管理
│   ├── BOOKING-011 房/床位管理
│   └── BOOKING-012 候补队列
│
├── Service (SERVICE)
│   ├── SERVICE-001 服务列表
│   ├── SERVICE-002 服务详情
│   ├── SERVICE-003 创建服务
│   ├── SERVICE-004 编辑服务
│   ├── SERVICE-005 删除服务
│   └── SERVICE-006 服务分类
│
├── Package (PACKAGE)
│   ├── PACKAGE-001 套餐列表
│   ├── PACKAGE-002 套餐详情
│   ├── PACKAGE-003 创建套餐
│   ├── PACKAGE-004 编辑套餐
│   ├── PACKAGE-005 删除套餐
│   ├── PACKAGE-006 购买套餐
│   ├── PACKAGE-007 会员套餐查询
│   ├── PACKAGE-008 套餐核销
│   ├── PACKAGE-009 多人共享
│   └── PACKAGE-010 套餐次卡统计
│
├── POS (POS)
│   ├── POS-001 收银台
│   ├── POS-002 创建订单
│   ├── POS-003 订单详情
│   ├── POS-004 订单列表
│   ├── POS-005 今日订单
│   ├── POS-006 订单支付
│   ├── POS-007 订单退款
│   ├── POS-008 退款审核
│   ├── POS-009 订单统计
│   ├── POS-010 支付对账
│   ├── POS-011 订单作废
│   └── POS-012 订单明细项
│
├── Payment (PAY)
│   ├── PAY-001 支付配置
│   ├── PAY-002 数字人民币
│   ├── PAY-003 硬件钱包
│   ├── PAY-004 离线交易
│   ├── PAY-005 支付宝/微信
│   ├── PAY-006 海外支付
│   ├── PAY-007 BNPL/花呗
│   ├── PAY-008 支付回调
│   ├── PAY-009 订单支付
│   └── PAY-010 支付路由
│
├── Commission (COMM)
│   ├── COMM-001 提成规则列表
│   ├── COMM-002 创建提成规则
│   ├── COMM-003 编辑提成规则
│   ├── COMM-004 删除提成规则
│   ├── COMM-005 提成计算
│   ├── COMM-006 提成记录查询
│   ├── COMM-007 提成模拟计算
│   ├── COMM-008 工资生成
│   ├── COMM-009 工资报表
│   ├── COMM-010 结算管理
│   ├── COMM-011 提成批次
│   └── COMM-012 提成冲正
│
├── Stock (STOCK)
│   ├── STOCK-001 商品列表
│   ├── STOCK-002 商品详情
│   ├── STOCK-003 创建商品
│   ├── STOCK-004 编辑商品
│   ├── STOCK-005 库存查询
│   ├── STOCK-006 入库
│   ├── STOCK-007 出库
│   ├── STOCK-008 盘点管理
│   ├── STOCK-009 库存预警
│   ├── STOCK-010 消耗记录
│   ├── STOCK-011 消耗追溯
│   ├── STOCK-012 库存统计
│   ├── STOCK-013 效期追踪
│   └── STOCK-014 耗材绑定
│
├── Coupon (COUPON)
│   ├── COUPON-001 优惠券列表
│   ├── COUPON-002 优惠券详情
│   ├── COUPON-003 创建优惠券
│   ├── COUPON-004 编辑优惠券
│   ├── COUPON-005 删除优惠券
│   ├── COUPON-006 发放优惠券
│   ├── COUPON-007 核销优惠券
│   └── COUPON-008 会员优惠券查询
│
├── Points (POINTS)
│   ├── POINTS-001 积分查询
│   ├── POINTS-002 积分记录
│   ├── POINTS-003 积分增加
│   ├── POINTS-004 积分消费
│   ├── POINTS-005 积分规则
│   └── POINTS-006 积分商城
│
├── Marketing (MKT)
│   ├── MKT-001 活动列表
│   ├── MKT-002 活动详情
│   ├── MKT-003 创建活动
│   ├── MKT-004 编辑活动
│   ├── MKT-005 删除活动
│   ├── MKT-006 活动统计
│   ├── MKT-007 渠道配置
│   ├── MKT-008 消息模板
│   ├── MKT-009 触达任务
│   └── MKT-010 营销旅程
│
├── CRM (CRM)
│   ├── CRM-001 回访任务
│   ├── CRM-002 回访计划
│   ├── CRM-003 自动任务规则
│   ├── CRM-004 投诉工单
│   ├── CRM-005 客户评价
│   ├── CRM-006 评价模板
│   ├── CRM-007 跨店规则
│   ├── CRM-008 跨店核销
│   ├── CRM-009 客户运营看板
│   ├── CRM-010 RFM 分群
│   ├── CRM-011 LTV 预测
│   └── CRM-012 Cohort 队列
│
├── Care (CARE)
│   ├── CARE-001 配方列表
│   ├── CARE-002 配方详情
│   ├── CARE-003 创建配方
│   ├── CARE-004 编辑配方
│   ├── CARE-005 护理建议生成
│   ├── CARE-006 建议规则
│   ├── CARE-007 建议日志
│   └── CARE-008 配方模板
│
├── Detection (DETECT)
│   ├── DETECT-001 肤质档案
│   ├── DETECT-002 皮肤检测
│   ├── DETECT-003 毛发检测
│   ├── DETECT-004 美甲检测
│   ├── DETECT-005 诊断报告
│   ├── DETECT-006 解决方案
│   ├── DETECT-007 AI 增强
│   └── DETECT-008 视觉档案
│
├── Attendance (ATTEND)
│   ├── ATTEND-001 考勤打卡
│   ├── ATTEND-002 考勤记录
│   ├── ATTEND-003 请假管理
│   ├── ATTEND-004 考勤统计
│   ├── ATTEND-005 考勤规则配置
│   ├── ATTEND-006 员工日程
│   ├── ATTEND-007 排班管理
│   └── ATTEND-008 打卡配置
│
├── Report (REPORT)
│   ├── REPORT-001 数据看板
│   ├── REPORT-002 营业报表
│   ├── REPORT-003 会员报表
│   ├── REPORT-004 员工报表
│   ├── REPORT-005 导出任务
│   ├── REPORT-006 指标口径
│   ├── REPORT-007 导出管理
│   ├── REPORT-008 数据中台
│   ├── REPORT-009 性能监控
│   └── REPORT-010 链路追踪
│
├── IoT (IOT)
│   ├── IOT-001 设备列表
│   ├── IOT-002 设备详情
│   ├── IOT-003 设备数据
│   ├── IOT-004 触发规则
│   ├── IOT-005 设备告警
│   └── IOT-006 设备联动
│
├── AI (AI)
│   ├── AI-001 AI 智能排班
│   ├── AI-002 客流预测
│   ├── AI-003 AI 视觉识别
│   ├── AI-004 LTV 流失预测
│   ├── AI-005 流失训练
│   ├── AI-006 灰度发布
│   ├── AI-007 A/B 测试
│   └── AI-008 AI 增强服务
│
├── Compliance (COMP)
│   ├── COMP-001 知情同意
│   ├── COMP-002 H5 知情同意
│   ├── COMP-003 并发症 SOP
│   ├── COMP-004 GDPR 合规
│   ├── COMP-005 HIPAA 加固
│   └── COMP-006 商户资质
│
├── Blockchain (BC)
│   ├── BC-001 区块链溯源
│   ├── BC-002 区块链监控
│   ├── BC-003 智能合约
│   └── BC-004 离线交易
│
├── Fiscal (FISCAL)
│   ├── FISCAL-001 财税直连
│   ├── FISCAL-002 发票管理
│   ├── FISCAL-003 对账管理
│   └── FISCAL-004 充值审计
│
├── Subscription (SUBS)
│   ├── SUBS-001 订阅会员
│   ├── SUBS-002 会员日自动化
│   ├── SUBS-003 生命周期看板
│   └── SUBS-004 动态定价
│
└── Gift Card (GIFT)
    ├── GIFT-001 礼品卡
    └── GIFT-002 礼品卡交易
```

---

## 6. Feature Statistics

### 6.1 状态分布

| Status | Count | Percentage |
|--------|-------|------------|
| DISCOVERED | 152 | 80.4% |
| PARTIAL | 21 | 11.1% |
| BROKEN | 8 | 4.2% |
| MOCK | 3 | 1.6% |
| MISSING | 2 | 1.1% |
| UNVERIFIED | 3 | 1.6% |

### 6.2 层覆盖率

| Layer | Covered Features | Coverage |
|-------|------------------|----------|
| Frontend | 156 | 82.5% |
| API | 163 | 86.2% |
| Backend | 172 | 91.0% |
| Database | 165 | 87.3% |

### 6.3 Domain 分布

| Priority | Feature Count |
|----------|---------------|
| P0 Core | 32 |
| P1 Business Core | 58 |
| P2 Supporting | 67 |
| P3 Auxiliary | 32 |

---

## 7. Orphan / Conflict / Duplicate

### 7.1 Frontend Orphan（前端孤儿）

| View | 状态 | 说明 |
|------|------|------|
| `views/dev/gap-suite.vue` | MISSING/duplicated | 与 `GapSuite.vue` 大小写重复，未路由 |
| `views/dev/p1-suite.vue` | MISSING | 同名大写版本已路由，小写版无引用 |
| `views/dev/p2-suite.vue` | MISSING | 同名大写版本已路由，小写版无引用 |
| `views/dev/p3-suite.vue` | MISSING | 同名大写版本已路由，小写版无引用 |
| `views/package/package-form.vue` | PARTIAL | Dialog 组件，未挂路由 |
| `views/package/purchase-dialog.vue` | PARTIAL | Dialog 组件，未挂路由 |
| `views/booking/booking-form.vue` | PARTIAL | Dialog 组件，引用 `posApi` |
| `views/intel/rfm/*Tab.vue` (9个) | PARTIAL | `intel/rfm/index.vue` 子组件 |

### 7.2 Backend Orphan（后端孤儿）

| Controller/Service | 说明 |
|---------------------|------|
| 多个 Service 直接用 `@RestController` | "Service 即 Controller" 架构 |
| `P2ComplianceSuiteService` | 可能有重复实现 |

### 7.3 Duplicate Features（重复功能）

| 路径 | 说明 |
|------|------|
| `/booking/list` vs `/booking/page` | 同一功能两种实现 |
| `/stock/products/list` vs `/stock/products/page` | 同一功能两种实现 |
| `/commission/rules/list` vs `/commission/rules/page` | 同一功能两种实现 |
| `material-binding.ts` vs `basic.ts` | `/material/group`、`/material/substitute` 重复声明 |

### 7.4 Contract Conflicts（契约冲突）

| 问题 | 说明 |
|------|------|
| Permission 名称不一致 | Frontend: `biz:service-item`, Backend: 可能不同 |
| API 版本混用 | `/report/v2/biz/daily` 存在但 Frontend 默认走 v1 |

---

## 8. BROKEN Features（断链）

| Feature ID | Business Feature | 断链说明 |
|------------|-----------------|----------|
| BOOKING-012 | 候补队列 | 前端有页面，后端 Controller 可能有但需验证 |
| PAY-004 | 离线交易 | DcepOfflineTransactionController 存在但完整链路待验证 |
| PAY-010 | 支付路由 | PaymentRouteRuleController 存在但完整链路待验证 |
| COMM-011 | 提成批次 | CommissionBatchController 存在但完整链路待验证 |
| COMM-012 | 提成冲正 | CommissionReversalController 存在但完整链路待验证 |
| POINTS-005 | 积分规则 | MarketPointRuleController 存在但完整链路待验证 |
| STOCK-014 | 耗材绑定 | ServiceMaterialController 存在但完整链路待验证 |
| REPORT-006 | 指标口径 | ReportCaliberController 存在但完整链路待验证 |

---

## 9. MOCK Features（模拟）

| Feature ID | Business Feature | Mock 说明 |
|------------|-----------------|----------|
| STYLE-GALLERY-001 | 美甲款式图库 | `style-gallery.ts` 调用 `/style/*`，但后端无对应实现 |
| AI-005 | 流失训练 | `ChurnTrainingService` 可能为空壳 |
| AI-008 | AI 增强服务 | `AiEnhanceService` 可能为空壳 |

---

## 10. Code Component → Feature Mapping

| Code Component | Type | Business Feature |
|----------------|------|------------------|
| `views/login.vue` | Frontend | AUTH-001 用户登录 |
| `views/member/index.vue` | Frontend | MEMBER-* 会员管理 |
| `views/booking/calendar.vue` | Frontend | BOOKING-001 预约日历 |
| `views/booking/list.vue` | Frontend | BOOKING-002 预约列表 |
| `views/pos/checkout.vue` | Frontend | POS-001 收银台 |
| `views/pos/orders.vue` | Frontend | POS-004 订单列表 |
| `views/stock/product.vue` | Frontend | STOCK-001 商品列表 |
| `views/commission/rules.vue` | Frontend | COMM-001 提成规则列表 |
| `AuthController.java` | Backend | AUTH-001 用户登录 |
| `MemberController.java` | Backend | MEMBER-* 会员管理 |
| `BookingController.java` | Backend | BOOKING-* 预约管理 |
| `PosController.java` | Backend | POS-* 收银订单 |
| `StockController.java` | Backend | STOCK-* 库存管理 |
| `CommissionController.java` | Backend | COMM-* 员工提成 |
| `member` | Database | MEMBER-* 会员管理 |
| `booking` | Database | BOOKING-* 预约管理 |
| `pos_order` | Database | POS-* 收银订单 |
| `commission_rule` | Database | COMM-* 员工提成 |
| `api/basic.ts` | Frontend API | STORE, EMP, MEMBER, WALLET, CATEGORY |
| `api/booking.ts` | Frontend API | BOOKING, SCHEDULE, ROOM |
| `api/pos.ts` | Frontend API | POS, REFUND, RECONCILIATION |
| `api/stock.ts` | Frontend API | STOCK, CONSUMPTION, CHECK |
| `api/commission.ts` | Frontend API | COMM, SALARY, SETTLEMENT |

---

## 11. Feature Reality Check Queue

### Phase 2 执行顺序（按优先级）

#### P0 Core Features（第一批验证）

```
1.  AUTH-001 用户登录
2.  AUTH-002 用户退出
3.  AUTH-007 权限加载
4.  AUTH-008 菜单加载
5.  ORG-001 租户列表
6.  ORG-005 数据隔离
7.  STORE-001 门店列表
8.  EMP-001 员工列表
9.  RBAC-001 角色列表
10. RBAC-005 权限分配
11. MEMBER-001 会员列表
12. MEMBER-003 创建会员
13. MEMBER-004 编辑会员
14. MEMBER-006 会员冻结
15. POS-001 收银台
16. POS-002 创建订单
17. POS-006 订单支付
18. POS-007 订单退款
19. PAY-001 支付配置
20. PAY-008 支付回调
21. WALLET-001 余额查询
22. WALLET-002 储值充值
23. WALLET-003 储值消费
24. BOOKING-001 预约日历
25. BOOKING-003 创建预约
26. BOOKING-006 预约签到
27. SERVICE-001 服务列表
28. PACKAGE-001 套餐列表
29. PACKAGE-006 购买套餐
30. COMM-005 提成计算
31. REPORT-001 数据看板
32. REPORT-002 营业报表
```

#### P1 Business Core Features（第二批验证）

```
33. EMP-004 创建员工
34. EMP-008 员工排班
35. MEMBER-008 会员标签
36. MEMBER-010 会员导入
37. LEVEL-001 等级列表
38. BOOKING-002 预约列表
39. BOOKING-010 排班管理
40. SERVICE-003 创建服务
41. PACKAGE-003 创建套餐
42. PACKAGE-008 套餐核销
43. POS-003 订单详情
44. POS-004 订单列表
45. POS-010 支付对账
46. PAY-002 数字人民币
47. PAY-006 海外支付
48. WALLET-004 储值退款
49. COMM-001 提成规则列表
50. COMM-006 提成记录查询
51. COMM-010 结算管理
52. STOCK-001 商品列表
53. STOCK-005 库存查询
54. STOCK-006 入库
55. STOCK-007 出库
56. STOCK-008 盘点管理
57. COUPON-001 优惠券列表
58. COUPON-006 发放优惠券
```

#### P2 Supporting Features（第三批验证）

```
59. MEMBER-007 会员解冻
60. MEMBER-012 会员搜索
61. BOOKING-004 编辑预约
62. BOOKING-005 取消预约
63. BOOKING-011 房/床位管理
64. SERVICE-004 编辑服务
65. PACKAGE-004 编辑套餐
66. POS-009 订单统计
67. PAY-003 硬件钱包
68. PAY-007 BNPL/花呗
69. COMM-002 创建提成规则
70. COMM-007 提成模拟计算
71. COMM-008 工资生成
72. STOCK-002 商品详情
73. STOCK-003 创建商品
74. STOCK-009 库存预警
75. STOCK-010 消耗记录
76. COUPON-002 优惠券详情
77. POINTS-001 积分查询
78. POINTS-003 积分增加
79. MKT-001 活动列表
80. MKT-003 创建活动
81. CRM-001 回访任务
82. CRM-005 客户评价
83. CARE-001 配方列表
84. CARE-005 护理建议生成
85. DETECT-001 肤质档案
86. ATTEND-001 考勤打卡
87. ATTEND-002 考勤记录
```

#### P3 Auxiliary Features（第四批验证）

```
88. AI-001 AI 智能排班
89. AI-002 客流预测
90. AI-004 LTV 流失预测
91. AI-006 灰度发布
92. IOT-001 设备列表
93. IOT-004 触发规则
94. COMP-001 知情同意
95. COMP-003 并发症 SOP
96. BC-001 区块链溯源
97. FISCAL-001 财税直连
98. SUBS-001 订阅会员
99. GIFT-001 礼品卡
... (剩余 Feature)
```

---

## 11.5 P2 验证补丁记录 (2026-08-27)

P2 链路核对发现并已修复的问题：

| 补丁文件 | 修复内容 | 影响功能 |
|----------|----------|----------|
| `backend/src/main/resources/db/sql/init_care_recipe.sql` | 新增 6 张表：`care_recipe`, `care_recipe_item`, `care_skin_assessment`, `care_formula`, `care_formula_apply`, `care_plan` | CARE-001 / CARE-005 |
| `backend/src/main/resources/db/sql/init_bnpl.sql` | 新增 `biz_bnpl_order` | PAY-007 |
| `backend/src/main/resources/db/sql/init_mkt_promotion.sql` | 新增 `mkt_promotion`（与已有 `market_activity` 并存，字段模型不同） | MKT-001 / MKT-003 |
| `docs/BUSINESS-FEATURE-INVENTORY.md` line 396 | ATTEND-001 路径订正：`/attendance/checkin` → `/attendance/check-in` + `/attendance/check-out`（与前端 `attendanceApi.checkIn/checkOut` 一致） | ATTEND-001 |
| `BUSINESS-FEATURE-INVENTORY.md` P2 报告 | PACKAGE-004 实为 `POST /packages`（前端 `packageApi.update = POST`），后端 `save()` 已按 id 自动分支创建/更新，链路完整；原"PUT 端点缺失"判定撤销 | PACKAGE-004 |

### 11.5.2 P3 验证补丁记录（2026-08-27）

| 补丁文件 | 修复内容 | 影响功能 |
|----------|----------|----------|
| `backend/src/main/resources/db/sql/init_ai_scheduler.sql` | 新增 `ai_schedule_plan` 表 | AI-001 |
| `backend/src/main/resources/db/sql/init_ai_forecast.sql` | 新增 `booking_forecast_feature` + `booking_forecast_result` 表 | AI-002 |
| `backend/src/main/resources/db/sql/init_complication_sop.sql` | 新增 `complication_sop` + `complication_event` 表 | COMP-003 |
| `backend/src/main/resources/db/sql/init_subscription_missing.sql` | 从 `backend/sql/` 迁移 `subscription_plan` + `subscription_billing` + `member_day_execution` 进主脚本集 | SUBS-001 |
| `backend/src/main/java/com/meiyetec/service/*.java` | 8 个 Service-Name-But-Controller 重命名为 `XxxController`：AiSchedulerService → AiSchedulerController、BookingForecastService → BookingForecastController、LtvChurnService → LtvChurnRealDataController（避免与 controller/ 同名冲突）、GrayReleaseService → GrayReleaseController（同步更新 AiApiAliasController 的 import+调用）、FiscalService → FiscalController、ComplicationService → ComplicationController、SubscriptionService → SubscriptionController、GiftCardService → GiftCardController | AI-001/002/004/006、FISCAL-001、COMP-003、SUBS-001、GIFT-001 |
| `docs/BUSINESS-FEATURE-INVENTORY.md` | AI-006 Controller 类名订正（GrayReleaseService → GrayReleaseController），表名订正（`gray_release_rule` → `feature_flag/ab_experiment/ab_assignment`），其他 7 个功能对应的 Controller 类名同步更新 | AI-001/002/004/006、FISCAL-001、COMP-003、SUBS-001、GIFT-001 |

### 11.5.3 LtvChurn 双 Controller 并存说明

controller/ 包下已有 `LtvChurnController`，service/ 包下有 `LtvChurnRealDataController`（原 LtvChurnService，2026-08-27 重命名），两者**共享同一请求路径前缀 `/ltv-churn`**。这是历史遗留问题：原 LtvChurnService 是用 mock 数据跑真实业务数据算法的验证版本，与 controller/ 下的标准版本并存。

建议后续统一：保留 `LtvChurnController`（标准版），将 `LtvChurnRealDataController` 的方法合并进去或废弃。

### 11.5.4 SQL 目录二分（运维笔记，需确认部署方式）

`backend/` 下存在**两套并行的手工 SQL 脚本集**，目前项目**无 Flyway / Liquibase / spring.sql.init** 依赖（pom.xml 已确认），所有 DDL 是**部署时手动执行**：

1. `backend/src/main/resources/db/sql/` — 主脚本集，含 `init_m0[1-9]_*.sql`、`init_*_*.sql`、`migrate_*.sql`、`init_care_suggestion.sql`、`init_attendance.sql`、`init_rbac_v3_0_c.sql`、本次新增的 `init_care_recipe.sql` / `init_bnpl.sql` / `init_mkt_promotion.sql` 等。
2. `backend/sql/` — 早期脚本集，主要含 `m10_market.sql`（创建 `market_coupon` / `market_coupon_grant` / `market_point_rule` / `market_point_record` / `market_activity`）。

**影响的功能**（DDL 只在 `backend/sql/`）：
- POINTS-001 / POINTS-003（`market_point_record`）
- MKT 老路径（`market_activity`，已被新 `mkt_promotion` 替代）
- COUPON-001/002 老路径（`market_coupon*`，与 `mkt_coupon_batch*` 并存）

**建议**：
- 短期：部署手册 / README 明确"两份 SQL 都需按顺序执行"，否则 POINTS/MKT 老路径会缺表。
- 中期：把 `backend/sql/m10_market.sql` 拆解后迁入 `backend/src/main/resources/db/sql/init_m10_market*.sql`，统一目录；或在 `pom.xml` 引入 Flyway，把两份脚本都纳入版本化迁移。
- 长期：引入 Flyway/Liquibase 后用 `V*__*.sql` 命名，禁手工执行，CI 校验。

### 11.5.2 MKT 双表并存（待决）

当前 `mkt_promotion`（新表，30+ 列展开式，`MktPromotionController` 落点）与 `market_activity`（老表，9 列 + `rule_config JSON`）**同时存在**，前端 `/mkt/promotion/page` 只走新表，老表的 DDL 保留但无 Controller 写入。

**建议二选一**：
- A. 删 `market_activity` 表（数据先归档）
- B. 写迁移脚本把老数据升级到 `mkt_promotion`

---

## 12. Recommended Next Steps

1. **Phase 2 — Feature Reality Check**：按照上述 Queue 顺序，逐个验证功能的三层连通性
2. **修复 BROKEN Features**：优先修复 P0 核心功能的断链问题
3. **验证 MOCK Features**：确认 AI 和 Style Gallery 等功能的实际实现状态
4. **清理重复实现**：统一 API 版本，减少维护成本
5. **完善 PARTIAL Features**：补全缺失的链路层

---

*Report Generated: 2026-08-24*
*Audit Tool: Business Feature Inventory Scanner*
*Total Features: 189*
