## Batch 1.3 — EXT-22 Google Maps Place Details + 有赞（25 Features）

> **范围来源**：`audit/baseline/scope-freeze-batch-1.3.md` §2.1 + `audit/feature-inventory.md` §1.3
> **EXT-* Operational Notes**：`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（**SoT**，**5 项与代码方向反转 + 1 项表 MISSING** — 见 §本批 I026-I030）
> **Batch 起点**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2 freeze commit）
> **DB 实际状态（Phase 2 静态验证）**：
> - `google_maps_config` **不存在**（db-snapshot.sql grep `CREATE TABLE google_maps_config` 0 匹配）
> - `google_maps_geocode_log` **不存在**（db-snapshot.sql grep `CREATE TABLE google_maps_geocode_log` 0 匹配）
> - `ecommerce_platform` 存在（db-snapshot.sql line 4434，**20 列**）— **缺 3 列 EXT-22 新列（`app_id/kdt_id/sign_secret`）**
> - `ecommerce_order` 存在（db-snapshot.sql line 4402，22 列）
> - `ecommerce_product` 存在（db-snapshot.sql line 4466）
> - `ecommerce_sync_log` 存在（db-snapshot.sql line 4491）
> - `webhook_msg_log` **不存在**（db-snapshot.sql grep 0 匹配 — **EXT-24 边界，Batch 1.3 只引用不重复审计**，用户决策 #6）
>
> **本批次关键决策**（用户 2026-09-13 22:45 确认）：
> 1. **I029 不跑迁移**（规则 6 + 与 I002/I017 一致性）→ F056/F057/F060/F066/F067/F075/F076/F080 标 **PARTIAL-BLOCKER**
> 2. **I026/I027/I030 CONFLICT 中立标接受**（沿用 I001/I018 模式）→ F071/F077/F078 标 **CONFLICT**
> 3. **F072 `buildWebhookUrl` 孤儿工具** → 标 **BACKLOG-001**（沿用 I013 dead code 模式）

---

### F056 — Google Maps / 实体 / GoogleMapsConfig（含 `enablePlaceDetails`）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F056 | `feature-inventory.md` §1.3.2 row 1 |
| 前端路由 | `/google-maps` | `frontend/meiyetec-web/src/router/index.ts:75`（推断；`router/index.ts` 含 `path: 'google-maps'`）|
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | |
| UI板块 | 「配置」tab | `views/google-maps/index.vue:13-31`（enablePlaceDetails el-switch）|
| Action | 编辑 `apiKey` + `enablePlaceDetails` → 点击「保存」 | |
| Handler | `saveConfig()` | `views/google-maps/index.vue:148-150`（推断） |
| API | `googleMapsApi.saveConfig(configForm)` | `frontend/meiyetec-web/src/api/google-maps.ts:2-3` |
| Method | POST | |
| Controller | `GoogleMapsService.saveConfig(@RequestBody GoogleMapsConfig body)` | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:67-91`** ⚠ **I019 命名异味**（同 Batch 1.1 I004 / Batch 1.2 I019 模式） |
| Service | 合并于 Controller | |
| Mapper | `GoogleMapsConfigMapper.insert / selectOne / updateById` | `backend/src/main/java/com/meiyetec/mapper/GoogleMapsConfigMapper.java` |
| DB表 | `google_maps_config` | **⚠ MISSING**（`db-snapshot.sql` 全文件 grep `CREATE TABLE google_maps_config` 0 匹配）|
| 关联表 | — | |
| 读/写 | W | |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | 普通租户（强制 `tenantId=TenantContextHolder.get().getTenantId()`）| `GoogleMapsService.java:68` |
| 软删除 | ❌（无 `deleted` 列）| `entity/GoogleMapsConfig.java:1-57`（实体含 `deleted` 但表不存在）|
| 审计字段 | `createTime / updateTime`（MyBatis auto-fill） | `entity/GoogleMapsConfig.java:54-57` |
| Static Status | L0（代码） / **L0（DB：表不存在）** | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ❌ PARTIAL | **I029** — `google_maps_config` 表 DDL 已写（`db/sql/migrate_ext_17_google_maps.sql:9`）但 **dev DB 未跑迁移** |
| Business Status | 11 字段 + `enablePlaceDetails: Integer`（L43，1=启用计费 / 0=默认）+ uk `uk_tenant_brand(tenant_id, brand_id, deleted)` | `entity/GoogleMapsConfig.java:11-57` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 全文件 grep `CREATE TABLE google_maps_config` 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **I029**（表 DDL 已就位但 dev DB 未跑迁移 — 用户决策：沿用 I002/I017 模式，**不跑迁移**，Post-Audit 统一执行） |
| Evidence | `audit/evidence/F056-google-maps-config/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

### F057 — Google Maps / 实体 + 日志 / GoogleMapsGeocodeLog

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F057 | `feature-inventory.md` §1.3.2 row 2 |
| 前端路由 | `/google-maps`（间接通过 F064 `/geocode/log`）| |
| 页面 | — | （前端无直接展示） |
| UI板块 | — | |
| Action | — | （日志实体 + 后端写入） |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `GoogleMapsService.geocode`（写入） + `GoogleMapsService.recentGeocodeLog`（读取） | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:159-170`**（`/geocode` 写入） + **`:225-237`**（`/geocode/batch` 写入） + **`:341-347`**（`/geocode/log` 读取）|
| Service | 合并于 Controller | |
| Mapper | `GoogleMapsGeocodeLogMapper.insert / selectList` | `backend/src/main/java/com/meiyetec/mapper/GoogleMapsGeocodeLogMapper.java` |
| DB表 | `google_maps_geocode_log` | **⚠ MISSING**（`db-snapshot.sql` grep 0 匹配）|
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 写入在 try/catch 内（失败仅 warn，不阻塞返回） | `GoogleMapsService.java:159-170` |
| 权限 | 普通租户 | `GoogleMapsService.java:341` |
| 软删除 | ❌（无 `deleted` 列）| `entity/GoogleMapsGeocodeLog.java:13-38`（实体无 deleted）|
| 审计字段 | `requestAt`（MyBatis auto-fill） | `entity/GoogleMapsGeocodeLog.java:35-37` |
| Static Status | L0（代码） / **L0（DB：表不存在）** | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ❌ PARTIAL | **I029** — `google_maps_geocode_log` 表 DDL 已写（`db/sql/migrate_ext_17_google_maps.sql:30`）但 **dev DB 未跑迁移** |
| Business Status | 11 字段（`tenantId/address/formattedAddress/lat/lng/placeId/status/errorMessage/durationMs/requestAt`）；每次 geocode 调用写一行；try/catch 包裹写日志（失败仅 warn）| `entity/GoogleMapsGeocodeLog.java:13-38` + `GoogleMapsService.java:159-170` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 全文件 grep 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **I029**（同上 F056 — 用户决策不跑迁移） |
| Evidence | `audit/evidence/F057-google-maps-geocode-log/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

### F058 — Google Maps / Client / Geocoding + Places HTTP 客户端

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F058 | `feature-inventory.md` §1.3.2 row 3 |
| 前端路由 | — | （无前端入口；由 F060-F063 调用） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | （后端 HTTP 客户端） |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | （被 F060/F061/F062/F063 委派调用） |
| Service | `GoogleMapsGeocodingClient.geocode / findPlace / placeDetails` + 3 URL 构造工具 + 私有 `httpGet` | **`backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsGeocodingClient.java:97-145`**（`geocode`）+ **`:155-194`**（`findPlace`）+ **`:209-292`**（`placeDetails` ⭐ EXT-22 计费）+ **`:51-72`**（3 URL 构造工具）|
| Mapper | — | |
| DB表 | — | （纯 HTTP 客户端，无 DB） |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证（GCP API Key 缺失 → 仅 Mock 降级） |
| DB Status | ✅ OK | 不依赖 DB |
| Business Status | 三 URL 常量：`GEOCODE_URL` / `PLACES_URL` / `PLACE_DETAILS_URL`；Place Details 一次性取 11 字段（`name,formatted_address,formatted_phone_number,international_phone_number,website,opening_hours,rating,user_ratings_total,business_status,geometry,types`）→ 始终命中 3 SKU（Basic+Contact+Atmosphere）；HTTP 客户端私有 `httpGet(url, connectTimeout=5000, readTimeout=15000)` | `GoogleMapsGeocodingClient.java:51-72` + `:97-292` |
| E2E Status | UNVERIFIED | Phase 5 用户手动注入 GCP API Key |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | GCP API Key 缺失 + Place Details 计费风险（EXT-22 §0.1 计费约束）；Phase 5 jshell 单元测试（用户决策 #2 — Mock 降级链路） |
| Evidence | `audit/evidence/F058-google-maps-client/` | 待建（Phase 5+） |
| Issue | （无独立 Issue；依赖 F060/F062） | |

---

### F059 — Google Maps / Config / Spring Bean 注册

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F059 | `feature-inventory.md` §1.3.2 row 4 |
| 前端路由 | — | （无前端入口；F058 依赖） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | （Spring 配置类） |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `@Configuration GoogleMapsClientConfig` + `@Bean googleMapsGeocodingClient(ObjectMapper jackson)` | **`backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsClientConfig.java:13-16`** |
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
| DB Status | ✅ OK | 不依赖 DB |
| Business Status | 注入 `ObjectMapper` 用于 JSON 解析；无 `@ConditionalOnMissingBean`（直接注册，无 fallback adapter — 与 CRM 集成不同）| `GoogleMapsClientConfig.java:13-16` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 无 fallback adapter（无 GCP Key 直接 NPE — 但 F061 `/geocode` 有 catch 兜底）；Phase 5 验证 Bean 注入 |
| Evidence | `audit/evidence/F059-google-maps-config/` | 待建（Phase 5+） |
| Issue | （无独立 Issue） | |

---

### F060 — Google Maps / Service / `/config` + `/health` 配置 CRUD

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F060 | `feature-inventory.md` §1.3.2 row 5 |
| 前端路由 | `/google-maps` | |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | |
| UI板块 | 「配置」tab | `views/google-maps/index.vue:13-31`（enablePlaceDetails el-switch + 计费警告 el-alert）|
| Action | 编辑 `apiKey` + `enablePlaceDetails` → 点击「保存」/ 点击「健康检查」| |
| Handler | `saveConfig()` + `loadConfig()` + `healthCheck()` | `views/google-maps/index.vue:148-150` |
| API | `googleMapsApi.saveConfig / getConfig / health` | `frontend/meiyetec-web/src/api/google-maps.ts:2-7` |
| Method | POST / GET | |
| Controller | `GoogleMapsService.saveConfig + getConfig + health` | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:67-91`**（POST /config）+ **`:94-110`**（GET /config）+ **`:113-131`**（POST /health）⚠ **I019 命名异味** |
| Service | 合并于 Controller | |
| Mapper | `GoogleMapsConfigMapper.insert / selectOne / updateById` | `backend/src/main/java/com/meiyetec/mapper/GoogleMapsConfigMapper.java` |
| DB表 | `google_maps_config` | **⚠ MISSING**（`db-snapshot.sql` grep 0 匹配）|
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无 `@Transactional` | |
| 权限 | 普通租户（强制 `tenantId=TenantContextHolder.get().getTenantId()`）| `GoogleMapsService.java:68, 95` |
| 软删除 | ❌ | |
| 审计字段 | `createTime / updateTime`（MyBatis auto-fill） | `entity/GoogleMapsConfig.java:54-57` |
| Static Status | L0（代码） / **L0（DB：表不存在）** | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ❌ PARTIAL | **I029** — 同 F056 |
| Business Status | `POST /config` 强制 `apiKey` 必填 + 默认值（`defaultLanguage=zh-CN`/`defaultRegion=cn`/`quotaDaily=10000`/`enabled=1`/`enablePlaceDetails=0`）（L78-82）；`GET /config` 输出 `apiKey="***"+substring(0, length-4)`（**仅遮蔽后 4 位，39 字符 apiKey 仍可猜前 35 位** — 与 SoT §5 一致）（L107）；`/health` 用 `geocode("Beijing")` 写回 `lastHealthAt/lastHealthMsg`（L118-128）| `GoogleMapsService.java:67-131` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 全文件 grep 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **I029**（`google_maps_config` 表缺失 — 用户决策不跑迁移）|
| Evidence | `audit/evidence/F060-google-maps-config-service/` | 待建（Phase 5+） |
| Issue | **I029 / I019** | |

