# I156 — `computeChurn` 与 `batchPredictChurn` 双写 `crm_churn_prediction` 无互斥，产生重复数据

> **状态**：Open
> **严重度**：P1（数据污染）—— 同一 `(member_id, snapshot_date)` 出现 2 条预测记录，看板计数翻倍
> **发现日期**：2026-11-12（I152 批 2-3 验证全绿后，运行时复现时发现）
> **登记人**：Cursor（I152 批 2-3 —— `batchPredictLtv/Churn` 表名+量纲+幂等修复）
> **关联**：I152（`crm_order` 引用外溢；批 2-3 修复引入了本双写面）、I155（预测端不消费训练模型）、I151（退款未扣）
> **修复决策**：**已选定方案 A**（2026-11-13，工程最优解，非业务拍板）—— 下一轮单独批次实施，本轮仅登记（见 §5.5）

---

## 0. 一句话描述

`crm_churn_prediction` 表存在**两个互不知情的写入者**：

| 写入者 | 入口 | 幂等策略 | 量纲 |
|---|---|---|---|
| `DataGovernanceService.batchPredictChurn` | `POST /governance/model/batch-predict`（`modelType=CHURN`） | **先删后插**（`DELETE ... WHERE tenant_id=? AND snapshot_date=CURDATE()`） | `churn_score` 4 位小数 |
| `LtvChurnController.computeChurn` | `POST /ltv-churn/churn/compute` | **无任何清理**，逐会员 `INSERT` | `churn_score` 2 位小数 |

两者写同一张表、同一 `model_code`（`RULE-CHURN-V1`）、同一 `snapshot_date`（当日），
且**计算口径不同**（数据源不同、精度不同、风险阈值边界不同）——
`batchPredictChurn` 的「先删后插」只对自己幂等，**对 `computeChurn` 完全无效**。

用户只要在同一天先点一次「批量预测」再点一次「计算流失」，表内立刻出现 489 组重复。

---

## 1. 缺陷位置

### 1.1 写入者 A：`batchPredictChurn`（先删后插）

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/DataGovernanceService.java` |
| 方法 | `batchPredictChurn(Long tenantId, int limit)`（L616） |
| 调用链 | `POST /governance/model/batch-predict`（L485 `batchPredict`）→ L503 `batchPredictChurn` |
| 幂等 | L698-701 `executeIdempotent("DELETE FROM crm_churn_prediction WHERE tenant_id = ? AND snapshot_date = CURDATE()", ...)` |
| 写入量 | 覆盖租户内**全部**会员（`crm_member` LEFT JOIN，无消费会员也写入，recency 记 9999 → 落入 HIGH） |
| 数据源 | `pos_order`（`status=2`）、`crm_complaint_ticket`、`crm_review` |
| `risk_level` | `HIGH` / `MED` / `LOW`（阈值 `>=0.66` / `>=0.33`） |
| `churn_score` | `decimal(5,4)`，4 位小数 |

### 1.2 写入者 B：`computeChurn`（无清理，直接 INSERT）

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/controller/LtvChurnController.java` |
| 方法 | `computeChurn()`（L160），`@PostMapping("/churn/compute")`（L158） |
| 输入 | `crm_ltv_snapshot` 全量快照（L163 `selectList`，`orderByDesc(id)`），`putIfAbsent` 取每个会员 id 最大的一条 → **不限 snapshot_date**，取的是每个会员的「最后一条快照」 |
| 写入 | L203 `churnMapper.insert(p)` —— **循环内直接 INSERT，无 DELETE、无 upsert** |
| 写入量 | 覆盖**有 LTV 快照的会员**（本环境实测 489 名，与租户会员数一致） |
| 数据源 | `crm_ltv_snapshot`（不再直连 `pos_order`） |
| `risk_level` | `HIGH` / `MED` / `LOW`（阈值 `>0.66` / `>0.33`，**严格大于**，与 A 的 `>=` 边界不同） |
| `churn_score` | `setScale(2)`，2 位小数 |
| 事务 | `@Transactional(rollbackFor = Exception.class)`（L159）—— 只保证本次调用自身原子，**不解决跨调用重复** |

### 1.3 两个写入者的计算口径差异（同一会员结果不一致）

以 `member_id = 1` 为例（同一天、同一 `model_code`、同一 `model_version`）：

