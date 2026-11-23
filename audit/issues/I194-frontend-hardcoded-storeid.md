# I194 — 前端 8 处硬编码 `storeId: 1`（+1 处关联缺陷），使 I186 第 1 批「缺省按门店集合过滤」在真实链路上被绕过

> **状态**：**Open（仅登记 + 只读调查结论，不改代码）** —— 本条**独立批次**处理，I186 第 1 批**不包含**前端改动
> **严重度**：**P1**（不是「越权漏洞」，而是**修复被绕过** —— 后端修好后，前端仍把 `storeId=1` 显式传下去，后端拿到的永远是显式单值 → 集合过滤形同虚设）
> **发现日期**：2026-11-16（I186 第 1 批修复草案 §4.3 核对前端调用点时发现）
> **本次更新**：2026-11-17（只读调查报告回写：逐处定性 + 新发现 2 处 + 前置依赖确认 + 修正方案 + 分批排期）
> **登记人**：Cursor
> **绑定基线**：HEAD `7a4e72a`（含 I186 第 1 批 `c37a549`）
> **验证等级**：**静态 VERIFIED**（前端源码行号 + 后端取参路径 + 控制器实现均可复核）；**运行期后果 PARTIAL**（I186 第 1 批的后端行为已运行期实测，见 `audit/evidence/F173-i186-batch1-runtime-verification/`；但**前端不改则修复在真实链路上仍不可达**，该结论为静态推导）
> ⚠️ **证据依赖临时授权，需重跑（2026-11-18 追加）**：本条引用的 `audit/evidence/F173-i186-batch1-runtime-verification/raw/01-runtime-verification.txt` **依赖 2026-11-16 23:34:53 的临时授权**（role 5 被授予 12 条 attendance / commission 权限码），该授权已于 **2026-11-17 23:36:34 撤销** → 清理后复检出现 **11 处 200 -> 403 状态翻转**（manager01，403 原因均为「权限不足: 需要权限码：X」），主验证 PASS **不可复现**，**不能单独作为 I186 验收证据**。本条「后端行为已实测」的表述据此降级为：**该运行期结果须在 I183 / I184 权限码绑定修复完成后重跑方可复现**。详见 `audit/issues/I198-i186-verify-evidence-tainted-and-backup-pollution.md` §A 与 `audit/evidence/F173-i186-batch1-runtime-verification/raw/03-post-cleanup-permission-recheck.txt`
> **关联**：**I186**（本条是其第 1 批的**前置阻塞项**）、I185（前后端权限码漂移，同属「前后端契约不同步」族）、I192（用户↔员工映射未定义，I194-1 的员工→门店反查受其影响）、I187（`/commission/simulation` 门控未解封）、I191（请假落库缺失）
> **用户决策（2026-11-16）**：登记为独立 Issue，**紧随 I186 第 1 批之后**上线，不得与第 1 批脱节
> **用户决策（2026-11-17）**：① 批准回写调查结论，**只写结论、不改代码**；② 本条暂不开工，先只读确认「后端是否已有按 `ctx.storeIds` 返回可访问门店列表的端点」；③ 分批按 I194-0 / I194-1 / I194-2 顺序推进，等第 ② 步确认后再排期；④ `pos/checkout.vue:638` **标注「另立项评估」，不纳入 I194**

---

## 0. 一句话描述

前端在**考勤 / 提成**范围内有 **8 处**把门店 ID 写死成 `1` 并作为**显式参数**发给后端；
I186 第 1 批修的是「**缺省 `storeId` 时按 `ctx.storeIds` 集合过滤**」—— 但前端**永远显式传 1** → **后端修复在真实链路上不会生效**。
另有 **1 处关联缺陷**：`views/attendance/records.vue` 的门店下拉 `storeList` **恒为空**，使「用户自选门店」这条路也不通。

---

## 1. 范围口径（本次明确）

