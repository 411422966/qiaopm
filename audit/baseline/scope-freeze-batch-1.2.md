# Scope Freeze — Batch 1.2（Phase 0 产出）

> 来源：`@project-audit.mdc` + `docs/PROJECT_AUDIT_PLAYBOOK.md` §十「Phase 0 — Audit Rules / Scope Freeze」
> 创建时间：2026-09-13 18:26 (UTC+8)
> 适用批次：**v3 第二批 / Batch 1.2 — EXT-23 Plugin Runtime**
> 上一阶段产出：`audit/baseline/preflight-batch-1.2.md`（Phase -1）
> 本阶段 freeze commit：`96b00025783ed71cbafba5cf450cee7275ef2b8d`（`audit(batch-1.2): EXT-23 Plugin Runtime preflight` — 包含 preflight-batch-1.2.md + issue-register.md + batches.md；**scope-freeze-batch-1.2.md 本身建议另 commit**，本文件尚未入库）
> 模板链路：`preflight-batch-1.2.md` §八 → 本文件 §九（引用，不复制）
> Phase 0 freeze 后进入 Phase 1（Feature Inventory）

---

## 〇、文档说明

Phase 0 **不列 Feature**（那是 Phase 1）。Phase 0 只做四件事：

1. 冻结规则（Feature ID / L0-L5 / Result 子标签 / Evidence 目录 / SoT 引用）
2. 冻结范围（本批次涉及哪些 EXT-* + 模块）
3. 确认能力边界（Cursor 能不能做什么）
4. 引用 preflight-batch-1.2.md 的已知约束（不复制内容，仅引用）

---

## 一、项目定位（QiaoPM 美业 SaaS — Batch 1.2 视角）

| 项 | 值 |
|---|---|
| **项目名** | QiaoPM（美业 SaaS 消费收银会员管理系统）|
| **代码仓库** | `D:\QiaoPM`（workspace root）|
| **业务范围** | 美容院 / 美发店 / 美甲美睫 / SPA / 会所 / 医美 |
| **核心特征** | 高频预约、套餐/次卡依赖、会员复购驱动、私域运营强 |
| **PRD** | `docs/PRD-美业SaaS系统功能大全.md`（v1.0, 14,190 B, 2026-07-31）|
| **标准模块** | M01-M12（预约/收银/会员/套餐/储值/项目/库存/员工/营销/评价/报表/连锁）|
| **进阶模块** | M13-M18（智能排班/动态定价/客户360/自动化营销/视觉档案/电子知情同意）|
| **本批次定位** | **EXT-23 Plugin Runtime**（**平台扩展机制**，不属于任何业务模块，是 SaaS 核心扩展点）|
| **本批次与业务模块关系** | Plugin 是 M13-M18 进阶模块的潜在实现方式（智能排班 / 动态定价 / 客户 360 / 自动化营销 / 视觉档案 / 电子知情同意 均可作为 plugin 实现），但 PRD 未明确指定 |
| **优先级分布（沿用 v3）** | P0 × 5 / P1 × 6 / P2 × 6 / P3 × 1 |
| **支付通道（沿用 v3）** | 微信 / 支付宝 / 银联云闪付 / 数字人民币 2.0 / 花呗 / Stripe / Square / PayPal / Gift Card / BNPL |

**Batch 1.2 Phase 7 验收范围**：以 EXT-23 Operational Notes（`.cursor/rules/ext-23-plugin-runtime.mdc`）为 SoT，PRD 不直接覆盖；Feature 清单由 Phase 1 输出，本阶段不预判。

---

## 二、验收范围（Batch 1.2）

### 2.1 涉及模块

| EXT-* | 模块 | 主要改动 / 验证范围 |
|---|---|---|
| **EXT-23** | Plugin Runtime（pf4j 3.10.0）| `MeiyetecPlugin` SPI + `MeiyetecPluginAdapter` + `PluginRuntimeService`（懒加载 + DCL 单例）+ `PluginPersistenceService`（`plugin_runtime_state` 表 — **表缺失**）+ `PluginTenantService`（叠加 pf4j）+ `PluginSecurityPolicy`（合作式沙箱）|

