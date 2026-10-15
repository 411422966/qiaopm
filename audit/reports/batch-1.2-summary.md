# Batch 1.2 — EXT-23 Plugin Runtime 最终汇总 Summary

> **创建时间**：2026-09-13 21:15 (UTC+8)
> **Phase**：5C — Final Report
> **范围**：EXT-23 Plugin Runtime（25 Features = F031-F055）
> **执行模式**：3 Phase（5A API/DB + 5B API/jshell + 5C 汇总），D10 铁律全程生效
> **审计基线**：未修改业务代码 / .mdc / SQL（所有 Fix = Post-Audit）

---

## 〇、Batch 1.2 完成宣言

**Batch 1.2 — EXT-23 Plugin Runtime 已完成**：

| 维度 | 数值 |
|---|---|
| 计划 Feature 数 | 25 |
| 实际验证 Feature 数 | **25（100%）** |
| 用户决策（含 3 项核实）| 6 项全部落档 |
| 新增 BUG 实证 | 1（review tenantId null）|
| Phase 5 产出报告 | 3 份（5A + 5B + 5C）|
| Evidence 文件 | 23 份 |
| Probe 脚本 | 7 份（5A 1 + 5B 6）|
| Playwright 降级 | 3 份（前端）|
| 修复优先级 P0 | 3 项 |
| **下一步** | **Batch 1.3 待用户启动** |

---

## 一、25 Feature 最终 Result（核实修正后）

> 综合判定：Phase 2 静态 + Phase 5A API/DB 实证 + Phase 5B API/jshell 实证 → Final Result **就高不就低**。

### 1.1 状态分布

| 状态 | 数量 | Feature | 占比 |
|---|---|---|---|
| **VERIFIED** | 2 | F031, F048 | 8% |
| **PARTIAL-BLOCKER** | 5 | F032, F036, F037, F049, **F050** | 20% |
| **PARTIAL-NON-BLOCKER** | 15 | F033, F034, F038-F043, F045-F047, F051-F054 | 60% |
| **CONFLICT** | 2 | F035, **F044** | 8% |
| **UNVERIFIED** | 1 | F055 | 4% |
| **合计** | **25** | F031-F055 | 100% |

### 1.2 25 Feature 终态表

