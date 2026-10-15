# Phase 4 — Runtime Verification Plan（Batch 1.2）

> **创建时间**：2026-09-13 20:10 (UTC+8)
> **Phase**：4 — Runtime Verification Plan
> **范围**：Batch 1.2 — EXT-23 Plugin Runtime（25 个 Feature：F031-F055）
> **Batch 决策回顾**（沿用 I017-I025 @ 18:51 + D6-D10 @ 20:10）：
> - **D6**：10 个 Shadow Feature（F043 ×6 + F044 ×4）标 Shadow + PARTIAL-NON-BLOCKER（不动前端，Phase 4 仅登记）
> - **D7**：I017（plugin_runtime_state 表缺失）维持 PARTIAL-BLOCKER，**不跑 migration**
> - **D8**：I018（SoT 文档漂移）维持 CONFLICT，**不改 .mdc**
> - **D9**：I019（PluginService 命名异味）+ I025（deniedPackages 孤儿）+ I021（autoLoaded dead code）合并 backlog（Post-Audit）
> - **D10**：I020（合作式沙箱）+ I022（disable 副作用）+ I023（幽灵 NPE）Phase 5 实测优先级：I023 > I022 > I020
> - **D10 验证铁律**：Phase 5 pf4j 跑不通 → 降级 UNVERIFIED，**不造证据**
> - **受保护页面干扰**：Plugin Runtime 端点（`/plugin/runtime/*` + `/plugin/*`）与 `/dashboard` / `/mh/visual` / `/mh/consent` 后端接口零交集 → 不触及
> - **规则**：审计阶段不修改业务代码（规则 6）→ Phase 4 仅记录验证策略，不进 Phase 5 执行

---

## 〇、运行时验证总览

### 0.1 验证工具矩阵

| 工具 | 适用 | 状态 |
|---|---|---|
| **curl / PowerShell `Invoke-RestMethod`** | API-only 验证 | ✅ 可用（Phase 5 用户跑）|
| **MySQL CLI** / **MySQL MCP** | DB-only 验证 | ✅ 可用（按 preflight §8.3 配置）|
| **JVM `mvn clean package` + 后端启动** | pf4j 加载真实 JAR | ⚠️ **Cursor 不能跑**（需 JVM + 插件 JAR 编译）→ 降级方案见 §0.2 |
| **Playwright MCP** | UI E2E | ❌ **未配置** → 降级方案 |
| **手动浏览器（用户执行）** | UI E2E 降级 | ✅ Phase 5 用户跑 Playwright 脚本 |

### 0.2 pf4j 降级方案（JVM 加载真实 JAR 不可达）

对任何标 "pf4j 实测" 的 Feature（F032/F033/F034/F035/F036/F037/F038/F041/F055），Phase 5 降级为：

1. **Cursor 静态验证**（已完成于 Phase 3）：代码路径、catch 位置、表/类引用全部 L0 可达
2. **用户手动跑**：
   - 在本地执行 `cd plugins/demo-hello && build.bat`（**仅 Windows**，F055）→ 生成 `target/demo-hello-1.0.0.jar`
   - 拷贝到 `${plugin.runtime.plugins-root}/demo-hello/1.0.0/`（F032/F033）
   - 启动后端 → `GET /plugin/runtime/list` 验证（curl）
3. **结果贴回** → `audit/evidence/FXXX-<slug>/result.md`（用户记录 pass/fail + 截图）
4. **若 pf4j 跑不通**（F055 未编译 / 后端启动失败）→ **直接降级 UNVERIFIED，不造证据**（D10 铁律）

### 0.3 验证方式 vs Feature 类型映射

| Feature 类型 | 主验证 | 辅验证 |
|---|---|---|
| **pf4j 生命周期（load/enable/disable/unload）** | 用户手动（mvn + 后端启动） | DB 断言（plugin_runtime_state） |
| **Plugin 业务入口（isEnabledForTenant）** | 用户手动 + API-only | DB 断言（plugin_install） |
| **PluginRuntimeController REST** | API-only（curl 直调） | DB 断言 |
| **PluginSecurityPolicy（沙箱）** | 用户手动（demo-hello onStart） | Static-Only（白/黑名单常量） |
| **Marketplace CRUD（publish/install/review）** | API-only | DB 断言（plugin_market/install/review） |
| **Entity / DDL / 配置** | L0/L1 静态已覆盖 | DB-only（DESCRIBE） |
| **Frontend UI** | Playwright E2E（降级）| API-only（直调后台）|
| **CONFLICT（I018）**| Phase 5 **只读不判** | 静态 SoT 比对完成 |
| **UNVERIFIED（demo-hello 编译）**| 用户手动 `mvn clean package` | Static-Only |

