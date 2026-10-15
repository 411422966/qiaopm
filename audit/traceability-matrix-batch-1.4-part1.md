# Traceability Matrix Appendix — Batch 1.4（EXT-19 多语言预约确认）

> **本附录将被追加到 `audit/traceability-matrix.md` 主文件 Batch 1.4 段**
> 创建时间：2026-09-14 16:11 (UTC+8)
> 范围来源：`audit/baseline/scope-freeze-batch-1.4.md` §2.3 + `audit/baseline/preflight-batch-1.4.md` §七
> SoT：`.cursor/rules/ext-19-operational-notes.mdc`（EXT-19 Operational Notes）
> Feature 数量：**33 个**（F081-F113）
> 用户决策（2026-09-14 16:11）：
> - I038/I045：维持 MISSING，不跑迁移（第 4 次强调）
> - I039/I043：CONFLICT 中立标（沿用 I001/I018/I026/I030 模式）
> - I040/I041/I042：Shadow，Post-Audit 决定
> - I044：backlog 统一整改（同模式第 4 次命名异味）
> - 审计阶段只记录不修复（规则 6）

---

## Batch 1.4 — EXT-19 多语言预约确认（33 Features）

> **范围来源**：`audit/baseline/scope-freeze-batch-1.4.md` §2.3 + `audit/feature-inventory.md` §1.4
> **SoT 引用**：`.cursor/rules/ext-19-operational-notes.mdc`（EXT-19 Operational Notes）
> **本批次起点 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（沿用 Batch 1.2/1.3 freeze commit）
> **DB 实际状态（Phase 2 静态验证）**：
> - `overseas_booking` **不存在**（db-snapshot.sql grep 0 匹配已确认 — `feature-inventory.md §1.4.4 I038`）
> - `member_message` **不存在**（db-snapshot.sql grep 0 匹配已确认 — `feature-inventory.md §1.4.4 I045`）
> - `pos_store` 存在（db-snapshot.sql grep 5 匹配）；`store_name + contact_phone` 字段对齐待 Phase 5 验证（I046）
> - `mkt_channel_config` 存在（5 channel sender 间接依赖）

---

### F081 — 海外预约 / Entity / OverseasBooking（含 miniProgramOpenId）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F081 | `feature-inventory.md` §1.4.2 row 1 |
| 前端路由 | — | (前端通过 API 间接) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | (Entity 层无 Controller) |
| Service | — | (Entity 层无 Service) |
| Mapper | **`OverseasBookingMapper extends BaseMapper<OverseasBooking>`** | `backend/src/main/java/com/meiyetec/mapper/OverseasBookingMapper.java:5` |
| DB表 | `overseas_booking` | `entity/OverseasBooking.java:7` `@TableName("overseas_booking")` |
| 关联表 | — | |
| 读/写 | R/W（其他 Feature 间接读写） | |
| 事务 | — | (Entity 层无事务) |
| 权限 | — | |
| 软删除 | ❌（无 `deleted` 列）| `OverseasBooking.java:7-31` |
| 审计字段 | `createdAt / updatedAt`（`@TableField(fill=...)` MyBatis 自动填充）| `OverseasBooking.java:29-30` |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 表未跑迁移；启动期触发 `TableNotFoundException` |
| DB Status | ❌ **MISSING** | `db-snapshot.sql` grep `overseas_booking` 0 匹配已确认 |
| Business Status | 26 字段：`id/tenantId/bookingNo/source/externalId/customerName/customerPhone/customerEmail/customerLocale/miniProgramOpenId/serviceId/serviceName/staffId/storeId/bookingDate/bookingTime/notes/status/ipAddress/userAgent/rawPayload/convertedMemberId/convertedOrderId/createdAt/updatedAt`；`miniProgramOpenId` 在 L21（SoT §0 提及）；uk 无（仅索引）| `OverseasBooking.java:11-31` |
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失 | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | **ISSUE-I038**（I002/I017/I029 MISSING 模式 — 跑迁移即解，**统一 Post-Audit 跑批** — 用户决策 #3 第 4 次强调）|
| Evidence | `audit/evidence/F081-overseas-booking-entity/` | 待建（Phase 5+）|
| Issue | **I038** | |

---

### F082 — 海外预约 / DB / overseas_booking DDL（建表 + ALTER）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F082 | `feature-inventory.md` §1.4.2 row 2 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | (DDL 静态资源) |
| Service | — | (DDL 静态资源) |
| Mapper | — | (DDL 静态资源) |
| DB表 | `overseas_booking` | DDL 目标 |
| 关联表 | — | |
| 读/写 | — | (DDL 创建阶段) |
| 事务 | — | |
| 权限 | — | |
| 软删除 | ❌（DDL 未含 `deleted` 列）| `db/sql/migrate_ext_19_mini_program_openid.sql:9-32` |
| 审计字段 | `created_at / updated_at`（DDL 默认 CURRENT_TIMESTAMP）| `migrate_ext_19_mini_program_openid.sql:30-31` |
| Static Status | L0（DDL 存在） | DDL 文件静态可达 |
| Runtime Status | UNVERIFIED | DEV DB 未跑迁移 |
| DB Status | ❌ **MISSING** | `db-snapshot.sql` grep 0 匹配已确认 |
| Business Status | 4 索引（`idx_tenant_status/store/source/created`）+ utf8mb4_bin + `ADD COLUMN IF NOT EXISTS mini_program_open_id`（MySQL 8.0.29+）| `db/sql/migrate_ext_19_mini_program_openid.sql:9-32`（CREATE TABLE IF NOT EXISTS）+ `:36`（ALTER ADD COLUMN）|
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失 | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | **ISSUE-I038**（沿用 I002/I017/I029 MISSING 模式 — **统一 Post-Audit 跑批**）|
| Evidence | `audit/evidence/F082-overseas-booking-ddl/` | 待建（Phase 5+）|
| Issue | **I038** | |

---

### F083 — 预约确认 / Service / BookingConfirmationService 核心发送

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F083 | `feature-inventory.md` §1.4.2 row 3 |
| 前端路由 | — | (间接通过 F095 + F096) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | (Service 层被 F095 + F096 调用) |
| Service | **`BookingConfirmationService.sendConfirmation(OverseasBooking)`** | `backend/src/main/java/com/meiyetec/booking/BookingConfirmationService.java:69-115` |
| Mapper | — | (Service 不直接写 DB) |
| DB表 | `overseas_booking`（间接：读 booking 字段；写由 F083 调用链上层完成）| |
| 关联表 | `pos_store`（经 F087 `StoreLookupService` 查 storeName/storePhone）| |
| 读/写 | R（读 booking + 间接读 pos_store） | |
| 事务 | ❌ 无 `@Transactional`（由调用方 F096 控制）| |
| 权限 | — | (无 dataScope 校验，依赖调用方) |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 依赖 F081 表存在 + F087 pos_store 字段对齐 |
| DB Status | ❌ **MISSING** | 间接 `overseas_booking` MISSING |
| Business Status | 4 步骤：① buildVars 构造占位符（含门店信息经 F087）② resolver.resolve 渲染模板（6 locale + warmup + fallback）③ 按可用渠道路由 3 渠道（SMS / EMAIL / MINI_PROGRAM — `IN_APP` / `WECHAT_OA` 未触达，与 SoT §4 一致）④ 聚合 `BookingConfirmationResult`；任一渠道 success → `overallSuccess=true`；`MP_TEMPLATE_ID = "BOOKING_CONFIRMATION"` 占位（SoT §0.1）；`FALLBACK_STORE_NAME/PHONE` 兜底常量（SoT §0.4）| `BookingConfirmationService.java:69-115` + `:25` + `:33-35` |
| E2E Status | UNVERIFIED | Phase 5 jshell 单元测试 + Mock 渠道调用 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失（间接）| |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **ISSUE-I038**（表 MISSING）+ **I047**（catch 吞异常 — I008 模式，与 SoT §0.3 "发送异常不会回滚状态变更" 一致设计）|
| Evidence | `audit/evidence/F083-booking-confirmation-service/` | 待建（Phase 5+）|
| Issue | **I038** + I047 | |

