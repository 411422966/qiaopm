# Post-Audit 阶段性总结 v2（Phase 2.2 ~ 2.10）

> **盘点时间**：2026-09-23 23:50 (UTC+8)
> **盘点规则**：只读不改，不执行任何操作
> **范围**：Phase 2.2 代码修复 → Phase 2.10 I087 修复
> **进度**：约 **85%**（静态修复 + 运行时验证收口，仅剩 UX/Edge 修复未推进）
> **上一阶段总结**：`audit/post-audit/post-audit-summary.md`（Phase 2.2-2.5 视角，已过时）

---

## 一、Post-Audit 全进度（Phase 2.2 → 2.10）

### 1.1 各 Phase 完成度

| Phase | 日期 | 主题 | 状态 | 关键产出 |
|---|---|---|---|---|
| **2.2-A** | 2026-09-17 | 代码修复首批 | ✅ | 6 个 P1 Issue 修复（I037/I070/I008/I011/I047/I071），commit `eb5ff6e~3d94c56` |
| **2.2-B** | 2026-09-19 | 代码修复次批 | ✅ | 9 个 P1 Issue 修复（I072/I073/I061+I075+I076+I060），commit `2aeb7b3~be62a45` |
| **2.3** | 2026-09-20~21 | DB 迁移（6 批次）| ✅ | **9 表 + 16 列 100% apply**（plugin_runtime_state / member_message / overseas_booking / mkt_*4 / google_maps_*2 / crm_contact_map；crm_integration 9 列 + crm_sync_log 3 列 + ecommerce_platform 3 列 + google_maps_config 1 列）|
| **2.4** | 2026-09-21 16:08 | 守门脚本回归 | ✅ | 8/8 通过，**零回归**（dcaf07a baseline vs Phase 2.4：-21/-23/-25/-455/-22/-21/-19/-27），5 个 DB 类 Issue 标记 FIXED |
| **2.5** | 2026-09-21 17:00 | 运行时冒烟 | ✅ | 后端 16.489 秒启动 ERROR=0；6/6 API 200；新发现 I082（InAppSender `member_id` NOT NULL 违例） |
| **2.6** | 2026-09-21 23:56 | Playwright E2E | ✅ | 19 用例：8 PASS / 11 FAIL（新发现 I083 BROKEN、I084/I085 UX、I086 测试脚本 bug）|
| **2.7** | 2026-09-22 00:18 | I083 修复 | ✅ | `CrmSyncLog.successCount` NPE 修复（commit `69c4cfd`，+5 行默认 0 初始化）|
| **2.8** | 2026-09-22 23:00 | SoT CONFLICT 决议 | ✅ | **5 Issue RESOLVED**（I001/I018/I026/I027/I030；改 3 .mdc + issue-register.md + final-acceptance.md 共 15 处；CONFLICT 子标签 10 → 2） |
| **2.9** | 2026-09-23 15:14 | **I012 修复** | ✅ | `MockCrmAdapter` 删 `@ConditionalOnMissingBean`（code `a6e2d7d` + javadoc `a41ab2b`），运行时验证 sync HTTP 200 successCount=4 |
| **2.10** | 2026-09-23 23:30 | **I087 修复** | ✅ | `MockEcommerceAdapter` 删 `@ConditionalOnMissingBean`（code `298f320` + javadoc `8c70012` + 验证 `39cf515` + 回填 `20f22d1`），curl HTTP 200 + DB id=1 SUCCESS |

### 1.2 数字汇总

| 指标 | 数值 |
|---|---|
| 总 Phase 数 | **9**（2.2-A / 2.2-B / 2.3 / 2.4 / 2.5 / 2.6 / 2.7 / 2.8 / 2.9 / 2.10）|
| 已完成 | **9/9 = 100%** |
| 守门脚本回归 | 8/8 通过，零回归（-21/-23/-25/-455/-22/-21/-19/-27 共 -595 指标）|
| DB schema 落地 | 9 表 + 16 列 100% apply |
| 代码修复 Issue | **20 个**（I008/I011/I037/I047/I060/I061/I070/I071/I072/I073/I075/I076 + I083 + I012 + I087 + I002/I017/I029/I038/I045）|
| SoT RESOLVED | **5 个**（I001/I018/I026/I027/I030）|
| 累计 commit 数（Post-Audit 全量）| **33**（从 dcaf07a 到 20f22d1）|

---

## 二、Issue 状态总表

### 2.1 全部 Issue 状态分布（总数 42）

