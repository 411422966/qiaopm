# F140 — I118 DDL 落地验证 + 后端 GET 端点全量扫描（2026-10-30）

> **调查方式**：运行时实测（HTTP 探测 + 容器日志堆栈回查）+ 只读静态分析（源码逐行定位）。
> **本目录性质**：原始证据留痕包。所有结论必须能回指到本目录内的文件或下文列出的源码行号。
> **重要限制**：本包不含"修复后复测"证据，因此所有缺陷当前状态均为 `BROKEN` / `UNVERIFIED`，**无一项可标 `VERIFIED`**。

---

## 0. 环境绑定

| 项 | 值 | 来源 |
|---|---|---|
| commit | `cc95a00` | `git -C d:\QiaoPM rev-parse --short HEAD` |
| DB | `127.0.0.1:3307/meiyetec`（456 张表，18:12 实测） | `audit/baseline/env.md` |
| DB snapshot（本次 DDL 前） | `backup/meiyetec-pre-i118-batch2-20261030.sql`（14,727,197 B / 16:54） | 本仓库 backup/ |
| 运行构件 | `meiyetec-api` 容器，image `sha256:aab70985…`，容器 2026-10-30 17:24 启动 | docker |
| **app.jar 构建时间** | **2026-10-20 15:59**（落后源码 10 天，见 §4 D1） | `jar-diff.json` |
| 探测脚本（非正式工具） | `.audit-tmp/` 下 `sweep-get2` / `classify-500` / `find-npe` / `find-product-name` / `dump-table` | — |
| 原始日志 | `.audit-tmp/api-full2.log`（2,780,204 B，18:14–18:19 扫描期） | — |

---

## 1. I118 状态判定

| 检查项 | 结果 | 状态 |
|---|---|---|
| 169 个目标表落地 | `information_schema` 实测 `missing_tables = 0`；建表时间 batch1 00:41–00:43、batch2 16:55–16:57 | **VERIFIED** |
| 对既有表的改动 | 仅 `mkt_coupon.coupon_name` ADD COLUMN（`backup/fix-i118-full-ddl.sql:2461-2466`） | 可控 |
| `biz_service.price` / `mkt_coupon.coupon_value` / `stock_inventory.warehouse_id` | before-i129 / pre-i118 / pre-i118-batch2 / 当前库 四份 dump 列集合一致 → **这些列从未存在过**，非 I118 引入 | 见 I133 |
| 17 个 `feature.legacy-missing-ddl.enabled` 守卫 Controller | `application.yml:151-152` = `false` → 实测 17 路径全部 HTTP 404（无 handler） | **SHELL-INTENTIONAL** |

---

## 2. 后端 GET 端点全量扫描（861 个端点，18:14–18:19）

原始结果：`sweep-get2.json`；500 明细：`500-classified.json`（含每条 traceId）。

| HTTP / code | 数量 | 说明 |
|---|---|---|
| 200 / code 200 | 658 | 正常 |
| 200 / code 500 | **79** | 见 §3 |
| 200 / code 404 | 51 | 业务 NOT_FOUND（HTTP 仍 200） |
| HTTP 404 无 handler | 46 | 其中 44 个属 I118 关闭的 Controller |
| 200 / 非 JSON | 20 | Excel/CSV 下载，非错误 |
| HTTP 404 + code 404 | 6 | `BusinessException(NOT_FOUND)` |
| 200 / code 400 | 1 | — |

79 个 500 的根因分布：

| 异常 | 数量 | 性质 |
|---|---|---|
| `MissingServletRequestParameterException` | 64 | 探测未带必填参数 → 探测侧假阳性；**但暴露契约缺陷 C1** |
| `BadSqlGrammarException` | 6 | 真 bug（R1–R5） |
| `NullPointerException`（`Map.get` / `Map.getOrDefault` → `.longValue()`） | 5 | 真 bug（R8） |
| `EmptyResultDataAccessException` | 1 | 真 bug（R7） |
| `TransientDataAccessResourceException`（参数占位符越界） | 1 | 真 bug（R6） |
| `Result.error(String)` 业务「不存在」 | 2 | 契约缺陷 C2 |

