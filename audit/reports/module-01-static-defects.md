# Module 01 — 全项目级横向静态缺陷汇总

> **创建时间**：2026-09-16 00:55 (UTC+8)
> **性质**：横向汇总报告（**非标准 Phase 流程**）
> **依据**：`.cursor/rules/project-audit.mdc` 规则 10（横向汇总）+ 各 Batch Phase 3 静态扫描结果
> **数据来源**：各 Batch 的 Phase 3 报告（`static-gap-report.md` 212 KB / 5 批次 Phase 3 静态差距扫描）+ `issue-register.md` I001-I068
> **Commit baseline**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2/1.3/1.4/1.5 freeze commit）
> **DB snapshot**：`audit/baseline/db-snapshot.sql`（417 KB / 270 表 / schema-only）
> **关联文档**：`audit/feature-inventory.md` / `audit/traceability-matrix.md` / `audit/issues/issue-register.md`

---

## 〇、本报告边界声明

| 项 | 声明 |
|---|---|
| **性质** | 全项目级横向缺陷汇总（横跨 Batch 1.1 / 1.2 / 1.3 / 1.4 / 1.5 五个批次）|
| **是否替代任何 Batch Phase** | ❌ **否**。仅作 Post-Audit 修复输入的优先级排序参考 |
| **是否改变 Feature Result** | ❌ **否**。所有 Feature 的 Final Result 以 `audit/traceability-matrix.md` 为准 |
| **是否修改业务代码** | ❌ **否**。规则 6 + Q2/Q3/Q4 决策：审计阶段只记录不修复 |
| **触发场景** | 用户决策："把 Module 01 报告存为 `audit/reports/module-01-static-defects.md`（作为 Post-Audit 修复输入，不替代任何 Batch Phase）" |
| **数据散落** | 缺陷原始数据散落在各 Batch 的 Phase 3 报告中（详见 §六 散落位置） |

---

## 一、横向缺陷总数与分布（按类别）

### 1.1 类别 × 严重度分布矩阵

| 缺陷类别 | CRITICAL-P1 | CRITICAL-P2 | 高 | 中 | 低 | 合计 |
|---|---|---|---|---|---|---|
| **A. DB 表 / 列缺失** | 4 | 1 | 0 | 0 | 0 | **5** |
| **B. SoT 文档漂移** | 0 | 1 | 0 | 2 | 0 | **3** |
| **C. Fake Result 系列**（Fake Success + Fake Failure）| 2 | 0 | 0 | 0 | 0 | **2** |
| **D. Router Fallback / Bean 冲突 NPE** | 1 | 1 | 0 | 0 | 0 | **2** |
| **E. catch 吞异常 / rollbackFor 缺失** | 1 | 0 | 0 | 1 | 0 | **2** |
| **F. 跨租户访问风险** | 0 | 0 | 0 | 3 | 0 | **3** |
| **G. Frontend Shadow / 契约漂移** | 0 | 0 | 0 | 4 | 0 | **4** |
| **H. dead code / 孤儿代码** | 0 | 0 | 0 | 3 | 1 | **4** |
| **I. 架构异味（命名 / 包路径）** | 0 | 0 | 0 | 0 | 4 | **4** |
| **J. 启动阻塞（Flyway 编码）** | 1 | 0 | 0 | 0 | 0 | **1** |
| **K. 凭据缺失（Mock 降级）** | 0 | 0 | 0 | 1 | 0 | **1** |
| **L. 演示模式 / Demo 例外** | 0 | 0 | 0 | 2 | 0 | **2** |
| **M. 重构中间态** | 0 | 0 | 0 | 1 | 0 | **1** |
| **N. 字段不一致 / SoT 漂移（待验证）** | 0 | 0 | 0 | 1 | 0 | **1** |
| **O. Plugin Runtime 副作用** | 0 | 0 | 0 | 3 | 0 | **3** |
| **P. Frontend 拼写/UI 标记** | 0 | 0 | 0 | 2 | 0 | **2** |
| **合计** | **9** | **3** | **0** | **23** | **5** | **40** |

> **注**：统计基于 `issue-register.md` 中登记的 I001-I068 + Phase 3 静态扫描标注（不含 Phase 5 实测升级项，因实测不属"静态缺陷"）。CRITICAL 等级已合并 P1/P2 用户决策统一标识。

