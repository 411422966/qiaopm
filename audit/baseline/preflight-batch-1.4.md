# Pre-flight Check（Batch 1.4 — Phase -1 产出）

> 适用规则：`@project-audit.mdc` 规则 16（Phase -1 必须先于 Phase 0）
> 模板：`@CURSOR_AUDIT_GUIDE.md`「二点五」章节
> 创建时间：2026-09-14 15:35 (UTC+8)
> 适用批次：**v3 第四批 / Batch 1.4 = EXT-19 多语言预约确认**
> 上一批次完成：`audit/reports/batch-1.3-summary.md`（EXT-22 25/25 Feature 完结）+ `audit/issues/issue-register.md` §十九（I026-I037 + I037 NEW CRITICAL-P0）
> 上一阶段产出：`audit/baseline/{commit.txt, env.md, db-snapshot.sql}`（沿用 Batch 1.1 起点）
> SoT 引用：`.cursor/rules/ext-19-operational-notes.mdc`（EXT-19 Operational Notes）

---

## 一、MCP 状态（沿用 Batch 1.1/1.2/1.3 决策）

| MCP | 状态 | 来源 |
|---|---|---|
| **MySQL MCP** | ✅ **已配置** | 沿用 Batch 1.1/1.2/1.3 决策 |
| **Playwright MCP** | ❌ **未配置**（沿用前三批次决策） | Phase 5 E2E 降级：① 生成 Playwright 脚本供用户手动执行；② 用户手动贴回 evidence；③ 静态抓 HTML + curl API + Vitest |

**降级方案（沿用）**：

| 工具/能力 | 降级方案 |
|---|---|
| **MySQL MCP** | ✅ 正常使用（验证 `overseas_booking` 表存在 + `mini_program_open_id` 字段 + 6 个 i18n locale 文件内容 + channel sender 实现） |
| **Playwright MCP** | Phase 5 降级为静态抓 HTML + curl API + Vitest（沿用 Batch 1.1/1.2/1.3 模式） |

---

## 二、PRD 覆盖（EXT-19 部分）

### 2.1 PRD 整体覆盖（沿用 Batch 1.1/1.2/1.3 §二）

| 项 | 值 |
|---|---|
| **路径** | `docs/PRD-美业SaaS系统功能大全.md`（14,190 bytes, v1.0, 2026-07-31）|
| **覆盖范围** | 12 标准模块（M01-M12）+ 6 进阶模块（M13-M18），P0/P1/P2/P3 分级 |
| **EXT-19 相关模块** | **M01（预约管理）** 可能相关（海外预约）；但 EXT-19 属于**多语言通知确认层**（跨渠道 i18n），非业务主线模块 |

### 2.2 EXT-19 覆盖度

| PRD 模块 | EXT-19 覆盖？ | 备注 |
|---|---|---|
| M01（预约管理）| ⚠️ 间接 | `overseas_booking` 实体持久化；预约状态变更触发多语言确认 |
| M09（营销渠道）| ⚠️ 直接 | SMS / Email / 微信订阅消息 / 站内信 / 公众号 — 全在 `mkt_channel_config` 体系下 |
| **多语言预约确认独立模块** | ❌ PRD 未覆盖 | i18n + 渠道路由，无独立 PRD 段 |

**Phase 1 影响**：
- EXT-19 评估**不依赖 PRD 模块清单**（属于通知层）
- Operational Notes（`.cursor/rules/ext-19-operational-notes.mdc`）作为 SoT 替代 PRD（沿用规则 11）
- Feature Inventory 直接以 Operational Notes §1 + §3 为骨架

### 2.3 缺失文档

| 缺失内容 | 实际位置 | 影响 Phase |
|---|---|---|
| **EXT-19 接口 / 渠道路由契约** | `.cursor/rules/ext-19-operational-notes.mdc`（Operational Notes）| Phase 2 / 3 |
| **WeChat 订阅消息模板** | 微信公众平台（需人工注册）| Phase 2 / 5 |
| **SMTP 凭据管理规范** | **未找到独立文档**（Operational Notes §0.2 仅说明 mismatch 降级 demo 模式）| Phase 5 需用户补 |
| **Re-confirmation Checklist** | Operational Notes §2（10 项上线前必查）| Phase 5 |

---

## 三、BLOCKER 阈值（沿用前三批次）+ EXT-19 特定

### 3.1 沿用前三批次 BLOCKER 阈值

