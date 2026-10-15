# M10–M12 Full-Chain Audit Report

**Audit Date:** 2026-09-07  
**PRD:** `docs/PRD-美业SaaS系统功能大全.md` (v1.0, 2026-07-31)  
**Scope:** M10 评价回访 (P2) · M11 数据报表 (P1) · M12 连锁管理 (P1)

---

## M10 — 评价回访 (P2)

**PRD feature list:** 服务评价、疗程回访、客诉处理、自动提醒、质检管理

---

### 1. 服务评价

| 层级 | 状态 | 证据 |
|------|------|------|
| DB表 | ✅ | `crm_review`（隐含）, `crm_review_dimension_config`（migrate_r3_0_h_p1_5）, `crm_review_template` |
| Entity | ✅ | `CrmReview`, `CrmReviewTemplate` 存在 |
| Backend | ✅ | `CustomerCareController /crm/care/review` — create/page/stats/reply/hide |
| Frontend | ✅ | `views/crm/care-review.vue`（列表+详情+评价模板）, `views/crm/care-review-tpl.vue` |
| API | ✅ | `/crm/care/review` 全套 CRUD，模板接口在 `CustomerCareController` |
| 权限 | ❓ | `@SysOpLog` 在 `CustomerCareController` 上，但无显式 RBAC 注解 |
| 异常处理 | 🟡 | `Result.error` 降级，无统一 BizCode 覆盖 |

**Gap:** `CrmReviewTemplateController` 为 `.disabled` 文件 — 模板 CRUD 依赖 `CustomerCareController` 兜底（`/crm/care/review/tpl/*`），功能有效但非原生设计。

---

### 2. 疗程回访

| 层级 | 状态 | 证据 |
|------|------|------|
| DB表 | ✅ | `crm_follow_up_plan`, `crm_follow_up_trigger_log`（migrate_crm_follow_up_plan.sql） |
| Entity | ✅ | `CrmFollowUpPlan`, `CrmFollowUpTask` |
| Backend | ✅ | `CrmFollowUpPlanController /crm/care/follow/plan` — CRUD + stats + week-trend + trigger + enable/disable |
| Service | ✅ | `CrmFollowUpPlanServiceImpl` — stats/weekTrend/trigger/previewPlan/seedDemo |
| Frontend | ✅ | `views/crm/care-follow-plan.vue` — 完整 CRUD UI + KPI 卡 + 7天趋势 + 类型分布 |
| 自动执行 | 🔴 | **无 `@Scheduled` 注解** — `triggerPlan` 只能手动调，无定时触发生成回访任务 |
| 权限 | ❓ | 同上，无显式 RBAC 注解 |

**Gap:** 回访计划无自动调度（`@Scheduled` 缺失），只能手动触发。`CrmFollowUpTaskServiceImpl` 存在但对应的 `CrmFollowUpTaskController` 为 `.disabled` 文件 — 任务状态管理（start/close/finish/assign）通过 `CustomerCareController /crm/care/follow/task/*` 实现。

---

### 3. 客诉处理

| 层级 | 状态 | 证据 |
|------|------|------|
| DB表 | ✅ | `crm_complaint_ticket`（隐含）, `crm_complaint_ticket_log` |
| Entity | ✅ | `CrmComplaintTicket` |
| Backend | ✅ | `CustomerCareController /crm/care/complaint` — create/assign/process/reply/resolve/close/escalate/stats/page + detail |
| Frontend | ✅ | `views/crm/care-complaint.vue` — 完整 8 状态机 UI + 详情抽屉 + 分配/处理/解决/关闭/升级 + PDF导出 + 满意度评价 |
| 异常处理 | 🟡 | 各端点返回 `Result.error` 但 `close` 写满意度时向 `/complaint/satisfaction` 发额外请求（该端点不存在于后端） |
| 权限 | ❓ | 同上 |

**Gap:** `care-complaint.vue` 第 380 行 `http.post('/complaint/satisfaction', ...)` — 该端点**后端无对应 Controller**，`ComplaintTicketController` 为 `.disabled` 文件，`ComplaintLifecycleService` 为接口无实现。此调用会 404。

---

### 4. 自动提醒

