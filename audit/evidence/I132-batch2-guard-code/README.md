# I132 批次 2 · 前端路由守卫码与后端权限注解的运行时对账

> **性质**：证据报告（Evidence Report），非问题单。问题单见 [`audit/issues/I137-guard-code-fake-through.md`](../../issues/I137-guard-code-fake-through.md)。
>
> **绑定**：commit `97b3513`（`feat(menu): 补全 MANAGER 的客户运营/员工提成顶级菜单绑定，修复 13 个守卫码断裂`）/ DB `meiyetec@127.0.0.1:3307`（docker `meiyetec-mysql`，MySQL 8.0.46）/ env `audit/baseline/env.md`
> **工作区状态**：dirty — `frontend/meiyetec-web/src/router/index.ts` 有未提交修改（本批次守卫码修正已生效于运行中 web 容器 bundle）
>
> **编号说明**：本目录沿用预置的 `I132-batch2-guard-code/`（该目录在探测开始前已建立并存放本批次截图）。按规则 12 的 `FXXX-<slug>` 命名要求，本批对应的 Feature 应为 `F132-route-guard-code-alignment`，**待用户确认后是否改名**。
>
> **数据安全**：探测使用 `serviceCode = AUDIT_PROBE_I132`（唯一前缀）与兜底 ID `99999999`；脚本运行前执行存在性检查，命中则 fail-fast。探测产生的 3 行 `biz_service_item` 已清理，表已恢复 22 行 / id 1–22 初始状态。`sys_log` 保留 1 条操作日志（正常审计行为）。

---

## 1. 结论速览

| # | 结论 | 等级 | 证据文件 |
|---|---|---|---|
| 1 | I132 批次 2 修复的 7 条路由，ADMIN / BOSS / MANAGER 三方首屏 API 全部 200，守卫码脱离 `roles=NONE` | ✅ VERIFIED | `i132-batch2-runtime.json` + `shots/` |
| 2 | `biz:service-item:edit` roles=NULL 的真实后果是「**谁都能改**」而非「谁都改不了」 | 🔴 BROKEN（越权写入） | `i132-priv-esc.json` |
| 3 | BOM 弹窗耗材列表恒空，根因是前端调 `/stock/page`（端点不存在 → 404），与权限无关 | 🔴 BROKEN | `i132-leftover-runtime.json` |
| 4 | `package:*` 9 个操作码 ADMIN 独占，且前端按钮无 `v-permission` 守卫 → 菜单可见、按钮可见、点下去 403 | 🔴 BROKEN | `i132-leftover-runtime.json` / `i132-leftover-runtime2.json` |
| 5 | 守卫码 `biz:service-item`（冒号）在 DB 中不存在，3 条路由依赖它；`/care` 无任何菜单行 → 幽灵路由 | 🟡 PARTIAL-BLOCKER | DB 查询 + `router/index.ts:80/81/156` |
| 6 | 菜单 `biz.service-item` 与按钮 `biz:service-item:list` 的角色绑定不一致 → 3 个角色可凭 URL 直达并执行无守卫写操作 | 🔴 BROKEN | DB 查询 + `i132-leftover-runtime3.json` |

---

## 2. 背景：批次 2 的修复内容

`backup/perm-fix-batch2.sql`（9037 字节，2026-11-06 16:17 落盘）针对的是**前端路由守卫码与 DB 权限码不匹配**导致的「假打通」——菜单可见、路由可达、但点进去一律 403。

同批次修正了 `frontend/meiyetec-web/src/router/index.ts` 两处守卫码：

```80:81:frontend/meiyetec-web/src/router/index.ts
      { path: 'dev/care-plan', name: 'DevCarePlan', component: () => import('@/views/dev/CarePlan.vue'), meta: { title: '护肤方案共享套餐', requiresAuth: true, permission: 'biz:service-item' } },
      { path: 'dev/pricelist', name: 'DevPriceList', component: () => import('@/views/dev/PriceList.vue'), meta: { title: '电子价目表卡项套餐', requiresAuth: true, permission: 'biz:service-item' } },
```

```88:90:frontend/meiyetec-web/src/router/index.ts
      { path: 'biz/service-item', name: 'BizServiceItem', component: () => import('@/views/biz/service-item.vue'), meta: { title: '服务项目', requiresAuth: true, permission: 'biz:service-item:list' } },
      { path: 'biz/treatment-plan', name: 'BizTreatmentPlan', component: () => import('@/views/biz/treatment-plan.vue'), meta: { title: '疗程方案', requiresAuth: true, permission: 'biz.treatment-plan' } },
      { path: 'biz/treatment-report', name: 'BizTreatmentReport', component: () => import('@/views/biz/treatment-report.vue'), meta: { title: '效果报告', requiresAuth: true, permission: 'biz.treatment-plan' } },
```

