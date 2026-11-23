# I199 — 提成模块 2 个孤儿权限码（0 绑定）+ tier/condition/simulation 3 个控制器 8 个端点零鉴权

> **状态**：**Open（仅登记，不修）** —— tier/condition 补码 + 加 `@RequirePermission` 注解**并入 I187 §3 建议方案步骤 1**，不另立独立批次；本条不重复登记端点本身
> **严重度**：**P2**（孤儿码本身不造成运行时越权；但 8 个无鉴权端点属于**鉴权缺口**，按角色边界应为 P1 候选 —— 见 §3 严重度说明）
> **发现日期**：2026-11-22（I183/I184 权限绑定批次 D3 决策时识别）
> **登记人**：Cursor
> **绑定基线**：HEAD `a77c42b`（`docs(audit): I198 头部补本轮收口状态`）
> **验证等级**：**静态 VERIFIED**（开发库实时 `SELECT` + 全仓源码检索 `backend/src/main/java`；**运行期未验**）
> **关联**：**I183**（考勤 9 码 0 绑定）、**I184**（提成 24 码 0 绑定）、**I185**（前后端权限码双轨）、**I187**（提成端点无鉴权 —— 端点层面以 I187 为准，本条见 §4 分工）、**I188**（种子与运行期漂移）
> **来源**：`audit/reports/i183-i184-perm-binding-plan.md` §4.1「D3 落地：孤儿码登记内容（I199 定稿素材）」—— 本文件 §0–§1.2 主体为该节的逐字摘出
> **段落性质声明**：本文件 §1.1 检索口径、§1.2 逐端点清单、§1.3、§2–§5 为**证据补全段落**，非 §4.1 原文，用户于 2026-11-22 追认

---

## 0. 一句话描述

提成模块存在 **2 个孤儿权限码**（在 `sys_permission` 中但后端零引用、**保持 0 绑定**），
以及 **3 个控制器共 8 个端点完全无鉴权**（`@RequirePermission` 计数为 0）——
其中 `commission:simulation` 权限码**已建码并绑定 5/6/8**，但后端**未消费该码**，形成「有码无注解」的悬空状态。

---

## 1. 事实核实

### 1.1 孤儿码（2 个）

| 码 | `permission_id` | 事实 | 处置 |
|---|---|---|---|
| `commission:list` | 1118 | 在 `sys_permission`，但**后端无任何 `@RequirePermission("commission:list")` 引用**（全仓检索 0 处命中）；`CommissionController` 实际使用 `commission:record:list` | **不绑定**；登记为孤儿码 |
| `commission:settlement:audit` | 1128 | 同上，全仓检索 0 处命中；结算审核实际走 `commission:settlement:confirm` | **不绑定**；登记为孤儿码 |

**检索口径**：对 `backend/src/main/java` 全量 `.java` 递归检索正则
`RequirePermission\(\s*"commission:list"\s*\)` 与 `RequirePermission\(\s*"commission:settlement:audit"\s*\)`
→ **命中数各为 0**。

**绑定期望**：2 个孤儿码在 `sys_role_permission` 中的绑定数**应为 0 且实到为 0**（D3 决策：绑定它们不会让任何端点放行，只会制造虚假的授权假象）。

### 1.2 无鉴权端点（8 个 / 3 个控制器）

三个控制器 `@RequirePermission` 注解计数**均为 0**：

| 控制器 | 文件 | `@RequirePermission` 计数 | 端点数 |
|---|---|---|---|
| `CommissionTierController` | `backend/src/main/java/com/meiyetec/controller/CommissionTierController.java` | **0** | 3 |
| `CommissionConditionController` | `backend/src/main/java/com/meiyetec/controller/CommissionConditionController.java` | **0** | 3 |
| `CommissionSimulationController` | `backend/src/main/java/com/meiyetec/controller/CommissionSimulationController.java` | **0** | 2 |

**逐端点清单**：

