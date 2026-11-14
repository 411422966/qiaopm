# F168 — `POST /invite/track` 的 `scan_count` 计数口径修正（I179）

> **Feature ID**：F168
> **状态**：**VERIFIED**（L5a — 实际运行 + DB 计数对照，5/5 CHECK PASS）
> **对应 Issue**：`audit/issues/I179-invite-track-scan-count-no-phone.md`
> **基线 commit**：`4ccca57`（修复前 HEAD）
> **修复 commit**：`6537180` — `fix(invite): track 的 scan_count 移到 phone 判空之后（I179）`
> **DB 快照**：本目录 `raw/00-fixture-seed.sql`（夹具）+ `raw/01-verify-i179.txt`（实测输出）
> **环境基线**：`audit/baseline/env.md`
> **改动文件**：`backend/src/main/java/com/meiyetec/service/InviteService.java` — `trackClick` + 新增 `incrementScanCount`

---

## 1. 缺陷与修复

`InviteService.trackClick` 中 `scan_count + 1` 位于 **phone 判空之前**
→ 任何**不带手机号**的 `POST /invite/track` 请求都会让扫码数 +1（可被匿名/机器人无限刷）。

修复：把计数移到 phone 判空之后，并抽出 `incrementScanCount(code)` 收敛计数口径。

| 场景 | 修复前 | 修复后 |
|---|---|---|
| 常规活动（`invite_need_register=1`）+ **带** phone | +1 | **+1（不变）** |
| 常规活动 + **不带** phone | +1 | **+0（变更）** |
| 常规活动 + phone 为空串 | +1 | **+0（变更）** |
| 「仅点击即可」活动（`invite_need_register=0`），不带 phone | +1 | **+1（不变，刻意保留）** |

> ⚠️ **口径变更声明**：`scan_count` 的语义从「**所有访问次数**」收敛为「**有手机号的访问次数**」
> （「仅点击即可」类活动除外）。**仅对变更后的新流量生效**，历史数据**未回溯校正**，
> 跨变更点的 `conversionRate` 同比 / 环比会失真。

---

## 2. 验证环境

| 项 | 值 |
|---|---|
| 运行时 | docker compose 栈：`meiyetec/api:dev`（重建自本分支）+ `meiyetec-mysql` |
| 令牌 | `operator01`（`userId=900006`，`tenant_id=1`） |
| 常规活动 | `m09_invite_campaign.id` = `990201`（`invite_need_register=1`，有效期 2026-01-01 ~ 2027-12-31，`status=1`） |
| 「仅点击即可」活动 | `m09_invite_campaign.id` = `990203`（`invite_need_register=0`） |
| 被测邀请码 | `m09_invite_code.id` = `990202`（`invite_code=TESTI179C`）、`990203`（`invite_code=TESTI179D`） |
| 计数器初值 | 两个 code 的 `scan_count` 测试前均复位为 **0** |

---

## 3. 实测结果（`raw/01-verify-i179.txt`）

| # | 场景 | 期望 | 实测 | 判定 |
|---|---|---|---|---|
| S1 | `POST /invite/track {code:"TESTI179C"}`（**无** phone） | `scan_count` **不变** | `code=200`，`scan_count` 0 → 0 | ✅ PASS |
| S2 | `POST /invite/track {code:"TESTI179C", phone:"13900000179"}` | `scan_count` **+1**，并建 record | `code=200`，`data.id=13`，`scan_count` 0 → 1 | ✅ PASS |
| S3 | `POST /invite/track {code:"TESTI179C", phone:""}`（空串） | `scan_count` **不变** | `code=200`，`scan_count` 1 → 1 | ✅ PASS |
| S4 | `POST /invite/track {code:"TESTI179D"}`（仅点击即可活动，无 phone） | `scan_count` **+1**（原行为保留） | `code=200`，`scan_count` 0 → 1 | ✅ PASS |
| S5 | 无 phone 的 track **不建** `m09_invite_record` | `TESTI179C` 只有 1 条 record（来自 S2） | `COUNT(*)=1` | ✅ PASS |

**OVERALL: ALL PASS（5/5）**

---

## 4. 验证等级

| 项 | 等级 | 说明 |
|---|---|---|
| 无 phone 不计数 | **L5a** | 实际运行 + DB 计数对照 |
| 带 phone 计数 | **L5a** | 实际运行 + DB 计数对照 |
| 空串 phone 不计数 | **L5a** | 实际运行 + DB 计数对照 |
| 「仅点击即可」活动原行为保留 | **L5a** | 实际运行 + DB 计数对照 |
| 不建 invite_record | **L5a** | 实际运行 + DB 行数对照 |
| 历史 `scan_count` 虚高的回溯校正 | **未实施** | 无法区分「匿名刷量」与「真实无手机号访问」→ 见 Issue §3.3 |
| 前端 C 端落地页端到端 | **UNVERIFIED** | `sharePage` / `trackClick` 在 `frontend/**` 中**当前无调用方** |

---

## 5. 边界声明

- 结论基于**本地 docker 栈实测**；未做浏览器侧验证（纯后端改动，前端无调用方）。
- 测试数据为隔离行（`m09_invite_campaign.id` = 990201 / 990203，`m09_invite_code.id` = 990202 / 990203，
  `m09_invite_record.id` = 13），验证后已删除，4 张 `m09_*` 表回到 **0 行**基线。
- 「带手机号的重复 track」**仍无频率限流**（每次仍 +1，靠 `uk_campaign_inviter_phone` + `MAX_BIND_PER_PHONE`
  拒绝重复绑定）→ 归属 **I169**（通用限流基建，本批暂缓）。
- `raw/00-fixture-seed.sql` 与 `raw/verify-i179.ps1.txt` 标注为**非正式工具**，仅供证据复现，
  **未接入 CI / PR 必跑清单**。脚本为 **UTF-8 with BOM**（Windows PowerShell 5.1 无 BOM 会把中文按 ANSI 读成乱码）。