### 0.4 优先级规则

| 优先级 | 适用 Feature 类型 | Phase 5 必测 |
|---|---|---|
| **P0** | Plugin 加载/卸载/启停核心链路 + 持久化 + 业务入口 | ✅ |
| **P1** | 租户隔离 + 沙箱 + 持久化读取 | ✅ |
| **P2** | Controller REST + 前端 UI + Entity + 配置 + DDL + demo-hello | ⏸️ 降级后非强制 |

---

## 一、P0 — Plugin 核心链路（6 个 Feature）

| Feature ID | Priority | 验证方式 | 是否需 E2E | 是否需 DB 断言 | 理由 |
|---|---|---|---|---|---|
| **F031** Plugin SPI 契约 | **P0** | **Static-Only** | ✗ | ✗ | `MeiyetecPlugin` 接口 5 方法 + `MeiyetecPluginAdapter extends Plugin` 桥接（`plugin/MeiyetecPlugin.java:27-47` + `plugin/MeiyetecPluginAdapter.java:25-38`）。L0 静态可达，**Phase 5 不需要黑盒验证**（接口契约本身通过 demo-hello onStart 间接覆盖，依赖 F055 编译）|
| **F032** Plugin 运行时 init + auto-load | **P0** | **用户手动 + DB** | ✗ | ✗* | `PluginRuntimeService.run` ApplicationRunner (`:88-101`) + `loadAllFromDisk` (`:118-143`)。**预期 Phase 5 暴露 I017**：`recordState` 写表 → `TableNotFoundException` → try/catch 包裹整体（`:94-100`）仅 warn → 与 I008 Fake Success 同链路。**用户手动**：① 启动后端 → 看 `[PLUGIN-RUNTIME]` 日志；② `GET /plugin/runtime/list` → 验证插件状态。DB 断言：可选（I017 时 `plugin_runtime_state` 不存在 → 写库失败被吞）|
| **F033** Plugin 手动生命周期（load/enable/disable/unload）| **P0** | **用户手动 + API-only + DB** | ✗ | ✓ | `PluginRuntimeService.loadPlugin` (`:152-163`) + `enablePlugin` (`:167-174`) + `disablePlugin` (`:178-191`) + `unloadPlugin` (`:195-213`)。**用户手动**：`POST /plugin/runtime/demo-hello/load` → `enable` → `disable` → `unload` 四步链验证。**预期 Phase 5 暴露 I017 + I022**：① load → `TableNotFoundException` 持久化失败；② **disable → `recordState(...,errorMsg=null)` 清掉 FAILED 错误**（I022 副作用）。DB 断言：失败前先 write `state=FAILED + lastError="测试失败"` → 调 disable → 查 `plugin_runtime_state.lastError` 应为 null（I022 验证）|
| **F035** Plugin `isEnabledForTenant` 业务入口 | **P0** | **用户手动 + API-only** | ✗ | ✗ | `PluginRuntimeService.isEnabledForTenant` (`:256-259`) → 委派 `PluginTenantService.isEnabledForTenant` (`plugin/PluginTenantService.java:43-66`)。**CONFLICT（I018）** + **I023 幽灵 NPE 风险**。Phase 5 **只读不判 I018**（SoT vs 代码矛盾已记录）。**用户手动**：① 普通租户 token 调跨租户 `GET /plugin/runtime/tenant/{tid}/list` → 预期 `BizCode.FORBIDDEN(403)`（assertTenantAccess 已实现）→ 验证 I018 文档漂移；② `POST /plugin/install`（仅插 plugin_install）→ 调业务入口 → 预期 NPE（I023 幽灵记录） |
| **F036** Plugin 持久化 `recordState` 写入 | **P0** | **API-only + DB-only** | ✗ | ✓ | `PluginPersistenceService.recordState` (`plugin/PluginPersistenceService.java:42-77`)。**PARTIAL-BLOCKER（I017）** + **@Transactional 失效**（try/catch 包裹整体，`:65-72`）。**预期 Phase 5 暴露**：触发 load/enable → `recordState` 写库 → `TableNotFoundException` → catch 吞 → 事务回滚失效（`:70-72` catch 整体包住 → 即使 catch 重新抛也来不及）。DB 断言：① `DESCRIBE plugin_runtime_state` 应 `Table not exists`；② `SHOW TABLES LIKE 'plugin_runtime_state'` 应 0 行 |
| **F037** Plugin 持久化 `restoreStates` + `listByTenant` 读取 | **P0** | **API-only + DB-only** | ✗ | ✓ | `PluginPersistenceService.restoreStates` (`:81-87`) + `listByTenant` (`:91-96`)。**PARTIAL-BLOCKER（依赖 F036/I017）**。**预期 Phase 5 暴露**：启动期 `dashboard()` 调用 `restoreStates` → 表缺失 → 返回 `List.of()` → dashboard `persistedRecords=0/lastKnownFailed=0`（I008 同模式 Fake Success）。DB 断言：`SELECT COUNT(*) FROM plugin_runtime_state` → 异常（表不存在） |