| # | 控制器 | 行号 | 路由 | 方法 |
|---|---|---|---|---|
| 1 | `CommissionTierController` | L22 | `/commission/tier/by-rule/{ruleId}` | GET |
| 2 | `CommissionTierController` | L27 | `/commission/tier/batch/{ruleId}` | POST |
| 3 | `CommissionTierController` | L33 | `/commission/tier/{id:\d+}` | DELETE |
| 4 | `CommissionConditionController` | L22 | `/commission/condition/by-rule/{ruleId}` | GET |
| 5 | `CommissionConditionController` | L27 | `/commission/condition/batch/{ruleId}` | POST |
| 6 | `CommissionConditionController` | L33 | `/commission/condition/{id:\d+}` | DELETE |
| 7 | `CommissionSimulationController` | L23 | `/commission/simulation` | POST |
| 8 | `CommissionSimulationController` | L28 | `/commission/simulation/recent/{employeeId}` | GET |

**关联的第二个缺口（本批同批登记）**：`CommissionTierController`（3 端点）、`CommissionConditionController`（3 端点）、`CommissionSimulationController`（2 端点）三个控制器**零 `@RequirePermission` 注解** —— `commission:tier:*` / `commission:condition:*` 权限码在 `sys_permission` 中不存在，且后端无任何等价码（§1.1-C C2/C3）。D5a 已为 `commission:simulation` 建码（1631）并绑定，但后端未加注解 → 该码当前**无端点消费**。

### 1.3 `commission:simulation` 的悬空状态

| 项 | 值 | 事实来源 |
|---|---|---|
| 权限码 | `commission:simulation` | `sys_permission` |
| `permission_id` | **1631** | 开发库实时 `SELECT` |
| `permission_type` | `API` | 开发库实时 `SELECT` |
| 绑定角色 | **5, 6, 8** | `21-*.out.txt` 执行后状态 / `24-*.out.txt` V2 逐码核验 |
| 后端消费点 | **无** | `CommissionSimulationController` 的 `@RequirePermission` 计数 = 0 |

→ 该码**已入库、已绑定、已分配角色**，但**没有任何端点校验它** → 授权数据与鉴权实现之间存在**契约断裂**（有码无注解）。

---

## 2. 与 I183/I184 批次的关系

本条内容来自 `audit/reports/i183-i184-perm-binding-plan.md` §4.1，为该节的**逐字摘出**。

在 I183/I184 批次中的处置：

| 缺口 | 批次内处置 | 状态 |
|---|---|---|
| `commission:list`(1118) / `commission:settlement:audit`(1128) | **不绑定**（D3 决策），保持 0 绑定 | 已在本批次定稿，登记到本条 |
| `commission:tier:*` / `commission:condition:*` 权限码 | **不补码**（不在本批次范围） | 未处理 |
| 8 个端点补 `@RequirePermission`（并入 I187 §3 步骤 1） | **未授权、未执行** | 未处理 |
| `commission:simulation` 建码 + 绑 5,6,8（D5a） | ✅ **已执行**（`permission_id=1631`） | 已执行，但缺后端消费 |

---

## 3. 风险与严重度说明

### 3.1 孤儿码风险（低）

- 2 个孤儿码**绑定数 = 0** → 运行时**不会**因为它们而多放行任何请求。
- 真实风险是**认知陷阱**：看到 `sys_permission` 里存在 `commission:list` 会误以为「提成列表」已受控，实际后端用的是 `commission:record:list`。
- 处置建议（未执行）：**保留码、不绑定**，或按治理决策**删除 / 标记 deprecated**（需单独批准改数据）。

### 3.2 无鉴权端点风险（中—高，但当前为「潜伏」而非「正在流血」）

