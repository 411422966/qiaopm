# F174 — I202-P2 权限补绑 26 条（A-mid）+ D2 前端 meta 对齐

> **Feature ID**：F174
> **关联 Issue**：I202（前后端权限码漂移，全量口径）、I206（mh 三页后端零鉴权）、I207（mh/consent 契约漂移）
> **方案文档**：`audit/reports/i202-p2-perm-binding-plan.md`
> **落地范围**：`sys_role_permission` 补绑 **26** 行 + `frontend/meiyetec-web/src/router/index.ts` 三行 `meta.permission`
> **执行日期**：2026-11-26（UTC+8）
> **验证等级**：**L4 — 运行期 VERIFIED**（DB 后态 + HTTP 端点 + 浏览器页面级三重实测；**唯二缺口**：① 执行前 1267 基线快照未在本轮重新采集，② INSERT 事务的客户端 stdout 未落盘 → 两点均以「增量等价证明」替代，详见 §2.2）

---

## 1. 基线绑定（规则 13：commit hash + DB snapshot + env.md）

| 项 | 值 | 采集方式 |
|---|---|---|
| 仓库根 | `D:\QiaoPM` | — |
| 分支 | `main` | `git branch --show-current` |
| **HEAD commit（验证时）** | `d102a3f098de7956037b8d2e23a6a021405589b8`（短 `d102a3f`） | `git rev-parse HEAD` |
| **落地提交（验证后）** | 与本 Evidence 同批提交 —— `git log --oneline -1`，message = `fix(datascope): I202 P2 权限补绑 26 条 + D2 前端 meta 对齐`（因补记本行而 amend 过一次，hash 以 `git log` 实际值为准） | `git commit --amend` |
| 工作树 | 验证时为 **dirty**（本批改动未提交）；提交后除 `tmp_*.txt` 临时草稿外为 clean | `git status` |
| **DB** | MySQL 8.0.46 / `localhost:3307` / 库 `meiyetec` / root / utf8mb4 | docker 容器 `meiyetec-mysql` |
| 环境清单 | `audit/baseline/env.md`（已存在，本批未改动） | — |
| DB schema 快照 | `audit/baseline/db-snapshot.sql`（**2026-09-12 生成，已过期**，本批未刷新） | — |
| **本批相关表 DDL** | `sys_role_permission`：`UNIQUE KEY uk_role_permission (role_id, permission_id)`、`AUTO_INCREMENT=5760` | `SHOW CREATE TABLE`（本轮实测） |

> ⚠️ **DB snapshot 未刷新**：`audit/baseline/db-snapshot.sql` 仍是 2026-09-12 的 schema-only dump，**不含**本批 26 行的数据状态。本批的 DB 事实以 §2 的运行期输出为准（比 schema dump 更强），但 baseline 目录本身的 staleness 属**已知遗留**（不在本批范围）。

---

## 2. 落地内容

### 2.1 数据库：26 条 `sys_role_permission` 补绑（方案 A-mid）

| 权限码 | permission_id | 目标角色 | 行数 |
|---|---|---|---|
| `spa:scene:list` | 1564 | 4, 5, 6, 8 | 4 |
| `spa:scene:edit` | 1565 | 5, 8 | 2 |
| `iot:link:list` | 1479 | 5, 6, 8 | 3 |
| `iot:rule:list` | 1552 | 5, 6, 8 | 3 |
| `iot:alert:list` | 1548 | 5, 6, 8 | 3 |
| `iot:data:list` | 1550 | 5, 6, 8 | 3 |
| `iot:rule:edit` | 1553 | 5, 8 | 2 |
| `iot:alert:edit` | 1549 | 5, 8 | 2 |
| `iot:data:edit` | 1551 | 5, 8 | 2 |
| `iot:link:edit` | 1480 | 5, 8 | 2 |
| **合计** | — | — | **26** |

**未触碰（方案决策）**：`iot:link:execute`(1481) 保持 **0 绑定**（P2=b）；`crm:care:recipe:list`(1494) 保持原绑 **3 角色**（P1=a，不补 CLERK）。

**执行 SQL / 回滚 SQL**：见 `audit/reports/i202-p2-perm-binding-plan.md` §1（26 条 INSERT + 断言 + COMMIT）与 §2（26 条逐条镜像 DELETE + 断言 + COMMIT）。回滚后应回到 1267 / 118·47·0·54·304·224·105·415。

### 2.2 ⚠️ 执行前基线（1267）未在本轮直接采集 —— 增量等价证明

