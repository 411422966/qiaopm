# Scope Freeze（Phase 0 产出）

> 来源：`@project-audit.mdc` + `docs/PROJECT_AUDIT_PLAYBOOK.md` §十「Phase 0 — Audit Rules / Scope Freeze」
> 创建时间：2026-09-12 21:18 (UTC+8)
> 适用批次：v3 第一批（MKT 重构 + EXT-19/22/23/24）
> 上一阶段产出：`audit/baseline/preflight.md`（Phase -1）
> 模板链路：`preflight.md` §已知约束 → 本文件 §已知约束（引用，不复制）

---

## 〇、文档说明

Phase 0 **不列 Feature**（那是 Phase 1）。Phase 0 只做四件事：
1. 冻结规则（Feature ID / L0-L5 / Result 子标签 / Evidence 目录 / SoT 引用）
2. 冻结范围（本批次涉及哪些 EXT-* + 模块）
3. 确认能力边界（Cursor 能不能做什么）
4. 引用 preflight.md 的已知约束（不复制内容，仅引用）

---

## 一、项目定位

| 项 | 值 |
|---|---|
| **项目名** | QiaoPM（美业 SaaS 消费收银会员管理系统）|
| **代码仓库** | `D:\QiaoPM`（workspace root）|
| **业务范围** | 美容院 / 美发店 / 美甲美睫 / SPA / 会所 / 医美 |
| **核心特征** | 高频预约、套餐/次卡依赖、会员复购驱动、私域运营强 |
| **PRD** | `docs/PRD-美业SaaS系统功能大全.md`（v1.0, 14,190 B, 2026-07-31）|
| **标准模块** | M01-M12（预约/收银/会员/套餐/储值/项目/库存/员工/营销/评价/报表/连锁）|
| **进阶模块** | M13-M18（智能排班/动态定价/客户360/自动化营销/视觉档案/电子知情同意）|
| **优先级分布** | P0 × 5 / P1 × 6 / P2 × 6 / P3 × 1 |
| **支付通道** | 微信 / 支付宝 / 银联云闪付 / 数字人民币 2.0 / 花呗 / Stripe / Square / PayPal / Gift Card / BNPL |

**Phase 7 验收范围**：以 PRD 模块清单为依据，但具体 Feature 清单由 Phase 1 输出，本阶段不预判。

---

## 二、验收范围（本批次）

按用户决策，本批次 v3 第一批仅涉及以下模块，**不在范围内的模块不在本批次审计**：

### 2.1 涉及模块

| EXT-* | 模块 | 主要改动 |
|---|---|---|
| **EXT-19** | 多语言预约确认 | `OverseasBooking.miniProgramOpenId` 字段 + 6 个 locale i18n template + 5 个 channel sender（SMS / Email / MiniProgram / InApp / WechatOA）|
| **EXT-22** | Google Maps Place Details + 有赞签名 | `enablePlaceDetails` 计费开关 + `ecommerce_platform.{appId,kdtId,signSecret}` 三个新字段 + `verifyWebhookSignature` 工具 |
| **EXT-23** | Plugin Runtime（pf4j 3.10.0）| `MeiyetecPlugin` SPI + `MeiyetecPluginAdapter` + `PluginRuntimeService` + `PluginPersistenceService` + `PluginTenantService` + `PluginSecurityPolicy`（合作式沙箱）|
| **EXT-24** | CRM 集成 | `CrmAdapter` 接口 + `SalesforceCrmAdapter`（OAuth2 JWT）+ `MockCrmAdapter` + `CrmSyncScheduler` + `crm_integration.objectMappings` JSON 配置 + `SyncCursor.knownExternalIds` 去重 |
| **本批次** | 营销模块重构（无 EXT-* 编号）| `MarketActivity` 实体/Service/Controller/Mapper 删除 + 拆出 6 个新控制器（`MktChannel` / `MktJourney` / `MktTemplate` / `MktTouchTask` / `MktConversion` / `MktEffect`）|

### 2.2 不在本批次范围（明确排除）

| 类别 | 模块 | 排除原因 |
|---|---|---|
| 受保护页面 | `/dashboard`, `/mh/visual`, `/mh/consent` | 用户已实测通过，按 `.cursor/rules/no-touch-verified-pages.mdc` 只观察不修复 |
| 历史模块 | M01-M18 中除上述 5 个外的所有模块 | 不在本批次；待后续批次（v3 第二批起）评估 |
| 基础设施 | Redis / MySQL 配置 / CI 流水线 | 基础设施层不在本批次审计 |

