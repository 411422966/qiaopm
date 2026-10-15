# Phase 4 — Runtime Verification Plan（Batch 1.3）

> **创建时间**：2026-09-13 23:55 (UTC+8)
> **Phase**：4 — Runtime Verification Plan
> **范围**：Batch 1.3 — EXT-22 Google Maps + 有赞（25 个 Feature：F056-F080）
> **Batch 决策回顾**（沿用 D11-D20 @ 23:50 + D1-D5 @ preflight §十 + D6-D10 @ Batch 1.2 沿用）：
> - **D11**：不改 SoT（I026 / I027 / I030 维持 CONFLICT 中立标，沿用 I001 / I018 模式）
> - **D12**：不加 UI（I028 登记 Issue，Phase 5 仅观察）
> - **D13**：不跑迁移（I029 维持 MISSING，沿用 Batch 1.1 D5 + Batch 1.2 D7 + I002 + I017 模式）
> - **D14**：不加日志（I031 登记 Issue）
> - **D15**：不加 audit log（I032 登记 backlog）
> - **D16**：保持 backlog（I033 BACKLOG-001 沿用 I013 dead code 模式）
> - **D17**：不加 rollbackFor（I034 登记 Issue，沿用 I009 模式）
> - **D18**：不加时间窗（I035 登记 Issue，I030 幂等已实现，时间窗为可选增强）
> - **D19**：仅标注不改（I036 GCP API Key 泄露，沿用 user decision #9）
> - **D20**：不加 UI（F063 / F064 / F075 Shadow 维持 PARTIAL-NON-BLOCKER）
> - **D10 验证铁律（沿用 Batch 1.2）**：Phase 5 跑不通 → 降级 UNVERIFIED，**不造证据**
> - **受保护页面干扰**：EXT-22 端点与 `/dashboard` / `/mh/visual` / `/mh/consent` 后端接口零交集 → 不触及（scope-freeze §九.8 已确认）
> - **规则**：审计阶段不修改业务代码（规则 6）→ Phase 4 仅记录验证策略，不进 Phase 5 执行

---

## 〇、运行时验证总览

### 0.1 验证工具矩阵

| 工具 | 适用 | 状态 |
|---|---|---|
| **curl / PowerShell `Invoke-RestMethod`** | API-only 验证 | ✅ 可用（Phase 5 用户跑）|
| **MySQL CLI / MySQL MCP** | DB-only 验证 | ✅ 可用（按 preflight §8.3 配置）|
| **JVM `mvn clean package` + 后端启动** | 后端真实启动验证 | ⚠️ **Cursor 不能跑**（需 JVM + Spring Boot）→ 降级方案见 §0.2 |
| **jshell** | 静态方法调用（签名算法）| ✅ 可用（验证 `verifyWebhookSignature`）|
| **Playwright MCP** | UI E2E | ❌ **未配置** → 降级方案 |
| **手动浏览器（用户执行）**| UI E2E 降级 | ✅ Phase 5 用户跑 Playwright 脚本 |
| **GCP Places API 真实调用** | Place Details 真实链路 | ❌ **凭据缺失**（user decision #1）→ Mock 降级 |
| **有赞 webhook 真实推送** | webhook 真实链路 | ❌ **push_url 未注册**（user decision #2）→ Mock 推送样本 |

### 0.2 EXT-22 降级方案（GCP / 有赞真实链路不可达）

对任何标「真实链路验证」的 Feature（F062 / F068-F070 / F071 / F077），Phase 5 降级为：

1. **Cursor 静态验证**（已完成于 Phase 3）：代码路径、catch 位置、表/类引用全部 L0 可达
2. **用户手动跑**：
   - 启动后端 → 看 `[GOOGLE-MAPS]` + `[ECOMMERCE]` + `[YOUZAN-WEBHOOK]` 启动日志
   - curl `/google-maps/health` 验证 Bean 注册
   - curl `/google-maps/config` 验证配置读取（含 I036 泄露验证）
   - curl `/ecommerce/platform/list` 验证平台 CRUD
   - jshell 调用 `YouzanEcommerceAdapter.verifyWebhookSignature` 验证签名算法（3 组 payload）
   - Mock 推送 webhook 样本（生成有赞 payload + 正确 signature 头部）→ curl `POST /webhook/youzan?kdt_id=test`
3. **结果贴回** → `audit/evidence/FXXX-<slug>/result.md`（用户记录 pass/fail + 截图）
4. **若 GCP / 有赞真实链路跑不通**（凭据缺失 / push_url 未注册）→ **直接降级 UNVERIFIED，不造证据**（D10 铁律）

### 0.3 验证方式 vs Feature 类型映射

