
---

## Batch 1.4 — EXT-19 多语言预约确认（33 Features）

> **Batch 起点**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2/1.3 freeze commit 沿用）
> **Feature 编号策略**：接续 Batch 1.3 的 F056-F080 → 本批次 **F081-F113（33 Features）**
> **SoT**：`ext-19-operational-notes.mdc`（Operational Notes）
> **DB 实际状态（Phase 2 静态验证）**：
> - `overseas_booking` **不存在**（db-snapshot.sql grep 0 匹配 — **I038 MISSING 模式**）
> - `member_message` **不存在**（db-snapshot.sql grep 0 匹配 — **I045 MISSING 模式**）
> - `pos_store` 存在（db-snapshot.sql 5 匹配；`store_name / contact_phone` 字段待 Phase 5 验证）
> - `mkt_channel_config` 存在（db-snapshot.sql 匹配；5 channel sender 依赖）

---

### F081 — 海外预约 / Entity

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F081 | `feature-inventory.md` §1.4.2 row 1 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/router/index.ts`（待 grep 确认） |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 「国际预约」tab（间接通过 API） | |
| Action | （无直接前端入口；后端通过 webhook / OAuth / pull 写库） | |
| Handler | — | |
| API | `intlBookingApi.listBookings / getBooking / updateStatus` | `frontend/meiyetec-web/src/api/intl-booking.ts:12-16` |
| Method | POST / GET | |
| Controller | `IntlBookingService.updateStatus(@PathVariable Long id, @RequestBody Map body)` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java:185-210`** ⚠ 命名异味（I044） |
| Service | `OverseasBookingMapper.selectById / insert / update` | `backend/src/main/java/com/meiyetec/mapper/OverseasBookingMapper.java:5` |
| Mapper | `OverseasBookingMapper` | |
| DB表 | `overseas_booking` | **❌ MISSING（db-snapshot.sql 0 匹配）** |
| 关联表 | `pos_store`（查询 storeName / storePhone）| |
| 读/写 | R/W（间接通过 F095-F106 各端点） | |
| 事务 | ❌ 无独立事务注解（各端点独立） | |
| 权限 | 租户隔离（`tenantId` 过滤） | `IntlBookingService.java:各端点` |
| 软删除 | ❌（无 `deleted` 列） | `OverseasBooking.java:11-31` |
| 审计字段 | `created_at / updated_at`（MyBatis auto-fill） | `OverseasBooking.java` |
| **Static Status** | L0 | 代码静态可达 |
| **Runtime Status** | UNVERIFIED | 表不存在，启动期会报 TableNotFoundException |
| **DB Status** | ❌ MISSING（I038 — I002/I017/I029 模式；统一 Post-Audit 跑迁移） | |
| **Business Status** | 完整（`miniProgramOpenId` 字段 L21；26 列定义完整） | |
| **E2E Status** | UNVERIFIED | Playwright 未配置 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql — overseas_booking 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | I038（表缺失，不跑迁移） |
| Evidence | `audit/evidence/F081-overseas-booking-entity/` | 待建（Phase 5+） |
| Issue | I038 | |

---

### F082 — 海外预约 / DB

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F082 | `feature-inventory.md` §1.4.2 row 2 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （无前端入口） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | — | |
| Mapper | — | |
| DB表 | `overseas_booking` | **❌ MISSING（db-snapshot.sql 0 匹配）** |
| 关联表 | — | |
| 读/写 | W（建表） | |
| 事务 | ❌ | |
| 权限 | — | |
| 软删除 | ❌（无 deleted 列） | |
| 审计字段 | `created_at / updated_at`（DDL L21-22）| |
| **Static Status** | L0 | DDL 静态存在 |
| **Runtime Status** | UNVERIFIED | 表不存在 |
| **DB Status** | ❌ MISSING（I038） | |
| **Business Status** | 完整（26 列 + 4 索引 + `ADD COLUMN IF NOT EXISTS mini_program_open_id`） | |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | I038 |
| Evidence | `audit/evidence/F082-overseas-booking-ddl/` | 待建（Phase 5+） |
| Issue | I038 | |

---

### F083 — 预约确认 / Service

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F083 | `feature-inventory.md` §1.4.2 row 3 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （间接通过 F095/F096 端点触发） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `BookingConfirmationService.sendConfirmation(OverseasBooking b)` | **`backend/src/main/java/com/meiyetec/booking/BookingConfirmationService.java:69-115`** |
| Service | 内部：buildVars → resolver.resolve → 3 渠道路由（SMS/EMAIL/MINI_PROGRAM）→ 聚合 BookingConfirmationResult | |
| Mapper | — | |
| DB表 | `overseas_booking`（读）+ `pos_store`（间接经 F087 查询）| |
| 关联表 | `mkt_channel_config`（5 sender 各查）| |
| 读/写 | R | |
| 事务 | ❌ 无独立事务注解 | |
| 权限 | 租户隔离（tenantId 过滤） | |
| 软删除 | ❌（overseas_booking 无 deleted 列）| |
| 审计字段 | — | |
| **Static Status** | L0 | 代码静态可达 |
| **Runtime Status** | UNVERIFIED | overseas_booking / pos_store / mkt_channel_config 表缺失或不完整 |
| **DB Status** | ❌ MISSING（I038 overseas_booking）+ ⚠ pos_store / mkt_channel_config 待 Phase 5 | |
| **Business Status** | 完整（步骤与 SoT §0.3 一致；I008 模式：autoSend 异常 catch 内不回滚）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | 依赖表缺失 + 凭据缺失 |
| Evidence | `audit/evidence/F083-booking-confirmation-service/` | 待建 |
| Issue | I038 / I049 | |

---

### F084 — 预约确认 / i18n

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F084 | `feature-inventory.md` §1.4.2 row 4 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （间接通过 F083 调用） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `BookingConfirmationTemplateResolver.resolve(locale, vars)` | **`backend/src/main/java/com/meiyetec/booking/BookingConfirmationTemplateResolver.java:64-111`** |
| Service | `warmup()` @PostConstruct 预热 6 locale；`resolveWithFallback` 回退链；`normalize` 归一化 | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R（仅读 classpath 资源）| |
| 事务 | ❌ | |
| 权限 | 无（仅 classpath 资源）| |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 代码 + 6 locale JSON 静态存在 |
| **Runtime Status** | L3（warmup 可静态验证） | |
| **DB Status** | N/A（无 DB 依赖）| |
| **Business Status** | 完整（6 locale + 回退链 + normalize 与 SoT §0.5 §4 完全一致）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 6 locale JSON 内容完整（F107 覆盖）；warmup 需 Phase 5 启动验证 |
| Evidence | `audit/evidence/F084-template-resolver/` | 待建 |
| Issue | — | |

---