（`biz:service:item` → `biz:service-item:list`；`biz:treatment:plan` → `biz.treatment-plan`）

---

## 3. 结论 1 — 批次 2 的 7 条路由已打通（VERIFIED）

`i132-batch2-runtime.json` 记录 3 角色 × 10 次 API 调用，**全部 HTTP 200 / code 200**：

| 路由 | 首屏 API | 守卫码 | ADMIN | BOSS | MANAGER |
|---|---|---|---|---|---|
| `/market/coupon` | `POST /mkt/coupon/batch/page` | `market:coupon:list` | 200 | 200 | 200 |
| `/market/coupon` | `GET /mkt/coupon/page` | `market:coupon:list` | 200 | 200 | 200 |
| `/market/points` | `GET /mkt/points/rule/list` | `market:point:list` | 200 | 200 | 200 |
| `/market/activity` | `GET /mkt/promotion/page` | `market:activity:list` | 200 | 200 | 200 |
| `/package/list` | `GET /packages` | `package:list` | 200 | 200 | 200 |
| `/package/list` | `GET /packages/stats` | `package:list` | 200 | 200 | 200 |
| `/package/member-orders` | `GET /packages/members/recent-with-orders` | `package:list` | 200 | 200 | 200 |
| `/biz/service-item` | `GET /biz/service/item/page` | `biz:service-item:list` | 200 | 200 | 200 |
| `/biz/service-item` | `GET /biz/service/category/list` | — | 200 | 200 | 200 |
| `/biz/treatment-plan` | `GET /biz/service/item/page?status=1` | `biz.treatment-plan` | 200 | 200 | 200 |

三角色权限集合中 6 个守卫码均为 `true`（admin 走通配 `*`，见 `RbacContextLoader.java:78-88`）。

前端运行时校验：运行中 web 容器的 `index-*.js` bundle 内 `biz:service-item:list` 出现 1 次、`biz.treatment-plan` 出现 2 次 —— **守卫码修正已进入实际部署产物**。

截图见 `shots/`（3 角色 × 7 路由 = 21 张）。

---

## 4. 结论 2 — `biz:service-item:edit` 的真实后果是越权写入（🔴）

### 4.1 SQL 头部注释的判断与实测相反

`backup/perm-fix-batch2.sql:37-40` 的遗留项说明写的是：

```
--   - biz:service-item:edit (1440) roles=NULL -> 谁都改不了（走别名控制器时）
```

**这个描述是错的**，方向反了。

### 4.2 根因：两条并行路径，只有一条有守卫

| | 别名控制器 | 实际服务类 |
|---|---|---|
| 路径 | `/biz/service-item` | `/biz/service/item` |
| 类 | `controller/BizServiceItemController.java:15` | `service/BizServiceItemService.java:32` |
| 守卫注解 | `@RequirePermission("biz:service-item:list" / ":edit")` × 5 | **全文 0 个 `@RequirePermission`** |
| 前端调用 | ❌ 从不调用 | ✅ `views/biz/service-item.vue` 全部 22 处 API 调用都走这里 |

`BizServiceItemService.java` 的全部 22 个映射端点（category / item / material / export / template / import）**没有任何权限注解**：

```32:32:backend/src/main/java/com/meiyetec/service/BizServiceItemService.java
@RequestMapping("/biz/service")
```

```
L44  @GetMapping("/category/list")      L175 @PostMapping("/item")          L287 @GetMapping("/item/export")
L54  @GetMapping("/category/page")      L193 @PutMapping("/item/{id}")      L352 @GetMapping("/item/template")
L71  @PostMapping("/category")          L209 @DeleteMapping("/item/{id}")   L372 @PostMapping("/item/import")
L89  @PutMapping("/category/{id}")      L247 @PostMapping("/item/{id}/material")
L99  @DeleteMapping("/category/{id}")   L277 @DeleteMapping("/item/{id}/material/{productId}")
L112 @GetMapping("/item/page")          L160 @GetMapping("/item/{id}")
```

而别名控制器是**有**守卫的：

