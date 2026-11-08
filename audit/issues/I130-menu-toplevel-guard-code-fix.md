# I130 — MANAGER 顶级菜单补全（客户运营 / 员工提成）+ 守卫码断裂修复

- **状态**：批次 1 已落地并验证（PARTIAL — 见 §6 遗留问题）；**营销域部分已由 batch2-B 修复（2026-11-09，见 §8）**
- **发现批次**：I124 权限码体系分裂调查续（2026-11-06）
- **父批次**：I124（权限码体系分裂）
- **严重度**：🟡 中（修复前 MANAGER 完全看不到「客户运营」「员工提成」两个顶级分类；补完后菜单可见但 `/commission/rules` 仍 403）
- **落盘脚本**：`backup/menu-fix-batch1.sql`（含块 0 备份 / 块 1 绑定 / 块 5.4 守卫码 / 块 6 验证）
- **回滚**：脚本文末「回滚段」+ `backup_sys_*_bak_20261106` 三张备份表
- **是否阻塞上线**：否（核心目标已达成；`/commission/rules` 死链归属 I124-c 战役）

---

## 1. 问题现象

### 1.1 MANAGER 看不到「客户运营」「员工提成」

`sys_permission` 中存在两个顶级分类（`permission_type='menu'`，`parent_id=0`）：

| id | permission_code | permission_name |
|---|---|---|
| 1203 | `commission` | 员工提成 |
| 1206 | `crm` | 客户运营 |

二者在 `sys_menu_permission` 中均已有行且 `hidden=0`（菜单本身可见），
但 `sys_role_permission` 中 **MANAGER 角色未绑定** → 前端 `GET /auth/me/menu` 不返回这两棵子树。

菜单可见性查询链路（`backend/src/main/java/com/meiyetec/mapper/SysMenuPermissionMapper.java:14-24`）：

```
sys_menu_permission smp
  JOIN sys_permission p        ON smp.permission_id = p.id
  JOIN sys_role_permission rp  ON p.id = rp.permission_id
  JOIN sys_user_role ur        ON rp.role_id = ur.role_id
 WHERE ur.user_id = ? AND p.permission_type='menu' AND p.deleted=0
   AND (smp.hidden IS NULL OR smp.hidden=0)
```

→ **顶级分类必须与子菜单同时绑定**，否则子菜单被当成根节点平铺（`RbacService.java:70-116` 依赖 `parent_id` 拼树）。

### 1.2 守卫码断裂（独立缺陷）

13 个后端 `@RequirePermission` 实际使用的权限码，在 `sys_permission` 中存在，
但 `sys_role_permission` 中**零角色绑定**（`roles=NONE`）：

| 权限码 | permission_type | 使用位置 |
|---|---|---|
| `commission:rule:list` | button | `CommissionController.java:47` / `:97` |
| `commission:record:list` | button | `CommissionController.java:213` |
| `commission:salary:list` | button | `CommissionController.java:319` |
| `commission:settlement:list` | button | `CommissionController.java:568` |
| `commission:batch` | button | `CommissionController.java`（批次接口） |
| `commission:reversal` | button | `CommissionController.java`（冲正接口） |
| `market:coupon:list` | button | `CouponController.java` |
| `market:point:list` | button | `PointController.java` / `PointGoodsController.java` |
| `market:activity:list` | button | `MarketActivityController.java` |
| `mkt:coupon` | button | `MktCouponController.java` |
| `mkt:points` | button | `MktPointsController.java` |
| `mkt:promotion` | button | `MktPromotionController.java` |
| `mkt:invite:list` | API | `InviteController.java` / `MemberInviteController.java` |

后端守卫只校验 code 是否在权限集合（`security/PermissionAspect.java:47-55`），不校验 `permission_type`，
因此这批 `button` 类型码同样构成实际访问门禁。

**SUPER_ADMIN 不需要补**：通配 `*` 已放行（`security/RbacContextLoader.java:78-91`、`service/AuthService.java:63-72`）。

---

## 2. 用户拍板范围（2026-11-06）

| 决策 | 内容 |
|---|---|
| 决策 1 | 只做 **块 1（MANAGER 绑定）+ 块 5.4（守卫码断裂）**；块 2-5 全量展开留作 I124-c 战役 |
| 决策 2 | **不**升级 `permission_type` button→menu（避免角色权限配置页显示混乱，与下批次一起做） |
| 决策 3 | **不**新建考勤/耗材顶级分类（涉及 `parent_id=0` 新行 + `Layout.vue` 图标/排序，属 UI 结构变更） |
| 决策 4 | 块 5.4 给 **ADMIN + BOSS** 补 13 个守卫码；**SUPER_ADMIN 不补**（通配 `*`）；**CLERK/OPERATOR/STORE_ADMIN 不补**（本就不该访问） |