---

## 3. 真缺陷清单（R1–R8）

运行时异常证据：`r1-r7-runtime-errors.txt`、`r8-npe-stacks.txt`。

| ID | 端点 | 源码位置 | 运行时症状 | 根因 |
|---|---|---|---|---|
| R1 | `GET /material/cost/analysis`、`/material/cost/by-service/{id}` | `mapper/ServiceMaterialCostMapper.java:15,27,34` | `BadSqlGrammarException: Unknown column 'bs.price' in 'field list'` | SQL 引用 `bs.price`，`biz_service` 实有列是 `unit_price`/`member_price` |
| R2 | `GET /mapi/home` | `service/MobileApiService.java:128` | `Unknown column 'coupon_value' in 'field list'` | SQL 引用 `coupon_value`，`mkt_coupon` 实有列是 `discount_amount`/`discount_rate` |
| R3 | `GET /stock/trace/{id}`（及全部 `StockInventoryMapper` 查询） | `entity/StockInventory.java:21,22,32` | `Unknown column 'product_name' in 'field list'`（`The error may exist in com/meiyetec/mapper/StockInventoryMapper.java`） | 实体多出 `productName`/`category`/`version` 三字段，`stock_inventory` 无对应列 |
| R4 | `GET /wms/cross-store/inventory/aggregate` | `service/impl/CrossStoreInventoryServiceImpl.java:56,61,77` | `Unknown column 'i.warehouse_id' in 'field list'` | `stock_inventory` 无 `warehouse_id`；`migrate_r3_0_d_multi_warehouse.sql` 从未执行 |
| R5 | `GET /wms/cross/inventory/view` | `service/CrossWarehouseService.java:59,63` | 同上 | 同上 |
| R6 | `GET /pay/dcep/order/page` | `service/DcepService.java:649` | `TransientDataAccessResourceException: ... Parameter index out of range (2 > number of parameters, which is 1)` | COUNT 语句只有 1 个 `?`，却把分页列表的全部参数数组传了进去 |
| R7 | `GET /lbs/technician/{id}/route` | `service/LbsDispatchService.java:589` | `EmptyResultDataAccessException: Incorrect result size: expected 1, actual 0` | `queryForObject` 无行时抛异常，未按「无计划」分支处理 |
| R8 | `GET /overseas-payment/payment/statistics` | `service/impl/OverseasPaymentServiceImpl.java:259` | `Cannot invoke "java.lang.Number.longValue()" because the return value of "java.util.Map.get(Object)" is null` | `SUM(CASE WHEN ...)` 无数据时返回 NULL，`((Number) row.get(...)).longValue()` 未判空 |
| R8 | `GET /wms/cross-store/dashboard` | `service/impl/CrossStoreInventoryServiceImpl.java:674` | `Cannot invoke "java.lang.Number.longValue()" because ... "java.util.Map.getOrDefault(Object, Object)" is null` | 同上（`successRate` 聚合行） |
| R8 | `GET /intel/recommend/{id}` | `service/P1RemainingService.java:466` | `... Map.getOrDefault(Object, Object)" is null` | `last_purchase_days` 为 NULL 时 `getOrDefault` 返回 null（key 存在） |
| R8 | `GET /crm/care/review-analysis` | `service/WmsService.java:~239` | `... Map.get(Object)" is null` | `SUM(CASE...)` → NULL → `((Number) data.get("positive")).longValue()` |
| R8 | `GET /mkt/fission/stats` | `service/P2FeaturesService.java:346-350` | `Cannot invoke "java.lang.Long.longValue()" because ... getOrDefault(Object, Object)" is null` | 同上，`(Long) row.getOrDefault("ordered", 0)` 拆箱 NPE |

---

## 4. 契约缺陷与部署漂移

