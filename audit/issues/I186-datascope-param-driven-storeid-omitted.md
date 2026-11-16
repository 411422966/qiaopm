# I186 — 考勤/提成模块数据范围失控

> **副标题**：门店范围「参数驱动」+ 员工维度零约束（含补充发现 N1–N7、N9–N11；**N8 已拆出为 I191**；覆盖 49 个 endpoint）
>
> **状态**：**Open**（本批**仅登记**，**未修改任何业务代码、未写库**）
> **严重度**：**P0**（门店级越权读取：`dataScope=STORE` 的 MANAGER / BOSS 不带 `storeId` 请求时，返回**租户内全部门店**的数据；带 `storeId` 才被拦 → 授权行为完全取决于调用方是否主动传参）
> **覆盖范围**（2026-11-16 第二轮扩展 + 第三轮修订）：**49 个直接受影响的 endpoint**（考勤 20 + 提成 29），由 `raw/01` 的 59 行矩阵按「是否存在门店/员工维度缺口」收敛而来；排除 8 个门控端点（归 I187）与 2 个缺口不成立的端点（`A13 POST /attendance/config` body 的 `storeId` 已被拦截器覆盖、`A21 GET /attendance/leave/pending-count` 的 `storeId` 必填且已被拦截器覆盖）；⚠️ 原先排除的 `C9 POST /commission/calculate/order/{orderId}` **已重新纳入**（排除理由不成立，见 §X.4 N9）→ 覆盖数由 48 修正为 **49**
> **发现日期**：2026-11-16（batch2-C 权限对齐只读调查）
> **登记人**：Cursor
> **绑定基线**：HEAD `56d59a2`
> **验证等级**：**VERIFIED（运行期 L5a，决定性对比）** —— 同一账号、同一接口，仅切换「带 / 不带 `storeId`」即得 200 / 403 的成对证据
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`
> **证据**：`audit/evidence/F171-attendance-commission-perm-alignment/`（`raw/06b-runtime-datascope-probe-3.txt` A/B/D 段、`raw/05b` A 段、`raw/01-static-endpoint-matrix.md` §0）
> **关联**：I117（`DataScopeInterceptor` 覆盖路径不全）、I183 / I184（补权限绑定前，本条**被死码掩盖**）、I187（门控控制器无鉴权）、I188（RBAC 种子漂移）、I189（请假撤回 + `employeeId` 伪造）、I190（405 特判与 `handleAny` 收窄拆出）、**I191（原 §X.4 N8「请假审批后考勤记录从未落库」已拆出独立立项）**、I088 / I095（catch 吞异常同族）
> **用户决策（2026-11-16，第一轮）**：① N1–N7 **全部并入本条**，不单独立项，标题改为「考勤/提成模块数据范围失控」；② 缺省语义取**集合**（`ctx.storeIds` → `IN (...)`），与 `RbacContextLoader` 的 `data_scope_value` 多店配置语义一致；③ 员工映射分两阶段 —— 阶段 1 本轮做 **M3**（`pos_employee.store_id ∈ ctx.storeIds`，堵跨店），阶段 2 后续做 **M1**（`sys_user.employee_id` + 数据迁移，堵同店他人）；④ 405 分两层 —— 405 特判随 **I189 本批**落地，`handleAny` 收窄（415/406/404）拆为 **I190** 单独批次；⑤ 修复顺序 **I186 → I189 → I183/I184 → I185**（绑定必须等范围控制上线）
> **第三轮追加（2026-11-16）**：静态核验新发现 **N8–N11 / S5–S6**（见 §X.4）→ 覆盖范围 **48 → 49**（`C9 POST /commission/calculate/order/{orderId}` 的排除理由不成立）；M3 判定口径补充 **`tenant_id` 双条件**；N10 需**新增 DDL 迁移脚本**（三表补 `tenant_id` + 唯一键扩列）。**未改任何业务代码、未执行 SQL、未写库**。
> **第四轮拆分（2026-11-16，用户决策）**：**N8 单独立项为 I191**（`I191-leave-approval-attendance-not-persisted.md`）—— N8 属「功能缺失（写入被静默丢弃）」，与本条「数据范围失控」是**不同缺陷类型**，且修复需先定请假语义（表内无请假标记列）、影响工资口径，需独立业务讨论。**N9 / N10 / N11 保留在本条**（分别是范围缺陷本身、范围修复的 DDL 前置、M1 的前置事实）。
> **Q1–Q4 拍板（2026-11-16）**：**Q1** 移除导出接口白名单（拦截器只在 `preHandle` 工作、不参与响应写出 → 流式 xlsx 不受影响，见 §3 步骤 4）；**Q2** M1 开始规划，但**先跑只读映射键查询**核实 `phone` 交集 → **结论：交集 0 行，`phone` 不可行**，M1 须改走「新增 `sys_user.employee_id` + 人工回填」（见 §X.4 N11）；**Q3** I189 表述改为「当前 cancel 为空转（markLeaveDays 未落库，见 N8/I191）」；**Q4** register 的 I186 行同步为 **49 个 endpoint + N8–N11**（N8 标注已拆出 I191）。

---

## 0. 一句话描述

`DataScopeInterceptor` 只在**请求显式携带** `storeId` / `tenantId` 时才校验范围；
而 `CommissionController` 各查询在 `storeId == null` 时**根本不拼 `store_id` 条件**（`if (storeId != null)` 无 else 分支）→
`dataScope=STORE` 的店长/老板**只要不传 `storeId`，就能拿到租户内全部门店**的提成记录、工资报表、结算单、提成规则；
`employeeId` 则**完全不受任何范围约束**（不校验是否本人、是否本门店）。

---

## 1. 事实核实

### 1.1 拦截器判定逻辑（静态）

`backend/src/main/java/com/meiyetec/security/DataScopeInterceptor.java`：

| 行号 | 内容 |
|---|---|
| `:33` | `WHITELIST` 集合（含 `/commission/salary/export`、`/commission/settlement/export`） |
| `:56` | `if (WHITELIST.contains(path)) return true;` → **导出接口完全跳过范围校验** |
| `:61-66` | 租户校验：`extractTenantId(request)` 取到值**且**与 ctx 不等且非 ALL → 403 |
| `:68` | `Long sid = extractStoreId(request);` |
| `:78-95` | `extractTenantId` 只从**请求参数**（query / form / body）取值 |
| `:97-132` | `extractStoreId` 同上，**只从请求参数取值** |
| `:134-139` | `canAccess(ctx, storeId)`：`return storeId == null \|\| ids.contains(storeId);` → **`storeId == null` 直接判过** |

> ⚠️ **两个漏洞叠加**：
> ① 拦截器侧：`storeId` 缺省 = 不校验（`canAccess` 显式放行 `null`）；
> ② 业务侧：`storeId` 缺省 = **不拼门店条件**（`CommissionController.java:226` 等 8 处 `if (storeId != null)`）。
> → 两者一致地退化为**租户级全量**。

### 1.2 业务侧「无 else 分支」逐点清点

`backend/src/main/java/com/meiyetec/controller/CommissionController.java`：

| 行号 | 方法 | 代码 | 缺省后果 |
|---|---|---|---|
| `:60` | `rulePage` | `if (storeId != null) query.eq(CommissionRule::getStoreId, storeId);` | 全租户规则 |
| `:105` | `ruleList` | 同上 | 全租户规则 |
| `:189` | `ruleStats` | `if (storeId != null) allQuery.eq(CommissionRule::getStoreId, storeId);` | 全租户统计 |
| `:194` | `ruleStats` | `if (storeId != null) activeQuery.eq(CommissionRule::getStoreId, storeId);` | 同上 |
| `:226` | `recordPage` | `if (storeId != null) { where.append("AND r.store_id=? "); args.add(storeId); }` | 全租户提成记录 |
| `:331` | `salaryPage` | `if (storeId != null) { where.append(" AND s.store_id=?"); args.add(storeId); }` | 全租户工资 |
| `:438` | `salaryExport` | `if (storeId != null) { where.append(" AND s.store_id=?"); args.add(storeId); }` | 全租户工资（且在白名单内，**拦截器也不校验**） |
| `:578` | `settlementPage` | `if (storeId != null) query.eq(CommissionSettlement::getStoreId, storeId);` | 全租户结算 |
| `:689` | `settlementExport` | `if (storeId != null) { where.append(" AND s.store_id=?"); args.add(storeId); }` | 全租户结算（白名单内） |

考勤侧（`AttendanceRecordServiceImpl`）：

| 行号 | 方法 | 代码 | 缺省后果 |
|---|---|---|---|
| `:79-80` | `getRecords` | `baseMapper.findByStoreAndDateRange(tenantId, storeId, ...)` | `storeId=null` → 无门店过滤 |
| `:84-85` | `queryRecords` | `baseMapper.queryRecords(tenantId, storeId, employeeId, ...)` | 同上 |
| `:89-90` | `getStatistics` | `baseMapper.findByStoreAndDateRange(tenantId, storeId, ...)` | 同上 |
| `:111-112` | `getMonthlyReport` | 仅按 `tenantId` + `employeeId` 过滤 | **无门店维度**，且 `employeeId` 由调用方指定 |

### 1.3 `employeeId` 无任何范围约束

| 接口 | `employeeId` 来源 | 是否校验「本人」 | 是否校验「本门店」 |
|---|---|---|---|
| `GET /attendance/today?employeeId=` | 查询参数 | ❌ | ❌ |
| `GET /attendance/monthly-report?employeeId=` | 查询参数 | ❌ | ❌ |
| `GET /attendance/records?employeeId=` | 查询参数 | ❌ | ❌ |
| `GET /attendance/leave/my?employeeId=` | 查询参数 | ❌ | ❌ |
| `GET /commission/records/page?employeeId=` | 查询参数 | ❌ | ❌ |
| `GET /commission/salary/page?employeeId=` | 查询参数 | ❌ | ❌ |
| `GET /commission/simulation/recent/{employeeId}` | 路径变量 | ❌ | ❌（且当前 404） |

`DataScopeInterceptor` 的采集清单里**没有 `employeeId`**（`extractStoreId` / `extractTenantId` 只覆盖 `storeId` / `tenantId`）→ 该维度**在框架层就没有拦截点**。

### 1.4 运行期决定性对比（`raw/06b` A/B 段，MANAGER `dataScope=STORE`、`storeIds={1}`）

| 请求 | HTTP | body |
|---|---|---|
| `GET /commission/records/page`（**不带** `storeId`） | **200** | 返回提成记录（含 `ruleName":"服务项目通用提成10%"`、`id:717`、`rate:0.1000` 等） |
| `GET /commission/records/page?storeId=1` | **200** | 同上（自己门店） |
| `GET /commission/records/page?storeId=5` | **403** | `{"code":403,"message":"无权访问该门店数据"}` |
| `GET /commission/salary/page` | **200** | `{"records":[],"total":0,...}`（本库 `commission_salary` 0 行） |
| `GET /commission/salary/page?storeId=5` | **403** | 「无权访问该门店数据」 |
| `GET /commission/settlement/page` | **200** | `{"records":[],"total":0,...}` |
| `GET /commission/settlement/page?storeId=5` | **403** | 「无权访问该门店数据」 |
| `GET /commission/rules/page` | **200** | 返回规则 |
| `GET /commission/rules/page?storeId=5` | **403** | 「无权访问该门店数据」 |