**事实**：本轮开始时，DB `sys_role_permission` 已是 **1293** 行，且工作树中 `router/index.ts` 三行已改。**即 26 条 INSERT 与 D2 三行改动在本轮指令到达之前已落地**，故「V1 执行前 = 1267」无法在本轮重采。

**替代证明（增量等价，运行时实测）**：

| role_id | role_code | 方案基线 | 本轮实测 | 增量 | 方案预期增量 | 一致 |
|---|---|---|---|---|---|---|
| 1 | SUPER_ADMIN | 118 | 118 | 0 | 0 | ✅ |
| 2 | STORE_ADMIN | 47 | 47 | 0 | 0 | ✅ |
| 3 | EMPLOYEE | 0 | 0 | 0 | 0 | ✅ |
| 4 | CLERK | 54 | 55 | **+1** | +1（`spa:scene:list`） | ✅ |
| 5 | MANAGER | 304 | 314 | **+10** | +10 | ✅ |
| 6 | BOSS | 224 | 229 | **+5** | +5 | ✅ |
| 7 | OPERATOR | 105 | 105 | 0 | 0 | ✅ |
| 8 | ADMIN | 415 | 425 | **+10** | +10 | ✅ |
| — | **合计** | **1267** | **1293** | **+26** | **+26** | ✅ |

> 交叉校验：`118+47+0+55+314+229+105+425 = 1293` = `COUNT(*) FROM sys_role_permission` ✅（DB 内 `matches_total = 1`）
> 负向对照：未被本批触碰的 `iot:link:execute` = 0、`crm:care:recipe:list` = 3 ✅

**未确认项**：① 执行前 1267 的**直采快照**未留证；② INSERT 事务的 mysql 客户端 stdout 未落盘。两者以**上表的增量等价证明 + V2 精确 26 行**替代，证据等级由「L5（含执行前快照与执行日志）」降为 **L4（后态运行期验证）**。

### 2.3 前端：D2 方案 A — `router/index.ts` 三行 `meta.permission`

```diff
@@ -153,1 +153,1 @@
-      { path: 'mh/visual', name: 'MhVisual', ... permission: 'mh:visual' },
+      { path: 'mh/visual', name: 'MhVisual', ... permission: 'mh.visual' },
@@ -154,1 +154,1 @@
-      { path: 'mh/consent', name: 'MhConsent', ... permission: 'mh:consent' },
+      { path: 'mh/consent', name: 'MhConsent', ... permission: 'mh.consent' },
@@ -167,1 +167,1 @@
-      { path: 'complication', name: 'Complication', ... permission: 'mh:consent' },
+      { path: 'complication', name: 'Complication', ... permission: 'mh.consent' },
```

- **不改** `views/mh/visual.vue` / `views/mh/consent.vue` / `views/dashboard/**`（用户已验证页面）。
- 库中既有码：`mh.visual`(id=1251) / `mh.consent`(id=1252)，绑 role 1 / 6 / 8 → BOSS(6) 放行，MANAGER / CLERK / OPERATOR 仍 403（属预期）。
- **构建产物核验**：本地 `dist/assets/router-Bi9r1y5L.js` 内 `mh.visual` 命中、`mh:visual` **0 命中**；容器 `meiyetec-web`（`/usr/share/nginx/html/assets/`）同一 chunk（`router-Bi9r1y5L.js`）内容一致 → **镜像内构建产物 = 当前源码**。

---

## 3. 验证输出

### 3.1 DB 验证（V1–V7）

原始输出：`v1-v6-verification-output.txt`；查询脚本：`v1-v6-verification.sql`（只读 SELECT）。

| 查询 | 期望 | 实测 | 判定 |
|---|---|---|---|
| V1 目标码绑定数 | `iot:link:execute`=0 / `crm:care:recipe:list`=3，其余按方案 | spa:scene:list=4 / spa:scene:edit=2 / iot:link:list=3 / iot:rule:list=3 / iot:alert:list=3 / iot:data:list=3 / iot:rule:edit=2 / iot:alert:edit=2 / iot:data:edit=2 / iot:link:edit=2 / **iot:link:execute=0** / **crm:care:recipe:list=3** | ✅ |
| V2 目标行逐条核对 | **26 行**，码与权限表一一对应 | **26 行**，role/permission_id/permission_code 全对齐 | ✅ |
| V3 分角色总数 | 118/47/55/314/229/105/425 | 全部一致 | ✅ |
| V4 总数 + 交叉校验 | 1293 且与分角色之和相等 | 1293，`matches_total = 1` | ✅ |
| V5 孤儿行 / 重复组 | 孤儿仍 3（基线既有，本批不碰）/ 重复组 0 | 3 / 0 | ✅ |
| V6 负向对照 | `iot:link:execute`=0 / `crm:care:recipe:list`=3 | 0 / 3 | ✅ |
| V7 断言计数 | 26 | 26 | ✅ |

