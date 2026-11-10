# I153 — `biz_service.category_id` 全表为 NULL：服务分类未回填，5 个分类无法关联

> **状态**：Open
> **严重度**：P2 — 不报错、不崩，但导致分类维度特征恒为 0，分类统计报表恒空
> **发现日期**：2026-11-11（I142 批 1 ETL 修复验证过程中发现，F152 证据 §5 遗留观察）
> **登记人**：Cursor（I142 批 1 — LTV 训练 ETL 8 处表名/列名修复）
> **决策**：用户 2026-11-11 拍板 —— **数据回填需业务确认「哪 5 个分类对应哪些服务」，本 issue 只登记缺口，不改数据**
> **关联**：I142（`LtvTrainingService` 表名/列名修复）、I150（`total_package_balance` 口径）、I151（GMV 未扣部分退款）、I152（`crm_order` 引用外溢）

---

## 0. 一句话描述

`biz_service`（美业服务主表）**10 行的 `category_id` 全部为 NULL**，
而 `biz_service_category`（服务分类表）**已有 5 个启用分类**。
两者之间**没有任何外键关联**——服务挂在哪个分类下，从未被业务确认过。

直接后果：LTV 特征 `service_category_count` **在全部 489 条训练样本上恒为 0**，
所有按服务类别切分的统计报表口径同样落空。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| 表 | `meiyetec.biz_service` — 列 `category_id bigint NULL` |
| 参照表 | `meiyetec.biz_service_category` — 5 行，`deleted=0`、`status=1` |
| ETL 消费点 | `backend/src/main/java/com/meiyetec/service/LtvTrainingService.java` — `extractFeatures` 内 `biz_service` JOIN 子查询 |
| 落库列 | `ltv_training_feature.service_category_count`（`int NULL`） |
| 实体 | `backend/src/main/java/com/meiyetec/entity/LtvTrainingFeature.java` — `serviceCategoryCount` |
| 特征清单 | `LtvTrainingService.FEATURE_COLUMNS`（L56-60）—— `service_category_count` 是模型第 9 个输入特征 |

### 1.1 分类表已就绪，服务表未挂接

`biz_service_category` 5 行（`tenant_id=1`、`store_id=1`、`deleted=0`、`status=1`）：

| id | category_code | category_name | sort_order |
|---|---|---|---|
| 1 | `FACE` | 面部护理 | 1 |
| 2 | `BODY` | 美体塑形 | 2 |
| 3 | `TATTOO` | 纹绣美瞳 | 3 |
| 4 | `HEAD` | 头部SPA | 4 |
| 5 | `OTHER` | 其他 | 5 |

`biz_service` 10 行，`category_id` **全 NULL**：

| id | service_code | service_name | category_id | unit_price |
|---|---|---|---|---|
| 1 | `SV0001` | 基础面部护理 | **NULL** | 280.00 |
| 2 | `SV0002` | 深层补水护理 | **NULL** | 480.00 |
| 3 | `SV0003` | 肩颈舒缓按摩 | **NULL** | 180.00 |
| 4 | `SV0004` | 全身精油SPA | **NULL** | 880.00 |
| 5 | `SV0005` | 美睫嫁接 | **NULL** | 380.00 |
| 6 | `SV0001` | 基础面部护理 | **NULL** | 280.00 |
| 7 | `SV0002` | 深层补水护理 | **NULL** | 480.00 |
| 8 | `SV0003` | 肩颈舒缓按摩 | **NULL** | 180.00 |
| 9 | `SV0004` | 全身精油SPA | **NULL** | 880.00 |
| 10 | `SV0005` | 美睫嫁接 | **NULL** | 380.00 |

> ⚠️ **顺带发现（未单独登记）**：`service_code` `SV0001`~`SV0005` **各出现 2 次**（id 1-5 与 id 6-10 完全重复，同价同名）。
> 该重复是否有意（多门店各存一份）**未确认** —— `biz_service.store_id` 10 行全为 NULL，
> 无法区分归属门店。数据回填时需一并澄清。

### 1.2 数据流

```
biz_service.category_id (全 NULL)
        ↓ COUNT(DISTINCT s.category_id)   ← LtvTrainingService.extractFeatures
   service_category_count = 0
        ↓ FEATURE_COLUMNS[8]
   LTV 回归模型输入特征（恒为常量 0 → 梯度下降中权重恒为 0）
```

`service_category_count` 在全部样本上为**常量**，标准化后 `std < 1e-6` → 被置为 `1.0`
（`computeStats` 的防除零分支），该特征**对模型输出没有任何贡献**，但占据了一个特征位。

---

## 2. 实测证据

### 2.1 回填前基线（2026-11-11 实测）

```sql
-- 分类表已就绪
SELECT COUNT(*) FROM biz_service_category WHERE deleted = 0 AND status = 1;
-- 结果： 5

-- 服务表 category_id 全 NULL
SELECT COUNT(*) FROM biz_service WHERE category_id IS NOT NULL;
-- 结果： 0   （总行数 10）

-- ETL 落库后特征恒为 0
SELECT SUM(service_category_count), COUNT(DISTINCT service_category_count)
FROM ltv_training_feature;
-- 结果： 0 | 1   （489 条样本，值域 {0}）
```

### 2.2 与 F152 证据一致

`audit/evidence/F152-ltv-training-etl-fix/README.md` §4.4 聚合核对表已记录：

| 列 | 合计 | 说明 |
|---|---|---|
| `service_category_count` | **0** | ⚠️ `biz_service.category_id` 全 NULL → 恒为 0（见 §5 遗留观察） |

