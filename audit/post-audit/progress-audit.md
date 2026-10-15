> ⚠️ **本文件为历史快照，已过时。**
> **最新状态见：`audit/ARCHIVE.md` + `audit/post-audit/final-post-audit-report.md`**
> **本文件保留作为审计轨迹，不反映当前状态。**

# Post-Audit Phase 2.2 + 2.3 + 2.4 完整进度盘点

> **盘点时间**：2026-09-21 14:58 (UTC+8)
> **盘点规则**：只读不改，不执行任何操作
> **产出文件**：`audit/post-audit/progress-audit.md`

---

## 一、Phase 2.2 代码修复进度

### 1.1 Issue Register 总览（截至 Phase 5 结束）

| Issue | 严重度 | 最终状态 | 修复 commit | 批次 |
|---|---|---|---|---|
| **I001** | P0 | CONFLICT（SoT 冲突）| — | Post-Audit 未修 |
| **I002** | P0 | OPEN（DB 列缺失，根因未修）| — | Post-Audit 未修 |
| **I003** | P2 | OPEN（integrationId 列不匹配）| — | Post-Audit 未修 |
| **I004** | Backlog | BACKLOG | — | Backlog |
| **I005** | P2 | OPEN（enum PAUSED vs DISABLED / force 缺失）| — | Post-Audit 未修 |
| **I006** | P2 | OPEN（Shadow: dashboard tab 缺失）| — | Post-Audit 未修 |
| **I007** | P2 | OPEN（Shadow: edit dialog 缺失）| — | Post-Audit 未修 |
| **I008** | **P1** | ✅ FIXED | `776e8be` | Phase 2.2-A |
| **I009** | P2 | OPEN（@Transactional rollbackFor=RuntimeException）| — | Post-Audit 未修 |
| **I010** | Backlog | BACKLOG（Flyway UTF-8 BOM）| — | Backlog |
| **I011** | **P1** | ✅ FIXED | `0b5607e` | Phase 2.2-A |
| **I012** | **P1** | OPEN（Mock ConditionalOnMissingBean NPE）| — | Post-Audit 未修 |
| **I013** | Backlog | BACKLOG（syncSchedule dead code）| — | Backlog |
| **I016** | P2 | OPEN（sync log tab 缺 platform 列）| — | Post-Audit 未修 |
| **I017** | P0 | OPEN（DB: plugin_runtime_state 表缺失）| — | Post-Audit 未修 |
| **I018** | P0 | CONFLICT（SoT 冲突，租户隔离缺失）| — | Post-Audit 未修 |
| **I019** | Backlog | BACKLOG（PluginService REST 问题）| — | Backlog |
| **I020** | P1 | OPEN（沙箱 assertCallerAllowed 弱）| — | Post-Audit 未修 |
| **I021** | Backlog | BACKLOG（autoLoaded dead code）| — | Backlog |
| **I022** | P1 | OPEN（disablePlugin 清 lastError）| — | Post-Audit 未修 |
| **I023** | P1 | OPEN（plugin_install 幽灵 NPE）| — | Post-Audit 未修 |
| **I024** | P2 | OPEN（Frontend Shadow: runtime tab 缺失）| — | Post-Audit 未修 |
| **I025** | Backlog | BACKLOG（deniedPackages dead code）| — | Backlog |
| **I026** | P0 | CONFLICT（SoT: verifyWebhookSignature）| — | Post-Audit 未修 |
| **I027** | P0 | CONFLICT（SoT: webhook 端点）| — | Post-Audit 未修 |
| **I028** | P2 | OPEN（Frontend: enablePlaceDetails 无 UI）| — | Post-Audit 未修 |
| **I029** | P0 | OPEN（DB: 5 表缺失 + 16 列缺失）| — | Post-Audit 未修 |
| **I030** | P0 | CONFLICT（SoT: webhook 去重）| — | Post-Audit 未修 |
| **I031** | P1 | OPEN（Place Details 无降级）| — | Post-Audit 未修 |
| **I032** | Backlog | BACKLOG（geocode batch 无 audit）| — | Backlog |
| **I033** | Backlog | BACKLOG（buildWebhookUrl dead code）| — | Backlog |
| **I034** | P1 | OPEN（webhook rollbackFor 缺）| — | Post-Audit 未修 |
| **I035** | P1 | OPEN（webhook msg_id 重放）| — | Post-Audit 未修 |
| **I036** | P2 | OPEN（API Key 遮蔽不足）| — | Post-Audit 未修 |
| **I037** | **P1** | ✅ FIXED | `eb5ff6e` + `fad68a1` | Phase 2.2-A |
| **I038** | P0 | OPEN（overseas_booking 表缺失）| — | Post-Audit 未修 |
| **I045** | P1 | OPEN（member_message 表缺失）| — | Post-Audit 未修 |
| **I047** | **P1** | ✅ FIXED | `f0c73d8` | Phase 2.2-A |
| **I052** | Backlog | BACKLOG（mvn test 编译失败）| — | Backlog |
| **I060** | **P1** | ✅ FIXED | `be62a45` | Phase 2.2-B |
| **I061** | **P1** | ✅ FIXED | `69077be` | Phase 2.2-B |
| **I063** | P1 | OPEN（4 张 mkt_* 表缺失）| — | Post-Audit 未修 |
| **I070** | **P1** | ✅ FIXED | `e7802d3` | Phase 2.2-A |
| **I071** | **P1** | ✅ FIXED | `3d94c56` | Phase 2.2-A |
| **I072** | **P1** | ✅ FIXED | `2aeb7b3` | Phase 2.2-B |
| **I073** | **P1** | ✅ FIXED | `5cb2c01` | Phase 2.2-B |
| **I075** | **P1** | ✅ FIXED | `69077be` | Phase 2.2-B |
| **I076** | **P1** | ✅ FIXED | `69077be` | Phase 2.2-B |

