> ⚠️ **本文件为历史快照，已过时。**
> **最新状态见：`audit/ARCHIVE.md` + `audit/post-audit/final-post-audit-report.md`**
> **本文件保留作为审计轨迹，不反映当前状态。**

# Post-Audit 阶段性总结（Phase 2.2 / 2.3 / 2.4 / 2.5）

> **盘点时间**：2026-09-21 16:55 (UTC+8)
> **盘点规则**：只读不改；本文档为阶段性总结，非修复执行
> **范围**：Phase 2.2 代码修复 + Phase 2.3 DB 迁移 + Phase 2.4 守门回归 + Phase 2.5 运行时冒烟
> **数据库**：`127.0.0.1:3307 / meiyetec`（MySQL 8.0.46）

---

## 一、Phase 2.2 代码修复完成度

### 1.1 修复统计

| 严重度 | 总数 | 已修 | 未修（批次内）| FIXED 率 |
|---|---|---|---|---|
| **P0** | 9 | 0 | 9 | 0% |
| **P1** | 17 | **15** | 2（I012 / I020）| **88%** |
| **P2** | 7 | 0 | 7 | 0% |
| **Backlog** | 9 | 0 | 9 | 0% |
| **合计** | **42** | **15** | **27** | **36%** |

> ⚠️ **口径说明**："未修 2 个"仅指 Phase 2.2 修复批次范围内未处理的 P1（I012/I020）。
> Post-Audit 全局剩余 P1（含批次外新发现）= §六的 **10 个工作项**；全局 OPEN P1 Issue = §5.2 的 **18 个**。
> 三组数字口径不同，不矛盾。

> 备注：I002/I017/I029/I038/I045 5 个 DB 类 P0 Issue **不在 Phase 2.2 范围**，归属 Phase 2.3，已于 Phase 2.3 Batch 1-6 全部 apply。详见 §二。

### 1.2 Phase 2.2 A/B 两批次详情

#### Phase 2.2-A（2026-09-17 22:00-22:50）— 6 个 P1 Issue

| Issue | 文件 | +行/-行 | commit | 方案 |
|---|---|---|---|---|
| I037 | `WebConfig.java` | +5/-1 | `eb5ff6e`+`fad68a1` | excludePathPatterns 补全 |
| I070 | `MktCouponController.java` | +2/-1 | `e7802d3` | member → crm_member |
| I008 | `CrmIntegrationService.java` | +2/-1 | `776e8be` | catch ignore → log.error |
| I011 | `CrmIntegrationService.java` | +3/-1 | `0b5607e` | 移除 selectById |
| I047 | `IntlBookingService.java` | +5/-2 | `f0c73d8` | catch warn → log.error + Result.error |
| I071 | `MarketingAutomationService.java` | +11/-2 | `3d94c56` | rows 校验 + tenantId |

#### Phase 2.2-B（2026-09-19 23:38-23:55）— 9 个 P1 Issue

| Issue | 文件 | +行/-行 | commit | 方案 |
|---|---|---|---|---|
| I072 | `MarketingAutomationService.java` | +18/-2 | `2aeb7b3` | catch DataAccessException + 兜底 |
| I073 | `MarketingAutomationService.java` | +20/-16 | `5cb2c01` | COUNT args / pageArgs 拆分 |
| I061+I075+I076 | `ChannelDispatcher.java`+`PushSender.java` | +62/-3 | `69077be` | 别名 MP+WECHAT_OA / PushSender 新增 |
| I060 | `MktCouponController.java` | +27/-11 | `be62a45` | try/catch + audit log |

### 1.3 Phase 2.2 仍 OPEN 的 P1 Issue（2 个）

| Issue | 描述 | 阻塞 Feature |
|---|---|---|
| **I012** | Mock `@ConditionalOnMissingBean` NPE（与 Salesforce 同名冲突）| F012 / F015 / F018 |
| **I020** | `PluginSecurityPolicy.assertCallerAllowed` 合作式沙箱弱 | F042 |

---

## 二、Phase 2.3 DB 迁移完成度

### 2.1 实际落地统计

| 维度 | 应建/应加 | 实际已建/已加 | 差异 |
|---|---|---|---|
| **表** | 9 张 | **9 张** ✅ | 0 |
| **列** | 16 列 | **16 列** ✅ | 0 |

### 2.2 各 Batch 落地详情

