# I175 — `crm_ltv_snapshot` 缺 `UNIQUE (tenant_id, member_id, snapshot_date)`，加约束前须先清 236 组存量重复 + 写入者幂等落地

> **状态**：Open（**DDL 未实施** —— 前置条件未满足）
> **严重度**：P2 —— 唯一约束是「从根上杜绝重复写入」的兜底，但**必须最后做**
> **发现日期**：2026-11-14（I170 部分修复批次的调查中确立）
> **登记人**：Cursor（I170 部分修复批次）
> **关联**：I170（父 issue）、I172（**硬前置**：约束等于强制「一会员一日一行」= 强制谁后写谁覆盖）、I174（**硬前置**：存量重复必须先清）、I171（CHURN 侧同构项 —— `crm_churn_prediction` 同样缺该约束）

---

## 0. 一句话描述

`crm_ltv_snapshot` **没有任何唯一约束**（只有 `PRIMARY KEY(id)` + 两个普通索引），
因此「同一会员同一快照日多行」在数据库层面**没有任何阻断**——
唯一约束是兜底防线，但它**必须排在最后**执行，否则 `ALTER TABLE` 会直接失败。

---

## 1. 现状（本地实测，租户 1，2026-11-14）

### 1.1 现有 DDL

```sql
CREATE TABLE `crm_ltv_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `member_id` bigint NOT NULL,
  `snapshot_date` date NOT NULL,
  ... ,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_brand_date` (`brand_id`,`snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> 📌 **两个普通索引都不含 `tenant_id`**，也没有任何 `UNIQUE` 约束。
> `SHOW CREATE TABLE` 于 2026-11-14 23:30 实测确认。

### 1.2 存量重复统计

| 口径 | 组数（重复的 `(member_id, snapshot_date)` 组合） | 备注 |
|---|---|---|
| 修复前（本 issue 立项时） | **237** | 当日 232 + 历史 5 |
| **当前（写入者 C 单会员幂等落地后）** | **236** | 当日 **231** + 历史 **5** |
| 其中：当日重复组 | 231 | 同一会员有 `batch` + `compute-job` 两行 |
| 其中：历史重复组 | 5 | 全部来自 `online` 写入者，详见 I174 |

> ⚠️ **当日 231 组重复无法靠「清理存量」解决** —— 它们是 A（`batch`）与 B（`compute-job`）**两个写入者同时存活**的产物，
> 每日运行即重新产生。**唯一约束的前提是 I172 先拍板 + A3 落地**，否则约束一旦加上，
> B 写入时就会撞唯一键报错。

---

## 2. 加约束的**四个前置条件**（按依赖顺序）

| 序 | 前置条件 | 当前状态 | 阻塞项 |
|---|---|---|---|
| ① | **写入者幂等落地** | ✅ **已完成**（本批） | — |
| ② | **存量重复清零** | ⏳ **未做**（历史 5 组 = I174；当日 231 组需 ③） | I174 |
| ③ | **写入者收敛 / A3 落地**（决定「一天只允许一行」由谁写） | ⏳ **未做**，等业务拍板 | **I172** |
| ④ | **生产库只读复核** | ⏳ 未做（本地不可代表生产） | — |

> 🔴 **顺序不能颠倒**：③ 未落地就加约束 → B（`compute-job`）跑批时**撞唯一键抛 `DuplicateKeyException`**，
> 整批失败（`@Transactional(rollbackFor = Exception.class)` → 全量回滚），
> 比「允许重复」严重得多。

---

## 3. 建议 DDL（**待前置条件全部满足后**执行）

```sql
-- 步骤 0：执行前先在 information_schema 复核是否已存在该唯一键
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) cols, NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_ltv_snapshot'
GROUP BY INDEX_NAME, NON_UNIQUE;

-- 步骤 1：清理存量重复（见 I174 §4，含备份步骤）

-- 步骤 2：加唯一约束
ALTER TABLE crm_ltv_snapshot
  ADD UNIQUE KEY uk_tenant_member_date (tenant_id, member_id, snapshot_date);
```

### 3.1 执行注意

| 项 | 说明 |
|---|---|
| **MySQL 版本** | `ADD UNIQUE KEY` 是标准语法，**无版本门槛**（不同于 `ADD COLUMN IF NOT EXISTS` 的 8.0.29+ 要求，见 I130）。但若改用 `ADD CONSTRAINT ... IF NOT EXISTS` 变体则受版本限制 —— **不要用** |
| **无 `IF NOT EXISTS`** | MySQL **不支持** `ADD UNIQUE KEY IF NOT EXISTS` → 重复执行会报 `Duplicate key name`，须先查 `information_schema`（步骤 0） |
| **执行耗时** | `crm_ltv_snapshot` 本地 4594 行，微秒级；**生产库行数未知**，可能需要在线 DDL 窗口 |
| **锁影响** | `ALTER TABLE ... ADD UNIQUE KEY` 走 `ALGORITHM=INPLACE, LOCK=NONE`（8.0 支持加唯一索引），但仍需评估生产窗口 |
| **回滚** | `ALTER TABLE crm_ltv_snapshot DROP INDEX uk_tenant_member_date;` |
| **与 `brand_id` 索引的关系** | 现有 `idx_brand_date (brand_id, snapshot_date)` 与新唯一键**功能不同**（前者按品牌筛，后者按租户+会员唯一），**两者都保留** |
| **副作用** | 加约束后，任何写入者若在同一日对同一会员写第二行都会**抛异常而非静默重复** → 需要同步确认三个写入者的错误处理（当前 `LtvChurnController.computeLtv` 是 `@Transactional`，异常会回滚整批） |

---

## 4. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 生产库 `crm_ltv_snapshot` 的行数与重复组数 | **未确认** —— 本地仅 4594 行；生产库不可访问 |
| 生产库是否已存在同名唯一键 | **未确认** —— 须在生产库执行 §3 步骤 0 的查询确认 |
| 生产库加约束所需的维护窗口 | **未确认** —— 需与运维确认 |
| `crm_ltv_snapshot` 是否有其他表的**逻辑外键引用** | **未确认** —— 本 issue 未做全仓外键扫描 |

---

## 5. 建议处理顺序（与 I172 / I174 合并排期）

1. **I172 业务拍板** → 确定权威口径与收敛方案（决定谁写、怎么写）；
2. 落地 A3 / 写入者收敛 → 当日重复组不再产生；
3. **I174 清理历史 5 组** → 历史重复组清零；
4. 生产库只读复核重复组数（§3 步骤 0 + 等价于 §1.2 的统计）；
5. **执行 `ADD UNIQUE KEY`** → 兜底防线生效；
6. 补回归测试：并发/重复触发同一会员当日写入时，断言「不产生重复行」且「写入失败是可预期的业务错误」。

> ⚠️ **本批明确不做**（用户指示「不加唯一约束（I175，需先清存量）」）。
