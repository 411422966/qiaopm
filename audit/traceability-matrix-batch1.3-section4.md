

---

## Batch 1.3 — Phase 2 汇总表（统计 + 修正 + Phase 标记规则）

> **批次范围**：EXT-22 Google Maps Place Details + 有赞（25 Features，F056-F080）
> **Phase**：2（Full-Stack Traceability Matrix）
> **创建时间**：2026-09-13 22:55 (UTC+8)
> **SoT**：`.cursor/rules/ext-22-google-maps-and-ecommerce.mdc`
> **用户决策（2026-09-13 22:50）**：
> - I029 不跑迁移（沿用 I002/I017 模式）→ F056/F057/F060/F080 标 **MISSING**
> - I026/I027/I030 中立标 CONFLICT 接受 → F071/F077/F078 标 **CONFLICT**
> - `buildWebhookUrl` 孤儿工具 → F072 标 **BACKLOG-001**（I033）
> - `webhook_msg_log` 归 EXT-24 → **只引用不重复审计**
> **批次起点 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2 freeze commit）
> **DB Snapshot**：`audit/baseline/db-snapshot.sql`（**注意**：3 张 EXT-22 表/列 MISSING — I029）

---

### 一、Result 分布统计

| Result | 数量 | 占比 | Feature IDs | 关联 Issue |
|---|---|---|---|---|
| VERIFIED | 0 | 0.0% | — | — |
| **MISSING（DB）** | **4** | **16.0%** | F056, F057, F060, F080 | **I029**（沿用 I002/I017 模式，不跑迁移） |
| **PARTIAL-NON-BLOCKER** | **5** | **20.0%** | F062, F063, F065, F072, F079 | I031, I032, I028, I033（BACKLOG）, I028 |
| **CONFLICT（中立标）** | **3** | **12.0%** | F071, F077, F078 | I026, I027, I030 |
| **UNVERIFIED** | **13** | **52.0%** | F058, F059, F061, F064, F066, F067, F068, F069, F070, F073, F074, F075, F076 | （无前端入口 / Entity-Adapter 内部 / 无 Phase 5 证据） |
| BROKEN | 0 | 0.0% | — | — |
| MOCK-INTENTIONAL | 0 | 0.0% | — | — |
| MOCK-FAKE | 0 | 0.0% | — | — |
| SHELL-INTENTIONAL | 0 | 0.0% | — | — |
| SHELL-ABANDONED | 0 | 0.0% | — | — |
| **合计** | **25** | **100.0%** | F056-F080 | — |

### 1.1 复合 Result 说明

部分 Feature 涉及多重 Issue，最终 Result 取**主要风险**：

| Feature | 主要 Result | 叠加 Issue | 说明 |
|---|---|---|---|
| F062 | PARTIAL-NON-BLOCKER | I029（DB 联动）+ I031（未写日志）| F062 自身可调（`/geocode` 日志可降级），未写日志是审计缺失而非阻塞 |
| F063 | PARTIAL-NON-BLOCKER | I029（DB 联动）+ I032（无 audit log）| F063 admin-only 可调，audit log 缺失是审计风险而非阻塞 |
| F065 | PARTIAL-NON-BLOCKER | I028（部分反向）| `enablePlaceDetails` 开关已实现，但 `configForm` 缺字段（`defaultLanguage/defaultRegion/quotaDaily/enabled`），更新时会被覆盖 |
| F072 | PARTIAL-NON-BLOCKER | I033（BACKLOG-001）| 沿用 I013 dead code 模式，BACKLOG-001 是额外标签 |
| F077 | CONFLICT | I027（中立标）+ I029（DB 联动）+ I034（事务）| SoT §5 "未实现" 与代码反向（中立标），叠加事务与 DB 风险 |
| F078 | CONFLICT | I030（中立标）+ I035（重放 nonce）| SoT §5 "无重放保护" 与代码反向（中立标），叠加 nonce 校验缺失 |

### 1.2 与 Batch 1.1 / 1.2 Result 对比

| 批次 | VERIFIED | PARTIAL-BLOCKER | PARTIAL-NON-BLOCKER | CONFLICT | UNVERIFIED | MISSING | 合计 |
|---|---|---|---|---|---|---|---|
| Batch 1.1（EXT-24）| 0 | 11 | 14 | 4 | 1 | 0 | 30 |
| Batch 1.2（EXT-23）| 2 | 5 | 16 | 1 | 2 | 0 | 25 |
| **Batch 1.3（EXT-22）**| **0** | **0** | **5** | **3** | **13** | **4** | **25** |

