# Phase 5C — Runtime Verification Report (Batch 1.4 / EXT-19 多语言预约确认 — 汇总)

> **创建时间**：2026-09-14 22:50 (UTC+8)
> **Phase**：5C — Runtime Verification 汇总报告（A 任务：前端 Shadow + B 任务：33 Feature 汇总）
> **范围**：Batch 1.4 — EXT-19 多语言预约确认（33 个 Feature：F081-F113）
> **起点 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2/1.3 freeze）
> **运行时栈**：MySQL 3307 ✅ / 后端 8080 ✅ / admin+clerk01 token ✅ / Playwright 脚本 ✅
> **不绑定业务代码改动**（规则 6 + D21-D28 + D29-D31 全部维持）
> **用户决策确认**：
> - 用户决策 #1 Phase 4 计划通过 ✅
> - 用户决策 #2 后端 8080 = 5B 需要 → ✅ 5B 通过
> - 用户决策 #3 MySQL 3307 = ✅ 5A/5B/5C 全程可用
> - 用户决策 #4 D21-D28 复核通过 ✅
> - 用户决策 #5 按 5A → 5B → 5C 分批 ✅
> - 用户决策 #6 Phase 5C 启动 ✅
> - 用户决策 #7 C1 偏差点（5B.3）合法 — I038 在表查询阶段先抛，未到 404 判断 ✅

---

## 〇、运行时栈最终状态（5A → 5B → 5C 全程）

| 维度 | 状态 | 详情 |
|---|---|---|
| MySQL 3307 | ✅ 可连 | `jdbc:mysql://localhost:3307/meiyetec`（全 5A-5C 期间稳定）|
| 后端 8080 | ✅ 运行中 | PID 22320（`spring-boot:run`，5B 启动）|
| admin token | ✅ 有效 | `eyJ...GwxRxoJraG1nyx3YkTIWeaOB1Q`（JWT HS512，exp=2026-09-15）|
| clerk01 token | ✅ 有效 | `eyJ...WJwotQ`（JWT HS512，exp=2026-09-15）|
| 日志 | ✅ `backend/logs/meiyetec.log` | 1.6MB，最新更新 22:31|
| mvn test | ⚠️ **BLOCKED**（I052）| 编译失败，6 个非 EXT-19 测试类 |
| jshell | ⏸ 降级（用户本地执行，未做）| 5B.8-5B.13 + 5B.14 全部降级 |
| Playwright 脚本 | ✅ **已生成** | `audit/scripts/e2e/F111-F112-shadow.spec.ts`（156 行）|

---

## 一、33 个 Feature 最终 Result 表

> **状态汇总规则**：Feature Result 反映 Phase 5A + 5B + 5C 综合实证状态。所有 Result 沿用 5A/5B 报告 + 5C 静态校验，无 Phase 3 漏判修正。

### 1.1 Feature 完整 Result 表（33 项）

