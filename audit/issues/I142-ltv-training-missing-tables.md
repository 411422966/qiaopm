# I142 — `LtvTrainingService` 引用 4 张不存在的表，LTV 训练 ETL 全链路必然失败

> **状态**：Open
> **严重度**：P1 — 端点 500 / 功能完全不可用
> **发现日期**：2026-11-07（I140 修复过程中顺带发现）
> **登记人**：Cursor（I140 修复批次）
> **修复状态**：**UNVERIFIED**（本文件仅登记缺陷，未修改业务代码）

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
| `runEtl` 的 `catch`（L83 起）是否吞掉 SQL 异常并返回"成功" | **未确认** —— 未实际调用该端点取证 |
| `crm_order` ↔ `pos_order` 的字段级映射 | **未确认** |
| `crm_member_coupon` 的等价表 | **未确认** |
| `crm_point_account` 的等价表（积分 vs 余额） | **未确认** |

---

## 6. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`） |
| 取证命令 | `mysql --default-character-set=utf8mb4`（只读 `SHOW TABLES` / `SELECT COUNT(*)`） |
| 取证时间 | 2026-11-07 |
| 证据文件 | `audit/evidence/F145-i140-ai-enhance-fix/after-02-sql-verification.txt` §[E] §[F] §[G] |
| commit hash | 待补（本 issue 未提交修复） |