### 2.3 重构类变更标记

按规则 14：「重构类变更必须先跑 Phase 3.5 Diff Audit」。

本批次包含 **重构**（MKT 模块整体重写 + 营销模块拆分）→ **Phase 3.5 不可跳过，必须先于 Phase 4**。

---

## 三、Feature ID 规则确认

引用：`.cursor/rules/feature-id.mdc` + `docs/PROJECT_AUDIT_PLAYBOOK.md` §三

| 规则 | 内容 |
|---|---|
| **格式** | `F` + 三位数字（如 `F001`, `F012`, `F123`）|
| **拆分粒度** | 一个 Feature = 一个可独立验收的业务能力 |
| **拆分示例** | F001 登录 / F002 退出 / F003 用户列表 / F004 新增用户 / F005 编辑用户 / F006 删除用户 / F007 用户详情 / F008 权限配置 |
| **禁止** | 不要把"用户管理"作为一个 Feature / 不要把"订单模块"作为一个 Feature / 不要把一张数据库表作为一个 Feature |
| **Evidence 目录** | `audit/evidence/FXXX-<slug>/`（编号固定，缺一张即 UNVERIFIED）|

**Phase 1 任务**：按此规则拆分本批次 5 个模块的 Feature 清单。

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
- L5a：**需真实浏览器交互**（降级方案见 §能力边界）
- L5b：**需 NFR 工具 + traceId 体系**（降级方案见 §能力边界）

**Phase 2-7 任务**：对每个 Feature 标注其期望达到的 L 等级。

---

## 五、Result 子标签确认

引用：`docs/PROJECT_AUDIT_PLAYBOOK.md` §五 + `.cursor/rules/project-audit.mdc` 规则 7

**最终 Result 只允许以下 9 种状态**：

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

**严格禁止**：
- 没有运行证据只能标 UNVERIFIED（规则 3 + 9）
- 不允许出现 VERIFIED 但缺 traceId / DB row 的情况

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

### 6.2 audit/ 目录骨架（v3）

| 子目录 | 职责 |
|---|---|
| `audit/baseline/` | 基线快照（Phase -1/0 产出，不可变）：`preflight.md` / `commit.txt` / `env.md` / `db-snapshot.sql` / **`scope-freeze.md`（本文件）** |
| `audit/evidence/FXXX-<slug>/` | 每 Feature 证据沉淀 |
| `audit/scripts/` | 8 个守门脚本（g1-g8）— **未实现**（preflight §6）|
| `audit/static-gap/` | Phase 3 静态差距扫描结果 |
| `audit/runtime/` | Phase 4 运行时计划 + Phase 5 验证 |
| `audit/db/` | Phase 2 DB Schema Truth |
| `audit/business/` | Phase 3 业务测试矩阵 + Phase 6 业务 E2E |
| `audit/non-functional/` | Phase 7 非功能报告 |
| `audit/reports/` | Phase 8 收尾报告 |

### 6.3 回归对照三件套（规则 13）

每次验证必须绑定：
- **commit hash**（`audit/baseline/commit.txt` 当前 = `656caa7...`）
- **DB snapshot**（`audit/baseline/db-snapshot.sql`，417 KB / 270 表 / schema-only）
- **env.md**（`audit/baseline/env.md`，9,951 B）

---

## 七、EXT-* 为 SoT（事实来源）确认

引用：`.cursor/rules/project-audit.mdc` 规则 11 + 4 份 EXT-* Operational Notes

### 7.1 SoT 规则

> 「审计结论与 EXT-* Operational Notes 冲突时，以 EXT-* 为准并标 ISSUE。」

### 7.2 本批次已加载的 EXT-* Operational Notes（4 份）

