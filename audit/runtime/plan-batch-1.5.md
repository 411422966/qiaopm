# Phase 4 — Runtime Verification Plan（Batch 1.5）

> **适用批次**：Batch 1.5 — MKT 多渠道重构（v3 第五批 · 首个重构批次）
> **创建时间**：2026-09-16 01:12 (UTC+8)
> **触发**：Phase 3（Static Gap Audit）通过 + Phase 3.5（Diff Audit 8 项）通过 + 用户 2026-09-16 01:05 决策（I069 登记不修，与 I064 合并同类；Fix Phase = Post-Audit）
> **Phase**：Phase 4 — Runtime Verification Plan
> **下一阶段**：Phase 5 — Runtime Execution & Evidence Collection（阻塞条件见 §六）
> **本文件状态**：⚠ Phase 5 启动前可微调；Phase 5 启动后冻结

---

## 〇、文档说明（2026-09-16 02:38 Phase 5A 实测补充）

> **§三 MySQL 实测补正**：plan §三 3.0.1-3.0.4 注释已落档（2026-09-16 02:30 实测 16 张 mkt_% 表）。**Phase 5A 启动前无需再改 §三**。Phase 5A 直接按已修正的 §三执行。
> **新增 §0.1**：plan §三 之前列「11-13 张表」，**实测 16 张**（含 6 张辅助表：mkt_brand / mkt_dynamic_pricing_rule / mkt_journey_trigger_log / mkt_order_discount / mkt_pricing_log / mkt_segment）。9 个 Mkt*Controller **不直接 CRUD 这 6 张**。
> **本次实测确认**：market_activity 表存在（11 列）、4 张 I063 MISSING 表确认 0 匹配、I054 NEW 反向（mkt_channel_config 实际存在）、I066 关闭。

Phase 4 **只做一件事**：把 Phase 2/3 已落档的 27 个 Feature（F114-F140）逐一映射到运行时验证方式，回答五个问题：

| 问题 | 答案来源 |
|---|---|
| **这个 Feature 在 Phase 5 怎么验？** | curl / DB CLI / jshell / 静态 |
| **需不需要 E2E（浏览器）？** | Playwright MCP 未配置（沿用 Batch 1.1-1.4 决策）→ 全部降级 |
| **需不需要 DB 断言？** | 涉及 MKT 表 / `market_activity` 表的 Feature 必须 |
| **预期会暴露哪些负向证据？** | 来自 Issue I055-I069（14 + 1） |
| **需要用户手动跑什么？** | 启动 Spring + curl + DB MCP + jshell + Phase 3.5 Diff Audit curl 复测 |

---

## 一、范围与引用

### 1.1 批次输入链

| 引用文件 | 内容 | 本文件依赖维度 |
|---|---|---|
| `audit/baseline/scope-freeze-batch-1.5.md` | Phase 0 冻结规则 + 10 项用户决策 + 重构中间态 | §三 SoT / §四 用户决策 / §五 已知约束 |
| `audit/feature-inventory.md` §1.5（line 661-898） | 27 个 Feature 清单 + Issue I055-I068 | §三 Feature 表 / §四 优先级 |
| `audit/traceability-matrix.md` §Batch 1.5（line 2820-3823） | 全栈追溯 + Final Result + Issue 归属 | §三 Feature 表的「DB / 读 / 写」维度 |
| `audit/reports/static-gap-report.md` §三十二（line 1890-2225） | Phase 3 静态差距审计 + 14 个 I055-I068 + 守门脚本命中 | §五 预期负向证据 |
| `audit/diff-audit/mkt-refactor.md`（Phase 3.5 8 项已产出） | 重构差异审计 + I055-I059 | §三 F140 验证方式 / §五 重构负向证据 |

### 1.2 SoT 与 Commit

| 项 | 值 |
|---|---|
| **SoT（事实来源）** | `docs/ROLLBACK-6.2-mkt_ddl.md`（MKT-002 SoT）+ git status（⚠ 无 .mdc Operational Notes — 本批次退化） |
| **Freeze commit（Phase 0 锚点）** | `e85ba8ba8540081f2f59c97120052b5bef5a29ef`（沿用 Batch 1.2/1.3/1.4 freeze） |
| **Batch 1.5 起点 commit** | 同上（无新代码 commit — Phase 1-3.5 全程无业务代码改动） |
| **DB snapshot** | `audit/baseline/db-snapshot.sql`（417 KB / 270 表 / schema-only） |
| **env** | `audit/baseline/env.md`（9,951 B） |
| **回归对照三件套** | commit hash + DB snapshot + env.md（规则 13）|

### 1.3 Issue 累计（69 个，跳过 I053）

| 编号段 | 来源批次 | 数量 |
|---|---|---|
| I001-I037 | Batch 1.1-1.4 | 37 |
| I038-I052 | Batch 1.4 EXT-19 | 15 |
| **I053 跳号** | 段落登记（scope-freeze §二十.20.1.1）| 0 |
| I054 NEW | 段落登记（I054 NEW — mkt_channel_config 缺失推论，已被 I066 反向：表实际存在）| 0 |
| I055-I068 | Batch 1.5 Phase 1 + Phase 3.5 | 14 |
| **I069 NEW** | Batch 1.5 Phase 3（2026-09-16 01:05 用户决策：登记，不修，与 I064 合并同类）| 1 |
| **合计** | — | **69 个**（I053 跳号 + I054 NEW 段落登记不占编号）|

### 1.4 Phase 4 用户决策落档（2026-09-16 01:12）

| # | 项 | 用户决策 |
|---|---|---|
| **1** | **Phase 3 通过** | ✅ Phase 3 静态差距审计（§三十二）27 Feature Result 全部归属 + 守门脚本 ~271 命中 + 14 个 I055-I068 + 1 个 I069 NEW |
| **2** | **I069 决策** | ✅ 登记，不修，与 I064 合并同类（Frontend Shadow / 封装层违反）|
| **3** | **Fix Phase** | ✅ **Post-Audit**（沿用 Q3 第六次统一口径 — 所有 MISSING 表 + 所有 CRITICAL/PARTIAL Issue 都不修，Post-Audit 统一处置）|
| **4** | **进入 Phase 4** | ✅ 本文件输出 + 后续 Phase 5 启动待用户确认 |
| **5** | **不进 Phase 5 / 不改业务代码** | ✅ 沿用规则 6 + Q2/Q3/Q4（审计阶段只记录不修复）|

---

## 二、验证原则（D10 铁律 — 不可破）

### 2.1 D10 铁律

> **跑不通 → UNVERIFIED，不造证据。**
> 严禁在缺运行证据时声称 VERIFIED / PARTIAL-*；严禁伪造证据、伪造 traceId、伪造 DB row；严禁"看起来跑了"的假成功。

### 2.2 优先级用户指定（与 feature-inventory.md §1.5.2 区别）