| ID | 描述 | 位置 | 状态 |
|---|---|---|---|
| C1 | `MissingServletRequestParameterException` / `MethodArgumentTypeMismatchException` / `HttpMessageNotReadableException` 无处理器 → 落到 `handleAny(Exception)` → **缺参返回 code 500「系统繁忙」**，应为 400 | `exception/GlobalExceptionHandler.java:59-63`（`handleAny`）；`:64-68`（`handleRuntime` 亦无栈） | 见 I134 |
| C2 | `Result.error(String)` 硬编码 `code = 500`；同语义「不存在」在系统里有 3 种表现：HTTP200+code404（51 个）/ HTTP404+code404（6 个）/ HTTP200+code500（2 个） | `util/Result.java:44-49` | 本次**不处理**（用户指示暂缓） |
| D1 | 运行中的 `app.jar` 构建于 2026-10-20 15:59，与当前源码差异恰好 3 个类：源码有而包内无 `controller/GrayReleaseController`、`util/GrayReleaseHasher`；包内有、源码已删 `service/GrayReleaseService` | `jar-diff.json` / `jar-diff.txt` | 见 I135 |

---

## 5. 本包无法确认的项

| 项 | 原因 |
|---|---|
| 各缺陷修复后的行为 | 本包只有修复前证据；修复后需重新跑 GET 扫描并另行归档 |
| `sys_menu_permission` 运行态菜单 | 需连库导出，未纳入本次范围 |
| 64 个 `MissingServletRequestParameterException` 是否都是「探测未带参」 | 本包仅按异常类型分类，未逐个复核接口契约；其中真正缺「必填参数文档」的部分归入 I134 |
| R8 之外的 `Map.getOrDefault` 潜在 NPE | 本次仅覆盖扫描期实际触发的 5 处；同类写法在 `ReportV2Service`、`OrgHierarchyService`、`ConsentAndDrugDeviceService`、`FollowUpClosureService`、`MarketingAutomationService` 等处存在，**是否在空数据集下触发未验证** |

---

# 附录 A — R1–R8 修复后复测（2026-10-30 23:0x）

> **环境绑定**：commit `cc95a00`（工作区有未提交改动，见 §A.5）/ DB `127.0.0.1:3307/meiyetec` / 镜像 `meiyetec/api:fixscan3`（本次新建，jar 由 `mvn -Dmaven.test.skip=true package` 于 2026-10-30 23:04 生成）/ 容器 `meiyetec-api` healthy。
> **DB snapshot（本次修复后）**：`backup/meiyetec-post-r1r8-fix-20261030.sql`（32,878,534 B / 23:21，第 16970 行含 `stock_record.dual_verify_status` varchar(16) DEFAULT 'NOT_REQUIRED'）。
> **env**：`audit/baseline/env.md`。
> **本附录性质**：修复后的运行时实测证据。R1–R8 各项可改判为 `VERIFIED`；其余 67 个仍 500 的端点**不因本次修复而改判**。

## A.1 本次追加的两处修复（R3 / R8 的补漏）

首轮修复打包后复测发现 R3、R8 两个端点仍 500，根因是**另外两个同类问题**，不在首轮修复清单内：

| ID | 端点 | 源码位置 | 运行时异常（实测） | 根因 | 处置 |
|---|---|---|---|---|---|
| R3-b | `GET /stock/trace/{id}` | `entity/StockRecord.java:35`（`dualVerifyStatus`） | `Unknown column 'dual_verify_status' in 'field list'` | `stock_record` 表缺 `dual_verify_status` 列。`migrate_p2_11_dual_verify.sql:44` 已定义该列、`StockDualVerifyService.updateRecordStatus`（`:112-119`）会写它，但该迁移从未执行；I118 落地时只补了 `stock_check.dual_verify_status`，**漏了 `stock_record`** | **在库补列**（DDL），脚本 `backup/fix-r3-stock-record.sql`（判存 + PREPARE 动态执行，兼容 MySQL < 8.0.29）+ 回滚 `backup/fix-r3-stock-record-rollback.sql` |
| R8-b | `GET /intel/recommend/{id}` | `service/P1RemainingService.java:488`（SQL）、`:496`（取值） | `Unknown column 'package_name' in 'field list'` | SQL 硬编码 `crm_package.package_name`，但该表实际列名是 `name`（`package_name` 是 `crm_package_order` 的冗余列名，此处查错了表） | **改 SQL 对齐现有库**：`package_name` → `name`（SELECT 与 `p.get(...)` 同步改） |

