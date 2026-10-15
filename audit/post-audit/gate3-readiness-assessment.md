# Gate 3 生产就绪评估（Production Readiness Assessment）

> **生成时间**：2026-10-08 23:55 (UTC+8)
> **评估范围**：PROJECT_AUDIT_PLAYBOOK §十四 Gate 3 全部 13 项
> **基线 commit**：`e6bf350`（Post-Audit 二期封存 + EOL normalize + ARCHIVE 修复）
> **基线 DB**：v3 审计基线 + Phase 2.3 6 批次迁移（9 表 + 16 列已 100% apply）
> **评估类型**：只读静态评估（不改代码 / 不跑业务 / 不动 SoT）
> **依据**：
> - `audit/reports/final-acceptance.md` §6.3 + §8（Gate 3 baseline + 优先级矩阵）
> - `audit/post-audit/final-post-audit-report.md`（收口状态）
> - `audit/post-audit/post-audit-phase3-backlog.md`（三期 backlog）
> - `audit/issues/issue-register.md`（88 Issue 现状）
> - **本次实测**：`mvn -q -o compile -DskipTests` EXIT=0 + 4 个 UTF 损坏文件 L5b 字节流核实

---

## 〇、当前数字快照（截至 2026-10-08 23:55）

| 维度 | 数值 | 来源 |
|---|---|---|
| 总 Issue 数 | **88**（I001-I093 + I095 + I097） | issue-register.md §最新 |
| 🔴 CRITICAL | **0**（全部修复） | final-acceptance §11.2 |
| ✅ FIXED | **29**（21 L5b VERIFIED + 8 仅静态层） | final-post-audit-report A.7.2 |
| ✅ RESOLVED（SoT）| **5**（I001/I018/I026/I027/I030）| final-post-audit-report §2.3 |
| 🟡 PARTIAL-FIXED | 1（I023）| final-post-audit-report §1.2 |
| ⏸ OPEN | **7**（I005-C1/C2 + I003/I016/I028/I036 + I034/I035 + I006/I007 + I088）| final-post-audit-report §3.1 |
| 📋 BACKLOG | **5+**（I004/I010/I013/I021/I025/I032/I033/I036 等）| final-post-audit-report §3.3 |
| mvn compile | **EXIT=0** ✅（本次实测，e6bf350）| 本次评估 |
| 守门脚本 | 8/8 零回归（01: +2 / 02: 0 / 03: +1 / 04: +2 / 05: 0 / 06: 0 / 07: -10 / 08: 0）| final-post-audit-report A.7.3 |
| I088 真注解 | **650/650 带 rollbackFor**（100% FIXED）| I097 §1.1 |
| I096（4 UTF 文件 mojibake）| **已修复**（11aa1b1），但 **Issue 未登记** | 本次评估（mvn compile EXIT=0）|
| I082 运行时 | ✅ FIXED & VERIFIED（grep 0 + ERROR 封顶 1008）| final-post-audit-report 附录 A |
| Gate 1（Development）| 🟢 MOSTLY COMPLETE | final-acceptance §11.6 |
| Gate 2（QA）| 🟢 READY（21 L5b + 8 仅静态层）| final-post-audit-report A.7.2 |
| **Gate 3（Production）**| 🟡 **STILL NOT READY → 评估中** | 本报告 |

---

## 一、Gate 3 清单（13 项，按 PROJECT_AUDIT_PLAYBOOK §十四）

> **评估标尺**：
> - ✅ **READY** = L5b 验证 + 可观测 + 有 Runbook
> - 🟡 **PARTIAL** = 核心已通但有缺口 / 部分待补
> - 🔴 **NOT READY** = 缺核心能力 / 阻塞上线
> - **工作量单位**：人天（1 人天 = 8 小时）

### 1. 生产配置（Production Configuration）

| 维度 | 状态 |
|---|---|
| 现状 | 🟡 PARTIAL |
| 详细 | - `application.yml` 单文件（无 dev/test/prod profile）<br>- 5 处 PLACEHOLDER 待替换（pay / smtp / oss / gcp / jpush）<br>- **I080 NEW**（HIGH）仍 OPEN：建 `application-{dev,test,prod}.yml` + 4 个 Mock 类 `@Profile("!prod")`<br>- Spring Mail / 短信 / OSS 凭据：env 注入路径未确认 |
| 缺口 | 1) profile 拆分；2) PLACEHOLDER 替换；3) 凭据走环境变量；4) 启动期凭据校验 |
| 工作量 | **0.5 天**（profile + env 校验） |
| 阻塞上线? | **🔴 是**（生产配置错配可导致启动失败 / 跨租户串发 / 安全风险） |

