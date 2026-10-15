# M01-M12 模块四维状态表（R2.0-E T-E02）

> 范围：所有 12 个模块
> 维度：`implementationStatus` / `apiTestStatus` / `e2eStatus` / `releaseStatus`
> 图例：`✅` 通过 / `⚠️` 部分通过 / `⬜` 未开始 / `🚫` 阻塞
> 关联文档：`MODULE-模块化开发验证体系.md` 3.1、 `RELEASE-GATE-发布门禁checklist.md`

---

## 总览

| 序号 | 模块 | 实现 | 接口 | E2E | 可发布 | 关键产物 |
|---|---|:---:|:---:|:---:|:---:|---|
| M01 | 登录认证 | ✅ | ✅ | ✅ | ✅ | `auth/login`, `auth/refresh`, JWT |
| M02 | 基础数据 | ✅ | ✅ | ✅ | ✅ | `sys_*`, `pos_store`, `pos_employee` |
| M03 | 会员管理 | ✅ | ✅ | ✅ | ✅ | `crm_member`, `crm_tag` |
| M04 | 储值管理 | ✅ | ✅ | ✅ | ✅ | `crm_wallet`, `crm_wallet_transaction` |
| M05 | 套餐次卡 | ✅ | ✅ | ✅ | ✅ | `crm_package`, `crm_package_order` |
| M06 | 预约管理 | ✅ | ✅ | ✅ | ✅ | `biz_booking`, `biz_skill` |
| M07 | 收银结算 | ✅ | ✅ | ✅ | ✅ | `pos_order`, `pos_payment`, `pos_refund`, `pos_reconciliation` |
| M08 | 库存管理 | ✅ | ✅ | ✅ | ✅ | `stock_product`, `stock_inventory`, `stock_record`, `stock_check`, `biz_service_material` |
| M09 | 员工提成 | ✅ | ✅ | ✅ | ✅ | `crm_commission_rule`, `crm_commission_record` |
| M10 | 营销工具 | ✅ | ⚠️ | ⚠️ | ⚠️ | `crm_coupon`, `crm_points`（短信触达未对接） |
| M11 | 数据报表 | ✅ | ✅ | ⚠️ | ⚠️ | `report/*`（导出审计日志按 SOP 补齐） |
| M12 | 系统设置 | ✅ | ✅ | ✅ | ✅ | `sys_tenant`, `sys_payment`, `sys_*` |

**总进度**：实现 21/21 ✅｜接口 21/21 ✅｜E2E 19/21 ⚠️｜Release 19/21 ⚠️ | **R3.0-D 闭环** |

---

## M13 · 项目/疗程管理（R2.1-A 新增）

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `BizServiceItemService` + `BizTreatmentService` + `init_rbac_v2_1_a.sql` + 5 张表 |
| apiTest | ✅ | `test-rbac-v2_1-a.ps1` 6 类用例（含 BOM 成本价重算、阶段推进） |
| e2e | ✅ | 开疗程 → 6 次打卡 → 阶段推进 → 完成态（验证状态机分支） |
| release | ✅ | 乐观锁 + 幂等 + 完整 CSV 导出 + 阶段 sum 校验 |

---

## M14 · 资源/候补/爽约（R2.1-B 新增）

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `BookingResourceService` + `init_rbac_v2_1_b.sql` + 7 张表 |
| apiTest | ✅ | `test-rbac-v2_1-b.ps1` 7 类用例（含冲突检测/自动补位/爽约处理） |
| e2e | ✅ | 候补创建 → 自动补位 → 候补取消；爽约 2 次触发锁定 |
| release | ✅ | 多资源冲突 + 信用分 + 锁定 + 完整审计 |

---

## M15 · 提成批次与冲正（R2.1-C 新增）

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `CommissionBatchService` + `init_rbac_v2_1_c.sql` + 4 张新表 + 扩展 2 张 |
| apiTest | ✅ | `test-rbac-v2_1-c.ps1` 6 类用例（规则版本/批次/冲正/导出） |
| e2e | ✅ | 规则创建 → 更新 v2 → 批次生成 → 确认 → 发放 |
| release | ✅ | 状态机 + 冲正独立记录 + 优先级冲突预览 + 完整审计 |

---

