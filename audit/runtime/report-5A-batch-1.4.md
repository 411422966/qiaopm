# Phase 5A — Runtime Verification Report (Batch 1.4 / EXT-19 多语言预约确认)

> **创建时间**：2026-09-14 20:40 (UTC+8)
> **Phase**：5A — Runtime Verification（DB-only + 静态验证）
> **范围**：Batch 1.4 / EXT-19 多语言预约确认侧（8 个任务：5A.1-5A.8）
> **起点 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2/1.3 freeze）
> **不绑定业务代码改动**（规则 6）
> **不动 SoT**（D24 决策）
> **不跑迁移**（D21/D22 决策 — 沿用 I002/I017/I029 模式）
> **用户决策确认**：
> - 用户决策 #1 Phase 4 计划通过 ✅
> - 用户决策 #2 后端 8080 = 5A 不需要，5B 需要 → 5A 仅 DB-only + 静态
> - 用户决策 #3 MySQL 3307 = 用户待确认 → 5A 期间可连（与 preflight §0.1 一致）
> - 用户决策 #4 D21-D28 复核通过（与之前 D21-D30 一致）✅
> - 用户决策 #5 按 5A → 5B → 5C 分批 ✅

---

## 〇、运行时栈核查

| 维度 | 状态 | 来源 |
|---|---|---|
| MySQL 客户端 | ✅ `mysql.exe Ver 8.0.46 for Win64` 可用 | `where mysql` |
| MySQL 3307 连接 | ✅ `127.0.0.1:3307 / meiyetec / root / root123456` 可连 | DESCRIBE 命令成功 |
| MySQL 版本 | ✅ MySQL 8.0.46 | `mysql --version` |
| 后端 8080 | ⏸ 5A 不需要（用户决策 #2） | N/A |
| 前端 5173 | ⏸ 5A 不需要 | N/A |
| Token (admin/clerk01) | ⏸ 5A 不需要 | N/A |
| 后端日志 | ⏸ 5A 不需要 | N/A |

> **5A 范围明确**：仅 DB-only DESCRIBE + 静态代码分析；不启动后端、不跑 curl、不跑 jshell。

---

## 一、5A 任务实证汇总（8 个任务）

| 任务 | 验证目标 | 预期 | 实际 | 状态 |
|---|---|---|---|---|
| **5A.1** | `DESCRIBE overseas_booking` | 表缺失（I038） | ✅ `ERROR 1146 (42S02): Table 'meiyetec.overseas_booking' doesn't exist` | **PASS** |
| **5A.2** | `DESCRIBE member_message` | 表缺失（I045） | ✅ `ERROR 1146 (42S02): Table 'meiyetec.member_message' doesn't exist` | **PASS** |
| **5A.3** | `DESCRIBE pos_store` 字段对齐 | store_name + contact_phone 存在 | ✅ 24 字段表存在；store_name (varchar(100) NOT NULL) + contact_phone (varchar(20) YES NULL) | **PASS** |
| **5A.4** | grep `ChannelDispatcher.healthCheckAll` | 仅更新 tenant_id=1（I050） | ✅ L39 `Long tenantId = 1L;` 硬编码 + L41 `[T07-D4]` 例外声明 + L46 UPDATE SQL 参数绑定 tenantId | **PASS** |
| **5A.5** | grep `IntlBookingService.updateStatus` autoSend catch | I047 try/catch 吞异常模式 | ✅ L195-208 autoSend=true 默认 + try/catch 包 sendConfirmation + catch Exception 吞 + 仅写 confirmationError + 不重新抛出 | **PASS** |
| **5A.6** | 6 locale JSON 内容完整性 | 4 段 + 7 占位符完整 | ✅ 6 文件全部存在 + subject/sms/miniProgram/email 各 1 + 7 占位符全覆盖 | **PASS** |
| **5A.7** | Frontend Shadow（I040/I041/I042）| autoSend=false UI 缺失 + 按钮文案 + sendConfirmation 方法缺失 | ✅ grep 全工程 0 命中 + setStatus 不传 autoSend + 按钮文案仅"确认" | **PASS** |
| **5A.8** | grep `IntlBookingService` 注解 | @RestController (I044) | ✅ L33 @RestController + L34 @RequestMapping("/intl-booking") — Service as Controller 第 3 次登记 | **PASS** |