- 8 个端点**不受任何 `@RequirePermission` 约束** → 一旦生效，任何已登录租户用户均可调用。
- 涉及**写操作**的有 4 个：`POST /commission/tier/batch/{ruleId}`、`DELETE /commission/tier/{id}`、`POST /commission/condition/batch/{ruleId}`、`DELETE /commission/condition/{id}`。
- ⚠️ **当前运行期不可达（2026-11-22 复核仍成立）**：三个控制器类级均带
  `@ConditionalOnProperty(name = "feature.legacy-missing-ddl.enabled", havingValue = "true", matchIfMissing = false)`
  （`CommissionTierController.java:13`、`CommissionConditionController.java:13`、`CommissionSimulationController.java:14`），
  而 `backend/src/main/resources/application.yml:151-152` 为 `enabled: false`
  → **Bean 不注册 → 路由 404**。I187 §1.3 已实测全部 404（admin / manager / boss / clerk 一致）。
  → **本条的准确表述是「解封即裸奔」，不是「当前正在裸奔」**；一旦有人为补 132 张缺失 DDL（I118）
  而把开关置 `true`，4 个写接口将**同时上线且不带任何鉴权**（I187 §2 R1/R2）。
- ⚠️ **运行期未验证**：本条结论来自**静态源码检索**（`@RequirePermission` 计数 = 0、类级门控注解、`application.yml` 取值）。
  **未**通过实际 HTTP 请求确认这些端点当前确实返回 404 —— 按验收规则「无运行证据只能标 UNVERIFIED」，
  该行为维度标 **UNVERIFIED**（I187 §1.3 的 404 实测为 2026-11-16 证据，本轮未复测）。

### 3.3 与 I186 的交互

`GET /commission/simulation/recent/{employeeId}` 按 `employeeId` 取数，**是否叠加数据范围（tenant / store）过滤**取决于 `CommissionSimulationService` 内部实现 —— 该服务层**未纳入本条核查范围**，其数据范围行为标 **UNVERIFIED**。本条**不重复** I186 的数据范围议题。

---

## 4. 与 I187 的分工

I187（`audit/issues/I187-commission-gated-controllers-no-auth.md`）已登记「tier/condition/simulation 3 控制器 8 endpoint 零 `@RequirePermission`」，
其内容与本条 §1.2 **在端点层面重叠**。为避免重复登记，两条的分工如下：

| 项 | I187 | I199 |
|---|---|---|
| 8 端点无鉴权 | **原始登记**（含门控分析、404 实测、6 项风险清单、解封前置顺序） | **复核当前状态仍成立**（2026-11-22：3 控制器 `@RequirePermission` 计数仍为 0、类级 `@ConditionalOnProperty` 门控仍在、`application.yml:151-152` 仍为 `enabled: false`），**不重复登记端点本身** |
| 2 个孤儿码（`commission:list` 1118 / `commission:settlement:audit` 1128） | **无**（已全文检索确认：I187 中 0 处提及 `commission:list` / `settlement:audit` / `1118` / `1128` / 「孤儿」） | **新增登记（I199 独占）** |
| `commission:simulation` 悬空 | **部分涉及**（§3 建议方案步骤 2「补 `commission:simulation` 码入库」—— 当时该码尚未入库） | **补充当前状态**：`commission:simulation` 已入库（`permission_id=1631`、`permission_type='API'`）+ 已绑 role 5,6,8，但后端消费点仍为 0 → 「有码无注解」悬空状态 |

**结论**：端点层面的问题**以 I187 为准**（更完整、含运行期证据与解封前置条件）；
本条 I199 的**独占价值**是 2 个孤儿码的登记 + `commission:simulation` 建码后的悬空状态复核。

---

## 5. 建议处置（均需单独批准，本轮不执行）

