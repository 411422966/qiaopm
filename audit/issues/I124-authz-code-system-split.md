# I124 — 权限码体系分裂（点号 vs 冒号），前端菜单与路由守卫系统性错配

> 发现时间：2026-10-24
> 发现方式：浏览器实测（Playwright，5 角色 × 全量菜单项）+ DB 交叉验证 + 容器字节码核验
> 严重级别：**P0 BLOCKER**
> 状态：Open（未修复，仅诊断）
> 关联：`docs/acceptance/QIAOPM/commission-rules/QIAOPM-ACC-002/root-cause.md`（本问题是该单点根因的系统化版本）
> 基线：commit `77767e14c177a7512fc92ba6764e9d5427447969`（2026-10-23 23:38:44）

---

## 1. 一句话结论

`sys_permission` 表里并存**两套互不兼容的权限码体系**（78 个点号 `xxx.yyy` + 359 个冒号 `xxx:yyy`），
**后端 RBAC 授权数据与菜单绑定用的是点号，前端路由守卫与后端 `@RequirePermission` 用的是冒号**。
两套码在 DB 中几乎零交集，导致：

- **前端**：菜单渲染依据点号（可见），路由准入依据冒号（拦截）→ 5 个非超管角色共 **77 处「菜单可见但点进去 403」**
- **后端**：若按当前源码重建镜像，**106 个 Controller 的 308 个权限码零角色绑定** → 非超管角色全面 403

这不是单点配置错误，是**数据层两套体系并存**导致的全系统 RBAC 失效。

---

## 2. 硬证据

### 2.1 DB 层：两套体系并存且几乎零交集

```
$ mysql ... -e "SELECT COUNT(*) total, SUM(permission_code LIKE '%:%') colon,
                 SUM(permission_code LIKE '%.%') dotted FROM sys_permission;"
total_perm  colon_codes  dotted_codes
454         359          78
```

`sys_role_permission` 共 360 行绑定；**332 / 454 个权限码零绑定**。

### 2.2 前端守卫码 vs DB（决定性）

对 `frontend/meiyetec-web/src/router/index.ts` 的 96 个唯一 `meta.permission` 码逐一回查 DB：

| 分类 | 数量 | 说明 |
|---|---|---|
| **DB 中不存在** | **57** | 守卫要求的码，`sys_permission` 表里根本没有 → 任何角色都无法获得 |
| **DB 中存在但零角色绑定** | **32** | 存在，但没绑给任何角色 → 守卫必然拒绝 |
| **DB 中存在且有绑定** | **7** | `blockchain:monitor` / `blockchain:trace` / `employee:level` / `employee:level:benefit` / `member:list` / `setting` / `wallet:list` |

**96 个守卫码里只有 7 个（7.3%）在当前 DB 下可满足。**

DB 中不存在的前端守卫码样例：
```
biz:service:item  biz:treatment:plan  commission:rule  commission:rule:config
commission:rule:list  crm:complaint  crm:follow-up  crm:review  crm:cross-rule
crm:rfm  mh:visual  mh:consent  org:hq  org:brand  org:store
pos:list  stock:list  market:coupon:list  report:dashboard  report:biz:list
sys:tenant:list  sys:log:list  sys:login-log:list  mkt:channel  mkt:template ...
```

### 2.3 运行时权限数组：用户持有的是点号

`GET /auth/me/permissions` 实测：

| 用户 | 角色 | 权限码总数 | 冒号 | 点号 | 裸码 |
|---|---|---|---|---|---|
| admin | SUPER_ADMIN, ADMIN | 123 | 27 | 78 | 18（含 `*`） |
| boss01 | BOSS | 49 | **2** | 32 | 15 |
| clerk01 | CLERK | 19 | **1** | 13 | 5 |
| manager01 | MANAGER | 37 | **2** | 25 | 10 |
| operator01 | OPERATOR | 8 | **0** | 5 | 3 |

非超管角色持有的冒号码仅 0–2 个，**而守卫要求的正是冒号码** → 必然拒绝。

### 2.4 浏览器实测（`.audit-tmp/browser-deadlinks.js`，Playwright）