### 2.2 不在本批次范围（明确排除）

| 类别 | 模块 | 排除原因 |
|---|---|---|
| **受保护页面** | `/dashboard`, `/mh/visual`, `/mh/consent` | 用户已实测通过，按 `.cursor/rules/no-touch-verified-pages.mdc` 只观察不修复 |
| **EXT-24 CRM 集成** | `crm_integration` / `crm_contact_map` / `crm_sync_log` | Batch 1.1 已完结；Batch 1.2 不触达（Q1 决策不跑 EXT-15 迁移）|
| **EXT-19 多语言预约确认** | `overseas_booking` / i18n template | 不在本批次；EXT-23 评估不依赖 EXT-19 |
| **EXT-22 Google Maps + 有赞** | `google_maps_config` / `ecommerce_platform` | 不在本批次；EXT-23 评估不依赖 EXT-22 |
| **历史模块** | M01-M18 中除上述外的所有模块 | 不在本批次；待后续批次（v3 第三批起）评估 |
| **基础设施** | Redis / MySQL 配置 / CI 流水线 | 基础设施层不在本批次审计 |

### 2.3 EXT-23 详细验证范围

| 层级 | 文件 | 验证范围 |
|---|---|---|
| **业务 SPI** | `MeiyetecPlugin.java` / `MeiyetecPluginAdapter.java` | SPI 接口契约 + pf4j 桥接 |
| **核心服务** | `PluginRuntimeService.java` | 生命周期（init/run/load/enable/disable/unload）+ 懒加载 + DCL 单例 + loadAllFromDisk |
| **持久化** | `PluginPersistenceService.java` | `recordState` / `restoreStates` + 异常 warn 不抛语义 |
| **租户级** | `PluginTenantService.java` | `isEnabledForTenant` 叠加 pf4j + `plugin_install.enabled` |
| **沙箱** | `PluginSecurityPolicy.java` | 白/黑名单 + `assertPackageAllowed` + `assertCallerAllowed` |
| **DTO / Context** | `PluginRuntimeInfo.java` / `PluginRuntimeContext.java` | 看板输出 + onStart/onStop 上下文 |
| **Controller** | `controller/PluginRuntimeController.java` | `/plugin/runtime/*` 全部端点（含**无 `dataScope` 校验**风险）|
| **Entity / Mapper** | `entity/PluginRuntimeState.java` + `mapper/PluginRuntimeStateMapper.java` | 表缺失触达（Phase 1 标 MISSING）|
| **配置** | `application.yml` line 183-187 | `plugin.runtime.{enabled,plugins-root,auto-load-on-startup}` |
| **前端** | `views/plugin/index.vue` + `api/plugin.ts` | 只有 marketplace + 已安装 tab（**无 runtime tab**）|
| **插件示例** | `plugins/demo-hello/{pom.xml,DemoHelloPlugin.java,META-INF/pf4j/plugin.properties}` | 验证用（demo-hello 插件）|

### 2.4 重构类变更标记

按规则 14：「重构类变更必须先跑 Phase 3.5 Diff Audit」。

本批次**不涉及重构**（EXT-23 是新增模块，Batch 1.1 期间未触达）→ Phase 3.5 **不适用**。

---

## 三、Feature ID 规则（沿用 Batch 1.1 + 编号策略）

引用：`.cursor/rules/feature-id.mdc` + `docs/PROJECT_AUDIT_PLAYBOOK.md` §三

### 3.1 基础规则（沿用 Batch 1.1）

| 规则 | 内容 |
|---|---|
| **格式** | `F` + 三位数字（如 `F001`, `F012`, `F123`, `F031`, `F055`）|
| **拆分粒度** | 一个 Feature = 一个可独立验收的业务能力 |
| **拆分示例** | F001 登录 / F002 退出 / F003 用户列表 / F004 新增用户 / F005 编辑用户 / F006 删除用户 / F007 用户详情 / F008 权限配置 |
| **禁止** | 不要把"用户管理"作为一个 Feature / 不要把"订单模块"作为一个 Feature / 不要把一张数据库表作为一个 Feature |
| **Evidence 目录** | `audit/evidence/FXXX-<slug>/`（编号固定，缺一张即 UNVERIFIED）|