---

## 二、P1 — 租户隔离 + 沙箱 + 持久化（5 个 Feature）

| Feature ID | Priority | 验证方式 | 是否需 E2E | 是否需 DB 断言 | 理由 |
|---|---|---|---|---|---|
| **F038** Plugin 租户叠加 pf4j | **P1** | **用户手动 + API-only + DB** | ✗ | ✓ | `PluginTenantService.isEnabledForTenant` (`plugin/PluginTenantService.java:43-66`)。**三层判断**：① pf4j state ≠ STARTED → false；② install 记录不存在 → 递归查平台级；③ 仍无 → 默认 true。**I023 兜底默认启用**：未 load 的插件被 `install` 后 → `isEnabledForTenant` 返回 true → 业务侧调不存在的插件入口 → NPE。**用户手动**：① install 后不 load → 调 `isEnabledForTenant` → 预期 true；② 调业务入口（反射）→ 预期 NPE。DB 断言：查 `plugin_install` 应有 1 行（仅 install 表，无 runtime 表）|
| **F039** Plugin 租户 `setTenantEnabled` 启停 | **P1** | **API-only + DB** | ✗ | ✓ | `PluginTenantService.setTenantEnabled` (`plugin/PluginTenantService.java:71-95`)。upsert by `(pluginCode, tenantId)`。**用户手动**：`POST /plugin/runtime/tenant/{tid}/{code}/enable` → 查 DB → `enabled` 应为 1 → `disable` → `enabled` 应为 0。DB 断言：① `SELECT enabled FROM plugin_install WHERE plugin_code=? AND tenant_id=?`；② 重复调 3 次 → 验证 upsert 不重复（**当前无 `(tenant_id, plugin_code)` UNIQUE KEY → F039 潜在重复行风险**）|
| **F040** Plugin 沙箱 白/黑名单常量 | **P1** | **Static-Only + 用户手动** | ✗ | ✗ | `PluginSecurityPolicy.HOST_PUBLIC_PACKAGES` (`:33-37`) + `HOST_FORBIDDEN_PACKAGES` (`:40-50`) + `FORBIDDEN_JDK_PACKAGES` (`:53-63`)。L0 静态可达，Phase 5 不需要黑盒验证（通过 F041/F042 间接覆盖）。**用户手动**：demo-hello onStart 调 `assertPackageAllowed` → 验证白名单行为（F041 间接）|
| **F041** Plugin 沙箱 `assertPackageAllowed` 自检 | **P1** | **用户手动** | ✗ | ✗ | `PluginSecurityPolicy.assertPackageAllowed` (`plugin/PluginSecurityPolicy.java:78-97`)。**用户手动**：① demo-hello onStart 调 `assertPackageAllowed("demo-hello", "com.meiyetec.demo.")` → 预期通过（白名单 `com.meiyetec.plugin.`）；② 在 demo-hello 加 `assertPackageAllowed("demo-hello", "com.meiyetec.service.UserService")` 测试用例 → 预期通过（**`service` 不在黑名单 `service.internal` → I020 合作式沙箱**）。**注意**：F055 demo-hello 未编译 → 若 Phase 5 用户跑 `mvn clean package` 失败 → **降级 UNVERIFIED（D10 铁律）** |
| **F042** Plugin 沙箱 `assertCallerAllowed` 反射探测 | **P1** | **用户手动** | ✗ | ✗ | `PluginSecurityPolicy.assertCallerAllowed` (`plugin/PluginSecurityPolicy.java:106-135`)。**PARTIAL-NON-BLOCKER（I020 合作式沙箱）** + **孤儿代码（I025）**。**用户手动**：① 反射加载 `com.meiyetec.service.UserService`（非 internal）→ 预期通过（**沙箱不拦截 → I020 实证**）；② 反射加载 `com.meiyetec.service.internal.NonExistent` → 预期 `ClassNotFoundException` → 沙箱抛出 → 黑名单命中（验证白名单兜底逻辑）。**Post-Audit**：I025 `deniedPackages()` 孤儿删除 |

