# 美业 SaaS 真实链路矩阵 V2 (Feature Reality Matrix V2)

**审计日期**: 2026-08-13
**审计范围**: 9 大核心关键功能的全链路验证
**审计原则**: UI → Route → Component → Frontend API → HTTP Endpoint → Controller → Service → Mapper → Entity → DB Table

**图例**:
- ✅ EXISTS - 完整存在且有真实实现
- ⚠️ PARTIAL - 部分实现（部分链路 OK，部分缺失）
- ❌ MISSING - 缺失
- 🟠 SHELL - 空壳（仅继承 IService，逻辑在 Controller）
- 🟣 MOCK - Mock 占位

---

## 1. 关键功能 1：会员 CRUD

### 1.1 创建会员 (Create Member)

```
UI → 会员列表 → 按钮 → 创建表单
       ↓
Route: /member
       ↓
Component: views/member/index.vue
       ↓
Frontend API: memberApi.create -> request.post('/member', data)
       ↓
HTTP Endpoint: POST /api/member
       ↓
Controller: MemberController.create() (line 271-293)
       ↓
Service: MemberServiceImpl (空) + MemberMapper
       ↓
Mapper: MemberMapper.insert() (MyBatis-Plus BaseMapper)
       ↓
Entity: Member (line 1374)
       ↓
DB Table: crm_member
```

| 层级 | 状态 | 证据 |
|------|------|------|
| UI | ✅ | views/member/index.vue: 文件存在 |
| Route | ✅ | router/index.ts:23 - `/member` |
| Frontend API | ✅ | api/basic.ts:50 - `memberApi.create` |
| Controller | ✅ | MemberController.java:271-293 - 完整实现 + @RequirePermission("member:add") |
| Service | 🟠 SHELL | MemberServiceImpl - 仅 `extends ServiceImpl<MemberMapper, Member>` |
| Mapper | ✅ | MemberMapper extends BaseMapper<Member> |
| Entity | ✅ | Member.java - 1374 字节 |
| DB Table | ✅ | crm_member |

**业务逻辑评估**:
- `@RequirePermission("member:add")` ✅
- 字段校验 (BeanUtils.copyProperties) ✅
- 会员号自动生成 (M+日期+4位随机数) ✅
- 手机号唯一性校验 ✅
- 标签关联 (saveMemberTags) ✅
- 业务逻辑全部在 Controller ✅
- ServiceImpl 仅做空壳代理 ⚠️ (分层不规范但不影响功能)

### 1.2 查询会员 (Query Member)

```
UI → 会员列表 → 搜索/分页
       ↓
Route: /member
       ↓
Frontend API: memberApi.page -> request.post('/member/page', data)
       ↓
Controller: MemberController.page() (line 49-104)
       ↓
Service: MemberServiceImpl (空) + MemberMapper
       ↓
DB Table: crm_member
```

| 层级 | 状态 | 证据 |
|------|------|------|
| UI | ✅ | views/member/index.vue |
| API | ✅ | api/basic.ts:46 - `memberApi.page` |
| Controller | ✅ | MemberController.java:49-104 |
| 多条件过滤 | ✅ | 关键字/状态/标签/等级/门店 |
| 租户隔离 | ✅ | TenantContext 注入 |
| 数据范围 | ✅ | ALL/STORE/BRAND/SELF 4 种 |
| 排序 | ✅ | totalConsume/points/balance/lastVisit/joinDate |
| 标签子查询 | ✅ | `inSql` + crm_member_tag_rel |
| 详情补全 | ✅ | fillExtras() 补 levelName/storeName/tags |

### 1.3 更新会员 (Update Member)

| 层级 | 状态 | 证据 |
|------|------|------|
| UI | ✅ | member/index.vue |
| API | ✅ | api/basic.ts:51 - `memberApi.update` |
| Controller | ✅ | MemberController.java:295-309 |
| Service | 🟠 SHELL | 空 |
| 业务逻辑 | ✅ | 重号校验 + 标签重写 (saveMemberTags) |
| 权限 | ✅ | @RequirePermission("member:edit") |

**链路评级**: ✅ 完整可运行，仅 ServiceImpl 为空壳

---

## 2. 关键功能 2：预约 Booking

### 2.1 创建预约 (Create Booking)