| # | 处置项 | 类型 | 前置条件 |
|---|---|---|---|
| R1 | 为 8 个端点补 `@RequirePermission` —— **与 I187 合并修复，不另立条目**（tier/condition 需先补码，simulation 用已有码 1631） | 代码 + 数据 | 补码口径拍板；**并入 I187 §3 建议方案步骤 1**，不另立独立批次 |
| R2 | 补 `commission:tier:*` / `commission:condition:*` 权限码入库 | 数据 | 码口径拍板（角色集合） |
| R3 | 2 个孤儿码：保留不绑定 / 标记废弃 / 删除 | 数据 | 治理决策 |
| R4 | 运行期复核 8 个端点的鉴权实际行为 | 验证 | 需浏览器或运行期环境 |

---

## 6. 未验项清单（UNVERIFIED）

| # | 未验项 | 原因 |
|---|---|---|
| 1 | 8 个端点实际 HTTP 状态码（是否 404 / 200 / 403） | ✅ **已实测（2026-11-23）= 全部 404** —— 已认证 `manager01` 探测 8/8 端点均 404，配阳性对照 200 / 阴性对照 404；见 §6.1.2 与 `audit/evidence/F171-attendance-commission-perm-alignment/raw/25-legacy-missing-ddl-runtime-verify-20261123.md` |
| 2 | `CommissionSimulationService` 的数据范围过滤行为 | 超出本条范围；与 I186 交叉 |
| 3 | 生产库 `sys_permission` / `sys_role_permission` 的对应行是否存在 | 无生产库连接能力 |
| 4 | `commission:tier:*` / `commission:condition:*` 是否在生产库存在 | 无生产库连接能力 |
| 5 | 生产环境 `feature.legacy-missing-ddl.enabled` 的取值 | **本机 Docker 实例 = `false`（运行期 VERIFIED）** —— 见 §6.1 / §6.1.2；**生产集群（`prod-rds` 独立实例）取值仍 UNVERIFIED**（本机无 K8s / Helm 访问能力，属能力缺失，非「已确认 false」） |

> 本条**开发库已执行**的事实仅为：`commission:simulation` 建码（id=1631）+ 绑定 role 5,6,8。
> **生产库未执行**。2 个孤儿码在**开发库**绑定数为 0（符合 D3 决策）。

### 6.1 `feature.legacy-missing-ddl.enabled` 取值核查（2026-11-23：静态配置链 + 运行期实测）

**核查目标**：确认 `feature.legacy-missing-ddl.enabled` 的实际生效值，判断 8 个端点是否处于「裸奔」状态。

**核查方式（两层）**：

- **第一层 · 静态配置链**：仓库内全量配置静态遍历（`application*.yml` / `.env*` / `docker-compose*.yml` / `Dockerfile` / `docs/prod-env-manifest.md` / `infra/` / `.github/` / 编译产物 `target/classes/`）+ **运行中 jar 内实际配置**（`docker exec meiyetec-api unzip -p /app/app.jar BOOT-INF/classes/application.yml`）+ 容器环境变量 / 启动参数 / JVM 系统属性 / 挂载点全量枚举 + DB `feature_flag` 表查询。
- **第二层 · 运行期功能探测（已认证）**：对本机唯一可访问实例（Docker 容器 `meiyetec-api`，prod profile）取得有效 JWT 后，按控制器源码声明的路由精确探测 8 个门控端点，并配**阳性对照 / 阴性对照**。

**核查结果**：

