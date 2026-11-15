# I182 — `InAppSender.parseMemberId` 把手机号当 memberId 落库（站内信脏数据）

> **状态**：**Open**（本批**仅登记**，**未修改任何业务代码**）
> **严重度**：**P2**（不造成数据越权；产生指向不存在会员的脏行，污染站内信与会员统计口径）
> **发现日期**：2026-11-15（I178 验证期副产物，batch2-C 调查时补立项）
> **登记人**：Cursor
> **绑定基线**：HEAD `cfa7909cdd8cea1e5e5d57c9259a385669507c9b`
> **验证等级**：静态代码分析 + I178 批次运行期实证（曾产生 49 行脏行，已清理）→ 行为 **VERIFIED（运行期，历史样本）**；当前库内样本已清零（`member_message` 现 0 行）
> **证据**：`audit/evidence/F169-channel-quota-brand-scope/`（产生与清理过程）+ `audit/evidence/F170-mkt-send-path-quota-coverage/raw/01-channel-config-and-touch-log.txt`（当前行数 0）
> **关联**：I178（验证期副产物来源）、I181（同一触达链路）、I082（`InAppSender` 的 skip 兜底修复）

---

## 0. 一句话描述

`InAppSender.parseMemberId` 对**任何数字串**一律按 `memberId` 解析；
而触达任务流程传给 `IN_APP` 通道的 receiver 是**会员手机号**（`lg.getMemberPhone()`）
→ 手机号被当作 `memberId` 写入 `member_message.member_id`
→ 产生**指向不存在会员的脏行**（`member_id` 为 `bigint NOT NULL`，无外键约束，数据库层不会拦截）。

---

## 1. 事实核实

### 1.1 代码链路

| # | 位置 | 代码 / 行为 |
|---|---|---|
| 1 | `backend/src/main/java/com/meiyetec/service/channel/InAppSender.java:146-154` | `parseMemberId`：去掉 `-` 之后的部分后 `Long.parseLong` → **数字串一律解析成 memberId**，不做手机号 / openid / 组账号的区分 |
| 2 | `backend/src/main/java/com/meiyetec/service/channel/InAppSender.java:44,54-56` | `send` 用 `parseMemberId(receiver)` 得到 `memberId` |
| 3 | `backend/src/main/java/com/meiyetec/service/channel/InAppSender.java:59-62` | `memberId == null` → 记 warn 并 `SendResult.ok("MSG-SKIPPED-NO-MEMBER")`（**I082 的 skip 兜底**）→ 只挡住了**非数字** receiver，挡不住手机号 |
| 4 | `backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java:281-282` | `String receiver = task.getChannelType().equals("MINI_PROGRAM") ? "OPENID-" + lg.getMemberId() : lg.getMemberPhone();` → **`IN_APP` 走 else 分支，receiver 是手机号** |
| 5 | `backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java:284` / `L295` | `sender.send(tpl, receiver, vars)` → 首次发送与 3 次重试**都传手机号** |
| 6 | `backend/src/main/java/com/meiyetec/service/channel/InAppSender.java:75-95` | `msg.setMemberId(memberId)` → `messageMapper.insert(msg)` → 脏行落库 |

### 1.2 运行期实证

| 项 | 值 | 来源 |
|---|---|---|
| I178 验证期产生的脏行 | **49 行**（`member_message.member_id` 存的是手机号数值） | I178 批次执行记录，清理前计数 |
| 清理后 | 脏行**已全部清零**（物理删除） | I178 批次执行记录 |
| 当前库 | `member_message` 总行数 **0** | `audit/evidence/F170-mkt-send-path-quota-coverage/raw/01-channel-config-and-touch-log.txt` |

### 1.3 影响面

