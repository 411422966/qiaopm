### F071 — Ecommerce / Adapter / YouzanEcommerceAdapter.verifyWebhookSignature（md5 签名算法，⚠ CONFLICT I026）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F071 | `feature-inventory.md` §1.3.2 row 16 |
| 前端路由 | — | （无前端入口；F077 调用） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | （Adapter 静态方法）|
| Method | — | |
| Controller | — | |
| Service | **`YouzanEcommerceAdapter.verifyWebhookSignature`** | **`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:146-168`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ✅ N/A | |
| Business Status | **算法（SoT §0.5 完全一致）**：`expected = lowercase( hex( md5( signSecret + rawBody ) ) )`；比较 `equalsIgnoreCase`（防御性）；`null/empty` 任一入参 → false；异常 catch → false；签名算法与 SoT §0.5 一致 | `backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:146-168` |
| E2E Status | UNVERIFIED | Phase 5 jshell 单元测试（3 组 payload：正常 / 篡改 body / 篡改 signature）|
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **CONFLICT（I026 中立标，SoT vs 代码反向）** | **ISSUE-I026**：SoT §5 "`verifyWebhookSignature` 是孤儿代码" — **与代码反向**；`YouzanWebhookController:121` **已调用** `YouzanEcommerceAdapter.verifyWebhookSignature(pf.getSignSecret(), rawBody, signature)`；Fix Phase = Post-Audit 人类决策（3 选项：改 SoT / 改代码 / 维持现状） |
| Evidence | `audit/evidence/F071-youzan-webhook-signature/` | 待建（Phase 5+） |
| Issue | **I026** | |

---

### F072 — Ecommerce / Adapter / YouzanEcommerceAdapter.buildWebhookUrl（孤儿工具 BACKLOG-001，⚠ I033）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F072 | `feature-inventory.md` §1.3.2 row 17 |
| 前端路由 | — | （无前端入口；**无任何调用方**） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`YouzanEcommerceAdapter.buildWebhookUrl`** | **`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:106-114`**（`static String buildWebhookUrl(String baseUrl, String kdtId)`）|
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ✅ N/A | |
| Business Status | 构造 `https://{domain}/webhook/youzan?kdt_id={kdtId}`；**Phase 1 grep 全代码 0 匹配调用方**（仅声明 + 实现）；⚠ **BACKLOG-001**（沿用 I013 dead code 模式）— 用户决策接受 | `backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:106-114` |
| E2E Status | UNVERIFIED | Phase 7 Post-Audit 决定删除 / 保留 / 改造为注册工具 |
| Verified Commit | — | Phase 7+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **BACKLOG-001（孤儿工具）** | **ISSUE-I033**（沿用 I013 dead code 模式，SoT §5 描述"是孤儿工具"确认一致） |
| Evidence | `audit/evidence/F072-youzan-build-webhook-url/` | 待建（Phase 7+） |
| Issue | **I033** | |

---

### F073 — Ecommerce / Adapter / MockEcommerceAdapter `@ConditionalOnMissingBean` fallback

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F073 | `feature-inventory.md` §1.3.2 row 18 |
| 前端路由 | — | （无前端入口；F074 fallback） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`MockEcommerceAdapter`** | **`backend/src/main/java/com/meiyetec/ecommerce/MockEcommerceAdapter.java:18-78`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ✅ N/A | |
| Business Status | 3 样本商品（L21-25）+ 2 样本订单（L27-31）；`authenticate` 返回 `MOCK-TOKEN-{UUID}`；`sleep(50ms/80ms)` 模拟延迟；`healthCheck` 返回 `MOCK_OK`；**激活条件**：`@ConditionalOnMissingBean(name="youzanEcommerceAdapter")`（**注意 Bean 名是 youzanEcommerceAdapter** — 与 I012 模式同 Bean 条件冲突风险） | `backend/src/main/java/com/meiyetec/ecommerce/MockEcommerceAdapter.java:18-78` |
| E2E Status | UNVERIFIED | Phase 5 实测 Mock 降级 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | Bean 条件冲突风险（同 I012）；Mock 行为稳定 |
| Evidence | `audit/evidence/F073-mock-ecommerce-adapter/` | 待建（Phase 5+） |
| Issue | — | |

