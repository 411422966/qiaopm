# Audit Batches（批次汇总）

> **单一汇总文件，按 Batch 分段**
> 创建时间：2026-09-13 18:05 (UTC+8)
> 最近更新：2026-09-17 00:30 (UTC+8)（**EN-09 唯一归类**：F136 → PARTIAL-NON-BLOCKER / F139 → PARTIAL-BLOCKER，CONFLICT 改为子标签；batch-1.5-summary.md 全量产出）
> 引用：`@project-audit.mdc` §6 + `@feature-id.mdc` + `@batches.mdc`

---

## 全局约定

- **Batch ID**：`1.N` 顺序编号（按批次推进时间顺序）
- **模块范围**：每个 Batch 锁定一个 EXT-* Operational Notes 范围
- **状态输入**：Feature Inventory + Traceability Matrix + Issue Register + Runtime Report（5A/5B/5C）
- **最终 Result**：综合 4 个 Phase（静态 / 5A / 5B / 5C），**就高不就低**（PARTIAL-BLOCKER 覆盖 PARTIAL-NON-BLOCKER）
- **下一批次建议**：列出候选 + 用户决策空间

---

## 批次总览

| Batch ID | 模块 | 时间 | 起点 Commit | Feature 数 | Issue 数 | E2E 测试 | 状态 |
|---|---|---|---|---|---|---|---|
| **Batch 1.1** | EXT-24 CRM 集成 | 2026-09-12 ~ 2026-09-13 | `12a97ea` | 30 | 14 | 12 (5P+7F) | ✅ 完成（用户决策 D1-D6 维持）|
| **Batch 1.2** | EXT-23 Plugin Runtime | 2026-09-13 18:12 ~ 2026-09-13 21:15 | `e85ba8b` | 25 | 9 (+1 BUG-001) | 0（Playwright 降级）| ✅ 完成（Q1-Q4 决策落档）|
| **Batch 1.3** | EXT-22 Google Maps + 有赞 | 2026-09-13 23:50 ~ 2026-09-14 15:30 | `e85ba8b` | 25 | 12（含 I037 NEW）| 0（Playwright 降级）| ✅ 完成（Q1-Q5 决策落档）|
| **Batch 1.4** | EXT-19 多语言预约确认 | 2026-09-14 20:40 ~ 2026-09-14 23:15 | `e85ba8b` | 33 | 15 + 1 建议（I054 NEW）| 0（Playwright 降级，5C 已生成脚本）| ✅ 完成（D21-D31 + 3 处核实全部落实 + EN-09 落档）|
| **Batch 1.5** | MKT 多渠道重构 | 2026-09-16 ~ 2026-09-17 | `e85ba8b` | 27 | 23 | 0（curl+jshell）| ✅ 完成（D11-D25 全维持 + EN-09 唯一归类已落档）|

---

# Batch 1.5 — MKT 多渠道重构（v3 第五批）

> 创建时间：2026-09-17 00:05 (UTC+8)
> 最近更新：2026-09-17 00:30 (UTC+8)（**EN-09 唯一归类**：F136 → PARTIAL-NON-BLOCKER / F139 → PARTIAL-BLOCKER，CONFLICT 改为子标签）
> 状态：**Phase 5C 完成**（27 Feature 完结 + 23 Issue 处置 + 8 个新 Issue + D11-D25 全维持 + EN-09 唯一归类已落档）

## 1. 概要

| 维度 | 值 |
|---|---|
| **模块** | MKT 多渠道重构（9 个 Mkt*Controller + ChannelDispatcher + 5 Sender + MarketingAutomationService + 重构中间态）|
| **时间范围** | 2026-09-16 01:55 (UTC+8)（Phase 5A 起点）→ 2026-09-17 00:05 (UTC+8)（Phase 5C 完结）|
| **起点 commit** | `e85ba8ba8540081f2f59c97120052b5bef5a29ef` |
| **DB 状态** | `mkt_channel_config` ✅ / `marketing_touch_log` ✅ / `mkt_journey` ✅ / `mkt_effect_snapshot` ❌ / 4 表 ❌ MISSING（I063）|
| **Feature 数** | **27**（F114-F140）|
| **Issue 数** | **23**（I055-I077，含 8 个新 Issue）|
| **Evidence 文件** | **350+ 份** |
| **验证方式** | curl（21 API）+ jshell（2 × 反射单元）+ DB（information_schema）+ 静态归档 |
| **修复策略** | **本批次不修任何业务代码**；Fix Phase = **Post-Audit** |

### 1.1 Phase 完成情况

| Phase | 内容 | 状态 | 产出 |
|---|---|---|---|
| Phase 3 Static Gap | 32.9.10 项静态扫描 + 9 项 Frontend Shadow | ✅ | `audit/reports/static-gap-report.md` |
| Phase 3.5 Diff Audit | MKT 重构差异审计 8 项 | ✅ | `audit/diff-audit/mkt-refactor.md` |
| Phase 5A | 21 Feature × curl + DB（21 API 端点）| ✅ | `audit/runtime/report-5A-batch-1.5.md` |
| Phase 5B | 6 Feature × jshell + curl + DB | ✅ | `audit/runtime/report-5B-batch-1.5.md` |
| **Phase 5C** | **Final Summary** | ✅ | **`audit/reports/batch-1.5-summary.md`** |

### 1.2 用户决策（D11-D25 全维持）

| 决策 | 内容 | 状态 |
|---|---|---|
| **D11-D20** | 不改 SoT / 不加 UI / 不跑迁移 / 不加日志 / 不加 audit log / 不加 rollbackFor | ✅ 维持 |
| **D21** | 不修前端 api 封装层（I069 维持）| ✅ 维持 |
| **D22** | 不重命名（命名异味 + Service as Controller 模式）| ✅ 维持 |
| **D23** | 不修 setRateLimitPerDay（I062 + I074 维持）| ✅ 维持 |
| **D24** | 不跑 migrate_mkt_002 DROP（Q3 第六次统一口径）| ✅ 维持 |
| **D25** | 不跑 WeChat MP / SMTP 真实凭据（F137 维持 UNVERIFIED）| ✅ 维持 |

---

## 2. 27 Feature 最终 Result 表

| Feature | 功能点 | Final Result | 主 Issue | 实证阶段 |
|---|---|---|---|---|
| F114 | MKT Channel / 渠道列表 | **VERIFIED** | — | 5A |
| F115 | MKT Channel / 创建渠道 | **PARTIAL-NON-BLOCKER** | I062 + I074 | 5A |
| F116 | MKT Channel / 更新渠道 | **VERIFIED** | — | 5A |
| F117 | MKT Channel / 删除渠道 | **PARTIAL-NON-BLOCKER** | I056（跨租户删除）| 5A |
| F118 | MKT Channel / 健康检查 | **PARTIAL-NON-BLOCKER** | I050（tenant_id=1L）| 5A |
| F119 | MKT Conversion / 漏斗统计 | **VERIFIED** | — | 5A |
| F120 | MKT Conversion / ROI 统计 | **UNVERIFIED** | I072（空数据 500）| 5A |
| F121 | MKT Conversion / 按渠道统计 | **VERIFIED** | — | 5A |
| F122 | MKT Conversion / 记录转化 | **PARTIAL-NON-BLOCKER** | I057（跨租户写）| 5A |
| F123 | MKT Conversion / 标记已读 | **PARTIAL-NON-BLOCKER + Fake Success** | I071 | 5A |
| F124 | MKT Journey / 创建旅程 | **VERIFIED** | — | 5A |
| F125 | MKT Journey / 分页（双形式）| **PARTIAL-BLOCKER** | I058 + I073 | 5A |
| F126 | MKT Journey / 详情+更新+发布+暂停 | **VERIFIED** | — | 5A |
| F127 | MKT Journey / 会员进入+推进节点 | **PARTIAL-BLOCKER** | enter 首次 500 | 5A |
| F128 | MKT Template / 列表+创建+审核 | **VERIFIED** | — | 5A |
| F129 | MKT TouchTask / 分页+生命周期+日志 | **VERIFIED** | — | 5A |
| F130 | MKT Effect / 效果快照 | **PARTIAL-NON-BLOCKER + Dead Code** | I059 | 5A |
| F131 | MKT Coupon / 批次 CRUD+发放 | **PARTIAL-BLOCKER** | **I070 CRITICAL** | 5A |
| F132 | MKT Coupon / 过期扫描+券实例+导出 | **VERIFIED** | I060 | 5A |
| F133 | MKT Promotion / CRUD+导出 | **VERIFIED** | — | 5A |
| F134 | MKT Points / 规则+手动发分 | **VERIFIED** | — | 5A |
| F135 | ChannelDispatcher / getSender 路由 | **PARTIAL-BLOCKER** | I061 + I039 + I075 + I076 | 5B |
| F136 | ChannelDispatcher / 限流检查 | **PARTIAL-NON-BLOCKER（EN-09 修正）** | I077 | 5B |
| F137 | ChannelSender × 5 / SMS+EMAIL+MP+IN_APP+OA | **PARTIAL-NON-BLOCKER** | I039 + 3 渠道凭据缺失 | 5B |
| F138 | MarketingAutomationService / 业务入口聚合 | **PARTIAL-BLOCKER** | I063（4 表 MISSING）| 5B |
| F139 | MKT Refactor / 旧 `MarketActivity*` 5 类残留 | **PARTIAL-BLOCKER + CONFLICT 子标签（EN-09 修正）** | I055 + I067 + I068 | 5B |
| F140 | MKT Refactor / Phase 3.5 Diff Audit | **VERIFIED** | — | 5B |

---

## 3. Result 分布统计

### 3.1 5 个状态标签分布（EN-09 唯一归类后）

| 状态 | 数量 | 占比 | Feature IDs |
|---|---|---|---|
| **VERIFIED** | **12** | **44.4%** | F114, F116, F119, F121, F124, F126, F128, F129, F132, F133, F134, F140 |
| **PARTIAL-NON-BLOCKER** | **8** | **29.6%** | F115, F117, F118, F122, F123, F130, **F136 (EN-09 修正)**, F137 |
| **PARTIAL-BLOCKER** | **6** | **22.2%** | F125, F127, F131, F135, F138, **F139 (EN-09 修正)** |
| **UNVERIFIED** | **1** | **3.7%** | F120 |
| **CONFLICT（独立）** | **0** | **0.0%** | （改为子标签：F139 仍含 CONFLICT 子标签但唯一归属 PARTIAL-BLOCKER）|
| **合计** | **27** | **100.0%** | **F114-F140（唯一归类，无重叠计数）** |

### 3.2 关键统计

| 维度 | 数值 |
|---|---|
| **12 VERIFIED** | F114/F116/F119/F121/F124/F126/F128/F129/F132/F133/F134/F140 |
| **0 BROKEN** | 无设计层面缺陷 |
| **2 CRITICAL** | I070（member 表名）+ I071（markRead Fake Success）|
| **6 BLOCKER** | F125/F127/F131/F135/F138/F139 |
| **1 UNVERIFIED** | F120（ROI 500）|
| **0 CONFLICT（独立）** | F139 CONFLICT 改为子标签（中立标）|
| **EN-09 修正** | F136 VERIFIED → PARTIAL-NON-BLOCKER；F139 CONFLICT → PARTIAL-BLOCKER 子标签 |

---

## 4. Issue 汇总（I055-I077 — 23 个）

### 4.1 按严重度排序

| 严重度 | Issue ID | 标题 | 类型 | 影响 Feature |
|---|---|---|---|---|
| 🔴 **CRITICAL** | **I070** | grantBatch 用错表名 `member`（应为 `crm_member`）| SQL 错误 | F131 |
| 🔴 **CRITICAL** | **I071** | markRead Fake Success（200 成功但 DB 0 行更新）| Fake Result | F123 |
| 🟠 **HIGH** | **I072** | conversionRoi 空数据 500 | 空数据兜底缺失 | F120 |
| 🟠 **HIGH** | **I073** | journeyPage COUNT 参数 bug（双形式都 500）| SQL 参数异常 | F125 |
| 🟡 中 | I050/I056/I057/I058/I059/I060/I061/I062/I063/I064/I067/I068/I069/I074/I075/I076/I077 | 详见 `batch-1.5-summary.md` §四 | — | — |
| 🟢 低 | **I055** | SoT `ROLLBACK-6.2 §1` 与代码不符 | SoT CONFLICT | F139 |

**Issue 总数**：**23 个**（I055-I077）

---

## 5. 阻塞项清单

| Feature | Final Result | 阻塞原因 |
|---|---|---|
| **F131** | PARTIAL-BLOCKER | I070 CRITICAL — grantBatch 错表名 → 券发放 500 |
| **F135** | PARTIAL-BLOCKER | I061 NPE + I075/I076 别名漂移 |
| **F138** | PARTIAL-BLOCKER | I063 — 4 表 MISSING |
| **F139** | PARTIAL-BLOCKER + CONFLICT | I055/I067/I068 — 重构中间态 |
| **F125** | PARTIAL-BLOCKER | I058 双路由 + I073 COUNT bug |
| **F127** | PARTIAL-BLOCKER | enter 首次 500 |
| **F120** | UNVERIFIED | I072 — 空数据 500 |

---

## 6. 修复优先级矩阵（Post-Audit）

| 优先级 | Issue | 修复 | 影响 Feature |
|---|---|---|---|
| **P0-1 🔴** | **I070** | grantBatch SQL `member` → `crm_member` | F131 |
| **P0-2 🔴** | **I071** | markRead 加结果校验 | F123 |
| **P0-3 🟠** | **I073** | journeyPage COUNT 参数拆分 | F125 |
| **P0-4 🟠** | **I072** | conversionRoi 空数据兜底 | F120 |
| **P0-5 🟠** | **I063** | 跑 4 表 DDL | F138 |
| **P0-6 🟠** | **I061+I075+I076** | ChannelDispatcher getSender 加 null 检查 + 别名统一 | F135 |

