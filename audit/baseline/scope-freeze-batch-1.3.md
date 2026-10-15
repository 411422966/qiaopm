# Scope Freeze — Batch 1.3（Phase 0 产出）

> 来源：`@project-audit.mdc` + `docs/PROJECT_AUDIT_PLAYBOOK.md` §十「Phase 0 — Audit Rules / Scope Freeze」
> 创建时间：2026-09-13 21:57 (UTC+8)
> 适用批次：**v3 第三批 / Batch 1.3 — EXT-22 Google Maps + 有赞**
> 上一阶段产出：`audit/baseline/preflight-batch-1.3.md`（Phase -1，413 行 / 8 项内容完整）
> 上一批次完结：`audit/reports/batch-1.2-summary.md`（EXT-23 25/25 Feature 完结）+ `audit/issues/issue-register.md` §十八（I017-I025 + 3 项核实决策）
> 本批次起点 commit（沿用 Batch 1.2 freeze commit）：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`
> 用户决策来源：`audit/baseline/preflight-batch-1.3.md` §十（2026-09-13 21:55 用户确认）
> SoT 引用：`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（EXT-22 Operational Notes）
> 模板链路：`preflight-batch-1.3.md` §八 → 本文件 §九（引用，不复制）

---

## 〇、文档说明

Phase 0 **不列 Feature**（那是 Phase 1）。Phase 0 只做四件事：

1. 冻结规则（Feature ID / L0-L5 / Result 子标签 / Evidence 目录 / SoT 引用）
2. 冻结范围（本批次涉及哪些 EXT-* + 模块）
3. 确认能力边界（Cursor 能不能做什么）
4. 引用 preflight-batch-1.3.md 的已知约束（不复制内容，仅引用）

**用户确认（2026-09-13 21:55）**：5 项关键决策 + 5 项次要决策 + 第 3 项漂移分类修正（详见 §十）。

---

## 一、项目定位（QiaoPM 美业 SaaS — Batch 1.3 视角）

| 项 | 值 |
|---|---|
| **项目名** | QiaoPM（美业 SaaS 消费收银会员管理系统）|
| **代码仓库** | `D:\QiaoPM`（workspace root）|
| **业务范围** | 美容院 / 美发店 / 美甲美睫 / SPA / 会所 / 医美 |
| **核心特征** | 高频预约、套餐/次卡依赖、会员复购驱动、私域运营强 |
| **PRD** | `docs/PRD-美业SaaS系统功能大全.md`（v1.0, 14,190 B, 2026-07-31）|
| **标准模块** | M01-M12（预约/收银/会员/套餐/储值/项目/库存/员工/营销/评价/报表/连锁）|
| **进阶模块** | M13-M18（智能排班/动态定价/客户360/自动化营销/视觉档案/电子知情同意）|
| **本批次定位** | **EXT-22 Google Maps Place Details + 有赞开放平台签名字段**（**第三方平台集成层**，非业务主线模块）|
| **本批次与业务模块关系** | Google Maps 用于门店地图展示 / 地理编码（间接服务 M01-M12）；有赞 webhook 服务会员商城对接（间接服务 M06-M12）|
| **优先级分布（沿用 v3）** | P0 × 5 / P1 × 6 / P2 × 6 / P3 × 1 |
| **支付通道（沿用 v3）** | 微信 / 支付宝 / 银联云闪付 / 数字人民币 2.0 / 花呗 / Stripe / Square / PayPal / Gift Card / BNPL |

**Batch 1.3 Phase 7 验收范围**：以 EXT-22 Operational Notes（`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`）为 SoT，**PRD 不直接覆盖**；Feature 清单由 Phase 1 输出，本阶段不预判。

---

## 二、验收范围（Batch 1.3）

### 2.1 涉及模块

| EXT-* | 模块 | 主要改动 / 验证范围 |
|---|---|---|
| **EXT-22** | Google Maps Place Details + 有赞 | `GoogleMapsConfig.enablePlaceDetails` 计费开关 + `google_maps_config.enable_place_details` 字段 + `/google-maps/place/details` 计费接口 + `GoogleMapsGeocodingClient.placeDetails` + `ecommerce_platform.{appId,kdtId,signSecret}` 三个字段 + `YouzanEcommerceAdapter.verifyWebhookSignature` + `YouzanEcommerceAdapter.buildWebhookUrl` + `POST /webhook/youzan` 接收端（已实现）|

### 2.2 不在本批次范围（明确排除）

| 类别 | 模块 | 排除原因 |
|---|---|---|
| **受保护页面** | `/dashboard`, `/mh/visual`, `/mh/consent` | 用户已实测通过，按 `.cursor/rules/no-touch-verified-pages.mdc` 只观察不修复 |
| **EXT-24 CRM 集成** | `crm_integration` / `crm_contact_map` / `crm_sync_log` | Batch 1.1 已完结；Batch 1.3 不触达 |
| **EXT-23 Plugin Runtime** | `plugin_runtime_state` / `plugin_install` / pf4j | Batch 1.2 已完结；Batch 1.3 不触达 |
| **EXT-19 多语言预约确认** | `overseas_booking` / i18n template | 不在本批次；EXT-22 评估不依赖 EXT-19 |
| **共用 entity/table** | `ecommerce_platform` / `ecommerce_order` / `webhook_msg_log` | webhook_msg_log **归 EXT-24**（Batch 1.3 只引用，不重复审计，详见用户决策 #6）；`ecommerce_platform` / `ecommerce_order` 边界 Phase 1 期间明确 |
| **历史模块** | M01-M18 中除上述外的所有模块 | 不在本批次；待后续批次（v3 第四批起）评估 |
| **基础设施** | Redis / MySQL 配置 / CI 流水线 | 基础设施层不在本批次审计 |

### 2.3 EXT-22 详细验证范围