BOSS 同组探测结果完全一致（`raw/06b` B 段）。

> ✅ **决定性**：同一账号、同一接口，**只改 `storeId` 参数的有无**，结果在 200 与 403 之间翻转 →
> 拦截逻辑确实存在，但**只在调用方主动传参时生效**；不传即绕过。

### 1.5 `employeeId` 实测

| 探测 | 账号 | 结果 |
|---|---|---|
| `GET /commission/records/page?employeeId=99999999` | `manager01` | **200**（`records:[]`）→ 参数被接受，未被拒绝 |
| `GET /commission/records/page?employeeId=99999999` | `admin` | **200**（`records:[]`） |
| `GET /attendance/records?employeeId=99999999` | `admin` | **200**（`data:[]`） |
| `GET /attendance/leave/my?employeeId=99999999` | `admin` | **200**（`data:[]`） |
| `GET /attendance/records?employeeId=99999999` | `manager01` | **403** —— 但这是**权限码死**（I183），**不是范围拦截** |

> `99999999` 为**确认不存在的兜底 ID**（`attendance_leave_request` / `attendance_record` 均 0 行），返回空数组即证明**参数未被拒绝**。

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | **跨门店读取提成/工资/结算** | 店长省略 `storeId` | 看到租户内**其他门店**的提成记录、工资表、结算单 → 商业敏感数据泄露 |
| R2 | **越权仅靠调用方自觉** | 任何前端页面忘记传 `storeId` | 后端**不兜底**，静默返回全量 → 一次前端疏忽就是一次越权 |
| R3 | **导出接口彻底无范围校验** | `GET /commission/salary/export` / `settlement/export` | 两者都在 `DataScopeInterceptor.WHITELIST`（`:33`、`:56`）→ 即使显式传 `storeId=5` 也不做范围判定（但因权限码死，当前仅超管可达，见 I184） |
| R4 | **查他人工资/提成** | 任意带 `employeeId` 的接口 | 不校验「本人」也不校验「本门店」→ 可查同租户任意员工的考勤明细与提成 |
| R5 | **月度报表无门店维度** | `GET /attendance/monthly-report?employeeId=` | `AttendanceRecordServiceImpl:111-112` 只按 `tenantId` + `employeeId` 过滤 → 跨店员工的月度报表可被查 |
| R6 | **当前被死码掩盖** | 修复 I183 / I184 后 | 本条**立刻从「潜伏」变成「可利用」** → 修复顺序必须与 I183/I184 **同步排期**，不可先补权限码、后补范围校验 |

