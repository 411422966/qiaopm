# F163 · `GET /invite/share-page` 去除隐式埋点（扫码双计数，I166）

> 状态：**VERIFIED**（`share-page` 不再改动 `scan_count`；显式 `/invite/track` 仍为唯一计数来源）
> 基线 commit：`1bc831f`（修复前 HEAD，工作树 clean）
> DB 快照：本目录 `raw/`
> 环境基线：`audit/baseline/env.md`
> 对应 Issue：`audit/issues/I166-invite-share-page-scan-double-count.md`
> 改动文件：`backend/src/main/java/com/meiyetec/controller/InviteController.java`（`sharePage`）

---

## 1. 缺陷与修复

`InviteController.sharePage` 在返回落地页数据前**隐式调用** `inviteService.trackClick(code, null, "LINK", null, null)`，
而 `trackClick` 内部**无条件**执行 `scan_count + 1`（与是否带 phone 无关）
→ 前端若同时调 `share-page` + `track`，一次真实访问被计 **2** 次。

修复（按 I166 §3.1）：
1. 删除隐式 `trackClick` 调用及其 `try/catch`（顺带消除「异常被静默吞掉」的 S2）
2. 删除死代码局部变量 `InviteCampaign activity = null`（S1）
3. 补注释固化口径：「本端点只读，扫码计数只由 `POST /invite/track` 产生」（§3.2）

---

## 2. 验证环境

| 项 | 值 |
|---|---|
| 令牌 | `operator01`（`tenant_id=1`） |
| 被测邀请码 | `id=8` `TESTI164A`（tenant 1），campaign `TESTAUDIT_C1`（`id=8`, status=1, 有效期 2026-01-01 ~ 2027-12-31） |
| 计数器初值 | `scan_count = 0`（测试前显式复位） |

---

## 3. 修复前后对照

| 阶段 | 操作 | `m09_invite_code.id=8.scan_count` | 判定 |
|---|---|---|---|
| 修复前 | `GET /invite/share-page?code=TESTI164A` ×1 | 0 → **1** | ❌ GET 产生写副作用 |
| 修复后 | `GET /invite/share-page?code=TESTI164A` ×1 | 0 → **0** | ✅ 只读，无副作用 |
| 修复后 | `POST /invite/track {code:"TESTI164A", channel:"LINK"}` ×1 | 0 → **1** | ✅ 显式埋点仍正常计数（回归不退化） |

响应体对照：修复前后 `share-page` 的 `data` 结构**完全一致**（`campaignId` / `campaignName` / `inviteCode` /
`inviterNick` / `inviterRewardDesc` / `inviteeRewardDesc` / `inviteeMinConsume` / `startTime` / `endTime` / `status` 等）
→ 符合 I166 §4 检查项 4「响应内容与修复前完全一致」。

原始文件：
- 修复前：`raw/01-before-share-page.txt`
- 修复后：`raw/02-after-share-page.txt`、`raw/03-after-db-counters.txt`

---

## 3.1 独立复跑（2026-11-14，重建夹具后）

> 做法：按 `raw/00-fixture-seed.sql` 重建隔离夹具 → 跑 `raw/verify-rerun.ps1` → 验证后删除夹具，4 张 `m09_*` 表回到 0 行基线。

| 场景 | 请求 | 期望 | 实测 |
|---|---|---|---|
| S5 | `GET /invite/share-page?code=TESTI164A` | `scan_count` 不变（0 → 0） | ✅ PASS |
| EXTRA | `POST /invite/track {code:"TESTI164A", channel:"LINK"}` | 显式埋点仍 +1（0 → 1） | ✅ PASS |

> 响应体 `data` 结构与修复前逐字段一致（`campaignId` / `inviteCode` / `inviterNick` / `inviterRewardDesc` /
> `inviteeRewardDesc` / `startTime` / `endTime` / `status` 等），未因删除隐式埋点而改变。

原始文件：`raw/04-verify-rerun.txt`

---

## 4. 验证等级

| 项 | 等级 | 说明 |
|---|---|---|
| `share-page` 不再改动 `scan_count` | **L5a** | 实际运行 + DB 计数对照 |
| `/invite/track` 埋点不受影响 | **L5a** | 实际运行 + DB 计数对照 |
| `share-page` 响应结构未变 | **L5a** | 修复前后报文逐字段比对 |
| 独立复跑（S5 + EXTRA） | **L5a** | 2026-11-14 重建夹具后重新实测，7/7 PASS（与 I164/I165 同一批脚本） |
| 前端落地页端到端 | **UNVERIFIED** | 前端 C 端落地页当前**无任何调用方**（`frontend/**` 中 `sharePage` / `trackClick` 均无调用），本轮未做前端改造 |
| 历史 `scan_count` 双计数脏数据校正 | **未实施** | 按用户指令不做 → 转 **I168** |

---

## 5. 边界声明

- 结论基于实际运行证据；未做浏览器侧验证（纯后端改动，且前端无调用方）。
- 测试数据为隔离行，已全部删除（4 张 `m09_*` 表回到 0 行基线）。
- 口径变更仅对**新流量**生效；历史 `scan_count` 仍含双计数部分（I166 §3.4 建议 (a) 保留现状 + 标注口径变更时间点 → I168）。
- `raw/00-fixture-seed.sql` 与 `raw/verify-rerun.ps1` 标注为**非正式工具**，仅供证据复现，**未接入 CI / PR 必跑清单**。
  > 注：仓库 `.gitignore` 第 143 行 `*.ps1` 会排除复跑脚本 → `verify-rerun.ps1` **仅本地留存、未入库**，他人复现需按同口径自建脚本。
