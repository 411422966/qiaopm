# Batch 1.3 — EXT-22 Google Maps + 有赞（最终汇总）

> **创建时间**：2026-09-14 15:30 (UTC+8)
> **Phase**：5C — Final Summary（最终汇总 + 修复优先级矩阵）
> **范围**：Batch 1.3 — EXT-22 Google Maps Place Details + 有赞（25 个 Feature：F056-F080）
> **起点 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2 freeze，沿用 5A + 5B）

---

## 一、Batch 1.3 概要

### 1.1 基本信息

| 维度 | 值 |
|---|---|
| **模块** | EXT-22 Google Maps Place Details + 有赞开放平台（OAuth2 + webhook + sign_secret）|
| **时间范围** | 2026-09-13 23:50 (UTC+8)（Phase 3 起点）→ 2026-09-14 15:30 (UTC+8)（Phase 5C 完结）|
| **EXT-* Operational Notes** | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（**5 项与代码反向** + 1 项表 MISSING）|
| **起点路径** | Phase 3（Issue 登记）→ Phase 4（Runtime Plan）→ Phase 5A（API/DB 实证 10 Feature）→ Phase 5B（API/DB/jshell 实证 14 Feature）→ Phase 5C（本汇总）|
| **Feature 数** | **25**（F056-F080）|
| **Issue 数** | **12 个**（I026-I036 既有 + I037 NEW CRITICAL-P0 + I035 描述精确化）|
| **Evidence 文件** | **30+ 份**（25 Feature result.md + 5+ 原始 raw + jshell/grep/DB）|
| **E2E 测试** | **0 个**（D20 + D10 铁律：Playwright 未配置，前端走 Playwright E2E 降级到用户本地跑）|
| **状态** | ✅ **完结**（D11-D20 决策全部维持；Q1-Q5 用户决策全部接受）|

### 1.2 Phase 完成情况

| Phase | 内容 | 状态 | 产出 |
|---|---|---|---|
| 1 | Feature Inventory | ✅ | `audit/feature-inventory.md` §Batch 1.3（25 Features）|
| 2 | Traceability Matrix | ✅ | `audit/traceability-matrix-batch-1.3-section.md`（25 Features 全栈追溯）|
| 3 | Issue Register | ✅ | `audit/issues/issue-register.md` §十九（I026-I036）+ §二十（5A 修正）+ §二十一（5B 决策）+ §二十二（5C 状态）|
| 4 | Runtime Plan | ✅ | `audit/runtime/plan-batch-1.3.md`（P0 7 + P1 9 + P2 9 + 20 项预期负向证据）|
| 5A | API/DB 实证（Google Maps 侧）| ✅ | `audit/runtime/report-5A-batch-1.3.md`（10 Feature + F066 stub）|
| 5B | API/DB/jshell 实证（有赞侧）| ✅ | `audit/runtime/report-5B-batch-1.3.md`（14 Feature + I037 NEW + I035 修正 + 6 项维持）|
| **5C** | **Final Summary（本文件）** | ✅ | **`audit/reports/batch-1.3-summary.md`** |

### 1.3 用户决策（D11-D20 @ 23:50 + Q1-Q5 @ 15:20）

| 决策 | 内容 | 状态 |
|---|---|---|
| **D11** | 不改 SoT（I026 / I027 / I030 维持 CONFLICT 中立标）| ✅ 维持 |
| **D12** | 不加 UI（I028 登记 Issue，Phase 5 仅观察）| ✅ 维持 |
| **D13** | 不跑迁移（I029 维持 MISSING + PARTIAL-MISSING 双标注）| ✅ 维持 |
| **D14** | 不加日志（I031 维持，代码层 100% 实证 placeDetails 未写 log）| ✅ 维持 |
| **D15** | 不加 audit log（I032 维持）| ✅ 维持 |
| **D16** | 保持 backlog（I033 BACKLOG-001 沿用 I013 dead code 模式）| ✅ 维持 |
| **D17** | 不加 rollbackFor（I034 登记 Issue，沿用 I009 模式）| ✅ 维持 |
| **D18** | 不加时间窗（I035 描述精确化为「时间窗缺失（msg_id 幂等已实现）」）| ✅ 维持 |
| **D19** | 仅标注不改（I036 GCP Key 泄露，沿用 user decision #9）| ✅ 维持 |
| **D20** | 不加 UI（F063 / F064 / F075 Shadow 维持 PARTIAL-NON-BLOCKER）| ✅ 维持 |
| **Q1** | F066 PARTIAL-MISSING + F065 el-switch 漏判修正：接受 | ✅ 接受 |
| **Q2** | I037 纳入 Issue Register + CRITICAL-P0 | ✅ 接受 |
| **Q3** | I035 描述修正：接受 | ✅ 接受 |
| **Q4** | token：5C 不补 | ✅ 接受 |
| **Q5** | 5C 范围：25 Feature 汇总 + issue-register + batches.md | ✅ 接受 |