| 口径项 | 数值 | 说明 |
|---|---|---|
| **I194 纳入范围** | **8 处硬编码 + 1 处关联缺陷** | 全部落在**考勤 / 提成**两个模块内（I186 第 1 批修复的作用域） |
| 涉及文件 | **6 个** | `attendance/index.vue`（2 处）、`attendance/leave.vue`（2 处）、`commission/salary.vue`、`commission/settlement.vue`、`commission/rule-list.vue`、`commission/simulation.vue` |
| **全仓总量（对照）** | **19 处 / 14 文件** | **8 处 / 6 文件** 属 I186 作用域 → 纳入本条；**其余 11 处 / 8 文件** 属其他模块 → **不在本条范围**，见 §6 |
| **口径说明（算术自洽）** | 19 = 8 + 11；14 = 6 + 8 | ① **8 处 / 6 文件**（纳入）= 7 处 `storeId: 1` **字面量** + `attendance/leave.vue:291` 的 `pendingCount(1)` **位置参数** 1 处；② **11 处 / 8 文件**（不纳入）= §6 表逐行合计，其中**含** `views/pos/checkout.vue:638`（标注「另立项评估」，**已计入这 11 处，不额外计数**）；③ 若只统计 `storeId: 1` **字面量**，全仓为 **18 处 / 14 文件**（= 7 + 11），与 19 的差额就是 `pendingCount(1)` 这处**非字面量**写法；④ **本口径不含 `|| 1` 兜底写法**（`views/stock/CheckCreateWizard.vue:173/247/283`、`views/setting/employee.vue:258` 共 4 处 / 2 文件）—— 该类为「取不到门店时退化为 1」，与「写死 1 并显式下发」是不同问题，如需纳入应另立条目 |
| **不纳入** | `views/pos/checkout.vue:638` | **标注「另立项评估」**，下单金额归属门店、影响面与本条不同，**不并入 I194 批次**。该处**已计入**下方「11 处 / 8 文件」之中，**不额外加 1** |

> ⚠️ 本条**只处理考勤 / 提成**。`google-maps` / `crm/care-review` / `report/caliber` / `resource/resource-schedule` / `technician/evaluation` / `ai-scheduler` / `setting/employee` 的硬编码**属另一类问题（表单默认值 vs 请求参数）**，另行评估。

---

## 2. 逐处定性表（8 处）

> 「后端现状」列的判定依据均为**源码级**；I186 第 1 批（`c37a549`）已把「缺省按 `ctx.storeIds` 集合过滤」落到 G1 端点，故「可删参数」= 删掉后端即按集合语义工作。

| # | 文件:行 | 调用 | 后端端点 | 接口性质 | 后端现状 | 定性 | 建议 |
|---|---|---|---|---|---|---|---|
| 1 | `attendance/index.vue:224` | `attendanceApi.statistics({ storeId: 1, … })` | `GET /attendance/statistics` | **读** | I186 第 1 批 A2 已修：缺省 → `ctx.storeIds` 集合 | **可删参数**（方案 A） | 改为不传 `storeId` |
| 2 | `attendance/index.vue:246` | `attendanceApi.checkIn({ …, storeId: 1 })` | `POST /attendance/check-in` | **写（单店语义）** | `AttendanceController.java:36` 取 body 的 `storeId`；`AttendanceRecordServiceImpl.checkIn` **直接 `record.setStoreId(storeId)`**（`:47`）→ 缺省会写 **NULL 门店** | **不可直接删**（需后端先补 employee→store 反查） | I194-1：后端按 `employeeId` 反查门店落库，前端才可不传 |
| 3 | `attendance/leave.vue:317` | `attendanceApi.submitLeave({ …, storeId: 1 })` | `POST /attendance/leave` | **写（单店语义）** | `LeaveRequestController.java:29-40` 同上，`storeId` 直接落 `attendance_leave_request.store_id` | **不可直接删**（同 #2） | I194-1：随 #2 一并处理 |
| 4 | `attendance/leave.vue:291`（**本次新发现**） | `leaveApi.pendingCount(1)` | `GET /attendance/leave/pending-count` | **读（单值必填）** | `LeaveRequestController.java:83-85`：`@RequestParam Long storeId` **必填**（缺省 → 400）→ 前端被迫写死 | **集合语义缺陷**（多店账号只统计门店 1 的待审批数） | I194-1：**后端**改 `storeId` 可选 → 按 `ctx.storeIds` 聚合；前端随之不传 |
| 5 | `commission/salary.vue:136` | `commissionApi.generateSalary({ storeId: 1, salaryMonth })` | `POST /commission/salary/generate` | **写（单店语义）** | I186 第 1 批专项 2 已改：**缺省** → 单店账号按其唯一门店生成（实测：门店 5 账号缺省 → 生成门店 5）；**多店账号 → 400**「当前账号可访问多个门店，请指定 storeId」 | **可删，但多店需选店** | I194-2：单店账号不传；多店账号加「选店」交互（方案 B/C） |
| 6 | `commission/settlement.vue:206` | `commissionApi.generateSettlement({ storeId: 1, salaryMonth })` | `POST /commission/settlement/generate` | **写（单店语义）** | `storeId` **仍为必填**（Q-G 决策 G1：保持必填）→ 实测缺省返回业务码 400「请求参数错误: storeId 必填」 | **必须保留显式值** | I194-2：**必须先有「选店」交互**（不能删参数） |
| 7 | `commission/rule-list.vue:119` | `commissionRuleApi.simulate({ …, storeId: 1 })` | `/commission/simulation` 族 | **读（试算）** | 归 **I187** 门控（控制器无鉴权、当前 404）→ 该调用**当前不可达** | **延后**（无实际后果） | I187 解封后随 I194-2 一并处理 |
| 8 | `commission/simulation.vue:224` | 表单初始化 `storeId: 1` | `/commission/simulation` 族 | **读（表单默认值）** | 同 #7 | **延后** | 同 #7 |