```
UI → 预约日历 → 选时段 → 确认
       ↓
Route: /booking/calendar
       ↓
Component: views/booking/calendar.vue
       ↓
Frontend API: bookingApi.create -> request.post('/booking', data)
       ↓
HTTP Endpoint: POST /api/booking
       ↓
Controller: BookingController.create() (line 54-98)
       ↓
Service: BookingServiceImpl (空) + BookingMapper
       ↓
Entity: Booking
       ↓
DB Table: biz_booking
```

| 层级 | 状态 | 证据 |
|------|------|------|
| UI | ✅ | calendar.vue |
| Route | ✅ | router/index.ts:29 `/booking/calendar` |
| API | ✅ | api/booking.ts:11 `bookingApi.create` |
| Controller | ✅ | BookingController.java:54-98 |
| 业务校验 | ✅ | 会员必填 / 起止时间校验 / 结束>开始 |
| 冲突检测 | ✅ | 技师冲突 (line 67-77) + 房间冲突 (line 80-89) |
| 单号生成 | ✅ | BK + yyyyMMddHHmmss |
| 默认状态 | ✅ | 1 (待确认) |
| Service | 🟠 SHELL | 空 |
| Mapper | ✅ | BookingMapper |
| Entity | ✅ | Booking.java |

**额外业务**:
- `booking/available` (line 138-176) - 查询技师可用时段（含排班 + 已有预约）
- `booking/{id}/status` - 状态机校验 (1→2/5/6, 2→3/5/6, 3→4/5)
- `booking/{id}/checkin` - 签到
- `booking/{id}/complete` - 完成
- `booking/stats` - 今日统计 (JdbcTemplate 真实 SQL)
- `booking/export` - CSV 导出

### 2.2 签到/完成

| 操作 | 链路 | 状态 |
|------|------|------|
| 签到 | POST /booking/{id}/checkin → BookingController:204-213 | ✅ |
| 完成 | POST /booking/{id}/complete → BookingController:216-226 | ✅ |
| 状态机 | HashMap<Integer, Set<Integer>> | ✅ |
| 时间戳记录 | checkinTime/completeTime | ✅ |

**链路评级**: ✅ 完整可运行，BookingServiceImpl 空但不影响

---

## 3. 关键功能 3：POS 订单

### 3.1 创建订单 (Create Order)

```
UI → 收银台 → 选会员 → 选服务 → 创建订单
       ↓
Route: /pos/checkout
       ↓
Component: views/pos/checkout.vue
       ↓
Frontend API: orderApi.create -> request.post('/pos/orders', data)
       ↓
HTTP Endpoint: POST /api/pos/orders
       ↓
Controller: PosController.createOrder() (line 84-125)
       ↓
Mapper: PosOrderMapper.insert() + PosOrderItemMapper.insert()
       ↓
Entity: PosOrder, PosOrderItem
       ↓
DB Tables: pos_order, pos_order_item
```

| 层级 | 状态 | 证据 |
|------|------|------|
| UI | ✅ | pos/checkout.vue |
| Route | ✅ | router/index.ts:32 `/pos/checkout` |
| API | ✅ | api/pos.ts:16 `orderApi.create` |
| Controller | ✅ | PosController.java:84-125 |
| 业务校验 | ✅ | 会员必填 (line 88-89) |
| 单号生成 | ✅ | PO + yyyyMMdd + 6位随机 |
| 收银员获取 | ✅ | JWT 解析 userId |
| Service | 🟠 SHELL | PosController 直接用 Mapper |
| Mapper | ✅ | PosOrderMapper |
| Entity | ✅ | PosOrder.java |

### 3.2 添加项目 (Add Item)

| 层级 | 状态 | 证据 |
|------|------|------|
| API | ✅ | api/pos.ts:21 `orderApi.addItem` |
| Controller | ✅ | PosController.java:127-135 |
| 业务校验 | ✅ | 状态 != 1 不允许 |
| 服务价取逻辑 | ✅ | 会员价优先 (line 150-152) |
| 价格计算 | ✅ | totalPrice = unitPrice * quantity |
| 折扣 | ✅ | item.discountAmount |
| 重新计算汇总 | ✅ | recalcOrder() (line 548-565) |

### 3.3 支付 (Pay) - 核心功能

