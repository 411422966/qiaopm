# I194 — 前端 6 个文件硬编码 `storeId: 1`，使 I186 第 1 批「缺省按门店集合过滤」永不触发

> **状态**：**Open（仅登记，不修）** —— 本条**独立批次**处理，I186 第 1 批**不包含**前端改动
> **严重度**：**P1**（不是「越权漏洞」，而是**修复被绕过** —— 后端修好后，前端仍把 `storeId=1` 显式传下去，后端拿到的永远是显式单值 → 集合过滤形同虚设）
> **发现日期**：2026-11-16（I186 第 1 批修复草案 §4.3 核对前端调用点时发现）
> **登记人**：Cursor
> **绑定基线**：HEAD `d7e4a2c`（`docs(audit): I186 第 1 批修复草案（G0/G1/专项1/专项2/Q1）`）
> **验证等级**：**静态 VERIFIED**（前端源码行号 + 后端取参路径均可复核）；**运行期后果 UNVERIFIED**（需 I194 落地后复测）
> **关联**：**I186**（本条是其第 1 批的**前置阻塞项**）、I185（前后端权限码漂移，同属「前后端契约不同步」族）、I152（外溢清单，幽灵功能同族）
> **用户决策（2026-11-16）**：登记为独立 Issue，**紧随 I186 第 1 批之后**上线，不得与第 1 批脱节

---

## 0. 一句话描述

前端有 **6 个文件、共 7 处**把门店 ID 写死成 `1` 并作为**显式参数**发给后端；
I186 第 1 批修的是「**缺省 `storeId` 时按 `ctx.storeIds` 集合过滤**」—— 但前端**永远显式传 1** → **后端修复在真实链路上不会生效**。

---

## 1. 事实核实（前端源码定位）

| # | 文件 | 行 | 代码 | 调用点 |
|---|---|---|---|---|
| 1 | `frontend/meiyetec-web/src/views/commission/salary.vue` | 136 | `commissionApi.generateSalary({ storeId: 1, salaryMonth: filter.value.salaryMonth })` | `POST /commission/salary/generate` |
| 2 | `frontend/meiyetec-web/src/views/commission/settlement.vue` | 206 | `commissionApi.generateSettlement({ storeId: 1, salaryMonth: genMonth.value })` | `POST /commission/settlement/generate` |
| 3 | `frontend/meiyetec-web/src/views/attendance/index.vue` | 224 | `attendanceApi.statistics({ storeId: 1, startDate, endDate })` | `GET /attendance/statistics` |
| 4 | `frontend/meiyetec-web/src/views/attendance/index.vue` | 246 | `attendanceApi.checkIn({ ..., storeId: 1 })` | `POST /attendance/check-in` |
| 5 | `frontend/meiyetec-web/src/views/attendance/leave.vue` | 317 | `attendanceApi.submitLeave({ ..., storeId: 1 })` | `POST /attendance/leave` |
| 6 | `frontend/meiyetec-web/src/views/commission/rule-list.vue` | 119 | `commissionRuleApi.simulate({ ..., storeId: 1 })` | 提成规则试算（`/commission/simulation` 族） |
| 7 | `frontend/meiyetec-web/src/views/commission/simulation.vue` | 224 | 表单初始化 `storeId: 1` | 提成模拟表单默认值 |

> **统计口径**：**6 个文件**（`commission/salary.vue`、`commission/settlement.vue`、`attendance/index.vue`、`attendance/leave.vue`、`commission/rule-list.vue`、`commission/simulation.vue`）、**7 处赋值**（`attendance/index.vue` 占 2 处）。

### 1.1 对照：真正「缺省不传」的前端（不受影响）

| 前端位置 | 现状 | 说明 |
|---|---|---|
| `views/attendance/records.vue:219` | `storeId: query.storeId \|\| undefined` | ✅ 缺省不传 → **I186 第 1 批的核心受益点** |
| `views/attendance/statistics.vue:252` | 同上 | ✅ 同上 |
| `views/attendance/config.vue` | `{ storeId }`（可为 undefined） | ✅ 同上 |

---

## 2. 为什么这会让后端修复失效

I186 第 1 批修复的是**后端**这条路径：

```
请求未带 storeId
  → DataScopeInterceptor：采集不到 storeId → 不校验（现状的「缺省放行」）
  → Service 层：resolveStoreScope() → ctx.storeIds 集合 → store_id IN (集合)
```

而前端**总是带 `storeId=1`**：

```
请求带 storeId=1
  → DataScopeInterceptor：sid=1 → ctx.canAccessStore(1)
       ├─ 账号范围含门店 1 → 放行 → Service 层按「显式值」走 eq(1)  ← 集合过滤被跳过
       └─ 账号范围不含门店 1 → 403（门店≠1 的账号点按钮直接被拒）
  → Service 层：requestedStoreId != null → 取交集 → 只返回门店 1
```

→ **两条路径都不会走「按集合过滤」**：要么退化成 `eq(1)`（集合语义丢失），要么 403（功能不可用）。

### 2.1 三个具体后果

