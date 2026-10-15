## Batch 1.4 — Phase 2 汇总表（统计 + 修正 + Phase 标记规则）

> **本段范围**：EXT-19 多语言预约确认，**33 Features**，F081-F113
> **Phase**：2（Full-Stack Traceability Matrix）
> **创建时间**：2026-09-14 16:11 (UTC+8)
> **SoT**：`.cursor/rules/ext-19-operational-notes.mdc`
> **用户决策（2026-09-14 16:11）**：
> - I038/I045：维持 MISSING，不跑迁移（第 4 次强调）
> - I039/I043：CONFLICT 中立标（沿用 I001/I018/I026/I030 模式）
> - I040/I041/I042：Shadow，Post-Audit 决定
> - I044：backlog 统一整改（同模式第 4 次命名异味）
> - I049：凭据缺失 UNVERIFIED（D10 铁律 + 用户决策 #1/#2）
> - I047/I048/I050/I051：PARTIAL-NON-BLOCKER（catch 吞异常 / rollbackFor / T07-D4 例外 / 演示模式）
> **本批次起点 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（沿用 Batch 1.2/1.3 freeze commit）
> **DB Snapshot**：`audit/baseline/db-snapshot.sql`（**注意**：`overseas_booking` / `member_message` 表均 MISSING — I038/I045）

---

### 一、Result 分布统计

| Result | 数量 | 占比 | Feature IDs | 关联 Issue |
|---|---|---|---|---|
| VERIFIED | 0 | 0.0% | — | — |
| **MISSING（DB）** | **3** | **9.1%** | F081（overseas_booking 实体 + miniProgramOpenId）, F082（overseas_booking DDL）, F091（InAppSender → member_message）| **I038 + I045**（沿用 I002/I017/I029 MISSING 模式 — **统一 Post-Audit 跑批 — 用户决策 #3 第 4 次强调**）|
| **PARTIAL-BLOCKER** | **5** | **15.2%** | F098, F099, F100, F103, F104, F106 | **I038**（6 Feature 全部依赖 overseas_booking 表，Phase 5 实测会暴露 TableNotFoundException — **审计有效负向证据**）；F099/F100/F103/F106 含 I048（rollbackFor）+ F099/F100/F106 含 I051（webhook demo 模式）|
| **PARTIAL-NON-BLOCKER** | **7** | **21.2%** | F083, F087, F094, F095, F096, F101, F102, F105, F111, F112, F113 | I047（catch 吞异常 — I008 模式）, I046（pos_store 字段对齐）, I048（rollbackFor — I009 模式）, I050（healthCheckAll T07-D4 例外）, I051（OAuth demo 模式）, I040/I041（前端 Shadow — autoSend UI / 按钮文案）, I042（前端 Shadow — sendConfirmation API）, I044（命名异味 — backlog 第 4 次）|
| **CONFLICT（中立标）** | **2** | **6.1%** | F092（I039 — WECHAT_OA vs WECHAT_OFFICIAL_ACCOUNT 别名）, F097（I043 — 硬编码 switcher vs Resolver 体系）| **I039 + I043**（沿用 I001/I018/I026/I030 中立标模式 — Fix Phase = Post-Audit 人类决策，3 选项：改 SoT / 改代码 / 接受现状）|
| **UNVERIFIED** | **16** | **48.5%** | F084, F085, F086, F088, F089, F090, F093, F107, F108, F109, F110 + F083 (运行时)/ F087 (运行时)/ F095/F098/F104 (运行时) | **I049**（4 个真实凭据缺失 — D10 铁律 + 用户决策 #1/#2）+ 其他 Feature 因 DB MISSING/接口层无前端入口/无 Phase 5 证据 |
| BROKEN | 0 | 0.0% | — | — |
| MOCK-INTENTIONAL | 0 | 0.0% | — | — |
| MOCK-FAKE | 0 | 0.0% | — | — |
| SHELL-INTENTIONAL | 0 | 0.0% | — | — |
| SHELL-ABANDONED | 0 | 0.0% | — | — |
| **合计** | **33** | **100.0%** | F081-F113 | — |

