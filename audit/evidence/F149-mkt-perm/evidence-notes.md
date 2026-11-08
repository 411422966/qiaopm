# F149 · batch2-B 营销权限矩阵补齐 —— 执行记录（evidence notes）

> 执行时间：2026-11-09 00:5x–01:0x（Asia/Shanghai）
> 基线 commit：`02d1da8ce6c2ea60a67bfbfba6efae8aa534b6dc`
> 目标环境：Docker `meiyetec-mysql`（mysql:8.0，host 127.0.0.1:3307）/ 库 `meiyetec` / tenant_id=1
> 变更范围：**仅 DB**（`sys_permission` / `sys_role_permission` / `sys_menu_permission`），未改动任何前后端代码
> 状态：**PARTIAL-NON-BLOCKER**（DB 层已落地并通过 SQL 实跑验证；浏览器人工验收未做，见 §7）

---

## 1. 执行前确认

| 检查项 | 结果 |
|---|---|
| 运行镜像 | `meiyetec/api:dev`，image id `f0611732489e`，构建于 2026-11-08 23:00:43 |
| 容器 | `meiyetec-api` Up 2 hours (healthy) |
| 容器内 `/app/app.jar` SHA256 | `1112bc3638db243b940fc76a4ac251f6b93682591501e9ec4e6b583daab16d39` |
| 本地 `backend/target/meiyetec-api-1.0.0.jar` SHA256 | `1112BC3638DB243B940FC76A4AC251F6B93682591501E9EC4E6B583DAAB16D39`（打包时间 2026-11-08 22:59:47） |
| 结论 | ✅ **镜像与源码一致，无需重建镜像** |
| 源码最后修改 | `backend/src/main/java/com/meiyetec/service/AiEnhanceService.java` 2026-11-08 22:58:14（早于 jar 打包与镜像构建）；前端最后修改 2026-11-08 15:43:06 |

## 2. 执行前备份（刷新）

| 项 | 值 |
|---|---|
| 文件 | `backup/before-batch2b-exec.sql` |
| 大小 | 22,627,208 字节 |
| 首 4 字节 | `2D 2D 20 4D`（`-- M`，mysqldump 头，**非** `FF FE` UTF-16 BOM） |
| `CREATE TABLE` 计数 | 460（≥ 280 阈值） |
| 生成命令 | `docker exec meiyetec-mysql sh -c "mysqldump -uroot -proot123456 --default-character-set=utf8mb4 meiyetec > /tmp/bk.sql"` → `docker cp meiyetec-mysql:/tmp/bk.sql backup\before-batch2b-exec.sql` |
| 附注 | mysqldump 仅输出密码告警（`[Warning] Using a password on the command line interface can be insecure`），无其他错误 |

> 注：先前 `backup/before-batch2b.sql`（22,602,813 B，2026-11-08 23:44:00）保留未删，作为第二份历史快照。

## 3. 执行 fix-batch2b.sql

| 项 | 结果 |
|---|---|
| 执行命令 | `cmd /c 'docker exec -i meiyetec-mysql mysql -uroot -proot123456 meiyetec --default-character-set=utf8mb4 < audit\evidence\F149-mkt-perm\fix-batch2b.sql'` |
| 退出码 | **0** |
| ERROR | **无**（stderr 仅密码告警） |

### 3.1 影响行数对账（执行前 → 执行后）

| 表 | 执行前 | 执行后 | 增量 | 预期 | 判定 |
|---|:-:|:-:|:-:|:-:|:-:|
| `sys_permission` | 476 | 508 | **+32** | Part1 粗码 10 + Part2 细分码 10 + Part3 菜单码 12 = 32 | ✅ |
| `sys_role_permission` | 913 | 1158 | **+245** | 66 + 38 + 76 + 63 + 2 = 245 | ✅ |
| `sys_menu_permission` | 110 | 122 | **+12** | Part3 新建 12 条菜单 UI 行 | ✅ |

### 3.2 脚本内置自检输出（按营销域权限码统计的绑定行数）

| role_code | bound_rows | 说明 |
|---|:-:|---|
| ADMIN | 86 | 51 规格码 + 10 粗码 + 12 新菜单码 + 13 执行前既有营销绑定 |
| BOSS | 57 | 23 规格码 + 10 粗码 + 12 新菜单码 + 12 执行前既有营销绑定 |
| MANAGER | 86 | 53 规格码 + 10 粗码 + 19 菜单码 + 4 执行前既有营销绑定 |
| OPERATOR | 69 | 36 规格码 + 10 粗码 + 19 菜单码 + 4 执行前既有营销绑定 |
| CLERK | 2 | 2 跨域只读码 |

