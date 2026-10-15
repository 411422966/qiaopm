## Batch 1.3 — EXT-22 Google Maps Place Details + 有赞（v3 第三批）

**范围来源**：`audit/baseline/scope-freeze-batch-1.3.md` §2.1 + `audit/baseline/preflight-batch-1.3.md` §七
**EXT-* Operational Notes**：`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（**SoT**，**4 项与代码方向反转** — 见 §1.3.5）
**Batch 起点**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2 freeze commit）
**Feature 编号策略**：接续 Batch 1.2 的 F031-F055 → 本批次 F056-F080（25 Features）

### 1.3.1 范围涉及文件（已静态扫描）

| 类别 | 文件 | 行数 | 备注 |
|---|---|---|---|
| Google Maps 实体 | `backend/src/main/java/com/meiyetec/entity/GoogleMapsConfig.java` | 51 | `apiKey JsonProperty.WRITE_ONLY` (L29) + `enablePlaceDetails` Integer 字段 (L43, 1=启用/0=默认) |
| Google Maps 实体 | `backend/src/main/java/com/meiyetec/entity/GoogleMapsGeocodeLog.java` | 28 | `place_id` / `status` 列（Place Details 复用）|
| Google Maps 客户端 | `backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsGeocodingClient.java` | 247 | `geocode/findPlace/placeDetails` 3 方法 + URL 构造 (L42-66) + `GeocodeResult` / `PlaceDetailResult` DTO |
| Google Maps Config | `backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsClientConfig.java` | 14 | `@Configuration` + `@Bean googleMapsGeocodingClient(ObjectMapper)` |
| Google Maps Service | `backend/src/main/java/com/meiyetec/service/GoogleMapsService.java` | 257 | **`@RestController` 命名误导**（不是 Service）；7 端点 + 5 STORES + heatmap mock |
| Ecommerce 实体 | `backend/src/main/java/com/meiyetec/entity/EcommercePlatform.java` | 36 | **`appId`(L19) + `kdtId`(L21) + `signSecret`(L23) 三 EXT-22 新字段** |
| Ecommerce 实体 | `backend/src/main/java/com/meiyetec/entity/EcommerceOrder.java` | 31 | 21 字段（与 EXT-16 共有）|
| Ecommerce 实体 | `backend/src/main/java/com/meiyetec/entity/EcommerceProduct.java` | 21 | 13 字段（与 EXT-16 共有）|
| Ecommerce 实体 | `backend/src/main/java/com/meiyetec/entity/EcommerceSyncLog.java` | 17 | `syncType=PRODUCT/ORDER` + `successCount/failCount/durationMs/status` |
| Webhook 实体（**归 EXT-24**）| `backend/src/main/java/com/meiyetec/entity/WebhookMsgLog.java` | 23 | **Batch 1.3 只引用不重复审计**（用户决策 #6）；`source=YOUZAN` + `msg_id` + status=RECEIVED/PROCESSED/FAILED |
| Ecommerce Adapter 接口 | `backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapter.java` | 113 | 5 方法 + 4 DTO（AuthResult / ProductPage / OrderPage / HealthStatus）|
| Ecommerce Adapter | `backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java` | 269 | **OAuth2 + 商品/订单/health + `verifyWebhookSignature`(L114) + `buildWebhookUrl`(L100) + URL 构造** |
| Ecommerce Adapter | `backend/src/main/java/com/meiyetec/ecommerce/MockEcommerceAdapter.java` | 67 | `@ConditionalOnMissingBean(name="youzanEcommerceAdapter")` (L18) + 3 商品 + 2 订单样本 |
| Ecommerce Router | `backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapterRouter.java` | 38 | platform → adapter；MOCK fallback (L25)；`hasAdapter` + `listPlatforms` |
| Ecommerce Service | `backend/src/main/java/com/meiyetec/service/EcommerceService.java` | 312 | **`@RestController` 命名误导**（不是 Service）；9 端点 + upsertOrder 私有方法 |
| Webhook Controller | `backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java` | 295 | **完整链路**：ContentCachingRequestWrapper(L77-89) + 反查 pf(L91-105) + 验签(L121) + msg_id 幂等去重(L136-148) + upsert order(L182-186) + 写 webhook_msg_log(L195-200) |
| Mapper × 7 | `backend/src/main/java/com/meiyetec/mapper/{GoogleMapsConfigMapper,GoogleMapsGeocodeLogMapper,EcommercePlatformMapper,EcommerceProductMapper,EcommerceOrderMapper,EcommerceSyncLogMapper,WebhookMsgLogMapper}.java` | 5×7 | 全部 `extends BaseMapper` + `@Mapper` |
| DDL（建表）| `backend/src/main/resources/db/sql/migrate_ext_17_google_maps.sql` | 56 | DROP + CREATE `google_maps_config` + `google_maps_geocode_log`（uk_tenant_brand）|
| DDL（ALTER）| `backend/src/main/resources/db/sql/migrate_ext_22_google_maps_place_details.sql` | 17 | **ALTER** `google_maps_config` ADD `enable_place_details` (L6) + `ecommerce_platform` ADD `app_id/kdt_id/sign_secret` (L10-13) |
| DDL（建表）| `backend/src/main/resources/db/sql/migrate_ext_24_webhook_msg_log.sql` | 25 | **归 EXT-24**；CREATE `webhook_msg_log` + uk `uk_source_msg(source, msg_id)` + 3 索引 |
| DDL（参考）| `backend/src/main/resources/db/sql/create_missing_tables_b4b5.sql` | 230+ | `ecommerce_platform/product/order/sync_log` 4 张表 CREATE IF NOT EXISTS |
| Frontend 页面 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | 159 | **4 tab（配置 + 地图 + geocode + Place Details 测试）**；**已渲染 enablePlaceDetails 开关(L13-22) + 计费警告(L27-36)**（**与 SoT §5 "前端未渲染" 漂移反向** — 见 I028）|
| Frontend 页面 | `frontend/meiyetec-web/src/views/ecommerce/index.vue` | 81 | **3 tab（平台管理 + 商品同步 + 订单同步）**；**未渲染 appId/kdtId/signSecret 字段**（grep 0 匹配）|
| Frontend API | `frontend/meiyetec-web/src/api/google-maps.ts` | 14 | 5 方法（含 `getPlaceDetails`）|
| Frontend API | `frontend/meiyetec-web/src/api/ecommerce.ts` | 20 | 7 方法（无新增字段）|
| Router | `frontend/meiyetec-web/src/router/index.ts:174/179` | — | `path: 'ecommerce'` → `mkt:activity`；`path: 'google-maps'` → `dev:access` |
| SoT | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` | — | EXT-22 Operational Notes（§0-§6 完整）|