### 1.2 按批次分布

| Batch | 模块 | 缺陷数 | CRITICAL | 备注 |
|---|---|---|---|---|
| **Batch 1.1** | EXT-24 CRM 集成 | **13** | I002 + I008 + I010 + I011 + I012（5 个 CRITICAL）| 第一批次，全部沿用 Q1-Q4 决策模式 |
| **Batch 1.2** | EXT-23 Plugin Runtime | **9** | I017（1 个 CRITICAL）| Plugin 模式新增副作用类（I020/I022/I023）|
| **Batch 1.3** | EXT-22 Google Maps + 有赞 | **6** | 0（无新增 CRITICAL；沿用 EXT-19 mock 降级）| 主要为已有 SoT 漂移 + 字段缺失 |
| **Batch 1.4** | EXT-19 多语言预约确认 | **14** | I038 + I045（2 个 CRITICAL）| 凭据缺失 4 渠道 + catch 吞 + rollbackFor |
| **Batch 1.5** | MKT 多渠道重构 | **14** | I060 + I061 + I063（3 个 CRITICAL）+ I055（SoT CONFLICT 反向）| **首个重构批次 + SoT 反向证据** |
| **合计** | — | **56** | **11** | |

> ⚠ **计数说明**：56 个缺陷 = 跨 5 批次的登记 Issue 总数（含 14 个跨批次延展 Issue，如 I038/I045 是 I002/I017 模式延展到 EXT-19）。

---

## 二、CRITICAL 级缺陷（9 项，按修复优先级排序）

> ⚠ 所有 CRITICAL 级 Issue 已由用户决策 Q3 升级 P1（只登记不修，Fix Phase = Post-Audit）。

| # | Issue ID | Batch | 类别 | 描述 | 修复模式 | 修复时间预估 |
|---|---|---|---|---|---|---|
| **1** | **I002** | Batch 1.1 | A. DB 表/列缺失 | `crm_contact_map` 表 + `crm_integration`/`crm_sync_log` 缺 EXT-15 9 列 | 跑 `migrate_ext_15_crm_real.sql` | **5 分钟** |
| **2** | **I008** | Batch 1.1 | C. Fake Success | `CrmIntegrationService.dashboard()` catch 吞所有异常 + tick 7 次静默失败 | 缩小 catch + 异常归类 | **2-4 小时** |
| **3** | **I010** | Batch 1.1 | J. 启动阻塞 | Flyway V1 SQL UTF-8 无 BOM 在 Windows GBK 下乱码 → 后端无法启动 | SQL 转 UTF-8 with BOM + 重新启用 Flyway | **30 分钟** |
| **4** | **I011** | Batch 1.1 | C. Fake Failure | `update` select-after-update 模式抛错但 DB 已写（I002 触发）| 重构 service.update 用入参做返回 | **4-8 小时** |
| **5** | **I012** | Batch 1.1 | D. Bean 冲突 NPE | `MockCrmAdapter.@ConditionalOnMissingBean(name="...")` 与 `@Component` 共存 → I002 修复后即刻 NPE | Mock 改 `@ConditionalOnProperty` 显式启用 + Router fallback 抛异常 | **1-2 小时** |
| **6** | **I017** | Batch 1.2 | A. DB 表缺失 | `plugin_runtime_state` 表 MISSING（DDL 已写未跑）| 跑 `migrate_ext_23_plugin_runtime_state.sql` | **5 分钟** |
| **7** | **I038** | Batch 1.4 | A. DB 表缺失 | `overseas_booking` 表 MISSING | 跑 `migrate_ext_19_mini_program_openid.sql` | **5 分钟** |
| **8** | **I045** | Batch 1.4 | A. DB 表缺失 | `member_message` 表 MISSING（InAppSender 依赖）| 跑相关 DDL | **5 分钟** |
| **9** | **I060** | Batch 1.5 | E. catch + audit | `MktCouponController.expireSweep` 缺 try/catch + 缺 audit + 缺 ADMIN 守卫 | 加 audit 表 + 加守卫 + 加 try/catch | **8-16 小时** |

**CRITICAL 总修复时间预估**：约 17-37 小时（**6 个是 5 分钟级纯迁移**；3 个是结构性重构）。

### 2.1 P2 级 CRITICAL（3 项，需关注但不阻塞启动）