## 4. 执行 verify-batch2b.sql

执行命令同上（只读脚本，仅 SELECT）。退出码 **0**，无 ERROR。

### V0 · 校验用码表分类计数

| category | code_count |
|---|:-:|
| FINE | 39 |
| READ | 21 |
| EXCL | 5 |
| LEGACY | 12 |
| CROSS | 2 |
| GUARD | 10 |
| MENU_NEW | 12 |
| MENU OLD | 7 |

### V1 · 20 个新码存在性 —— ✅ 20/20 `OK`

全部 `permission_type='button'`、`status=1`、`deleted=0`；10 个粗码 `parent_id=1204`，10 个细分码 `parent_id=0`。

```
mkt:activity      button 1204  OK      mkt:birthday:edit  button 0    OK
mkt:birthday      button 1204  OK      mkt:birthday:list  button 0    OK
mkt:channel       button 1204  OK      mkt:dispatch:edit  button 0    OK
mkt:conversion    button 1204  OK      mkt:dispatch:list  button 0    OK
mkt:dispatch      button 1204  OK      mkt:ecommerce:edit button 0    OK
mkt:effect        button 1204  OK      mkt:ecommerce:list button 0    OK
mkt:journey       button 1204  OK      mkt:member-day:edit button 0   OK
mkt:pricing       button 1204  OK      mkt:member-day:list button 0   OK
mkt:pricing:rule:edit button 0   OK    mkt:template       button 1204 OK
mkt:pricing:rule:list button 0   OK    mkt:touch-task     button 1204 OK
```

### V2 · 19 条菜单行（12 新 + 7 既有）—— ✅ 19/19 `OK`

| permission_code | 名称 | menu_row_id | route_path | icon | sort |
|---|---|:-:|---|---|:-:|
| mkt.effect | 营销效果 | 307 | /mkt/effect | DataAnalysis | 4 |
| mkt.channel | 渠道配置 | 308 | /mkt/channel | Connection | 5 |
| mkt.template | 消息模板 | 309 | /mkt/template | ChatLineRound | 6 |
| mkt.touch-task | 触达任务 | 310 | /mkt/touch-task | Bell | 7 |
| mkt.journey | 营销旅程 | 311 | /mkt/journey | Star | 8 |
| mkt.conversion | 触达效果 | 312 | /mkt/conversion | DataAnalysis | 9 |
| mkt.pricing | 动态定价 | 313 | /mkt/pricing | PriceTag | 10 |
| mkt.promotion | 营销活动 | 374 | /mkt/promotion | Promotion | 11 |
| mkt.coupon | 优惠券 | 375 | /mkt/coupon | Present | 12 |
| mkt.points | 积分规则 | 376 | /mkt/points | Medal | 13 |
| mkt.birthday | 生日提醒 | 377 | /mkt/birthday | Calendar | 14 |
| mkt.invite | 裂变邀请 | 378 | /m09/invite | Share | 15 |
| mkt.dispatch | 业务策略下发 | 379 | /m12/dispatch | Connection | 16 |
| mkt.member-day | 会员日自动化 | 380 | /member-day | Star | 17 |
| mkt.style-gallery | 款式图库 | 381 | /style-gallery | Picture | 18 |
| mkt.style-gallery.quick-order | 款式图库快速下单 | 382 | /style-gallery/quick-order | Goods | 19 |
| mkt.subscription | 订阅会员制 | 383 | /subscription | Trophy | 20 |
| mkt.lifecycle | 会员生命周期看板 | 384 | /lifecycle | Histogram | 21 |
| mkt.ecommerce | 电商集成 | 385 | /ecommerce | ShoppingCart | 22 |

图标全部命中 `Layout.vue:88` 的 ICONS 白名单；`route_path` 与 `router/index.ts` 逐条对齐；`hidden=0`。

### V3 · 五角色绑定数 —— ✅ 5/5 `PASS`

| role_code | spec_actual | spec_expected | fine | read | excl | legacy | cross | guard | menu_new | menu_old | 结果 |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ADMIN | 51 | 51 | 39 | 21 | 5 | 12 | 2 | 10 | 12 | 7 | **PASS** |
| BOSS | 23 | 23 | 21 | 21 | 0 | 1 | 2 | 10 | 12 | 7 | **PASS** |
| MANAGER | 53 | 53 | 39 | 21 | 5 | 12 | 2 | 10 | 12 | 7 | **PASS** |
| OPERATOR | 36 | 36 | 34 | 21 | 0 | 0 | 2 | 10 | 12 | 7 | **PASS** |
| CLERK | 2 | 2 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | **PASS** |