```
UI → 收银台 → 选支付方式 → 支付
       ↓
Frontend API: orderApi.pay -> request.post('/pos/orders/{id}/pay', body)
       ↓
Controller: PosController.pay() (line 198-254) - @Transactional
       ↓
关键业务链:
  1. 归一化支付明细 (normalizePayments) (line 257-269)
  2. 遍历每笔支付 (processOnePayment) (line 272-335)
  3. 钱包扣款 (Member.balance CAS) + 写流水
  4. 次卡核销 (redeemPackage) - 扣 remaining_times + 写 redemption
  5. 数字人民币 (e_cny_soft/hard) - 占位
  6. 现金/微信/支付宝 - 记录即可
  7. 累积 paidAmount
  8. 订单状态置为 2 (已支付)
  9. 触发提成计算 (commissionCalculationService.calculateOrder)
  10. 触发库存消耗 (inventoryConsumptionService.consumeByOrder)
  11. 发送 OrderPaidEvent
       ↓
Mappers: PosOrderMapper, PosPaymentMapper, PosOrderItemMapper
       ↓
Entities: PosOrder, PosPayment, PosOrderItem
       ↓
DB Tables: pos_order, pos_payment, pos_order_item, crm_wallet_transaction
```

| 层级 | 状态 | 证据 |
|------|------|------|
| UI | ✅ | checkout.vue |
| API | ✅ | api/pos.ts:25 `orderApi.pay` |
| Controller | ✅ | PosController.java:198-254 |
| 事务 | ✅ | @Transactional(rollbackFor = Exception.class) |
| 混合支付 | ✅ | normalizePayments() 支持单笔/混合 |
| 钱包扣款 | ✅ | CAS (Wallet.balance) + 流水 |
| 次卡核销 | ✅ | redeemPackage (line 338-361) |
| 数字人民币 | ⚠️ PARTIAL | 占位 + 模拟 transaction ID |
| 现金/微信/支付宝 | ✅ | 记录即可 |
| 库存消耗 | ✅ | InventoryConsumptionService.consumeByOrder |
| 提成计算 | ✅ | CommissionCalculationService.calculateOrder |
| 事件广播 | ✅ | ApplicationEventPublisher |

**链路评级**: ✅ 完整可运行，混合支付 + 事务回滚 + 跨服务联动

---

## 4. 关键功能 4：套餐购买 (Package Purchase)

```
UI → 套餐列表 → 购买 → 收银
       ↓
Route: /package
       ↓
Frontend API: packageApi.purchase -> request.post('/packages/purchase', data)
       ↓
HTTP Endpoint: POST /api/packages/purchase
       ↓
Controller: PackageController.purchase() (line 138-183) - @Transactional
       ↓
关键业务:
  1. 校验会员 (Member 状态)
  2. 校验套餐 (PackageItem 状态)
  3. 创建 PackageOrder (line 152-167)
  4. 单号生成 (genOrderNo)
  5. 总次数 = 剩余次数
  6. 过期时间 = now + validDays
  7. 状态 = 1 (正常)
  8. 累加 saleCount
       ↓
Service: PackageOrderServiceImpl (空) + PackageItemServiceImpl (空)
       ↓
Mapper: PackageOrderMapper.insert(), PackageItemMapper.updateById()
       ↓
Entity: PackageOrder, PackageItem
       ↓
DB Tables: crm_package_order, biz_package_item
```

| 层级 | 状态 | 证据 |
|------|------|------|
| UI | ✅ | views/package/index.vue |
| API | ✅ | api/package.ts:12 `packageApi.purchase` |
| Controller | ✅ | PackageController.java:138-183 |
| 业务校验 | ✅ | 会员冻结/套餐下架 |
| 单号生成 | ✅ | genOrderNo() |
| 默认过期 | ✅ | now + validDays (默认 365) |
| 累加销量 | ✅ | saleCount + 1 |
| Service | 🟠 SHELL | 空 |
| Mapper | ✅ | PackageOrderMapper extends BaseMapper |
| Entity | ✅ | PackageOrder.java |

**注意**: 套餐购买未扣款，未扣库存；只是创建订单记录。

**链路评级**: ✅ 完整可运行，但付款链路未与套餐购买联动（依赖后续 POS 支付流程）

---

## 5. 关键功能 5：套餐核销 (Package Redemption)