| Feature 类型 | 主验证 | 辅验证 |
|---|---|---|
| **GCP Places API 真实链路（F062）** | 用户手动（启动后端 + curl）| jshell 静态调用 + MySQL DESCRIBE |
| **GCP 计费闸门（F062）** | 用户手动（dev DB `enable_place_details=0` → PERMISSION_DENIED）| DB-only（DESCRIBE google_maps_config）|
| **GCP 批量 geocode（F063）** | admin-only + curl `/geocode/batch` | DB-only（逐条写 google_maps_geocode_log）|
| **GCP 日志（F057 / F062）** | MySQL MCP 验证 `google_maps_geocode_log` 表存在性 + 字段对齐 | L0 静态可达 |
| **有赞 OAuth2 token（F069 / F070）** | Mock 降级（user decision #1）| Static-Only（`EcommerceAdapterRouter` fallback）|
| **有赞签名算法（F071）** | **jshell 单元测试**（3 组 payload）| Static-Only（md5 算法 + equalsIgnoreCase）|
| **有赞 webhook 接收端（F077）** | **Mock 推送样本**（生成 payload + 正确 signature 头）| DB-only（webhook_msg_log 幂等去重）|
| **有赞 webhook 幂等去重（F078）** | Mock 推送样本重复 2 次 | DB-only（webhook_msg_log + ecommerce_order）|
| **`buildWebhookUrl` 孤儿工具（F072）** | **Static-Only**（grep 0 调用方）| — |
| **EcommercePlatform CRUD（F075）** | API-only + DB-only | Mock-INTENTIONAL（C4 `tenantId=0L` 强制）|
| **Ecommerce Sync（F076）** | API-only + DB-only | `EcommerceService.@Transactional` 默认 rollbackFor |
| **Entity / DDL / 配置（F056 / F057 / F066 / F080）** | DB-only（DESCRIBE）| Static-Only |
| **Frontend UI（F065 / F079 / F080）** | Playwright E2E（降级）| Static-Only（缺字段 UI 验证）|
| **CONFLICT（I026 / I027 / I030）** | Phase 5 **只读不判** | 静态 SoT 比对完成 |
| **MISSING（F056 / F057 / F060 / F066 / F080）** | DB-only（DESCRIBE → 表缺失）| Static-Only（DDL 已写）|

### 0.4 优先级规则

| 优先级 | 适用 Feature 类型 | Phase 5 必测 |
|---|---|---|
| **P0** | webhook 接收端 + 签名算法 + DB MISSING 实证 + 幂等去重 | ✅ |
| **P1** | GCP 计费闸门 + Place Details + EcommercePlatform CRUD + 前端缺字段 UI | ✅ |
| **P2** | 批量 geocode + 日志 + OAuth2 token + Ecommerce Sync + Entity + 配置 + 前端 + 孤儿工具 | ⏸️ 降级后非强制 |

---

## 一、P0 — webhook 接收端 + 签名算法 + DB MISSING + 幂等去重（7 个 Feature）

| Feature ID | Priority | 验证方式 | 是否需 E2E | 是否需 DB 断言 | 理由 |
|---|---|---|---|---|---|
| **F056** GoogleMapsConfig 实体 | **P0** | **DB-only** | ✗ | ✓ | `entity/GoogleMapsConfig.java:11-56` 21 字段含 `enablePlaceDetails (L43, Integer)`。**MISSING（I029）**。**用户手动**：`mysql -e "DESCRIBE google_maps_config"` → **预期错误** `Table 'meiyetec.google_maps_config' doesn't exist`（I029 MISSING 实证）。DB 断言：① `SHOW TABLES LIKE 'google_maps_config'` → 0 行；② 对照 DDL `migrate_ext_17_google_maps.sql:9-39` 21 字段；③ ALTER `migrate_ext_22_google_maps_place_details.sql:6` 加 `enable_place_details` → 22 字段 |
| **F057** GoogleMapsGeocodeLog 实体 | **P0** | **DB-only** | ✗ | ✓ | `entity/GoogleMapsGeocodeLog.java:13-38` 11 字段。**MISSING（I029）**。**用户手动**：`mysql -e "DESCRIBE google_maps_geocode_log"` → **预期错误** `Table 'meiyetec.google_maps_geocode_log' doesn't exist`（I029 MISSING 实证）。DB 断言：① `SHOW TABLES LIKE 'google_maps_geocode_log'` → 0 行；② 对照 DDL `migrate_ext_17_google_maps.sql:40-53` |
| **F060** GoogleMapsService `/config` + `/health` | **P0** | **API-only + DB-only** | ✗ | ✓ | `GoogleMapsService.java:67-131` `/config` + `/health`。**MISSING（I029，表）+ I036 Key 泄露 + C1 configForm 缺字段**。**用户手动**：① 启动后端 → `curl /google-maps/health` → 应返回 `{enabled, apiKey, ...}`（即使 I029 缺失，后端启动可能受 I010 Flyway 影响 → 验证 I010 处置）；② `curl /google-maps/config` → 响应应含 `"apiKey": "***xxxx"`（最后 4 位明文 + 前 35 位省略，**I036 实证**）。DB 断言：① `DESCRIBE google_maps_config` → I029 预期不存在；② `SELECT * FROM google_maps_config WHERE tenant_id=?` → 异常（表不存在） |
| **F066** EcommercePlatform 实体 | **P0** | **DB-only** | ✗ | ✓ | `entity/EcommercePlatform.java:11-41` 21 字段含 `appId (L19) + kdtId (L21) + signSecret (L23)`。**MISSING（I029，表 3 新列）+ I028 字段漂移确认**。**用户手动**：`mysql -e "DESCRIBE ecommerce_platform"` → **预期错误** `Table 'meiyetec.ecommerce_platform' doesn't exist`（I029 MISSING 实证）。DB 断言：① `SHOW TABLES LIKE 'ecommerce_platform'` → 0 行；② 对照 DDL `create_missing_tables_b4b5.sql:372`（21 字段）+ ALTER `migrate_ext_22_google_maps_place_details.sql:11` 加 3 列 |
| **F071** `verifyWebhookSignature` 签名算法 | **P0** | **jshell 单元测试** | ✗ | ✗ | `YouzanEcommerceAdapter.java:146-168` `public static boolean verifyWebhookSignature(String signSecret, String rawBody, String signature)` — md5(signSecret + rawBody) 全小写 hex + equalsIgnoreCase。**CONFLICT（I026 中立标，SoT 描述反孤儿 — 已被 `YouzanWebhookController:121` 调用）**。**用户手动 jshell**：① 正常签名 `signSecret="test123" + rawBody='{"id":"x"}'` + signature=正确 md5 → 预期 `true`；② 篡改 body → 预期 `false`；③ 篡改 signature → 预期 `false`；④ 边界 case `signSecret=null` → 预期 `false` |
| **F077** `POST /webhook/youzan` 接收端 | **P0** | **Mock 推送样本 + API-only + DB** | ✗ | ✓ | `YouzanWebhookController.java:76-203` `HandleYouzan` `@PostMapping("/webhook/youzan") @Transactional` **完整实现**：ContentCachingRequestWrapper L85-95 + 反查 platform L107-114 + 验签 L121 + msg_id 幂等 L136-149 + upsert L182-186 + 写 log L195-200。**CONFLICT（I027 + I030 中立标，SoT 描述反缺失 / 反重放保护缺失）**。**用户手动 Mock 推送**：① 生成有赞 webhook payload `{"msg_id":"test-001","status":"TRADE_ORDER_STATE_UPDATE","order_no":"TEST-001",...}` + 计算正确 signature 头 `X-Youzan-Sign`；② `curl -X POST "http://localhost:8080/webhook/youzan?kdt_id=test-kdt" -H "X-Youzan-Sign: <正确签名>" -d '{"msg_id":"test-001",...}'` → 预期 `200 + {status:"PROCESSED"}`；③ 同 payload 重复推送 → 预期 `200 + duplicate=true + previousStatus=PROCESSED`（F078 幂等去重） |
| **F080** Frontend API + DB 迁移 | **P0** | **DB-only + Static-Only** | ✗ | ✗ | `api/google-maps.ts:1-18` + `api/ecommerce.ts:1-19` + `migrate_ext_22_google_maps_place_details.sql:6/11`。**MISSING（I029，DB 迁移未跑）**。**用户手动**：① `grep google_maps_config db-snapshot.sql` → 0 命中（I029 实证）；② `grep enable_place_details db-snapshot.sql` → 0 命中；③ `grep ecommerce_platform db-snapshot.sql` → 0 命中；④ `grep app_id db-snapshot.sql` → 0 命中；⑤ `grep kdt_id db-snapshot.sql` → 0 命中；⑥ `grep sign_secret db-snapshot.sql` → 0 命中。**Post-Audit（D13）**：跑迁移前由 DBA 决策 |

