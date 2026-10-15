# Pre-flight Check（Batch 1.2 — Phase -1 产出）

> 适用规则：`@project-audit.mdc` 规则 16（Phase -1 必须先于 Phase 0）
> 模板：`@CURSOR_AUDIT_GUIDE.md`「二点五」章节
> 创建时间：2026-09-13 18:12 (UTC+8)
> 适用批次：**v3 第二批 / Batch 1.2 = EXT-23 Plugin Runtime**
> 上一批次产出：`audit/batches.md` §Batch 1.2 + `audit/issues/issue-register.md` §十六（Q1-Q4 决策）
> 上一阶段产出：`audit/baseline/{commit.txt, env.md, db-snapshot.sql}`
> 用户决策来源：`audit/issues/issue-register.md` §十六（2026-09-13 18:12 决策时间）

---

## 一、MCP 状态

| MCP | 状态 | 来源 |
|---|---|---|
| **MySQL MCP** | ✅ **已配置** | Batch 1.1 用户决策 B2 阶段确认完成（"配置 MySQL MCP，已完成"） |
| **Playwright MCP** | ❌ **未配置**（沿用 Batch 1.1 决策）| Batch 1.1 用户决策 1（Phase 5 E2E 降级为静态抓 HTML + curl API + Vitest）|

**降级方案（沿用）**：

| 工具/能力 | 降级方案 |
|---|---|
| **MySQL MCP** | ✅ 正常使用 |
| **Playwright MCP** | Phase 5 降级为：① 生成 Playwright 脚本供用户手动执行；② 用户手动贴回 evidence；③ 静态抓 HTML + curl API + Vitest 替代 |
| **Redis** | ❌ 未验证（沿用决策）— Phase 5/6 暴露运行时 NPE 风险 |

---

## 二、PRD 覆盖（EXT-23 部分）

### 2.1 PRD 整体覆盖（沿用 Batch 1.1 §二）

| 项 | 值 |
|---|---|
| **路径** | `docs/PRD-美业SaaS系统功能大全.md`（14,190 bytes, v1.0, 2026-07-31）|
| **覆盖范围** | 12 标准模块（M01-M12）+ 6 进阶模块（M13-M18），P0/P1/P2/P3 分级 |
| **Plugin Runtime 相关模块** | **M13-M18「进阶模块」中未明确标注 EXT-23 Plugin Runtime**；Plugin Runtime 属于**平台扩展机制**，非业务模块 → 不在 PRD 主线 |

### 2.2 EXT-23 Plugin Runtime 覆盖度

| PRD 模块 | EXT-23 覆盖？ | 备注 |
|---|---|---|
| M01-M12（标准）| ❌ | 标准业务无依赖 |
| M13-M18（进阶）| ⚠️ 隐含 | Plugin Runtime 是 SaaS 扩展点（智能排班 / 动态定价 / 客户 360 / 自动化营销 / 视觉档案 / 电子知情同意 都可作为 plugin 实现），但 PRD 未明确指定 |
| **Plugin Runtime 独立模块** | ❌ PRD 未覆盖 | 平台扩展机制，无独立 PRD 段 |

**Phase 1 影响**：
- EXT-23 评估**不依赖 PRD 模块清单**（属于平台层）
- Operational Notes（`.cursor/rules/ext-23-plugin-runtime.mdc`）作为 SoT 替代 PRD（沿用规则 11）
- Feature Inventory 直接以 Operational Notes §1 + §3 为骨架

### 2.3 缺失文档（沿用 §preflight 二）

| 缺失内容 | 实际位置 | 影响 Phase |
|---|---|---|
| **验收标准（L0-L5 等级定义）** | `docs/PROJECT_AUDIT_PLAYBOOK.md` 完整融合 | Phase 7 需对照 |
| **Plugin Runtime 接口 / 沙箱契约** | `.cursor/rules/ext-23-plugin-runtime.mdc`（Operational Notes）| Phase 2 / 3 |
| **Plugin 安全策略白/黑名单** | Operational Notes §0.5c（已完整）| Phase 3 |
| **插件开发指南 / SDK** | Operational Notes §4（已完整）| Phase 5 |
| **NFR（性能 / 多租户隔离 / 审计）** | **未找到独立 NFR 文档** | Phase 8 需用户补充 |

