# QPM-004: 美业 SaaS 功能差距矩阵与 P4 入场决策报告

**报告编号**: QPM-004
**报告日期**: 2026-08-17
**编制依据**: QPM-003 Reality Re-Audit + QPM-002-02 E2E 覆盖率分析
**目标读者**: 产品负责人、工程负责人

---

## 1. Gap Summary Table

> 基于 QPM-003 的 28 模块 Reality 状态与 QPM-002-02 的 29 Controller E2E 覆盖数据，综合评估所有能力差距。

### 1.1 全部差距总览

| # | Category | Gap | Current State | Target State | Priority | P4 Fit | Notes |
|---|----------|-----|--------------|--------------|----------|--------|-------|
| G01 | 核心闭环 | **E2E 覆盖率不足** — Package/Wallet/Reconciliation/Commission 子模块无完整 E2E 链路 | 8/29 FULL, 9/29 PARTIAL, 12/29 NONE | 20+ controllers FULL，覆盖全部资金链路 | **P0** | **Y** | 涉及资金安全，必须 P4 前补齐 |
| G02 | 核心闭环 | **AI 智能排班** 缺失 — 无 AiSchedulerService / NoShowModel | MISSING | AiSchedulerService + 客流预测 + 自动补位 | **P0** | **Y** | 核心差异化能力，P4 候选 |
| G03 | 核心闭环 | **配方中心** 缺失 — 无独立配方表，无配方管理 API | MISSING | 独立配方表 + 配方管理 CRUD + 护理建议 | **P0** | **Y** | P4 候选，需配合 M19 实现 |
| G04 | 核心闭环 | **护理建议** 缺失 — CareSuggestionService 待实现 | MISSING | CareSuggestionService + 阶段提醒 + 个性化建议 | **P0** | **Y** | P4 候选，与配方中心强耦合 |
| G05 | 核心闭环 | **多人共享套餐** 缺失 — 无 sharer_id / owner_id 逻辑 | MISSING | PackageMember + 多人核销 + 份额管理 | **P0** | **Y** | 会员运营基础能力 |
| G06 | 核心闭环 | **会员生命周期营销** 缺失 — 生日提醒、会员日自动化、裂变邀请均 MISSING | 部分自动化(MISSING) | MarketingAutomationService 全链路 + 会员日触发 | **P1** | **Y** | P4 会员运营核心 |
| G07 | 高级能力 | **视觉档案 AI 识别** 为 MOCK — mockAiAnalyze / mockAiCompare 无真实 CV 模型 | MOCK (mock AI) | 接入真实 CV 模型 (人脸/皮肤/指甲) | **P1** | **N** | 属于 AI 深度方向，P5 |
| G08 | 高级能力 | **LTV/Churn 算法** PARTIAL — 服务存在但算法未用真实数据验证 | PARTIAL (未验证) | DataGovernanceService + 真实数据跑通 LTV/Churn | **P1** | **N** | 需数据成熟后 P5 |
| G09 | 国际化 | **礼品卡/订阅制** 完全缺失 — 无 gift_card / subscription_plan 表 | MISSING | gift_card + subscription_plan 完整生命周期 | **P1** | **N** | P5 国际化方向 |
| G10 | 国际化 | **Stripe/Square/BNPL** 境外支付缺失 | MISSING | StripeService + SquareService + BNPL 接入 | **P1** | **N** | P5 国际化方向 |
| G11 | 国际化 | **Google Booking / Instagram 预约** 缺失 | MISSING | ReserveWithGoogle + IG 预约 API 集成 | **P1** | **N** | P5 国际化方向 |
| G12 | 国际化 | **GDPR 合规** 缺失 — 无数据导出/删除/同意管理 API | UNVERIFIED | 数据导出/删除/同意管理完整实现 | **P1** | **N** | P5 国际化方向 |
| G13 | 差异化 | **上门服务 LBS** 缺失 — 无 LbsDispatchService / 技师定位 | MISSING | LbsDispatchService + 技师定位 + 派单逻辑 | **P2** | **N** | O2O 方向，P5+ |
| G14 | 差异化 | **美甲款式图库** 缺失 — 无 style_gallery 表 | MISSING | style_gallery + 快速下单 | **P2** | **N** | 美甲垂直场景，P5+ |
| G15 | 差异化 | **IoT 真实设备驱动** PARTIAL — IotService 框架有但无真实硬件对接 | PARTIAL (框架有) | IotService + 真实设备驱动 | **P2** | **N** | IoT 单独 track，P5+ |
| G16 | 差异化 | **自助签到 Kiosk** 缺失 | MISSING | Kiosk 自助流程 | **P2** | **N** | 到店体验优化，P5+ |
| G17 | 差异化 | **动态定价** AB 实验框架有但 NPS 联动改价 PARTIAL | REAL (基础) | NPS → 价格联动触发 | **P2** | **N** | 价格优化方向，P5 |
| G18 | 平台 | **插件市场** 架构缺失 — Open Platform 全栈有，插件化待完善 | PARTIAL | 开放生态模块化 | **P2** | **N** | 生态建设，P5+ |
| G19 | 平台 | **财税直连** PARTIAL — DcepConfigService 部分 | PARTIAL | 完整财税 API 直连 | **P2** | **N** | 集成方向，P5+ |
| G20 | 平台 | **电商集成** 缺失 — 微店/有赞/自营电商 | MISSING | 独立电商同步服务 | **P2** | **N** | 电商方向，P5+ |
| G21 | 合规 | **独立资金风控系统** 缺失 — 无 RiskControlService | MISSING | RiskControlService + 规则引擎 | **P2** | **N** | 风控增强，P5+ |
| G22 | 合规 | **并发症 SOP (医美)** 缺失 — 无 complication_sop 表 | MISSING | complication_sop + 应对流程 | **P2** | **N** | 医美合规，P5+ |
| G23 | 数据 | **Cohort 分析** 缺失 — 无 CohortAnalysisService | MISSING | CohortAnalysisService | **P2** | **N** | 数据分析，P5+ |
| G24 | 高级AI | **AI Business Copilot** 完全缺失 — 无 AI 辅助决策层 | MISSING | AI Business Copilot | **P2** | **N** | AI 深水区，P5 |
| G25 | 国际化 | **HIPAA 完整合规** PARTIAL — 医美数据加密+访问审计部分 | PARTIAL | 完整 HIPAA 合规 | **P3** | **N** | 美国市场，P6+ |
| G26 | 国际化 | **Google Maps 集成** 缺失 | MISSING | 国际化地图适配 | **P3** | **N** | P6+ |
| G27 | 国际化 | **海外 POS 终端对接** 缺失 | MISSING | Square/POS 硬件集成 | **P3** | **N** | P6+ |