```36:50:backend/src/main/java/com/meiyetec/controller/BizServiceItemController.java
    @PostMapping
    @RequirePermission("biz:service-item:edit")
    public Result<?> create(@RequestBody com.meiyetec.entity.BizServiceItem item) {
        ...
    @PutMapping("/{id:\\d+}")
    @RequirePermission("biz:service-item:edit")
    public Result<?> update(@PathVariable Long id, @RequestBody com.meiyetec.entity.BizServiceItem item) {
        ...
    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("biz:service-item:edit")
    public Result<?> delete(@PathVariable Long id) {
```

### 4.3 运行时实测（`i132-priv-esc.json`）

| 步骤 | HTTP | code | msg | 判定 |
|---|---|---|---|---|
| operator01 `POST /biz/service/item` | 200 | 200 | 创建成功 | **ALLOWED** |
| operator01 `PUT /biz/service/item/{id}` | 200 | 200 | 更新成功 | **ALLOWED** |
| operator01 `DELETE /biz/service/item/{id}` | 200 | 200 | 删除成功 | **ALLOWED** |
| operator01 `POST /biz/service-item`（别名） | 403 | 403 | 权限不足: 需要权限码：`biz:service-item:edit` | DENIED |
| admin `POST /biz/service-item`（别名） | 200 | 200 | 创建成功 | ALLOWED（通配 `*`） |

**operator01 全流程创建 → 更新 → 删除成功**，其权限集合中不含 `biz:service-item:edit`、也不含任何菜单 `biz.service-item`。

### 4.4 叠加影响：菜单绑定与按钮绑定不一致

DB 实测（`sys_permission` + `sys_role_permission`）：

| id | permission_code | type | 绑定角色 |
|---|---|---|---|
| 1233 | `biz.service-item`（点号） | menu | ADMIN, BOSS, MANAGER, SUPER_ADMIN |
| 1234 | `biz.treatment-plan` | menu | ADMIN, BOSS, MANAGER, SUPER_ADMIN |
| 1439 | `biz:service-item:list`（冒号） | button | ADMIN, BOSS, **CLERK**, MANAGER, **OPERATOR**, **STORE_ADMIN** |
| 1440 | `biz:service-item:edit` | button | **NULL（无任何角色）** |
| 1575 | `stock:list` | button | ADMIN, MANAGER, STORE_ADMIN |

- `biz:service-item:list` 绑了 CLERK / OPERATOR / STORE_ADMIN，但**菜单 `biz.service-item` 没绑这三个角色** → 这三个角色**没有菜单入口**，却能凭 URL 直达 `/biz/service-item`（路由守卫只校验 `biz:service-item:list`，这三个角色通过）→ 页面加载 → 调用**无守卫**的 `/biz/service/item/**` → **可读可写可删**。
- `biz:service-item:edit` 绑 NULL：若前端走别名路径，任何非 ADMIN 角色都会被 403 —— 这正是 SQL 头部说的「谁都改不了」，但**该路径前端从不使用**，所以这个「保护」保护的是一个没人走的门。

---

## 5. 结论 3 — BOM 弹窗耗材列表恒空（404，不是 403）

### 5.1 前端调用

```1016:1021:frontend/meiyetec-web/src/views/biz/service-item.vue
async function openAddMaterial() {
  addMaterialDialog.visible = true
  if (productList.value.length === 0) {
    const r = await http.get('/stock/page', { params: { pageNum: 1, pageSize: 200, status: 1 } })
    productList.value = r.data?.records || []
  }
}
```

### 5.2 后端无此端点

`controller/StockController.java:44` 的 `@RequestMapping("/stock")` 下**只有** `/stock/products/page`（L70）、`/stock/products/list`（L93）、`/stock/inventory/*`、`/stock/consumption/*` 等，**没有 `/stock/page`**。

### 5.3 运行时实测（`i132-leftover-runtime.json`，group=`stock`）

| 路径 | ADMIN | BOSS | MANAGER | CLERK |
|---|---|---|---|---|
| `GET /stock/page` | **404** | **404** | **404** | **404** |
| `GET /stock/products/page` | 200 | 403 | 200 | 403 |

`/stock/page` 对**所有角色**都是 404 —— 与 `stock:list` 是否授权**完全无关**。

**结论**：SQL 头部「`stock:list` 未给 BOSS → BOM 弹窗 403」的判断不成立。真实根因是**前端调错了端点**，且比 403 更严重（404 = 功能整体失效，ADMIN 也看不到耗材列表）。`/stock/products/page` 需要 `stock:product:list`，该码 BOSS / CLERK 无 → 修复前端调用后仍需同步处理 `stock:product:list` 的角色绑定。

