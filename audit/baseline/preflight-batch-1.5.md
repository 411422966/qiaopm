# Pre-flight Check（Batch 1.5 — Phase -1 产出）

> 适用规则：`@project-audit.mdc` 规则 16（Phase -1 必须先于 Phase 0）
> 模板：`@CURSOR_AUDIT_GUIDE.md`「二点五」章节
> 创建时间：2026-09-15 01:16 (UTC+8)
> 适用批次：**v3 第五批 / Batch 1.5 = MKT 多渠道重构**
> 上一批次完结：`audit/reports/batch-1.4-summary.md`（EXT-19 33/33 Feature 完结）+ `audit/issues/issue-register.md` §十九（I038-I052）+ §二十（EN-09 I054 NEW 建议）
> SoT 引用：`docs/ROLLBACK-6.2-mkt_ddl.md`（MKT-002 DDL 回滚预案 SoT）

---

## 〇、关键核实（用户预先提问 → 落档）

### 0.1 I053 是否存在？

| 项 | 结果 |
|---|---|
| **I053 单独文件**（`audit/issues/I053*.md`）| ❌ **不存在**（`ls audit/issues/I05*` → 仅 `I052-mvn-test-compilation-failure.md`）|
| **issue-register.md 中 I053 章节 / 行** | ❌ **不存在**（§一/§十九/§二十 均无 I053 痕迹）|
| **其他文档提及 I053** | ❌ **不存在**（全项目 grep `I053` / `I05[3-5]` 0 匹配） |
| **版本号模式推测** | `scope-freeze-batch-1.4.md:145` 提到 "**I055**（预留 18 个 Issue 编号应对 Phase 1-5 触达）" → I054 / I055 是**预留给 Phase 5 触达**的 buffer，未触达则**跳号** |
| **现有 I054 状态** | 仅 §二十.20.1 "**I054 NEW（建议）**" 段落登记（mkt_channel_config 表缺失），**无单独 issue 文件**，落档为建议而非正式登记 |
| **结论** | ⚠️ **I053 = 真实跳号**（无内容、无登记、无报告） — 推测原因为：Batch 1.4 Phase 5 期间预留 I054 给触达，但触达未发生，I053 顺位空缺 |
| **建议处理** | 把 I053 占位为 "**Batch 1.4 Phase 5 触达预留但未发生**"，避免后续批次审计误判漏登记。如需补登记，建议在 issue-register.md §二十.20.1 后插入 §二十.1 段落登记 I053 跳号事实，不创建独立 issue 文件 |

### 0.2 用户 Q1-Q4 决策落档（执行 Batch 1.5 前确认）

| # | 决策 | 用户回复 | 解读 |
|---|---|---|---|
| **Q1** | Batch 1.5 选哪个模块？ | ✅ **MKT 多渠道重构** | 沿用 Batch 1.4 报告 §11 推荐路径 A（MKT）|
| **Q2** | I047 Fake Success（IG webhook catch 吞异常）是否前置修？| ❌ **不修**。沿用规则 6 + D10 铁律 | **CRITICAL-P1** 但 Fix Phase = **Post-Audit** |
| **Q3** | 所有 MISSING 表（含 I038 / I045 / I054 NEW）是否在 Batch 1.5 前一次性跑迁移？| ❌ **第 5 次强调不跑** | Batch 1.5 期间 EXT-19 PARTIAL-BLOCKER 维持现状；migrate_mkt_002_drop_market_activity.sql 也**不跑** |
| **Q4** | I037（webhook path 未 exclude）+ I051（token→tenantId 演示模式）是否同步修？| ❌ **不修**。Fix Phase = Post-Audit | 沿用 Batch 1.4 决策 |

**统一动作**：Batch 1.5 期间**不修任何业务代码**；规则 6 覆盖 Q2 + Q4；规则 6 + D10 铁律覆盖 Q3（迁移）。

---

## 一、MCP 状态（沿用 Batch 1.1/1.2/1.3/1.4 决策）

| MCP | 状态 | 来源 |
|---|---|---|
| **MySQL MCP** | ✅ **已配置** | 沿用 Batch 1.1/1.2/1.3/1.4 决策 |
| **Playwright MCP** | ❌ **未配置**（沿用前四批次决策）| Phase 5 E2E 降级：① 生成 Playwright 脚本供用户手动执行；② 用户手动贴回 evidence；③ 静态抓 HTML + curl API + Vitest |

**降级方案（沿用）**：

| 工具/能力 | 降级方案 |
|---|---|
| **MySQL MCP** | ✅ 正常使用（验证 market_activity 表存在 + tenant_id 列 + ChannelDispatcher health + 6 个 Mkt*Controller 路由 + I054 推论验证）|
| **Playwright MCP** | Phase 5 降级为静态抓 HTML + curl API + Vitest（沿用 Batch 1.1/1.2/1.3/1.4 模式）|

---

## 二、PRD 覆盖（MKT 部分）

### 2.1 PRD 整体覆盖（沿用 Batch 1.1/1.2/1.3/1.4 §二）

| 项 | 值 |
|---|---|
| **路径** | `docs/PRD-美业SaaS系统功能大全.md`（14,190 bytes, v1.0, 2026-07-31）|
| **覆盖范围** | 12 标准模块（M01-M12）+ 6 进阶模块（M13-M18），P0/P1/P2/P3 分级 |
| **MKT 相关模块** | **M09（营销渠道）** 直接相关；**M01（预约管理）** 间接相关；MKT 多渠道重构涉及 channel sender + 重构 |

### 2.2 MKT 重构覆盖度

| PRD 模块 | MKT 覆盖？ | 备注 |
|---|---|---|
| M01（预约管理）| ⚠️ 间接 | 营销链路中可能涉及预约推送（与 EXT-19 共用 BookingConfirmation） |
| M09（营销渠道）| ✅ **直接** | 6 个 Mkt*Controller + ChannelDispatcher（SMS/EMAIL/MINI_PROGRAM/IN_APP/WECHAT_OA）覆盖渠道管理 |
| M10（会员中心）| ⚠️ 间接 | MktCoupon / MktPoints 可能涉及会员积分系统 |
| M11（订单中心）| ⚠️ 间接 | MktPromotion 可能下发订单促销（与 mkt_promotion 表关联）|
| M17（自动化营销）| ✅ **直接** | MarketingAutomationService + channel 编排 |
| **MKT 多渠道重构独立段** | ❌ PRD 未覆盖 | 重构属于**架构治理层**（旧 MarketActivity → 新 6 个 Mkt*Controller + 删 market_activity 表），非业务主线模块 |

