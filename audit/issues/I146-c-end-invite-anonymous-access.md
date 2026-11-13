# I146 — C 端邀请端点匿名访问改造（`/invite/**` 8 端点免登录开放）

> **状态**：Parked（调查报告已通过；**实施未开始**；等待产品决策 Q1–Q5 / Q9–Q14，故挂起）
> **严重度**：P0（改造前置安全债 —— F1/F2/F3 三项发现使任务性质发生根本变化）
> **发现日期**：2026-11-13
> **登记人**：Cursor（只读调查报告）
> **绑定基线**：`audit/baseline/env.md` → HEAD commit `03173da0f72c4e0066fa407f96d083601ac1643c`（工作树 dirty；baseline 锁定为该 HEAD 的代码状态，非上线版本）
> **验证等级**：静态分析 → 运行期结论一律 **UNVERIFIED**
> **本 Issue 的任务性质已被 F1/F2/F3 改写**：原任务设定为「给 `/invite/**` 加 JWT 白名单、实现 C 端匿名访问」。调查报告证明该设定建立在三个错误前提上 —— **F1** 前端根本没有任何 C 端调用方（不是"被 JWT 挡住"，而是"后端有端点 + 前端无页面"）；**F2** `bind` / `redeem` 是没有服务端触发链路的影子端点；**F3** `invite_code` 非全局唯一，匿名场景无法用 code 反查 tenantId。因此任务从「加白名单」变为「**先补齐安全债与数据基础，再谈开放**」。
> **拆分**：调查中确认的三项**既存**安全缺陷（与匿名化无关，现在就要修）与一项**前置数据基础缺口**已拆分为独立 Issue：
> | 拆分项 | 新 Issue | 级别 | 内容 |
> |---|---|---|---|
> | Q6 | **I164** | P1 | `my-stats` 三个计数缺 `tenantId` 过滤 → 跨租户 IDOR |
> | Q7 | **I165** | P1 | `report-share` 无 `tenantId` 过滤 + 无限流 + 无幂等 |
> | Q8 | **I166** | P2 | `share-page` 隐式调用 `trackClick` → `scanCount` 双计数 |
> | Q5 / F3 | **I167** | P2 | `m09_invite_code.invite_code` 非全局唯一 → 匿名场景无法用 code 反查 tenantId（**本 Issue 的硬前置**） |
>
> **关联**：I149（M09 邀请奖励链路业务缺陷）、I148（`atomicGrant` 闸门稳定性）、I145（`issued_count`）、F150（M09 解封证据）、I164、I165、I166、I167

---

## 0.0 拆分说明（本次修订新增）

用户于 2026-11-13 审阅本调查报告后确认：**F1 / F2 / F3 三项发现改变了任务性质，批准拆分**。据此：

1. **本 Issue 保留** —— C 端匿名访问改造的方案选型、前置 DDL、前端 H5/小程序页面，以及 §6 中 Q1–Q5 / Q9–Q14 的业务决策项。原「只读调查」性质升级为「已批准、待实施」的 Open Issue。
2. **拆出 I164 / I165 / I166 / I167** —— 前三项的共同特征是：**它们是当前已存在的、可被已登录用户利用的缺陷**，与是否匿名化无关；若混在本 Issue 内，会被「匿名化方案尚未拍板」长期挂起。因此按用户决策「现在就要修」，各自独立登记、独立跟踪。**I167** 则不同：它是**前置数据基础缺口**（邀请码非全局唯一），本身不构成越权，但没有它，方案 A 的 tenantId 反查无法闭环 → 匿名端点落地会因 `selectOne` 命中多行而返回 500。故 I167 独立跟踪，但**它是本 Issue 的硬前置**，本 Issue 的实施必须等待 I167 完成。
3. **本 Issue 内不再重复修这四项** —— 修复方案与验证口径以 I164 / I165 / I166 / I167 为准；本 Issue 若后续实施匿名化，其安全防护清单需引用那几项 Issue 的结论（尤其 I165 的 `report-share` 幂等窗口、I166 的「`share-page` 不再隐式埋点」口径约定、I167 的全局唯一约束口径）。
4. **状态由 Open 调整为 Parked** —— 本 Issue 的剩余动作全部依赖产品决策（Q1 落地页形态、Q4 tenantId 判定方式、Q14 前端归属）与前置 DDL（I167），在用户拍板前**无法开工**，故挂起为 Parked（等待产品决策），而非进行中的 Open。
4. **未落盘内容之外**：本 Issue 至今**未修改任何业务代码、未执行 SQL、未启动应用、未打开浏览器**，§0 之后的所有结论仍为静态分析结果。

---



---

> 调查报告原标题：C 端邀请端点匿名访问改造 · 只读调查报告

## 0. 先说三个与任务前提冲突的发现（影响后续方案选择）