| 批次 | 范围 | 表 | 列 | 状态 | 日期 |
|---|---|---|---|---|---|
| **Batch 1** | EXT-19 / EXT-23 | `plugin_runtime_state` / `member_message` / `overseas_booking` | `mini_program_open_id` | ✅ | 2026-09-20 |
| **Batch 2** | mkt_* 子集 | `mkt_invitation` / `mkt_invitation_rule` / `mkt_dispatch` / `mkt_dispatch_target` | — | ✅ | 2026-09-20 |
| **Batch 3** | EXT-17 Google Maps | `google_maps_config` / `google_maps_geocode_log` | — | ✅ | 2026-09-21 |
| **Batch 4** | EXT-24 CRM 子集 | — | `crm_integration` 9 列 + `crm_sync_log` 3 列 | ✅ | 2026-09-21 |
| **Batch 5** | EXT-22 Google Maps + 有赞 | — | `google_maps_config.enable_place_details` + `ecommerce_platform` 3 列 | ✅ | 2026-09-21 |
| **Batch 6** | EXT-24 CRM contact_map | `crm_contact_map`（13 列 + 4 索引）| — | ✅ | 2026-09-21 |

### 2.3 已知约束 / 事故

| 事故 | 等级 | 状态 |
|---|---|---|
| S1: Batch 2 误执行（流程违规）| L2 低破坏性 | ✅ 用户接受现状，记入 postmortem |
| S2: migrate_ext_19 ADD COLUMN IF NOT EXISTS 语法错 | 无影响 | ✅ CREATE 路径已带列 |
| S3: Batch 5 MariaDB 语法 | 无影响 | ✅ 已用 SHOW COLUMNS 改写 |

### 2.4 仍待 apply 的脚本（保留）

| 脚本 | 内容 | 处理计划 |
|---|---|---|
| `migrate_mkt_002_drop_market_activity.sql` | `DROP TABLE market_activity` + 撤销 rollback | ⏸ 保留脚本，等用户决策 |
| `EXCLUDED-FOR-2.3.sql`（2 份）| 5 张范围外 DROP+CREATE | ⏸ Phase 7 评审后再 apply |

---

## 三、Phase 2.4 守门脚本回归结果

### 3.1 8 个脚本（基于真实 committed baseline dcaf07a）

| # | 脚本 | committed 基线 | Phase 2.4 | Delta | 状态 |
|---|---|---|---|---|---|
| 01 | `01-scan-fake-success.ps1` | 318 | 297 | **-21** ✅ | 改善 |
| 02 | `02-scan-mock.ps1` | 113 | 90 | **-23** ✅ | 改善 |
| 03 | `03-scan-disabled.ps1` | 119 | 94 | **-25** ✅ | 改善 |
| 04 | `04-scan-endpoint-coverage.ps1` | backend=2468 | 2013 | **-455** ✅ | 改善 |
| 05 | `05-scan-contract-drift.ps1` | 1551 | 1529 | **-22** ✅ | 改善 |
| 06 | `06-scan-soft-delete.ps1` | 98 | 77 | **-21** ✅ | 改善 |
| 07 | `07-scan-transaction.ps1` | 233 | 214 | **-19** ✅ | 改善 |
| 08 | `08-scan-shadow-ghost.ps1` | Total=2028 | 2001 | **-27** ✅ | 改善 |

**回归判断**：✅ **零回归**（8/8 全部指标 ≤ committed baseline，无任何指标上升）

### 3.2 DB schema 验证

| 验证项 | 结果 |
|---|---|
| 8 张关键表存在 | ✅ 全部存在 |
| 关键列存在 | ✅ 全部到位 |
| mvn compile | ✅ Exit 0 |

### 3.3 v1 → v2 数字修正

- v1 报告引用"Phase 9 基线"（299/90/94/...）与 dcaf07a committed baseline 全部不符
- v2 报告已用 `git show dcaf07a:audit/static-gap/*.txt` 逐脚本核实并更正
- 教训已记入 `phase-2.3-postmortem.md §12`

---

## 四、Phase 2.5 运行时冒烟验证结果

### 4.1 启动状态

| 维度 | 状态 |
|---|---|
| 启动耗时 | **16.489 秒** |
| 启动 ERROR 数 | **0** ✅ |
| Tomcat 端口 | 8080 |
| Flyway enabled | ✅ 是（`flyway_schema_history` 已存在，跳过 auto-baseline）|
| 9 个 Mkt*Controller | ✅ 全部存在 |
| MarketActivityController | ✅ 存在 |

### 4.2 6 个 API 冒烟

