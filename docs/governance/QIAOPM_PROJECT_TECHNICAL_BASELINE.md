\# 《QIAOPM 项目总技术基准与开发验收治理规范》



> \*\*文档状态\*\*：QIAOPM 框架完整版 / 项目事实待调查回填

> \*\*适用对象\*\*：QIAOPM 产品、设计、前端、后端、测试、架构、Cursor/AI 辅助开发

> \*\*重要说明\*\*：本文档为 QIAOPM 项目的\*\*治理规范 + 技术基线模板\*\*。凡是标注 `\[待调查回填]`、`UNVERIFIED`、`待确认` 的内容，均\*\*不得\*\*在未经真实调查前被当作 QIAOPM 项目事实使用。本阶段只建立规范，不修改任何 QIAOPM 业务代码、数据库、API、依赖或环境配置。



\---



\## 0. 文档控制



\### 0.1 文档元信息



| 项目 | 内容 |

|---|---|

| 项目名称 | QIAOPM |

| 文档名称 | QIAOPM 项目总技术基准与开发验收治理规范 |

| 文档路径 | `docs/governance/QIAOPM\_PROJECT\_TECHNICAL\_BASELINE.md` |

| 文档版本 | v0.1（QIAOPM 框架版） |

| 文档状态 | 治理规则已建立，QIAOPM 项目技术基线待调查回填 |

| 维护人 | QIAOPM 首席软件架构师 / 技术文档负责人 / 质量治理负责人 |

| 更新方式 | 任何技术事实变更、验收标准变更、状态定义变更，必须走变更记录 |

| 裁决原则 | 治理规则与状态定义以本规范为准；业务预期以经确认的 PRD/补充需求为准；技术实现以技术设计/ADR/接口文档为准；代码与运行时证据用于证明当前实现事实；冲突按 0.3 裁决 |

| 适用范围 | QIAOPM 全项目需求分析、开发、修复、测试、验收、上线、技术债管理 |

| 不适用范围 | 不替代 QIAOPM PRD 原文、不替代接口文档原文、不替代数据库设计原文 |



\### 0.2 版本与变更记录



| 版本 | 日期 | 变更人 | 变更内容 | 影响范围 | 审核状态 |

|---|---|---|---|---|---|

| v0.1 | `\[填写]` | `\[填写]` | 建立 QIAOPM 治理框架 | 全项目 | 待审阅 |

| v0.2 | 2026-10-22 | QIAOPM 技术文档负责人 | 回填第 2 章，登记 ISSUE-1~8 | 第 2 章 / 第 5 章索引 / 附录 D | 待审阅 |
| v0.3 | 2026-10-22 | QIAOPM 技术文档负责人 | 回填第 4.2 节正向清单初稿（静态），登记 ISSUE-006~014、TD-002~003、Q-005~006 | 第 4.2~4.4 节 / 附录 D / 0.2 版本记录 | 待审阅 |
| v0.4 | 2026-10-22 | QIAOPM 技术文档负责人 | 登记 Q-007 暂停 P-1，ISSUE-006 标记暂停 | 附录 D.1 / 附录 D.3 / 0.2 版本记录 | 待审阅 |
| v0.5 | 2026-10-23 | QIAOPM 技术文档负责人 | ISSUE-011 根因升级为权限体系分裂，新增 TD-005/Q-008 | 附录 D.1 / 附录 D.2 / 附录 D.3 / 0.2 版本记录 | 待审阅 |



\### 0.3 文档优先级与冲突裁决



发生信息冲突时，按以下顺序裁决：



1\. 本总规范中的治理规则与状态定义；

2\. 经确认的最新 QIAOPM PRD / 技术设计 / 补充需求；

3\. QIAOPM 架构决策记录（ADR）；

4\. QIAOPM 现有代码与运行时实际证据；

5\. 人工确认记录。



\*\*注意\*\*：代码事实可以证明“QIAOPM 当前实现是什么”，但不能自动证明“业务应该是什么”。业务预期以经确认的需求文档为准；若文档缺失，必须记录待确认，不得猜测。



\### 0.4 文档使用方式



\- QIAOPM 所有新需求、修复、验收开始前，\*\*先查本规范第 5 章文档索引与第 15/16 章工作流\*\*。

\- 本规范不复制 QIAOPM PRD 全文，只建立\*\*索引、引用和裁决规则\*\*。

\- QIAOPM 项目事实部分必须由调查证据回填，禁止把推测写成事实。

\- 未验证内容一律标注 `UNVERIFIED`，并在附录 D 待确认问题登记。



\### 0.5 QIAOPM 术语表



> 本表用于统一 QIAOPM 菜单名、模块名、角色名、业务状态名、数据范围名称，避免 PRD、代码、菜单、数据库字段各叫一套。以下为模板，必须调查后回填。



| 术语 | QIAOPM 中的定义 | 对应菜单/模块 | 对应代码/表/字段 | 状态 |

|---|---|---|---|---|

| `\[待调查回填]` | `\[待调查回填]` | `\[待调查回填]` | `\[待调查回填]` | UNVERIFIED |



\### 0.6 QIAOPM 编号规则



| 类型 | 编号格式 | 示例 |

|---|---|---|

| 验收记录 | `QIAOPM-ACC-<模块>-<序号>` | `QIAOPM-ACC-ORDER-001` |

| 已知问题 | `QIAOPM-ISSUE-<序号>` | `QIAOPM-ISSUE-012` |

| 技术债 | `QIAOPM-TD-<序号>` | `QIAOPM-TD-003` |

| 待确认问题 | `QIAOPM-Q-<序号>` | `QIAOPM-Q-007` |

| 差异分析报告 | `QIAOPM-DIFF-<模块>-<序号>` | `QIAOPM-DIFF-USER-001` |



\---



\## 1. 文档用途、适用范围、优先级与维护规则



\### 1.1 用途



本规范用于统一 QIAOPM 项目内以下活动的判断标准：



\- 需求是否完整、是否存在冲突；

\- QIAOPM 当前代码是否真实实现了功能；

\- 页面可打开、测试通过是否等于功能完成；

\- 哪些是 MOCK、硬编码、假成功；

\- 修复时允许改什么、禁止改什么；

\- 验收需要哪些证据；

\- 技术债、待确认事项如何留痕；

\- Cursor/AI 辅助分析应如何输出、何时允许改代码。



\### 1.2 适用范围



\- QIAOPM 所有左侧菜单功能模块；

\- QIAOPM 所有非菜单入口、隐藏功能、个人中心、消息通知、导出、审批、第三方回调、定时任务、后台任务；

\- QIAOPM 前端页面、路由、组件、状态管理；

\- QIAOPM 后端接口、服务、任务、权限、数据范围；

\- QIAOPM 数据库实体、表、字段、关联、迁移；

\- QIAOPM 测试、回归、部署前检查；

\- QIAOPM 文档与代码一致性维护。



\### 1.3 维护规则



\- QIAOPM 项目事实变更后，必须在变更记录中登记。

\- 功能状态变化必须同步更新功能矩阵与验收记录。

\- 不得为了让检查通过而删除测试、降低标准或隐藏错误。

\- 不得覆盖、删除或擅自改写现有规范；如需调整，走变更评审。

\- 每季度或每个大版本结束，复审一次本规范与 QIAOPM 项目基线。



\---



\## 2. QIAOPM 真实技术架构与目录结构



> \*\*本章为 QIAOPM 项目事实章节，必须调查后回填。未调查内容一律标记 `\[待调查回填]` / `UNVERIFIED`，不得编造。\*\*



\### 2.1 仓库与目录结构

> 仓库形态：**单仓**（`D:/QiaoPM`，含 `.git/`），非多仓结构；仓库根无 `package.json` / `pom.xml` 聚合层。
> 回填依据：2026-10-22 只读静态调查（目录枚举 + 依赖文件读取），未启动任何服务、未连接任何数据库。
> 状态含义：`CONFIRMED` = 已在文件/配置中找到直接证据；`PARTIAL` = 有证据但不完整；`UNVERIFIED` = 仓库内无法确认。

| 目录/文件 | 用途 | 证据来源 | 状态 |
|---|---|---|---|
| `frontend/meiyetec-web/` | QIAOPM 前端 SPA（Vue 3 + Vite + TypeScript） | `package.json`（`"name": "meiyetec-web"`）、`src/`、`vite.config.ts` | CONFIRMED |
| `frontend/meiyetec-web/src/{api,components,directives,router,stores,utils,views}` | 前端分层：`api` 62 个 `.ts`、`views` 171 个 `.vue`、`stores/permission.ts`、`router/index.ts` | 目录枚举计数 | CONFIRMED |
| `frontend/meiyetec-web/e2e/` | Playwright E2E 测试，21 个 `*.spec.ts` + `auth.setup.ts` + `helpers.ts` | 目录枚举 | CONFIRMED |
| `frontend/meiyetec-web/{Dockerfile,nginx.conf,playwright.config.ts,vitest.config.ts}` | 前端容器镜像 / nginx 反代 / 两套测试配置 | 文件存在 | CONFIRMED |
| `backend/` | QIAOPM 后端服务（Spring Boot），Maven `artifactId=meiyetec-api` | `backend/pom.xml:14-20` | CONFIRMED |
| `backend/src/main/java/com/meiyetec/` | 后端源码 1544 个 `.java`；顶层包 28 个（`controller` 136 个类 / `service` / `entity` / `mapper` / `config` / `interceptor` / `security` / `plugin` / `crm` / `dcep` / `iot` / `payment` / `ecommerce` / `googlemaps` / `booking` / `mkt` / `job` / `task` / `util` 等） | 目录枚举 + 计数 | CONFIRMED |
| `backend/src/main/resources/application.yml` | base 配置（dev 友好默认值） | 文件存在，全文读取 | CONFIRMED |
| `backend/src/main/resources/application-prod.yml` | prod 强约束覆盖（`SPRING_PROFILES_ACTIVE=prod`） | 文件存在，全文读取 | CONFIRMED |
| `backend/src/main/resources/{logback-spring.xml,mapper/}` | 日志配置 + MyBatis XML 映射 | 目录枚举 | CONFIRMED |
| `backend/src/test/java/` | 后端单元/集成测试 123 个 `.java`（含 `test/resources/application.yml`） | 目录枚举 + 计数 | CONFIRMED |
| `backend/src/main/resources/db/sql/` | **数据库脚本目录（手工执行）**：170 个 `.sql`（`init_*` / `migrate_*` / `patch_*` / `seed_*` / `verify_*`），另有 `audit-subset/` 子目录 | 目录枚举 + 计数 | CONFIRMED |
| `backend/src/main/resources/db/migration/V1__init_baseline.sql` | **Flyway 迁移目录**（仅 1 个 V 文件，另有 `V1__init_baseline.sql.tmp`）；`application.yml:29` 设 `baseline-version: 2`，该 V1 被永久跳过 | `application.yml:22,29-30`；目录枚举 | PARTIAL（见 QIAOPM-ISSUE-002） |
| `infra/` | 基础设施：`prometheus/{prometheus.yml,alerts/}`、`grafana/{dashboards,provisioning}`、`alertmanager/`、`systemd/meiyetec-backup.{service,timer}`（共 14 个文件） | 目录枚举 | CONFIRMED |
| `docker-compose.yml` | 本地全栈编排：mysql 8.0 / redis 7 / api / web | 文件存在，全文读取 | CONFIRMED |
| `docker-compose.monitoring.yml` | 监控栈编排 | 文件存在 | PARTIAL（未逐行读取） |
| `scripts/` | 运维脚本：`backup-db.sh`、`restore-db.sh`、`rollback-{app,db-restore,frontend}.sh`、`cleanup-local-backups.sh` | 目录枚举 | CONFIRMED |
| `tests/` | 冒烟/接口测试脚本（PowerShell）：`smoke-full.ps1`、`smoke.ps1`、`frontend-smoke.ps1`、`probe-bizerr.ps1`，含 `REPORT.md` | 目录枚举 | CONFIRMED |
| `.github/workflows/ci.yml` | CI：backend / frontend / integration / e2e / docker-build 5 个 job | 文件存在，全文读取 | CONFIRMED |
| `plugins/demo-hello/` | 插件示例（**独立 Maven 项目**，非后端模块） | `plugins/demo-hello/pom.xml` 存在 | CONFIRMED |
| `docs/governance/` | 治理文档：本文档、`CURRENT_STATE.md`、`feature_inventory.tsv`、`reality_score.{sh,ps1}` | 目录枚举 | CONFIRMED |
| `audit/`、`docs/audit/` | 审计与证据：`audit/{baseline,checklists,diff-audit,evidence,issues,post-audit,reports,runtime,scripts,sql,static-gap}` | 目录枚举 | CONFIRMED |
| `docs/runbook/` | 运行手册：`backup-restore.md`、`monitoring.md`、`rollback.md` | 目录枚举 | CONFIRMED |
| `.env.example` | 环境变量样例模板（7603 字节，全文已读） | 文件存在 | CONFIRMED |
| `.env` | 真实环境变量文件（572 字节） | 文件存在；经人工确认：未进 git 历史、已被 `.gitignore` 忽略、未被跟踪 | CONFIRMED（安全状态已人工确认，原 ISSUE-5 关闭） |
| `backend/` 根目录下 300+ 个 `*.log` / `*.txt` / `compile_*.ps1` / `javac_args*.txt` / `*.sql` 临时产物 | 历史构建日志、编译参数、临时 SQL 残留 | 目录枚举 | CONFIRMED（见 QIAOPM-TD-001） |



> 若 QIAOPM 为多仓结构，必须在本节明确仓库清单及各自职责。
> **当前事实**：单仓，仓库根无聚合构建文件，前后端依赖各自独立声明。



\### 2.2 技术栈

| 层级 | 技术/框架/版本 | 证据来源 | 状态 |
|---|---|---|---|
| 前端框架 | Vue `^3.5.40` + TypeScript `~6.0.2` | `frontend/meiyetec-web/package.json` `dependencies.vue` / `devDependencies.typescript` | CONFIRMED |
| 前端构建 | Vite `^8.2.0` + `@vitejs/plugin-vue` `^6.0.8` + `unplugin-auto-import` `^21.0.0`；类型检查 `vue-tsc ^3.3.8` | `package.json` `devDependencies`；`vite.config.ts:1-4,26-34` | CONFIRMED |
| 状态管理 | Pinia `^2.2.0`，仅 `src/stores/permission.ts` 1 个 store | `package.json` `dependencies.pinia`；`src/stores/` 枚举 | CONFIRMED（覆盖面 PARTIAL：其余状态在组件内管理） |
| UI 组件库 | Element Plus `^2.14.3` + `@element-plus/icons-vue` `^2.3.2`；图表 ECharts `^5.5.1`；进度条 `nprogress ^0.2.0` | `package.json` `dependencies` | CONFIRMED |
| 前端路由 / HTTP | `vue-router ^4.6.4`；`axios ^1.19.0`，统一封装 `src/api/request.ts`（`baseURL:'/api'`），`src/utils/http.ts` 为别名再导出 | `package.json`；`src/api/request.ts:8`；`src/utils/http.ts:1-9` | CONFIRMED |
| 后端语言/框架 | Java 17 + Spring Boot `3.1.5`（parent），`artifactId=meiyetec-api`，打包 jar | `backend/pom.xml:8-11,12-19,23`（`<java.version>17</java.version>`） | CONFIRMED |
| ORM/数据访问 | MyBatis-Plus `3.5.5`（`mybatis-plus-boot-starter`）为主；部分场景直用 `spring-boot-starter-jdbc`（JdbcTemplate） | `pom.xml` `mybatis-plus.version` / `<artifactId>spring-boot-starter-jdbc</artifactId>`；`application.yml:49-62`；`StoreLookupService`（EXT-19 §0.4） | CONFIRMED |
| 数据库 | MySQL 8.0（`mysql-connector-j`），库名 `meiyetec`；逻辑删除字段 `deleted` | `pom.xml` mysql-connector-j；`application.yml:6-11,60-62`；`docker-compose.yml` `image: mysql:8.0` | CONFIRMED |
| 数据库迁移（依赖形式） | Flyway（`flyway-core` + `flyway-mysql`，版本由 Spring Boot 3.1.5 BOM 锁定 9.22.x） | `pom.xml` flyway 依赖块；`application.yml:18-38` | CONFIRMED（**实际生效范围见 QIAOPM-ISSUE-002**） |
| 缓存 | Redis（`spring-boot-starter-data-redis`）；`config/RedisConfig.java` 提供 `RedisTemplate<String,Object>`；`dcep/operator/NotifyReplayProtector.java` 用 `StringRedisTemplate` 做重放保护 | `pom.xml`；`config/RedisConfig.java:14-15`；`dcep/operator/NotifyReplayProtector.java:7,31,35` | CONFIRMED |
| 消息队列 | **代码零 MQ 依赖**：全仓 `backend/src` 无 kafka / rabbitmq / rocketmq 命中。唯一类 MQ 组件为 IoT 用 HiveMQ MQTT Client `1.3.3`（`iot.mqtt.enabled` 默认 false） | `pom.xml` `<artifactId>hivemq-mqtt-client</artifactId>`；`application.yml` `iot.mqtt` 段 | CONFIRMED（MQ=无，见 QIAOPM-ISSUE-003） |
| 认证方式 | **JWT 无状态认证**：`jjwt 0.12.3`，请求头 `Authorization: Bearer <token>`；`JwtInterceptor.preHandle` 校验前缀/过期并取 userId。密码 **BCrypt**（仅引 `spring-security-crypto 6.2.0`，**未**引入完整 Spring Security 过滤器链） | `pom.xml` jjwt + spring-security-crypto 块；`interceptor/JwtInterceptor.java:48-60`；`config/PasswordConfig.java`；前端 `src/api/request.ts:50-52` | CONFIRMED |
| 认证密钥策略 | `jwt.secret` base 配置带 dev 默认值；prod profile 覆盖为 `${JWT_SECRET:}` 空默认，并由 `JwtUtil` 启动期做强度校验（≥32 字节、禁弱值） | `application.yml` `jwt:` 段；`application-prod.yml`；`docs/prod-env-manifest.md` §3 | CONFIRMED |
| 部署方式 | Docker + Docker Compose（本地全栈）；GitHub Actions CI 构建镜像（`push=false`，标签为 8 位短 SHA，目标 `ghcr.io`）；生产为 SRE 蓝绿（自构建镜像 + nginx 切流），前端另走 S3 + CloudFront | `docker-compose.yml`；`.github/workflows/ci.yml` `docker-build` job；`docs/DEPLOY-SOP.md` §2.1-2.3、§3.1 | CONFIRMED（K8s 仅文档提及，仓库无编排文件，见 2.5） |
| 容器运行时 | 后端 `eclipse-temurin:17-jre-alpine` + tini + 非 root（uid 1000）；前端 `node:20-alpine` 构建 → `nginx:1.27-alpine` | `backend/Dockerfile`（FROM/USER/ENTRYPOINT/CMD）；`frontend/meiyetec-web/Dockerfile` | CONFIRMED |
| 可观测性 | `spring-boot-starter-actuator` + `micrometer-registry-prometheus`，暴露 `health,info,prometheus`（base-path `/actuator`），启用 liveness/readiness 探针；prod 覆盖 `show-details: never` | `pom.xml` 监控块；`application.yml` `management:` 段；`application-prod.yml` | CONFIRMED |
| 监控栈 | Prometheus + Grafana（`api-overview.json` / `jvm-db.json`）+ AlertManager | `infra/prometheus/`、`infra/grafana/`、`infra/alertmanager/` | CONFIRMED |
| 扩展/插件运行时 | pf4j `3.10.0`（`plugin.runtime.*` 配置）；业务 SPI `com.meiyetec.plugin.MeiyetecPlugin` | `pom.xml` pf4j 块；`application.yml` `plugin.runtime` 段；`backend/src/main/java/com/meiyetec/plugin/` | CONFIRMED |
| 灰度开关 | LaunchDarkly Java Server SDK `7.16.0`（默认关闭）+ DB 兜底 `feature_flag` + `IN_MEMORY` 评估器 | `pom.xml`；`application.yml` `feature.launchdarkly` 段 | CONFIRMED |
| 国密/支付/财税 | BouncyCastle `1.78.1`（SM2/SM3/SM4）；微信支付 V3 `wechatpay-java 0.2.17`；支付宝 `alipay-sdk-java 4.40.984.ALL` | `pom.xml` 对应依赖块 | CONFIRMED |
| Excel / 邮件 | Apache POI `5.2.5`；`spring-boot-starter-mail` | `pom.xml` 对应依赖块 | CONFIRMED |
| 第三方环境模式 | 财税网关 `fiscal.mode`：base=`mock` / prod=`baiwang`；支付 `app-cn-pay.env-mode`：base=`sandbox` / prod=`production`；DCEP `app.dcep.mode`：base=`dev` / prod=`prod` | `application.yml:234`、`application-prod.yml` | CONFIRMED（`app-cn-pay` 环境变量命名冲突见 QIAOPM-ISSUE-001） |



