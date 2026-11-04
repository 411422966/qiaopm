# I124 批次二调查报告 — 权限码前后端两套命名与批次二-D（报表+系统）修复基线

> 调查对象：前端路由守卫码 / 前端 API 调用 / 后端 `@RequirePermission` 注解码 / `sys_permission` 表 / `sys_role_permission` 绑定表
> 数据源：源码静态扫描 + 运行库 `meiyetec` 实查（`docker exec meiyetec-mysql`）
> 前置问题单：`audit/issues/I124-authz-code-system-split.md`（P0 BLOCKER，Open）
> 本文件定位：批次二调查 + 批次二-D 修复方案基线（不修改任何业务代码）

---

## 1. 一句话结论

前端路由守卫用的**冒号码**（`report:dashboard` / `sys:log:list`）与后端 `@RequirePermission` 用的**冒号码**在 DB 中**大量缺失或零绑定**，
而 `sys_role_permission` 的历史绑定几乎全部落在**点号码**（`report.dashboard` / `sys.log.list`）上。
两者不是"名字不同"，而是**两个互不相干的字符串**，直接改前端路由或后端注解都无法收敛，必须**同时补齐两套码的授权数据**。

批次二-D 选定 **报表（report）+ 系统（sys）** 两个域作为首个修复批次：
域边界清晰、权限面小、可零风险验证、且能立刻覆盖"运营看数 / 老板查日志"两条主业务链路。

---

## 2. 批次划分与本次范围（用户已拍板）

| 决策项 | 结论 |
|---|---|
| 首个批次 | **D = 报表 + 系统**，零风险起步，验证脚本模板 |
| `mh:visual` / `mh:consent` | 暂不纳入，留到最后确认（涉及用户"已验证通过页面"规则） |
| `market` / `mkt` | 保守做法：两套码都绑，不改前端代码 |

本报告覆盖批次二-D 的完整清单：涉及页面、守卫码、后端注解码、DB 码存在性、角色分配矩阵、修复脚本设计。

---

## 3. 调查方法与证据来源

| 手段 | 具体做法 | 覆盖 |
|---|---|---|
| 前端路由扫描 | 逐条提取 `frontend/meiyetec-web/src/router/index.ts` 的 `meta.permission` | 批次二-D 涉及 12 条路由 |
| 前端调用扫描 | 提取各页面 `reportApi.*` / `Api.*` / `http.get|post` 实际 URL | 报表 7 页 + 系统 7 页 |
| 后端注解扫描 | 提取 `backend/src/main/java/com/meiyetec/controller/**` 的 `@RequirePermission("...")` | 106 个 Controller（批次二-D 相关 9 个） |
| DB 实查 | `SELECT ... FROM sys_permission / sys_role_permission / sys_role` | 权限码存在性、绑定现状、角色清单 |
| 交叉比对 | 前端守卫码 × 后端注解码 × DB 存在性 × 角色绑定 | 四方对齐 |

**本报告所有结论均带文件路径 + 类名 + 方法名 + 路由 + 表名 + 行号**（规则 4）。

---

## 4. 核心发现 A：前后端两套命名（I124 主体，本批次再确认）

### 4.1 前端守卫码来源

```
frontend/meiyetec-web/src/router/index.ts
  :65  report/dashboard  → meta.permission = 'report:dashboard'
  :66  report/biz        → 'report:biz:list'
  :67  report/kpi        → 'report:dashboard'
  :68  report/export     → 'report:export:list'
  :70  sys/role          → 'sys:role:list'
  :72  sys/tenant        → 'sys:tenant:list'
  :73  sys/log           → 'sys:log:list'
  :74  sys/login-log     → 'sys:login-log:list'
  :75  sys/payment       → 'sys:config:edit'
  :76  sys/qualification → 'sys:config:edit'
  :100 report/task       → 'report:task'
  :101 report/caliber    → 'report:caliber'
```

### 4.2 守卫判定逻辑（前端）

```
frontend/meiyetec-web/src/stores/permission.ts:62-65
  hasPermission: (code) => permissions.includes(code) || permissions.includes('*')
```
**纯字符串精确匹配，无归一化、无点号/冒号互认** → 前端放行与否完全取决于 `permissions` 数组里有没有那个字符串。

```
frontend/meiyetec-web/src/router/index.ts:223-225
  if (need && !safePath && !store.hasPermission(need)) → next('/403')
```