---

## 三、P2 — Controller REST + 前端 + Entity + 配置 + DDL（14 个 Feature）

| Feature ID | Priority | 验证方式 | 是否需 E2E | 是否需 DB 断言 | 理由 |
|---|---|---|---|---|---|
| **F043** Plugin Runtime Controller 平台级 API（×6）| **P2** | **API-only** | ✗ | ✗ | `PluginRuntimeController` (`controller/PluginRuntimeController.java:76-130`) — `GET /list` `:78-81` + `GET /dashboard` `:84-87` + `POST /{code}/load` `:90-103` + `POST /{code}/enable` `:106-112` + `POST /{code}/disable` `:115-121` + `POST /{code}/unload` `:124-130`。**Shadow Feature（I024，10 个端点均无前端消费）+ P1 RequestBody 无 @Valid**。**用户手动**：curl 6 个端点直调验证响应。**预期**：① `GET /list` → 应返回所有插件 + NOT_FOUND/RUNTIME_DISABLED 标记；② `GET /dashboard` → 应返回 `runtimeEnabled/pluginsRoot/total/started/.../persistedRecords/lastKnownFailed` 字段；③ load 不传 version → 走字典序倒序挑版本（`PluginRuntimeService.resolvePluginDir` `:227-237`，Operational Notes §5 已知简陋）|
| **F044** Plugin Runtime Controller 租户级 API（×4）| **P2** | **API-only + DB** | ✗ | ✓ | `PluginRuntimeController.assertTenantAccess` (`:58-69`) + 4 个租户级端点（`:140-200`）。**CONFLICT（I018）+ Shadow（I024）+ P2 RequestParam 无 defaultValue**。**用户手动**：① 普通租户 token + 跨租户 `tid` 调 4 个端点 → 预期 `BizCode.FORBIDDEN(403)`（**I018 实证**：assertTenantAccess 真的拦？）；② 平台超管（dataScope=ALL）调 → 预期 200。**Phase 5 不判 I018**（SoT 漂移已记录），仅观测行为。DB 断言：`GET /persisted?tenantId=X` → 应返回该 tenantId 的持久化状态（**预期 I017 暴露**：表不存在 → 返回 `success=true + data=null` → I008 Fake Success）|
| **F045** Plugin Marketplace 上架管理（×3）| **P2** | **API-only + DB** | ✗ | ✓ | `PluginService` (`service/PluginService.java:38-72`) — `POST /plugin/market` `:39-50` + `GET /plugin/market/list` `:52-62` + `POST /plugin/market/{id}` `:64-72`。**架构异味（I019，PluginService 是 @RestController）+ P3 RequestBody 无 @Valid（×3）**。**用户手动**：① `POST /plugin/market` body `{pluginCode, pluginName, category, version, description, shortDesc}` → 查 DB → `plugin_market` 应有 1 行；② `GET /plugin/market/list` → 应返回按 `installCount` 倒序；③ `POST /plugin/market/{id}` 改 `description` → 查 DB 应更新。DB 断言：`SELECT * FROM plugin_market WHERE plugin_code=?` |
| **F046** Plugin Marketplace 安装生命周期（×3）| **P2** | **API-only + DB** | ✗ | ✓ | `PluginService` (`service/PluginService.java:74-103`) — `POST /plugin/install` `:75-95` + `POST /plugin/install/{id}/uninstall` `:97-103` + `GET /plugin/install/list` `:105-110`。**I023 幽灵 NPE + I019 命名异味 + P4 @Transactional 无 rollbackFor**。**用户手动**：① `POST /plugin/install` body `{marketId, tenantId}` → 查 DB → `plugin_install` 应有 1 行 + `plugin_market.installCount` 应 +1；② 不调 load → 调业务入口 → 预期 NPE（**I023 实证**）；③ `POST /plugin/install/{id}/uninstall` → 查 DB → `plugin_install` 应删除或标记。DB 断言：`SELECT enabled, install_status FROM plugin_install WHERE plugin_code=?` |
| **F047** Plugin Marketplace 评价（×2）| **P2** | **API-only + DB** | ✗ | ✓ | `PluginService` (`service/PluginService.java:112-130`) — `POST /plugin/review` `:113-121` + `GET /plugin/review/list/{pluginId}` `:123-130`。**I019 命名异味 + PluginService.listReviews 未按 tenantId 隔离（跨租户读风险）**。**用户手动**：① tenantA 发评价 → tenantB `GET /plugin/review/list/{pluginId}` → **预期能读到 tenantA 评价（跨租户读风险）**。DB 断言：`SELECT * FROM plugin_review WHERE plugin_id=?` → 不应过滤 tenantId |
| **F048** Plugin 实体 Marketplace 三件套 | **P2** | **DB-only** | ✗ | ✓ | `PluginMarket` (`entity/PluginMarket.java:13-32`) 21 字段 + `PluginInstall` (`:10-31`) 14 字段 + `PluginReview` (`:10-21`) 8 字段。L0 静态可达 + Phase 5 DB DESCRIBE 验证。**用户手动**：`mysql -e "DESCRIBE plugin_market; DESCRIBE plugin_install; DESCRIBE plugin_review"` → 三表应分别 21/14/8 列。**预期**：三表已存在（`db-snapshot.sql:7092/7120/7159`）→ 不暴露 I017（I017 仅影响 plugin_runtime_state）|
| **F049** Plugin 实体 `PluginRuntimeState` | **P2** | **DB-only** | ✗ | ✓ | `PluginRuntimeState` (`entity/PluginRuntimeState.java:27-54`) 8 字段 + `PluginRuntimeStateMapper` (`mapper/PluginRuntimeStateMapper.java:5`)。**MISSING（I017，表缺失）**。**用户手动**：`mysql -e "DESCRIBE plugin_runtime_state"` → **预期错误 `Table 'meiyetec.plugin_runtime_state' doesn't exist`**（I017 实证）。DB 断言：① `SHOW TABLES LIKE 'plugin_runtime_state'` → 0 行；② 对照 DDL `db/sql/migrate_ext_23_plugin_runtime_state.sql:9-29`（8 字段 vs DDL 9 字段 — 差 1 字段，需 review）|
| **F050** Plugin 配置 application.yml | **P2** | **Static-Only + 用户手动** | ✗ | ✗ | `application.yml:183-187` — `plugin.runtime.{enabled,plugins-root,auto-load-on-startup}`。L0 静态可达 + Phase 5 用户手动验证配置生效。**用户手动**：① 启动后端 → 看 `[PLUGIN-RUNTIME]` 启动日志 → 验证 `plugins-root` 路径正确（**默认 `./plugins` 相对路径 → 生产风险**，Operational Notes §2）；② 设 `PLUGIN_RUNTIME_ENABLED=false` 环境变量 → 启动 → 验证 `RUNTIME_DISABLED` 标记。**Post-Audit**：生产部署改绝对路径 |
| **F051** Plugin DB `migrate_ext_23` DDL | **P2** | **DB-only + Static-Only** | ✗ | ✗ | DDL `db/sql/migrate_ext_23_plugin_runtime_state.sql:9-29`。**DDL 已写未应用（I017）+ DDL 9 字段 vs entity 8 字段（差 1 字段需 review）**。**用户手动**：`mysql -e "SOURCE db/sql/migrate_ext_23_plugin_runtime_state.sql"` → 应成功（**但 Phase 5 不跑！D7 决策**）→ 仅 grep `db-snapshot.sql` 验证未应用：`grep plugin_runtime_state db-snapshot.sql` → 0 命中（I017 实证）。**Post-Audit**：跑批前先 review DDL 9 vs entity 8 字段差 |
| **F052** Plugin 前端 市场 + 已安装 tab | **P2** | **Playwright E2E 降级** | ✓ | ✗ | `plugin/index.vue:5-30` 「插件市场」tab + 「已安装」tab。L0 静态可达 + Phase 5 用户手动 Playwright。**用户手动**：① 打开 `/plugin` 页面 → 应只有 2 个 tab（市场 + 已安装，**无 runtime tab → I024 Shadow**）；② 「发布插件」按钮 → `pubDialog` 5 字段 → 提交 → 预期缺 loading 态；③ 「安装」按钮 → 应仅插 `plugin_install` 表（**不触发 runtime load → I023 联动**）。**降级**：Playwright MCP 未配置 → 用户本地跑 `frontend/meiyetec-web/e2e/audit/` 脚本 |
| **F053** Plugin 前端 发布 + 评价对话框 | **P2** | **Playwright E2E 降级** | ✓ | ✗ | `plugin/index.vue:34-59` `pubDialog`（5 字段）+ `:63-78` `reviewDialog`（2 字段）。**无 loading 态 / 无二次确认 / 无错误码展示**。**用户手动**：① 打开 `pubDialog` → 不填字段直接提交 → 验证错误码展示（**预期无 errorCode 字段 → I006 同模式**）；② 打开 `reviewDialog` → 不填字段提交 → 同预期 |
| **F054** Plugin 前端 `api/plugin.ts` 封装 | **P2** | **Static-Only + Playwright E2E** | ✗* | ✗ | `api/plugin.ts:6-19` `pluginApi` 8 方法。**缺所有 `/plugin/runtime/*` 方法（I024）+ 仅 marketplace 8 方法**。L0 静态可达（**grep `runtime` 全文 0 命中**）+ Phase 5 用户本地 Playwright 间接验证（前端调不到 runtime 端点）。DB 断言：无 |
| **F055** Plugin 示例 demo-hello | **P2** | **用户手动** | ✗ | ✗ | `plugins/demo-hello/pom.xml` + `META-INF/pf4j/plugin.properties` + `DemoHelloPlugin extends MeiyetecPluginAdapter`。**未编译成 jar（`plugins/demo-hello/` 无 `target/`）+ `build.bat` 仅 Windows + plugin.properties 完整 5 字段**。**用户手动**：① `cd plugins/demo-hello && build.bat` → 应生成 `target/demo-hello-1.0.0.jar`；② 拷贝到 `${plugins-root}/demo-hello/1.0.0/`；③ 重启后端 → `GET /plugin/runtime/list` → 应有 `demo-hello` 项。**D10 铁律**：若 `mvn clean package` 失败 / `build.bat` 在 Windows PowerShell 报错 → **直接降级 UNVERIFIED，不造证据** |