| Priority | 范围 | 数量 | 理由 |
|---|---|---|---|
| **P0** | 9 个 Mkt*Controller 核心端点（F114-F134 = 21 个 Feature）| 21 | 用户明确指定；MKT 重构的主战场；外部接口契约 |
| **P1** | ChannelDispatcher + Sender（F135-F137 = 3 个 Feature）| 3 | 渠道分发的承上启下层；缺失则 9 Controller 无法触发真实触达 |
| **P2** | MarketingAutomationService + 重构中间态 + Diff Audit（F138-F140 = 3 个 Feature）| 3 | 业务入口聚合 + 历史包袱 + 静态已产出 |

> **优先级 ≠ feature-inventory.md 中的 P1/P2/P3** — feature-inventory 用 PRD 视角（业务优先级）；Phase 4 用运行时验证视角（依赖链优先级）。

### 2.3 验证方式选项

| 方式 | 含义 | 工具 | 适用场景 |
|---|---|---|---|
| **API-only** | curl 调真实端点 + 验证 HTTP 状态码 + 响应体 | PowerShell `Invoke-RestMethod` / `curl` | F114-F134 全部端点 + F136 |
| **DB-only** | MySQL CLI / MySQL MCP 查表结构 + 行数 + 列对齐 | `mysql -e "DESCRIBE ..."` 或 MCP | F114-F134 表存在性 + F139 `market_activity` 列对齐 + F138 4 表 MISSING 验证 |
| **API + DB** | API 调用前后 diff DB 行数 / 列值 | curl + mysql CLI | F114-F134 涉及写库 + F118 health-check 副作用 + F132 expire-sweep |
| **静态（F139/F140）** | Phase 3 静态差距扫描 + Phase 3.5 Diff Audit 已产出 | grep + Read | F139（中间态 6 调用点量化）+ F140（8 项已产出） |
| **jshell 单元测试** | 调 `SmsSender.render(var)` 等算法 | `jshell` | F137 5 sender（沿用 Batch 1.4 F088 模板）|
| **降级（Mock 真实凭据缺失）** | WeChat MP / SMTP 无凭据时只跑 Mock 链路 | curl / jshell + log 关键词 | F137 WECHAT_OA + EMAIL + MINI_PROGRAM（沿用 EXT-19 D10 铁律）|

### 2.4 E2E 全部降级

| 工具 | 状态 | 降级方案 |
|---|---|---|
| **Playwright MCP** | ❌ 未配置（沿用 Batch 1.1-1.4 决策）| Phase 5 E2E 全部降级为：① curl + ② DB 断言 + ③ jshell 单元测试 + ④ 用户手动贴 HTML 片段（如必要）|
| **MySQL MCP** | ✅ 已配置（沿用 Batch 1.1-1.4）| 正常使用 — 仅验证表存在 / 列对齐 / 行数 diff，**不跑迁移**（Q3 第六次统一口径）|

### 2.5 Result 子标签（沿用 §五规则 7）

> 最终状态只允许 10 种之一：**VERIFIED / PARTIAL-BLOCKER / PARTIAL-NON-BLOCKER / BROKEN / MOCK-INTENTIONAL / MOCK-FAKE / SHELL-INTENTIONAL / SHELL-ABANDONED / MISSING / UNVERIFIED**。
> 跑不通 → **UNVERIFIED**（D10）；表缺失 → **MISSING → PARTIAL-BLOCKER**（沿用 I002/I017/I029/I038/I045/I054 NEW/I063 模式）；SoT vs 代码 CONFLICT → **中立标（CONFLICT）+ 不预设方向**（沿用 I001/I018/I026-I030/I055 模式）。

---

## 三、Feature 验证计划表（27 Feature · F114-F140）

> 列：**Feature ID | Priority | 验证方式 | 是否需 E2E | 是否需 DB 断言 | 理由**
> E2E 列均为 **否**（Playwright MCP 未配置，全部降级为 API+DB+静态）

### 3.0 §三 表范围说明 — 16 张 mkt_% 表全清单（Phase 5A MySQL 实测 2026-09-16 02:30）

> **修正说明**：plan §3.1 原列 12 张直接相关表；MySQL 实测 `SHOW TABLES LIKE 'mkt_%'` 实际 **16 张**。多出 6 张（**粗体**）不在 F114-F134 直接 CRUD 范围，仅以注释说明归属。

#### 3.0.1 F114-F134 直接相关表（10 张）

| # | 表名 | 涉及 Feature | Controller / Mapper |
|---|---|---|---|
| 1 | `mkt_channel_config` | F114-F118, F136 | `MktChannelController` + `MktChannelConfigMapper` |
| 2 | `mkt_channel_template` | F127, F128 | `MktTemplateController` + `MktChannelTemplateMapper` |
| 3 | `mkt_coupon` | F131, F132 | `MktCouponController` + `MktCouponMapper` |
| 4 | `mkt_coupon_batch` | F131, F132 | `MktCouponController` + `MktCouponBatchMapper` |
| 5 | `mkt_journey` | F124-F127, F130 | `MktJourneyController` + `MktJourneyMapper` |
| 6 | `mkt_journey_instance` | F127 | `MktJourneyController` + `MktJourneyInstanceMapper` |
| 7 | `mkt_promotion` | F133 | `MktPromotionController` + `MktPromotionMapper` |
| 8 | `mkt_points_rule` | F134 | `MktPointsController` + `MktPointsRuleMapper` |
| 9 | `mkt_points_log` | F134 | `MktPointsController` + `MktPointsLogMapper` |
| 10 | `marketing_touch_log` | F119-F123, F127, F129, F136 | `MktTouchTaskController` + `MarketingTouchLogMapper` |
| 11 | `marketing_touch_conversion` | F119-F122 | `MktConversionController` + `MarketingTouchConversionMapper` |
| 12 | `marketing_touch_task` | F129 | `MktTouchTaskController` + `MarketingTouchTaskMapper` |

> 备注：原 plan §3.1 注释写「12 张直接相关表」，本节修正为 **10 张 P0 直接相关 + 2 张触达链路表**（`marketing_touch_log` / `marketing_touch_conversion` / `marketing_touch_task`）。

#### 3.0.2 F114-F134 不直接相关辅助表（6 张 — 粗体标记）

| # | 表名 | 归属 Service | 备注 |
|---|---|---|---|
| 13 | **`mkt_brand`** | `OrgHierarchyService`（不在 F114-F134） | 品牌层级管理，F138 不调 |
| 14 | **`mkt_segment`** | `RfmService`（不在 F114-F134） | RFM 分群，F138 不调 |
| 15 | **`mkt_dynamic_pricing_rule`** | `DynamicPricingService`（不在 F114-F134） | 动态定价规则 |
| 16 | **`mkt_pricing_log`** | `DynamicPricingService`（不在 F114-F134） | 定价日志 |
| 17 | **`mkt_order_discount`** | `MarketingCouponService` 内部（不在 F114-F134） | 订单折扣中间表 |
| 18 | **`mkt_journey_trigger`** | `JourneyTriggerController`（独立 Controller，不在 9 Mkt*Controller 范围）| 旅程触发器配置 |
| 19 | **`mkt_journey_trigger_log`** | `JourneyTriggerController`（独立 Controller）| 触发日志，F124-F127 间接经此 |