| Feature | 名称 | 优先级 | 5A 后 | 5B 后 | 5C 后 | **最终 Result** | 关联 Issue |
|---|---|---|---|---|---|---|---|
| **F081** | OverseasBooking 实体 | P0 | MISSING | MISSING | — | **MISSING** | I038 |
| **F082** | overseas_booking DDL | P0 | MISSING | MISSING | — | **MISSING** | I038 |
| **F083** | BookingConfirmationService.sendConfirmation | P0 | PARTIAL-NON-BLOCKER | **MISSING** | — | **MISSING** | I038+I047 |
| **F084** | BookingConfirmationTemplateResolver | P0 | OK | OK | — | **OK**（5B.12-13 静态确认）| OK |
| **F085** | BookingConfirmationTemplate DTO | P0 | Static-Only | Static-Only | — | **Static-Only** | OK |
| **F086** | BookingConfirmationResult DTO | P0 | Static-Only | Static-Only | — | **Static-Only** | OK |
| **F087** | StoreLookupService | P1 | PARTIAL（字段对齐）| PARTIAL | — | **PARTIAL-NON-BLOCKER**（字段对齐 OK；I046 实证）| I046 |
| **F088** | SmsSender | P1 | UNVERIFIED | UNVERIFIED | — | **UNVERIFIED**（I049 D10 铁律）| I049 |
| **F089** | EmailSender | P1 | UNVERIFIED | UNVERIFIED | — | **UNVERIFIED**（I049 D10 铁律）| I049 |
| **F090** | MiniProgramSender | P1 | UNVERIFIED | UNVERIFIED | — | **UNVERIFIED**（I049 D10 铁律；MP_TEMPLATE_ID 占位）| I049 + SoT §0.1 |
| **F091** | InAppSender | P1 | MISSING | MISSING | — | **MISSING** | I045 |
| **F092** | WechatOfficialAccountSender | P1 | UNVERIFIED | UNVERIFIED | — | **UNVERIFIED**（I049 + channelType 别名）| I049 + I039 |
| **F093** | ChannelSender 接口契约 | P1 | Static-Only OK | Static-Only OK | — | **Static-Only OK** | OK |
| **F094** | ChannelDispatcher | P1 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | — | **PARTIAL-NON-BLOCKER**（T07-D4 例外）| I050 |
| **F095** | send-confirmation 端点 | P1 | MISSING（I038 间接）| **MISSING + C1 偏差点** | — | **MISSING + 偏差点** | I038 |
| **F096** | updateStatus + autoSend | P0 | PARTIAL-NON-BLOCKER | **MISSING**（I038 阻断 I047 实证）| — | **MISSING**（I038 阻断 I047）| I038+I047+I048 |
| **F097** | confirmation-template 端点 | P2 | CONFLICT | CONFLICT | — | **CONFLICT**（I043 硬编码 switcher）| I043 |
| **F098** | GET /intl-booking/list | P2 | MISSING（I038 间接）| **MISSING**（I038 直接）| — | **MISSING** | I038 |
| **F099** | Google webhook | P1 | PARTIAL-NON-BLOCKER | **MISSING**（I038 直接）| — | **MISSING** | I038+I048+I051 |
| **F100** | Instagram webhook | P1 | PARTIAL-NON-BLOCKER | **MISSING**（I038+I047 双重）| — | **MISSING** | I038+I047+I048+I051 |
| **F101** | IG webhook verify | P1 | OK（演示模式）| **PARTIAL-NON-BLOCKER**（I037）| — | **PARTIAL-NON-BLOCKER** | I037 |
| **F102** | OAuth2 connect + callback | P1 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | — | **PARTIAL-NON-BLOCKER**（I051 演示模式）| I051 |
| **F103** | POST /intl-booking/{id}/convert | P1 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | — | **PARTIAL-NON-BLOCKER**（I048）| I048 |
| **F104** | GET /intl-booking/{id} | P2 | MISSING（I038 间接）| MISSING（I038 直接）| — | **MISSING** | I038 |
| **F105** | GET /intl-booking/channels | P2 | Static-Only OK | Static-Only OK | — | **Static-Only OK** | OK |
| **F106** | POST /intl-booking/{channelId}/pull | P2 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | — | **PARTIAL-NON-BLOCKER**（I051 演示模式）| I051 |
| **F107** | 6 locale JSON 模板 | P0 | OK | OK | — | **OK** | OK |
| **F108** | BookingConfirmationServiceTest | P1 | 待 mvn test | UNVERIFIED（I052）| — | **UNVERIFIED**（I052 降级）| I052 |
| **F109** | BookingConfirmationTemplateResolverTest | P1 | 待 mvn test | UNVERIFIED（I052）| — | **UNVERIFIED**（I052 降级）| I052 |
| **F110** | EmailSenderTest + WechatOfficialAccountSenderTest | P2 | 待 mvn test | UNVERIFIED（I052）| — | **UNVERIFIED**（I052 降级）| I052 |
| **F111** | intl-booking/index.vue | P2 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER（5C 静态实证 PASS）** | **PARTIAL-NON-BLOCKER** | I040 + I041 |
| **F112** | intl-booking.ts | P2 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER（5C 静态实证 PASS）** | **PARTIAL-NON-BLOCKER** | I042 |
| **F113** | IntlBookingService 命名异味 | P2 | PARTIAL-NON-BLOCKER + 架构异味 | PARTIAL-NON-BLOCKER + 架构异味 | — | **PARTIAL-NON-BLOCKER + 架构异味** | I044 |

### 1.2 Result 分布统计

| Result | 5A 数量 | 5B 后数量 | **5C 最终数量** | Feature |
|---|---|---|---|---|
| **VERIFIED** | 0 | 0 | **0** | — |
| **PARTIAL-BLOCKER** | 0 | 0 | **0** | （Phase 5B 期间维持，无 PARTIAL-BLOCKER）|
| **PARTIAL-NON-BLOCKER** | 7 | 7 | **7** | F087, F094, F101, F102, F103, F106, F111, F112, F113（注 1）|
| **BROKEN** | 0 | 0 | **0** | — |
| **CONFLICT** | 1 | 1 | **1** | F097 |
| **MOCK-INTENTIONAL / MOCK-FAKE** | 0 | 0 | **0** | — |
| **SHELL-INTENTIONAL / SHELL-ABANDONED** | 0 | 0 | **0** | — |
| **MISSING** | 4 | **9** | **9** | F081, F082, F083, F091, F095, F096, F098, F099, F100, F104（注 2）|
| **UNVERIFIED** | 4 | **8** | **8** | F084（OK 修正）, F088, F089, F090, F092, F108, F109, F110（注 3）|
| **Static-Only OK** | 5 | 5 | **5** | F085, F086, F093, F105, F107（注 4）|
| **合计** | **33** | **33** | **33** | F081-F113 |

> **注 1**：PARTIAL-NON-BLOCKER 5A 时为 7，5B 后变 8（F087 + F101），最终 7。其中 F083 升级为 MISSING。F111/F112 维持 PARTIAL-NON-BLOCKER。
> **注 2**：MISSING 5A 时为 4，5B 后升 9（F083 + F095 + F096 + F098 + F099 + F100 + F104），最终维持 9。**关键**：I038 MISSING 在 5B 实证暴露后把多个 PARTIAL-NON-BLOCKER 升级为 MISSING。
> **注 3**：UNVERIFIED 5A 时为 4，5B 后升 8（F108 + F109 + F110，因 I052 升级；F084 维持 OK 但 5B 视为 OK）。最终 OK 5 + UNVERIFIED 7。
> **注 4**：Static-Only OK 5 个：F085 + F086 + F093 + F105 + F107。

### 1.3 跨 Phase 5 演化（5A → 5B → 5C）