### F085 — 预约确认 / DTO

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F085 | `feature-inventory.md` §1.4.2 row 5 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （无前端入口；由 F083 + F084 构造） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `BookingConfirmationTemplate` DTO | **`backend/src/main/java/com/meiyetec/booking/BookingConfirmationTemplate.java:14-32`** |
| Service | 8 字段：resolvedLocale / usedFallback / subject / sms / miniProgram / miniProgramTitle / emailSubject / emailBody | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 代码静态存在 |
| **Runtime Status** | L3 | |
| **DB Status** | N/A | |
| **Business Status** | 完整（8 字段与 SoT §1 完全一致）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 依赖 F084 warmup；F097 用硬编码 switcher 不走此 DTO（I043）|
| Evidence | `audit/evidence/F085-confirmation-template/` | 待建 |
| Issue | I043 | |

---

### F086 — 预约确认 / DTO

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F086 | `feature-inventory.md` §1.4.2 row 6 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （无前端入口；由 F083 构造返回） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `BookingConfirmationResult` DTO | **`backend/src/main/java/com/meiyetec/booking/BookingConfirmationResult.java:11-32`** |
| Service | 字段：overallSuccess / resolvedLocale / usedFallback / bookingId / channelResults | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 代码静态存在 |
| **Runtime Status** | L3 | |
| **DB Status** | N/A | |
| **Business Status** | 完整（overallSuccess 自动累加与 SoT §0.3 完全一致）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 依赖 F083 真实调用 |
| Evidence | `audit/evidence/F086-confirmation-result/` | 待建 |
| Issue | — | |

---

### F087 — 预约确认 / 查询

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F087 | `feature-inventory.md` §1.4.2 row 7 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （间接通过 F083 调用） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `StoreLookupService.lookup(storeId)` | **`backend/src/main/java/com/meiyetec/booking/StoreLookupService.java:54-76`** |
| Service | JdbcTemplate 直查 `pos_store.store_name + contact_phone`；5 分钟缓存 | |
| Mapper | — | |
| DB表 | `pos_store` | ⚠ 存在（db-snapshot.sql 5 匹配）；字段对齐待 Phase 5 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | ✅ `WHERE deleted=0` | `StoreLookupService.java:60` |
| 审计字段 | — | |
| **Static Status** | L0 | 代码静态可达；pos_store 表存在 |
| **Runtime Status** | UNVERIFIED | `store_name / contact_phone` 字段类型对齐待 Phase 5 |
| **DB Status** | ⚠ PARTIAL（表存在；字段对齐待验证）| |
| **Business Status** | 完整（5 分钟缓存 + 兜底常量与 SoT §0.4 完全一致）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql pos_store 5 匹配 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | pos_store 字段对齐待 Phase 5（I046）|
| Evidence | `audit/evidence/F087-store-lookup/` | 待建 |
| Issue | I046 | |

---

### F088 — 渠道 / SMS

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F088 | `feature-inventory.md` §1.4.2 row 8 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （间接通过 F083 路由） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `SmsSender.send()` | **`backend/src/main/java/com/meiyetec/service/channel/SmsSender.java:46-90`** |
| Service | `channelType() = "SMS"`；`HttpUtil.sendAliYunSms`；`render(var)` 占位符替换 | |
| Mapper | — | |
| DB表 | `mkt_channel_config`（间接）| ⚠ 存在（db-snapshot.sql 匹配）；凭据待 Phase 5 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | — | |
| 审计字段 | — | |
| **Static Status** | L0 | 代码静态可达 |
| **Runtime Status** | UNVERIFIED | 无真实阿里云 accessKey；Mock 降级模式 |
| **DB Status** | ⚠ 存在（mkt_channel_config）；SMS 凭据待 Phase 5 | |
| **Business Status** | 完整（render 占位符 + normalizePhone + demoMode 与 SoT §1 §4 一致）| |
| **E2E Status** | UNVERIFIED | 凭据缺失（D10 铁律）|
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | I049（凭据缺失）|
| Evidence | `audit/evidence/F088-sms-sender/` | 待建 |
| Issue | I049 | |

---

### F089 — 渠道 / EMAIL

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F089 | `feature-inventory.md` §1.4.2 row 9 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （间接通过 F083 路由） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `EmailSender.send()` | **`backend/src/main/java/com/meiyetec/service/channel/EmailSender.java:60-114`** |
| Service | `channelType() = "EMAIL"`；三模式：租户 cfg / 全局 spring.mail.* / demo | |
| Mapper | — | |
| DB表 | `mkt_channel_config`（间接）| ⚠ 存在 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | — | |
| 审计字段 | — | |
| **Static Status** | L0 | 代码静态可达；三模式完整实现 |
| **Runtime Status** | UNVERIFIED | 无真实 SMTP 凭据；mismatch demo 模式可观测日志 |
| **DB Status** | ⚠ 存在；SMTP 凭据待 Phase 5 | |
| **Business Status** | 完整（三模式与 SoT §0.2 完全一致；日志关键词 `[EMAIL] tenant SMTP host/user mismatch` 已确认）| |
| **E2E Status** | UNVERIFIED | 凭据缺失 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | I049（凭据缺失）；mismatch demo 模式可验证 |
| Evidence | `audit/evidence/F089-email-sender/` | 待建 |
| Issue | I049 | |

---

### F090 — 渠道 / MINI_PROGRAM

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F090 | `feature-inventory.md` §1.4.2 row 10 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （间接通过 F083 路由；`miniProgramOpenId` 非空时） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `MiniProgramSender.send()` | **`backend/src/main/java/com/meiyetec/service/channel/MiniProgramSender.java:48-100`** |
| Service | `channelType() = "MINI_PROGRAM"`；AccessToken 缓存 7100s；errcode 40001 清缓存 | |
| Mapper | — | |
| DB表 | `mkt_channel_config`（间接）| ⚠ 存在 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | — | |
| 审计字段 | — | |
| **Static Status** | L0 | 代码静态可达；AccessToken 缓存完整实现 |
| **Runtime Status** | UNVERIFIED | 无真实 appId/appSecret；MP_TEMPLATE_ID = "BOOKING_CONFIRMATION" 占位（SoT §0.1）|
| **DB Status** | ⚠ 存在；WeChat 凭据待 Phase 5 | |
| **Business Status** | 完整（TOKEN_CACHE_MS / errcode 处理 / buildSubscribeData 与 SoT §0.1 一致）| |
| **E2E Status** | UNVERIFIED | 凭据缺失（D10 铁律）|
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | I049（凭据缺失）+ I038（overseas_booking 缺失影响路由条件）|
| Evidence | `audit/evidence/F090-mini-program-sender/` | 待建 |
| Issue | I038 / I049 | |

---

