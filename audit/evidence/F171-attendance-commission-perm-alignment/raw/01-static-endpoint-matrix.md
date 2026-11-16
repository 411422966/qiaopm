# F171 — 静态梳理：endpoint × 权限码 × 角色绑定 对齐矩阵

> **性质**：静态代码分析（只读），基线 HEAD `56d59a2`，调查日期 2026-11-16
> **来源文件**：
> - `backend/src/main/java/com/meiyetec/controller/AttendanceController.java`
> - `backend/src/main/java/com/meiyetec/controller/AttendanceConfigController.java`
> - `backend/src/main/java/com/meiyetec/controller/LeaveRequestController.java`
> - `backend/src/main/java/com/meiyetec/controller/CommissionController.java`
> - `backend/src/main/java/com/meiyetec/controller/CommissionTierController.java`（门控）
> - `backend/src/main/java/com/meiyetec/controller/CommissionConditionController.java`（门控）
> - `backend/src/main/java/com/meiyetec/controller/CommissionSimulationController.java`（门控）
> - `backend/src/main/java/com/meiyetec/security/PermissionAspect.java`
> - `backend/src/main/java/com/meiyetec/security/PermissionAutoRegistrar.java`
> - `backend/src/main/java/com/meiyetec/security/RbacContextLoader.java`
> - `backend/src/main/java/com/meiyetec/security/DataScopeInterceptor.java`
> - `backend/src/main/java/com/meiyetec/mapper/SysPermissionMapper.java:13-18`

## 0. 判定口径

| 机制 | 事实 | 位置 |
|---|---|---|
| 鉴权切面 | `@RequirePermission` 精确码匹配；集合含 `*` 直接放行；否则抛 `BizCode.PERMISSION_DENIED` | `PermissionAspect.java:29-66` |
| 超管通配 | `codes` 含 `booking`/`member`/`setting` **或** `username == "admin"` **且** 角色含 `SUPER_ADMIN`/`PLATFORM_ADMIN` → 追加 `*` | `RbacContextLoader.java:78-90` |
| 自动注册 | 启动期扫描 `@RequirePermission` → 缺失码 INSERT `sys_permission`（`permission_type='API'`），**不绑定任何角色** | `PermissionAutoRegistrar.java:97-110` |
| 权限来源 | `sys_permission ⋈ sys_role_permission ⋈ sys_user_role`，**不按 `permission_type` 过滤** → menu/button/API 三类码都会下发给前端 | `SysPermissionMapper.java:13-18` |
| 数据范围 | `DataScopeInterceptor` 仅在**请求显式携带** `storeId`/`tenantId`（header/query/form/body）时校验；缺省不校验；`employeeId` 完全不校验 | `DataScopeInterceptor.java:52-74` |
| 导出白名单 | `/commission/salary/export`、`/commission/settlement/export` 在 `WHITELIST` 内 → **跳过数据范围校验** | `DataScopeInterceptor.java:32-42` |

---

## 1. 考勤模块 — endpoint × 权限码 × 绑定角色

> 列说明：**绑定角色**取自 `sys_role_permission` 实测（`raw/04b`）；`—` = 该码**无任何角色绑定**（死权限码）。

