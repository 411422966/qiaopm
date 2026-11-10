# F150 · M09 裂变邀请解封（批 1：解封 + 修 SQL + 只读验证）

> 状态：**VERIFIED**（8 个目标端点 + 1 个回归对照 + 1 个已解封对照全部实测符合预期）
> 基线 commit：`d5bce5fd005bdadfbb7bc4458e5b2d09c432cc8a`（批 1 改动前 HEAD）
> DB 快照：`audit/evidence/F150-m09-invite-unseal/raw/10-db-row-counts.txt` + `raw/11-schema-market_coupon.txt` + `raw/12-schema-market_coupon_grant.txt`
> 环境基线：`audit/baseline/env.md`
> 范围声明：**本批只做只读解封 + `atomicGrant` 的 D1 修复；不创建任何带优惠券奖励的活动；不改表结构；不动其他 14 个被门控 Controller**

---

## 1. 背景

`/m09/invite/**`（`InviteController`）与 `/lifecycle/invite/**`（`MemberInviteController`）在 I135 止血时
被加上 `@ConditionalOnProperty(name = "feature.legacy-missing-ddl.enabled", havingValue = "true", matchIfMissing = false)` 守卫，
而 `application.yml` 中该开关恒为 `false` → **两个 Controller 的 Bean 从不注册 → 全部路径 HTTP 404（无 handler）**。

I135 的处置是「不全局打开开关」——因为该开关同时门控另外 14 个 Controller，其中若干依赖的表在当前库仍缺失，
全局打开会重新引入 500。因此本次采用**逐个解封**：只摘掉这两个 Controller 的守卫注解。

---

## 2. 批 1 改动清单（4 个文件，均为 §5.1 审核通过项）

| # | 文件 | 改动 | 依据 |
|---|---|---|---|
| 1 | `backend/src/main/java/com/meiyetec/controller/InviteController.java` | 删 `import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;`（原 L14） | 守卫注解已删，import 失效 |
| 2 | 同上 | 删 `@ConditionalOnProperty(name = "feature.legacy-missing-ddl.enabled", havingValue = "true", matchIfMissing = false)`（原 L49） | 解封核心 |
| 3 | `backend/src/main/java/com/meiyetec/controller/MemberInviteController.java` | 删 `import ...ConditionalOnProperty;`（原 L8） | 同上 |
| 4 | 同上 | 删 `@ConditionalOnProperty(...)`（原 L24） | 解封核心 |
| 5 | `backend/src/main/resources/mapper/CouponGrantMapper.xml` | `atomicGrant` 配额判定按**方案 A** 改：`total_count IS NULL OR total_count = 0` 视为不限量 | D1 缺陷（NULL 时 `NULL > COUNT(*)` 恒不成立 → 误报「已达发放上限」） |
| 6 | `backend/src/main/java/com/meiyetec/mapper/CouponGrantMapper.java` | javadoc 去掉 `issued_count` 的过时表述，补充「NULL/0 视为不限量」说明 | 文档与实表对齐 |

**未改动**：`application.yml`（`feature.legacy-missing-ddl.enabled: false` 保持不变）、`api/invite.ts`、任何前端页面、任何表结构、其余 14 个门控 Controller。

---

## 3. 构建与部署

| 步骤 | 命令 | 结果 |
|---|---|---|
| 1 | `mvn clean package -Dmaven.test.skip=true`（`backend/`） | ✅ BUILD SUCCESS，1547 source files，产出 `target/meiyetec-api-1.0.0.jar` |
| 2 | `docker compose build api` | ✅ `meiyetec/api:dev` 构建成功（`COPY --from=builder /build/app.jar /app/app.jar`） |
| 3 | `docker compose up -d api` | ✅ 容器 `meiyetec-api` 重建，`Up (healthy)` |
| 4 | 依赖服务 | `meiyetec-mysql` / `meiyetec-redis` 均 `healthy` |

---

## 4. 端点实测结果（9 个，admin token，只读 GET）

采集时间：2026-11-10 22:32 (UTC+8)；原始报文见 `raw/01` ~ `raw/09`。

### 4.1 解封目标端点（6 个，全部 200）

| # | 端点 | 期望 | 实测 | 原始文件 |
|---|---|---|---|---|
| 1 | `GET /m09/invite/campaign/page?pageNum=1&pageSize=10` | 200 | **HTTP 200**，`data.total=0`、`records=[]` | `raw/01-*.txt` |
| 2 | `GET /m09/invite/stats` | 200 | **HTTP 200**，`rewardCount/registerCount/totalInviteCodes/firstOrderCount/scanCount/activeCampaigns` 全 0，`conversionRate=0` | `raw/02-*.txt` |
| 3 | `GET /m09/invite/record/page?pageNum=1&pageSize=10` | 200 | **HTTP 200**，`data.total=0`、`records=[]` | `raw/03-*.txt` |
| 4 | `GET /m09/invite/reward-log/page?pageNum=1&pageSize=10` | 200 | **HTTP 200**，`data.total=0`、`records=[]` | `raw/04-*.txt` |
| 5 | `GET /lifecycle/invite/code?memberId=1` | 200 | **HTTP 200**，返回 `inviteCode=INVD5AA8288`、`status=ACTIVE`、`rewardCouponAmount=50.00` | `raw/05-*.txt` |
| 6 | `GET /lifecycle/invite/my-invites?memberId=1` | 200 | **HTTP 200**，`data=[]` | `raw/06-*.txt` |