\### 2.3 模块划分与依赖关系

| 项 | 事实 | 证据来源 | 状态 |
|---|---|---|---|
| 仓库形态 | 单仓，前后端与插件、审计目录同处一个 Git 仓库；无子模块聚合构建 | `D:/QiaoPM/.git` 存在；仓库根无 `pom.xml` / `package.json` | CONFIRMED |
| 后端分层 | `controller` → `service` / `service.impl` → `mapper` → `entity`；横切层 `config` / `interceptor` / `security` / `exception` / `util` | `backend/src/main/java/com/meiyetec/` 28 个顶层包 | CONFIRMED |
| 后端业务子域包 | `booking` `crm` `dcep` `ecommerce` `googlemaps` `iot` `payment` `plugin` `mkt` `ai` `engine` `cn` `event` `featureflag` `log` 等 | 同上目录枚举 | CONFIRMED |
| 前端分层 | `views/`（页面）→ `api/`（接口封装）→ `src/api/request.ts`（axios 实例）→ `src/utils/http.ts`（别名再导出）；`stores/permission.ts` 承载权限态 | `src/` 目录枚举；`utils/http.ts` 注释 | CONFIRMED |
| 前后端契约 | 前端 `baseURL='/api'`；vite 代理与前端 nginx.conf 均剥掉 `/api` 前缀后转发真实后端路径 | `src/api/request.ts:8`；`vite.config.ts:21-24,40-44`；`frontend/meiyetec-web/nginx.conf` | CONFIRMED |
| 插件边界 | 后端经 pf4j 加载 `${plugins-root}/{pluginCode}/{version}/*.jar`；插件实现 `MeiyetecPlugin` SPI；宿主侧用 `isEnabledForTenant` 做平台级 + 租户级双重判定 | `application.yml` `plugin.runtime.plugins-root`；`backend/src/main/java/com/meiyetec/plugin/` | CONFIRMED |
| 前后端发布耦合 | 同一仓、同一 CI job 链（`frontend` → `docker-build`），随同一版本发布 | `.github/workflows/ci.yml` `needs:` 关系 | CONFIRMED |
| 禁止跨层引用规则 | 待治理规范补充后确认；本次调查未发现成文的跨层引用禁令 | 仓库内未检出对应规则文档 | UNVERIFIED |

> **依赖方向（当前实现事实）**：前端 → HTTP(`/api`) → 后端 controller → service → mapper → entity → MySQL/Redis。
> 本节只描述**已实现**的分层事实；跨层禁止规则属治理约定，仍为 UNVERIFIED，需在后续章节补充。



\### 2.4 启动与运行方式

\#### 2.4.1 启动命令

| 环境 | 启动命令 | 依赖服务 | 证据 | 状态 |
|---|---|---|---|---|
| 本地前端 | `cd frontend/meiyetec-web` → `npm install` → `npm run dev`（= `vite`），访问 `http://localhost:5173` | Node 运行时；后端 `localhost:8080`（`/api` 代理目标） | `package.json` `scripts.dev`；`vite.config.ts:40-44`（`server.port:5173` + `/api` 代理）；`README.md`「启动前端」 | CONFIRMED（Node 版本口径不一致，见 QIAOPM-Q-004） |
| 本地后端 | `cd backend` → `mvn spring-boot:run`；或 `mvn -Dmaven.test.skip=true package` → `java -jar target/meiyetec-api-1.0.0.jar`，端口 `8080` | MySQL 8.0（`localhost:3307/meiyetec`）+ Redis `localhost:6379` | `README.md`「启动后端」；`application.yml:6-11,44-48,72`（`server.port: 8080`）；`backend/Dockerfile` CMD `java -jar /app/app.jar` | CONFIRMED |
| 本地全栈（Docker） | `docker compose up -d`；查看日志 `docker compose logs -f api web` | 内置 mysql 8.0（宿主 `3307`→容器 `3306`）、redis 7（`6379`）、api（`8080`）、web（`8088`→容器 `80`） | `docker-compose.yml` 头部注释 + `services` 段 | CONFIRMED（外部卷 `qiaopm_mysql-data` / `qiaopm_redis-data` 标 `external: true`，需预先存在） |
| 本地数据库初始化 | Docker 首次启动自动执行 `./backend/src/main/resources/db/sql:/docker-entrypoint-initdb.d`（按文件名字典序） | MySQL 容器 | `docker-compose.yml` mysql `volumes` 段 | CONFIRMED（init 脚本仅 `init_*` 系列，**不含** `migrate_*`） |
| 数据库迁移（启动期自动） | Flyway 启动期 apply `classpath:db/migration`（`enabled=${FLYWAY_ENABLED:true}`，`baseline-version: 2`） | 同上 | `application.yml:18-38` | PARTIAL —— `baseline-version: 2` 使唯一的 V1 永久被跳过，实际靠 170 个手工 SQL（见 QIAOPM-ISSUE-002） |
| 测试环境（staging） | 未在仓库中找到独立编排或启动脚本 | — | `docker-compose.yml` 仅本地用途；`DEPLOY-SOP.md` §0 提及环境含 main/staging 但无地址/编排 | UNVERIFIED（见 QIAOPM-Q-001） |
| 生产后端 | SRE 自构建短 SHA 镜像 → `docker run` 蓝节点（`--env-file /opt/meiyetec/prod.env`）→ nginx 切流 10%/50%/100% | MySQL RDS + Redis 集群 + nginx + `/opt/meiyetec/prod.env` | `docs/DEPLOY-SOP.md` §2.1-2.3、§1.4；`docs/prod-env-manifest.md` §2-§4 | CONFIRMED（**流程文档已定义，运行态无证据 → UNVERIFIED**） |
| 生产前端 | `npm run build` → 同步至 S3 → CloudFront 失效刷新 | S3 + CloudFront | `docs/DEPLOY-SOP.md` §3.1-3.2 | CONFIRMED（**流程文档已定义，运行态无证据 → UNVERIFIED**） |
| 生产回滚 | `scripts/rollback-app.sh` / `rollback-frontend.sh` / `rollback-db-restore.sh` | 同上 | `scripts/` 目录枚举；`DEPLOY-SOP.md` §2.3、§3.2 | CONFIRMED |
| 生产环境变量注入 | 事实源 `docs/prod-env-manifest.md`；模板 `.env.example`；prod 强约束 `application-prod.yml`（`SPRING_PROFILES_ACTIVE=prod`），全部由外部 ENV 注入不落盘 | 各项 ENV 从外部注入 | `docs/prod-env-manifest.md` §0-§4；`.env.example` 全文；`application-prod.yml` | CONFIRMED |

\#### 2.4.2 测试与运维命令

| 类别 | 命令 | 依赖服务 | 证据 | 状态 |
|---|---|---|---|---|
| 后端测试（CI） | `mvn -B -DskipTests=false test` | CI 起 `mysql:8.0` service，库名 `meiyetec_test` | `.github/workflows/ci.yml` `backend` job | CONFIRMED |
| 后端测试（本地） | `cd backend` → `mvn test` | MySQL + Redis | `README.md`「贡献指南」 | CONFIRMED |
| 前端单元测试 | `npm run test`（= `vitest run`）、`npm run test:watch` | — | `package.json` `scripts.test`；`vitest.config.ts`；`src/utils/format.spec.ts` | CONFIRMED |
| 前端类型检查 | `npm run build:check`（`vue-tsc -b`）；CI 用 `npx vue-tsc --noEmit` | — | `package.json`；`ci.yml` frontend job | CONFIRMED |
| E2E 测试 | `npx playwright install chromium --with-deps` → `npx playwright test`（CI 门禁跑 `new-modules.spec.ts` + `business-flow.spec.ts`，chromium + Mobile Chrome） | 后端 `8080` + 前端 `5173` + MySQL | `README.md`；`package.json` `scripts.e2e*`；`ci.yml` `e2e` job；`frontend/meiyetec-web/playwright.config.ts` | CONFIRMED |
| 接口冒烟（PowerShell） | `pwsh ./tests/smoke-full.ps1`（另有 `smoke.ps1`、`frontend-smoke.ps1`、`probe-bizerr.ps1`） | 后端 `8080` + DB | `tests/` 目录枚举 | PARTIAL（脚本存在已确认；参数与实际通过率未执行验证） |
| CI 集成测试 job | `pwsh ./Run-All-Tests.ps1` | MySQL + 后端 | `.github/workflows/ci.yml:143` | **PARTIAL — 疑似失效**：仓库根不存在 `Run-All-Tests.ps1`，且该 job 混用 bash/pwsh 语法（见 QIAOPM-ISSUE-005） |
| CI 镜像构建 | `docker-build` job（`push=false`，标签为 8 位短 SHA，目标 `ghcr.io`） | — | `.github/workflows/ci.yml` `docker-build` job | CONFIRMED |
| 备份 / 恢复 | `scripts/backup-db.sh` / `scripts/restore-db.sh`；另有 systemd 定时器 `infra/systemd/meiyetec-backup.{service,timer}` | MySQL | `scripts/` 目录枚举；`infra/systemd/` | CONFIRMED |
| 监控栈启动 | `docker compose -f docker-compose.monitoring.yml up -d`（Prometheus + Grafana + AlertManager） | — | `docker-compose.monitoring.yml`；`infra/` | PARTIAL（文件存在已确认，未逐行读取、未实跑） |
| 定时 / 后台任务 | Spring `@Scheduled`：CRM 同步 `crm.sync.tick-interval-ms=300000`、Kiosk 告警 `KIOSK_ALERT_SCAN_MS=60000`；另有 `task/` `job/` 包与消息保留任务 | — | `application.yml` `crm.sync` / `kiosk.alert` 段；`backend/src/main/java/com/meiyetec/task/`、`job/` | CONFIRMED |
| Maven / Node 版本 | 无 `mvnw` wrapper，需系统 Maven；仓库无 `.nvmrc` | — | 仓库根枚举；`README.md:45`（Node.js 18+）vs `ci.yml:73,114,184`（`node-version: '20'`）vs `frontend/meiyetec-web/Dockerfile`（`node:20-alpine`） | PARTIAL（**口径不一致，见 QIAOPM-Q-004**） |



\### 2.5 QIAOPM 环境矩阵



| 环境 | 前端地址 | 后端地址 | 数据库 | 认证方式 | 可用账号/角色 | 是否允许写操作 | 证据 |
|---|---|---|---|---|---|---|---|
| 本地 | `http://localhost:5173`（dev server） | `http://localhost:8080` | `localhost:3307` MySQL 8.0，库名 `meiyetec`（root/root123456，**仅本地开发用**） | JWT（`Authorization: Bearer`），需登录获取 token | `\[待调查回填]`（本地 seed 数据中的账号未逐个核实） | 允许（本地开发） | `vite.config.ts:40-44`；`application.yml:6-11,44-48,72`；`docker-compose.yml` |
| 本地（Docker 全栈） | `http://localhost:8088`（容器 nginx，容器内 80） | `http://localhost:8080`（compose service `api`） | compose 内 mysql 8.0（宿主 `3307`→容器 `3306`）+ redis 7（`6379`） | 同上 | `\[待调查回填]` | 允许（本地开发） | `docker-compose.yml` `services` 段 |
| 测试（staging） | UNVERIFIED | UNVERIFIED | UNVERIFIED | UNVERIFIED | UNVERIFIED | 待确认 | **存在性本身不确定**：`DEPLOY-SOP.md` §0 提及环境含 main/staging，但仓库无 staging 编排、无 profile（`application.yml` 无 staging profile）、无地址清单（见 QIAOPM-Q-001） |
| 生产 | S3 + CloudFront（前端静态托管，nginx 容器**不是**生产前端链路，仅为本地 compose 方案） | SRE 自构建镜像 + nginx 蓝绿切流 10%/50%/100%；环境变量由 `/opt/meiyetec/prod.env` 注入 | UNVERIFIED（真实 DB 主机；`docs/prod-env-manifest.md` 仅有 `prod-rds-host` 等占位） | UNVERIFIED（`application-prod.yml` 强制 `${JWT_SECRET:}` 空默认，需运行时注入） | UNVERIFIED | 禁止未授权写入 | 流程已定义：`docs/DEPLOY-SOP.md` §2.1-2.3（后端蓝绿）、§3.1-3.2（前端 S3+CloudFront）、§1.4；`docs/prod-env-manifest.md` §2-§4 |
| 生产（真实地址） | UNVERIFIED（CloudFront 真实域名） | UNVERIFIED（nginx 真实入口域名） | UNVERIFIED（DB / Redis 真实主机） | UNVERIFIED | UNVERIFIED | 禁止未授权写入 | `.env.example` 全为占位（`prod-rds-host` / `prod-redis-host`）；本次未访问任何服务器或容器（见 QIAOPM-Q-002） |



> 验收记录必须写明"在哪个环境验证"。
> **本次回填仅覆盖静态配置事实**：本地 / Docker / 生产的**流程**已确认；生产的**运行态**（服务是否在跑、当前部署版本、健康状态）与 staging 的**存在性**均无运行证据，按规范保持 UNVERIFIED。
> **注意**：`DEPLOY-SOP.md` §2 为**后端**蓝绿发布章节，§3 为**前端**发布章节（S3 + CloudFront）；DEPLOY-SOP 与 `application-prod.yml` 多处提及 K8s ConfigMap/Secret/探针，但仓库**无任何 k8s yaml / helm chart**，K8s 编排现状为 UNVERIFIED。



> 验收记录必须写明“在哪个环境验证”。



\---



\## 3. QIAOPM 前端、后端、数据库、API、认证授权与权限技术约定



\### 3.1 前端约定



| 约定项 | 规则 | 证据/来源 | 状态 |

|---|---|---|---|

| 路由组织 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 页面/组件命名 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| API 调用封装 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 错误提示 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| Loading/空态 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 表单校验 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 权限控制 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| MOCK 使用 | 禁止将 MOCK 作为 QIAOPM 验收通过依据 | 本规范 | 生效 |



\### 3.2 后端约定



| 约定项 | 规则 | 证据/来源 | 状态 |

|---|---|---|---|

| 分层结构 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 接口返回结构 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 异常处理 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 事务边界 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 参数校验 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 日志/审计 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 定时任务 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |



\### 3.3 数据库约定



| 约定项 | 规则 | 证据/来源 | 状态 |

|---|---|---|---|

| 表命名 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 主键/外键 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 软删除 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 迁移管理 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 敏感字段 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 数据范围 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |



\### 3.4 API 约定



| 约定项 | 规则 | 证据/来源 | 状态 |

|---|---|---|---|

| 路径风格 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 认证头 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 分页参数 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 错误码 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 幂等要求 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 契约变更 | 不得擅自改变已确认 QIAOPM API 契约 | 本规范 | 生效 |



\### 3.5 认证授权与权限



| 项目 | 规则/事实 | 证据 | 状态 |

|---|---|---|---|

| 登录方式 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| Token/Session | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 角色模型 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 菜单权限 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 按钮权限 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 数据范围 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 越权检查 | 每个功能验收必须覆盖 | 本规范 | 生效 |



\### 3.6 QIAOPM 数据库迁移与回滚要求



| 要求项 | 规则 | 证据 | 状态 |

|---|---|---|---|

| 迁移脚本路径 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 迁移命名规范 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 回滚脚本要求 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 生产变更授权人 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |

| 变更前数据备份 | `\[待调查回填]` | `\[证据]` | UNVERIFIED |



\---



\## 4. QIAOPM 左侧菜单、路由、页面、功能模块映射规则



> 本章采用 \*\*正向清单 + 反向清单双向对账\*\*，防止只按 UI 驱动导致漏检 PRD 有但 UI 没有的功能。



\### 4.1 映射层级



```text

一级菜单

&#x20; └── 二级菜单/页面

&#x20;       └── 路由路径

&#x20;             └── 页面组件

&#x20;                   └── 功能点（按钮/表单/列表/弹窗/导入导出/状态流转）

&#x20;                         └── 前端 API 调用

&#x20;                               └── 后端接口

&#x20;                                     └── 数据库实体/表

```



\### 4.2 正向清单（UI → 文档 → 代码）



\#### 4.2.0 本章限制说明



> \*\*本节主表基于前端 fallbackMenus 静态代码，不等于运行态菜单；运行态菜单由 sys\_menu\_permission 表决定，待导出后对账。\*\*
> 关联待确认项：QIAOPM-Q-005。



以下 5 条结构性事实决定了本节全表的**可追溯性边界**，阅读主表前必须先确认：



| 编号 | 结构性事实 | 证据 | 对全表的影响 |

|---|---|---|---|

| S-1 | 菜单由后端 `/auth/me/menu` 动态下发，`Layout.vue` 的 `fallbackMenus` 仅为兜底 | `components/Layout.vue:102`（`fallbackMenus` 定义）、`:321-324`（`dynamicMenus` 取 `store.menus`）、`:326-332`（`displayedMenus`：`store.loaded && dynamicMenus.length > 0` 时用动态菜单，否则回落 `fallbackMenus`）；`controller/RbacController.java:71-75`；`service/RbacService.buildMenuTree` | 左侧菜单**实际内容取决于 `sys_menu_permission` 表运行态数据，本次只读调查无法确认**。下表「菜单层级 / UI 入口」列基于 `fallbackMenus` 静态代码，**非运行态事实** |

| S-2 | 菜单数据源为表 `sys_menu_permission`，菜单与权限同表 | `entity/SysMenuPermission.java`；`mapper/SysMenuPermissionMapper.java` | 菜单与权限同表，**无法静态区分「隐藏菜单」与「无权限菜单」**；`visible` 与权限点实际覆盖需连库确认 |

| S-3 | 84 个 `@RestController` 位于 `service/` 包，不在 `controller/` 包 | `backend/src/main/java/com/meiyetec/service/` 下 `@RestController` 计数（本次复测口径见 QIAOPM-TD-002）；标准位置 `controller/` 包 136 个全部为 `@RestController` | 「追踪到 `controller/` 目录」的做法**不成立**，约 1/3 后端接口无法通过 `controller/` 包定位，第 4 步只能部分完成 |

| S-4 | 前端 62 个 `api/*.ts` 中多数页面绕过 api 层用 http 直连 | `src/api/` 计数 62 个 `.ts`；`views/**` 中直接 `import ... from '@/utils/http'` 或 `@/api/request` 的 `.vue` 文件 51 个 | 「前端 API」列**无法统一按 api 模块归集**，须按实际请求 URL 追踪（见 QIAOPM-TD-003） |