### 1.1 复合 Result 说明

部分 Feature 涉及多重 Issue，最终 Result 取**主要风险**：

| Feature | 主要 Result | 叠加 Issue | 说明 |
|---|---|---|---|
| F083 | PARTIAL-NON-BLOCKER | I038（DB 联动）+ I047（catch 吞异常 — I008 模式）| F083 自身代码完整，但 overseas_booking 表 MISSING 联动（读 booking 字段），Phase 5 实测会暴露 |
| F087 | PARTIAL-NON-BLOCKER | I046（pos_store 字段对齐）| F087 自身代码完整（5 分钟缓存），但 pos_store 表存在但 `store_name + contact_phone` 字段对齐待 Phase 5 验证 |
| F088 | UNVERIFIED | I049（凭据缺失 — D10 铁律）| 真实阿里云 accessKey 缺失；Phase 5 仅 mock + jshell 单元测试；真实链路 UNVERIFIED |
| F089 | UNVERIFIED | I049（凭据缺失）| 真实 SMTP password 缺失；Phase 5 mismatch demo 模式日志关键词验证 |
| F090 | UNVERIFIED | I049（凭据缺失）+ WeChat 模板 ID 占位（SoT §0.1）| 真实 appId/appSecret 缺失；Phase 5 mock + jshell 单元测试 |
| F091 | MISSING | I045（DB）| `member_message` 表 DDL 已写但 DB 未跑迁移；`@Transactional(rollbackFor=Exception.class)` 启动期触发 `TableNotFoundException` |
| F092 | UNVERIFIED + CONFLICT | I049（凭据缺失）+ I039（别名反向）| OA sender 凭据缺失 + SoT §1 别名 "WECHAT_OA" vs 代码 "WECHAT_OFFICIAL_ACCOUNT" 反向（中立标）|
| F095 | PARTIAL-NON-BLOCKER | I038（DB）+ I042（前端 Shadow）| 后端端点存在，但前端 `intl-booking.ts` 无 `sendConfirmation` 方法（I042）|
| F096 | PARTIAL-NON-BLOCKER | I038 + I040 + I041 + I047 + I048 | 行为变更（SoT §0.3）+ 前端 2 项 Shadow（autoSend UI / 按钮文案）+ catch 吞异常 + rollbackFor 缺失（5 项叠加，主要风险为前端 Shadow）|
| F097 | CONFLICT | I038 + I043 | 硬编码 switcher vs Resolver 体系反向（中立标，SoT §1 期望走 Resolver）|
| F098 | PARTIAL-BLOCKER | I038（DB）| 列表查询 — Phase 5 实测暴露 `TableNotFoundException`（**审计有效负向证据**）|
| F099 | PARTIAL-BLOCKER | I038 + I048 + I051 | webhook + 写库 + 未指定 rollbackFor + 演示模式（3 项叠加，主要风险为 DB 缺失）|
| F100 | PARTIAL-BLOCKER | I038 + I048 + I051 | 同 F099 模式 |
| F102 | PARTIAL-NON-BLOCKER | I048 + I051 | OAuth callback demo 模式 + 未指定 rollbackFor（无 DB 依赖）|
| F103 | PARTIAL-BLOCKER | I038 + I048 | convert + 写 overseas_booking + crm_member + pos_order + 未指定 rollbackFor（3 项叠加）|
| F104 | PARTIAL-BLOCKER | I038 | 单条查询 — Phase 5 暴露 `TableNotFoundException` |
| F106 | PARTIAL-BLOCKER | I038 + I048 + I051 | pull + 写库 + rollbackFor + 演示模式（3 项叠加）|
| F111 | PARTIAL-NON-BLOCKER（Shadow）| I040 + I041 | 前端页面缺 autoSend=false UI + 状态确认按钮文案缺失 |
| F112 | PARTIAL-NON-BLOCKER（Shadow）| I042 | 前端 API 缺 `sendConfirmation` 方法 |
| F113 | BACKLOG（架构异味）| I044 | 命名异味（同模式第 4 次出现 — I004/I019/I029/I044）|