**关键差异**：
- Batch 1.3 没有 PARTIAL-BLOCKER（I029 选择不跑迁移，避免类似 I002 的运行时 NPE 阻塞分类）
- Batch 1.3 UNVERIFIED 比例较高（52%），因大部分 Feature 是 Entity/Adapter 内部实现，无前端入口 / 无 Phase 5 证据
- Batch 1.3 MISSING 比例（16%）与 Batch 1.1 PARTIAL-BLOCKER（37%）相当，但粒度不同（DB-only）

---

### 二、VERIFIED 修正表（Phase 5 实测升级路径）

> **沿用 Batch 1.1 §十五.5 + Batch 1.2 §18.5 模板**：Phase 5 实测可能升级 Result。本表列出每个 Feature 的潜在升级路径，**Phase 5 用户决策后再修正**。

| Feature | Phase 2 Result | Phase 5 潜在升级路径 | 升级条件 | 关联 Issue |
|---|---|---|---|---|
| F056 | MISSING | VERIFIED | 用户决策跑 `migrate_ext_17_google_maps.sql` + `migrate_ext_22_google_maps_place_details.sql`；`DESCRIBE google_maps_config` 显示 11 字段含 `enable_place_details` | I029（跑迁移即解） |
| F057 | MISSING | VERIFIED | 同 F056 | I029 |
| F060 | MISSING | VERIFIED | 同 F056 + Phase 5 调 `/google-maps/config` + `/health` 验证 | I029 |
| F080 | MISSING | VERIFIED | 同 F056 | I029 |
| F062 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER（不变）或 VERIFIED | Phase 5 加 `google_maps_geocode_log` 写入（实现审计日志）→ 可能升 VERIFIED；否则维持 | I031 |
| F063 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER（不变）| Phase 5 加 `geocode_batch_log` 表（实现审计日志）→ 本批次不改 | I032 |
| F065 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER（不变）或 VERIFIED | Phase 5 调 `/google-maps/config` POST 验证 `enablePlaceDetails` 实际保存到后端；当前 `configForm` 缺字段，更新时被覆盖风险 | I028 |
| F072 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER（不变）或 DELETE | Phase 7 Post-Audit 决定删除 / 保留 / 改造为注册工具 | I033（BACKLOG-001） |
| F079 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER（不变）| 当前缺 UI 是事实；Phase 5 接受现状 | I028 |
| F071 | CONFLICT | CONFLICT（不变）或 VERIFIED | Phase 5 jshell 单元测试（3 组 payload：正常 / 篡改 body / 篡改 signature）→ 算法行为正确，但 SoT vs 代码方向冲突需 Post-Audit 人类决策 | I026 |
| F077 | CONFLICT | CONFLICT（不变）或 VERIFIED | Phase 5 Mock 推送样本 + jshell 单元测试 → 行为正确，但 SoT 漂移需 Post-Audit 人类决策 | I027 + I034 |
| F078 | CONFLICT | CONFLICT（不变）或 VERIFIED | Phase 5 重复 msg_id 推送测试 → 幂等行为正确，但 SoT 漂移需 Post-Audit 人类决策 | I030 + I035 |
| F058-F076 | UNVERIFIED | PARTIAL-NON-BLOCKER 或 VERIFIED | Phase 5 跑 mock 链路 + jshell 单元测试（沿用 Batch 1.2 Phase 5B 模板）| — |

**修正规则**：
- Phase 5 实测若发现新问题（如 I008 Fake Success 升级路径），按 Batch 1.1 §十五.5 / Batch 1.2 §18.5 模板处理
- CONFLICT 升级为 VERIFIED 的前提：SoT vs 代码方向冲突由 Post-Audit 人类决策后再修订 .mdc
- MISSING 升级为 VERIFIED 的前提：用户决策跑迁移（**当前决策是不跑**）

---

### 三、Phase 2/5 标记规则

#### 3.1 Phase 2 静态标记规则

