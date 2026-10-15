# I037 — Webhook 路径未在 JWT excludePathPatterns（生产环境永远 401）

> **发现时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification
> **关联 Feature**：F077 / F078（YouzanWebhookController）
> **严重度**：🔴 **CRITICAL-P0**（生产 webhook 永远走不到 controller）
> **类别**：认证 / 安全配置（Production Blocker）
> **修复阶段**：**Post-Audit**（Phase 7）
> **修复状态**：✅ **FIXED**（2026-09-17 16:25 / commit `eb5ff6e` / Post-Audit 批次 2.2-A1）

---

## 1. 摘要

`backend/src/main/java/com/meiyetec/config/WebConfig.java:25-33` 中 `JwtInterceptor` 注册时 `addPathPatterns("/**")` 拦截所有路径，但 `excludePathPatterns` **没有** `/webhook/**`，导致所有 `/webhook/youzan` 类推送（无论有赞、BnPL、海外支付等）都被 JWT 拦截返回 **401 UNAUTHORIZED** —— 即使有赞推送带正确签名头、即使后续 controller 完整实现，永远走不到。

| 维度 | 值 |
|---|---|
| **触发条件** | 任何 `/webhook/**` 请求（含 `/webhook/youzan`、`/domestic/webhook/**`、`/bnpl/webhook/**`、`/overseas-payment/webhook/**` 等）|
| **失败现象** | HTTP 401，`code=UNAUTHORIZED` |
| **根本原因** | `WebConfig.excludePathPatterns` 缺 `/webhook/**` 路径白名单 |
| **影响范围** | 所有 webhook 类推送（生产环境有赞 push_url 注册后即暴露）|
| **阻断路径** | 整条 webhook 链路（F077 接收端 / F078 幂等去重 / F072 push_url 注册）|
| **批次元数据** | Batch 1.3（EXT-22）Phase 5B 实证发现 |

---

## 2. 实证证据（Phase 5B）

### 2.1 代码位置

`backend/src/main/java/com/meiyetec/config/WebConfig.java:25-33`：

```java
registry.addInterceptor(jwtInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns(
                "/auth/login",
                "/auth/logout",
                "/api/auth/login",
                "/api/auth/logout",
                "/api/static/**",
                "/static/**",
                "/error"
        );
// 缺: /webhook/**, /domestic/webhook/**, /bnpl/webhook/**, /overseas-payment/webhook/**
```

→ `excludePathPatterns` 仅放行了登录/登出/静态资源/错误页 4 类路径，**未放行任何 webhook 路径**。

### 2.2 Phase 5B 运行时实证

| 测试 | 命令 | 结果 | 阻断原因 |
|---|---|---|---|
| **测试 1** | `curl -X POST "http://localhost:8080/webhook/youzan?kdt_id=test" -H "X-Youzan-Sign: <sig>" -d '<payload>'` | **401 UNAUTHORIZED** | JwtInterceptor 拦截，无 token |
| **测试 2** | `curl -X POST "http://localhost:8080/webhook/youzan?kdt_id=test" -H "Authorization: Bearer <admin-token>" -H "X-Youzan-Sign: <sig>" -d '<payload>'` | **500**（I029 PARTIAL-MISSING 暴露）| JWT 通过 → controller 触发，但 `ecommerce_platform` 表缺 `app_id` 列 → `Unknown column 'app_id'` 异常 |
| **测试 3** | 同 payload 重复推送 | **500**（同原因）| F078 幂等去重永远走不到 |

### 2.3 双层阻断结构

```
请求进入
   ↓
[1] JwtInterceptor（I037 阻断）→ 无 token → 401
                              ↓ token 通过
[2] YouzanWebhookController 接收（F077 实现完整）
                              ↓
[3] 反查 platform by kdt_id → ecommerce_platform 表
                              ↓
[4] I029 PARTIAL-MISSING：表存在但缺 app_id/kdt_id/sign_secret 3 列
                              ↓
[5] SELECT * 触发 Unknown column → 500
```

→ **I037 + I029 形成双层阻断**：要看到 F077 / F078 完整链路，必须**先修 I037**（否则永远 401）。