---

### F074 — Ecommerce / Router / EcommerceAdapterRouter platform 分发 + Mock fallback

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F074 | `feature-inventory.md` §1.3.2 row 19 |
| 前端路由 | — | （无前端入口；F079/F080 调用） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`EcommerceAdapterRouter.getAdapter/hasAdapter/listPlatforms`** | **`backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapterRouter.java:19-37`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ✅ N/A | |
| Business Status | 与 `CrmAdapterRouter` 同源设计；`platform` toUpperCase 查 Map；miss → fallback `getAdapter("MOCK")`；null → fallback；`listPlatforms` 返回 sorted List | `backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapterRouter.java:19-37` |
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | Mock fallback 完整（同 F018 I001 模式） |
| Evidence | `audit/evidence/F074-ecommerce-router/` | 待建（Phase 5+） |
| Issue | — | |

---

### F075 — Ecommerce / Service / 平台 CRUD + Health（4 端点）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F075 | `feature-inventory.md` §1.3.2 row 20 |
| 前端路由 | `/ecommerce` | `frontend/meiyetec-web/src/router/index.ts`（待 Phase 2 静态扫描补全）|
| 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | "平台管理" tab |
| UI板块 | listPlatforms el-table + 添加平台对话框 | `views/ecommerce/index.vue:1-95` + 第二 tab |
| Action | 点击「添加平台」→ 填 platform/shopName/shopId → 「保存」/ 点击「健康检查」| `views/ecommerce/index.vue:60-77` (对话框) |
| Handler | `doAddPlatform()` + `doHealthCheck()` | `views/ecommerce/index.vue` |
| API | `ecommerceApi.createPlatform/listPlatforms/updatePlatform` + (无 health API — 端点存在但前端未消费) | `frontend/meiyetec-web/src/api/ecommerce.ts:1-19` |
| Method | POST / GET | |
| Controller | **`EcommerceService.createPlatform/listPlatforms/updatePlatform/health`** | **`backend/src/main/java/com/meiyetec/service/EcommerceService.java:65-72` / `:75-80` / `:83-89` / `:92-94`** ⚠ I019 架构异味 |
| Service | 合并于 Controller | （I019 沿用） |
| Mapper | `EcommercePlatformMapper` | `mapper/EcommercePlatformMapper.java:5` |
| DB表 | `ecommerce_platform` | **MISSING（I029）** |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无 `@Transactional`（单条 INSERT 影响小）| |
| 权限 | **⚠ 不隔离**：`createPlatform` 默认 `tenantId=0L` (`:73`)（**同 Batch 1.1 I019 命名异味模式** — `EcommerceService` 是 `@RestController` 但文件名是 `*Service`）| `EcommerceService.java:73` |
| 软删除 | BaseEntity `deleted` 字段 | `entity/EcommercePlatform.java` |
| 审计字段 | `created_at` / `updated_at` | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| **DB Status** | **❌ MISSING（I029）** | |
| Business Status | `createPlatform` 强制 `platform` 必填；`tenantId=0L` 默认（**⚠ 跨租户共享 platform** — 同 I019）；`updatePlatform` 用 `setId(id) + updateById`；health 走 `router.getAdapter(platform).healthCheck(cfg)` (`:94`) | `backend/src/main/java/com/meiyetec/service/EcommerceService.java:62-94` |
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | `ecommerce_platform` MISSING | |
| Last Verified At | — | |
| **Final Result** | **MISSING（DB，I029）+ PARTIAL-NON-BLOCKER（I019 异味 + 跨租户缺失）** | **ISSUE-I029 / I019**（架构异味沿用 I019 模式）|
| Evidence | `audit/evidence/F075-ecommerce-platform-crud/` | 待建（Phase 5+） |
| Issue | **I029 / I019** | |

---