### 1.3.2 Feature 清单（25 项）

> 列：Feature ID / Module / 功能点 / Priority / 需求来源 / 前端入口 / 后端入口 / DB表 / 静态可达性 / 备注
> KNOWN-CONSTRAINT 标签说明：
> - **⚠ MISSING**：`google_maps_config` / `google_maps_geocode_log` / `webhook_msg_log` 表在 `db-snapshot.sql` **未发现**（DDL 已写但迁移未跑 — Phase 1 grep 实证，见 §1.3.6 未确认项 #1）；`ecommerce_platform` 表存在但 db-snapshot 中**没有** `app_id/kdt_id/sign_secret` 3 列（Phase 1 grep 实证，见 §1.3.6 未确认项 #2）— 影响 F062/F063/F080
> - **⚠ PARTIAL-BLOCKER**：GCP Places API 凭据缺失 → 真实链路 Phase 5 UNVERIFIED（用户决策 #1）— F058
> - **⚠ PARTIAL-NON-BLOCKER**：buildWebhookUrl 孤儿 / API Key `substring(length-4)` 泄露 / 字段全拿 11 / 批量 geocode 无 audit log / `@Transactional` 无 rollbackFor / `Mock` 与 `YouzanEcommerceAdapter` 同时存在导致 Mock 永不启用（`@ConditionalOnMissingBean` 因前者存在失效）
> - **🔄 CONFLICT**（SoT vs 代码反向，已中立标 I026-I030）— 见 §1.3.4