| 层级 | 文件 | 验证范围 |
|---|---|---|
| **Google Maps Entity** | `entity/GoogleMapsConfig.java` | `enablePlaceDetails` 字段（L43，Integer，1=启用 / 0=默认）|
| **Google Maps Entity** | `entity/GoogleMapsGeocodeLog.java` | `place_id` / `status` 列（Place Details 复用）|
| **Google Maps Client** | `googlemaps/GoogleMapsGeocodingClient.java` | `placeDetails(...)` + `PlaceDetailResult` DTO（11 字段全拿）|
| **Google Maps Config** | `googlemaps/GoogleMapsClientConfig.java` | Spring Bean 注册 |
| **Google Maps Service** | `service/GoogleMapsService.java` | `/google-maps/place/details` + `/google-maps/geocode/batch`（admin-only） + 三段门禁 |
| **Ecommerce Entity** | `entity/EcommercePlatform.java` | `appId`(L19) + `kdtId`(L21) + `signSecret`(L23) |
| **Ecommerce Entity** | `entity/EcommerceOrder.java` | EXT-22 复用（边界 Phase 1 明确）|
| **Ecommerce Adapter** | `ecommerce/EcommerceAdapter.java` | `AuthResult / ProductPage / OrderPage / HealthStatus` |
| **Ecommerce Adapter** | `ecommerce/YouzanEcommerceAdapter.java` | `verifyWebhookSignature`(L114) + `buildWebhookUrl`(L100) + OAuth2 |
| **Ecommerce Adapter** | `ecommerce/MockEcommerceAdapter.java` | dev fallback（@ConditionalOnMissingBean）|
| **Ecommerce Router** | `ecommerce/EcommerceAdapterRouter.java` | platform → adapter 分发 |
| **Ecommerce Service** | `service/EcommerceService.java` | `/ecommerce/*` 全部端点 |
| **Webhook Controller** | `controller/YouzanWebhookController.java` | `POST /webhook/youzan?kdt_id={kdtId}` + ContentCachingRequestWrapper + 验签 + 幂等去重 + upsert |
| **SQL 迁移** | `db/sql/migrate_ext_17_google_maps.sql` | google_maps_config + google_maps_geocode_log（DROP + CREATE）|
| **SQL 迁移** | `db/sql/migrate_ext_22_google_maps_place_details.sql` | ALTER google_maps_config + ecommerce_platform（ADD COLUMN IF NOT EXISTS）|
| **前端** | `views/google-maps/index.vue` | **未渲染 enablePlaceDetails 开关**（SoT §5 待 Phase 2 验证）|
| **前端** | `views/ecommerce/index.vue` | **未渲染 appId/kdtId/signSecret 字段**（SoT §5 待 Phase 2 验证）|
| **前端** | `api/{google-maps,ecommerce}.ts` | API 封装 |

### 2.4 重构类变更标记

按规则 14：「重构类变更必须先跑 Phase 3.5 Diff Audit」。

本批次**不涉及重构**（EXT-22 是新增字段 + 新增接口，Batch 1.1/1.2 期间未触达）→ Phase 3.5 **不适用**。

---

## 三、Feature ID 规则（沿用 Batch 1.1/1.2 + 编号策略）

引用：`.cursor/rules/feature-id.mdc` + `docs/PROJECT_AUDIT_PLAYBOOK.md` §三

### 3.1 基础规则（沿用 Batch 1.1/1.2）

| 规则 | 内容 |
|---|---|
| **格式** | `F` + 三位数字（如 `F001`, `F031`, `F055`, `F056`, `F080`）|
| **拆分粒度** | 一个 Feature = 一个可独立验收的业务能力 |
| **拆分示例** | F001 登录 / F002 退出 / F003 用户列表 / F004 新增用户 / F005 编辑用户 / F006 删除用户 / F007 用户详情 / F008 权限配置 |
| **禁止** | 不要把"用户管理"作为一个 Feature / 不要把"订单模块"作为一个 Feature / 不要把一张数据库表作为一个 Feature |
| **Evidence 目录** | `audit/evidence/FXXX-<slug>/`（编号固定，缺一张即 UNVERIFIED）|

### 3.2 编号策略（**Batch 1.3 决策**）

| 选项 | 推荐 | 理由 |
|---|---|---|
| **A. 接续 Batch 1.2 的 F055 起** | ✅ **采用**（用户决策）| 全局唯一、便于跨批次交叉引用与 grep；Evidence 目录 / Issue 引用无歧义 |
| B. 从 F001 重新开始 | ❌ 不采用 | 跨批次引用需补 Batch 前缀，增加元数据开销 |
| C. 混合（保留 F001-F055 给 Batch 1.1/1.2，新模块用 F101+）| ❌ 不采用 | 编号跳跃不利于连续阅读 |

### 3.3 Batch 1.3 编号规则

| 维度 | 值 |
|---|---|
| **起始编号** | **F056**（接续 Batch 1.2 的 F031-F055）|
| **预估上限** | **F080**（按 Operational Notes §3 endpoints + 子模块估算 20-25 Features）|
| **预留上限** | **F085**（5 个缓冲编号，应对 Phase 1 触达时发现新 Feature）|
| **冲突解决** | Phase 1 期间如发现编号冲突，重命名旧 Feature（不要回插新 Feature 到中间）|
| **跨批次引用** | 直接用全局 F 编号（如 "F056 见 `audit/evidence/F056-google-maps-place-details/`"），不需加批次前缀 |
| **跨 EXT-* 引用** | Feature ID 按 EXT-22 视角命名；引用 EXT-24 的 webhook_msg_log 注明 "EXT-24 边界" |

### 3.4 Issue 编号（沿用 Batch 1.1/1.2）

| 维度 | 值 |
|---|---|
| **Issue 编号** | 沿用全局 `I` + 三位数字（Batch 1.1 用到 I016，Batch 1.2 用到 I025）|
| **Batch 1.3 起始** | **I026**（Phase 3 期间首次登记 — 与 preflight §七.4 漂移标对齐）|
| **预留上限** | **I040**（预留 15 个 Issue 编号应对 Phase 1-5 触达）|
| **漂移分类** | 按用户修正（详见 §五.2）：I026-I030 = SoT vs 代码 CONFLICT；表 MISSING 类用 I002 模式 |

### 3.5 Phase 1 任务

按 §3.3 规则拆分 EXT-22 的 Feature 清单（编号 F056 起），覆盖：

- Google Maps Place Details 接口（`/place/details` + `/config` + `/health`）~5
- Google Maps 批量 geocode（`/geocode/batch` admin-only）~1
- Google Maps 配置 / 凭据（`enablePlaceDetails` 计费闸门）~2
- Google Maps 日志 + 计费审计（`google_maps_geocode_log`）~1
- 有赞 Adapter 鉴权（OAuth2 token + JWT）~2
- 有赞 Adapter 签名算法（`verifyWebhookSignature` md5）~2
- 有赞 Adapter `buildWebhookUrl` 工具（孤儿 → UNVERIFIED / BACKLOG）~1
- 有赞 webhook 接收端（`POST /webhook/youzan` 已实现）~3
- EcommercePlatform CRUD（`/platform` + `/platform/{id}` + `/health`）~3
- EcommerceProduct/Order sync（`/product/sync/{pid}` + `/order/sync/{pid}`）~2
- Ecommerce Dashboard（`/ecommerce/dashboard`）~1
- 前端 UI（google-maps + ecommerce）~2