---

## 二、P1 — GCP 计费闸门 + Place Details + EcommercePlatform CRUD + 前端缺字段（9 个 Feature）

| Feature ID | Priority | 验证方式 | 是否需 E2E | 是否需 DB 断言 | 理由 |
|---|---|---|---|---|---|
| **F058** GoogleMapsGeocodingClient | **P1** | **Static-Only + 用户手动** | ✗ | ✗ | `googlemaps/GoogleMapsGeocodingClient.java:51-381` 11 字段 Place Details 全拿（Basic+Contact+Atmosphere 三 SKU）+ 私有 `httpGet` 连接 5s + 读 15s。L0 静态可达。**用户手动**：启动后端 → 看 `[GOOGLE-MAPS]` 日志 → 验证 Bean 注册。**预期 UNVERIFIED 维持**（GCP 真实调用受凭据缺失限制） |
| **F059** GoogleMapsClientConfig | **P1** | **Static-Only + 用户手动** | ✗ | ✗ | `googlemaps/GoogleMapsClientConfig.java:13-16` `@Bean googleMapsGeocodingClient(ObjectMapper jackson)` — 无 `@ConditionalOnMissingBean`（无 fallback）。L0 静态可达。**用户手动**：启动后端 → 看 `[GOOGLE-MAPS]` Bean 加载日志 → 验证 `googleMapsGeocodingClient` 注册成功。**预期 UNVERIFIED 维持** |
| **F062** GoogleMapsService `/place/details` | **P1** | **用户手动 + DB-only** | ✗ | ✓ | `GoogleMapsService.java:191-232` `placeDetails` 三段门禁（tenantId + enabled=1 + apiKey 非空 → 否则 NOT_FOUND；`enablePlaceDetails=1` → 否则 PERMISSION_DENIED；调用 geocodingClient.placeDetails）。**PARTIAL-NON-BLOCKER（I031 未写日志 + C1 configForm 缺字段）**。**用户手动**：① dev DB `enable_place_details=0`（默认） → `curl POST /google-maps/place/details -d '{"placeId":"test"}'` → 预期 `PERMISSION_DENIED`（**计费闸门实证** — 不调 Google，无 GCP 计费）；② `enable_place_details=1` → 同请求 → 预期 `NOT_FOUND`（apiKey 为空）；③ 注入真实 apiKey + placeId → 预期 `200 + PlaceDetailResult`（**UNVERIFIED**，凭据缺失由用户决策 #7 评估）。DB 断言：`SELECT * FROM google_maps_geocode_log WHERE place_id=?` → **预期无新增行**（**I031 实证** — Place Details 未写日志） |
| **F063** GoogleMapsService `/geocode/batch` | **P1** | **用户手动 + API-only + DB** | ✗ | ✓ | `GoogleMapsService.java:259-318` `geocodeBatch` admin-only 最多 50 条。**PARTIAL-NON-BLOCKER（I032 无独立 audit log 表）+ 真实 Shadow（无前端）**。**用户手动**：admin token `curl POST /google-maps/geocode/batch -d '["北京","上海",...]'` → 预期 `200 + [{lat,lng,formattedAddress,...}, ...]`（mock 降级）；查 `google_maps_geocode_log` 应**逐条**新增（每条 address 一行）；查 `geocode_batch_log` → **预期表不存在**（**I032 实证** — 无独立 audit log） |
| **F064** GoogleMapsService `/stores` + `/heatmap` + `/geocode/log` | **P1** | **API-only** | ✗ | ✗ | `GoogleMapsService.java:320-348` 3 端点。**PARTIAL-NON-BLOCKER（真实 Shadow，`/geocode/log` 端点无前端消费）**。**用户手动**：① `curl GET /google-maps/stores` → 预期 `200 + 5 门店常量`（STORES L33-43）；② `curl GET /google-maps/heatmap` → 预期 `200 + heatmap 数据`（mock 降级）；③ `curl GET /google-maps/geocode/log` → 预期 `200 + 日志列表`（**真实 Shadow**：后端有，前端无 — 沿用 I006 / I007 模式） |
| **F065** google-maps/index.vue | **P1** | **Playwright E2E 降级** | ✓ | ✗ | `views/google-maps/index.vue:1-175` 4 tab：配置 / 门店地图 / 地理编码测试 / Place Details 测试。**PARTIAL-NON-BLOCKER（I028 部分反向 + C1 configForm 缺字段）**。**用户手动**：① 打开 `/google-maps` 页面 → 应有 4 tab；② `enablePlaceDetails` el-switch L13-31 已实现 → 切换开关 → 验证是否实际保存到后端（curl `saveConfig` + `getConfig` — **I028 实证**，user decision 接受现状）；③ `configForm` 仅 3 字段 L139 → 验证缺 `defaultLanguage/defaultRegion/quotaDaily/enabled`（**C1 实证**）。**降级**：Playwright MCP 未配置 → 用户本地跑 Playwright 脚本 |
| **F067** EcommerceOrder + EcommerceProduct + EcommerceSyncLog | **P1** | **DB-only + Static-Only** | ✗ | ✓ | `entity/EcommerceOrder.java` + `entity/EcommerceProduct.java` + `entity/EcommerceSyncLog.java`。L0 静态可达 + Phase 5 DB DESCRIBE 验证。**用户手动**：`mysql -e "DESCRIBE ecommerce_order; DESCRIBE ecommerce_product; DESCRIBE ecommerce_sync_log"` → 三表应分别存在（`create_missing_tables_b4b5.sql:411` 创建 ecommerce_order + EXT-16 范围）。**预期**：ecommerce_order / ecommerce_product 已存在；ecommerce_sync_log 可能 MISSING（**Phase 1 期间确认**） |
| **F069** YouzanEcommerceAdapter `authenticate` | **P1** | **用户手动 + Mock 降级** | ✗ | ✗ | `YouzanEcommerceAdapter.java:187-279` OAuth2 Client-Credentials + Token 缓存（`tokenExpireAt > now()+2min`）。**用户手动**：Mock 降级（user decision #1）→ `curl GET /ecommerce/platform/{id}/health` → 预期 `200 + {platform, status, ...}`（Mock fallback）。**预期 UNVERIFIED 维持**（真实 OAuth2 凭据缺失） |
| **F075** EcommerceService `/platform` + `/platform/{id}/health` | **P1** | **API-only + DB** | ✗ | ✓ | `EcommerceService.java:65-94` `createPlatform` `tenantId=0L` 强制 + `:82-89` `updatePlatform` 无租户隔离（C4 MOCK-INTENTIONAL）+ `/platform/{id}/health` 真实 Shadow（无前端）。**PARTIAL-NON-BLOCKER + MOCK-INTENTIONAL（C4）**。**用户手动**：① `curl POST /ecommerce/platform -d '{platform:"YOUZAN",shopName:"test",shopId:"test-kdt"}'` → 查 DB → `ecommerce_platform` 应有 1 行（**预期 I029 MISSING 暴露**：表不存在 → 500）；② `curl GET /ecommerce/platform/{id}/health` → 预期 `200 + health status`（**真实 Shadow**：后端有，前端无 — D20 不加 UI） |

