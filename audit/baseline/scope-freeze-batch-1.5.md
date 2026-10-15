# Scope Freeze — Batch 1.5（Phase 0 产出）

> 来源：`@project-audit.mdc` + `docs/PROJECT_AUDIT_PLAYBOOK.md` §十「Phase 0 — Audit Rules / Scope Freeze」
> 创建时间：2026-09-15 01:19 (UTC+8)
> 适用批次：**v3 第五批 / Batch 1.5 — MKT 多渠道重构**
> 上一阶段产出：`audit/baseline/preflight-batch-1.5.md`（Phase -1，594 行 / 8 项内容完整）
> 上一批次完结：`audit/reports/batch-1.4-summary.md`（EXT-19 33/33 Feature 完结）+ `audit/issues/issue-register.md` §十九（I038-I052）+ §二十（EN-09 I054 NEW 建议）
> 本批次起点 commit（沿用 Batch 1.2/1.3/1.4 freeze commit）：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`
> 用户决策来源：`audit/baseline/preflight-batch-1.5.md` §十（2026-09-15 01:16 用户确认 10 项）
> SoT 引用：`docs/ROLLBACK-6.2-mkt_ddl.md`（MKT-002 SoT）+ git status（MKT 改动清单）— ⚠️ **无 .mdc Operational Notes**
> 模板链路：`preflight-batch-1.5.md` §八 → 本文件 §九（引用，不复制）

---

## 〇、文档说明

Phase 0 **不列 Feature**（那是 Phase 1）。Phase 0 只做四件事：

1. 冻结规则（Feature ID / L0-L5 / Result 子标签 / Evidence 目录 / SoT 引用）
2. 冻结范围（本批次涉及哪些模块 + 重构边界）
3. 确认能力边界（Cursor 能不能做什么）
4. 引用 preflight-batch-1.5.md 的已知约束（不复制内容，仅引用）

**用户决策已落档（2026-09-15 01:19）**：10 项决策已确认（详见 §十）。

**关键约束（本批次独有）**：
- ⚠️ **MKT 是首个重构批次**（区别于前四批次的新增 / 修改 / 集成类别）
- ⚠️ **Phase 3.5 Diff Audit 8 项强制**（@project-audit.mdc 规则 14）
- ⚠️ **重构中间态**：旧 `MarketActivity*` 5 个 Java 类全部存在 + 新 9 个 Mkt*Controller 已建 + `market_activity` 表未 DROP（Q3 第 6 次强调不跑迁移）
- ⚠️ **审计阶段只记录不修复**（规则 6 + Q2/Q3/Q4 决策）

---

## 一、项目定位（QiaoPM 美业 SaaS — Batch 1.5 视角）

| 项 | 值 |
|---|---|
| **项目名** | QiaoPM（美业 SaaS 消费收银会员管理系统）|
| **代码仓库** | `D:\QiaoPM`（workspace root）|
| **业务范围** | 美容院 / 美发店 / 美甲美睫 / SPA / 会所 / 医美 |
| **核心特征** | 高频预约、套餐/次卡依赖、会员复购驱动、私域运营强 |
| **PRD** | `docs/PRD-美业SaaS系统功能大全.md`（v1.0, 14,190 B, 2026-07-31）|
| **标准模块** | M01-M12（预约/收银/会员/套餐/储值/项目/库存/员工/营销/评价/报表/连锁）|
| **进阶模块** | M13-M18（智能排班/动态定价/客户360/自动化营销/视觉档案/电子知情同意）|
| **本批次定位** | **MKT 多渠道重构**（**架构治理层**，**首个重构批次**，非业务主线模块）|
| **本批次与业务模块关系** | 覆盖 M09（营销渠道）+ M10（会员中心 — 积分）+ M11（订单中心 — 促销）+ M17（自动化营销 — ChannelDispatcher）；服务 9 个 Mkt*Controller（git status 漏列 3 个，实际 9 个：MktChannel / MktConversion / MktJourney / MktTemplate / MktTouchTask / MktEffect / +MktCoupon / +MktPromotion / +MktPoints）|
| **重构策略** | **中间态**：旧 `MarketActivity*` 5 个 Java 类未删 + 新 9 个 Mkt*Controller 已建 + `market_activity` 表未 DROP；Batch 1.5 期间此中间态维持 |
| **优先级分布（沿用 v3）** | P0 × 5 / P1 × 6 / P2 × 6 / P3 × 1 |
| **支付通道（沿用 v3）** | 微信 / 支付宝 / 银联云闪付 / 数字人民币 2.0 / 花呗 / Stripe / Square / PayPal / Gift Card / BNPL |

**Batch 1.5 Phase 7 验收范围**：以 `docs/ROLLBACK-6.2-mkt_ddl.md`（MKT-002 SoT）+ git status 为 SoT（⚠️ **无 .mdc Operational Notes** — 前四批次都有 .mdc，本批次退化）；Feature 清单由 Phase 1 输出，本阶段不预判。

---

## 二、验收范围（Batch 1.5）

### 2.1 涉及模块（MKT 多渠道重构）

| 模块 | 主要改动 / 验证范围 |
|---|---|
| **新 9 个 Mkt*Controller**（含 git status 漏列 3 个）| `MktChannelController`（5 端点：list / POST / PUT / DELETE / health-check）+ `MktConversionController` + `MktJourneyController` + `MktTemplateController` + `MktTouchTaskController` + `MktEffectController` + `MktCouponController`（git status 漏列）+ `MktPromotionController`（git status 漏列）+ `MktPointsController`（git status 漏列）|
| **`ChannelDispatcher` 完善 + 5 个 ChannelSender** | `service/channel/ChannelDispatcher.java` 分发逻辑 + 5 个 sender（SmsSender / EmailSender / MiniProgramSender / InAppSender / WechatOfficialAccountSender，沿用 EXT-19）|
| **`MarketingAutomationService`** | 渠道编排自动化（与 `mkt_channel_config` 表关联）|
| **`migrate_mkt_002_drop_market_activity.sql` 准备** | 主动作：探测表存在 → 探测行数 → RENAME → DROP（**未跑，Q3 第 6 次强调**）|
| **`migrate_mkt_002_rollback.sql` 准备** | 回滚预案：探测归档表 → 反向 RENAME（**未跑，对称性已确认**）|
| **`docs/ROLLBACK-6.2-mkt_ddl.md` SoT** | T08-Q1 决策依据 + 90 天观察窗口 |
| **旧 `MarketActivity*` 5 个 Java 类未删**（重构中间态）| `entity/MarketActivity` + `mapper/MarketActivityMapper` + `service/MarketActivityService`（interface）+ `service/impl/MarketActivityServiceImpl` + `controller/MarketActivityController` — **仍存在，Phase 3.5 Diff Audit 必查** |
| **`BusinessPolicyDispatchServiceImpl` 仍 grep 引用 MarketActivity** | 注释说"已无读写"，**实际调用链需 Phase 3 全文核实** |
| **重构存量审计** | Phase 3.5 Diff Audit 8 项（详见 §十一）|

### 2.2 不在本批次范围（明确排除）

| 类别 | 模块 | 排除原因 |
|---|---|---|
| **受保护页面** | `/dashboard`, `/mh/visual`, `/mh/consent` | 用户已实测通过，按 `.cursor/rules/no-touch-verified-pages.mdc` 只观察不修复；**⚠️ Phase 2 必查 dashboard 是否调用 ChannelDispatcher（间接风险）**|
| **EXT-19 多语言预约确认** | `OverseasBooking` / `IntlBookingService` / 6 locale / 5 sender / i18n / webhook | Batch 1.4 已完结；Batch 1.5 与 EXT-19 共用 5 ChannelSender（MKT 不修改 sender 实现）|
| **EXT-24 CRM 集成** | `crm_integration` / `crm_contact_map` / `crm_sync_log` | Batch 1.1 已完结；Batch 1.5 不触达 |
| **EXT-23 Plugin Runtime** | `plugin_runtime_state` / `plugin_install` / pf4j | Batch 1.2 已完结；Batch 1.5 不触达 |
| **EXT-22 Google Maps + 有赞** | `google_maps_config` / `ecommerce_platform` / 有赞 webhook | Batch 1.3 已完结；Batch 1.5 不触达 |
| **共用 entity/table** | `mkt_channel_config` / `pos_store` / `member_message` / `overseas_booking` | Phase 1 期间明确边界（用户决策 #10）；`mkt_channel_config` 与 EXT-19 共用，Phase 1 grep 确认调用方 |
| **历史模块** | M01-M18 中除上述外的所有模块 | 不在本批次；待后续批次（v3 第六批起）评估 |
| **基础设施** | Redis / MySQL 配置 / CI 流水线 | 基础设施层不在本批次审计 |

### 2.3 MKT 重构详细验证范围（含 9 个 Mkt*Controller + 中间态）

| 层级 | 文件 | 验证范围 |
|---|---|---|
| **新 Controller（6 + 3 = 9）**| `controller/MktChannelController.java` | `@RequestMapping("/mkt/channel")` + 5 端点（已确认：`/list` `POST /` `PUT /{id}` `DELETE /{id}` `POST /health-check`）+ ChannelDispatcher 注入 |
| 新 Controller | `controller/MktConversionController.java` | 转化追踪 — 待 Phase 1 全文核实 |
| 新 Controller | `controller/MktJourneyController.java` | 用户旅程 — 待 Phase 1 全文核实 |
| 新 Controller | `controller/MktTemplateController.java` | 营销模板 — 待 Phase 1 全文核实 |
| 新 Controller | `controller/MktTouchTaskController.java` | 触达任务 — 待 Phase 1 全文核实 |
| 新 Controller | `controller/MktEffectController.java` | 效果分析 + dashboard — 待 Phase 1 全文核实 |
| 新 Controller（git status 漏列）| `controller/MktCouponController.java` | 优惠券 — Phase 1 全文核实 + 范围纳入确认 |
| 新 Controller（git status 漏列）| `controller/MktPromotionController.java` | 促销活动 — Phase 1 全文核实 + 范围纳入确认 |
| 新 Controller（git status 漏列）| `controller/MktPointsController.java` | 积分系统 — Phase 1 全文核实 + 范围纳入确认 |
| **channel 分发** | `service/channel/ChannelDispatcher.java` | 渠道分发逻辑 + 5 sender 注册 |
| **channel 接口** | `service/channel/ChannelSender.java` | `send(MktChannelTemplate, receiver, vars)` 接口契约 |
| **channel 短信** | `service/channel/SmsSender.java` | `channelType() = "SMS"`（沿用 EXT-19）|
| **channel 邮件** | `service/channel/EmailSender.java` | `channelType() = "EMAIL"` + mismatch demo 模式 |
| **channel 小程序** | `service/channel/MiniProgramSender.java` | `channelType() = "MINI_PROGRAM"` |
| **channel 站内信** | `service/channel/InAppSender.java` | `channelType() = "IN_APP"` |
| **channel 公众号** | `service/channel/WechatOfficialAccountSender.java` | `channelType() = "WECHAT_OA"` |
| **业务入口** | `service/MarketingAutomationService.java` | channelList + 渠道编排 + 与 `MktChannelConfig` 实体关联 |
| **实体（核心）**| `entity/MktChannelConfig.java` | 渠道配置实体（与 EXT-19 共用）|
| **SQL 迁移（前置）**| `db/sql/migrate_mkt_001_add_tenant.sql` | ✅ 已在 HEAD（已加 tenant_id） |
| **SQL 迁移（主动作）**| `db/sql/migrate_mkt_002_drop_market_activity.sql` | 49 行预览：探测 → COUNT(*) → RENAME → DROP（**未跑**，Q3 第 6 次强调）|
| **SQL 迁移（回滚）**| `db/sql/migrate_mkt_002_rollback.sql` | 30 行预览：存储过程 `migrate_mkt_002_rollback()`（**未跑**，作为 SoT 预案）|
| **SoT 文档** | `docs/ROLLBACK-6.2-mkt_ddl.md` | T08-Q1 决策依据 + 90 天观察窗口（`market_activity__archived_20260910` 归档表名严格一致 ✅）|
| **⚠️ 旧 Controller（中间态）**| `controller/MarketActivityController.java` | ⚠️ **未删** — Phase 3.5 Diff Audit 项 #2 路由冲突检查 |
| ⚠️ 旧 Service 接口 | `service/MarketActivityService.java` | ⚠️ **未删** — 10 个抽象方法保留（`activityPage` / `getActivity` / `createActivity` / `updateActivity` / `deleteActivity` / `stats` / `detail` / `toggleStatus` / `copyActivity` / `participants`）|
| ⚠️ 旧 Service 实现 | `service/impl/MarketActivityServiceImpl.java` | ⚠️ **未删** |
| ⚠️ 旧 Mapper | `mapper/MarketActivityMapper.java` | ⚠️ **未删** |
| ⚠️ 旧 Entity | `entity/MarketActivity.java` | ⚠️ **未删** |
| ⚠️ BusinessPolicyDispatchServiceImpl | `service/impl/BusinessPolicyDispatchServiceImpl.java` | ⚠️ **仍 grep 引用 MarketActivity**（注释说"已无读写"待 Phase 3 全文核实）|

### 2.4 重构类变更标记（⭐ 本批次独有 — 强制 Phase 3.5）

按规则 14：「重构类变更必须先跑 Phase 3.5 Diff Audit」。

| 项 | 值 |
|---|---|
| **是否重构** | ✅ **是**（首个重构批次） |
| **重构原因** | ① DROP `market_activity` 表 ② 新增 9 个 Mkt*Controller ③ 旧 `MarketActivity*` 5 个类应删未删（中间态）④ 新增 `migrate_mkt_002_*` 脚本族 |
| **Phase 3.5 必跑** | ✅ **强制**（详见 §十一）|
| **产出位置** | `audit/diff-audit/mkt-refactor.md` |
| **时间点** | Phase 1 末尾启动，**阻塞 Phase 2 / 5** |

---

## 三、Feature ID 规则（沿用 Batch 1.1/1.2/1.3/1.4 + 编号策略）

引用：`.cursor/rules/feature-id.mdc` + `docs/PROJECT_AUDIT_PLAYBOOK.md` §三

### 3.1 基础规则（沿用 Batch 1.1/1.2/1.3/1.4）

| 规则 | 内容 |
|---|---|
| **格式** | `F` + 三位数字（如 `F001`, `F114`, `F131`）|
| **拆分粒度** | 一个 Feature = 一个可独立验收的业务能力 |
| **拆分示例** | F001 登录 / F002 退出 / F003 用户列表 / F004 新增用户 / F005 编辑用户 / F006 删除用户 / F007 用户详情 / F008 权限配置 |
| **禁止** | 不要把"营销管理"作为一个 Feature / 不要把"订单模块"作为一个 Feature / 不要把一张数据库表作为一个 Feature |
| **Evidence 目录** | `audit/evidence/FXXX-<slug>/`（编号固定，缺一张即 UNVERIFIED）|

### 3.2 编号策略（**Batch 1.5 决策**）

| 选项 | 推荐 | 理由 |
|---|---|---|
| **A. 接续 Batch 1.4 的 F114 起** | ✅ **采用**（用户决策）| 全局唯一、便于跨批次交叉引用与 grep；Evidence 目录 / Issue 引用无歧义 |
| B. 从 F001 重新开始 | ❌ 不采用 | 跨批次引用需补 Batch 前缀，增加元数据开销 |
| C. 混合（保留 F001-F113 给前四批次，新模块用 F114+）| ❌ 不采用 | 编号跳跃不利于连续阅读 |

### 3.3 Batch 1.5 编号规则

| 维度 | 值 |
|---|---|
| **起始编号** | **F114**（接续 Batch 1.4 的 F081-F113）|
| **预估上限** | **F138**（按 9 个 Mkt*Controller × 3-5 endpoint + ChannelDispatcher + 重构存量审计估算 24-25 Features）|
| **预留上限** | **F145**（7 个缓冲编号，应对 Phase 1 触达时发现新 Feature + Phase 3.5 Diff Audit 触达）|
| **冲突解决** | Phase 1 期间如发现编号冲突，重命名旧 Feature（不要回插新 Feature 到中间）|
| **跨批次引用** | 直接用全局 F 编号（如 "F114 见 `audit/evidence/F114-mkt-channel-controller-list/`"），不需加批次前缀 |
| **跨 EXT-* 引用** | Feature ID 按 MKT 视角命名；引用 EXT-19 的代码路径注明 "EXT-19 边界" |

### 3.4 Issue 编号（沿用 Batch 1.1/1.2/1.3/1.4 + 本批次决策）

| 维度 | 值 |
|---|---|
| **Issue 编号** | 沿用全局 `I` + 三位数字（Batch 1.1 用到 I016，Batch 1.2 用到 I025，Batch 1.3 用到 I037，Batch 1.4 用到 I052）|
| **I053 状态** | ⚠️ **跳号已登记**（落档位置：§二十.20.1.1 — 段落登记，不创建独立 issue 文件）|
| **I054 状态** | ⚠️ **I054 NEW 维持**（仅 §二十.20.1 段落登记，不创建独立 issue 文件）|
| **Batch 1.5 起始** | **I055**（接续 I054 之后）|
| **预留上限** | **I072**（预留 18 个 Issue 编号应对 Phase 1-5 触达）|
| **漂移分类** | 按用户修正（详见 §五.2 沿用 I002/I017/I029 模式）：I055+ = SoT vs 代码 CONFLICT；表 MISSING 类用 I002/I017/I029/I038/I045 同模式（**统一口径：所有 MISSING 表都不跑迁移，Post-Audit 统一跑**）|

### 3.5 Phase 1 任务

按 §3.3 规则拆分 MKT 多渠道重构的 Feature 清单（编号 F114 起），覆盖：

- `MktChannelController`（5 endpoint：list / POST / PUT / DELETE / health-check）~5-7
- `MktConversionController`（转化追踪）~3
- `MktJourneyController`（用户旅程）~3
- `MktTemplateController`（营销模板）~3
- `MktTouchTaskController`（触达任务）~3
- `MktEffectController`（效果分析 + dashboard）~3-5
- `MktCouponController`（优惠券 — git status 漏列）~3-5
- `MktPromotionController`（促销活动 — git status 漏列）~3-5
- `MktPointsController`（积分系统 — git status 漏列）~3-5
- `ChannelDispatcher` 分发逻辑 + 5 Sender 注册 ~5
- `MarketingAutomationService` 业务入口 ~3
- `MktChannelConfig` 实体（沿用 EXT-19 共用）~1
- 重构存量审计（Phase 3.5 Diff Audit 触达）~5-8

**估算合计**：**~42-50 Features**（F114-F138 范围），预留 F139-F145 应对扩展。

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

**Phase 2-7 任务**：对每个 Feature 标注其期望达到的 L 等级。Batch 1.5 期间建议 MKT Feature **至少 L3**（静态链路通），Mock 降级路径（WeChat / SMTP 无凭据）**争取 L4**（API 可调 + jshell 单元测试），真实链路（WeChat MP / SMTP 真实发送）**L5a/L5b 受凭据缺失限制降级为 UNVERIFIED**。

**重构批次特殊约束**：
- ⚠️ 旧 `MarketActivity*` 5 个类 + 新 9 个 Mkt*Controller **共存期间**，L1（编译通过）必须验证两组代码不冲突
- ⚠️ `MarketActivityController` 的旧路由与新 9 个 Mkt*Controller 路由可能冲突 → L3 静态链路通检查必须双向

---

## 五、Result 子标签确认

引用：`docs/PROJECT_AUDIT_PLAYBOOK.md` §五 + `.cursor/rules/project-audit.mdc` 规则 7

**最终 Result 只允许以下 10 种状态**（沿用 Batch 1.1/1.2/1.3/1.4）：

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

### 5.1 MKT 重构特殊 Result 判定建议

| MKT 特殊场景 | 建议 Result | 理由 |
|---|---|---|
| **9 个新 Mkt*Controller — 路由可调（业务链路完整）** | **VERIFIED** 或 **PARTIAL-NON-BLOCKER** | 取决于 Phase 5 调用是否暴露 TableNotFoundException（I054 NEW 推论）|
| **`ChannelDispatcher` 真实调用（无凭据）** | **UNVERIFIED** | 凭据缺失（沿用 D10 铁律）；Phase 5 真实链路验证标记为 UNVERIFIED |
| **`market_activity` 表未 DROP（迁移未跑）** | **MISSING → PARTIAL-BLOCKER** | DDL 写了但 DB 未跑（Q3 第 6 次强调）；Phase 5 维持，Post-Audit 统一跑 |
| **`mkt_channel_config` 表未重建（I054 NEW）** | **MISSING → PARTIAL-BLOCKER** | 同上模式（沿用 I002/I017/I029/I038/I045）|
| **其它 MKT 表（mkt_promotion / mkt_segment / mkt_audit_log / mkt_automation）未跑迁移** | **MISSING → PARTIAL-BLOCKER** | 待 Phase 5 MySQL MCP 验证 |
| **SoT vs 代码 CONFLICT（`docs/ROLLBACK-6.2-mkt_ddl.md` vs 代码）** | **中立标 I055+** | 沿用 I001/I018/I026/I027/I030 模式；Fix Phase = Post-Audit 人类决策 |
| **旧 `MarketActivity*` 5 个类未删（重构中间态）** | **PARTIAL-NON-BLOCKER + 重构存量标注** | Phase 3.5 Diff Audit 必跑；本批次不修业务代码 |
| **旧端点（`/market-activity/*`）仍被前端引用（如有）** | **PARTIAL-NON-BLOCKER + Shadow** | Phase 1 grep 确认；前端是否已迁移到 `/mkt/*` 待核 |
| **`BusinessPolicyDispatchServiceImpl` 仍 grep 引用 MarketActivity** | **PARTIAL-NON-BLOCKER + 中间态标注** | Phase 3 全文核实 |
| **真实凭据缺失**（WeChat MP / SMTP） | **强制 UNVERIFIED** | 沿用前四批次；不允许在 Mock 降级下声称 VERIFIED |
| **I050 ChannelDispatcher.healthCheckAll tenant_id=1L 硬编码** | **PARTIAL-NON-BLOCKER + 死代码** | 沿用 I050；Phase 3 死代码审查 |
| **mockChannel fallback / @ConditionalOnMissingBean 冲突** | **Phase 3 静态扫描** | 沿用 I012 模式（CRM Mock 激活冲突）|
| **I047 CRITICAL-P1**（沿用 EXT-19）| **MKT 可能再犯**（catch 吞异常模式）| Phase 3 警惕 |
| **Phase 3.5 Diff Audit 8 项触达** | **每个 Feature 必检**：旧调用链 / 路由冲突 / 前端引用 / SoT 对称 | 详见 §十一 |

### 5.2 漂移分类原则（**用户统一口径 — 关键** — 第四批次第六次强调）

> ⚠️ **用户 2026-09-15 01:19 强调（第六次）**：所有 MISSING 表统一不跑迁移，Post-Audit 统一跑。
>
> 漂移分类原则（沿用 Batch 1.1/1.2/1.3/1.4 用户修正）：
> ① 涉及"表缺失"的 → I002/I017/I029/I038/I045 模式（MISSING，跑迁移即解，**统一 Post-Audit 跑**）
> ② 涉及"SoT 描述 vs 代码实现不一致"的 → I001/I018/I026/I027/I030 模式（CONFLICT，中立标）
> ③ 涉及"重构中间态"的 → 新分类（本批次独有）：**旧类残留 + 新类已建 + 表未迁移** → **PARTIAL-NON-BLOCKER + 中间态标注**（不视为 BROKEN，也不视为 MISSING）
> ④ 不混为一谈

**漂移清单分类**（沿用 Batch 1.4 §五.2 + preflight-batch-1.5.md §七）：

| # | 漂移项 | 漂移类型 | Issue 编号 | 处理 |
|---|---|---|---|---|
| 1 | `market_activity` 表未 DROP（迁移未跑）| **I002/I017/I029/I038/I045 MISSING 模式** | I055+（待登记）| **统一 Post-Audit 跑迁移**（Q3 第 6 次强调）|
| 2 | `mkt_channel_config` 表可能未重建（I054 NEW）| **MISSING** | I054 NEW（已登记，保持段落）| 同上 |
| 3 | 其它 MKT 表（mkt_promotion / mkt_segment / mkt_audit_log / mkt_automation）| **MISSING** | I055+（待登记）| 同上 |
| 4 | SoT vs 代码 CONFLICT（待 Phase 1 全文核实）| **中立标** | I055+ | Fix Phase = Post-Audit 人类决策 |
| 5 | **重构中间态**：旧 `MarketActivity*` 5 个类未删 | **新分类：PARTIAL-NON-BLOCKER + 中间态标注** | I055+（待登记）| Phase 3.5 Diff Audit 必跑；本批次不修业务代码（规则 6）|
| 6 | catch 吞异常 / rollbackFor 缺失（I047 / I009 模式）| **EXT-19 模式沿用** | I055+（待登记）| 中立标 |

### 5.3 严格禁止

- 没有运行证据只能标 UNVERIFIED（规则 3 + 9）
- 不允许出现 VERIFIED 但缺 traceId / DB row 的情况
- **MKT 特殊约束**：
  - **真实凭据缺失**（WeChat MP / SMTP）→ **强制 UNVERIFIED**（不允许在 Mock 降级下声称 VERIFIED）
  - **SoT 漂移中立标**（I055+）→ **不允许预设方向**（既不写"SoT 错"也不写"代码错"）
  - **表 MISSING**（DDL 写了但 DB 没建）→ **不允许用 UNVERIFIED 掩盖**（I002/I017/I029/I038/I045 模式：跑迁移即解，**统一 Post-Audit 跑**）
  - **统一口径（第六次强调）**：所有 MISSING 表（`market_activity` / `mkt_channel_config` / `mkt_promotion` / `mkt_segment` / `mkt_audit_log` / `mkt_automation` / `crm_contact_map` / `plugin_runtime_state` / `google_maps_config` / `google_maps_geocode_log` / `webhook_msg_log` / `overseas_booking` / `member_message`）→ **Post-Audit 统一跑迁移**
  - **重构中间态**（旧类残留）→ **不允许说成 BROKEN / MISSING**（既不是 bug 也不是缺失，是已知中间态 + Phase 3.5 Diff Audit 必须量化）

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

### 6.2 MKT 重构特殊 Evidence 要求

| MKT 场景 | 必备文件 | 验证内容 |
|---|---|---|
| **每个 Feature** | `traceability-matrix.md` | 路由 → controller → service → mapper → SQL/表 → DTO 全栈追溯 |
| **MktChannelController 5 endpoint Feature** | `runtime-evidence.md` 含 `mkt-channel-list.txt` + `mkt-channel-create.txt` + `mkt-channel-update.txt` + `mkt-channel-delete.txt` + `mkt-channel-health-check.txt` | 5 端点 mock 链路可调（含 tenantId 隔离）|
| **9 个 Mkt*Controller Controller Feature** | `traceability-matrix.md` + `runtime-evidence.md` | 9 个 Controller 路由注册 + 编译成功 |
| **`ChannelDispatcher` Feature** | `runtime-evidence.md` 含 `channel-dispatcher-routing-test.txt` | mock 链路可调（含 5 sender 注册验证）|
| **5 个 ChannelSender Feature**（沿用 EXT-19）| `runtime-evidence.md` 含 5 sender 单元测试 | mock 链路可调（无真实凭据 → UNVERIFIED）|
| **`MarketingAutomationService` Feature** | `runtime-evidence.md` 含 `marketing-automation-test.txt` | 业务链路可调 |
| **`MktChannelConfig` entity Feature（沿用 EXT-19）**| `db-evidence.sql` 含 `mkt-channel-config-schema.txt` | dev DB 中表存在/缺失 + 字段对齐（**I054 NEW 预期 MISSING**）|
| **`market_activity` 表（迁移未跑）**| `db-evidence.sql` 含 `market-activity-still-exists.txt` + `migrate_mkt_002_not_applied.txt` | **不跑迁移记录**（Q3 第 6 次强调；记录 DDL 已写 + DB 未跑 + 旧 9 行 RENAME 计划 + 90 天观察窗口）|
| **`mkt_promotion` / `mkt_segment` / `mkt_audit_log` / `mkt_automation` 表** | `db-evidence.sql` 含 4 个 `*-schema.txt` | Phase 5 MySQL MCP 验证存在性 |
| **重构中间态审计（Phase 3.5 触达）**| `audit/diff-audit/mkt-refactor.md`（8 项 Diff Audit）| 详见 §十一 |

### 6.3 audit/ 目录骨架（Batch 1.5 沿用 v3）

| 子目录 | 职责 | Batch 1.5 产出 |
|---|---|---|
| `audit/baseline/` | 基线快照（Phase -1/0 产出，不可变）：`preflight-batch-1.5.md` + **`scope-freeze-batch-1.5.md`（本文件）**| 本批次已落档 |
| `audit/evidence/F114-.../ ~ F145-.../` | 每 Feature 证据沉淀 | Phase 1 期间按编号创建 |
| `audit/scripts/` | 8 个守门脚本（g1-g8）— **未实现**（preflight §六）| Phase 1-2 期间补（用户决策 #6）|
| `audit/static-gap/` | Phase 3 静态差距扫描结果 | Phase 3 期间产出 |
| `audit/runtime/` | Phase 4 运行时计划 + Phase 5 验证 | `audit/runtime/plan-batch-1.5.md`（待写）|
| `audit/diff-audit/` | **Phase 3.5 Diff Audit 产出（⭐ 重构批次特有）**| `audit/diff-audit/mkt-refactor.md`（待 Phase 1 末写）|
| `audit/issues/issue-register.md` | Issue 汇总（沿用 Batch 1.1/1.2/1.3/1.4，单一文件）| §二十一 起追加 I055+；§二十.20.1.1 登记 I053 跳号 |

### 6.4 回归对照三件套（规则 13）

每次验证必须绑定：

- **commit hash**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2/1.3/1.4 freeze commit，即本批次起点）+ `656caa72341c96d1b2e703dde993603dda9eebe1`（v3 整体 baseline）+ `12a97ea57a98d268c864fc7c010b82b305d7bb93`（Batch 1.1 freeze commit）
- **DB snapshot**：`audit/baseline/db-snapshot.sql`（417 KB / 270 表 / schema-only）— **注意 `market_activity` 表存在（旧）+ `mkt_channel_config` 表可能不存在（I054 NEW）**
- **env.md**：`audit/baseline/env.md`（9,951 B）

---

## 七、SoT 确认：`docs/ROLLBACK-6.2-mkt_ddl.md` + git status（**无 .mdc** ⚠️）

引用：`.cursor/rules/project-audit.mdc` 规则 11

### 7.1 SoT 规则

> 「审计结论与 EXT-* Operational Notes 冲突时，以 EXT-* 为准并标 ISSUE。」

### 7.2 ⚠️ MKT 无专属 .mdc Operational Notes（本批次退化）

| 批次 | 模块 | 专属 .mdc / Operational Notes |
|---|---|---|
| **Batch 1.1** | EXT-24 CRM 集成 | ✅ `ext-24-crm-integration.mdc` |
| **Batch 1.2** | EXT-23 Plugin Runtime | ✅ `ext-23-plugin-runtime.mdc` |
| **Batch 1.3** | EXT-22 Google Maps + 有赞 | ✅ `ext-22-googlemaps-youzan.mdc` |
| **Batch 1.4** | EXT-19 多语言预约确认 | ✅ `ext-19-operational-notes.mdc` |
| **Batch 1.5** | **MKT 多渠道重构** | ❌ **无 .mdc**（本批次独有）|

**SoT 退化路径**：
1. `docs/ROLLBACK-6.2-mkt_ddl.md`（MKT-002 SoT — 已确认存在）
2. `git status` MKT 改动清单（6+3=9 个新 Controller + 2 SQL 脚本 + 1 ROLLBACK 文档）
3. 9 个 Mkt*Controller 源码注释（MktChannelController 已确认含「路由清单」注释）
4. `MktChannelController.java` 已确认含 `marketingAutomationService.channelList()` 调用

**后果**：
- Phase 1 Feature Inventory 需**自建骨架**，不能照搬前四批次的 Operational Notes §X 模板
- Phase 2 / 3 期间建议补建 `ext-mkt-multichannel.mdc`（沿用前四批次 Operational Notes 格式 — 用户决策 #3 Post-Audit 排期）

### 7.3 SoT 内容覆盖（`docs/ROLLBACK-6.2-mkt_ddl.md`）

| § | 主题 | 内容要点 |
|---|---|---|
| **§1** | 决策依据 | 生产 market_activity 0 行 + Java 端无引用 + 替代表 mkt_promotion + 风险等级低 |
| **§2** | 主动作（migrate_mkt_002_drop_market_activity.sql）| ① 探测表存在 → ② 探测行数 → ③ RENAME → ④ DROP |
| **§3** | 回滚预案（migrate_mkt_002_rollback.sql）| 90 天观察窗口（2026-09-10 → 2026-12-09）触发条件（下游 BI / 生产遗漏 / 业务部门）|

### 7.4 Batch 1.5 审计优先级

1. **`docs/ROLLBACK-6.2-mkt_ddl.md`**（事实来源 — 优先级最高）
2. `git status` MKT 改动清单
3. 9 个 Mkt*Controller 源码注释（`MktChannelController.java` 已确认含路由清单）
4. `docs/PROJECT_AUDIT_PLAYBOOK.md`（体系规则）
5. `audit/issues/issue-register.md`（历史 Issue 关联 — I047 / I050 / I054 NEW 沿用）
6. `audit/baseline/preflight-batch-1.5.md`（已知约束）
7. `backend/src/main/java/com/meiyetec/{controller,service,entity,mapper}/Mkt*` + `service/channel/*Sender`（实际代码）
8. `backend/src/main/resources/db/sql/migrate_mkt_*`（迁移脚本）

### 7.5 SoT 已加载确认

- ✅ `docs/ROLLBACK-6.2-mkt_ddl.md` 已确认存在（preflight §七.2.4 已确认）
- ✅ preflight §七.5 Phase 3.5 Diff Audit 8 项清单已落地
- ⚠️ Phase 0 期间需再次确认 SoT 与最新代码版本一致（preflight §七.2.4 验证清单）

---

## 八、能力边界确认（Cursor 不能 / 只能）

引用：`docs/PROJECT_AUDIT_PLAYBOOK.md` §十八 + `.cursor/rules/project-audit.mdc` 规则 9

### 8.1 Cursor **不能**做的事

| ❌ 不能 | 原因 |
|---|---|
| 真打开浏览器点击 | 无 Playwright MCP（沿用 Batch 1.1/1.2/1.3/1.4 决策）|
| 连接生产数据库执行查询 | 无 MySQL MCP 受限时的能力边界 |
| 调用 WeChat 订阅消息 API 真实接口 | **WeChat appId/appSecret 缺失**（沿用 EXT-19 Mock 降级）|
| 发送真实 SMTP 邮件 | **SMTP 凭据缺失**（沿用 SoT §0.2 mismatch demo 模式）|
| 注册有赞 push_url 到有赞后台 | 需人工到有赞开发者后台操作（Batch 1.3 范围，本批次不触达）|
| 跑 `migrate_mkt_002_drop_market_activity.sql` 迁移 | **用户决策 Q3 第 6 次强调不跑** |
| 跑 `mkt_channel_config` 表重建迁移 | **用户决策 Q3 第 6 次强调不跑**（同模式）|
| 判断业务是否符合 PRD | MKT 不在 PRD 主线；`docs/ROLLBACK-6.2-mkt_ddl.md` 为 SoT |
| 替代人工验收 | 最终验收必须人工签收 |
| 在没有运行证据时声称 VERIFIED | 规则 3 + 9 硬性要求 |
| **不修业务代码** | **规则 6 + 本批次 Q2/Q3/Q4 决策**（审计阶段只记录不修复）|

### 8.2 Cursor **只能**做的事

| ✅ 能做 | 方式 |
|---|---|
| 静态分析代码 | grep / glob / Read 文件内容 |
| 生成测试代码和 SQL | 写文件 + 给建议 |
| 根据看到的内容推断 | 在已知 MKT 上下文下的合理推断 |
| 标注 UNVERIFIED | 显式标注，不掩盖缺失 |
| **执行 jshell 单元测试** | 直接调用 `SmsSender.render(var)` / `EmailSender` mismatch demo 模式 / 5 channel 验证 |
| **生成 Mock 渠道发送样本** | 模拟 5 个 channel sender 的 mock payload + 验证返回值 |
| **Phase 5 期间 grep DB schema 验证** | MySQL MCP 验证 `market_activity` / `mkt_channel_config` 表存在性 + `tenant_id` 列对齐（**仅验证不修复**）|
| **Phase 3.5 Diff Audit 8 项执行** | grep / 全文读 / Spring 启动测试 / curl 验证 |
| **I053 跳号登记** | 在 §二十.20.1.1 段落登记跳号事实，不创建独立文件 |

### 8.3 本批次降级方案（沿用 Batch 1.1/1.2/1.3/1.4 + 用户决策）

| 工具/能力 | 状态 | 降级方案 |
|---|---|---|
| **MySQL MCP** | ✅ 已配置 | 正常使用（**仅验证不修复** — 验证 `market_activity` / `mkt_channel_config` 表存在性 + 字段对齐，但**不跑迁移**）|
| **Playwright MCP** | ❌ 未配置（沿用 Batch 1.1/1.2/1.3/1.4 决策）| Phase 5 E2E 降级为：① 生成 Playwright 脚本供用户手动执行；② 用户手动贴回 evidence；③ 静态抓 HTML + curl API + Vitest |
| **Redis** | ❓ 未验证（沿用决策）| 不探测；运行时 NPE 风险由 Phase 5 暴露 |
| **WeChat MP API 真实调用** | ❌ **凭据缺失**（沿用 EXT-19 Mock 降级）| Mock 降级（D10 铁律）；jshell 单元测试验证算法；真实链路 Phase 5 UNVERIFIED |
| **SMTP 真实发送** | ❌ **凭据缺失**（沿用 EXT-19 mismatch demo 模式）| mismatch demo 模式（无租户配置时走 demo 或全局 spring.mail.*）；观测日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode` |
| **`market_activity` 表 DROP 迁移** | ❌ **不跑**（Q3 第 6 次强调）| Phase 5 仅 MySQL MCP 验证存在；Post-Audit 统一跑批 |
| **`mkt_channel_config` 表重建迁移** | ❌ **不跑**（Q3 同模式）| 同上 |
| **`mkt_promotion` / `mkt_segment` / `mkt_audit_log` / `mkt_automation` 表迁移** | ❌ **不跑**（Q3 同模式）| 同上（统一口径第三/第四/第五/第六次强调）|
| **守门脚本 g1-g8** | ❌ 未实现（沿用决策 #6）| Phase 1-2 期间补 |
| **MKT 重构特定守门脚本 g20-g23** | ⚠️ 建议（preflight §六.2）| Phase 1 期间评估是否实现 |

### 8.4 Batch 1.5 评估期间需要用户手动配合的事项

| 事项 | 触发 Phase | 用户配合内容 |
|---|---|---|
| 启动后端（含 MKT 9 Controller + ChannelDispatcher + 5 sender 注册 + 重构中间态验证）| Phase 5 | 启动日志 + 9 Controller 路由成功 + 5 sender 注册 |
| 跑 jshell `SmsSender.render(var)` 单元测试 | Phase 5（沿用 Batch 1.2 Phase 5B 模板）| 验证占位符替换算法 |
| 跑 jshell `EmailSender` mismatch demo 模式测试 | Phase 5 | 验证 mismatch 日志关键词 + demo 模式分支 |
| Mock 渠道发送样本（5 sender × 9 Controller 触发）| Phase 5 | 生成 mock payload → 调 `ChannelDispatcher.dispatch()` 期望正常路由 |
| Phase 3.5 Diff Audit 8 项执行 | Phase 1 末 / Phase 5 | 旧类调用链 grep + 路由冲突测试 + 前端引用扫描 + Spring 路由表验证 |
| 跑 MySQL MCP 验证 `market_activity` 表存在性 | Phase 5 | 验证 DDL 存在 + DB 中表存在/缺失 + 9 列对齐（**仅验证不修复**）|
| 跑 MySQL MCP 验证 `mkt_channel_config` / `mkt_promotion` / `mkt_segment` / `mkt_audit_log` / `mkt_automation` 表存在性 | Phase 5 | 验证 MKT 依赖表（**仅验证不修复**）|
| 跨租户访问测试（如可达）| Phase 5 | 普通租户调别人 /mkt/* → 期望 `BizCode.FORBIDDEN` |
| WeChat / SMTP 真实凭据注入（如有）| Phase 5 | 注入凭据 + 调 5 channel sender 期望真实响应 |
| **不跑任何迁移脚本** | 全部 | 用户决策 Q3 第 6 次强调 |

---

## 九、已知约束（引用 preflight-batch-1.5.md §八）

**本节仅引用，不复制内容**。详见 `audit/baseline/preflight-batch-1.5.md` §八（line 287-388）。

### 9.1 market_activity 表（Q3 第 6 次强调不跑迁移）

> ⚠️ **用户 2026-09-15 01:19 强调（Q3 第 6 次）**：所有 MISSING 表统一不跑迁移，Post-Audit 统一跑。
>
> Phase 0 沿用 Batch 1.4 §五.2 用户修正：漂移分类原则明确 — **表 MISSING = I002/I017/I029/I038/I045 模式（跑迁移即解，统一 Post-Audit 跑）**。

| 表 | 创建迁移脚本 | 实体类 | DB 实际状态 | 处理 |
|---|---|---|---|---|
| `market_activity` | `migrate_mkt_002_drop_market_activity.sql`（已写但未跑）| ✅ `entity/MarketActivity.java` | ✅ **仍在 DB**（迁移未跑）| **不跑迁移**（Q3 第 6 次强调）；Phase 5 仅 MySQL MCP 验证存在；Post-Audit 统一跑批 |
| `mkt_channel_config` | 沿用 EXT-09（I054 NEW 推论未重建）| ✅ `entity/MktChannelConfig.java` | ⚠️ **可能未跑迁移**（I054 NEW 模式）| 同上 — 不跑迁移，Post-Audit 统一跑 |
| `mkt_promotion` | 沿用（基线 `init_mkt_promotion.sql`）| 待 Phase 1 grep | ⚠️ **可能未跑迁移** | 同上 |
| `mkt_segment` / `mkt_audit_log` / `mkt_automation` | 沿用 / 缺失迁移 | 待 Phase 1 grep | ⚠️ **未确认** | 同上 |

### 9.2 旧 `MarketActivity*` 5 个 Java 类（重构中间态）

> **重构批次独有**：本约束区别于前四批次（前三 / 第四批次均为新增 / 集成 / 修改类别）。

| 维度 | 详情 |
|---|---|
| **清单** | `entity/MarketActivity` + `mapper/MarketActivityMapper` + `service/MarketActivityService`（interface 10 个抽象方法）+ `service/impl/MarketActivityServiceImpl` + `controller/MarketActivityController` |
| **当前状态** | ⚠️ **5 个全部存在**（SoT 注释说"已无读写"，但类文件还在）|
| **5 个类的 Phase 1 任务** | 完整调用链扫描（grep Autowired + import + 静态编译证据 + `BusinessPolicyDispatchServiceImpl` 全文读）|
| **5 个类的 Phase 5 任务** | 旧 `/market-activity/*` 端点是否仍在 Spring 路由表（curl 测试）|
| **决策** | Phase 3.5 Diff Audit 必跑；**本批次不修业务代码**（规则 6）|

### 9.3 I054 NEW（mkt_channel_config 表缺失 — 已登记，保持段落）

| 维度 | 详情 |
|---|---|
| **Issue ID** | **I054 NEW（建议登记，落档到 §二十.20.1，未升级为正式 issue 文件）** |
| **类别** | MISSING（DB）|
| **严重度** | 🟠-中-高 |
| **影响 Feature** | F094（ChannelDispatcher.healthCheckAll）+ Batch 1.5 ChannelDispatcher 全部 |
| **Fix Phase** | Post-Audit（Batch 1.5 期间维持 MISSING 状态）|
| **Phase 5 影响** | Phase 5 真实调用 ChannelDispatcher.healthCheckAll → `TableNotFoundException` → 审计的有效负向证据（沿用 I002 / I017 / I029 / I038 / I045 模式）|

### 9.4 I047 CRITICAL-P1（IG webhook catch 吞异常，沿用）

> **沿用 EXT-19**：MKT 可能再犯（ChannelDispatcher / MarketingAutomationService 中的 catch 模式需 Phase 3 警惕）。

| 维度 | 详情 |
|---|---|
| **Issue ID** | I047 |
| **严重度** | 🔴 **CRITICAL-P1** |
| **Phase 1 / 3 影响** | MKT ChannelDispatcher / MarketingAutomationService 中的 catch 模式需 Phase 3 警惕（避免重犯 Fake Success / Fake Failure）|
| **Fix Phase** | Post-Audit（Q2 决策）|

### 9.5 I050 死代码（ChannelDispatcher.healthCheckAll tenant_id=1L 硬编码）

| 维度 | 详情 |
|---|---|
| **Issue ID** | I050 |
| **类别** | 死代码 / 演示模式例外（T07-D4）|
| **影响 Feature** | F094 + Batch 1.5 ChannelDispatcher 全部 |
| **Phase 3 影响** | ChannelDispatcher 多租户适配是 Phase 3 死代码 + 跨租户读风险审查重点（沿用 I019 模式）|
| **Fix Phase** | Post-Audit |

### 9.6 I053 跳号登记（用户决策 #8，本批次完成）

| 维度 | 详情 |
|---|---|
| **Issue ID** | I053（**跳号**，无独立文件，无段落登记）|
| **处理** | **§二十.20.1.1 段落登记**（落档位置）+ **不创建独立 issue 文件** |
| **段落登记模板** | `audit/issues/issue-register.md` §二十.20.1.1：「I053 跳号事实登记 — Batch 1.4 Phase 5 期间预留触达位但未触达 → I053 顺位空缺；后续批次视为已占用」 |
| **当前批次承担** | 用户决策 #8 批准 |

### 9.7 Phase 3.5 Diff Audit 必跑（⭐ 重构批次特有约束）

| 维度 | 详情 |
|---|---|
| **规则来源** | `@project-audit.mdc` 规则 14 |
| **触发条件** | 重构类变更（含 DROP 迁移 + 新 Controller + 旧 Controller 删除 + 接口签名变动）|
| **MKT 触发原因** | ① DROP `market_activity` 表 ② 新增 9 个 Mkt*Controller ③ 旧 `MarketActivity*` 5 个类应删未删 ④ 新增 `migrate_mkt_002_*` 脚本族 |
| **产出位置** | `audit/diff-audit/mkt-refactor.md` |
| **不跑后果** | 重构中间态的全部影响未量化 → Phase 5 E2E 盲测 → 审计结论不可信 |
| **决策** | Phase 1 末尾必跑，**不阻塞 Phase 0** 但阻塞 Phase 2（Traceability）+ Phase 5（E2E 设计）|

### 9.8 与前 4 批次独立性检查（引用 preflight §七.4）

| 检查项 | 结果 |
|---|---|
| **代码包共享** | ⚠️ **部分共享** — `MarketingAutomationService` + `ChannelDispatcher` + 5 个 `ChannelSender`（`service/channel/*Sender.java`）在 Batch 1.4 EXT-19 也使用，但属**不同职责**（EXT-19 用作发送确认 / MKT 用作营销触达）|
| **DB 表共享** | ⚠️ **部分共享** — `mkt_channel_config` 同时被 EXT-19（EmailSender/SmsSender 的 channelId 引用）+ MKT 多渠道（ChannelDispatcher）使用；I054 NEW 是共同阻塞项 |
| **配置共享** | ⚠️ `spring.mail.*` 全局配置（MKT Email channel）+ `mkt_channel_config.configJson`（租户级 SMTP）|
| **业务依赖** | ⚠️ MKT 渠道管理**不依赖** EXT-19/22/23/24，但共享基础设施 |
| **审计可分离性** | ✅ Feature Inventory 可分离（Phase 1 期间明确边界 — 按 MKT 视角划分 9 Controller）|

### 9.9 共用 entity/table 边界（用户决策 #10）

| 共享资源 | 所属 EXT-* | Batch 1.5 处理 |
|---|---|---|
| `MktChannelConfig` entity + `mkt_channel_config` 表 | EXT-09（已有）+ EXT-19（5 channel sender 复用）+ EXT-25（MKT）| **tenantId 隔离**（用户决策 #10）；Phase 1 期间 grep 确认边界；Feature ID 按 MKT 视角命名 |
| `pos_store` 表 | EXT-03（已有）+ EXT-19（StoreLookupService 复用）| 不被 MKT 使用；互不干扰 |
| `overseas_booking` 表 | EXT-15（已有）+ EXT-19 核心 | 不被 MKT 使用；互不干扰 |
| `member_message` 表 | EXT-19 + 不被 MKT 使用 | 互不干扰 |
| `spring.mail.*` 配置 | EXT-19 EmailSender + MKT Email channel | 共用全局配置；SMTP mismatch 降级模式共用 |
| 5 个 `ChannelSender`（SMS/EMAIL/MINI_PROGRAM/IN_APP/WECHAT_OA）| EXT-19 + MKT 共用 | **同一套 sender，多套触发逻辑**；MKT 不修改 sender 实现 |

### 9.10 受保护页面与 ChannelDispatcher 间接依赖（dashboard 渠道统计 — ⚠️ Phase 2 必查）

| 维度 | 详情 |
|---|---|
| **风险路径** | `/dashboard` → 渠道统计 → ChannelDispatcher.healthCheckAll → `mkt_channel_config`（I054 NEW 缺失）→ `TableNotFoundException` |
| **风险等级** | 🟡 **中**（dashboard 是受保护页面之一）|
| **Phase 2 任务**（**用户决策 #4 强制必查**）| 静态扫描 `frontend/meiyetec-web/src/views/dashboard/**` 是否有 `mktChannelApi.*` 或 `/mkt/channel/health-check` 引用 |
| **决策** | ⚠️ 若 dashboard 调用 → 受保护页面**间接风险**（即使不修业务代码，也需确认 dashboard 不显式调用 ChannelDispatcher）|

---

## 十、待用户确认项（**用户已确认** — 详见 preflight §十）

### 10.1 10 项决策（用户已确认 — 2026-09-15 01:19）

| # | 项 | 用户决策 | 当前状态 |
|---|---|---|---|
| **1** | **9 个 vs 6 个 Mkt*Controller 范围** | ✅ **9 个全纳入**（静态扫描为准；git status 漏列 MktCoupon/MktPromotion/MktPoints）| Phase 1 Feature Inventory F114 起估算 42-50 Features |
| **2** | **Phase 3.5 Diff Audit 8 项清单** | ✅ **全跑**（§七.5 + §十一）| Phase 1 末执行；产出 `audit/diff-audit/mkt-refactor.md` |
| **3** | **MKT .mdc Operational Notes 补写时机** | ✅ **Post-Audit** 排期 | Phase 1 / 2 期间不强求；Feature Inventory 自建骨架 |
| **4** | **dashboard 调用 ChannelDispatcher 必查** | ✅ **Phase 2 必查**（§八.10 + §九.10）| 受保护页面间接风险；Phase 2 静态扫描 `dashboard/**` |
| **5** | **commit 策略 C（stash）** | ✅ **接受** | Phase -1 完成后立即 `git stash push -m "after-phase-minus-1-clean-before-phase-0-batch-1.5"` |
| **6** | **g1-g8 守门脚本补齐时机** | ✅ **Phase 1-2 期间补** | 不阻塞 Phase 0 |
| **7** | **Phase 0 freeze commit 追加** | ✅ **接受** | 追加 `audit(freeze): Batch 1.4 完结 + Batch 1.5 启动` |
| **8** | **I053 跳号落档** | ✅ **§二十.20.1.1 段落登记，不创建独立文件** | Phase 0 期间同步更新 `issue-register.md` |
| **9** | **I054 NEW 是否升级独立文件** | ✅ **维持段落登记，不升级独立文件** | 保持 §二十.20.1 现状 |
| **10** | **mkt_channel_config 边界** | ✅ **tenantId 隔离，Phase 1 期间明确** | Phase 1 grep 确认；Feature ID 按 MKT 视角命名 |

### 10.2 用户第六次强调（关键 — 漂移统一口径）

| # | 项 | 用户强调 | 影响 |
|---|---|---|---|
| **A** | **所有 MISSING 表统一不跑迁移**（**第六次**）| "Q3 第 6 次强调：市场活动 + mkt 系列 + 累积 12+ 张表都不跑迁移，Post-Audit 统一跑" | 本批次 `market_activity` / `mkt_channel_config` / `mkt_promotion` / `mkt_segment` / `mkt_audit_log` / `mkt_automation` 不跑迁移；累计 12+ 张表（`crm_contact_map` / `plugin_runtime_state` / `google_maps_config` / `google_maps_geocode_log` / `webhook_msg_log` / `ecommerce_platform` 部分列 / `overseas_booking` / `member_message` / `mkt_*`）→ Post-Audit 统一跑批 |

### 10.3 审计阶段约束（统一 — 不可违反）

| 约束 | 来源 | 影响 |
|---|---|---|
| **审计阶段只记录不修复** | 规则 6 + Q2/Q3/Q4 决策 | 本批次**不修任何业务代码 / SQL / 配置**；所有发现记入 Issue Register；Fix Phase = Post-Audit |

### 10.4 默认决策影响

| 默认 | 影响 |
|---|---|
| **#1 9 个 Mkt*Controller 全纳入** | Phase 1 Feature Inventory 数量约 42-50（F114-F138 范围 + F139-F145 缓冲）|
| **#2 Phase 3.5 8 项必跑** | Phase 1 末执行，产出 `audit/diff-audit/mkt-refactor.md`；阻塞 Phase 2/5 |
| **#3 .mdc Post-Audit 补** | Phase 1 Feature Inventory 自建骨架；Phase 2 / 3 缺 SoT 引用 |
| **#4 dashboard ChannelDispatcher 调用必查** | Phase 2 静态扫描 `frontend/meiyetec-web/src/views/dashboard/**` |
| **#5 stash C** | Phase -1 完成后保持 working tree clean |
| **#6 g1-g8 Phase 1-2 期间补** | 不阻塞 Phase 0 |
| **#7 freeze commit 追加** | git history 清晰；Phase 0 scope-freeze 锚点明确 |
| **#8 I053 段落登记** | issue-register.md §二十.20.1.1 插入段落；不创建独立 issue 文件 |
| **#9 I054 NEW 维持** | 沿用 §二十.20.1 建议登记状态；不创建独立 issue 文件 |
| **#10 tenantId 隔离** | Phase 1 Feature ID 按 MKT 视角划分；与 EXT-19 共用 `mkt_channel_config` 按 tenantId 隔离 |

---

## 十一、⭐ Phase 3.5 Diff Audit 必跑（重构批次特有 — 强制）

> ⚠️ **MKT 是 v3 首个重构批次**。Phase 3.5 Diff Audit 是**强制 Phase**（@project-audit.mdc 规则 14）。

### 11.1 触发条件确认

| 项 | 验证 | 结果 |
|---|---|---|
| 重构类变更 | DROP `market_activity` 表 + 新增 9 个 Mkt*Controller + 旧 `MarketActivity*` 5 个类应删 + 新增 `migrate_mkt_002_*` 脚本族 | ✅ 触发 |
| Phase 3.5 必跑 | 沿用 Batch 1.1-1.4 无重构批次（新增 / 集成 / 修改）；本批次为首个重构 | ✅ 首次触发 |

### 11.2 8 项审计清单（与 preflight §七.5 完全一致）

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

### 11.3 产出与阻塞

| 维度 | 值 |
|---|---|
| **产出文件** | `audit/diff-audit/mkt-refactor.md` |
| **格式** | Markdown 8 节，每节覆盖 1 项审计结论（含证据命令输出 + 结论 + 联动 Feature）|
| **发布时间** | Phase 1 末尾（Feature Inventory 完成后立即触发，不阻塞 Phase 0）|
| **阻塞点** | ❌ **不阻塞 Phase 0**（本批次 Phase 0 仅冻结规则不列 Feature） |
| | ✅ **阻塞 Phase 2**（Traceability Matrix 需引用 Diff Audit 结论）|
| | ✅ **阻塞 Phase 5**（E2E 设计需依据 Diff Audit 触达点，避免盲测）|

### 11.4 8 项审计触达 Issue 预测（Phase 3.5 完成后预计登记）

| Diff Audit 项 | 预测 Issue | 严重度 | 处理 |
|---|---|---|---|
| #1 旧类 @Autowired 调用方 | I055+（PARTIAL-NON-BLOCKER + 中间态）| 🟡 中 | Phase 3 全文证实；立 PARTIAL-NON-BLOCKER 标 |
| #2 路由冲突（`@RequestMapping`）| I055+（PARTIAL-NON-BLOCKER + 路由冲突）| 🟠 中-高 | curl 验证；如冲突 → BROKEN 标记 |
| #3 前端引用 `market-activity` | I055+（PARTIAL-NON-BLOCKER + Shadow）| 🟡 中 | grep 验证；如仍引用 → Shadow + 路由不可删 |
| #4 `BusinessPolicyDispatchServiceImpl` 实际调用链 | I055+（沿用 I019 跨租户读风险）| 🟡 中 | 全文读 + grep |
| #5 9 Controller 路由注册成功 | — | — | Phase 5 必测 |
| #6 migrate_mkt_002 ↔ rollback 对称性 | — | 🟢 OK | 已确认严格对称 |
| #7 ROLLBACK-6.2 ↔ SQL 归档表名一致性 | — | 🟢 OK | 已确认严格一致 |
| #8 旧 Controller 仍在 Spring 路由表 | I055+（PARTIAL-NON-BLOCKER + 路由残留）| 🟡 中 | Phase 5 curl 验证 |

---

## 十二、Phase 0 准入自检

| 检查项 | 结果 |
|---|---|
| preflight-batch-1.5.md 已落档 | ✅ `audit/baseline/preflight-batch-1.5.md`（594 行）|
| 10 项决策已回答 | ✅ 用户 2026-09-15 01:19 确认 |
| 用户第六次强调漂移统一口径已落档 | ✅ §五.2 + §九.1 + §十.2 明确所有 MISSING 表 Post-Audit 跑迁移 |
| Phase 0 freeze commit 计划 | ✅ 沿用 Batch 1.1→1.2→1.3→1.4 freeze 模式（追加 `audit(freeze): Batch 1.4 完结 + Batch 1.5 启动`）|
| scope-freeze-batch-1.5.md 可读 | ✅（本文件）|
| 项目定位已明确 | ✅ §一 |
| 验收范围已冻结（含 9 个 Mkt*Controller + 中间态详细验证范围）| ✅ §二 |
| 重构类变更标记已识别（Phase 3.5 必跑）| ✅ §二.4 + §十一 |
| Feature ID 规则已确认（含编号策略 F114-F145）| ✅ §三 |
| Issue 编号已确认（I055 起 + I053 跳号 + I054 NEW 状态）| ✅ §三.4 |
| L0-L5 等级已确认 | ✅ §四 |
| Result 子标签已确认（含 MKT 重构特殊判定 + 漂移分类原则）| ✅ §五 |
| Evidence 目录已约定（含 MKT 重构特殊要求）| ✅ §六 |
| SoT 已确认（`docs/ROLLBACK-6.2-mkt_ddl.md` + git status，**无 .mdc**）| ✅ §七 |
| 能力边界已确认（含 WeChat / SMTP Mock 降级 + 不跑迁移）| ✅ §八 |
| 已知约束已引用（含 I055+ 中立标 + 重构中间态）| ✅ §九 |
| 待用户确认项已列出（10 项 + 用户第六次强调）| ✅ §十 |
| **Phase 3.5 Diff Audit 8 项**（⭐ 重构批次特有）| ✅ §十一（详细列出 + 阻塞 Phase 2/5）|

✅ **Phase 0 完成。可以进入 Phase 1（Feature Inventory — F114 起）。**

---

## 十三、本文件状态

| 项 | 值 |
|---|---|
| **状态** | 不可变（frozen）|
| **变更控制** | 任何改动需用户明确同意 |
| **下一阶段产出位置** | `audit/feature-inventory.md` §1.5（F114-F145 MKT Feature 清单）|
| **下一阶段 Prompt 模板** | 见 `docs/PROJECT_AUDIT_PLAYBOOK.md` §十「Phase 1」|
| **Phase 3.5 产出位置**（后续）| `audit/diff-audit/mkt-refactor.md` |
| **Phase 3.5 启动时间** | Phase 1 末尾（Feature Inventory 完成后立即触发）|

---

**Scope Freeze（Batch 1.5）完成。MKT 多渠道重构范围已冻结 + 10 项决策已落档 + F114-F145 编号策略已确认 + Issue I055 起 + I053 跳号（§二十.20.1.1）+ I054 NEW 状态维持 + 重构中间态已识别（旧 5 类未删 + 新 9 Controller 已建 + market_activity 表未 DROP）+ Phase 3.5 Diff Audit 8 项必跑（⭐ 首个重构批次）+ 所有 MISSING 表统一不跑迁移（Q3 第 6 次强调）+ WeChat/SMTP 凭据 Mock 降级 + freeze commit 策略 C 接受 + commit 策略 + 不进 Phase 1。⏸ 等用户确认 → 进入 Phase 1（Feature Inventory，F114 起）。**