**估算合计**：**20-25 Features**（F056-F080 范围），预留 F081-F085 应对扩展。

---

## 四、L0–L5a/L5b 验证等级确认

引用：`docs/PROJECT_AUDIT_PLAYBOOK.md` §四

| 等级 | 名称 | 含义 | 能否算完成 |
|---|---|---|---|
| **L0** | 代码存在 | 静态扫到代码 / SQL / 前端文件 | ❌ 仅存在 |
| **L1** | 编译通过 | `mvn compile` / `vue-tsc --noEmit` 通过 | ❌ 编译 ≠ 业务 |
| **L2** | 单元测试通过 | Vitest / JUnit / Surefire 通过 | ❌ 单元 ≠ 集成 |
| **L3** | 静态链路通 | 调用图完整（路由 → controller → service → mapper → SQL 存在）| ❌ 静态 ≠ 动态 |
| **L4** | 集成测试 / API 测试通过 | 真实启动 + curl / PowerShell 集成脚本通过 | ❌ 单步 ≠ 闭环 |
| **L5a** | 业务闭环 | E2E 跑通：点击 → 接口 → DB 写入 → 读回 → 前端展示 | ✅（受 Playwright MCP 限制降级，见 §能力边界）|
| **L5b** | 非功能闭环 | 性能 / 安全 / 多租户隔离 / 审计 / traceId 串起来 | ✅ |

**判定规则**：

- L0-L3：**静态分析可判定**（Cursor 能做）
- L4：**需运行项目**（Cursor 配合用户手动跑 / CI 跑）
- L5a：**需真实浏览器交互**（降级方案见 §能力边界 8.3）
- L5b：**需 NFR 工具 + traceId 体系**（降级方案见 §能力边界 8.3）

**Phase 2-7 任务**：对每个 Feature 标注其期望达到的 L 等级。Batch 1.3 期间建议 EXT-22 Feature **至少 L3**（静态链路通），Mock 降级路径 **争取 L4**（API 可调），真实链路（GCP / 有赞 push）**L5a/L5b 受凭据缺失限制降级为 UNVERIFIED**。

---

## 五、Result 子标签确认

引用：`docs/PROJECT_AUDIT_PLAYBOOK.md` §五 + `.cursor/rules/project-audit.mdc` 规则 7

**最终 Result 只允许以下 10 种状态**（沿用 Batch 1.1/1.2）：

| Result | 含义 | 处理 |
|---|---|---|
| **VERIFIED** | L5a 完整通过 + DB 验证 + 业务规则匹配 | 可宣布完成 |
| **PARTIAL-BLOCKER** | 部分功能通过，但有 BLOCKER 级缺失 | 阻塞 Gate 1 |
| **PARTIAL-NON-BLOCKER** | 部分功能通过，有缺陷但非 BLOCKER | 不阻塞 Gate 1，列入 Issue Register |
| **BROKEN** | 存在明确错误，运行链路无法正常完成 | 必修 |
| **MOCK-INTENTIONAL** | 设计上就是 fallback，有注解/配置说明，允许保留 | 需 Phase 1 文档化意图 |
| **MOCK-FAKE** | 假成功（看起来跑了，实际没跑通）| 必修（规则 10 硬性要求）|
| **SHELL-INTENTIONAL** | P2 占位页，已知且计划内 | 需 Phase 1 标注 |
| **SHELL-ABANDONED** | 已被废弃但未清理 | 必修清理 |
| **MISSING** | 需求存在，但实际没有对应实现 | 必修 |
| **UNVERIFIED** | 代码可能存在，但没有足够运行/数据/业务证据证明完成 | 必须有显式标注，不允许在无证据时声称 VERIFIED |

### 5.1 EXT-22 特殊 Result 判定建议

| EXT-22 特殊场景 | 建议 Result | 理由 |
|---|---|---|
| **GCP Places API 真实调用（无凭据）** | **UNVERIFIED** | 凭据缺失（用户决策 #1：Mock 降级）；Phase 5 真实链路验证标记为 UNVERIFIED |
| **`google_maps_config` / `ecommerce_platform` 表未跑迁移** | **MISSING** | DDL 写了但 DB 没建（preflight §八.1 + 用户修正 #3）→ I002/I017 模式 |
| **`webhook_msg_log` 表** | **不在 Batch 1.3 范围** | 归 EXT-24（用户决策 #6）；Batch 1.3 只引用不重复审计 |
| **`buildWebhookUrl` 孤儿工具** | **BACKLOG-001**（沿用 I013 模式）| 无调用方；不删除，Phase 7 Post-Audit 决定 |
| **SoT vs 代码 CONFLICT（5 项漂移）** | **中立标 I026-I030** | 沿用 I001/I018 模式；Fix Phase = Post-Audit 人类决策 |
| **前端缺 UI（`enablePlaceDetails` 开关 + `appId/kdtId/signSecret` 字段）** | **PARTIAL-NON-BLOCKER** | Shadow Feature；启用只能 SQL 直写（SoT §5）|
| **`apiKey.substring(length-4)` 泄露** | **PARTIAL-NON-BLOCKER**（仅标注，不改）| 用户决策 #9：仅标注，Fix Phase = Post-Audit |
| **Place Details 字段全拿（11 字段）** | **PARTIAL-NON-BLOCKER** | 始终命中 Basic+Contact+Atmosphere 三 SKU（SoT §5）|
| **批量 geocode 无 audit log** | **PARTIAL-NON-BLOCKER** | admin-only 但无 audit log（SoT §5）|
| **有赞 push_url 未在有赞后台注册** | **PARTIAL-BLOCKER** | 后端能收 POST /webhook/youzan，但有赞不会推 → 链路空转（用户决策 #2）|
| **`YouzanWebhookController` 未声明租户隔离校验** | **Phase 2 静态扫描验证** | 待 Phase 2 确认（沿用 Batch 1.2 I019 模式）|

### 5.2 SoT 漂移分类（**用户修正** — 关键）

> ⚠️ 用户 2026-09-13 21:55 修正：preflight §七.4 第 3 项"google_maps_config 表缺失"实际是 **preflight 假设 vs 代码漂移**（DDL 写了 + 迁移未跑 = 表在 DB 不存在），与 **SoT vs 代码漂移** 不同。Phase 0 期间需明确区分两类漂移。

