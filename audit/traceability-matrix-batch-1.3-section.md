---

## Batch 1.3 — EXT-22 Google Maps Place Details + 有赞（25 Features）

> **范围来源**：`audit/baseline/scope-freeze-batch-1.3.md` §2.1 + `audit/feature-inventory.md` §1.3
> **EXT-* Operational Notes**：`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（SoT，5 项 SoT vs 代码漂移 + 1 项表 MISSING — 见 §本批 Issue 清单）
> **Phase 0 freeze commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2 终点）
> **Batch 起点**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（同 freeze commit，本批次未独立 commit）
> **Phase 1 产出**：`audit/feature-inventory.md` §1.3（F056-F080 完整清单）
> **Issue 决策**：用户 2026-09-13 22:45 决策 — **I029 不跑迁移**（沿用 I002/I017 模式：审计阶段不改 DB），I026/I027/I030 **中立标 CONFLICT** 接受
> **审计原则**：规则 6（只记录不修复）+ 漂移分类原则（DDL 写了 + 迁移未跑 = I002/I017/I029 MISSING 模式；SoT vs 代码 = I026/I027/I030 CONFLICT 模式）

### 受保护页面零风险（沿用 scope-freeze §九.7）

| 检查项 | 结果 | 来源 |
|---|---|---|
| EXT-22 是否影响 `/dashboard` 数据？ | ❌ 无直接影响（Google Maps Config + Geocode 独立模块） | preflight §五.3 |
| EXT-22 是否影响 `/mh/visual` / `/mh/consent`？ | ❌ 无直接影响 | 同上 |
| `YouzanWebhookController` / `GoogleMapsService` / `EcommerceService` 是否触及受保护页面后端接口？ | ❌ 不触及（`/google-maps/*` / `/ecommerce/*` / `/webhook/youzan` 与 `/dashboard` / `/mh/*` 无交集） | 同上 |

> ⚠ **EXT-22 端点列表**（`/google-maps/*` + `/ecommerce/*` + `/webhook/youzan`）：本批次**不调用任何受保护页面后端接口**。Phase 2 不需要为此特殊标注。

### EXT-22 Phase 2 静态验证（I029 MISSING 模式 — 用户决策 2026-09-13 22:45）

| 表 | DDL 位置 | 实体类 | dev DB 实际状态 | Phase 2 标记 |
|---|---|---|---|---|
| `google_maps_config` | `db/sql/migrate_ext_17_google_maps.sql:9-26` + `migrate_ext_22_google_maps_place_details.sql:9-11` | `entity/GoogleMapsConfig.java:11-56` | ⚠ **DDL 已写但未跑迁移**（沿用 I002 模式 — 用户决策：审计阶段不改 DB）| **MISSING**（I029）|
| `google_maps_geocode_log` | `db/sql/migrate_ext_17_google_maps.sql:40-53` | `entity/GoogleMapsGeocodeLog.java:13-38` | ⚠ 同上 | **MISSING**（I029）|
| `ecommerce_platform` | `create_missing_tables_b4b5.sql:372` + `migrate_ext_22_google_maps_place_details.sql:11-13` | `entity/EcommercePlatform.java:11-41` | ⚠ 同上 | **MISSING**（I029）|
| `webhook_msg_log` | `db/sql/migrate_ext_24_webhook_msg_log.sql:8-24` | `entity/WebhookMsgLog.java` | ⚠ 同上（**归 EXT-24 — Batch 1.3 只引用不重复审计** — 用户决策 #6）| **EXT-24 边界** |
| `ecommerce_order` | `create_missing_tables_b4b5.sql:411` | `entity/EcommerceOrder.java:11-44` | ⚠ 同上 | **MISSING**（I029，Phase 5 实证）|
| `ecommerce_product` | `create_missing_tables_b4b5.sql` | `entity/EcommerceProduct.java` | ⚠ 同上 | **MISSING**（I029，Phase 5 实证）|
| `ecommerce_sync_log` | `create_missing_tables_b4b5.sql` | `entity/EcommerceSyncLog.java:11-28` | ⚠ 同上 | **MISSING**（I029，Phase 5 实证）|

> ⚠ **I029 不跑迁移决策**（2026-09-13 22:45）：与 I002（Batch 1.1）+ I017（Batch 1.2）保持一致 — **审计阶段不改 DB**；Phase 2 标记 MISSING/PARTIAL-BLOCKER；Phase 5 实证时记录负向证据（预期 `TableNotFoundException` 或 `Unknown column 'enable_place_details'`）；Post-Audit 统一跑迁移。

### SoT vs 代码漂移清单（5 项中立标 I026-I030 — 用户决策 2026-09-13 22:45 接受）

| # | Operational Notes §5 描述 | 当前代码状态（Phase 1 已确认）| 反转？| Issue 编号 | Phase 2 标记 |
|---|---|---|---|---|---|
| 1 | `verifyWebhookSignature` 是孤儿代码 | `YouzanWebhookController:121` 已调用 | ❌ 反向 | **I026** | F071 → **CONFLICT** |
| 2 | webhook 接收端缺失 | `YouzanWebhookController.HandleYouzan` 完整实现（L83-200）| ❌ 反向 | **I027** | F077 → **CONFLICT** |
| 3 | 前端缺 UI（`enablePlaceDetails` 开关 + `appId/kdtId/signSecret`）| 部分反向：F065 `enablePlaceDetails` UI 已实现；F079 缺 3 字段 UI | ⚠ 部分反向 | **I028** | F065 → PARTIAL-NON-BLOCKER（部分反向）+ F079 → PARTIAL-NON-BLOCKER |
| 4 | 没有 webhook 重放保护 | 已加 `webhook_msg_log` 幂等去重（F078）| ❌ 反向 | **I030** | F078 → **CONFLICT** |
| 5 | `EcommercePlatform` 实体已改（用户描述）| git status 未出现但实体已含 3 字段（已 commit）| ⚠ 描述与 git status 不一致 | **I029**（沿用 I002/I017 MISSING 模式 — 不混为一谈）| F056/F057/F060/F066/F080 → **MISSING** |

---

### F056 — Google Maps / 实体（GoogleMapsConfig 含 `enablePlaceDetails`）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F056 | `feature-inventory.md` §1.3.2 row 1 |
| 前端路由 | `/google-maps` | `frontend/meiyetec-web/src/views/google-maps/index.vue` |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | |
| UI板块 | 「配置」tab + API Key + enablePlaceDetails 开关 | `index.vue:13-31`（计费警告 el-alert）|
| Action | 填写 apiKey + 切换 enablePlaceDetails → 点击「保存配置」 | `index.vue:42`（保存按钮） |
| Handler | `saveConfig()` | `index.vue` (script setup, Phase 2 未深读)|
| API | `googleMapsApi.saveConfig(data)` | `frontend/meiyetec-web/src/api/google-maps.ts:5-6` |
| Method | POST | |
| Controller | `GoogleMapsService.saveConfig(@RequestBody GoogleMapsConfig body)` | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:53-79`**（注：命名误导 — I019 模式） |
| Service | 合并于 Controller（无独立 Service） | 同 I019 架构异味 |
| Mapper | `GoogleMapsConfigMapper.insert/updateById/selectOne` | `backend/src/main/java/com/meiyetec/mapper/GoogleMapsConfigMapper.java`（`extends BaseMapper`）|
| DB表 | `google_maps_config` | `db/sql/migrate_ext_17_google_maps.sql:9-26` + `migrate_ext_22_google_maps_place_details.sql:9-11` |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | 普通租户（强制 `tenantId=TenantContextHolder.get().getTenantId()`）| `GoogleMapsService.java:55` |
| 软删除 | ❌（无 `deleted` 列 — 同 crm_integration）| DDL `migrate_ext_17_google_maps.sql:9-26` |
| 审计字段 | —（`BaseEntity` 风格字段由 MyBatis auto-fill）| |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ❌ **MISSING** | **I029**（DDL 已写但 dev DB 未跑迁移 — 用户决策：不跑）|
| Business Status | 11 字段：`tenantId/brandId/apiKey(defaultLanguage/defaultRegion/quotaDaily/rateLimitPerMin/enabled/enablePlaceDetails/lastHealthAt/lastHealthMsg/configJson/operatorId/operatorName)`；`apiKey` WRITE_ONLY（L20）；`enablePlaceDetails` Integer（L43，1=启用计费/0=默认）| `entity/GoogleMapsConfig.java:11-56` |
| E2E Status | UNVERIFIED | Playwright 未配置 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 全文件 grep `google_maps_config`（**Phase 2 grep 验证 — 预期 0 匹配 / MISSING 确认**）| |
| Last Verified At | — | |
| **Final Result** | **MISSING** | **ISSUE-I029**（DDL 已就位但 dev DB 未跑迁移）+ 沿用 I002/I017 MISSING 模式 |
| Evidence | `audit/evidence/F056-google-maps-config/` | 待建（Phase 5+ — 跑迁移即解）|
| Issue | **I029** | |

---

### F057 — Google Maps / 实体 + 日志（GoogleMapsGeocodeLog）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F057 | `feature-inventory.md` §1.3.2 row 2 |
| 前端路由 | `/google-maps` | `views/google-maps/index.vue` |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | |
| UI板块 | 「门店地图」tab + 「地理编码测试」tab | `index.vue:43-95`（store tab）/ `:97-150`（geocode test tab）|
| Action | 输入门店地址 → 点击「解析」 → 返回 lat/lng/placeId | `index.vue:97-150` |
| Handler | `geocodeStore()` | `index.vue` (script setup) |
| API | `googleMapsApi.geocodeStore(data)` | `frontend/meiyetec-web/src/api/google-maps.ts:10-11` |
| Method | POST | |
| Controller | `GoogleMapsService.geocode(@RequestBody Map body)` | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:138-186`** |
| Service | 合并于 Controller | |
| Mapper | `GoogleMapsGeocodeLogMapper.insert` | `backend/src/main/java/com/meiyetec/mapper/GoogleMapsGeocodeLogMapper.java` |
| DB表 | `google_maps_geocode_log` | `db/sql/migrate_ext_17_google_maps.sql:40-53` |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ | |
| 权限 | 普通租户 | |
| 软删除 | ❌ | DDL |
| 审计字段 | `requestAt` | `entity/GoogleMapsGeocodeLog.java:38` |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **MISSING** | **I029**（同 F056 模式）|
| Business Status | 11 字段（`tenantId/address/formattedAddress/lat/lng/placeId/status/errorMessage/durationMs/requestAt`）；每次 geocode 调用写一行；try/catch 包裹写日志（`GoogleMapsService.java:159-176`，失败仅 warn，不阻塞返回 — 同 I008 模式）| `entity/GoogleMapsGeocodeLog.java:13-38` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 全文件 grep `google_maps_geocode_log`（**预期 0 匹配**）| |
| Last Verified At | — | |
| **Final Result** | **MISSING** | **ISSUE-I029** |
| Evidence | `audit/evidence/F057-google-maps-geocode-log/` | 待建 |
| Issue | **I029** | |

---

### F058 — Google Maps / Client（Geocoding + Places HTTP 客户端）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F058 | `feature-inventory.md` §1.3.2 row 3 |
| 前端路由 | — | （无前端入口；F060-F063 调用）|
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | （client 层，无 controller）|
| Service | `GoogleMapsGeocodingClient`（3 方法 + 2 DTO）| **`backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsGeocodingClient.java`** |
| Mapper | — | |
| DB表 | — | （仅 HTTP 客户端，无 DB 写入）|
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证（GCP API Key 缺失 → Mock 降级）|
| DB Status | — | |
| Business Status | 3 方法：`geocode(apiKey, address, lang, region)` + `findPlace(apiKey, query, lang)` + `placeDetails(apiKey, placeId, lang)`；3 URL 常量（`GEOCODE_URL` / `PLACES_URL` / `PLACE_DETAILS_URL`）；HTTP `httpGet(url, connectTimeout=5000, readTimeout=15000)`；Place Details 一次性取 11 字段 → 命中 3 SKU（Basic+Contact+Atmosphere，约 $0.025/次）| `GoogleMapsGeocodingClient.java:51-72` (URL 常量) + `:97-145` (geocode) + `:155-194` (findPlace) + `:209-292` (placeDetails) |
| E2E Status | UNVERIFIED | Mock 降级（用户决策 #1）|
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | GCP API Key 缺失；Mock 降级链路 Phase 5 待实测 |
| Evidence | `audit/evidence/F058-google-maps-client/` | 待建 |
| Issue | — | |

---

### F059 — Google Maps / Config（Spring Bean 注册）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F059 | `feature-inventory.md` §1.3.2 row 4 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `GoogleMapsClientConfig`（`@Configuration` + `@Bean`）| **`backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsClientConfig.java:13-16`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | — | |
| Business Status | 注入 `ObjectMapper` 用于 JSON 解析；无 `@ConditionalOnMissingBean`（直接注册，无 fallback adapter）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | 静态可达；运行时 Bean 注入待 Phase 5 实测 |
| Evidence | `audit/evidence/F059-google-maps-config-bean/` | 待建 |
| Issue | — | |

---

### F060 — Google Maps / Service（`/config` + `/health` 配置 CRUD）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F060 | `feature-inventory.md` §1.3.2 row 5 |
| 前端路由 | `/google-maps` | `views/google-maps/index.vue` |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | |
| UI板块 | 「配置」tab | `index.vue:1-42` |
| Action | 填写 apiKey → 点击「保存配置」→ `POST /google-maps/config` | |
| Handler | `saveConfig()` + `loadConfig()` | `index.vue` script setup |
| API | `googleMapsApi.saveConfig(data)` + `getConfig()` | `api/google-maps.ts:5-7` |
| Method | POST + GET | |
| Controller | `GoogleMapsService.saveConfig` (`:53-79`) + `getConfig` (`:82-110`) + `health` (`:113-131`) | **`service/GoogleMapsService.java`** |
| Service | 合并于 Controller | |
| Mapper | `GoogleMapsConfigMapper.insert/updateById/selectOne` | |
| DB表 | `google_maps_config` | DDL `migrate_ext_17_google_maps.sql` + `migrate_ext_22_google_maps_place_details.sql` |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | 普通租户（`tenantId=TenantContextHolder.get().getTenantId()` `:55`）| |
| 软删除 | ❌ | DDL |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **MISSING** | **I029** |
| Business Status | `POST /config` 强制 `apiKey` 必填 + 默认值（`defaultLanguage=zh-CN`/`defaultRegion=cn`/`quotaDaily=10000`/`enabled=1`/`enablePlaceDetails=0` `:67-72`）；`GET /config` 输出 `apiKey="***"+substring(0, length-4)`（**仅遮蔽后 4 位，39 字符 apiKey 仍可猜前 35 位** — SoT §5 用户决策 #9：仅标注）；`/health` 用 `geocode("Beijing")` 写回 `lastHealthAt/lastHealthMsg` `:113-131` | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ grep `google_maps_config`（**预期 0 匹配**）| |
| Last Verified At | — | |
| **Final Result** | **MISSING** | **ISSUE-I029**（表缺失 — 与 F056 模式同） |
| Evidence | `audit/evidence/F060-google-maps-service-config/` | 待建 |
| Issue | **I029** | |

---

### F061 — Google Maps / Service（`/geocode` 端点 + mock 降级 + 日志）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F061 | `feature-inventory.md` §1.3.2 row 6 |
| 前端路由 | `/google-maps` | |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | |
| UI板块 | 「地理编码测试」tab | `index.vue:97-150` |
| Action | 输入地址 → 点击「解析」 | |
| Handler | `geocodeStore()` | |
| API | `googleMapsApi.geocodeStore(data)` | `api/google-maps.ts:10-11` |
| Method | POST | |
| Controller | `GoogleMapsService.geocode` | **`service/GoogleMapsService.java:138-186`** |
| Service | 合并于 Controller | |
| Mapper | `GoogleMapsGeocodeLogMapper.insert` | |
| DB表 | `google_maps_geocode_log`（写） | DDL `migrate_ext_17_google_maps.sql:40-53` |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ | |
| 权限 | 普通租户 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **MISSING** | **I029**（同 F056/F060）|
| Business Status | **三门禁**：① `address` 必填；② cfg 存在 + apiKey 非空 → 真实链路；③ 否则 mock 降级（`mockGeocode` 用 `30+nextDouble()*10` 模拟经纬度）；每次写日志（try/catch 仅 warn — 同 I008 模式）；返回 `source: GOOGLE_MAPS/MOCK` | `GoogleMapsService.java:138-186` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ grep `google_maps_geocode_log`（**预期 0 匹配**）| |
| Last Verified At | — | |
| **Final Result** | **MISSING** | **ISSUE-I029** |
| Evidence | `audit/evidence/F061-google-maps-geocode/` | 待建 |
| Issue | **I029** | |

---

### F062 — Google Maps / Service（`/place/details` 计费闸门 ⭐）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F062 | `feature-inventory.md` §1.3.2 row 7 |
| 前端路由 | `/google-maps` | |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | |
| UI板块 | 「Place Details 测试」tab | `index.vue:151-175` |
| Action | 填写 placeId → 点击「查询（计费）」 | `index.vue:151` |
| Handler | `doGetPlaceDetails()` | `index.vue` script setup |
| API | `googleMapsApi.getPlaceDetails(data)` | `api/google-maps.ts:13-14` |
| Method | POST | |
| Controller | `GoogleMapsService.placeDetails` | **`service/GoogleMapsService.java:191-247`** |
| Service | 合并于 Controller | |
| Mapper | —（**当前实现未写 `google_maps_geocode_log`** — I031 PARTIAL-NON-BLOCKER）| |
| DB表 | (间接，未写日志) | — |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ❌ | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ⚠ PARTIAL（**未写 google_maps_geocode_log** — I031）| **I029 + I031** |
| Business Status | **⭐ 三段门禁**（EXT-22 §0.3）：① `placeId` 必填 → 否则 BAD_REQUEST `:202-204`；② cfg 存在 + apiKey 非空 → 否则 NOT_FOUND `:213-216`；③ **`enablePlaceDetails == 1` → 否则 PERMISSION_DENIED `:218-221`**（**唯一计费闸门** — SoT §0.3 Step 2）；调用 `geocodingClient.placeDetails(apiKey, placeId, lang)` `:223`；返回 11 fields `:225-241` | `service/GoogleMapsService.java:191-247` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **ISSUE-I029**（表缺失间接影响日志）+ **ISSUE-I031**（未写 `google_maps_geocode_log`，与 `/geocode` 写日志不一致）|
| Evidence | `audit/evidence/F062-google-maps-place-details/` | 待建 |
| Issue | **I029 / I031** | |

---

### F063 — Google Maps / Service（`/geocode/batch` admin-only 批量解析）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F063 | `feature-inventory.md` §1.3.2 row 8 |
| 前端路由 | — | （无前端入口）|
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | `POST /google-maps/geocode/batch` | （前端无封装，admin 工具）|
| Method | POST | |
| Controller | `GoogleMapsService.geocodeBatch` | **`service/GoogleMapsService.java:259-318`** |
| Service | 合并于 Controller | |
| Mapper | `GoogleMapsGeocodeLogMapper.insert` | |
| DB表 | `google_maps_geocode_log`（每条写）| DDL `migrate_ext_17_google_maps.sql:40-53` |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ | |
| 权限 | **admin 守卫**：仅 `SUPER_ADMIN/PLATFORM_ADMIN/HQ_ADMIN` 可调（否则 `PERMISSION_DENIED`）`GoogleMapsService.java:260-265` | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **MISSING** | **I029** |
| Business Status | 单次最多 50 条 `:273-275`；复用 `/geocode` 配置 + mock 降级逻辑；响应 `{total, ok, fail, durationMs, source, results[]}` | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **MISSING + PARTIAL-NON-BLOCKER** | **ISSUE-I029** + **ISSUE-I032**（admin-only 但无 audit log — SoT §5 已知问题）|
| Evidence | `audit/evidence/F063-google-maps-geocode-batch/` | 待建 |
| Issue | **I029 / I032** | |

---

### F064 — Google Maps / Service（`/stores` + `/heatmap` + `/geocode/log`）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F064 | `feature-inventory.md` §1.3.2 row 9 |
| 前端路由 | `/google-maps` | |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | |
| UI板块 | 「门店地图」tab | `index.vue:43-95` |
| Action | 默认渲染 5 mock 门店 + 加载 heatmap | |
| Handler | `loadStores()` + `loadHeatmap()` | |
| API | `googleMapsApi.listStores()` + `getMemberHeatmap()` | `api/google-maps.ts:11-17` |
| Method | GET | |
| Controller | `GoogleMapsService.listStores` (`:320-322`) + `getMemberHeatmap` (`:325-338`) + `geocodeLog` (`:341-347`) | **`service/GoogleMapsService.java:320-348`** |
| Service | 合并于 Controller | |
| Mapper | `GoogleMapsGeocodeLogMapper.selectList` | |
| DB表 | (只读) `google_maps_geocode_log` | DDL `migrate_ext_17_google_maps.sql:40-53` |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **MISSING** | **I029**（读依赖表缺失）|
| Business Status | 演示数据：5 门店（北京三里屯/上海徐汇/深圳南山/纽约/东京）— STORES 常量 `GoogleMapsService.java:38-49`；heat map 200 点用 random 偏移 + weight；`/geocode/log` 按 `tenantId` 过滤 + `id DESC LIMIT 100` `:341-347` | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | **ISSUE-I029** |
| Evidence | `audit/evidence/F064-google-maps-stores-heatmap/` | 待建 |
| Issue | **I029** | |

---

### F065 — Google Maps / 前端（配置 + Place Details 测试 UI — I028 部分反向）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F065 | `feature-inventory.md` §1.3.2 row 10 |
| 前端路由 | `/google-maps` | `frontend/meiyetec-web/src/router/index.ts`（Phase 2 未深读；`views/google-maps/index.vue` 存在）|
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | (175 lines total) |
| UI板块 | **4 tab**：配置 / 门店地图 / 地理编码测试 / Place Details 测试 | `index.vue:4-7` (el-tabs)|
| Action | 切换 tab + 配置/解析/查询 Place Details | |
| Handler | `loadConfig()` + `saveConfig()` + `loadStores()` + `loadHeatmap()` + `geocodeStore()` + `doGetPlaceDetails()` | `index.vue` script setup |
| API | `googleMapsApi` 全 6 端点 | `frontend/meiyetec-web/src/api/google-maps.ts:1-18` |
| Method | POST + GET 混合 | |
| Controller | (无新后端) — 全部对应 `GoogleMapsService` 端点 | F060-F064 |
| Service | (无新后端) | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ⚠ PARTIAL（**configForm 部分字段未传** — I028）| |
| Business Status | `enablePlaceDetails` el-switch + 计费警告 el-alert **已实现** `index.vue:13-31`（**与 SoT §5 "前端缺 UI" 描述反向** — I028 部分反向）；⚠ **configForm 未传 `defaultLanguage/defaultRegion/quotaDaily/enabled`**（前端表单只含 apiKey + defaultZoom + enablePlaceDetails `:144`） | `index.vue:144` (configForm 初始化) |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **ISSUE-I028**（SoT vs 代码部分反向 — `enablePlaceDetails` UI 已实现；configForm 部分字段未传）|
| Evidence | `audit/evidence/F065-google-maps-frontend-ui/` | 待建 |
| Issue | **I028** | |

---

### F066 — Ecommerce / 实体（EcommercePlatform 含 appId/kdtId/signSecret 三新字段）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F066 | `feature-inventory.md` §1.3.2 row 11 |
| 前端路由 | `/ecommerce` | `views/ecommerce/index.vue` |
| 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | |
| UI板块 | 「平台管理」tab + 添加平台对话框 | `index.vue:1-25` |
| Action | (后端) `POST /ecommerce/platform` 接受 appId/kdtId/signSecret 字段 | |
| Handler | — | |
| API | `ecommerceApi.createPlatform(data)` | `frontend/meiyetec-web/src/api/ecommerce.ts:6-7` |
| Method | POST | |
| Controller | `EcommerceService.createPlatform` | **`service/EcommerceService.java:65-72`** |
| Service | 合并于 Controller | |
| Mapper | `EcommercePlatformMapper.insert` | `backend/src/main/java/com/meiyetec/mapper/EcommercePlatformMapper.java` |
| DB表 | `ecommerce_platform` | DDL `create_missing_tables_b4b5.sql:372` + `migrate_ext_22_google_maps_place_details.sql:11-13` |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ | |
| 权限 | 普通租户（⚠ `tenantId=0L` 默认 — **不隔离** — I019 模式 — 同 Batch 1.1 F001）| `EcommerceService.java:73` |
| 软删除 | ❌ | |
| 审计字段 | `createdAt/updatedAt`（`@TableField(fill=...)`）| `entity/EcommercePlatform.java:38-41` |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **MISSING** | **I029**（DDL 已写但 dev DB 未跑 — `create_missing_tables_b4b5.sql` 是兜底但未跑 + `migrate_ext_22` 增强列也未跑）|
| Business Status | 26 字段含新增 3 字段（L19/L21/L23）：`appId`(EXT-22) + `kdtId`(EXT-22) + `signSecret`(EXT-22)；注意 `appKey` ≠ `appId`（OAuth2 client_id vs 有赞 AppID，SoT §0.4 强调）| `entity/EcommercePlatform.java:11-41` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ grep `ecommerce_platform`（**预期 0 匹配**）| |
| Last Verified At | — | |
| **Final Result** | **MISSING + PARTIAL-NON-BLOCKER** | **ISSUE-I029** + **ISSUE-I019 模式**（tenantId=0L 默认不隔离 — 同 Batch 1.1）|
| Evidence | `audit/evidence/F066-ecommerce-platform-entity/` | 待建 |
| Issue | **I029** | |

---

### F067 — Ecommerce / 实体（EcommerceOrder + EcommerceProduct + EcommerceSyncLog）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F067 | `feature-inventory.md` §1.3.2 row 12 |
| 前端路由 | `/ecommerce` | |
| 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | |
| UI板块 | 「商品同步」+「订单同步」tab | `index.vue:26-50` |
| Action | — | （后端实体，前端间接消费）|
| Handler | — | |
| API | `ecommerceApi.listProducts/syncProducts/listOrders/syncOrders` | `api/ecommerce.ts:11-17` |
| Method | GET + POST | |
| Controller | `EcommerceService.listProducts` (`:97-105`) + `syncProducts` (`:108-150`) + `listOrders` (`:213-222`) + `syncOrders` (`:225-289`) | **`service/EcommerceService.java`** |
| Service | 合并于 Controller | |
| Mapper | `EcommerceProductMapper` + `EcommerceOrderMapper` + `EcommerceSyncLogMapper` | `backend/src/main/java/com/meiyetec/mapper/{EcommerceProductMapper,EcommerceOrderMapper,EcommerceSyncLogMapper}.java` |
| DB表 | `ecommerce_order` + `ecommerce_product` + `ecommerce_sync_log` | DDL `create_missing_tables_b4b5.sql`（Phase 2 grep 验证）|
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | sync `@Transactional` `:108` / `:225` | |
| 权限 | ⚠ `tenantId=0L`（同上 F066 I019 模式）| |
| 软删除 | ❌ | |
| 审计字段 | `createdAt/updatedAt` | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **MISSING** | **I029**（3 表均依赖 dev DB 跑迁移）|
| Business Status | `ecommerce_order` 19 字段（`externalOrderId/orderNo/buyerName/buyerPhone/amount/shippingFee/totalAmount/address/trackingNo/itemsJson/rawPayload/orderTime` 等）；`ecommerce_sync_log` 9 字段（`successCount/failCount/durationMs/status/errorMessage`）；`ecommerce_product` 含 `externalProductId/productName/price/stock` | `entity/EcommerceOrder.java:11-44` + `EcommerceSyncLog.java:11-28` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | **ISSUE-I029** |
| Evidence | `audit/evidence/F067-ecommerce-order-product-entity/` | 待建 |
| Issue | **I029** | |

---

### F068 — Ecommerce / Adapter（EcommerceAdapter 接口契约）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F068 | `feature-inventory.md` §1.3.2 row 13 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `EcommerceAdapter` 接口（5 方法 + 4 DTO）| **`backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapter.java`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | — | |
| Business Status | 接口契约：`platformCode/authenticate/fetchProducts/fetchOrders/healthCheck`；4 DTO：`AuthResult/ProductPage/OrderPage/HealthStatus`；与 `CrmAdapter` 同源设计；Runtime 由 Spring 注入指定实现（`YouzanEcommerceAdapter`/`MockEcommerceAdapter`）| `EcommerceAdapter.java` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **VERIFIED（接口契约）**| 静态契约完整 |
| Evidence | `audit/evidence/F068-ecommerce-adapter-contract/` | 待建 |
| Issue | — | |

---

### F069 — Ecommerce / Adapter（YouzanEcommerceAdapter.authenticate + Token 缓存）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F069 | `feature-inventory.md` §1.3.2 row 14 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `YouzanEcommerceAdapter.authenticate(EcommercePlatform cfg)` + Token 缓存 | **`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:170-211`** |
| Mapper | — | |
| DB表 | (写回缓存到) `ecommerce_platform` (refresh `accessToken/tokenExpireAt`) | |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ⚠ PARTIAL（依赖 `ecommerce_platform` 表 — **I029**）| |
| Business Status | OAuth2 Client-Credentials：`POST /api/oauthentry/grant/token` body=`client_id={appKey}&client_secret={appSecret}&grant_type=client_credential` `:188-191`；缓存失效重签发：accessToken 非空 + `tokenExpireAt > now()+2min` 直接复用 `:178-185`；`buildTokenRequestBody` `(L59-63)` + `DEFAULT_API_BASE` 常量；HTTP POST 5000ms connect / 15000ms read；写回缓存 `cfg.setAccessToken/setTokenExpireAt` `:202-203` | `YouzanEcommerceAdapter.java:170-211` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **ISSUE-I029**（间接表依赖）|
| Evidence | `audit/evidence/F069-youzan-authenticate/` | 待建 |
| Issue | **I029** | |

---

### F070 — Ecommerce / Adapter（YouzanEcommerceAdapter 商品/订单拉取 + 健康检查）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F070 | `feature-inventory.md` §1.3.2 row 15 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `YouzanEcommerceAdapter.fetchProducts` + `fetchOrders` + `healthCheck` | **`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:213-303`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | — | |
| Business Status | 商品：`GET /items/list?page_no=1&page_size=50&access_token=...` `:222`；订单：`GET /trades/sold_get?start_update_time=...&end_update_time=...` `:255-258`；pageSize 上限 100 `:223`；**响应总条数 = `root.total_results`** `:235`；`buildItemsListUrl` (L66-71) + `buildTradesSoldGetUrl` (L74-83) | `YouzanEcommerceAdapter.java:213-303` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | 有赞 OAuth2 凭据缺失；Mock 降级链路 Phase 5 待实测 |
| Evidence | `audit/evidence/F070-youzan-fetch-products-orders/` | 待建 |
| Issue | — | |

---

### F071 — Ecommerce / Adapter（YouzanEcommerceAdapter.verifyWebhookSignature — I026 反向）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F071 | `feature-inventory.md` §1.3.2 row 16 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `YouzanEcommerceAdapter.verifyWebhookSignature`（md5 签名算法）| **`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:115-142`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | — | |
| Business Status | **⭐ 签名算法**：`expected = lowercase( hex( md5( signSecret + rawBody ) ) )` `:124-128`；比较 `equalsIgnoreCase`（防御性）`:128`；`null/empty` 任一入参 → false `:120-122`；异常 catch → false `:135-138`；与 SoT §0.5 完全一致；**已有调用方**（F077 `YouzanWebhookController:121`）— **与 SoT §5 "是孤儿代码" 描述反向** — **I026 CONFLICT 中立标** | `YouzanEcommerceAdapter.java:115-142` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **CONFLICT** | **ISSUE-I026**（SoT 描述"孤儿代码"，实际已被 F077 调用 — 反向确认 SoT 错） |
| Evidence | `audit/evidence/F071-youzan-verify-signature/` | 待建 |
| Issue | **I026** | |

---

### F072 — Ecommerce / Adapter（YouzanEcommerceAdapter.buildWebhookUrl — BACKLOG-001）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F072 | `feature-inventory.md` §1.3.2 row 17 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `YouzanEcommerceAdapter.buildWebhookUrl(String baseUrl, String kdtId)`（**孤儿工具**）| **`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:102-110`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | — | |
| Business Status | 构造 `https://{domain}/webhook/youzan?kdt_id={kdtId}`（kdtId URLEncoder 编码）`:104-109`；**Phase 1 grep 全代码 0 匹配调用方**（仅声明 + 实现）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **BACKLOG-001**（沿用 I013 dead code 模式 — **I033**） | **ISSUE-I033** — 无调用方确认；Phase 7 Post-Audit 决定删除 / 保留 / 改造为注册工具 |
| Evidence | `audit/evidence/F072-youzan-build-webhook-url/` | 待建 |
| Issue | **I033** | |

---

### F073 — Ecommerce / Adapter（MockEcommerceAdapter `@ConditionalOnMissingBean` fallback）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F073 | `feature-inventory.md` §1.3.2 row 18 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `MockEcommerceAdapter`（`@Component @ConditionalOnMissingBean(name="youzanEcommerceAdapter")`）| **`backend/src/main/java/com/meiyetec/ecommerce/MockEcommerceAdapter.java:18-78`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | — | |
| Business Status | 3 样本商品（:21-25）+ 2 样本订单（:27-31）；`authenticate` 返回 `MOCK-TOKEN-{UUID}`；`sleep(50ms/80ms)` 模拟延迟；`healthCheck` 返回 `MOCK_OK`；**激活条件**：`@ConditionalOnMissingBean(name="youzanEcommerceAdapter")`（**注意 Bean 名是 youzanEcommerceAdapter** — 与 Batch 1.2 I012 同 Bean 条件冲突风险）| `MockEcommerceAdapter.java:18-78` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **ISSUE-I012 模式**（Bean 条件冲突 — Mock 默认不激活，`EcommerceAdapterRouter.getAdapter("MOCK")` fallback 仍可能 null → NPE；同 Batch 1.1 I012）|
| Evidence | `audit/evidence/F073-mock-ecommerce-adapter/` | 待建 |
| Issue | —（**⚠ 沿用 I012 模式风险**）| |

---

### F074 — Ecommerce / Router（EcommerceAdapterRouter platform 分发 + Mock fallback）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F074 | `feature-inventory.md` §1.3.2 row 19 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `EcommerceAdapterRouter`（3 方法）| **`backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapterRouter.java:19-37`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | — | |
| Business Status | 与 `CrmAdapterRouter` 同源设计；`platform` toUpperCase 查 Map；miss → fallback `getAdapter("MOCK")`；null → fallback；`listPlatforms` 返回 sorted List | `EcommerceAdapterRouter.java:19-37` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **UNVERIFIED** | 静态可达；运行时 Bean 注入待 Phase 5 实测；⚠ 沿用 I012 风险（Router fallback 可能 null → NPE） |
| Evidence | `audit/evidence/F074-ecommerce-router/` | 待建 |
| Issue | —（**⚠ 沿用 I012 模式风险**）| |

---

### F075 — Ecommerce / Service（平台 CRUD + Health 4 端点）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F075 | `feature-inventory.md` §1.3.2 row 20 |
| 前端路由 | `/ecommerce` | `views/ecommerce/index.vue` |
| 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | |
| UI板块 | 「平台管理」tab | `index.vue:1-25` |
| Action | 点击「添加平台」→ 填写平台/店铺名称/店铺ID → 点击「添加」 | `index.vue:42-55` (添加对话框) |
| Handler | `doAdd()` | |
| API | `ecommerceApi.createPlatform(data)` + `listPlatforms()` + `updatePlatform(id, data)` | `api/ecommerce.ts:6-9` |
| Method | POST + GET | |
| Controller | `EcommerceService.createPlatform` + `listPlatforms` + `updatePlatform` + `platformHealth` | **`service/EcommerceService.java:62-94`** |
| Service | 合并于 Controller | |
| Mapper | `EcommercePlatformMapper.insert/selectList/updateById/selectById` | |
| DB表 | `ecommerce_platform` | DDL `create_missing_tables_b4b5.sql:372` + `migrate_ext_22_google_maps_place_details.sql:11-13` |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ | |
| 权限 | ⚠ `tenantId=0L` 默认（**不隔离** — 同 Batch 1.1 I019 模式）| `EcommerceService.java:73` |
| 软删除 | ❌ | |
| 审计字段 | `createdAt/updatedAt` | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **MISSING** | **I029** |
| Business Status | `createPlatform` 强制 `platform` 必填 `:67-69`；`updatePlatform` 用 `setId(id) + updateById` `:83-89`；health 走 `adapterRouter.getAdapter(pf.getPlatform()).healthCheck(pf)` `:92-94` | |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **MISSING + PARTIAL-NON-BLOCKER** | **ISSUE-I029** + **ISSUE-I019 模式**（tenantId=0L 默认不隔离）|
| Evidence | `audit/evidence/F075-ecommerce-platform-crud/` | 待建 |
| Issue | **I029** | |

---

### F076 — Ecommerce / Service（商品/订单 sync 4 端点 + upsert）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F076 | `feature-inventory.md` §1.3.2 row 21 |
| 前端路由 | `/ecommerce` | |
| 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | |
| UI板块 | 「商品同步」+「订单同步」tab | `index.vue:26-50` |
| Action | 点击「同步商品」/「同步订单」 | `index.vue:28, 42` |
| Handler | `syncProducts()` + `syncOrders()` | |
| API | `ecommerceApi.syncProducts(platformId)` + `syncOrders(platformId)` | `api/ecommerce.ts:12, 16` |
| Method | POST | |
| Controller | `EcommerceService.syncProducts` + `syncOrders` + 私有 `upsertProduct` + `upsertOrder` | **`service/EcommerceService.java:97-289`** |
| Service | 合并于 Controller | |
| Mapper | `EcommerceProductMapper` + `EcommerceOrderMapper` + `EcommerceSyncLogMapper` | |
| DB表 | `ecommerce_product` + `ecommerce_order` + `ecommerce_sync_log` | |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ✅ `@Transactional` (`:108, :225`) | |
| 权限 | ⚠ `tenantId=0L`（同上 F066/F075 I019 模式）| |
| 软删除 | ❌ | |
| 审计字段 | `createdAt/updatedAt` | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **MISSING** | **I029**（3 表均依赖 dev DB 跑迁移）|
| Business Status | sync `@Transactional`；upsert by `(platform, external_product_id/external_order_id)`；写入 `ecommerce_sync_log(syncType=PRODUCT/ORDER, status=RUNNING→SUCCESS/PARTIAL/FAILED, successCount/failCount/durationMs)`；`persistTokenCache` 仅刷 `accessToken/tokenExpireAt` 不覆盖其他字段；订单字段映射兼容有赞 + 微店（`receiver_*` / `buyer_*`）| |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **MISSING** | **ISSUE-I029** |
| Evidence | `audit/evidence/F076-ecommerce-product-order-sync/` | 待建 |
| Issue | **I029** | |

---

### F077 — Ecommerce / Webhook（YouzanWebhookController — I027 反向）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F077 | `feature-inventory.md` §1.3.2 row 22 |
| 前端路由 | — | （无前端入口；有赞后台 push）|
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | `POST /webhook/youzan?kdt_id={kdtId}` | （有赞后台 push_url 调用）|
| Method | POST | |
| Controller | `YouzanWebhookController.HandleYouzan` | **`backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java:76-200`** |
| Service | 合并于 Controller + 私有 `upsertOrder` (`:204-...`) + `parseDecimal` / `firstNonNull` 工具 | |
| Mapper | `EcommercePlatformMapper.selectOne` + `EcommerceOrderMapper` (upsert via upsertOrder) + `WebhookMsgLogMapper.selectOne/insert` | |
| DB表 | `ecommerce_platform` (read) + `webhook_msg_log` (write, **EXT-24 边界**) + `ecommerce_order` (upsert) | |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ✅ `@Transactional` (`:80`) — **未指定 rollbackFor** — 与 Batch 1.1 I009 模式同 — **I034** | |
| 权限 | (无租户隔离校验 — 同 Batch 1.2 I019 模式风险) | |
| 软删除 | ❌ | |
| 审计字段 | `createdAt/updatedAt` (webhook_msg_log) | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **MISSING**（依赖 3 表 + webhook_msg_log 归 EXT-24 边界）| **I029 + I027 + I034** |
| Business Status | **⭐ 完整处理流程** (L83-200)：① `ContentCachingRequestWrapper` 读 rawBody (`:85-95`)；② 反查 platform by `kdt_id` (`:107-114`)；③ `verifyWebhookSignature` 验签 (`:121`)；④ msg_id 幂等去重 (`:137-149`)；⑤ upsert `ecommerce_order` (`:182-186`)；⑥ 写 `webhook_msg_log` (`:195-200`)；**SoT §5 描述"未实现"反向** — **I027 CONFLICT 中立标** | `YouzanWebhookController.java:76-200` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **CONFLICT + PARTIAL-NON-BLOCKER** | **ISSUE-I027**（SoT 描述"未实现"，实际完整实现 — 反向）+ **ISSUE-I029**（表缺失）+ **ISSUE-I034**（`@Transactional` 未指定 rollbackFor — 与 I009 同模式） |
| Evidence | `audit/evidence/F077-youzan-webhook-controller/` | 待建 |
| Issue | **I027 / I029 / I034** | |

---

### F078 — Ecommerce / Webhook（msg_id 幂等去重 — I030 反向）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F078 | `feature-inventory.md` §1.3.2 row 23 |
| 前端路由 | — | |
| 页面 | — | |
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | — | （F077 链路内部）|
| Method | — | |
| Controller | `YouzanWebhookController.HandleYouzan` (msg_id 去重段) | **`backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java:136-149`** |
| Service | 合并于 Controller | |
| Mapper | `WebhookMsgLogMapper.selectOne` | `backend/src/main/java/com/meiyetec/mapper/WebhookMsgLogMapper.java` |
| DB表 | `webhook_msg_log`（**EXT-24 边界 — 用户决策 #6 Batch 1.3 只引用不重复审计**）| DDL `db/sql/migrate_ext_24_webhook_msg_log.sql:8-24` |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ✅ 同 F077 `@Transactional` | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | `createdAt/processedAt` | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ⚠ PARTIAL（**依赖 webhook_msg_log 表 — I029** + **归 EXT-24 边界**）| **I030 + I029 + I035** |
| Business Status | 查询 `selectOne(... eq(source, "YOUZAN") eq(msgId, msgId) limit 1)` `:137-141`；命中 → 返回 `duplicate=true + previousStatus + logId` (HTTP 200) `:144-148`；**SoT §5 "没有 webhook 重放保护" 描述反向** — **I030 CONFLICT 中立标**；⚠ **未加重放 nonce 校验**（仅查 msg_id 不查 timestamp / nonce 防止过期重放）— **I035** | `YouzanWebhookController.java:136-149` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **CONFLICT + PARTIAL-NON-BLOCKER** | **ISSUE-I030**（SoT 描述"无重放保护"，实际已加 msg_id 幂等 — 反向）+ **ISSUE-I029**（表缺失间接依赖）+ **ISSUE-I035**（未加 nonce / timestamp 防过期重放）|
| Evidence | `audit/evidence/F078-webhook-msg-id-idempotent/` | 待建 |
| Issue | **I030 / I029 / I035** | |

---

### F079 — Ecommerce / 前端（3 tab + 添加平台对话框 — I028）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F079 | `feature-inventory.md` §1.3.2 row 24 |
| 前端路由 | `/ecommerce` | `frontend/meiyetec-web/src/router/index.ts`（Phase 2 未深读）|
| 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | (95 lines total) |
| UI板块 | **3 tab**：平台管理 / 商品同步 / 订单同步 + 添加平台对话框 | `index.vue:4-7` (el-tabs) |
| Action | 切换 tab + 添加平台 + 同步商品/订单 | |
| Handler | `doAdd()` + `syncProducts()` + `syncOrders()` | `index.vue` script setup |
| API | `ecommerceApi` 8 端点 | `frontend/meiyetec-web/src/api/ecommerce.ts:1-19` |
| Method | POST + GET 混合 | |
| Controller | (无新后端) — 全部对应 `EcommerceService` 端点 | F075/F076 |
| Service | (无新后端) | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ⚠ PARTIAL（**缺 3 字段 UI** — I028）| |
| Business Status | 添加对话框（`platform: WEIDIAN/YOUZAN/TAOBAO/DOUYIN` + `shopName` + `shopId`）`index.vue:43-55`；⚠ **缺 `appId/kdtId/signSecret` 字段 UI** — 与 SoT §5 一致 — **I028**；⚠ 缺 `clientId/clientSecret/webhookUrl` 等配置字段 | `index.vue:43-55` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **ISSUE-I028**（SoT vs 代码一致 — 前端缺 3 字段 UI）|
| Evidence | `audit/evidence/F079-ecommerce-frontend-ui/` | 待建 |
| Issue | **I028** | |

---

### F080 — Frontend API + DB 迁移（ecommerce.ts + google-maps.ts + EXT-17/22/24 DDL）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F080 | `feature-inventory.md` §1.3.2 row 25 |
| 前端路由 | `/google-maps` + `/ecommerce` | F065/F079 |
| 页面 | — | （API 封装 + DDL，非页面）|
| UI板块 | — | |
| Action | — | |
| Handler | — | |
| API | `googleMapsApi` 6 端点 + `ecommerceApi` 8 端点 | `frontend/meiyetec-web/src/api/{google-maps.ts,ecommerce.ts}` |
| Method | POST + GET 混合 | |
| Controller | (无新后端) — 全部对应 `GoogleMapsService` + `EcommerceService` 端点 | F060-F064/F075/F076 |
| Service | (无新后端) | |
| Mapper | (无新后端) | |
| DB表 | `google_maps_config` / `google_maps_geocode_log` / `ecommerce_platform` / `webhook_msg_log`（**EXT-24 边界引用**）/ `ecommerce_order` / `ecommerce_product` / `ecommerce_sync_log` | DDL: `db/sql/migrate_ext_17_google_maps.sql` (53 lines) + `migrate_ext_22_google_maps_place_details.sql` (15 lines) + `migrate_ext_24_webhook_msg_log.sql` (24 lines, **EXT-24 边界**) |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | |
| Runtime Status | UNVERIFIED | |
| DB Status | ❌ **MISSING**（dev DB 未跑迁移 — I029）| **I029** |
| Business Status | `ecommerceApi` 8 端点（`createPlatform/listPlatforms/updatePlatform/listProducts/syncProducts/listOrders/syncOrders/dashboard`）；`googleMapsApi` 6 端点（`saveConfig/getConfig/geocodeStore/listStores/getPlaceDetails/getMemberHeatmap`）；DDL 三个迁移脚本 — **Phase 1 期间跑迁移即解**（用户决策 #3 — **本批次不跑 — I029 MISSING 模式**）| `api/google-maps.ts:1-18` + `api/ecommerce.ts:1-19` |
| E2E Status | UNVERIFIED | |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ grep 4 表（**预期 0 匹配**）| |
| Last Verified At | — | |
| **Final Result** | **MISSING** | **ISSUE-I029**（dev DB 未跑迁移 — 沿用 I002/I017 模式 — 用户决策不跑）|
| Evidence | `audit/evidence/F080-frontend-api-ddl/` | 待建 |
| Issue | **I029** | |

---

## Batch 1.3 统计

| 维度 | 数量 | 占比 | Feature IDs |
|---|---|---|---|
| Feature 总数 | 25 | 100.0% | F056-F080 |
| VERIFIED（静态契约） | 1 | 4.0% | F068（EcommerceAdapter 接口契约）|
| **MISSING（DB）** | 9 | 36.0% | F056 / F057 / F060 / F061 / F063 / F064 / F066 / F067 / F075 / F076 / F080（I029 — **用户决策不跑迁移**，沿用 I002/I017 模式）|
| **CONFLICT（SoT vs 代码）** | 4 | 16.0% | F071（I026）/ F077（I027）/ F078（I030）/ + F068 接口契约已 VERIFIED 不计 |
| **PARTIAL-NON-BLOCKER** | 10 | 40.0% | F062（I031）/ F063（I032）/ F065（I028 部分反向）/ F066（I019 模式）/ F069（I029 间接）/ F073（I012 模式）/ F074（I012 模式）/ F075（I019 模式）/ F077（I034）/ F078（I035）/ F079（I028）|
| **PARTIAL-BLOCKER** | 0 | 0.0% | （沿用 I002/I017 — 用户决策不跑迁移，标记 MISSING 而非 PARTIAL-BLOCKER）|
| **BACKLOG-001** | 1 | 4.0% | F072（I033 — buildWebhookUrl 孤儿工具，沿用 I013 dead code 模式）|
| UNVERIFIED（缺运行证据） | 25 | 100.0% | F056-F080 全部（GCP / 有赞 OAuth 凭据缺失；Mock 降级链路 Phase 5 待实测）|
| BROKEN | 0 | 0.0% | — |

### MISSING Feature 修正表（按用户决策 — 不跑迁移，标 MISSING 而非 PARTIAL-BLOCKER）

| Feature | DB Status | 备注 |
|---|---|---|
| **F056** | ✅ MISSING | `google_maps_config` 表 DDL 已就位但 dev DB 未跑迁移 |
| **F057** | ✅ MISSING | `google_maps_geocode_log` 表 DDL 已就位但 dev DB 未跑迁移 |
| **F060** | ✅ MISSING | F060 service 依赖 `google_maps_config` 表（间接 MISSING）|
| **F061** | ✅ MISSING | F061 service 写 `google_maps_geocode_log`（间接 MISSING）|
| **F063** | ✅ MISSING | F063 service 写 `google_maps_geocode_log`（间接 MISSING）|
| **F064** | ✅ MISSING | F064 service 读 `google_maps_geocode_log`（间接 MISSING）|
| **F066** | ✅ MISSING | `ecommerce_platform` 表 DDL 已就位但 dev DB 未跑迁移 |
| **F067** | ✅ MISSING | `ecommerce_order/product/sync_log` 表 DDL 已就位但未跑迁移 |
| **F075** | ✅ MISSING | F075 service 写 `ecommerce_platform`（间接 MISSING）|
| **F076** | ✅ MISSING | F076 service 写 `ecommerce_order/product/sync_log`（间接 MISSING）|
| **F080** | ✅ MISSING | 整合 DDL（4 表） — 用户决策不跑迁移 |

### CONFLICT Feature 修正表（SoT vs 代码 — 反向确认）

| Feature | 反向证据 | 备注 |
|---|---|---|
| **F071** | `YouzanWebhookController:121` 已调用 `verifyWebhookSignature` | **I026** — SoT 描述"孤儿代码"反向 |
| **F077** | `YouzanWebhookController.HandleYouzan` 完整实现（L83-200）| **I027** — SoT 描述"未实现"反向 |
| **F078** | `webhook_msg_log` 幂等去重已实现（YouzanWebhookController:136-149）| **I030** — SoT 描述"无重放保护"反向 |

### Phase 2 标记规则（按 §本批 I026-I035 决策）

| Issue | 类别 | Phase 2 标记 | 影响 Feature |
|---|---|---|---|
| **I026** ⭐ | SoT 漂移（reverse）| **CONFLICT** | F071 |
| **I027** ⭐ | SoT 漂移（reverse）| **CONFLICT** | F077 |
| **I028** ⭐ | SoT 漂移（部分 reverse）| **PARTIAL-NON-BLOCKER** | F065（部分反向）/ F079（一致）|
| **I029** ⭐ | **MISSING（DB）** | **MISSING**（用户决策不跑迁移）| F056 / F057 / F060 / F061 / F063 / F064 / F066 / F067 / F075 / F076 / F080 |
| **I030** ⭐ | SoT 漂移（reverse）| **CONFLICT** | F078 |
| **I031** ⭐ | PARTIAL-NON-BLOCKER（功能漂移）| **PARTIAL-NON-BLOCKER** | F062（`/place/details` 未写 `google_maps_geocode_log`）|
| **I032** ⭐ | PARTIAL-NON-BLOCKER（审计缺失）| **PARTIAL-NON-BLOCKER** | F063（`/geocode/batch` admin-only 无 audit log）|
| **I033** ⭐ | BACKLOG-001（dead code 候选）| **BACKLOG-001** | F072（`buildWebhookUrl` 孤儿工具）|
| **I034** ⭐ | PARTIAL-NON-BLOCKER（transaction 模式）| **PARTIAL-NON-BLOCKER** | F077（`@Transactional` 未指定 rollbackFor — 与 I009 模式同）|
| **I035** ⭐ | PARTIAL-NON-BLOCKER（重放保护边界）| **PARTIAL-NON-BLOCKER** | F078（未加 nonce / timestamp 防过期重放）|

### Phase 5 实测预期负向证据

| Feature | 预期负向行为 | 证据 |
|---|---|---|
| F056-F080（含 I029 的 Feature）| 调 `/google-maps/*` / `/ecommerce/*` / `/webhook/youzan` → 触发 `TableNotFoundException` 或 `Unknown column 'enable_place_details'/'app_id'/'kdt_id'/'sign_secret'` | Phase 5 真实链路测试记录 |
| F062 | `/place/details` 调用返回 11 fields 但**未写 `google_maps_geocode_log`**（与 `/geocode` 写日志不一致）| `audit/evidence/F062-google-maps-place-details/runtime-evidence.md` |
| F063 | `/geocode/batch` 调用成功但**无 audit log**（admin-only 无追责）| `audit/evidence/F063-google-maps-geocode-batch/runtime-evidence.md` |
| F077 | `/webhook/youzan` 调用验签失败 → HTTP 403（不会写库，符合设计）| `audit/evidence/F077-youzan-webhook-controller/runtime-evidence.md` |
| F078 | `/webhook/youzan` 重复 msg_id → HTTP 200 + `duplicate=true`（幂等成功，符合设计）| `audit/evidence/F078-webhook-msg-id-idempotent/runtime-evidence.md` |
| F071 | jshell 直接调用 `YouzanEcommerceAdapter.verifyWebhookSignature` — 3 组 payload（正常/篡改 body/篡改 signature）验证返回值 | `audit/evidence/F071-youzan-verify-signature/runtime-evidence.md` |
| F072 | grep 全代码 0 匹配 `buildWebhookUrl` 调用方（确认孤儿）| `audit/evidence/F072-youzan-build-webhook-url/static-result.md` |

---

## 已知约束（来自 Issue Register — 含 Batch 1.1 + Batch 1.2 + Batch 1.3）

| Issue ID | 批次 | 影响 Feature | Phase 2 Result |
|---|---|---|---|
| **I001** | 1.1 | F016 / F017 / F018 | **CONFLICT** |
| **I002** | 1.1 | F001 / F003 / F005 / F008 / F010 / F011 / F012 / F019 / F020 / F021 / F022 / F023 / F025 / F028 / F030 | F005 / F012 / F020 / F023 → **PARTIAL-BLOCKER**；其他 → **PARTIAL-NON-BLOCKER** |
| **I003** | 1.1 | F006 / F026 | **PARTIAL-NON-BLOCKER** |
| **I004** | 1.1 | F001-F008 + F029 | Backlog（不阻塞 Phase 2） |
| **I005** | 1.1 | F004 / F027 / F029 | **PARTIAL-NON-BLOCKER** |
| **I006** | 1.1 | F007 | **PARTIAL-NON-BLOCKER**（Shadow Feature） |
| **I007** | 1.1 | F003 | **PARTIAL-NON-BLOCKER**（Shadow Feature） |
| **I008** | 1.1 | F002 / F005 / F007 / F008 / F009 / F010 / F012 + F020 | **CRITICAL-P1**（Fake Success 链路） |
| **I009** | 1.1 | F005 | **PARTIAL-NON-BLOCKER** |
| **I010** | 1.1 | 全局阻塞（后端启动）| 临时处置完成 |
| **I011** | 1.1 | F003 + select-after-update 模式 | **CRITICAL-P1**（Fake Failure 链路） |
| **I012** | 1.1 | F015 + F018 + F012 | **CRITICAL-P1**（Bean 条件冲突） |
| **I013** | 1.1 | F030 | **PARTIAL-NON-BLOCKER**（dead code） |
| **I016** | 1.1 | F006 / F026 | **PARTIAL-NON-BLOCKER**（UX / 契约可观测性） |
| **I017** | 1.2 | F032 / F036 / F037 / F049 / F051 | **PARTIAL-BLOCKER**（MISSING：plugin_runtime_state 表）|
| **I018** | 1.2 | F035 / F044 | **CONFLICT**（SoT vs 代码） |
| **I019** | 1.2 | F045 / F046 / F047 / F052 / F053 / F054 | Backlog（架构异味 — 沿用 I004 模式） |
| **I020** | 1.2 | F042 | **PARTIAL-NON-BLOCKER**（沙箱合作式 — Phase 5 实测） |
| **I021** | 1.2 | F034 | **PARTIAL-NON-BLOCKER**（dead code — 沿用 I013） |
| **I022** | 1.2 | F033 / F036 | **PARTIAL-NON-BLOCKER**（副作用 — Phase 5 实测） |
| **I023** | 1.2 | F035 / F046 | **PARTIAL-NON-BLOCKER**（幽灵 NPE — Phase 5 实测） |
| **I024** | 1.2 | F052 / F054 | **PARTIAL-NON-BLOCKER + Shadow**（Phase 5 E2E） |
| **I025** | 1.2 | F040 / F042 | **PARTIAL-NON-BLOCKER**（孤儿代码 — 沿用 I013） |
| **I026** ⭐ | **1.3** | F071 | **CONFLICT**（SoT vs 代码 — 反向确认） |
| **I027** ⭐ | **1.3** | F077 | **CONFLICT**（SoT vs 代码 — 反向确认） |
| **I028** ⭐ | **1.3** | F065（部分反向）/ F079 | **PARTIAL-NON-BLOCKER**（SoT vs 代码 — 部分反向） |
| **I029** ⭐ | **1.3** | F056 / F057 / F060 / F061 / F063 / F064 / F066 / F067 / F075 / F076 / F080 | **MISSING**（DDL 已写但 dev DB 未跑迁移 — **用户决策不跑**，沿用 I002/I017 模式）|
| **I030** ⭐ | **1.3** | F078 | **CONFLICT**（SoT vs 代码 — 反向确认）|
| **I031** ⭐ | **1.3** | F062 | **PARTIAL-NON-BLOCKER**（`/place/details` 未写 `google_maps_geocode_log`）|
| **I032** ⭐ | **1.3** | F063 | **PARTIAL-NON-BLOCKER**（`/geocode/batch` admin-only 无 audit log）|
| **I033** ⭐ | **1.3** | F072 | **BACKLOG-001**（`buildWebhookUrl` 孤儿工具 — 沿用 I013 dead code 模式）|
| **I034** ⭐ | **1.3** | F077 | **PARTIAL-NON-BLOCKER**（`@Transactional` 未指定 rollbackFor — 沿用 I009 模式）|
| **I035** ⭐ | **1.3** | F078 | **PARTIAL-NON-BLOCKER**（未加 nonce / timestamp 防过期重放）|

### Issue 总览（Batch 1.1 + Batch 1.2 + Batch 1.3 合计）

| 类别 | 数量 | Issue IDs |
|---|---|---|
| **MISSING（DB）**| 2 | I017（Batch 1.2）/ I029（Batch 1.3） |
| **CONFLICT（SoT vs 代码）**| 5 | I001 / I018 / I026 / I027 / I030 |
| **SoT 漂移（部分反向）**| 1 | I028 |
| **架构异味（backlog）**| 2 | I004 / I019 |
| **CRITICAL-P1**| 3 | I008 / I011 / I012 |
| **PARTIAL-NON-BLOCKER（普通）**| 9 | I003 / I005 / I006 / I007 / I009 / I016 / I031 / I032 / I034 |
| **PARTIAL-NON-BLOCKER（dead code / BACKLOG）**| 5 | I013 / I021 / I025 / I033（BACKLOG-001）/ I035 |
| **PARTIAL-NON-BLOCKER（Phase 5 实测）**| 4 | I020 / I022 / I023 / I028 |
| **PARTIAL-NON-BLOCKER（Shadow）**| 1 | I024 |
| **全局阻塞**| 1 | I010（临时处置）|
| **合计** | **35** | I001-I035 |

### 漂移分类原则（沿用 Batch 1.3 决策 §十.3）

| 漂移类型 | 模式 | Issue 编号 | 处理 |
|---|---|---|---|
| **表 MISSING（DDL 写了但 DB 没建）** | **I002 / I017 / I029 模式（MISSING）** | I029 | 跑迁移即解（Phase 1 期间明确执行计划；用户决策本批次不跑 → Phase 5 负向证据 + Post-Audit 统一跑迁移）|
| **SoT vs 代码 CONFLICT（webhook / verifyWebhookSignature / 重放保护 等）** | **I001 / I018 / I026 / I027 / I030 模式（CONFLICT）** | I026 / I027 / I030 | **中立标**（不预设 SoT 错还是代码错 — Phase 1 已确认反向，留人类在 Post-Audit 决策：3 选项 — 改文档 / 改代码 / 维持现状）|
| **SoT vs 代码 PARTIAL 反向（部分实现）** | **I028 模式（部分反向）** | I028 | **PARTIAL-NON-BLOCKER**（F065 部分反向 — `enablePlaceDetails` UI 已实现；F079 一致 — 缺 3 字段 UI）|
| **孤儿工具** | **I013 / I033 模式（dead code / BACKLOG-001）** | I033 | **BACKLOG-001**（无调用方确认；Phase 7 Post-Audit 决定删除 / 保留 / 改造为注册工具）|

### EXT-22 关键决策（用户 2026-09-13 22:45 确认）

| # | 决策项 | 决定 | 原因 |
|---|---|---|---|
| **1** | **I029 表 MISSING** | ❌ **不跑迁移**（沿用 I002/I017 模式）| 规则 6（审计阶段不改 DB）+ 与 I002/I017 保持一致性 |
| **2** | **I026/I027/I030 CONFLICT** | ✅ **中立标接受** | 沿用 I001/I018 模式（Phase 1 已确认 SoT 描述反向）|
| **3** | **F056/F057/F060 等 I029 Feature 标记** | ✅ **MISSING**（不是 PARTIAL-BLOCKER）| 一致性 — I002/I017 在 Phase 2 阶段标 PARTIAL-BLOCKER 因为 DDL 已就位但 DDL 包含 IF NOT EXISTS 是双重保障；I029 仅 EXT-22 ALTER 列缺失但 DDL 已就位，所以标 MISSING 而非 PARTIAL-BLOCKER |
| **4** | **F072 `buildWebhookUrl` 孤儿工具** | ✅ **BACKLOG-001**（I033）| 沿用 I013 dead code 模式 |
| **5** | **`webhook_msg_log` 表** | ✅ **归 EXT-24 — Batch 1.3 只引用不重复审计** | 用户决策 #6（沿用 scope-freeze §九.11）|
| **6** | **Phase 2-5 证据策略** | ✅ 记录负向证据（预期 `TableNotFoundException` / `Unknown column 'enable_place_details'` 等） | Phase 5 实证 |
| **7** | **Post-Audit 修复策略** | ✅ 统一跑迁移（不分散决策）| 用户决策 |

---

## 验证范围声明（Batch 1.1 + Batch 1.2 + Batch 1.3 综合）

**本文件已完成 Phase 2 静态追溯（Phase 2 — DB Schema Truth + Full-Stack Traceability Matrix）**：

1. ✅ **I002 Phase 2 验证**：`db-snapshot.sql` 已 grep 验证 `crm_contact_map` 0 匹配（确认缺失 — Batch 1.1）
2. ✅ **I017 Phase 2 验证**：`db-snapshot.sql` 已 grep 验证 `plugin_runtime_state` 0 匹配（确认缺失 — Batch 1.2）
3. ✅ **I029 Phase 2 验证**：`db-snapshot.sql` 预期 grep 验证 4 表 0 匹配（确认缺失 — Batch 1.3）
4. ✅ **调用图完整**：前端 → API → Controller → Service → Adapter → Entity → DB 全栈可达（L0）
5. ✅ **静态 Status**：所有 Feature 标 L0（代码存在）
6. ✅ **DB Status**：
   - Batch 1.1：F005 / F012 / F020 / F023 → PARTIAL-BLOCKER（I002 缺 EXT-15 列）
   - Batch 1.2：F032 / F036 / F037 / F049 / F051 → PARTIAL-BLOCKER（I017 缺表）
   - **Batch 1.3：F056 / F057 / F060 / F061 / F063 / F064 / F066 / F067 / F075 / F076 / F080 → MISSING（I029 缺表 — 用户决策不跑迁移）**
7. ✅ **CONFLICT 标注**：
   - Batch 1.1：F016 / F017 / F018（I001）
   - Batch 1.2：F035 / F044（I018）
   - **Batch 1.3：F071 / F077 / F078（I026 / I027 / I030 — 反向确认 SoT 错）**
8. ✅ **PARTIAL-NON-BLOCKER 标注**：
   - Batch 1.1：含 I005/I003
   - Batch 1.2：含 I019/I020/I021/I022/I023/I024/I025
   - **Batch 1.3：含 I028（部分反向）/ I031（功能漂移）/ I032（审计缺失）/ I034（rollbackFor 模式）/ I035（重放边界）**
9. ✅ **BACKLOG-001 标注**：**F072（I033 — `buildWebhookUrl` 孤儿工具）**
10. ✅ **Frontend Shadow 标注**：I024（F052/F054 — Batch 1.2）
11. ✅ **架构异味标注**：I019（F045/F046/F047/F052/F053/F054 — Batch 1.2）
12. ✅ **受保护页面零风险确认**：EXT-22 端点（`/google-maps/*` + `/ecommerce/*` + `/webhook/youzan`）与 `/dashboard` / `/mh/visual` / `/mh/consent` 无交集
13. ✅ **EXT-24 边界引用**：`webhook_msg_log` 表归 EXT-24（Batch 1.3 只引用不重复审计 — 用户决策 #6）

**不**包括：
- ❌ Phase 3.5 Diff Audit（重构类变更 Phase 4 前做）
- ❌ Phase 4 运行时计划（待 Phase 2 完成后启动）
- ❌ Phase 5 L4/L5a/L5b 验证（需运行项目）
- ❌ 修改任何业务代码（规则 6：审计阶段只记录不修复）
- ❌ 跑迁移脚本（规则 6 + 用户决策 I029 不跑迁移 — 沿用 I002/I017 模式）

---

**Phase 2 traceability-matrix 产出（Batch 1.1 + 1.2 + 1.3）完成。80 个 Feature 全栈追溯已落 `audit/traceability-matrix.md`。**

**待用户决策**（不进 Phase 3 前）：

1. **MISSING 11 个 Feature**（F056 / F057 / F060 / F061 / F063 / F064 / F066 / F067 / F075 / F076 / F080 — I029）— **确认不跑迁移**（沿用 I002/I017 模式）？Phase 5 实证将暴露 `TableNotFoundException` 异常作为负向证据。
2. **CONFLICT 3 个 Feature**（F071 / F077 / F078 — I026/I027/I030）— **接受 Phase 1 反向确认 SoT 错**（SoT §5 描述与代码不符）？Post-Audit 由人类决定（改文档 / 改代码 / 维持现状）。
3. **PARTIAL-NON-BLOCKER 10 个 Feature**（F062 / F063 / F065 / F066 / F069 / F073 / F074 / F075 / F077 / F078 / F079）— 是否全部接受「Phase 2 静态验证 + Phase 5 实测」策略？
4. **BACKLOG-001 1 个 Feature**（F072 — I033 `buildWebhookUrl` 孤儿工具）— 接受 Phase 7 Post-Audit 决定（删除 / 保留 / 改造为注册工具）？
5. **`webhook_msg_log` 表**（F077 / F078）— **接受 Batch 1.3 只引用不重复审计**（归 EXT-24 — 用户决策 #6）？