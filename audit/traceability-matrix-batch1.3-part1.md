---

## Batch 1.3 — EXT-22 Google Maps Place Details + 有赞（25 Features）

> **起点 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2 freeze commit，沿用 scope-freeze §六.4）
> **Feature 编号策略**：接续 Batch 1.2 的 F031-F055 → 本批次 **F056-F080（25 Features）**
> **EXT-* Operational Notes**：`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（**SoT**，**3 项与代码方向反转（I026/I027/I030 中立标 CONFLICT）+ 1 项表 MISSING（I029 不跑迁移）**）
> **User 决策 2026-09-13 22:50**：
> - I029 不跑迁移（与 I002/I017 模式一致，Phase 2-5 记录负向证据：预期 `TableNotFoundException` / column not found）；F056/F057/F060 → **MISSING**
> - I026/I027/I030 中立标 CONFLICT 接受（Phase 2 维持 CONFLICT 标签，不预设方向）
> - `buildWebhookUrl` → **BACKLOG-001**（I033，沿用 I013 模式）
> - `webhook_msg_log` → 标"归 EXT-24，只引用"（用户决策 #6）
> **Feature Inventory 来源**：`audit/feature-inventory.md` §1.3.2

### 受保护页面零风险（沿用 scope-freeze §九.8）

| 受保护路由 | 后端接口 | EXT-22 依赖 |
|---|---|---|
| `/dashboard` | `DashboardController`（不入 EXT-22 范围）| ❌ 无依赖 |
| `/mh/visual` | `VisualArchiveController`（不入 EXT-22 范围）| ❌ 无依赖 |
| `/mh/consent` | `ConsentController`（不入 EXT-22 范围）| ❌ 无依赖 |

**Phase 2 静态扫描确认**：EXT-22 端点（`/google-maps/*` + `/ecommerce/*` + `/webhook/youzan`）与 3 个受保护页面的后端接口**完全无间接依赖**（不同 Service 包 / 不同 Mapper / 不同 DB 表）。审计期间**不触达**受保护页面。

---

### F056 — Google Maps / 实体 / GoogleMapsConfig（含 `enablePlaceDetails`）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F056 | `feature-inventory.md` §1.3.2 row 1 |
| 前端路由 | `/google-maps` | （前端通过 F065 间接） |
| 页面 | — | (前端通过 API 间接) |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | `POST /google-maps/config` + `GET /google-maps/config` | F060 入口 |
| Method | POST / GET | |
| Controller | **`GoogleMapsService.saveConfig/getConfig`** | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:67-91` / `:94-110`** ⚠ I019 架构异味（`*Service` 命名实为 `@RestController`） |
| Service | 合并于 Controller | （I019 沿用） |
| Mapper | `GoogleMapsConfigMapper` | `mapper/GoogleMapsConfigMapper.java:5` |
| DB表 | `google_maps_config` | `db/sql/migrate_ext_17_google_maps.sql:9` (DROP + CREATE) + `db/sql/migrate_ext_22_google_maps_place_details.sql:6` (ALTER ADD enable_place_details) |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无 `@Transactional`（单条 INSERT 影响小） | |
| 权限 | 普通租户 + tenantId 隔离 | `GoogleMapsService.java:75` |
| 软删除 | BaseEntity `deleted` 字段 | `entity/GoogleMapsConfig.java` |
| 审计字段 | `created_at` / `updated_at`（auto-fill）| |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| **DB Status** | **❌ MISSING（I029）** | **`migrate_ext_17_google_maps.sql` 已写 + dev DB 未跑迁移**；用户决策 I029 不跑迁移 |
| Business Status | 11 字段；`apiKey` `JsonProperty.WRITE_ONLY`（`entity/GoogleMapsConfig.java:20`）；`enablePlaceDetails: Integer`（L43，1=启用计费 / 0=默认）；uk `uk_tenant_brand(tenant_id, brand_id, deleted)` | `entity/GoogleMapsConfig.java:11-56` |
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | `db-snapshot.sql` 未含 `google_maps_config`（待 Phase 2 grep 验证缺失）| |
| Last Verified At | — | |
| **Final Result** | **MISSING（DB）** | **ISSUE-I029**（I002/I017 模式，用户决策不跑迁移） |
| Evidence | `audit/evidence/F056-google-maps-config/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

### F057 — Google Maps / 实体 + 日志 / GoogleMapsGeocodeLog

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F057 | `feature-inventory.md` §1.3.2 row 2 |
| 前端路由 | `/google-maps` | （前端通过 F064 `/geocode/log` 间接） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | `GET /google-maps/geocode/log` | F064 入口 |
| Method | GET | |
| Controller | **`GoogleMapsService.geocodeLog`** | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:341-347`** |
| Service | 合并于 Controller | （I019 沿用） |
| Mapper | `GoogleMapsGeocodeLogMapper` | `mapper/GoogleMapsGeocodeLogMapper.java:5` |
| DB表 | `google_maps_geocode_log` | `db/sql/migrate_ext_17_google_maps.sql:40` (DROP + CREATE) |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无 `@Transactional`（单条 INSERT 影响小） | |
| 权限 | 普通租户 + tenantId 过滤 | `GoogleMapsService.java:345` |
| 软删除 | — | |
| 审计字段 | `request_at`（写入时间戳）| |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| **DB Status** | **❌ MISSING（I029）** | **`migrate_ext_17` 已写 + dev DB 未跑迁移** |
| Business Status | 11 字段（`tenantId/address/formattedAddress/lat/lng/placeId/status/errorMessage/durationMs/requestAt`）；每次 `/geocode` (`:159-170`) + `/geocode/batch` (`:225-237`) 调用写一行；try/catch 包裹写日志（失败仅 warn，不阻塞返回）| `entity/GoogleMapsGeocodeLog.java:13-38` + `GoogleMapsService.java:159-170/225-237` |
| E2E Status | UNVERIFIED | Phase 5 实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | `db-snapshot.sql` 未含 `google_maps_geocode_log`（待 Phase 2 grep 验证）| |
| Last Verified At | — | |
| **Final Result** | **MISSING（DB）** | **ISSUE-I029** |
| Evidence | `audit/evidence/F057-google-maps-geocode-log/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

### F058 — Google Maps / Client / Geocoding + Places HTTP（3 方法）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F058 | `feature-inventory.md` §1.3.2 row 3 |
| 前端路由 | — | （无前端入口；由 F060-F063 调用） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | （Client 层，不暴露 REST 端点） |
| Method | — | |
| Controller | — | |
| Service | **`GoogleMapsGeocodingClient`** | **`backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsGeocodingClient.java`**（381 行）|
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ✅ N/A | 无 DB 表 |
| Business Status | 三方法：`geocode(apiKey, address, language, region)` (`:97-145`) + `findPlace(apiKey, query, language)` (`:155-194`) + **`placeDetails(apiKey, placeId, language)`** (`:209-292`)；三 URL 常量：`GEOCODE_URL` / `PLACES_URL` / `PLACE_DETAILS_URL` (`:51-72`)；Place Details 一次性取 11 字段 → 始终命中 3 SKU（Basic+Contact+Atmosphere）— **PARTIAL-NON-BLOCKER**（SoT §5）；HTTP 客户端私有 `httpGet(url, connectTimeout=5000, readTimeout=15000)` (`:75-91`) | `backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsGeocodingClient.java` |
| E2E Status | UNVERIFIED | Phase 5 真实 GCP 调用（用户决策 #1 Mock 降级） |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 11 字段全拿 → 命中三 SKU（SoT §5 已知约束） |
| Evidence | `audit/evidence/F058-google-maps-client/` | 待建（Phase 5+） |
| Issue | — | |

---

### F059 — Google Maps / Config / Spring Bean 注册

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F059 | `feature-inventory.md` §1.3.2 row 4 |
| 前端路由 | — | （无前端入口；F058 依赖） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | **`GoogleMapsClientConfig`** | **`backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsClientConfig.java:13-16`** |
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
| Business Status | `@Configuration` + `@Bean googleMapsGeocodingClient(ObjectMapper jackson)`；注入 `ObjectMapper` 用于 JSON 解析；无 `@ConditionalOnMissingBean`（直接注册，无 fallback adapter）| `backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsClientConfig.java:13-16` |
| E2E Status | UNVERIFIED | Phase 5 实测启动 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 无 fallback adapter（GoogleMaps 无 SalesforceCRM 的 Mock fallback 模式） |
| Evidence | `audit/evidence/F059-google-maps-bean/` | 待建（Phase 5+） |
| Issue | — | |

---

### F060 — Google Maps / Service / `/config` + `/health`（⭐ MISSING）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F060 | `feature-inventory.md` §1.3.2 row 5 |
| 前端路由 | `/google-maps` | `frontend/meiyetec-web/src/router/index.ts`（待 Phase 2 静态扫描补全）|
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | "配置" tab |
| UI板块 | configForm（apiKey + defaultZoom + enablePlaceDetails el-switch）| `views/google-maps/index.vue:13-31` + `:35-50` |
| Action | 点击「保存配置」→ 调 `saveConfig` / 调 `loadConfig` 拉取 | `views/google-maps/index.vue:148-150` |
| Handler | `doSaveConfig()` + `loadConfig()` | `views/google-maps/index.vue:138-160` |
| API | `googleMapsApi.saveConfig(configForm)` + `googleMapsApi.getConfig()` | `frontend/meiyetec-web/src/api/google-maps.ts:1-18` |
| Method | POST / GET | |
| Controller | **`GoogleMapsService.saveConfig/getConfig/health`** | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:67-91` / `:94-110` / `:113-131`** ⚠ I019 架构异味 |
| Service | 合并于 Controller | （I019 沿用） |
| Mapper | `GoogleMapsConfigMapper` | `mapper/GoogleMapsConfigMapper.java:5` |
| DB表 | `google_maps_config` | **MISSING（I029）** |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | 普通租户 + tenantId 隔离 | `GoogleMapsService.java:75/98` |
| 软删除 | BaseEntity `deleted` 字段 | `entity/GoogleMapsConfig.java` |
| 审计字段 | `created_at` / `updated_at` | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| **DB Status** | **❌ MISSING（I029）** | dev DB 未跑 `migrate_ext_17` |
| Business Status | `POST /config` 强制 `apiKey` 必填 + 默认值（`defaultLanguage=zh-CN`/`defaultRegion=cn`/`quotaDaily=10000`/`enabled=1`/`enablePlaceDetails=0`）；`GET /config` 输出 `apiKey="***"+substring(0, length-4)`（**仅遮蔽后 4 位，39 字符 apiKey 仍可猜前 35 位** — SoT §5 一致，**用户决策 #9 仅标注**）；`/health` 用 `geocode("Beijing")` 写回 `lastHealthAt/lastHealthMsg` (`:113-131`) | `backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:67-131` |
| E2E Status | UNVERIFIED | Phase 5 用户决策 #1 Mock 降级 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | `db-snapshot.sql` 未含 `google_maps_config` | |
| Last Verified At | — | |
| **Final Result** | **MISSING（DB）+ PARTIAL-NON-BLOCKER（API Key 泄露）** | **ISSUE-I029**（DB 缺失）+ SoT §5 API Key 泄露（用户决策 #9 仅标注）|
| Evidence | `audit/evidence/F060-google-maps-config-endpoint/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

### F061 — Google Maps / Service / `/geocode` + mock 降级 + 日志

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F061 | `feature-inventory.md` §1.3.2 row 6 |
| 前端路由 | `/google-maps` | |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | "地理编码测试" tab |
| UI板块 | geocodeForm（address + language）| `views/google-maps/index.vue` 第三 tab |
| Action | 输入地址 → 点击「查询」→ 调 `geocodeStore` | |
| Handler | `doGeocode()` | |
| API | `googleMapsApi.geocodeStore(payload)` | `frontend/meiyetec-web/src/api/google-maps.ts` |
| Method | POST | |
| Controller | **`GoogleMapsService.geocode`** | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:138-180`** |
| Service | 合并于 Controller | （I019 沿用） |
| Mapper | `GoogleMapsConfigMapper` (read) + `GoogleMapsGeocodeLogMapper` (write) | `mapper/GoogleMapsConfigMapper.java:5` + `mapper/GoogleMapsGeocodeLogMapper.java:5` |
| DB表 | `google_maps_config` (read, **MISSING**) + `google_maps_geocode_log` (write, **MISSING**)| |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无 `@Transactional`（写日志 try/catch 包裹）| |
| 权限 | 普通租户 + tenantId 隔离 | `GoogleMapsService.java:144` |
| 软删除 | — | |
| 审计字段 | `request_at`（日志写入时间）| |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| **DB Status** | **❌ MISSING（I029 联动）**| 两表均未跑迁移 |
| Business Status | 三门禁：① `address` 必填 ② cfg 存在 + apiKey 非空 → 真实链路（`geocodingClient.geocode`）③ 否则 mock 降级（`mockGeocode` L243-253 用 `30+nextDouble()*10` 模拟经纬度）；每次写日志；返回 `source: GOOGLE_MAPS/MOCK` | `backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:138-180` + `:243-253` |
| E2E Status | UNVERIFIED | Phase 5 Mock 降级实测 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | 两表 MISSING | |
| Last Verified At | — | |
| **Final Result** | **MISSING（DB，I029 联动）** | **ISSUE-I029** |
| Evidence | `audit/evidence/F061-google-maps-geocode/` | 待建（Phase 5+） |
| Issue | **I029**（联动）| |

---

### F062 — Google Maps / Service / `/place/details` 计费闸门（EXT-22 ⭐）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F062 | `feature-inventory.md` §1.3.2 row 7 |
| 前端路由 | `/google-maps` | |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | "Place Details 测试" tab |
| UI板块 | placeDetailsForm（placeId + language）+ 计费警告 el-alert | `views/google-maps/index.vue:13-31` (el-alert) + 第四 tab |
| Action | 输入 placeId → 点击「查询」→ 调 `getPlaceDetails` | |
| Handler | `doPlaceDetails()` | |
| API | `googleMapsApi.getPlaceDetails(payload)` | `frontend/meiyetec-web/src/api/google-maps.ts` |
| Method | POST | |
| Controller | **`GoogleMapsService.placeDetails`** | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:191-232`** |
| Service | 合并于 Controller | （I019 沿用） |
| Mapper | `GoogleMapsConfigMapper` (read) | `mapper/GoogleMapsConfigMapper.java:5` |
| DB表 | `google_maps_config` (read, **MISSING**) | （**注意：当前实现未写 `google_maps_geocode_log`** — 仅 `/geocode` 写日志）|
| 关联表 | — | |
| 读/写 | R（**未写日志 — I031**）| |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | 普通租户 + tenantId 隔离 | `GoogleMapsService.java:201` |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| **DB Status** | **❌ MISSING（I029）** | |
| Business Status | **三段门禁**（EXT-22 §0.3 ⭐）：① `placeId` 必填 → 否则 `BAD_REQUEST`；② cfg 存在 + apiKey 非空 → 否则 `NOT_FOUND`；③ **`enablePlaceDetails == 1` → 否则 `PERMISSION_DENIED`**（**唯一计费闸门** — SoT §0.3 Step 2）；调用 `geocodingClient.placeDetails(apiKey, placeId, lang)` (`:225-228`)；返回 11 fields (`:229-230`) | `backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:191-232` |
| E2E Status | UNVERIFIED | Phase 5 用户决策 #1 Mock 降级 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | `google_maps_config` MISSING | |
| Last Verified At | — | |
| **Final Result** | **MISSING（DB）+ PARTIAL-NON-BLOCKER（I031 未写日志）**| **ISSUE-I029**（DB）+ **I031**（审计缺失，未写 geocode_log）|
| Evidence | `audit/evidence/F062-google-maps-place-details/` | 待建（Phase 5+） |
| Issue | **I029 / I031** | |

---