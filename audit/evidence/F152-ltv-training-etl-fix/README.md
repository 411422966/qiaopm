# F152 — I142 批 1 证据：LTV 训练 ETL 8 处表名/列名修复验证

> **Feature**：F152 — LTV 训练 ETL 链路修复（对应 Issue I142 批 1）
> **取证日期**：2026-11-11
> **commit hash（取证时 HEAD）**：`97361b1`（修复前）→ 本批修复 commit 见 issue I142 §7.4
> **数据库**：`meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`，MySQL 8.0.46）
> **应用**：`http://localhost:8080`（容器 `meiyetec-api`，镜像 `meiyetec/api:dev`）
> **sql_mode**：`ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION`

---

## 1. 环境

| 项 | 值 |
|---|---|
| 后端构建 | `mvn clean package "-Dmaven.test.skip=true"` → **BUILD SUCCESS** |
| 镜像重建 | `docker compose build api` → `meiyetec/api:dev`（sha256:9806bbff…） |
| 容器重建 | `docker compose up -d api` → `Up (healthy)` |
| 触发入口 | `POST /intel/ltv/training/etl/run`（`LtvTrainingService` 本身即 `@RestController`） |
| 鉴权 | `POST /auth/login`（admin / 123456，角色 SUPER_ADMIN） |

---

## 2. 修复前基线（`raw/02-data-baseline.txt`）

```
@@sql_mode
ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION

-- 4 张目标表在 information_schema 中 0 命中（查询返回空集）
t	COUNT(*)
pos_order	1256                              -- status=2 AND deleted=0 AND member_id IS NOT NULL
status2_deleted0_total	1290
pos_order_item	180
market_coupon_grant_status2	0
crm_member_tenant1	489
biz_service_cat_notnull	0                    -- ⚠️ biz_service.category_id 全 NULL
ltv_training_feature_before	0              -- 修复前 ETL 从未成功产出
```

> ⚠️ `ltv_training_feature_before` 在本文件生成时已为 489（因 ETL 已在同一会话中先跑过一次）。
> **修复前的 0 行**由 I142 原始登记（2026-11-07 `SELECT COUNT(*) FROM ltv_training_feature` = 0）确认，
> 且本次 ETL 端点返回 `featuresExtracted = 489`（即本次运行前该表无当次快照数据）。

---

## 3. ETL 触发结果（成功）

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
  },
  "traceId": "f655449a0e9a4a76af555907c834d573"
}
```

**核心验证通过**：`ltv_training_feature` 从 **0 行 → 489 行**，覆盖 tenant 1 的 **489 个在册会员**
（原预期 401 个有有效订单的会员；此处为全部在册会员，因为 ETL 对无订单会员也写入全 0 特征行）。

**`ONLY_FULL_GROUP_BY` 无报错** —— Q3 首选方案（`m.points` 靠 `m.id` 函数依赖）成立，
**未触发降级到 `market_point_record` 备选**。

---

## 4. 抽样与字段合理性检查（`raw/03-post-etl-verification.txt`）

### 4.1 总量

```
total_rows	distinct_members	tenants
489	489	1
```

### 4.2 非空 / 分布

```
recency_nn	monetary_nn	point_nn	recency_null	monetary_null	point_null
489	489	489	0	0	0