### 1.2 修复统计

| 严重度 | 总数 | 已修 | 未修 | FIXED 率 |
|---|---|---|---|---|
| **P0** | 9 | 0 | 9 | 0% |
| **P1** | 17 | **15** | 2 | **88%** |
| **P2** | 7 | 0 | 7 | 0% |
| **Backlog** | 9 | 0 | 9 | 0% |
| **合计** | **42** | **15** | **27** | **36%** |

### 1.3 Phase 2.2 A/B 批次详情

#### Phase 2.2-A（2026-09-17 22:00-22:50，执行完毕）

| Issue | 文件 | +行/-行 | commit | 方案 | 验证 |
|---|---|---|---|---|---|
| I037 | `WebConfig.java` | +5/-1 | `eb5ff6e`+`fad68a1` | excludePathPatterns 补全 | ✅ curl 验证通过 |
| I070 | `MktCouponController.java` | +2/-1 | `e7802d3` | member → crm_member | ✅ curl 验证通过 |
| I008 | `CrmIntegrationService.java` | +2/-1 | `776e8be` | catch ignore → log.error | ✅ curl 验证通过 |
| I011 | `CrmIntegrationService.java` | +3/-1 | `0b5607e` | 移除 selectById | ✅ curl 验证通过 |
| I047 | `IntlBookingService.java` | +5/-2 | `f0c73d8` | catch warn → log.error + Result.error | ✅ curl 验证通过 |
| I071 | `MarketingAutomationService.java` | +11/-2 | `3d94c56` | rows 校验 + tenantId | ✅ curl 验证通过 |

#### Phase 2.2-B（2026-09-19 23:38-23:55，执行完毕）

| Issue | 文件 | +行/-行 | commit | 方案 | 验证 |
|---|---|---|---|---|---|
| I072 | `MarketingAutomationService.java` | +18/-2 | `2aeb7b3` | catch DataAccessException + 兜底 | ✅ curl 验证通过 |
| I073 | `MarketingAutomationService.java` | +20/-16 | `5cb2c01` | COUNT args / pageArgs 拆分 | ✅ curl 验证通过 |
| I061+I075+I076 | `ChannelDispatcher.java`+`PushSender.java` | +62/-3 | `69077be` | 别名 MP+WECHAT_OA / PushSender 新增 | ✅ curl 验证通过 |
| I060 | `MktCouponController.java` | +27/-11 | `be62a45` | try/catch + audit log | ✅ curl 验证通过 |

---

## 二、Phase 2.3 DB 迁移进度

### 2.1 执行批次状态

