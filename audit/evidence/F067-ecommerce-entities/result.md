# F067 Ecommerce 实体（Order + Product + SyncLog）— Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification

---

## 1. 实证结论

**状态**：**VERIFIED**（plan §5.2 预测的反转 — 3 张表全存在）

| 表名 | 状态 | 字段数 | 行数 |
|---|---|---|---|
| **ecommerce_order** | ✅ 存在 | 20 | 0 |
| **ecommerce_product** | ✅ 存在 | 14 | 0 |
| **ecommerce_sync_log** | ✅ 存在 | 11 | 0 |

## 2. 关键 SQL 证据

```sql
DESCRIBE ecommerce_order;     -- 20 字段，bigint id 主键 + 各类订单字段
DESCRIBE ecommerce_product;   -- 14 字段，含 external_product_id 唯一键
DESCRIBE ecommerce_sync_log;  -- 11 字段，含 sync_type / status / duration_ms
```

## 3. 与 plan §5.2 预测对比

| plan 预测 | 实际 | 影响 |
|---|---|---|
| `ecommerce_order` 已存在 | ✅ 存在 | — |
| `ecommerce_product` 已存在 | ✅ 存在 | — |
| `ecommerce_sync_log` **可能 MISSING** | ✅ 存在 | **反转 → F067 VERIFIED** |

## 4. 同步触发依赖

- F076 `POST /ecommerce/product/sync/{pid}` → 写 `ecommerce_product` + `ecommerce_sync_log`
- F076 `POST /ecommerce/order/sync/{pid}` → 写 `ecommerce_order` + `ecommerce_sync_log`
- F077 webhook → 写 `ecommerce_order` + `webhook_msg_log`（**注意 webhook 用 webhook_msg_log 而不是 ecommerce_sync_log**）

## 5. Issue 状态

无新增 Issue。F067 实体层完整，仅依赖 F075/F076/F077 业务链路触发写入。

## 6. 证据文件

- `db-describe.txt` — 三表 DESCRIBE + SHOW TABLES + COUNT