### 2.1 定性汇总

| 类别 | 处数 | 编号 |
|---|---|---|
| 可直接删 `storeId`（读接口，后端已支持集合） | 1 | #1 |
| 需后端先补 employee→门店反查（写接口） | 2 | #2 #3 |
| 需后端先改集合语义（读接口，必填参数） | 1 | #4 |
| 需先有「选店」交互（写接口，单店语义） | 2 | #5 #6 |
| 延后至 I187 解封 | 2 | #7 #8 |

---

## 3. 新发现两处（本次调查新增）

### 3.1 `views/attendance/leave.vue:291` — `pendingCount(1)` 写死门店 1

```291:292:frontend/meiyetec-web/src/views/attendance/leave.vue
    const res: any = await leaveApi.pendingCount(1)
    pendingCount.value = res.data || 0
```

- 后端 `LeaveRequestController.java:83-85`：`@GetMapping("/pending-count") public Result<Integer> pendingCount(@RequestParam Long storeId)` → **`storeId` 必填**，缺省直接 400（Spring `MissingServletRequestParameterException`）。
- 该端点此前被 I186 判定为「`storeId` 必填且已被拦截器覆盖」→ **排除在 I186 覆盖范围外**，因此**后端从来没有人给它做过集合语义改造**。
- 后果：多店账号（`ctx.storeIds = {1,5}`）看到的「待审批数量」**只统计门店 1** → 徽标/标签页计数与实际待办不一致（**静默少计**）。
- 另注：`api/attendance.ts:113-114` 的类型签名 `pendingCount: (storeId: number)` 把「必填」固化进了前端 API 层 → 改后端需同步改前端签名。

### 3.2 `views/attendance/records.vue:174` — 门店下拉 `storeList` **恒为空**

```173:174:frontend/meiyetec-web/src/views/attendance/records.vue
const employeeList = ref<any[]>([])
const storeList = ref<any[]>([])
```

- 模板 `records.vue:21-35` 有完整的「门店」`el-select`（`v-for="store in storeList"`、`placeholder="全部门店"`），但 **`storeList` 在整个文件中只有声明、没有任何赋值**（全文件仅 2 处引用：模板 `:29` + 声明 `:174`）→ **下拉永远渲染 0 个选项**。
- 影响：用户**无法在 UI 上选择/限定门店** → 「多店账号自己选店」这条路在本页面**不通** → 这也是 I194-1 / I194-2 需要「选店交互」时**必须先补的地基**。
- 同源兄弟项（**同因、同文件**，一并登记）：`employeeList`（`:173`）同样**从未赋值** → 员工下拉（模板 `:14-19`）也是空的。
- ⚠️ 该文件**本身不传硬编码 `storeId`**（`records.vue:219` `storeId: query.storeId || undefined`）→ 它是 I186 第 1 批「缺省按集合」路径的**正面受益页**，正因为下拉为空，用户实际只能看到集合全量、无法收敛到单店。

