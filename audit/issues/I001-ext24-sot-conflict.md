# Issue I001 — EXT-24 .mdc (SoT) vs 代码实现冲突

**创建时间**：2026-09-12 21:42 (UTC+8)
**Batch**：1.1 — EXT-24 CRM 集成
**SoT 引用**：`.cursor/rules/ext-24-crm-integration.mdc`（**仅作为参考** — 本 issue 标记其与代码不一致）
**Phase**：1（仅记录，不修复）

---

## 一、冲突点

### 冲突 1.1 — HUBSPOT Adapter 状态

| 维度 | SoT（`.mdc` §0.6 表格）声称 | 代码实际 |
|---|---|---|
| Adapter 类 | "**未实现**（前端可选会 500）" | `HubSpotCrmAdapter.java`（219 行）完整实现 |
| 鉴权方式 | — | OAuth2 refresh-token 流 + PAT fallback（`HubSpotCrmAdapter.java:107-156`）|
| 拉取 API | — | POST `/crm/v3/objects/contacts/search`（`HubSpotCrmAdapter.java:170-218`）|
| Upsert API | — | POST `/crm/v3/objects/contacts/batch/upsert`（by email，`HubSpotCrmAdapter.java:230-280`）|
| 默认字段映射 | `.mdc` §6 提及但未注册 | `hs_object_id / firstname / lastname / email / phone / jobtitle / hs_lastmodifieddate`（`HubSpotCrmAdapter.java:60-69`）|

**结论**：HubSpot Adapter **已实现**，文档 §0.6 / §1 漏列、§6 仍标"待添加"。

### 冲突 1.2 — ZOHO Adapter 状态

| 维度 | SoT（`.mdc` §0.6 表格）声称 | 代码实际 |
|---|---|---|
| Adapter 类 | "**未实现**（前端可选会 500）" | `ZohoCrmAdapter.java`（230 行）完整实现 |
| 鉴权方式 | — | OAuth2 refresh-token 流（`ZohoCrmAdapter.java:120-170`）|
| 拉取 API | — | COQL POST `/crm/v6/contacts/search`（`ZohoCrmAdapter.java:185-237`）|
| Upsert API | — | POST `/crm/v6/Contacts/upsert`（by Email，`ZohoCrmAdapter.java:250-300`）|
| 域名推导 | — | `deriveAccountsBase` 自动从 apiBaseUrl 推导 `accounts.zoho.com / .com.cn / .eu / .in / .com.au`（`ZohoCrmAdapter.java:172-180`）|

**结论**：Zoho Adapter **已实现**，文档 §0.6 / §1 漏列。

### 冲突 1.3 — Router 异常处理

| 维度 | SoT（`.mdc` §5 已知问题）声称 | 代码实际 |
|---|---|---|
| `CrmAdapterRouter.getAdapter(null / unknown)` | "返回 null → NPE" | 实际 fallback 到 `MOCK`（`CrmAdapterRouter.java:21-29`） |
| 未注册 platform 的行为 | "前端可选会 500" | 实际自动回落到 Mock Adapter，返回 mock 数据 |

**结论**：Router **有 Mock fallback**，文档 §5 描述与代码不符。

### 冲突 1.4 — `crm_integration` 字段遗漏（事实补充）

`.mdc` §0.6 Salesforce 凭据只列 `clientId / username / privateKey / loginUrl` 四件套，但 `entity/CrmIntegration.java` 还有：

| 字段 | 用途 | .mdc 是否提及 |
|---|---|---|
| `clientSecret` | HubSpot OAuth2 client_secret | ❌ |
| `accessToken` | OAuth2 缓存 | ❌（仅 Salesforce token 缓存提及） |
| `refreshToken` | OAuth2 refresh 流 | ❌ |
| `tokenExpireAt` | 缓存命中判断 | ❌ |
| `apiBaseUrl` | HubSpot / Zoho 自定义端点 | ❌ |
| `objectMappings` | 字段映射 JSON | ✅ §0.2 |
| `syncWindowMinutes` | 排程窗口 | ✅ §0.4 |
| `lastIncrementalSyncAt` | 排程游标 | ✅ §0.4 |
| `syncToken` | 平台侧游标 | ✅ §0.2 |
| `syncSchedule` | cron 表达式 | ✅ §0.4 |
| `syncEnabled` | 排程开关 | ✅ §0.4 |
| `autoSync` / `syncMembers` / `syncOrders` / `syncContacts` / `syncDeals` | 老字段，DTO 中保留 | ❌ |