| 漂移类型 | 模式 | Issue 编号 | 处理 |
|---|---|---|---|
| **表 MISSING（DDL 写了但 DB 没建）** | **I002 / I017 模式（MISSING）** | 跑迁移即解（Phase 1 期间明确执行计划）| Fix Phase = Phase 1（可执行修复）|
| **SoT vs 代码 CONFLICT（webhook 接收端 / verifyWebhookSignature / webhook 重放保护 等 5 项）** | **I001 / I018 模式（CONFLICT）** | **中立标 I026-I030** | Fix Phase = Post-Audit 人类决策（3 选项：改文档 / 改代码 / 维持现状）|

**漂移清单分类**（preflight §七.4 + 用户修正）：

| # | 漂移项 | 漂移类型 | Issue 编号 | 处理 |
|---|---|---|---|---|
| 1 | `verifyWebhookSignature` 是孤儿代码 | **SoT vs 代码 CONFLICT** | I026 | 中立标（已被 `YouzanWebhookController:121` 调用）|
| 2 | webhook 接收端缺失 | **SoT vs 代码 CONFLICT** | I027 | 中立标（已完整实现 ContentCachingRequestWrapper + 验签 + 幂等去重 + upsert）|
| 3 | `google_maps_config` 表缺失（用户描述）| **preflight 假设 vs 代码漂移**（**MISSING**）| 不中立标 | DDL 已写 + 迁移未跑 = 表在 DB 不存在 → **跑迁移即解**（I002 模式，Phase 1 期间执行）|
| 4 | `buildWebhookUrl` 是孤儿工具 | **SoT vs 代码 CONFIRMED**（非漂移）| BACKLOG-001 | 沿用 I013 模式（无调用方确认）|
| 5 | 前端缺 UI（`enablePlaceDetails` + `appId/kdtId/signSecret`）| 待 Phase 2 验证 | I028（待登记）| 中立标（Phase 2 静态扫描后定论）|
| 6 | 没有 webhook 重放保护 | **SoT vs 代码 CONFLICT** | I030 | 中立标（已加 webhook_msg_log 幂等去重）|

### 5.3 严格禁止

- 没有运行证据只能标 UNVERIFIED（规则 3 + 9）
- 不允许出现 VERIFIED 但缺 traceId / DB row 的情况
- **EXT-22 特殊约束**：
  - **真实凭据缺失**（GCP / 有赞 push）→ **强制 UNVERIFIED**（不允许在 Mock 降级下声称 VERIFIED）
  - **SoT 漂移中立标**（I026-I030）→ **不允许预设方向**（既不写"SoT 错"也不写"代码错"）
  - **表 MISSING**（DDL 写了但 DB 没建）→ **不允许用 UNVERIFIED 掩盖**（I002 模式：跑迁移即解）

---

## 六、Evidence 目录约定确认

引用：`docs/PROJECT_AUDIT_PLAYBOOK.md` §七 + `.cursor/rules/project-audit.mdc` 规则 12

**每个 Feature 的 Evidence 必须落在固定目录，编号固定，缺一张即 UNVERIFIED**。

### 6.1 Evidence 必备文件（每个 FXXX 目录下）

| 文件 | 内容 | 缺失影响 |
|---|---|---|
| `traceability-matrix.md` | Feature 的全栈追溯（前端文件 → API → 后端方法 → SQL → 表）| 缺则 UNVERIFIED |
| `runtime-evidence.md` 或 `runtime-trace.jsonl` | 运行证据（curl / PowerShell / 日志切片）| L4+ 必须有 |
| `db-evidence.sql` 或 `db-diff.sql` | DB 验证证据（执行前后 diff / EXPLAIN）| L4+ 涉及写库时必须有 |
| `business-evidence.md` | 业务规则匹配证据（前端显示 ↔ 后端返回 ↔ DB 数据）| L5a 必须有 |
| `nfr-evidence.md` | 非功能证据（traceId / 性能 / 多租户隔离 / 权限）| L5b 必须有 |
| `ISSUE.md`（可选）| 该 Feature 的问题清单 | 非空时必须 |

### 6.2 EXT-22 特殊 Evidence 要求

| EXT-22 场景 | 必备文件 | 验证内容 |
|---|---|---|
| **每个 Feature** | `traceability-matrix.md` | 路由 → controller → service → mapper → SQL/表 → DTO 全栈追溯 |
| **Google Maps Place Details Feature** | `runtime-evidence.md` 含 `mock-call-test.txt` + `gcp-billing-gate-test.txt` | mock 路径可调 + GCP 计费闸门测试（dev DB `enable_place_details=0` → PERMISSION_DENIED）|
| **有赞 webhook 接收端 Feature** | `runtime-evidence.md` 含 `signature-verify-test.txt` + `idempotency-test.txt` | 3 组 payload 测试（正常签名 / 篡改 body / 篡改 signature）+ webhook_msg_log 幂等去重测试 |
| **`buildWebhookUrl` 孤儿工具 Feature** | `traceability-matrix.md` + `ISSUE.md` | 标注 BACKLOG-001 + 无调用方证据 |
| **`verifyWebhookSignature` 算法 Feature** | `runtime-evidence.md` 含 `jshell-signature-test.txt` | jshell 直接调用静态方法（沿用 Batch 1.2 Phase 5B 模板）|
| **EcommercePlatform CRUD Feature** | `db-evidence.sql` 含 `ecommerce-platform-schema.txt` | ALTER 后 3 列对齐验证 |
| **`google_maps_config` 表 MISSING** | `db-evidence.sql` 含 `migrate_ext_22_apply.txt` | **跑迁移记录**（Phase 1 期间执行）|

### 6.3 audit/ 目录骨架（Batch 1.3 沿用 v3）

| 子目录 | 职责 | Batch 1.3 产出 |
|---|---|---|
| `audit/baseline/` | 基线快照（Phase -1/0 产出，不可变）：`preflight-batch-1.3.md` + **`scope-freeze-batch-1.3.md`（本文件）**| 本批次已落档 |
| `audit/evidence/F056-.../ ~ F085-.../` | 每 Feature 证据沉淀 | Phase 1 期间按编号创建 |
| `audit/scripts/` | 8 个守门脚本（g1-g8）— **未实现**（preflight §六）| Phase 1-2 期间补（按用户决策 #2）|
| `audit/static-gap/` | Phase 3 静态差距扫描结果 | Phase 3 期间产出 |
| `audit/runtime/` | Phase 4 运行时计划 + Phase 5 验证 | `audit/runtime/plan-batch-1.3.md`（待写）|
| `audit/issues/issue-register.md` | Issue 汇总（沿用 Batch 1.1/1.2，单一文件）| §十九 起追加 I026+ |