---

## 3. 建议方案（**仅建议，本批不实施**；已按用户决策 2026-11-16 修订）

> 📌 **决策落点**：① 缺省语义取**集合**（`ctx.storeIds` → `IN (...)`）；② 员工归属校验分两阶段（本轮 M3，后续 M1）；③ 405 特判归 I189 本批，`handleAny` 收窄归 I190 单独批次；④ I183/I184 绑定必须排在本条**之后**。
>
> ⚠️ **第 1 批后端修复后，前端硬编码会绕过修复，I194 必须紧随其后**（2026-11-16 登记 I194 时确认）：前端 `views/commission/salary.vue:136`、`views/commission/settlement.vue:206`、`views/attendance/index.vue:224` / `:246`、`views/attendance/leave.vue:317`、`views/commission/rule-list.vue:119`、`views/commission/simulation.vue:224` 共 **6 文件 7 处**把 `storeId` 写死为 `1` 并**显式**传给后端 → 步骤 1 的「缺省按集合」路径在真实链路上**不可达**（要么退化成 `eq(1)`，要么被拦截器 403）。**后端第 1 批上线与 I194 前端改造必须同批次或紧随发布**，否则修复效果被绕过。

| # | 步骤 | 内容 | 备注 |
|---|---|---|---|
| 1 | **业务侧补 else 分支**（框架层之外） | 所有列表/统计/导出查询：调用方未传 `storeId` 且 `dataScope != ALL` 时，由服务端**按 `ctx.storeIds` 强制注入**门店条件 | **集合语义**（决策 2）：`ctx.storeIds` 返回 `Set` → 过滤用 `IN (1,5)` 而非单值，与 `RbacContextLoader` 的 `data_scope_value` 多店配置语义一致；建议集中在 Service 层统一封装，避免每个 Controller 各写一遍 |
| 2 | **框架层收敛** | `DataScopeInterceptor` 从 ctx 取 `storeIds` 后，向 request 注入**默认门店**或直接拒绝对「STORE 范围 + 未指定门店」的请求 | 需评估对其它模块的兼容影响（本 Issue 范围只覆盖考勤/提成） |
| 3 | **`employeeId` 增加本人/本门店校验** | 对「员工本人视角」的接口（`/attendance/today`、`/attendance/leave/my`、`/attendance/monthly-report`）在 `dataScope=STORE` 时校验 `employeeId` 是否属于 ctx.storeIds 内员工；或改为**从 token 取当前员工**，不接受调用方传入 | **M3（本批，阶段 1）**：`pos_employee.store_id ∈ ctx.storeIds` → 堵死跨店；**M1（阶段 2，后续）**：`sys_user.employee_id` 映射 + 数据迁移 → 堵死同店他人；M3 需与 I183/I184 同批 |
| 4 | **导出接口出白名单** | `/commission/salary/export`、`/commission/settlement/export` 从 `WHITELIST` 移除，改为**参与范围校验** | ✅ **用户 2026-11-16 已拍板（Q1）：移除白名单**。依据：拦截器只在 `preHandle` 阶段工作、**不参与响应写出** → 移除只影响「是否抛 403」，**流式 xlsx 字节流写出不受影响**（写出发生在 `salaryExport` 内的 `wb.write(response.getOutputStream())`）。⚠️ 但白名单内 `storeId == null` 仍会放行 → **必须与步骤 1（集合过滤）合并**才能真正闭合 |
| 5 | **补跨门店测试样本** | 在隔离测试租户内造「他店有提成记录」的样本，才能把「实数据泄露」从结构性推断升级为 VERIFIED | 见 §5 观测限制 |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | `manager01` `GET /commission/records/page`（**不带** `storeId`） | 仅返回 `store_id ∈ ctx.storeIds` 的记录（**集合**语义；多店账号应看到 `IN (1,5)` 对应的全部行） |
| 2 | `manager01` `GET /commission/records/page?storeId=5` | **403**「无权访问该门店数据」（保持现状） |
| 3 | `manager01` `GET /commission/salary/page`（不带 `storeId`） | 仅本门店 |
| 4 | `manager01` `GET /commission/settlement/page`（不带 `storeId`） | 仅本门店 |
| 5 | `manager01` `GET /attendance/records`（不带 `storeId`） | 仅本门店 |
| 6 | `manager01` `GET /attendance/records?employeeId=<他店员工>` | **403** 或空结果（**M3 口径**：`pos_employee.store_id ∈ ctx.storeIds`；**同店他人**在 M1 落地前仍可查 → 该项为 **PARTIAL**） |
| 7 | `manager01` `GET /commission/salary/export?salaryMonth=2026-08` | 仅本门店数据；**Q1 已拍板：导出接口移出白名单 → 参与范围校验**，仍缺省 `storeId` 时按 `ctx.storeIds` 集合过滤 |
| 8 | `admin`（`dataScope=ALL`）同组请求 | 仍返回全量（ALL 语义不受影响） |
| 9 | N2 复测：`manager01` `GET /attendance/config/list`（不带 `storeId`） | **仅本租户**的行（`OR store_id IS NULL` 分支必须带 `tenant_id` 约束），**不得出现其它租户的全局配置** |
| 10 | N3 复测：`manager01` `GET /attendance/statistics`（不带 `storeId`） | 与 `GET /attendance/records` **数据口径一致**（不得「列表非空、统计为 0」） |
| 11 | N4/N5/N6 复测：以他店 / 他租户 id 访问详情、写、批量接口 | 全部 **403**（当前为 200 或跨租户命中） |
| 12 | N7 复测：`POST /attendance/check-in` body 伪造他店 `employeeId` | **403**，且 `attendance_record` **不产生新行**（写前校验） |
| 13 | N8 复测：**已转 I191**（`attendance_record` 新增 3 行、`store_id` 正确、请假标记语义正确）→ 见 `I191-leave-approval-attendance-not-persisted.md` §4 | 本条**不再承载**该项验证（随 N8 拆出） |
| 14 | N9 复测：`manager01` `POST /commission/calculate/order/{他租户订单id}` | **403**，且 `commission_record` **不新增行**（跨租户写被堵死） |
| 15 | N10 复测：新环境按仓库脚本初始化后 `SHOW COLUMNS FROM attendance_record` | 存在 `tenant_id`；`uk_employee_date` 含 `tenant_id`；三表均可用 |
| 16 | S6 复测：构造跨租户**同 `employee_id`** 的员工，M3 判定 | **403**（`tenant_id` 与 `store_id` 双条件均生效，非仅 `store_id`） |