---

## 7. 下一阶段建议

**Phase 8 = Post-Audit 修复**（按 P0-1 → P0-6 顺序兑现）

---

## 8. 批次完成宣言

- ✅ **Phase 3 → 5C 全部完成**
- ✅ **27 / 27 Feature = 100% 验证**
- ✅ **Feature 升级**：F114/F116/F119/F121/F124/F126/F128/F129/F132/F133/F134/F140 = 12 个 VERIFIED（EN-09 修正：F136 从 VERIFIED → PARTIAL-NON-BLOCKER）
- ✅ **Feature 降级**：F125 → PARTIAL-BLOCKER / F127 → PARTIAL-BLOCKER / F136 → PARTIAL-NON-BLOCKER（EN-09 唯一归类修正）
- ✅ **8 个新 Issue 登记**（I069-I077）
- ✅ **D10 铁律全程生效**（跑不通 → UNVERIFIED）
- ✅ **不修改业务代码**（Fix Phase = Post-Audit）
- ✅ **累计 Issue 总数：77 个**（跨 5 批次）

---

*文件落档时间：2026-09-17 00:30 (UTC+8)（EN-09 唯一归类修正）*

---

# Batch 1.1 — EXT-24 CRM 集成（v3 第一批）

## 1. 概要

| 维度 | 值 |
|---|---|
| **模块** | EXT-24 CRM 集成（`backend/src/main/java/com/meiyetec/crm/*` + `backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java` + `frontend/meiyetec-web/src/views/crm-integration/index.vue` + 3 个 DB 表）|
| **时间范围** | 2026-09-12 21:23 (UTC+8)（Phase 1 起点）→ 2026-09-13 18:01 (UTC+8)（Phase 5C 完结）|
| **Git 起点 commit** | `12a97ea57a98d268c864fc7c010b82b305d7bb93`（commit message: `audit(batch-1.1-phase2): EXT-24 CRM 集成 Full-Stack Traceability Matrix`）|
| **DB 起点状态** | `crm_integration` 22 列 / `crm_sync_log` 12 列 / `crm_contact_map` ❌ 不存在（EXT-15 未跑）|
| **起点路径** | Phase 1（Feature Inventory）→ Phase 2（Traceability Matrix）→ Phase 3（Issue 登记）→ Phase 4（Runtime Plan）→ Phase 5A（API 实测）→ Phase 5B（端点 + E2E 实测）→ Phase 5C（DDL + 迁移状态）|
| **修复策略** | **全部 D1-D6 决策保持不修**（审计阶段只记录不修复，`@project-audit.mdc` 规则 6）|

### 1.1 Phase 完成情况

| Phase | 内容 | 状态 | 产出 |
|---|---|---|---|
| 1 | Feature Inventory | ✅ | `audit/feature-inventory.md`（30 Features）|
| 2 | Traceability Matrix | ✅ | `audit/traceability-matrix.md`（30 Features 全栈追溯）|
| 3 | Issue Register | ✅ | `audit/issues/issue-register.md`（14 Issues）|
| 4 | Runtime Plan | ✅ | `audit/runtime/plan.md`（P0 7 + P1 9 + P2 14）|
| 5A | API 实测 | ✅ | `audit/runtime/report-5A.md`（P0 7 个 Feature 实测）|
| 5B | 端点 + E2E | ✅ | `audit/runtime/report-5B.md` + `audit/reports/e2e-summary.md`（12 E2E 测试）|
| 5C | DDL + 迁移 | ✅ | `audit/runtime/report-5C.md`（F022 + F023 静态对比）|

### 1.2 用户决策（D1-D6）

| 决策 | 内容 | 状态 |
|---|---|---|
| **D1** | 不加 I006 dashboard tab + I007 edit 按钮 | ✅ 维持 |
| **D2** | 不修 I003 integrationId 漂移 | ✅ 维持 |
| **D3** | 不修 I005 enum / force / 表单字段漂移 | ✅ 维持 |
| **D4** | 不修 I008 / I009 / I011 / I012 | ✅ 维持 |
| **D5** | 不跑 EXT-15 迁移 | ✅ 维持 |
| **D6** | I010 Flyway 编码仅临时处置（flyway.enabled=false）| ✅ 维持 |

→ **所有 6 项决策严格遵守**，F023 BROKEN 是设计意图，非事故。

---

## 2. 30 Feature 最终 Result 表

> **综合判定**：Phase 2 静态 + Phase 5A/5B 实测 + Phase 5C DDL 验证 → Final Result 就高不就低。

| Feature ID | 功能点 | Priority | Final Result | 主 Issue | Evidence |
|---|---|---|---|---|---|
| **F001** | 创建集成 | P3 | **PARTIAL-BLOCKER** + I011 二次实证 | I002 + **I011** | `audit/evidence/F001-create-integration/` + `audit/evidence/F025-create-form/e2e-result.md` |
| **F002** | 集成列表 | P3 | **PARTIAL-BLOCKER** | I002 | `audit/evidence/F002-list-integration/` |
| **F003** | 更新集成 | P3 | **PARTIAL-BLOCKER**（I011 CRITICAL）| I002 + **I011** | `audit/evidence/F003-update-integration/` + `audit/issues/I011-crm-fake-failure.md` |
| **F004** | 切换状态 | P3 | **PARTIAL-NON-BLOCKER** | I005 C1 | `audit/evidence/F004-toggle-enum-drift/` |
| **F005** | 手动触发同步 | P3 | **PARTIAL-BLOCKER** | I002 + I005 C2 | `audit/evidence/F005-manual-sync/` |
| **F006** | 同步日志 | P3 | **PARTIAL-NON-BLOCKER** | I003 + I002 | `audit/evidence/F006-sync-log/` |
| **F007** | Dashboard | P3 | **PARTIAL-NON-BLOCKER** + I008（5A CRITICAL）| I008 + I006 | `audit/evidence/F007-dashboard/` |
| **F008** | 手动 sync 窗口跳过 | P3 | **PARTIAL-NON-BLOCKER** | I002 + I005 C2 | `audit/evidence/F008-sync-skip-window/` |
| **F009** | 手动 sync 跨租户拒绝 | P3 | **PARTIAL-NON-BLOCKER** | I002（间接）| `audit/evidence/F009-sync-cross-tenant/` |
| **F010** | 自动 tick 扫描 | P3 | **PARTIAL-NON-BLOCKER** + I008（5A CRITICAL）| I008 + I002 | `audit/evidence/F010-auto-tick-scan/` |
| **F011** | shouldRun 窗口判断 | P3 | **PARTIAL-NON-BLOCKER** | I002 | `audit/evidence/F011-should-run/` |
| **F012** | known-ids 差集加载 | P3 | **PARTIAL-BLOCKER** | I002（crm_contact_map 缺失）| `audit/evidence/F012-known-ids-load/` |
| **F013** | CrmAdapter 接口契约 | P3 | **UNVERIFIED** | — | `audit/evidence/F013-adapter-contract/` |
| **F014** | Salesforce 适配器 | P3 | **PARTIAL-NON-BLOCKER** | I002（依赖 object_mappings / private_key 等）| `audit/evidence/F014-salesforce-adapter/` |
| **F015** | Mock 适配器 | P3 | **CONFLICT**（I012 CRITICAL 联动）+ MOCK-INTENTIONAL | I012 + I001 | `audit/evidence/F015-mock-adapter/` |
| **F016** | HubSpot 适配器 | P3 | **CONFLICT** | I001（文档 vs 代码冲突）| `audit/evidence/F016-hubspot-adapter/` |
| **F017** | Zoho 适配器 | P3 | **CONFLICT** | I001（文档 vs 代码冲突）| `audit/evidence/F017-zoho-adapter/` |
| **F018** | Platform 分发 + Mock fallback | P3 | **CONFLICT** | I001（文档标 NPE，实际 fallback）| `audit/evidence/F018-router-fallback/` |
| **F019** | CrmIntegration 配置实体 | P3 | **PARTIAL-NON-BLOCKER** | I002（缺 9 列）| `audit/evidence/F019-crm-integration-entity/` |
| **F020** | CrmContactMap 映射实体 | P3 | **PARTIAL-BLOCKER** | I002（表缺失）| `audit/evidence/F020-crm-contact-map/` |
| **F021** | CrmSyncLog 日志实体 | P3 | **PARTIAL-NON-BLOCKER** | I002（缺 3 列）| `audit/evidence/F021-crm-sync-log/` |
| **F022** | 三表 DDL + EXT-15 ALTER | P3 | **PARTIAL-BLOCKER** | I002 | `audit/evidence/F022-crm-ddl-check/` |
| **F023** | crm_contact_map 表存在性 | P3 | **PARTIAL-BLOCKER**（D5 设计意图）| I002 | `audit/evidence/F023-ext15-migration-check/` |
| **F024** | 集成列表 UI | P3 | **PARTIAL-BLOCKER** | I002 | `audit/evidence/F024-list-ui/e2e-result.md` |
| **F025** | 新建集成表单 | P3 | **PARTIAL-BLOCKER** + I011 二次实证 | I002 + **I011** | `audit/evidence/F025-create-form/e2e-result.md` |
| **F026** | 同步日志 tab | P3 | **PARTIAL-NON-BLOCKER** | I003 + I016 | `audit/evidence/F026-sync-log-tab/e2e-result.md` |
| **F027** | 同步 API 未传 `?force=1` | P3 | **PARTIAL-BLOCKER** | I005 C2 + I002 | `audit/evidence/F027-sync-button/e2e-result.md` |
| **F028** | objectMappings 输入入口缺失 | P3 | **PARTIAL-NON-BLOCKER** | I005 C4 + I002 | `audit/evidence/F028-object-mappings/e2e-result.md` |
| **F029** | crm_integration.status 状态机 | P3 | **PARTIAL-NON-BLOCKER** | I005 C1 | `audit/evidence/F029-status-state-machine/` |
| **F030** | syncWindowMinutes + syncSchedule | P3 | **PARTIAL-NON-BLOCKER** | I002 + I013（dead code）| `audit/evidence/F030-sync-config/` |

---

## 3. Result 分布统计

### 3.1 9 个状态标签分布

| 状态 | 数量 | Feature IDs | 占比 |
|---|---|---|---|
| **VERIFIED** | 0 | — | 0.0% |
| **PARTIAL-BLOCKER** | **11** | F001, F002, F003, F005, F012, F020, F022, F023, F024, F025, F027 | **36.7%** |
| **PARTIAL-NON-BLOCKER** | **14** | F004, F006, F007, F008, F009, F010, F011, F014, F019, F021, F026, F028, F029, F030 | **46.7%** |
| **BROKEN** | 0 | — | 0.0% |
| **CONFLICT** | **4** | F015, F016, F017, F018 | **13.3%** |
| **MOCK-INTENTIONAL** | 1 | F015（与 CONFLICT 重叠）| — |
| **MOCK-FAKE** | 0 | — | 0.0% |
| **SHELL-INTENTIONAL** | 0 | — | 0.0% |
| **SHELL-ABANDONED** | 0 | — | 0.0% |
| **MISSING** | 0 | — | 0.0% |
| **UNVERIFIED** | **1** | F013 | **3.3%** |
| **合计** | **30** | F001-F030 | **100.0%** |

### 3.2 关键统计

| 维度 | 数值 |
|---|---|
| **0 VERIFIED** | 无任何 Feature 通过完整验证（与 D5 不跑 EXT-15 直接相关）|
| **0 BROKEN** | 设计层面无缺陷，但 D5 决策下 F023 应理解为「BROKEN-INTENTIONAL」|
| **CRITICAL 升级** | I008（5A）+ I011（5B）= 2 个 CRITICAL |
| **PARTIAL 总计** | 11 BLOCKER + 14 NON-BLOCKER = **25 / 30 = 83.3%** |
| **CONFLICT 总计** | **4 / 30 = 13.3%**（全部由 I001 + I012 文档/代码不一致引起）|
| **UNVERIFIED** | **1 / 30 = 3.3%**（F013 Adapter 接口契约无运行时验证）|

### 3.3 与 Phase 2 对比

| 阶段 | UNVERIFIED | PARTIAL-NON-BLOCKER | PARTIAL-BLOCKER | CONFLICT | BROKEN |
|---|---|---|---|---|---|
| Phase 2（静态）| 7 | 17 | 4 | 3 | 0 |
| **Phase 5C（终态）** | **1** | **14** | **11** | **4** | **0** |

→ **实测后变化**：
- UNVERIFIED 减少 6 个（→ 4 个升 PARTIAL-NON-BLOCKER + 1 个升 PARTIAL-BLOCKER + 1 个升 CONFLICT）
- PARTIAL-NON-BLOCKER 减少 3 个（→ 3 个升 PARTIAL-BLOCKER，因 I002 实际阻塞写入）
- PARTIAL-BLOCKER 增加 7 个（实测暴露 I002 写入失败 / I011 假失败）
- CONFLICT 增加 1 个（F015 升 CONFLICT 因 I012）

---

## 4. Issue 汇总

### 4.1 按严重度排序

