# I179 — `POST /invite/track` 无手机号请求仍累加 `scan_count`（口径缺陷）

> **状态**：**已修复**（2026-11-15，修复前 HEAD `4ccca57`）→ 证据：`audit/evidence/F168-invite-track-scan-count-gate/`（VERIFIED / L5a）
> **严重度**：**P2**（运营指标口径失真 + 匿名刷量入口；不报错、不越权）
> **发现日期**：2026-11-14（I169 通用限流调查批次，§2 暴露面表 #2）
> **登记人**：Cursor
> **绑定基线**：`audit/baseline/env.md` → 修复前 HEAD `4ccca57`
> **验证等级**：静态代码分析 → 运行期 **VERIFIED（L5a，5/5 CHECK PASS）**
> **关联**：I169（通用限流，本批**暂缓**）、I166（`share-page` 隐式埋点双计数，**已修复**）、I164 / I165（同批）

---

## 0. 一句话描述

`InviteService.trackClick` 中 `scan_count + 1` 的执行位置**在 phone 判空之前**
→ 任何**不带手机号**的 `POST /invite/track` 请求都会让扫码数 +1（匿名请求可无限刷）。

本 Issue 与 I169 §2 暴露面表 #2 描述的是**同一个代码事实**，但**修复手段不同**：
I169 说的是「需要通用限流」，本 Issue 说的是「**计数口径本身就不该对无手机号的请求生效**」
→ 本 Issue 是**口径修正**（P2），I169 是**基建补齐**（待业务拍板）。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| 方法 | `backend/src/main/java/com/meiyetec/service/InviteService.java` — `trackClick`（**L408**） |
| 修复前计数位置 | `scan_count + 1` 在 **L428**（判空 **L437** 之前） |
| 修复后计数位置 | `incrementScanCount(code)` — **L441**（判空之后）；另在「仅点击即可」分支 **L431** |
| 计数器字段 | `InviteCode.scanCount`（列 `scan_count`，表 `m09_invite_code`） |
| 端点 | `POST /invite/track` → `controller/InviteController.java` — `trackClick` |
| 前端 | `frontend/meiyetec-web/src/api/invite.ts` — `trackClick({code, phone?, channel?})` |

### 1.1 修复前代码（HEAD `4ccca57`）

```java
// 累加扫码数（即使后续不注册也记录）
code.setScanCount((code.getScanCount() == null ? 0 : code.getScanCount()) + 1);   // ← L428 无条件 +1
codeMapper.updateById(code);

// 是否需要创建 record
if (Boolean.FALSE.equals(c.getInviteNeedRegister())) {
    return Result.success(null);                     // 「仅点击即可」活动：计数已发生
}
if (phone == null || phone.isBlank()) {
    return Result.success(null);                     // ← L437 无手机号：计数**已**发生，直接返回
}
```

---

## 2. 缺陷机制

### 2.1 计数与「是否为真人访问」脱钩

`scan_count` 的业务含义是「**这个邀请码被扫码/访问了多少次**」，
但真正能识别「一次真实访问」的信号是**请求带上了手机号**（带手机号才会建 `m09_invite_record`、才会走
`uk_campaign_inviter_phone` 唯一键去重）。

修复前：计数发生在「是否拿到手机号」**之前** →

```
POST /invite/track {code:"X"}              （无 phone，机器人 / 匿名请求）
  → scan_count + 1                              ⇒ 扫码数可被无成本、无鉴权地无限放大
  → 且不建 record、不触发任何唯一键约束
```

### 2.2 影响面

| 消费点 | 位置 | 影响 |
|---|---|---|
| 邀请码列表 / 我的邀请码 | `InviteService.myCodes` / `codePage` | `scanCount` 虚高 |
| 活动漏斗转化率 | `InviteService.computeCampaignFunnel` → `conversionRate = firstOrderCount / scanCount` | 分母虚高 → **转化率被系统性低估** |
| 看板 | `campaignPage` / `campaignDetail` / `dashboardStats` | 同上 |

### 2.3 与 I166 的关系

I166（**已修复**）处理的是「**`share-page` 隐式调用 `trackClick`**」造成的**双计数**；
本 Issue 处理的是「**`track` 自身的计数口径**」。

I166 修复后，`share-page` 已不再触发任何计数，**`POST /invite/track` 成为计数的唯一来源**——
这使得本 Issue 的口径修正**更重要**（此前匿名请求造成的虚高，无从与正常扫码区分）。

