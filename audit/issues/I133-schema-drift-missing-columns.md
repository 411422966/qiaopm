# I133 — Schema 漂移：代码引用的列在数据库中不存在（R1–R5）

> **编号说明**：用户 2026-10-30 19:23 指定为 `I131-schema-drift-missing-columns.md`，但 `I131` 已被 `I131-packageitem-missing-deleted-and-createby.md` 占用（不覆盖既有登记），故顺延为 **I133**。
> **严重度**：P0（6 个 GET 端点 500，其中 3 个是跨店库存核心链路）
> **状态**：**VERIFIED**（2026-10-30 23:0x 修复后复测，13 个 R1–R8 端点全 code=200，全量复扫 79→74 且无回归；证据 `audit/evidence/F140-i118-ddl-landing/result.md` 附录 A）
> **关联遗留**：R3 首轮修复后 `/stock/trace/{id}` 仍 500，根因是 `stock_record` 缺 `dual_verify_status` 列（与 R3 原根因同族但不同列），已由 `backup/fix-r3-stock-record.sql` 补列解决；详见附录 A.1

---

## 1. 现象

后端 GET 端点全量扫描（861 个端点，2026-10-30 18:14–18:19）发现 6 个 `BadSqlGrammarException`，全部是**代码/SQL 引用的列在数据库中根本不存在**：

| ID | 端点 | 源码位置 | 运行时异常（实测） | 引用了不存在的列 | 表实际列 |
|---|---|---|---|---|---|
| R1 | `GET /material/cost/analysis`<br>`GET /material/cost/by-service/{id}` | `backend/src/main/java/com/meiyetec/mapper/ServiceMaterialCostMapper.java:15`（SELECT）、`:19`（GROUP BY）、`:27`、`:34` | `BadSqlGrammarException: Unknown column 'bs.price' in 'field list'` | `biz_service.price` | `unit_price`（NOT NULL）、`member_price`（NULL） |
| R2 | `GET /mapi/home` | `backend/src/main/java/com/meiyetec/service/MobileApiService.java:128` | `SQLSyntaxErrorException: Unknown column 'coupon_value' in 'field list'` | `mkt_coupon.coupon_value` | `discount_amount` DECIMAL(12,2)、`discount_rate` DECIMAL(3,2)、`min_consume` |
| R3 | `GET /stock/trace/{id}`<br>+ **所有** `StockInventoryMapper` 查询 | `backend/src/main/java/com/meiyetec/entity/StockInventory.java:21`（`productName`）、`:22`（`category`）、`:32`（`version`） | `BadSqlGrammarException: Unknown column 'product_name' in 'field list'`<br>`### The error may exist in com/meiyetec/mapper/StockInventoryMapper.java` | `stock_inventory.product_name` / `.category` / `.version` | `stock_inventory` 实际列：`id, tenant_id, store_id, product_id, batch_no, quantity, production_date, expiry_date, supplier, last_in_time, last_out_time, create_time, update_time, deleted` |
| R4 | `GET /wms/cross-store/inventory/aggregate` | `backend/src/main/java/com/meiyetec/service/impl/CrossStoreInventoryServiceImpl.java:56`（SELECT）、`:61`（JOIN）、`:77`（GROUP BY） | `SQLSyntaxErrorException: Unknown column 'i.warehouse_id' in 'field list'` | `stock_inventory.warehouse_id` | 无该列 |
| R5 | `GET /wms/cross/inventory/view` | `backend/src/main/java/com/meiyetec/service/CrossWarehouseService.java:59`（SELECT）、`:63`（JOIN/GROUP BY） | 同上 | 同上 | 同上 |

## 2. 根因

**这不是 I118 引入的。** 四份 DB 快照的列集合完全一致，说明这些列**从未存在过**：

| 快照 | 时间 | `biz_service.price` | `mkt_coupon.coupon_value` | `stock_inventory.warehouse_id` |
|---|---|---|---|---|
| `before-i129` | 2026-10-27 | 无 | 无 | 无 |
| `pre-i118` | 2026-10-30 00:39 | 无 | 无 | 无 |
| `pre-i118-batch2` | 2026-10-30 16:54 | 无 | 无 | 无 |
| 当前库 | 2026-10-30 18:12 | 无 | 无 | 无 |

三处独立的漂移源：