逐个登录 5 个账号，取 `GET /auth/me/menu` 的全部菜单项，逐条 `page.goto` 实测落地页：

| 用户 | 菜单项 | D1 守卫 403 | D2 无路由跳登录 | 可进入 |
|---|---|---|---|---|
| admin | 95 | 0 | 2 | 93 |
| boss01 | 40 | **36** | 0 | 4 |
| clerk01 | 15 | **13** | 0 | 2 |
| manager01 | 27 | **23** | 0 | 4 |
| operator01 | 6 | **5** | 0 | 1 |

**77 处 D1 中，`menuCode !== routePermission` 的比例 = 77/77 = 100%。**

样本（`deniedCode` 取自 403 页渲染的「权限码：xxx」，即 `to.meta.permission`）：

| 落地路径 | 菜单码（后端给） | 守卫要求（前端） | 用户是否持有守卫码 |
|---|---|---|---|
| `/commission/rules` | `commission.rules` | `commission:rule:config` | 否 |
| `/booking/calendar` | `booking.calendar` | `booking:list` | 否 |
| `/pos/checkout` | `pos.checkout` | `pos:list` | 否 |
| `/stock/list` | `stock.list` | `stock:list` | 否 |
| `/report/dashboard` | `report.dashboard` | `report:dashboard` | 否 |
| `/org/store` | `org.store` | `org:store` | 否 |
| `/mkt/channel` | `mkt.channel` | `mkt:channel` | 否 |

### 2.5 后端侧：源码 324 个权限码，308 个零绑定

扫描 `backend/src/main/java/com/meiyetec/controller/*.java` 的 `@RequirePermission("...")`：

| 分类 | 数量 |
|---|---|
| DB 中不存在 | 1（`xxx:yyy` — `SysPermissionController.java`，占位符遗留） |
| **DB 中存在但零角色绑定** | **308** |
| DB 中存在且有绑定 | 15 |

受影响的 Controller 文件（去重）**106 个**，包括 `BookingController` / `CommissionController` / `PosController` /
`StockController` / `ReportController` / `MemberController` / `MktChannelController` / `SysTenantController` /
`PluginRuntimeController` / `Dcep*Controller` 等核心域。

> **推论（未运行验证）**：若用当前 HEAD 源码重建 `meiyetec/api` 镜像并重启，非超管角色将对这 106 个控制器
> 全面 403。标记 **UNVERIFIED** —— 需重建镜像后实测确认。

### 2.6 运行中的容器实例 ≠ 当前源码（影响证据解释）

| 组件 | 运行产物 | 构建时间 | 源码 HEAD |
|---|---|---|---|
| `meiyetec/api:dev` | `/app/app.jar` | **2026-10-20 17:14** | 2026-10-23 23:38 |
| `meiyetec/web:dev` | `nginx/assets/` | **2026-10-10 08:16** | 2026-10-23 23:38 |

证据：
- 容器内 `SysTenantController.class` 字节码**无 `RequirePermission` 常量池引用**，源码有（`6afd67e` 2026-10-21 补的 37 个注解）
- 容器内 143 个 Controller 中仅 **14** 个含 `RequirePermission`；源码侧 **108** 个文件含该注解
- 实测 `/sys/tenant/list`（源码有 `@RequirePermission("sys:tenant:list")`）对 clerk01/operator01 **放行 200**，
  而 `/member/list`（运行镜像内已有注解） 对 operator01 **正确 403** → 证明切面本身生效，放行是因镜像缺注解

**前端路由表无漂移**，实测结论对当前源码有效：
- 容器 bundle `router-Bn4tUSJb.js` 提取的 `permission` 码 = **97**，源码 = **97**，交集 **97**，差集双向为 **0**
- `path => permission` 组合 147 vs 147，交集 146（唯一差异是正则跨条目误配，已核实 `setting` 路由两处一致）

---

## 3. 故障链（完整闭合）

