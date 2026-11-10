# I149 — M09 邀请奖励发放链路的业务逻辑缺陷（门槛后置无法补发 / 会员缺失静默漏发 / 脱敏手机号参与唯一键）

> **状态**：Open
> **严重度**：P1 — 三个缺陷均**不报错**，表现为「业务静默失效」：用户少拿券 / 券白发 / 邀请被误判重复
> **发现日期**：2026-11-10（F151 批 2 实测）
> **登记人**：Cursor（F151 m09 邀请发放链路验证批次）
> **决策**：用户 2026-11-10 拍板 —— 奖励发放链路的业务逻辑缺陷**单独立本 issue**，不与 I145（技术债）/ I148（闸门稳定性）混登
> **关联**：I148（`atomicGrant` 闸门稳定性）、I145（`issued_count` 方案 B）、F151（批 2 实测证据）、F150（m09 invite 解封）

---

## 0. 一句话描述

M09 邀请链路的「首单 → 发券」环节有三个**不抛异常、不告警**的业务逻辑缺陷：

| 编号 | 现象 | 后果 |
|---|---|---|
| **Q5** | 未达消费门槛后 record 被置 `status = 3`，但补发查询只取 `status = 2` | 后续更高金额订单**无法自动补发**，只能人工调 `resend-reward` |
| **Q8** | `grantInviteeReward` 在会员不存在时**静默 `return`** | 不发券、不写 `reward_log`，但 record 仍被置 `status = 4` → **漏发无痕迹**（触发条件：会员被**软删除** → 常见情况） |
| **Q9** | `invitee_phone` 存**脱敏**手机号且参与唯一键 | 前 3 后 4 相同的不同手机号**撞唯一键** → 真实不同的人被当成同一人 |

---

## 1. Q5 — 未达消费门槛后无法自动补发 🔴 P1

### 1.1 缺陷位置

| 项 | 内容 |
|---|---|
| 方法 | `backend/src/main/java/com/meiyetec/service/InviteService.java` — `rewardOnFirstOrder`（L493） |
| 查询条件 | L497-500：`.eq(InviteRecord::getStatus, 2)` —— **只取「已注册」** |
| 门槛判定 | L511-519：不达 `inviteeMinConsume` → `r.setStatus(3)`（FIRST_ORDER）+ `continue` |
| 人工补发入口 | `POST /m09/invite/record/{id}/resend-reward` → `InviteController.java:132` → `InviteService.resendReward`（L757，允许 `status == 3`） |
| 表 | `m09_invite_record.status`（注释：`1待注册 2已注册 3已首单 4已奖励 5已失效 6已拒绝`） |

### 1.2 缺陷机制

```java
// L497-500 —— 只捞 status = 2（已注册）
List<InviteRecord> records = recordMapper.selectList(new LambdaQueryWrapper<InviteRecord>()
        .eq(InviteRecord::getInviteeMemberId, inviteeMemberId)
        .eq(InviteRecord::getStatus, 2)          // ← 只取 2
        .eq(InviteRecord::getTenantId, tid));
...
// L511-519 —— 不达门槛 → 置 status = 3（已首单）并 continue
if (orderAmount != null && orderAmount.compareTo(min) < 0) {
    r.setStatus(3);                               // ← 已首单
    r.setFirstConsumeAt(now);
    r.setFirstOrderId(orderId);
    recordMapper.updateById(r);
    continue;                                     // ← 退出本次处理
}
```

**状态机断裂**：

```
status=1 待注册 ──bind──► status=2 已注册
                              │
                              │ 首单金额 < 门槛
                              ▼
                        status=3 已首单  ◄────────┐
                              │                  │ 下一笔订单（金额更高）
                              │  ← 查询只取 status=2，status=3 不再被捞起
                              │                  │
                              ▼                  │
                        status=4 已奖励          │
                        （永不自动到达）──────────┘
```

**后果**：受邀人首单金额低于活动门槛时，**第一笔订单不会发券**（这是预期内），
但**后续任何金额达标的订单都不会触发自动补发** —— 因为 record 已是 `status = 3`，
而 `rewardOnFirstOrder` 只捞 `status = 2`。

