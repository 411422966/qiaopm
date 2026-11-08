# F149 · 营销模块权限矩阵补齐（batch2-B）

> 状态：**SQL 草案已审核通过，2026-11-09 在 meiyetec 正式库执行**（执行结果见 `evidence-notes.md`）
> 基线 commit：`02d1da8ce6c2ea60a67bfbfba6efae8aa534b6dc`（Sun Nov 8 23:13:08 2026 +0800）
> DB 快照：`backup/before-batch2b.sql`（mysqldump 10.13，2026-11-08 23:44:00，22.6 MB）
> 源调查报告：`.audit-tmp/batch2b-report.md`（35 KB，工作区草稿，未纳入证据目录）
> 范围声明：**本批只动 DB（sys_permission / sys_role_permission / sys_menu_permission），不改前后端代码**

---

## 1. 调查结论摘要

### 1.1 BLOCKER

| 编号 | 问题 | 证据位置 | 本批处理 |
|---|---|---|---|
| B1 | 10 个前端守卫码在 `sys_permission` 中不存在 → 对应路由全员 403 | `frontend/meiyetec-web/src/router/index.ts:99,136-145,157-187` | ✅ 新建 10 个粗码（Part 1） |
| B2 | OPERATOR 只有 4 个菜单码、0 个按钮码 → 核心用户全程不可用 | `sys_role_permission` 现有绑定 | ✅ 补 36 个规格码 + 10 粗码 + 19 菜单码 |
| B3 | 35 个后端注解码（`mkt:*` 细分码）零角色绑定 | 后端 `@RequirePermission` 全量扫描 | ✅ 按 §5 矩阵绑定 |
| B4 | `mkt:activity` 被 6 个不相关页面复用为守卫码，且该码不存在 | `router/index.ts:157,158,159,162,180,187` | ✅ 本批建 1 个粗码（裁决 4）；下一批 Phase 3 拆分 |

### 1.2 高 / 中 / 低（不在本批修复范围，登记待 Phase 3）

| 编号 | 问题 | 位置 |
|---|---|---|
| H1 | 5 个 Service/Controller 零 `@RequirePermission`（生日提醒、会员日、电商集成、动态定价、策略下发） | 报告 §3 表末 5 行 |
| H2 | `/market/*` 三条旧路由是假成功入口（守卫旧码、接口新码） | 裁决 3：本批不动，Phase 3 统一删除 |
| H3 | `/m09/invite` 守卫码匹配但写操作要 `mkt:invite:edit`（原零绑定） | `InviteController.java:82` |
| H4 | `/mkt/touch-task` 路由路径 ≠ 接口前缀 `/mkt/task` | `MktTouchTaskController.java:31` |
| M1 | 菜单码（点号）与守卫码（冒号）两套字符串 | 本批通过「粗码 + 细分码并存」兼容 |
| M2 | `mkt:touch:task:*` / `mkt.touch-task` / `mkt:touch-task` 三写法并存 | 本批三者同时建码并存 |
| M3 | CLERK 缺 `market:coupon:list` → POS 收银台选券 403 | 裁决 5：本批补 CLERK 2 码 |
| M4 | 12 条路由无 `sys_menu_permission` 行 → 侧边栏无入口 | ✅ 本批新建 12 条菜单行 |
| M5 | `mkt:promotion` / `mkt:coupon` / `mkt:points` 为单一粗码，读写不分 | 报告登记，Phase 3 拆分 |
| L1 | `views/mkt/experiment.vue` 无路由（死页面） | — |
| L2 | `api/market.ts` 的 `marketApi` 死代码 | — |
| L3 | `marketing` 根菜单 route_path 指向 `/market/coupon`，与 id=304 重复 | Phase 3 随旧路由一并清理 |
| L4 | `market:list`（id 1138）后端零引用（占位码） | 本批不绑定 |

### 1.3 关键实现事实（已静态确认）