### F076 — Ecommerce / Service / 商品/订单 sync（4 端点 + upsert）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F076 | `feature-inventory.md` §1.3.2 row 21 |
| 前端路由 | `/ecommerce` | |
| 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | "商品同步" + "订单同步" tab |
| UI板块 | listProducts / listOrders el-table + "同步" 按钮 | `views/ecommerce/index.vue:1-95` 第三/四 tab |
| Action | 点击「同步」→ 调 `syncProducts` / `syncOrders` | `views/ecommerce/index.vue` |
| Handler | `doSyncProducts()` + `doSyncOrders()` | `views/ecommerce/index.vue` |
| API | `ecommerceApi.syncProducts/syncOrders/listProducts/listOrders` | `frontend/meiyetec-web/src/api/ecommerce.ts:1-19` |
| Method | POST / GET | |
| Controller | **`EcommerceService.listProducts/syncProducts/listOrders/syncOrders`** | **`backend/src/main/java/com/meiyetec/service/EcommerceService.java:97-105` / `:108-150` / `:213-222` / `:225-289`** + 私有 `upsertProduct` (`:152-200`) + 私有 `upsertOrder` (`:291-372`) ⚠ I019 架构异味 |
| Service | 合并于 Controller | （I019 沿用） |
| Mapper | `EcommerceProductMapper` + `EcommerceOrderMapper` + `EcommerceSyncLogMapper` | |
| DB表 | `ecommerce_product` + `ecommerce_order` + `ecommerce_sync_log` | 待 Phase 2 grep 验证 |
| 关联表 | `ecommerce_platform` (FK to platformId) | |
| 读/写 | RW | |
| 事务 | ✅ `@Transactional`（`:109` + `:227`）| |
| 权限 | 普通租户 | |
| 软删除 | BaseEntity `deleted` 字段 | |
| 审计字段 | `created_at` / `updated_at` + `last_sync_at`（sync 时更新）| |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ⚠ 待 Phase 2 grep 验证（多表） | |
| Business Status | upsert by `(platform, external_product_id/external_order_id)`；写入 `ecommerce_sync_log(syncType=PRODUCT/ORDER, status=RUNNING→SUCCESS/PARTIAL/FAILED, successCount/failCount/durationMs)`；`persistTokenCache` 仅刷 `accessToken/tokenExpireAt` 不覆盖其他字段；订单字段映射兼容有赞 + 微店（`receiver_*` / `buyer_*`） | `backend/src/main/java/com/meiyetec/service/EcommerceService.java:97-372` |
| E2E Status | UNVERIFIED | Phase 5 实测（用户决策 #1 Mock 降级） |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | 待 Phase 2 grep | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 多表依赖 + 字段映射复杂；真实链路 Phase 5 UNVERIFIED |
| Evidence | `audit/evidence/F076-ecommerce-sync/` | 待建（Phase 5+） |
| Issue | **I019**（架构异味沿用）| |

---

### F077 — Ecommerce / Webhook / YouzanWebhookController `/webhook/youzan`（⚠ CONFLICT I027）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F077 | `feature-inventory.md` §1.3.2 row 22 |
| 前端路由 | — | （无前端入口；有赞后台 push） |
| 页面 | — | |
| UI板块 | — | |
| Action | 有赞开发者后台推送 → POST `/webhook/youzan` | （push 端，无前端） |
| Handler | — | |
| API | `POST /webhook/youzan?kdt_id={kdtId}` | |
| Method | POST | |
| Controller | **`YouzanWebhookController.HandleYouzan`** | **`backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java:76-203`** `@RestController @PostMapping("/webhook/youzan") @Transactional` |
| Service | 合并于 Controller | |
| Mapper | `EcommercePlatformMapper` (read) + `WebhookMsgLogMapper` (write) + `EcommerceOrderMapper` (upsert) | |
| DB表 | `ecommerce_platform` (read, **MISSING I029**) + `webhook_msg_log` (write, **归 EXT-24 只引用**) + `ecommerce_order` (upsert, 待 Phase 2 grep) | |
| 关联表 | `ecommerce_platform.kdt_id` 匹配 | |
| 读/写 | RW | |
| 事务 | ✅ `@Transactional` **未指定 `rollbackFor`** (`:76`) — 与 **I009 模式同**（默认仅 RuntimeException 回滚）| **ISSUE-I034** |
| 权限 | **push 端无校验**（`@PostMapping` 无任何 `dataScope` / tenantId 校验 — push 来源是有赞后台）| `YouzanWebhookController.java:76` |
| 软删除 | — | |
| 审计字段 | `processed_at` + `created_at`（webhook_msg_log）| `entity/WebhookMsgLog.java` |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| **DB Status** | **❌ MISSING（I029 联动）** | `ecommerce_platform` MISSING；`webhook_msg_log` 归 EXT-24（**Batch 1.3 只引用不重复审计**）|
| Business Status | **完整处理流程**（L83-200）：① ContentCachingRequestWrapper 读 rawBody（L85-95）② 反查 platform by `kdt_id`(L107-114) ③ `verifyWebhookSignature` 验签(L121) ④ msg_id 幂等去重(L137-149) ⑤ upsert `ecommerce_order`(L182-186) ⑥ 写 `webhook_msg_log`(L195-200)；**SoT §5 描述"未实现"反向** — 见 **I027**；`@Transactional` **未指定 rollbackFor** — 见 **I034** | `backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java:76-203` |
| E2E Status | UNVERIFIED | Phase 5 Mock 推送样本 + jshell 单元测试 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | `ecommerce_platform` MISSING；`webhook_msg_log` 待 Phase 2 grep（EXT-24 边界） |
| Last Verified At | — | |
| **Final Result** | **CONFLICT（I027 中立标，SoT vs 代码反向）+ PARTIAL-NON-BLOCKER（I034 事务）+ MISSING（I029）** | **ISSUE-I027**（中立标）+ **I034**（rollbackFor）+ **I029**（DB 联动）|
| Evidence | `audit/evidence/F077-youzan-webhook-endpoint/` | 待建（Phase 5+） |
| Issue | **I027 / I034 / I029** | |

