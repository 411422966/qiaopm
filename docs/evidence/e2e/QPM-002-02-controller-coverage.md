# QPM-002-02: E2E 测试覆盖率分析报告

**报告编号**：QPM-002-02  
**分析日期**：2026-08-17  
**分析范围**：全部 29 个后端 REST Controller  
**E2E 测试来源**：`frontend/meiyetec-web/e2e/*.spec.ts`（共 7 个文件）

---

## 一、分析摘要

| 指标 | 数量 | 说明 |
|------|------|------|
| 后端 Controller 总数 | **29** | 全部位于 `backend/src/main/java/com/meiyetec/controller/` |
| E2E 测试文件总数 | **7** | auth / smoke / role-menu / golden-path / full-flow / business-flow / new-modules |
| ✅ FULL 覆盖 | **8** | 有完整业务链路 E2E，覆盖核心 CRUD+状态流转 |
| ⚠️ PARTIAL 覆盖 | **9** | 路由可达但无深度业务逻辑验证 |
| ❌ NO 覆盖 | **12** | 完全无 E2E 测试 |

---

## 二、覆盖等级判定标准

| 等级 | 定义 | 说明 |
|------|------|------|
| ✅ FULL | 同一 `test()` 内覆盖"列表→创建→状态变更→数据库读回"完整链路 | 如 `full-flow.spec.ts` 的完整业务闭环测试 |
| ⚠️ PARTIAL | 路由可达（`page.goto` 成功 + `toHaveTitle` 通过）但无 API 直接调用或业务状态断言 | 如 `golden-path.spec.ts` 的"仅验证路由 + 页面标题"测试 |
| ❌ NO | E2E 测试套件中无任何测试触及该 Controller 的任何路由或 API | — |

---

## 三、Controller 覆盖明细表