### 6.4 回归对照三件套（规则 13）

每次验证必须绑定：

- **commit hash**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2 freeze commit，即本批次起点）+ `656caa72341c96d1b2e703dde993603dda9eebe1`（v3 整体 baseline）+ `12a97ea57a98d268c864fc7c010b82b305d7bb93`（Batch 1.1 freeze commit）
- **DB snapshot**：`audit/baseline/db-snapshot.sql`（417 KB / 270 表 / schema-only）— **注意 google_maps_config / ecommerce_platform 表可能未跑迁移**
- **env.md**：`audit/baseline/env.md`（9,951 B）

---

## 七、EXT-22 .mdc 为 SoT（事实来源）确认

引用：`.cursor/rules/project-audit.mdc` 规则 11 + `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`（EXT-22 Operational Notes）

### 7.1 SoT 规则

> 「审计结论与 EXT-* Operational Notes 冲突时，以 EXT-* 为准并标 ISSUE。」

### 7.2 EXT-22 Operational Notes 内容覆盖

| § | 主题 | 内容要点 |
|---|---|---|
| **§0** | 关键约束 | Google Maps Place Details 计费开关 + `enable_place_details` 写入策略 + `placeDetails` 行为 + 有赞 `appKey` ≠ `appId` + webhook 签名算法 md5 + `buildWebhookUrl` 工具 + SQL 迁移兼容 |
| **§1** | Touchpoints | 4 个 entity + 2 个 googlemaps client + 4 个 ecommerce adapter + 2 个 service + 1 个 webhook controller + SQL 迁移 + 前端 |
| **§2** | Re-confirmation Checklist | Place Details 上线前必查（6 项）+ 有赞 webhook 上线前必查（7 项）+ 数据迁移前必查（4 项）|
| **§3** | Endpoints | Google Maps 5 个 + Ecommerce 6 个（含 webhook 接收端 `POST /webhook/youzan`）|
| **§4** | 字段映射速查 | google_maps_config（enable_place_details）+ ecommerce_platform（app_id/kdt_id/sign_secret）+ google_maps_geocode_log |
| **§5** | 已知问题 / 限制 | 11 个已知 EXT-22 风险（**5 项与代码漂移** — 详见 §五.2 分类）|
| **§6** | 业务侧调用模板 | Google Maps Place Details 调用 + 有赞 webhook 接收端模板 |

### 7.3 Batch 1.3 审计优先级

1. **EXT-22 Operational Notes（事实来源，优先级最高）**
2. `docs/PROJECT_AUDIT_PLAYBOOK.md`（体系规则）
3. `audit/issues/issue-register.md`（历史 Issue 关联）
4. `audit/baseline/preflight-batch-1.3.md`（已知约束）
5. `backend/src/main/java/com/meiyetec/{entity,googlemaps,ecommerce,service,controller}/*`（实际代码）
6. `backend/src/main/resources/{application.yml,db/sql/migrate_ext_*.sql}`（配置 + 迁移）

### 7.4 EXT-22 Operational Notes 已加载确认

- ✅ `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` 已加载（preflight §八.5 已确认）
- ✅ preflight §七.4 已摘录 5 项 SoT 漂移详单
- ✅ preflight §七.4 + 用户修正 #3 已明确漂移分类（CONFLICT vs MISSING）
- Phase 0 期间需再次确认 Operational Notes 与最新代码版本一致（preflight §八.5 验证清单）

---

## 八、能力边界确认（Cursor 不能 / 只能）

引用：`docs/PROJECT_AUDIT_PLAYBOOK.md` §十八 + `.cursor/rules/project-audit.mdc` 规则 9

### 8.1 Cursor **不能**做的事

| ❌ 不能 | 原因 |
|---|---|
| 真打开浏览器点击 | 无 Playwright MCP（沿用 Batch 1.1/1.2 决策）|
| 连接生产数据库执行查询 | 无 MySQL MCP 受限时的能力边界 |
| 调用 GCP Places API 真实接口 | **GCP API Key 缺失**（用户决策 #1：Mock 降级；Phase 5 真实凭据由用户决策 #7 评估）|
| 注册有赞 push_url 到有赞后台 | 需人工到有赞开发者后台操作（用户决策 #2）|
| 触发有赞真实推送 | 需有赞后台配置 push_url（用户决策 #2）|
| 判断业务是否符合 PRD | EXT-22 不在 PRD 主线；Operational Notes 为 SoT |
| 替代人工验收 | 最终验收必须人工签收 |
| 在没有运行证据时声称 VERIFIED | 规则 3 + 9 硬性要求 |

### 8.2 Cursor **只能**做的事

| ✅ 能做 | 方式 |
|---|---|
| 静态分析代码 | grep / glob / Read 文件内容 |
| 生成测试代码和 SQL | 写文件 + 给建议 |
| 根据看到的内容推断 | 在已知 EXT-22 上下文下的合理推断 |
| 标注 UNVERIFIED | 显式标注，不掩盖缺失 |
| **执行 jshell 单元测试** | 直接调用 `YouzanEcommerceAdapter.verifyWebhookSignature` 静态方法（验证签名算法）|
| **生成 Mock webhook 推送样本** | 模拟有赞 webhook payload + signature 头部 |
| **跑 `migrate_ext_22_*.sql` 迁移** | dev DB 执行（用户决策 #3：Phase 1 期间补齐缺失表迁移）|

### 8.3 本批次降级方案（沿用 Batch 1.1/1.2 + 用户决策）

| 工具/能力 | 状态 | 降级方案 |
|---|---|---|
| **MySQL MCP** | ✅ 已配置 | 正常使用（验证 google_maps_config / ecommerce_platform 表存在性 + 字段对齐）|
| **Playwright MCP** | ❌ 未配置（沿用 Batch 1.1/1.2 决策）| Phase 5 E2E 降级为：① 生成 Playwright 脚本供用户手动执行；② 用户手动贴回 evidence；③ 静态抓 HTML + curl API + Vitest 替代 |
| **Redis** | ❓ 未验证（沿用决策）| 不探测；运行时 NPE 风险由 Phase 5 暴露 |
| **GCP Places API 真实调用** | ❌ **凭据缺失**（用户决策 #1 + #7）| Mock 降级（D10 铁律）；jshell 单元测试验证签名算法；真实链路 Phase 5 UNVERIFIED |
| **有赞 webhook 真实推送** | ❌ **push_url 未注册**（用户决策 #2）| Mock 推送样本模拟（生成 payload + signature 头部）；真实推送 Phase 5 UNVERIFIED |
| **守门脚本 g1-g8** | ❌ 未实现（沿用决策 #2）| Phase 1-2 期间补 |
| **EXT-22 特定守门脚本 g12-g15** | ⚠️ 建议（preflight §六.3）| Phase 1 期间评估是否实现 |