---

### F078 — Ecommerce / Webhook / msg_id 幂等去重（⚠ CONFLICT I030）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F078 | `feature-inventory.md` §1.3.2 row 23 |
| 前端路由 | — | （无前端入口；F077 链路） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | （F077 内部逻辑） |
| Method | — | |
| Controller | **`YouzanWebhookController.HandleYouzan` 内部** | **`backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java:136-149`** |
| Service | 合并于 Controller | |
| Mapper | `WebhookMsgLogMapper` | `mapper/WebhookMsgLogMapper.java:5` |
| DB表 | `webhook_msg_log` | **归 EXT-24（用户决策 #6）— Batch 1.3 只引用不重复审计** |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | 继承 F077 `@Transactional` | |
| 权限 | push 端无校验 | |
| 软删除 | — | |
| 审计字段 | `processed_at` + `created_at` | `entity/WebhookMsgLog.java` |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ⚠ 归 EXT-24 边界（Batch 1.3 不重审）| |
| Business Status | 查询 `selectOne(... eq(source, "YOUZAN") eq(msgId, msgId) limit 1)` (`:137-143`)；命中 → 返回"已处理（幂等）" + 既有 logId；**SoT §5 "没有 webhook 重放保护" 描述反向** — 见 **I030**；⚠ **未加重放 nonce 校验**（仅查 msg_id 不查 timestamp / nonce 防止过期重放）— 见 **I035** | `backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java:136-149` |
| E2E Status | UNVERIFIED | Phase 5 Mock 推送重复 msg_id 测试 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | `webhook_msg_log` 归 EXT-24（不重审）| |
| Last Verified At | — | |
| **Final Result** | **CONFLICT（I030 中立标，SoT vs 代码反向）+ PARTIAL-NON-BLOCKER（I035 重放）** | **ISSUE-I030**（中立标）+ **I035**（nonce 校验）|
| Evidence | `audit/evidence/F078-youzan-webhook-idempotent/` | 待建（Phase 5+） |
| Issue | **I030 / I035** | |

---