### 4.3 后端权限判定逻辑

```
backend/src/main/java/com/meiyetec/security/TenantContext.java:68
  hasPermission(code) → permissions.contains(code)
```
同样是精确匹配。`permissions` 来源：

```
backend/src/main/java/com/meiyetec/mapper/SysPermissionMapper.java:12-18
  SELECT p.* FROM sys_permission p
  INNER JOIN sys_role_permission rp ON p.id = rp.permission_id
  INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id
  WHERE ur.user_id = ? AND p.deleted = 0
```
→ **后端可用权限码 ≡ 该用户角色在 `sys_role_permission` 里绑定的 `sys_permission.permission_code` 原文**。

### 4.4 DB 实查：两套码并存

`sys_permission` 中批次二-D 相关码**全部是冒号制且全部存在**：

| permission_code | id | permission_type |
|---|---|---|
| `report:dashboard` | 1131 | button |
| `report:biz:list` | 1132 | button |
| `report:caliber` | 1137 | button |
| `report:caliber:edit` | 1423 | button |
| `report:export:list` | 1135 | button |
| `report:export:add` | 1133 | button |
| `report:export:download` | 1134 | button |
| `report:task` | 1136 | button |
| `sys:role:list` | 1556 | button |
| `sys:role:add` | 1557 | button |
| `sys:role:edit` | 1558 | button |
| `sys:role:delete` | 1559 | button |
| `sys:permission:list` | 1363 | API |
| `sys:tenant:list` | 1156 | button |
| `sys:tenant:add` | 1157 | button |
| `sys:tenant:edit` | 1158 | button |
| `sys:tenant:delete` | 1159 | button |
| `sys:tenant:freeze` | 1160 | button |
| `sys:log:list` | 1161 | button |
| `sys:log:export` | 1162 | button |
| `sys:log:clear` | 1163 | button |
| `sys:login-log:list` | 1164 | button |
| `sys:login-log:export` | 1165 | button |
| `sys:login-log:clear` | 1166 | button |
| `sys:config:list` | 1154 | button |
| `sys:config:edit` | 1155 | button |
| `sys:dcep:list` | 1398 | button |
| `sys:dcep:view` | 1167 | button |
| `sys:dcep:edit` | 1168 | button |
| `sys:dcep:test` | 1169 | button |
| `dcep:qualification:list` | 1395 | button |
| `dcep:qualification:edit` | 1396 | button |
| `dcep:qualification:submit` | 1397 | button |

**结论修正**：批次二-D 涉及的冒号码在 `sys_permission` 表**并不缺**（缺码集中在 `crm:*` / `org:*` / `mkt:*` 等域）。
本批次的真实病灶是**零绑定**，不是缺码 —— 即 I124 §2.2 分类中的"DB 中存在但零角色绑定"一类。

### 4.5 DB 实查：零绑定（决定性）

批次二-D 的 33 个码与 4 个目标角色做交叉绑定查询，**返回 0 行**：

```sql
SELECT r.role_code, p.permission_code
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.permission_code IN ('report:dashboard', ... , 'dcep:qualification:submit');
-- → 0 rows
```

现状角色绑定总量（`sys_role_permission` 共 593 行）：

| role_code | 绑定行数 |
|---|---|
| ADMIN | 207 |
| BOSS | 69 |
| CLERK | 38 |
| MANAGER | 101 |
| OPERATOR | 28 |
| STORE_ADMIN | 34 |
| SUPER_ADMIN | 116 |

`sys_role` 全表 8 行，`tenant_id` 全为 `NULL`（平台级角色），`deleted=0`。

**故障链（本批次闭环）**：

```
sys_role_permission 中 report/sys 冒号码绑定数 = 0
        ↓
SysPermissionMapper.getPermissionsByUserId → permissions 集合中无 'report:dashboard'
        ↓
前端 hasPermission('report:dashboard') → false → /403
后端 TenantContext.hasPermission('report:dashboard') → false → 403
```

---

## 5. 核心发现 B：守卫码 ≠ 实际调用接口的注解码（三种契约漂移）

这是本批次修复中最容易漏掉的部分：**光绑前端守卫码，页面能打开，但页面内的写操作仍会被后端 403。**

### 5.1 漂移类型一：页面内多接口，各接口注解码不同