---

## 3. 已落地的改动

脚本：`backup/menu-fix-batch1.sql`（幂等，全部 INSERT 带 `NOT EXISTS` 判重）。

### 3.1 块 0 — 备份

```sql
CREATE TABLE IF NOT EXISTS backup_sys_role_permission_bak_20261106 AS SELECT * FROM sys_role_permission;
CREATE TABLE IF NOT EXISTS backup_sys_menu_permission_bak_20261106 AS SELECT * FROM sys_menu_permission;
CREATE TABLE IF NOT EXISTS backup_sys_permission_bak_20261106    AS SELECT * FROM sys_permission;
```

实测行数：`role_perm=849` / `menu=110` / `perm=476`。
另存全库快照 `backup/menu-fix-batch1-pre.sql`（mysqldump 10.13 / MySQL 8.0.46）。

### 3.2 块 1 — MANAGER 绑定

| 段 | 内容 | 实际新增 |
|---|---|---|
| 1.1 | 顶级分类 `crm` / `commission` | 2 行 |
| 1.2 | `parent_id=1206` 子菜单（`permission_type='menu'`，排除 `crm.rfm`） | 9 行 |
| 1.3 | `parent_id=1203` 子菜单（`permission_type='menu'`） | 6 行 |
| 1.4.1 | CRM 守卫码 8 个（`crm:follow-up` 等，执行前已绑定，幂等兜底） | 0 行 |
| 1.4.2 | 提成守卫码 6 个（`commission:rule:list` 等，原 `roles=NONE`） | 6 行 |

排除 `crm.rfm`（id=1261）的理由：前端路由已注释（`router/index.ts:149`「RFM 分群为幽灵功能」），
`hidden` 调整归属块 2（孤儿菜单行清理）。

### 3.3 块 5.4 — 守卫码断裂修复（ADMIN / BOSS）

13 个码 × 2 角色 = 26 行。

### 3.4 增量合计

`sys_role_permission` 849 → **898（+49 行）**，逐行明细已核对：

| 角色 | 新增内容 | 行数 |
|---|---|---|
| ADMIN | 13 个守卫码 | 13 |
| BOSS | 13 个守卫码 | 13 |
| MANAGER | `commission` + `crm` 顶级分类 | 2 |
| MANAGER | CRM 子菜单 9 条 | 9 |
| MANAGER | 提成子菜单 6 条 | 6 |
| MANAGER | 提成守卫码 6 个 | 6 |

**未新增**：SUPER_ADMIN / CLERK / OPERATOR / STORE_ADMIN 零改动。

---

## 4. 验证结果

### 4.1 验证 SQL（脚本块 6，实测输出）

**6.1 manager01 可见菜单行总数**（口径与 `SysMenuPermissionMapper.listVisibleMenuByUserId` 一致）：

```
manager01_visible_menu_rows
53
```

**6.2 顶级分类是否出现**：

| permission_code | permission_name | child_count |
|---|---|---|
| commission | 员工提成 | 1 |
| crm | 客户运营 | 1 |

**6.3 manager01 可见顶级分类清单**（改后新增 `commission` / `crm`，共 12 项）：

```
dashboard / pos / booking / member / wallet / package /
inventory / biz / commission / marketing / crm / report
```

**6.4 13 个守卫码 roles 复查**（全部脱离 `NONE`）：

| 权限码 | roles |
|---|---|
| `commission:batch` | ADMIN, BOSS, MANAGER |
| `commission:record:list` | ADMIN, BOSS, MANAGER |
| `commission:reversal` | ADMIN, BOSS, MANAGER |
| `commission:rule:list` | ADMIN, BOSS, MANAGER |
| `commission:salary:list` | ADMIN, BOSS, MANAGER |
| `commission:settlement:list` | ADMIN, BOSS, MANAGER |
| `market:activity:list` | ADMIN, BOSS |
| `market:coupon:list` | ADMIN, BOSS |
| `market:point:list` | ADMIN, BOSS |
| `mkt:coupon` | ADMIN, BOSS |
| `mkt:invite:list` | ADMIN, BOSS |
| `mkt:points` | ADMIN, BOSS |
| `mkt:promotion` | ADMIN, BOSS |

**6.5 增量统计**：`delta_rows = 49`（重跑脚本后仍为 49，幂等成立）。

### 4.2 接口层实测

`POST /auth/login`（manager01 / 123456）→ `permissions` 192 项，菜单树：