---

### F061 — Google Maps / Service / `/geocode` 端点 + Mock 降级 + 日志

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F061 | `feature-inventory.md` §1.3.2 row 6 |
| 前端路由 | `/google-maps` | |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | |
| UI板块 | 「地理编码测试」tab | `views/google-maps/index.vue` |
| Action | 输入地址 → 点击「地理编码」 | |
| Handler | `geocodeStore()` | `views/google-maps/index.vue` |
| API | `googleMapsApi.geocodeStore(body)` | `frontend/meiyetec-web/src/api/google-maps.ts:8` |
| Method | POST | |
| Controller | `GoogleMapsService.geocode(@RequestBody Map body)` | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:138-180`** |
| Service | 合并于 Controller | |
| Mapper | `GoogleMapsConfigMapper.selectOne` + `GoogleMapsGeocodeLogMapper.insert` | 同 F056/F057 Mapper |
| DB表 | `google_maps_config`（读）+ `google_maps_geocode_log`（写）| **⚠ MISSING** 两表均缺失 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 写入在 try/catch 内（`catch (Exception ignored)`）| `GoogleMapsService.java:159-170` |
| 权限 | 普通租户 | `GoogleMapsService.java:139` |
| 软删除 | ❌ | |
| 审计字段 | `requestAt`（MyBatis auto-fill） | `entity/GoogleMapsGeocodeLog.java:35-37` |
| Static Status | L0（代码） / **L0（DB：表不存在）** | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证（GCP API Key 缺失 → Mock 降级）|
| DB Status | ❌ PARTIAL | **I029**（两表均缺失 — 用户决策不跑迁移）|
| Business Status | 三门禁：① `address` 必填（L142）② cfg 存在 + apiKey 非空 → 真实链路（L153-156）③ 否则 mock 降级（`mockGeocode` L259-269 用 `30+nextDouble()*10` 模拟经纬度）；每次写日志（L159-170）；返回 `source: GOOGLE_MAPS/MOCK` | `GoogleMapsService.java:138-180` + `:259-269` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 全文件 grep 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **I029**（两表缺失 — 用户决策不跑迁移；Phase 5 实证 `TableNotFoundException` 负向证据）|
| Evidence | `audit/evidence/F061-google-maps-geocode/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

### F062 — Google Maps / Service / `/place/details` 计费闸门 ⭐ EXT-22

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F062 | `feature-inventory.md` §1.3.2 row 7 |
| 前端路由 | `/google-maps` | |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | |
| UI板块 | 「Place Details 测试」tab | `views/google-maps/index.vue` |
| Action | 输入 placeId → 点击「查询 Place Details」 | |
| Handler | `getPlaceDetails()` | `views/google-maps/index.vue` |
| API | `googleMapsApi.getPlaceDetails(body)` | `frontend/meiyetec-web/src/api/google-maps.ts:13` |
| Method | POST | |
| Controller | `GoogleMapsService.placeDetails(@RequestBody Map body)` | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:191-232`** ⭐ |
| Service | 合并于 Controller | |
| Mapper | `GoogleMapsConfigMapper.selectOne`（仅读 cfg）| `backend/src/main/java/com/meiyetec/mapper/GoogleMapsConfigMapper.java` |
| DB表 | `google_maps_config`（读） | **⚠ MISSING**（I029）|
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | 普通租户 | `GoogleMapsService.java:192` |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0（代码） / **L0（DB：表不存在）** | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证（GCP API Key 缺失 + 表缺失 → 多层降级）|
| DB Status | ❌ PARTIAL | **I029**（cfg 表缺失 — 用户决策不跑迁移）|
| Business Status | **三段门禁**（L194-217）：① `placeId` 必填 → 否则 `BAD_REQUEST`；② cfg 存在 + apiKey 非空 → 否则 `NOT_FOUND`；③ **`enablePlaceDetails == 1` → 否则 `PERMISSION_DENIED`**（**唯一计费闸门** — SoT §0.3 Step 2）；调用 `geocodingClient.placeDetails(apiKey, placeId, lang)`；返回 11 fields（L218-230）| `GoogleMapsService.java:191-232` |
| E2E Status | UNVERIFIED | Phase 5+（GCP API Key + 表都需要）|
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 全文件 grep 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I031**（`/place/details` 未写 `google_maps_geocode_log` — 仅 `/geocode` 写日志）+ **I029**（表缺失）|
| Evidence | `audit/evidence/F062-google-maps-place-details/runtime-evidence.md` | 待建（Phase 5+） |
| Issue | **I031 / I029** | |

---

### F063 — Google Maps / Service / `/geocode/batch` admin-only

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F063 | `feature-inventory.md` §1.3.2 row 8 |
| 前端路由 | — | （无前端入口） |
| 页面 | — | |
| UI板块 | — | |
| Action | — | （admin-only 后端端点） |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `GoogleMapsService.geocodeBatch(@RequestBody Map body)` | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:259-318`** |
| Service | 合并于 Controller | |
| Mapper | `GoogleMapsConfigMapper.selectOne` + `GoogleMapsGeocodeLogMapper.insert` | 同 F061 Mapper |
| DB表 | `google_maps_config`（读）+ `google_maps_geocode_log`（写每条）| **⚠ MISSING** 两表均缺失 |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 写入在 try/catch 内（`catch (Exception ignored)`）| `GoogleMapsService.java:296-308` |
| 权限 | **admin 守卫**：仅 `SUPER_ADMIN/PLATFORM_ADMIN/HQ_ADMIN` 可调（否则 `PERMISSION_DENIED`）| `GoogleMapsService.java:262-264` |
| 软删除 | ❌ | |
| 审计字段 | `requestAt`（MyBatis auto-fill）| `entity/GoogleMapsGeocodeLog.java:35-37` |
| Static Status | L0（代码） / **L0（DB：表不存在）** | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ❌ PARTIAL | **I029**（两表均缺失 — 用户决策不跑迁移）|
| Business Status | 单次最多 50 条（L271-273）；复用 `/geocode` 配置 + mock 降级逻辑；响应 `{total, ok, fail, durationMs, source, results[]}`（L309-316）| `GoogleMapsService.java:259-318` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 全文件 grep 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I032**（admin-only 但**无 audit log** — SoT §5 "批量 geocode 无 audit log" 已知问题一致）+ **I029**（表缺失）|
| Evidence | `audit/evidence/F063-google-maps-geocode-batch/runtime-evidence.md` | 待建（Phase 5+） |
| Issue | **I032 / I029** | |