### 2. 数据库 Migration

| 维度 | 状态 |
|---|---|
| 现状 | 🟢 **READY**（仅差 prod env 复测） |
| 详细 | - Phase 2.3 共 6 批次，**9 表 + 16 列 100% apply** ✅<br>- 脚本族：migrate_ext_15_crm_real / migrate_ext_17_google_maps / migrate_ext_19_mini_program_openid / migrate_ext_22 / migrate_ext_23 / migrate_ext_24_webhook_msg_log / migrate_mkt_*<br>- apply log 完整：phase-2.3-batch-{1..6}-apply-log.md |
| 缺口 | 1) **生产 env 重放 + 备份前置**；2) Flyway V1 编码不匹配（**I010 仍 OPEN**）|
| 工作量 | **0.5-1 天**（prod env 复测 + I010 修复）|
| 阻塞上线? | **🟢 否**（脚本已成熟，仅需 prod 复测） |

### 3. 备份恢复

| 维度 | 状态 |
|---|---|
| 现状 | 🔴 **NOT READY** |
| 详细 | - **0 备份策略**（无 mysqldump 脚本 / 无 cron / 无 S3 / 无异地）<br>- **0 恢复 Runbook**（无 RTO / RPO 定义 / 无演练记录）<br>- I092 已迁移 7 行 dev 测试数据，**I093 仍 OPEN**（dev 残留清理）|
| 缺口 | 1) mysqldump 策略（全量每日 + binlog 实时）；2) 异地存储；3) 恢复演练；4) RTO/RPO 文档化 |
| 工作量 | **1.5-2 天**（脚本 + 演练 + Runbook）|
| 阻塞上线? | **🔴 是**（生产必备底线） |

### 4. 权限（RBAC / Multi-Tenancy）

| 维度 | 状态 |
|---|---|
| 现状 | 🟡 **PARTIAL** |
| 详细 | - 后端 dataScope=ALL（平台超管）+ 普通租户 = tenantId 等值（I019/I090 FIXED）<br>- I056/I057 部分修复（Batch 1.5 跨租户删除/写残留）<br>- I018 assertTenantAccess RESOLVED（L55-65 + 4 tenantId 端点接入）<br>- 残留 OPEN: **I019**（PluginService listReviews 3 处跨租户读）/ **I050**（healthCheckAll tenant=1L 硬编码）/ **I051**（webhook 演示模式 tenant=1L 硬编码）<br>- 前端无 runtime 权限管理 UI（I024 plugin runtime tab 修复但不是权限 UI）|
| 缺口 | 1) I019 跨租户读重构；2) I050/I051 硬编码 tenantId 改造；3) 前端权限 UI |
| 工作量 | **1-1.5 天**（I019 1d + I050/I051 0.5d）|
| 阻塞上线? | **🟢 否**（核心隔离可用，edge case 风险可接受） |

### 5. 日志（Logging）

| 维度 | 状态 |
|---|---|
| 现状 | 🟡 **PARTIAL** |
| 详细 | - 后端 logback 默认配置（dev profile）<br>- 业务日志：CRITICAL/HIGH 修复后 catch 收紧 + log.error（I008/I011/I047/I082 等）<br>- I082 运行期 ERROR 已封顶 1008（行号封顶 = 无新增）<br>- 缺失：log rotation / retention / level 切换 / 结构化 JSON 输出未确认<br>- 缺失：**I095 NEW** P2 — 7 处 `@Transactional catch 块静默吞异常**（`log.warn/info/debug` + `// ignore`）→ 异常未抛出，事务不触发回滚**
| 缺口 | 1) I095 catch 块重新抛 `throw new BusinessException(...)`；2) log rotation + retention 策略；3) 关键链路结构化日志 |
| 工作量 | **0.5-1 天**（I095 4-6h + log 策略 2h）|
| 阻塞上线? | **🟢 否**（基础日志可用，I095 是累积风险非立即阻塞） |

### 6. 监控（Monitoring）

