# F151 — M09 邀请奖励发放链路真实数据验证（T1-T5）

> **Feature ID**：F151
> **Slug**：`m09-invite-grant-chain`
> **批次**：批 2（Part B — 真实数据发放链路验证）
> **执行时间**：2026-11-10 23:14 – 23:18 (UTC+8)
> **基线 commit**：`b4fe257dee42630d1a31b0dcb76a863719a09fe5`
> **应用**：`http://localhost:8080`（容器 `meiyetec-api`）
> **数据库**：`meiyetec` @ `127.0.0.1:3307`（容器 `meiyetec-mysql`，MySQL 8.0.46）
> **环境基线**：`audit/baseline/env.md`
> **结论**：**T1-T5 全部 PASS**；T3（D1 修复核心验证点）**PASS**
> **新登记**：I148（atomicGrant 稳定性）、I149（奖励链路业务缺陷）

---

## 1. 总体结论

| 用例 | 验证点 | 结果 |
|---|---|---|
| **T1** | 正常发放：track → bind → redeem 全链路 | ✅ **PASS** |
| **T2** | `total_count = 0` 视为不限量 | ✅ **PASS** |
| **T3** | `total_count IS NULL` 视为不限量 —— **D1 修复核心验证点** | ✅ **PASS** |
| **T4** | `per_limit = 1` 限领 —— 第 2 轮邀请人不超发 | ✅ **PASS** |
| **T5** | 重复 redeem 幂等 —— 不重复发券 | ✅ **PASS** |

**T3 明确结论**：D1 修复（`CouponGrantMapper.xml:149` 的 `total_count IS NULL OR total_count = 0`）**已生效**。
`total_count IS NULL` 的券正常发放 2 张（`market_coupon_grant` 新增 2 行），`m09_invite_record.status = 4`，
`m09_invite_reward_log` 2 行 `status = 1`，`fail_reason = NULL`。**无「已达发放上限」误报**。

**T4 明确结论**：实测结果与预期**完全一致** —— HTTP **200**（不是 409）+ `data = 0` +
`m09_invite_record.status = 5` + `fail_reason = "奖励发放失败：数据冲突: 邀请券已达发放上限"`。
代码行为未变更，`rewardOnFirstOrder` 捕获异常后不重抛。

---

## 2. R1 结论回顾（本批前置条件）

R1（`raw/14-r1-conclusion.md`）用 JDBC 探针证明：`atomicGrant` 的 affected-rows 是
**matched-rows**（不是 changed-rows），因此 T1-T5 **不需要** sleep 对冲、**不需要**分券规避。

| 探针分组 | 语义 | 第 1 次 | 第 2 次（同秒） |
|---|---|---|---|
| Part A（简单 UPDATE） | matched-rows | 1 | 1 |
| Part B（`atomicGrant` JOIN 形态） | matched-rows | 1 | 1 |
| **Part C（对照组，强制 `useAffectedRows=true`）** | changed-rows | 1 | **0** |

Part C 证明探针具备区分能力 → Part A/B 的 1/1 是有效证据。

**遗留风险已转嫁**：`matched-rows` 依赖 Connector/J `useAffectedRows=false` **默认值**，
生产 `SPRING_DATASOURCE_URL` 由 ENV 注入、未审计 → 登记为 **I148 R3-a / R3-c**。

---

## 3. T1 — 正常发放 ✅ PASS

**前置**：建 2 张券（`test-batch2-t1-inviter` id=14 / `test-batch2-t1-invitee` id=15，
`total_count=5, per_limit=1`）→ 压 `updated_at` 到 `2020-01-01` → 建活动 `test-batch2-t1`（campaignId=2）
→ fail-fast 校验 orderId=99999999 不存在 → 取邀请码 `94AM5SNB`。

**请求 / 响应**：

| 步骤 | 请求 | HTTP | 关键响应 |
|---|---|---|---|
| track | `POST /invite/track {code, phone:13800138002, channel:LINK}` | 200 | `data.id = 2`，`status = 1` |
| bind | `POST /invite/bind {phone, memberId:2, name}` | 200 | `data = 1`（更新 1 条待注册记录） |
| redeem | `POST /invite/redeem {inviteeMemberId:2, orderId:99999999, orderAmount:100}` | 200 | `data = 1` |

**DB 验证**（`raw/30-t1-db-verify.txt`）：

```
market_coupon_grant:  id=11 coupon=14 member=1 tenant=1 status=1 source=6   ← 邀请人券
                      id=12 coupon=15 member=2 tenant=1 status=1 source=6   ← 新人券
m09_invite_record:    id=2 status=4 inviter=1 invitee=2 first_order_id=99999999 fail_reason=NULL
m09_invite_reward_log:id=3 reward_to=1 reward_type=1 status=1 coupon_grant_id=11
                      id=4 reward_to=2 reward_type=1 status=1 coupon_grant_id=12
grant_cnt = 2   ✅（预期 2）
```