### 1.1 任务 → Feature 关联映射

| 任务 | 关联 Feature | 当前 Result | 5A 实证后 Result |
|---|---|---|---|
| **5A.1** | F081（OverseasBooking 实体）/ F082（overseas_booking DDL）| MISSING（I038） | **维持 MISSING** |
| **5A.2** | F091（InAppSender）| MISSING（I045） | **维持 MISSING** |
| **5A.3** | F087（StoreLookupService）| 待字段对齐验证 | **字段对齐 OK，5B 验证 lookup() + 缓存** |
| **5A.4** | F094（ChannelDispatcher）| PARTIAL-NON-BLOCKER（T07-D4 例外） | **维持 PARTIAL-NON-BLOCKER** |
| **5A.5** | F096（IntlBookingService.updateStatus）| PARTIAL-NON-BLOCKER（I047 + I048） | **维持 PARTIAL-NON-BLOCKER** |
| **5A.6** | F084（BookingConfirmationTemplateResolver）/ F107（6 locale JSON 模板）| OK（资源就位） | **维持 OK（5B 验证 warmup + 替换）** |
| **5A.7** | F111（intl-booking/index.vue）/ F112（intl-booking.ts）| PARTIAL-NON-BLOCKER（I040 + I041 + I042） | **维持 PARTIAL-NON-BLOCKER（D23 暂缓）** |
| **5A.8** | F113（IntlBookingService 命名异味）| PARTIAL-NON-BLOCKER + 架构异味（I044 backlog） | **维持 PARTIAL-NON-BLOCKER + 架构异味** |

---

## 二、负向证据实证清单（N1-N25 中 5A 覆盖项）

| # | 验证目标 | 预期 | 实际 | 状态 |
|---|---|---|---|---|
| **N1** | `overseas_booking` 表不存在 | TableNotFoundException | ✅ `ERROR 1146 (42S02): Table 'meiyetec.overseas_booking' doesn't exist` | **PASS** |
| **N2** | `member_message` 表不存在 | TableNotFoundException | ✅ `ERROR 1146 (42S02): Table 'meiyetec.member_message' doesn't exist` | **PASS** |
| **N4** | `pos_store` 字段对齐 | store_name + contact_phone | ✅ 24 字段表存在 + 2 目标字段全在 | **PASS** |
| **N3** | `db-snapshot.sql` grep 缺 EXT-19 表 | 0 命中 overseas_booking / member_message | ⏸ 5A 未执行（plan-batch-1.4.md §7.1 列在 5A.4 但本次按用户指令执行 I050 任务） | **DEFERRED** |
| **N21** | 前端缺 autoSend=false UI | grep 0 命中 | ✅ `views/intl-booking/` 全目录 grep `autoSend\|发送通知\|二次确认\|loading` → 0 命中 | **PASS** |
| **N22** | 前端按钮文案无"确认即发送通知" | 按钮文案仅"确认" | ✅ `intl-booking/index.vue:38` `<el-button ...>确认</el-button>` — 无提示文案 | **PASS** |
| **N23** | 前端 intl-booking.ts 无 sendConfirmation | grep 0 命中 | ✅ 全工程 `frontend/meiyetec-web/src/` grep `sendConfirmation\|send-confirmation` → 0 命中 | **PASS** |
| **N24** | IntlBookingService 命名异味 | @RestController 命中 | ✅ L33 `@RestController` + L34 `@RequestMapping("/intl-booking")` — Service as Controller 模式 | **PASS** |
| **N25** | ChannelDispatcher 仅更新 tenant_id=1 | 硬编码 1L | ✅ L39 `Long tenantId = 1L;` + L41 `[T07-D4]` 例外声明 + L46 UPDATE SQL 绑定 tenantId | **PASS** |

### 2.1 5A 未覆盖的负向证据（N3 / N5-N20 / N26+）