---

## 6. 结论 4 — `package:*` 操作码 ADMIN 独占 + 前端按钮无守卫

### 6.1 DB 实测：9 个操作码全是 ADMIN 独占

| id | permission_code | type | 角色 |
|---|---|---|---|
| 1099 | `package:add` | button | ADMIN |
| 1100 | `package:edit` | button | ADMIN |
| 1101 | `package:delete` | button | ADMIN |
| 1102 | `package:purchase` | button | ADMIN |
| 1103 | `package:redeem` | button | ADMIN |
| 1104 | `package:freeze` | button | ADMIN |
| 1105 | `package:transfer` | button | ADMIN |
| 1106 | `package:audit` | button | **NULL** |
| 1353 | `package:suspend` | API | ADMIN |
| 1438 | `package:refund` | button | ADMIN |

### 6.2 前端按钮无 `v-permission` 守卫

`views/package/index.vue` 与 `views/package/member-orders.vue` 中 **`v-permission` 出现次数 = 0**：

```
member-orders.vue:51  <el-button v-if="memberId" ... @click="purchaseVisible = true">购买套餐</el-button>
member-orders.vue:52  <el-button v-if="memberId" ... @click="openRedeem()">核销套餐</el-button>
```

即：**菜单可见 + 按钮可见 → 点击后端 403**。

### 6.3 运行时实测

`i132-leftover-runtime.json` / `i132-leftover-runtime2.json`：

| 端点 | 需要权限码 | ADMIN | BOSS | MANAGER | CLERK |
|---|---|---|---|---|---|
| `POST /packages` | `package:add` | 404 | **403** | **403** | **403** |
| `POST /packages/purchase` | `package:purchase` | 404 | **403** | **403** | **403** |
| `POST /packages/redeem` | `package:redeem` | 404 | **403** | **403** | **403** |
| `POST /packages/order/{id}/freeze` | `package:freeze` | 404 | **403** | **403** | **403** |
| `POST /packages/order/{id}/suspend` | `package:suspend` | 404 | **403** | **403** | **403** |
| `POST /packages/order/{id}/resume` | `package:suspend` | 404 | **403** | **403** | **403** |
| `POST /packages/order/{id}/refund` | `package:refund` | 404 | **403** | **403** | **403** |
| `POST /packages/order/{id}/transfer` | `package:transfer` | 404 | **403** | **403** | **403** |
| `DELETE /packages/{id}` | `package:delete` | 404 | **403** | **403** | **403** |
| `POST /packages/order/{id}/extend` | `package:edit` | 404 | **403** | **403** | **403** |

ADMIN 一律 404（`99999999` 不存在），非 ADMIN 一律 403 —— 说明**后端守卫本身是生效的**，问题在前端不隐藏按钮。

### 6.4 附带发现：参数校验早于权限判定

`POST /packages` 空 body 时（`i132-leftover-runtime2.json`），BOSS / MANAGER / CLERK 返回的是 **400 参数校验错误**，不是 403：

```json
{ "boss01": { "http": 200, "code": 400, "msg": "套餐名称不能为空; 拆分子项不能为空", "verdict": "ERROR" } }
```

同理 `POST /packages/redeem-split` 4 个角色全部 400「拆分子项不能为空」。说明 **`@Valid` 参数校验先于 `@RequirePermission` 切面执行** —— 对无权限调用方泄露了参数校验细节，属于次要信息泄露（低优先级）。

---

## 7. 结论 5 — `biz:service-item`（冒号）不存在 → 3 条路由依赖幽灵码 + `/care` 幽灵路由

### 7.1 DB 中只有点号版本

`sys_permission` 中 `biz:service-item`（冒号）**不存在**；只有 `biz.service-item`（点号，id 1233，menu）。

### 7.2 但 3 条路由用它做守卫码

| 行号 | 路由 | 守卫码 | DB 存在？ | 菜单角色 |
|---|---|---|---|---|
| `router/index.ts:80` | `/dev/care-plan` | `biz:service-item` | ❌ | `dev.care-plan`(1288) = ADMIN, SUPER_ADMIN |
| `router/index.ts:81` | `/dev/pricelist` | `biz:service-item` | ❌ | `dev.pricelist`(1287) = ADMIN, SUPER_ADMIN |
| `router/index.ts:156` | `/care` | `biz:service-item` | ❌ | **无任何菜单行** |

前端守卫逻辑：