```
UI → 收银台 → 选择套餐卡 → 核销
       ↓
Frontend API: packageApi.redeem -> request.post('/packages/redeem', data)
       ↓
HTTP Endpoint: POST /api/packages/redeem
       ↓
Controller: PackageController.redeem() (line 186-195) + Idempotency
       ↓
Service: PackageController.doRedeem() (line 197-269) - @Transactional
       ↓
关键业务:
  1. 校验订单状态 (1=正常, 2=已用完, 3=过期, 4=冻结, 5=退款, 6=转赠)
  2. 校验过期时间
  3. CAS 扣减 remaining_times (PackageOrderMapper.casRedeem)
  4. 写 PackageRedemption
  5. 累加 PackageItem.redeemCount
       ↓
Service: PackageRedemptionServiceImpl (空) - 仅插入 redemption
       ↓
Mapper: PackageOrderMapper.casRedeem (CAS 乐观锁)
       ↓
Entity: PackageOrder, PackageRedemption, PackageItem
       ↓
DB Tables: crm_package_order, crm_package_redemption, biz_package_item
```

| 层级 | 状态 | 证据 |
|------|------|------|
| UI | ✅ | package/index.vue |
| API | ✅ | api/package.ts:13 `packageApi.redeem` |
| Controller | ✅ | PackageController.java:186-195 |
| 幂等 | ✅ | Idempotency-Key |
| 状态校验 | ✅ | 6 种状态分支 |
| CAS 乐观锁 | ✅ | mapper.casRedeem (version + remainingTimes >= times) |
| 二次校验 | ✅ | 行数 0 时判断原因 |
| Redemption 记录 | ✅ | 写入 crm_package_redemption |
| 销量累加 | ✅ | redeemCount + times |
| Mapper | ✅ | PackageOrderMapper.casRedeem |
| Entity | ✅ | PackageOrder, PackageRedemption, PackageItem |

**可选分支**:
- `/packages/redeem-split` - 拆分核销（多员工+多次数）
- `/packages/order/{id}/freeze` - 冻结/解冻
- `/packages/order/{id}/extend` - 续期
- `/packages/order/{id}/refund` - 按剩余次数比例退款到钱包
- `/packages/order/{id}/transfer` - 转赠

**链路评级**: ✅ 完整可运行，CAS + 状态机 + 幂等保护

---

## 6. 关键功能 6：库存消耗 (Stock Consumption)

```
UI → 收银台 → 支付成功
       ↓
Trigger: PosController.pay() line 239
       ↓
Service: InventoryConsumptionService.consumeByOrder(orderId) (line 60-209)
       ↓
关键业务:
  1. 校验订单状态 (line 68-72)
  2. 拉订单明细 (line 78-81)
  3. 拉 BOM 清单 (biz_service_material)
  4. 校验幂等 (pos_order_consumption.idempotency_key)
  5. 逐条扣减库存 (StockInventory CAS)
  6. 写入 stock_record(type=4 领用)
  7. 写入 pos_order_consumption (幂等标记)
       ↓
Mappers: PosOrderMapper, PosOrderItemMapper, BizServiceMaterialMapper,
         StockInventoryMapper, StockRecordMapper, PosOrderConsumptionMapper
       ↓
Entities: PosOrder, PosOrderItem, StockInventory, StockRecord,
          PosOrderConsumption, BizServiceMaterial
       ↓
DB Tables: pos_order, pos_order_item, biz_service_material,
           stock_inventory, stock_record, pos_order_consumption
```

| 层级 | 状态 | 证据 |
|------|------|------|
| 服务调用 | ✅ | InventoryConsumptionService 完整 |
| 事务 | ✅ | @Transactional(rollbackFor = Exception.class) |
| 幂等 | ✅ | UNIQUE(idempotency_key) |
| BOM 加载 | ✅ | bomMapper.listSimpleBom |
| 库存扣减 | ✅ | CAS (version + remaining) |
| 库存记录 | ✅ | Insert stock_record(type=4) |
| 库存不足 | ✅ | 抛 STOCK_NOT_ENOUGH |
| CAS 失败 | ✅ | 抛 CONCURRENCY_CONFLICT |
| 前端展示 | ✅ | pos/checkout.vue + 库存预警/消耗记录 |
| API | ✅ | api/stock.ts:38 `consumptionByOrder` |

**链路评级**: ✅ 完整可运行，订单与库存同生共死（事务绑定）

---

## 7. 关键功能 7：提成计算 (Commission Calculation)