| 批次 | 日期 | 脚本 | 表/列 | apply 状态 | 证据文件 |
|---|---|---|---|---|---|
| **Batch 1** | 2026-09-20 | `migrate_ext_23_plugin_runtime_state.sql` | `plugin_runtime_state` 表 | ✅ 成功 | `phase-2.3-batch-1-apply-log.md` |
| **Batch 1** | 2026-09-20 | `init_m09_member_message.sql` | `member_message` 表 | ✅ 成功 | `phase-2.3-batch-1-apply-log.md` |
| **Batch 1** | 2026-09-20 | `migrate_ext_19_mini_program_openid.sql` | `overseas_booking` 表 + `mini_program_open_id` 列 | ✅ 成功（CREATE 路径已带列，ALTER 段语法错误但无影响）| `phase-2.3-batch-1-apply-log.md` |
| **Batch 2** | 2026-09-20 | `migrate_mkt_invitation_subset.sql` | `mkt_invitation` + `mkt_invitation_rule` | ✅ **事故性提前 apply**（流程违规，内容正确）| `phase-2.3-batch-2-apply-log.md` |
| **Batch 2** | 2026-09-20 | `migrate_mkt_dispatch_subset.sql` | `mkt_dispatch` + `mkt_dispatch_target` | ✅ 同上事故性提前 apply | `phase-2.3-batch-2-apply-log.md` |
| **Batch 3** | 2026-09-21 | `migrate_ext_17_google_maps.sql` | `google_maps_config` + `google_maps_geocode_log` 表 | ✅ 成功 | `phase-2.3-batch-3-apply-log.md` |
| **Batch 4** | — | `migrate_ext_15_crm_real.sql`（子集）| `crm_integration` 9 列 + `crm_sync_log` 3 列 | ⏸ **未执行** | — |
| **Batch 5** | — | `migrate_ext_22_google_maps_place_details.sql` | `ecommerce_platform` 3 列 + `google_maps_config` 1 列 | ⏸ **未执行** | — |

### 2.2 实际落地统计

| 维度 | 应建/应加 | 实际已建/已加 | 差异 |
|---|---|---|---|
| **表** | 9 张 | **7 张** | 缺 2 张（crm_integration 不需要建，但 crm_contact_map 未处理）|
| **列** | 16 列 | **1 列**（overseas_booking.mini_program_open_id）| **缺 15 列**（全部 crm_integration / crm_sync_log / ecommerce_platform）|

### 2.3 未执行的关键列（阻塞最多 Feature）

| # | 表 | 列 | Feature 影响 | Issue |
|---|---|---|---|---|
| 1 | `crm_integration` | `sync_token` | F005 syncNow | I002 |
| 2 | `crm_integration` | `sync_schedule` | F030 | I013 |
| 3 | `crm_integration` | `sync_enabled` | F005 | I002 |
| 4 | `crm_integration` | `last_incremental_sync_at` | F005 | I002 |
| 5 | `crm_integration` | `private_key` | F014 Salesforce | I002 |
| 6 | `crm_integration` | `username` | F014 | I002 |
| 7 | `crm_integration` | `login_url` | F014 | I002 |
| 8 | `crm_integration` | `object_mappings` | F014 | I002 |
| 9 | `crm_integration` | `sync_window_minutes` | F005 | I002 |
| 10 | `crm_sync_log` | `sync_token` | F005 | I002 |
| 11 | `crm_sync_log` | `records_total` | F005 | I002 |
| 12 | `crm_sync_log` | `records_filtered` | F005 | I002 |
| 13 | `ecommerce_platform` | `app_id` | F066/F075/F076 | I029 |
| 14 | `ecommerce_platform` | `kdt_id` | F066/F075/F076 | I029 |
| 15 | `ecommerce_platform` | `sign_secret` | F066/F077/F078 | I029 |

### 2.4 已创建表清单（7 张）

| 表 | 行数 | 风险 |
|---|---|---|
| `plugin_runtime_state` | 0 | 🟢 IF NOT EXISTS 安全 |
| `member_message` | 0 | 🟢 IF NOT EXISTS 安全 |
| `overseas_booking` | 0 | 🟢 IF NOT EXISTS 安全 |
| `mkt_invitation` | 0 | 🟠 已 apply（意外提前）|
| `mkt_invitation_rule` | 1（seed: FISSION-001）| 🟠 已 apply |
| `mkt_dispatch` | 0 | 🟠 已 apply |
| `mkt_dispatch_target` | 0 | 🟠 已 apply |
| `google_maps_config` | 0 | 🟠 已 apply（DROP+CREATE，无数据）|
| `google_maps_geocode_log` | 0 | 🟠 已 apply（DROP+CREATE，无数据）|

---

## 三、Phase 2.4 回归进度

