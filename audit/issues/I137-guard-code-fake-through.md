# I137 — 前端路由守卫码与后端权限注解的系统性错配（越权写入 + 假打通并存）

> **编号说明**：用户 2026-11-06 指定沿用 I132 批次 2 语境；但 `I132` 已被 `audit/issues/I132-pos-checkout-missing-tendered-ui.md` 占用，且 `backup/perm-fix-batch2.sql:38` 引用的 `audit/issues/I132-guard-code-fake-through.md` **实际不存在** → 本问题单改用 **I137**（与 I136 同 precedent）。
>
> **状态**：Open（🔴 高）
> **证据**：`audit/evidence/I132-batch2-guard-code/`（含 5 份运行时探测 JSON + 21 张截图）
> **绑定**：commit `97b3513` / DB `meiyetec@127.0.0.1:3307`（MySQL 8.0.46）/ env `audit/baseline/env.md`
> **关联**：I115（权限注解覆盖率，状态 Fixed 但本类未覆盖）、I124（点号 vs 冒号分裂）、I130（菜单守卫码）、I128（DB 编码污染）

---

## 1. 一句话总结

**同一个 `/biz/service-item` 页面下并存两个问题，且方向相反**：后端**有**守卫的那条路径前端从不调用（所以「谁都改不了」是伪保护），前端**实际调用**的那条路径后端**完全没有**守卫（所以「谁都能改」是真实越权）。

---

## 2. 问题清单

| 编号 | 问题 | 严重度 | 状态 |
|---|---|---|---|
| **P0** | `BizServiceItemService`（`/biz/service/item/**`）全文 0 个 `@RequirePermission` → operator01 可创建/更新/删除服务项目 | 🔴 高 | **Fixed**（2026-11-06，15 端点全覆盖 + operator01 实测 403，见 §12） |
| **P1** | 菜单 `biz.service-item` 只绑 ADMIN/BOSS/MANAGER，但按钮 `biz:service-item:list` 绑了 CLERK/OPERATOR/STORE_ADMIN → 这 3 个角色无菜单入口却可凭 URL 直达并执行无守卫写操作 | 🔴 高 | Open |
| **P2** | `GET /biz/service/item/page` 无守卫 → 任意已认证用户可读全量服务项目（读越权） | 🟡 中 | **Fixed**（随 P0 覆盖，现需 `biz:service-item:list`） |
| **P3** | 守卫码 `biz:service-item`（冒号）在 DB 中不存在，但被 `router/index.ts:80/81/156` 三条路由使用；`/care` 在 `sys_permission` 中无任何菜单行 → 幽灵路由 | 🟡 中 | Open |
| **P4** | `package:*` 9 个操作码 ADMIN 独占，且 `views/package/index.vue` / `member-orders.vue` 的操作按钮 `v-permission` 出现次数 = 0 → 菜单可见、按钮可见、点击必 403 | 🔴 高 | Open |
| **P5** | 前端 BOM 弹窗调 `/stock/page`（`StockController` 无此端点）→ HTTP 404，耗材列表恒空，与权限无关 | 🔴 高 | Open |
| **P6** | `@Valid` 参数校验早于 `@RequirePermission` 切面 → 无权限调用方可探测参数校验细节 | 🟢 低 | Open |

---

## 3. P0 — 服务项目越权写入（最高优先级）

### 3.1 根因：双路径，只有一条有守卫

| | 别名控制器 | 实际服务类 |
|---|---|---|
| 路径 | `/biz/service-item` | `/biz/service/item` |
| 类 | `backend/src/main/java/com/meiyetec/controller/BizServiceItemController.java:15` | `backend/src/main/java/com/meiyetec/service/BizServiceItemService.java:32` |
| 守卫注解 | `@RequirePermission("biz:service-item:list")` × 2、`("biz:service-item:edit")` × 3 | **0 个** |
| 前端调用 | ❌ 从不调用 | ✅ `views/biz/service-item.vue` 22 处调用全部走这里 |

