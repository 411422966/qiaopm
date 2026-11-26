# I210 — `/mh/visual` 在 `sys_menu_permission` 有 2 条同路径绑定行，菜单树出现重复入口

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P3**
> **P3 理由**：**纯 UI 重复**，不产生权限绕过、不产生数据越权、不影响后端鉴权。仅导致菜单树出现两个指向同一路由的入口（视觉重复 + 点击歧义）。
> **发现日期**：2026-11-26（F175 批次 —— I206 §3 修复方向落地后的一致性复核）
> **登记人**：Cursor
> **绑定基线**：HEAD `78c41f8`（`docs(audit): I202 口径 19→20 修正 + 登记 I206/I207 + D-3-34/D-3-35 流程规则`）
> **验证等级**：**静态 + 运行期 VERIFIED**（DB 实查 2 行绑定 + `sys_permission` 父子两级 `path` 实查）
> **关联**：I185 / I202（前端路由与菜单双轨）、I210 是 **I206** 落地后暴露的菜单侧遗留
> **登记状态**：**已登记于 `audit/issues/issue-register.md`**（2026-11-26，用户批准后写入）

---

## 0. 一句话描述

`sys_menu_permission` 中 **2 行**指向**同一个 `route_path = '/mh/visual'`**：
一行绑**父菜单** `sys_permission.id=1205`（`mh`），一行绑**子菜单** `sys_permission.id=1251`（`mh.visual`），
而 `sys_permission` 里 1205 与 1251 是**父子两级**、`path` **同为 `/mh/visual`**。
`buildMenuTree` 拼树后会出现**两个指向同一路由的菜单入口**。

---

## 1. 事实

### 1.1 `sys_menu_permission` 实查（2 行同路径）

```sql
SELECT smp.id, smp.permission_id, p.permission_code, p.permission_type,
       p.path AS perm_path, p.parent_id AS perm_parent, smp.route_path, smp.hidden
FROM sys_menu_permission smp JOIN sys_permission p ON p.id = smp.permission_id
WHERE smp.route_path = '/mh/visual';
```

| id | permission_id | permission_code | permission_type | perm_path | perm_parent | route_path | hidden |
|---|---|---|---|---|---|---|---|
| **268** | **1205** | `mh` | menu | `/mh/visual` | 0 | `/mh/visual` | 0 |
| **314** | **1251** | `mh.visual` | menu | `/mh/visual` | 1205 | `/mh/visual` | 0 |

→ 两行 `route_path` 完全相同、`hidden` 均为 0（**都可见**）。

### 1.2 `sys_permission` 侧：父子两级 `path` 相同

| id | permission_code | permission_type | parent_id | path |
|---|---|---|---|---|
| 1205 | `mh` | menu | 0 | `/mh/visual` |
| 1251 | `mh.visual` | menu | **1205** | `/mh/visual` |

→ 1205 是 `mh` 父节点（`parent_id=0`，顶层分组），1251 是其子节点；
但**两者的 `path` 都是 `/mh/visual`** —— 父分组本身不指向独立路由，却被配置成了与子节点相同的路由。

### 1.3 后果

- `buildMenuTree` 按 `permission_type='menu'` + `sys_menu_permission` 拼树时，**父子两个节点都会渲染出 `/mh/visual` 入口**；
- 点击任意一个都进入同一页面 → **视觉重复 + 点击歧义**；
- **不产生权限绕过**：F175 落地后后端 `@RequirePermission` 已按码放行，菜单重复不影响鉴权结论；
- **不产生数据越权**：同一页面的数据权限由后端端点决定。

---

## 2. 影响

用户侧：管理后台左侧菜单出现**两个「视觉档案」入口**，指向同一路由。属**体验缺陷 / 数据配置缺陷**，非安全缺陷。

运维侧：`sys_menu_permission` 的 `route_path` 存在**一对多**语义（同一 `route_path` 对应 2 个 `permission_id`），
后续任何按 `route_path` 反查唯一菜单的脚本 / 逻辑都会踩到歧义。

---

## 3. 修复方向（不在本批范围）

- **方案 1（推荐）**：把父菜单 1205 的 `route_path` 改为 `/mh`（作为分组容器），1251 保持 `/mh/visual`；
- **方案 2**：把父菜单 1205 的 `path` 置 `NULL`（纯分组节点，不参与路由匹配），并把 `sys_menu_permission.id=268` 置 `hidden=1`；
- 修改前需确认前端 `router/index.ts` 中 `/mh` 是否有对应路由，否则会引入新的 404。

> ⚠️ 本条**不修**，且修复涉及 `sys_permission.path` / `sys_menu_permission.route_path` 的**数据修改**，
> 与 F175 的「新增权限码 + 新增绑定」性质不同（后者是增量、零歧义；前者是改既有行的路由语义），需单独报批。

---

## 4. 遗留标注

本批**只登记，不修**。

与 **I206** 无冲突：F175 只**新增** `sys_permission` 行（id 1633–1641）与 `sys_role_permission` 绑定，
**未修改** id 1205 / 1251 的 `path`，也**未新增** `sys_menu_permission` 行 → 本条与本批变更**无因果关系**，
是本批复核 I206 菜单侧时**独立发现**的既有数据缺陷。

---

## 5. 关联与交叉引用

- `sys_menu_permission` id 268 / 314（`route_path='/mh/visual'`）
- `sys_permission` id 1205（`mh`，parent_id=0）/ 1251（`mh.visual`，parent_id=1205）
- `audit/issues/I206-mh-visual-consent-complication-no-backend-auth.md`
- `audit/issues/I185-frontend-backend-perm-code-drift.md`（前端路由 / 菜单双轨同族）
- Evidence：`audit/evidence/F175-mh-visual-consent-complication-perm-annotations/evidence.md`