附带的 `sys_config.stock_dual_verify_threshold` 阈值配置补录（读方 `StockDualVerifyService.getThreshold:55-63`，缺省回落 1000）。实测发现实际 `sys_config` 列结构为 `config_key / config_value / config_group / description / sort_order / created_at / updated_at`，与 `migrate_p2_11_dual_verify.sql:60-62` 假设的 `config_type / default_value / remark` **不一致** → 按实际列写。

## A.2 定向复测：13 个 R1–R8 端点全部 code=200

原始输出：`retest-r1-r8-after-fix.txt`。

| ID | 端点 | 修复前 | 修复后 |
|---|---|---|---|
| R1 | `GET /material/cost/analysis` | 500 | **200** |
| R1 | `GET /material/cost/by-service/1` | 500 | **200** |
| R2 | `GET /mapi/home` | 500 | **200** |
| R3 | `GET /stock/trace/1` | 500 | **200** |
| R4 | `GET /wms/cross-store/inventory/aggregate` | 500 | **200** |
| R5 | `GET /wms/cross/inventory/view` | 500 | **200** |
| R6 | `GET /pay/dcep/order/page` | 500 | **200** |
| R7 | `GET /lbs/technician/1/route` | 500 | **200** |
| R8 | `GET /overseas-payment/payment/statistics` | 500 | **200** |
| R8 | `GET /wms/cross-store/dashboard` | 500 | **200** |
| R8 | `GET /intel/recommend/1` | 500 | **200** |
| R8 | `GET /crm/care/review-analysis` | 500 | **200** |
| R8 | `GET /mkt/fission/stats` | 500 | **200** |

`code=500 count: 0`。

## A.3 全量复扫：865 端点，500 从 79 降到 74，**无回归**

原始结果：`sweep-all-get-after-fix.json`（修复后全量）、`sweep-get2.json`（修复前基线，861 端点）、`sweep-diff-after-fix.txt`（归一化差异）。

对比口径：路径变量归一化为 `/{id}` 后比较（基线用 `id=1`、本次扫描脚本用 `id=1548`，直接比字符串会产生 16 个假差异）。

| 项 | 修复前 | 修复后 |
|---|---|---|
| 扫描端点数 | 861 | 865 |
| `code=500` | 79 | **74** |
| 已消除 | — | **12**（R1×2、R2、R3、R4、R5、R6、R7、R8×5） |
| 仍 500 | — | 67 |
| 新增 500 | — | **7**（全部非回归，见下） |

「新增 500」的 7 个端点：`/member-level/{id}`、`/packages/{id}`、`/pos/orders/{id}`、`/pos/services/{id}`、`/stock/check/{id}`、`/stock/products/{id}`、`/sys/tenant/{id}/stats`。

逐个用 `id=1` / `id=1548` 双值复测（原始输出：`regress-candidates-check.txt`），结论：**7 个全部是 `id=1548` 记录不存在时的业务「不存在」响应**，用 `id=1` 均为 code=200。例：

```
/stock/products/1     HTTP=200 code=200  {"code":200,"message":"操作成功","data":{"id":1,...}}
/stock/products/1548  HTTP=200 code=500  {"code":500,"message":"商品不存在",...}
/packages/1           HTTP=200 code=200  ...
/packages/1548        HTTP=200 code=500  {"code":500,"message":"套餐不存在",...}
```

即 **C2 契约缺陷**（`util/Result.java:44-49` 的 `Result.error(String)` 硬编码 `code=500`）的另一种表现路径 —— 用户 2026-10-30 已指示 C2 暂缓处理，**不属本次修复引入的回归**。

## A.4 仍 500 的 67 个端点：不因本次修复改判

按 §3 的根因分布，绝大多数是「探测未带必填参数」导致的 `MissingServletRequestParameterException`（假阳性），归入 **I134**（缺参应返回 400 而非 500）。本次修复未触碰这些端点，其状态维持原判。