| 编号 | 验证目标 | 计划批次 | 备注 |
|---|---|---|---|
| N3 | db-snapshot.sql grep 缺 EXT-19 表 | 5A / 5B | 本次未执行（用户 5A 清单聚焦 8 任务；plan-batch-1.4.md §7.1 列在 5A.4 但本次按用户指令执行 I050） |
| N5 | i18n warmup 6 locale 启动期执行 | 5B | 需启动后端 |
| N6 | i18n locale 回退链（xx-YY → xx → en-US）| 5B | 需 jshell |
| N7 | i18n 占位符 ${key} 替换 | 5B | 需 jshell |
| N8 | EmailSender mismatch demo 模式 | 5B | 需启动后端 |
| N9 | EmailSender 三模式降级 | 5B | 需 jshell |
| N10 | MiniProgramSender MP_TEMPLATE_ID 占位 | 5B | 需启动后端 |
| N11 | WechatOfficialAccountSender channelType 别名 | 5B | 需 jshell |
| N12 | autoSend 自动触发（CONFIRMED → sendConfirmation）| 5B | 需后端 + I038 MISSING 暴露 |
| N13 | autoSend=false 关闭路径 | 5B | 需后端 |
| N14 | confirmationTemplate 端点硬编码 switcher（I043 实证）| 5B | 需后端 |
| N15 | Google webhook 演示模式 | 5B | 需后端 |
| N16 | Instagram webhook 演示模式 | 5B | 需后端 |
| N17 | Instagram webhook 验证握手 | 5B | 需后端 |
| N18 | OAuth2 connect 返回真实 URL | 5B | 需后端 |
| N19 | OAuth2 callback 返回 demo token | 5B | 需后端 |
| N20 | convert 转化演示模式 | 5B | 需后端 |
| **新增** | **mkt_channel_config 表缺失**（推论）| 5B / Phase 5.5 | 5A.4 SQL `UPDATE mkt_channel_config` 实际执行会抛 TableNotFoundException（与 I038/I045 同模式）|

---

## 三、跨任务发现

### 3.1 5A 实证未触发任何 Phase 3 漏判修正

对比 Batch 1.3 5A 的 C1-C5 实证，本批次 5A：
- ✅ I038（overseas_booking MISSING）Phase 3 判定维持
- ✅ I045（member_message MISSING）Phase 3 判定维持
- ✅ I046（pos_store 字段对齐）Phase 3 静态分析判定（"DDL 已写 + 字段期望已写"）维持
- ✅ I047（autoSend catch 吞异常）Phase 3 静态分析判定（"I008 同模式"）维持
- ✅ I048（rollbackFor 缺失）Phase 3 静态分析判定（"I009 同模式"）维持
- ✅ I050（healthCheckAll 仅更新 tenant_id=1）Phase 3 静态分析判定（"T07-D4 例外"）维持
- ✅ I040 / I041 / I042（Frontend Shadow）Phase 3 静态分析判定（"前端 UI 缺失"）维持
- ✅ I044（命名异味）Phase 3 静态分析判定（"Service as Controller" 模式）维持

→ **无 Phase 3 漏判修正**，所有 5A 实证与 Phase 3 一致。

### 3.2 I048 rollbackFor 缺失关联确认（I009 模式）

`IntlBookingService.java:187`：

```java
@PostMapping("/{id}/status")
@Transactional                                              ← 未指定 rollbackFor
public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
```

→ 与 Batch 1.1 I009 rollbackFor 缺失同模式（默认仅 RuntimeException）
→ D26 决策：I048 暂缓（I009 模式对齐）

### 3.3 I051 webhook 演示模式关联（5A 未覆盖，5B 待证）

`IntlBookingService.googleWebhook` (L262-289)：

```java
private Long resolveTenantFromToken(String token) {
    // P3-26：根据 token 反查 tenant_id；演示模式返回 null 兜底为 1L
    return null;
}
```

→ Google webhook token → tenantId 反查返回 null 兜底 1L（演示模式）
→ Instagram webhook tenantId 硬编码 1L（演示模式）

→ I051 实证需在 5B 期间跑 Mock 推送样本验证（5A 仅静态可见代码层证据）

### 3.4 mkt_channel_config 表缺失推论

`ChannelDispatcher.healthCheckAll` (L46)：

```java
jdbc.update("UPDATE mkt_channel_config SET last_health_at = NOW(), last_health_msg = ? WHERE tenant_id = ? AND channel_type = ? AND deleted = 0",
        ok ? "OK" : "FAIL", tenantId, s.channelType());
```