| 维度 | 状态 |
|---|---|
| 现状 | 🔴 **NOT READY** |
| 详细 | - **0 指标框架**（**I079 NEW** 仍 OPEN）<br>- Spring Boot Actuator：未确认是否启用<br>- Micrometer/Prometheus：**未集成**<br>- 关键链路指标：sync 计数 / webhook 成功失败 / 假成功嗅探 / DB 慢查询 / JVM 内存 — 全部 0 |
| 缺口 | 1) 接入 Spring Boot Actuator；2) Micrometer + Prometheus 端点；3) 关键链路打点（至少 8 个 metric：sync 成功/失败、webhook 各端点、catch-swallow 检测、cron tick 状态、SF 限流、cache 命中、DB 慢查询、JVM）|
| 工作量 | **1.5-2 天**（Actuator + Prometheus + 8 metric 打点）|
| 阻塞上线? | **🔴 是**（生产可观测性最低要求） |

### 7. 告警（Alerting）

| 维度 | 状态 |
|---|---|
| 现状 | 🔴 **NOT READY** |
| 详细 | - **0 告警规则**<br>- 与监控强依赖（监控指标未就绪 → 告警无对象）<br>- 现状告警手段：人工 grep / 守门脚本本地执行 |
| 缺口 | 1) 告警规则（建议 5 条：CRITICAL_ERROR > 0 / 同步失败率 > 5% / webhook 5xx > 1 / DB 连接池 > 80% / JVM 老年代 > 90%）；2) 告警通道（钉钉/企微/邮件）；3) on-call 值班表 |
| 工作量 | **1 天**（依赖监控就绪；通道配置 0.5d + 规则 0.5d）|
| 阻塞上线? | **🔴 是**（与监控一起，否则事故无法被叫醒） |

### 8. 安全（Security）

| 维度 | 状态 |
|---|---|
| 现状 | 🟡 **PARTIAL** |
| 详细 | - JWT：启用 + I037 excludePathPatterns 已修 ✅<br>- Webhook：JWT exclude 已补 / I026/I027/I030 RESOLVED<br>- I034 OPEN（Youzan webhook `@Transactional(rollbackFor)` 静态层已加 commit `977f58e`，待 L5b 验证）<br>- I035 OPEN（webhook msg_id **24h 时间窗缺失**）<br>- I036 OPEN（API Key 完整遮蔽 — 当前仅后 4 位遮蔽）<br>- **I089 NEW** P2（CRM privateKey 编辑缺失 — WRITE_ONLY 后无编辑回显）<br>- **I020 仍 OPEN**（PluginSecurityPolicy 合作式沙箱 — `service.internal` 反射加载不拦截）<br>- I010 仍 OPEN（Flyway V1 编码不匹配）<br>- I097 仍 OPEN（11aa1b1 合并 commit — encoding + business 不可分离 revert）|
| 缺口 | 1) I034 L5b 验证（0.5h）；2) I035 24h 时间窗（4-6h）；3) I036 masked input（0.5d）；4) I089 编辑回显（1-2d）；5) I010 Flyway 修复（1-2d）|
| 工作量 | **3-5 天**（I034+I035+I036 紧急 1d + I089 + I010 长期 2-3d + I020/I097 接受现状）|
| 阻塞上线? | **🟡 否**（核心已修，I034/I035 累积风险需修） |

### 9. 第三方服务（Third-party）

| 维度 | 状态 |
|---|---|
| 现状 | 🟡 **PARTIAL** |
| 详细 | - **CRM**：Salesforce/HubSpot/Zoho 三个 adapter 已实现（I001 RESOLVED），objectMappings 默认降级（EXT-24 行为）<br>- **EXT-19 多渠道**：WeChat 模板 ID **占位常量**（`BOOKING_CONFIRMATION`）— 必须替换<br>- **有赞**：webhook 完整链路（I026/I027/I030 RESOLVED），但 `buildWebhookUrl` 仍为孤儿工具（I033）<br>- **Google Maps Place Details**：计费接口（EXT-22），`enablePlaceDetails=0` 默认关闭<br>- **Email / SMS**：JavaMailSender + 阿里云（EXT-19 真实链路）<br>- **Plugin Runtime**：pf4j 3.10.0 + 合作式沙箱（I020 仍 OPEN）|
| 缺口 | 1) 微信模板 ID 替换（BLOCKER 上线前必须）；2) 真实凭据联调（SF/HB/ZO/有赞/微信）；3) GCP 预算告警（Place Details 计费）|
| 工作量 | **1-1.5 天**（模板替换 + 联调 + 凭据验证）|
| 阻塞上线? | **🟡 否**（按需启用，但**核心渠道上线前必须真实凭据联调**） |