| # | 影响 | 说明 |
|---|---|---|
| 1 | **数据污染** | `member_message.member_id` 出现与 `crm_member.id` 无关的数值（手机号），表内无外键 → 不报错、不拦截 |
| 2 | **站内信不可见** | 按 `member_id` 查询站内信时，手机号行不会出现在任何真实会员名下 → **站内信实际丢失**，但业务侧显示「已发送」 |
| 3 | **统计口径失真** | 按 `member_message` 统计的到达 / 阅读率分母被污染 |
| 4 | **`MINI_PROGRAM` 分支不受影响** | `"OPENID-" + memberId` 经 `parseMemberId` 取 `-` 前段 → 得到正确 memberId |

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | 站内信「假发送」 | `IN_APP` 通道的触达任务发送成功 | 明细记为已发送，会员端收不到任何站内信 |
| R2 | 表数据污染 | 同上 | `member_message.member_id` 混入手机号，统计口径失真 |
| R3 | 与 I182 同源的其它调用方 | 任何把「数字型非 memberId」当 receiver 传入的路径 | 同一缺陷可能重复发生 |
| R4 | 误伤真实会员 | 未来若引入「手机号即账号」的登录体系 | 与 R1 修复方案冲突（见 §3 方案取舍） |

---

## 3. 修复方向（需拍板，本批不实施）

| # | 方案 | 内容 | 代价 / 风险 |
|---|---|---|---|
| A（推荐，与调用方同改） | **调用方正确传参 + 防御性识别** | ① `MarketingAutomationService.java:281-282` 对 `IN_APP` 改传 `String.valueOf(lg.getMemberId())`（与 `MINI_PROGRAM` 分支同理）；② `parseMemberId` 增加手机号识别（`^1\d{10}$`）→ 判为非 memberId → 走既有 `MSG-SKIPPED-NO-MEMBER` 兜底 | 小；需注意 `MarketingAutomationService` 是业务代码，改动需走既有修复流程 |
| B | **引入显式 `receiverType` 参数** | `ChannelSender.send` 增加类型声明（`MEMBER_ID` / `PHONE` / `OPENID` / `GROUP`），由调用方显式指定，`InAppSender` 只在 `MEMBER_ID` 时解析 | 中；**改动 `ChannelSender` 接口 → 全部 6 个 sender 实现 + 4 条调用路径**都要同步，接口面变大 |
| C | 只加防御性识别 | 仅改 `parseMemberId` 加手机号识别 | 最小，但**治标**：调用方仍传错语义，只是从「脏行」变成「站内信被跳过」（仍是假发送） |

> ⚠️ 建议 **A + B 组合**：A 立即止住当前调用方的错误传参，B 在后续「闸门下沉」（I181 §3 步骤 2）改造统一发送入口时一并落地，
> 由统一入口负责 receiver 语义声明，避免每个调用方各自猜测。

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | `IN_APP` 触达任务发送 | `member_message.member_id` 落在**真实会员 id** 上；无手机号数值行 |
| 2 | `IN_APP` 任务 receiver 为手机号（异常场景） | 不落脏行，且有明确 warn 日志 |
| 3 | `MINI_PROGRAM` 分支 | 行为不变（memberId 解析正确） |
| 4 | 告警路径（receiver `"ops"`） | 行为不变（`MSG-SKIPPED-NO-MEMBER` + warn） |
| 5 | 生日通知 `IN_APP` 分支 | receiver 为 `memberId`（`BirthdayNotificationService.java:214` 已是 `String.valueOf(memberId)`）→ 行为不变 |
| 6 | 历史脏行 | 迁移脚本能识别 `member_id` 不存在于 `crm_member` 的行并清理，且**只清理**确认无对应会员的行 |

---

## 5. 边界声明

- 本 Issue **仅登记**，**未修改任何业务代码**、**未执行任何 SQL 写入**、**未执行任何 DDL**。
- 运行期证据来自 I178 批次的验证过程（49 行脏行 → 已清理）与 batch2-C 只读调查（当前 `member_message` 0 行）；
  **未在本次重新复现脏行**（复现需要写操作，且本批定位为只读调查）。
- 未核实：生产环境 `member_message` 中是否已存在同类脏行及其量级（生产库未连接）。
- 未核实：是否存在其它把「数字型非 memberId」传入 `InAppSender.send` 的调用方（R3 的具体清单待全仓复核）。
- **未打开浏览器、未做前端端到端验证、未替代人工验收**。

---

**下一步**：与 I181 的「闸门下沉」改造合并排期（先做 §3 方案 A 止住当前错误传参，再在统一发送入口落地方案 B）→ 历史脏行清理需单独一次带备份的写操作批次。