`BizServiceItemService` 的 22 个映射端点全部裸奔：

```
L44  @GetMapping("/category/list")              L175 @PostMapping("/item")
L54  @GetMapping("/category/page")              L193 @PutMapping("/item/{id}")
L71  @PostMapping("/category")                  L209 @DeleteMapping("/item/{id}")
L89  @PutMapping("/category/{id}")              L247 @PostMapping("/item/{id}/material")
L99  @DeleteMapping("/category/{id}")           L277 @DeleteMapping("/item/{id}/material/{productId}")
L112 @GetMapping("/item/page")                  L287 @GetMapping("/item/export")
L160 @GetMapping("/item/{id}")                  L352 @GetMapping("/item/template")
                                               L372 @PostMapping("/item/import")
```

### 3.2 运行时实测（`audit/evidence/I132-batch2-guard-code/i132-priv-esc.json`）

| 步骤 | HTTP | code | msg | 判定 |
|---|---|---|---|---|
| operator01 `POST /biz/service/item` | 200 | 200 | 创建成功 | **ALLOWED** |
| operator01 `PUT /biz/service/item/{id}` | 200 | 200 | 更新成功 | **ALLOWED** |
| operator01 `DELETE /biz/service/item/{id}` | 200 | 200 | 删除成功 | **ALLOWED** |
| operator01 `POST /biz/service-item`（别名） | 403 | 403 | 权限不足: 需要权限码：`biz:service-item:edit` | DENIED |
| admin `POST /biz/service-item`（别名） | 200 | 200 | 创建成功 | ALLOWED（通配 `*`） |

operator01 权限集合中**不含** `biz:service-item:edit`、**不含**菜单 `biz.service-item`。

### 3.3 为什么 SQL 头部的判断是错的

`backup/perm-fix-batch2.sql:37-40` 写：

```
--   - biz:service-item:edit (1440) roles=NULL -> 谁都改不了（走别名控制器时）
```

`roles=NULL` 确实让别名路径对所有非 ADMIN 关闭，但**该路径前端从不使用**。它保护的是一扇没人走的门；真正在用的门没有锁。

### 3.4 修复建议（**未实施**，等决策）

1. **推荐**：在 `BizServiceItemService` 的写方法上加 `@RequirePermission("biz:service-item:edit")`、读方法加 `@RequirePermission("biz:service-item:list")`，与别名控制器对齐。**同时**把 `biz:service-item:edit`（id 1440）绑定到应有角色（至少 ADMIN/MANAGER）。
2. **备选**：删除别名控制器 `BizServiceItemController`，前端统一走 `/biz/service/item/**` 并由 Service 层承担守卫 —— 消除双路径根源。但需确认是否有外部调用方依赖 `/biz/service-item`。
3. **不建议**：仅把 `biz:service-item:edit` 绑角色了事 —— 那只是把「伪保护」变成「真误伤」，前端走的路径依然无守卫。

> 注：本问题单**不含任何业务代码修改**（规则 6）。上述修复均需用户明确授权后另行实施。

---

## 4. P1 — 菜单绑定与守卫码绑定不一致

DB 实测：

| id | permission_code | type | 绑定角色 |
|---|---|---|---|
| 1233 | `biz.service-item`（点号，菜单） | menu | ADMIN, BOSS, MANAGER, SUPER_ADMIN |
| 1439 | `biz:service-item:list`（冒号，按钮） | button | ADMIN, BOSS, **CLERK**, MANAGER, **OPERATOR**, **STORE_ADMIN** |
| 1440 | `biz:service-item:edit` | button | **NULL** |

路由守卫只校验 `biz:service-item:list`：

```88:88:frontend/meiyetec-web/src/router/index.ts
      { path: 'biz/service-item', name: 'BizServiceItem', component: () => import('@/views/biz/service-item.vue'), meta: { title: '服务项目', requiresAuth: true, permission: 'biz:service-item:list' } },
```