> 说明：`spec_actual` 为**规格码口径**（不含 10 粗码、不含菜单码）。分类可重叠，故各列之和大于总数。

### V3b · 与用户口径对账（口径 A：保留实际值，不裁剪）

| role_code | user_expect | spec_actual | delta | 判定 | 说明 |
|---|:-:|:-:|:-:|---|---|
| ADMIN | 51 | 51 | 0 | 一致 | — |
| BOSS | 22 | **23** | **+1** | 已登记偏差（口径 A：不裁剪） | 报告 §5.1 只记 21 个只读码，矩阵另给 `market:coupon:list` / `market:point:list` 2 个跨域只读码 |
| MANAGER | 40 | **53** | **+13** | 已登记偏差（口径 A：不裁剪） | 报告「清单 C」MANAGER 行是 OPERATOR 行复制，未含 12 个 `market:*` 遗留 CRUD 码；§5 矩阵对 MANAGER 判为应授 |
| OPERATOR | 36 | 36 | 0 | 一致 | — |
| CLERK | 2 | 2 | 0 | 一致 | — |

> 用户 2026-11-09 拍板取 **A**：保留 **51 / 23 / 53 / 36 / 2**，偏差登记在 `README.md` §4.1 与本段。裁剪到 22 / 40 会移除 MANAGER 的 `market:*` 删除类破坏性码，与用户对 MANAGER 的原始描述冲突。

### V4 · 菜单可见性模拟（复刻 `RbacService.listVisibleMenuByUserId`）—— ✅

| 用户 | 可见营销菜单数 | 说明 |
|---|:-:|---|
| admin | 19 | 7 既有 + 12 新增 |
| boss01 | 19 | 7 既有 + 12 新增 |
| manager01 | 19 | 7 既有 + 12 新增（本批新增绑定） |
| operator01 | 19 | 7 既有 + 12 新增（本批新增绑定） |
| clerk01 | 0 | 无任何菜单码绑定，符合只读收银台定位 |

### V5 · 前端路由守卫模拟（复刻 `router/index.ts` 的 `meta.permission`）—— ✅ 95 行

19 条路由 × 5 用户 = 95 组判定：

| 用户 | PASS | DENY(403) |
|---|:-:|:-:|
| admin | 19 | 0 |
| boss01 | 19 | 0 |
| manager01 | 19 | 0 |
| operator01 | 19 | 0 |
| clerk01 | 0 | **19（全部 DENY）** |

5 条共用 `mkt:activity` 守卫的路由（`/member-day`、`/style-gallery`、`/style-gallery/quick-order`、`/subscription`、`/ecommerce`、`/lifecycle`）对 4 个有权角色全部 PASS。

### V6 · 孤儿/残留检查

| check_item | cnt | 判定 |
|---|:-:|---|
| `orphan_permission_market_list`（`market:list` 占位码） | 1 | ⚠️ 已知遗留 L4（后端零引用），本批不绑定 |
| `menu_root_routepath_dup_market_coupon`（route_path = `/market/coupon`） | 2 | ⚠️ 已知遗留 L3（营销根菜单 + 旧路由），按裁决 3 本批不动 |
| `legacy_market_menu_rows_kept`（旧 `/market/*` 菜单行保留） | 4 | ✅ 符合裁决 3 |

### V7 · 只读断言

**BOSS 持有的写码（全部为非营销域，均为本批执行前既有绑定，本批未触碰）**：

```
biz:service-item:edit / dcep:qualification:edit / report:caliber:edit
report:export:add / sys:config:edit / sys:dcep:edit / sys:tenant:add / sys:tenant:edit
```

→ **营销域（`mkt:*` / `market:*` / `member:lifecycle` / `style:gallery` / `subscription`）写码 0 条** ✅

**OPERATOR 持有的写码**：

| 域 | 码 |
|---|---|
| 非营销域（既有绑定，本批未动） | `member:add` `member:delete` `member:edit` `member:level:edit` `member:lifecycle:edit` `member:message:edit` `member:style-preference:edit` `report:caliber:edit` `report:export:add` |
| 营销域（本批授予） | `mkt:birthday:edit` `mkt:channel:edit` `mkt:conversion:track` `mkt:effect:edit` `mkt:invite:edit` `mkt:journey:edit` `mkt:journey:trigger:edit` `mkt:member-day:edit` `mkt:template:edit` `mkt:touch:task:edit` `style:gallery:edit` `subscription:member:edit` |