### 3.2 编号策略（**Batch 1.2 决策**）

| 选项 | 推荐 | 理由 |
|---|---|---|
| **A. 接续 Batch 1.1 的 F031 起** | ✅ **采用** | 全局唯一、便于跨批次交叉引用与 grep；Evidence 目录 / Issue 引用无歧义 |
| B. 从 F001 重新开始 | ❌ 不采用 | 跨批次引用需补 Batch 前缀（`F001-Batch1.1` vs `F001-Batch1.2`），增加元数据开销 |
| C. 混合（保留 F001-F030 给 Batch 1.1，新模块用 F101+）| ❌ 不采用 | 编号跳跃不利于连续阅读 |

### 3.3 Batch 1.2 编号规则

| 维度 | 值 |
|---|---|
| **起始编号** | **F031**（接续 Batch 1.1 的 F001-F030）|
| **预估上限** | **F055**（按 Operational Notes §3 endpoints + 子模块估算 20-25 Features）|
| **预留上限** | **F060**（5 个缓冲编号，应对 Phase 1 触达时发现新 Feature）|
| **冲突解决** | Phase 1 期间如发现编号冲突，重命名旧 Feature（不要回插新 Feature 到中间）|
| **跨批次引用** | 直接用全局 F 编号（如 "F031 见 `audit/evidence/F031-plugin-lifecycle-init/`"），不需加批次前缀 |

### 3.4 Issue 编号（沿用 Batch 1.1）

| 维度 | 值 |
|---|---|
| **Issue 编号** | 沿用全局 `I` + 三位数字（Batch 1.1 用到 I016）|
| **Batch 1.2 起始** | **I017**（Phase 3 期间首次登记）|
| **预留上限** | **I030**（预留 14 个 Issue 编号应对 Phase 1-5 触达）|

### 3.5 Phase 1 任务

按 §3.3 规则拆分 EXT-23 的 Feature 清单（编号 F031 起），覆盖：

- Plugin Runtime Service 生命周期（init/run/load/enable/disable/unload/list/dashboard）~5
- PluginPersistenceService 持久化（recordState/restoreStates）~2
- PluginTenantService 租户级（isEnabledForTenant/setTenantEnabled/list）~3
- PluginSecurityPolicy 沙箱（白名单/黑名单/assertCallerAllowed）~3
- Controller REST API（`/plugin/runtime/*` 全部端点）~10
- 配置 + 启动期（enabled=false / auto-load-on-startup=false）~2

**估算合计**：**20-25 Features**（F031-F050 范围），预留 F051-F060 应对扩展。

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

**Phase 2-7 任务**：对每个 Feature 标注其期望达到的 L 等级。Batch 1.2 期间建议 EXT-23 Feature **至少 L3**（静态链路通），有 demo-hello 插件时**争取 L4**（API 可调），L5a/L5b 因 Playwright MCP 缺失降级。

---

## 五、Result 子标签确认

引用：`docs/PROJECT_AUDIT_PLAYBOOK.md` §五 + `.cursor/rules/project-audit.mdc` 规则 7

**最终 Result 只允许以下 10 种状态**（沿用 Batch 1.1）：

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

### 5.1 EXT-23 特殊 Result 判定建议

| EXT-23 特殊场景 | 建议 Result | 理由 |
|---|---|---|
| **`plugin_runtime_state` 表缺失** | **MISSING** | 实体类存在但表不存在 → 触达 `MISSING` 状态 |
| **`plugin_install` 有数据但 runtime 未加载** | **PARTIAL-NON-BLOCKER**（兜底默认启用）| Operational Notes §5 已知问题，业务侧可能调 NPE |
| **PluginRuntimeController 无 `dataScope` 校验** | **PARTIAL-BLOCKER**（跨租户访问）| Operational Notes §5 + §0.7 高风险 |
| **`PluginSecurityPolicy.assertCallerAllowed` 实际不拦截 service.* 反射** | **PARTIAL-NON-BLOCKER**（合作式沙箱已知限制）| Operational Notes §5 已知问题 |
| **`disablePlugin` 副作用覆盖 FAILED 错误** | **PARTIAL-NON-BLOCKER**（运维审计线索丢失）| Operational Notes §5 已知问题 |
| **前端无 runtime tab** | **PARTIAL-NON-BLOCKER**（Shadow Feature）| 类比 I006 |
| **`loadPlugin` 不写 `autoLoaded=true`** | **UNVERIFIED**（dead code 候选）| Operational Notes §5 已知问题 |