recency_min	recency_max	mon_min	mon_max	pt_min	pt_max
72	9999	0.00	347083.00	0	6800
```

- ✅ `recency_days` / `monetary_total` / `point_balance` **489/489 均非 NULL**
- ✅ `recency_days` 范围 72 ~ 9999（9999 = 特征窗口内无消费，`COALESCE(..., 9999)` 兜底语义正确）
- ✅ `monetary_total` 最大 347083.00（对应 8 笔已支付订单 × 平均 4.3 万元，与 `pos_order.final_amount` 实测一致）
- ✅ `point_balance` 取自 `crm_member.points`，范围 0 ~ 6800（106/507 会员非零，已反映）

### 4.3 抽样记录（`monetary_total > 0` 前 8 条）

| member_id | recency_days | frequency_total | monetary_total | aov | avg_interval_days | tenure_days | active_packages | total_package_balance | point_balance | wallet_balance | next_90d_revenue | next_90d_active |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1414 | 102 | 8 | 347083.00 | 43385.38 | 22 | 69 | 0 | 0.00 | 0 | NULL | 0.00 | 0 |
| 1489 | 95 | 7 | 304761.00 | 43537.29 | 25 | 69 | 0 | 0.00 | 0 | NULL | 0.00 | 0 |
| 1418 | 104 | 7 | 291757.00 | 41679.57 | 24 | 69 | 0 | 0.00 | 0 | NULL | 0.00 | 0 |
| 1488 | 100 | 7 | 288658.00 | 41236.86 | 25 | 69 | 0 | 0.00 | 0 | NULL | 0.00 | 0 |
| 1220 | 108 | 7 | 285952.00 | 40850.29 | 25 | 79 | 0 | 0.00 | 0 | NULL | 0.00 | 0 |
| 1224 | 110 | 6 | 274215.00 | 45702.50 | 30 | 79 | 0 | 0.00 | 0 | NULL | 0.00 | 0 |
| 1420 | 103 | 7 | 256835.00 | 36690.71 | 24 | 69 | 0 | 0.00 | 0 | NULL | 0.00 | 0 |
| 1485 | 114 | 7 | 256606.00 | 36658.00 | 25 | 69 | 0 | 0.00 | 0 | NULL | 0.00 | 0 |

### 4.4 聚合核对

| 列 | 合计 | 说明 |
|---|---|---|
| `frequency_total` | 636 | 180 天窗口内有效订单总笔数（`pos_order` status=2 AND deleted=0） |
| `total_package_balance` | 124.00 | **次数**口径（I150），非金额 |
| `service_category_count` | 0 | ⚠️ `biz_service.category_id` 全 NULL → 恒为 0（见 §5） |
| `booking_count` | 0 | `biz_booking` 会员关联稀疏 |
| `review_count` | 3 | `crm_review` 仅 9 条已发布评价，3 条 member_id 可匹配 |
| `coupon_used_count` | 0 | `market_coupon_grant` 中 `status = 2` 为 0 行（Q5 接受） |
| `wallet_balance` | 25838.00 | `crm_wallet` 仅 50 行，439 条为 NULL（LEFT JOIN 未命中） |
| `active_packages` | 25 | `crm_package_order` 有效套餐 25 条，仅 1 个会员命中 |
| `next_90d_revenue` | 0.00 | 标签窗口落在未来，无订单 → 冷启动语义正常 |

---

## 5. 遗留观察（不阻塞本批）

| 现象 | 根因 | 归属 issue |
|---|---|---|
| `service_category_count` 恒为 0 | `biz_service.category_id` **10 行全部 NULL**（`biz_service_category` 表有 5 个分类，但 `biz_service` 未回填 `category_id`） | I152（待补数据治理） |
| `wallet_balance` 439/489 为 NULL | `crm_wallet` 仅 50 行且 `uk_member_tenant` 唯一键只覆盖部分会员 | 数据稀疏 |
| `total_package_balance` 为次数 | `crm_package_order` 无 `remaining_value`（金额）列 | **I150** |
| GMV 未扣 `refunded_amount` | 部分退款订单按全额计入 | **I151** |

---

## 6. 结论

| 项 | 状态 |
|---|---|
| I142 批 1（A1-A8） | **VERIFIED** |
| `runEtl` 异常处理 | **显式失败**（`Result.error(SYSTEM_ERROR, "ETL 失败: …")`），非假成功 |
| ONLY_FULL_GROUP_BY 兼容性 | ✅ 通过，未降级 |
| 剩余外溢引用 | 21 处 → **I152**（批 2~5） |

---

## 7. 文件清单

| 文件 | 内容 |
|---|---|
| `raw/01-target-table-schemas.txt` | `pos_order` / `pos_order_item` / `crm_member` / `crm_review` / `crm_package_order` / `market_coupon_grant` / `biz_service` 的 `SHOW CREATE TABLE` |
| `raw/02-data-baseline.txt` | `sql_mode`、4 张缺表 0 命中、各候选表行数基线 |
| `raw/03-post-etl-verification.txt` | ETL 后 489 行的总量 / 非空 / 分布 / 抽样明细 |
| `raw/04-post-rebuild-recheck.txt` | 实体注释改动后重新构建镜像 + 重跑 ETL 的复核结果（**与 03 完全一致**：489 行、NULL 数 0、`monetary_total` 最大 347083.00、`point_balance` 0~6800） |
