# I191 — 请假审批通过后考勤记录从未落库（`markLeaveDays` 只构造、不保存）

> **状态**：**Open**（本批**仅登记**，**未修改任何业务代码、未写库**）
> **严重度**：**P1**（功能缺失 + 假成功：审批接口返回 200 且状态置为 `approved`，但 `attendance_record` **零写入** → 请假在考勤口径上完全不存在；影响考勤统计与月度报表的业务正确性。当前不可利用性来自 `attendance:leave:approve` 死码（I183）与 `attendance_leave_request` 表 0 行）
> **发现日期**：2026-11-16（batch2-C 权限对齐调查的第三轮静态核验，从 I186 §X.4 N8 拆出）
> **登记人**：Cursor
> **绑定基线**：HEAD `56d59a2`
> **验证等级**：**静态 VERIFIED（L4）** —— 代码路径逐行可核；**运行期 UNVERIFIED** —— `attendance_record` 表 **0 行**，且 `attendance:leave:approve` 为死码 → 无运行样本可复现「已落库 vs 未落库」的差异
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`
> **证据**：`audit/evidence/F171-attendance-commission-perm-alignment/`（`raw/01-static-endpoint-matrix.md` A16–A18、`raw/03` 分布段）
> **关联**：**I186**（同模块；原 §X.4 N8，本条已从 I186 拆出）、**I189**（撤回 `cancel` 当前为**空转**，其 R3 表述已按本条修正）、I183（`attendance:leave:approve` 死码）、I188（RBAC 漂移）、I134（异常语义同族）
> **用户决策（2026-11-16）**：从 I186 单独立项 —— N8 属「功能缺失（写入被静默丢弃）」，与 I186「数据范围失控」是**不同缺陷类型**；修复需**先定请假语义**（表内无请假标记列），且需独立业务讨论

---

## 0. 一句话描述

请假审批通过时，`LeaveRequestServiceImpl.markLeaveDays` 会为请假区间的每一天**构造**一条 `AttendanceRecord`，
但方法体内**既不 `save` 也不 `update`** → 记录被静默丢弃 → **审批接口返回 200、状态置为 `approved`，而 `attendance_record` 一行都不会增加**；
请假因此在考勤口径上**完全不存在**（统计、月度报表均不含请假）。

---

## 1. 事实核实

### 1.1 代码位置与逐行事实

`backend/src/main/java/com/meiyetec/service/impl/LeaveRequestServiceImpl.java:119-133`：

```java
119: private void markLeaveDays(Long tenantId, Long employeeId, LocalDate startDate, LocalDate endDate) {
120:     LocalDate current = startDate;
121:     while (!current.isAfter(endDate)) {
122:         AttendanceRecord record = attendanceRecordService.getByEmployeeAndDate(tenantId, employeeId, current);
123:         if (record == null) {
124:             record = new AttendanceRecord();
125:             record.setTenantId(tenantId);
126:             record.setEmployeeId(employeeId);
127:             record.setCheckDate(current);
128:             record.setIsAbsence(0);
129:             record.setStoreId(1L);
130:         }
131:         current = current.plusDays(1);
132:     }
133: }
```

| 行号 | 事实 |
|---|---|
| `:122` | 通过 `attendanceRecordService.getByEmployeeAndDate`（`AttendanceRecordServiceImpl.java:74-76` → `AttendanceRecordMapper.findByEmployeeAndDate`）查询当日考勤记录 |
| `:124-129` | `record == null` 分支：**构造**一条 `AttendanceRecord`（tenantId / employeeId / checkDate / `isAbsence=0` / `storeId=1L`） |
| `:130` | 循环推进（日期推进正确，无 off-by-one） |
| **全方法** | **没有任何 `save` / `insert` / `update`** —— 构造出的对象在方法结束时被丢弃 |
| `:123` 的 `else` 语义 | `record != null` 分支（当日已有打卡记录）**同样什么都不做** —— 既不更新 `is_absence`，也不写任何请假标记 |
| 依赖注入 | `attendanceRecordService`（`:28`）在本类中**只用于这一个只读查询** → 该字段**不承载任何写入能力** |

### 1.2 调用链与触发点

| 环节 | 位置 | 事实 |
|---|---|---|
| 审批入口 | `LeaveRequestController.java:42-43` → `PUT /attendance/leave/{id}/approve`（权限码 `attendance:leave:approve`） | — |
| 服务实现 | `LeaveRequestServiceImpl.approve`（`:50-72`） | `:61` `request.setStatus(approved ? "approved" : "rejected")` → `:64` `updateById(request)` → `:66-68` `if (approved) { markLeaveDays(...) }` |
| 结果 | `approve` 返回 `LeaveRequest` | **HTTP 200 + `status=approved`** → 客户端与审批人**均无异常信号** |
| 事务 | `approve` 标注 `@Transactional(rollbackFor = Exception.class)` | 无异常发生 → **事务正常提交**，请假的 `status` 确实落库为 `approved` |

> ⚠️ **假成功形态**：这是一个**「写了一半」的假成功** —— 请假单状态写入成功（可查、可审批流正常结束），而它本应产生的考勤侧副作用**静默丢失**。
> 与 I189 的「HTTP 200 + code 500」型假成功不同：这里 **body 全部正常**（`code=200`），**没有任何外部可见的错误信号** → 排障时只能靠对账考勤数据发现。

### 1.3 影响面核对（逐个读点确认）

| 读点 | 位置 | 是否受「未落库」影响 |
|---|---|---|
| `GET /attendance/statistics` | `AttendanceController.java:105-114` → `AttendanceRecordServiceImpl.getStatistics`（`:89-90`）→ `AttendanceRecordMapper.findByStoreAndDateRange` | **是** —— 请假日不出现在统计里（请假天数、旷工数等口径均不含请假） |
| `GET /attendance/monthly-report` | `AttendanceController.java:116+` → 同 mapper | **是** —— 月度报表不含请假 |
| `GET /attendance/records` | `AttendanceController.java:98-103` → `queryRecords` | **是** |
| `GET /attendance/today` | `AttendanceController.java:60-63`（读 `getByEmployeeAndDate`） | **是** —— 请假当天查不到记录 |
| `attendance_config` / 考勤规则 | `AttendanceConfigController` | **否**（不依赖 `attendance_record`） |
| **提成 / 工资计算链** | `CommissionCalculationService.java:43-121`、`CommissionController.generateSalary`（`:277-280`） | **否** —— 全仓 grep「出勤 / attendanceDays / actualDays / workMinutes」**未命中任何提成或工资计算代码**；`commission_record` 的金额来源是 `pos_order_item.final_price` × `commission_rule.rate` → **提成金额与请假无关** |
| 「请假天数」字段本身 | `attendance_leave_request.leave_days` | **否** —— `submit`（`:43`）已正确计算 `ChronoUnit.DAYS.between + 1` 并落库 → 请假单上的天数是对的 |

> ❗ **口径纠正（与提报描述的差异）**：提报时称「影响工资计算出勤基数」——**在本仓不成立**：提成/工资计算链**不读** `attendance_record`，`work_minutes` 仅在 `AttendanceController.java:62` 作为单点展示字段使用。
> 因此本条的真实影响面 = **考勤统计 / 月度报表 / 今日考勤 / 考勤列表** 四类读点，**不含提成与工资金额**。
> ⚠️ 若产品侧存在「按出勤天数折算工资」的口径，则该口径**当前在本仓无实现**（属另一条「缺失能力」，非本条）；标 **UNVERIFIED（需产品确认）**。

### 1.4 连带缺陷（即使补上 `save` 也不能直接上线）

| # | 缺陷 | 位置 | 后果 |
|---|---|---|---|
| D1 | `storeId` **硬编码 `1L`** | `:129` | 补 `save` 后，请假记录会被写到**门店 1**而非请假单自己的门店 → 他店员工的请假会污染门店 1 的考勤（正是 I186 要治理的「门店维度污染」的另一条路径） |
| D2 | `isAbsence = 0`（**非旷工**） | `:128` | 请假被记为「正常出勤」；而请假与旷工在业务上**不是同一件事** → 需要独立的请假标记 |
| D3 | `attendance_record` **无请假标记列** | `init_attendance.sql:39-70` | 现有列只有 `is_late / is_early_leave / is_absence / work_minutes / remark` → 「请假」这一事实**无处落库**（塞 `remark` 是字符串约定，无法用于统计） |
| D4 | 唯一键约束 | `init_attendance.sql:68` `uk_employee_date (employee_id, check_date, deleted)` | 一人一天至多一行 → 已存在打卡记录的那天**只能 update 不能 insert**（`:123` 的 else 分支必须实现更新，否则静默跳过） |
| D5 | `attendance_record` 缺 `tenant_id` 列 | `init_attendance.sql:39`（DDL 无该列，实体 `AttendanceRecord.java:15` 有） | 补 `save` 前需先补迁移脚本，否则新环境缺列（与 I186 N10 同一批 DDL） |
| D6 | 请假单的 `storeId` 本身不可信 | `LeaveRequestServiceImpl.submit`（`:45` `request.setStoreId(storeId)`，`storeId` 来自请求体） | 落库考勤时应以**员工所属门店**（`pos_employee.store_id`）为准，而非调用方传入值（与 I186 N7 同源） |

### 1.5 当前为何不可复现

| 条件 | 事实 |
|---|---|
| 权限码 | `attendance:leave:approve` 运行期 **0 角色绑定**（死码，见 I183 / `raw/04b`）→ 非超管不可调用 |
| 数据 | `attendance_leave_request` **0 行**、`attendance_record` **0 行** → 即便调用成功也**观察不到**差异 |
| 写接口纪律 | 本批**未调用任何写接口**，未审批任何请假单 |

> → 本条验证等级 = **静态 VERIFIED（L4）**；**运行期后果 UNVERIFIED**。

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | **请假在考勤口径上不存在** | 任意请假审批通过 | 考勤统计、月度报表、今日考勤均**不含请假** → 员工出勤天数虚高、缺勤统计失真 |
| R2 | **假成功（无错误信号）** | 同上 | 接口 200、状态 `approved`、日志无异常 → **排障不可发现**；只能靠人工对账 |
| R3 | **请假与旷工混淆** | 补 `save` 而不定语义 | 请假被记为 `is_absence=0` 的「正常出勤」→ 口径错误固化 |
| R4 | **门店维度污染** | 补 `save` 而不修硬编码 | 他店请假写入门店 1（`storeId=1L`）→ 跨店考勤数据污染，与 I186 的治理目标**直接冲突** |
| R5 | **重复审批/撤销时的一致性** | 审批通过后再撤回 | `cancel`（I189）只改 `attendance_leave_request.status`，**不回滚考勤** → 但因本条从未落库，当前是**空转**；一旦本条修复，I189 的回滚逻辑**必须同批补上**，否则会产生「已撤回但考勤仍记请假」的脏数据 |
| R6 | **同一天既有打卡又有请假时行为未定义** | 员工当日已打卡 | 当前 else 分支静默跳过；修复后需明确「打卡优先 / 请假优先 / 标记共存」的口径 |

---

## 3. 建议方案（**仅建议，本批不实施**）

> 📌 **前置决策（必须先做）**：请假的**业务语义**与**落库位置**。三条候选路径：
> **α** 在 `attendance_record` **新增请假标记列**（如 `leave_type VARCHAR(32)` + `leave_days`，`is_absence` 保持 0）→ 统计口径需同步改；
> **β** 约定复用 `is_absence = 1` 表示请假 → **不建议**（请假≠旷工，会污染旷工统计）；
> **γ** 新增**独立请假考勤表**（如 `attendance_leave_day`）→ 影响面最大，但语义最干净、可与打卡彻底解耦。
> 选定后需同步：`attendance_record` 的统计口径（`AttendanceRecordServiceImpl.getStatistics`）、月度报表、`attendance_record` DDL 迁移（与 I186 N10 同批）。

| # | 步骤 | 内容 | 备注 |
|---|---|---|---|
| 1 | **定语义**（业务决策） | α / β / γ 三选一 | 需产品/运营确认；决定后续 DDL 与统计口径 |
| 2 | **补 DDL** | 按选定语义 ALTER `attendance_record`（含 `tenant_id` 补列，见 D5） | 与 I186 N10 的迁移脚本**合并到同一份**，避免两次改同一张表 |
| 3 | **修 `markLeaveDays`** | ① `record == null` → `save`；② `record != null` → 按 R6 口径 `update`；③ `storeId` 取**员工所属门店**（`pos_employee.store_id`）或请假单门店（需先解决 D6 的可信性），**删除硬编码 `1L`**；④ 请假标记按选定语义写入，**不再用 `is_absence=0` 表达请假** | 建议把「计算区间 + 写入」拆成可单测的纯函数 + 持久化两步，便于覆盖闰年/跨月/长区间 |
| 4 | **补幂等与边界** | 同一区间重复审批、审批后又撤回、区间内已有打卡 | 唯一键 `uk_employee_date` 冲突走 `update` 而非抛 `DuplicateKeyException` |
| 5 | **同步回滚逻辑（与 I189 同批）** | `cancel` 时对称撤销已落库的请假标记 | **必须与本条同批上线**，否则 R5 的脏数据会出现 |
| 6 | **补统计口径** | `getStatistics` / `monthly-report` 把请假纳入输出（请假天数、迟到/旷工口径） | 与第 1 步的语义选择一致 |
| 7 | **补测试** | 覆盖：1 天 / 跨月 / 闰日 / 已有打卡 / 重复审批 / 审批后撤回 | 当前项目存在**测试编译失败**（I052 / I103）→ 需先确认测试可编译 |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 审批通过一张 3 天请假单（员工所属门店 = S） | `attendance_record` **新增 3 行**（`check_date` 覆盖 3 天、`employee_id` = 该员工、`store_id` = **S**、**不是 1**） |
| 2 | 请假标记字段 | 按选定语义写入（α：新增列有值且 `is_absence=0`；γ：新表 3 行）→ **不得**用 `is_absence=0` 表示请假 |
| 3 | `GET /attendance/statistics`（同区间） | 请假天数 = 3；迟到/旷工口径**不因请假被误计** |
| 4 | `GET /attendance/monthly-report?employeeId=` | 含该 3 天请假 |
| 5 | 审批通过一张**他店员工**请假单 | 落库 `store_id` = 该员工门店（**不得**落 1） |
| 6 | 员工当日**已有打卡记录**时审批请假 | 按 R6 选定口径执行（`update` 或跳过），**不得**抛 `DuplicateKeyException`、**不得**静默丢弃 |
| 7 | 重复审批同一单 | 第二次被拒（`该申请已处理,无法重复审批`），`attendance_record` **不重复增行** |
| 8 | 审批通过后撤回（与 I189 同批） | `attendance_record` 中该请假日被撤销（**不留残留**） |
| 9 | 审批接口响应 | 仍为 **200 + `status=approved`**，且**日志无异常**（本条不改变接口契约） |

---

## 5. 边界声明

- 本 Issue 结论来自**静态代码分析**（`LeaveRequestServiceImpl.java` / `AttendanceRecordServiceImpl.java` / `AttendanceRecordMapper.java` / `AttendanceController.java` / `AttendanceRecord.java` / `init_attendance.sql`）+ **本机开发库只读查询**（`attendance_record` / `attendance_leave_request` 行数、分布）。
- **未修改任何业务代码**、**未执行任何写操作**、**未执行任何 DDL**、**未新增任何入库脚本**。
- ⚠️ **运行期未复现**：`attendance_record` 与 `attendance_leave_request` 均 **0 行**，`attendance:leave:approve` 为死码 → 「落库缺失」的**实数据后果标 UNVERIFIED**；「代码中无 `save`/`insert`」为**静态 VERIFIED**。
- ⚠️ **「影响工资计算出勤基数」在本仓不成立**（§1.3）：提成/工资链**不读** `attendance_record` → 该表述已在本 Issue 更正；「是否存在按出勤折算工资的产品口径」**未确认**，标 **UNVERIFIED**。
- ⚠️ 请假语义（α/β/γ）**未确认** —— 需业务决策；本 Issue 不预设结论。
- 兜底 ID 说明：本批未做按 ID 的写探测，**未使用任何真实业务主键**作为「不存在」样本。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。

---

**下一步（待用户决策）**：

1. **业务决策**：请假语义 α / β / γ 三选一（阻塞项）→ 建议 **α**（新增标记列）或 **γ**（独立表）；
2. **技术修复**：DDL（与 I186 N10 合并）→ `markLeaveDays` 写入 + 门店取员工门店 → 统计口径同步；
3. **同批联动**：与 **I189** 的撤回回滚逻辑**必须同批上线**（R5）；与 **I183** 的 `attendance:leave:approve` 绑定**必须排在修复之后**（否则批准即产生脏数据）。