---

## 四、预期负向证据清单（I017-I025 实证预期）

> 按 Phase 3 静态分析结论，下列负向证据是 Phase 5 必暴露的（验证审计结论准确性）：

| # | 验证目标 | 预期负向证据 | Phase 5 操作 | Issue 关联 |
|---|---|---|---|---|
| **N1** | `plugin_runtime_state` 表不存在 | `TableNotFoundException` 或 `Table 'meiyetec.plugin_runtime_state' doesn't exist` | `mysql -e "DESCRIBE plugin_runtime_state"` | **I017**（F036/F037/F049/F051）|
| **N2** | dashboard `catch (Exception ignored) {}` 吞异常 | `GET /plugin/runtime/dashboard` 返回 `success=true + persistedRecords=0` 但实际是表缺失 | 启动后端 → curl `/plugin/runtime/dashboard` → 看 `persistedRecords` 字段 | **I008 同模式**（F034）+ **I017** 链路 |
| **N3** | F043/F044 Shadow Feature | curl 10 个 `/plugin/runtime/*` 端点全部 200 → **前端无任何消费** | grep `runtime` in `frontend/meiyetec-web/src/api/plugin.ts` → 0 命中 | **I024**（F043 ×6 + F044 ×4）|
| **N4** | 沙箱不拦截反射加载 `service.*` | `Class.forName("com.meiyetec.service.UserService")` 成功 → 沙箱不抛 | demo-hello onStart 内加 `assertCallerAllowed(getClass())` → 预期通过 | **I020**（F042）|
| **N5** | `disablePlugin` 清 `lastError` | `lastError` 字段由 `state=FAILED + errorMsg="..."` → `disable` → 变 `state=DISABLED + errorMsg=null` | ① 先 enable demo-hello → write `lastError="测试"` → ② disable → 查 DB → 应 null | **I022**（F033/F036）|
| **N6** | 幽灵记录 NPE | install 后不 load → 业务入口（反射 `wrapper.getPlugin().hello("x")`）→ `NullPointerException`（wrapper 存在但 Plugin 为 null 或 class 找不到）| ① install `plugin_market_id=1`（不调 load）→ ② `isEnabledForTenant("xxx", tenantId)` → true → ③ 业务入口 → NPE | **I023**（F046/F035）|
| **N7** | F050 配置风险 | `./plugins` 相对路径在 IDE 启动时找不到 → `plugins-root not exists` 警告 | 启动后端 → 看启动日志 → 验证 `./plugins` 路径 | F050 PARTIAL-NON-BLOCKER |
| **N8** | F049 字段差异 | DDL 9 字段 vs entity 8 字段（差 1 字段）| 仔细对比 `migrate_ext_23_plugin_runtime_state.sql:9-29` vs `entity/PluginRuntimeState.java:27-54` | F049/F051（DDL review 项）|