| 类别 | 规则 | 沿用 Issue 模式 |
|---|---|---|
| **DB 表/列 MISSING**（DDL 写了但 dev DB 未跑迁移）| → **MISSING** | I002（Batch 1.1）+ I017（Batch 1.2）+ **I029**（Batch 1.3） |
| **SoT vs 代码 CONFLICT**（中立标，不预设方向）| → **CONFLICT** | I001（Batch 1.1）+ I018（Batch 1.2）+ **I026/I027/I030**（Batch 1.3） |
| **架构异味**（Controller 命名误导等）| → **PARTIAL-NON-BLOCKER** + 架构异味标注 | I004（Batch 1.1）+ I019（Batch 1.2）+ **I019 沿用**（Batch 1.3） |
| **dead code / 孤儿工具**| → **PARTIAL-NON-BLOCKER** + dead code 标注 / BACKLOG-NNN | I013（Batch 1.1）+ I021/I025（Batch 1.2）+ **I033**（Batch 1.3） |
| **事务 rollbackFor 缺失**| → **PARTIAL-NON-BLOCKER** | I009（Batch 1.1）+ **I034**（Batch 1.3） |
| **重放保护 / nonce 校验缺失**| → **PARTIAL-NON-BLOCKER** | **I035**（Batch 1.3 新型） |
| **审计缺失**（无 audit log）| → **PARTIAL-NON-BLOCKER** | **I031/I032**（Batch 1.3） |
| **Frontend Shadow**（缺 UI 入口）| → **PARTIAL-NON-BLOCKER** | I006/I007（Batch 1.1）+ I024（Batch 1.2）+ **I028**（Batch 1.3） |
| **Entity / Adapter 内部**（无前端入口 / 无 Phase 5 证据）| → **UNVERIFIED** | 沿用三个批次 |

#### 3.2 Phase 5 实测升级路径

| 触发条件 | 升级方向 | 决策点 |
|---|---|---|
| I029 跑迁移 + `DESCRIBE` 验证 | MISSING → VERIFIED | 用户决策（**当前：不跑**） |
| I026/I027/I030 中立标实测行为正确 | CONFLICT → VERIFIED（**待 Post-Audit 修 .mdc**） | Post-Audit 人类决策 |
| Phase 5 实证新发现（如 Fake Success / Fake Failure）| PARTIAL-NON-BLOCKER → CRITICAL-P1（沿用 I008/I011）| 用户决策 P1 升级 |
| Phase 5 实证环境依赖（如 GCP 凭据缺失、有赞 push_url 未注册）| UNVERIFIED 维持（Mock 降级 D10 铁律）| 用户决策 |
| Phase 5 实证安全 / NFR 问题 | PARTIAL-NON-BLOCKER → CRITICAL（升级 P0/P1）| 用户决策 |

#### 3.3 Phase 5 负向证据规则

> **沿用 Batch 1.1 §十五.5 + Batch 1.2 §18.3 模板**：Phase 5 实测预期暴露的负向证据，**作为审计的有效负向证据，不修后端绕过**。

| Feature | 预期负向证据 | 关联 Issue |
|---|---|---|
| F056/F057/F060/F080 | Phase 5 调任何相关 API → 后端抛 `TableNotFoundException: google_maps_config` / `google_maps_geocode_log` / `ecommerce_platform` | I029 |
| F062 | Phase 5 调 `/google-maps/place/details` → 同 F056 + `enable_place_details=0` → 期望 `PERMISSION_DENIED` | I029 + I031 |
| F063 | Phase 5 调 `/google-maps/geocode/batch` → 同 F057 + 无 audit log | I029 + I032 |
| F077/F078 | Phase 5 调 `POST /webhook/youzan` → 同 F056 (`ecommerce_platform` MISSING) | I027 + I029 |
| F071 | Phase 5 jshell 单元测试 3 组 payload：正常签名 → true / 篡改 body → false / 篡改 signature → false | I026（行为正确，但 SoT 漂移） |

---

### 四、Batch 1.3 受保护页面零风险段（沿用 Batch 1.1/1.2 模板）

> **来源**：`scope-freeze-batch-1.3.md` §九.8 + `preflight-batch-1.3.md` §五
> **规则**：`no-touch-verified-pages.mdc` — 用户已实测通过的 3 个页面（`/dashboard` / `/mh/visual` / `/mh/consent`）后端接口在 Batch 1.3 期间**直接不触达**

| 受保护路由 | 后端接口 | EXT-22 依赖 |
|---|---|---|
| `/dashboard` | `DashboardController`（不在 EXT-22 范围）| ❌ 无依赖 |
| `/mh/visual` | `VisualArchiveController`（不在 EXT-22 范围）| ❌ 无依赖 |
| `/mh/consent` | `ConsentController`（不在 EXT-22 范围）| ❌ 无依赖 |