---

## 三、P2 — Controller REST + 前端 + Entity + 配置 + 孤儿工具（9 个 Feature）

| Feature ID | Priority | 验证方式 | 是否需 E2E | 是否需 DB 断言 | 理由 |
|---|---|---|---|---|---|
| **F061** GoogleMapsService `/geocode` | **P2** | **用户手动 + Mock 降级** | ✗ | ✗ | `GoogleMapsService.java:138-180` `geocodeStore` + mockGeocode 降级路径 L243-253。**MOCK-INTENTIONAL**（user decision #1 Mock 降级一致）。**用户手动**：① 无 apiKey 配置 → `curl POST /google-maps/geocode -d '{"address":"北京"}'` → 预期 `200 + {lat: 30-40 范围, lng: 110-130 范围}`（**mockGeocode 实证**）；② 注入真实 apiKey → 预期 `200 + 真实响应`（**UNVERIFIED**，凭据缺失由用户决策 #7 评估） |
| **F068** EcommerceAdapter 接口 | **P2** | **Static-Only** | ✗ | ✗ | `ecommerce/EcommerceAdapter.java` 全文 122 行 — 5 方法 + 4 DTO（AuthResult / ProductPage / OrderPage / HealthStatus）。L0 静态可达 + 接口契约 OK。**预期 UNVERIFIED 维持**（接口契约本身通过 F069 / F070 / F071 间接覆盖） |
| **F070** YouzanEcommerceAdapter `fetchProducts` + `fetchOrders` + `healthCheck` | **P2** | **用户手动 + Mock 降级** | ✗ | ✗ | `YouzanEcommerceAdapter.java:187-279` 3 业务方法。**用户手动**：Mock 降级（user decision #1）→ `curl GET /ecommerce/platform/{id}/health` → 预期 `200 + health status`。**预期 UNVERIFIED 维持**（真实 API 调用凭据缺失） |
| **F073** MockEcommerceAdapter | **P2** | **用户手动 + Static-Only** | ✗ | ✗ | `ecommerce/MockEcommerceAdapter.java:18-77` `@ConditionalOnMissingBean(name="youzanEcommerceAdapter")` L21 + 3 商品 + 2 订单样本。**MOCK-INTENTIONAL**（user decision #1 Mock 降级）。**用户手动**：关闭 Mock → 验证降级路径；启动后端 → `curl GET /ecommerce/platform/list` → 应返回 Mock 数据 |
| **F074** EcommerceAdapterRouter | **P2** | **Static-Only + 用户手动** | ✗ | ✗ | `ecommerce/EcommerceAdapterRouter.java:21-46` platform → adapter；miss → MOCK fallback；与 `CrmAdapterRouter` 同源设计。**MOCK-INTENTIONAL**。**用户手动**：① `curl GET /ecommerce/platform/{id}/health` → 预期 200；② 关闭 `YouzanEcommerceAdapter` Bean → 验证 Mock fallback 路径 |
| **F076** EcommerceService `/product` + `/order sync` | **P2** | **API-only + DB** | ✗ | ✓ | `EcommerceService.java:97-150 (syncProducts)` + `:213-289 (syncOrders)` `@Transactional` 默认 rollbackFor 仅 RuntimeException。**PARTIAL-NON-BLOCKER（I034 关联 + C10 token 缓存弱一致）**。**用户手动**：① `curl POST /ecommerce/product/sync/{pid}` → 预期 `200 + {syncCount, ...}`（Mock 降级）；② `curl POST /ecommerce/order/sync/{pid}` → 同；查 `ecommerce_product` / `ecommerce_order` 应有新行（**预期 I029 MISSING 暴露**：表不存在 → 500） |
| **F078** webhook msg_id 幂等去重 | **P2** | **Mock 推送样本 + DB** | ✗ | ✓ | `YouzanWebhookController.java:136-149` `webhookMsgLogMapper.selectOne(... eq(source, "YOUZAN") eq(msgId, msgId) limit 1)` + `:195-200` 写 webhook_msg_log。**CONFLICT（I030 中立标，SoT 反重放保护缺失）+ PARTIAL-NON-BLOCKER（I035 时间窗缺失）+ 真实 Shadow（无运维查询接口）**。**用户手动 Mock 推送**（与 F077 同 payload）：① 首次推送 → 期望 `200 + status=PROCESSED`；② 同 payload 重复推送 → 期望 `200 + duplicate=true + previousStatus=PROCESSED`；③ 查 `webhook_msg_log` → 应有 2 行（同 msg_id，第 2 行 `status=FAILED` 或 `duplicate=true` 标记）。**I030 实证**：重复 msg_id 应被识别 |
| **F079** ecommerce/index.vue | **P2** | **Playwright E2E 降级** | ✓ | ✗ | `views/ecommerce/index.vue:1-95` 3 tab：平台管理 / 商品同步 / 订单同步。添加对话框 L60-77 **仅 3 字段**（platform/shopName/shopId）— **确认缺** `appId/kdtId/signSecret/appKey/appSecret`（I028 关联）。表内无 update 按钮（**真实 Shadow**，F075 关联）。**PARTIAL-NON-BLOCKER**。**用户手动**：① 打开 `/ecommerce` 页面 → 应有 3 tab（**真实 Shadow：缺 update 按钮** — D20 不加 UI）；② 添加平台对话框 → 验证仅 3 字段（**I028 实证**，user decision 接受现状）；③ 提交 → 验证缺 loading 态。**降级**：Playwright MCP 未配置 → 用户本地跑 Playwright 脚本 |
| **F072** YouzanEcommerceAdapter `buildWebhookUrl` | **P2** | **Static-Only** | ✗ | ✗ | `YouzanEcommerceAdapter.java:106-114` `buildWebhookUrl` 定义 + 全代码库 grep 0 真实调用。**BACKLOG-001**（I033 沿用 I013 dead code 模式）。**用户手动**：① `grep -r "buildWebhookUrl" backend/` → 仅声明 + 实现，无任何调用方；② Phase 7 Post-Audit 决定删除 / 保留 / 改造 |

