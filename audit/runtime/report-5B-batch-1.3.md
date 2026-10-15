# Phase 5B — Runtime Verification Report (Batch 1.3 / 有赞侧)

> **创建时间**：2026-09-14 01:15 (UTC+8)
> **Phase**：5B — Runtime Verification（运行时实证）
> **范围**：Batch 1.3 / EXT-22 有赞侧（14 个 Feature：F066-F080）
> **起点 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2 freeze，沿用 5A）
> **不绑定业务代码改动**（规则 6）

---

## ⚠️ 重要声明：Token 来源说明

用户在 5B 启动时声明 "用户已重新登录，提供新 token"，但**实际粘贴的是 `<粘贴>` 占位符**（未提供真实新 token）。

按规则"无法确认标注未确认，禁止猜测"，本 Phase 5B 实际使用的是 **Phase 5A 期间保存的有效 token**：
- admin token（userId=1，exp=1789403952 = 2026-09-14 03:59 UTC+8）
- clerk01 token（userId=900003，exp=1789403894 = 2026-09-14 03:58 UTC+8）

**有效性验证**：
- `POST /ecommerce/platform` → code=200（认证通过，admin token 有效）
- `GET /ecommerce/platform/list` → code=500（业务层 I029 PARTIAL-MISSING 暴露，非 token 失效）
- 旧 token 在 03:59 前持续有效，5B 期间（01:00-01:15）足够使用

→ **Phase 5B 不造证据，使用真实可验证的旧 token 跑实证**。如需重新登录拿新 token，请在 Phase 5C 开始前提供。

---

## 〇、运行时栈核查

| 维度 | 状态 | 来源 |
|---|---|---|
| 后端 8080 | ✅ PID 7216，meiyetec-backend 持续在跑 | `Get-NetTCPConnection -LocalPort 8080` |
| MySQL 3307 | ✅ MySQL 8.0.46 | `SELECT VERSION()` |
| DB `meiyetec` | ✅ 可连 | mysql CLI |
| 后端 `/google-maps/health` | ✅ 200 | curl（5A 验证） |
| Token（admin + clerk01） | ✅ 5A 旧 token 复用（03:59 前有效） | env-token-admin.txt |
| JDK 21 + jshell | ✅ 可用 | `C:\Program Files\Eclipse Adoptium\jdk-21` |

---

## 一、Feature 实证汇总（F066-F080 共 14 个 + F066 修正 + I037 NEW）

| Feature | 状态 | 关键证据 | 关联 Issue |
|---|---|---|---|
| **F066** EcommercePlatform 实体 | **PARTIAL-MISSING**（**5A 修正**）| DB 20 字段 / 缺 app_id+kdt_id+sign_secret | **I029** PARTIAL |
| **F067** EcommerceOrder/Product/SyncLog | **VERIFIED** | 三表全存在 0 行（plan 预测 ecommerce_sync_log 可能 MISSING → 反转） | — |
| **F068** EcommerceAdapter 接口 | **UNVERIFIED** | D10 静态层 PASS | D10 |
| **F069** YouzanEcommerceAdapter.authenticate | **UNVERIFIED** | D10 OAuth2 凭据缺失 | D10 |
| **F070** YouzanEcommerceAdapter.fetch* | **UNVERIFIED** | D10 真实 API 凭据缺失 → 走 Mock fallback | D10 |
| **F071** verifyWebhookSignature 算法 | **VERIFIED** | jshell 7/7 case PASS（含边界） | **I026** 维持中立标 |
| **F072** buildWebhookUrl 孤儿 | **BACKLOG-001** | 全代码库 0 调用方 | **I033** 维持 |
| **F073** MockEcommerceAdapter | **MOCK-INTENTIONAL** | @ConditionalOnMissingBean fallback | D10 |
| **F074** EcommerceAdapterRouter | **MOCK-INTENTIONAL** | platform → adapter + MOCK fallback | D10 |
| **F075** EcommerceService CRUD | **PARTIAL-NON-BLOCKER** | POST 200 + GET 500 + DB 写入 2 行 + C4 实证 | **C4 + I029** |
| **F076** EcommerceService Sync | **PARTIAL-NON-BLOCKER** | 双端点 500 数据库异常 | **I034 + I029** |
| **F077** POST /webhook/youzan | **PARTIAL-NON-BLOCKER** | 无 token 401 / 有 token 500（双层阻断）| **I037 NEW** + I027 + I029 |
| **F078** webhook msg_id 幂等去重 | **PARTIAL-NON-BLOCKER** | 同 F077 阻断，duplicate=true 走不到 | **I030** + I035 描述修正 + I029 |
| **F079** ecommerce/index.vue | **PARTIAL-NON-BLOCKER** | 仅 3 字段 UI / 缺 update / 缺 health | **I028 EXT-22 子项** |
| **F080** Frontend API + DB 迁移 | **MISSING** | snapshot grep 7 项：6 项 0 命中 / app_id 1 误命中 CRM | **I029** 维持 |