**Phase 1 影响**：
- MKT 重构评估**不依赖 PRD 模块清单**（属于架构治理层）
- **`docs/ROLLBACK-6.2-mkt_ddl.md`** 作为 SoT 替代 PRD（沿用规则 11）
- Feature Inventory 直接以 6 个 Mkt*Controller + ChannelDispatcher 为骨架

### 2.3 缺失文档

| 缺失内容 | 实际位置 | 影响 Phase |
|---|---|---|
| **MKT 6 个 Controller 接口契约** | 源码注释 + Mkt Channel 路由表 | Phase 1 / 2 |
| **ChannelDispatcher 接口契约** | `service/channel/ChannelDispatcher.java`（待 Phase 1 读全文）| Phase 1 / 2 |
| **market_activity 迁移前置脚本执行记录** | DBA 文档（缺失）| Phase 5 需用户补 |
| **跨业务依赖（BusinessPolicyDispatchServiceImpl 调用链）** | 静态扫描（无 SoT 段）| Phase 1 期间明确边界 |
| **Re-confirmation Checklist** | **未找到 MKT 专属 Operational Notes**（区别于 EXT-19/22/23/24 各自有 .mdc）| Phase 5 需自建 |

**关键缺口**：⚠️ **MKT 多渠道重构无专属 .mdc / Operational Notes**（区别于前四批次的 EXT-19/22/23/24 都有 `.cursor/rules/ext-*.mdc`）。SoT 退化为 `docs/ROLLBACK-6.2-mkt_ddl.md` + git status 中 MKT 相关文件清单。

---

## 三、BLOCKER 阈值（沿用）+ MKT 重构特定

### 3.1 沿用前四批次 BLOCKER 阈值

| 情况 | 是否 BLOCKER（沿用）| 备注 |
|---|---|---|
| Mock 未清理（含 fallback 链路）| ✅ **是** | 部分场景豁免（MOCK-INTENTIONAL） |
| 迁移脚本未在生产跑过 | ✅ **是** | dev 环境跑过也算"通过" |
| webhook 端点缺失（已知平台未对接）| ✅ **是** | Batch 1.5 不引入新 webhook |
| 未跑 E2E 的核心业务 | ✅ **是** | "核心业务"边界由谁定义 |
| SoT 文档 vs 代码方向不一致 | ✅ **是**（中立标 ISSUE，Fix Phase = Post-Audit 人类决策）| 沿用 I001 / I018 / I026 / I040 模式 |
| **重构批次：旧代码残留 + 新代码并存** | ✅ **是**（Phase 3.5 Diff Audit 必跑）| MKT 重构特有 BLOCKER |

### 3.2 MKT 重构特定 BLOCKER 项

| 项 | 默认 | 备注 |
|---|---|---|
| **market_activity 表未 DROP** | ✅ **是**（`migrate_mkt_002_drop_market_activity.sql` 已写但未跑）| Q3 决策第 5 次强调**不跑**迁移；Batch 1.5 期间表 + Java 旧类共存 |
| **旧 `MarketActivity*` 5 个 Java 类未删除** | ✅ **是**（Phase 3.5 Diff Audit 重点）| 重构中间态 — SoT 注释说"Java 侧无读写"，实际类文件存在 |
| **`mkt_channel_config` 表未重建** | ✅ **是**（I054 NEW 推论 + Q3 不跑迁移）| ChannelDispatcher.healthCheckAll 推论表缺失 |
| **6 个 Mkt*Controller 中部分 endpoint 引用缺失表** | ⚠️ **观察** | Phase 5 需 MySQL MCP 验证 dev DB 中 mkt_* 表完整 |
| **`BusinessPolicyDispatchServiceImpl` 仍 grep 引用 MarketActivity** | ⚠️ **观察** | 源码注释说"已无读写" → 需 Phase 3 grep 完整调用链 |
| **`MktCouponController` / `MktPromotionController` / `MktPointsController` 在 git status 未声明** | ⚠️ **观察** | 6 个 Mkt Controller 之外的额外 3 个，需 Phase 1 确认纳入范围 |
| **ChannelDispatcher I050 死代码（tenant_id=1L 硬编码）** | ⚠️ **观察** | Batch 1.4 I050 已知 |
| **mockChannel fallback** | ⚠️ **观察** | `@ConditionalOnMissingBean` 模式（沿用 I012 经验）|
| **MKT 无 .mdc / Operational Notes** | ⚠️ Shadow | SoT 退化为 `docs/ROLLBACK-6.2-mkt_ddl.md` + git status；规则 11 SoT 优先级无文件可遵循 |

### 3.3 MKT 重构"中间态"约束

| 维度 | 状态 | 风险 |
|---|---|---|
| **新代码（Mkt*Controller / ChannelDispatcher）** | ✅ 已存在（git status dirty）| Phase 1 静态确认完整 |
| **旧代码（MarketActivity* 5 个 Java 类）** | ⚠️ **未删**（仍在 HEAD）| Phase 3.5 Diff Audit 重点 — 与"完全迁移"假设的差异 |
| **旧表（market_activity）** | ⚠️ **未 DROP**（`migrate_mkt_002_drop_market_activity.sql` 未跑）| 旧代码仍可引用，编译通过；运行时会触发缺表异常 |
| **新表（mkt_promotion / mkt_channel_config 等）** | ⚠️ **可能未建**（I054 NEW 已登记 mkt_channel_config 缺失；mkt_promotion 需验证）| Batch 1.5 启动期间验证 |
| **业务代码（BusinessPolicyDispatchServiceImpl）** | ⚠️ 仍 grep 引用 MarketActivity | Phase 3 完整调用链扫描 |

**核心矛盾**：⚠️ **MKT 重构**实际处于 **"代码已加 + 旧代码未删 + 表未迁移"** 中间态，与典型完全重构（"代码加完即删旧"）**反向**。审计期间不修业务代码（规则 6 + Q3）→ 中间态将贯穿 Batch 1.5 全程。

---

## 四、git 状态

### 4.1 当前 HEAD