### 3.2 HTTP 端点验收（5 账号 × 12 端点）

原始输出：`api-acceptance-output.txt`。请求经 `http://localhost:8088/api`（nginx → `meiyetec-api:8080`）。

| 账号 | 端点 | 实测 | 判定 |
|---|---|---|---|
| manager01 | `GET /spa-scene/list` | **200** | ✅ 符合预期（`spa:scene:list` → role 5） |
| boss01 | `GET /spa-scene/list` | **200** | ✅（role 6） |
| clerk01 | `GET /spa-scene/list` | **200** | ✅（role 4，方案 A-mid 新增） |
| operator01 | `GET /spa-scene/list` | **403** | ✅ **属预期**（未绑 role 7） |
| admin | `GET /spa-scene/list` | 200 | ✅（`*` 通配） |
| manager01 / boss01 / admin | `GET /iot/rule/list`、`/iot/alert/list`、`/iot/data/list`、`/iot/device/link/list` | **全部 200** | ✅ |
| clerk01 / operator01 | 同上 4 个 | **全部 403** | ✅ **属预期**（未绑 role 4 / 7） |
| **manager01** | **`POST /iot/device/link/execute`** | **403** | ✅ **属预期，不是失败**（`iot:link:execute`(1481) 未绑角色，P2=b 决定不补；请求用不存在的 `linkId=99999999` 兜底，即使放行也不会命中真实联动） |
| boss01 | `POST /iot/device/link/execute` | 403 | ✅ 属预期 |
| admin | `POST /iot/device/link/execute` | 200 | ✅（`*` 通配，SUPER_ADMIN） |
| manager01 / boss01 / admin | `GET /mh/visual/archive/page`、`GET /mh/consent/stats` | 全部 200 | ⚠️ 后端**零鉴权**（I206 Open），200 属既有设计缺陷，非本批引入 |

**登录载荷核对（前端守卫判定的真实输入）**

| 账号 | roles | `mh*` | `spa:scene*` | `iot*` |
|---|---|---|---|---|
| manager01 | MANAGER | — | list, edit | link:list/edit, alert:list/edit, data:list/edit, rule:list/edit（**无 link:execute**） |
| boss01 | BOSS | mh, mh.visual, mh.consent | list | link:list, alert:list, data:list, rule:list（**无 edit**） |
| clerk01 | CLERK | — | list | — |
| operator01 | OPERATOR | — | — | — |
| admin | SUPER_ADMIN, ADMIN | mh, mh.visual, mh.consent | list, edit | 全 8 码 |

→ 与方案 §5 验收表**逐格一致**。

### 3.3 浏览器页面级验收

环境：`http://localhost:8088`（nginx 容器 `meiyetec-web`），真实登录 + 真实路由跳转，非静态推论。

| # | 账号 | 路由 | 实测表现 | 判定 | 截图 |
|---|---|---|---|---|---|
| 1 | manager01 | `/spa-scene` | 标题「SPA场景模式」，3 tab（场景模式 / 房间组合 / 预约演示）+「新增场景」按钮，空态文案「暂无场景模式，点击「新增场景」开始」→ **主数据接口 200 且返回空集**（若 403 则出现错误提示而非业务空态） | ✅ | `01-manager01-spa-scene.png` |
| 2 | manager01 | `/iot/management` | 标题「IoT设备联动」，4 tab（设备管理 / 数据采集 / 联动规则 / 告警中心）+ 类型/状态下拉 +「新增设备」按钮，设备空态「暂无设备」 | ✅ | — |
| 3 | manager01 | 页内同源 fetch 复核 | `/api/iot/rule/list` 200、`/api/iot/alert/list` 200、`/api/iot/data/list` 200、`/api/iot/device/link/list` 200、`/api/iot/device/list` 200、`/api/spa-scene/list` 200（bodyLen=1550，有数据） | ✅ | — |
| 4 | **boss01** | **`/mh/visual`** | 标题「视觉档案」，4 tab（视觉档案 / AI 对比 / 图像授权 / 撤回申请）+ 7 项筛选 +「新建档案」按钮，**表格渲染出 3 条数据行**；侧边栏出现「医美合规 → 视觉档案 / 合规记录」 | ✅ **D2 生效（修复前恒 403）** | `02-boss01-mh-visual.png` |
| 5 | **boss01** | **`/mh/consent`** | 标题「医美合规」，5 tab（知情同意 / 药械主档 / 批次 / 使用追溯 / 签署记录），模板表带数据、按钮齐全 | ✅ **D2 生效** | — |
| 6 | **boss01** | **`/complication`** | 标题「并发症 SOP」，2 tab（SOP 模板 / 并发症事件）+「初始化 5 个默认 SOP」「新建 SOP」 | ✅ **D2 生效** | — |
| 7 | boss01 | 页内同源 fetch 复核 | `/api/mh/visual/archive/page` 200、`/api/mh/consent/stats` 200、`/api/spa-scene/list` 200、`/api/iot/rule/list` 200 | ✅ | — |

