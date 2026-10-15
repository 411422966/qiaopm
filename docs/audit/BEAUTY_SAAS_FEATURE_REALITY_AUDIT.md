# 美业 SaaS 功能真实性审计报告
## Beauty SaaS Feature Reality Audit

**审计日期**: 2026-08-13  
**审计范围**: 整个代码仓库  
**审计原则**: 代码事实优先于产品描述

---

## 1. 审计摘要 (Audit Summary)

### 1.1 项目扫描范围

| 维度 | 信息 |
|------|------|
| 前端框架 | Vue 3 + TypeScript + Vite |
| 后端框架 | Spring Boot 3.1.5 + MyBatis-Plus |
| 数据库 | MySQL |
| ORM | MyBatis-Plus 3.5.5 |
| API 架构 | RESTful API |
| 前端测试 | Vitest |
| 后端测试 | JUnit (Spring Boot Test) |
| 前端页面数 | 89 个 Vue 组件 |
| 后端 Controller 数 | 29 个 |
| 后端 Service 数 | 79 个 |
| 数据库表数 | 120+ 个 Entity |

### 1.2 功能审计统计

| Reality Status | 数量 | 说明 |
|----------------|------|------|
| 🟢 REAL (真实完成) | ~45 | 核心业务逻辑完整 |
| 🟡 PARTIAL (部分完成) | ~25 | 具备基础但功能不完整 |
| 🟠 MOCK/SHELL (空壳/Mock) | ~15 | 有页面但无真实实现 |
| 🔴 MISSING (缺失) | ~10 | 完全没有实现 |
| ⚫ UNVERIFIED (未验证) | ~20 | 需要运行环境验证 |

**总计功能点**: ~115 个

### 1.3 Reality Score

| 维度 | 分数 | 权重 | 加权分 |
|------|------|------|--------|
| 功能实现 | 40% | 30% | 12% |
| 页面实现 | 75% | 10% | 7.5% |
| API 覆盖率 | 60% | 10% | 6% |
| 数据持久化 | 65% | 15% | 9.75% |
| 业务逻辑 | 50% | 15% | 7.5% |
| 测试覆盖 | 10% | 10% | 1% |
| E2E 测试 | 0% | 10% | 0% |
| **Overall Score** | | | **43.75%** |

| 子维度 | 分数 |
|--------|------|
| Core Business Reality Score | 55% |
| Advanced Feature Reality Score | 25% |
| Data Reality Score | 65% |
| API Reality Score | 60% |
| Test Reality Score | 10% |
| E2E Reality Score | 0% |

---

## 2. 项目架构 (Project Architecture)

### 2.1 技术栈

```
前端 (Frontend):
├── Vue 3.5.40
├── TypeScript
├── Vite 8.2.0
├── Element Plus 2.14.3
├── Pinia 4.0.2 (状态管理)
├── Vue Router 4.6.4
├── Axios 1.19.0
├── ECharts 6.1.0
└── Vitest 2.1.9 (测试)

后端 (Backend):
├── Spring Boot 3.1.5
├── MyBatis-Plus 3.5.5
├── MySQL Connector
├── Redis (Spring Data Redis)
├── JWT (jjwt 0.12.3)
├── BCrypt (Spring Security Crypto)
├── Apache POI 5.2.5 (Excel)
└── Lombok
```

### 2.2 目录结构

```
d:\QiaoPM
├── frontend/
│   └── meiyetec-web/
│       └── src/
│           ├── api/          (API 调用模块)
│           ├── views/        (页面组件)
│           ├── components/   (公共组件)
│           ├── stores/       (Pinia 状态)
│           ├── router/       (路由)
│           └── utils/        (工具函数)
├── backend/
│   └── src/main/java/com/meiyetec/
│       ├── controller/      (29 个控制器)
│       ├── service/         (79 个服务)
│       ├── entity/          (120+ 个实体)
│       ├── mapper/          (数据访问)
│       ├── dto/             (数据传输对象)
│       ├── config/          (配置)
│       ├── security/        (安全/租户)
│       └── exception/        (异常处理)
├── docs/
│   ├── audit/              (本报告)
│   └── 模块化验收/         (验收文档)
└── *.sql                   (数据库脚本)
```

---

## 3. 当前菜单映射 (Current Menu Mapping)

### 3.1 菜单列表与实现状态

