# M04/M05/M06 PRD 功能审计报告

> **审计日期**：2026-09-07
> **审计范围**：M04 套餐/次卡/疗程卡 / M05 储值卡 / M06 项目/疗程管理
> **审计方法**：逐文件逐链路核查（Frontend → API → Service → Mapper → Entity → DB → Permission）
> **约束**：不修改任何代码，只报告发现

---

## 一、M04 套餐/次卡/疗程卡（PRD P0）

**涉及文件**：

| 层 | 路径 |
|---|---|
| 前端页面 | `frontend/meiyetec-web/src/views/package/` (index.vue / member-orders.vue / purchase-dialog.vue / share.vue) |
| 前端API | `frontend/meiyetec-web/src/api/package.ts` / `package-share.ts` |
| 后端控制器 | `backend/.../controller/PackageController.java` (75043 bytes) |
| 后端服务 | `SharedPackageService.java` / `PackageRedeemService.java` / `PackageItemService.java` |
| 后端实体 | `PackageItem / PackageOrder / PackageRedemption / PackageStatusLog` |
| 后端Mapper | `PackageOrderMapper` (含 CAS 方法) |
| SQL | `init_m05_package.sql` / `init_m05_package_part2.sql` / `init_m05_seed.sql` / `migrate_m04_04_package_suspend_status_log.sql` / `init_style_package.sql` |

---

### M04-① 项目组合

**PRD 定义**：一个套餐可包含多个服务项目（bundle）。

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 前端 | 🟡 部分 | `package/index.vue` 新增套餐表单**无多服务选择器**，只有名称/分类/总次数 |
| API | 🟡 部分 | `POST /packages` 仅接收 `totalTimes`（整数），不接收服务数组 |
| 后端实体 | 🔴 缺失 | `PackageItem` 无 `serviceIds`/`items` 字段；`crm_package` 表也无此列 |
| DB | 🔴 缺失 | `crm_package` 仅 `name / total_times / price`，无服务关联表 |
| 核销逻辑 | ⚠️ Bug | `doRedeem` 传入 `serviceItem` 仅是文字描述字符串，无法做服务范围校验 |

> **结论**：`total_times` 是全局次数而非"各服务各多少次"。无法区分"面部清洁 5 次 + 肩颈按摩 3 次"这样的多项目组合。**等同于单项目次卡，不是真正的项目组合**。

---

### M04-② 计次管理

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 前端 | ✅ 完成 | `member-orders.vue` 显示剩余次数，核销弹窗可输入核销次数 |
| API | ✅ 完成 | `POST /packages/redeem` → `doRedeem` 接收 `dto.times` |
| 后端逻辑 | ✅ 完成 | `casRedeem` CAS 原子减 `remaining_times`，`version` 乐观锁 |
| DB | ✅ 完成 | `crm_package_order.remaining_times` INT 列存在 |
| 幂等 | ✅ 完成 | `IdempotencyService` 包装，`Idempotency-Key` header 支持 |

---

### M04-③ 有效期

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 前端 | ✅ 完成 | `member-orders.vue` 显示 `expireTime`，过期行有红色警告 |
| API | ✅ 完成 | `GET /packages/member/{id}` 返回 `expireTime` |
| 后端逻辑 | ✅ 完成 | `doRedeem` 检查 `expireTime < now` → 标 `ORDER_EXPIRED` 后报错 |
| 续期 | ✅ 完成 | `POST /packages/order/{id}/extend` 支持顺延 N 天 |
| DB | ✅ 完成 | `crm_package_order.expire_time` DATETIME 列存在 |

---

### M04-④ 转让

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 后端逻辑 | ✅ 完成 | `POST /packages/order/{id}/transfer` 11 步校验 + `casTransfer` CAS 原子 |
| 权限 | ✅ 完成 | `@RequirePermission("package:transfer")` |
| 租户隔离 | ✅ 完成 | `src.tenantId != currentTenantId` → `BizCode.TENANT_DENIED` |
| 审计 | ✅ 完成 | `writeStatusLog` 写 `SUSPEND/TRANSFER` 动作（实际实现用了 `SUSPEND` action，但注释写的 `TRANSFER`） |
| 前端 | ⚠️ 有Bug | `member-orders.vue` 中**没有转让按钮**。前端仅支持购买/核销/冻结，**没有调用 `/order/{id}/transfer` 的 UI 入口** |
| SQL | ✅ 完成 | `status=6` 已转赠状态枚举已定义 |