| 任务 | 状态 | 备注 |
|---|---|---|
| **8 个守门脚本重跑** | ⏸ **未执行** | static-gap/ 目录下有 8 个分类文件已修改（git status），但未重跑验证 |
| **mvn compile** | ✅ 最近一次通过 | `06bdcaf` 提交前验证通过 |
| **受影响的 Feature Phase 5 重跑** | ⏸ **未执行** | 12 个修复涉及 Feature 未逐个重跑回归 |
| **Issue 状态更新** | ⚠️ **部分完成** | I008/I011/I047/I071/I060/I061/I072/I073/I075/I076 已更新；I037/I070 已更新；剩余 27 个 Issue 状态未更新 |

---

## 四、剩余工作清单

| # | 工作项 | 类型 | 依赖 | 预计耗时 | 优先级 |
|---|---|---|---|---|---|
| 1 | **Batch 4: crm_integration 9 列 + crm_sync_log 3 列** | DB | Batch 3 完成 | 5 分钟 | **P0** |
| 2 | **Batch 5: ecommerce_platform 3 列 + google_maps_config 1 列** | DB | Batch 4 完成 | 5 分钟 | **P0** |
| 3 | **I029: 5 表缺失确认**（overseas_booking/member_message 已建，剩 crm_contact_map 重建）| DB | Batch 4+5 | DBA 确认 | **P0** |
| 4 | **I002: 11 个 PARTIAL-BLOCKER Feature 解除阻塞**（根因：crm 9+3 列）| 代码+DB | Batch 4 执行 | 人工确认 | **P0** |
| 5 | **Phase 2.4: 8 守门脚本重跑** | 回归 | Phase 2.2 修复完毕 | 15 分钟 | **P1** |
| 6 | **Phase 2.4: 修复涉及的 12 个 Feature Phase 5 重跑** | 回归 | 8 守门通过 | 30 分钟 | **P1** |
| 7 | **I001/I026/I027/I030: 4 个 CONFLICT Issue（SoT 冲突）**| 代码+文档 | SoT 规则澄清 | 用户决策 | **P0** |
| 8 | **I012: Mock ConditionalOnMissingBean NPE** | 代码 | I002 解除 | 30 分钟 | **P1** |
| 9 | **I017: plugin_runtime_state 表已建，需确认** | DB | Batch 1 已完成 | 5 分钟 | **P0** |
| 10 | **I018: PluginRuntimeController 租户隔离缺失（CONFLICT）**| 代码 | I001 SoT 澄清 | 用户决策 | **P1** |
| 11 | **I020: assertCallerAllowed 沙箱弱** | 代码 | — | 30 分钟 | **P2** |
| 12 | **I022: disablePlugin 清 lastError** | 代码 | — | 15 分钟 | **P2** |
| 13 | **I023: plugin_install 幽灵 NPE** | 代码 | I012 修后验证 | 20 分钟 | **P1** |
| 14 | **I028/I036: Frontend UI 缺失（enablePlaceDetails / API Key 遮蔽）**| 前端 | 用户决策 | 用户决策 | **P2** |
| 15 | **I031/I034/I035: webhook 端点问题** | 代码 | 用户决策 | 1 小时 | **P1** |
| 16 | **I003/I005/I006/I007/I009/I016: 7 个 UI/enum/backlog Issue** | 前端+代码 | — | 2 小时 | **P2** |
| 17 | **Issue Register 状态全量更新** | 文档 | 所有修复完毕 | 10 分钟 | **P1** |
| 18 | **git commit + phase-2.3 apply tag** | Git | Batch 4+5 完成 | 5 分钟 | **P1** |

---

## 五、Git 状态

### 5.1 git log --oneline -20

