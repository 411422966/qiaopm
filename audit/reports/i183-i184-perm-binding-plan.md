# I183 / I184 权限码绑定方案与执行结论

> **状态**：**开发库已执行完成并验证通过；生产库未执行；运行期行为未验证（UNVERIFIED）**
> **产出日期**：2026-11-20（方案定稿）→ 2026-11-22（D2 拍板 + 执行 + 验证 + 本文档全量重写）
> **重写原因**：原文档中文内容被 GBK→UTF-8 双重编码损坏（mojibake 字面量 2534 处 + 不可逆 `?` 丢失 1209 处），表格分隔符与加粗标记被打散，正文多处不可读。原文档另引用了不存在的 §1.6 / §1.7，且 §1.2.1 角色增量数字为 D5a 拍板前的旧值。详见 `audit/evidence/F171-attendance-commission-perm-alignment/plan-doc-encoding-corruption-20261122.md`
> **产出方**：Cursor
> **关联 Issue**：I183（考勤 9 码 0 绑定）、I184（提成 24 码 0 绑定）、I185（前后端权限码双轨）、I186（数据范围）、I187（提成端点无鉴权）、I188（种子与运行期漂移）、I198（证据污染与备份污染）
> **数据来源声明**：本文所有数字均取自下列事实源之一，未凭记忆或旧文档复述：
> 1. 开发库 `meiyetec` 实时 `SELECT` 输出（绑定矩阵、34 码、1267 行）
> 2. `audit/evidence/F171-attendance-commission-perm-alignment/raw/` 下的 `21-*.sql`（执行）、`22-*.sql`（回滚）、`23-*.sql`（正反双向 dry-run）、`24-*.sql`（V1–V5 验证）
> 3. 本轮 D2 拍板结论（A 组、B 组、H1–H3）
> 4. I198 已确认事实（基线 1164 / role 5 = 278 / 证据污染标记）
> **执行时 HEAD**：`a77c42bc50445f7683fb5aa3649be5975b1bcea1`（绑定为 SQL 执行，无代码 commit —— 该 commit 是「I198 头部补本轮收口状态」的文档提交，非绑定操作本身）
> **边界声明**：本文只做方案 + 执行结论记录。**阶段 2 未修改任何业务代码、未修改前端路由码、未新增任何可执行脚本**。已执行的仅 `21-*.sql` 与 `24-*.sql`（纯 `INSERT IGNORE` + `SELECT` 数据操作）。

---

## 0. 结论摘要

| # | 结论 | 依据 |
|---|---|---|
| 1 | **开发库已执行并验证通过**：`sys_role_permission` 总行数 **1164 → 1267**（+103 = D1 的 100 条 + D5a 的 3 条），34 个码的 `role_id` 集合与 D2 拍板口径**逐码全等**（V2 全 PASS），漏绑 0 行（V3）、越界绑定 0 行（V4） | §1.4 V1–V5、§1.7 证据文件 `24-*.out.txt` |
| 2 | **正反双向闭环已验证**：绑定 → 回滚 → 再绑定，库行数 **1267 → 1164 → 1267** 完全还原，`sys_permission` 行数 **510 → 509 → 510** | §1.7 证据文件 `23-*.out.txt` |
| 3 | **生产库未执行**：本文所有执行动作均发生在本机开发库（`docker exec meiyetec-mysql`）；生产库需另行评估 `sys_role_permission` 表结构后执行（见 §3 R5） | §1.8 阶段划分、§3 R5 |
| 4 | **运行期行为未验证**：9 项 V6 运行期复核全部 **UNVERIFIED** —— 无浏览器操作能力、无生产库连接能力（验收规则「能力边界」） | §1.5 |
| 5 | **2 个孤儿码保持 0 绑定**（D3 决策）：`commission:list`(1118)、`commission:settlement:audit`(1128) 在 `sys_permission` 内但后端无任何 `@RequirePermission` 引用 —— 绑定它们不会让任何端点放行 | §1.3 A-2、§3 R7 |
| 6 | **8 个端点仍无鉴权**（不在本批次范围）：`CommissionTierController`(3) + `CommissionConditionController`(3) + `CommissionSimulationController`(2) 三个控制器**零 `@RequirePermission` 注解**；`commission:simulation` 已作为权限码入库（id=1631，绑 5,6,8），但后端未消费该码 | §1.3 C 类、§4.1 |
| 7 | **B 类 7 个前端路由码未修改**：前端 `meta.permission` 仍使用 `employee:attendance` / `commission:rule` 等前端自有码，与后端码不互认（D4 决策：另批处理） | §1.2 B 类 |
| 8 | **表结构约束**：`sys_role_permission` 无 `permission_code` 列，仅 `id / role_id / permission_id / create_time`，唯一键 `uk_role_permission(role_id, permission_id)` —— 故 §1.4 正向 SQL 必须逐条显式给出 `role_id` + `permission_id` 常量对 | §1.3.1 |

---

## 1.1 三类判定

判定口径（按用户定义）：

- **A 类**：码在 `sys_permission`，但 `sys_role_permission` 无绑定 → **纳入 §1.4 绑定**
- **B 类**：码**不在** `sys_permission`，但后端有等价码 → **出映射表**（§1.2），前端码**不改**
- **C 类**：码**不在** `sys_permission`，后端**也没有**等价码 → **必须先补码**

判定范围 = 本批次（I183 考勤 + I184 提成）及为其前端对应面（I185 的 8 个码）。
范围外的 15 个前端缺码（`biz:room` / `org:*` / `pay:*` / `mh:*` 等）单列 §1.6，**本批次不处理、不绑定**。

> ⚠️ `mh:consent` / `mh:visual` 属**用户已验收页面**（`/mh/visual`、`/mh/consent`），**禁止任何主动修改**。

### 1.1-A　A 类：`sys_permission` 内、无绑定

**A-1　后端有引用 → 本批次已绑定（34 码 / 103 条绑定）**