| Feature ID | Module | 功能点 | Priority | 需求来源 | 前端入口 | 后端入口 | DB表 | 静态可达性 | 备注 |
|---|---|---|---|---|---|---|---|---|---|
| **F056** | Google Maps / 配置 | saveConfig 配置保存 | P3 | EXT-22 §0.2 + §3 | `views/google-maps/index.vue` "配置" tab + `el-switch enablePlaceDetails` (L13-22) + `el-alert` 计费警告 (L27-36) + 「保存配置」按钮 | `POST /google-maps/config` → `GoogleMapsService.saveConfig` (`service/GoogleMapsService.java:71-91`) | `google_maps_config` | L0 / **⚠ MISSING（DB）** | upsert by `(tenantId, brandId)`；默认 `enablePlaceDetails=0`（L82）；强制 `enabled=1`/`quotaDaily=10000`/`defaultLanguage=zh-CN`；⚠ **字段漂移风险**：`configForm.defaultZoom` 前端传但后端未用（仅 `enablePlaceDetails` 写入）|
| **F057** | Google Maps / 配置 | getConfig 配置读取 + 计费开关回显 | P3 | EXT-22 §0.2 + §3 | (F056 同页 "配置" tab + 初始化 `enablePlaceDetails.value = data?.enablePlaceDetails ?? 0`) | `GET /google-maps/config` → `GoogleMapsService.getConfig` (`service/GoogleMapsService.java:93-112`) | `google_maps_config` | L0 / **⚠ MISSING（DB）** | ⚠ **PARTIAL-NON-BLOCKER（API Key 泄露）I032**：`apiKey.substring(length-4)` (L108) 仅遮蔽后 4 位 → 整段 39 字符 API key 仍可猜测前 35 位（**与 SoT §5 一致** — 仅标注不改）；返回字段含 `enablePlaceDetails` |
| **F058** | Google Maps / Place Details | placeDetails 计费接口 + 三段门禁 | P0 | EXT-22 §0.1 + §0.3 | `views/google-maps/index.vue` "Place Details 测试" tab + `el-form` + `doGetPlaceDetails` (L117-128) | `POST /google-maps/place/details` → `GoogleMapsService.placeDetails` (`service/GoogleMapsService.java:178-220`) | `google_maps_config`（不写日志 — 与 /geocode 链路不同，仅返回 11 字段）| L0 / **⚠ MISSING（DB）** / **⚠ PARTIAL-BLOCKER（GCP 凭据）I039** | 三段门禁：① cfg+apiKey 空 → `NOT_FOUND`；② `enablePlaceDetails≠1` → `PERMISSION_DENIED`（**唯一计费闸门** — SoT §0.3）；③ 调 `geocodingClient.placeDetails` (L207) → 11 fields 一次拿（**SoT §5 字段全拿风险 I033**）|
| **F059** | Google Maps / 地理编码 | geocode 单条解析 + mock fallback | P2 | EXT-22 §3 + EXT-17 | `views/google-maps/index.vue` "地理编码测试" tab + `doGeocode` | `POST /google-maps/geocode` → `GoogleMapsService.geocode` (`service/GoogleMapsService.java:114-158`) | `google_maps_geocode_log`（写入 L144-155）| L0 / **⚠ MISSING（DB）** | cfg 不存在 → `mockGeocode` (L161-170) 返回随机坐标（**降级到 mock**）；写日志用 `try/catch ignored`（L154）— **与 Batch 1.2 I008 Fake Success 模式同 I038** |
| **F060** | Google Maps / 批量 geocode | admin-only 批量解析 | P2 | EXT-22 §3 + EXT-17 | (无直接入口；**无前端 batch UI**) | `POST /google-maps/geocode/batch` → `GoogleMapsService.geocodeBatch` (`service/GoogleMapsService.java:222-274`) | `google_maps_geocode_log`（每条都写 L255-265）| L0 / **⚠ MISSING（DB）** | admin 守卫 `SUPER_ADMIN` / `PLATFORM_ADMIN` / `HQ_ADMIN`（L228-230）；单次 ≤ 50 条 (L237-239)；⚠ **PARTIAL-NON-BLOCKER（无 audit log）I036** — SoT §5 "批量 geocode 无 audit" |
| **F061** | Google Maps / 维护 | health 健康检查 + 日志查询 + mock 数据 | P2 | EXT-22 §3 + EXT-17 | (无直接入口；运维 / 测试用) | `POST /google-maps/health` (`:114`) + `GET /google-maps/geocode/log` (`:259`) + `GET /google-maps/stores` (`:276`) + `GET /google-maps/heatmap` (`:282`) | `google_maps_config` (health) + `google_maps_geocode_log` (log) | L0 / **⚠ MISSING（DB）** | health 用 "Beijing" 调真实 geocode 写回 `lastHealthAt/lastHealthMsg` (L124-128)；stores 5 条固定 STORES mock（L42-52）；heatmap 200 个 mock 点 (L285-290)|
| **F062** | Google Maps / 客户端 | GeocodingClient.placeDetails + URL 构造 | P1 | EXT-22 §0.3 + EXT-17 | (无前端入口；通过 F058 调) | `GoogleMapsGeocodingClient.placeDetails` (`googlemaps/GoogleMapsGeocodingClient.java:148-208`) + `buildPlaceDetailsUrl` (`:63-71`) + `PlaceDetailResult` DTO (`:230-247`) | — | L0 | 11 fields 硬编码 (`:67`)；状态枚举 OK/ZERO_RESULTS/NOT_FOUND/OVER_QUERY_LIMIT/REQUEST_DENIED/INVALID_REQUEST/NETWORK_ERROR；DTO 17 字段 + `ok()` 方法 |
| **F063** | Google Maps / DB | 三实体 + DDL（建表 + ALTER）| P3 | EXT-22 §0.7 + §4 | (前端通过 F056-F061 间接) | `entity/GoogleMapsConfig.java` (51 行) + `entity/GoogleMapsGeocodeLog.java` (28 行) + `db/sql/migrate_ext_17_google_maps.sql` (56 行) + `db/sql/migrate_ext_22_google_maps_place_details.sql` (17 行) | `google_maps_config` + `google_maps_geocode_log` | L0（代码 + DDL）/ **⚠ MISSING（db-snapshot.sql 未含 — Phase 1 grep 实证）** | migrate_ext_17 用 `DROP TABLE IF EXISTS` + CREATE（**注意：会清掉已有数据**）；migrate_ext_22 用 `ADD COLUMN IF NOT EXISTS enable_place_details`（**MySQL 8.0.29+ 要求** — SoT §0.7）；uk `uk_tenant_brand (tenant_id, brand_id, deleted)` |
| **F064** | Youzan Adapter / 鉴权 + 业务 | OAuth2 + 商品拉取 + 订单拉取 + healthCheck | P1 | EXT-22 §0.4 + §1 | (无前端直接入口；通过 F071/F076 间接) | `YouzanEcommerceAdapter` (`ecommerce/YouzanEcommerceAdapter.java`) — `authenticate` (`:135-178` 含 Token 缓存提前 2min 失效) + `fetchProducts` (`:183-209`) + `fetchOrders` (`:215-247`) + `healthCheck` (`:253-266`) | `ecommerce_platform`（OAuth2 token 缓存写回 `access_token/token_expire_at`）| L0 / **⚠ MISSING（DB 字段）** | Token 缓存：`tokenExpireAt > now()+2min` 直接复用 (`:142-147`)；商品 URL：`base/ENTRY_PREFIX/items/list?page_no&page_size&access_token` (`:70-77`)；订单 URL 加 `start_update_time/end_update_time` (`:80-89`) |
| **F065** | Youzan Adapter / URL 构造 | buildTokenRequestBody + buildItemsListUrl + buildTradesSoldGetUrl | P2 | EXT-22 §3 (URL 编码规范) | (无前端入口；F064 内部用) | `YouzanEcommerceAdapter.buildTokenRequestBody` (`:62-67`) + `buildItemsListUrl` (`:70-77`) + `buildTradesSoldGetUrl` (`:80-89`) + `httpPost/httpGet` (`:268-296`) | — | L0 | package-private static；`pageSize = Math.min(size, 100)` (L74) 防越界；UTF-8 编码；HTTP 工具用 `HttpURLConnection` |
| **F066** | Youzan Adapter / 签名算法 | verifyWebhookSignature 静态方法 | P0 | EXT-22 §0.5 + §6 | (无前端入口；F073 webhook 接收端用) | `YouzanEcommerceAdapter.verifyWebhookSignature` (`ecommerce/YouzanEcommerceAdapter.java:114-131`) | — | L0 | **算法**：`md5(signSecret + rawBody).hex().lowercase()` + `equalsIgnoreCase`；**rawBody 必须在 Jackson 反序列化前读**（SoT §0.5 关键约束）；⚠ **I026（CONFLICT 中立标）**：SoT §5 标 "孤儿代码"，但 `YouzanWebhookController:121` 实际调用 — **与 SoT 反向** |
| **F067** | Youzan Adapter / 工具 | buildWebhookUrl push_url 构造 | P3 | EXT-22 §0.6 | (无前端入口；**无任何调用方**) | `YouzanEcommerceAdapter.buildWebhookUrl` (`ecommerce/YouzanEcommerceAdapter.java:100-109`) | — | L0 | **BACKLOG-001**：Phase 1 grep 全 backend 仅声明 + 实现，无任何调用方（**确认非漂移，SoT §5 与代码一致**）；Phase 7 Post-Audit 决定删除 / 保留 / 改造为注册工具 |
| **F068** | Ecommerce / 适配器层 | EcommerceAdapter 接口 + Router + Mock fallback | P1 | EXT-22 §0.6 + §3 | (无前端入口；F064 间接) | `EcommerceAdapter` 接口 (`ecommerce/EcommerceAdapter.java:113`) — `platformCode/authenticate/fetchProducts/fetchOrders/healthCheck` + 4 DTO + `EcommerceAdapterRouter` (`ecommerce/EcommerceAdapterRouter.java:38`) + `MockEcommerceAdapter` (`ecommerce/MockEcommerceAdapter.java:67`) | `ecommerce_platform` + `ecommerce_product` + `ecommerce_order` | L0 | Router 上转大写 + MOCK fallback (L25-28)；⚠ **Mock 实际永不启用 I035**：`@ConditionalOnMissingBean(name="youzanEcommerceAdapter")` (`MockEcommerceAdapter.java:18`) 因 YouzanEcommerceAdapter 是 `@Component` 必注册 → Mock 永远被排除（**与 Batch 1.1 I012 模式同** — Bean 条件冲突）|
| **F069** | Ecommerce / 平台 CRUD | createPlatform + listPlatforms + updatePlatform + platformHealth | P1 | EXT-22 §3 (EcommercePlatform CRUD 端点) | `views/ecommerce/index.vue` "平台管理" tab + 「添加平台」按钮 + `addDialog` (L22-29, **仅 3 字段 — 未含 appId/kdtId/signSecret**) | `POST /ecommerce/platform` (`:65-71`) + `GET /ecommerce/platform/list` (`:73-77`) + `POST /ecommerce/platform/{id}` (`:79-85`) + `GET /ecommerce/platform/{id}/health` (`:88-93`) | `ecommerce_platform` | L0 / **⚠ MISSING（DB 字段）** | ⚠ **tenantId 写死 `0L`（L69）** — 平台级（与 Batch 1.2 I019 跨租户读风险模式类似）；⚠ **未声明 `dataScope` 校验 I037**（与 Batch 1.1 I004 / 1.2 I019 命名异味同模式）|
| **F070** | Ecommerce / 商品同步 | syncProducts + upsertProduct | P2 | EXT-22 §3 + EXT-16 | `views/ecommerce/index.vue` "商品同步" tab + `syncProducts` 按钮 (L63-67) | `POST /ecommerce/product/sync/{platformId}` → `EcommerceService.syncProducts` (`service/EcommerceService.java:107-149`) + 私有 `upsertProduct` (`:151-180`) | `ecommerce_product` + `ecommerce_sync_log` | L0 | `@Transactional` (`:104`) 但**未指定 `rollbackFor` I034** — **I009 模式**；upsert by `(platform, external_product_id)`；⚠ **Mock 永不启用导致有赞路径必走**（依赖真实凭据 — I035 同源）|
| **F071** | Ecommerce / 订单同步 | syncOrders + upsertOrder + 时间窗 | P2 | EXT-22 §3 + EXT-16 | `views/ecommerce/index.vue` "订单同步" tab + `syncOrders` 按钮 (L68-72) | `POST /ecommerce/order/sync/{platformId}` → `EcommerceService.syncOrders` (`service/EcommerceService.java:204-249`) + 私有 `upsertOrder` (`:251-329`) | `ecommerce_order` + `ecommerce_sync_log` | L0 | `@Transactional` (`:201`) 但**未指定 `rollbackFor` I034** — **I009 模式**；时间窗默认最近 24h (`startTime/endTime` fallback L216-221)；upsert by `(platform, external_order_id)`；⚠ **与 F073 webhook upsertOrder 私有方法完全重复**（代码复制）— I031 |
| **F072** | Ecommerce / 列表查询 | listProducts + listOrders | P2 | EXT-22 §3 + EXT-16 | `views/ecommerce/index.vue` "商品同步" + "订单同步" tab 显示 table | `GET /ecommerce/product/list` (`:97-105`) + `GET /ecommerce/order/list` (`:204-211`) | `ecommerce_product` + `ecommerce_order` | L0 | 按 `platformId → platform` 反查过滤；订单按 `status` 过滤；**未声明租户隔离**（EcommerceService 全部 endpoint）— I037 |
| **F073** | Youzan Webhook / 接收端 | POST /webhook/youzan 完整链路 | P0 | EXT-22 §0.5 + §6 (Operational Notes 描述反向) | (无前端入口；有赞 push_url 由 `buildWebhookUrl` 构造) | `POST /webhook/youzan?kdt_id={kdtId}` → `YouzanWebhookController.HandleYouzan` (`controller/YouzanWebhookController.java:77-201`) | `ecommerce_platform`（反查 kdt_id） + `ecommerce_order`（upsert） + `webhook_msg_log`（写入日志） | L0 / **⚠ MISSING（DB 表）** | **完整链路**：① ContentCachingRequestWrapper 读 rawBody (`:82-89`)；② 按 `kdt_id + platform=YOUZAN` 反查 (`:102-104`)；③ `verifyWebhookSignature` (`:121`)；④ 解析 msg_id (`:130-141`)；⑤ 幂等去重 (`:145-148`)；⑥ upsert order (`:182-186`)；⑦ 写 webhook_msg_log (`:195-200`)；⚠ **I027（CONFLICT 中立标）**：SoT §5 标 "webhook 接收端缺失"，但代码**完整实现** — **与 SoT 反向**；⚠ **有赞 push_url 未在有赞后台注册 I040**（用户决策 #2）|
| **F074** | Youzan Webhook / 幂等去重 | msg_id + source 唯一键判重 | P1 | EXT-22 §5 + EXT-24 (`webhook_msg_log`) | (无前端入口；F073 内部) | `YouzanWebhookController` (`:144-148`) + 私有 `upsertOrder` (`:204-263`) | `webhook_msg_log` + `ecommerce_order` | L0 / **⚠ MISSING（DB 表）** | 重复 msg_id → 返回 `duplicate=true + previousStatus + logId` (`:151-157`)；⚠ **I030（CONFLICT 中立标）**：SoT §5 标 "没有 webhook 重放保护"，但代码**已实现幂等去重** + `webhook_msg_log` 表 — **与 SoT 反向** |
| **F075** | Ecommerce / 实体 + DDL | 4 entity + 3 DDL（b4b5 + ext_22 + ext_24）| P1 | EXT-22 §4 + §3 | (前端通过 F069-F072 间接) | `EcommercePlatform` (`entity/EcommercePlatform.java:36` 含 `appId/kdtId/signSecret`) + `EcommerceProduct` (`:21`) + `EcommerceOrder` (`:31`) + `EcommerceSyncLog` (`:17`) + `WebhookMsgLog` (`:23` 归 EXT-24) | `ecommerce_platform` + `ecommerce_product` + `ecommerce_order` + `ecommerce_sync_log` + `webhook_msg_log`（**归 EXT-24**） | L0（代码）/ **⚠ PARTIAL-MISSING（DB 字段：3 个新列）** | db-snapshot 已含 4 张 ecommerce 表 (4396/4428/4460/4485)，但 `app_id/kdt_id/sign_secret` 3 列**未跑 migration**；`webhook_msg_log` 表 DDL 在 ext_24 脚本，**db-snapshot 未含**（grep 0 匹配）— F080/F082 依赖 |
| **F076** | 前端 / Google Maps | 4 tab + enablePlaceDetails 开关 + 计费警告 | P1 | EXT-22 §0.2 + §3 | `views/google-maps/index.vue` 完整 159 行：配置/门店地图/地理编码测试/Place Details 测试 tab | (无新后端) `GET /google-maps/config` + `POST /google-maps/config` + `POST /google-maps/geocode` + `POST /google-maps/place/details` | — | L0 | ⚠ **I028（CONFLICT 中立标）**：SoT §5 标 "前端缺 UI enablePlaceDetails 开关"，**但代码已实现** `el-switch enablePlaceDetails` (L13-22) + `el-alert` 计费警告 (L27-36) — **与 SoT 反向**；**与 §0.2 "appId/kdtId/signSecret 前端无 UI" 一致**（F077 同模式）|
| **F077** | 前端 / Ecommerce | 3 tab（平台管理 + 商品 + 订单）| P1 | EXT-22 §3 (Ecommerce 端点) | `views/ecommerce/index.vue` 完整 81 行：3 tab + `addDialog` 仅 3 字段 | (无新后端) `GET /ecommerce/platform/list` + `GET /ecommerce/product/list` + `GET /ecommerce/order/list` + `POST /ecommerce/platform` + `POST /ecommerce/product/sync/{id}` + `POST /ecommerce/order/sync/{id}` | — | L0 | **未渲染 `appId/kdtId/signSecret` 字段**（grep 0 匹配 — 与 SoT §0.2 + §5 一致）；⚠ addForm 默认 `WEIDIAN + shopId='WD'+Date.now()`（**WEIDIAN 后端无对应 adapter BACKLOG-002** — 与 EXT-24 HUBSPOT/ZOHO I001 模式同）|
| **F078** | 前端 / API 封装 | google-maps.ts + ecommerce.ts | P2 | EXT-22 §3 (API 端点) | `api/google-maps.ts:14` + `api/ecommerce.ts:20` 全部 API 封装 | (无新后端) 5 + 7 方法 | — | L0 | `googleMapsApi.{saveConfig,getConfig,geocodeStore,listStores,getPlaceDetails,getMemberHeatmap}`；`ecommerceApi.{createPlatform,listPlatforms,updatePlatform,listProducts,syncProducts,listOrders,syncOrders,dashboard}`；⚠ **缺 `/ecommerce/platform/{id}/health` 封装**（后端有但前端未用）|
| **F079** | Ecommerce / Dashboard | dashboard 数据看板 | P3 | EXT-22 §3 | (无前端入口；F077 间接) | `GET /ecommerce/dashboard` → `EcommerceService.dashboard` (`service/EcommerceService.java:296-309`) | `ecommerce_platform` + `ecommerce_product` + `ecommerce_order` | L0 | 4 字段：`totalPlatforms/totalProducts/totalOrders/supportedPlatforms`；try/catch 吞所有异常（L302-307）— **I038 Fake Success 模式同**；⚠ **未声明租户隔离 I037**（I019 模式）|
| **F080** | 架构异味 | EXT-22 命名/复制异味（沿用 Batch 1.1 I004 + 1.2 I019 模式）| P3 | scope-freeze §二.3 | (无前端入口) | ① `EcommerceService.java` 文件名 `*Service`，实际 `@RestController`（**命名误导** — 同 Batch 1.1 I004 模式）；② `GoogleMapsService.java` 文件名 `*Service`，实际 `@RestController`（**命名误导** — 同 I004 模式）；③ `YouzanWebhookController.upsertOrder` (`:204-263`) 与 `EcommerceService.upsertOrder` (`:251-329`) **完全复制** — 字段映射工具 5 个私有方法 (`firstNonNull/parseDecimal/parseDateTime/putIfPresent/strOrNull`) 也复制 | — | L0 | 不阻塞 Phase 2；登记重构 backlog（沿用 Batch 1.1 I004 + 1.2 I019）；Phase 7 Post-Audit 决定提取公用 `EcommerceOrderUpserter` 组件 |