---

## 五、需要用户手动执行的清单（pf4j 相关）

> Cursor 不能跑 JVM 加载真实 JAR / 不能编译插件 → 下列 Feature 在 Phase 5 必须由用户在本地执行。

### 5.1 P0 必跑（核心链路）

| Feature | 用户手动步骤 | 失败降级 |
|---|---|---|
| **F032** Plugin 启动 init + auto-load | ① 启动后端 → 2. 看 `[PLUGIN-RUNTIME]` 日志 → ③ `curl /plugin/runtime/list` → ④ 检查 `auto-load-on-startup=true` 是否触发 | 启动失败 → F032 UNVERIFIED |
| **F033** Plugin 手动生命周期 | ① `curl -X POST /plugin/runtime/demo-hello/load` → ② `enable` → ③ `disable` → ④ `unload` 四步链 | 任一步 500 → 记录异常栈 |
| **F035** Plugin `isEnabledForTenant` | ① 普通租户 token 调跨租户端点 → 403 → ② install 后不 load 调业务入口 → NPE | NPE 不复现 → F035/I023 降级 UNVERIFIED |
| **F036** Plugin 持久化 `recordState` | 触发 load → 后端日志 `TableNotFoundException` | 异常未出现 → F036 UNVERIFIED |
| **F037** Plugin 持久化 `restoreStates` | `curl /plugin/runtime/dashboard` → `persistedRecords=0` 但表缺失 → I008 链路 | `persistedRecords > 0` → I008 不成立 |