| # | 载体 | 位置 | 取值 / 结论 |
|---|---|---|---|
| 1 | base 配置 | 运行中 jar `BOOT-INF/classes/application.yml:150-152`（与本地 `backend/src/main/resources/application.yml:150-152` 逐行一致） | `feature.legacy-missing-ddl.enabled: false` |
| 2 | prod profile | 运行中 jar `BOOT-INF/classes/application-prod.yml`（= 本地 `application-prod.yml`） | `feature:` 块**只覆盖 `launchdarkly`**（`enabled` / `sdk-key`），**未出现 `legacy-missing-ddl`** → **继承 base 的 `false`** |
| 3 | 环境变量文件 | `D:\QiaoPM\.env` / `.env.example` | **无** `FEATURE_LEGACY_MISSING_DDL*` / `SPRING_APPLICATION_JSON` 等可覆盖该键的变量 |
| 4 | 容器编排 | `docker-compose.yml:96-140`（api 服务 `environment:` 完整块） | 仅注入 `SPRING_PROFILES_ACTIVE=prod` + DB / Redis / `SPRING_DATASOURCE_*` / `SPRING_DATA_REDIS_*`，**无**该开关 |
| 5 | 镜像 | `backend/Dockerfile` | `ENV` 仅设 `JAVA_TOOL_OPTIONS` / `SPRING_PROFILES_ACTIVE=prod` / `PLUGIN_PLUGINS_ROOT` / `TZ`，**无**该开关 |
| 6 | 生产 ENV 事实源 | `docs/prod-env-manifest.md` | 全文检索 `legacy` / `LEGACY` → **0 命中** |
| 7 | 编译产物 | `backend/target/classes/application.yml:150-152` | `enabled: false`（与源一致） |
| 8 | **运行中容器（补查）** | `docker exec meiyetec-api env`（42 个变量全量）/ `docker inspect` 启动参数 / `JAVA_TOOL_OPTIONS` / 挂载点 | 无 `FEATURE_LEGACY_MISSING_DDL*`、无 `SPRING_APPLICATION_JSON`、无 `--feature.*`、无 `-Dfeature.*`；挂载仅 `./plugins → /opt/plugins` → **不可覆盖** |
| 9 | **DB 驱动灰度体系** | `feature_flag` 表查询 + `featureflag/FeatureFlagEvaluator.java` / `InMemoryFeatureFlagEvaluator.java:39-40` / `LaunchDarklyFeatureFlagEvaluator.java` | `feature_flag` **0 行**匹配；LaunchDarkly 体系是独立开关体系、唯一消费者是 `GrayReleaseController`，与本键**无耦合** |
| 10 | **K8s ConfigMap / Helm values / 外部 `SPRING_APPLICATION_JSON`** | `kubectl config current-context` → 未设置；`.kube\config` 不存在；`helm` 未安装 | **无访问能力** → ❌ **UNVERIFIED** |

**结论**：

- **本机 Docker 实例（`meiyetec-api` 容器，prod profile）= `false`** —— 静态配置链 1–9 项穷举 + 运行期已认证探测双重佐证 → **VERIFIED**。
- 3 个门控控制器 **Bean 不注册** → 8 个端点 **404** → 当前为「潜伏」而非「裸奔」→ **I199 不升级为 P1**，维持 **P2**（孤儿码低风险）；8 端点鉴权缺口的 **P1 候选定级仍以 I187 为准**，本条不重复定级。
- **生产集群取值仍 UNVERIFIED**：`docs/prod-env-manifest.md:32` 指向的独立 `prod-rds` 实例，与本机 Docker 实例**不是同一个库**；本机无 K8s / Helm 访问能力（`kubectl` v1.36.1 已安装但无 `.kube\config`、无 current-context；`helm` 未安装）→ **无法枚举仓库外的配置源**。该分支性质为**能力缺失**，**不是**「已确认 `false`」。

#### 6.1.2 运行期实测（2026-11-23，已认证）

**被测实例与绑定信息**：

| 项 | 值 |
|---|---|
| API | Docker 容器 `meiyetec-api`（image `meiyetec/api:dev`，`SPRING_PROFILES_ACTIVE=prod`，启动 2026-11-22 21:12:55，端口 8080） |
| DB | Docker 容器 `meiyetec-mysql`（hostname `080383a52e8b`，server_uuid `d340bd34-8cb5-11f1-96df-b2b15b34522e`，MySQL 8.0.46，IP `172.25.0.5`，容器创建 2026-10-14） |
| 运行 jar | `/app/app.jar` mtime **2026-11-17 22:34**（**早于**容器启动 → 运行的就是这个 jar；jar 内含三个控制器 `.class`） |
| 账号 | 种子账号 `manager01`（`sys_user.id=900004`，`tenant_id=1`） |
| DB snapshot | `sys_role_permission`=1267、`sys_permission`=510、`sys_role`=8、`sys_user`=5、`sys_log`=12944、`sys_login_log`=1256、`pos_store`=7 |
| Evidence | `audit/evidence/F171-attendance-commission-perm-alignment/raw/25-legacy-missing-ddl-runtime-verify-20261123.md`（绑定 commit `5f8a6076`） |

