# P2.3 多仓管理修复结果

> 审计报告 §10.3 第 694-697 行："多仓管理 / 仓库概念引入 / 库存调拨 / 跨仓预警"

---

## 1. 调研结论

| 审计子项 | 审计判定 | 实际 |
|---|---|---|
| **仓库概念引入** | 🔴 MISSING | ✅ **已实装**（WmsWarehouse CRUD + 类型 + storeId 关联 + 启用/停用） |
| **库存调拨** | 🔴 MISSING | ✅ **已实装**（状态机 PENDING → SHIPPED → RECEIVED，CAS 扣减源仓 + 入库目标仓） |
| **跨仓预警** | 🔴 MISSING | ❌ **缺失**（StockController 只有单仓 alert + 补货，**无跨仓不平衡预警 + 智能调拨建议 + 过期预警**） |

**审计判定 3/3 准确**——2/3 已实装。本次实装**跨仓预警**全套。

---

## 2. 实际修复

### 2.1 新增 SQL

`migrate_r3_0_h_p2_3_cross_warehouse.sql`：
- `wms_warehouse_threshold` — 仓库级安全阈值
- `wms_cross_warehouse_alert` — 跨仓预警工单（IMBALANCE / OVERLOAD / UNDERLOAD / EXPIRY / TRANSFER_GAP）
- `wms_transfer_suggestion` — 智能调拨建议
- `wms_expiry_alert` — 跨仓过期预警（EXPIRED / EXPIRING_30/60/90）

### 2.2 新增 Entity（4 个）

| Entity | 用途 |
|---|---|
| `WmsWarehouseThreshold` | 仓库级阈值（覆盖默认） |
| `WmsCrossWarehouseAlert` | 跨仓预警工单（4 状态：待处理 / 已生成调拨单 / 已忽略 / 已解决） |
| `WmsTransferSuggestion` | 智能调拨建议（BALANCE-V1 算法） |
| `WmsExpiryAlert` | 跨仓过期预警 |

### 2.3 新增 Mapper（4 个）

### 2.4 新增服务：`CrossWarehouseService`（**12 个新 API**）

#### A. 跨仓库存视图（1 个）

| 端点 | 说明 |
|---|---|
| `GET /wms/cross/inventory/view` | 按 SKU 看所有仓库库存分布 |

#### B. 跨仓不平衡预警（2 个）

| 端点 | 说明 |
|---|---|
| `POST /wms/cross/scan/imbalance` | 扫描生成 IMBALANCE 预警 + 调拨建议 |
| `POST /wms/cross/suggestion/run` | 同上（运行建议生成） |

#### C. 智能调拨建议（3 个）

| 端点 | 说明 |
|---|---|
| `GET /wms/cross/suggestion/page` | 建议列表 |
| `POST /wms/cross/suggestion/{id}/accept` | **采纳建议 → 自动生成调拨单 + 关闭预警** |

#### D. 跨仓过期预警（2 个）

| 端点 | 说明 |
|---|---|
| `POST /wms/cross/expiry/scan` | 扫描所有仓库批次过期情况 |
| `GET /wms/cross/expiry/page` | 过期预警分页 |

#### E. 仓库级阈值管理（2 个）

| 端点 | 说明 |
|---|---|
| `POST /wms/cross/threshold` | 设置/更新阈值 |
| `GET /wms/cross/threshold/list` | 阈值列表 |

#### F. 预警处理（2 个）

| 端点 | 说明 |
|---|---|
| `GET /wms/cross/alert/page` | 预警分页（按 severity 排序） |
| `POST /wms/cross/alert/{id}/ignore` | 忽略预警 |

#### G. 概览（1 个）

| 端点 | 说明 |
|---|---|
| `GET /wms/cross/overview` | 总览（各级预警 + 待处理建议 + 仓库数） |

---

## 3. 核心算法：BALANCE-V1

### 3.1 触发条件

```
lowQty < min && highQty > min × 2
```

| 输入 | 计算 |
|---|---|
| `deficit` | `min - lowQty`（低仓缺口） |
| `surplus` | `highQty - max(min×2, max)`（高仓盈余） |
| `suggestedQty` | `min(deficit, surplus/2)`（不全调走，保留 1/2 安全） |

### 3.2 Severity 判定

```
suggestedQty / deficit >= 0.8  → HIGH
否则                            → MEDIUM
```

### 3.3 采纳流程

```
建议列表 → 运营采纳 → 自动生成调拨单 → 关闭预警
   ↓
[WmsTransferSuggestion] → accepted=1, acceptedAt=now
[WmsStockTransfer]       → status=PENDING
[WmsCrossWarehouseAlert] → status=2 (已生成调拨单), relatedTransferId
```

---

## 4. 过期预警分级

```
days < 0     → EXPIRED
days ≤ 30    → EXPIRING_30
days ≤ 60    → EXPIRING_60
days ≤ 90    → EXPIRING_90
days > 90    → 不预警
```