---

## 4. 前置依赖确认（2026-11-17 只读调查结论）

> 用户决策 ②：先查后端是否已有「按 `ctx.storeIds` 返回当前用户可访问门店列表」的端点。

### 4.1 结论：**没有**

| 候选端点 | 实现位置 | 行为 | 能否作为「可访问门店列表」 |
|---|---|---|---|
| `GET /auth/me/permissions` | `controller/RbacController.java:35-38` → `service/RbacService.java:50-65` → `dto/RbacSessionDTO.java:18`（`storeIds`） | 返回当前用户的 `storeIds`（`dataScope=ALL` 时为 `[-1]`） | ⚠️ **只能给 ID，不能给门店名**；需前端再拼名称 |
| `GET /store/list` | `controller/StoreController.java:29-32` → `storeService.list()` | **无任何范围过滤**：不按 `ctx.storeIds`、**也不按 `tenant_id`**（`config/MybatisPlusConfig.java:22-27` 只注册了分页插件，**未启用 `TenantLineInnerInterceptor`**） | ❌ **不可直接用**（返回全表门店，越权面大）；受权限码 `store:list` 约束 |
| `GET /wms/cross-store/store/list` | `service/CrossStoreInventoryController.java:125-129` → `service/impl/CrossStoreInventoryServiceImpl.java:710-717` | 按 `tenant_id` 过滤的本租户全部门店 | ❌ **不按 `ctx.storeIds` 过滤**（是「跨店调拨」用途的下拉） |

### 4.2 前端已有的「门店 ID 来源」

| 位置 | 内容 |
|---|---|
| `frontend/meiyetec-web/src/stores/permission.ts:80` | `storeIds: loginData.storeIds \|\| []`（登录时灌入） |
| `frontend/meiyetec-web/src/stores/permission.ts:97-110` | `refresh()` → `rbacApi.permissions()` → `GET /auth/me/permissions` 刷新 session（含 `storeIds`） |
| `frontend/meiyetec-web/src/api/rbac.ts:21` | `storeIds: number[]`（类型已存在） |

→ **门店 ID 集合前端已有了**（`usePermissionStore().session.storeIds`），**门店名称没有按可访问范围的来源**。

### 4.3 因此 I194-0 的任务被明确为

| 项 | 内容 |
|---|---|
| **I194-0-A（后端）** | 新增「当前用户可访问门店」端点（建议 `GET /store/accessible`）：按 `ctx.storeIds` ∩ 本租户返回 `[{ id, storeName, storeCode }]`；`dataScope=ALL` 返回本租户全部门店。**不得复用 `GET /store/list`**（它无范围过滤）。 |
| **I194-0-B（前端）** | `records.vue` 的 `storeList`（及 `employeeList`）改为**从该端点加载**；`attendance/index.vue`、`leave.vue` 若需「当前门店」状态，从同一来源初始化 |

> **I194-0 执行说明（2026-11-23 用户决策登记）**：分页响应含 `tenantId` / `deleted` / `createTime` / `updateTime` 四个额外字段，前端不消费，属实体完整视图。

> **I194-0 验收说明（2026-11-23 运行期实测）**：I194-0 的后端/前端改动均已生效（admin 账号验证通过：表格有数据、门店下拉有值、员工下拉 6 项）；但 manager01（店长）打开 `/attendance/records` 仍被前端路由拦截，提示缺 `employee:attendance:list` —— 这是 **I185** 登记的前后端权限码漂移，需 I185 修复后才能让店长页面可用。I194-0 的「页面可用」仅对拥有前端码的账号成立。**验收结论：I194-0 本身 VERIFIED（admin 路径），店长路径 UNVERIFIED（依赖 I185）**。

---

## 5. 修正方案表