### 1.2 按优先级分布

```
P0 (Must Fix) : 5 gaps   [G01-G05]  资金安全 + 核心闭环
P1 (Should)   : 7 gaps   [G06-G12]  差异化 + 国际化基础
P2 (Could)    : 14 gaps  [G13-G24]  竞争优势 + 平台生态
P3 (Future)   :  3 gaps  [G25-G27]  国际化深度
P4 Fit (Y)    : 6 items  [G01-G06]  全部 P0 + 会员生命周期
```

---

## 2. P4 Fit Analysis — 5 大候选功能深度分析

### 2.1 P4-01: AI 智能排班

| Field | Value |
|-------|-------|
| **Module** | M18 — 视觉档案 / AI 排班 |
| **Reality State** | 🔴 MISSING |
| **E2E Coverage** | ❌ NO — 无相关 Controller |
| **P4 Recommendation** | **✅ P4 候选 — 推荐纳入** |

#### Current State (from Reality Re-Audit)

- **AI 智能排班**: 无 `AiSchedulerService`、`NoShowModel` 或任何 AI 排班 Entity ✅ `BookingResourceService` 候补队列存在，AI 补位未实现
- **爽约预测 (NoShow)**: 完全 MISSING
- **AI 客流预测**: 完全 MISSING
- 候补队列从 MISSING → PARTIAL(队列存在，AI 补位缺失)