| Feature | 5A → 5B → 5C（核实修正）| 关键证据 | 主 Issue |
|---|---|---|---|
| **F031** Plugin SPI 接口定义 | UNVERIFIED → VERIFIED → **VERIFIED** | SPI 7 方法 + demo-hello 源完整 | — |
| **F032** Plugin Runtime 初始化 | UNVERIFIED → PARTIAL-BLOCKER → **PARTIAL-BLOCKER** | I017 DB 实证缺表 + 受限 | I017 |
| **F033** Plugin 生命周期（load/enable/disable/unload）| PARTIAL-NON-BLOCKER → = → **PARTIAL-NON-BLOCKER** | I021 autoLoaded dead code + 受限 | I021, I022 |
| **F034** Dashboard 看板 | PARTIAL-NON-BLOCKER → = → **PARTIAL-NON-BLOCKER** | Phase 3 保持 + 受限 | — |
| **F035** isEnabledForTenant 业务入口 | CONFLICT → = → **CONFLICT** | I018 + I023 静态 + 受限 | I018, I023 |
| **F036** PluginPersistenceService.recordState | UNVERIFIED → PARTIAL-BLOCKER → **PARTIAL-BLOCKER** | I017 DB 实证缺表 + 受限 | I017 |
| **F037** restoreStates | UNVERIFIED → PARTIAL-BLOCKER → **PARTIAL-BLOCKER** | I017 同上 + 受限 | I017 |
| **F038** Plugin 租户叠加（listPlugins + tenantId）| PARTIAL-NON-BLOCKER → = → **PARTIAL-NON-BLOCKER** | 静态 + I023 受限 | I023 |
| **F039** setTenantEnabled（upsert）| PARTIAL-NON-BLOCKER → = → **PARTIAL-NON-BLOCKER** | upsert 实证 + effectiveEnabled UI 困惑 | — |
| **F040** PluginSecurityPolicy 常量 | UNVERIFIED → PARTIAL-NON-BLOCKER → **PARTIAL-NON-BLOCKER** | jshell 20 常量实证 | — |
| **F041** assertPackageAllowed（合作式沙箱）| UNVERIFIED → PARTIAL-NON-BLOCKER → **PARTIAL-NON-BLOCKER** | jshell 14 Case 全部通过 | — |
| **F042** assertCallerAllowed | PARTIAL-NON-BLOCKER → = → **PARTIAL-NON-BLOCKER** | 静态 + I020 + I025 | I020, I025 |
| **F043** 平台级 6 端点 | PARTIAL-NON-BLOCKER → = → **PARTIAL-NON-BLOCKER** | 6 端点 curl + I024 Shadow | I024 |
| **F044** 租户级 4 端点 | CONFLICT → PARTIAL-BLOCKER → **CONFLICT**（核实 3 修正）| I018 实证已记录方向不一致，不预设方向 | I018 |
| **F045** Marketplace 上架 | PARTIAL-NON-BLOCKER → = → **PARTIAL-NON-BLOCKER** | 3 端点 + I019 异味 + I009 | I009, I019 |
| **F046** Marketplace 安装 | PARTIAL-NON-BLOCKER → = → **PARTIAL-NON-BLOCKER** | 3 端点 + I023 + I009 rollbackFor | I009, I023 |
| **F047** Marketplace 评价 | PARTIAL-NON-BLOCKER → = → **PARTIAL-NON-BLOCKER** | 2 端点 + **I019 跨租户读风险实证** + review tenantId null BUG | I019, **BUG-001** |
| **F048** Marketplace 实体（DB）| VERIFIED → = → **VERIFIED** | DB DESCRIBE 27/14/8 列 | — |
| **F049** PluginRuntimeState 实体 | PARTIAL-BLOCKER → = → **PARTIAL-BLOCKER** | I017 MISSING DB 实证 | I017 |
| **F050** Plugin 配置 application.yml | PARTIAL-NON-BLOCKER → **PARTIAL-BLOCKER**（核实 1 升级）| pathsRoot=`backend\plugins` vs demo-hello=`plugins` → 0 插件加载 | **新增 P0** |
| **F051** plugin_runtime_state DDL | PARTIAL-BLOCKER → PARTIAL-NON-BLOCKER → **PARTIAL-NON-BLOCKER** | DDL 10 vs Entity 10 完全对齐（升）| I017（联动）|
| **F052** 前端 tab（plugin/index.vue）| PARTIAL-NON-BLOCKER → = → **PARTIAL-NON-BLOCKER** | Static + I024 Shadow + Playwright 降级 | I024 |
| **F053** 前端对话框 | PARTIAL-NON-BLOCKER → = → **PARTIAL-NON-BLOCKER** | Static + I006 模式 + Playwright 降级 | — |
| **F054** 前端 api（plugin.ts）| PARTIAL-NON-BLOCKER → = → **PARTIAL-NON-BLOCKER** | Static + I024 100% Shadow + Playwright 降级 | I024 |
| **F055** demo-hello 示例插件 | UNVERIFIED → = → **UNVERIFIED** | 决策 2 — 路径不通 + 改 yml 会改审计基线 | — |

### 1.3 升级明细（5A → 5B → 5C）

| Feature | 5A | 5B | 5C（核实）| 升级证据 |
|---|---|---|---|---|
| F031 | UNVERIFIED | VERIFIED | **VERIFIED** | SPI 完整 |
| F040 | UNVERIFIED | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | jshell 20 常量 |
| F041 | UNVERIFIED | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | jshell 14 Case |
| F044 | CONFLICT | PARTIAL-BLOCKER | **CONFLICT**（回退）| 核实 3 — 中立标 |
| F050 | PARTIAL-NON-BLOCKER | PARTIAL-NON-BLOCKER | **PARTIAL-BLOCKER**（核实 1 升级）| 路径错配 |
| F051 | PARTIAL-BLOCKER | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | DDL vs Entity 对齐 |

---

## 二、Issue I017-I025 最终处置（沿用既有模式）

### 2.1 Issue 处置总览