**清理**：✅ 回到基线 `4 / 8 / 0 / 0 / 0 / 0`

---

## 4. T2 — `total_count = 0` 视为不限量 ✅ PASS

**前置**：券 `test-batch2-t2-inviter` id=16 / `test-batch2-t2-invitee` id=17，`total_count = 0`、
`per_limit = 1`；活动 campaignId=3；邀请码 `6GQ396MC`。

**请求 / 响应**：track / bind / redeem 三步均 HTTP 200，redeem `data = 1`。

**DB 验证**（`raw/32-t2-db-verify.txt`）：

```
market_coupon:   id=16 test-batch2-t2-inviter total_count=0 per_limit=1
                 id=17 test-batch2-t2-invitee total_count=0 per_limit=1
market_coupon_grant: id=13 coupon=16 member=1 status=1 source=6
                     id=14 coupon=17 member=2 status=1 source=6
m09_invite_record:   id=3 status=4 fail_reason=NULL
m09_invite_reward_log: id=5 reward_to=1 status=1 coupon_grant_id=13
                       id=6 reward_to=2 status=1 coupon_grant_id=14
grant_cnt = 2   ✅（预期 2）
```

**清理**：✅ 回到基线

---

## 5. T3 — `total_count IS NULL` 视为不限量（D1 修复核心验证点）✅ PASS

**前置**：走 **SQL 路径**建券（API 路径的 `totalCount` 无法表达 NULL），
`test-batch2-t3-inviter` id=20 / `test-batch2-t3-invitee` id=21，`total_count = NULL`、
`per_limit = 1`；活动 campaignId=4；邀请码 `LXWDBB9S`。

**请求 / 响应**：track / bind / redeem 三步均 HTTP 200，redeem `data = 1`。

**DB 验证**（`raw/34-t3-db-verify.txt`）：

```
market_coupon:   id=20 test-batch2-t3-inviter total_count=NULL per_limit=1   ← 前置断言：NULL 确认
                 id=21 test-batch2-t3-invitee total_count=NULL per_limit=1
market_coupon_grant: id=15 coupon=20 member=1 status=1 source=6
                     id=16 coupon=21 member=2 status=1 source=6
m09_invite_record:   id=4 status=4 fail_reason=NULL
m09_invite_reward_log: id=7 reward_to=1 status=1 coupon_grant_id=15
                       id=8 reward_to=2 status=1 coupon_grant_id=16
grant_cnt = 2   ✅（预期 2）

[ASSERT] total_count IS NULL 的券行数 = 2（预期 2）  ✅
[ASSERT] market_coupon_grant 新增行数 = 2（预期 2）    ✅
[T3 RESULT] PASS
```

> **D1 判定**：修复前旧写法 `total_count > COUNT(*)` 在 `total_count = NULL` 时求值为 NULL
> → 条件恒不成立 → 误报「已达发放上限」。实测 **无此误报**，D1 修复有效。

**清理**：✅ 回到基线

---

## 6. T4 — `per_limit = 1` 限领（不超发）✅ PASS

**前置**：邀请人券 `test-batch2-t4-inviter` id=24（`total_count=100, per_limit=1`）、
新人券 `test-batch2-t4-invitee` id=25（`total_count=100, per_limit=0`）；活动 campaignId=5；
邀请码 `MQEP7E6Y`。fail-fast 校验 orderId 99999901 / 99999902 均不存在。

**两轮执行**（同一邀请人 member=1，第二轮换一个受邀人 member=3）：

| 轮次 | 受邀人 | orderId | redeem HTTP | redeem data |
|---|---|---|---|---|
| R1 | member=2 / 13800138002 | 99999901 | **200** | **1** |
| R2 | member=3 / 13800138003 | 99999902 | **200** | **0** |

**DB 验证**（`raw/36-t4-db-verify.txt`）：

```
market_coupon_grant: id=19 coupon=24 member=1 status=1 source=6   ← 邀请人券，仅 1 张
                     id=20 coupon=25 member=2 status=1 source=6   ← 新人券（round1）
inviter_grant_cnt = 1   ✅（预期 1，不得超发）

m09_invite_record:
  id=7 status=4 inviter=1 invitee=2 first_order_id=99999901 fail_reason=NULL
  id=8 status=5 inviter=1 invitee=3 first_order_id=99999902
        fail_reason=奖励发放失败：数据冲突: 邀请券已达发放上限

m09_invite_reward_log:
  id=12 reward_to=1 status=1 coupon_grant_id=19 fail_reason=NULL
  id=13 reward_to=2 status=1 coupon_grant_id=20 fail_reason=NULL
  id=14 reward_to=1 status=2 coupon_grant_id=NULL fail_reason=数据冲突: 邀请券已达发放上限
```