---

### F084 — 预约确认 / i18n / BookingConfirmationTemplateResolver（6 locale + warmup + fallback）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F084 | `feature-inventory.md` §1.4.2 row 4 |
| 前端路由 | — | (间接通过 F083) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`BookingConfirmationTemplateResolver.resolve(locale, vars)`** | `backend/src/main/java/com/meiyetec/booking/BookingConfirmationTemplateResolver.java:64-111` |
| Mapper | — | |
| DB表 | — | (仅 classpath 资源 `i18n/template/*.json`) |
| 关联表 | — | |
| 读/写 | R（读 classpath 资源） | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 启动期 warmup + 6 locale JSON 内容待 Phase 5 验证 |
| DB Status | ✅ N/A | (无 DB 依赖) |
| Business Status | 6 locale：`zh-CN/zh-TW/en-US/ja-JP/ko-KR/fr-FR`（与 SoT §1 完全一致）；`warmup()` 启动期 `@PostConstruct` 预热（L36-44）；`resolveWithFallback` 回退链 `xx-YY → xx → en-US`（L114-126）；`normalize` 归一化 `trim + lang 小写 / country 大写`（L130-138）；复用 `SmsSender.render(template, vars)` 占位符替换；空/null locale → DEFAULT_LOCALE="en-US"（L21）| `BookingConfirmationTemplateResolver.java:18-21` + `:36-44` + `:64-111` + `:114-126` + `:130-138` |
| E2E Status | UNVERIFIED | Phase 5 启动日志 + warmup 验证 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | 启动期 warmup + 6 locale JSON 内容 Phase 5 验证；`feature-inventory.md §1.4.6` 静态内容核对通过 |
| Evidence | `audit/evidence/F084-template-resolver/` | 待建（Phase 5+）|
| Issue | — | |

---

### F085 — 预约确认 / DTO / BookingConfirmationTemplate 渲染结果

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F085 | `feature-inventory.md` §1.4.2 row 5 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`BookingConfirmationTemplate`** | `backend/src/main/java/com/meiyetec/booking/BookingConfirmationTemplate.java:14-32` |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | (DTO) |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 待 Phase 5 实测 |
| DB Status | ✅ N/A | |
| Business Status | 8 字段：`resolvedLocale / usedFallback / subject / sms / miniProgram (title + keyword1..N) / miniProgramTitle / emailSubject / emailBody`；`miniProgram` 是 `LinkedHashMap<String,String>`（保留 keyword1..N 顺序）| `BookingConfirmationTemplate.java:14-32` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | (DTO 层无运行依赖；与 SoT §1 完全一致) |
| Evidence | `audit/evidence/F085-template-dto/` | 待建（Phase 5+）|
| Issue | — | |

---

### F086 — 预约确认 / DTO / BookingConfirmationResult 聚合结果

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F086 | `feature-inventory.md` §1.4.2 row 6 |
| 前端路由 | — | (间接通过 F095 + F096) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`BookingConfirmationResult`** | `backend/src/main/java/com/meiyetec/booking/BookingConfirmationResult.java:11-32` |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | (DTO) |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 待 Phase 5 实测 |
| DB Status | ✅ N/A | |
| Business Status | `overallSuccess` + `resolvedLocale / usedFallback / bookingId` + `channelResults: Map<channel, {success, externalMsgId, errorMsg}>`；`putChannel()` 自动累加 `overallSuccess`（任一 success=true → true）| `BookingConfirmationResult.java:11-32` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | (DTO 层无运行依赖；与 SoT §1 完全一致) |
| Evidence | `audit/evidence/F086-confirmation-result/` | 待建（Phase 5+）|
| Issue | — | |

---

### F087 — 预约确认 / 查询 / StoreLookupService（pos_store + 5min 缓存）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F087 | `feature-inventory.md` §1.4.2 row 7 |
| 前端路由 | — | (间接通过 F083) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`StoreLookupService.lookup(storeId)`** | `backend/src/main/java/com/meiyetec/booking/StoreLookupService.java:54-76` |
| Mapper | (JdbcTemplate 直接 SQL) | |
| DB表 | `pos_store` | (间接读) |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ 无 `@Transactional`（单一 SELECT）| |
| 权限 | — | |
| 软删除 | ❌（SQL 含 `AND deleted=0` 但无软删字段）| `StoreLookupService.java:60-66` |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 待 Phase 5 启动期验证 + 缓存命中验证 |
| DB Status | ✅ OK | `db-snapshot.sql` grep `pos_store` 5 匹配（存在）；`store_name/contact_phone` 字段对齐待 Phase 5 验证（I046）|
| Business Status | `CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)`（L24）；`ConcurrentHashMap<Long, CacheEntry>`（L26-31）；SQL `SELECT store_name, contact_phone FROM pos_store WHERE id=? AND deleted=0`（L60-66）；`lookupStoreName/lookupStorePhone` 静态方法（L43-45）；`invalidate(storeId)`（L78-79）| `StoreLookupService.java:54-76` + `:24` |
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 存在（5 匹配）；字段对齐待 Phase 5 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **ISSUE-I046**（pos_store 表存在但 EXT-19 依赖的 `store_name/contact_phone` 字段对齐待 Phase 5 `DESCRIBE pos_store` 验证）|
| Evidence | `audit/evidence/F087-store-lookup/` | 待建（Phase 5+）|
| Issue | **I046** | |

---

### F088 — 渠道 / SMS / SmsSender 阿里云短信

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F088 | `feature-inventory.md` §1.4.2 row 8 |
| 前端路由 | — | (间接通过 F083/F094) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`SmsSender.send(MktChannelTemplate, String, Map)`** | `backend/src/main/java/com/meiyetec/service/channel/SmsSender.java:46-90` |
| Mapper | (通过 `MktChannelConfigMapper` 间接) | |
| DB表 | `mkt_channel_config`（间接读 SMS 配置）| |
| 关联表 | — | |
| 读/写 | R（读 mkt_channel_config 配置）| |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | 依赖 TenantContextHolder 上下文（`resolveConfig` L106-124）| `SmsSender.java:106-124` |
| 软删除 | — | (查询含 `eq(MktChannelConfig::getDeleted, 0)`) |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | ⚠ **UNVERIFIED** | 无真实阿里云 accessKey（用户决策 #1 Mock 降级）|
| DB Status | ✅ OK | `mkt_channel_config` 存在 |
| Business Status | `channelType() = "SMS"`（L37-39）；走 `HttpUtil.sendAliYunSms`（L73）；缺凭据时 `demoMode()` 返回 `SMS-DEMO-{ts}`（L129-134）；`render(template, vars)` 静态方法（L137-149，`${key}` → vars[key]）；`normalizePhone` 自动去 `+86` 前缀（L172-177）；`maskPhone(135****0001)`（L179-182）| `SmsSender.java:37-39` + `:46-90` + `:129-134` + `:137-149` |
| E2E Status | UNVERIFIED | Phase 5 仅 mock 链路 + jshell 单元测试 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | ⚠ **UNVERIFIED** | **ISSUE-I049**（真实凭据缺失 — D10 铁律 + 用户决策 #1 Mock 降级）|
| Evidence | `audit/evidence/F088-sms-sender/` | 待建（Phase 5+）|
| Issue | **I049** | |