#### What's Needed to Make It REAL

1. **AiSchedulerService** — 排班优化引擎（需新建）
2. **NoShowModel** — 爽约预测模型（历史预约数据训练）
3. **客流预测** — 基于历史数据预测未来 N 天各时段客流
4. **排班建议生成** — 结合技师技能/偏好/客流生成排班方案
5. **AI 自动补位** — 爽约后 AI 自动从候补队列补位（已有队列，缺算法）

#### Dependencies

| 依赖模块 | 当前状态 | 是否就绪 |
|----------|----------|----------|
| BookingController | 🟢 REAL | ✅ 就绪 |
| EmployeeController | 🟢 REAL | ✅ 就绪 |
| ScheduleController | 🟢 REAL | ✅ 就绪 |
| BookingResourceService (候补队列) | 🟡 PARTIAL | ⚠️ 队列有，AI 补位缺 |
| 历史预约数据积累 | — | ⚠️ 需要真实数据 |

#### Estimated Complexity

| 维度 | 评估 |
|------|------|
| **后端新增 Controller/Service** | 1-2 个 (AiSchedulerController + AiSchedulerService) |
| **数据模型新增** | NoShowModel, ScheduleSuggestionModel |
| **前端页面** | 排班建议展示 + AI 确认/调整 UI |
| **算法复杂度** | 中高 — 需 ML 模型 (可以是规则+统计起步) |
| **依赖项** | 预约数据积累 (冷启动问题) |
| **总工时估算** | 中高 (8-12 周) |

#### Recommendation

**纳入 P4，推荐优先级 P1。**

理由：
- 是美业 SaaS 核心差异化功能，直接影响门店运营效率
- 候补队列已存在(PARTIAL)，只需叠加 AI 算法层
- 依赖的 Booking/Employee/Schedule 全部 REAL，就绪度高
- 建议 MVP：客流预测 + 排班建议（非全自动），降低复杂度

---

### 2.2 P4-02: 会员生命周期营销

| Field | Value |
|-------|-------|
| **Module** | M09 — 营销工具 / 会员管理 |
| **Reality State** | 🟢 REAL (基础) / 🔴 MISSING (自动化) |
| **E2E Coverage** | ⚠️ PARTIAL — 优惠券/活动路由可达，无完整链路 |
| **P4 Recommendation** | **✅ P4 候选 — 推荐纳入** |

#### Current State (from Reality Re-Audit)

- **基础优惠券**: 🟢 REAL — `CouponController` 存在
- **积分商城**: 🟢 REAL — `PointController` 存在
- **MarketingAutomationService**: 🟢 REAL (框架有，触发机制存在)
- **生日提醒**: 🔴 MISSING
- **会员日自动化**: 🔴 MISSING
- **裂变邀请**: 🔴 MISSING
- **ThirdPartyIntegrationService** (短信/微信): 🟡 PARTIAL

#### What's Needed to Make It REAL

1. **生日营销触发规则** — 会员生日 T-7/T-3/T-1 自动发送祝福+优惠券
2. **会员日自动化** — 每月固定日期自动发送会员日提醒
3. **复购旅程自动化** — T+30/T+60/T+90 未到店会员触发唤醒
4. **裂变邀请流程** — 邀请码 + 双方奖励 + 追踪
5. **自动化引擎完善** — MktJourneyInstance 已有，补全触达渠道

#### Dependencies

