### F063 — Google Maps / Service / `/geocode/batch` admin-only 批量解析

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F063 | `feature-inventory.md` §1.3.2 row 8 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | `POST /google-maps/geocode/batch` | |
| Method | POST | |
| Controller | **`GoogleMapsService.geocodeBatch`** | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:259-318`** |
| Service | 合并于 Controller | （I019 沿用） |
| Mapper | `GoogleMapsConfigMapper` (read) + `GoogleMapsGeocodeLogMapper` (write) | |
| DB表 | `google_maps_config` (read, **MISSING**) + `google_maps_geocode_log` (write, **MISSING**)| |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | **admin-only**：`SUPER_ADMIN/PLATFORM_ADMIN/HQ_ADMIN` 可调（否则 `PERMISSION_DENIED`）(`:266-274`) | `GoogleMapsService.java:266-274` |
| 软删除 | — | |
| 审计字段 | `request_at`（日志写入时间）| |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| **DB Status** | **❌ MISSING（I029）** | |
| Business Status | 单次最多 50 条（`:279`）；复用 `/geocode` 配置 + mock 降级逻辑；响应 `{total, ok, fail, durationMs, source, results[]}` (`:314-317`)；**⚠ PARTIAL-NON-BLOCKER：admin-only 但无 audit log**（SoT §5 "批量 geocode 无 audit log" 已知问题 — 见 **I032**） | `backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:259-318` |
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | 两表 MISSING | |
| Last Verified At | — | |
| **Final Result** | **MISSING（DB）+ PARTIAL-NON-BLOCKER（I032 审计缺失）**| **ISSUE-I029 / I032** |
| Evidence | `audit/evidence/F063-google-maps-batch/` | 待建（Phase 5+） |
| Issue | **I029 / I032** | |

---

### F064 — Google Maps / Service / `/stores` + `/heatmap` + `/geocode/log` 演示与审计

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F064 | `feature-inventory.md` §1.3.2 row 9 |
| 前端路由 | `/google-maps` | |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | "门店地图" tab |
| UI板块 | listStores table + heatmap visualization | `views/google-maps/index.vue` 第二 tab |
| Action | 点击 tab → 自动调 `listStores` / `getMemberHeatmap` / `geocode/log` | |
| Handler | `loadStores()` + `loadHeatmap()` + `loadGeocodeLog()` | `views/google-maps/index.vue` |
| API | `googleMapsApi.listStores()` + `getMemberHeatmap()` + (无前端 geocode/log API) | `frontend/meiyetec-web/src/api/google-maps.ts:1-18` |
| Method | GET | |
| Controller | **`GoogleMapsService.listStores/getMemberHeatmap/geocodeLog`** | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:320-322` / `:325-338` / `:341-347`** |
| Service | 合并于 Controller | （I019 沿用） |
| Mapper | `GoogleMapsGeocodeLogMapper` (read only for `/geocode/log`) | |
| DB表 | `google_maps_geocode_log` (read, **MISSING**) | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | 普通租户 + tenantId 隔离（`/geocode/log` 按 tenantId 过滤）(`:344`) | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| **DB Status** | **❌ MISSING（I029 联动）** | |
| Business Status | 演示数据：5 门店（北京三里屯/上海徐汇/深圳南山/纽约/东京，`STORES` 常量 L262-279）；heat map 200 点用 random 偏移 + weight (`:326-337`)；`/geocode/log` 按 `tenantId` 过滤 + `id DESC LIMIT 100` (`:344-346`) | `backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:320-347` |
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | `google_maps_geocode_log` MISSING | |
| Last Verified At | — | |
| **Final Result** | **MISSING（DB，I029 联动）** | **ISSUE-I029** |
| Evidence | `audit/evidence/F064-google-maps-demo/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

### F065 — Google Maps / 前端 / Google Maps 配置 + Place Details 测试 UI

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F065 | `feature-inventory.md` §1.3.2 row 10 |
| 前端路由 | `/google-maps` | `frontend/meiyetec-web/src/router/index.ts`（待 Phase 2 静态扫描补全）|
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | （175 行，4 tab）|
| UI板块 | **4 tab**：配置 / 门店地图 / 地理编码测试 / Place Details 测试；`enablePlaceDetails` el-switch + 计费警告 el-alert | `views/google-maps/index.vue:1-175` + `:13-31` (el-alert + switch) |
| Action | tab 切换 + 表单填写 + 按钮触发 | `views/google-maps/index.vue:138-160` (saveConfig) |
| Handler | `doSaveConfig()` + `doGeocode()` + `doPlaceDetails()` + `loadConfig()` | `views/google-maps/index.vue:138-175` |
| API | `googleMapsApi.saveConfig/getConfig/geocodeStore/listStores/getPlaceDetails/getMemberHeatmap` | `frontend/meiyetec-web/src/api/google-maps.ts:1-18` |
| Method | POST / GET | |
| Controller | **`GoogleMapsService` 6 端点** | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:67-348`** ⚠ I019 架构异味 |
| Service | 合并于 Controller | （I019 沿用） |
| Mapper | `GoogleMapsConfigMapper` + `GoogleMapsGeocodeLogMapper` | |
| DB表 | `google_maps_config` (read/write, **MISSING**) + `google_maps_geocode_log` (read/write, **MISSING**) | |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | **❌ MISSING（I029 联动）** | |
| Business Status | **与 SoT §5 "前端缺 UI" 描述部分反向**（`enablePlaceDetails` UI 已实现 — 见 **I028 部分反向**）；⚠ **configForm 仅含 apiKey + defaultZoom + enablePlaceDetails**，**不传** `defaultLanguage/defaultRegion/quotaDaily/enabled`（更新时这些字段被覆盖为 null/默认值 — 用户决策 #3 阶段已确认）| `frontend/meiyetec-web/src/views/google-maps/index.vue:13-50` |
| E2E Status | UNVERIFIED | Phase 5 实测（用户决策 #1 Mock 降级） |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | 两表 MISSING | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER（I028 部分反向）+ Shadow**| **ISSUE-I029**（DB 联动）+ **I028**（前端 UI 部分反向，SoT §5 描述需更新）|
| Evidence | `audit/evidence/F065-google-maps-frontend/` | 待建（Phase 5+） |
| Issue | **I028 / I029** | |