| 项 | 值 |
|---|---|
| **HEAD** | `e85ba8b`（`audit(batch-1.2-phase0): EXT-23 Plugin Runtime scope freeze`，沿用 Batch 1.4 preflight §4.1，未变）|
| **Batch 1.5 起点 commit** | **继承 Batch 1.4 freeze commit `e85ba8b`** |
| **Batch 1.4 freeze commit** | `e85ba8b`（沿用）|
| **Batch 1.3 freeze commit** | `e85ba8b`（沿用）|
| **Batch 1.2 freeze commit** | `e85ba8b` |
| **Batch 1.1 freeze commit** | `12a97ea57a98d268c864fc7c010b82b305d7bb93` |
| **v3 整体 baseline commit** | `656caa72341c96d1b2e703dde993603dda9eebe1` |

### 4.2 当前 working tree（`git status --short` 关键筛选）

```
M  .gitignore
M  audit/batches.md
M  audit/feature-inventory.md
M  audit/issues/issue-register.md
M  audit/traceability-matrix.md
M  backend/src/main/resources/application.yml

?? audit/baseline/preflight-batch-1.3.md
?? audit/baseline/preflight-batch-1.4.md
?? audit/baseline/scope-freeze-batch-1.3.md
?? audit/baseline/scope-freeze-batch-1.4.md
?? audit/reports/batch-1.4-summary.md

?? backend/src/main/java/com/meiyetec/controller/{MktChannel,MktConversion,MktJourney,MktTemplate,MktTouchTask,MktEffect}Controller.java  ← MKT 重构 6 个新 Controller
?? backend/src/main/java/com/meiyetec/service/ReportExportFileCleanupService.java
?? backend/src/main/resources/db/sql/migrate_mkt_002_drop_market_activity.sql  ← 重构迁移脚本
?? backend/src/main/resources/db/sql/migrate_mkt_002_rollback.sql               ← 回滚脚本
```

**注意**：还有 3 个 Mkt*Controller 不在 git status 显式列表但已实际存在：
- `MktCouponController.java`
- `MktPromotionController.java`
- `MktPointsController.java`

> ⚠️ **git status 与实际文件不一致**（git status 仅列 6 个新 Mkt Controller，但 `ls controller/Mkt*.java` 找到 9 个）— 这是 MKT 重构的"中间态"特征之一，**Phase 1 期间需对齐实际范围**。

### 4.3 MKT 重构文件状态详细表

| 文件路径 | git status 标记 | 在 HEAD 中？ | 备注 |
|---|---|---|---|
| `controller/MktChannelController.java` | `??`（新增未 track）| ❌ 未在 HEAD | 6 个 Mkt Controller 之一 |
| `controller/MktConversionController.java` | `??` | ❌ 未在 HEAD | 6 个 Mkt Controller 之一 |
| `controller/MktJourneyController.java` | `??` | ❌ 未在 HEAD | 6 个 Mkt Controller 之一 |
| `controller/MktTemplateController.java` | `??` | ❌ 未在 HEAD | 6 个 Mkt Controller 之一 |
| `controller/MktTouchTaskController.java` | `??` | ❌ 未在 HEAD | 6 个 Mkt Controller 之一 |
| `controller/MktEffectController.java` | `??` | ❌ 未在 HEAD | 6 个 Mkt Controller 之一 |
| `controller/MktCouponController.java` | （未列出）| ❌ 未在 HEAD | **额外 1 个 Mkt Controller（git status 漏列）** |
| `controller/MktPromotionController.java` | （未列出）| ❌ 未在 HEAD | **额外 1 个（git status 漏列）** |
| `controller/MktPointsController.java` | （未列出）| ❌ 未在 HEAD | **额外 1 个（git status 漏列）** |
| `db/sql/migrate_mkt_001_add_tenant.sql` | （未列出）| ✅ **已在 HEAD** | 重构前置迁移（已加 tenant_id） |
| `db/sql/migrate_mkt_002_drop_market_activity.sql` | `??` | ❌ 未在 HEAD | 删 market_activity 表迁移（准备） |
| `db/sql/migrate_mkt_002_rollback.sql` | `??` | ❌ 未在 HEAD | 回滚脚本 |
| `docs/ROLLBACK-6.2-mkt_ddl.md` | （未列出）| ✅ **已在 HEAD** | SoT 文档 |
| `controller/MarketActivityController.java` | （未列出）| ✅ **仍在 HEAD** | ⚠️ **旧 Controller 未删** |
| `service/MarketActivityService.java` | （未列出）| ✅ **仍在 HEAD** | ⚠️ **旧 Service 接口未删** |
| `service/impl/MarketActivityServiceImpl.java` | （未列出）| ✅ **仍在 HEAD** | ⚠️ **旧 Service 实现未删** |
| `mapper/MarketActivityMapper.java` | （未列出）| ✅ **仍在 HEAD** | ⚠️ **旧 Mapper 未删** |
| `entity/MarketActivity.java` | （未列出）| ✅ **仍在 HEAD** | ⚠️ **旧 Entity 未删** |
| `service/impl/BusinessPolicyDispatchServiceImpl.java` | （未列出）| ✅ **仍在 HEAD** | ⚠️ **仍 grep 引用 MarketActivity**（根据 §四 grep 结果）|

### 4.4 待 commit / stash 决策（沿用前四批次）

| 选项 | 内容 | 推荐 |
|---|---|---|
| **A** | 直接 commit（baseline + preflight + 8 项 + MKT 改动一次性入库）| ❌ 不推荐 |
| **B** | 拆 commit（baseline 单独 + MKT 重构文件 + preflight 分批）| ⚠️ 可行但复杂 |
| **C** | 再 stash 一次（Phase -1 完成后保持 working tree clean）| ✅ **推荐**（沿用前四批次决策）|

→ **沿用前四批次决策**：Phase -1 报告完成后**等用户决策 C（stash）**。

### 4.5 Phase 0 freeze commit 候选

| 候选 | 描述 | 何时 |
|---|---|---|
| **冻结 Batch 1.4 commit** | `e85ba8b` 已在 HEAD，作为 Batch 1.5 起点 | 已就位 |
| **冻结 Batch 1.5 commit** | 包含 `preflight-batch-1.5.md` + `scope-freeze-batch-1.5.md` | Phase 0 启动前 |

---

## 五、受保护页面（沿用 Batch 1.1/1.2/1.3/1.4）

> 来源：`.cursor/rules/no-touch-verified-pages.mdc`（用户实测通过）

| # | 路由 | 前端文件 | 后端接口 | 状态 |
|---|---|---|---|---|
| 1 | `/dashboard` | `frontend/meiyetec-web/src/views/dashboard/**` | （待 Phase 2 列出）| ✅ 已验证 |
| 2 | `/mh/visual` | `frontend/meiyetec-web/src/views/mh/visual.vue` | （待 Phase 2 列出）| ✅ 已验证 |
| 3 | `/mh/consent` | `frontend/meiyetec-web/src/views/mh/consent.vue` | （待 Phase 2 列出）| ✅ 已验证 |

