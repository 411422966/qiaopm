# I170 — `computeLtv` 与 `batchPredictLtv` 双写 `crm_ltv_snapshot` 无互斥：`crm_ltv_snapshot` 今日 781 行，同一会员出现多条当日快照

> **状态**：Open
> **严重度**：P1（数据污染）—— 同一 `(member_id, snapshot_date)` 出现 2 条快照，看板 LTV 累计值重复计数
> **发现日期**：2026-11-14（I156 / I157 修复批次中，作为同源缺陷新登记）
> **登记人**：Cursor（I156 A3 修复批次 —— LTV 侧同源双写复核）
> **关联**：I156（`crm_churn_prediction` 侧同源双写，本轮已修）、I157（年化分母，本轮已修）、I152（批 2-3 引入 `batchPredictLtv` 的先删后插）
> **修复决策**：**待拍板** —— 建议按 I156 的 A3 模式（`computeLtv` 也加先删后插），但**本轮未修**

---

## 0. 一句话描述

`crm_ltv_snapshot` 与 `crm_churn_prediction` 是**完全同构的双写缺陷**，但 I156 那一侧已修、这一侧未修：

| 写入者 | 入口 | 幂等策略 | I156/I170 状态 |
|---|---|---|---|
| `DataGovernanceService.batchPredictLtv` | `POST /governance/model/batch-predict`（`modelType=LTV`） | **先删后插**（`DELETE ... WHERE tenant_id=? AND snapshot_date=CURDATE()`） | — |
| `LtvChurnController.computeLtv` | `POST /ltv-churn/ltv/compute` | **无任何清理**，逐会员 `INSERT` | **未修（本 issue）** |

`batchPredictLtv` 的「先删后插」只对自己幂等，**对 `computeLtv` 完全无效**；
而 `computeLtv` 从不删任何东西 → 同一天两个入口各跑一次，当日快照立刻翻倍。

---

## 1. 缺陷位置

### 1.1 写入者 A：`batchPredictLtv`（先删后插）

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/DataGovernanceService.java` |
| 方法 | `batchPredictLtv(Long tenantId, int limit)`（L525） |
| 入口 | `POST /governance/model/batch-predict`（`modelType=LTV`） |
| 幂等 | L599-602 `executeIdempotent("DELETE FROM crm_ltv_snapshot WHERE tenant_id = ? AND snapshot_date = CURDATE()", ...)` |
| 写入量 | 覆盖租户内**全部** `crm_member`（LEFT JOIN `pos_order`，无消费会员也写入） |
| `operator_name` | `'batch'` |

### 1.2 写入者 B：`computeLtv`（无清理，直接 INSERT）

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/controller/LtvChurnController.java` |
| 方法 | `computeLtv()`（L73），`@PostMapping("/ltv/compute")` |
| 输入 | `pos_order` 直查（`tenant_id=? AND deleted=0 AND status=2`），按 `member_id` 分组 |
| 写入 | `ltvMapper.insert(s)` —— **循环内直接 INSERT，无 DELETE、无 upsert** |
| 写入量 | 仅覆盖**有消费记录**的会员（本地实测 294 名，远少于 A 的 491 名） |
| `operator_name` | `'compute-job'` |
| 事务 | `@Transactional(rollbackFor = Exception.class)` —— 只保证本次调用自身原子，**不解决跨调用重复** |

> ⚠️ **两个写入者的会员覆盖范围不同**（A 覆盖全部会员，B 只覆盖有消费会员），
> 因此这不是「同一条被写两遍」，而是「**同一批会员被写两遍 + 另一批只有一条**」——
> 重复只发生在有消费会员上，无消费会员只有 1 条。

---

## 2. 运行时复现证据

**环境**：本地 Docker（`meiyetec-mysql` / `meiyetec-api`），租户 1
**Evidence 目录**：`audit/evidence/F165-compute-churn-mutex/raw/02-after-i156-i157.txt`、`raw/03-cleanup.txt`

### 2.1 修复后实测（I157 修复批次，2026-11-14）

I157 修复过程中，两条路径在同一自然日先后执行后的当日快照状态：

```
=== I157-A: POST /governance/model/batch-predict (modelType=LTV) ===
RESP: code=200 predicted=491
=== I157-B: POST /ltv-churn/ltv/compute ===
RESP: code=200 members=294 snapshots=294
```

清理阶段实测当日状态：

```
ltv_today             781        ← 491 (batch) + 290 (compute-job)
ltv_total             4595
ltv_dates              21        ← 历史快照日期数不变
```

其中 `computeLtv` 侧写入 294 行中包含 4 行夹具会员（990001 / 990002），
清理夹具后当日为 781 行 = 491 (batch) + 290 (compute-job)。

### 2.2 重复的分布

同一会员在 `crm_ltv_snapshot` 中当日出现 2 条（一条 `operator_name='batch'`，一条 `operator_name='compute-job'`）。
由于两者的 `model_type` 硬编码同为 `'RULE'`、`model_version` 同为 `'v1'`（或注册表版本），
**表内没有任何字段可以区分「重复写入的两条」与「两次正常的历史快照」** ——
只有 `operator_name` 不同，而该列不是业务语义字段。

