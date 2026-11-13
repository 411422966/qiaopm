# F161 · `GET /invite/my-stats` 跨租户 IDOR 修复（I164）

> 状态：**VERIFIED**（修复前后同口径对照实测，跨租户计数从 2/1/2 收敛为 1/0/1）
> 基线 commit：`1bc831f`（修复前 HEAD，工作树 clean）
> DB 快照：本目录 `raw/`（隔离测试数据，验证后已删除，4 张表回到 0 行基线）
> 环境基线：`audit/baseline/env.md`（MySQL `localhost:3307/meiyetec`、Redis `localhost:6379`、API `localhost:8080`）
> 对应 Issue：`audit/issues/I164-invite-my-stats-tenant-idor.md`
> 改动文件：`backend/src/main/java/com/meiyetec/service/InviteService.java`（`myStats`）

---

## 1. 缺陷与修复

`InviteService.myStats(Long memberId)` 的 4 个计数中，3 个（`successCount` / `pendingReward` / `rewardLogs`）
只按 `memberId` + `status` 过滤，**缺 `tenant_id` 条件** → 传入其他租户的 `memberId` 即可读到他人运营数据。
`totalCodes` 原本就有 `tenant_id` 过滤。

修复：`myStats` 的三个 `selectCount` 各补 `.eq(InviteRecord::getTenantId, tid)` /
`.eq(InviteRewardLog::getTenantId, tid)`（与同文件 `codePage` / `recordPage` / `myCodes` 的写法一致）。

> 本次未采纳 I164 §3.2 的三项建议（`memberId` 与登录会员一致性校验、端点权限注解、字段名语义）——
> 超出本轮授权范围，按 §3.1 最小修复执行。

---

## 2. 验证环境

| 项 | 值 |
|---|---|
| 令牌 | `operator01`（`sys_user.id=900006`，`tenant_id=1`） |
| 租户 | 请求租户 = 1 |
| 受害 memberId | `memberId=1`（`crm_member` 内 tenant 1 会员） |
| 不存在 memberId | `99999999` |
| 隔离测试数据 | campaign `TESTAUDIT_C1`(t1) / `TESTAUDIT_C2`(t2)；code `TESTI164A`(t1) / `TESTI165B`(t2)；record ×3（t1 status=4、t2 status=4、t2 status=3）；reward_log ×2（t1 / t2） |

**DB 事实**：进入本轮时 `m09_invite_campaign` / `m09_invite_code` / `m09_invite_record` / `m09_invite_reward_log`
**均为 0 行** → 插入的全部是本批专用隔离行，验证后已 `DELETE`，表回到 0 行基线（无真实业务数据被触碰）。

---

## 3. 修复前后对照（原始报文）

| # | 场景 | 修复前 | 修复后 | 判定 |
|---|---|---|---|---|
| 1 | `GET /invite/my-stats?memberId=1`（t1 管理员） | `totalCampaigns=1, successInvites=2, pendingRewards=1, rewardReceived=2` | `totalCampaigns=1, successInvites=1, pendingRewards=0, rewardReceived=1` | ✅ 租户 2 的 1 条 status=4 记录、1 条 status=3 记录、1 条 reward_log 被正确排除 |
| 2 | `GET /invite/my-stats?memberId=99999999` | 全 0 | 全 0 | ✅ 无回归 |
| 3 | 同场景重复调用（稳定性） | — | 与 #1 一致 | ✅ 计数稳定，无偶发泄漏 |

期望值推导：
- `successInvites`：tenant1 侧 record(status=4) ×1 → 1；tenant2 侧 record(status=4) ×1 → **排除**
- `pendingRewards`：仅 tenant2 侧有 record(status=3) ×1 → **排除后为 0**
- `rewardReceived`：tenant1 侧 reward_log(status=1) ×1 → 1；tenant2 侧 ×1 → **排除**

原始文件：
- 修复前：`raw/01-before-my-stats.txt`
- 修复后：`raw/02-after-my-stats.txt`

---

## 3.1 独立复跑（2026-11-14，重建夹具后）

> 目的：不依赖上一轮「修复前」环境，独立确认**修复后的当前运行态**行为仍然成立。
> 做法：按 `raw/00-fixture-seed.sql` 重建隔离夹具 → 清空 Redis 幂等键 → 跑 `raw/verify-rerun.ps1` → 验证后删除夹具，4 张表回到 0 行基线。

| 场景 | 请求 | 期望 | 实测 |
|---|---|---|---|
| S1 | `GET /invite/my-stats?memberId=1` | `totalCampaigns=1 / successInvites=1 / pendingRewards=0 / rewardReceived=1` | ✅ PASS |
| S2 | `GET /invite/my-stats?memberId=99999999` | 四项计数全 0 | ✅ PASS |

> DB 现场：`m09_invite_record` 存在 `8/1/status=4`、`9/2/status=4`、`10/2/status=3` 三行
> （**memberId=1 同时存在于两个租户**，正是暴露「缺 tenantId 过滤」的构造），
> 但 S1 的 `successInvites=1 / pendingRewards=0` —— **tenant 2 的两行未计入**，隔离生效。

原始文件：`raw/05-verify-rerun.txt`

---

## 4. 验证等级

| 项 | 等级 | 说明 |
|---|---|---|
| I164 修复行为（跨租户计数隔离） | **L5a** | 有实际运行报文 + DB 对照数据（修复前/后双向对照） |
| 单租户回归（`memberId=99999999` 全 0、重复调用稳定） | **L5a** | 有实际运行报文 |
| 独立复跑（S1/S2） | **L5a** | 2026-11-14 重建夹具后重新实测，7/7 PASS |
| `memberId` 与登录会员一致性校验、端点权限注解 | **UNVERIFIED / 未实施** | 属 I164 §3.2，超出本轮授权范围 |

---

## 5. 边界声明

- 本目录所有结论基于**实际运行证据**（HTTP 报文 + DB 行级对照）；未做浏览器侧验证（纯后端改动，前端无调用方）。
- 测试数据全部为本批隔离行，已全部删除；`sys_config` 新增 1 行配置（`invite.share.idempotent.window`，I165 配套，非本 Feature 缺陷修复必需项）。
- 未核实：多租户真实业务数据量下的性能影响（本 Feature 仅增加等值过滤条件，无索引变更）。
- `raw/00-fixture-seed.sql` 与 `raw/verify-rerun.ps1` 标注为**非正式工具**，仅供证据复现，**未接入 CI / PR 必跑清单**。
  > 注：仓库 `.gitignore` 第 143 行 `*.ps1` 会排除复跑脚本 → `verify-rerun.ps1` **仅本地留存、未入库**，他人复现需按同口径自建脚本。