### F091 — 渠道 / IN_APP

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F091 | `feature-inventory.md` §1.4.2 row 11 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （间接通过 F083 未路由；其他 M09 业务可调用） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `InAppSender.send()` | **`backend/src/main/java/com/meiyetec/service/channel/InAppSender.java:42-87`** |
| Service | `channelType() = "IN_APP"`；写 `member_message` 表；`@Transactional(rollbackFor = Exception.class)` | |
| Mapper | — | |
| DB表 | `member_message` | **❌ MISSING（db-snapshot.sql 0 匹配）** |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | ✅ `@Transactional(rollbackFor = Exception.class)` | `InAppSender.java:43` |
| 权限 | 无 | |
| 软删除 | — | |
| 审计字段 | — | |
| **Static Status** | L0 | 代码静态可达 |
| **Runtime Status** | UNVERIFIED | member_message 表不存在 |
| **DB Status** | ❌ MISSING（I045 — I002/I017/I029 模式）| |
| **Business Status** | 完整（F083 未路由 IN_APP，与 SoT §4 一致；ChannelDispatcher 可被其他业务调用）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql — member_message 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | I045（表缺失，不跑迁移）|
| Evidence | `audit/evidence/F091-in-app-sender/` | 待建 |
| Issue | I045 | |

---

### F092 — 渠道 / WECHAT_OFFICIAL_ACCOUNT

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F092 | `feature-inventory.md` §1.4.2 row 12 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （间接通过 F083 未路由；其他业务可调用） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `WechatOfficialAccountSender.send()` | **`backend/src/main/java/com/meiyetec/service/channel/WechatOfficialAccountSender.java:53-113`** |
| Service | `channelType() = "WECHAT_OFFICIAL_ACCOUNT"`（⚠ SoT §1 描述 `WECHAT_OA` — I039）；AccessToken 缓存 | |
| Mapper | — | |
| DB表 | `mkt_channel_config`（间接）| ⚠ 存在 |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | — | |
| 审计字段 | — | |
| **Static Status** | L0 | 代码静态可达；AccessToken 缓存完整 |
| **Runtime Status** | UNVERIFIED | 无真实 appId/appSecret |
| **DB Status** | ⚠ 存在；WeChat OA 凭据待 Phase 5 | |
| **Business Status** | ⚠ CONFLICT（I039）— 功能完整，别名漂移 | |
| **E2E Status** | UNVERIFIED | 凭据缺失 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | — | |
| **Final Result** | **CONFLICT** | I039（别名 WECHAT_OA vs WECHAT_OFFICIAL_ACCOUNT）|
| Evidence | `audit/evidence/F092-wechat-oa-sender/` | 待建 |
| Issue | I039 / I049 | |

---

### F093 — 渠道 / 接口

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F093 | `feature-inventory.md` §1.4.2 row 13 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （无前端入口；5 个 Sender 实现此接口） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `ChannelSender` 接口 | **`backend/src/main/java/com/meiyetec/service/channel/ChannelSender.java:9-37`** |
| Service | 3 方法：channelType() / send() / healthCheck()；内嵌 SendResult | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 接口契约静态存在 |
| **Runtime Status** | L3 | |
| **DB Status** | N/A | |
| **Business Status** | 完整（与 SoT §1 §4 完全一致）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 5 个实现类（I049/I045/I039 依赖）|
| Evidence | `audit/evidence/F093-channel-sender-interface/` | 待建 |
| Issue | — | |

---

### F094 — 渠道 / 分发

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F094 | `feature-inventory.md` §1.4.2 row 14 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （无前端入口；ChannelDispatcher 由业务层调用） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `ChannelDispatcher.getSender(channelType)` + `healthCheckAll()` + `rateLimitCheck()` | **`backend/src/main/java/com/meiyetec/service/channel/ChannelDispatcher.java:34-90`** |
| Service | 按 channelType filter；healthCheckAll 仅更新 tenant_id=1（T07-D4 例外）；rateLimit 查 marketing_touch_log | |
| Mapper | — | |
| DB表 | `mkt_channel_config`（写）+ `marketing_touch_log`（读）| |
| 关联表 | — | |
| 读/写 | R/W | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | — | |
| 审计字段 | — | |
| **Static Status** | L0 | 代码静态可达 |
| **Runtime Status** | UNVERIFIED | 依赖表存在性 |
| **DB Status** | ⚠ mkt_channel_config / marketing_touch_log 存在待确认 | |
| **Business Status** | ⚠ PARTIAL-NON-BLOCKER（I050 — healthCheckAll 仅更新 tenant_id=1，T07-D4 例外声明）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I050（单租户 healthCheck；I045 影响 InAppSender）|
| Evidence | `audit/evidence/F094-channel-dispatcher/` | 待建 |
| Issue | I045 / I050 | |

---

### F095 — 预约确认 / 端点

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F095 | `feature-inventory.md` §1.4.2 row 15 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | ⚠ **Shadow I042** — 前端无独立手动触发入口 | |
| Handler | — | |
| API | ⚠ `intlBookingApi.sendConfirmation` **不存在** | `frontend/meiyetec-web/src/api/intl-booking.ts`（grep `send-confirmation` 0 匹配）|
| Method | POST | |
| Controller | `IntlBookingService.sendConfirmation(@PathVariable Long id)` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java`** |
| Service | 独立于状态变更；返回 `BookingConfirmationResult` | |
| Mapper | `OverseasBookingMapper.selectById` | |
| DB表 | `overseas_booking`（读）| **❌ MISSING（I038）** |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ 无独立事务注解 | |
| 权限 | 租户隔离（selectById 自带） | |
| 软删除 | ❌（无 deleted 列）| |
| 审计字段 | — | |
| **Static Status** | L0 | 端点代码静态可达 |
| **Runtime Status** | UNVERIFIED | 表缺失 |
| **DB Status** | ❌ MISSING（I038）| |
| **Business Status** | ⚠ Shadow（I042 — 后端存在但前端无调用入口；店长无法手动重发）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **SHELL-INTENTIONAL** | I038（表缺失）+ I042（前端 Shadow）|
| Evidence | `audit/evidence/F095-send-confirmation/` | 待建 |
| Issue | I038 / I042 | |

---

### F096 — 预约确认 / 端点

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F096 | `feature-inventory.md` §1.4.2 row 16 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 「国际预约」tab — 操作列「确认」按钮 | `intl-booking/index.vue` |
| Action | 点击「确认」→ 调用 updateStatus → 触发 autoSend | ⚠ Shadow I040/I041 |
| Handler | `setStatus(row, 'CONFIRMED')` | `intl-booking/index.vue:66`（待确认行号）|
| API | `intlBookingApi.updateStatus(id, body)` | `frontend/meiyetec-web/src/api/intl-booking.ts:17` |
| Method | POST | |
| Controller | `IntlBookingService.updateStatus(id, body)` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java:185-210`** |
| Service | autoSend 逻辑：非 CONFIRMED → CONFIRMED 时自动调用 sendConfirmation；`body.autoSend=false` 可关闭 | |
| Mapper | `OverseasBookingMapper.update` | |
| DB表 | `overseas_booking`（读+写）| **❌ MISSING（I038）** |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无独立事务注解（I048 — I009 模式）| |
| 权限 | 租户隔离 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 代码静态可达 |
| **Runtime Status** | UNVERIFIED | 表缺失 |
| **DB Status** | ❌ MISSING（I038）| |
| **Business Status** | ⚠ Shadow（I040 — 不传 autoSend=false 自动触发；I041 — 按钮无文案提示）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **SHELL-INTENTIONAL** | I038（表缺失）+ I040（autoSend Shadow）+ I041（按钮文案 Shadow）+ I048（rollbackFor）|
| Evidence | `audit/evidence/F096-update-status/` | 待建 |
| Issue | I038 / I040 / I041 / I048 | |