---

## X. 补充发现（2026-11-16 第二轮盘点：N1–N7；第三轮追加 N8–N11 见 §X.4）

> **来源**：batch2-C 第 1 批只读调查（逐文件读 Controller / Service / Mapper / Interceptor / RbacContextLoader）
> **性质**：**静态 VERIFIED**（代码路径确定，行号可核）；**运行期后果未复现** —— 受影响的写接口与读接口其权限码当前均为死码（I183 / I184），且 `attendance_record` / `attendance_leave_request` / `commission_salary` / `commission_settlement` 四表均 0 行 → **实数据后果一律标 UNVERIFIED**
> **处置**：按用户决策 2026-11-16「N1–N7 全部并入 I186，不单独立项」
> ⚠️ 与前文 §1 的关系：§1 聚焦「读侧、参数驱动」；§X 补齐 **请假侧遗漏（N1）**、**跨租户分支（N2）**、**静默空集（N3）**、**详情/写/批量三条 IDOR 通道（N4/N5/N6）**、**写侧 employeeId 伪造（N7）**——前四者比 §1 的原有问题**更严重**

### X.1 逐条清单

#### N1 — 请假列表缺省 `storeId` → 租户级全量（§1.2 未覆盖请假侧）

| 项 | 内容 |
|---|---|
| 端点 | `GET /attendance/leave/list`（`attendance:leave:list`） |
| 位置 | `LeaveRequestController.java:62-71` → `LeaveRequestServiceImpl.java:104-106` → `LeaveRequestMapper.java` 的 `queryRequests`：`AND (#{storeId} IS NULL OR lr.store_id = #{storeId})` |
| 缺省后果 | 返回**租户内全部门店**的请假单（含 `reason` 事由、`approverId`/`approverName` 审批人、`leaveDays` 天数） |
| 备注 | §1.2 的「无 else 分支」清单只列了 `CommissionController` 与 `AttendanceRecordServiceImpl`，**请假侧 3 个端点被漏掉** |

#### N2 — 考勤配置列表的 OR 优先级缺陷 → **跨租户**行泄漏（比 N1 更严重）

| 项 | 内容 |
|---|---|
| 端点 | `GET /attendance/config/list`（`attendance:config:list`） |
| 位置 | `AttendanceConfigController.java:34-43`：`wrapper.eq(AttendanceConfig::getTenantId, t); if (storeId != null) { wrapper.eq(AttendanceConfig::getStoreId, storeId).or().isNull(AttendanceConfig::getStoreId); }` |
| 生成的 SQL 语义 | `(tenant_id = ? AND store_id = ?) OR (store_id IS NULL)` —— **`OR` 的第二个分支没有 `tenant_id` 约束** |
| 后果 | 其它租户的「全局考勤配置」（`store_id IS NULL`）行会被返回本租户 → **跨租户**泄漏（与 N1 的跨门店不同层级） |
| 连带 | 同文件 `GET /attendance/config/active` → `AttendanceConfigMapper.findActiveConfig`：`AND (store_id = #{storeId} OR store_id IS NULL)`；`storeId` 缺省时 `store_id = NULL` 恒 FALSE → **只命中全局行，本店专属配置永远取不到**（缺省语义与 A 方案「按 ctx.storeIds 取本店」直接冲突） |

#### N3 — 考勤统计缺省 `storeId` → 静默空集，且与同模块列表行为相反

| 项 | 内容 |
|---|---|
| 端点 | `GET /attendance/statistics`（`attendance:stat`） |
| 位置 | `AttendanceRecordServiceImpl.java:89-90` → `AttendanceRecordMapper.java:21-25` 的 `findByStoreAndDateRange`：`AND store_id = #{storeId}` |
| 缺省后果 | `storeId == null` → SQL 变成 `store_id = NULL` → **恒 FALSE** → 统计全部为 0（不是越权，是**语义错误**） |
| 矛盾点 | 同一模块 `GET /attendance/records` 缺省 → `queryRecords` 的 `#{storeId} IS NULL` 分支 → 返回**租户全量** → **列表非空、统计为 0**，同一页面前后端数据自相矛盾 |
| 归类 | 「缺省即退化」的三种形态：N1 退化为**全量**、N3 退化为**空集**、N2 退化为**跨租户** → 修复必须**逐端点**处理，不能只加一处默认 |

#### N4 — 详情类端点只校验 tenant、不校验门店 → 跨店 IDOR 读取

| 端点 | 位置 | 校验现状 |
|---|---|---|
| `GET /attendance/{id}` | `AttendanceController.java:135-139` | `selectById` → 只比 `tenantId` |
| `GET /attendance/config/{id}` | `AttendanceConfigController.java:45-51` | 同上 |
| `GET /attendance/leave/{id}` | `LeaveRequestController.java:88-94` | 同上 |
| `GET /commission/rules/{id}` | `CommissionController.java:109-118` | 同上 |
| `GET /commission/settlement/{id}` | `CommissionController.java:585-598` | 同上（返回结算单 + 明细记录） |

- **形态**：`selectById(id)` → 命中即返回 → **绕过列表层的门店过滤**，直接按 ID 读到他店记录（提成规则含费率与计算方式、结算单含金额与员工明细、考勤记录含打卡时间与地点）。
- ⚠️ 与 I184 记录的现象互为镜像：**列表端点有门店过滤（可被缺省绕过），详情端点反而比列表端点更宽松**。

#### N5 — 写接口只校验 tenant、不校验门店 → 跨店修改 / 删除 / 确认 / 发放