| 模块 | 码 | `permission_id` | `permission_type` | **实到 role_id 集合** | 绑定数 |
|---|---|---|---|---|---|
| 考勤 | `attendance:check` | 1453 | button | 2,4,5,6,8 | 5 |
| 考勤 | `attendance:config:edit` | 1452 | button | 5,8 | 2 |
| 考勤 | `attendance:config:list` | 1451 | button | 2,5,6,8 | 4 |
| 考勤 | `attendance:edit` | 1456 | button | 5,8 | 2 |
| 考勤 | `attendance:leave:approve` | 1458 | button | 2,5,6,8 | 4 |
| 考勤 | `attendance:leave:list` | 1459 | button | 2,4,5,6,8 | 5 |
| 考勤 | `attendance:leave:submit` | 1457 | button | 2,4,5,6,8 | 5 |
| 考勤 | `attendance:list` | 1454 | button | 2,4,5,6,8 | 5 |
| 考勤 | `attendance:stat` | 1455 | button | 2,5,6,8 | 4 |
| 提成 | `commission:calculate` | 1328 | API | 5,6,8 | 3 |
| 提成 | `commission:record:stat` | 1330 | API | 5,6,8 | 3 |
| 提成 | `commission:rule:add` | 1120 | button | 5,6,8 | 3 |
| 提成 | `commission:rule:delete` | 1122 | button | 5,6,8 | 3 |
| 提成 | `commission:rule:detail` | 1331 | API | 5,6,8 | 3 |
| 提成 | `commission:rule:edit` | 1121 | button | 5,6,8 | 3 |
| 提成 | `commission:rule:stat` | 1332 | API | 5,6,8 | 3 |
| 提成 | `commission:rule:status` | 1329 | API | 5,6,8 | 3 |
| 提成 | `commission:salary:add` | 1335 | API | 5,6,8 | 3 |
| 提成 | `commission:salary:adjust` | 1336 | API | 5,6,8 | 3 |
| 提成 | `commission:salary:batch-confirm` | 1339 | API | 6,8 | 2 |
| 提成 | `commission:salary:batch-pay` | 1337 | API | 6,8 | 2 |
| 提成 | `commission:salary:confirm` | 1125 | button | 6,8 | 2 |
| 提成 | `commission:salary:export` | 1333 | API | 5,6,8 | 3 |
| 提成 | `commission:salary:pay` | 1126 | button | 6,8 | 2 |
| 提成 | `commission:salary:stat` | 1334 | API | 5,6,8 | 3 |
| 提成 | `commission:settlement:add` | 1345 | API | 5,6,8 | 3 |
| 提成 | `commission:settlement:batch-confirm` | 1327 | API | 6,8 | 2 |
| 提成 | `commission:settlement:batch-pay` | 1341 | API | 6,8 | 2 |
| 提成 | `commission:settlement:confirm` | 1344 | API | 6,8 | 2 |
| 提成 | `commission:settlement:detail` | 1343 | API | 5,6,8 | 3 |
| 提成 | `commission:settlement:export` | 1342 | API | 5,6,8 | 3 |
| 提成 | `commission:settlement:pay` | 1340 | API | 6,8 | 2 |
| 提成 | `commission:settlement:stat` | 1338 | API | 5,6,8 | 3 |
| 提成 | `commission:simulation` | 1631 | API | 5,6,8 | 3 |

> **小计（实时核算）**：考勤 9 码 = **36 条**（5+2+4+2+4+5+5+4+5）；提成 24 码 = **64 条**（3-role 型 16 码 × 3 = 48；2-role 型 8 码 × 2 = 16）；`commission:simulation` = **3 条**。合计 **103 条**。
>
> **D1 与 D5a 拆分**：D1 = 33 码 / **100 条**（元组数核算见 `19-*.out.txt`：`total_tuples=100`；分角色 2→7、4→4、5→25、6→31、8→33）；D5a = 1 码（`commission:simulation`）/ **3 条**（分角色 5→+1、6→+1、8→+1）。

**A-2　后端无引用 → 本批次不绑定（2 码，D3 决策）**

| 码 | `permission_id` | `permission_type` | 实到绑定数 | 判定依据 |
|---|---|---|---|---|
| `commission:list` | 1118 | button | **0** | 全仓 `backend/src/main/java` 检索 `RequirePermission("commission:list")` → **0 处命中**；`CommissionController` 实际使用 `commission:record:list` |
| `commission:settlement:audit` | 1128 | button | **0** | 全仓检索 `RequirePermission("commission:settlement:audit")` → **0 处命中**；结算审核实际走 `commission:settlement:confirm` |

> **为什么「补了仍是死码」**：`PermissionAspect` 只在 `@RequirePermission` 声明的码上做判定（`PermissionAspect.java:47` 判断 `ctx.getPermissions().contains("*")`）。绑定 `commission:list` 不会让任何端点放行 —— 与 I185 §3 步骤 1「不反向补码」结论一致。
>
> **本批处置**：不绑定。孤儿码登记见 §4.1（I199 定稿内容，本轮尚未落盘 issue 文件）。

### 1.1-B　B 类：不在 `sys_permission`，但后端有等价码

| # | 前端码（`router/index.ts` 行号） | 页面 | 后端等价码（实时查得存在） | 建议前端改用 | 备注 |
|---|---|---|---|---|---|
| B1 | `employee:attendance`（L105） | 考勤打卡 | `attendance:check` / `attendance:list` | `attendance:check` | 前端码在 `sys_permission` **不存在** |
| B2 | `employee:attendance:list`（L106） | 考勤记录 | `attendance:list` | `attendance:list` | — |
| B3 | `employee:leave`（L107） | 请假管理 | `attendance:leave:list` / `:submit` / `:approve` | `attendance:leave:list` | — |
| B4 | `employee:attendance:stat`（L108） | 考勤统计 | `attendance:stat` | `attendance:stat` | — |
| B5 | `employee:attendance:config`（L109） | 考勤规则配置 | `attendance:config:list` | `attendance:config:list` | — |
| B6 | `commission:rule`（L113） | 提成规则列表 | `commission:rule:list` | `commission:rule:list` | — |
| B7 | `commission:rule:config`（L114） | 提成规则配置 | `commission:rule:list` / `:add` / `:edit` | `commission:rule:list` | ⚠️ **路由重名**：L114 与 L58 均为 `path: 'commission/rules'` + `name: 'CommissionRules'`，L114 覆盖 L58（见 §4 R9） |

> **本批次只输出映射表，不修改 `frontend/meiyetec-web/src/router/index.ts`** —— 属业务代码改动，需单独授权（D4 决策：另批处理）。
> 映射表的存在**不影响** §1.4 的绑定：后端只认 `@RequirePermission` 的码，前端守卫只认 `meta.permission` 的码，两者**互不替代**。

### 1.1-C　C 类：不在 `sys_permission`，后端也没有等价码

| # | 缺口 | 涉及端点 | 处置 |
|---|---|---|---|
| C1 | `commission:simulation` 权限码不存在 | `POST /commission/simulation`、`GET /commission/simulation/recent/{employeeId}` | ✅ **D5a 已执行**：新建 `sys_permission` 码（id=1631，`permission_type='API'`）+ 绑定 role 5,6,8。**但后端未加 `@RequirePermission`** → 见 §4.1 |
| C2 | `/commission/tier/*` 无权限码、无注解 | `GET /commission/tier/by-rule/{ruleId}`、`POST /commission/tier/batch/{ruleId}`、`DELETE /commission/tier/{id}` | ❌ **D5b 本批不执行**，只登记（关联 I187） |
| C3 | `/commission/condition/*` 无权限码、无注解 | `GET /commission/condition/by-rule/{ruleId}`、`POST /commission/condition/batch/{ruleId}`、`DELETE /commission/condition/{id}` | ❌ **D5b 本批不执行**，只登记（关联 I187） |

### 1.1-D　范围外观察项（本批次不处理、不绑定）

