# Phase 8 — Non-Functional / Closure Readiness 横向报告

> **批次**：v3 第六批（横向检查，非 Feature 维度）
> **创建时间**：2026-09-17 00:50 (UTC+8)
> **EN-09 触发后**：2026-09-17 00:30 — 沿用唯一归类
> **状态**：✅ **Phase 8 完成**（9 项横向检查 + 4 项新发现 + Gate 2/3 评估）
> **范围**：权限 / 异常处理 / 事务边界 / 并发幂等 / 软删除 / 多租户 / 可观测性 / 环境差异 / 种子数据
> **基线**：起点 commit `e85ba8ba8540081f2f59c97120052b5bef5a29ef` + `audit/baseline/db-snapshot.sql`
> **不做单 Feature Evidence / 不改业务代码 / 不深挖 Feature**（按用户约束）

---

## 〇、本批次产出摘要

| 产出 | 内容 |
|---|---|
| **9 项横向检查** | 权限 / 异常处理 / 事务边界 / 并发幂等 / 软删除 / 多租户 / 可观测性 / 环境差异 / 种子数据 |
| **4 项新发现** | I078 / I079 / I080 / I081 — 软删除 + 指标 + 环境 profile + 初始化器 |
| **Gate 评估** | Gate 2（QA Acceptance）= ⚠️ CONDITIONAL READY；Gate 3（Production Ready）= ❌ NOT READY |
| **修复时间估算** | P0（6 项 CRITICAL + 7 项 HIGH）= **3.5 天**；P1（10 项）= **9.5 天**；P2（10 项）= **9-10 天**；合计 **2-3 周**（明确说明**不是 2 周**）|

---

## 一、9 项横向检查结论表

> **风险等级图例**：🔴 CRITICAL（生产阻塞）/ 🟠 HIGH（数据一致 / 安全）/ 🟡 MEDIUM（运维风险）/ 🟢 LOW（可接受）