**Phase 2 静态扫描确认**：EXT-22 端点（`/google-maps/*` + `/ecommerce/*` + `/webhook/youzan`）与 3 个受保护页面的后端接口**完全无间接依赖**（不在同 Service 包 / 不共享 Mapper / 不共享 DB 表）。审计期间**绝不触达**受保护页面。

---

### 五、Batch 1.3 跨 EXT-* 引用规则段（沿用 Batch 1.1/1.2 模板）

> **来源**：`scope-freeze-batch-1.3.md` §九.11 + 用户决策 #6

| 共享资源 | 所属 EXT-* | Batch 1.3 处理 | 备注 |
|---|---|---|---|
| `EcommercePlatform` entity + `ecommerce_platform` 表 | EXT-22（webhook 接收端反查）+ EXT-16（marketplace CRUD）| F066 实体（EXT-22 视角，含 `appId/kdtId/signSecret` 3 字段漂移）；F075 CRUD + F079 前端（EXT-22 视角）| Phase 1 期间明确边界 |
| `EcommerceOrder` entity + `ecommerce_order` 表 | EXT-22（webhook upsert）+ EXT-16（marketplace order sync）| F067 实体 + F076 sync + F077 webhook upsert（全部 EXT-22 视角）| 同上 |
| **`WebhookMsgLog` entity + `webhook_msg_log` 表** | **EXT-24**（CRM 集成）+ EXT-22（webhook 幂等去重引用）| **F078 引用**（**只引用不重复审计**） | **EXT-24 边界**（用户决策 #6） |

**Batch 1.3 视角**：F078 的 `webhook_msg_log` 操作（selectByMsgId + insert）**仅作为外部依赖引用**，不审计其表结构 / 业务规则 / 完整性约束。所有 `webhook_msg_log` 相关审计由 EXT-24 Batch 1.1 负责（已完结）。

---

### 六、Batch 1.3 已知约束汇总（沿用 scope-freeze §九）

| # | 约束 | 类别 | 处理 |
|---|---|---|---|
| 1 | GCP Places API 凭据缺失（用户决策 #1）| Mock 降级 | Phase 5 仅验证 mock 链路 + jshell 单元测试；真实链路 UNVERIFIED |
| 2 | 有赞 push_url 未在有赞后台注册（用户决策 #2）| Mock 推送 | Phase 5 生成 mock payload + signature 头部 → 调 `POST /webhook/youzan` 期望 200 |
| 3 | I029 不跑迁移（用户决策 2026-09-13 22:50）| DB MISSING | F056/F057/F060/F080 标 MISSING；Phase 2-5 记录负向证据（`TableNotFoundException`）|
| 4 | I026/I027/I030 中立标 CONFLICT 接受 | SoT 漂移 | F071/F077/F078 标 CONFLICT；Post-Audit 人类决策（3 选项：改文档 / 改代码 / 维持）|
| 5 | `buildWebhookUrl` BACKLOG-001（I033）| dead code | F072 标 PARTIAL-NON-BLOCKER + BACKLOG-001；Phase 7 Post-Audit 决定删除 / 保留 / 改造 |
| 6 | `webhook_msg_log` 归 EXT-24（用户决策 #6）| 跨 EXT-* 边界 | F078 只引用不重复审计 |
| 7 | 受保护页面 `/dashboard` / `/mh/visual` / `/mh/consent` | 用户实测通过 | Phase 2 静态扫描确认 EXT-22 无间接依赖；审计期间不触达 |
| 8 | Playwright MCP 未配置 | 工具降级 | Phase 5 E2E 降级为：① 生成 Playwright 脚本供用户手动执行；② 用户手动贴回 evidence |
| 9 | MySQL MCP 已配置 | 工具就绪 | Phase 5 用 MySQL MCP 验证表存在性 + 字段对齐 |
| 10 | `BizCode` 枚举值（`FORBIDDEN(403)` / `NOT_FOUND(404)` / `PERMISSION_DENIED` / `BAD_REQUEST` / `UNAUTHORIZED` / `SYSTEM_ERROR`）| 已知 | 沿用 Batch 1.1 F030 模板 |

---

### 七、Batch 1.3 Phase 2 产出位置

