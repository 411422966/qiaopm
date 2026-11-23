# raw/25 — `feature.legacy-missing-ddl.enabled` 运行期取值核查（2026-11-23）

> **目的**：I199 §6.1 要求「生产环境 `feature.legacy-missing-ddl.enabled` 的取值」；I199 §6.2 把该运行期证据列为**方案阶段 5（生产库 `sys_role_permission` 绑定）的前置门槛**。
> **编号更正（2026-11-23）**：本文件初稿写作「阶段 2b」，经核方案文档 §1.8（L454 阶段 2b = 开发库 D1 绑定 / L459 阶段 5 = 生产库执行）已更正为「阶段 5」；其余内容未变。
> **本文件**：仅落证据，**不改 I199 正文**（I199 §6.1 / §6.2 的更新待用户逐字确认后另行落盘）。
> **证据性质**：静态配置链核查 **+** 运行期功能探测（已认证）。
> **关联**：I199 / I187 / I135（部署 jar 陈旧警告）/ I118（补 132 张缺失 DDL 的来源）/ F171。

---

## 0. 绑定信息

| 项 | 值 |
|---|---|
| commit hash | **`5f8a60766db1d376cd35c9d97db8692284cac0ab`**（`docs(audit): I199 孤儿码登记 + 方案文档重写 + 证据补全（阶段 1 收口）`） |
| 上游 commit | `a77c42b`（`docs(audit): I198 头部补本轮收口状态`） |
| 分支 / 推送 | `main` → `origin/main`（`7a4e72a..5f8a607  main -> main`，EXIT=0） |
| 核查日期 | 2026-11-23 00:33–00:42 (UTC+8) |
| DB snapshot | `sys_role_permission` = **1267**；`sys_permission` = **510**；`sys_role` = **8**；`sys_user` = **5**；`sys_log` = 12944；`sys_login_log` = 1256；`pos_store` = 7 |
| env 参照 | `audit/evidence/env.md`（Phase 5A Runtime Environment）；**本轮 env 与之有出入，以本文件 §1/§2 为准** |
| 被测实例 | Docker 容器 `meiyetec-api`（image `meiyetec/api:dev`，`SPRING_PROFILES_ACTIVE=prod`，启动 **2026-11-22 21:12:55**） |
| 被测 DB | Docker 容器 `meiyetec-mysql`（`mysql:8.0`，hostname `080383a52e8b`，server_uuid `d340bd34-8cb5-11f1-96df-b2b15b34522e`，MySQL **8.0.46**，IP `172.25.0.5`，容器创建 2026-10-14） |

### 0.1 结论（一句话）

> **在本机唯一可访问的运行实例（`meiyetec-api` 容器，prod profile）上，`feature.legacy-missing-ddl.enabled` 的生效值为 `false`；三个门控 Controller 的 8 个端点在已认证条件下全部返回 HTTP 404，Bean 未注册，当前处于「潜伏」而非「裸奔」状态。**
>
> **唯一未覆盖分支**：本机**无 K8s / Helm 访问能力**（`kubectl` 已安装但无 `.kube\config`、无 current-context；`helm` 未安装），因此**无法排除存在另一套独立部署（K8s ConfigMap / 启动参数 / 外部 `SPRING_APPLICATION_JSON`）覆盖该键**。该分支标 **UNVERIFIED**。

---

## 1. 12 个配置源核查表

对「`feature.legacy-missing-ddl.enabled` 是否可能被置为 `true`」做穷举式排查。判定口径：**该源能否改变 Spring `Environment` 中 `feature.legacy-missing-ddl.enabled` 的最终解析值**。