→ CLERK / OPERATOR / STORE_ADMIN **无菜单入口**，但**凭 URL 直达可达** → 页面加载 → 调用无守卫的写端点 → 可增删改服务项目。

**修复建议**：菜单 `biz.service-item` 与按钮 `biz:service-item:list` 的角色集合必须一致（二选一：给菜单补 CLERK/OPERATOR/STORE_ADMIN，或从按钮里移除它们）。

---

## 5. P2 — 服务项目读取越权

`GET /biz/service/item/page` 无 `@RequirePermission`，实测 5 个角色全部 200（`i132-leftover-runtime3.json`）：任意已认证用户可分页读取全部服务项目（编码、名称、价格）。

与 P0 同源，修复 P0 时一并覆盖。

---

## 6. P3 — 幽灵守卫码 + 幽灵路由

### 6.1 DB 中 `biz:service-item`（冒号）不存在

只有 `biz.service-item`（点号，id 1233）。

### 6.2 但 3 条路由用它做守卫码

| 位置 | 路由 | 守卫码 | DB 存在？ | 对应菜单及角色 |
|---|---|---|---|---|
| `router/index.ts:80` | `/dev/care-plan` | `biz:service-item` | ❌ | `dev.care-plan`(1288) = ADMIN, SUPER_ADMIN |
| `router/index.ts:81` | `/dev/pricelist` | `biz:service-item` | ❌ | `dev.pricelist`(1287) = ADMIN, SUPER_ADMIN |
| `router/index.ts:156` | `/care` | `biz:service-item` | ❌ | **无任何菜单行** |

守卫为精确匹配（`stores/permission.ts:62-65`），`/care` 因此对所有非 `*` 用户 403 —— 菜单不显示 + 路由进不去 = **幽灵路由**（规则 10）。

### 6.3 修复建议

把三处守卫码改为与菜单同源的点号码 `biz.service-item`（id 1233）；同时为 `/care` 补菜单行或删除该路由。

---

## 7. P4 — 套餐操作按钮无守卫

### 7.1 DB 实测：9 个操作码 ADMIN 独占

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

### 7.2 前端按钮无 `v-permission`

`views/package/index.vue` 与 `views/package/member-orders.vue` 中 `v-permission` 出现次数 = **0**：

```
member-orders.vue:51  <el-button v-if="memberId" type="success" :icon="ShoppingCart" @click="purchaseVisible = true">购买套餐</el-button>
member-orders.vue:52  <el-button v-if="memberId" type="primary" :icon="Plus" @click="openRedeem()">核销套餐</el-button>
```

### 7.3 实测：菜单可见 → 按钮可见 → 点击 403

| 端点 | 权限码 | ADMIN | BOSS | MANAGER | CLERK |
|---|---|---|---|---|---|
| `POST /packages` | `package:add` | 404 | 403 | 403 | 403 |
| `POST /packages/purchase` | `package:purchase` | 404 | 403 | 403 | 403 |
| `POST /packages/redeem` | `package:redeem` | 404 | 403 | 403 | 403 |
| `POST /packages/order/{id}/freeze` | `package:freeze` | 404 | 403 | 403 | 403 |
| `POST /packages/order/{id}/suspend` | `package:suspend` | 404 | 403 | 403 | 403 |
| `POST /packages/order/{id}/resume` | `package:suspend` | 404 | 403 | 403 | 403 |
| `POST /packages/order/{id}/refund` | `package:refund` | 404 | 403 | 403 | 403 |
| `POST /packages/order/{id}/transfer` | `package:transfer` | 404 | 403 | 403 | 403 |
| `DELETE /packages/{id}` | `package:delete` | 404 | 403 | 403 | 403 |
| `POST /packages/order/{id}/extend` | `package:edit` | 404 | 403 | 403 | 403 |