```229:230:frontend/meiyetec-web/src/router/index.ts
    if (need && !safePath && !store.hasPermission(need)) {
      next({ path: '/403', query: { denied: need } })
```

`hasPermission` 精确匹配 + 通配 `*`：

```62:65:frontend/meiyetec-web/src/stores/permission.ts
    hasPermission: (state) => (code: string) => {
      if (!code) return true
      return state.permissions.includes(code) || state.permissions.includes('*')
    },
```

### 7.3 后果

- **ADMIN / SUPER_ADMIN**：权限集合含 `*` → 守卫通过 → 3 条路由可达。
- **BOSS / MANAGER / CLERK / OPERATOR / STORE_ADMIN**：不含 `biz:service-item`，也没有 `*` → **一律 403**。
- `/care` 路由在 `sys_permission` 中**无对应菜单行** → 菜单不显示 + 路由被幽灵守卫码拦住 → **幽灵路由**（规则 10）。
- `/dev/care-plan`、`/dev/pricelist` 的菜单绑定 ADMIN/SUPER_ADMIN，与「仅 ADMIN/SUPER_ADMIN 可达」的守卫行为**恰好一致**，属于「因错得对」，但守卫码本身仍是错的。

---

## 8. 结论 6 — 菜单与守卫码绑定不一致导致越权读取面（附带）

`GET /biz/service/item/page` **无 `@RequirePermission`**，`i132-leftover-runtime3.json` 实测 5 个角色**全部 200**：

| 角色 | `GET /biz/service/item/page` |
|---|---|
| ADMIN / BOSS / MANAGER / CLERK / OPERATOR | 200 操作成功 |

即：任意已认证用户都能分页读取全部服务项目（名称、编码、价格等）。相较结论 2 的写越权，此项为**读越权**，严重度次一级但同源。

---

## 9. 证据文件清单

| 文件 | 内容 |
|---|---|
| `i132-batch2-runtime.json` | 3 角色 × 10 次首屏 API 探测（结论 1） |
| `i132-leftover-runtime.json` | 17 组遗留项探测：package / biz-service-item / stock 三组（结论 3、4、5） |
| `i132-leftover-runtime2.json` | 4 组 package 端点 × 4 角色（结论 4 + 参数校验顺序） |
| `i132-leftover-runtime3.json` | 4 组 `/biz/service/item/**` × 5 角色（结论 6） |
| `i132-priv-esc.json` | operator01 全流程越权创建/更新/删除（结论 2） |
| `i132-*.js` | 对应探测脚本（**非正式工具**，不进入 `audit/scripts/`，不接入 CI） |
| `shots/` | 3 角色 × 7 路由 = 21 张浏览器截图 |

脚本头部均已标注「非正式工具，仅为调查证据，不属于 `audit/scripts/` 正式守门序列」。

---

## 10. 未确认项

1. **生产环境权限绑定是否与本机一致** —— 本次探测只针对 `127.0.0.1:3307` 的本机 Docker 库，**未连接生产库**（规则 9）。
2. **`/biz/service/item/**` 无注解是否为有意设计** —— 未找到 PRD 或 EXT-* Operational Notes 授权该路径免鉴权，**按越权处理**，标注「未确认设计意图」。
3. **`package:audit`（roles=NULL）是否有对应后端端点** —— 未在本次探测范围内确认，标注「未确认」。
4. **`/care` 路由的业务归属** —— `sys_permission` 无菜单行，前端 `views/care/index.vue` 存在但可达性未确认，标注「未确认」。
5. **`stock:product:list` 是否应授权给 BOSS / CLERK** —— 属业务决策，未确认。

---

## 11. 与既有结论的关系

| 既有条目 | 关系 |
|---|---|
| **I115**（权限注解覆盖率不足，状态 Fixed） | 本报告结论 2 说明**该修复未覆盖 `BizServiceItemService`** —— 该类仍是 0 注解。建议在 I115 上追加「部分回归」说明，或由 I137 独立承载。 |
| **I124**（权限码体系分裂：点号 vs 冒号） | 结论 5 是 I124 的又一处实例：`biz.service-item` vs `biz:service-item` |
| **I130**（菜单顶级守卫码修复） | 同类问题的不同批次实例 |
| **I132**（收银台 tenderedAmount） | 编号冲突：I132 已被 `I132-pos-checkout-missing-tendered-ui.md` 占用；`backup/perm-fix-batch2.sql:38` 引用的 `audit/issues/I132-guard-code-fake-through.md` **实际不存在** → 本批问题单改用 **I137** |