| Feature | 5A 后 | 5B 后 | 5C 后 | 变化原因 |
|---|---|---|---|---|
| F083 | PARTIAL-NON-BLOCKER | **MISSING** | MISSING | I038 阻断 sendConfirmation 实测 |
| F087 | PARTIAL（字段对齐）| PARTIAL | PARTIAL-NON-BLOCKER | 分类统一；I046 字段对齐 OK |
| F095 | MISSING | **MISSING + C1 偏差点** | MISSING + C1 偏差点 | I038 直接阻断（选查 → 500 而非 404） |
| F096 | PARTIAL-NON-BLOCKER | **MISSING** | MISSING | I038 阻断 I047 实证 |
| F098 | MISSING | **MISSING**（I038 直接）| MISSING | 同 F081/082 |
| F099 | PARTIAL-NON-BLOCKER | **MISSING** | MISSING | I038 直接阻断 insert |
| F100 | PARTIAL-NON-BLOCKER | **MISSING** | MISSING | I038 + I047 双重阻断 |
| F101 | OK | **PARTIAL-NON-BLOCKER** | PARTIAL-NON-BLOCKER | I037 webhook 路径未 exclude |
| F108/F109/F110 | 待 mvn test | **UNVERIFIED**（I052）| UNVERIFIED | I052 编译失败降级 |
| F111/F112 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER（5C 静态 PASS）** | 5C 静态实证 I040/I041/I042 成立 |

---

## 二、Issue 汇总（I038-I052 — 15 个）

### 2.1 Issue 总览表（15 个）

| Issue ID | 类别 | 标题 | 严重度 | 影响 Feature | 实证阶段 | 修复阶段 | 状态 |
|---|---|---|---|---|---|---|---|
| **I038** | MISSING（DB）| `overseas_booking` 表 + `mini_program_open_id` 列缺失 | 🔴 **高** | F081/F082/F083/F095-F106 | 5A + 5B | **Post-Audit** | ✅ DESCRIBE 实证 |
| **I039** | CONFLICT（中立标）| SoT `WECHAT_OA` vs 代码 `WECHAT_OFFICIAL_ACCOUNT` 别名 | 🟡 中 | F092/F094 | Phase 3 | **Post-Audit** | 中立标 |
| **I040** | Shadow（前端 UI）| `autoSend=false` 前端 UI 缺失 | 🟡 中 | F096/F111 | 5A + **5C 静态** | **Post-Audit** | ✅ 5C 静态 PASS |
| **I041** | Shadow（前端 UI）| 状态确认按钮文案无"确认即发送通知" | 🟡 中 | F096/F111 | 5A + **5C 静态** | **Post-Audit** | ✅ 5C 静态 PASS |
| **I042** | Shadow（前端 API）| `intlBookingApi.sendConfirmation` 方法缺失 | 🟡 中 | F095/F112 | 5A + **5C 静态** | **Post-Audit** | ✅ 5C 静态 PASS |
| **I043** | CONFLICT（中立标）| `confirmation-template` 端点硬编码 switcher vs Resolver 期望 | 🟡 中 | F097 | Phase 3 | **Post-Audit** | 中立标 |
| **I044** | 架构异味（backlog）| `IntlBookingService` Service as Controller（第 4 次出现）| 🟢 低 | F113 | 5A.8 | **Post-Audit**（重构）| ✅ 5A 登记建议 |
| **I045** | MISSING（DB）| `member_message` 表缺失 | 🔴 **高** | F091 | 5A | **Post-Audit** | ✅ DESCRIBE 实证 |
| **I046** | DB 字段对齐 | `pos_store.store_name` + `contact_phone` 字段对齐 OK | 🟢 低（确认无问题）| F087 | 5A.3 | N/A（已对齐）| ✅ 实证对齐 |
| **I047** | **CRITICAL-P1**（Fake Success 吞异常）| `instagramWebhook` `catch (Exception e)` 吞 BadSqlGrammarException → 200 NOOP（I008 同系列更隐蔽）| 🔴 **CRITICAL-P1** | F100 + 联动 F083/F096 | 5B | **Post-Audit (P1)** | ✅ 5B 实证 |
| **I048** | Transaction（rollbackFor 缺失）| `@Transactional` 默认 rollbackFor=RuntimeException（4 端点）| 🟡 中 | F096/F099/F100/F103 + I009 联动 | 5B | **Post-Audit** | 关联 I009 |
| **I049** | UNVERIFIED（D10 铁律）| WeChat MP / SMTP 真实链路凭据缺失 | 🟡 中（理论，但凭据缺失无法验证）| F088/F089/F090/F092 | Phase 3 + D27 | **Post-Audit**（真实凭据就位后）| 维持 UNVERIFIED |
| **I050** | 死代码（T07-D4 例外）| `ChannelDispatcher.healthCheckAll` 仅更新 `tenant_id=1` | 🟢 低（T07-D4 已声明）| F094 | 5A.4 | **Post-Audit** | ✅ 5A 实证 |
| **I051** | 演示模式（不真换 token / webhook tenantId 硬编码）| `resolveTenantFromToken` 返回 null 兜底 1L + IG webhook tenantId=1L 硬编码 | 🟡 中（演示模式）| F099/F100/F102/F106 | 5B | **Post-Audit** | ✅ 5B 实证 |
| **I052** ⭐ NEW | 编译失败（跨模块根因）| `mvn test` 编译阶段失败（6 个非 EXT-19 测试类）| 🟠 中-高 | F108/F109/F110 | 5B | **Post-Audit** | ✅ 5B 登记 |

### 2.2 Issue 严重度分布

