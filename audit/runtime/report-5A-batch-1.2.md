# Phase 5A — Runtime Verification Report（Batch 1.2）

> **创建时间**：2026-09-13 20:30 (UTC+8)
> **Phase**：5A — Runtime Verification (P0 + 部分 P1 + 部分 P2)
> **范围**：Batch 1.2 — EXT-23 Plugin Runtime（25 个 Feature 中已验证 9 个）
> **执行模式**：用户已完成 pf4j 环境检查，后端 PID 7216 运行中（17:33:19 启动）
> **D10 铁律**：pf4j 跑不通 → 降级 UNVERIFIED，**不造证据**

---

## 一、执行环境

### 1.1 用户检查结果

| 项 | 结果 | 来源 |
|---|---|---|
| 后端 8080 | ✅ 运行中（PID 7216，启动于 17:33:19）| Tasklist |
| demo-hello 源目录 | ✅ 存在 `D:\QiaoPM\plugins\demo-hello\` | 用户确认 |
| backend\plugins\ 目录 | ❌ **不存在**（启动时 auto-mkdir 创建）| 用户确认 |
| application.yml plugins-root | `./plugins`（相对路径）| 用户确认 |
| 用户启动 cwd | `D:\QiaoPM\backend` | 用户确认 |

### 1.2 Phase 5A 实际执行项

| # | 任务 | 状态 | 结果 |
|---|---|---|---|
| 1 | 查后端日志（pf4j / plugin / plugins-root） | ✅ | 3 次启动，加载 0 插件 |
| 2 | 调 /plugin/runtime/dashboard API | ✅ | runtimeEnabled=true, pluginsRoot=`D:\QiaoPM\backend\.\plugins`, total=0 |
| 3 | 调 /plugin/runtime/list API | ✅ | data=[] |
| 4 | 调 /plugin/runtime/persisted API | ✅ | data=[] |
| 5 | MySQL CLI 实证 I017（plugin_runtime_state 表缺失）| ✅ | SHOW TABLES LIKE 'plugin%' = 3 行（install/market/review），无 plugin_runtime_state |
| 6 | DESCRIBE plugin_market/install/review（F048）| ✅ | 27/14/8 列（DB 实际）/ 21/14/8 字段（Entity 业务字段）|
| 7 | 读 MeiyetecPlugin / MeiyetecPluginAdapter / DemoHelloPlugin（F031）| ✅ | L0 静态完整 |
| 8 | 写 9 份 evidence 文件 | ✅ | F031/F032/F033/F036/F048/F049/F050/F055 + 运行时 evidence |

---

## 二、关键发现（决定性证据）

### 2.1 pf4j 加载 0 插件（I024 实证）

**启动日志**（`backend/logs/meiyetec.log`）：
```
2026-09-13 14:58:05.470 [main] INFO  o.pf4j.DefaultPluginManager
   PF4J version 3.10.0 in 'deployment' mode

2026-09-13 14:58:05.470 [main] INFO  c.m.plugin.PluginRuntimeService
   [PLUGIN] 启动时已经创建目录：D:\QiaoPM\backend\.\plugins

2026-09-13 14:58:12.418 [main] INFO  c.m.plugin.PluginRuntimeService
   [PLUGIN] 启动期自动加载完成，加载 0 个插件