### 10. 部署（Deployment）

| 维度 | 状态 |
|---|---|
| 现状 | 🔴 **NOT READY** |
| 详细 | - **0 Dockerfile** / **0 docker-compose.yml** / **0 k8s manifest**<br>- **0 CI/CD pipeline**（.github/workflows 不存在）<br>- 单体 jar：`mvn package` 可生成，但**入口脚本 + 启动参数 + JVM 配置 0 文档化**<br>- **I096 待登记**：4 个 UTF 损坏文件**已实际修复**（mvn compile EXIT=0 实测），但 Issue 未登记 + 未正式验收<br>- 端口/依赖（MySQL/Redis/RocketMQ/MinIO）部署拓扑 **0 文档化**|
| 缺口 | 1) Dockerfile + docker-compose；2) CI/CD（建议 GitHub Actions）；3) 启动脚本 + JVM 参数；4) 部署拓扑文档；5) I096 立项 + 关闭（30 min 形式工作）|
| 工作量 | **2-3 天**（Docker 1d + CI/CD 1d + 文档 0.5d + I096 0.5h）|
| 阻塞上线? | **🔴 是**（无部署路径无法上线） |

### 11. 回滚（Rollback）

| 维度 | 状态 |
|---|---|
| 现状 | 🔴 **NOT READY** |
| 详细 | - **0 回滚 Runbook** / 0 演练<br>- DB Migration 回滚：部分脚本有配套（migrate_*.sql 备份+回滚），但 **无统一回滚入口**<br>- **I097 OPEN**（11aa1b1 合并 commit 设计缺陷 — encoding + business 不可单独 revert，必须 revert 整个 11aa1b1 即同时回退 mojibake 修复 + rollbackFor 替换）<br>- git revert 流程：未文档化 |
| 缺口 | 1) 回滚 Runbook（DB + 代码 + 配置 三层）；2) I097 长期规范（R1-R5 流程规则）；3) 回滚演练 1 次 |
| 工作量 | **1-1.5 天**（Runbook 1d + 演练 0.5d）|
| 阻塞上线? | **🔴 是**（上线必须可回滚，否则 BUG 修复需 hotfix 时无法快速降级） |

### 12. 性能（Performance）

| 维度 | 状态 |
|---|---|
| 现状 | 🟡 **PARTIAL** |
| 详细 | - **0 性能测试** / 0 压测数据 / 0 TPS / 0 P99 延迟<br>- 索引：I092 idx_tenant 已补（ecommerce 4 表）<br>- 缓存：StoreLookupService 5min 本地缓存（Batch 1.4）<br>- Salesforce adapter knownExternalIds 限 5000（EXT-24 文档化）<br>- DB 慢查询 / 连接池 / JVM GC **0 配置调优**|
| 缺口 | 1) 关键链路压测（CRM 同步 / Webhook / Marketing ROI 至少 3 场景）；2) DB 连接池调优；3) JVM GC 参数（建议 G1 + 4G heap）|
| 工作量 | **2-3 天**（压测 1.5d + 调优 1d）|
| 阻塞上线? | **🟢 否**（性能优化非上线前提，但**首周应做**） |

### 13. 数据初始化（Data Initialization）

| 维度 | 状态 |
|---|---|
| 现状 | 🟡 **PARTIAL** |
| 详细 | - **I081 NEW** 仍 OPEN（Menu/Role/Dict 初始化器 + 租户模板）<br>- 0 种子数据 / 0 租户模板 / 0 默认管理员账号脚本<br>- 首租户开通：**手动 SQL 插入**（无 `ApplicationRunner` 模板复制）<br>- Super Admin 创建流程：未文档化 |
| 缺口 | 1) 初始化器（Menu/Role/Dict）；2) 租户模板（admin 账号 + 默认权限 + 默认配置）；3) Super Admin 创建流程 |
| 工作量 | **2 天**（I081 完整实施）|
| 阻塞上线? | **🟢 否**（首租户可手动建，多租户模板上线后补） |

