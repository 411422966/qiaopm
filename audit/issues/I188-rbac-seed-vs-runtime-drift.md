# I188 — 权限种子与运行期库漂移：7 个「已声明绑定」的码仍未生效 + 类型错位 + BOSS 缺 2 菜单码 + 超管通配条件脆弱

> **状态**：**Open**（本批**仅登记**，**未修改任何业务代码、未写库、未新增任何入库脚本**）
> **严重度**：**P2**（不直接造成越权；但让「配置已经写好」的假象持续存在，且使 I183 / I184 的修复缺少可信的种子基线）
> **发现日期**：2026-11-16（batch2-C 权限对齐只读调查）
> **登记人**：Cursor
> **绑定基线**：HEAD `56d59a2`
> **验证等级**：**VERIFIED（运行期 L5a + 静态）** —— 运行期 `sys_permission` / `sys_role_permission` 实查 + 种子 SQL 逐行比对
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`
> **证据**：`audit/evidence/F171-attendance-commission-perm-alignment/`（`raw/04b` V1/V2/V5/V6、`raw/04` X4/X5、`raw/08-db-super-admin-wildcard.txt`、`raw/09` H 段）
> **关联**：I183 / I184（死码）、I185（前后端码双轨）、I107（种子编码污染族）

---

## 0. 一句话描述

RBAC 种子 SQL 与运行期库存在 **4 类漂移**：
① `migrate_r3_0_c_menu_role.sql:133-137` 明确声明要给 MANAGER/BOSS 绑定 7 个提成码（`commission:list` / `rule:add/edit/delete` / `salary:confirm/pay` / `settlement:audit`），**运行期库里这 7 个码仍然 0 绑定** → 种子未生效；
② `sys_permission.permission_type` 类型错位：`commission:batch` / `commission:reversal` 是 `button`，却被前端当**菜单级** `meta.permission` 用；
③ BOSS 角色缺 `commission.batch` / `commission.records` 两个菜单码；
④ 超管通配 `*` 的触发条件是**复合条件**，新建的 `SUPER_ADMIN` 角色若不含 `booking`/`member`/`setting` 且用户名非 `admin` → **拿不到 `*`**。

---

## 1. 事实核实

### 1.1 漂移类型一：种子声明绑定 vs 运行期 0 绑定

`backend/src/main/resources/db/sql/migrate_r3_0_c_menu_role.sql:131-137`：

```sql
    -- MANAGER 独有
    -- 员工提成
    'commission', 'commission:list',
    'commission:rule:list', 'commission:rule:add', 'commission:rule:edit', 'commission:rule:delete',
    'commission:record:list', 'commission:salary:list', 'commission:salary:confirm', 'commission:salary:pay',
    'commission:settlement:list', 'commission:settlement:audit',
    'commission:batch', 'commission:reversal',
