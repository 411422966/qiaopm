# I145 — market_coupon 缺少 issued_count 计数列，atomicGrant 无法在 UPDATE 内原子自增

> **状态**：Open（待评估）
> **严重度**：P2 — 当前**不导致功能错误**（方案 A 已把 total_count 语义补齐），但并发超发与发放量统计缺失
> **发现日期**：2026-11-10（F150 解封批次顺带发现）
> **登记人**：Cursor（F150 m09 invite 解封批次）
> **决策**：用户 2026-11-10 拍板 —— atomicGrant **只做方案 A**（改 SQL 适配真实表结构），**方案 B 单独立本 issue 待后续批次评估**
> **关联**：F150（m09 invite 解封，批 1 已 VERIFIED）、I135（legacy-missing-ddl 门控止血）、F131 P0 护栏 D4

---

## 0. 一句话描述

`market_coupon` 表**没有 `issued_count` 计数列**，导致 `CouponGrantMapper.atomicGrant`
的「条件 UPDATE」只能做「配额条件命中与否」的闸门判定，**无法在同一条 UPDATE 里原子自增发放计数** ——
极端并发下判定与随后的 `INSERT` 之间存在 TOCTOU 窗口，理论上可轻微超出 `total_count`；
同时**没有权威的「已发放数」落库字段**，只能靠 `COUNT(*) FROM market_coupon_grant` 现算。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| SQL | `backend/src/main/resources/mapper/CouponGrantMapper.xml` — `<update id="atomicGrant">`（L140-171） |
| 接口 | `backend/src/main/java/com/meiyetec/mapper/CouponGrantMapper.java` — `int atomicGrant(@Param("couponId") Long couponId, @Param("memberId") Long memberId)`（L43-52） |
| 调用方 | `com.meiyetec.controller.CouponController`（`/market/coupon/grant`）与 `MktCouponController`（`/mkt/coupon/*/grant`） |
| 涉及表 | `market_coupon`（券主表）/ `market_coupon_grant`（发放流水） |

---

## 2. 实测表结构（2026-11-10 22:33，`meiyetec` @ 127.0.0.1:3307）

### 2.1 `market_coupon` 现有列（无 `issued_count`、无 `deleted`）

```
id / store_id / name / type / rule_type / rule_value / min_amount / max_discount
total_count / per_limit / start_time / end_time / status / remark
created_at / updated_at / tenant_id
```

### 2.2 `market_coupon_grant` 现有列（无 `deleted`）

```
id / coupon_id / member_id / code / status / source / order_id / used_at / expire_time
created_at / updated_at / version / tenant_id
```

> `status` 语义：`1` 未使用 / `2` 已使用 / `3` 已作废（**无软删列**，作废用 `status = 3` 表达）。

---

## 3. 当前实现（方案 A，F150 已落地）

`atomicGrant` 现状（`CouponGrantMapper.xml` L140-171）：

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
              WHERE coupon_id = #{couponId} AND status != 3
          )
      )
      AND (
          per_limit IS NULL OR per_limit = 0
          OR (
              SELECT COUNT(*) FROM market_coupon_grant
              WHERE coupon_id = #{couponId} AND member_id = #{memberId} AND status != 3
          ) < per_limit
      )
    LIMIT 1
) sub ON c.id = sub.id
SET c.updated_at = NOW()
WHERE c.id = #{couponId}
```

方案 A 的取舍：

- ✅ **不改表结构**，零 DDL 风险
- ✅ D1 缺陷已修：`total_count IS NULL` 时旧写法 `NULL > COUNT(*)` 求值为 NULL → 条件恒不成立 → 误报「已达发放上限」；现按「NULL / 0 = 不限量」处理
- ⚠️ `SET c.updated_at = NOW()` 只刷新时间戳，**不携带计数**（表无计数列）
- ⚠️ 闸门与随后的 `INSERT INTO market_coupon_grant` **非同一原子操作**

---

## 4. 残留风险（方案 A 未覆盖）

| 编号 | 风险 | 说明 |
|---|---|---|
| R1 | **并发超发 TOCTOU** | 闸门 UPDATE（判定）与 INSERT（写入）分离；两个并发事务可同时通过 `total_count > COUNT(*)` 判定 → 超出 `total_count`。窗口极小（毫秒级），但**理论存在** |
| R2 | **无权威已发放数** | 「已发放数」只能 `COUNT(*) FROM market_coupon_grant WHERE coupon_id=? AND status != 3` 现算；该值随 `status` 变更（核销/作废）波动，**不能稳定代表累计发放量** |
| R3 | **COUNT(*) 全表扫** | 每次 atomicGrant 都对 `market_coupon_grant` 做子查询聚合；无 `(coupon_id, member_id)` 复合索引时（实测 `coupon_id`/`member_id` 各自有单列索引，**无复合索引**）→ 高频发放下有性能压力 |
| R4 | **D3 租户口径未校验**（本批**明确不修**） | `atomicGrant` 的 `market_coupon_grant` 计数子查询**未带 `tenant_id` 条件** → 跨租户计数会互相干扰；`market_coupon` 主表查询也未校验 `tenant_id`。按用户决策**留待批 2 实测后再定** |

---

## 5. 方案 B 候选内容（**待评估，本批不做**）

> 用户已明确：**方案 B 单独立 issue，后续批次评估**。以下仅为设计草案，未实施。

### 5.1 DDL

```sql
ALTER TABLE market_coupon
  ADD COLUMN issued_count INT NOT NULL DEFAULT 0 COMMENT '累计发放张数';