---

### F064 — Google Maps / Service / `/stores` + `/heatmap` + `/geocode/log`

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F064 | `feature-inventory.md` §1.3.2 row 9 |
| 前端路由 | `/google-maps` | |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | |
| UI板块 | 「门店地图」tab | `views/google-maps/index.vue` |
| Action | 进入「门店地图」tab → 自动加载门店 + 热力图 | |
| Handler | `listStores()` + `getMemberHeatmap()` | `views/google-maps/index.vue` |
| API | `googleMapsApi.listStores / getMemberHeatmap` | `frontend/meiyetec-web/src/api/google-maps.ts:10, 14` |
| Method | GET | |
| Controller | `GoogleMapsService.listStores + heatmap + recentGeocodeLog` | **`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:320-322`**（GET /stores）+ **`:325-338`**（GET /heatmap）+ **`:341-347`**（GET /geocode/log）|
| Service | 合并于 Controller | |
| Mapper | `GoogleMapsGeocodeLogMapper.selectList`（仅 `/geocode/log` 用）| `backend/src/main/java/com/meiyetec/mapper/GoogleMapsGeocodeLogMapper.java` |
| DB表 | `google_maps_geocode_log`（仅 `/geocode/log` 读）| **⚠ MISSING** |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | 普通租户 | `GoogleMapsService.java:342` |
| 软删除 | ❌ | |
| 审计字段 | — | |
| Static Status | L0（代码） / **L0（DB：表不存在）** | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ❌ PARTIAL | **I029**（表缺失 — 用户决策不跑迁移）|
| Business Status | 演示数据：5 门店（北京三里屯/上海徐汇/深圳南山/纽约/东京）— 静态常量 STORES（L38-46）；heat map 200 点用 random 偏移 + weight（L325-338）；`/geocode/log` 按 `tenantId` 过滤 + `id DESC LIMIT 100`（L341-347）| `GoogleMapsService.java:320-347` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 全文件 grep 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **I029**（`google_maps_geocode_log` 表缺失）|
| Evidence | `audit/evidence/F064-google-maps-stores-heatmap/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

### F065 — Google Maps / 前端 / 配置 + Place Details 测试 UI

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F065 | `feature-inventory.md` §1.3.2 row 10 |
| 前端路由 | `/google-maps` | |
| 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | `views/google-maps/index.vue:1-175` |
| UI板块 | **4 tab**：配置 / 门店地图 / 地理编码测试 / Place Details 测试 | `views/google-maps/index.vue:1-175` |
| Action | 切换 tab → 编辑表单 → 点击按钮 | |
| Handler | `saveConfig / loadConfig / healthCheck / geocodeStore / listStores / getMemberHeatmap / getPlaceDetails` | `views/google-maps/index.vue` 各 handler |
| API | `googleMapsApi` 6 端点 | `frontend/meiyetec-web/src/api/google-maps.ts:1-18` |
| Method | POST / GET | |
| Controller | — | （无新后端，对应 F060-F063）|
| Service | — | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ✅ OK | 不依赖 DB（前端纯展示）|
| Business Status | `enablePlaceDetails` el-switch + 计费警告 el-alert **已实现**（`index.vue:13-31`）；⚠ **configForm 未传 `defaultLanguage/defaultRegion/quotaDaily/enabled`**（前端表单只含 apiKey + defaultZoom + enablePlaceDetails）— `saveConfig` 调用时可能覆盖后端默认值为 null（I028 部分反向）| `views/google-maps/index.vue:13-31` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I028**（部分反向：`enablePlaceDetails` UI 已实现，但 `configForm` 字段不全 — `saveConfig` 调用时可能把默认字段覆盖为 null）|
| Evidence | `audit/evidence/F065-google-maps-frontend/` | 待建（Phase 5+） |
| Issue | **I028** | |

---

### F066 — Ecommerce / 实体 / EcommercePlatform（含 `appId/kdtId/signSecret` 三新字段）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F066 | `feature-inventory.md` §1.3.2 row 11 |
| 前端路由 | `/ecommerce` | |
| 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | |
| UI板块 | — | （间接通过 F079/F080）|
| Action | — | |
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | （被 F075/F076 委派调用）|
| Service | — | |
| Mapper | `EcommercePlatformMapper.insert / selectOne / selectList / updateById` | `backend/src/main/java/com/meiyetec/mapper/EcommercePlatformMapper.java` |
| DB表 | `ecommerce_platform` | **⚠ PARTIAL**（`db-snapshot.sql` line 4434，**20 列** — **缺 3 列 EXT-22 新列**：`app_id/kdt_id/sign_secret`）|
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无 `@Transactional` | `service/EcommerceService.java:62-72` |
| 权限 | 普通租户 | |
| 软删除 | ❌（无 `deleted` 列）| `entity/EcommercePlatform.java:1-41` |
| 审计字段 | `createdAt / updatedAt`（MyBatis auto-fill）| `entity/EcommercePlatform.java:38-41` |
| Static Status | L0（代码） / **L0（DB：缺 3 列）** | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ❌ PARTIAL | **I029**（`ecommerce_platform` 表存在但 **缺 3 列 EXT-22 新列** — DDL `migrate_ext_22:11` 已写但 dev DB 未跑迁移；Phase 5 实证将抛 `Unknown column 'app_id' / 'sign_secret' / 'kdt_id'`）|
| Business Status | 26 字段 + 新增 3 字段（L19/L21/L23）：`appId`(EXT-22) + `kdtId`(EXT-22) + `signSecret`(EXT-22)；**注意**：`appKey` ≠ `appId`（OAuth2 client_id vs 有赞 AppID — SoT §0.4 强调）；`signSecret` 用于 webhook 验签；⚠ `tenantId=0L` 默认（`EcommerceService.createPlatform` L73）| `entity/EcommercePlatform.java:11-41` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 4434（20 列 — 缺 `app_id/kdt_id/sign_secret`）| |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **I029**（表存在但缺 3 列 — 用户决策不跑迁移）|
| Evidence | `audit/evidence/F066-ecommerce-platform/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