| 方案 | 做法 | 适用 | 优点 | 代价 |
|---|---|---|---|---|
| **A** | **不传 `storeId`**，由后端按 `ctx.storeIds` 注入 | #1（读） | 与 I186 第 1 批后端口径完全一致；多店账号自动覆盖集合内全部门店 | 仅适用于**后端已支持集合语义**的端点 |
| **B** | 后端**从 `employeeId` 反查门店**落库 | #2 #3（打卡 / 请假） | 前端彻底不传门店，写入门店永远正确 | 依赖员工→门店映射可信；**与 I193（`pos_employee.tenant_id` 96% 为 NULL）、I192（`sys_user` 无 `employee_id`）强相关** |
| **C** | 加门店下拉选择器，写入请求 | #5 #6（工资 / 结算生成） | 语义最清晰，与后端「单店必填/多店 400」完全对齐 | 依赖 I194-0 的门店列表来源；交互改动较大 |
| **D** | 后端把 `storeId` 改可选并按集合聚合 | #4（pending-count） | 多店账号计数正确 | 需同步改前端 API 签名 `pendingCount(storeId: number)` → 可选 |

### 5.1 修正后的落地拆分（与用户 2026-11-17 决策一致）

| 批次 | 范围 | 前置依赖 | 备注 |
|---|---|---|---|
| **I194-0** | 门店列表来源（后端可访问门店端点）+ `records.vue` 的 `storeList`（及 `employeeList`）填充 | 无 | 是 I194-1 / I194-2 的地基（选店交互要有数据源） |
| **I194-1** | 考勤 3 处（#1 #2 #3）+ #4 `pendingCount` 集合语义 + 后端 employee→门店反查 + `pendingCount` 修集合语义 | I194-0（若需门店下拉）；**I193 / I192 的员工归属口径** | 写侧反查依赖员工表可信度 |
| **I194-2** | 提成 4 处（#5 #6 #7 #8） | **#7 #8 需 I187 解封**；#5 #6 需 I194-0 的选店数据源 | 与用户决策「rule-list/simulation 等 I187 解封后再动」一致 |
| **另立项** | `views/pos/checkout.vue:638` | — | **不纳入 I194**，另行评估下单金额归属门店的影响 |

### 5.2 I194-1 已知限制（2026-11-23 用户决策登记）

> **决策（2026-11-23）**：`records.vue` 的 `employeeList` **本期按租户展示，不补 `ctx.storeIds` 过滤** —— 作为**已知限制**登记，随 I194-1 一并处理。

| 项 | 内容 |
|---|---|
| **现象** | `records.vue` 的「员工」下拉来自 `GET /employee/list`（`EmployeeController.java:48-66`）→ 该端点按**租户**返回本租户全部员工，**不按 `ctx.storeIds` 过滤**（多店账号会看到其他门店的员工） |
| **影响面** | **仅影响下拉选项展示**，不影响数据权限 —— 记录列表本身由 `/attendance/page` 的门店范围条件过滤（`AttendanceRecordServiceImpl.pageRecords` → `resolveEffectiveStoreScope`） |
| **为何本期不补** | ① 与 `storeList` 走 `/store/accessible`（有专属端点）不同，员工侧**当前没有**「按数据范围过滤的员工列表」端点；② 新增该端点需要定义「员工可见范围」与 `ctx.storeIds` 的映射关系，与 **I192**（用户↔员工映射未定义）、**I193**（`pos_employee.tenant_id` 96% 为 NULL）**强相关** —— 在这两个口径未定前，贸然按 `store_id` 过滤会引入新的口径漂移 |
| **归属批次** | **I194-1**（与「后端 employee→门店反查」同批处理，届时员工口径一并厘清） |
| **状态** | **已知限制**，非缺陷定级；**不阻塞 I194-0 验收**（I194-0 的验收只涉及记录列表与门店下拉） |

---

## 6. 同族发现（**不在本条范围**，另行评估）

全仓另有 **11 处** `storeId: 1` 硬编码 / **8 个文件**，均**不在**考勤 / 提成模块（**与 §1「其余 11 处 / 8 文件」为同一口径、同一集合，此处不重复计数**）：