---

## 三、BLOCKER 阈值（沿用 Batch 1.1）

> 沿用 Batch 1.1 §preflight 三「BLOCKER 判定阈值」，**全部沿用，无需用户重新确认**。

### 3.1 默认 BLOCKER 判定

| 情况 | 是否 BLOCKER（沿用）| 备注 |
|---|---|---|
| Mock 未清理（含 fallback 链路）| ✅ **是** | 部分场景豁免 |
| 迁移脚本未在生产跑过 | ✅ **是** | dev 环境跑过也算"通过" |
| webhook 端点缺失（已知平台未对接）| ✅ **是** | 是否允许"已规划/已建空壳"占位 |
| 未跑 E2E 的核心业务 | ✅ **是** | "核心业务"边界由谁定义 |
| **Plugin Runtime 新增检查项** | — | — |
| **Plugin 未在沙箱白名单内** | ✅ **是**（继承 EXT-23 §0.5c 黑名单）| 合作式沙箱，反射加载仍可能绕过 |
| **`plugin_runtime_state` 表缺失** | ✅ **是**（EXT-23 §0.5a 持久化必需）| Phase 1 触达 |
| **PluginRuntimeController 无 `dataScope` 校验** | ✅ **是**（EXT-23 §0.7 + §5）| 跨租户访问，Phase 1 触达 |

### 3.2 额外 BLOCKER 项（建议加）

| 项 | 默认 | 备注 |
|---|---|---|
| **Mock 平台在无凭据时 500**（I012 关联）| ✅ **是** | Batch 1.1 F015 已实证 |
| **`plugin_install` 表有数据但 `plugin_runtime_state` 缺失** | ✅ **是** | 业务侧 `isEnabledForTenant` 返回 true 但持久化写失败 |
| **租户级启停 `dataScope="ALL"` 缺失** | ✅ **是** | Phase 1 触达 |
| **`disablePlugin` 副作用覆盖 FAILED 错误** | ⚠️ 观察 | EXT-23 §5 已知问题 |
| **`plugin_install` 幽灵记录**（marketplace 安装但 runtime 未加载）| ⚠️ 观察 | EXT-23 §5 已知问题，isEnabledForTenant 兜底默认启用 → 业务侧 NPE |

---

## 四、git 状态

### 4.1 当前 HEAD

| 项 | 值 |
|---|---|
| **HEAD** | `12a97ea audit(batch-1.1-phase2): EXT-24 CRM 集成 Full-Stack Traceability Matrix` |
| **起点 commit**（Batch 1.1 终点）| `12a97ea57a98d268c864fc7c010b82b305d7bb93` |
| **沿用 baseline commit** | `656caa72341c96d1b2e703dde993603dda9eebe1`（Batch 1.1 起点的 `audit/baseline/commit.txt`）|

### 4.2 当前 working tree（`git status --short`）

```
M  .gitignore
?? audit/evidence/F001-F030/**    (Batch 1.1 30 Feature evidence)
?? audit/issues/I006-I013,I016** (Batch 1.1 Issue files)
?? audit/reports/static-gap-report.md
?? audit/runtime/{plan,report-5A,report-5B,report-5C}.md
?? audit/scripts/e2e/{F007,F024-F028}*.spec.ts
?? audit/scripts/e2e/playwright.config.ts
?? audit/static-gap/{01-08}*.txt
?? backend/src/main/java/com/meiyetec/controller/{MktChannel,MktConversion,MktJourney,MktTemplate,MktTouchTask}Controller.java (MKT 重构增量)
?? backend/src/main/java/com/meiyetec/service/ReportExportFileCleanupService.java
?? backend/src/main/resources/application.yml (M 改动)
?? backend/src/main/resources/db/sql/migrate_mkt_002_*.sql
?? backend/logs/{domestic-pay,meiyetec-error,meiyetec}.log
?? frontend/meiyetec-web/e2e/{audit/*,crm-integration,mh-routes}.spec.ts
?? frontend/meiyetec-web/playwright.audit.config.ts
?? frontend/meiyetec-web/node_modules/ (gitignored)
?? frontend/meiyetec-web/src/api/*.ts (M 改动或新增)
?? frontend/meiyetec-web/src/views/** (M 改动)
?? frontend/meiyetec-web/test-results/ (gitignored)
?? 项目开发完成度与最终验收体系（最终融合版） (中文文件名 root)
M  audit/issues/issue-register.md (Batch 1.1 → Batch 1.2 决策已合并，§十六 Q1-Q4 + I008/I011/I012 P1 升级 + Fix Phase 列)
```