### F067 — Ecommerce / 实体 / EcommerceOrder + EcommerceProduct + EcommerceSyncLog

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F067 | `feature-inventory.md` §1.3.2 row 12 |
| 前端路由 | — | — |
| 页面 | — | — |
| UI板块 | — | — |
| Action | — | （间接通过 F076 sync 链路）|
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | （被 F076 sync 委派调用）|
| Service | — | |
| Mapper | `EcommerceOrderMapper / EcommerceProductMapper / EcommerceSyncLogMapper` | `backend/src/main/java/com/meiyetec/mapper/{EcommerceOrder,EcommerceProduct,EcommerceSyncLog}Mapper.java` |
| DB表 | `ecommerce_order` (db-snapshot.sql:4402, 22 列) + `ecommerce_product` (db-snapshot.sql:4466) + `ecommerce_sync_log` (db-snapshot.sql:4491) | **✅ 三表均已存在**（与 EXT-22 ALTER 无关 — I029 不影响）|
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | F076 sync `@Transactional` | `service/EcommerceService.java:108, 225` |
| 权限 | 普通租户 | |
| 软删除 | ❌（三表均无 `deleted` 列）| `entity/EcommerceOrder.java:1-44` + `EcommerceProduct.java` + `EcommerceSyncLog.java:1-28` |
| 审计字段 | `createdAt / updatedAt`（MyBatis auto-fill）| `entity/EcommerceOrder.java:42-44` |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ✅ OK | 三表均已存在 |
| Business Status | `ecommerce_order` 19 字段含 `externalOrderId/orderNo/buyerName/buyerPhone/amount/shippingFee/totalAmount/address/trackingNo/itemsJson/rawPayload/orderTime`；`ecommerce_sync_log` 9 字段含 `successCount/failCount/durationMs/status/errorMessage`；共用于 F076 sync 链路 | `entity/EcommerceOrder.java:11-44` + `EcommerceSyncLog.java:11-28` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 4402 + 4466 + 4491 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 三表均存在但依赖 F066 字段（订单 upsert 链路读取 `appId/kdtId/signSecret` 用于 webhook 反查 — F077 链路；若无三字段则 webhook 失败）|
| Evidence | `audit/evidence/F067-ecommerce-entities/` | 待建（Phase 5+） |
| Issue | **I029**（间接 — 依赖 F066 字段）| |

---

### F068 — Ecommerce / Adapter / EcommerceAdapter 接口契约

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F068 | `feature-inventory.md` §1.3.2 row 13 |
| 前端路由 | — | — |
| 页面 | — | — |
| UI板块 | — | — |
| Action | — | （接口契约）|
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `EcommerceAdapter` 接口 + 4 DTO（`AuthResult / ProductPage / OrderPage / HealthStatus`）| **`backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapter.java:1-122`** |
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
| DB Status | ✅ OK | 不依赖 DB |
| Business Status | 与 `CrmAdapter` 同源设计；屏蔽有赞/微店/淘宝/Mock 实现差异；Runtime 由 Spring 注入指定实现（`YouzanEcommerceAdapter`/`MockEcommerceAdapter`）| `EcommerceAdapter.java:1-122` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 接口契约完整；**⚠ `EcommerceAdapter` 未声明 tenantId 隔离**（与 CrmAdapter 一致）|
| Evidence | `audit/evidence/F068-ecommerce-adapter-interface/` | 待建（Phase 5+） |
| Issue | （无独立 Issue） | |

---

### F069 — Ecommerce / Adapter / YouzanEcommerceAdapter.authenticate (OAuth2 + Token 缓存)

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F069 | `feature-inventory.md` §1.3.2 row 14 |
| 前端路由 | — | — |
| 页面 | — | — |
| UI板块 | — | — |
| Action | — | （被 F075/F076 委派调用）|
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `YouzanEcommerceAdapter.authenticate(EcommercePlatform cfg)` + Token 缓存（`tokenExpireAt > now()+2min`）| **`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:170-211`** |
| Mapper | — | |
| DB表 | `ecommerce_platform`（写回 `accessToken/tokenExpireAt`）| **⚠ PARTIAL**（表存在但缺三字段 — F066 I029）|
| 关联表 | — | |
| 读/写 | W（缓存写回） | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证（有赞凭据缺失 → Mock fallback）|
| DB Status | ❌ PARTIAL | **I029**（间接 — 写回字段存在但其他 EXT-22 字段缺失）|
| Business Status | OAuth2 Client-Credentials：`POST /api/oauthentry/grant/token` body=`client_id={appKey}&client_secret={appSecret}&grant_type=client_credential`（L184-189）；缓存失效重签发；`buildTokenRequestBody` (L59-63) + `DEFAULT_API_BASE` 常量（L36）；HTTP POST 5000ms connect / 15000ms read（L298）| `YouzanEcommerceAdapter.java:170-211` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 4434（缺三字段 — I029）| |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I029**（间接）+ 有赞 OAuth 凭据缺失（Phase 5 Mock 降级）|
| Evidence | `audit/evidence/F069-youzan-authenticate/` | 待建（Phase 5+） |
| Issue | **I029**（间接）| |

---

### F070 — Ecommerce / Adapter / YouzanEcommerceAdapter 商品 / 订单拉取 + 健康检查

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F070 | `feature-inventory.md` §1.3.2 row 15 |
| 前端路由 | — | — |
| 页面 | — | — |
| UI板块 | — | — |
| Action | — | （被 F076 sync 委派调用）|
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `YouzanEcommerceAdapter.fetchProducts / fetchOrders / healthCheck` | **`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:213-244`**（`fetchProducts`）+ **`:248-284`**（`fetchOrders`）+ **`:286-303`**（`healthCheck`）|
| Mapper | — | |
| DB表 | — | （仅 HTTP 调用）|
| 关联表 | — | |
| 读/写 | R（仅读有赞 API）| |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证（有赞凭据缺失 → Mock fallback）|
| DB Status | ✅ OK | 不依赖 DB |
| Business Status | 商品：`GET /items/list?page_no=1&page_size=50&access_token=...`（L213-244）；订单：`GET /trades/sold_get?start_update_time=...&end_update_time=...`（L248-284）；pageSize 上限 100（`Math.min(pageSize, 100)`，L73-77）；**响应总条数 = `root.total_results`**；`buildItemsListUrl` (L66-71) + `buildTradesSoldGetUrl` (L74-83) | `YouzanEcommerceAdapter.java:213-303` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | 有赞 OAuth 凭据缺失（Phase 5 Mock 降级）；`fetchProducts` / `fetchOrders` 返回 `items=[]` / `orders=[]`（L222 / L256）|
| Evidence | `audit/evidence/F070-youzan-fetch/` | 待建（Phase 5+） |
| Issue | （无独立 Issue） | |

---

### F071 — Ecommerce / Adapter / YouzanEcommerceAdapter.verifyWebhookSignature ⭐ 签名算法

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F071 | `feature-inventory.md` §1.3.2 row 16 |
| 前端路由 | — | — |
| 页面 | — | — |
| UI板块 | — | — |
| Action | — | （被 F077 webhook 链路调用：`YouzanWebhookController.java:121`）|
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `public static boolean verifyWebhookSignature(String signSecret, String rawBody, String signature)` | **`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:146-168`** |
| Mapper | — | |
| DB表 | — | （纯算法）|
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | Phase 5 jshell 单元测试（3 组 payload：正常 / 篡改 body / 篡改 signature）|
| DB Status | ✅ OK | 不依赖 DB |
| Business Status | 算法：`expected = lowercase( hex( md5( signSecret + rawBody ) ) )`（L153-160）；比较 `equalsIgnoreCase`（防御性）（L161）；`null/empty` 任一入参 → false（L147-149）；异常 catch → false（L165-167）；**与 SoT §0.5 完全一致**；**已有调用方**（F077 `YouzanWebhookController.java:121`）— **与 SoT §5 "是孤儿代码" 描述反向** | `YouzanEcommerceAdapter.java:146-168` |
| E2E Status | UNVERIFIED | Phase 5+ jshell 单元测试 |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **CONFLICT** | **I026**（中立标 — SoT §5 vs 代码反向：`verifyWebhookSignature` 实际被 `YouzanWebhookController.java:121` 调用；Fix Phase = Post-Audit 人类决策 — 改 SoT / 改代码 / 维持现状） |
| Evidence | `audit/evidence/F071-youzan-verify-signature/runtime-evidence.md` | 待建（Phase 5+ jshell 验证） |
| Issue | **I026** | |

---