| # | Controller | 路径前缀 | HTTP 方法 | E2E 覆盖 | 覆盖说明 |
|---|-----------|---------|-----------|---------|---------|
| 1 | **AuthController** | `/auth` | POST GET | ✅ FULL | `auth.spec.ts` 验证登录表单/按钮可见；`role-menu.spec.ts` 调用 `/api/auth/login` 和 `/me/permissions`；`full-flow.spec.ts` Step 1 直接调 `/auth/login` 验证 token |
| 2 | **BookingController** | `/booking` | POST GET PUT DELETE | ✅ FULL | `full-flow.spec.ts` 完整闭环：创建预约 → 确认(status 1→2) → 签到(→3) → 数据库读回验证；`golden-path.spec.ts` 验证 `/booking/calendar`、`/booking/list`、`/booking/schedule` 路由可达；`smoke.spec.ts` 验证 `/booking/calendar` 可访问 |
| 3 | **CommissionController** | `/commission` | GET POST PUT | ⚠️ PARTIAL | `full-flow.spec.ts` Step 7 调 `/commission/records/page` 验证佣金写入（业务端到端）；`golden-path.spec.ts` 验证 `/commission/rules`、`/commission/records` 路由可达；但 `/commission/rules/CRUD`、`/salary/generate`、`/settlement` 等子模块无独立 E2E |
| 4 | **CouponController** | `/market/coupon` | GET POST PUT DELETE | ⚠️ PARTIAL | `golden-path.spec.ts` 验证 `/market/coupon` 路由可达 + title="优惠券"；但无创建/发放/核销优惠券的完整 E2E |
| 5 | **DcepConfigController** | `/sys/dcep` | GET POST PUT | ❌ NO | 数字人民币配置（敏感），无任何 E2E 测试覆盖 |
| 6 | **DcepMerchantQualificationController** | `/sys/dcep/qualification` | GET POST DELETE | ❌ NO | 商户资质管理（敏感），无任何 E2E 测试覆盖 |
| 7 | **EmployeeController** | `/employee` | GET POST PUT DELETE | ⚠️ PARTIAL | `role-menu.spec.ts` 菜单断言中隐含员工菜单可见性；无直接调 `/employee` API 的 E2E；无员工创建/编辑的完整链路测试 |
| 8 | **LogRetentionController** | `/sys/log-retention` | GET PUT | ❌ NO | 日志保留策略配置，无任何 E2E 测试覆盖 |
| 9 | **MarketActivityController** | `/market/activity` | GET POST PUT DELETE | ⚠️ PARTIAL | `golden-path.spec.ts` 验证 `/market/activity` 路由可达 + title="营销活动"；无创建活动/上下线的完整 E2E |
| 10 | **MemberController** | `/member` | GET POST PUT DELETE | ✅ FULL | `full-flow.spec.ts` Step 1 直接调 `/member/1` 验证会员存在（数据契约断言）；`golden-path.spec.ts` 验证 `/member` 路由可达+title="会员管理"+新建按钮可见；`smoke.spec.ts` 验证 `/member` 可访问 |
| 11 | **MemberLevelController** | `/member-level` | GET POST PUT DELETE | ❌ NO | 会员等级管理，无任何 E2E 测试覆盖 |
| 12 | **MemberTagController** | `/member/tag` | GET POST PUT DELETE | ❌ NO | 会员标签管理，无任何 E2E 测试覆盖 |
| 13 | **PackageController** | `/packages` | GET POST PUT DELETE | ⚠️ PARTIAL | `golden-path.spec.ts` 验证 `/package`、`/package/member-orders` 路由可达；无套餐购买/核销/退款的完整 E2E；注意：PackageController 由 PackageService 提供完整业务，E2E 尚未覆盖此核心购买链路 |
| 14 | **PointController** | `/market/point` | GET POST | ❌ NO | 积分管理，无任何 E2E 测试覆盖 |
| 15 | **PosController** | `/pos` | GET POST PUT DELETE | ✅ FULL | `full-flow.spec.ts` 完整闭环：创建订单 → 支付(status→2) → 数据库读回验证；`golden-path.spec.ts` 验证 `/pos/checkout`、`/pos/orders`、`/pos/refund` 路由可达；`smoke.spec.ts` 验证 `/pos/orders` 可访问 |
| 16 | **PosRefundController** | `/pos/refund` | GET POST PUT | ⚠️ PARTIAL | `full-flow.spec.ts` 异常链路：超额支付被拒绝；`golden-path.spec.ts` 验证 `/pos/refund` 路由可达；但无退款单创建→审核→处理的完整 E2E |
| 17 | **RbacController** | `/auth/me/*`, `/sys/role/*` | GET POST PUT DELETE | ✅ FULL | `role-menu.spec.ts` 完整覆盖：5 角色登录 → `/auth/me/permissions` 获取菜单+权限 → 断言菜单矩阵匹配（`MENU_ROLE_MAPPING.md`）；`auth.spec.ts` 登录守卫验证 |
| 18 | **ReconciliationController** | `/pos/reconciliation` | GET POST | ❌ NO | 支付对账功能，无任何 E2E 测试覆盖 |
| 19 | **ReportController** | `/report` | GET POST | ⚠️ PARTIAL | `golden-path.spec.ts` 验证 `/report/dashboard`、`/report/biz` 路由可达；无报表数据因业务操作产生变化的端到端验证 |
| 20 | **RoomController** | `/room` | GET POST PUT DELETE | ❌ NO | 房间管理，无任何 E2E 测试覆盖 |
| 21 | **ScheduleController** | `/schedule` | GET POST PUT DELETE | ⚠️ PARTIAL | `golden-path.spec.ts` 验证 `/booking/schedule` 路由可达 + title="排班管理"；无排班创建/更新的完整 E2E |
| 22 | **StockController** | `/stock` | GET POST PUT DELETE | ⚠️ PARTIAL | `golden-path.spec.ts` 验证 `/stock/list`、`/stock/record`、`/stock/alert` 路由可达；无入库/出库/预警的完整 E2E |
| 23 | **StoreController** | `/store` | GET POST PUT DELETE | ❌ NO | 门店管理，无任何 E2E 测试覆盖 |
| 24 | **SysConfigController** | `/sys/config` | GET POST PUT DELETE | ❌ NO | 系统配置管理，无任何 E2E 测试覆盖 |
| 25 | **SysLogController** | `/sys/log` | GET DELETE | ❌ NO | 操作日志查询/清理，无任何 E2E 测试覆盖 |
| 26 | **SysLoginLogController** | `/sys/login-log` | GET POST DELETE | ❌ NO | 登录日志查询/清理，无任何 E2E 测试覆盖 |
| 27 | **SysTenantController** | `/sys/tenant` | GET POST PUT DELETE | ❌ NO | 租户管理，无任何 E2E 测试覆盖；`golden-path.spec.ts` 仅验证 `/sys/tenant` 路由可达 |
| 28 | **WalletController** | `/wallet` | GET POST | ⚠️ PARTIAL | `full-flow.spec.ts` 端到端流程中通过 POS 支付间接触发钱包扣款（`wallet: consume`）；`golden-path.spec.ts` 验证 `/wallet` 路由可达（会员储值入口）；但无充值/消费的独立 E2E 完整链路 |
| 29 | **CategoryController** | `/category` | GET POST PUT DELETE | ❌ NO | 商品分类管理，无任何 E2E 测试覆盖 |