### 5.2 严格禁止

- 没有运行证据只能标 UNVERIFIED（规则 3 + 9）
- 不允许出现 VERIFIED 但缺 traceId / DB row 的情况
- **EXT-23 特殊约束**：`plugin_runtime_state` 表缺失导致的所有相关 Feature → **强制 MISSING / PARTIAL-BLOCKER**（不允许 UNVERIFIED 掩盖表缺失）

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
| `nfr-evidence.md` | 非功能证据（traceId / 性能 / 多租户隔离 / 权限 / 沙箱）| L5b 必须有 |
| `ISSUE.md`（可选）| 该 Feature 的问题清单 | 非空时必须 |

### 6.2 EXT-23 特殊 Evidence 要求

| EXT-23 场景 | 必备文件 | 验证内容 |
|---|---|---|
| **每个 Feature** | `traceability-matrix.md` | 路由 → controller → service → mapper → SQL/表 → DTO 全栈追溯 |
| **Plugin 生命周期 Feature** | `runtime-evidence.md` 含 `pf4j-state-*.txt` | pf4j state 切换日志 |
| **持久化 Feature** | `db-evidence.sql` + `persisted-record-diff.txt` | `plugin_runtime_state` 写入前后 diff（**如表缺失则标 MISSING**）|
| **租户级 Feature** | `nfr-evidence.md` 含 `tenant-isolation-test.txt` | 多租户隔离测试结果（已知缺 `dataScope`）|
| **沙箱 Feature** | `nfr-evidence.md` 含 `sandbox-test.txt` | 黑名单拦截测试 + 反射加载测试 |
| **Controller Feature** | `runtime-evidence.md` 含 `dataScope-check.txt` | 跨租户访问校验结果 |

### 6.3 audit/ 目录骨架（Batch 1.2 沿用 v3）

| 子目录 | 职责 | Batch 1.2 产出 |
|---|---|---|
| `audit/baseline/` | 基线快照（Phase -1/0 产出，不可变）：`preflight-batch-1.2.md` + **`scope-freeze-batch-1.2.md`（本文件）**| 本批次已落档 |
| `audit/evidence/F031-.../ ~ F055-.../` | 每 Feature 证据沉淀 | Phase 1 期间按编号创建 |
| `audit/scripts/` | 8 个守门脚本（g1-g8）— **未实现**（preflight §6）| Phase 1-2 期间补（按用户决策 #2）|
| `audit/static-gap/` | Phase 3 静态差距扫描结果 | Phase 3 期间产出 |
| `audit/runtime/` | Phase 4 运行时计划 + Phase 5 验证 | `audit/runtime/plan-batch-1.2.md`（待写）|
| `audit/issues/issue-register.md` | Issue 汇总（沿用 Batch 1.1，单一文件）| §十七 起追加 I017+ |

### 6.4 回归对照三件套（规则 13）

每次验证必须绑定：

- **commit hash**：`96b00025783ed71cbafba5cf450cee7275ef2b8d`（Batch 1.2 Phase 0 freeze commit）+ `656caa72341c96d1b2e703dde993603dda9eebe1`（v3 整体 baseline）
- **DB snapshot**：`audit/baseline/db-snapshot.sql`（417 KB / 270 表 / schema-only）— **注意 `plugin_runtime_state` 表不存在**
- **env.md**：`audit/baseline/env.md`（9,951 B）

---

## 七、EXT-23 .mdc 为 SoT（事实来源）确认