| 页面 | 前端守卫码 | 页面内实际调用 | 后端注解码 | 只绑守卫码的后果 |
|---|---|---|---|---|
| `/report/export` | `report:export:list` | `/report/export/list` | `report:export:list` | ✅ |
| | | `/report/export/create` | `report:export:add` | ❌ 新建导出 403 |
| | | `/report/export/download` | `report:export:download` | ❌ 下载 403 |
| `/sys/payment` | `sys:config:edit` | `/sys/dcep/current` | `sys:dcep:list` | ❌ 页面数据加载失败 |
| | | `/sys/dcep` | `sys:dcep:edit` | ❌ 保存 403 |
| | | `/sys/dcep/test` | `sys:dcep:test` | ❌ 连通测试 403 |
| | | `/sys/dcep/{id}/toggle` | `sys:dcep:edit` | ❌ 启停 403 |
| `/sys/qualification` | `sys:config:edit` | `/sys/dcep/qualification/current` | `dcep:qualification:list` | ❌ 页面数据加载失败 |
| | | `/sys/dcep/qualification` | `dcep:qualification:edit` | ❌ 保存 403 |
| | | `/sys/dcep/qualification/submit` | `dcep:qualification:submit` | ❌ 提交 403 |
| `/sys/role` | `sys:role:list` | `/sys/role/list` | `sys:role:list` | ✅ |
| | | `/sys/permission/list` | `sys:permission:list` | ❌ 权限树加载失败 |
| | | `/sys/role` POST | `sys:role:add` | ❌ 新建角色 403 |
| | | `/sys/role/{id}` PUT | `sys:role:edit` | ❌ 编辑 403 |
| | | `/sys/role/{id}` DELETE | `sys:role:delete` | ❌ 删除 403 |
| `/sys/tenant` | `sys:tenant:list` | `/sys/tenant/page`、`/{id}/stats`、`/{id}/config` | `sys:tenant:list` | ✅ |
| | | POST `/sys/tenant` | `sys:tenant:add` | ❌ |
| | | PUT `/sys/tenant` | `sys:tenant:edit` | ❌ |
| | | DELETE `/sys/tenant/{id}` | `sys:tenant:delete` | ❌ |
| | | PUT `/sys/tenant/{id}/status/{status}` | `sys:tenant:freeze` | ❌ |
| `/sys/log` | `sys:log:list` | `/page`、`/stats`、`/trend`、`/anomaly`、`/enums`、`/context`、`/search` | `sys:log:list` | ✅ |
| | | `/sys/log/export` | `sys:log:export` | ❌ 导出 403 |
| | | DELETE `/sys/log/{id}`、`/sys/log/clear` | `sys:log:clear` | ❌ 删除/清空 403 |
| `/sys/login-log` | `sys:login-log:list` | `/page`、`/stats`、`/trend`、`/anomaly`、`/search` | `sys:login-log:list` | ✅ |
| | | `/sys/login-log/export` | `sys:login-log:export` | ❌ |
| | | DELETE `/{id}`、POST `/clear` | `sys:login-log:clear` | ❌ |
| `/report/biz` | `report:biz:list` | `/report/biz/daily`、`/biz/monthly`、`/member/analysis`、`/employee/performance` | `report:biz:list` | ✅ |
| `/report/kpi` | `report:dashboard` | `/report/dashboard/kpi`、`/dashboard/consumables-variance`、`/dashboard/inventory-turnover-trend` | `report:dashboard` | ✅ |
| `/report/caliber` | `report:caliber` | `/report/config/list` | `report:caliber` | ⚠ 见 5.3 |

### 5.2 漂移类型二：后端接口完全没有权限注解（越权面）

`backend/src/main/java/com/meiyetec/service/ReportV2Service.java`（`@RestController @RequestMapping("/report/v2")`）
**全类 0 个 `@RequirePermission`**：