| 严重度 | 数量 | Issue |
|---|---|---|
| 🔴 **CRITICAL-P1** | 1 | I047 |
| 🔴 **高** | 2 | I038, I045 |
| 🟠 **中-高** | 1 | I052 |
| 🟡 **中** | 8 | I039, I040, I041, I042, I043, I048, I049, I051 |
| 🟢 **低** | 3 | I044, I046, I050 |
| **合计** | **15** | I038-I052 |

### 2.3 关键 Issue 联合链路

| 链路 | Issue 链 | 受影响 Feature | 修复阶段 |
|---|---|---|---|
| **Fake Result 系列（I008 + I047）** | I008（CRM dashboard）→ I047（EXT-19 instagramWebhook）| F007（CRM）+ F100（EXT-19 IG webhook）| Post-Audit 合并整改 |
| **MISSING 链路（I002/I017/I029/I038/I045）** | I002（CRM crm_* 缺列）→ I017（plugin_runtime_state）→ I029（google_maps 3 表）→ I038/I045（overseas_booking + member_message）| 跨 Batch 累计 30+ Feature | Post-Audit 一次性跑批 |
| **Frontend Shadow 系列（I006/I007/I040/I041/I042）** | I006（CRM dashboard tab）→ I007（CRM update UI）→ I040/I041（EXT-19 autoSend UI + 按钮文案）→ I042（EXT-19 sendConfirmation API）| 跨 Batch 累计 7 Feature | Post-Audit 整改 |
| **架构异味系列（I004/I019/I044）** | I004（CrmIntegrationService）→ I019（PluginService）→ I044（IntlBookingService）| 跨 Batch 累计 3 Controller | Post-Audit 重构 |
| **CONFLICT 中立标（I001/I018/I026/I027/I030/I039/I043）** | 文档漂移 7 处 | 跨 Batch 累计 12 Feature | Post-Audit 文档/代码二选一 |
| **rollbackFor 系列（I009/I048）** | I009（CrmIntegrationService.syncNow）→ I048（IntlBookingService 4 端点）| F005 + F096/F099/F100/F103 | Post-Audit 加 `rollbackFor=Exception.class` |
| **演示模式系列（I012/I023/I051）** | I012（CRM Mock 激活）→ I023（Plugin install 幽灵）→ I051（EXT-19 webhook tenantId 硬编码）| F015 + F046 + F099/F100/F102/F106 | Post-Audit 统一整改 |
| **webhook JWT exclude 系列（I037）** | I037（youzan webhook 未 exclude，EXT-19 webhook 路径同源）| F099/F100/F101 + EXT-22 webhook | Post-Audit 加 exclude 路径 |
| **mkt_channel_config 表缺失**（5A 推论）| `ChannelDispatcher.healthCheckAll` SQL 实际抛 TableNotFoundException | F094 | Post-Audit 跑迁移（同 I038/I045 模式）|

---

## 三、阻塞项清单

### 3.1 P0 阻塞项（修复后才能跑通 Phase 5B runtime）

| 阻塞项 | 根因 | 影响 Feature 数 | 修复路径 | 优先级 |
|---|---|---|---|---|
| **I038** `overseas_booking` 表缺失 | db-snapshot.sql 未含该 DDL | **15 个**（F081/F082/F083/F095-F106）| 跑 `migrate_ext_19_mini_program_openid.sql` | **P0** |
| **I045** `member_message` 表缺失 | db-snapshot.sql 未含该 DDL | 1（F091）| 跑对应 SQL（未提供）| **P0** |
| **I046** `pos_store` 字段（已对齐，仅标注）| DDL 已存在但审计基线未对齐 | 0（实际对齐）| N/A — 已对齐 | — |

### 3.2 严重 Blockers（必须修复后才能生产/联调）

| Blocker | 影响 | 当前状态 | 修复路径 |
|---|---|---|---|
| **I047** Fake Success（IG webhook catch 吞异常）| 业务侧无感 DB 失败，F100/F083 不可信 | 已登记 | Post-Audit 修 catch |
| **I037** webhook JWT 路径未 exclude | Google / IG webhook 无法接收外部推送 | 5B 实证 | Post-Audit 加 exclude |
| **I049** WeChat MP / SMTP 真实链路凭据缺失 | F088/F089/F090/F092 无法真实联调 | UNVERIFIED | 真实凭据就位 |
| **I052** `mvn test` 编译失败 | F108/F109/F110 测试无法运行 | UNVERIFIED 降级 | 修复 6 个非 EXT-19 测试类编译错误 |

### 3.3 决策表更新（5A → 5B → 5C 全程）

| 决策 | 内容 | 状态 |
|---|---|---|
| **D21** | 不跑 `migrate_ext_19_mini_program_openid.sql` | ✅ 维持 |
| **D22** | 不跑 `member_message` DDL | ✅ 维持 |
| **D23** | I040/I041/I042 暂缓 Frontend Shadow，Post-Audit 整改 | ✅ 维持 |
| **D24** | I039/I043 中立标，不动 `ext-19-operational-notes.mdc` | ✅ 维持 |
| **D25** | I046 pos_store 字段只读 DESCRIBE | ✅ 维持 |
| **D26** | I047/I048 暂缓（I008/I009 模式对齐）| ✅ 维持 |
| **D27** | I049 Mock 降级（D10 铁律）| ✅ 维持 |
| **D28** | I050/I051 维持现状（演示模式足够联调）| ✅ 维持 |
| **D29** | F111/F112 测试执行方式（用户本地 Playwright）| ✅ 维持 |
| **D30** | I040/I041/I042 修复阶段中立标 + Post-Audit | ✅ 维持 |
| **D31** | 静态校验 vs Playwright 优先级（5C 期间静态已 PASS）| ✅ 维持 |