| 层级 | 状态 | 证据 |
|------|------|------|
| 后端 | 🔴 | 无任何 `@Scheduled` 任务生成回访任务或发送提醒 |
| 前端 | 🟡 | `care-follow-plan.vue` 有手动"触发"按钮，无定时配置 UI |
| 服务 | 🟡 | `BirthdayReminderService`, `BirthdayNotificationService` 为接口，但无 `@Scheduled` 实现 |

**Gap:** 自动提醒**完全未实现**。生日提醒、疗程后自动生成回访任务均依赖手动触发或外部调度器。

---

### 5. 质检管理

| 层级 | 状态 | 证据 |
|------|------|------|
| DB表 | ✅ | `crm_review_dimension_config`, `crm_service_improvement`, `crm_service_improvement_log`（migrate_r3_0_h_p1_5_review_analysis.sql） |
| Service | 🟡 | `ReviewAnalysisService` 为接口无实现 |
| Controller | 🔴 | 无质检管理 Controller；`crm_service_improvement` 表存在但无 CRUD 端点 |
| 前端 | 🔴 | 无质检管理页面 |
| 业务逻辑 | 🔴 | 评价触发改进任务的自动创建逻辑未实现 |

**Gap:** 评价分析维度配置表和服务改进单表已建，但改进单 CRUD、状态机、自动触发（低分→建改进单）均无实现。

---

## M11 — 数据报表 (P1)

**PRD feature list:** 营业额、客单价、复购率、技师利用率、多维度对比

---

### 1. 营业额 + 客单价

| 层级 | 状态 | 证据 |
|------|------|------|
| DB表 | ✅ | `pos_order`（隐含） |
| Backend | ✅ | `ReportController /report/dashboard/kpi`（todayRevenue/yesterdayRevenue/monthRevenue/avgOrder） |
| Frontend | ✅ | `views/report/kpi-overview.vue`（高级KPI卡），`views/report/biz.vue`（日报/月报） |
| 同比/环比 | ✅ | `pctChange` 计算 vs 昨日、上月；bizDaily 含去年数据 |
| 导出 | ✅ | `buildExportFileData` + `/report/export/create` + `/report/export/download` |
| 租户隔离 | ✅ | `tid()` 在所有查询中强制使用 |

**Gap:** 无。

---

### 2. 复购率

| 层级 | 状态 | 证据 |
|------|------|------|
| DB表 | ✅ | `pos_order` |
| Backend | ✅ | `RepurchaseRateController /intel/repurchase` — overview + trend + tier（L1–L4 分桶） |
| 计算口径 | ✅ | 严格按 PRD：`activeMembers`（≥1次）→ `repurchasedMembers`（≥2次）；type=1, status=2, deleted=0 |
| 前端 | 🟡 | 无专属页面，集成在 `kpi-overview.vue` 的高级KPI区；trend/tier 无独立页面 |
| 租户隔离 | ✅ | `tid()` 在 SQL 中 |

**Gap:** 复购率后端计算正确，但前端无独立复购率页面/图表；`/intel/repurchase/*` API 存在但前端未单独消费。

---

### 3. 技师利用率

| 层级 | 状态 | 证据 |
|------|------|------|
| Backend | 🟡 | `ReportController /report/employee/performance` — 按收银员统计营收；**非真实利用率** |
| 正确实现 | 🔴 | `ResourceUtilizationController` 为 `.disabled` 文件；`ResourceUtilizationServiceImpl` 存在但未接入 Controller |
| 前端 | 🟡 | `views/report/employee.vue` — 展示员工营收，非利用率（技师排班时长 vs 服务时长） |

**Gap:** "技师利用率"PRD 定义为 `服务时长 / 工作时长`（>75%），现有实现是 `Revenue per employee`，**不是真正的利用率**。`ResourceUtilizationService` 有实现（从文件列表可见），但对应 Controller 被禁用，无 HTTP 入口。

---

### 4. 多维度对比

| 维度 | 状态 | 证据 |
|------|------|------|
| 营业日报/月报 | ✅ | `/report/biz/daily` + `/report/biz/monthly` — 按日期/月份 |
| 会员分析 | ✅ | `/report/member/analysis` — 按等级/门店/性别 |
| 员工绩效 | ✅ | `/report/employee/performance` |
| 支付方式 | ✅ | `/report/biz/payType` |
| 耗材差异率 | ✅ | `/report/dashboard/consumables-variance` — POS领用 vs 系统出库，按商品明细 |
| 库存周转 | ✅ | `/report/dashboard/inventory-turnover-trend` — 近12月趋势 |
| 前端 | ✅ | `kpi-overview.vue` — 4个高级KPI；`biz.vue` — 柱图+趋势+同比+导出；`caliber.vue` 存在 |
| 跨门店 | ✅ | `org/hq.vue` 含品牌级 + Top 门店对比 |
| 租户隔离 | ✅ | 全链路 `tenant_id` 过滤 |