### 4.3 working tree 统计

| 状态 | 数量（估） | 备注 |
|---|---|---|
| **Modified** | ~10 个（含 `application.yml` / `issue-register.md` / `project-audit.mdc` 等）| 沿用 Batch 1.1 状态 |
| **Untracked** | ~80 个（30 Feature evidence + Issue files + MKT 重构 + 日志）| 沿用 Batch 1.1 状态 |
| **Deleted** | — | — |

### 4.4 待 commit / stash 决策

| 选项 | 内容 | 推荐 |
|---|---|---|
| **A** | 直接 commit（baseline + preflight.md + 25+ 改动一次性入库）| ❌ 不推荐（chunk 太大）|
| **B** | 拆 commit（baseline 单独 + MKT 重构 + EXT-19/22/23/24 分批）| ✅ **推荐**（按模块分批审计）|
| **C** | 再 stash 一次（Phase -1 完成后保持 working tree clean）| ⚠️ Phase 1 期间可能需 stash pop 调整 |
| **D** | 不 commit（保持 dirty 状态直接进 Phase 0）| ❌ 不推荐（Phase 0 scope-freeze 需要 commit 锚点）|

→ **沿用 Batch 1.1 决策**：Phase -1 报告完成后**等用户决策 A/B/C/D**。

### 4.5 Phase 0 freeze commit 候选

| 候选 | 描述 | 何时 |
|---|---|---|
| **冻结 Batch 1.1 commit** | 在 `12a97ea` 后追加 `audit(freeze): Batch 1.1 完结 + Batch 1.2 启动` | Phase 0 启动前 |
| **冻结 Batch 1.2 commit** | 包含 `preflight-batch-1.2.md` + `scope-freeze-batch-1.2.md` | Phase 1 启动前 |

---

## 五、受保护页面（沿用 Batch 1.1）

> 来源：`.cursor/rules/no-touch-verified-pages.mdc`（用户实测通过）

| # | 路由 | 前端文件 | 后端接口 | 状态 |
|---|---|---|---|---|
| 1 | `/dashboard` | `frontend/meiyetec-web/src/views/dashboard/**` | （待 Phase 2 列出）| ✅ 已验证 |
| 2 | `/mh/visual` | `frontend/meiyetec-web/src/views/mh/visual.vue` | （待 Phase 2 列出）| ✅ 已验证 |
| 3 | `/mh/consent` | `frontend/meiyetec-web/src/views/mh/consent.vue` | （待 Phase 2 列出）| ✅ 已验证 |

### 5.1 影响（沿用 Batch 1.1）

- Phase 3（Static Gap Audit）：仅列可疑点，**不修改**
- Phase 5（Runtime Verification）：仅验证运行，**不动代码**
- Phase 6（DB Verification）：仅读 schema/数据，**不调整**
- 即使发现 Bug / 风格不一致 / dead code → 仅列入 Issue Register

### 5.2 例外（沿用 Batch 1.1）

- 用户明确说"修 X"且 X 指向这 3 个页面 → 可以动手
- 用户明确说"还原/回退"对它们的某次修改 → 可以动手

### 5.3 EXT-23 与受保护页面的关系

| 检查项 | 结果 |
|---|---|
| **Plugin Runtime 是否影响 `/dashboard` 数据？**| ❌ 无直接影响（Plugin 是独立扩展点，Plugin 加载失败不影响 dashboard）|
| **Plugin Runtime 是否影响 `/mh/visual` / `/mh/consent`？**| ❌ 无直接影响 |
| **PluginRuntimeController 是否触及受保护页面的后端接口？**| ❌ 不触及（PluginRuntimeController 是 `/plugin/runtime/*`，与 `/dashboard` / `/mh/*` 无交集）|