| 行号 | 路由 | 前端调用方 | 状态 |
|---|---|---|---|
| :94 | `GET /report/v2/dashboard` | `views/report/dashboard.vue` | ⚠ 无后端鉴权 |
| :288 | `POST /report/v2/task` | `views/report/dashboard.vue` | ⚠ 无后端鉴权 |
| :566 | `GET /report/v2/task/page` | `views/report/task.vue` | ⚠ 无后端鉴权 |
| :609 | `GET /report/v2/task/{id}` | `views/report/dashboard.vue` | ⚠ 无后端鉴权 |
| :616 | `POST /report/v2/task/{id}/cancel` | `views/report/task.vue` | ⚠ 无后端鉴权 |
| :630 | `GET /report/v2/task/{id}/download` | `views/report/task.vue`、`dashboard.vue` | ⚠ 无后端鉴权 |
| :657 | `POST /report/v2/snapshot/run` | `views/report/caliber.vue` | ⚠ 无后端鉴权 |
| :722 | `GET /report/v2/snapshot/list` | `views/report/caliber.vue` | ⚠ 无后端鉴权 |
| :739 | `GET /report/v2/caliber/list` | — | ⚠ 无后端鉴权 |
| :748 | `GET /report/v2/caliber/{key}/query` | — | ⚠ 无后端鉴权 |

**含义**：绑定 `report:task` / `report:dashboard` 只解决**前端路由守卫**；
一旦路由放行，**任何已登录用户都能直接调 `/report/v2/**` 拉取报表与导出文件**。
这是本批次发现的**独立安全缺陷**，建议单开 ISSUE 跟进（本次不改业务代码）。

### 5.3 漂移类型三：前端调用了后端不存在的路由（幽灵接口）

| 前端调用 | 前端文件 | 后端实际情况 | 后果 |
|---|---|---|---|
| `GET /report/caliber/list` | `views/report/caliber.vue` | 后端只有 `ReportController` 的 `/report/config/list`（`ReportController.java:807`，注解 `report:caliber`）与 `ReportV2Service` 的 `/report/v2/caliber/list`（:739） | **404** |
| `GET /report/snapshot/list` | `views/report/caliber.vue` | 只有 `/report/v2/snapshot/list`（:722） | **404** |
| `POST /report/snapshot/run` | `views/report/caliber.vue` | 只有 `/report/v2/snapshot/run`（:657） | **404** |
| `GET /report/caliber/{key}/query` | `views/report/caliber.vue` | 只有 `/report/v2/caliber/{key}/query`（:748） | **404** |
| `GET /sys/role/{id}/permissions` | `api/rbac.ts:39` | `RbacController` 只有 `/sys/role/list`(41)、`POST /sys/role`(47)、`PUT /sys/role/{id}`(53)、`DELETE /sys/role/{id}`(60) | **404** |
| `PUT /sys/role/{id}/permissions` | `api/rbac.ts:41` | 同上 | **404** |
| `GET /sys/log/session` | `api/sys.ts:141` | `SysLogController` 无 `/session` 端点 | **404** |

**含义**：即使权限全部绑对，`/report/caliber` 页面仍会白屏（4 个请求全 404），
`/sys/role` 页面无法读取/保存角色权限（2 个请求 404）。
**这是权限修复解决不了的问题**，必须单独处理。已列为待裁决项。

### 5.4 附带发现：死页面

`frontend/meiyetec-web/src/views/report/employee.vue` 调用 `reportApi.employeePerformance`（`/report/employee/performance`，
后端 `ReportController.java:726` 注解 `report:biz:list`），但 `router/index.ts` 中**没有 `report/employee` 路由** → 死页面。
同理 `views/report/employee.vue` 之外，`/report/caliber` 虽有路由但接口全 404。

---

## 6. 批次二-D 权限矩阵（最终方案）

### 6.1 报表域（report:*）

| 权限码 | 来源 | 前端守卫 | 后端注解 | ADMIN | BOSS | MANAGER | OPERATOR |
|---|---|---|---|:-:|:-:|:-:|:-:|
| `report:dashboard` | 前端+后端 | ✓ :65/:67 | ✓ `ReportController.java:71,419,567` | ✅ | ✅ | ✅ | ✅ |
| `report:biz:list` | 前端+后端 | ✓ :66 | ✓ `ReportController.java:329,641,691,726,769` | ✅ | ✅ | ✅ | ✅ |
| `report:export:list` | 前端+后端 | ✓ :68 | ✓ `ReportController.java:853` | ✅ | ✅ | ✅ | ✅ |
| `report:export:add` | **仅后端** | — | ✓ `ReportController.java:892` | ✅ | ✅ | ✅ | ✅ |
| `report:export:download` | **仅后端** | — | ✓ `ReportController.java:948` | ✅ | ✅ | ✅ | ✅ |
| `report:caliber` | 前端+后端 | ✓ :101 | ✓ `ReportController.java:808` | ✅ | ✅ | ✅ | ✅ |
| `report:caliber:edit` | **仅后端** | — | ✓ `ReportController.java:823,841` | ✅ | ✅ | ✅ | ✅ |
| `report:task` | **仅前端** | ✓ :100 | ✗（`/report/v2/task/**` 无注解，见 5.2） | ✅ | ✅ | ✅ | ✅ |