| # | 发现 | 证据 | 影响 |
|---|---|---|---|
| **F1** | **前端 8 个 C 端端点没有任何调用方**。`api/invite.ts` 的 `sharePage / trackClick / bindOnRegister / redeem / myCode / myCodes / myStats / reportShare` 全部零引用 | 全仓 grep：`frontend/**` 中 `invite` 仅命中 `views/m09-invite/index.vue`（B 端后台）与 `views/lifecycle/index.vue`（B 端测试台），后者调的是 `/lifecycle/invite/*`（**另一套** `MemberInviteController`） | 当前 C 端裂变**不是"有功能但被 JWT 挡住"，而是"后端有端点 + 前端无页面"**。匿名化改造解决的是"后端能力未对 C 端开放"，**还需要前端 H5/小程序页面才能产生价值** |
| **F2** | **`/invite/bind` 与 `/invite/redeem` 是"影子端点"**。`InviteService.bindOnRegister` 注释写「由 MemberService 注册成功后异步调用」，但 `MemberService` 中 grep `invite` **零命中**；`rewardOnFirstOrder` 注释写「由 PosController 收银完成回调时调用」，`PosController` 中 grep `invite` **零命中** | `service/MemberService.java`、`controller/PosController.java` grep 无匹配 | 这两个端点**只有 HTTP 入口，没有服务端触发链路**。匿名化它们 = 把一个「唯一的调用方式是外部 HTTP 调用」的口子开到公网，风险收益比最差 |
| **F3** | **`invite_code` 不是全局唯一**，唯一键是 `uk_campaign_code (campaign_id, invite_code)`；`generateUniqueCode(campaignId)` 也只在活动内查重 | `db/sql/init_m09_invite.sql` L89；`InviteService.generateUniqueCode` | **匿名场景无法用 code 反查 tenantId**（同一 code 可在多租户多活动并存）。这是匿名化最硬的技术阻塞点 → 📌 **已拆出为 I167（P2）**，详见 `audit/issues/I167-invite-code-global-unique-ddl.md`，**本 Issue 不再承担该项修复** |

---

## 1. 拦截器与白名单配置清单

### 1.1 `JwtInterceptor`（`backend/src/main/java/com/meiyetec/interceptor/JwtInterceptor.java`）

- 注册位置：`config/WebConfig.java` L25-26 → `addPathPatterns("/**")`（**全路径**）
- 逻辑：非 `OPTIONS` 且 `Authorization` 不以 `Bearer ` 开头 → 直接 **HTTP 401** + envelope `code=401`；token 过期 / `extractUserId` 为 null → 401；成功 → `RbacContextLoader.load(userId)` → 写 `TenantContextHolder` + MDC traceId
- `afterCompletion` 统一 `TenantContextHolder.clear()`

**当前 `excludePathPatterns` 完整清单（`WebConfig.java` L27-49）：**

| 类别 | 路径 |
|---|---|
| 认证 | `/auth/login`、`/auth/logout`、`/api/auth/login`、`/api/auth/logout` |
| 静态 | `/api/static/**`、`/static/**` |
| 错误 | `/error` |
| Webhook | `/webhook/**`、`/overseas-payment/webhook/**`、`/ecommerce/webhook/**`、`/pos/webhook/**`、`/payment/webhook/**`、`/booking/webhook/**`、`/platform/webhook/**`、`/intl-booking/webhook/**`、`/bnpl/webhook/**`、`/domestic/webhook/**` |
| 监控 | `/actuator/**` |

> **没有任何 C 端 / H5 /会员端路径在白名单里。** `/invite/**` 全部走 JWT 强制校验。
> ⚠️ **`/mh/consent/sign-link/**` 也不在白名单** —— 见 §4.1。

### 1.2 `DataScopeInterceptor`（`backend/src/main/java/com/meiyetec/security/DataScopeInterceptor.java`）

- 注册位置：`WebConfig.java` L51-52 → `addPathPatterns("/**")`（同样是全路径）
- 排除清单（`WebConfig.java` L53-73）：与 JWT 对齐 + 多一个 `/auth/refresh`
- 内部 `WHITELIST`（精确路径，L32-43）：`/auth/login`、`/auth/refresh`、`/sys/log/export`、`/report/export`、`/report/download`、`/booking/export`、`/commission/salary/export`、`/commission/settlement/export`、`/market/coupon/export`、`/stock/export` —— **不含任何 /invite 路径**
- 校验逻辑：
  - `ctx.getUserId() == null` → **直接 return true（完全放行）**
  - tenantId 来源优先级：`X-Tenant-Id` header → `?tenantId` query → JSON body 正则
  - storeId 同理（`X-Store-Id` → `?storeId` → body 正则）

**与 JWT 的重叠/冲突结论：**

| 检查项 | 结论 |
|---|---|
| 路径范围重叠 | **完全重叠**（两者都是 `/**`），但排除清单几乎一致 → 现状无冲突 |
| 冲突隐患 | 若只把 `/invite/**` 加入 JWT 白名单而**不**加入 DataScope 白名单：**不会报错**（`userId == null` 提前放行），但会产生「JWT 层放行 / DataScope 层静默跳过」的**语义不对称**，后续若有人给 DataScope 加逻辑会踩坑 |
| 请求体字段 | `/invite/track` body = `{code, phone, channel}`；`/invite/bind` body = `{phone, memberId, name}`；`/invite/redeem` body = `{inviteeMemberId, orderId, orderAmount}` —— **均不含 `tenantId` / `storeId`**，因此 DataScope 的 body 正则**匹配不到**，当前无论登录与否都不构成越权通道 |

### 1.3 其他 Filter / 拦截器