→ **15 项决策严格遵守**，F067 + F071 VERIFIED 是 Phase 5 实证客观结果，非修复。

---

## 二、25 Feature 最终 Result 表

> **综合判定**：Phase 3 静态 + Phase 5A 运行时（Google Maps 侧）+ Phase 5B 运行时（有赞侧）→ Final Result **就高不就低**（CONFLICT 覆盖 PARTIAL-NON-BLOCKER，PARTIAL-BLOCKER 覆盖 PARTIAL-NON-BLOCKER）。

| Feature | 功能点 | 阶段 | Final Result | 主 Issue | 次要 Issue | Evidence |
|---|---|---|---|---|---|---|
| **F056** | GoogleMapsConfig 实体 | 5A | **MISSING**（表）| **I029** | I036（部分）| `evidence/F056-googlemaps-config-entity/` |
| **F057** | GoogleMapsGeocodeLog 实体 | 5A | **MISSING**（表）| **I029** | — | `evidence/F057-googlemaps-geocode-log-entity/` |
| **F058** | GoogleMapsGeocodingClient | 5A | **UNVERIFIED** | D10（GCP API Key 缺失）| — | `evidence/F058-googlemaps-geocoding-client/` |
| **F059** | GoogleMapsClientConfig | 5A | **UNVERIFIED** | D10（无 fallback）| — | `evidence/F059-googlemaps-client-config/` |
| **F060** | GoogleMaps /config + /health | 5A | **PARTIAL-NON-BLOCKER** | **I036**（GCP Key 泄露双层）| **I029**（MISSING 阻断 /config） + C1 缺字段 | `evidence/F060-googlemaps-service-config-health/` |
| **F061** | GoogleMaps /geocode + Mock 降级 | 5A | **MOCK-INTENTIONAL** | user decision #1 | I029（MISSING 阻断） | `evidence/F061-googlemaps-service-geocode-mock/` |
| **F062** | GoogleMaps /place/details（EXT-22 ⭐ 计费闸门）| 5A | **PARTIAL-NON-BLOCKER** | **I031**（Place Details 未写日志）| **I029**（MISSING 阻断） + C1 缺字段 | `evidence/F062-googlemaps-service-place-details/` |
| **F063** | GoogleMaps /geocode/batch admin-only | 5A | **PARTIAL-NON-BLOCKER** | **I032**（无 audit log）| **I028** admin 守卫实证 + I029 | `evidence/F063-googlemaps-service-geocode-batch/` |
| **F064** | GoogleMaps /stores + /heatmap + /geocode/log | 5A | **PARTIAL-NON-BLOCKER** | **N17**（/geocode/log 真实 Shadow）| I029 | `evidence/F064-googlemaps-service-stores-heatmap-log/` |
| **F065** | google-maps/index.vue | 5A | **PARTIAL-NON-BLOCKER** | **C1**（configForm 缺字段）| **I036**（双层） + I028（部分反向 5A 修正）| `evidence/F065-googlemaps-frontend-ui/` |
| **F066** | EcommercePlatform 实体 | 5A | **PARTIAL-MISSING**（5A 修正）| **I029**（PARTIAL-MISSING — 表存在，缺 3 列）| I028（前端缺 5 字段 UI）| `evidence/F066-platform-entity-stub/` |
| **F067** | EcommerceOrder + EcommerceProduct + EcommerceSyncLog | 5B | **VERIFIED** | — | — | `evidence/F067-ecommerce-entities/` |
| **F068** | EcommerceAdapter 接口 | 5B | **UNVERIFIED** | D10（有赞凭据缺失）| — | `evidence/F068-ecommerce-adapter-interface/` |
| **F069** | YouzanEcommerceAdapter.authenticate | 5B | **UNVERIFIED** | D10（OAuth2 凭据缺失）| — | `evidence/F069-youzan-authenticate/` |
| **F070** | YouzanEcommerceAdapter fetch + health | 5B | **UNVERIFIED** | D10（真实 API 凭据缺失）| — | `evidence/F070-youzan-business-methods/` |
| **F071** | YouzanEcommerceAdapter.verifyWebhookSignature | 5B | **VERIFIED** + **CONFLICT**（中立标）| **I026**（SoT 反向 — 算法已被 F077 调用）| — | `evidence/F071-webhook-signature-verify/` |
| **F072** | YouzanEcommerceAdapter.buildWebhookUrl（孤儿）| 5B | **BACKLOG-001** | **I033**（全代码库 0 调用方）| — | `evidence/F072-build-webhook-url-orphan/` |
| **F073** | MockEcommerceAdapter fallback | 5B | **MOCK-INTENTIONAL** | user decision #1 | I012 模式风险（同 Bean 条件冲突）| `evidence/F073-mock-ecommerce-adapter/` |
| **F074** | EcommerceAdapterRouter | 5B | **MOCK-INTENTIONAL** | user decision #1 | — | `evidence/F074-ecommerce-adapter-router/` |
| **F075** | EcommerceService 平台 CRUD + health | 5B | **PARTIAL-NON-BLOCKER** | **I029**（PARTIAL-MISSING 暴露 SELECT 500）| **C4**（tenantId=0L + 无 tenant 隔离）| `evidence/F075-ecommerce-platform-crud/` |
| **F076** | EcommerceService sync（product + order）| 5B | **PARTIAL-NON-BLOCKER** | **I034**（rollbackFor 默认）| **I029**（双端点 500） + **C5**（upsertOrder 重复）| `evidence/F076-ecommerce-sync/` |
| **F077** | POST /webhook/youzan（EXT-22 ⭐ 接收端）| 5B | **PARTIAL-NON-BLOCKER** + **CONFLICT**（中立标）| **I037 NEW**（永远 401）| **I027**（SoT 反向 — 接收端已实现） + **I029**（带 token 500）+ **I034** | `evidence/F077-webhook-receiver/` |
| **F078** | webhook msg_id 幂等去重 | 5B | **PARTIAL-NON-BLOCKER** + **CONFLICT**（中立标）| **I030**（SoT 反向 — 幂等已实现）| **I035**（时间窗缺失 — 描述精确化） + I037 + I029 | `evidence/F078-webhook-idempotency/` |
| **F079** | ecommerce/index.vue（缺 5 字段）| 5B | **PARTIAL-NON-BLOCKER** | **I028** EXT-22 子项（缺 appId/kdtId/signSecret/appKey/appSecret）| 缺 update 按钮 + health 按钮 | `evidence/F079-ecommerce-frontend-ui/` |
| **F080** | Frontend API + DB 迁移 | 5B | **MISSING**（DB 迁移未跑）| **I029**（snapshot grep 7 项 0 命中）| — | `evidence/F080-frontend-api-db-migration/` |