### 2.4 5B 报告原文

> **5B 报告 §三.1（I037 NEW — Webhook 路径未在 JWT excludePathPatterns）**：
>
> **影响**：所有 `/webhook/**` 路径被 JwtInterceptor 拦截 → **生产有赞 push_url 推送会全部 401**
>
> 同源问题：国内支付 `/domestic/webhook/**`、BNPL `/bnpl/webhook/**`、海外 `/overseas-payment/webhook/**` 都有相同风险
>
> 严重度：**P0**（webhook 永远走不到 controller）

---

## 3. 修复方式（不改业务代码前提下）

### 3.1 推荐方案（最小改动）

修改 `backend/src/main/java/com/meiyetec/config/WebConfig.java:27`：

```java
.excludePathPatterns(
    "/auth/login", "/auth/logout",
    "/api/auth/login", "/api/auth/logout",
    "/api/static/**", "/static/**",
    "/error",
    "/webhook/**",                  // ← 新增
    "/domestic/webhook/**",         // ← 新增（同源风险 — 推测路径，需 Phase 6 验证）
    "/bnpl/webhook/**",             // ← 新增（同源风险）
    "/overseas-payment/webhook/**"  // ← 新增（同源风险）
)
```

→ 改动量：**4-8 行新增**（视是否同步预防 BNPL/海外支付等路径）

### 3.2 替代方案

| 方案 | 改动 | 评估 |
|---|---|---|
| **A. excludePathPatterns 显式添加** | 见 §3.1 | ✅ 推荐：最小改动，语义清晰 |
| **B. 注册多个 JwtInterceptor Bean** | 给 webhook 单独建不带 JWT 的拦截器链 | 改动大，重构链路 |
| **C. 全局关闭 webhook 路径鉴权** | 用 `@PermitAll` 注解到 `YouzanWebhookController` + 配置 JwtInterceptor 仍生效 | 复杂，不推荐 |
| **D. 用 HandlerInterceptor 优先级** | 把 webhook 专用拦截器注册到更高优先级 | 复杂，需要新增拦截器类 |

→ **方案 A 为 Post-Audit 首选**。

### 3.3 同源风险清单（需 Phase 6 / Phase 7 实证）

| 路径模式 | 推测来源 | 风险 |
|---|---|---|
| `/webhook/**` | 有赞 EXT-22 | **已实证 PASS（I037）** |
| `/domestic/webhook/**` | 国内支付（推测 EXT-*）| 待验证 |
| `/bnpl/webhook/**` | BNPL（先买后付，推测 EXT-*）| 待验证 |
| `/overseas-payment/webhook/**` | 海外支付（推测 EXT-*）| 待验证 |

→ 建议修复时**一并加上 4 个路径**，避免再次出现同类问题。

---

## 4. 严重度论证

### 4.1 为什么是 CRITICAL-P0

| 维度 | 评估 |
|---|---|
| **生产阻塞** | ✅ webhook 是有赞后台 push_url 注册后即触发的实时推送。注册后 100% 触发，所有推送 401 |
| **数据丢失** | ✅ 订单同步全部丢失（webhook → upsert `ecommerce_order` 链路永远走不到）|
| **用户感知** | ❌ 无人主动监控 webhook 失败率（I008 经验：监控告警同样缺失）|
| **修复成本** | ✅ 极低（4-8 行配置改动）|
| **同源风险** | ⚠️ 推测 3 条其他 webhook 路径有相同问题 |
| **架构异味** | ⚠️ 路径硬编码，应走白名单配置而非散落 |

### 4.2 与 I008 / I011 Fake Result 系列对比

| 维度 | I008 Fake Success | I011 Fake Failure | **I037 永远 401** |
|---|---|---|---|
| **API 返回** | `code=200, success=true`（静默）| `code=500` | `code=401` |
| **实际状态** | DB 失败但 API 报成功 | DB 成功但 API 报失败 | **业务永远走不到** |
| **用户感知** | "成功"（最坏）| "失败"（次坏）| **"401 Unauthorized"**（明显但被忽视）|
| **触发条件** | catch 吞异常 | select-after-update | **认证拦截** |
| **生产阻塞** | 中（CRITICAL-P1）| 中（CRITICAL-P1）| **高（CRITICAL-P0）** |