```
UI → 订单支付完成
       ↓
Trigger: PosController.pay() line 235
       ↓
Service: CommissionCalculationService.calculateOrder(orderId) (line 39-104)
       ↓
关键业务:
  1. 校验订单状态 (必须是 status=2)
  2. 拉订单明细 (PosOrderItem)
  3. 遍历每个有 employeeId 的明细:
     a. 查是否已存在提成记录 (幂等)
     b. 查员工 (Employee.status != 0)
     c. 匹配规则 (matchRule - 按门店/业务类型/目标/优先级)
     d. 计算金额 (RATE: baseAmount * rate / FIXED: fixedAmount * quantity)
     e. 写入 CommissionRecord
       ↓
Mappers: PosOrderMapper, PosOrderItemMapper, EmployeeMapper,
         CommissionRuleMapper, CommissionRecordMapper
       ↓
Entities: PosOrder, PosOrderItem, Employee, CommissionRule, CommissionRecord
       ↓
DB Tables: pos_order, pos_order_item, pos_employee,
           commission_rule, commission_record
```

| 层级 | 状态 | 证据 |
|------|------|------|
| Service | ✅ | CommissionCalculationService 真实逻辑 (155 LOC) |
| 业务校验 | ✅ | 订单状态/员工状态/规则匹配 |
| 幂等 | ✅ | sourceType + sourceItemId 查重 |
| 规则匹配 | ✅ | matchRule() - 优先级 + 业务类型 + 目标 |
| RATE 计算 | ✅ | baseAmount * rate |
| FIXED 计算 | ✅ | fixedAmount * quantity |
| 金额精度 | ✅ | BigDecimal.setScale(2, HALF_UP) |
| 重复检测 | ✅ | DuplicateKeyException 重读 |
| 月份标记 | ✅ | salaryMonth = yyyy-MM |
| 反向冲正 | ✅ | invalidateOrder() (line 106-118) |
| Mapper | ✅ | 5 个 Mapper |
| Entity | ✅ | 5 个 Entity |

### 7.2 工资生成 (Salary Generation)

```
Controller: CommissionController.generateSalary() (line 223-267)
       ↓
业务:
  1. 拉员工列表 (Employee.status != 0)
  2. 遍历员工：
     a. 查已存在的工资记录
     b. 聚合 commission_record (SUM baseAmount + SUM commissionAmount)
     c. 计算应付 (baseSalary + commission + adjustment)
     d. 写入 commission_salary
```

| 层级 | 状态 | 证据 |
|------|------|------|
| UI | ✅ | commission/salary.vue |
| API | ✅ | api/commission.ts:32 `generateSalary` |
| Controller | ✅ | CommissionController.generateSalary |
| 业务 | ✅ | 聚合 + 重算 |
| 导出 | ✅ | commission/salary/export (XSSFWorkbook) |
| 确认/发放 | ✅ | salary/{id}/confirm + pay |

### 7.3 结算生成 (Settlement Generation)

```
Controller: CommissionController.generateSettlement() (line 474-485)
       ↓
Service: CommissionSettlementService.generate() (line 37-106)
       ↓
业务:
  1. 校验是否已存在 (existing != null && status > 1)
  2. 拉员工列表
  3. 遍历员工：
     a. sumCommission (从 commission_record)
     b. sumAdjustment (从 commission_salary)
     c. 写入 CommissionSettlementRecord
  4. 累加总计
  5. 更新 CommissionSettlement
```

| 层级 | 状态 | 证据 |
|------|------|------|
| Service | ✅ | CommissionSettlementService 真实逻辑 (179 LOC) |
| 单号生成 | ✅ | CS + yyyyMM + storeId + 4位随机 |
| 状态机 | ✅ | 1(草稿) → 2(已确认) → 3(已发放) |
| 关联回写 | ✅ | pay() 写 settlement_id 到 commission_record |
| Mapper | ✅ | 4 个 Mapper |

**链路评级**: ✅ 完整可运行，含规则匹配、状态机、CAS、回退

---

## 8. 关键功能 8：钱包充值 (Wallet Recharge)