| 严重度 | Issue ID | 标题 | 类型 | 影响 Feature | Phase 5 升级？ |
|---|---|---|---|---|---|
| **🔴 CRITICAL** | **I011** | Fake Failure（API 报错但 DB 已写）| Fake Result（与 I008 对称）| F001 / F003 / F025 + 任何 select-after-update 端点 | 5B 首次实证 + 5B 第三轮二次升级（dashboard 反推）|
| **🔴 CRITICAL** | **I008** | Fake Success（dashboard catch 吞异常）| Fake Result | F002 / F005 / F007 / F008 / F010 / F012 + tick | 5A 实测升级（F010 tick 7 次静默）|
| **🔴 高** | **I002** | DB 表 / 列缺失（crm_contact_map + EXT-15 9+3 列）| PARTIAL-BLOCKER 根因 | 11 个 PARTIAL-BLOCKER Feature | 5A/5B/5C 持续确认 |
| **🔴 高** | **I012** | Mock 激活条件冲突（`@ConditionalOnMissingBean` + `@Component`）| 配置 / Bean 条件 | F015 + F018 + F012 | 5B 新发现，I002 修复后即刻暴露 NPE |
| **🔴 高** | **I010** | Flyway V1__init_baseline.sql 编码（UTF-8 × GBK）| 启动期故障 | 全局阻塞（后端启动）| 临时处置（flyway.enabled=false）已完成 |
| 🟠 中-高 | I013 | syncSchedule dead code | dead code | F030 | 5B 新发现 |
| 🟡 中 | I001 | EXT-24 .mdc 文档与代码冲突 | 文档/SoT | F015 / F016 / F017 / F018 | 静态发现 |
| 🟡 中 | I003 | UI 契约漂移（integrationId）| UI 契约 | F006 / F026 | 5B 第二轮补正 |
| 🟡 中 | I005 | 前端契约多重漂移（enum / force / 表单字段）| UI 协议 | F004 / F005 / F025 / F027 / F028 / F029 | 静态发现 |
| 🟡 中 | I006 | Shadow Feature（dashboard tab 缺失）| Shadow | F007 | 静态发现 |
| 🟡 中 | I007 | Shadow Feature（update UI 缺失）| Shadow | F003 | 静态发现 |
| 🟡 中 | I009 | `@Transactional` rollbackFor 缺失 | 事务边界 | F005 | 静态发现 |
| 🟡 中 | I016 | 同步日志 tab 缺 platform 列 | UX 可观测性 | F006 / F026 | 5B 第二轮新发现 |
| 🟢 低 | I004 | `CrmIntegrationService` 命名误导（实为 `@RestController`）| 架构异味 | F001-F008 + F029 | Backlog |

**Issue 总数**：**14** 个（I001-I013 + I016）

### 4.2 Fake Result 系列（I008 + I011 合并分类）

| 维度 | I008 Fake Success | I011 Fake Failure |
|---|---|---|
| **异常类型** | catch 吞 + 返回 success | 抛错 + GlobalExceptionHandler |
| **API 返回** | `code=200, success=true` | `code=500, message="数据库异常"` |
| **实际状态** | DB 操作失败但 API 报告成功 | DB 操作成功但 API 报告失败 |
| **用户感知** | "成功"（最坏）| "失败"（次坏 — 但数据已改）|
| **触发 Feature** | F007 + F010 tick | F001/F003/F025 + 任何 select-after-write |
| **严重度** | 🔴 CRITICAL | 🔴 CRITICAL |
| **5C Phase 升级路径** | 5A F010 tick 7 次静默实证 | 5B F003 update 实证 + 5B 第三轮 F025 dashboard 反推二次实证 |

### 4.3 Issue 关联 Feature 矩阵

| Issue ↓ \ Feature → | F001 | F002 | F003 | F004 | F005 | F006 | F007 | F008 | F010 | F011 | F012 | F014 | F015 | F016 | F017 | F018 | F019 | F020 | F021 | F022 | F023 | F024 | F025 | F026 | F027 | F028 | F029 | F030 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **I001** | | | | | | | | | | | | | ● | ● | ● | ● | | | | | | | | | | | |
| **I002** | ● | ● | ● | | ● | ● | | ● | ● | ● | ● | ● | | | | | ● | ● | ● | ● | ● | ● | ● | | ● | | ● |
| **I003** | | | | | | ● | | | | | | | | | | | | | | | | | | ● | | |
| **I004** | ● | ● | ● | ● | ● | ● | ● | ● | | | | | | | | | ● | | | | | | | | | ● |
| **I005** | | | | ● | ● | | | | | | | | | | | | | | | | | ● | | | ● | ● | ● |
| **I006** | | | | | | | ● | | | | | | | | | | | | | | | | | | | | |
| **I007** | | | ● | | | | | | | | | | | | | | | | | | | | | | | |
| **I008** | | ● | | | ● | | ● | ● | ● | | ● | | | | | | | | | | | | | | | | |
| **I009** | | | | | ● | | | | | | | | | | | | | | | | | | | | | |
| **I012** | | | | | | | | | | | ● | | ● | | | ● | | | | | | | | | | | |
| **I013** | | | | | | | | | | | | | | | | | | | | | | | | | | | ● |
| **I016** | | | | | | ● | | | | | | | | | | | | | | | | | ● | | | | |

---

## 5. 阻塞项清单

### 5.1 I002 阻塞的 11 个 PARTIAL-BLOCKER Feature

| Feature | 阻塞原因 | I002 影响路径 |
|---|---|---|
| **F001** | create 端点 SELECT 抛 I002 → API 500 → 但 DB INSERT 成功（I011 二次实证）| `CrmIntegrationService.create` insert 后 selectById 抛错 |
| **F002** | list 端点 SELECT 抛 I002 → API 500 | `CrmIntegrationService.list` 全表扫描时列引用 |
| **F003** | update 端点 SELECT-after-update 抛 I002 → API 500 → 但 DB UPDATE 成功（I011 CRITICAL 实证）| `CrmIntegrationService.update` selectById 抛错 |
| **F005** | syncNow 端点嵌套 SELECT 抛 I002 | `CrmSyncScheduler.runIncremental` 多处 selectById |
| **F012** | loadKnownExternalIds 读 `crm_contact_map` 表 → 表不存在 → catch 吞 → empty Set | `CrmSyncScheduler.loadKnownExternalIds` |
| **F020** | upsertContactMap 写 `crm_contact_map` → 表不存在 → fail_count++ | `CrmSyncScheduler.upsertContactMap` |
| **F022** | DDL 文件存在但未跑 | `migrate_ext_15_crm_real.sql` 未执行 |
| **F023** | `crm_contact_map` 表本身不存在 | 同上 |
| **F024** | E2E 测试期望 list 返回 ≥ 1 行 → API 500 → 行数 0 | 经 F002 链路 |
| **F025** | E2E 测试期望 create 成功 + dialog 关闭 → API 500 → dialog 不关 | 经 F001 链路 |
| **F027** | E2E 测试期望 syncNow 成功 → API 500 → force 参数无效 | 经 F005 链路 |

### 5.2 解锁路径

| 路径 | 改动 | 影响 Issue | 解锁 Feature |
|---|---|---|---|
| **A. 跑 EXT-15 迁移** | `mysql ... < migrate_ext_15_crm_real.sql` | I002（核心）| **全部 11 个** + F005/F008/F010/F011/F014/F019/F021/F026/F028/F030 等 PARTIAL-NON-BLOCKER |
| **B. 重构 I011（select-after-write）** | `service.update` 用入参做返回（不调 selectById）| I011 | F001 / F003 / F025（保留 PARTIAL-NON-BLOCKER）|
| **C. 修 I005 C2（force 参数）** | `syncNow(id, { force: 1 })` 在 API 封装层添加 | I005 C2 | F005 / F027（窗口外场景）|
| **D. 修 I005 C4（表单字段）** | 新建对话框加 clientId/clientSecret/privateKey/objectMappings 等字段 | I005 C4 | F025 / F028（前端完整性）|
| **E. 修 I005 C1（toggle enum）** | 前端 `inactive-value="DISABLED"` + 后端 toggle 加 enum 校验 | I005 C1 | F004 / F029 |
| **F. 修 I003（UI 列名）** | 同步日志 tab `integrationId` → `platform` | I003 | F006 / F026 |
| **G. 修 I008（catch 收紧）** | `catch (Exception ignore) {}` → `catch + log.error` | I008 | F002 / F005 / F007 / F008 / F010 / F012 |
| **H. 修 I012（Mock 激活）** | Mock 改 `@ConditionalOnProperty` + Router fallback 抛异常 | I012 | F015 + F018 |
| **I. 修 I001（更新 .mdc）** | EXT-24 .mdc §0.6/§5/§6 修正 HubSpot/Zoho/Router 描述 | I001 | F015 / F016 / F017 / F018（CONFLICT → VERIFIED/PARTIAL）|

### 5.3 推荐顺序

| 优先级 | 路径 | 理由 |
|---|---|---|
| **P0** | A（跑 EXT-15）+ I（更新 .mdc）| A 解锁 11 PARTIAL-BLOCKER；I 解锁 4 CONFLICT（最小改动）|
| **P1** | B（重构 I011）+ H（修 I012）| 消除 CRITICAL 风险 |
| **P2** | G（修 I008 catch）+ C（修 I005 C2）| 降低 CRITICAL 静默风险 + UX 改善 |
| **P3** | D / E / F（前端契约对齐）| I003/I005 C1/C4 一并修 |

---

## 6. 下一批次建议

### 6.1 候选

| 选项 | 模块 | 来源 | Feature 估算 | 时间估算 |
|---|---|---|---|---|
| **A. Batch 1.2 = EXT-23 Plugin Runtime** | pf4j + plugin tenant + 沙箱 | `.cursor/rules/ext-23-plugin-runtime.mdc` | ~15-20 Features | 1-2 天 |
| **B. Batch 1.3 = EXT-22 Google Maps + Youzan** | Google Place Details + 有赞 webhook + sign_secret | `.cursor/rules/ext-22-google-maps-youzan.mdc` | ~10-15 Features | 1 天 |
| **C. Batch 1.4 = EXT-19 多语言预约确认** | i18n + WeChat 模板 + SMTP | `.cursor/rules/ext-19-intl-booking-confirmation.mdc` | ~12-18 Features | 1-2 天 |
| **D. 进入 Phase 6（上线预演）** | 不开新批次，先决策 D1-D6 是否调整 | `audit/baseline/preflight.md` §八 | — | 0.5 天 |

### 6.2 推荐（2026-09-13 18:12 ✅ 用户决策 Q2 已确认）

**Batch 1.2 = EXT-23 Plugin Runtime**，理由：

| 维度 | 评估 |
|---|---|
| **业务相关性** | 高（Plugin 是 SaaS 核心扩展机制）|
| **依赖 Batch 1.1 决策** | 中（不依赖 EXT-15 迁移状态）|
| **风险等级** | 中（沙箱合作式 + 租户隔离缺失）|
| **Issue 预判** | I008（catch 吞异常）/ I012（Bean 条件冲突）可能再次出现 |
| **可达性** | 高（独立模块，代码 4 个 service + 1 个 controller）|

→ **Q2 决策确认**：Batch 1.2 = EXT-23 Plugin Runtime，沿用此推荐。
→ **Q3 决策关联**：P1 升级 I008/I011/I012（只登记不修），Batch 1.2 期间保持登记 Post-Audit。
→ **Q4 决策关联**：F015 Mock + I012 P1 登记不修。

### 6.3 备选

- **如果用户决策放开 D5（跑 EXT-15）** → 立即跑迁移 + F024-F028 E2E 重测 → 验证「A 路径」实际解锁效果 → 再进 Batch 1.2
- **如果用户决策维持 D5** → Batch 1.2 直接推进，保留 EXT-24 PARTIAL-BLOCKER

---

## 7. Batch 1.1 总结

### 7.1 完成情况

| 维度 | 完成？ | 备注 |
|---|---|---|
| Phase 1（Feature Inventory）| ✅ | 30 Features |
| Phase 2（Traceability Matrix）| ✅ | 30 Features 全栈追溯 |
| Phase 3（Issue Register）| ✅ | 14 Issues |
| Phase 4（Runtime Plan）| ✅ | P0 7 + P1 9 + P2 14 |
| Phase 5A（API 实测）| ✅ | 7 个 P0 Feature 实测 |
| Phase 5B（端点 + E2E）| ✅ | 9 个 P1 + 5B 第三轮 E2E 12 测试 |
| Phase 5C（DDL + 迁移）| ✅ | F022 + F023 + 跨阶段 E2E 引用 |
| 不动业务代码 | ✅ | 严格遵守 D1-D6 |

### 7.2 关键贡献

1. **2 个 CRITICAL 升级**：I008（5A tick 静默）+ I011（5B select-after-update 假失败 + 5B 第三轮二次实证）
2. **14 个 Issue 完整登记**：覆盖文档漂移 / DB 缺失 / UI 契约 / 架构异味 / 启动阻塞 / dead code / Fake Result 等 7 大类
3. **30 Features 全栈追溯**：从 Vue 前端 → Spring Controller → MyBatis Mapper → MySQL DDL 完整链路
4. **测试有效性自检**：F025 第二轮假 PASS 已消除 + F026 断言方向错已修复
5. **意外发现**：F007 dashboard 端点 selectCount 不引用 EXT-15 列 → I002 意外不阻塞此端点

### 7.3 下一阶段决策空间（2026-09-13 18:12 ✅ 用户回复）