---

### F089 — 渠道 / EMAIL / EmailSender JavaMailSender + mismatch demo

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F089 | `feature-inventory.md` §1.4.2 row 9 |
| 前端路由 | — | (间接通过 F083/F094) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`EmailSender.send(MktChannelTemplate, String, Map)`** | `backend/src/main/java/com/meiyetec/service/channel/EmailSender.java:60-114` |
| Mapper | (通过 `MktChannelConfigMapper` 间接) | |
| DB表 | `mkt_channel_config`（间接读 EMAIL 配置）| |
| 关联表 | — | |
| 读/写 | R（读 mkt_channel_config 配置）| |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | 依赖 TenantContextHolder 上下文（`resolveConfig` L188-203）| `EmailSender.java:188-203` |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | ⚠ **UNVERIFIED** | 无真实 SMTP 凭据（用户决策 #2 沿用 SoT §0.2 mismatch demo 模式）|
| DB Status | ✅ OK | `mkt_channel_config` 存在 |
| Business Status | `channelType() = "EMAIL"`（L46-48）；**三模式**：① 租户 cfg + JavaMailSender 已注入 → 真实 SMTP（HTML / 纯文本自适应）；② JavaMailSender 已注入但无租户 cfg → 全局 `spring.mail.*`；③ JavaMailSender 未注入或 mismatch → demo 模式；**日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode`**（SoT §0.2 完全一致）；mismatch 检测 `globalHost.equals(smtpHost) ∧ globalUsername.equals(username)`| `EmailSender.java:46-48` + `:60-114` + `:121-167` (doSendSmtp) + `:178-186` (demoMode) + `:188-203` (resolveConfig) |
| E2E Status | UNVERIFIED | Phase 5 mismatch demo 模式日志关键词验证 + jshell |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | ⚠ **UNVERIFIED** | **ISSUE-I049**（真实 SMTP 凭据缺失 — D10 铁律 + 用户决策 #2 mismatch demo 模式）|
| Evidence | `audit/evidence/F089-email-sender/` | 待建（Phase 5+）|
| Issue | **I049** | |

---

### F090 — 渠道 / MINI_PROGRAM / MiniProgramSender 微信订阅消息

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F090 | `feature-inventory.md` §1.4.2 row 10 |
| 前端路由 | — | (间接通过 F083/F094) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`MiniProgramSender.send(MktChannelTemplate, String, Map)`** | `backend/src/main/java/com/meiyetec/service/channel/MiniProgramSender.java:48-100` |
| Mapper | (通过 `MktChannelConfigMapper` 间接) | |
| DB表 | `mkt_channel_config`（间接读 MINI_PROGRAM 配置）| |
| 关联表 | — | |
| 读/写 | R（读 mkt_channel_config 配置 + 内存 TokenCache）| |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | 依赖 TenantContextHolder 上下文 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | ⚠ **UNVERIFIED** | 无真实 appId/appSecret（用户决策 #1 Mock 降级）|
| DB Status | ✅ OK | `mkt_channel_config` 存在 |
| Business Status | `channelType() = "MINI_PROGRAM"`；**AccessToken 内存缓存**：`ConcurrentHashMap<Long, TokenCache>`（L33-37）；`TOKEN_CACHE_MS = 7100_000L`（提前 10 分钟刷新 2 小时 token，L41）；errcode=40001 自动清缓存（L96-98）；`buildSubscribeData` 按 `variables` 数组生成 `keyword1..keyword5`（L135-163，最多 5）；`MP_TEMPLATE_ID = "BOOKING_CONFIRMATION"` 是占位（SoT §0.1）| `MiniProgramSender.java:48-100` + `:107-128` (getAccessToken) + `:135-163` (buildSubscribeData) |
| E2E Status | UNVERIFIED | Phase 5 仅 mock 链路 + jshell 单元测试 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | ⚠ **UNVERIFIED** | **ISSUE-I049**（真实 appId/appSecret 缺失 — D10 铁律）；**Phase 5 验证 WeChat 模板 ID 是占位符（SoT §0.1）**|
| Evidence | `audit/evidence/F090-mini-program-sender/` | 待建（Phase 5+）|
| Issue | **I049** | |

---

### F091 — 渠道 / IN_APP / InAppSender 站内信（database 持久化）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F091 | `feature-inventory.md` §1.4.2 row 11 |
| 前端路由 | — | (M09 营销链路间接调用) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`InAppSender.send(MktChannelTemplate, String, Map)`** | `backend/src/main/java/com/meiyetec/service/channel/InAppSender.java:42-87` |
| Mapper | (通过 `MemberMessageMapper` 间接) | |
| DB表 | `member_message`（**写**）| |
| 关联表 | `member` / `crm_member`（间接）| |
| 读/写 | W（写 member_message）| |
| 事务 | ✅ `@Transactional(rollbackFor = Exception.class)` (L43) | (rollbackFor 正确) |
| 权限 | 依赖 TenantContextHolder 上下文 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | ❌ **MISSING** | `member_message` 表未跑迁移 — 启动期触发 `TableNotFoundException` |
| DB Status | ❌ **MISSING** | `db-snapshot.sql` grep `member_message` 0 匹配已确认 |
| Business Status | `channelType() = "IN_APP"`（L36-38）；写 `member_message(tenantId/memberId/title/content/channelType=IN_APP/messageType/priority=2/...)`（L50-80）；`receiver` 解析 `memberId[-name]` 格式（L82-86）；`messageType` 推断（BIRTHDAY/EXPIRE/PROMO/TASK/SYSTEM，L92-110）；**F083 `sendConfirmation` 未路由 IN_APP**（与 SoT §4 一致 — SoT 仅明确 3 渠道路由：SMS/EMAIL/MINI_PROGRAM，"任一无对应 receiver → skipped"），但 `ChannelDispatcher.getSender("IN_APP")` 仍可被其他业务调用 | `InAppSender.java:36-38` + `:42-87` + `:43` |
| E2E Status | UNVERIFIED | Phase 5 表迁移 + 站内信写入验证 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失 | |
| Last Verified At | — | |
| **Final Result** | ❌ **MISSING** | **ISSUE-I045**（`member_message` 表 DDL 已写但 DEV DB 未跑迁移 — I002/I017/I029 MISSING 模式，**统一 Post-Audit 跑批 — 用户决策 #3**）|
| Evidence | `audit/evidence/F091-in-app-sender/` | 待建（Phase 5+）|
| Issue | **I045** | |

---

### F092 — 渠道 / WECHAT_OFFICIAL_ACCOUNT / WechatOfficialAccountSender 公众号模板消息

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F092 | `feature-inventory.md` §1.4.2 row 12 |
| 前端路由 | — | (M09 营销链路间接调用) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`WechatOfficialAccountSender.send(MktChannelTemplate, String, Map)`** | `backend/src/main/java/com/meiyetec/service/channel/WechatOfficialAccountSender.java:53-113` |
| Mapper | (通过 `MktChannelConfigMapper` 间接) | |
| DB表 | `mkt_channel_config`（间接读 OA 配置）| |
| 关联表 | — | |
| 读/写 | R（读 mkt_channel_config 配置 + 内存 TokenCache）| |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | 依赖 TenantContextHolder 上下文 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | ⚠ **UNVERIFIED** | 无真实 appId/appSecret（用户决策 #1 Mock 降级）|
| DB Status | ✅ OK | `mkt_channel_config` 存在 |
| Business Status | `channelType() = "WECHAT_OFFICIAL_ACCOUNT"`（L60 — **注意：非 `WECHAT_OA` 别名，与 SoT §1 描述"WECHAT_OA"不一致 — 见 I039**）；AccessToken 缓存与 F090 同模式（`TOKEN_CACHE_MS = 7100_000L`）；模板消息用 `first/keyword1..N` 格式（最多 10 个 keyword，L148-184）；errcode 40001/40003/43003/43004/43005 业务失败不重试 | `WechatOfficialAccountSender.java:53-113` + `:120-141` (getAccessToken) + `:148-184` (buildTemplateData) |
| E2E Status | UNVERIFIED | Phase 5 仅 mock 链路 + jshell 单元测试 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | ⚠ **UNVERIFIED + CONFLICT** | **ISSUE-I049**（真实 appId/appSecret 缺失 — D10 铁律）；**ISSUE-I039**（SoT §1 别名 "WECHAT_OA" vs 代码 "WECHAT_OFFICIAL_ACCOUNT" 反向 — 中立标，Fix Phase = Post-Audit 人类决策）|
| Evidence | `audit/evidence/F092-wechat-oa-sender/` | 待建（Phase 5+）|
| Issue | **I049 + I039** | |

---

### F093 — 渠道 / 接口 / ChannelSender 接口契约

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F093 | `feature-inventory.md` §1.4.2 row 13 |
| 前端路由 | — | (无前端入口) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`ChannelSender` 接口** | `backend/src/main/java/com/meiyetec/service/channel/ChannelSender.java:9-37` |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | (接口契约) |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | (接口层无运行依赖) |
| DB Status | ✅ N/A | |
| Business Status | 3 方法：`channelType()` / `send(MktChannelTemplate, String, Map)` / `healthCheck()`；`SendResult` 内嵌类：`{success, externalMsgId, errorMsg}` + `static ok(externalMsgId)` / `static fail(msg)` 工厂 | `ChannelSender.java:9-37` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | (接口契约层；5 个 channel sender 均实现) |
| Evidence | `audit/evidence/F093-channel-sender-interface/` | 待建（Phase 5+）|
| Issue | — | |

---

### F094 — 渠道 / 分发 / ChannelDispatcher 路由 + healthCheckAll

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F094 | `feature-inventory.md` §1.4.2 row 14 |
| 前端路由 | — | (M09 体系调用) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`ChannelDispatcher.getSender / healthCheckAll / rateLimitCheck`** | `backend/src/main/java/com/meiyetec/service/channel/ChannelDispatcher.java:34-41` + `:53-73` + `:78-90` |
| Mapper | (通过 `MktChannelConfigMapper` 间接) | |
| DB表 | `mkt_channel_config`（写 `last_health_at/last_health_msg`）+ `marketing_touch_log`（读限流计数）| |
| 关联表 | — | |
| 读/写 | R/W | (rateLimitCheck 读 + healthCheckAll 写) |
| 事务 | ❌ 无 `@Transactional`（healthCheckAll 单条 UPDATE） | |
| 权限 | 平台超管 / TenantContextHolder 上下文 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ OK | `mkt_channel_config` + `marketing_touch_log` 存在 |
| Business Status | `getSender(channelType)` 按 channelType filter（L34-41）；`healthCheckAll` 轮询所有 sender + UPDATE `mkt_channel_config.last_health_at`（L53-73 — **T07-D4 例外声明：仅更新默认租户(tenant_id=1)**）；`rateLimitCheck` 查 `marketing_touch_log` 按分钟/日限额（L78-90）| `ChannelDispatcher.java:34-41` + `:53-73` + `:78-90` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **ISSUE-I050**（`healthCheckAll` T07-D4 例外：仅更新 tenant_id=1，其他租户渠道健康状态不会被更新 — 有意保留的语义，本批次不改）|
| Evidence | `audit/evidence/F094-channel-dispatcher/` | 待建（Phase 5+）|
| Issue | **I050** | |

---

### F095 — 预约确认 / 端点 / POST /intl-booking/{id}/send-confirmation 手动触发

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F095 | `feature-inventory.md` §1.4.2 row 15 |
| 前端路由 | `/intl-booking` | (无直接调用 — **I042 Shadow**) |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 操作列（**⚠ Shadow I042：缺独立 sendConfirmation 入口**）| |
| Action | (无；前端 intl-booking.ts 无 sendConfirmation 方法) | |
| Handler | — | |
| API | ⚠ **(无 sendConfirmation 方法)** | (前端 `api/intl-booking.ts:1-31` 12 端点中无 sendConfirmation) |
| Method | POST | |
| Controller | **`IntlBookingService.sendConfirmation(@PathVariable Long id)`** ⚠ **命名误导 — 实为 `@RestController`** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:237-242` |
| Service | 合并于 Controller（`@RestController` 命名异味 — I044）| (I044 第 4 次命名异味) |
| Mapper | **`OverseasBookingMapper.selectById`** | `OverseasBookingMapper.java:5` |
| DB表 | `overseas_booking`（**MISSING**）| |
| 关联表 | `pos_store`（经 F087 间接）| |
| 读/写 | R | |
| 事务 | ❌ 无 `@Transactional`（单一 SELECT + Service 调用）| |
| 权限 | 普通租户（依赖 TenantContextHolder 上下文）| |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 表 MISSING；前端无调用入口 |
| DB Status | ❌ **MISSING** | `overseas_booking` 表缺失 |
| Business Status | 独立于状态变更；店长可对任何状态的预约重发确认（SoT §3 明确）；返回 `BookingConfirmationResult`；调 `confirmationService.sendConfirmation(b)` | `IntlBookingService.java:237-242` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **ISSUE-I038**（表 MISSING）+ **I042**（前端 Shadow：缺 `sendConfirmation` 方法）|
| Evidence | `audit/evidence/F095-send-confirmation/` | 待建（Phase 5+）|
| Issue | **I038 + I042** | |