| 组件 | 文件 | 对 `/invite/**` 匿名化的影响 |
|---|---|---|
| `TraceFilter` | `config/TraceFilter.java` | `@Order(MIN_VALUE+10)`，最先生效。只做 traceId + MDC，finally 里 `TenantContextHolder.clear()`。**无阻塞** |
| `TracingFilter` | `config/TracingFilter.java` | `@Order(1)`，写 span。无阻塞 |
| `LogContextFilter` | `log/LogContextFilter.java` | ① 用 `CachedBodyRequestWrapper` 缓存 body（DataScope 读 body 依赖它）② 若请求带 `Bearer`，**粗解析 token 写 userId/username** 到 `TenantContext`（不带则不写）③ 不做鉴权判定 |
| `LogAspect` | `log/LogAspect.java` | **`InviteController` 类上有 `@SysOpLog(module = "裂变邀请")`** → 8 个 C 端端点**每次调用都会写 `sys_log`**。匿名时 `tenant_id = null`（该列可空，OK），`username = "anonymous"`。⚠️ `SENSITIVE_KEYS = [password, oldPassword, newPassword, token]` —— **不含 `phone`** → `/invite/track` 和 `/invite/bind` 的**手机号明文进 `sys_log.request_params`**（GET 走 `maskQuery`，POST 走 `maskJson`，两者都不脱敏 phone）。匿名化后这会变成**公网可触发的明文 PII 落库** |
| `PermissionAspect` | `security/PermissionAspect.java` | 切点是 `@annotation` 或 `@within` 的 `@RequirePermission`。`InviteController` 类上**没有** `@RequirePermission` → C 端方法**全部不进入权限校验** |
| `CorsConfig` | `config/CorsConfig.java` | `allowedOriginPatterns("*")` + `allowCredentials(true)` + `allowedHeaders("*")` → **任何来源的浏览器都能直调 `/invite/**`**（匿名化后 CORS 本身不构成额外屏障，但也意味着 CSRF 防护依赖「无 Cookie 鉴权」而非 token） |
| `MybatisPlusConfig` | `config/MybatisPlusConfig.java` | **未启用 `TenantLineInnerInterceptor`**（注释明确说明：业务表 tenant_id 字段不一致，改由 Service 层显式 `eq(tenantId)`）→ **租户隔离100% 依赖各 Service 手工加 `eq(tenantId)` 条件**，这直接决定了 §3 的风险面 |
| `TenantMetaObjectHandler` | `config/TenantMetaObjectHandler.java` | INSERT 时自动填 `tenantId = TenantContextHolder.getTenantIdOrNull()` → 匿名时为 **null** → `m09_invite_record.tenant_id` 是 `NOT NULL` → **INSERT 会失败**（`trackClick` 建 record 的路径直接500） |
| `RedisConfig` | `config/RedisConfig.java` | Redis 已接入 → 限流可用 Redis 实现（`ChannelDispatcher.rateLimitCheck` 已有现成范式可参考） |
| Nginx | `frontend/meiyetec-web/nginx.conf` | **无 `limit_req` / `limit_conn`** → 当前**没有任何网关层限流** |

---

## 2. 8 个端点逐个分析

### 2.1 汇总表

| 端点 | Controller位置 | 当前需要什么 | 业务上应否匿名 | 匿名后自证机制 | tenantId 来源（现状） | `@RequirePermission` | body 含 tenantId/storeId？ |
|---|---|---|---|---|---|---|---|
| `GET /invite/share-page?code=` | `InviteController.java` L158 | JWT + tenantId | **是**（分享落地页） | `code` 本身可自证 | `TenantContextHolder.get().getTenantId()`（JWT claim，经 `RbacContextLoader` 填充） | ❌ 无 | GET，无 body |
| `POST /invite/track` | L177 | JWT + tenantId | **是**（点击埋点） | `code` + `phone`（建 record 时） | 同上 | ❌ 无 | ❌ 无（`code/phone/channel`） |
| `POST /invite/bind` | L192 | JWT（**注意：不读 tenantId**） | **否**（见 F2） | 需 `phone` + 注册上下文 | Service 内 `TenantContextHolder.getTenantIdOrNull()`；**null 时直接 `return Result.success(0)`** | ❌ 无 | ❌ 无 |
| `POST /invite/redeem` | L203 | JWT（**Controller 不读 tenantId**） | **否**（POS 内部触发语义，见 F2） | 需 `inviteeMemberId` + 签名 | Service 内 `getTenantIdOrNull()`；null 时 `return Result.success(0)` | ❌ 无 | ❌ 无 |
| `GET /invite/my-code?campaignId=&memberId=` | L216 | JWT + tenantId | **否**（我的数据） | memberId 需绑定登录态 | JWT tenantId | ❌ 无 | GET |
| `GET /invite/my-codes?memberId=` | L228 | JWT + tenantId | **否** | 同上 | JWT tenantId | ❌ 无 | GET |
| `GET /invite/my-stats?memberId=` | L238 | JWT（**Controller 不读 tenantId**） | **否** | 同上 | Service 内 `TenantContextHolder.get().getTenantId()` | ❌ 无 | GET |
| `POST /invite/{codeId}/report-share` | L246 | JWT（**不读 tenantId**） | **半**（需 code 自证） | `codeId` → 反查 invite_code | **无 tenantId 过滤** | ❌ 无 | 无 body |