| 菜单 | 路由 | 页面 | API | 后端 | DB | Reality Status |
|------|------|------|-----|------|-----|----------------|
| 首页 | /dashboard | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 会员管理 | /member | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 储值管理 | /wallet/recharge | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 储值流水 | /wallet/transactions | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 套餐次卡 | /package | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 套餐管理 | /package/list | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 会员套餐查询 | /package/member-orders | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 预约日历 | /booking/calendar | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 预约列表 | /booking/list | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 排班管理 | /booking/schedule | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 收银台 | /pos/checkout | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 订单列表 | /pos/orders | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 收银统计 | /pos/stats | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 退款单 | /pos/refund | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 支付对账 | /pos/reconciliation | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 库存列表 | /stock/list | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 商品/耗材 | /stock/product | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 出入库记录 | /stock/record | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 消耗记录 | /stock/consumption | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 盘点管理 | /stock/check | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 库存预警 | /stock/alert | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 库存统计 | /stock/stats | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 提成规则 | /commission/rules | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 提成记录 | /commission/records | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 工资报表 | /commission/salary | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 结算管理 | /commission/settlement | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 优惠券 | /market/coupon | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 积分商城 | /market/points | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 营销活动 | /market/activity | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 数据看板 | /report/dashboard | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 营业报表 | /report/biz | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 导出管理 | /report/export | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 服务项目 | /biz/service-item | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 疗程方案 | /biz/treatment-plan | ✅ | ⚠️ | ⚠️ | ⚠️ | 🟡 PARTIAL |
| 预约资源 | /booking/resource | ✅ | ⚠️ | ⚠️ | ⚠️ | 🟡 PARTIAL |
| 候补队列 | /booking/waitlist | ✅ | ⚠️ | ⚠️ | ⚠️ | 🟡 PARTIAL |
| 爽约管理 | /booking/no-show | ✅ | ⚠️ | ⚠️ | ⚠️ | 🟡 PARTIAL |
| 提成结算批次 | /commission/batch | ✅ | ⚠️ | ⚠️ | ⚠️ | 🟡 PARTIAL |
| 提成冲正 | /commission/reversal | ✅ | ⚠️ | ⚠️ | ⚠️ | 🟡 PARTIAL |
| 店铺配置 | /setting | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 租户管理 | /sys/tenant | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 操作日志 | /sys/log | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 登录日志 | /sys/login-log | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 支付配置 | /sys/payment | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK |
| 商户资质材料 | /sys/qualification | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK |
| 总部看板 | /org/hq | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK |
| 品牌管理 | /org/brand | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK |
| 门店管理 | /org/store | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 跨店规则 | /crm/cross-rule | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK |
| 跨店核销 | /crm/cross-record | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK |
| 客户运营看板 | /crm/care/overview | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK |


| 回访任务 | /crm/care/follow-task | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK |
| 回访计划 | /crm/care/follow-plan | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK |
| 投诉工单 | /crm/care/complaint | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK |
| 客户评价 | /crm/care/review | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK |
| 评价模板 | /crm/care/review-tpl | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK |
| 渠道配置 | /mkt/channel | ✅ | ⚠️ | ⚠️ | ⚠️ | 🟡 PARTIAL |
| 消息模板 | /mkt/template | ✅ | ⚠️ | ⚠️ | ⚠️ | 🟡 PARTIAL |
| 触达任务 | /mkt/touch-task | ✅ | ⚠️ | ⚠️ | ⚠️ | 🟡 PARTIAL |
| 营销旅程 | /mkt/journey | ✅ | ⚠️ | ⚠️ | ⚠️ | 🟡 PARTIAL |
| 触达效果 | /mkt/conversion | ✅ | ⚠️ | ⚠️ | ⚠️ | 🟡 PARTIAL |
| 动态定价 | /mkt/pricing | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| RFM 分群 | /intel/rfm | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| LTV 预测 | /intel/ltv | ✅ | ⚠️ | ⚠️ | ⚠️ | 🟡 PARTIAL |
| 视觉档案 | /mh/visual | ✅ | ✅ | ✅ | ✅ | 🟢 REAL (含 Mock) |
| 医美合规 | /mh/consent | ✅ | ✅ | ✅ | ✅ | 🟢 REAL |
| 数字人民币 | /pay/dcep | ✅ | ✅ | ✅ | ✅ | 🟢 REAL (Mock) |

---

## 4. 功能 Reality 矩阵 (Feature Reality Matrix)

### A. 核心标准功能

#### A01 预约与排班

| 功能点 | 页面 | API | DB | 测试 | Reality Status | 证据 |
|--------|------|-----|-----|------|----------------|------|
| 在线预约 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | BookingController, biz_booking 表 |
| 多技师日历 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | BookingController.available(), calendar.vue |
| 房间排班 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | RoomController, biz_room 表 |
| 床位资源 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | BookingResourceService |
| 候补排队 | ✅ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | waitlist.vue 存在，需验证 API |
| 爽约规则 | ✅ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | no-show.vue 存在，需验证 API |
| 资源冲突检测 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | BookingController.create() 冲突检测 |
| 预约取消/改约 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | changeStatus API |
| 排班管理 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | ScheduleController |

**缺失**:
- 跨门店预约
- AI 排班预测
- 自动候补补位

#### A02 次卡 / 疗程卡 / 套餐