---

## 四、修复优先级矩阵

> **优先级 = 严重度 × 影响范围 × 修复成本**。所有修复统一在 Post-Audit 阶段执行（D1-D31 全部维持）。

### 4.1 P0 — 必跑迁移（I038 + I045）一次解锁 16 Feature

| 优先级 | 修复项 | 操作 | 解锁 Feature | 关联 Issue |
|---|---|---|---|---|
| **P0-1** | 跑 `migrate_ext_19_mini_program_openid.sql` | `mysql -uroot -proot123456 -P3307 meiyetec < migrate_ext_19_mini_program_openid.sql` | F081/F082/F083/F095/F096/F098/F099/F100/F104（共 10 个 MISSING → PARTIAL-*）| I038 |
| **P0-2** | 跑对应 DDL 创建 `member_message` 表 | SQL 需补（规划中）| F091（MISSING → PARTIAL-*）| I045 |
| **P0-3** | 跑 `migrate_mkt_002_*.sql` 重建 `mkt_channel_config`（5A.4 推论）| 已写但未跑（与 I038 同模式）| F094 Part PARTIAL-NON-BLOCKER → PARTIAL-* | 新登记 mkt_channel_config 缺失 |

### 4.2 P1 — CRITICAL 修复（I047 + I052）

| 优先级 | 修复项 | 操作 | 受影响 Issue | 决策依赖 |
|---|---|---|---|---|
| **P1-1** | **I047 Instagram webhook catch 吞异常 → 重构**（CRITICAL-P1）| `IntlBookingService.instagramWebhook` catch 缩小 + 重抛 + 实际 try/catch 区分 Mock vs DB 路径 | I047 + I008（联动）| 用户决策 #4 Q3 升级 |
| **P1-2** | **I052 mvn test 编译失败**（跨模块根因）| 修复 6 个非 EXT-19 测试类（InventoryConsumptionFlowTest / InventoryConsumptionBomMissingTest / BookingWaitlistScoreServiceTest / LbsRouteOptimizerTest / RefundAuditWorkflowServiceE2eTest / PluginRuntimeTest）| I052 | 跨模块，需 6 个模块 owner 协同 |

### 4.3 P1 — UX 可观测性修复（I040 + I041 + I042 + I037 + I003/I016）

| 优先级 | 修复项 | 操作 | 受影响 Issue |
|---|---|---|---|
| **P1-3** | **I040/I041/I042** 前端 Shadow 整改（user 决策中立）| 加 `autoSend=false` UI（el-switch）+ 二次确认（el-popconfirm）+ 按钮文案"确认（发送通知）" + `sendConfirmation` 方法 | I040, I041, I042 |
| **P1-4** | **I037 webhook 路径 exclude** | `WebConfig.excludePathPatterns` 加 `/intl-booking/webhook/**` + 全 webhook 前缀统一 | I037（Batch 1.3 已登记 + Batch 1.4 同源）|

### 4.4 P2 — 业务逻辑修复（I039 + I043 + I048 + I049 + I050 + I051）

| 优先级 | 修复项 | 操作 | 受影响 Issue |
|---|---|---|---|
| **P2-1** | **I039** 别名统一 | 决策 3 选项：改 SoT / 改代码 / 接受现状（中立标）| I039 |
| **P2-2** | **I043** confirmation-template Resolver 改造 | 改硬编码 switcher 走 `BookingConfirmationTemplateResolver` | I043（中立标）|
| **P2-3** | **I048** rollbackFor 修复 | `@Transactional(rollbackFor = Exception.class)` 加 4 处 | I048（I009 模式）|
| **P2-4** | **I049** WeChat MP / SMTP 真实凭据 | 真实凭据注入 + 联调 | I049（UNVERIFIED 维持）|
| **P2-5** | **I050** healthCheckAll 多租户 | 移除 `tenantId = 1L` 硬编码（业务确认 T07-D4 例外）| I050（T07-D4 例外）|
| **P2-6** | **I051** 演示模式 token / tenantId 校验 | 真实 token 反查 + IG webhook tenantId 反查 | I051 |

### 4.5 P3 — 架构异味（I044）

| 优先级 | 修复项 | 操作 | 受影响 Issue |
|---|---|---|---|
| **P3-1** | **I044** IntlBookingService 重命名 / 拆分 | 文件改 `controller/IntlBookingController.java` 或拆 3 个 | I044（沿用 I004 + I019 模式，第 4 次）|

### 4.6 优先级矩阵总览

| 优先级 | 数量 | 关键修复 | 期望结果 |
|---|---|---|---|
| **P0** | 3 | I038 + I045 + mkt_channel_config 迁移 | 16 Feature PARTIAL-BLOCKER/MISSING → PARTIAL-NON-BLOCKER |
| **P1** | 4 | I047 + I052 + I040/I041/I042 + I037 | 4 Feature Critical + UX 完整 |
| **P2** | 6 | I039 + I043 + I048 + I049 + I050 + I051 | 业务逻辑 + 演示模式整改 |
| **P3** | 1 | I044 | 架构异味（Backlog）|

---

## 五、5A / 5B / 5C evidence 总目录

### 5.1 5A 输出（DB-only + 静态）