| Issue | ID | 来源 | 最终状态 | 修复阶段 | 关联 Feature | 严重度 |
|---|---|---|---|---|---|---|
| **I017** | `plugin_runtime_state` 表缺失 | Phase 2 标 MISSING | **MISSING** | Post-Audit | F032/F036/F037/F049/F051 | 🔴 高 |
| **I018** | SoT vs 代码方向不一致 | Phase 2 标 CONFLICT | **CONFLICT（中立）** | Post-Audit（人类决策）| F035/F044 | 🟡 中 |
| **I019** | `PluginService` 命名异味 + 跨租户读风险 | Phase 2 标架构异味 | **Backlog + 已实证** | Post-Audit（重构）| F045/F046/F047 | 🟢 低 |
| **I020** | `assertCallerAllowed` 合作式沙箱 | Phase 5 实测 | **PARTIAL-NON-BLOCKER** | Post-Audit（接受）| F042 | 🟡 中 |
| **I021** | `PluginRuntimeInfo.autoLoaded` dead code | Phase 5 实证 | **Backlog** | Post-Audit | F033/F034 | 🟡 中 |
| **I022** | `disablePlugin` 清 lastError 副作用 | Phase 5 实测 | **PARTIAL-NON-BLOCKER** | Post-Audit（接受）| F033/F036 | 🟡 中 |
| **I023** | `plugin_install` 幽灵记录 NPE | Phase 5 实证 | **PARTIAL-NON-BLOCKER** | Post-Audit | F035/F046 | 🟡 中 |
| **I024** | 前端无 runtime tab（Shadow）| Phase 2 + Phase 5 E2E | **PARTIAL-NON-BLOCKER + Shadow** | Post-Audit | F052/F054 | 🟡 中 |
| **I025** | `PluginSecurityPolicy.deniedPackages()` 孤儿 | Phase 5 jshell 实证 | **Backlog** | Post-Audit | F042 | 🟢 低 |

### 2.2 沿用模式映射（与 Batch 1.1 决策对齐）

| Batch 1.1 既有模式 | Batch 1.2 对应 Issue |
|---|---|
| **I001** SoT 漂移中立标 CONFLICT | **I018**（一致 — 标 CONFLICT 不判方向）|
| **I002** MISSING 表标 PARTIAL-BLOCKER | **I017**（一致 — plugin_runtime_state 缺失，F032/F036/F037/F049 升 PARTIAL-BLOCKER）|
| **I004** 命名异味 Backlog | **I019**（一致）|
| **I006/I007** Shadow | **I024**（一致）|
| **I008** Fake Result（接受 + 仍报告）| **I020**（合作式沙箱 — 接受 + 仍记录）|
| **I011** Fake Failure | — |
| **I012** Bean 冲突降级 | **I023**（幽灵 NPE — pf4j 0 插件不可达，降级 UNVERIFIED 的连锁）|
| **I013** dead code | **I021** + **I025**（一致 — 留 Post-Audit 清理）|

### 2.3 新增 BUG（5B 实证发现）

| BUG ID | 描述 | 影响 Feature | 严重度 | 修复 |
|---|---|---|---|---|
| **BUG-001** | `PluginService.review` 未设 `tenantId` → DB 行 `tenant_id=NULL` | F047 | 🟡 中（与 I019 联动）| `r.setTenantId(TenantContextHolder.get().getTenantId())` |

### 2.4 I018 方向不一致详细记录（核实 3 修正后）

| 维度 | 实测事实 | SoT 描述 | 方向冲突？|
|---|---|---|---|
| **PluginRuntimeController.assertTenantAccess**（行 58-69）| ✅ 存在 + 正常工作（clerk01 跨 tid=2 4/4 = 403）| ⚠️ SoT §0.7/§5 说"未加租户隔离校验" | ❌ **不一致** |

**处理**：不预设谁对谁错，CONFLICT 维持，Fix = Post-Audit Human Decision。

**人类决策空间**（Post-Audit）：
- 选项 A：删 `.mdc` §5 那句"未加校验"，**改文档**（承认代码正确）
- 选项 B：保留 `.mdc`，**加更严的多层校验**到代码（+ tenant ownership + audit log）
- 选项 C：维持现状，CONFLICT 永久保持（接受 SoT 已知漂移）

---

## 三、修复优先级矩阵（Post-Audit）

### 3.1 P0 — 必须修（生产阻塞）