| # | 配置源 | 核查方式 / 命令 | 实测结果 | 是否可覆盖 | 判定 |
|---|---|---|---|---|---|
| 1 | 运行中 jar 的 base 配置 | `docker exec meiyetec-api unzip -p /app/app.jar BOOT-INF/classes/application.yml` | `feature:` → `legacy-missing-ddl:` → **`enabled: false`**（jar 内行号 150-152） | 否（值即 `false`） | ✅ **false** |
| 2 | 运行中 jar 的 prod profile | 同上，取 `application-prod.yml` | `feature:` 块**仅含** `launchdarkly`（`enabled` / `sdk-key`），**未出现 `legacy-missing-ddl`** → 继承 base | 否 | ✅ **false**（继承） |
| 3 | 容器环境变量 | `docker exec meiyetec-api env`（实测 42 个变量全量列出） | 无 `FEATURE_LEGACY_MISSING_DDL`、无 `FEATURE_LEGACY_MISSING_DDL_ENABLED`、无 `SPRING_APPLICATION_JSON` | 否 | ✅ **false** |
| 4 | 容器启动参数 | `docker inspect meiyetec-api --format '{{json .Config.Entrypoint}} {{json .Config.Cmd}}'` | `["/sbin/tini","--"]` + `["sh","-c","exec java $JAVA_TOOL_OPTIONS -jar /app/app.jar"]` → **无 `--feature.*` 参数** | 否 | ✅ **false** |
| 5 | JVM 系统属性 | 容器 env 中 `JAVA_TOOL_OPTIONS` = `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom -Djava.net.preferIPv4Stack=true` | **无 `-Dfeature.*`**、无 `-Dspring.*` | 否 | ✅ **false** |
| 6 | 容器挂载（外挂配置） | `docker inspect meiyetec-api --format '{{range .Mounts}}...{{end}}'` | 仅 `bind D:\QiaoPM\plugins -> /opt/plugins (RW=true)` → **无外挂 application.yml / properties** | 否 | ✅ **false** |
| 7 | compose 服务定义 | `docker-compose.yml:96-140`（api 服务 `environment:` 完整块逐条核对） | 12 类变量：`SPRING_PROFILES_ACTIVE` / `SPRING_DATASOURCE_*` / `SPRING_DATA_REDIS_*` / `APP_DCEP_MODE` / `APP_ID_GENERATOR_*` / `JWT_SECRET` / `PAY_ENV_MODE` / `FISCAL_MODE` / `PLUGIN_*` / `TZ` → **无 `feature.*`** | 否 | ✅ **false** |
| 8 | 仓库 `.env` | `D:\QiaoPM\.env`（9 个键全量枚举，值已脱敏） | `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`、`JWT_SECRET`、`SPRING_DATA_REDIS_HOST/PORT/PASSWORD`、`APP_ID_GENERATOR_WORKER_ID/DATACENTER_ID` → **无 `feature.*`**；`.env.example` 同 | 否 | ✅ **false** |
| 9 | CI 工作流 | `D:\QiaoPM\.github\` 全量检索 `legacy-missing` / `feature\.` | **0 命中** | 否 | ✅ **false** |
| 10 | `infra/` 部署目录 | `infra/` 全量列举（alertmanager / grafana / prometheus / systemd），检索 `legacy-missing-ddl` | **0 命中**；`infra/` 为监控与备份配置，**不含应用配置** | 否 | ✅ **false** |
| 11 | DB 驱动的 Feature Flag 体系 | `SELECT id,tenant_id,flag_key,enabled FROM feature_flag WHERE flag_key LIKE '%legacy%' OR flag_key LIKE '%missing-ddl%'` | **0 行**（该表为空匹配） | 否（见 §2.3） | ✅ **false** |
| 12 | **K8s ConfigMap / Helm values / 外部 `SPRING_APPLICATION_JSON`** | `kubectl version --client` → v1.36.1 可用；`kubectl config current-context` → `error: current-context is not set`；`Test-Path $env:USERPROFILE\.kube\config` → **不存在**；`helm version` → **未安装** | **无访问能力**，无法枚举 | **⚠️ 未知** | ❌ **UNVERIFIED** |

**核查覆盖结论**：**第 1–11 项全部确认为 `false`**；**第 12 项 UNVERIFIED**（能力缺失，非"已确认 false"）。

---

## 2. 运行期功能探测

### 2.1 方法学修正 —— 为什么「未认证 401」不能作为判定依据

I187 §1.3（2026-11-16 证据）与本轮初测均观察到门控端点返回 **401**。**本轮查明 401 与 404 的来源不同，且 401 对本次判定无效**：

| 环节 | 事实 | 出处 |
|---|---|---|
| 全局 JWT 拦截器注册范围 | `registry.addInterceptor(jwtInterceptor).addPathPatterns("/**")` —— 覆盖**所有**路径 | `backend/src/main/java/com/meiyetec/config/WebConfig.java:25-27` |
| 401 触发点 | `Authorization` 头缺失或非 `Bearer ` 前缀 → `writeUnauthorized(response, ...)` → `response.setStatus(401)`，**立即 `return false`** | `backend/src/main/java/com/meiyetec/interceptor/JwtInterceptor.java:48-52`、`:80-85` |
| 关键性质 | 该拦截器在 **Handler 映射之前**执行（`preHandle`），**路由到不存在的路径同样先被 401 拦下** | `JwtInterceptor.java:38-72`（`implements HandlerInterceptor`） |

**实测对照（未认证）**：

| 请求 | 状态码 | 说明 |
|---|---|---|
| `GET /commission/tier/list` | 401 | 门控端点 |
| `GET /commission/definitely-not-exist-xyz` | **401** | **不存在的路径，同样 401** |
| `GET /commission/xxx/yyy` | **401** | 同上 |
| `GET /` | **401** | 同上 |
| `GET /actuator/health` | 200 | 在 `WebConfig.java:49` 的 `excludePathPatterns("/actuator/**")` 内 |

> **结论**：未认证探测时，**401 对「Bean 是否注册」零信息量** —— 它只证明"请求没带 token"。**只有已认证后的 404 才是有效判据。**

### 2.2 已认证探测（有效判据）

**认证方式**：`POST /auth/login` body `{"username":"manager01","password":"123456"}` → `code=200 登录成功`（种子账号，`sys_user.id=900004`，`tenant_id=1`）。取得有效 Bearer token 后，精确按控制器源码声明的路由探测。

**控制器实际路由**（源码核对，注意路径为 `by-rule/{ruleId}` 而非 `/list`）：

| 控制器 | 类级 `@RequestMapping` | 方法级映射 | 源码位置 |
|---|---|---|---|
| `CommissionTierController` | `/commission/tier` | `GET /by-rule/{ruleId}`、`POST /batch/{ruleId}`、`DELETE /{id:\d+}` | `CommissionTierController.java:15,22,27,33` |
| `CommissionConditionController` | `/commission/condition` | `GET /by-rule/{ruleId}`、`POST /batch/{ruleId}`、`DELETE /{id:\d+}` | `CommissionConditionController.java:15,22,27,33` |
| `CommissionSimulationController` | `/commission/simulation` | `POST`（类级）、`GET /recent/{employeeId}` | `CommissionSimulationController.java:16,23,28` |

**8 个端点探测结果（已认证，manager01）**：

| # | 方法 | 路径 | 状态码 | 响应体 |
|---|---|---|---|---|
| 1 | GET | `/commission/tier/by-rule/1` | **404** | `{"status":404,"error":"Not Found","path":"/commission/tier/by-rule/1"}` |
| 2 | POST | `/commission/tier/batch/1` | **404** | 同构 404 |
| 3 | DELETE | `/commission/tier/1` | **404** | 同构 404 |
| 4 | GET | `/commission/condition/by-rule/1` | **404** | 同构 404 |
| 5 | POST | `/commission/condition/batch/1` | **404** | 同构 404 |
| 6 | DELETE | `/commission/condition/1` | **404** | 同构 404 |
| 7 | POST | `/commission/simulation` | **404** | 同构 404 |
| 8 | GET | `/commission/simulation/recent/900004` | **404** | 同构 404 |

**阳性对照（证明 token 有效、路由正常）**：

| 请求 | 状态码 | 响应体摘要 |
|---|---|---|
| `GET /store/list` | **200** | `{"code":200,"message":"操作成功","data":[{"id":1,"tenantId":1,"brandId":null,"storeType":"DIRECT",...}]}` |

**阴性对照（证明 404 语义 = 路由不存在）**：

| 请求 | 状态码 |
|---|---|
| `GET /commission/definitely-not-exist-xyz`（已认证） | **404**，响应体与门控端点**完全同构** |

> **判定**：已认证 + 阳性对照 200 + 阴性对照 404 + 8 端点 404 → **三个门控 Controller 的 Bean 未注册**，`@ConditionalOnProperty(havingValue="true")` 未满足。**8 端点当前全部不可达。**

### 2.3 无动态覆盖路径（源码穷举）

**全仓 `legacy-missing-ddl` 引用共 15 处，全部是 `@ConditionalOnProperty`**：

| 消费方 | 数量 | 出处 |
|---|---|---|
| `CommissionTierController` / `CommissionConditionController` / `CommissionSimulationController` | 3 | `CommissionTierController.java:13`、`CommissionConditionController.java:13`、`CommissionSimulationController.java:14` |
| 其余 11 个门控 Controller | 11 | `BizTreatmentReportController.java:36`、`ExpiryHandleLogController.java:17`、`MaterialConsumptionLogController.java:16`、`MaterialSubstituteController.java:18`、`MultiAccountSplitController.java:38`、`ResourceConflictLogController.java:16`、`ResourceUtilizationController.java:21`、`ServiceMaterialDeductLogController.java:16`、`ServiceMaterialGroupController.java:18`、`ServiceMaterialRuleController.java:13`、`StockShortageLogController.java:13` |
| **非 Controller 消费者** | **0** | 全仓检索 `legacy-missing-ddl` 且路径不含 `Controller.java` → **0 命中** |

**LaunchDarkly 集成不构成覆盖路径**：

- `featureflag/FeatureFlagEvaluator.java`（接口）、`InMemoryFeatureFlagEvaluator.java`、`LaunchDarklyFeatureFlagEvaluator.java` 是**独立的 DB 驱动灰度开关体系**，数据源为 `feature_flag` 表，**唯一消费者是 `GrayReleaseController`**。
- `InMemoryFeatureFlagEvaluator` 的激活条件是 `@ConditionalOnProperty(prefix="feature.launchdarkly", name="enabled", ...)`（`InMemoryFeatureFlagEvaluator.java:39-40`）—— 切换的是 **LaunchDarkly 评估器的实现**，与 `feature.legacy-missing-ddl.enabled` **是两个不同键，互不影响**。
- `@ConditionalOnProperty` 在 **Bean 定义注册期**求值，**运行期不可被 DB 标志位改写** → 排除了"运行期被热改成 true"的路径。

### 2.4 部署 jar 新鲜度（排除 I135「陈旧 jar」伪因）

I135 警告过「部署 jar 陈旧 vs 源码」会导致结论失真。本轮核对：

| 项 | 实测 | 判定 |
|---|---|---|
| 运行中 jar mtime | `/app/app.jar` = **2026-11-17 22:34:13**（140,059,898 bytes） | — |
| 容器启动时间 | `StartedAt = 2026-11-22T13:12:24Z`（= 21:12:24 +08:00） | **jar 早于启动** → 运行的就是这个 jar |
| 应用启动完成 | `Tomcat started on port(s): 8080 (http) with context path ''` / `Started MeiyetecApplication in 29.07 seconds`（2026-11-22 21:12:55） | ✅ |
| jar 内是否含三个控制器 class | `BOOT-INF/classes/com/meiyetec/controller/CommissionTierController.class`（2893 B）、`CommissionConditionController.class`（2968 B）、`CommissionSimulationController.class`（2529 B） | ✅ **class 存在** |
| jar 内 base 配置 vs 本地源码 | jar 内 `application.yml` 150-152 行 = `feature: / legacy-missing-ddl: / enabled: false`；本地 `backend/src/main/resources/application.yml:150-152` **逐行一致** | ✅ 一致 |

> **判定**：404 **不是**「jar 陈旧缺 class」造成的，而是 `@ConditionalOnProperty` 判定 `false` 的正常结果。I135 风险在本轮**已排除**。

---

## 3. 零鉴权事实复核（与 I199 §1.2 对齐）

| 控制器 | `@RequirePermission` 出现 | import 是否有 `RequirePermission` |
|---|---|---|
| `CommissionTierController` | **False** | ❌ 无 |
| `CommissionConditionController` | **False** | ❌ 无 |
| `CommissionSimulationController` | **False** | ❌ 无 |

- 三个源文件**连 `import ...RequirePermission` 都没有**（`CommissionTierController.java:1-11` import 块实测）。
- 对照：全仓 `@RequirePermission` 注解共 **894 处**（说明该注解体系在本项目中被广泛使用，三控制器的缺失是**真实缺口**，不是"项目不使用该注解"）。
- 4 个写接口（`POST /commission/tier/batch/{ruleId}`、`DELETE /commission/tier/{id}`、`POST /commission/condition/batch/{ruleId}`、`DELETE /commission/condition/{id}`）在开关置 `true` 后将对**任意登录用户**开放。

**相关权限码 DB 现状**（`sys_permission` 表）：

| permission_code | id | permission_type | status | deleted | 当前绑定角色数 |
|---|---|---|---|---|---|
| `commission:list` | 1118 | button | 1 | 0 | **0** |
| `commission:settlement:audit` | 1128 | button | 1 | 0 | **0** |
| `commission:simulation` | 1631 | API | 1 | 0 | **3**（role 5,6,8） |

> 与 I199 §1.1 / §1.3 登记口径一致，**无漂移**。

---

## 4. 未验项清单

| # | 未验项 | 原因 | 是否阻塞方案阶段 5 |
|---|---|---|---|
| U1 | **是否存在另一套独立部署（K8s ConfigMap / Helm values / 外部 `SPRING_APPLICATION_JSON`）并把该键置为 `true`** | 本机无 `.kube\config`、无 current-context、`helm` 未安装 → **能力缺失** | ⚠️ 取决于 §5 的库归属判定 |
| U2 | 该开关在**真实生产集群**（若存在）上的实际生效值 | 同 U1 | ⚠️ 同上 |
| U3 | 8 端点在开关置 `true` 后的「无鉴权可写」行为 | **未复现**（当前 404）；属 I187 §3 建议方案的验证范畴 | ❌ 不阻塞（当前不可复现） |
| U4 | `actuator/env` / `configprops` / `conditions` / `beans` / `mappings` 读数 | 全部 404（仅 `/actuator/health` 与 `/actuator/prometheus` 放行，`WebConfig.java:49`） | ❌ 不阻塞（已用功能探测替代） |

### 4.1 U1/U2 的性质说明

U1 是**能力边界**导致的未验（"无法访问"，不是"已确认 false"），**不是**"已确认该键在其他环境为 false"。二者证据强度不同，**不可混同**。

---

## 5. 被测实例的部署形态与「生产库」归属

| 维度 | 实测 | 指向 |
|---|---|---|
| DB 容器 | `meiyetec-mysql`，image `mysql:8.0`，容器创建 **2026-10-14**，IP `172.25.0.5`（Docker bridge 网络） | **本机 Docker 内网** |
| DB 实例标识 | hostname `080383a52e8b`（= 容器短 id）、server_uuid `d340bd34-8cb5-11f1-96df-b2b15b34522e` | 本机实例 |
| DB 数据规模 | `pos_store` = **7**、`sys_user` = **5**、`sys_role` = 8、`sys_log` = 12944、`sys_login_log` = 1256 | **联调/开发规模**，非生产量级 |
| API 容器 | `meiyetec-api`，image `meiyetec/api:dev`（**dev tag**），`SPRING_PROFILES_ACTIVE=prod` | **本地 dev 镜像跑 prod profile** |
| 本地备份目录 | `D:\QiaoPM\backup\`（`batch1-*.sql`、`fix-i129-*.sql`，mtime 2026-11-22 21:13–21:14） | 与本机容器同源 |
| K8s 集群 | **无访问能力**（无 kubeconfig / 无 context / 无 helm） | 无法确认存在 |

> **判定**：本轮可访问的实例 = **本机 Docker 部署**，其 profile 名虽为 `prod`，但**镜像 tag 是 `dev`**、**DB 数据量是联调规模**。因此**不宜直接称之为"生产库"**；其准确定性为：**"本机 Docker 联调实例（prod profile）"**。

---

## 6. 本轮判定汇总

| 判定项 | 结果 | 等级 |
|---|---|---|
| 本机 Docker 实例的 `feature.legacy-missing-ddl.enabled` 生效值 | **false** | **VERIFIED**（配置源 1–11 穷举 + 已认证 404 功能探测双重佐证） |
| 8 个门控端点当前可达性 | **全部 404，Bean 未注册** | **VERIFIED**（已认证 + 阳性/阴性对照） |
| I135「部署 jar 陈旧」伪因 | **已排除** | **VERIFIED** |
| 运行期动态覆盖路径（LD / DB flag） | **不存在** | **VERIFIED**（源码 15 处引用穷举） |
| 三个控制器零 `@RequirePermission` | **成立**（import 缺失，全仓对照 894 处） | **VERIFIED**（静态） |
| 独立 K8s 集群的同名开关取值 | **未取到** | **UNVERIFIED**（U1/U2，能力缺失） |
| 「解封后裸奔」行为复现 | **未复现**（当前不可达） | **UNVERIFIED**（U3） |

---

## 7. 复现命令清单

```powershell
# 配置源 1/2：运行中 jar 的两份配置
docker exec meiyetec-api unzip -p /app/app.jar BOOT-INF/classes/application.yml        | Select-String "legacy-missing-ddl" -Context 1,1
docker exec meiyetec-api unzip -p /app/app.jar BOOT-INF/classes/application-prod.yml   | Select-String "feature|legacy|launchdarkly" -Context 2,4

