# Phase 4 — Runtime Verification Plan(Batch 1.4)

> **创建时间**:2026-09-14 20:25 (UTC+8)
> **Phase**:4 — Runtime Verification Plan
> **范围**:Batch 1.4 — EXT-19 多语言预约确认(33 个 Feature:F081-F113)
> **起点 commit**:`e85ba8ba8540081f2f59c97120052b5bef5a29ef`(Batch 1.2/1.3 freeze)
> **Batch 决策回顾**(沿用 preflight §十 + scope-freeze §十.1 用户确认 8 项):
> - **D21**:**不跑** `migrate_ext_19_mini_program_openid.sql`(沿用 I002/I017/I029 模式,统一 Post-Audit)
> - **D22**:**不跑** `member_message` DDL(沿用 D21 模式)
> - **D23**:I040/I041/I042 暂缓 Frontend Shadow,Post-Audit 整改
> - **D24**:I039/I043 中立标,不动 `ext-19-operational-notes.mdc`
> - **D25**:I046(pos_store 字段)只读 DESCRIBE,Phase 6 时执行(不是 Phase 3,不是迁移)
> - **D26**:I047/I048 暂缓(I008/I009 模式对齐)
> - **D27**:I049 Mock 降级,D10 铁律(WeChat MP / SMTP 真实链路 UNVERIFIED)
> - **D28**:I050/I051 维持现状(演示模式足够联调)
> - **D10 验证铁律(沿用 Batch 1.1-1.3)**:Phase 5 跑不通 → 降级 UNVERIFIED,**不造证据**
> - **受保护页面干扰**:EXT-19 端点与 `/dashboard` / `/mh/visual` / `/mh/consent` 后端接口零交集 → 不触及(scope-freeze §九.8 已确认)
> - **规则**:审计阶段不修改业务代码(规则 6)→ Phase 4 仅记录验证策略,不进 Phase 5 执行

---

## 〇、运行时验证总览

### 0.1 验证工具矩阵