唯一的补发途径是运营人员手工调用 `POST /m09/invite/record/{id}/resend-reward`
（`resendReward` L757-766 明确允许 `status == 3`）。

**影响面**：所有配置了 `inviteeMinConsume > 0` 的邀请活动。**这是常规配置**
（`m09_invite_campaign.invitee_min_consume` 是活动创建表单字段），
因此这是一个**默认生效**的缺陷，而不是边缘配置问题。

**建议修复**（本 issue 只登记，不在本批改代码）：

方案一（最小改动，推荐）：把查询条件改为**同时捞 `status = 2` 和 `status = 3`**
（`in(InviteRecord::getStatus, 2, 3)`），并在循环内判断：
`status == 3` 的记录是「已首单但未发奖」，需**重新校验门槛**后发奖。

方案二：把「已首单」与「已达门槛待发奖」拆成两个状态值（如新增 `status = 7` 表示
「已首单但未达门槛」），避免语义重载。

⚠️ 无论哪种方案，都需要处理**幂等**：同一 record 若被重复处理，必须确保不重复发券
（F151 T5 已验证「同一 record 重复 redeem 不重复发券」的当前行为，改动后需回归该用例）。

---

## 2. Q8 — 会员不存在时静默漏发，无任何痕迹 🔴 P1

### 2.1 缺陷位置

| 项 | 内容 |
|---|---|
| 方法 | `backend/src/main/java/com/meiyetec/service/InviteService.java` — `grantInviteeReward`（L891） |
| 缺陷行 | **L895**：`if (invitee == null) return;` |
| 对照 | `grantInviterReward` L814-815：`if (inviter == null) throw new BusinessException(BizCode.MEMBER_NOT_FOUND, "邀请人不存在");` —— **邀请人侧是抛异常**，新人侧是静默 return |
| 调用方 | `rewardOnFirstOrder` L528-531：`grantInviterReward(c, r); grantInviteeReward(c, r); r.setStatus(4);` |

### 2.2 缺陷机制

```java
private void grantInviteeReward(InviteCampaign c, InviteRecord r) {
    if (r.getInviteeMemberId() == null) return;   // L894 —— 未注册，不发新人奖（合理）
    Long inviteeId = r.getInviteeMemberId();
    Member invitee = memberMapper.selectById(inviteeId);
    if (invitee == null) return;                   // L895 ← 缺陷：静默返回
    ...
}
```

调用链：

```
grantInviteeReward 静默 return（不发券、不写 reward_log、无异常）
        ↓
grantInviterReward 正常完成（邀请人券已发）
        ↓
r.setStatus(4)                                    // L530 —— record 被标为「已奖励」
        ↓
recordMapper.updateById(r)
```

**后果**：

- 新人券**完全没发**，但 record 状态是 `status = 4`（已奖励）；
- `m09_invite_reward_log` 中**没有**对应的失败行（`logRow` 在 `return` 之后才创建，L899）；
- `fail_reason` 为 `NULL`；
- 运营在后台看记录：`status = 4` / `已奖励` → **看不出任何异常**；
- 该状态**无法**通过 `resend-reward` 补救（`resendReward` L763-765 对 `status == 4` 直接返回
  `CONFLICT "该记录已发放，无需重发"`）。

即：**新人奖励漏发 → 无痕迹 → 无法自助补救 → 只能靠用户投诉发现**。

**触发条件**（**2026-11-10 补充调查后已修正**）：

`r.getInviteeMemberId()` 非空但 `memberMapper.selectById(inviteeId)` 返回 null。