| 文件 | 行 | 用途 |
|---|---|---|
| `views/ai-scheduler/index.vue` | 61 / 62 | AI 排班表单默认值 |
| `views/crm/care-review.vue` | 559 / 574 | 评价表单默认值 |
| `views/google-maps/index.vue` | 140 | 地理编码表单默认值 |
| `views/pos/checkout.vue` | 638 | **POS 收银下单 —— 标注「另立项评估」，不纳入 I194** |
| `views/report/caliber.vue` | 100 | 口径查询参数 |
| `views/resource/resource-schedule.vue` | 470 | 资源排班重算 |
| `views/technician/evaluation.vue` | 339 / 367 | 技师等级评定 |
| `views/setting/employee.vue` | 226 | 员工表单默认值 |

> ⚠️ `pos/checkout.vue:638` 影响面最大（下单金额归属门店）→ 按用户决策**单独登记、另行评估**。

> 📌 **follow-up（本轮未纳入）**：`|| 1` 兜底写法 4 处 / 2 文件（`CheckCreateWizard.vue:173/247/283`、`setting/employee.vue:258`）不是 `storeId: 1` 字面量，未被本轮扫描口径覆盖，但语义同源（缺省回退门店 1）。需在 I194-1/-2 落地时一并评估，或另立项。

---

## 7. 边界声明

- 本次**仅回写调查结论**：**未修改任何前端代码、未修改任何后端代码、未写库、未执行 DDL、未新增入库脚本**。
- 所有行号、端点、方法签名均来自**源码核对**；后端行为判定引用 I186 第 1 批**已实测**的运行结果（`audit/evidence/F173-i186-batch1-runtime-verification/raw/01-runtime-verification.txt`）。
- ⚠️ **该运行期结果依赖临时授权，需重跑**：F173 主验证的 PASS 落在 2026-11-16 23:34:53 ~ 2026-11-17 23:36:34 的临时授权窗口内，撤销后同账号同端点复检 **11 处 200 -> 403 翻转** → 上条「已实测」在**基线授权状态**下**不可复现**，**不能单独作为 I186 验收证据**（见 I198 §A / F173 `raw/03`）。本条的**静态结论**（前端 8 处硬编码 `storeId=1`）不受影响。
- ⚠️ **运行期部分验证**：本条的 records.vue 前端改动已由 I194-0（commit `0b3e281`）实施；但 I194 主表的 8 处硬编码修复（考勤 index/leave、提成 salary/settlement/rule/simulation）仍待 I194-1/-2 批次执行 → 整体标 **PARTIAL**，records.vue 单页已验证（admin 路径，manager01 依赖 I185）。
- ⚠️ `GET /store/list` 的**跨租户返回**属结构性事实（无 `tenant_id` 条件、无 `TenantLineInnerInterceptor`），但本机库 `pos_store` 7 行**全部 `tenant_id=1`** 且仅 1 行未逻辑删除 → **跨租户泄露后果 UNVERIFIED**（本机无样本），**不并入本条**，建议单独立项。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。

---

## 8. 核查记录与流程偏差登记（2026-11-23）

> 本节独立于 §4「前置依赖确认」—— 以下内容为**引用核实**与**流程留痕**，不属于前置依赖，故单独成节。

### 8.1 R7 引用核实（`audit/reports/i183-i184-perm-binding-plan.md` 第 26 行）

`3ed2925` 的父 commit（`1b617e2`）第 26 行是 `§4 R7`；`3ed2925` 第 26 行是 `§3 R7`；diff 的 `4` → `3` 就是从父到子。R7 位于 `## 3. 风险与残留` 下，`§4 R7` 是错的。

> 补充事实：两行长度同为 177，字节级比对**唯一差异在第 172 个字符**（`U+0034` → `U+0033`），其余完全一致 → 该改动是**单字符引用修正**，方向正确，非冗余。

### 8.2 流程偏差登记：本会话前段编译部署未报告

本会话前段 Cursor 曾编译部署 I194-0 代码到容器，但未报告；导致后续多轮判断基于「容器跑的是 11-17 旧 jar」的错误前提。轮次编号 **UNVERIFIED**，事实由**本地 jar 与容器 jar 字节同源**（同为 140060925 字节、mtime 同为 `2026-11-23 17:23:51`）+ **时间线连续**（代码写入 17:16–17:20 → 编译 17:21 → 打包 17:23:51 → 镜像 17:25:05 → 容器启动 17:26:11）确定。**后续编译部署动作须即时报告。**