### 2.2 关键说明**(a) 4 个端点的 tenantId 实际来源不是 JWT claim，而是 Service 内读上下文**

`JwtInterceptor` → `RbacContextLoader.load(userId)` → `TenantContext.tenantId`（来自 `sys_user.tenant_id`）。匿名后 `TenantContextHolder.get()` 返回 `TenantContext.empty()`（`tenantId = null`）。

因此匿名化的 **tenantId 唯一可行来源**只有三种：

| 来源 | 可行性 | 评价 |
|---|---|---|
| **URL 参数 `?tenantId=`** | ✅ 可行 | 最小改动，但 **tenantId 完全由客户端控制** → 必须服务端交叉校验（用 code 反查出的 tenantId 与参数比对，不一致则拒） |
| **code 反查 `m09_invite_code.tenant_id`** | ❌ **不可行** | 唯一键是 `(campaign_id, invite_code)`，code 非全局唯一 → 反查可能命中多行（**F3**） |
| **默认 tenantId=1** | ⚠️ 技术可行、业务不可接受 | 单租户部署可接受；多租户 SaaS 下**直接串租户** |

**(b) `Result.error(401, "未识别租户")` 是 envelope 401，不是 HTTP 401**

`InviteController.tid()` 返回 null 时，5 个端点返回 **HTTP 200 + body `code=401`**。前端 `request.ts` 响应拦截器只看 `res.code !== 200` → 会 reject，但**不会走 401 → refresh token → 跳登录**的分支**。这意味着：把 `/invite/**` 加入白名单后，未改造的前端会收到「HTTP 200 + code 401」这种混合状态，极易误判为「服务端报错」。

**(c) `my-stats` 存在**已存在的**跨租户统计泄露（与匿名化无关，但会被匿名化放大）**

`InviteService.myStats(memberId)` 中三个计数查询**缺 `tenantId` 条件**：

```java
// totalCodes 有 tenantId ✓
.eq(InviteRecord::getMemberId, memberId).eq(InviteRecord::getTenantId, tid)
```
但 `successCount` / `pendingReward` / `rewardLogs` 三个 `selectCount` **只按 memberId + status 过滤，无 tenantId**。

→ 现状下，租户 A 的任意登录用户传 `memberId`（B 租户的会员 ID）即可读到 B 租户的邀请成功数 / 待发奖数 / 奖励数。**这是一个既存的 IDOR**，一旦 `my-stats` 匿名化，变成「任意匿名用户遍历 memberId 读全平台统计」。

> 📌 **已拆出为 I164（P1）**，详见 `audit/issues/I164-invite-my-stats-tenant-idor.md`。**本 Issue 不再承担该项修复**。

**(d) `report-share` 存在既存跨租户 IDOR**

`InviteService.reportShare(codeId)`：`codeMapper.selectById(codeId)` → **无 tenantId 过滤** → 直接 `share_count + 1`。任意 codeId 可被任意租户刷。匿名化 = 完全开放。

> 📌 **已拆出为 I165（P1）**，详见 `audit/issues/I165-invite-report-share-tenant-idor.md`。**本 Issue 不再承担该项修复**。

**(e) `share-page` 隐式调用 `trackClick`，导致 scanCount 双计数**

`InviteController.sharePage` 内部先 `inviteService.trackClick(code, null, "LINK", null, null)`（`phone=null` → 只 `scan_count+1`），再返回 `getSharePage`。若前端同时调 `share-page` 和 `track` → **一次真实访问 scanCount +2**。这属于数据口径问题，匿名化 + 刷接口会进一步放大。

> 📌 **已拆出为 I166（P2）**，详见 `audit/issues/I166-invite-share-page-scan-double-count.md`。**本 Issue 不再承担该项修复**。

---

## 3. 安全风险评估

### 3.1 风险总表