> `total=0` 属正常：4 张 `m09_*` 业务表当前 0 行（`raw/10-db-row-counts.txt`）。

### 4.2 回归对照（2 个，仍 404 —— 证明未误伤其余门控 Controller）

| # | 端点 | 期望 | 实测 | 原始文件 |
|---|---|---|---|---|
| 7 | `GET /m02/split/list` | 404 | **HTTP 404** `No message available`（`MultiAccountSplitController` 仍被门控） | `raw/07-*.txt` |
| 8 | `GET /commission/tier/list` | 404 | **HTTP 404** `No message available`（`CommissionTierController` 仍被门控） | `raw/08-*.txt` |

### 4.3 已解封对照（1 个，200 —— 证明该链路此前已解封且未回归）

| # | 端点 | 期望 | 实测 | 原始文件 |
|---|---|---|---|---|
| 9 | `GET /gray/flag/list` | 200 | **HTTP 200**，`data=[]` | `raw/09-*.txt` |

### 4.4 GET 无写副作用核查

`GET /lifecycle/invite/code?memberId=1` 返回了 `inviteCode`，需确认不是本次新建。
核查 `member_invite_code` 表：`id=1 / tenant_id=1 / inviter_id=1 / invite_code=INVD5AA8288 / status=ACTIVE / create_time=2026-08-17 21:17:02`
→ **该记录早已存在**（2026-08-17），本次 GET 走的是 `MemberInviteService.myInviteCode` 的「已存在 ACTIVE → 直接返回」分支，**未产生新行**。

---

## 5. 前端页面验证

| 项 | 结果 |
|---|---|
| 登录 | `operator01` / `123456` 登录成功 → `http://localhost:8088/dashboard` |
| 菜单 | 营销工具 → 裂变邀请（`/m09/invite`，权限码 `mkt.invite`）可见 |
| 页面 | `http://localhost:8088/m09/invite` 打开成功，标题「裂变邀请」，三个 Tab（活动管理 / 邀请记录 / 奖励日志）均正常渲染 |
| 数据区 | 显示「暂无数据」表格空态，**无报错、无 404 toast** |

---

## 6. 关联表数据快照

```
market_coupon         4
market_coupon_grant  8
m09_invite_campaign  0
m09_invite_record    0
m09_invite_reward_log 0
```

`market_coupon` 现有行（`raw/13-market_coupon-rows.txt`）：

| id | tenant_id | name | total_count | per_limit | status |
|---|---|---|---|---|---|
| 1 | 1 | Test Coupon | 100 | 1 | 1 |
| 2 | 1 | audit-p0-level-grant | 50 | 1 | 1 |
| 3 | 1 | audit-p0-total-truncate | 2 | 0 | 1 |
| 4 | 1 | audit-p0-single-grant-fix | 50 | 0 | 1 |

> 实测 `market_coupon` **无 `issued_count` 列、无 `deleted` 列**；`market_coupon_grant` **无 `deleted` 列** → 方案 A 适配真实表结构的方向成立。

---

## 7. 本批未做 / 待后续批次

| 项 | 状态 | 去向 |
|---|---|---|
| 发放链路端到端测试（创建带优惠券奖励的活动 → 触发发放 → 校验券到账） | ⏸ 未做 | **批 2**（用户决策：批 1 验收通过后启动） |
| 创建任何带优惠券奖励的活动 | ⏸ 未做 | 批 2 |
| `atomicGrant` D2（并发语义 / 原子自增） | ⏸ 未做 | `audit/issues/I145-market-coupon-issued-count.md`（方案 B 候选） |
| `atomicGrant` D3（租户口径） | ⏸ 未做 | 批 2 实测后再定（已记入 I145 §4 R4） |
| 其余 14 个门控 Controller 解封 | ⏸ 未做 | 各自独立批次，需先确认依赖表已落地 |

---

## 8. 验证等级

| 项 | 等级 | 说明 |
|---|---|---|
| 端点可达性（6 个解封端点 HTTP 200 + 响应体结构） | **L5a** | 有实际运行报文证据（`raw/01`~`raw/06`） |
| 未误伤其余门控 Controller（2 个 404 回归对照） | **L5a** | 有实际运行报文证据（`raw/07`~`raw/08`） |
| 前端页面可打开、显示空态 | **L5b** | 浏览器实测截图确认 |
| `atomicGrant` D1 修复的**实际发放行为** | **UNVERIFIED** | 本批未创建带优惠券奖励的活动（按用户决策留待批 2）；D1 仅做静态核对 + SQL 语义确认 |
| 发放奖励链路（`/invite/redeem` → 发券） | **UNVERIFIED** | 批 2 范围 |