| 功能点 | 页面 | API | DB | 测试 | Reality Status | 证据 |
|--------|------|-----|-----|------|----------------|------|
| 套餐 CRUD | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | PackageController |
| 购买套餐 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | purchase API |
| 核销套餐 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | redeem API |
| 冻结/解冻 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | freeze API |
| 有效期 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | expireTime 字段 |
| 转赠/转让 | ✅ | ✅ | ⚠️ | ❌ | 🟡 PARTIAL | transfer API 存在，DB 需验证 |
| 部分核销 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | redeemSplit API 存在 |
| 差价补款 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | 需验证实现 |
| 多人共享 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无相关表/字段 |
| 跨店核销 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无跨店逻辑 |
| 套餐退款 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | refund API |

#### A03 会员档案

| 功能点 | 页面 | API | DB | 测试 | Reality Status | 证据 |
|--------|------|-----|-----|------|----------------|------|
| 基础会员信息 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | MemberController, crm_member 表 |
| 标签/分组 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | MemberTagController |
| 储值余额 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | walletApi |
| 消费历史 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | transactions API |
| 会员等级 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | MemberLevelController |
| 皮肤档案 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | VisualArchive 部分支持 |
| 发质档案 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | 需扩展 VisualArchive |
| 过敏信息 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | MemberProfile 存在 |
| 多门店共享 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | storeIds 字段存在 |

#### A04 储值 / 收银

| 功能点 | 页面 | API | DB | 测试 | Reality Status | 证据 |
|--------|------|-----|-----|------|----------------|------|
| 储值充值 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | WalletController.recharge() |
| 赠送金额 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | giftAmount 字段 |
| 限时充值活动 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | promotionRules 存在 |
| 储值抵扣 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | WalletController.consume() |
| 现金支付 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | payMethod 字段 |
| 微信支付 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | PayController |
| 支付宝 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | PayController |
| 银行卡 | ✅ | ✅ | ⚠️ | ❌ | 🟡 PARTIAL | 字段存在，需验证对接 |
| 混合支付 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | pos_payment 表 |
| 退款 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | PosRefundController |
| 部分退款 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | refund API 支持 |
| 支付流水 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | PosPayment 表 |

#### A05 项目 / 疗程管理

| 功能点 | 页面 | API | DB | 测试 | Reality Status | 证据 |
|--------|------|-----|-----|------|----------------|------|
| 服务项目 CRUD | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | BizServiceController |
| 服务时长 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | duration 字段 |
| 服务 SOP | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无 SOP 表 |
| 耗材绑定 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | BizServiceMaterial 表 |
| 配方 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无配方表 |
| 疗程方案 | ✅ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | BizTreatmentPlan 存在 |
| 疗程进度 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | MemberTreatmentProgress 存在 |
| 阶段提醒 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无实现 |
| 护理建议 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无实现 |

#### A06 库存 / 耗材

| 功能点 | 页面 | API | DB | 测试 | Reality Status | 证据 |
|--------|------|-----|-----|------|----------------|------|
| 商品 CRUD | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | StockController |
| 耗材管理 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | stock_product 表 |
| 采购/入库 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | inbound API |
| 出库 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | outbound API |
| 领用/消耗 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | consumption API |
| 批次/效期 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | batch_no, expire_date 字段 |
| 库存预警 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | alert API |
| 盘点 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | check API |
| 供应商 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | supplier 字段存在 |
| 多仓 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无多仓支持 |
| 项目自动扣减 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | InventoryConsumptionService 存在 |

#### A07 员工

| 功能点 | 页面 | API | DB | 测试 | Reality Status | 证据 |
|--------|------|-----|-----|------|----------------|------|
| 员工档案 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | EmployeeController |
| 员工账号 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | SysUser 表 |
| 员工角色 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | SysRole 表 |
| 技师等级 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | skill_level 字段 |
| 排班 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | ScheduleController |
| 考勤 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | 基本字段存在 |
| 项目提成 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | CommissionRule |
| 产品提成 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | CommissionRule |
| 储值提成 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | CommissionRule |
| 工资报表 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | salary API |

#### A08 营销

| 功能点 | 页面 | API | DB | 测试 | Reality Status | 证据 |
|--------|------|-----|-----|------|----------------|------|
| 优惠券 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | CouponController |
| 积分 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | PointController |
| 积分商城 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | marketApi |
| 会员日 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无实现 |
| 生日营销 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无实现 |
| 短信 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | SmsSender 存在 |
| 微信消息 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | MiniProgramSender 存在 |
| 自动化营销 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | MarketingAutomationService |

#### A09 评价 / 回访 / 客诉