→ **EXT-23 审计期间受保护页面零风险**。

---

## 六、守门脚本（g1-g8 命名约定存在 / 脚本未实现）

### 6.1 8 个守门脚本命名（沿用 `audit/README.md` §1）

> **当前状态**：`audit/scripts/` 目录**已创建但为空**，8 个守门脚本（g1-g8）**未实现**（沿用 preflight §6）。

| 编号 | 脚本名（预期）| 当前状态 | 阻塞 Phase 0？ |
|---|---|---|---|
| **g1** | `g1-scope.sh` / `.ps1` | ❌ **未实现** | 否（按 Batch 1.1 决策 3：Phase 1 期间补）|
| **g2** | `g2-traceability.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g3** | `g3-issue-format.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g4** | `g4-evidence-check.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g5** | `g5-runtime-contract.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g6** | `g6-no-touch-pages.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g7** | `g7-blocker-rules.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g8** | `g8-pre-deploy.sh` / `.ps1` | ❌ **未实现** | 否 |

### 6.2 EXT-23 期间是否补齐？

| 建议 | 理由 |
|---|---|
| **Phase 0 期间补齐 g1-g8** | EXT-23 是平台核心扩展机制，Phase 3（Issue Register）+ Phase 5（Runtime Verification）依赖守门脚本 |
| **保持沿用（Phase 1-2 期间补）** | Batch 1.1 已确认延后，本批次沿用 |

→ **本批次沿用 Batch 1.1 决策**：Phase 1-2 期间补，**不阻塞 Phase 0**。

### 6.3 g1-g8 命名约定的扩展（建议）

| 编号 | 扩展脚本（EXT-23 特定，可选）| 用途 |
|---|---|---|
| **g9** | `g9-plugin-sandbox-check.sh` | 检查插件 jar 是否在沙箱黑名单内 |
| **g10** | `g10-plugin-tenant-isolation.sh` | 检查 PluginRuntimeController 是否有 `dataScope` 校验 |
| **g11** | `g11-plugin-runtime-state-table.sh` | 检查 `plugin_runtime_state` 表是否存在 |

→ **可选**：Phase 1 期间根据需要新增 EXT-23 守门脚本（不强制）。

### 6.4 风险

- 规则 15：自动化守门脚本每次 PR 必须跑 → **当前 CI 不守门 v3 体系规则**
- PR 可绕过审计规则合并 → **Batch 1.2 Phase 0 仍需确认守门脚本缺失是否算 BLOCKER**

---

## 七、当前批次：EXT-23 Plugin Runtime

### 7.1 批次基本信息

| 项 | 值 |
|---|---|
| **Batch ID** | **Batch 1.2** |
| **模块** | **EXT-23 Plugin Runtime** |
| **来源** | `.cursor/rules/ext-23-plugin-runtime.mdc`（Operational Notes §0-§6）|
| **起点时间** | 2026-09-13 18:12 (UTC+8)（用户决策 Q2）|
| **Phase -1 时间** | 2026-09-13 18:12 (UTC+8)（本文件落档）|
| **起点 commit** | `12a97ea57a98d268c864fc7c010b82b305d7bb93`（沿用 Batch 1.1 终点，Phase 0 重新 freeze）|

### 7.2 模块范围（来自 Operational Notes §1）