## A.5 本次未处理项

| 项 | 原因 |
|---|---|
| C2（`Result.error(String)` 硬编码 code=500） | 用户 2026-10-30 指示暂缓 |
| I134（缺参 500 → 400） | 不在本次 R1–R8 修复范围 |
| `migrate_p2_11_dual_verify.sql` 的 `ADD COLUMN IF NOT EXISTS` 语法 | MySQL 8.0.46 **不支持**该语法（实测 `ERROR 1064`），脚本整体不可执行；已归入 **I130**。本次用动态 SQL 绕过，脚本本体未改 |
| `migrate_p2_11_dual_verify.sql` 的 `sys_config` 插入列名 | 实测 `config_type / default_value / remark` 三列**不存在**于实际 `sys_config` 表；本次按实际列写，脚本本体未改 |
| `member_credit_log.lock_until` vs 实体 `lockedUntil` | 列名漂移（实体 `lockedUntil` → MyBatis-Plus 期望 `locked_until`，实际列名 `lock_until`）。本轮全量扫描未触发该端点 500，**未验证是否有端点会炸**，不在 R1–R8 范围 |
| `sys_role_permission.tenant_id` 缺失 | 实体 `SysRolePermission.tenantId` 无对应列。本轮扫描未触发 500，**未验证是否有端点会炸** |
| 工作区未提交改动 | R1–R8 修复 + 本次补漏共 13 个 Java 文件改动均在工作区，未 commit；证据绑定 commit `cc95a00` + 未提交 diff |

---

# 附录 B — Q1 技师等级评定指标口径修复（2026-11-01）

> **环境绑定**：commit `596a395`（工作区 dirty，含本附录所述未提交改动）/ DB `127.0.0.1:3307/meiyetec`（**只读查询**，未改库）/ DB snapshot `backup/meiyetec-post-r1r8-fix-20261030.sql` / env `audit/baseline/env.md`。
> **本附录性质**：Q1 修复的代码改动 + SQL 实跑证据 + 运行时复测证据。
> **本包新增证据文件**：`500-verify-final-victory.json`（全量 500 扫描终局，源自 `.audit-tmp/i118-inv/`，MD5 `B86A8E067133B83F71E41D3757DAA927`）、`q1-sql-probe-final.txt`（SQL 实跑原始输出）、`q1-endpoint-triage.txt`（剩余两个 500 端点的同类 SQL 排查结论）。

## B.1 Q1 问题定性

M08 技师等级评定存在**两处口径漂移 + 一处硬编码**：

| 编号 | 缺陷 | 原位置 | 原行为 |
|---|---|---|---|
| Q1-a | **好评率硬编码兜底** | `LevelEvaluationServiceImpl.java`（原 `:196-211`） | 查 `booking_review` 表（**该表不存在**）→ 抛异常 → catch 里 `ratingScore = new BigDecimal("85.0")` **写死 85 分** |
| Q1-b | **好评率口径与晋升页不一致** | `TechnicianLevelServiceImpl.java` | 自带一套好评率算法（`booking_review` 同源），两页各算各的 |
| Q1-c | **服务月数缺失回落 0** | `LevelEvaluationServiceImpl.java`（原 `:170-176`） | 仅看 `pos_employee.entry_date`，`entry_date` 为 NULL 时**直接记 0**，不回落到 `create_time` |

`booking_review` 表在库中**不存在** → Q1-a 的 catch 分支是**每次必然命中**的路径，等价于好评率恒为 85.0。

## B.2 修复方案与落点

新增统一口径实现类，**单一事实来源**：

- `backend/src/main/java/com/meiyetec/util/EmployeeMetricSupport.java`（**新增**）
  - `CalculateRatingScore(JdbcTemplate, Long employeeId, Long tenantId)` — 好评率（百分数，一位小数）
  - `CalculateServiceMonths(LocalDate entryDate, LocalDateTime createTime)` — 服务月数

修改方：