前端 94 个路由码中，**23 个不在 `sys_permission`**；其中 8 个属 I185（已列 §1.1-B），剩余 15 个如下（仅登记，供后续批次立项）：

| # | 前端码 | 后端是否有等价码 |
|---|---|---|
| 1 | `biz:room` | ✅ `biz:room:list` |
| 2 | `org:department` | ✅ `org:department:list` |
| 3 | `org:employee` | ✅ `employee:list` |
| 4 | `org:role` | ✅ `role:list` |
| 5 | `pay:order` | ✅ `pay:order:list` |
| 6 | `pay:refund` | ✅ `pay:refund:list` |
| 7 | `pay:settlement` | ✅ `pay:settlement:list` |
| 8 | `biz:service-item` | ✅ `biz:service-item:list` |
| 9 | `dcep:hardware-wallet` | ✅ `dcep:wallet:list` |
| 10 | `employee:profile` | ✅ `employee:profile` |
| 11 | `mh:profile` | — |
| 12 | `mh:visual` | ⚠️ 用户已验收页 |
| 13 | `mh:consent` | ⚠️ 用户已验收页 |
| 14 | `dashboard:workbench` | — |
| 15 | `profile:security` | — |

---

## 1.2 角色与绑定口径

### 1.2.1 角色字典与实到值（开发库实时复核，2026-11-22）

| role_id | role_code | role_name | 用户数（`sys_user_role` 聚合） | 基线绑定数（执行前） | D1 新增 | D5a 新增 | **实到绑定数（执行后）** |
|---|---|---|---|---|---|---|---|
| 1 | SUPER_ADMIN | 超级管理员 | 1（admin） | 118 | **0** | 0 | **118** |
| 2 | STORE_ADMIN | 门店管理员 | 0 | 40 | **+7** | 0 | **47** |
| 3 | EMPLOYEE | 员工 | 0 | 0 | **0** | 0 | **0** |
| 4 | CLERK | 店员 | 1（clerk01） | 50 | **+4** | 0 | **54** |
| 5 | MANAGER | 店长 | 1（manager01） | 278 | **+25** | **+1** | **304** |
| 6 | BOSS | 老板 | 1（boss01） | 192 | **+31** | **+1** | **224** |
| 7 | OPERATOR | 运营 | 1（operator01） | 105 | **0** | 0 | **105** |
| 8 | ADMIN | 系统管理员 | 1 | 381 | **+33** | **+1** | **415** |
| — | — | **合计** | — | **1164** | **+100** | **+3** | **1267** |

> **分角色增量明细（实时核算，证据 `19-*.out.txt` 与 `20-*.out.txt`）**：D1 = role2 **7**、role4 **4**、role5 **25**、role6 **31**、role8 **33**（合计 100）；D5a = role5 **+1**、role6 **+1**、role8 **+1**（合计 3）。
>
> **D1 的 33 码逐码绑定数分布（加总 = 100，与 `19-*.out.txt` 的 `total_tuples=100`、`20-*.out.txt` 的 `chk1_codes_bindings = 33 / 100` 三方一致）**：
>
> | 绑定角色数 | 码数 | 码清单 | 小计 |
> |---|---|---|---|
> | 2 个角色 | 10 | `attendance:config:edit`、`attendance:edit`、`commission:salary:batch-confirm`、`commission:salary:batch-pay`、`commission:salary:confirm`、`commission:salary:pay`、`commission:settlement:batch-confirm`、`commission:settlement:batch-pay`、`commission:settlement:confirm`、`commission:settlement:pay` | 10 × 2 = **20** |
> | 3 个角色 | 16 | `commission:calculate`、`commission:record:stat`、`commission:rule:add`、`commission:rule:delete`、`commission:rule:detail`、`commission:rule:edit`、`commission:rule:stat`、`commission:rule:status`、`commission:salary:add`、`commission:salary:adjust`、`commission:salary:export`、`commission:salary:stat`、`commission:settlement:add`、`commission:settlement:detail`、`commission:settlement:export`、`commission:settlement:stat` | 16 × 3 = **48** |
> | 4 个角色 | 3 | `attendance:config:list`、`attendance:leave:approve`、`attendance:stat` | 3 × 4 = **12** |
> | 5 个角色 | 4 | `attendance:check`、`attendance:leave:list`、`attendance:leave:submit`、`attendance:list` | 4 × 5 = **20** |
> | **合计** | **33** | — | **100** |
>
> **交叉校验（分角色加总，与 `19-*.out.txt` / `20-*.out.txt` 的 `by_role` 完全一致）**：
>
> | role_id | 计入方式 | 计数 |
> |---|---|---|
> | 2 STORE_ADMIN | 4 个 5 角色型（check / leave:list / leave:submit / list）+ 3 个 4 角色型（config:list / leave:approve / stat） | 4 + 3 = **7** |
> | 4 CLERK | 4 个 5 角色型（check / leave:list / leave:submit / list） | **4** |
> | 5 MANAGER | 2 个 2 角色型（config:edit / edit）+ 16 个 3 角色型 + 3 个 4 角色型 + 4 个 5 角色型 | 2 + 16 + 3 + 4 = **25** |
> | 6 BOSS | 8 个 2 角色型资金类码（pay / confirm / batch-pay / batch-confirm × salary/settlement）+ 16 个 3 角色型 + 3 个 4 角色型 + 4 个 5 角色型 | 8 + 16 + 3 + 4 = **31** |
> | 8 ADMIN | 全部 33 码 | **33** |
> | **合计** | 7 + 4 + 25 + 31 + 33 | **100** |
>
> **「102 条」的来源与结论**：上一轮（拍板前方案口径）写「33 码 × role 明细 = 102 条」，该数字**不是笔误**，而是 **D2 拍板前的旧口径**；D2 拍板后净减 2 条 → **100 条**。逐项拆解（证据 `17-*.out.txt` 与拍板记录）：
>
> | 变更项 | 净变化 | 原因 |
> |---|---|---|
> | `attendance:edit` 去掉 STORE_ADMIN(2) | **−1** | D2 A 组拍板：编辑类收敛为 5,8 |
> | `attendance:config:edit` 去掉 STORE_ADMIN(2) | **−1** | D2 A 组拍板：配置编辑类收敛为 5,8 |
> | `attendance:config:list` 补回 BOSS(6) | **+1** | D2 A 组拍板：列表类含 BOSS |
> | `commission:salary:adjust` 补回 BOSS(6) | **+1** | D2 B 组拍板：管理类含 BOSS |
> | 8 个资金类码（salary/settlement 的 pay / confirm / batch-pay / batch-confirm） | **净 0** | D2 H1：去 MANAGER(5)、加 BOSS(6)，一增一减 |
> | `commission:salary:export` / `commission:settlement:export` 去掉 OPERATOR(7) | **−2** | D2 H2：导出类收敛为 5,6,8 |
> | **合计** | **102 − 2 = 100** | ✅ 与执行脚本 `21-*.sql` 实到条数一致 |
>
> **为什么 role 1（SUPER_ADMIN）不增量**：`RbacContextLoader.java:78-89` 对 SUPER_ADMIN 用户自动追加通配 `*`（`PermissionAspect.java:47` 判定 `contains("*")` 直接放行），无需逐码绑定。
>
> **为什么 role 3（EMPLOYEE）不增量**：该角色**无用户、当前绑定数为 0**，库内不存在任何「员工」角色的授权先例 —— 不新造口径。
>
> **为什么 role 7（OPERATOR）本批次新增 0**（D2 变更）：原方案曾按 `report:export:list` = 5,6,7,8 口径给 role 7 拟 `salary:export` / `settlement:export`，D2 拍板**去掉 role 7**（导出类收敛为 5,6,8）。