```

### 5.2 回填

```sql
UPDATE market_coupon c
SET c.issued_count = (
  SELECT COUNT(*) FROM market_coupon_grant g
  WHERE g.coupon_id = c.id AND g.status != 3
);
```

### 5.3 全链路同步（需逐点评估）

| # | 触点 | 现状 | 方案 B 要求 |
|---|---|---|---|
| 1 | `atomicGrant` SQL | `SET c.updated_at = NOW()` | 改 `SET c.issued_count = c.issued_count + 1, c.updated_at = NOW()`，并把配额判定从 `COUNT(*)` 改为 `c.issued_count < c.total_count` |
| 2 | 发放回滚 / 撤销 | 无计数回退 | 需定义「撤销发放」是否 `issued_count - 1` |
| 3 | 作废券（`status = 3`） | 计数时排除 | 明确作废是否回退计数（影响 `total_count` 语义） |
| 4 | 券作废 / 删除（`market_coupon.status`） | 无联动 | 明确是否同步 |
| 5 | 批量发放路径（`mkt_coupon_batch`） | 已有独立 `issued_count` 列（条件 UPDATE + `CONCURRENCY_CONFLICT`） | 两套计数口径是否统一（**当前是两套**：`mkt_coupon_batch.issued_count` vs `market_coupon` 无列） |
| 6 | 报表 / 看板 | 用 `COUNT(*)` 现算 | 是否改读 `issued_count` |
| 7 | 存量数据迁移 | — | 需 Flyway 迁移脚本（`db/migration/V*.sql`）+ MySQL 版本兼容（`ADD COLUMN IF NOT EXISTS` 需 8.0.29+） |

### 5.4 方案 B 的风险

- 计数与流水**双写不一致**风险（计数列 vs `market_coupon_grant` 实际行数）→ 需对账巡检任务
- 回填脚本在 `market_coupon_grant` 大表上全量 UPDATE，**需评估锁表时长**
- 破坏「单一事实来源」：目前发放事实**只在流水表**，方案 B 引入第二处可能不一致的副本

---

## 6. 与本批（F150）的关系

| 项 | 状态 |
|---|---|
| D1（`total_count` NULL / 0 语义） | ✅ **已修**（F150 批 1，`CouponGrantMapper.xml` + `CouponGrantMapper.java` javadoc） |
| D2（并发语义 / 原子自增） | ⏸ **本批不做**（即本 issue 的 R1，方案 B 候选项） |
| D3（租户口径） | ⏸ **本批不做**（即本 issue 的 R4，留待批 2 实测后定） |
| 表结构变更 | ⏸ **本批不做**（用户明确：不动任何表结构） |

---

## 7. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`，只读 SELECT / SHOW COLUMNS） |
| 应用 | `http://localhost:8080`（容器 `meiyetec-api`，镜像 `meiyetec/api:dev`） |
| 基线 commit | `d5bce5fd005bdadfbb7bc4458e5b2d09c432cc8a`（批 1 改动前 HEAD） |
| 环境基线 | `audit/baseline/env.md` |
| 证据目录 | `audit/evidence/F150-m09-invite-unseal/`（含 `raw/11-schema-market_coupon.txt`、`raw/12-schema-market_coupon_grant.txt`、`raw/13-market_coupon-rows.txt`） |
| 取证时间 | 2026-11-10 22:33 (UTC+8) |