| 依赖模块 | 当前状态 | 是否就绪 |
|----------|----------|----------|
| MemberController | 🟢 REAL | ✅ 就绪 |
| CouponController | 🟢 REAL | ✅ 就绪 |
| PointController | 🟢 REAL | ✅ 就绪 |
| MarketingAutomationService | 🟢 REAL | ✅ 就绪 |
| ThirdPartyIntegrationService | 🟡 PARTIAL | ⚠️ 短信/微信 PARTIAL |
| RfmService | 🟢 REAL | ✅ 就绪 (会员分群) |
| MktJourneyInstance | 🟢 REAL | ✅ 就绪 |

#### Estimated Complexity

| 维度 | 评估 |
|------|------|
| **后端新增** | 自动化规则配置 UI + 触发引擎扩展 |
| **前端页面** | 会员生命周期看板 + 自动化规则配置 |
| **触达渠道** | 短信(PARTIAL)、微信(PARTIAL)、APP Push |
| **数据依赖** | 会员数据(RFM 已就绪) |
| **总工时估算** | 中 (6-10 周) |

#### Recommendation

**纳入 P4，推荐优先级 P1。**

理由：
- 是会员运营的核心能力，直接影响会员留存和复购
- 基础营销工具(MarketingAutomationService/优惠券/积分)已 REAL
- RFM 分群已 REAL(P1.6)，可作为会员分层触达的数据基础
- 与 AI 智能排班可并行开发，独立性强
- ROI 明确：会员生命周期管理直接驱动复购率提升

---

### 2.3 P4-03: 配方中心

| Field | Value |
|-------|-------|
| **Module** | M19 — 配方中心 |
| **Reality State** | 🔴 MISSING |
| **E2E Coverage** | ❌ NO — 无相关 Controller |
| **P4 Recommendation** | **✅ P4 候选 — 推荐纳入** |

#### Current State (from Reality Re-Audit)

- **独立配方表**: 🔴 MISSING
- **配方管理 API**: 🔴 MISSING
- **BizServiceMaterial** (耗材配方绑定): 🟢 REAL — 服务与材料关联已实装
- **配方展示/搜索**: 🔴 MISSING

#### What's Needed to Make It REAL

1. **配方主表** — `formula` 表（配方名称、类型、成分、适用肤质、步骤说明）
2. **配方管理 CRUD API** — 配方创建/编辑/禁用/搜索
3. **配方与服务绑定** — 配方 → 关联服务项目
4. **配方与材料关联** — 配方 → 耗材用量（BizServiceMaterial 已 REAL，可复用）
5. **配方分类管理** — 护理类型(面部/身体/美甲/美发)/肤质/季节

#### Dependencies

| 依赖模块 | 当前状态 | 是否就绪 |
|----------|----------|----------|
| BizServiceMaterial (耗材绑定) | 🟢 REAL | ✅ 就绪 (可复用) |
| BizServiceController (服务) | 🟡 PARTIAL | ⚠️ 服务 REAL，疗程 PARTIAL |
| 护理建议 (CareSuggestionService) | 🔴 MISSING | ❌ 需同步开发 |
| 会员肤质档案 | 🟡 PARTIAL (Visual Archive) | ⚠️ 视觉档案 PARTIAL |
| 配方数据库/标准配方集 | — | ❌ 需业务方提供 |

#### Estimated Complexity

| 维度 | 评估 |
|------|------|
| **后端新增** | FormulaController + FormulaService + 数据库表 |
| **数据模型** | formula, formula_ingredient, formula_category |
| **前端页面** | 配方列表/详情/编辑 + 配方搜索 |
| **关联系统** | 与服务管理、库存管理、护理建议深度耦合 |
| **业务依赖** | 需要行业专家提供标准配方集 |
| **总工时估算** | 中高 (8-12 周) |

#### Recommendation

**纳入 P4，推荐优先级 P2（依赖护理建议）。**

理由：
- 是美业服务标准化的核心基础设施
- 耗材配方绑定(BizServiceMaterial)已 REAL，可复用
- 但与护理建议(CareSuggestionService)强耦合，需同步开发
- 需要行业配方专家参与（业务门槛高）
- 建议：P4 上半年完成配方中心基础版，护理建议作为 P4 下半年 milestone

