# I185 — 前后端权限码双轨漂移：前端 8 个码不在 `sys_permission` 中，8 个页面永久 403

> **状态**：**Open**（本批**仅登记**，**未修改任何业务代码、未写库**）
> **严重度**：**P0**（考勤 5 个页面 + 提成 3 个页面对**所有非超管角色永久不可达**，前端路由守卫在进入页面前就把请求拦掉）
> **发现日期**：2026-11-16（batch2-C 权限对齐只读调查）
> **登记人**：Cursor
> **绑定基线**：HEAD `56d59a2`
> **验证等级**：**VERIFIED（运行期 L5a + 静态）** —— 前端源码静态梳理 + `GET /auth/me/permissions` 实测载荷 + `sys_permission` 实际查询
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`
> **证据**：`audit/evidence/F171-attendance-commission-perm-alignment/`（`raw/09-frontend-route-and-permission-codes.txt` A/B/F/G/H 段、`raw/01-static-endpoint-matrix.md` §5、`raw/04b` V1/V2、`raw/04` X4）
> **关联**：I183（考勤死码）、I184（提成死码）、I188（种子与运行期漂移）、I124（授权码体系分裂）、I115（权限注解覆盖率）
> **全量口径（2026-11-23 补充）**：本条的「8 个码」**仅为考勤 / 提成切片快照**（批次 2-C 调查范围）；全仓前端码的完整漂移清单为 **22 个**（含 `dev:access`、`pay:order`、`org:*`、`mh:*`、`dcep:hardware-wallet`、`iot:device:manage`、`payment:overseas:gateway`、`resource:schedule` 等），覆盖约 **28 条路由**，见 **I202**（2026-11-23）。本条正文结论与证据保持原样不改。
> **前提变更（2026-11-23）**：实测 `commission:simulation` **在库中存在**（id=1631，API 型，已绑 ADMIN/BOSS/MANAGER），本条 §1.1 该行「❌ 不存在」应读作 ✅；且 I183 的「`attendance:*` 9 码绑定数全 0」前提**已过期**（现全部绑定 2–5 个角色）。详见 I202 §2 的 D2 / D3。

---

## 0. 一句话描述

后端用 `attendance:*` / `commission:rule:list` 一套码，前端路由 `meta.permission` 用 `employee:attendance*` / `commission:rule` / `commission:rule:config` / `commission:simulation` 另一套码；
后者共 **8 个码在 `sys_permission` 中完全不存在**（种子 `init_attendance.sql:112-119` 声明过但运行期库里没有）→
前端守卫 `hasPermission()` 恒为 `false` → **考勤 5 个页面 + 提成 3 个页面永远跳转 `/403`**，与后端是否修好无关。

---

## 1. 事实核实

### 1.1 前端路由 `meta.permission` 清单（`frontend/meiyetec-web/src/router/index.ts`）

| 行号 | 路由 | 页面 | `meta.permission` | `sys_permission` 中是否存在 |
|---|---|---|---|---|
| 105 | `attendance/index` | 考勤打卡 | `employee:attendance` | ❌ **不存在** |
| 106 | `attendance/records` | 考勤记录 | `employee:attendance:list` | ❌ **不存在** |
| 107 | `attendance/leave` | 请假管理 | `employee:leave` | ❌ **不存在** |
| 108 | `attendance/statistics` | 考勤统计 | `employee:attendance:stat` | ❌ **不存在** |
| 109 | `attendance/config` | 考勤规则配置 | `employee:attendance:config` | ❌ **不存在** |
| 113 | `commission/rule-list` | 提成规则列表 | `commission:rule` | ❌ **不存在** |
| 114 | `commission/rules` | 提成规则配置 | `commission:rule:config` | ❌ **不存在** |
| 115 | `commission/simulation` | 提成模拟计算 | `commission:simulation` | ❌ **不存在** |
| 58 | `commission/rules` | 提成规则 | `commission:rule:list` | ✅ 存在 |
| 59 | `commission/records` | 提成记录 | `commission:record:list` | ✅ 存在 |
| 60 | `commission/salary` | 工资报表 | `commission:salary:list` | ✅ 存在 |
| 61 | `commission/settlement` | 结算管理 | `commission:settlement:list` | ✅ 存在 |
| 94 | `commission/batch` | 提成结算批次 | `commission:batch` | ✅ 存在（**类型是 button**，见 I188） |
| 95 | `commission/reversal` | 提成冲正 | `commission:reversal` | ✅ 存在（**类型是 button**） |

> ⚠️ **路由重名**：`commission/rules` 在 `:58` 与 `:114` **出现两次**（`name` 分别为 `CommissionRules`），`:114` 覆盖前者 —— 提成规则页最终生效的守卫码是 `commission:rule:config`（不存在）→ **该页面对非超管也是 403**。

### 1.2 前端守卫判定链（静态）

| 位置 | 代码 | 行为 |
|---|---|---|
| `router/index.ts:226-233` | `const need = to.meta.permission as string \| undefined` … `if (need && !safePath && !store.hasPermission(need)) { next({ path: '/403', query: { denied: need } }) }` | 精确码匹配，失败跳 `/403`；**不发起任何后端请求** |
| `stores/permission.ts:62-66` | `hasPermission: (code) => state.permissions.includes(code) \|\| state.permissions.includes('*')` | 只认**精确码**或 `*`；**无前缀/通配匹配** |
| `router/index.ts:228` | `safePath = to.path === '/dashboard' \|\| '/login' \|\| startsWith('/403') \|\| startsWith('/404')` | 兜底白名单**不含**任何考勤/提成路径 |

### 1.3 运行期下发载荷（`raw/09` H 段）

`GET /auth/me/permissions` 实测：

| 账号 | `attendance*` 码 | `commission*` 码 | 含 `*` |
|---|---|---|---|
| `admin` | `[]` | 13 个（含 `commission:rule:list` / `record:list` / `salary:list` / `settlement:list` / `commission:batch` / `commission:reversal` / 6 个 `commission.x` 菜单码） | ✅ 是 |
| `manager01` | `[]` | 同上 13 个 | ❌ 否 |
| `boss01` | `[]` | 11 个（**缺 `commission.batch` / `commission.records`**） | ❌ 否 |
| `clerk01` | `[]` | `[]` | ❌ 否 |

> 关键点：**4 个账号的 `attendance*` 码全部为空数组** —— 不只是「前端码没入库」，连**后端的 `attendance:*` 码也没人持有**（I183）。
> `employee:attendance*` 8 个码在**任何账号**的载荷里都不存在 → 前端守卫对所有账号（含超管以外）都判 false。

### 1.4 按钮级权限完全缺失

| 项 | 实测 |
|---|---|
| `frontend/meiyetec-web/src/directives/permission.ts` | 指令**已定义** |
| `src/views/**` 下 `v-permission` 使用点 | **0 处**（`raw/09` B 段全文检索无匹配） |
| 结论 | 前端**只有路由级门禁**，无按钮级门禁 → 页面能进的话，**页面上所有按钮都可见可点**，只是点下去被后端 403 |

### 1.5 种子侧的前端码声明（说明这些码「本该存在」）

`backend/src/main/resources/db/sql/init_attendance.sql:112-119`：

```sql
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('employee:attendance', '考勤打卡', 'employee', 'menu', 1, 0, NOW()),
    ('employee:attendance:list', '考勤记录', 'employee', 'menu', 1, 0, NOW()),
    ('employee:leave', '请假管理', 'employee', 'menu', 1, 0, NOW()),
    ('employee:attendance:stat', '考勤统计', 'employee', 'menu', 1, 0, NOW()),
    ('employee:attendance:config', '考勤规则配置', 'employee', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);
```

> ⚠️ **该 INSERT 与运行期表结构不匹配**：运行期 `sys_permission` 的列是 `permission_code` / `permission_name` / `permission_type` / `parent_id` / `path` / `icon` / `sort_order` / `status` / `create_time` / `update_time` / `deleted`，
> **没有 `code` / `name` / `module` / `type` 这 4 列** → 该脚本若被执行会因**未知列**报错；即使被手工改造执行过，码也未出现在运行期库中。
> 同样，`fix_menu_tree_restructure.sql:300-306` 也用 `SELECT 'employee:attendance', ... FROM sys_permission WHERE permission_code='employee'` 的形式声明过这些码。

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | **8 个页面永久 403** | 非超管访问考勤 5 页 / 提成 3 页 | 前端守卫先拦，**后端修好也没用**；业务侧表现为「菜单点了没反应/跳 403」 |
| R2 | 路由重名导致守卫码被覆盖 | 访问 `commission/rules` | `:114` 的 `commission:rule:config` 覆盖 `:58` 的 `commission:rule:list` → 提成规则页**比预期多锁一道** |
| R3 | 前后端各改一半 → 死锁 | 只改前端码或只改后端码 | 前端改用 `attendance:*` 但后端未改 → 页面能进但接口 403；后端改了但前端未改 → 页面进不去 |
| R4 | **按钮级权限 0 覆盖** | 页面进入成功 | 所有写按钮可见 → 用户点了得到 403，**体验上等于功能坏了**（叠加 I184 则永远点不动） |
| R5 | 种子脚本与运行期表结构漂移 | 有人在生产重跑 `init_attendance.sql` | 因列名不存在而**报错中断**；或被改造后插入码但**不绑定角色** → 又一批死码（与 I183 同型） |
| R6 | 命名体系无收敛机制 | 后续新增模块 | 前后端各自命名（`attendance:*` vs `employee:attendance*`、`commission:rule:list` vs `commission:rule`）**没有单一事实来源**，同类问题必然复发 |

---

## 3. 建议方案（**仅建议，本批不实施**）

| # | 步骤 | 内容 | 备注 |
|---|---|---|---|
| 1 | **定一版规范码表** | 以**后端 `@RequirePermission` 实际引用的 37 个码**为准（这是唯一有服务端强制力的口径），前端路由改为引用同一批码 | 前端 8 个「不存在」的码应**废弃**，不反过来往库里补码（补了也是死码，见 I183） |
| 2 | **修路由重名** | `:58` 与 `:114` 两条 `path: 'commission/rules'` 需合并或改名；`name` 也重复（`CommissionRules`） | 前端问题，**需单独确认后再改**（本批不改业务代码） |
| 3 | **删除或修正 `v-permission` 空指令** | 若保留则需在提成/考勤页面的写按钮上**真正使用**；否则删除以免误导 | 需与前端对齐交互口径 |
| 4 | **建立「码清单单一来源」** | 把权限码列表从散落的 SQL 收敛到一份可执行清单（如 `permission-codes.csv` / 一份迁移脚本），由 CI 校验：① 后端注解码 ⊆ 清单 ② 前端 `meta.permission` ⊆ 清单 ③ 清单中每个码至少被一个角色绑定 | 与 I183 / I184 / I188 同批落地 |
| 5 | **种子脚本与表结构对齐** | `init_attendance.sql:112-119` 的列名（`code`/`name`/`module`/`type`）与运行期 `sys_permission`（`permission_code`/`permission_name`/`permission_type`/…）不符，需修正或标注为历史脚本 | 本批**未入库**任何脚本（规则 19） |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 提取 `router/index.ts` 全部 `meta.permission` | 每个码都能在 `sys_permission` 查到对应行 |
| 2 | 同上，**且**每个码**至少被一个非超管角色绑定** | 否则仍是无用码（与 I183/I184 联动） |
| 3 | `manager01` 打开考勤 5 个页面 | **不跳 `/403`**，页面正常渲染 |
| 4 | `manager01` 打开提成 `rule-list` / `rules` / `simulation` 三个页面 | **不跳 `/403`**（`simulation` 需 I187 先补后端 handler 与鉴权） |
| 5 | `clerk01` 打开 `/commission/salary` | 仍应跳 `/403`（最小权限角色不应看到工资报表） |
| 6 | 全仓 `meta.permission` 集合 ∩ 后端 `@RequirePermission` 集合 | 差异必须为 0，或每个差异有明确书面理由 |
| 7 | `sys_menu_permission` 中 `route_path` 与 `router/index.ts` 路由对照 | 一一对应，无孤儿菜单 |

---

## 5. 边界声明

- 本 Issue 结论来自**前端源码静态梳理** + **后端源码静态梳理** + **本机开发库只读查询** + **运行期 `/auth/me/permissions` 只读 GET 载荷实测**。
- **未打开浏览器**、**未做任何前端交互验收**、**未修改任何业务代码**、**未执行任何写操作 / DDL / 入库脚本**。
- 「页面永久 403」是基于守卫代码（`router/index.ts:226-233` + `stores/permission.ts:62-66`）+ 实测载荷的**代码级结论**；未做浏览器端到端点击验证 → 按验收规则，页面级最终表现仍标 **UNVERIFIED**，需人工在浏览器复核。
- 未核实：生产环境 `sys_permission` 是否含这 8 个码（若生产有，则结论需重估）。
- 未核实：`init_attendance.sql` 在生产部署链路中是否被执行、执行是否报错。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。

---

**下一步**：先与 I183 / I184 / I188 商定**唯一码表**（以服务端 37 码为准），再改前端路由；前端改动需按项目规则另行取得授权后实施。