---

## 四、预期负向证据清单（I026-I036 实证预期）

> 按 Phase 3 静态分析结论 + D11-D20 决策，下列负向证据是 Phase 5 必暴露的（验证审计结论准确性）：

| # | 验证目标 | 预期负向证据 | Phase 5 操作 | Issue 关联 |
|---|---|---|---|---|
| **N1** | `google_maps_config` 表不存在 | `TableNotFoundException` 或 `Table 'meiyetec.google_maps_config' doesn't exist` | `mysql -e "DESCRIBE google_maps_config"` | **I029**（F056 / F060） |
| **N2** | `google_maps_geocode_log` 表不存在 | `TableNotFoundException` 或 `Table 'meiyetec.google_maps_geocode_log' doesn't exist` | `mysql -e "DESCRIBE google_maps_geocode_log"` | **I029**（F057） |
| **N3** | `ecommerce_platform` 表不存在 | `TableNotFoundException` 或 `Table 'meiyetec.ecommerce_platform' doesn't exist` | `mysql -e "DESCRIBE ecommerce_platform"` | **I029**（F066） |
| **N4** | DB snapshot 未含 EXT-22 迁移 | `grep` 0 命中 `google_maps_config` / `enable_place_details` / `app_id` / `kdt_id` / `sign_secret` | `grep google_maps_config db-snapshot.sql` 等 | **I029**（F080） |
| **N5** | GCP API Key `getConfig` 端点泄露 | 响应 `"apiKey": "***xxxx"`（仅遮蔽后 4 位） | `curl GET /google-maps/config` → 看 `apiKey` 字段 | **I036**（F060） |
| **N6** | GCP 计费闸门（`enable_place_details=0`） | `POST /google-maps/place/details` → `PERMISSION_DENIED`（不调 Google，无计费） | 启动后端 → `curl POST /google-maps/place/details -d '{"placeId":"test"}'` | **I031**（F062） |
| **N7** | Place Details 未写日志 | 调 `/place/details` → 查 `google_maps_geocode_log WHERE place_id=?` → **无新增行** | 启动后端 → curl `/place/details` → 查 log 表 | **I031**（F062） |
| **N8** | 批量 geocode 无独立 audit log 表 | 查 `geocode_batch_log` → **表不存在** | `mysql -e "SHOW TABLES LIKE 'geocode_batch_log'"` → 0 行 | **I032**（F063） |
| **N9** | webhook 验签算法 md5（`verifyWebhookSignature`） | jshell 3 组 payload：① 正常签名 → true；② 篡改 body → false；③ 篡改 signature → false | jshell 直接调用静态方法 | **I026**（F071） |
| **N10** | webhook 接收端完整实现 | Mock 推送正确签名 payload → `200 + status=PROCESSED` + 订单入库 | Mock 推送样本 + curl `POST /webhook/youzan` | **I027**（F077） |
| **N11** | webhook msg_id 幂等去重 | 同 payload 重复推送 → `200 + duplicate=true + previousStatus=PROCESSED` | Mock 推送样本（重复 2 次） | **I030**（F078） |
| **N12** | `webhook_msg_log` 表归属 EXT-24（Batch 1.3 只引用）| `mysql -e "DESCRIBE webhook_msg_log"` → 表应存在（EXT-24 迁移）| 验证表存在性（不重复审计）| **I027** + I030（同源） |
| **N13** | `buildWebhookUrl` 孤儿工具 | `grep buildWebhookUrl backend/src/main` → 仅声明 + 实现，无任何调用方 | grep 全代码库 | **I033**（F072） |
| **N14** | webhook 事务 rollbackFor 缺失 | mock 推送触发 `upsertOrder` 失败 → 查 `webhook_msg_log.status` 应为 `FAILED`（事务回滚 OK，RuntimeException） | 实证 + 不验证 checked exception 路径（D17 不加 rollbackFor） | **I034**（F077） |
| **N15** | webhook 幂等时间窗缺失 | 实证：构造同一 msg_id + 篡改 body（绕过签名需额外攻击）→ 验证签名机制已防重放 | 同 N11，签名机制层验证 | **I035**（F078） |
| **N16** | 前端缺字段 UI（`appId/kdtId/signSecret`）| `views/ecommerce/index.vue:60-77` 添加对话框仅 3 字段（**I028 实证**） | 静态 grep 或 Playwright 验证 | **I028**（F079） |
| **N17** | 前端缺 platform 列（`/geocode/log` 真实 Shadow）| `views/google-maps/index.vue` 无 `/geocode/log` tab | 静态 grep 或 Playwright 验证 | **I024 模式**（F064） |
| **N18** | EcommercePlatform `tenantId=0L` 强制（C4）| `EcommerceService.java:69` `p.setTenantId(0L)` | 静态 grep | **C4**（F075） |
| **N19** | `updatePlatform` 无租户隔离（C4）| `EcommerceService.java:82-89` 无 tenant 校验 | 静态 grep | **C4**（F075） |
| **N20** | upsertOrder 重复实现（C5）| `YouzanWebhookController.java:206-358` vs `EcommerceService.java:241-353` 重复 | 静态 diff | **C5**（F077） |

