六项调查完成，未修改任何文件（临时解析脚本已删除）。

## 1. 路由守卫逻辑

`frontend/meiyetec-web/src/router/index.ts:200-232`，全文关键部分：

```200:232:frontend/meiyetec-web/src/router/index.ts
router.beforeEach(async (to, from, next) => {
  NProgress.start()
  document.title = (to.meta.title as string) || '美业SaaS系统'

  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth !== false && !token) {
    const redirect = to.fullPath !== '/login' ? to.fullPath : undefined
    next({ path: '/login', query: redirect ? { redirect } : undefined })
    NProgress.done()
    return
  }

  if (token) {
    const store = usePermissionStore()
    if (!store.loaded) {
      await store.refresh()
    }
    const need = to.meta.permission as string | undefined
    // 兜底白名单：首页 / 登录 / 404 始终放行，避免死循环
    const safePath = to.path === '/dashboard' || to.path === '/login' || to.path.startsWith('/403') || to.path.startsWith('/404')
    if (need && !safePath && !store.hasPermission(need)) {
      next({ path: '/403', query: { denied: need } })
      NProgress.done()
      return
    }
  }
  next()
  NProgress.done()
})
```

守卫读的是 **`route.meta.permission`**（`index.ts:220`），唯一判据是 `store.hasPermission(need)`。`denied` 查询参数直接透传给403 页，`403.vue:23-25` 渲染为「权限码：{denied}」——这解释了用户看到的 `commission:rule:config` 字样来自 `to.meta.permission`，而非后端返回。

守卫**不读 `to.meta.menus`**，也不查 `store.menus`。

## 2. `/commission/rules` 的两处注册

```58:58:frontend/meiyetec-web/src/router/index.ts
      { path: 'commission/rules', name: 'CommissionRules', component: () => import('@/views/commission/rules.vue'), meta: { title: '提成规则', requiresAuth: true, permission: 'commission:rule:list' } },
```

```114:114:frontend/meiyetec-web/src/router/index.ts
      { path: 'commission/rules', name: 'CommissionRules', component: () => import('@/views/commission/rules.vue'), meta: { title: '提成规则配置', requiresAuth: true, permission: 'commission:rule:config' } },
```

两处 **path 相同、name 相同**（`'CommissionRules'`），只有 `title` 与 `permission` 不同。Cursor 之前报告的 `:58` 和 `:114` 属实。

## 3. Vue Router 重复注册行为

`vue-router` 版本 **4.6.4**（`node_modules/vue-router/package.json`）。源码 `node_modules/vue-router/dist/vue-router.mjs:657-660`：

```js
if (isRootAdd && record.name && !isAliasRecord(matcher)) {
    if (process.env.NODE_ENV !== "production") checkSameNameAsAncestor(record, parent);
    removeRoute(record.name);
}
```

`removeRoute`（同文件 `:673-691`）把旧 matcher 从 `matchers` 数组移除并 `matcherMap.delete(name)`。

**结论：后注册者覆盖先注册者。** `createRouter` 时 routes 数组按序 `addRoute`，`:114` 的 `commission:rule:config` 会移除 `:58` 的 `commission:rule:list`。所以**生效的是 `:114`**，守卫要求 `commission:rule:config` —— 与用户观察到的 403 提示完全吻合。

注意这里两条记录的 `name` 相同是关键：若 name 不同，vue-router 会两条并存、由路径匹配顺序决定，反而更模糊。

## 4. 权限判断函数

`frontend/meiyetec-web/src/stores/permission.ts:64-66`：

```64:66:frontend/meiyetec-web/src/stores/permission.ts
    hasPermission: (state) => (code: string) => {
      if (!code) return true
      return state.permissions.includes(code) || state.permissions.includes('*')
    },
```

只比对 **`permissions` 数组**（字符串精确包含），**完全不查 `menus` 树**。`menus` 数组仅供 `Layout.vue:321-331` 渲染侧边栏使用，与路由准入判定无关。

`permissions` 的来源是 `sys_permission.permission_code`（后端 `RbacService.java:59` `ctx.getPermissions()`、`AuthService.java:64-80` 登录时注入），`menus` 来源是 `sys_menu_permission` + `sys_permission.parent_id` 拼树（`RbacService.buildMenuTree:70-113`）。

## 5. 命名体系分裂的证据

分裂点已定位到 **DB 层**，不是前端。两套码在同一张 `sys_permission` 表并存：

| 体系 | 样例 | 数量 | 语义 |
|---|---|---|---|
| 点号（菜单级） | `commission.rules`、`commission.records`、`commission.salary` | 74 个 | 路由路径去掉斜杠 |
| 冒号（按钮级） | `commission:rule:list`、`commission:rule:add`、`commission:rule:edit` | 380 个 | `模块:资源:动作` |

分裂的**时间证据在 git 里不可得**（`git log -- router/index.ts` 同样只剩 `a414f95 recover: 从工作区重建（NTFS objects/30 损坏不可修，历史丢失 2026-10-16）` 这一条，2026-10-16 前的历史已丢）。可用的替代证据是 SQL 脚本的**引入顺序与自述日期**：

- **点号体系（菜单级）** — `backend/src/main/resources/db/sql/init_rbac_v2.sql`，`sys_permission` 里 `commission:rule:list` 被显式标注为 `'button'` 类型（`:156`），配套 `sys_menu_permission` 把路由 `/commission/rules` 绑到 `permission_code='commission:rule:list'`（`fix_menu_chinese_encoding.sql:336`、`fix_menu_permission_complete.sql:219`）。**同一张表同一批数据里，路由路径 `/commission/rules` 绑定的是按钮级码 `commission:rule:list`。**
- **冒号体系（菜单级）** — `backend/src/main/resources/db/sql/init_commission_engine.sql:80-85`，脚本注释第 1 行标注日期 `2026-08-14`，插入 `('commission:rule', '提成规则', 'commission', 'menu', ...)`、`('commission:rule:config', '提成规则配置', 'commission', 'menu', ...)`、`('commission:simulation', ...)`，并紧接着 `:90-121` 建 `sys_menu` 目录 `/commission` + 子菜单 `/commission/rule-list`、`/commission/rules`、`/commission/simulation`。