| EXT-* | 文件 | 主要内容 |
|---|---|---|
| **EXT-19** | `.cursor/rules/ext-19-operational-notes.mdc` | 多语言预约确认 / WeChat 模板占位 / EmailSender 降级 demo / CONFIRMED 自动触发 sendConfirmation / StoreLookupService / 6 个 locale i18n template |
| **EXT-22** | `.cursor/rules/ext-22-google-maps-and-ecommerce.mdc` | Google Maps Place Details 计费开关 / 有赞 `appKey` ≠ `appId` / 有赞 webhook `md5(signSecret + rawBody)` / `buildWebhookUrl` 工具 / SQL 迁移兼容 |
| **EXT-23** | `.cursor/rules/ext-23-plugin-runtime.mdc` | pf4j 3.10.0 架构分层 / `MeiyetecPlugin` SPI / `PluginRuntimeService` 生命周期 / `PluginPersistenceService` / `PluginTenantService` / `PluginSecurityPolicy` 合作式沙箱 / 业务侧调用模板 |
| **EXT-24** | `.cursor/rules/ext-24-crm-integration.mdc` | `POST /crm-integration/{id}/sync` 行为变更 / `objectMappings` JSON 配置 / `SyncCursor.knownExternalIds` 去重 / `KNOWN_IDS_LIMIT=5000` / 排程与手动触发共用 `runIncremental` / 跨租户访问 |

### 7.3 本批次审计优先级

1. EXT-* Operational Notes（事实来源，**优先级最高**）
2. `docs/PROJECT_AUDIT_PLAYBOOK.md`（体系规则）
3. `docs/PRD-美业SaaS系统功能大全.md`（业务需求）
4. `docs/EXEC-*.md`（执行细节）
5. `docs/TECH-*.md`（技术方案）
6. `backend/src/main/java/...` / `frontend/meiyetec-web/src/...`（实际代码）

---

## 八、能力边界确认（Cursor 不能 / 只能）

引用：`docs/PROJECT_AUDIT_PLAYBOOK.md` §十八 + `.cursor/rules/project-audit.mdc` 规则 9

### 8.1 Cursor **不能**做的事

| ❌ 不能 | 原因 |
|---|---|
| 真打开浏览器点击 | 无 Playwright MCP（用户决策 1：未配置）|
| 连接生产数据库执行查询 | 无 MySQL MCP 受限时的能力边界 |
| 判断业务是否符合 PRD | 需人工核对；Cursor 只对照接口/字段映射 |
| 替代人工验收 | 最终验收必须人工签收 |
| 在没有运行证据时声称 VERIFIED | 规则 3 + 9 硬性要求 |

### 8.2 Cursor **只能**做的事

| ✅ 能做 | 方式 |
|---|---|
| 静态分析代码 | grep / glob / Read 文件内容 |
| 生成测试代码和 SQL | 写文件 + 给建议 |
| 根据看到的内容推断 | 在已知 EXT-* 上下文下的合理推断 |
| 标注 UNVERIFIED | 显式标注，不掩盖缺失 |

### 8.3 本批次降级方案（按用户决策）

| 工具/能力 | 状态 | 降级方案 |
|---|---|---|
| **MySQL MCP** | ✅ 已配置 | 正常使用 |
| **Playwright MCP** | ❌ 未配置（用户决策 1）| **Phase 5 E2E 降级为**：① 生成 Playwright 脚本供用户手动执行；② 用户手动贴回 evidence；③ 静态抓 HTML + curl API + Vitest 替代 |
| **Redis** | ❓ 未验证（用户决策 4）| 不探测；运行时 NPE 风险由 Phase 5 暴露 |
| **守门脚本 g1-g8** | ❌ 未实现（用户决策 3：不阻塞 Phase 0）| **Phase 1 期间补**（按用户决策 3）|

---

## 九、已知约束（引用 preflight.md §八）

**本节仅引用，不复制内容**。详见 `audit/baseline/preflight.md` §八（line 253-313）。

### 9.1 EXT-* 缺失表（preflight §8.1，Phase 1 触达）

| # | 缺失表 | 所属 EXT-* | preflight 引用 |
|---|---|---|---|
| 1 | `crm_contact_map` | EXT-24 | §8.1 / line 253-264 |
| 2 | `overseas_booking` | EXT-19 | §8.1 |
| 3 | `google_maps_config` | EXT-22 | §8.1 |
| 4 | `plugin_runtime_state` | EXT-23 | §8.1 |

> **Phase 1 触达任务**：判断这 4 张表缺失是 MISSING / BROKEN / SHELL-ABANDONED / MOCK-FAKE 中的哪一种。

### 9.2 market_activity 表仍在（preflight §8.2）