---

## 五、需要用户手动执行的清单（EXT-22 相关）

> Cursor 不能跑 JVM 启动后端 / 不能连接生产 GCP / 不能注册有赞 push_url → 下列 Feature 在 Phase 5 必须由用户在本地执行。

### 5.1 P0 必跑（DB MISSING + webhook 链路）

| Feature | 用户手动步骤 | 失败降级 |
|---|---|---|
| **F056 / F057** GoogleMaps 表 MISSING | ① `mysql -e "DESCRIBE google_maps_config"` → 表缺失；② `DESCRIBE google_maps_geocode_log` → 表缺失 | 表存在 → I029 不成立 |
| **F060** GoogleMaps `/config` + `/health` | ① 启动后端 → ② `curl GET /google-maps/health` → ③ `curl GET /google-maps/config` → 看 `apiKey` 字段（N5）| 启动失败 → F060 UNVERIFIED |
| **F066** EcommercePlatform 实体 MISSING | ① `mysql -e "DESCRIBE ecommerce_platform"` → 表缺失；② 检查 21 字段 + 3 新列是否预期缺失 | 表存在 → I029 不成立 |
| **F071** `verifyWebhookSignature` 算法 | jshell 4 组 payload（N9） | 任一 case 失败 → F071 PARTIAL-NON-BLOCKER |
| **F077** webhook 接收端 | Mock 推送正确签名 payload → `curl POST /webhook/youzan?kdt_id=test` → 期望 200 + status=PROCESSED + 订单入库 | 500 / 验签失败 → F077 CONFLICT 不成立，降级 PARTIAL-NON-BLOCKER |
| **F080** DB 迁移 MISSING | `grep google_maps_config db-snapshot.sql` 等 5 个 grep → 0 命中（N4）| >0 命中 → I029 不成立 |