---

### F097 — 预约确认 / 端点

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F097 | `feature-inventory.md` §1.4.2 row 17 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 「国际预约」tab — 操作列「确认模板」按钮 + templateDialog | |
| Action | 点击「确认模板」→ 预览多语言模板 | |
| Handler | `viewTemplate(row.id)` | `intl-booking/index.vue` |
| API | `intlBookingApi.confirmationTemplate(id)` | `frontend/meiyetec-web/src/api/intl-booking.ts:21` |
| Method | GET | |
| Controller | `IntlBookingService.confirmationTemplate(id)` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java`** ⚠ **I043 CONFLICT** |
| Service | ⚠ **硬编码 switcher**（zh/zh-cn/zh-tw/ja/ko/default）；未走 BookingConfirmationTemplateResolver | |
| Mapper | `OverseasBookingMapper.selectById` | |
| DB表 | `overseas_booking`（读）| **❌ MISSING（I038）** |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 租户隔离 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 端点代码静态可达 |
| **Runtime Status** | UNVERIFIED | 表缺失 |
| **DB Status** | ❌ MISSING（I038）| |
| **Business Status** | ⚠ **CONFLICT（I043）** — 硬编码 switcher 与 SoT §3 期望走 Resolver 体系反向；返回仅 subject+body，缺 sms/miniProgram/email 4 段 | |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **CONFLICT** | I038（表缺失）+ I043（硬编码 vs Resolver）|
| Evidence | `audit/evidence/F097-confirmation-template/` | 待建 |
| Issue | I038 / I043 | |

---

### F098 — 预约 / 端点

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F098 | `feature-inventory.md` §1.4.2 row 18 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 「国际预约」tab — el-table | |
| Action | 页面加载 → 调用 listBookings → 展示列表 | |
| Handler | `load()` | `intl-booking/index.vue` |
| API | `intlBookingApi.listBookings(params)` | `frontend/meiyetec-web/src/api/intl-booking.ts:12` |
| Method | GET | |
| Controller | `IntlBookingService.listBookings(status?, source?)` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java`** |
| Service | `LambdaQueryWrapper<OverseasBooking>` 按 tenantId 过滤 + id DESC | |
| Mapper | `OverseasBookingMapper.selectList` | |
| DB表 | `overseas_booking`（读）| **❌ MISSING（I038）** |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 租户隔离 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 端点代码静态可达 |
| **Runtime Status** | UNVERIFIED | 表缺失 |
| **DB Status** | ❌ MISSING（I038）| |
| **Business Status** | 完整（可选 status/source 过滤与 SoT §3 一致）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | I038 |
| Evidence | `audit/evidence/F098-list-bookings/` | 待建 |
| Issue | I038 | |

---

### F099 — 预约 / Webhook

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F099 | `feature-inventory.md` §1.4.2 row 19 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （Google BP 后台 push；无前端入口） | |
| Handler | — | |
| API | — | |
| Method | POST | |
| Controller | `IntlBookingService.googleWebhook(payload, token)` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java`** |
| Service | **演示模式**：解析 payload → 插 overseas_booking；`@Transactional` 无 rollbackFor（I048 模式） | |
| Mapper | `OverseasBookingMapper.insert` | |
| DB表 | `overseas_booking`（写）| **❌ MISSING（I038）** |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | ⚠ `@Transactional` 无 rollbackFor（I048）| |
| 权限 | token → tenantId 反查（演示模式返回 null 兜底 1L）| |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 端点代码静态可达 |
| **Runtime Status** | UNVERIFIED | 表缺失 |
| **DB Status** | ❌ MISSING（I038）| |
| **Business Status** | ⚠ demo 模式（I051 — token 不真换；tenantId 反查返回 null 兜底 1L）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | I038（表缺失）+ I048（rollbackFor）+ I051（demo 模式）|
| Evidence | `audit/evidence/F099-google-webhook/` | 待建 |
| Issue | I038 / I048 / I051 | |

---

### F100 — 预约 / Webhook

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F100 | `feature-inventory.md` §1.4.2 row 20 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （Meta 后台 push；无前端入口） | |
| Handler | — | |
| API | — | |
| Method | POST | |
| Controller | `IntlBookingService.instagramWebhook(payload)` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java`** |
| Service | **演示模式**：简易 NLP 关键词匹配 facial/massage/manicure/nail/hair → 插 overseas_booking；`@Transactional` 无 rollbackFor（I048）| |
| Mapper | `OverseasBookingMapper.insert` | |
| DB表 | `overseas_booking`（写）| **❌ MISSING（I038）** |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | ⚠ `@Transactional` 无 rollbackFor（I048）| |
| 权限 | tenantId 硬编码 1L | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 端点代码静态可达 |
| **Runtime Status** | UNVERIFIED | 表缺失 |
| **DB Status** | ❌ MISSING（I038）| |
| **Business Status** | ⚠ demo 模式（I051）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | I038 + I048 + I051 |
| Evidence | `audit/evidence/F100-instagram-webhook/` | 待建 |
| Issue | I038 / I048 / I051 | |

---