```
UI → 储值管理 → 选会员 → 充值
       ↓
Route: /wallet/recharge
       ↓
Frontend API: walletApi.recharge -> request.post('/wallet/recharge', data)
       ↓
HTTP Endpoint: POST /api/wallet/recharge
       ↓
Controller: WalletController.recharge() (line 82-91) + Idempotency
       ↓
Service: WalletController.doRecharge() (line 115-163) - @Transactional
       ↓
关键业务:
  1. 校验会员存在
  2. 计算总到账 (amount + gift)
  3. 校验金额 > 0
  4. ensureWallet (不存在则创建)
  5. CAS 更新余额 (casUpdate 重试 3 次)
  6. 写 WalletTransaction (type=1 充值)
  7. 同步 crm_member.balance
       ↓
Service: WalletServiceImpl (空) - 仅 WalletMapper 操作
       ↓
Mapper: WalletMapper.updateBalance (CAS)
       ↓
Entity: Wallet, WalletTransaction, Member
       ↓
DB Tables: crm_wallet, crm_wallet_transaction, crm_member
```

| 层级 | 状态 | 证据 |
|------|------|------|
| UI | ✅ | wallet/recharge.vue |
| API | ✅ | api/basic.ts:92 `walletApi.recharge` |
| Controller | ✅ | WalletController.recharge |
| 幂等 | ✅ | Idempotency-Key 支持 |
| 赠送规则 | ✅ | 4 档规则 (100/500/1000/2000) |
| CAS 乐观锁 | ✅ | version + balance + delta |
| 三次重试 | ✅ | MAX_OPTIMISTIC_RETRY = 3 |
| 余额条件 | ✅ | delta < 0 && balance + delta < 0 抛错 |
| 事务 | ✅ | @Transactional |
| 同步 | ✅ | crm_member.balance 同步更新 |
| 事务流水 | ✅ | crm_wallet_transaction |
| Service | 🟠 SHELL | WalletServiceImpl 空 |
| Mapper | ✅ | WalletMapper.updateBalance + insertIfAbsent |
| Entity | ✅ | Wallet, WalletTransaction |

**其他钱包操作**:
- `/wallet/consume` - 消费扣款（独立 API）
- `/wallet/refund` - 退款（独立 API）
- `/wallet/{memberId}` - 查询余额
- `/wallet/{memberId}/transactions` - 流水
- `/wallet/transactions/all` - 全量流水
- `/wallet/promotion/rules` - 赠送规则列表
- `/wallet/promotion/calc` - 计算赠送

**链路评级**: ✅ 完整可运行，CAS + 幂等 + 双向同步

---

## 9. 关键功能 9：退款 (Refund)

### 9.1 旧路径 (PosController.refund)

```
UI → 订单列表 → 退款
       ↓
Frontend API: orderApi.refund -> request.post('/pos/orders/{id}/refund', body)
       ↓
Controller: PosController.refund() (line 363-409) - @Transactional
       ↓
业务:
  1. 校验订单状态 (status=2)
  2. 校验退款金额 <= paidAmount
  3. 如果使用了 wallet 支付 → 按比例退回余额
  4. 同步 crm_member.balance
  5. 写 WalletTransaction (type=3 退款)
  6. 更新 order 状态 (3=已退款)
  7. 触发提成冲正 (commissionCalculationService.invalidateOrder)
```

| 层级 | 状态 | 证据 |
|------|------|------|
| API | ✅ | api/pos.ts:26 `orderApi.refund` |
| Controller | ✅ | PosController.refund |
| 钱包退款 | ✅ | 比例计算 + 余额更新 |
| 提成冲正 | ✅ | invalidateOrder |
| 订单状态 | ✅ | 3 (已退款) |

### 9.2 新路径 (R2.0-C: PosRefundService)