### 1.2 与 Batch 1.1 / 1.2 / 1.3 Result 对比

| 批次 | VERIFIED | PARTIAL-BLOCKER | PARTIAL-NON-BLOCKER | CONFLICT | UNVERIFIED | MISSING | 合计 |
|---|---|---|---|---|---|---|---|
| Batch 1.1（EXT-24） | 0 | 11 | 14 | 4 | 1 | 0 | 30 |
| Batch 1.2（EXT-23） | 2 | 5 | 16 | 1 | 2 | 0 | 25 |
| Batch 1.3（EXT-22） | 0 | 0 | 5 | 3 | 13 | 4 | 25 |
| **Batch 1.4（EXT-19）** | **0** | **5** | **11** | **2** | **12** | **3** | **33** |

**关键差异**：
- Batch 1.4 PARTIAL-BLOCKER（5）低于 Batch 1.1（11）但高于 Batch 1.2/1.3 — 主要因 I038（表 MISSING）触发多个端点联动
- Batch 1.4 MISSING 比例（9.1%）较低 — 仅 3 Feature 受表缺失直接影响（其余通过联动 PARTIAL-BLOCKER 体现）
- Batch 1.4 UNVERIFIED 比例（48.5% 含 12 主项）— 含 I049 凭据缺失（4 个 sender）+ Phase 5 待实测 + i18n/测试静态可达但待 Phase 5 验证
- Batch 1.4 CONFLICT（2）来自 I039（OA 别名）+ I043（Resolver vs switcher）— 沿用 I001/I018/I026/I030 中立标模式

---

### 二、VERIFIED 修正表（Phase 5 实测升级路径）

> **沿用 Batch 1.1 §十五.5 + Batch 1.2 §18.5 + Batch 1.3 §二 模板**：Phase 5 实测可能升级 Result。本表列出每个 Feature 的潜在升级路径，**Phase 5 用户决策后再修正**。