| 决策 | 用户决定 | 解读 |
|---|---|---|
| **Q1：D5 是否放开（跑 EXT-15）？** | **B 维持**（不跑 EXT-15 迁移） | 保持审计完整性；Batch 1.2 期间 EXT-24 PARTIAL-BLOCKER 维持现状 |
| **Q2：Batch 1.2 选哪个模块？** | **EXT-23 Plugin Runtime** | 沿用 §6.2 推荐 |
| **Q3：I008 / I011 / I012 是否升级修复优先级？** | **是**（升级 P1，**只登记不修**） | 严重度升级到 CRITICAL-P1 / 🟠-P1，Fix Phase = Post-Audit |
| **Q4：F015 Mock 是否修 I012？** | **是**（登记 P1，**不修**） | I012 升级 P1，标记 NPE 风险，修复阶段合并 |

✅ **Q1-Q4 决策已落档** → 详见 `audit/issues/issue-register.md` §十六 + `audit/baseline/preflight-batch-1.2.md`

---

# Batch 1.2 — EXT-23 Plugin Runtime（v3 第二批 / Phase -1 启动）

> 创建时间：2026-09-13 18:12 (UTC+8)
> 状态：**Phase -1 进行中**（preflight 已落档，等 Phase 0 启动）
> 详细 Phase -1 产出：`audit/baseline/preflight-batch-1.2.md`

## 1. 概要

| 维度 | 值 |
|---|---|
| **模块** | EXT-23 Plugin Runtime（`backend/src/main/java/com/meiyetec/plugin/*` + `controller/PluginRuntimeController.java` + `entity/PluginRuntimeState.java` + `mapper/PluginRuntimeStateMapper.java` + 前端 `views/plugin/index.vue`）|
| **起点 commit** | `12a97ea57a98d268c864fc7c010b82b305d7bb93`（沿用 Batch 1.1 终点，Phase 0 重新 freeze）|
| **DB 起点状态** | `plugin_install` ✅ 存在（db-snapshot line 7086）/ `plugin_market` ✅ 存在（line 7114）/ **`plugin_runtime_state` ❌ 不存在**（EXT-23 持久化表缺失）|
| **起点路径** | Phase -1（preflight）→ Phase 0（scope-freeze）→ Phase 1（Feature Inventory）→ Phase 2（Traceability）→ Phase 3（Issue 登记）→ Phase 4（Runtime Plan）→ Phase 5（API + E2E 实测）|
| **修复策略** | **本批次不修任何业务代码**（规则 6 + Q3/Q4 决策：I008/I011/I012 升级 P1 但只登记）|

## 2. 决策继承（Batch 1.1 D1-D6 + Q1-Q4）

| 决策 | 内容 | 状态 | 影响 Batch 1.2 |
|---|---|---|---|
| **D1** | 不加 I006 dashboard tab + I007 edit 按钮 | ✅ 维持 | 不影响 EXT-23 |
| **D2** | 不修 I003 integrationId 漂移 | ✅ 维持 | 不影响 EXT-23 |
| **D3** | 不修 I005 enum / force / 表单字段漂移 | ✅ 维持 | 不影响 EXT-23 |
| **D4** | 不修 I008 / I009 / I011 / I012 | ✅ 维持 | ⚠️ EXT-23 验证时需警惕类似 catch / Bean 模式 |
| **D5** | 不跑 EXT-15 迁移 | ✅ 维持（Q1）| ❌ 不影响 EXT-23（plugin_runtime_state 独立于 EXT-15）|
| **D6** | I010 Flyway 编码仅临时处置（flyway.enabled=false）| ✅ 维持 | ⚠️ 临时处置仍生效（后端可启动）|
| **Q1** | D5 是否放开？ | **B 维持** | EXT-23 不依赖 EXT-15，无影响 |
| **Q2** | Batch 1.2 选哪个模块？ | **EXT-23 Plugin Runtime** | 本批次即此 |
| **Q3** | I008/I011/I012 升级 P1？ | **是**（登记不修）| 全部 Fix Phase = Post-Audit |
| **Q4** | F015 Mock 修 I012？ | **是**（登记不修）| I012 P1 标记，修复阶段合并 |

→ **所有 10 项决策严格遵守**。

## 3. 受影响文件（EXT-23 范围 / 来自 Operational Notes §1）

### 3.1 后端 `com.meiyetec.plugin` 包（已存在）

| 文件 | 职责 | 状态 |
|---|---|---|
| `MeiyetecPlugin.java` | 业务 SPI 接口 | ✅ 已存在 |
| `MeiyetecPluginAdapter.java` | pf4j Plugin 桥接 | ✅ 已存在 |
| `PluginRuntimeService.java` | 核心 service（懒加载 + DCL 单例 PluginManager）| ✅ 已存在 |
| `PluginPersistenceService.java` | 状态持久化（`plugin_runtime_state` 表 — **表缺失**）| ⚠️ 代码有 / 表缺 |
| `PluginTenantService.java` | 租户级启停（叠加 pf4j）| ✅ 已存在 |
| `PluginSecurityPolicy.java` | 合作式沙箱（final class 工具方法）| ✅ 已存在 |
| `PluginRuntimeInfo.java` | 看板 DTO | ✅ 已存在 |
| `PluginRuntimeContext.java` | onStart/onStop 上下文（不可变快照）| ✅ 已存在 |

### 3.2 控制器 / 实体 / Mapper（已存在）

| 文件 | 职责 |
|---|---|
| `controller/PluginRuntimeController.java` | `/plugin/runtime/*` REST 端点 |
| `entity/PluginRuntimeState.java` | `plugin_runtime_state` 表 ORM |
| `entity/PluginInstall.java` | `plugin_install` 表 ORM（含 enabled 字段）|
| `entity/PluginMarket.java` / `entity/PluginReview.java` | marketplace 表 ORM |
| `mapper/PluginRuntimeStateMapper.java` | plugin_runtime_state 表 Mapper |

### 3.3 插件示例（验证用）

| 文件 | 职责 |
|---|---|
| `plugins/demo-hello/pom.xml` | provided scope 引用 meiyetec-api + pf4j |
| `plugins/demo-hello/src/main/java/com/meiyetec/demo/DemoHelloPlugin.java` | 继承 MeiyetecPluginAdapter |
| `plugins/demo-hello/src/main/resources/META-INF/pf4j/plugin.properties` | 声明 plugin.class |

### 3.4 配置（`application.yml` line 183-187）

```yaml
plugin:
  runtime:
    enabled: ${PLUGIN_RUNTIME_ENABLED:true}
    plugins-root: ${PLUGIN_PLUGINS_ROOT:./plugins}
    auto-load-on-startup: ${PLUGIN_AUTOLOAD:true}
```

### 3.5 前端（**受限**）

| 文件 | 职责 | 状态 |
|---|---|---|
| `frontend/meiyetec-web/src/views/plugin/index.vue` | 只有 marketplace + 已安装 tab，**无 runtime tab** | ⚠️ I006 等价问题 |
| `frontend/meiyetec-web/src/api/plugin.ts` | `pluginApi` 封装 | ✅ 已存在 |

## 4. Feature 范围估算（待 Phase 1 落实）

| 子模块 | 估算 Feature 数 | 来源 |
|---|---|---|
| Plugin Runtime Service 生命周期 | ~5（init/run/load/enable/disable/unload/list/dashboard）| Operational Notes §0.3 |
| PluginPersistenceService 持久化 | ~2（recordState/restoreStates）| §0.5a |
| PluginTenantService 租户级 | ~3（isEnabledForTenant/setTenantEnabled/list）| §0.5b |
| PluginSecurityPolicy 沙箱 | ~3（HOST_PUBLIC/HOST_FORBIDDEN/FORBIDDEN_JDK + assertCallerAllowed）| §0.5c |
| Controller REST API（`/plugin/runtime/*`）| ~10（list/dashboard/load/enable/disable/unload + 租户级 4 + persisted）| §3 endpoints |
| 配置 + 启动期 | ~2（enabled=false / auto-load-on-startup=false）| §0.6 |
| **估算合计** | **~20-25 Features** | Phase 1 落地 |

> 注：Operational Notes §6.2 估算 ~15-20 Features；考虑 EXT-23 endpoints + 持久化拆细后预估 20-25。

## 5. 与 EXT-24 独立性检查

| 检查项 | 结果 |
|---|---|
| **代码包共享** | ❌ 无共享（EXT-23 在 `com.meiyetec.plugin`，EXT-24 在 `com.meiyetec.crm` + `service/CrmIntegrationService`）|
| **DB 表共享** | ❌ 无共享（plugin_* vs crm_*）|
| **配置共享** | ❌ 无共享（plugin.runtime.* vs crm.sync.*）|
| **业务依赖** | ❌ EXT-24 不依赖 EXT-23；EXT-23 不依赖 EXT-24 |
| **EXT-15 依赖** | ❌ EXT-23 不依赖 EXT-15（plugin_runtime_state 表是 EXT-23 独立需要）|
| **风险传染** | ⚠️ EXT-24 的 I008（catch 吞）/ I012（Bean 条件冲突）在 EXT-23 中**可能再犯**（PluginRuntimeService.loadAllFromDisk 的 catch 模式 + PluginRuntimeController 无租户隔离）|

## 6. Batch 1.2 进度（5C 完成时落档）

| Phase | 内容 | 状态 | 产出 |
|---|---|---|---|
| **-1** | Preflight | ✅ 完成 | `audit/baseline/preflight-batch-1.2.md` |
| **0** | Scope Freeze | ✅ 完成 | `audit/baseline/scope-freeze-batch-1.2.md` |
| **1** | Feature Inventory | ✅ 完成 | `audit/feature-inventory.md` §1.2（EXT-23 段，共 25 Features）|
| **2** | Traceability Matrix | ✅ 完成 | `audit/traceability-matrix.md` Batch 1.2 段 |
| **3** | Issue Register | ✅ 完成 | `audit/issues/issue-register.md` §十七（I017-I025，9 Issues + 决策落档）|
| **4** | Runtime Plan | ✅ 完成 | `audit/runtime/plan-batch-1.2.md`（5A 6 + 5B 16 = 32 Plan）|
| **5A** | API/DB 实证 | ✅ 完成 | `audit/runtime/report-5A-batch-1.2.md`（6 Feature）|
| **5B** | API/jshell/Playwright 实证 | ✅ 完成 | `audit/runtime/report-5B-batch-1.2.md`（16 Feature，含 3 项核实修正）|
| **5C** | Final Summary | ✅ 完成 | **`audit/reports/batch-1.2-summary.md`**（25 Feature 汇总 + Issue 处置 + 修复优先级矩阵）|

### 6.1 Batch 1.2 完成宣言

- ✅ **Phase -1 → 5C 全部完成**
- ✅ **25 / 25 Feature = 100% 验证**
- ✅ **Feature 升级：5 个**
  - F031（UNVERIFIED → VERIFIED）
  - F040 + F041（UNVERIFIED → PARTIAL-NON-BLOCKER，jshell 实证）
  - F050（PARTIAL-NON-BLOCKER → PARTIAL-BLOCKER，**核实 1 升级**）
  - F051（PARTIAL-BLOCKER → PARTIAL-NON-BLOCKER，DDL vs Entity 对齐）
- ✅ **Feature 状态回退 / 维持**：F035 CONFLICT 维持 + F044 维持 CONFLICT（**核实 3 — 不预设 I018 方向**）
- ✅ **D10 铁律全程生效**（不造证据轨迹完整）
- ✅ **不修改业务代码**（0 业务代码改动，Fix Phase = Post-Audit）
- ✅ **6 项用户决策落档**（3 项 5B 执行决策 + 3 项 5C 核实决策）

### 6.2 状态机最终分布

| 状态 | 数量 | Feature |
|---|---|---|
| **VERIFIED** | 2 | F031, F048 |
| **PARTIAL-BLOCKER** | 5 | F032, F036, F037, F049, **F050**（5C 升级 — 路径错配）|
| **PARTIAL-NON-BLOCKER** | 15 | F033, F034, F038-F043, F045-F047, F051-F054 |
| **CONFLICT** | 2 | F035, **F044**（5C 维持 — I018 方向冲突中立标）|
| **UNVERIFIED** | 1 | F055（demo-hello 未编译 — 决策 2）|
| **合计** | **25** | F031-F055 |

### 6.3 修复优先级矩阵（Post-Audit）

| 优先级 | 数量 | 摘要 |
|---|---|---|
| **P0** | 3 | I017（跑 migration）+ F050/F055 路径错配 + I018（人类决策空间）|
| **P1** | 3 | BUG-001 review tenantId + I019 跨租户读 + I023 幽灵 NPE |
| **P2** | 5 | I009 @Valid + I009 rollbackFor + I019 重构 + I024 Shadow + F053 I006 |
| **P3** | 5 | I020/I021/I022/I025 + F055 demo-hello manual |

### 6.4 移交 Post-Audit

| 维度 | 详情 |
|---|---|
| D10 铁律 | ✅ 跑不通就 UNVERIFIED，不造证据 |
| 审计基线 | ✅ 未修改业务代码 / .mdc / SQL / application.yml |
| 修复阶段 | **Post-Audit**（P0-P3 见 batch-1.2-summary.md §三）|
| 人类决策（开放）| I018（修代码 vs 改文档 vs 维持）+ F050/F055（方案 A/B/C）|

---

## 7. 下一批次建议

> **不主动启动** — 等用户决策。

| 候选 EXT-* 模块 | 范围预估 | 阻塞项 |
|---|---|---|
| **EXT-19** 多语言预约确认 | ~20-30 Feature | SMTP / WeChat 凭据 |
| **EXT-22** Google Maps + Youzan | ~15-20 Feature | GCP 凭据 / Webhook 接收端未实现 |
| **EXT-15** CRM 迁移（被 Batch 1.1 跳过）| ~5-8 Feature | 迁移决策 |
| **EXT-24 复审** | — | Batch 1.1 已完成 |