### 1.3.3 Feature 数量统计

| 分类 | 数量 | Feature IDs |
|---|---|---|
| Google Maps REST 端点（Service）| 6 | F056-F061 |
| Google Maps 客户端 + 实体 + DDL | 2 | F062-F063 |
| Youzan Adapter 主逻辑 | 3 | F064-F066 |
| Youzan Adapter 工具 + 适配器层 | 2 | F067-F068 |
| Ecommerce 平台 CRUD + 同步 + 列表 + Dashboard | 5 | F069-F072 + F079 |
| Youzan Webhook 接收端 + 幂等去重 | 2 | F073-F074 |
| Ecommerce 实体 + DDL（合并 EXT-24 边界）| 1 | F075 |
| 前端（GM UI + Ecommerce UI + API 封装）| 3 | F076-F078 |
| 架构异味 | 1 | F080 |
| **合计** | **25** | F056-F080 |

### 1.3.4 Phase 1 Issue 清单（**不进 Phase 2 前必须决策**）

> 按用户修正（scope-freeze §五.2）：**MISSING vs CONFLICT 严格分离** — 表缺失用 I002/I017 MISSING 模式（跑迁移即解），SoT vs 代码不一致用 I001/I018 CONFLICT 模式（中立标，Fix Phase = Post-Audit 人类决策）。