```
crm         -> 10 children
   crm.care-overview  /crm/care/overview
   crm.follow-task    /crm/care/follow-task
   crm.follow-plan    /crm/care/follow-plan
   crm.complaint      /crm/care/complaint
   crm.review         /crm/care/review
   crm.review-tpl     /crm/care/review-tpl
   crm.cross-rule     /crm/cross-rule
   crm.cross-record   /crm/cross-record
   crm.ltv            /intel/ltv
   crm:follow-rule    /crm/care/follow-rule
commission  -> 6 children
   commission.rules / records / salary / settlement / batch / reversal
```

后端接口带 manager01 token 实测：`GET /commission/rules/page` → 200，`GET /commission/records/page` → 200。

### 4.3 浏览器实测（manager01 登录，localhost:8088）

- 左侧菜单出现「客户运营」「员工提成」两个顶级分类 ✅
- `/crm/care/overview`（客户运营看板）正常渲染 ✅
- `/commission/records`（提成记录）正常渲染，含「工资月份 / 查询 / 补算订单 / 导出」控件 ✅
- `/commission/rules` → **403 页面**（`?denied=commission:rule:config`）❌ → 见 §5

---

## 5. 新发现缺陷（归属 I124-c，本批次未修）

### 5.1 路由表重复定义导致守卫码被覆盖

`frontend/meiyetec-web/src/router/index.ts` 中 `commission/rules` 被定义 **两次**，且 `name` 相同：

```58:58:frontend/meiyetec-web/src/router/index.ts
      { path: 'commission/rules', name: 'CommissionRules', component: () => import('@/views/commission/rules.vue'), meta: { title: '提成规则', requiresAuth: true, permission: 'commission:rule:list' } },
```

```114:114:frontend/meiyetec-web/src/router/index.ts
      { path: 'commission/rules', name: 'CommissionRules', component: () => import('@/views/commission/rules.vue'), meta: { title: '提成规则配置', requiresAuth: true, permission: 'commission:rule:config' } },
```

vue-router 按 `name` 去重时**后者覆盖前者** → 实际生效守卫码为 `commission:rule:config`。

而 `commission:rule:config` 在 `sys_permission` 中 **不存在**（实测 `COUNT(*)=0`），
后端 `CommissionController` 也不用这个码（用的是 `commission:rule:list`）。
→ 任何角色访问 `/commission/rules` 必然 403，**与本批次 DB 绑定无关**（ADMIN/BOSS 同样中招）。

**性质**：契约漂移（前端路由 meta.permission 指向不存在的权限码），非权限绑定问题。
**建议修法**：删除第 114 行重复定义（保留第 58 行 `commission:rule:list`，与后端 controller 一致）。
**本批次未修**：涉及前端源码修改，超出用户拍板范围。

### 5.2 前端守卫码在 DB 中不存在的其余 33 个

`router/index.ts` 147 条路由、97 个唯一权限码，其中 **33 个在 `sys_permission` 中不存在**（块 4 范围）：

```
dev:access / biz:service-item / biz:service:item / biz:treatment:plan / mkt:effect /
org:hq / org:brand / org:store / employee:attendance / employee:attendance:list /
employee:leave / employee:attendance:stat / employee:attendance:config /
employee:level:evaluation / commission:rule / commission:rule:config /
commission:simulation / resource:schedule / payment:overseas:gateway /
iot:device:manage / dcep:hardware-wallet / mkt:channel / mkt:template /
mkt:touch-task / mkt:birthday / mkt:journey / mkt:conversion / mkt:pricing /
mkt:dispatch / mh:visual / mh:consent / pay:dcep / mkt:activity / pay:order / biz:room
```

其中 `mh:visual` / `mh:consent` 属**用户已验证通过页面**（禁止主动修改），本次仅登记不处理。

### 5.3 manager01 菜单中 7 条路由守卫码未绑定（既有断裂，非本批次引入）

实测（manager01 权限集 vs 路由 meta.permission 逐条比对，41 条菜单路径）：

| 菜单路径 | 路由守卫码 | 状态 |
|---|---|---|
| `/biz/service-item` | `biz:service:item` | DENIED（DB 中只有 `biz:service-item:list` / `:edit`） |
| `/biz/treatment-plan` | `biz:treatment:plan` | DENIED（DB 中不存在） |
| `/market/coupon` | `market:coupon:list` | DENIED（仅 ADMIN/BOSS 已绑，MANAGER 未绑） |
| `/market/points` | `market:point:list` | DENIED（同上） |
| `/market/activity` | `market:activity:list` | DENIED（同上） |
| `/package/list` | `package:list` | DENIED（该码 `permission_type='API'`，仅 ADMIN/BOSS 已绑） |
| `/package/member-orders` | `package:list` | DENIED（同上） |