---

### 2.4 P4-04: 护理建议

| Field | Value |
|-------|-------|
| **Module** | M20 — 护理建议 |
| **Reality State** | 🔴 MISSING |
| **E2E Coverage** | ❌ NO — 无相关 Controller |
| **P4 Recommendation** | **✅ P4 候选 — 推荐纳入 (与配方中心捆绑)** |

#### Current State (from Reality Re-Audit)

- **CareSuggestionService**: 🔴 MISSING (待独立实现)
- **MemberTreatmentProgress**: 🟡 PARTIAL — 阶段进度部分支持
- **疗程阶段提醒**: 🟡 PARTIAL — 有部分支持
- **护理建议算法**: 🔴 MISSING

#### What's Needed to Make It REAL

1. **CareSuggestionService 独立实现** — 护理建议生成引擎
2. **会员档案关联** — 读取会员肤质/发质/历史护理记录
3. **配方中心联动** — 基于配方库生成个性化护理方案
4. **阶段进度追踪** — MemberTreatmentProgress 完善
5. **疗程提醒推送** — 基于护理周期自动提醒会员

#### Dependencies

| 依赖模块 | 当前状态 | 是否就绪 |
|----------|----------|----------|
| 配方中心 (Formula Center) | 🔴 MISSING | ❌ 需同步开发 |
| 会员档案 (Visual Archive) | 🟡 PARTIAL | ⚠️ 视觉档案 PARTIAL |
| RFM 分群 | 🟢 REAL | ✅ 可用于护理优先级分层 |
| MarketingAutomationService | 🟢 REAL | ✅ 疗程提醒可复用触达渠道 |
| MemberTreatmentProgress | 🟡 PARTIAL | ⚠️ 需完善 |

#### Estimated Complexity

| 维度 | 评估 |
|------|------|
| **后端新增** | CareSuggestionController + CareSuggestionService |
| **前端页面** | 护理建议展示 + 会员护理档案 + 进度追踪 |
| **算法复杂度** | 中高 — 规则引擎 + ML (取决于建议精准度要求) |
| **数据依赖** | 配方中心、会员档案、疗程进度 |
| **总工时估算** | 中高 (8-10 周) |

#### Recommendation

**纳入 P4，推荐优先级 P2，与配方中心捆绑开发。**

理由：
- 与配方中心强耦合，必须同步开发
- 会员护理建议是美业差异化的核心亮点
- 疗程阶段进度(MemberTreatmentProgress)已有 PARTIAL 基础
- RFM REAL 可用于护理优先级分层
- 建议：P4 中后期交付，配方中心 MVP 先上线

---

### 2.5 P4-05: 多人套餐共享

| Field | Value |
|-------|-------|
| **Module** | M04 — 套餐/次卡 |
| **Reality State** | 🟡 PARTIAL (基础链路有，共享缺失) |
| **E2E Coverage** | ⚠️ PARTIAL — 路由可达，无完整购买→核销→退款链路 |
| **P4 Recommendation** | **✅ P4 候选 — 推荐纳入** |

#### Current State (from Reality Re-Audit)

- **套餐购买**: 🟡 PARTIAL — PackageService 有，但 E2E 无完整购买链路
- **套餐核销**: 🟢 REAL — PackageRedemptionService + redeemSplit 部分核销
- **套餐退款**: 🟡 PARTIAL — 链路存在，E2E 未覆盖
- **跨店核销**: 🟢 REAL — CrossStoreService REAL (P2.2)
- **转赠/转让**: 🟡 PARTIAL
- **多人共享**: 🔴 MISSING — 无 sharer_id / owner_id 逻辑
- PackageController E2E 缺口：购买→核销→退款完整链路未覆盖

#### What's Needed to Make It REAL