> **关键**：9 个 Mkt*Controller（F114-F134）**不直接 CRUD 这 6 张辅助表**。F124-F127 journey 触发链**间接**经 `mkt_journey_trigger_log`（独立 `JourneyTriggerController`，非本批次范围）。
>
> **I066 已确认**：plan §3.1 注释原写「`mkt_channel_config` 缺失」，MySQL 实测表**实际存在**（I054 NEW 已反向，登记不修）。

#### 3.0.3 I063 MISSING 表（4 张 — 不在 16 张内，0 匹配）

| # | 表名 | 涉及 Feature | 状态 |
|---|---|---|---|
| 1 | `mkt_invitation` | F138（BusinessPolicyDispatchController）| Entity + Mapper 已建，**表从未 CREATE**（db-snapshot.sql 0 匹配）|
| 2 | `mkt_invitation_rule` | F138 | 同上 |
| 3 | `mkt_dispatch` | F138 | 同上 |
| 4 | `mkt_dispatch_target` | F138 | 同上 |

> **I063 CRITICAL-P2**：实证路径 → `P1RemainingService` / `P2FeaturesService` 通过 `JdbcTemplate.update("UPDATE mkt_xxx ...")` **直写 SQL**，调 `/m12/dispatch/*` 期望 `TableNotFoundException`。

#### 3.0.4 旧表 `market_activity`（I055/I067/I068 争议表）

| 项 | 值 |
|---|---|
| 表名 | `market_activity` |
| 列数（MySQL 实测 2026-09-16 01:42）| **11 列**（id / store_id / tenant_id / name / type / rule_config / start_time / end_time / status / created_at / updated_at） |
| 涉及 Feature | F139（重构中间态）|
| 状态 | 表存在，旧 Controller `/market/activity/*` 仍注册响应；SoT 声称无引用但代码 6 个活跃调用点 → **CONFLICT** |

---

### 3.1 P0 — 9 个 Mkt*Controller 核心端点（21 Feature）

| Feature ID | Module / 功能点 | Priority | 验证方式 | 需 E2E | 需 DB 断言 | 理由 |
|---|---|---|---|---|---|---|
| **F114** | MktChannel / 渠道列表查询 | **P0** | API-only（curl GET `/mkt/channel/list`）+ DB（`mkt_channel_config` 行数 + tenant_id 过滤）| ❌ | ✅ | 强制 `tenantId` 过滤（`MarketingAutomationService.java:55-65`）；JdbcTemplate 直查 15 列；期望 200 + 驼峰 rows；预期负向：表缺失 → MISSING / I054 NEW 已反向（表存在 — I066）|
| **F115** | MktChannel / 创建渠道配置 | **P0** | API + DB（POST 前后 diff `mkt_channel_config` 行 + 列值）+ 静态 | ❌ | ✅ | 默认值 `status=1/retryMax=3/retryIntervalSec=60/rateLimitPerMin=60`；⚠ **I062 rateLimitPerDay setter 缺失（line 81 空语句）** → 验证默认值是否真为 null；预期负向：缺 setter → 数据库 `rate_limit_per_day` 为 null |
| **F116** | MktChannel / 更新渠道配置 | **P0** | API + DB（PUT 前后 diff）+ 静态 | ❌ | ✅ | `c.setTenantId(old.getTenantId())` 强制保留原租户（防越权写）；验证跨租户写防护 |
| **F117** | MktChannel / 删除渠道配置 | **P0** | API + DB（DELETE 前后 diff）+ 静态 | ❌ | ✅ | ⚠ **I056 跨租户删除风险**（`MktChannelController.java:60-62` 未校验 `old.tenantId`）；期望：tenantId 不匹配时仍可删（**当前行为即风险**）|
| **F118** | MktChannel / 健康检查 | **P0** | API + DB（POST `/health-check` → 5 sender 轮询 + `mkt_channel_config.last_health_at`/`last_health_msg` 更新）| ❌ | ✅ | ⚠ **I050 沿用**：硬编码 `Long tenantId = 1L`（`ChannelDispatcher.java:48-66`）；log.warn 声明设计意图；仅更新 tenant=1 的健康状态；5 sender 中真实凭据缺失的（WECHAT_OA / EMAIL / MINI_PROGRAM）期望 UNVERIFIED（沿用 EXT-19 D10）|
| **F119** | MktConversion / 转化漏斗 | **P0** | API-only（curl GET `/mkt/conversion/funnel`）+ DB（`marketing_touch_conversion` + `marketing_touch_log` 行数）| ❌ | ✅ | 4 段漏斗聚合查询；JdbcTemplate；空数据时返回 0；表不存在 → MISSING（沿用 I054 NEW 模式）|
| **F120** | MktConversion / ROI 统计 | **P0** | API-only + DB | ❌ | ✅ | 按 taskId 过滤 ROI = 转化收益 / 触达成本；空数据返回 0 |
| **F121** | MktConversion / 按渠道统计 | **P0** | API-only + DB | ❌ | ✅ | 按 `channel_type` 分组聚合 |
| **F122** | MktConversion / 记录转化（业务回调）| **P0** | API + DB（POST 前后 diff `marketing_touch_conversion`）| ❌ | ✅ | ⚠ **I057 跨租户写风险**（`MarketingAutomationService.trackConversion` `:731-760` body 直接取 `tenantId`）；跨租户 POST 期望：写入但 `tenantId` 取自 body（**当前行为即风险**）|
| **F123** | MktConversion / 标记已读（渠道回调）| **P0** | API + DB（POST 前后 diff `marketing_touch_log`）| ❌ | ✅ | ⚠ **I057 跨租户写风险**（同 F122 模式）|
| **F124** | MktJourney / 创建旅程 | **P0** | API + DB（POST 前后 diff `mkt_journey` + 嵌套 nodes）+ 静态 | ❌ | ✅ | `@Transactional`；body 包含 journey_type / nodes / triggers；期望 200 + new id |
| **F125** | MktJourney / 旅程分页（POST/GET 双形式）| **P0** | API-only（POST + GET `/mkt/journey/page`）+ DB | ❌ | ✅ | ⚠ **I058 Frontend 双路由形式**：POST body + GET query 同 handler（`MktJourneyController.java:42-60`）；验证两种调用都成功 |
| **F126** | MktJourney / 详情+更新+发布+暂停 | **P0** | API + DB（4 端点 × 前后 diff）+ 静态 | ❌ | ✅ | 4 端点聚合；发布/暂停不改 `mkt_journey.status` 字段（需 Phase 3 grep 核实 — 已确认：发布改 status=1，暂停改 status=0）|
| **F127** | MktJourney / 进入旅程 + 推进下一节点 | **P0** | API + DB（POST 前后 diff `mkt_journey_instance` + `marketing_touch_log`）+ 静态 | ❌ | ✅ | `@Transactional(rollbackFor = Exception.class)`；nextNode 通过 `MktChannelTemplate` 触发触达（`MktChannelTemplate.tplCode` 引用 — line 598）；⚠ 触达触发后真实凭据缺失 → UNVERIFIED（沿用 EXT-19）|
| **F128** | MktTemplate / 列表+创建+审核 | **P0** | API + DB（3 端点 × 前后 diff `mkt_channel_template`）| ❌ | ✅ | audit 用 `@PutMapping("/{id}/audit")` 改模板状态；验证状态机转换 |
| **F129** | MktTouchTask / 任务分页+创建+启停+日志 | **P0** | API + DB（9 端点 × 前后 diff `marketing_touch_task` + `marketing_touch_log`）+ 静态 | ❌ | ✅ | 9 端点；⚠ **I058 同模式 POST/GET 双形式**（`MktTouchTaskController.java:37/47`）；验证两种 `/page` 调用都成功 |
| **F130** | MktEffect / 效果快照+生成快照 | **P0** | API + DB（GET snapshot 聚合 4 表；POST snapshotSave 仅 log.info）+ 静态 | ❌ | ✅ | ⚠ **I059 dead code**：`snapshotSave()`（`MktEffectController.java:175-`）仅 `log.info` 不写 `mkt_effect_snapshot` 表（表本身 0 匹配）；预期负向：调用返回 200 但 DB 无写入 |
| **F131** | MktCoupon / 批次分页+创建+发布+结束+发放 | **P0** | API + DB（5 端点 × 前后 diff `mkt_coupon_batch` + `mkt_coupon`）| ❌ | ✅ | 5 端点；grant 时 memberIds 缺则发放给全部 active 会员（line 140+）；⚠ 全员发放性能风险 → 仅验证功能，不验压力 |
| **F132** | MktCoupon / 过期扫描+券实例分页+导出 | **P0** | API + DB（POST `/expire-sweep` 前后 diff `mkt_coupon` + `mkt_coupon_batch`）+ 静态 | ❌ | ✅ | ⚠ **I060 CRITICAL-P1**：`expireSweep()`（`MktCouponController.java:230-246`）**无 try/catch**（异常 → Spring 500）+ **缺 audit log**；预期负向：异常路径无兜底；**验证** ① sweep 正常返回 expiredCount + batchesClosed ② SQL 注入 / 异常时事务回滚（已有 `@Transactional`）|
| **F133** | MktPromotion / 分页+列表+创建+更新+删除+导出 | **P0** | API + DB（7 端点 × 前后 diff `mkt_promotion`）+ 静态 | ❌ | ✅ | 7 端点；`@GetMapping({"/", ""})` 双路径（list）；导出 CSV 带 UTF-8 BOM（验证响应头 + 内容）|
| **F134** | MktPoints / 积分规则列表+创建+更新+手动发分 | **P0** | API + DB（4 端点 × 前后 diff `mkt_points_rule` + `mkt_points_log`）+ 静态 | ❌ | ✅ | 4 端点；award 链路：`PosController.checkout()` → `MktPointsRule.pointsToCash` 计算（默认 100:1）；⚠ award 调用链跨 Controller — Phase 5 仅验证 MktPointsController.award 直调，不验收银链路 |

