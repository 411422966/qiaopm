# F131 · 优惠券发放链路的 P0 护栏与 405 修复（问题 2 + 3）

> 批次：本批只修「问题 2（`/batch/page` 405）+ 问题 3（P0 批量误发）」；问题 1（`/m09/invite`）放下一批单独评估。
> 基线 commit：`39dfa1935e1112ebb1fdb256e01412338c66764e`（`docs(audit): 更新 I130 记录 batch2-B 营销域已修复`）
> 工作区状态：dirty（本批改动均未提交，见 §6 文件清单）
> 环境：`audit/evidence/env.md`（项目级运行环境）；DB 快照见 `backup/before-batch2b.sql` 与 `audit/baseline/db-snapshot.sql`
> 运行时：docker compose 服务 `api`（`meiyetec-api`，镜像 `meiyetec/api:dev`，本批重新构建并重建容器）、`web`（`meiyetec-web`，`localhost:8088`）
> 验证时间：2026-11-09 21:45 ~ 21:53 (UTC+8)

---

## 1. 缺陷清单与处置

| # | 缺陷 | 根因（证据） | 处置 | 状态 |
|---|---|---|---|---|
| D1 | `GET /mkt/coupon/batch/page` 返回 405 | `MktCouponController` 只有 `@PostMapping("/batch/page")`，前端 `views/mkt/coupon.vue:221` 用的是 GET | 保留 POST，新增 `@GetMapping("/batch/page")` 别名（`@RequestParam`），两者共用 `queryBatchPage(...)` | ✅ 已修，raw/01、raw/02 双 200 |
| D2 | P0 批量误发：`batch-grant-by-level` 在 `levelIds` 为空时静默退化为「发给全部 active 会员」 | `CouponController.batchGrantByLevel` 旧实现 `levelIds` 空 → `q.in(...)` 不生效 → 全租户会员 | 护栏 1：必须显式 `levelIds`；护栏 2：全量需 `allMembers=true` **且** `confirm=true`；护栏 3：按 `total_count` / `per_limit` 截断并在响应回报；会员查询强制 `tenant_id` | ✅ 已修，raw/09 ~ raw/12 |
| D3 | P0 批量误发：`/mkt/coupon/batch/{id}/grant` 不校验发放对象，随手一个 memberId 就能发 | `MktCouponController.grantBatch` 旧实现直接按传入 id 发券，无存在性/租户校验 | 会员存在性 + 租户 + 状态校验；单人 ≤100 张；单次总量 ≤2000 张；批次配额改为条件 UPDATE（并发抢占 → `CONCURRENCY_CONFLICT` 回滚）；响应回报 `requestedCount / grantedCount / truncatedCount / remainingCount` | ✅ 已修，raw/06 ~ raw/08 |
| D4 | `POST /market/coupon/grant` **每次调用必 500**（假成功入口 / 影子功能） | `CouponGrantMapper.xml` 的 `atomicGrant` 引用了库里**不存在**的两列：`market_coupon_grant.deleted`、`market_coupon.issued_count`（实测表结构见 `raw/db-schema-coupon-tables.txt`） | `deleted = 0` → `status != 3`（作废）；`SET c.issued_count = c.issued_count + 1` → `SET c.updated_at = NOW()`（原子闸门保留：命中 1 行 / 不命中 0 行） | ✅ 已修，raw/13、raw/14 |
| D5 | 契约漂移：`/market/coupon` 路由渲染的就是 mkt 券页面，但页面数据接口要求 `mkt:coupon`，CLERK 持有 `market:coupon:list` → 整页 403 | `router/index.ts:62`（meta `market:coupon:list`）→ `views/mkt/coupon.vue` 调 `/mkt/coupon/*`（`@RequirePermission("mkt:coupon")`）；`sys_role_permission` 中 CLERK 无 `mkt:coupon` | 只读端点（`/batch/page`、`/page`、`/export`）改为 `ANY{mkt:coupon, market:coupon:list}`；**写端点（创建/发布/结束/发放）仍严格要求 `mkt:coupon`** | ✅ 已修，raw/03、raw/04（CLERK 只读 200）与 raw/05（CLERK 发放 403） |

> D4 的原始报错原文（重建容器前采集于 `/app/logs/meiyetec-error.log`）：
> `java.sql.SQLSyntaxErrorException: Unknown column 'deleted' in 'where clause'` →
> 去掉 `deleted` 后再暴露第二处：`Unknown column 'c.issued_count' in 'field list'`。