| 事实 | 依据 |
|---|---|
| 10 个粗码**后端零 `@RequirePermission` 引用** → 纯前端守卫码，绑给 BOSS 仍是只读语义 | `backend/src/main/java` 全量 grep 无命中 |
| 用户权限集 = 该角色绑定的**全部**权限码（含 button/API/menu） | `SysPermissionMapper.getPermissionsByUserId`（无 type 过滤） |
| 菜单可见性 = `sys_menu_permission` 行 ∩ `sys_role_permission` 绑定 ∩ `permission_type='menu'` | `SysMenuPermissionMapper.listVisibleMenuByUserId` |
| 菜单树按 `sys_permission.parent_id` 拼装，父节点不在集合内则子节点变根 | `RbacService.buildMenuTree` L100-L112 |
| 前端图标白名单含 `Promotion/Present/Medal/Calendar/Share/Connection/Star/Picture/Goods/Trophy/Histogram/ShoppingCart` | `frontend/meiyetec-web/src/components/Layout.vue:88` |

---

## 2. 本批新建的 20 个权限码

### 2.1 Part 1 · 10 个页面准入粗码（`permission_type='button'`，`parent_id=1204`）

| # | 权限码 | 名称 | 前端守卫位置 |
|---|---|---|---|
| 1 | `mkt:effect` | 营销效果（页面准入） | `router/index.ts:99` |
| 2 | `mkt:channel` | 营销渠道（页面准入） | `router/index.ts:136` |
| 3 | `mkt:template` | 消息模板（页面准入） | `router/index.ts:137` |
| 4 | `mkt:touch-task` | 触达任务（页面准入） | `router/index.ts:138` |
| 5 | `mkt:birthday` | 生日提醒（页面准入） | `router/index.ts:139` |
| 6 | `mkt:journey` | 营销旅程（页面准入） | `router/index.ts:140` |
| 7 | `mkt:conversion` | 触达效果（页面准入） | `router/index.ts:142` |
| 8 | `mkt:pricing` | 动态定价（页面准入） | `router/index.ts:143` |
| 9 | `mkt:dispatch` | 业务策略下发（页面准入） | `router/index.ts:145` |
| 10 | `mkt:activity` | 营销活动通用（页面准入） | `router/index.ts:157,158,159,162,180,187` |

### 2.2 Part 2 · 10 个细分码（`permission_type='button'`，`parent_id=0`）

| # | 权限码 | 名称 | 拟挂注解位置 |
|---|---|---|---|
| 11 | `mkt:birthday:list` | 生日提醒-查询 | `BirthdayReminderService.java:141,200,327,354,381,425` |
| 12 | `mkt:birthday:edit` | 生日提醒-编辑 | `BirthdayReminderService.java:52,105,255,276` |
| 13 | `mkt:member-day:list` | 会员日-查询 | `MemberDayService.java:64,278,314,374` |
| 14 | `mkt:member-day:edit` | 会员日-编辑 | `MemberDayService.java:49,73,85,100,173,184,296` |
| 15 | `mkt:ecommerce:list` | 电商集成-查询 | `EcommerceService.java:74,113,223,485` |
| 16 | `mkt:ecommerce:edit` | 电商集成-编辑 | `EcommerceService.java:58,86,99,132,244` |
| 17 | `mkt:pricing:rule:list` | 动态定价规则-查询 | `DynamicPricingService.java:87,188` |
| 18 | `mkt:pricing:rule:edit` | 动态定价规则-编辑 | `DynamicPricingService.java:57,71,81,160,221,276` |
| 19 | `mkt:dispatch:list` | 业务策略下发-查询 | `BusinessPolicyDispatchController.java:127,148,155,177,189` |
| 20 | `mkt:dispatch:edit` | 业务策略下发-编辑 | `BusinessPolicyDispatchController.java:87,105,116` |

> 本批**只建码、不挂注解**（裁决：不动后端代码）。注解落地属 Phase 3。

---

## 3. Part 3 · 新建 12 条菜单行