- `backend/src/main/java/com/meiyetec/service/impl/LevelEvaluationServiceImpl.java`
  - `calculateEmployeeMetrics`：好评率改走 `EmployeeMetricSupport`（**移除 85.0 硬编码**）；服务月数改走 `EmployeeMetricSupport`；投诉率 SQL 补租户隔离；补 `employeeName` / `currentLevelName` 字段（前端 `views/technician/evaluation.vue` 消费）
- `backend/src/main/java/com/meiyetec/service/impl/TechnicianLevelServiceImpl.java`
  - `evaluatePromotion`：好评率 / 服务月数改走同一实现，消除双口径

### B.2.1 好评率口径（无任何硬编码兜底）

评价来源表 `crm_review`（`deleted = 0 AND status = 2`，`status` 语义见 `entity/CrmReview.java:37`「1待审核 2已发布 3已隐藏 4已申诉」）。

好评（`overall_rating >= 4`）数 / 总评价数 × 100，**总评价数为 0 时记 0**。

血缘两条：

| 血缘 | `source_type` | `source_id` 指向 | 归属判定 |
|---|---|---|---|
| ① 订单评价 | `ORDER` | `pos_order.id` | 该订单项的服务员工（`pos_order_item.employee_id`） |
| ② 员工评价 | `STAFF` | `pos_employee.id` | 直接评价该员工 |

两条血缘均带租户隔离：外层 `r.tenant_id = ?`（`tenantId` 为 null 时不过滤），订单血缘内层 `o.tenant_id = r.tenant_id`。

### B.2.2 服务月数口径

`pos_employee.entry_date` 优先 → 缺失回落 `pos_employee.create_time` → 两者都缺失记 0。
算法：`ChronoUnit.MONTHS.between(joinDate, LocalDate.now())`，下限 0。

## B.3 SQL 实跑证据（`q1-sql-probe-final.txt`）

| 探针 | 内容 | 结果 |
|---|---|---|
| C | 好评率完整 SQL（employee_id=1，租户 1） | **执行成功**，`total=0 / good=0` |
| G | 业绩（employee_id=1） | **执行成功**，`112898.00` |
| H | 投诉（handler_id=1） | **执行成功**，0 |
| F | `pos_employee` 档案 | 员工 1-6 的 `entry_date` **全为 NULL**、`create_time = 2026-08-14 23:19:55` → 验证了 Q1-c 的回落路径**在真实数据上确实会被触发** |
| P | `crm_review` 全量血缘命中率 | 13 条存活评价中，血缘①命中 **0** 条、血缘②命中 **0** 条（`source_id` 为 1000+ 的演示数据；`pos_order` 最大 id=3936 但这些 id 无对应员工项；`pos_employee` 最大 id=627） |

**B.3.1 由此得出的重要结论**：修复后好评率**当前实测为 0**，因为现存 `crm_review` 数据的 `source_id`（1000–1011，demo-seed 生成）**与 `pos_order` / `pos_employee` 的真实 id 无交集**。这是**数据侧的种子数据问题**，不是 SQL 缺陷 —— SQL 逻辑正确（在真实订单/员工 id 下会正常命中），但**在当前库数据下无一条评价可归属到任何员工**。

这正是「绝不可硬编码」口径的**必然结果**：宁可记 0，也不伪造 85 分。该状态标为 **PARTIAL-NON-BLOCKER**（口径正确、当前数据下无评价可评），需在评价数据与订单/员工打通后复测。

## B.4 运行时复测（`q1-runtime-verify-after-fix.json`，base `http://localhost:8082`）

| 端点 | code | 关键返回 |
|---|---|---|
| `GET /technician/level/promote/evaluate?employeeId=1` | **200** | `ratingRate:0, monthsOfService:2, totalSales:112898, eligible:true` |
| `GET /technician/level/evaluation/metrics/1` | **200** | `ratingScore:0, serviceMonths:2, totalSales:112898, complaintRate:0, employeeName:"王雪梅"` |
| `GET /technician/level/evaluation/list?employeeId=1` | **200** | `[]` |