### F072 — Ecommerce / Adapter / YouzanEcommerceAdapter.buildWebhookUrl ⚠ BACKLOG-001

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F072 | `feature-inventory.md` §1.3.2 row 17 |
| 前端路由 | — | — |
| 页面 | — | — |
| UI板块 | — | — |
| Action | — | （孤儿工具 — **Phase 1 grep 全代码 0 匹配调用方**）|
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `static String buildWebhookUrl(String baseUrl, String kdtId)` | **`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:106-114`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | （孤儿工具 — 无调用方，运行时不被触发）|
| DB Status | ✅ OK | 不依赖 DB |
| Business Status | 构造 `https://{domain}/webhook/youzan?kdt_id={kdtId}`（L106-114）；**Phase 1 grep 全代码 0 匹配调用方**（仅声明 + 实现）；⚠ **BACKLOG-001**（沿用 I013 dead code 模式）；SoT §5 描述"是孤儿工具"确认一致（**非漂移**）| `YouzanEcommerceAdapter.java:106-114` |
| E2E Status | UNVERIFIED | Phase 7 Post-Audit 决定（删除 / 保留 / 改造为注册工具）|
| Verified Commit | — | Phase 7 |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **BACKLOG-001** | **I033**（`buildWebhookUrl` 孤儿工具 — 沿用 I013 dead code 模式；Phase 7 Post-Audit 决定） |
| Evidence | `audit/evidence/F072-youzan-build-webhook-url/static-result.md` | 待建（Phase 5+） |
| Issue | **I033** | |

---

### F073 — Ecommerce / Adapter / MockEcommerceAdapter fallback

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F073 | `feature-inventory.md` §1.3.2 row 18 |
| 前端路由 | — | — |
| 页面 | — | — |
| UI板块 | — | — |
| Action | — | （Fallback adapter）|
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `@Component @ConditionalOnMissingBean(name="youzanEcommerceAdapter") MockEcommerceAdapter` | **`backend/src/main/java/com/meiyetec/ecommerce/MockEcommerceAdapter.java:18-78`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | R | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | Phase 5 验证 Bean 条件激活 |
| DB Status | ✅ OK | 不依赖 DB |
| Business Status | 3 样本商品（L21-25）+ 2 样本订单（L27-31）；`authenticate` 返回 `MOCK-TOKEN-{UUID}`；`sleep(50ms/80ms)` 模拟延迟；`healthCheck` 返回 `MOCK_OK`；**激活条件**：`@ConditionalOnMissingBean(name="youzanEcommerceAdapter")`（**注意 Bean 名是 youzanEcommerceAdapter** — 与 I012 模式同 Bean 条件冲突风险）| `MockEcommerceAdapter.java:18-78` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | Bean 条件冲突风险（与 Batch 1.1 I012 同模式 — 若 `youzanEcommerceAdapter` Bean 存在但 mock 也注册 → Router 走 youzan，否则 mock）；Phase 5 验证 Bean 注入优先级 |
| Evidence | `audit/evidence/F073-mock-ecommerce-adapter/` | 待建（Phase 5+） |
| Issue | （与 I012 同模式 — 未标独立 Issue） | |

---

### F074 — Ecommerce / Router / EcommerceAdapterRouter

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F074 | `feature-inventory.md` §1.3.2 row 19 |
| 前端路由 | — | — |
| 页面 | — | — |
| UI板块 | — | — |
| Action | — | （被 F075/F076 委派调用）|
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | — | |
| Service | `EcommerceAdapterRouter.getAdapter(platform)` + `hasAdapter(platform)` + `listPlatforms()` | **`backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapterRouter.java:19-37`** |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | Phase 5 验证 Router 分发 |
| DB Status | ✅ OK | 不依赖 DB |
| Business Status | 与 `CrmAdapterRouter` 同源设计；`platform` toUpperCase 查 Map；miss → fallback `getAdapter("MOCK")`；null → fallback；`listPlatforms` 返回 sorted List | `EcommerceAdapterRouter.java:19-37` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | Router fallback OK；**⚠ 未声明 tenantId 隔离**（与 CrmAdapterRouter 同模式）|
| Evidence | `audit/evidence/F074-ecommerce-router/` | 待建（Phase 5+） |
| Issue | （与 Batch 1.1 I018 同模式 — 未标独立 Issue） | |

---

### F075 — Ecommerce / Service / 平台 CRUD + Health（4 端点）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F075 | `feature-inventory.md` §1.3.2 row 20 |
| 前端路由 | `/ecommerce` | |
| 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | |
| UI板块 | 「平台管理」tab | `views/ecommerce/index.vue` |
| Action | 点击「添加平台」→ 填写表单 → 点击「保存」| |
| Handler | `doAdd()` + `loadList()` + `updatePlatform()` + `healthCheck()` | `views/ecommerce/index.vue` |
| API | `ecommerceApi.createPlatform / listPlatforms / updatePlatform / platformHealth` | `frontend/meiyetec-web/src/api/ecommerce.ts:2-5, 17` |
| Method | POST / GET | |
| Controller | `EcommerceService.createPlatform + listPlatforms + updatePlatform + platformHealth` | **`backend/src/main/java/com/meiyetec/service/EcommerceService.java:65-72`**（POST /platform）+ **`:75-80`**（GET /platform/list）+ **`:83-89`**（POST /platform/{id}）+ **`:92-94`**（GET /platform/{id}/health）⚠ **I019 命名异味** |
| Service | 合并于 Controller | |
| Mapper | `EcommercePlatformMapper.insert / selectList / selectById / updateById` | `backend/src/main/java/com/meiyetec/mapper/EcommercePlatformMapper.java` |
| DB表 | `ecommerce_platform` | **⚠ PARTIAL**（db-snapshot.sql:4434，缺 3 列 — F066 I029）|
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ❌ 无 `@Transactional` | `EcommerceService.java:65-89` |
| 权限 | 普通租户 | （**⚠ 不隔离** — `tenantId=0L` 默认 L73；同 Batch 1.1 I019 模式）|
| 软删除 | ❌ | |
| 审计字段 | `createdAt / updatedAt`（MyBatis auto-fill）| `entity/EcommercePlatform.java:38-41` |
| Static Status | L0（代码） / **L0（DB：缺 3 列）** | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ❌ PARTIAL | **I029**（表存在但缺 3 列 — 用户决策不跑迁移）|
| Business Status | `createPlatform` 强制 `platform` 必填（L66-69）；`updatePlatform` 用 `setId(id) + updateById`（L83-89）；health 走 `adapter.healthCheck`（L92-94）| `EcommerceService.java:65-94` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 4434（缺三字段 — I029）| |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **I029**（间接 — 表缺 3 列 `app_id/kdt_id/sign_secret`，写库时抛 `Unknown column`） |
| Evidence | `audit/evidence/F075-ecommerce-platform-crud/` | 待建（Phase 5+） |
| Issue | **I029 / I019** | |

---