> `manager01` 访问 `/mh/visual` 仍为 **403 属预期**：manager01 登录载荷中无 `mh.visual`（§3.2 载荷表），前端守卫 `hasPermission()` 精确匹配 → 判 false。符合方案 §5.4。

---

## 4. 本批明确未做（边界）

- 未新增 `sys_permission` 行；未修 3 行孤儿绑定；未修 `iot:link:execute` / `crm:care:recipe:list` 绑定。
- 未改 `views/mh/visual.vue` / `views/mh/consent.vue` / `views/dashboard/**`（用户已验证页面）。
- 未改后端 Java 代码（**本批无后端代码改动**）。
- 未动 `/spa-scene` 菜单行 358（`booking:spa-scene`(1295) 仅绑 role 8）—— P4=a 接受 URL 直达，登记为菜单域遗留。
- 未动 IoT 菜单（`sys_menu_permission` 中 `route_path LIKE '%iot%'` 命中 0 行）—— P5=a 本批不管。
- 未修 IoT 域 `/iot/device` 零鉴权（`IoTDeviceService` 10 个方法级端点无 `@RequirePermission`）—— 须另开 issue。

---

## 5. 遗留与后续

| # | 事项 | 等级 | 状态 |
|---|---|---|---|
| 1 | **I206**：mh / visual / consent / complication 四个类 **41 个方法级端点零 `@RequirePermission`**，D2 后前端守卫成为唯一门禁 | P1 | **Open**（仅登记） |
| 2 | **I207**：`views/mh/consent.vue` **6 个端点 / 7 个调用点**后端无实现（toggle / version list / new-version / sign-link issue / drug-device toggle / batch status） | P2 | **Open**（仅登记） |
| 3 | `MhConsentH5Controller` 的 `/mh/consent/sign-link/*` 0 注解属设计，但 `WebConfig` 未排除 `/mh/consent/**` → 无 token 调 sign-link = **401**，与「免登录」描述不符 | P1 | 记于 I206 §1.2 第 6 点 + I202 §1.3 E2 |
| 4 | 前端码口径已修正为 **20 个前端码**（D2 实际修复 2 码 / 3 路由；码 22−2=20、路由 44−3=41） | 文档口径 | ✅ **已销账**（用户 2026-11-26 确认原「剩余 19 个」表述有误；I202 标题 / §0 / §1.2 / §7 R1 + register I202 行同步改毕） |
| 5 | `audit/baseline/db-snapshot.sql` 仍为 2026-09-12 版本，未含本批 DB 状态 | 基线治理 | **已知遗留**，不在本批范围 |
| 6 | `audit/issues/issue-register.md` 的 I202 行「状态」列仍写「P2 后端补码未做」，与本批已补 26 条绑定的事实存在措辞滞后 | 文档滞后 | **待用户批准后统一**（登记表属治理基线） |

---

## 6. 证据清单

| 文件 | 内容 |
|---|---|
| `v1-v6-verification.sql` | V1–V7 只读验证查询（非正式工具，仅本批证据用） |
| `v1-v6-verification-output.txt` | V1–V7 原始输出 |
| `api-acceptance-output.txt` | 5 账号 × 12 端点 HTTP 状态码原始输出 |
| `01-manager01-spa-scene.png` | manager01 `/spa-scene` 页面截图（**本地留存，未入库** —— `.gitignore:148` 的 `*.png` 规则；`.gitignore` 属治理基线，未经批准不修改；`git ls-files "audit/evidence/**"` 历史入库图片数 = 0 → 与仓库现行策略一致） |
| `02-boss01-mh-visual.png` | boss01 `/mh/visual` 页面截图（同上，本地留存未入库） |
| `../i202-p2-perm-binding-plan.md` | 方案文档（执行 SQL / 回滚 SQL / 验收表 / I206 / I207 拟登记文本） |