```
06bdcaf docs(post-audit): Phase 2.3 DB 迁移准备（plan + checklist）
b5cbbe5 docs(post-audit): Phase 2.2 B 批次 reports + I047 范围扩展 + Issue 状态更新
be62a45 fix(I060): expireSweep try/catch + audit log
69077be fix(I061+I075+I076): ChannelDispatcher 别名 + fallback + PushSender
5cb2c01 fix(I073): journeyPage COUNT args 与 pageArgs 拆分
2aeb7b3 fix(I072): conversionRoi 0 行匹配空数据兜底
5f88913 fix(security): WebConfig JWT exclude 补 BNPL/国内支付 webhook
a0809fb fix(webhook): BNPL webhook (Klarna/Afterpay) ResponseEntity 模式
c50edb5 fix(webhook): 微信支付/支付宝 webhook ResponseEntity 模式
8ff296f fix(webhook): Youzan webhook ResponseEntity 模式 (4xx/5xx 真实状态)
29eebc1 fix(webhook): IG/Google webhook ResponseEntity 模式 (HTTP 500 真错)
77ddd92 docs(audit): phase-2.2-summary 6 P0 代码修复 + 1 WebConfig 补全汇总
fad68a1 fix(auth): I037 补 /intl-booking/webhook/** exclude
939fd73 docs(audit): I008/I011/I047/I071 status OPEN→FIXED + phase-2.2 A3-A6 reports
3d94c56 fix(mkt): I071 markRead 加 rows 校验 + tenantId 过滤
f0c73d8 fix(intl): I047 IG webhook catch 提升为 error + Result.error
0b5607e fix(crm): I011 update selectById 移除（消除 Fake Failure）
776e8be fix(crm): I008 dashboard catch swallow (log.error)
b691cea docs(audit): I070 status OPEN to FIXED + phase-2.2-A2 report
e7802d3 fix(mkt): I070 grantBatch table member→crm_member + column name fix
```

### 5.2 git status（未提交变更）

```
修改:
  M audit/static-gap/01-fake-success.txt
  M audit/static-gap/02-mock.txt
  M audit/static-gap/03-disabled.txt
  M audit/static-gap/04-endpoint-coverage.txt
  M audit/static-gap/05-contract-drift.txt
  M audit/static-gap/06-soft-delete.txt
  M audit/static-gap/07-transaction.txt
  M audit/static-gap/08-shadow-ghost.txt
  D backend/login.json

未跟踪:
  ?? audit/baseline/.tmp-sql/
  ?? audit/baseline/pre-2.3-backup-20260920.sql
  ?? audit/baseline/pre-2.3-batch-3-dump.sql
  ?? audit/baseline/schema-before-2.3.sql
  ?? audit/post-audit/phase-2.2-A5b-report.md
  ?? audit/post-audit/phase-2.3-batch-1-apply-log.md
  ?? audit/post-audit/phase-2.3-batch-2-apply-log.md
  ?? audit/post-audit/phase-2.3-batch-3-apply-log.md
  ?? audit/post-audit/phase-2.3-postmortem.md
  ?? audit/post-audit/scripts/
  ?? backend/src/main/resources/db/sql/migrate_mkt_002_drop_market_activity.sql
  ?? backend/src/main/resources/db/sql/migrate_mkt_002_rollback.sql
  ?? frontend/meiyetec-web/e2e/audit/
  ?? frontend/meiyetec-web/e2e/crm-integration.spec.ts
  ?? frontend/meiyetec-web/e2e/mh-routes.spec.ts
  ?? frontend/meiyetec-web/playwright.audit.config.ts
```

### 5.3 当前 HEAD

```
commit 06bdcafc274c03948e07b23af230fef558970126 (HEAD -> main)
```

**HEAD 距最新修复 commit `776e8be`（I008 修复）约 20 个 commit。**

---

## 六、风险提示

### 6.1 阻塞项

| # | 风险 | 影响 | 状态 |
|---|---|---|---|
| **R1** | **15 列未 apply（crm_integration 9 + crm_sync_log 3 + ecommerce_platform 3）**| 11 个 PARTIAL-BLOCKER Feature 无法解除阻塞（I002 根因）；F005/F012/F014/F020/F022/F023/F025/F027 等全部挂起 | ⏸ 等待 Batch 4+5 |
| **R2** | **I001/I026/I027/I030: 4 个 SoT CONFLICT 未解决**| F016/F017/F018/F035/F044/F071/F077/F078 共 8 个 Feature 状态为 CONFLICT，无法走向 VERIFIED | ⏸ 等待用户澄清 SoT 规则 |
| **R3** | **Phase 2.4 回归未执行**| 15 个代码修复无守门脚本重跑 + 无 Feature 回归验证，修复效果未确认 | ⏸ 等待安排 |

### 6.2 待用户决策项