| 文件 | 内容 | 状态 |
|---|---|---|
| `audit/traceability-matrix.md` Batch 1.3 段 | 25 Feature 全栈追溯 + 统计表 + VERIFIED 修正表 + Phase 2/5 标记规则 + 受保护页面段 + 跨 EXT-* 引用段 + 已知约束段 | ✅ 本次输出 |
| `audit/feature-inventory.md` §1.3 | F056-F085 Feature 清单 + Issue 决策 + SoT 漂移清单 | ✅ Phase 1 已完成 |
| `audit/feature-inventory.md` §1.3.5 | SoT vs 代码冲突点比对（11 项 + 用户修正 #3）| ✅ Phase 1 已完成 |
| `audit/baseline/scope-freeze-batch-1.3.md` | Phase 0 scope freeze + 10 项用户决策 | ✅ Phase 0 已完成 |
| `audit/baseline/preflight-batch-1.3.md` | Phase -1 preflight（413 行 / 8 节）| ✅ Phase -1 已完成 |
| `audit/issues/issue-register.md` §十九（待写）| I026-I035 登记（本批次）| ⏸ Phase 3 启动后追加 |

---

### 八、Phase 3 启动约束

| 维度 | 约束 |
|---|---|
| **继承 Batch 1.1/1.2 决策** | D1-D6 全部维持 + Q1-Q4 全部维持 + I017-I025 沿用既有模式 + **本批次用户决策（2026-09-13 22:50）** |
| **Phase 2 标记规则** | 缺 DB 表（I029）→ MISSING；SoT 漂移（I026/I027/I030）→ CONFLICT；架构异味（I019）→ PARTIAL-NON-BLOCKER；其它（I028/I031/I032/I033/I034/I035）→ PARTIAL-NON-BLOCKER |
| **受保护页面约束** | `/dashboard` / `/mh/visual` / `/mh/consent` 后端接口与 EXT-22 **零交集**（scope-freeze §九.8 已确认）|
| **跨 EXT-* 引用规则** | `webhook_msg_log` 归 EXT-24（只引用）；`EcommercePlatform` / `EcommerceOrder` 按 EXT-22 视角 |
| **Phase 3 任务** | Static Gap Audit — 8 类静态差距扫描（fake-success / mock / disabled / endpoint-coverage / contract-drift / soft-delete / transaction / shadow-ghost）+ Issue 增补 |
| **Phase 4-5 启动条件** | Phase 3 完成后；Phase 2 不阻塞 Phase 3 启动 |

---

### 九、关联文档

| 文件 | 路径 |
|---|---|
| Feature Inventory（Batch 1.3 段）| `audit/feature-inventory.md` §1.3（F056-F085 Feature 清单 + Issue 决策 + SoT 漂移清单 + 受保护页面段）|
| Scope Freeze | `audit/baseline/scope-freeze-batch-1.3.md`（Phase 0 产出，10 项用户决策 + 用户修正 #3）|
| Preflight | `audit/baseline/preflight-batch-1.3.md`（Phase -1 产出，413 行 / 8 节）|
| Issue Register（本批次）| `audit/issues/issue-register.md` §十九（I026-I035，待 Phase 3 追加）|
| **Batch 1.3 Traceability Matrix（本文件）**| `audit/traceability-matrix.md` Batch 1.3 段（F056-F080 全栈追溯 + 25 Feature 详情 + 统计 + 修正 + Phase 2/5 规则）|
| Batch 1.1 Traceability Matrix（30 Features）| `audit/traceability-matrix.md` Batch 1.1 段 |
| Batch 1.2 Traceability Matrix（25 Features）| `audit/traceability-matrix.md` Batch 1.2 段 |
| Batch 1.1 Issue Register | `audit/issues/issue-register.md` §十五（30 Feature 最终 Result + I001-I013 + I016）|
| Batch 1.2 Issue Register | `audit/issues/issue-register.md` §十七 + §十八（25 Feature 最终 Result + I017-I025 + 3 项核实决策）|

---

**Batch 1.3 Phase 2（Full-Stack Traceability Matrix）完结。25 Features 全栈追溯完成 + 统计表 + VERIFIED 修正表 + Phase 2/5 标记规则 + 受保护页面段 + 跨 EXT-* 引用规则 + 已知约束汇总 + Phase 3 启动约束全部落档。⏸ 等用户确认 → 进入 Phase 3（Static Gap Audit）。**