1. **多人共享套餐模型** — `package_sharer` 表 + `owner_id` + `sharer_ids[]`
2. **份额管理 API** — 份额分配/回收/查看
3. **多人核销逻辑** — CAS 并发扣次 + 份额校验
4. **共享记录追溯** — 谁用了哪次、剩余次数、谁持有
5. **PackageController E2E 补齐** — 购买→核销→退款完整链路测试

#### Dependencies

| 依赖模块 | 当前状态 | 是否就绪 |
|----------|----------|----------|
| PackageController | 🟡 PARTIAL | ⚠️ 基础链路有，E2E 缺 |
| PackageRedemptionService | 🟢 REAL | ✅ 就绪 |
| CrossStoreService | 🟢 REAL | ✅ 就绪 |
| 会员管理 | 🟢 REAL | ✅ 多人共享需会员数据 |
| 钱包支付 | 🟢 REAL | ✅ 购买时需支付链路 |

#### Estimated Complexity

| 维度 | 评估 |
|------|------|
| **后端新增** | PackageShareService + ShareController (新增) |
| **数据模型** | package_sharer, package_share_log |
| **前端页面** | 套餐分享 UI + 份额管理 + 共享记录 |
| **并发安全** | CAS 扣次需处理多人同时核销 |
| **总工时估算** | 中 (5-8 周) |

#### Recommendation

**纳入 P4，推荐优先级 P1。**

理由：
- 是会员运营和家庭账户的核心能力，直接影响套餐销量
- 套餐核销(CrossStoreService)已 REAL，基础扎实
- 核心是新增共享逻辑，不需重构现有套餐体系
- 独立性强，可与 AI 排班/配方中心并行开发
- 涉及资金安全，P4 前需先补齐 PackageController E2E (G01)

---

## 3. P4 Entry Gate Criteria

> 定义 P4 正式启动前必须满足的条件。满足条件后，产品团队方可正式进入 P4 开发阶段。

### 3.1 必选条件 (Must Pass All)

| # | Gate Criteria | Current Status | 标准 |
|---|--------------|----------------|------|
| G1 | **E2E 通过率 >= 95%** | ✅ 98.3% (177/180) | PASS |
| G2 | **Reality Score >= 60%** | ✅ 63.9% | PASS (RC3 43.75% → 63.9%) |
| G3 | **核心资金链路 E2E FULL** | ⚠️ PARTIAL | Package/Wallet/Reconciliation E2E 待补 |
| G4 | **无 P0 安全问题** | ✅ 无已知 P0 | PASS |
| G5 | **SQL migrations 全部执行** | ✅ 假设通过 | 需生产环境验证 |
| G6 | **跨店核销 E2E 覆盖** | ⚠️ CrossStoreService REAL，E2E 缺 | 需补充 E2E |
| G7 | **会员生命周期基础链路** | ⚠️ MarketingAutomationService REAL，自动化缺 | 需补充 G06 |
| G8 | **配方中心 MVP 就绪** | 🔴 MISSING | P4 启动后才开始 |
| G9 | **护理建议 MVP 就绪** | 🔴 MISSING | P4 启动后才开始 |

### 3.2 P4 启动前 Checklist

```
【资金安全红线 — P4 前必须完成】
□ PackageController E2E FULL — 套餐购买→核销→退款完整链路
□ WalletController E2E FULL — 储值充值→消费→退款→余额一致性
□ ReconciliationController E2E FULL — 对账触发→查询→异常处理

【P4 候选功能就绪度】
□ AI 智能排班 — 候补队列已 PARTIAL，需完成 AiSchedulerService
□ 会员生命周期营销 — 生日提醒/会员日自动化 MVP
□ 配方中心 — 独立配方表 + CRUD API MVP
□ 护理建议 — CareSuggestionService MVP
□ 多人共享套餐 — PackageShareService MVP

【质量基线】
□ E2E 覆盖率 >= 40% (当前 27.6%)
□ 核心 Controller 全部有 PARTIAL+ E2E 覆盖
□ 无 P0/P1 Bug 积压
□ 性能基线达标 (P3 压测通过)
```

---

## 4. P5 Forward-Looking Section