> **结论**：后端链路完整，但前端**没有转让入口**，功能对用户不可见。

---

### M04-⑤ 冻结/解冻

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 后端逻辑 | ✅ 完成 | `POST /packages/order/{id}/freeze` 含 `casFreeze` / `casUnfreeze` CAS 原子 |
| 状态机 | ✅ 完成 | `ORDER_NORMAL=1` 才可冻结，`ORDER_FROZEN=4` 才可解冻 |
| 权限 | ✅ 完成 | `@RequirePermission("package:freeze")` |
| 审计日志 | ✅ 完成 | `writeStatusLog` → `crm_package_status_log` 表 |
| 前端 | ✅ 完成 | `member-orders.vue` 有冻结/解冻按钮（`handleFreeze`） |

---

### M04-⑥ 挂起/恢复

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 后端逻辑 | ✅ 完成 | `POST /packages/order/{id}/suspend` + `POST /packages/order/{id}/resume` |
| CAS | ✅ 完成 | `casSuspend` / `casResume` 原子操作 |
| 可选到期时间 | ✅ 完成 | `suspendUntil` 字段支持临时挂起 + 自动恢复 |
| 审计日志 | ✅ 完成 | `writeStatusLog` 写 `SUSPEND/RESUME` |
| 前端 | ⚠️ 有Bug | `member-orders.vue` 中**没有挂起/恢复按钮**，仅冻结/解冻 |

---

### M04-⑦ 部分核销

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 后端 | ✅ 完成 | `dto.times` 支持任意正整数，`casRedeem(orderId, version, times)` |
| 前端 | ✅ 完成 | `member-orders.vue` 核销弹窗有"核销次数"输入框 |
| CAS | ✅ 完成 | 乐观锁防止并发超扣 |

---

### M04-⑧ 差价补款/升级

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 后端逻辑 | ✅ 完成 | `POST /packages/upgrade` 完整实现：剩余价值计算 → 差价计算 → 钱包扣款/退款 → 新订单创建 |
| 降级退款 | ✅ 完成 | `diff < 0` → 退差价至钱包 |
| 前端 | ⚠️ 部分 | `member-orders.vue` 中**没有升级入口**。前端未暴露升级按钮 |

---

### M04 额外发现

| 项 | 状态 | 说明 |
|---|---|---|
| 款式库/材料包 (`init_style_package.sql`) | 🟡 部分 | `biz_style_library` / `biz_material_package` 表已创建，`StyleLibraryController` 为 **disabled**，前端 `style-gallery/index.vue` 走独立路由 |
| 共享套餐 (`SharedPackageService`) | ⚠️ 有Bug | 是 `@RestController` + `@RequestMapping`，但没有 `@RequirePermission`；共享套餐不在 `package/index.vue` 界面中出现，前端走 `package-share.ts` / `share.vue` |
| 部分核销（拆分核销） | 🟡 部分 | `PackageSplitRedeemDTO` 已定义，`redeem-split` endpoint 存在（PackageController 末尾被截断），前端 `member-orders.vue` **没有调用** |

---

## 二、M05 储值卡（PRD P0）

**涉及文件**：

| 层 | 路径 |
|---|---|
| 前端页面 | `frontend/meiyetec-web/src/views/wallet/` (recharge.vue / transactions.vue) |
| 后端控制器 | `backend/.../controller/WalletController.java` (12951 bytes) |
| 后端服务 | `WalletService.java` (接口) / `WalletServiceImpl.java` |
| 后端实体 | `Wallet / WalletTransaction / WalletPromotion` |
| SQL | `init_overseas_wallet_pay.sql` / `migrate_wallet_001_create_promotion_table.sql` / `migrate_v0_p0_money_002_wallet_reconciliation.sql` / `create_recharge_audit_tables.sql` |

---

### M05-① 充值

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 前端 | ✅ 完成 | `recharge.vue` 有完整充值表单：选会员 → 选金额 → 支付方式 |
| API | ✅ 完成 | `POST /wallet/recharge` → `IdempotencyService` 包装 |
| 后端 | ✅ 完成 | `WalletServiceImpl.doRecharge` → CAS 写 `crm_wallet.balance` + 写 `crm_wallet_transaction` |
| 租户隔离 | ✅ 完成 | `WalletController.ensureWallet` 要求 `tenantId` |
| 幂等 | ✅ 完成 | `Idempotency-Key` header 支持 |
| SSOT 同步 | ✅ 完成 | `syncMemberBalance` 在每次写操作后调用 |