### 8.4 EXT-22 评估期间需要用户手动配合的事项

| 事项 | 触发 Phase | 用户配合内容 |
|---|---|---|
| 跑 `migrate_ext_17_google_maps.sql` + `migrate_ext_22_google_maps_place_details.sql` 迁移 | **Phase 1**（用户决策 #3：表 MISSING 跑迁移即解）| 迁移前后 dev DB schema diff + 表存在性确认 |
| 启动后端（含 google-maps / ecommerce 初始化）| Phase 5 | 启动日志 + `/google-maps/health` 截图 |
| 跑 jshell `verifyWebhookSignature` 单元测试 | Phase 5（沿用 Batch 1.2 Phase 5B 模板）| 3 组 payload（正常 / 篡改 body / 篡改 signature）验证返回值 |
| Mock 推送 webhook 样本 | Phase 5 | 生成有赞 webhook payload + signature 头部 → 调 `POST /webhook/youzan` 期望 200 |
| 跨租户访问测试 | Phase 5（如果路由可达）| 普通租户调 `POST /ecommerce/platform` 期望 dataScope 校验 |
| GCP API Key 注入（如有）| Phase 5（用户决策 #7 评估）| 注入 GCP API Key + 调 `/google-maps/place/details` 期望真实响应 |

---

## 九、已知约束（引用 preflight-batch-1.3.md §八）

**本节仅引用，不复制内容**。详见 `audit/baseline/preflight-batch-1.3.md` §八（line 290-413）。

### 9.1 EXT-22 表存在性检查（用户修正 #3 — **关键约束**）

> ⚠️ **重要提示**：Operational Notes §8.1 假设 `google_maps_config` 表缺失，但实际**已通过 `migrate_ext_17_google_maps.sql` 创建**（详见 preflight §七.4 SoT 漂移说明）。Phase 0 已按用户修正分类：**这是 preflight 假设 vs 代码漂移（MISSING 模式），不是 SoT vs 代码漂移（CONFLICT 模式）**。

| 表 | 创建迁移脚本 | 实体类 | DB 实际状态 | 处理 |
|---|---|---|---|---|
| `google_maps_config` | `migrate_ext_17_google_maps.sql:9`（DROP + CREATE）+ `migrate_ext_22_google_maps_place_details.sql`（ALTER）| ✅ `entity/GoogleMapsConfig.java`（含 `enablePlaceDetails`）| ⚠️ **DDL 写了但 dev DB 可能未跑**（I002/I017 MISSING 模式）| **Phase 1 期间跑迁移**（用户决策 #3）|
| `google_maps_geocode_log` | `migrate_ext_17_google_maps.sql:40`（DROP + CREATE）| ✅ `entity/GoogleMapsGeocodeLog.java` | ⚠️ 同上 | **Phase 1 期间跑迁移** |
| `ecommerce_platform` | `create_missing_tables_b4b5.sql:372`（CREATE IF NOT EXISTS）+ `migrate_ext_22_google_maps_place_details.sql:11`（ALTER 3 列）| ✅ `entity/EcommercePlatform.java`（含 `appId/kdtId/signSecret`）| ⚠️ 同上 | **Phase 1 期间跑迁移** |
| `webhook_msg_log` | `migrate_ext_24_webhook_msg_log.sql:8`（CREATE IF NOT EXISTS）| ✅ `entity/WebhookMsgLog.java` | ⚠️ 同上 | **EXT-24 边界**（用户决策 #6：Batch 1.3 只引用不重复审计）|
| `ecommerce_order` | `create_missing_tables_b4b5.sql:411`（CREATE IF NOT EXISTS）| ✅ `entity/EcommerceOrder.java` | ⚠️ 同上 | Phase 1 期间确认（与 EXT-22 接收端共用）|

### 9.2 SoT vs 代码漂移（5 项 — 中立标 I026-I030）

> 沿用 Batch 1.1 I001 + Batch 1.2 I018 模式：**中立标，不预设 SoT 错还是代码错，留人类在 Post-Audit 决策**。

| # | Operational Notes §5 描述 | 当前代码状态（preflight §七.4）| 反转？| Issue 编号 |
|---|---|---|---|---|
| 1 | `verifyWebhookSignature` 是孤儿代码 | `YouzanWebhookController:121` 已调用 | ❌ 反向 | **I026** |
| 2 | webhook 接收端未实现 | `YouzanWebhookController.HandleYouzan` 完整实现 | ❌ 反向 | **I027** |
| 3 | 前端缺 UI（`enablePlaceDetails` 开关 + `appId/kdtId/signSecret`）| 待 Phase 2 验证 | ⏸ 待验证 | **I028**（待登记）|
| 4 | 没有 webhook 重放保护 | 已加 `webhook_msg_log` 幂等去重 | ❌ 反向 | **I029** |
| 5 | `EcommercePlatform` 实体已改（用户描述）| git status 未出现但实体已含 3 字段 | ⚠️ 用户描述与 git status 不一致 | **I030** |

### 9.3 buildWebhookUrl 孤儿工具（BACKLOG-001）

| 维度 | 详情 |
|---|---|
| **声明 + 实现位置** | `YouzanEcommerceAdapter.java:100` |
| **调用方** | **无**（grep 全 backend 仅声明 + 实现，无任何调用方）|
| **SoT 漂移类型** | **SoT vs 代码 CONFIRMED**（非漂移）|
| **处理** | **BACKLOG-001**（沿用 I013 dead code 模式）|
| **Phase 1 任务** | Feature Inventory 标注 UNVERIFIED；Phase 7 Post-Audit 决定删除 / 保留 / 改造为注册工具 |

### 9.4 有赞 webhook 接收端（已实现但 SoT 未更新）

| 维度 | 详情 |
|---|---|
| **文件路径** | `controller/YouzanWebhookController.java` |
| **路由** | `POST /webhook/youzan?kdt_id={kdtId}`（L77）|
| **处理流程** | ContentCachingRequestWrapper 读 rawBody（L77-89）+ 按 kdt_id 反查 `EcommercePlatform`（L91-105）+ 调 verifyWebhookSignature 验签（L121）+ msg_id 幂等去重 `webhook_msg_log`（L136-148）+ upsert `ecommerce_order`（L182-186）+ 写 webhook_msg_log（L195-200）|
| **与 EXT-22 SoT 关系** | ❌ **SoT §5 第一条描述反向**（说"未实现"，实际已实现）|
| **SoT 漂移处理** | 中立标 I027（Phase 0 登记，Fix Phase = Post-Audit 人类决策）|
| **Phase 5 验证需求** | Mock 推送样本 + jshell 单元测试（真实有赞推送由用户决策 #7 评估）|