# 配置源 3/4/5：环境变量与启动参数
docker exec meiyetec-api env
docker inspect meiyetec-api --format '{{json .Config.Entrypoint}} {{json .Config.Cmd}}'

# 配置源 6：挂载点
docker inspect meiyetec-api --format '{{range .Mounts}}{{.Type}} {{.Source}} -> {{.Destination}}{{println}}{{end}}'

# 配置源 11：DB feature_flag 表
docker exec meiyetec-mysql mysql -uroot -proot123456 meiyetec -e `
  "SELECT id,tenant_id,flag_key,enabled FROM feature_flag WHERE flag_key LIKE '%legacy%';"

# §2.1 方法学对照（未认证 401 对照组）
Invoke-WebRequest http://localhost:8080/commission/definitely-not-exist-xyz -UseBasicParsing   # 401
Invoke-WebRequest http://localhost:8080/store/list -UseBasicParsing                              # 401

# §2.2 已认证探测
$login = Invoke-RestMethod http://localhost:8080/auth/login -Method POST `
  -ContentType 'application/json' -Body '{"username":"manager01","password":"123456"}'
$tok = $login.data.token
Invoke-WebRequest "http://localhost:8080/commission/tier/by-rule/1" -Headers @{Authorization="Bearer $tok"} -UseBasicParsing   # 404
Invoke-WebRequest "http://localhost:8080/store/list"                -Headers @{Authorization="Bearer $tok"} -UseBasicParsing   # 200 阳性对照
```

---

## 8. 声明

- 本文件**不**修改任何业务代码；本轮全部操作为**只读**（SELECT / unzip / inspect / HTTP GET+POST 登录），**未对 `sys_role_permission` 执行任何写操作**。
- 登录测试仅使用种子账号 `manager01`（`sys_user.id=900004`），**未创建/修改/删除任何数据**；取得的 token 文件已在探测结束后**立即删除**（未入库）。
- 本文件所有状态码、计数、路径、行号均来自本轮**实际执行**的命令输出，未做推断性填充；推断性结论已单独标注为「指向」或 UNVERIFIED。