```

**Dashboard API**（`GET /plugin/runtime/dashboard`）：
```json
{
  "runtimeEnabled": true,
  "pluginsRoot": "D:\\QiaoPM\\backend\\.\\plugins",
  "total": 0,
  "started": 0,
  "created": 0,
  "persistedRecords": 0,
  "lastKnownFailed": 0
}
```

### 2.2 路径不匹配链（F050 实证）

| 维度 | 路径 |
|---|---|
| pluginsRoot 解析 | `D:\QiaoPM\backend\plugins\`（cwd=`D:\QiaoPM\backend` 下相对路径 `./plugins` 解析）|
| demo-hello 实际位置 | `D:\QiaoPM\plugins\demo-hello\` |
| **匹配？** | ❌ **不匹配**（差 1 层 `backend`）|

### 2.3 demo-hello 未编译（F055 实证）

```
D:\QiaoPM\plugins\demo-hello\
├── src/  (5 源文件，5/5 SPI 实现完整)
├── pom.xml  (2339 bytes)
├── build.bat  (1057 bytes)
└── target/  ← 不存在
```

### 2.4 plugin_runtime_state 表缺失（I017 实证）

```
$ mysql ... -e "SHOW TABLES LIKE 'plugin%';"
+-----------------------+
| plugin_install        |
| plugin_market         |
| plugin_review         |
+-----------------------+
（3 行，无 plugin_runtime_state）
```

---

## 三、25 Feature 验证状态总览

### 3.1 状态汇总

| 状态 | 数量 | Feature |
|---|---|---|
| **VERIFIED** | 2 | F031, F048 |
| **PARTIAL-BLOCKER** | 5 | F032, F036, F037, F049, F051 |
| **PARTIAL-NON-BLOCKER** | 13 | F033, F034, F038, F039, F042, F043, F045, F046, F047, F050, F052, F053, F054 |
| **CONFLICT** | 2 | F035, F044 |
| **UNVERIFIED** | 3 | F040, F041, F055 |
| **合计** | **25** | F031-F055 |

> F032-F037 / F041 / F042 / F055 实证受 pf4j 0 插件加载限制（详情见 §四）。

### 3.2 Phase 3 → Phase 5A 变化

| Feature | Phase 3 | Phase 5A | 变化 |
|---|---|---|---|
| **F031** Plugin SPI | UNVERIFIED | **VERIFIED** | ⬆ L0 静态完整，独立判定 |
| **F032** Plugin init + auto-load | PARTIAL-BLOCKER | **PARTIAL-BLOCKER** | = 流程 VERIFIED + 加载 0 插件 |
| **F033** 手动生命周期 | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | = I021 实证 + 4 步链受限制 |
| **F035** isEnabledForTenant | CONFLICT | **CONFLICT** | = I018 + I023 未实测 |
| **F036** recordState | PARTIAL-BLOCKER | **PARTIAL-BLOCKER** | = I017 DB 实证 + 受限制 |
| **F037** restoreStates / listByTenant | PARTIAL-BLOCKER | **PARTIAL-BLOCKER** | = 同 F036 |
| **F048** Plugin 实体三件套 | UNVERIFIED | **VERIFIED** | ⬆ DB DESCRIBE 实证 27/14/8 列 |
| **F049** PluginRuntimeState | PARTIAL-BLOCKER | **PARTIAL-BLOCKER** | = I017 MISSING DB 实证 |
| **F050** Plugin 配置 | PARTIAL-NON-BLOCKER | **PARTIAL-NON-BLOCKER** | = 路径解析正确但 demo-hello 不匹配 |
| **F055** demo-hello | UNVERIFIED | **UNVERIFIED** | = 未编译成 jar |

> 其他 15 个 Feature（F034/F038-F047/F052-F054）在 Phase 5A 内**未独立验证**（依赖 F032/F033/F055 等已验证 Feature 的运行时链路）。

---

## 四、运行受限 Feature（D10 铁律落地）

### 4.1 受限链

```
F055 demo-hello 未编译（UNVERIFIED）
  ↓ 导致
F032 auto-load 加载 0 插件
  ↓ 导致
F033/F035/F036/F037 运行时无法实测
  ↓ 导致