| 端点 | 状态 | 备注 |
|---|---|---|
| `GET /crm-integration/dashboard` | **200** ✅ | totalIntegrations:4，EXT-15 列无映射错误 |
| `GET /plugin/runtime/list` | **200** ✅ | runtimeEnabled:true，persistedRecords 可读 |
| `GET /plugin/runtime/dashboard` | **200** ✅ | plugin_runtime_state 表可读 |
| `GET /intl-booking/list` | **200** ✅ | overseas_booking 表可读，无 NPE |
| `GET /google-maps/config` | **200** ✅ | google_maps_config 表可读 |
| `GET /ecommerce/platform/list` | **200** ✅ | ecommerce_platform 3 行，新列到位 |
| `POST /mkt/coupon/batch/1/grant` | **200（409）** ✅ | I060/I070/I071/I072/I073 修复生效 |

**6/6 无 500 ✅**

### 4.3 Feature 运行时验证

| Feature | 验证结果 |
|---|---|
| F005 syncNow 入口 | ✅ 运行时正常 |
| F012 / F020 known-ids 差集 | ✅ 运行时正常（catch 路径消除）|
| F023 crm_contact_map | ✅ 表存在 |
| F032 / F036 / F037 / F049 / F051 | ✅ 运行时正常 |
| F081 / F082 overseas_booking | ✅ 运行时正常 |
| F056-F065 google_maps | ✅ 运行时正常 |
| F066 / F075-F078 ecommerce | ✅ 运行时正常 |

### 4.4 运行期 ERROR 残留

| Issue | 错误源 | 内容 | 频率 | 修复阶段 |
|---|---|---|---|---|
| **I082** | `InAppSender` + `KioskOfflineAlertService` | `Field 'member_id' doesn't have a default value`（receiver 非 memberId → parseMemberId 返回 null → NOT NULL 违例）| 每 ~60 秒 | Phase 2.6+ |

---

## 五、Issue Register 总状态（截至 Phase 2.5 后）

### 5.1 按状态汇总

| 状态 | 数量 | 详情 |
|---|---|---|
| ✅ **FIXED**（Phase 2.2 + 2.3）| **20** | I008/I011/I037/I047/I060/I061/I070/I071/I072/I073/I075/I076 + I002/I017/I029/I038/I045（DB 类）+ I019/F047 |
| ⚠️ **PARTIAL-FIXED**（DB 层 FIXED，业务层仍 OPEN）| **1** | I023 |
| ⏸ **OPEN**（Post-Audit 待修）| **19** | 详见 §5.2 |
| 🔄 **CONFLICT**（SoT 冲突，需用户决策）| **5** | I001/I018/I026/I027/I030 |
| 📋 **BACKLOG** | **9** | I004/I010/I013/I019/I020/I021/I022/I025/I032/I033/I036 等 |
| 🟢 **VERIFIED** Feature（Phase 5 报告）| 2 | F031/F048 |

### 5.2 仍 OPEN 的 Issue（Post-Audit 待修，共 19 个）

| Issue | 描述 | 阻塞 Feature |
|---|---|---|
| **I012** | Mock `@ConditionalOnMissingBean` + Router null | F012/F015/F018 |
| **I012-Bean** | Router fallback 返回 null → NPE | F015 |
| **I009** | `@Transactional` 无 `rollbackFor` | F005 |
| **I011** | CrmIntegrationService.update select-after-update（Phase 2.2-A 已修，5B 复发） | F003 |
| **I013** | `crm_integration.syncSchedule` dead code | F030 |
| **I018** | SoT PluginRuntimeController 租户隔离缺失 | F035/F044 |
| **I019** | `PluginService` 端点在 service 包而非 controller 包（backlog） | F045/F046/F047 |
| **I005-C1** | toggle enum PAUSED vs DISABLED | F004 |
| **I005-C2** | `syncNow` 按钮未传 `?force=1` | F005 |
| **I005-C4** | 新建集成表单缺凭据字段 | F014 |
| **I006** | dashboard tab 缺失（Shadow）| F007 |
| **I007** | edit dialog 缺失（Shadow）| F003 |
| **I003** | sync log 列名 integrationId（DB 列缺失）| F006/F026 |
| **I016** | sync log 缺 platform 列 | F006/F026 |
| **I028** | enablePlaceDetails / appId/kdtId/signSecret 前端无 UI | F065/F079 |
| **I031** | Place Details 无降级 | F062 |
| **I034** | @Transactional rollbackFor（Youzan webhook）| F077 |
| **I035** | webhook msg_id 去重不完整 | F078 |
| **I082** | InAppSender 运行期 `member_id` NOT NULL 违例（receiver 非 memberId，KioskOfflineAlertService 每 60s 触发）| F094（ChannelDispatcher 链路）|