**预期命中**：实测与预期**完全一致** —— HTTP **200**（非 409）+ `data = 0` +
`record.status = 5` + `fail_reason` 含「已达发放上限」。
原因：`InviteService.rewardOnFirstOrder`（L528-545）捕获 `BusinessException` 后写
`status = 5` + `fail_reason = "奖励发放失败：" + e.getMessage()`，**不重抛**。

**限领有效性**：邀请人券 `per_limit = 1` 下，第 2 轮受邀人的邀请人奖励被正确拦截，
`market_coupon_grant` 中 `coupon=24 AND member_id=1` **只有 1 行** —— **未超发**。

**清理**：✅ 回到基线

---

## 7. T5 — 重复 redeem 幂等 ✅ PASS

**前置**：券 `test-batch2-t5-inviter` id=26 / `test-batch2-t5-invitee` id=27（`total_count=5, per_limit=1`）；
活动 campaignId=7；邀请码 `B332J3JY`；orderId=99999999。

**请求 / 响应**：

| 次序 | 请求 | HTTP | data |
|---|---|---|---|
| 1st | track + bind + redeem(orderId=99999999) | 200 | `1` |
| 2nd | `POST /invite/redeem {inviteeMemberId:2, orderId:99999999, orderAmount:100}` | 200 | **`0`** |

**DB 验证**（`raw/38-t5-db-verify.txt`）：

```
grant_cnt = 2   ✅（预期仍为 2，幂等未新增）
market_coupon_grant: id=21 coupon=26 member=1 status=1 source=6
                     id=22 coupon=27 member=2 status=1 source=6
m09_invite_record:   id=9 status=4 fail_reason=NULL   （仅 1 条 record）
log_cnt = 2          ✅（reward_log 未重复写）
```

**幂等判定**：第 2 次 redeem 返回 `data = 0`（未新增发放），`market_coupon_grant` 仍 2 行，
`m09_invite_reward_log` 仍 2 行 → **幂等成立**。

**清理**：✅ 回到基线

---

## 8. 清理与基线复核

每个用例结束立即执行统一清理 SQL（删除 `test-batch2-%` 命名的券 / 活动 / 记录 / 流水），
并复核 6 表行数。

| 表 | 基线 | T1 后 | T2 后 | T3 后 | T4 后 | T5 后（最终） |
|---|---|---|---|---|---|---|
| `market_coupon` | 4 | 4 ✅ | 4 ✅ | 4 ✅ | 4 ✅ | 4 ✅ |
| `market_coupon_grant` | 8 | 8 ✅ | 8 ✅ | 8 ✅ | 8 ✅ | 8 ✅ |
| `m09_invite_campaign` | 0 | 0 ✅ | 0 ✅ | 0 ✅ | 0 ✅ | 0 ✅ |
| `m09_invite_code` | 0 | 0 ✅ | 0 ✅ | 0 ✅ | 0 ✅ | 0 ✅ |
| `m09_invite_record` | 0 | 0 ✅ | 0 ✅ | 0 ✅ | 0 ✅ | 0 ✅ |
| `m09_invite_reward_log` | 0 | 0 ✅ | 0 ✅ | 0 ✅ | 0 ✅ | 0 ✅ |

**最终态**：`4 / 8 / 0 / 0 / 0 / 0` —— **与基线完全一致，无残留**。

---

## 9. 本批暴露的业务缺陷（已登记 ISSUE，不在本批修）

| 编号 | 缺陷 | 严重度 | ISSUE |
|---|---|---|---|
| **Q5** | 未达消费门槛后置 `status = 3`（已首单），但 `rewardOnFirstOrder` 只查 `status = 2` → 后续更高金额订单**无法自动补发**，只能人工 `POST /invite/resend-reward` | P1 | I149 |
| **Q6** | `atomicGrant` 的 `total_count` / `per_limit` 计数子查询**未带 `tenant_id`** → 跨租户互相扣配额 | P1 | I148 |
| **Q7** | `atomicGrant` 只做条件 UPDATE、不做 INSERT；闸门与写入分离 → 并发下仍可能超发 | P1 | I148 |
| **Q8** | `grantInviteeReward` 在会员不存在时**静默 `return`**，不发券、不写 reward_log，但 record 仍置 `status = 4` → **漏发无痕迹** | P1 | I149 |
| **Q9** | `m09_invite_record.invitee_phone` 存**脱敏**手机号且参与唯一键 `uk_campaign_inviter_phone` → 前 3 后 4 相同的不同手机号会撞唯一键 | P1 | I149 |
| **R3-a** | matched-rows 隐式依赖驱动默认值 `useAffectedRows=false` → 生产配置翻车会**静默失败** | P1 | I148 |
| **R3-c** | `application-prod.yml:42` 的 `SPRING_DATASOURCE_URL` 由 ENV 注入，**生产实际值未审计** | P1 | I148 |

