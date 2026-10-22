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



\### 4.2 正向清单模板（UI → 文档 → 代码）



| 菜单层级 | 菜单名称 | 路由 | 页面组件 | 功能点 | UI 入口 | 前端 API | 后端接口 | 数据表 | PRD 章节 | 状态 |

|---|---|---|---|---|---|---|---|---|---|---|

| `\[待回填]` | `\[待回填]` | `\[待回填]` | `\[待回填]` | `\[待回填]` | `\[待回填]` | `\[待回填]` | `\[待回填]` | `\[待回填]` | `\[待回填]` | UNVERIFIED |



\### 4.3 反向清单模板（文档 → UI → 代码）



| PRD 章节 | 文档描述的功能点 | 期望菜单/入口 | 实际 UI 是否存在 | 前端 API | 后端接口 | 数据表 | 差异类型 | 状态 |

|---|---|---|---|---|---|---|---|---|

| `\[待回填]` | `\[待回填]` | `\[待回填]` | 是/否/待确认 | `\[待回填]` | `\[待回填]` | `\[待回填]` | DOC\_ONLY / UI\_ONLY / CODE\_ONLY / 一致 | UNVERIFIED |



\### 4.4 映射规则



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

> **已关闭**：原调查项 ISSUE-5（仓库根 `.env` 敏感文件风险）—— 经人工确认，`.env` 未进 git 历史、已被 `.gitignore` 忽略、未被跟踪，**关闭**，不再登记为 ISSUE。

\#### D.2 技术债（TD）

| 编号 | 级别 | 问题 | 涉及模块 | 文档说法 | 代码事实（证据） | 影响 | 建议处理 | 状态 |
|---|---|---|---|---|---|---|---|---|
| QIAOPM-TD-001 | P2 | `backend/` 根目录堆积 300+ 个临时日志 / 脚本 / 编译参数产物 | 仓库卫生 | 仓库根与 `backend/` 根应为源码与配置的规范结构 | `backend/` 根下有 300+ 个 `*.log` / `*.txt` / `compile_*.ps1` / `javac_args*.txt` / 临时 `*.sql`；仓库根另有 `out*.log`、`check_servers.ps1`、`check_v2.ps1` | 污染目录结构事实（已登记到 2.1）；干扰新人定位源码；`.gitignore` 覆盖不全时有误提交风险 | 归档到 `backup/` 或删除；补齐 `.gitignore` 规则；清理后更新 2.1 目录表 | OPEN |

\#### D.3 待确认问题（Q）

| 编号 | 问题 | 涉及模块 | 文档说法 | 代码事实 | 冲突点 | 影响 | 待确认人 | 临时处理 | 状态 | 结论 |
|---|---|---|---|---|---|---|---|---|---|---|
| QIAOPM-Q-001 | 测试环境（staging）是否存在？若存在，请提供前端 / 后端地址、DB 名与可用账号（admin / 普通用户 / 跨租户角色） | 环境矩阵 / 部署 | `docs/DEPLOY-SOP.md` §0 提及环境含 main / staging | 仓库无 staging 编排文件、`application.yml` 无 staging profile、无地址清单 | 文档暗示有 staging，仓库无任何可佐证事实 | 2.5 环境矩阵 staging 整行 UNVERIFIED；无法在此环境做验证 | 运维 | 2.5 staging 行保持 UNVERIFIED，不得用于验收 | UNVERIFIED | |
| QIAOPM-Q-002 | 生产真实域名（CloudFront 前端域名、nginx 后端入口）、真实 DB / Redis 主机分别是什么？ | 环境矩阵 / 部署 | `docs/prod-env-manifest.md` §2-§4 为环境变量事实源 | `.env.example` 全为占位（`prod-rds-host` / `prod-redis-host`）；`application-prod.yml` 强制 ENV 注入不落盘 | 流程文档完整，但真实地址无任何仓库内来源 | 2.5 生产"真实地址"行 UNVERIFIED；生产验收无法定位入口 | 运维 | 2.5 生产真实地址行保持 UNVERIFIED；验收记录不得写具体生产地址 | UNVERIFIED | |
| QIAOPM-Q-003 | Kafka 是否实际部署？ | 部署 / 消息队列 | `DEPLOY-SOP.md` §4 监控表列"Kafka / MQ 积压 < 100" | 代码零 MQ 依赖（见 QIAOPM-ISSUE-003 证据） | 文档有监控项，代码无对应组件 | 若确未部署 → 监控表为假能力需删除；若确有部署 → 监控表与代码脱节，需补接入路径 | 运维 | 2.2 "消息队列"行按"代码零 MQ 依赖 = CONFIRMED"记录，不宣称有 Kafka | UNVERIFIED | |
| QIAOPM-Q-004 | Node 版本以 20 为准还是 18+ 为准？ | 前端构建 / 工程规范 | `README.md:45` 写"Node.js 18+" | `ci.yml:73,114,184` 用 `node-version: '20'`；`frontend/meiyetec-web/Dockerfile` 用 `node:20-alpine`；仓库无 `.nvmrc` | 文档口径 18+，CI 与镜像口径 20 | 新人按 18+ 装环境可能与 CI 不一致，导致"本地过、CI 挂" | 前端负责人 | 2.4.1 本地前端行标 PARTIAL 并注明口径冲突，不擅自选定版本 | UNVERIFIED | |

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