引用：`.cursor/rules/project-audit.mdc` 规则 11 + `.cursor/rules/ext-23-plugin-runtime.mdc`（EXT-23 Operational Notes）

### 7.1 SoT 规则

> 「审计结论与 EXT-* Operational Notes 冲突时，以 EXT-* 为准并标 ISSUE。」

### 7.2 EXT-23 Operational Notes 内容覆盖

| § | 主题 | 内容要点 |
|---|---|---|
| **§0** | 关键约束 | 架构分层 / 目录布局 / 生命周期 API / 状态机 / 三件套（持久化 / 租户 / 沙箱）/ 配置 / 业务侧 vs 运行时端点 |
| **§1** | Touchpoints | 8 个 service 类 + 1 个 controller + 4 个 entity + 1 个 mapper + 插件示例 + 前端 |
| **§2** | Re-confirmation Checklist | 12 项生产/联调/上线前必查 |
| **§3** | Endpoints | 9 个 `/plugin/runtime/*` 端点（含租户级 4 个 + persisted）|
| **§4** | 新增插件 checklist | 7 步（pom / plugin class / properties / package / mvn / 重启 / 业务前置）|
| **§5** | 已知问题 / 限制 | 10 个已知 EXT-23 风险（沙箱 / 租户隔离 / 幽灵记录等）|
| **§6** | 业务侧调用模板 | 3 步（isEnabledForTenant → PluginWrapper.getPlugin → assertCallerAllowed）|

### 7.3 Batch 1.2 审计优先级

1. **EXT-23 Operational Notes（事实来源，优先级最高）**
2. `docs/PROJECT_AUDIT_PLAYBOOK.md`（体系规则）
3. `audit/issues/issue-register.md`（历史 Issue 关联）
4. `audit/baseline/preflight-batch-1.2.md`（已知约束）
5. `backend/src/main/java/com/meiyetec/plugin/*`（实际代码）
6. `backend/src/main/resources/application.yml`（配置）

### 7.4 EXT-23 Operational Notes 已加载确认

- ✅ `.cursor/rules/ext-23-plugin-runtime.mdc` 已加载（preflight §8.2 已确认）
- ✅ preflight §8.4 已摘录沙箱白/黑名单详单
- Phase 0 期间需再次确认 Operational Notes 与最新代码版本一致（preflight §8.2 验证清单）

---

## 八、能力边界确认（Cursor 不能 / 只能）

引用：`docs/PROJECT_AUDIT_PLAYBOOK.md` §十八 + `.cursor/rules/project-audit.mdc` 规则 9

### 8.1 Cursor **不能**做的事

| ❌ 不能 | 原因 |
|---|---|
| 真打开浏览器点击 | 无 Playwright MCP（沿用 Batch 1.1 决策）|
| 连接生产数据库执行查询 | 无 MySQL MCP 受限时的能力边界 |
| 判断业务是否符合 PRD | EXT-23 不在 PRD 主线；Operational Notes 为 SoT |
| 替代人工验收 | 最终验收必须人工签收 |
| 在没有运行证据时声称 VERIFIED | 规则 3 + 9 硬性要求 |
| **加载 / 运行 pf4j 插件 jar** | 需要真实 JVM + PluginManager 启动；Cursor 只能静态分析 |
| **执行 OAuth2 JWT / Salesforce 凭据流程** | 需要真实凭据；Batch 1.2 demo-hello 不涉及 |

### 8.2 Cursor **只能**做的事

| ✅ 能做 | 方式 |
|---|---|
| 静态分析代码 | grep / glob / Read 文件内容 |
| 生成测试代码和 SQL | 写文件 + 给建议 |
| 根据看到的内容推断 | 在已知 EXT-23 上下文下的合理推断 |
| 标注 UNVERIFIED | 显式标注，不掩盖缺失 |
| **检查 plugin.properties 完整性** | glob + Read 验证 `plugin.id` / `plugin.class` / `plugin.version` 字段 |
| **验证目录结构** | glob 检查 `${plugins-root}/{pluginCode}/{version}/*.jar` 布局 |
| **生成 demo-hello 插件测试** | 写 `audit/scripts/e2e/F031-...spec.ts` 供用户手动执行 |