### 1.2.2 参考绑定的实时 SELECT 依据

参考口径全部取自**当前库中已有的、已被业务采纳的绑定**，逐条 `SELECT` 取证：

| 参考码 | 实时 role_id 集合 | 语义（用于推导的口径） |
|---|---|---|
| `booking:detail` | 4,5,6,8 | 门店员工可查单据详情 |
| `booking:list` | 4,5,6,8 | 门店员工可查单据列表 |
| `booking:edit` / `booking:delete` | 5,8 | 门店单据写操作 |
| `pos:order:list` | 4,5,8 | 门店在岗人员可查单据 |
| `pos:order:add` | 5,8 | 门店写单据 |
| `pos:order:pay` / `pos:refund` | 5,8 | 资金动作 |
| `pos:refund:audit:store` | 2,8 | 门店级审核 |
| `stock:check` / `stock:alert` / `stock:export` | 2,5,8 | 门店级配置与数据口径 |
| `employee:list` | 2,4,5,6,7,8 | 门店管理侧可见员工 |
| `report:export:list` | 5,6,7,8 | 导出能力口径 |
| `biz:service-item:edit` | 1,5,6,8 | 业务配置写入 |

**推导规则（可复算）**：

- 自助类（打卡、查考勤、提交请假、查看请假）→ `4,5,6,8`（booking 口径）∪ `5,8`（pos 单据口径）∪ `2,5,8`（store 门店口径）= **2,4,5,6,8**
- 门店管理层类（统计、审批、门店配置、记录纠错）→ **2,5,6,8** 或 **2,5,8**
- 提成管理类 → **5,6,8**（与提成模块 4 个已绑码完全一致）
- 提成导出类 → D2 拍板收敛为 **5,6,8**（与 `report:export:list` 口径有差异，D2 明确去 7）

### 1.2.3 D2 拍板记录与与 I183 / I184 原建议的差异

> **拍板结论速览（权威口径）**：A 组 9 码、B 组 11 码、H1–H3 **全部拍定**，§1.1-A (a)(b)(c) 三表已回写最终角色集。

#### (a) 考勤 9 码：I183 原建议 vs 本方案 vs D2 最终口径

| # | 码 | `permission_id` | I183 原建议角色 | 拍板前本方案 | **D2 最终口径** | 与原建议差异 | 风险（按最终口径） |
|---|---|---|---|---|---|---|---|
| A1 | `attendance:edit` | 1456 | ADMIN(8) | 2,5,8 | **5,8** | **+5**（比原建议多给店长） | ⚠️ 中：补录改卡直接影响工资计算基数；该链路无 `@SysOpLog` 审计 |
| A2 | `attendance:config:edit` | 1452 | ADMIN(8) | 2,5,8 | **5,8** | **+5** | ⚠️ 中：考勤**规则**写入（打卡时间/迟到判定） |
| A3 | `attendance:list` | 1454 | 4,5,6,8 | 2,4,5,6,8 | **2,4,5,6,8** | **+2** | 中：新增 STORE_ADMIN 可见考勤明细 |
| A4 | `attendance:leave:approve` | 1458 | 5,6,8 | 2,5,6,8 | **2,5,6,8** | **+2** | 中：请假审批下放到门店管理员 |
| A5 | `attendance:check` | 1453 | 4,5,6,8 | 2,4,5,6,8 | **2,4,5,6,8** | **+2** | 低：打卡为自助动作 |
| A6 | `attendance:leave:submit` | 1457 | 4,5,6,8 | 2,4,5,6,8 | **2,4,5,6,8** | **+2** | 低：提交为自助动作 |
| A7 | `attendance:leave:list` | 1459 | 4,5,6,8 | 2,4,5,6,8 | **2,4,5,6,8** | **+2** | 低：查看为自助动作 |
| A8 | `attendance:stat` | 1455 | 5,6,8 | 2,5,6,8 | **2,5,6,8** | **+2** | 低：统计为管理层口径 |
| A9 | `attendance:config:list` | 1451 | 5,6,8 | 2,5,8 | **2,5,6,8** | **+2** | 低：**D2 恢复 BOSS(6)** |

> **A 组小结（D2 后）**：考勤 9 码 = **36 条**。STORE_ADMIN(2) 拿到 7 条自助/只读/审批码（check / list / stat / leave:submit / leave:list / leave:approve / config:list），**不含** A1 `attendance:edit` 与 A2 `attendance:config:edit`（纠错与规则写入仅 5,8）。
>
> ⚠️ **残留风险（按 D2 最终口径仍然存在）**：A1 / A2 给到 MANAGER(5) 后，**店长可改考勤记录与考勤规则 → 可间接影响工资计算基数**，而这两条链路**均无 `@SysOpLog` 审计**。

#### (b) 提成 24 码：I184 原建议 vs 本方案 vs D2 最终口径

| # | 码 | `permission_id` | I184 原建议角色 | 拍板前本方案 | **D2 最终口径** | 与原建议差异 | 风险（按最终口径） |
|---|---|---|---|---|---|---|---|
| B1 | `commission:salary:pay` | 1126 | 5,8 | 5,8 | **6,8** | **去 MANAGER(5)** | 中：工资发放仅老板 + 系统管理员 |
| B2 | `commission:salary:confirm` | 1125 | 5,8 | 5,8 | **6,8** | **去 MANAGER(5)** | 中：工资确认仅老板 + 系统管理员 |
| B3 | `commission:salary:batch-pay` | 1337 | 5,8 | 5,8 | **6,8** | **去 MANAGER(5)** | 中 |
| B4 | `commission:salary:batch-confirm` | 1339 | 5,8 | 5,8 | **6,8** | **去 MANAGER(5)** | 中 |
| B5 | `commission:salary:adjust` | 1336 | 5,8 | 5,8 | **5,6,8** | **补 BOSS(6)** | 中：工资调整权扩大 |
| B6 | `commission:settlement:pay` | 1340 | 5,8 | 5,8 | **6,8** | **去 MANAGER(5)** | 中：结算发放仅老板 + 系统管理员 |
| B7 | `commission:settlement:confirm` | 1344 | 5,8 | 5,8 | **6,8** | **去 MANAGER(5)** | 中 |
| B8 | `commission:settlement:batch-pay` | 1341 | 5,8 | 5,8 | **6,8** | **去 MANAGER(5)** | 中 |
| B9 | `commission:settlement:batch-confirm` | 1327 | 5,8 | 5,8 | **6,8** | **去 MANAGER(5)** | 中 |
| B10 | `commission:salary:export` | 1333 | 5,6,7,8 | 5,6,7,8 | **5,6,8** | **去 OPERATOR(7)** | 低：导出收敛 |
| B11 | `commission:settlement:export` | 1342 | 5,6,7,8 | 5,6,7,8 | **5,6,8** | **去 OPERATOR(7)** | 低：导出收敛 |
| B12 | `commission:calculate` | 1328 | 5,6,8 | 5,6,8 | **5,6,8** | 无差异 | — |
| B13 | 其余 12 个提成管理码 | — | 5,6,8 | 5,6,8 | **5,6,8** | 无差异 | — |
| B14 | `commission:settlement:detail` | 1343 | 5,6,8 | 5,6,8 | **5,6,8** | 无差异 | — |