---

## 10. 证据文件清单

| 文件 | 内容 |
|---|---|
| `raw/00-backup-before.sql` | Step 0 六表全量备份（16827 字节） |
| `raw/00-baseline-counts.txt` | Step 0 基线行数快照 |
| `raw/10-r1-setup.txt` | R1 建券 + 压 `updated_at` |
| `raw/11-r1-simple-update.txt` | R1 实验一（CLI `ROW_COUNT()`）1/0 |
| `raw/12-r1-jdbc-probe.txt` | R1 实验二（JDBC Part A/B/C）完整输出 |
| `raw/13-r1-cleanup-verify.txt` | R1 清理 + 基线复核 |
| `raw/14-r1-conclusion.md` | **R1 结论全文** |
| `raw/20-login-res.json` | admin 登录响应 |
| `raw/21-seed-members-check.txt` | 会员种子数据确认（member 1/2/3 存在） |
| `raw/30-t1-db-verify.txt` / `raw/31-t1-transcript.txt` | T1 DB 验证 + 完整请求/响应 |
| `raw/32-t2-db-verify.txt` / `raw/33-t2-transcript.txt` | T2 同上 |
| `raw/34-t3-db-verify.txt` / `raw/35-t3-transcript.txt` | T3 同上（**D1 核心证据**） |
| `raw/36-t4-db-verify.txt` / `raw/37-t4-transcript.txt` | T4 同上 |
| `raw/38-t5-db-verify.txt` / `raw/39-t5-transcript.txt` | T5 同上 |
| `raw/40-t1-run.txt` … `raw/44-t5-run.txt` | 五个用例的完整运行日志（含 stdout/stderr/退出码） |
| `raw/R1AffectedRowsProbe.java` | ⚠️ 非正式工具：JDBC affected-rows 探针源码 |
| `raw/RunBatch2Case.ps1` | ⚠️ 非正式工具：T1-T5 执行脚本（**未进入 `audit/scripts/` 正式守门序列**） |

> ⚠️ `RunBatch2Case.ps1` 与 `R1AffectedRowsProbe.java` 均为**调查附带工具**，仅存放于 evidence 目录，
> 文件头已标注「非正式工具」，不接入 CI / PR 必跑清单。

---

## 11. 执行方法学说明

- **数据安全**：所有写操作使用 `test-batch2-*` 前缀；`orderId` 用 `99999999 / 99999901 / 99999902`
  （写前 fail-fast 校验 `m09_invite_record` 中不存在）；每用例结束立即物理删除并复核基线。
- **无 sleep / 无分券规避**：依据 R1 结论，`atomicGrant` 为 matched-rows 语义，
  同秒重复调用不会误判「已达发放上限」，故 T1-T5 全程**未使用任何 sleep 对冲**，
  邀请人券 / 新人券**未分券**（本来就是两张不同券）。
- **券的 `updated_at` 前置压平**：所有用例建券后执行
  `UPDATE market_coupon SET updated_at='2020-01-01 00:00:00'`，确保 `atomicGrant` 的
  `SET c.updated_at = NOW()` 确实产生列值变化 —— 该前置在 matched-rows 语义下**不是必需**，
  但可作为「即使改成 changed-rows 语义、T1-T3 也应通过」的额外对照。
- **编码**：mysql 客户端加 `--default-character-set=utf8mb4`，输出文件按 UTF-8 读取，
  避免 PowerShell 5.1 默认 ANSI 读取导致中文 `fail_reason` 乱码。

---

## 12. 状态判定

| 范围 | 判定 |
|---|---|
| F151 发放链路正常发放 / `total_count=0` / `total_count IS NULL` / `per_limit` 限领 / 幂等 | **VERIFIED**（T1-T5 全 PASS + 基线复核一致） |
| I145（`issued_count` 方案 B） | **Open**（本批不评估） |
| I148（atomicGrant 稳定性） | **Open**（新登记） |
| I149（奖励链路业务缺陷 Q5/Q8/Q9） | **Open**（新登记） |

> 本批**不修改任何业务代码**，仅执行验证与登记 ISSUE。