| 端点 | 位置 | 写操作 |
|---|---|---|
| `PUT /commission/rules/{id}` | `CommissionController.java:137-152` | 改费率、计算方式、金额 |
| `PUT /commission/rules/{id}/status` | `CommissionController.java:154-165` | 停用他店规则 |
| `DELETE /commission/rules/{id}` | `CommissionController.java:167-180` | 删除他店规则 |
| `PUT /commission/salary/{id}/adjustment` | `CommissionController.java:349-364` | 改他店工资调整额与应发额 |
| `POST /commission/salary/{id}/confirm` | `CommissionController.java:366-382` | 确认他店工资 + 连带 `UPDATE commission_record` 状态 |
| `POST /commission/salary/{id}/pay` | `CommissionController.java:384-397` | 发放他店工资 |
| `PUT /commission/settlement/{id}/confirm` | `CommissionController.java:600-612` | 确认他店结算单 |
| `PUT /commission/settlement/{id}/pay` | `CommissionController.java:614-626` | 发放他店结算单 |
| `PUT /attendance/config/{id}` | `AttendanceConfigController.java:62-71` | 改他店考勤配置 |
| `DELETE /attendance/config/{id}` | `AttendanceConfigController.java:73-81` | 删他店考勤配置 |

- **根因（框架层）**：`DataScopeInterceptor.extractStoreId`（`:97-111`）只从 **header / query / form / body** 采集 `storeId`，**看不到路径参数** → **写路径（`{id}` 定位资源）完全没有范围防线**，只能靠前端列表过滤兜底。
- **现状**：这些码全部 0 绑定（I184）→ 修复绑定后**立即可利用**，与 §2 R6 同理 → 必须排在 I183/I184 **之前**。

#### N6 — 批量接口 `ids` 无任何范围校验 → 跨店 / 跨租户批量操作

| 端点 | 位置 | 校验现状 |
|---|---|---|
| `PUT /commission/salary/batch/confirm` | `CommissionController.java:476-501` | `salaryMapper.selectBatchIds(ids)` → 逐条按 `status` 更新，**不校验 `tenantId` / `storeId`** |
| `PUT /commission/salary/batch/pay` | `CommissionController.java:503-526` | 同上 |
| `PUT /commission/settlement/batch/confirm` | `CommissionController.java:628-651` | 同上 |
| `PUT /commission/settlement/batch/pay` | `CommissionController.java:653-676` | 同上 |

- **后果**：只要给出 id 列表，即可批量确认 / 发放**任意门店、任意租户**的工资单与结算单。
- ⚠️ **比单条接口更宽松**：单条接口（C14/C15/C16/C25/C26）至少还校验了 `tenantId` → 批量接口是**授权面更大**的入口。

#### N7 — 写侧 `employeeId` 可伪造：可替他人打卡 / 替他人提交请假（§1.3 只覆盖读侧）

| 端点 | 位置 | 写入后果 |
|---|---|---|
| `POST /attendance/check-in` | `AttendanceController.java:36-46` → `AttendanceRecordServiceImpl.java:33-51` | `attendance_record.employee_id` 写为**他人** |
| `POST /attendance/check-out` | `AttendanceController.java:48-57` → `AttendanceRecordServiceImpl.java:53-68` | 同上 |
| `POST /attendance/leave` | `LeaveRequestController.java:28-40` → `LeaveRequestServiceImpl.java:29-49` | `attendance_leave_request.employee_id` 写为**他人** |

- **后果比读侧严重**：
  ① 考勤记录落在他人名下 → 直接影响 `commission_record` 的提成基数、`attendance:stat` 统计与 `monthly-report`；
  ② 请假单挂在他人名下 → 影响审批链；审批通过后 `markLeaveDays`（`LeaveRequestServiceImpl.java:118-131`）写入的考勤记录同样落在他人名下；
  ③ 与 I189 的伪造 `employeeId` 合并后，形成「**替他人打卡 + 替他人请假 + 取消他人请假**」的完整伪造链路。
- **现状**：同样被死码掩盖（`attendance:check` / `attendance:leave:submit` 均 0 绑定）→ 与 I186 R6 同批触发。

### X.2 次要附注（不单列，纳入修复清单一并处理）

| # | 位置 | 事实 |
|---|---|---|
| S1 | `CommissionController.java:276` | `POST /commission/salary/generate` 的 `body.storeId` 缺省**静默硬编码默认 `1L`** → 与「缺省按 `ctx.storeIds`」语义冲突（多店用户需显式指定或按店循环生成） |
| S2 | `CommissionSettlementService.java:40-42` | 结算单 `selectOne(storeId, month)` **无 `tenantId` 条件** → 跨租户「同门店 id + 同月份」会命中他租户结算单并被复用 / 覆盖 |
| S3 | `CommissionController.java:277-280` | `generateSalary` 的员工查询 `eq(Employee::getStoreId, storeId)` **无 `tenantId` 条件** → 跨租户同店 id 的员工会被纳入计算 |
| S4 | `CommissionTierServiceImpl.java:29-45` / `CommissionConditionServiceImpl.java:20-30` / `CommissionSimulationServiceImpl.java:38-56,122-124` | 门控三件套的 `ruleId` / `employeeId` / `storeId` 均无归属校验 → 归 I187 随解封一并处理 |

### X.3 N1–N7 的修复归属（本 Issue 内）

| 编号 | 修法要点 | 批次 |
|---|---|---|
| N1 | 缺省 `storeId` → 按 `ctx.storeIds` 注入过滤（集合语义，`IN (...)`） | 第 1 批 |
| N2 | `AttendanceConfigController.list` 的 `or()` 改为**带 `tenant_id` 约束**的嵌套条件（或直接去掉 `or().isNull()` 分支）；`config/active` 缺省改为先按 `ctx.storeIds` 取本店配置，无则回落全局行 | 第 1 批 |
| N3 | `findByStoreAndDateRange` 的 `store_id = #{storeId}` 改为**缺省走集合过滤**，与 `queryRecords` 行为对齐 | 第 1 批 |
| N4 | 详情类端点补「资源所属门店 ∈ `ctx.storeIds`」校验 | 第 1 批 |
| N5 | 写接口补「资源所属门店 ∈ `ctx.storeIds`」校验（路径参数无法被拦截器采集 → 必须在 Service 层做） | 第 1 批 |
| N6 | 批量接口逐条校验 `tenantId` + 门店归属后再更新 | 第 1 批 |
| N7 | 写侧 `employeeId` 校验：**M3 口径** —— `pos_employee.tenant_id = ctx.tenantId` AND `pos_employee.store_id IN ctx.storeIds`（双条件，见 §X.4 S6）；M1（`sys_user.employee_id` 映射，阶段 2）后续再做 | 第 1 批（M3） |
| S1–S4 | S1/S2/S3 并入第 1 批；S4 归 I187 | 第 1 批 / I187 |