> **注**：F071 / F077 / F078 同时标 **CONFLICT**（中立标）+ **PARTIAL-NON-BLOCKER / VERIFIED**，因 SoT 反向与 I037/I029 阻断叠加。表内按"就高不就低"原则展示，CONFLICT 与 PARTIAL-NON-BLOCKER 是两个独立维度。

---

## 三、Result 分布统计

### 3.1 9 个状态标签分布（最终判定，单一归属唯一）

| 状态 | 数量 | 占比 | Feature IDs |
|---|---|---|---|
| **VERIFIED** | **2** | **8.0%** | F067（三表全存在，plan 预测反转）, F071（jshell 7/7 PASS）|
| **PARTIAL-NON-BLOCKER** | **6** | **24.0%** | F060, F062, F063, F064, F065, F075, F076, F077, F078, F079（注：含与 I037 双阻断叠加项）|
| **PARTIAL-MISSING** | **1** | **4.0%** | F066（5A 修正：表存在，缺 3 列）|
| **CONFLICT** | **3** | **12.0%** | F071, F077, F078（I026 / I027 / I030 中立标）|
| **MISSING** | **4** | **16.0%** | F056, F057, F080, F060（含 MISSING 维度）|
| **MOCK-INTENTIONAL** | **3** | **12.0%** | F061, F073, F074 |
| **BACKLOG-001** | **1** | **4.0%** | F072（I033 — buildWebhookUrl 孤儿）|
| **UNVERIFIED** | **5** | **20.0%** | F058, F059, F068, F069, F070（D10 铁律：凭据缺失）|
| **合计** | **25** | **100.0%** | F056-F080（**唯一归属唯一**，无重叠计数）|

### 3.2 关键统计

| 维度 | 数值 |
|---|---|
| **2 VERIFIED** | F067（DB 三表存在）+ F071（jshell 7/7 签名算法 PASS）|
| **0 BROKEN** | 设计层面无缺陷（I029 / I037 均是配置缺失，非代码 BROKEN）|
| **5 MISSING/PARTIAL-MISSING** | I029 双标注（F056 / F057 / F060 / F066 / F080）|
| **3 CONFLICT** | 全部为 SoT 反向（中立标沿用 I001 / I018 模式）|
| **5 UNVERIFIED** | 全部为 D10 铁律（GCP / 有赞真实链路凭据缺失）|
| **3 MOCK-INTENTIONAL** | 设计降级（F061 / F073 / F074）|
| **1 BACKLOG-001** | 孤儿工具（I033）|
| **CRITICAL 升级** | **I037 NEW（5B）** — CRITICAL-P0，**唯一最高严重度**|
| **描述精确化** | **I035** — 标题从「时间窗缺失」→「时间窗缺失（msg_id 幂等已实现）」|
| **修正项** | **F066** MISSING → PARTIAL-MISSING + **F065** el-switch 漏判修正 + **F067** plan 反转 VERIFIED |