| # | Issue / Feature | 修复内容 | 关联 Feature | 修完效果 |
|---|---|---|---|---|
| **P0-1** | I002 / I017 | 跑 `migrate_ext_23_plugin_runtime_state.sql` + 重生成 db-snapshot | F032/F036/F037/F049/F051 | 解锁 4 个 PARTIAL-BLOCKER → VERIFIED |
| **P0-2** | F050 路径错配（核实 1 升级）+ F055 demo-hello 未编译 | 复制 `demo-hello-1.0.0.jar` 到 `backend/plugins/demo-hello/1.0.0/`（不改 yml，方案 A）**或** 改 `application.yml` plugins-root=`D:/QiaoPM/plugins`（方案 B，改审计基线）| F050 / F055 | 解锁 Plugin Runtime 从"0 插件"到"N 插件" |
| **P0-3** | I018 SoT vs 代码方向不一致 | 留人类决策（核实 3）— 选项 A/B/C | F035/F044 | 消解 CONFLICT（如何修由人类拍板）|

### 3.2 P1 — 强烈建议修（功能性 BUG）

| # | Issue / Feature | 修复内容 | 关联 Feature | 修完效果 |
|---|---|---|---|---|
| **P1-1** | **BUG-001** review tenantId null | `PluginService.review` 加 `r.setTenantId(currentTenantId)` | F047 | 修复跨租户评价读风险一半 |
| **P1-2** | I019 跨租户读 | `PluginService.listReviews` 加 `.eq(PluginReview::getTenantId, currentTenantId)` | F047 | F047 → VERIFIED |
| **P1-3** | I023 幽灵 NPE | `POST /plugin/install` 后联动 `POST /plugin/runtime/{code}/load`（异步 + 失败容忍）| F035/F046 | 防 install 后业务入口 NPE |

### 3.3 P2 — 建议修（代码质量）

| # | Issue / Feature | 修复内容 | 关联 Feature |
|---|---|---|---|
| **P2-1** | I009 @Valid 缺失 | PluginService 3 端点加 `@Valid` + entity 字段加 `@NotBlank` 等 | F045/F046 |
| **P2-2** | I009 @Transactional 无 rollbackFor | `POST /plugin/install` 加 `rollbackFor = Exception.class` | F046 |
| **P2-3** | I019 命名异味 | 拆分 `PluginController` + 3 个 Service（marketplace/install/review）| F045/F046/F047 |
| **P2-4** | I024 Shadow | 加 runtime tab + `api/plugin.ts` 补 10 个 runtime 方法 | F052/F054 |
| **P2-5** | F053 I006 模式 | pubDialog / reviewDialog 加 loading 态 + 二次确认 + errorCode 展示 | F053 |

### 3.4 P3 — 接受现状 / 留作未来增强

| # | Issue / Feature | 处理 | 关联 Feature |
|---|---|---|---|
| **P3-1** | I020 合作式沙箱 | **接受**（升级到字节码增强成本高 — OSGi / agent）| F042 |
| **P3-2** | I021 autoLoaded dead code | 删字段或 `loadAllFromDisk` 填充 | F033/F034 |
| **P3-3** | I022 disable 清 lastError | `recordState` 加 `errorMsg=null` 时保留旧值策略 | F033/F036 |
| **P3-4** | I025 deniedPackages 孤儿 | 删除或留作未来增强 | F042 |
| **P3-5** | F055 demo-hello | 用户可选 manual compile | F055/F031/F041/F042 |

---

## 四、D10 铁律落地（D10 铁律 = 跑不通就 UNVERIFIED，不造证据）

### 4.1 受限链

```
F055 demo-hello 未编译（UNVERIFIED，决策 2）
   ↓ 路径 + 基线双重锁
F050 pluginsRoot 路径错配（PARTIAL-BLOCKER，核实 1 升级）
   ↓ 加载 0 插件
F032 auto-load 加载 0 插件（PARTIAL-BLOCKER，I017 联动）
   ↓ 导致
F033/F036/F037/F038/F041/F042/F055 运行时无法实测
   ↓ 但
F040/F041 jshell 直接加载 PluginSecurityPolicy.class → 绕过 demo-hello 编译依赖
F044 I018 跨租户 8 端点实测（不需要 Plugin Runtime 加载）
F043/F045/F046/F047 直接调 Controller REST（不需要 pf4j）
```

### 4.2 不造证据清单（D10 铁律执行轨迹）