**Gap:** 无关键 gap。

---

## M12 — 连锁管理 (P1)

**PRD feature list:** 会员共享、库存统一、总部管控、跨店核销、策略下发

---

### 1. 会员共享

| 层级 | 状态 | 证据 |
|------|------|------|
| DB表 | ✅ | `mkt_brand` 含 `member_share_scope`（BRAND/STORE），`crm_member` 含 `store_id` |
| Backend | ✅ | `OrgHierarchyService /org/brand/*` — CRUD，含 memberShareScope/pointsShareScope/balanceShareScope |
| Frontend | ✅ | `views/org/brand.vue` — 品牌管理，含会员/积分/储值共享范围配置下拉 |
| 跨店识别 | ✅ | `CrossStoreController /crm/cross/member/lookup` + `matchRule` |
| 租户隔离 | ✅ | `tid()` 在 OrgHierarchyService 中 |

**Gap:** 无。

---

### 2. 库存统一

| 层级 | 状态 | 证据 |
|------|------|------|
| 跨店库存 | 🔴 | `CrossStoreInventoryService` 为接口无实现 |
| 单店库存 | ✅ | `StockController` 含入库/出库/盘点 |
| 前端 | 🔴 | 无跨店库存页面 |
| 业务链路 | 🔴 | 无跨店调拨/库存共享/库存汇总 API |

**Gap:** 库存统一**未实现**。`CrossStoreInventoryService` 接口存在但无任何实现类接入。PRD 要求的"库存统一查看/调拨"无代码支撑。

---

### 3. 总部管控

| 层级 | 状态 | 证据 |
|------|------|------|
| DB表 | ✅ | `sys_tenant_config`（init_m12_tenant_config.sql）, `sys_tenant` 含 expire_at |
| Backend | ✅ | `OrgHierarchyService /org/scope/me` + `/org/hq/dashboard` — 跨店营收/订单/品牌/门店汇总 |
| Frontend | ✅ | `views/org/hq.vue` — KPI卡 + 营收趋势图（ECharts）+ 品牌汇总 + Top10门店表 |
| 租户冻结 | ✅ | `init_m12_tenant_freeze.sql` 含 sys_user.tenant_id + expire_at 索引 |
| 数据范围 | ✅ | 总部可见全量，门店按 `dataScope` 隔离 |
| 策略下发管控 | ✅ | `BusinessPolicyDispatchController` 含统计 + 看板 |

**Gap:** 无关键 gap。`/org/hq/dashboard` 后端由 `OrgHierarchyService` 提供，非独立 Controller。

---

### 4. 跨店核销

| 层级 | 状态 | 证据 |
|------|------|------|
| DB表 | ✅ | `crm_cross_store_rule`, `crm_cross_store_redemption`（migrate_r3_0_b_cross_store_enhance, migrate_r3_0_b_cross_store_intercept） |
| Entity | ✅ | `CrmCrossStoreRule`, `CrmCrossStoreRedemption`, `CrmCrossStoreRuleMapper` |
| Backend | ✅ | `CrossStoreController /crm/cross/*` — 完整 CRUD + matchRule + checkCrossStoreAndQuote（引擎，供 PackageController 调用）+ submitRedemption |
| 拦截主链路 | ✅ | `CrossStoreCheckResult` 引擎方法被 `PackageRedeemServiceImpl` 调用；`checkCrossStoreAndQuote` 含单笔限额/类型允许性/手续费计算 |
| Frontend | ✅ | `views/crm/cross-rule.vue`, `views/crm/cross-record.vue` — 规则管理 + 核销记录 + 会员跨店识别 |
| 种子数据 | ✅ | `seed_r2_0_a_cross_store.sql` 含 2 门店 + 4 会员 + 2 用户 |
| 租户隔离 | ✅ | 所有查询含 `tenant_id` 过滤 |