| 维度 | 内容 |
|---|---|
| **架构分层** | 业务调用 → `PluginRuntimeService.isEnabledForTenant` → `PluginTenantService` → pf4j `PluginManager` → `PluginSecurityPolicy` → 插件 `MeiyetecPlugin.onStart` |
| **核心依赖** | pf4j 3.10.0（本地 jar）+ `com.meiyetec.plugin` SPI 体系 |
| **关键文件** | `backend/src/main/java/com/meiyetec/plugin/{MeiyetecPlugin,MeiyetecPluginAdapter,PluginRuntimeService,PluginPersistenceService,PluginTenantService,PluginSecurityPolicy,PluginRuntimeInfo,PluginRuntimeContext}.java` + `controller/PluginRuntimeController.java` + `entity/{PluginRuntimeState,PluginInstall,PluginMarket,PluginReview}.java` + `mapper/PluginRuntimeStateMapper.java` |
| **插件示例** | `plugins/demo-hello/{pom.xml,DemoHelloPlugin.java,META-INF/pf4j/plugin.properties}` |
| **前端** | `frontend/meiyetec-web/src/views/plugin/index.vue`（只有 marketplace + 已安装 tab）+ `api/plugin.ts` |
| **配置** | `application.yml` line 183-187：`plugin.runtime.{enabled,plugins-root,auto-load-on-startup}` |

### 7.3 Feature 估算（待 Phase 1 落实）

| 子模块 | 估算 Feature 数 | 来源 |
|---|---|---|
| Plugin Runtime Service 生命周期 | ~5 | Operational Notes §0.3 |
| PluginPersistenceService 持久化 | ~2 | §0.5a |
| PluginTenantService 租户级 | ~3 | §0.5b |
| PluginSecurityPolicy 沙箱 | ~3 | §0.5c |
| Controller REST API | ~10 | §3 endpoints |
| 配置 + 启动期 | ~2 | §0.6 |
| **估算合计** | **~20-25 Features** | Phase 1 落地 |

### 7.4 已知 EXT-23 风险（Operational Notes §5 摘录）

| 风险 | 严重度 | 关联 Batch 1.1 经验 |
|---|---|---|
| **沙箱是合作式**（`assertCallerAllowed` 仅做轻量探测，**反射加载 `com.meiyetec.service.*` 实际不拦截**）| ⚠️ 高 | — |
| **租户隔离缺失**（`PluginRuntimeController` 所有端点无 `dataScope` 校验，**任何租户能调任何 tenantId 的接口**）| 🔴 高 | — |
| **`pluginId` 约定漂移**（`resolvePluginId` 在插件未注册时返回 `pluginCode + "@unknown"`）| 🟡 中 | — |
| **目录选择策略简陋**（字典序倒序挑最大，**非真正的 semver 比较**）| 🟡 中 | — |
| **多版本并存冲突**（`loadPlugin` 加载多版本会冲突）| 🟡 中 | — |
| **`plugin_install` 幽灵记录**（marketplace 安装只插 `plugin_install`，**runtime 未加载时 `isEnabledForTenant` 仍返回 STARTED && true** → 业务侧可能调到一个不存在的插件入口 NPE）| 🔴 高 | 关联 I012 |
| **`disablePlugin` 副作用**（禁用成功写 `recordState(... state=DISABLED)`，**会把上一次的 FAILED 错误也清掉**）| 🟡 中 | — |
| **前端无 runtime tab**（`plugin/index.vue` 只有「市场」+「已安装」，看不到 pf4j 状态机）| 🟡 中 | 关联 I006 |
| **`loadPlugin` 不写 `autoLoaded=true`**（`PluginRuntimeInfo.autoLoaded` 字段定义了但实际未填充）| 🟢 低 | — |
| **`pluginId` 与 `pluginCode` 强约定**（`plugin.id` 建议与 `pluginCode` 保持一致，否则 `resolvePluginId` 会通过遍历匹配）| 🟡 中 | — |

### 7.5 与 Batch 1.1 经验关联

| EXT-24 经验（Batch 1.1）| EXT-23 预期重演？ |
|---|---|
| **I008（Fake Success / catch 吞异常）** | ⚠️ **PluginRuntimeService.loadAllFromDisk 的 try/catch 模式 + 单版本目录失败 try/catch 不影响其他**（Operational Notes §0.3）→ Phase 1 重点检查 |
| **I011（Fake Failure / select-after-write）** | ⚠️ `recordState` 写失败但 pf4j 状态机已变（Operational Notes §0.5a 写「异常仅 warn 不抛」）→ 关联 audit 失真 |
| **I012（Bean 条件冲突 / `@ConditionalOnMissingBean` 误用）** | ⚠️ `PluginRuntimeService` 是 `@PostConstruct` 懒加载 + `@Component`，需要检查是否存在类似 `@ConditionalOnMissingBean` 误用 |
| **I013（dead code）**| ⚠️ `PluginRuntimeInfo.autoLoaded` 字段定义了但实际未填充 → dead code |
| **I001（SoT 文档漂移）**| ⚠️ Operational Notes §0.5c 沙箱黑名单 vs `service.impl.` 实际可被反射加载 |