### 5.2 P1 必跑（租户 + 沙箱）

| Feature | 用户手动步骤 | 失败降级 |
|---|---|---|
| **F038** Plugin 租户叠加 | install 后不 load → 调 `isEnabledForTenant` → true | NPE 不复现 → F038/I023 UNVERIFIED |
| **F039** Plugin `setTenantEnabled` | `POST /plugin/runtime/tenant/{tid}/{code}/enable` ×3 → 验证 upsert 不重复 | 重复行出现 → F039 PARTIAL-BLOCKER |
| **F041** Plugin `assertPackageAllowed` | demo-hello onStart 调 → 验证白名单 | demo-hello 编译失败 → F041 UNVERIFIED |
| **F042** Plugin `assertCallerAllowed` | demo-hello 内反射加载 `service.UserService` → 沙箱不抛（I020）| 沙箱抛异常 → I020 不成立 |

### 5.3 P2 部分必跑（依赖 F055 demo-hello 编译）

| Feature | 用户手动步骤 | 失败降级 |
|---|---|---|
| **F043** Platform-level API | curl 6 个端点 → 全部 200 | 401/403 → 检查 auth token |
| **F044** Tenant-level API | 普通租户 token 调跨租户 → 403（I018 实证）| 200 → I018 不成立 |
| **F048** Entity 三件套 | `mysql -e "DESCRIBE plugin_market/install/review"` | 三表缺一 → F048 PARTIAL-BLOCKER |
| **F049** Entity PluginRuntimeState | `mysql -e "DESCRIBE plugin_runtime_state"` → 表缺失 | 表存在 → I017 不成立 |
| **F050** 配置生效 | 启动后端 → 验证 `./plugins` 路径警告 | 路径错误 → F050 PARTIAL-BLOCKER |
| **F051** DDL 未应用 | `grep plugin_runtime_state db-snapshot.sql` → 0 命中 | >0 命中 → I017 不成立 |
| **F052-F054** 前端 | Playwright 跑 `frontend/meiyetec-web/e2e/audit/` 脚本 | 脚本失败 → F052-F054 UNVERIFIED |
| **F055** demo-hello 编译 | `cd plugins/demo-hello && build.bat` | 编译失败 → **F055 + F031/F041/F042 全部降级 UNVERIFIED（D10 铁律）** |