### 8.3 本批次降级方案（沿用 Batch 1.1）

| 工具/能力 | 状态 | 降级方案 |
|---|---|---|
| **MySQL MCP** | ✅ 已配置 | 正常使用（含 `plugin_runtime_state` 表创建后验证）|
| **Playwright MCP** | ❌ 未配置（用户决策 #2：Phase 1-2 期间不阻塞）| Phase 5 E2E 降级为：① 生成 Playwright 脚本供用户手动执行；② 用户手动贴回 evidence；③ 静态抓 HTML + curl API + Vitest 替代 |
| **Redis** | ❓ 未验证（沿用决策）| 不探测；运行时 NPE 风险由 Phase 5 暴露 |
| **pf4j 运行时** | ❌ 不可在 Cursor 内运行 | 静态分析代码 + 生成 demo-hello 插件构建脚本 + 用户手动跑 |
| **守门脚本 g1-g8** | ❌ 未实现（沿用决策 #2：Phase 1-2 期间补）| Phase 1 期间补 |
| **EXT-23 特定守门脚本 g9-g11** | ⚠️ 建议（preflight §6.3）| Phase 1 期间评估是否实现 |

### 8.4 EXT-23 评估期间需要用户手动配合的事项

| 事项 | 触发 Phase | 用户配合内容 |
|---|---|---|
| 启动后端（含 pf4j PluginManager 初始化）| Phase 5 | 启动日志 + `/plugin/runtime/dashboard` 截图 |
| 编译 demo-hello 插件 jar | Phase 5 | `mvn clean package` 后拷贝到 `${plugins-root}/demo-hello/1.0.0/` |
| 加载 demo-hello 插件 | Phase 5 | `POST /plugin/runtime/demo-hello/load` 调用 + 响应 |
| 触发 `recordState` 持久化 | Phase 5 | 验证 `plugin_runtime_state` 表写入（**如表缺失则 ERROR**）|
| 跨租户访问测试 | Phase 5（如果表存在）| 普通租户调 `/plugin/runtime/tenant/{otherTid}/list` 期望 403 |

---

## 九、已知约束（引用 preflight-batch-1.2.md §八）

**本节仅引用，不复制内容**。详见 `audit/baseline/preflight-batch-1.2.md` §八（line 304-456）。

### 9.1 `plugin_runtime_state` 表缺失（**关键约束**）

| 维度 | 详情 | 来源 |
|---|---|---|
| **缺失表** | `plugin_runtime_state` | preflight §8.1 |
| **所属 EXT-* ** | EXT-23 | 同上 |
| **Operational Notes 引用** | §0.5a `PluginPersistenceService` 持久化表 | 同上 |
| **代码状态** | ✅ 实体类 + Mapper 已存在 | 同上 |
| **DB snapshot 状态** | ❌ **不存在** | 同上 |
| **Phase 1 影响** | 触达 → 标 `MISSING`（按 preflight 8.1 决策）| 同上 |
| **Phase 5 影响** | `PluginPersistenceService.recordState` 写表 → `TableNotFoundException` → try/catch 仅 warn → 与 Batch 1.1 I008 类似的 Fake Success 链路 | 同上 |
| **决策** | ❌ **本批次不修**（沿用规则 6 + Q3 + 用户决策 #3）| 同上 |
| **修复阶段** | **Post-Audit**（与 I008/I011/I012 一起处理 Fake Success 链路）| 推论 |

### 9.2 EXT-23 Operational Notes（`.cursor/rules/ext-23-plugin-runtime.mdc`）

| 维度 | 详情 | 来源 |
|---|---|---|
| **文件路径** | `.cursor/rules/ext-23-plugin-runtime.mdc` | preflight §8.2 |
| **当前状态** | ✅ **已加载** | 同上 |
| **内容覆盖** | §0-§6 完整（关键约束 / Touchpoints / Checklist / Endpoints / 新增插件 / 已知问题 / 调用模板）| 同上 |
| **SoT 优先级** | 最高（规则 11）| 同上 |