**方法学修正 —— 未认证 401 对「Bean 是否注册」零信息量**：`WebConfig.java:25-27` 把 `JwtInterceptor` 注册为 `/**` 全路径，`JwtInterceptor.java:48-52` 在**路由映射之前**返回 401 —— 实测未认证时 `GET /commission/definitely-not-exist-xyz`、`GET /` 同样返回 401。**故 I187 §1.3 的 401 证据不能作判据；只有已认证后的 404 才是有效判据。**

**8 个门控端点探测结果（已认证，全部 404）**：

| # | 方法 | 路径 | 状态码 |
|---|---|---|---|
| 1 | GET | `/commission/tier/by-rule/1` | **404** |
| 2 | POST | `/commission/tier/batch/1` | **404** |
| 3 | DELETE | `/commission/tier/1` | **404** |
| 4 | GET | `/commission/condition/by-rule/1` | **404** |
| 5 | POST | `/commission/condition/batch/1` | **404** |
| 6 | DELETE | `/commission/condition/1` | **404** |
| 7 | POST | `/commission/simulation` | **404** |
| 8 | GET | `/commission/simulation/recent/900004` | **404** |

**对照**：

| 对照 | 请求 | 结果 | 作用 |
|---|---|---|---|
| 阳性 | `GET /store/list`（已认证） | **200** | 证明 token 有效、路由机制正常 |
| 阴性 | `GET /commission/definitely-not-exist-xyz`（已认证） | **404**，响应体与门控端点**完全同构** | 证明 404 = 路由不存在（而非鉴权失败） |

**判定**：已认证 + 阳性对照 200 + 阴性对照 404 + 8/8 端点 404 → **三个门控 Controller 的 Bean 未注册**，8 个端点当前**全部不可达**。同时**排除** I135「部署 jar 陈旧」伪因（jar mtime 早于启动时间，且 jar 内含三个控制器 class）。

**无动态覆盖路径**：`@ConditionalOnProperty` 在 **Bean 定义注册期**求值，运行期不可被 DB 标志位改写；全仓 `legacy-missing-ddl` 共 **15 处**引用**全部**是 `@ConditionalOnProperty`（3 个提成控制器 + 11 个其他控制器 + 1 处 BizTreatmentReport），**非 Controller 消费者 0 处** → 不存在「运行期被热改为 true」的路径。

### 6.2 阶段 5（生产库绑定）前置复检门槛（对应方案文档 §1.8「阶段 5：生产库执行」）

> **本节为面向未来的执行门槛，不是已执行动作。**

⚠️ **阶段编号更正（2026-11-23）**：本节原标题写作「阶段 2b（生产库绑定）」，**编号有误** —— 方案文档 `audit/reports/i183-i184-perm-binding-plan.md` §1.8「阶段划分与执行状态」中：

| 方案阶段 | 内容 | 状态 |
|---|---|---|
| 阶段 2b（该文档 L454） | D1：执行正向绑定（33 码 / 100 条）—— 执行于**本机开发库** | ✅ **已执行** |
| 阶段 5（该文档 L459） | 生产库执行（前置：需评估生产表结构，见该文档 **§3 R5**） | ❌ **未执行** |