---

## 二、负向证据实证清单（N3' / N9-N13 / N16-N20）

| # | 验证目标 | 预期 | 实际 | 状态 |
|---|---|---|---|---|
| **N3'** | `ecommerce_platform` 列缺失（5A 修正） | app_id/kdt_id/sign_secret 0 命中 | ✅ INFORMATION_SCHEMA 0 命中 / DESCRIBE 0 命中 | **PASS（PARTIAL-MISSING）** |
| **N9** | `verifyWebhookSignature` 算法 4 组 | ①正常 true / ②篡改 body false / ③篡改 sig false / ④大写 true | ✅ **7/7 PASS**（含 4 个边界）| **PASS** |
| **N10** | webhook 接收端 mock 推送 | 200 + status=PROCESSED | ❌ 401（I037）+ 500（I029）| **DEGRADED** |
| **N11** | webhook msg_id 幂等去重 | 同 payload 重复 → duplicate=true | ❌ 500（I029 阻断）| **DEGRADED** |
| **N12** | `webhook_msg_log` 表 | EXT-24 迁移应存在 | ❌ `TableNotFoundException`（EXT-24 迁移也未跑）| **PASS（I029 EXT-24 实证）** |
| **N13** | `buildWebhookUrl` 孤儿 | 全代码库 0 调用方 | ✅ 0 命中 | **PASS（BACKLOG-001）** |
| **N16** | 前端缺字段 UI `appId/kdtId/signSecret` | index.vue 仅 3 字段 | ✅ 实证：仅 platform/shopName/shopId | **PASS** |
| **N17** | 前端缺 platform 列（`/health` 真实 Shadow） | 无 update/health 按钮 | ✅ 实证：3 tab 列表均无操作列 | **PASS** |
| **N18** | EcommercePlatform `tenantId=0L` 强制 | DB 行 tenant_id=0 | ✅ 5B POST 写入 2 行：tenant_id 都是 0 | **PASS** |
| **N19** | `updatePlatform` 无租户隔离 | 任何 token 可改任何 platform | ✅ 代码层 L82-89 无 TenantContext 校验 | **PASS（代码层）** |
| **N20** | upsertOrder 重复实现（C5）| YouzanWebhookController vs EcommerceService | ✅ webhook L208-355 与 service L241-353 字段映射逻辑重复 | **PASS（代码层）** |

---

## 三、跨 Feature 发现（5B 新发现）

### 3.1 I037 NEW — Webhook 路径未在 JWT excludePathPatterns