### 6.2 系统域（sys:*）

| 权限码 | 来源 | 前端守卫 | 后端注解 | ADMIN | BOSS |
|---|---|---|---|:-:|:-:|
| `sys:role:list` | 前端+后端 | ✓ :70 | ✓ `RbacController.java:42` | ✅ | ❌ |
| `sys:role:add` | 仅后端 | — | ✓ `RbacController.java:48` | ✅ | ❌ |
| `sys:role:edit` | 仅后端 | — | ✓ `RbacController.java:54` | ✅ | ❌ |
| `sys:role:delete` | 仅后端 | — | ✓ `RbacController.java:61` | ✅ | ❌ |
| `sys:permission:list` | 仅后端 | — | ✓ `SysPermissionController.java:53,65` | ✅ | ❌ |
| `sys:tenant:list` | 前端+后端 | ✓ :72 | ✓ `SysTenantController.java:30,45,52,103,111` | ✅ | ✅ |
| `sys:tenant:add` | 仅后端 | — | ✓ `SysTenantController.java:58` | ✅ | ✅ |
| `sys:tenant:edit` | 仅后端 | — | ✓ `SysTenantController.java:65,117` | ✅ | ✅ |
| `sys:tenant:delete` | 仅后端 | — | ✓ `SysTenantController.java:72` | ✅ | ❌ |
| `sys:tenant:freeze` | 仅后端 | — | ✓ `SysTenantController.java:79` | ✅ | ✅ |
| `sys:log:list` | 前端+后端 | ✓ :73 | ✓ `SysLogController.java:39,216,222,232,243,251,266` | ✅ | ✅ |
| `sys:log:export` | 仅后端 | — | ✓ `SysLogController.java:73` | ✅ | ✅ |
| `sys:log:clear` | 仅后端 | — | ✓ `SysLogController.java:192,203` | ✅ | ❌ |
| `sys:login-log:list` | 前端+后端 | ✓ :74 | ✓ `SysLoginLogController.java:31,45,51,57,64` | ✅ | ✅ |
| `sys:login-log:export` | 仅后端 | — | ✓ `SysLoginLogController.java:90` | ✅ | ✅ |
| `sys:login-log:clear` | 仅后端 | — | ✓ `SysLoginLogController.java:76,82` | ✅ | ❌ |
| `sys:config:list` | 仅后端 | — | ✓ `SysConfigController.java:24,33,41` | ✅ | ✅ |
| `sys:config:edit` | 前端+后端 | ✓ :75/:76 | ✓ `SysConfigController.java:47,54,61,68` | ✅ | ✅ |
| `sys:dcep:list` | 仅后端 | — | ✓ `DcepConfigController.java:69` | ✅ | ✅ |
| `sys:dcep:view` | 仅后端 | — | （无注解，仅占位码） | ✅ | ✅ |
| `sys:dcep:edit` | 仅后端 | — | ✓ `DcepConfigController.java:128,160,174` | ✅ | ✅ |
| `sys:dcep:test` | 仅后端 | — | ✓ `DcepConfigController.java:192` | ✅ | ✅ |
| `dcep:qualification:list` | 仅后端 | — | ✓ `DcepMerchantQualificationController.java:29` | ✅ | ✅ |
| `dcep:qualification:edit` | 仅后端 | — | ✓ `DcepMerchantQualificationController.java:79,92,143` | ✅ | ✅ |
| `dcep:qualification:submit` | 仅后端 | — | ✓ `DcepMerchantQualificationController.java:159` | ✅ | ✅ |

### 6.3 角色分配规则（用户拍板 + 保守细分）