> ⚠️ **阶段划分（用户决策 2026-11-16）**：
> - **M3（本批，阶段 1）**：`employeeId` 必须满足 `pos_employee.store_id IN ctx.storeIds` → 堵死**跨店**越权；
> - **M1（后续，阶段 2）**：`sys_user` 增加 `employee_id` 列 + 数据迁移 → 堵死**同店他人**越权。
> M3 先行的前提是：M1 需要 DDL 与数据迁移，而 M3 已能把可利用面从「任意员工」收敛到「本店内员工」。

### X.4 追加发现（第二轮核验补充：N8–N11 / S5–S6；**N8 已拆出 I191**）

> **来源**：2026-11-16 第三轮静态核验（逐文件复读 `LeaveRequestServiceImpl` / `CommissionCalculationService` / `CommissionController` / `init_attendance.sql` / `SysUser` / `RbacContextLoader` / `AttendanceRecord` / `LeaveRequest`）
> **性质**：**静态 VERIFIED**（代码与 DDL 事实确定，行号可核）；**运行期后果 UNVERIFIED** —— 相关表 0 行、相关权限码为死码
> **处置**：**N8 拆出为 I191**（用户决策 2026-11-16）；**N9 / N10 / N11 / S5 / S6 随本 Issue 主体一并修复**（不单独立项）

#### N8 — 请假审批通过后，考勤记录**从未落库**（`markLeaveDays` 只构造、不保存） → **已拆出为 I191**

> ⚠️ **2026-11-16 用户决策：N8 单独拆出为 I191**（`audit/issues/I191-leave-approval-attendance-not-persisted.md`）。
> 理由：N8 属「**功能缺失**（写入被静默丢弃）」，与本条「**数据范围失控**」是不同缺陷类型；且修复前必须先定请假语义（`attendance_record` 表内无请假标记列），并需独立讨论工资计算口径 → 不能并入范围控制批次。
> 下文保留 N8 的**事实摘要**（供追溯）；**详细事实、影响面、修复前置与验证口径见 I191**，本条不再承载其修复责任。

| 项 | 内容 |
|---|---|
| 位置 | `LeaveRequestServiceImpl.java:119-133`（`markLeaveDays`），调用点 `:69`（`approve` 内 `if (approved)`） |
| 事实 1 | `record == null` 分支：`new AttendanceRecord()` + `setTenantId/setEmployeeId/setCheckDate/setIsAbsence(0)/setStoreId(1L)` → **方法体内没有任何 `save` / `insert`**，记录构造完即丢弃 |
| 事实 2 | `record != null` 分支（当日已有打卡记录）：**也不做任何更新**（不写 `is_absence`、不写请假标记） |
| 后果 | 请假审批通过对 `attendance_record` **零影响** → `attendance:stat` 统计、`/attendance/monthly-report` 月度报表、`commission_record` 的出勤相关基数**均不体现请假** → 「审批通过」只是改了个 `attendance_leave_request.status` |
| 连带缺陷 1 | `record.setStoreId(1L)` **硬编码门店 1**，不是请假单自己的 `storeId` → 即便补上 `save`，记录也会写到错误的门店（与 §X.2 S1 的 `setStoreId(1L)` 同族硬编码） |
| 连带缺陷 2 | `setIsAbsence(0)` —— 请假与旷工语义不同（请假不是旷工），`attendance_record` 也**无请假标记列** → 补 `save` 前需先定语义（新增 `leave_type` 列 / 或按 `is_absence=1` 约定） |
| 对 I189 的影响 | I189 §「`cancel` 未回滚 `approve` 写入的考勤请假天数」的前提**不成立** —— 天数**从未被写入**，因此「撤回不回滚」当前是**空转**而非「脏数据残留」→ I189 该条描述已按用户决策（2026-11-16）修正为「当前 cancel 为空转（markLeaveDays 未落库，见 N8/I191）」 |

#### N9 — 原「C9 排除理由不成立」：`POST /commission/calculate/order/{orderId}` 存在**跨租户读 + 跨租户写**

| 项 | 内容 |
|---|---|
| 位置 | `CommissionController.java:200-210` → `CommissionCalculationService.java:43-121` |
| 事实 1（跨租户读） | `orderMapper.selectById(orderId)`（`:44`）**无 `tenantId` 条件** → 可取他租户订单 |
| 事实 2（跨租户写） | `record.setTenantId(order.getTenantId() == null ? 1L : order.getTenantId())`（`:87`）+ `record.setStoreId(order.getStoreId())`（`:88`）→ 提成记录**写进该订单所属租户与门店** |
| 事实 3 | `itemMapper.selectList(...)`（`:52`，仅 `orderId` 条件）、`matchRule(order.getStoreId(), ...)`（`:70`）同样**无 `tenantId` 条件** → 跨租户订单明细参与计算、跨租户提成规则被套用 |
| 事实 4（为何现在不可利用） | 权限码 `commission:calculate` 运行期 **0 角色绑定**（死码，`raw/04b` V1 行 `commission:calculate API 0 NULL`；`raw/01:152` 标注「🔴 死码（写）」）→ 当前**仅超管**可达 → **潜伏**；绑定后立即可利用（同 §2 R6 逻辑） |
| 结论 | §0「覆盖范围」原写「C9 无门店/员工入参 → 排除」，该理由**不成立**（虽然无 `storeId` 入参，但 `orderId` **间接携带**门店与租户维度，且该端点**可写**）→ C9 纳入覆盖范围，48 → **49** |

#### N10 — 考勤三表 DDL 与实体漂移：`init_attendance.sql` 三张 CREATE TABLE **均无 `tenant_id`**