---

## 2. 验证矩阵（全部为实际 HTTP 调用，原始响应见 `raw/`）

| # | 用例 | 请求 | 期望 | 实测 | 文件 |
|---|---|---|---|---|---|
| 1 | ADMIN `GET /mkt/coupon/batch/page` | `?pageNum=1&pageSize=3` | 200 + 分页体 | 200，`total=6` | `raw/01-admin-get-batch-page.txt` |
| 2 | ADMIN `POST /mkt/coupon/batch/page`（兼容） | `{}` | 200 | 200，`total=6` | `raw/02-admin-post-batch-page.txt` |
| 3 | CLERK `GET /mkt/coupon/batch/page` | 同上 | 200（只读放行） | 200 | `raw/03-clerk-get-batch-page.txt` |
| 4 | CLERK `GET /mkt/coupon/page` | 同上 | 200（只读放行） | 200，`total=102` | `raw/04-clerk-get-coupon-page.txt` |
| 5 | CLERK `POST /mkt/coupon/batch/6/grant` | 空 members | 403（写权限保留） | 403 `需要权限码：mkt:coupon` | `raw/05-clerk-grant-denied.txt` |
| 6 | 不存在的会员 99999999 | `requests/01` | 400 | 400 `发放对象不存在、不属于当前租户或状态非正常：99999999` | `raw/06-guard-member-not-exist.txt` |
| 7 | 单人 3000 张 | `requests/02` | 400 | 400 `每人张数不能超过 100` | `raw/07-guard-count-over-limit.txt` |
| 8 | 空 members / 未指定发放对象 | `requests/03` | 400 | 400 `必须指定发放对象…allMembers=true 且 confirm=true` | `raw/08-guard-empty-members.txt` |
| 9 | 按等级发放未传 levelIds | `requests/04` | 400 | 400 `必须指定会员等级 levelIds…` | `raw/09-level-no-levelids.txt` |
| 10 | `allMembers=true` 未带 confirm | `requests/05` | 400 | 400 `全量发放需要二次确认：请传 confirm=true` | `raw/10-level-allmembers-no-confirm.txt` |
| 11 | per_limit=1 已达限领 | `requests/06` | 截断回报 | 200 `grantedCount=0`，`skippedMembers` 3 条「已达每人限领 1 张」 | `raw/11-level-perlimit-skip.txt` |
| 12 | total_count=2 已发满 | `requests/07` | 截断回报 | 200 `skipReason=优惠券总量已用尽`，`skippedCount=15` | `raw/12-level-total-exhausted.txt` |
| 13 | 单张发放 `POST /market/coupon/grant` | `couponId=4&memberId=3` | 200 | 200 `发放成功`（grantId=8） | `raw/13-market-single-grant.txt` |
| 14 | 单张发放 + 不存在会员 | `couponId=4&memberId=99999999` | 404 | 404 `会员不存在` | `raw/14-market-single-grant-bad-member.txt` |

补充：`raw/00-clerk-login.json` 为 CLERK 登录返回（含 `permissions` 列表，可核对 `market:coupon:list` 存在、`mkt:coupon` 不存在）；
`raw/db-schema-coupon-tables.txt` 为 `market_coupon` / `market_coupon_grant` 实际列清单（D4 根因证据）。

### 2.1 前端侧验证

| 项 | 方式 | 结果 |
|---|---|---|
| `views/mkt/coupon.vue` / `api/coupon.ts` 类型检查 | `npx vue-tsc -b` | 本批改动文件 **0 报错**（仓库既有的 `views/system/*`、`views/wallet/*` 报错为历史基线，与本批无关） |
| 前端构建 | `npx vite build` | ✅ built in 11.97s；镜像 `meiyetec/web:dev` 重建并重建容器，`http://localhost:8088/` 200，产物含 `coupon-*.js` / `coupon-*.css` |

> **未做浏览器人工验收**（规则 9）：二次确认弹窗、截断提示文案需人工在 `http://localhost:8088/mkt/coupon` 走一遍发放流程确认。

---

## 3. 前端改动（与后端护栏配套）