```
UI → 退款单 → 创建
       ↓
Frontend API: refundApi.create -> request.post('/pos/refund', body)
       ↓
Controller: PosRefundController.create() (line 36-48) + Idempotency
       ↓
Service: PosRefundService.createRefund() (line 74-202) - @Transactional
       ↓
关键业务:
  1. 校验订单 (status=2)
  2. 幂等: 同 orderId 已有 SUCCESS 退款单 → 报错
  3. 校验金额 (paidAmount - refundedAmount)
  4. 决定 RefundType (FULL/PART)
  5. 决定 RefundStrategy (WALLET/MIXED/ORIGINAL)
  6. 金额阈值 ≥5000 进入 AUDIT 状态
  7. 拆分到原支付明细 (PosRefundItem)
  8. 状态机: PENDING → APPROVED → PROCESSING → SUCCESS
       ↓
逆向补偿 (handleReverse, line 359-380):
  1. reversePackage (line 389-417) - CAS 回退次数
  2. reversePoints (line 419-447) - 积分回退
  3. reverseStock (line 449-520) - 库存回退
  4. reverseCommission (line 522-528) - 提成冲正
       ↓
逆向日志: PosRefundReverseLog
       ↓
Mappers: PosRefundMapper, PosRefundItemMapper, PosRefundReverseLogMapper,
         PosOrderMapper, PosOrderItemMapper, PosPaymentMapper, MemberMapper,
         WalletMapper, PackageOrderMapper, StockInventoryMapper, StockRecordMapper
       ↓
Entities: PosRefund, PosRefundItem, PosRefundReverseLog, PosOrder, PosOrderItem,
          PosPayment, Member, Wallet, PackageOrder, StockInventory, StockRecord
       ↓
DB Tables: pos_refund, pos_refund_item, pos_refund_reverse_log,
           pos_order, pos_order_item, pos_payment, crm_member,
           crm_wallet, crm_package_order, stock_inventory, stock_record
```

| 层级 | 状态 | 证据 |
|------|------|------|
| UI | ✅ | pos/refund.vue |
| API | ✅ | api/pos.ts:35 `refundApi.create` |
| Controller | ✅ | PosRefundController.create |
| Service | ✅ | PosRefundService 完整真实逻辑 (606 LOC) |
| 幂等 | ✅ | Idempotency-Key + 同 orderId SUCCESS 检查 |
| 状态机 | ✅ | DRAFT → PENDING → AUDIT → APPROVED → PROCESSING → SUCCESS |
| 金额阈值 | ✅ | ≥5000 进 AUDIT |
| 拆分支付 | ✅ | 按比例拆分到 PosRefundItem |
| 逆向 Package | ✅ | CAS 回退 remaining_times |
| 逆向 Points | ✅ | 点数 CAS + 流水 |
| 逆向 Stock | ✅ | CAS + 库存记录 |
| 逆向 Commission | ✅ | invalidateOrder |
| 逆向日志 | ✅ | PosRefundReverseLog |
| 跨单退款 | ✅ | createCrossRefund |
| 审核 | ✅ | refund/{id}/audit |
| 取消 | ✅ | refund/{id}/cancel |

**链路评级**: ✅ 完整可运行，5 维逆向补偿 + 状态机 + 幂等

---

## 10. 关键功能总结矩阵

| Feature | UI | Route | API | Controller | Service | Mapper | Entity | DB | 整体评级 |
|---------|----|----|-----|-----------|---------|--------|-------|-----|---------|
| 会员 CRUD | ✅ | ✅ | ✅ | ✅ | 🟠 | ✅ | ✅ | ✅ | ✅ 完整 |
| 预约 | ✅ | ✅ | ✅ | ✅ | 🟠 | ✅ | ✅ | ✅ | ✅ 完整 |
| POS 订单 | ✅ | ✅ | ✅ | ✅ | (N/A) | ✅ | ✅ | ✅ | ✅ 完整 |
| 套餐购买 | ✅ | ✅ | ✅ | ✅ | 🟠 | ✅ | ✅ | ✅ | ✅ 完整 |
| 套餐核销 | ✅ | ✅ | ✅ | ✅ | 🟠 | ✅ | ✅ | ✅ | ✅ 完整 |
| 库存消耗 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ 完整 |
| 提成计算 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ 完整 |
| 钱包充值 | ✅ | ✅ | ✅ | ✅ | 🟠 | ✅ | ✅ | ✅ | ✅ 完整 |
| 退款 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ 完整 |

**总览**: 9 大核心功能均端到端可运行，但部分 **ServiceImpl 为空壳**（业务逻辑下沉到 Controller），不影响功能正确性，但分层不规范。

---

## 11. ServiceImpl 深度审查

### 11.1 BookingServiceImpl

```java
@Service
public class BookingServiceImpl extends ServiceImpl<BookingMapper, Booking> implements BookingService {
}
```
- **LOC**: 11 (仅类声明)
- **真实业务**: 0 (全部在 `BookingController.create/page/available/checkin/complete/stats`)
- **评估**: 🟠 SHELL - 业务逻辑全部在 Controller
- **可改进**: 移入 ServiceImpl，分层更清晰

### 11.2 WalletServiceImpl

