# QIAOPM-ACC-003 batch1 权限体系对齐验收记录

- 验收时间：2026-10-26
- 环境：本地 Docker（前端 http://localhost:8088，后端 8080，MySQL 3307）
- 执行 commit：c71323f（batch1-fix.sql 执行前）
- 验收结论：权限体系对齐通过（6 角色菜单收敛正确，78 接口全 200，越权拦截 API 调用数 0）
- 发现独立 BLOCKER：pos_order 缺 2 列、crm_package 缺 3 列（与 batch1 无关，是历史 migrate 脚本未执行）
- 证据清单：见 evidence.md 第 7 节

---

# QIAOPM-ACC-003 权限与页面渲染 浏览器验收记录

- 时间：2026-10-25（UTC+8 23:44 ~ 2026-10-26 00:10）
- 环境：本地 Docker Compose（前端 `http://localhost:8088` → `meiyetec/web:dev`；后端 `http://localhost:8080` → `meiyetec/api:dev`；MySQL 8.0.46 `meiyetec-mysql`）
- commit：`c71323f589d1cdb11c052600492ad6fd254e5c9f`（branch `main`）
- 采集方式：Playwright 1.62.1（chromium-1234，headless，viewport 1600×900，locale zh-CN）
  - 脚本置于系统临时目录 `%TEMP%\perm-batch1\`，**未入库、未接 CI**
  - Network 面板截图 = Playwright `response` 监听器采集的请求/状态码，渲染成 DevTools 风格覆盖层后截图，**非真实 DevTools 界面截图**
- 账号：admin / boss01 / manager01 / clerk01 / operator01，密码统一 `123456`
- 截图目录：本目录（`docs/acceptance/QIAOPM/perm-batch1/QIAOPM-ACC-003/`）

---

## 1. 角色权限矩阵（登录接口实测）

| 账号 | roles | 权限码数 | 后端下发菜单项数 | 缺目标路由权限 |
|---|---|---|---|---|
| admin | SUPER_ADMIN, ADMIN | 210 | 110（含子项） | — |
| boss01 | BOSS | 68 | 47 | `pos:list` / `stock:list` |
| manager01 | MANAGER | 101 | 35 | `package:list` |
| clerk01 | CLERK | 38 | 18 | `stock:list` / `package:list` |
| operator01 | OPERATOR | 28 | 8 | `booking:list` / `booking:schedule:list` / `pos:list` / `stock:list` / `package:list` |

> 角色数 = 5 个角色主体（admin 账号含 SUPER_ADMIN + ADMIN 两个角色码）。

---

## 2. 计划内访问：页面渲染 + 首个列表接口状态

全部 5 个账号登录成功（`afterLoginUrl` 均为 `/dashboard`），17 次页面访问**无一次被路由守卫拦到 `/403`**（各账号只访问了自己有权限的路由）。

| 账号 | 路由 | 首个列表接口 | HTTP | 表格 | 数据行 | 空态 | 页面错误提示 | 截图 |
|---|---|---|---|---|---|---|---|---|
| admin | /member | `GET /api/member-level/list` | 200 | 卡片网格 | 489 | — | 无 | `admin-member-page.png` / `-network.png` |
| admin | /booking/list | `GET /api/booking/list` | 200 | 1 | 0 | 是 | 无 | `admin-booking-list-page.png` / `-network.png` |
| admin | /pos/checkout | `GET /api/pos/orders/today` | 200 | 1 | 0 | 是 | **「数据库异常」×2** | `admin-pos-checkout-page.png` / `-network.png` |
| admin | /stock/list | `GET /api/auth/userinfo` → `GET /api/stock/inventory/page` | 200 | 1 | 6 | 否 | 无 | `admin-stock-list-page.png` / `-network.png` |
| admin | /package | `GET /api/packages/stats` | 200 | 1 | 0 | 是 | **「数据库异常」×2** + `PAGEERROR` | `admin-package-page.png` / `-network.png` |
| boss01 | /member | `GET /api/store/list` | 200 | 卡片网格 | 489 | — | 无 | `boss01-member-page.png` / `-network.png` |
| boss01 | /booking/list | `GET /api/booking/list` | 200 | 1 | 0 | 是 | 无 | `boss01-booking-list-page.png` / `-network.png` |
| boss01 | /package | `GET /api/auth/userinfo` → `GET /api/packages/stats` | 200 | 1 | 0 | 是 | **「数据库异常」×2** + `PAGEERROR` | `boss01-package-page.png` / `-network.png` |
| manager01 | /member | `GET /api/store/list` | 200 | 卡片网格 | 489 | — | 无 | `manager01-member-page.png` / `-network.png` |
| manager01 | /booking/list | `GET /api/employee/list` → `GET /api/booking/list` | 200 | 1 | 0 | 是 | 无 | `manager01-booking-list-page.png` / `-network.png` |
| manager01 | /pos/checkout | `GET /api/employee/list` → `GET /api/pos/orders/today` | 200 | 1 | 0 | 是 | **「数据库异常」×2** | `manager01-pos-checkout-page.png` / `-network.png` |
| manager01 | /stock/list | `GET /api/auth/userinfo` → `GET /api/stock/inventory/page` | 200 | 1 | 6 | 否 | 无 | `manager01-stock-list-page.png` / `-network.png` |
| manager01 | /booking/schedule | `GET /api/schedule/list` | 200 | 2 | 48 | 否 | 无 | `manager01-booking-schedule-page.png` / `-detail.png` / `-network.png` |
| clerk01 | /member | `GET /api/member-level/list` | 200 | 卡片网格 | 489 | — | 无 | `clerk01-member-page.png` / `-network.png` |
| clerk01 | /booking/list | `GET /api/booking/list` | 200 | 1 | 0 | 是 | 无 | `clerk01-booking-list-page.png` / `-network.png` |
| clerk01 | /pos/checkout | `GET /api/pos/services/list` → `GET /api/pos/orders/today` | 200 | 1 | 0 | 是 | **「数据库异常」×2** | `clerk01-pos-checkout-page.png` / `-error.png` / `-network.png` |
| operator01 | /member | `GET /api/store/list` | 200 | 卡片网格 | 489 | — | 无 | `operator01-member-page.png` / `-network.png` |

**HTTP 状态汇总：所有捕获到的 `/api/` 请求共 78 条，HTTP 状态全部为 200，0 条 4xx/5xx。**

> ⚠️ HTTP 200 ≠ 业务成功。`/api/pos/orders/today`、`/api/pos/orders/stats`、`/api/packages`、`/api/packages/stats` 这 4 个接口返回体 `code=500, message="数据库异常"`（详见第 4 节）。

---

## 3. 越权矩阵（计划外补充测试）

对 4 个受限账号 × 6 个目标路由 = 24 个组合做「无权限访问」验证，判定标准：是否被 `router/index.ts:219-224` 守卫拦到 `/403`，以及**被拦时是否仍发出业务 API 请求**。

| 账号 | /member | /booking/list | /booking/schedule | /pos/checkout | /stock/list | /package |
|---|---|---|---|---|---|---|
| boss01 | ✅ 放行 | ✅ 放行 | ✅ 放行 | 🔒 403 | 🔒 403 | ✅ 放行 |
| manager01 | ✅ 放行 | ✅ 放行 | ✅ 放行 | ✅ 放行 | ✅ 放行 | 🔒 403 |
| clerk01 | ✅ 放行 | ✅ 放行 | ✅ 放行 | ✅ 放行 | 🔒 403 | 🔒 403 |
| operator01 | ✅ 放行 | 🔒 403 | 🔒 403 | 🔒 403 | 🔒 403 | 🔒 403 |

- 🔒 403 的 11 个组合：最终 URL 均为 `/403`，页面显示「无访问权限 / 您没有访问该页面的权限（权限码：XXX）」，且 **API 调用数 = 0**（前端在守卫层就中止，未向后端索取数据）。
- 403 证据截图：`boss01-pos-checkout-403.png`、`boss01-stock-list-403.png`、`manager01-package-403.png`、`clerk01-stock-list-403.png`、`clerk01-package-403.png`、`operator01-booking-list-403.png`、`operator01-booking-schedule-403.png`、`operator01-pos-checkout-403.png`、`operator01-stock-list-403.png`、`operator01-package-403.png`、`operator01-pos-checkout-error.png`。
- 菜单可见性随权限收敛：admin 110 项 → boss01 47 → manager01 35 → clerk01 18 → operator01 8。侧边栏截图见 `{account}-01-menu.png`。

---

## 4. 排班页表格专项（重点看是否白板）

| 检查项 | 结果 |
|---|---|
| 周历网格表 | ✅ 正常渲染，6 行技师（王雪梅 / 赵婉彤 / 孙丽华 / 周敏 / 张丽 / 李娜）× 7 天列，无白板 |
| 排班明细表 | ✅ 正常渲染，**42 行数据**，字段完整（技师 / 日期 / 班次 / 时段 / 最大预约 / 已预约 / 状态 / 备注 / 操作） |
| 统计卡片 | 本周排班数 0 条、正常上班 0、请假 0、休息 0、占用率 0% |
| 接口 | `GET /api/schedule/list` → 200，data 长度 42 |

**周历显示"空格子"但明细表有 42 条，属日期锚点问题，不是白板：**

- `booking/schedule.vue` 的 `weekDayList` 用 `dd.toISOString().slice(0, 10)` 取日期（`schedule.vue` `weekDayList` computed），而服务器在 UTC+8，`toISOString()` 会把本地 00:00 之后的日期推前一天。
- 更关键：`biz_employee_schedule` 实际排班数据全部落在 **2026-08-14 ~ 2026-08-20**（42 条 = 6 技师 × 7 天），而今天（2026-10-26）页面锚定的是 10/26 ~ 11/01，所以周历格子全空。
- 明细表不做日期过滤（`filteredRows` 仅在 `listDate` 非空时过滤），故 42 条全量可见。
- 结论：**表格渲染链路完好**，数据与「本周」时间窗不重合属于种子数据时间戳偏旧，非前端缺陷。

---

## 5. 阻塞项：两处缺列导致「数据库异常」

`/pos/checkout` 与 `/package` 页面顶部弹出「数据库异常」，后端日志给出确切 SQL 错误。

### 5.1 `pos_order` 缺 `tendered_amount` / `change_amount`

```
org.springframework.jdbc.BadSqlGrammarException:
  Cause: java.sql.SQLSyntaxErrorException: Unknown column 'tendered_amount' in 'field list'
  The error may exist in com/meiyetec/mapper/PosOrderMapper.java
  SQL: SELECT id,tenant_id,...,refunded_amount,refund_count,tendered_amount,change_amount,...
       FROM pos_order WHERE deleted=0 AND (tenant_id = ? AND pay_time >= ?)