| 字段 | A（batch，id=2534） | B（compute，id=4022） |
|---|---|---|
| `churn_score` | `0.4444`（4 位） | `0.4400`（2 位，精度截断） |
| `total_orders` | 10 | 10 |
| `last_purchase_days` | 80 | 80 |

根因：B 的分数来自 `crm_ltv_snapshot` 的 `last_purchase_days`（来自 `LtvChurnController.computeLtv` 的口径），
A 的分数来自 `pos_order` 直算的 `DATEDIFF(CURDATE(), MAX(pay_time))`；
两者**输入链路不同**，加上精度与阈值边界差异，即使只有一条记录，落库值也不唯一。

> 附带：`computeChurn` 的 `tenure_days` 写的是 `s.getLastPurchaseDays()`（L196），
> 疑似 `setTenureDays` 的复制粘贴错误（与 `last_purchase_days` 同值）。
> 该项与本 issue 是不同缺陷，**本 issue 不落盘**，仅在此备注供后续处理。

---

## 2. 运行时复现证据

**环境**：本地 Docker（`meiyetec-mysql` / `meiyetec-api`），租户 1，管理员 `admin`
**commit HEAD**：`43bbcce`（`fix(ltv): 修复 batchPredictLtv/Churn 表名+量纲+幂等（I152 批 2-3）`）
**Evidence 目录**：`audit/evidence/F156-compute-churn-duplicate/`
（`raw/01-repro.txt` 复现全过程、`raw/02-db-state.txt` 逐条 DB 证据、`raw/03-cleanup.txt` 清理与基线恢复）

### 2.1 复现前（基线，`batchPredictChurn` 写入）

```
total  dm    ds
489    489   1        ← 489 行 / 489 会员 / 1 个 snapshot_date
risk_level  c
HIGH        393
MED         96
create_time            c
2026-11-12 23:14:47    489
```

### 2.2 触发写入者 B

```
POST /ltv-churn/churn/compute   (admin)
{ "code": 200, "message": "流失预测已刷新",
  "data": { "members": 489, "predictions": 489,
            "modelCode": "RULE-CHURN-V1", "computedAt": "2026-11-12" } }
```

### 2.3 复现后（污染态）

```
total  dm
978    489        ← 978 行（应为 489），会员数不变
dup_member_day_groups
489                ← 489 组 (member_id, snapshot_date) 重复

create_time            c    min_id  max_id
2026-11-12 23:14:47    489  2534    3022   ← 写入者 A
2026-11-12 23:25:13    469  3534    4002   ← 写入者 B
2026-11-12 23:25:14     20  4003    4022   ← 写入者 B
```

### 2.4 看板数字翻倍

| 接口 | 基线 | 污染态 | 变化 |
|---|---|---|---|
| `GET /ltv-churn/dashboard` → `highRiskCount` | 393 | **786** | ×2 |
| `GET /ltv-churn/dashboard` → `medRiskCount` | 96 | **192** | ×2 |
| `GET /intel/churn/overview` → `highCount` | 393 | **786** | ×2 |
| `GET /intel/churn/overview` → `avgChurnScore` | 0.8767 | 0.8766 | 均值被两条不同精度记录平均 |
| `GET /ltv-churn/dashboard` → `membersWithLtv` | 489 | 489 | 不变（去重按 member） |

`/ltv-churn/dashboard` 的统计实现是 `churnAll.stream().filter(...).count()`（`LtvChurnController.java:224-243`），
**对 `crm_churn_prediction` 全表计数、不按 `snapshot_date` 过滤** → 任何历史重复都会永久累积。

### 2.5 重复数据的额外风险

- `GET /ltv-churn/churn/member/{id}`（L267）按 `orderByDesc(id) LIMIT 1` 取「最新一条」
  → 返回哪一条取决于**两条路径的调用先后**，单会员查询结果**不确定**（本环境 id=1 返回 `0.4400`，若顺序颠倒则返回 `0.4444`）。
- `GET /ltv-churn/churn/risk-list`（L289）按 `churn_score DESC` 排序取 Top N
  → 同一会员可能**同时占据两条 Top 记录**，榜单失真。
- `crm_churn_alert` 的写入路径（`ChurnPredictionService.upsertAlert`）按 `member_id + status` 去重，
  不受本重复影响；但若后续放开模型激活，告警基数会跟着重复数据放大。