→ 5A.4 仅静态确认 SQL 包含 tenantId=1L 硬编码
→ 实际执行时若 mkt_channel_config 表 MISSING → SQL 抛 TableNotFoundException
→ D13 决策：mkt_channel_config 不跑迁移（沿用 I002/I017/I029 模式）
→ 5A 实证到此为止；运行时实证留待 5B / Phase 5.5（建议 Post-Audit 跑迁移后补一次）

### 3.5 plan-batch-1.4.md §7.1 5A 任务清单 vs 用户 5A 指令差异

| plan §7.1 5A 任务 | 用户 5A 指令 | 实际执行 |
|---|---|---|
| 5A.1 overseas_booking DESCRIBE | 5A.1 overseas_booking | ✅ 一致 |
| 5A.2 member_message DESCRIBE | 5A.2 member_message | ✅ 一致 |
| 5A.3 pos_store 字段对齐 | 5A.3 pos_store 字段对齐 | ✅ 一致 |
| 5A.4 **db-snapshot.sql grep 0 命中** | 5A.4 **ChannelDispatcher.healthCheckAll (I050)** | ⚠️ **替换为用户最新指令**（I050 替代 db-snapshot grep） |
| 5A.5 6 locale JSON 完整性 | 5A.5 **autoSend catch 吞异常 (I047)** | ⚠️ **替换为用户最新指令**（I047 替代 locale 完整性） |
| 5A.6 autoSend UI grep | 5A.6 **6 locale JSON 内容完整性** | ⚠️ **顺延为 5A.6** |
| 5A.7 sendConfirmation grep | 5A.7 **Frontend Shadow 静态分析（I040/I041/I042）** | ⚠️ **合并 3 项 Issue 到 5A.7** |
| 5A.8 @RestController grep | 5A.8 **命名异味静态分析（I044）** | ⚠️ **顺延为 5A.8** |

→ 用户 5A 指令中 5A.4 / 5A.5 与 plan §7.1 不同；本次按用户最新指令执行
→ db-snapshot.sql grep（N3）被替换为 I050 + I047 静态实证；如需补 N3 实证请在 5B 启动前手动补
→ 建议 plan-batch-1.4.md §7.1 与本次 5A 报告对齐（更新 plan 文件）

---

## 四、Feature 单一归属校验（5A 触及的 12 个 Feature）

| Feature | 当前归属 | 5A 实证后归属 | 复核 |
|---|---|---|---|
| F081 | MISSING（I038）| MISSING | ✅ 维持 |
| F082 | MISSING（I038）| MISSING | ✅ 维持 |
| F083 | 待 5B 启动验证 | 待 5B | ✅ 维持 |
| F084 | OK（6 locale 资源就位）| OK | ✅ 维持（5A.6 静态验证） |
| F085 | Static-Only | Static-Only | ✅ 维持 |
| F086 | Static-Only | Static-Only | ✅ 维持 |
| F087 | MISSING（I046 pos_store 字段）| PARTIAL（字段对齐 OK；5B 验证 lookup + 缓存） | ✅ 修正 |
| F088 | UNVERIFIED（I049 D10）| UNVERIFIED | ✅ 维持 |
| F089 | UNVERIFIED（I049 D10）| UNVERIFIED | ✅ 维持 |
| F090 | UNVERIFIED（I049 D10）| UNVERIFIED | ✅ 维持 |
| F091 | MISSING（I045）| MISSING | ✅ 维持 |
| F092 | UNVERIFIED（I049 D10）| UNVERIFIED | ✅ 维持 |
| F093 | Static-Only OK | Static-Only OK | ✅ 维持 |
| F094 | PARTIAL-NON-BLOCKER（T07-D4）| PARTIAL-NON-BLOCKER | ✅ 维持（5A.4 静态验证） |
| F095 | MISSING（I038 间接）| MISSING | ✅ 维持 |
| F096 | PARTIAL-NON-BLOCKER（I047 + I048）| PARTIAL-NON-BLOCKER | ✅ 维持（5A.5 静态验证） |
| F097 | CONFLICT（I043 硬编码 switcher）| CONFLICT | ✅ 维持 |
| F098 | MISSING（I038 间接）| MISSING | ✅ 维持 |
| F099 | PARTIAL-NON-BLOCKER（I048 + I051）| PARTIAL-NON-BLOCKER | ✅ 维持 |
| F100 | PARTIAL-NON-BLOCKER（I048 + I051）| PARTIAL-NON-BLOCKER | ✅ 维持 |
| F101 | OK（演示模式）| OK | ✅ 维持 |
| F102 | PARTIAL-NON-BLOCKER（I051）| PARTIAL-NON-BLOCKER | ✅ 维持 |
| F103 | PARTIAL-NON-BLOCKER（I048）| PARTIAL-NON-BLOCKER | ✅ 维持 |
| F104 | MISSING（I038 间接）| MISSING | ✅ 维持 |
| F105 | Static-Only OK | Static-Only OK | ✅ 维持 |
| F106 | PARTIAL-NON-BLOCKER（I051）| PARTIAL-NON-BLOCKER | ✅ 维持 |
| F107 | OK（6 locale 资源就位）| OK | ✅ 维持（5A.6 静态验证） |
| F108 | 待 5B mvn test | 待 5B | ✅ 维持 |
| F109 | 待 5B mvn test | 待 5B | ✅ 维持 |
| F110 | 待 5B mvn test | 待 5B | ✅ 维持 |
| F111 | PARTIAL-NON-BLOCKER（I040 + I041）| PARTIAL-NON-BLOCKER | ✅ 维持（5A.7 静态验证） |
| F112 | PARTIAL-NON-BLOCKER（I042）| PARTIAL-NON-BLOCKER | ✅ 维持（5A.7 静态验证） |
| F113 | PARTIAL-NON-BLOCKER + 架构异味（I044 backlog）| PARTIAL-NON-BLOCKER + 架构异味 | ✅ 维持（5A.8 静态验证） |