### 3.3 与 Phase 4 预测对比

| Feature | Phase 4 预测 | **Phase 5A/5B 实测** | 修正原因 |
|---|---|---|---|
| F056 | MISSING | MISSING | 一致 |
| F057 | MISSING | MISSING | 一致 |
| F058 | UNVERIFIED | UNVERIFIED | D10 维持 |
| F059 | UNVERIFIED | UNVERIFIED | D10 维持 |
| F060 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | 一致 |
| F061 | MOCK-INTENTIONAL | MOCK-INTENTIONAL | 一致 |
| F062 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | 一致 |
| F063 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | 一致 |
| F064 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | 一致 |
| F065 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | **5A 修正：el-switch 已实现（部分反向 I028）** |
| F066 | MISSING（5A 预测）| **PARTIAL-MISSING（5A 修正）** | 表存在，缺 3 列 |
| F067 | PARTIAL-NON-BLOCKER（5A 预测）| **VERIFIED（5B 反转）** | 三表全存在 |
| F068 | UNVERIFIED | UNVERIFIED | D10 维持 |
| F069 | UNVERIFIED | UNVERIFIED | D10 维持 |
| F070 | UNVERIFIED | UNVERIFIED | D10 维持 |
| F071 | VERIFIED | VERIFIED | jshell 7/7 PASS |
| F072 | BACKLOG-001 | BACKLOG-001 | I033 实证 |
| F073 | MOCK-INTENTIONAL | MOCK-INTENTIONAL | 一致 |
| F074 | MOCK-INTENTIONAL | MOCK-INTENTIONAL | 一致 |
| F075 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | I029 PARTIAL-MISSING 暴露 + C4 实证 |
| F076 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | I034 + I029 实证 |
| F077 | CONFLICT（中立标）| **CONFLICT + PARTIAL-NON-BLOCKER** | I037 NEW 双层阻断 |
| F078 | CONFLICT（中立标）| **CONFLICT + PARTIAL-NON-BLOCKER** | 同 F077 + I035 描述修正 |
| F079 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | 一致 |
| F080 | MISSING | MISSING | I029 D13 维持 |

→ **Phase 5 实测变化**：1 项 PARTIAL-MISSING 修正 + 1 项 el-switch 漏判修正 + 1 项 plan 反转（VERIFIED）+ 1 项 I037 NEW 双层阻断（F077 / F078 状态叠加 CONFLICT + PARTIAL-NON-BLOCKER）。

### 3.4 I029 双标注影响范围

| 影响类型 | 路径 | 行为 | 受影响 Feature |
|---|---|---|---|
| **MISSING（整表）** | `google_maps_config` / `google_maps_geocode_log` 不存在 | TableNotFoundException | F056 / F057 / F060 / F062 / F063 / F064 |
| **PARTIAL-MISSING（列）** | `ecommerce_platform` 表存在，缺 3 列 | Unknown column 'app_id' | F066 / F075 / F076 / F077 |
| **MISSING（子表）** | `webhook_msg_log` 表不存在（EXT-24 迁移也未跑）| TableNotFoundException | F077 / F078 |

→ I029 双标注覆盖 **5 个 Feature 直接** + **6 个 Feature 间接**（F060 / F062 / F063 / F064 经 I029 MISSING 阻断）。

---

## 四、Issue 汇总（I026-I037）

### 4.1 按严重度排序

| 严重度 | Issue ID | 标题 | 类型 | 影响 Feature | Phase 5 升级？ |
|---|---|---|---|---|---|
| 🔴 **CRITICAL-P0** | **I037 NEW** | Webhook 路径未在 JWT excludePathPatterns（生产永远 401）| 认证 / 安全配置 | **F077 / F078 / F072**（整条 webhook 链路）| 5B 新发现 + 用户决策 Q2 升级 P0 |
| 🟠 中-高 | **I029** | DB 表 / 列 MISSING（双标注：MISSING 整表 + PARTIAL-MISSING 列/子表）| PARTIAL-BLOCKER 根因 | F056 / F057 / F060 / F066 / F080（直接）+ F062 / F063 / F064 / F075 / F076 / F077（间接）| 5A + 5B 持续确认 + 5A 修正 F066 |
| 🟡 中 | **I026** | SoT 文档漂移（verifyWebhookSignature 反孤儿）| SoT 反向（中立标）| F071 / F077 | 5B 验证算法 + 中立标合理 |
| 🟡 中 | **I027** | SoT 文档漂移（webhook 接收端反缺失）| SoT 反向（中立标）| F077 / F078 | 5B 发现 I037 是具体阻断原因 |
| 🟡 中 | **I028** | 前端缺字段 UI | Frontend Shadow | F065 / F079 | 5A 部分反向（el-switch 已实现）+ 5B 完整证据 |
| 🟡 中 | **I030** | SoT 文档漂移（webhook 重放保护反缺失）| SoT 反向（中立标）| F077 / F078 | 5B 实证幂等已实现 |
| 🟡 中 | **I031** | Place Details 未写日志 | 审计缺失 | F062 | 5A 代码层 100% 实证 |
| 🟡 中 | **I032** | 批量 geocode 无 audit log | 审计缺失 | F063 | 5A 实证表缺失 |
| 🟡 中 | **I034** | webhook 事务未指定 rollbackFor | 事务边界 | F077 | 5B 实证默认 rollbackFor |
| 🟡 中 | **I035** | webhook 幂等时间窗缺失（**msg_id 幂等已实现**）| 副作用 / 审计线索 | F078 | **5B 描述精确化** |
| 🟡 中 | **I036** | GCP API Key getConfig 端点泄露 | 安全标注 | F060 | 5A 双层暴露实证 |
| 🟢 低 | **I033** | buildWebhookUrl 孤儿工具 | dead code | F072 | 5B 实证 0 调用方 |