| S-5 | 路由表存在重复注册：`commission/rules` 出现 2 次 | `router/index.ts:58`（`permission: 'commission:rule:list'`，title `提成规则`）与 `router/index.ts:114`（`permission: 'commission:rule:config'`，title `提成规则配置`），二者 `name` 同为 `CommissionRules` | 后注册覆盖前者，`commission:rule:list` 权限点失效，持有该权限但无 `commission:rule:config` 的角色访问 `/commission/rules` 被守卫跳 `/403`（见 QIAOPM-ISSUE-011） |



> \*\*状态口径（本章专用）\*\*：`CONFIRMED` = 静态文件级链路齐全（菜单 → 路由 → 组件 → API → 后端 → 表可追踪）；`PARTIAL` = 链路存在但有断点或语义错配；`MISSING` = 静态证据显示明确缺失；`UNVERIFIED` = 只读范围内无法确认。
> \*\*按第 10.3 节，本节无任何运行证据，全表最高只到 `CONFIRMED`，不得据此标 `VERIFIED`。\*\*
> \*\*「PRD 章节」列统一留空\*\*：QIAOPM PRD 原文未找到，待补（见 QIAOPM-Q-006、第 5.1 章）。



\#### 4.2.1 主表：一级 / 二级菜单 → 路由 → 页面 → API → 后端 → 数据表



**（1）首页**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L1 | 首页 | `/dashboard` | `views/dashboard.vue` | KPI 卡片、跳转 | 有 | `basic.ts` | `MemberController` `/member/stats`；`PosController` `/pos/orders/today`；`BookingController` `/booking/stats` | `crm_member`、`pos_order`、`biz_booking` | *（QIAOPM PRD 原文未找到，待补）* | CONFIRMED（用户已实测通过） |

| — | （KPI 卡片跳转） | `$router.push` 直跳 | — | — | 无独立菜单 | — | 跳转目标 `/crm/care-follow-task`，该页面存在跨页面路径悬空 | — | *（同上）* | PARTIAL |



**（2）基础资料**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L2 | 服务项目 | `/biz/service-item` | `views/biz/service-item.vue` | 列表 / 编辑 | 有 | \*\*无\*\*（`http` 直连，S-4） | `service/BizServiceItemService.java` `/biz/service` ⚠️非 `controller/` 包 | `biz_service_item`、`biz_service_category` | *（同上）* | CONFIRMED |

| L2 | 疗程方案 | `/biz/treatment-plan` | `views/biz/treatment-plan.vue` | 列表 / 编辑 | 有 | `treatment.ts` | `service/BizTreatmentService.java` `/biz/treatment` ⚠️ | `biz_treatment_plan`、`biz_treatment_phase` | *（同上）* | CONFIRMED |

| L2 | 效果报告 | `/biz/treatment-report` | `views/biz/treatment-report.vue` | 列表 / 导出 | 有 | `treatment-report.ts` + `http` 直连 | `controller/BizTreatmentReportController.java` `/biz/treatment/report` | `treatment_report` | *（同上）* | CONFIRMED |

| L2 | 电子价目表卡项套餐 | `/dev/pricelist` | `views/dev/PriceList.vue` | 价目表 / 套餐 | 有 | `sys.ts` | `service/PriceListService.java` `/intel/pricelist` ⚠️ | `price_list`、`price_list_item` | *（同上）* | \*\*PARTIAL\*\*：`views/dev/PriceList.vue:331` 调 `POST /crm/member/list`，后端无此路径（QIAOPM-ISSUE-008） |

| L2 | 护肤方案共享套餐 | `/dev/care-plan` | `views/dev/CarePlan.vue` | 方案维护 | 有 | `sys.ts` | `service/CarePlanService.java` `/care/plan` ⚠️ | `care_plan` | *（同上）* | CONFIRMED |

| L2 | 配方护理建议 | `/care` | `views/care/index.vue` | 配方 / 建议 | 有 | `care.ts` | `service/CareRecipeService.java` `/care`（另 `CareFormulaService` `/care/formula`） | `care_recipe`、`care_recipe_item`、`care_formula` | *（同上）* | CONFIRMED |



**（3）会员 / 储值 / 套餐**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L1 | 会员管理 | `/member` | `views/member/index.vue` | 会员全量操作 | 有 | `basic`+`coupon`+`package`+`point`+`pos`+`treatment` + `http` | `controller/MemberController.java` `/member` | `crm_member`、`crm_member_profile`、`member_credit` | *（同上）* | CONFIRMED |

| L2 | 储值充值 | `/wallet/recharge` | `views/wallet/recharge.vue` | 充值 | 有 | `basic.ts` | `controller/WalletController.java` `/wallet` | `crm_wallet`、`crm_wallet_transaction` | *（同上）* | CONFIRMED |

| L2 | 储值流水 | `/wallet/transactions` | `views/wallet/transactions.vue` | 流水查询 | 有 | `basic.ts` | `controller/WalletController.java` `/wallet` | `crm_wallet_transaction` | *（同上）* | CONFIRMED |

| L2 | 礼品卡 | `/gift-card` | `views/gift-card/index.vue` | 礼品卡 | 有 | `gift-card.ts` | `service/GiftCardService.java` `/gift-card` ⚠️ | `gift_card`、`gift_card_transaction` | *（同上）* | CONFIRMED |

| L2 | 套餐管理 | `/package/list` | `views/package/index.vue` | 套餐 / 次卡 | 有 | `package.ts` | `controller/PackageController.java` `/packages` | `crm_package`、`crm_package_order` | *（同上）* | CONFIRMED |

| L2 | 会员套餐查询 | `/package/member-orders` | `views/package/member-orders.vue` | 套餐订单 | 有 | `package`+`basic` | `controller/PackageController.java` `/packages` | `crm_package_order`、`crm_package_redemption` | *（同上）* | CONFIRMED |

| L2 | \*\*多人共享套餐\*\* | `/package/share` | `views/package/share.vue` | 分享 / 暂停 / 冲正 | 有（菜单 `Layout.vue:158`，路由 `router/index.ts:41`） | `package-share.ts` | `service/P2FeaturesService.java` `/crm/package/share`（**仅** `invite`:52 / `redeem`:105 / `list`:166 / `enable-share`:175） | `crm_package_share`、`shared_package*` | *（同上）* | \*\*MISSING\*\*：前端调 `stats`/`page`/`pause`/`resume`/`revoke`/`quota` 六端点，后端均无 mapping（QIAOPM-ISSUE-006） |



**（4）预约管理**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L2 | 预约日历 | `/booking/calendar` | `views/booking/calendar.vue` | 日历排期 | 有 | `booking.ts` | `controller/BookingController.java` `/booking` | `biz_booking` | *（同上）* | CONFIRMED |

| L2 | 预约列表 | `/booking/list` | `views/booking/list.vue` | 列表 / 改约 | 有 | `booking`+`basic` | `controller/BookingController.java` `/booking` | `biz_booking` | *（同上）* | CONFIRMED |

| L2 | 排班管理 | `/booking/schedule` | `views/booking/schedule.vue` | 排班 | 有 | `booking`+`basic` | `controller/ScheduleController.java` `/schedule` | `biz_employee_schedule` | *（同上）* | CONFIRMED |

| L2 | 预约资源 | `/booking/resource` | `views/booking/resource.vue` | 资源维护 | 有 | \*\*无\*\*（`http` 直连） | `service/BookingResourceService.java` `/biz/booking` ⚠️ | `booking_resource`、`booking_resource_link` | *（同上）* | CONFIRMED |

| L2 | 候补队列 | `/booking/waitlist` | `views/booking/waitlist.vue` | 候补 | 有 | \*\*无\*\*（`http` 直连） | `service/BookingResourceService.java` `/biz/booking` ⚠️ | `booking_waitlist` | *（同上）* | CONFIRMED |

| L2 | 爽约管理 | `/booking/no-show` | `views/booking/no-show.vue` | 爽约规则 / 预测 | 有 | \*\*无\*\*（`http` 直连） | `service/BookingResourceService.java` + `NoShowPredictionService` `/noshow` | `booking_no_show_rule`、`no_show_prediction` | *（同上）* | CONFIRMED |

| L2 | 上门服务调度 | `/lbs` | `views/lbs/index.vue` | 调度 | 有 | `lbs.ts` | `service/LbsDispatchService.java` `/lbs` ⚠️ | `geofence`、`pos_order` | *（同上）* | CONFIRMED |

| L2 | SPA场景模式 | `/spa-scene` | `views/spa-scene/index.vue` | 场景 / 时段 | 有 | \*\*无\*\*（`http` 直连，仅 `/room/list`） | `controller/SpaSceneModeController.java` `/spa-scene`；`controller/RoomController.java` `/room` | `biz_spa_scene_mode`、`biz_spa_scene_room_slot`、`biz_room` | *（同上）* | CONFIRMED |

| L2 | 客流预测与智能排班 | `/dev/forecast` | `views/dev/Forecast.vue` | 预测 | 有 | `sys.ts` | `service/BookingForecastService.java` `/ai/forecast` ⚠️ | `biz_forecast_demand`、`ai_sales_forecast` | *（同上）* | CONFIRMED |



**（5）收银结算**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L2 | 收银台 | `/pos/checkout` | `views/pos/checkout.vue` | 开单 / 收银 | 有 | `basic`+`coupon`+`pos`+`pricing-experiment` | `controller/PosController.java` `/pos` | `pos_order`、`pos_order_item`、`pos_payment` | *（同上）* | CONFIRMED |

| L2 | 订单列表 | `/pos/orders` | `views/pos/orders.vue` | 订单 | 有 | `pos.ts` | `controller/PosController.java` `/pos` | `pos_order`、`pos_transaction` | *（同上）* | CONFIRMED |

| L2 | 收银统计 | `/pos/stats` | `views/pos/stats.vue` | 统计 | 有 | `pos.ts` | `controller/PosController.java` `/pos` | `pos_payment` | *（同上）* | CONFIRMED |

| L2 | 退款单 | `/pos/refund` | `views/pos/refund.vue` | 退款 / 审核 | 有 | `pos.ts` | `controller/PosRefundController.java` `/pos/refund`；`RefundAuditController` `/pos/refund/audit` | `pos_refund`、`pos_refund_item`、`pos_refund_audit_log` | *（同上）* | CONFIRMED |

| L2 | 支付对账 | `/pos/reconciliation` | `views/pos/reconciliation.vue` | 对账 | 有 | `pos.ts` | `controller/ReconciliationController.java` `/pos/reconciliation` | `pos_reconciliation` | *（同上）* | CONFIRMED |

| L2 | 数字人民币 | `/pay/dcep` | `views/pay/dcep.vue` | 数字人民币 | 有 | \*\*无\*\*（`http` 直连） | `service/DcepService.java` `/pay/dcep` ⚠️ | `pay_dcep_order`、`pay_dcep_channel` | *（同上）* | CONFIRMED |



**（6）库存管理**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L2 | 库存列表 | `/stock/list` | `views/stock/list.vue` | 库存 | 有 | `stock.ts` | `controller/StockController.java` `/stock` | `stock_inventory` | *（同上）* | CONFIRMED |

| L2 | 商品耗材 | `/stock/product` | `views/stock/product.vue` | 商品 | 有 | `stock.ts` | `controller/StockController.java` `/stock/products` | `stock_product` | *（同上）* | CONFIRMED |

| L2 | 出入库记录 | `/stock/record` | `views/stock/record.vue` | 出入库 | 有 | `stock`+`pos` | `controller/StockController.java` `/stock` | `stock_record` | *（同上）* | CONFIRMED |

| L2 | 消耗记录 | `/stock/consumption` | `views/stock/consumption.vue` | 消耗 | 有 | `basic`+`pos`+`stock` | `controller/StockController.java` `/stock/consumption` | `pos_order_consumption`、`biz_material_consumption_log` | *（同上）* | CONFIRMED |

| L2 | 盘点管理 | `/stock/check` | `views/stock/check.vue` | 盘点 | 有 | `stock`+`basic` | `controller/StockController.java` `/stock/check` | `stock_check` | *（同上）* | CONFIRMED |

| L2 | 库存预警 | `/stock/alert` | `views/stock/alert.vue` | 预警 | 有 | `stock.ts` | `controller/StockController.java` `/stock/alert` | `stock_inventory`、`wms_expiry_alert` | *（同上）* | CONFIRMED |

| L2 | 库存统计 | `/stock/stats` | `views/stock/stats.vue` | 统计 | 有 | `stock.ts` | `controller/StockController.java` `/stock/stats` | `stock_inventory`、`stock_record` | *（同上）* | CONFIRMED |

| L2 | 跨店调度 | `/stock/cross-store` | `views/stock/crossStore.vue` | 调拨 | 有 | `stock.ts` | `service/CrossStoreInventoryController.java` `/wms/cross-store` ⚠️（`service/` 包下唯一以 `Controller` 命名的类） | `wms_stock_transfer`、`wms_transfer_suggestion`、`wms_warehouse` | *（同上）* | CONFIRMED |

| L2 | 批次追溯 | `/stock/trace/:id` | `views/stock/trace.vue` | 追溯 | 有 | `stock.ts` | `controller/StockController.java` + `service/TraceService` `/trace` ⚠️ | `stock_product`、`cloud_trace_log` | *（同上）* | CONFIRMED |



**（7）员工提成**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L2 | 提成规则 | `/commission/rules` | `views/commission/rules.vue` | 规则 / 版本 | 有 | `commission-rule.ts` + `http` | `controller/CommissionController.java` `/commission` | `commission_rule`、`commission_rule_version` | *（同上）* | \*\*PARTIAL\*\*：路由重复注册，`commission:rule:list` 权限点失效（S-5 / QIAOPM-ISSUE-011） |

| L2 | 提成记录 | `/commission/records` | `views/commission/records.vue` | 记录 | 有 | `commission`+`basic` | `controller/CommissionController.java` `/commission` | `commission_record` | *（同上）* | CONFIRMED |

| L2 | 工资报表 | `/commission/salary` | `views/commission/salary.vue` | 报表 | 有 | `commission`+`basic` | `controller/CommissionController.java` `/commission` | `commission_salary` | *（同上）* | CONFIRMED |

| L2 | 结算管理 | `/commission/settlement` | `views/commission/settlement.vue` | 结算 | 有 | `commission.ts` | `controller/CommissionController.java` `/commission` | `commission_settlement`、`commission_settlement_batch` | *（同上）* | CONFIRMED |

| L2 | \*\*提成结算批次\*\* | `/commission/batch` | `views/commission/batch.vue` | 批次生成 / 导出 | 有（路由 `router/index.ts:94`，`permission: 'commission:batch'`） | \*\*无\*\*（`http` 直连） | 后端\*\*无\*\* `/commission/batch` mapping | 实体 `CommissionSettlementBatch` 存在 | *（同上）* | \*\*MISSING\*\*（QIAOPM-ISSUE-007） |

| L2 | \*\*提成冲正\*\* | `/commission/reversal` | `views/commission/reversal.vue` | 冲正 / 导出 | 有（路由 `router/index.ts:95`，`permission: 'commission:reversal'`） | \*\*无\*\*（`http` 直连） | 后端\*\*无\*\* `/commission/reversal` mapping（全仓 grep 零命中） | 实体 `CommissionReversal` 存在 | *（同上）* | \*\*MISSING\*\*（QIAOPM-ISSUE-007） |



**（8）营销工具**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L2 | 优惠券 | `/market/coupon` | `views/mkt/coupon.vue` | 券管理 | 有 | \*\*无\*\*（`http` 直连） | `controller/MktCouponController.java` `/mkt/coupon`；`CouponController` `/market/coupon` | `mkt_coupon`、`mkt_coupon_batch`、`market_coupon` | *（同上）* | CONFIRMED（\*\*双后端并存，见 QIAOPM-ISSUE-014\*\*） |

| L2 | 积分商城 | `/market/points` | `views/mkt/points.vue` | 积分 | 有 | \*\*无\*\*（`http` 直连） | `controller/MktPointsController.java` `/mkt/points`；`PointController` `/market/point` | `mkt_points_rule`、`mkt_points_log`、`market_point_record`、`point_goods` | *（同上）* | CONFIRMED（\*\*双后端并存，见 QIAOPM-ISSUE-014\*\*） |

| L2 | 营销活动 | `/market/activity` | `views/mkt/promotion.vue` | 活动 | 有 | \*\*无\*\*（`http` 直连） | `controller/MktPromotionController.java` `/mkt/promotion`；`MarketActivityController` `/market/activity` | `mkt_promotion`、`market_activity` | *（同上）* | CONFIRMED（\*\*双后端并存，见 QIAOPM-ISSUE-014\*\*） |

| L2 | 会员日自动化 | `/member-day` | `views/member-day/index.vue` | 会员日 | 有 | `member-day.ts` | `service/MemberDayService.java` `/member-day` ⚠️ | `member_day_config`、`member_day_execution` | *（同上）* | CONFIRMED |

| L2 | 订阅会员制 | `/subscription` | `views/subscription/index.vue` | 订阅 | 有 | `subscription.ts` | `controller/SubscriptionController.java` `/subscription` | `subscription_plan`、`subscription_member`、`subscription_billing` | *（同上）* | CONFIRMED |

| L2 | 营销效果 | `/mkt/effect` | `views/mkt/effect.vue` | 效果分析 | 有 | \*\*无\*\*（`http` 直连） | `controller/MktEffectController.java` `/mkt/effect` | `marketing_touch_log` | *（同上）* | CONFIRMED |

| L2 | 渠道配置 | `/mkt/channel` | `views/mkt/channel.vue` | 渠道 | 有 | \*\*无\*\*（`http` 直连） | `controller/MktChannelController.java` `/mkt/channel` | `mkt_channel_config`、`mkt_channel_template` | *（同上）* | CONFIRMED |

| L2 | 消息模板 | `/mkt/template` | `views/mkt/template.vue` | 模板 | 有 | \*\*无\*\*（`http` 直连） | `controller/MktTemplateController.java` `/mkt/template` | `mkt_channel_template` | *（同上）* | CONFIRMED |

| L2 | 触达任务 | `/mkt/touch-task` | `views/mkt/touch-task.vue` | 触达 | 有 | \*\*无\*\*（`http` 直连） | `controller/MktTouchTaskController.java` `/mkt/task` | `marketing_touch_task`、`marketing_touch_log` | *（同上）* | CONFIRMED |

| L2 | 营销旅程 | `/mkt/journey` | `views/mkt/journey.vue` | 旅程 | 有 | \*\*无\*\*（`http` 直连） | `controller/MktJourneyController.java` `/mkt/journey` | `mkt_journey`、`mkt_journey_instance` | *（同上）* | CONFIRMED |

| L2 | 触达效果 | `/mkt/conversion` | `views/mkt/conversion.vue` | 转化 | 有 | \*\*无\*\*（`http` 直连） | `controller/MktConversionController.java` `/mkt/conversion` | `marketing_touch_conversion` | *（同上）* | CONFIRMED |

| L2 | 动态定价 | `/mkt/pricing` | `views/mkt/pricing.vue` | 定价 | 有 | \*\*无\*\*（`http` 直连） | `service/DynamicPricingService.java` `/mkt/pricing` ⚠️ | `mkt_dynamic_pricing_rule`、`mkt_pricing_log` | *（同上）* | CONFIRMED |