> **B 组小结（D2 后）**：提成 24 码 = **64 条**。资金发放类（pay / confirm / batch-pay / batch-confirm，8 码）= **6,8**；导出类 2 码 = **5,6,8**；其余管理类 = **5,6,8**。
>
> ⚠️ **资金类 8 码不给 MANAGER(5)** —— 店长不能直接确认/发放工资。与 `pos:order:pay` = 5,8 口径**不一致**，D2 已拍板为「老板发放、店长只确认」的对齐方案 —— 实际实现为「老板确认、老板发放」，店长既不确认也不发放。

#### (c) 3 项高敏动作：D2 拍板结果

| # | 事项 | D2 拍板结果 | 实到 |
|---|---|---|---|
| H1 | 工资 / 结算的 pay + confirm + batch 类码 | **6,8**（不给 5） | ✅ 已落 |
| H2 | 导出类 2 码 | **5,6,8**（去 7） | ✅ 已落 |
| H3 | `attendance:edit` + `attendance:config:edit` | **5,8**（不含 STORE_ADMIN(2)） | ✅ 已落 |

> **口径修正说明**：早期汇报中「4 处与 I183 建议不同」是**下界**；逐码展开后为 **9 处**（全部考勤码，源于统一引入 STORE_ADMIN(2)），其中 A1 / A2 / A9 三处不止「多一个角色」而是集合变化。

---

## 1.3 绑定 SQL（已执行，2026-11-22）

### 1.3.1 写法约束自查

| 约束 | 落实方式 |
|---|---|
| 表结构无 `permission_code` 列 | 逐条显式给 `role_id` + `permission_id` 常量对，注释中标 `permission_code` |
| 唯一键 `uk_role_permission(role_id, permission_id)` | 使用 `INSERT IGNORE` 幂等，重复执行不报错 |
| 禁止按时间范围批量删 | 回滚脚本逐条白名单 `DELETE`，**无 `create_time` 条件** |
| 禁止全表 `INSERT ... SELECT` / 按 role_id 全量复制 | 无此类写法 |
| `commission:simulation` 建码幂等 | 先 `SELECT COUNT(*) ... WHERE permission_code='commission:simulation'` 判存在，再 `INSERT` |

### 1.3.2 正向 SQL（D1：33 码 / 100 条 + D5a：1 码 / 3 条）

执行文件：`audit/evidence/F171-attendance-commission-perm-alignment/raw/21-i183-i184-bind-exec-20261122.sql`

**前置检查（`21-*.out.txt` 实录）**：

- `PRECHECK_simulation_exists` = **0**（建码前不存在）
- `PRECHECK_total_now` = **1267**（执行时点已是绑定后状态，此为幂等重跑证据）
- `PRECHECK_permission_ids` = 34 个码的 `permission_id` 实时回查值 —— 与 §1.1-A 表逐条一致
- `PRECHECK_bound_nonzero` = 34 个码的当前绑定数 —— 与 §1.1-A 表逐条一致

**执行结果**：`sys_role_permission` **1164 → 1267**；`sys_permission` **509 → 510**（新增 `commission:simulation` id=1631）。

> 注：`21-*.out.txt` 中的 `PRECHECK_bound_nonzero` 非零、`PRECHECK_total_now` = 1267，说明该脚本在**已绑定状态**下被再次执行（幂等重跑），`INSERT IGNORE` 使其无副作用。实际绑定动作发生在 2026-11-22 05:27–05:29 的首次执行批次。

### 1.3.3 回滚 SQL

执行文件：`audit/evidence/F171-attendance-commission-perm-alignment/raw/22-i183-i184-rollback-20261122.sql`

- 步骤 1：按 `permission_code` 定位删 D5a 的 3 条绑定（`commission:simulation` × role 5,6,8）
- 步骤 2：逐条删 D1 的 100 条绑定，每条 `DELETE` 前一行注释同时给出 `permission_code`、`permission_id`、删除的 role 列表，与该行 `WHERE` 条件**逐字一致**
- 步骤 3：删 `commission:simulation` 权限码
- 预期：`sys_role_permission` 1267 → 1164；`sys_permission` 510 → 509

### 1.3.4 D5a：新码 `commission:simulation`

| 项 | 值 |
|---|---|
| `permission_id` | 1631（执行时 `MAX(id)` = 1624 → 新分配 1625；**dry-run 阶段分配 1626，正式执行分配 1631**） |
| `permission_type` | `API` |
| 绑定 | role 5, 6, 8 |
| 落地后 `sys_permission` 总数 | **510** |

> ⚠️ **`permission_id` 不稳定**：dry-run（`18-*.out.txt`）中 `commission:simulation` id = **1626**，正式执行后为 **1631**。因 `sys_permission.id` 为 auto_increment，dry-run 事务回滚后自增值不回收。**引用该 id 的任何外部引用须以正式执行后的 1631 为准**。

### 1.3.5 正反双向闭环 dry-run

执行文件：`audit/evidence/F171-attendance-commission-perm-alignment/raw/23-i183-i184-roundtrip-dry-run-20261122.sql`

| 步骤 | `sys_role_permission` | `sys_permission` | 分角色（1/2/4/5/6/7/8） |
|---|---|---|---|
| 绑定后 | **1267** | **510** | 118 / 47 / 54 / 304 / 224 / 105 / 415 |
| 回滚后 | **1164** | **509** | 118 / 40 / 50 / 278 / 192 / 105 / 381 |
| `commission:simulation` 存在性 | — | 回滚后 **0** | — |

→ **完全还原，无残留**。

---

## 1.4 验证 SQL（区分「绑了」与「绑对了」）

执行文件：`audit/evidence/F171-attendance-commission-perm-alignment/raw/24-i183-i184-verify-v1-v5-20261122.sql`
执行输出：`audit/evidence/F171-attendance-commission-perm-alignment/raw/24-i183-i184-verify-v1-v5-20261122.out.txt`

