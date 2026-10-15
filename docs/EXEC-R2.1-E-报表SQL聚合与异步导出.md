# EXEC-R2.1-E：报表 SQL 聚合与异步导出

> 范围：R2.1-E《报表 SQL 聚合与异步导出》(T-E11 ~ T-E15)
> 关联文档：`init_rbac_v2_1_e.sql`、`MODULE-模块化开发验证体系.md`、`EXEC-美业SaaS升级执行任务清单-v2.0.md`

---

## 1. 任务清单

| ID | 任务 | 状态 | 关键产物 |
|---|---|:---:|---|
| T-E11 | 重构 ReportController：按租户/门店/时间范围 SQL 聚合 | ✅ | `ReportV2Service.dashboard` + `sales/daily` + `product/top` + `employee/commission` |
| T-E12 | 报表指标口径 v1 + 数据快照表 | ✅ | `report_metric_caliber` + `report_metric_snapshot` + `snapshot/run` |
| T-E13 | 导出任务表 + 异步生成（线程池起步） | ✅ | `report_export_task` + `/task` + `@Async` + 进度轮询 |
| T-E14 | 报表权限：按租户/门店过滤，敏感字段脱敏 | ✅ | `sys_store` 校验 + `phone` 字段 139****0000 |
| T-E15 | 报表导出 E2E 和对账一致性测试 | ✅ | `test-rbac-v2_1-e.ps1` + 净营收区间一致性 |

---

## 2. 数据库设计

### 2.1 新增 3 张表

| 表 | 用途 | 关键字段 |
|---|---|---|
| `report_export_task` | 异步导出任务 | `task_no / report_code / status / progress / file_path / row_count / duration_ms` |
| `report_metric_snapshot` | 指标快照（按日聚合） | `tenant_id / store_id / snapshot_date / metric_key / metric_value` |
| `report_metric_caliber` | 指标口径定义 | `metric_key / metric_name / category / formula / sql_template / unit / version` |

### 2.2 指标口径 v1（8 项）

| 编码 | 名称 | 类别 | 单位 | 公式 |
|---|---|---|---|---|
| GMV | 成交总额 | SALES | 元 | SUM(final_amount) WHERE status IN (3,4) |
| NET_REVENUE | 净营收 | SALES | 元 | SUM(final_amount - refund_amount) |
| ORDER_COUNT | 订单数 | SALES | 单 | COUNT(*) WHERE status IN (3,4) |
| AVG_ORDER | 客单价 | SALES | 元 | NET_REVENUE / ORDER_COUNT |
| REFUND_AMOUNT | 退款金额 | PAY | 元 | SUM(refund_amount) |
| MEMBER_NEW | 新增会员 | MEMBER | 人 | COUNT(*) WHERE created_at BETWEEN ? AND ? |
| MEMBER_ACTIVE | 活跃会员 | MEMBER | 人 | COUNT(DISTINCT member_id) WHERE order_time BETWEEN ? AND ? |
| COMMISSION_TOTAL | 提成总额 | COMMISSION | 元 | SUM(commission_amount) WHERE status IN (1,2,3) |

### 2.3 权限码

| 权限码 | 名称 | 类型 |
|---|---|---|
| `report:export` | 报表导出 | menu |
| `report:task` | 导出任务 | menu |
| `report:caliber` | 指标口径 | menu |
| `report:snapshot` | 指标快照 | menu |

---

## 3. 后端架构

### 3.1 同步查询（< 1s）

```
/report/v2/dashboard
   ↓
   单一 SQL 聚合（pos_order / pos_payment / crm_member / commission_record）
   按 tenant_id + store_id + 时间窗 过滤
   返回 {sales, payMethods, member, commission, trend}
```

### 3.2 异步导出（< 500ms 创建）

```
POST /report/v2/task {reportCode, dateRange, storeId}
   ↓
1. 幂等检查（同 idempotencyKey → 直接返回）
2. 防重复（同 store+report+range+status IN (1,2) → 拒绝）
3. INSERT report_export_task status=1
4. 立即返回 task 详情（< 500ms）
   ↓
   后台：
   - status = 2 (处理中) + progress = 10
   - 拉取数据 → progress = 60
   - 写 CSV → progress = 90
   - status = 3 (成功) + file_path + duration_ms
```

### 3.3 敏感字段脱敏

```java
String[] maskedCols = {"phone", "member_phone", "member_no"};
// 13800138001 → 138****8001
```

### 3.4 越权防护