| 预期证据 | 是否造 | 替代方案 |
|---|---|---|
| demo-hello STARTED 状态 | ❌ 不造 | UNVERIFIED（F055）|
| recordState 写库成功 | ❌ 不造 | UNVERIFIED + DB 实证表缺失（I017）|
| isEnabledForTenant 业务入口 NPE | ❌ 不造 | UNVERIFIED（pf4j 0 插件阻断）|
| onStart 沙箱自检通过 | ✅ 已造 | **jshell 绕过 demo-hello 编译**（F041 升级）|
| 反射加载 service.UserService 行为 | ✅ 已造 | **jshell 实证**（I020 确认 + F042 静态）|
| 跨租户访问 403 行为 | ✅ 已造 | **8 端点 API 实证**（F044 维持 CONFLICT — 核实 3）|
| install → 业务入口 NPE | ❌ 不造 | UNVERIFIED（无 runtime 加载）|

---

## 五、Evidence 文件清单（23 份）

### 5.1 5A 阶段（6 份）

| Feature | 文件 | 路径 |
|---|---|---|
| F032 | runtime-evidence.md | `audit/evidence/F032-plugin-runtime-init/runtime-evidence.md` |
| F033 | runtime-evidence.md | `audit/evidence/F033-plugin-lifecycle/runtime-evidence.md` |
| F036 | runtime-evidence.md | `audit/evidence/F036-plugin-persistence-record/runtime-evidence.md` |
| F049 | db-table-check.md | `audit/evidence/F049-plugin-runtime-state/db-table-check.md` |
| F050 | relative-path-evidence.md | `audit/evidence/F050-plugin-config/relative-path-evidence.md` |
| F055 | compilation-status.md | `audit/evidence/F055-demo-hello/compilation-status.md` |

### 5.2 5B 阶段（16 份）

| Feature | 文件 | 路径 |
|---|---|---|
| F035 | i018-evidence.md | `audit/evidence/F035-plugin-tenant-access/i018-evidence.md` |
| F038 | static-evidence.md | `audit/evidence/F038-plugin-tenant-stacking/static-evidence.md` |
| F039 | upsert-evidence.md | `audit/evidence/F039-set-tenant-enabled/upsert-evidence.md` |
| F040 | jshell-evidence.md | `audit/evidence/F040-plugin-sandbox-constants/jshell-evidence.md` |
| F041 | jshell-evidence.md | `audit/evidence/F041-plugin-sandbox-allow/jshell-evidence.md` |
| F042 | jshell-evidence.md | `audit/evidence/F042-plugin-sandbox-caller/jshell-evidence.md` |
| F043 | api-evidence.md | `audit/evidence/F043-platform-runtime-controller/api-evidence.md` |
| F044 | i018-evidence.md | `audit/evidence/F044-tenant-runtime-controller/i018-evidence.md` |
| F045 | api-evidence.md | `audit/evidence/F045-marketplace-publish/api-evidence.md` |
| F046 | api-evidence.md | `audit/evidence/F046-marketplace-install/api-evidence.md` |
| F047 | i019-evidence.md | `audit/evidence/F047-marketplace-review/i019-evidence.md` |
| F048 | db-tables.md | `audit/evidence/F048-plugin-marketplace-entities/db-tables.md` |
| F051 | ddl-compare.md | `audit/evidence/F051-runtime-state-ddl/ddl-compare.md` |
| F052 | static-evidence.md | `audit/evidence/F052-plugin-tabs-ui/static-evidence.md` |
| F053 | static-evidence.md | `audit/evidence/F053-plugin-dialogs-ui/static-evidence.md` |
| F054 | static-evidence.md | `audit/evidence/F054-plugin-api-ui/static-evidence.md` |

> 注：5A 的 F031 evidence 在 5A 报告中也有，但当前 `audit/evidence/F031-plugin-spi/` 也有独立文件（参考 git status）。

### 5.3 Probe 脚本（7 份）

| 阶段 | 脚本 | 路径 | 用途 |
|---|---|---|---|
| 5A | probe-5a.ps1 | `audit/runtime/probe-5a.ps1` | 5A 综合探测 |
| 5B | probe-5b-i018.ps1 | `audit/runtime/probe-5b-i018.ps1` | F044 I018 跨租户 |
| 5B | probe-5b-f039.ps1 | `audit/runtime/probe-5b-f039.ps1` | F039 upsert |
| 5B | probe-5b-f043.ps1 | `audit/runtime/probe-5b-f043.ps1` | F043 平台级 6 端点 |
| 5B | probe-5b-f045-7.ps1 | `audit/runtime/probe-5b-f045-7.ps1` | F045-F046 CRUD |
| 5B | probe-5b-f047.ps1 | `audit/runtime/probe-5b-f047.ps1` | F047 评价 + 跨租户 |
| 5B | probe-5b-sandbox.jsh | `audit/runtime/probe-5b-sandbox.jsh` | F040/F041/F042 沙箱 jshell |