```
DB 层分裂
  sys_permission 同时存 78 个点号 + 359 个冒号
  角色绑定（sys_role_permission 360 行）几乎全部落在点号侧
        ↓
后端菜单树构建  RbacService.buildMenuTree → menu.permissionCode = 点号
后端权限码下发  RbacContextLoader.load → ctx.permissions = 点号（+ 少量冒号）
        ↓
前端 Layout.vue 用 menus(点号) 渲染侧边栏 → 菜单「可见」
前端路由守卫 index.ts 用 to.meta.permission(冒号) 判定准入
  → store.hasPermission(冒号) 在 permissions(点号) 里找不到 → next('/403')
        ↓
非超管角色：菜单点进去全是 403（实测 77 处）
```

后端侧同一分裂的镜像：`@RequirePermission` 用冒号，而 DB 授权用点号 → 一旦注解真正生效，非超管全面被拒。

---

## 4. 受影响功能清单（非超管角色视角）

以下菜单项对 boss01 / clerk01 / manager01 / operator01 **可见但不可进入**：

| 域 | 不可进入的菜单项 |
|---|---|
| 预约 | 预约管理、预约日历、预约列表、排班管理 |
| 收银 | 收银结算、订单列表、收银统计、退款单、支付对账 |
| 库存 | 库存管理、库存列表、商品耗材、出入库记录、消耗记录、盘点管理 |
| 提成 | 提成规则、提成记录、工资报表、结算管理 |
| 营销 | 优惠券、积分商城、营销活动、动态定价、营销效果、渠道配置、消息模板、触达任务、营销旅程、触达效果 |
| 报表 | 数据看板、营业报表、导出管理 |
| 客户 | 客户运营看板、回访任务、投诉工单、客户评价、跨店规则、RFM 分群 |
| 组织 | 总部看板、品牌管理、门店管理 |
| 系统 | 租户管理、操作日志、登录日志 |
| 套餐/业务 | 套餐管理、会员套餐查询、服务项目、疗程方案 |

**实质可用性**：operator01 的 6 个菜单项里 5 个 403；clerk01 的 15 个里 13 个 403；
boss01 的 40 个里 36 个 403 → **非超管角色当前几乎无法使用本系统。**

---

## 5. ⚠️ 与「用户已验证通过页面」规则的冲突（需用户裁决）

用户规则声明 `/dashboard`、`/mh/visual`、`/mh/consent` 已实测通过、禁止主动修改。
实测显示这两个 MH 页面的**机制**同样落在本问题范围内：

| 页面 | 菜单码 | 守卫码 | admin | boss01 |
|---|---|---|---|---|
| `/mh/visual` | `mh.visual`（DB id=1251，绑 ADMIN/BOSS/SUPER_ADMIN） | `mh:visual`（DB 不存在） | OK（持 `*`） | **D1 → /403** |
| `/mh/consent` | `mh.consent`（DB id=1252，绑 ADMIN/BOSS/SUPER_ADMIN） | `mh:consent`（DB 不存在） | OK（持 `*`） | **D1 → /403** |

用户在浏览器验证时很可能使用的是 admin（持 `*` 通配，任何守卫码都放行），因而未暴露该问题。

**本单不修改任何 MH 相关代码**，仅登记事实。是否纳入修复范围由用户裁决。

---

## 6. 复现步骤

```bash
# 1. 登录非超管账号
curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"operator01","password":"123456"}'

# 2. 取权限码 —— 输出为点号
curl -s http://localhost:8080/auth/me/permissions -H "Authorization: Bearer $TOKEN"
#   -> {"perms":["dashboard","marketing","report","market.coupon",...]}

# 3. 取菜单 —— 菜单码为点号
curl -s http://localhost:8080/auth/me/menu -H "Authorization: Bearer $TOKEN"

# 4. 取守卫码（源码）—— 冒号
grep -o "permission: '[^']*'" frontend/meiyetec-web/src/router/index.ts | sort -u

# 5. DB 侧交叉
mysql -h 127.0.0.1 -P 3307 -u root meiyetec -e "
  SELECT p.permission_code,
         (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.permission_id=p.id) binds
  FROM sys_permission p WHERE p.permission_code LIKE '%:%' ORDER BY binds LIMIT 20;"
```

完整脚本见 `audit/evidence/ACC002-authz-code-split/`：
`browser-deadlinks.js`（浏览器实测）、`authz-cross.js`（前端路由 × DB 交叉）、
`backend-authz-impact.js`（后端注解 × DB 影响面）、`api-authz.js`（后端端点实测）。