> ⚠️ **触发条件的性质已修正（软删除）**：
>
> 最初登记时本条被描述为「会员行**丢失**（会员被删除 / 数据迁移丢失 / 跨库引用错位）」——
> 属于**极端情况**。经 `SHOW CREATE TABLE crm_member` 实测（证据：
> `audit/evidence/F151-m09-invite-grant-chain/raw/15-crm-member-schema.txt`），
> `crm_member` 采用**软删除**模型：
>
> ```sql
> `deleted` tinyint DEFAULT '0',
> UNIQUE KEY `uk_phone` (`phone`,`deleted`),
> UNIQUE KEY `uk_member_no` (`member_no`,`deleted`),
> ```
>
> 实体侧 `backend/src/main/java/com/meiyetec/entity/Member.java:53` 标注
> `@TableLogic`（`@TableName("crm_member")`，L11），因此 MyBatis-Plus 的
> `memberMapper.selectById(inviteeId)` **会自动追加 `WHERE deleted = 0`**。
>
> **修正结论**：本缺陷的触发条件**从「会员行丢失」（极端）修正为「普通软删除」（常见）** ——
> 会员只要执行过一次逻辑删除（注销/停用/数据清理），其 `id` 仍留在 `m09_invite_record.invitee_member_id`，
> 但 `selectById` 查不到 → 必然走进 `L895` 的静默 `return`。
> 这不是数据异常，而是**正常运营动作（会员注销）触发的漏发路径**。
>
> **严重度：P2 → P1**（依据：触发频率由「罕见」上升为「每次会员注销都必现」，
> 且失败完全无痕迹、无法通过 `resend-reward` 补救）。

**建议修复**：

与邀请人侧对齐 —— 改为抛异常：

```java
if (invitee == null) {
    throw new BusinessException(BizCode.MEMBER_NOT_FOUND, "受邀人不存在");
}
```

这样异常会被 `rewardOnFirstOrder` 的 catch 捕获（L537-545），
写 `record.status = 5` + `fail_reason = "奖励发放失败：受邀人不存在"`，
并写一条 `reward_log` 失败行 —— **漏发有痕迹、可通过 `resend-reward` 补救**。

⚠️ 注意：改成抛异常后，邀请人券也会因 `@Transactional(rollbackFor = Exception.class)`
而回滚 —— 但 `rewardOnFirstOrder` 内部已经 catch 了异常，事务不会真的回滚，
`grantInviterReward` 已发的券**不会撤销**（该券已 INSERT 进 `market_coupon_grant`）。
这是**另一个潜在不一致**（邀请人发了、新人没发、record 标失败），但比当前的「静默漏发 + 标成功」
**可观测性更好**，且 `resend-reward` 可补发新人侧。
是否要在抛异常前**先完成邀请人侧发放**、把新人侧失败单独记录，需要在实施时一并决策。

---

### 2.3 关联发现 —— 同一软删除模式可能使 I147（Q4）的严重性被低估 🔴 待评估

同样的 `@TableLogic` 软删除模式**在另一条邀请链路中也存在**，需在修 I147 时一并评估：

| 项 | 内容 |
|---|---|
| 实体 | `backend/src/main/java/com/meiyetec/entity/MemberInviteReward.java:15` `@TableName("member_invite_reward")`，L53 `@TableLogic` |
| 位置 | `backend/src/main/java/com/meiyetec/service/MemberInviteService.java` — `grantRewardOnFirstOrder`（L217） |
| 代码 | `inviteRewardMapper.selectOne(new LambdaQueryWrapper<MemberInviteReward>().eq(tenantId).eq(inviteeId))` |
| 行为 | MyBatis-Plus 自动追加 `WHERE deleted = 0` → **软删除的邀请奖励记录查不到** → `if (r == null) return Result.success("无邀请关系", null);` |

即：`member_invite_reward` 行被逻辑删除后，「首单发奖」会**静默返回成功**（HTTP 200 + `"无邀请关系"`），
调用方无法区分「本来就没有邀请关系」与「邀请关系被软删了」。

这与 Q8 是**同型问题**（静默失效 + 无痕迹）。因此：

> **Q4（I147 `MemberInviteService.grantRewardOnFirstOrder` 只改状态不发券）的严重性
> 可能被低估** —— 除 I147 已记录的 TODO 假成功外，还需叠加评估「邀请奖励记录被软删除时
> 首单发奖静默跳过」这一路径。请在修 I147 时**一并评估并决定**是否需要区分
> 「无邀请关系」与「记录已删除」两种状态。

**本条不改变 I147 的结论**，仅标记为**实施 I147 修复时必须一并处理的关联项**。

---

## 3. Q9 — 脱敏手机号参与唯一键 → 不同手机号撞唯一键 🔴 P1

### 3.1 缺陷位置