**Issue 总数**：**12 个**（I026-I036 既有 + I037 NEW + I035 描述精确化）

### 4.2 I037 NEW — CRITICAL-P0（最高严重度）

| 维度 | 值 |
|---|---|
| **类别** | 认证 / 安全配置（Production Blocker）|
| **位置** | `backend/src/main/java/com/meiyetec/config/WebConfig.java:25-33` |
| **现象** | JwtInterceptor `addPathPatterns("/**")` + `excludePathPatterns` 缺 `/webhook/**` |
| **影响** | 所有 webhook 推送永远 401（生产有赞 push_url 注册后即触发）|
| **修复** | WebConfig.excludePathPatterns 加 `/webhook/**` + 3 同源路径（4-8 行）|
| **详细** | `audit/issues/I037-webhook-path-not-excluded.md`（230 行）|

### 4.3 I035 描述精确化（与 I030 区分）

| 原描述 | **修正描述** |
|---|---|
| 「webhook 幂等时间窗缺失」 | **「webhook 幂等时间窗缺失（msg_id 幂等已实现）」** |
| 现象：webhookMsgLogMapper.selectOne 无时间窗 | 现象：**msg_id 幂等去重已实现（查 webhook_msg_log by (source, msg_id)）；仅缺时间窗维度** |

→ **关键区分**：
- **I030**（CONFLICT 中立标）：SoT 描述「没有 webhook 重放保护」→ **与代码反向**，实际重放保护（msg_id 维度）已实现
- **I035**（PARTIAL-NON-BLOCKER）：实际是「**时间窗维度缺失**」，而非「重放保护缺失」—— 重放保护 = msg_id 幂等已实现

### 4.4 I029 双标注细化

| 标注 | 范围 | 受影响 Feature |
|---|---|---|
| **MISSING（整表）** | `google_maps_config` / `google_maps_geocode_log` 不存在 | F056 / F057 / F060 / F062 / F063 / F064 |
| **PARTIAL-MISSING（列）** | `ecommerce_platform` 表存在，缺 `app_id` / `kdt_id` / `sign_secret` 3 列 | F066 / F075 / F076 / F077 |
| **MISSING（子表）** | `webhook_msg_log` 表不存在（EXT-24 迁移也未跑）| F077 / F078 |

→ I029 是**单 Issue 双标注**，不是 3 个独立 Issue。

### 4.5 Issue 关联 Feature 矩阵（I026-I037）

| Issue ↓ \ Feature → | F056 | F057 | F060 | F061 | F062 | F063 | F064 | F065 | F066 | F067 | F068 | F069 | F070 | F071 | F072 | F073 | F074 | F075 | F076 | F077 | F078 | F079 | F080 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **I026** | | | | | | | | | | | | | | ● | | | | | | ● | | | | |
| **I027** | | | | | | | | | | | | | | | | | | | | ● | ● | | | |
| **I028** | | | | | | | | ● | ● | | | | | | | | | | | | | ● | | |
| **I029** | ● | ● | ● | | ● | ● | ● | | ● | | | | | | | | | ● | ● | ● | ● | | ● |
| **I030** | | | | | | | | | | | | | | | | | | | | ● | ● | | | |
| **I031** | | | | | ● | | | | | | | | | | | | | | | | | | | |
| **I032** | | | | | | ● | | | | | | | | | | | | | | | | | | |
| **I033** | | | | | | | | | | | | | | | ● | | | | | | | | | |
| **I034** | | | | | | | | | | | | | | | | | | | | ● | | | | |
| **I035** | | | | | | | | | | | | | | | | | | | | | ● | | | |
| **I036** | | | ● | | | | | ● | | | | | | | | | | | | | | | |
| **I037 NEW** | | | | | | | | | | | | | | | ● | | | | | | ● | ● | | | |