### F076 — Ecommerce / Service / 商品 / 订单 sync（4 端点 + upsert）

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F076 | `feature-inventory.md` §1.3.2 row 21 |
| 前端路由 | `/ecommerce` | |
| 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | |
| UI板块 | 「商品同步」+「订单同步」tab | `views/ecommerce/index.vue` |
| Action | 点击「同步商品」/「同步订单」 | |
| Handler | `syncProducts()` + `syncOrders()` | `views/ecommerce/index.vue` |
| API | `ecommerceApi.syncProducts / syncOrders / listProducts / listOrders` | `frontend/meiyetec-web/src/api/ecommerce.ts:10-15` |
| Method | POST / GET | |
| Controller | `EcommerceService.syncProducts + syncOrders + listProducts + listOrders` | **`backend/src/main/java/com/meiyetec/service/EcommerceService.java:97-105`**（GET /product/list）+ **`:108-150`**（POST /product/sync/{pid} ⭐ `@Transactional`）+ **`:213-222`**（GET /order/list）+ **`:225-289`**（POST /order/sync/{pid} ⭐ `@Transactional`）+ 私有 `upsertProduct`（`:152-200`）+ 私有 `upsertOrder`（`:291-372`）⚠ **I019 命名异味** |
| Service | 合并于 Controller | |
| Mapper | `EcommerceProductMapper / EcommerceOrderMapper / EcommerceSyncLogMapper / EcommercePlatformMapper` | `backend/src/main/java/com/meiyetec/mapper/{EcommerceProduct,EcommerceOrder,EcommerceSyncLog,EcommercePlatform}Mapper.java` |
| DB表 | `ecommerce_product` + `ecommerce_order` + `ecommerce_sync_log`（✅ 三表存在）+ `ecommerce_platform`（⚠ 缺 3 列 — I029）| |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ✅ sync `@Transactional`（L108, L225）| |
| 权限 | 普通租户 | |
| 软删除 | ❌ | |
| 审计字段 | `createdAt / updatedAt`（MyBatis auto-fill）| |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ❌ PARTIAL | **I029**（`ecommerce_platform` 缺 3 列 — 影响 token 缓存写回）|
| Business Status | upsert by `(platform, external_product_id/external_order_id)`；写入 `ecommerce_sync_log(syncType=PRODUCT/ORDER, status=RUNNING→SUCCESS/PARTIAL/FAILED, successCount/failCount/durationMs)`；`persistTokenCache` 仅刷 `accessToken/tokenExpireAt` 不覆盖其他字段；订单字段映射兼容有赞 + 微店（`receiver_*` / `buyer_*`）| `EcommerceService.java:97-372` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 4402 + 4466 + 4491 + 4434（缺 3 列 — I029）| |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **I029**（间接 — 依赖 `ecommerce_platform` 三字段做 token 缓存）；Phase 5 实证 `Unknown column 'app_id'` 负向证据 |
| Evidence | `audit/evidence/F076-ecommerce-sync/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

### F077 — Ecommerce / Webhook / YouzanWebhookController `/webhook/youzan` ⭐ CONFLICT

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F077 | `feature-inventory.md` §1.3.2 row 22 |
| 前端路由 | `/webhook/youzan`（push 端，无前端）| |
| 页面 | — | — |
| UI板块 | — | — |
| Action | — | （有赞后台 push → 后端 webhook 接收）|
| Handler | — | |
| API | — | |
| Method | POST | |
| Controller | `YouzanWebhookController.HandleYouzan(...)` | **`backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java:76-203`** ⭐ `@PostMapping("/webhook/youzan") @Transactional`（**未指定 `rollbackFor` — 与 I009 模式同 — 见 I034**）|
| Service | — | |
| Mapper | `EcommercePlatformMapper.selectOne` + `WebhookMsgLogMapper.selectOne/insert` + `EcommerceOrderMapper.insert/updateById` | `backend/src/main/java/com/meiyetec/mapper/{EcommercePlatform,WebhookMsgLog,EcommerceOrder}Mapper.java` |
| DB表 | `ecommerce_platform`（读 by `kdt_id` — ⚠ 缺 3 列 I029）+ `webhook_msg_log`（写 — ⚠ MISSING EXT-24 边界）+ `ecommerce_order`（upsert — ✅ 存在）| |
| 关联表 | — | |
| 读/写 | RW | |
| 事务 | ✅ `@Transactional` 但**未指定 `rollbackFor`**（与 Batch 1.1 I009 同模式 — 见 I034）| `YouzanWebhookController.java:76` |
| 权限 | push 端无 tenantId 校验（webhook 推送） | |
| 软删除 | ❌ | |
| 审计字段 | `createdAt`（MyBatis auto-fill）| `entity/EcommerceOrder.java:42-44` |
| Static Status | L0（代码） / **L0（DB：缺 3 列 + 缺表）** | 代码静态可达 |
| Runtime Status | UNVERIFIED | Phase 5 jshell + Mock push 验证 |
| DB Status | ❌ PARTIAL | **I029**（`ecommerce_platform` 缺 3 列 `kdt_id/sign_secret` — F077 强依赖）+ **`webhook_msg_log` MISSING**（EXT-24 边界）|
| Business Status | **完整处理流程**（L83-200）：① ContentCachingRequestWrapper 读 rawBody（L85-95）② 反查 platform by `kdt_id`（L107-114）③ `verifyWebhookSignature` 验签（L121）④ msg_id 幂等去重（L137-149）⑤ upsert `ecommerce_order`（L182-186）⑥ 写 `webhook_msg_log`（L195-200）；**SoT §5 描述"未实现"反向**（I027 中立标） | `YouzanWebhookController.java:76-203` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 4434（缺 3 列 I029）+ 全文件 grep `webhook_msg_log` 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **CONFLICT** | **I027**（中立标 — SoT §5 "webhook 接收端缺失" vs 代码反向：`YouzanWebhookController` 已完整实现；Fix Phase = Post-Audit 人类决策）+ **I034**（事务未指定 rollbackFor）+ **I029**（依赖字段缺失）|
| Evidence | `audit/evidence/F077-youzan-webhook-controller/runtime-evidence.md` | 待建（Phase 5+） |
| Issue | **I027 / I034 / I029** | |

---

### F078 — Ecommerce / Webhook / msg_id 幂等去重 ⭐ CONFLICT

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F078 | `feature-inventory.md` §1.3.2 row 23 |
| 前端路由 | — | （间接通过 F077 webhook 链路）|
| 页面 | — | — |
| UI板块 | — | — |
| Action | — | （F077 链路中的幂等分支）|
| Handler | — | |
| API | — | |
| Method | — | |
| Controller | `YouzanWebhookController.HandleYouzan`（L136-149 幂等分支）| **`backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java:136-149`** |
| Service | — | |
| Mapper | `WebhookMsgLogMapper.selectOne`（仅查） | `backend/src/main/java/com/meiyetec/mapper/WebhookMsgLogMapper.java` |
| DB表 | `webhook_msg_log`（读）| **⚠ MISSING**（EXT-24 边界 — 用户决策 #6 引用不重审）|
| 关联表 | — | |
| 读/写 | R | |
| 事务 | ✅ 在 F077 `@Transactional` 内 | |
| 权限 | — | |
| 软删除 | ❌ | |
| 审计字段 | `createdAt`（MyBatis auto-fill）| `entity/WebhookMsgLog.java:1-27`（实体类）|
| Static Status | L0（代码） / **L0（DB：表不存在）** | 代码静态可达 |
| Runtime Status | UNVERIFIED | Phase 5 jshell + Mock push 验证 |
| DB Status | ❌ MISSING | `webhook_msg_log` 表 DDL 在 `db/sql/migrate_ext_24_webhook_msg_log.sql:8`（**EXT-24 边界 — Batch 1.3 只引用不重审**）|
| Business Status | 查询 `selectOne(... eq(source, "YOUZAN") eq(msgId, msgId) limit 1)`（L138-141）；命中 → 返回 `duplicate=true + previousStatus + logId`（HTTP 200）（L142-149）；**SoT §5 "没有 webhook 重放保护" 描述反向**（I030 中立标）；⚠ **未加重放 nonce 校验**（仅查 msg_id 不查 timestamp / nonce 防止过期重放 — I035）| `YouzanWebhookController.java:136-149` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 全文件 grep `webhook_msg_log` 0 匹配 | |
| Last Verified At | — | |
| **Final Result** | **CONFLICT** | **I030**（中立标 — SoT §5 "无重放保护" vs 代码反向：`webhook_msg_log` 幂等已实现；Fix Phase = Post-Audit 人类决策）+ **I035**（未加重放 nonce / timestamp）+ **`webhook_msg_log` 表归 EXT-24**（用户决策 #6 — Batch 1.3 只引用不重复审计）|
| Evidence | `audit/evidence/F078-webhook-msg-id-idempotent/runtime-evidence.md` | 待建（Phase 5+） |
| Issue | **I030 / I035** | |

---

### F079 — Ecommerce / 前端 / 3 tab + 添加平台对话框

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F079 | `feature-inventory.md` §1.3.2 row 24 |
| 前端路由 | `/ecommerce` | |
| 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | `views/ecommerce/index.vue:1-95` |
| UI板块 | **3 tab**：平台管理 / 商品同步 / 订单同步 | `views/ecommerce/index.vue:1-95` |
| Action | 切换 tab → 点击「添加平台」 → 填写 platform/shopName/shopId → 点击「保存」 | |
| Handler | `doAdd()` + `loadList()` + `syncProducts()` + `syncOrders()` | `views/ecommerce/index.vue` |
| API | `ecommerceApi` 8 端点 | `frontend/meiyetec-web/src/api/ecommerce.ts:1-19` |
| Method | POST / GET | |
| Controller | — | （无新后端，对应 F075/F076）|
| Service | — | |
| Mapper | — | |
| DB表 | — | |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | 普通租户 | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0 | 代码静态可达 |
| Runtime Status | UNVERIFIED | 未启动验证 |
| DB Status | ✅ OK | 不依赖 DB（前端纯展示）|
| Business Status | 平台管理（`id/platform/shopName/shopId/lastSyncAt/status` el-table）；商品同步（`id/platformId/productId/productName/price/stock`）；订单同步（`id/platformId/orderNo/amount/status`）；添加对话框（`platform: WEIDIAN/YOUZAN/TAOBAO/DOUYIN` + `shopName` + `shopId`）（`index.vue:60-77`）；⚠ **缺 `appId/kdtId/signSecret` 字段 UI**（与 SoT §5 一致 — I028 PARTIAL-NON-BLOCKER）；⚠ 缺 `clientId/clientSecret/webhookUrl` 等配置字段 | `views/ecommerce/index.vue:60-77` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | — | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-NON-BLOCKER** | **I028**（前端缺 UI：`appId/kdtId/signSecret` 字段 — 与 SoT §5 一致；用户决策：接受现状，启用通过 SQL 直写）|
| Evidence | `audit/evidence/F079-ecommerce-frontend/` | 待建（Phase 5+） |
| Issue | **I028** | |

---

### F080 — Frontend API + DB 迁移 / ecommerce.ts + google-maps.ts + EXT-17/22/24 DDL

| 维度 | 值 | 证据 |
|---|---|---|
| Feature ID | F080 | `feature-inventory.md` §1.3.2 row 25 |
| 前端路由 | — | — |
| 页面 | — | — |
| UI板块 | — | — |
| Action | — | （前端 API 封装 + 后端 DDL 静态验证）|
| Handler | — | |
| API | `ecommerceApi` 8 端点 + `googleMapsApi` 6 端点 | `frontend/meiyetec-web/src/api/ecommerce.ts:1-19` + `frontend/meiyetec-web/src/api/google-maps.ts:1-18` |
| Method | — | |
| Controller | — | |
| Service | — | |
| Mapper | — | |
| DB表 | `google_maps_config` / `google_maps_geocode_log` / `ecommerce_platform` / `webhook_msg_log` | **❌ 三表缺失 + 一表缺 3 列** — I029 + EXT-24 边界 |
| 关联表 | — | |
| 读/写 | — | |
| 事务 | — | |
| 权限 | — | |
| 软删除 | — | |
| 审计字段 | — | |
| Static Status | L0（前端） / L0（DDL 存在） / **L0（DB 未未跑）** | 代码静态可达 |
| Runtime Status | UNVERIFIED | Phase 5+ |
| DB Status | ❌ MISSING + PARTIAL | **I029**（4 表 DDL 已就位但 dev DB 未跑迁移 — 用户决策：**不跑迁移**，Post-Audit 统一执行）|
| Business Status | `ecommerceApi` 8 端点（`createPlatform/listPlatforms/updatePlatform/listProducts/syncProducts/listOrders/syncOrders/dashboard`）；`googleMapsApi` 6 端点（`saveConfig/getConfig/geocodeStore/listStores/getPlaceDetails/getMemberHeatmap`）；DDL 三个迁移脚本（`migrate_ext_17_google_maps.sql` 53 行 + `migrate_ext_22_google_maps_place_details.sql` 15 行 + `migrate_ext_24_webhook_msg_log.sql` 24 行 EXT-24 边界）| `frontend/meiyetec-web/src/api/ecommerce.ts:1-19` + `frontend/meiyetec-web/src/api/google-maps.ts:1-18` + `backend/src/main/resources/db/sql/migrate_ext_17_google_maps.sql:1-53` + `migrate_ext_22_google_maps_place_details.sql:1-15` + `migrate_ext_24_webhook_msg_log.sql:1-24` |
| E2E Status | UNVERIFIED | Phase 5+ |
| Verified Commit | — | Phase 5+ |
| DB Snapshot | db-snapshot.sql @ 全文件 grep `google_maps_config/google_maps_geocode_log/webhook_msg_log` 0 匹配 + `ecommerce_platform` 缺 3 列 | |
| Last Verified At | — | |
| **Final Result** | **PARTIAL-BLOCKER** | **I029**（4 表 DDL 已就位但 dev DB 未跑迁移 — 用户决策：**不跑迁移**；Phase 5 实证将抛 `TableNotFoundException` 或 `Unknown column 'app_id'/'kdt_id'/'sign_secret'` 负向证据；Post-Audit 统一跑迁移）+ **`webhook_msg_log` 归 EXT-24**（用户决策 #6 — 只引用不重审）|
| Evidence | `audit/evidence/F080-frontend-api-and-db-migration/` | 待建（Phase 5+） |
| Issue | **I029** | |

---

## Batch 1.3 统计

| 维度 | 数量 | 占比 | Feature IDs |
|---|---|---|---|
| Feature 总数 | 25 | 100.0% | F056-F080 |
| VERIFIED（业务逻辑静态可达） | 4 | 16.0% | F058 / F059 / F068 / F074（4 个，详见 VERIFIED 修正表）|
| PARTIAL-BLOCKER（DB 缺失 + 依赖） | 8 | 32.0% | F056 / F057 / F060 / F061 / F064 / F066 / F075 / F076 / F080（I029 — 沿用 I002/I017 PARTIAL-BLOCKER 模式）|
| PARTIAL-NON-BLOCKER | 7 | 28.0% | F062（I031）/ F063（I032）/ F065（I028 部分反向）/ F067（I029 间接）/ F069（I029 间接）/ F073（I012 Bean 条件冲突模式）/ F079（I028）|
| CONFLICT（SoT vs 代码，中立标） | 3 | 12.0% | F071（I026）/ F077（I027）/ F078（I030）|
| MISSING（DB） | 0 | 0.0% | （用户决策：标 PARTIAL-BLOCKER 而非 MISSING，与 I002/I017 一致 — F056/F057/F060 等已计入 PARTIAL-BLOCKER）|
| BACKLOG-001（孤儿工具） | 1 | 4.0% | F072（I033 — `buildWebhookUrl`）|
| PARTIAL-NON-BLOCKER（含事务 rollbackFor） | 1 | 4.0% | F077（I034 — `@Transactional` 未指定 rollbackFor）|
| PARTIAL-NON-BLOCKER（含重放 nonce） | 1 | 4.0% | F078（I035 — 重放保护仅查 msg_id 不查时间窗）|
| Shadow（Frontend） | 1 | 4.0% | F079（I028 — 缺 `appId/kdtId/signSecret` UI）|
| dead code / BACKLOG | 1 | 4.0% | F072（I033 — `buildWebhookUrl` 孤儿）|
| BROKEN | 0 | 0.0% | — |
| UNVERIFIED（缺运行证据） | 25 | 100.0% | F056-F080 全部（GCP / 有赞凭据缺失；Phase 5 待实测）|

### VERIFIED Feature 修正表（静态可达）

| Feature | 静态可达性 | 备注 |
|---|---|---|
| **F058** | ✅ VERIFIED（HTTP 客户端契约）| `GoogleMapsGeocodingClient` 3 方法 + 3 URL 构造工具 + 私有 `httpGet` 完整实现 |
| **F059** | ✅ VERIFIED（Bean 注册）| `@Configuration` + `@Bean googleMapsGeocodingClient(ObjectMapper)` 完整 |
| **F068** | ✅ VERIFIED（接口契约）| `EcommerceAdapter` 5 方法 + 4 DTO（`AuthResult/ProductPage/OrderPage/HealthStatus`）完整 |
| **F074** | ✅ VERIFIED（Router 契约）| `EcommerceAdapterRouter` 3 方法（`getAdapter/hasAdapter/listPlatforms`）+ Mock fallback 完整 |

### CONFLICT Feature 修正表（SoT vs 代码 — 反向确认）

| Feature | 反向证据 | 备注 |
|---|---|---|
| **F071** | `YouzanWebhookController.java:121` 已调用 `YouzanEcommerceAdapter.verifyWebhookSignature` | **I026** — SoT §5 描述"孤儿代码"反向（中立标）|
| **F077** | `YouzanWebhookController.HandleYouzan` 完整实现（L83-200）| **I027** — SoT §5 描述"webhook 接收端缺失"反向（中立标）|
| **F078** | `YouzanWebhookController` L136-149 幂等去重已实现 + `webhook_msg_log` 持久化 | **I030** — SoT §5 描述"无重放保护"反向（中立标）|

### PARTIAL-BLOCKER Feature 修正表（沿用 I002/I017 模式 — I029 用户决策）

| Feature | DB 状态 | 备注 |
|---|---|---|
| **F056** | ❌ PARTIAL（表缺失）| `google_maps_config` 表 DDL 已就位（`migrate_ext_17:9`）但 dev DB 未跑迁移 — **I029 不跑迁移**（沿用 I002/I017）|
| **F057** | ❌ PARTIAL（表缺失）| `google_maps_geocode_log` 表 DDL 已就位（`migrate_ext_17:30`）但 dev DB 未跑迁移 — **I029 不跑迁移** |
| **F060** | ❌ PARTIAL（依赖表缺失）| F060 service 写 `google_maps_config`（间接 PARTIAL-BLOCKER）— **I029** |
| **F061** | ❌ PARTIAL（依赖表缺失）| F061 service 写 `google_maps_geocode_log`（间接 PARTIAL-BLOCKER）— **I029** |
| **F064** | ❌ PARTIAL（依赖表缺失）| F064 service 读 `google_maps_geocode_log`（间接 PARTIAL-BLOCKER）— **I029** |
| **F066** | ❌ PARTIAL（表存在但缺 3 列）| `ecommerce_platform` 表存在（db-snapshot.sql:4434，20 列）但缺 3 列 EXT-22 新列（`app_id/kdt_id/sign_secret`）— DDL `migrate_ext_22:11` 已写但 dev DB 未跑迁移 — **I029 不跑迁移** |
| **F075** | ❌ PARTIAL（依赖表缺 3 列）| F075 service 写 `ecommerce_platform`（间接 PARTIAL-BLOCKER）— **I029** |
| **F076** | ❌ PARTIAL（依赖表缺 3 列）| F076 service 写 `ecommerce_platform` token 缓存（间接 PARTIAL-BLOCKER）— **I029** |
| **F080** | ❌ PARTIAL（4 表迁移未跑）| 整合 DDL（4 表） — 用户决策**不跑迁移**（沿用 I002/I017 模式）|

### BACKLOG-001 Feature 修正表（孤儿工具 — 沿用 I013 模式）

| Feature | 孤儿工具 | 备注 |
|---|---|---|
| **F072** | `YouzanEcommerceAdapter.buildWebhookUrl` (L106-114) | **I033** — 全代码 0 匹配调用方；Phase 7 Post-Audit 决定（删除 / 保留 / 改造为注册工具）|

### Phase 5 实测预期负向证据

| Feature | 预期负向行为 | 证据 |
|---|---|---|
| F056/F057/F060/F064 | 调用 `/google-maps/*` → 触发 `TableNotFoundException: google_maps_config` 或 `google_maps_geocode_log` | `audit/evidence/F056-google-maps-config/db-evidence.md` |
| F061/F063 | 调用 `/google-maps/geocode` 或 `/geocode/batch` → 触发 `TableNotFoundException` 写日志失败（业务可继续，但日志丢失） | `audit/evidence/F061-google-maps-geocode/runtime-evidence.md` |
| F062 | `/place/details` 调用返回 11 fields 但**未写 `google_maps_geocode_log`**（与 `/geocode` 写日志不一致）| `audit/evidence/F062-google-maps-place-details/runtime-evidence.md` |
| F063 | `/geocode/batch` 调用成功但**无 audit log**（admin-only 无追责）| `audit/evidence/F063-google-maps-geocode-batch/runtime-evidence.md` |
| F066/F075/F076 | 调用 `/ecommerce/*` → 触发 `Unknown column 'app_id' / 'kdt_id' / 'sign_secret'` | `audit/evidence/F066-ecommerce-platform/db-evidence.md` |
| F077 | `/webhook/youzan` 调用验签失败 → HTTP 403 + `code=403 "签名校验失败"`（不会写库，符合设计）| `audit/evidence/F077-youzan-webhook-controller/runtime-evidence.md` |
| F078 | `/webhook/youzan` 重复 msg_id → 抛 `TableNotFoundException: webhook_msg_log`（幂等失败因表缺失）| `audit/evidence/F078-webhook-msg-id-idempotent/runtime-evidence.md` |
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
| **I029** ⭐ | **1.3** | F056 / F057 / F060 / F061 / F064 / F066 / F075 / F076 / F080 | **PARTIAL-BLOCKER**（DDL 已写但 dev DB 未跑迁移 — **用户决策不跑**，沿用 I002/I017 模式）|
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
| **PARTIAL-NON-BLOCKER（普通）**| 10 | I003 / I005 / I006 / I007 / I009 / I016 / I031 / I032 / I034 / I035 |
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
| **3** | **F056/F057/F060 等 I029 Feature 标记** | ✅ **PARTIAL-BLOCKER**（不是 MISSING）| 一致性 — I002/I017 在 Phase 2 阶段标 PARTIAL-BLOCKER 因为 DDL 已就位但 DDL 包含 IF NOT EXISTS 是双重保障；I029 仅 EXT-22 ALTER 列缺失但 DDL 已就位，所以标 PARTIAL-BLOCKER 而非 MISSING |
| **4** | **F072 `buildWebhookUrl` 孤儿工具** | ✅ **BACKLOG-001**（I033）| 沿用 I013 dead code 模式 |
| **5** | **`webhook_msg_log` 表** | ✅ **归 EXT-24 — Batch 1.3 只引用不重复审计** | 用户决策 #6（沿用 scope-freeze §九.11）|
| **6** | **Phase 2-5 证据策略** | ✅ 记录负向证据（预期 `TableNotFoundException` / `Unknown column 'enable_place_details'` 等） | Phase 5 实证 |
| **7** | **Post-Audit 修复策略** | ✅ 统一跑迁移（不分散决策）| 用户决策 |

---

## 验证范围声明（Batch 1.1 + Batch 1.2 + Batch 1.3 综合）

**本文件已完成 Phase 2 静态追溯（Phase 2 — DB Schema Truth + Full-Stack Traceability Matrix）**：

1. ✅ **I002 Phase 2 验证**：`db-snapshot.sql` 已 grep 验证 `crm_contact_map` 0 匹配（确认缺失 — Batch 1.1）
2. ✅ **I017 Phase 2 验证**：`db-snapshot.sql` 已 grep 验证 `plugin_runtime_state` 0 匹配（确认缺失 — Batch 1.2）
3. ✅ **I029 Phase 2 验证**：`db-snapshot.sql` 已 grep 验证 4 表（`google_maps_config` / `google_maps_geocode_log` / `webhook_msg_log`） 0 匹配 + `ecommerce_platform` 缺 3 列（确认缺失 — Batch 1.3）
4. ✅ **调用图完整**：前端 → API → Controller → Service → Adapter → Entity → DB 全栈可达（L0）
5. ✅ **静态 Status**：所有 Feature 标 L0（代码存在）
6. ✅ **DB Status**：
   - Batch 1.1：F005 / F012 / F020 / F023 → PARTIAL-BLOCKER（I002 缺 EXT-15 列）
   - Batch 1.2：F032 / F036 / F037 / F049 / F051 → PARTIAL-BLOCKER（I017 缺表）
   - **Batch 1.3：F056 / F057 / F060 / F061 / F064 / F066 / F075 / F076 / F080 → PARTIAL-BLOCKER（I029 缺表 / 缺列 — 用户决策不跑迁移）**
7. ✅ **CONFLICT 标注**：
   - Batch 1.1：F016 / F017 / F018（I001）
   - Batch 1.2：F035 / F044（I018）
   - **Batch 1.3：F071 / F077 / F078（I026 / I027 / I030 — 反向确认 SoT 错）**
9. ✅ **BACKLOG-001 标注**：**F072（I033 — `buildWebhookUrl` 孤儿工具）**
10. ✅ **Frontend Shadow 标注**：I024（F052/F054 — Batch 1.2）+ I028（F065/F079 — Batch 1.3）
11. ✅ **架构异味标注**：I019（F045/F046/F047/F052/F053/F054 — Batch 1.2）+ F060/F075/F076 命名异味（沿用 I019）
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

1. **PARTIAL-BLOCKER 9 个 Feature**（F056 / F057 / F060 / F061 / F064 / F066 / F075 / F076 / F080 — I029）— **确认不跑迁移**（沿用 I002/I017 模式）？Phase 5 实证将暴露 `TableNotFoundException` / `Unknown column 'app_id'` 异常作为负向证据。
2. **CONFLICT 3 个 Feature**（F071 / F077 / F078 — I026/I027/I030）— **接受 Phase 1 反向确认 SoT 错**（SoT §5 描述与代码不符）？Post-Audit 由人类决定（改文档 / 改代码 / 维持现状）。
3. **PARTIAL-NON-BLOCKER 10 个 Feature**（F062 / F063 / F065 / F066 / F069 / F073 / F074 / F075 / F077 / F078 / F079）— 是否全部接受「Phase 2 静态验证 + Phase 5 实测」策略？
4. **BACKLOG-001 1 个 Feature**（F072 — I033 `buildWebhookUrl` 孤儿工具）— 接受 Phase 7 Post-Audit 决定（删除 / 保留 / 改造为注册工具）？
5. **`webhook_msg_log` 表**（F077 / F078）— **接受 Batch 1.3 只引用不重复审计**（归 EXT-24 — 用户决策 #6）？