| 项 | 内容 |
|---|---|
| 脱敏函数 | `InviteService.maskPhone`（L1142-1145）：`phone.substring(0,3) + "****" + phone.substring(len-4)` |
| 写入点 | `InviteService.trackClick` L436：`record.setInviteePhone(maskPhone(phone));` |
| 查询点 | L424（重复绑定判定）：`.eq(InviteRecord::getInviteePhone, maskPhone(phone))` |
| 绑定点 | `InviteService.bindOnRegister` L461-464：`.eq(InviteRecord::getInviteePhone, masked)` |
| 表 | `m09_invite_record.invitee_phone`（列注释：**「受邀人手机号（脱敏存储 138****1234）」**） |
| 唯一键 | `m09_invite_record` 的 `UNIQUE KEY uk_campaign_inviter_phone (campaign_id, inviter_member_id, invitee_phone)` |

### 3.2 缺陷机制

脱敏保留**前 3 位 + 后 4 位**，中间 4 位用 `****` 替代。而**这个脱敏值直接进唯一键**。

```sql
UNIQUE KEY `uk_campaign_inviter_phone` (`campaign_id`,`inviter_member_id`,`invitee_phone`)
```

**冲突示例**（同一活动、同一邀请人）：

| 真实手机号 | 脱敏后 | 唯一键值 |
|---|---|---|
| `13800138001` | `138****8001` | `(5, 1, '138****8001')` |
| `13800138001` | `138****8001` | `(5, 1, '138****8001')` ← 真重复，正确拦截 |
| **`13812348001`** | **`138****8001`** | `(5, 1, '138****8001')` ← **不同的人，误判为重复** |
| **`13856788001`** | **`138****8001`** | `(5, 1, '138****8001')` ← **不同的人，误判为重复** |

即：**前 3 位与后 4 位都相同的两个不同手机号**，在同一活动同一邀请人下**撞唯一键**。

**具体后果分两层**：

1. **应用层（先于 DB 触发）**：L421-427 的重复绑定判定
   ```java
   Long existSame = recordMapper.selectCount(... .eq(InviteRecord::getInviteePhone, maskPhone(phone)));
   if (existSame >= MAX_BIND_PER_PHONE) {   // MAX_BIND_PER_PHONE = 1 (L59)
       return Result.error(BizCode.CONFLICT, "该手机号已绑定过此活动的邀请");
   }
   ```
   不同手机号脱敏后相同 → `existSame >= 1` → **返回「该手机号已绑定过此活动的邀请」** →
   真实的新受邀人**无法通过该邀请链接完成 track** → **邀请链路直接断掉**，且用户看到的是
   「该手机号已绑定过」这种**误导性提示**。

2. **DB 层（唯一键兜底）**：若绕过应用层（并发 track / 历史数据修复），INSERT 会撞
   `uk_campaign_inviter_phone` → 抛 `DuplicateKeyException` → 500 错误
   （**与 I134「缺请求参数返回 500」同类的问题形态**）。

**注意反向不对称**：同一手机号的**重复**判定是**期望行为**（`MAX_BIND_PER_PHONE = 1`
= 一人只能被同一邀请人邀请一次），问题不在「判重」本身，而在**判重用的键是脱敏值**。

**建议修复**：

- **正解**：唯一键与判重都用**原始手机号**（或手机号的哈希），脱敏值仅用于**展示**。
  具体做法：新增列 `invitee_phone_hash`（`CHAR(32)`，`SHA256(phone + salt)`），
  唯一键改为 `(campaign_id, inviter_member_id, invitee_phone_hash)`，
  `invitee_phone` 列保留脱敏值仅供展示；
- **次优（不改表）**：应用层判重改为**精确匹配原始手机号**——但原始手机号未落库，
  **做不到**。除非在 `m09_invite_code` 之外另存一份原始手机号 → 同样需要改表。
- **最小妥协**：把脱敏保留位数从「前 3 + 后 4」改为**前 3 + 后 6**（或加长），
  降低撞键概率 —— 但这是**概率性缓解**，不是根治，且 `varchar(20)` 列宽需确认
  （`138****8001` 是 10 字符，扩到 13 字符仍在 20 以内，**列宽够**）。仍不推荐作为终态方案。

