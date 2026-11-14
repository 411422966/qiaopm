# I169 — 应用层通用限流能力缺失（I165 D2 遗留项）

> **状态**：Open（2026-11-15 更新：**本批暂缓通用限流基建** —— 从零建设、429 契约与 Nginx 侧限制待业务 / 运维确认；期间由 I177 / I179 分别止血两处高风险点）
> **严重度**：P2（防滥用能力缺口；不造成数据越权，但让任何可被高频调用的写接口缺少兜底）
> **发现日期**：2026-11-14（I165 修复批次 —「仓内无通用限流工具」）
> **登记人**：Cursor
> **来源**：I165 §3.5 取舍 ②（限流维度/阈值移出本批）+ `audit/evidence/F162-invite-report-share-hardening/README.md` §1 D2
> **绑定基线**：修复前 HEAD `1bc831f`；**§2 暴露面表补齐于 2026-11-15，基线 HEAD `4ccca57`**
> **验证等级**：静态代码检索（`RedisTemplate` / `RateLimiter` / 拦截器注解 全仓检索 + 写端点全量清点）→ 运行期 **UNVERIFIED**（**未实现、未运行**）
> **关联**：I165（`POST /invite/{codeId}/report-share`）、I168、I146、**I177**（预约确认幂等，✅ 已修）、**I179**（`scan_count` 计数口径，✅ 已修）、**I178**（渠道配额覆盖缺口，Open）

---

## 0. 一句话描述

I165 修复时，用户明确口径「**没有现成工具就只做幂等，限流单独立 Issue**」。
本 Issue 登记「**应用层通用限流**」这一平台级能力缺口 —— 当前仓库**没有任何通用限流工具**，
所有可被高频调用的写接口都只能依赖各自的业务级防刷逻辑（幂等键、配额、状态机），
缺少「同 IP / 同用户 N 次/分钟」这一层**兜底**。

---

## 1. 现状核实（2026-11-14 静态检索）

| # | 检索项 | 结果 |
|---|---|---|
| 1 | `RedisTemplate` 全仓使用点 | **仅 1 处业务使用**：`NotifyReplayProtector`（通知重放保护，语义是幂等而非限流） |
| 2 | `RateLimiter` / `Bucket4j` / `Resilience4j` / `Guava RateLimiter` | **未引入**（`backend/pom.xml` 无相关依赖） |
| 3 | 自定义限流注解 + AOP 拦截器（`@RateLimit` 之类） | **不存在** |
| 4 | Servlet `Filter` / `HandlerInterceptor` 中的 QPS 校验 | **不存在**（现有 `JwtInterceptor` 只做鉴权） |
| 5 | 网关 / Nginx 层限流配置 | **未发现**（`docker-compose.yml` 无 nginx 限流配置；前端 `meiyetec-web` 直连 API） |
| 6 | Spring Cloud Gateway / Sentinel | **未引入** |

> 结论：仓库**从零开始没有限流能力**。新建通用限流不是「接线」而是「从零建设」，
> 涉及依赖引入、注解 + 切面、配置项、Redis 计数器、响应码约定、监控告警 —— 属于独立工程项。

---

## 2. 缺口带来的具体风险（当前仓内的暴露面）

| # | 接口 | 无限流时的后果 | 现状缓解 |
|---|---|---|---|
| 1 | `POST /invite/{codeId}/report-share` | 同 IP 换 `codeId` 即可对**任意本租户**邀请码刷 `share_count`（运营指标失真） | ✅ I165 已加**幂等**（同 codeId+同 IP，600s 窗口）；**但换 codeId 可绕过** |
| 2 | `POST /invite/track` | 登录态用户可对本租户任意邀请码无限刷 `scan_count` | ✅ **I179 已修**（2026-11-15）：无 phone 的请求不再计数；⚠️ **仍无频率限流**（带 phone 的重复请求仍 +1） |
| 3 | `POST /auth/login` | 已有登录失败次数锁定（`AuthController.checkLoginFailBlock` + `failMap`），但**进程内 Map，重启即清零** | 部分缓解 |
| 4 | **完全匿名（免 JWT）的写入口** | 见下方 #4 清单 | 无 |
| 5 | **渠道发送类写接口（真实外部计费）** | 见下方 #5 清单 | 仅 1/4 路径有配额闸门（I178） |
| 6 | **EXT-19 预约确认发送（真实外部计费）** | 见下方 #6 清单 | ✅ **I177 已修**（2026-11-15，Redis 幂等闸门） |
| 7 | **写接口总量（1021 个 POST/PUT/DELETE）** | 见下方 #7 清单 | 各自业务级校验 |
| 8 | **C 端邀请端点（不要求任何权限点）** | 见下方 #8 清单 | 仅要求「已登录」 |