### 9.3 与 EXT-24 独立性检查

| 检查项 | 结果 | 来源 |
|---|---|---|
| **代码包共享** | ❌ 无共享 | preflight §8.3 |
| **DB 表共享** | ❌ 无共享 | 同上 |
| **配置共享** | ❌ 无共享 | 同上 |
| **业务依赖** | ❌ EXT-24 不依赖 EXT-23；EXT-23 不依赖 EXT-24 | 同上 |
| **EXT-15 依赖** | ❌ EXT-23 不依赖 EXT-15 | 同上 |
| **风险传染** | ⚠️ EXT-24 的 I008（catch 吞）/ I012（Bean 条件冲突）模式可能再犯 | 同上 |
| **审计可分离性** | ✅ 完全可分离 | 同上 |

### 9.4 沙箱白/黑名单（引用 preflight §8.4）

| 名单 | 内容 | 来源 |
|---|---|---|
| **白名单 `HOST_PUBLIC_PACKAGES`** | `com.meiyetec.plugin.` / `util.` / `exception.` | preflight §8.4.1 |
| **黑名单 `HOST_FORBIDDEN_PACKAGES`** | `com.meiyetec.service.internal.` / `service.impl.` / `security.` / `mapper.` / `config.` + `plugin.PluginRuntimeService` / `PersistenceService` / `TenantService` | preflight §8.4.2 |
| **黑名单 `FORBIDDEN_JDK_PACKAGES`** | `java.lang.reflect.` / `Runtime` / `Process` / `URLClassLoader` / `sun.misc.` / `sun.reflect.` / `jdk.internal.` / `java.io.FileSystem` / `java.nio.file.Files` | preflight §8.4.3 |
| **合作式沙箱风险** | `assertCallerAllowed` 仅做轻量探测，**反射加载 `com.meiyetec.service.*`（非 internal）实际不拦截** | preflight §8.4.4 |

### 9.5 Plugin 启动期配置（引用 preflight §8.5）

```yaml
plugin:
  runtime:
    enabled: ${PLUGIN_RUNTIME_ENABLED:true}    # 总开关
    plugins-root: ${PLUGIN_PLUGINS_ROOT:./plugins}   # 默认相对路径（用户决策 #7：不改）
    auto-load-on-startup: ${PLUGIN_AUTOLOAD:true}    # 启动时自动 load+enable
```

→ **用户决策 #7**：保持默认相对路径（生产部署时再改绝对路径）。

### 9.6 业务侧 `/plugin/*` ≠ 运行时 `/plugin/runtime/*`（引用 preflight §8.6）

| 路径 | 职责 | 是否触达 pf4j |
|---|---|---|
| `POST /plugin/market*` `/install*` `/review*` | marketplace CRUD | ❌ |
| `GET /plugin/runtime/list` `/dashboard` | pf4j 状态看板 | ✅ |
| `POST /plugin/runtime/{code}/load\|enable\|disable\|unload` | pf4j 生命周期 | ✅ |
| `GET /plugin/runtime/tenant/{tid}/list` | 租户级视图 | ✅ |
| `POST /plugin/runtime/tenant/{tid}/{code}/enable\|disable` | 租户级启停 | ✅ |
| `GET /plugin/runtime/persisted` | 持久化状态查询 | ❌（只读 DB）|

→ **关键**：前端「安装」按钮只插 `plugin_install` 表，**不触发 runtime load**。

### 9.7 受保护页面零风险（引用 preflight §五）

- Plugin Runtime 审计期间**不触达**受保护页面（`/dashboard` / `/mh/visual` / `/mh/consent`）

### 9.8 历史审计沉淀不在 v3 目录（沿用 Batch 1.1）

- `docs/audit/` 25+ 个 `audit_*.txt` + `BEAUTY_SAAS/` 子目录（28+ 报告）
- 不迁移（按 `audit/README.md` §2）
- Phase 7 引用时使用相对路径 `../docs/audit/BEAUTY_SAAS/19_REPAIR_ROADMAP_V3.md`

---

## 十、待用户确认项