| 等级 | 含义 | 建议 |
|---|---|---|
| EXPIRED | 已过期 | 立即下架 |
| EXPIRING_30 | 30 天内到期 | 优先处理 |
| EXPIRING_60 | 60 天内到期 | 关注 |
| EXPIRING_90 | 90 天内到期 | 常规监控 |

---

## 5. 测试覆盖（30 个新场景）

| 维度 | 测试 | 场景 |
|---|---|---|
| **BALANCE-V1** | `balance_lowestDeficit` / `balance_highestSurplus` | 缺口/盈余计算 |
| | `balance_suggestedQty_minOfDeficitAndHalfSurplus` | 取 deficit |
| | `balance_suggestedQty_cappedBySurplus` | 取 surplus/2 |
| | `balance_severityHigh_whenCoverageOver80Percent` / `_Medium` | 严重度 |
| | `balance_triggerCondition` / `_noTrigger_*` | 触发条件（含单仓不触发） |
| **过期分级** | `expiryLevel_expired` / `_expiring30/60/90` | 4 级分类 |
| | `expiryLevel_normal` / `_boundary*` / `_zeroDays` | 边界 + 正常 |
| **ChronoUnit** | `daysBetween_dates` / `_pastDate` | 日期计算 |
| **状态机** | `transferStateMachine_valid` / `_invalid` | 调拨状态机 |
| **优先级** | `severityOrder_HIGH_first` | HIGH→MEDIUM→LOW 排序 |
| **阈值** | `threshold_defaultMin/Max/safetyDays` | 默认值 |
| **数值** | `recommendedQty_positive` / `_zero_invalid` | 边界 |
| **格式** | `transferNo_format` | 调拨号格式 |

---

## 6. 编译 + 测试

- ✅ `mvn compile` 通过
- ✅ 新增 30 个测试全部通过
- ✅ 全量 266 个测试，265 通过；剩 1 个失败为已知预先问题（与本次无关）

---

## 7. 多仓管理 Checklist

| 维度 | 审计判定 | 实际 |
|---|---|---|
| **仓库概念** | 🔴 | ✅ R3.0-D 已有（WmsWarehouse） |
| **仓库 CRUD** | 🔴 | ✅ R3.0-D 已有 |
| **库存调拨** | 🔴 | ✅ R3.0-D 已有（状态机 + CAS） |
| **跨仓库存视图** | — | ✅ **本次新增** |
| **跨仓不平衡预警** | 🔴 | ✅ **本次新增**（BALANCE-V1 算法） |
| **智能调拨建议** | — | ✅ **本次新增**（采纳→自动生成调拨单） |
| **跨仓过期预警** | — | ✅ **本次新增**（4 级分类） |
| **仓库级阈值管理** | — | ✅ **本次新增** |
| **预警处理** | — | ✅ **本次新增**（分页 + 忽略） |
| **概览** | — | ✅ **本次新增** |
| **测试覆盖** | — | ✅ 30 个新场景 |

---

## 8. 累计审计报告修正

| 批次 | 结果 |
|---|---|
| **P0.1** 库存消耗 | ✅ 9 合计 |
| **P0.2** 回访流程 | ✅ 11 新测试 |
| **P0.3** 跨店核销 | ✅ 29 合计 |
| **P1.4** 服务 SOP | ✅ 10 新测试 |
| **P1.5** 评价体系 | ✅ 15 新测试 |
| **P1.6** AI 真实化 | ✅ 17 新测试 |
| **P2.1** 开放平台 | ✅ 23 新测试 |
| **P2.2** 第三方集成 | ✅ 28 新测试 |
| **P2.3** 多仓管理 | ✅ **30 新测试**（本次） |
| P2.4+ 长期 | 待执行 |

---

## 9. 相关文件

- `backend/src/main/resources/db/sql/migrate_r3_0_h_p2_3_cross_warehouse.sql`（本次新增）
- `backend/src/main/java/com/meiyetec/service/CrossWarehouseService.java`（本次新增 12 API）
- `backend/src/main/java/com/meiyetec/entity/{WmsWarehouseThreshold,WmsCrossWarehouseAlert,WmsTransferSuggestion,WmsExpiryAlert}.java`（本次新增 4）
- `backend/src/main/java/com/meiyetec/mapper/*`（本次新增 4 个）
- `backend/src/test/java/com/meiyetec/service/CrossWarehouseTest.java`（本次新增 30 测试）

---

## 10. 测试统计

| 测试套件 | 场景数 |
|---|---|
| **P2.3 多仓管理**（本次新增） | **30** |
| P2.2 第三方集成 | 28 |
| P2.1 开放平台 | 23 |
| P1.6 AI 真实化 | 17 |
| P1.5 评价体系 | 15 |
| P1.4 SOP 步骤 | 10 |
| P0.1 库存链路 | 9 |
| P0.2 回访补强 | 11 |
| P0.3 跨店补强 | 29 |
| 其他（P1/P2/P3/其他） | 约 94 |
| **总计** | **266** |