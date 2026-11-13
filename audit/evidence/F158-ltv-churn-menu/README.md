# F158 — `/ltv-churn` 菜单入口补齐（dev.ltv-churn）

> **Feature ID**：F158
> **证据日期**：2026-11-13（UTC+8）
> **核实 commit（修复前 HEAD）**：**`1dd9a0a`** `docs(audit): 登记 I159/I160 + F157 证据`
> **修复 commit**：见 `git log --oneline` 中 `feat(menu): 补 /ltv-churn 菜单入口（dev.ltv-churn）`
> **环境**：`audit/evidence/env.md`（Phase 5A Runtime Environment）
> **DB 快照**：本目录 `raw/07-db-snapshot-menu.txt`
> **关联 Issue**：I161（同批新发现，不本轮修）；连带 I159 / I160（`/dev/*` 静默失败）
> **最终状态**：**PARTIAL**（API 层 L5b 已验证 4 个账号；浏览器层仅 admin 有截图，其余 3 个账号为 API 级）

---

## 1. 缺陷

前端路由 `/ltv-churn` 已存在，但**后端动态菜单表里没有对应记录**，导致侧边栏无入口。

### 1.1 前端已有的东西

| 位置 | 内容 |
|---|---|
| `frontend/meiyetec-web/src/router/index.ts:164` | `{ path: 'ltv-churn', name: 'LtvChurn', component: () => import('@/views/ltv-churn/index.vue'), meta: { title: 'LTV/Churn 分析', requiresAuth: true, permission: 'crm:tag' } }` |
| `frontend/meiyetec-web/src/components/Layout.vue:297` | `fallbackMenus` 中已硬编码 `/ltv-churn` 入口（icon `DataLine`） |

### 1.2 缺失的（后端菜单表）

| 表 | 缺失内容 |
|---|---|
| `sys_permission` | 无 `permission_code = 'dev.ltv-churn'` 行 → 菜单树推不出该节点 |
| `sys_menu_permission` | 无 `route_path = '/ltv-churn'` 的映射行 |

### 1.3 为什么 fallbackMenus 救不了

`Layout.vue:328-334` 的逻辑：**动态菜单一旦非空就整体覆盖 `fallbackMenus`**。
本项目动态菜单非空（`dev` 组下有 16 个子菜单），因此 `fallbackMenus` 里的 `/ltv-churn` **永远不显示**。

### 1.4 守卫码不是问题

前端路由守卫码是 `crm:tag`（`sys_permission.id = 1591`，batch2-A 已绑 MANAGER / BOSS / ADMIN）。
本次决策：**守卫码不动**，只补菜单码。

---

## 2. 修复

### 2.1 迁移脚本

`backend/src/main/resources/db/sql/add_menu_dev_ltv_churn_20261113.sql`（幂等，可重复执行）

| 段 | 内容 |
|---|---|
| A | `sys_permission` 新增 `dev.ltv-churn` / 'LTV/Churn 分析' / `menu` / `parent_id=1210` / `path='/ltv-churn'` / `icon='DataLine'` / `sort_order=12` |
| B | `sys_menu_permission` 新增 `permission_id=1624` / `route_path='/ltv-churn'` / `icon='DataLine'` / `hidden=0` / `keep_alive=1` / `sort_order=12` |
| C | `sys_role_permission` 绑 `dev.ltv-churn` → SUPER_ADMIN / ADMIN / MANAGER / BOSS |
| D | **补绑父级 `dev`(1210) → MANAGER / BOSS**（菜单树按 `parent_id` 拼装，父节点不可见则子节点被丢弃） |
| E | 5 段验证查询 |
| 尾部 | ROLLBACK 段（手动执行） |

**`sort_order = 12` 的取值依据**：紧接 `dev.care-plan`(11) 之后，排在 `dev:intl-payment`(101) 等扩展菜单之前 —— 与 `sys_menu_permission` 实际排序一致。

**icon = `DataLine` 的取值依据**：`Layout.vue:297` `fallbackMenus` 中 `/ltv-churn` 的图标。

### 2.2 为什么绑 MANAGER / BOSS（而不是只对齐 dev.ltv-training）

`dev.ltv-training`(1279) 现绑 **SUPER_ADMIN + ADMIN**；`crm:tag`(1591) 现绑 **MANAGER + BOSS + ADMIN**。

- 若**只**绑 `dev.ltv-churn` 给 SUPER_ADMIN / ADMIN：MANAGER / BOSS 有守卫码能进页面，却看不到菜单 → 入口不可发现。
- 若**只**对齐 `dev.ltv-training`：MANAGER / BOSS 侧边栏完全没有 LTV 相关入口，与 `crm:tag` 已绑定的意图矛盾。
- 本次取**并集**：菜单可见性 = 有 `crm:tag`（能进页面）∪ 有 `dev.ltv-training`（同类研发工具入口）。
- **不绑 OPERATOR**(role 7)：它既无 `crm:tag` 也无 `dev.ltv-training`，绑了会出现「菜单可见但点击跳 403」的坏体验。
- 已确认后端**无任何** `@RequirePermission("dev")` / `'dev'` 系列校验（静态检索），故 D 段补绑父级不会额外开放后端接口。

### 2.3 连带前端修复（同一批）

后端 `/ltv-churn/dashboard` 返回字段是 `medRiskCount`，前端读的是 `mediumRiskCount` → 「中流失风险」恒显示 0。

| 文件 | 改动 |
|---|---|
| `frontend/meiyetec-web/src/views/ltv-churn/index.vue:37` | `dashboard.mediumRiskCount` → `dashboard.medRiskCount` |
| `frontend/meiyetec-web/src/views/dev/P2Suite.vue:93` | `ltvDash.mediumRiskCount` → `ltvDash.medRiskCount` |