| # | 方法 | 路由 | 权限码 | sys_permission 类型 | 绑定角色 | 运行期（实测） |
|---|---|---|---|---|---|---|
| A1 | POST | `/attendance/check-in` | `attendance:check` | button | — | admin 200 / 其余 403 |
| A2 | POST | `/attendance/check-out` | `attendance:check` | button | — | 未单独探测（同码） |
| A3 | GET | `/attendance/today` | `attendance:list` | button | — | admin 200 / manager 403 / boss 403 / clerk 403 |
| A4 | POST | `/attendance/page` | `attendance:list` | button | — | 未单独探测（同码） |
| A5 | GET | `/attendance/records` | `attendance:list` | button | — | admin 200 / manager 403 |
| A6 | GET | `/attendance/statistics` | `attendance:stat` | button | — | admin 200 / manager 403 |
| A7 | GET | `/attendance/monthly-report` | `attendance:stat` | button | — | admin 200 / manager 403 |
| A8 | GET | `/attendance/{id}` | `attendance:list` | button | — | 未单独探测（同码） |
| A9 | PUT | `/attendance/{id}` | `attendance:edit` | button | — | 未单独探测（同码，写接口） |
| A10 | GET | `/attendance/config/active` | `attendance:config:list` | button | — | admin 200 |
| A11 | GET | `/attendance/config/list` | `attendance:config:list` | button | — | admin 200 / manager 403 |
| A12 | GET | `/attendance/config/{id}` | `attendance:config:list` | button | — | 未单独探测（同码） |
| A13 | POST | `/attendance/config` | `attendance:config:edit` | button | — | 未单独探测（写接口） |
| A14 | PUT | `/attendance/config/{id}` | `attendance:config:edit` | button | — | 未单独探测（写接口） |
| A15 | DELETE | `/attendance/config/{id}` | `attendance:config:edit` | button | — | 未单独探测（写接口） |
| A16 | POST | `/attendance/leave` | `attendance:leave:submit` | button | — | 未单独探测（写接口） |
| A17 | PUT | `/attendance/leave/{id}/approve` | `attendance:leave:approve` | button | — | 未单独探测（写接口） |
| A18 | PUT | `/attendance/leave/{id}/cancel` | **`attendance:leave:approve`** | button | — | 未单独探测（写接口，**权限码口径可疑**，见 I189） |
| A19 | GET | `/attendance/leave/list` | `attendance:leave:list` | button | — | admin 200 / manager 403 |
| A20 | GET | `/attendance/leave/my` | `attendance:leave:list` | button | — | admin 200 |
| A21 | GET | `/attendance/leave/pending-count` | `attendance:leave:list` | button | — | admin 200 / manager 403 |
| A22 | GET | `/attendance/leave/{id}` | `attendance:leave:list` | button | — | 未单独探测（同码） |

**考勤小结**：22 个 endpoint、9 个权限码，**9/9 无任何角色绑定** → 除持 `*` 的超管外，**全部 22 个接口对所有角色 403**。
所有 9 个码的 `create_time` 均为 `2026-10-21 22:33:46`（`raw/02`），与 `PermissionAutoRegistrar` 的自动写入特征一致，**不是种子 SQL 产物**。

---

## 2. 员工提成模块 — endpoint × 权限码 × 绑定角色

| # | 方法 | 路由 | 权限码 | sys_permission 类型 | 绑定角色 | 运行期（实测） |
|---|---|---|---|---|---|---|
| C1 | GET | `/commission/rules/page` | `commission:rule:list` | button | **MANAGER / BOSS / ADMIN** | admin 200 / manager 200 / boss 200 / clerk 403 |
| C2 | GET | `/commission/rules/list` | `commission:rule:list` | button | 同上 | admin 200 / manager 200 |
| C3 | GET | `/commission/rules/{id}` | `commission:rule:detail` | API | — | admin 200 / manager 403 / boss 403 |
| C4 | POST | `/commission/rules` | `commission:rule:add` | button | — | 未单独探测（写接口） |
| C5 | PUT | `/commission/rules/{id}` | `commission:rule:edit` | button | — | 未单独探测（写接口） |
| C6 | PUT | `/commission/rules/{id}/status` | `commission:rule:status` | API | — | 未单独探测（写接口） |
| C7 | DELETE | `/commission/rules/{id}` | `commission:rule:delete` | button | — | 未单独探测（写接口） |
| C8 | GET | `/commission/rules/stats` | `commission:rule:stat` | API | — | admin 200 / manager 403 / boss 403 |
| C9 | POST | `/commission/calculate/order/{orderId}` | `commission:calculate` | API | — | 未单独探测（写接口） |
| C10 | GET | `/commission/records/page` | `commission:record:list` | button | **MANAGER / BOSS / ADMIN** | admin 200 / manager 200 / boss 200 / clerk 403 |
| C11 | GET | `/commission/records/stats` | `commission:record:stat` | API | — | admin 200 / manager 403 / boss 403 |
| C12 | POST | `/commission/salary/generate` | `commission:salary:add` | API | — | 未单独探测（写接口） |
| C13 | GET | `/commission/salary/page` | `commission:salary:list` | button | **MANAGER / BOSS / ADMIN** | admin 200 / manager 200 / boss 200 |
| C14 | PUT | `/commission/salary/{id}/adjustment` | `commission:salary:adjust` | API | — | 未单独探测（写接口） |
| C15 | POST | `/commission/salary/{id}/confirm` | `commission:salary:confirm` | button | — | 未单独探测（写接口） |
| C16 | POST | `/commission/salary/{id}/pay` | `commission:salary:pay` | button | — | 未单独探测（写接口） |
| C17 | GET | `/commission/salary/stats` | `commission:salary:stat` | API | — | admin 200 / manager 403 / boss 403 |
| C18 | GET | `/commission/salary/export` | `commission:salary:export` | API | — | manager 403 / boss 403 |
| C19 | PUT | `/commission/salary/batch/confirm` | `commission:salary:batch-confirm` | API | — | 未单独探测（写接口） |
| C20 | PUT | `/commission/salary/batch/pay` | `commission:salary:batch-pay` | API | — | 未单独探测（写接口） |
| C21 | GET | `/commission/settlement/stats` | `commission:settlement:stat` | API | — | admin 200 / manager 403 / boss 403 |
| C22 | POST | `/commission/settlement/generate` | `commission:settlement:add` | API | — | 未单独探测（写接口） |
| C23 | GET | `/commission/settlement/page` | `commission:settlement:list` | button | **MANAGER / BOSS / ADMIN** | admin 200 / manager 200 / boss 200 |
| C24 | GET | `/commission/settlement/{id}` | `commission:settlement:detail` | API | — | admin 200（业务 404）/ manager 403 |
| C25 | PUT | `/commission/settlement/{id}/confirm` | `commission:settlement:confirm` | API | — | 未单独探测（写接口） |
| C26 | PUT | `/commission/settlement/{id}/pay` | `commission:settlement:pay` | API | — | 未单独探测（写接口） |
| C27 | PUT | `/commission/settlement/batch/confirm` | `commission:settlement:batch-confirm` | API | — | 未单独探测（写接口） |
| C28 | PUT | `/commission/settlement/batch/pay` | `commission:settlement:batch-pay` | API | — | 未单独探测（写接口） |
| C29 | GET | `/commission/settlement/export` | `commission:settlement:export` | API | — | manager 403 / boss 403 |