### 2.6 清理与基线恢复

复现完成后已删除本次产生的 489 行（`id > 3022`），删除前做了存在性检查（`COUNT(*) = 489`）：

```
rows_now  dm
489       489
dup_groups
0
GET /ltv-churn/dashboard => highRiskCount=393, medRiskCount=96   ← 已回到基线
```

---

## 3. 触发路径与前置条件

| 触发方式 | 入口 | 前端位置 |
|---|---|---|
| 点「批量预测」 | `POST /governance/model/batch-predict`（`modelType=CHURN`） | `frontend/meiyetec-web/src/views/dev/LtvTraining.vue:584` `runBatchPredict()`（前端目前固定传 `'LTV'`，`CHURN` 需手改参数或后端默认分支） |
| 点「计算流失」 | `POST /ltv-churn/churn/compute` | `frontend/meiyetec-web/src/views/dev/P2Suite.vue:447` `runChurnCompute()`（按钮「跑 Churn」） |
| 直接调 API | 两个端点均无「同一天只允许一种口径」的护栏 | — |

**复现条件**：同一租户、同一自然日，先执行 A 再执行 B（或反序执行 B 再执行 A —— 两个方向都会重复，因为 A 的 DELETE 只删自己那一批，B 从不删）。

> **注**：A 的 `DELETE` 条件是 `tenant_id + snapshot_date = CURDATE()`。
> 写入者 B 写入的 `snapshot_date` 也是当日（`LocalDate.now()`），
> 因此**若 B 在 A 之后执行**，B 的 489 行会幸存；若 **A 在 B 之后执行**，
> A 的 DELETE 会把 B 的当日行一并删掉（B 刚写完就被抹掉）→ 表现为「B 的数据丢失」而非重复。
> 两种顺序都是缺陷：**顺序一 → 数据重复；顺序二 → 数据被静默覆盖**。

---

## 4. 修复方向（二选一，待业务决策）

### 方案 A：`computeChurn` 也改为先删后插

- 在 `computeChurn` 的写入循环外层加 `DELETE FROM crm_churn_prediction WHERE tenant_id = ? AND snapshot_date = ?`，
  与 `batchPredictChurn` 的 `executeIdempotent` 对齐。
- **优点**：保留两个入口的能力，改动面小，与 A 行为一致。
- **代价 / 需决策**：
  1. **语义覆盖**——两个入口算出的口径不同（§1.3），谁后跑谁覆盖，**看板数值会随用户点击顺序抖动**。业务需明确「谁是权威口径」。
  2. **精度回退**——B 的 `setScale(2)` 会把 `decimal(5,4)` 的精度截到 2 位，覆盖 A 的 4 位值。
  3. **阈值边界**——B 用 `>0.66` / `>0.33`，A 用 `>=0.66` / `>=0.33`，恰好等于阈值的会员会落在不同等级。
  4. `computeChurn` 的 `@Transactional` 与 `executeIdempotent` 的 `TransactionTemplate` 需协调，避免嵌套事务语义不清。
- **配套建议**：同时把 B 的 `churn_score` 改为 4 位小数、阈值改为 `>=`，与 A 对齐；
  修正 `setTenureDays(s.getLastPurchaseDays())` 的疑似复制粘贴错误。

### 方案 B：`computeChurn` 改为只读（只返回计算结果，不落库）

- `computeChurn` 只计算并把结果放进响应 `data`，**不写 `crm_churn_prediction`**；
  表内数据统一由 `batchPredictChurn` 独占写入。
- **优点**：单写入者，从根上消除重复与「谁覆盖谁」的不确定性；B 的口径若作为最终口径，可反向合并进 A。
- **代价 / 需决策**：
  1. `P2Suite.vue` 的「跑 Churn」按钮（L447）语义变成「试算不落库」，前端需改文案与后续刷新逻辑；
  2. `GET /ltv-churn/churn/member/{id}` 与 `churn/risk-list` 只看得到 A 的口径，B 的历史行为消失；
  3. B 目前是**唯一消费 `crm_ltv_snapshot` 快照口径**的写入者 —— 弃用它等于放弃「基于 LTV 快照反推流失」的这条链路，需确认该链路是否还有业务价值。

### 方案 A/B 之外的补充动作（两个方案都需要）

