# R1 对照测试结果 — `atomicGrant` affected-rows 语义

> 批次：F151 批 2（`F151-m09-invite-grant-chain`）
> 执行时间：2026-11-10 22:58
> 结论：**matched-rows 语义成立**（不是 changed-rows）→ T1-T5 **不需要** sleep 对冲、**不需要**分券

---

## 1. 待验证的命题

`InviteService.grantInviterReward` / `grantInviteeReward` 通过 `couponMapper.atomicGrant(couponId, memberId)` 的
**受影响行数**判断发放是否成功：

```java
int updated = couponGrantMapper.atomicGrant(couponId, inviterId);
if (updated == 0) {
    throw new BusinessException(BizCode.CONFLICT, "邀请券已达发放上限");
}
```

而 `atomicGrant` 的 SQL 只修改了一个字段：

```xml
SET c.updated_at = NOW()
WHERE c.id = #{couponId}
```

`updated_at` 的列定义是 `datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`，
所以同一秒内第二次 UPDATE **实际值不变**。若 JDBC 返回的是 **changed-rows**，第二次必然返回 0 →
误判「已达发放上限」。若返回 **matched-rows**，第二次仍返回 1 → 链路正常。

---

## 2. 实验一：MySQL CLI 会话（`ROW_COUNT()`）

会话：容器 `meiyetec-mysql`，MySQL **8.0.46**，用 `mysql` CLI 直连。

```sql
-- 建券
INSERT INTO market_coupon (store_id,tenant_id,name,type,rule_type,rule_value,min_amount,
                           total_count,per_limit,start_time,end_time,status,remark)
VALUES (1,1,'test-batch2-r1',1,1,10.00,0.00,10,0,
        '2020-01-01 00:00:00','2027-12-31 23:59:59',1,'test-batch2-*');
-- → id = 5
UPDATE market_coupon SET updated_at='2020-01-01 00:00:00' WHERE id=5;  -- 压到过去

UPDATE market_coupon SET updated_at=NOW() WHERE id=5;
SELECT 'R1_A_FIRST'  AS step, ROW_COUNT() AS rc, NOW() AS t;   -- rc = 1
UPDATE market_coupon SET updated_at=NOW() WHERE id=5;
SELECT 'R1_A_SECOND' AS step, ROW_COUNT() AS rc, NOW() AS t;   -- rc = 0  ← 同秒
```

**结果：1 / 0（changed-rows）**

> ⚠️ 这个结果**不能直接套到应用上**：`mysql` CLI 默认**不**发送 `CLIENT_FOUND_ROWS` 标志，
> 而 MySQL Connector/J 的 `useAffectedRows` 默认值为 `false` → **会**发送 `CLIENT_FOUND_ROWS`
> → JDBC 拿到的是 matched-rows。所以必须做实验二。

---

## 3. 实验二：JDBC 层（决定性实验）

探针：`raw/R1AffectedRowsProbe.java`（⚠️ 非正式工具，置于 evidence 目录，未进入 `audit/scripts/`）
驱动：**mysql-connector-j 8.0.33**（与部署的 `app.jar` 内 `BOOT-INF/lib/mysql-connector-j-8.0.33.jar` 一致）
URL：与 `backend/src/main/resources/application.yml:6` **逐字符一致**（无 `useAffectedRows` 参数）

```java
String URL = "jdbc:mysql://localhost:3307/meiyetec?useUnicode=true&characterEncoding=UTF-8"
    + "&connectionCollation=utf8mb4_bin&serverTimezone=Asia/Shanghai"
    + "&createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true";
```

### Part A — 简单 UPDATE（与 CLI 同一 SQL，改用 JDBC 执行）

| 次序 | `executeUpdate()` |
|---|---|
| 第 1 次 | **1** |
| 第 2 次（同秒） | **1** |

### Part B — `atomicGrant` 的真实 JOIN 形态

SQL 与 `CouponGrantMapper.xml:140-168` 逐字一致（`UPDATE market_coupon c JOIN (SELECT id ...) sub
ON c.id = sub.id SET c.updated_at = NOW() WHERE c.id = ?`）：

| 次序 | `executeUpdate()` |
|---|---|
| 第 1 次 | **1** |
| 第 2 次（同秒，间隔 2ms） | **1** |

### Part C — 强制 changed-rows 对照组（URL 追加 `&useAffectedRows=true`）

| 次序 | `executeUpdate()` |
|---|---|
| 第 1 次 | 1 |
| 第 2 次（同秒） | **0** |