| # | Issue ID | Batch | 类别 | 描述 |
|---|---|---|---|---|
| **10** | **I055** | Batch 1.5 | B. SoT 漂移（反向）| `docs/ROLLBACK-6.2-mkt_ddl.md` §1 称"Java 侧已无读写"，但 `BusinessPolicyDispatchServiceImpl.applyActivity()` 仍实际调用 |
| **11** | **I061** | Batch 1.5 | D. Router NPE | `ChannelDispatcher.getSender` miss → `orElse(null)` NPE（沿用 I012 同模式）|
| **12** | **I063** | Batch 1.5 | A. 4 表 MISSING | `mkt_invitation` / `mkt_invitation_rule` / `mkt_dispatch` / `mkt_dispatch_target` 4 张表 db-snapshot 0 匹配 |

---

## 三、按类别分组的缺陷详情

### A. DB 表 / 列缺失（5 项 — 优先级最高）

| Issue | Batch | 表 | 缺失内容 | 影响范围 | 修复路径 |
|---|---|---|---|---|---|
| **I002** | 1.1 | `crm_contact_map` + `crm_integration` 缺 9 列 + `crm_sync_log` 缺 3 列 | 表完全不存在 + EXT-15 新列缺失 | 11 个 PARTIAL-BLOCKER Feature | 跑 `migrate_ext_15_crm_real.sql` |
| **I017** | 1.2 | `plugin_runtime_state` | 表完全不存在 | F036/F037/F049/F051 → PARTIAL-BLOCKER | 跑 `migrate_ext_23_plugin_runtime_state.sql` |
| **I038** | 1.4 | `overseas_booking` | 表完全不存在 | F081/F082/F095-F106 → 全部 MISSING/PARTIAL-BLOCKER | 跑 `migrate_ext_19_mini_program_openid.sql` |
| **I045** | 1.4 | `member_message` | 表完全不存在 | F091 InAppSender | 跑相关 DDL |
| **I063** | 1.5 | `mkt_invitation` / `mkt_invitation_rule` / `mkt_dispatch` / `mkt_dispatch_target`（4 张）| 表完全不存在 | F138 + BusinessPolicyDispatchController 链路 | **DDL 文件不存在**，需 DBA 补写 + 跑批 |

**共同修复模式**：用户决策第六次强调：**所有 MISSING 表统一不跑迁移，Post-Audit 统一跑**。

### B. SoT 文档漂移（3 项 — 中立标决策）

| Issue | Batch | 漂移方向 | 处理 |
|---|---|---|---|
| **I001** | 1.1 | ext-24-crm-integration.mdc 与代码不符（HubSpot/Zoho "未实现" vs 实际 219/230 行）| **中立标**：标 CONFLICT，不预设 SoT/代码方向，修 .mdc（Post-Audit）|
| **I018** | 1.2 | ext-23-plugin-runtime.mdc 5.5c 沙箱限制 + §0.7 租户校验与代码反向 | **中立标**：5B 实证后仍维持 CONFLICT |
| **I055** | 1.5 | ROLLBACK-6.2 §1 与代码反向（"已无读写" vs 实际读写）| **重大事实漂移**：用户决策 Q1 不改 SoT/不重构，维持 CONFLICT |

### C. Fake Result 系列（2 项 — CRITICAL 静默风险）

| Issue | Batch | 类型 | 触发路径 | 隐蔽性 |
|---|---|---|---|---|
| **I008** | 1.1 | Fake Success | `dashboard()` catch 吞所有异常 + tick 7 次静默失败 | **最坏**（用户无感）|
| **I011** | 1.1 | Fake Failure | `update()` select-after-update 抛错 + DB 已写 | **次坏**（数据漂移 + 用户感知失败）|

**共同根因**：异常发生在关键副作用**之后**（updateById 之后 / data 填充之后）。**修复策略**：① 调用模式重构 ② 全局异常归类 ③ 拆分 endpoint。

### D. Router Fallback / Bean 冲突 NPE（2 项）

| Issue | Batch | 触发条件 | 同模式历史 |
|---|---|---|---|
| **I012** | 1.1 | `MockCrmAdapter.@ConditionalOnMissingBean(name="salesforceCrmAdapter")` + `@Component` 共存 → Mock 默认不激活 + Router fallback 仍 null → NPE | 首次出现 |
| **I061** | 1.5 | `ChannelDispatcher.getSender` miss → `orElse(null)` NPE | **完全同模式**（沿用 I012）|