### F101 — 预约 / Webhook 验证

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F101 | `feature-inventory.md` §1.4.2 row 21 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （Meta webhook 验证握手；无前端入口） | |
| Handler | — | |
| API | — | |
| Method | GET | |
| Controller | `IntlBookingService.instagramWebhookVerify(mode, verifyToken, challenge)` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java`** |
| Service | 返回 hub.challenge 完成握手；演示模式：mode=subscribe + challenge 非空即返回 | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 端点代码静态可达 |
| **Runtime Status** | UNVERIFIED | 需实际 Meta 验证 |
| **DB Status** | N/A | |
| **Business Status** | 完整（演示模式与 SoT §3 一致）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 演示模式（I051）；生产需 verifyToken 比对（SoT §3 未明确）|
| Evidence | `audit/evidence/F101-instagram-verify/` | 待建 |
| Issue | I051 | |

---

### F102 — 预约 / OAuth2

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F102 | `feature-inventory.md` §1.4.2 row 22 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 「渠道连接」tab — Google / Instagram 绑定对话框 | |
| Action | 点击绑定 → window.open 跳转 OAuth URL → callback | |
| Handler | `connectGoogle / connectInstagram` | `intl-booking/index.vue` |
| API | `intlBookingApi.connectGoogle / connectInstagram / googleCallback / instagramCallback` | `frontend/meiyetec-web/src/api/intl-booking.ts:6-9` |
| Method | POST | |
| Controller | `IntlBookingService.connectGoogle / googleCallback / connectInstagram / instagramCallback` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java`** |
| Service | **演示模式**：返回真实 OAuth URL 但 callback 不真换 token，返回 `ya29.DEMO-{code前8位}` / `IGQVJ-DEMO-{code前8位}` | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 租户隔离 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 端点代码静态可达 |
| **Runtime Status** | UNVERIFIED | OAuth 演示模式 |
| **DB Status** | N/A | |
| **Business Status** | ⚠ demo 模式（I051 — 不真换 token）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | I051（demo 模式）|
| Evidence | `audit/evidence/F102-oauth2-connect/` | 待建 |
| Issue | I051 | |

---

### F103 — 预约 / 转化

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F103 | `feature-inventory.md` §1.4.2 row 23 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 「国际预约」tab — 操作列「转化为会员」按钮 | |
| Action | 点击「转化为会员」→ 调用 convertBooking → 创建会员 + 订单 | |
| Handler | `convert(row.id)` | `intl-booking/index.vue` |
| API | `intlBookingApi.convertBooking(id)` | `frontend/meiyetec-web/src/api/intl-booking.ts:19` |
| Method | POST | |
| Controller | `IntlBookingService.convertBooking(id)` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java`** |
| Service | **演示模式**：JdbcTemplate 直接 INSERT crm_member + pos_order；写回 convertedMemberId/convertedOrderId；`@Transactional` 无 rollbackFor（I048）| |
| Mapper | — | |
| DB表 | `overseas_booking`（读）+ `crm_member`（写）+ `pos_order`（写）| **❌ overseas_booking MISSING（I038）；crm_member/pos_order 存在待确认** |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ⚠ `@Transactional` 无 rollbackFor（I048）| |
| 权限 | 租户隔离 | |
| 软删除 | — | |
| 审计字段 | — | |
| **Static Status** | L0 | 端点代码静态可达 |
| **Runtime Status** | UNVERIFIED | 表缺失 |
| **DB Status** | ❌ MISSING（I038 overseas_booking）| |
| **Business Status** | 完整（演示模式；仅支持 CONFIRMED 且未转化状态可见）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | I038 + I048 |
| Evidence | `audit/evidence/F103-convert-booking/` | 待建 |
| Issue | I038 / I048 | |

---

### F104 — 预约 / 端点

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F104 | `feature-inventory.md` §1.4.2 row 24 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | （间接通过 F098 列表点开详情） | |
| Action | 点击列表行 → 调用 getBooking → 展示详情 | |
| Handler | — | |
| API | `intlBookingApi.getBooking(id)` | `frontend/meiyetec-web/src/api/intl-booking.ts:13` |
| Method | GET | |
| Controller | `IntlBookingService.getBooking(id)` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java`** |
| Service | `selectById`；null → BizCode.NOT_FOUND | |
| Mapper | `OverseasBookingMapper.selectById` | |
| DB表 | `overseas_booking`（读）| **❌ MISSING（I038）** |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 租户隔离 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 端点代码静态可达 |
| **Runtime Status** | UNVERIFIED | 表缺失 |
| **DB Status** | ❌ MISSING（I038）| |
| **Business Status** | 完整（selectById + NOT_FOUND 处理与 SoT §3 一致）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | I038 |
| Evidence | `audit/evidence/F104-get-booking/` | 待建 |
| Issue | I038 | |

---