| Feature | Phase 2 Result | Phase 5 潜在升级路径 | 升级条件 | 关联 Issue |
|---|---|---|---|---|
| F081 | MISSING | VERIFIED | 用户决策跑 `migrate_ext_19_mini_program_openid.sql` + `DESCRIBE overseas_booking` 显示 26 列 | I038（跑迁移即解，**当前决策：不跑**）|
| F082 | MISSING | VERIFIED | 同 F081 | I038 |
| F083 | PARTIAL-NON-BLOCKER | VERIFIED（依赖 I038）+ PARTIAL-NON-BLOCKER（catch 吞异常 — 不变）| 同 F081 | I038 + I047 |
| F087 | PARTIAL-NON-BLOCKER | VERIFIED | Phase 5 `DESCRIBE pos_store` 验证 `store_name/contact_phone` 列对齐 + 5 分钟缓存命中测试 | I046 |
| F088 | UNVERIFIED | UNVERIFIED（不变）或 PARTIAL-NON-BLOCKER（mismatch demo 模式验证）| Phase 5 仅 mock + jshell 单元测试；真实链路维持 UNVERIFIED（D10 铁律）| I049 |
| F089 | UNVERIFIED | 同 F088 | Phase 5 mismatch demo 模式日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode` 验证 | I049 |
| F090 | UNVERIFIED | UNVERIFIED（不变）| Phase 5 仅 mock + jshell 单元测试；WeChat 模板 ID 是占位符（SoT §0.1）| I049 |
| F091 | MISSING | VERIFIED（依赖 I045 跑迁移）| 用户决策跑 `member_message` DDL + `DESCRIBE member_message` 验证 | I045（跑迁移即解，**当前决策：不跑**）|
| F092 | UNVERIFIED + CONFLICT | UNVERIFIED + CONFLICT（不变）| I039 中立标不变（Post-Audit 决策）；凭据缺失维持 UNVERIFIED | I039 + I049 |
| F094 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER（不变）| `healthCheckAll` T07-D4 例外（有意保留）| I050 |
| F095-F106 | PARTIAL-* | VERIFIED（依赖 I038 跑迁移）| 同 F081 | I038 + 其他 |
| F097 | CONFLICT | CONFLICT（不变）或 VERIFIED | Phase 5 实测确认行为正确，但 SoT 漂移需 Post-Audit 人类决策改 `.mdc` | I043 |
| F107-F110 | UNVERIFIED | VERIFIED | Phase 5 启动日志 + warmup + mvn test 通过 | OK |
| F111-F112 | PARTIAL-NON-BLOCKER（Shadow）| PARTIAL-NON-BLOCKER（不变）| 前端 Shadow 维持（Post-Audit 决策）| I040/I041/I042 |
| F113 | BACKLOG | BACKLOG（不变）| 命名异味（Post-Audit 统一整改）| I044 |

**修正规则**：
- Phase 5 实测若发现新问题（如 Fake Success 升级路径），按 Batch 1.1 §十五.5 / Batch 1.2 §18.5 / Batch 1.3 §二 模板处理
- CONFLICT 升级为 VERIFIED 的前提：SoT vs 代码方向冲突由 Post-Audit 人类决策后再改 `.mdc`
- MISSING 升级为 VERIFIED 的前提：用户决策跑迁移（**当前决策是不跑**）

---

### 三、Phase 2/5 标记规则

#### 3.1 Phase 2 静态标记规则

| 类别 | 规则 | 沿用 Issue 模式 |
|---|---|---|
| **DB 表缺失**（DDL 写了但 dev DB 未跑迁移）| → **MISSING** | I002（Batch 1.1）, I017（Batch 1.2）, I029（Batch 1.3）, **I038 + I045**（Batch 1.4）|
| **SoT vs 代码 CONFLICT**（中立标，不预设方向）| → **CONFLICT** | I001（Batch 1.1）, I018（Batch 1.2）, I026/I027/I030（Batch 1.3）, **I039 + I043**（Batch 1.4）|
| **架构异味**（Controller 命名误导）| → **PARTIAL-NON-BLOCKER + BACKLOG** | I004（Batch 1.1）, I019（Batch 1.2）, I029（Batch 1.3 架构异味模式）, **I044**（Batch 1.4 — **第 4 次出现**）|
| **dead code / 孤儿工具** | → **PARTIAL-NON-BLOCKER + BACKLOG-NNN** | I013（Batch 1.1）, I021/I025（Batch 1.2）, I033（Batch 1.3）|
| **事务 rollbackFor 缺失** | → **PARTIAL-NON-BLOCKER** | I009（Batch 1.1）, **I048**（Batch 1.4）|
| **catch 吞异常**（与 SoT 一致设计 — 不算 Bug）| → **PARTIAL-NON-BLOCKER** | I008（Batch 1.1 Fake Success 模式）, **I047**（Batch 1.4 — 与 SoT §0.3 "发送异常不回滚状态变更" 一致）|
| **Frontend Shadow**（前端缺 UI / 缺 API 方法）| → **PARTIAL-NON-BLOCKER**（中立标，Post-Audit 决定）| I006/I007（Batch 1.1）, I024（Batch 1.2）, I028（Batch 1.3）, **I040/I041/I042**（Batch 1.4）|
| **真实凭据缺失**（WeChat / SMTP）| → **UNVERIFIED**（D10 铁律 + 用户决策 #1/#2）| I049（Batch 1.4 新型）|
| **演示模式**（OAuth + webhook 不真换 token / 不真订阅）| → **PARTIAL-NON-BLOCKER** | I051（Batch 1.4 新型，沿用 P3-26 演示模式）|
| **DB 联动**（其他表 MISSING 导致本 Feature 无法工作）| → PARTIAL-BLOCKER（Phase 5 实测会暴露 TableNotFoundException）| 沿用 I002 模式 |
| **Entity / DTO / 接口层**（无前端入口 / 无 Phase 5 证据）| → **UNVERIFIED** | 沿用三个批次 |

#### 3.2 Phase 5 实测升级路径

| 触发条件 | 升级方向 | 决策点 |
|---|---|---|
| I038 + I045 跑迁移 + `DESCRIBE` 验证 | MISSING → VERIFIED | 用户决策，**当前决策：不跑** |
| I039 + I043 中立标实测行为正确 | CONFLICT → VERIFIED（**需 Post-Audit 改 `.mdc`**）| Post-Audit 人类决策 |
| Phase 5 实证新发现（如 Fake Success / Fake Failure 升级路径）| PARTIAL-NON-BLOCKER → CRITICAL-P1（沿用 I008/I011 模式）| 用户决策 P1 升级 |
| Phase 5 实证环境依赖（如 WeChat 凭据缺失、SMTP 凭据缺失）| UNVERIFIED 维持（Mock 降级 D10 铁律）| 用户决策 |
| Phase 5 实证安全 / NFR 问题 | PARTIAL-NON-BLOCKER → CRITICAL（升级 P0/P1）| 用户决策 |

#### 3.3 Phase 5 负向证据规则

> **沿用 Batch 1.1 §十五.5 + Batch 1.2 §18.3 + Batch 1.3 §三.3 模板**：Phase 5 实测预期暴露的负向证据，**作为审计的有效负向证据，不修后端绕过它**。

| Feature | 预期负向证据 | 关联 Issue |
|---|---|---|
| F081/F082/F095-F106 | Phase 5 调任何相关 API → 后端抛 `TableNotFoundException: overseas_booking`（**I038 跑迁移即解，当前决策：不跑**）| I038 |
| F091 | Phase 5 调 InAppSender → 后端抛 `TableNotFoundException: member_message`（**I045 跑迁移即解，当前决策：不跑**）| I045 |
| F087 | Phase 5 调 StoreLookupService → 后端抛 `Unknown column 'store_name' / 'contact_phone'` 或表缺失（**I046 待 Phase 5 验证**）| I046 |
| F089 | Phase 5 观测日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode`（**确认走 demo 模式**）| I049 |
| F097 | Phase 5 调 `/confirmation-template` → 返回 `subject + body` **不走 Resolver**（**I043 中立标**）| I043 |