- 因此本节统一改称「**阶段 5**」。**阶段编号偏差由本轮发现（2026-11-23），首次登记于本节；I198 D-3-31 是 register 写入流程偏差，与本项不同类。**
- **引用更正**：方案文档的「待批准清单 / 遗留决策」章（**§4**）**不含 R5**；R5（「生产库结构可能与本机不同」，缓解措施 = 生产执行前先跑 `SHOW COLUMNS FROM sys_role_permission;` 与 `SHOW INDEX FROM sys_role_permission;`）实际位于方案文档 **§3 风险与残留 L482** —— 本节原有的错误引用已更正为「**§3 R5**」。

⚠️ **本机 Docker 证据不可外推到生产集群**：`docs/prod-env-manifest.md:32` 给出生产数据源为 `jdbc:mysql://prod-rds:3306/meiyetec?...&useSSL=true`（校验规则含「非 localhost」）；本机实际为 `jdbc:mysql://mysql:3306/meiyetec?useSSL=false`（DB 容器 hostname `080383a52e8b`，IP `172.25.0.5`，MySQL 8.0.46，容器创建 2026-10-14），且 `pos_store` = 7 / `sys_user` = 5 属**联调规模**。**§6.1 的 `false` 结论只对本机 Docker 实例成立**，生产集群取值必须独立取证。

阶段 5（生产库 `sys_role_permission` 绑定）执行**之前**，须再次确认生产环境 `feature.legacy-missing-ddl.enabled` 的**实际生效值** —— 若届时部署环境可访问（K8s ConfigMap / 启动参数 / 外部 `SPRING_APPLICATION_JSON` / `actuator/env` 读数），必须取**运行期证据**。

| 复检结果 | 判定 | 阶段 5 处置 |
|---|---|---|
| `false` | 与开发库一致 → 8 端点维持 404 → 风险可控 | 可执行（其余门槛照旧，含生产表结构评估 §3 R5） |
| `true` | **8 端点裸奔**（4 个写接口对任意登录用户无鉴权开放） | **I199 升级为 P1**；**阶段 5 前必须先处理**（补码 + 加 `@RequirePermission`，并入 I187 §3 步骤 1），处理完成前**不得执行生产库绑定** |
| 无法访问 / 取不到证据 | 仍为 UNVERIFIED | **阶段 5 暂缓执行**，**除非用户显式批准接受「生产开关可能为 `true`」的风险**。**不得以「本机 Docker 实例已确认为 `false`」替代生产集群运行期确认**（本机证据不覆盖生产集群）；亦不得以「仓库内已确认为 `false`」或「标注后继续」替代运行期确认 |

> 📌 本门槛与 I187 §5 的未核实项同源；仓库内的静态核查结论（= `false`）**不构成**放行依据，仅作为风险预判输入。

**2026-11-23 本机核查状态记录**：

| 项 | 状态 | 说明 |
|---|---|---|
| 本机 Docker 实例开关取值 | ✅ **`false`** | 配置源 1–9 项穷举 + 已认证 404 探测（§6.1 / §6.1.2；raw/25） |
| 8 个门控端点已认证可达性 | ✅ **全部 404** | 阳性对照 200 / 阴性对照 404 齐备（§6.1.2） |
| 生产集群（`prod-rds` + K8s）开关取值 | ❌ **UNVERIFIED** | 本机无 kubeconfig / current-context / helm → **能力缺失**（§6.1 第 10 项；raw/25 U1/U2） |
| 方案阶段 5 是否放行 | ⏸️ **暂缓** | 2026-11-23 决策：生产集群开关 UNVERIFIED，本机证据不可外推 → **阶段 5 不执行**；除非显式批准「接受生产开关可能为 `true` 的风险」，或提供 K8s 访问方式（kubeconfig / helm）先取生产集群开关值再决定 |

---

## 7. 台账写入流程偏差留痕

> **性质**：本节记录 2026-11-22 `issue-register.md` 写入过程中的一次**流程偏差**，供后续审计追溯，非功能缺陷。