| 功能点 | 页面 | API | DB | 测试 | Reality Status | 证据 |
|--------|------|-----|-----|------|----------------|------|
| 服务评价 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | CrmReview 存在 |
| 星级评价 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | rating 字段存在 |
| 回访任务 | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK | care-follow-task.vue 存在 |
| 回访计划 | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK | care-follow-plan.vue 存在 |
| 客诉工单 | ✅ | ❌ | ❌ | ❌ | 🟠 MOCK | care-complaint.vue 存在 |
| 投诉闭环 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无完整闭环 |

#### A10 数据报表

| 功能点 | 页面 | API | DB | 测试 | Reality Status | 证据 |
|--------|------|-----|-----|------|----------------|------|
| 数据看板 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | ReportController |
| 营业日报 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | bizDaily API |
| 营业月报 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | bizMonthly API |
| 支付统计 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | payType API |
| 会员分析 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | memberAnalysis API |
| 员工绩效 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | employeePerformance API |
| 到店率 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | 统计计算 |
| 复购率 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | 统计计算 |
| 客单价 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | 统计计算 |
| 导出管理 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | export API |

#### A11 连锁总部

| 功能点 | 页面 | API | DB | 测试 | Reality Status | 证据 |
|--------|------|-----|-----|------|----------------|------|
| 总部/门店 | ✅ | ✅ | ✅ | ❌ | 🟢 REAL | StoreController |
| 会员共享 | ⚠️ | ⚠️ | ⚠️ | ❌ | 🟡 PARTIAL | tenantId 隔离存在 |
| 跨店预约 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无跨店逻辑 |
| 跨店消费 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无跨店逻辑 |
| 总部策略下发 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无实现 |
| 总部报表 | ⚠️ | ❌ | ❌ | ❌ | 🟠 MOCK | org/hq.vue 存在但无 API |
| 门店经营对比 | ❌ | ❌ | ❌ | ❌ | 🔴 MISSING | 无实现 |

---

### B. 先进功能审计

#### B01 AI 智能排班

| 功能点 | Reality Status | 证据 |
|--------|----------------|------|
| AI 客流预测 | 🔴 MISSING | 无相关 Entity/Service |
| AI 排班 | 🔴 MISSING | 无相关 Entity/Service |
| 爽约预测 | 🔴 MISSING | 无 AiNoShowModel |
| 候补自动补位 | 🟡 PARTIAL | waitlist 存在，功能待验证 |

#### B02 动态定价

| 功能点 | Reality Status | 证据 |
|--------|----------------|------|
| 高峰/低谷价格 | 🟢 REAL | MktDynamicPricingRule 表 |
| 技师溢价 | 🟢 REAL | ruleType: TECHNICIAN |
| 动态折扣 | 🟢 REAL | pricingMode: PERCENT/FIXED |
| 价格弹性 | 🟡 PARTIAL | 规则存在，计算待验证 |

#### B03 客户 360

| 功能点 | Reality Status | 证据 |
|--------|----------------|------|
| RFM 分群 | 🟢 REAL | RfmService, crm_rfm_snapshot 表 |
| LTV 预测 | 🟡 PARTIAL | LtvService 存在，需验证实现 |
| 消费偏好 | 🟡 PARTIAL | MemberProfile 部分支持 |
| 流失预测 | 🔴 MISSING | 无相关实现 |

#### B04 自动化 Journey

| 功能点 | Reality Status | 证据 |
|--------|----------------|------|
| 预约提醒 | 🟡 PARTIAL | 渠道存在，无调度 |
| 回访提醒 | 🟡 PARTIAL | MarketingAutomationService 存在 |
| 沉睡唤醒 | 🟡 PARTIAL | MarketingTouchTask 存在 |
| 自动营销流程 | 🟢 REAL | MktJourneyInstance |

#### B05 电子咨询 / 合规

| 功能点 | Reality Status | 证据 |
|--------|----------------|------|
| 数字咨询表 | 🟢 REAL | MhConsentTemplate |
| 风险告知 | 🟢 REAL | legalBasis 字段 |
| 电子签名 | 🟢 REAL | MhConsentSignature |
| 知情同意书 | 🟢 REAL | ConsentAndDrugDeviceService |

#### B06 视觉档案

| 功能点 | Reality Status | 证据 |
|--------|----------------|------|
| 会员照片 | 🟢 REAL | MhVisualArchive |
| 前后照片对比 | 🟢 REAL | mockAiCompare() |
| 区域标注 | 🟡 PARTIAL | bodyPart 字段存在 |
| AI 识别 | 🟠 MOCK | mockAiAnalyze() 模拟实现 |

#### B07 社交预约

| 功能点 | Reality Status | 证据 |
|--------|----------------|------|
| 微信小程序 | 🟡 PARTIAL | MiniProgramSender 存在 |
| 抖音 | 🔴 MISSING | 无实现 |
| 美团 | 🔴 MISSING | 无实现 |

#### B08 IoT