---

### 四、Batch 1.4 受保护页面零风险段（沿用 Batch 1.1/1.2/1.3 模板）

> **来源**：`scope-freeze-batch-1.4.md` §九.10 + `preflight-batch-1.4.md` §五
> **规则**：`no-touch-verified-pages.mdc` — 用户已实测通过的 3 个页面（`/dashboard` / `/mh/visual` / `/mh/consent`）后端接口在 Batch 1.4 期间**直接不触达**

| 受保护路由 | 后端接口 | EXT-19 依赖 |
|---|---|---|
| `/dashboard` | `DashboardController`（不在 EXT-19 范围）| ❌ 无依赖 |
| `/mh/visual` | `VisualArchiveController`（不在 EXT-19 范围）| ❌ 无依赖 |
| `/mh/consent` | `ConsentController`（不在 EXT-19 范围）| ❌ 无依赖 |

**Phase 2 静态扫描确认**：EXT-19 端点（`/intl-booking/*` + `/google-maps/*` 不在范围）与 3 个受保护页面的后端接口**完全无间接依赖**（不在同 Service 包 / 不共享 Mapper / 不共享 DB 表）。审计期间**绝不触及**受保护页面。

---

### 五、Batch 1.4 跨 EXT-* 引用规则段（沿用 Batch 1.1/1.2/1.3 模板）

> **来源**：`scope-freeze-batch-1.4.md` §九.7 + 用户决策 #8