| Issue ID | 类别 | 描述 | 影响 Feature | 建议处理 |
|---|---|---|---|---|
| **ISSUE-I026** | **🔄 CONFLICT（SoT vs 代码反向）** | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` §5 标 "`verifyWebhookSignature` 是孤儿代码 — 没有任何代码路径调用它"；但 `YouzanWebhookController.java:121` **实际调用** `YouzanEcommerceAdapter.verifyWebhookSignature`（`if (!YouzanEcommerceAdapter.verifyWebhookSignature(...))`）— **方向反转** | F066 / F073 | **中立标**：Phase 0 登记，Fix Phase = Post-Audit 人类决策（3 选项：改文档 §5 / 改代码移除注释 / 维持现状） |
| **ISSUE-I027** | **🔄 CONFLICT（SoT vs 代码反向）** | SoT §5 标 "webhook 接收端缺失 — `EcommerceService` 没有 `/webhook/youzan` 路由"；但 `controller/YouzanWebhookController.java` 是**独立 Controller**，**已完整实现** ContentCachingRequestWrapper + 反查 + 验签 + 幂等去重 + upsert + 日志（295 行）。**与 SoT 反向** | F073 / F074 | **中立标**：Phase 0 登记，Fix Phase = Post-Audit 人类决策 |
| **ISSUE-I028** | **🔄 CONFLICT（SoT vs 代码反向）** | SoT §5 标 "前端缺 UI：`google-maps/index.vue` 不显示 `enablePlaceDetails` 开关"；但 `views/google-maps/index.vue:13-22` **已实现** `el-switch enablePlaceDetails` (active-value=1/inactive-value=0) + `el-alert` 计费警告 (L27-36)。**与 SoT 反向** | F057 / F058 / F076 | **中立标**：Phase 0 登记，Fix Phase = Post-Audit 人类决策 |
| **ISSUE-I029** | **⚠ MISSING（DDL 写了但 db-snapshot.sql 未含）** | `db-snapshot.sql` Phase 1 grep `google_maps_config` / `google_maps_geocode_log` **0 匹配**（**注意**：SoT §5 假设 "表缺失"，但实际是 preflight 假设 vs 代码漂移 — DDL 已写 `migrate_ext_17_google_maps.sql:9/40` + `migrate_ext_22` ALTER，**迁移未跑**） | F056-F063 | **跑迁移即解**（I002/I017 MISSING 模式）：Phase 1 期间执行 `migrate_ext_17_google_maps.sql` + `migrate_ext_22_google_maps_place_details.sql` 跑批；写 `audit/evidence/F063-google-maps-ddl/db-evidence.sql` 验证 schema 对齐 |
| **ISSUE-I030** | **🔄 CONFLICT（SoT vs 代码反向）** | SoT §5 标 "没有 webhook 重放保护 — `verifyWebhookSignature` 只验签，不记录已处理 msg_id（理论上可重放）"；但 `YouzanWebhookController.java:144-148` **实际做了 msg_id 幂等去重**（SELECT webhook_msg_log WHERE source=YOUZAN AND msg_id=?），且 `migrate_ext_24_webhook_msg_log.sql:8` 已用 `CREATE TABLE IF NOT EXISTS` 建表 + uk `uk_source_msg(source, msg_id)`。**与 SoT 反向** | F073 / F074 / F082 | **中立标**：Phase 0 登记；F075 已含 `WebhookMsgLog` 实体登记（归 EXT-24 边界）；Phase 1 期间同步跑 `migrate_ext_24_webhook_msg_log.sql` 解决 DB MISSING（**注意**：此表**归 EXT-24**，Batch 1.3 只引用不重复审计，但幂等去重链路依赖） |
| **ISSUE-I031** | 代码重复（沿用 Batch 1.1/1.2 模式） | `YouzanWebhookController.upsertOrder` (L204-263) 与 `EcommerceService.upsertOrder` (L251-329) **完全复制**（60 行映射逻辑 + 5 个私有工具方法）；未来字段变更需同步改两处 | F071 / F073 / F080 | 决策：① 提取公用 `EcommerceOrderUpserter` 组件；② 接受现状（重复但不漂移） |
| **ISSUE-I032** | **⚠ PARTIAL-NON-BLOCKER（API Key 泄露）** | `GoogleMapsService.getConfig` (L108) `apiKey.substring(length-4)` 仅遮蔽后 4 位 → 整段 39 字符 API key 仍可猜测前 35 位（**与 SoT §5 一致 — 仅标注**） | F057 | 仅标注（用户决策 #9）；Fix Phase = Post-Audit |
| **ISSUE-I033** | **⚠ PARTIAL-NON-BLOCKER（Place Details 字段全拿）** | `GoogleMapsGeocodingClient.placeDetails` (`GoogleMapsGeocodingClient.java:67`) 硬编码 11 个 fields `name,formatted_address,formatted_phone_number,international_phone_number,website,opening_hours,rating,user_ratings_total,business_status,geometry,types` → 始终命中 Basic+Contact+Atmosphere 三 SKU（成本无法拆分 — **与 SoT §5 一致**） | F058 / F062 | 仅标注；Fix Phase = Post-Audit（按需选择 fields） |
| **ISSUE-I034** | **⚠ PARTIAL-NON-BLOCKER（@Transactional 无 rollbackFor）** | `EcommerceService.syncProducts` (L104) + `syncOrders` (L201) + `YouzanWebhookController.HandleYouzan` (L76) 加了 `@Transactional` 但**未指定 `rollbackFor`** → RuntimeException 默认回滚但 Checked Exception 不回滚 — **与 Batch 1.1 I009 模式同** | F070 / F071 / F073 | 决策：① 加 `rollbackFor = Exception.class`；② 接受现状 |
| **ISSUE-I035** | **⚠ PARTIAL-NON-BLOCKER（Mock 永不启用）** | `MockEcommerceAdapter` 加了 `@ConditionalOnMissingBean(name="youzanEcommerceAdapter")` (`MockEcommerceAdapter.java:18`)，但 `YouzanEcommerceAdapter` 是 `@Component`（`YouzanEcommerceAdapter.java:32`）→ **永远必注册** → Mock **永远不会启用**（**与 Batch 1.1 I012 模式同** — Bean 条件冲突） | F068 / F070 / F071 | 决策：① 接受现状（dev 始终走有赞 / OAuth2 失败降级到空列表）；② 改成 `@ConditionalOnProperty(name="ecommerce.mock.enabled", havingValue="true")` |
| **ISSUE-I036** | **⚠ PARTIAL-NON-BLOCKER（无 audit log）** | `GoogleMapsService.geocodeBatch` (L222-274) admin-only 但**无 audit log 记录**是谁调了多少条（**与 SoT §5 一致**）| F060 | 仅标注；Fix Phase = Post-Audit（加 `geocode_batch_log` 表） |
| **ISSUE-I037** | **⚠ PARTIAL-NON-BLOCKER（无 dataScope 校验）** | `EcommerceService` 全部 9 端点 + `YouzanWebhookController`（webhook 推送）**未声明任何租户隔离校验** — 与 Batch 1.2 I019 模式同 | F069-F072 / F073-F074 / F079 | 决策：① 加 `TenantContextHolder.get().getDataScope()` 校验；② 接受现状（webhook 是店铺 ID 维度的隔离，与 tenant 不同） |
| **ISSUE-I038** | **⚠ PARTIAL-NON-BLOCKER（Fake Success 模式）** | `EcommerceService.dashboard` (L296-309) `try/catch ignore` 吞所有异常 → 出错时仍返回 `Result.success(data={totalPlatforms=0/totalProducts=0/...})`；`GoogleMapsService.geocode` 写日志 `try/catch ignored` (L154) — **与 Batch 1.1 I008 模式同** | F059 / F060 / F061 / F079 | 决策：① catch 内至少打 warn log；② 加 Result.error 路径 |
| **ISSUE-I039** | **⚠ PARTIAL-BLOCKER（GCP 凭据缺失）** | `enable_place_details=0` 默认 + GCP API Key 缺失 → F058 placeDetails 真实链路 Phase 5 UNVERIFIED（**用户决策 #1 Mock 降级**）| F058 | Phase 5 仅验证 mock 链路 + jshell 单元测试；真实链路 UNVERIFIED |
| **ISSUE-I040** | **⚠ PARTIAL-BLOCKER（有赞 push_url 未注册）** | `YouzanWebhookController.HandleYouzan` 完整链路已实现，但有赞开发者后台**未配置 push_url** → 真实推送不会到达 → 链路空转（**用户决策 #2**） | F073 | Phase 5 仅验证 Mock 推送样本；真实推送 UNVERIFIED |
| **BACKLOG-001** | dead code | `YouzanEcommerceAdapter.buildWebhookUrl` (`YouzanEcommerceAdapter.java:100-109`) 无任何调用方（Phase 1 grep 全 backend 仅声明 + 实现）；**与 SoT §5 一致**（非漂移，CONFIRMED）| F067 | Phase 7 Post-Audit 决定删除 / 保留 / 改造为注册工具 |
| **BACKLOG-002** | dead code / 字段 | `frontend/ecommerce/index.vue` `addForm.shopId` 默认 `'WD'+Date.now()` — 后端无 WEIDIAN adapter（与 Batch 1.1 I001 HubSpot/Zoho 漂移模式同），**前端可选会 500** | F077 | 决策：① 前端只显示有 backend adapter 的平台（YOUZAN/MOCK）；② 后端加 WEIDIAN adapter；③ 接受现状 |

### 1.3.5 与 EXT-* Operational Notes 的冲突点（SoT 比对）

> 按 `project-audit.mdc` 规则 11：审计结论与 EXT-* Operational Notes 冲突时，**以 EXT-* 为准并标 ISSUE**。
> 本次审计的发现是 **EXT-* 文档本身与代码方向反转**（同 Batch 1.1 I001 + Batch 1.2 I018 模式），需在 Issue 清单显式标记并提请更新 EXT-*.mdc。

| EXT-* 章节 | 文档声称 | 代码事实 | 处理 |
|---|---|---|---|
| §0.6 `buildWebhookUrl` | "用于注册到有赞后台，当前**未实现** webhook 接收端" | `controller/YouzanWebhookController.java:77` 完整实现 `/webhook/youzan` 接收端（295 行） | ISSUE-I026 + I027 |
| §5 已知问题 1 | "`verifyWebhookSignature` 是孤儿代码 — 没有任何代码路径调用它" | `YouzanWebhookController.java:121` 实际调用 | ISSUE-I026 |
| §5 已知问题 2 | "webhook 接收端缺失 — `EcommerceService` 没有 `/webhook/youzan` 路由" | `YouzanWebhookController.HandleYouzan` 完整实现 | ISSUE-I027 |
| §5 已知问题 3 | "前端缺 UI：`google-maps/index.vue` 不显示 `enablePlaceDetails` 开关" | `views/google-maps/index.vue:13-22` `el-switch enablePlaceDetails` + L27-36 `el-alert` 计费警告已渲染 | ISSUE-I028 |
| §5 已知问题 5 | "`google_maps_config` 表缺失" | `db-snapshot.sql` grep 0 匹配（**与 SoT 一致** — DDL 已写但 DB 未跑迁移）| ISSUE-I029（**MISSING**，跑迁移即解）|
| §5 已知问题 6 | "批量 geocode 无 audit log" | `GoogleMapsService.geocodeBatch` (L222-274) **确实无 audit log** | OK（**与 SoT 一致**）|
| §5 已知问题 7 | "SQL 缺 `CREATE TABLE IF NOT EXISTS`" | `migrate_ext_17_google_maps.sql` 用 `DROP TABLE IF EXISTS + CREATE`，`migrate_ext_22_google_maps_place_details.sql` 用 `ADD COLUMN IF NOT EXISTS`（**部分一致** — migrate_ext_22 用 IF NOT EXISTS 兜底）| OK |
| §5 已知问题 8 | "`getConfig` 端点泄露 API key" | `GoogleMapsService.getConfig` (L108) `apiKey.substring(length-4)` 仅遮蔽后 4 位 | OK（**与 SoT 一致**）|
| §5 已知问题 9 | "Place Details 字段全拿" | `GoogleMapsGeocodingClient.buildPlaceDetailsUrl` (L67) 硬编码 11 fields | OK（**与 SoT 一致**）|
| §5 已知问题 10 | "没有 webhook 重放保护" | `YouzanWebhookController.java:144-148` msg_id 幂等去重 + `webhook_msg_log` uk `uk_source_msg` 已实现 | ISSUE-I030 |
| §5 已知问题 4 | "`buildWebhookUrl` 是孤儿工具" | Phase 1 grep 全 backend **仅声明 + 实现，无任何调用方** | OK（**与 SoT 一致** — BACKLOG-001）|
| §0.2 `enable_place_details` 写入 | "前端无对应开关 UI" | **与 SoT 反向**（F076 已实现 `el-switch`）| ISSUE-I028 |
| §0.2 启用方式 | "直接 SQL UPDATE（前端缺 UI 时唯一可行路径）" | 前端已实现开关 → 此段需更新为"前端已实现 + SQL 兜底双路径" | ISSUE-I028 |
| §1 Touchpoints | 列了 `EcommerceService.java` "**未实现 webhook 接收端**" | `YouzanWebhookController` 是独立 Controller，**不**在 `EcommerceService` 中 | ISSUE-I027 |
| §3 Endpoints | Ecommerce 端点列出 `POST /webhook/youzan` 标 "缺失 — EXT-22 未实现" | 已实现 | ISSUE-I027 |
| §3 Endpoints | Google Maps 列出 `POST /google-maps/place/details` | 已实现 + 三段门禁 | OK（**一致**）|
| §0.3 门禁顺序 | "1. cfg 存在 2. `enablePlaceDetails == 1` 3. 调 client" | `GoogleMapsService.placeDetails` (L181-220) 完全一致 | OK（**一致**）|
| §0.5 签名算法 | "`md5(signSecret + rawBody).lowercase()` + `equalsIgnoreCase`" | `verifyWebhookSignature` (`:114-131`) 完全一致 | OK（**一致**）|
| §0.6 `buildWebhookUrl` | 工具用于注册到有赞后台 | 工具**是孤儿**（与 §5 一致）| OK（**与 SoT 一致**）|

### 1.3.6 未确认项（标记"未确认"，禁止猜测）

| # | 项 | 当前信息 | 待补来源 |
|---|---|---|---|
| 1 | `db-snapshot.sql` 中 `google_maps_config` / `google_maps_geocode_log` / `webhook_msg_log` 是否存在 | Phase 1 grep **0 匹配**（**MISSING 实证**）| Phase 2：① 跑 `migrate_ext_17_google_maps.sql` + `migrate_ext_22_google_maps_place_details.sql` + `migrate_ext_24_webhook_msg_log.sql` 跑批；② `DESCRIBE google_maps_config` 验证 `enable_place_details` 列 |
| 2 | `db-snapshot.sql` 中 `ecommerce_platform` 表的 `app_id/kdt_id/sign_secret` 3 列 | Phase 1 读 L4434-4459 **未含** 3 列（**MISSING 实证**）| Phase 2 跑 `migrate_ext_22_google_maps_place_details.sql` ALTER + `DESCRIBE ecommerce_platform` 验证 3 列 |
| 3 | MySQL 版本 ≥ 8.0.29（`ADD COLUMN IF NOT EXISTS` 兼容） | `migrate_ext_22` 注释明示 | Phase 2 `SELECT VERSION()` |
| 4 | GCP Places API Key 是否真注入 | 用户决策 #1：Mock 降级；Phase 5 再评估 | Phase 5 用户决策 |
| 5 | 有赞开发者后台 push_url 是否已注册 | 用户决策 #2：Mock 推送样本；真实推送 UNVERIFIED | Phase 5 用户决策 |
| 6 | `YouzanWebhookController` 是否有 `dataScope` 校验 | Phase 1 静态扫描确认**无**（与 Batch 1.2 I019 同模式）；webhook 是店铺级隔离（kdt_id）非 tenant 级，**与平台端不同语义** | Phase 2 静态确认 + Phase 5 跨租户测试 |
| 7 | `EcommerceService` 全部 9 端点租户隔离 | Phase 1 静态扫描确认**无** `TenantContextHolder.getDataScope()` 调用；`tenantId` 写死 `0L`（平台级 — L69）| Phase 2 静态确认 |
| 8 | `MockEcommerceAdapter` 是否真启用 | `@ConditionalOnMissingBean(name="youzanEcommerceAdapter")` + `YouzanEcommerceAdapter` 是 `@Component` → **永不启用** | Phase 2 启动日志 `grep MockEcommerceAdapter` |
| 9 | `webhook_msg_log` 表实际是否落地（依赖 EXT-24） | DDL 在 `migrate_ext_24_webhook_msg_log.sql:8`；db-snapshot 未含；Batch 1.3 归 EXT-24 边界（用户决策 #6）| Phase 2 跑 `migrate_ext_24_webhook_msg_log.sql` |
| 10 | 前端 `/google-maps` 路由注册 | `router/index.ts:179` `path: 'google-maps'` + `permission: 'dev:access'` | OK |
| 11 | 前端 `/ecommerce` 路由注册 | `router/index.ts:174` `path: 'ecommerce'` + `permission: 'mkt:activity'` | OK |
| 12 | `BizCode.PERMISSION_DENIED` / `NOT_FOUND` / `SYSTEM_ERROR` | `exception/BizCode.java` 已确认（沿用 Batch 1.1/1.2） | OK |

### 1.3.7 与 preflight §八 的交叉引用

| preflight §八 已知约束 | 对应 Feature / Issue | Phase 1 静态确认 |
|---|---|---|
| **§8.1 `google_maps_config` 表存在但需 Phase 5 实证** | F063 / ISSUE-I029（**MISSING 已升级**）| **Phase 1 升级确认**：db-snapshot.sql grep **0 匹配**，DDL 已写但迁移未跑 → I029 MISSING 跑迁移即解 |
| **§8.2 有赞 webhook 接收端（已实现但 SoT 未更新）** | F073 / ISSUE-I027（**CONFLICT 已中立标**）| **确认**：代码完整实现 295 行，与 SoT §5 反向 → I027 中立标 |
| **§8.3 EcommercePlatform 字段变更（已实施但 SoT 未更新）** | F075 / `EcommercePlatform.java:19/21/23` | **确认**：实体 / DDL / SoT 完全对齐（仅 `db-snapshot.sql` 缺 3 列）|
| **§8.4 与 Batch 1.1/1.2 独立性检查** | F068-F080 + I019/I020 模式 | **确认**：部分共享 `EcommercePlatform` / `EcommerceOrder` / `WebhookMsgLog` |
| **preflight §七.4 SoT 漂移 1-7** | I026-I030 + I032/I033/I036 | **4 项反转确认**（I026/I027/I028/I030）+ 3 项与 SoT 一致（I032/I033/I036）|

---

**Phase 1 产出（Batch 1.3）完成。本批次 25 个 Feature 已落 `audit/feature-inventory.md`，与 scope-freeze §3.3 预期 20-25 完全一致（上限）。**

**已知约束标记汇总**（KNOW-CONSTRAINT，强制关注）：

- ⚠ **MISSING（DB）**：3 张表（`google_maps_config` / `google_maps_geocode_log` / `webhook_msg_log`）+ 1 张表的 3 列（`ecommerce_platform.{app_id/kdt_id/sign_secret}`）— **Phase 1 期间跑迁移即解**（I029/I030 + F063/F075 依赖）
- ⚠ **PARTIAL-BLOCKER**：GCP 凭据缺失（F058 / I039）+ 有赞 push_url 未注册（F073 / I040）— **用户决策 #1/#2 Mock 降级**
- ⚠ **PARTIAL-NON-BLOCKER**：API Key 泄露（I032）/ 字段全拿（I033）/ `@Transactional` 无 rollbackFor（I034）/ Mock 永不启用（I035）/ 无 audit log（I036）/ 无 dataScope（I037）/ Fake Success try/catch（I038）

**SoT 漂移中立标汇总**（沿用 Batch 1.1 I001 + Batch 1.2 I018 模式）：

- **I026** `verifyWebhookSignature` 是孤儿代码 → 反向（已被 `YouzanWebhookController:121` 调用）
- **I027** webhook 接收端缺失 → 反向（`YouzanWebhookController` 完整实现 295 行）
- **I028** 前端 `enablePlaceDetails` 开关缺失 → 反向（`google-maps/index.vue:13-22` 已渲染）
- **I030** 没有 webhook 重放保护 → 反向（msg_id 幂等去重 + `webhook_msg_log` uk 已实现）

**待用户决策**（不进 Phase 2 前）：

1. **ISSUE-I029**：Phase 1 期间是否立即跑 `migrate_ext_17_google_maps.sql` + `migrate_ext_22_google_maps_place_details.sql` 迁移解决 DB MISSING？
2. **ISSUE-I026-I028 + I030**：是否授权更新 `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` §0.2 + §0.6 + §1 + §3 + §5（修正 4 项与代码反向的描述）？
3. **ISSUE-I031**：`upsertOrder` 私有方法代码重复 — ① 提取公用组件 ② 接受现状？
4. **ISSUE-I032**：API Key 泄露仅标注（沿用 SoT — 用户决策 #9）。
5. **ISSUE-I033**：Place Details 字段全拿仅标注（沿用 SoT）。
6. **ISSUE-I034**：`@Transactional` 无 rollbackFor — ① 加 `rollbackFor=Exception.class` ② 接受现状？
7. **ISSUE-I035**：Mock 永不启用（`@ConditionalOnMissingBean` 因 `YouzanEcommerceAdapter` 必注册失效）— ① 接受现状 ② 改成 `@ConditionalOnProperty`？
8. **ISSUE-I036**：批量 geocode 无 audit log 仅标注。
9. **ISSUE-I037**：`EcommerceService` + `YouzanWebhookController` 无 dataScope 校验 — ① 加校验 ② 接受现状（webhook 是店铺级隔离）？
10. **ISSUE-I038**：dashboard / geocode 日志 try/catch 吞异常 — ① 加 warn log ② 加 Result.error 路径？
11. **ISSUE-I039**：GCP 凭据 Phase 5 评估（沿用用户决策 #1/#7）。
12. **ISSUE-I040**：有赞 push_url Phase 5 用户决策（沿用用户决策 #2）。
13. **BACKLOG-001**：`buildWebhookUrl` 孤儿工具 — ① 删除 ② 保留 ③ 改造为注册工具？
14. **BACKLOG-002**：前端 `WEIDIAN` 选项无后端 adapter — ① 前端过滤 ② 后端加 adapter ③ 接受现状？

---

**Phase 1（Batch 1.3）完成 ⏸ 等用户确认 → 进入 Phase 2（static-gap + Traceability Matrix）。**