> ⚠️ 上表 #2（`POST /invite/track`）曾是**本 Issue 最值得优先做限流**的落点 ——
> 它是 I166 修复后**唯一**的扫码计数来源。
> **2026-11-15 更新**：该端点的**计数口径**已由 **I179** 修正（无 phone 不计数），
> 但「同 IP / 同用户 N 次/分钟」的**频率闸门**仍未落地 —— 仍归属本 Issue。

### 2.1 #4 — 完全匿名（免 JWT）的写入口清单

> 来源：`backend/src/main/java/com/meiyetec/config/WebConfig.java` — `JwtInterceptor` 的 `excludePathPatterns`（**L27-50**）。

| # | 路径 | 写操作 | 风险 |
|---|---|---|---|
| 4.1 | `POST /auth/login` | 登录 | 撞库 / 暴力破解（见 #3） |
| 4.2 | `POST /webhook/**` | 通用 webhook | 上游重放无节流（各 webhook 自身幂等情况不一） |
| 4.3 | `POST /overseas-payment/webhook/**` | 海外支付回调 | 支付回调重放 → 重复入账 |
| 4.4 | `POST /ecommerce/webhook/**` | 电商回调 | 有赞 webhook（I026/I027 已加 `msg_id` 幂等去重，但**无时间窗**，见 EXT-22 §5 的 I035 遗留） |
| 4.5 | `POST /pos/webhook/**` | POS 回调 | — |
| 4.6 | `POST /payment/webhook/**` | 支付回调 | — |
| 4.7 | `POST /booking/webhook/**` | 预约回调 | — |
| 4.8 | `POST /platform/webhook/**` | 平台回调 | — |
| 4.9 | `POST /intl-booking/webhook/**` | 海外预约回调（Google / Instagram） | Google 会因 5xx 重试；`IntlBookingService.googleWebhook` L~290 每次都 **INSERT 一条新预约** → 重放即**重复建单** |
| 4.10 | `POST /bnpl/webhook/**` | BNPL 回调 | — |
| 4.11 | `POST /domestic/webhook/**` | 国内支付回调 | — |
| 4.12 | `POST /actuator/**` | 监控端点 | 只读为主 |

> ⚠️ #4.9（Google / Instagram 预约 webhook）是**匿名可写 + 无幂等 + 每次新建行**的组合，
> 风险等级高于一般 webhook，建议纳入限流 + 幂等评估（本 Issue 未展开，**未做运行期核实**）。

### 2.2 #5 — 渠道发送类写接口（真实外部计费，且仅 1/4 路径有配额闸门）

> 来源：`ChannelDispatcher.rateLimitCheck` 的**唯一**调用点是
> `MarketingAutomationService.startTask`（**L258**）；其余 3 条真实发送路径**不查配额**。

| # | 路径 | 发送实现 | 配额闸门 |
|---|---|---|---|
| 5.1 | `POST /mkt/task/{id}/start`（触达任务批量发送） | `MarketingAutomationService.startTask` → `ChannelDispatcher.getSender` | ✅ **有**（`rateLimitCheck`） |
| 5.2 | `POST /intl-booking/{id}/send-confirmation` + `POST /intl-booking/{id}/status`（EXT-19 预约确认） | `BookingConfirmationService` → `SmsSender` / `EmailSender` / `MiniProgramSender` | ❌ **无**（计数口径 = tenant+channel，配置口径 = tenant+brand+channel → 多品牌串味） |
| 5.3 | 生日通知（`BirthdayNotificationService.scheduledScan` / REST 触发） | `BirthdayNotificationService` L194 → `sender.send` L221 | ❌ **无** |
| 5.4 | 设备离线告警（`KioskOfflineAlertService`） | `KioskOfflineAlertService` L147 → `sender.send` L191 | ❌ **无**（告警风暴最易打爆短信配额） |