---

## 3. 修复方案（已实施）

### 3.1 计数口径

| 场景 | 修复前 | 修复后 |
|---|---|---|
| 常规活动（`invite_need_register = 1`）+ **带** phone | +1 | **+1（不变）** |
| 常规活动 + **不带** phone | +1 | **+0（本次变更）** |
| 常规活动 + phone 为空串 | +1 | **+0（本次变更）** |
| 「仅点击即可」活动（`invite_need_register = 0`），不带 phone | +1 | **+1（不变）** |

> ⚠️ 最后一行是**刻意保留**的：这类活动里「扫码」本身就是转化事件
> （如扫码关注公众号），不建 record 是设计使然；若不计数，该活动的扫码数会**恒为 0**。

### 3.2 改动内容

- `InviteService.trackClick`：`scan_count + 1` 从「判空之前」移到「判空之后」（L441）；
  「仅点击即可」分支显式调用计数（L431）。
- 新增私有方法 `incrementScanCount(InviteCode code)`（**L487**）—— 把计数口径收敛到一处，
  注释里写明「**调用点只有两处，且都代表『这是一次真实的扫码访问』**」。

### 3.3 ⚠️ 口径变更声明（必须同步给业务 / 数据侧）

> **`scan_count` 的语义从「所有访问次数」收敛为「有手机号的访问次数」**
> （「仅点击即可」类活动除外，仍为「所有访问次数」）。

| 项 | 说明 |
|---|---|
| 变更生效时间 | **仅对变更之后的新流量生效** |
| 历史数据 | 修复前的历史 `scan_count` **仍含**匿名请求造成的虚高，**未做回溯校正**（无法区分「匿名刷量」与「真实无手机号访问」） |
| 派生指标 | `conversionRate` 的历史值同样偏高 → 跨变更点的**同比 / 环比对比会失真** |
| 建议 | ① 运营看板对**变更时间点前后的 `scan_count` 不做直接比较**；② 若需校正，只能按**上限折算**（不可靠），建议保留现状 + 标注口径变更时间点（与 I166 §3.4 / I168 同口径） |

---

## 4. 验证口径（修复后需满足）

| # | 检查项 | 期望 | 实测 |
|---|---|---|---|
| 1 | `POST /invite/track {code}` **不带** phone | `scan_count` **不变** | ✅ PASS（0 → 0） |
| 2 | `POST /invite/track {code, phone}` **带** phone | `scan_count` **+1**，并建 record | ✅ PASS（0 → 1，record id=13） |
| 3 | `POST /invite/track {code, phone:""}` 空串 | `scan_count` **不变** | ✅ PASS（1 → 1） |
| 4 | 「仅点击即可」活动不带 phone | `scan_count` **+1**（原行为保留） | ✅ PASS（0 → 1） |
| 5 | 无 phone 的 track **不建** `m09_invite_record` | `TESTI179C` 只有 1 条 record（来自 #2） | ✅ PASS |

> 证据：`audit/evidence/F168-invite-track-scan-count-gate/raw/01-verify-i179.txt`

---

## 5. 遗留与边界

| 项 | 状态 |
|---|---|
| 历史 `scan_count` 虚高 | **未校正**（见 §3.3）→ 口径变更已声明 |
| 「同一 IP N 次/分钟」频率限流 | **未做** → I169（本 Issue 只解决「无手机号也计数」，**带手机号的重复刷量仍无防护**） |
| 带手机号的重复 track | 仍有 `uk_campaign_inviter_phone` + `MAX_BIND_PER_PHONE` 兜底（返回 `CONFLICT`），但**计数仍会 +1**（在配额判空之前） |
| 前端 C 端落地页 | **当前无调用方**（I166 §2.5 同结论）→ 本次口径变更当前不影响线上 |

---

## 6. 边界声明

- 运行期结论基于**本地 docker 栈实测**（`meiyetec-api` + `meiyetec-mysql`），令牌 `operator01`（`tenant_id=1`）。
- 测试数据为隔离行（`m09_invite_campaign.id` = 990201 / 990203，`m09_invite_code.id` = 990202 / 990203，
  `m09_invite_record.id` = 13），验证后已删除，相关表回到 0 行基线。
- 未做浏览器侧验证（纯后端改动，且前端无调用方）。

---

**下一步**：① 把 §3.3 的口径变更同步给运营 / 数据侧；② I169 通用限流待业务拍板（429 契约 + 阈值 + 维度）。