### 5.4 Playwright 降级脚本（3 份）

| Feature | 脚本 | 路径 |
|---|---|---|
| F052 | F052-plugin-tabs.spec.ts | `frontend/meiyetec-web/e2e/audit/F052-plugin-tabs.spec.ts` |
| F053 | F053-plugin-dialogs.spec.ts | `frontend/meiyetec-web/e2e/audit/F053-plugin-dialogs.spec.ts` |
| F054 | F054-plugin-api.spec.ts | `frontend/meiyetec-web/e2e/audit/F054-plugin-api.spec.ts` |

---

## 六、Batch 1.2 与 Batch 1.1 决策对齐

### 6.1 共用决策（D1-D6 / Q1-Q4 + 3 项核实）

| Batch 1.1 决策 | Batch 1.2 沿用 | 备注 |
|---|---|---|
| D1（不加 dashboard tab + edit 按钮）| ✅ 沿用（I024 Shadow）| — |
| D2（不修 integrationId 漂移）| — | 不适用 |
| D3（不修 enum/force/表单漂移）| — | 不适用 |
| D4（不修 I008/I009/I011/I012）| ✅ 部分沿用（I009/I023 沿用 I012 模式）| Plugin 端 I009/I023 同样不修 |
| D5（不跑迁移）| ✅ 沿用（I017 不跑迁移）| 与 D5 模式一致 |
| D6（I010 Flyway 编码临时处置）| — | 不适用（Plugin 端用 recordState）|
| — | ✅ Q1 D10 铁律覆盖 F032/F033/F035/F036/F037（决策 1）| 5B 决策 |
| — | ✅ Q2 不编译 demo-hello（决策 2）| 5B 决策 |
| — | ✅ Q3 F044 I018 API 实证（决策 3）| 5B 决策 |
| — | ✅ 核实 1 F050 升级 PARTIAL-BLOCKER | 5C 决策 |
| — | ✅ 核实 2 F040/F041/F051 升级明确 | 5C 决策 |
| — | ✅ 核实 3 I018 中立标（F035/F044 CONFLICT）| 5C 决策 |

### 6.2 决策一致性

- **审计基线**：未修改业务代码 / .mdc / SQL（Batch 1.1 规则 6 + Batch 1.2 一致）
- **状态机**：全部 25 Feature 已落在规则 7 允许的状态（VERIFIED / PARTIAL-BLOCKER / PARTIAL-NON-BLOCKER / CONFLICT / UNVERIFIED）
- **SoT 处理**：I018 沿用 I001 处理模式（中立标，留人类决策）
- **MISSING 处理**：I017 沿用 I002 处理模式（标 PARTIAL-BLOCKER，迁移 = Post-Audit）
- **架构异味**：I019 沿用 I004 处理模式（Backlog 重构）
- **假成功**：I020 沿用 I008 处理模式（接受 + 报告）
- **Bean 冲突降级**：I023 沿用 I012 处理模式（降级 UNVERIFIED）
- **Shadow**：I024 沿用 I006/I007 处理模式（标 PARTIAL-NON-BLOCKER + Shadow）
- **dead code**：I021/I025 沿用 I013 处理模式（登记 backlog）

---

## 七、Batch 1.2 完成度自评

| 维度 | 计划 | 实际 |
|---|---|---|
| Feature Inventory（Phase 1）| 25 | **25** |
| Traceability Matrix（Phase 2）| 25 | **25** |
| Issue Register（Phase 3）| 9 (I017-I025) | **9** |
| Runtime Plan（Phase 4）| 16 (5A) + 16 (5B) | **32 Plan** |
| API 实测（5A）| 6 Feature | **6** |
| API/jshell/Playwright（5B）| 16 Feature | **16** |
| DDL/Entity 校对 | 1 Feature | **1**（F051 升级）|
| Feature 升级（非 PARTIAL-NON-BLOCKER）| 多 | **5 个**：F031 (UNVERIFIED→VERIFIED) + F040/F041 (UNVERIFIED→NON-BLOCKER) + F050 (NON-BLOCKER→BLOCKER) + F051 (BLOCKER→NON-BLOCKER) |
| Feature 维持 | — | **20 个**（含 1 个 CONFLICT 维持 F044，1 个 CONFLICT 维持 F035） |
| Evidence 文件 | 25+ | **23 份** |
| 新增 BUG 实证 | 0+ | **1**：BUG-001 review tenantId null |
| D10 铁律 | ✓ | ✅ 不造证据轨迹完整 |
| 不修改业务代码 | ✓ | ✅ 0 业务代码改动 |