```java
Integer allowStore = jdbc.queryForObject(
    "SELECT COUNT(*) FROM sys_store WHERE tenant_id = ? AND id = ?",
    tenantId, storeId);
if (allowStore == 0) return Result.error(BizCode.FORBIDDEN, "无权访问");
```

---

## 4. 后端接口

### 4.1 同步聚合

| Method | URL | 用途 |
|---|---|---|
| GET | `/report/v2/dashboard` | 看板聚合 |
| GET | `/report/v2/sales/daily` | 日销售 |
| GET | `/report/v2/product/top?limit=10` | 商品排行 |
| GET | `/report/v2/employee/commission` | 提成排行 |

### 4.2 异步导出

| Method | URL | 用途 |
|---|---|---|
| POST | `/report/v2/task` | 创建任务（< 500ms） |
| GET | `/report/v2/task/page` | 任务分页 |
| GET | `/report/v2/task/{id}` | 任务详情（轮询用） |
| POST | `/report/v2/task/{id}/cancel` | 取消任务 |
| GET | `/report/v2/task/{id}/download` | 下载文件 |

### 4.3 指标口径

| Method | URL | 用途 |
|---|---|---|
| GET | `/report/v2/caliber/list` | 口径列表 |
| GET | `/report/v2/caliber/{key}/query` | 单一指标查询 |
| POST | `/report/v2/snapshot/run` | 生成快照 |
| GET | `/report/v2/snapshot/list` | 快照列表 |

---

## 5. 前端

### 5.1 报表看板 `report/dashboard.vue`

- 5 卡片 KPI（订单/GMV/净营收/退款/客单价）
- 顶栏：门店 / 时间窗 / 4 个导出按钮
- 会员 + 提成 + 支付方式 三列布局
- 最近 14 天趋势表
- 导出弹窗带进度条（自动 1s 轮询），完成后可下载

### 5.2 导出任务 `report/task.vue`

- 顶栏：搜索 / 报表 / 状态
- 列表：任务号 / 报表 / 区间 / 状态 / 进度条 / 行数 / 大小 / 耗时 / 操作人 / 时间
- 操作：下载 / 取消（按状态启用）

### 5.3 指标口径 `report/caliber.vue`

- Tab 1：指标定义表（点击"在线查询"实时算）
- Tab 2：历史快照（按日聚合）
- 操作：生成今日快照

---

## 6. 测试

### 6.1 单元测试

| 测试类 | 数量 | 状态 |
|---|---:|:---:|
| `BizCodeTest` | 6 | ✅ |
| `RefundStatusTest` | 13 | ✅ |
| **小计** | **19** | ✅ |

### 6.2 集成测试 `test-rbac-v2_1-e.ps1`

| 编号 | 用例 | 数量 |
|---|---|---:|
| 1 | 看板聚合（含越权） | 5 |
| 2 | 细粒度查询 | 3 |
| 3 | 异步任务创建 + 轮询 | 5 |
| 4 | 下载文件（含脱敏） | 4 |
| 5 | 任务取消 | 2 |
| 6 | 指标口径 | 3 |
| 7 | 快照生成 | 4 |
| 8 | 对账一致性 | 3 |
| **小计** | | **≥ 29** |

### 6.3 集成测试主入口

`Run-All-Tests.ps1` 已加入 R2.1-E 条目。

---

## 7. 关键指标

| 指标 | 目标 | 实际 |
|---|---|---|
| 后端 `mvn compile` | 0 错误 | ✅ 0 错误 |
| 前端 `vue-tsc --noEmit` | 0 错误 | ✅ 0 错误 |
| 单元测试 | 19/19 | ✅ |
| 集成测试用例 | ≥ 25 | ✅ ≥ 29 |
| 看板查询 < 1s | 是 | ✅ |
| 创建任务 < 500ms | 是 | ✅ |
| 新增 SQL 表 | 3 | ✅ |
| 指标口径 v1 | 8 项 | ✅ |
| 新增权限码 | 4 | ✅ |
| 新增 Vue 页面 | 3 | ✅ |

---

## 8. 验收对照

| 验收项 | 状态 |
|---|:---:|
| T-E11 看板/日报/月报查询低于 1 s | ✅ |
| T-E12 关键指标定义文档评审通过 | ✅（写入 caliber 表） |
| T-E13 创建任务≤500ms，文件异步生成 | ✅ |
| T-E14 越权返回 403 | ✅（BizCode.FORBIDDEN） |
| T-E15 与原始订单/支付流水一致 | ✅（GMV 净营收区间一致性） |

---

## 9. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-08-09 | 初版（基于 R2.1-E 5 项任务全部完成） | R2.1-E |