### F079 — Ecommerce / 前端 / 3 tab + 添加平台对话框（⚠ PARTIAL-NON-BLOCKER I028）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F079 | `feature-inventory.md` §1.3.2 row 24 |
| 前端路由 | `/ecommerce` | `frontend/meiyetec-web/src/router/index.ts`（待 Phase 2 静态扫描补全）|
| 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | （95 行，3 tab）|
| UI板块 | **3 tab**：平台管理 + 商品同步 + 订单同步；添加对话框（`platform: WEIDIAN/YOUZAN/TAOBAO/DOUYIN` + `shopName` + `shopId`）| `views/ecommerce/index.vue:1-95` + `:60-77` (对话框) |
| Action | tab 切换 + 表单填写 + 「保存」/「同步」按钮触发 | `views/ecommerce/index.vue` |
| Handler | `doAddPlatform()` + `doSyncProducts()` + `doSyncOrders()` | `views/ecommerce/index.vue` |
| API | `ecommerceApi.createPlatform/listPlatforms/updatePlatform/listProducts/syncProducts/listOrders/syncOrders/dashboard` | `frontend/meiyetec-web/src/api/ecommerce.ts:1-19` |
| Method | POST / GET | |
| Controller | **`EcommerceService` 10 端点** | **`backend/src/main/java/com/meiyetec/service/EcommerceService.java:62-372`** ⚠ I019 架构异味 |
| Service | 合并于 Controller | （I019 沿用） |
| Mapper | `EcommercePlatformMapper` + `EcommerceProductMapper` + `EcommerceOrderMapper` + `EcommerceSyncLogMapper` | |
| DB表 | 4 个 ecommerce 表（待 Phase 2 grep 验证）| |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | — | |
| 权限 | 普通租户 + tenantId 隔离（除 F075 `tenantId=0L`）| |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ⚠ 待 Phase 2 grep 验证（4 表）| |
| Business Status | 添加对话框（`platform: WEIDIAN/YOUZAN/TAOBAO/DOUYIN` + `shopName` + `shopId`）；⚠ **缺 `appId/kdtId/signSecret` 字段 UI** — 与 SoT §5 一致 — 见 **I028**；⚠ 缺 `clientId/clientSecret/webhookUrl` 等配置字段 | `frontend/meiyetec-web/src/views/ecommerce/index.vue:1-95` + `:60-77` |
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | 4 表待 Phase 2 grep | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER（I028 Frontend Shadow）** | **ISSUE-I028**（SoT §5 一致，缺字段 UI） |
| Evidence | `audit/evidence/F079-ecommerce-frontend/` | 待建（Phase 5+） |
| Issue | **I028** | |

---

### F080 — Frontend API + DB 迁移 / ecommerce.ts + google-maps.ts + EXT-17/22/24 DDL（⚠ MISSING I029）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F080 | `feature-inventory.md` §1.3.2 row 25 |
| 前端路由 | `/ecommerce` + `/google-maps` | |
| 页面 | — | （API 封装层 + DDL 迁移层）|
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | `ecommerceApi` 8 端点 + `googleMapsApi` 6 端点 | `frontend/meiyetec-web/src/api/ecommerce.ts:1-19` + `google-maps.ts:1-18` |
| Method | POST / GET | |
| Controller | 全部对应 `EcommerceService` + `GoogleMapsService` 端点 | |
| Service | — | |
| Mapper | — | |
| DB表 | `google_maps_config` + `google_maps_geocode_log` + `ecommerce_platform` + `webhook_msg_log` | DDL: `db/sql/migrate_ext_17_google_maps.sql` + `migrate_ext_22_google_maps_place_details.sql` + `migrate_ext_24_webhook_msg_log.sql`（EXT-24 边界引用）|
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| **DB Status** | **❌ MISSING（I029）** | **3 个迁移脚本 + dev DB 未跑迁移**（用户决策不跑迁移）|
| Business Status | `ecommerceApi` 8 端点（`createPlatform/listPlatforms/updatePlatform/listProducts/syncProducts/listOrders/syncOrders/dashboard`）；`googleMapsApi` 6 端点（`saveConfig/getConfig/geocodeStore/listStores/getPlaceDetails/getMemberHeatmap`）；DDL 三个迁移脚本 | `frontend/meiyetec-web/src/api/ecommerce.ts:1-19` + `google-maps.ts:1-18` + DDL `db/sql/migrate_ext_*.sql` |
| E2E Status | UNVERIFIED | Phase 5 用户决策 #1 Mock 降级 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | 3 张新表/列 MISSING（I029）；`webhook_msg_log` 归 EXT-24（不重审）| |
| Last Verified At | — | |
| **Final Result** | **MISSING（DB，I029）** | **ISSUE-I029** |
| Evidence | `audit/evidence/F080-frontend-api-and-ddl/` | 待建（Phase 5+） |
| Issue | **I029** | |

---