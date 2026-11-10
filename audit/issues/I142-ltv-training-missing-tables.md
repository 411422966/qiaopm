# I142 — `LtvTrainingService` 引用 4 张不存在的表，LTV 训练 ETL 全链路必然失败

> **状态**：**修复中**（批 1 已完成，批 2-5 见 I152）
> **严重度**：P1 — 端点 500 / 功能完全不可用
> **发现日期**：2026-11-07（I140 修复过程中顺带发现）
> **登记人**：Cursor（I140 修复批次）
> **修复状态**：**批 1 已 VERIFIED**（2026-11-11）—— `LtvTrainingService` 内 8 处（A1-A8）已修，
> ETL 实测 `ltv_training_feature` 从 0 行 → 489 行。
> 剩余外溢引用见 **I152**；衍生口径问题见 **I150**（次数 vs 金额）、**I151**（未扣部分退款）。
> **拆分决策**：用户 2026-11-11 拍板 —— 本 issue 只承载 `LtvTrainingService` 的 8 处（批 1），
> 其余引用按 5 批次拆到 I152；口径类衍生问题拆到 I150 / I151。

> **原始登记内容（2026-11-07 批次）保留如下**：

---

## 0. 一句话描述

`LtvTrainingService.extractFeatures` 与 `computeLabels` 的原生 SQL 里引用了
**`crm_order` / `crm_order_item` / `crm_point_account` / `crm_member_coupon`** 四张表，
**这四张表在 `meiyetec` 库中都不存在** → `POST /intel/ltv/training/etl/run` 必然抛
`SQLException`（ERROR 1146）并因 `@Transactional(rollbackFor = Exception.class)` 整体回滚，
**LTV 特征抽取 / 训练 / 部署整条链路不可用**。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/LtvTrainingService.java` |
| 类 | `com.meiyetec.service.LtvTrainingService` |
| 端点 | `POST /intel/ltv/training/etl/run`（L68-69，`@Transactional(rollbackFor = Exception.class)`） |
| 缺陷方法 | `extractFeatures`（private，L115）、`computeLabels`（private，L275） |

### 1.1 缺表引用点逐条列出

| 行号 | SQL 片段 | 缺失表 | 所在语句 |
|---|---|---|---|
| **L154** | `LEFT JOIN crm_order o ON o.member_id = m.id AND o.status = 3` | `crm_order` | `extractFeatures` 第 1 段 INSERT…SELECT（基础 RFM） |
| **L166** | `FROM crm_point_account` | `crm_point_account` | 同上（积分余额子查询） |
| **L216** | `FROM crm_member_coupon` | `crm_member_coupon` | `extractFeatures` 第 4 段 UPDATE（优惠券使用数） |
| **L230** | `FROM crm_order o` | `crm_order` | `extractFeatures` 第 5 段 UPDATE（消费类别数） |
| **L231** | `JOIN crm_order_item oi ON oi.order_id = o.id` | `crm_order_item` | 同上 |
| **L286** | `FROM crm_order o` | `crm_order` | `computeLabels`（标签计算） |

### 1.2 关键点

- **L154 是第一段 INSERT…SELECT** → 训练 ETL 在**最开始的特征抽取**就炸，后面 4 段 UPDATE 根本轮不到。
- 错误类型是 `SQLSyntaxErrorException` / `BadSqlGrammarException`（ERROR 1146），
  **不是**空结果集 —— 与 I140 的"假成功"不同，本 issue 是**显式失败**（可被监控捕获）。
- 但 `runEtl` 的 `try` 块（L83）捕获后**是否吞异常**需另行核查（本次**未确认**，见 §5）。

---

## 2. 实测证据

### 2.1 四张表全部不存在

```
MISSING crm_order
MISSING crm_order_item
MISSING crm_point_account
MISSING crm_member_coupon
```

直接查询报 ERROR：

```
mysql> SELECT COUNT(*) FROM crm_order;
ERROR 1146 (42S02) at line 1: Table 'meiyetec.crm_order' doesn't exist
```

### 2.2 对照：`LtvTrainingService` 引用的**存在**的表

```
OK      crm_member
OK      crm_package_order
OK      biz_booking
OK      crm_review
OK      biz_service_item
OK      crm_wallet
OK      ltv_training_feature
```

即：**4 缺 7 存**。缺的 4 张全部落在 `crm_` 前缀的"订单 / 积分 / 券"域。

### 2.3 库中最接近的现有表（候选映射，**未取证，不作为结论**）

| 缺失表 | 库中近似表 |
|---|---|
| `crm_order` | `pos_order`、`pos_order_item`（POS 收银订单） |
| `crm_order_item` | `pos_order_item` |
| `crm_point_account` | `crm_wallet`（余额，非积分；另有 `market_point_record` / `mkt_points_log`） |
| `crm_member_coupon` | `market_coupon`、`market_coupon_grant`、`mkt_coupon` |

> ⚠️ 上表仅为"名字最接近"的候选，**字段级映射未取证**，实际语义可能完全不同（例如 `crm_order` 的
> `status = 3` / `pay_time` / `pay_amount` 三列在 `pos_order` 是否存在且语义一致，**未确认**）。

---

## 3. 影响面

| 受影响功能 | 说明 |
|---|---|
| `POST /intel/ltv/training/etl/run` | 特征抽取必失败 → 端点 500 或被 catch 吞成假成功（**未确认**，见 §5） |
| `POST /intel/ltv/training/train` | 依赖 `ltv_training_feature` 有数据；该表当前 **0 行**（实测 `SELECT COUNT(*) FROM ltv_training_feature` = 0）→ 无样本可训 |
| `POST /intel/ltv/training/model/{id}/deploy` | 无可用模型 → 不可用 |
| 前端 `/dev/ltv-training` | 对应页面无数据可展示 |

---

## 4. 修复建议（未实施）

1. **先决策数据源**：确认 LTV 训练应当以哪套订单为准 —— `pos_order`（POS 收银）还是新建 CRM 订单域。
   这是**产品/数据口径决策**，不是代码修复，需业务确认后才能动。
2. **决策后再改 SQL**：把 6 处引用（§1.1）改为真实存在的表与列，并逐条校验列语义
   （`status` 枚举含义、`pay_time` / `pay_amount` 对应关系、`deleted` 软删标记是否存在）。
3. **补缺失表的 DDL**（若确认需要独立 CRM 订单域）：走 `db/sql/` 迁移脚本 + Flyway 同步，
   遵循 EXT-19 / EXT-22 的 `ADD COLUMN IF NOT EXISTS`（MySQL ≥ 8.0.29）兼容约束。
4. **顺带核查 `runEtl` 的 catch 行为**：确认异常是被吞成"成功但 0 样本"（假成功）还是正常返回 500。

> ⚠️ 本 issue **未修改任何业务代码**，状态保持 `UNVERIFIED`。
> 修改 ETL SQL 会影响 LTV 模型训练结果，属高风险变更，需独立批次 + 前后对比证据。

---

## 5. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| `runEtl` 的 `catch`（L83 起）是否吞掉 SQL 异常并返回"成功" | ✅ **已确认（2026-11-11）** —— 走 `Result.error(BizCode.SYSTEM_ERROR, "ETL 失败: …")`，**显式失败，非假成功** |
| `crm_order` ↔ `pos_order` 的字段级映射 | ✅ **已确认（2026-11-11）** —— 见 §7.2 / §7.3 |
| `crm_member_coupon` 的等价表 | ✅ **已确认** —— `market_coupon_grant`（`status` 枚举吻合，无 `deleted` 列） |
| `crm_point_account` 的等价表（积分 vs 余额） | ✅ **已确认** —— 采用 `crm_member.points`（首选） |

---

## 6. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`） |
| 取证命令 | `mysql --default-character-set=utf8mb4`（只读 `SHOW TABLES` / `SELECT COUNT(*)`） |
| 取证时间 | 2026-11-07 |
| 证据文件 | `audit/evidence/F145-i140-ai-enhance-fix/after-02-sql-verification.txt` §[E] §[F] §[G] |
| commit hash | 见 commit 1 `fix(ltv): 修复 LtvTrainingService 8 处表名与列名错误（I142 批 1）` |

