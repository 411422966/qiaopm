# Pre-flight Check（Batch 1.3 — Phase -1 产出）

> 适用规则：`@project-audit.mdc` 规则 16（Phase -1 必须先于 Phase 0）
> 模板：`@CURSOR_AUDIT_GUIDE.md`「二点五」章节
> 创建时间：2026-09-13 21:25 (UTC+8)
> 适用批次：**v3 第三批 / Batch 1.3 = EXT-22 Google Maps + 有赞**
> 上一批次产出：`audit/reports/batch-1.2-summary.md`（EXT-23 25/25 Feature 完结）+ `audit/issues/issue-register.md` §十八（I017-I025 + 3 项核实决策）
> 上一阶段产出：`audit/baseline/{commit.txt, env.md, db-snapshot.sql}`
> 用户决策来源：`audit/issues/issue-register.md` §十八（2026-09-13 21:15 决策时间）
> SoT 引用：`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（EXT-22 Operational Notes）

---

## 一、MCP 状态

| MCP | 状态 | 来源 |
|---|---|---|
| **MySQL MCP** | ✅ **已配置** | 沿用 Batch 1.1/1.2 决策 |
| **Playwright MCP** | ❌ **未配置**（沿用 Batch 1.1/1.2 决策）| Phase 5 E2E 降级为：① 生成 Playwright 脚本供用户手动执行；② 用户手动贴回 evidence；③ 静态抓 HTML + curl API + Vitest |

**降级方案（沿用）**：

| 工具/能力 | 降级方案 |
|---|---|
| **MySQL MCP** | ✅ 正常使用（验证 google_maps_config / ecommerce_platform / webhook_msg_log / ecommerce_order 表存在性 + 字段对齐）|
| **Playwright MCP** | Phase 5 降级为静态抓 HTML + curl API + Vitest（沿用 Batch 1.2 §一）|

---

## 二、PRD 覆盖（EXT-22 部分）

### 2.1 PRD 整体覆盖（沿用 Batch 1.1/1.2 §二）

| 项 | 值 |
|---|---|
| **路径** | `docs/PRD-美业SaaS系统功能大全.md`（14,190 bytes, v1.0, 2026-07-31）|
| **覆盖范围** | 12 标准模块（M01-M12）+ 6 进阶模块（M13-M18），P0/P1/P2/P3 分级 |
| **EXT-22 相关模块** | **M01-M18 未明确标注 EXT-22**；EXT-22 属于**第三方平台集成层**（Google Maps / 有赞），非业务主线模块 |

### 2.2 EXT-22 覆盖度

| PRD 模块 | EXT-22 覆盖？ | 备注 |
|---|---|---|
| M01-M12（标准业务）| ⚠️ 间接 | Google Maps 用于门店地图展示 / 地理编码；电商平台用于会员商城对接 |
| M13-M18（进阶业务）| ❌ | 进阶模块不依赖 EXT-22 |
| **Google Maps Place Details 独立模块** | ❌ PRD 未覆盖 | 计费平台集成，无独立 PRD 段 |
| **有赞 webhook 接收端** | ❌ PRD 未覆盖 | 电商平台 webhook 集成，无独立 PRD 段 |

**Phase 1 影响**：
- EXT-22 评估**不依赖 PRD 模块清单**（属于平台集成层）
- Operational Notes（`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`）作为 SoT 替代 PRD（沿用规则 11）
- Feature Inventory 直接以 Operational Notes §1 + §3 为骨架

### 2.3 缺失文档（沿用 §preflight 二）

| 缺失内容 | 实际位置 | 影响 Phase |
|---|---|---|
| **验收标准（L0-L5 等级定义）** | `docs/PROJECT_AUDIT_PLAYBOOK.md` 完整融合 | Phase 7 需对照 |
| **EXT-22 接口 / 签名契约** | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（Operational Notes）| Phase 2 / 3 |
| **GCP / Youzan 凭据管理规范** | **未找到独立文档**（Operational Notes §0.4 仅说明字段区分）| Phase 5 需用户补 |
| **Webhook 安全（NFR）** | Operational Notes §0.5 + §2 仅说明签名算法，**重放保护 / IP 白名单 / rate limit 未实现**（Operational Notes §5 已知问题）| Phase 3 / 8 需用户补 |
| **NFR（性能 / 多租户隔离 / 审计）** | **未找到独立 NFR 文档** | Phase 8 需用户补充 |

---

## 三、BLOCKER 阈值（沿用 Batch 1.1/1.2）+ EXT-22 特定

### 3.1 沿用 Batch 1.1/1.2 BLOCKER 阈值

| 情况 | 是否 BLOCKER（沿用）| 备注 |
|---|---|---|
| Mock 未清理（含 fallback 链路）| ✅ **是** | 部分场景豁免 |
| 迁移脚本未在生产跑过 | ✅ **是** | dev 环境跑过也算"通过" |
| webhook 端点缺失（已知平台未对接）| ✅ **是** | 是否允许"已规划/已建空壳"占位 |
| 未跑 E2E 的核心业务 | ✅ **是** | "核心业务"边界由谁定义 |
| SoT 文档 vs 代码方向不一致 | ✅ **是**（中立标 ISSUE，Fix Phase = Post-Audit 人类决策）| 沿用 I001 / I018 模式 |

### 3.2 EXT-22 特定 BLOCKER 项

| 项 | 默认 | 备注 |
|---|---|---|
| **GCP Places API 凭据缺失** | ✅ **是** | Phase 5 真实调用 Place Details 需要 GCP API Key（用户决策 #1）|
| **有赞 push_url 未在有赞后台注册** | ✅ **是** | 后端能收 `POST /webhook/youzan`，但有赞不会推 → 链路空转（用户决策 #2）|
| **GCP 未绑定结算账号 + 未设预算告警** | ✅ **是** | Place Details 单次 ≈ $0.025，无结算账号会 403（Operational Notes §0.1）|
| **前端 `enablePlaceDetails` 开关 UI 缺失** | ⚠️ Shadow | 启用只能 SQL 直写（Operational Notes §0.2 + §5）|
| **前端 `appId/kdtId/signSecret` 字段 UI 缺失** | ⚠️ Shadow | 配置只能 SQL 直写（Operational Notes §5）|
| **Place Details 字段全拿** | ⚠️ 观察 | Operational Notes §5 已知问题：11 字段一次性 → 始终命中 Basic+Contact+Atmosphere 三 SKU（无法拆分优化）|
| **API Key `getConfig` 端点泄露** | ⚠️ 观察 | `apiKey.substring(length-4)` 仅遮蔽后 4 位 → 整段 39 字符 API key 仍可猜测前 35 位（Operational Notes §5）|
| **无 webhook 重放保护** | ⚠️ 观察 | `verifyWebhookSignature` 只验签不记录已处理 msg_id — **但 `YouzanWebhookController` 实际加了 `webhook_msg_log` 幂等去重**（**反转详见 §七.4**）|
| **批量 geocode 无 audit log** | ⚠️ 观察 | Operational Notes §5 已知问题：admin-only 但无 audit log（建议 `geocode_batch_log` 表）|

### 3.3 EXT-22 表存在性检查（关键）

> ⚠️ **重要提示**：Operational Notes §8.1 假设 `google_maps_config` 表缺失，但实际**已通过 `migrate_ext_17_google_maps.sql` 创建**（详见 §七.4 SoT 漂移说明）。Phase 0 需对此进行中立标。

| 表 | 创建迁移脚本 | 实体类 | 需 Phase 0 验证？|
|---|---|---|---|
| `google_maps_config` | `migrate_ext_17_google_maps.sql:9`（DROP + CREATE）| ✅ `entity/GoogleMapsConfig.java` | ✅ 已建（migration 存在）|
| `google_maps_geocode_log` | `migrate_ext_17_google_maps.sql:40`（DROP + CREATE）| ✅ `entity/GoogleMapsGeocodeLog.java` | ✅ 已建（migration 存在）|
| `ecommerce_platform` | `create_missing_tables_b4b5.sql:372`（CREATE IF NOT EXISTS）| ✅ `entity/EcommercePlatform.java` | ✅ 已建 |
| `webhook_msg_log` | `migrate_ext_24_webhook_msg_log.sql:8`（CREATE IF NOT EXISTS）| ✅ `entity/WebhookMsgLog.java` | ✅ 已建（**注意来自 EXT-24 不是 EXT-22**）|
| `ecommerce_order` | `create_missing_tables_b4b5.sql:411`（CREATE IF NOT EXISTS）| ✅ `entity/EcommerceOrder.java` | ✅ 已建 |

**Phase 5 需 MySQL MCP 实际验证 dev DB 中 5 张表都存在 + 字段对齐**。

---

## 四、git 状态

### 4.1 当前 HEAD

| 项 | 值 |
|---|---|

| **HEAD** | `e85ba8b audit(batch-1.2-phase0): EXT-23 Plugin Runtime scope freeze` |
| **起点 commit**（Batch 1.3 起点）| `e85ba8ba8540081f2f59c97120052b5bef5a29ef`（用户预期一致）|
| **沿用 baseline commit** | `656caa72341c96d1b2e703dde993603dda9eebe1`（v3 整体 baseline）|
| **Batch 1.1 freeze commit** | `12a97ea57a98d268c864fc7c010b82b305d7bb93` |
| **Batch 1.2 freeze commit** | `e85ba8b`（HEAD，即 Phase 0 freeze commit）|

### 4.2 当前 working tree（`git status --short` 筛选 EXT-22 + Batch 1.2 残留）

```
M  backend/src/main/resources/application.yml       ← Batch 1.1 I010（Flyway 关闭，沿用）
M  audit/batches.md                                  ← Batch 1.2 Phase 5C 落档
M  audit/feature-inventory.md                        ← Batch 1.2 Phase 1 落档
M  audit/issues/issue-register.md                    ← Batch 1.2 Phase 5C 落档
?? backend/src/main/java/com/meiyetec/controller/{MktChannel,MktConversion,MktJourney,MktTemplate,MktTouchTask}Controller.java (MKT 重构 — 与 EXT-22 无关)
?? backend/src/main/java/com/meiyetec/service/ReportExportFileCleanupService.java (Batch 1.1 范围)
?? backend/src/main/resources/db/sql/migrate_mkt_002_drop_market_activity.sql (MKT 重构 — 与 EXT-22 无关)
?? backend/src/main/resources/db/sql/migrate_mkt_002_rollback.sql (MKT 重构 — 与 EXT-22 无关)
?? audit/evidence/F031-F055/**                      (Batch 1.2 25 Feature evidence)
?? audit/evidence/F052-plugin-tabs-ui/**            (Batch 1.2)
?? audit/reports/batch-1.2-summary.md               (Batch 1.2 5C 输出)
?? audit/issues/issue-register.md                   (Batch 1.2 Issue Register)
?? audit/runtime/{report-5A,report-5B,plan-batch-1.2}.md (Batch 1.2 Runtime)
?? audit/runtime/probe-5*.ps1, probe-5*.json, probe-5*.jsh (Batch 1.2 Probe 脚本)
?? frontend/meiyetec-web/e2e/audit/F052-F054.spec.ts (Batch 1.2 Playwright 降级)
```

**EXT-22 特定文件在 git status 中状态**：

| 文件 | git status | 备注 |
|---|---|---|
| `backend/src/main/java/com/meiyetec/entity/EcommercePlatform.java` | 未出现（已 HEAD 中）| **实体已含 appId/kdtId/signSecret**（L19/21/23）|
| `backend/src/main/java/com/meiyetec/entity/GoogleMapsConfig.java` | 未出现（已 HEAD 中）| **实体已含 enablePlaceDetails**（L43）|
| `backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java` | 未出现（已 HEAD 中）| **webhook 接收端已实现完整链路**（反转详见 §七.4）|
| `backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java` | 未出现（已 HEAD 中）| verifyWebhookSignature + buildWebhookUrl 已实现 |
| `backend/src/main/resources/db/sql/migrate_ext_22_google_maps_place_details.sql` | 未出现（已 HEAD 中）| ADD COLUMN IF NOT EXISTS（MySQL 8.0.29+）|
| `application.yml` | `M`（关闭 Flyway）| 不直接相关 |

### 4.3 working tree 统计

| 状态 | 数量（估）| 备注 |
|---|---|---|
| **Modified** | ~4 个（含 `application.yml` + 3 个 audit 文件）| 沿用 Batch 1.1/1.2 状态 |
| **Untracked** | ~80 个（Batch 1.2 25 Feature evidence + MKT 重构 + 日志）| 沿用 Batch 1.1/1.2 状态 |
| **Deleted** | — | — |

### 4.4 待 commit / stash 决策

| 选项 | 内容 | 推荐 |
|---|---|---|
| **A** | 直接 commit（baseline + preflight.md + 25+ 改动一次性入库）| ❌ 不推荐（chunk 太大）|
| **B** | 拆 commit（baseline 单独 + MKT 重构 + EXT-19/22/23/24 分批）| ✅ **推荐**（按模块分批审计）|
| **C** | 再 stash 一次（Phase -1 完成后保持 working tree clean）| ⚠️ 沿用 Batch 1.1/1.2 决策 |
| **D** | 不 commit（保持 dirty 状态直接进 Phase 0）| ❌ 不推荐（Phase 0 scope-freeze 需要 commit 锚点）|

→ **沿用 Batch 1.1/1.2 决策**：Phase -1 报告完成后**等用户决策 A/B/C/D**。

### 4.5 Phase 0 freeze commit 候选

| 候选 | 描述 | 何时 |
|---|---|---|
| **冻结 Batch 1.2 commit** | `e85ba8b` 已在 HEAD，作为 Batch 1.3 起点 | 已就位 |
| **冻结 Batch 1.3 commit** | 包含 `preflight-batch-1.3.md` + `scope-freeze-batch-1.3.md` | Phase 0 启动前 |

---

## 五、受保护页面（沿用 Batch 1.1/1.2）

> 来源：`.cursor/rules/no-touch-verified-pages.mdc`（用户实测通过）

| # | 路由 | 前端文件 | 后端接口 | 状态 |
|---|---|---|---|---|
| 1 | `/dashboard` | `frontend/meiyetec-web/src/views/dashboard/**` | （待 Phase 2 列出）| ✅ 已验证 |
| 2 | `/mh/visual` | `frontend/meiyetec-web/src/views/mh/visual.vue` | （待 Phase 2 列出）| ✅ 已验证 |
| 3 | `/mh/consent` | `frontend/meiyetec-web/src/views/mh/consent.vue` | （待 Phase 2 列出）| ✅ 已验证 |

### 5.1 影响（沿用 Batch 1.1/1.2）

- Phase 3（Static Gap Audit）：仅列可疑点，**不修改**
- Phase 5（Runtime Verification）：仅验证运行，**不动代码**
- Phase 6（DB Verification）：仅读 schema/数据，**不调整**
- 即使发现 Bug / 风格不一致 / dead code → 仅列入 Issue Register

### 5.2 例外（沿用 Batch 1.1/1.2）

- 用户明确说"修 X"且 X 指向这 3 个页面 → 可以动手
- 用户明确说"还原/回退"对它们的某次修改 → 可以动手

### 5.3 EXT-22 与受保护页面的关系

| 检查项 | 结果 |
|---|---|
| **Google Maps Place Details 是否影响 `/dashboard` 数据？**| ⚠️ **可能间接影响**（如果 dashboard 用地图展示门店，需 Place Details 拿经纬度 + 详情）— 需 Phase 2 确认 dashboard 是否调 google-maps API |
| **Google Maps Place Details 是否影响 `/mh/visual` / `/mh/consent`？**| ⚠️ **可能间接影响**（视觉档案可能涉及地理位置）— 需 Phase 2 静态扫描 |
| **有赞 webhook 是否触及受保护页面的后端接口？**| ❌ 不触及（`POST /webhook/youzan` 是有赞推入端，与前端 `/dashboard` / `/mh/*` 无交集）|
| **Google Maps Geocoding（EXT-17）是否触及受保护页面？**| ⚠️ **可能间接影响** — 与 EXT-22 同包，但 Geocoding 不属于 EXT-22 审计范围（沿用 EXT-22 SoT §0 适用范围排除）|

→ **EXT-22 审计期间受保护页面零直接风险**，但需 Phase 2 静态扫描确认无间接依赖。

---

## 六、守门脚本（g1-g8 命名约定存在 / 脚本未实现）

### 6.1 8 个守门脚本命名（沿用 `audit/README.md` §1）

> **当前状态**：`audit/scripts/` 目录**已创建但为空**，8 个守门脚本（g1-g8）**未实现**（沿用 Batch 1.1/1.2 决策）。

| 编号 | 脚本名（预期）| 当前状态 | 阻塞 Phase 0？ |
|---|---|---|---|
| **g1** | `g1-scope.sh` / `.ps1` | ❌ **未实现** | 否（按 Batch 1.1/1.2 决策：Phase 1-2 期间补）|
| **g2** | `g2-traceability.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g3** | `g3-issue-format.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g4** | `g4-evidence-check.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g5** | `g5-runtime-contract.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g6** | `g6-no-touch-pages.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g7** | `g7-blocker-rules.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g8** | `g8-pre-deploy.sh` / `.ps1` | ❌ **未实现** | 否 |

### 6.2 EXT-22 期间是否补齐？

| 建议 | 理由 |
|---|---|
| **Phase 0 期间补齐 g1-g8** | EXT-22 涉及 GCP / 有赞第三方集成 + 计费接口 + webhook 签名，Phase 5（Runtime Verification）依赖守门脚本 |
| **保持沿用（Phase 1-2 期间补）** | Batch 1.1/1.2 已确认延后，本批次沿用 |

→ **本批次沿用 Batch 1.1/1.2 决策**：Phase 1-2 期间补，**不阻塞 Phase 0**。

### 6.3 g1-g8 命名约定的扩展（建议）

| 编号 | 扩展脚本（EXT-22 特定，可选）| 用途 |
|---|---|---|
| **g12** | `g12-google-maps-config-table-check.sh` | 检查 `google_maps_config` + `enable_place_details` 字段存在 |
| **g13** | `g13-ecommerce-platform-fields-check.sh` | 检查 `ecommerce_platform` 含 app_id / kdt_id / sign_secret |
| **g14** | `g14-youzan-webhook-signature-check.sh` | 校验 `YouzanEcommerceAdapter.verifyWebhookSignature` 算法实现 |
| **g15** | `g15-webhook-msg-log-table-check.sh` | 检查 `webhook_msg_log` 表存在 + 幂等字段 |

→ **可选**：Phase 1 期间根据需要新增 EXT-22 守门脚本（不强制）。

### 6.4 风险

- 规则 15：自动化守门脚本每次 PR 必须跑 → **当前 CI 不守门 v3 体系规则**
- PR 可绕过审计规则合并 → **Batch 1.3 Phase 0 仍需确认守门脚本缺失是否算 BLOCKER**

---

## 七、当前批次：EXT-22 Google Maps + 有赞

### 7.1 批次基本信息

| 项 | 值 |
|---|---|
| **Batch ID** | **Batch 1.3** |
| **模块** | **EXT-22 Google Maps Place Details + 有赞开放平台签名字段** |
| **来源** | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（Operational Notes §0-§6）|
| **起点时间** | 2026-09-13 21:25 (UTC+8)（本批次启动）|
| **Phase -1 时间** | 2026-09-13 21:25 (UTC+8)（本文件落档）|
| **起点 commit** | `e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2 freeze commit）|

### 7.2 模块范围（来自 Operational Notes §1）

| 维度 | 内容 |
|---|---|
| **Google Maps** | `/google-maps/place/details` 计费接口 + `enable_place_details` 计费闸门 + 批量 geocode + `google_maps_config` + `google_maps_geocode_log` |
| **有赞** | `ecommerce_platform.app_id/kdt_id/sign_secret` 字段 + `YouzanEcommerceAdapter.verifyWebhookSignature` + `YouzanEcommerceAdapter.buildWebhookUrl` + `POST /webhook/youzan` 接收端 |
| **核心依赖** | `com.meiyetec.googlemaps.*` + `com.meiyetec.ecommerce.*` + GCP Places API（New）+ 有赞开放平台 |
| **关键文件** | `backend/src/main/java/com/meiyetec/entity/{GoogleMapsConfig,GoogleMapsGeocodeLog,EcommercePlatform,WebhookMsgLog,EcommerceOrder}.java` + `googlemaps/{GoogleMapsGeocodingClient,GoogleMapsClientConfig}.java` + `ecommerce/{EcommerceAdapter,EcommerceAdapterRouter,YouzanEcommerceAdapter,MockEcommerceAdapter}.java` + `service/{GoogleMapsService,EcommerceService}.java` + `controller/YouzanWebhookController.java` |
| **SQL** | `migrate_ext_17_google_maps.sql`（建 google_maps_config / google_maps_geocode_log）+ `migrate_ext_22_google_maps_place_details.sql`（ALTER）+ `migrate_ext_24_webhook_msg_log.sql`（建 webhook_msg_log）+ `create_missing_tables_b4b5.sql`（建 ecommerce_platform / ecommerce_order）|
| **前端** | `frontend/meiyetec-web/src/views/google-maps/index.vue`（未渲染 enablePlaceDetails 开关）+ `frontend/meiyetec-web/src/views/ecommerce/index.vue`（未渲染 appId/kdtId/signSecret 字段）+ `api/{google-maps,ecommerce}.ts` |

### 7.3 Feature 估算（待 Phase 1 落实）

| 子模块 | 估算 Feature 数 | 来源 |
|---|---|---|
| Google Maps Place Details 接口 | ~3 | Operational Notes §0.3 + §3 endpoints（`/place/details` + `/config` + `/health`）|
| Google Maps 批量 geocode | ~1 | §3（`/geocode/batch` admin-only）|
| Google Maps 配置 / 凭据 | ~2 | §0.2 + §3（`/config` POST/GET）|
| Google Maps 日志 + 计费审计 | ~1 | §4 `google_maps_geocode_log` |
| 有赞 Adapter 鉴权 | ~2 | §0.4 + §3（OAuth2 token + JWT）|
| 有赞 Adapter 签名算法 | ~2 | §0.5 + `YouzanEcommerceAdapter.verifyWebhookSignature` |
| 有赞 Adapter buildWebhookUrl 工具 | ~1 | §0.6（无调用方 — 可能 UNVERIFIED）|
| 有赞 webhook 接收端 | ~3 | `YouzanWebhookController`（ContentCachingRequestWrapper + 验签 + 幂等去重 + upsert）|
| EcommercePlatform CRUD | ~3 | §3 endpoints（`/platform` POST/GET + `/platform/{id}` POST + `/platform/{id}/health`）|
| EcommerceProduct/Order sync | ~2 | §3 endpoints（`/product/sync/{pid}` + `/order/sync/{pid}`）|
| Ecommerce Dashboard | ~1 | §3 endpoints（`/ecommerce/dashboard`）|
| 前端 UI（google-maps + ecommerce）| ~2 | `views/google-maps/index.vue` + `views/ecommerce/index.vue` |
| **估算合计** | **~20-25 Features** | Phase 1 落地 |

### 7.4 EXT-22 SoT 漂移（关键发现 — 沿用 I001 / I018 模式）

> ⚠️ **EXT-22 Operational Notes §5"已知问题"部分描述与当前代码状态反向 — 这是 SoT 漂移信号**。沿用 Batch 1.1 I001（CRM `integration_id` 漂移）+ Batch 1.2 I018（Plugin Runtime Controller assertTenantAccess 漂移）的处理模式：**中立标，不预设谁对谁错，留人类在 Post-Audit 决策**。

| # | Operational Notes §5 描述 | 当前代码状态（preflight 静态确认）| 反转？|
|---|---|---|---|
| 1 | "**`verifyWebhookSignature` 是孤儿代码** — `YouzanEcommerceAdapter.verifyWebhookSignature` 已写好算法，但 `EcommerceService` 没有 `/webhook/youzan` 接收端" | `controller/YouzanWebhookController.java:77` 的 `@PostMapping("/webhook/youzan")` **实际调用了** `YouzanEcommerceAdapter.verifyWebhookSignature`（L121）；且 `YouzanWebhookController` 是独立 controller，**不是** `EcommerceService` 的子方法 | ❌ **反向** |
| 2 | "**webhook 接收端缺失** — `EcommerceService` 没有 `/webhook/youzan` 路由" | `controller/YouzanWebhookController.HandleYouzan` **已完整实现**：ContentCachingRequestWrapper 读 rawBody（L77-89）+ 按 kdt_id 反查 `EcommercePlatform`（L91-105）+ 调 verifyWebhookSignature 验签（L121）+ msg_id 幂等去重 `webhook_msg_log`（L136-148）+ upsert `ecommerce_order`（L182-186）| ❌ **反向** |
| 3 | "**`google_maps_config` 表缺失**"（用户 prompt 第 7 节）| `migrate_ext_17_google_maps.sql:9` 已用 `CREATE TABLE google_maps_config` 建表（含 enable / quota_daily 等字段）；`migrate_ext_22_google_maps_place_details.sql` 用 ALTER ADD COLUMN 加 enable_place_details | ❌ **反向** |
| 4 | "**`buildWebhookUrl` 是孤儿工具** — 没有任何代码把 url 注册到有赞后台" | grep `buildWebhookUrl` 仅在 `YouzanEcommerceAdapter.java:100` 出现（声明 + 实现），**无任何调用方** | ✓ **确认** |
| 5 | "**前端缺 UI**：`google-maps/index.vue` 不显示 `enablePlaceDetails` 开关；`ecommerce/index.vue` 不显示 `appId/kdtId/signSecret` 字段" | `views/google-maps/index.vue` + `views/ecommerce/index.vue` 文件存在（未读具体内容，Phase 2 静态扫描验证）| ⏸ **待 Phase 2 验证** |
| 6 | "**没有 webhook 重放保护** — `verifyWebhookSignature` 只验签，不记录已处理 msg_id" | `YouzanWebhookController` **实际加了 `webhook_msg_log` 幂等去重**（L136-148 + L239-244）；且 `webhook_msg_log` 表已通过 `migrate_ext_24_webhook_msg_log.sql` 创建 | ❌ **反向** |
| 7 | "**`EcommercePlatform` 实体已改**（git status 里 M 标记）"（用户 prompt 第 7 节）| git status 中 `EcommercePlatform.java` **未出现**（已在 HEAD 中）；但实体**已含** `appId`(L19) + `kdtId`(L21) + `signSecret`(L23) | ⚠️ **用户描述与 git status 不一致 — 但实体字段确实已存在** |

**处理原则**（沿用 I001 / I018）：
- Phase 0 中立标（不预设 SoT 错还是代码错）
- Phase 5 实证记录事实（curl `/webhook/youzan` 端点是否真存在 + 验签是否工作）
- Fix Phase = **Post-Audit 人类决策**（3 选项：改文档 / 改代码 / 维持现状）

**Phase 0 影响**：
- I026-I029（建议登记 — 见 §八.4）
- F 编号可能因为 SoT 漂移而调整（如 F067 webhook 接收端 = VERIFIED 还是 PARTIAL-BLOCKER 取决于 Phase 5 实证）

### 7.5 与 Batch 1.1/1.2 独立性检查

| 检查项 | 结果 |
|---|---|
| **代码包共享** | ⚠️ **部分共享** — `YouzanWebhookController` 引用了 `EcommerceOrder` / `WebhookMsgLog` / `EcommercePlatform` 三个 entity；这些 entity 也被 `EcommerceService` 使用（marketplace CRUD，与 EXT-22 是同 package 不同职责）|
| **DB 表共享** | ⚠️ **部分共享** — `ecommerce_platform` + `ecommerce_order` + `webhook_msg_log` 三张表被 EXT-22（webhook 接收端）+ EXT-16/24（marketplace CRUD）共用 |
| **配置共享** | ❌ 无共享 |
| **业务依赖** | ❌ EXT-22 不依赖 Batch 1.1/1.2；Batch 1.1/1.2 不依赖 EXT-22 |
| **EXT-15 依赖** | ❌ EXT-22 不依赖 EXT-15 |
| **风险传染** | ⚠️ Batch 1.2 I019（PluginService 跨租户读风险）的模式可能再犯于 `EcommercePlatform` CRUD |
| **审计可分离性** | ✅ **可分离**（EXT-22 独立模块 + Feature Inventory + Issue Register）— 但需关注共用表的边界划分（Phase 1 期间明确）|

### 7.6 与 Batch 1.1 经验关联（沿用 §preflight 7.5 模板）

| EXT-24 经验（Batch 1.1）| EXT-22 预期重演？ |
|---|---|
| **I008（Fake Success / catch 吞异常）** | ⚠️ **EXT-22 `YouzanWebhookController` 主流程有 try/catch（L195-200），失败时写 `webhook_msg_log.status=FAILED`** — 与 EXT-22 §5 "没有 webhook 重放保护" 不同，**已实现幂等去重** |
| **I009（@Transactional 无 rollbackFor）** | ⚠️ `YouzanWebhookController.HandleYouzan` 加了 `@Transactional`（L76）但**未指定 `rollbackFor`** → 与 I009 模式一致 → Phase 3 检查 |
| **I011（Fake Failure / select-after-write）** | ⚠️ upsert 流程有 `existing == null ? insert : update`（L307-313）— 需 Phase 5 验证 race condition |
| **I012（Bean 条件冲突 / `@ConditionalOnMissingBean` 误用）** | ⚠️ Google Maps 和 Ecommerce 都有 `@ConditionalOnMissingBean` fallback 到 Mock — Phase 1 重点检查 |
| **I013（dead code）**| ⚠️ `buildWebhookUrl` 已确认是孤儿工具（无调用方）— Phase 1 建议登记 `BACKLOG-001` 或在 Phase 5 决定删除 |
| **I001（SoT 文档漂移）**| ⚠️ **Operational Notes §5 多项与代码方向不一致**（详见 §七.4）— 已中立标 |
| **I018（SoT vs 代码 `dataScope` 漂移）**| ⚠️ `YouzanWebhookController` / `GoogleMapsService` / `EcommerceService` **未声明任何租户隔离校验**（需 Phase 2 静态确认）|

### 7.7 已知 EXT-22 风险（Operational Notes §5 摘录 + 漂移标注）

| 风险 | 严重度 | 漂移？| 处理 |
|---|---|---|---|
| **`verifyWebhookSignature` 是孤儿代码** | 🟡 中 | ❌ **反向**（已被 `YouzanWebhookController` 调用）| 中立标 I026 |
| **webhook 接收端缺失** | 🔴 高 | ❌ **反向**（已实现完整链路）| 中立标 I027 |
| **`buildWebhookUrl` 是孤儿工具** | 🟢 低 | ✓ **确认**（无调用方）| Phase 1 标 BACKLOG-001 |
| **前端缺 UI**（`enablePlaceDetails` 开关 + `appId/kdtId/signSecret` 字段）| 🟡 中 | ⏸ **待 Phase 2 验证** | 中立标 I028 |
| **`google_maps_config` 表缺失**（用户描述）| 🔴 高 | ❌ **反向**（已通过 migrate_ext_17 创建）| 中立标 I029 |
| **Place Details 无降级** | 🟡 中 | ⏸ **待 Phase 2 验证降级逻辑** | 沿用 SoT |
| **批量 geocode 无 audit log** | 🟡 中 | ⏸ **待 Phase 2 静态确认** | 沿用 SoT |
| **SQL 缺 `CREATE TABLE IF NOT EXISTS`** | 🟡 中 | ❌ **反向**（migrate_ext_17 用 DROP+CREATE，migrate_ext_22 用 ADD COLUMN IF NOT EXISTS）| 沿用 SoT |
| **`getConfig` 端点泄露 API key** | 🟡 中 | ⏸ **待 Phase 2 验证** | 沿用 SoT |
| **Place Details 字段全拿** | 🟡 中 | ⏸ **待 Phase 2 验证** | 沿用 SoT |
| **没有 webhook 重放保护** | 🔴 高 | ❌ **反向**（已加 webhook_msg_log 幂等去重）| 中立标 I030 |

---

## 八、已知约束

### 8.1 `google_maps_config` 表（已存在但需 Phase 5 实证）

| 维度 | 详情 |
|---|---|
| **表名** | `google_maps_config` |
| **所属 EXT-* ** | EXT-17（创建）+ EXT-22（ALTER）|
| **Operational Notes 引用** | §0.2 + §4 字段映射速查 |
| **创建迁移** | `migrate_ext_17_google_maps.sql:9`（DROP + CREATE）|
| **ALTER 迁移** | `migrate_ext_22_google_maps_place_details.sql:6`（ADD COLUMN IF NOT EXISTS enable_place_details）|
| **代码状态** | ✅ `entity/GoogleMapsConfig.java` 已含 `enablePlaceDetails`（L43）|
| **DB 状态** | ⚠️ Phase 5 需 MySQL MCP 实际验证 dev DB 中表存在 + 字段对齐 |
| **Phase 1 影响** | ⚠️ **用户描述"缺失"与实际"已建"反转** → 标 ISSUE（中立标）→ Fix Phase = Post-Audit 人类决策 |
| **决策** | ❌ 本批次不修 SoT（沿用规则 6 + 规则 11：审计结论与 EXT-* 冲突时中立标）|

### 8.2 有赞 webhook 接收端（已实现但 SoT 未更新）

| 维度 | 详情 |
|---|---|
| **文件路径** | `backend/src/main/java/com/meiyetec/controller/YouzanWebhookController.java` |
| **路由** | `POST /webhook/youzan?kdt_id={kdtId}`（L77）|
| **处理流程** | 读 rawBody → 反查 platform → 验签 → msg_id 幂等去重 → upsert order → 写 webhook_msg_log（L69-200）|
| **与 EXT-22 SoT 关系** | ❌ **SoT §5 第一条描述反向**（说"未实现"，实际已实现）|
| **SoT 漂移处理** | 中立标 I027（Phase 0 登记，Fix Phase = Post-Audit 人类决策）|
| **Phase 5 验证需求** | 真实有赞环境（用户决策 #2）或 Mock 推送样本（降级方案）|

### 8.3 EcommercePlatform 字段变更（已实施但 SoT 未更新）

| 维度 | 详情 |
|---|---|
| **实体类** | `backend/src/main/java/com/meiyetec/entity/EcommercePlatform.java` |
| **新增字段** | `appId`(L19) + `kdtId`(L21) + `signSecret`(L23) |
| **DB 表** | `ecommerce_platform`（`create_missing_tables_b4b5.sql:372` 创建，**ALTER 未包含在 ext_22 migration 中**）|
| **DB 迁移** | `migrate_ext_22_google_maps_place_details.sql:11` ALTER TABLE ecommerce_platform ADD COLUMN app_id/kdt_id/sign_secret |
| **SoT 一致性** | ✅ 实体 / DB / Migration / SoT 完全对齐 |
| **Phase 5 验证需求** | MySQL MCP 验证 dev DB 中 3 列存在 |

### 8.4 与 Batch 1.1/1.2 独立性检查

| 检查项 | 结果 |
|---|---|
| **代码包共享** | ⚠️ 部分共享（共用 `EcommercePlatform` / `EcommerceOrder` / `WebhookMsgLog`）|
| **DB 表共享** | ⚠️ 部分共享（`ecommerce_platform` / `ecommerce_order` / `webhook_msg_log`）|
| **配置共享** | ❌ 无共享 |
| **业务依赖** | ❌ EXT-22 不依赖 EXT-23/EXT-24 |
| **审计可分离性** | ✅ Feature Inventory 可分离（Phase 1 期间明确边界划分）|

### 8.5 EXT-22 Operational Notes（`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`）

| 维度 | 详情 |
|---|---|
| **文件路径** | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` |
| **当前状态** | ✅ **已加载**（preflight §七.2 已确认）|
| **内容覆盖** | §0 关键约束（7 节）/ §1 Touchpoints / §2 Re-confirmation Checklist / §3 Endpoints / §4 字段映射 / §5 已知问题（**11 项部分漂移**）/ §6 业务侧调用模板 |
| **SoT 优先级** | 最高（规则 11：审计结论与 EXT-* 冲突时以 EXT-* 为准并标 ISSUE）|
| **使用方式** | Phase 1（Feature Inventory）+ Phase 2（Traceability）+ Phase 3（Issue 登记）全部以 Operational Notes 为骨架 |

**Phase 0 期间确认**：

- [ ] Operational Notes §5 已知问题 11 项 vs 代码现状（已完成 §七.4 中立标分析）
- [ ] Operational Notes §3 endpoints 列表 vs `GoogleMapsService` / `EcommerceService` / `YouzanWebhookController` 实际路由（Phase 2 静态扫描）
- [ ] Operational Notes §4 字段映射 vs 实体类字段（已确认对齐）

### 8.6 有赞 webhook 签名算法（Operational Notes §0.5 + 静态确认）

```java
// YouzanEcommerceAdapter.verifyWebhookSignature（line 114）
expected = lowercase( hex( md5( signSecret + rawBody ) ) )
compare expected == X-Youzan-Sign header value   (case-insensitive)
```

- 实现位置：`YouzanEcommerceAdapter.java:114` — `public static boolean verifyWebhookSignature(String signSecret, String rawBody, String signature)`
- 调用方：`YouzanWebhookController.java:121` — `YouzanEcommerceAdapter.verifyWebhookSignature(pf.getSignSecret(), rawBody, signature)`
- rawBody 来源：`ContentCachingRequestWrapper`（避免 Jackson 重排破坏签名）— L77-89
- **SoT 与实现完全对齐**

**Phase 5 验证需求**：
- 准备 3 组 payload：正常签名 / 篡改 body / 篡改 signature → 验证 verifyWebhookSignature 返回值
- 用 jshell 直接调用静态方法（绕过 HTTP）— Phase 5B 模板沿用 Batch 1.2

### 8.7 GCP Places API 计费闸门（Operational Notes §0.3）

```
门禁顺序（GoogleMapsService.placeDetails）：
1. tenantId + enabled=1 配置存在 + apiKey 非空 → 否则 NOT_FOUND
2. enablePlaceDetails == 1 → 否则 PERMISSION_DENIED（不调 Google）
3. 调用 geocodingClient.placeDetails(apiKey, placeId, lang) → 真实链路
```

- 步骤 2 是**唯一计费闸门**，前端 / 后端 / 中间人绕过它都能直接打到 Google
- Phase 5 验证需求：dev DB 中 `enable_place_details=0` → 调 `/google-maps/place/details` → 期望 `PERMISSION_DENIED`（不调 Google，无 GCP 计费）

### 8.8 受保护页面零风险（沿用 §五）

- EXT-22 审计期间**直接不触达**受保护页面（`/dashboard` / `/mh/visual` / `/mh/consent`）
- 需 Phase 2 静态扫描确认 EXT-22 与受保护页面后端接口无间接依赖

### 8.9 历史审计沉淀不在 v3 目录（沿用 Batch 1.1/1.2）

- `docs/audit/` 25+ 个 `audit_*.txt` + `BEAUTY_SAAS/` 子目录（28+ 报告）
- 不迁移（按 `audit/README.md` §2）
- Phase 7 引用时使用相对路径 `../docs/audit/BEAUTY_SAAS/19_REPAIR_ROADMAP_V3.md`

---

## 九、Phase -1 准入确认（自检）

| 检查项 | 结果 |
|---|---|
| Batch 1.3 = EXT-22 已决策（用户启动消息）| ✅ 用户决策 2026-09-13 21:25 |
| MCP 状态已确认 | ✅ MySQL ✅ / Playwright ❌（沿用降级）|
| PRD 覆盖已评估 | ✅ EXT-22 不在 PRD 主线；以 Operational Notes 为 SoT |
| BLOCKER 阈值已沿用 | ✅ Batch 1.1/1.2 §三 + EXT-22 特定项 |
| git 状态已快照 | ✅ HEAD `e85ba8b` / dirty 沿用 Batch 1.1/1.2 |
| 受保护页面已列出 | ✅ 沿用 `/dashboard` / `/mh/visual` / `/mh/consent` |
| 守门脚本状态已确认 | ✅ g1-g8 命名约定存在，脚本未实现 |
| 当前批次范围已明确 | ✅ EXT-22 Google Maps + 有赞 |
| SoT 漂移已识别 | ✅ §七.4 中立标 I026-I030 |
| 已知约束已记录 | ✅ google_maps_config / webhook 接收端 / EcommercePlatform / 沙箱签名 / GCP 计费闸门 |

✅ **Phase -1 准入条件全部满足。可以进入 Phase 0（scope-freeze-batch-1.3.md）。**

---

## 十、待用户确认清单（不阻塞 Phase 0，但建议在 Phase 0 前回复）

| # | 决策项 | 默认 | 影响 |
|---|---|---|---|
| 1 | GCP Places API 凭据 + 有赞 push_url 注册如何处理？| 真实凭据由用户提供；无凭据走 Mock 降级（沿用 Batch 1.2 D10 铁律）| Phase 5 真实链路验证范围 |
| 2 | EXT-22 Operational Notes §5 已知问题 11 项的反转如何处理？| **中立标 I026-I030**（沿用 I001 / I018 模式 — 不预设 SoT 错还是代码错，留人类 Post-Audit 决策）| Issue Register §十九 起 |
| 3 | `buildWebhookUrl` 孤儿工具是删除还是留作未来增强？| Phase 1 标 BACKLOG-001（沿用 I013 dead code 模式）| Phase 7 Post-Audit 清理 |
| 4 | Phase -1 报告完成后 commit 策略（A/B/C/D）| 待回复 | git history |
| 5 | 是否需要 Phase 0 期间补齐 g1-g8 守门脚本？| 沿用 Batch 1.1/1.2：Phase 1-2 期间补 | 不阻塞 Phase 0 |
| 6 | EXT-22 与 EXT-16/24 共用 entity/table 的边界如何划分？| Phase 1 期间明确（Feature ID 编号按 EXT-22 视角，跨 EXT-* 引用注明）| Phase 1 Feature Inventory |
| 7 | GCP Place Details 是否需要预先配置 GCP 凭据？| ❌ Phase 0-1 不需要；Phase 5 真实链路验证时由用户决策 | Phase 5 |
| 8 | 受保护页面（/dashboard, /mh/visual, /mh/consent）后端接口清单由谁提供？| Phase 2 静态扫描时列出 | 否 |
| 9 | GCP API Key `getConfig` 端点泄露风险是否在本批次处理？| 沿用 SoT — 仅标注，Fix Phase = Post-Audit | 否 |
| 10 | Phase 0 freeze commit 是否在 `e85ba8b` 后追加 `audit(freeze): Batch 1.2 完结 + Batch 1.3 启动`？| ✅ 推荐（与 Batch 1.1→1.2 freeze 模式一致）| Phase 0 启动前 |

### 10.1 默认决策影响

| 默认 | 影响 |
|---|---|
| **#1 GCP / 有赞凭据缺失 → Mock 降级** | Phase 5 仅验证 mock 链路 + jshell 单元测试；真实链路（`@ConditionalOnMissingBean` 禁用 + Mock 关闭）验证标记为 UNVERIFIED |
| **#2 SoT 漂移中立标** | I026-I030 不预设方向；Fix Phase = Post-Audit 人类决策 |
| **#3 buildWebhookUrl 标 BACKLOG** | 不影响 Phase 5 实证；Phase 7 决定删 / 留 |
| **#6 共用 entity/table 边界** | Phase 1 Feature Inventory 期间明确；Feature ID 按 EXT-22 视角（如 F061-EXT-22-Feature-X）|
| **#10 freeze commit 追加** | git history 清晰；Phase 0 scope-freeze 锚点明确 |

---

## 十一、本文件状态

| 项 | 值 |
|---|---|
| **状态** | 已落档（Phase -1 产出）|
| **变更控制** | Phase 0 启动后冻结；Phase 1 期间如发现新约束追加新段（§十二+）|
| **下一阶段产出位置** | `audit/baseline/scope-freeze-batch-1.3.md`（Phase 0）|
| **下一阶段 Prompt 模板** | 见 `docs/PROJECT_AUDIT_PLAYBOOK.md` §十「Phase 0」|

---

## 十二、附录：Phase 0 之前需要用户确认的关键问题（精简）

1. **SoT 漂移中立标（I026-I030）** ✅ 推荐接受 — 与 Batch 1.1 I001 / Batch 1.2 I018 模式完全一致
2. **GCP / 有赞凭据缺失 → Mock 降级** ✅ 推荐接受 — 沿用 Batch 1.2 D10 铁律
3. **`buildWebhookUrl` 孤儿 → BACKLOG-001** ✅ 推荐接受 — 沿用 Batch 1.1 I013 dead code 模式
4. **共用 entity/table 边界** ⏸ Phase 1 期间明确 — Feature ID 按 EXT-22 视角
5. **freeze commit 追加 `audit(freeze): Batch 1.2 完结 + Batch 1.3 启动`** ⏸ 待用户决策

---

**Batch 1.3 Phase -1 完结。EXT-22 Google Maps + 有赞范围已冻结 + 5 项 SoT 漂移已中立标 + 10 项已知约束已记录。⏸ 等用户确认 → 进入 Phase 0（scope-freeze-batch-1.3.md）。**