---

## 八、已知约束

### 8.1 `plugin_runtime_state` 表缺失（**关键约束**）

| 维度 | 详情 |
|---|---|
| **缺失表** | `plugin_runtime_state` |
| **所属 EXT-* ** | EXT-23 |
| **Operational Notes 引用** | §0.5a `PluginPersistenceService` 持久化表 |
| **代码状态** | ✅ 已存在（`entity/PluginRuntimeState.java` + `mapper/PluginRuntimeStateMapper.java`）|
| **DB snapshot 状态** | ❌ **不存在**（`audit/baseline/db-snapshot.sql` line 1-9000 全文 grep 0 匹配）|
| **推测原因** | 实体类可能未建表 / Flyway migration 未写 / 运行时 NPE |
| **Phase 1 影响** | 触达 → 标 `MISSING`（按 §preflight 8.1 决策）|
| **Phase 5 影响** | `PluginPersistenceService.recordState` 写表 → `TableNotFoundException` → try/catch 仅 warn（Operational Notes §0.5a）→ 与 Batch 1.1 I008 类似的 Fake Success 链路 |
| **决策** | ❌ **本批次不修**（沿用规则 6 + Q3）→ 标 Issue，Fix Phase = Post-Audit |

**关联验证清单**（Phase 1 期间）：

- [ ] 实体类 `PluginRuntimeState.java` 字段列表 vs Operational Notes §0.5a
- [ ] Mapper XML / Annotation 完整性
- [ ] 建表 SQL 是否存在（Flyway `V*__plugin_runtime_state.sql` / `db/sql/migrate_ext_23_*.sql`）
- [ ] 是否在 `migrate_ext_15_crm_real.sql` 等历史迁移脚本中创建

### 8.2 EXT-23 Operational Notes（`.cursor/rules/ext-23-plugin-runtime.mdc`）

| 维度 | 详情 |
|---|---|
| **文件路径** | `.cursor/rules/ext-23-plugin-runtime.mdc` |
| **当前状态** | ✅ **已加载**（preflight §7.2 + §8.8 已确认）|
| **内容覆盖** | §0 关键约束（7 节）/ §1 Touchpoints / §2 Re-confirmation Checklist / §3 Endpoints / §4 新增插件 checklist / §5 已知问题 / §6 业务侧调用模板 |
| **SoT 优先级** | 最高（规则 11：审计结论与 EXT-* 冲突时以 EXT-* 为准并标 ISSUE）|
| **使用方式** | Phase 1（Feature Inventory）+ Phase 2（Traceability）+ Phase 3（Issue 登记）全部以 Operational Notes 为骨架 |

**Phase 0 期间确认**：

- [ ] Operational Notes 与最新代码版本一致（已读 Operational Notes §0.5c 沙箱黑名单，对比 `PluginSecurityPolicy.java` 实际常量）
- [ ] Operational Notes §3 endpoints 列表与 `PluginRuntimeController.java` 实际路由一致
- [ ] Operational Notes §5 已知问题列表与代码状态一致（重点：`@PostConstruct` 懒加载是否仍生效、`disablePlugin` 副作用是否仍存在）

### 8.3 与 EXT-24 独立性检查

| 检查项 | 结果 |
|---|---|
| **代码包共享** | ❌ 无共享 |
| **DB 表共享** | ❌ 无共享 |
| **配置共享** | ❌ 无共享 |
| **业务依赖** | ❌ EXT-24 不依赖 EXT-23；EXT-23 不依赖 EXT-24 |
| **EXT-15 依赖** | ❌ EXT-23 不依赖 EXT-15 |
| **风险传染** | ⚠️ EXT-24 的 I008（catch 吞）/ I012（Bean 条件冲突）模式可能再犯（详见 §7.5）|
| **审计可分离性** | ✅ 完全可分离（独立模块，独立 Feature Inventory，独立 Issue Register）|

