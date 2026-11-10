# I148 — `atomicGrant` 发放闸门的稳定性与口径缺陷（配置脆弱性 / 跨租户扣配额 / 并发超发）

> **状态**：Open
> **严重度**：P1 — 当前**不导致单租户单线程下的功能错误**（F151 批 2 实测 T1-T5 全 PASS），但存在**跨租户互相扣配额**与**并发超发**的真实风险，且生产配置未审计
> **发现日期**：2026-11-10（F151 批 2 实测）
> **登记人**：Cursor（F151 m09 邀请发放链路验证批次）
> **决策**：用户 2026-11-10 拍板 —— I145 **保持原样**（只做 `issued_count` 方案 B 的技术债登记），`atomicGrant` 的稳定性与口径问题**单独立本 issue**
> **关联**：I145（`issued_count` 方案 B，方案 B 需与本 issue 的 Q7 一并评估）、F150（m09 invite 解封）、F151（批 2 实测证据）

---

## 0. 一句话描述

`CouponGrantMapper.atomicGrant` 是邀请奖励与手动发券共用的**配额闸门**。它有三个未闭合的问题：

1. **配置脆弱** —— 它的返回值语义（matched-rows）**隐式依赖** Connector/J `useAffectedRows=false` 这个**驱动默认值**，生产 `SPRING_DATASOURCE_URL` 由 ENV 注入、**实际值未经审计**，一旦被改成 changed-rows 语义就会**静默翻转**为「同秒重复调用必报已达发放上限」；
2. **跨租户口径错误** —— 配额计数的两个子查询**都没有 `tenant_id` 过滤** → 跨租户互相扣配额；
3. **并发超发** —— 闸门（条件 UPDATE）与写入（`INSERT INTO market_coupon_grant`）**分离**，非同一原子操作。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| SQL | `backend/src/main/resources/mapper/CouponGrantMapper.xml` — `<update id="atomicGrant">`（L140-168） |
| 接口 | `backend/src/main/java/com/meiyetec/mapper/CouponGrantMapper.java` — `int atomicGrant(@Param("couponId") Long couponId, @Param("memberId") Long memberId)` |
| 调用方 | `backend/src/main/java/com/meiyetec/service/InviteService.java` — `grantInviterReward`（L811，`atomicGrant` 调用在 L827）、`grantInviteeReward`（L891，调用在 L907） |
| 涉及表 | `market_coupon`（券主表）/ `market_coupon_grant`（发放流水） |

### atomicGrant 现状 SQL（L140-168）

```sql
UPDATE market_coupon c
JOIN (
    SELECT id FROM market_coupon
    WHERE id = #{couponId}
      AND status = 1
      AND (start_time IS NULL OR start_time <= NOW())
      AND (end_time IS NULL OR end_time >= NOW())
      AND (
          total_count IS NULL OR total_count = 0        -- D1 修复：NULL / 0 视为不限量
          OR total_count > (
              SELECT COUNT(*) FROM market_coupon_grant
              WHERE coupon_id = #{couponId} AND status != 3     -- ← 无 tenant_id（Q6）
          )
      )
      AND (
          per_limit IS NULL OR per_limit = 0
          OR (
              SELECT COUNT(*) FROM market_coupon_grant
              WHERE coupon_id = #{couponId}
                AND member_id = #{memberId}
                AND status != 3                                -- ← 无 tenant_id（Q6）
          ) < per_limit
      )
    LIMIT 1
) sub ON c.id = sub.id
SET c.updated_at = NOW()                                    -- ← 不携带任何计数
WHERE c.id = #{couponId}
```

---

## 2. 问题清单

### R3-a — matched-rows 隐式依赖驱动默认值 🟠 P1

**事实**（F151 批 2 R1 实测，见 `audit/evidence/F151-m09-invite-grant-chain/raw/14-r1-conclusion.md`）：

| 探针 | JDBC URL | 第 1 次 | 第 2 次（同秒） |
|---|---|---|---|
| Part A（简单 UPDATE） | `application.yml` 默认值 | 1 | 1 |
| Part B（`atomicGrant` JOIN 形态） | `application.yml` 默认值 | 1 | 1 |
| **Part C（对照组）** | 追加 `&useAffectedRows=true` | 1 | **0** |

`application.yml:6` 的 JDBC URL **未写** `useAffectedRows` → 取 Connector/J 默认值 `false` →
发送 `CLIENT_FOUND_ROWS` → JDBC 返回 **matched-rows**。