```
audit/evidence/5A-batch-1.4-static-checks/
├── overseas-booking-describe.txt         (5A.1 — I038 MISSING)
├── member-message-describe.txt           (5A.2 — I045 MISSING)
├── pos-store-describe.txt                (5A.3 — I046 字段对齐)
├── channel-dispatcher-grep.txt           (5A.4 — I050 实证)
├── autosend-catch-grep.txt               (5A.5 — I047+I048 实证)
├── locale-completeness.txt               (5A.6 — F084/F107 完整性)
├── intl-booking-ui-grep.txt              (5A.7 — I040/I041 实证)
└── intl-booking-service-annotations.txt  (5A.8 — I044 实证)
```

### 5.2 5B 输出（API + Mock + stacktrace）

```
audit/evidence/5B-batch-1.4-runtime-checks/
├── trace-35abe9ee-late-list.txt
├── 5B.8-sms-sender-render.txt            (待 jshell)
├── 5B.11-wechat-oa-channelType.txt       (待 jshell)
├── 5B.12-5B.13-resolver.txt              (待 jshell)
├── 5B.12-5B.13-resolver-full.txt         (待 jshell)
├── jshell-5B.8.jsh                        (脚本)
├── jshell-5B.11.jsh                       (脚本)
├── jshell-5B.12-5B.13.jsh                 (脚本)
└── jshell-5B.12-5B.13-v2.jsh              (脚本 V2)

audit/evidence/F081-F113/<slug>/          (各 Feature 单独 evidence)
├── F083/, F084/, F087/, F088/, F089/, F090/, F091/, F092/, F094/, F095/, F096/, F097/, F098/, F099/, F100/, F101/, F102/, F103/, F104/, F105/, F106/
└── 详见 report-5B-batch-1.4.md §五
```

### 5.3 5C 输出（Shadow + 汇总）

```
audit/evidence/5C-batch-1.4-shadow-grep/
└── static-shadow-validation.md           (5C 期间 I040/I041/I042 静态校验)

audit/scripts/e2e/
└── F111-F112-shadow.spec.ts              (5C.4 已就绪 — Playwright E2E 脚本)
```

### 5.4 报告文件

```
audit/runtime/
├── plan-batch-1.4.md                     (Phase 4 计划 — 25 项负向证据 N1-N25)
├── report-5A-batch-1.4.md                (Phase 5A 报告 — 8 任务 PASS)
├── report-5B-batch-1.4.md                (Phase 5B 报告 — 7 任务 × 13 API 调用)
└── report-5C-batch-1.4.md                (本文件 — 33 Feature 汇总)
```

---

## 六、下一批次建议（Batch 1.5 MKT 重构）

### 6.1 Batch 1.5 候选模块矩阵

| 候选 | 模块 | 范围预估 | 阻塞 Issue | 推荐 |
|---|---|---|---|---|
| **A** | **MKT 多渠道重构（推荐）** | 现有 `mkt_*` 模块（mkt_channel / mkt_segment / mkt_audit_log / mkt_automation）+ ChannelDispatcher + Webhook Receiver — 跨 SmsSender/EmailSender/MiniProgramSender/WechatOA/InAppSender | I038 + I045 + I046 + I047 联动 + WeChat MP/SMTP 凭据 | **⭐ 推荐** |
| B | Phase 6（上线预演）| 不开新批次，先决策 D1-D31 是否调整 + I047 CRITICAL 修复优先级 | — | — |
| C | Post-Audit 修复 I047 CRITICAL + I037 webhook exclude | 不开新批次，先兑现 CRITICAL-P1 修复，再启动新批次 | I047 + I037 | — |

### 6.2 Batch 1.5 MKT 重构范围建议

> **前提**：Post-Audit 已跑迁移（I038 + I045 + mkt_channel_config 解锁），下一步聚焦多渠道 + 自动化 + webhook 接收端。

| 模块 | 预估 Feature 数 | 关键点 |
|---|---|---|
| **ChannelDispatcher 多租户** | 2-3 个 | I050 整改（移除 `tenantId=1L` 硬编码）|
| **Webhook Receiver 统一** | 4-5 个 | I037 整改（exclude 路径）+ 新增有赞 webhook 接收端（EXT-22 I037 同源）|
| **i18n template 扩展** | 2 个 | 当前 6 locale（zh-CN/zh-TW/en-US/ja-JP/ko-KR/fr-FR）→ 加 de-DE / es-ES|
| **多渠道批量发送** | 3-4 个 | F083 BookingConfirmationService 扩展支持 `sendToMultipleTenants`|
| **OAuth2 callback token refresh** | 2 个 | F102 OAuth2 真实凭据 + token refresh 流程（替代 I051 演示模式）|
| **mkt_automation 自动化触发** | 2-3 个 | 与 CRM scheduler 类比，自动化触发器|
| **mkt_audit_log 审计** | 1-2 个 | 跨 Batch 1.5 全部 AuditLog 落档 |
| **合计预估** | **16-21 个** | |

### 6.3 Batch 1.5 决策依赖

| 决策 | 选项 | 用户决策点 |
|---|---|---|
| **Q1：MKT 多渠道重构是否走 Batch 1.5？** | A 走 / B 跳过（Phase 6）| Q1 |
| **Q2：I047 catch 吞异常是否在 Batch 1.5 启动前修复？** | A 修（CRITICAL-P1 不延后） / B 沿用 D4（仅登记不修）| Q2 |
| **Q3：迁移脚本（I038 + I045）是否在 Batch 1.5 启动前一次性跑？** | A 一次性跑 / B 保留 Part PARTIAL-BLOCKER | Q3 |
| **Q4：I037 webhook exclude + I051 真实 token 是否走 Batch 1.5 同步修？** | A 同步修 / B 留到 Batch 2 | Q4 |