### 5.3 CONFLICT Issue（待用户决策）

| Issue | 描述 | 影响 Feature |
|---|---|---|
| **I001** | EXT-24 .mdc vs 实际代码差异（HubSpot/Zoho/Router）| F016/F017/F018 |
| **I018** | EXT-23 .mdc 0.7 + 5 描述与代码不一致 | F035/F044 |
| **I026** | EXT-22 .mdc vs `verifyWebhookSignature` 实际实现 | F071/F077 |
| **I027** | EXT-22 .mdc vs webhook 接收端状态（未实现）| F077/F078 |
| **I030** | EXT-22 .mdc vs webhook 去重实现 | F077/F078 |

---

## 六、剩余工作清单（按优先级）

### 6.1 全局 P1 工作项（Post-Audit 全局视角，非仅 Phase 2.2 批次）

> ⚠️ **与 §1.1 "剩 2 个 P1" 的关系**：§1.1 的"2"是 Phase 2.2 批次内未修；本节 10 个是 Post-Audit 全局剩余 P1 工作项，口径不同。

| # | 工作项 | 类型 | 预计耗时 | 依赖 |
|---|---|---|---|---|
| 1 | **I012/I012-Bean**：Mock NPE 修复 | 代码 | 30 min | 无 |
| 2 | **I009**：`@Transactional(rollbackFor=Exception.class)` 修复 | 代码 | 10 min | 无 |
| 3 | **I018 SoT 决策**：PluginRuntimeController 租户隔离规则 | 决策 | 用户决策 | 无 |
| 4 | **I005-C2**：`syncNow(id, { force: 1 })` 前端 API 修复 | 前端 | 20 min | 无 |
| 5 | **I005-C1**：toggle enum DISABLED 替换 PAUSED | 前端 | 10 min | 无 |
| 6 | **I003 + I016**：sync log 列对齐（integrationId / platform）| DB+前端 | 1 小时 | DB ALTER |
| 7 | **I028 + I036**：enablePlaceDetails / API Key 遮蔽 UI | 前端 | 1 小时 | 无 |
| 8 | **I034 + I035**：Youzan webhook rollbackFor + msg_id 去重 | 代码 | 1 小时 | 用户决策（webhook 接收端）|
| 9 | **I031**：Place Details 降级 | 代码 | 30 min | 用户决策 |
| 10 | **I082**：InAppSender `member_id` NOT NULL 违例（receiver 非 memberId）| 代码 | 20 min | 无 |

**全局 P1 合计**：约 6-8 小时代码 + 用户决策若干 + **I082 运行期 ERROR（独立 Track，20 min）**

### 6.2 P2（应该修，影响 UX / 一致性）

| # | 工作项 | 类型 | 预计耗时 |
|---|---|---|---|
| 11 | **I006/I007**：crm-integration dashboard tab + edit dialog | 前端 | 2 小时 |
| 12 | **I024**：plugin/index.vue 加 runtime tab | 前端 | 1.5 小时 |
| 13 | **I019**：`PluginService` 拆分为 Controller 三件套 | 代码重构 | 1 小时 |
| 14 | **I005-C4**：CRM 新建表单补 5 字段 | 前端 | 30 min |
| 15 | **I022**：`disablePlugin` 副作用（清 lastError）| 代码 | 20 min |
| 16 | **I020**：`assertCallerAllowed` 沙箱加强 | 代码 | 1 小时 |

**P2 合计**：约 6-7 小时

### 6.3 Backlog（不阻塞，可后续清理）

| # | 工作项 | 类型 | 预计耗时 |
|---|---|---|---|
| 17 | **I004**：CrmIntegrationService 拆 3 Controller | 重构 | 2 小时 |
| 18 | **I010**：Flyway V1 UTF-8 BOM + encoding 配置 | 配置 | 30 min |
| 19 | **I013**：syncSchedule per-integration cron | 代码 | 1.5 小时 |
| 20 | **I021/I025**：PluginRuntimeInfo.autoLoaded / deniedPackages dead code | 代码 | 30 min |
| 21 | **I032**：geocode_batch_log audit 表 | DB+代码 | 1.5 小时 |
| 22 | **I033**：buildWebhookUrl 归档或删除 | 代码 | 10 min |
| 23 | **I036**（masked input 实现）| 前端 | 30 min |
| 24 | **mkt_market_activity 表 DROP+CREATE 决策** | DB | 用户决策 |