---

### F096 — 预约确认 / 端点 / POST /intl-booking/{id}/status + autoSend 自动触发

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F096 | `feature-inventory.md` §1.4.2 row 16 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/router/index.ts` (待确认) |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 操作列 → 「确认」按钮（L40 `setStatus(row, 'CONFIRMED')`）| `index.vue:40` |
| Action | 点击「确认」→ 调用 `intlBookingApi.updateStatus(row.id, { status })` | `index.vue:113-117` |
| Handler | `setStatus(row, status)` | `index.vue:113-117` |
| API | `intlBookingApi.updateStatus(id, data)` | `frontend/meiyetec-web/src/api/intl-booking.ts:24-25` |
| Method | POST | |
| Controller | **`IntlBookingService.updateStatus(@PathVariable Long id, @RequestBody Map)`** ⚠ **`@RestController` 命名误导** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:187-214` |
| Service | 合并于 Controller（`@RestController` 命名异味 — I044）| (I044 第 4 次命名异味) |
| Mapper | **`OverseasBookingMapper.selectById / updateById`** | `OverseasBookingMapper.java:5` |
| DB表 | `overseas_booking`（**MISSING**）| |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ⚠ `@Transactional` 但**未指定 `rollbackFor`**（**I048 模式**：默认仅 RuntimeException 回滚，HTTP 异常 / 受检异常可能不回滚）| `IntlBookingService.java:188` |
| 权限 | 普通租户（依赖 TenantContextHolder 上下文）| |
| 软删除 | — | |
| 审计字段 | `updated_at`（`@TableField(fill=INSERT_UPDATE)`）| `OverseasBooking.java:30` |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 表 MISSING |
| DB Status | ❌ **MISSING** | `overseas_booking` 表缺失 |
| Business Status | **行为变更**（SoT §0.3 ⭐）：非 CONFIRMED → CONFIRMED 时**自动调用** `confirmationService.sendConfirmation(b)`；`body.autoSend=false` 可关闭（L194-196）；发送异常在 try/catch 内 + 写 `confirmationError` 字段（L207-211 — **不回滚状态变更** — I047 I008 模式）；响应 `data: { booking, confirmation?, confirmationError? }`（L200-213 — 与 SoT §0.3 完全一致）；前端 `setStatus(row, 'CONFIRMED')` 直接传 `{status: 'CONFIRMED'}`，**不传 `autoSend` 字段**（`index.vue:113-117` — **I040 Shadow**）；按钮**无 "确认即发送通知" 文案 + 无 loading / 无二次确认**（**I041 Shadow**）| `IntlBookingService.java:187-214` + `index.vue:40` + `:113-117` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **ISSUE-I038**（表 MISSING）+ **I040**（autoSend=false UI 缺失）+ **I041**（按钮文案缺失）+ **I047**（catch 吞异常 — I008 模式）+ **I048**（rollbackFor — I009 模式）|
| Evidence | `audit/evidence/F096-update-status/` | 待建（Phase 5+）|
| Issue | **I038 + I040 + I041 + I047 + I048** | |

