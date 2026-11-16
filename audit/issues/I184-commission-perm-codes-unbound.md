# I184 — 提成模块 24/28 权限码无角色绑定，全部 14 个写接口不可用

> **状态**：**Open**（本批**仅登记**，**未修改任何业务代码、未写库**）
> **严重度**：**P0**（上线阻断级 —— 提成模块仅剩 4 个只读列表码可用，规则维护、工资生成/调整/确认/发放、结算生成/确认/发放/批量共 14 个写接口对全部非超管角色不可用）
> **发现日期**：2026-11-16（batch2-C 权限对齐只读调查）
> **登记人**：Cursor
> **绑定基线**：HEAD `56d59a2`
> **验证等级**：**VERIFIED（运行期 L5a + 静态）** —— 运行期四账号实测 + `sys_role_permission` 实际绑定统计
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`（2026-11-16 02:00）
> **证据**：`audit/evidence/F171-attendance-commission-perm-alignment/`（`raw/01-static-endpoint-matrix.md` §2/§4、`raw/04b` V1、`raw/05`、`raw/05b`）
> **关联**：I183（考勤模块同类死码）、I185（前后端码双轨）、I186（门店数据范围参数驱动）、I188（种子与运行期漂移）、I115（权限注解覆盖率）

---

## 0. 一句话描述

后端 `@RequirePermission` 实际引用 **28 个** `commission:*` 权限码，其中 **24 个（86%）在 `sys_role_permission` 中绑定数为 0**；
可用���只有 `commission:rule:list` / `commission:record:list` / `commission:salary:list` / `commission:settlement:list` 4 个**只读列表码**（绑定 MANAGER / BOSS / ADMIN），
→ **规则增删改停、订单提成计算、工资生成/调整/确认/发放/批量、结算生成/确认/发放/批量/导出共 14 个写接口全部不可用**。

---

## 1. 事实核实

### 1.1 运行期绑定实测（`raw/04b` V1，共 41 条 `commission*` 码）

**有绑定（4 个业务码 + 3 个菜单/按钮码）**

| 权限码 | 类型 | 绑定角色 | 说明 |
|---|---|---|---|
| `commission` | menu | SUPER_ADMIN, ADMIN, BOSS, MANAGER | 菜单根 |
| `commission:rule:list` | button | MANAGER, ADMIN, BOSS | ✅ 只读列表 |
| `commission:record:list` | button | MANAGER, ADMIN, BOSS | ✅ 只读列表 |
| `commission:salary:list` | button | MANAGER, ADMIN, BOSS | ✅ 只读列表 |
| `commission:settlement:list` | button | MANAGER, ADMIN, BOSS | ✅ 只读列表 |
| `commission:batch` | button | MANAGER, ADMIN, BOSS | 前端路由门（**类型错位**，见 I188） |
| `commission:reversal` | button | MANAGER, ADMIN, BOSS | 前端路由门（**类型错位**） |

**无绑定（24 个）**

| 权限码 | 类型 | 引用的写接口 |
|---|---|---|
| `commission:rule:add` | button | `POST /commission/rules` |
| `commission:rule:edit` | button | `PUT /commission/rules/{id}` |
| `commission:rule:delete` | button | `DELETE /commission/rules/{id}` |
| `commission:rule:detail` | API | `GET /commission/rules/{id}` |
| `commission:rule:stat` | API | `GET /commission/rules/stats` |
| `commission:rule:status` | API | `PUT /commission/rules/{id}/status` |
| `commission:calculate` | API | `POST /commission/calculate/order/{orderId}` |
| `commission:record:stat` | API | `GET /commission/records/stats` |
| `commission:salary:add` | API | `POST /commission/salary/generate` |
| `commission:salary:adjust` | API | `PUT /commission/salary/{id}/adjustment` |
| `commission:salary:confirm` | button | `POST /commission/salary/{id}/confirm` |
| `commission:salary:pay` | button | `POST /commission/salary/{id}/pay` |
| `commission:salary:stat` | API | `GET /commission/salary/stats` |
| `commission:salary:export` | API | `GET /commission/salary/export` |
| `commission:salary:batch-confirm` | API | `PUT /commission/salary/batch/confirm` |
| `commission:salary:batch-pay` | API | `PUT /commission/salary/batch/pay` |
| `commission:settlement:add` | API | `POST /commission/settlement/generate` |
| `commission:settlement:detail` | API | `GET /commission/settlement/{id}` |
| `commission:settlement:stat` | API | `GET /commission/settlement/stats` |
| `commission:settlement:confirm` | API | `PUT /commission/settlement/{id}/confirm` |
| `commission:settlement:pay` | API | `PUT /commission/settlement/{id}/pay` |
| `commission:settlement:batch-confirm` | API | `PUT /commission/settlement/batch/confirm` |
| `commission:settlement:batch-pay` | API | `PUT /commission/settlement/batch/pay` |
| `commission:settlement:export` | API | `GET /commission/settlement/export` |

> 另有 2 个**无接口引用**的死码：`commission:list`、`commission:settlement:audit`（种子声明要给 MANAGER/BOSS，见 I188）。

### 1.2 运行期实测（`raw/05`、`raw/05b`）

| 探测 | `admin` | `manager01` | `boss01` | `clerk01` |
|---|---|---|---|---|
| `GET /commission/rules/page` | 200 | **200** | **200** | **403** |
| `GET /commission/records/page` | 200 | **200** | **200** | **403** |
| `GET /commission/salary/page` | 200 | **200** | **200** | **403** |
| `GET /commission/settlement/page` | 200 | **200** | **200** | **403** |
| `GET /commission/rules/1`（`rule:detail`） | 200 | **403** | **403** | — |
| `GET /commission/rules/stats`（`rule:stat`） | 200 | **403** | **403** | — |
| `GET /commission/records/stats`（`record:stat`） | 200 | **403** | **403** | — |
| `GET /commission/salary/stats`（`salary:stat`） | 200 | **403** | **403** | — |
| `GET /commission/salary/export`（`salary:export`） | 200 | **403** | **403** | — |
| `GET /commission/settlement/export`（`settlement:export`） | 200 | **403** | **403** | — |
| `GET /commission/settlement/1`（`settlement:detail`） | 200（业务 404） | **403** | **403** | — |

403 响应体原文：`{"code":403,"message":"权限不足: 需要权限码：commission:rule:detail"}`

> ⚠️ **「列表能读、详情不能读」是最反直觉的一组现象**：`GET /commission/rules/page` 200，但点进某条规则的 `GET /commission/rules/1` 却 403 —— 前端列表页一旦支持「查看详情 / 编辑」跳转，会在**第二跳**失败。

### 1.3 写接口可用性（静态 × 运行期推导）

写接口**一律未调用**（只读纪律），可用性由「其 `@RequirePermission` 码是否被绑定」直接判定：

| 业务动作 | 接口 | 权限码 | 可用性 |
|---|---|---|---|
| 新增提成规则 | `POST /commission/rules` | `commission:rule:add` | ❌ 403 |
| 编辑提成规则 | `PUT /commission/rules/{id}` | `commission:rule:edit` | ❌ 403 |
| 删除提成规则 | `DELETE /commission/rules/{id}` | `commission:rule:delete` | ❌ 403 |
| 规则启停 | `PUT /commission/rules/{id}/status` | `commission:rule:status` | ❌ 403 |
| 按订单算提成 | `POST /commission/calculate/order/{orderId}` | `commission:calculate` | ❌ 403 |
| 生成工资 | `POST /commission/salary/generate` | `commission:salary:add` | ❌ 403 |
| 工资调整 | `PUT /commission/salary/{id}/adjustment` | `commission:salary:adjust` | ❌ 403 |
| 工资确认 | `POST /commission/salary/{id}/confirm` | `commission:salary:confirm` | ❌ 403 |
| 工资发放 | `POST /commission/salary/{id}/pay` | `commission:salary:pay` | ❌ 403 |
| 工资批量确认 / 发放 | `PUT /commission/salary/batch/confirm` / `batch/pay` | `commission:salary:batch-confirm` / `batch-pay` | ❌ 403 |
| 生成结算单 | `POST /commission/settlement/generate` | `commission:settlement:add` | ❌ 403 |
| 结算确认 / 发放 | `PUT /commission/settlement/{id}/confirm` / `pay` | `commission:settlement:confirm` / `pay` | ❌ 403 |
| 结算批量确认 / 发放 | `PUT /commission/settlement/batch/confirm` / `batch/pay` | `commission:settlement:batch-confirm` / `batch-pay` | ❌ 403 |

→ **14 个写接口全部不可用**。

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | **提成业务链路整体断裂** | 非超管执行任何提成写操作 | 规则配不了、工资算不出、发不了、结算做不了 → 提成模块**只有「看」没有「做」** |
| R2 | 「能看不能改」造成数据僵化 | 运营发现规则配错想改 | 只能超管改；运营期响应依赖超管账号 |
| R3 | **统计类接口全 403** | 提成/工资/结算看板 | `rule:stat` / `record:stat` / `salary:stat` / `settlement:stat` 无绑定 → 门店看不到提成概览 |
| R4 | 导出不可用 | 对账场景 | `salary:export` / `settlement:export` 无绑定 → 无法导出工资表；且导出还在 `DataScopeInterceptor` 白名单内（I186） |
| R5 | 静默空转 | 管理员看权限配置 | 28 个码都在 `sys_permission` 里（19 个 `permission_type='API'`），**看不出无人绑定** |
| R6 | 与前端叠加后**双重不可达** | 业务访问提成页面 | 前端路由用 `commission:rule` / `commission:rule:config` / `commission:simulation`（**库中不存在**）→ 页面先被守卫拦死；即使放行，后端也只有 4 个 list 码可用（见 I185） |

---

## 3. 建议方案（**仅建议，本批不实施**）

| # | 步骤 | 内容 | 备注 |
|---|---|---|---|
| 1 | **按职责补绑定** | 建议口径（**需业务确认**）：<br>· MANAGER / BOSS：`commission:rule:add/edit/delete/status/detail/stat`、`commission:record:stat`、`commission:salary:add/adjust/stat/export`、`commission:settlement:*` 全套<br>· 工资确认 / 发放、结算审核类是否只给 BOSS，需业务拍板（涉**资金发放**） | **资金发放权限不应默认给 MANAGER**，建议单独确认 |
| 2 | **「列表能读、详情 403」一并修** | 绑定时把 `rule:detail` / `settlement:detail` 与对应 list 码**同角色同批**绑定，避免前端详情页二次跳 403 | 复现路径：`GET /commission/rules/page` 200 → `GET /commission/rules/1` 403 |
| 3 | **种子脚本补齐** | `migrate_r3_0_c_menu_role.sql:133-137` 已声明要给 MANAGER/BOSS 绑定 `commission:list` / `rule:add/edit/delete` / `salary:confirm/pay` / `settlement:audit` / `batch` / `reversal`，**运行期库里这 7 个码仍全部无绑定**（详见 I188）→ 先查该脚本**是否在部署流程内执行**，再决定是补执行还是新写迁移 | 本批**未入库**任何脚本（规则 19） |
| 4 | **资金类写操作加二次确认 + 审计日志** | 工资发放 / 结算发放属资金动作，建议补 `@SysOpLog` 与金额级阈值审批 | 当前这批接口**没有**任何操作日志（`PermissionAspect` 与 `@SysOpLog` 相互独立，见 `PermissionAspect.java:19-20`） |
| 5 | **可观测性** | 权限后台增加「未被任何角色引用」的孤儿码提示 | — |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | `sys_role_permission` 中 `commission:*` 绑定统计 | 业务用码**每个** ≥ 1 绑定；无引用的 `commission:list` / `settlement:audit` 应删除或补上引用 |
| 2 | `manager01` `GET /commission/rules/1` | **200**（与 list 200 一致） |
| 3 | `manager01` `GET /commission/rules/stats` | **200** |
| 4 | `manager01` `GET /commission/salary/export?salaryMonth=2026-08` | **200**（导出需带 token，探测时注意响应为流） |
| 5 | `clerk01` `GET /commission/salary/page` | 仍 **403**（最小权限角色不应获得薪资数据） |
| 6 | 绑定后重测 `?storeId=5` | 仍 **403**「无权访问该门店数据」（补权限码**不得**顺带打开跨店读取，见 I186） |
| 7 | `admin` 全部 29 个 endpoint | 保持 **200** |

---

## 5. 边界声明

- 本 Issue 结论来自**静态代码分析** + **本机开发库只读查询** + **运行期只读 GET 探测**。
- **未修改任何业务代码**、**未执行任何写操作**、**未执行任何 DDL**、**未新增任何入库脚本**。
- **全部写接口一律未调用**；§1.3 的写接口可用性由权限码绑定关系**推导**，标注为运行期推导而非运行期实测。
- 未核实：生产环境 `sys_role_permission` 绑定情况；生产库 `commission_salary`（本机 **0 行**）/ `commission_settlement`（**0 行**）是否有真实数据。
- 未核实：业务侧真实的权限分配意图（尤其资金发放类）—— §3 步骤 1 的分配表为**建议**。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。

---

**下一步**：与 I183 合并为「权限码绑定」批次；在资金类权限分配口径（谁可确认/发放工资与结算）确认前，不宜直接按建议表批量绑定。