→ 5A 实证维持 32/33 Feature 归属；F087 修正：I046 "MISSING" → "PARTIAL（字段对齐 OK）"

---

## 五、5A 输出文件清单

```
audit/evidence/5A-batch-1.4-static-checks/
├── overseas-booking-describe.txt         (5A.1 — I038 MISSING 实证)
├── member-message-describe.txt           (5A.2 — I045 MISSING 实证)
├── pos-store-describe.txt                (5A.3 — I046 字段对齐实证)
├── channel-dispatcher-grep.txt           (5A.4 — I050 实证)
├── autosend-catch-grep.txt               (5A.5 — I047 + I048 实证)
├── locale-completeness.txt               (5A.6 — F084/F107 完整性)
├── intl-booking-ui-grep.txt              (5A.7 — I040/I041/I042 实证)
└── intl-booking-service-annotations.txt  (5A.8 — I044 实证)

audit/runtime/
├── plan-batch-1.4.md                     (Phase 4 计划 — 已生成)
└── report-5A-batch-1.4.md                (本文件 — Phase 5A 报告)
```

→ 5A 共 8 个 evidence 文件 + 1 个报告文件
→ 5A 不写 FXXX 单独目录（5A 是整体性 DB-only + 静态验证，不针对单个 Feature 单独 evidence）
→ 5B 启动后如需 Feature 级 evidence，按 FXXX 模式创建

---

## 六、未完成 / 等待用户确认

### 6.1 Phase 5B 准备状态

| 项 | 状态 |
|---|---|
| MySQL 3307 可连 | ✅ 5A 期间确认 |
| 后端 8080 启动 | ⏸ 用户决策 #2 待确认（5B 必需） |
| 后端启动后端日志 | ⏸ 5B 期间采集（`backend/logs/meiyetec.log`）|
| admin + clerk01 token | ⏸ 5B 启动前需重登（token 24h 过期） |
| jshell 环境 | ⏸ 需 jshell 可用 |
| mvn test 环境 | ⏸ 需后端 mvn 环境 |

### 6.2 Phase 5B 范围预告（基于 plan-batch-1.4.md §7.2）