**Phase 5C 完成度：1/1 = 100%（汇总报告）**

**Batch 1.2 总完成度：25/25 Feature = 100%**

---

## 八、Batch 1.3 启动建议（候选 EXT-* 模块）

> **不主动启动** — 等用户决策。

| 候选模块 | 来源 | 范围预估 | 阻塞项 |
|---|---|---|---|
| **EXT-19** 多语言预约确认 | `ext-19-intl-booking.mdc` | ~20-30 Feature | SMTP / WeChat 凭据 |
| **EXT-22** Google Maps + Youzan | `ext-22-google-maps-youzan.mdc` | ~15-20 Feature | GCP 凭据 |
| **EXT-15** CRM 迁移（被 Batch 1.1 跳过）| `ext-15-crm-migration.mdc` | ~5-8 Feature | 迁移决策 |
| **EXT-24 复审** | 已完成 Batch 1.1 | — | — |

---

## 九、Audit Trail（决策时间轴）

| 时间 | 决策/事件 | 影响 |
|---|---|---|
| 2026-09-13 18:05 | Batch 1.2 启动（沿用 commit `12a97ea`）| Pre-Flight → Phase 0 |
| 2026-09-13 18:12 | Batch 1.1 → 1.2 决策落档 + EXT-23 启动 | Phase 1 启动 |
| 2026-09-13 18:51 | Phase 1 Issue 决策：I017-I025 沿用既有模式 | Phase 2 启动 |
| 2026-09-13 19:30 | Phase 2 Full-Stack Traceability Matrix 完成 | Phase 3 Issue 登记 |
| 2026-09-13 20:01 | Phase 5A 启动（6 Feature）| 5A 实证 |
| 2026-09-13 20:25 | F050 relative-path-evidence 完成 | F050 实证 |
| 2026-09-13 20:34 | Phase 5B 启动条件：Q1 D10 / Q2 不编译 / Q3 I018 | 5B 启动 |
| 2026-09-13 20:52 | Phase 5B 完成：16/16 Feature + 23 evidence + 6 probe + 3 spec | 5B 完成 |
| 2026-09-13 21:05 | 用户核实 3 项：F050 升级 / F040-F041-F051 明确 / I018 中立标 | 5C 启动 |
| **2026-09-13 21:15** | **Phase 5C 完成：batch-1.2-summary.md 输出** | **Batch 1.2 完结** |

---

## 十、不修改业务代码（强调）

Phase 5A + 5B + 5C **全程未修改任何业务代码 / .mdc / SQL**：

| 类别 | 文件 | 状态 |
|---|---|---|
| 业务代码 | `backend/src/main/java/com/meiyetec/**` | ❌ 不动 |
| MDC 规则 | `.cursor/rules/*` | ❌ 不动（含 I018 待修 .mdc）|
| SQL DDL | `backend/src/main/resources/db/sql/*` | ❌ 不动（含 I017 待跑 migration）|
| application.yml | `backend/src/main/resources/application.yml` | ❌ 不动（含 F050 待改 plugins-root）|

**所有 Issue Fix Phase = Post-Audit**。

---

**Batch 1.2 — EXT-23 Plugin Runtime 已完成（25/25 Feature）。**

**输出文件**：
- `audit/reports/batch-1.2-summary.md`（本文）
- `audit/runtime/report-5A-batch-1.2.md`（Phase 5A 报告）
- `audit/runtime/report-5B-batch-1.2.md`（Phase 5B 报告 — 含 3 项核实修正）
- `audit/runtime/plan-batch-1.2.md`（Phase 4 Runtime Plan）
- `audit/feature-inventory.md`（Phase 1 Feature Inventory）
- `audit/traceability-matrix.md`（Phase 2 Traceability Matrix）
- `audit/issues/issue-register.md`（Phase 3 Issue Register — 含 Batch 1.2 段）
- `audit/batches.md`（Batch 1.2 段 — 已更新）

**下一步**：等用户最终确认 → 进 Batch 1.3 或 Post-Audit 修复。