### 5.1 MKT 重构与受保护页面的关系

| 检查项 | 结果 |
|---|---|
| **MKT 重构是否影响 `/dashboard` 数据？**| ⚠️ **需 Phase 2 静态扫描** — `dashboard` 数据可能含营销渠道统计（ChannelDispatcher.healthCheckAll） |
| **MKT 重构是否影响 `/mh/visual` / `/mh/consent`？**| ❌ **不触及**（mh 模块是视觉档案/合规记录，与营销渠道无关）|
| **`MarketingAutomationService` 是否被受保护页面调用？**| ⚠️ **需 Phase 2 静态扫描** |
| **`MktChannelController` / `MktEffectController` 是否被 dashboard 调用？**| ⚠️ **需 Phase 2 静态扫描** — dashboard 渠道统计属疑似依赖 |

→ **MKT 重构审计期间受保护页面**潜在风险（dashboard 渠道统计间接），**待 Phase 2 确认无间接依赖**。`/mh/visual` / `/mh/consent` 零风险。

---

## 六、守门脚本（g1-g8 命名约定存在 / 脚本未实现 — 沿用决策）

### 6.1 8 个守门脚本状态（沿用前四批次）

> **当前状态**：8 个守门脚本（g1-g8）**未实现**（沿用前四批次决策，Phase 1-2 期间补）。

| 编号 | 脚本名（预期）| 当前状态 | 阻塞 Phase 0？ |
|---|---|---|---|
| **g1** | `g1-scope.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g2** | `g2-traceability.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g3** | `g3-issue-format.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g4** | `g4-evidence-check.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g5** | `g5-runtime-contract.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g6** | `g6-no-touch-pages.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g7** | `g7-blocker-rules.sh` / `.ps1` | ❌ **未实现** | 否 |
| **g8** | `g8-pre-deploy.sh` / `.ps1` | ❌ **未实现** | 否 |

### 6.2 MKT 重构特定守门脚本（可选扩展）

| 编号 | 扩展脚本 | 用途 |
|---|---|---|
| **g20** | `g20-mkt-controller-existence.sh` | 验证 6（+3=9）个 Mkt*Controller 类存在 + 路由注解 `@RequestMapping("/mkt/...")` |
| **g21** | `g21-market-activity-legacy-check.sh` | 验证旧 `MarketActivity*` 5 个类文件存在状态（重构中间态基线）|
| **g22** | `g22-mkt-002-migration-symmetry.sh` | 验证 `migrate_mkt_002_drop_market_activity.sql` ↔ `migrate_mkt_002_rollback.sql` 双向操作对称 |
| **g23** | `g23-channel-dispatcher-registration.sh` | 验证 ChannelDispatcher 注册 5 个 ChannelSender（SmsSender/EmailSender/MiniProgramSender/InAppSender/WechatOfficialAccountSender）|

→ **可选**：Phase 1 期间根据需要新增 MKT 守门脚本（不强制）。

### 6.3 风险

- 规则 15：自动化守门脚本每次 PR 必须跑 → **当前 CI 不守门 v3 体系规则**
- MKT 重构是首个**重构批次**，守门脚本缺失可能在 Phase 3.5 Diff Audit 暴露"重构一致性"问题

---

## 七、当前批次：MKT 多渠道重构（含 Phase 3.5 Diff Audit）

### 7.1 批次基本信息