---

### M05-② 赠送金额

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 规则表 | ✅ 完成 | `wallet_promotion` 表有 `recharge_amount` / `gift_amount` |
| 种子数据 | ✅ 完成 | 4 条全局规则（充100送10等） |
| 计算 | ✅ 完成 | `GET /wallet/promotion/calc` → 返回 `gift` + `total` |
| 前端展示 | ✅ 完成 | `recharge.vue` 显示"预计获赠 ¥XX" |
| 租户级规则 | ✅ 完成 | `WalletController.promotionRules` 优先租户规则，其次全局规则 |
| 实际发放 | ⚠️ Bug | **`doRecharge` 中没有自动叠加赠送金额的逻辑**。充值 100 元，实际只加 100，赠送金额需前端另行处理或手动叠加；`WalletOpDTO` 没有 `giftAmount` 字段 |

---

### M05-③ 消费抵扣

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 前端 | ✅ 完成 | `POS` 收银台可选择钱包支付 |
| API | ✅ 完成 | `POST /wallet/consume` |
| 后端 | ✅ 完成 | `WalletServiceImpl.doConsume` → CAS 扣款 + 写流水 |
| 余额校验 | ✅ 完成 | SQL `WHERE balance >= amount` CAS 保护 |
| SSOT | ✅ 完成 | `syncMemberBalance` 调用 |

---

### M05-④ 限时活动

| 链路环节 | 状态 | 证据 |
|---|---|---|
| DB | 🔴 缺失 | `wallet_promotion` 表**无开始/结束时间字段**，无 `start_time / end_time` 列 |
| 后端 | 🔴 缺失 | `promotionRules` / `calcPromotion` **不检查时间有效性**，返回所有 `status=1` 规则 |
| 前端 | 🔴 缺失 | `recharge.vue` 不显示"活动截止时间" |

> **结论**：充值赠送**无时间限制**，等同于常驻优惠。无法配置"12月圣诞活动充500送100"这种限时促销。

---

### M05-⑤ 余额冻结

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 实体字段 | 🟡 部分 | `Wallet.frozenAmount` DECIMAL 字段存在 |
| 充值审计表 | ✅ 完成 | `crm_recharge_audit` / `crm_recharge_audit_log` 表已建（`create_recharge_audit_tables.sql`） |
| 充值审核 | 🔴 缺失 | **`RechargeAuditController` 是 disabled**；`recharge.vue` 前端**无审核流程 UI**，充值直接到账 |
| 冻结金额操作 | 🔴 缺失 | **无 `freeze` / `unfreeze` 端点**；`WalletService` 接口无 `freezeBalance` 方法；`WalletTransaction.type=5/6`（冻结/解冻）定义了但无调用方 |
| 前端 | 🔴 缺失 | `recharge.vue` / `transactions.vue` 无冻结/解冻按钮 |

> **结论**：余额冻结功能**基本未实现**。`frozenAmount` 字段存在但从未被任何业务代码写入或读取。

---

### M05 额外发现

| 项 | 状态 | 说明 |
|---|---|---|
| 钱包漂移对账 | ✅ 完成 | `migrate_v0_p0_money_002_wallet_reconciliation.sql` 有完整 DRY-RUN + 修复 + 验证脚本 |
| 海外支付网关 | 🟡 部分 | `init_overseas_wallet_pay.sql` 有 Apple Pay / Google Pay 网关种子数据（占位符），但无真实 Stripe 集成代码 |
| CAS 重试上限 | ✅ 完成 | `WalletServiceImpl` `MAX_OPTIMISTIC_RETRY = 100`（P0-TEST-001 patch） |

---

## 三、M06 项目/疗程管理（PRD P1）

**涉及文件**：

| 层 | 路径 |
|---|---|
| 前端页面 | `frontend/meiyetec-web/src/views/care/` (index.vue) / `frontend/meiyetec-web/src/views/complication/` (index.vue) |
| 前端API | `frontend/meiyetec-web/src/api/care.ts` / `care-suggestion.ts` |
| 后端控制器 | `BizTreatmentService.java` / `BizTreatmentStepService.java` / `ComplicationService.java` / `CareSuggestionService.java` / `BizTreatmentReportController.java` |
| 后端服务 | `CareSuggestionService.java` / `TreatmentReportService.java` |
| 后端实体 | `CareRecipe / CareRecipeItem / CareSkinAssessment / CarePlan / CareFormula / CareSuggestion* / BizTreatmentPhase / BizTreatmentPhaseStep / MemberTreatmentProgress / MemberTreatmentSession / TreatmentReport / ComplicationSop / ComplicationEvent` |
| SQL | `init_care_recipe.sql` / `init_care_suggestion.sql` / `init_complication_sop.sql` / `init_treatment_report.sql` / `migrate_r3_0_h_p1_4_sop_step.sql` |