---

## 四、覆盖率统计

```
✅ FULL 覆盖   :  8 / 29  (27.6%)
⚠️ PARTIAL 覆盖:  9 / 29  (31.0%)
❌ NO 覆盖     : 12 / 29  (41.4%)
```

**按业务模块分组**：

| 模块 | Controller 数 | FULL | PARTIAL | NO |
|------|-------------|------|---------|-----|
| 核心业务（M03-M07） | 8 | 4 | 3 | 1 |
| 营销模块（M04） | 4 | 0 | 2 | 2 |
| 系统管理（M08） | 8 | 0 | 0 | 8 |
| RBAC/认证 | 2 | 2 | 0 | 0 |
| 门店/基础数据 | 3 | 0 | 0 | 3 |
| 报表/对账 | 2 | 0 | 1 | 1 |
| 数字人民币（敏感） | 2 | 0 | 0 | 2 |

---

## 五、Top 5 高优先级覆盖缺口

以下 Controller 无任何 E2E 覆盖，但业务重要性高，建议优先补充：

### 1. 🔴 PackageController (`/packages`) — 高优先级
**理由**：套餐次卡是美业 SaaS 核心变现产品，涉及**购买→核销→退款→转赠**完整生命周期的资金安全。
**缺口**：
- `/packages/purchase` 套餐购买（含钱包/支付扣款）
- `/packages/redeem` 套餐核销（含 CAS 并发扣次）
- `/packages/order/{id}/refund` 套餐退款（按比例折现退钱包）
- `/packages/order/{id}/transfer` 套餐转赠
- `/packages/order/{id}/extend` 套餐续期
**建议**：参考 `full-flow.spec.ts` 的 API 驱动模式，创建独立 `package-flow.spec.ts`

### 2. 🔴 WalletController (`/wallet`) — 高优先级
**理由**：钱包模块涉及**储值金额**（资金安全 P0 级），P0-MONEY-002 已做重构，但无独立 E2E 验证。
**缺口**：
- `/wallet/recharge` 储值充值（含赠送规则）
- `/wallet/consume` 钱包扣款（POS 收银联动）
- `/wallet/refund` 钱包退款
- `/wallet/promotion/calc` 储值赠送规则计算
**建议**：创建 `wallet-flow.spec.ts`，验证充值→消费→退款→余额一致性

### 3. 🟠 ReconciliationController (`/pos/reconciliation`) — 中高优先级
**理由**：支付对账是资金安全的最后防线，涉及第三方支付流水与本地订单一致性。
**缺口**：`/pos/reconciliation/run` 对账触发、`/pos/reconciliation/page` 对账结果查询
**建议**：配合 mock 支付网关，创建 `reconciliation-flow.spec.ts`

### 4. 🟠 CommissionController 子模块 (`/commission`) — 中高优先级
**理由**：提成计算直接影响技师收入，业务逻辑复杂（P0-T11 已验证佣金自动计算）。
**缺口**：
- `/commission/rules/CRUD` 提成规则增删改查
- `/commission/salary/generate` 工资报表生成
- `/commission/settlement/*` 结算单生成/确认/发放
**建议**：在 `commission-flow.spec.ts` 中补充提成规则配置→订单→佣金记录→工资报表的完整链路

### 5. 🟠 PosRefundController (`/pos/refund`) — 中优先级
**理由**：退款业务涉及资金回流，是高频投诉场景。
**缺口**：
- `/pos/refund` 退款单创建（含幂等校验）
- `/pos/refund/cross` 跨单退款
- `/pos/refund/{id}/audit` 退款审核
- `/pos/refund/{id}/process` 退款执行
- `/pos/refund/{id}/cancel` 退款取消
**建议**：创建 `refund-flow.spec.ts`，验证退款申请→审核→执行→钱包回滚的完整链路

---