| 项 | 值 |
|---|---|
| **Batch ID** | **Batch 1.5** |
| **模块** | **MKT 多渠道重构（6 个新 Mkt*Controller + ChannelDispatcher 完善 + market_activity 表 DROP + 旧 MarketActivity* 5 个类删除）** |
| **来源** | `docs/ROLLBACK-6.2-mkt_ddl.md`（MKT-002 SoT）+ git status（MKT 改动清单）|
| **起点时间** | 2026-09-15 01:16 (UTC+8)（本批次启动）|
| **Phase -1 时间** | 2026-09-15 01:16 (UTC+8)（本文件落档）|
| **起点 commit** | `e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2/1.3/1.4 freeze commit，沿用）|

### 7.2 MKT 重构范围完整性检查（静态确认） ⭐ Phase 3.5 Diff Audit 前置

#### 7.2.1 旧 `MarketActivity*` 5 个类 — **未删除**（重构中间态 ⚠️）

| 文件 | 状态 | 备注 |
|---|---|---|
| `entity/MarketActivity.java` | ⚠️ **未删** | 仍可在 Java 路径上引用 |
| `mapper/MarketActivityMapper.java` | ⚠️ **未删** | 同上 |
| `service/MarketActivityService.java` | ⚠️ **未删**（interface） | 10 个抽象方法保留（`activityPage` / `getActivity` / `createActivity` / `updateActivity` / `deleteActivity` / `stats` / `detail` / `toggleStatus` / `copyActivity` / `participants`）|
| `service/impl/MarketActivityServiceImpl.java` | ⚠️ **未删** | Service 实现仍编译通过 |
| `controller/MarketActivityController.java` | ⚠️ **未删** | 旧 HTTP 端点仍可访问（路由冲突风险）|

**Phase 3.5 Diff Audit 必查**：
1. 这些类是否被任何地方 `@Autowired` / `@Resource` 注入？
2. `controller/MarketActivityController` 的 HTTP 路由是否与新 6 个 Mkt*Controller 冲突？
3. 旧端点（`/market-activity/*`）是否仍被前端 `frontend/meiyetec-web/src/views/market-activity/**` 引用？

#### 7.2.2 新 6 个 Mkt*Controller — **真实存在** ✅

| 文件 | 状态 | 路由（已确认首个）| 备注 |
|---|---|---|---|
| `controller/MktChannelController.java` | ✅ **存在** | `@RequestMapping("/mkt/channel")` | 5 个端点（`/list` `POST /` `PUT /{id}` `DELETE /{id}` `POST /health-check`）|
| `controller/MktConversionController.java` | ✅ **存在** | （待 Phase 1 全文核实）| MKT 转化追踪 |
| `controller/MktJourneyController.java` | ✅ **存在** | （待 Phase 1 全文核实）| MKT 用户旅程 |
| `controller/MktTemplateController.java` | ✅ **存在** | （待 Phase 1 全文核实）| MKT 模板 |
| `controller/MktTouchTaskController.java` | ✅ **存在** | （待 Phase 1 全文核实）| MKT 触达任务 |
| `controller/MktEffectController.java` | ✅ **存在** | （待 Phase 1 全文核实）| MKT 效果分析 |

**额外 3 个 Mkt*Controller（git status 未声明）— 需 Phase 1 范围确认**：

| 文件 | 状态 | 备注 |
|---|---|---|
| `controller/MktCouponController.java` | ✅ **存在** | **git status 漏列**，可能属于 MKT Coupon 子模块（与 mkt_coupon 表关联） |
| `controller/MktPromotionController.java` | ✅ **存在** | **git status 漏列**，可能属于 mkt_promotion 表关联 |
| `controller/MktPointsController.java` | ✅ **存在** | **git status 漏列**，可能属于积分系统 |

⚠️ **git status 不完整** → 实际 MKT Controller 数量 = **9 个**（6 + 3），不是 6 个。**Phase 1 Feature Inventory 需对齐 9 个 Controller**。

#### 7.2.3 SQL 迁移脚本 — **真实存在** ✅

| 文件 | 状态 | 行数确认 | 备注 |
|---|---|---|---|
| `db/sql/migrate_mkt_001_add_tenant.sql` | ✅ **已在 HEAD**（不在 git status dirty 列表）| 旧迁移（已加 tenant_id）| 前置 |
| `db/sql/migrate_mkt_002_drop_market_activity.sql` | ✅ **存在** | 49 行预览（前 25 行）：① 探测表存在 → ② 探测行数 → ③ RENAME → ④ DROP | 主动作 — **未跑（Q3）** |
| `db/sql/migrate_mkt_002_rollback.sql` | ✅ **存在** | 30 行预览（前 30 行）：存储过程 `migrate_mkt_002_rollback()` 探测归档表 → 反向 RENAME | 回滚 — **作为 SoT 预案** |

**回滚对称性快速检查** ✅：
- 主脚本探测：表存在 → 0 行 → RENAME → DROP
- 回滚脚本探测：归档表 `market_activity__archived_20260910` 存在 → 反向 RENAME → 重建 market_activity
- **对称性已确认**：归档表名 `market_activity__archived_20260910` 在两个脚本中**严格一致** ✅

#### 7.2.4 SoT 回滚文档 — **真实存在** ✅

| 文件 | 状态 | 内容 |
|---|---|---|
| `docs/ROLLBACK-6.2-mkt_ddl.md` | ✅ **已在 HEAD**（不在 git status dirty 列表）| 6.2 数据迁移脚本 + 双写期回滚预案（MKT-002 SoT） |
| **关键决策** | T08-Q1 = A（确认无数据 → 直接归档 + DROP）| DBA 2026-09-10 核对生产 market_activity 0 行 |
| **关键设计** | ① 探测行数（0 行才继续）② 重命名为 `market_activity__archived_20260910` 留观 90 天 ③ 立即 DROP（选项 A）④ 若表不存在静默跳过 | 不写 Flyway 版本，由 DBA 手工 `mysql <` 执行 |
| **触发条件（90 天观察窗口 2026-09-10 → 2026-12-09）** | ① 下游 BI 报表依赖 ② 生产遗漏 SQL 引用 ③ 业务部门审计需求 | 触发后停服 → 执行 rollback → 验证 → 启服 |

### 7.3 MKT 重构四要素状态矩阵（⭐ Phase 3.5 Diff Audit 关键）

| 维度 | 期望（重构完成态） | 实际（当前） | 评估 |
|---|---|---|---|
| **旧 Java 类（MarketActivity* 5 个）** | ❌ 已删除 | ⚠️ **5 个全部存在** | 🔴 **未完成** |
| **新 Controller（6 + 3 = 9 个 Mkt*Controller）** | ✅ 全部存在 | ✅ **全部存在**（git status 漏列 3 个）| ✅ 部分完成（**Phase 1 需确认范围**）|
| **迁移脚本（migrate_mkt_002_drop + rollback）** | ✅ 全部写好且对称 | ✅ **存在且对称** | ✅ 完成（**未跑但本批次不跑 Q3**）|
| **回滚文档（docs/ROLLBACK-6.2）** | ✅ 存在 | ✅ **存在** | ✅ 完成 |

→ **核心差距**：⚠️ **旧 Java 类未删除**，与"完全重构"假设**反向**。Phase 3.5 Diff Audit 必跑以明确这一中间态的全部影响。

### 7.4 与 Batch 1.1/1.2/1.3/1.4 独立性检查

| 检查项 | 结果 |
|---|---|
| **代码包共享** | ⚠️ **部分共享** — `MarketingAutomationService` + `ChannelDispatcher` + 5 个 `ChannelSender`（`service/channel/*Sender.java`）在 Batch 1.4 EXT-19 也使用，但属**不同职责**（EXT-19 用作发送确认 / MKT 用作营销触达）|
| **DB 表共享** | ⚠️ **部分共享** — `mkt_channel_config` 同时被 EXT-19（EmailSender/SmsSender 的 channelId 引用）+ MKT 多渠道（ChannelDispatcher）使用；I054 NEW 已登记该表缺失 |
| **配置共享** | ⚠️ `spring.mail.*` 全局配置（MKT Email channel）+ `mkt_channel_config.configJson`（租户级 SMTP）|
| **业务依赖** | ⚠️ MKT 渠道管理**不依赖** EXT-19/22/23/24，但共享基础设施 |
| **审计可分离性** | ✅ Feature Inventory 可分离（Phase 1 期间明确边界 — 按 MKT 视角划分 6+3=9 Controller）|

**特殊风险**：
- **I047（IG webhook catch 吞异常，CRITICAL-P1）**沿用 — MKT 可能再犯（ChannelDispatcher / MarketingAutomationService 中的 catch 模式需 Phase 3 警惕）
- **I054 NEW（mkt_channel_config 表缺失）**沿用 — Phase 5 ChannelDispatcher 真实调用将暴露 `TableNotFoundException`
- **I050（ChannelDispatcher.healthCheckAll tenant_id=1L 硬编码）**沿用 — Phase 3 死代码审查重点
- **I012（@ConditionalOnMissingBean + @Component 冲突模式）**沿用 — mockChannel fallback 需 Phase 3 警惕

### 7.5 Phase 3.5 Diff Audit 必跑清单（⭐ 重构批次特有）

> ⚠️ **MKT 是首个**重构批次**，Phase 3.5 Diff Audit 是**强制 Phase**（@project-audit.mdc 规则 14）。**

| # | 审计项 | 期望 | 验证方法 |
|---|---|---|---|
| **1** | 旧 `MarketActivity*` 5 个类是否被任何代码 `@Autowired` / `@Resource` 注入？| ❌ 0 个调用方（SoT 注释说"已无读写"）| `Grep -r "MarketActivityService\|MarketActivityMapper\|MarketActivity entity" backend/src` |
| **2** | `MarketActivityController` 的 HTTP 路由是否与 9 个 Mkt*Controller 冲突？| ❌ 0 冲突 | `Grep "@RequestMapping" backend/src/main/java/com/meiyetec/controller/MarketActivityController.java` |
| **3** | 旧 `/market-activity/*` 端点是否被前端 `frontend/meiyetec-web/src/views/market-activity/**` 引用？| ❌ 0 引用（前端应已迁移到 `/mkt/*`）| `Grep "market-activity" frontend/meiyetec-web/src` |
| **4** | `BusinessPolicyDispatchServiceImpl` 注释说"已无读写"是否与代码一致？| 注释 vs 代码一致 | Phase 1 全文读 `BusinessPolicyDispatchServiceImpl` + grep `MarketActivity` |
| **5** | 9 个 Mkt*Controller 注册到 Spring `@RestController` 是否都能注入并路由成功？| ✅ 全部能编译 + 启动 | Phase 5 启动 Spring + curl 测试 9 个 endpoint |
| **6** | `migrate_mkt_002_drop_market_activity.sql` ↔ `migrate_mkt_002_rollback.sql` 对称性？| ✅ 完全对称 | grep `market_activity__archived_20260910` 在两个脚本中 |
| **7** | `docs/ROLLBACK-6.2-mkt_ddl.md` 描述的归档表名是否与 SQL 脚本一致？| ✅ 一致 | grep `market_activity__archived_20260910` 在 md + sql 三处 |
| **8** | 旧 Controller 的端点（`/market-activity/*`）是否仍在 Spring 路由表中？| ⚠️ 若旧 Controller 注册 → 路由冲突 | Phase 5 curl 测试 `/market-activity/*` |

**Phase 3.5 产出**：`audit/diff-audit/mkt-refactor.md`（重构差异审计报告）。

### 7.6 Feature 估算（待 Phase 1 落实）

| 子模块 | 估算 Feature 数 | 来源 |
|---|---|---|
| MktChannelController（5 endpoint）| ~5-8 | `/mkt/channel/list` `/mkt/channel` `PUT/DELETE /mkt/channel/{id}` `/mkt/channel/health-check` |
| MktConversionController | ~3 | 转化追踪 |
| MktJourneyController | ~3 | 用户旅程 |
| MktTemplateController | ~3 | 营销模板 |
| MktTouchTaskController | ~3 | 触达任务 |
| MktEffectController | ~3-5 | 效果分析 + dashboard |
| MktCouponController | ~3-5 | 优惠券 |
| MktPromotionController | ~3-5 | 促销活动 |
| MktPointsController | ~3-5 | 积分系统 |
| ChannelDispatcher + 5 Sender | ~5 | 渠道路由 + 发送 |
| 重构存量审计（market_activity 删除 + 旧类清理）| ~5-8 | Phase 3.5 审计 |
| **估算合计** | **~40-50 Features** | **Feature ID 从 F114 起（接续 Batch 1.4 的 F113）** |

---

## 八、已知约束

### 8.1 market_activity 表（Q3 第 5 次强调不跑迁移）

| 维度 | 详情 |
|---|---|
| **表名** | `market_activity` |
| **所属任务** | MKT-002（DROP） |
| **SoT 引用** | `docs/ROLLBACK-6.2-mkt_ddl.md` §1 |
| **生产数据** | 0 行（DBA 2026-09-10 核对）|
| **迁移脚本** | `migrate_mkt_002_drop_market_activity.sql`（已写但未跑）|
| **回滚脚本** | `migrate_mkt_002_rollback.sql`（已写，未跑）|
| **Q3 决策** | ❌ **第 5 次强调不跑**（Batch 1.5 期间表仍存在）|
| **Phase 5 影响** | ChannelDispatcher / MarketingAutomationService 等若使用 market_activity → 表存在 → 正常；若无使用 → 表存在无害 |

### 8.2 旧 `MarketActivity*` 5 个 Java 类（重构中间态）

| 维度 | 详情 |
|---|---|
| **清单** | `entity/MarketActivity` + `mapper/MarketActivityMapper` + `service/MarketActivityService`（interface）+ `service/impl/MarketActivityServiceImpl` + `controller/MarketActivityController` |
| **当前状态** | ⚠️ **全部存在**（SoT 注释说"已无读写"，但类文件还在）|
| **Phase 3 影响** | Phase 3 完整调用链扫描（grep Autowired + import + 静态编译证据）|
| **Phase 5 影响** | 若路由表中注册，会响应 `/market-activity/*` 请求；可能与新 Mkt*Controller 路由冲突 |
| **决策** | Phase 3.5 Diff Audit 必跑；本批次不修业务代码（规则 6）|

### 8.3 I054 NEW（mkt_channel_config 表缺失 — 上一批次建议登记，本批次纳入范围）

| 维度 | 详情 |
|---|---|
| **Issue ID** | **I054 NEW（建议登记，落档到 §二十.20.1，未升级为正式 issue 文件）** |
| **类别** | MISSING（DB）|
| **严重度** | 🟠-中-高 |
| **影响 Feature** | F094（ChannelDispatcher.healthCheckAll）+ Batch 1.5 ChannelDispatcher 全部 |
| **Fix Phase** | Post-Audit（Batch 1.5 期间维持 MISSING 状态）|
| **Phase 5 影响** | Phase 5 真实调用 ChannelDispatcher.healthCheckAll → `TableNotFoundException` → 审计的有效负向证据（沿用 I002 / I017 / I029 / I038 / I045 模式）|

### 8.4 I047 CRITICAL-P1（IG webhook catch 吞异常，沿用）

| 维度 | 详情 |
|---|---|
| **Issue ID** | I047 |
| **严重度** | 🔴 **CRITICAL-P1** |
| **Phase 1 影响** | MKT ChannelDispatcher / MarketingAutomationService 中的 catch 模式需 Phase 3 警惕（避免重犯 Fake Success / Fake Failure）|
| **Fix Phase** | Post-Audit |

### 8.5 I050 死代码（ChannelDispatcher.healthCheckAll tenant_id=1L 硬编码）

| 维度 | 详情 |
|---|---|
| **Issue ID** | I050 |
| **类别** | 死代码 / 演示模式例外（T07-D4）|
| **影响 Feature** | F094 |
| **Phase 3 影响** | ChannelDispatcher 多租户适配是 Phase 3 死代码 + 跨租户读风险审查重点（沿用 I019 模式）|
| **Fix Phase** | Post-Audit |

### 8.6 Phase 3.5 Diff Audit 必跑（重构批次特有约束）

| 维度 | 详情 |
|---|---|
| **规则来源** | `@project-audit.mdc` 规则 14 |
| **触发条件** | 重构类变更（含 DROP 迁移 + 新 Controller + 旧 Controller 删除 + 接口签名变动）|
| **MKT 触发原因** | ① DROP `market_activity` 表 ② 新增 9 个 Mkt*Controller ③ 旧 `MarketActivity*` 5 个类应删未删 ④ 新增 `migrate_mkt_002_*` 脚本族 |
| **产出位置** | `audit/diff-audit/mkt-refactor.md` |
| **不跑后果** | 重构中间态的全部影响未量化 → Phase 5 E2E 盲测 → 审计结论不可信 |
| **决策** | Phase 1 末尾必跑，**不阻塞 Phase 0** 但阻塞 Phase 2（Traceability）+ Phase 5（E2E 设计）|

### 8.7 与前 4 批次独立性

| 共享资源 | MKT 使用方 | 边界划分 |
|---|---|---|
| `mkt_channel_config` 表 | Batch 1.4 EXT-19（EmailSender/SmsSender 引用）+ Batch 1.5 MKT（ChannelDispatcher）| 以 `tenantId` 隔离；I054 NEW 是共同阻塞项 |
| `pos_store` 表 | Batch 1.4 EXT-19（StoreLookupService）+ 不被 MKT 使用 | 互不干扰 |
| `overseas_booking` 表 | Batch 1.4 EXT-19 核心 + 不被 MKT 使用 | 互不干扰 |
| `member_message` 表 | Batch 1.4 EXT-19 + 不被 MKT 使用 | 互不干扰 |
| `spring.mail.*` 配置 | Batch 1.4 EXT-19 EmailSender + Batch 1.5 MKT Email channel | 共用全局配置；SMTP mismatch 降级模式共用 |
| 5 个 `ChannelSender`（SMS/EMAIL/MINI_PROGRAM/IN_APP/WECHAT_OA）| Batch 1.4 EXT-19 + Batch 1.5 MKT 共用 | **同一套 sender，多套触发逻辑**；MKT 不修改 sender 实现 |

### 8.8 市场活动相关表（清查）

| 表 | 用途 | MKT 关联 | Phase 5 是否存在？|
|---|---|---|---|
| `market_activity` | 旧简易活动表（9 列 + rule_config JSON）| **待 DROP（MKT-002）**| ⚠️ 旧（Batch 1.1 preflight 称存在）|
| `mkt_promotion` | 新展开式活动表（30+ 列）| ✅ 核心替代表 | ⚠️ Phase 5 验证存在性 |
| `mkt_channel_config` | 渠道配置（含 tenant_id）| ✅ ChannelDispatcher 用 | ❌ **I054 NEW 缺失** |
| `mkt_segment` | 用户分群 | ✅ MKT 用 | ⚠️ Phase 5 验证 |
| `mkt_audit_log` | 营销审计 | ✅ MKT 用 | ⚠️ Phase 5 验证 |
| `mkt_automation` | 自动化营销 | ✅ MKT 用 | ⚠️ Phase 5 验证 |
| `mkt_coupon` / `mkt_points` / `mkt_promotion` 等 | 优惠/积分/促销子表 | ✅ 3 个额外 Mkt Controller 用 | ⚠️ 9 vs 6 vs 实际需 Phase 1 对齐 |

### 8.9 MKT 重构无 .mdc / Operational Notes（沿用规则 11 SoT 退化）

| 维度 | 详情 |
|---|---|
| **缺失内容** | MKT 多渠道重构无专属 `.cursor/rules/ext-mkt-*.mdc` 或 Operational Notes |
| **前四批次对照** | Batch 1.1 EXT-24 → `ext-24-crm-integration.mdc` ✅ / Batch 1.2 EXT-23 → `ext-23-plugin-runtime.mdc` ✅ / Batch 1.3 EXT-22 → `ext-22-*.mdc` ✅ / Batch 1.4 EXT-19 → `ext-19-operational-notes.mdc` ✅ |
| **Batch 1.5 对照** | ❌ **无专属 .mdc** |
| **SoT 退化路径** | ① `docs/ROLLBACK-6.2-mkt_ddl.md`（MKT-002 SoT）② `git status` 改动清单 ③ 6+3=9 个 Mkt*Controller 源码注释 ④ `MktChannelController.java` 已确认含「路由清单」注释（首批 Controller 注释完整）|
| **后果** | Phase 1 Feature Inventory 需**自建骨架**，不能照搬前四批次的 Operational Notes §X 模板 |
| **建议** | Phase 2 / 3 期间补建 `ext-mkt-multichannel.mdc`（沿用前四批次 Operational Notes 格式）— Post-Audit 排期 |

### 8.10 受保护页面与 ChannelDispatcher 间接依赖（dashboard 渠道统计）

| 维度 | 详情 |
|---|---|
| **风险路径** | `/dashboard` → 渠道统计 → ChannelDispatcher.healthCheckAll → mkt_channel_config（缺失 I054）→ TableNotFoundException |
| **风险等级** | 🟡 **中**（dashboard 是受保护页面之一）|
| **Phase 2 任务** | 静态扫描 `frontend/meiyetec-web/src/views/dashboard/**` 是否有 `mktChannelApi.*` 或 `/mkt/channel/health-check` 引用 |
| **决策** | ⚠️ 若 dashboard 调用 → 受保护页面**间接风险**（即使不修业务代码，也需确认 dashboard 不显式调用 ChannelDispatcher）|

---

## 九、Phase -1 准入确认（自检）

| 检查项 | 结果 |
|---|---|
| Batch 1.5 = MKT 多渠道重构 已决策（用户启动消息）| ✅ 用户决策 2026-09-15 01:16（Q1-Q4）|
| MCP 状态已确认 | ✅ MySQL ✅ / Playwright ❌（沿用降级）|
| PRD 覆盖已评估 | ✅ MKT 模块 PRD 部分覆盖（M09 / M17）；无独立段；以 `docs/ROLLBACK-6.2-mkt_ddl.md` 为 SoT |
| BLOCKER 阈值已沿用 | ✅ 前四批次 §三 + MKT 特定项（含重构批次 Phase 3.5 强制）|
| git 状态已快照 | ✅ HEAD `e85ba8b` / dirty 沿用前四批次 |
| 受保护页面已列出 | ✅ 沿用 `/dashboard` / `/mh/visual` / `/mh/consent` |
| 守门脚本状态已确认 | ✅ g1-g8 命名约定存在，脚本未实现 |
| 当前批次范围已明确 | ✅ MKT 多渠道重构（含 9 个 Mkt*Controller — git status 漏列 3 个）|
| MKT 重构四要素状态已检查 | ✅ 旧类未删 / 新 Controller 存在 / SQL 脚本对称 / ROLLBACK 文档存在 |
| Phase 3.5 Diff Audit 已规划 | ✅ 8 项审计清单（§7.5）已落地 |
| 已知约束已记录 | ✅ market_activity / 旧类残留 / I054 / I047 / I050 / Phase 3.5 / 与前 4 批次独立性 / 营销表清查 / 无 .mdc / dashboard 间接依赖 |
| I053 跳号已核实 | ✅ **真实跳号**（无内容、无登记、无报告）|
| Q1-Q4 决策已落档 | ✅ §0.2 表已明确（不修 I047、不跑迁移、不修 I037/I051）|

✅ **Phase -1 准入条件全部满足。可以进入 Phase 0（scope-freeze-batch-1.5.md）。**

---

## 十、待用户确认清单

| # | 决策项 | 默认 | 影响 |
|---|---|---|---|
| **1** | **9 个 vs 6 个 Mkt*Controller 范围如何确定？**| 实际静态扫描确认（git status 漏列 MktCoupon/MktPromotion/MktPoints），按实际 9 个范围审计 | Phase 1 Feature Inventory 范围 |
| **2** | **Phase 3.5 Diff Audit 必跑的 8 项清单是否完整？**| 是（§7.5）；如需扩展请指出 | Phase 3.5 输出 `audit/diff-audit/mkt-refactor.md` |
| **3** | **MKT 无 .mdc Operational Notes 是否在 Batch 1.5 期间补建？**| ⚠️ 沿用决策：**Post-Audit 排期**，Batch 1.5 内不强求 | Phase 1 / 2 Feature Inventory 自建骨架 |
| **4** | **`/dashboard` 渠道统计是否调用 ChannelDispatcher？**| ⚠️ **必查**（§8.10 受保护页面间接风险）；Phase 2 期间静态扫描 | 受保护页面约束 |
| **5** | **Phase -1 报告完成后 commit 策略（A/B/C）**| **C 再 stash 一次**（沿用前四批次）| git history |
| **6** | **是否需要 Phase 0 期间补齐 g1-g8 守门脚本？**| 沿用前四批次：Phase 1-2 期间补 | 不阻塞 Phase 0 |
| **7** | **Phase 0 freeze commit 是否在 `e85ba8b` 后追加？**| ✅ 推荐（追加 `audit(freeze): Batch 1.5 MKT 多渠道重构启动`）| Phase 0 启动前 |
| **8** | **I053 跳号如何落档？**| 建议插入 §二十.20.1 段落登记 I053 跳号事实，**不创建独立 issue 文件**；I054 NEW 升级或保持现状待用户决策 | issue-register.md 完整度 |
| **9** | **I054 NEW 是否在 Batch 1.5 期间升级为正式 issue 文件 `audit/issues/I054-mkt-channel-config-missing.md`？**| 沿用 §二十.20.1 现状（仅段落登记）；如需独立文件请指出 | Phase 1 / 2 Issue 增补 |
| **10** | **MKT 与 EXT-19 共用 `mkt_channel_config` 表的边界划分？**| 以 `tenantId` 隔离；I054 NEW 是共同阻塞项；Phase 1 期间明确 | Phase 1 Feature Inventory |

### 10.1 默认决策影响

| 默认 | 影响 |
|---|---|
| **#1 9 个 Mkt*Controller 全纳入** | Phase 1 Feature Inventory 数量从 ~30 增至 ~40-50（详细见 §7.6）|
| **#2 Phase 3.5 8 项必跑** | Phase 1 末执行，产出 `audit/diff-audit/mkt-refactor.md`；阻塞 Phase 2/5 |
| **#3 .mdc Post-Audit 补** | Phase 1 Feature Inventory 自建骨架；Phase 2 / 3 缺 SoT 引用 |
| **#4 dashboard ChannelDispatcher 调用必查** | Phase 2 静态扫描 `frontend/meiyetec-web/src/views/dashboard/**` |
| **#5 stash C** | Phase -1 完成后保持 working tree clean |
| **#6 g1-g8 Phase 1-2 期间补** | 不阻塞 Phase 0 |
| **#7 freeze commit 追加** | git history 清晰；Phase 0 scope-freeze 锚点明确 |
| **#8 I053 落档** | issue-register.md §二十 插入 §二十.20.1.1 段落；不创建独立文件 |
| **#9 I054 NEW 维持** | 沿用 §二十.20.1 建议登记状态；不创建独立 issue 文件 |
| **#10 tenantId 隔离** | Phase 1 Feature ID 按 MKT 视角划分 |

---

## 十一、本文件状态

| 项 | 值 |
|---|---|
| **状态** | 已落档（Phase -1 产出）|
| **变更控制** | Phase 0 启动后冻结；Phase 1 期间如发现新约束追加新段（§十二+）|
| **下一阶段产出位置** | `audit/baseline/scope-freeze-batch-1.5.md`（Phase 0）|
| **下一阶段 Prompt 模板** | 见 `docs/PROJECT_AUDIT_PLAYBOOK.md` §十「Phase 0」|
| **Phase 3.5 产出位置**（后续）| `audit/diff-audit/mkt-refactor.md` |

---

**Batch 1.5 Phase -1 完结。MKT 多渠道重构范围已确认：9 个 Mkt*Controller（git status 漏列 3 个），市场活动 0 行旧表待 DROP（不跑 Q3），旧 MarketActivity* 5 个 Java 类未删（重构中间态），migrate_mkt_002 + rollback 真实存在且对称，ROLLBACK-6.2 文档已存在，I054 NEW 已为 mkt_channel_config 缺失预先登记，Phase 3.5 Diff Audit 8 项清单已落档。⏸ 等用户确认 → 进入 Phase 0（scope-freeze-batch-1.5.md）。**