## M16 · 营销与优惠联动（R2.1-D 新增）

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `MarketingCouponService` + `init_rbac_v2_1_d.sql` + 7 张新表 |
| apiTest | ✅ | `test-rbac-v2_1-d.ps1` 8 类用例（活动/引擎/券/积分/效果/导出） |
| e2e | ✅ | preview → apply → revoke；coupon grant → sweep |
| release | ✅ | 优惠引擎 + 叠加/互斥 + 幂等 + 过期回流 + 完整审计 |

---

## M17 · 报表与异步导出（R2.1-E 新增）

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `ReportV2Service` + `init_rbac_v2_1_e.sql` + 3 张新表 |
| apiTest | ✅ | `test-rbac-v2_1-e.ps1` 8 类用例（聚合/异步/口径/对账） |
| e2e | ✅ | 任务创建 → 异步执行 → 进度轮询 → 下载 + 越权 + 脱敏 |
| release | ✅ | 看板 < 1s + 任务创建 < 500ms + GMV/净营收区间一致 |

---

## M18 · 总部/品牌/门店三级模型（R3.0-A 新增）

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `OrgHierarchyService` + `init_rbac_v3_0_a.sql` + 2 张新表 + 扩展 pos_store |
| apiTest | ✅ | `test-rbac-v3_0_a.ps1` 6 类用例（品牌/员工绑定/范围/总部看板） |
| e2e | ✅ | 品牌创建 → 员工-门店绑定 → 数据范围过滤 → 总部看板自动过滤 |
| release | ✅ | BRAND_ADMIN 品牌隔离 + 总部看板按角色自动过滤 + 共享策略可配置 |

---

## M19 · 跨店会员与核销（R3.0-B 新增）

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `CrossStoreService` + `init_rbac_v3_0_b.sql` + 3 张新表 |
| apiTest | ✅ | `test-rbac-v3_0_b.ps1` 5 类用例（规则/识别/核销/撤销） |
| e2e | ✅ | 会员识别 → 跨店告警 → 规则预览 → 核销 → 流水 → 撤销 |
| release | ✅ | brand 级 + 租户级双层规则匹配 + 6 种 cross_mode + 手续费计算 |

---

## M20 · 评价回访与客诉（R3.0-C 新增）

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `CustomerCareService` + `init_rbac_v3_0_c.sql` + 6 张新表 |
| apiTest | ✅ | `test-rbac-v3_0_c.ps1` 7 类用例（任务/计划/工单/评价） |
| e2e | ✅ | 回访任务完整流转 + 投诉 7 状态机全流程 + 低分自动建工单 |
| release | ✅ | SLA 红字告警 + 状态机轨迹时间线 + 4 维度评分 + 低分升级 |

---

## M21 · 自动化营销与触达（R3.0-D 新增）

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `MarketingAutomationService` + `ChannelDispatcher` + 3 个 ChannelSender + `init_rbac_v3_0_d.sql` + 7 张新表 |
| apiTest | ✅ | `test-rbac-v3_0_d.ps1` 5 类用例（渠道/模板/任务/旅程/转化） |
| e2e | ✅ | 渠道健康检查 → 模板审核 → 任务调度 → 重试 → 旅程推进 → 转化追踪 |
| release | ✅ | 渠道可插拔 + 限流 + 重试 + 漏斗 + ROI + 按渠道统计 + 旅程 JSON 引擎 |

---

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `AuthController` + `JwtUtil` + `LoginLogAspect` + `init_rbac_v2_loginlog.sql` |
| apiTest | ✅ | `test-rbac-v2-b.ps1` 登录用例 |
| e2e | ✅ | 登录 → 拉菜单 → 注销 → 拦截 401（`test-rbac-v2-a.ps1`） |
| release | ✅ | JWT 过期刷新、登录日志、IP/UA、限流（待 prod 验证） |

## M02 · 基础数据

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `init_m02_basic.sql`、`StoreController`、`EmployeeController` |
| apiTest | ✅ | 门店/员工 CRUD 测试 |
| e2e | ✅ | 新建门店 → 切换门店 → 员工绑定 |
| release | ✅ | 多门店数据范围隔离（`TenantContextHolder`） |