---

### F097 — 预约确认 / 端点 / GET /intl-booking/{id}/confirmation-template 模板预览

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F097 | `feature-inventory.md` §1.4.2 row 17 |
| 前端路由 | `/intl-booking` | `router/index.ts` (待确认) |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 操作列 → 「确认模板」按钮（L41 `viewTemplate(row.id)`）| `index.vue:41` |
| Action | 点击「确认模板」→ 调用 `intlBookingApi.confirmationTemplate(id)` | `index.vue:120-124` |
| Handler | `viewTemplate(id)` | `index.vue:120-124` |
| API | `intlBookingApi.confirmationTemplate(id)` | `frontend/meiyetec-web/src/api/intl-booking.ts:30` |
| Method | GET | |
| Controller | **`IntlBookingService.confirmationTemplate(@PathVariable Long id)`** ⚠ **`@RestController` 命名误导** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:388-432` |
| Service | 合并于 Controller（`@RestController` 命名异味 — I044）| (I044 第 4 次命名异味) |
| Mapper | **`OverseasBookingMapper.selectById`** | `OverseasBookingMapper.java:5` |
| DB表 | `overseas_booking`（**MISSING**）| |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | 普通租户（依赖 TenantContextHolder 上下文）| |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 表 MISSING |
| DB Status | ❌ **MISSING** | `overseas_booking` 表缺失 |
| Business Status | **⚠ I043 CONFLICT**（中立标 — SoT vs 代码反向）：SoT §1 期望走 `BookingConfirmationTemplateResolver` + 6 locale JSON 模板体系；代码实际是**控制器内硬编码 switcher**（`zh/zh-cn/zh-tw/ja/ko/default` 6 分支，L401-427）；返回仅 `subject + body`（L428-431），**与 `BookingConfirmationTemplate` DTO 反向**（缺 sms/miniProgram/email 4 段）；当前 preview (F097) 与生产发送链路 (F083+F084) **不一致** | `IntlBookingService.java:388-432` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失 | |
| Last Verified At | — | |
| **Final Result** | ❌ **CONFLICT**（中立标 — SoT vs 代码反向） | **ISSUE-I038**（表 MISSING）+ **I043**（硬编码 switcher vs Resolver 体系 — Fix Phase = Post-Audit 人类决策，3 选项：改用 Resolver / 保留硬编码 + 标注差异 / 接受现状）|
| Evidence | `audit/evidence/F097-confirmation-template/` | 待建（Phase 5+）|
| Issue | **I038 + I043** | |

---

### F098 — 预约 / 端点 / GET /intl-booking/list 列表查询

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F098 | `feature-inventory.md` §1.4.2 row 18 |
| 前端路由 | `/intl-booking` | `router/index.ts` (待确认) |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 「国际预约」tab table（L25-50）| `index.vue:25-50` |
| Action | 页面加载时自动调 `intlBookingApi.listBookings()` | `index.vue:91-94` |
| Handler | `load()` | `index.vue:91-94` |
| API | `intlBookingApi.listBookings(params?)` | `frontend/meiyetec-web/src/api/intl-booking.ts:21-22` |
| Method | GET | |
| Controller | **`IntlBookingService.listBookings(status?, source?)`** ⚠ **`@RestController` 命名误导** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:170-178` |
| Service | 合并于 Controller（`@RestController` 命名异味 — I044）| (I044 第 4 次命名异味) |
| Mapper | **`OverseasBookingMapper.selectList(LambdaQueryWrapper)`** | `OverseasBookingMapper.java:5` |
| DB表 | `overseas_booking`（**MISSING**）| |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ 无 `@Transactional`（单一 SELECT）| |
| 权限 | 普通租户（强制 `tenantId=TenantContextHolder.get().getTenantId()`）| `IntlBookingService.java:172` |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 表 MISSING |
| DB Status | ❌ **MISSING** | `overseas_booking` 表缺失 |
| Business Status | `LambdaQueryWrapper<OverseasBooking>` 按 `tenantId` 过滤 + `id DESC`（L172-176）；可选 `status` / `source` 过滤（L177-178）| `IntlBookingService.java:170-178` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **ISSUE-I038**（表 MISSING — Phase 5 实测会暴露 `TableNotFoundException`，作为审计有效负向证据）|
| Evidence | `audit/evidence/F098-list-bookings/` | 待建（Phase 5+）|
| Issue | **I038** | |

---

### F099 — 预约 / Webhook / POST /intl-booking/webhook/google Google 推送

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F099 | `feature-inventory.md` §1.4.2 row 19 |
| 前端路由 | — | (无前端入口；Google BP 后台 push) |
| 页面 | — | |
| UI板块 | — | |
| Action | Google BP 后台推送 POST | |
| Handler | — | |
| API | — | |
| Method | POST | |
| Controller | **`IntlBookingService.googleWebhook(payload, token)`** ⚠ **`@RestController` 命名误导** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:251-287` |
| Service | 合并于 Controller | (I044 第 4 次命名异味) |
| Mapper | **`OverseasBookingMapper.insert`** | `OverseasBookingMapper.java:5` |
| DB表 | `overseas_booking`（**MISSING**）| |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | ⚠ `@Transactional` 但**未指定 `rollbackFor`**（**I048 模式**：默认仅 RuntimeException 回滚）| `IntlBookingService.java:252` |
| 权限 | (无；push 端无需 dataScope 校验；但内部从 token 反查 tenantId) | |
| 软删除 | — | |
| 审计字段 | `created_at / updated_at`（MyBatis 自动填充）| `OverseasBooking.java:29-30` |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 表 MISSING |
| DB Status | ❌ **MISSING** | `overseas_booking` 表缺失 |
| Business Status | **演示模式**：解析 `payload.notificationId / customerName / customerPhone / customerEmail / locale / serviceName / bookingTime`（L264-281）→ 插 `overseas_booking`（L283）；`token → tenantId` 反查当前演示返回 `null` 兜底 1L（L255-256 — **I051 演示模式**：真实场景需 OAuth subscription 订阅表反查） | `IntlBookingService.java:251-287` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **ISSUE-I038**（表 MISSING）+ **I048**（rollbackFor — I009 模式）+ **I051**（webhook demo 模式 — 真实链路缺失）|
| Evidence | `audit/evidence/F099-google-webhook/` | 待建（Phase 5+）|
| Issue | **I038 + I048 + I051** | |

---

### F100 — 预约 / Webhook / POST /intl-booking/webhook/instagram IG 推送

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F100 | `feature-inventory.md` §1.4.2 row 20 |
| 前端路由 | — | (无前端入口；Meta 后台 push) |
| 页面 | — | |
| UI板块 | — | |
| Action | Meta 后台推送 POST | |
| Handler | — | |
| API | — | |
| Method | POST | |
| Controller | **`IntlBookingService.instagramWebhook(payload)`** ⚠ **`@RestController` 命名误导** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:292-339` |
| Service | 合并于 Controller | (I044 第 4 次命名异味) |
| Mapper | **`OverseasBookingMapper.insert`** | `OverseasBookingMapper.java:5` |
| DB表 | `overseas_booking`（**MISSING**）| |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | ⚠ `@Transactional` 但**未指定 `rollbackFor`**（**I048 模式**）| `IntlBookingService.java:293` |
| 权限 | (无；push 端) | |
| 软删除 | — | |
| 审计字段 | `created_at / updated_at` | `OverseasBooking.java:29-30` |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 表 MISSING |
| DB Status | ❌ **MISSING** | `overseas_booking` 表缺失 |
| Business Status | **演示模式**：解析 `entry[0].messaging[0].message.text / sender.id`（L300-318）+ 简易 NLP（`parseServiceFromText` 关键词匹配 facial/massage/manicure/nail/hair，L376-385）→ 插 `overseas_booking`；tenantId 演示模式硬编码 1L（L299 — **I051**）| `IntlBookingService.java:292-339` + `:376-385` (parseServiceFromText) |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **ISSUE-I038**（表 MISSING）+ **I048**（rollbackFor）+ **I051**（webhook demo 模式）|
| Evidence | `audit/evidence/F100-instagram-webhook/` | 待建（Phase 5+）|
| Issue | **I038 + I048 + I051** | |