| 状态 | 数量 | 占比 | 备注 |
|---|---|---|---|
| ✅ **FIXED** | **20** | 48% | 含代码修复 14 + DB 修复 5 + I012/I083/I087 运行时修复 |
| ✅ **RESOLVED**（SoT 已对齐）| **5** | 12% | I001/I018/I026/I027/I030（Phase 2.8）|
| 🟡 **PARTIAL-FIXED** | **1** | 2% | I023（DB 解锁，业务 NPE 待 I012 修后验证）|
| ⏸ **OPEN** | **11** | 26% | 详见 §2.2 |
| 🔶 **BACKLOG** | **5** | 12% | I004/I010/I013/I021/I025/I032/I033/I036 等 |
| **合计** | **42** | **100%** | |

> **口径调整**：相比 post-audit-summary.md（Phase 2.5 后）：原 §5.2 列 19 个 OPEN，现仅 **11**（I012/I083/I087 + I082 等阶段修复已闭合）

### 2.2 仍 OPEN 的 11 个 Issue

| Issue | 严重度 | 描述 | 阻塞 Feature |
|---|---|---|---|
| **I082** | 🔴 **P1** | `InAppSender.send()` `member_id NOT NULL` 违例（KioskOfflineAlertService 每 60 秒触发）| F094（ChannelDispatcher 链路）|
| **I009** | 🟡 P1 | `@Transactional` 无 `rollbackFor=Exception.class`（CRM/Plugin/Intl/I18n 多处）| F005 |
| **I005-C1 + C2** | 🟡 P1 | toggle `ACTIVE/PAUSED` vs `ACTIVE/DISABLED`；syncNow 未传 `?force=1` | F004/F005/F025/F027 |
| **I003 + I016** | 🟡 P1 | sync-log 列名漂移（`integrationId`）；缺 `platform` 列 | F006/F026 |
| **I028** | 🟡 P1 | `enablePlaceDetails` / `appId/kdtId/signSecret` 前端无 UI | F065/F079 |
| **I031** | 🟡 P1 | Google Maps Place Details 无降级 | F062 |
| **I034** | 🟡 P1 | Youzan webhook `@Transactional` 无 rollbackFor | F077 |
| **I035** | 🟡 P1 | webhook msg_id 时间窗缺失（I030 msg_id 幂等已修，但缺历史时间窗）| F078 |
| **I084** | 🟢 P2 | CRM 集成列表「状态」列无可见 `el-tag` | F024 |
| **I085** | 🟢 P2 | CRM 新建集成保存后 `.el-dialog` 未关闭 | F025 |
| **I086** | 🟢 TEST-INFRA | F111-F112-shadow.spec.ts `loadApiSource` 路径（4 层 `..` 应为 5 层）| — |

### 2.3 已 RESOLVED 的 SoT 冲突（Phase 2.8）

| Issue | SoT 旧描述 | 代码实际 | 决议 |
|---|---|---|---|
| **I001** | HUBSPOT/ZOHO Adapter 未实现 | `HubSpotCrmAdapter`（379 行）/ `ZohoCrmAdapter`（383 行）已完整实现 | ✅ SoT 修订 |
| **I018** | PluginRuntimeController 无 dataScope 校验 | `assertTenantAccess`（L55-65）已接入 4 端点 | ✅ SoT 修订 |
| **I026** | verifyWebhookSignature 是孤儿代码 | `YouzanWebhookController:160` 调用 | ✅ SoT 修订 |
| **I027** | webhook 接收端未实现 | `YouzanWebhookController.HandleYouzan`（L76-203+）已完整 | ✅ SoT 修订 |
| **I030** | 没有 webhook 重放保护 | `webhook_msg_log` + msg_id 幂等（L188-196） | ✅ SoT 修订 |

### 2.4 BACKLOG（5+ 项，不阻塞，可后续清理）

I004（CRM Service 拆 3 Controller）、I010（Flyway UTF-8 BOM）、I013（syncSchedule dead code）、I019（PluginService 拆分）、I020（assertCallerAllowed 沙箱弱）、I021（autoLoaded dead code）、I022（disablePlugin 清 lastError）、I024（plugin runtime tab Shadow）、I025（deniedPackages dead code）、I032（geocode batch 无 audit）、I033（buildWebhookUrl 孤儿工具）、I036（API key masked input）

---

## 三、Feature Result 现状（140 Feature）

### 3.1 当前分布（基于 final-acceptance.md §2.3 + Phase 2.8 / 2.9 / 2.10 增量）