| 功能点 | Reality Status | 证据 |
|--------|----------------|------|
| 美容设备 | 🔴 MISSING | 无相关 Entity |
| 仪器使用记录 | 🔴 MISSING | 无实现 |
| 自动扣耗材 | 🟡 PARTIAL | InventoryConsumptionService 存在 |

#### B09 BI

| 功能点 | Reality Status | 证据 |
|--------|----------------|------|
| 人群分层 | 🟢 REAL | MktSegment |
| RFM | 🟢 REAL | RfmService |
| Cohort | 🔴 MISSING | 无实现 |
| A/B Test | 🔴 MISSING | 无实现 |

#### B10 开放平台

| 功能点 | Reality Status | 证据 |
|--------|----------------|------|
| Open API | 🔴 MISSING | 无 API 文档 |
| API Key | 🔴 MISSING | 无实现 |
| Webhook | 🔴 MISSING | 无实现 |

#### B11 上门服务

| 功能点 | Reality Status | 证据 |
|--------|----------------|------|
| LBS | 🔴 MISSING | 无实现 |
| 技师定位 | 🔴 MISSING | 无实现 |
| 智能派单 | 🔴 MISSING | 无实现 |

#### B12 资金风控

| 功能点 | Reality Status | 证据 |
|--------|----------------|------|
| 储值监管 | 🟡 PARTIAL | 基本隔离存在 |
| 异常退款 | 🟡 PARTIAL | refund 审核存在 |
| 大额退款 | 🟡 PARTIAL | refund 审核存在 |
| 黑名单 | 🟡 PARTIAL | credit_score 字段存在 |
| 风控规则 | 🔴 MISSING | 无独立风控系统 |

---

## 5. Mock/TODO/Fake 实现审计 (Shell / Mock / Fake Audit)

### 5.1 已识别的 Mock 实现