**风险**：matched-rows 行为**完全来自驱动默认值**，不是显式声明。若出现下列任一情况，语义**静默翻转**为 changed-rows
（此时 `atomicGrant` 只改 `updated_at`，同秒内第二次 UPDATE 因值未变而返回 0 →
`InviteService.grantInviterReward` 抛 `BizCode.CONFLICT "邀请券已达发放上限"` →
**误报发放失败**）：

- 生产 ENV 注入的 `SPRING_DATASOURCE_URL` 追加了 `useAffectedRows=true`
- 更换非 Connector/J 驱动（MariaDB Connector / ProxySQL / 自研代理）
- 中间件（ShardingSphere / 读写分离代理）重写或拦截了 UPDATE 语句
- Connector/J 版本升级改变默认值（历史上默认即为 `false`，但无显式声明即无契约）

**无任何编译期 / 启动期告警** —— 翻转只在生产运行时表现为「偶发发放失败」。

**建议修复**（本 issue 只登记，不在本批改代码）：

- 在 `application.yml` 的 JDBC URL **显式写出** `useAffectedRows=false`，把「靠默认值」变成「显式声明」；
- 在 `CouponGrantMapper.xml` 的 `atomicGrant` 注释中写明该前置条件（该 XML 已有 L126-134 的详细注释块，可直接扩展）；
- 更彻底的做法见 Q7（改用真正的原子计数器）—— 一旦 `atomicGrant` 携带真实计数变更，matched/changed 语义差异**不再影响业务判定**。

---

### R3-c — 生产 JDBC URL 未审计 🟠 P1

**事实**：

- `backend/src/main/resources/application-prod.yml:42` → `url: ${SPRING_DATASOURCE_URL:}`（**空默认值，ENV 注入**）
- `backend/src/main/resources/application.yml:6` → `url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3307/meiyetec?useUnicode=true&...&allowPublicKeyRetrieval=true}`（**无** `useAffectedRows`）

即：**生产实际使用的 JDBC URL 不在仓库中**，本次 R1 结论只覆盖**本地开发默认值**。
生产是否显式带了 `useAffectedRows=true`、或换了其他驱动 —— **未确认**。

**建议修复**：把生产 `SPRING_DATASOURCE_URL` 的**实际值**（脱敏后）纳入 `audit/baseline/env.md`
的审计条目，并在部署清单中固定「不得出现 `useAffectedRows=true`」。

---

### Q6 — 配额计数未带 `tenant_id` → 跨租户互相扣配额 🔴 P1

**事实**：`atomicGrant` 的两个 `COUNT(*)` 子查询（L151-152、L158-161）**均只按 `coupon_id` /
`coupon_id + member_id` 过滤**，**没有 `tenant_id` 条件**；`market_coupon` 主表的子查询
（L145 起）也**未校验 `tenant_id`**。

**影响**：`market_coupon_grant.tenant_id` 是真实存在的列（表结构见 I145 §2.2），
`market_coupon_grant` 表中不同租户的行**共用同一个 `coupon_id` 空间**（券 id 是自增全局主键，
不按租户分段）。因此：

- 租户 A 的券发放到第 50 张（`total_count = 50`）时，租户 B 使用**同一张券**（跨租户共用券，
  或数据迁移导致 id 重叠）会因为 `COUNT(*)` 计入 A 的 50 行而**误判「已达发放上限」**；
- 反向地，`per_limit` 判定同样会被其他租户的同 `member_id` 行干扰。

**当前未在本批暴露的原因**：本批测试环境为**单租户**（`tenant_id = 1`），F151 T1-T5 全部单租户，
故无法通过实测复现跨租户互相扣配额。**该结论为代码静态分析 + 表结构分析，未做跨租户运行时实测**。

**建议修复**：两个 `COUNT(*)` 子查询**显式加 `tenant_id` 条件**，且 `market_coupon` 主表子查询
同样加 `tenant_id` 校验（可从 `TenantContextHolder.get().getTenantId()` 取值传入，
或由 `atomicGrant` 的参数补一个 `tenantId` 入参）。注意：`CouponGrantMapper.xml` 中
另一个方法 `atomicGrantCoupon`（L64-95）**已经带 `tenant_id`**（L80、L87），
说明该文件内部已有正确范式，`atomicGrant` 属于**遗漏**。

---

### Q7 — 闸门与写入分离 → 并发下仍可能超发 🔴 P1

**事实**：`atomicGrant` 只做**条件 UPDATE**（`SET c.updated_at = NOW()`），
**不做 INSERT**；随后的 `INSERT INTO market_coupon_grant` 由调用方
（`InviteService.grantInviterReward` L833 / `grantInviteeReward` L913）单独执行。