| # | permission_code | 名称 | route_path | icon | sort_order | 组件 |
|---|---|---|---|---|:-:|---|
| 1 | `mkt.promotion` | 营销活动 | `/mkt/promotion` | Promotion | 11 | `views/mkt/promotion.vue` |
| 2 | `mkt.coupon` | 优惠券 | `/mkt/coupon` | Present | 12 | `views/mkt/coupon.vue` |
| 3 | `mkt.points` | 积分规则 | `/mkt/points` | Medal | 13 | `views/mkt/points.vue` |
| 4 | `mkt.birthday` | 生日提醒 | `/mkt/birthday` | Calendar | 14 | `views/mkt/birthday.vue` |
| 5 | `mkt.invite` | 裂变邀请 | `/m09/invite` | Share | 15 | `views/m09-invite/index.vue` |
| 6 | `mkt.dispatch` | 业务策略下发 | `/m12/dispatch` | Connection | 16 | `views/m12/dispatch/index.vue` |
| 7 | `mkt.member-day` | 会员日自动化 | `/member-day` | Star | 17 | `views/member-day/index.vue` |
| 8 | `mkt.style-gallery` | 款式图库 | `/style-gallery` | Picture | 18 | `views/style-gallery/index.vue` |
| 9 | `mkt.style-gallery.quick-order` | 款式图库快速下单 | `/style-gallery/quick-order` | Goods | 19 | `views/style-gallery/quick-order.vue` |
| 10 | `mkt.subscription` | 订阅会员制 | `/subscription` | Trophy | 20 | `views/subscription/index.vue` |
| 11 | `mkt.lifecycle` | 会员生命周期看板 | `/lifecycle` | Histogram | 21 | `views/lifecycle/index.vue` |
| 12 | `mkt.ecommerce` | 电商集成 | `/ecommerce` | ShoppingCart | 22 | `views/ecommerce/index.vue` |

已存在、本批**不重复插入**（仅幂等自愈补齐）的 7 条：`mkt.effect`(307) `mkt.channel`(308) `mkt.template`(309) `mkt.touch-task`(310) `mkt.journey`(311) `mkt.conversion`(312) `mkt.pricing`(313)。

---

## 4. Part 4 · 角色绑定矩阵

### 4.1 规格码口径（不含 10 个粗码、不含菜单码）

> **口径裁决（用户 2026-11-09 拍板，取 A）**：保留报告 §5 矩阵推导出的 **51 / 23 / 53 / 36 / 2**，**不做裁剪**。
> 用户早期口径中的 22 / 40 与之存在偏差，作为**已登记偏差**处理，差异来源与理由逐条列于下表「差异说明」列；
> 对账查询见 `verify-batch2b.sql` 的 **V3b**。

| 角色 | 组成 | 实际 | 用户口径 | 偏差 | 偏差说明（已登记，不裁剪） |
|---|---|:-:|:-:|:-:|---|
| ADMIN | 39 营销码 + 12 遗留 CRUD 码 | **51** | 51 | 0 | 一致 |
| BOSS | 21 只读码 + 2 跨域只读码 | **23** | 22 | **+1** | 报告 §5.1 只记了 21 个只读码，§5 矩阵另给出 `market:coupon:list` / `market:point:list` 2 个跨域只读码 → 21 + 2 = 23 |
| MANAGER | 39 营销码 + 12 遗留 CRUD 码 + 2 跨域只读码 | **53** | 40 | **+13** | 报告「清单 C」的 MANAGER 行是 OPERATOR 行复制，未含 12 个 `market:*` 遗留 CRUD 码；而报告 §5 矩阵对 MANAGER 的 `market:*` 判定为 ✅（应授），故 36 + 12 + … + 2 → 53 |
| OPERATOR | 39 营销码 − 5 受限 edit 码 + 2 跨域只读码 | **36** | 36 | 0 | 一致 |
| CLERK | 2 跨域只读码 | **2** | 2 | 0 | 一致 |

**偏差处置结论**：偏差仅存在于**用户早期估算口径与报告矩阵**之间，不影响权限模型正确性（§5 矩阵逐条可追溯到代码/DB 证据）。
本批按 §5 矩阵落地，`V3b` 把 5 个角色的「用户口径 / 实际 / 偏差 / 说明」显式打出，作为长期可追溯的偏差登记。
若后续要严格对齐 22 / 40，需重新裁剪 BOSS / MANAGER 的码集 —— 裁剪会**移除 MANAGER 的 `market:*` 删除类破坏性码**，与用户对 MANAGER 的原始描述冲突，故本批不做。

### 4.2 OPERATOR 被排除的 5 个写码（裁决 1、2）

`mkt:pricing:experiment:edit`、`mkt:pricing:rule:edit`、`mkt:dispatch:edit`、`mkt:ecommerce:edit`、`subscription:plan:edit`

### 4.3 附加绑定