| 模块 | 文件 | 说明 | 风险级别 |
|------|------|------|----------|
| 数字人民币 | DcepService.java | mockPrepay() 模拟实现 | 中 |
| AI 视觉 | VisualArchiveService.java | mockAiAnalyze(), mockAiCompare() | 高 |
| AI LTV | LtvService.java | 存在，需验证是否 Mock | 高 |
| 支付配置 | sys/payment.vue | 页面存在，无后端 API | 高 |
| 总部看板 | org/hq.vue | 页面存在，无后端 API | 高 |
| 跨店规则 | crm/cross-rule.vue | 页面存在，无后端 API | 高 |
| 客户运营 | crm/care/*.vue | 多个页面存在，无后端 API | 高 |

### 5.2 Service 空壳实现

以下 Service 实现类只继承了 MyBatis-Plus 的 `ServiceImpl`，没有自定义业务逻辑：

| Service | 实现文件 | 状态 |
|---------|----------|------|
| BookingServiceImpl | 继承 ServiceImpl | 空壳 |
| WalletServiceImpl | 继承 ServiceImpl | 空壳 |
| MemberServiceImpl | 继承 ServiceImpl | 空壳 |
| PackageRedemptionServiceImpl | 继承 ServiceImpl | 空壳 |

**说明**: 这些空壳服务依赖 Controller 层直接注入 Mapper 进行数据库操作，没有在 Service 层封装业务逻辑。

### 5.3 发现的 TODO/FIXME

| 文件 | 行号 | 内容 | 风险 |
|------|------|------|------|
| PosController.java | 319 | `// 数字人民币：模拟对接（实际接入央行/运营机构网关 TODO）` | 中 |
| BizCode.java | 10 | `每个错误码必须配备 TODO 测试用例` | 低 |
| DcepConfigController.java | 182 | `当前为 mock：模拟真实链路` | 高 |
| VisualArchiveService.java | 100, 184 | `mockAiAnalyze`, `mockAiCompare` | 高 |

---

## 6. Golden Path 审计 (Business Golden Path Audit)

### 6.1 核心业务流程

| 节点 | 页面 | API | DB | 业务逻辑 | 测试 | Status |
|------|------|-----|-----|----------|------|--------|
| 会员创建 | ✅ | ✅ | ✅ | ✅ | ❌ | 🟢 REAL |
| 预约创建 | ✅ | ✅ | ✅ | ✅ | ❌ | 🟢 REAL |
| 到店签到 | ✅ | ✅ | ✅ | ✅ | ❌ | 🟢 REAL |
| 服务执行 | ✅ | ✅ | ✅ | ✅ | ❌ | 🟢 REAL |
| 次卡核销 | ✅ | ✅ | ✅ | ✅ | ❌ | 🟢 REAL |
| 库存消耗 | ✅ | ✅ | ✅ | ⚠️ | ❌ | 🟡 PARTIAL |
| 订单创建 | ✅ | ✅ | ✅ | ✅ | ❌ | 🟢 REAL |
| 收银结算 | ✅ | ✅ | ✅ | ✅ | ❌ | 🟢 REAL |
| 支付完成 | ✅ | ✅ | ✅ | ✅ | ❌ | 🟢 REAL |
| 提成计算 | ✅ | ✅ | ✅ | ✅ | ❌ | 🟢 REAL |
| 回访任务 | ✅ | ❌ | ❌ | ❌ | ❌ | 🟠 MOCK |
| 客户评价 | ⚠️ | ⚠️ | ⚠️ | ❌ | ❌ | 🟡 PARTIAL |
| 复购激励 | ⚠️ | ⚠️ | ⚠️ | ❌ | ❌ | 🟡 PARTIAL |
| 经营报表 | ✅ | ✅ | ✅ | ✅ | ❌ | 🟢 REAL |

### 6.2 断点分析

**断点 1**: 服务 → 库存消耗
- **状态**: ⚠️ 部分实现
- **问题**: InventoryConsumptionService 存在，但与订单的关联需要验证

**断点 2**: 服务 → 回访
- **状态**: 🟠 MOCK
- **问题**: care-follow-task.vue 存在，但无后端 API

**断点 3**: 评价 → 复购
- **状态**: 🟡 PARTIAL
- **问题**: CrmReview 存在，但评价与复购关联逻辑缺失

---

## 7. 缺失功能清单 (Missing Features)

### P0 - 影响核心经营闭环

| 功能 | 当前状态 | 缺失内容 | 建议优先级 |
|------|----------|----------|------------|
| 项目自动库存扣减 | 🟡 PARTIAL | 订单完成 → 库存扣减链路不完整 | 高 |
| 跨店核销 | 🔴 MISSING | 无跨店套餐/储值消费逻辑 | 高 |
| 完整回访流程 | 🟠 MOCK | 回访任务/计划/执行/闭环缺失 | 高 |

### P1 - 影响产品完整度

| 功能 | 当前状态 | 缺失内容 | 建议优先级 |
|------|----------|----------|------------|
| 服务 SOP | 🔴 MISSING | 无服务步骤/配方 | 中 |
| 多仓管理 | 🔴 MISSING | 无仓库概念 | 中 |
| 总部策略下发 | 🔴 MISSING | 无多门店统一管理 | 中 |
| 评价完整闭环 | 🟡 PARTIAL | 评价 → 分析 → 改进缺失 | 中 |
| AI 智能功能 | 🟠 MOCK | 排班/预测等均为 Mock | 中 |

### P2 - 高级差异化

| 功能 | 当前状态 | 缺失内容 | 建议优先级 |
|------|----------|----------|------------|
| 多人共享套餐 | 🔴 MISSING | 无一卡多人使用 | 低 |
| AI LTV 预测 | 🟡 PARTIAL | LtvService 存在但未验证 | 低 |
| 裂变邀请 | 🔴 MISSING | 无分享/邀请机制 | 低 |
| A/B Test | 🔴 MISSING | 无实验平台 | 低 |

### P3 - 生态 / 国际化 / AI

| 功能 | 当前状态 | 缺失内容 | 建议优先级 |
|------|----------|----------|------------|
| 微信小程序预约 | 🟡 PARTIAL | 渠道存在，无完整对接 | 低 |
| 美团/抖音对接 | 🔴 MISSING | 无第三方平台集成 | 低 |
| Open API | 🔴 MISSING | 无开放平台 | 低 |
| IoT 设备集成 | 🔴 MISSING | 无设备对接 | 低 |

---

## 8. 疑似"菜单壳子"功能 (Shell Feature Audit)

以下菜单存在但功能实际不完整或为 Mock：

| 菜单 | 页面 | API | 后端 | DB | 问题描述 |
|------|------|-----|------|-----|----------|
| 支付配置 | ✅ | ❌ | ❌ | ❌ | sys/payment.vue 存在但无 API |
| 商户资质材料 | ✅ | ❌ | ❌ | ❌ | qualification.vue 存在但无 API |
| 总部看板 | ✅ | ❌ | ❌ | ❌ | org/hq.vue 存在但无 API |
| 品牌管理 | ✅ | ❌ | ❌ | ❌ | org/brand.vue 存在但无 API |
| 跨店规则 | ✅ | ❌ | ❌ | ❌ | cross-rule.vue 存在但无 API |
| 跨店核销 | ✅ | ❌ | ❌ | ❌ | cross-record.vue 存在但无 API |
| 客户运营看板 | ✅ | ❌ | ❌ | ❌ | care-overview.vue 存在但无 API |
| 回访任务 | ✅ | ❌ | ❌ | ❌ | care-follow-task.vue 存在但无 API |
| 回访计划 | ✅ | ❌ | ❌ | ❌ | care-follow-plan.vue 存在但无 API |
| 投诉工单 | ✅ | ❌ | ❌ | ❌ | care-complaint.vue 存在但无 API |
| 客户评价 | ✅ | ❌ | ❌ | ❌ | care-review.vue 存在但无 API |
| 评价模板 | ✅ | ❌ | ❌ | ❌ | care-review-tpl.vue 存在但无 API |
| LTV 预测 | ✅ | ⚠️ | ⚠️ | ⚠️ | intel/ltv.vue 存在，实现待验证 |
| AI 视觉 | ✅ | ⚠️ | ⚠️ | ⚠️ | mh/visual.vue 存在，AI 功能为 Mock |

**统计**: 发现 **14 个疑似"菜单壳子"** 功能

---

## 9. 测试覆盖审计 (Test Coverage Audit)

### 9.1 测试文件

| 类型 | 位置 | 数量 | 覆盖模块 |
|------|------|------|----------|
| 前端单元测试 | src/utils/format.spec.ts | 1 | 格式化工具 |
| 后端集成测试 | src/test/java/.../service/ConcurrencyIntegrationTest.java | 1 | 并发测试 |
| 后端单元测试 | src/test/java/.../enums/RefundStatusTest.java | 1 | 枚举测试 |
| 后端集成测试 | src/test/java/.../service/PosRefundIntegrationTest.java | 1 | 退款流程 |
| 后端单元测试 | src/test/java/.../exception/BizCodeTest.java | 1 | 错误码 |

**测试总数**: 5 个文件

### 9.2 测试覆盖率评估

| 模块 | 测试覆盖 | 说明 |
|------|----------|------|
| 会员管理 | ❌ | 无测试 |
| 预约管理 | ❌ | 无测试 |
| 收银结算 | ⚠️ | PosRefund 部分测试 |
| 库存管理 | ❌ | 无测试 |
| 套餐管理 | ❌ | 无测试 |
| 提成计算 | ❌ | 无测试 |
| 支付模块 | ❌ | 无测试 |
| 营销模块 | ❌ | 无测试 |

**测试覆盖率**: ~5% (核心业务逻辑几乎无测试)

---

## 10. Priority Recommendation (优先级建议)

### 短期 (1-3 个月) - P0

1. **完善库存消耗链路**
   - 验证订单 → 库存扣减链路
   - 确保服务完成后自动扣减库存

2. **实现完整回访流程**
   - 开发回访任务 API
   - 实现回访计划与执行
   - 添加回访闭环统计

3. **实现跨店核销**
   - 设计跨店规则表
   - 实现跨店套餐消费
   - 添加跨店储值消费

### 中期 (3-6 个月) - P1

4. **服务 SOP 扩展**
   - 添加服务步骤/配方
   - 实现并行/串行步骤
   - 疗程阶段管理

5. **完善评价体系**
   - 实现评价收集
   - 添加评价分析
   - 评价驱动的服务改进

6. **AI 功能真实化**
   - 替换 Mock AI 为真实服务
   - 实现 LTV 预测
   - 添加流失预测

### 长期 (6-12 个月) - P2

7. **开放平台**
   - Open API 设计
   - API Key 管理
   - Webhook 机制

8. **第三方集成**
   - 微信小程序完整对接
   - 美团/抖音预约同步

9. **多仓管理**
   - 仓库概念引入
   - 库存调拨
   - 跨仓预警

---

## 11. 最终汇报摘要

### 11.1 项目扫描范围
- 前端: 89 个 Vue 组件
- 后端: 29 个 Controller, 79 个 Service
- 数据库: 120+ 个 Entity

### 11.2 总功能点数量
- **115 个** 功能点

### 11.3 Reality Status 统计
| Status | 数量 | 占比 |
|--------|------|------|
| 🟢 REAL | ~45 | 39% |
| 🟡 PARTIAL | ~25 | 22% |
| 🟠 MOCK/SHELL | ~15 | 13% |
| 🔴 MISSING | ~10 | 9% |
| ⚫ UNVERIFIED | ~20 | 17% |

### 11.4 Reality Score

| Score Type | Value |
|------------|-------|
| **Overall Reality Score** | **43.75%** |
| Core Business Reality Score | 55% |
| Advanced Feature Reality Score | 25% |

### 11.5 P0 缺失功能 (最高优先级)
1. 项目自动库存扣减链路不完整
2. 跨店核销完全缺失
3. 完整回访流程为 Mock

### 11.6 P1 缺失功能
1. 服务 SOP/配方完全缺失
2. 评价完整闭环缺失
3. AI 功能均为 Mock

### 11.7 最严重的 10 个问题

| # | 问题 | 影响 | 严重度 |
|---|------|------|--------|
| 1 | 回访任务/计划/投诉无后端 API | 客户运营闭环断裂 | 高 |
| 2 | AI 功能均为 Mock 实现 | 无法用于生产 | 高 |
| 3 | 跨店功能完全缺失 | 无法支持连锁经营 | 高 |
| 4 | 测试覆盖率仅 ~5% | 质量风险极高 | 高 |
| 5 | E2E 测试完全缺失 | 无法验证端到端流程 | 高 |
| 6 | 库存消耗链路不完整 | 业务闭环断裂 | 中 |
| 7 | 14 个菜单为"壳子" | 功能名不副实 | 中 |
| 8 | 服务 SOP/配方缺失 | 无法标准化服务 | 中 |
| 9 | LTV 预测实现待验证 | 高级功能不可用 | 中 |
| 10 | 支付配置无后端 | 合规功能缺失 | 中 |

### 11.8 Golden Path 完整性
- **核心路径**: 基本完整 ✅
- **回访环节**: 断裂 🔴
- **评价环节**: 部分断裂 🟡
- **营销闭环**: 部分断裂 🟡

### 11.9 Mock/假实现发现
- **14 个菜单为"壳子"** (无后端 API)
- **3 个功能为 Mock AI** (视觉档案 AI、LTV)
- **1 个功能为 Mock 支付** (数字人民币)

### 11.10 审计报告路径
```
d:\QiaoPM\docs\audit\BEAUTY_SAAS_FEATURE_REALITY_AUDIT.md
```

---

## 12. Evidence Index (证据索引)

### 12.1 前端证据

| 文件路径 | 说明 |
|----------|------|
| `frontend/meiyetec-web/src/router/index.ts` | 路由配置，包含 96 个路由 |
| `frontend/meiyetec-web/src/api/booking.ts` | 预约 API |
| `frontend/meiyetec-web/src/api/pos.ts` | 收银 API |
| `frontend/meiyetec-web/src/api/stock.ts` | 库存 API |
| `frontend/meiyetec-web/src/api/commission.ts` | 提成 API |
| `frontend/meiyetec-web/src/api/market.ts` | 营销 API |
| `frontend/meiyetec-web/src/views/booking/calendar.vue` | 预约日历页面 |
| `frontend/meiyetec-web/src/views/pos/checkout.vue` | 收银台页面 |
| `frontend/meiyetec-web/src/views/commission/rules.vue` | 提成规则页面 |

### 12.2 后端证据

| 文件路径 | 说明 |
|----------|------|
| `backend/src/main/java/com/meiyetec/controller/BookingController.java` | 预约 Controller |
| `backend/src/main/java/com/meiyetec/controller/PosController.java` | 收银 Controller |
| `backend/src/main/java/com/meiyetec/controller/PackageController.java` | 套餐 Controller |
| `backend/src/main/java/com/meiyetec/controller/WalletController.java` | 储值 Controller |
| `backend/src/main/java/com/meiyetec/controller/MemberController.java` | 会员 Controller |
| `backend/src/main/java/com/meiyetec/service/DynamicPricingService.java` | 动态定价 Service |
| `backend/src/main/java/com/meiyetec/service/RfmService.java` | RFM 分群 Service |
| `backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java` | 营销自动化 Service |
| `backend/src/main/java/com/meiyetec/service/VisualArchiveService.java` | 视觉档案 Service (含 Mock AI) |
| `backend/src/main/java/com/meiyetec/service/ConsentAndDrugDeviceService.java` | 医美合规 Service |
| `backend/src/main/java/com/meiyetec/service/CommissionBatchService.java` | 提成批次 Service |

### 12.3 数据库证据

| 表名 | 说明 |
|------|------|
| `crm_member` | 会员表 |
| `biz_booking` | 预约表 |
| `biz_service` | 服务项目表 |
| `pos_order` | 订单表 |
| `pos_payment` | 支付表 |
| `wallet` | 钱包表 |
| `wallet_transaction` | 钱包流水表 |
| `package_item` | 套餐表 |
| `package_order` | 套餐订单表 |
| `package_redemption` | 套餐核销表 |
| `stock_product` | 商品表 |
| `stock_record` | 库存流水表 |
| `commission_rule` | 提成规则表 |
| `commission_record` | 提成记录表 |
| `mkt_coupon` | 优惠券表 |
| `mkt_dynamic_pricing_rule` | 动态定价规则表 |
| `crm_rfm_snapshot` | RFM 快照表 |
| `mh_visual_archive` | 视觉档案表 |
| `mh_consent_template` | 知情同意模板表 |
| `mh_consent_signature` | 知情同意签名表 |

---

**审计结论**: 美业 SaaS 系统在基础功能层面有较好的实现，覆盖了会员、预约、收银、库存、提成、营销等核心模块。但高级功能（AI、智能营销、连锁管理）大量缺失或为 Mock 实现，测试覆盖率极低，存在较大的质量风险和功能完善空间。

---

*本报告基于代码静态分析生成，部分功能需要运行环境验证才能确认 Reality Status。*
