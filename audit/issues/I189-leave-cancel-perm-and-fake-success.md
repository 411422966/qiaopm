# I189 — 请假撤回复用审批权限码，且「本人」校验可被请求体伪造；附带方法不匹配被吞成 HTTP 200 + code 500

> **状态**：**Open**（本批**仅登记**，**未修改任何业务代码、未写库**）
> **严重度**：**P2**（当前不可利用 —— `attendance:leave:approve` 是死码（I183）、`attendance_leave_request` 表 0 行；但**修复绑定后即成为可利用路径**，属「修复即触发」型缺陷）
> **发现日期**：2026-11-16（batch2-C 权限对齐只读调查）
> **登记人**：Cursor
> **绑定基线**：HEAD `56d59a2`
> **验证等级**：**VERIFIED（静态 L4）** —— 代码路径已完整定位；运行期**未复现**（库内 0 条请假样本，且权限码死），实数据后果标 **UNVERIFIED**
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`
> **证据**：`audit/evidence/F171-attendance-commission-perm-alignment/`（`raw/01-static-endpoint-matrix.md` A18、`raw/06b` C 段末行）
> **关联**：I183（考勤死码，修复即触发）、I186（`employeeId` 无范围约束）、**I191（原 N8「请假审批后考勤记录从未落库」已拆出独立立项；本条 R3 的前提据此修正）**、I134（缺失参数返回 500）、I088 / I095（catch 吞异常族）

---

## 0. 一句话描述

`PUT /attendance/leave/{id}/cancel`（员工**撤回自己**的请假申请）复用了**审批**权限码 `attendance:leave:approve`；
其「无权取消他人申请」的校验依赖**请求体传入的 `employeeId`** 与记录里的 `employeeId` 比对 → **调用方可任意伪造 `employeeId` 绕过本人语义**。
此外 `GET /attendance/leave/{id}/cancel`（方法不匹配）被 `GlobalExceptionHandler.handleAny` 吞成 **HTTP 200 + body `code=500 "系统繁忙"`**（假成功语义）。

---

## 1. 事实核实

### 1.1 控制器侧（`backend/src/main/java/com/meiyetec/controller/LeaveRequestController.java`）

| 行号 | 路由 | 权限码 | 方法 |
|---|---|---|---|
| `:28-29` | `POST /attendance/leave` | `attendance:leave:submit` | 提交申请 |
| `:42-43` | `PUT /attendance/leave/{id}/approve` | `attendance:leave:approve` | 审批 |
| **`:54-55`** | **`PUT /attendance/leave/{id}/cancel`** | **`attendance:leave:approve`** | **撤回（员工本人动作，却用审批码）** |
| `:62-63` | `GET /attendance/leave/list` | `attendance:leave:list` | 列表 |
| `:74-75` | `GET /attendance/leave/my` | `attendance:leave:list` | 我的请假 |
| `:81-82` | `GET /attendance/leave/pending-count` | `attendance:leave:list` | 待审批计数 |
| `:88-89` | `GET /attendance/leave/{id}` | `attendance:leave:list` | 详情 |

> ⚠️ **语义错位**：员工撤回自己的申请，是「申请人」动作，却要求**审批人**权限码。
> 后果一：员工永远无法撤回（审批码死 / 即使绑定了也没绑给员工）。
> 后果二：把审批码与撤回动作绑在一起，导致**能做审批的人也能撤回任何人的申请**（当 `employeeId` 伪造成立时）。

### 1.2 服务侧「本人校验」的实现（`backend/src/main/java/com/meiyetec/service/impl/LeaveRequestServiceImpl.java`）

```java
76: public boolean cancel(Long tenantId, Long requestId, Long employeeId) {
...
84:     if (!request.getEmployeeId().equals(employeeId)) {
...        // 「无权取消他人申请」
90:     request.setStatus("cancelled");
```

| 事实 | 说明 |
|---|---|
| `employeeId` 来源 | **请求体**（`LeaveRequestController.java:56` 的 `@RequestBody Map<String, Object> params` → 取 `employeeId`） |
| 校验语义 | 「传入的 employeeId == 记录的 employeeId」→ **等价于「我知道这个 employeeId」** |
| 真正意图 | 应为「**当前登录用户**就是申请人」 |
| 可绕过性 | 知道（或猜到）目标 `employeeId` 即可通过；`employeeId` 是**自增/可枚举**的 `pos_employee.employee_id`（本机库 171 行） |

> ❗ **这不是「校验本人」，而是「校验参数相等」**。只要伪造 `employeeId`，任何持有 `attendance:leave:approve` 的人都能取消**他人**的请假申请。

### 1.3 与 `employeeId` 无范围约束同源

`GET /attendance/leave/my?employeeId=` 直接接受调用方传入的 `employeeId`（`LeaveRequestController.java:75`），实测（`raw/05b`）`admin` 传 `employeeId=99999999` → **200**（返回空数组）。
即：**本系统当前没有「当前登录员工」的概念**，`employeeId` 全部由调用方指定 → 「本人语义」在架构层面就无法成立（详见 I186）。

### 1.4 方法不匹配被吞成假成功（附带发现）

`backend/src/main/java/com/meiyetec/exception/GlobalExceptionHandler.java:91-95`：

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<Result<Void>> handleAny(Exception e) {
    log.error("[SystemException]", e);
    return ResponseEntity.ok(Result.error(BizCode.SYSTEM_ERROR));
}
```

运行期实测（`raw/06b` C 段末行）：

```
ADMIN [GET] /attendance/leave/99999999/cancel => HTTP 200 {"code":500,"message":"系统繁忙，请稍后重试","data":null,"traceId":"42925414fbbe49429942064f44f3e7f5","timestamp":"2026-11-16 03:10:06"}
```

| 项 | 值 |
|---|---|
| HTTP 状态码 | **200** |
| body `code` | **500** |
| body `message` | 「系统繁忙，请稍后重试」 |
| 真实异常 | `HttpRequestMethodNotSupportedException`（该路径仅注册了 `PUT`） |

> ⚠️ **假成功语义**：HTTP 200 + body `code=500` 是**契约漂移** —— 依赖 HTTP 状态码的客户端/网关（重试策略、监控告警）会把它当成功；只有解析 body 的前端才能察觉。
> `99999999` 为**确认不存在的兜底 ID**（`attendance_leave_request` 表 0 行），本次探测**无任何写副作用**。
> ⚠️ 另注：`handleAny` 捕获**所有** `Exception` → 405 / 400 / 解析失败等**客户端错误**全部被归为「系统繁忙」，会**淹没真实故障**（与 I134「缺失参数返回 500」同族）。

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | **员工无法撤回请假** | 任意员工想撤回 | 撤回接口要求审批码 → 403（当前因死码 403，修复后若按 I183 建议只把 `leave:approve` 给 MANAGER/BOSS，**员工仍然撤不回**） |
| R2 | **可取消他人请假** | 持有 `attendance:leave:approve` + 伪造 `employeeId` | 取消他人申请 → 影响考勤结算（⚠️ 当前 `markLeaveDays` **不落库** → 考勤侧暂无实数据影响；一旦 **I191** 修复落库，取消他人申请将直接改写他人考勤记录 → **I191 修复后本条风险等级上升**，必须与 I191 同批评估） |
| R3 | **撤销请假后考勤天数残留**（**当前为空转**） | 取消已审批的申请 | `LeaveRequestServiceImpl.approve:68-69` 在审批时调 `markLeaveDays` 写考勤记录；`cancel:90` 只改状态，**未见回滚考勤天数的逻辑** → 取消后考勤仍显示请假。⚠️ **前提修正（2026-11-16）**：`markLeaveDays` **只构造记录、从不落库**（`LeaveRequestServiceImpl.java:119-133` 全方法无 `save`/`insert`/`update`）→ 天数**从未被写入** `attendance_record`，因此当前 `cancel` 是**空转**（无脏数据残留，但也无回滚对象）。详见 **I191**（N8 已拆出独立立项） |
| R4 | 契约漂移（假成功） | 客户端用 GET 调 PUT-only 路径 | HTTP 200 + `code=500` → 网关/监控**误判为成功**，排障时看到「系统繁忙」却找不到原因 |
| R5 | **真实故障被淹没** | 任何未特判的异常 | `handleAny` 全捕获 → 客户端错误也报「系统繁忙」，服务端日志与前端提示语义脱节 |

---

## 3. 建议方案（**仅建议，本批不实施**）

| # | 步骤 | 内容 | 备注 |
|---|---|---|---|
| 1 | **撤回用独立权限码** | 新增 `attendance:leave:cancel`，绑给**员工本人**；`attendance:leave:approve` 只留给审批人 | 需与 I183 的绑定表同步 |
| 2 | **引入「当前登录员工」** | 从 token / 会话解析当前 `employeeId`（按用户→员工映射），**不再信任请求体传入的 `employeeId`** | 这是根因修复；工作量较大（涉及 `pos_employee` 与 `sys_user` 的映射关系，**本批未核实该映射是否存在**） |
| 3 | **短期兜底**：撤回时服务端用**记录里的 employeeId** 与**当前登录用户可管理的员工集合**比对 | 若映射暂不可得，至少改为「只能取消 `status IN ('submitted','pending')` 且 `approverId IS NULL` 的申请」 | — |
| 4 | **取消时回滚考勤天数** | `cancel` 中对称调用一次「撤销请假天数」逻辑（`markLeaveDays` 的逆向） | ⚠️ **当前 `cancel` 为空转** —— `markLeaveDays` 从不落库（见 I191）→ 无可回滚对象。**本步骤的真正前置是 I191 先修复并定下请假语义**（`attendance_record` 的请假标记字段），两者**必须同批上线**，否则撤回后会产生「已撤回但考勤仍记请假」的脏数据 |
| 5 | **修假成功**：`GlobalExceptionHandler` 增加 `@ExceptionHandler(HttpRequestMethodNotSupportedException.class)` → 返回 **HTTP 405**；并把 4xx 类异常与「系统繁忙」分离 | 同时收窄 `handleAny` 的捕获范围（可参照 I134 的处理） | 全局异常处理器的改动**影响面超出本批模块**，需单独排期与授权 |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | `clerk01` `PUT /attendance/leave/{自己的id}/cancel` | **200**（持 `attendance:leave:cancel`） |
| 2 | `clerk01` `PUT /attendance/leave/{他人的id}/cancel`（body 伪造 `employeeId`） | **403**「无权取消他人申请」，且**不改变记录状态** |
| 3 | `manager01` `PUT /attendance/leave/{id}/approve` | **200**（审批码有效） |
| 4 | `manager01` `PUT /attendance/leave/{id}/cancel` | 按最终权限设计（建议**不允许**审批人撤回他人申请） |
| 5 | 取消已审批申请后查 `attendance_record` | **当前为 N/A（空转）** —— 请假天数从未落库（见 I191）；待 **I191** 修复后，本项才成立，且期望「该员工的**请假天数已回滚**」（R3） |
| 6 | `GET /attendance/leave/99999999/cancel` | **HTTP 405**（不再是 200 + code 500） |
| 7 | `GET /attendance/leave/99999999/cancel` 的 body | 若仍为错误体，应为 4xx 语义的 `code`，**不出现「系统繁忙」** |

---

## 5. 边界声明

- 本 Issue 结论来自**静态代码分析**（`LeaveRequestController.java` / `LeaveRequestServiceImpl.java` / `GlobalExceptionHandler.java` / `AttendanceRecordServiceImpl.java`）+ **运行期只读 GET 探测**。
- **未修改任何业务代码**、**未执行任何写操作**、**未执行任何 DDL**、**未新增任何入库脚本**。
- ⚠️ **运行期未复现**：
  - 「可取消他人请假」→ **未复现**。原因有二：① `attendance:leave:approve` 是死码（I183），任何角色调用均 403；② `attendance_leave_request` 表 **0 行**，无可操作样本。
  - 该结论的验证等级为 **静态 VERIFIED（代码路径确定）**，**实数据后果标 UNVERIFIED**，需在有请假样本且绑定生效后复测。
- 兜底 ID 说明：`/attendance/leave/99999999/cancel` 使用 **确认不存在**的兜底 ID（`attendance_leave_request` 表 0 行）；**未使用任何真实业务主键**作为「不存在」样本；本次为 **GET** 请求，**无任何写副作用**。
- 未核实：`sys_user` ↔ `pos_employee` 是否存在可用的映射关系（这决定 §3 步骤 2 的可行性）。→ **2026-11-16 只读核验结论：`phone` 交集为 0 行，不可用**（详见 I186 §X.4 N11 与 `raw/10` / `raw/11`）。
- 未核实：`attendance_record` 中「请假天数」的具体存储字段与语义（决定 §3 步骤 4 的改法）。→ **2026-11-16 补充事实：`attendance_record` 表内无请假标记列**（现有列仅 `is_late / is_early_leave / is_absence / work_minutes / remark`），且 `markLeaveDays` 从不落库 → 语义需在 **I191** 中先定（α 新增标记列 / β 约定 `is_absence` / γ 独立请假表）。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。

---

**下一步**：与 I183 同批实施 —— **撤回权限码拆分必须与绑定修复一起上线**，否则会出现「绑了审批码、员工依然撤不回，且审批人可取消他人申请」的中间态。