| 类别 | ADMIN | BOSS | MANAGER | OPERATOR | CLERK |
|---|:-:|:-:|:-:|:-:|:-:|
| 10 个页面准入粗码 | ✅ 10 | ✅ 10 | ✅ 10 | ✅ 10 | ❌ |
| 12 条新菜单码 | ✅ 12 | ✅ 12 | ✅ 12 | ✅ 12 | ❌ |
| 7 条既有菜单码 | 已有 | 已有 | ✅ 7（新增） | ✅ 7（新增） | ❌ |

### 4.4 本批新增 `sys_role_permission` 行数

| 角色 | 规格码 | 粗码 | 菜单码 | 合计 |
|---|:-:|:-:|:-:|:-:|
| ADMIN | 44 | 10 | 12 | 66 |
| BOSS | 16 | 10 | 12 | 38 |
| MANAGER | 47 | 10 | 19 | 76 |
| OPERATOR | 34 | 10 | 19 | 63 |
| CLERK | 2 | 0 | 0 | 2 |
| **合计** | 143 | 40 | 62 | **245** |

---

## 5. 执行前确认（任务 2）

| 检查项 | 结果 |
|---|---|
| 运行镜像 | `meiyetec/api:dev`，image id `f0611732489e`，构建于 2026-11-08 23:00:43；容器 `meiyetec-api` 起于 23:01:28（healthy） |
| 源码最后修改 | `backend/src/main/java/com/meiyetec/service/AiEnhanceService.java` 2026-11-08 22:58:14 |
| 镜像与源码一致性 | ✅ 容器内 `/app/app.jar` SHA256 = `1112bc3638db243b940fc76a4ac251f6b93682591501e9ec4e6b583daab16d39`，与本地 `backend/target/meiyetec-api-1.0.0.jar` **完全一致** → **无需重建镜像** |
| 前端源码最后修改 | 2026-11-08 15:43:06（早于镜像构建） |
| 全库备份 | ✅ `backup/before-batch2b.sql`（22,602,813 字节，dump completed 2026-11-08 23:44:00，仅密码告警） |

---

## 6. 草案验证结果（scratch 库真实执行，非纸面推演）

在临时库 `mkt_scratch_2b`（由 meiyetec 复制 `sys_permission / sys_role_permission / sys_menu_permission / sys_role / sys_user / sys_user_role` 六表得到）上真实执行三件套：

| 验证项 | 结果 |
|---|---|
| `fix-batch2b.sql` 首次执行 | ✅ exit 0，无错误 |
| `fix-batch2b.sql` 二次执行（幂等） | ✅ 行数与首次完全一致（ADMIN 86 / BOSS 57 / MANAGER 86 / OPERATOR 69 / CLERK 2） |
| V1 · 20 个新码存在性 | ✅ 20/20 `OK`（`permission_type`/`parent_id`/`status`/`deleted` 均符合预期） |
| V2 · 12 新 + 7 既有菜单行 | ✅ 19/19 `OK`，`route_path` 与前端路由逐条一致 |
| V3 · 角色绑定数 | ✅ ADMIN 51 / BOSS 23 / MANAGER 53 / OPERATOR 36 / CLERK 2 —— 5/5 `PASS` |
| V4 · 菜单可见性模拟 | ✅ admin / boss01 / manager01 / operator01 各可见 19 条营销菜单；clerk01 可见 0 条 |
| V5 · 前端路由守卫模拟 | ✅ manager01 / operator01 / boss01 / admin 对 19 条路由全部 `PASS`（含 `/member-day`、`/style-gallery`、`/subscription`、`/ecommerce`、`/lifecycle` 5 条共用 `mkt:activity` 守卫的路由） |
| V7 · 只读断言 | ✅ BOSS 营销域写码 0 条；OPERATOR 未持有 5 个受限 edit 码 |
| V8 · 中文编码 | ✅ `HEX(permission_name)` 为标准 UTF-8 字节序（例：生日提醒 = `E7949FE697A5E68F90E98692`） |
| `rollback-batch2b.sql` 执行 | ✅ exit 0；残留新码 0、新菜单行 0 |
| 回滚后与执行前对比 | ✅ `sys_permission` 476 / `sys_role_permission` 913 / `sys_menu_permission` 110，与执行前**逐条比对完全一致**（66 条营销相关绑定全量 diff 无差异） |

---

## 7. 未确认项 / 已知限制（规则 5）