| 项 | 内容 |
|---|---|
| 位置 | `backend/src/main/resources/db/sql/init_attendance.sql`（`:13` `attendance_config` / `:39` `attendance_record` / `:73` `attendance_leave_request`） |
| 事实 1 | 三张表的 `CREATE TABLE` **都没有 `tenant_id` 列**，而实体 `AttendanceConfig` / `AttendanceRecord`（`AttendanceRecord.java:15`）/ `LeaveRequest`（`LeaveRequest.java:14`）**都有 `tenantId` 字段**（非 `exist=false`） |
| 事实 2 | `uk_employee_date (employee_id, check_date, deleted)` **不含 `tenant_id`** → 跨租户同 `employee_id` 同日期会撞唯一键（即使补了 `tenant_id` 列，唯一键也需同步扩列） |
| 事实 3 | `attendance_record` 的 `employee_name` / `position` / `store_name` 在实体侧是 `@TableField(exist = false)`（`AttendanceRecord.java:58-63`）→ **不是**列，无漂移；`LeaveRequest` 同理（`:41-48`）→ 此项排除，勿误判 |
| 运行期状态 | **推定**列已存在：本轮之前的运行期探测中，`GET /attendance/leave/list`、`GET /attendance/records` 均返回 **200**（空列表），而这两条查询的 WHERE 均含 `tenant_id` → 若列不存在应报 `Unknown column` 并被 `handleAny` 吞成 500。⚠️ 本轮**未执行 `SHOW COLUMNS`**（按指令不执行 SQL）→ 标 **UNVERIFIED（推定）** |
| 后果 | ① **新环境按仓库脚本初始化即缺列** → 考勤模块整体不可用；② 修复 N2 / N3 时若以「补 `tenant_id` 条件」为目标，必须**同时补迁移脚本**，否则新环境仍缺列；③ 唯一键不扩列 → M3 引入 `tenant_id` 判定后仍可能在跨租户同员工同日时撞键 |
| 修复归属 | 与 I118（缺 132 张 DDL）相邻，**但本表是「已存在表的列缺失」**，不是「表缺失」→ 需**新增 ALTER 迁移脚本**（列 + 唯一键扩列），**不得**改 `init_attendance.sql` 的 `DROP TABLE IF EXISTS` 语义 |

#### N11 — `sys_user` 无 `employee_id` 列：阶段 2（M1）的硬前置事实 + 候选映射键

| 项 | 内容 |
|---|---|
| 事实 1 | `entity/SysUser.java` 字段全集：`id / username / password / realName / phone / email / avatar / status / storeId / tenantId / createTime / updateTime / deleted` → **无 `employeeId`** → M1 需要 DDL（新增列 + 数据迁移） |
| 事实 2（候选映射键，**已只读核验 —— 不可行**） | `sys_user.phone` 与 `pos_employee.phone` 同时存在，原为 M1 的候选映射键；**2026-11-16 只读核验结论：交集为 0 行**（`raw/10-m1-mapping-key-phone.out.txt` §2：`matched_rows=0 / distinct_users=0 / distinct_employees=0`；§5 唯一性判定：5 个用户**全部命中 0 条**员工）→ **`phone` 不是可靠映射键**。两表 `phone` **各自内部唯一**（`sys_user` 5/5、`pos_employee` 150/150 无重复），但**值域完全不同域**：`sys_user` 为 `13800138000` / `13800000001~4`（5 个系统账号），`pos_employee` 为 `13900139001~6` / `139000xxxxx`（10 位 hex 形态的测试员工）→ **无任何交集，也无一一对应关系**。备用键核验同样为 0（`raw/11-m1-mapping-key-alternatives.out.txt`）：`real_name = employee_name` → 0 行；`username = employee_code` → 0 行；`real_name LIKE %employee_name%` → 0 行；按 `(tenant_id, phone)` 复合口径 → 仍 0 行。`sys_user` 表内**不存在**任何 `employee_id` / `staff` / `pos` 关联列（`raw/10` §11 → 空结果），`pos_employee` 亦无 `user` / `account` / `login` 反向关联列（§12 → 空结果）→ **M1 必须走「新增 `sys_user.employee_id` 列 + 人工/接口回填」路线，`phone` 自动映射不可行**（需用户确认回填数据来源） |
| 事实 3（不可行路径） | `sys_user.store_id` + `pos_employee.store_id` **不足以唯一定位员工**（同店多员工）→ 不能作为 M1 的映射依据 |
| 事实 4（与 M3 的关系） | M3（`pos_employee.store_id ∈ ctx.storeIds`）**不依赖** `sys_user.employee_id` → M3 可独立先行；M1 落地后，「同店他人」越权才被堵死 |

#### S5 — 决策 2「集合语义」的**数据来源事实**（影响修复实现）

| 项 | 内容 |
|---|---|
| 位置 | `RbacContextLoader.java:103-140` |
| 事实 | `ctx.storeIds` = `sys_role_data_scope.data_scope_value`（逗号分隔，多角色**并集**）+ **兜底** `user.getStoreId()`（`:138`）；`data_scope_value` 为 NULL 时跳过解析（`:124` 有 null 判空，无 NPE 风险） |
| 本机现状 | 8 条 `sys_role_data_scope` 的 `data_scope_value` **全为 NULL**（`raw/03` R 段）→ 实际 `storeIds` **恒为单元素** `{user.store_id}` |
| 影响 | 按决策 2 落地 `IN (...)` 后：单店账号行为与现状**一致**（不会更严也不会更松）；**多店账号必须运维侧配置 `data_scope_value`** 才会出现多元素集合 → 修复文档需附一条运维说明，否则「集合语义」在生产等价于「单值语义」 |

#### S6 — M3 判定必须**同时带 `tenant_id`**（否则跨租户同 `employee_id` 命中）

| 项 | 内容 |
|---|---|
| 事实 | `pos_employee` 有 `tenant_id`（`raw/00` 的 `GROUP BY tenant_id, store_id` 查询成功执行）；`employee_id` 是**租户内**主键，跨租户可重号 |
| **实测分布（2026-11-16 只读核验）** | `pos_employee` 150 行按 `tenant_id, store_id` 分布为 **`tenant_id=NULL` → 144 行**（全部 `store_id=1`）、**`tenant_id=1` → 6 行**（`store_id=1`）（`raw/11-m1-mapping-key-alternatives.out.txt` §H）→ ⚠️ **`tenant_id` 存在大量 NULL**（96% 的员工行没有租户归属） |
| 结论 | M3 口径应为 **`tenant_id = ctx.tenantId` AND `store_id IN ctx.storeIds`**（双条件），只判 `store_id` 会在跨租户重号场景下误命中 → 与 N10 的唯一键问题同源。⚠️ **但 `tenant_id = ?` 对 NULL 行恒 FALSE** → 按上述口径，**144 名 `tenant_id IS NULL` 的员工将全部被判为「不在范围内」** → M3 落地前必须先决定这批行的归属口径（`tenant_id IS NULL` 视为「无租户隔离的历史/测试数据」并先回填，还是放行 `tenant_id IS NULL OR tenant_id = ?`）→ **列为 M3 实施前阻塞项** |

