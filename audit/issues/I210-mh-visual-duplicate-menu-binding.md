# I210 — `/mh/visual` 在 `sys_menu_permission` 有 2 条同路径绑定行，菜单树出现重复入口

> **状态**：**Fixed（2026-11-27，DB 变更已执行）** —— 开发库 `sys_menu_permission.id=268`（父菜单 `mh` / permission_id=1205）的 `route_path` 已由 `/mh/visual` 改为 `/mh`，与子菜单 id=314（`mh.visual`）的 `route_path` 解耦，重复入口消除；**本次修复不含任何代码改动**（与 commit `10d7981` 无关，该提交只涉及 I209#2 与 I208）；**生产库尚未执行**，迁移 SQL 见 `backend/src/main/resources/db/sql/migrate_i210_mh_visual_route_path.sql`
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

## 3. 修复方向（✅ 方案 1 已实施 —— DB 变更）

- ✅ **方案 1（已采用）**：把父菜单 1205 的 `route_path` 改为 `/mh`（作为分组容器），1251 保持 `/mh/visual`；
- **未采用方案 2**（把 `sys_permission.path` 置 `NULL` + `smp.hidden=1`）—— 方案 1 已足以消除同 `route_path` 一对多，且改动面更小。

**实施结果**：
- 开发库已执行：`UPDATE sys_menu_permission SET route_path='/mh' WHERE id=268;` → 复查 `WHERE route_path='/mh/visual'` 仅剩 id=314 一行，重复消除；
- **生产库未执行**，迁移 SQL 已留档（见 §6）；
- **本次不含代码改动** —— 前端 `router/index.ts:153` 的 `mh/visual` 路由本就存在且带 `permission: 'mh.visual'`，父节点 `route_path` 改为 `/mh` 后不再与其重复，无需配套前端修改。

---

## 4. 遗留标注

本条**已修**（2026-11-27，DB 变更已执行；生产库待执行，迁移 SQL 见 §6）。

与 **I206** 无冲突：F175 只**新增** `sys_permission` 行（id 1633–1641）与 `sys_role_permission` 绑定，
**未修改** id 1205 / 1251 的 `path`，也**未新增** `sys_menu_permission` 行 → 本条与该批变更**无因果关系**，
是复核 I206 菜单侧时**独立发现**的既有数据缺陷，本次修复为其独立实施。

---

## 5. 关联与交叉引用

- `sys_menu_permission` id 268（`mh` 父菜单，修复后 `route_path='/mh'`）/ 314（`mh.visual` 子菜单，`route_path='/mh/visual'`）
- `sys_permission` id 1205（`mh`，parent_id=0）/ 1251（`mh.visual`，parent_id=1205）
- `audit/issues/I206-mh-visual-consent-complication-no-backend-auth.md`
- `audit/issues/I185-frontend-backend-perm-code-drift.md`（前端路由 / 菜单双轨同族）
- Evidence：`audit/evidence/F175-mh-visual-consent-complication-perm-annotations/evidence.md`

---

## 6. 遗留风险

父菜单 `mh`（1205）的 `route_path` 现为 `/mh`，而前端 `router/index.ts` **不存在 `/mh` 路由**（只有 `mh/visual` 与 `mh/consent` 两个子路径）。

- **触发条件**：出现「**只持 `mh`（1205）而不持 `mh.visual`（1251）**」的角色 → 该角色菜单树中父节点退化为**可点击项**，点击后前端匹配不到 `/mh` 路由 → **404**；
- **当前不可达**：现有角色的绑定关系中，持 1205 者均同时持 1251，父节点对所有现存角色均有子项，**不产生可点击的空父节点**；
- **未来风险**：新增只授予 `mh` 分组码而不授予 `mh.visual` 子码的角色时，该 404 会出现。缓解方案二选一：① 前端补一条 `/mh` 的重定向路由（指向 `/mh/visual`）；② 把父菜单 `route_path` 置 `NULL`（纯分组节点，不参与路由匹配）—— 需另行报批；
- 本条为**已知遗留**，本批只做「消除重复入口」这一最小修复，不扩大改动面。