| L2 | 生命周期看板 | `/lifecycle` | `views/lifecycle/index.vue` | 生命周期 | 有 | \*\*无\*\*（`request` 直连） | `controller/MemberLifecycleController.java` `/lifecycle`；`MemberInviteController` `/lifecycle/invite` | `member_lifecycle_log`、`member_lifecycle_rule` | *（同上）* | CONFIRMED |

| L2 | 款式图库 | `/style-gallery` | `views/style-gallery/index.vue` | 图库浏览 | 有（路由 `router/index.ts:155`） | `style-gallery.ts` | `controller/StyleGalleryController.java` base `/gallery` | `style_gallery`、`biz_style_browse_log` | *（同上）* | \*\*PARTIAL\*\*：前端调 `/style/*`，后端图库 base 为 `/gallery`，`/style/*` 归 `StyleLibraryController`（款式**偏好库**）→ 契约漂移（QIAOPM-ISSUE-012） |



**（9）客户运营**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L2 | 客户运营看板 | `/crm/care/overview` | `views/crm/care-overview.vue` | 看板 | 有 | \*\*无\*\*（`http` 直连） | `controller/CrmCareOpsController.java` `/crm/care/ops` | `crm_follow_up_task`、`crm_follow_up_plan`、`crm_complaint_ticket`、`crm_review` | *（同上）* | CONFIRMED |

| L2 | 回访任务 | `/crm/care/follow-task` | `views/crm/care-follow-task.vue` | 回访任务 | 有 | \*\*无\*\*（`http` 直连） | `controller/CrmFollowUpTaskController.java` `/crm/care/follow/task` | `crm_follow_up_task`、`crm_follow_up_touch`、`crm_follow_up_note` | *（同上）* | \*\*PARTIAL\*\*：页面另调 `/crm/care/follow-rule`，后端仅有 `CrmFollowUpPlanController` `/crm/care/follow/plan` → 路径悬空 |

| L2 | 回访计划 | `/crm/care/follow-plan` | `views/crm/care-follow-plan.vue` | 计划 | 有 | \*\*无\*\*（`http` 直连） | `controller/CrmFollowUpPlanController.java` `/crm/care/follow/plan` | `crm_follow_up_plan` | *（同上）* | CONFIRMED |

| L2 | 回访任务规则 | `/crm/care/follow-rule` | `views/crm/care-follow-rule.vue` | 规则 | 有 | \*\*无\*\*（`http` 直连） | 后端\*\*无独立\*\* `/crm/care/follow/rule`；页面实际打 `/crm/care/follow/plan` | `crm_follow_up_trigger_log` | *（同上）* | \*\*PARTIAL\*\*：菜单 / 路由 / 组件三段一致，后端语义错配（复用 plan 接口） |

| L2 | 护理建议 | `/crm/care/suggestion` | `views/crm/care-suggestion.vue` | 建议 | 有 | `care-suggestion.ts` + `http` | `controller/CareSuggestionEngineController.java` `/crm/care/suggestion`；+ `RuleController`/`TemplateController`/`LogController` | `biz_care_suggestion_log`、`biz_care_suggestion_rule`、`biz_care_suggestion_template` | *（同上）* | CONFIRMED |

| L2 | 肤质档案 | `/crm/detection` | `views/crm/detection.vue` | 肤质检测 | 有 | `detection.ts` + `http` | `controller/SkinDetectionController` `/crm/detection/skin`；`HairDetectionController` `/crm/detection/hair`；`NailDetectionController` `/crm/detection/nail`；`DetectionReportController` `/crm/detection/report` | `biz_skin_detection`、`biz_hair_detection`、`biz_nail_detection`、`biz_detection_report` | *（同上）* | CONFIRMED |

| L2 | 款式偏好 | `/crm/style-package` | `views/crm/style-package.vue` | 偏好 | 有 | `style-package.ts` + `http` | `controller/StyleLibraryController.java` `/style`；`MaterialPackageController.java` `/material/package`；`MemberStylePreferenceController` `/style/preference` | `biz_style_library`、`biz_material_package`、`biz_member_style_preference` | *（同上）* | CONFIRMED |

| L2 | 投诉工单 | `/crm/care/complaint` | `views/crm/care-complaint.vue` | 投诉 | 有 | \*\*无\*\*（`http` 直连） | `controller/ComplaintTicketController.java` `/crm/care/complaint`；`service/ComplaintLifecycleService` `/complaint` | `crm_complaint_ticket`、`crm_complaint_log`、`complaint_satisfaction` | *（同上）* | CONFIRMED |

| L2 | 客户评价 | `/crm/care/review` | `views/crm/care-review.vue` | 评价 | 有 | \*\*无\*\*（`http` 直连） | `controller/CrmReviewController.java` `/crm/care/review` | `crm_review`、`crm_review_dimension_config` | *（同上）* | CONFIRMED |

| L2 | 评价模板 | `/crm/care/review-tpl` | `views/crm/care-review-tpl.vue` | 模板 | 有 | \*\*无\*\*（`http` 直连） | `controller/CrmReviewTemplateController.java` `/crm/care/review/tpl` | `crm_review_template` | *（同上）* | CONFIRMED |

| L2 | 跨店规则 | `/crm/cross-rule` | `views/crm/cross-rule.vue` | 规则 | 有 | \*\*无\*\*（`http` 直连） | `controller/CrossStoreController.java` `/crm/cross` | `crm_cross_store_rule` | *（同上）* | CONFIRMED |

| L2 | 跨店核销 | `/crm/cross-record` | `views/crm/cross-record.vue` | 核销 | 有 | \*\*无\*\*（`http` 直连） | `controller/CrossStoreController.java` `/crm/cross` | `crm_cross_store_redemption` | *（同上）* | CONFIRMED |

| L2 | RFM 分群 | `/intel/rfm` | `views/intel/rfm.vue`（shim）→ `views/intel/rfm/index.vue` + 子组件 | 分群 / 批量 | 有（路由 `router/index.ts:146`） | `api/rfm.ts`（被 `SegmentDrawer.vue`、`TagDictTable.vue`、`useRfm*.ts` 等 11 处引用） | `controller/RfmBatchController.java` `/intel/rfm`；`RfmSeedController` `/intel/rfm/seed` | `crm_rfm_snapshot`、`rfm_batch_log`、`crm_tag_dictionary` | *（同上）* | UNVERIFIED：**静态链路齐全**，但引 RFM 的均为 `views/intel/rfm/` 内部子组件，`index.vue` 的 4 个 tab 是否均取到数需运行期确认 |

| L2 | LTV 预测 | `/intel/ltv` | `views/intel/ltv.vue` | 预测 | 有 | \*\*无\*\*（`http` 直连） | `service/LtvService.java` `/intel/ltv` ⚠️ | `crm_ltv_snapshot`、`ai_ltv_model` | *（同上）* | CONFIRMED |



**（10）组织架构 / 数据报表 / 医美合规**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L2 | 总部看板 | `/org/hq` | `views/org/hq.vue` | 总部看板 | 有 | \*\*无\*\*（`http` 直连） | `service/OrgHierarchyService.java` `/org` ⚠️ | `hq_dashboard_snapshot`、`hq_dashboard_widget`、`hq_cross_store_metric` | *（同上）* | CONFIRMED |

| L2 | 品牌管理 | `/org/brand` | `views/org/brand.vue` | 品牌 | 有 | \*\*无\*\*（`http` 直连） | `service/OrgHierarchyService` `/org`；`BrandManagementService` `/brand` ⚠️ | `brand`、`brand_store_rel`、`mkt_brand` | *（同上）* | CONFIRMED |

| L2 | 门店管理 | `/org/store` | `views/org/store.vue` | 门店 | 有 | \*\*无\*\*（`http` 直连） | `service/OrgHierarchyService` `/org`；`controller/StoreController.java` `/store` | `pos_store` | *（同上）* | CONFIRMED |

| L2 | 数据看板 | `/report/dashboard` | `views/report/dashboard.vue` | 看板 | 有 | \*\*无\*\*（`http` 直连） | `service/ReportV2Service.java` `/report/v2` ⚠️ | `report_snapshot`、`report_metric_snapshot` | *（同上）* | CONFIRMED |

| L2 | 营业报表 | `/report/biz` | `views/report/biz.vue` | 报表 | 有 | `report.ts` | `controller/ReportController.java` `/report` | `report_snapshot`、`report_config` | *（同上）* | CONFIRMED |

| L2 | 导出管理 | `/report/export` | `views/report/export.vue` | 导出 | 有 | `report.ts` | `controller/ReportController.java` `/report` | `report_export_task`、`report_export_log` | *（同上）* | CONFIRMED |

| L2 | 导出任务 | `/report/task` | `views/report/task.vue` | 任务 | 有 | \*\*无\*\*（`http` 直连 `/report/task/page`） | `controller/ReportController.java` `/report` | `report_export_task` | *（同上）* | CONFIRMED |

| L2 | 指标口径 | `/report/caliber` | `views/report/caliber.vue` | 口径 | 有 | \*\*无\*\*（`http` 直连） | `controller/ReportController.java` `/report` | `report_metric_caliber`、`metric_definition` | *（同上）* | CONFIRMED |

| L2 | 视觉档案 | `/mh/visual` | `views/mh/visual.vue` | 视觉档案 | 有 | \*\*无\*\*（`http` 直连） | `service/VisualArchiveService.java` `/mh/visual` ⚠️ | `mh_visual_archive`、`mh_visual_compare`、`mh_image_consent` | *（同上）* | CONFIRMED（用户已实测通过） |

| L2 | 合规记录 | `/mh/consent` | `views/mh/consent.vue` | 合规签署 | 有 | \*\*无\*\*（`http` 直连） | `service/ConsentAndDrugDeviceService.java` `/mh` ⚠️；`controller/MhConsentH5Controller` `/mh/consent` | `mh_consent_signature`、`mh_consent_template`、`mh_drug_device` | *（同上）* | CONFIRMED（用户已实测通过） |



**（11）考勤 / 技师 / 耗材 / 设备**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L2 | 考勤打卡 | `/attendance/index` | `views/attendance/index.vue` | 打卡 | 有 | `attendance.ts` | `controller/AttendanceController.java` `/attendance` | `attendance_record` | *（同上）* | CONFIRMED |

| L2 | 考勤记录 | `/attendance/records` | `views/attendance/records.vue` | 记录 | 有 | `attendance.ts` | `controller/AttendanceController.java` `/attendance` | `attendance_record` | *（同上）* | CONFIRMED |

| L2 | 请假管理 | `/attendance/leave` | `views/attendance/leave.vue` | 请假 | 有 | `attendance.ts` | `controller/LeaveRequestController.java` `/attendance/leave` | `attendance_leave`（实体未在 `@TableName` 提取结果中检出） | *（同上）* | UNVERIFIED：表映射未能静态确认 |

| L2 | 考勤统计 | `/attendance/statistics` | `views/attendance/statistics.vue` | 统计 | 有 | `attendance.ts` | `controller/AttendanceController.java` `/attendance` | `attendance_record` | *（同上）* | CONFIRMED |

| L2 | 考勤规则 | `/attendance/config` | `views/attendance/config.vue` | 规则 | 有 | `attendance.ts` | `controller/AttendanceConfigController.java` `/attendance/config` | `attendance_config` | *（同上）* | CONFIRMED |

| L2 | 技师等级 | `/technician/level` | `views/technician/level.vue` | 等级 | 有 | `technician-level.ts` | `controller/TechnicianLevelController.java` `/technician/level` | `biz_technician_level` | *（同上）* | CONFIRMED |

| L2 | 等级权益 | `/technician/benefit` | `views/technician/benefit.vue` | 权益 | 有 | `technician-level.ts` | `controller/LevelBenefitController.java` `/technician/level/benefit` | `biz_technician_level_benefit`（实体未检出） | *（同上）* | PARTIAL |

| L2 | 等级评定 | `/technician/evaluation` | `views/technician/evaluation.vue` | 评定 | 有 | `technician-level.ts` | `controller/LevelEvaluationController.java` `/technician/level/evaluation` | `biz_technician_level_evaluation`（实体未检出） | *（同上）* | PARTIAL |

| L2 | 耗材绑定 | `/material/binding` | `views/material/binding.vue` | 绑定 | 有 | `material-binding.ts` + `http` | `controller/ServiceMaterialController.java` `/material/binding` | `biz_service_material`、`biz_material_substitute` | *（同上）* | \*\*PARTIAL\*\*：`views/material/binding.vue:543` 调 `/service/list`，后端无此路径（QIAOPM-ISSUE-009） |

| L2 | 消耗统计 | `/material/statistics` | `views/material/statistics.vue` | 统计 | 有 | `material-binding.ts` + `http` | `controller/MaterialConsumptionLogController.java` `/material/consumption` | `biz_material_consumption_log` | *（同上）* | CONFIRMED |

| L2 | 效期追踪 | `/material/expiry` | `views/material/expiry-tracking.vue` | 效期 | 有 | `expiry-tracking.ts` + `http` | `controller/ExpiryController.java` `/expiry`；+ `AlertRule`/`AlertLog`/`Scan`/`Handle` | `biz_expiry_alert_rule`、`biz_expiry_alert_log`、`biz_expiry_handle_log` | *（同上）* | CONFIRMED |

| L2 | 项目耗材 | `/material/service-material` | `views/material/service-material.vue` | 项目耗材规则 | 有 | `service-material.ts` + `http` | `controller/ServiceMaterialRuleController.java` `/stock/service-material/rule`；`ServiceMaterialDeductController` `/stock/service-material/deduct` | `biz_service_material_rule`、`biz_service_material_deduct_log` | *（同上）* | \*\*PARTIAL\*\*：`views/material/service-material.vue:364` 调 `/service/list`，后端无此路径（QIAOPM-ISSUE-009） |

| L2 | 双资源排程 | `/resource/schedule` | `views/resource/resource-schedule.vue` | 排程 | 有 | `resource-schedule.ts` + `http` | `controller/ResourceScheduleController.java` `/resource/schedule`；+ `Conflict`/`MatchRule`/`Utilization` | `biz_resource_conflict_log`、`biz_resource_match_rule`、`biz_resource_utilization` | *（同上）* | \*\*PARTIAL\*\*：`views/resource/resource-schedule.vue:386` 调 `/service/list`，后端无此路径（QIAOPM-ISSUE-009） |

| L2 | 海外支付 | `/payment/overseas` | `views/payment/overseas-payment.vue` | 海外支付 | 有 | `overseas-payment.ts` | `controller/OverseasPaymentController.java` `/overseas-payment/payment`；`Gateway`；`PaymentRouteRule`；`PaymentWebhookController` | `biz_overseas_payment`、`biz_overseas_payment_gateway`、`biz_payment_route_rule`、`biz_payment_webhook_log` | *（同上）* | CONFIRMED |

| L2 | IoT设备 | `/iot/management` | `views/iot/iot-management.vue` | 设备 | 有 | `iot.ts` + `http` | `controller/IotDeviceLinkController` `/iot/device/link`；`IotDeviceDataController` `/iot/data`；`IotTriggerRuleController` `/iot/rule`；`IotDeviceAlertController` `/iot/alert` | `iot_device`、`iot_device_link`、`biz_iot_device_data`、`biz_iot_device_alert` | *（同上）* | CONFIRMED |

| L2 | 数字人民币2.0 | `/payment/dcep-advanced` | `views/payment/dcep-advanced.vue` | DCEP 进阶 | 有 | `dcep-advanced.ts` | `controller/DcepSmartContractController` `/dcep/contract`；`DcepHardwareWalletController` `/dcep/wallet`；`DcepOfflineTransactionController` `/dcep/offline` | `biz_dcep_smart_contract`、`biz_dcep_hardware_wallet`、`biz_dcep_offline_transaction` | *（同上）* | CONFIRMED |



**（12）系统设置**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L2 | 店铺配置 | `/sys/setting` | `views/system/setting.vue` | 配置 | 有 | `sys.ts` | `controller/SysConfigController.java` `/sys/config` | `sys_config` | *（同上）* | CONFIRMED |

| L2 | 租户管理 | `/sys/tenant` | `views/system/tenant.vue` | 租户 | 有 | `sys.ts` | `controller/SysTenantController.java` `/sys/tenant` | `sys_tenant`、`sys_tenant_config` | *（同上）* | CONFIRMED |

| L2 | \*\*员工管理\*\* | \*\*无路由\*\* | \*\*无独立组件\*\*（`views/setting/employee.vue` 仅被 `/setting` 作 tab 引用） | 员工 | 菜单 `Layout.vue:281` `{ path: 'employee', title: '员工管理' }` → 解析为 `/sys/employee` | — | `controller/EmployeeController.java` `/employee` + `/employee/list` + `/employee/page`（**后端齐备**） | `pos_employee` | *（同上）* | \*\*MISSING\*\*：153+ 路由中无 `sys/employee`，菜单点击无落地页（QIAOPM-ISSUE-010） |

| L2 | 操作日志 | `/sys/log` | `views/system/log.vue` | 日志 | 有 | `sys`+`request` | `controller/SysLogController.java` `/sys/log` | `sys_log` | *（同上）* | CONFIRMED |

| L2 | 登录日志 | `/sys/login-log` | `views/system/login-log.vue` | 登录日志 | 有 | `sys`+`request` | `controller/SysLoginLogController.java` `/sys/login-log` | `sys_login_log` | *（同上）* | CONFIRMED |

| L2 | 支付配置 | `/sys/payment` | `views/system/payment.vue` | 支付配置 | 有 | `sys.ts` | `service/PaymentConfigService.java` `/payment` ⚠️；`controller/DcepConfigController` `/sys/dcep` | `payment_config`、`payment_channel`、`dcep_config` | *（同上）* | CONFIRMED |

| L2 | 商户资质材料 | `/sys/qualification` | `views/system/qualification.vue` | 资质 | 有 | `sys.ts` | `controller/DcepMerchantQualificationController.java` `/sys/dcep/qualification` | `dcep_merchant_qualification`、`dcep_qualification_attachment` | *（同上）* | CONFIRMED |



**（13）研发工具**



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| L2 | 灰度发布控制台 | `/dev/gray-release` | `views/dev/GrayRelease.vue` | 灰度 | 有 | `sys.ts` | `controller/GrayReleaseController.java` `/gray` | `feature_flag`、`gray_experiment` | *（同上）* | CONFIRMED |

| L2 | LTV流失预测 | `/dev/ltv-training` | `views/dev/LtvTraining.vue` | 训练 | 有 | `sys.ts` | `service/LtvTrainingService.java` `/intel/ltv/training`；`ChurnTrainingService` `/intel/churn/training` | `ai_ltv_model`、`ai_churn_model`、`churn_training_run_log` | *（同上）* | CONFIRMED |

| L2 | 数据中台 | `/dev/datahub` | `views/dev/DataHub.vue` | 元数据 | 有 | `sys.ts` | `service/DataHubService.java` `/datahub` ⚠️ | `meta_table`、`meta_column`、`meta_lineage`、`meta_value` | *（同上）* | CONFIRMED |

| L2 | 配方护理会员日 | `/dev/care-memberday` | `views/dev/CareMemberDay.vue` | 会员日 | 有 | `sys.ts` | `service/MemberDayService.java` `/member-day` ⚠️ | `member_day_config`、`member_day_execution` | *（同上）* | CONFIRMED |

| L2 | AI 视觉识别 | `/ai-vision` | `views/ai-vision/index.vue` | 视觉识别 | 有 | `ai-vision.ts` | `service/AIVisionService.java` `/ai/vision` ⚠️ | `cv_model`、`cv_recognition_log` | *（同上）* | CONFIRMED |

| L2 | LTV/Churn 分析 | `/ltv-churn` | `views/ltv-churn/index.vue` | 分析 | 有 | `ltv-churn.ts` | `controller/LtvChurnController.java` `/ltv-churn` | `crm_churn_prediction`、`crm_churn_alert` | *（同上）* | CONFIRMED |

