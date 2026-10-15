# Scope Freeze — Batch 1.4（Phase 0 产出）

> 来源：`@project-audit.mdc` + `docs/PROJECT_AUDIT_PLAYBOOK.md` §十「Phase 0 — Audit Rules / Scope Freeze」
> 创建时间：2026-09-14 15:45 (UTC+8)
> 适用批次：**v3 第四批 / Batch 1.4 — EXT-19 多语言预约确认**
> 上一阶段产出：`audit/baseline/preflight-batch-1.4.md`（Phase -1，458 行 / 8 项内容完整）
> 上一批次完结：`audit/reports/batch-1.3-summary.md`（EXT-22 25/25 Feature 完结）+ `audit/issues/issue-register.md` §十九（I026-I037 + I037 NEW CRITICAL-P0）
> 本批次起点 commit（沿用 Batch 1.2/1.3 freeze commit）：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`
> 用户决策来源：`audit/baseline/preflight-batch-1.4.md` §十（2026-09-14 15:35 用户确认 8 项）
> SoT 引用：`.cursor/rules/ext-19-operational-notes.mdc`（EXT-19 Operational Notes）
> 模板链路：`preflight-batch-1.4.md` §八 → 本文件 §九（引用，不复制）

---

## 〇、文档说明

Phase 0 **不列 Feature**（那是 Phase 1）。Phase 0 只做四件事：

1. 冻结规则（Feature ID / L0-L5 / Result 子标签 / Evidence 目录 / SoT 引用）
2. 冻结范围（本批次涉及哪些 EXT-* + 模块）
3. 确认能力边界（Cursor 能不能做什么）
4. 引用 preflight-batch-1.4.md 的已知约束（不复制内容，仅引用）

**用户确认（2026-09-14 15:45）**：8 项决策已落档（详见 §十）。

---

## 一、项目定位（QiaoPM 美业 SaaS — Batch 1.4 视角）

| 项 | 值 |
|---|---|
| **项目名** | QiaoPM（美业 SaaS 消费收银会员管理系统）|
| **代码仓库** | `D:\QiaoPM`（workspace root）|
| **业务范围** | 美容院 / 美发店 / 美甲美睫 / SPA / 会所 / 医美 |
| **核心特征** | 高频预约、套餐/次卡依赖、会员复购驱动、私域运营强 |
| **PRD** | `docs/PRD-美业SaaS系统功能大全.md`（v1.0, 14,190 B, 2026-07-31）|
| **标准模块** | M01-M12（预约/收银/会员/套餐/储值/项目/库存/员工/营销/评价/报表/连锁）|
| **进阶模块** | M13-M18（智能排班/动态定价/客户360/自动化营销/视觉档案/电子知情同意）|
| **本批次定位** | **EXT-19 多语言预约确认 + i18n 模板 + 多渠道发送**（**通知层**，非业务主线模块）|
| **本批次与业务模块关系** | `IntlBookingService.updateStatus` 自动触发多渠道确认（SMS / Email / MiniProgram / InApp / WechatOA）；服务 M01（预约管理）的 CONFIRMED 状态通知链路；间接服务 M09（营销渠道）|
| **优先级分布（沿用 v3）** | P0 × 5 / P1 × 6 / P2 × 6 / P3 × 1 |
| **支付通道（沿用 v3）** | 微信 / 支付宝 / 银联云闪付 / 数字人民币 2.0 / 花呗 / Stripe / Square / PayPal / Gift Card / BNPL |

**Batch 1.4 Phase 7 验收范围**：以 EXT-19 Operational Notes（`.cursor/rules/ext-19-operational-notes.mdc`）为 SoT，**PRD 不直接覆盖**；Feature 清单由 Phase 1 输出，本阶段不预判。

---

## 二、验收范围（Batch 1.4）

### 2.1 涉及模块

| EXT-* | 模块 | 主要改动 / 验证范围 |
|---|---|---|
| **EXT-19** | 多语言预约确认 | `OverseasBooking.miniProgramOpenId` 字段 + 6 个 locale i18n template（zh-CN / zh-TW / en-US / ja-JP / ko-KR / fr-FR）+ 5 个 channel sender（SmsSender / EmailSender / MiniProgramSender / InAppSender / WechatOfficialAccountSender）+ `BookingConfirmationService`（核心发送 + 渠道路由 + 聚合结果）+ `BookingConfirmationTemplateResolver`（i18n 加载 + locale 回退）+ `StoreLookupService`（pos_store 查询 + 5 分钟缓存）+ `IntlBookingService.updateStatus`（CONFIRMED 自动触发 sendConfirmation）+ Google/Instagram webhook 端点 |

### 2.2 不在本批次范围（明确排除）

| 类别 | 模块 | 排除原因 |
|---|---|---|
| **受保护页面** | `/dashboard`, `/mh/visual`, `/mh/consent` | 用户已实测通过，按 `.cursor/rules/no-touch-verified-pages.mdc` 只观察不修复 |
| **EXT-24 CRM 集成** | `crm_integration` / `crm_contact_map` / `crm_sync_log` | Batch 1.1 已完结；Batch 1.4 不触达 |
| **EXT-23 Plugin Runtime** | `plugin_runtime_state` / `plugin_install` / pf4j | Batch 1.2 已完结；Batch 1.4 不触达 |
| **EXT-22 Google Maps + 有赞** | `google_maps_config` / `ecommerce_platform` / 有赞 webhook | Batch 1.3 已完结；Batch 1.4 不触达 |
| **共用 entity/table** | `mkt_channel_config` / `pos_store` / `overseas_booking` | Phase 1 期间明确边界（用户决策 #8）；`mkt_channel_config` 与 `pos_store` 可能被 EXT-19 复用，Phase 1 grep 确认调用方 |
| **历史模块** | M01-M18 中除上述外的所有模块 | 不在本批次；待后续批次（v3 第五批起）评估 |
| **基础设施** | Redis / MySQL 配置 / CI 流水线 | 基础设施层不在本批次审计 |

### 2.3 EXT-19 详细验证范围

| 层级 | 文件 | 验证范围 |
|---|---|---|
| **Entity** | `entity/OverseasBooking.java` | `miniProgramOpenId` 字段（L21）+ 26 列 + 4 索引 |
| **booking 核心** | `booking/BookingConfirmationService.java` | `sendConfirmation(booking)` + `MP_TEMPLATE_ID = "BOOKING_CONFIRMATION"`（L25）+ `MP_VARIABLES_JSON`（L28）+ 5 渠道路由 |
| **booking 模板** | `booking/BookingConfirmationTemplate.java` | 渲染后模板 DTO（subject / sms / miniProgram / email）|
| **booking 解析器** | `booking/BookingConfirmationTemplateResolver.java` | `warmup()` 启动期预热 6 个 locale（L36-40）+ locale 回退链（`xx-YY → xx → en`）|
| **booking 结果** | `booking/BookingConfirmationResult.java` | 多渠道聚合结果 + `overallSuccess` 字段 |
| **booking 查询** | `booking/StoreLookupService.java` | `pos_store` 查询 + `ConcurrentHashMap<storeId, CacheEntry>` 5 分钟缓存 + 兜底常量（`美业旗舰店 / 400-888-8888`）|
| **channel 短信** | `service/channel/SmsSender.java` | `channelType() = "SMS"` + 阿里云短信 + `render(var)` 占位符替换 |
| **channel 邮件** | `service/channel/EmailSender.java` | `channelType() = "EMAIL"` + 真实 JavaMailSender SMTP + mismatch 降级 demo 模式（SoT §0.2）|
| **channel 小程序** | `service/channel/MiniProgramSender.java` | `channelType() = "MINI_PROGRAM"` + AccessToken 内存缓存（`TOKEN_CACHE_MS = 7100_000L`）|
| **channel 站内信** | `service/channel/InAppSender.java` | `channelType() = "IN_APP"` + database 持久化 |
| **channel 公众号** | `service/channel/WechatOfficialAccountSender.java` | `channelType() = "WECHAT_OA"` + 公众号推送 |
| **channel 分发** | `service/channel/ChannelDispatcher.java` | 渠道分发逻辑 |
| **channel 接口** | `service/channel/ChannelSender.java` | `send(MktChannelTemplate, receiver, vars)` 接口契约 |
| **业务入口** | `service/IntlBookingService.java` | `sendConfirmation` + `updateStatus`（CONFIRMED 自动触发）+ `/intl-booking/{id}/status` + `/intl-booking/{id}/send-confirmation` + `/intl-booking/{id}/confirmation-template` + `/intl-booking/list` + `/intl-booking/webhook/google` + `/intl-booking/webhook/instagram` |
| **i18n 模板** | `resources/i18n/template/zh-CN.json` | 简体中文（subject + sms + miniProgram + email）|
| **i18n 模板** | `resources/i18n/template/zh-TW.json` | 繁体中文 |
| **i18n 模板** | `resources/i18n/template/en-US.json` | 英文 |
| **i18n 模板** | `resources/i18n/template/ja-JP.json` | 日文 |
| **i18n 模板** | `resources/i18n/template/ko-KR.json` | 韩文 |
| **i18n 模板** | `resources/i18n/template/fr-FR.json` | 法文 |
| **SQL 迁移** | `db/sql/migrate_ext_19_mini_program_openid.sql` | `overseas_booking` 表 CREATE TABLE IF NOT EXISTS（L9）+ ALTER ADD COLUMN IF NOT EXISTS（L28）|
| **测试** | `test/booking/BookingConfirmationServiceTest.java` | 单元测试（渠道路由 + 聚合结果）|
| **测试** | `test/booking/BookingConfirmationTemplateResolverTest.java` | 单元测试（locale 加载 + 占位符替换）|
| **测试** | `test/service/channel/EmailSenderTest.java` | 单元测试（mismatch 降级 demo）|
| **前端** | `views/intl-booking/**` | 状态确认按钮文案 + `autoSend=false` UI（SoT §0.3 待 Phase 2 验证）|

### 2.4 重构类变更标记

按规则 14：「重构类变更必须先跑 Phase 3.5 Diff Audit」。

本批次**不涉及重构**（EXT-19 是新增字段 + 新增 service + 新增 sender，Batch 1.1/1.2/1.3 期间未触达）→ Phase 3.5 **不适用**。

---

## 三、Feature ID 规则（沿用 Batch 1.1/1.2/1.3 + 编号策略）

引用：`.cursor/rules/feature-id.mdc` + `docs/PROJECT_AUDIT_PLAYBOOK.md` §三

### 3.1 基础规则（沿用 Batch 1.1/1.2/1.3）

| 规则 | 内容 |
|---|---|
| **格式** | `F` + 三位数字（如 `F001`, `F081`, `F085`）|
| **拆分粒度** | 一个 Feature = 一个可独立验收的业务能力 |
| **拆分示例** | F001 登录 / F002 退出 / F003 用户列表 / F004 新增用户 / F005 编辑用户 / F006 删除用户 / F007 用户详情 / F008 权限配置 |
| **禁止** | 不要把"用户管理"作为一个 Feature / 不要把"订单模块"作为一个 Feature / 不要把一张数据库表作为一个 Feature |
| **Evidence 目录** | `audit/evidence/FXXX-<slug>/`（编号固定，缺一张即 UNVERIFIED）|

### 3.2 编号策略（**Batch 1.4 决策**）

| 选项 | 推荐 | 理由 |
|---|---|---|
| **A. 接续 Batch 1.3 的 F081 起** | ✅ **采用**（用户决策）| 全局唯一、便于跨批次交叉引用与 grep；Evidence 目录 / Issue 引用无歧义 |
| B. 从 F001 重新开始 | ❌ 不采用 | 跨批次引用需补 Batch 前缀，增加元数据开销 |
| C. 混合（保留 F001-F080 给前三批次，新模块用 F101+）| ❌ 不采用 | 编号跳跃不利于连续阅读 |

### 3.3 Batch 1.4 编号规则

| 维度 | 值 |
|---|---|
| **起始编号** | **F081**（接续 Batch 1.3 的 F031-F080）|
| **预估上限** | **F110**（按 Operational Notes §1 touchpoints + §3 endpoints + 子模块估算 20-30 Features）|
| **预留上限** | **F115**（5 个缓冲编号，应对 Phase 1 触达时发现新 Feature）|
| **冲突解决** | Phase 1 期间如发现编号冲突，重命名旧 Feature（不要回插新 Feature 到中间）|
| **跨批次引用** | 直接用全局 F 编号（如 "F081 见 `audit/evidence/F081-overseas-booking-entity/`"），不需加批次前缀 |
| **跨 EXT-* 引用** | Feature ID 按 EXT-19 视角命名；引用 EXT-22/23/24 的代码路径注明 "EXT-* 边界" |

### 3.4 Issue 编号（沿用 Batch 1.1/1.2/1.3）

| 维度 | 值 |
|---|---|
| **Issue 编号** | 沿用全局 `I` + 三位数字（Batch 1.1 用到 I016，Batch 1.2 用到 I025，Batch 1.3 用到 I037）|
| **Batch 1.4 起始** | **I038**（Phase 3 期间首次登记 — 与 preflight §七 漂移标对齐）|
| **预留上限** | **I055**（预留 18 个 Issue 编号应对 Phase 1-5 触达）|
| **漂移分类** | 按用户修正（详见 §五.2 沿用 I002/I017/I029 模式）：I038-I040 = SoT vs 代码 CONFLICT；表 MISSING 类用 I002/I017/I029 同模式（**统一口径：所有 MISSING 表都不跑迁移，Post-Audit 统一跑**）|

### 3.5 Phase 1 任务

按 §3.3 规则拆分 EXT-19 的 Feature 清单（编号 F081 起），覆盖：

- `OverseasBooking` entity + `miniProgramOpenId` 字段（`overseas_booking` 表）~2
- `BookingConfirmationService` 核心发送 + 渠道路由 ~3
- `BookingConfirmationTemplateResolver` + 6 locale + warmup + fallback ~3
- `BookingConfirmationTemplate` + `BookingConfirmationResult` DTO ~1
- 5 个 ChannelSender（Sms / Email / MiniProgram / InApp / WechatOA）~5
- `ChannelSender` 接口契约 + `ChannelDispatcher` 分发 ~2
- `StoreLookupService`（pos_store 查询 + 5 分钟缓存）~1
- `IntlBookingService` 入口端点（6 个：send-confirmation / status / confirmation-template / list + 2 个 webhook）~6
- CONFIRMED 自动触发 sendConfirmation（autoSend 行为变更）~1
- 前端预约确认 UI（状态确认按钮文案 + autoSend=false UI）~2
- Google / Instagram webhook 接收 ~2

**估算合计**：**~25-30 Features**（F081-F110 范围），预留 F111-F115 应对扩展。

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

**Phase 2-7 任务**：对每个 Feature 标注其期望达到的 L 等级。Batch 1.4 期间建议 EXT-19 Feature **至少 L3**（静态链路通），Mock 降级路径（WeChat / SMTP 无凭据）**争取 L4**（API 可调 + jshell 单元测试），真实链路（WeChat MP / SMTP 真实发送）**L5a/L5b 受凭据缺失限制降级为 UNVERIFIED**。

---

## 五、Result 子标签确认

引用：`docs/PROJECT_AUDIT_PLAYBOOK.md` §五 + `.cursor/rules/project-audit.mdc` 规则 7

**最终 Result 只允许以下 10 种状态**（沿用 Batch 1.1/1.2/1.3）：

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

### 5.1 EXT-19 特殊 Result 判定建议

| EXT-19 特殊场景 | 建议 Result | 理由 |
|---|---|---|
| **WeChat MiniProgramSender 真实调用（无凭据）** | **UNVERIFIED** | 凭据缺失（用户决策 #1：Mock 降级）；Phase 5 真实链路验证标记为 UNVERIFIED |
| **EmailSender 真实 SMTP（无凭据）** | **UNVERIFIED** | 凭据缺失；Phase 5 真实链路验证标记为 UNVERIFIED |
| **`overseas_booking` 表未跑迁移** | **MISSING** | DDL 写了但 DB 没建（用户决策 #3：I002/I017/I029 统一口径，不跑迁移）→ Phase 5 维持 MISSING，Post-Audit 统一跑 |
| **`mkt_channel_config` 表未跑迁移（EmailSender 依赖）**| **MISSING** | 同上模式（I002/I017/I029 统一口径）|
| **`pos_store` 表未跑迁移（StoreLookupService 依赖）**| **MISSING** | 同上模式 |
| **SoT vs 代码 CONFLICT（待 Phase 1 全文核实）**| **中立标 I038+** | 沿用 I001/I018/I026/I027/I030 模式；Fix Phase = Post-Audit 人类决策 |
| **autoSend 前端 UI 缺失** | **PARTIAL-NON-BLOCKER** | Shadow Feature；用户决策 #6：Post-Audit |
| **autoSend=false 前端 UI 缺失** | **PARTIAL-NON-BLOCKER** | Shadow Feature；用户决策 #6：Post-Audit |
| **前端状态确认按钮文案无「确认即发送通知」** | **PARTIAL-NON-BLOCKER** | Shadow Feature；用户决策 #6：Post-Audit |
| **I008 catch 吞异常模式（autoSend 异常不回滚状态变更）**| **Phase 2 静态扫描验证** | 沿用 I008 模式，SoT §0.3 显式确认不回滚；待 Phase 2 静态确认 catch 范围 |
| **I009 rollbackFor 缺失（`IntlBookingService.updateStatus`）**| **Phase 3 静态扫描验证** | 沿用 I009 模式；待 Phase 3 静态确认 |

### 5.2 漂移分类原则（**用户统一口径 — 关键**）

> ⚠️ **用户 2026-09-14 15:45 强调（第三次）**：所有 MISSING 表统一不跑迁移，Post-Audit 统一跑。
> 
> 漂移分类原则（沿用 Batch 1.3 用户修正 #3）：
> ① 涉及"表缺失"的 → I002/I017/I029 模式（MISSING，跑迁移即解，**统一 Post-Audit 跑**）
> ② 涉及"SoT 描述 vs 代码实现不一致"的 → I001/I018/I026/I027/I030 模式（CONFLICT，中立标）
> ③ 不混为一谈

**漂移清单分类**（沿用 Batch 1.3 §五.2 + preflight-batch-1.4.md §七）：

| # | 漂移项 | 漂移类型 | Issue 编号 | 处理 |
|---|---|---|---|---|
| 1 | `overseas_booking` 表未跑迁移 | **I002/I017/I029 MISSING 模式** | I038（建议）| **统一 Post-Audit 跑迁移**（用户决策 #3 第三次强调）|
| 2 | SoT vs 代码 CONFLICT（待 Phase 1 全文核实 §0.1-§0.5）| **中立标** | I039+ | Fix Phase = Post-Audit 人类决策 |
| 3 | 前端缺 UI（autoSend=false / 按钮文案）| 待 Phase 2 验证 | I040+（待登记）| 中立标 |
| 4 | catch 吞异常 / rollbackFor 缺失 | **I008/I009 模式** | 待 Phase 2/3 验证 | 中立标 |

### 5.3 严格禁止

- 没有运行证据只能标 UNVERIFIED（规则 3 + 9）
- 不允许出现 VERIFIED 但缺 traceId / DB row 的情况
- **EXT-19 特殊约束**：
  - **真实凭据缺失**（WeChat MP / SMTP）→ **强制 UNVERIFIED**（不允许在 Mock 降级下声称 VERIFIED）
  - **SoT 漂移中立标**（I038-I040）→ **不允许预设方向**（既不写"SoT 错"也不写"代码错"）
  - **表 MISSING**（DDL 写了但 DB 没建）→ **不允许用 UNVERIFIED 掩盖**（I002/I017/I029 模式：跑迁移即解，**统一 Post-Audit 跑**）
  - **统一口径（第三次强调）**：所有 MISSING 表（`overseas_booking` / `mkt_channel_config` / `pos_store` / `crm_contact_map` / `plugin_runtime_state` / `google_maps_config` / `google_maps_geocode_log` / `webhook_msg_log`）→ **Post-Audit 统一跑迁移**

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

### 6.2 EXT-19 特殊 Evidence 要求

| EXT-19 场景 | 必备文件 | 验证内容 |
|---|---|---|
| **每个 Feature** | `traceability-matrix.md` | 路由 → controller → service → mapper → SQL/表 → DTO 全栈追溯 |
| **BookingConfirmationService 核心 Feature** | `runtime-evidence.md` 含 `mock-call-test.txt` + `multi-channel-routing-test.txt` | mock 路径可调 + 5 渠道路由（SMS / EMAIL / MINI_PROGRAM / IN_APP / WECHAT_OA）|
| **BookingConfirmationTemplateResolver i18n Feature** | `runtime-evidence.md` 含 `locale-load-test.txt` + `fallback-test.txt` | 6 locale warmup + locale 回退链（`xx-YY → xx → en`）|
| **`OverseasBooking` entity Feature** | `db-evidence.sql` 含 `overseas-booking-schema.txt` | **MISSING 验证**（dev DB 中表不存在，DB-evidence 记录 schema 期望 + 实际不存在 + 26 列预期）|
| **`overseas_booking` 表 MISSING** | `db-evidence.sql` 含 `migrate_ext_19_not_applied.txt` | **不跑迁移记录**（D3 决策；记录 DDL 已写 + DB 未跑 + Post-Audit 跑批计划）|
| **SmsSender Feature** | `runtime-evidence.md` 含 `sms-send-test.txt` | mock 链路可调（无真实阿里云凭据 → UNVERIFIED）|
| **EmailSender Feature** | `runtime-evidence.md` 含 `email-mismatch-demo-test.txt` | mismatch 降级 demo 模式日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode` |
| **MiniProgramSender Feature** | `runtime-evidence.md` 含 `mini-program-mock-test.txt` | mock 链路可调（无真实 appId/appSecret → UNVERIFIED）|
| **InAppSender Feature** | `runtime-evidence.md` 含 `in-app-db-test.txt` | database 持久化验证 |
| **WechatOfficialAccountSender Feature** | `runtime-evidence.md` 含 `wechat-oa-mock-test.txt` | mock 链路可调 |
| **StoreLookupService Feature** | `runtime-evidence.md` 含 `pos-store-lookup-test.txt` + `cache-5min-test.txt` | pos_store 查询 + 5 分钟缓存 |
| **`IntlBookingService.updateStatus` autoSend Feature** | `runtime-evidence.md` 含 `auto-send-test.txt` + `auto-send-false-test.txt` | CONFIRMED 自动触发 + autoSend=false 关闭验证 |
| **Google webhook Feature** | `runtime-evidence.md` 含 `google-webhook-test.txt` | POST /intl-booking/webhook/google 验证 |
| **Instagram webhook Feature** | `runtime-evidence.md` 含 `instagram-webhook-test.txt` | POST /intl-booking/webhook/instagram 验证 |

### 6.3 audit/ 目录骨架（Batch 1.4 沿用 v3）

| 子目录 | 职责 | Batch 1.4 产出 |
|---|---|---|
| `audit/baseline/` | 基线快照（Phase -1/0 产出，不可变）：`preflight-batch-1.4.md` + **`scope-freeze-batch-1.4.md`（本文件）**| 本批次已落档 |
| `audit/evidence/F081-.../ ~ F115-.../` | 每 Feature 证据沉淀 | Phase 1 期间按编号创建 |
| `audit/scripts/` | 8 个守门脚本（g1-g8）— **未实现**（preflight §六）| Phase 1-2 期间补（用户决策 #5）|
| `audit/static-gap/` | Phase 3 静态差距扫描结果 | Phase 3 期间产出 |
| `audit/runtime/` | Phase 4 运行时计划 + Phase 5 验证 | `audit/runtime/plan-batch-1.4.md`（待写）|
| `audit/issues/issue-register.md` | Issue 汇总（沿用 Batch 1.1/1.2/1.3，单一文件）| §二十三 起追加 I038+ |

### 6.4 回归对照三件套（规则 13）

每次验证必须绑定：

- **commit hash**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2/1.3 freeze commit，即本批次起点）+ `656caa72341c96d1b2e703dde993603dda9eebe1`（v3 整体 baseline）+ `12a97ea57a98d268c864fc7c010b82b305d7bb93`（Batch 1.1 freeze commit）
- **DB snapshot**：`audit/baseline/db-snapshot.sql`（417 KB / 270 表 / schema-only）— **注意 `overseas_booking` 表可能未跑迁移**（用户决策 #3）
- **env.md**：`audit/baseline/env.md`（9,951 B）

---

## 七、EXT-19 .mdc 为 SoT（事实来源）确认

引用：`.cursor/rules/project-audit.mdc` 规则 11 + `.cursor/rules/ext-19-operational-notes.mdc`（EXT-19 Operational Notes）

### 7.1 SoT 规则

> 「审计结论与 EXT-* Operational Notes 冲突时，以 EXT-* 为准并标 ISSUE。」

### 7.2 EXT-19 Operational Notes 内容覆盖

| § | 主题 | 内容要点 |
|---|---|---|
| **§0** | 关键约束 | WeChat 模板 ID 占位 + EmailSender mismatch 降级 demo + CONFIRMED 自动触发 sendConfirmation + StoreLookupService 字段映射 + SQL 迁移兼容（MySQL 8.0.29+）|
| **§1** | Touchpoints | 6 个 booking 包文件 + 5 个 channel sender + IntlBookingService 业务入口 + 6 个 i18n locale JSON + 1 个 SQL 迁移 + 3 个测试 |
| **§2** | Re-confirmation Checklist | 10 项上线前必查（WeChat 模板 / SMTP 凭据 / 前端文案 / autoSend / pos_store 字段 / MySQL 版本 / locale JSON / EmailSender 真实链路 / overallSuccess 上报）|
| **§3** | Endpoints | 6 个端点（send-confirmation / status / confirmation-template / list + google / instagram webhook）|
| **§4** | Channel Routing | customerPhone → SMS / customerEmail → EMAIL / miniProgramOpenId → MINI_PROGRAM / 任一无 receiver → skipped |
| **§5** | 占位符变量 | 9 个 `${var}` 占位符（customerName / customerPhone / customerEmail / serviceName / bookingDate / bookingTime / bookingNo / storeName / storePhone）|

### 7.3 Batch 1.4 审计优先级

1. **EXT-19 Operational Notes（事实来源，优先级最高）**
2. `docs/PROJECT_AUDIT_PLAYBOOK.md`（体系规则）
3. `audit/issues/issue-register.md`（历史 Issue 关联）
4. `audit/baseline/preflight-batch-1.4.md`（已知约束）
5. `backend/src/main/java/com/meiyetec/{entity,booking,service/channel,service}/*`（实际代码）
6. `backend/src/main/resources/{application.yml,db/sql/migrate_ext_19*.sql,i18n/template/*}`（配置 + 迁移 + 模板）

### 7.4 EXT-19 Operational Notes 已加载确认

- ✅ `.cursor/rules/ext-19-operational-notes.mdc` 已加载（preflight §八.6 已确认）
- ✅ preflight §七.4 已摘录 SoT 与代码初步对照（6 项 §0 描述均在代码中找到对应）
- ✅ Phase 0 期间需再次确认 Operational Notes 与最新代码版本一致（preflight §八.6 验证清单）

---

## 八、能力边界确认（Cursor 不能 / 只能）

引用：`docs/PROJECT_AUDIT_PLAYBOOK.md` §十八 + `.cursor/rules/project-audit.mdc` 规则 9

### 8.1 Cursor **不能**做的事

| ❌ 不能 | 原因 |
|---|---|
| 真打开浏览器点击 | 无 Playwright MCP（沿用 Batch 1.1/1.2/1.3 决策）|
| 连接生产数据库执行查询 | 无 MySQL MCP 受限时的能力边界 |
| 调用 WeChat 订阅消息 API 真实接口 | **WeChat appId/appSecret 缺失**（用户决策 #1：Mock 降级）|
| 发送真实 SMTP 邮件 | **SMTP 凭据缺失**（用户决策 #2：沿用 SoT §0.2 mismatch demo 模式）|
| 注册有赞 push_url 到有赞后台 | 需人工到有赞开发者后台操作（Batch 1.3 范围，本批次不触达）|
| 跑 `migrate_ext_19_mini_program_openid.sql` 迁移 | **用户决策 #3 第三次强调：所有 MISSING 表不跑迁移，Post-Audit 统一跑** |
| 判断业务是否符合 PRD | EXT-19 不在 PRD 主线；Operational Notes 为 SoT |
| 替代人工验收 | 最终验收必须人工签收 |
| 在没有运行证据时声称 VERIFIED | 规则 3 + 9 硬性要求 |

### 8.2 Cursor **只能**做的事

| ✅ 能做 | 方式 |
|---|---|
| 静态分析代码 | grep / glob / Read 文件内容 |
| 生成测试代码和 SQL | 写文件 + 给建议 |
| 根据看到的内容推断 | 在已知 EXT-19 上下文下的合理推断 |
| 标注 UNVERIFIED | 显式标注，不掩盖缺失 |
| **执行 jshell 单元测试** | 直接调用 `SmsSender.render(var)` / `EmailSender` mismatch demo 模式 / 6 个 locale warmup 验证 |
| **生成 Mock 渠道发送样本** | 模拟 5 个 channel sender 的 mock payload + 验证返回值 |
| **Phase 5 期间 grep DB schema 验证** | MySQL MCP 验证 `overseas_booking` 表存在性 + `mini_program_open_id` 字段 + 26 列对齐（**仅验证不修复**）|

### 8.3 本批次降级方案（沿用 Batch 1.1/1.2/1.3 + 用户决策）

| 工具/能力 | 状态 | 降级方案 |
|---|---|---|
| **MySQL MCP** | ✅ 已配置 | 正常使用（**仅验证不修复** — 验证 `overseas_booking` 表存在性 + 字段对齐，但**不跑迁移**）|
| **Playwright MCP** | ❌ 未配置（沿用 Batch 1.1/1.2/1.3 决策）| Phase 5 E2E 降级为：① 生成 Playwright 脚本供用户手动执行；② 用户手动贴回 evidence；③ 静态抓 HTML + curl API + Vitest |
| **Redis** | ❓ 未验证（沿用决策）| 不探测；运行时 NPE 风险由 Phase 5 暴露 |
| **WeChat MP API 真实调用** | ❌ **凭据缺失**（用户决策 #1：Mock 降级）| Mock 降级（D10 铁律）；jshell 单元测试验证算法；真实链路 Phase 5 UNVERIFIED |
| **SMTP 真实发送** | ❌ **凭据缺失**（用户决策 #2：沿用 SoT §0.2 mismatch demo 模式）| mismatch demo 模式（无租户配置时走 demo 或全局 spring.mail.*）；观测日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode` |
| **`overseas_booking` 表迁移** | ❌ **不跑**（用户决策 #3 第三次强调）| Phase 5 仅 MySQL MCP 验证缺失；Post-Audit 统一跑批 |
| **守门脚本 g1-g8** | ❌ 未实现（沿用决策 #5）| Phase 1-2 期间补 |
| **EXT-19 特定守门脚本 g16-g19** | ⚠️ 建议（preflight §六.2）| Phase 1 期间评估是否实现 |

### 8.4 EXT-19 评估期间需要用户手动配合的事项

| 事项 | 触发 Phase | 用户配合内容 |
|---|---|---|
| 启动后端（含 booking / channel sender / i18n warmup 初始化）| Phase 5 | 启动日志 + 6 locale warmup 验证 |
| 跑 jshell `SmsSender.render(var)` 单元测试 | Phase 5（沿用 Batch 1.2 Phase 5B 模板）| 验证占位符替换算法 |
| 跑 jshell `EmailSender` mismatch demo 模式测试 | Phase 5 | 验证 mismatch 日志关键词 + demo 模式分支 |
| Mock 渠道发送样本（5 个 sender）| Phase 5 | 生成 5 个 channel mock payload → 调 `sendConfirmation(b)` 期望 `BookingConfirmationResult.overallSuccess=true` |
| 跑 jshell `BookingConfirmationTemplateResolver.loadLocale(locale)` 单元测试 | Phase 5 | 验证 6 locale warmup + fallback 链 |
| 跑 MySQL MCP 验证 `overseas_booking` 表存在性 | Phase 5 | 验证 DDL 存在 + DB 中表存在/缺失 + 26 列对齐（**仅验证不修复**）|
| 跑 MySQL MCP 验证 `mkt_channel_config` / `pos_store` 表存在性 | Phase 5 | 验证 EXT-19 依赖表 |
| 跨租户访问测试（如可达）| Phase 5 | 普通租户调别人 bookingId → 期望 `BizCode.FORBIDDEN` |
| WeChat / SMTP 真实凭据注入（如有）| Phase 5（用户决策 #1/#2 评估）| 注入凭据 + 调 5 个 channel sender 期望真实响应 |

---

## 九、已知约束（引用 preflight-batch-1.4.md §八）

**本节仅引用，不复制内容**。详见 `audit/baseline/preflight-batch-1.4.md` §八（line 290-394）。

### 9.1 EXT-19 表存在性检查（**关键约束 — 用户决策 #3 第三次强调**）

> ⚠️ **重要提示（第三次）**：所有 MISSING 表（`overseas_booking` / `mkt_channel_config` / `pos_store` / `crm_contact_map` / `plugin_runtime_state` / `google_maps_config` / `google_maps_geocode_log` / `webhook_msg_log`）→ **统一不跑迁移，Post-Audit 统一跑**。
>
> Phase 0 沿用 Batch 1.3 用户修正 #3：漂移分类原则明确 — **表 MISSING = I002/I017/I029 模式（跑迁移即解，统一 Post-Audit 跑）**。

| 表 | 创建迁移脚本 | 实体类 | DB 实际状态 | 处理 |
|---|---|---|---|---|
| `overseas_booking` | `migrate_ext_19_mini_program_openid.sql:9`（CREATE TABLE IF NOT EXISTS）+ `migrate_ext_19_mini_program_openid.sql:28`（ALTER ADD COLUMN IF NOT EXISTS）| ✅ `entity/OverseasBooking.java`（含 `miniProgramOpenId`）| ⚠️ **DDL 写了但 dev DB 未跑**（I002/I017/I029 MISSING 模式）| **不跑迁移**（用户决策 #3）；Phase 5 仅 MySQL MCP 验证缺失；Post-Audit 统一跑批 |
| `mkt_channel_config` | 沿用（EXT-09 期间已建）| ✅ `entity/MktChannelConfig.java` | ⚠️ **可能未跑迁移**（I002/I017/I029 模式）| 同上 — 不跑迁移，Post-Audit 统一跑 |
| `pos_store` | 沿用（基线已建）| ⚠️ 待 Phase 1 grep 确认 entity 位置 | ⚠️ **可能未跑迁移** | 同上 |

### 9.2 SoT vs 代码漂移（待 Phase 1 全文核实 — 中立标 I038+）

> 沿用 Batch 1.1 I001 + Batch 1.2 I018 + Batch 1.3 I026/I027/I030 模式：**中立标，不预设谁对谁错，留人类在 Post-Audit 决策**。

| # | Operational Notes §0 描述 | 当前代码状态（preflight §七.4 初步确认）| 反转？| Issue 编号 |
|---|---|---|---|---|
| 1 | §0.1 WeChat 模板 ID 是占位符 | `BookingConfirmationService:25` `MP_TEMPLATE_ID = "BOOKING_CONFIRMATION"` 确认 | ✓ **符合** | 待 Phase 1 全文核实 |
| 2 | §0.2 EmailSender mismatch demo 模式 | `EmailSender` 注释确认三种模式 | ✓ **符合** | 待 Phase 1 全文核实 |
| 3 | §0.3 CONFIRMED 自动触发 sendConfirmation | `IntlBookingService.updateStatus` 初步确认存在 autoSend 逻辑 | ⏸ 待 Phase 1 全文核实响应结构 | 待 Phase 1 全文核实 |
| 4 | §0.4 StoreLookupService 字段映射 | `StoreLookupService` 注释确认 pos_store 查询 + 5 分钟缓存 | ✓ **符合** | 待 Phase 1 全文核实 SQL |
| 5 | §0.5 SQL 迁移兼容（MySQL 8.0.29+）| `migrate_ext_19:28` 使用 `ADD COLUMN IF NOT EXISTS` | ✓ **符合** | 待 MySQL MCP 验证 dev DB |

### 9.3 Frontend UI Shadow（autoSend / 按钮文案）

| 维度 | 详情 |
|---|---|
| **autoSend=false UI 缺失** | SoT §0.3：`{"status":"CONFIRMED", "autoSend": false}` 可关闭自动发送；但前端无此入口 |
| **状态确认按钮文案缺失** | SoT §0.3：前端状态确认按钮文案需标注「确认即发送通知」；当前可能无提示 |
| **SoT 漂移类型** | **SoT vs 代码 CONFIRMED**（非漂移，SoT §0.3 明确说明）|
| **处理** | **Shadow Feature**（沿用 I006/I007 模式）|
| **Phase 1 任务** | Feature Inventory 标注 PARTIAL-NON-BLOCKER；Phase 7 Post-Audit 决定 |
| **用户决策 #6** | **Post-Audit** 修复（用户明确推迟）|

### 9.4 I008 catch 吞异常模式（autoSend 异常不回滚）

| 维度 | 详情 |
|---|---|
| **SoT §0.3** | "发送异常不会回滚状态变更（已在 try/catch 内），仅写入 confirmationError 字段" |
| **I008 模式风险** | `IntlBookingService.updateStatus` 中 autoSend 发送异常在 try/catch 内 — **与 I008 catch 吞异常同模式** |
| **Phase 1 任务** | 全文读取 `IntlBookingService.updateStatus` 确认 catch 范围；确认 confirmationError 写入逻辑 |
| **Phase 2/3 任务** | 静态扫描 + Issue 登记（沿用 I008 模式）|

### 9.5 I009 rollbackFor 缺失模式

| 维度 | 详情 |
|---|---|
| **实现位置** | `IntlBookingService.updateStatus` 加了 `@Transactional` 但未指定 `rollbackFor` |
| **I009 模式风险** | 沿用 Batch 1.1 I009 模式 — 默认 rollbackFor 仅 RuntimeException |
| **Phase 3 任务** | 静态确认 + Issue 登记 |

### 9.6 与 Batch 1.1/1.2/1.3 独立性检查（引用 preflight §七.5）

| 检查项 | 结果 |
|---|---|
| **代码包共享** | ⚠️ 部分共享（共用 `mkt_channel_config` / `pos_store` / `OverseasBooking`）|
| **DB 表共享** | ⚠️ 部分共享（`mkt_channel_config` / `pos_store` / `overseas_booking`）|
| **配置共享** | ⚠️ `spring.mail.*` 全局配置（EmailSender 使用）|
| **业务依赖** | ❌ EXT-19 不依赖 EXT-22/23/24 |
| **审计可分离性** | ✅ Feature Inventory 可分离（Phase 1 期间明确边界划分）|

### 9.7 共用 entity/table 边界（用户决策 #8）

| 共享资源 | 所属 EXT-* | Batch 1.4 处理 |
|---|---|---|
| `MktChannelConfig` entity + `mkt_channel_config` 表 | EXT-09（已有）+ EXT-19（5 channel sender 复用）| **Phase 1 期间 grep 确认边界**：5 sender 共用同一 configJson，按 channelType 字段分发；Feature ID 按 EXT-19 视角命名 |
| `pos_store` 表 | EXT-03（已有）+ EXT-19（StoreLookupService 复用）| **Phase 1 期间 grep 确认边界**：StoreLookupService 仅读 `store_name + contact_phone`；Feature ID 按 EXT-19 视角命名 |
| `OverseasBooking` entity + `overseas_booking` 表 | EXT-15（已有）+ EXT-19（CONFIRMED 自动触发 + 5 渠道发送）| **Phase 1 期间 grep 确认边界**：BookingConfirmationService 仅读 + 写 confirmationError；Feature ID 按 EXT-19 视角命名 |

### 9.8 受保护页面零风险（引用 preflight §五）

- EXT-19 审计期间**直接不触达**受保护页面（`/dashboard` / `/mh/visual` / `/mh/consent`）
- Phase 2 静态扫描确认 EXT-19 与受保护页面后端接口无间接依赖（用户决策 — 沿用 Batch 1.3）

### 9.9 历史审计沉淀不在 v3 目录（沿用 Batch 1.1/1.2/1.3）

- `docs/audit/` 25+ 个 `audit_*.txt` + `BEAUTY_SAAS/` 子目录（28+ 报告）
- 不迁移（按 `audit/README.md` §2）
- Phase 7 引用时使用相对路径 `../docs/audit/BEAUTY_SAAS/19_REPAIR_ROADMAP_V3.md`

### 9.10 受保护页面与 EXT-19 边界（引用 preflight §五.1）

- `/dashboard` / `/mh/visual` / `/mh/consent` 与 EXT-19 无直接依赖
- Phase 2 期间静态扫描确认无间接依赖（`OverseasBooking` 不被受保护页面后端接口引用）

---

## 十、待用户确认项（**用户已确认** — 详见 preflight §十）

### 10.1 8 项决策（用户已确认 — 2026-09-14 15:45）

| # | 项 | 用户决策 | 当前状态 |
|---|---|---|---|
| **1** | **WeChat 订阅消息凭据** | ✅ **Mock 降级**（接受 D10 铁律）| Phase 5 仅验证 mock 链路 + jshell 单元测试；真实链路 UNVERIFIED |
| **2** | **SMTP 凭据来源** | ✅ **沿用 SoT §0.2**（mismatch 降级 demo 模式）| Phase 5 验证 mismatch demo 模式日志关键词 |
| **3** | **`overseas_booking` 表跑迁移？** | ❌ **不跑**（**第三次强调**：所有 MISSING 表都不跑迁移，Post-Audit 统一跑）| Phase 5 仅 MySQL MCP 验证缺失；Issue 登记 I038 |
| **4** | **Phase -1 报告完成后 commit 策略** | ✅ **选 C**（再 stash 一次，Phase -1 完成后保持 working tree clean）| Phase -1 完成后立即 `git stash push -m "after-phase-minus-1-clean-before-phase-0-batch-1.4"` |
| **5** | **g1-g8 守门脚本补齐时机** | ✅ **Phase 1-2 期间补** | 不阻塞 Phase 0；Phase 1-2 期间实现 |
| **6** | **autoSend 前端 Shadow** | ✅ **Post-Audit** | 沿用 I006/I007 模式；Phase 7 Post-Audit 决定 |
| **7** | **freeze commit** | ✅ **接受**（追加 `audit(freeze): Batch 1.3 完结 + Batch 1.4 启动`）| Phase 0 启动前 commit；与 Batch 1.1→1.2→1.3 freeze 模式一致 |
| **8** | **共用 entity/table 边界** | ✅ **Phase 1 期间明确** | Phase 1 Feature Inventory 期间 grep 确认调用方；Feature ID 按 EXT-19 视角 |

### 10.2 用户第三次强调（关键 — 漂移统一口径）

| # | 项 | 用户强调 | 影响 |
|---|---|---|---|
| **A** | **所有 MISSING 表统一不跑迁移** | "前三次：I002/I017/I029 都没跑；统一口径：所有 MISSING 表都不跑迁移，维持 MISSING，Post-Audit 统一跑" | 本批次 `overseas_booking` 不跑迁移；累计 8 张表（`crm_contact_map` / `plugin_runtime_state` / `google_maps_config` / `google_maps_geocode_log` / `webhook_msg_log` / `ecommerce_platform` 部分列 / `overseas_booking` / `mkt_channel_config` / `pos_store`）→ Post-Audit 统一跑批 |

### 10.3 默认决策影响

| 默认 | 影响 |
|---|---|
| **#1 WeChat 凭据缺失 → Mock 降级** | Phase 5 仅验证 mock 链路 + jshell 单元测试；真实链路（MiniProgramSender 真实调用）验证标记为 UNVERIFIED |
| **#2 SMTP 沿用 SoT §0.2 mismatch demo 模式** | Phase 5 验证 mismatch demo 模式 + 观测日志关键词 `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode` |
| **#3 所有 MISSING 表统一 Post-Audit 跑迁移** | Phase 5 仅 MySQL MCP 验证缺失；累计 8 张表 → Post-Audit 统一跑批 |
| **#4 commit 策略 C（再 stash 一次）** | Phase -1 完成后立即 `git stash push`；Phase 0 scope-freeze 文件 commit 时保持 working tree clean |
| **#5 g1-g8 Phase 1-2 期间补** | 不阻塞 Phase 0；Phase 1-2 期间实现 |
| **#6 autoSend 前端 Shadow → Post-Audit** | Phase 1 标 PARTIAL-NON-BLOCKER；Phase 7 Post-Audit 决定 |
| **#7 freeze commit 接受** | git history 清晰；Phase 0 scope-freeze 锚点明确 |
| **#8 共用字段边界 Phase 1 明确** | Phase 1 grep 确认 `mkt_channel_config` / `pos_store` / `OverseasBooking` 跨 EXT-* 边界 |

---

## 十一、Phase 0 准入自检

| 检查项 | 结果 |
|---|---|
| preflight-batch-1.4.md 已落档 | ✅ `audit/baseline/preflight-batch-1.4.md`（458 行）|
| 8 项决策已回答 | ✅ 用户 2026-09-14 15:45 确认 |
| 用户第三次强调漂移统一口径已落档 | ✅ §五.2 + §九.1 明确 I002/I017/I029 模式统一 Post-Audit 跑迁移 |
| Phase 0 freeze commit 计划 | ✅ 沿用 Batch 1.1→1.2→1.3 freeze 模式（追加 `audit(freeze): Batch 1.3 完结 + Batch 1.4 启动`）|
| scope-freeze-batch-1.4.md 可读 | ✅（本文件）|
| 项目定位已明确 | ✅ §一 |
| 验收范围已冻结（含 EXT-19 详细验证范围）| ✅ §二 |
| Feature ID 规则已确认（含编号策略 F081 起）| ✅ §三 |
| L0-L5 等级已确认 | ✅ §四 |
| Result 子标签已确认（含 EXT-19 特殊判定 + 漂移分类原则）| ✅ §五 |
| Evidence 目录已约定（含 EXT-19 特殊要求）| ✅ §六 |
| EXT-19 SoT 已确认 | ✅ §七 |
| 能力边界已确认（含 WeChat / SMTP Mock 降级）| ✅ §八 |
| 已知约束已引用（含 I038+ 中立标）| ✅ §九 |
| 待用户确认项已列出（8 项 + 用户第三次强调）| ✅ §十 |

✅ **Phase 0 完成。可以进入 Phase 1（Feature Inventory — F081 起）。**

---

## 十二、本文件状态

| 项 | 值 |
|---|---|
| **状态** | 不可变（frozen）|
| **变更控制** | 任何改动需用户明确同意 |
| **下一阶段产出位置** | `audit/feature-inventory.md` §1.4（F081-F115 EXT-19 Feature 清单）|
| **下一阶段 Prompt 模板** | 见 `docs/PROJECT_AUDIT_PLAYBOOK.md` §十「Phase 1」|

---

**Scope Freeze（Batch 1.4）完成。EXT-19 多语言预约确认范围已冻结 + 8 项决策已落档 + F081 编号策略已确认 + 漂移统一口径已第三次强调（所有 MISSING 表 Post-Audit 统一跑迁移）+ WeChat/SMTP 凭据 Mock 降级 + freeze commit 策略 C 接受。⏸ 等用户确认 → 进入 Phase 1（Feature Inventory，F081 起）。**