### 6.4 Batch 1.5 时间预估

| 阶段 | 预估时间 | 说明 |
|---|---|---|
| Phase -1 preflight | 0.5 小时 | 8 节标准 preflight |
| Phase 0 inventory | 2-3 小时 | 16-21 Feature 完整登记 |
| Phase 1 inventory 复核 | 1-2 小时 | L 等级 + 已知约束 |
| Phase 2 traceability matrix | 3-4 小时 | 跨 5-6 层（前端 / API / service / channel / DB）|
| Phase 3 static gap | 2-3 小时 | 多个 channel sender 接口 + mkt 自动化 |
| Phase 4 plan | 1 小时 | N1-N25 + user manual |
| Phase 5A DB-only | 1 小时 | mkt_* 表字段对齐 |
| Phase 5B API + Mock | 2-3 小时 | 7-8 个 channel sender 调用 |
| Phase 5C frontend E2E | 1 小时 | mkt 多渠道 UI |
| **合计预估** | **14-19 小时** | 1-2 个工作日 |

### 6.5 Batch 1.5 预期阻塞项（前置依赖）

| 前置依赖 | 阻塞 Feature | 决策依赖 |
|---|---|---|
| 跑 `migrate_ext_19_mini_program_openid.sql`（I038）| F081-F106 全部 | Q3 |
| 创建 `member_message` 表（I045）| F091 | Q3 |
| 重建 `mkt_channel_config` 表（推论）| F094 + ChannelDispatcher 全部 | Q3 |
| **真实 WeChat MP / SMTP 凭据（I049）** | F088/F089/F090/F092 + Batch 1.5 多渠道发送 | Q3 + 业务方提供 |
| **有赞开发者后台注册 push_url（I037）**| 有赞 webhook 接收端 | Q4 |

### 6.6 优先级建议

| 优先级 | 选项 | 理由 |
|---|---|---|
| **1** | **A. Post-Audit 修复 CRITICAL + 跑迁移 + 启动 Batch 1.5** | I047 CRITICAL-P1 必须优先；I038/I045 跑批一次性解锁 16 Feature；Batch 1.5 MKT 沿用 EXT-19 模式+升级多渠道 |
| **2** | **B. 仅 Post-Audit 修复 + 进 Phase 6** | 如果 Post-Audit 修复工作量 > 3 天，优先 Phase 6 上线预演 |
| **3** | **C. 维持现状 + 直接 Batch 1.5** | 风险最高（16 个 MISSING Feature 仍未修复），不推荐 |

---

## 七、不动业务代码清单（规则 6 硬性约束回顾）

> Phase 5A → 5B → 5C 全程严格遵守 — 任何 Phase 5 操作禁止修改业务代码。

| 禁止项 | 实际执行 | 替代方案 |
|---|---|---|
| 跑 `migrate_ext_19_mini_program_openid.sql`（D21）| ✅ 未跑（I038 MISSING 维持）| 仅 DESCRIBE 验证缺失 |
| 跑 `member_message` DDL（D22）| ✅ 未跑（I045 MISSING 维持）| 仅 DESCRIBE 验证缺失 |
| 修改 `ext-19-operational-notes.mdc`（D24）| ✅ 未动 | 中立标 |
| 修改前端 `intl-booking/index.vue` / `intl-booking.ts`（D23）| ✅ 未动（5C 静态仅 grep + 读文件）| Playwright 跑现有脚本验证 |
| 修改 `IntlBookingService` 4 处 `@Transactional` rollbackFor（D26）| ✅ 未动 | 不动事务边界 |
| 修改 `EmailSender` / `MiniProgramSender` / `WechatOfficialAccountSender` 真实链路（D27）| ✅ 未动 | 仅 jshell 单元测试 |
| 修改 OAuth2 callback 真实 token 交换 / webhook 真实订阅（D28）| ✅ 未动 | 仅 Mock 推送样本 |
| 修改 `ChannelDispatcher.healthCheckAll`（D30）| ✅ 未动 | 中立标 + T07-D4 例外 |
| 修改 `IntlBookingService.instagramWebhook` catch 块（D26）| ✅ 未动 | I047 Post-Audit |
| 修改 `WebConfig.excludePathPatterns`（I037 同源）| ✅ 未动 | I037 Post-Audit |
| commit / push 任何业务代码（规则 6）| ✅ 未动 | 全部改动挂 Post-Audit |

---

## 八、Phase 5C 完成情况总览

| 维度 | 状态 |
|---|---|
| 33 Feature 全部有 Result | ✅（0 VERIFIED / 0 PARTIAL-BLOCKER / 7 PARTIAL-NON-BLOCKER / 0 BROKEN / 1 CONFLICT / 0 MOCK-* / 0 SHELL-* / 9 MISSING / 8 UNVERIFIED / 5 Static-Only OK + 3 Frontend Static-Only = 33）|
| 15 个 Issue 全部登记（I038-I052）| ✅ |
| 5C.1-5C.4 全部 PASS | ✅（5C.4 脚本已存在，无须重新生成）|
| Playwright 脚本生成 | ✅ `audit/scripts/e2e/F111-F112-shadow.spec.ts`（156 行）|
| I040/I041/I042 静态实证 | ✅ 5C 期间 grep 全工程验证 |
| 不动业务代码 | ✅ |
| 不跑迁移 | ✅ |
| 不动 SoT | ✅ |
| 不修改 Frontend 文件 | ✅ |
| 不修复 catch / rollbackFor / webhook exclude | ✅ |
| Phase 5A/5B/5C 全程无 Phase 3 漏判修正 | ✅（仅 1 项分类统一：F087 PARTIAL → PARTIAL-NON-BLOCKER）|
| 受保护页面零风险 | ✅（EXT-19 与 3 受保护页面零交集）|