---

### F101 — 预约 / Webhook 验证 / GET /intl-booking/webhook/instagram Meta verify_token 握手

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F101 | `feature-inventory.md` §1.4.2 row 21 |
| 前端路由 | — | (无前端入口；Meta webhook 验证握手) |
| 页面 | — | |
| UI板块 | — | |
| Action | Meta 后台 GET 验证 | |
| Handler | — | |
| API | — | |
| Method | GET | |
| Controller | **`IntlBookingService.instagramWebhookVerify(mode, verifyToken, challenge)`** ⚠ **`@RestController` 命名误导** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:344-356` |
| Service | 合并于 Controller | (I044 第 4 次命名异味) |
| Mapper | — | (仅返回 hub.challenge) |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | (无；Meta 握手) | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 无 DB 依赖 |
| DB Status | ✅ N/A | |
| Business Status | 返回 `hub.challenge` 完成 Meta webhook 验证握手（L348-353）；**演示模式**：mode=subscribe + challenge 非空即返回；生产需 `verifyToken` 比对（SoT §3 未明确，本批次不实现）| `IntlBookingService.java:344-356` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **ISSUE-I051**（演示模式 — 真实链路 `verifyToken` 比对未实现）|
| Evidence | `audit/evidence/F101-instagram-webhook-verify/` | 待建（Phase 5+）|
| Issue | **I051** | |

---

### F102 — 预约 / OAuth2 / Google / Instagram connect + callback

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F102 | `feature-inventory.md` §1.4.2 row 22 |
| 前端路由 | `/intl-booking` | `router/index.ts` (待确认) |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 「渠道连接」tab + Google/Instagram 绑定对话框（L54-78）| `index.vue:54-78` |
| Action | 点击「绑定」→ 填写 clientId/clientSecret → 点击「获取授权链接」→ `window.open(data.authUrl, '_blank')` | `index.vue:101-110` |
| Handler | `connectGoogle / connectInstagram / doGoogle / doInstagram` | `index.vue:96-110` |
| API | `intlBookingApi.connectGoogle / connectInstagram` | `frontend/meiyetec-web/src/api/intl-booking.ts:6-9` |
| Method | POST | (4 端点) |
| Controller | **`IntlBookingService.connectGoogle + googleCallback + connectInstagram + instagramCallback`** ⚠ **`@RestController` 命名误导** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:55-65` + `:68-93` + `:96-122` + `:125-142` |
| Service | 合并于 Controller | (I044 第 4 次命名异味) |
| Mapper | — | (OAuth 不写 DB) |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | ⚠ `googleCallback / instagramCallback` 加了 `@Transactional` 但**未指定 `rollbackFor`**（**I048 模式**）| `IntlBookingService.java:69` + `:126` |
| 权限 | 普通租户（依赖 TenantContextHolder 上下文）| |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | |
| DB Status | ✅ N/A | |
| Business Status | **演示模式**：返回真实 OAuth URL（`accounts.google.com/o/oauth2/v2/auth` + `facebook.com/v18.0/dialog/oauth`）但 callback 不真换 token，返回 `ya29.DEMO-{code前8位}` / `IGQVJ-DEMO-`（L84-87, L137-140 — **I051 演示模式**：真实场景需调 Google/Meta token 端点 + webhook 订阅）；前端 `window.open(data.authUrl, '_blank')` 跳转（L107, L110）| `IntlBookingService.java:55-142` + `index.vue:96-110` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **ISSUE-I048**（rollbackFor — I009 模式）+ **I051**（OAuth callback demo 模式 — 真实链路缺失）|
| Evidence | `audit/evidence/F102-oauth-connect-callback/` | 待建（Phase 5+）|
| Issue | **I048 + I051** | |

---

### F103 — 预约 / 转化 / POST /intl-booking/{id}/convert 转化为会员 + 订单

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F103 | `feature-inventory.md` §1.4.2 row 23 |
| 前端路由 | `/intl-booking` | `router/index.ts` (待确认) |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 操作列 → 「转化为会员」按钮（L42 `convert(row.id)`，仅 `status === 'CONFIRMED' && !row.convertedMemberId` 可见）| `index.vue:42` |
| Action | 点击「转化为会员」→ 调用 `intlBookingApi.convertBooking(id)` | `index.vue:118-125` |
| Handler | `convert(id)` | `index.vue:118-125` |
| API | `intlBookingApi.convertBooking(id)` | `frontend/meiyetec-web/src/api/intl-booking.ts:28` |
| Method | POST | |
| Controller | **`IntlBookingService.convertBooking(id) + doCreateMember + doCreatePosOrder`** ⚠ **`@RestController` 命名误导** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:362-373` + `:375-390` + `:392-405` |
| Service | 合并于 Controller | (I044 第 4 次命名异味) |
| Mapper | **`OverseasBookingMapper.selectById + updateById`** + `JdbcTemplate` 直接 INSERT | `OverseasBookingMapper.java:5` + `IntlBookingService.java:375-405` |
| DB表 | `overseas_booking`（**MISSING**）+ `crm_member`（写）+ `pos_order`（写）| |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ⚠ `@Transactional` 但**未指定 `rollbackFor`**（**I048 模式**）| `IntlBookingService.java:363` |
| 权限 | 普通租户（依赖 TenantContextHolder 上下文）| |
| 软删除 | — | |
| 审计字段 | `updated_at`（booking 写回时）| |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 表 MISSING |
| DB Status | ❌ **MISSING** | `overseas_booking` 表缺失 |
| Business Status | **演示模式**：JdbcTemplate 直接 INSERT `crm_member`（L377-385）+ `pos_order`（L394-403，避免引入 MemberService 复杂依赖）；写回 `convertedMemberId / convertedOrderId`（L406-407）；前置条件：`status === 'CONFIRMED'` 不转化返回 `BizCode.BAD_REQUEST`（L371）；已转化返回 `BizCode.CONFLICT`（L368）| `IntlBookingService.java:362-407` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **ISSUE-I038**（表 MISSING）+ **I048**（rollbackFor — I009 模式）|
| Evidence | `audit/evidence/F103-convert-booking/` | 待建（Phase 5+）|
| Issue | **I038 + I048** | |

---

### F104 — 预约确认 / 端点 / GET /intl-booking/{id} 单条查询

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F104 | `feature-inventory.md` §1.4.2 row 24 |
| 前端路由 | `/intl-booking` | `router/index.ts` (待确认) |
| 页面 | — | (间接通过 F098 列表点击) |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | `intlBookingApi.getBooking(id)` | `frontend/meiyetec-web/src/api/intl-booking.ts:23` |
| Method | GET | |
| Controller | **`IntlBookingService.getBooking(@PathVariable Long id)`** ⚠ **`@RestController` 命名误导** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:181-185` |
| Service | 合并于 Controller | (I044 第 4 次命名异味) |
| Mapper | **`OverseasBookingMapper.selectById`** | `OverseasBookingMapper.java:5` |
| DB表 | `overseas_booking`（**MISSING**）| |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ 无 `@Transactional`（单一 SELECT）| |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 表 MISSING |
| DB Status | ❌ **MISSING** | `overseas_booking` 表缺失 |
| Business Status | `selectById`；`null` → `BizCode.NOT_FOUND`（L182-183）| `IntlBookingService.java:181-185` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **ISSUE-I038**（表 MISSING）|
| Evidence | `audit/evidence/F104-get-booking/` | 待建（Phase 5+）|
| Issue | **I038** | |