后端守卫**本身生效**，问题在前端不隐藏按钮。

**修复建议**：① 给按钮加 `v-permission`（前端只对 ADMIN 显示，UX 一致）；② 或按业务角色把操作码授权给 MANAGER/BOSS。前者成本低且不改权限模型。

---

## 8. P5 — BOM 弹窗耗材列表恒空（404）

### 8.1 前端调用

```1016:1021:frontend/meiyetec-web/src/views/biz/service-item.vue
async function openAddMaterial() {
  addMaterialDialog.visible = true
  if (productList.value.length === 0) {
    const r = await http.get('/stock/page', { params: { pageNum: 1, pageSize: 200, status: 1 } })
    productList.value = r.data?.records || []
  }
}
```

### 8.2 后端无此端点

`controller/StockController.java:44` 的 `/stock` 下只有 `/stock/products/page`（L70）、`/stock/products/list`（L93）、`/stock/inventory/*`、`/stock/consumption/*` 等，**无 `/stock/page`**。

### 8.3 实测

| 路径 | ADMIN | BOSS | MANAGER | CLERK |
|---|---|---|---|---|
| `GET /stock/page` | **404** | **404** | **404** | **404** |
| `GET /stock/products/page` | 200 | 403 | 200 | 403 |
| `GET /stock/consumption/bom/{id}` | 200 | 403 | 200 | 403 |

`/stock/page` 对所有角色 404 —— 与 `stock:list`（id 1575 = ADMIN/MANAGER/STORE_ADMIN）是否授权无关。SQL 头部「`stock:list` 未给 BOSS → 403」的判断**不成立**，真实根因是前端调错端点。

**修复建议**：前端改调 `/stock/products/page`；同时确认 `stock:product:list` 对 BOSS/CLERK 的授权（该码 BOSS/CLERK 无 → 修复端点后仍 403）。

---

## 9. P6 — 参数校验早于权限判定（信息泄露，低优先级）

`POST /packages` 空 body 时，**无权限**的 BOSS/MANAGER/CLERK 返回 **400**（"套餐名称不能为空; 拆分子项不能为空"）而非 403；`POST /packages/redeem-split` 4 角色全部 400「拆分子项不能为空」。

说明 `@Valid` 参数校验先于 `@RequirePermission` 切面执行，无权限调用方可据此探测后端参数约束。建议在切面中把权限判定提到参数校验之前，或在无权限时直接短路。

---

## 10. 未确认项

1. **生产库权限绑定是否与本机一致** —— 本次只测 `127.0.0.1:3307` 本机 Docker 库，**未连接生产库**（规则 9）。
2. **`/biz/service/item/**` 无注解是否为有意设计** —— 未找到 PRD 或 EXT-* Operational Notes 授权该路径免鉴权，标注「未确认」，按越权处理。
3. **`package:audit`（roles=NULL）是否有对应后端端点** —— 未确认。
4. **`/care` 路由的业务归属与 `views/care/index.vue` 的可达性** —— `sys_permission` 无菜单行，标注「未确认」。
5. **`stock:product:list` 是否应授权 BOSS / CLERK** —— 业务决策，未确认。
6. **`package:*` 操作码是否应下放给 MANAGER / BOSS** —— 业务决策，未确认。

---

## 11. 证据清单