| L2 | IoT 设备管理 | `/iot` | `views/iot/index.vue` | 设备 | 有 | `iot.ts` | `service/IoTDeviceService.java` `/iot/device` ⚠️ | `iot_device`、`iot_command`、`iot_device_event` | *（同上）* | CONFIRMED |

| L2 | 自助 Kiosk | `/kiosk` | `views/kiosk/index.vue` | 自助 | 有 | `kiosk.ts` | `service/KioskService.java` `/kiosk` ⚠️；`service/P1SuiteService` `/p1-suite` | `pos_device`、`kiosk_checkin` | *（同上）* | CONFIRMED |

| L2 | 并发症 SOP | `/complication` | `views/complication/index.vue` | SOP | 有 | `complication.ts` | `service/ComplicationService.java` `/complication` ⚠️ | `complication_event`、`complication_sop` | *（同上）* | CONFIRMED |

| L2 | Cohort 队列 | `/cohort` | `views/cohort/index.vue` | 队列 | 有 | `cohort.ts` | `controller/CohortController.java` `/cohort`；`service/P1SuiteService` | `cohort_definition`、`cohort_result` | *（同上）* | CONFIRMED |

| L2 | GDPR 合规 | `/gdpr` | `views/gdpr/index.vue` | 合规 | 有 | `gdpr.ts` + `http` | `service/GdprService.java` `/gdpr` ⚠️ | `gdpr_consent`、`gdpr_data_request`、`gdpr_audit_log` | *（同上）* | CONFIRMED |

| L2 | HIPAA 加固 | `/hipaa` | `views/hipaa/index.vue` | 加固 | 有 | `hipaa.ts` | `service/HipaaService.java` `/hipaa` ⚠️ | `hipaa_encryption_key`、`hipaa_phi_access` | *（同上）* | CONFIRMED |

| L2 | 海外 POS | `/pos-device` | `views/pos-device/index.vue` | 设备 | 有 | `pos-device.ts` | `service/PosDeviceService.java` `/pos-device` ⚠️ | `pos_device` | *（同上）* | CONFIRMED |

| L2 | 插件市场 | `/plugin` | `views/plugin/index.vue` | 插件 | 有 | `plugin.ts` | `service/PluginService.java` `/plugin` ⚠️；`controller/PluginRuntimeController.java` `/plugin/runtime` | `plugin_market`、`plugin_install`、`plugin_review`、`plugin_runtime_state` | *（同上）* | CONFIRMED（EXT-23 SoT） |

| L2 | 财税直连 | `/fiscal` | `views/fiscal/index.vue` | 财税 | 有 | `fiscal.ts` | `service/FiscalService.java` `/fiscal` ⚠️；`controller/FiscalConfigController.java` `/fiscal/config` | `fiscal_config`、`fiscal_invoice` | *（同上）* | CONFIRMED |

| L2 | CRM 集成 | `/crm-integration` | `views/crm-integration/index.vue` | CRM | 有 | `crm-integration.ts` | `service/CrmIntegrationService.java` `/crm-integration` ⚠️ | `crm_integration`、`crm_contact_map`、`crm_sync_log` | *（同上）* | CONFIRMED（EXT-24 SoT） |

| L2 | 电商集成 | `/ecommerce` | `views/ecommerce/index.vue` | 电商 | 有 | `ecommerce.ts` | `service/EcommerceService.java` `/ecommerce` ⚠️；`service/P3EcosystemSuiteService` | `ecommerce_platform`、`ecommerce_order`、`ecommerce_product` | *（同上）* | CONFIRMED（EXT-22 SoT） |

| L2 | 国际支付 | `/intl-payment` | `views/intl-payment/index.vue` | 国际支付 | 有 | `intl-payment.ts` | `service/IntlPaymentService.java` `/intl-payment` ⚠️ | `payment_intent`、`payment_gateway` | *（同上）* | CONFIRMED |

| L2 | 国际预约 | `/intl-booking` | `views/intl-booking/index.vue` | 国际预约 | 有 | `intl-booking.ts` | `service/IntlBookingService.java` `/intl-booking` ⚠️ | `overseas_booking`、`platform_booking` | *（同上）* | CONFIRMED（EXT-19 SoT） |

| L2 | AI 智能排班 | `/ai-scheduler` | `views/ai-scheduler/index.vue` | 排班 | 有 | `ai-scheduler.ts` | `service/AiSchedulerService.java` `/ai-scheduler` ⚠️ | `ai_schedule_plan`、`schedule_recommendation` | *（同上）* | CONFIRMED |

| L2 | 资金风控 | `/risk-control` | `views/risk-control/index.vue` | 风控 | 有 | `risk-control.ts` | `service/RiskControlService.java` `/risk-control` ⚠️ | `risk_event`、`risk_rule` | *（同上）* | CONFIRMED |

| L2 | Google Maps | `/google-maps` | `views/google-maps/index.vue` | 地图 | 有 | `google-maps.ts` | `service/GoogleMapsService.java` `/google-maps` ⚠️ | `google_maps_config`、`google_maps_geocode_log` | *（同上）* | CONFIRMED（EXT-22 SoT） |

| L2 | P1套件合集 | `/dev/p1-suite` | `views/dev/P1Suite.vue` | 套件 | 有 | `sys.ts` | `service/P1SuiteService.java` `/p1-suite` ⚠️ | `perf_load_test_result` 等 | *（同上）* | CONFIRMED |

| L2 | P2套件合集 | `/dev/p2-suite` | `views/dev/P2Suite.vue` | 套件 | 有 | `sys.ts` | `service/P2ComplianceSuiteService.java` `/p2-suite` ⚠️ | `gdpr_consent`、`hipaa_phi_access` | *（同上）* | CONFIRMED |

| L2 | P3套件合集 | `/dev/p3-suite` | `views/dev/P3Suite.vue` | 套件 | 有 | `sys.ts` | `service/P3EcosystemSuiteService.java` `/p3-suite` ⚠️ | `ecommerce_*`、`platform_*` | *（同上）* | CONFIRMED |

| L2 | 补齐套件 | `/dev/gap-suite` | `views/dev/GapSuite.vue` | 套件 | 有 | `sys.ts` | `service/P1RemainingService.java`（无 `@RequestMapping` base）⚠️ | 多表 | *（同上）* | UNVERIFIED：控制器无 base path，无法静态归属 |



### 4.3 隐藏路由与非菜单入口



> 本节登记**在 `router/index.ts` 中注册、但不在 `fallbackMenus` 任何一级/二级菜单中**的路由，以及**有页面但无路由**的孤儿入口。
> 按第 16 章要求，非菜单入口必须单独登记，不得因「左侧菜单没有」而漏检。
> 标签口径：`NON_MENU_ENTRY` = 有路由但无菜单入口；`CODE_WITHOUT_UI` = 有代码无用户可达入口；`已计入菜单` = 该路由已在上节主表登记，不重复计列。



| 编号 | 路由 | 名称 / 标题 | 页面组件 | 前端 API | 后端接口 | 标签 | 说明 |

|---|---|---|---|---|---|---|---|

| H-1 | `/login` | Login / 登录 | `views/login.vue` | `auth.ts` + `request` | `controller/AuthController.java` `/auth/login` | `NON_MENU_ENTRY` | 认证入口，免登录 |

| H-2 | `/403` | Forbidden / 无权限 | `views/403.vue` | 无 | 无 | `NON_MENU_ENTRY` | 错误页；S-5 权限点失效时用户最终落点 |

| H-3 | `/mh-consent-h5/:token` | MhConsentH5 / 知情同意签署 | `views/mh/consent-h5.vue` | \*\*无\*\*（`http` 直连） | `controller/MhConsentH5Controller.java` `/mh/consent` | `NON_MENU_ENTRY` | \*\*外部 H5 入口\*\*，凭 URL token 免登录进入，属高风险外部入口需单独验收 |

| H-4 | `/setting` | Setting / 系统设置 | `views/setting/index.vue`（组合 4 个 tab） | 经 `store`/`category`/`level` 子组件 | `CategoryController` `/category`、`MemberLevelController` `/member-level`、`EmployeeController` `/employee`、`StoreController` `/store` | `NON_MENU_ENTRY` | 与菜单 `/sys/setting` 标题相同、职责不同，用户易混淆 |

| H-5 | `/package` | PackageList / 套餐次卡 | `views/package/index.vue` | `package.ts` | `controller/PackageController.java` `/packages` | `NON_MENU_ENTRY` | 与菜单 `/package/list` 同组件重复路由 |

| H-6 | `/booking/resource` | — | `views/booking/resource.vue` | — | — | `已计入菜单` | 已在 4.2.1（4）登记 |

| H-7 | `/report/kpi` | ReportKpiOverview / 高级KPI看板 | `views/report/kpi-overview.vue` | `report.ts` | `controller/ReportController.java` `/report` | `NON_MENU_ENTRY` | 有完整页面无菜单入口，只能手敲 URL |

| H-8 | `/commission/rule-list` | CommissionRuleList / 提成规则列表 | `views/commission/rule-list.vue` | `commission-rule.ts` + `http` | `controller/CommissionController.java` `/commission` | `NON_MENU_ENTRY` | 同上 |

| H-9 | `/commission/simulation` | CommissionSimulation / 提成模拟计算 | `views/commission/simulation.vue` | `commission-rule.ts` + `http` | `controller/CommissionSimulationController.java` `/commission/simulation` | `NON_MENU_ENTRY` | 同上 |

| H-10 | `/mkt/birthday` | MktBirthday / 生日提醒 | `views/mkt/birthday.vue` | `birthday`+`basic`+`coupon` | `service/BirthdayReminderService.java` `/birthday` ⚠️ | `NON_MENU_ENTRY` | 同上 |

| H-11 | `/mkt/promotion` | MktPromotion / 营销活动 | `views/mkt/promotion.vue` | `http` 直连 | `controller/MktPromotionController.java` `/mkt/promotion` | `NON_MENU_ENTRY` | 与菜单 `/market/activity` \*\*同组件不同 base\*\*（QIAOPM-ISSUE-014） |

| H-12 | `/mkt/coupon` | MktCoupon / 优惠券 | `views/mkt/coupon.vue` | `http` 直连 | `controller/MktCouponController.java` `/mkt/coupon` | `NON_MENU_ENTRY` | 与菜单 `/market/coupon` 同组件不同 base（QIAOPM-ISSUE-014） |

| H-13 | `/mkt/points` | MktPoints / 积分规则 | `views/mkt/points.vue` | `http` 直连 | `controller/MktPointsController.java` `/mkt/points` | `NON_MENU_ENTRY` | 与菜单 `/market/points` 同组件不同 base（QIAOPM-ISSUE-014） |

| H-14 | `/intel/price-sensitivity` | IntelPriceSensitivity / 价格敏感度 | `views/intel/price-sensitivity.vue` | `price-sensitivity.ts` | `controller/PriceSensitivityController.java` `/intel/price-sensitivity` | `NON_MENU_ENTRY` | 有完整页面无菜单入口 |

| H-15 | `/m09/invite` | M09Invite / 裂变邀请 | `views/m09-invite/index.vue` | `invite.ts` | `controller/InviteController.java` `/m09/invite/*` | `NON_MENU_ENTRY` | 同上 |

| H-16 | `/m12/dispatch` | M12Dispatch / 业务策略下发 | `views/m12/dispatch/index.vue` | `dispatch.ts` | `controller/BusinessPolicyDispatchController.java` `/m12/dispatch` | `NON_MENU_ENTRY` | 同上 |

| H-17 | `/fiscal/config` | FiscalConfig / 财税渠道配置 | `views/fiscal/config.vue` | `fiscal.ts` | `controller/FiscalConfigController.java` `/fiscal/config` | `NON_MENU_ENTRY` | 配置型子页，无菜单直达 |

| H-18 | `/fiscal/list` | FiscalList / 发票列表 | `views/fiscal/list.vue` | `fiscal.ts` | `service/FiscalService.java` `/fiscal` ⚠️ | `NON_MENU_ENTRY` | 同上 |

| H-19 | `/style-gallery/quick-order` | StyleGalleryQuickOrder / 快速下单 | `views/style-gallery/quick-order.vue` | `style-gallery`+`basic`+`request` | `controller/StyleGalleryController.java` ⚠️ | `NON_MENU_ENTRY` | 二级下钻页；受 QIAOPM-ISSUE-012 base path 漂移影响 |

| H-20 | `/blockchain/trace` | BlockchainTrace / 区块链溯源 | `views/blockchain/trace.vue` | `blockchain.ts` | `controller/BlockchainController.java` `/blockchain` | `NON_MENU_ENTRY` | 有完整页面无菜单入口 |

| H-21 | `/blockchain/monitor` | BlockchainMonitor / 区块链监控大屏 | `views/blockchain/monitor.vue` | `blockchain.ts` | `controller/BlockchainController.java` `/blockchain` | `NON_MENU_ENTRY` | 同上 |

| H-22 | `/sys/role` | SysRole / 角色管理 | `views/system/role.vue` | `rbac.ts` | `controller/RbacController.java` `/sys/role` | `NON_MENU_ENTRY` | 菜单「系统设置」无「角色管理」项，但属 RBAC 核心页，缺入口影响权限验收 |

| H-23 | `/sys/qualification` | — | `views/system/qualification.vue` | — | — | `已计入菜单` | 已在 4.2.1（12）登记 |

| H-24 | `/mkt/experiment` | \*\*无路由\*\* | `views/mkt/experiment.vue` | `pricing-experiment.ts` | `controller/PricingExperimentController.java` `/mkt/pricing/experiment` | `CODE_WITHOUT_UI` | 组件 + API + 后端三端齐全，但 `router/index.ts` 未注册该路径（已复测确认零命中）→ 用户无法到达 |



### 4.4 未引用 API 模块



> 本节登记**在 `src/api/` 存在、但未被任何 `.vue` 视图引用**的接口模块。此类模块属潜在孤儿：后端接口可能已实现却无前端入口，是第 15 章 Step 2「反向清单」的重要线索来源。
> 判定方式：对 `src/` 全量检索模块名的 import 语句（含 `@/api/x` 与相对路径两种写法）。



| API 模块 | 大小 | 端点覆盖 | 引用方 | 标签 | 状态与说明 |

|---|---|---|---|---|---|

| `api/rfm.ts` | 8892 B | `/intel/rfm/*`（14 个端点） | `views/intel/rfm/` 下 11 处（`SegmentDrawer.vue:93`、`TagDictTable.vue:185`、`useRfmBatch.ts:2`、`useRfmOverview.ts:2`、`useRfmCompute.ts:2`、`useRfmDict.ts:2` 等） | — | UNVERIFIED：**已被引用**，非孤儿；主表 `/intel/rfm` 行亦标 UNVERIFIED（子组件链路齐全，tab 取数需运行期确认） |

| `api/gray.ts` | 3396 B | `/gray/*`（3 个端点） | \*\*0 个视图\*\*（`src/` 全量检索 `api/gray` 零命中） | `CODE_WITHOUT_UI` | UNVERIFIED：疑似与 `sys.ts` 覆盖同类 `/gray/*` 路径；需确认是重复封装还是被 `sys.ts` 取代后的残留 |

| `api/bnpl-huabei.ts` | 2026 B | `/bnpl/*`、`/huabei/*`（8 个端点） | \*\*0 个视图\*\*（`src/` 全量检索 `api/bnpl-huabei` 零命中） | `CODE_WITHOUT_UI` | UNVERIFIED：后端 `BnplController`/`HuabeiController` 存在，但无菜单、无路由、无组件引用 → 三端缺失中的「UI 端」 |



> \*\*反向清单（4.5）当前仍为模板状态\*\*：QIAOPM PRD 原文未找到（QIAOPM-Q-006），反向清单的「PRD 章节 / 文档描述的功能点」两列在 PRD 补齐前**无法回填**，不得以本节正向清单反推文档预期。



\### 4.5 反向清单模板（文档 → UI → 代码）



| PRD 章节 | 文档描述的功能点 | 期望菜单/入口 | 实际 UI 是否存在 | 前端 API | 后端接口 | 数据表 | 差异类型 | 状态 |

|---|---|---|---|---|---|---|---|---|

| `\[待回填]` | `\[待回填]` | `\[待回填]` | 是/否/待确认 | `\[待回填]` | `\[待回填]` | `\[待回填]` | DOC\_ONLY / UI\_ONLY / CODE\_ONLY / 一致 | UNVERIFIED |



\### 4.6 映射规则



\- QIAOPM 每个菜单必须能找到对应路由、页面组件和至少一个功能点。

\- 每个功能点必须能追踪到前端 API、后端接口和数据表；无法追踪的标记 `MISSING` 或 `UNVERIFIED`。

\- 页面能打开不等于功能完整；必须完成第 8 章端到端验证。

\- 非菜单入口必须单独登记（见第 16 章），不得因“左侧菜单没有”而漏检。

\- \*\*反向清单必须与正向清单逐项对账\*\*：正向清单里出现但反向清单没有来源的，标 `CODE\_ONLY`；反向清单里出现但正向清单没有 UI 的，标 `DOC\_ONLY` 或 `MISSING`。

\- 双向清单对账完成前，该模块整体状态不得标 `VERIFIED`。



\---



\## 5. QIAOPM PRD、技术设计、补充需求、架构决策与代码的依据关系



\### 5.1 文档索引



| 文档类型 | 路径/位置 | 版本 | 适用范围 | 状态 |

|---|---|---|---|---|

| QIAOPM PRD | `\[待调查回填]` | `\[待回填]` | `\[待回填]` | UNVERIFIED |

| QIAOPM 技术设计 | `\[待调查回填]` | `\[待回填]` | `\[待回填]` | UNVERIFIED |

| QIAOPM 补充需求 | `\[待调查回填]` | `\[待回填]` | `\[待回填]` | UNVERIFIED |

| QIAOPM 架构决策 ADR | `\[待调查回填]` | `\[待回填]` | `\[待回填]` | UNVERIFIED |

| QIAOPM 接口文档 | `\[待调查回填]` | `\[待回填]` | `\[待回填]` | UNVERIFIED |

| QIAOPM 数据库设计 | `\[待调查回填]` | `\[待回填]` | `\[待回填]` | UNVERIFIED |

| QIAOPM 设计稿 | `\[待调查回填]` | `\[待回填]` | `\[待回填]` | UNVERIFIED |
| 审计问题记录（Flyway V1 编码） | `audit/issues/I010-flyway-v1-encoding.md` | — | Flyway `V1__init_baseline.sql` 编码问题记录（GBK mojibake 根因分析） | CONFIRMED |
| 审计问题记录（Flyway baseline 冲突） | `audit/issues/I010-baseline-version-conflict.md` | — | Flyway `baseline-version` 与 V1 版本号冲突记录 | CONFIRMED（**文件当前不存在，待补建**，见 QIAOPM-ISSUE-002） |



\### 5.2 依据关系



```text

业务预期：QIAOPM PRD / 补充需求 / 已确认业务规则

技术设计：QIAOPM 技术设计文档 / ADR / 接口文档 / 数据库设计

当前实现：QIAOPM 代码 + 运行时证据

验收结论：本规范第 8/10 章 + QIAOPM 验收记录

```



\- 业务预期与技术设计冲突：以经确认的业务预期为准，并记录技术方案调整。

\- 文档与代码冲突：代码只能证明现状，不能自动证明正确；必须记录冲突并请求确认。

\- 文档缺失：记录到附录 D，不得猜测补全。



\---



\## 6. 需求冲突、文档缺失、信息不明确时的处理流程



\### 6.1 处理流程