→ **5 个受限 edit 码（`mkt:pricing:experiment:edit` / `mkt:pricing:rule:edit` / `mkt:dispatch:edit` / `mkt:ecommerce:edit` / `subscription:plan:edit`）OPERATOR 全部未持有** ✅（裁决 1、2 达成）
→ `mkt:conversion:track`（转化打点，非配置写）按设计保留。

### V8 · 中文名称写入编码校验 —— ✅ 标准 UTF-8 字节序

| permission_code | name_hex | 解码 |
|---|---|---|
| `mkt.birthday` | `E7949FE697A5E68F90E98692` | 生日提醒 |
| `mkt.member-day` | `E4BC9AE59198E697A5E887AAE58AA8E58C96` | 会员日自动化 |
| `mkt.style-gallery` | `E6ACBEE5BC8FE59BBEE5BA93` | 款式图库 |
| `mkt:dispatch:edit` | `E4B89AE58AA1E7AD96E795A5E4B88BE58F912DE7BC96E8BE91` | 业务策略下发-编辑 |

→ 无 mojibake、无替换字符（`?` / `EF BF BD`），中文名写入正确。

## 5. 回滚预案

`audit/evidence/F149-mkt-perm/rollback-batch2b.sql`（幂等）。该脚本已在 scratch 库 `mkt_scratch_2b` 实跑验证：残留新码 0、残留新菜单行 0、回滚后回到 `perm476 / rp913 / mp110`，与执行前**逐条 diff 无差异**。

⚠️ 已知限制：若后续 Phase 3 给这 10 个细分码补上 `@RequirePermission` 注解并重启应用，`PermissionAutoRegistrar` 会以 `permission_type='API'` 重新登记被删的新码，届时需人工再删。

## 6. 本批实际修复的问题

| 编号 | 问题 | 修复方式 | 验证 |
|---|---|---|---|
| B1 | 10 个前端守卫码在 `sys_permission` 中不存在 → 对应路由全员 403 | Part1 建 10 个粗码 | V1 ✅ |
| B2 | OPERATOR 只有 4 个菜单码、0 个按钮码 | Part4 补 34 规格码 + 10 粗码 + 19 菜单码 | V3/V4/V5 ✅ |
| B3 | 35 个后端注解码（`mkt:*` 细分码）零角色绑定 | Part4 按矩阵绑定（注：本批只建码不挂注解，注解落地属 Phase 3） | V3 ✅ |
| B4 | `mkt:activity` 被 6 个页面复用为守卫码且不存在 | 建 1 个粗码（裁决 4），Phase 3 拆分 | V1/V5 ✅ |
| M3 | CLERK 缺 `market:coupon:list` / `market:point:list` | Part4 授予 CLERK 2 码（裁决 5） | V3 ✅ |
| M4 | 12 条路由无 `sys_menu_permission` 行 → 侧边栏无入口 | Part3 建 12 条菜单行 | V2/V4 ✅ |

## 7. 未验证 / 已知限制

1. **本批未做浏览器人工验收**。V4 / V5 是用 SQL 复刻后端 `RbacService.listVisibleMenuByUserId` 与前端 `router/index.ts` 的 `meta.permission` 判定逻辑得出的**静态推演 + 数据库实跑证据**，不替代人工点击验收。验收清单见 `README.md` §9。
2. **`mkt:promotion` / `mkt:coupon` / `mkt:points` 读写不分**（M5）：BOSS 绑定后实际具备这三个模块的写能力（粗码无只读变体），Phase 3 拆分时解决。
3. **10 个细分码本批未挂后端注解**：`mkt:birthday:list` / `mkt:birthday:edit` / `mkt:member-day:list` / `mkt:member-day:edit` / `mkt:ecommerce:list` / `mkt:ecommerce:edit` / `mkt:pricing:rule:list` / `mkt:pricing:rule:edit` / `mkt:dispatch:list` / `mkt:dispatch:edit` 的 `@RequirePermission` 需 Phase 3 补（5 个 Service/Controller 当前零注解，H1）。
4. **10 个粗码后端零引用** → 纯前端守卫码，绑给 BOSS 仍是只读语义（已 grep 全量确认）。
5. **`marketing` 根菜单 `route_path` 仍指向 `/market/coupon`**（L3），旧 `/market/*` 三条路由按裁决 3 保留到 Phase 3。
6. **`market:list`（id 1138）仍零绑定**（L4，占位码）。
7. **口径偏差**（BOSS +1 / MANAGER +13）按口径 A 保留，详见 §4 的 V3b 与 `README.md` §4.1。