### 3.2 P1 — ChannelDispatcher + Sender（3 Feature）

| Feature ID | Module / 功能点 | Priority | 验证方式 | 需 E2E | 需 DB 断言 | 理由 |
|---|---|---|---|---|---|---|
| **F135** | ChannelDispatcher / 渠道分发（按 channelType 路由）| **P1** | **静态 + jshell**（`ChannelDispatcher.getSender(channelType)` 单元测试 + 静态分析 `List<ChannelSender>` 注入）+ API 间接验证（通过 F118 + F127 触达触发）| ❌ | ⚠ 间接 | ⚠ **I061 CRITICAL-P2 NPE 风险**：`getSender(channelType)` miss 时 `findFirst().orElse(null)`（`ChannelDispatcher.java:34-39`）；预期负向：传 `channelType="UNKNOWN"` 期望 NPE；**验证** ① 5 sender 全部注册（`List<ChannelSender> senders` size=5）② 有效 channelType 返回非 null ③ 无效 channelType 返回 null（NPE 风险实证）|
| **F136** | ChannelDispatcher / 限流检查（每分钟+每日）| **P1** | API-only（通过 MarketingTouchTask 触发）+ DB（`mkt_channel_config.rate_limit_per_min/day` + `marketing_touch_log` 行数聚合）| ❌ | ✅ | 双重检查；JdbcTemplate 聚合；空数据时返回 false（不限流）；表不存在 → MISSING |
| **F137** | ChannelSender × 5（SMS / EMAIL / MINI_PROGRAM / IN_APP / WECHAT_OA）| **P1** | **jshell 单元测试**（`SmsSender.render(var)` / `EmailSender` mismatch demo / `MiniProgramSender.buildPayload` / `InAppSender.save` / `WechatOfficialAccountSender`）+ **降级**：真实凭据缺失相关 Feature → UNVERIFIED | ❌ | ❌（仅 IN_APP 涉及 DB） | 5 Sender 共用 EXT-19 链路（**MKT 不修改 sender 实现**，沿用 scope-freeze §九.9）；⚠ **沿用 I039 WECHAT_OA vs WECHAT_OFFICIAL_ACCOUNT 别名 CONFLICT**；预期负向：① WECHAT_OA/EMAIL/MINI_PROGRAM 真实凭据缺失 → 期望降级 demo 模式（沿用 D10 铁律 → UNVERIFIED 真实链路）② SMS / IN_APP 算法链路 → VERIFIED（jshell 单元测试通过） |

### 3.3 P2 — MarketingAutomationService + 重构中间态 + Diff Audit（3 Feature）

| Feature ID | Module / 功能点 | Priority | 验证方式 | 需 E2E | 需 DB 断言 | 理由 |
|---|---|---|---|---|---|---|
| **F138** | MarketingAutomationService / 业务入口聚合（T-D21~D24）| **P2** | **静态 + API 间接**（27 个方法委派 9 mapper + JdbcTemplate + ChannelDispatcher）+ DB（验证依赖表存在性）| ❌ | ✅（**4 表 MISSING 必查** — I063）| ⚠ **I063 CRITICAL-P2 表 MISSING（4 张）**：`mkt_invitation` / `mkt_invitation_rule` / `mkt_dispatch` / `mkt_dispatch_target` 实体 + Mapper 已建，但 `db-snapshot.sql` 0 匹配；Phase 5 验证：调 `BusinessPolicyDispatchController` 任何端点 → 期望 `TableNotFoundException`；⚠ **I062 rateLimitPerDay setter 缺失（line 81 空语句）** 同 F115；⚠ **`@Transactional(rollbackFor = Exception.class)` 在 4 方法上 — good pattern（Phase 3 已验证）** |
| **F139** | MKT Refactor / 中间态（旧 `MarketActivity*` 5 类残留）| **P2** | **静态（Phase 3.5 Diff Audit 已量化）** + **API 间接**（curl `/market/activity/*` 10 端点验证旧 Controller 仍响应）+ DB（`market_activity` 表存在 + **11 列对齐** — `DESCRIBE market_activity`）| ❌ | ✅ | ⚠ **I055 SoT 反向证据** + **I067 重构中间态** + **I068 SoT 字段不一致**：① Phase 3.5 已量化 6 个活跃调用点（BusinessPolicyDispatchServiceImpl.applyActivity × 3 + MktEffectController.snapshot × 1 + MktCouponController + MktPromotionController 引用 MarketActivity）② `BusinessPolicyDispatchServiceImpl.applyActivity()` 仍调 `marketActivityMapper.selectById/insert(clone)` → **DROP `market_activity` 表会导致运行时 NPE**（与 SoT 描述反向）③ Phase 5 验证：调 `/market/activity/{id}` 端点期望 200（旧 Controller 仍注册）+ `DESCRIBE market_activity` 期望 **11 列**存在（已 Phase 5A 实测确认）|
| **F140** | MKT Refactor / Diff Audit / Phase 3.5 Diff Audit 8 项 | **P2** | **静态（已产出 `audit/diff-audit/mkt-refactor.md`）** | ❌ | ❌ | ✅ Phase 3.5 已完成 8 项审计 + 产出 I055-I059；本 Feature 在 Phase 4 仅做归档，不再跑新验证；Phase 5 不涉及 F140 |