### X.5 N8–N11 的修复归属（**N8 已拆出 I191**；其余在本 Issue 内）

| 编号 | 修法要点 | 批次 |
|---|---|---|
| N8 | **已拆出为 I191** —— `markLeaveDays` 补 `save` / 更新、先定请假语义（新增 `leave_type` 列 或独立请假表）、`storeId` 取员工所属门店而非硬编码 `1L` → 详见 `I191-leave-approval-attendance-not-persisted.md` | **见 I191**（不在本条第 1 批） |
| N9 | `calculateOrder` 补 `order.tenantId == ctx.tenantId` 校验 + `order.storeId ∈ ctx.storeIds`；`matchRule` / item 查询补 `tenantId`；跨租户订单 → 抛 `BizCode.FORBIDDEN` | 第 1 批 |
| N10 | 新增迁移脚本：三表补 `tenant_id` 列 + `uk_employee_date` 扩列含 `tenant_id`（MySQL 8.0.29+ 用 `ADD COLUMN IF NOT EXISTS`，低版本先 `SHOW COLUMNS` —— 见 I130 同族约束） | 第 1 批（DDL 单独评审） |
| N11 | M1 的前置事实登记（`sys_user` 无 `employee_id`；候选映射键 `phone` **只读核验后交集为 0 → 不可行**）→ **不实施**，仅作为 M1 时间点决策依据 | 阶段 2（M1） |
| S5 | 修复文档附运维说明：`data_scope_value` 为 NULL 时集合退化为单店；多店账号需配置该列 | 第 1 批（文档） |
| S6 | M3 判定加 `tenant_id` 条件（与 N10 的唯一键扩列同批） | 第 1 批（M3） |

---

## 5. 边界声明

- 本 Issue 结论来自**静态代码分析** + **本机开发库只读查询** + **运行期只读 GET 探测**。
- **未修改任何业务代码**、**未执行任何写操作**、**未执行任何 DDL**、**未新增任何入库脚本**。
- ⚠️ **观测限制（关键）**：本机库 `commission_record` **10 行全部集中在 `store_id=1`**；`attendance_record` / `attendance_leave_request` / `commission_salary` / `commission_settlement` **均为 0 行**。
  → 因此「跨门店**泄露实数据**」**无运行样本可证**，本 Issue 的 VERIFIED 范围限定为**授权层结构性确认**（`if (storeId != null)` 无 else 分支 + 拦截器 `canAccess(null)` 放行 + 成对 200/403 对比）；
  **实际数据泄露后果标 UNVERIFIED**，需在**他店有提成/考勤数据**的环境复测。
- 兜底 ID 说明：本批所有按 ID 探测一律使用 `99999999`，运行前已确认 `attendance_leave_request` / `attendance_record` 表 0 行 → 该 ID 必然不存在；**未使用任何真实业务主键**作为「不存在」样本。
- 未核实：生产库 `commission_record` / `commission_salary` 的门店分布；若生产存在多门店提成数据，本条风险等级需上调。
- 未核实：`DataScopeInterceptor.WHITELIST` 中导出路径的豁免理由（可能为流式响应技术原因）。
- ⚠️ **§X（N1–N7）的验证等级**：**静态 VERIFIED**（代码路径与行号可核），**运行期后果 UNVERIFIED** —— N4/N5/N6/N7 涉及的写接口与详情接口，其权限码当前均为死码（I183 / I184）；N1/N3 涉及的读接口在本机库无数据样本（`attendance_record` / `attendance_leave_request` 0 行）。补完 I183/I184 绑定后**必须立即复测**。
- ⚠️ **§X.4（N8–N11 / S5–S6）的验证等级**：**静态 VERIFIED**（代码与 DDL 事实确定，行号可核），**运行期 UNVERIFIED** —— N8 涉及的 `attendance_record` 0 行（无法观测「未落库」的实数据后果，**该条已拆出为 I191**）；N9 涉及 `commission:calculate` 0 绑定（死码，超管以外不可达）；N10 的**运行期列存在性为推定**（依据运行期列表接口 200 且查询含 `tenant_id`），**未执行 `SHOW COLUMNS` 复核**（本轮按指令不执行 SQL）→ 该条标 **UNVERIFIED（推定）**；N11 的 `phone` 映射键**已于 2026-11-16 只读核验 → 交集 0 行，判定为「不可行」**（证据 `raw/10-m1-mapping-key-phone.out.txt` / `raw/11-m1-mapping-key-alternatives.out.txt`）。
- ⚠️ **N2 的跨租户分支**：`AttendanceConfigController.list` 的 OR 分支缺 `tenant_id` 约束 → 这是**跨租户**问题（严于其余 6 条的跨门店问题），但同样受「`attendance:config:list` 死码」掩盖 → 运行期未复现。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。

---

**下一步（2026-11-16 用户决策后更新）**：

1. **第 1 批（本 Issue 主体）**：I186 范围控制 —— 缺省 `storeId` 按 `ctx.storeIds` **集合**过滤（`IN (...)`）+ N1–N7 / N9–N11 逐条修（**N8 已拆出 I191，不在本批**）+ **M3 员工归属校验**（`tenant_id = ctx.tenantId` AND `store_id IN ctx.storeIds`）+ **N10 的 DDL 迁移脚本**（三表补 `tenant_id`、唯一键扩列，需单独评审）；
2. **第 2 批**：I189 权限码拆分 + 405 特判；
3. **第 3 批**：I183 / I184 角色绑定（**必须等第 1、2 批上线后**）；
4. **第 4 批**：I185 前后端码对齐；
5. **M1（阶段 2）**：`sys_user.employee_id` 映射 + 数据迁移 —— 时间点待用户拍板（Q2）；**前置事实见 §X.4 N11**（候选映射键 `phone` **只读核验后交集为 0 → 不可行**，须改走「新增列 + 人工回填」）。
6. **I191（独立批次）**：请假审批落库缺失 —— 需先做业务决策（请假语义 α/β/γ），DDL 与 **I186 N10** 合并到同一份迁移脚本。

> ⚠️ 顺序不可颠倒：先补绑定（I183/I184）会把 I186 / I189 从「潜伏」变为「可利用」。
> ⚠️ **I189 已同步修正**：`I189` 中「`cancel` 未回滚 `approve` 写入的考勤请假天数」的前提不成立（见 §X.4 N8，天数从未落库）→ 该表述已改为「**当前 cancel 为空转（markLeaveDays 未落库，见 N8/I191）**」（2026-11-16 用户决策已批准）。