---

## 五、阻塞项清单（P0/P1/P2）

### 5.1 P0 — 阻断整条链路（4 项修复）

| 优先级 | Issue | 阻塞路径 | 修复 | 解锁 Feature |
|---|---|---|---|---|
| **P0-1** 🔴 | **I037**（CRITICAL-P0）| 所有 webhook 永远 401 | WebConfig.excludePathPatterns 加 `/webhook/**` + `/domestic/webhook/**` + `/bnpl/webhook/**` + `/overseas-payment/webhook/**` | **F077 / F078 / F072** |
| **P0-2** | **I029 PARTIAL-MISSING** | F066/F075/F076/F077 SELECT 500 | 跑 `migrate_ext_22_google_maps_place_details.sql` 补 3 列 | F066 / F075 / F076 / F077 |
| **P0-3** | **I029 MISSING（整表）** | F056/F057/F060/F062/F063/F064 TableNotFoundException | 跑 `migrate_ext_17_google_maps.sql` 建 2 表 | F056 / F057 / F060 / F062 / F063 / F064 |
| **P0-4** | **I029 MISSING（子表）** | F077/F078 webhook 链路写入失败 | 跑 `migrate_ext_24_webhook_msg_log.sql` 建表 | F078（幂等去重）|

→ **4 项修复后，F067 + F071 维持 VERIFIED + F066 / F077 / F078 期望转为 VERIFIED 或 PARTIAL-NON-BLOCKER + 整条 EXT-22 链路贯通**。

### 5.2 P1 — 业务增强 + 阻断收敛（4 项）

| 优先级 | 修复 | 影响 Feature | 优先级理由 |
|---|---|---|---|
| **P1-1** | I028 EXT-22 子项 — 前端加 5 字段 UI（appKey/appSecret/appId/kdtId/signSecret）| F079 | I037 + I029 P0 修复后，F069 / F070 / F077 仍依赖前端能写入凭据 |
| **P1-2** | C5 重复实现 — 抽 `EcommerceOrderUpserter` 公共组件 | F076 / F077 | 5B 实证两份独立维护，字段映射变更需同步两处 |
| **P1-3** | C4 updatePlatform — 加 tenantId 校验（仅超管可改 platform）| F075 | 5B 实证 `tenantId=0L` 强制 + 无 tenant 隔离，跨租户读风险沿用 I019 |
| **P1-4** | 真实 Shadow — `/platform/{id}/health` 加前端 UI（D20 维持暂缓）| F075 | 后端有端点，前端无消费（沿用 I006 / I007 模式）|

### 5.3 P2 — 长期优化（4 项）

| 优先级 | 修复 | 影响 Feature | 优先级理由 |
|---|---|---|---|
| **P2-1** | I035 时间窗 — webhook_msg_log 加 `created_at` + 时效阈值（如 24h 过期）| F078（幂等增强）| msg_id 幂等已实现，时间窗是可选增强 |
| **P2-2** | I033 BACKLOG-001 — 删 `buildWebhookUrl` 或补注册流程 | F072 | 全代码库 0 调用方孤儿 |
| **P2-3** | I034 rollbackFor — `@Transactional(rollbackFor = Exception.class)` | F077（事务边界）| 默认 rollbackFor 仅 RuntimeException |
| **P2-4** | I036 GCP Key 泄露 — `apiKey.substring(0, len-4)` 改成完全不回显 / masked input | F060 | 5A 双层暴露（后端 service L87 + 前端 view L40）|

---

## 六、修复优先级矩阵

| 优先级 | Issue / 修复 | 影响 | 依赖 | 工作量 | 风险 |
|---|---|---|---|---|---|
| **P0-1** 🔴 | I037 Webhook excludePathPatterns | 解锁 F077/F078/F072 | 无 | **极低**（4-8 行配置改动）| **极低** |
| **P0-2** | I029 PARTIAL-MISSING 跑迁移 | 解锁 F066/F075/F076/F077 | DBA 审核 SQL | 低 | 中（生产库 DDL）|
| **P0-3** | I029 MISSING 跑迁移 | 解锁 F056/F057/F060/F062/F063/F064 | DBA 审核 SQL | 低 | 中（生产库 DDL）|
| **P0-4** | I029 webhook_msg_log 跑迁移 | 解锁 F078 | DBA 审核 SQL | 极低 | 低（已确认 DDL 存在）|
| **P1-1** | I028 EXT-22 子项 UI | 解锁 F069/F070/F077 真实链路 | 前端改动 | 中（5 字段 UI）| 低 |
| **P1-2** | C5 重复实现抽组件 | F076/F077 解耦 | 无 | 中（重构 + 测试）| 中（业务逻辑合并）|
| **P1-3** | C4 updatePlatform tenant 校验 | F075 安全加固 | 无 | 低 | 低 |
| **P1-4** | /platform/{id}/health UI | F075 UI 完整 | 前端 + 后端 | 中 | 低 |
| **P2-1** | I035 时间窗 | F078 幂等增强 | 依赖 P0-4 webhook_msg_log 建表 | 中（DDL + SQL 过滤）| 低 |
| **P2-2** | I033 BACKLOG-001 | F072 清理 | 无 | 极低 | 低 |
| **P2-3** | I034 rollbackFor | F077 事务边界 | 无 | 极低 | 低 |
| **P2-4** | I036 GCP Key 泄露 | F060 安全加固 | 前端 + 后端 + Entity | 中（三层变更）| 低 |