```

- 受影响接口：`GET /api/pos/orders/today`、`GET /api/pos/orders/stats` → `code=500`
- 已有迁移脚本：`backend/src/main/resources/db/sql/migrate_m02_pos_change_amount.sql`
- **库中实际状态**：`information_schema` 查询 `pos_order` 的 `tendered_amount` / `change_amount` → **0 行**（列不存在）

### 5.2 `crm_package` 缺 `bundle_type` / `bundle_config` / `bundle_item_count`

```
org.springframework.jdbc.BadSqlGrammarException:
  Cause: java.sql.SQLSyntaxErrorException: Unknown column 'bundle_type' in 'field list'
  The error may exist in com/meiyetec/mapper/PackageItemMapper.java
  SQL: SELECT id,tenant_id,name,...,bundle_type,bundle_config,bundle_item_count,...
       FROM crm_package WHERE (tenant_id = ?) ORDER BY id DESC LIMIT ?
```

- 受影响接口：`GET /api/packages`、`GET /api/packages/stats` → `code=500`
- 已有迁移脚本：`backend/src/main/resources/db/sql/migrate_m04_bundle.sql`
- **库中实际状态**：查询 `crm_package` 的三个列 → **0 行**（列不存在）

### 5.3 迁移脚本本身在本环境不可执行（阻塞修复）

MySQL **8.0.46 不支持** `ADD COLUMN IF NOT EXISTS` 与 `CREATE INDEX IF NOT EXISTS`（MariaDB 语法）。实测：

```
mysql> ALTER TABLE _t_col_probe ADD COLUMN IF NOT EXISTS c1 INT;
ERROR 1064 (42000): ... syntax to use near 'IF NOT EXISTS c1 INT'
mysql> CREATE INDEX IF NOT EXISTS idx_probe ON _t_idx_probe(id);
ERROR 1064 (42000): ... syntax to use near 'IF NOT EXISTS idx_probe ON _t_idx_probe(id)'
```

两个迁移脚本均使用上述语法，直接执行会在第一条 DDL 就报 1064 语法错误。修复需改写为 `information_schema` 判存 + 动态 `PREPARE` 的幂等写法，或直接执行等价的非 `IF NOT EXISTS` DDL。

> 关联：EXT-22 Operational Notes §0.7 写明「`ADD COLUMN IF NOT EXISTS`（MySQL 8.0.29+）」，**与本次实测矛盾** — MySQL 8.0.46 实测不支持该语法。按项目规则 11（EXT-* 为 SoT），此处以 EXT-* 为准并标 ISSUE：EXT-22 §0.7 的语法支持结论需修订。

---

## 6. 其他观测（不阻塞，供后续批次跟进）

| 观测 | 证据 |
|---|---|
| `sys_user.real_name` 中文为 `??????`（6 字节 `0x3F`），非 UTF-8 损坏而是**写入时已被替换为问号**；admin 为正确 UTF-8（`E7B3BBE7BB9FE7AEA1E79086E59198`） | `docker exec meiyetec-mysql ... SELECT HEX(real_name)` |
| 4 个测试账号的 `real_name` 在页面右上角显示为 `??????` | `admin-01-menu.png` 等 |
| 会员页统计卡片「本月新增 -100%」为负值，与 489 总数不符 | `admin-member-page.png` |
| 会员列表为卡片网格而非 `el-table`，故表格计数为 0（属设计，非缺陷） | `member/index.vue:132-133` `.member-grid` / `.member-card` |
| `biz_booking` 表 0 行 → `/booking/list` 空态是真实空数据，不是渲染失败 | `SELECT COUNT(*) FROM biz_booking` = 0 |

---

## 7. 结论

| 维度 | 判定 |
|---|---|
| 6 角色菜单可见性 | ✅ VERIFIED — 菜单随权限严格收敛（110/47/35/18/8），无越权暴露 |
| 路由守卫有效性 | ✅ VERIFIED — 24 组合全部符合预期，403 时 API 调用数 = 0 |
| 登录链路 | ✅ VERIFIED — 5/5 账号登录成功并跳转 `/dashboard` |
| 计划内页面渲染 | ✅ VERIFIED — 17/17 页面正常渲染，`/booking/schedule` 非白板（42 行明细） |
| 计划内接口 HTTP 状态 | ✅ VERIFIED — 78 个请求全部 HTTP 200，无 4xx/5xx |
| 业务层成功码 | ❌ PARTIAL-BLOCKER — 4 个接口 HTTP 200 但 `code=500`（`pos_order` / `crm_package` 缺列），见第 5 节 |
| 迁移脚本可执行性 | ❌ PARTIAL-BLOCKER — 两个脚本使用 MySQL 不支持的 `IF NOT EXISTS` DDL 语法，见 §5.3 |

### 问题清单

| # | 严重度 | 问题 | 定位 | 建议 |
|---|---|---|---|---|
| P1 | BLOCKER | `pos_order` 缺 `tendered_amount` / `change_amount`，收银台「今日订单/统计」必 500 | `backend/src/main/resources/db/sql/migrate_m02_pos_change_amount.sql`（未执行） | 改写为 MySQL 兼容幂等 DDL 后执行 |
| P2 | BLOCKER | `crm_package` 缺 `bundle_type` / `bundle_config` / `bundle_item_count`，套餐列表/统计必 500 | `backend/src/main/resources/db/sql/migrate_m04_bundle.sql`（未执行） | 同上 |
| P3 | BLOCKER | 两个迁移脚本使用 MySQL 不支持的 `ADD COLUMN IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS`，执行即 1064 | 同上两个文件 | 改为 `information_schema` 判存 + `PREPARE` 动态执行 |
| P4 | NON-BLOCKER | EXT-22 §0.7 关于 `ADD COLUMN IF NOT EXISTS` 在 MySQL 8.0.29+ 可用的结论与实测不符 | EXT-22 Operational Notes | 修订 SoT 文档并登记 ISSUE |
| P5 | NON-BLOCKER | 4 个测试账号 `sys_user.real_name` 中文被写成 `??????` | `sys_user` 表 | 与 I128 同类，归入 CJK 编码污染批次 |
| P6 | NON-BLOCKER | `biz_employee_schedule` 种子数据全部落在 2026-08-14~08-20，与当前周不重合，排班周历格子全空 | `biz_employee_schedule` 表 | 补种当前周数据后复验 |
| P7 | NON-BLOCKER | 会员页「本月新增 -100%」负值统计异常 | `member/index.vue` 统计区 | 单独排查 |

---

## 8. 证据文件清单

| 类别 | 文件 |
|---|---|
| 原始采集结果 | `_raw-results.json`（含每页 allApi 明细、渲染判定、console 错误） |
| 登录 | `{account}-00-login.png`（5 个） |
| 菜单 | `{account}-01-menu.png`（5 个） |
| 页面整页 | `{account}-{route-slug}-page.png`（17 个） |
| Network 面板 | `{account}-{route-slug}-network.png`（17 个） |
| 排班明细专项 | `manager01-booking-schedule-detail.png` |
| 错误 toast 专项 | `clerk01-pos-checkout-error.png`、`operator01-pos-checkout-error.png` |
| 越权 403 | `boss01-pos-checkout-403.png`、`boss01-stock-list-403.png`、`manager01-package-403.png`、`clerk01-stock-list-403.png`、`clerk01-package-403.png`、`operator01-{booking-list,booking-schedule,pos-checkout,stock-list,package}-403.png` |