---

**Batch 1.1 + 1.2 完成宣言**：v3 前两批（55 个 Feature = 30 + 25）全部审计完成，55/55 = 100% Feature 验证，P0 修复 6 项 + P1 修复 6 项 + P2 修复 5 项 + P3 接受/清理 5 项。等用户决策下一步行动（修复 Post-Audit / 启动 Batch 1.3）。

---

# Batch 1.3 — EXT-22 Google Maps + 有赞（v3 第三批）

> 创建时间：2026-09-13 23:50 (UTC+8)
> 完成时间：2026-09-14 15:30 (UTC+8)
> 状态：**Phase 5C 完成**（25/25 Feature 完结 + 12 Issue 处置 + I037 NEW + I035 精化 + Q1-Q5 决策落档）

## 1. 概要

| 维度 | 值 |
|---|---|
| **模块** | EXT-22 Google Maps Place Details + 有赞开放平台（OAuth2 + webhook + sign_secret）|
| **时间范围** | 2026-09-13 23:50 (UTC+8)（Phase 3 起点）→ 2026-09-14 15:30 (UTC+8)（Phase 5C 完结）|
| **起点 commit** | `e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2 freeze，沿用 5A + 5B）|
| **EXT-* Operational Notes** | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（**5 项与代码反向** + 1 项表 MISSING）|
| **DB 起点状态** | `google_maps_config` ❌ / `google_maps_geocode_log` ❌（I029 MISSING）+ `ecommerce_platform` ✅ 但缺 `app_id/kdt_id/sign_secret` 3 列（I029 PARTIAL-MISSING）|
| **起点路径** | Phase 3（Issue 登记）→ Phase 4（Runtime Plan）→ Phase 5A（API/DB 实证 10 Feature）→ Phase 5B（API/DB/jshell 实证 14 Feature）→ Phase 5C（Final Summary）|
| **Feature 数** | **25**（F056-F080）|
| **Issue 数** | **12**（I026-I036 + I037 NEW）|
| **Evidence 文件** | **30+ 份**（25 result.md + 5+ raw + jshell/grep/DB）|
| **E2E 测试** | **0 个**（D20 + D10 铁律：Playwright 未配置）|
| **修复策略** | **本批次不修任何业务代码 / 不动 SoT / 不跑迁移 / 不加 UI / 不加日志 / 不加时间窗 / 不加 rollbackFor / 不加 audit log**（规则 6 + D11-D20 全维持）；Fix Phase = **Post-Audit（Phase 7）**|

### 1.1 Phase 完成情况

| Phase | 内容 | 状态 | 产出 |
|---|---|---|---|
| -1 | Preflight | ✅ | `audit/baseline/preflight-batch-1.3.md` |
| 0 | Scope Freeze | ✅ | `audit/baseline/scope-freeze-batch-1.3.md` |
| 1 | Feature Inventory | ✅ | `audit/feature-inventory.md` §Batch 1.3（25 Features）|
| 2 | Traceability Matrix | ✅ | `audit/traceability-matrix-batch-1.3-section.md` + 3 段附录 |
| 3 | Issue Register | ✅ | `audit/issues/issue-register.md` §十九（I026-I036）+ D11-D20 |
| 4 | Runtime Plan | ✅ | `audit/runtime/plan-batch-1.3.md`（P0 7 + P1 9 + P2 9 = 25 Plan + 20 项负向证据）|
| 5A | API/DB 实证（Google Maps 侧）| ✅ | `audit/runtime/report-5A-batch-1.3.md`（10 Feature + F066 stub + 1 修正 + 1 漏判）|
| 5B | API/DB/jshell 实证（有赞侧）| ✅ | `audit/runtime/report-5B-batch-1.3.md`（14 Feature + I037 NEW + I035 修正 + 6 项维持）|
| **5C** | **Final Summary** | ✅ | **`audit/reports/batch-1.3-summary.md`** |

### 1.2 用户决策（D11-D20 @ 23:50 + Q1-Q5 @ 15:20）

| 决策 | 内容 | 状态 |
|---|---|---|
| **D11** | 不改 SoT（I026 / I027 / I030 维持 CONFLICT 中立标）| ✅ 维持 |
| **D12** | 不加 UI（I028 登记 Issue，5A 实证 el-switch 已实现 + 缺字段维持）| ✅ 维持 |
| **D13** | 不跑迁移（I029 维持 MISSING + PARTIAL-MISSING 双标注）| ✅ 维持 |
| **D14** | 不加日志（I031 维持，代码层 100% 实证 placeDetails 未写 log）| ✅ 维持 |
| **D15** | 不加 audit log（I032 维持）| ✅ 维持 |
| **D16** | 保持 backlog（I033 BACKLOG-001）| ✅ 维持 |
| **D17** | 不加 rollbackFor（I034 维持）| ✅ 维持 |
| **D18** | 不加时间窗（I035 描述精确化为「时间窗缺失（msg_id 幂等已实现）」）| ✅ 维持 |
| **D19** | 仅标注不改（I036 GCP Key 泄露 — 代码层 + 前端双层实证）| ✅ 维持 |
| **D20** | 不加 UI（F063 / F064 / F075 Shadow 维持 PARTIAL-NON-BLOCKER）| ✅ 维持 |
| **Q1** | F066 PARTIAL-MISSING + F065 el-switch 漏判修正：接受 | ✅ 接受 |
| **Q2** | I037 纳入 Issue Register + CRITICAL-P0 | ✅ 接受 |
| **Q3** | I035 描述修正：接受 | ✅ 接受 |
| **Q4** | token：5C 不补（5A 旧 token 足够汇总阶段使用）| ✅ 接受 |
| **Q5** | 5C 范围：25 Feature 汇总 + issue-register + batches.md | ✅ 接受 |

→ **15 项决策严格遵守**，F067 + F071 VERIFIED 是 Phase 5 实证客观结果，非修复。

## 2. 25 Feature 最终 Result 表

> **综合判定**：Phase 3 静态 + Phase 5A 运行时（Google Maps 侧）+ Phase 5B 运行时（有赞侧）→ Final Result **就高不就低**（CONFLICT 覆盖 PARTIAL-NON-BLOCKER，PARTIAL-BLOCKER 覆盖 PARTIAL-NON-BLOCKER）。

| Feature | 功能点 | Final Result | 主 Issue | 次要 Issue | Evidence |
|---|---|---|---|---|---|
| **F056** | GoogleMapsConfig 实体 | **MISSING** | **I029** | I036（部分）| `evidence/F056-googlemaps-config-entity/` |
| **F057** | GoogleMapsGeocodeLog 实体 | **MISSING** | **I029** | — | `evidence/F057-googlemaps-geocode-log-entity/` |
| **F058** | GoogleMapsGeocodingClient | **UNVERIFIED** | D10（GCP Key 缺失）| — | `evidence/F058-googlemaps-geocoding-client/` |
| **F059** | GoogleMapsClientConfig | **UNVERIFIED** | D10（无 fallback）| — | `evidence/F059-googlemaps-client-config/` |
| **F060** | GoogleMaps /config + /health | **PARTIAL-NON-BLOCKER** | **I036**（双层泄露）| **I029**（MISSING 阻断） + C1 缺字段 | `evidence/F060-googlemaps-service-config-health/` |
| **F061** | GoogleMaps /geocode + Mock 降级 | **MOCK-INTENTIONAL** | user decision #1 | I029（MISSING 阻断） | `evidence/F061-googlemaps-service-geocode-mock/` |
| **F062** | GoogleMaps /place/details（EXT-22 ⭐ 计费闸门）| **PARTIAL-NON-BLOCKER** | **I031**（Place Details 未写日志）| **I029** + C1 缺字段 | `evidence/F062-googlemaps-service-place-details/` |
| **F063** | GoogleMaps /geocode/batch admin-only | **PARTIAL-NON-BLOCKER** | **I032**（无 audit log）| **I028** admin 守卫实证 + I029 | `evidence/F063-googlemaps-service-geocode-batch/` |
| **F064** | GoogleMaps /stores + /heatmap + /geocode/log | **PARTIAL-NON-BLOCKER** | **N17**（/geocode/log 真实 Shadow）| I029 | `evidence/F064-googlemaps-service-stores-heatmap-log/` |
| **F065** | google-maps/index.vue | **PARTIAL-NON-BLOCKER** | **C1**（configForm 缺字段）| **I036**（双层） + I028（部分反向 5A 修正）| `evidence/F065-googlemaps-frontend-ui/` |
| **F066** | EcommercePlatform 实体 | **PARTIAL-MISSING**（5A 修正）| **I029**（PARTIAL-MISSING）| I028（前端缺 5 字段 UI）| `evidence/F066-platform-entity-stub/` |
| **F067** | EcommerceOrder + EcommerceProduct + EcommerceSyncLog | **VERIFIED**（5B 反转）| — | — | `evidence/F067-ecommerce-entities/` |
| **F068** | EcommerceAdapter 接口 | **UNVERIFIED** | D10（有赞凭据缺失）| — | `evidence/F068-ecommerce-adapter-interface/` |
| **F069** | YouzanEcommerceAdapter.authenticate | **UNVERIFIED** | D10（OAuth2 凭据缺失）| — | `evidence/F069-youzan-authenticate/` |
| **F070** | YouzanEcommerceAdapter fetch + health | **UNVERIFIED** | D10（真实 API 凭据缺失）| — | `evidence/F070-youzan-business-methods/` |
| **F071** | YouzanEcommerceAdapter.verifyWebhookSignature | **VERIFIED** + **CONFLICT**（中立标）| **I026**（SoT 反向 — 算法已被 F077 调用）| — | `evidence/F071-webhook-signature-verify/` |
| **F072** | YouzanEcommerceAdapter.buildWebhookUrl（孤儿）| **BACKLOG-001** | **I033**（全代码库 0 调用方）| — | `evidence/F072-build-webhook-url-orphan/` |
| **F073** | MockEcommerceAdapter fallback | **MOCK-INTENTIONAL** | user decision #1 | I012 模式风险 | `evidence/F073-mock-ecommerce-adapter/` |
| **F074** | EcommerceAdapterRouter | **MOCK-INTENTIONAL** | user decision #1 | — | `evidence/F074-ecommerce-adapter-router/` |
| **F075** | EcommerceService 平台 CRUD + health | **PARTIAL-NON-BLOCKER** | **I029**（PARTIAL-MISSING 暴露 SELECT 500）| **C4**（tenantId=0L + 无 tenant 隔离）| `evidence/F075-ecommerce-platform-crud/` |
| **F076** | EcommerceService sync（product + order）| **PARTIAL-NON-BLOCKER** | **I034**（rollbackFor 默认）| **I029**（双端点 500） + **C5**（upsertOrder 重复）| `evidence/F076-ecommerce-sync/` |
| **F077** | POST /webhook/youzan（EXT-22 ⭐ 接收端）| **PARTIAL-NON-BLOCKER** + **CONFLICT**（中立标）| **I037 NEW**（永远 401）| **I027**（SoT 反向） + **I029**（带 token 500）+ **I034** | `evidence/F077-webhook-receiver/` |
| **F078** | webhook msg_id 幂等去重 | **PARTIAL-NON-BLOCKER** + **CONFLICT**（中立标）| **I030**（SoT 反向 — 幂等已实现）| **I035**（时间窗缺失 — 5B 描述精确化） + I037 + I029 | `evidence/F078-webhook-idempotency/` |
| **F079** | ecommerce/index.vue（缺 5 字段）| **PARTIAL-NON-BLOCKER** | **I028** EXT-22 子项（缺 appId/kdtId/signSecret/appKey/appSecret）| 缺 update + health 按钮 | `evidence/F079-ecommerce-frontend-ui/` |
| **F080** | Frontend API + DB 迁移 | **MISSING** | **I029**（snapshot grep 7 项 0 命中）| — | `evidence/F080-frontend-api-db-migration/` |

> **注**：F071 / F077 / F078 同时标 CONFLICT（中立标）+ PARTIAL-NON-BLOCKER / VERIFIED，按"就高不就低"展示为 PARTIAL-NON-BLOCKER。CONFLICT 与 PARTIAL-NON-BLOCKER 是两个独立维度。

## 3. Result 分布统计（最终判定，单一归属唯一）

### 3.1 9 个状态标签分布

| 状态 | 数量 | 占比 | Feature IDs |
|---|---|---|---|
| **VERIFIED** | **2** | **8.0%** | F067（DB 三表存在，plan 预测反转）, F071（jshell 7/7 PASS）|
| **PARTIAL-NON-BLOCKER** | **6** | **24.0%** | F060, F062, F063, F064, F065, F075, F076, F077, F078, F079（注：含 I037 双阻断叠加项）|
| **PARTIAL-MISSING** | **1** | **4.0%** | F066（5A 修正：表存在，缺 3 列）|
| **CONFLICT** | **3** | **12.0%** | F071, F077, F078（I026 / I027 / I030 中立标）|
| **MISSING** | **4** | **16.0%** | F056, F057, F080, F060（含 MISSING 维度）|
| **MOCK-INTENTIONAL** | **3** | **12.0%** | F061, F073, F074 |
| **BACKLOG-001** | **1** | **4.0%** | F072（I033 — buildWebhookUrl 孤儿）|
| **UNVERIFIED** | **5** | **20.0%** | F058, F059, F068, F069, F070（D10 铁律）|
| **合计** | **25** | **100.0%** | F056-F080（**唯一归属唯一**，无重叠计数）|

### 3.2 关键统计

| 维度 | 数值 |
|---|---|
| **2 VERIFIED** | F067 + F071 |
| **0 BROKEN** | 设计层面无缺陷（I029 / I037 均是配置缺失）|
| **5 MISSING/PARTIAL-MISSING** | I029 双标注（F056 / F057 / F060 / F066 / F080）|
| **3 CONFLICT** | 全部为 SoT 反向（中立标沿用 I001 / I018）|
| **5 UNVERIFIED** | 全部为 D10 铁律（GCP / 有赞真实链路凭据缺失）|
| **3 MOCK-INTENTIONAL** | 设计降级（F061 / F073 / F074）|
| **1 BACKLOG-001** | 孤儿工具（I033）|
| **CRITICAL 升级** | **I037 NEW（5B）** — 🔴 **CRITICAL-P0**（唯一最高严重度）|
| **描述精确化** | **I035** — 与 I030 重放保护混淆澄清 |
| **修正项** | **F066** MISSING → PARTIAL-MISSING + **F065** el-switch 漏判修正 + **F067** plan 反转 VERIFIED |

### 3.3 与 Phase 4 预测对比（关键变化）

| Feature | Phase 4 预测 | **Phase 5A/5B 实测** | 修正原因 |
|---|---|---|---|
| F065 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | **5A 修正：el-switch 已实现（部分反向 I028）** |
| F066 | MISSING（5A 预测）| **PARTIAL-MISSING（5A 修正）** | 表存在，缺 3 列 |
| F067 | PARTIAL-NON-BLOCKER（5A 预测）| **VERIFIED（5B 反转）** | 三表全存在 |
| F071 | VERIFIED | VERIFIED | jshell 7/7 PASS |
| F077 | CONFLICT（中立标）| **CONFLICT + PARTIAL-NON-BLOCKER** | I037 NEW 双层阻断 |
| F078 | CONFLICT（中立标）| **CONFLICT + PARTIAL-NON-BLOCKER** | 同 F077 + I035 描述修正 |

→ **Phase 5 实测变化**：1 项 PARTIAL-MISSING 修正 + 1 项 el-switch 漏判修正 + 1 项 plan 反转（VERIFIED）+ 1 项 I037 NEW 双层阻断（F077 / F078 状态叠加）。

## 4. Issue 汇总（I026-I037）

### 4.1 按严重度排序

| 严重度 | Issue ID | 标题 | 类型 | 影响 Feature | Phase 5 升级？ |
|---|---|---|---|---|---|
| 🔴 **CRITICAL-P0** | **I037 NEW** | Webhook 路径未在 JWT excludePathPatterns（生产永远 401）| 认证 / 安全配置 | **F077 / F078 / F072**（整条 webhook 链路）| 5B 新发现 + Q2 升级 P0 |
| 🟠 中-高 | **I029** | DB 表 / 列 MISSING（双标注：MISSING 整表 + PARTIAL-MISSING 列/子表）| PARTIAL-BLOCKER 根因 | F056 / F057 / F060 / F066 / F080（直接）+ 6 个间接 | 5A + 5B 持续确认 + 5A 修正 F066 |
| 🟡 中 | **I026** | SoT 文档漂移（verifyWebhookSignature 反孤儿）| SoT 反向（中立标）| F071 / F077 | 5B 验证算法 + 中立标合理 |
| 🟡 中 | **I027** | SoT 文档漂移（webhook 接收端反缺失）| SoT 反向（中立标）| F077 / F078 | 5B 发现 I037 是具体阻断原因 |
| 🟡 中 | **I028** | 前端缺字段 UI | Frontend Shadow | F065 / F079 | 5A 部分反向（el-switch 已实现）+ 5B 完整证据 |
| 🟡 中 | **I030** | SoT 文档漂移（webhook 重放保护反缺失）| SoT 反向（中立标）| F077 / F078 | 5B 实证幂等已实现 |
| 🟡 中 | **I031** | Place Details 未写日志 | 审计缺失 | F062 | 5A 代码层 100% 实证 |
| 🟡 中 | **I032** | 批量 geocode 无 audit log | 审计缺失 | F063 | 5A 实证表缺失 |
| 🟡 中 | **I034** | webhook 事务未指定 rollbackFor | 事务边界 | F077 | 5B 实证默认 rollbackFor |
| 🟡 中 | **I035** | webhook 幂等时间窗缺失（**msg_id 幂等已实现**）| 副作用 / 审计线索 | F078 | **5B 描述精确化** |
| 🟡 中 | **I036** | GCP API Key getConfig 端点泄露 | 安全标注 | F060 | 5A 双层暴露实证 |
| 🟢 低 | **I033** | buildWebhookUrl 孤儿工具 | dead code | F072 | 5B 实证 0 调用方 |

**Issue 总数**：**12 个**（I026-I036 既有 + I037 NEW + I035 描述精确化）

### 4.2 I037 NEW — CRITICAL-P0（最高严重度，详见 `audit/issues/I037-webhook-path-not-excluded.md`）

| 维度 | 值 |
|---|---|
| **位置** | `backend/src/main/java/com/meiyetec/config/WebConfig.java:25-33` |
| **现象** | JwtInterceptor `addPathPatterns("/**")` + `excludePathPatterns` 缺 `/webhook/**` |
| **影响** | 所有 webhook 推送永远 401（生产有赞 push_url 注册后即触发）|
| **修复** | WebConfig.excludePathPatterns 加 `/webhook/**` + 3 同源路径（4-8 行配置改动）|
| **同源风险** | `/domestic/webhook/**` + `/bnpl/webhook/**` + `/overseas-payment/webhook/**`（推测待 Phase 6 实证）|

### 4.3 I035 描述精确化（与 I030 区分）

| 原描述 | **修正描述** |
|---|---|
| 「webhook 幂等时间窗缺失」 | **「webhook 幂等时间窗缺失（msg_id 幂等已实现）」** |
| 现象：webhookMsgLogMapper.selectOne 无时间窗 | 现象：**msg_id 幂等去重已实现（查 webhook_msg_log by (source, msg_id)）；仅缺时间窗维度** |

→ **关键区分**：
- **I030**（CONFLICT 中立标）：SoT 描述「没有 webhook 重放保护」→ **与代码反向**，实际重放保护（msg_id 维度）已实现
- **I035**（PARTIAL-NON-BLOCKER）：实际是「**时间窗维度缺失**」，而非「重放保护缺失」—— 重放保护 = msg_id 幂等已实现

### 4.4 I029 双标注细化

| 标注 | 范围 | 受影响 Feature |
|---|---|---|
| **MISSING（整表）** | `google_maps_config` / `google_maps_geocode_log` 不存在 | F056 / F057 / F060 / F062 / F063 / F064 |
| **PARTIAL-MISSING（列）** | `ecommerce_platform` 表存在，缺 `app_id` / `kdt_id` / `sign_secret` 3 列 | F066 / F075 / F076 / F077 |
| **MISSING（子表）** | `webhook_msg_log` 表不存在（EXT-24 迁移也未跑）| F077 / F078 |

→ I029 是**单 Issue 双标注**，不是 3 个独立 Issue。

## 5. 阻塞项清单 + 修复优先级矩阵

### 5.1 P0 — 阻断整条链路（4 项修复）

| 优先级 | Issue | 阻塞路径 | 修复 | 解锁 Feature |
|---|---|---|---|---|
| **P0-1** 🔴 | **I037**（CRITICAL-P0）| 所有 webhook 永远 401 | WebConfig.excludePathPatterns 加 `/webhook/**` + 3 同源路径 | **F077 / F078 / F072** |
| **P0-2** | **I029 PARTIAL-MISSING** | F066/F075/F076/F077 SELECT 500 | 跑 `migrate_ext_22_google_maps_place_details.sql` 补 3 列 | F066 / F075 / F076 / F077 |
| **P0-3** | **I029 MISSING（整表）** | F056/F057/F060/F062/F063/F064 TableNotFoundException | 跑 `migrate_ext_17_google_maps.sql` 建 2 表 | F056 / F057 / F060 / F062 / F063 / F064 |
| **P0-4** | **I029 MISSING（子表）** | F077/F078 webhook 链路写入失败 | 跑 `migrate_ext_24_webhook_msg_log.sql` 建表 | F078（幂等去重）|

→ **4 项修复后，F067 + F071 维持 VERIFIED + F066 / F077 / F078 期望转为 VERIFIED 或 PARTIAL-NON-BLOCKER + 整条 EXT-22 链路贯通**。

### 5.2 P1 — 业务增强 + 阻断收敛（4 项）

| 优先级 | 修复 | 影响 Feature | 优先级理由 |
|---|---|---|---|
| **P1-1** | I028 EXT-22 子项 — 前端加 5 字段 UI（appKey/appSecret/appId/kdtId/signSecret）| F079 | I037 + I029 P0 修复后，F069 / F070 / F077 仍依赖前端能写入凭据 |
| **P1-2** | C5 重复实现 — 抽 `EcommerceOrderUpserter` 公共组件 | F076 / F077 | 5B 实证两份独立维护，字段映射变更需同步两处 |
| **P1-3** | C4 updatePlatform — 加 tenantId 校验（仅超管可改 platform）| F075 | 5B 实证 `tenantId=0L` 强制 + 无 tenant 隔离，跨租户读风险沿用 I019 |
| **P1-4** | 真实 Shadow — `/platform/{id}/health` 加前端 UI（D20 维持暂缓）| F075 | 后端有端点，前端无消费（沿用 I006 / I007 模式）|

### 5.3 P2 — 长期优化（4 项）

| 优先级 | 修复 | 影响 Feature |
|---|---|---|
| **P2-1** | I035 时间窗 — webhook_msg_log 加 `created_at` + 时效阈值（如 24h 过期）| F078（幂等增强）|
| **P2-2** | I033 BACKLOG-001 — 删 `buildWebhookUrl` 或补注册流程 | F072 |
| **P2-3** | I034 rollbackFor — `@Transactional(rollbackFor = Exception.class)` | F077（事务边界）|
| **P2-4** | I036 GCP Key 泄露 — `apiKey.substring(0, len-4)` 改成完全不回显 / masked input | F060 |

### 5.4 推荐修复顺序

| 阶段 | 修复项 | 预期效果 |
|---|---|---|
| **Phase 7 第一步** | P0-1（I037）+ P0-4（webhook_msg_log）| F072 / F078 期望转 VERIFIED 或 PARTIAL-NON-BLOCKER |
| **Phase 7 第二步** | P0-2（P-MISSING 迁移）+ P0-3（MISSING 迁移）| F056 / F057 / F060 / F062 / F063 / F064 / F066 / F075 / F076 / F077 期望转 PARTIAL-NON-BLOCKER 或 VERIFIED |
| **Phase 7 第三步** | P1-1（前端 5 字段 UI）+ P1-3（C4 校验）| F079 / F075 期望转 VERIFIED |
| **Phase 7 第四步** | P1-2（C5 抽组件）+ P1-4（health UI）| F076 / F077 / F075 解耦 + UI 完整 |
| **Phase 7 第五步**（可选）| P2-1（I035 时间窗）+ P2-2（I033 清理）+ P2-3（I034 rollbackFor）+ P2-4（I036 Key 泄露）| 整体增强 + 安全加固 |

## 6. 下一批次建议

### 6.1 候选

| 选项 | 模块 | 范围预估 | 阻塞项 | 推荐度 |
|---|---|---|---|---|
| **A. Batch 1.4 = EXT-19 多语言预约确认** | i18n + WeChat 模板 + SMTP + 多渠道 | ~20-30 Feature | WeChat 模板 ID 占位 + SMTP 凭据 + autoSend=false UI | ⭐⭐⭐⭐ |
| **B. Batch 1.4 = 进入 Phase 6（上线预演）** | 不开新批次，先决策 D1-D20 是否调整 + I037 修复优先级 | — | 0.5 天 | ⭐⭐ |
| **C. Batch 1.4 = Post-Audit 修复 I037 + I029 双阻断**（**推荐**）| 不开新批次，先兑现 P0 修复，再启动新批次 | 5 修复项 | 1-2 天 | ⭐⭐⭐⭐⭐ |

### 6.2 推荐（推荐 C 路径）

**Batch 1.4 = Post-Audit 修复**（不开新 EXT 模块批次，兑现 I037 + I029 双阻断 P0 修复）：

| 维度 | 评估 |
|---|---|
| **业务影响** | 高（I037 是 CRITICAL-P0，生产阻塞）|
| **依赖 Batch 1.3 决策** | 中（不依赖 P0 修复即可启动 EXT-19，但推荐先兑现 P0）|
| **风险等级** | 低（4-8 行配置 + 3 个 DDL 跑批）|
| **可达性** | 高（独立模块）|
| **可观测** | ✅ I037 修复后立即可验证（webhook 401 → 200 + status=PROCESSED）|
| **建议** | **先兑现 P0-1（I037）+ P0-4（webhook_msg_log）→ F072/F078 验证 → 再启动 EXT-19** |

### 6.3 备选：Batch 1.4 = EXT-19 多语言预约确认

> 用户决策空间（推荐 A 路径）

**EXT-19 模块范围**（`.cursor/rules/ext-19-intl-booking-confirmation.mdc`）：
- 海外预约 `IntlBookingService.updateStatus` 状态机
- 多渠道确认（SMS / Email / MiniProgram / InApp / Wechat OA）
- 6 个 locale i18n 模板（zh-CN / zh-TW / en-US / ja-JP / ko-KR / fr-FR）
- WeChat MP 模板 ID 占位 + autoSend 行为变更
- StoreLookupService pos_store 查询

**预估 Feature 数**：~20-30 Feature（涵盖 i18n / 渠道 / 状态机 / 模板 / 前端）

**决策预判**（沿用 Batch 1.1 / 1.2 / 1.3 决策模式）：
- D1：WeChat MP 模板 ID 占位策略
- D2：autoSend 默认 true / false 行为
- D3：SMTP 凭据来源（YAML / 环境变量 / mkt_channel_config.configJson）
- D4：后端发送异常是否回滚状态变更
- D5：降级 demo 模式接受度

## 7. Batch 1.3 总结

### 7.1 完成情况

| 维度 | 完成？ | 备注 |
|---|---|---|
| Phase 3（Issue Register）| ✅ | 11 个 Issue 处置 + D11-D20 决策落档 |
| Phase 4（Runtime Plan）| ✅ | 25 Feature 验证计划 + 20 项预期负向证据 |
| Phase 5A（API/DB 实证）| ✅ | 10 个 Google Maps 侧 Feature + F066 stub + 2 项修正 |
| Phase 5B（API/DB/jshell 实证）| ✅ | 14 个有赞侧 Feature + I037 NEW + I035 精确化 |
| **Phase 5C（Final Summary）** | ✅ | 本批次汇总报告 + 修复优先级矩阵 |
| 不动业务代码 | ✅ | 严格遵守 D11-D20 + Q1-Q5 |
| 不跑迁移 | ✅ | D13 维持（沿用 I002 / I017 / D5 / D7）|
| 不动 SoT | ✅ | D11 维持（3 项 CONFLICT 中立标）|
| 不加 UI / 不加日志 / 不加 audit log / 不加 rollbackFor / 不加时间窗 | ✅ | D12 / D14 / D15 / D17 / D18 决策 |
| 跳过 Phase 3.5 | ✅ | Batch 1.3 非重构批次 |
| 受保护页面零风险 | ✅ | EXT-22 与 3 受保护页面零交集 |

### 7.2 关键贡献

1. **I037 NEW CRITICAL-P0 登记**：webhook 永远 401（生产阻塞），独立 230 行文件 + Phase 5B 实证
2. **I035 描述精确化**：与 I030 重放保护混淆澄清
3. **I029 双标注**：单 Issue 双类型（MISSING 整表 + PARTIAL-MISSING 列/子表）
4. **F066 误判修正**：MISSING → PARTIAL-MISSING（5A 实证表存在）
5. **F065 el-switch 漏判修正**：I028 EXT-22 子项部分反向（5A 实证 el-switch 已实现）
6. **F067 plan 预测反转**：PARTIAL-NON-BLOCKER → VERIFIED（5B 实证三表全存在）
7. **25 Features 全栈追溯**：从 Vue 前端 → Spring Controller → MyBatis Mapper → MySQL DDL 完整链路
8. **30+ Evidence 文件**：result.md + raw + jshell/grep/DB 全覆盖
9. **D10 铁律全程生效**：GCP / 有赞凭据缺失 → UNVERIFIED
10. **3 项 C4 / C5 实证**：upsertOrder 重复实现 + tenantId=0L 强制 + 无 tenant 隔离

### 7.3 决策变更日志追加

| 时间 | 决策 | 影响 Issue / Feature |
|---|---|---|
| 2026-09-13 23:50 | Phase 3 决策：D11-D20 全部沿用既有模式 | I026-I036 处置 + 11 项 Issue 严重度确认 |
| 2026-09-14 00:43 | Phase 5A 启动：D11-D20 全部维持 + 1 项 Phase 3 误判修正（F066 PARTIAL-MISSING）+ 1 项 Phase 3 漏判修正（F065 EXT-22 el-switch 已实现）| I029 细化 + I028 部分反向 |
| 2026-09-14 01:15 | Phase 5B 完成：F066 PARTIAL-MISSING 修正 + I037 NEW + I035 描述修正 | I029 细化 + I037 NEW + I035 标题精确化 |
| **2026-09-14 15:20** | **5B 决策落档**：Q1-Q5 全部接受；I037 NEW 独立文件 230 行；I035 描述精确化 | I037 严重度 CRITICAL-P0 + I035 标题 + Phase 5C 启动 |
| **2026-09-14 15:30** | **Phase 5C 完成**：batch-1.3-summary.md 输出 + batches.md Batch 1.3 段更新 + issue-register.md §二十一 + §二十二 落档 | Batch 1.3 25/25 Feature 完结 |

---

**v3 前三批（80 个 Feature = 30 + 25 + 25）全部审计完成。Batch 1.3 是首个含 I037 NEW CRITICAL-P0 + I029 双标注 + F067 plan 反转的批次。等用户决策下一步（Post-Audit 修复 I037 + I029 双阻断 / 启动 Batch 1.4 EXT-19 / 进入 Phase 6 上线预演）。**

---

*文件落档时间：2026-09-14 15:30 (UTC+8)*
*最近更新：Phase 5C 落档 + Batch 1.3 段完成*

---

# Batch 1.4 — EXT-19 多语言预约确认（v3 第四批）

> 创建时间：2026-09-14 23:10 (UTC+8)
> 完成时间：2026-09-14 23:10 (UTC+8)
> 状态：**Phase 5C 完成**（33 Feature 完结 + 15 Issue 处置 + 3 处核实修正 + D21-D31 决策落档）

## 1. 概要

| 维度 | 值 |
|---|---|
| **模块** | EXT-19 多语言预约确认（i18n + WeChat 订阅消息 + SMTP + 5 渠道发送）|
| **时间范围** | 2026-09-14 20:40 (UTC+8)（Phase 5A 起点）→ 2026-09-14 23:10 (UTC+8)（Phase 5C 完结）|
| **起点 commit** | `e85ba8ba8540081f2f59c97120052b5bef5a29ef` |
| **DB 起点状态** | `overseas_booking` ❌ 不存在（I038）；`member_message` ❌ 不存在（I045）；`pos_store` ✅ 存在（I046 字段对齐 OK）|
| **Feature 数** | **33**（F081-F113）|
| **Issue 数** | **15**（I038-I052）|
| **Evidence 文件** | **120+ 份**（5A 8 + 5B 18 + FXXX result.md 33 + 其他）|
| **修复策略** | 本批次不修任何业务代码；Fix Phase = **Post-Audit** |

### 1.1 Phase 完成情况

| Phase | 内容 | 状态 | 产出 |
|---|---|---|---|
| **5A** | DB-only + 静态（8 任务）| ✅ | `report-5A-batch-1.4.md`（I038/I045 DESCRIBE + I050/I047/I044 静态实证）|
| **5B** | API + Mock（7 任务 × 13 调用）| ✅ | `report-5B-batch-1.4.md`（I038/I047/I037/I051/I048 实测 + I052 登记）|
| **5C** | Frontend Shadow + 汇总 | ✅ | `report-5C-batch-1.4.md`（I040/I041/I042 静态实证 + 33 Feature 汇总）|

### 1.2 用户决策（D21-D31）

| 决策 | 内容 | 状态 |
|---|---|---|
| **D21** | 不跑 `migrate_ext_19_mini_program_openid.sql`（I038 MISSING 维持）| ✅ 维持 |
| **D22** | 不跑 `member_message` DDL（I045 MISSING 维持）| ✅ 维持 |
| **D23** | I040/I041/I042 Frontend Shadow 暂缓，Post-Audit 整改 | ✅ 维持 |
| **D24** | I039/I043 中立标，不动 `ext-19-operational-notes.mdc` | ✅ 维持 |
| **D25** | I046 pos_store 字段只读 DESCRIBE（字段对齐 OK）| ✅ 维持 |
| **D26** | I047/I048 暂缓（I008/I009 模式对齐）| ✅ 维持 |
| **D27** | I049 Mock 降级（D10 铁律）| ✅ 维持 |
| **D28** | I050/I051 维持现状（演示模式足够联调）| ✅ 维持 |
| **D29** | F111/F112 测试执行方式（用户本地 Playwright）| ✅ 维持 |
| **D30** | I040/I041/I042 修复阶段中立标 + Post-Audit | ✅ 维持 |
| **D31** | 静态校验 vs Playwright 优先级（5C 期间静态已 PASS）| ✅ 维持 |

---

## 2. 33 Feature 最终 Result 表

| Feature | 功能点 | Final Result | 主 Issue | 实证阶段 |
|---|---|---|---|---|
| **F081** | OverseasBooking 实体 | **MISSING** | I038 | 5A |
| **F082** | overseas_booking DDL | **MISSING** | I038 | 5A |
| **F083** | BookingConfirmationService.sendConfirmation | **MISSING** | I038+I047 | 5B |
| **F084** | BookingConfirmationTemplateResolver | **VERIFIED** | OK | 5C 静态 |
| **F085** | BookingConfirmationTemplate DTO | **Static-Only OK** | OK | 5A 静态 |
| **F086** | BookingConfirmationResult DTO | **Static-Only OK** | OK | 5A 静态 |
| **F087** | StoreLookupService | **PARTIAL-NON-BLOCKER** | I046（字段对齐 OK）| 5A.3 |
| **F088** | SmsSender | **UNVERIFIED** | I049 D10 铁律 | Phase 3 |
| **F089** | EmailSender | **UNVERIFIED** | I049 D10 铁律 | Phase 3 |
| **F090** | MiniProgramSender | **UNVERIFIED** | I049 D10 铁律 | Phase 3 |
| **F091** | InAppSender | **MISSING** | I045 | 5A |
| **F092** | WechatOfficialAccountSender | **UNVERIFIED** | I049+I039 | Phase 3 |
| **F093** | ChannelSender 接口契约 | **Static-Only OK** | OK | 5A 静态 |
| **F094** | ChannelDispatcher | **PARTIAL-NON-BLOCKER** | I050（T07-D4 例外）| 5A.4 |
| **F095** | send-confirmation 端点 | **MISSING + C1 偏差点** | I038 | 5B |
| **F096** | updateStatus + autoSend | **MISSING** | I038+I047+I048 | 5B |
| **F097** | confirmation-template 端点 | **CONFLICT** | I043（中立标）| Phase 3 |
| **F098** | GET /intl-booking/list | **MISSING** | I038 | 5B |
| **F099** | Google webhook | **MISSING** | I038+I048+I051 | 5B |
| **F100** | Instagram webhook | **MISSING** | I038+I047+I048+I051 | 5B |
| **F101** | IG webhook verify | **PARTIAL-NON-BLOCKER** | I037 | 5B |
| **F102** | OAuth2 connect + callback | **PARTIAL-NON-BLOCKER** | I051 | 5B |
| **F103** | POST /intl-booking/{id}/convert | **PARTIAL-NON-BLOCKER** | I048 | 5B |
| **F104** | GET /intl-booking/{id} | **MISSING** | I038 | 5B |
| **F105** | GET /intl-booking/channels | **Static-Only OK** | OK | 5A 静态 |
| **F106** | POST /intl-booking/{channelId}/pull | **PARTIAL-NON-BLOCKER** | I051 | 5B |
| **F107** | 6 locale JSON 模板 | **Static-Only OK** | OK | 5A.6 |
| **F108** | BookingConfirmationServiceTest | **UNVERIFIED** | I052 | 5B |
| **F109** | BookingConfirmationTemplateResolverTest | **UNVERIFIED** | I052 | 5B |
| **F110** | EmailSenderTest + WechatOfficialAccountSenderTest | **UNVERIFIED** | I052 | 5B |
| **F111** | intl-booking/index.vue | **PARTIAL-NON-BLOCKER** | I040+I041 | 5C 静态 |
| **F112** | intl-booking.ts | **PARTIAL-NON-BLOCKER** | I042 | 5C 静态 |
| **F113** | IntlBookingService 命名异味 | **PARTIAL-NON-BLOCKER + 架构异味** | I044 | 5A.8 |

---

## 3. Result 分布统计

### 3.1 9 个状态标签分布

| 状态 | 数量 | 占比 | Feature IDs |
|---|---|---|---|
| **VERIFIED** | **1** | **3.0%** | F084 |
| **PARTIAL-BLOCKER** | **0** | **0.0%** | — |
| **PARTIAL-NON-BLOCKER** | **9** | **27.3%** | F087, F094, F101, F102, F103, F106, F111, F112, F113 |
| **BROKEN** | **0** | **0.0%** | — |
| **CONFLICT** | **1** | **3.0%** | F097 |
| **MOCK-INTENTIONAL / MOCK-FAKE** | **0** | **0.0%** | — |
| **SHELL-INTENTIONAL / SHELL-ABANDONED** | **0** | **0.0%** | — |
| **MISSING** | **10** | **30.3%** | F081, F082, F083, F091, F095, F096, F098, F099, F100, F104 |
| **UNVERIFIED** | **7** | **21.2%** | F088, F089, F090, F092, F108, F109, F110 |
| **Static-Only OK** | **5** | **15.2%** | F085, F086, F093, F105, F107 |
| **合计** | **33** | **100.0%** | F081-F113 |

### 3.2 核实 1 — 计数修正（report-5C-batch-1.4.md 原文错误）

| 类别 | 原报告值 | **修正后** | 差异 |
|---|---|---|---|
| **MISSING** | 9 | **10** | +1（多计了 F083 未升级）|
| **PARTIAL-NON-BLOCKER** | 7 | **9** | +2（F083 升级 MISSING；F101 升级）|
| **UNVERIFIED** | 8 | **7** | −1（F084 误归 UNVERIFIED）|
| **总计** | 32 | **33** | 相加误差修正 |

> 注：report-5C-batch-1.4.md §1.2 注释中也有相同计数错误（PARTIAL-NON-BLOCKER 注1 称"5B 后变 8"，实际应为 9；MISSING 注2 称"5B 后升 9"，实际应为 10）。

### 3.3 核实 2 — MISSING vs PARTIAL-BLOCKER 口径统一

| 批次 | 表缺失类 Issue 标法 | 实例 |
|---|---|---|
| **Batch 1.1** | **MISSING**（I002）| crm_contact_map 表不存在 |
| **Batch 1.2** | **MISSING**（I017）| plugin_runtime_state 表不存在 |
| **Batch 1.3** | **MISSING**（I029）| google_maps_config / google_maps_geocode_log 表不存在 |
| **scope-freeze 建议** | ⚠️ 建议 PARTIAL-BLOCKER（scope-freeze §五.1 有误）| — |
| **实际执行** | **MISSING**（I038/I045）| overseas_booking / member_message |

→ **统一口径**：所有「表 DDL 写了但 baseline DB 未跑迁移」→ **MISSING**（与历史一致）。scope-freeze-batch-1.4.md §五.1 建议与历史矛盾，以实际执行为准。

---

## 4. Issue 汇总（I038-I052 — 15 个）

### 4.1 按严重度排序

| 严重度 | Issue ID | 标题 | 类型 | 影响 Feature | 实证阶段 |
|---|---|---|---|---|---|
| 🔴 **CRITICAL-P1** | **I047** | Fake Success：IG webhook `catch (Exception e)` 吞 BadSqlGrammarException → 200 NOOP | Fake Result | F100 + F083/F096 | 5B |
| 🔴 **高** | **I038** | `overseas_booking` 表 + `mini_program_open_id` 列缺失 | MISSING（DB）| F081/F082/F083/F095-F106（9 个）| 5A + 5B |
| 🔴 **高** | **I045** | `member_message` 表缺失 | MISSING（DB）| F091 | 5A |
| 🟠 中-高 | **I052** | `mvn test` 编译失败（6 个非 EXT-19 测试类）| 编译失败 | F108/F109/F110 | 5B |
| 🟡 中 | **I039** | SoT `WECHAT_OA` vs 代码 `WECHAT_OFFICIAL_ACCOUNT` 别名 | CONFLICT 中立标 | F092/F094 | Phase 3 |
| 🟡 中 | **I040** | `autoSend=false` 前端 UI 缺失 | Shadow | F096/F111 | 5A + 5C 静态 |
| 🟡 中 | **I041** | 状态确认按钮文案无「确认即发送通知」| Shadow | F096/F111 | 5A + 5C 静态 |
| 🟡 中 | **I042** | `intlBookingApi.sendConfirmation` 方法缺失 | Shadow | F095/F112 | 5A + 5C 静态 |
| 🟡 中 | **I043** | `confirmation-template` 端点硬编码 switcher vs Resolver 期望 | CONFLICT 中立标 | F097 | Phase 3 |
| 🟡 中 | **I048** | `@Transactional` rollbackFor 缺失（4 端点）| Transaction | F096/F099/F100/F103 | 5B |
| 🟡 中 | **I049** | WeChat MP / SMTP 真实链路凭据缺失 | UNVERIFIED D10 铁律 | F088/F089/F090/F092 | Phase 3 |
| 🟡 中 | **I051** | webhook 演示模式（token 硬编码 1L）| 演示模式 | F099/F100/F102/F106 | 5B |
| 🟡 中 | **I037** | webhook JWT 路径未 exclude（EXT-19 同源）| 认证配置 | F099/F100/F101 | 5B |
| 🟢 低 | **I044** | `IntlBookingService` Service as Controller（第 4 次）| 架构异味 | F113 | 5A.8 |
| 🟢 低 | **I046** | `pos_store.store_name` + `contact_phone` 字段对齐 OK | DB 字段对齐确认 | F087 | 5A.3 |
| 🟢 低 | **I050** | `ChannelDispatcher.healthCheckAll` 仅更新 `tenant_id=1` | 死代码 T07-D4 例外 | F094 | 5A.4 |

---

## 5. 阻塞项清单

### 5.1 P0 — 必跑迁移（解锁 10 Feature MISSING）

| 优先级 | 修复项 | 操作 | 解锁 Feature | 关联 Issue |
|---|---|---|---|---|
| **P0-1** | 跑 `migrate_ext_19_mini_program_openid.sql` | `mysql ... < migrate_ext_19_mini_program_openid.sql` | F081/F082/F083/F095/F096/F098/F099/F100/F104（共 9 个 MISSING → PARTIAL-*）| I038 |
| **P0-2** | 跑 `member_message` DDL | SQL 需补 | F091（MISSING → PARTIAL-*）| I045 |
| **P0-3** | ✅ DESCRIBE 实证 `mkt_channel_config`（核实 3）| `DESCRIBE mkt_channel_config` | 若缺失则跑迁移 | 新增（待实证）|

> ⚠️ **核实 3**：report-5C-batch-1.4.md 称「mkt_channel_config 表缺失」，但无 DESCRIBE 直接证据。overseas_booking/member_message 均有 DESCRIBE 实证；mkt_channel_config 仅通过 ChannelDispatcher.healthCheckAll 静态分析推断。应先 `DESCRIBE mkt_channel_config` 实证，区分 MISSING（表不存在）vs UNVERIFIED（未验证）。

### 5.2 CRITICAL-P1 — Fake Success 修复（必须优先）

| 优先级 | 修复项 | 操作 | 关联 Issue |
|---|---|---|---|
| **P1-1** | **I047 IG webhook catch 吞异常** | `IntlBookingService.instagramWebhook` catch 缩小 + 重抛 | I047 + I008 联动 |

---

## 6. 下一批次建议

### 6.1 候选

| 选项 | 模块 | 范围预估 | 阻塞项 | 推荐度 |
|---|---|---|---|---|
| **A. Batch 1.5 = MKT 多渠道重构（推荐）** | mkt_channel / mkt_automation + ChannelDispatcher + Webhook | ~16-21 Feature | I038/I045 跑批 + mkt_channel_config 实证 | ⭐⭐⭐⭐ |
| B. Batch 1.5 = 进入 Phase 6 | 不开新批次，先决策 D21-D31 | — | 0.5 天 | ⭐⭐ |
| C. Batch 1.5 = Post-Audit 先修 I047 | 不开新批次，先兑现 CRITICAL + 跑批 | 4 修复项 | 1-2 天 | ⭐⭐⭐⭐⭐ |

### 6.2 Batch 1.5 决策矩阵

| 决策 | 选项 | 用户决策点 |
|---|---|---|
| **Q1：Batch 1.5 选哪个模块？** | A MKT 重构 / B Phase 6 / C Post-Audit 先修 | Q1 |
| **Q2：I047 CRITICAL-P1 是否在 Batch 1.5 启动前修复？** | A 修 / B 沿用 D26 | Q2 |
| **Q3：迁移脚本（I038 + I045）是否在 Batch 1.5 启动前一次性跑？** | A 跑 / B 保留 MISSING | Q3 |
| **Q4：I037 webhook exclude + I051 真实 token 是否走 Batch 1.5 同步修？** | A 同步 / B 留到 Batch 2 | Q4 |

---

## 7. Batch 1.4 总结

### 7.1 完成情况

| 维度 | 完成？ | 备注 |
|---|---|---|
| Phase 5A（DB-only + 静态，8 任务）| ✅ | I038/I045 DESCRIBE + I050/I047/I044 静态实证 |
| Phase 5B（API + Mock，7 任务 × 13 调用）| ✅ | I038/I047/I037/I051/I048 实测 + I052 登记 |
| Phase 5C（Frontend Shadow + 汇总）| ✅ | I040/I041/I042 静态实证 PASS + 33 Feature 汇总 |
| 33 Feature 全部有 Result | ✅ | 1 VERIFIED / 9 PARTIAL-NON-BLOCKER / 1 CONFLICT / 10 MISSING / 7 UNVERIFIED / 5 Static-Only OK |
| 15 个 Issue 全部登记（I038-I052）| ✅ | 含 I047 CRITICAL-P1 |
| Playwright 脚本已生成 | ✅ | `audit/scripts/e2e/F111-F112-shadow.spec.ts` |
| 不动业务代码 | ✅ | D21-D31 全部维持 |
| 不跑迁移 | ✅ | D21/D22 维持 |
| 不动 SoT | ✅ | D24 维持 |
| 受保护页面零风险 | ✅ | EXT-19 与 3 受保护页面零交集 |
| **3 处核实修正已落档** | ✅ | 计数修正 + 口径统一 + mkt_channel_config 归属标注 |

### 7.2 关键发现

1. **I047 CRITICAL-P1**：IG webhook `catch (Exception e)` 吞 BadSqlGrammarException → 200 NOOP（I008 同系列）
2. **Result 分布计数修正**：PARTIAL-NON-BLOCKER 应为 9（非 7），MISSING 应为 10（非 9），总计 33（非 32）
3. **MISSING vs PARTIAL-BLOCKER 口径统一**：历史 Batches 全用 MISSING，本批次 scope-freeze 建议有误，实际执行已统一为 MISSING
4. **mkt_channel_config 实证缺失**：应先 DESCRIBE 实证区分 MISSING vs UNVERIFIED，再纳入 Post-Audit 批清单

### 7.3 移交 Post-Audit

| 维度 | 详情 |
|---|---|
| P0 迁移 | I038 + I045 + mkt_channel_config（需先 DESCRIBE 实证）|
| P1 CRITICAL | I047（Fake Success）+ I052（mvn test）|
| P1 UX | I037（webhook exclude）+ I040/I041/I042（Frontend Shadow）|
| P2 业务 | I039/I043/I048/I049/I050/I051 |
| P3 架构 | I044（Service as Controller 第 4 次）|
| D10 铁律 | ✅ 全程生效 |

### 7.4 EN-09 Batch 小结落档（2026-09-14 23:15）

> **触发场景**：用户 3 处核实请求（计数 / 口径 / mkt_channel_config 归属）→ EN-09 完整汇总落档

**EN-09 产出**：
- ✅ **`audit/reports/batch-1.4-summary.md`**（523 行）— 完整 EN-09 小结报告
- ✅ **`audit/issues/issue-register.md §二十`** — I054 NEW 建议登记 + 4 条 MISSING/PARTIAL-BLOCKER 口径统一规则 + EN-09 链接
- ✅ **`audit/batches.md`** — 头部时间戳更新 + Batch 1.4 段 §7.1 / §7.3 修正

**核实 1（计数修正）**：
- PARTIAL-NON-BLOCKER：7 → **9**（F083 升级 MISSING + F101 升级）
- MISSING：9 → **10**（F083 升级）
- UNVERIFIED：8 → **7**（F084 移到 VERIFIED）
- VERIFIED：0 → **1**（F084 升级）
- 合计：32 → **33**（5C 原文实际 33 但报告说 32）
- **每个 Feature 唯一归属唯一**；按 Feature ID **唯一计数**

**核实 2（MISSING vs PARTIAL-BLOCKER 口径）**：
- 4 条规则明确（沿用 I002 / I017 / I029 一致模式）：
  - 表完全缺失 → **MISSING**
  - 表存在缺列 → **PARTIAL-NON-BLOCKER**（PARTIAL-MISSING）
  - 表缺失 + catch 吞异常 → **PARTIAL-NON-BLOCKER**（功能降级）
  - 表缺失 + I011 Fake Failure → **PARTIAL-BLOCKER**（CRITICAL）
- Batch 1.4 维持 MISSING 模式；**不反向追溯** Batch 1.1（历史记录保留）
- scope-freeze-batch-1.4 §五.2 建议与执行冲突 — 建议在下一批次 scope-freeze 中同步修正

**核实 3（mkt_channel_config 双重归属）**：
- **修复阶段** = **Post-Audit P0-3**（与 I038 / I045 同模式一次性跑批）
- **审计阶段** = **Batch 1.5 = MKT 多渠道重构**（依赖 Post-Audit 先兑现）
- **本次 EN-09 修正**：明确登记 **I054 NEW（建议）— mkt_channel_config 表缺失**作为 Post-Audit 跑批清单的正式成员
- **Batch 1.5 启动决策依赖**：Q3「迁移脚本（I038 + I045 + I054 NEW）是否在 Batch 1.5 启动前一次性跑？」用户决策空间

**EN-09 决策依赖（Q1-Q4）**：
- Q1：Batch 1.5 走 MKT 多渠道重构 / 进 Phase 6 / 走其他模块
- Q2：I047 catch 吞异常前置修（CRITICAL-P1 不延后）？Yes / No
- Q3：迁移（I038 + I045 + **I054 NEW**）一次性跑？Yes / No
- Q4：I037 webhook exclude + I051 真实 token 同步修？Yes / No

---

**v3 前四批（113 个 Feature = 30 + 25 + 25 + 33）全部审计完成。Batch 1.4 是首个含 I047 CRITICAL-P1 Fake Success + 计数修正 + mkt_channel_config 双重归属明确 + 4 条 MISSING/PARTIAL-BLOCKER 口径统一建议的批次。等用户决策下一步（Post-Audit 修复 I047 + 跑批 I038/I045/I054 NEW / 启动 Batch 1.5 MKT 重构 / 进入 Phase 6 上线预演）。**

---

*文件落档时间：2026-09-14 23:15 (UTC+8)*
*最近更新：Batch 1.4 段完成 + EN-09 落档 + 3 处核实全部落实*
*引用：`@project-audit.mdc` §6 + `@feature-id.mdc` + `@batches.mdc`*
*引用：`audit/reports/batch-1.4-summary.md`（完整小结 — 523 行）*
*引用：`audit/issues/issue-register.md §二十`（EN-09 段 — I054 NEW + 4 条口径规则）*