→ I037 比 I008 / I011 严重度更高：**永远走不到 controller**，连"假失败"或"假成功"都触发不了。

### 4.3 与 EXT-22 SoT 的关系

`ext-22-google-maps-and-ecommerce.mdc` §0.5 + §6 假设 webhook 接收端实现后能正常接收推送；**未提及 JWT 鉴权拦截**——属于**审计未覆盖**的安全配置盲点。

---

## 5. 关联 Issue / Feature

| 关联项 | 关系 |
|---|---|
| **I026 / I027 / I030**（SoT 反向）| I037 与 I027（webhook 接收端反缺失）形成补充：I027 证实 controller 已实现，但 I037 揭示 controller 永远走不到 |
| **I029 PARTIAL-MISSING** | I037 修好后，I029 仍会阻断（Unknown column 'app_id'）—— **双层阻断需同时修复** |
| **I034**（rollbackFor）| I037 修好后，F077 才能实测 I034 事务边界 |
| **I035**（时间窗缺失）| I037 修好后，F078 才能实测 msg_id 幂等 + 时间窗行为 |
| **I033**（buildWebhookUrl 孤儿）| I037 修好后，`buildWebhookUrl` 才有意义（注册到有赞后台才能走通）|
| **F077 / F078** | I037 阻断主路径 |
| **F072**（buildWebhookUrl）| I037 修好后，F072 才有意义 |
| **F075 / F076** | I029 PARTIAL-MISSING 阻断 — I037 修好后 I029 仍存在 |

---

## 6. 修复优先级矩阵

| 优先级 | 修复 | 影响 |
|---|---|---|
| **P0-1（CRITICAL）** | `WebConfig.excludePathPatterns` 加 `/webhook/**` + 3 个推测路径 | 解锁 F077 / F078 / F072 |
| **P0-2** | I029 PARTIAL-MISSING（跑 `migrate_ext_22_google_maps_place_details.sql`）| 解锁 F075 / F076 / F077 |
| **P0-3** | I029 MISSING（跑 `migrate_ext_17_google_maps.sql`）| 解锁 F056 / F057 / F060 |
| **P0-4** | webhook_msg_log 表（跑 `migrate_ext_24_webhook_msg_log.sql`）| 解锁 F078（幂等去重）|
| **P1** | I028 EXT-22 子项（前端加 5 字段 UI）| 解锁 F079 |
| **P1** | C5 重复实现（抽 `EcommerceOrderUpserter`）| 解耦 F076 / F077 |
| **P2** | I035 时间窗（webhook_msg_log 加 `created_at` + 24h 阈值）| 增强幂等安全性 |
| **P2** | I033 BACKLOG-001（删 `buildWebhookUrl` 或补注册流程）| 清理孤儿 |
| **P2** | I034 rollbackFor（`@Transactional(rollbackFor = Exception.class)`）| 事务边界完整 |

→ I037 是**解锁整条 webhook 链路的 P0 第一步**。

---

## 7. 决策（用户 2026-09-14 15:20 回复）

| 决策项 | 用户回复 | 落档 |
|---|---|---|
| **是否纳入 Issue Register** | ✅ 接受 | issue-register.md §二十一 登记 I037 |
| **严重度** | 🔴 **CRITICAL-P0** | §一 总览表 + §二十一 决策段 |
| **修复阶段** | **Post-Audit**（Phase 7）| Fix Phase 列 |
| **描述** | webhook 路径未 exclude（生产环境永远 401）| §本文件 |

---

## 8. Phase 5B 决策变更日志追加

| 时间 | 决策 | 影响 Issue / Feature |
|---|---|---|
| **2026-09-14 15:20** | **I037 NEW 登记**：CRITICAL-P0，Fix Phase = Post-Audit（Phase 7）| **F077 / F078 / F072** 整条 webhook 链路解锁前提 |

---

## 9. 关联文档