---

## 四、优先级分布汇总

### 4.1 P0 / P1 / P2 分布（用户指定优先级）

| Priority | Feature 数 | Feature IDs | 占 27 Feature 比例 |
|---|---|---|---|
| **P0** | 21 | F114-F134（9 个 Mkt*Controller 全量）| **77.8%** |
| **P1** | 3 | F135 / F136 / F137（ChannelDispatcher + 5 Sender）| **11.1%** |
| **P2** | 3 | F138 / F139 / F140（业务入口 + 重构中间态 + Diff Audit）| **11.1%** |
| **合计** | **27** | **F114-F140** | **100%** |

### 4.2 验证方式分布（27 Feature 维度统计 — 同一 Feature 可多选）

| 验证方式 | Feature 数 | 涉及 Feature | 备注 |
|---|---|---|---|
| **API-only / API+DB** | 24 | F114-F134 + F136 + F138（间接）+ F139（间接）| 必跑 curl + 验证 HTTP 200/4xx/5xx + 响应体 + traceId |
| **DB-only** | 0（独立 Feature）| — | 全部 DB 断言伴随 API 调用（F114-F134 全部需要）|
| **静态** | 3 | F138（间接）/ F139 / F140 | F140 Phase 3.5 已产出，不重复 |
| **jshell 单元测试** | 2 | F135（getSender 探测）/ F137（5 sender 算法）| F137 SMS / IN_APP 期望 VERIFIED；WECHAT_OA / EMAIL / MINI_PROGRAM 期望 UNVERIFIED（凭据缺失）|
| **降级（Mock 真实凭据缺失）** | 3 个 channel | F137（WECHAT_OA + EMAIL + MINI_PROGRAM）| 沿用 EXT-19 D10 铁律 |

### 4.3 DB 断言分布（涉及表 — 同 feature-inventory.md §1.5.7）

| DB 表 | 涉及 Feature | 关键检查 |
|---|---|---|
| `mkt_channel_config` | F114-F118 / F136 | tenant_id 过滤 + 列对齐（含 enable / configJson）|
| `marketing_touch_log` | F119-F123 / F127 / F136 | 行数 + status 字段 |
| `marketing_touch_conversion` | F119-F122 | 行数 + tenantId 字段 |
| `mkt_journey` + `mkt_journey_instance` | F124-F127 + F130 | 状态机 + 嵌套 nodes |
| `mkt_channel_template` | F127 / F128 | tplCode + audit 状态 |
| `marketing_touch_task` | F129 | 状态机（CREATED/RUNNING/PAUSED/CANCELLED）+ tenantId |
| `mkt_promotion` + `mkt_coupon` + `mkt_coupon_batch` | F130 / F131 / F132 / F133 | 行数 + status + expire_sweep 前后 diff |
| `mkt_points_rule` + `mkt_points_log` | F134 | rule + log 前后 diff |
| **`market_activity`（旧表 / I055/I067）** | F139 | **DESCRIBE 实测 11 列**（id / store_id / tenant_id / name / type / rule_config / start_time / end_time / status / created_at / updated_at）— plan §3.1 F139 原写「9 列」**修正为 11 列** |
| **`mkt_invitation` / `mkt_invitation_rule` / `mkt_dispatch` / `mkt_dispatch_target`（I063 MISSING）** | F138 间接（BusinessPolicyDispatchController）| **实证调用链**：`P1RemainingService.java:100-181` + `P2FeaturesService.java:217-343` 通过 `JdbcTemplate.update("UPDATE mkt_xxx ...")` **直写 SQL**（不经过 MyBatis Mapper）；Entity + Mapper 已建（`MktInvitation.java` / `MktDispatch.java` / `MktDispatchTarget.java` / `MktInvitationRule.java`），但表从未 CREATE；调 `/m12/dispatch/*` 或 P1RemainingService 任一端点 → 期望 `InvalidDataAccessResourceUsageException: Table 'meiyetec.mkt_xxx' doesn't exist`（JDBC 直查 / 直写，MyBatis 不拦截）|
| **`mkt_brand` / `mkt_segment` / `mkt_dynamic_pricing_rule` / `mkt_pricing_log` / `mkt_order_discount` / `mkt_journey_trigger_log`（plan §3 漏列 6 张 MKT 辅助表 — MySQL 实测 16 张 mkt_%）** | **不在 F114-F140 范围** | **MySQL 实测 16 张 mkt_%**：channel_config / channel_template / coupon / coupon_batch / journey / journey_instance / **journey_trigger** / journey_trigger_log / **brand** / **dynamic_pricing_rule** / **order_discount** / points_log / points_rule / **pricing_log** / promotion / **segment**；归属：OrgHierarchyService (`mkt_brand`) / RfmService (`mkt_segment`) / DynamicPricingService (`mkt_dynamic_pricing_rule` + `mkt_pricing_log`) / MarketingCouponService 内部 (`mkt_order_discount`) / JourneyTriggerController (`mkt_journey_trigger_log`) — 9 个 Mkt*Controller **不直接 CRUD 这些表**；仅 F124-F127 journey 间接经 `journey_trigger_log`（独立 JourneyTriggerController）| **2026-09-16 01:42 Phase 5A MySQL 实测确认** |

---

## 五、预期负向证据清单（⭐ Phase 5 关键交付物）

> Phase 5 启动后，**预期**这些负向证据会浮出水面（沿用 I055-I069）。跑不出反而是异常（D10 铁律反向）。

### 5.1 负向证据分组