---

### F105 — 预约 / 渠道列表 / GET /intl-booking/channels 静态渠道列表

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F105 | `feature-inventory.md` §1.4.2 row 25 |
| 前端路由 | `/intl-booking` | `router/index.ts` (待确认) |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 「渠道连接」tab `channels` 卡片网格（L4-22）| `index.vue:4-22` |
| Action | 页面加载时自动调 `intlBookingApi.listChannels()` | `index.vue:92` |
| Handler | `load()` | `index.vue:91-94` |
| API | `intlBookingApi.listChannels()` | `frontend/meiyetec-web/src/api/intl-booking.ts:18` |
| Method | GET | |
| Controller | **`IntlBookingService.listChannels()`** ⚠ **`@RestController` 命名误导** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:145-156` |
| Service | 合并于 Controller | (I044 第 4 次命名异味) |
| Mapper | — | (纯静态枚举) |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | — | (无权限校验) |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | (无 DB 依赖) |
| DB Status | ✅ N/A | |
| Business Status | 4 渠道：`GOOGLE / INSTAGRAM / FACEBOOK / TIKTOK`（L148-153 — **FACEBOOK / TIKTOK status=PLANNED** — 非 PLANNED 但显示）；纯静态枚举无 DB 查询 | `IntlBookingService.java:145-156` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | FACEBOOK / TIKTOK 渠道 `status=PLANNED` 但卡片显示（**已知约束 — 待真实渠道接入**）|
| Evidence | `audit/evidence/F105-list-channels/` | 待建（Phase 5+）|
| Issue | — | |

---

### F106 — 预约 / 拉取 / POST /intl-booking/{channelId}/pull 拉取预约（演示）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F106 | `feature-inventory.md` §1.4.2 row 26 |
| 前端路由 | `/intl-booking` | `router/index.ts` (待确认) |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 「渠道连接」tab → 「拉取预约」按钮（L17）| `index.vue:17` |
| Action | 点击「拉取预约」→ 调用 `intlBookingApi.pullBookings(channelId)` | `index.vue:111-115` |
| Handler | `pull(id)` | `index.vue:111-115` |
| API | `intlBookingApi.pullBookings(channelId)` | `frontend/meiyetec-web/src/api/intl-booking.ts:20` |
| Method | POST | |
| Controller | **`IntlBookingService.pullBookings(@PathVariable Long channelId)`** ⚠ **`@RestController` 命名误导** | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:159-167` |
| Service | 合并于 Controller | (I044 第 4 次命名异味) |
| Mapper | **`OverseasBookingMapper.insert`** | `OverseasBookingMapper.java:5` |
| DB表 | `overseas_booking`（**MISSING**）| |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | ⚠ `@Transactional` 但**未指定 `rollbackFor`**（**I048 模式**）| `IntlBookingService.java:160` |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | `created_at / updated_at` | `OverseasBooking.java:29-30` |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 表 MISSING |
| DB Status | ❌ **MISSING** | `overseas_booking` 表缺失 |
| Business Status | **演示模式**：GOOGLE / INSTAGRAM 渠道插 1 条示例记录（`ExternalId="EXT-{ts}"` + 演示客户 + 演示服务，L165-166）；FACEBOOK / TIKTOK 返回 `BizCode.NOT_FOUND`（`channelIdToName` L370-376 返 null）| `IntlBookingService.java:159-167` + `:370-376` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 中缺失 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **ISSUE-I038**（表 MISSING）+ **I048**（rollbackFor — I009 模式）+ **I051**（演示模式 — 真实 API 接入需 OAuth Client）|
| Evidence | `audit/evidence/F106-pull-bookings/` | 待建（Phase 5+）|
| Issue | **I038 + I048 + I051** | |

---

### F107 — 预约确认 / i18n / 6 个 locale JSON 模板（合并）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F107 | `feature-inventory.md` §1.4.2 row 27 |
| 前端路由 | — | (间接通过 F083 经 F084 渲染) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | 6 资源文件（`BookingConfirmationTemplateResolver.warmup()` 启动期加载）| `BookingConfirmationTemplateResolver.java:18-21` (WARMUP_LOCALES) + `:36-44` (warmup) |
| Mapper | — | |
| DB表 | — | (仅 classpath 资源) |
| 关联表 | — | |
| 读/写 | R（classpath 资源） | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | Phase 5 启动日志 + warmup 验证 |
| DB Status | ✅ N/A | |
| Business Status | **6 locale JSON 内容完整核对通过**（`feature-inventory.md §1.4.6` 详细交叉表）；均含 `subject / sms / miniProgram(title+keyword1..keyword5) / email(subject+body)` 4 段；占位符 `${customerName / serviceName / bookingDate / bookingTime / bookingNo / storeName / storePhone}` **7 变量**（与 SoT §5 完全一致 — 缺 `customerPhone / customerEmail` 是设计合理：仅在有 receiver 时才路由 SMS/EMAIL）| `backend/src/main/resources/i18n/template/{zh-CN,zh-TW,en-US,ja-JP,ko-KR,fr-FR}.json` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | (i18n 模板内容静态核对通过；启动期 warmup 待 Phase 5 验证) |
| Evidence | `audit/evidence/F107-locale-templates/` | 待建（Phase 5+）|
| Issue | — | |

---

### F108 — 测试 / BookingConfirmationServiceTest（StubSms/Email + Mockito StoreLookup）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F108 | `feature-inventory.md` §1.4.2 row 28 |
| 前端路由 | — | (无；JUnit 测试) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`BookingConfirmationServiceTest`** | `backend/src/test/java/com/meiyetec/booking/BookingConfirmationServiceTest.java` |
| Mapper | — | (Mockito mock) |
| DB表 | — | (Mockito mock OverseasBooking) |
| 关联表 | — | |
| 读/写 | — | (测试) |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | Phase 5 跑 JUnit |
| DB Status | ✅ N/A | (Mockito mock) |
| Business Status | StubSmsSender / StubEmailSender 桩 + Mockito mock StoreLookupService + resolver；测试点：渠道路由 + overallSuccess 聚合 + null booking 安全 + ChannelSender 缺失降级 + 门店信息读取 | `BookingConfirmationServiceTest.java` (全文) |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | (测试代码静态可达；Phase 5 跑 mvn test 验证) |
| Evidence | `audit/evidence/F108-confirmation-service-test/` | 待建（Phase 5+）|
| Issue | — | |