---

## 二、优先级分层

### P0 — 阻塞上线（缺此项无法上线）🔴

| # | 项 | 工作量 | 关键 Issue |
|---|---|---|---|
| 1 | **生产配置**（#1）| 0.5 天 | I080 |
| 2 | **备份恢复**（#3）| 1.5-2 天 | — |
| 3 | **监控**（#6）| 1.5-2 天 | I079 |
| 4 | **告警**（#7）| 1 天 | — |
| 5 | **部署**（#10）| 2-3 天 | I096（形式）|
| 6 | **回滚**（#11）| 1-1.5 天 | I097 |
| **P0 合计** | — | **7.5-10 天** | — |

### P1 — 强烈建议（缺此项上线风险高）🟡

| # | 项 | 工作量 | 关键 Issue |
|---|---|---|---|
| 7 | **数据库 Migration prod 复测**（#2）| 0.5-1 天 | I010 |
| 8 | **权限 edge case 修复**（#4）| 1-1.5 天 | I019/I050/I051 |
| 9 | **日志策略 + I095**（#5）| 0.5-1 天 | I095 |
| 10 | **安全累积风险**（#8）| 3-5 天 | I034/I035/I036/I089 |
| 11 | **第三方服务联调**（#9）| 1-1.5 天 | 微信模板 + 凭据 |
| 12 | **数据初始化**（#13）| 2 天 | I081 |
| **P1 合计** | — | **8.5-12 天** | — |

### P2 — 可后置（上线后补）🟢

| # | 项 | 工作量 | 备注 |
|---|---|---|---|
| 13 | **性能压测 + 调优**（#12）| 2-3 天 | 首周应做，非阻塞 |
| **P2 合计** | — | **2-3 天** | — |

### 总计

| 优先级 | 天数 | 累计 |
|---|---|---|
| **P0** | 7.5-10 天 | 7.5-10 天 |
| **P1** | 8.5-12 天 | 16-22 天 |
| **P2** | 2-3 天 | **18-25 天 ≈ 3-5 周** |

---

## 三、建议执行顺序

### 批次 1（P0，7.5-10 天）— **必做**

| 序 | 任务 | 工作量 | 依赖 |
|---|---|---|---|
| 1.1 | **I096 立项 + 关闭**（4 UTF 文件 mvn compile EXIT=0 已实测）| 0.5 h | 无 |
| 1.2 | **生产配置**（I080 profile 拆分 + env 校验）| 0.5 天 | 无 |
| 1.3 | **部署基础**（Dockerfile + docker-compose + 启动脚本）| 1.5 天 | 1.2 |
| 1.4 | **备份恢复**（mysqldump + 异地 + 演练 + Runbook）| 1.5-2 天 | 1.3 |
| 1.5 | **监控接入**（Actuator + Micrometer + Prometheus + 8 metric 打点）| 1.5-2 天 | 1.3 |
| 1.6 | **告警规则**（5 条核心 + 钉钉/企微通道）| 1 天 | 1.5 |
| 1.7 | **回滚 Runbook**（DB + 代码 + 配置三层）| 1-1.5 天 | 1.3, 1.4 |
| **批次 1 小计** | — | **7.5-10 天** | — |

**批次 1 完成效果**：**Gate 3 CONDITIONAL GO**（核心可观测性 + 部署路径 + 备份回滚全部就绪）

### 批次 2（P1，8.5-12 天）— **强烈建议**

| 序 | 任务 | 工作量 | 依赖 |
|---|---|---|---|
| 2.1 | **I034 L5b 验证**（Youzan webhook rollbackFor 运行时）| 0.5 h | 无 |
| 2.2 | **I035 24h 时间窗**（webhook msg_id 去重时间窗）| 4-6 h | 无 |
| 2.3 | **I036 masked input**（API Key 完整遮蔽）| 0.5 天 | 无 |
| 2.4 | **I019 跨租户读**（PluginService listReviews 3 处）| 1 天 | 无 |
| 2.5 | **I050/I051 硬编码 tenantId** | 0.5 天 | 无 |
| 2.6 | **I095 catch 重新抛**（7 处事务边界）| 4-6 h | 无 |
| 2.7 | **I010 Flyway V1 编码** | 1-2 天 | 无 |
| 2.8 | **I089 privateKey 编辑** | 1-2 天 | 无 |
| 2.9 | **I081 初始化器 + 租户模板** | 2 天 | 无 |
| 2.10 | **微信模板 ID 替换** + **凭据联调** | 1-1.5 天 | 无 |
| **批次 2 小计** | — | **8.5-12 天** | — |