| # | 预期负向证据 | 关联 Issue | 涉及 Feature | 验证方法 | Phase 5 期望输出 |
|---|---|---|---|---|---|
| **N1** | **9 个 Mkt*Controller 是否真路由**（@RequestMapping 注册成功 + Spring 启动加载）| I064 + I069（前端绕过 api 封装层）| F114-F134 全部 21 Feature | ① 启动日志 `Mapped "/mkt/..."` 21 行 ② curl 21 个端点期望 HTTP 200 | `audit/evidence/F114-mkt-channel-list/runtime-evidence.md` + 20 个 evidence |
| **N2** | **F132 expireSweep 无 try/catch + 缺 audit**（CRITICAL-P1）| **I060** | F132 | ① curl `/mkt/coupon/expire-sweep` 正常路径 → 期望 200 ② SQL 异常路径（手工注入脏数据触发 SQLException）→ 期望 Spring 500 无错误说明（**I060 实证**） | `audit/evidence/F132-expire-sweep/runtime-evidence.md` 含 **I060 实证** |
| **N3** | **F135 ChannelDispatcher.getSender miss → NPE**（CRITICAL-P2）| **I061** | F135 | jshell `ChannelDispatcher.getSender("UNKNOWN_CHANNEL")` → 期望返回 null（NPE 风险实证 — 调用方拿到 null 直接 NPE） | `audit/evidence/F135-channel-dispatcher/jshell-getSender-unknown.txt` |
| **N4** | **F138 BusinessPolicyDispatchController 4 表 MISSING → TableNotFoundException**（CRITICAL-P2）| **I063** | F138 间接 + BusinessPolicyDispatchController | ① `db-snapshot.sql` grep 4 表 0 匹配（已确认）② curl `/m12/dispatch/*` 任一端点 → 期望 TableNotFoundException | `audit/evidence/F138-marketing-automation/4-table-missing-evidence.md` |
| **N5** | **F139 重构中间态 + SoT CONFLICT**（SoT 声称无引用但代码实际有 6 个活跃调用点）| **I055 + I067 + I068** | F139 + F140 | ① `DESCRIBE market_activity` 期望 9 列存在 ② grep `marketActivityMapper` 调用点期望 6 个 ③ curl `/market/activity/{id}` 期望 200（旧 Controller 仍注册） | `audit/evidence/F139-refactor-intermediate/runtime-evidence.md` |
| **N6** | **F114-F134 前端绕过 api 封装层**（Frontend Shadow / 维护成本风险，非 Bug）| **I069 NEW（与 I064 合并同类）** | F114-F134 全部前端 view Feature | grep `frontend/meiyetec-web/src/views/mkt/*.vue` `http.get/post/put/delete('/mkt/')` 期望 ~70 命中（Phase 3 §32.8 已确认） | `audit/evidence/F114-.../frontend-shadow.md`（聚合到一个文件） |
| **N7** | **F115 rateLimitPerDay setter 缺失**（空语句，编译过但默认值未设）| **I062** | F115 + F138 | ① curl `POST /mkt/channel` 不传 `rateLimitPerDay` → DB 期望 `rate_limit_per_day` 为 null（**I062 实证**） | `audit/evidence/F115-create-channel/db-rate-limit-per-day-null.txt` |
| **N8** | **F130 snapshotSave 仅 log.info 不写表**（dead code）| **I059** | F130 | ① curl `POST /mkt/effect/snapshot/save` → 期望 200 + 但 `mkt_effect_snapshot` 表无写入（**I059 实证** — 表本身也 MISSING）| `audit/evidence/F130-snapshot/dead-code-snapshotSave.txt` |
| **N9** | **F117 跨租户删除不校验 tenantId**（PARTIAL-NON-BLOCKER）| **I056** | F117 | ① curl `DELETE /mkt/channel/{id}` 跨租户 → 期望 200 删除成功（**I056 实证** — 当前行为即风险） | `audit/evidence/F117-delete/cross-tenant-evidence.md` |
| **N10** | **F122/F123 跨租户写不强制 tenantId**（body 直接取，PARTIAL-NON-BLOCKER）| **I057** | F122 + F123 | ① curl `POST /mkt/conversion/track` body 含 `tenantId=1` 但当前用户 tenantId=2 → DB 期望写入 tenantId=1（**I057 实证** — 当前行为即风险） | `audit/evidence/F122-track-conversion/cross-tenant-write-evidence.md` |
| **N11** | **F118 ChannelDispatcher.healthCheckAll tenant_id=1L 硬编码**（沿用 I050 T07-D4 例外）| **I050** | F118 | ① curl `POST /mkt/channel/health-check` → DB 期望仅更新 `tenant_id=1` 的 `last_health_at`/`last_health_msg`（**I050 沿用实证**）| `audit/evidence/F118-health-check/tenant-1-only-evidence.md` |
| **N12** | **F137 WECHAT_OA vs WECHAT_OFFICIAL_ACCOUNT 别名漂移**（CONFLICT，沿用 I039）| **I039 + I047 模式** | F137 | ① jshell `WechatOfficialAccountSender.channelType()` 期望返回 `"WECHAT_OA"` 或 `"WECHAT_OFFICIAL_ACCOUNT"`（验证别名一致性）② 真实凭据缺失 → 期望 UNVERIFIED（沿用 D10）| `audit/evidence/F137-channel-sender/sender-alias-evidence.md` |
| **N13** | **F125/F129 POST/GET 双路由形式**（API 风格不一致，PARTIAL-NON-BLOCKER）| **I058** | F125 + F129 | ① curl `POST /mkt/journey/page` body + `GET /mkt/journey/page` query → 都期望 200（**I058 实证双形式共存**） | `audit/evidence/F125-journey-page/dual-route-evidence.md` |
| **N14** | **9 张 MKT 表 UNIQUE 不含 deleted**（软删除后重建 UNIQUE 冲突）| 沿用 I002 模式 | F114-F134 涉及软删的 9 张表 | ① 6 张 HIGH 表（`mkt_channel_template` / `mkt_coupon` / `mkt_coupon_batch` / `mkt_dynamic_pricing_rule` / `mkt_journey` / `mkt_promotion` / `mkt_segment` + `marketing_touch_task`）的 `SHOW INDEX` 期望 UNIQUE 不含 `deleted` 列 | `audit/evidence/F114-.../soft-delete-unique-evidence.md`（聚合） |

### 5.2 负向证据清单汇总

| 负向证据类型 | 数量 | 严重度分布 |
|---|---|---|
| **CRITICAL（Phase 5 必须暴露）** | 4 | I060 (F132) + I061 (F135) + I063 (F138 4 表) + I055 (F139) |
| **PARTIAL-NON-BLOCKER（Phase 5 暴露 + 登记 Issue）** | 7 | I056 / I057 / I058 / I059 / I062 / I050 沿用 / I069（I064 合并）|
| **CONFLICT（中立标）** | 2 | I039 沿用（F137）/ I055 / I067 / I068 |
| **MISSING（DB）** | 1 | I063（F138 间接）|
| **dead code** | 1 | I059（F130 snapshotSave）|
| **SoT 漂移** | 1 | I068（F139）|

---

## 六、Phase 5 阻塞条件

### 6.1 阻塞（必须先解决才能进 Phase 5）