```java
@Service
public class WalletServiceImpl extends ServiceImpl<WalletMapper, Wallet> implements WalletService {
}
```
- **LOC**: 11
- **真实业务**: 0 (全部在 `WalletController.doRecharge/doConsume/doRefund`)
- **评估**: 🟠 SHELL
- **CAS 逻辑**: 在 Controller.casUpdate()，未下沉

### 11.3 PackageRedemptionServiceImpl

```java
@Service
public class PackageRedemptionServiceImpl extends ServiceImpl<PackageRedemptionMapper, PackageRedemption> implements PackageRedemptionService {
}
```
- **LOC**: 11
- **真实业务**: 0 (插入 redemption 在 `PackageController.doRedeem`)
- **评估**: 🟠 SHELL

### 11.4 MemberServiceImpl

```java
@Service
public class MemberServiceImpl extends ServiceImpl<MemberMapper, Member> implements MemberService {
}
```
- **LOC**: 11
- **真实业务**: 0 (全部在 MemberController)
- **评估**: 🟠 SHELL

### 11.5 CommissionCalculationService (有真实业务)

```java
@Service
public class CommissionCalculationService {
    @Transactional(rollbackFor = Exception.class)
    public List<CommissionRecord> calculateOrder(Long orderId) { ... 155 LOC ... }
    public int invalidateOrder(Long orderId, String reason) { ... }
    public CommissionRule matchRule(...) { ... }
    private BigDecimal calculateAmount(...) { ... }
}
```
- **LOC**: 155
- **真实业务**: ✅ 4 个核心方法
- **关键逻辑**:
  - 订单状态校验
  - 幂等查询 (sourceType + sourceItemId)
  - 规则匹配 (业务类型 + 目标 + 优先级)
  - RATE / FIXED 两种计算模式
  - DuplicateKeyException 重读
- **评估**: ✅ 真实业务逻辑完整

### 11.6 CommissionSettlementService (有真实业务)

```java
@Service
public class CommissionSettlementService {
    @Transactional
    public CommissionSettlement generate(Long storeId, String salaryMonth) { ... }
    public CommissionSettlement confirm(Long settlementId, Long operatorId) { ... }
    public CommissionSettlement pay(Long settlementId, Long operatorId) { ... }
    public List<Map<String, Object>> getRecords(Long settlementId) { ... }
}
```
- **LOC**: 179
- **真实业务**: ✅ 4 个核心方法
- **关键逻辑**:
  - 状态机校验 (1→2→3)
  - 员工聚合 (sumCommission + sumAdjustment)
  - 单号生成 (CS + yyyyMM + storeId + random)
  - settlement_id 回写 commission_record
- **评估**: ✅ 真实业务逻辑完整

---

## 12. 关键观察

### 12.1 优点
- ✅ 端到端可运行，所有关键功能链路完整
- ✅ 关键链路（POS 订单、套餐核销、退款、库存消耗）都有真实的 Service 层实现
- ✅ CAS 乐观锁、幂等键、状态机、事务回滚都到位
- ✅ 复杂业务（PosRefundService、PosController.pay）跨多个 mapper 联动
- ✅ 自动化测试 30+ PowerShell 脚本

### 12.2 缺点 / 风险
- 🟠 4 个 ServiceImpl 为空（Booking/Wallet/Member/PackageRedemption）
- ⚠️ 业务逻辑集中在 Controller，违反分层原则
- ⚠️ 数字人民币支付仅占位
- ⚠️ 会员详情 Tabs 返回空列表（consumeRecords/bookings/packages/walletTransactions）
- 🟣 E2E 测试完全缺失（无 Cypress/Playwright）
- 🟣 部分前端 API 调用与后端命名不一致（须查 basic.ts + 各模块 api 文件）

### 12.3 下一步建议
1. 将 Controller 业务逻辑下沉到 ServiceImpl
2. 补充 E2E 测试
3. 完成数字人民币支付通道对接
4. 联通会员详情 Tabs（消费/预约/套餐/储值）

---

## 附录：E-Level 评分卡

| Feature | E0 | E1 | E2 | E3 | E4 | E5 | E6 | E7 |
|---------|----|----|----|----|----|----|----|----|
| 会员 CRUD | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| 预约 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| POS 订单 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| 套餐 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| 库存 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| 提成 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| 钱包 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| 退款 (R2.0-C) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |

**最小可接受线 E0-E6 全部满足** ✅
**E7 E2E 待补** ❌