### 8.4 EXT-23 Operational Notes 已加载的 §0.5c 沙箱（关键约束）

#### 8.4.1 白名单 `HOST_PUBLIC_PACKAGES`

| 包前缀 | 内容 |
|---|---|
| `com.meiyetec.plugin.` | 业务 SPI 入口 |
| `util.` | 通用工具 |
| `exception.` | 异常基类 |

#### 8.4.2 黑名单 `HOST_FORBIDDEN_PACKAGES`（**优先级高**）

| 包前缀 | 内容 |
|---|---|
| `com.meiyetec.service.internal.` | 服务内部实现 |
| `service.impl.` | 服务具体实现 |
| `security.` | 安全模块 |
| `mapper.` | 数据访问层 |
| `config.` | 配置层 |
| `plugin.PluginRuntimeService` | 宿主运行时入口（防递归）|
| `plugin.PersistenceService` | 持久化服务 |
| `plugin.TenantService` | 租户服务 |

#### 8.4.3 黑名单 `FORBIDDEN_JDK_PACKAGES`

| 包前缀 | 内容 |
|---|---|
| `java.lang.reflect.` | 反射 |
| `Runtime` / `Process` | 命令执行 |
| `URLClassLoader` | 自定义类加载 |
| `sun.misc.` / `sun.reflect.` / `jdk.internal.` | JDK 内部 |
| `java.io.FileSystem` / `java.nio.file.Files` | 文件系统 |

#### 8.4.4 合作式沙箱风险（Phase 1 重点）

- ⚠️ **`assertCallerAllowed` 仅做轻量探测**（`Class.forName("com.meiyetec.service.internal.NonExistent", false, cl)`），**反射加载 `com.meiyetec.service.*`（非 internal）实际不拦截**
- Phase 1 期间需验证：插件 jar 中是否引用 `com.meiyetec.service.*`（非 internal）类

### 8.5 Plugin 启动期配置（Operational Notes §0.6）

```yaml
plugin:
  runtime:
    enabled: ${PLUGIN_RUNTIME_ENABLED:true}    # 总开关；false 时仍暴露 REST 元数据
    plugins-root: ${PLUGIN_PLUGINS_ROOT:./plugins}   # 生产建议绝对路径
    auto-load-on-startup: ${PLUGIN_AUTOLOAD:true}    # 启动时自动 load+enable
```

**Phase 1 期间验证**：

- [ ] `application.yml` line 183-187 是否完整
- [ ] `plugin.runtime.plugins-root` 当前值（默认 `./plugins`）→ **生产建议绝对路径**（Operational Notes §0.6 警告）
- [ ] `plugin.runtime.auto-load-on-startup` 当前值（默认 `true`）→ **生产首启建议关闭**（Operational Notes §2 Re-confirmation）

### 8.6 业务侧 `/plugin/*` ≠ 运行时 `/plugin/runtime/*`（关键区分）

| 路径 | 职责 | 是否触达 pf4j |
|---|---|---|
| `POST /plugin/market` `/market/list` `/market/{id}` | 插件市场 CRUD（`plugin_market` 表）| ❌ |
| `POST /plugin/install` `/install/{id}/uninstall` `/install/list` | 安装记录（`plugin_install` 表）| ❌ |
| `POST /plugin/review` `/review/list/{pluginId}` | 评价（`plugin_review` 表）| ❌ |
| `GET /plugin/runtime/list` `/dashboard` | pf4j 状态看板 | ✅ |
| `POST /plugin/runtime/{code}/load\|enable\|disable\|unload` | pf4j 生命周期 | ✅ |
| `GET /plugin/runtime/tenant/{tid}/list` | 租户级视图 | ✅ |
| `POST /plugin/runtime/tenant/{tid}/{code}/enable\|disable` | 租户级启停 | ✅ |
| `GET /plugin/runtime/persisted?tenantId=` | 持久化状态查询 | ❌（只读 DB）|