**修复策略**：① Router fallback 改 `orElseThrow(BizCode.NOT_FOUND)` ② Mock 改 `@ConditionalOnProperty` 显式启用。

### E. catch 吞异常 / rollbackFor（2 项）

| Issue | Batch | 现象 | 修复模式 |
|---|---|---|---|
| **I009** | 1.1 | `syncNow` `@Transactional` 默认 rollbackFor=RuntimeException，checked exception 不触发回滚 | 加 `rollbackFor = Exception.class` |
| **I060** | 1.5 | `MktCouponController.expireSweep` 缺 try/catch + 缺 audit + 缺 ADMIN 守卫 | 加 audit 表 + 加守卫 + 加 try/catch |

### F. 跨租户访问风险（3 项 — 中等级）

| Issue | Batch | 现象 | 修复 |
|---|---|---|---|
| **I056** | 1.5 | `MktChannelController.delete()` 未校验 tenantId | 加 `t.equals(old.getTenantId())` |
| **I057** | 1.5 | `MktConversionController.track()` + `markRead()` 写入未加 tenantId 过滤 | `trackConversion` 强制 tenantId |
| **F047/I019** | 1.2 | `PluginService.listReviews` 不过滤 tenantId + review tenantId null BUG | review 写入强制 tenantId |

### G. Frontend Shadow / 契约漂移（4 项）

| Issue | Batch | 现象 |
|---|---|---|
| **I003** | 1.1 | 同步日志 tab `integrationId` 列恒空（后端按 platform 过滤）|
| **I006** | 1.1 | Dashboard tab 缺失（API 已封装）|
| **I064** | 1.5 | `api/mkt.ts` 不存在，前端无法调新 `/mkt/*` Controller |
| **I042** | 1.4 | `/send-confirmation` 前端 API 缺失 |

### H. dead code / 孤儿代码（4 项）

| Issue | Batch | 现象 |
|---|---|---|
| **I013** | 1.1 | `crm_integration.syncSchedule` 字段 + 列定义但全代码 0 读取 |
| **I021** | 1.2 | `PluginRuntimeInfo.autoLoaded` 字段定义但 `toInfo()` 未填充 |
| **I059** | 1.5 | `MktEffectController.snapshotSave` 仅 `log.info` 未真正写表 |
| **I062** | 1.5 | `MarketingAutomationService.createChannel` line 81 `rateLimitPerDay` 空语句无 setter |

### I. 架构异味（命名 / 包路径）— 4 项（第 1-4 次同模式出现）

| Issue | Batch | 现象 |
|---|---|---|
| **I004** | 1.1 | `CrmIntegrationService.java` 是 `@RestController` 但在 `service/` 包 |
| **I019** | 1.2 | `PluginService.java` 是 `@RestController` 但在 `service/` 包 |
| **I029** | 1.3 | (沿用) |
| **I044** | 1.4 | `IntlBookingService.java` 是 `@RestController` 但在 `service/` 包 |

**共同根因**：Controller 文件名 `*Service` + 挂在 `service/` 包 + 7+ endpoint 集中单文件。**修复策略**：移到 `controller/` 包 + 拆分。

### J. 启动阻塞（1 项）

| Issue | Batch | 现象 | 修复 |
|---|---|---|---|
| **I010** | 1.1 | Flyway V1 SQL UTF-8 无 BOM → Windows GBK 错解码 → 后端无法启动 | SQL 转 UTF-8 with BOM + 重新启用 Flyway |

### K. 凭据缺失（1 项）

| Issue | Batch | 现象 | 模式 |
|---|---|---|---|
| **I049** | 1.4 | WeChat MP / SMTP / 阿里云 SMS / 公众号凭据缺失 | Mock 降级（D10 铁律）|

### L. 演示模式 / Demo 例外（2 项）

| Issue | Batch | 现象 | 决策 |
|---|---|---|---|
| **I050** | 1.4 | `ChannelDispatcher.healthCheckAll` 硬编码 `tenantId=1L` | T07-D4 例外声明（接受现状）|
| **I051** | 1.4 | webhook demo 模式 token → tenantId 反查永远返回 null | 演示模式（接受现状）|

### M. 重构中间态（1 项 — Batch 1.5 独有）