> 详见 **I178**（本批仅登记，未改 `ChannelDispatcher`）。

### 2.3 #6 — EXT-19 预约确认发送（外部计费）

| # | 路径 | 触发方式 | 现状 |
|---|---|---|---|
| 6.1 | `POST /intl-booking/{id}/send-confirmation` | 手动重发（**前端当前无调用方**，`intl-booking.ts` 未封装 → I042 Shadow） | ✅ **I177 已修**：Redis 幂等闸门 `booking:confirm:{tenantId}:{bookingId}`，窗口 `sys_config intl.booking.send.idempotent.window`（默认 300s），命中返 `code=400` |
| 6.2 | `POST /intl-booking/{id}/status {"status":"CONFIRMED"}` | 状态迁移自动触发（EXT-19） | ✅ **I177 已修**：同一闸门，命中则 `confirmationSkipped=true` 跳过发送 |
| 6.3 | 「同一用户 1 分钟最多 N 次」 | 频率维度 | ❌ **无** → 本 Issue |

### 2.4 #7 — 写接口总量

| 项 | 数值 | 来源 |
|---|---|---|
| `@PostMapping` / `@PutMapping` / `@DeleteMapping` 标注总数 | **1021** | 全仓 `backend/src/main/java/com/meiyetec` 静态计数 |
| Controller 类总数 | **138** | 同上 |
| 其中带 `@RequirePermission` 的 Controller | **109**（79%） | 同上 |
| 无限流基建 | **0** | §1 检索结论 |

> ⚠️ 1021 个写端点**不可能逐个手写限流** → 这正是 §3 方案 A（通用注解 + AOP）的核心论据。

### 2.5 #8 — C 端邀请端点（只要求「已登录」，不要求任何权限点）

> 来源：`InviteController` 中 `/invite/*` 系列方法的 `@RequirePermission` 覆盖情况。

| # | 端点 | 权限点 | 备注 |
|---|---|---|---|
| 8.1 | `GET /invite/share-page` | **无** | I166 已改为只读 |
| 8.2 | `POST /invite/track` | **无** | I179 已修计数口径；仍无频率限流 |
| 8.3 | `POST /invite/bind` | **无** | — |
| 8.4 | `POST /invite/redeem` | **无** | — |
| 8.5 | `GET /invite/my-code` / `my-codes` / `my-stats` | **无** | I164 已修跨租户 IDOR |
| 8.6 | `POST /invite/{codeId}/report-share` | **无** | I165 已加幂等 |

→ 任意**已登录**账号（含任意租户）均可调用 → 限流维度若按「用户 ID」，对这些端点同样适用。


---

## 3. 候选方案（需拍板）

| # | 方案 | 说明 | 代价 | 风险 |
|---|---|---|---|---|
| **A（推荐）** | **Redis 固定窗口计数器 + 通用注解 + AOP** | 自建轻量限流：`@RateLimit(key=..., limit=..., window=...)`；计数器落 Redis（`INCR` + `EXPIRE`），与 I165 幂等键**同一套 Redis 基建** | 中（1 个注解 + 1 个切面 + 1 个配置表/配置类） | 固定窗口在边界可放大到 2 倍；Redis 故障需定策略（建议 **fail-open**，与 I165 一致） |
| B | 引入 `Bucket4j` / `Resilience4j RateLimiter` | 用成熟库 | 中（加依赖 + 学 API） | 与现有 `RedisTemplate` 基建不统一；分布式需配 Redis backend |
| C | 网关 / Nginx 层限流 | 在入口层统一限流 | 低（配置为主） | 本项目**无网关**，需引入 Nginx/Ingress；C 端直连场景需额外部署 |
| D | 端点级手写计数（每个接口自己写） | 不用通用工具 | 低（局部） | 重复代码、易漏、无法统一观测 → **不推荐** |

---

## 4. 建议的落地口径（若选 A）

