# 美业 SaaS Reality Inventory v2.0

**审计时间**: 2026-08-13
**审计版本**: v2.0 (第二轮)
**审计原则**: CODE > CONFIG > DB SCHEMA > API > TEST > UI > MENU > DOCUMENTATION

---

## 一、项目基本信息

| 维度 | 路径 | 技术栈 |
|------|------|--------|
| 前端 | `D:\QiaoPM\frontend\meiyetec-web` | Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router |
| 后端 | `D:\QiaoPM\backend` | Spring Boot 3.1.5 + Java 17 + MyBatis-Plus + MySQL + Redis |
| 数据库 | MySQL 8.0.46 | 租户隔离架构 |

---

## 二、前端 Reality Inventory

### 2.1 Vue 组件统计

| 指标 | 数量 | 备注 |
|------|------|------|
| Vue 文件总数 | 89 | 包括 views 和 components |
| Views 页面 | ~80 | 各业务模块页面 |
| Components 组件 | ~9 | Layout, SubmitButton 等 |

### 2.2 Router 路由统计

| 指标 | 数量 | 备注 |
|------|------|------|
| 路由总数 | 96 | 包括嵌套路由 |
| 业务路由 | ~94 | 带权限控制的业务页面 |
| 公开路由 | 2 | /login, /:pathMatch(.*)* |

**路由领域分布**:

| 领域 | 路由数 |
|------|--------|
| 系统/权限 | ~15 |
| 会员/CRM | ~12 |
| 预约/排班 | ~8 |
| 收银/POS | ~7 |
| 库存 | ~7 |
| 提成/佣金 | ~6 |
| 营销 | ~12 |
| 报表 | ~6 |
| 套餐卡项 | ~4 |
| 组织架构 | ~3 |
| 客户运营 | ~8 |
| AI/智能 | ~2 |
| 医美合规 | ~2 |
| 支付 | ~2 |

### 2.3 API 模块统计

| 文件 | API 数量 | 领域 |
|------|----------|------|
| `api/basic.ts` | ~5 | 基础 CRUD |
| `api/booking.ts` | 9 | 预约 + 排班 + 房间 |
| `api/commission.ts` | ~30 | 提成规则/记录/结算/批次 |
| `api/market.ts` | ~20 | 优惠券/积分/营销活动 |
| `api/package.ts` | ~20 | 套餐/卡项 |
| `api/pos.ts` | ~15 | 收银/订单/退款/对账 |
| `api/rbac.ts` | ~5 | 权限/菜单 |
| `api/report.ts` | ~15 | 报表/导出 |
| `api/stock.ts` | ~30 | 库存/商品/盘点 |
| `api/sys.ts` | ~15 | 系统/租户/日志 |
| `api/request.ts` | 1 | Axios 封装 |
| **总计** | **~175+** | 11 个 API 模块 |

### 2.4 Store 统计

| Store | 用途 |
|-------|------|
| `stores/permission.ts` | 权限/菜单状态管理 |

### 2.5 前端测试统计

| 指标 | 数量 | 备注 |
|------|------|------|
| 测试文件 | 1 | `utils/format.spec.ts` |
| 测试用例 | 4 | 格式化工具函数测试 |
| 测试命令 | `npm run test` (vitest) | 只测试 format.ts |

**测试状态**: 🟠 PARTIAL - 仅覆盖工具函数，无业务逻辑测试

---

## 三、后端 Reality Inventory

### 3.1 Controller 统计

| 指标 | 数量 |
|------|------|
| Controller 总数 | 29 |
| 核心业务 | ~25 |
| 系统管理 | ~4 |

**Controller 清单**:

1. `AuthController` - 认证
2. `BookingController` - 预约
3. `CategoryController` - 分类
4. `CommissionController` - 提成
5. `CouponController` - 优惠券
6. `DcepConfigController` - 数字人民币配置
7. `DcepMerchantQualificationController` - 商户资质
8. `EmployeeController` - 员工
9. `LogRetentionController` - 日志保留
10. `MarketActivityController` - 营销活动
11. `MemberController` - 会员
12. `MemberLevelController` - 会员等级
13. `MemberTagController` - 会员标签
14. `PackageController` - 套餐
15. `PointController` - 积分
16. `PosController` - 收银
17. `PosRefundController` - 退款
18. `RbacController` - 权限
19. `ReconciliationController` - 对账
20. `ReportController` - 报表
21. `RoomController` - 房间
22. `ScheduleController` - 排班
23. `StockController` - 库存
24. `StoreController` - 门店
25. `SysConfigController` - 系统配置
26. `SysLogController` - 操作日志
27. `SysLoginLogController` - 登录日志
28. `SysTenantController` - 租户
29. `WalletController` - 钱包

### 3.2 API Endpoint 统计