1. **R1 / R2：SQL 硬编码列名与建表脚本不一致** —— `biz_service` / `mkt_coupon` 的建表脚本从未使用过 `price` / `coupon_value` 这两个名字，代码里的 `@Select` 却是按旧命名写的。
2. **R3：实体字段超出表结构** —— `StockInventory` 实体比表多 3 个字段，且 `version` 被注释为「乐观锁版本号」但实体上**没有 `@Version` 注解**，表里也没有 `version` 列。
3. **R4 / R5：迁移脚本从未执行** —— `backend/src/main/resources/db/sql/migrate_r3_0_d_multi_warehouse.sql` 内含
   `ALTER TABLE stock_inventory ADD COLUMN warehouse_id BIGINT DEFAULT NULL COMMENT '仓库ID' AFTER store_id, ADD KEY idx_warehouse (warehouse_id);`，
   但该脚本**从未在目标库执行**（且脚本整体含 `DROP TABLE IF EXISTS wms_warehouse` 等破坏性语句，不宜整体重跑）。

## 3. 影响面

- R3 影响面最大：`StockInventory` 是 MyBatis-Plus 实体，**任何**基于 `StockInventoryMapper` 的查询都会 SELECT 出 `product_name` / `category` / `version` → 全炸（库存列表、库存统计、批次追溯、跨店聚合均受影响）。
- R4 / R5 是跨店库存核心链路：即使补了 `warehouse_id` 列，`LEFT JOIN wms_warehouse w ON i.warehouse_id = w.id` 在历史数据 `warehouse_id IS NULL` 时会丢掉仓库名（`wms_warehouse` 侧为 LEFT JOIN，不丢行，但 `warehouse_name` 为空）。
- R1 是耗材成本分析（毛利看板依赖）；R2 是移动端首页优惠券位。

## 4. 修复方向（用户 2026-10-30 19:23 已定方向：**改代码对齐现有库**，不随意改表结构）

| ID | 处置 | 说明 |
|---|---|---|
| R1 | 改 SQL：`bs.price` → `bs.unit_price` | `unit_price` 为 NOT NULL 的标准售价，`member_price` 为会员价；成本分析用标准售价口径 |
| R2 | 改 SQL：`coupon_value` → `discount_amount` | `discount_amount` 为减免金额，`discount_rate` 为折扣率；`coupon_value` 语义对应 `discount_amount` |
| R3 | 实体加 `@TableField(exist = false)`：`productName` / `category` / `version` | 这 3 个字段不落库；`productName` / `category` 若业务需要应从 `stock_product` JOIN 取，`version` 若要做乐观锁需另加 `@Version` + 建列（本次不做） |
| R4 / R5 | **在库中执行补列**：`stock_inventory.warehouse_id` | 脚本见 `backup/fix-r4-r5.sql`（判存 + 加列 + 加索引 + 回填主仓 + 回滚脚本） |

## 5. 证据

- 证据包：`audit/evidence/F140-i118-ddl-landing/`
  - `r1-r7-runtime-errors.txt` —— 6 条 `BadSqlGrammarException` 原始堆栈
  - `sweep-get2.json` / `500-classified.json` —— 端点级扫描与 traceId
  - `result.md` —— 环境绑定与结论
- DB 结构来源：`backup/meiyetec-pre-i118-batch2-20261030.sql`（`biz_service` 第 2441 行、`mkt_coupon` 第 10698 行、`stock_inventory` 第 14251 行）
- 环境：commit `cc95a00`，DB `127.0.0.1:3307/meiyetec`，env `audit/baseline/env.md`

## 6. 状态

- 缺陷：**VERIFIED**（2026-10-30 23:0x）
  - 定向复测：13 个 R1–R8 端点全部 `code=200`（`audit/evidence/F140-i118-ddl-landing/retest-r1-r8-after-fix.txt`）
  - 全量复扫：865 端点，`code=500` 由 79 降至 **74**，已消除 12 个，**新增 0**（7 个差异经双值复测确认为 `id=1548` 不存在的业务响应，属 C2 契约缺陷，非回归；`audit/evidence/F140-i118-ddl-landing/regress-candidates-check.txt`）
  - R3 追加处置：`stock_record.dual_verify_status` 补列（`backup/fix-r3-stock-record.sql`）
  - R8 追加处置：`P1RemainingService.java:488` SQL `crm_package.package_name` → `name`
- 证据包：`audit/evidence/F140-i118-ddl-landing/result.md` 附录 A