| # | 动作 | 说明 |
|---|---|---|
| 1 | `GET /ltv-churn/dashboard` 的 Churn 统计加 `snapshot_date` 过滤 | 当前全表计数，任何历史重复都会永久污染看板 |
| 2 | `GET /intel/churn/overview` 的统计口径统一 | 现按 `HIGH/MEDIUM/LOW/SAFE` 统计，而两个写入者都写 `HIGH/MED/LOW` → `mediumCount/lowCount/safeCount` **恒为 0**（实测 `mediumCount=0, lowCount=0, safeCount=0`），HIGH 之外的风险分布**在任何写入路径下都看不到**。这是与本 issue 同表的独立枚举漂移缺陷，未单独登记。 |
| 3 | 加唯一约束 | `crm_churn_prediction` 加 `UNIQUE (tenant_id, member_id, snapshot_date)`，让重复在数据库层直接失败（需先清理存量重复行） |
| 4 | 单会员查询口径 | `churn/member/{id}` 的 `orderByDesc(id) LIMIT 1` 在有重复时不确定，需与唯一约束方案配套 |

---

## 5. 相邻观察：同表第三个写入者（未单独登记）

`ChurnPredictionService.predictForMember`（`backend/src/main/java/com/meiyetec/service/ChurnPredictionService.java:162`，
`GET /intel/churn/predict/{memberId}` 与 `POST /intel/churn/batch/predict`）**也写 `crm_churn_prediction`**，
且**同样无先删后插**（`predMapper.insert(p)`，L235），是同一缺陷类的第三个写入者。

本 issue 之所以未把它列为主缺陷：当前它**不产生数据** —— `ai_churn_model` 表无 `is_active=1 AND enabled=1` 的记录，
调用直接短路：

```
GET /intel/churn/predict/1 => {"code":404,"message":"资源不存在: 未配置激活的流失模型"}
SELECT COUNT(*) FROM ai_churn_model WHERE is_active=1 AND enabled=1 => 0
```

一旦模型被激活，它会用**第四套枚举**（`HIGH/MEDIUM/LOW/SAFE`，见 `riskLevel`）和 4 位小数的 `churn_score`
写入同一张表，与 A/B 的 `HIGH/MED/LOW` **互不可比**。
建议在修复 A/B 时一并处理其幂等策略与枚举口径，避免届时再开一个 issue。

---

## 5.5 方案选定（2026-11-13 决策）

> **状态变更**：修复方案已从「待业务拍板」收敛为 **方案 A**。
> **本轮不修** —— 修复需要重建镜像并做运行时验证，混在当前收尾批次里风险过高。

### 选定结果

**选定方案 A：`computeChurn` 也改为先删后插。**

| 项 | 内容 |
|---|---|
| 选定方案 | 方案 A（`computeChurn` 在写入循环外层加 `DELETE ... WHERE tenant_id = ? AND snapshot_date = ?`，与 `batchPredictChurn` 的 `executeIdempotent` 对齐） |
| 决策依据 | **工程最优解（非业务拍板）** —— 见下方三条 |
| 决策日期 | 2026-11-13 |
| 实施批次 | **下一轮单独批次**（本轮仅登记，不改业务代码） |
| 状态 | 仍为 **Open**（修复未实施） |

### 决策依据（工程最优解）

1. **改动面最小、风险最低** —— 方案 B 要把 `computeChurn` 降级为只读，会连带改动前端 `P2Suite.vue` 的「跑 Churn」按钮语义（L447）、
   单会员查询与 Top-N 榜单的数据口径，以及「基于 LTV 快照反推流失」这条链路的存废判断 —— 涉及产品语义决策，
   而方案 A 是纯后端幂等语义对齐，**不改变任何对外可见的业务能力**。
2. **保留双入口能力** —— 方案 A 让两个入口的行为一致（都可写、可刷新看板），
   消除了 §3 的「顺序一 → 数据重复；顺序二 → 数据被静默覆盖」两种失败模式，
   而方案 B 会让 `computeChurn` 丧失落库能力，现有调用方的写后读假设被打破。