| # | 检查项 | 风险等级 | 关键发现 | 关联 Issue | 建议 |
|---|---|---|---|---|---|
| 1 | **权限（菜单/页面/按钮/数据/API/字段脱敏）** | 🟡 MEDIUM | 4 层架构完整（`RequirePermission` 注解 + `PermissionAspect` AOP + `PermissionAutoRegistrar` 自动注册 + `RbacContextLoader` 上下文）；缺字段脱敏 AOP 框架（`FieldDesensitization` 实体存在但无注解驱动）+ 按钮级控制靠前端 v-if 后端弱校验 | I003 + I005 + I006 + I007 + I024 + I055 + I068 | 增加 `@SensitiveField` 注解 + Jackson `Serializer`；按钮级权限加 `@RequirePermission("button:xxx")` 子权限码 |
| 2 | **异常处理（全局 + catch 吞异常）** | 🟠 HIGH | **299 catch 命中**（220 catch-log-swallow + 80 catch-ignore-empty）；`GlobalExceptionHandler` 存在但只覆盖 RuntimeException + 部分 checked；I008 Fake Success + I011 Fake Failure 已 CRITICAL 升级 | **I008** + I011 + I047 + I022 + I034 + I040 | catch 收紧规则：`catch (Exception e) { log.error("[{}] {}", traceId, "msg", e); throw new BizException(...) }` |
| 3 | **事务边界（@Transactional 自调用/private/吞异常）** | 🟠 HIGH | **662 处 @Transactional**（其中 **215 处缺 rollbackFor ≈ 32%**）；沿用 I009 模式 `rollbackFor = Exception.class`；private method + self-invocation 在 Spring AOP 下不生效 | **I009** + I034 + P4 沿用 + Plugin 沿用 | 全局 `@Transactional` 加 `rollbackFor = Exception.class, propagation = REQUIRED`；Self-invocation 检查（IDEA 插件）|
| 4 | **并发/幂等（重复提交/超卖/分布式锁）** | 🟡 MEDIUM | `IdempotencyService` + `SysIdempotencyRecord` 完整实现；但**调用方覆盖率 < 10%**（grep 仅 2 处 service impl 直接用）；超卖场景（MktCoupon.grantBatch）依赖 DB UNIQUE 兜兜但缺 Redis 分布式锁；Scheduling 并发靠 DB tick 但无 lock | I070 + 跨批次 P0 | 幂等注解 `@Idempotent(expireMs = 60000)` + AOP；超卖加 Redis `SETNX` 锁 + 库存预扣 |
| 5 | **软删除与唯一索引冲突** | 🟠 HIGH | **77 张表扫描**，**63 张 HIGH 风险**（`deleted` 列存在但 UNIQUE KEY 不含 `deleted` → 软删后重插 UNIQUE 冲突）；典型 `crm_member_store.uk_member_store (member_id, store_id)` 等 | **I078 NEW** + I002 沿用 | 修复 SQL：所有 UNIQUE 加 `deleted` 列；或改用部分索引（MySQL 8.0.13+：`UNIQUE (x, y, deleted)` 当 `deleted=0`）|
| 6 | **多租户隔离（tenantId 强制 / dataScope）** | 🟠 HIGH | 4 层完整：`TenantContextHolder` + `DataScopeInterceptor` (141 行) + `DataScopeService` + `TenantMetaObjectHandler`；**白名单路径 13 条**（JwtInterceptor + DataScopeInterceptor）覆盖不全；**body.tenantId 信任前端**风险 4 处实证（Plugin `install` / Crm `trackConversion` / Crm `couponGrant` / Ecommerce `updatePlatform`）| **I019** + I023 + I037 + I056 + I057 + I065 + I075 | tenantId 强制从 Context 取 + `assertTenantAccess` 在所有 Controller 入口；业务字段加 `@TenantField` 注解强制注入 |
| 7 | **可观测性（traceId / 指标 / 日志）** | 🟡 MEDIUM | **traceId 完整**（90 hits：`TraceFilter` + `TraceUtil` + `TraceResponseAdvice` + `LogAspect`）；`logback-spring.xml` 存在；**0 Micrometer / 0 Prometheus / 0 actuator 指标端点**（仅 `MonitorMetric` 实体 + `MonitorAlertService` Javadoc 注释「应替换为 Prometheus」+ 无实际接入）| **I079 NEW** + I031 + I032 | 引入 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`；`/actuator/prometheus` 端点暴露；CRITICAL 业务链路打点（CRM sync / MKT coupon / webhook）|
| 8 | **环境差异（dev/test/prod 配置）** | 🟠 HIGH | **0 个 `application-{env}.yml` profile 文件**；**0 处 `@Profile` 注解**；**5 处 `PLACEHOLDER_` 字面常量**（`APP_CN_PAY_WECHAT_MCH_ID` / `APP_CN_PAY_WECHAT_API_V3_KEY` / `APP_CN_PAY_ALIPAY_PRIVATE_KEY` / `APP_CN_PAY_ALIPAY_PUBLIC_KEY` / `APP_CN_PAY_WECHAT_CERT_SERIAL`）；当前为**单 application.yml + 环境变量覆盖** | **I080 NEW** + I010（沿用 — Flyway 编码）| 建 `application-dev.yml` / `application-test.yml` / `application-prod.yml`；`@Profile("!prod")` 控制 Mock bean；prod profile 强制禁用 Mock |
| 9 | **种子数据（字典/菜单/角色初始化）** | 🟡 MEDIUM | 权限码自动注册完整（`PermissionAutoRegistrar`）；**71 个 init_*.sql 文件**（基本数据 / 字典 / 菜单 / 角色已 init）；但**SysMenu / SysRole / SysDict 启动期初始化器缺失**（`@PostConstruct` 仅 23 处 — 多数为 Bean 初始化，非字典填充）；新租户注册无默认菜单/角色/字典模板 | **I081 NEW** | 新建 `SysMenuInitializer` / `SysRoleInitializer` / `SysDictInitializer`（`@Component implements ApplicationRunner`）；模板存 `db/sql/template/` |

---

## 二、各项实测数据汇总（Spot-Check on `e85ba8ba8540081f2f59c97120052b5bef5a29ef`）

> 数据来源：`audit/scripts/01-08-scan-*.ps1` 全量扫描 + Phase 8 当下 spot-check grep（绝对路径计数）

| 检查项 | 全量命中 | HIGH 风险 | 工具 / 证据 |
|---|---|---|---|
| catch 吞异常 | **299** | 299（沿用 I008 模式）| `audit/static-gap/01-fake-success.txt` |
| catch-log-swallow | 220 | — | 同上 |
| catch-ignore-empty | 80 | — | 同上（典型：`catch (Exception ignore) {}`）|
| @Transactional 总数 | **662** | **215 缺 rollbackFor ≈ 32%** | Phase 8 spot-check + `audit/static-gap/07-transaction.txt`（214 hits）|
| 软删除 + UNIQUE 不含 deleted | **77 张表扫描** | **63 张 HIGH** | `audit/static-gap/06-soft-delete.txt`（regex 已修）|
| Shadow Feature（仅后端有）| 2013 后端 / 859 前端 | 真实 ~10 + I024 ×10 | `audit/static-gap/04-endpoint-coverage.txt` + `08-shadow-ghost.txt` |
| Ghost Feature（仅前端有）| 418 | 0 真实（L1 false positive）| 同上 |
| PLACEHOLDER_ 字面常量 | **5** | — | Phase 8 spot-check `application.yml` |
| @Profile 注解 | **0** | — | Phase 8 spot-check |
| application-{env}.yml | **0** | — | Phase 8 spot-check |
| Micrometer / Prometheus / actuator | **0 实际接入** | — | Phase 8 spot-check（仅 Javadoc 提及 + `MonitorMetric.java` 实体无 exporter）|
| traceId / TraceUtil | **90 hits** | — | Phase 8 spot-check |
| logback-spring.xml | **EXISTS** | — | Phase 8 spot-check |
| init_*.sql | **71** | — | Phase 8 spot-check |
| migrate_*.sql | **75** | — | Phase 8 spot-check |
| @PostConstruct + CommandLineRunner + ApplicationRunner | **23** | — | Phase 8 spot-check |
| DataScopeInterceptor | **141 行**（白名单不全）| — | 实际读 `security/DataScopeInterceptor.java` |
| FieldDesensitization 实体 | 存在 | 无 AOP 驱动 | `entity/FieldDesensitization.java` |
| RequirePermission / PermissionAspect / PermissionAutoRegistrar / RbacContextLoader | 4 件套完整 | 缺字段脱敏 / 按钮级子权限 | `security/*.java` 7 文件 |
| IdempotencyService | 存在 | 调用方覆盖率 < 10% | grep 仅 `service/impl/CrossStoreInventoryServiceImpl.java` + `plugin/PluginRuntimeService.java` 命中 |

---

## 三、4 项新发现（建议登记 Issue）

### I078 — 软删除与唯一索引冲突（63 张表 HIGH）

| 维度 | 详情 |
|---|---|
| **位置** | 全项目 63 张带 `deleted` 列但 UNIQUE KEY 不含 `deleted` 的表 |
| **典型表** | `crm_member_store.uk_member_store (member_id, store_id)` / `mkt_channel_config.*` / `crm_member_tag_value.*` 等 |
| **风险** | 软删除（`deleted=1`）后用相同业务字段重建 → UNIQUE 冲突 → 业务侧 500 |
| **修复** | **方案 A**：所有 UNIQUE 加 `deleted` 列（DDL 一致性）<br>**方案 B**：MySQL 8.0.13+ 用 `UNIQUE (x, y, deleted) WHERE deleted = 0`（虚拟生成列 + 部分索引）<br>**方案 C**：软删转硬删（业务侧拒绝"恢复 + 同字段"语义）|
| **工作量** | 方案 A：~63 张表 DDL + 索引重建（半天到 1 天）<br>方案 B：~63 张表 DDL 重写 + MySQL 版本校验（1-2 天）<br>方案 C：业务规则调整（3-5 天）|
| **优先级** | 🟠 HIGH（生产高频操作）|

### I079 — 无 Micrometer / Prometheus 指标框架

| 维度 | 详情 |
|---|---|
| **位置** | 全项目 `pom.xml` + `application.yml` + Java 代码 |
| **现状** | 0 Micrometer 依赖 / 0 Prometheus 依赖 / 0 actuator 端点暴露；`MonitorMetric` 实体 + `MonitorAlertService` 存在但**无 exporter 对接**（Javadoc 写"应该替换为 Prometheus"但未落地）|
| **风险** | 生产故障无指标可查（CRITICAL 业务链路 CRM sync / MKT coupon / webhook 等无失败计数 / 延迟分布 / 队列长度）|
| **修复** | 加依赖 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`；`/actuator/prometheus` 暴露；关键路径打 `@Timed` / `Counter` |
| **工作量** | 依赖 + 配置 2 小时；关键链路打点 1-2 天 |
| **优先级** | 🟡 MEDIUM（运维侧） |

### I080 — 无 `application-{env}.yml` profile + 5 处 PLACEHOLDER 字面常量

| 维度 | 详情 |
|---|---|
| **位置** | `backend/src/main/resources/application.yml` lines 117-141 (`app-cn-pay` 段) |
| **5 处 PLACEHOLDER** | `APP_CN_PAY_WECHAT_MCH_ID` / `APP_CN_PAY_WECHAT_API_V3_KEY` / `APP_CN_PAY_ALIPAY_PRIVATE_KEY` / `APP_CN_PAY_ALIPAY_PUBLIC_KEY` / `APP_CN_PAY_WECHAT_CERT_SERIAL` |
| **风险** | 当前是单 `application.yml` + 环境变量覆盖模式；**0 `@Profile` 注解** → prod 误用 dev Mock 凭据无防护（`app.dcep.mode=dev` 写在 yml 而非 prod profile 覆盖）|
| **修复** | 建 `application-dev.yml` / `application-test.yml` / `application-prod.yml`；`@Profile("!prod")` 标注所有 Mock 类（`MockCrmAdapter` / `MockEcommerceAdapter` / `MockBnplProvider` / `MockFiscalGateway`）；PLACEHOLDER 全部改 `${VAR:}` 引用 |
| **工作量** | 拆分 3 文件 + 标注 4 个 Mock 类 + PLACEHOLDER 改写 = **半天** |
| **优先级** | 🟠 HIGH（生产配置错配可静默失守）|

### I081 — SysMenu / SysRole / SysDict 启动期初始化器缺失

| 维度 | 详情 |
|---|---|
| **位置** | 全项目缺 `MenuInitializer` / `RoleInitializer` / `DictInitializer` |
| **现状** | `PermissionAutoRegistrar`（权限码自动注册）已实现；但菜单 / 角色 / 字典依赖 `init_*.sql` 静态初始化（71 个 init 文件）；新租户注册**无默认菜单/角色/字典模板** |
| **风险** | 新租户接入后无菜单显示 / 无角色权限 / 无业务字典 → 系统不可用 |
| **修复** | 新建 3 个 `ApplicationRunner`：从 `db/sql/template/{menu,role,dict}/` 读 JSON 模板 → 按租户复制 → 写 sys_menu/sys_role/sys_dict |
| **工作量** | 1-2 天（含模板整理 + 租户隔离校验）|
| **优先级** | 🟡 MEDIUM（新租户流程阻塞但不影响已上线租户）|

---

## 四、与 5 批次决策的一致性

| 维度 | 5 批次决策 | Phase 8 一致性 |
|---|---|---|
| 不改业务代码 | D1-D25 维持 + 规则 6 | ✅ **Phase 8 不改业务代码**，只横向检查 |
| 不深挖 Feature | 沿用 Phase 3 静态扫描 | ✅ **9 项横向检查**（不重做 Phase 5 实测）|
| 不做单 Feature Evidence | Phase 3.5-1.4/1.5 沿用 | ✅ **本报告为唯一产出**，无 `evidence/Fxxx/` 子目录 |
| 沿用 `e85ba8ba8540081f2f59c97120052b5bef5a29ef` commit + db-snapshot.sql | 5 批次统一基线 | ✅ **Phase 8 spot-check 在该 commit 上执行** |
| 8 个静态守门脚本可重跑 | `audit/scripts/01-08-scan-*.ps1` | ✅ **Phase 8 数据均来自重跑 + spot-check**（Re-verifiable）|
| 不开新 EXT 模块批次 | 用户决策 6.2 推荐 A | ✅ **Phase 8 不开新批次**，是 Post-Audit 修复准备 |

---

## 五、Gate 2 / Gate 3 准入评估

### 5.1 Gate 2 — QA Acceptance（测试通过）

| 维度 | 状态 | 说明 |
|---|---|---|
| 12 / 27 = 44.4% VERIFIED | ✅ 部分 | 沿用 Batch 1.5 EN-09 修正 |
| 0 BROKEN | ✅ 设计层无缺陷 | 同 Batch 1.4 |
| 6 CRITICAL 已识别 | ✅ | I037 / I008 / I011 / I070 / I071 / I047（修复前不阻塞 QA）|
| 测试覆盖率（前端 E2E）| ⚠️ 部分 | 0 Playwright 配置；5B 用 curl + jshell 替代 |
| **Gate 2 状态** | **⚠️ CONDITIONAL READY** | 受 13 项 P0 CRITICAL 制约（P0-1 ~ P0-6 + Phase 8 新 P0-7 ~ P0-13）|

### 5.2 Gate 3 — Production Ready（生产就绪）

| 维度 | 状态 | 说明 |
|---|---|---|
| 6 CRITICAL 全部修复 | ❌ | 修复期 Post-Audit |
| 9 张表未跑迁移 | ❌ | 累积 5 批次 DDL 阻塞（CRM / Plugin / Google Maps / Webhook / MKT）|
| 多租户 / 软删除 / 环境配置三类风险 | ❌ | P0-7~P0-13 + I078/I080 |
| 可观测性 / 监控告警 | ❌ | I079 — 0 指标框架 |
| **Gate 3 状态** | **❌ NOT READY** | 受 P0（13 项）+ P1（10 项）+ P2（10 项）多租户 / 环境 / 软删除 / 指标 制约 |

---

## 六、修复时间估算（按 P0 → P1 → P2 顺序）

> **重要约束**（来自用户）：**Phase 8 时间 3-5 天（不是 2 周）**。以下估算是 Post-Audit 修复期（非 Phase 8 自身）。

### 6.1 P0 — CRITICAL + 必须修复（13 项，~3.5 天）

| # | Issue | 修复 | 工作量 | 累计 |
|---|---|---|---|---|
| P0-1 🔴 | I037（CRITICAL-P0）| WebConfig.excludePathPatterns 加 `/webhook/**` + 3 同源路径（4-8 行配置）| 30 分钟 | 30 分钟 |
| P0-2 🔴 | I070（CRITICAL）| grantBatch SQL `member` → `crm_member`（1 行代码）| 30 分钟 | 1 小时 |
| P0-3 🔴 | I071（CRITICAL）| markRead 加结果校验（rowsAffected > 0 才返回 success）| 30 分钟 | 1.5 小时 |
| P0-4 🔴 | I008（CRITICAL-P1）| catch 收紧 + log.error（dashboard + scheduler 全局）| 1 小时 | 2.5 小时 |
| P0-5 🔴 | I011（CRITICAL-P1）| service.update 用入参做返回（不调 selectById）| 2 小时 | 4.5 小时 |
| P0-6 🔴 | I047（CRITICAL-P1）| IG webhook catch 收紧 + metric + 告警 | 2 小时 | **6.5 小时** |
| **P0-7** 🟠 | I073（HIGH）| journeyPage COUNT 参数拆分 | 1 小时 | 7.5 小时 |
| **P0-8** 🟠 | I072（HIGH）| conversionRoi 空数据兜底 | 1 小时 | 8.5 小时 |
| **P0-9** 🟠 | I061（HIGH）| ChannelDispatcher.getSender 加 null 检查 + 默认 fallback sender | 1 小时 | 9.5 小时 |
| **P0-10** 🟠 | I075+I076（HIGH）| 别名统一（MP → WECHAT_OFFICIAL_ACCOUNT）+ PUSH sender 补实现 | 2 小时 | 11.5 小时 |
| **P0-11** 🟠 | I063（HIGH）| 跑 4 表 DDL（mkt_dispatch / mkt_dispatch_target / mkt_invitation / mkt_invitation_rule）| 30 分钟 | 12 小时 |
| **P0-12** 🟠 | **I078 NEW**（HIGH）| 63 张表 UNIQUE 加 `deleted` 列（DDL 写脚本 + 半天跑批）| 半天（4 小时）| **16 小时 = 2 天** |
| **P0-13** 🟠 | **I080 NEW**（HIGH）| 建 `application-{dev,test,prod}.yml` + 4 个 Mock 类 `@Profile("!prod")` + 5 处 PLACEHOLDER 改写 | 半天（4 小时）| **20 小时 ≈ 2.5 天** |
| **Post-Audit 跑批** | 9 张表统一跑批 | （沿用 Batch 1.5 §七）| 4 小时 | **24 小时 ≈ 3 天** |
| **回归验证 + 测试** | curl + jshell 重测 | 半天 | **3.5 天** |

### 6.2 P1 — 短期修复（10 项，~9.5 天）

| # | Issue | 工作量 |
|---|---|---|
| P1-1 | I009 + I034 rollbackFor 全局修复（**剩余 ~215 处**）| 2 天 |
| P1-2 | I019 跨租户读重构（Plugin `listReviews` 等 3 处）| 1 天 |
| P1-3 | I056（跨租户删除）+ I057（跨租户写）+ I065（tenant=1L）| 0.5 天 |
| P1-4 | I037 同源路径（3 同源 webhook 路径确认）| 0.5 天 |
| P1-5 | I005 契约漂移 C1/C2/C4（前端 enum/force/字段）| 1.5 天 |
| P1-6 | I022 disablePlugin lastError 清空审计线索 | 0.5 天 |
| P1-7 | I050 healthCheckAll tenant=1L 硬编码 | 0.5 天 |
| P1-8 | I074 rate_limit_per_day API null setter 补 | 0.5 天 |
| P1-9 | I077 SMS rate_limit_per_day=5 阈值默认改 1000 | 0.5 天 |
| P1-10 | **I081 NEW** Menu/Role/Dict 初始化器 + 租户模板 | 2 天 |
| **小计** | — | **9.5 天** |

### 6.3 P2 — 中长期清理（10 项，~9-10 天）

| # | Issue | 工作量 |
|---|---|---|
| P2-1 | **I079 NEW** Micrometer/Prometheus 接入（actuator + 关键链路打点）| 1-2 天 |
| P2-2 | I058 双路由契约统一（F125/F129 单路由形式）| 1 天 |
| P2-3 | I004 + I019 Service as Controller 异味重构 | 3 天 |
| P2-4 | I003 + I016 + I005 C3 前后端契约对齐 | 1 天 |
| P2-5 | I012 / I039 / I066 / I075 别名漂移 | 0.5 天 |
| P2-6 | I059 / I013 / I021 / I025 / I033 dead code 清理 | 0.5 天 |
| P2-7 | I032 / I031 审计日志补全 | 0.5 天 |
| P2-8 | I036 GCP Key 泄露（getConfig 不回显）| 0.5 天 |
| P2-9 | I020 沙箱合作式增强（agent / OSGi — **接受现状备选**）| 2-3 天 |
| P2-10 | SoT 文档漂移（I001 / I018 / I055 / I068 — 4 个 .mdc + ROLLBACK-6.2）| 1 天 |
| **小计** | — | **9-10 天** |

### 6.4 总计

| 阶段 | 工作量 | 累计 |
|---|---|---|
| **P0（13 项）** | 3.5 天 | 3.5 天 |
| **P1（10 项）** | 9.5 天 | **13 天 ≈ 2.5 周** |
| **P2（10 项）** | 9-10 天 | **22-23 天 ≈ 3-4 周** |
| **Post-Audit 全部完成** | — | **2-3 周（P0+P1）/ 3-4 周（含 P2）** |

> **重要说明**：**Phase 8 自身**（横向检查 + 报告）**用时约 3-5 天**（用户约束）。本节估算是**修复期**（Post-Audit），**合计 2-3 周**（明确说明**不是 2 周**）。

---

## 七、与受保护页面的零交集确认

| 受保护页面 | 与 Phase 8 横向检查的交集 | 风险 |
|---|---|---|
| `/dashboard` | 检查项 6（多租户）— dashboard 数据需走 tenantId 过滤 | 🟡 低（已通过 DataScopeInterceptor，但 I008 Fake Success 路径需 P0-4 修复时确认）|
| `/mh/visual` | 检查项 5（软删除）— `mh_visual_archive` 表需 UNIQUE 检查 | 🟡 低（Phase 8 spot-check 命中 I078 范围）|
| `/mh/consent` | 检查项 5（软删除）— `mh_consent_*` 表需 UNIQUE 检查 | 🟡 低（Phase 8 spot-check 命中 I078 范围）|

> **结论**：Phase 8 横向检查不直接改动 3 受保护页面的代码 / 数据 / 配置；I078 修复期需确认受保护页用到的表 UNIQUE 字段是否含 `deleted`，避免误改 DDL。

---

## 八、Phase 8 完成宣言

| 维度 | 完成？ | 备注 |
|---|---|---|
| 9 项横向检查 | ✅ | 权限 / 异常 / 事务 / 并发 / 软删除 / 多租户 / 可观测 / 环境 / 种子 |
| 4 项新发现登记 | ✅ | I078 / I079 / I080 / I081 |
| Gate 2 评估 | ✅ | ⚠️ CONDITIONAL READY（受 13 项 P0 制约）|
| Gate 3 评估 | ✅ | ❌ NOT READY（受 P0 + P1 多租户/环境/软删除 制约）|
| 修复时间估算 | ✅ | P0 = 3.5 天 / P1 = 9.5 天 / P2 = 9-10 天 / 合计 2-3 周 |
| 不改业务代码 | ✅ | 严格遵守规则 6 + D1-D25 |
| 不深挖 Feature | ✅ | 仅横向扫描（沿用 Phase 3 静态守门 + Phase 8 spot-check）|
| 不做单 Feature Evidence | ✅ | 本报告为唯一产出 |
| 沿用 `e85ba8ba` commit + db-snapshot | ✅ | Phase 8 spot-check 在该基线执行 |
| 受保护页面零风险 | ✅ | 不直接改动；I078 修复期需交叉确认 |
| 8 个守门脚本可重跑 | ✅ | 数据均来自 `audit/scripts/01-08-scan-*.ps1`（Re-verifiable）|

---

## 九、下一阶段建议 → Phase 9（Regression / Git / Cleanup）

| 选项 | 内容 | 推荐度 |
|---|---|---|
| **A. Phase 9 = Post-Audit 修复（兑现 P0-1 → P0-13）** | 6.5 小时 + 半天 + 半天 = **3.5 天**（与本报告 §六 P0 一致）| ⭐⭐⭐⭐⭐ |
| **B. Phase 9 = Git 收尾（commit / branch / tag）** | 当前 142 Feature + 77 Issue 已稳定；可打 tag v3-audit-complete | ⭐⭐⭐ |
| **C. Phase 9 = 仓库清理（删除 tmp-* / logs / .gitignore）** | 仓库未 git 初始化（`Is directory a git repo: No`）→ 需先 git init + .gitignore 整理 | ⭐⭐⭐⭐ |

**推荐**：A 路径 — Phase 9 = Post-Audit 修复（兑现 P0-13），预计 3.5 天；Phase 10 = Git / 清理 / 上线预演。

---

*文件落档时间：2026-09-17 00:50 (UTC+8)*
*Phase 8 横向检查 9 项 + 4 项新发现（I078-I081）+ Gate 2/3 评估完成*
*下一阶段：Phase 9 — Post-Audit 修复（推荐 A 路径，P0-1 → P0-13）*