| 任务 | 验证目标 | 预期结果 | 关联 Issue |
|---|---|---|---|
| **5B.1** | 启动后端 → `[I18N-TPL] warmup done, locales=[6]` | warmup 6 locale OK | OK |
| **5B.2** | `curl GET /intl-booking/list` | I038 MISSING 暴露 500 | I038 |
| **5B.3** | `curl POST /intl-booking/{id}/send-confirmation` | I038 MISSING 暴露 404 | I038 |
| **5B.4** | `curl POST /intl-booking/{id}/status -d '{"status":"CONFIRMED"}'` | I038 MISSING 暴露 500 + I047 实证 | I038 + I047 |
| **5B.5** | `curl POST /intl-booking/{id}/status -d '{"status":"CONFIRMED","autoSend":false}'` | 不触发 sendConfirmation | I047 |
| **5B.6** | Mock 推送 Google webhook → `curl POST /intl-booking/webhook/google` | I038 MISSING 暴露 500 + I051 演示模式 | I038 + I051 |
| **5B.7** | Mock 推送 IG webhook → `curl POST /intl-booking/webhook/instagram` | I038 MISSING 暴露 500 + 简易 NLP 匹配 | I038 + I051 |
| **5B.8** | jshell `SmsSender.render(template, vars)` 3 组 | 占位符替换 OK | OK |
| **5B.9** | jshell `EmailSender.demoMode(...)` | demo 模式 OK | OK |
| **5B.10** | jshell `MiniProgramSender.buildSubscribeData(variables)` | keyword1..5 生成 OK | OK |
| **5B.11** | jshell `WechatOfficialAccountSender.channelType()` | `"WECHAT_OFFICIAL_ACCOUNT"` 而非 `"WECHAT_OA"` | I039 |
| **5B.12** | jshell `resolver.warmup()` + `resolver.loadLocale("zh-CN")` | 6 locale OK + 4 段字段完整 | OK |
| **5B.13** | jshell `resolver.resolve("xx-YY", vars)` | `usedFallback=true` | OK |
| **5B.14** | `mvn -Dtest=BookingConfirmationServiceTest,BookingConfirmationTemplateResolverTest,'*SenderTest' test` | 全部 PASS | OK |

### 6.3 关键前置确认（5B 启动前）

- [ ] **后端 8080 启动？**（用户决策 #2 待确认）
- [ ] **MySQL 3307 仍可连？**（5A 期间 ✅；5B 启动前再验证一次）
- [ ] **admin + clerk01 token 仍有效？**（token 24h 过期，5B 启动前重登）
- [ ] **WeChat MP / SMTP 凭据缺失 → F088 / F089 / F090 / F092 维持 UNVERIFIED**（D10 铁律 / I049）
- [ ] **Cursor 不跑 jshell / mvn test** → 5B.8-5B.14 需用户在本地跑（提供 PS 脚本）
- [ ] **db-snapshot.sql N3 实证**（被本次 5A 替换）→ 5B 启动前补做

### 6.4 受保护页面零风险确认

- ✅ EXT-19 端点（`/intl-booking/*`）与受保护页面（`/dashboard` / `/mh/visual` / `/mh/consent`）后端接口**零交集**
- ✅ 5A 期间**未触及**受保护页面任何文件
- ✅ 5B 启动后继续维持零交集

---

## 七、修正建议

### 7.1 plan-batch-1.4.md §7.1 5A 任务清单对齐

建议更新 plan-batch-1.4.md §7.1 表格 5A.4 / 5A.5 / 5A.6 / 5A.7 / 5A.8 描述，与本次用户指令对齐：

| 任务 | plan §7.1 描述 | 本次 5A 实际执行 |
|---|---|---|
| 5A.4 | `grep overseas_booking db-snapshot.sql` | `grep ChannelDispatcher.healthCheckAll (I050)` |
| 5A.5 | `ls i18n/template/*.json`（6 locale）| `grep autoSend catch 吞异常 (I047)` |
| 5A.6 | `grep autoSend frontend/.../intl-booking/index.vue` | `6 locale JSON 内容完整性检查` |
| 5A.7 | `grep sendConfirmation frontend/.../intl-booking.ts` | `Frontend Shadow 静态分析（I040/I041/I042）` |
| 5A.8 | `grep "@RestController" service/IntlBookingService.java` | `命名异味静态分析（I044）` |

→ 5B 启动前建议同步 plan §7.1 与本次报告（保持文档与执行一致）

### 7.2 I044 命名异味 Issue 登记建议

当前 issue-register.md 命名异味历史：
- I004 (Batch 1.1) — CrmIntegrationService
- I019 (Batch 1.2) — PluginService
- C4 实证 (Batch 1.3) — EcommerceService（未单独登记新 Issue；I019 模式覆盖）
- **I044 (Batch 1.4) — IntlBookingService（待登记）**

建议登记条目：