| 域 | 规则 | 说明 |
|---|---|---|
| 报表 `report:*` | ADMIN、BOSS、MANAGER、OPERATOR | 运营看数是主业务链路，四角色全给 |
| 系统 `sys:*` | **仅 ADMIN 和 BOSS** | 用户拍板 |
| `sys:role:*` | **仅 ADMIN** | 用户拍板"sys:role:list 仅给 ADMIN"，其余角色管理码一并收敛到 ADMIN |
| 破坏性码 | **仅 ADMIN** | `sys:tenant:delete` / `sys:log:clear` / `sys:login-log:clear` / `sys:role:delete` — BOSS 为只读 |
| SUPER_ADMIN | 不绑定 | 持通配 `*`（`permission.ts:64`） |
| CLERK / STORE_ADMIN | 不绑定 | 本批次不涉及其业务面 |

**新增绑定行数**：ADMIN 33（25 系统 + 8 报表）、BOSS 25（17 系统 + 8 报表）、MANAGER 8、OPERATOR 8，**合计 74 行**。

---

## 7. 修复脚本设计（三件套）

| 文件 | 作用 | 幂等性 |
|---|---|---|
| `fix-batch2d.sql` | 补 `sys_role_permission` 绑定 | `INSERT ... SELECT ... WHERE NOT EXISTS`（含派生表包装，规避 MySQL 1093 目标表限制） |
| `fix-batch2d-verify.sql` | 查当前绑定现状 + 修复后计数核对 | 只读 |
| `fix-batch2d-rollback.sql` | 精确删除本批新增绑定 | 按 `(role_code, permission_code)` 精确匹配删除 |

### 7.1 设计约束

1. **不写 `sys_permission`**：本批 33 个码在 DB 中**全部已存在**（§4.4），无需新建。
2. **不写 `tenant_id`**：`sys_role_permission` 实表结构为
   `id / role_id / permission_id / create_time`（**无 tenant_id 列**），
   唯一键 `uk_role_permission (role_id, permission_id)`。
   注：实体 `backend/src/main/java/com/meiyetec/entity/SysRolePermission.java:25` 声明了 `tenantId` 字段但表中无对应列 →
   **任何经 MyBatis-Plus 实体写入本表的代码都会因未知列而失败**（历史绑定只能靠裸 SQL 灌入）。
   本脚本一律使用裸 SQL，只写 `role_id / permission_id`。
3. **`WHERE NOT EXISTS` 幂等**：脚本可重复执行，重复执行影响 0 行。
4. **不删历史点号绑定**：保留 `report.dashboard` 等点号绑定，避免影响后端菜单树
   （`RbacService.buildMenuTree` 走 `sys_menu_permission`，不依赖本表，但菜单可见性仍按点号码下发）。
5. **不含事务控制**：`START TRANSACTION` / `COMMIT` 由执行命令控制，与批次一脚本风格一致。

### 7.2 执行前置检查（fix 脚本执行前必跑）

- `sys_permission` 中 33 个码全部存在（已在 §4.4 实查确认，脚本内仍保留断言式查询）
- `sys_role` 中 ADMIN/BOSS/MANAGER/OPERATOR 四行 `deleted=0`（已实查确认）
- 全库备份已完成
- 执行账号具备 `INSERT`/`DELETE` 权限

---

## 8. 遗留风险与待裁决项

| 编号 | 事项 | 级别 | 说明 | 建议 |
|---|---|---|---|---|
| R1 | `/report/v2/**` 全类无 `@RequirePermission` | 高 | 任何登录用户可拉取报表与导出文件（含 `download`） | 单开 ISSUE，补注解；本批不改业务代码 |
| R2 | `/report/caliber` 4 个接口 404 | 高 | 前端缺 `/v2` 前缀 | 单开 ISSUE，修前端或后端路由（二选一，需用户裁决） |
| R3 | `/sys/role/{id}/permissions` GET/PUT 404 | 高 | 角色管理页无法读/写权限分配 | 单开 ISSUE，补后端端点或前端改走 `SysPermissionController` |
| R4 | `/sys/log/session` 404 | 中 | 操作日志"同会话"视图不可用 | 单开 ISSUE |
| R5 | `SysRolePermission.tenantId` 字段与表结构不符 | 中 | MyBatis-Plus 实体写入必失败 | 补 `tenant_id` 列 或 标 `@TableField(exist=false)` |
| R6 | `sys:config:edit` 被同时用作 `/sys/payment`、`/sys/qualification` 两个页面的守卫码 | 中 | 语义漂移（配置页 ≠ 支付配置页） | 后续拆分为独立码 |
| R7 | `report:task` 只有前端守卫码，无后端注解 | 中 | 见 R1 | 同 R1 |
| R8 | BOSS 拿到 `sys:dcep:edit` / `dcep:qualification:edit` 等写码 | 低 | 老板可改支付配置/提交资质 | 如需收紧，从脚本对应段移除（已单列成段，便于调整） |
| R9 | `views/report/employee.vue` 无路由 | 低 | 死页面 | 后续处理 |