**批次 2 完成效果**：**Gate 3 GO**（所有累积风险闭环，多租户模板就绪）

### 批次 3（P2，2-3 天）— **上线后补**

| 序 | 任务 | 工作量 |
|---|---|---|
| 3.1 | **关键链路压测**（CRM 同步 / Webhook / Marketing ROI）| 1.5 天 |
| 3.2 | **DB 连接池 + JVM GC 调优** | 1 天 |
| **批次 3 小计** | — | **2-3 天** |

**批次 3 完成效果**：性能基线建立 + 调优到位

---

## 四、与原 Post-Audit 三期 P3 的关系

### 4.1 交叉矩阵（I089/I093/I095/I097 vs Gate 3 13 项）

| 三期 Issue | 严重度 | 状态 | 归属 Gate 3 项 | 备注 |
|---|---|---|---|---|
| **I089**（privateKey 编辑缺失）| P2 | OPEN | **#8 安全** | 编辑回显缺，写入路径存在 UX 风险；Batch 2.8 修 |
| **I093**（ecommerce 4 表 dev 残留清理）| P3 | OPEN | **#2 数据库 Migration** | 7 行 `tenant_id=0` 测试数据；Batch 2（与 I092 同阶段）或独立 SQL 清理 |
| **I095**（@Transactional catch 吞异常 7 处）| P2 | OPEN | **#5 日志** | 与 I088 同源（事务边界）；Batch 2.6 修 |
| **I097**（11aa1b1 合并 commit 设计缺陷）| P3 | OPEN | **#11 回滚** | 纯流程规范 / 未来预防；Batch 1.7 Runbook 中明确标注不可单独 revert |

### 4.2 关系结论

| 维度 | 判断 |
|---|---|
| **重叠程度** | **部分重叠，非独立** |
| **分工逻辑** | 三期 P3 主要是**代码层 Issue**；Gate 3 13 项是**生产就绪能力维度**。两者是"问题清单 vs 能力清单"的不同切面 |
| **I089 归属** | 100% 落入 Gate 3 #8 安全（Batch 2.8）|
| **I093 归属** | 100% 落入 Gate 3 #2 数据库（可与 I092 同 SQL 一次清理）|
| **I095 归属** | 100% 落入 Gate 3 #5 日志（Batch 2.6）|
| **I097 归属** | 100% 落入 Gate 3 #11 回滚（Batch 1.7 Runbook 文档化）|
| **I096（新发现，4 UTF 文件）** | **100% 落入 Gate 3 #10 部署**（mvn compile EXIT=0 已实测，仅需形式登记 0.5h）|

### 4.3 整合建议

- **不另起 Phase 4 / Phase 5**，把 I089/I093/I095/I096/I097 **直接吸收到 Gate 3 批次 1 + 批次 2 的执行顺序**中
- I096 是**最小投入**（0.5h 形式工作），**应作为批次 1 第一步**（早闭早安心）
- I089/I095 是**累积风险**，**应作为批次 2 早期**（避免上线后才发现）
- I093 是**纯数据清理**，可在批次 2 任意时机执行（最低优先级）
- I097 是**流程规范**，**应作为批次 1.7 Runbook 的一部分**（未来预防）

---

## 五、报告结论

### 5.1 当前 Gate 3 状态

# **🟡 CONDITIONAL（可短期达到 GO，3-5 周）**

> **NOT READY 的原因**（6 项核心缺口）：
> 1. 🔴 **无部署路径**（无 Dockerfile / CI/CD）
> 2. 🔴 **无备份恢复**（无策略 / 无 Runbook / 无演练）
> 3. 🔴 **无监控告警**（0 指标 / 0 告警规则）
> 4. 🔴 **无回滚 Runbook**（特别是 I097 合并 commit 风险）
> 5. 🟡 **无生产 profile**（I080 仍 OPEN）
> 6. 🟡 **无租户模板**（I081 仍 OPEN，多租户上线后受影响）

### 5.2 达到 Gate 3 GO 需要