> P5 = AI 深水区。P4 交付后，AI 能力进入深水区，需要真实业务数据支撑。

### 4.1 P5 Feature Readiness Matrix

| Feature | Current State | P5 Fit | P5 Prerequisites | Estimated Complexity |
|---------|--------------|--------|-----------------|---------------------|
| **AI 视觉识别** | 🟡 PARTIAL (mock AI) | ✅ Y | 需要配方中心数据(P4) + 真实 CV 模型接入 | 高 (12-16 周) |
| **LTV 预测** | 🟡 PARTIAL (算法有，未验证) | ✅ Y | 需要会员数据成熟(6+ 月历史) + DataGovernanceService | 中 (8-10 周) |
| **Churn 预测** | 🟡 PARTIAL (LtvService 含流失逻辑) | ✅ Y | 需要历史到店数据(12+ 月) | 中 (6-8 周) |
| **AI Business Copilot** | 🔴 MISSING | ✅ Y | 需要 P4 数据层就绪 + LLM API | 高 (16+ 周) |
| **IoT 联动** | 🟡 PARTIAL (框架有) | ❌ N | 属于硬件/IoT 独立 track，不属于 AI | 中 (需硬件) |
| **动态定价 AB 实验** | 🟡 PARTIAL (基础有) | ✅ Y | NPS → 价格联动触发引擎 | 中 (8 周) |
| **智能客流预测** | 🔴 MISSING | ✅ Y | 需要历史预约数据(6+ 月) | 中 (8-10 周) |
| **个性化推荐引擎** | 🔴 MISSING | ✅ Y | 需要配方中心(P4) + 会员画像 | 高 (12 周) |
| **智能催转化** | 🔴 MISSING | ✅ Y | 需要 MktJourneyInstance (REAL) + LLM | 中 (6-8 周) |

### 4.2 P5 触发条件

```
【P5 启动触发条件 — 必须全部满足】

1. P4 交付完成，且运行 3 个月以上
2. 会员数据积累 >= 6 个月历史
3. 预约数据积累 >= 6 个月历史
4. 配方中心数据 >= 100 条有效配方
5. LTV/Churn 算法用真实数据验证通过
6. AI 视觉识别 CV 模型选型完成
7. LLM API (GPT-4o / Claude) 合规审查通过
8. AI Ethics Review 完成 (数据隐私、算法公平性)
```

### 4.3 P5 非 AI 功能 (国际化深度)

| Feature | Current State | P5 Fit | Notes |
|---------|--------------|--------|-------|
| **Stripe/Square 境外支付** | 🔴 MISSING | ✅ Y | P5 国际化支付 |
| **Google Booking** | 🔴 MISSING | ✅ Y | P5 国际化预约 |
| **礼品卡/订阅制** | 🔴 MISSING | ✅ Y | P5 营收模式扩展 |
| **GDPR 合规** | ⚫ UNVERIFIED | ✅ Y | P5 欧盟市场准入 |
| **美甲款式图库** | 🔴 MISSING | ✅ Y | P5 美甲垂直场景 |
| **插件市场** | 🔴 MISSING | ✅ Y | P5 生态建设 |
| **电商集成** | 🔴 MISSING | ✅ Y | P5 营收扩展 |


---

## 5. RC4 Baseline Declaration

**Date**: 2026-08-17
**Commit**: 9e095ee (feat: QPM-002 QPM-003 E2E verification + Reality Re-Audit evidence)
**Reality Score**: 63.9%
**E2E Pass Rate**: 98.3% (177 passed / 1 failed / 2 skipped)
**E2E Coverage**: 8/29 Controllers FULL, 9/29 PARTIAL, 12/29 NONE