**Backlog 合计**：约 6-7 小时

---

## 七、建议下一步（A/B/C/D 选项）

### 选项 A：修 P1 全部 10 项 → 达成 Phase 5 主线收敛

| 内容 | 时间 |
|---|---|
| 修 I012/I009/I005/I003/I016/I028/I031/I034/I035 + 运行期 ERROR | **6-8 小时代码 + 1-2 小时用户决策** |
| 加固后效果 | 16 P1 Issue 全部 FIXED；4 CONFLICT 走用户决策；可走 Phase 5B/5C 重跑 |
| 风险 | I018 SoT 决策若用户延后，会拖住 I018 整体修复 |

### 选项 B：先做 Phase 2.6（Playwright E2E 冒烟）

| 内容 | 时间 |
|---|---|
| 跑 `crm-integration.spec.ts` + `mh-routes.spec.ts` + `audit/crm-integration.spec.ts` | **1-2 小时** |
| 作用 | 验证修复在浏览器层而非 API 层是否生效；提供 L5b 级证据 |
| 风险 | 不会解锁 P1 Issue，仅提升证据等级 |

### 选项 C：先做 Post-Audit Diff Audit（重构审查）

> ⚠️ **与审计阶段 Phase 3.5 的区别**：
> - 审计阶段 Phase 3.5 = `audit/diff-audit/mkt-refactor.md`（MKT 重构专项），**已做过**
> - 本选项 C = 对 Post-Audit 修改（CrmIntegrationService / PluginService 拆 Controller）做**新的** Diff Audit
> - 用途：防止 P1 修复 + 重构同时进行时引入新回归

| 内容 | 时间 |
|---|---|
| 对 Post-Audit 变更做 Diff Audit | **30-60 分钟** |
| 作用 | P1 修复 + 重构同时进行时，避免引入新回归 |
| 风险 | 与 A 选项并行时需协调 commit 顺序 |

### 选项 D：暂缓 P1，进入 SoT CONFLICT 决策专项

| 内容 | 时间 |
|---|---|
| 用户决策 I001/I018/I026/I027/I030 5 个 SoT 冲突 | **用户主导，时长不定** |
| 作用 | 解锁 4 个 CONFLICT Feature（F016/F017/F018/F035/F044/F071/F077/F078），让其从 CONFLICT → 其它状态 |
| 风险 | 如果 SoT 与代码冲突大，可能产生新修复工作量 |

---

## 八、Phase 2.2 - 2.5 总结

### 8.1 数字汇总

| 指标 | 数值 |
|---|---|
| 守门脚本回归 | 8/8 通过，0 回归 |
| DB schema 落地 | 9 表 + 16 列 100% apply |
| 代码修复 | 15 个 P1 Issue FIXED |
| mvn compile | Exit 0 |
| 后端启动 ERROR | 0 |
| API 冒烟 200 率 | 6/6 = 100% |

### 8.2 最大剩余阻塞（Post-Audit 待修）

| 类别 | 数量 | 简介 |
|---|---|---|
| P1 代码 Issue | 10 个 | I012/I009/I005-C1/C2/I003/I016/I028/I031/I034/I035/I082 |
| P1 代码 Issue 详细 OPEN | 19 个 | §5.2 列出（含 I006/I007/I013/I018/I019 等非 P1 级）|
| CONFLICT（SoT）| 5 个 | I001/I018/I026/I027/I030 |
| P2 UX / 一致性 | 6 个 | I006/I007/I019/I020/I024 + I005-C4 |
| Backlog | 8 个 | I004/I010/I013/I021/I025/I032/I033/I036 |
| 运行期 ERROR | 1 个 | `I082` InAppSender `member_id` NOT NULL 违例（KioskOfflineAlertService 每 60 秒触发）|

### 8.3 风险提示

- **P1 代码 Issue 不修复**：会一直停留在 UNVERIFIED / PARTIAL-BLOCKER，无法走到 VERIFIED
- **CONFLICT 不决策**：8 个 Feature 永远停留在 CONFLICT 状态
- **运行期 ERROR 不修**：监控噪音 + 站内信功能实际不可用

---

**本文件为只读盘点，不执行任何操作。等用户决策 A/B/C/D 后进入对应 Phase。**