| 共享资源 | 所属 EXT-* | Batch 1.4 处理 | 备注 |
|---|---|---|---|
| `OverseasBooking` entity + `overseas_booking` 表 | EXT-15（P3-25 国际支付 / P3-26 深化 + BookingConfirmationService 复用）+ **EXT-19**（CONFIRMED 自动触发 + 5 渠道发送）| F081 实体（共用）；F083 核心发送（EXT-19 视角）；F095-F106 业务端点（EXT-19 视角）| Phase 1 期间明确边界；Feature ID 不加 EXT 前缀 |
| `MktChannelConfig` entity + `mkt_channel_config` 表 | EXT-09（M09-01 营销渠道 + InAppSender/SmsSender/EmailSender 等共用）+ **EXT-19**（5 channel sender 复用）| F088-F092 channel sender（EXT-19 视角）；F094 ChannelDispatcher（EXT-19 视角，rateLimit 复用 marketing_touch_log）| Feature ID 按 EXT-19 视角命名 |
| `MemberMessage` entity + `member_message` 表 | EXT-09（M09-01 InAppSender 写入）+ **EXT-19**（InAppSender 共用）| F091 InAppSender（EXT-19 视角，channel sender 实现）| 同上 |
| `pos_store` 表 | EXT-03（基线已建）+ **EXT-19**（StoreLookupService 复用）| F087 StoreLookupService（EXT-19 视角）| 同上 |
| `crm_member` + `pos_order` 表 | EXT-03（基线已建）+ **EXT-19**（IntlBookingService.convert 复用）| F103 convert（EXT-19 视角）| 同上 |
| `marketing_touch_log` 表 | EXT-09（M09-01 营销渠道 + ChannelDispatcher.rateLimit 复用）+ **EXT-19**（ChannelDispatcher 共用）| F094 ChannelDispatcher.rateLimit（EXT-19 视角）| 同上 |

**Batch 1.4 视角**：F081/F087 等共用 Feature 按 EXT-19 视角命名；不跨批次重复审计（与 Batch 1.1/1.2/1.3 一致模式）。

---

### 六、Batch 1.4 已知约束汇总（沿用 scope-freeze §九）