---

## 7. 修复方向（备选，未实施，待用户裁决）

| 方案 | 说明 | 影响面 |
|---|---|---|
| **A. 统一到冒号体系** | 迁移 78 个点号 → 冒号；补齐 57 个缺失码；重建 308 个零绑定 | 数据迁移 + 前端路由改码，**最彻底** |
| **B. 统一到点号体系** | 前端路由 96 个守卫码全改点号；后端 324 个注解全改点号 | 代码改动量巨大，但**不改数据** |
| **C. 守卫双码兼容** | `hasPermission` 接受 `xxx.yyy` 与 `xxx:yyy` 互认（归一化） | 改动最小，**但掩盖体系分裂**，后端 308 零绑定仍需单独处理 |
| **D. 仅补 DB 绑定** | 为 308 个零绑定码补角色授权 | 不解决命名分裂，`xxx:yyy` 与 `commission.rules` 仍是两个东西 |

**必须同时处理的问题**：
1. `SysPermissionController.java` 中的占位符 `xxx:yyy`（DB 不存在）
2. 6afd67e 之后补的 37 个 `@RequirePermission` 依赖「零绑定码」，重建镜像前必须先定方案，否则上线即全量 403
3. 运行容器镜像落后源码 4 天（api）/ 14 天（web），任何验收结论都须标注其对应的产物版本

---

## 8. 证据清单

| 文件 | 内容 |
|---|---|
| `audit/evidence/ACC002-authz-code-split/browser-deadlinks.json` | 5 角色 × 全量菜单项落地页实测（77 D1 + 2 D2 明细） |
| `audit/evidence/ACC002-authz-code-split/authz-cross.json` | 前端 96 守卫码 × DB 三分类 + 各角色权限码体系分布 |
| `audit/evidence/ACC002-authz-code-split/backend-authz-impact.json` | 后端 324 注解码 × DB 绑定 × 受影响控制器清单 |
| `audit/evidence/ACC002-authz-code-split/api-authz.json` | 后端端点实测（`member:list` 正确 403 vs `sys:tenant:list` 放行） |
| `audit/evidence/ACC002-authz-code-split/bundle-vs-src.json` | 容器前端 bundle 路由码 vs 源码（97/97 一致，证明前端证据有效） |
| `audit/evidence/ACC002-authz-code-split/browser-deadlinks.js` | 浏览器实测脚本 |
| `audit/evidence/ACC002-authz-code-split/authz-cross.js` | 前端路由 × DB 交叉脚本 |
| `audit/evidence/ACC002-authz-code-split/backend-authz-impact.js` | 后端影响面脚本 |
| `audit/evidence/ACC002-authz-code-split/perm-aspect-check.js` | 后端切面生效性验证脚本 |

**绑定基线**：commit `77767e14c177a7512fc92ba6764e9d5427447969` /
DB `meiyetec@localhost:3307`（实测时刻 2026-10-24 00:30–00:50）/
env 见 `audit/baseline/env.md` /
运行产物 `meiyetec/api:dev`(2026-10-20 17:14) + `meiyetec/web:dev`(2026-10-10 08:16)

---

## 9. 未确认项

1. **两套体系分裂的具体时间点无法用 git 确认** —— 2026-10-16 前的历史因 NTFS objects/30 损坏丢失（见 I109），
   仅有 `init_rbac_v2.sql` 与 `init_commission_engine.sql` 两套列名不同的脚本作为间接证据
2. **「重建镜像后 106 个控制器全面 403」是静态推演，未经运行验证** —— 需重建 `meiyetec/api` 镜像实测
3. **`init_commission_engine.sql` 等脚本是否曾被 apply 到当前 DB 未确认** —— 其 `sys_permission(code,name,module,type)`
   列名与实际表结构 `permission_code/permission_name/parent_id` 不符，apply 应失败
4. **`sys_menu_permission` 的 110 条菜单绑定中，冒号码仅 15 条、点号 78 条、裸码 17 条** —— 菜单侧确实以点号为主