| # | 后果 | 说明 |
|---|---|---|
| 1 | **「缺省按集合」这条修复路径在生产链路上不可达** | 后端 `resolveStoreScope()` 的集合语义只在「请求确实不带 storeId」时生效；前端不带 → **永远带** |
| 2 | **门店 ≠ 1 的账号功能不可用** | `storeId=1` 若不在 `ctx.storeIds` → 拦截器 403 → 打卡 / 请假 / 生成工资 / 生成结算全部失败（**现状即如此**，非本次引入） |
| 3 | **多店账号语义被悄悄窄化** | 账号可见 {1,5}，前端只操作门店 1 → 门店 5 的工资永远不会被生成/查看，且 UI 无任何提示（**静默漏算**） |

---

## 3. 修复方向

| 方案 | 做法 | 优点 | 代价 |
|---|---|---|---|
| **A（推荐）** | **不传 `storeId`**，由后端按 `ctx.storeIds` 注入 | 与 I186 第 1 批后端口径完全一致；多店账号自动覆盖集合内全部门店 | 需要后端「缺省按集合」的语义在写接口上也成立（工资/结算生成是**单门店语义**，见 §4） |
| **B** | 前端从**当前用户上下文**取门店 ID（如 `userStoreStore` / `useUserStore()`） | 与 I186 后端无耦合 | 仍需前端维护「当前门店」状态；多店账号仍需选店交互 |
| **C** | 加门店下拉选择器，写入请求 | 语义最清晰 | 交互改动大，超出本条范围 |

> ⚠️ **方案 A 的边界**：**写类接口**（`/commission/salary/generate`、`/commission/settlement/generate`）的语义是「**哪一家店的工资/结算**」，**单值**是必要的 → Q-F / Q-G 已定为「多店账号 → **400 要求显式指定**」→ 这两个接口的前端**不能简单删掉 `storeId`**，必须先有「选店」交互（方案 B/C）。
> → **考勤类读接口**（`/attendance/statistics`）与**写接口**（打卡 / 请假）可以走方案 A。

### 3.1 建议的落地拆分

| 批次 | 范围 | 说明 |
|---|---|---|
| I194-1 | `views/attendance/index.vue:224`（statistics）、`:246`（check-in）、`views/attendance/leave.vue:317` | 考勤三处 → 方案 A（不传 storeId） |
| I194-2 | `views/commission/salary.vue:136`、`views/commission/settlement.vue:206` | 写接口 → 方案 B/C（先加选店交互），**不得直接删参数**（否则多店账号 400、单店账号仍要带值） |
| I194-3 | `views/commission/rule-list.vue:119`、`views/commission/simulation.vue:224` | 试算/模拟 → 读性质，可走方案 A（注意 `/commission/simulation` 归 **I187** 门控，404） |

---

## 4. 与其他 Issue 的关系

| Issue | 关系 |
|---|---|
| **I186** | **本条是 I186 第 1 批的前置阻塞项**：第 1 批修完后端「缺省按集合」，但**必须紧随本条**，否则修复在真实链路上不生效（**修复效果被绕过**） |
| **I187** | `views/commission/simulation.vue` 调用的后端控制器无鉴权且当前 404 → 该页面的硬编码 `storeId: 1` 在解封前无实际后果 |
| **I185** | 同属「前后端契约不同步」族（一个漂移在权限码，一个漂移在门店维度） |
| **I192** | 若产品选择「员工登录 `sys_user`」（方案 B），前端需要「当前登录人 ↔ 门店」映射 → 本条与 I192 强相关 |
| **I193** | 无关（I193 是员工表的 `tenant_id` 口径） |

---

## 5. 同族发现（本条**不**包含，登记备查）

全仓另有 **8 处** `storeId: 1` 硬编码，**均不在 I186（考勤/提成）范围内**，本条不处理，另行评估：

| 文件 | 行 | 用途 |
|---|---|---|
| `views/google-maps/index.vue` | 140 | 地理编码表单默认值 |
| `views/crm/care-review.vue` | 559 / 574 | 评价表单默认值 |
| `views/report/caliber.vue` | 100 | 口径查询参数 |
| `views/pos/checkout.vue` | 638 | POS 收银下单 |
| `views/resource/resource-schedule.vue` | 470 | 资源排班重算 |
| `views/technician/evaluation.vue` | 339 / 367 | 技师等级评定 |
| `views/ai-scheduler/index.vue` | 61 / 62 | AI 排班 |
| `views/setting/employee.vue` | 226 | 员工表单默认值 |

> ⚠️ 其中 **`views/pos/checkout.vue:638`（POS 收银下单）** 的影响面最大（下单金额归属门店），建议优先评估。

---

## 6. 边界声明

- 本条**仅登记**：本轮**未修改任何前端代码、未修改任何后端代码、未写库、未执行 DDL**。
- 所有行号与调用链均来自**静态源码核对**；**运行期后果 UNVERIFIED**（需 I186 第 1 批落地 + 本条落地后，用多店账号实测）。
- 本条**未触达** `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。