---

## 六、Phase 4 → Phase 5 入口守门

| 检查项 | 状态 | 备注 |
|---|---|---|
| 25 Feature Phase 4 计划已落 | ✅ | 本文件 |
| P0/P1/P2 优先级已分布 | ✅ | P0=6 / P1=5 / P2=14 |
| 验证方式已指定（API-only / DB-only / 用户手动 / Static-Only）| ✅ | 每 Feature 1 个主方式 |
| 预期负向证据清单已建（N1-N8）| ✅ | 关联 I017/I020/I022/I023/I024/I008 |
| 用户手动执行清单已建（5.1/5.2/5.3）| ✅ | 含失败降级 UNVERIFIED 策略 |
| D6-D10 决策已落实 | ✅ | Shadow 不动 / I017 不跑 / I018 不改 / I019-I025 进 backlog / D10 pf4j 跑不通降级 UNVERIFIED |
| 不动代码 | ✅ | 仅 Phase 4 计划生成 |
| 不进 Phase 5 | ✅ | 等用户确认本计划 → Phase 5 执行 |
| 跳过 Phase 3.5 | ✅ | Batch 1.2 非重构批次 |
| 受保护页面零风险 | ✅ | Plugin Runtime 与 `/dashboard` / `/mh/visual` / `/mh/consent` 后端接口零交集 |

---

## 七、Phase 4 → Phase 5 衔接说明

> 本文件仅生成 **验证策略**，不执行任何运行时操作。
>
> **Phase 5 启动条件**（按用户决策）：
> 1. 用户确认本计划（P0/P1/P2 分布 + D6-D10 落实）
> 2. 用户启动后端（Spring Boot + MySQL + plugin_runtime_state 表**未创建**——按 I017 决策）
> 3. 用户在本地编译 demo-hello（仅 Windows PowerShell，`build.bat`）
> 4. 用户跑 curl + mysql CLI 验证
> 5. 用户跑 Playwright 脚本（前端 E2E 降级）
>
> **Phase 5 输出物**：
> - `audit/evidence/F031-F055/<slug>/result.md`（每个 Feature 1 份）
> - `audit/runtime/report-5-batch-1.2.md`（汇总 25 Feature Phase 5 结果）
> - Issue Register 更新（I017-I025 实证状态）
>
> **失败降级铁律（D10）**：
> - F055 demo-hello 编译失败 → F031/F041/F042 全部 UNVERIFIED
> - pf4j 启动失败（plugin_runtime_state 表缺失导致连锁）→ F032-F037 全部 UNVERIFIED
> - 任何 Phase 5 操作**不造证据**（不写假 result.md）

---

## 八、附录 — Batch 1.2 vs Batch 1.1 Phase 4 对照

| 维度 | Batch 1.1 (CRM) | Batch 1.2 (Plugin Runtime) |
|---|---|---|
| Feature 数 | 30 | 25 |
| P0 必测 | 7 | 6 |
| P1 | 15 | 5 |
| P2 | 8 | 14 |
| pf4j 加载 | N/A | **Cursor 不能跑** → 用户手动 |
| Frontend E2E | Playwright E2E 降级 | 同 |
| DB 缺表 | `crm_contact_map`（F023）+ `crm_integration` 缺 9 列（I002） | `plugin_runtime_state`（F049）+ 9 vs 8 字段差异（F051）|
| 核心决策 | D5 不跑 migration | **D7 不跑 migration**（同模式）|
| SoT 漂移 | I001 HubSpot/Zoho 误标"未实现" | **I018 assertTenantAccess 误标"无校验"** |
| 合作式限制 | N/A | **I020 沙箱不拦截 service.\*** |
| 副作用 | N/A | **I022 disable 清 lastError** |
| 幽灵记录 | N/A | **I023 install 不 load → NPE** |
| 命名异味 | I004 Service as Controller | **I019 PluginService as Controller**（同模式）|

> **沿用经验**：
> - Batch 1.1 D5「不跑 migration 保留 PARTIAL-BLOCKER」 → Batch 1.2 D7 同模式
> - Batch 1.1 I001「保留 CONFLICT 不动 .mdc」 → Batch 1.2 D8 同模式
> - Batch 1.1 04-script false Shadow → Batch 1.2 已用 04 重跑验证真 Shadow 数 = 10
> - Batch 1.1 受保护页面零风险 → Batch 1.2 同