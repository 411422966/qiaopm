# I187 — 三个提成门控控制器完全无 `@RequirePermission`（8 个 endpoint / 4 个写接口），解封即裸奔

> **状态**：**Open**（本批**仅登记**，**未修改任何业务代码、未写库**）
> **严重度**：**P1**（当前因 `feature.legacy-missing-ddl.enabled=false` 未注册、运行期一律 404，**不可复现**；但一旦开关置 true，4 个写接口将**对任意登录用户无鉴权开放**）
> **发现日期**：2026-11-16（batch2-C 权限对齐只读调查）
> **登记人**：Cursor
> **绑定基线**：HEAD `56d59a2`
> **验证等级**：**VERIFIED（静态 L4 + 运行期 L5a 的 404 事实）** —— 源码中确实无任何 `@RequirePermission`；运行期实测 404（因 Bean 未注册）；「解封后裸奔」为**静态可确定的推论**，运行期未复现
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`
> **证据**：`audit/evidence/F171-attendance-commission-perm-alignment/`（`raw/01-static-endpoint-matrix.md` §3、`raw/07-runtime-ghost-endpoints-404.txt`、`raw/06b` C 段）
> **关联**：I115（权限注解覆盖率不足 P0）、I135（部署 jar 陈旧 / `feature.legacy-missing-ddl` 警告）、I184（提成死码）、I185（前后端码漂移）、**I202**（前后端权限码漂移全量口径 —— 引用本条 G7/G8：`/commission/simulation` 404 的根因与覆盖关系）

---

## 0. 一句话描述

`CommissionTierController`（3 个 endpoint）/ `CommissionConditionController`（3 个）/ `CommissionSimulationController`（2 个）
**8 个 endpoint 全部没有 `@RequirePermission` 注解**，其中 4 个是写接口
（`POST /commission/tier/batch/{ruleId}`、`DELETE /commission/tier/{id}`、`POST /commission/condition/batch/{ruleId}`、`DELETE /commission/condition/{id}`）；
三个控制器均带 `@ConditionalOnProperty(name = "feature.legacy-missing-ddl.enabled", havingValue = "true", matchIfMissing = false)`，
而 `application.yml:151-152` 设为 `false` → **运行期 Bean 未注册、全部 404** →
**当前是「潜伏」而非「正在流血」**，但这是 I135 明确警告过的开关，只要有人为了补 132 张缺失 DDL（I118）而把它置 `true`，这 4 个写接口就**同时上线且不带任何鉴权**。

---

## 1. 事实核实

### 1.1 静态：8 个 endpoint 逐条（`raw/01` §3）

| # | 方法 | 路由 | 权限码 | 类型 | 控制器 / 行号 |
|---|---|---|---|---|---|
| G1 | GET | `/commission/tier/by-rule/{ruleId}` | **无** | 读 | `CommissionTierController.java:22-23` |
| G2 | POST | `/commission/tier/batch/{ruleId}` | **无** | **写** | `CommissionTierController.java:27-28` |
| G3 | DELETE | `/commission/tier/{id}` | **无** | **写** | `CommissionTierController.java:33-34` |
| G4 | GET | `/commission/condition/by-rule/{ruleId}` | **无** | 读 | `CommissionConditionController.java:22-23` |
| G5 | POST | `/commission/condition/batch/{ruleId}` | **无** | **写** | `CommissionConditionController.java:27-28` |
| G6 | DELETE | `/commission/condition/{id}` | **无** | **写** | `CommissionConditionController.java:33-34` |
| G7 | POST | `/commission/simulation` | **无** | 纯计算 | `CommissionSimulationController.java:24` |
| G8 | GET | `/commission/simulation/recent/{employeeId}` | **无** | 读 | `CommissionSimulationController.java:28-29` |

> 三份源文件的 imports 中**均无** `com.meiyetec.annotation.RequirePermission`（`CommissionTierController.java` / `CommissionConditionController.java` / `CommissionSimulationController.java`）。

### 1.2 门控条件

`backend/src/main/resources/application.yml:151-152`：

```yaml
feature:
  legacy-missing-ddl:
    enabled: false