**Part C 证明探针本身具备区分能力**（同一 SQL、同一秒，仅切换连接标志即产生不同返回值），
因此 Part A/B 的 1/1 是有效证据，不是探针失效。

---

## 4. 结论

| 问题 | 答案 |
|---|---|
| `atomicGrant` 的 affected rows 是 changed-rows 还是 matched-rows？ | **matched-rows**（在当前 JDBC URL 下） |
| Q3「同秒重复调用必然误报已达发放上限」是否坐实？ | **不坐实**（当前配置下） |
| T1-T5 是否需要「两次发放间隔 sleep 2s」？ | **不需要** |
| T1/T2/T3 是否需要「inviter 券 / invitee 券分开」？ | **不需要**（本就是两张不同券） |

### 生效机制

MySQL 协议标志 `CLIENT_FOUND_ROWS` 决定 `UPDATE` 返回「匹配行数」还是「实际变更行数」：

- `mysql` CLI：默认**不**发该标志 → 返回 changed-rows（第 2 次 = 0）
- Connector/J `useAffectedRows=false`（默认）→ **发**该标志 → 返回 matched-rows（第 2 次 = 1）
- Connector/J `useAffectedRows=true` → 不发该标志 → 返回 changed-rows（第 2 次 = 0，Part C 实测）

当前 `application.yml:6` 的 JDBC URL **未配置** `useAffectedRows` → 采用默认值 false → **matched-rows**。

---

## 5. Q3 的修订结论（⚠️ 风险降级但未消失）

Q3 原文：「`atomicGrant` 的 affected-rows 语义存疑，同秒重复调用可能返回 0 → 误报已达发放上限」。

**当前配置下不成立**。但风险并未完全消除，而是**转嫁为另一条真实缺陷**：

| 风险 | 说明 | 级别 |
|---|---|---|
| **R3-a 隐式依赖驱动默认值** | `matched-rows` 行为依赖 `useAffectedRows` **默认值**。若任何环境（生产 `SPRING_DATASOURCE_URL` / 未来的 URL 变更）追加了 `useAffectedRows=true`，或换用非 Connector/J 驱动（如 MariaDB / ProxySQL），语义会**静默翻转为 changed-rows**，Q3 立刻坐实，且**无任何编译期/启动期告警**。 | 🟠 P1 |
| **R3-b 语义脆弱（业务层不可见）** | `matched-rows` 让 `atomicGrant` 的返回值**无法区分**「命中并更新」与「命中但值未变」。当前业务逻辑只判 `== 0`，恰好兼容；但若将来有人依赖 `updated==1` 表示「本次真的扣减了配额」，就会踩坑——因为**配额扣减根本没有原子计数**（见 Q7）。 | 🟡 P2 |
| **R3-c 生产 URL 未审计** | `application-prod.yml:42` 的 `SPRING_DATASOURCE_URL` 为空、由 ENV 注入，**生产实际值未经审计**。本批 R1 结论只覆盖本地 `application.yml:6` 默认值。 | 🟠 P1 |

> 📌 建议（仅登记，不在批 2 动代码）：在 `application.yml` 的 JDBC URL 显式写出 `useAffectedRows=false`，
> 把当前「靠默认值」变成「显式声明」；或在 `atomicGrant` 的 SQL 注释中写明该前置条件。

---

## 6. 清理与基线复核

R1 测试券 `test-batch2-r1`（id=5）已物理删除。

```
market_coupon         4   ✅ 与基线一致
market_coupon_grant   8   ✅ 与基线一致
m09_invite_campaign   0   ✅
m09_invite_code       0   ✅
m09_invite_record     0   ✅
m09_invite_reward_log 0   ✅
```

---

## 7. 原始证据文件

| 文件 | 内容 |
|---|---|
| `raw/00-backup-before.sql` | Step 0 全表备份（6 张表，16827 字节 / 281 行） |
| `raw/00-baseline-counts.txt` | Step 0 基线行数快照 |
| `raw/10-r1-setup.txt` | 建券 + 压 `updated_at`（券 id=5） |
| `raw/11-r1-simple-update.txt` | 实验一 CLI `ROW_COUNT()` 1/0 |
| `raw/12-r1-jdbc-probe.txt` | 实验二 JDBC Part A/B/C 完整输出 |
| `raw/13-r1-cleanup-verify.txt` | 清理 + 基线复核 |
| `raw/R1AffectedRowsProbe.java` | ⚠️ 非正式工具：JDBC 探针源码 |