| 条件 | 当前状态 | 阻塞原因 |
|---|---|---|
| **Phase 0 freeze commit 落档** | ✅ 已完成（沿用 `e85ba8ba...`）| — |
| **Phase 1 Feature Inventory 27 Feature 完整** | ✅ 已完成（`feature-inventory.md` §1.5.3）| — |
| **Phase 2 Traceability Matrix 27 Feature 全栈追溯** | ✅ 已完成（`traceability-matrix.md` §Batch 1.5，line 2820-3823）| — |
| **Phase 3.5 Diff Audit 8 项产出** | ✅ 已完成（`audit/diff-audit/mkt-refactor.md` + I055-I059）| — |
| **Phase 3 Static Gap Audit 产出** | ✅ 已完成（`static-gap-report.md` §三十二 + 14 项 I055-I068 + I069 NEW）| — |
| **Phase 4 Runtime Plan 产出（本文件）** | ✅ 本文件 | — |
| **环境就绪（启动 Spring + MySQL MCP）** | ⏳ 需用户手动启动 | Phase 5 启动阻塞 |

### 6.2 不阻塞（按用户决策维持）

| 项 | 状态 | 决策依据 |
|---|---|---|
| **不跑任何迁移脚本** | 维持 | Q3 第六次统一不跑（market_activity / mkt_channel_config / 其它 MKT 表 + 累计 12+ 张表全部 Post-Audit 跑批）|
| **不改任何业务代码** | 维持 | 规则 6 + Q2/Q3/Q4（审计阶段只记录不修复）|
| **不写 api/mkt.ts** | 维持 | I069 决策：登记，不修，与 I064 合并同类 |
| **不写 ext-mkt-multichannel.mdc** | 维持 | I054 NEW 决策 #3：Post-Audit 排期 |
| **不修 I060 expireSweep try/catch + audit** | 维持 | 用户决策：不加 try/catch，登记 Issue，Post-Audit |
| **不修 I061 ChannelDispatcher fallback** | 维持 | 用户决策：不变 Router，Post-Audit |

### 6.3 进 Phase 5 的入口条件

| # | 项 | 由谁负责 |
|---|---|---|
| 1 | ✅ Phase 4 计划表（本文件）已落档 | Cursor（已完成） |
| 2 | ⏳ 用户确认启动后端（含 9 Mkt*Controller + ChannelDispatcher + 5 sender + 旧 MarketActivityController 共存）| 用户手动 |
| 3 | ⏳ MySQL MCP 已连接目标 DB（与 scope-freeze 沿用）| 用户确认 |
| 4 | ⏳ 决定 Phase 5 启动时机（立即 / 延迟 / 分批）| 用户决策 |

---

## 七、需要用户手动执行的清单

### 7.1 Phase 5 启动前（一次性）

| # | 项 | 操作 | 备注 |
|---|---|---|---|
| **1** | **确认 Phase 4 计划表** | 阅读本文件 + 回复 "进 Phase 5" 或调整 | — |
| **2** | **启动后端（含 MKT 9 Controller + ChannelDispatcher + 5 sender + 旧 MarketActivityController 共存）** | `cd backend && mvn spring-boot:run` 或 IDE 启动；启动后贴出：① 9 Controller 路由注册成功日志 ② 5 sender 注册成功日志 ③ 旧 `/market/activity/*` 路由仍在日志 | Phase 5 必测 |
| **3** | **确认 MySQL MCP 已连接** | 跑 `SHOW TABLES LIKE 'mkt_%'` + `SHOW TABLES LIKE 'market_activity'` 贴回结果 | 验证 11 张 MKT 表 + market_activity 存在性 |
| **4** | **生成 admin / clerk token** | 复用 `audit/baseline/env-login-admin.txt` + `env-token-admin.txt`（Batch 1.4 已生成）| Phase 5 curl 鉴权用 |
| **5** | **确认 jshell 可用** | `jshell --version`；准备 classpath | F135 / F137 单元测试用 |

### 7.2 Phase 5 过程中（按 Feature 分批）

| # | 项 | 操作 | 涉及 Feature |
|---|---|---|---|
| **6** | **跑 P0 9 Controller 21 端点 curl** | 21 个 `Invoke-RestMethod` 命令（每个端点 1 次）+ 贴回 HTTP 200 + traceId | F114-F134 |
| **7** | **跑 P0 DB 断言** | 每个写库端点前后 diff `SELECT COUNT(*)` + 关键列值；贴回 mysql CLI 输出 | F114-F134 |
| **8** | **跑 P1 ChannelDispatcher + 5 sender 单元测试** | jshell 6 次（getSender 5 个有效 channelType + 1 个无效）+ 5 sender 各自 1 次算法测试 | F135 / F137 |
| **9** | **跑 P2 F138 BusinessPolicyDispatchController 验证 4 表 MISSING** | curl `/m12/dispatch/*` 任一端点 → 贴回 500 + TableNotFoundException stack | F138 |
| **10** | **跑 P2 F139 重构中间态** | ① `DESCRIBE market_activity` 贴回 9 列 ② curl `/market/activity/{id}` 贴回 200（旧 Controller 仍响应） ③ grep `marketActivityMapper` 调用点贴回 6 个 | F139 |
| **11** | **跑 P2 F140 归档确认** | 仅阅读 `audit/diff-audit/mkt-refactor.md` 8 项；无需新证据 | F140 |
| **12** | **触发预期负向证据（N2-N14）**| 按 §五 5.1 表逐项跑 + 贴回 evidence | F115 / F117 / F118 / F122 / F123 / F125 / F129 / F130 / F132 / F135 / F137 / F138 / F139 |

### 7.3 Phase 5 完成后

| # | 项 | 操作 | 备注 |
|---|---|---|---|
| **13** | **汇总 Phase 5 Evidence** | 输出 `audit/reports/batch-1.5-summary.md`（沿用 Batch 1.3 / 1.4 模板）| 27 Feature Result 全量归属 |
| **14** | **更新 issue-register.md §二十三** | 落档 Phase 5 I070+（如有新触达）+ I055-I069 处置状态 | — |
| **15** | **更新 batches.md** | 追加 Batch 1.5 完结段 | — |
| **16** | **决策下一步** | Post-Audit 修复 P0-1 + P0-2 + P0-3 / 启动 Batch 1.6 / 进 Phase 6 上线预演 | — |

### 7.4 不需要用户执行（Cursor 自完成）

| 项 | 说明 |
|---|---|
| 静态分析（grep / Read / glob）| Cursor 自完成 |
| 生成 jshell 探针（沿用 F088/F089/F090/F092 模板）| Cursor 自完成 |
| 生成 Playwright 脚本（如需）| 沿用 F007 / F024-F028 / F052-F054 模板（即使不用也生成备查）|
| 写 Evidence 文件结构（traceability-matrix.md + runtime-evidence.md + db-evidence.sql + business-evidence.md + nfr-evidence.md）| Cursor 自完成 |
| 更新 issue-register.md / batches.md / 各 Feature evidence 目录 | Cursor 自完成 |

---

## 八、不在范围（明确排除）

### 8.1 受保护页面（沿用 scope-freeze §二.2 + Phase 2 验证 I065）