### F105 — 预约 / 渠道列表

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F105 | `feature-inventory.md` §1.4.2 row 25 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 「渠道连接」tab — 渠道卡片网格 | |
| Action | 页面加载 → 调用 listChannels → 展示渠道列表 | |
| Handler | — | |
| API | `intlBookingApi.listChannels()` | `frontend/meiyetec-web/src/api/intl-booking.ts:11` |
| Method | GET | |
| Controller | `IntlBookingService.listChannels()` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java`** |
| Service | 4 渠道：GOOGLE/INSTAGRAM/FACEBOOK/TIKTOK（FACEBOOK/TIKTOK status=PLANNED）；纯静态枚举无 DB 查询 | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 端点代码静态可达 |
| **Runtime Status** | L3 | 无 DB 依赖 |
| **DB Status** | N/A | |
| **Business Status** | 完整（静态枚举与 SoT §3 一致）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 无 DB 依赖；FACEBOOK/TIKTOK PLANNED 状态显示 |
| Evidence | `audit/evidence/F105-list-channels/` | 待建 |
| Issue | — | |

---

### F106 — 预约 / 拉取

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F106 | `feature-inventory.md` §1.4.2 row 26 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/router/index.ts` |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | |
| UI板块 | 「渠道连接」tab — 拉取预约按钮 | |
| Action | 点击「拉取预约」→ 调用 pullBookings → 演示模式插 1 条 | |
| Handler | — | |
| API | `intlBookingApi.pullBookings(channelId)` | `frontend/meiyetec-web/src/api/intl-booking.ts:10` |
| Method | POST | |
| Controller | `IntlBookingService.pullBookings(channelId)` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java`** |
| Service | **演示模式**：GOOGLE/INSTAGRAM 插 1 条示例 overseas_booking；FACEBOOK/TIKTOK → NOT_FOUND | |
| Mapper | `OverseasBookingMapper.insert` | |
| DB表 | `overseas_booking`（写）| **❌ MISSING（I038）** |
| 关联表 | — | |
| 读/写 | W | |
| 事务 | ❌ | |
| 权限 | 租户隔离 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 端点代码静态可达 |
| **Runtime Status** | UNVERIFIED | 表缺失 |
| **DB Status** | ❌ MISSING（I038）| |
| **Business Status** | ⚠ demo 模式（I051）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | I038 + I051 |
| Evidence | `audit/evidence/F106-pull-bookings/` | 待建 |
| Issue | I038 / I051 | |

---

### F107 — 预约确认 / i18n

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F107 | `feature-inventory.md` §1.4.2 row 27 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （间接通过 F083/F084 读取 classpath 资源） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | 6 个资源文件：`resources/i18n/template/{zh-CN,zh-TW,en-US,ja-JP,ko-KR,fr-FR}.json` | **`backend/src/main/resources/i18n/template/`** |
| Service | `BookingConfirmationTemplateResolver.warmup()` 预热 6 locale；`resolve(locale, vars)` 渲染 | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 6 locale JSON 静态存在；内容完整性 Phase 1 §1.4.6 交叉验证通过 |
| **Runtime Status** | L3 | warmup() 静态可验证；需 Phase 5 启动日志验证 |
| **DB Status** | N/A | |
| **Business Status** | ✅ 完整（6 locale + 10 字段/文件 + 7 占位符与 SoT §1 §5 完全一致）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | warmup 需 Phase 5 启动验证 |
| Evidence | `audit/evidence/F107-i18n-templates/` | 待建 |
| Issue | — | |

---

### F108 — 测试

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F108 | `feature-inventory.md` §1.4.2 row 28 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （JUnit5 测试） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `BookingConfirmationServiceTest` | **`backend/src/test/java/com/meiyetec/booking/BookingConfirmationServiceTest.java`** |
| Service | StubSmsSender / StubEmailSender 桩；Mockito mock StoreLookupService + resolver；渠道路由 + overallSuccess 聚合 | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 测试代码静态存在 |
| **Runtime Status** | L1（需 `mvn test` 验证）| |
| **DB Status** | N/A | |
| **Business Status** | 完整（测试覆盖渠道路由 + overallSuccess + null 安全 + ChannelSender 缺失降级）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 需 mvn test 实际运行验证 |
| Evidence | `audit/evidence/F108-confirmation-service-test/` | 待建 |
| Issue | — | |

---

### F109 — 测试

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F109 | `feature-inventory.md` §1.4.2 row 29 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （JUnit5 测试） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `BookingConfirmationTemplateResolverTest` | **`backend/src/test/java/com/meiyetec/booking/BookingConfirmationTemplateResolverTest.java`** |
| Service | 反射注入 ObjectMapper；warmup 触发 6 locale 加载；normalize + fallback + 占位符渲染 | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 测试代码静态存在 |
| **Runtime Status** | L1（需 `mvn test` 验证）| |
| **DB Status** | N/A | |
| **Business Status** | 完整（normalize zh_CN→zh-CN + 回退链 zh-TW→zh-CN→en-US + 未注册→en-US）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 需 mvn test 实际运行验证 |
| Evidence | `audit/evidence/F109-template-resolver-test/` | 待建 |
| Issue | — | |

---

### F110 — 测试

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F110 | `feature-inventory.md` §1.4.2 row 30 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （JUnit5 测试 × 2） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `EmailSenderTest` + `WechatOfficialAccountSenderTest` | **`backend/src/test/java/com/meiyetec/service/channel/EmailSenderTest.java`** + `WechatOfficialAccountSenderTest.java` |
| Service | EmailSenderTest：channelType/receiver 校验/demo 模式/healthCheck；WechatOfficialAccountSenderTest：同模式 | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 无 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 测试代码静态存在（Phase 1 新发现 4 测试 vs SoT §1 列 3 测试）|
| **Runtime Status** | L1（需 `mvn test` 验证）| |
| **DB Status** | N/A | |
| **Business Status** | 完整（EmailSender mismatch demo 模式 + WechatOA sender 链路）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 需 mvn test 实际运行验证 |
| Evidence | `audit/evidence/F110-channel-sender-tests/` | 待建 |
| Issue | — | |

---

### F111 — 前端 / 页面

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F111 | `feature-inventory.md` §1.4.2 row 31 |
| 前端路由 | `/intl-booking` | `frontend/meiyetec-web/src/router/index.ts`（待 grep 确认） |
| 页面 | `frontend/meiyetec-web/src/views/intl-booking/index.vue` | `frontend/meiyetec-web/src/views/intl-booking/index.vue:1-150+` |
| UI板块 | 2 tab（渠道连接 + 国际预约）+ 3 对话框（Google OAuth / Instagram OAuth / 模板预览）| |
| Action | 列表查询 / 状态确认 / 模板预览 / 转化为会员 / 拉取预约 | |
| Handler | `load()` / `setStatus()` / `viewTemplate()` / `convert()` / `pullBookings()` | `intl-booking/index.vue` |
| API | `intlBookingApi` 12 端点 | `frontend/meiyetec-web/src/api/intl-booking.ts:1-31` |
| Method | POST / GET | |
| Controller | 间接通过 IntlBookingService 端点 | `backend/src/main/java/com/meiyetec/service/IntlBookingService.java` |
| Service | — | |
| Mapper | — | |
| DB表 | `overseas_booking`（间接）| **❌ MISSING（I038）** |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌（前端无事务）| |
| 权限 | 前端 token 携带 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | Vue 文件静态存在 |
| **Runtime Status** | UNVERIFIED | 表缺失 + 依赖后端 |
| **DB Status** | ❌ MISSING（I038）| |
| **Business Status** | ⚠ **Shadow（I040/I041/I042）** — 确认按钮无 loading/二次确认/文案提示；autoSend=false UI 缺失；sendConfirmation API 缺失 | |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **SHELL-INTENTIONAL** | I038（表缺失）+ I040（autoSend Shadow）+ I041（按钮文案 Shadow）|
| Evidence | `audit/evidence/F111-intl-booking-page/` | 待建 |
| Issue | I038 / I040 / I041 / I042 | |

---

### F112 — 前端 / API

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F112 | `feature-inventory.md` §1.4.2 row 32 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （intl-booking.ts 封装 12 端点供 F111 调用） | |
| Handler | — | |
| API | `intlBookingApi`（本身即 API 层）| `frontend/meiyetec-web/src/api/intl-booking.ts:1-31` |
| Method | POST / GET | |
| Controller | — | |
| Service | — | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R/W | |
| 事务 | ❌ | |
| 权限 | 前端 token 携带 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | TypeScript 文件静态存在 |
| **Runtime Status** | UNVERIFIED | 依赖后端 |
| **DB Status** | N/A | |
| **Business Status** | ⚠ **Shadow（I042）** — `sendConfirmation` 方法缺失（后端有 `/intl-booking/{id}/send-confirmation` 但前端无对应封装）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | N/A | |
| Last Verified At | — | |
| **Final Result** | **SHELL-INTENTIONAL** | I042（sendConfirmation API Shadow）|
| Evidence | `audit/evidence/F112-intl-booking-api/` | 待建 |
| Issue | I042 | |

---

### F113 — 业务入口 / 命名异味

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F113 | `feature-inventory.md` §1.4.2 row 33 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | （无） | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `IntlBookingService` | **`backend/src/main/java/com/meiyetec/service/IntlBookingService.java:14-17`** ⚠ **I044** |
| Service | 13 个 REST 端点全在一个 `@RestController` 文件里，文件名是 `*Service` 但实际是 Controller | |
| Mapper | — | |
| DB表 | `overseas_booking`（间接）| **❌ MISSING（I038）** |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ⚠ 多个端点无 rollbackFor（I048）| |
| 权限 | 租户隔离 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| **Static Status** | L0 | 代码静态存在 |
| **Runtime Status** | UNVERIFIED | 表缺失 |
| **DB Status** | ❌ MISSING（I038）| |
| **Business Status** | ⚠ 架构异味（I044 — 与 Batch 1.1 I004 / 1.2 I019 / 1.3 I029 完全同模式，**第 4 次出现**）| |
| **E2E Status** | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **SHELL-INTENTIONAL** | I038（表缺失）+ I044（命名异味 backlog）+ I048（rollbackFor）|
| Evidence | `audit/evidence/F113-intl-booking-service-naming/` | 待建 |
| Issue | I038 / I044 / I048 | |

---

## Batch 1.4 — Phase 2 统计表

| Result 分类 | 数量 | Feature IDs |
|---|---|---|
| **MISSING** | 8 | F081 / F082（overseas_booking 缺失，I038）；F091（member_message 缺失，I045）；F098 / F104（overseas_booking 缺失，I038）；F099 / F100 / F103 / F106（overseas_booking 缺失，I038）|
| **SHELL-INTENTIONAL** | 4 | F095（I038+I042）/ F096（I038+I040+I041）/ F111（I038+I040+I041）/ F112（I042）/ F113（I038+I044）|
| **CONFLICT** | 2 | F092（I039 WECHAT_OA 别名）/ F097（I043 硬编码 vs Resolver）|
| **UNVERIFIED** | 4 | F083（I038+I049）/ F090（I038+I049）/ F088（I049）/ F089（I049）|
| **PARTIAL-NON-BLOCKER** | 12 | F084 / F085 / F086 / F087 / F094 / F101 / F102 / F105 / F107 / F108 / F109 / F110 |
| **VERIFIED** | 0 | 无（overseas_booking / member_message 表缺失 + 凭据缺失）|
| **BROKEN** | 0 | 无 |
| **合计** | **33** | F081-F113 |

**Batch 1.4 结果分析**：
- MISSING 占比 8/33（24%）— overseas_booking 表 + member_message 表 DDL 已就位但 dev DB 未跑迁移
- SHELL-INTENTIONAL 占比 5/33（15%）— 前端 Shadow（autoSend UI / 按钮文案 / sendConfirmation API / 命名异味 backlog）
- CONFLICT 占比 2/33（6%）— WECHAT_OA 别名（I039）+ confirmation-template 硬编码 vs Resolver（I043）
- UNVERIFIED 占比 4/33（12%）— 5 渠道 sender 真实凭据缺失（D10 铁律）
- PARTIAL-NON-BLOCKER 占比 12/33（36%）— Entity / DTO / i18n / 测试 / ChannelDispatcher
- VERIFIED 0/33 — 表缺失 + 凭据缺失双重限制

---

## Batch 1.4 — Phase 2 Issue 汇总（I038-I051）

| Issue ID | 类别 | 影响 Feature | Final Result | 备注 |
|---|---|---|---|---|
| **I038** | **MISSING（DB）** | F081 / F082 / F083 / F090 / F095 / F096 / F097 / F098 / F099 / F100 / F103 / F104 / F106 / F113 | MISSING / SHELL-INTENTIONAL | overseas_booking 表缺失（I002/I017/I029 模式；统一 Post-Audit 跑迁移）|
| **I039** | **CONFLICT（中立标）** | F092 | CONFLICT | WECHAT_OA 别名 vs WECHAT_OFFICIAL_ACCOUNT；Fix Phase = Post-Audit |
| **I040** | **Shadow** | F096 / F111 | SHELL-INTENTIONAL | autoSend=false UI 缺失；Fix Phase = Post-Audit |
| **I041** | **Shadow** | F096 / F111 | SHELL-INTENTIONAL | 状态确认按钮无「确认即发送通知」文案提示；Fix Phase = Post-Audit |
| **I042** | **Shadow** | F095 / F112 | SHELL-INTENTIONAL | sendConfirmation API 缺失；Fix Phase = Post-Audit |
| **I043** | **CONFLICT（中立标）** | F097 | CONFLICT | confirmation-template 硬编码 switcher vs SoT 期望 Resolver 体系；Fix Phase = Post-Audit |
| **I044** | **架构异味（命名）** | F113 | SHELL-INTENTIONAL | `@RestController` 命名误导（I004/I019/I029 同模式，第 4 次）；backlog 统一整改 |
| **I045** | **MISSING（DB）** | F091 | MISSING | member_message 表缺失（I002/I017/I029 模式；统一 Post-Audit 跑迁移）|
| **I046** | **PARTIAL（字段对齐）** | F087 | PARTIAL-NON-BLOCKER | pos_store store_name/contact_phone 字段对齐待 Phase 5 验证 |
| **I047** | **PARTIAL-NON-BLOCKER（I008 模式）** | F083 | UNVERIFIED | autoSend 异常 catch 内不回滚（SoT §0.3 明确设计）|
| **I048** | **PARTIAL-NON-BLOCKER（I009 模式）** | F096 / F099 / F100 / F103 / F113 | MISSING / SHELL-INTENTIONAL | @Transactional 无 rollbackFor（受检异常不回滚）|
| **I049** | **UNVERIFIED（凭据缺失）** | F088 / F089 / F090 / F092 | UNVERIFIED / CONFLICT | WeChat MP / SMTP / OA / 阿里云 SMS 真实凭据缺失；D10 铁律 |
| **I050** | **PARTIAL-NON-BLOCKER（I050）** | F094 | PARTIAL-NON-BLOCKER | healthCheckAll 仅更新 tenant_id=1（T07-D4 例外声明）|
| **I051** | **PARTIAL-NON-BLOCKER（I051）** | F099 / F100 / F101 / F102 / F106 | MISSING / PARTIAL-NON-BLOCKER | webhook / OAuth 演示模式；token 不真换；Fix Phase = Post-Audit（用户决策 #6）|

---

## Batch 1.4 — Phase 2 Issue 决策状态

| Issue ID | 决策 | 来源 | 状态 |
|---|---|---|---|
| I038 | **维持 MISSING，不跑迁移**（第 4 次强调） | 用户 2026-09-14 决策 #3 | ✅ 已确认 |
| I039 | **CONFLICT 中立标**（I001/I018/I026 模式） | 用户指令 | ✅ 已确认 |
| I040 / I041 / I042 | **Shadow，Post-Audit** | 用户指令 | ✅ 已确认 |
| I043 | **CONFLICT 中立标** | 用户指令 | ✅ 已确认 |
| I044 | **backlog 统一整改**（I004/I019/I029 同模式，第 4 次命名异味） | 用户指令 | ✅ 已确认 |
| I045 | **维持 MISSING，不跑迁移**（第 4 次强调） | 用户决策 #3 | ✅ 已确认 |

---

## Batch 1.4 — 已知约束汇总

| # | 约束项 | 影响 Feature | 统一处理 |
|---|---|---|---|
| 1 | overseas_booking 表 DDL 已写但 DB 未跑（I038）| F081/F082/F083/F090/F095-F100/F103/F104/F106/F113 | **Post-Audit 统一跑迁移**（用户决策 #3 第四次强调）|
| 2 | member_message 表缺失（I045）| F091 | **Post-Audit 统一跑迁移** |
| 3 | pos_store 表存在但字段对齐待 Phase 5（I046）| F087 | Phase 5 DESCRIBE pos_store 验证 |
| 4 | WeChat MP / SMTP / OA / 阿里云 SMS 凭据缺失（I049）| F088/F089/F090/F092 | **UNVERIFIED + Mock 降级**（D10 铁律）|
| 5 | WECHAT_OA 别名漂移（I039）| F092 | **CONFLICT 中立标，Post-Audit 决策** |
| 6 | confirmation-template 硬编码 vs Resolver（I043）| F097 | **CONFLICT 中立标，Post-Audit 决策** |
| 7 | autoSend=false / 按钮文案 / sendConfirmation Shadow（I040/I041/I042）| F095/F096/F111/F112 | **Post-Audit 修复** |
| 8 | IntlBookingService @RestController 命名异味（I044）| F113 | **backlog 统一整改（第 4 次）** |
| 9 | @Transactional 无 rollbackFor（I048）| F096/F099/F100/F103/F113 | **PARTIAL-NON-BLOCKER，Post-Audit 决策** |
| 10 | webhook / OAuth 演示模式（I051）| F099/F100/F101/F102/F106 | **接受现状，Post-Audit 升级为真实链路** |
| 11 | healthCheckAll 单租户（I050）| F094 | **T07-D4 例外声明，Post-Audit 决策** |

---

## Batch 1.4 — 跨 EXT-* 引用规则

| 共享资源 | 所属 EXT-* | Batch 1.4 Feature 视角 | 备注 |
|---|---|---|---|
| `OverseasBooking` entity + `overseas_booking` 表 | EXT-15 (P3-25/26) + EXT-19（本批次）| F081 实体；F083 核心发送；F095-F106 业务端点 | Post-Audit 统一跑迁移 |
| `MktChannelConfig` + `mkt_channel_config` 表 | EXT-09 + EXT-19（本批次）| F088-F092 channel sender；F094 ChannelDispatcher | 存在待确认 |
| `member_message` 表 | EXT-09 + EXT-19（本批次）| F091 InAppSender | Post-Audit 统一跑迁移 |
| `pos_store` 表 | EXT-03 + EXT-19（本批次）| F087 StoreLookupService | 存在但字段对齐待验证 |
| `crm_member` + `pos_order` 表 | EXT-03 + EXT-19（本批次）| F103 convert（演示模式）| 存在待确认 |
| `marketing_touch_log` 表 | EXT-09 + EXT-19（本批次）| F094 ChannelDispatcher.rateLimit | 存在待确认 |
| `BookingConfirmationTemplateResolver` | EXT-19（本批次）| F084 解析器；F097 ⚠ 未走此路径（I043）| I043 CONFLICT |
| `BookingConfirmationService` | EXT-19（本批次）| F083 核心；F096 autoSend 触发 | SoT §0.3 一致 |

---

## Batch 1.4 — 继承 Batch 1.1/1.2/1.3 决策

| 决策项 | 来源 | Batch 1.4 继承 |
|---|---|---|
| D1-D6 全部维持 | Batch 1.1 | ✅ 继承 |
| Q1-Q4 全部维持 | Batch 1.2 | ✅ 继承 |
| I001-I037 全部继承 | Batch 1.1/1.2/1.3 | ✅ 继承 |
| **本批次新增决策** | 用户 2026-09-14 决策 | ✅ 已落档 |

---

## Batch 1.4 — Phase 2 最终验收汇总

| 产出物 | 位置 | 状态 |
|---|---|---|
| Batch 1.4 Traceability Matrix（本文件）| `audit/traceability-matrix.md` Batch 1.4 段 | ✅ 完成 |
| Batch 1.4 Issue Register | `audit/issues/issue-register.md` §二十三起追加 I038-I051 | ✅ 完成 |
| Batch 1.4 Feature Inventory | `audit/feature-inventory.md` §1.4（F081-F113 33 Features）| ✅ 完成 |
| Batch 1.1 Traceability Matrix（30 Features）| `audit/traceability-matrix.md` Batch 1.1 段 | ✅ 完成 |
| Batch 1.2 Traceability Matrix（25 Features）| `audit/traceability-matrix.md` Batch 1.2 段 | ✅ 完成 |
| Batch 1.3 Traceability Matrix（25 Features）| `audit/traceability-matrix.md` Batch 1.3 段 | ✅ 完成 |
| Batch 1.1 Issue Register | `audit/issues/issue-register.md` §十五（10 Feature 最终 Result + I001-I013 + I016）| ✅ 完成 |
| Batch 1.2 Issue Register | `audit/issues/issue-register.md` §十七+十八（25 Feature 最终 Result + I017-I025 + 3 项核心理决策）| ✅ 完成 |
| Batch 1.3 Issue Register | `audit/issues/issue-register.md` §十九+二十+二十一+二十二（25 Feature 最终 Result + I026-I037 + I037 NEW CRITICAL-P0 + 5 项核心理决策）| ✅ 完成 |
| Batch 1.4 验证范围声明 | `audit/traceability-matrix.md` Batch 1.4 段 | ✅ 完成 |
| 受保护页面零风险段 | `audit/traceability-matrix.md` Batch 1.4 段 | ✅ 完成 |
| 跨 EXT-* 引用规则 | `audit/traceability-matrix.md` Batch 1.4 段 | ✅ 完成 |
| 已知约束汇总 | `audit/traceability-matrix.md` Batch 1.4 段 | ✅ 完成 |
| Phase 2 启动声明 | `audit/traceability-matrix.md` Batch 1.4 段 | ✅ 完成 |

---

**Batch 1.4 Phase 2：Full-Stack Traceability Matrix 完成。33 Features 全栈追溯完成 + 统计表 + Issue 决策汇总（I038-I051）+ 已知约束汇总 + 跨 EXT-* 引用规则 + 继承决策 + Phase 2 最终验收汇总全部钀芥。审计阶段只记录不修复（规则 6）。不进入 Phase 3。等待用户确认 → 进入 Phase 4/5（Runtime Verification）。**