均为**改动前既已存在**（备份表验证：`BEFORE: MGR market menu perm = 4`、`package = 3`、`biz = 3`，
菜单绑定早已存在，但守卫码未绑 → 菜单可见、点击 403）。
归属块 4 / 块 5.1-5.3（其余角色绑定），本批次按决策 1 不处理。

---

## 6. 本批次结论

| Feature | 状态 | 依据 |
|---|---|---|
| F-MENU-MGR-TOPLEVEL（MANAGER 顶级菜单绑定） | **VERIFIED** | 6.1=53 / 6.2 / 6.3 + 接口 + 浏览器三方一致 |
| F-GUARD-CODE-BREAK（守卫码断裂修复） | **VERIFIED** | 6.4 全部脱离 NONE + 6.5 delta=49 + 幂等重跑一致 |
| F-ROUTE-DUP-COMMISSION（提成规则路由死链） | **BROKEN**（既有缺陷，归属 I124-c） | §5.1，浏览器 403 `?denied=commission:rule:config` |
| F-GUARD-MISSING-33（33 个前端守卫码 DB 缺失） | **PARTIAL**（块 4 范围；营销域 10 个已由 batch2-B 补齐 → **Fixed**，其余 23 个仍 MISSING） | §5.2 + §8 |
| F-MENU-DENIED-7（manager01 7 条菜单守卫码未绑） | **PARTIAL**（块 5.1-5.3 范围；营销域 3 条 `/market/*` 已由 batch2-B 修复 → **Fixed**，其余 4 条仍 DENIED） | §5.3 + §8 |

## 7. 未确认项

- `backup/menu-fix-batch1.sql` 由上一轮落盘、本轮复核执行；**执行前**备份表已存在（`CREATE TABLE IF NOT EXISTS` 未覆盖），
  故 `backup_sys_role_permission_bak_20261106` 快照反映的是**首次执行前**状态（849 行），已与当前 898 行比对确认 delta=49 一致。
- 浏览器实测用的是 docker 镜像 `meiyetec/web:dev`（8088），非 vite dev server（5173 未运行）。
  该镜像为构建产物，与 `frontend/meiyetec-web/src` 源码是否同步未验证（参照 I126 runtime-image-drift）。
- `menu-fix-batch1.sql` 内块 6 验证 SQL 与主脚本同文件，未单独拆 verify 文件。

---

## 8. 营销域修复记录 — batch2-B（2026-11-09）

> 落盘证据：`audit/evidence/F149-mkt-perm/`（`README.md` / `fix-batch2b.sql` / `verify-batch2b.sql` / `rollback-batch2b.sql` / `evidence-notes.md`）
> 基线 commit：`02d1da8ce6c2ea60a67bfbfba6efae8aa534b6dc`
> DB：`meiyetec@127.0.0.1:3307`（mysql:8.0），执行前备份 `backup/before-batch2b-exec.sql`（22,627,208 B）
> 范围：**仅 DB**（`sys_permission` / `sys_role_permission` / `sys_menu_permission`），未改前后端代码

### 8.1 §5.2 的 33 个缺失守卫码 —— 营销域 10 个已 Fixed

| 守卫码 | 修复前 | 修复后 | 绑定角色 |
|---|---|---|---|
| `mkt:effect` | ❌ DB 不存在 | ✅ 已建（button, parent_id=1204） | ADMIN / BOSS / MANAGER / OPERATOR |
| `mkt:channel` | ❌ | ✅ | 同上 |
| `mkt:template` | ❌ | ✅ | 同上 |
| `mkt:touch-task` | ❌ | ✅ | 同上 |
| `mkt:birthday` | ❌ | ✅ | 同上 |
| `mkt:journey` | ❌ | ✅ | 同上 |
| `mkt:conversion` | ❌ | ✅ | 同上 |
| `mkt:pricing` | ❌ | ✅ | 同上 |
| `mkt:dispatch` | ❌ | ✅ | 同上 |
| `mkt:activity` | ❌ | ✅ | 同上 |

**实测复核**：原 33 个缺失码当前仍缺 **25** 个（33 − 10 = 23，逐条比对多出 2 个为 `mh:visual` / `mh:consent` 在库中存在但归属「用户已验证通过页面」不处理范围）。
→ 营销域 10 个全部脱离 MISSING，**F-GUARD-MISSING-33 在营销域范围内 → Fixed**；其余 23 个非营销域缺失码仍 Open（归属 I124-c）。