---

## 9. 未确认项（规则 5：禁止猜测）

1. **批次一（`docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/batch1-fix.sql`）是否已应用到当前 DB** — 未确认。
   该脚本预期 ADMIN 93 码，但当前 ADMIN 实测 207 行绑定，**数量对不上** → 极可能已应用后又叠加了其他批次，也可能应用了其他脚本。
   本批脚本用 `WHERE NOT EXISTS` 幂等，不依赖批次一状态。
2. **运行容器镜像是否与当前源码一致** — 未确认。I124 §2.6 记录 `meiyetec/api:dev` 构建于 2026-10-20、源码 HEAD 为 2026-10-23，
   容器内 143 个 Controller 仅 14 个含 `@RequirePermission`。**本报告的后端注解码清单基于源码扫描，不代表运行镜像现状**。
   修复后需**重建镜像并实测**才能升为 VERIFIED。
3. **`sys:role:list` 之外是否还有别处引用裸码 `setting`** — `router/index.ts:71` 的 `/sys/setting` 守卫码为裸码 `setting`，
   DB 中存在（`sys_permission` 有 `setting`），但 `/sys/setting` 页面不在批次二-D 范围（用户清单未含），本批不处理。
4. **BOSS 是否需要 `sys:tenant:add` / `sys:tenant:edit` / `sys:tenant:freeze`** — 用户只说"系统类仅给 ADMIN 和 BOSS"，
   未细分读写。本报告按"破坏性码仅 ADMIN、其余系统码 ADMIN+BOSS"落地，属**保守推断**，如与业务预期不符请指出。
5. **`sys:dcep:view`（id=1167）在后端无任何 `@RequirePermission` 引用** — 占位码，本批仍绑定，无副作用。

---

## 10. 证据与引用清单

| 类型 | 路径 / 位置 |
|---|---|
| 前置问题单 | `audit/issues/I124-authz-code-system-split.md` |
| 批次一脚本（模板参考） | `docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/batch1-fix.sql` |
| 批次一校验脚本 | `docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/batch1-verify.sql` |
| 批次一回滚脚本 | `docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/batch1-rollback.sql` |
| I124 原始证据 | `audit/evidence/ACC002-authz-code-split/`（browser-deadlinks / authz-cross / backend-authz-impact / api-authz / bundle-vs-src） |
| 前端路由 | `frontend/meiyetec-web/src/router/index.ts` |
| 前端权限 store | `frontend/meiyetec-web/src/stores/permission.ts` |
| 前端 API | `frontend/meiyetec-web/src/api/report.ts`、`sys.ts`、`rbac.ts` |
| 后端权限切面数据源 | `backend/src/main/java/com/meiyetec/mapper/SysPermissionMapper.java` |
| 后端权限上下文 | `backend/src/main/java/com/meiyetec/security/TenantContext.java` |
| 后端控制器 | `ReportController` / `ReportV2Service` / `SysLogController` / `SysLoginLogController` / `SysTenantController` / `SysConfigController` / `RbacController` / `SysPermissionController` / `DcepConfigController` / `DcepMerchantQualificationController` |
| 后端实体 | `backend/src/main/java/com/meiyetec/entity/SysRolePermission.java` |
| DB 表 | `sys_permission` / `sys_role` / `sys_role_permission` / `sys_menu_permission` / `sys_user_role` |

**绑定基线**：DB `meiyetec@localhost:3307`（容器 `meiyetec-mysql`，`mysql:8.0`，实查时刻 2026-11-04 21:4x）
/ 源码 HEAD `D:/QiaoPM`（非 git 仓库，本地工作副本）/ 运行产物 `meiyetec/api:dev`、`meiyetec/web:dev`。

---

## 11. 变更声明

本文件为**新增调查基线**，不含任何业务代码修改，不含 `sys_permission` 数据变更。
后续三个 SQL 脚本在本批次获准执行前**仅以草案形式存在于对话中**。