| # | 项 | 当前默认 | 阻塞 Phase 1？ |
|---|---|---|---|
| 1 | Feature ID 编号策略：F031-F060（已决策）| ✅ 采用 | 否（已决策）|
| 2 | Issue 编号：I017 起（已决策）| ✅ 采用 | 否（已决策）|
| 3 | Phase 0 freeze 后是否需 Phase 0.5（commit 检查 + ENV 验证）| ❌ 跳过，直接 Phase 1 | 否 |
| 4 | g1-g8 守门脚本补齐时机（Phase 1-2 期间，已决策 #2）| Phase 1-2 期间补 | 否（按决策）|
| 5 | 是否在 Phase 0 期间更新 `audit/baseline/commit.txt` 为 Batch 1.2 freeze commit `96b0002`？| ❓ 未决策 | 是（baseline commit.txt 是规则 13 三件套之一）|
| 6 | PluginRuntimeController 跨租户访问问题：Phase 1 是否拆分为独立 Feature（F035 跨租户测试）？| ✅ 拆分（与 Operational Notes §0.7 对应）| 否 |
| 7 | `plugin_runtime_state` 表缺失：Phase 1 是否触发 ISSUE 编号 I017？| ✅ 触发 I017 | 否 |

### 10.1 默认决策影响

| 默认 | 影响 |
|---|---|
| **#3 跳过 Phase 0.5** | Phase 0 freeze 后立即 Phase 1（Feature Inventory），不做额外自检 |
| **#5 不更新 commit.txt** | 沿用 v3 baseline commit `656caa7`；Batch 1.2 freeze commit `96b0002` 仅在 Phase 0/1/5 Evidence 中引用，不覆盖 baseline |
| **#6 拆分跨租户 Feature** | F035（暂定）专门验证 `dataScope` 缺失风险 |
| **#7 触发 I017** | I017 = MISSING: `plugin_runtime_state` 表不存在 |

---

## 十一、Phase 0 准入自检

| 检查项 | 结果 |
|---|---|
| preflight-batch-1.2.md 已落档 | ✅ `audit/baseline/preflight-batch-1.2.md`（474 行）|
| 7 项决策已回答 | ✅ 用户已确认（commit C / g1-g8 Phase 1-2 / 不补 DDL / 不重评 / Phase 2 列 / 不评估 / 不改路径）|
| Phase 0 freeze commit 已落档 | ✅ `96b0002 audit(batch-1.2): EXT-23 Plugin Runtime preflight`（含 preflight + issue-register + batches；本 scope-freeze 文件另 commit）|
| scope-freeze-batch-1.2.md 可读 | ✅（本文件）|
| 项目定位已明确 | ✅ §一 |
| 验收范围已冻结 | ✅ §二 |
| Feature ID 规则已确认（含编号策略 F031 起）| ✅ §三 |
| L0-L5 等级已确认 | ✅ §四 |
| Result 子标签已确认 | ✅ §五 |
| Evidence 目录已约定（含 EXT-23 特殊要求）| ✅ §六 |
| EXT-23 SoT 已确认 | ✅ §七 |
| 能力边界已确认 | ✅ §八 |
| 已知约束已引用 | ✅ §九（指向 preflight-batch-1.2.md §八）|
| 待用户确认项已列出 | ✅ §十 |

✅ **Phase 0 完成。可以进入 Phase 1（Feature Inventory — F031 起）。**

---

## 十二、本文件状态

| 项 | 值 |
|---|---|
| **状态** | 不可变（frozen）|
| **变更控制** | 任何改动需用户明确同意 |
| **下一阶段产出位置** | `audit/feature-inventory.md` §1.2（F031-F060 EXT-23 Feature 清单）|
| **下一阶段 Prompt 模板** | 见 `在 Cursor 中落地这套验收体系的操作指南` §四「Phase 1」|

---

**Scope Freeze（Batch 1.2）完成。EXT-23 Plugin Runtime 范围已冻结 + 7 项决策已落档 + F031 编号策略已确认。⏸ 等用户确认 → 进入 Phase 1（Feature Inventory，F031 起）。**