**提成小结**：29 个 endpoint、28 个权限码，其中 **24 个无任何角色绑定**（含**全部 14 个写接口**的权限码）。
仅 4 个只读码有绑定：`commission:rule:list` / `commission:record:list` / `commission:salary:list` / `commission:settlement:list`。

---

## 3. 门控控制器 — 无 `@RequirePermission` 的 8 个 endpoint

> 三个控制器均带 `@ConditionalOnProperty(name = "feature.legacy-missing-ddl.enabled", havingValue = "true", matchIfMissing = false)`；
> `backend/src/main/resources/application.yml:151-152` 设为 `false` → **运行期 Bean 未注册 → 全部 404**（`raw/07` / `raw/06b`）。

| # | 方法 | 路由 | 权限码 | 类型 | 运行期 |
|---|---|---|---|---|---|
| G1 | GET | `/commission/tier/by-rule/{ruleId}` | **无** | 读 | 404（admin / manager / boss / clerk 一致） |
| G2 | POST | `/commission/tier/batch/{ruleId}` | **无** | **写** | 404 |
| G3 | DELETE | `/commission/tier/{id}` | **无** | **写** | 404 |
| G4 | GET | `/commission/condition/by-rule/{ruleId}` | **无** | 读 | 404 |
| G5 | POST | `/commission/condition/batch/{ruleId}` | **无** | **写** | 404 |
| G6 | DELETE | `/commission/condition/{id}` | **无** | **写** | 404 |
| G7 | POST | `/commission/simulation` | **无** | 纯计算 | 404 |
| G8 | GET | `/commission/simulation/recent/{employeeId}` | **无** | 读 | 404 |

> ⚠️ **潜伏风险**：一旦 `feature.legacy-missing-ddl.enabled` 被置为 `true`（I135 明确警告过「不要整体改 true」），
> 上述 8 个 endpoint 会**同时上线且不带任何鉴权**，其中 4 个是写接口 → 见 I187。

---

## 4. 权限码 × 角色绑定 总表（运行期实测）