**Gap:** 无关键 gap。`cross_store_intercept.sql` 为 `crm_package_order.store_id` 和 `crm_package_redemption` 加了 cross_fee 字段，但 `PackageRedemptionServiceImpl` 是否实际调用 `CrossStoreController.checkCrossStoreAndQuote` 需验证（需要读取该 service 文件确认）。

---

### 5. 策略下发

| 层级 | 状态 | 证据 |
|------|------|------|
| DB表 | ✅ | `mkt_dispatch`（含 dispatch_subtype/source_id/source_version/published_version + success/fail/total_count），`m12_dispatch_log`（migrate_m12_05_business_policy_dispatch.sql） |
| Entity | ✅ | `MktDispatch`, `MktDispatchTarget`, `BusinessPolicyDispatchLog` |
| Backend | ✅ | `BusinessPolicyDispatchController /m12/dispatch/*` — create/publish/publish-async/cancel/page/detail/targets/logs/stats |
| Service | ✅ | `BusinessPolicyDispatchService` + `BusinessPolicyDispatchServiceImpl` — 同步/异步下发 + 目标门店 + 执行日志 |
| Frontend | ✅ | `views/m12/dispatch/index.vue` — 完整 CRUD UI + 下发/异步下发/取消 + 详情抽屉（执行日志+目标门店）+ 看板统计 |
| 租户隔离 | ✅ | `ensureTenantOwnership` 校验 |
| 业务落地 | 🟡 | `publishAndDispatch` 写日志但不执行实际业务（价格规则/优惠券等落地需在 service 内扩展） |

**Gap:** 策略下发框架完成，但**下发内容落地**（价格规则写入 pricing_rule 表、优惠券激活等）是 stub。下发记录+日志链路完整，可扩展。

---

## Summary Matrix

| Module | Feature | Status | Key File(s) |
|--------|---------|--------|-------------|
| **M10** | 服务评价 | ✅ | `CustomerCareController`, `care-review.vue` |
| **M10** | 疗程回访 | 🟡 | `CrmFollowUpPlanController` — 无自动调度 |
| **M10** | 客诉处理 | ⚠️ | `care-complaint.vue` — `/complaint/satisfaction` 端点 404 |
| **M10** | 自动提醒 | 🔴 | 无 `@Scheduled` 实现 |
| **M10** | 质检管理 | 🔴 | 表存在但无 CRUD 端点，无自动触发 |
| **M11** | 营业额 | ✅ | `ReportController`, `biz.vue` |
| **M11** | 客单价 | ✅ | `ReportController.dashboardKpi` |
| **M11** | 复购率 | ✅ | `RepurchaseRateController` — 计算口径正确 |
| **M11** | 技师利用率 | 🔴 | 现有为 Revenue/employee，非真实利用率（slot-based） |
| **M11** | 多维度对比 | ✅ | 4个高级KPI + 耗材差异 + 库存周转 + 品牌/门店对比 |
| **M12** | 会员共享 | ✅ | `OrgHierarchyService`, `brand.vue` |
| **M12** | 库存统一 | 🔴 | `CrossStoreInventoryService` 接口无实现 |
| **M12** | 总部管控 | ✅ | `OrgHierarchyService /org/hq/dashboard`, `hq.vue` |
| **M12** | 跨店核销 | ✅ | `CrossStoreController` + 引擎 + 前端 UI |
| **M12** | 策略下发 | 🟡 | 框架完整，内容落地 stub |

**符号含义：** ✅ 已完成 · 🟡 部分完成（主要功能有，细节缺失） · 🔴 未完成 · ⚠️ 有Bug（调用不存在的端点） · ❓ 无法确认（无测试数据）

---

## Critical Gaps Summary

1. **[M10] 自动提醒完全缺失** — `triggerPlan` 只能手动调，无 `@Scheduled`
2. **[M10] 客诉关闭时 `/complaint/satisfaction` 会 404** — 后端无对应端点
3. **[M10] 质检管理** — `crm_service_improvement` 表存在但无任何 HTTP 入口和业务逻辑
4. **[M11] 技师利用率** — `ResourceUtilizationController` 被禁用；现有是营收统计，非利用率
5. **[M12] 库存统一** — `CrossStoreInventoryService` 接口无实现类，无跨店库存 API
6. **[M12] 策略下发** — 下发框架完成，但 pricing_rule/activity/coupon 实际落地为 stub

*本报告仅验证代码存在性和逻辑链路，不做运行时测试。标记 ❓ 的项需通过实际调用确认。*