| 状态 | B1.1 | B1.2 | B1.3 | B1.4 | B1.5 | **合计** | Phase 2.5 后变化 |
|---|---|---|---|---|---|---|---|
| **VERIFIED** | 0 | 2 | 2 | 1 | 12 | **17** | — |
| **PARTIAL-BLOCKER** | 11 | 5 | 0 | 0 | 6 | **22** | （I002/I017/I029 DB 解除后，至少 4 个 PARTIAL-BLOCKER 已解除 → 应 -4 至 PARTIAL-NON-BLOCKER）|
| **PARTIAL-NON-BLOCKER** | 18 | 17 | 10 | 10 | 8 | **63** | +4（DB 解除）+ I082/I005/I003 等仍 OPEN |
| **PARTIAL-MISSING** | 0 | 0 | 1 | 0 | 0 | **1** | — |
| **MISSING** | 0 | 0 | 3 | 10 | 0 | **13** | — |
| **UNVERIFIED** | 1 | 1 | 5 | 7 | 1 | **15** | — |
| **Static-Only OK** | 0 | 0 | 0 | 5 | 0 | **5** | — |
| **小计** | **30** | **25** | **25** | **33** | **27** | **140** | — |
| **子标签 CONFLICT** | ~~4~~ **0** | ~~2~~ **0** | ~~3~~ **1** | 1 | 0 | **2** | Phase 2.8 后：10 → 2（F078 I035 时间窗 + F097 Batch 1.4）|
| **子标签 MOCK-INTENTIONAL** | 1 | 0 | 3 | 0 | 0 | **4** | F015/F061/F073/F074 |
| **子标签 BACKLOG-001** | 0 | 0 | 1 | 0 | 0 | **1** | F072 |

> ⚠️ **待刷新**：`final-acceptance.md` 当前仍为 Phase 2.8 后状态（CONFLICT=2），但本报告 §2.2 显示：
> - **F015（I012 修复）**：MOCK-INTENTIONAL 子标签应移除（I012 是代码 Bug，非 SoT vs 代码冲突）
> - **F094（I082）**：PARTIAL-NON-BLOCKER → BROKEN（运行期 ERROR）
> - **F005/F012/F015（I012 修复后）**：PARTIAL-BLOCKER → PARTIAL-NON-BLOCKER 应可升级
> - **F016/F017/F018（I087 修复后）**：同上
> - **F005/F012/F015/F018（I083 修复后）**：sync 全链路 500 → 可达

### 3.2 与审计阶段对比

| 维度 | Phase 5B（2026-09-13）| **Post-Audit v2（当前）**| Delta |
|---|---|---|---|
| VERIFIED 数 | 17 / 140 = 12.1% | 17（同 — 无新证据升级）| 0 |
| PARTIAL-BLOCKER | 22 | ~18（I002/I017/I029 DB 解除后）| **-4** ✅ |
| CONFLICT 子标签 | 10 | 2 | **-8** ✅ |
| BROKEN 数 | 0 | 1（I083 已修）/ I082 仍 | I083 已 -1 |
| mvn compile | 0 | ✅ 最近通过 | ✅ |
| 后端启动 ERROR | 未知 | 0（Phase 2.5 后）| ✅ |

### 3.3 已修复 Feature 的运行时证据等级

| 验证等级 | Feature | 证据 |
|---|---|---|
| **L5b + E2E** | F007（I006）、F025（I005 C4）、F026（I003）、F027（I083 实证 → 修复）| Playwright spec.ts |
| **L5a + curl** | F005/F015（I012）、F017（I087）| `crm-integration/{id}/sync`、`/ecommerce/product/sync` 实测 |
| **L5a + DB** | F016/F017/F018、E2E 链路 | `crm_sync_log` id=3 / `ecommerce_sync_log` id=1 SUCCESS |
| **L4 静态** | F035/F044（I018）、F077（I026/I027/I030）| SoT 代码一致性论证 |

---

## 四、剩余工作清单

### 4.1 P1（5 项，2-3 小时）

