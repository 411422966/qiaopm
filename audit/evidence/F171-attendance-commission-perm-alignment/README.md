# F171 — 考勤 + 员工提成「权限对齐」专项只读调查（batch2-C）

> **性质**：**只读调查**（未修改任何业务代码、未执行任何写操作、未执行任何 DDL）
> **调查日期**：2026-11-16
> **执行者**：Cursor
> **绑定基线**：HEAD `56d59a2` — `docs(audit): I180 绑定基线同步 cfa7909 + I182 InAppSender 脏数据登记`
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`（2026-11-16 02:00，24.3 MB）；本次调查全程只读，未产生新快照
> **环境**：`audit/evidence/env.md`
> **运行栈**：docker `meiyetec-mysql`（MySQL 8.0，库 `meiyetec`）/ `meiyetec-redis` / `meiyetec-api`（8080，容器内 `/app/app.jar`，构建于 2026-11-15 23:02）/ `meiyetec-web`
> **关联**：I180（Parked）、I181、I182（上一批已登记）；本批新建 **I183 – I189**，**第四轮拆出 I191**（N8 请假落库缺失）

---

## 0. 调查目标与边界

| 项 | 内容 |
|---|---|
| 目标 | 回答「考勤 / 员工提成」两个模块的权限体系是否闭环：① 权限码是否全部入库且被角色绑定 ② 前后端码是否同源 ③ 是否存在缺鉴权接口 ④ 数据范围是否越权 ⑤ 种子 SQL 与运行期库是否一致 |
| 模块边界（只查这两块） | 控制器 `AttendanceController` / `AttendanceConfigController` / `LeaveRequestController` / `CommissionController` / `CommissionTierController` / `CommissionConditionController` / `CommissionSimulationController`；服务 `AttendanceRecordServiceImpl` / `AttendanceConfigServiceImpl` / `LeaveRequestServiceImpl` / `CommissionCalculationService` / `CommissionSettlementService` / `CommissionBatchService`；实体 `AttendanceRecord` / `AttendanceConfig` / `LeaveRequest` / `Commission*` |
| 不在范围 | 不改代码、不改配置、不做人工验收、不连生产库、不碰 `/dashboard` `/mh/visual` `/mh/consent` |
| 产出 | 59 个 endpoint 静态矩阵 + 运行期鉴权矩阵 + 4 类差异清单 + **7 个新 Issue（I183–I189）** |

> ✅ **写操作**：**无**。本目录不含任何夹具写入、任何 `INSERT/UPDATE/DELETE/DDL`；所有写接口一律**未调用**。
> ✅ **不存在的兜底 ID**：需要按 ID 探测的路径统一使用 `99999999`（运行前已确认库内不存在，见 `raw/04c` 与 §5）。
> ✅ **可执行脚本**：**无入库脚本**。`raw/00-readonly-sql-batch.sql` 为**只读查询合集**，文件头已标注「非正式工具」，不进入 `audit/scripts/` 正式守门序列，不接入 CI。

---

## 1. 一句话结论

**不闭环。** 后端 37 个实际引用的权限码中 **33 个（89%）无任何角色绑定**——考勤 **9/9 全死**、提成 **24/28 全死**（含全部 14 个写接口）；
前端另用一套 `employee:attendance*` 命名，其中 **8 个码在 `sys_permission` 中根本不存在** → 考勤前端 5 个页面 + 提成 3 个页面**对所有非超管永久 403 不可达**。
最主要是三个缺口：**① 权限码未绑定角色（权限体系整体空转）② 前后端码空间双轨且前端码未入库 ③ 门店数据范围只在请求显式带 `storeId` 时才校验，缺省即退化为租户级**。

---

## 2. 交付物索引

| 文件 | 内容 |
|---|---|
| `raw/00-readonly-sql-batch.sql` | 本批全部只读 SQL 合集（非正式工具） |
| `raw/01-static-endpoint-matrix.md` | **endpoint × 权限码 × 角色绑定 对齐矩阵**（59 个 endpoint 逐条） |
| `raw/02-db-permission-codes-and-role-bindings.txt` | `sys_permission` 中 attendance/commission 全量码 + 角色绑定 |
| `raw/03-db-roles-scopes-and-data-distribution.txt` | 角色清单 / 角色数据范围 / 用户角色分布 / 业务表行数 |
| `raw/04-db-seed-vs-runtime-diff.txt` | 无角色绑定的码清单、菜单映射、样本分布 |
| `raw/04b-db-permission-binding-verification.txt` | **V1–V6 绑定复核**（37 码逐条绑定数 + 角色名） |
| `raw/04c-db-store-employee-distribution.txt` | 门店 / 员工 / 提成记录分布（判定越权可观测性） |
| `raw/05-runtime-authz-matrix.txt` | 运行期鉴权矩阵（admin / manager01 / boss01 / clerk01 × 22 个接口） |
| `raw/05b-runtime-authz-matrix-2.txt` | 运行期鉴权矩阵 2（细粒度码 + 越权参数组合） |
| `raw/06-runtime-datascope-probe.txt` | 数据范围探测（employeeId / storeId 由调用方指定） |
| `raw/06b-runtime-datascope-probe-3.txt` | **数据范围探测 3（storeId 缺省 vs 指定他店的决定性对比）** |
| `raw/07-runtime-ghost-endpoints-404.txt` | 前端调用但后端无 handler 的路由（404 实测） |
| `raw/08-db-super-admin-wildcard.txt` | SUPER_ADMIN 通配 `*` 的触发条件与实际持有码 |
| `raw/09-frontend-route-and-permission-codes.txt` | 前端路由 `meta.permission` / `v-permission` 使用 / API 路径 + **运行期 `/auth/me/permissions` 下发载荷** |
| `raw/10-m1-mapping-key-phone.sql` / `.out.txt` | **M1 映射键「手机号」只读核验**（Q2 前置）：`sys_user.phone` ∩ `pos_employee.phone` 交集 / 唯一性 / 冲突统计 |
| `raw/11-m1-mapping-key-alternatives.sql` / `.out.txt` | **M1 备用映射键只读核验**（`real_name`= `employee_name` / `username`=`employee_code` / LIKE 模糊 / `(tenant_id, phone)` 复合口径）+ `pos_employee` 按 tenant/store 分布 |

---

## 3. 发现汇总

| # | 发现 | 验证等级 | 关键证据 |
|---|---|---|---|
| F1 | **考勤模块 9 个权限码 100% 无角色绑定** → 22 个 endpoint 对所有角色 403，只有持 `*` 的超管可用 | **VERIFIED（运行期 + 静态）** | `raw/04b` V1（9 码 role_bindings=0）；`raw/05`（manager/boss/clerk 全部 403，admin 200）；`raw/09` H（manager01 attendance 码为空） |
| F2 | **提成模块 24/28 权限码无角色绑定**，含全部 14 个写接口（规则增删改停、工资生成/调整/确认/发放、结算生成/确认/发放/批量） | **VERIFIED（运行期 + 静态）** | `raw/04b` V1；`raw/05`（manager01 `commission:rule:stat` / `record:stat` / `salary:stat` / `settlement:stat` 全部 403，而 4 个 list 码 200） |
| F3 | **前端 8 个权限码在 `sys_permission` 中不存在**：`employee:attendance`、`employee:attendance:list`、`employee:attendance:stat`、`employee:attendance:config`、`employee:leave`、`commission:rule`、`commission:rule:config`、`commission:simulation` | **VERIFIED（运行期 + 静态）** | `raw/04b` V1（IN 列表无对应行）；`raw/04` W4（cnt=0）；`raw/09` A/H（router meta 与下发载荷） |
| F4 | **前端守卫因此永久拦截 8 个页面**：考勤 5 个（index/records/leave/statistics/config）+ 提成 3 个（rule-list/rule-config/simulation）；`hasPermission` 只认精确码或 `*` | **VERIFIED（静态 + 载荷实测）** | `router/index.ts:226-233`；`stores/permission.ts:62-66`；`raw/09` H（4 个非超管账号 attendance 码均为空） |
| F5 | **前后端权限码命名双轨**：后端 `attendance:*` ↔ 前端 `employee:attendance*`；后端 `commission:rule:list` ↔ 前端 `commission:rule` / `commission:rule:config`；前端 `v-permission` 指令**全仓 0 处使用**（按钮级权限完全缺失） | **VERIFIED（静态）** | `raw/09` A/B/C/D/E |
| F6 | **5 组前后端路由契约漂移**：前端调用的 `/commission/rules/{id}/toggle`、`/commission/records/export`、`/commission/records/batch/settle`、`/commission/records/batch/invalidate` 后端**无 handler**；`/commission/tier/*`、`/commission/condition/*`、`/commission/simulation*` 共 7 条路径后端**被门控关闭** | **VERIFIED（运行期 404）** | `raw/07`；`raw/06b` C 段；后端实为 `/commission/rules/{id}/status` |
| F7 | **三个门控控制器完全无 `@RequirePermission`**（`CommissionTierController` / `CommissionConditionController` / `CommissionSimulationController`，共 8 个 endpoint、其中 4 个写接口）。当前因 `feature.legacy-missing-ddl.enabled=false` 未注册（404）→ **潜伏**，一旦开关置 true 即裸奔 | **VERIFIED（静态 + 运行期 404）** | `CommissionTierController.java:13`、`CommissionConditionController.java:13`、`CommissionSimulationController.java:14`；`application.yml:151-152`；`raw/07` |
| F8 | **门店数据范围校验是「参数驱动」而非「范围驱动」**：`DataScopeInterceptor` 仅在请求显式携带 `storeId`/`tenantId` 时校验；缺省则查询**只带 tenant 条件** → 退化为租户级全量。实测：MANAGER 不带 `storeId` → 200 返回数据；带 `storeId=5` → 403 | **VERIFIED（运行期，决定性对比）** | `raw/06b` A/B 段；`raw/05b`；`CommissionController.java:224-231`（`if (storeId != null)` 无 else 分支） |
| F9 | **`employeeId` 完全无任何范围约束**：打卡、考勤查询、月报、请假列表、提成记录、工资报表均直接接受调用方传入的 `employeeId`，既不校验「是否本人」也不校验「是否本门店」 | **VERIFIED（运行期 + 静态）** | `raw/05b`（manager01 `?employeeId=99999999` → 200）；`raw/06` 考勤段 |
| F10 | **请假撤回复用审批权限码** `attendance:leave:approve`；且 `cancel` 的「无权取消他人申请」校验**可被伪造**（`employeeId` 由请求体传入，与 `request.employeeId` 比对即可绕过本人语义） | **VERIFIED（静态）+ 运行期未复现（无请假样本）** | `LeaveRequestController.java:64-68`；`LeaveRequestServiceImpl.java:84-99`；`attendance_leave_request` 0 行 |
| F11 | **种子与运行期漂移**：`migrate_r3_0_c_menu_role.sql:133-137` 明确声明给 MANAGER/BOSS 绑定 `commission:list` `commission:rule:add/edit/delete` `commission:salary:confirm/pay` `commission:settlement:audit` —— **运行期库中这 7 个码仍无任何角色绑定**（种子未生效） | **VERIFIED（运行期）** | `raw/04b` V1；`raw/04` X4 |
| F12 | **`sys_permission` 类型错位**：`commission:batch` / `commission:reversal` 类型是 `button`，却在前端 `router/index.ts:94-95` 当**菜单级** `meta.permission` 使用；`commission.rules` / `commission.records` / `commission.salary` / `commission.settlement` 才是真 menu 类型 | **VERIFIED（运行期）** | `raw/04b` V1；`raw/04` X5 |
| F13 | **BOSS 角色缺 2 个提成子菜单码**：`commission.batch` / `commission.records` 未绑定 BOSS → BOSS 的提成菜单树缺「提成记录」「结算批次」入口（但路由本身可达） | **VERIFIED（运行期）** | `raw/09` H（boss01 码表无这两项）；`raw/04` C 段 |
| F14 | **超管通配 `*` 的触发条件是复合的**：需 `codes` 含 `booking`/`member`/`setting` **或** `username == "admin"`，**且** 角色含 `SUPER_ADMIN`/`PLATFORM_ADMIN`。新建的 SUPER_ADMIN 角色若无这些码且用户名非 admin → **拿不到 `*`** | **VERIFIED（静态 + 运行期）** | `RbacContextLoader.java:78-90`；`raw/08` W1（SUPER_ADMIN 持有 booking/member/setting）；`raw/09` H（admin 含 `*`） |
| F15 | **方法不匹配被吞成 HTTP 200 + code 500**：`GET /attendance/leave/99999999/cancel`（路径存在但仅 PUT）→ `HttpRequestMethodNotSupportedException` 落入 `GlobalExceptionHandler.handleAny` → **HTTP 200 + body `code=500 "系统繁忙"`**（假成功语义） | **VERIFIED（运行期）** | `raw/06b` C 段末行；`GlobalExceptionHandler.java:98-103` |
| F16 | **M1 映射键「手机号」不可行**：`sys_user.phone` ∩ `pos_employee.phone` **交集 = 0 行**（5 个 `sys_user` 全部命中 0 条员工）；两表 `phone` 各自内部唯一（5/5、150/150 无重复），但**值域完全不同域**（`sys_user` = `13800138000` / `13800000001~4` 系统账号；`pos_employee` = `13900139001~6` / `139000xxxxx` 测试员工）。备用键 `real_name`=`employee_name` / `username`=`employee_code` / LIKE 模糊 / `(tenant_id, phone)` 复合口径**均为 0 行**；`sys_user` 无任何 `employee_id`/`staff`/`pos` 关联列，`pos_employee` 无 `user`/`account`/`login` 反向关联列 | **VERIFIED（只读查询）** | `raw/10` §2 / §5 / §11 / §12；`raw/11` C–I |
| F17 | **`pos_employee.tenant_id` 96% 为 NULL**：150 行中 **144 行 `tenant_id IS NULL`**（全部 `store_id=1`）、仅 6 行 `tenant_id=1` → M3 口径 `tenant_id = ctx.tenantId AND store_id IN ctx.storeIds` 会把这 144 行**全部判为越权** | **VERIFIED（只读查询）** | `raw/11` §H |

---

## 4. endpoint × 权限码 × 角色绑定 对齐矩阵（摘要）

完整 59 行矩阵见 `raw/01-static-endpoint-matrix.md`。

| 模块 | endpoint 数 | 权限码数 | 有角色绑定的码 | 死码 | 写接口可用性 |
|---|---|---|---|---|---|
| 考勤 | 22 | 9 | **0** | **9（100%）** | 5 个写接口（打卡×2、配置增改删、请假提交/审批/撤回）**全部不可用** |
| 员工提成 | 29 | 28 | 4 | **24（86%）** | **14 个写接口全部不可用**，仅 4 个列表可读 |
| 门控关闭 | 8 | 0（无鉴权） | — | — | 当前 404；开关置 true 即 **4 个写接口裸奔** |

**实测可用性（运行期）**

| 角色 | 考勤（9 码） | 提成（28 码） | 典型结果 |
|---|---|---|---|
| `admin`（SUPER_ADMIN + ADMIN，`*`） | ✅ 全部放行 | ✅ 全部放行 | 所有接口 200 |
| `manager01`（MANAGER，`dataScope=STORE`，`storeIds={1}`） | ❌ 全 403 | ⚠️ 仅 4 个 list 码可用 | `commission/rules/page` 200、`commission/rules/stats` 403 |
| `boss01`（BOSS，同上） | ❌ 全 403 | ⚠️ 同 MANAGER（且少 2 个菜单码） | 同上 |
| `clerk01`（CLERK，50 码） | ❌ 全 403 | ❌ 全 403 | 提成列表 403 |

---

## 5. 越权面实测明细（只读）

| 探测（账号 / 请求） | 结果 | 结论 |
|---|---|---|
| MANAGER `GET /commission/records/page`（**不带** `storeId`） | **200**，返回租户内提成记录 | 🔴 storeId 缺省 → 无门店过滤 |
| MANAGER `GET /commission/records/page?storeId=5` | **403** `无权访问该门店数据` | ✅ 带了 `storeId` 才校验 |
| MANAGER `GET /commission/records/page?employeeId=99999999` | **200** | 🔴 `employeeId` 无本人/门店约束 |
| MANAGER `GET /commission/salary/page`（不带 storeId） | 200 | 🔴 同上 |
| MANAGER `GET /commission/salary/page?storeId=5` | 403 | ✅ 显式带才拦 |
| MANAGER `GET /commission/settlement/page` / `?storeId=5` | 200 / 403 | 同上 |
| MANAGER `GET /commission/rules/page` / `?storeId=5` | 200 / 403 | 同上 |
| BOSS 同组探测 | 200 / 403 | 同 MANAGER |
| ADMIN `GET /attendance/records?storeId=5` | 200 | ✅ 预期（`dataScope=ALL`） |
| MANAGER `GET /attendance/records?storeId=5` | 403 | ✅ 显式带才拦；**不带则 403（权限码死）** |
| ADMIN `GET /attendance/today?employeeId=99999999` | 200（空数据） | 🔴 `employeeId` 不校验本人 |
| ADMIN `GET /attendance/leave/my?employeeId=99999999` | 200（空数据） | 🔴 同上 |
| ADMIN `GET /commission/records/page?employeeId=99999999` | 200（空数据） | 🔴 同上 |

> ⚠️ **观测限制**：`commission_record` 10 行**全部集中在 `store_id=1`**，`attendance_record` / `attendance_leave_request` / `commission_salary` / `commission_settlement` **均为 0 行**
> → **「跨门店泄露他人工资/提成」的实数据后果无运行样本可证**，只能停在**授权层结构性确认**（`if (storeId != null)` 无 else 分支）。
> 按验收规则，**实际数据泄露结论标 UNVERIFIED**，需在有他店提成的环境复测。

### 兜底 ID 使用声明

- 本批所有按 ID 探测一律使用 **`99999999`**（`attendance/leave/{id}`、`commission/records/page?employeeId=`、`commission/salary/page?employeeId=`）。
- 运行前已通过只读查询确认：`attendance_leave_request` 表 **0 行**、`attendance_record` **0 行** → `99999999` 必然不存在。
- **未使用任何真实业务主键**（如 `employeeId=4`、`ruleId=1` 之外的现存 id）作为「不存在」样本；`ruleId=1` 仅用于**存在性**读取（`GET /commission/rules/1`），不作为「不存在」样本。

---

## 6. 边界声明

- 本次调查**未修改任何业务代码**、**未执行任何写操作**、**未执行任何 DDL**、**未新增任何入库脚本**。
- **未打开浏览器**、**未连接生产库**、**未替代人工验收**；前端结论来自源码静态分析 + 运行期 `/auth/me/permissions` 载荷实测。
- 未核实：生产环境 `sys_role_permission` / `sys_permission` 是否与本机开发库同构；生产库 `attendance_record` / `commission_salary` 是否已有真实数据（若有，F8/F9 的实数据后果可被观测）。
- 未核实：`commission_batch` / `commission_reversal` / `commission_settlement` 三个前端路由对应的后端服务（`CommissionBatchService`、`commission_settlement_batch` 等）是否有独立控制器——本次范围内**未找到**对应 `@RequestMapping`，前端这三页调用 404。
- **未触达** `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面及其数据接口。
- **M1 映射键查询（`raw/10` / `raw/11`）**：全部为 `SELECT` / `SHOW`，**无任何写操作**，同样不进入 `audit/scripts/` 正式守门序列、不接入 CI；文件头已标注「非正式工具」。

---

**下一步**：按 I183–I189 的严重度排期。I183 / I184（权限码绑定）是**上线阻断级**——在绑定关系修复前，考勤与提成模块对全部业务角色不可用。
**M1 前置结论**：`phone` 映射键**不可行**（交集 0 行）→ M1 须改走「新增 `sys_user.employee_id` + 人工回填」；且 `pos_employee.tenant_id` 有 144 行 NULL，M3 实施前需先定这批行的归属口径。