### 2.3 影响面

| 接口 | 影响 |
|---|---|
| `GET /ltv-churn/dashboard`（`totalLtv365d` / `avgLtv365d`） | 按 `crm_ltv_snapshot` 全表求和 / 求均值 → **有消费会员的 LTV 被计入两次** |
| `GET /ltv-churn/ltv/member/{memberId}` | 按 `orderByDesc(id) LIMIT 1` 取最新 → 返回哪条取决于调用顺序，**结果不确定** |
| `GET /ltv-churn/ltv/top?limit=N` | 按 LTV 倒序取 Top N → 同一会员可**同时占据两条** |
| `GET /ltv-churn/churn/compute` 的输入 | 从 `crm_ltv_snapshot` 全量按 `orderByDesc(id)` `putIfAbsent` 取每会员最后一条 → 重复行不影响取值（取最新），**该链路暂未被污染** |

> 本地库 `crm_ltv_snapshot` 21 个历史日期中还存在 5 组历史重复（`dup_groups=5`，分布在 2026-08-02 / 09-02 等），
> 成因与本 issue 同源但发生在更早批次，**本轮未追溯处理**（清理只针对今日测试数据）。

---

## 3. 触发路径与前置条件

| 触发方式 | 入口 | 前端位置 |
|---|---|---|
| 点「批量预测」 | `POST /governance/model/batch-predict`（`modelType=LTV`） | `frontend/meiyetec-web/src/views/dev/LtvTraining.vue:584` `runBatchPredict()` |
| 点「计算 LTV」 | `POST /ltv-churn/ltv/compute` | `frontend/meiyetec-web/src/views/dev/P2Suite.vue`（LTV 计算按钮） |

**复现条件**：同一租户、同一自然日，两个入口各执行一次 → 当日快照翻倍（限于有消费会员）。

---

## 4. 修复方向（建议按 I156 的 A3 模式）

**建议方案（待拍板）：`computeLtv` 也改为先删后插，删除条件 `tenant_id + snapshot_date = CURDATE()`，不按 `model_code`/`operator_name` 收窄。**

| 项 | 说明 |
|---|---|
| 与 I156 的关系 | **完全同构** —— I156 已于 2026-11-14 按 A3 模式修复 `computeChurn`（先删后插 + `model_code='COMPUTE-CHURN-V1'`），本 issue 是 LTV 侧的镜像 |
| 优点 | 改动面最小；谁后跑谁覆盖；与 batch 侧幂等语义对齐；不新增 DDL、不改前端 |
| 需一并考虑 | ① `crm_ltv_snapshot` **无 `model_code` 列**（只有 `model_type` / `model_version` / `operator_name`）→ 若要像 I156 那样区分写入者，需要**复用 `model_type`**（当前两边都硬编码 `'RULE'`，改为 `'RULE'` / `'COMPUTE'` 之类）或**依赖 `operator_name`**；② `model_type` 是训练链路的输入字段（`ltv_training_feature`），改值需评估训练侧影响 |
| 已知代价 | 与 I156 相同：**谁后跑谁覆盖，看板数值随点击顺序抖动**（两个写入者计算口径不同：`batchPredictLtv` 走 `crm_member` LEFT JOIN，`computeLtv` 走 `pos_order` 直查），**权威口径仍待业务拍板** |
| 未纳入本轮 | ① 唯一约束（`crm_ltv_snapshot` 加 `UNIQUE (tenant_id, member_id, snapshot_date)`，需先清理存量重复行 + DDL）；② 看板加 `snapshot_date` 过滤；③ 5 组历史重复行的清理 |

---

## 5. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 生产环境 `crm_ltv_snapshot` 是否已有存量重复行 | **未确认** —— 本次仅在本地 Docker 验证；生产库不可访问 |
| `crm_ltv_snapshot.model_type` 是否可安全用于区分写入者 | **未确认** —— 未审阅 `LtvTrainingService` 对 `model_type` 的消费方式；改动前需确认训练链路不受影响 |
| 两个 LTV 写入者哪个口径是业务权威 | **未确认**（与 I156 同类问题，待业务拍板） |
| 历史 21 个日期中的 5 组历史重复行的成因 | **未确认** —— 本轮未追溯 |

---

## 6. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ 本地 Docker `meiyetec-mysql`，租户 1 |
| 应用 | 本地 `meiyetec-api` @ `localhost:8080`（镜像 `meiyetec/api:dev`，已重建并 healthy） |
| commit hash | 见 `git log`（本批次提交：`fix(ltv): computeChurn 先删后插（I156 A3）+ 年化分母下限 30 天（I157）`） |
| DB 快照 | `crm_ltv_snapshot` 总 4595 行 / 21 个日期 / 今日 781 行（491 batch + 290 compute-job）；`crm_churn_prediction` 总 1527 行 / 今日 489 行 / dup 组 0 |
| Evidence | `audit/evidence/F165-compute-churn-mutex/raw/02-after-i156-i157.txt`、`raw/03-cleanup.txt` |
| 关联 issue | I156、I157、I152 |
| 取证时间 | 2026-11-14 17:45 – 17:55 |