| 文件 / 位置 | 说明 |
|---|---|
| `backend/src/main/java/com/meiyetec/service/BizServiceItemService.java:32-380` | 22 个端点，0 个 `@RequirePermission` |
| `backend/src/main/java/com/meiyetec/controller/BizServiceItemController.java:15-52` | 别名控制器，5 个 `@RequirePermission`（前端不使用） |
| `backend/src/main/java/com/meiyetec/security/PermissionAspect.java:44-58` | `*` 通配放行逻辑 |
| `backend/src/main/java/com/meiyetec/security/TenantContext.java:67-78` | `hasPermission` / `hasAllPermissions` 精确匹配 |
| `backend/src/main/java/com/meiyetec/security/RbacContextLoader.java:78-88` | SUPER_ADMIN 加 `*` |
| `frontend/meiyetec-web/src/router/index.ts:80/81/88/89/90/156/229-230` | 守卫码与 403 跳转 |
| `frontend/meiyetec-web/src/stores/permission.ts:62-65` | 前端 `hasPermission` 精确匹配 |
| `frontend/meiyetec-web/src/views/biz/service-item.vue:818-1282` | 22 处 API 调用全走 `/biz/service/item/**` |
| `frontend/meiyetec-web/src/views/biz/service-item.vue:1019` | `/stock/page` 错误调用 |
| `frontend/meiyetec-web/src/views/package/index.vue` / `member-orders.vue:51-52` | 操作按钮无 `v-permission` |
| `backend/src/main/java/com/meiyetec/controller/StockController.java:44-70` | `/stock` 无 `/stock/page` |
| `backup/perm-fix-batch2.sql:37-40` | 头部遗留项判断（已被本报告推翻） |
| `audit/evidence/I132-batch2-guard-code/` | 5 份探测 JSON + 5 份脚本 + 21 张截图 |

**绑定快照**：commit `97b3513` / DB `meiyetec@127.0.0.1:3307`，MySQL 8.0.46 / env `audit/baseline/env.md`

---

## 12. P0 修复记录（2026-11-06）

### 12.1 改动内容

| 位置 | 改动 |
|---|---|
| `backend/src/main/java/com/meiyetec/service/BizServiceItemService.java` | 15 个映射端点全部补 `@RequirePermission`：写方法 7 个用 `biz:service-item:edit`，读方法 8 个用 `biz:service-item:list` |
| `backend/src/main/resources/db/sql/migrate_i137_service_item_edit_permission.sql` | 新增迁移脚本：把 `biz:service-item:edit`（id 1440）绑定给 ADMIN / BOSS / MANAGER / SUPER_ADMIN（与菜单 `biz.service-item` id 1233 的角色集合对齐） |

> `biz:service-item:edit` 此前**零角色绑定**（`sys_role_permission` 中 permission_id=1440 记录数 = 0）。若只加注解不绑角色，菜单可见的 ADMIN/BOSS/MANAGER 会一律 403 —— 那正是本报告 3.4 节第 3 条所警告的「伪保护变真误伤」。因此绑定与注解同批实施。

### 12.2 端点 → 权限码对照（15 个映射端点）

| # | 端点 | 权限码 |
|---|---|---|
| 1 | `GET /biz/service/category/list` | `biz:service-item:list` |
| 2 | `GET /biz/service/category/page` | `biz:service-item:list` |
| 3 | `POST /biz/service/category` | `biz:service-item:edit` |
| 4 | `PUT /biz/service/category/{id}` | `biz:service-item:edit` |
| 5 | `DELETE /biz/service/category/{id}` | `biz:service-item:edit` |
| 6 | `GET /biz/service/item/page` | `biz:service-item:list` |
| 7 | `GET /biz/service/item/{id}` | `biz:service-item:list` |
| 8 | `POST /biz/service/item` | `biz:service-item:edit` |
| 9 | `PUT /biz/service/item/{id}` | `biz:service-item:edit` |
| 10 | `DELETE /biz/service/item/{id}` | `biz:service-item:edit` |
| 11 | `POST /biz/service/item/{id}/material` | `biz:service-item:edit` |
| 12 | `DELETE /biz/service/item/{id}/material/{productId}` | `biz:service-item:edit` |
| 13 | `GET /biz/service/item/export` | `biz:service-item:list` |
| 14 | `GET /biz/service/item/template` | `biz:service-item:list` |
| 15 | `POST /biz/service/item/import` | `biz:service-item:edit` |