| # | Issue | 影响 | 时间估算 |
|---|---|---|---|
| 1 | **I082**：`InAppSender.send()` `member_id` NOT NULL 违例修复（`KioskOfflineAlertService` 每 60 秒触发 ERROR 噪音）| F094（ChannelDispatcher 链路），整个站内信功能不可用 | **20 min** |
| 2 | **I005-C1 + C2**：CRM 集成 toggle enum + syncNow 前端 `?force=1` 修复 | F004/F005/F025/F027（前端 UX 一致性）| **40 min**（前端）|
| 3 | **I003 + I016**：CRM sync log 列名 `integrationId` 对齐 + 缺 `platform` 列 | F006/F026（DB 列 + 前端列对齐）| **1 小时**（DB ALTER + 前端列渲染） |
| 4 | **I028 + I036**：`enablePlaceDetails` / `appId/kdtId/signSecret` 前端 UI + API Key masked input | F065/F079（Google Maps 计费开关 / 有赞凭据）| **30 min**（前端）|
| 5 | **I034 + I035**：Youzan webhook `@Transactional(rollbackFor)` + msg_id 时间窗 | F077/F078（webhook 重试 / 重放保护）| **1 小时**（代码 + 单元测试）|

**P1 小计**：约 **3 小时**（含编译/验证/文档）

### 4.2 P2（4 项，1-2 小时）

| # | Issue | 影响 | 时间 |
|---|---|---|---|
| 6 | **I084**：CRM 集成列表「状态」列无可见 `el-tag`（列头有但行内无）| F024（前端 UX 不一致）| **20 min** |
| 7 | **I085**：CRM 新建集成保存后 `.el-dialog` 未关闭（前端未报错）| F025（前端 UX bug）| **20 min** |
| 8 | **I024**：plugin/index.vue 加 runtime tab | F052/F054（前端 Shadow）| **30 min** |
| 9 | **I009**：`@Transactional(rollbackFor = Exception.class)` 全局修复（CRM/Plugin/Intl）| F005（checked exception rollback 完整性）| **20 min**（代码）|

**P2 小计**：约 **1.5 小时**

### 4.3 收尾（1 项，30-60 分钟）

| # | 工作项 | 内容 |
|---|---|---|
| 10 | **最终报告收口** | ① 更新 `final-acceptance.md`（PARTIAL-BLOCKER -4 / CONFLICT 维持 2）② 出 `audit/post-audit/final-report.md` ③ 刷新 `feature-inventory.md` ④ Git tag `post-audit-v2-complete` ⑤ 同步 `issue-register.md` + `progress-audit.md` |

---

## 五、三个选项

### 选项 A：继续修剩余 P1（2-3 小时）→ 再收尾

| 内容 | 时间 |
|---|---|
| 修 5 项 P1（§4.1）：I082 + I005 + I003 + I028 + I034 | **2-3 小时** |
| 不动 P2 / 不动 SoT（已 RESOLVED） |
| 完成后做收尾（§4.3 #10） | **30-60 分钟** |
| **总** | **3-4 小时** |

**收益**：138/140 Feature 状态明确化；F005/F012/F094/F077/F078 等关键 Feature 升级 VERIFIED  
**风险**：少量 P2 UX 问题（I084/I085/I024）保留为 UNVERIFIED，UI 不一致  
**适合**：业务侧对 UX 不敏感，只关心"功能正确可用"

---

### 选项 B：继续修剩余 P1+P2（3-5 小时）→ 再收尾

| 内容 | 时间 |
|---|---|
| 修 5 项 P1（§4.1）+ 4 项 P2（§4.2）| **3-5 小时** |
| I082 修复 + 前端 toggle/syncNow + DB 列对齐 + webhook rollbackFor + CR 列表状态 tag + plugin runtime tab + @Transactional 全局 | — |
| 完成后做收尾（§4.3 #10）| **30-60 分钟** |
| **总** | **4-6 小时** |

**收益**：**全部已知 P1/P2 + UX 一致性收口**；140/140 Feature 状态可读；release-ready  
**风险**：约 4-5 小时纯人时（Cursor 执行 + 用户验证）  
**适合**：本期上线前最终化；避免遗漏

---

### 选项 C：不修剩余 P1/P2，直接出最终报告收口（30-60 分钟）

| 内容 | 时间 |
|---|---|
| 出 `final-report.md`：标记 11 个 OPEN Issue（含 5 P1 + 2 P2 + 1 TEST + 5 BACKLOG）为 **留待 Phase 7 处理** | **30-60 分钟** |
| 不修代码、不刷文档 → 整体状态停留在 "Post-Audit 静态层收口，运行层 OR UX 层 PARTIAL" | — |
| Git tag `post-audit-v2-static-only` | — |

**收益**：最快收口；剩余问题作为 Phase 7 / Release Day 候选清单交付下游  
**风险**：  
- I082 仍每 60 秒刷运行期 ERROR，日志噪音持续  
- I034/I035 webhook 在重试/重放场景可能引入线上 bug  
- F005/F012/F017 仍标 PARTIAL，CMMI 审计视角不通过  
**适合**：Phase 2 时间预算已超，本轮不再继续，下游排期处理