### 5.2 P1 必跑（GCP 计费 + EcommercePlatform CRUD）

| Feature | 用户手动步骤 | 失败降级 |
|---|---|---|
| **F058 / F059** GoogleMaps Client + Config | 启动后端 → 看 Bean 加载日志 | 启动失败 → UNVERIFIED |
| **F062** Place Details 计费闸门 | ① dev DB `enable_place_details=0` → `curl POST /google-maps/place/details` → 期望 PERMISSION_DENIED（N6）；② 查 `google_maps_geocode_log` 无新增行（N7） | 不返回 PERMISSION_DENIED → F062 PARTIAL-BLOCKER |
| **F063** 批量 geocode | admin token `curl POST /google-maps/geocode/batch` → 查 `geocode_batch_log` 表不存在（N8） | 表存在 → I032 不成立 |
| **F064** GoogleMaps 3 端点 | `curl GET /google-maps/stores` + `/heatmap` + `/geocode/log` 全部 200 | 任一 500 → F064 UNVERIFIED |
| **F065** google-maps/index.vue | Playwright 跑 F065 脚本 → 验证 4 tab + enablePlaceDetails 开关实际保存 | 脚本失败 → F065 UNVERIFIED |
| **F067** Ecommerce 实体 | `mysql -e "DESCRIBE ecommerce_order; DESCRIBE ecommerce_product; DESCRIBE ecommerce_sync_log"` | 任一缺失 → F067 PARTIAL-BLOCKER |
| **F069** Youzan OAuth2 | Mock 降级 → `curl GET /ecommerce/platform/{id}/health` → 200 | 500 → F069 UNVERIFIED |
| **F075** EcommercePlatform CRUD | `curl POST /ecommerce/platform` → 查 DB → 期望 I029 MISSING 暴露 500 | 200 → I029 不成立 |

### 5.3 P2 部分必跑（依赖 Mock 降级 + 静态）

| Feature | 用户手动步骤 | 失败降级 |
|---|---|---|
| **F061** mockGeocode 降级 | 无 apiKey 配置 → `curl POST /google-maps/geocode -d '{"address":"北京"}'` → 期望 30-40/110-130 mock 范围 | 真实响应 → F061 不属于 MOCK-INTENTIONAL |
| **F068** EcommerceAdapter 接口 | L0 静态可达，Phase 5 不验证 | — |
| **F070** Youzan 业务方法 | Mock 降级 → `curl GET /ecommerce/platform/{id}/health` → 200 | 500 → F070 UNVERIFIED |
| **F072** buildWebhookUrl 孤儿 | `grep buildWebhookUrl backend/src/main` → 0 调用方（N13）| >0 调用方 → I033 不成立 |
| **F073 / F074** Mock + Router | 关闭 `YouzanEcommerceAdapter` Bean → 验证 Mock fallback | 启动失败 → F073 / F074 UNVERIFIED |
| **F076** Ecommerce Sync | `curl POST /ecommerce/product/sync/{pid}` + `/order/sync/{pid}` → 期望 200 或 I029 MISSING 暴露 500 | 500 → F076 PARTIAL-BLOCKER |
| **F078** webhook 幂等去重 | Mock 推送同 payload 重复 2 次 → 期望 200 + duplicate=true（N11） | 重复未识别 → F078 CONFLICT 不成立，降级 PARTIAL-NON-BLOCKER |
| **F079** ecommerce/index.vue | Playwright 跑 F079 脚本 → 验证 3 tab + 添加对话框仅 3 字段（N16） | 脚本失败 → F079 UNVERIFIED |

---

## 六、Phase 4 → Phase 5 入口守门

| 检查项 | 状态 | 备注 |
|---|---|---|
| 25 Feature Phase 4 计划已落 | ✅ | 本文件 |
| P0/P1/P2 优先级已分布 | ✅ | P0=7 / P1=9 / P2=9 |
| 验证方式已指定（API-only / DB-only / jshell / Mock 推送 / Static-Only）| ✅ | 每 Feature 1 个主方式 |
| 预期负向证据清单已建（N1-N20）| ✅ | 关联 I026-I036 + C1-C10 |
| 用户手动执行清单已建（5.1/5.2/5.3）| ✅ | 含失败降级 UNVERIFIED 策略 |
| D11-D20 决策已落实 | ✅ | 全部统一为「审计阶段只记录不修复」 |
| I026-I036 决策已落档到 issue-register.md §十九 | ✅ | 11 项 Issue Fix Phase = Post-Audit |
| Feature 矩阵唯一归属修正已落档（static-gap-report.md §二十六）| ✅ | F061/F077 单一归属 + F080 加入 MISSING 计数（5 项）|
| 不动代码 | ✅ | 仅 Phase 4 计划生成 |
| 不跑迁移 | ✅ | D13 决策：维持 MISSING，不跑迁移 |
| 不动 SoT | ✅ | D11 决策：3 项 CONFLICT 维持中立标 |
| 不加 UI / 不加日志 / 不加 audit log / 不加 rollbackFor / 不加时间窗 | ✅ | D12 / D14 / D15 / D17 / D18 决策 |
| 不进 Phase 5 | ✅ | 等用户确认本计划 → Phase 5 执行 |
| 跳过 Phase 3.5 | ✅ | Batch 1.3 非重构批次 |
| 受保护页面零风险 | ✅ | EXT-22 与 `/dashboard` / `/mh/visual` / `/mh/consent` 后端接口零交集 |