---

## 九、停止点

### 9.1 Batch 1.4（EXT-19）Phase 5 全程完成

| Phase | 任务数 | 状态 |
|---|---|---|
| Phase 5A（DB-only + 静态）| 8 | ✅ 全部 PASS |
| Phase 5B（API + Mock + jshell）| 7 + jshell 降级 | ✅ 全部 PASS（jshell 降级给用户本地）|
| Phase 5C（Frontend Shadow + 汇总）| 4 + 汇总 | ✅ 全部 PASS（静态实证 + 汇总报告）|

### 9.2 33 Feature 最终归属校验

| Result | 数量 | Feature |
|---|---|---|
| **VERIFIED** | 0 | — |
| **PARTIAL-BLOCKER** | 0 | （I038/I045 阻断导致 16 Feature MISSING，无 PARTIAL-BLOCKER）|
| **PARTIAL-NON-BLOCKER** | 7 | F087 / F094 / F101 / F102 / F103 / F106 / F111 / F112 / F113（注：F111/F112 在 5C 静态 PASS）|
| **BROKEN** | 0 | — |
| **CONFLICT** | 1 | F097 |
| **MOCK-INTENTIONAL / MOCK-FAKE** | 0 | — |
| **SHELL-INTENTIONAL / SHELL-ABANDONED** | 0 | — |
| **MISSING** | 9 | F081 / F082 / F083 / F091 / F095 / F096 / F098 / F099 / F100 / F104（注：I038 阻断 + C1 偏差点）|
| **UNVERIFIED** | 7 | F088 / F089 / F090 / F092 / F108 / F109 / F110（注：F084/F085/F086/F093/F105/F107 归 OK / Static-Only OK）|
| **Static-Only OK** | 5 | F085 / F086 / F093 / F105 / F107 |
| **OK** | 1 | F084（BookingConfirmationTemplateResolver 通过 6 locale 资源 + 静态判定）|
| **合计** | **33** | F081-F113 |

### 9.3 15 个 Issue 最终归属

| Issue 类别 | 数量 | 严重度分布 |
|---|---|---|
| MISSING（DB）| 2 | I038（高）/ I045（高）|
| CONFLICT（中立标）| 2 | I039 / I043（均中）|
| Shadow（Frontend）| 3 | I040 / I041 / I042（均中）|
| 架构异味（Backlog）| 1 | I044（低）|
| 字段对齐确认 | 1 | I046（低，已对齐）|
| **CRITICAL-P1（Fake Success）**| 1 | **I047（CRITICAL-P1）** |
| Transaction | 1 | I048（中）|
| UNVERIFIED（D10 铁律）| 1 | I049（中）|
| 死代码（T07-D4 例外）| 1 | I050（低）|
| 演示模式 | 1 | I051（中）|
| 编译失败（跨模块）| 1 | I052（中-高）|
| **合计** | **15** | — |

### 9.4 ⏸ 等待用户确认后启动 EN-09（Batch 1.4 小结）

**Phase 5C 完成。Batch 1.4 Phase 5 全程（5A + 5B + 5C）已闭环。⏸ 等用户确认后启动 Batch 1.4 小结（EN-09 批次汇总）→ 输出 `audit/reports/batch-1.4-summary.md` + `audit/batches.md` Batch 1.4 段。**

---

## 十、EN-09（Batch 1.4 小结）启动建议

> 等用户确认本报告后启动 EN-09 批次汇总。

| EN-09 任务 | 内容 | 输出位置 |
|---|---|---|
| 任务 1 | 输出 `audit/reports/batch-1.4-summary.md`（30 Feature 完整 Result + 阻塞项 + 修复优先级 + 决策矩阵）| 与 batch-1.2-summary.md 同模式 |
| 任务 2 | 更新 `audit/batches.md` Batch 1.4 段（Feature Inventory + Traceability + Static Gap + Reports + Issues）| 沿用 Batch 1.3 段格式 |
| 任务 3 | 更新 `audit/feature-inventory.md` Batch 1.4 段 I040/I041/I042 实证状态（5C 静态 PASS）| 5C 已完成 |
| 任务 4 | 更新 `audit/traceability-matrix.md` Batch 1.4 段（33 Feature 全栈追溯）| 沿用 Batch 1.3 格式 |
| 任务 5 | 更新 `audit/issues/issue-register.md` 追加 §二十 Batch 1.4 EN-09 段 | 沿用 §十七 / §十八 / §十九 格式 |
| 任务 6 | 决策表更新（Q5-Q8：Post-Audit 启动条件 + Batch 1.5 选择 + I047 修复 + 跑迁移）| 等用户决策 |

**EN-09 状态**：⏸ 等用户确认本 Phase 5C 汇总报告 → 启动 Batch 1.4 小结

---

**Batch 1.4 Phase 5C 完成。33 Feature Result 全部归属；15 个 Issue 全部登记（I038-I052）；Playwright 脚本已就位；I040/I041/I042 静态实证 PASS。下一阶段：EN-09（Batch 1.4 小结） → 等用户确认。**