---

## 3. 验证

### 3.1 菜单树（4 个账号，API 级 L5b）

原始输出：`raw/03-after-menu-tree-4-accounts.txt`

| 账号 | 角色 | dev 组可见 | dev.ltv-churn | 有 crm:tag | 结果 |
|---|---|---|---|---|---|
| admin | SUPER_ADMIN, ADMIN | ✅ | ✅ VISIBLE path=/ltv-churn title=LTV/Churn 分析 icon=DataLine sort=12 | ✅ | ✅ 通过 |
| boss01 | BOSS | ✅ | ✅ VISIBLE | ✅ | ✅ 通过 |
| manager01 | MANAGER | ✅ | ✅ VISIBLE | ✅ | ✅ 通过 |
| operator01 | OPERATOR | ❌ 无 dev 组 | ❌ NOT FOUND | ❌ | ✅ 符合预期（未绑） |

### 3.2 看板数据（4 个账号全 200）

原始输出：`raw/04-dashboard-4-accounts.txt`

```
GET /ltv-churn/dashboard → code=200
data = {"totalLtv365d":676125348.87,"avgLtv365d":191971.99,"membersWithLtv":489,
        "highRiskCount":393,"medRiskCount":96,"lowRiskCount":0,"highMedRatio":100.0,
        "ltvModelVersion":"v1","churnModelCode":"RULE-CHURN-V1","snapshotsAt":"2026-11-13"}
```

4 个账号返回完全一致（该端点无租户维度过滤，本租户单租户数据）。

**用户要求的验收点已达成**：**393 HIGH / 96 MED**（`highRiskCount=393`、`medRiskCount=96`）。

### 3.3 前端部署产物字段核对

原始输出：`raw/05-frontend-asset-field-check.txt`

```
--- files containing mediumRiskCount (should be EMPTY) ---
--- files containing medRiskCount ---
/usr/share/nginx/html/assets/P2Suite-BOUm3vdv.js
/usr/share/nginx/html/assets/ltv-churn-huO7yTjL.js
```

容器内实际产物已含 `medRiskCount`、不含 `mediumRiskCount` → §2.3 修复已进入部署产物。

### 3.4 浏览器实测（admin）

截图：`raw/06-browser-admin-ltv-churn.png`

admin 登录后侧边栏「研发工具」组下出现「LTV/Churn 分析」，点击进入 `/ltv-churn` 页面，页面显示 **高流失风险 393 / 中流失风险 96 / 低流失风险 0**。

### 3.5 DB 快照

原始输出：`raw/07-db-snapshot-menu.txt`

```
sys_permission:      id=1624  dev.ltv-churn  LTV/Churn 分析  menu  parent=1210  path=/ltv-churn  icon=DataLine  sort=12  roles=SUPER_ADMIN,MANAGER,BOSS,ADMIN
sys_menu_permission: id=386   permission_id=1624  route_path=/ltv-churn  icon=DataLine  hidden=0  keep_alive=1  sort_order=12
研发工具组排序:      ... dev.care-plan(11) → dev.ltv-churn(12) → dev:intl-payment(101) ...
```

---

## 4. 证据清单

| 文件 | 内容 |
|---|---|
| `raw/01-before-permission-state.txt` | 迁移前 `sys_permission` 状态（无 dev.ltv-churn 行） |
| `raw/02-migration-apply-output.txt` | 迁移脚本执行输出（E1–E5 验证段） |
| `raw/03-after-menu-tree-4-accounts.txt` | 4 账号菜单树可见性 |
| `raw/04-dashboard-4-accounts.txt` | 4 账号 `/ltv-churn/dashboard` 响应体 |
| `raw/05-frontend-asset-field-check.txt` | 容器内前端产物字段名核对 |
| `raw/06-browser-admin-ltv-churn.png` | admin 浏览器实测截图 |
| `raw/07-db-snapshot-menu.txt` | 落地后菜单表 DB 快照 |
| `raw/collect-db.ps1` | 快照采集脚本（**非正式工具**，不接入 CI） |

---

## 5. 验证等级与未确认项

| 观测项 | 等级 | 说明 |
|---|---|---|
| admin 菜单可见 + 页面渲染 393/96 | **L5a** | 浏览器实测 + 截图 |
| boss01 / manager01 菜单可见 | **L5b** | 菜单树 API 响应体留证（**浏览器层未实测**） |
| operator01 不可见 | **L5b** | 同上 |
| `/ltv-churn/dashboard` 4 账号 200 + 真实数据 | **L5b** | curl 原始响应体 |
| 前端部署产物含 `medRiskCount` | **L5b** | 容器内实际产物 grep |
| 菜单可点击跳转（boss01 / manager01 浏览器实测） | **UNVERIFIED** | 仅 API 级验证 |
| 生产环境部署 | **UNVERIFIED** | 本次仅本地 Docker |
| `dev` 父级补绑后端接口是否被意外放开 | **L4（静态）** | 已检索确认无 `@RequirePermission("dev")` 系列校验；未做越权实测 |

**整体判定**：**PARTIAL** —— admin 浏览器级闭环；boss01 / manager01 为 API 级闭环，浏览器层待人工验收。

---

## 6. 未做 / 明确不做

| 项 | 说明 |
|---|---|
| 守卫码 `crm:tag` | **不动**（用户决策） |
| OPERATOR 角色绑定 | **不绑**（无守卫码，绑了会 403） |
| `dev.ltv-training` 对 MANAGER / BOSS 的可见性 | **不动**（不在本次范围） |
| 生产环境验证 | 未执行 |