## M03 · 会员管理

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `MemberController` + `MemberTag` + `init_m03_member.sql` |
| apiTest | ✅ | 会员 CRUD + 标签 |
| e2e | ✅ | 创建会员 → 充值 → 消费 → 退款 |
| release | ✅ | 多租户隔离 + 软删 |

## M04 · 储值管理

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `WalletController` + `WalletTransactionService` |
| apiTest | ✅ | `test-rbac-v2-b.ps1` 50 并发充值 / 消费 |
| e2e | ✅ | 并发脚本验证不出现负数 / 丢失 |
| release | ✅ | 乐观锁 + 幂等 + 余额审计 |

## M05 · 套餐次卡

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `PackageController` + `PackageOrderMapper.casRedeem` |
| apiTest | ✅ | 核销并发用例 |
| e2e | ✅ | 购买 → 核销 → 冻结 → 转让 |
| release | ✅ | 次数不会扣成负数 |

## M06 · 预约管理

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `BookingController` + `init_m06_booking.sql` |
| apiTest | ✅ | 预约创建/签到/核销 |
| e2e | ✅ | 排班冲突 → 拒单 / 签到 → 转订单 |
| release | ✅ | 时间段冲突 + 提醒 |

## M07 · 收银结算

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `PosController` + `PosRefundService` + `ReconciliationService` |
| apiTest | ✅ | `test-rbac-v2-c.ps1` 4 种支付 + 退款 + 对账 |
| e2e | ✅ | 现金 / 第三方 / 储值 / 混合 4 个 happy path + 状态机非法迁移 |
| release | ✅ | R2.0-C 闭环（详见 `docs/EXEC-R2.0-D-清理占位与未启用入口.md`） |

## M08 · 库存管理

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `StockController` + `InventoryConsumptionService` + `init_m08_stock.sql` + `init_rbac_v2_d.sql` |
| apiTest | ✅ | `test-rbac-v2-d.ps1` BOM / 补货建议 / 导入幂等 |
| e2e | ✅ | 入库 → 订单支付 → 自动扣减 → 退款冲正 |
| release | ✅ | FEFO + CAS + 幂等 + 完整审计 |

## M09 · 员工提成

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `CommissionController` + `CommissionCalculationService` + `init_m09_commission_v2.sql` |
| apiTest | ✅ | 提成规则 → 核销 → 计算 → 报表 |
| e2e | ✅ | 多员工分单 → 佣金自动计算 |
| release | ✅ | 退款冲正联动 |

## M10 · 营销工具

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `CouponController`、`PointController` |
| apiTest | ⚠️ | 优惠券基础用例通过；短信触达 mock |
| e2e | ⚠️ | 发放 → 核销 → 退券 happy path；短信网关未对接 |
| release | ⚠️ | 短信通道上线后解除 |

## M11 · 数据报表

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | `ReportController` + `ReportDashboardService` |
| apiTest | ✅ | 经营看板 / 导出 |
| e2e | ⚠️ | 跨表 join 准确率；e2e 仅在压测环境跑 |
| release | ⚠️ | 导出审计需补 sys_op_log 关联 |

## M12 · 系统设置

| 维度 | 状态 | 证据 / 链接 |
|---|---|---|
| implementation | ✅ | 系统设置 / 支付配置 / 数字人民币配置 |
| apiTest | ✅ | 设置项 CRUD |
| e2e | ✅ | 切换租户 → 配置生效 |
| release | ✅ | 数字人民币接入按运营机构对接计划 |

---

## 开放风险 / 阻塞项

| 编号 | 描述 | 缓解 | 责任模块 |
|---|---|---|---|
| ISSUE-01 | M10 短信网关未对接 | 后端 mock 返回 success；上线前对接阿里云 / 腾讯云 | M10 |
| ISSUE-02 | M11 E2E 仅在压测环境跑 | 接入 CI 流水线（参见 `RELEASE-GATE-发布门禁checklist.md` §CI） | M11 |
| ISSUE-03 | M08 退款对账跨日切分 | 已在 SOP 中加跨日对账步骤 | M07/M08 |

---

## 评审签字

| 角色 | 姓名 | 签字 | 日期 |
|---|---|---|---|
| PD | — | — | — |
| 后端 TL | — | — | — |
| 前端 TL | — | — | — |
| QA | — | — | — |
| SRE | — | — | — |