1. **用户口径 22 / 40 与实际 23 / 53 的偏差**：已于 2026-11-09 由用户拍板**取 A** —— 保留 **51 / 23 / 53 / 36 / 2**，不做裁剪；偏差登记在 §4.1 与 `verify-batch2b.sql` 的 V3b（长期可追溯）。详见 §4.1「口径裁决」。
2. **`mkt:promotion` / `mkt:coupon` / `mkt:points` 读写不分**：BOSS 绑定后实际具备这三个模块的写能力（粗码无只读变体），属报告 M5，Phase 3 拆分时一并解决。
3. **粗码 `parent_id=1204`**：菜单树只读取 `permission_type='menu'` 的行，取值不影响渲染；但权限管理侧若按 parent_id 展示全部权限，这 10 个 button 会挂在 marketing 节点下。
4. **回滚后再重启风险**：若 Phase 3 给这 10 个细分码补上 `@RequirePermission` 注解，`PermissionAutoRegistrar` 会以 `permission_type='API'` 重新登记被删的新码，需人工再删。
5. **前端仍用旧粗码守卫**：`mkt:touch-task` 与 `mkt:touch:task:list`、点号菜单码与冒号守卫码并存（M1/M2），本批为兼容策略，Phase 3 统一。
6. **`marketing` 根菜单 route_path 仍指向 `/market/coupon`**（L3），旧 `/market/*` 三条路由本批按裁决 3 保留。
7. 本批**未做浏览器实测**：守卫与菜单的可达性由 V4/V5 的 SQL 模拟复刻后端查询逻辑得出，属静态推演 + 数据库实跑证据，不替代人工验收。

---

## 8. 本批落盘文件

| 文件 | 用途 |
|---|---|
| `fix-batch2b.sql` | 建 32 个新码（20 按钮 + 12 菜单）+ 5 角色绑定（幂等） |
| `verify-batch2b.sql` | V0–V8 验证（只读） |
| `rollback-batch2b.sql` | 回滚（幂等） |
| `evidence-notes.md` | 正式库执行记录与结果回填（2026-11-09） |

---

## 9. 浏览器人工验收清单（待用户执行，本批未做）

> 环境：http://localhost:8088（`meiyetec-web` 容器，Up 9 hours healthy）。登录后逐角色核对**左侧菜单可见项**与**路由可达性**。

### 9.1 通用预期

四个有权角色（admin / boss01 / manager01 / operator01）应看到 **19 条营销菜单**（7 既有 + 12 新增），clerk01 应看到 **0 条**。

### 9.2 逐角色清单

| 用户 | 角色 | 侧边栏应出现的 19 条营销菜单 | 逐条访问应 200 的页面 | 应 403 / 不可达 |
|---|---|---|---|---|
| `admin` | ADMIN, SUPER_ADMIN | 营销效果、渠道配置、消息模板、触达任务、营销旅程、触达效果、动态定价、营销活动、优惠券、积分规则、生日提醒、裂变邀请、业务策略下发、会员日自动化、款式图库、款式图库快速下单、订阅会员制、会员生命周期看板、电商集成 | 全部 19 条：`/mkt/effect` `/mkt/channel` `/mkt/template` `/mkt/touch-task` `/mkt/journey` `/mkt/conversion` `/mkt/pricing` `/mkt/promotion` `/mkt/coupon` `/mkt/points` `/mkt/birthday` `/m09/invite` `/m12/dispatch` `/member-day` `/style-gallery` `/style-gallery/quick-order` `/subscription` `/lifecycle` `/ecommerce` | 无 |
| `boss01` | BOSS | 同上 19 条 | 同上 19 条 | 无 |
| `manager01` | MANAGER | 同上 19 条 | 同上 19 条 | 无 |
| `operator01` | OPERATOR | 同上 19 条 | 同上 19 条（**营销域写操作按钮仍应隐藏**，5 个受限码 `pricing:rule:edit` / `dispatch:edit` / `ecommerce:edit` / `subscription:plan:edit` / `pricing:experiment:edit` OPERATOR 未持有） | 无 |
| `clerk01` | CLERK | **0 条营销菜单** | — | 手工访问上述 19 条路由应全部跳 403 / 未授权页 |

### 9.3 记录要求

每条记录：URL、HTTP/页面结果、截图文件名、是否与预期一致。**不一致项**回填到本目录 `evidence-notes.md` §9 并登记为新 issue。