```

三个控制器的类级注解：`@ConditionalOnProperty(name = "feature.legacy-missing-ddl.enabled", havingValue = "true", matchIfMissing = false)`
→ `false` 时 **Bean 不注册** → 路由不存在 → 404。

### 1.3 运行期实测（`raw/07`、`raw/06b` C 段）

| 探测 | 账号 | 结果 |
|---|---|---|
| `GET /commission/tier/by-rule/1` | admin | **404** `{"status":404,"error":"Not Found","path":"/commission/tier/by-rule/1"}` |
| `GET /commission/condition/by-rule/1` | admin | **404** |
| `GET /commission/simulation/recent/1` | admin | **404** |
| `GET /commission/simulation` | admin | **404** |
| 同组 | manager / boss / clerk | **404**（一致，无差别） |

> ⚠️ **404 是「Bean 未注册」的典型特征**，与「路径写错」表现相同 → 判定依据是**源码中确有对应 `@RequestMapping`**（`CommissionTierController.java:15` / `CommissionConditionController.java:15` / `CommissionSimulationController.java:16`）+ `application.yml` 开关 + I135 的历史结论。

### 1.4 前端确实在调用这些路径（说明它们是「计划内功能」而非死代码）

`frontend/meiyetec-web/src/api/commission-rule.ts`：

| 行号 | 前端调用 | 对应后端 |
|---|---|---|
| 8 | `GET /commission/tier/by-rule/${ruleId}` | G1 |
| 10 | `POST /commission/tier/batch/${ruleId}` | G2 |
| 12 | `DELETE /commission/tier/${id}` | G3 |
| 15 | `GET /commission/condition/by-rule/${ruleId}` | G4 |
| 17 | `POST /commission/condition/batch/${ruleId}` | G5 |
| 19 | `DELETE /commission/condition/${id}` | G6 |
| 32 | `POST /commission/simulation` | G7 |
| 35 | `GET /commission/simulation/recent/${employeeId}` | G8 |

> 前端路由 `router/index.ts:113-115` 还为 `commission/rule-list` / `commission/rules` / `commission/simulation` 配了守卫码（`commission:rule` / `commission:rule:config` / `commission:simulation`），
> 但这 3 个码**在 `sys_permission` 中不存在**（I185）→ 页面与接口**双层不可达**。

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | **4 个写接口解封即裸奔** | 有人把 `feature.legacy-missing-ddl.enabled` 置 `true` | 任意登录用户（哪怕 CLERK）可批量改/删提成阶梯与条件 → **提成规则体系可被任意篡改** |
| R2 | 与 I118（132 张表 DDL 缺失）耦合 | 补 DDL 的动作通常伴随解封开关 | 这是**最可能的触发路径**，不是理论风险 |
| R3 | `/commission/simulation` 无鉴权 + 可算任意 `employeeId` | 解封 | 可用于**探测他人提成构成**（收入结构属敏感信息） |
| R4 | `/commission/simulation/recent/{employeeId}` 无鉴权 | 解封 | 可枚举任意员工的提成模拟历史 |
| R5 | 「当前 404」被误当作「功能不存在」 | 运维看到 404 | 实际是**门控关闭**；一旦有人「顺手打开开关修 404」→ 直接触发 R1 |
| R6 | 前后端**双层不可达** | 当前 | `commission/simulation` 页面守卫码不存在 + 接口 404 → 用户完全看不到该功能；这是「幽灵功能」 |

---

## 3. 建议方案（**仅建议，本批不实施**）

| # | 步骤 | 内容 | 备注 |
|---|---|---|---|
| 1 | **解封前先补鉴权（硬前置）** | 为 8 个 endpoint 加 `@RequirePermission`：<br>· G1/G4/G5/G6/G2/G3 → `commission:rule:edit`（阶梯/条件是规则的子结构）<br>· G7/G8 → 新增 `commission:simulation`（需先入库并绑定，I185/I188） | **顺序不可颠倒**：先补鉴权，再解封 |
| 2 | **补 `commission:simulation` 码入库** | `sys_permission` 插入该码并绑定相应角色（前端 `router/index.ts:115` 已引用它） | 与 I185 的码表收敛合并。**2026-11-23 更新（I202）**：该码**已在库中存在**（id=1631，type=API，已绑 ADMIN/BOSS/MANAGER 3 角色），本步**无需再入库**，只需确认绑定口径 |
| 3 | **解封开关与鉴权用同一事务性变更上线** | 避免「先开开关后补注解」的中间态 | — |
| 4 | **纳入守门脚本** | 加一条静态检查：所有 `@RestController`（含 `@ConditionalOnProperty` 门控类）必须有 `@RequirePermission` 或显式标注豁免 | 可复用 I115 的扫描思路 |
| 5 | **确认这批功能是否仍需要** | 若业务已放弃阶梯/条件/模拟计算功能 → **删除**比保留裸奔更安全 | 需业务确认 |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 解封前：8 个 endpoint | 仍 **404**（不得提前解封） |
| 2 | 解封后：`clerk01` `POST /commission/tier/batch/1` | **403**「权限不足: 需要权限码：commission:rule:edit」 |
| 3 | 解封后：`manager01` `POST /commission/tier/batch/1` | 按绑定口径放行/拒绝，**不得无鉴权 200** |
| 4 | 解封后：`manager01` `DELETE /commission/tier/{id}` | 同上 |
| 5 | 解封后：`clerk01` `POST /commission/simulation` | **403** |
| 6 | 解封后：`manager01` `GET /commission/simulation/recent/{employeeId}` | 需校验 `employeeId` 是否本门店（叠加 I186） |
| 7 | 静态扫描：所有 `@RestController` 方法 | 100% 有 `@RequirePermission` 或白名单豁免 |

---

## 5. 边界声明

- 本 Issue 结论来自**静态代码分析**（三个控制器全文 + `application.yml` + 前端 api 封装）+ **运行期只读 GET 探测（全部 404）**。
- **未修改任何业务代码**、**未执行任何写操作**、**未执行任何 DDL**、**未新增任何入库脚本**；**未切换任何配置开关**。
- ⚠️ 「解封后裸奔」为**静态可确定的推论**（无 `@RequirePermission` + 无其它鉴权机制覆盖这三个控制器），**运行期未复现**（当前 404）→ 该分支标 **UNVERIFIED**（需在隔离环境切换开关后复测）。
- 已确认这三个控制器**不在** `DataScopeInterceptor` 的其它拦截路径之外也未被其它切面覆盖 —— 本次范围内**未找到**任何针对 `/commission/tier` / `/commission/condition` / `/commission/simulation` 的额外鉴权切面；**未穷举**全仓所有 `@Aspect`（超出本批模块边界）。
- 未核实：`biz_commission_tier` / `biz_commission_condition` / `biz_commission_simulation` 三表**均为 0 行**（本机库）→ 解封后写操作的实际业务后果暂无样本。
- 未核实：生产环境 `feature.legacy-missing-ddl.enabled` 的取值。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。

---

**下一步**：把「补鉴权」列入**解封前置条件**，与 I118（补 DDL）的排期绑定；I135 已有明确警告不得整体置 `true`，本条应作为该警告的具体依据补入迁移说明。