> 原报告「22 个端点」为**前端 API 调用点数**（`views/biz/service-item.vue:818-1282` 共 22 处调用，含同一端点的重复调用）；后端**映射端点数为 15**，已 100% 覆盖。

### 12.3 运行时实测（修复后）

环境：commit 修复后重新 `mvn package` + `docker compose build api` + `docker compose up -d api`（镜像 `meiyetec/api:dev` sha256 `6a73841cbef2`），DB `meiyetec@127.0.0.1:3307`。

**operator01（OPERATOR 角色，权限集合不含 `biz:service-item:edit`）**

| 步骤 | code | msg | 判定 |
|---|---|---|---|
| `POST /biz/service/item` | 403 | 权限不足: 需要权限码：`biz:service-item:edit` | **DENIED** |
| `PUT /biz/service/item/99999999` | 403 | 同上 | **DENIED** |
| `DELETE /biz/service/item/99999999` | 403 | 同上 | **DENIED** |
| `POST /biz/service/category` | 403 | 同上 | **DENIED** |
| `PUT /biz/service/category/99999999` | 403 | 同上 | **DENIED** |
| `DELETE /biz/service/category/99999999` | 403 | 同上 | **DENIED** |
| `POST /biz/service/item/99999999/material` | 403 | 同上 | **DENIED** |
| `DELETE /biz/service/item/99999999/material/1` | 403 | 同上 | **DENIED** |
| `GET /biz/service/item/page` | 200 | 操作成功 | ALLOWED（list 码，OPERATOR 持有） |
| `GET /biz/service/category/list` | 200 | 操作成功 | ALLOWED |

**manager01（MANAGER 角色，已绑定 `biz:service-item:edit`）**

| 步骤 | code | msg | 判定 |
|---|---|---|---|
| `POST /biz/service/item` | 200 | 创建成功 | ALLOWED（未被误伤） |
| `PUT /biz/service/item/99999999` | 404 | 资源不存在: 项目不存在 | ALLOWED（权限通过，落到业务校验） |
| `DELETE /biz/service/item/99999999` | 404 | 资源不存在: 项目不存在 | ALLOWED（权限通过，落到业务校验） |
| `DELETE /biz/service/item/29`（探测期间创建的真实行） | 200 | 删除成功 | ALLOWED（探测数据已清理） |

### 12.4 探测数据清理

探测期间 manager01 创建的探测行（`service_code = I137PROBE_<HHmmss>`，id 29）已通过 `DELETE /biz/service/item/29` 删除。

⚠️ **`biz_service_item.uk_tenant_code` 唯一键不含 `deleted` 列** —— 软删除行仍占用 `tenant_id + service_code`，同一 `service_code` 无法再次创建（实测 `DuplicateKeyException`）。因此 API 层 DELETE 之后仍需 `DELETE FROM biz_service_item WHERE service_code LIKE 'I137PROBE%'` 硬删残留行。**本批已硬删，库内 `I137PROBE%` 残留 = 0 行**。该唯一键设计缺陷属独立问题，本批不修。

### 12.5 已知未修项（不属 P0 范围）

`POST /biz/service/item/import` 与 `POST /stock/product/import` 在**任何角色**下均返回 500 `MultipartException: Failed to parse multipart servlet request`（admin 亦 500）。根因是 `log/LogContextFilter` 用 `security/CachedBodyRequestWrapper` 全量缓存 body，破坏了 multipart 流 —— 与权限无关，属**独立缺陷**，本批不修，需另开 issue 单。operator01 对 import 的 403 未能直接观测（multipart 解析早于权限切面失败）。

### 12.6 P0 状态

**P0 → Fixed（已修复并有运行时证据）**。P1–P6 仍 Open，本批不修。

---

## 13. 编号与目录说明（2026-11-06 追加）

证据目录沿用历史命名 I132-batch2-guard-code，后续新批次统一用 FXXX-slug 格式。