---

## 二、影响范围

### 2.1 受影响 Feature（来自 `audit/feature-inventory.md` Batch 1.1）

| Feature | Result 标签 | 原因 |
|---|---|---|
| **F016** HubSpot Adapter | **CONFLICT** | SoT 标 MISSING，代码实现完整 |
| **F017** Zoho Adapter | **CONFLICT** | SoT 标 MISSING，代码实现完整 |
| **F018** CrmAdapterRouter | **CONFLICT** | SoT 标"NPE"，实际有 MOCK fallback |
| **F014** Salesforce Adapter | OK | 与 SoT §0.2 / §0.6 一致 |
| **F015** Mock Adapter | OK | 与 SoT §0.6 `@ConditionalOnMissingBean` 一致 |
| **F013** CrmAdapter 接口 | OK | 与 SoT §0 一致 |
| **F019** CrmIntegration 实体 | OK（但 §0.6 字段列表不完整） | 见冲突 1.4 |

### 2.2 受影响 Phase

| Phase | 是否阻塞 |
|---|---|
| Phase 2 Traceability Matrix | ❌ 不阻塞（仅标 CONFLICT） |
| Phase 3 业务测试矩阵 | ❌ 不阻塞（CONFLICT Feature 跳过业务 E2E） |
| Phase 5 运行时验证 | ⚠ HubSpot / Zoho 可走真实端到端（API key 即可） |
| Phase 7 上线前 Checklist | ⚠ .mdc 必须同步更新，否则下次审计再走错 |

---

## 三、建议处理（**仅记录，待用户决策**）

按 `project-audit.mdc` 规则 11："审计结论与 EXT-* Operational Notes 冲突时，**以 EXT-* 为准并标 ISSUE**"。

但本 issue 揭示的是 **EXT-* 文档本身与代码不一致** — 这不是审计侧的矛盾，而是 **EXT-* 文档需要更新**。

| 方案 | 改动范围 | 优点 | 缺点 |
|---|---|---|---|
| **A. 更新 `.mdc` §0.6 / §1 / §5** | 单文件就地修订 | 单一 SoT 维持权威 | 改动大；§5 已知问题整段重写 |
| **B. 新建 `ext-24b-hubspot-zoho.mdc`** | 新增文件，旧 .mdc 标注"已过时" | 不动 SoT 主文档 | 出现两份"事实来源" |
| **C. 不动 .mdc，在审计侧记 CONFLICT** | 仅 `audit/issues/` | 最快 | 下次审计还会按 .mdc 走错 |

**用户决策**：**A 或 C 或 B？** 当前默认：**C（不修 .mdc，只在审计侧记 CONFLICT）**（用户指令 "不改 ext-24.mdc"）。

---

## 四、未确认项（标记"未确认"，禁止猜测）

| # | 项 | 当前信息 | 待补来源 |
|---|---|---|---|
| 1 | HubSpot / Zoho 是否在生产环境跑过真实端到端 | 仅代码静态可达 L0 | Phase 5 真实沙箱调用 |
| 2 | `privateKey` WRITE_ONLY 是否真生效（前端拉列表是否泄漏） | 仅 entity `@JsonProperty(WRITE_ONLY)` | Phase 5 curl 验证 GET `/crm-integration/list` 响应 |
| 3 | Salesforce `objectMappings` 中的 `Email__c` 自定义字段是否真生效 | 静态代码 merge 到 SOQL | Phase 5 SF 沙箱实测 |

---

**本 Issue 在 Phase 2 traceability-matrix 中对 F016 / F017 / F018 标 `CONFLICT` Result。**