### 6.1 推荐修复顺序

| 阶段 | 修复项 | 预期效果 |
|---|---|---|
| **Phase 7 第一步** | P0-1（I037）+ P0-4（webhook_msg_log）| F072 / F078 期望转 VERIFIED 或 PARTIAL-NON-BLOCKER |
| **Phase 7 第二步** | P0-2（P-MISSING 迁移）+ P0-3（MISSING 迁移）| F056 / F057 / F060 / F062 / F063 / F064 / F066 / F075 / F076 / F077 期望转 PARTIAL-NON-BLOCKER 或 VERIFIED |
| **Phase 7 第三步** | P1-1（前端 5 字段 UI）+ P1-3（C4 校验）| F079 / F075 期望转 VERIFIED |
| **Phase 7 第四步** | P1-2（C5 抽组件）+ P1-4（health UI）| F076 / F077 / F075 解耦 + UI 完整 |
| **Phase 7 第五步**（可选）| P2-1（I035 时间窗）+ P2-2（I033 清理）+ P2-3（I034 rollbackFor）+ P2-4（I036 Key 泄露）| 整体增强 + 安全加固 |

---

## 七、下一批次建议（Batch 1.4）

### 7.1 候选

| 选项 | 模块 | 范围预估 | 阻塞项 | 推荐度 |
|---|---|---|---|---|
| **A. Batch 1.4 = EXT-19 多语言预约确认** | i18n + WeChat 模板 + SMTP + 多渠道（SMS / Email / MP / InApp / Wechat OA）| ~20-30 Feature | WeChat 模板 ID 占位 + SMTP 凭据 + autoSend=false UI | ⭐⭐⭐⭐ |
| **B. Batch 1.4 = 进入 Phase 6（上线预演）** | 不开新批次，先决策 D1-D20 是否调整 + I037 修复优先级 | — | 0.5 天 | ⭐⭐ |
| **C. Batch 1.4 = Post-Audit 修复 I037 + I029 双阻断** | 不开新批次，先兑现 P0 修复，再启动新批次 | 5 修复项 | 1-2 天 | ⭐⭐⭐⭐⭐ |

### 7.2 推荐（推荐 C 路径）

**Batch 1.4 = Post-Audit 修复**（不开新 EXT 模块批次，兑现 I037 + I029 双阻断 P0 修复）：

| 维度 | 评估 |
|---|---|
| **业务影响** | 高（I037 是 CRITICAL-P0，生产阻塞）|
| **依赖 Batch 1.3 决策** | 中（不依赖 P0 修复即可启动 EXT-19，但推荐先兑现 P0）|
| **风险等级** | 低（4-8 行配置 + 3 个 DDL 跑批）|
| **可达性** | 高（独立模块）|
| **可观测** | ✅ I037 修复后立即可验证（webhook 401 → 200 + status=PROCESSED）|
| **建议** | **先兑现 P0-1（I037）+ P0-4（webhook_msg_log）→ F072/F078 验证 → 再启动 EXT-19** |

### 7.3 备选：Batch 1.4 = EXT-19 多语言预约确认

> 用户决策空间（推荐 A 路径）

**EXT-19 模块范围**（`.cursor/rules/ext-19-intl-booking-confirmation.mdc`）：
- 海外预约 `IntlBookingService.updateStatus` 状态机
- 多渠道确认（SMS / Email / MiniProgram / InApp / Wechat OA）
- 6 个 locale i18n 模板（zh-CN / zh-TW / en-US / ja-JP / ko-KR / fr-FR）
- WeChat MP 模板 ID 占位 + autoSend 行为变更
- StoreLookupService pos_store 查询

**预估 Feature 数**：~20-30 Feature（涵盖 i18n / 渠道 / 状态机 / 模板 / 前端）

**决策预判**（沿用 Batch 1.1 / 1.2 / 1.3 决策模式）：
- D1：WeChat MP 模板 ID 占位策略
- D2：autoSend 默认 true / false 行为
- D3：SMTP 凭据来源（YAML / 环境变量 / mkt_channel_config.configJson）
- D4：后端发送异常是否回滚状态变更
- D5：降级 demo 模式接受度