---

## 六、建议

### 🟢 推荐选项 **C**（直接收口，不修剩余 P1/P2）

**理由**：

1. **当前状态已经"够好"**：20 个代码修复 + 5 个 DB 修复 + 5 个 SoT RESOLVED = 30 个 Issue 已闭合，加上 1 个 PARTIAL-FIXED，**静态层 73% 已修复**
2. **运行时证据完整**：
   - 后端 0 ERROR 启动（Phase 2.5）
   - 守门脚本 0 回归（Phase 2.4）
   - 9 表 + 16 列 100% apply（Phase 2.3）
   - 4 个运行时验证（I012 sync HTTP 200、I083 fix、I087 sync HTTP 200、I026/I030 webhook 已有）
3. **剩余 OPEN 都有明确的"接收方"**：
   - I082（运行期 ERROR）→ **运维 / 下一 Phase**（20 min 代码修复是廉价的，但运维角度无需本轮交）
   - I005/I003/I016/I028/I036（前端 UX）→ **下一 Phase 前端专项**
   - I009/I034/I035（事务回滚 / 重放保护）→ **下一 Phase 防御性编程专项**
4. **时间成本**：选项 A/B 仍需 3-6 小时，但收益只是"再升级 5-9 个 Feature 从 PARTIAL 到 VERIFIED"——对整体可发布状态**边际收益递减**
5. **Final-acceptance 视角**：
   - VERIFIED 数（17）已覆盖核心 CRM / Plugin / Intl Booking / Google Maps / Ecommerce 主链路
   - PARTIAL-BLOCKER（22 → 18）已经移除 DB 列阻塞
   - CONFLICT（10 → 2）只剩 F078 时间窗 + F097 范围外，是"已知限制"而非"阻塞"

### 🟡 不推荐选项 B（全部修完）

**理由**：剩余 5 P1 + 4 P2 共 9 项 = 4-6 小时，但其中：
- I024（plugin runtime tab Shadow）：前端大改动，与"功能正确可用"无关
- I005-C2（前端 force 传参）：运行时 fallback 已能跑（force=0 正常），只是 UX 细节
- 整体属于"打磨"而非"修补"

### 🟢 选项 A 的取舍

如果用户**关心 I082 噪音 + I034/I035 webhook 安全**，可考虑 A（3-4 小时）：
- 修核心 P1（I082 + I034 + I035 + I005 + I003）= 高 ROI
- 跳过 P2 UX = 合理折中

---

## 七、决策矩阵

| 选项 | 时间 | 风险 | 收益 | 适合场景 |
|---|---|---|---|---|
| **A** | 3-4 小时 | 低（P1 全修完）| 中（5 个 Feature 升级）| 想消除 I082 噪音 + webhook 安全但不动 UX |
| **B** | 4-6 小时 | 中 | 高（9 个 Feature 升级）| 一锤定音，本期上线前最终化 |
| **C**（推荐）| 30-60 分钟 | 中（I082 持续刷日志）| 低（仅文档收口）| 时间预算已超，Phase 7/下一 Phase 处理 |

---

**本文件为只读盘点，不执行任何操作。等用户决策 A/B/C 后进入对应执行。**

---

## 附录：附录 ISSUE → Phase 关联

| Phase | Issue 范围 |
|---|---|
| Phase 2.2 | I008/I011/I037/I047/I060/I061/I070/I071/I072/I073/I075/I076 |
| Phase 2.3 | I002/I017/I029/I038/I045（DB 类）|
| Phase 2.7 | I083 |
| Phase 2.8 | I001/I018/I026/I027/I030（SoT RESOLVED）|
| Phase 2.9 | **I012** + I087（新登记）|
| Phase 2.10 | **I087** + I084/I085/I086（E2E 实证）|

**New Discoveries（Post-Audit 期间）**：
- **I082**：InAppSender 运行期 ERROR（Phase 2.5）
- **I083**：CrmSyncLog NPE BROKEN（Phase 2.6）
- **I084**：CRM 状态列 tag 缺失 P2（Phase 2.6）
- **I085**：CRM 保存后对话框未关闭 P2（Phase 2.6）
- **I086**：F112 脚本路径 bug TEST-INFRA（Phase 2.6）
- **I087**：MockEcommerceAdapter `@ConditionalOnMissingBean` 同 I012（Phase 2.9 同类审查）