---

### M06-① 服务SOP

| 链路环节 | 状态 | 证据 |
|---|---|---|
| SOP 步骤定义 | ✅ 完成 | `BizTreatmentStepService` / `biz_treatment_phase_step` 表（`migrate_r3_0_h_p1_4_sop_step.sql`） |
| SOP 步骤执行日志 | ✅ 完成 | `member_treatment_step_log` 表定义完整 |
| 配方步骤 | ✅ 完成 | `care_recipe_item` 表存储护理步骤 |
| POS 执行时展示 | 🔴 缺失 | **服务执行时技师看不到 SOP 步骤**。`recordSession` 只记录打卡，不展示步骤；前端没有"服务执行页面"显示当前 SOP 步骤清单 |
| SOP 模板初始化 | ✅ 完成 | `CareSuggestionService.initDefaultRecipes` 创建示例配方 |
| SOP 与套餐联动 | 🔴 缺失 | 套餐核销 (`redeem`) 不关联 SOP；购买套餐后没有引导进入 SOP 执行流程 |

> **结论**：SOP 数据存在，但**与 POS 执行流程完全割裂**，技师在服务时无法看到 SOP 步骤指引。

---

### M06-② 阶段进度

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 阶段定义 | ✅ 完成 | `BizTreatmentPhase` + `biz_treatment_phase` 表 |
| 阶段创建 | ✅ 完成 | `POST /biz/treatment/plan` 内嵌阶段创建，校验 `total_sessions` 等于所有阶段之和 |
| 会员开疗程 | ✅ 完成 | `POST /biz/treatment/progress` |
| 到店打卡 | ✅ 完成 | `POST /biz/treatment/progress/{id}/session` → `checkAndAdvancePhase` 自动切换阶段 |
| 进度查询 | ✅ 完成 | `GET /biz/treatment/progress/member/{id}` 含进度百分比 |
| 前端展示 | 🟡 部分 | `care/index.vue` **无疗程进度 Tab**。无页面展示会员的 `member_treatment_progress` 列表 |
| 套餐联动 | ⚠️ Bug | `BizTreatmentService.createProgress` 接收 `packageOrderId` 参数，但 `PackageController.redeem` **未调用开疗程接口**，买完套餐后需手工到 care 页面开疗程 |

---

### M06-③ 耗材绑定

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 耗材表 | ⚠️ 有Bug | `biz_service_material` / `biz_service_material_rule` 等表存在 |
| ServiceMaterialDeductController | 🔴 缺失 | **disabled**，且 `ServiceMaterialDeductServiceStub` 是永远返回 null 的桩代码 |
| 前端 | 🔴 缺失 | `service-material.ts` 存在但对应的 Vue 页面未找到；库存管理 `stock/` 下无耗材与项目的绑定 UI |
| 步骤级耗材 | 🟡 部分 | `biz_treatment_phase_step` 有 `product_id` / `product_qty` 字段，但 `member_treatment_step_log` 也有 `product_id`；**无扣减逻辑** |

> **结论**：耗材绑定**功能不存在**。即使在 SOP 步骤里定义了耗材，也不会有任何自动扣减库存的逻辑。库存页面与疗程/SOP 完全独立。

---

### M06-④ 护理建议

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 建议模板 | ✅ 完成 | `biz_care_suggestion_template` 表，4 条种子模板（敏感肌/油性肌/熟龄肌/基础补水） |
| 建议规则 | ✅ 完成 | `biz_care_suggestion_rule` 表，4 条种子规则 |
| 规则触发引擎 | 🟡 部分 | `CareSuggestionEngineServiceImpl` 存在（disabled 的 `CareSuggestionEngineController` 调用），但规则引擎**没有被任何业务入口调用**（孤儿代码） |
| 皮肤档案 | ✅ 完成 | `care_skin_assessment` / `biz_member_skin_profile` 表 |
| 皮肤检测 | ✅ 完成 | `CareSuggestionService.submitAssessment` / `care/index.vue` 的"皮肤检测"Tab |
| 智能推荐 | 🟡 部分 | `care/index.vue` 的"智能推荐"Tab 调用 `careApi.recommend`，但推荐结果来自配方库过滤，**不是规则引擎**生成的 |
| 前端建议推送 | 🔴 缺失 | 无自动推送机制；建议生成后不推送（无 WeChat/SMS/站内信调用） |
| 护理建议日志 | ✅ 完成 | `biz_care_suggestion_log` 表记录建议生命周期（pending/viewed/accepted/rejected） |