> **判定标准不是「绑定数 > 0 就算通过」，而是「每个码的 role_id 集合恰好等于预期集合」**。

### V1　总量与分角色计数

**期望**：`total = 1267`；分角色 = `1:118, 2:47, 4:54, 5:304, 6:224, 7:105, 8:415`
**实到**：`V1_total = 1267`；分角色与期望**逐项一致** ✅

### V2　每个码的 role_id 集合恰好等于预期集合（集合全等，非计数）

**实到**：34 行全部 **PASS**（`actual_roles` 与 `expected_roles` 字符串全等）✅

### V3　「预期该绑但没绑」检查（NOT IN 语义 / LEFT JOIN ... IS NULL）

**期望**：**0 行**
**实到**：**0 行** ✅

### V4　「绑了但不在预期内」检查（LEFT JOIN ... IS NULL）

**期望**：**0 行**
**实到**：**0 行** ✅

### V5　逐码绑定覆盖（确认无「一个都没绑」的码）

**期望**：34 行，`bind_cnt` 与 §1.1-A「实到 role_id 集合」条数一一对应
**实到**：34 行全部匹配 ✅（`attendance:check`=5 / `attendance:config:edit`=2 / … / `commission:simulation`=3）

### V6　运行期复核（**未执行 —— 全部 UNVERIFIED**）

> **数据面已改、运行期未验。** V1–V5 是 SQL 层集合校验，可证明「绑定表内容正确」；V6 是 HTTP 层行为验证，可证明「运行时实际放行/拦截符合预期」。**前者通过不能推出后者通过。**

**未验原因**：① Cursor 无浏览器操作能力（验收规则「能力边界」）；② 无生产库连接能力，运行期验证只能在**部署后的应用实例**上执行，而当前开发库绑定动作的运行环境未经确认；③ 本批次决策明确**不强行模拟**。

| # | 验证项 | 预期 | 状态 |
|---|---|---|---|
| V6-1 | `clerk01` → `POST /attendance/check-in` | 200（不 403） | **UNVERIFIED** |
| V6-2 | `clerk01` → `GET /attendance/leave/list` | 200 | **UNVERIFIED** |
| V6-3 | `clerk01` → `GET /attendance/statistics` | **403**（CLERK 不在 `attendance:stat` 预期集合 —— 反向验证 V2 不是「全员放行」） | **UNVERIFIED** |
| V6-4 | `manager01` → `GET /attendance/statistics` | 200 | **UNVERIFIED** |
| V6-5 | `manager01` → `GET /attendance/records?storeId=5`（非本门店） | **403**（数据范围拦截不被补绑定绕过 —— I186 联动） | **UNVERIFIED** |
| V6-6 | `manager01` → `GET /auth/me/permissions` | `attendance*` 非空且与 §1.1-A 集合一致 | **UNVERIFIED** |
| V6-7 | `boss01` → `GET /auth/me/permissions` | 含 `attendance:leave:approve`、含 `commission:rule:list` | **UNVERIFIED** |
| V6-8 | `clerk01` → `GET /commission/salary/page` | **403**（CLERK 不在提成任何集合内） | **UNVERIFIED** |
| V6-9 | `operator01` → 工资导出 | 允许（`salary:export` 已去 role 7 → 预期应为 **403**，需确认前端/后端对该码的消费位置） | **UNVERIFIED** |

> ⚠️ V6 全部为**待执行项**，本阶段无运行证据 → 按验收规则标 **UNVERIFIED**，**不得声称已修复或已验证**。

---

## 1.5 V6 未验项清单（汇总）

| # | 未验项 | 对应接口/操作 | 预期结果 | 阻塞级别 |
|---|---|---|---|---|
| 1 | 店员打卡放行 | `POST /attendance/check-in`（clerk01） | 200 | 低（正向） |
| 2 | 店员查看请假列表 | `GET /attendance/leave/list`（clerk01） | 200 | 低（正向） |
| 3 | 店员被拒于统计 | `GET /attendance/statistics`（clerk01） | 403 | **中（反向验证，防「全员放行」）** |
| 4 | 店长看统计 | `GET /attendance/statistics`（manager01） | 200 | 低（正向） |
| 5 | 跨门店数据范围拦截 | `GET /attendance/records?storeId=5`（manager01） | 403 | **高（越权风险，I186 联动）** |
| 6 | 权限回显一致性 | `GET /auth/me/permissions`（manager01） | 与 §1.1-A 集合一致 | 中 |
| 7 | 老板权限回显 | `GET /auth/me/permissions`（boss01） | 含 `attendance:leave:approve`、`commission:rule:list` | 中 |
| 8 | 店员被拒于提成 | `GET /commission/salary/page`（clerk01） | 403 | 中（越权风险） |
| 9 | 运营导出被拒 | 工资导出（operator01） | 403（D2 已去 role 7） | 低 |

> **汇总状态**：9 项 **全部 UNVERIFIED**。**数据面已改、运行期未验。** 阻塞级别最高的是 V6-5（跨门店数据范围），因补绑定本身不改变 `dataScope` 机制，但补绑定后可达端点变多 —— I186 记录的 `DataScopeInterceptor` 覆盖路径不全问题仍 **OPEN**。

---

## 1.6 范围外观察项（前端缺码 15 个）

见 §1.1-D。**本批次不处理、不绑定、不修改前端。** 仅登记，供后续批次立项。

---

## 1.7 证据文件与执行链

| 编号 | 文件 | 内容 | 状态 |
|---|---|---|---|
| `15-*.sql` / `.out.txt` | `15-i183-i184-binding-plan-live-db-20261120` | 方案阶段的实时库取数（只读） | 归档 |
| `16-*.sql` / `.out.txt` | `16-i183-i184-tech-confirm-20261120` | 技术确认 T1–T3（GROUP_CONCAT 顺序、回滚注释、MySQL 版本） | 归档 |
| `17-*.sql` / `.out.txt` | `17-i183-i184-d2-count-check-20261122` | D2 后条数核算（**含 THEN 3 算错，已被 20-*.sql 修正**） | 作废，以 20-*.sql 为准 |
| `18-*.sql` / `.out.txt` | `18-i183-i184-dry-run-20261122` | 执行前 dry-run（事务内 ROLLBACK） | 归档 |
| `19-*.sql` / `.out.txt` | `19-i183-i184-tuple-count-20261122` | 正向 SQL 元组数核算（`total_tuples = 100`） | 归档 |
| `20-*.sql` / `.out.txt` | `20-i183-i184-d2-count-check-fixed-20261122` | **修正 17-*.sql 的 D5a 逐角色增量算错**（THEN 3 → THEN 1） | **权威条数源** |
| `21-*.sql` / `.out.txt` | `21-i183-i184-bind-exec-20261122` | **D1 + D5a 正式执行脚本与输出** | **权威执行源** |
| `22-*.sql` | `22-i183-i184-rollback-20261122` | 配套回滚脚本（精确白名单，无时间范围条件） | 权威回滚源 |
| `23-*.sql` / `.out.txt` | `23-i183-i184-roundtrip-dry-run-20261122` | 正向 + 回滚双向闭环 dry-run（1267↔1164 完全还原） | 权威闭环源 |
| `24-*.sql` / `.out.txt` | `24-i183-i184-verify-v1-v5-20261122` | **绑定后 V1–V5 验证**（34 码全 PASS / V3 漏绑 0 / V4 多绑 0） | **权威验证源** |
| `plan-doc-encoding-corruption-20261122.md` | — | 原文档编码损坏的事实记录与恢复边界 | 事实记录 |