---

## 七、Phase 4 → Phase 5 衔接说明

> 本文件仅生成 **验证策略**，不执行任何运行时操作。
>
> **Phase 5 启动条件**（按用户决策）：
> 1. 用户确认本计划（P0/P1/P2 分布 + D11-D20 落实）
> 2. 用户启动后端（Spring Boot + MySQL + google_maps_config / google_maps_geocode_log / ecommerce_platform 表**未创建**——按 I029 D13 决策）
> 3. 用户跑 curl + mysql CLI + jshell 验证
> 4. 用户跑 Playwright 脚本（前端 E2E 降级）
> 5. 用户跑 Mock 推送 webhook 样本（验证 F077 / F078）
> 6. GCP / 有赞真实链路凭据缺失 → **UNVERIFIED**（D10 铁律）
>
> **Phase 5 输出物**：
> - `audit/evidence/F056-F080/<slug>/result.md`（每个 Feature 1 份）
> - `audit/runtime/report-5A-batch-1.3.md` + `report-5B-batch-1.3.md`（汇总 25 Feature Phase 5 结果 + 负向证据清单）
> - Issue Register 更新（I026-I036 实证状态）
>
> **失败降级铁律（D10）**：
> - GCP 凭据缺失 / 有赞 push_url 未注册 → F062 / F068-F070 / F071 / F077 全部 UNVERIFIED
> - 后端启动失败（Flyway 编码 I010 处置后仍失败）→ F056-F080 全部 UNVERIFIED
> - 任何 Phase 5 操作**不造证据**（不写假 result.md）

---

## 八、附录 — Batch 1.3 vs Batch 1.1 / 1.2 Phase 4 对照

| 维度 | Batch 1.1 (CRM) | Batch 1.2 (Plugin Runtime) | **Batch 1.3 (EXT-22)** |
|---|---|---|---|
| Feature 数 | 30 | 25 | **25** |
| P0 必测 | 7 | 6 | **7** |
| P1 | 15 | 5 | **9** |
| P2 | 8 | 14 | **9** |
| pf4j 加载 | N/A | **Cursor 不能跑** → 用户手动 | N/A |
| GCP 真实链路 | N/A | N/A | **凭据缺失** → Mock 降级 |
| 有赞真实推送 | N/A | N/A | **push_url 未注册** → Mock 推送样本 |
| Frontend E2E | Playwright E2E 降级 | 同 | 同 |
| DB 缺表 | `crm_contact_map`（F023）+ `crm_integration` 缺 9 列（I002）| `plugin_runtime_state`（F049）+ 9 vs 8 字段差异（F051）| **`google_maps_config` / `google_maps_geocode_log` / `ecommerce_platform` 3 张表全 MISSING（I029，F056/F057/F060/F066/F080）**|
| 核心决策 | D5 不跑 migration | **D7 不跑 migration**（同模式）| **D13 不跑 migration**（沿用 D5/D7 + I002/I017）|
| SoT 漂移 | I001 HubSpot/Zoho 误标"未实现" | **I018 assertTenantAccess 误标"无校验"** | **I026 / I027 / I030 三项 EXT-22 SoT 描述反向（中立标沿用 I001/I018）**|
| 合作式限制 | N/A | **I020 沙箱不拦截 service.\*** | N/A |
| 副作用 | N/A | **I022 disable 清 lastError** | **I035 webhook 幂等无时间窗**（I030 幂等已实现）|
| 幽灵记录 | N/A | **I023 install 不 load → NPE** | N/A |
| 命名异味 | I004 Service as Controller | **I019 PluginService as Controller**（同模式）| **EcommerceService / GoogleMapsService `@RestController` 同模式**（未登记新 Issue，沿用 I004/I019）|
| DB 缺列 API 暴露 | N/A | N/A | **I036 GCP API Key `getConfig` 端点泄露** |
| 孤儿工具 | N/A | **I025 deniedPackages** | **I033 buildWebhookUrl**（同模式）|
| 加 rollbackFor | N/A | N/A | **I034 webhook 事务未指定 rollbackFor**（沿用 I009）|

> **沿用经验**：
> - Batch 1.1 D5「不跑 migration 保留 PARTIAL-BLOCKER」 → Batch 1.2 D7 同模式 → **Batch 1.3 D13 同模式**
> - Batch 1.1 I001「保留 CONFLICT 不动 .mdc」 → Batch 1.2 I018 同模式 → **Batch 1.3 D11 同模式（3 项 CONFLICT）**
> - Batch 1.1 04-script false Shadow → Batch 1.2 已用 04 重跑验证真 Shadow 数 = 10 → **Batch 1.3 同（17 + 后端全量）**
> - Batch 1.1 受保护页面零风险 → Batch 1.2 同 → **Batch 1.3 同（EXT-22 与 3 受保护页面零交集）**

---

**Batch 1.3 Phase 4 完成。25 Feature 验证计划已落档 + 20 项预期负向证据已建 + 11 项 Issue（I026-I036）维持 Post-Audit 修复。⏸ 等用户确认本计划 → 启动 Phase 5（运行时实证）。**