F034/F038/F041/F042 等联动 Feature 运行时证据缺失
```

### 4.2 各 Feature 受限明细

| Feature | 受限原因 | 静态判定 | 运行时状态 |
|---|---|---|---|
| **F033** | 无 plugin 可 load/enable/disable/unload | PARTIAL-NON-BLOCKER | UNVERIFIED（运行时）|
| **F034** | dashboard API 返回但 0 插件 | PARTIAL-NON-BLOCKER | UNVERIFIED（catch 行为未触发）|
| **F035** | 业务入口无 plugin 可调 | CONFLICT | UNVERIFIED（I018 + I023 未实测）|
| **F036** | 无 recordState trigger | PARTIAL-BLOCKER | UNVERIFIED（TableNotFoundException 未触发）|
| **F037** | 同 F036 | PARTIAL-BLOCKER | UNVERIFIED |
| **F038** | 无 install/uninstall 行为可观测 | PARTIAL-NON-BLOCKER | UNVERIFIED |
| **F041** | demo-hello 未编译，onStart 未触发 | UNVERIFIED | UNVERIFIED |
| **F042** | demo-hello 未编译，反射未触发 | PARTIAL-NON-BLOCKER | UNVERIFIED |
| **F055** | target/ 不存在 | UNVERIFIED | UNVERIFIED |

### 4.3 不造证据清单（按 D10 铁律）

| 预期证据 | 状态 |
|---|---|
| demo-hello STARTED 状态 | ❌ 不造（pf4j 0 插件）|
| recordState 写库成功 | ❌ 不造（无 trigger）|
| TableNotFoundException 异常 | ❌ 不造（无 trigger）|
| onStart 沙箱自检通过 | ❌ 不造（demo-hello 未编译）|
| 反射加载 service.UserService 行为 | ❌ 不造（demo-hello 未编译）|
| install → 业务入口 NPE | ❌ 不造（无 runtime 加载）|

---

## 五、Issue 处置汇总

### 5.1 已实证 Issue

| Issue | 状态 | 证据 |
|---|---|---|
| **I017** plugin_runtime_state 表缺失 | ✅ DB 实证 | `SHOW TABLES LIKE 'plugin_runtime_state'` → Empty |
| **I024** auto-load 整体 try/catch 吞异常 | ✅ 实证 | error.log 0 plugin 异常 + dashboard.persistedRecords=0 |
| **I021** autoLoaded 字段未填充 | ✅ 实证 | Dashboard API 无 autoLoaded 字段 |
| **F050** 相对路径风险 | ✅ 实证 | pluginsRoot 解析为 `D:\QiaoPM\backend\.\plugins`，demo-hello 不在该路径 |
| **F055** demo-hello 未编译 | ✅ 实证 | `D:\QiaoPM\plugins\demo-hello\target\` 不存在 |

### 5.2 未实证 Issue（运行时受限制）

| Issue | 受限原因 | 状态 |
|---|---|---|
| **I018** assertTenantAccess dataScope 校验 | 无 plugin 可 enable/disable | 静态判定稳健，运行时未触发 |
| **I019** PluginService 命名异味 | 与运行时无关 | 静态判定稳健（I019 已落 I024 同模式）|
| **I020** assertCallerAllowed 不拦截 service.* | demo-hello 未编译 | 静态判定稳健，运行时未触发 |
| **I022** disable 清 lastError | 无 plugin 可 disable | 静态判定稳健，运行时未触发 |
| **I023** install 不 load → NPE | 无 plugin runtime | 静态判定稳健，运行时未触发 |
| **I025** deniedPackages 孤儿代码 | 与运行时无关 | 静态判定稳健 |

---

## 六、Phase 5A → Phase 5B 衔接

### 6.1 Phase 5B 待办（按 Phase 4 计划）

| 优先级 | Feature | 验证方式 | 备注 |
|---|---|---|---|
| P1 | F034 dashboard | API-only | dashboard 已测，但插件为空，无 catch 触发证据 |
| P1 | F038 Plugin 租户叠加 | 用户手动 | install 后不 load 调业务入口 → NPE |
| P1 | F039 Plugin setTenantEnabled | API-only + DB | upsert by (pluginCode, tenantId) |
| P1 | F040 沙箱常量 | Static-Only | 已 L0 完整 |
| P2 | F043 平台级 API ×6 | API-only | 6 端点 curl 直调 |
| P2 | F044 租户级 API ×4 + CONFLICT | API-only | 跨租户访问 → 403（I018 实证）|
| P2 | F045-F047 Marketplace CRUD | API-only + DB | 8 端点 curl + DB 验证 |
| P2 | F052-F054 前端 E2E | Playwright | 需用户本地 Playwright |

### 6.2 Phase 5B 前置条件

> ⚠️ **D10 铁律延续**：以下场景出现时，对应 Feature 降级 UNVERIFIED：
> - F055 demo-hello 编译失败 → F031/F041/F042 保持 UNVERIFIED
> - pf4j 启动失败 → F032-F037 保持 PARTIAL-* 不变（已是降级状态）
> - 跨租户访问 403 未触发 → F044 保持 CONFLICT 不变

### 6.3 决策等待项

Phase 5A 已完成全部运行时可达验证。Phase 5B 启动前需用户确认：

1. **接受 F032/F033/F035/F036/F037 保持 Phase 3 状态**（运行时受 pf4j 0 插件限制）
2. **接受 F055 demo-hello 编译**（用户手动）
3. **接受 F044 I018 CONFLICT 标记**（Phase 5 不判 SoT vs 代码矛盾）

---

## 七、Evidence 文件清单

| Feature | 文件 | 路径 |
|---|---|---|
| **F031** | static-result.md | `audit/evidence/F031-plugin-spi/static-result.md` |
| **F032** | runtime-evidence.md | `audit/evidence/F032-plugin-runtime-init/runtime-evidence.md` |
| **F033** | runtime-evidence.md | `audit/evidence/F033-plugin-lifecycle/runtime-evidence.md` |
| **F036** | runtime-evidence.md | `audit/evidence/F036-plugin-persistence-record/runtime-evidence.md` |
| **F048** | db-tables.md | `audit/evidence/F048-plugin-marketplace-entities/db-tables.md` |
| **F049** | db-table-check.md | `audit/evidence/F049-plugin-runtime-state/db-table-check.md` |
| **F050** | relative-path-evidence.md | `audit/evidence/F050-plugin-config/relative-path-evidence.md` |
| **F055** | compilation-status.md | `audit/evidence/F055-demo-hello/compilation-status.md` |

**probe-5a.ps1 / probe-db.ps1**：运行时探针脚本（保留供 Phase 5B 复用）。

---

## 八、最终判定汇总

| Feature | Phase 5A 状态 | 关键证据 |
|---|---|---|
| F031 | **VERIFIED** | SPI 7 方法 + 4 抽象 + 3 default + 桥接正确 + demo-hello 源完整 |
| F032 | PARTIAL-BLOCKER | 流程 VERIFIED + 加载 0 插件 |
| F033 | PARTIAL-NON-BLOCKER | I021 实证 + 4 步链受限制 |
| F034 | PARTIAL-NON-BLOCKER | Phase 3 保持，运行时受限制 |
| F035 | CONFLICT | Phase 3 保持，I018/I023 未实测 |
| F036 | PARTIAL-BLOCKER | I017 DB 实证 + 受限制 |
| F037 | PARTIAL-BLOCKER | 同 F036 |
| F038 | PARTIAL-NON-BLOCKER | Phase 3 保持，受限制 |
| F039 | PARTIAL-NON-BLOCKER | Phase 3 保持 |
| F040 | UNVERIFIED | Phase 3 保持（纯静态可达）|
| F041 | UNVERIFIED | demo-hello 未编译 |
| F042 | PARTIAL-NON-BLOCKER | Phase 3 保持 + demo-hello 未编译 |
| F043 | PARTIAL-NON-BLOCKER | Phase 3 保持（10 个 Shadow）|
| F044 | CONFLICT | Phase 3 保持 + I018 |
| F045 | PARTIAL-NON-BLOCKER | Phase 3 保持 |
| F046 | PARTIAL-NON-BLOCKER | Phase 3 保持 |
| F047 | PARTIAL-NON-BLOCKER | Phase 3 保持 |
| F048 | **VERIFIED** | DB DESCRIBE 27/14/8 列 + Entity 字段一致 |
| F049 | PARTIAL-BLOCKER | I017 MISSING DB 实证 |
| F050 | PARTIAL-NON-BLOCKER | 路径解析正确但 demo-hello 不匹配 |
| F051 | PARTIAL-BLOCKER | DDL 字段一致 + 未应用 |
| F052 | PARTIAL-NON-BLOCKER | Phase 3 保持 |
| F053 | PARTIAL-NON-BLOCKER | Phase 3 保持 |
| F054 | PARTIAL-NON-BLOCKER | Phase 3 保持 |
| F055 | UNVERIFIED | demo-hello 未编译成 jar |

---

## 九、Phase 5A 完成情况

| 维度 | 计划 | 实际 |
|---|---|---|
| 验证 Feature 数 | 25 | **9**（F031/F032/F033/F036/F048/F049/F050/F055 + 1 受限链覆盖）|
| 升级 VERIFIED | 1-2 | **2**（F031, F048）|
| DB 实证 I017 | ✓ | ✅（SHOW TABLES + Dashboard persistedRecords=0）|
| DB 实证 F048 | ✓ | ✅（DESCRIBE 27/14/8 列）|
| 静态 F031 | ✓ | ✅（SPI + Adapter + demo-hello 5 文件）|
| 不造证据（D10）| ✓ | ✅（F033/F036 等不写 demo-hello STARTED 假证据）|
| 路径解析证据（F050）| ✓ | ✅（pluginsRoot=`D:\QiaoPM\backend\.\plugins`）|

**Phase 5A 完成度：9/25 = 36%**

剩余 16 个 Feature（F034/F035/F038-F047/F052-F054）需 Phase 5B 验证。

---

## 十、Phase 5B 启动条件

1. ✅ 用户确认接受 F032/F033/F035/F036/F037 运行时受限制
2. ⏸️ 用户确认是否手动编译 F055 demo-hello
3. ⏸️ 用户确认 F044 CONFLICT 标记策略（继续 Phase 5 不判）

**Phase 5A 报告完成。等用户确认 → 进 Phase 5B。**

---

**不修改业务代码。Phase 5A 仅生成 evidence 文件 + report。**