**执行链**：`18-*.sql` dry-run → `19-*.sql` 元组核算 → `20-*.sql` 条数修正 → `21-*.sql` 正式执行 → `23-*.sql` 闭环验证 → `24-*.sql` V1–V5 验证。

**基线与污染标记（I198 已确认事实）**：执行前基线 `sys_role_permission` 总行数 = **1164**、role 5（MANAGER）= **278** —— 与 I198 记录一致（无额外污染）。I198 另记录的历史证据污染（12 个 role 5 临时提权、3 个越界角色绑定）**不属于本批次的 103 条**，本批次的增量已由 V2 集合全等 + V4 越界 0 行双重排除污染。

---

## 1.8 阶段划分与执行状态

| 阶段 | 内容 | 前置批准 | 状态 |
|---|---|---|---|
| 阶段 1 | 只读调查 + 方案 + 待批准清单 | — | ✅ 已完成 |
| 阶段 2a | D5a：补 `commission:simulation` 权限码 + 绑定 5,6,8 | D5a（随 D1 同批） | ✅ **已执行** |
| 阶段 2b | D1：执行正向绑定（33 码 / 100 条） | D1 + D2 | ✅ **已执行** |
| 阶段 2c | 执行 §1.4 验证（V1–V5 SQL） | 绑定后立即执行 | ✅ **已执行，全 PASS** |
| 阶段 2c′ | V6 运行期复核（9 项） | 需运行环境 | ❌ **未执行 —— UNVERIFIED** |
| 阶段 3 | 前端路由码替换（§1.1-B 映射表落地） | D4 已批「另批处理」 | ⏸️ 本批不执行 |
| 阶段 4 | 孤儿码处置（`commission:list` / `commission:settlement:audit`） | D3 已批「不绑定 + 登记」 | ⏸️ 登记内容见 §4.1，issue 文件待落盘 |
| 阶段 5 | 生产库执行 | 需评估生产表结构（§3 R5） | ❌ **未执行** |

---

## 2. I185 与 I183/I184 的重叠情况（按要求停下报告，未自行合并）

| 维度 | 结论 |
|---|---|
| **字面码重叠** | **无**。I185 是 8 个码（`employee:attendance`、`employee:attendance:list`、`employee:leave`、`employee:attendance:stat`、`employee:attendance:config`、`commission:rule`、`commission:rule:config` + `commission:simulation` 前端路由位），与 I183/I184 的 34 个后端码无字面交集 |
| **语义耦合（真实重叠）** | **有，且是 1:1 耦合**：I185 的 5 个考勤前端码只能映射到 I183 的 5 个后端码（§1.1-B B1–B5）；I185 的 2 个提成前端码映射到 I184 的 `commission:rule:list`（B6–B7） |
| **本方案的处理** | **未自行合并**。§1.4 只处理后端码绑定；前端 `meta.permission` 的替换单列 §1.1-B，未写入任何前端文件；C1/C2 单列 §1.1-C |
| **需要用户确认的范围问题** | ① 是否在本批次同时改前端路由码（改 = 业务代码改动，需授权）；② `commission:simulation` 是**新增**还是**改用** `commission:rule:list`（D2 已选：新增，id=1631） |

---

## 3. 风险与残留

| # | 风险 | 影响 | 缓解 / 现状 |
|---|---|---|---|
| R1 | 绑定让 `clerk01` 等账号**新增**了此前不可见的接口可达性 | `attendance:check` 等码同时被前端用作菜单码，可能出现新菜单项 | 本批 9 考勤码 + 24 提成码的 `permission_type` 为 `button` / `API`，**均非 `menu`** —— 不会新增菜单（实时查询确认：34 码中 `menu` 型 0 个） |
| R2 | 数据范围（`dataScope`）与权限码解耦，补绑定可能暴露跨门店数据 | 越权读取 | V6-5 专项复核；I186 记录的 `DataScopeInterceptor` 覆盖路径不全问题仍 **OPEN** → **V6-5 未验** |
| R3 | 提成资金类码（pay/confirm）**不给** MANAGER(5) | 与 `pos:order:pay` = 5,8 口径不一致，店长无法直接确认/发放工资 | D2 已拍板为「老板发放、店长只确认」的对齐方案 —— 实际实现为「老板确认、老板发放」，店长既不确认也不发放 |
| R4 | 提成导出类码已**去掉** OPERATOR(7) | 运营不能导出工资/结算数据 | D2 拍板收敛为 5,6,8；V6-9 待验 |
| R5 | 生产库结构可能与本机不同 | 脚本在生产报错或写错 | 生产执行前必须在目标库跑 `SHOW COLUMNS FROM sys_role_permission;` 与 `SHOW INDEX FROM sys_role_permission;` |
| R6 | 补绑定后 `PermissionAutoRegistrar` 启动期行为 | 可能重复插入或冲掉绑定 | 静态确认 `PermissionAutoRegistrar.java:73-110` 只做 `INSERT` 到 `sys_permission`，**不写** `sys_role_permission` → 无冲突 |
| R7 | 2 个孤儿码保持 0 绑定 | `commission:list` / `commission:settlement:audit` 在库内无任何端点消费 | D3 已批「不绑定 + 登记」；登记内容见 §4.1，issue 文件本轮尚未落盘 |
| R8 | `commission:simulation` 权限码已入库但**后端不消费** | 该码有绑定（5,6,8）却无端点校验它 —— 绑定形同虚设 | D5b 本批不执行（改业务代码 + 影响现有调用方 200→403）；关联 I187 |
| R9 | 前端路由重名 | `router/index.ts:114` 与 `:58` 均为 `path: 'commission/rules'` + `name: 'CommissionRules'`，后者覆盖前者 → 「提成规则配置」菜单实际不可达 | D4 另批处理时一并解决 |

---

## 4. 待批准清单 / 遗留决策