---

## 7. 批 1 修复记录（2026-11-11）

### 7.1 修复前的只读调查结论（用户审核后拍板）

I142 原始登记范围**严重不足**，实际缺陷分三层：

| 层 | 内容 | 原 issue 是否登记 |
|---|---|---|
| **L1 缺表** | 4 张表（`crm_order` / `crm_order_item` / `crm_point_account` / `crm_member_coupon`）确实不存在 | ✅ 已登记 |
| **L2 列名错误** | 6 处列名错误（`crm_member.created_at` / `crm_review.created_at` / `crm_package_order.remaining_value` / 积分表 `deleted` / 券表 `deleted` / `biz_service_item` 血缘错表） | ❌ **完全未登记** |
| **L3 影响面外溢** | 另有 13 个文件 21 处 `crm_order` 引用 | ❌ **完全未登记** |

**方案 B（补建 4 张表）已排除**，证据：

- `git log --all -S` 最早只命中 `a414f95 recover: 从工作区重建`，**无任何 DDL 历史**
- 全仓 `*.sql` grep 4 表名 → **0 命中**
- `DcepService.java:759` 注释：「之前：直接 `jdbc.update("UPDATE crm_order ...")`（表名错误：实际是 `pos_order`）」
- `LtvSeedController.java:142-160` 造订单代码**已经写 `pos_order`**

→ **4 张表是「废弃/未落地设计」，不是「漏建」**。