> **结论**：护理建议模板和规则已建，但**触发引擎是孤儿代码**，建议生成靠配方库过滤而非规则引擎。建议生命周期已支持但无自动推送。

---

### M06-⑤ 疗程报告

| 链路环节 | 状态 | 证据 |
|---|---|---|
| 报告生成服务 | ✅ 完成 | `TreatmentReportService.generateReport` |
| REST 接口 | ✅ 完成 | `BizTreatmentReportController` (`POST /biz/treatment/report/generate` 等) |
| 前端页面 | 🔴 缺失 | **无疗程报告查看页面**。用户无法在浏览器查看已生成的报告 |
| 报告内容 | 🟡 部分 | `TreatmentReport` 字段完整（completed_sessions / care_compliance_rate / effect_score_before/after），但 `before/after` 皮肤分计算逻辑中 `readSkinScoreIfAny` 把同一条最新档案**同时作为 before 和 after**（BUG：两者永远相同，delta 永远为 0）|
| 护理遵守率 | ⚠️ Bug | `careSuggestionsFollowed` 统计 `status='accepted'`，但 `care-suggestion` **从未被业务代码标记为 accepted**（建议是手动生成/查看的，status 永远是 pending） |

> **结论**：疗程报告**后端链路完整但前端不可见**。生成报告的触发点也不明确（`BizTreatmentService` 各个端点均未调用 `reportService.generateReport`）。

---

### M06 额外发现

| 项 | 状态 | 说明 |
|---|---|---|
| 并发症 SOP | 🟡 部分 | `ComplicationService` + `init_complication_sop.sql` 完整（5 类 SOP 预案：红肿/瘀青/过敏/感染/血管栓塞）；前端 `complication/index.vue` 有上报和列表 UI；**但并发症事件不会从 POS 自动触发**，需手工上报 |
| 配方管理 | 🟡 部分 | `CareSuggestionService` CRUD 完整；前端 `care/index.vue` 的"配方库"Tab 正常 |
| 护理方案（CarePlan） | 🟡 部分 | `care_plan` 表存在，`CareSuggestionService` 未集成；前端无护理方案 Tab |
| 肤质评估报告 | 🟡 部分 | `care_skin_assessment` 表，`care/index.vue` 有检测历史展示；但推荐配方 ID 仅显示 ID 数字，**不显示配方名称** |

---

## 四、汇总评分卡

### M04 套餐/次卡/疗程卡

| PRD 功能点 | 评分 | 关键文件 | 说明 |
|---|---|---|---|
| 项目组合 | 🔴 未完成 | — | 仅支持单项目次卡，无多服务 bundle |
| 计次管理 | ✅ 已完成 | `PackageController.doRedeem` / `PackageOrderMapper.casRedeem` | |
| 有效期 | ✅ 已完成 | `crm_package_order.expire_time` + 校验逻辑 | |
| 转让 | 🟡 部分完成 | `PackageController.transfer` 后端完成，前端无入口 | |
| 冻结/解冻 | ✅ 已完成 | `casFreeze/casUnfreeze` + 前端按钮 | |
| 挂起/恢复 | 🟡 部分完成 | 后端完整，前端无挂起按钮 | |
| 部分核销 | ✅ 已完成 | `dto.times` 支持任意正整数 | |
| 差价补款 | 🟡 部分完成 | 后端完整，前端无升级入口 | |

### M05 储值卡

| PRD 功能点 | 评分 | 关键文件 | 说明 |
|---|---|---|---|
| 充值 | ✅ 已完成 | `WalletServiceImpl.doRecharge` | |
| 赠送金额 | ⚠️ 有Bug | `wallet_promotion` 规则有，但 `doRecharge` 不自动叠加赠送 | |
| 消费抵扣 | ✅ 已完成 | `WalletServiceImpl.doConsume` | |
| 限时活动 | 🔴 未完成 | — | `wallet_promotion` 无时间字段，常驻优惠 |
| 余额冻结 | 🔴 未完成 | — | `frozenAmount` 字段存在但无任何业务逻辑 |