| 指标 | 数量 |
|------|------|
| Endpoint 总数 | ~180+ |
| REST Methods | GET/POST/PUT/DELETE |
| 分页接口 | ~25 |
| 导出接口 | ~8 |

### 3.3 Service 层统计

| 指标 | 数量 |
|------|------|
| Service Interface | 52 |
| ServiceImpl | 24 |
| **Service 实现率** | **46%** (24/52) |

**Service 分类**:

| 类型 | 数量 |
|------|------|
| 业务 Service | ~30 |
| 基础 Service | ~22 |
| 核心业务 ServiceImpl | ~24 |

**关键 ServiceImpl**:

| Service | 状态 |
|---------|------|
| `AuthService` | ✅ 独立实现 |
| `BookingServiceImpl` | ✅ 独立实现 |
| `CategoryServiceImpl` | ✅ 独立实现 |
| `CouponServiceImpl` | ✅ 独立实现 |
| `DcepConfigServiceImpl` | ✅ 独立实现 |
| `DcepMerchantQualificationServiceImpl` | ✅ 独立实现 |
| `EmployeeScheduleServiceImpl` | ✅ 独立实现 |
| `EmployeeServiceImpl` | ✅ 独立实现 |
| `MarketActivityServiceImpl` | ✅ 独立实现 |
| `MemberLevelServiceImpl` | ✅ 独立实现 |
| `MemberProfileServiceImpl` | ✅ 独立实现 |
| `MemberServiceImpl` | ⚠️ 仅继承 IService |
| `MemberTagServiceImpl` | ✅ 独立实现 |
| `PackageItemServiceImpl` | ✅ 独立实现 |
| `PackageOrderServiceImpl` | ✅ 独立实现 |
| `PackageRedemptionServiceImpl` | ⚠️ 仅继承 IService |
| `PointServiceImpl` | ✅ 独立实现 |
| `RoomServiceImpl` | ✅ 独立实现 |
| `StoreServiceImpl` | ✅ 独立实现 |
| `SysConfigServiceImpl` | ✅ 独立实现 |
| `SysLoginLogServiceImpl` | ✅ 独立实现 |
| `SysLogServiceImpl` | ✅ 独立实现 |
| `SysTenantServiceImpl` | ✅ 独立实现 |
| `WalletServiceImpl` | ⚠️ 仅继承 IService |
| `WalletTransactionServiceImpl` | ✅ 独立实现 |

**空壳 Service (仅继承 IService)**:
- `BookingService`
- `WalletService`
- `PackageRedemptionService`
- `MemberService`

### 3.4 Mapper 统计

| 指标 | 数量 |
|------|------|
| Mapper 接口 | 120 |
| MyBatis-Plus | 120 |

### 3.5 Entity 统计

| 指标 | 数量 |
|------|------|
| Entity 类 | 120 |
| 数据库表映射 | ~120 |

### 3.6 DTO 统计

| 指标 | 数量 |
|------|------|
| DTO 类 | 23 |
| VO 类 | ~5 |

### 3.7 Enum 统计

| 指标 | 数量 |
|------|------|
| Enum 类 | 3 |
| 退款相关 | 3 (RefundType, RefundStatus, RefundStrategy) |

### 3.8 Exception 统计

| 指标 | 数量 |
|------|------|
| Exception 类 | 3 |
| GlobalExceptionHandler | 1 |

---

## 四、数据库 Reality Inventory

### 4.1 SQL 文件统计

| 指标 | 数量 |
|------|------|
| SQL 迁移文件 | 43+ |
| 初始化脚本 | ~30 |
| 修复脚本 | ~10 |
| Seed 数据 | ~5 |

### 4.2 表结构分类

| 模块 | 表数量 | 主要表 |
|------|--------|--------|
| 系统管理 (sys) | ~15 | sys_tenant, sys_user, sys_role, sys_permission, sys_log |
| 会员 CRM (crm) | ~20 | crm_member, crm_wallet, crm_member_profile |
| 预约 (biz_booking) | ~8 | biz_booking, booking_resource, booking_waitlist |
| 收银 POS (pos) | ~10 | pos_order, pos_order_item, pos_payment, pos_refund |
| 营销 (mkt/market) | ~15 | mkt_coupon, mkt_points_log, mkt_promotion |
| 库存 (stock) | ~8 | stock_product, stock_inventory, stock_record, stock_check |
| 提成 (commission) | ~10 | commission_rule, commission_record, commission_settlement |
| 医美合规 (mh) | ~12 | mh_consent_template, mh_drug_device, mh_visual_archive |
| 数字人民币 (dcep/pay) | ~6 | dcep_config, pay_dcep_order, pay_dcep_notify |
| 套餐卡项 | ~5 | crm_package, crm_package_order, crm_package_redemption |
| **总计** | **~110+** | |

### 4.3 通用字段检查