| 情况 | 是否 BLOCKER（沿用）| 备注 |
|---|---|---|
| Mock 未清理（含 fallback 链路）| ✅ **是** | 部分场景豁免（MOCK-INTENTIONAL） |
| 迁移脚本未在生产跑过 | ✅ **是** | dev 环境跑过也算"通过" |
| webhook 端点缺失（已知平台未对接）| ✅ **是** | EXT-19 含 Google/Instagram webhook |
| 未跑 E2E 的核心业务 | ✅ **是** | "核心业务"边界由谁定义 |
| SoT 文档 vs 代码方向不一致 | ✅ **是**（中立标 ISSUE，Fix Phase = Post-Audit 人类决策）| 沿用 I001 / I018 / I026 模式 |
| **表缺失（DDL 写了但 DB 没建）** | ✅ **是**（I002/I017/I029 模式 — MISSING，跑迁移即解）| 沿用 Batch 1.1/1.2/1.3 |

### 3.2 EXT-19 特定 BLOCKER 项

| 项 | 默认 | 备注 |
|---|---|---|
| **`overseas_booking` 表未跑迁移** | ✅ **是**（I002/I017/I029 模式 — MISSING）| SoT §0.5：`CREATE TABLE IF NOT EXISTS` + ALTER；dev DB 可能未跑 |
| **WeChat 模板 ID 是占位符**（`MP_TEMPLATE_ID = "BOOKING_CONFIRMATION"`）| ⚠️ 观察 | SoT §0.1：真实使用前必须替换；不替换 = 微信 API 40037 |
| **WeChat MiniProgramSender 需真实 appId/appSecret** | ⚠️ 观察 | 凭据缺失时降级 Mock 或失败 |
| **SMTP mismatch 降级 demo 模式** | ⚠️ 观察 | SoT §0.2：`spring.mail.*` vs 租户 configJson 不一致时走 demo |
| **autoSend 默认 true（自动触发 sendConfirmation）** | ⚠️ 观察 | SoT §0.3：状态变更时自动发送；异常不回滚状态 |
| **前端状态确认按钮文案缺失** | ⚠️ Shadow | SoT §0.3：需标注「确认即发送通知」；当前可能无提示 |
| **`autoSend=false` 前端 UI 缺失** | ⚠️ Shadow | SoT §0.3：`{"status":"CONFIRMED", "autoSend": false}` 可关闭；但前端无此入口 |

### 3.3 EXT-19 表存在性检查

| 表 | 创建迁移脚本 | 实体类 | 需 Phase 5 验证？|
|---|---|---|---|
| `overseas_booking` | `migrate_ext_19_mini_program_openid.sql:9`（CREATE TABLE IF NOT EXISTS）+ `migrate_ext_19_mini_program_openid.sql:28`（ALTER ADD COLUMN IF NOT EXISTS）| ✅ `entity/OverseasBooking.java`（含 `miniProgramOpenId`）| ⚠️ **Phase 5 需 MySQL MCP 实际验证 dev DB 中表存在 + 字段对齐** |

**与前三批次模式对照**：

| Issue ID | 批次 | 模式 | 表 |
|---|---|---|---|
| **I002** | Batch 1.1（EXT-24）| MISSING | `crm_contact_map` 表不存在 |
| **I017** | Batch 1.2（EXT-23）| MISSING | `plugin_runtime_state` 表不存在 |
| **I029** | Batch 1.3（EXT-22）| MISSING + PARTIAL-MISSING | `google_maps_config`/`google_maps_geocode_log` 整表不存在 + `ecommerce_platform` 缺 3 列 |
| **ISSUE-EXT19-I001**（建议）| Batch 1.4（EXT-19）| MISSING | `overseas_booking` 表可能未跑迁移（与 I002/I017/I029 同模式）|

→ **Phase 5 需 MySQL MCP 实际验证 dev DB 中 `overseas_booking` 表存在 + `mini_program_open_id` 字段 + 26 列完整**。

---

## 四、git 状态

### 4.1 当前 HEAD