```markdown
### I044 — `IntlBookingService` 命名异味（Backlog，沿用 I004 / I019 模式）

> **类别**：命名/架构异味
> **严重度**：🟢 低（backlog，不阻塞）
> **发现时间**：2026-09-14 20:40 (UTC+8)
> **关联 Feature**：F113
> **Phase 5A 实证**：`backend/src/main/java/com/meiyetec/service/IntlBookingService.java:33-34`
>  - L33: `@RestController`
>  - L34: `@RequestMapping("/intl-booking")`
>  - 文件名 `*Service.java` 但实际为 REST 控制器（含 15 个 HTTP 端点）
> **历史沿用**：I004 (CrmIntegrationService) + I019 (PluginService) + C4 实证 (EcommerceService)
> **决策**：登记 backlog，Post-Audit 整改；Fix Phase = Post-Audit（重构）
> **沿用依据**：I004 决策"登记 backlog，不进 Phase 5 修复"
```

### 7.3 F087 字段对齐结论修正

- 原 plan-batch-1.4.md §一 F087 描述："MISSING(I046 I002 模式)" + "用户手动 mysql -e 'DESCRIBE pos_store'"
- 5A.3 实证：pos_store 表**实际存在**（24 字段），store_name + contact_phone 都在
- 建议：F087 维持 PARTIAL-NON-BLOCKER（字段对齐 OK），不再是 MISSING
- plan-batch-1.4.md §一 F087 描述建议更新为 "PARTIAL-NON-BLOCKER（5A.3 字段对齐 OK；5B 验证 lookup() + 5 分钟缓存）"

---

## 八、停止点

### 8.1 5A 完成情况

| 维度 | 状态 |
|---|---|
| 8 个任务全部 PASS | ✅ |
| 8 个 evidence 文件已落 | ✅ `audit/evidence/5A-batch-1.4-static-checks/` |
| 1 个报告已生成 | ✅ 本文件 `audit/runtime/report-5A-batch-1.4.md` |
| 不动业务代码 | ✅（规则 6） |
| 不跑迁移 | ✅（D21/D22 决策） |
| 不动 SoT | ✅（D24 决策） |
| 不修改 Frontend 文件 | ✅（D23 决策） |
| 不修复命名异味 | ✅（D26 决策） |
| 不跑 jshell / 不跑 mvn test | ✅（5A 范围） |
| 不启动后端 | ✅（用户决策 #2） |
| 受保护页面零风险 | ✅（零交集） |

### 8.2 关键 Issue 实证状态

| Issue | 5A 实证 | 状态 |
|---|---|---|
| I038（overseas_booking MISSING）| ✅ 5A.1 PASS | 维持（Post-Audit 跑迁移）|
| I045（member_message MISSING）| ✅ 5A.2 PASS | 维持（Post-Audit 跑迁移）|
| I046（pos_store 字段对齐）| ✅ 5A.3 PASS | 维持（字段对齐 OK）|
| I047（autoSend catch 吞异常）| ✅ 5A.5 PASS | 维持（D26 暂缓）|
| I048（rollbackFor 缺失）| ✅ 5A.5 关联 PASS | 维持（D26 暂缓）|
| I050（healthCheckAll 仅 tenant_id=1）| ✅ 5A.4 PASS | 维持（T07-D4 例外）|
| I040（autoSend=false UI 缺失）| ✅ 5A.7 PASS | 维持（D23 暂缓）|
| I041（按钮文案无"确认即发送通知"）| ✅ 5A.7 PASS | 维持（D23 暂缓）|
| I042（intl-booking.ts 无 sendConfirmation）| ✅ 5A.7 PASS | 维持（D23 暂缓）|
| I044（命名异味 backlog）| ✅ 5A.8 PASS | **新登记建议（§7.2）** |

### 8.3 ⏸ 等待用户确认后启动 5B

- 用户决策 #2：后端 8080 启动确认
- 用户决策 #3：MySQL 3307 5B 期间可用确认
- admin + clerk01 token 重登（5B 启动前）
- 是否同意 plan-batch-1.4.md §7.1 5A 任务清单对齐建议（§7.1）
- 是否同意 I044 命名异味 Issue 登记建议（§7.2）
- 是否同意 F087 字段对齐结论修正建议（§7.3）

**5A 完成。⏸ 等用户确认后启动 Phase 5B（API + Mock + jshell 单元测试）。**