| 字段 | 是否存在 | 表覆盖率 |
|------|----------|----------|
| tenant_id | ✅ | ~100% 业务表 |
| store_id | ✅ | ~80% 业务表 |
| created_at | ✅ | ~100% 表 |
| updated_at | ✅ | ~90% 表 |
| deleted | ✅ | ~95% 表 (软删除) |

### 4.4 事务支持

| 特性 | 状态 |
|------|------|
| @Transactional | 在 ServiceImpl 中使用 |
| 分布式事务 | ❌ 未实现 |
| TCC/Saga | ❌ 未实现 |

---

## 五、测试 Reality Inventory

### 5.1 后端测试统计

| 测试文件 | 测试用例 | 覆盖范围 |
|----------|----------|----------|
| `ConcurrencyIntegrationTest.java` | ~5 | 并发幂等测试 |
| `PosRefundIntegrationTest.java` | ~8 | 退款集成测试 |
| `RefundStatusTest.java` | ~3 | 退款状态枚举测试 |
| `RefundStrategyTest.java` | ~4 | 退款策略测试 |
| `BizCodeTest.java` | ~15 | 错误码枚举测试 |

**测试框架**: JUnit 5 + Spring Boot Test

### 5.2 测试覆盖率评估

| 模块 | 覆盖率 | 备注 |
|------|--------|------|
| 退款模块 | 🟢 ~30% | 集成测试 |
| 并发幂等 | 🟡 ~10% | 基础覆盖 |
| 错误码 | 🟡 ~20% | 枚举测试 |
| 业务逻辑 | 🔴 <5% | 无业务测试 |
| **整体覆盖率** | **🔴 <5%** | 严重不足 |

---

## 六、配置文件 Reality Inventory

### 6.1 application.yml 配置

| 配置项 | 状态 |
|--------|------|
| Spring Boot | ✅ |
| MySQL DataSource | ✅ |
| Redis | ✅ |
| JWT | ✅ |
| MyBatis-Plus | ✅ |
| CORS | ✅ |

### 6.2 Docker 配置

| 文件 | 状态 |
|------|------|
| docker-compose.yml | ✅ |
| MySQL | ✅ |
| Redis | ✅ |
| Kafka | ✅ |
| MinIO | ✅ |

---

## 七、Reality Inventory 汇总

| 维度 | 数量 | Reality Status |
|------|------|----------------|
| Vue 组件 | 89 | 🟢 EXISTS |
| 前端路由 | 96 | 🟢 EXISTS |
| 前端 API 模块 | 11 | 🟢 EXISTS |
| 前端 API 方法 | ~175+ | 🟢 EXISTS |
| 前端测试 | 1 (4 用例) | 🟠 PARTIAL |
| Controller | 29 | 🟢 EXISTS |
| API Endpoint | ~180+ | 🟢 EXISTS |
| Service Interface | 52 | 🟢 EXISTS |
| ServiceImpl | 24 (46%) | 🟡 PARTIAL |
| Mapper | 120 | 🟢 EXISTS |
| Entity | 120 | 🟢 EXISTS |
| DTO | 23 | 🟢 EXISTS |
| Enum | 3 | 🟢 EXISTS |
| SQL 迁移 | 43+ | 🟢 EXISTS |
| DB 表 | ~110+ | 🟢 EXISTS |
| 后端测试 | 5 文件 | 🟠 PARTIAL |

---

## 八、关键发现

### 8.1 Shell/Mock 风险

| 风险项 | 位置 | 优先级 |
|--------|------|--------|
| 空壳 Service | BookingService, WalletService, PackageRedemptionService, MemberService | P1 |
| DCEP Mock | DcepService.mockPrepay() | P0 |
| AI Mock | VisualArchiveService.mockAiAnalyze() | P1 |
| SMS Mock | SmsSender Demo | P2 |
| 小程序 Mock | MiniProgramSender Demo | P2 |

### 8.2 测试覆盖不足

- 后端业务测试覆盖率 <5%
- 前端业务测试覆盖率 0%
- 缺少集成测试
- 缺少 E2E 测试

### 8.3 事务风险

- 缺少分布式事务支持
- 跨服务事务边界不清晰

---

## 九、Evidence Level 分布

| 证据等级 | 定义 | 数量 |
|----------|------|------|
| E0 | 菜单/名称存在 | ~96 路由 |
| E1 | 页面存在 | ~89 Vue 文件 |
| E2 | API 存在 | ~180+ Endpoints |
| E3 | Backend implementation | ~24 ServiceImpl |
| E4 | DB persistence | ~110+ Tables |
| E5 | Automated Test | <5% 覆盖率 |
| E6 | Runtime verification | 未执行 |
| E7 | E2E verification | 未执行 |

---

**结论**: 项目在代码量上已经具备相当规模，但 **Service 层实现率仅 46%**，测试覆盖率 <5%，存在大量 Mock/Shell 代码。真实业务逻辑的落地情况需要深入审计。