`serviceMonths=2` 与 `create_time=2026-08-14` 到 `2026-11-01` 的月差一致 → **Q1-c 回落路径实测生效**。
`ratingScore=0` 与 §B.3.1 的血缘命中 0 一致 → **Q1-a 硬编码已消除**。

## B.5 剩余两个 500 端点的同类 SQL 排查（详见 `q1-endpoint-triage.txt`）

| 端点 | 判定 | 依据 |
|---|---|---|
| `GET /biz/booking/resource/check-conflict` | **不是同类 SQL 问题**；表列齐备、SQL 实跑通过；四参齐全时 code=200。500 = 缺 `endTime` → `MissingServletRequestParameterException` → **I134** | `BookingResourceService.java:264-291` |
| `GET /sync/member/list` | **不是同类 SQL 问题**；表列齐备、SQL 实跑通过；`sessionKey` 齐全时 code=200。500 = 缺 `sessionKey` → **I134** | `MultiDeviceSyncService.java:141-147` |

附带纠正：`sweep-frontend-reach.tsv` 把这两个端点标为 `MISSING_TABLE booking_waitlist_score_log` / `MISSING_TABLE dcep_config` 属**扫描脚本归因错误** —— 两张表均存在，且这两个端点的 SQL 根本不引用它们；原始分类 `sweep-classified.tsv` 已正确标为 `PROBE_NOISE_MISSING_PARAM`。

顺带发现 `GET /biz/booking/member-credit` 修复后仍 500（「数据库异常」），与 `member_credit_log.lock_until` 列名漂移相关（§A.5 已记为已知未处理项），**不在 Q1 授权范围，未处理**。

## B.6 本附录未确认项

| 项 | 原因 |
|---|---|
| 好评率在**有真实归属评价**下的非零取值 | 现存 `crm_review` 的 `source_id` 与 `pos_order`/`pos_employee` 真实 id 无交集（§B.3.1），无法造出可归属评价 |
| `promote` 落库后 `level_evaluation` 表的实际写入 | 本次仅走只读 evaluate / metrics 端点，未触发写库 |
| `ratingScore` / `monthsOfService` 在 `promotion_rating > 0` 等级下的晋升判定 | 库内 `biz_technician_level` 现有等级 `promotion_rating` 均为 0 / null，实测走的是 `requiredRating=0` 分支 |
| 工作区未提交 | Q1 修复的 3 个 Java 文件改动均在工作区未 commit；证据绑定 commit `596a395` + 未提交 diff |

## B.7 交叉验证补充（`q1-sql-probe-final.txt` SECTION E）

§B.3.1 判定「`crm_review.source_id` 与 `pos_order` / `pos_employee` 真实 id 无交集」后，又用两种彼此独立的方式复验该结论，结论成立。

| 复验方式 | 结果 |
|---|---|
| 区间交集：`id BETWEEN 1000 AND 1011` | `pos_order` / `pos_order_item` / `pos_employee` 各 **0 条**；`crm_review.source_id` 该区间 **24 条** |
| 逐条血缘命中（`status=2` 已发布、`deleted=0`） | ORDER：`source_id` 1000/1004/1008 → `order_exists=0`、`order_item_exists=0`；STAFF：1001/1005 → `emp_exists=0` |
| 全库主键区间扫描（不依赖 `source_id` 假设） | 命中表仅 `crm_ltv_snapshot` / `crm_sync_log` / `marketing_touch_log` / `sys_log` / `sys_role_permission` / `trace_span`；**`pos_order` / `pos_order_item` / `pos_employee` / `biz_booking` 均未命中** |

根因定位在数据侧而非 SQL 侧：`crm_review.source_id` 由 `CrmReviewController.demoSeed()`（`backend/src/main/java/com/meiyetec/controller/CrmReviewController.java:322-323`，`setSourceId((long)(1000 + i))`）生成的演示数据，与业务真实主键空间不重合。

因此修复后好评率实测为 **0** 属于「不可硬编码」口径的正确表现（真实反映无可评评价），而非 SQL 缺陷。状态维持 **PARTIAL-NON-BLOCKER**：待评价数据与订单 / 员工打通后复测非零取值。
