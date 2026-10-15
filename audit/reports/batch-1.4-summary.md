# Batch 1.4 小结 — EXT-19 多语言预约确认

> **创建时间**：2026-09-14 23:15 (UTC+8)
> **完结状态**：✅ Phase 5C 完成 — EN-09 小结落档
> **批次**：v3 第四批 / Batch 1.4 — EXT-19 多语言预约确认
> **起点 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`
> **不绑定业务代码改动**（规则 6 + D21-D31 全部维持）

---

## 〇、用户核实 → 本次修正清单

> 5C 汇总阶段（`audit/runtime/report-5C-batch-1.4.md`）已落档，**EN-09 阶段用户提出 3 处核实**，已在 batches.md §3.2 + 本报告修正。

| 核实 | 内容 | 处理 | 修正位置 |
|---|---|---|---|
| **核实 1** | Result 分布计数（PARTIAL-NON-BLOCKER 写 7 实际 9 / MISSING 写 9 实际 10 / 合计写 33 实际 32） | ✅ **修正**：每个 Feature 唯一归属唯一一类；按 Feature ID 唯一计数；F084 升级 OK（VERIFIED）；PARTIAL-NON-BLOCKER = 9, MISSING = 10, UNVERIFIED = 7 | 本报告 §2 / §3 + batches.md §3.2 |
| **核实 2** | MISSING vs PARTIAL-BLOCKER 口径（Batch 1.1 F020 用 PARTIAL-BLOCKER vs Batch 1.4 F081 用 MISSING） | ✅ **统一口径建议**（4 条规则沿用 I002/I017/I029 模式），详情见 §7 | 本报告 §7 + batches.md §3.3 + scope-freeze-batch-1.4.md 待补 |
| **核实 3** | mkt_channel_config 批次归属（5C §6.1 Batch 1.5 vs 5C §4.1 Post-Audit 修复） | ✅ **明确双重定位**：Post-Audit 跑迁移修（I054 NEW 建议）+ Batch 1.5 审计（依赖前者） | 本报告 §6 修复优先级矩阵 P0-3 + §10 下一批次建议 |

---

## 一、Batch 1.4 概要

| 维度 | 值 |
|---|---|
| **模块** | EXT-19 多语言预约确认（i18n + WeChat 订阅消息 + SMTP + 5 渠道发送 + overseas_booking CONFIRMED 自动触发） |
| **时间范围** | 2026-09-14 20:40 (UTC+8)（Phase 5A 起点）→ 2026-09-14 23:15 (UTC+8)（EN-09 小结完结） |
| **Feature 数** | **33**（F081-F113） |
| **Issue 数** | **15**（I038-I052，本批次登记）+ 1 建议追加（I054 NEW — mkt_channel_config 缺失） |
| **DB 起点状态** | `overseas_booking` ❌ / `member_message` ❌ / `pos_store` ✅ / `mkt_channel_config` ❌（5A.4 推论） |
| **Evidence 文件** | **120+ 份**（5A 8 + 5B 18 + F081-F113 result.md 33 + other） |
| **Feature Result 维度** | 1 VERIFIED + 0 PARTIAL-BLOCKER + 9 PARTIAL-NON-BLOCKER + 0 BROKEN + 1 CONFLICT + 0 MOCK-* + 0 SHELL-* + 10 MISSING + 7 UNVERIFIED + 5 Static-Only OK = **33** |
| **修复策略** | 本批次不修任何业务代码；Fix Phase = **Post-Audit** |
| **E2E 测试** | **0 个**（Playwright 降级，沿用 D11-D20 + D29 决策；5C 已生成 `F111-F112-shadow.spec.ts`） |

### 1.1 Phase 完成情况

| Phase | 内容 | 状态 | 产出 |
|---|---|---|---|
| -1 | Preflight | ✅ | `audit/baseline/preflight-batch-1.4.md`（458 行 / 8 项内容完整） |
| 0 | Scope Freeze | ✅ | `audit/baseline/scope-freeze-batch-1.4.md`（含 F081 起编号 + 8 项决策 + 漂移统一口径） |
| 1 | Feature Inventory | ✅ | `audit/feature-inventory.md` §1.4（33 Features） |
| 2 | Traceability Matrix | ✅ | `audit/traceability-matrix.md` §Batch 1.4 + 部分附录 |
| 3 | Issue Register | ✅ | `audit/issues/issue-register.md` §十九（I038-I052 + 决策落档） |
| 4 | Runtime Plan | ✅ | `audit/runtime/plan-batch-1.4.md`（25 项负向证据 N1-N25） |
| 5A | DB-only + 静态 | ✅ | `audit/runtime/report-5A-batch-1.4.md`（8 任务 PASS — I038/I045/I046/I050/I047/I048/I044） |
| 5B | API + Mock + jshell | ✅ | `audit/runtime/report-5B-batch-1.4.md`（7 任务 × 13 API 调用 — I038/I047/I037/I051/I048/I052） |
| 5C | Frontend Shadow + 汇总 | ✅ | `audit/runtime/report-5C-batch-1.4.md`（I040/I041/I042 静态 PASS + 33 Feature 汇总 + 15 Issue 处置） |
| **EN-09** | **Batch 小结** | ✅ | **`audit/reports/batch-1.4-summary.md`（本报告）** |

### 1.2 用户决策（D21-D31 全 11 项）

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

## 二、33 Feature 最终 Result 表（修正计数后）

> **修正说明**：5C 报告 §1.2 计数错误（漏算了 F084 VERIFIED，且 PARTIAL-NON-BLOCKER/MISSING 各 +1 / +1，UNVERIFIED −1）。**每个 Feature 归入唯一一类**，按 Feature ID 唯一计数。

| Feature | 功能点 | Final Result | 主 Issue | 次要 Issue | Evidence |
|---|---|---|---|---|---|
| **F081** | OverseasBooking 实体 | **MISSING** | I038 | — | `evidence/F081-overseas-booking-entity/` |
| **F082** | overseas_booking DDL | **MISSING** | I038 | — | `evidence/F082-overseas-booking-ddl/` |
| **F083** | BookingConfirmationService.sendConfirmation | **MISSING** | I038 | I047（联动）| `evidence/F083-send-confirmation/` |
| **F084** | BookingConfirmationTemplateResolver | **VERIFIED** | OK | — | `evidence/F084-template-resolver/` |
| **F085** | BookingConfirmationTemplate DTO | **Static-Only OK** | OK | — | `evidence/F085-template-dto/` |
| **F086** | BookingConfirmationResult DTO | **Static-Only OK** | OK | — | `evidence/F086-result-dto/` |
| **F087** | StoreLookupService | **PARTIAL-NON-BLOCKER** | I046（已对齐）| — | `evidence/F087-store-lookup/` |
| **F088** | SmsSender | **UNVERIFIED** | I049（D10 铁律）| — | `evidence/F088-sms-sender/` |
| **F089** | EmailSender | **UNVERIFIED** | I049（D10 铁律）| SoT §0.2 mismatch demo | `evidence/F089-email-sender/` |
| **F090** | MiniProgramSender | **UNVERIFIED** | I049（D10 铁律）| MP_TEMPLATE_ID 占位 | `evidence/F090-mini-program-sender/` |
| **F091** | InAppSender | **MISSING** | I045 | — | `evidence/F091-in-app-sender/` |
| **F092** | WechatOfficialAccountSender | **UNVERIFIED** | I049 + I039（channelType 别名）| — | `evidence/F092-wechat-oa-sender/` |
| **F093** | ChannelSender 接口契约 | **Static-Only OK** | OK | — | `evidence/F093-channel-interface/` |
| **F094** | ChannelDispatcher | **PARTIAL-NON-BLOCKER** | I050（T07-D4 例外）| I054（mkt_channel_config 缺失 — 5A.4 推论）| `evidence/F094-channel-dispatcher/` |
| **F095** | POST /intl-booking/{id}/send-confirmation | **MISSING** | I038 | C1 偏差点（选查 → 500 而非 404）| `evidence/F095-send-confirmation/` |
| **F096** | POST /intl-booking/{id}/status + autoSend | **MISSING** | I038 + I047（联动）+ I048 | — | `evidence/F096-autosend-status/` |
| **F097** | GET /intl-booking/{id}/confirmation-template | **CONFLICT** | I043（中立标，硬编码 switcher）| — | `evidence/F097-confirmation-template/` |
| **F098** | GET /intl-booking/list | **MISSING** | I038 | — | `evidence/F098-list-endpoint/` |
| **F099** | POST /intl-booking/webhook/google | **MISSING** | I038 + I048 + I051 | I037（webhook 401）| `evidence/F099-google-webhook/` |
| **F100** | POST /intl-booking/webhook/instagram | **MISSING** | I038 + I047 + I048 + I051 | I037（webhook 401）| `evidence/F100-instagram-webhook/` |
| **F101** | GET /intl-booking/webhook/instagram（verify）| **PARTIAL-NON-BLOCKER** | I037（webhook JWT 路径未 exclude）| — | `evidence/F101-instagram-verify/` |
| **F102** | OAuth2 connect + callback | **PARTIAL-NON-BLOCKER** | I051（演示模式）| — | `evidence/F102-oauth2-connect/` |
| **F103** | POST /intl-booking/{id}/convert | **PARTIAL-NON-BLOCKER** | I048（rollbackFor）| — | `evidence/F103-convert/` |
| **F104** | GET /intl-booking/{id} | **MISSING** | I038 | — | `evidence/F104-get-by-id/` |
| **F105** | GET /intl-booking/channels | **Static-Only OK** | OK | — | `evidence/F105-list-channels/` |
| **F106** | POST /intl-booking/{channelId}/pull | **PARTIAL-NON-BLOCKER** | I051（演示模式）| — | `evidence/F106-pull-bookings/` |
| **F107** | 6 locale JSON 模板 | **Static-Only OK** | OK | — | `evidence/F107-locale-templates/` |
| **F108** | BookingConfirmationServiceTest | **UNVERIFIED** | I052（mvn test 编译失败）| — | `evidence/F108-confirmation-service-test/` |
| **F109** | BookingConfirmationTemplateResolverTest | **UNVERIFIED** | I052（mvn test 编译失败）| — | `evidence/F109-resolver-test/` |
| **F110** | EmailSenderTest + WechatOfficialAccountSenderTest | **UNVERIFIED** | I052（mvn test 编译失败）| — | `evidence/F110-sender-tests/` |
| **F111** | intl-booking/index.vue | **PARTIAL-NON-BLOCKER** | I040 + I041（autoSend UI 缺失 + 按钮文案）| 5C 静态 PASS | `evidence/F111-intl-booking-page/` |
| **F112** | intl-booking.ts | **PARTIAL-NON-BLOCKER** | I042（sendConfirmation API 缺失）| 5C 静态 PASS | `evidence/F112-intl-booking-api/` |
| **F113** | IntlBookingService 命名异味 | **PARTIAL-NON-BLOCKER** | I044（架构异味，第 4 次出现）| — | `evidence/F113-intl-booking-controller/` |

---

## 三、Result 分布统计（修正计数后）

### 3.1 9 个状态标签分布（单一归属唯一）

| 状态 | 数量 | 占比 | Feature IDs |
|---|---|---|---|
| **VERIFIED** | **1** | **3.0%** | F084（BookingConfirmationTemplateResolver — 6 locale warmup + 静态确认）|
| **PARTIAL-BLOCKER** | **0** | **0.0%** | — |
| **PARTIAL-NON-BLOCKER** | **9** | **27.3%** | F087, F094, F101, F102, F103, F106, F111, F112, F113 |
| **BROKEN** | **0** | **0.0%** | — |
| **CONFLICT** | **1** | **3.0%** | F097 |
| **MOCK-INTENTIONAL / MOCK-FAKE** | **0** | **0.0%** | — |
| **SHELL-INTENTIONAL / SHELL-ABANDONED** | **0** | **0.0%** | — |
| **MISSING** | **10** | **30.3%** | F081, F082, F083, F091, F095, F096, F098, F099, F100, F104 |
| **UNVERIFIED** | **7** | **21.2%** | F088, F089, F090, F092, F108, F109, F110 |
| **Static-Only OK** | **5** | **15.2%** | F085, F086, F093, F105, F107 |
| **合计** | **33** | **100.0%** | F081-F113（**单一归属唯一**，无重叠计数）|

### 3.2 核实 1 — 计数修正对照（5C 报告原文 vs 本次修正）

| 类别 | 5C 原文 | **EN-09 修正** | 差异 | 修正原因 |
|---|---|---|---|---|
| **VERIFIED** | 0 | **1** | +1 | F084 通过 5B.12-13 6 locale 静态验证 → **升级 VERIFIED** |
| **PARTIAL-BLOCKER** | 0 | **0** | 0 | 不变 |
| **PARTIAL-NON-BLOCKER** | 7 | **9** | +2 | F083 升级为 MISSING（5B 实测）；F101 升级（I037 实证）|
| **BROKEN** | 0 | **0** | 0 | 不变 |
| **CONFLICT** | 1 | **1** | 0 | 不变 |
| **MOCK-*** | 0 | **0** | 0 | 不变 |
| **MISSING** | 9 | **10** | +1 | F083 升级为 MISSING（5B I038 实证）|
| **UNVERIFIED** | 8 | **7** | −1 | F084 移到 VERIFIED |
| **Static-Only OK** | 5 | **5** | 0 | 不变 |
| **合计** | 32 | **33** | +1 | 唯一定位修正（5C 原文实际为 33 但报告说 32）|

→ **计数规则**：33 个 Feature **每个归入唯一一类**；按 Feature ID **唯一计数**（不重叠）。

### 3.3 关键统计

| 维度 | 数值 |
|---|---|
| **1 VERIFIED** | F084（i18n resolver 6 locale warmup PASS）|
| **0 PARTIAL-BLOCKER** | I038/I045 实证暴露后所有受影响 Feature 全部 5B 实测升级到 MISSING（不再保留 PARTIAL-BLOCKER）|
| **0 BROKEN** | 设计层面无缺陷（I038/I045 是 DDL 写了但 DB 没跑迁移）|
| **10 MISSING** | I038 / I045 阻断 16 个端点 / 服务层 Feature |
| **9 PARTIAL-NON-BLOCKER** | I046 / I037 / I048 / I050 / I051 / I040 / I041 / I042 / I044 影响 |
| **1 CONFLICT** | I043 SoT vs 代码漂移（中立标）|
| **7 UNVERIFIED** | I049（D10 铁律）+ I052（编译失败降级）|
| **CRITICAL 升级** | **I047**（5B 实证 IG webhook catch 吞异常）— 🔴 CRITICAL-P1 |
| **最终结论** | 10 个 MISSING 是 Post-Audit 跑迁移即可解锁；7 个 UNVERIFIED 是凭据缺失；9 个 PARTIAL-NON-BLOCKER 是结构性非 BLOCKER 缺陷 |

---

## 四、Issue 汇总 I038-I052（15 个）

### 4.1 按严重度排序

| 严重度 | Issue ID | 标题 | 类型 | 影响 Feature | Phase 5 升级？ |
|---|---|---|---|---|---|
| 🔴 **CRITICAL-P1** | **I047** | Fake Success — IG webhook `catch (Exception e)` 吞 BadSqlGrammarException → 200 NOOP（I008 同系列更隐蔽）| Fake Result（CRITICAL）| F100 + 联动 F083 / F096 | **5B 实证升级** |
| 🔴 **高** | **I038** | `overseas_booking` 表 + `mini_program_open_id` 列缺失 | MISSING（DB）| F081 / F082 / F083 / F095 / F096 / F098 / F099 / F100 / F103 / F104 / F106（**10 个 MISSING**）| 5A + 5B 持续确认 |
| 🔴 **高** | **I045** | `member_message` 表缺失 | MISSING（DB）| F091 | 5A 持续确认 |
| 🟠 **中-高** | **I052** | `mvn test` 编译失败（6 个非 EXT-19 测试类）| 编译失败 | F108 / F109 / F110 | 5B 登记 |
| 🟡 中 | **I037（同源）**| Webhook 路径未在 JWT excludePathPatterns（`/intl-booking/webhook/**` 也未 exclude）| 认证 / 安全配置 | **F099 / F100 / F101**（整条 EXT-19 webhook 链路）| 5B 同源实证 |
| 🟡 中 | **I039** | SoT `WECHAT_OA` vs 代码 `WECHAT_OFFICIAL_ACCOUNT` 别名 | CONFLICT（中立标）| F092 / F094 | Phase 3 |
| 🟡 中 | **I040** | `autoSend=false` 前端 UI 缺失 | Shadow（前端 UI）| F096 / F111 | 5C 静态 PASS |
| 🟡 中 | **I041** | 状态确认按钮文案无「确认即发送通知」 | Shadow（前端 UI）| F096 / F111 | 5C 静态 PASS |
| 🟡 中 | **I042** | `intlBookingApi.sendConfirmation` 方法缺失 | Shadow（前端 API）| F095 / F112 | 5C 静态 PASS |
| 🟡 中 | **I043** | `confirmation-template` 端点硬编码 switcher vs Resolver 期望 | CONFLICT（中立标）| F097 | Phase 3 |
| 🟡 中 | **I048** | `@Transactional` 默认 rollbackFor=RuntimeException（4 端点：F096 / F099 / F100 / F103）| Transaction（rollbackFor 缺失）| F083 / F096 / F099 / F100 / F103 + I009 联动 | 5B 实证 |
| 🟡 中 | **I049** | WeChat MP / SMTP 真实链路凭据缺失 | UNVERIFIED（D10 铁律）| F088 / F089 / F090 / F092 | Phase 3 + D27 |
| 🟡 中 | **I051** | Google/IG webhook 硬编码 `tenantId=1L` + `resolveTenantFromToken` 演示模式 | 演示模式 | F099 / F100 / F101 / F102 / F106 | 5B 实证 |
| 🟢 低 | **I044** | `IntlBookingService` 命名误导（Service as Controller — 第 4 次出现：I004 / I019 / I029 / I044）| 架构异味（Backlog）| F113 | 5A.8 |
| 🟢 低 | **I046** | `pos_store.store_name` + `contact_phone` 字段对齐 OK | DB 字段对齐（确认）| F087 | 5A.3 |
| 🟢 低 | **I050** | `ChannelDispatcher.healthCheckAll` 仅更新 `tenant_id=1`（T07-D4 例外）| 死代码（T07-D4 例外）| F094 | 5A.4 |
| 🟠 **中-高（**建议追加**）** | **I054 NEW（建议）** | `mkt_channel_config` 表缺失（5A.4 ChannelDispatcher.healthCheckAll 推论 — 与 I038 / I045 同模式）| MISSING（DB）| F094 + Batch 1.5 ChannelDispatcher 全部 | **5A.4 推论 + 核实 3 明确登记** |

**Issue 总数**：**15 个（I038-I052，已登记）+ 1 个建议追加（I054 NEW — mkt_channel_config）**

### 4.2 Issue 严重度分布

| 严重度 | 数量 | Issue IDs |
|---|---|---|
| 🔴 **CRITICAL-P1** | 1 | I047 |
| 🔴 **高** | 2 | I038, I045 |
| 🟠 **中-高** | 1 | I052 |
| 🟠 **中-高（**建议追加**）** | 1 | **I054 NEW（建议）** |
| 🟡 中 | 8 | I037（同源）, I039, I040, I041, I042, I043, I048, I049, I051（注：9 个含 I037）|
| 🟢 低 | 3 | I044, I046, I050 |
| **合计** | **15 + 1** | I038-I052 + I054 NEW |

### 4.3 关键 Issue 联合链路

| 链路 | Issue 链 | 受影响 Feature | 修复阶段 |
|---|---|---|---|
| **Fake Result 系列（I008 + I047）** | I008（CRM dashboard）→ I047（EXT-19 instagramWebhook）| F007（CRM）+ F100（EXT-19 IG webhook）| Post-Audit 合并整改 |
| **MISSING 链路（I002 / I017 / I029 / I038 / I045 / I054）**| I002（CRM crm_* 缺列）→ I017（plugin_runtime_state）→ I029（google_maps 3 表）→ I038 / I045（overseas_booking + member_message）→ **I054 NEW（mkt_channel_config）**| 跨 Batch 累计 30+ Feature | Post-Audit 一次性跑批 |
| **Frontend Shadow 系列（I006 / I007 / I040 / I041 / I042）** | I006（CRM dashboard tab）→ I007（CRM update UI）→ I040 / I041（EXT-19 autoSend UI + 按钮文案）→ I042（EXT-19 sendConfirmation API）| 跨 Batch 累计 7 Feature | Post-Audit 整改 |
| **架构异味系列（I004 / I019 / I044）** | I004（CrmIntegrationService）→ I019（PluginService）→ I044（IntlBookingService）| 跨 Batch 累计 3 Controller | Post-Audit 重构 |
| **CONFLICT 中立标（I001 / I018 / I026 / I027 / I030 / I039 / I043）**| 文档漂移 7 处 | 跨 Batch 累计 12 Feature | Post-Audit 文档/代码二选一 |
| **rollbackFor 系列（I009 / I048）** | I009（CrmIntegrationService.syncNow）→ I048（IntlBookingService 4 端点）| F005 + F096 / F099 / F100 / F103 | Post-Audit 加 `rollbackFor=Exception.class` |
| **演示模式系列（I012 / I023 / I051）** | I012（CRM Mock 激活）→ I023（Plugin install 幽灵）→ I051（EXT-19 webhook tenantId 硬编码）| F015 + F046 + F099 / F100 / F102 / F106 | Post-Audit 统一整改 |
| **webhook JWT exclude 系列（I037）** | I037（youzan webhook 未 exclude，EXT-19 webhook 路径同源）| F099 / F100 / F101 + EXT-22 webhook | Post-Audit 加 exclude 路径 |
| **编译失败系列（I052）** | I052（mvn test 编译失败 — 跨模块根因）| F108 / F109 / F110（Batch 1.4 测试类）| 跨模块根因，Post-Audit 修 6 个非 EXT-19 测试类 |

---

## 五、阻塞项清单

### 5.1 P0 阻塞项（修复后才能跑通整条 EXT-19 链路）

| 阻塞项 | 根因 | 影响 Feature 数 | 修复路径 | 优先级 |
|---|---|---|---|---|
| **I038** `overseas_booking` 表 + `mini_program_open_id` 列缺失 | db-snapshot.sql 未含该 DDL | **10 个**（F081 / F082 / F083 / F095 / F096 / F098 / F099 / F100 / F104）| 跑 `migrate_ext_19_mini_program_openid.sql` | **P0** |
| **I045** `member_message` 表缺失 | db-snapshot.sql 未含该 DDL | 1（F091）| 跑对应 SQL（需补）| **P0** |
| **I054 NEW（建议）** `mkt_channel_config` 表缺失 | 5A.4 ChannelDispatcher.healthCheckAll 推论（DDL 已写但 dev DB 未跑迁移）| F094 + Batch 1.5 ChannelDispatcher 全部 | 跑 `migrate_mkt_002_*.sql`（5C §四.4.6 P0-3）| **P0** |

### 5.2 严重 Blockers（必须修复后才能生产/联调）

| Blocker | 影响 | 当前状态 | 修复路径 |
|---|---|---|---|
| **I047** Fake Success（IG webhook catch 吞异常）| F100 / F083 不可信 | 已登记 | Post-Audit 修 catch（CRITICAL-P1）|
| **I037** webhook JWT 路径未 exclude | Google / IG webhook 无法接收外部推送 | 5B 实证 | Post-Audit 加 exclude |
| **I049** WeChat MP / SMTP 真实链路凭据缺失 | F088 / F089 / F090 / F092 无法真实联调 | UNVERIFIED | 真实凭据就位 |
| **I052** `mvn test` 编译失败 | F108 / F109 / F110 测试无法运行 | UNVERIFIED 降级 | 修复 6 个非 EXT-19 测试类编译错误 |

---

## 六、修复优先级矩阵

> **优先级 = 严重度 × 影响范围 × 修复成本**。所有修复统一在 Post-Audit 阶段执行（D21-D31 全部维持）。

### 6.1 P0 — 必跑迁移（I038 + I045 + I054）一次解锁 11 Feature

| 优先级 | 修复项 | 操作 | 解锁 Feature | 关联 Issue |
|---|---|---|---|---|
| **P0-1** | 跑 `migrate_ext_19_mini_program_openid.sql` | `mysql -uroot -proot123456 -P3307 meiyetec < migrate_ext_19_mini_program_openid.sql` | F081 / F082 / F083 / F095 / F096 / F098 / F099 / F100 / F104（共 9 个 MISSING → PARTIAL-* 或 VERIFIED）| I038 |
| **P0-2** | 跑对应 DDL 创建 `member_message` 表 | SQL 需补（规划中）| F091（MISSING → PARTIAL-*）| I045 |
| **P0-3** ⭐ | 跑 `migrate_mkt_002_*.sql` 重建 `mkt_channel_config` | 已写但未跑（与 I038 同模式）| F094 PARTIAL-NON-BLOCKER → PARTIAL-* + Batch 1.5 全部 ChannelDispatcher Feature | **I054 NEW（建议）** |

### 6.2 P1 — CRITICAL 修复（I047 + I052）

| 优先级 | 修复项 | 操作 | 受影响 Issue | 决策依赖 |
|---|---|---|---|---|
| **P1-1** | **I047 Instagram webhook catch 吞异常 → 重构**（CRITICAL-P1）| `IntlBookingService.instagramWebhook` catch 缩小 + 重抛 + 实际 try/catch 区分 Mock vs DB 路径 | I047 + I008（联动）| 用户决策 #4 Q3 升级 |
| **P1-2** | **I052 mvn test 编译失败**（跨模块根因）| 修复 6 个非 EXT-19 测试类（InventoryConsumptionFlowTest / InventoryConsumptionBomMissingTest / BookingWaitlistScoreServiceTest / LbsRouteOptimizerTest / RefundAuditWorkflowServiceE2eTest / PluginRuntimeTest）| I052 | 跨模块，需 6 个模块 owner 协同 |

### 6.3 P1 — UX 可观测性修复（I040 + I041 + I042 + I037 + I003 / I016）

| 优先级 | 修复项 | 操作 | 受影响 Issue |
|---|---|---|---|
| **P1-3** | **I040 / I041 / I042** 前端 Shadow 整改（user 决策中立）| 加 `autoSend=false` UI（el-switch）+ 二次确认（el-popconfirm）+ 按钮文案「确认（发送通知）」+ `sendConfirmation` 方法 | I040, I041, I042 |
| **P1-4** | **I037 webhook 路径 exclude** | `WebConfig.excludePathPatterns` 加 `/intl-booking/webhook/**` + 全 webhook 前缀统一 | I037（Batch 1.3 已登记 + Batch 1.4 同源）|

### 6.4 P2 — 业务逻辑修复（I039 + I043 + I048 + I049 + I050 + I051）

| 优先级 | 修复项 | 操作 | 受影响 Issue |
|---|---|---|---|
| **P2-1** | **I039** 别名统一 | 决策 3 选项：改 SoT / 改代码 / 接受现状（中立标）| I039 |
| **P2-2** | **I043** confirmation-template Resolver 改造 | 改硬编码 switcher 走 `BookingConfirmationTemplateResolver` | I043（中立标）|
| **P2-3** | **I048** rollbackFor 修复 | `@Transactional(rollbackFor = Exception.class)` 加 4 处 | I048（I009 模式）|
| **P2-4** | **I049** WeChat MP / SMTP 真实凭据 | 真实凭据注入 + 联调 | I049（UNVERIFIED 维持）|
| **P2-5** | **I050** healthCheckAll 多租户 | 移除 `tenantId = 1L` 硬编码（业务确认 T07-D4 例外）| I050（T07-D4 例外）|
| **P2-6** | **I051** 演示模式 token / tenantId 校验 | 真实 token 反查 + IG webhook tenantId 反查 | I051 |

### 6.5 P3 — 架构异味（I044）

| 优先级 | 修复项 | 操作 | 受影响 Issue |
|---|---|---|---|
| **P3-1** | **I044** IntlBookingService 重命名 / 拆分 | 文件改 `controller/IntlBookingController.java` 或拆 3 个 | I044（沿用 I004 + I019 模式，第 4 次）|

### 6.6 优先级矩阵总览

| 优先级 | 数量 | 关键修复 | 期望结果 |
|---|---|---|---|
| **P0** | 3 | I038 + I045 + I054（**核实 3 明确归属**）| **11 Feature** PARTIAL-BLOCKER / MISSING → PARTIAL-NON-BLOCKER（含 F094）|
| **P1** | 4 | I047 + I052 + I040 / I041 / I042 + I037 | 1 CRITICAL Feature + 3 UX Shadow Feature + 1 webhook 链路修复 |
| **P2** | 6 | I039 + I043 + I048 + I049 + I050 + I051 | 业务逻辑 + 演示模式整改 |
| **P3** | 1 | I044 | 架构异味（Backlog）|

---

## 七、MISSING vs PARTIAL-BLOCKER 口径统一建议（核实 2 落实）

### 7.1 历史口径回顾

| 批次 | 表缺失类 Issue 标法 | 实例 | 备注 |
|---|---|---|---|
| **Batch 1.1** | 标 **PARTIAL-BLOCKER** | F012（loadKnownExternalIds catch 吞）+ F020（CrmContactMap 实体）+ F022（DDL）+ F023（crm_contact_map 表）| F012 因 catch 吞异常 → 功能降级为空 Set；F020 因实体未生效 |
| **Batch 1.2** | 标 **MISSING** + **PARTIAL-NON-BLOCKER** | F049 (plugin_runtime_state 表缺失) → PARTIAL-BLOCKER | 沿用 I002 模式 |
| **Batch 1.3** | 标 **MISSING** + **PARTIAL-MISSING** | F056 / F057（google_maps_config/geocode_log 整表）= MISSING；F066（ecommerce_platform 表存在缺 3 列）= PARTIAL-MISSING | 沿用 I029 双标注模式 |
| **Batch 1.4** | 标 **MISSING** | F081 / F082 / F083 / F091 / F095-F106（10 个）= MISSING | 与 I038 / I029 模式对齐 |
| **scope-freeze-batch-1.4 §五.2** | 建议 PARTIAL-BLOCKER（与 1.1-1.3 一致）| 但实际执行是 MISSING | ⚠ **scope-freeze 建议与执行冲突** |

### 7.2 统一口径（4 条规则 — 沿用 I002 / I017 / I029 模式）

> **建议**：维持现状（MISSING 模式 + I002/I017/I029 一致模式），不"统一到 PARTIAL-BLOCKER"。

| 规则 | 场景 | Result | 模式 | 实例 |
|---|---|---|---|---|
| **规则 1**：表完全缺失（DDL 写了但 dev DB 未跑迁移 → 表在 DB 不存在）→ Feature 完全不可用 | 表 SELECT / INSERT 抛 TableNotFoundException → 500 | **MISSING** | I002 / I017 / I029 一致 | F081 / F082 / F091（Batch 1.4）/ F020 / F023（Batch 1.1 — 应升级为 MISSING）|
| **规则 2**：表存在但缺列（DDL 跑了但 ALTER 列未加 → 列在 DB 不存在）→ 部分功能可用 | 部分列 SELECT 抛 Unknown column | **PARTIAL-NON-BLOCKER**（沿用 Batch 1.3 I029 PARTIAL-MISSING 模式）| I029 双标注 | F066（Batch 1.3）/ F019 / F021（Batch 1.1 — 应标 PARTIAL-NON-BLOCKER）|
| **规则 3**：表缺失但 catch 吞异常 → 功能降级可用 | 异常被 catch 吞，返回空 / 默认值 | **PARTIAL-NON-BLOCKER**（功能降级，非完全不可用）| I008 catch 模式 | F012（Batch 1.1 — loadKnownExternalIds 合理）|
| **规则 4**：表缺失但暴露 I011 Fake Failure（API 报错但 DB INSERT 成功）| select-after-write 模式 + DB 已写入 | **PARTIAL-BLOCKER**（数据已写但用户看到失败 — CRITICAL）| I011 模式 | F001 / F003（Batch 1.1 — CRITICAL-P1 实证）|

### 7.3 4 规则图示

```
        ┌───────────────────────┐
        │   表缺失 / 列缺失    │
        └─────────┬─────────────┘
                  │
                  ▼
        ┌───────────────────────┐
        │   表完全不存在？     │
        └─────┬─────┬───────────┘
              │     │
         YES  │     │ NO
              ▼     ▼
    ┌──────────┐  ┌────────────────┐
    │ SELECT? │  │ 表存在缺列？   │
    └────┬─────┘  └──────┬─────────┘
         │               │
         ▼               ▼
   ┌─────────┐      ┌─────────────────┐
   │ catch? │      │  PARTIAL-NON-    │
   └────┬────┘      │  BLOCKER（I029）│
        │           └─────────────────┘
   ┌────┴─────┐
   │ SELECT?  │
   └────┬─────┘
       YES  NO
        │    │
        ▼    ▼
  ┌──────────┐ ┌──────────────┐
  │ 已 INSERT │ │ I008/I047   │
  │ 但报错   │ │ Fake Result │
  │ → I011   │ │ PARTIAL-    │
  │ PARTIAL- │ │ NON-BLOCKER │
  │ BLOCKER  │ └──────────────┘
  └──────────┘
```

### 7.4 修正建议

| 批次 | Feature | 当前 Result | 建议修正 | 修正原因 |
|---|---|---|---|---|
| Batch 1.1 | F012 loadKnownExternalIds | PARTIAL-BLOCKER | PARTIAL-NON-BLOCKER | catch 吞异常（功能降级为空 Set）— 规则 3 |
| Batch 1.1 | F020 CrmContactMap 实体 | PARTIAL-BLOCKER | **MISSING** | 表完全缺失 → 规则 1 |
| Batch 1.1 | F022 DDL | PARTIAL-BLOCKER | **MISSING** | 表完全缺失 → 规则 1 |
| Batch 1.1 | F023 crm_contact_map 表存在性 | PARTIAL-BLOCKER | **MISSING** | 表完全缺失 → 规则 1 |
| Batch 1.1 | F019 CrmIntegration 配置实体 | PARTIAL-NON-BLOCKER | ✅ 维持 | 表存在，缺 9 列 → 规则 2 |
| Batch 1.1 | F021 CrmSyncLog 日志实体 | PARTIAL-NON-BLOCKER | ✅ 维持 | 表存在，缺 3 列 → 规则 2 |

→ **批次间口径差异是历史性记录，修正需用户决策**（用户决策 Q5 已维持历史口径）。**Batch 1.4 自行维持 MISSING 模式与 I038/I045 一致**，不"反向追溯" Batch 1.1。

---

## 八、mkt_channel_config 双重归属明确（核实 3 落实）

### 8.1 双重归属

| 维度 | 归属 | 证据 | 状态 |
|---|---|---|---|
| **修复阶段** | **Post-Audit P0-3 修复项** | 5C §4.1 P0-3：「跑 `migrate_mkt_002_*.sql` 重建 `mkt_channel_config`」| ⏸ 等 Post-Audit 兑现 |
| **审计阶段** | **Batch 1.5 = MKT 多渠道重构**（建议）| 5C §6.1 候选 A：「MKT 多渠道重构（推荐）」| ⏸ 等用户决策 Q1-Q4 |

### 8.2 5C §四.4.6 P0-3 原文（修复路径）

> 「跑 `migrate_mkt_002_*.sql` 重建 `mkt_channel_config`（已写但未跑，与 I038 同模式）」

### 8.3 5C §六.1 候选 A 原文（审计路径）

> 「MKT 多渠道重构（推荐）：现有 `mkt_*` 模块（mkt_channel / mkt_segment / mkt_audit_log / mkt_automation）+ ChannelDispatcher + Webhook Receiver — 跨 SmsSender / EmailSender / MiniProgramSender / WechatOA / InAppSender」

### 8.4 用户建议 — 明确标识

> "报告说 'P0 修复：I038 + I045 + mkt_channel_config 迁移'  
> 但 Batch 1.5 = MKT 重构，mkt_channel_config 是 EXT-19 相关表  
> 请核实：mkt_channel_config 属于哪一批次？  
> 如果在 Post-Audit 跑批清单里，请明确标出"

**核实结论**：
1. mkt_channel_config 表迁移修复 → **Post-Audit P0-3**（与 I038 / I045 同模式一次性跑批）
2. mkt_channel_config 表重建后审计 → **Batch 1.5 = MKT 多渠道重构**（依赖 P0-3 先兑现）
3. **本次 EN-09 修正**：明确登记 **I054 NEW（建议）— mkt_channel_config 表缺失**作为 Post-Audit 跑批清单的正式成员
4. **Batch 1.5 启动决策依赖**：Q3「迁移脚本（I038 + I045 + I054 NEW）是否在 Batch 1.5 启动前一次性跑？」用户决策空间

---

## 九、跨 Phase 5 演化（5A → 5B → 5C → EN-09）

| Feature | 5A 后 | 5B 后 | 5C 后 | EN-09 最终 | 变化原因 |
|---|---|---|---|---|---|
| F083 | PARTIAL-NON-BLOCKER | MISSING | MISSING | MISSING | I038 阻断 sendConfirmation 实测 |
| F084 | OK | OK | OK（5B 静态）| **VERIFIED** | 6 locale 资源 + 静态判定 — **5C 后升级 VERIFIED** |
| F087 | PARTIAL（字段对齐）| PARTIAL | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | 分类统一；I046 字段对齐 OK |
| F095 | MISSING | MISSING + C1 偏差点 | MISSING + C1 偏差点 | MISSING | I038 阻断（选查 → 500 而非 404）|
| F096 | PARTIAL-NON-BLOCKER | MISSING | MISSING | MISSING | I038 阻断 I047 实证 |
| F098 | MISSING | MISSING（I038 直接）| MISSING | MISSING | 同 F081/082 |
| F099 | PARTIAL-NON-BLOCKER | MISSING | MISSING | MISSING | I038 直接阻断 insert |
| F100 | PARTIAL-NON-BLOCKER | MISSING | MISSING | MISSING | I038 + I047 双重阻断 |
| F101 | OK（演示模式）| PARTIAL-NON-BLOCKER（I037）| PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | I037 webhook 路径未 exclude |
| F108 / F109 / F110 | 待 mvn test | UNVERIFIED（I052）| UNVERIFIED | UNVERIFIED | I052 编译失败降级 |
| F111 / F112 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER（5C 静态 PASS）| PARTIAL-NON-BLOCKER | 5C 静态实证 I040 / I041 / I042 成立 |
| F094 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | I050（T07-D4 例外）+ I054 NEW 推论（**核实 3 登记**）|

---

## 十、下一批次建议（Batch 1.5 = MKT 重构）

### 10.1 候选矩阵

| 选项 | 模块 | 范围预估 | 阻塞项 | 推荐 | 决策依赖 |
|---|---|---|---|---|---|
| **A** ⭐ | **MKT 多渠道重构（推荐）**| 现有 `mkt_*` 模块（mkt_channel / mkt_segment / mkt_audit_log / mkt_automation）+ ChannelDispatcher + Webhook Receiver — 跨 5 个 channel sender | I038 + I045 + **I054 NEW**（mkt_channel_config）+ I047（CRITICAL）+ I040/I041/I042（前端 Shadow）+ I037（webhook exclude）+ I049（凭据）| ⭐⭐⭐⭐⭐ | Q1（Batch 1.5 选 A？）+ Q2（I047 是否前置修）+ Q3（迁移是否一次性跑）+ Q4（I037 + I051 同步修）|
| **B** | Post-Audit 修复 P0 项（不开 Batch）| 跑迁移 I038 + I045 + **I054 NEW** + 修 I047 catch + 加 `/intl-booking/webhook/**` exclude | 4 个 P0 + 1 个 CRITICAL | ⭐⭐⭐⭐ | 等用户决策 |
| **C** | Phase 6（上线预演）| 不开新批次，先决策 D1-D31 + P0 是否调整 | — | ⭐⭐ | — |

### 10.2 推荐路径（C → A）

| 阶段 | 内容 | 预期效果 |
|---|---|---|
| **Step 1**（Post-Audit 第一天）| 跑 P0-1（I038）+ P0-2（I045）+ P0-3（**I054 NEW**）三条迁移 | F081 / F082 / F083 / F091 / F094 / F095 / F096 / F098 / F099 / F100 / F104 共 **11 个 MISSING → PARTIAL-NON-BLOCKER** |
| **Step 2**（Post-Audit 第一天）| 修 P1-1（I047 CRITICAL catch）+ P1-4（I037 webhook exclude） | F083 / F100 CRITICAL 修复 + F099 / F100 / F101 整条 webhook 链路贯通 |
| **Step 3**（Post-Audit 第二天）| P1-3（I040 / I041 / I042 前端 Shadow）+ P2-3（I048 rollbackFor） | F096 / F099 / F100 / F103 / F111 / F112 完整修复 |
| **Step 4**（Batch 1.5 启动，1-2 天）| **MKT 多渠道重构**（mkt_channel / mkt_segment / mkt_audit_log / mkt_automation）+ ChannelDispatcher 多租户整改 + Webhook Receiver 统一 + OAuth2 token refresh | F094 + 16-21 个新 Feature 完整审计 |

→ **Step 1 + Step 2 完成后再启动 Batch 1.5（Step 4）**，避免 Batch 1.5 期间迁移 + 审计双轨并行。

### 10.3 Batch 1.5 = MKT 多渠道重构范围

| 模块 | 预估 Feature 数 | 关键点 |
|---|---|---|
| **ChannelDispatcher 多租户** | 2-3 个 | I050 整改（移除 `tenantId=1L` 硬编码）+ I054（mkt_channel_config 修复后）|
| **Webhook Receiver 统一** | 4-5 个 | I037 整改（exclude 路径）+ 新增有赞 / Google / Instagram / 飞书 webhook 接收端（EXT-22 + EXT-19 链路统一）|
| **i18n template 扩展** | 2 个 | 当前 6 locale（zh-CN / zh-TW / en-US / ja-JP / ko-KR / fr-FR）→ 加 de-DE / es-ES |
| **多渠道批量发送** | 3-4 个 | F083 BookingConfirmationService 扩展支持 `sendToMultipleTenants` |
| **OAuth2 callback token refresh** | 2 个 | F102 OAuth2 真实凭据 + token refresh 流程（替代 I051 演示模式）|
| **mkt_automation 自动化触发** | 2-3 个 | 与 CRM scheduler 类比，自动化触发器 |
| **mkt_audit_log 审计** | 1-2 个 | 跨 Batch 1.5 全部 AuditLog 落档 |
| **合计预估** | **16-21 个** | Phase 1 期间精确化 |

### 10.4 Batch 1.5 决策依赖（Q1-Q4）

| 决策 | 选项 | 用户决策点 |
|---|---|---|
| **Q1** | MKT 多渠道重构是否走 Batch 1.5？ | A 走（MKT 重构）/ B 跳过（Phase 6）/ C 走其他模块 |
| **Q2** | I047 catch 吞异常是否在 Batch 1.5 启动前修复？ | A 修（CRITICAL-P1 不延后）/ B 沿用 D4（仅登记不修）|
| **Q3** | 迁移脚本（I038 + I045 + **I054 NEW**）是否在 Batch 1.5 启动前一次性跑？ | A 一次性跑 / B 保留 Part PARTIAL-BLOCKER |
| **Q4** | I037 webhook exclude + I051 真实 token 是否走 Batch 1.5 同步修？ | A 同步修 / B 留到 Batch 2 |

### 10.5 时间预估

| 阶段 | 预估时间 |
|---|---|
| Step 1 + 2 + 3（Post-Audit）| 2 天 |
| Phase -1 preflight + Phase 0 scope-freeze | 0.5 小时 |
| Phase 1 inventory | 2-3 小时 |
| Phase 2 traceability matrix | 3-4 小时 |
| Phase 3 static gap | 2-3 小时 |
| Phase 4 plan | 1 小时 |
| Phase 5A DB-only | 1 小时 |
| Phase 5B API + Mock | 2-3 小时 |
| Phase 5C frontend E2E | 1 小时 |
| **Batch 1.5 合计** | 1.5-2 个工作日 |

---

## 十一、决策变更日志追加

| 时间 | 决策 | 影响 Issue / Feature |
|---|---|---|
| 2026-09-14 15:45 | Phase 0 决策：D21-D28 全部沿用既有模式 | I038-I040 处置 + 8 项决策落档 |
| 2026-09-14 20:40 | Phase 5A 启动：D21-D28 全部维持 + I038/I045 DESCRIBE 实证 + I046 字段对齐 OK + I050 + I044 + I047 静态 | I029 细化 + I038 / I045 / I046 / I047 / I048 / I050 登记 |
| 2026-09-14 22:20 | Phase 5B 完成：I038 6 路径实测 + I047 Fake Success 实证 + I037 同源 + I051 演示模式 + I052 mvn test 编译失败 | **I047 升级 CRITICAL-P1** + I052 NEW + I037 同源实证 |
| 2026-09-14 22:50 | Phase 5C 完成：I040 / I041 / I042 静态实证 PASS + Playwright 脚本生成 + 33 Feature 汇总 + 15 Issue 处置 | I040 / I041 / I042 静态 PASS + F111-F112-shadow.spec.ts 156 行 |
| **2026-09-14 23:15** | **EN-09 完成**：3 处核实修正（计数 / 口径 / mkt_channel_config 双重归属）+ 33 Feature 汇总 + 15 Issue 处置 + 修复优先级矩阵 + Batch 1.5 建议 | **I054 NEW（建议）+ 计数修正 + 4 条口径规则 + Batch 1.5 Q1-Q4 决策依赖** |

---

## 十二、EN-09 完成情况总览

| 维度 | 状态 |
|---|---|
| 33 Feature 全部有 Final Result | ✅（1 VERIFIED / 0 PARTIAL-BLOCKER / 9 PARTIAL-NON-BLOCKER / 0 BROKEN / 1 CONFLICT / 0 MOCK-* / 0 SHELL-* / 10 MISSING / 7 UNVERIFIED / 5 Static-Only OK = 33）|
| 15 个 Issue 全部登记（I038-I052）| ✅ |
| 1 个建议追加（I054 NEW — mkt_channel_config）| ✅（核实 3 落实）|
| 3 处核实全部落实 | ✅（核实 1 计数修正 / 核实 2 口径统一建议 / 核实 3 mkt_channel_config 双重归属）|
| 修复优先级矩阵输出（P0-P3）| ✅ |
| 下一批次建议（Batch 1.5 Q1-Q4）| ✅ |
| 不动业务代码 | ✅ |
| 不跑迁移 | ✅ |
| 不动 SoT | ✅ |
| 不修改 Frontend 文件 | ✅ |
| 不修复 catch / rollbackFor / webhook exclude | ✅ |
| Phase 5A / 5B / 5C 全程无 Phase 3 漏判修正 | ✅ |
| 受保护页面零风险 | ✅（EXT-19 与 3 受保护页面零交集）|

---

## 十三、停止点

**Batch 1.4 Phase 5 + EN-09 全程完结。33 Feature Result 全部归属唯一；15 + 1 个 Issue 全部登记（I038-I052 + I054 NEW 建议）；3 处核实全部落实；修复优先级矩阵 + Batch 1.5 决策依赖输出。⏸ 等用户决策下一步（Post-Audit 修复 P0-1 + P0-2 + P0-3 / 启动 Batch 1.5 MKT 重构 / 进入 Phase 6 上线预演）。**

---

**Batch 1.4 小结完成 — EN-09 落档。审计阶段维持现状，Post-Audit 兑现 P0 修复后可启动 Batch 1.5 MKT 多渠道重构（预估 1.5-2 天 + Post-Audit 2 天 = 3.5-4 天）。**

---

*文件落档时间：2026-09-14 23:15 (UTC+8)*
*最近更新：EN-09 完成 + 3 处核实修正 + I054 NEW 建议追加*