`
Gate Status: CONDITIONAL READY FOR P4

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Conditions met:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ✅ E2E Pass Rate 98.3% >= 95% threshold
  ✅ Reality Score 63.9% >= 60% threshold
  ✅ No P0 security issues
  ✅ Core booking → POS → Commission E2E FULL
  ✅ Cross-store redemption REAL
  ✅ RFM scoring REAL
  ✅ Dynamic pricing REAL
  ✅ Marketing automation framework REAL

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Conditions pending (P4 GATES — must resolve before P4 launch):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  🔴 G01: PackageController E2E FULL (资金安全红线)
  🔴 G01: WalletController E2E FULL (资金安全红线)
  🔴 G01: ReconciliationController E2E FULL (资金安全红线)
  🟡 G01: E2E Coverage 27.6% < 40% target
  🟡 G06: 会员生命周期营销 — 生日提醒/会员日自动化 MVP
  🟡 G02: AI 智能排班 MVP (候补队列 PARTIAL → AI 补位 REAL)
  🟡 G03: 配方中心 MVP
  🟡 G04: 护理建议 MVP (CareSuggestionService)
  🟡 G05: 多人共享套餐 MVP (PackageShareService)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
P4 Gate Decision:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ✅ Core metrics: READY
  ✅ Security baseline: READY
  ⏸  Fund security E2E: BLOCKED (3 controllers missing)
  ⏸  P4 features MVP: NOT STARTED (gates define scope)

  Verdict: Proceed to P4 gate clearing (fund security E2E),
           then P4 launch. All 5 candidate features recommended.
`

### 5.1 P4 Feature Recommendation Summary

| P4 Feature | Current State | Recommendation | Priority | Notes |
|-----------|--------------|----------------|----------|-------|
| **P4-01 AI 智能排班** | MISSING | ✅ 纳入 | P1 | 候补队列 PARTIAL，需叠加 AI 层 |
| **P4-02 会员生命周期营销** | PARTIAL | ✅ 纳入 | P1 | 基础营销 REAL，自动化缺 |
| **P4-03 配方中心** | MISSING | ✅ 纳入 | P2 | 与护理建议强耦合，同步开发 |
| **P4-04 护理建议** | MISSING | ✅ 纳入 | P2 | 与配方中心捆绑 |
| **P4-05 多人共享套餐** | PARTIAL | ✅ 纳入 | P1 | 基础链路 REAL，需补共享逻辑 |

### 5.2 P4 Not Recommended (Belongs to P5)

| Feature | Reason for Deferral |
|---------|-------------------|
| AI 视觉识别 (真实 CV) | 需配方中心数据 + CV 模型接入，P4 数据基础不足 |
| LTV/Churn 算法验证 | 需 6+ 月真实会员数据沉淀 |
| AI Business Copilot | 需 P4 完整数据层 + LLM 合规审查 |
| 国际化 (Stripe/GDPR/Google) | 独立 track，与 P4 国内聚焦冲突 |
| IoT 设备联动 | 硬件依赖，独立 track |

### 5.3 RC3 → RC4 Progress

| Metric | RC3 (2026-08-14) | RC4 (2026-08-17) | Delta |
|--------|-------------------|-------------------|-------|
| Reality Score | 43.75% | 63.9% | **+20.15 pp** |
| REAL modules | ~39% | 64.3% | **+25.3 pp** |
| E2E Pass Rate | N/A | 98.3% | **NEW** |
| E2E FULL coverage | 0% | 27.6% | **NEW** |
| Key gaps closed | — | 14 Shell 功能填补 | **+14 features** |
| P4 candidates ready | — | 5/5 recommended | **NEW** |

> **结论**: RC4 基线已达到 P4 入场核心指标(Reality Score >= 60%, E2E Pass >= 95%)，但资金安全 E2E 链路缺失为 P4 启动前的绝对阻塞项。P4 启动建议时间: 所有 G01 资金安全 E2E 补齐后。

---

*Gap Matrix 生成依据: QPM-003 Reality Re-Audit (28 模块) + QPM-002-02 E2E Coverage (29 Controllers)*
*下次 Gap Review 建议时间: P4 Gate 条件全部满足后，或 P4 Sprint 1 结束后*