1\. \*\*停止猜测\*\*，不擅自设计 QIAOPM 业务规则。

2\. 查阅本规范第 5 章 QIAOPM 文档索引。

3\. 查阅 QIAOPM PRD、技术设计、补充需求、ADR。

4\. 查阅 QIAOPM 代码与运行时证据，确认当前实现。

5\. 若仍不明确，登记到附录 D《待确认问题登记表》。

6\. 向 QIAOPM 业务/产品/设计/后端负责人请求确认。

7\. 确认前，相关功能状态标 `UNVERIFIED`，不得标 `VERIFIED`。

8\. 确认后，更新文档、功能矩阵和验收记录。



\### 6.2 禁止事项



\- 不得用猜测补全 QIAOPM 缺失需求。

\- 不得因“代码已经这么写了”就认定业务规则正确。

\- 不得擅自改变已确认的业务规则、数据库契约和 API 契约。

\- 不得隐藏冲突或只记录对自己有利的一方。



\---



\## 7. QIAOPM 新增功能、修改功能、修复缺陷时的调查与实施流程



\### 7.1 通用流程



```text

1\. 查阅总规范 → 2. 查阅 QIAOPM PRD/技术设计 → 3. 查阅代码与证据

→ 4. 输出差异分析/影响范围 → 5. 人工确认范围

→ 6. 最小修改 → 7. 运行适用测试 → 8. 回归关联模块

→ 9. 更新功能矩阵与验收记录 → 10. 记录未验证/待确认项

```



\### 7.2 新增功能



\- 必须有明确需求来源；无来源不得开发。

\- 先确认菜单/路由/权限/数据模型/API 契约。

\- 先写验收条件，再开发。

\- 涉及数据库变更必须评估迁移、回滚、数据兼容。



\### 7.3 修改功能



\- 必须说明修改原因、影响范围、关联模块。

\- 不得顺手重构无关代码。

\- 修改 API/数据库契约必须单独评审。



\### 7.4 修复缺陷



\- 先复现，记录复现步骤、环境、证据。

\- 定位根因，不做表面掩盖。

\- 修复后必须回归原缺陷 + 关联功能。

\- 不得通过删除测试、降低标准、隐藏错误来“修复”。



\### 7.5 Cursor / AI 辅助规则



\- \*\*默认只读分析\*\*：先输出差异报告，不直接改代码。

\- 差异报告必须包含：应有什么、现有什么、差异、证据、最小修复建议、风险、测试点。

\- 人工确认后才允许小步修改。

\- 禁止让 AI 大范围重构、批量改文件、擅自改契约。

\- AI 输出不得直接当作事实，必须人工核对证据。

\- AI 分析范围必须限定在 QIAOPM 指定模块目录内；跨模块影响必须列入风险。



\### 7.6 问题严重级别定义



| 级别 | 定义 | 示例 | 处理时限建议 |

|---|---|---|---|

| P0 | 主流程不可用、数据错误、越权、生产风险 | 无法登录、下单失败、越权可查他人数据 | 立即处理 |

| P1 | 主要功能缺失、异常/权限缺失 | 列表分页失效、无权限按钮仍可点 | 本轮修复 |

| P2 | 体验、边界、非阻塞问题 | 空态文案、边界提示、样式偏差 | 排期处理 |



\---



\## 8. QIAOPM 前端操作、后端逻辑、API、数据库的端到端验证标准



\### 8.1 端到端验证链



```text

前端操作 → 前端状态 → API 请求 → 后端逻辑 → 数据库读写 → API 响应 → 前端渲染 → 用户可见结果

```



任何一环无法验证，不得标 `VERIFIED`。



\### 8.2 每个功能点必须覆盖的场景



| 场景 | 要求 | 证据 |

|---|---|---|

| 正常流 | 主流程可完成，数据正确持久化 | 截图/录屏/接口返回/DB 记录 |

| 空态 | 无数据时展示合理 | 截图 |

| 异常/报错 | 后端错误、网络错误有合理提示 | 截图/日志 |

| 边界值 | 最大/最小/超长/特殊字符 | 截图/接口 |

| 权限/角色 | 无权限不可见/不可操作/后端拦截 | 截图/接口返回 |

| 刷新/返回/重复提交 | 状态不丢失、不重复写入 | 截图/DB 记录 |

| 数据一致性 | 前端展示与 DB 一致 | DB 查询证据 |

| 非菜单入口 | 如有，单独验证 | 证据 |



\### 8.3 验证禁止事项



\- 页面能打开 ≠ 功能完整。

\- 测试通过 ≠ 业务正确。

\- MOCK/硬编码/假成功响应 ≠ 真实实现。

\- 只验证前端不验证 DB ≠ 端到端通过。

\- 只验证正常流不验证异常/权限 ≠ 验收通过。

\- 只做 UI 清单不做文档反向对账 ≠ 模块验收通过。



\---



\## 9. QIAOPM 测试、回归测试、代码审查及证据留存要求



\### 9.1 测试要求



| 测试类型 | 要求 | 证据 |

|---|---|---|

| 单元测试 | `\[待调查回填：QIAOPM 现状与要求]` | 测试报告 |

| 接口测试 | `\[待调查回填]` | 请求/响应 |

| E2E/UI 测试 | `\[待调查回填]` | 录屏/报告 |

| 手工验收 | 每个功能点按第 8 章执行 | 验收记录 |

| 回归测试 | 修复后回归原功能 + 关联模块 | 回归记录 |



\### 9.2 代码审查



\- 修改必须可追溯：需求/缺陷 → 变更 → 测试 → 验收。

\- 审查重点：契约变更、权限、事务、异常、日志、敏感信息、跨模块影响。

\- 不得以“能跑”替代审查。



\### 9.3 证据留存



QIAOPM 验收证据统一存放路径：



```text

docs/acceptance/QIAOPM/<模块>/<验收编号>/

```



每个完成结论必须有证据，至少包括：



\- 操作路径/菜单路径；

\- 操作步骤；

\- 预期结果；

\- 实际结果；

\- 截图/录屏；

\- 接口请求与响应；

\- 数据库查询结果（涉及数据时）；

\- 控制台/服务端日志（涉及报错时）；

\- 环境信息（引用 2.5 环境矩阵）；

\- 验证人和时间。



无证据的结论标 `UNVERIFIED`。



\---



\## 10. QIAOPM 功能状态分类、验收条件、阻塞条件和完成定义



\### 10.1 主状态



| 状态 | 定义 | 是否可标完成 |

|---|---|---|

| VERIFIED | 已实现，按第 8 章端到端验证通过，有证据 | 是 |

| PARTIAL | 主流程可用，但异常/权限/校验/边界等缺失 | 否 |

| BROKEN | 实际点击/接口/数据验证失败 | 否 |

| MOCK | 假数据、假成功、硬编码、未接真实后端 | 否 |

| MISSING | 文档要求但页面/代码/接口/数据缺失 | 否 |

| UNVERIFIED | 尚未调查或环境阻塞，不能下结论 | 否 |



\### 10.2 子标签



| 子标签 | 含义 |

|---|---|

| UI\_ONLY | 页面有但逻辑无 |

| CODE\_WITHOUT\_UI | 代码有但前端无入口 |

| DOC\_ONLY | 文档有但代码无 |

| CODE\_ONLY | 代码有但文档未定义 |

| PERMISSION\_GAP | 权限/数据范围缺失 |

| NON\_MENU\_ENTRY | 非菜单入口未覆盖 |

| ENV\_BLOCKED | 环境阻塞无法验证 |

| DOC\_CONFLICT | 文档之间冲突 |



\### 10.3 验收条件



标 `VERIFIED` 必须同时满足：



1\. 有明确需求来源；

2\. 前端入口存在且可用；

3\. 真实 API 接通，非 MOCK；

4\. 数据正确持久化；

5\. 权限/数据范围验证通过；

6\. 正常流、空态、异常、边界、刷新/重复提交验证通过；

7\. 有证据记录；

8\. 关联模块回归通过；

9\. 正向清单与反向清单已对账，无未解释的 `DOC\_ONLY` / `CODE\_ONLY` / `MISSING`。



\### 10.4 阻塞条件



出现以下情况必须标阻塞，不得强行完成：



\- 环境不可用；

\- 依赖服务/第三方不可用；

\- 需求冲突未确认；

\- 权限/数据缺失无法验证；

\- 数据库不可访问；

\- 关键证据无法获取。



\### 10.5 完成定义（DoD）



```text

需求确认 + 实现完成 + 端到端验证通过 + 权限验证通过

\+ 异常/边界验证通过 + 双向清单对账通过 + 回归通过

\+ 证据留存 + 文档更新

= 完成

```



\### 10.6 UNVERIFIED 关闭条件



`UNVERIFIED` 只有在以下条件全部满足后才能关闭：



1\. 已完成真实调查；

2\. 已取得证据（截图/接口/DB/日志/命令输出至少其一）；

3\. 已更新功能矩阵与验收记录；

4\. 已登记最终结论（VERIFIED / PARTIAL / BROKEN / MOCK / MISSING）。



不得只在聊天中口头确认，不得以“应该可以”关闭。



\---



\## 11. 最小修改原则、跨模块影响检查及禁止擅自扩大范围



\### 11.1 最小修改原则



\- 只改与 QIAOPM 目标问题直接相关的代码。

\- 不顺手重构、不批量格式化、不升级无关依赖。

\- 不改变已确认契约。

\- 每次修改尽量小、可回滚、可审查。



\### 11.2 跨模块影响检查



修改前必须检查：



\- 是否被 QIAOPM 其他页面/模块引用；

\- 是否影响共享组件/公共 API/数据库字段；

\- 是否影响权限、数据范围、导入导出、报表；

\- 是否影响定时任务/后台任务/外部回调；

\- 是否需要回归关联功能。



\### 11.3 禁止事项



\- 禁止擅自扩大范围。

\- 禁止以“优化”为名改业务逻辑。

\- 禁止删除测试、降低标准、隐藏错误。

\- 禁止未授权操作生产环境或高风险数据。



\---



\## 12. 数据保护、敏感配置、生产环境和高风险操作安全约束



\### 12.1 数据保护



\- 敏感数据不得写入日志、截图、QIAOPM 文档明文。

\- 验收证据中的敏感字段必须脱敏。

\- 不得将生产数据复制到非生产环境，除非有授权和脱敏方案。



\### 12.2 敏感配置



\- 密钥、Token、数据库密码不得提交到 QIAOPM 仓库。

\- 配置变更必须走审批。

\- 不得在文档中记录真实密钥。



\### 12.3 生产环境与高风险操作



以下操作必须先确认授权和安全措施：



\- 生产数据库写操作；

\- 数据迁移/删除/批量更新；

\- 权限模型变更；

\- 接口契约破坏性变更；

\- 部署/回滚；

\- 第三方回调配置变更；

\- 定时任务启停。



\### 12.4 安全验收



每个功能验收至少检查：



\- 未登录不可访问；

\- 无权限不可操作且后端拦截；

\- 越权访问被拒绝；

\- 输入校验防注入/XSS；

\- 敏感信息不泄露；

\- 文件上传类型/大小限制。



\---



\## 13. QIAOPM 技术债务、已知问题、待确认事项和未验证事项的记录方式



\### 13.1 技术债务登记



| 编号 | 模块 | 描述 | 影响 | 风险 | 建议 | 状态 | 证据 |

|---|---|---|---|---|---|---|---|

| QIAOPM-TD-001 | `\[待回填]` | `\[待回填]` | `\[待回填]` | `\[待回填]` | `\[待回填]` | UNVERIFIED | `\[证据]` |



\### 13.2 已知问题登记



| 编号 | 模块 | 问题 | 复现步骤 | 影响 | 状态 | 证据 |

|---|---|---|---|---|---|---|

| QIAOPM-ISSUE-001 | `\[待回填]` | `\[待回填]` | `\[待回填]` | `\[待回填]` | UNVERIFIED | `\[证据]` |



\### 13.3 待确认事项



使用附录 D 模板登记，必须写明：问题、涉及文档、代码事实、冲突点、影响、待确认人、临时处理、状态。



\### 13.4 未验证事项



所有 `UNVERIFIED` 必须在 QIAOPM 功能矩阵或附录 D 中有对应记录，不得只写在聊天里。关闭条件见 10.6。



\---



\## 14. QIAOPM 文档与代码不一致时的处理方法及更新要求



\### 14.1 不一致类型



| 类型 | 含义 | 处理 |

|---|---|---|

| DOC\_ONLY | 文档有代码无 | 确认是否未实现，登记 MISSING |

| CODE\_ONLY | 代码有文档无 | 确认是否保留，登记待确认 |

| CONTRACT\_DIFF | 接口/字段/规则不一致 | 停止修改，评审确认 |

| UI\_DIFF | 页面与设计稿不一致 | 确认以哪个为准 |

| PERMISSION\_DIFF | 权限规则不一致 | 安全评审 |

| DATA\_DIFF | 数据结构/约束不一致 | 数据库评审 |



\### 14.2 处理要求



\- 记录冲突点、文档说法、代码事实、证据、临时处理、待确认人、最终结论。

\- 确认后更新文档或代码，二者必须收敛。

\- 不得长期保留“文档一套、代码一套”而不登记。



\---



\## 15. QIAOPM 逐菜单、逐页面、逐功能开发与验收标准工作流



> 本章是 QIAOPM 落地主流程，融合 \*\*左侧菜单 UI 驱动 + 文档反向对账 + 人工逐功能点击 + Cursor 只读差异分析 + 小步修复 + 回归\*\*。



\### 15.1 标准工作流



```text

Step 1  建立 QIAOPM 菜单/路由/页面/功能正向地图

Step 2  对照 PRD/技术设计/补充需求，建立反向功能清单

Step 3  正向清单与反向清单逐项对账，标注差异类型

Step 4  Cursor 只读差异分析，输出差异报告（不改代码）

Step 5  人工逐功能点击验收，覆盖第 8 章场景，留证据

Step 6  合并 UI 验收结果 + Cursor 差异报告，确定问题清单与优先级

Step 7  人工确认修复范围与优先级（P0/P1/P2，见 7.6）

Step 8  Cursor 小步修复，最小改动，不重构，不改契约

Step 9  回归原功能 + 关联模块

Step 10 更新功能矩阵、验收记录、待确认问题

Step 11 未验证项标记 UNVERIFIED，不假装完成

```



\### 15.2 每个模块的执行清单



\- \[ ] 菜单项已登记

\- \[ ] 路由/页面组件已登记

\- \[ ] 功能点已拆解

\- \[ ] 反向清单已建立（PRD → 功能点 → UI）

\- \[ ] 正反向清单已对账

\- \[ ] PRD/设计依据已关联

\- \[ ] 前端 API/后端接口/数据表已追踪

\- \[ ] Cursor 差异报告已输出（未改代码）

\- \[ ] 每个功能点已人工验收

\- \[ ] 正常流/空态/异常/边界/权限/刷新重复提交已覆盖

\- \[ ] 证据已留存到 `docs/acceptance/QIAOPM/<模块>/<验收编号>/`

\- \[ ] 状态已更新

\- \[ ] 修复项已回归

\- \[ ] 未验证/待确认已登记

\- \[ ] UNVERIFIED 关闭条件已满足（如关闭）



\### 15.3 Cursor 提示词模板



\*\*只读分析阶段（必须先用这一段）：\*\*



```text

你现在只分析 QIAOPM 项目的【模块名】模块，不要直接改代码。

依据：QIAOPM PRD 第 X 章、设计稿说明、左侧菜单【路径】。

任务：

1\. 列出该模块按 PRD/设计文档应具备的功能点；

2\. 对照 QIAOPM 前端页面、路由、API、状态管理、后端接口、数据库，

&#x20;  标记：VERIFIED / PARTIAL / BROKEN / MOCK / MISSING / UNVERIFIED；

3\. 找出 mock、TODO、硬编码、空实现、异常流缺失、权限缺失；

4\. 对照左侧菜单实际 UI，标注 UI\_ONLY / DOC\_ONLY / CODE\_WITHOUT\_UI；

5\. 对每个差异给出最小修复建议、影响范围和测试点；

6\. 输出差异报告，不要改代码。

相关目录：xxx（限定在 QIAOPM 指定模块目录内）。

实际现象：xxx。

控制台/接口返回：xxx。

```



\*\*人工确认后修复阶段：\*\*



```text

只修复已人工确认的问题编号 2 和 3，保持最小改动，不重构，不改变 QIAOPM API/数据库契约。

修改后列出：变更文件、影响范围、复测步骤、回归清单。

```



\---



\## 16. QIAOPM 非菜单入口、隐藏功能、后台任务、外部回调与权限差异检查



\### 16.1 必须单独检查的入口



| 类型 | QIAOPM 示例 | 是否已检查 | 状态 |

|---|---|---|---|

| 个人中心 | 头像菜单、设置 | `\[待回填]` | UNVERIFIED |

| 消息通知 | 站内信、邮件、短信 | `\[待回填]` | UNVERIFIED |

| 导出/导入 | 列表导出、模板导入 | `\[待回填]` | UNVERIFIED |

| 审批流 | 待办、已办、抄送 | `\[待回填]` | UNVERIFIED |

| 第三方回调 | 支付、OAuth、Webhook | `\[待回填]` | UNVERIFIED |

| 定时任务 | 清理、同步、报表 | `\[待回填]` | UNVERIFIED |

| 后台任务 | 异步队列、批处理 | `\[待回填]` | UNVERIFIED |

| 权限角色差异 | 管理员/普通用户/访客 | `\[待回填]` | UNVERIFIED |

| 数据范围 | 本人/本部门/全部 | `\[待回填]` | UNVERIFIED |

| 隐藏路由 | 详情页、编辑页、打印页 | `\[待回填]` | UNVERIFIED |



\### 16.2 检查要求



\- QIAOPM 非菜单入口不得因“左侧菜单没有”而漏检。

\- 权限差异必须用不同角色实际验证。

\- 后台任务/回调必须有日志或执行记录证据。

\- 隐藏路由必须确认权限和数据范围。



\---



\## 附录 A：QIAOPM 功能矩阵模板



| 模块 | 菜单路径 | UI 入口 | 功能点 | 文档来源 | PRD 依据 | 前端 API | 后端接口 | 数据表 | 优先级 | 预期结果 | 实际结果 | 主状态 | 子标签 | 证据 | 负责人 | 修复状态 | 备注 |

|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |



\---



\## 附录 B：QIAOPM 验收记录模板



```text

验收编号：QIAOPM-ACC-<模块>-<序号>

模块/菜单路径：

功能点：

需求依据：

环境（引用 2.5）：

验证人：

验证时间：



操作步骤：

1\.

2\.



预期结果：



实际结果：



覆盖场景：

\[ ] 正常流  \[ ] 空态  \[ ] 异常  \[ ] 边界

\[ ] 权限  \[ ] 刷新/返回  \[ ] 重复提交  \[ ] 数据一致性



证据（存放于 docs/acceptance/QIAOPM/<模块>/<验收编号>/）：

\- 截图/录屏：

\- 接口请求/响应：

\- 数据库记录：

\- 日志：



结论：VERIFIED / PARTIAL / BROKEN / MOCK / MISSING / UNVERIFIED

子标签：

关联问题：

待确认事项：

```



\---



\## 附录 C：QIAOPM Cursor 差异分析报告模板