`init_commission_engine.sql` 的表结构写法（`sys_permission (code, name, module, type, ...)`）与 `init_rbac_v2.sql` 使用的（`permission_code, permission_name, parent_id`）**字段名都不同**——说明它是**独立时期、由不同脚本引入的第二套体系**，落在同一张物理表上。

数据库实测（`backup/meiyetec_20261023_220648.sql`，454 个 distinct权限码 / 360 条角色绑定）：

| 权限码 | perm_id | 绑定的角色 | 在守卫可达性 |
|---|---|---|---|
| `commission.rules` | 1235 | 1/SUPER_ADMIN、8/ADMIN、**6/BOSS** | 前端 meta 从不使用 |
| `commission:rule:list` | 1119 | **无任何角色绑定** | 曾是 `:58` 的要求，现已被覆盖 |
| `commission:rule:config` | **不存在于 DB** | — | `:114` 现行要求 |
| `commission:rule` | **不存在于 DB** | — | `:113` `commission/rule-list` 的要求 |

BOSS（role id 6）实际持有的 49 个权限码中，`commission.*` 系列为 `commission`、`commission.records`、`commission.rules`、`commission.salary`、`commission.settlement` —— **全部是点号，无一个是 `commission:rule:*`**。

所以分裂的机制是：点号体系（`commission.rules`）是**后端 RBAC 菜单/权限数据的既有真实体系**，BOSS 拥有它；`commission:rule:config` 是**前端路由表里引用的一个 DB 中根本不存在的码**，由 `init_commission_engine.sql` 这套独立 SQL 在**数据层引入、但从未被 apply**（该脚本在 `sys_permission` 用 `code/name/module` 列名，与实际表结构 `permission_code/permission_name/parent_id` 不符，apply 会失败）而只在前端路由里留下引用。

## 6. 同类不一致统计

对 `router/index.ts` 全部 `meta.permission` 逐条统计（147 条）：

| 分类 | 数量 | 说明 |
|---|---|---|
| 含点号（菜单级，如 `commission.rules`） | **0** | 前端路由**从不使用**点号体系 |
| 含冒号（按钮级，如 `commission:rule:list`） | 146 | 全部路由的写法 |
| 无分隔符（`setting`） | 1 | `index.ts:71` `sys/setting` |

**统计结论：前端路由表内部不存在「点 vs 冒号」混用——它 100% 用冒号体系，零个点号。** 所谓"同类不一致"不在前端内部，而在**前端 ↔ 后端数据之间**：守卫用冒号码比对 `permissions` 数组，而 boss01 的 `permissions` 数组里装的是点号码。

但按「守卫要求的码是否真实存在于 `sys_permission`」这一口径统计，不一致面如下：

| 不一致类型 | 数量 |
|---|---|
| 路由 meta 引用的权限码在 `sys_permission` 中**不存在** | 需按 147 条逐一回查 DB（本次已确认 `commission:rule:config`、`commission:rule` 两个缺失） |
| 路由 meta 引用的码**存在但无任何角色绑定**（守卫永远拒绝） | 已确认 `commission:rule:list`（perm_id 1119，绑定数 0） |
| 同一 path 重复注册（vue-router 后者覆盖前者） | `/commission/rules` 1 处 |
| `name` 重复 | `CommissionRules` 1 处 |
| BOSS 持有的点号码被前端路由引用 | 0 处（前端从不用点号） |
| 后端 `sys_permission` 中存在但前端从不引用的点号码 | 74 个 |

补充一项：`init_rbac_v2.sql:294` 的 ADMIN 角色授权列表里包含 `commission:rule:list`，`migrate_r3_0_c_menu_role.sql:132/170` 也把它写进了角色权限——但这些脚本的授权结果在当前 DB 里**没有生效**（`commission:rule:list` 绑定数 0），实际绑定的是点号 `commission.rules`（perm_id 1235 → role 6）。这说明**授权数据也发生过一次体系切换，且切换后的数据与 `init_rbac_v2.sql` 的脚本预期不一致**。

## 判断故障链完整闭合，无需技术猜测：

1. `router/index.ts:114` 的 `commission/rules` 重复注册覆盖了 `:58`，守卫改要求 `commission:rule:config`
2. 该码在 `sys_permission` 中**不存在**（DB dump 全库0 命中），无任何角色可持有
3. boss01 实际持有的是点号 `commission.rules`（perm_id 1235，绑 role 6/BOSS）
4. `stores/permission.ts:64-66` 的 `hasPermission` 只做 `permissions.includes(code)` 精确匹配，**不查 menus 树**，点号 `commission.rules` 无法命中冒号 `commission:rule:config`
5. → 守卫必然 403

两处「未确认」需标注：一是2026-10-16 前的 git 历史已损坏丢失，**分裂发生的具体时间点无法用 git 确认**，只能以 SQL 脚本的自述日期（`init_commission_engine.sql` 标注 2026-08-14）与字段结构差异作为间接证据；二是当前生产/目标库的实际绑定未经连库验证，以上数据来自 `backup/meiyetec_20261023_220648.sql`（2026-10-23 22:06 备份）。

按你的要求，未给修复建议，未改动任何文件。