| 项 | 建议 |
|---|---|
| 维度 | **IP**（匿名场景）+ **用户 ID**（登录场景），两者取其一或取交集 |
| 阈值 | 匿名写接口 **60 次/分钟/IP**（I165 §3.5 原口径）；登录态接口 **120 次/分钟/用户** |
| 窗口 | 固定窗口 60s（简单）；若用滑动窗口需接受 Redis 操作变复杂 |
| 键 | `ratelimit:{scope}:{dimension}:{value}`（与 I165 的 `invite:share:` 前缀同风格） |
| 超限响应 | `Result.error(BizCode.CONFLICT, "请求过于频繁")` + HTTP **429**；**必须统一**，否则前端无从区分 |
| Redis 故障 | **fail-open**（放行 + `log.warn`），与 I165 幂等降级策略一致 |
| 可观测 | 至少记录「超限次数 + 接口 + 维度」到日志；后续接 Prometheus 告警 |

> ⚠️ 上表为**建议**，未经业务拍板；实施前需确认：
> ① 阈值是否分端点；② 超限是否返回 429 还是沿用 200 + `code=429`（**前端契约影响**）；
> ③ 是否需要在 B 端看板暴露「被限流次数」。

---

## 5. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 同一 IP 连续调用受保护接口超过阈值 | 第 N+1 次被限流，返回统一错误码 |
| 2 | 限流窗口过期后再次调用 | 恢复放行 |
| 3 | 不同 IP / 不同用户互不影响 | 各自独立计数 |
| 4 | Redis 不可用 | **fail-open** 放行 + 告警日志（不阻断业务） |
| 5 | 多实例部署 | 限流在实例间**共享**（依赖 Redis 计数器） |
| 6 | 未加注解的接口 | **不受影响**（回归） |

---

## 6. 边界声明

- 本 Issue **仅登记**，**未实现任何限流代码**、**未修改任何业务代码**、**未引入任何依赖**。
- 结论来自**静态代码检索**（`RedisTemplate` / 限流类 / 注解 / 拦截器 / 网关配置 / `pom.xml` 依赖
  + 写端点全量清点 + `WebConfig` 免鉴权路径清单 + `InviteController` 权限点覆盖）
  → 运行期行为一律 **UNVERIFIED**。
- §2 表 #2 的「无 phone 时仍 `scan_count + 1`」原为**静态代码位置推断**
  （`InviteService.trackClick` L428 的计数在 L437 的 phone 判空之前）；
  **2026-11-15 已由 I179 修复并运行期实测**（证据 `audit/evidence/F168-invite-track-scan-count-gate/`）。
  → 本 Issue 余下的缺口是**频率维度**，不是计数口径。
- §2.1 #4.9（`/intl-booking/webhook/**` 每次重放新建预约行）**未做运行期核实**，
  仅由 `IntlBookingService.googleWebhook` / `instagramWebhook` 的代码结构推断。
- §2.4 的 1021 / 138 / 109 三个数字来自**注解计数**，不含非注解映射（`RequestMapping` 直接标注在方法上）
  → 为**下界**。
- 限流阈值 / 响应码 / 维度均**未拍板**。
- **2026-11-15 用户决策**：通用限流基建（§3 方案 A/B/C）**本批不做** —— 从零建设、需业务确认 429 契约、
  需运维确认 Nginx 部署侧限制；`auth/login` 的 `failMap` 迁 Redis 亦为**独立批次**。

---

**下一步**：① 业务拍板 §3 方案（A/B/C/D）；② 拍板 §4 的阈值与响应码契约（尤其 429 vs 200+code）；③ 与前端确认超限响应是否需要特殊处理；④ 实施 + 按 §5 采集运行证据 → 落 `audit/evidence/FXXX-.../`。

> **2026-11-15 决策记录**：通用限流基建**本批暂缓**（从零建设 + 429 契约待定 + Nginx 侧待运维确认）。
> 本批改为**定点止血两处**：
> - **I177**（P1）→ `POST /intl-booking/{id}/send-confirmation` 及 CONFIRMED 自动触发加 **Redis 幂等闸门**（✅ 已修，VERIFIED）
> - **I179**（P2）→ `POST /invite/track` 的 `scan_count` 计数移到 phone 判空之后（✅ 已修，VERIFIED）
>
> **明确不做**：① 通用限流基建（§3 A/B/C）；② 改 `ChannelDispatcher`（I178，待业务确认多品牌配额口径）；
> ③ 动 Nginx 配置；④ 动 `auth/login` 的 `failMap`（迁 Redis 为独立批次）。