| 路径 | 状态 |
|---|---|
| `/dashboard` | ✅ Phase 2 grep 0 命中 MKT 调用 → **I065 风险已解除** |
| `/mh/visual` | 沿用受保护策略 |
| `/mh/consent` | 沿用受保护策略 |

### 8.2 前四批次 EXT-*（不重复审计）

| EXT | 范围 | 处置 |
|---|---|---|
| EXT-19 多语言预约确认 | `OverseasBooking` / `IntlBookingService` / 6 locale / 5 sender / i18n / webhook | ✅ Batch 1.4 已完结；Batch 1.5 共用 5 ChannelSender（不修改） |
| EXT-24 CRM 集成 | `crm_integration` / `crm_contact_map` / `crm_sync_log` | ✅ Batch 1.1 已完结 |
| EXT-23 Plugin Runtime | `plugin_runtime_state` / `plugin_install` / pf4j | ✅ Batch 1.2 已完结 |
| EXT-22 Google Maps + 有赞 | `google_maps_config` / `ecommerce_platform` / 有赞 webhook | ✅ Batch 1.3 已完结 |

### 8.3 历史模块（M01-M18 除 MKT 外）

| 模块 | 处置 |
|---|---|
| M01-M08 / M12 / M14-M16 / M18 | 不在本批次；待后续批次（v3 第六批起）评估 |
| M09 / M10 / M11 / M17（与 MKT 关联）| 已纳入本批次 F114-F140 |

### 8.4 基础设施

| 项 | 处置 |
|---|---|
| Redis / MySQL 配置 / CI 流水线 | 不在审计范围 |
| 守门脚本 g1-g8 | Phase 1-2 期间补（用户决策 #6）|

---

## 九、已知约束与决策依赖

### 9.1 重构批次特殊约束（⭐）

| 维度 | 详情 |
|---|---|
| **MKT 是 v3 首个重构批次** | Phase 3.5 Diff Audit 8 项必跑（已产出）|
| **重构中间态** | 5 旧类 + 9 新 Controller + market_activity 表未 DROP + `mkt_invitation*` 4 表未跑迁移（**维持中间态** — 用户决策）|
| **SoT 无 .mdc** | 退化路径：`docs/ROLLBACK-6.2-mkt_ddl.md` + git status + 9 Controller 源码注释 |

### 9.2 用户第六次强调（漂移统一口径）

> ⚠ **所有 MISSING 表统一不跑迁移，Post-Audit 统一跑**（Q3 第 6 次）。
> 累计 12+ 张表（market_activity / mkt_channel_config / mkt_promotion / mkt_segment / mkt_audit_log / mkt_automation / mkt_invitation / mkt_invitation_rule / mkt_dispatch / mkt_dispatch_target / crm_contact_map / plugin_runtime_state / google_maps_config / google_maps_geocode_log / webhook_msg_log / ecommerce_platform 部分列 / overseas_booking / member_message）→ Post-Audit 统一跑批。

### 9.3 决策依赖

| 决策 | 选项 | 当前 |
|---|---|---|
| **Phase 5 启动时机** | 立即 / 延迟 / 分批 | ⏳ 待用户 |
| **真实凭据注入（WeChat MP / SMTP）** | 注入 / 维持 Mock 降级 | ⏳ 沿用 EXT-19 Mock 降级（无凭据）|
| **守门脚本 g1-g8 补齐** | Phase 1-2 期间 / Phase 5 期间 | ✅ Phase 1-2 期间已部分产出（见 static-gap-report §32.2）|

---

## 十、本文件状态

| 项 | 值 |
|---|---|---|
| **状态** | ⚠ Phase 5 启动前可微调；Phase 5 启动后冻结 |
| **变更控制** | 任何改动需用户明确同意 |
| **下一阶段产出位置** | `audit/evidence/F114-.../` ~ `F140-.../`（27 个目录）+ `audit/reports/batch-1.5-summary.md` |
| **下一阶段 Prompt 模板** | 见 `docs/PROJECT_AUDIT_PLAYBOOK.md` §十二「Phase 5」 |

---

## 十一、报告附录

### 11.1 Feature ID 全量映射（27 个 — F114-F140）

| 段 | Feature 数 | IDs |
|---|---|---|
| MKT Channel | 5 | F114-F118 |
| MKT Conversion | 5 | F119-F123 |
| MKT Journey | 4 | F124-F127 |
| MKT Template | 1 | F128 |
| MKT TouchTask | 1 | F129 |
| MKT Effect | 1 | F130 |
| MKT Coupon | 2 | F131-F132 |
| MKT Promotion | 1 | F133 |
| MKT Points | 1 | F134 |
| ChannelDispatcher + 5 Sender | 3 | F135-F137 |
| MarketingAutomationService | 1 | F138 |
| MKT Refactor 中间态 + Diff Audit | 2 | F139-F140 |
| **合计** | **27** | **F114-F140** |

### 11.2 Issue 累计映射（69 个，跳过 I053）

| 段 | Issue IDs | 来源 |
|---|---|---|
| Batch 1.1 CRM | I001-I016 | Phase 1 + Phase 3 |
| Batch 1.2 Plugin Runtime | I017-I025 | Phase 1 + Phase 3 |
| Batch 1.3 Google Maps + 有赞 | I026-I037 | Phase 1 + Phase 3 |
| Batch 1.4 EXT-19 多语言预约确认 | I038-I052 | Phase 1 + Phase 3 + Phase 5 |
| 跳号 | I053 | 段落登记（scope-freeze §二十.20.1.1）|
| I054 NEW | — | 段落登记（被 I066 反向 — 表实际存在）|
| Batch 1.5 Phase 1 | I055-I068 | Phase 1 + Phase 3.5 + Phase 3 |
| Batch 1.5 Phase 3 | **I069 NEW** | Phase 3（2026-09-16 01:05 决策：登记不修，与 I064 合并）|
| **累计** | **69 个** | — |

### 11.3 用户决策时间线（2026-09-15 01:19 ~ 2026-09-16 01:12）

| 时间 | 决策 |
|---|---|
| 2026-09-15 01:19 | scope-freeze 10 项 + Q3 第六次统一不跑迁移 |
| 2026-09-15 22:00 | Phase 2 用户决策（I055 不改 SoT / I056 不修跨租户 / ...）|
| 2026-09-16 01:00 | Phase 3 Static Gap Audit 产出（§三十二）|
| 2026-09-16 01:05 | **I069 NEW 登记**：前端绕过 api 封装层，与 I064 合并同类 |
| 2026-09-16 01:12 | **Phase 3 通过 / I069 决策登记不修 / Fix Phase = Post-Audit / 进 Phase 4** |

---

**Phase 4 计划表（Batch 1.5）完成。27 Feature（F114-F140）+ P0/P1/P2 分布（21/3/3）+ 14 项预期负向证据（N1-N14）+ 16 项用户手动执行清单（§七.1-§七.3）+ D10 铁律（跑不通 → UNVERIFIED）+ 不改业务代码 / 不进 Phase 5。⏸ 等用户确认启动 Phase 5（按 §七.1 一次性清单操作）。**