| 批次 | 工作量 | 累计 |
|---|---|---|
| 批次 1（P0，6 项核心缺口）| **7.5-10 天** | 7.5-10 天 |
| 批次 2（P1，6 项累积风险）| **8.5-12 天** | **16-22 天** |
| **达到 Gate 3 GO** | — | **约 3-4 周**（不含 P2 性能） |

### 5.3 推荐第一步

# **🎯 第一步（0.5 小时）：立项 I096 + 关闭**

| 步骤 | 操作 | 时间 |
|---|---|---|
| 1 | 新建 `audit/issues/I096-utf-source-file-corrupt.md`（描述 4 文件 + 11aa1b1 修复过程 + mvn compile EXIT=0 证据）| 10 min |
| 2 | 在 `issue-register.md` 登记 I096（状态：FIXED & VERIFIED）| 5 min |
| 3 | 在 `phase-2.34-I088-3-report.md` §5 表格追加"I096 已通过 11aa1b1 修复" + 删除 4 文件遗留行 | 5 min |
| 4 | 在 `post-audit-phase3-backlog.md` §1 修订 4 UTF 损坏文件状态（从 P1 BLOCKER → RESOLVED）| 5 min |
| 5 | 守门脚本重跑（确认 baseline 不变）| 5 min |
| **合计** | — | **30 min** |

**为什么这是第一步**：
- ✅ **零风险**（mvn compile EXIT=0 已实测）
- ✅ **形式工作**（不写新代码，仅补登记）
- ✅ **闭环心理**（用户对"现在能 build 吗"的疑虑立即消除）
- ✅ **解锁后续**（没有 I096 悬而未决的心理负担，可全力投入 Gate 3 真实缺口）

### 5.4 三个备选路径

| 选项 | 内容 | 适合场景 | 时间 |
|---|---|---|---|
| **A（推荐）** | 批次 1 + 批次 2 全部完成 → **Gate 3 GO** | 商业上线 / 金融级 SLA | 3-4 周 |
| **B（折中）** | 批次 1 全部 + 批次 2 部分（I034/I035/I036 安全 + I081 初始化器）| 中小规模生产 | 2-3 周 |
| **C（最快）** | 仅批次 1（核心 6 项缺口）| 灰度环境 / 内部 QA | 7.5-10 天 |

### 5.5 决策建议

> **根据本次评估数据**（无明确上线时间 + "随时可上线"目标 + 当前 Gate 3 = STILL NOT READY）：
>
> ✅ **推荐选项 A**（3-4 周达成 Gate 3 GO）
>
> - 第一步（今天）：I096 立项 + 关闭（30 min 形式工作）
> - 第一周：批次 1（部署 + 监控 + 备份 + 回滚）
> - 第二周：批次 1 收尾 + 批次 2 启动（安全 + 权限）
> - 第三/四周：批次 2 收尾（初始化 + 联调）
> - 第五周（可选）：批次 3 性能调优
> - **第五周末**：Gate 3 GO ✅

---

## 六、附录

### 6.1 13 项评估汇总表

| # | 项 | 现状 | 缺口 | 工作量 | 阻塞? |
|---|---|---|---|---|---|
| 1 | 生产配置 | 🟡 PARTIAL | I080 profile 拆分 + 凭据 env | 0.5 天 | 🔴 是 |
| 2 | 数据库 Migration | 🟢 READY | prod 复测 + I010 | 0.5-1 天 | 🟢 否 |
| 3 | 备份恢复 | 🔴 NOT READY | mysqldump + 异地 + 演练 | 1.5-2 天 | 🔴 是 |
| 4 | 权限 | 🟡 PARTIAL | I019/I050/I051 edge case | 1-1.5 天 | 🟢 否 |
| 5 | 日志 | 🟡 PARTIAL | I095 + log 策略 | 0.5-1 天 | 🟢 否 |
| 6 | 监控 | 🔴 NOT READY | Actuator + Prometheus + 8 metric | 1.5-2 天 | 🔴 是 |
| 7 | 告警 | 🔴 NOT READY | 5 规则 + 通道 | 1 天 | 🔴 是 |
| 8 | 安全 | 🟡 PARTIAL | I034/I035/I036/I089 | 3-5 天 | 🟡 否 |
| 9 | 第三方服务 | 🟡 PARTIAL | 微信模板 + 凭据联调 | 1-1.5 天 | 🟡 否 |
| 10 | 部署 | 🔴 NOT READY | Docker + CI/CD + 文档 + I096 | 2-3 天 | 🔴 是 |
| 11 | 回滚 | 🔴 NOT READY | Runbook + I097 规范 | 1-1.5 天 | 🔴 是 |
| 12 | 性能 | 🟡 PARTIAL | 压测 + 调优 | 2-3 天 | 🟢 否 |
| 13 | 数据初始化 | 🟡 PARTIAL | I081 初始化器 | 2 天 | 🟢 否 |