**关键**：前端「安装」按钮只插 `plugin_install` 表，**不触发 runtime load**。要让插件真正能调用，需另调 `/plugin/runtime/{code}/load`。

### 8.7 受保护页面零风险（沿用 §五）

- Plugin Runtime 审计期间**不触达**受保护页面（`/dashboard` / `/mh/visual` / `/mh/consent`）
- 见 §五 5.3

### 8.8 历史审计沉淀不在 v3 目录（沿用 Batch 1.1）

- `docs/audit/` 25+ 个 `audit_*.txt` + `BEAUTY_SAAS/` 子目录（28+ 报告）
- 不迁移（按 `audit/README.md` §2）
- Phase 7 引用时使用相对路径 `../docs/audit/BEAUTY_SAAS/19_REPAIR_ROADMAP_V3.md`

---

## 九、Phase -1 准入确认（自检）

| 检查项 | 结果 |
|---|---|
| Batch 1.2 = EXT-23 已决策（Q2）| ✅ 用户决策 2026-09-13 18:12 |
| MCP 状态已确认 | ✅ MySQL ✅ / Playwright ❌（沿用降级）|
| PRD 覆盖已评估 | ✅ EXT-23 不在 PRD 主线；以 Operational Notes 为 SoT |
| BLOCKER 阈值已沿用 | ✅ Batch 1.1 §preflight 三 |
| git 状态已快照 | ✅ HEAD `12a97ea` / dirty 沿用 Batch 1.1 |
| 受保护页面已列出 | ✅ 沿用 `/dashboard` / `/mh/visual` / `/mh/consent` |
| 守门脚本状态已确认 | ✅ g1-g8 命名约定存在，脚本未实现 |
| 当前批次范围已明确 | ✅ EXT-23 Plugin Runtime |
| 已知约束已记录 | ✅ `plugin_runtime_state` 表缺失 + Operational Notes + EXT-24 独立 + 沙箱白/黑名单 + 配置 |

✅ **Phase -1 准入条件全部满足。可以进入 Phase 0（scope-freeze-batch-1.2.md）。**

---

## 十、待用户决策清单（不阻塞 Phase 0，但建议在 Phase 0 前回复）

| # | 决策项 | 默认 | 影响 |
|---|---|---|---|
| 1 | Phase -1 报告完成后 commit 策略（A/B/C/D）| 待回复 | git history |
| 2 | 是否需要 Phase 0 期间补齐 g1-g8 守门脚本？| 沿用 Batch 1.1：Phase 1-2 期间补 | 不阻塞 Phase 0 |
| 3 | 是否需要在 Phase 1 之前补齐 `plugin_runtime_state` 表 DDL？| ❌ 本批次不修（Q3 决策）| Phase 1 标 MISSING Issue |
| 4 | Plugin Runtime Service 懒加载行为是否需要重新评估？| ❌ 不评估（Operational Notes 已定义）| Phase 1 验证懒加载是否仍生效 |
| 5 | 受保护页面（/dashboard, /mh/visual, /mh/consent）后端接口清单由谁提供？| Phase 2 静态扫描时列出 | 否 |
| 6 | Plugin 沙箱合作式是否需要在本批次升级为字节码增强（agent）或 OSGi？| ❌ 本批次不评估 | Post-Audit backlog |
| 7 | Plugin 目录 `./plugins` 是否在 Phase 0 改为绝对路径？| ❌ 沿用默认（生产部署时再改）| Phase 5 验证加载 |

---

## 十一、本文件状态

| 项 | 值 |
|---|---|
| **状态** | 已落档（Phase -1 产出）|
| **变更控制** | Phase 0 启动后冻结；Phase 1 期间如发现新约束追加新段（§十二+）|
| **下一阶段产出位置** | `audit/baseline/scope-freeze-batch-1.2.md`（Phase 0）|
| **下一阶段 Prompt 模板** | 见 `在 Cursor 中落地这套验收体系的操作指南` §四「Phase 0」|

---

**Batch 1.2 Phase -1 完结。EXT-23 Plugin Runtime 范围已冻结 + 已知约束已记录。⏸ 等用户确认 → 进入 Phase 0（scope-freeze-batch-1.2.md）。**