### 8.2 §5.3 manager01 的 7 条 DENIED 菜单 —— 营销域 3 条已 Fixed

| 菜单路径 | 路由守卫码 | 修复前 | 修复后 |
|---|---|---|---|
| `/market/coupon` | `market:coupon:list` | DENIED（仅 ADMIN/BOSS 已绑） | ✅ **Fixed**（MANAGER / OPERATOR / CLERK 已绑） |
| `/market/points` | `market:point:list` | DENIED | ✅ **Fixed**（同上） |
| `/market/activity` | `market:activity:list` | DENIED | ✅ **Fixed**（ADMIN / BOSS / MANAGER 已绑） |

实测绑定（`sys_role_permission`）：`market:coupon:list` → ADMIN / BOSS / MANAGER / OPERATOR / CLERK；`market:point:list` → 同上；`market:activity:list` → ADMIN / BOSS / MANAGER。

仍 DENIED（**非营销域，本批未处理**）：`/biz/service-item`（`biz:service:item`）、`/biz/treatment-plan`（`biz:treatment:plan`）、`/package/list` 与 `/package/member-orders`（`package:list`）。

### 8.3 本次同时新建的 22 个权限码 + 12 条菜单行

- 新建 10 个页面准入**粗码**（上表）+ 10 个**细分码**（`mkt:birthday:list/edit`、`mkt:member-day:list/edit`、`mkt:ecommerce:list/edit`、`mkt:pricing:rule:list/edit`、`mkt:dispatch:list/edit`）
- 新建 12 条 `menu` 权限码 + 12 条 `sys_menu_permission` 行：`mkt.promotion` / `mkt.coupon` / `mkt.points` / `mkt.birthday` / `mkt.invite` / `mkt.dispatch` / `mkt.member-day` / `mkt.style-gallery` / `mkt.style-gallery.quick-order` / `mkt.subscription` / `mkt.lifecycle` / `mkt.ecommerce`

### 8.4 角色绑定矩阵（规格码口径，不含粗码与菜单码）

| 角色 | 规格码 | 粗码 | 菜单码 | 本批新增绑定 |
|---|:-:|:-:|:-:|:-:|
| ADMIN | 51 | 10 | 12 | 66 |
| BOSS | 23 | 10 | 12 | 38 |
| MANAGER | 53 | 10 | 19 | 76 |
| OPERATOR | 36 | 10 | 19 | 63 |
| CLERK | 2 | 0 | 0 | 2 |
| **合计** | | | | **245** |

执行后实测三表增量：`sys_permission` 476→508（**+32**）、`sys_role_permission` 913→1158（**+245**）、`sys_menu_permission` 110→122（**+12**），与预期完全一致。

### 8.5 验证结果（V1–V8，全 PASS）

| 验证 | 结果 |
|---|---|
| V1 · 20 个新码存在性 | ✅ 20/20 `OK` |
| V2 · 19 条菜单行（12 新 + 7 既有） | ✅ 19/19 `OK` |
| V3 · 五角色规格码计数 | ✅ ADMIN 51 / BOSS 23 / MANAGER 53 / OPERATOR 36 / CLERK 2，5/5 PASS |
| V3b · 用户口径对账 | 口径 A：保留 51/23/53/36/2 不裁剪；BOSS +1 / MANAGER +13 偏差已登记 |
| V4 · 菜单可见性模拟 | ✅ admin / boss01 / manager01 / operator01 各 19 条；clerk01 0 条 |
| V5 · 路由守卫模拟 | ✅ 4 个有权角色 19/19 PASS；clerk01 19/19 DENY（预期） |
| V7 · 只读断言 | ✅ BOSS 营销域写码 0；OPERATOR 未持 5 个受限 edit 码 |
| V8 · 中文编码 | ✅ 标准 UTF-8 字节序 |

### 8.6 仍未修复（归属下一批 Phase 3）

1. `mkt:promotion` / `mkt:coupon` / `mkt:points` 读写不分 → BOSS 实际具备这三个模块写能力（粗码无只读变体）。
2. 10 个细分码**本批未挂后端 `@RequirePermission` 注解**；5 个 Service/Controller 仍零注解。
3. 旧 `/market/*` 三条路由与 `marketing` 根菜单 `route_path` 指向 `/market/coupon` 的问题本批不动。
4. `market:list`（id 1138）占位码仍零绑定。
5. **本批未做浏览器人工验收**，守卫与菜单可达性由 SQL 模拟复刻后端查询逻辑得出，**不替代人工验收**。