| 文件 | 路径 |
|---|---|
| 完整 Phase 5B 报告（含 I037 实证） | `audit/runtime/report-5B-batch-1.3.md` §三.1 |
| F077 evidence | `audit/evidence/F077-webhook-receiver/result.md` |
| F078 evidence | `audit/evidence/F078-webhook-idempotency/result.md` |
| F072 evidence | `audit/evidence/F072-build-webhook-url-orphan/result.md` |
| Batch 1.3 Feature Inventory | `audit/feature-inventory.md` §Batch 1.3 |
| Issue Register 入口 | `audit/issues/issue-register.md` §二十一 |

---

## 10. Post-Audit 修复实录（批次 2.2-A1）

### 10.1 修复动作摘要

| 维度 | 内容 |
|---|---|
| **commit** | `eb5ff6e` fix(auth): I037 exclude webhook paths from JWT interceptor |
| **修改文件** | `backend/src/main/java/com/meiyetec/config/WebConfig.java`（仅 1 文件）|
| **diff 规模** | +10 / -1（10 行新增 + 1 行逗号）|
| **mvn compile** | ✅ BUILD SUCCESS, exit code 0 |
| **curl 验证** | ⚠️ 后端未本地运行，无法实时验证（运行证据待补）|
| **不受保护页面影响** | ✅ 0 改动（仅修改全局拦截器白名单）|

### 10.2 实际放行路径（7 条）

| # | 路径 | 来源 Controller/Service | 影响 EXT |
|---|---|---|---|
| 1 | `/webhook/**` | YouzanWebhookController / BnplController / IntlBookingService / OpenPlatformEnhanceService / DomesticPayController / DianpingSyncService | EXT-22/17/19/20/23 |
| 2 | `/overseas-payment/webhook/**` | PaymentWebhookController | EXT-*（海外支付）|
| 3 | `/ecommerce/webhook/**` | P3EcosystemSuiteService | EXT-*（电商套件）|
| 4 | `/pos/webhook/**` | P2ComplianceSuiteService | EXT-*（POS 合规）|
| 5 | `/payment/webhook/**` | P1SuiteService | EXT-*（支付套件）|
| 6 | `/booking/webhook/**` | P1SuiteService | EXT-*（预订套件）|
| 7 | `/platform/webhook/**` | P3EcosystemService | EXT-*（平台接口）|

→ 比 I037 §3.1 文档里的 4 条多覆盖 **3 条**（`/ecommerce/webhook/**` / `/pos/webhook/**` / `/payment/webhook/**` + `/booking/webhook/**`）。

### 10.3 DataScopeInterceptor 兼容确认

`DataScopeInterceptor.preHandle` 已带 `ctx.getUserId() == null` 自动放行（见 `security/DataScopeInterceptor.java:59`）：

```java
TenantContext ctx = TenantContextHolder.get();
if (ctx.getUserId() == null) return true;   // ← webhook 自动通过（无 JWT → 无 userId）
```

→ **不需要第二处修改**。JWT 修复是充分的。

### 10.4 仍待解决（follow-up）

| 阻塞项 | Issue | 说明 |
|---|---|---|
| `ecommerce_platform` 缺 app_id/kdt_id/sign_secret | **I029 PARTIAL-MISSING** | I037 修好后，webhook 进入 controller 仍会因 `Unknown column` 报 500 |
| `webhook_msg_log` 表未建 | **I029 MISSING** | F078 幂等去重无法写表 |
| `google_maps_*` 2 表未建 | **I029 MISSING** | F056/F057/F060 阻断 |
| Backend 未跑实际 curl 验证 | 运行证据 | 用户启动后端后可补 curl 测试 |

### 10.5 一修一验清单

- [x] mvn compile 通过（exit code 0）
- [x] git commit 已提交（`eb5ff6e`）
- [x] I037 状态更新：OPEN → FIXED
- [x] Phase 2.2-A1 报告已落档（`audit/post-audit/phase-2.2-A1-report.md`）
- [ ] curl 运行时验证（待 backend 启动后补充）
- [ ] Phase 5B F077/F078 重跑（待 I029 解决后）

---

**I037 修复完成（Post-Audit 批次 2.2-A1）。**