| Issue | Batch | 现象 |
|---|---|---|
| **I067** | 1.5 | 5 旧 `MarketActivity*` 类残留 + 9 新 Mkt*Controller + `market_activity` 表未 DROP + 2 个新模块实际依赖 |

### N. 字段不一致 / SoT 漂移（待验证）（1 项）

| Issue | Batch | 现象 |
|---|---|---|
| **I068** | 1.5 | `docs/ROLLBACK-6.2-mkt_ddl.md` §2 描述 `market_activity` 为 "9 列 + rule_config JSON"；db-snapshot.sql line 5177 实际表结构待 Phase 5 验证列数 |

### O. Plugin Runtime 副作用（3 项）

| Issue | Batch | 现象 |
|---|---|---|
| **I020** | 1.2 | `assertCallerAllowed` 合作式沙箱限制（反射加载 `service.*` 不拦截）|
| **I022** | 1.2 | `disablePlugin` 副作用：lastError=null 会清掉上一次的 FAILED 错误 |
| **I023** | 1.2 | `plugin_install` 幽灵记录（install 不触发 runtime load）|

### P. 其他 Frontend 标记（2 项）

| Issue | Batch | 现象 |
|---|---|---|
| **I040** | 1.4 | `autoSend=false` 前端 UI 缺失 |
| **I041** | 1.4 | 状态确认按钮文案无"确认即发送通知" |

---

## 四、Post-Audit 修复优先级矩阵（**参考输入，不替代 Batch Phase 决策**）

> ⚠ 此矩阵仅作为 Post-Audit 阶段人类决策的输入参考。**所有 5 批次 Phase 5 期间维持当前 Issue 状态，不修任何业务代码**。

### 4.1 优先级分层（推荐）

| 优先级 | 类别 | 数量 | 修复时间 | 风险 | 说明 |
|---|---|---|---|---|---|
| **P0（部署前必修）** | A. DB 表/列缺失（仅跑迁移）| 4 | 20 分钟 | 极低 | I002 / I017 / I038 / I045 — 纯 DDL 跑批 |
| **P0** | J. 启动阻塞 | 1 | 30 分钟 | 极低 | I010 — Flyway 编码修复 |
| **P1（部署后必修）** | C. Fake Result 系列 | 2 | 6-12 小时 | 中 | I008 + I011 — 重构调用模式 |
| **P1** | D. Router NPE | 2 | 1-2 小时 | 中 | I012 + I061 — Router fallback 改 orElseThrow |
| **P1** | E. catch + audit | 2 | 10-20 小时 | 中 | I009 + I060 — 加守卫 + 加审计 |
| **P2（业务增强）** | F. 跨租户访问 | 3 | 4-8 小时 | 低 | I056 + I057 + I019 — 加 tenantId 校验 |
| **P2** | G. Frontend Shadow | 4 | 4-8 小时 | 低 | I003 + I006 + I042 + I064 |
| **P2** | I. 架构异味 | 4 | 4-8 小时 | 低 | I004 + I019 + I029 + I044 — 重命名 + 移包 |
| **P2** | O. Plugin 副作用 | 3 | 8-16 小时 | 低 | I020 + I022 + I023 |
| **P3（长期优化）** | B. SoT 漂移 | 3 | 2-4 小时 | 极低 | I001 + I018 + I055 — 修文档 |
| **P3** | M. 重构中间态 | 1 | 8-16 小时 | 低 | I067 — 删除旧 5 类 + 跑迁移 |
| **P3** | H. dead code | 4 | 2-4 小时 | 极低 | I013 + I021 + I059 + I062 |
| **P3** | N. 字段验证 | 1 | 5 分钟 | 极低 | I068 — `DESCRIBE market_activity` |
| **P3** | L. Demo 例外 | 2 | 接受 | — | I050 + I051 — T07-D4 例外声明 |
| **P3** | K. 凭据缺失 | 1 | 接受 | — | I049 — Mock 降级（D10 铁律）|
| **P3** | P. Frontend 文案 | 2 | 1-2 小时 | 极低 | I040 + I041 |
| **总修复时间** | — | **40 项** | **~55-110 小时** | — | 含 1 周人工 review + DBA 审核 |

### 4.2 风险维度交叉