### 9.5 EcommercePlatform 字段变更（已实施但 SoT 描述漂移）

| 维度 | 详情 |
|---|---|
| **实体类** | `entity/EcommercePlatform.java` |
| **新增字段** | `appId`(L19) + `kdtId`(L21) + `signSecret`(L23) |
| **DB 迁移** | `migrate_ext_22_google_maps_place_details.sql:11` ALTER TABLE ecommerce_platform ADD COLUMN app_id/kdt_id/sign_secret |
| **SoT 一致性** | ✅ 实体 / DB Migration / SoT 完全对齐（仅用户描述与 git status 不一致）|
| **Phase 5 验证需求** | MySQL MCP 验证 dev DB 中 3 列存在 |

### 9.6 有赞 webhook 签名算法（Operational Notes §0.5 + 静态确认）

```java
// YouzanEcommerceAdapter.verifyWebhookSignature（line 114）
expected = lowercase( hex( md5( signSecret + rawBody ) ) )
compare expected == X-Youzan-Sign header value   (case-insensitive)
```

- 实现位置：`YouzanEcommerceAdapter.java:114`
- 调用方：`YouzanWebhookController.java:121`
- rawBody 来源：`ContentCachingRequestWrapper`（避免 Jackson 重排破坏签名）— L77-89
- **SoT 与实现完全对齐**

**Phase 5 验证需求**：
- 准备 3 组 payload：正常签名 / 篡改 body / 篡改 signature → jshell 调用 verifyWebhookSignature 返回值
- 用 Mock 推送样本调 `POST /webhook/youzan` 期望 200

### 9.7 GCP Places API 计费闸门（Operational Notes §0.3）

```
门禁顺序（GoogleMapsService.placeDetails）：
1. tenantId + enabled=1 配置存在 + apiKey 非空 → 否则 NOT_FOUND
2. enablePlaceDetails == 1 → 否则 PERMISSION_DENIED（不调 Google）
3. 调用 geocodingClient.placeDetails(apiKey, placeId, lang) → 真实链路
```

- 步骤 2 是**唯一计费闸门**，前端 / 后端 / 中间人绕过它都能直接打到 Google
- Phase 5 验证需求：dev DB 中 `enable_place_details=0` → 调 `/google-maps/place/details` → 期望 `PERMISSION_DENIED`（不调 Google，无 GCP 计费）

### 9.8 受保护页面零风险（引用 preflight §五）

- EXT-22 审计期间**直接不触达**受保护页面（`/dashboard` / `/mh/visual` / `/mh/consent`）
- Phase 2 静态扫描确认 EXT-22 与受保护页面后端接口无间接依赖（用户决策 #8）

### 9.9 历史审计沉淀不在 v3 目录（沿用 Batch 1.1/1.2）

- `docs/audit/` 25+ 个 `audit_*.txt` + `BEAUTY_SAAS/` 子目录（28+ 报告）
- 不迁移（按 `audit/README.md` §2）
- Phase 7 引用时使用相对路径 `../docs/audit/BEAUTY_SAAS/19_REPAIR_ROADMAP_V3.md`

### 9.10 与 Batch 1.1/1.2 独立性检查（引用 preflight §七.5）

| 检查项 | 结果 |
|---|---|
| **代码包共享** | ⚠️ 部分共享（共用 `EcommercePlatform` / `EcommerceOrder` / `WebhookMsgLog`）|
| **DB 表共享** | ⚠️ 部分共享（`ecommerce_platform` / `ecommerce_order` / `webhook_msg_log`）|
| **配置共享** | ❌ 无共享 |
| **业务依赖** | ❌ EXT-22 不依赖 EXT-23/EXT-24 |
| **风险传染** | ⚠️ Batch 1.2 I019（PluginService 跨租户读风险）的模式可能再犯于 `EcommerceService` / `YouzanWebhookController` |
| **审计可分离性** | ✅ Feature Inventory 可分离（Phase 1 期间明确边界划分）|

### 9.11 共用 entity/table 边界（用户决策 #6）

| 共享资源 | 所属 EXT-* | Batch 1.3 处理 |
|---|---|---|
| `EcommercePlatform` entity + `ecommerce_platform` 表 | EXT-22（webhook 接收端反查）+ EXT-16（marketplace CRUD）| **Feature Inventory 期间明确边界**：webhook 接收端反查 F062（EXT-22 视角），CRUD F080+（EXT-16 视角，引用 EXT-22 边界）|
| `EcommerceOrder` entity + `ecommerce_order` 表 | EXT-22（webhook 接收端 upsert）+ EXT-16（marketplace order sync）| 同上（Phase 1 明确）|
| `WebhookMsgLog` entity + `webhook_msg_log` 表 | **EXT-24**（CRM 集成）+ EXT-22（webhook 接收端幂等去重引用）| **EXT-24 边界**（用户决策 #6）；Batch 1.3 只引用，不重复审计 webhook_msg_log 表结构与业务规则 |

---

## 十、待用户确认项（**用户已确认** — 详见 preflight §十）

### 10.1 关键 5 项（用户已确认）

| # | 项 | 用户决策 | 当前状态 |
|---|---|---|---|
| **1** | **GCP Places API 凭据 + 有赞 push_url 注册** | ✅ **Mock 降级**（接受 D10 铁律）| Phase 5 仅验证 mock 链路 + jshell 单元测试；真实链路 UNVERIFIED |
| **2** | **EXT-22 Operational Notes §5 已知问题 11 项的反转** | ✅ **中立标 I026-I030**（接受 I001 / I018 模式）| Issue Register §十九 起追加 I026-I030；Fix Phase = Post-Audit 人类决策 |
| **3** | **`buildWebhookUrl` 孤儿工具** | ✅ **BACKLOG-001**（接受 I013 模式）| Phase 1 标 BACKLOG-001；Phase 7 Post-Audit 决定 |
| **4** | **Phase -1 报告完成后 commit 策略** | ✅ **选 C**（再 stash 一次，Phase -1 完成后保持 working tree clean）| Phase -1 完成后立即 `git stash push -m "after-phase-minus-1-clean-before-phase-0-batch-1.3"` |
| **5** | **g1-g8 守门脚本补齐时机** | ✅ **Phase 1-2 期间补** | 不阻塞 Phase 0；Phase 1-2 期间实现 |