| 权限码 | 类型 | 绑定角色数 | 绑定角色 | 判定 |
|---|---|---|---|---|
| `attendance:check` | button | 0 | — | 🔴 死码 |
| `attendance:list` | button | 0 | — | 🔴 死码 |
| `attendance:stat` | button | 0 | — | 🔴 死码 |
| `attendance:edit` | button | 0 | — | 🔴 死码 |
| `attendance:config:list` | button | 0 | — | 🔴 死码 |
| `attendance:config:edit` | button | 0 | — | 🔴 死码 |
| `attendance:leave:submit` | button | 0 | — | 🔴 死码 |
| `attendance:leave:approve` | button | 0 | — | 🔴 死码 |
| `attendance:leave:list` | button | 0 | — | 🔴 死码 |
| `commission:rule:list` | button | 3 | MANAGER, BOSS, ADMIN | ✅ 可用 |
| `commission:record:list` | button | 3 | MANAGER, BOSS, ADMIN | ✅ 可用 |
| `commission:salary:list` | button | 3 | MANAGER, BOSS, ADMIN | ✅ 可用 |
| `commission:settlement:list` | button | 3 | MANAGER, BOSS, ADMIN | ✅ 可用 |
| `commission:rule:add` | button | 0 | — | 🔴 死码（写） |
| `commission:rule:edit` | button | 0 | — | 🔴 死码（写） |
| `commission:rule:delete` | button | 0 | — | 🔴 死码（写） |
| `commission:salary:confirm` | button | 0 | — | 🔴 死码（写） |
| `commission:salary:pay` | button | 0 | — | 🔴 死码（写） |
| `commission:list` | button | 0 | — | 🟡 死码（**无接口引用**） |
| `commission:settlement:audit` | button | 0 | — | 🟡 死码（**无接口引用**） |
| `commission:rule:detail` | API | 0 | — | 🔴 死码 |
| `commission:rule:stat` | API | 0 | — | 🔴 死码 |
| `commission:rule:status` | API | 0 | — | 🔴 死码（写） |
| `commission:calculate` | API | 0 | — | 🔴 死码（写） |
| `commission:record:stat` | API | 0 | — | 🔴 死码 |
| `commission:salary:add` | API | 0 | — | 🔴 死码（写） |
| `commission:salary:adjust` | API | 0 | — | 🔴 死码（写） |
| `commission:salary:stat` | API | 0 | — | 🔴 死码 |
| `commission:salary:export` | API | 0 | — | 🔴 死码 |
| `commission:salary:batch-confirm` | API | 0 | — | 🔴 死码（写） |
| `commission:salary:batch-pay` | API | 0 | — | 🔴 死码（写） |
| `commission:settlement:stat` | API | 0 | — | 🔴 死码 |
| `commission:settlement:add` | API | 0 | — | 🔴 死码（写） |
| `commission:settlement:detail` | API | 0 | — | 🔴 死码 |
| `commission:settlement:confirm` | API | 0 | — | 🔴 死码（写） |
| `commission:settlement:pay` | API | 0 | — | 🔴 死码（写） |
| `commission:settlement:batch-confirm` | API | 0 | — | 🔴 死码（写） |
| `commission:settlement:batch-pay` | API | 0 | — | 🔴 死码（写） |
| `commission:settlement:export` | API | 0 | — | 🔴 死码 |
| `commission` | menu | 4 | SUPER_ADMIN, ADMIN, BOSS, MANAGER | ✅ 菜单根 |
| `commission:batch` | button | 3 | MANAGER, BOSS, ADMIN | ✅ 前端路由门（类型错位：button 当菜单用） |
| `commission:reversal` | button | 3 | MANAGER, BOSS, ADMIN | ✅ 前端路由门（类型错位） |

**统计**：后端实际引用的 37 个权限码 → **33 个死码（89%）**、4 个可用。

---

## 5. 前后端权限码同源性

| 来源 | 码空间 | 与 `sys_permission` 的关系 |
|---|---|---|
| 前端路由 `meta.permission`（`router/index.ts`） | `employee:attendance*`、`employee:leave`、`commission:rule`、`commission:rule:config`、`commission:simulation`、`commission:batch`、`commission:reversal` | 前 5 类 **8 个码在 `sys_permission` 中完全不存在**（`raw/04b` V1 无对应行）→ 前端守卫 `hasPermission()` 恒为 false |
| 前端按钮 `v-permission` | **全仓 0 处使用**（`src/directives/permission.ts` 已定义但无调用方；`views/attendance/*.vue`、`views/commission/*.vue` 均无 `v-permission`） | — |
| 后端 `@RequirePermission` | 37 个码 | 全部存在于 `sys_permission`（自动注册补齐） |
| 结论 | 前端 `employee:attendance*` 与后端 `attendance:*` **是两套互不相通的命名**，前端 `employee:*` 系全部未入库；前端 `commission:rule` 系与后端 `commission:rule:list` **不同码** | 契约漂移（详见 `raw/09`、I185） |