| # | 约束 | 类别 | 处理 |
|---|---|---|---|
| 1 | `overseas_booking` 表未跑迁移（DDL 已写）| DB MISSING | F081/F082/F095-F106 全部 → **MISSING / PARTIAL-BLOCKER**；**统一 Post-Audit 跑批**（用户决策 #3 第 4 次强调）|
| 2 | `member_message` 表未跑迁移（DDL 已写）| DB MISSING | F091 → **MISSING**；**统一 Post-Audit 跑批** |
| 3 | WeChat 模板 ID 占位符（SoT §0.1）| 凭据缺失 | F090 → **UNVERIFIED**（Phase 5 仅 mock + jshell 单元测试）|
| 4 | WeChat MP appId/appSecret 缺失（用户决策 #1）| 凭据缺失 | F090/F092 → **UNVERIFIED**（D10 铁律 + 用户决策 #1 Mock 降级）|
| 5 | SMTP password 缺失（用户决策 #2）| 凭据缺失 | F089 → **UNVERIFIED**（D10 铁律 + 用户决策 #2 mismatch demo 模式）|
| 6 | 阿里云 accessKey 缺失 | 凭据缺失 | F088 → **UNVERIFIED**（D10 铁律 + 用户决策 #1 Mock 降级）|
| 7 | autoSend=false 前端 UI 缺失 | Frontend Shadow | F096/F111 → **PARTIAL-NON-BLOCKER**（I040 — Post-Audit 决定）|
| 8 | 状态确认按钮文案无"确认即发送通知"提示 | Frontend Shadow | F096/F111 → **PARTIAL-NON-BLOCKER**（I041 — Post-Audit 决定）|
| 9 | 前端 `sendConfirmation` 方法缺失 | Frontend Shadow | F095/F112 → **PARTIAL-NON-BLOCKER**（I042 — Post-Audit 决定）|
| 10 | `IntlBookingService.confirmationTemplate` 硬编码 switcher vs Resolver 体系反向 | CONFLICT 中立标 | F097 → **CONFLICT**（I043 — Fix Phase = Post-Audit 人类决策，3 选项）|
| 11 | `WechatOfficialAccountSender` 渠道别名 "WECHAT_OA" vs 代码 "WECHAT_OFFICIAL_ACCOUNT" 反向 | CONFLICT 中立标 | F092 → **CONFLICT**（I039 — Fix Phase = Post-Audit 人类决策，3 选项）|
| 12 | `IntlBookingService.updateStatus` catch 吞异常（I008 模式 — 与 SoT §0.3 一致设计）| I008 模式 | F096 → **PARTIAL-NON-BLOCKER**（I047 — 决策：① 接受现状 ② 升级结构化错误码）|
| 13 | `IntlBookingService` 4 个 `@Transactional` 未指定 rollbackFor（I009 模式）| I009 模式 | F096/F099/F100/F102/F103/F106 → **PARTIAL-NON-BLOCKER**（I048 — 决策：① 接受现状 ② 加 rollbackFor=Exception.class）|
| 14 | `IntlBookingService` `@RestController` 命名误导（I044 第 4 次同模式）| 架构异味 | F113 → **BACKLOG**（沿用 I004/I019/I029 模式，建议 Post-Audit 统一整改）|
| 15 | `ChannelDispatcher.healthCheckAll` 仅更新 tenant_id=1（T07-D4 例外）| I050 例外声明 | F094 → **PARTIAL-NON-BLOCKER**（I050 — 决策：① 接受现状 ② 升级多租户批量更新）|
| 16 | Google/Instagram webhook + OAuth callback 演示模式（I051）| I051 演示模式 | F099/F100/F101/F102/F106 → **PARTIAL-NON-BLOCKER**（I051 — 沿用 P3-26 演示模式，本批次不改）|
| 17 | `pos_store` 表存在但 `store_name + contact_phone` 字段对齐待 Phase 5 验证 | DB 字段对齐 | F087 → **PARTIAL-NON-BLOCKER**（I046 — Phase 5 `DESCRIBE pos_store`）|
| 18 | 受保护页面 `/dashboard` / `/mh/visual` / `/mh/consent` | 用户实测通过 | Phase 2 静态扫描确认 EXT-19 与受保护页面后端接口无间接依赖；审计期间不触及 |
| 19 | Playwright MCP 未配置 | 工具降级 | Phase 5 E2E 降级为：① 生成 Playwright 脚本供用户手动执行；② 用户手动贴回 evidence；③ 静态抓 HTML + curl API |
| 20 | MySQL MCP 已配置 | 工具就绪 | Phase 5 验证 overseas_booking / member_message / pos_store 表结构 + 字段 |
| 21 | g1-g8 守门脚本未实现 | 工具缺失 | Phase 1-2 期间补（沿用前三批次决策）；不阻塞 Phase 2 |
| 22 | `BizCode` 枚举值（`BAD_REQUEST / NOT_FOUND / FORBIDDEN / CONFLICT` 等）已确认 | 已知约束 | 沿用 Batch 1.1 F030 模板 |

---

### 七、Batch 1.4 Phase 2 产出位置

| 文件 | 内容 | 状态 |
|---|---|---|
| `audit/traceability-matrix.md` Batch 1.4 段（本段）| 33 Feature 全栈追溯 + 统计表 + VERIFIED 修正表 + Phase 2/5 标记规则 + 受保护页面零风险段 + 跨 EXT-* 引用规则段 + 已知约束汇总段 + Phase 3 启动约束段 + 关联文档段 | ✅ 此次产出 |
| `audit/feature-inventory.md` §1.4 | F081-F113 Feature 清单 + Issue 决策 + SoT 漂移清单 | ✅ Phase 1 已完成 |
| `audit/feature-inventory.md` §1.4.4 + §1.4.5 | SoT vs 代码冲突点比对（17 项 + 6 locale JSON 完整性核对）| ✅ Phase 1 已完成 |
| `audit/baseline/scope-freeze-batch-1.4.md` | Phase 0 scope freeze + 8 项用户决策 | ✅ Phase 0 已完成 |
| `audit/baseline/preflight-batch-1.4.md` | Phase -1 preflight，458 行 / 8 项内容 | ✅ Phase -1 已完成 |
| `audit/issues/issue-register.md` §二十（待写）| I038-I051 登记（本批次）| ⏸ Phase 3 启动后追加 |

---

### 八、Phase 3 启动约束