| 工具 | 适用 | 状态 |
|---|---|---|
| **curl / PowerShell `Invoke-RestMethod`** | API-only 验证 | ✅ 可用(Phase 5 用户跑) |
| **MySQL CLI / MySQL MCP** | DB-only 验证 | ✅ 可用(按 preflight §8.3 配置)|
| **JVM `mvn clean package` + 后端启动** | 后端真实启动验证 | ⚠️ **Cursor 不能跑**(需 JVM + Spring Boot)→ 降级方案见 §0.2 |
| **jshell** | 静态方法调用(SmsSender.render / EmailSender demo / Resolver warmup)| ✅ 可用(沿用 Batch 1.2/1.3 Phase 5B 模式) |
| **Playwright MCP** | UI E2E | ❌ **未配置** → 降级方案 |
| **手动浏览器(用户执行)**| UI E2E 降级 | ✅ Phase 5 用户跑 Playwright 脚本 |
| **WeChat MP API 真实调用** | MiniProgramSender 真实链路 | ❌ **凭据缺失**(用户决策 #1)→ Mock 降级 |
| **SMTP 真实发送** | EmailSender 真实链路 | ❌ **凭据缺失**(用户决策 #2)→ mismatch demo 模式 |
| **`overseas_booking` 表迁移** | Phase 5 仅验证缺失 | ❌ **不跑**(D21 用户决策)|

### 0.2 EXT-19 降级方案(WeChat MP / SMTP 真实链路不可达)

对任何标「真实链路验证」的 Feature(F088 SmsSender / F089 EmailSender / F090 MiniProgramSender / F092 WechatOA Sender),Phase 5 降级为:

1. **Cursor 静态验证**(已完成于 Phase 3):代码路径、catch 位置、表/类引用全部 L0 可达
2. **用户手动跑**:
   - 启动后端 → 看 `[I18N-TPL]` + `[EMAIL]` + `[MINI-PROGRAM]` + `[WECHAT-OA]` 启动日志 + `warmup done, locales=6`
   - curl `/intl-booking/list`(需 overseas_booking 表 → D21 决策仅验证缺失)
   - jshell 调用 `SmsSender.render(template, vars)` 验证占位符替换算法
   - jshell 调用 `EmailSender.demoMode(...)` 验证 mismatch demo 模式日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode`
   - jshell 调用 `BookingConfirmationTemplateResolver.warmup()` + `loadLocale(locale)` 验证 6 locale 加载
   - Mock 推送 webhook 样本(Google / Instagram 演示模式)
3. **结果贴回** → `audit/evidence/FXXX-<slug>/result.md`(用户记录 pass/fail + 截图)
4. **若 WeChat MP / SMTP 真实链路跑不通**(凭据缺失)→ **直接降级 UNVERIFIED,不造证据**(D10 铁律 / I049)

### 0.3 验证方式 vs Feature 类型映射

| Feature 类型 | 主验证 | 辅验证 |
|---|---|---|
| **DB 表 MISSING(F081 / F082 / F091)** | **DB-only**(DESCRIBE → 表缺失)| Static-Only(DDL 已写)|
| **DB 字段对齐(F087 pos_store)** | **DB-only**(DESCRIBE pos_store)| Static-Only(SQL 已写)|
| **i18n warmup + locale(F084 / F107)** | **用户手动 + jshell**(启动期 warmup + 6 locale load)| Static-Only(资源已就位)|
| **BookingConfirmationService 核心(F083)** | **用户手动**(启动 + curl `/intl-booking/{id}/send-confirmation`)| Mock 调用 + jshell |
| **Channel Sender 真实链路(F088 / F089 / F090 / F092)** | **Mock 降级**(用户决策 #1/#2)| Static-Only + jshell 单元测试 |
| **InAppSender DB 写库(F091)** | **DB-only**(DESCRIBE member_message)| Static-Only |
| **5 渠道路由 + 聚合(F083)** | **用户手动**(启动 + mock booking + 5 sender 调用)| jshell 单元测试 |
| **autoSend 自动触发(F096)** | **用户手动**(POST status=CONFIRMED)| Static-Only(代码层验证)|
| **autoSend=false 关闭(F096)** | **用户手动**(POST body.autoSend=false)| Static-Only |
| **confirmationTemplate 端点(F097)** | **用户手动 + curl**(`GET /intl-booking/{id}/confirmation-template`)| Static-Only(I043 中立标)|
| **webhook 演示模式(F099 / F100 / F101)** | **Mock 推送样本**(Google / Instagram)| DB-only(写 overseas_booking 演示行) |
| **OAuth2 connect/callback(F102)** | **Mock URL**(authUrl 跳转)| Static-Only(返回 ya29.DEMO- token) |
| **convert 转化(F103)** | **Mock**(JdbcTemplate 写 crm_member + pos_order)| Static-Only |
| **Frontend UI Shadow(F111 / F112)** | **Playwright E2E 降级** | Static-Only(grep autoSend / sendConfirmation 0 匹配)|
| **CONFLICT 中立标(I039 / I043)** | Phase 5 **只读不判** | 静态 SoT 比对完成 |
| **UNVERIFIED(I049)** | Phase 5 仅验证 mock + jshell 单元测试 | 真实链路 UNVERIFIED 维持 |
| **命名异味(F113 / I044)** | Static-Only | Phase 7 Post-Audit 决定 |
| **测试(F108 / F109 / F110)** | **mvn test**(沿用 Batch 1.2/1.3)| — |

### 0.4 优先级规则

| 优先级 | 适用 Feature 类型 | Phase 5 必测 |
|---|---|---|
| **P0** | DB MISSING 实证 + BookingConfirmationService 核心 + autoSend 行为变更 + 6 locale warmup | ✅ |
| **P1** | 5 Channel Sender + i18n Resolver + webhook 演示 + OAuth2 connect + convert | ✅ |
| **P2** | 列表 / 单条 / 渠道列表 / 拉取 / Entity / DTO / 命名异味 / 测试 | ⏸️ 降级后非强制 |
| **P3** | 仅前端 UI(Shadow) | ⏸️ 用户手动 Playwright 脚本 |

---

## 一、P0 — DB MISSING 实证 + 核心发送 + i18n warmup(7 个 Feature)

| Feature ID | Priority | 验证方式 | 是否需 E2E | 是否需 DB 断言 | 理由 |
|---|---|---|---|---|---|
| **F081** OverseasBooking 实体 | **P0** | **DB-only** | ✗ | ✓ | `entity/OverseasBooking.java:11-31` 26 字段含 `miniProgramOpenId (L21)`。**MISSING(I038)**。**用户手动**:`mysql -e "DESCRIBE overseas_booking"` → **预期错误** `Table 'meiyetec.overseas_booking' doesn't exist`(I038 MISSING 实证)。DB 断言:① `SHOW TABLES LIKE 'overseas_booking'` → 0 行;② 对照 DDL `migrate_ext_19_mini_program_openid.sql:9-32` 26 列 + 4 索引 |
| **F082** overseas_booking DDL | **P0** | **DB-only + Static-Only** | ✗ | ✓ | `db/sql/migrate_ext_19_mini_program_openid.sql:9-36` CREATE TABLE IF NOT EXISTS + ALTER ADD COLUMN IF NOT EXISTS mini_program_open_id。**MISSING(I038)**。**用户手动**:`grep overseas_booking audit/baseline/db-snapshot.sql` → 预期 0 命中(已确认);② `grep mini_program_open_id audit/baseline/db-snapshot.sql` → 0 命中;③ DDL 文件自身存在(`ls db/sql/migrate_ext_19*.sql` 验证) |
| **F083** BookingConfirmationService.sendConfirmation | **P0** | **用户手动 + jshell + Mock** | ✗ | ✗ | `service/BookingConfirmationService.java:69-115` 核心发送入口 + `:104-140` 5 渠道路由(SMS/EMAIL/MINI_PROGRAM)+ `:157-167` buildVars 8 变量。**PARTIAL-NON-BLOCKER(I047 I008 模式 try/catch 不回滚 — 与 SoT §0.3 完全一致)**。**用户手动**:① 启动后端 → 看 `[BOOKING-CONFIRMATION]` 启动日志;② `curl POST /intl-booking/{id}/send-confirmation` → 期望 `200 + BookingConfirmationResult.overallSuccess=true`(I038 MISSING 暴露:表不存在 → bookingId 不存在 → 404 NOT_FOUND);③ jshell 模拟 5 channel sender 全部 mock → 期望 overallSuccess=true |
| **F084** BookingConfirmationTemplateResolver | **P0** | **jshell + 用户手动** | ✗ | ✗ | `booking/BookingConfirmationTemplateResolver.java:64-111` `resolve(locale, vars)` + `:36-44` `warmup()` 启动期预热 6 locale + `:114-126` `resolveWithFallback`(xx-YY → xx → en-US)+ `:130-138` `normalize`(trim + lang 小写 / country 大写)。**用户手动**:① 启动后端 → 看启动日志 `[I18N-TPL] warmup done, locales=[zh-CN, zh-TW, en-US, ja-JP, ko-KR, fr-FR]`;② jshell `resolver.loadLocale("zh-CN")` → 期望非空 + 含 4 段(subject/sms/miniProgram/email);③ jshell `resolver.resolve("zh_CN", vars)` → 期望走 normalize → zh-CN;④ jshell `resolver.resolve("xx-YY", vars)` → 期望走 fallback → en-US;⑤ jshell `resolver.resolve(null, vars)` → 期望 DEFAULT_LOCALE=en-US |
| **F085** BookingConfirmationTemplate DTO | **P0** | **Static-Only** | ✗ | ✗ | `booking/BookingConfirmationTemplate.java:14-32` 8 字段 DTO(resolvedLocale/usedFallback/subject/sms/miniProgram/miniProgramTitle/emailSubject/emailBody)。L0 静态可达。**用户手动**:jshell 验证 `new BookingConfirmationTemplate(...)` 构造 → 字段赋值正确;`miniProgram` LinkedHashMap 顺序保留 |
| **F086** BookingConfirmationResult DTO | **P0** | **Static-Only** | ✗ | ✗ | `booking/BookingConfirmationResult.java:11-32` `overallSuccess` + `resolvedLocale/usedFallback/bookingId` + `channelResults: Map<channel, SendResult>`。L0 静态可达。**用户手动**:jshell 验证 `putChannel(channel, success=true)` → overallSuccess 自动累加 true;`putChannel(channel, success=false)` → overallSuccess 不变(任一 success=true 即 overallSuccess=true)|
| **F096** IntlBookingService.updateStatus + autoSend | **P0** | **用户手动 + curl** | ✗ | ✗ | `service/IntlBookingService.java:186-210` updateStatus — 非 CONFIRMED → CONFIRMED 自动触发 sendConfirmation;`body.autoSend=false` 可关闭;发送异常在 try/catch 内写 confirmationError;响应 `{booking, confirmation?, confirmationError?}`。**PARTIAL-NON-BLOCKER(I047 + I048 I009 模式默认 rollbackFor 仅 RuntimeException)**。**用户手动**:① 启动后端;② `curl POST /intl-booking/{id}/status -d '{"status":"CONFIRMED"}'` → 期望 I038 MISSING 暴露 `TableNotFoundException`(booking 不存在);③ 预插 1 行 overseas_booking(需先跑 D21 迁移 → 决策:不跑,改为 mock booking 调用 sendConfirmation 直接验证 autoSend 行为);④ `curl POST /intl-booking/{id}/status -d '{"status":"CONFIRMED","autoSend":false}'` → 期望 autoSend 不触发(但 I038 MISSING 仍暴露)|
| **F107** 6 locale JSON 模板 | **P0** | **Static-Only + 用户手动** | ✗ | ✗ | 6 资源文件 `resources/i18n/template/{zh-CN/zh-TW/en-US/ja-JP/ko-KR/fr-FR}.json` 均含 subject/sms/miniProgram(title+keyword1..5)/email(subject+body)4 段 + 7 占位符(customerName/serviceName/bookingDate/bookingTime/bookingNo/storeName/storePhone)。L0 静态可达。**用户手动**:① `ls resources/i18n/template/*.json` → 期望 6 文件;② jshell `ObjectMapper.readValue(URL, BookingConfirmationTemplate.class)` 验证 6 locale JSON 全部可解析;③ 占位符覆盖核对(详见 inventory §1.4.6)|

---

## 二、P1 — 5 Channel Sender + i18n Resolver + webhook 演示 + OAuth2(15 个 Feature)

| Feature ID | Priority | 验证方式 | 是否需 E2E | 是否需 DB 断言 | 理由 |
|---|---|---|---|---|---|
| **F087** StoreLookupService | **P1** | **用户手动 + DB-only** | ✗ | ✓ | `booking/StoreLookupService.java:54-76` `lookup(storeId)` + 5 分钟缓存(`CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)` L24)+ SQL `SELECT store_name, contact_phone FROM pos_store WHERE id=? AND deleted=0`。**MISSING(I046 I002 模式)**。**用户手动**:① `mysql -e "DESCRIBE pos_store"` → 验证 `store_name` + `contact_phone` 字段是否存在(I046 实证);② 启动后端 → 调 `BookingConfirmationService.sendConfirmation` 期望走 `StoreLookupService.lookup()`;③ 缓存验证:同一 storeId 第二次调用应走缓存(无 SQL)|
| **F088** SmsSender | **P1** | **用户手动 + jshell + Mock 降级** | ✗ | ✗ | `service/channel/SmsSender.java:46-90` `channelType() = "SMS"` + `:106-124` `resolveConfig()` 按 tenantId + channelType 查 `mkt_channel_config` + 缺凭据时 `demoMode()` 返回 `SMS-DEMO-{ts}` + `:render(template, vars)` 静态方法 `${key}` → vars[key]。**UNVERIFIED(I049 D10 铁律 — 无真实阿里云 accessKey)**。**用户手动**:① jshell `SmsSender.render(template, vars)` 验证占位符替换 3 组(普通/缺占位符/null);② jshell `SmsSender.normalizePhone("+8613500000000")` → 期望 `13500000000`;③ jshell `SmsSender.maskPhone("13500000000")` → 期望 `135****0001`(待 Phase 1 静态确认 mask 算法);④ 启动后端 → 调 BookingConfirmationService 走 SmsSender → 期望 `demoMode=true` + `SMS-DEMO-` 返回 |
| **F089** EmailSender | **P1** | **用户手动 + jshell + Mock 降级** | ✗ | ✗ | `service/channel/EmailSender.java:60-114` `send()` + `:121-167` `doSendSmtp()` + `:178-186` `demoMode()` + `:188-203` `resolveConfig()`。**三模式**:① 租户 cfg + JavaMailSender 已注入 → 真实 SMTP;② JavaMailSender 已注入但无租户 cfg → 全局 `spring.mail.*`;③ JavaMailSender 未注入或 mismatch → demo 模式 + 日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode`(SoT §0.2 完全一致)。**UNVERIFIED(I049 D10 铁律 — 无真实 SMTP 凭据)**。**用户手动**:① 启动后端 → 走 mismatch demo 模式 → 看日志关键词命中;② jshell `EmailSender.demoMode(to, subject, body)` 验证 demo 模式返回 success;③ jshell mismatch 检测边界(`globalMatch = globalHost.equals(smtpHost) ∧ globalUsername.equals(username)`)|
| **F090** MiniProgramSender | **P1** | **用户手动 + jshell + Mock 降级** | ✗ | ✗ | `service/channel/MiniProgramSender.java:48-100` `send()` + `:107-128` `getAccessToken(tenantId, appId, appSecret)` + `:135-163` `buildSubscribeData()`。**AccessToken 内存缓存**:`ConcurrentHashMap<Long, TokenCache>` + `TOKEN_CACHE_MS = 7100_000L`(提前 10 分钟刷新 2 小时 token)+ errcode=40001 自动清缓存。**`MP_TEMPLATE_ID = "BOOKING_CONFIRMATION"` 是占位**(SoT §0.1)。**UNVERIFIED(I049 D10 铁律 — 无真实 appId/appSecret)**。**用户手动**:① 启动后端 → 走 demo / 失败路径(无凭据)→ 日志验证;② jshell `MiniProgramSender.buildSubscribeData(variables)` 验证 keyword1..keyword5 生成;③ jshell AccessToken 缓存边界(2 小时 + errcode=40001 清缓存)|
| **F091** InAppSender | **P1** | **用户手动 + DB-only + Static-Only** | ✗ | ✓ | `service/channel/InAppSender.java:42-87` `send()` + `@Transactional(rollbackFor = Exception.class)` L43 + 写 `member_message`。**MISSING(I045 I002 模式 — db-snapshot.sql grep member_message 0 匹配已确认)**。**用户手动**:① `mysql -e "DESCRIBE member_message"` → 期望 `Table 'meiyetec.member_message' doesn't exist`(I045 MISSING 实证);② DDL 文件存在性 `find . -name "*member_message*.sql"` 验证;③ Phase 5 验证:`BookingConfirmationService.sendConfirmation` **未路由 IN_APP**(与 SoT §4 一致,SoT 仅明确 3 路由 SMS/EMAIL/MINI_PROGRAM)|
| **F092** WechatOfficialAccountSender | **P1** | **用户手动 + jshell + Mock 降级** | ✗ | ✗ | `service/channel/WechatOfficialAccountSender.java:53-113` `send()` + `:120-141` `getAccessToken()` + `:148-184` `buildTemplateData()`。**`channelType() = "WECHAT_OFFICIAL_ACCOUNT"`(非 WECHAT_OA — 见 I039 中立标 SoT 反向别名)**。AccessToken 缓存与 F090 同模式;模板消息用 `first/keyword1..N` 格式(最多 10 个 keyword)。**UNVERIFIED(I049 D10 铁律 — 无真实 appId/appSecret)**。**用户手动**:① 启动后端 → 走 demo / 失败路径 → 日志验证;② jshell `WechatOfficialAccountSender.buildTemplateData(variables)` 验证 first/keyword1..keywordN 生成;③ **Phase 5 实测 channelType() = "WECHAT_OFFICIAL_ACCOUNT" 而非 "WECHAT_OA"**(I039 SoT 反向别名实证)|
| **F093** ChannelSender 接口契约 | **P1** | **Static-Only** | ✗ | ✗ | `service/channel/ChannelSender.java:9-37` 接口 — 3 方法 `channelType()` / `send(MktChannelTemplate, String, Map)` / `healthCheck()` + `SendResult` 内嵌类 `{success, externalMsgId, errorMsg}` + `static ok(externalMsgId)` / `static fail(msg)` 工厂。L0 静态可达 + 接口契约 OK。**预期 L0 PASS**(无需运行时验证) |
| **F094** ChannelDispatcher | **P1** | **用户手动 + Static-Only** | ✗ | ✗ | `service/channel/ChannelDispatcher.java:34-41` `getSender(channelType)` + `:53-73` `healthCheckAll()`(仅更新 tenant_id=1 — T07-D4 例外声明)+ `:78-90` `rateLimitCheck()`。**PARTIAL-NON-BLOCKER(I050 — T07-D4 例外声明)**。**用户手动**:① 启动后端 → 调 `dispatcher.healthCheckAll()` → 期望更新 `mkt_channel_config.last_health_at`(需 mkt_channel_config 表存在,可能 MISSING 暴露);② jshell `dispatcher.getSender("SMS")` / `("EMAIL")` / `("MINI_PROGRAM")` / `("IN_APP")` / `("WECHAT_OFFICIAL_ACCOUNT")` 全部返回非 null;③ `getSender("INVALID")` 返回 null |
| **F095** POST /intl-booking/{id}/send-confirmation | **P1** | **用户手动 + curl** | ✗ | ✗ | `service/IntlBookingService.sendConfirmation(@PathVariable Long id)` 独立手动触发方法。**Shadow(I042 — 前端 intl-booking.ts 无 sendConfirmation 方法)**。**用户手动**:① 启动后端;② `curl POST /intl-booking/{id}/send-confirmation` → 期望 I038 MISSING 暴露 `TableNotFoundException`(booking 不存在);③ 任意 id → 期望 404 NOT_FOUND |
| **F099** POST /intl-booking/webhook/google | **P1** | **用户手动 + Mock 推送样本** | ✗ | ✗ | `service/IntlBookingService.googleWebhook(payload, token)` P3-26 演示模式。**PARTIAL-NON-BLOCKER(I048 I009 rollbackFor 模式 + I051 webhook demo 模式)**。**用户手动 Mock 推送**:① 构造 Google BP payload `{"notificationId":"test-001","customerName":"Alice","customerPhone":"+8613500000000","customerEmail":"alice@test.com","locale":"en-US","serviceName":"Facial","bookingTime":"2026-10-01T14:00"}` + token=`ya29.DEMO-test`;② `curl -X POST http://localhost:8080/intl-booking/webhook/google -d '{payload}'` → 期望 I038 MISSING 暴露 500(overseas_booking 表不存在);③ 期望演示模式 token → tenantId 反查返回 null 兜底 1L |
| **F100** POST /intl-booking/webhook/instagram | **P1** | **用户手动 + Mock 推送样本** | ✗ | ✗ | `service/IntlBookingService.instagramWebhook(payload)` P3-26 演示模式。**PARTIAL-NON-BLOCKER(I048 + I051)**。**用户手动 Mock 推送**:① 构造 IG payload `entry[0].messaging[0].message.text="Book facial tomorrow"` + sender.id="IGQVJ-test";② `curl -X POST http://localhost:8080/intl-booking/webhook/instagram -d '{payload}'` → 期望 I038 MISSING 暴露 500 + 简易 NLP 匹配 facial;③ tenantId 演示模式硬编码 1L |
| **F101** GET /intl-booking/webhook/instagram 验证握手 | **P1** | **用户手动 + curl** | ✗ | ✗ | `service/IntlBookingService.instagramWebhookVerify(mode, verifyToken, challenge)` 返回 hub.challenge 完成 Meta webhook 验证握手。**演示模式**:mode=subscribe + challenge 非空即返回。**用户手动**:`curl "http://localhost:8080/intl-booking/webhook/instagram?hub.mode=subscribe&hub.verify_token=test&hub.challenge=CHALLENGE_12345"` → 期望返回 `CHALLENGE_12345`;生产需 `verifyToken` 比对(SoT §3 未明确,本批次不实现) |
| **F102** Google / Instagram OAuth2 connect + callback | **P1** | **用户手动 + curl** | ✗ | ✗ | `service/IntlBookingService.connectGoogle/body` + `googleCallback/body` + `connectInstagram/body` + `instagramCallback/body`。**演示模式**:返回真实 OAuth URL(`accounts.google.com/o/oauth2/v2/auth` + `facebook.com/v18.0/dialog/oauth`)但 callback 不真换 token,返回 `ya29.DEMO-{code前8位}`。**PARTIAL-NON-BLOCKER(I051)**。**用户手动**:① `curl POST /intl-booking/connect/google -d '{"redirectUri":"http://localhost/cb"}'` → 期望 `{authUrl: "https://accounts.google.com/o/oauth2/v2/auth?..."}`;② `curl POST /intl-booking/callback/google -d '{"code":"test123"}'` → 期望 `{token: "ya29.DEMO-test123"}`(演示模式)|
| **F103** POST /intl-booking/{id}/convert 转化为会员 + 订单 | **P1** | **用户手动 + curl** | ✗ | ✗ | `service/IntlBookingService.convertBooking(id)` + `doCreateMember` + `doCreatePosOrder`。**演示模式**:JdbcTemplate 直接 INSERT `crm_member` + `pos_order`(避免引入 MemberService 复杂依赖)+ 写回 `convertedMemberId/convertedOrderId` + `@Transactional`(默认 rollbackFor 仅 RuntimeException — I048 模式)。**PARTIAL-NON-BLOCKER(I048)**。**用户手动**:① 启动后端;② `curl POST /intl-booking/{id}/convert` → 期望 I038 MISSING 暴露 500(booking 不存在);③ 验证 `crm_member` + `pos_order` 表存在 |
| **F108** BookingConfirmationServiceTest | **P1** | **mvn test** | ✗ | ✗ | `test/booking/BookingConfirmationServiceTest.java` JUnit5 — StubSms/Email + Mockito StoreLookup。测试点:渠道路由 + overallSuccess 聚合 + null booking 安全 + ChannelSender 缺失降级 + 门店信息读取。**用户手动**:`cd backend && mvn -Dtest=BookingConfirmationServiceTest test` → 期望全部 PASS |
| **F109** BookingConfirmationTemplateResolverTest | **P1** | **mvn test** | ✗ | ✗ | `test/booking/BookingConfirmationTemplateResolverTest.java` JUnit5。测试点:normalize(zh_CN → zh-CN, lang 小写 / country 大写)+ 回退链(zh-TW → zh-CN → en-US)+ 占位符 ${} 渲染 + 未注册 locale → en-US。**用户手动**:`mvn -Dtest=BookingConfirmationTemplateResolverTest test` → 期望全部 PASS |

---

## 三、P2 — Controller REST 端点 + Entity + 配置 + 命名异味(9 个 Feature)

| Feature ID | Priority | 验证方式 | 是否需 E2E | 是否需 DB 断言 | 理由 |
|---|---|---|---|---|---|
| **F097** GET /intl-booking/{id}/confirmation-template | **P2** | **用户手动 + curl** | ✗ | ✗ | `service/IntlBookingService.confirmationTemplate(id)` 控制器内硬编码语言 switcher(zh/zh-cn/zh-tw/ja/ko/default)6 分支,返回仅 subject + body(缺 sms/miniProgram/email 4 段 — 与 BookingConfirmationTemplate DTO 反向)。**CONFLICT(I043 中立标 — SoT §3 期望走 Resolver + 6 locale JSON;实际硬编码 switcher)**。**用户手动**:① 启动后端;② `curl GET /intl-booking/{id}/confirmation-template?lang=zh-CN` → 期望 I038 MISSING 暴露 500;③ 任意 id → 期望返回硬编码中文模板(I043 中立标实证)|
| **F098** GET /intl-booking/list 列表查询 | **P2** | **用户手动 + curl** | ✗ | ✗ | `service/IntlBookingService.listBookings(status?, source?)` LambdaQueryWrapper 按 tenantId 过滤 + id DESC + 可选 status/source 过滤。**MISSING(I038 间接)**。**用户手动**:① 启动后端;② `curl GET /intl-booking/list` → 期望 I038 MISSING 暴露 500 |
| **F104** GET /intl-booking/{id} 单条查询 | **P2** | **用户手动 + curl** | ✗ | ✗ | `service/IntlBookingService.getBooking(id)` selectById + null → BizCode.NOT_FOUND。**MISSING(I038 间接)**。**用户手动**:① `curl GET /intl-booking/{id}` → 期望 I038 MISSING 暴露 500;② 任意 id → 期望 404 NOT_FOUND |
| **F105** GET /intl-booking/channels 静态渠道列表 | **P2** | **用户手动 + curl** | ✗ | ✗ | `service/IntlBookingService.listChannels()` 4 渠道枚举(GOOGLE/INSTAGRAM/FACEBOOK/TIKTOK — FACEBOOK/TIKTOK status=PLANNED)。纯静态枚举无 DB 查询。L0 静态可达。**用户手动**:`curl GET /intl-booking/channels` → 期望 `200 + [{code:"GOOGLE", name, status: "ACTIVE"}, ...]` |
| **F106** POST /intl-booking/{channelId}/pull 拉取预约 | **P2** | **用户手动 + curl** | ✗ | ✗ | `service/IntlBookingService.pullBookings(channelId)` 演示模式。GOOGLE/INSTAGRAM 插 1 条示例记录(ExternalId="EXT-{ts}");FACEBOOK/TIKTOK 返回 BizCode.NOT_FOUND。**PARTIAL-NON-BLOCKER(I051 演示模式)**。**用户手动**:① `curl POST /intl-booking/GOOGLE/pull` → 期望 I038 MISSING 暴露 500;② `curl POST /intl-booking/FACEBOOK/pull` → 期望 404 NOT_FOUND |
| **F110** EmailSenderTest + WechatOfficialAccountSenderTest | **P2** | **mvn test** | ✗ | ✗ | `test/service/channel/EmailSenderTest.java` 6 测试点(channelType/receiver 校验/demo 模式/healthCheck)+ `WechatOfficialAccountSenderTest.java` 同模式。**Phase 1 新发现 4 测试存在**。**用户手动**:`mvn -Dtest='*SenderTest' test` → 期望全部 PASS |
| **F111** intl-booking/index.vue | **P2** | **Playwright E2E 降级** | ✓ | ✗ | `views/intl-booking/index.vue:1-150+` 2 tab("渠道连接" + "国际预约")+ 3 对话框(Google OAuth / Instagram OAuth / 模板预览)。**Shadow(I040 + I041 — 状态确认按钮无 loading / 无二次确认 / 无"确认即发送通知"文案提示;无 autoSend=false UI)**。**用户手动**:① 打开 `/intl-booking` 页面 → 应有 2 tab;② "国际预约" tab 操作列"确认"按钮(无文案提示);③ 添加 autoSend=false UI 缺失(I040 实证)。**降级**:Playwright MCP 未配置 → 用户本地跑 Playwright 脚本 |
| **F112** intl-booking.ts(12 端点封装) | **P2** | **Static-Only** | ✗ | ✗ | `api/intl-booking.ts:1-31` 12 方法。**Shadow(I042 — `sendConfirmation` 方法缺失,后端 POST `/intl-booking/{id}/send-confirmation` 端点存在但前端无对应入口)**。**用户手动**:① `grep sendConfirmation frontend/meiyetec-web/src/api/intl-booking.ts` → 期望 0 命中(I042 实证);② 对照 `F112 12 端点封装表`验证方法完整性 |
| **F113** IntlBookingService 命名异味 | **P2** | **Static-Only** | ✗ | ✗ | `service/IntlBookingService.java:14-17` `@RestController @RequestMapping("/intl-booking")` 但文件名是 `*Service` — **第 4 次出现**(沿用 Batch 1.1 I004 / 1.2 I019 / 1.3 I029 模式)。**不阻塞 Phase 2**,记入后续重构 backlog(I044)。Phase 7 Post-Audit 统一整改 |

---

## 四、预期负向证据清单(I038-I051 实证预期)

> 按 Phase 3 静态分析结论 + D21-D28 决策,下列负向证据是 Phase 5 必暴露的(验证审计结论准确性):

| # | 验证目标 | 预期负向证据 | Phase 5 操作 | Issue 关联 |
|---|---|---|---|---|
| **N1** | `overseas_booking` 表不存在 | `TableNotFoundException` 或 `Table 'meiyetec.overseas_booking' doesn't exist` | `mysql -e "DESCRIBE overseas_booking"` | **I038**(F081 / F082 / F095-F106)|
| **N2** | `member_message` 表不存在 | `TableNotFoundException` 或 `Table 'meiyetec.member_message' doesn't exist` | `mysql -e "DESCRIBE member_message"` | **I045**(F091)|
| **N3** | DB snapshot 未含 EXT-19 迁移 | `grep` 0 命中 `overseas_booking` / `member_message` / `mini_program_open_id` | `grep overseas_booking db-snapshot.sql` 等 | **I038 + I045**(F082 / F091)|
| **N4** | `pos_store.store_name` + `contact_phone` 字段对齐 | `DESCRIBE pos_store` 应见 2 字段(表存在)| `mysql -e "DESCRIBE pos_store"` | **I046**(F087)|
| **N5** | i18n warmup 6 locale 启动期执行 | 启动日志 `[I18N-TPL] warmup done, locales=[zh-CN, zh-TW, en-US, ja-JP, ko-KR, fr-FR]` | 启动后端 + grep 启动日志 | **OK**(F084)|
| **N6** | i18n locale 回退链(xx-YY → xx → en-US) | jshell `resolver.resolve("xx-YY", vars)` → `usedFallback=true` | jshell 直接调用 | **OK**(F084)|
| **N7** | i18n 占位符 `${key}` 替换 | jshell `SmsSender.render(template, vars)` 3 组(普通/缺占位符/null)| jshell 直接调用 | **OK**(F088)|
| **N8** | EmailSender mismatch demo 模式 | 启动日志 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode` | 启动后端(无凭据)+ grep 日志 | **OK**(F089 + SoT §0.2 完全一致)|
| **N9** | EmailSender 三模式降级(真实 SMTP / 全局 spring.mail.* / demo) | jshell `EmailSender.demoMode()` + mismatch 检测边界 | jshell 直接调用 | **OK**(F089)|
| **N10** | MiniProgramSender `MP_TEMPLATE_ID = "BOOKING_CONFIRMATION"` 是占位 | 启动后端走无凭据路径 → 日志验证 + 真实 API 调用预期返回 40037 | 启动后端 + grep 日志 | **OK**(F090 + SoT §0.1)|
| **N11** | WechatOfficialAccountSender channelType 别名 | jshell `sender.channelType()` → 期望 `"WECHAT_OFFICIAL_ACCOUNT"` 而非 `"WECHAT_OA"`(I039 实证)| jshell 直接调用 | **I039**(F092)|
| **N12** | autoSend 自动触发(CONFIRMED → sendConfirmation)| `curl POST /intl-booking/{id}/status -d '{"status":"CONFIRMED"}'` → 期望调用 sendConfirmation(虽然 I038 MISSING 暴露 500,但 try/catch 内 I047 实证)| curl 调用 | **I047 + I048**(F096)|
| **N13** | autoSend=false 关闭路径 | `curl POST /intl-booking/{id}/status -d '{"status":"CONFIRMED","autoSend":false}'` → 不触发 sendConfirmation(虽然 I038 MISSING 暴露)| curl 调用 | **I047**(F096)|
| **N14** | confirmationTemplate 端点硬编码 switcher | `curl GET /intl-booking/{id}/confirmation-template` → 期望返回硬编码模板(走 switcher,不走 Resolver)— I043 实证 | curl 调用 | **I043**(F097)|
| **N15** | Google webhook 演示模式 | Mock 推送 Google BP payload → 期望 I038 MISSING 暴露 500 + token → tenantId 反查返回 null 兜底 1L(I051 实证)| Mock 推送 + curl | **I048 + I051**(F099)|
| **N16** | Instagram webhook 演示模式 | Mock 推送 IG payload → 期望 I038 MISSING 暴露 500 + 简易 NLP 匹配 facial | Mock 推送 + curl | **I048 + I051**(F100)|
| **N17** | Instagram webhook 验证握手 | `curl "/intl-booking/webhook/instagram?hub.mode=subscribe&hub.verify_token=test&hub.challenge=CHALLENGE"` → 期望返回 `CHALLENGE` | curl 调用 | **OK**(F101 演示模式)|
| **N18** | OAuth2 connect 返回真实 URL | `curl POST /intl-booking/connect/google` → 期望 `{authUrl: "https://accounts.google.com/..."}` | curl 调用 | **I051**(F102)|
| **N19** | OAuth2 callback 返回 demo token | `curl POST /intl-booking/callback/google -d '{"code":"test123"}'` → 期望 `{token: "ya29.DEMO-test123"}`(不真换)| curl 调用 | **I051**(F102)|
| **N20** | convert 转化演示模式 | `curl POST /intl-booking/{id}/convert` → 期望 I038 MISSING 暴露 500 | curl 调用 | **I048**(F103)|
| **N21** | 前端缺 autoSend=false UI | `grep autoSend frontend/meiyetec-web/src/views/intl-booking/index.vue` → 0 命中(I040 实证)| grep | **I040**(F111)|
| **N22** | 前端状态确认按钮文案无"确认即发送通知"提示 | `intl-booking/index.vue` L66 按钮文案"确认" → 无文案提示(I041 实证)| grep 或 Playwright | **I041**(F111)|
| **N23** | 前端 intl-booking.ts 无 sendConfirmation 方法 | `grep sendConfirmation frontend/meiyetec-web/src/api/intl-booking.ts` → 0 命中(I042 实证)| grep | **I042**(F112)|
| **N24** | IntlBookingService 命名异味(第 4 次出现)| 文件 `service/IntlBookingService.java` 含 `@RestController @RequestMapping("/intl-booking")` 但文件名 `*Service` | grep | **I044**(F113)|
| **N25** | ChannelDispatcher healthCheckAll 仅更新 tenant_id=1 | `ChannelDispatcher.java:53-73` 静态确认 `UPDATE ... WHERE tenant_id=1` 硬编码(I050 实证)| grep | **I050**(F094)|

---

## 五、需要用户手动执行的清单(EXT-19 相关)

> Cursor 不能跑 JVM 启动后端 / 不能连接 WeChat MP / 不能连 SMTP → 下列 Feature 在 Phase 5 必须由用户在本地执行。

### 5.1 P0 必跑(DB MISSING + 核心发送 + i18n warmup)

| Feature | 用户手动步骤 | 失败降级 |
|---|---|---|
| **F081 / F082** overseas_booking 表 MISSING | ① `mysql -e "DESCRIBE overseas_booking"` → 表缺失;② `grep overseas_booking audit/baseline/db-snapshot.sql` → 0 命中 | 表存在 → I038 不成立 |
| **F083** BookingConfirmationService.sendConfirmation | ① 启动后端 → 看 `[BOOKING-CONFIRMATION]` 启动日志;② `curl POST /intl-booking/{id}/send-confirmation` → 期望 I038 MISSING 暴露 404;③ jshell 模拟 5 channel sender 全部 mock → 期望 overallSuccess=true | 启动失败 → F083 UNVERIFIED |
| **F084** Resolver warmup | ① 启动后端 → 看 `[I18N-TPL] warmup done, locales=[6]`;② jshell 验证 6 locale load + normalize + fallback | warmup 失败 → F084 PARTIAL-BLOCKER |
| **F085 / F086** DTO | jshell 验证 `new BookingConfirmationTemplate(...)` + `putChannel(channel, success=true)` 累加 | L0 静态可达 |
| **F096** updateStatus + autoSend | 启动后端 → curl POST status=CONFIRMED(自动触发) + status=CONFIRMED, autoSend=false(关闭)| I038 MISSING 暴露 500 + I047 实证 |
| **F107** 6 locale JSON | `ls resources/i18n/template/*.json` → 期望 6 文件;jshell 验证 6 locale JSON 全部可解析 | 任一缺失 → F107 PARTIAL-BLOCKER |

### 5.2 P1 必跑(Channel Sender + webhook + OAuth2 + 测试)

| Feature | 用户手动步骤 | 失败降级 |
|---|---|---|
| **F087** StoreLookupService | ① `mysql -e "DESCRIBE pos_store"` → 验证 `store_name` + `contact_phone`(I046 实证);② 启动后端 → 调 sendConfirmation 走 StoreLookup | 字段缺失 → F087 PARTIAL-BLOCKER |
| **F088 / F089 / F090 / F092** Channel Sender | ① jshell 单元测试(render / demoMode / buildSubscribeData / buildTemplateData);② 启动后端 → 走 demo / 失败路径 → 日志验证 | 凭据缺失 → UNVERIFIED(D10)|
| **F091** InAppSender MISSING | ① `mysql -e "DESCRIBE member_message"` → 表缺失(I045 实证)| 表存在 → I045 不成立 |
| **F093 / F094** Channel Sender 接口 + Dispatcher | jshell `dispatcher.getSender("SMS"/"EMAIL"/...)` → 全部返回非 null + healthCheckAll | 启动失败 → UNVERIFIED |
| **F095** send-confirmation 端点 | `curl POST /intl-booking/{id}/send-confirmation` → 期望 I038 MISSING 暴露 404 | 启动失败 → UNVERIFIED |
| **F099 / F100** webhook 演示模式 | Mock 推送 Google BP / IG payload → 期望 I038 MISSING 暴露 500 + I051 实证 | 启动失败 → UNVERIFIED |
| **F101** webhook 验证握手 | curl GET /intl-booking/webhook/instagram?... → 期望返回 challenge | 启动失败 → UNVERIFIED |
| **F102** OAuth2 connect + callback | curl POST /intl-booking/connect/google → 期望 authUrl;callback → ya29.DEMO-{code} | 启动失败 → UNVERIFIED |
| **F103** convert 转化 | curl POST /intl-booking/{id}/convert → 期望 I038 MISSING 暴露 500 | 启动失败 → UNVERIFIED |
| **F108 / F109** BookingConfirmationService + Resolver 测试 | `cd backend && mvn -Dtest=BookingConfirmationServiceTest,BookingConfirmationTemplateResolverTest test` → 期望全部 PASS | 失败 → F108/F109 PARTIAL-BLOCKER |

### 5.3 P2 部分必跑(列表 / 单条 / 渠道列表 / 拉取 / 测试 / 前端)

| Feature | 用户手动步骤 | 失败降级 |
|---|---|---|
| **F097** confirmationTemplate 端点 | curl GET → 期望 I038 MISSING 暴露 500 + I043 硬编码 switcher 实证 | 启动失败 → UNVERIFIED |
| **F098 / F104 / F105 / F106** 列表 / 单条 / 渠道 / 拉取 | curl 各端点 → 期望 I038 MISSING 暴露 500 / 404 / 200 | 启动失败 → UNVERIFIED |
| **F110** Sender 测试 | `mvn -Dtest='*SenderTest' test` → 期望全部 PASS | 失败 → F110 PARTIAL-BLOCKER |
| **F111 / F112** 前端 Shadow | grep `autoSend` + `sendConfirmation` → 期望 0 命中(I040/I041/I042 实证);Playwright 跑脚本(可选) | grep 命中 → I040/I041/I042 不成立 |
| **F113** 命名异味 | grep `service/IntlBookingService.java` `@RestController` → 期望命中(I044 实证)| 文件改名 → I044 不成立 |

---

## 六、Phase 4 → Phase 5 入口守门

| 检查项 | 状态 | 备注 |
|---|---|---|
| 33 Feature Phase 4 计划已落 | ✅ | 本文件 |
| P0/P1/P2 优先级已分布 | ✅ | P0=7 / P1=15 / P2=9 + P3=2 |
| 验证方式已指定(API-only / DB-only / jshell / Mock 推送 / Static-Only)| ✅ | 每 Feature 1 个主方式 |
| 预期负向证据清单已建(N1-N25)| ✅ | 关联 I038-I051 |
| 用户手动执行清单已建(5.1/5.2/5.3)| ✅ | 含失败降级 UNVERIFIED 策略 |
| D21-D28 决策已落实 | ✅ | 全部统一为「审计阶段只记录不修复」 |
| I038-I051 决策已落档到 feature-inventory.md §1.4.4 | ✅ | 14 项 Issue(全部 Phase 1 登记,无矛盾)|
| Feature 矩阵覆盖 F081-F113 | ✅ | 33 Feature 完整 |
| **矛盾核实** | ✅ | **I038-I051 共 14 个 Issue 全部由 Phase 1 已登记**(`audit/feature-inventory.md` §1.4.4 行号 615-627) |
| 不动代码 | ✅ | 仅 Phase 4 计划生成 |
| 不跑迁移 | ✅ | D21/D22 决策:维持 MISSING,不跑迁移 |
| 不动 SoT | ✅ | D24 决策:I039/I043 中立标,不动 `ext-19-operational-notes.mdc` |
| Frontend Shadow 暂缓 | ✅ | D23 决策:I040/I041/I042 暂缓,Post-Audit |
| I047/I048 I008/I009 模式暂缓 | ✅ | D26 决策 |
| I049 D10 铁律 | ✅ | D27 决策:Mock 降级 + UNVERIFIED |
| I050/I051 维持现状 | ✅ | D28 决策:演示模式足够联调 |
| 不进 Phase 5 | ✅ | 等用户确认本计划 → Phase 5 执行 |
| 跳过 Phase 3.5 | ✅ | Batch 1.4 非重构批次 |
| 受保护页面零风险 | ✅ | EXT-19 与 `/dashboard` / `/mh/visual` / `/mh/consent` 后端接口零交集 |

---

## 七、Phase 5A/5B/5C 分批计划

> 按用户决策,可拆分为 3 个子阶段执行,每阶段独立产出 evidence + 报告。

### 7.1 Phase 5A — DB-only + 静态验证(不依赖凭据 / 不依赖后端启动)

| 任务 | 验证目标 | 预期结果 | 关联 Issue |
|---|---|---|---|
| **5A.1** | `mysql -e "DESCRIBE overseas_booking"` | 表缺失(I038 实证)| I038 |
| **5A.2** | `mysql -e "DESCRIBE member_message"` | 表缺失(I045 实证)| I045 |
| **5A.3** | `mysql -e "DESCRIBE pos_store"` | 表存在 + `store_name` + `contact_phone` 字段对齐(I046 实证)| I046 |
| **5A.4** | `grep overseas_booking db-snapshot.sql` + `grep member_message db-snapshot.sql` | 0 命中(I038 + I045 实证)| I038 + I045 |
| **5A.5** | `ls backend/src/main/resources/i18n/template/*.json` | 6 locale 文件 | OK |
| **5A.6** | `grep autoSend frontend/meiyetec-web/src/views/intl-booking/index.vue` | 0 命中(I040 实证)| I040 |
| **5A.7** | `grep sendConfirmation frontend/meiyetec-web/src/api/intl-booking.ts` | 0 命中(I042 实证)| I042 |
| **5A.8** | `grep -n "@RestController" backend/src/main/java/com/meiyetec/service/IntlBookingService.java` | 命中(I044 实证)| I044 |

### 7.2 Phase 5B — API + Mock + jshell 单元测试(需后端启动)

| 任务 | 验证目标 | 预期结果 | 关联 Issue |
|---|---|---|---|
| **5B.1** | 启动后端 → 看 `[I18N-TPL] warmup done, locales=[6]` | warmup 6 locale OK | OK |
| **5B.2** | `curl GET /intl-booking/list` | I038 MISSING 暴露 500 | I038 |
| **5B.3** | `curl POST /intl-booking/{id}/send-confirmation` | I038 MISSING 暴露 404 | I038 |
| **5B.4** | `curl POST /intl-booking/{id}/status -d '{"status":"CONFIRMED"}'` | I038 MISSING 暴露 500 + I047 实证 | I038 + I047 |
| **5B.5** | `curl POST /intl-booking/{id}/status -d '{"status":"CONFIRMED","autoSend":false}'` | 不触发 sendConfirmation(I047 实证)| I047 |
| **5B.6** | Mock 推送 Google webhook → `curl POST /intl-booking/webhook/google` | I038 MISSING 暴露 500 + I051 演示模式 | I038 + I051 |
| **5B.7** | Mock 推送 IG webhook → `curl POST /intl-booking/webhook/instagram` | I038 MISSING 暴露 500 + 简易 NLP 匹配 | I038 + I051 |
| **5B.8** | jshell `SmsSender.render(template, vars)` 3 组 | 占位符替换 OK | OK |
| **5B.9** | jshell `EmailSender.demoMode(...)` | demo 模式 OK | OK |
| **5B.10** | jshell `MiniProgramSender.buildSubscribeData(variables)` | keyword1..5 生成 OK | OK |
| **5B.11** | jshell `WechatOfficialAccountSender.channelType()` | 返回 `"WECHAT_OFFICIAL_ACCOUNT"` 而非 `"WECHAT_OA"`(I039 实证)| I039 |
| **5B.12** | jshell `resolver.warmup()` + `resolver.loadLocale("zh-CN")` | 6 locale OK + 4 段字段完整 | OK |
| **5B.13** | jshell `resolver.resolve("xx-YY", vars)` | `usedFallback=true`(走 fallback → en-US)| OK |
| **5B.14** | `mvn -Dtest=BookingConfirmationServiceTest,BookingConfirmationTemplateResolverTest,'*SenderTest' test` | 全部 PASS | OK |

### 7.3 Phase 5C — Frontend Shadow + Playwright 降级(可选)

| 任务 | 验证目标 | 预期结果 | 关联 Issue |
|---|---|---|---|
| **5C.1** | Playwright 跑 `intl-booking/index.vue` 2 tab + 3 对话框 | 2 tab OK + 3 对话框 OK | OK |
| **5C.2** | Playwright 验证状态确认按钮无 loading / 无二次确认 / 无文案提示 | I041 实证 | I041 |
| **5C.3** | Playwright 验证 autoSend=false UI 缺失 | I040 实证 | I040 |
| **5C.4** | Playwright 验证 intl-booking.ts 无 sendConfirmation 方法 | I042 实证 | I042 |

---

## 八、Phase 4 → Phase 5 衔接说明

> 本文件仅生成 **验证策略**,不执行任何运行时操作。
>
> **Phase 5 启动条件**(按用户决策):
> 1. 用户确认本计划(P0/P1/P2 分布 + D21-D28 落实 + 矛盾核实通过)
> 2. 用户启动后端(Spring Boot + MySQL + overseas_booking 表**未创建** — 按 I038 D21 决策)
> 3. 用户跑 curl + mysql CLI + jshell 验证
> 4. 用户跑 Playwright 脚本(前端 E2E 降级,可选)
> 5. 用户跑 Mock 推送 webhook 样本(验证 F099 / F100)
> 6. WeChat MP / SMTP 真实链路凭据缺失 → **UNVERIFIED**(D10 铁律 / I049)
>
> **Phase 5 输出物**:
> - `audit/evidence/F081-F113/<slug>/result.md`(每个 Feature 1 份)
> - `audit/runtime/report-5A-batch-1.4.md` + `report-5B-batch-1.4.md` + `report-5C-batch-1.4.md`(汇总 33 Feature Phase 5 结果 + 负向证据清单)
> - Issue Register 更新(I038-I051 实证状态)
>
> **失败降级铁律(D10)**:
> - WeChat MP / SMTP 凭据缺失 → F088 / F089 / F090 / F092 全部 UNVERIFIED
> - 后端启动失败(I038 MISSING 暴露 `TableNotFoundException`)→ F081 / F082 / F095-F106 全部 PARTIAL-BLOCKER / MISSING 维持
> - 任何 Phase 5 操作**不造证据**(不写假 result.md)

---

## 九、附录 — Batch 1.4 vs Batch 1.1/1.2/1.3 Phase 4 对照

| 维度 | Batch 1.1 (CRM) | Batch 1.2 (Plugin Runtime) | Batch 1.3 (EXT-22) | **Batch 1.4 (EXT-19)** |
|---|---|---|---|---|
| Feature 数 | 30 | 25 | 25 | **33** |
| P0 必测 | 7 | 6 | 7 | **7** |
| P1 | 15 | 5 | 9 | **15** |
| P2 | 8 | 14 | 9 | **9** |
| P3 | 0 | 0 | 0 | **2** |
| pf4j 加载 | N/A | **Cursor 不能跑** → 用户手动 | N/A | N/A |
| WeChat MP 真实链路 | N/A | N/A | N/A | **凭据缺失** → Mock 降级 |
| SMTP 真实链路 | N/A | N/A | N/A | **凭据缺失** → mismatch demo 模式 |
| GCP 真实链路 | N/A | N/A | **凭据缺失** → Mock 降级 | N/A |
| 有赞 push_url | N/A | N/A | **未注册** → Mock 推送样本 | N/A |
| Google / IG webhook | N/A | N/A | N/A | **演示模式** → Mock 推送样本(I051)|
| Frontend E2E | Playwright E2E 降级 | 同 | 同 | **同 + 2 Shadow Feature 静态实证** |
| DB 缺表 | `crm_contact_map`(F023)+ `crm_integration` 缺 9 列(I002)| `plugin_runtime_state`(F049)+ 9 vs 8 字段差异(F051)| `google_maps_config` / `google_maps_geocode_log` / `ecommerce_platform` 3 张表全 MISSING(I029)| **`overseas_booking`(F081/F082)+ `member_message`(F091)+ `pos_store` 字段对齐(I046)** |
| 核心决策 | D5 不跑 migration | D7 不跑 migration | D13 不跑 migration | **D21 + D22 不跑 migration**(沿用 I002/I017/I029 模式) |
| SoT 漂移 | I001 HubSpot/Zoho 误标"未实现" | I018 assertTenantAccess 误标"无校验" | I026 / I027 / I030 三项 EXT-22 SoT 描述反向 | **I039(WECHAT_OA 别名)+ I043(confirmationTemplate 硬编码 switcher)**|
| 合作式限制 | N/A | I020 沙箱不拦截 service.* | N/A | N/A |
| 副作用 | N/A | I022 disable 清 lastError | I035 webhook 幂等无时间窗 | **I047 catch 吞(I008 模式)+ I048 rollbackFor(I009 模式)**|
| 幽灵记录 | N/A | I023 install 不 load → NPE | N/A | **I051 webhook 演示模式**(token → tenantId 反查返回 null 兜底 1L)|
| 命名异味 | I004 Service as Controller | I019 PluginService as Controller | (沿用 I004/I019,未登记新 Issue)| **I044 IntlBookingService as Controller(第 4 次出现)** |
| 加 rollbackFor | N/A | N/A | I034 webhook 事务未指定 rollbackFor | **I048 IntlBookingService 4 端点未指定 rollbackFor**(I009 模式)|
| DB 缺列 API 暴露 | N/A | N/A | I036 GCP API Key `getConfig` 端点泄露 | N/A |
| 孤儿工具 | N/A | I025 deniedPackages | I033 buildWebhookUrl | N/A |
| Frontend Shadow | I006 dashboard tab + I007 update UI | I024 前端无 runtime tab | I028 EXT-22 子项(缺 5 字段 UI)| **I040 autoSend=false UI + I041 按钮文案 + I042 sendConfirmation 方法缺失** |
| 凭据缺失 | N/A | N/A | D10 GCP / 有赞 | **D10 WeChat MP / SMTP(I049)** |
| 死代码 | N/A | N/A | I033 buildWebhookUrl | **I050 ChannelDispatcher.healthCheckAll 仅更新 tenant_id=1(T07-D4 例外)** |

> **沿用经验**:
> - Batch 1.1 D5「不跑 migration 保留 PARTIAL-BLOCKER」 → Batch 1.2 D7 → Batch 1.3 D13 → **Batch 1.4 D21 + D22 同模式**
> - Batch 1.1 I001「保留 CONFLICT 不动 .mdc」 → Batch 1.2 I018 → Batch 1.3 D11 → **Batch 1.4 D24 同模式(2 项 CONFLICT 中立标)**
> - Batch 1.1 04-script false Shadow → Batch 1.2 已用 04 重跑 → Batch 1.3 同 → **Batch 1.4 同(2 项 Frontend Shadow 静态实证)**
> - Batch 1.1 受保护页面零风险 → Batch 1.2 同 → Batch 1.3 同 → **Batch 1.4 同(EXT-19 与 3 受保护页面零交集)**
> - Batch 1.1/1.2/1.3 D10「凭据缺失 → UNVERIFIED」 → **Batch 1.4 D27 同模式(WeChat MP / SMTP)**

---

## 十、不动业务代码清单(规则 6 硬性约束 — Phase 5 用户手动)

> **Phase 5 用户手动操作时严格禁止**:

| 禁止项 | 原因 | 替代方案 |
|---|---|---|
| **跑 `migrate_ext_19_mini_program_openid.sql`** | D21 决策:统一 Post-Audit 跑迁移 | 仅 DESCRIBE 验证缺失 |
| **跑 `member_message` DDL** | D22 决策:同 D21 模式 | 仅 DESCRIBE 验证缺失 |
| **修改 `ext-19-operational-notes.mdc`** | D24 决策:I039/I043 中立标 | 不改 SoT |
| **修改前端 `intl-booking/index.vue` / `intl-booking.ts`** | D23 决策:I040/I041/I042 暂缓 Post-Audit | 仅 grep 实证 |
| **修改 `IntlBookingService` 4 处 `@Transactional` rollbackFor** | D26 决策:I048 暂缓 | 不动事务边界 |
| **修改 `EmailSender` / `MiniProgramSender` / `WechatOfficialAccountSender` 真实链路** | D27 决策:凭据缺失维持 UNVERIFIED | 仅 jshell 单元测试 |
| **修改 OAuth2 callback 真实 token 交换 / webhook 真实订阅** | D28 决策:I051 维持现状 | 仅 Mock 推送样本 |
| **commit / push 任何业务代码** | 规则 6:审计阶段只记录不修复 | 全部改动挂 Post-Audit |

---

**Batch 1.4 Phase 4 完成。33 Feature 验证计划已落档 + 25 项预期负向证据已建 + 14 项 Issue(I038-I051 全部 Phase 1 登记,无矛盾)+ Phase 5A/5B/5C 分批计划 + 10 条不动业务代码硬性约束。⏸ 等用户确认本计划 → 启动 Phase 5A(DB-only + 静态验证)**。