### M06 项目/疗程管理

| PRD 功能点 | 评分 | 关键文件 | 说明 |
|---|---|---|---|
| 服务SOP | 🟡 部分完成 | `biz_treatment_phase_step` 已定义，与 POS 流程割裂 | |
| 阶段进度 | 🟡 部分完成 | 后端完整，前端无进度 Tab，套餐核销不联动开疗程 | |
| 耗材绑定 | 🔴 未完成 | `ServiceMaterialDeductServiceStub` 是桩代码 | |
| 护理建议 | 🟡 部分完成 | 模板/规则/皮肤检测有，建议引擎是孤儿代码 | |
| 疗程报告 | 🟡 部分完成 | 后端链路完整，前端无查看页面；before/after 永远是同值 | |

---

## 五、关键发现汇总

### 5.1 后端 disabled 控制器影响 M06 核心功能

| 控制器 | 状态 | 影响 |
|---|---|---|
| `CareRecipeController` | **disabled** | M06 护理配方 CRUD；但 `CareSuggestionService` 已提供 `/care/recipe` 端点作为替代，功能未中断 |
| `CareSuggestionEngineController` | **disabled** | 规则引擎（`CareSuggestionEngineServiceImpl`）未被调用，建议靠配方库过滤实现 |
| `CareSuggestionLogController` | **disabled** | 建议日志有表有 Mapper，前端无直接调用 |
| `CareSuggestionRuleController` | **disabled** | 规则管理无独立 UI |
| `CareSuggestionTemplateController` | **disabled** | 模板管理无独立 UI |
| `RechargeAuditController` | **disabled** | M05 充值审核功能缺失，前端无审核流程 |
| `ServiceMaterialDeductController` | **disabled** | M06 耗材绑定功能完全缺失 |
| `ResourceScheduleController` | **disabled** | SOP 步骤执行与排程资源无关联 |

### 5.2 前端 UI 缺失（后端已完成但用户不可见）

| 功能 | 后端 | 前端 | 证据文件 |
|---|---|---|---|
| 套餐转让 | ✅ `/packages/order/{id}/transfer` | ❌ 无入口 | `member-orders.vue` 无转让按钮 |
| 套餐挂起 | ✅ `/packages/order/{id}/suspend` | ❌ 无入口 | `member-orders.vue` 无挂起按钮 |
| 差价升级 | ✅ `POST /packages/upgrade` | ❌ 无入口 | `member-orders.vue` 无升级按钮 |
| 疗程进度 | ✅ `/biz/treatment/progress/member/{id}` | ❌ 无页面 | `care/index.vue` 无进度 Tab |
| 疗程报告 | ✅ `POST /biz/treatment/report/generate` | ❌ 无页面 | 无独立报告查看 Vue |

### 5.3 数据完整性与逻辑 BUG

| 位置 | BUG 描述 |
|---|---|
| `TreatmentReportService.readSkinScoreIfAny` | `before = readSkinScoreIfAny(memberId)` 和 `after = readSkinScoreIfAny(memberId)` 调用**完全相同**，delta 永远为 null（两条赋值指向同一个结果）。应分别取第一次 session 之前和最后一次 session 之后的档案 |
| `WalletServiceImpl.doRecharge` | 充值后 `giftAmount` 从不叠加。`wallet_promotion.gift_amount` 有数据但业务逻辑未消费 |
| `care/index.vue` 推荐配方 | `recommendedRecipeId` 只显示数字 ID，不做表 lookup 显示配方名称 |
| `CareSuggestionEngineServiceImpl` | 规则引擎 service 存在但**没有任何业务代码路径调用它**（orphan service） |
| `WalletPromotion` | 无时间范围字段，无法实现限时赠送活动 |
| `biz_treatment_phase_step.product_id` | SOP 步骤有耗材字段但无扣减逻辑，耗材库存不联动 |

---

*审计结束。共发现 3 项 ✅、5 项 🟡 部分完成、2 项 🔴 未完成、5 项 ⚠️ 有Bug/孤儿代码。核心问题是前端 UI 与后端接口的断裂（M04/M05/M06 均有），以及 M05 余额冻结和 M06 耗材绑定完全缺失。*