| 维度 | 约束 |
|---|---|
| **继承 Batch 1.1/1.2/1.3 决策** | D1-D6 全部维持 + Q1-Q4 全部维持 + I017-I029 沿用既有模式 + **本批次用户决策（2026-09-14 16:11）**：I038/I045 不跑迁移 / I039/I043 中立标 / I040-I042 Post-Audit / I044 backlog / I049 凭据缺失 UNVERIFIED |
| **Phase 2 标记规则** | 缺 DB 表（I038/I045）→ MISSING；SoT 漂移（I039/I043）→ CONFLICT（中立标）；架构异味（I044）→ BACKLOG（**第 4 次出现，建议 Post-Audit 统一整改**）；凭据缺失（I049）→ UNVERIFIED；Frontend Shadow（I040/I041/I042）→ PARTIAL-NON-BLOCKER；catch 吞异常（I047，I008 模式）→ PARTIAL-NON-BLOCKER；rollbackFor 缺失（I048，I009 模式）→ PARTIAL-NON-BLOCKER；演示模式（I051）→ PARTIAL-NON-BLOCKER；T07-D4 例外（I050）→ PARTIAL-NON-BLOCKER；其他（i18n/测试/Entity/DTO/接口层）→ UNVERIFIED |
| **受保护页面零触达** | `/dashboard` / `/mh/visual` / `/mh/consent` 后端接口与 EXT-19 **完全无依赖**（scope-freeze §九.10 已确认）|
| **跨 EXT-* 引用规则** | `overseas_booking` / `mkt_channel_config` / `member_message` / `pos_store` / `crm_member` / `pos_order` / `marketing_touch_log` 按 EXT-19 视角命名（用户决策 #8）；不跨批次重复审计 |
| **Phase 3 任务** | Static Gap Audit → 8 类静态差距扫描（fake-success / mock / disabled / endpoint-coverage / contract-drift / soft-delete / transaction / shadow-ghost）+ Issue 增补 |
| **Phase 4-5 启动条件** | Phase 3 完成后；Phase 2 不阻塞 Phase 3 启动 |

---

### 九、关联文档

| 文件 | 路径 |
|---|---|
| Feature Inventory（Batch 1.4 段）| `audit/feature-inventory.md` §1.4（F081-F113 Feature 清单 + Issue 决策 + SoT 漂移清单 + 受保护页面零风险段）|
| Scope Freeze | `audit/baseline/scope-freeze-batch-1.4.md`（Phase 0 产出 + 8 项用户决策）|
| Preflight | `audit/baseline/preflight-batch-1.4.md`（Phase -1 产出，458 行 / 8 节）|
| Issue Register（本批次）| `audit/issues/issue-register.md` §二十（I038-I051，待 Phase 3 追加）|
| **Batch 1.4 Traceability Matrix（本文件）**| `audit/traceability-matrix.md` Batch 1.4 段（F081-F113 全栈追溯 + 33 Feature 详情 + 统计 + 修正 + Phase 2/5 规则）|
| Batch 1.1 Traceability Matrix（30 Features）| `audit/traceability-matrix.md` Batch 1.1 段 |
| Batch 1.2 Traceability Matrix（25 Features）| `audit/traceability-matrix.md` Batch 1.2 段 |
| Batch 1.3 Traceability Matrix（25 Features）| `audit/traceability-matrix.md` Batch 1.3 段 |
| Batch 1.1 Issue Register | `audit/issues/issue-register.md` §十五（30 Feature 最终 Result + I001-I013 + I016）|
| Batch 1.2 Issue Register | `audit/issues/issue-register.md` §十七 + §十八（25 Feature 最终 Result + I017-I025 + 3 项核实决策）|

---

**Batch 1.4 Phase 2：Full-Stack Traceability Matrix 完成。33 Features 全栈追溯完成 + 统计表 + Issue 决策汇总（I038-I051）+ 已知约束汇总 + 跨 EXT-* 引用规则 + 继承决策 + Phase 2 最终验收汇总全部钀芥。审计阶段只记录不修复（规则 6）。不进入 Phase 3。等待用户确认 → 进入 Phase 4/5（Runtime Verification）。**