---

### F066 — Ecommerce / 实体 / EcommercePlatform（含 `appId/kdtId/signSecret` 三新字段）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F066 | `feature-inventory.md` §1.3.2 row 11 |
| 前端路由 | — | （前端通过 F079/F080 间接） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | — | |
| Mapper | `EcommercePlatformMapper` | `mapper/EcommercePlatformMapper.java:5` |
| DB表 | `ecommerce_platform` | `db/sql/create_missing_tables_b4b5.sql:372` (CREATE IF NOT EXISTS base 表) + `db/sql/migrate_ext_22_google_maps_place_details.sql:11` (ALTER 3 列) |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无 `@Transactional`（单条 INSERT） | |
| 权限 | — | |
| 软删除 | BaseEntity `deleted` 字段 | `entity/EcommercePlatform.java` |
| 审计字段 | `created_at` / `updated_at` | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| **DB Status** | **❌ MISSING（I029）** | **base 表 DDL 在 `create_missing_tables_b4b5.sql:372` + 3 新列 ALTER 在 `migrate_ext_22:11`；dev DB 未跑迁移** |
| Business Status | 26 字段，**新增 3 字段**（L19/L21/L23）：`appId`(EXT-22) + `kdtId`(EXT-22) + `signSecret`(EXT-22)；**注意：`appKey` ≠ `appId`**（OAuth2 client_id vs 有赞 AppID，SoT §0.4 强调）；`signSecret` 用于 webhook 验签；⚠ `tenantId=0L` 默认（`EcommerceService.createPlatform` L73 — 跨租户共享 platform）| `entity/EcommercePlatform.java:11-41` |
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | `ecommerce_platform` MISSING（base 表 + 3 新列） | |
| Last Verified At | — | |
| **Final Result** | **MISSING（DB，I029 联动）** | **ISSUE-I029** |
| Evidence | `audit/evidence/F066-ecommerce-platform-entity/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

### F067 — Ecommerce / 实体 / EcommerceOrder + EcommerceProduct + EcommerceSyncLog 三件套

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F067 | `feature-inventory.md` §1.3.2 row 12 |
| 前端路由 | — | （前端通过 F080 间接） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | — | |
| Mapper | `EcommerceOrderMapper` + `EcommerceProductMapper` + `EcommerceSyncLogMapper` | |
| DB表 | `ecommerce_order` + `ecommerce_product` + `ecommerce_sync_log` | `db/sql/create_missing_tables_b4b5.sql:411` (ecommerce_order) + others |
| 关联表 | `ecommerce_platform` (FK to platformId) | |
| 读/写 | RW | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | BaseEntity `deleted` 字段 | |
| 审计字段 | `created_at` / `updated_at` | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ✅ 表已设计（待 Phase 2 grep db-snapshot 确认） | |
| Business Status | `ecommerce_order` 19 字段含 `externalOrderId/orderNo/buyerName/buyerPhone/amount/shippingFee/totalAmount/address/trackingNo/itemsJson/rawPayload/orderTime`；`ecommerce_sync_log` 9 字段含 `successCount/failCount/durationMs/status/errorMessage`；共用于 F076 同步链路 + F077 webhook upsert | `entity/EcommerceOrder.java:11-44` + `EcommerceSyncLog.java:11-28` |
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | 待 Phase 2 grep 验证 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 三表均依赖 dev DB 落地；与 F066 共享 `create_missing_tables_b4b5.sql` 跑批 |
| Evidence | `audit/evidence/F067-ecommerce-entities/` | 待建（Phase 5+） |
| Issue | — | |

---

### F068 — Ecommerce / Adapter / EcommerceAdapter 接口契约

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F068 | `feature-inventory.md` §1.3.2 row 13 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`EcommerceAdapter` 接口** | **`backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapter.java`**（122 行，5 方法 + 4 DTO）|
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
| DB Status | ✅ N/A | 无 DB 表 |
| Business Status | 5 方法（`platformCode/authenticate/fetchProducts/fetchOrders/healthCheck`）+ 4 DTO（`AuthResult/ProductPage/OrderPage/HealthStatus`）；与 `CrmAdapter` 同源设计；屏蔽有赞/微店/淘宝/Mock 实现差异；Runtime 由 Spring 注入指定实现（`YouzanEcommerceAdapter`/`MockEcommerceAdapter`）| `backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapter.java` |
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 仅接口；具体实现见 F069/F070/F073 |
| Evidence | `audit/evidence/F068-ecommerce-adapter-contract/` | 待建（Phase 5+） |
| Issue | — | |

---

### F069 — Ecommerce / Adapter / YouzanEcommerceAdapter.authenticate（OAuth2 + Token 缓存）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F069 | `feature-inventory.md` §1.3.2 row 14 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | （Adapter 层）|
| Method | — | |
| Controller | — | |
| Service | **`YouzanEcommerceAdapter.authenticate`** | **`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:170-211`** |
| Mapper | — | |
| DB表 | `ecommerce_platform`（Token 缓存写回 accessToken/tokenExpireAt，**MISSING**）| |
| 关联表 | — | |
| 读/写 | RW（写 Token 缓存）| |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | **❌ MISSING（I029 联动）** | `ecommerce_platform` 未跑迁移 |
| Business Status | OAuth2 Client-Credentials：`POST /api/oauthentry/grant/token` body=`client_id={appKey}&client_secret={appSecret}&grant_type=client_credential`；缓存失效重签发（`tokenExpireAt > now()+2min` 直接复用）；`buildTokenRequestBody` (L59-63) + `DEFAULT_API_BASE` 常量；HTTP POST 5000ms connect / 15000ms read | `backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:170-211` + `:59-63` |
| E2E Status | UNVERIFIED | Phase 5 用户决策 #1 Mock 降级 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | `ecommerce_platform` MISSING | |
| Last Verified At | — | |
| **Final Result** | **MISSING（DB，I029 联动）** | **ISSUE-I029** |
| Evidence | `audit/evidence/F069-youzan-authenticate/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

### F070 — Ecommerce / Adapter / YouzanEcommerceAdapter 商品/订单拉取 + 健康检查

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F070 | `feature-inventory.md` §1.3.2 row 15 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | （Adapter 层）|
| Method | — | |
| Controller | — | |
| Service | **`YouzanEcommerceAdapter.fetchProducts/fetchOrders/healthCheck`** | **`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:213-244` / `:248-284` / `:286-303`** |
| Mapper | — | |
| DB表 | — | （Adapter 层不直接写 DB；F076 sync 链路写 `ecommerce_sync_log`）|
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ✅ N/A | |
| Business Status | 商品：`GET /items/list?page_no=1&page_size=50&access_token=...`；订单：`GET /trades/sold_get?start_update_time=...&end_update_time=...`；pageSize 上限 100；**响应总条数 = `root.total_results`**；`buildItemsListUrl` (L66-71) + `buildTradesSoldGetUrl` (L74-83) | `backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:213-303` |
| E2E Status | UNVERIFIED | Phase 5 用户决策 #1 Mock 降级 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 仅 adapter 静态可达；真实链路 Phase 5 UNVERIFIED |
| Evidence | `audit/evidence/F070-youzan-products-orders/` | 待建（Phase 5+） |
| Issue | — | |

---