3. **与配套动作天然衔接** —— §4 补充动作 #1（dashboard 加 `snapshot_date` 过滤）、
   #3（加 `UNIQUE (tenant_id, member_id, snapshot_date)` 唯一约束）、
   #4（单会员查询口径）在方案 A 下都成立；若采用方案 B，#3/#4 的紧迫性下降，
   但 #2（`/intel/churn/overview` 的 `HIGH/MEDIUM/LOW/SAFE` 与写入者 `HIGH/MED/LOW` 枚举漂移）
   仍需单独处理 —— 两条路线都不会顺带解决枚举漂移，因此该选择不构成决定性差异。

### 实施时的配套要求（下一轮必须一并完成）

| # | 动作 | 来源 |
|---|---|---|
| 1 | `computeChurn` 加先删后插，与 `batchPredictChurn` 幂等语义对齐 | §4 方案 A |
| 2 | `churn_score` 精度由 `setScale(2)` 改为 4 位小数 | §4 方案 A 代价 #2 |
| 3 | 风险阈值由 `>` 改为 `>=`（与 A 路径一致） | §4 方案 A 代价 #3 |
| 4 | 修正 `setTenureDays(s.getLastPurchaseDays())` 的疑似复制粘贴错误 | §4 方案 A 配套建议 |
| 5 | `GET /ltv-churn/dashboard` 统计加 `snapshot_date` 过滤 | §4 补充动作 #1 |
| 6 | `GET /intel/churn/overview` 枚举口径统一（`HIGH/MED/LOW` vs `HIGH/MEDIUM/LOW/SAFE`） | §4 补充动作 #2 |
| 7 | `crm_churn_prediction` 加 `UNIQUE (tenant_id, member_id, snapshot_date)`，先清理存量重复行 | §4 补充动作 #3 |
| 8 | 第三个写入者 `ChurnPredictionService.predictForMember` 的幂等策略与枚举对齐 | §5 相邻观察 |

> ⚠️ **仍未解决的口径问题（方案 A 不覆盖）**：A 与 B 的**计算口径不同**（数据源不同：`pos_order` 直算 vs `crm_ltv_snapshot` 快照），
> 谁后跑谁覆盖，**看板数值仍会随用户点击顺序抖动**。方案 A 只保证「不重复、不静默丢失」，
> **不保证「唯一权威口径」**。权威口径的确定需要业务拍板，仍列在 §6 未确认项。
> 同理，`crm_churn_prediction.monthly_freq` 的量纲漂移（见 I157 §6）也不在方案 A 覆盖范围内。

---

## 6. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 前端是否存在直接触发 `modelType=CHURN` 的按钮 | **未确认** —— `LtvTraining.vue:587` 固定传 `'LTV'`；`sys.ts:219` 的 `modelBatchPredict` 封装支持传参，但未找到 CHURN 调用点 |
| 生产环境 `crm_churn_prediction` 是否已有存量重复行 | **未确认** —— 本次仅在本地 Docker 验证；生产库不可访问 |
| `executeIdempotent` 在 `transactionManager == null` 时退化为非事务先删后插，在生产是否真实发生 | **未确认** —— 生产为 Spring Boot + `spring-boot-starter-jdbc`，通常存在 `DataSourceTransactionManager`；本地未观测到该分支被触发 |
| B 与 A 哪个口径是业务权威 | **未确认**（待业务决策） |
| `crm_ltv_snapshot` 与 `crm_churn_prediction` 的历史 `snapshot_date` 分布是否一致 | **未确认** —— 本地 `crm_ltv_snapshot` 有 3522 行、跨多个历史日期，且存在 5 组历史重复（`dup_groups=5`，分布在 2026-08-02 / 09-02 等），与本 issue 同源但成因不同 |

---

## 7. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`） |
| 应用 | 本地 `meiyetec-api` @ `localhost:8080`，管理员 `admin` |
| commit hash | `43bbcce`（`fix(ltv): 修复 batchPredictLtv/Churn 表名+量纲+幂等（I152 批 2-3）`） |
| DB 快照 | `crm_churn_prediction` 基线 489 行 / 489 会员 / `snapshot_date=2026-11-12`；复现态 978 行 / 489 组重复；清理后回到 489 行 / 0 组重复 |
| Evidence | `audit/evidence/F156-compute-churn-duplicate/raw/01-repro.txt`、`raw/02-db-state.txt`、`raw/03-cleanup.txt` |
| 环境记录 | `audit/evidence/env.md` |
| 取证时间 | 2026-11-12 23:25 – 23:48 |
| 关联 issue | I152、I155、I151 |