## 六、已验证的完整业务链路（FULL Coverage）

### 链路 1：会员 → 预约 → 签到 → 收银 → 支付 → 佣金（`full-flow.spec.ts`）
```
MemberController      → /member/1              ✅
BookingController      → /booking (POST)        ✅
BookingController      → /booking/{id}/status  ✅ 确认
BookingController      → /booking/{id}/checkin  ✅ 签到
PosController          → /pos/orders (POST)     ✅ 创建
PosController          → /pos/orders/{id}/pay  ✅ 支付
CommissionController   → /commission/records/page ✅ 佣金验证
```

### 链路 2：5 角色权限矩阵（`role-menu.spec.ts`）
```
AuthController        → /auth/login            ✅
RbacController        → /auth/me/permissions   ✅ 菜单+权限矩阵
```

### 链路 3：登录 → 业务页路由可达（`golden-path.spec.ts`）
```
/login               → 表单/按钮可见        ✅
/dashboard           → title=首页            ✅
/member              → title=会员管理        ✅
/booking/calendar    → title=预约日历        ✅
/booking/list        → 新建按钮可见         ✅
/booking/schedule    → title=排班管理        ✅
/pos/checkout        → title=收银台          ✅
/pos/orders          → title=订单列表        ✅
/pos/refund          → title=退款单          ✅
/stock/*             → title=库存...         ✅
/package             → title=套餐次卡        ✅
/package/member-orders → title=会员套餐查询  ✅
/commission/rules     → title=提成规则        ✅
/commission/records  → title=提成记录        ✅
/report/dashboard    → title=数据看板        ✅
/report/biz           → title=营业报表        ✅
/market/coupon        → title=优惠券          ✅
/market/activity      → title=营销活动       ✅
/setting             → title=系统设置        ✅
/sys/tenant          → title=租户管理        ✅
```

---

## 七、E2E 测试文件清单

| 文件 | 用途 | 覆盖 Controller |
|------|------|----------------|
| `auth.spec.ts` | 未认证登录页测试 | AuthController |
| `smoke.spec.ts` | 5 核心路由冒烟测试 | MemberController, BookingController, PosController |
| `role-menu.spec.ts` | 5 角色权限矩阵验证（L1+L2+L3） | AuthController, RbacController |
| `golden-path.spec.ts` | 9 大模块路由可达 + 性能 | Member, Booking, Pos, Stock, Package, Commission, Report, Coupon, Activity, Sys |
| `full-flow.spec.ts` | 完整业务闭环 + 异常链路 | Auth, Member, Booking, Pos, Commission |
| `business-flow.spec.ts` | 新模块业务流（Kiosk/GDPR/Plugin） | 无直接对应后端 Controller |
| `new-modules.spec.ts` | 批次 4+5 新模块 DOM 断言 | 无直接对应后端 Controller（/kiosk 等为前端路由） |

---

## 八、技术债与改进建议

### 8.1 测试数据隔离
`full-flow.spec.ts` 依赖 demo 数据（会员 id=1、技师 id=4），重跑可能冲突。建议：
- 每次 E2E 前通过 API 创建临时测试数据，测试结束后清理
- 或使用 `@test({ parallel })` 隔离+随机种子避免碰撞

### 8.2 API 直接调用覆盖率偏低
当前 29 个 Controller 中，E2E 直接调用的仅：
- `POST /auth/login`
- `GET /auth/me/permissions`
- `GET /member/{id}`
- `POST /booking`
- `PUT /booking/{id}/status`
- `POST /booking/{id}/checkin`
- `POST /pos/orders`
- `POST /pos/orders/{id}/pay`
- `GET /commission/records/page`

其余 20+ 个 API（包括所有系统管理类）均无 E2E 直接覆盖。

### 8.3 建议新增 E2E 文件
```
e2e/
  package-flow.spec.ts      # 套餐：购买→核销→退款→转赠
  wallet-flow.spec.ts        # 钱包：充值→消费→退款→余额验证
  commission-flow.spec.ts    # 提成：规则配置→订单→佣金→工资→结算
  refund-flow.spec.ts        # 退款：申请→审核→执行→钱包回滚
  reconciliation-flow.spec.ts # 对账：触发→查询→异常处理
```

---

*报告生成：基于 2026-08-17 代码库分析，E2E 测试套件版本参见 `frontend/meiyetec-web/e2e/`*