| # | 事项 | 内容 | 状态 | 风险 |
|---|---|---|---|---|
| **D1** | 绑定范围 | §1.2 绑定 33 码 / 100 条 | ✅ **已批准并执行** | 低 |
| **D2** | 角色分配口径 | §1.2.3 A1–A9 + B1–B11 + H1/H2/H3 | ✅ **已逐项拍板并执行** | 中～高 |
| **D3** | 孤儿码处置 | `commission:list`(1118)、`commission:settlement:audit`(1128) 无后端引用 | ✅ **已批准：不绑定，另立 issue 登记**（内容见 §4.1，issue 文件待落盘） | 低 |
| **D4** | 前端路由码替换 | §1.1-B 7 条映射 + `:58`/`:114` 路由重名 | ✅ **已批准：另批处理**（前端属业务代码改动；本批只做后端绑定，映射表已出） | 中（改业务代码） |
| **D5a** | C1 补码（数据改动） | 新增 `commission:simulation`（id=1631）+ 绑定 5,6,8 | ✅ **已批准并执行** | 低 |
| **D5b** | C2 补码 + 加 `@RequirePermission`（代码改动） | `/commission/tier/*`（3 端点）、`/commission/condition/*`（3 端点）、`/commission/simulation`（2 端点）共 **8 端点** | ⏸️ **本批不执行，只登记**（关联 I187；影响现有调用方 200 → 403） | 中高（改业务代码 + 破坏性） |
| **D6** | V6 运行期复核（9 项） | §1.5 | ⏸️ **本批收口，标 UNVERIFIED** | 中 |
| **D7** | 生产库执行 | §1.3.1 / R5 | ❌ **未执行** | 高（生产影响） |

### 4.1　D3 落地：孤儿码登记内容（I199 定稿素材）

| 码 | `permission_id` | 事实 | 处置 |
|---|---|---|---|
| `commission:list` | 1118 | 在 `sys_permission`，但**后端无任何 `@RequirePermission("commission:list")` 引用**（全仓检索 0 处命中）；`CommissionController` 实际使用 `commission:record:list` | **不绑定**；登记为孤儿码 |
| `commission:settlement:audit` | 1128 | 同上，全仓检索 0 处命中；结算审核实际走 `commission:settlement:confirm` | **不绑定**；登记为孤儿码 |

**关联的第二个缺口（本批同批登记）**：`CommissionTierController`（3 端点）、`CommissionConditionController`（3 端点）、`CommissionSimulationController`（2 端点）三个控制器**零 `@RequirePermission` 注解** —— `commission:tier:*` / `commission:condition:*` 权限码在 `sys_permission` 中不存在，且后端无任何等价码（§1.1-C C2/C3）。D5a 已为 `commission:simulation` 建码（1631）并绑定，但后端未加注解 → 该码当前**无端点消费**。

> **关于 issue 编号**：按项目惯例 issue 编号需与 `audit/issues/issue-register.md` 登记台账对齐。当前 `audit/issues/` 下**最高编号为 I198**，新登记应从 **I199** 起。`audit/issues/issue-register.md` 属**治理基线保护文件**（规则 18），未经单独批准不得写入 → **issue 文件已按用户单独批准落盘为 `audit/issues/I199-commission-orphan-perm-codes-and-unauthenticated-endpoints.md`（内容为本节逐字摘出），但 `issue-register.md` 台账仍待单独批准后同步 —— 本文档不写「已登记为 I199」**。

---

## 附录 A　实时查询原始语句与结果

所有查询在开发库执行：`docker exec meiyetec-mysql mysql --default-character-set=utf8mb4 -uroot -proot123456 meiyetec -N -B -e "..."`

### A.1　绑定矩阵（34 码）

```sql
SELECT p.permission_code, p.id, p.permission_type,
       IFNULL(GROUP_CONCAT(DISTINCT rp.role_id ORDER BY rp.role_id),'NONE')
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code IN (
  'attendance:check','attendance:list','attendance:stat','attendance:edit',
  'attendance:config:list','attendance:config:edit','attendance:leave:submit',
  'attendance:leave:list','attendance:leave:approve',
  'commission:rule:add','commission:rule:edit','commission:rule:delete',
  'commission:rule:status','commission:rule:detail','commission:rule:stat',
  'commission:calculate','commission:record:stat','commission:salary:add',
  'commission:salary:stat','commission:salary:adjust','commission:salary:confirm',
  'commission:salary:pay','commission:salary:batch-confirm','commission:salary:batch-pay',
  'commission:salary:export','commission:settlement:add','commission:settlement:detail',
  'commission:settlement:stat','commission:settlement:confirm','commission:settlement:pay',
  'commission:settlement:batch-confirm','commission:settlement:batch-pay',
  'commission:settlement:export','commission:simulation')
GROUP BY p.permission_code, p.id, p.permission_type
ORDER BY p.permission_code;
```

返回见 §1.1-A 表。

### A.2　总量与分角色

```sql
SELECT COUNT(*) FROM sys_role_permission;
-- 1267

SELECT rp.role_id, COUNT(*) FROM sys_role_permission rp
GROUP BY rp.role_id ORDER BY rp.role_id;
-- 1:118  2:47  4:54  5:304  6:224  7:105  8:415
```

### A.3　孤儿码绑定数

```sql
SELECT p.permission_code, COUNT(rp.id)
FROM sys_permission p LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code IN ('commission:list','commission:settlement:audit')
GROUP BY p.permission_code;
-- commission:list:0  commission:settlement:audit:0
```

---

## 附录 B　与用户指令的偏差说明

| # | 偏差 | 原因 |
|---|---|---|
| 1 | 本文档不声明「已登记为 I199」 | `audit/issues/I199-commission-orphan-perm-codes-and-unauthenticated-endpoints.md` 已按用户单独批准落盘（内容为 §4.1 逐字摘出）；但 `audit/issues/issue-register.md` 台账**尚未同步**（治理基线保护文件，待单独批准），故不写「已登记为 I199」 |
| 2 | 本文档 §1.5 标注 9 项 V6 全部 UNVERIFIED，未声称已验证 | 无浏览器操作能力、无生产库连接能力；且用户决策 3 明确「不强行模拟」 |
| 3 | §1.1-D 列出 15 个前端缺码，但未处理 | 范围外观察项（D4 另批处理） |
| 4 | `commission:simulation` 的 `permission_id` 在 dry-run（1626）与正式执行（1631）中不同 | `sys_permission.id` 为 auto_increment，dry-run 事务回滚后自增值不回收。正式值为 **1631** |

---

## 附录 C　边界声明

- 本文档**只做方案 + 执行结论记录**，不含对 `audit/issues/issue-register.md`、`audit/baseline/*.md`、`docs/governance/*.md` 的写入。
- **未修改**任何业务代码（后端 Java / 前端 Vue / 前端路由）、**未修改**数据库 schema、**未新增**任何可执行脚本。
- 本批次执行的数据变更仅 `21-*.sql`（`INSERT IGNORE` 到 `sys_role_permission` + 1 条 `INSERT` 到 `sys_permission`）与 `24-*.sql`（纯 `SELECT` 验证）。
- **未触及**用户已验证页面 `/dashboard`、`/mh/visual`、`/mh/consent` 及其数据接口。
- 生产库**未执行**本批次任何 SQL。