F152 §5 遗留观察表已把它归属到本 issue。

---

## 3. 影响面

| 受影响项 | 说明 | 状态 |
|---|---|---|
| **LTV 类别特征** | `service_category_count` 恒 0 → LTV 回归模型无法区分「偏好面部护理 / 偏好美体塑形」的会员 | ✅ 已确认 |
| **LTV `preferred_category_id`** | `ltv_training_feature.preferred_category_id` 同样依赖 `biz_service.category_id`，预期同样无法回填 | **未确认**（ETL 实测该列亦全为 NULL，但未逐条核对 SQL 归属） |
| **热销榜类别统计** | 按类别聚合服务销量/GMV 时类别维度塌陷为单一桶 | **未确认**（未逐页核对前端热销榜实现） |
| **按类别报表** | 任何 `GROUP BY category` 的报表输出恒为「未分类」 | **未确认**（未逐页核对报表模块） |
| **ETL 的 `pos_order_item JOIN biz_service` 聚合** | 该子查询按 `service_id` 关联，分类字段为 NULL 时只影响 `category_id` 投影，不影响订单金额 | ✅ 已确认（金额链路正常，见 F152 §4.4） |

> 说明：「热销榜类别统计」与「按类别报表」两条为**用户 2026-11-11 明确点名的潜在影响面**，
> 但本次调查为**只读静态分析 + 只读 SQL**，未逐页核对前端实现，故标「未确认」。

---

## 4. 待业务确认事项（**阻塞数据回填**）

| # | 问题 | 为什么必须业务确认 |
|---|---|---|
| Q1 | `SV0001 基础面部护理` → 哪个分类？ | 名称含「面部」但类别归属需业务定 |
| Q2 | `SV0002 深层补水护理` → 哪个分类？ | 同上 |
| Q3 | `SV0003 肩颈舒缓按摩` → 哪个分类？ | 按 `HEAD`（头部SPA）还是 `BODY`（美体塑形）存在歧义 |
| Q4 | `SV0004 全身精油SPA` → 哪个分类？ | 按 `BODY` 还是 `OTHER` 需确认 |
| Q5 | `SV0005 美睫嫁接` → 哪个分类？ | 按 `TATTOO`（纹绣美瞳）最接近，但需确认 |
| Q6 | 5 个分类是否**全部**要覆盖？ | 若存在未被任何服务引用的分类，需确认是否保留 |
| Q7 | `id 6-10` 与 `id 1-5` 的重复服务如何处理？ | 见 §1.1 —— 回填时是两条都填，还是先去重 |

> ⚠️ **`OTHER`（其他）不是"随便填"的兜底**。如果一个分类没有对应服务，
> 正确的做法是**让该分类空置**，而不是把不属于它的服务塞进 `OTHER` ——
> 否则 `OTHER` 会变成"未归类垃圾桶"，分类维度仍然失真。

---

## 5. 建议修复路径（**本轮未实施**）

### 5.1 数据回填（需先解 Q1-Q7）

```sql
-- 示例：仅在业务确认映射后执行，且必须先做存在性检查
UPDATE biz_service SET category_id = 1 WHERE tenant_id = 1 AND service_code = 'SV0001' AND category_id IS NULL;
-- ...
```

- 回填前**必须**先 `SELECT id, service_code, service_name, category_id FROM biz_service WHERE tenant_id = 1` 留底
- 回填后**必须**重跑 `POST /intel/ltv/training/etl/run`（ETL 会重算 `service_category_count`）
- 重跑后校验：`SELECT SUM(service_category_count) FROM ltv_training_feature` 应 > 0

### 5.2 数据完整性护栏（建议）

- 在 `biz_service` 上加 `CHECK` 或应用层校验：**新建/编辑服务时 `category_id` 必填**
  （当前 `biz_service.category_id` 可空 → 新增服务仍会漏挂分类，缺口会重新累积）
- 在服务编辑页的前端表单中**增加分类下拉框**（当前若表单无该字段，任何后续操作都填不上）

> ⚠️ 「前端表单是否有分类下拉框」**未确认**（未逐页核对服务管理页）。

### 5.3 与 I142 批 1 的关系

本 issue **不是**代码缺陷 —— ETL SQL 本身正确（`COUNT(DISTINCT s.category_id)`），
是**源数据缺失**导致结果为 0。因此**不需要改 `LtvTrainingService`**。

---

## 6. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| `ltv_training_feature.preferred_category_id` 的 ETL 赋值来源与当前值 | **未确认** |
| 前端「热销榜」是否按类别聚合 | **未确认**（未逐页核对） |
| 前端「按类别报表」的实现位置与 SQL | **未确认**（未逐页核对） |
| 服务管理页是否有 `category_id` 输入入口 | **未确认**（未逐页核对） |
| `biz_service` id 6-10 的重复是「多门店各一份」还是「脏数据」 | **未确认** |
| `biz_service_category.store_id = 1` 与 `biz_service.store_id = NULL` 的门店语义冲突 | **未确认** |

---

## 7. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`，MySQL 8.0.46） |
| 取证命令 | `mysql --default-character-set=utf8mb4`（**只读**：`SELECT` / `SHOW COLUMNS` / `information_schema`） |
| 取证时间 | 2026-11-11 |
| 关联 issue | I142（批 1 修复）、I150、I151、I152 |
| 关联证据 | `audit/evidence/F152-ltv-training-etl-fix/README.md` §4.4 / §5 |