```text

分析编号：QIAOPM-DIFF-<模块>-<序号>

分析范围：

依据文档：

相关目录：

实际现象：



一、应具备的功能点（来自 PRD/设计）

1\.

2\.



二、当前实现事实

| 功能点 | UI 入口 | 前端 | 后端 | 数据库 | 证据 | 状态 |



三、正向/反向清单对账差异

| 编号 | 差异 | 类型（UI\_ONLY/DOC\_ONLY/CODE\_WITHOUT\_UI/CODE\_ONLY） | 影响 | 证据 | 最小修复建议 | 测试点 |



四、MOCK/TODO/硬编码/空实现清单



五、权限/异常/边界缺失清单



六、风险与跨模块影响



七、待确认问题



八、结论（未改代码）

```



\---



\## 附录 D：QIAOPM 待确认问题登记模板



> 本表在第 2 章回填（v0.2）时首次填充。严重级别按 7.6 定义：P0 / P1 / P2。
> `ISSUE` = 已确认存在的事实性缺陷；`TD` = 技术债；`Q` = 需人工确认后才能下结论的问题。



\#### D.1 已知问题（ISSUE）

| 编号 | 级别 | 问题 | 涉及模块 | 文档说法 | 代码事实（证据） | 影响 | 建议处理 | 状态 |
|---|---|---|---|---|---|---|---|---|
| QIAOPM-ISSUE-001 | P1 | `docker-compose.yml` 支付环境变量名 `PAY_ENV_MODE` 与配置约定 `APP_CN_PAY_ENV_MODE` 不一致，compose 注入的支付环境变量不会被应用读取 | 部署编排 / 支付 | `.env.example`、`application.yml`、`application-prod.yml` 均使用 `APP_CN_PAY_ENV_MODE` | `docker-compose.yml:120` → `PAY_ENV_MODE: ${PAY_ENV_MODE:-production}`；`application.yml:234` → `env-mode: ${APP_CN_PAY_ENV_MODE:sandbox}`。变量名不匹配，配置键取不到 compose 注入值，实际落到 `${APP_CN_PAY_ENV_MODE:sandbox}` 默认值 | compose 起服务时支付环境模式始终为 `sandbox`，与 `${PAY_ENV_MODE:-production}` 的意图相反；本地联调可能出现"以为在生产实则沙箱"的假象 | 统一变量名（改 compose 或改配置键，二选一），并补一条 compose→应用生效性的启动校验 | OPEN |
| QIAOPM-ISSUE-002 | P1 | Flyway 形式存在但 V1 被 `baseline-version: 2` 跳过，实际 schema 演进靠 170 个手工 SQL | 数据库迁移 | `application.yml:29-30` 自述：`baseline-description: "pre-flyway schema (legacy init_*/migrate_* scripts, manually applied; V1 file is mojibake GBK, must skip via baseline-version>V1.version)"` | `db/migration/` 下仅有 `V1__init_baseline.sql`（+ 一个 `.tmp` 残留），因 `baseline-version: 2` > `V1.version=1` 而**永久不会 apply**；`db/sql/` 下 170 个 `init_*` / `migrate_*` / `patch_*` / `seed_*` / `verify_*` 脚本为实际 schema 来源；docker-compose 的 initdb 只挂 `db/sql` 且按字典序执行 `init_*` | 新环境无法凭 Flyway 自动获得一致 schema；schema 演进无版本链、无回滚路径；`V1__init_baseline.sql.tmp` 属残留文件 | 二选一：① 重建干净 UTF-8 的 V1 并把 `baseline-version` 降回 0；② 正式确认"Flyway 仅作形式、迁移以手工 SQL 为准"，并在第 3.6 章按此事实改写迁移规范。详见 `audit/issues/I010-flyway-v1-encoding.md`（**注**：`audit/issues/I010-baseline-version-conflict.md` 当前不存在，待补建） | OPEN |
| QIAOPM-ISSUE-003 | P2 | `DEPLOY-SOP.md` 监控表提及"Kafka / MQ 积压"，但代码零 MQ 依赖 | 部署文档 / 消息队列 | `docs/DEPLOY-SOP.md` §4 监控项列有"Kafka / MQ 积压 < 100" | `backend/pom.xml` 无 kafka / rabbitmq / rocketmq 依赖；`backend/src` 全量 grep 无命中；唯一类 MQ 组件为 IoT 用 HiveMQ MQTT Client `1.3.3`（`iot.mqtt.enabled` 默认 false） | 监控项无指标来源，属**文档漂移（假能力）**；若生产真部署了 Kafka 则监控表与运行态脱节 | 确认是否实际部署 Kafka（见 QIAOPM-Q-003）：确未部署则删除监控项；确有部署则补明其用途与代码接入路径 | OPEN |
| QIAOPM-ISSUE-004 | P2 | `docs/governance/CURRENT_STATE.md` 代码规模数字过期 | 治理文档 / 现状快照 | `CURRENT_STATE.md:99` SQL 迁移文件 `78`；`:107` 前端视图目录 `41`；`:108` API 接口文件 `42`；`:110,123` E2E `8 个 spec 文件 / 180` 用例 | 实测：`db/sql/` **170** 个 `.sql`；`src/views/` **171** 个 `.vue`；`src/api/` **62** 个 `.ts`；`e2e/` **21** 个 `*.spec.ts` | 现状快照失真，新人按此评估工作量会低估约 2~4 倍 | 基于实测重新生成代码规模快照，替换过期数字 | OPEN |
| QIAOPM-ISSUE-005 | P2 | CI `integration` job 引用仓库中不存在的 `Run-All-Tests.ps1` | CI 流水线 | `.github/workflows/ci.yml` `integration` job 声明执行集成测试 | `.github/workflows/ci.yml:143` → `pwsh ./Run-All-Tests.ps1`；仓库根**不存在**该文件；该 job 另混用 bash / pwsh 语法 | 该 job 必然失败或被静默跳过 → **集成测试实际无门禁效果**，假绿风险 | 补齐脚本或改指向 `tests/` 下现有的 `smoke-full.ps1`；同时统一 job 的 shell 声明 | OPEN |
| QIAOPM-ISSUE-006 | P0 | `多人共享套餐`（`/package/share`）前端调用的 6 个后端接口完全缺失，页面 `onMounted` 即触发必然失败的请求 | 会员 / 套餐（对应 4.2.1（3）主表行；对应调查草稿 P-1） | 第 4.2 节正向清单要求「每个功能点必须能追踪到前端 API、后端接口和数据表」 | 菜单 `Layout.vue:158`；路由 `router/index.ts:41`（`permission: 'package:list'`）；组件 `views/package/share.vue`；前端 `api/package-share.ts`（1929 B）调 `stats`→`GET /crm/package/share/stats`、`page`→`GET /crm/package/share/page`、`pause`→`POST .../pause`、`resume`→`POST .../resume`、`revoke`→`POST .../revoke`、`adjustQuota`→`PUT .../quota`；后端 `service/P2FeaturesService.java` 全量 mapping 仅 `invite`(:52)、`redeem`(:105)、`list`(:166)、`enable-share`(:175) 四项 → **六个 mapping 均不存在**；实体 `CrmPackageShare` + Mapper 存在 | 主流程断裂：页面 `onMounted:341` 同时调 `loadList()+loadStats()`，两者必然失败；列表与统计恒为空且 `try/catch` 吞异常无提示 → **静默空白页**（属假成功/幽灵功能）。属「只做了 Model 层未做接口层」 | 补齐 6 个 mapping，或按最小修改原则移除页面对应调用与按钮；补接口后按第 8 章覆盖正常流/空态/异常/权限 | 暂停 —— 见 QIAOPM-Q-007 |
| QIAOPM-ISSUE-007 | P0 | `提成结算批次` / `提成冲正` 两个菜单页后端无任何接口映射 | 员工提成（对应 4.2.1（7）主表两行；对应调查草稿 P-4） | 第 4.2 节正向清单要求后端接口可追踪 | 菜单 `Layout.vue:170-171`；路由 `router/index.ts:94`（`commission:batch`）与 `:95`（`commission:reversal`）；组件 `views/commission/batch.vue`、`views/commission/reversal.vue`；前端直连 `/commission/batch/{export,generate,page}`、`/commission/reversal`、`/commission/reversal/export`、`/commission/reversal/page`、`/commission/reversal/stats`；后端 `controller/` 包仅有 `CommissionController`(`/commission`)、`CommissionSimulationController`、`CommissionConditionController`、`CommissionTierController`，**全仓检索 `commission/batch`、`commission/reversal` 零命中**；实体 `CommissionSettlementBatch`、`CommissionReversal` 存在 | 两个一级功能菜单可从左侧进入但**全部操作无效**；提成财务链路（结算→冲正）不可用，属主流程断裂 | 补齐 `/commission/batch` 与 `/commission/reversal` 的 Controller 层；确认是否与既有 `/commission/settlement` 重复后再定契约 | OPEN |
| QIAOPM-ISSUE-008 | P1 | 电子价目表页调用不存在的 `POST /crm/member/list` | 基础资料（对应 4.2.1（2）主表行；对应调查草稿 P-2） | `controller/MemberController.java` base 为 `/member` | `views/dev/PriceList.vue:331` → `method: 'POST', url: '/crm/member/list'`；后端 `MemberController` base `/member`（正确路径应为 `/member/list` 或 `/member/page`）；全仓检索 `@RequestMapping("/crm/member")` **零命中** | 契约漂移：价目表页绑卡项套餐时必然 404，下拉为空且无有效错误提示 | 统一改为 `/member/list` 或 `/member/page`；按第 3.4 章「契约变更需评审」确认目标路径后修改 | OPEN |
| QIAOPM-ISSUE-009 | P1 | `/service/list` 被 3 个页面调用，后端不存在该路径 | 耗材 / 资源排程（对应 4.2.1（11）主表三行；对应调查草稿 P-9） | 服务项目实际由 `service/BizServiceItemService.java` 提供 `/biz/service*` | 调用方三处：`views/resource/resource-schedule.vue:386`、`views/material/binding.vue:543`、`views/material/service-material.vue:364`，均为 `request.get('/service/list')`；后端全仓检索 `@RequestMapping("/service")` **零命中** | 三个页面的服务下拉框恒为空 —— 耗材绑定、双资源排程、项目耗材规则**均依赖此下拉**，属主要功能缺失 | 统一改为 `/biz/service/item/page`（以 `BizServiceItemService` 实际 mapping 为准）；建议抽到 `api/` 层避免第 3 处再漂移 | OPEN |
| QIAOPM-ISSUE-010 | P1 | 菜单「员工管理」无路由、无组件，菜单点击无落地页 | 系统设置（对应 4.2.1（12）主表行；对应调查草稿 P-10） | 菜单声明存在即应有路由与组件（第 4.6 章映射规则） | 菜单 `Layout.vue:281` `{ path: 'employee', title: '员工管理', icon: User }` → 按父级解析为 `/sys/employee`；`router/index.ts` 中 `path: 'employee'` **零命中**（全表 156 条 `path:` 声明）；组件 `views/setting/employee.vue` 存在但仅被 `views/setting/index.vue` 作为 tab 引用；后端 `controller/EmployeeController.java` 的 `/employee`、`/employee/list`、`/employee/page` **接口齐备** | 菜单可见 → 点击空白/404，属「UI 入口与实现脱节」；后端已就绪，属纯前端路由缺失 | 补注册 `sys/employee` 路由指向 `views/setting/employee.vue`；或按最小修改原则从 `fallbackMenus` 移除该菜单项 | OPEN |
| QIAOPM-ISSUE-011 | P0 | `commission/rules` 路由重复注册，权限点被覆盖导致 403 | 员工提成（对应 4.2.1（7）主表行；对应 4.2.0 S-5；对应调查草稿 P-5） | 每条路由应唯一绑定一个权限点（第 3.5 章权限约定） | `router/index.ts:58` → `permission: 'commission:rule:list'`，title `提成规则`；`router/index.ts:114` → `permission: 'commission:rule:config'`，title `提成规则配置`；两条 `name` 同为 `CommissionRules`，Vue Router 后注册者覆盖前者 | 持有 `commission:rule:list` 但无 `commission:rule:config` 的角色访问 `/commission/rules` 被 `beforeEach` 守卫跳 `/403` → **假性 404：有菜单、有权限点但进不去**，属假成功 | 见 QIAOPM-TD-005 / QIAOPM-Q-008，权限体系对齐后再修；当前不单独修复 | 待决策 —— 根因升级为权限体系分裂，见 QIAOPM-TD-005 |
| | | | | | 2026-10-23 运行验证：boss01 访问 /commission/rules 被 403，提示 commission:rule:config。根因调查（QIAOPM-ACC-002）确认该码在 sys_permission 中不存在，boss01 实际持有 commission.rules（点号）。修复方向不唯一，待决策。 | | | |
| QIAOPM-ISSUE-012 | P2 | 款式图库前端 API base path 与后端不一致 | 营销工具 / 款式图库（对应 4.2.1（8）主表行；对应 4.3 H-19；对应调查草稿 P-6） | 前后端契约须一致（第 3.4 章 API 约定） | 前端 `api/style-gallery.ts` 调 `/style/list`(:16)、`/style/${id}`(:20)、`/style/${id}/view`(:28)、`/style/${id}/like`(:32)、`/style/${id}/order`(:40)、`/style/quick-order`(:77)、`/style/init-default`(:81)；后端 `controller/StyleGalleryController.java:23` base 为 **`/gallery`**（含 `POST`、`GET /{id}`、`POST /{id}/view`、`POST /{id}/like`、`GET /list`、`POST /{id}/order`、`POST /init-default`）；`/style` 由 `controller/StyleLibraryController.java:16` 提供，实体为 `StyleLibrary`（款式**偏好库**，`@SysOpLog(module = "款式库")`），**语义不同** | 前端图库请求打到款式库接口，命中错误的实体与权限点（`style:library:*` vs `style:gallery:*`）；返回结构可能不匹配，快捷下单受影响 | 前端改调 `/gallery/*`，或后端在 `/style` 下补图库 mapping；**须先确认 `/style` 下两套语义是否本就重叠**，属契约变更需评审 | OPEN |
| QIAOPM-ISSUE-013 | P2 | RFM 分群模块静态链路存在但数据取数未确认 | 客户运营 / RFM（对应 4.2.1（9）主表行；对应 4.4 API 模块表；对应调查草稿 P-7） | 第 4.6 章「每个功能点必须能追踪到前端 API、后端接口和数据表」 | 路由 `router/index.ts:146`（`permission: 'crm:rfm'`）；入口 `views/intel/rfm.vue` 为 shim（注释自述「旧入口兼容，真实实现已迁移到 `./rfm/index.vue`」）；`views/intel/rfm/index.vue` 挂 4 个 tab（`OverviewTab`、`ComputeTab`、`TagDictTable`、`BatchTab`）；**`api/rfm.ts` 确被引用**：`SegmentDrawer.vue:93`、`TagDictTable.vue:185`、`useRfmBatch.ts:2`、`useRfmOverview.ts:2`、`useRfmCompute.ts:2`、`useRfmDict.ts:2` 等 11 处（均用相对路径 `../../../api/rfm`）；后端 `controller/RfmBatchController.java` `/intel/rfm`、`RfmSeedController` `/intel/rfm/seed` 存在 | 静态链路（菜单→路由→组件→API→后端）**齐全**，但「10 个组件全部零 API import」这一初判**经复测不成立**（原调查疑似只检索了 `@/api/rfm` 别名写法，漏掉相对路径写法）。剩余风险：4 个 tab 是否均取到数、批量任务是否真跑通，需运行期确认 | 按第 8 章对 `/intel/rfm` 4 个 tab 逐个做端到端验证后再定级；**本条不得按「UI 与 API 零关联」处理** | OPEN |
| QIAOPM-ISSUE-014 | P2 | 营销三模块双后端并存（`/market/*` 与 `/mkt/*`），数据表亦分裂 | 营销工具（对应 4.2.1（8）主表三行；对应 4.3 H-11/H-12/H-13；对应调查草稿 P-14） | 一个业务概念应有唯一权威接口与表（第 3.4 / 3.3 章） | `/market/coupon` → `controller/CouponController.java:38` 与 `/mkt/coupon` → `controller/MktCouponController.java:37` 并存；`/market/point` → `controller/PointController.java:21` 与 `/mkt/points` → `controller/MktPointsController.java:31` 并存；`/market/activity` → `controller/MarketActivityController.java:29` 与 `/mkt/promotion` → `controller/MktPromotionController.java:35` 并存；对应表分裂为 `market_coupon` / `mkt_coupon`、`market_point_record` / `mkt_points_log`、`market_activity` / `mkt_promotion`；同一组件 `views/mkt/coupon.vue` 等被两条 base 复用 | **数据分裂风险**：同一业务概念两套表，若两套都在写入将导致数据不一致、统计口径失真；当前哪套在用**无法静态确认** | 连库确认两套表的行数与写入方，确定唯一权威实现后下线另一套；迁移前须按第 12.3 章做生产数据变更授权 | OPEN |

> **已关闭**：原调查项 ISSUE-5（仓库根 `.env` 敏感文件风险）—— 经人工确认，`.env` 未进 git 历史、已被 `.gitignore` 忽略、未被跟踪，**关闭**，不再登记为 ISSUE。
> **本轮复测修正（2026-10-22）**：`QIAOPM-ISSUE-013` 登记描述与初稿（P-7「RFM 模块 UI 与 API 零关联」）**不一致**。复测确认 `api/rfm.ts` 被 `views/intel/rfm/` 下 11 处引用，故该行按「静态链路齐全、取数待运行期确认」登记，未按「零关联」定性。详见该行证据列。

\#### D.2 技术债（TD）