### 7.2 已实施改动（A1-A8，`LtvTrainingService` 内 8 处）

| 编号 | 行号（原） | 改前 | 改后 |
|---|---|---|---|
| **A1** | L154 | `LEFT JOIN crm_order o ON ... AND o.status = 3` | `LEFT JOIN pos_order o ON ... AND o.status = 2`（+ `pay_amount`→`final_amount`） |
| **A2** | L143 / L170 | `m.created_at` | `m.create_time`（`crm_member` 真实列名） |
| **A3** | L159 | `SUM(remaining_value) AS total_balance` | `SUM(remaining_times) AS total_remaining_times` |
| **A4** | L166 | `LEFT JOIN (SELECT ... FROM crm_point_account) pt` | 删子查询，改用 `COALESCE(m.points, 0)`（靠 `m.id` 函数依赖，不进 GROUP BY） |
| **A5** | L200 | `crm_review.created_at` | `crm_review.create_time` |
| **A6** | L216-217 | `FROM crm_member_coupon WHERE deleted = 0 AND status = 2` | `FROM market_coupon_grant WHERE status = 2`（无 `deleted` 列） |
| **A7** | L230-231 | `crm_order` + `crm_order_item` + `biz_service_item` + `status = 3` | `pos_order` + `pos_order_item` + `biz_service` + `status = 2`（+ 补 `oi.deleted = 0` / `s.deleted = 0`） |
| **A8** | L286 | `FROM crm_order o ... status = 3` | `FROM pos_order o ... status = 2`（+ `pay_amount`→`final_amount`） |

**`GROUP BY` 调整**：`m.created_at` → `m.create_time`；`pt.point_balance` 移除、`m.points` 加入
（`m.id` 为主键 → 函数依赖，MySQL 8 `ONLY_FULL_GROUP_BY` 接受，实测无报错）。

**`biz_service` 的选择依据**：`PosController.addItemInternal` 用 `BizServiceMapper` 写入
`pos_order_item.service_id` → 该列实指 `biz_service.id`（非 `biz_service_item.id`），A7 JOIN 目标确认无误。

### 7.3 验证结果（2026-11-11 实测）

**触发入口**：`POST /intel/ltv/training/etl/run`（`LtvTrainingService` 本身即 `@RestController`）

```json
{
  "code": 200,
  "message": "ETL 完成",
  "data": {
    "featureDate": "2026-11-10",
    "featuresExtracted": 489,
    "labelsComputed": 0,
    "coldStartDropped": 0,
    "trainingReady": 0,
    "durationMs": 3375
  }
}
```

```sql
SELECT COUNT(*) FROM ltv_training_feature;   -- 489（修复前 0）
SELECT COUNT(DISTINCT member_id) ...;         -- 489（tenant 1 全部在册会员）
```

抽样（`monetary_total > 0` 排序）：

| member_id | recency_days | frequency_total | monetary_total | aov | avg_interval_days | tenure_days | point_balance |
|---|---|---|---|---|---|---|---|
| 1414 | 102 | 8 | 347083.00 | 43385.38 | 22 | 69 | 0 |
| 1489 | 95 | 7 | 304761.00 | 43537.29 | 25 | 69 | 0 |
| 1418 | 104 | 7 | 291757.00 | 41679.57 | 24 | 69 | 0 |

**NULL 检查**：`recency_days` / `monetary_total` / `point_balance` **489/489 均非 NULL**；
`recency_days` 范围 72~9999（9999 = 180 天窗口内无消费，符合 `COALESCE` 兜底语义）。

**遗留观察**（不阻塞本批，已在 I150 / I151 / I152 登记）：

| 现象 | 原因 | 归属 |
|---|---|---|
| `service_category_count` 全部为 0 | `biz_service.category_id` **10 行全部为 NULL** → `COUNT(DISTINCT s.category_id)` 恒为 0 | **I152**（待补数据治理） |
| `review_count` 合计仅 3 | `crm_review` 仅 9 条已发布评价，其中 3 条 `member_id` 能匹配到在册会员 | 数据稀疏（Q6 接受） |
| `next_90d_revenue` 全为 0 | 标签窗口 `featureDate+1 ~ +90` 落在**未来**，无订单 | 语义正常（冷启动） |
| `total_package_balance` 语义为「次数」 | 原始设计写的 `remaining_value`（金额）列不存在 | **I150** |
| GMV 未扣 `refunded_amount` | 部分退款订单按全额计入 | **I151** |

### 7.4 批 1 的状态

| 项 | 值 |
|---|---|
| I142 批 1 状态 | **VERIFIED**（ETL 端到端实测通过） |
| 剩余批次 | **I152** 批 2 / 批 3 / 批 4 / 批 5 |
| commit hash | 见 commit 1 `fix(ltv): 修复 LtvTrainingService 8 处表名与列名错误（I142 批 1）` |