### 10.2 次要 5 项（用户已确认）

| # | 项 | 用户决策 | 当前状态 |
|---|---|---|---|
| **6** | **共用 entity/table 边界** | ✅ **`webhook_msg_log` 归 EXT-24，Batch 1.3 只引用不重复审计** | Phase 1 Feature Inventory 期间明确边界；Feature ID 按 EXT-22 视角 |
| **7** | **GCP 真实凭据评估时机** | ✅ **Phase 5 时再评估** | Phase 0-1 不需要 GCP 凭据；Phase 5 由用户决策是否注入 |
| **8** | **受保护页接口清单** | ✅ **Phase 2 静态扫描** | Phase 2 期间列出 `/dashboard` / `/mh/visual` / `/mh/consent` 后端接口 + 验证 EXT-22 无间接依赖 |
| **9** | **GCP API Key `getConfig` 端点泄露** | ✅ **仅标注，不改** | 沿用 SoT — 仅标注，Fix Phase = Post-Audit 人类决策 |
| **10** | **freeze commit** | ✅ **接受**（追加 `audit(freeze): Batch 1.2 完结 + Batch 1.3 启动`）| Phase 0 启动前 commit；与 Batch 1.1→1.2 freeze 模式一致 |

### 10.3 用户修正（2026-09-13 21:55）

| # | 项 | 用户修正 |
|---|---|---|
| **A** | **第 3 项漂移分类** | "google_maps_config 表缺失"实际是 **preflight 假设 vs 代码漂移**（DDL 写了 + 迁移未跑 = 表在 DB 不存在），与 **I002/I017 MISSING 模式**相同，不是 I001/I018 CONFLICT 模式 |
| **B** | **漂移分类原则** | Phase 0 / Phase 1 / Issue Register 期间明确：<br>① 涉及"表缺失"的 → I002 模式（MISSING，跑迁移即解）<br>② 涉及"webhook 代码 vs notes 描述不一致"的 → I001/I018 模式（CONFLICT，中立标）<br>③ 不混为一谈 |

### 10.4 默认决策影响

| 默认 | 影响 |
|---|---|
| **#1 GCP / 有赞凭据缺失 → Mock 降级** | Phase 5 仅验证 mock 链路 + jshell 单元测试；真实链路（`@ConditionalOnMissingBean` 禁用 + Mock 关闭）验证标记为 UNVERIFIED |
| **#2 SoT 漂移中立标** | I026-I030 不预设方向；Fix Phase = Post-Audit 人类决策（3 选项：改文档 / 改代码 / 维持现状）|
| **#3 buildWebhookUrl 标 BACKLOG** | 不影响 Phase 5 实证；Phase 7 Post-Audit 决定删除 / 保留 |
| **#4 commit 策略 C（再 stash 一次）** | Phase -1 完成后立即 `git stash push`；Phase 0 scope-freeze 文件 commit 时保持 working tree clean |
| **#5 g1-g8 Phase 1-2 期间补** | 不阻塞 Phase 0；Phase 1-2 期间实现 |
| **#6 webhook_msg_log 归 EXT-24** | Batch 1.3 只引用不重复审计；Feature Inventory 期间明确边界 |
| **#7 GCP 真实凭据 Phase 5 评估** | Phase 0-1 不需要 GCP 凭据；Phase 5 由用户决策是否注入 |
| **#8 受保护页接口 Phase 2 扫描** | Phase 2 期间列出 + 验证 EXT-22 无间接依赖 |
| **#9 GCP Key 泄露仅标注** | 沿用 SoT；Fix Phase = Post-Audit 人类决策 |
| **#10 freeze commit 接受** | git history 清晰；Phase 0 scope-freeze 锚点明确 |

---

## 十一、Phase 0 准入自检

| 检查项 | 结果 |
|---|---|
| preflight-batch-1.3.md 已落档 | ✅ `audit/baseline/preflight-batch-1.3.md`（413 行）|
| 10 项决策已回答（5 关键 + 5 次要）| ✅ 用户 2026-09-13 21:55 确认 |
| 用户修正 #3 漂移分类已落档 | ✅ §五.2 + §九.2 明确 CONFLICT vs MISSING 分类原则 |
| Phase 0 freeze commit 计划 | ✅ 沿用 Batch 1.1→1.2 freeze 模式（追加 `audit(freeze): Batch 1.2 完结 + Batch 1.3 启动`）|
| scope-freeze-batch-1.3.md 可读 | ✅（本文件）|
| 项目定位已明确 | ✅ §一 |
| 验收范围已冻结（含 EXT-22 详细验证范围）| ✅ §二 |
| Feature ID 规则已确认（含编号策略 F056 起）| ✅ §三 |
| L0-L5 等级已确认 | ✅ §四 |
| Result 子标签已确认（含 EXT-22 特殊判定 + 漂移分类原则）| ✅ §五 |
| Evidence 目录已约定（含 EXT-22 特殊要求）| ✅ §六 |
| EXT-22 SoT 已确认 | ✅ §七 |
| 能力边界已确认（含 GCP / 有赞 Mock 降级）| ✅ §八 |
| 已知约束已引用（含 5 项 SoT 漂移中立标 I026-I030）| ✅ §九 |
| 待用户确认项已列出（10 项 + 用户修正 #3）| ✅ §十 |

✅ **Phase 0 完成。可以进入 Phase 1（Feature Inventory — F056 起）。**

---

## 十二、本文件状态

| 项 | 值 |
|---|---|
| **状态** | 不可变（frozen）|
| **变更控制** | 任何改动需用户明确同意 |
| **下一阶段产出位置** | `audit/feature-inventory.md` §1.3（F056-F085 EXT-22 Feature 清单）|
| **下一阶段 Prompt 模板** | 见 `docs/PROJECT_AUDIT_PLAYBOOK.md` §十「Phase 1」|

---

**Scope Freeze（Batch 1.3）完成。EXT-22 Google Maps + 有赞范围已冻结 + 10 项决策已落档 + F056 编号策略已确认 + 5 项 SoT 漂移中立标 I026-I030 已分类（CONFLICT vs MISSING 原则明确）+ `buildWebhookUrl` 标 BACKLOG-001 + GCP/有赞凭据 Mock 降级 + freeze commit 策略 C 接受。⏸ 等用户确认 → 进入 Phase 1（Feature Inventory，F056 起）。**