---

### F109 — 测试 / BookingConfirmationTemplateResolverTest（locale 归一化 + 回退 + 渲染）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F109 | `feature-inventory.md` §1.4.2 row 29 |
| 前端路由 | — | (无；JUnit 测试) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`BookingConfirmationTemplateResolverTest`** | `backend/src/test/java/com/meiyetec/booking/BookingConfirmationTemplateResolverTest.java` |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | (测试) |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | Phase 5 跑 JUnit |
| DB Status | ✅ N/A | |
| Business Status | 反射注入 ObjectMapper（`@Autowired(required=false)` 留空）；`warmup()` 触发 6 locale 加载；测试点：normalize（zh_CN → zh-CN，lang 小写 / country 大写）+ 回退链（zh-TW → zh-CN → en-US）+ 占位符 ${} 渲染 + 未注册 locale → en-US | `BookingConfirmationTemplateResolverTest.java` (全文) |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | (测试代码静态可达；Phase 5 跑 mvn test 验证) |
| Evidence | `audit/evidence/F109-resolver-test/` | 待建（Phase 5+）|
| Issue | — | |

---

### F110 — 测试 / EmailSenderTest + WechatOfficialAccountSenderTest

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F110 | `feature-inventory.md` §1.4.2 row 30 |
| 前端路由 | — | (无；JUnit 测试) |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`EmailSenderTest + WechatOfficialAccountSenderTest`** | `backend/src/test/java/com/meiyetec/service/channel/EmailSenderTest.java` + `WechatOfficialAccountSenderTest.java` |
| Mapper | — | (Mockito mock MktChannelConfigMapper) |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | (测试) |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | Phase 5 跑 JUnit |
| DB Status | ✅ N/A | |
| Business Status | EmailSenderTest 6 测试点（channelType=EMAIL + receiver 邮箱校验 + demo 模式（mapper null 时仍 success）+ healthCheck=false）；WechatOfficialAccountSenderTest 同模式（OA sender 测试 — **Phase 1 新发现**，preflight §7.2 仅列 3 测试，实际有 4 测试）| `EmailSenderTest.java` (全文 88 行) + `WechatOfficialAccountSenderTest.java` |
| E2E Status | UNVERIFIED | Phase 5 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | (测试代码静态可达；Phase 5 跑 mvn test 验证) |
| Evidence | `audit/evidence/F110-sender-tests/` | 待建（Phase 5+）|
| Issue | — | |

---

### F111 — 前端 / 页面 / intl-booking/index.vue（2 tab + 3 对话框）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F111 | `feature-inventory.md` §1.4.2 row 31 |
| 前端路由 | `/intl-booking` | (待 Phase 2 grep router/index.ts 确认) |
| 页面 | **`views/intl-booking/index.vue`** | `frontend/meiyetec-web/src/views/intl-booking/index.vue` (全文 134 行) |
| UI板块 | 「渠道连接」tab (L4-22) + 「国际预约」tab table (L25-50) + 3 对话框（Google OAuth L54-64 + Instagram OAuth L66-76 + 模板预览 L78-86）| `index.vue:4-86` |
| Action | (参见 F095-F106 各 Action 行)| |
| Handler | `load() / setStatus() / convert() / viewTemplate() / pull() / connectGoogle() / connectInstagram() / doGoogle() / doInstagram()` | `index.vue:91-124` |
| API | `intlBookingApi` (12 端点封装) | `frontend/meiyetec-web/src/api/intl-booking.ts:1-31` |
| Method | (参见 F095-F106 各 Method 行)| |
| Controller | — | (前端) |
| Service | — | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | Phase 5 E2E (Playwright 降级) |
| DB Status | ✅ N/A | (前端) |
| Business Status | **⚠ Shadow I040 + I041**：状态确认按钮（L40）无 loading 状态 / 无 `el-popconfirm` 二次确认 / 无"确认即发送通知"文案提示 — 直接 `setStatus(row, 'CONFIRMED')` 触发自动发送；**无 `autoSend=false` UI** 暴露；`ElMessage.success('已更新状态')` 提示用户无感 | `index.vue:40` + `:113-117` (setStatus) |
| E2E Status | UNVERIFIED | Phase 5 E2E 降级 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER**（Shadow）| **ISSUE-I040**（autoSend=false UI 缺失）+ **I041**（按钮文案缺失）；**SoT §0.3 明确要求文案提示**；Fix Phase = Post-Audit 人类决策 |
| Evidence | `audit/evidence/F111-intl-booking-page/` | 待建（Phase 5+）|
| Issue | **I040 + I041** | |

---

### F112 — 前端 / API / intl-booking.ts（12 端点封装）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F112 | `feature-inventory.md` §1.4.2 row 32 |
| 前端路由 | `/intl-booking` | (伴随 F111) |
| 页面 | — | (API 封装层) |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | **`intlBookingApi`** | `frontend/meiyetec-web/src/api/intl-booking.ts:1-31` |
| Method | (混合 GET / POST) | |
| Controller | — | (前端) |
| Service | — | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | Phase 5 E2E |
| DB Status | ✅ N/A | (前端) |
| Business Status | 12 端点封装：OAuth × 4（connectGoogle / googleCallback / connectInstagram / instagramCallback，L6-9）+ listChannels（L18）+ pullBookings（L20）+ listBookings（L21-22）+ getBooking（L23）+ updateStatus（L24-25）+ convertBooking（L28）+ confirmationTemplate（L30）；**⚠ Shadow I042**：`sendConfirmation` 方法**缺失**（后端 `POST /intl-booking/{id}/send-confirmation` 端点存在但前端无对应入口；当前只能通过 `updateStatus(id, {status: 'CONFIRMED'})` 间接触发自动发送，无法独立手动重发）| `intl-booking.ts:1-31` |
| E2E Status | UNVERIFIED | Phase 5 E2E 降级 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER**（Shadow）| **ISSUE-I042**（`sendConfirmation` 方法缺失 — 后端端点存在但前端无入口；Fix Phase = Post-Audit 人类决策）|
| Evidence | `audit/evidence/F112-intl-booking-api/` | 待建（Phase 5+）|
| Issue | **I042** | |

---

### F113 — 业务入口 / 命名异味 / IntlBookingService `@RestController` 命名误导

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F113 | `feature-inventory.md` §1.4.2 row 33 |
| 前端路由 | `/intl-booking` | |
| 页面 | — | (架构问题) |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | **`IntlBookingService` ⚠ `@RestController @RequestMapping("/intl-booking")`**（命名误导）| `backend/src/main/java/com/meiyetec/service/IntlBookingService.java:38-39` |
| Service | (实为 Controller；与项目其他 Controller 在 `controller/` 包下不一致)| |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | (架构异味) |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | (不影响功能) |
| DB Status | ✅ N/A | |
| Business Status | 与 Batch 1.1 I004 / 1.2 I019 / 1.3 I029 **完全同模式（第 4 次出现）**：文件名 `*Service` 但实际 `@RestController @RequestMapping("/intl-booking")`（L38-39），挂在 `service/` 包下（项目其他 Controller 在 `controller/` 包）；13 个 REST 端点全在一个文件里 — **不阻塞 Phase 2/3/5**，记入后续重构 backlog | `IntlBookingService.java:38-39` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **BACKLOG**（架构异味）| **ISSUE-I044**（命名异味 — 同模式第 4 次出现；建议 Post-Audit 统一整改 — 不阻塞 Phase 2/3/5）|
| Evidence | `audit/evidence/F113-intl-booking-naming/` | 待建（Phase 5+）|
| Issue | **I044** | |

---