| 文件 | 改动 |
|---|---|
| `frontend/meiyetec-web/src/views/mkt/coupon.vue` | 发放弹窗：张数上限 `:max="MAX_COUNT_PER_MEMBER"`(100) + 上限提示文案；`runGrant` 增加 `ElMessageBox.confirm` 二次确认（取消则不发）；结果回显区分「已发放 N 张」与「已发放 N 张（申请 M 张，因批次额度不足截断 K 张）」；新增 `.form-tip` 样式 |
| `frontend/meiyetec-web/src/api/coupon.ts` | `batchPage` 由 POST 改 GET（与修复后的端点一致；后端 POST 仍保留兼容历史调用方） |

---

## 4. 测试数据副作用（规则 20 合规说明）

| 对象 | 动作 | 说明 |
|---|---|---|
| `market_coupon` id=4 `audit-p0-single-grant-fix` | 新建 | 隔离测试券（totalCount=50 / perLimit=0），供 D4 验证使用 |
| `market_coupon_grant` grantId 6/7/8 | 新增 | 会员 3 的发放记录（id=4 券） |
| `mkt_coupon_batch` id=6 `audit-p0-coupon-guard` | 发放 2 张 | 上一轮 P0 护栏验证已产生（issued_count=2/100） |
| `market_coupon` id=2 / id=3 | 上一轮验证已存在 | id=2 per_limit=1（已被限领拦截），id=3 total_count=2（已被总量拦截） |

写操作一律使用**确认不存在**的兜底 ID `99999999`（用例 6、14），未对真实业务会员批量发放。

---

## 5. 未确认项 / 残留风险（规则 5）

1. **并发超发的原子性降级（PARTIAL-NON-BLOCKER）**：`market_coupon` 表**没有 `issued_count` 计数列**，`atomicGrant` 只能做「条件命中与否」的闸门（命中 1 行 = 配额与限领条件满足），无法像原设计那样在 UPDATE 里自增计数。极端并发下，闸门判定与随后的 `INSERT` 之间仍存在极小 TOCTOU 窗口，理论上可轻微超出 `total_count`。彻底解决需 DDL 增加 `market_coupon.issued_count`（属新增迁移，未在本批范围内，已登记待下一批）。
2. **`mkt_coupon_batch.issued_count` 的并发控制**：已改为条件 UPDATE（`COALESCE(issued_count,0) + ? <= COALESCE(total_count,0)`），抢占失败抛 `CONCURRENCY_CONFLICT` 并回滚事务，该条路径无残留风险。
3. **`couponCode` 唯一性**：`mkt_coupon` 唯一键 `uk_tenant_code(tenant_id, coupon_code)`，原「时间戳后 8 位 + 随机 4 位」在批量发放时有碰撞风险，本批已改为 `时间戳 8 位 + 自增序列 5 位 + 随机 4 位`。
4. **`/market/coupon` 的「按等级批量发放」无 UI 入口**：`api/coupon.ts` 的 `batchGrantByLevel` 目前无任何视图调用（幽灵 API）。D2 的护栏只保护接口，不构成 UI 能力，本批未新增入口（属功能范围决策）。
5. **`market.coupon`（菜单码，点号）与 `market:coupon:list`（守卫码，冒号）两套命名并存**（batch2-B 遗留），本批未做统一。
6. **浏览器人工验收未做**（见 §2.1 说明）。

---

## 6. 本批落盘文件

| 文件 | 类型 | 说明 |
|---|---|---|
| `backend/src/main/java/com/meiyetec/controller/MktCouponController.java` | 修改 | GET 别名 + 发放护栏 + 只读端点权限 ANY |
| `backend/src/main/java/com/meiyetec/controller/CouponController.java` | 修改 | 按等级批量发放护栏 + 返回体改为明细 |
| `backend/src/main/java/com/meiyetec/mapper/CouponGrantMapper.java` | 修改 | 新增 `countGrantedByMember` |
| `backend/src/main/resources/mapper/CouponGrantMapper.xml` | 修改 | `countGrantedByMember` + `atomicGrant` 列名对齐实表 |
| `frontend/meiyetec-web/src/views/mkt/coupon.vue` | 修改 | 二次确认 + 截断回显 + 上限约束 |
| `frontend/meiyetec-web/src/api/coupon.ts` | 修改 | `batchPage` 改 GET |
| `audit/evidence/F131-coupon-batch-crud-grant/p0-guard-20261109/**` | 新增 | 本证据目录 |