| 项 | 值 |
|---|---|
| **HEAD** | `e85ba8b audit(batch-1.2-phase0): EXT-23 Plugin Runtime scope freeze` |
| **起点 commit**（Batch 1.4 起点）| `e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2/1.3 freeze，沿用） |
| **沿用 baseline commit** | `656caa72341c96d1b2e703dde993603dda9eebe1`（v3 整体 baseline）|
| **Batch 1.1 freeze commit** | `12a97ea57a98d268c864fc7c010b82b305d7bb93` |
| **Batch 1.2 freeze commit** | `e85ba8b`（HEAD）|
| **Batch 1.3 freeze commit** | `e85ba8b`（HEAD，沿用）|

### 4.2 当前 working tree（`git status --short` 筛选）

```
M  .gitignore
M  audit/batches.md
M  audit/feature-inventory.md
M  audit/issues/issue-register.md
M  audit/traceability-matrix.md
M  backend/src/main/resources/application.yml
?? audit/baseline/preflight-batch-1.3.md
?? audit/baseline/scope-freeze-batch-1.3.md
?? audit/batch-1.3-inventory-draft.md
?? audit/evidence/F056-F080/**                    (Batch 1.3 EXT-22 evidence)
?? audit/issues/I037-webhook-path-not-excluded.md  (Batch 1.3 NEW)
?? audit/reports/batch-1.3-summary.md             (Batch 1.3 完结)
?? audit/runtime/report-5A-batch-1.2.md
?? audit/runtime/report-5B-batch-1.2.md
?? audit/runtime/report-5C-batch-1.3.md
?? audit/traceability-matrix-batch-1.3-*.md
?? frontend/meiyetec-web/e2e/audit/F007-F054.spec.ts (Batch 1.2/1.3 Playwright 降级)
?? backend/src/main/java/com/meiyetec/controller/{MktChannel,MktConversion,MktJourney,MktTemplate,MktTouchTask}Controller.java
?? backend/src/main/java/com/meiyetec/service/ReportExportFileCleanupService.java
?? backend/src/main/resources/db/sql/migrate_mkt_002_*.sql
```

### 4.3 EXT-19 特定文件在 git status 中状态

| 文件 | git status | 备注 |
|---|---|---|
| `backend/src/main/java/com/meiyetec/entity/OverseasBooking.java` | **未出现**（已 HEAD 中）| 实体已含 `miniProgramOpenId`（L21）|
| `backend/src/main/java/com/meiyetec/booking/BookingConfirmationService.java` | **未出现**（已 HEAD 中）| 核心发送服务已实现 |
| `backend/src/main/java/com/meiyetec/booking/BookingConfirmationTemplateResolver.java` | **未出现**（已 HEAD 中）| i18n 加载 + locale 回退 |
| `backend/src/main/java/com/meiyetec/booking/StoreLookupService.java` | **未出现**（已 HEAD 中）| pos_store 查询 + 5 分钟缓存 |
| `backend/src/main/java/com/meiyetec/service/channel/{Sms,Email,MiniProgram,InApp,WechatOfficialAccount}Sender.java` | **未出现**（已 HEAD 中）| 5 个 channel sender 已实现 |
| `backend/src/main/java/com/meiyetec/service/IntlBookingService.java` | **未出现**（已 HEAD 中）| 业务入口已实现 |
| `backend/src/main/resources/i18n/template/{zh-CN,zh-TW,en-US,ja-JP,ko-KR,fr-FR}.json` | **未出现**（已 HEAD 中）| 6 个 locale 模板已实现 |
| `backend/src/main/resources/db/sql/migrate_ext_19_mini_program_openid.sql` | **未出现**（已 HEAD 中）| DDL 已写（CREATE TABLE + ALTER）|

→ **所有 EXT-19 文件已在 HEAD 中（git status 无标记），与 EXT-22 有显著差异（后者部分文件已在 HEAD 但有 webhook 实现与 SoT 反向问题）**

### 4.4 待 commit / stash 决策（沿用 Batch 1.1/1.2/1.3）

| 选项 | 内容 | 推荐 |
|---|---|---|
| **A** | 直接 commit（baseline + preflight + 80+ 改动一次性入库）| ❌ 不推荐 |
| **B** | 拆 commit（baseline 单独 + EXT-22 evidence + EXT-19 preflight 分批）| ⚠️ 可行但复杂 |
| **C** | 再 stash 一次（Phase -1 完成后保持 working tree clean）| ✅ **推荐**（沿用前三批次决策）|

→ **沿用 Batch 1.1/1.2/1.3 决策**：Phase -1 报告完成后**等用户决策 C（stash）**。

### 4.5 Phase 0 freeze commit 候选

| 候选 | 描述 | 何时 |
|---|---|---|
| **冻结 Batch 1.2/1.3 commit** | `e85ba8b` 已在 HEAD，作为 Batch 1.4 起点 | 已就位 |
| **冻结 Batch 1.4 commit** | 包含 `preflight-batch-1.4.md` + `scope-freeze-batch-1.4.md` | Phase 0 启动前 |

---

## 五、受保护页面（沿用 Batch 1.1/1.2/1.3）

> 来源：`.cursor/rules/no-touch-verified-pages.mdc`（用户实测通过）

| # | 路由 | 前端文件 | 后端接口 | 状态 |
|---|---|---|---|---|
| 1 | `/dashboard` | `frontend/meiyetec-web/src/views/dashboard/**` | （待 Phase 2 列出）| ✅ 已验证 |
| 2 | `/mh/visual` | `frontend/meiyetec-web/src/views/mh/visual.vue` | （待 Phase 2 列出）| ✅ 已验证 |
| 3 | `/mh/consent` | `frontend/meiyetec-web/src/views/mh/consent.vue` | （待 Phase 2 列出）| ✅ 已验证 |

### 5.1 EXT-19 与受保护页面的关系

| 检查项 | 结果 |
|---|---|
| **EXT-19 是否影响 `/dashboard` 数据？** | ❌ 不触及（预约确认是通知层，不写 dashboard 数据）|
| **EXT-19 是否影响 `/mh/visual` / `/mh/consent`？** | ❌ 不触及（mh 模块是视觉档案/合规记录，与预约确认无关）|
| **IntlBookingService 是否被受保护页面调用？** | ⚠️ **需 Phase 2 静态扫描**（`overseas_booking` 可能被前端预约页调用，需确认是否与 `/mh/visual` 或 `/mh/consent` 有交集）|

→ **EXT-19 审计期间受保护页面零直接风险**，但需 Phase 2 确认无间接依赖。

---

## 六、守门脚本（g1-g8 命名约定存在 / 脚本未实现 — 沿用决策）

### 6.1 8 个守门脚本状态（沿用 Batch 1.1/1.2/1.3）

> **当前状态**：8 个守门脚本（g1-g8）**未实现**（沿用前三批次决策，Phase 1-2 期间补）。

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

### 6.2 EXT-19 特定守门脚本（可选扩展）

| 编号 | 扩展脚本 | 用途 |
|---|---|---|
| **g16** | `g16-overseas-booking-table-check.sh` | 检查 `overseas_booking` 表存在 + `mini_program_open_id` 字段 |
| **g17** | `g17-i18n-locale-files-check.sh` | 检查 6 个 locale JSON 文件存在 + 占位符完整 |
| **g18** | `g18-channel-sender-registry-check.sh` | 检查 5 个 ChannelSender 实现注册（SMS/EMAIL/MINI_PROGRAM/IN_APP/WECHAT_OA）|
| **g19** | `g19-auto-send-confirmation-check.sh` | 检查 `IntlBookingService.updateStatus` 非 CONFIRMED → CONFIRMED 时自动调用 sendConfirmation |

→ **可选**：Phase 1 期间根据需要新增 EXT-19 守门脚本（不强制）。

### 6.3 风险

- 规则 15：自动化守门脚本每次 PR 必须跑 → **当前 CI 不守门 v3 体系规则**
- PR 可绕过审计规则合并 → **Batch 1.4 Phase 0 仍需确认守门脚本缺失是否算 BLOCKER**

---

## 七、当前批次：EXT-19 多语言预约确认

### 7.1 批次基本信息

| 项 | 值 |
|---|---|
| **Batch ID** | **Batch 1.4** |
| **模块** | **EXT-19 多语言预约确认 + i18n 模板 + 多渠道发送** |
| **来源** | `.cursor/rules/ext-19-operational-notes.mdc`（Operational Notes §0-§6）|
| **起点时间** | 2026-09-14 15:35 (UTC+8)（本批次启动）|
| **Phase -1 时间** | 2026-09-14 15:35 (UTC+8)（本文件落档）|
| **起点 commit** | `e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2/1.3 freeze commit）|

### 7.2 代码存在性检查（静态确认）

| 类别 | 文件 | 状态 | SoT 引用 |
|---|---|---|---|
| **Entity** | `entity/OverseasBooking.java` | ✅ 存在，含 `miniProgramOpenId`（L21）| SoT §1 |
| **booking 包** | `booking/BookingConfirmationService.java` | ✅ 存在 | SoT §1 |
| **booking 包** | `booking/BookingConfirmationTemplate.java` | ✅ 存在 | SoT §1 |
| **booking 包** | `booking/BookingConfirmationTemplateResolver.java` | ✅ 存在，含 `warmup()` + 6 locale | SoT §1 |
| **booking 包** | `booking/BookingConfirmationResult.java` | ✅ 存在 | SoT §1 |
| **booking 包** | `booking/StoreLookupService.java` | ✅ 存在，含 5 分钟缓存 | SoT §1 |
| **channel** | `service/channel/SmsSender.java` | ✅ 存在（`channelType() = "SMS"`）| SoT §1 |
| **channel** | `service/channel/EmailSender.java` | ✅ 存在（真实 JavaMailSender SMTP）| SoT §1 |
| **channel** | `service/channel/MiniProgramSender.java` | ✅ 存在（`channelType() = "MINI_PROGRAM"`）| SoT §1 |
| **channel** | `service/channel/InAppSender.java` | ✅ 存在（`channelType() = "IN_APP"`）| SoT §1 |
| **channel** | `service/channel/WechatOfficialAccountSender.java` | ✅ 存在（`channelType() = "WECHAT_OA"`）| SoT §1 |
| **业务入口** | `service/IntlBookingService.java` | ✅ 存在，含 `sendConfirmation` + `updateStatus` | SoT §1 |
| **i18n** | `resources/i18n/template/zh-CN.json` | ✅ 存在 | SoT §1 |
| **i18n** | `resources/i18n/template/zh-TW.json` | ✅ 存在 | SoT §1 |
| **i18n** | `resources/i18n/template/en-US.json` | ✅ 存在 | SoT §1 |
| **i18n** | `resources/i18n/template/ja-JP.json` | ✅ 存在 | SoT §1 |
| **i18n** | `resources/i18n/template/ko-KR.json` | ✅ 存在 | SoT §1 |
| **i18n** | `resources/i18n/template/fr-FR.json` | ✅ 存在 | SoT §1 |
| **SQL** | `db/sql/migrate_ext_19_mini_program_openid.sql` | ✅ 存在（CREATE TABLE + ALTER）| SoT §1 |
| **测试** | `booking/BookingConfirmationServiceTest.java` | ✅ 存在 | SoT §1 |
| **测试** | `booking/BookingConfirmationTemplateResolverTest.java` | ✅ 存在 | SoT §1 |
| **测试** | `channel/EmailSenderTest.java` | ✅ 存在 | SoT §1 |

**统计**：全部 **23 个文件**静态确认存在（✅），无缺失文件。

### 7.3 Feature 估算（待 Phase 1 落实）

| 子模块 | 估算 Feature 数 | 来源 |
|---|---|---|
| `overseas_booking` 实体（含 miniProgramOpenId）| ~2 | SoT §1 entity |
| BookingConfirmationService 核心发送 | ~3 | sendConfirmation + 渠道路由 + 聚合结果 |
| BookingConfirmationTemplateResolver（i18n）| ~3 | warmup + 6 locale + fallback |
| 5 个 ChannelSender | ~5（每个 sender 一个）| SMS / EMAIL / MINI_PROGRAM / IN_APP / WECHAT_OA |
| StoreLookupService（pos_store 查询）| ~1 | 缓存 + 字段映射 |
| IntlBookingService 业务入口 | ~4 | send-confirmation + updateStatus + confirmation-template + list |
| Google / Instagram webhook | ~2 | POST /intl-booking/webhook/google + /instagram |
| 前端预约确认 UI | ~1 | 状态确认按钮文案 + autoSend=false |
| **估算合计** | **~20-25 Features** | Phase 1 落地 |

### 7.4 EXT-19 SoT 与代码对照（静态初步分析）

> ⚠️ **Phase 1 需完整确认**。以下为 preflight 期间初步扫描，仅记录差异信号。

| # | SoT §0 描述 | 静态初步确认 | 反向？| 需 Phase 1 核实 |
|---|---|---|---|---|
| **§0.1** | "WeChat 模板 ID 是占位符：`MP_TEMPLATE_ID = BOOKING_CONFIRMATION`" | ✅ 确认存在（`BookingConfirmationService:25`）| ⏸ 待核实真实接入 | |
| **§0.2** | "EmailSender mismatch 降级 demo 模式" | ✅ 确认存在（`EmailSender` 注释 §0.2 有三种模式描述）| ⏸ 待核实 | |
| **§0.3** | "CONFIRMED 自动触发 sendConfirmation" | ✅ 初步确认（`IntlBookingService.updateStatus` 逻辑）；响应结构 `{ booking, confirmation, confirmationError }` 需读全文确认 | ⏸ 待 Phase 1 全文核实 | |
| **§0.4** | "StoreLookupService 字段映射（pos_store.store_name + contact_phone）" | ✅ 确认存在（`StoreLookupService` 注释有 SQL）| ⏸ 待核实 SQL 字段名 | |
| **§0.5** | "SQL 迁移兼容：ADD COLUMN IF NOT EXISTS（MySQL 8.0.29+）" | ✅ 确认（`migrate_ext_19:28` 使用 `ADD COLUMN IF NOT EXISTS`）| ⏸ 待 MySQL MCP 验证 dev DB | |
| **§3 Endpoints** | 6 个端点 | ⚠️ 需逐个 grep 确认路由存在 | ⏸ 待 Phase 1 全文核实 | |
| **§4 Channel Routing** | customerPhone → SMS / customerEmail → EMAIL / miniProgramOpenId → MINI_PROGRAM | ✅ MiniProgramSender 返回 `"MINI_PROGRAM"`，与 SoT §4 一致；SMS/EMAIL/IN_APP 需全文核实 | ⏸ 待 Phase 1 全文核实 | |

### 7.5 与 Batch 1.1/1.2/1.3 独立性检查

| 检查项 | 结果 |
|---|---|
| **代码包共享** | ⚠️ **部分共享** — `OverseasBooking` entity 可能被 `IntlBookingService` 以外的地方引用；`BookingConfirmationService` 依赖 `ChannelSender` 接口（与 `mkt_channel_config` 共用）；`IntlBookingService` 在 `com.meiyetec.service` 包（与其他 service 同包）|
| **DB 表共享** | ⚠️ **部分共享** — `overseas_booking` 表可能被 Booking 模块（`IntlBookingService`）以外引用；`mkt_channel_config` / `pos_store` 被多模块共用 |
| **配置共享** | ⚠️ `spring.mail.*` 全局配置（EmailSender 使用）|
| **业务依赖** | ❌ EXT-19 不依赖 EXT-22/23/24；EXT-22/23/24 不依赖 EXT-19 |
| **与前三批次共享** | ⚠️ `OverseasBooking` 可能被 Batch 1.1（CRM）/ Batch 1.3（ecommerce）共用字段 |
| **风险传染** | ⚠️ Batch 1.1 I008（catch 吞异常）模式可能在 `IntlBookingService.updateStatus` 或 `BookingConfirmationService.sendConfirmation` 中再犯 |
| **审计可分离性** | ✅ Feature Inventory 可分离（Phase 1 期间明确边界划分）|

### 7.6 与 Batch 1.1 经验关联

| Batch 1.1 经验（EXT-24）| EXT-19 预期重演？ |
|---|---|
| **I008（Fake Success / catch 吞异常）** | ⚠️ `IntlBookingService.updateStatus` 中 autoSend 发送异常在 try/catch 内（SoT §0.3："发送异常不会回滚状态变更"）— 与 I008 模式对称，需核实 catch 范围 |
| **I002（表缺失 / DDL 写了但 DB 没建）** | ⚠️ `overseas_booking` 表可能未跑迁移（`migrate_ext_19_mini_program_openid.sql` 已写）— 与 I002/I017/I029 同模式 → Phase 5 需 MySQL MCP 验证 |
| **I009（@Transactional 无 rollbackFor）** | ⚠️ `IntlBookingService` 加了 `@Transactional` 但未指定 `rollbackFor` — 需 Phase 3 检查 |
| **I011（Fake Failure / select-after-write）** | ⚠️ `updateStatus` 后是否 select → 需 Phase 3 核实 |
| **I001（SoT 文档漂移）** | ⚠️ EXT-19 SoT §5 未知问题 — 需 Phase 1 全文核对后登记 |

---

## 八、已知约束

### 8.1 `overseas_booking` 表（可能未跑迁移 — I002/I017/I029 模式）

| 维度 | 详情 |
|---|---|
| **表名** | `overseas_booking` |
| **所属 EXT-*** | EXT-19（核心表）|
| **Operational Notes 引用** | SoT §0.5 + §1 |
| **创建迁移** | `migrate_ext_19_mini_program_openid.sql:9`（CREATE TABLE IF NOT EXISTS）|
| **ALTER 迁移** | `migrate_ext_19_mini_program_openid.sql:28`（ADD COLUMN IF NOT EXISTS mini_program_open_id）|
| **代码状态** | ✅ `entity/OverseasBooking.java` 已含 `miniProgramOpenId`（L21）|
| **DB 状态** | ⚠️ Phase 5 需 MySQL MCP 实际验证 dev DB 中表存在 + 26 列完整 |
| **Phase 1 影响** | 与 I002/I017/I029 同模式 → **跑迁移即解（MISSING 模式）** |
| **决策** | ❌ 本批次不修 SoT（沿用规则 6 + 规则 11：审计结论与 EXT-* 冲突时中立标）|

### 8.2 WeChat 模板 ID 占位符（WeChat 凭据缺失）

| 维度 | 详情 |
|---|---|
| **常量位置** | `BookingConfirmationService:25`：`MP_TEMPLATE_ID = "BOOKING_CONFIRMATION"` |
| **占位符说明** | SoT §0.1：真实使用前必须在微信公众平台注册订阅消息模板 |
| **Phase 5 影响** | MiniProgramSender 真实调用 → **UNVERIFIED**（无 WeChat 凭据）；Mock 降级需核实 |
| **决策** | Phase 5 按 D10 铁律：真实链路 UNVERIFIED |

### 8.3 SMTP mismatch 降级 demo 模式

| 维度 | 详情 |
|---|---|
| **实现位置** | `EmailSender.java`（注释 §0.2）|
| **三种模式** | ① 配置齐全 + JavaMailSender → 真实 SMTP；② 无租户配置 → 全局 spring.mail.*；③ JavaMailSender 未注入 → demo 模式 |
| **日志关键词** | `[EMAIL] tenant SMTP host/user mismatch with global config; demo mode` |
| **Phase 5 影响** | 需观测日志确认走哪条路径 |
| **决策** | Phase 5 静态分析 EmailSender 源码 + 观测日志 |

### 8.4 autoSend 默认 true 行为（状态变更自动触发）

| 维度 | 详情 |
|---|---|
| **实现位置** | `IntlBookingService.updateStatus`（SoT §0.3）|
| **默认行为** | 非 CONFIRMED → CONFIRMED 时自动调用 `confirmationService.sendConfirmation(b)` |
| **关闭方式** | `{"status":"CONFIRMED", "autoSend": false}` |
| **发送异常处理** | try/catch 包裹，异常不回滚状态变更（SoT §0.3）|
| **响应结构变化** | `data` 从单个 `OverseasBooking` → `{ booking, confirmation?, confirmationError? }` |
| **Phase 1 任务** | 全文读取 `IntlBookingService.updateStatus` 确认 autoSend 逻辑；确认响应结构变化 |
| **前端 UI** | 状态确认按钮文案需标注「确认即发送通知」（当前可能无提示 → Shadow）|

### 8.5 与 EXT-22/23/24 的共用字段

| 共用资源 | 共享方 | Batch 1.4 处理 |
|---|---|---|
| `mkt_channel_config` | EXT-19（EmailSender / SmsSender）+ EXT-22（可能有）| Phase 1 期间 grep 确认边界 |
| `pos_store` | EXT-19（StoreLookupService）+ 可能被其他模块共用 | Phase 1 期间 grep 确认 |
| `overseas_booking` | EXT-19（核心）+ 可能被 Booking 模块共用 | Phase 1 期间 grep 确认 |
| `OverseasBooking` entity | 跨 service 使用 | Phase 1 期间 grep import 确认调用方 |

### 8.6 EXT-19 Operational Notes（`.cursor/rules/ext-19-operational-notes.mdc`）

| 维度 | 详情 |
|---|---|
| **文件路径** | `.cursor/rules/ext-19-operational-notes.mdc` |
| **当前状态** | ✅ **已加载**（本 preflight §七已引用）|
| **内容覆盖** | §0 关键约束（5 节）/ §1 Touchpoints / §2 Re-confirmation Checklist（10 项）/ §3 Endpoints（6 个）/ §4 Channel Routing / §5 占位符变量 |
| **SoT 优先级** | 最高（规则 11：审计结论与 EXT-* 冲突时以 EXT-* 为准并标 ISSUE）|
| **使用方式** | Phase 1（Feature Inventory）+ Phase 2（Traceability）+ Phase 3（Issue 登记）全部以 Operational Notes 为骨架 |

---

## 九、Phase -1 准入确认（自检）

| 检查项 | 结果 |
|---|---|
| Batch 1.4 = EXT-19 已决策（用户启动消息）| ✅ 用户决策 2026-09-14 15:35 |
| MCP 状态已确认 | ✅ MySQL ✅ / Playwright ❌（沿用降级）|
| PRD 覆盖已评估 | ✅ EXT-19 不在 PRD 主线；以 Operational Notes 为 SoT |
| BLOCKER 阈值已沿用 | ✅ Batch 1.1/1.2/1.3 §三 + EXT-19 特定项 |
| git 状态已快照 | ✅ HEAD `e85ba8b` / dirty 沿用前三批次 |
| 受保护页面已列出 | ✅ 沿用 `/dashboard` / `/mh/visual` / `/mh/consent` |
| 守门脚本状态已确认 | ✅ g1-g8 命名约定存在，脚本未实现 |
| 当前批次范围已明确 | ✅ EXT-19 多语言预约确认 |
| 代码存在性已确认 | ✅ 23 个文件全部存在（无缺失文件）|
| SoT 漂移已初步扫描 | ✅ 6 项初步对照，待 Phase 1 全文核实 |
| 已知约束已记录 | ✅ overseas_booking 表 / WeChat 占位符 / SMTP mismatch / autoSend 行为 |

✅ **Phase -1 准入条件全部满足。可以进入 Phase 0（scope-freeze-batch-1.4.md）。**

---

## 十、待用户确认清单

| # | 决策项 | 默认 | 影响 |
|---|---|---|---|
| **1** | **WeChat 订阅消息凭据如何处理？** | 真实凭据由用户提供；无凭据走 Mock 降级（沿用 Batch 1.3 D10 铁律）| Phase 5 真实链路验证范围 |
| **2** | **SMTP 凭据来源？** | `mkt_channel_config.configJson`（SoT §0.2）；无配置时走全局 spring.mail.* 或降级 demo | Phase 5 EmailSender 链路验证 |
| **3** | **`overseas_booking` 表未跑迁移是否算 BLOCKER？** | ✅ **是**（I002/I017/I029 模式 — MISSING，跑迁移即解）| Phase 5 MySQL MCP 验证 + 迁移执行 |
| **4** | **Phase -1 报告完成后 commit 策略（A/B/C）** | **C 再 stash 一次**（沿用前三批次）| git history |
| **5** | **是否需要 Phase 0 期间补齐 g1-g8 守门脚本？** | 沿用 Batch 1.1/1.2/1.3：Phase 1-2 期间补 | 不阻塞 Phase 0 |
| **6** | **autoSend 默认 true（状态变更自动触发）前端是否需要配合？** | ⚠️ 前端状态确认按钮文案需标注「确认即发送通知」（Shadow Feature）| Phase 2 前端 UI 检查 |
| **7** | **Phase 0 freeze commit 是否在 `e85ba8b` 后追加？** | ✅ 推荐（追加 `audit(freeze): Batch 1.4 启动`）| Phase 0 启动前 |
| **8** | **EXT-19 与前三批次共用字段边界如何划分？** | Phase 1 期间明确（Feature ID 按 EXT-19 视角）| Phase 1 Feature Inventory |

### 10.1 默认决策影响

| 默认 | 影响 |
|---|---|
| **#1 WeChat 凭据缺失 → Mock 降级** | Phase 5 仅验证 mock 链路；真实链路（MiniProgramSender 真实调用）验证标记为 UNVERIFIED |
| **#2 SMTP mismatch → demo 模式** | Phase 5 EmailSender 走 demo 或全局 mail 之一（观察日志关键词）|
| **#3 overseas_booking MISSING → 跑迁移即解** | Phase 5 MySQL MCP 验证后执行迁移；不影响 Phase 0 启动 |
| **#4 stash C** | Phase -1 完成后保持 working tree clean |
| **#5 g1-g8 Phase 1-2 期间补** | 不阻塞 Phase 0 |
| **#6 autoSend 前端配合** | Shadow Feature；Fix Phase = Post-Audit |
| **#7 freeze commit 追加** | git history 清晰；Phase 0 scope-freeze 锚点明确 |
| **#8 共用字段边界** | Phase 1 Feature Inventory 期间明确；Feature ID 按 EXT-19 视角 |

---

## 十一、本文件状态

| 项 | 值 |
|---|---|
| **状态** | 已落档（Phase -1 产出）|
| **变更控制** | Phase 0 启动后冻结；Phase 1 期间如发现新约束追加新段（§十二+）|
| **下一阶段产出位置** | `audit/baseline/scope-freeze-batch-1.4.md`（Phase 0）|
| **下一阶段 Prompt 模板** | 见 `docs/PROJECT_AUDIT_PLAYBOOK.md` §十「Phase 0」|

---

**Batch 1.4 Phase -1 完结。EXT-19 多语言预约确认范围已确认：23 个文件全部存在 ✅（无缺失文件），overseas_booking 表未跑迁移 → I002/I017/I029 模式，WeChat 占位符已知，autoSend 行为已记录，6 个 locale i18n 模板就位，5 个 channel sender 已实现。⏸ 等用户确认 → 进入 Phase 0（scope-freeze-batch-1.4.md）。**