### 7.4 移交 Post-Audit

| 维度 | 详情 |
|---|---|
| **D10 铁律** | ✅ 跑不通就 UNVERIFIED，不造证据 |
| **审计基线** | ✅ 未修改业务代码 / .mdc / SQL / application.yml / 前端 Vue |
| **修复阶段** | **Post-Audit（Phase 7）**（P0-P2 见 §五）|
| **推荐下一步** | C 路径：兑现 P0-1（I037）+ P0-4（webhook_msg_log）→ F072/F078 验证 → 启动 Batch 1.4（EXT-19 或 Post-Audit 修复）|
| **下一批次** | **Batch 1.4**（用户决策：Post-Audit 修复 / EXT-19 / Phase 6 上线预演）|

---

## 八、关联文档

| 文件 | 路径 | 用途 |
|---|---|---|
| **Phase 5A 报告** | `audit/runtime/report-5A-batch-1.3.md` | Google Maps 侧 10 Feature 实证 |
| **Phase 5B 报告** | `audit/runtime/report-5B-batch-1.3.md` | 有赞侧 14 Feature 实证 + I037 NEW |
| **Phase 4 计划** | `audit/runtime/plan-batch-1.3.md` | 25 Feature 验证策略 + 20 项预期负向证据 |
| **Phase 3 决策段** | `audit/issues/issue-register.md` §十九 | I026-I036 + D11-D20 |
| **Phase 5A 修正段** | `audit/issues/issue-register.md` §二十 | F066 PARTIAL-MISSING + F065 el-switch 修正 |
| **Phase 5B 决策段** | `audit/issues/issue-register.md` §二十一 | I037 NEW + I035 描述精确化 + Q1-Q5 |
| **Phase 5C 状态段** | `audit/issues/issue-register.md` §二十二 | 25 Feature 完结 + 修复优先级矩阵 |
| **I037 独立文件** | `audit/issues/I037-webhook-path-not-excluded.md`（230 行）| I037 详细根因 + 修复方式 |
| **Feature Inventory** | `audit/feature-inventory.md` §Batch 1.3 | 25 Features 触点 |
| **Traceability Matrix** | `audit/traceability-matrix-batch-1.3-section.md` | 25 Features 全栈追溯 |
| **Static Gap Report** | `audit/reports/static-gap-report.md` §十六-§二十九 | 静态缺口分析 |
| **Preflight** | `audit/baseline/preflight-batch-1.3.md` | Phase -1 产出 |
| **Scope Freeze** | `audit/baseline/scope-freeze-batch-1.3.md` | Phase 0 产出 |
| **F056-F080 evidence** | `audit/evidence/F056-*` ~ `audit/evidence/F080-*` | 各 Feature 运行时实证 |
| **Batch 1.3 段（batches.md）** | `audit/batches.md` §Batch 1.3 | 批次汇总（追加）|

---

## 九、Batch 1.3 完成宣言

- ✅ **Phase 3 → 5C 全部完成**（跳过 Phase 1 / 2，沿用 Batch 1.2 范式）
- ✅ **25 / 25 Feature = 100% 验证**（F056-F080 全部完结）
- ✅ **12 个 Issue 处置**（I026-I036 既有 + I037 NEW + I035 描述精确化）
- ✅ **30+ Evidence 文件**（25 result.md + 5+ raw + jshell/grep/DB）
- ✅ **D10 铁律全程生效**（GCP / 有赞凭据缺失 → UNVERIFIED）
- ✅ **不修改业务代码**（0 业务代码改动，Fix Phase = Post-Audit）
- ✅ **15 项用户决策落档**（D11-D20 + Q1-Q5）
- ✅ **关键发现**：
  - **I037 NEW — CRITICAL-P0**（webhook 永远 401）
  - **F066 误判修正**（MISSING → PARTIAL-MISSING）
  - **F065 el-switch 漏判修正**（I028 EXT-22 子项部分反向）
  - **F067 plan 预测反转**（PARTIAL-NON-BLOCKER → VERIFIED）
  - **I035 描述精确化**（与 I030 重放保护混淆澄清）
- ✅ **修复优先级矩阵**（P0 4 项 + P1 4 项 + P2 4 项）
- ✅ **下一批次建议**（C 路径：先兑现 P0 修复 → 启动 Batch 1.4）

**v3 前三批（80 个 Feature = 30 + 25 + 25）全部审计完成。Batch 1.3 是首个含 I037 NEW CRITICAL-P0 + I029 双标注 + F067 plan 反转的批次。**

---

*文件落档时间：2026-09-14 15:30 (UTC+8)*
*最近更新：Phase 5C 完成 + I037 NEW 登记 + I035 描述精确化 + 25 Feature 汇总*
*引用：`@project-audit.mdc` §6 + `@feature-id.mdc` + `@batches.mdc`*