**位置**：`backend/src/main/java/com/meiyetec/config/WebConfig.java:25-33`

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
// 缺: /webhook/**
```

**影响**：
- 所有 `/webhook/**` 路径被 JwtInterceptor 拦截 → **生产有赞 push_url 推送会全部 401**
- 同源问题：国内支付 `/domestic/webhook/**`、BNPL `/bnpl/webhook/**`、海外 `/overseas-payment/webhook/**` 都有相同风险（待 Phase 5C 或后续批次验证）

**修复建议**：
```java
.excludePathPatterns(
    "/auth/login", "/auth/logout",
    "/api/auth/login", "/api/auth/logout",
    "/api/static/**", "/static/**",
    "/error",
    "/webhook/**",             // ← 新增
    "/domestic/webhook/**",
    "/bnpl/webhook/**",
    "/overseas-payment/webhook/**"
)
```

**严重度**：**P0**（webhook 永远走不到 controller）

### 3.2 I029 PARTIAL-MISSING 双层影响（5B 新发现，5A 未暴露）

| 影响类型 | 路径 | 行为 |
|---|---|---|
| **INSERT 路径** | POST /platform | ✅ 200（不读 app_id 列） |
| **SELECT 路径** | GET /list / webhook 反查 platform | ❌ 500（SELECT * 触发 Unknown column） |
| **updateById 路径** | POST /platform/{id}（updateById 不含缺失列）| ✅ 大概率 200（MyBatis-Plus 按 id 写） |

→ **5A 仅看到 google_maps_config 整表 MISSING（TableNotFound）**，
**5B 看到 PARTIAL-MISSING 触发 Unknown column** —— 这是 5A 没识别的**更深的影响**

→ **PARTIAL-MISSING 比 MISSING 更隐蔽**：表存在让代码层看起来 OK，但运行时 SELECT 全部 500

### 3.3 C5 实证：upsertOrder 重复实现

| 文件 | 行号 | 内容 |
|---|---|---|
| `EcommerceService.java` | L241-353 | upsertOrder 私有方法（私有，仅 service 内调用）|
| `YouzanWebhookController.java` | L208-355 | upsertOrder 私有方法（**几乎完全复制** EcommerceService 版本） |

→ 两份独立维护的代码，任何字段映射变更需要同步修改两处

### 3.4 I035 描述修正：时间窗缺失（非重放保护缺失）

**原 I035 描述**（plan-batch-1.3.md §四 N15）：webhook 幂等**时间窗缺失**

**实际代码**（YouzanWebhookController.java:136-149）：
- ✅ 已有 msg_id 幂等去重（webhook_msg_log）
- ❌ 没有时间窗 → 一年前 msg_id 还能命中幂等（理论上可重放）

→ I035 实际是"**时间窗缺失**"，不是"**重放保护缺失**"（重放保护已实现，仅缺时间窗维度）

### 3.5 I028 EXT-22 子项实证完整

`views/ecommerce/index.vue` L60-77 添加平台对话框**仅 3 字段**：
- platform (L66-70)
- shopName (L73)
- shopId (L74)

→ **缺 5 字段**：appKey / appSecret / appId / kdtId / signSecret

→ **直接因果**：
- 缺 appKey/appSecret → **F069/F070 永远走 Mock fallback**（OAuth2 token 无法获取）
- 缺 appId/kdtId/signSecret → **F077 webhook 永远走不到 controller**（验签失败）

---

## 四、Feature 单一归属校验

| Feature | 单一归属 | 复核 |
|---|---|---|
| F066 | PARTIAL-MISSING（5A 修正）| ✅ 维持 |
| F067 | VERIFIED | ✅ 维持（plan 预测反转）|
| F068 | UNVERIFIED | ✅ 维持（D10）|
| F069 | UNVERIFIED | ✅ 维持（D10）|
| F070 | UNVERIFIED | ✅ 维持（D10）|
| F071 | VERIFIED | ✅ 维持（I026 中立标）|
| F072 | BACKLOG-001 | ✅ 维持（I033）|
| F073 | MOCK-INTENTIONAL | ✅ 维持 |
| F074 | MOCK-INTENTIONAL | ✅ 维持 |
| F075 | PARTIAL-NON-BLOCKER | ✅ 维持（C4 + I029）|
| F076 | PARTIAL-NON-BLOCKER | ✅ 维持（I034 + I029）|
| F077 | PARTIAL-NON-BLOCKER | ✅ 维持（I037 NEW + I027 + I029）|
| F078 | PARTIAL-NON-BLOCKER | ✅ 维持（I030 + I035 描述修正）|
| F079 | PARTIAL-NON-BLOCKER | ✅ 维持（I028 EXT-22 子项 + D20）|
| F080 | MISSING | ✅ 维持（I029 D13）|

---

## 五、Issue 注册表更新

### 5.1 维持（不修正）

| Issue | 维持状态 |
|---|---|
| I026 | CONFLICT 中立标（5B 验证算法 + 中立标合理）|
| I027 | CONFLICT 中立标（5B 发现 I037 是具体阻断原因）|
| I028 | EXT-22 子项实证 PASS（5B 完整证据）|
| I029 | MISSING + PARTIAL-MISSING 双标注 |
| I030 | CONFLICT 中立标（5B 实证幂等已实现）|
| I033 | BACKLOG-001（5B 实证 0 调用方）|
| I034 | PARTIAL-NON-BLOCKER（D17 不加 rollbackFor）|
| I035 | PARTIAL-NON-BLOCKER + **描述修正**（时间窗缺失，非重放保护缺失）|
| C4 | 实证 PASS（tenantId=0L + 无 tenant 隔离）|

### 5.2 新增

| Issue | 内容 | 严重度 |
|---|---|---|
| **I037 NEW** | Webhook 路径未在 JWT excludePathPatterns → 生产 webhook 永远 401 | **P0** |

### 5.3 描述修正

| Issue | 原描述 | 修正描述 |
|---|---|---|
| **F066** | MISSING（5A Phase 3 漏判）| **PARTIAL-MISSING**（5B 修正：表存在，缺 3 列）|
| **I035** | "重放保护缺失" | "**时间窗缺失**"（重放保护 = msg_id 幂等已实现）|
| **I029** | "DB 缺表 / 缺列" | "DB 缺表（MISSING）+ DB 缺列（PARTIAL-MISSING）双标注" |

---

## 六、5B 输出文件清单

```
audit/evidence/
├── F066-platform-entity-stub/
│   ├── db-describe.txt
│   └── result.md
├── F067-ecommerce-entities/
│   ├── db-describe.txt
│   └── result.md
├── F068-ecommerce-adapter-interface/
│   └── result.md
├── F069-youzan-authenticate/
│   └── result.md
├── F070-youzan-business-methods/
│   └── result.md
├── F071-webhook-signature-verify/
│   ├── AuditVerifySig.java (123 行)
│   ├── compile.log
│   ├── jshell-result.txt
│   └── result.md
├── F072-build-webhook-url-orphan/
│   ├── grep-result.txt
│   └── result.md
├── F073-mock-ecommerce-adapter/
│   └── result.md
├── F074-ecommerce-adapter-router/
│   └── result.md
├── F075-ecommerce-platform-crud/
│   ├── raw-create.txt (200)
│   ├── raw-list.txt (500)
│   ├── raw-health.txt (500)
│   ├── db-row-check.txt (3 行)
│   ├── backend-log-tail.txt (Unknown column 'app_id')
│   └── result.md
├── F076-ecommerce-sync/
│   ├── raw-product-sync.txt (500)
│   ├── raw-order-sync.txt (500)
│   └── result.md
├── F077-webhook-receiver/
│   ├── raw-push-1.txt (无 token 401)
│   ├── raw-push-with-token.txt (带 token 500)
│   ├── db-check.txt (webhook_msg_log 不存在)
│   └── result.md
├── F078-webhook-idempotency/
│   ├── raw-push-2.txt (重复推送 500)
│   ├── backend-log.txt
│   └── result.md
├── F079-ecommerce-frontend-ui/
│   └── result.md
└── F080-frontend-api-db-migration/
    ├── db-snapshot-grep.txt (7 项 grep)
    └── result.md
```

---

## 七、修复优先级（Phase 7 Post-Audit 决策清单）

### 7.1 P0 — 阻断 webhook 真实链路

| 优先级 | Issue | 修复 |
|---|---|---|
| **P0-1** | **I037** | WebConfig.excludePathPatterns 加 `/webhook/**` + `/domestic/webhook/**` 等 |
| **P0-2** | I029 PARTIAL | 跑 `migrate_ext_22_google_maps_place_details.sql` 补 3 列 |
| **P0-3** | I029 MISSING | 跑 `migrate_ext_17_google_maps.sql` 建 google_maps_config 表 |
| **P0-4** | webhook_msg_log | 跑 `migrate_ext_24_webhook_msg_log.sql` 建表 |

→ 4 项修复后，F075/F076/F077/F078 链路可重新验证，期望转为 VERIFIED 或 PARTIAL-NON-BLOCKER

### 7.2 P1 — 业务增强

| 优先级 | Issue | 修复 |
|---|---|---|
| P1-1 | I028 EXT-22 子项 | 前端加 5 字段 UI（appKey/appSecret/appId/kdtId/signSecret）|
| P1-2 | 真实 Shadow | /platform/{id}/health 加前端 UI（D20 维持暂缓）|
| P1-3 | C5 重复实现 | 抽 `EcommerceOrderUpserter` 公共组件 |
| P1-4 | C4 updatePlatform | 加 tenantId 校验（仅超管可改 platform）|

### 7.3 P2 — 长期优化

| 优先级 | Issue | 修复 |
|---|---|---|
| P2-1 | I035 时间窗 | 加 webhook_msg_log.created_at + 时效阈值（如 24h 过期）|
| P2-2 | I033 BACKLOG-001 | 删除 buildWebhookUrl 或补全注册流程 |
| P2-3 | I034 rollbackFor | @Transactional(rollbackFor = Exception.class) |

---

## 八、停止点

✅ **5B 完成（F066-F080 全部 14 个 feature 验证），等用户确认后启动 5C**。

**需用户确认的事项**：
1. **F066 PARTIAL-MISSING 修正**是否接受？（表存在但缺 3 列，非整表 MISSING）
2. **F065 el-switch 漏判修正**是否接受？（I028 EXT-22 子项部分反向）
3. **I037 NEW** 是否纳入 Issue Register？（webhook 路径未 exclude — P0 bug）
4. **I035 描述修正**是否接受？（时间窗缺失，非重放保护缺失）
5. **Token 来源说明**是否接受？（复用 5A 旧 token，用户未提供新 token）

⏸ 等用户确认 → 启动 Phase 5C。