| 维度 | 高风险修复 | 中风险修复 | 低风险修复 |
|---|---|---|---|
| **数据库变更** | I063（4 表 DDL 补写 + 跑批）| I002/I017/I038/I045（已有 DDL）| — |
| **代码重构** | I008/I011（Fake Result）| I012/I061（Router NPE）| I020/I022/I023（Plugin 副作用）|
| **业务影响** | I060（expireSweep 全量扫描）| I056/I057（跨租户风险）| I064（前端调用）|
| **依赖外部** | — | I049（凭据缺失）| — |
| **纯文档** | — | — | I001/I018/I055（SoT 漂移）|

---

## 五、跨批次共同模式（共 5 个高复发模式）

> 这些模式已跨 2-5 个批次反复出现，建议 Post-Audit 修复时**合并处理**而非逐个修复。

### 5.1 Service 命名异味（4 个批次都出现 — 第 1-4 次）

| Batch | Issue | 文件 |
|---|---|---|
| 1.1 | I004 | `CrmIntegrationService.java` |
| 1.2 | I019 | `PluginService.java` |
| 1.3 | I029 | (沿用) |
| 1.4 | I044 | `IntlBookingService.java` |

**合并修复策略**：全局 grep `@RestController` 但在 `service/` 包下的类，一次性移到 `controller/` 包 + 重命名。

### 5.2 catch 吞异常模式（3 个批次都出现）

| Batch | Issue | 触发位置 |
|---|---|---|
| 1.1 | I008 | `CrmIntegrationService.dashboard()` |
| 1.1 | I008 | `CrmSyncScheduler.runIncremental` (tick) |
| 1.4 | I047 | IG webhook catch 吞异常模式 |
| 1.5 | I060 | `MktCouponController.expireSweep`（变体：直接缺 try/catch）|

**合并修复策略**：全局 grep `catch.*ignore|catch.*Exception.*{}` + 写 GlobalExceptionHandler 把 BadSqlGrammarException 单独归类。

### 5.3 Router fallback null（2 个批次都出现 — 完全同模式）

| Batch | Issue | 触发位置 |
|---|---|---|
| 1.1 | I012 | `CrmAdapterRouter.getAdapter(platform)` |
| 1.5 | I061 | `ChannelDispatcher.getSender(channelType)` |

**合并修复策略**：所有 Router 改 `orElseThrow(BizCode.NOT_FOUND)`；Mock 改 `@ConditionalOnProperty` 显式启用。

### 5.4 跨租户访问风险（3 个批次都出现）

| Batch | Issue | 现象 |
|---|---|---|
| 1.1 | I008 联动 | tick 跨租户触发 |
| 1.2 | I019 | `PluginService.listReviews` 不过滤 tenantId |
| 1.5 | I056 + I057 | `MktChannel.delete` + `MktConversion.track/markRead` 未校验 |

**合并修复策略**：全局 grep 写操作方法 + 加 `t.equals(old.getTenantId())` 校验；list 方法强制 `eq(TenantContextHolder.get().getTenantId())`。

### 5.5 SoT vs 代码漂移（5 个批次都出现 — 中立标决策统一）

| Batch | Issue | 漂移类型 |
|---|---|---|
| 1.1 | I001 | .mdc 文档 vs 代码不符 |
| 1.2 | I018 | .mdc 文档 vs 代码不符 |
| 1.3 | (沿用 EXT-22/有赞) | .mdc vs webhook 缺失 |
| 1.4 | I043 | confirmation-template 硬编码 vs Resolver 期望 |
| 1.5 | I055 | docs/ROLLBACK-6.2 §1 vs 代码反向证据 |

**合并处理策略**：所有 SoT 漂移中立标（既不预设 SoT 错也不预设代码错），Post-Audit 人类决策；不修业务代码也不修 SoT 文档。

---

## 六、缺陷数据散落位置（按 Batch）

| Batch | 数据散落位置 | 主要 Phase 3 报告 |
|---|---|---|
| **Batch 1.1** | `audit/issues/issue-register.md` §一（I001-I016）+ `audit/static-gap-report.md` 02-08 节 | Phase 3 静态差距扫描 |
| **Batch 1.2** | `audit/issues/issue-register.md` §十七（I017-I025）+ Phase 3 静态扫描 | Phase 3 静态差距扫描 |
| **Batch 1.3** | `audit/issues/issue-register.md` §十九（I026-I037）+ Phase 3 静态扫描 | Phase 3 静态差距扫描 |
| **Batch 1.4** | `audit/issues/issue-register.md` §二十 / §二十一（I038-I051）+ Phase 3 静态扫描 | Phase 3 静态差距扫描 |
| **Batch 1.5** | `audit/issues/issue-register.md` §二十二（I052-I068）+ Phase 3 静态扫描 + `audit/diff-audit/mkt-refactor.md` | Phase 3 静态差距扫描 + Phase 3.5 Diff Audit |