```

运行期实测（`raw/04b` V1）：

| 种子声明的码 | 运行期绑定数 | 运行期绑定角色 |
|---|---|---|
| `commission:list` | **0** | — |
| `commission:rule:add` | **0** | — |
| `commission:rule:edit` | **0** | — |
| `commission:rule:delete` | **0** | — |
| `commission:salary:confirm` | **0** | — |
| `commission:salary:pay` | **0** | — |
| `commission:settlement:audit` | **0** | — |
| `commission`（menu） | 4 | SUPER_ADMIN, ADMIN, BOSS, MANAGER ✅ |
| `commission:rule:list` | 3 | MANAGER, ADMIN, BOSS ✅ |
| `commission:record:list` | 3 | MANAGER, ADMIN, BOSS ✅ |
| `commission:salary:list` | 3 | MANAGER, ADMIN, BOSS ✅ |
| `commission:settlement:list` | 3 | MANAGER, ADMIN, BOSS ✅ |
| `commission:batch` | 3 | MANAGER, ADMIN, BOSS ✅ |
| `commission:reversal` | 3 | MANAGER, ADMIN, BOSS ✅ |

> ⚠️ **完全一致的模式**：种子里的 `menu` 与 `list` 类码**生效了**，`add/edit/delete/confirm/pay/audit` 这类**动作类码一律没生效**。
> 这不像「脚本没跑」（没跑的话 `commission:rule:list` 也不会有绑定）→ 更像是**脚本执行了，但动作类码的 `INSERT ... SELECT` 走了子查询/过滤条件而静默跳过**。
> 本次**未追到具体原因**（脚本执行日志不在库内），故该判断标 **UNVERIFIED**。

同时，`commission:list` 与 `commission:settlement:audit` 这 2 个码**后端无任何 `@RequirePermission` 引用**（`raw/01` §4 标注 🟡）→ 绑定了也没有接口能用，是**孤儿码**。

### 1.2 漂移类型二：`permission_type` 类型错位

运行期 `sys_permission` 实际类型（`raw/04b` V1 / V2）：

| 权限码 | 实际 `permission_type` | 前端用途 | 判定 |
|---|---|---|---|
| `commission:batch` | **button** | `router/index.ts:94` 菜单级 `meta.permission` | 🔴 类型错位 |
| `commission:reversal` | **button** | `router/index.ts:95` 菜单级 `meta.permission` | 🔴 类型错位 |
| `commission.rules` | menu | `sys_menu_permission.route_path='/commission/rules'` | ✅ |
| `commission.records` | menu | `'/commission/records'` | ✅ |
| `commission.salary` | menu | `'/commission/salary'` | ✅ |
| `commission.settlement` | menu | `'/commission/settlement'` | ✅ |
| `commission.batch` | menu | `'/commission/batch'` | ✅ |
| `commission.reversal` | menu | `'/commission/reversal'` | ✅ |

> **两套命名并存**：`commission:batch`（冒号，`button`）与 `commission.batch`（点，`menu`）**同时存在**于 `sys_permission`，且**都**被 BOSS/MANAGER/ADMIN 持有。
> 前端 `router/index.ts:94` 用的是**冒号版** `commission:batch`（button），而 `sys_menu_permission` 菜单树用的是**点版** `commission.batch`（menu）→ 菜单可见性与路由可达性由两套不同码控制。

### 1.3 漂移类型三：BOSS 缺 2 个菜单码

`raw/09` H 段实测 `/auth/me/permissions`：

| 账号 | `commission*` 码集合 | 缺项 |
|---|---|---|
| `manager01` | 13 个（含 `commission.batch`、`commission.records`） | — |
| `boss01` | **11 个** | 🔴 缺 `commission.batch`、`commission.records` |
| `clerk01` | 0 个 | — |

→ BOSS 的提成菜单树缺「提成记录」「结算批次」入口（**但路由本身可达**，因为 `router/index.ts:59` 用的是 `commission:record:list`，BOSS 持有该码）。

### 1.4 漂移类型四：超管通配 `*` 的触发条件是复合的

`backend/src/main/java/com/meiyetec/security/RbacContextLoader.java:78-91`：

```java
if (codes.contains("booking") || codes.contains("member") || codes.contains("setting")
        || user.getUsername() != null && "admin".equalsIgnoreCase(user.getUsername())) {
    boolean isSuper = userRoles.stream().anyMatch(r ->
            "SUPER_ADMIN".equals(r.getRoleCode()) || "PLATFORM_ADMIN".equals(r.getRoleCode()));
    if (isSuper) {
        codes.add("*");
        ...
    }
}
```

**两个必要条件**（缺一不可）：
1. `codes` 含 `booking` / `member` / `setting` **或** `username == "admin"`
2. 角色含 `SUPER_ADMIN` 或 `PLATFORM_ADMIN`

运行期实测（`raw/08` W1）：`SUPER_ADMIN` 角色确实持有 `booking` / `member` / `setting` → 条件 1 成立；`admin` 用户名也为 `admin` → **双保险**。
**但**：若运维新建一个 `SUPER_ADMIN` 角色（不绑 `booking`/`member`/`setting`），并分配给用户名非 `admin` 的账号 → **拿不到 `*`** → 该超管只能访问 118 个码里的权限，其余全部 403。

> `SUPER_ADMIN` 角色当前持有权限码数 **118**（`raw/03` E 段）；`ADMIN` 持有 **381**。

### 1.5 库内「有码无接口引用」与「有接口无码」双向差异

| 差异类型 | 清单 | 数量 |
|---|---|---|
| **库有码、无接口引用（孤儿码）** | `commission:list`、`commission:settlement:audit` | 2 |
| **接口有码、无角色绑定（死码）** | 见 I183（9 个）/ I184（24 个） | 33 |
| **前端有码、库中无此码** | `employee:attendance`、`employee:attendance:list`、`employee:leave`、`employee:attendance:stat`、`employee:attendance:config`、`commission:rule`、`commission:rule:config`、`commission:simulation` | 8 |
| **库有码、前端无引用** | `commission:batch`（button 版）、`commission:reversal`（button 版）、`commission.rules` / `.records` / `.salary` / `.settlement`（menu 版走 `sys_menu_permission`） | 6（其中前 2 个与点版并存） |
| **后端有码、前端无入口** | `commission:rule:detail/stat/status`、`record:stat`、`salary:add/adjust/stat/export/batch-confirm/batch-pay`、`settlement:add/detail/stat/confirm/pay/batch-*/export`、`commission:calculate` | 19 |

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | **种子不可信** | 依据 `migrate_r3_0_c_menu_role.sql` 判断「已经配好了」 | 动作类码（add/edit/delete/confirm/pay/audit）**实际一个都没生效** → 运维以为配过、实际全 403 |
| R2 | **动作码静默跳过** | 重新执行该种子脚本 | `commission:list` / `settlement:audit` 是孤儿码，绑了也没接口用 |
| R3 | 冒号版与点版并存 | 前端/菜单各取一套 | `commission:batch`（button）与 `commission.batch`（menu）语义重复；改一个不改另一个 → 菜单与路由可见性脱节 |
| R4 | BOSS 菜单树缺入口 | BOSS 登录 | 看不到「提成记录」「结算批次」菜单（路由可达）→ 功能发现性差 |
| R5 | **新建超管拿不到 `*`** | 新建 `SUPER_ADMIN` 角色 + 非 `admin` 用户名 | 该超管**大面积 403**，且现象与 I183/I184 的死码**完全一样** → 排查极易误判 |
| R6 | 无码表单一来源 | 后续新增模块 | 三处（后端注解 / 前端路由 / 种子 SQL）各自维护 → 漂移必然复发 |

---

## 3. 建议方案（**仅建议，本批不实施**）

| # | 步骤 | 内容 | 备注 |
|---|---|---|---|
| 1 | **定位动作码未生效的真实原因** | 查 `migrate_r3_0_c_menu_role.sql` 中动作码对应的 `INSERT ... SELECT` 写法与过滤条件；确认是否依赖某张表（如 `sys_permission` 中已 `deleted=1` 的行） | 本次**未追到根因**，标 UNVERIFIED |
| 2 | **确定唯一码表并重排种子** | 以服务端 37 码为准出**一份**权威清单；种子、注解、前端三方对齐（与 I185 §3 步骤 4 合并） | 本批**未入库**任何脚本（规则 19） |
| 3 | **清理孤儿码与重复码** | `commission:list` / `settlement:audit` 要么补接口引用、要么删；冒号版 `commission:batch` / `commission:reversal` 与点版二选一 | 需先确认历史引用方 |
| 4 | **补齐 BOSS 的 2 个菜单码** | 参照 MANAGER 补 `commission.batch` / `commission.records` | — |
| 5 | **加固超管通配判定** | 建议：`roleCode ∈ {SUPER_ADMIN, PLATFORM_ADMIN}` **直接**授予 `*`，不再附加「必须持有 booking/member/setting 或用户名 admin」的复合条件 | 会**扩大**超管权限面 → 需业务确认；同时需评估与 I115 的关系 |
| 6 | **补「孤儿码 / 死码 / 缺码」巡检** | 加入守门脚本：① 后端注解码 ⊆ `sys_permission` ② 每个码至少被一个角色绑定 ③ 前端 `meta.permission` ⊆ `sys_permission` ④ 无接口引用的码告警 | 可复用 I115 扫描器 |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | `migrate_r3_0_c_menu_role.sql` 中全部 `commission*` 码 | 运行期绑定数与脚本声明**一致**（动作类不再静默跳过） |
| 2 | `commission:list` / `commission:settlement:audit` | 要么有接口引用、要么已删除（不再有孤儿码） |
| 3 | `SELECT permission_code, permission_type FROM sys_permission WHERE permission_code IN ('commission:batch','commission.batch')` | 不再同时存在冒号版与点版（同义重复） |
| 4 | `boss01` `GET /auth/me/permissions` | 含 `commission.batch`、`commission.records` |
| 5 | 新建 `SUPER_ADMIN` 角色 + 非 `admin` 用户名账号 | 该账号 `GET /auth/me/permissions` 含 `*`（或至少能访问预期接口） |
| 6 | 全量巡检脚本 | 「后端注解码 / 前端路由码 / `sys_permission`」三方差异清单为空或每项有书面理由 |

---

## 5. 边界声明

- 本 Issue 结论来自**静态代码分析**（`RbacContextLoader.java:78-91`、种子 SQL 逐行）+ **本机开发库只读查询**（`sys_permission` / `sys_role_permission` / `sys_role` / `sys_role_data_scope`）+ **运行期 `/auth/me/permissions` 只读 GET**。
- **未修改任何业务代码**、**未执行任何写操作**、**未执行任何 DDL**、**未新增任何入库脚本**、**未创建任何新角色或新账号**。
- ⚠️ 「动作类码为何静默跳过」的**根因未确认**（脚本执行日志不在库内）→ 该子结论标 **UNVERIFIED**。
- ⚠️ 「新建超管拿不到 `*`」为**静态推导 + 复合条件分析**，**未在运行期实际创建超管验证**（创建角色属写操作，本批禁止）→ 标 **UNVERIFIED**。
- 未核实：生产环境 `sys_permission` / `sys_role_permission` 是否与本机开发库同构；生产库是否已执行过 `migrate_r3_0_c_menu_role.sql`。
- 未核实：后端 19 个「有码无前端入口」的码是否有其他消费方（如定时任务、开放接口）。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。

---

**下一步**：与 I183 / I184 / I185 合并为「权限码表收敛 + 绑定」批次；建议先做 §3 步骤 1（根因定位）与步骤 5（超管判定口径确认），再动种子。