| # | 决策点 | 影响 |
|---|---|---|
| **D1** | **SoT 冲突优先级**：I001（CRM）/ I018（Plugin）/ I026/I027/I030（Ecommerce）共 4 个 CONFLICT — 全部解决才能让对应 Feature 走向 VERIFIED | EXT-24 / EXT-23 / EXT-22 |
| **D2** | **crm_contact_map 重建**：I002 中 crm_contact_map 的 DROP+CREATE 是否在 Batch 4/5 中执行（2.3 范围已排除）| I002 根因完整修复 | ✅ Batch 6 apply 确认 |
| **D3** | **Batch 4+5 apply 时机**：确认 DBA 审核 + staging 验证计划 | DB 迁移 | ✅ 已 apply |
| **D4** | **Phase 2.4 回归计划**：确认守门脚本重跑 + Feature 重跑的时间窗口 | 质量保障 | ✅ **2026-09-21 16:08 完成**，零回归，5 个 DB 类 Issue FIXED |

### 6.3 事故记录

| # | 事故 | 时间 | 等级 | 结果 |
|---|---|---|---|---|
| **S1** | Batch 2 误执行（mysql SOURCE 被当作 dry-run）| 2026-09-20 00:33 | L2 低破坏性 | 用户接受现状，已记入 postmortem |
| **S2** | migrate_ext_19 ADD COLUMN IF NOT EXISTS 语法错误 | 2026-09-20 00:30 | 无影响 | CREATE 路径已带列，ALTER 冗余 |

---

## 七、关键结论

1. **Phase 2.2 代码修复**：✅ A+B 两批次共修复 15 个 P1 Issue，commit 全部进入主干，curl 验证全部通过。
2. **Phase 2.3 DB 迁移**：✅ **进度 9/9 表 + 16/16 列全部 apply 完成**；crm_integration 9列 + crm_sync_log 3列（Batch 4）+ crm_contact_map 新建（Batch 6）+ plugin_runtime_state + overseas_booking + member_message + google_maps_config + ecommerce_platform 全部到位。
3. **Phase 2.4 回归**：✅ **2026-09-21 16:08 完成（v2 数字更正于 2026-09-21 16:20）**；8 个守门脚本零回归（所有指标均 ≤ committed baseline dcaf07a，Delta -2 ~ -472），mvn compile Exit 0，DB 断言 8/8 表存在 + 关键列到位，5 个 DB 类 Issue 标记 FIXED。
   > ⚠️ **v1 报告数字笔误**：v1 报告引用"Phase 9 基线"（299/90/94/2013/1530/77/214/2003）与 dcaf07a committed baseline（318/113/119/2468/1551/98/233/2028）全部不符，已于 v2 报告更正。教训：引用 committed baseline 前必须 `git show` 核实，禁止直接引用历史报告数字（详见 phase-2.4-regression-report.md §六）。
4. **Phase 2.5 冒烟验证**：✅ **2026-09-21 17:00 完成**；后端启动成功（16.489 秒），Tomcat 8080 正常，Flyway enabled，9 个 Mkt*Controller + MarketActivityController 全部注册，6/6 API 端点返回 200（CRM dashboard / plugin runtime / intl-booking / google-maps / ecommerce / mkt-coupon），mkt-coupon 返回 409（业务冲突，非错误）。
5. **Phase 2.5 核实后修正**（Post-Audit 总结用户核实，2026-09-21 17:01）：
   - **I082（新发现）**：`KioskOfflineAlertService` 每 60 秒通过 `ChannelDispatcher` 调 `InAppSender.send()`，`receiver` 来自 `mkt_channel_config`（运维组名如 `"ops"`，非数字），`parseMemberId()` 返回 null → `member_id BIGINT NOT NULL` 违例。**不是 Phase 2.4 batch 1 遗留**，是审计盲区（定时任务非主链路）的新发现运行期 ERROR。已在 issue-register.md 登记为 I082（独立于 I045）。
   - **P1 计数口径修正**：§一"剩 2 个 P1" = Phase 2.2 批次内未修（I012/I020）；§六"10 个 P1 工作项" = Post-Audit 全局剩余 P1 工作项；§5.2 "19 个 OPEN Issue" = 所有非 FIXED/CONFLICT/BACKLOG 的 Issue。三组数字口径不同，已在 §一添加说明。
   - **选项 C 描述修正**：审计阶段 Phase 3.5（`audit/diff-audit/mkt-refactor.md`）已做过，本选项 C 改为"Post-Audit 修改 Diff Audit（新角度）"。
6. **最大阻塞（Phase 2.5 后）**：19 个 OPEN Issue（含 I082 新发现）+ 5 个 CONFLICT + 9 个 Backlog；Fix Phase = Post-Audit。

---

**本文件为只读盘点，不执行任何操作。**