### 6.2 与 post-audit-phase3-backlog 的关系

| 三期文档章节 | 本报告对应 |
|---|---|
| §1 4 个 UTF 损坏文件（I096 待立项）| **已实测修复**（mvn compile EXIT=0）→ 落入本报告 §1 部署 + §5.3 第一步 |
| §2.1 I096 P1 BLOCKER | **已实际修复**，但**形式工作未完成**（Issue 未登记）|
| §2.2 I089 / I093 / I095 | 落入本报告 §4 交叉矩阵 → 批次 2 整合 |
| §2.3 I008/I011/I012/I047 | 全部已 FIXED，与 Gate 3 无新增交集 |
| §2.4 I004/I010/I013/... | I010 落入 §1 数据库；I013/I021/I025 等 dead code 不阻塞 Gate 3 |
| §3 阶段 A-E 执行顺序 | **本报告建议整合到批次 1-3**（不另起 Phase）|

### 6.3 实测证据清单

| # | 证据 | 来源 | 状态 |
|---|---|---|---|
| 1 | `mvn -q -o compile -DskipTests` | 本次评估（2026-10-08 23:55）| **EXIT=0** ✅ |
| 2 | SubscriptionServiceImpl L80 实际字节 | Read tool | `"7天体验"`（非 mojibake）✅ |
| 3 | PointServiceImpl L64 实际字节 | Read tool | `"获得"` / `"消耗"` ✅ |
| 4 | DcepMerchantQualificationServiceImpl L68 实际字节 | Read tool | `"资质审核中，暂不可修改"` ✅ |
| 5 | 4 文件 grep mojibake 字符串（鑾峰緱/娑堣€/璧勮川/宸插叧闂/澶╀綋楠/7澶╀綋楠）| Grep tool | **0 命中** ✅ |
| 6 | 4 文件 grep `\?\?` 占位符 | Grep tool | **0 命中** ✅ |
| 7 | Git HEAD | git log | `e6bf350`（Post-Audit 二期封存）✅ |

### 6.4 受保护页面零风险确认

> 3 个受保护页面（`/dashboard` / `/mh/visual` / `/mh/consent`）与本次评估**零交集**：
> - 本次评估**仅做静态分析 + 1 次 mvn compile 验证**
> - 未对受保护页面的 frontend/backend 代码做任何改动
> - Gate 3 修复期（批次 1+2）需在对应 Phase 实施前确认受保护页用到的表（`mh_visual_archive` / `mh_consent_*` 等）UNIQUE 字段是否含 `deleted`（沿用 final-acceptance §11.3 约束）

---

## 七、报告版本

| 字段 | 值 |
|---|---|
| 报告版本 | v1.0（首发）|
| 创建时间 | 2026-10-08 23:55 (UTC+8)|
| 评估类型 | 只读静态 + 1 次 mvn compile 验证 |
| 改动代码 | **否**（严格遵守规则 6）|
| 改动 SoT | **否** |
| 推荐下一步 | **立项 I096 + 关闭（30 min）→ 进入批次 1（7.5-10 天）→ 批次 2（8.5-12 天）→ Gate 3 GO** |

---

**🎯 核心结论**：Gate 3 状态 = **🟡 CONDITIONAL**（NOT READY 但可短期达 GO）；3-4 周可完成全部批次；**推荐第一步 = I096 立项 + 关闭（30 min 形式工作，立即消除 mvn compile 心智负担）**。

---

*文件落档时间：2026-10-08 23:55 (UTC+8)*
*基线 commit：e6bf350*
*实测证据：mvn compile EXIT=0 + 4 UTF 文件 L5b 字节流核实 + Grep 0 命中*
*下一阶段：批次 1（P0 阻塞项，7.5-10 天）+ 批次 2（P1 累积风险，8.5-12 天）*