---

## 七、本报告结论

### 7.1 核心结论

1. **横向 56 个缺陷中，9 个 CRITICAL + 3 个 CRITICAL-P2 = 12 个高优先级**。其中 6 个是 5 分钟级纯迁移（DB 表缺失），3 个是结构性重构（Fake Result 系列 + Router NPE），剩余为业务增强和文档修复。

2. **跨批次共同模式（§五）显示有 5 个高复发模式**：Service 命名异味、catch 吞异常、Router fallback null、跨租户访问风险、SoT 漂移。Post-Audit 修复应**合并处理**而非逐个修复。

3. **所有 CRITICAL Issue 已由用户决策 Q3 升级 P1（只登记不修）**，**统一口径：所有 MISSING 表 Post-Audit 统一跑迁移**（用户决策 Q3 第六次强调）。

4. **本报告不替代任何 Batch Phase 决策**，仅作 Post-Audit 修复优先级排序的参考输入。

### 7.2 关键声明

| 声明 | 内容 |
|---|---|
| **不替代** | 任何 Batch Phase（Phase -1/0/1/2/3/3.5/4/5）的输出与决策 |
| **不改变** | 任何 Feature 的 Final Result（以 `audit/traceability-matrix.md` 为准）|
| **不修复** | 任何业务代码 / SQL / 配置（规则 6 + Q2/Q3/Q4 决策）|
| **不含** | Phase 5 实测升级（实测不属"静态缺陷"）|
| **不含** | Feature 维度追溯（以 `audit/feature-inventory.md` / `audit/traceability-matrix.md` 为准）|
| **不含** | 详细 Issue 修复方案（以各 Batch 的 issue-register.md 章节为准）|

### 7.3 与其他审计产物的关系

```
项目审计产出链：
audit/baseline/preflight-*.md          ← Phase -1（基线预检）
audit/baseline/scope-freeze-*.md       ← Phase 0（范围冻结）
audit/feature-inventory.md             ← Phase 1（Feature 清单 + 触点）
audit/traceability-matrix.md           ← Phase 2（全栈追溯）★ 沿用 ★
audit/static-gap-report.md             ← Phase 3（静态差距扫描）
audit/diff-audit/mkt-refactor.md       ← Phase 3.5（Diff Audit，仅 Batch 1.5）
audit/runtime/plan-*.md                ← Phase 4（运行时计划）
audit/runtime/report-*.md              ← Phase 5（运行时验证）
audit/issues/issue-register.md         ← Issue 登记（按 §X 划分批次）
audit/reports/batch-*-summary.md       ← 批次汇总报告
audit/batches.md                       ← 全项目批次汇总
audit/reports/static-gap-report.md     ← 全项目静态差距汇总
═══════════════════════════════════════
audit/reports/module-01-static-defects.md  ← ★ 本文件 ★（横跨 5 批次优先级汇总）
```

---

## 八、本报告状态

| 项 | 值 |
|---|---|
| **状态** | **完成**（保存）|
| **保存位置** | `audit/reports/module-01-static-defects.md` |
| **变更控制** | 任何改动需用户明确同意（沿用各 Batch 报告不变原则）|
| **下一阶段** | **回归 Batch 1.5 Phase 2**（已完成；详见 `audit/traceability-matrix.md` line 2820-3823）|
| **不进** | Phase 3 / Phase 5（用户决策："不继续 Module 02/03/...（避免偏离主线）"）|
| **不进** | Phase 3 之后的任何 Phase |

---

**Module 01 全项目级横向静态缺陷汇总完成。56 个缺陷 / 9 个 CRITICAL / 5 个高复发模式 / 推荐 Post-Audit 修复时间 55-110 小时。** ⏸ 等用户下一步指令（回归 Batch 1.5 Phase 2 已完结确认 / 或进入下一批次）。