- DB snapshot 中 `market_activity` 表存在
- `migrate_mkt_002_drop_market_activity.sql` 已写但**未跑**
- `MarketActivity.java` 等 4 个类已删除（D 状态）
- `docs/ROLLBACK-6.2-mkt_ddl.md` 已写（6,121 B）
- **Phase 1 触达**：标 ISSUE（按用户决策 4）

### 9.3 Redis 未验证（preflight §8.3，用户决策 4）

- 不探测；运行时风险由 Phase 5 暴露

### 9.4 Playwright MCP 未配置（preflight §8.4，用户决策 1）

- Phase 5 降级方案见 §能力边界 8.3

### 9.5 守门脚本 g1-g8 未实现（preflight §8.5，用户决策 3）

- Phase 1 期间补

### 9.6 working tree 仍 dirty（preflight §8.6，已结案）

- 已 git stash push -m "after-phase-minus-1-clean-before-phase-0"（不带 -u）
- 25+ 处 modified 已 stash（`stash@{0}`）
- preflight.md + scope-freeze.md（untracked）保留在 working tree
- 18 个其他 untracked 文件（root 中文文件名 / docs/ 落地文件 / backend 新增文件）保留

### 9.7 历史审计沉淀不在 v3 目录（preflight §8.7）

- `docs/audit/` 25+ 个 `audit_*.txt` + `BEAUTY_SAAS/` 子目录（28+ 报告）
- 不迁移（按 `audit/README.md` §2）
- Phase 7 引用时使用相对路径 `../docs/audit/BEAUTY_SAAS/19_REPAIR_ROADMAP_V3.md`

### 9.8 EXT-* Operational Notes 已加载（preflight §8.8）

- 见本文件 §七

---

## 十、待用户确认项

| # | 项 | 当前默认 | 阻塞 Phase 1？ |
|---|---|---|---|
| 1 | 是否在 Phase 1 期间补齐 8 个守门脚本（g1-g8）？ | 按用户决策 3：补 | 否（按决策）|
| 2 | 是否需要在 Phase 2（DB Schema Truth）前跑 `migrate_mkt_002_drop_market_activity.sql`？ | ❓ 未决策 | 是（DB schema 状态不一致会影响 Phase 2）|
| 3 | 是否需要在 Phase 2 前补齐 4 张 EXT-* 缺失表的 DDL？ | ❓ 未决策 | 是（同样影响 Phase 2）|
| 4 | Redis 连通性是否在 Phase 5 之前补充探测？ | 按用户决策 4：不探测 | 否（按决策）|
| 5 | 受保护页面（/dashboard, /mh/visual, /mh/consent）后端接口清单由谁提供？ | Phase 2 静态扫描时列出 | 否 |
| 6 | 本批次是否分多个 sub-batch 跑（如先 EXT-24 后 EXT-22 等）？ | 默认按 5 个模块合并跑 | 否 |

---

## 十一、Phase 0 准入自检

| 检查项 | 结果 |
|---|---|
| preflight.md 已落盘 | ✅ `audit/baseline/preflight.md`（28,858 B / 358 L）|
| 7 项决策已回答 | ✅ 用户已确认 |
| git status modified 已 stash | ✅ `stash@{0}: after-phase-minus-1-clean-before-phase-0` |
| preflight.md + scope-freeze.md 可读 | ✅（都在 `audit/baseline/`）|
| 项目定位已明确 | ✅ §一 |
| 验收范围已冻结 | ✅ §二 |
| Feature ID 规则已确认 | ✅ §三 |
| L0-L5 等级已确认 | ✅ §四 |
| Result 子标签已确认 | ✅ §五 |
| Evidence 目录已约定 | ✅ §六 |
| EXT-* SoT 已确认 | ✅ §七 |
| 能力边界已确认 | ✅ §八 |
| 已知约束已引用 | ✅ §九（指向 preflight.md §八）|
| 待用户确认项已列出 | ✅ §十 |

✅ **Phase 0 完成。可以进入 Phase 1（Feature Inventory）。**

---

## 十二、本文件状态

| 项 | 值 |
|---|---|
| **状态** | 不可变（frozen）|
| **变更控制** | 任何改动需用户明确同意 |
| **下一阶段产出位置** | `audit/static-gap/`（Phase 1 完成后）|
| **下一阶段 Prompt 模板** | 见 `在 Cursor 中落地这套验收体系的操作指南` §四「Phase 1」|

---

**Scope Freeze 完成。等待用户确认 → 进入 Phase 1（Feature Inventory）。**