`market_coupon` 表**没有 `issued_count` 计数列**（见 I145 §2.1），所以：

- 闸门的配额判定依据是 `COUNT(*) FROM market_coupon_grant` —— 一个**实时现算**的值；
- 两个并发事务可以**同时**通过 `total_count > COUNT(*)` 判定（都读到同一个 COUNT），
  然后**各自 INSERT** → 超出 `total_count`；
- `per_limit` 同理。

**窗口大小**：两次 `COUNT(*)` 聚合查询 + UPDATE + INSERT 之间，理论窗口为毫秒级。
概率低，但**在高并发发券场景（如批量发券活动、秒杀级邀请活动）必然出现**。

**与 I145 的关系**：I145 的**方案 B**（`market_coupon.issued_count` + `SET issued_count = issued_count + 1`）
**正是这个问题的根治方案**。但方案 B 有自己的代价（计数与流水双写不一致、需回填脚本、
作废/撤销时是否回退计数需定义、破坏「单一事实来源」）—— 见 I145 §5。

**建议修复**（需与 I145 方案 B 一并评估，不在本批做）：

- 短期：为 `market_coupon_grant` 建 `(coupon_id, member_id, status)` 复合索引降低 COUNT 成本，
  并把 `atomicGrant` 与 `INSERT` 合并到**单条 `INSERT ... SELECT ... WHERE` 条件写入**
  （`CouponGrantMapper.xml` L64-95 的 `atomicGrantCoupon` **已经是这个形态**，
  用 `INSERT ... SELECT` 配合条件子查询让「判定 + 写入」在一条语句内完成）。
- 中期：评估 I145 方案 B（`issued_count` 原子自增）。

---

## 3. 各问题的影响面与相互关系

```
                    atomicGrant（单条 SQL 闸门）
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
   R3-a 配置脆弱          Q6 租户口径          Q7 并发超发
   （返回值语义）        （COUNT 少条件）      （判定≠写入）
        │                     │                     │
  误报「已达上限」      跨租户扣配额         突破 total_count
        │                     │                     │
        └──────────► 需要真正的原子计数器 ◄────────┘
                        （= I145 方案 B）
```

**三者共同的根治方向是「用真正的原子计数替代 `COUNT(*)` 现算」**，
即 I145 方案 B；但方案 B 本身带双写一致性代价，需独立评估。
**R3-a 是三者中成本最低、见效最快的**：只需在 JDBC URL 显式声明 `useAffectedRows=false`。

---

## 4. F151 批 2 实测结论（限定本 issue 的边界）

**已实测确认的**：

| 项 | 结论 | 证据 |
|---|---|---|
| 单租户下单链路发放正常 | ✅ T1 PASS（grant_cnt=2） | `raw/30-t1-db-verify.txt` |
| `total_count = 0` 视为不限量 | ✅ T2 PASS | `raw/32-t2-db-verify.txt` |
| `total_count IS NULL` 视为不限量（D1 修复） | ✅ T3 PASS | `raw/34-t3-db-verify.txt` |
| `per_limit = 1` 时不超发 | ✅ T4 PASS（邀请人券仅 1 行） | `raw/36-t4-db-verify.txt` |
| 重复 redeem 幂等 | ✅ T5 PASS（仍 2 行 / 2 log） | `raw/38-t5-db-verify.txt` |
| 「已达发放上限」实际响应形态 | HTTP **200** + `data = 0` + `record.status = 5` + `fail_reason` 含「已达发放上限」（**不是 HTTP 409**） | `raw/36-t4-db-verify.txt` |

**未实测的（本文档中标注为静态分析）**：

- Q6 的跨租户互相扣配额 —— 测试环境单租户，**无法运行时复现**
- Q7 的并发超发 —— 未做并发压测
- R3-c 的生产 URL 实际值 —— **未确认**

---

## 5. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（容器 `meiyetec-mysql`，MySQL 8.0.46） |
| 应用 | `http://localhost:8080`（容器 `meiyetec-api`） |
| 基线 commit | `b4fe257dee42630d1a31b0dcb76a863719a09fe5` |
| 环境基线 | `audit/baseline/env.md` |
| 证据目录 | `audit/evidence/F151-m09-invite-grant-chain/`（含 `raw/14-r1-conclusion.md`、T1-T5 全部 DB 验证与 transcript） |
| 取证时间 | 2026-11-10 23:14 – 23:18 (UTC+8) |