⚠️ 该修复涉及 **DDL 变更 + 唯一键重建**，需评估存量数据
（现存 `m09_invite_record` 行的 `invitee_phone` 都是脱敏值，**无法反推原手机号** → 唯一键重建时
存量行的 hash 需要用脱敏值算，会导致存量与新数据口径不一致，需全量回填决策）。

---

## 4. 三个问题的相互关系

```
                    InviteService.rewardOnFirstOrder
                              │
              ┌───────────────┼───────────────┐
              │               │               │
         Q5 查询只取        Q8 静默 return    Q9 唯一键用脱敏值
         status=2            （新人侧）        （写入层）
              │               │               │
    高门槛活动受影响     会员软删除时受影响   所有活动都受影响
              │               │               │
         人工补发            record 标 4      应用层误拒 / DB 500
        (resend-reward)     → 无法补救         → 邀请链路断
```

**共同特征**：三者都是**静默失效** —— 不抛 5xx、不写失败日志、后台看不出异常。
这正是本 issue 单独立册的理由：它们不属于「闸门稳定性」（I148）或「表结构技术债」（I145），
而是**业务规则实现层面的错误**。

---

## 5. F151 批 2 实测结论（限定本 issue 的边界）

**已实测确认的**：

| 项 | 结论 | 证据 |
|---|---|---|
| 正常发放链路 | ✅ T1 PASS（双券各 1 张，record status=4，reward_log 2 行 status=1） | `raw/30-t1-db-verify.txt` |
| `total_count = 0` / `IS NULL` 不限量 | ✅ T2 / T3 PASS | `raw/32-` `raw/34-t3-db-verify.txt` |
| `per_limit = 1` 限领生效，邀请人券不超发 | ✅ T4 PASS（`inviter_grant_cnt = 1`） | `raw/36-t4-db-verify.txt` |
| 「已达发放上限」的实际响应形态 | HTTP **200** + `data = 0` + `record.status = 5` + `fail_reason = "奖励发放失败：数据冲突: 邀请券已达发放上限"`（**不是 HTTP 409**） | `raw/36-t4-db-verify.txt` |
| 重复 redeem 幂等 | ✅ T5 PASS（grant 仍 2 行 / log 仍 2 行 / 第 2 次 `data = 0`） | `raw/38-t5-db-verify.txt` |
| 脱敏手机号确实参与唯一键 | ✅ **表结构实测确认**：`uk_campaign_inviter_phone (campaign_id, inviter_member_id, invitee_phone)`，列注释明写「脱敏存储」 | `SHOW CREATE TABLE m09_invite_record`（见本文档 §3.1） |

**未实测的（静态分析）**：

- Q5 的「后续更高金额订单不自动补发」—— 本批活动 `inviteeMinConsume = 0`（默认），
  未触发门槛分支，**未做运行时复现**
- Q8 的「会员不存在静默 return」—— 本批会员 1/2/3 均存在，**未构造缺失会员场景**；
  但**触发条件已由表结构实测修正**：`crm_member` 为软删除模型（`raw/15-crm-member-schema.txt`）
  + `Member.java:53 @TableLogic` → `selectById` 自动带 `WHERE deleted = 0`
  → 触发条件为「普通软删除」（常见），而非「会员行丢失」（极端）
- Q9 的「撞唯一键」—— 本批手机号 `13800138002` / `13800138003` 脱敏后不同，**未构造撞键样本**

> 上述三项的**代码路径与表结构均已确认**，但**运行时未复现** → 按验收规则标 **UNVERIFIED（运行时）**，
> 结论本身基于静态分析 + 表结构实测。

---

## 6. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（容器 `meiyetec-mysql`，MySQL 8.0.46） |
| 应用 | `http://localhost:8080`（容器 `meiyetec-api`） |
| 基线 commit | `b4fe257dee42630d1a31b0dcb76a863719a09fe5` |
| 环境基线 | `audit/baseline/env.md` |
| 证据目录 | `audit/evidence/F151-m09-invite-grant-chain/`（含 T1-T5 全部 DB 验证与 transcript） |
| 取证时间 | 2026-11-10 23:14 – 23:18 (UTC+8) |
| 补充调查 | 2026-11-10 23:35 — Q8 触发条件修正（`crm_member` 软删除模型），证据 `raw/15-crm-member-schema.txt`（`SHOW CREATE TABLE crm_member` 实测输出） |