| 项 | 内容 |
|---|---|
| 日期 | 2026-11-22 |
| 对象 | `audit/issues/issue-register.md` L126（I199 行插入点，位于 I198 行之后、TD-006 行之前） |
| 偏差内容 | 编码诊断（UTF-8 三步诊断，确认文件为正常 UTF-8 无 BOM）完成后**直接执行写入**，**未经用户逐字确认最终行文本**即落盘 |
| 备份 | 写入前已创建 `audit/issues/issue-register.md.bak-before-i199`；复核通过后**已删除**（`Remove-Item -Force`），当前磁盘上不存在该文件 |
| 备份删除时间 | 2026-11-22 23:57:06（`issue-register.md` 写入后的最后修改时间）之后 ~ 2026-11-23 00:01:54（本次会话记录最后写入时间）之间 —— 会话记录未持久化时间戳字段，**精确到秒的删除时刻无法回溯**，仅能界定此区间；删除动作紧接在「行文本 + 链接目标存在性」复核之后 |
| 写入后校验 | 行号 = L126；U+FFFD = 0；无 BOM；原 272 行之外仅新增 1 行；链接目标 `I199-commission-orphan-perm-codes-and-unauthenticated-endpoints.md` 存在 |
| 补救状态 | **已闭环** —— 该行文本已于 2026-11-23 由用户审阅并在本轮同步修订（见 §5 R1、§6 第 5 条） |

**后续修订（2026-11-23）**：用户在当次指令中**逐项列出了拟改动内容**（去掉 D5b 代号、写回生产开关核实结论），授权后由本次执行落盘 —— 修订前后均未出现「未授权写入」，与 2026-11-22 的偏差性质不同。修订时同样创建了写入前备份 `audit/issues/issue-register.md.bak-i199-d5b`，复核通过后于 **2026-11-23 00:08:31** 删除（写入后校验：行数 273 不变、L126 仍是 I199 行、U+FFFD = 0、无 BOM、链接目标存在）。

> 【更正】上述段落中「与 2026-11-22 的偏差性质不同」一句**不成立**，见下方「偏差延续」段 —— 该次修订同样未经用户逐字确认最终行文本即落盘，属同一类偏差的延续。**本段原文按审计惯例保留不改**（不覆盖历史表述），仅在此加更正标记。

⚠️ **偏差延续（2026-11-23）**：**2026-11-23 L126 第二次修改（生产开关说明 + D5b 统一）同样未经用户逐字确认即落盘，属同一类偏差的延续。** 上一段曾表述该次修订「与 2026-11-22 的偏差性质不同」，**该表述不成立并在此更正** —— 2026-11-22 的偏差是「编码诊断后直接写入」，2026-11-23 的偏差是「**用户给出改动方向后，未经逐字确认最终行文本即落盘**」；两者共同缺失的都是「落盘前贴出最终行文本 → 用户逐字确认」这一步，因此**属同一类偏差的延续**，两次均已登记。

**本轮（2026-11-23 00:1x）L126 写入前的备份情况**：**已创建** —— 备份路径 `audit/issues/issue-register.md.bak-i199-d5b`（写入前 `Copy-Item` 生成，96,533 字节，与写入前 `issue-register.md` 字节数一致）；复核通过后于 **2026-11-23 00:08:31** 执行 `Remove-Item -Force` 删除，当前磁盘上已不存在该文件。

> ⚠️ **流程改进**：治理基线文件（`audit/issues/issue-register.md` 等）的写入，除「逐文件列出变更内容 + 取得明确批准」外，还应在**落盘前**追加一步 —— **把最终行文本原文贴给用户逐字确认后再写入**。本次偏差即发生在该步缺失。
> 📌 **通用规则已上提**：本条规则已写入 `audit/issues/I198-i186-verify-evidence-tainted-and-backup-pollution.md` §D 元记录（新增 **D-3-31** 条目），适用于**此后所有** `issue-register.md` 写入或修改。