| 编号 | 级别 | 问题 | 涉及模块 | 文档说法 | 代码事实（证据） | 影响 | 建议处理 | 状态 |
|---|---|---|---|---|---|---|---|---|
| QIAOPM-TD-001 | P2 | `backend/` 根目录堆积 300+ 个临时日志 / 脚本 / 编译参数产物 | 仓库卫生 | 仓库根与 `backend/` 根应为源码与配置的规范结构 | `backend/` 根下有 300+ 个 `*.log` / `*.txt` / `compile_*.ps1` / `javac_args*.txt` / 临时 `*.sql`；仓库根另有 `out*.log`、`check_servers.ps1`、`check_v2.ps1` | 污染目录结构事实（已登记到 2.1）；干扰新人定位源码；`.gitignore` 覆盖不全时有误提交风险 | 归档到 `backup/` 或删除；补齐 `.gitignore` 规则；清理后更新 2.1 目录表 | OPEN |
| QIAOPM-TD-002 | P2 | 84 个 `@RestController` 位于 `service/` 包，不符合「controller → service」分层约定 | 后端分层 / 可追溯性（对应 4.2.0 S-3；对应第 2.3 章分层事实） | 第 2.3 章「后端分层：`controller` → `service` / `service.impl` → `mapper` → `entity`」 | 标准位置 `backend/src/main/java/com/meiyetec/controller/` 136 个 `.java` 全部为 `@RestController`；`service/` 目录下另有 81 个 `@RestController`（含 `P1SuiteService`、`P2FeaturesService`、`P3EcosystemSuiteService`、`KioskService`、`GdprService`、`HipaaService`、`IntlBookingService`、`IntlPaymentService`、`GoogleMapsService`、`ReportV2Service`、`VisualArchiveService`、`OrgHierarchyService`、`ConsentAndDrugDeviceService`、`CrmIntegrationService`、`EcommerceService`、`FiscalService`、`MemberDayService`、`LbsDispatchService`、`LtvService`、`DynamicPricingService`、`GiftCardService`、`ComplicationService`、`BirthdayReminderService`、`BizServiceItemService`、`BizTreatmentService`、`BookingResourceService`、`BrandManagementService`、`CarePlanService`、`CareFormulaService`、`PriceListService`、`DataHubService`、`PosDeviceService`、`PaymentConfigService`、`PluginService`、`RiskControlService` 及 `CrossStoreInventoryController` 等）；另 `cn/domestic/DomesticPayController.java`、`config/TraceResponseAdvice.java`、`exception/GlobalExceptionHandler.java` | 违反第 2.3 章分层事实：**按目录追踪接口会漏掉约 1/3 接口**（本次 4.2 主表中 ⚠️ 标记行均属此类）；Service 类同时承担业务与 HTTP 契约，难以单测与复用 | 制定 `controller` 包迁移计划（建议随功能迭代小步迁移，不做一次性大重构）；至少先在第 2.3 章标注「`service/` 下 84 个 `@RestController` 属已知偏差」；排查工具/脚本须按 `@RestController` 注解全仓检索而非仅扫 `controller/` 目录 | OPEN |
| QIAOPM-TD-003 | P2 | 40+ 视图绕过 `api/` 层直连 HTTP，接口契约分散 | 前端分层（对应 4.2.0 S-4；对应第 2.3 章前端分层事实） | 第 2.3 章「前端分层：`views/`（页面）→ `api/`（接口封装）→ `src/api/request.ts`」 | `src/api/` 存在 62 个 `.ts`，但 `src/views/**/*.vue` 中直接 `import ... from '@/utils/http'` 或 `@/api/request` 的文件达 **51** 个（如 `views/org/{hq,brand,store}.vue`、`views/mkt/*.vue`、`views/crm/care-*.vue`、`views/mh/{visual,consent}.vue`、`views/booking/{resource,waitlist,no-show}.vue`、`views/commission/{batch,reversal}.vue`、`views/spa-scene/index.vue`、`views/pay/dcep.vue` 等） | 接口路径散落在 51 个组件内，**绕过 `api/` 统一封装**：改 base path / 加拦截器 / 统一错误处理时易遗漏；本次 `/service/list`（ISSUE-009）与 `/crm/member/list`（ISSUE-008）两处契约漂移均出自直连代码 | 将直连调用逐步收敛到 `src/api/*.ts`；优先收敛 4.2 主表中标 **无（http 直连）** 的行；收敛后按第 3.1 章「API 调用封装」补齐约定 | OPEN |
| QIAOPM-TD-004 | P2 | 静态引用扫描只覆盖 `@/` 别名、漏检相对路径写法，导致「零引用 / 零关联」误判 | 审计方法论 / 排查工具（由 QIAOPM-ISSUE-013 复测衍生；关联 QIAOPM-TD-003） | 第 4.6 章「每个功能点必须能追踪到前端 API、后端接口和数据表」要求结论以全量引用关系为依据 | 本次 RFM 初判「UI 与 API 零关联」**仅检索 `@/api/rfm` 别名写法**，未覆盖 `../../../api/rfm` 相对路径写法，复测发现 `views/intel/rfm/` 下实际有 11 处相对路径引用（`SegmentDrawer.vue:93`、`TagDictTable.vue:185`、`useRfmBatch.ts:2`、`useRfmOverview.ts:2`、`useRfmCompute.ts:2`、`useRfmDict.ts:2` 等），初判不成立 | **方法论缺陷**：单写法扫描会产生「零引用」假阴性结论，进而误判功能缺失、错定 ISSUE 级别（如 ISSUE-013 曾被按「主流程断裂」拟稿）；同样可能把实际已收敛到 `api/` 的视图误计入 TD-003 的 51 个直连统计（**TD-003 的 51 这一数字未经双写法复核，暂按待复核对待**） | 后续所有「零引用 / 零关联 / 零调用」结论必须**双写法交叉验证**：`@/api/*`（或 `@/utils/http`、`@/api/request`）与 `../../../api/*` 相对路径同时检索，两者结果合并后才可下结论；建议在 `audit/` 下沉淀一个双写法检索脚本作为守门步骤，后续 PR / 复测复用；对 TD-003 的 51 个直连视图做一次双写法重扫并回填数字 | OPEN |
| QIAOPM-TD-005 | P0 | 权限体系分裂：前端 147 条路由 100% 使用冒号体系（如 `commission:rule:config`），后端 `sys_permission` 存在点号体系（如 `commission.rules`）且 BOSS 持有的是点号码 | 认证授权 / 权限体系（对应 4.2.0 S-5；对应 QIAOPM-ISSUE-011；关联 QIAOPM-TD-004 方法论缺陷） | 第 3.5 章「认证授权与权限」的菜单权限 / 按钮权限两行目前仍为 `\[待调查回填]`，尚未确立权限码命名规范 | 已确认至少 2 个前端引用的权限码在 DB 中不存在（`commission:rule:config`、`commission:rule`），另有 1 个存在但零角色绑定（`commission:rule:list`）；前端 `router/index.ts` 147 条 `meta.permission` 中 146 条为冒号体系、1 条无分隔符（`setting`）、**0 条点号**；DB 侧 `sys_permission` 454 个 distinct 码中点号 74 个 / 冒号 380 个，两套体系在同一张物理表并存 | 影响面可能覆盖多个模块，需全量审计 147 条路由后方可决策修复策略 | 先做全量对账（147 条路由 `meta.permission` × 454 个 `sys_permission` 码），产出「前端引用码 vs DB 实际码 / 角色绑定」差异清单；清单出来前**不修改任何权限码或路由**；对齐方向待架构决策，见 QIAOPM-Q-008 | OPEN |

\#### D.3 待确认问题（Q）

| 编号 | 问题 | 涉及模块 | 文档说法 | 代码事实 | 冲突点 | 影响 | 待确认人 | 临时处理 | 状态 | 结论 |
|---|---|---|---|---|---|---|---|---|---|---|
| QIAOPM-Q-001 | 测试环境（staging）是否存在？若存在，请提供前端 / 后端地址、DB 名与可用账号（admin / 普通用户 / 跨租户角色） | 环境矩阵 / 部署 | `docs/DEPLOY-SOP.md` §0 提及环境含 main / staging | 仓库无 staging 编排文件、`application.yml` 无 staging profile、无地址清单 | 文档暗示有 staging，仓库无任何可佐证事实 | 2.5 环境矩阵 staging 整行 UNVERIFIED；无法在此环境做验证 | 运维 | 2.5 staging 行保持 UNVERIFIED，不得用于验收 | UNVERIFIED | |
| QIAOPM-Q-002 | 生产真实域名（CloudFront 前端域名、nginx 后端入口）、真实 DB / Redis 主机分别是什么？ | 环境矩阵 / 部署 | `docs/prod-env-manifest.md` §2-§4 为环境变量事实源 | `.env.example` 全为占位（`prod-rds-host` / `prod-redis-host`）；`application-prod.yml` 强制 ENV 注入不落盘 | 流程文档完整，但真实地址无任何仓库内来源 | 2.5 生产"真实地址"行 UNVERIFIED；生产验收无法定位入口 | 运维 | 2.5 生产真实地址行保持 UNVERIFIED；验收记录不得写具体生产地址 | UNVERIFIED | |
| QIAOPM-Q-003 | Kafka 是否实际部署？ | 部署 / 消息队列 | `DEPLOY-SOP.md` §4 监控表列"Kafka / MQ 积压 < 100" | 代码零 MQ 依赖（见 QIAOPM-ISSUE-003 证据） | 文档有监控项，代码无对应组件 | 若确未部署 → 监控表为假能力需删除；若确有部署 → 监控表与代码脱节，需补接入路径 | 运维 | 2.2 "消息队列"行按"代码零 MQ 依赖 = CONFIRMED"记录，不宣称有 Kafka | UNVERIFIED | |
| QIAOPM-Q-004 | Node 版本以 20 为准还是 18+ 为准？ | 前端构建 / 工程规范 | `README.md:45` 写"Node.js 18+" | `ci.yml:73,114,184` 用 `node-version: '20'`；`frontend/meiyetec-web/Dockerfile` 用 `node:20-alpine`；仓库无 `.nvmrc` | 文档口径 18+，CI 与镜像口径 20 | 新人按 18+ 装环境可能与 CI 不一致，导致"本地过、CI 挂" | 前端负责人 | 2.4.1 本地前端行标 PARTIAL 并注明口径冲突，不擅自选定版本 | UNVERIFIED | |
| QIAOPM-Q-005 | 运行态左侧菜单的真实内容是什么？请导出 `sys_menu_permission` 表数据（菜单层级 / `path` / `title` / `permission` / `visible` / `sort`）用于与 4.2 节静态清单对账 | 菜单与权限 / 第 4.2 章（对应 4.2.0 S-1、S-2） | 第 4.2 章正向清单要求「菜单 → 路由 → 页面 → 功能点」逐段可追踪 | 菜单由后端 `GET /auth/me/menu` 动态下发（`controller/RbacController.java:71-75` → `service/RbacService.buildMenuTree`）；`components/Layout.vue:321-332` 优先渲染 `store.menus`，仅在动态菜单为空时回落到静态 `fallbackMenus`（`Layout.vue:102`）；菜单与权限**同表**（`entity/SysMenuPermission.java`） | 4.2 主表基于**静态 `fallbackMenus`**，与运行态 `sys_menu_permission` 是否一致**未验证**；两者不一致时会出现「有菜单无路由」或「有路由无菜单」，直接影响第 4 章全部行 | 4.2 全表可信度存疑；H-22（`/sys/role` 无菜单入口）等判定可能反转 | 产品 + 后端 | 4.2.0 顶部按原文声明限制；主表「菜单层级 / UI 入口」列视为静态事实，**不得当作运行态事实引用**；导出后逐行对账并更新 4.2 / 4.3 | UNVERIFIED | |
| QIAOPM-Q-006 | QIAOPM PRD 原文在哪里？如不存在，请指定替代的经确认需求文档（技术设计 / 补充需求 / 验收清单） | 文档依据 / 第 4.5、第 5.1 章 | 第 0.3 章规定「业务预期以经确认的 PRD / 补充需求为准」；第 4.5 章反向清单需以 PRD 为唯一输入 | 仓库内未检索到 QIAOPM PRD 文档；`docs/` 下仅有 `DEPLOY-SOP.md`、`prod-env-manifest.md`、`runbook/*`、治理文档 | 规范强依赖的 PRD 缺失 → 业务预期无权威来源 | 第 4.2 节主表「PRD 章节」列全部留空；第 4.5 章反向清单无法回填；双向对账无法完成 → 相关模块整体不得标 `VERIFIED` | 产品 | 主表「PRD 章节」列统一标注"QIAOPM PRD 原文未找到，待补"；4.5 保持模板状态，**不得用正向清单反推文档预期** | UNVERIFIED | |
| QIAOPM-Q-007 | 多人共享套餐（`/package/share`）的业务意图与 `crm_package_share` 表状态双重待确认 | 会员 / 套餐（对应 QIAOPM-ISSUE-006；对应 4.2.1（3）主表行） | 第 7.2 章要求新增功能须有需求来源；第 4.6 章要求功能点可追踪到数据表 | **代码证据（指向开发中断）**：六项调查证据中 5 项指向「开发中断」而非「功能废弃」——(a) 实体+Mapper 被 `P2FeaturesService.java:34/95/119/169` 生产代码实际读写，`invite`/`redeem`/`list`/`enable-share` 四端点逻辑完整（含 CAS 扣次、配额校验、状态机、`@Transactional`、租户隔离）；(b) `share.vue` / `P2FeaturesService.java` / `package-share.ts` 三文件零 TODO / FIXME / 待补充注释；(c) `migrate_r3_0_e_p2_features.sql:12-40` 有完整建表 + `crm_package_order` 3 列 ALTER，注释自述「R3.0-E P2：高级差异化（最小骨架）」；(d) `P2EntityFieldsTest.java:19` 实体字段测试保留；(e) `share.vue` 无 `v-if="false"` / 无「敬请期待」/ 无 feature flag，撤销按钮的 `disabled` 为业务态条件 | **缺失证据**：① PRD 原文（见 QIAOPM-Q-006，仓库内无 QIAOPM PRD）；② `crm_package_share` 表在目标环境是否实际 apply —— `audit/post-audit/scripts/migrate_r3_0_e_p2_features.EXCLUDED-FOR-2.3.sql:16` 注释「暂不 apply」，`audit/post-audit/phase-2.3-batch-2-apply-log.md:40,48` 记录 apply 后 `SHOW TABLES LIKE 'crm_package_share'` 返回 `Empty set`；③ 停做决策来源（2026-10-16 前 git 历史因仓库损坏丢失，`git log` 仅剩 `a414f95 recover`，无法查证是否存在主动叫停记录） | **双重阻塞**：① 补齐 6 个接口（`stats`/`page`/`pause`/`resume`/`revoke`/`quota`）属**新增功能**，按第 7.2 章须有需求来源定义验收条件，PRD 缺失状态下不得开发——**开发中断 ≠ 应当补齐**；② 若表未建，即便补齐接口也无法运行验证，修复不可验收 | 产品（PRD 与业务意图）+ 运维/DBA（目标环境表状态） | **暂停该模块修复**；前端保持现状不改（`share.vue` 的 `loadStats` / `loadList` 以 `catch { /* 静默 */ }` 吞异常，用户看到空列表且不报错，属已知静默降级，不在本次修复范围）；后端 4 个现存端点保持不动；待 Q-007 结论回填后再定修复方向（补接口 / 移除页面 / 维持现状三选一须重新决策） | UNVERIFIED | |
| QIAOPM-Q-008 | 权限体系分裂的修复方向：对齐到点号体系（改前端路由 `permission`）还是冒号体系（改 DB 权限码 + 角色绑定）？涉及 147 条路由 + 454 条权限码，需架构决策 | 认证授权 / 权限体系（对应 QIAOPM-TD-005；对应 QIAOPM-ISSUE-011；关联 QIAOPM-Q-005 / Q-006） | 第 3.5 章「认证授权与权限」尚未回填（菜单权限 / 按钮权限两行均为 `\[待调查回填]`），**无成文的权限码命名规范可依**；第 0.3 章规定业务预期以经确认的 PRD / 补充需求为准 | 前端 `router/index.ts` 147 条 `meta.permission` 全为冒号体系（0 条点号），其中 `commission:rule:config`、`commission:rule` 在 `sys_permission` 中**不存在**、`commission:rule:list` 存在但**零角色绑定**；DB 侧 `sys_permission` 454 个 distinct 码中点号 74 个（`commission.rules` 等，绑定 role 1/8/6）、冒号 380 个；守卫 `router/index.ts:220-226` 只读 `to.meta.permission`，经 `stores/permission.ts:64-66` 的 `hasPermission` 做 `permissions.includes(code)` 精确匹配，**不查 `menus` 树** → 点号码与冒号码永不相交 | 前端 147 条路由统一用冒号体系，后端 BOSS 角色持有的是点号码，两套体系从不在同一命名空间相交；第 3.5 章尚未确立权限码命名规范，无成文基准可判定哪一套是「正确」的一方 | 两套体系并存导致「有菜单、有角色授权但进不去」的假性 403；BOSS（role id 6）实际持有的 `commission.rules`（点号）与守卫要求的 `commission:rule:config`（冒号）永不相交，**任何角色都无法访问该路由**；修复方向影响 147 条路由 + 454 条权限码 + 角色绑定数据，属全局架构级变更 | 架构 + 产品 + 后端负责人 | 在决策前**不修改任何权限码或路由 `permission`**；`/commission/rules` 维持现状（走 `/403`，见 QIAOPM-ISSUE-011）；先完成 147 条路由 × 454 条权限码的全量对账，产出「前端引用码 vs DB 实际码」差异清单，再定对齐方向 | UNVERIFIED | |

> **关闭条件**：以上 Q 项按第 10.6 节执行——取得证据（运维确认记录 / 实际地址 / 配置截图）后更新 2.5 对应行、在此表填写「结论」列，并把状态改为最终结论（CONFIRMED / CORRECTED）。不得只在聊天中口头关闭。



\---



\## 附录 E：QIAOPM 状态标记与证据清单说明



\### E.1 状态标记



```text

VERIFIED     已实现并端到端验证通过，有证据

PARTIAL      部分实现，主流程可用但存在缺失

BROKEN       实际验证失败

MOCK         假数据/假成功/硬编码/未接真实后端

MISSING      文档要求但实现缺失

UNVERIFIED   未调查或环境阻塞，不能下结论

```



\### E.2 子标签



```text

UI\_ONLY / CODE\_WITHOUT\_UI / DOC\_ONLY / CODE\_ONLY

PERMISSION\_GAP / NON\_MENU\_ENTRY / ENV\_BLOCKED / DOC\_CONFLICT

```



\### E.3 证据清单



\- 截图/录屏

\- 接口请求与响应

\- 数据库查询结果

\- 控制台/服务端日志

\- 命令输出

\- 文档路径与章节

\- 代码文件与行号

\- 环境信息（引用 2.5）

\- 验证人与时间



无证据不得标 `VERIFIED`。



\---



\## 附录 F：QIAOPM 变更影响检查清单



```text

\[ ] 是否影响 QIAOPM 菜单/路由

\[ ] 是否影响共享组件

\[ ] 是否影响公共 API

\[ ] 是否影响数据库字段/约束

\[ ] 是否影响权限/数据范围

\[ ] 是否影响导入导出/报表

\[ ] 是否影响定时任务/后台任务

\[ ] 是否影响第三方回调

\[ ] 是否需要回归关联模块

\[ ] 是否需要更新功能矩阵/验收记录/文档

```



\---



\## 附录 G：QIAOPM 本阶段调查与回填要求（交付检查）



完成本规范落地时，必须输出：



\*\*A. 实际检查过的 QIAOPM 目录、文件和证据\*\*

逐项列出路径、命令、截图或日志来源。



\*\*B. 新建或更新的文档及完整路径\*\*

如 `docs/governance/QIAOPM\_PROJECT\_TECHNICAL\_BASELINE.md`。



\*\*C. 总规范的主要章节与使用方式\*\*

说明 QIAOPM 团队如何查阅、如何用于逐菜单验收。



\*\*D. 当前仍然缺失、冲突或无法确认的 QIAOPM 技术信息\*\*

对应附录 D 登记。



\*\*E. 后续如何通过本规范执行 QIAOPM 逐菜单、逐功能检查和完善\*\*

按第 15 章工作流执行；先选一个模块试点跑通闭环，再全量推广。



\*\*F. 文档自身是否还存在未经核实的内容\*\*

明确列出所有 `\[待调查回填]` / `UNVERIFIED` 项。



\---



\## 文档结束声明



本规范为 \*\*QIAOPM 治理框架 + 项目基线模板\*\*。

其中治理规则自审阅确认后生效；QIAOPM 项目技术事实必须在真实调查后回填，未回填内容不得作为开发、验收或上线依据。

本阶段只建立治理文档，不修改任何 QIAOPM 业务代码、数据库结构、API、依赖或环境配置；完成后停止，等待审阅确认。

```



\---



\## 保存后建议马上做的 3 件事



1\. \*\*提交到 QIAOPM 仓库\*\*：放在 `docs/governance/QIAOPM\_PROJECT\_TECHNICAL\_BASELINE.md`，并在版本记录里填日期和变更人。

2\. \*\*建证据目录\*\*：同时创建 `docs/acceptance/QIAOPM/`，避免后面验收时证据乱放。

3\. \*\*选一个试点模块\*\*：按第 15 章跑一轮“正向清单 → 反向清单 → Cursor 只读 → 人工点击 → 确认 → 小步修 → 回归 → 更新矩阵”，跑通后再全量推广。



下一步如果你把 QIAOPM 的\*\*左侧菜单清单、仓库目录、技术栈、PRD/接口文档路径\*\*发我，我可以帮你把第 2、3、4、5 章直接回填成 QIAOPM 真实版，而不是停留在模板。