| # | 风险 | 严重度 | **当前已有的防护** | 匿名后**必须新增**的防护 |
|---|---|---|---|---|
| **R1** | `track` 被刷（伪造 code 疯狂点击埋点） | 中 | ① `invite_code` 8 位 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`（32 字符集，熵 ≈ 40 bit，`Keys.randomCode`）② `trackClick` 内 **静默返回 null**（找不到 code 不报错，注释明写「防刷」）③ 带 phone 时才建 record，且受 `checkQuota` 约束 | ① **IP/设备级限流**（Redis，参考 `ChannelDispatcher.rateLimitCheck`）② `scan_count` 增量封顶（如单 code 单日 +N）③ **share-page 不再隐式 trackClick**，避免双计数 ④ 匿名下 tenantId 需交叉校验 |
| **R2** | `bind` 被刷（批量伪造手机号绑定） | **高** | ① `MAX_BIND_PER_PHONE = 1`（同活动同邀请人同手机号只能绑一次）② `checkQuota` 限邀请人单日/单次/活动总量 ③ 唯一键 `uk_campaign_inviter_phone` 兜底 ④ ⚠️ **但脱敏手机号参与唯一键 → 撞键误拒**（I149-Q9，Open） | ① **短信验证码**（当前**完全没有**验证码基础设施，见 §5.2）② 手机号频次限流 ③ 匿名下 `memberId` **不可由客户端传入**（当前 `bind` body 接受 `memberId`，攻击者可任意指定！） |
| **R3** | `redeem` 被刷（伪造 inviteeMemberId 触发发券） | **严重** | ① 查记录时 `eq(tenantId, tid)` ② 只处理 `status = 2`（已注册）③ 记录状态机推进后不再命中 ④ 发券走 `atomicGrant`（有上限，I148 另立） |① **绝不能匿名** —— 这是发券/发储值的资金接口 ② 必须保留 JWT + 权限码，或改为**内部服务间调用**（POS → 后端内部 API +签名） |
| **R4** | `my-code` 被刷（遍历 memberId 看所有人邀请码） | **高** | ① `getOrCreateCode` 校验 `member.tenantId == tid` ② campaign 校验 tenantId ③ 需知道 memberId | ① **必须保留 JWT** ② 若必须匿名：memberId 需由服务端从登录态/签名推导，**不可作为 query 参数** ③ `InviteCode` 返回体含 `memberPhone`（脱敏）、`inviteCode`、漏斗计数 —— 属**他人业务数据** |
| **R5** | `my-codes` 被刷 | **高** | `myCodes` 有 `eq(tenantId, tid)` ✅ | 同 R4 |
| **R6** | `my-stats` 被刷 | **高** | ① `totalCodes` 有 tenantId ② ⚠️ **其余 3 个计数无 tenantId**（既存 IDOR） | ① **先补 tenantId 过滤**（这是必修项，与是否匿名无关）② 必须保留 JWT |
| **R7** | `report-share` 被刷（刷分享计数） | 中 | **几乎为零** —— 无 tenantId、无配额、无频控、无幂等 | ① `codeId` → 反查 invite_code → **校验该 code 属于调用者**（或至少属于当前 tenant）② 幂等（同一 code + 同一 ip + 同一 ua + N 分钟内只计一次）③ 频控 |
| **R8** | **tenantId 泄露 / 跨租户串数据** | **严重** | ① Service 层逐处 `eq(tenantId, tid)`（但**不完整**，见 R6/R7）② `MybatisPlusConfig` **无全局 TenantLine 拦截器**（有意为之，注释说明）③ `DataScopeInterceptor` 依赖 `userId != null` 才生效 → **匿名完全无租户校验** | ① 匿名入口**必须**自行做 tenantId 解析 + 校验（不能依赖 `DataScopeInterceptor`）② `DataScopeInterceptor` 的 `userId == null → return true` 早退是匿名化的**结构性盲区** ③ `reportShare` / `myStats` 补 tenantId 过滤 |
| **R9** | **匿名下 INSERT 落 `tenant_id = NULL`** | **高** | 无 —— `TenantMetaObjectHandler` 会填 null，而 `m09_invite_record.tenant_id` 是 `NOT NULL` | 匿名写路径必须**显式 set tenantId**（`record.setTenantId(tid)` 已有这行，但 `tid` 来自上下文，匿名时为 null） |
| **R10** | **手机号明文落 `sys_log`** | 中 | `SENSITIVE_KEYS` 不含 `phone` | `LogAspect.SENSITIVE_KEYS` 增 `phone`；或 C 端端点排除出 `@SysOpLog` |
| **R11** | **无限流 / 无网关防护** | 中 | Nginx 无 `limit_req`；应用层无通用限流（只有 `ChannelDispatcher` 的渠道级限流） | Redis 限流 或 Nginx `limit_req`（按 IP +路径） |

### 3.2 资金/数据敏感度分级（决定匿名化优先级）

| 等级 | 端点 | 判定依据 |
|---|---|---|
| 🟢 可只读匿名 | `share-page` | 只返回活动公开信息 + 邀请人**脱敏昵称**（`getSharePage` 内部已做 `nick.charAt(0) + "**"`）；无 PII 写、无资金 |
| 🟡 可匿名但需强风控 | `track`、`report-share` | 只改计数（`scan_count` / `share_count`），不触资金；`track` 带 phone 时会写 `m09_invite_record` |
| 🔴 绝不能匿名 | `bind`、`redeem`、`my-code`、`my-codes`、`my-stats` | 前两个触及**邀请关系与资金发放**；后三个是**「我的」个人数据**（会员邀请码、漏斗、奖励），本质必须登录态 |

---

## 4. 前端 H5 现状

### 4.1 `consent-h5.vue` —— 它**并不是**真正的免登录页面 ⚠️

| 检查项 | 结论 | 证据 |
|---|---|---|
| 后端是否白名单 | **否** | `MhConsentH5Controller` 路由 `/mh/consent/sign-link/{token}`，**不在** `WebConfig` JWT exclude 列表 → **无 Bearer 直接 401** |
| 前端如何调用 | `import http from '@/utils/http'` → `utils/http.ts` 内容是 `import request from '@/api/request'; const http = request` → **完全复用同一 axios 实例** | `frontend/meiyetec-web/src/utils/http.ts`（全文 8 行） |
| 未登录会怎样 | **`request.ts` 请求拦截器本地短路**：`localStorage.getItem('token')` 为空且 URL 不含 `/auth/login`/`/auth/refresh` → `return Promise.reject({__unauthorized: true, message: '未登录'})` —— **请求根本发不出去** | `frontend/meiyetec-web/src/api/request.ts` 请求拦截器 |
| 结论 | `consent-h5` 之所以「能用」，是因为**同域 localStorage 里残留了已登录 token**（H5 与后台同源 `/api` 前缀）。**真正的未登录访客访问它会失败** | 上述三项叠加 |

> **这条对 I146 的意义**：`consent-h5` **不能**作为「本项目已有免登录先例」来援引。后端白名单与前端拦截器两层都要改，才是真正的匿名。

### 4.2 邀请相关 H5 页面

**不存在**。全仓 `frontend/**` 无任何 invite H5 / 小程序页面：
- `views/m09-invite/index.vue` —— B 端运营后台（活动/码/记录/看板）
- `views/lifecycle/index.vue` —— B 端「裂变邀请」测试标签页，调 `/lifecycle/invite/*`（`MemberInviteController`，**另一套体系**，且那套端点**全部带 `@RequirePermission`**）

### 4.3 `api/invite.ts` 的 token 假设

- 8 个 C 端方法全部走 `import request from './request'` → **强制要求 localStorage 有 token**，且未登录时**本地拒绝**（不发请求）
- → **即使后端加白名单，前端匿名调用仍会被 `request.ts` 拦截器挡在浏览器内**
- → 前端改造是**必须的独立工作量**：需要匿名 axios 实例，或在 `request.ts` 增加 `skipAuth` 标记（当前代码只有 `/auth/login`/`/auth/refresh` 两个硬编码豁免）

---

## 5. 方案草案（不落盘）

### 方案 A —— 最小面：只开放只读/计数类（🟢🟡 端点）

**改动范围**

| 文件 | 改动 |
|---|---|
| `backend/.../config/WebConfig.java` | JWT `excludePathPatterns` 增 `/invite/share-page`、`/invite/track`、`/invite/*/report-share`（**逐条精确列，不用 `/invite/**`**，避免误放 `bind`/`redeem`/`my-*`）；DataScope 排除清单同步增同样 3 条（保持两层语义一致） |
| `backend/.../controller/InviteController.java` |3 个端点改为**匿名可用的 tenantId 解析**：新增 `resolveTenant(code)` —— 先按 `invite_code` + `campaign_id` 反查（**需新增全局唯一约束或码前缀**，见下）；tenantId 一律**服务端解析**，不接受客户端传入 |
| `backend/.../service/InviteService.java` | `trackClick` / `reportShare` 增加显式 tenantId 入参；`reportShare` 补 tenantId 过滤（修既存 IDOR）；`sharePage` 移除隐式 `trackClick`（消除双计数） |
| `backend/.../log/LogAspect.java` | `SENSITIVE_KEYS` 增 `phone`（或 C 端端点退出 `@SysOpLog`） |
| `db/sql/*.sql` | `m09_invite_code` 增加**全局唯一**邀请码（改生成规则为含 tenant 前缀 / 加 `UNIQUE(tenant_id, invite_code)` 不足以反查 → 需要 `UNIQUE(invite_code)` 或码内嵌 tenant）—— **这是前置 DDL** |
| `frontend/.../src/api/request.ts` | 支持匿名请求（新增 `skipAuth` 或独立匿名实例） |
| `frontend/.../src/api/invite.ts` | 3 个 C 端方法切到匿名实例 |
| 新增前端 H5 页面 | 分享落地页（**目前完全缺失**） |

**安全防护**
- Redis 限流（IP + 路径）：`share-page` 60/min、`track` 30/min、`report-share` 10/min
- `report-share` 幂等：同 `codeId` + 同 ip + 10 分钟窗口只计 1 次
- `share-page` 隐式 track 移除 → 埋点只由显式 `track` 产生
- tenantId 服务端反查 + 交叉校验

**兼容性**：已登录用户行为不变（tenantId 从 JWT 取；优先级：JWT > code 反查）

**风险等级**：🟢 **低**。不触资金、不读个人数据。**但前置 DDL（邀请码全局唯一）不可回避。**

---

### 方案 B —— 全部匿名 + 短信验证码 + 限流

**改动范围**：方案 A 全部 + 以下新增

| 文件 | 改动 |
|---|---|
| 新增 `SmsCodeService` / `SmsCodeController` | 验证码下发 + 校验（**当前无任何验证码基础设施** —— `SmsSender` 只做单向通知，无「发码-校验」存储） |
| 新增表 `sms_verify_code` |存储 `phone / code / purpose / expire_at / used` |
| `InviteController` | `bind` 增加 `code` 必填 + 验证码校验；`redeem` 增加**服务端签名**（HMAC，密钥来自渠道配置） |
| `InviteService` | `bind` 忽略客户端 `memberId`（改由验证码绑定手机号反查）；`redeem` 验签 + 幂等键 |
| `nginx.conf` | `location /api/invite/`段 `limit_req` |
| Redis | 限流实现 |

**安全防护**：验证码 + 全端限流 + `redeem` 签名 + `bind` 去客户端 `memberId`

**兼容性**：⚠️ **已登录用户行为会变** —— `bind` 现有调用方（无实际调用方，见 F2）签名需重协商；`redeem` 现有契约（`inviteeMemberId/orderId/orderAmount`）需加签名字段

**风险等级**：🟡 **中高**。工作量最大（验证码体系从零建），且**仍然解决不了 `my-code`/`my-codes`/`my-stats` 的「他人数据」问题** —— 这三个端点本质上是「我的」，匿名 + 验证码也证明不了「你就是这个 memberId」。**不建议。**

---

### 方案 C —— C 端专用 token（微信 openid + 短期 JWT），先静默登录再调用

**改动范围**

| 文件 | 改动 |
|---|---|
| 新增 `CEndAuthController` | `POST /auth/c-end/login`（小程序 `jscode2session` → openid → 签发短期 JWT，claims 含 `openid` / `tenantId` / `memberId` / `scope=c_end`） |
| `JwtUtil` | 新增 `cEnd` claim 签发/校验（与 staff token 隔离） |
| `JwtInterceptor` | **不放行** `/invite/**`；改为识别 `c_end` token → 写 `TenantContext`（`tenantId` + `memberId`）但不写 `sys_user` 权限 |
| `P3EcosystemService` / `ThirdPartyIntegrationService` | 复用现有 openid 能力（`stableOpenId` / `mockOpenId` 已存在）—— ⚠️ 但 **`mockOpenId` 是 mock**，真实 `jscode2session` 在 `ThirdPartyIntegrationService`（含 `WxAppConfig` 校验），需确认生产可用性 |
| `InviteController` | C 端端点从 `TenantContext` 读 `memberId`，**忽略** query 里的 `memberId` |
| `InviteService` | `bind`/`redeem` 改由登录态推导 |

**安全防护**：token 自带身份（openid/memberId/tenantId）→ memberId 无法伪造、tenantId 天然确定；`scope` 隔离，`c_end` token 不能调 B 端

**兼容性**：✅ 已登录用户行为不变（C 端 token 走独立通道）；**但引入第二套身份体系**

**风险等级**：🟢 **中低（安全上最优）**，但 🟡 **工作量最大**：
- 需要真实微信 `jscode2session` 凭据（`appSecret`）与生产小程序配置 —— 当前 `ThirdPartyIntegrationService` 有实现但未验证生产可用
- H5（浏览器，非小程序）**没有 openid** → 需退化为手机号验证码或游客 cookie → 方案退化成 B 的变体
- 需要新增 C 端会员与 `crm_member` 的绑定关系（`wx_member_bind` 表已有 `WxMemberBind` 实体，可复用）

---

### 5.3 方案对比

| 维度 | 方案 A（只读/计数匿名） | 方案 B（全匿名 + 验证码） | 方案 C（C 端 token） |
|---|---|---|---|
| 改动文件数 | **~8**（含 1 份 DDL） | ~15（含2 张新表 + 新服务） | ~12（含新鉴权体系） |
| 新增基础设施 | 无 | **短信验证码体系（从零）** | 微信 `jscode2session` + 双 token 体系 |
| 解决的端点 | 3 / 8 | 8 / 8 | 8 / 8 |
| 资金端点（`redeem`） | ❌ 保持 JWT | ⚠️ 靠签名 | ✅ 靠登录态 |
| 个人数据端点（`my-*`） | ❌ 保持 JWT | ⚠️ **仍不安全** | ✅ 天然安全 |
| tenantId 隔离 | 服务端反查（需 DDL） | 服务端反查（需 DDL） | ✅ token 自带 |
| 已登录用户行为变化 | ❌ 无 | ⚠️ 有（bind/redeem 契约变） | ❌ 无 |
| 前端工作量 | 中（需匿名 axios + 新建 H5 页） | 中高 | 高（需登录态管理） |
| 风险等级 | 🟢 低 | 🟡 中高 | 🟡 中（安全最优，工程量最大） |
| **推荐度** | ⭐⭐⭐ **推荐先落地** | ⭐ 不推荐 | ⭐⭐ 长期目标 |

### 5.4 我的建议

**先落方案 A**，并把 A 当作**地基**：
1. A 的 3 个端点覆盖了真实 C 端落地页的**全部必要调用**（落地页要活动信息 + 埋点 + 分享计数）
2. A 的前置 DDL（**邀请码全局唯一**）是 B/C 也必须做的 —— 不会白做
3. A 不需要任何新基础设施，能在最短路径上把「C 端完全不可用」变成「C 端可用」
4. `bind`/`redeem`/`my-*` 保持 JWT，等方案 C 的 C 端 token 落地后再统一放开

---

## 6. 待确认问题清单（需业务 / 产品拍板）

| # | 问题 | 为什么必须先问 | 建议默认值 |
|---|---|---|---|
| **Q1** | **C 端落地页的真实形态是什么？** 小程序 / H5 / 两者都有？ | 决定方案 A 还是 C。H5 无 openid → 方案 C需退化 | 待定 |
| **Q2** | **`/invite/bind` 的正确调用方是谁？** 注释说 MemberService 会调，但实际没有。是「计划中未实现」还是「已废弃」？ | 决定它是否值得匿名化（F2：当前是影子端点） | 建议：**先补内部调用方**（注册成功后由 MemberService 内部调），**保持 JWT**，而不是开公网口子 |
| **Q3** | **`/invite/redeem` 的正确调用方是谁？** 注释说 PosController，但实际没有。POS 是服务端内部调用（可保留 JWT /改内部签名），还是外部 POS 设备直连？ | 若是外部设备直连，需要的是**设备签名**而不是会员 JWT | 建议：保留 JWT 或改设备 HMAC 签名，**不匿名** |
| **Q4** | **匿名请求的 tenantId 用什么确定？** ① URL 带 tenantId ② 邀请码全局唯一后反查 ③ 单租户默认 1 | F3：邀请码当前非全局唯一，反查不可行 | 建议②（改码规则，如 `T{tenantId}-{code}`），同时**服务端交叉校验 URL tenantId** |
| **Q5** | **`m09_invite_code.invite_code` 改全局唯一 —— 是否接受存量数据影响？** 现有码需重生成或加租户前缀 | DDL + 存量数据迁移 | ✅ **已定案**：单独立 issue **I167**（P2），2026-11-13 登记，**不并入 I146**。I167 内给出 3 个候选方案（推荐「保持码不变 + 加全局唯一约束」，零存量迁移）与只读排查 SQL；方案选型待业务拍板 |
| **Q6** | **`my-stats` 缺 tenantId 过滤（既存 IDOR）** | 这是**独立于 I146 的既存漏洞**，即使不匿名也存在 | ✅ **已定案**：单独立 issue **I164**（P1），2026-11-13 登记，**不并入 I146** |
| **Q7** | **`report-share` 缺 tenantId 过滤（既存 IDOR）—— 同上** | 同 Q6 | ✅ **已定案**：单独立 issue **I165**（P1），2026-11-13 登记，**不并入 I146** |
| **Q8** | **`share-page` 隐式 trackClick造成 scanCount 双计数 —— 修不修？** | 影响所有埋点数据口径 | ✅ **已定案**：单独立 issue **I166**（P2），2026-11-13 登记，**不并入 I146** |
| **Q9** | **限流选型**：Nginx `limit_req` / 应用层 Redis / 两者？阈值多少？ | 影响需不需要动 nginx.conf（部署侧） | 建议应用层 Redis（可动态调、可按 code 维度），Nginx 作粗粒度兜底 |
| **Q10** | **`sys_log` 明文手机号 —— 是否在 I146 内一并修 `LogAspect.SENSITIVE_KEYS`？** | 改 `LogAspect` 影响**全系统**日志 | 建议：只在 `InviteController` 的 C 端方法上退出 `@SysOpLog`，**不动全局 `LogAspect`**（避免扩大影响面） |
| **Q11** | **匿名后 `tenant_id` 为 NULL 的 INSERT —— 匿名路径是否需要写 `sys_log` / 埋点表？** | `m09_invite_record.tenant_id NOT NULL`，匿名写路径必须显式 set | 建议：匿名路径显式 set tenantId 后再 INSERT |
| **Q12** | **`/invite/**` 白名单用精确路径还是通配？** | `/invite/**` 会连带放开 `bind`/`redeem`/`my-*` | 建议**逐条精确列举**（`/invite/share-page`、`/invite/track`、`/invite/*/report-share`），并在 WebConfig 注释里写明「其余 /invite/* 需登录」 |
| **Q13** | **前端匿名请求的载体**：独立 axios 实例 / `request.ts` 加 `skipAuth` 标记？ | `request.ts` 是全局文件，改动影响所有页面 | 建议**新增独立实例** `api/anonymous.ts`（不动 `request.ts` 的全局拦截器逻辑） |
| **Q14** | **前端 H5 落地页由本仓交付还是小程序仓交付？** | 影响 I146 是否包含前端页面开发 | 待定 |

---

## 7. 本次调查的边界声明

-全部结论基于**静态代码分析 + SQL DDL 阅读**，**无运行时验证** → 运行期行为一律 **UNVERIFIED**
- **未**连接数据库、**未**执行 SQL、**未**启动应用、**未**打开浏览器、**未**修改任何文件
- `audit/baseline/env.md` 记录的 HEAD 为 `03173da`，工作树为 dirty；本报告结论对应该工作树的**当前代码状态**，非已上线版本
- 未核查：`m09_invite_code` / `m09_invite_record` 的**存量数据**（例如是否已存在跨租户同码、是否存在 `tenant_id` 为 NULL 的脏行）、Redis 实际可用性、生产微信 `jscode2session` 可用性

---

**下一步建议**：Q1–Q5 决定方案选型与 DDL 范围，Q9–Q14 可在方案确定后一并处理。Q5 / Q6 / Q7 / Q8 已于 2026-11-13 定案拆出（**I167 / I164 / I165 / I166**）。**剩余待拍板：Q1–Q5、Q9–Q14**。下一步建议：先回答 Q1（落地页形态）与 Q4（tenantId 判定方式），这两项决定方案选型与 DDL 范围；Q5 的落地依赖 I167 的方案拍板与存量排查结果。

> **本 Issue 当前状态：Parked（等待产品决策）** —— 剩余全部动作依赖 Q1 / Q4 / Q14 的产品拍板与前置 DDL（I167），在拍板前无法开工。
> **实施顺序建议**：① I167 完成（全局唯一约束 + 存量排查）→ ② I164 / I165 / I166 修复（既存缺陷，与匿名化解耦，可并行）→ ③ 本 Issue 匿名化改造开工。