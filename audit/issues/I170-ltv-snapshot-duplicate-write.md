# I170 — `crm_ltv_snapshot` 多写入者无互斥：今日 781 行（489 batch + 292 compute-job），看板 LTV 全表求和虚高 9.4 倍

> **状态**：**Partially Fixed**（2026-11-14 本批修 3 项无争议项；A3 核心 —— `computeLtv` 加 DELETE —— **暂缓**，等 I172 业务拍板）
> **严重度**：P1（数据污染）—— 同一 `(member_id, snapshot_date)` 出现 2 条快照，看板 LTV 累计值重复计数
> **发现日期**：2026-11-14（I156 / I157 修复批次中，作为同源缺陷新登记）
> **登记人**：Cursor（I156 A3 修复批次 —— LTV 侧同源双写复核）
> **关联**：I156（`crm_churn_prediction` 侧同源双写，**已修**）、I157（年化分母，**已修**）、I152（批 2-3 引入 `batchPredictLtv` 的先删后插）、**I172 / I173 / I174 / I175**（本轮拆出的 4 个待业务决策项）
> **修复决策**：**部分修复** —— 详见 §7「本轮已修」与 §8「本轮暂缓」

---

## 0. 一句话描述

`crm_ltv_snapshot` 存在 **3 个写入者**，彼此**没有任何互斥**：

| 写入者 | 入口 | 幂等策略 | `model_type` | `operator_name` | 会员覆盖范围 | I170 状态 |
|---|---|---|---|---|---|---|
| A `DataGovernanceService.batchPredictLtv` | `POST /governance/model/batch-predict`（`modelType=LTV`） | **先删后插**（`DELETE ... WHERE tenant_id=? AND snapshot_date=CURDATE()`，L600） | `'RULE'` | `'batch'` | 租户内**全部** `crm_member`（本地实测 **489**） | 本身幂等，但对 B 无效 |
| B `LtvChurnController.computeLtv` | `POST /ltv-churn/ltv/compute` | **无任何清理**，逐会员 `INSERT`（L141） | `'RULE'` | `'compute-job'` | 仅**有消费记录**的会员（本地实测 **292**） | **未修（A3 暂缓 → I172）** |
| C `LtvService.computeForMember` | `GET /intel/ltv/compute/{memberId}`（+ `batchCompute` 批量包装） | **无任何清理**（本轮已修为**单会员**先删后插，L131） | `'BG'` | `'online'` | 单会员（批量时循环全部会员） | **已修（单会员幂等）** |

A 的「先删后插」只对自己幂等，**对 B / C 完全无效**；
B 与 C 从不删任何东西 → 同一天多个入口各跑一次，当日快照立刻重复。

> ⚠️ **三个写入者的 `model_type` 只有两种取值**（A/B 都是 `'RULE'`，C 是 `'BG'`），
> **表内没有任何字段可以区分「B 写的」与「A 写的」** —— 只有 `operator_name` 不同，而该列不是业务语义字段。

---

## 1. 缺陷位置

### 1.1 写入者 A：`batchPredictLtv`（先删后插）

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/DataGovernanceService.java` |
| 方法 | `batchPredictLtv(Long tenantId, int limit)`（L525） |
| 入口 | `POST /governance/model/batch-predict`（`modelType=LTV`） |
| 幂等 | L599-602 `executeIdempotent("DELETE FROM crm_ltv_snapshot WHERE tenant_id = ? AND snapshot_date = CURDATE()", ...)` |
| 写入量 | 覆盖租户内**全部** `crm_member`（LEFT JOIN `pos_order`，**无消费会员也写入**） |
| `operator_name` | `'batch'` |

### 1.2 写入者 B：`computeLtv`（无清理，直接 INSERT）

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/controller/LtvChurnController.java` |
| 方法 | `computeLtv()`（L75），`@PostMapping("/ltv/compute")` |
| 输入 | `pos_order` 直查（`tenant_id=? AND deleted=0 AND status=2`），按 `member_id` 分组 |
| 写入 | `ltvMapper.insert(s)`（L141）—— **循环内直接 INSERT，无 DELETE、无 upsert** |
| 写入量 | 仅覆盖**有消费记录**的会员（本地实测 **292** 名，**远少于** A 的 489 名） |
| `operator_name` | `'compute-job'` |
| 事务 | `@Transactional(rollbackFor = Exception.class)` —— 只保证本次调用自身原子，**不解决跨调用重复** |

### 1.3 写入者 C：`computeForMember` / `batchCompute`（无清理，本轮已修为单会员幂等）

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/LtvService.java` |
| 方法 | `computeForMember(Long memberId)`（L49，`@GetMapping("/compute/{memberId}")`）；`batchCompute`（L177，`@PostMapping("/batch/compute")`）**循环调用** `computeForMember` |
| 写入 | `ltvSnapshotMapper.insert(snap)`（L133）—— 修复前**每次调用都 INSERT 一行当日快照，无 DELETE** |
| `model_type` / `operator_name` | `'BG'` / `'online'`（与 A/B 的 `'RULE'` 可区分） |
| 触发方式 | ① 前端「重新计算」按钮：`frontend/meiyetec-web/src/views/intel/ltv.vue:693` `GET /intel/ltv/compute/{memberId}`；② `POST /intel/ltv/batch/compute` 批量重算（前端暂无入口） |
| 本轮修复 | 插入前先 `DELETE ... WHERE tenant_id=? AND member_id=? AND snapshot_date=CURDATE()`（**只删该会员自己**，不影响其他会员） |

> ⚠️ **写入者 C 与 A/B 的关键差异**：C 走的是 **BG/NBD 简化模型**（`computeRepurchaseProb` sigmoid），
> 与 A/B 的 **RULE 年化模型**（`predictedLtv365d = AOV × 年化频次`）**算法完全不同** ——
> 因此 C 的行与 A/B 的行**不是同一口径的同一指标**，不能互相替代。

---

## 2. 运行时复现证据

**环境**：本地 Docker（`meiyetec-mysql` / `meiyetec-api`），租户 1
**Evidence 目录**：`audit/evidence/F166-ltv-snapshot-idempotency/`（本轮）、`audit/evidence/F165-compute-churn-mutex/raw/02-after-i156-i157.txt`（I157 批次）

### 2.1 当日快照构成（2026-11-14 23:0x 实测）

```sql
SELECT operator_name, model_type, COUNT(*) c
FROM crm_ltv_snapshot
WHERE tenant_id=1 AND snapshot_date=CURDATE()
GROUP BY operator_name, model_type;
```

| `operator_name` | `model_type` | 行数 | 写入者 |
|---|---|---|---|
| `batch` | `RULE` | **489** | A（`batchPredictLtv`） |
| `compute-job` | `RULE` | **292** | B（`computeLtv`） |
| 合计 | | **781** | |

> 📌 **数字修正（本轮）**：本 issue 初版写的是「781 = 491 batch + 290 compute-job」——
> 那是 **I157 修复批次当时的快照值**（该批次结束后又跑过若干次 A/B，成员基数也发生了变化）。
> **当前实测（2026-11-14 23:0x）为 489 batch + 292 compute-job = 781 行**，与本轮验证窗口一致。
> 历史值保留在 `audit/evidence/F165-compute-churn-mutex/raw/02-after-i156-i157.txt`（I157 批次证据），不再作为结论引用。

> 📌 **修复 1 + 修复 2 落地后的当日快照构成（2026-11-14 23:24 实测，最终值）**：
>
> | `operator_name` | `model_type` | 行数 |
> |---|---|---|
> | `batch` | `RULE` | 488 |
> | `compute-job` | `RULE` | 291 |
> | `online` | `BG` | 1 |
> | **合计** | | **780** |
>
> 与上表相差 **−1 行** 的原因：修复 2（单会员幂等）在验证中把 **member 1** 当日的
> `batch` + `compute-job` 两行合并为 **1 行 `online` / `BG`**（见 §7 副作用说明）——
> 即 **789 → 780 中的 1 行**由「重复行」收敛为「单行」，**不是数据丢失**。
> 当日 distinct 会员数仍为 **549**（不受影响）。
> 当日 dup 组数由 **232 → 231**（减少的正是 member 1 这一组）。

同表按写入者全量分布（用于识别历史写入来源）：

| `operator_name` | `model_type` | 总行数 | 最后写入日 |
|---|---|---|---|
| `seed` | `BG` | 3016 | 2026-09-02 |
| `online` | `BG` | 17 | 2026-11-02 |
| `batch` | `RULE` | 978 | 2026-11-14 |
| `compute-job` | `RULE` | 584 | 2026-11-14 |

### 2.2 重复的分布

同一会员在 `crm_ltv_snapshot` 中当日出现 2 条（一条 `operator_name='batch'`，一条 `operator_name='compute-job'`）。
由于 A / B 的 `model_type` 硬编码同为 `'RULE'`、`model_version` 同为 `'v1'`（或注册表版本），
**表内没有任何字段可以区分「重复写入的两条」与「两次正常的历史快照」** ——
只有 `operator_name` 不同，而该列不是业务语义字段。

### 2.3 看板数字虚高（**本轮已修**）

`GET /ltv-churn/dashboard` 的 LTV 统计原本是**全表求和**（`ltvMapper.selectList(eq(tenantId, t))`，不加日期条件），
把 **21 个历史快照日**全部累加：

| 口径 | 行数 | `SUM(predicted_ltv_365d)` |
|---|---|---|
| 修复前（全表） | 4595（21 个日期） | **¥805,548,915.63** |
| 修复后（最新快照日，修复 2 落地前） | 781（2026-11-14） | **¥86,068,818.60** |
| 修复后（最新快照日，修复 2 落地后 · 最终值） | 780（2026-11-14） | **¥86,064,597.99** |
| 倍率 | | **9.36 倍** |

> ✅ **「从错误变正确」，不是数据丢失**：修复后前端 LTV 卡片由 ¥805,548,915 变为 ¥86,064,598，
> 减少的部分是**被重复计入的历史快照**，不是真实业务数据被删除。
> 同批次 `membersWithLtv` 由「全表 distinct 会员数」变为**最新快照日的 distinct 会员数 = 549**。

> 📌 **两次实测相差 ¥4,220.61 / 1 行的解释**：修复 1 与修复 2 在同一批次落地，
> 验证脚本执行「连调 `GET /intel/ltv/compute/1` 两次」时，member 1 当日的
> `batch`（≈¥4,222）与 `compute-job` 两行被**单会员 DELETE** 收敛为 1 行 `online` / `BG`（¥3,801.38），
> 净额 −¥4,220.61。**这是 member 1 单会员的重复行归一，与其余 548 个会员无关**，
> `membersWithLtv` 恒为 549 不变。
> 证据：`raw/01-dashboard-after-fix.txt`（781 行口径）、`raw/03-dashboard-after-idempotent-test.txt`（780 行口径，最终值）。

### 2.4 历史重复行的来源（**本轮新查明**）

全表历史重复组共 **5 组**（分布在 4 个日期），逐组拆解 `operator_name` 后：

| `snapshot_date` | `member_id` | 该会员该日全部行 | 重复来源 |
|---|---|---|---|
| 2026-09-02 | 1224 | `seed` × 1 + **`online` × 4** | seed + online 混合 |
| 2026-10-30 | 1 | **`online` × 3** | 纯 online |
| 2026-10-30 | 1548 | **`online` × 3** | 纯 online |
| 2026-10-31 | 1548 | **`online` × 3** | 纯 online |
| 2026-11-02 | 1548 | **`online` × 3** | 纯 online |

> ✅ **结论：5 组历史重复中至少 4 组来自写入者 C（`operator_name='online'`）**，
> 剩余 1 组（2026-09-02 / member 1224）是 `seed` + `online` 混合 ——
> 没有任何一组来自 A/B 组合。
> 换言之：**`online` 写入者不仅会制造当日重复，还会持续累积历史重复**（17 行 online 数据造成 4 组纯 online 重复）。
> 证据：`audit/evidence/F166-ltv-snapshot-idempotency/raw/05-db-snapshot-after-fix.txt`。

### 2.5 影响面

| 接口 | 影响 | 本轮状态 |
|---|---|---|
| `GET /ltv-churn/dashboard`（`totalLtv365d` / `avgLtv365d` / `membersWithLtv`） | 全表求和 / 求均值 → 数字虚高 9.4 倍 | ✅ **已修**（加 `snapshot_date = MAX(snapshot_date)` 过滤） |
| `GET /ltv-churn/ltv/member/{memberId}` | 按 `orderByDesc(id) LIMIT 1` 取最新 → 返回哪条取决于调用顺序，**结果不确定** | ⏳ 待 I172 拍板后统一口径 |
| `GET /ltv-churn/ltv/top?limit=N` | 按 LTV 倒序取 Top N → 同一会员可**同时占据两条** | ⏳ 同上 |
| `GET /intel/ltv/trend`（`LtvService.trend()`） | **按月 `SUM`** → 重复行会**放大历史趋势**（同月多条重复被累加） | ⏳ 待 I174 清理历史重复 |
| `GET /ltv-churn/churn/compute` 的输入 | 从 `crm_ltv_snapshot` 全量按 `orderByDesc(id)` `putIfAbsent` 取每会员最后一条 → 重复行不影响取值（取最新），**该链路未被污染** | — |
| `POST /intel/ltv/compute/{memberId}`（写入者 C） | 每调一次多插一行当日快照 | ✅ **已修**（单会员幂等） |
| `POST /intel/ltv/seed`（`mode=reset`） | `DELETE FROM crm_ltv_snapshot WHERE tenant_id = ?` **整租户清空** | ✅ **已修**（dev/test 环境守卫） |

---

## 3. 触发路径与前置条件

| 触发方式 | 入口 | 前端位置 |
|---|---|---|
| 点「批量预测」 | `POST /governance/model/batch-predict`（`modelType=LTV`） | `frontend/meiyetec-web/src/views/dev/LtvTraining.vue:584` `runBatchPredict()` |
| 点「计算 LTV」 | `POST /ltv-churn/ltv/compute` | `frontend/meiyetec-web/src/views/dev/P2Suite.vue`（LTV 计算按钮） |
| 点「重新计算」（单会员） | `GET /intel/ltv/compute/{memberId}` | `frontend/meiyetec-web/src/views/intel/ltv.vue:693` |

**复现条件**：同一租户、同一自然日，两个及以上入口各执行一次 → 当日快照翻倍（限于有消费会员）。

---

## 4. 修复方向

### 4.1 A3 模式（`computeLtv` 加先删后插）—— **本轮暂缓，见 §8**

**原建议**：按 I156 的 A3 模式，`computeLtv` 改为先删后插，删除条件 `tenant_id + snapshot_date = CURDATE()`，不按 `model_code`/`operator_name` 收窄。

| 项 | 说明 |
|---|---|
| 与 I156 的关系 | I156 已于 2026-11-14 按 A3 模式修复 `computeChurn`（先删后插 + `model_code='COMPUTE-CHURN-V1'`），本 issue 是 LTV 侧的镜像 |
| 优点 | 改动面最小；谁后跑谁覆盖；与 batch 侧幂等语义对齐；不新增 DDL、不改前端 |
| 需一并考虑 | ① `crm_ltv_snapshot` **无 `model_code` 列**（只有 `model_type` / `model_version` / `operator_name`）→ 若要像 I156 那样区分写入者，需要**复用 `model_type`**（当前两边都硬编码 `'RULE'`）或**依赖 `operator_name`**；② `model_type` 是训练链路的输入字段（`ltv_training_feature`），改值需评估训练侧影响 |
| 已知代价 | 与 I156 相同：**谁后跑谁覆盖，看板数值随点击顺序抖动**（两个写入者计算口径不同），**权威口径仍待业务拍板** |
| **LTV 侧特有的新风险** | **CHURN 侧与 LTV 侧的覆盖方向相反 —— A3 在 LTV 侧会「删掉 197 个会员的快照」**，详见 §8 |
| 未纳入本轮 | ① 唯一约束（`crm_ltv_snapshot` 加 `UNIQUE (tenant_id, member_id, snapshot_date)` → I175）；② 5 组历史重复行的清理 → I174 |

### 4.2 复购概率档位口径不一致 → I173

### 4.3 快照口径权威性（compute 覆盖 292 vs batch 覆盖 489）→ I172

---

## 5. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 生产环境 `crm_ltv_snapshot` 是否已有存量重复行 | **未确认** —— 本次仅在本地 Docker 验证；生产库不可访问 |
| `crm_ltv_snapshot.model_type` 是否可安全用于区分写入者 | **未确认** —— 未审阅 `LtvTrainingService` 对 `model_type` 的消费方式；改动前需确认训练链路不受影响 |
| 两个 LTV 写入者哪个口径是业务权威 | **未确认**（I172 待业务拍板） |
| 历史 21 个日期中的 5 组历史重复行的成因 | **已查明来源（§2.4：≥4 组来自 `online` 写入）**，但**清理动作未授权**（I174） |

---

## 6. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ 本地 Docker `meiyetec-mysql`（宿主 3307），租户 1 |
| 应用 | 本地 `meiyetec-api` @ `localhost:8080`（镜像 `meiyetec/api:dev`，本轮已重建并 healthy） |
| 代码 commit | `fix(ltv): dashboard 加 snapshot_date 过滤 + computeForMember 幂等 + seed 环境守卫（I170 部分）`（见 §7 证据目录内绑定值） |
| DB 快照（修复后，最终值） | `crm_ltv_snapshot` 总 **4594** 行 / 21 个日期 / 当日 **780** 行（488 batch + 291 compute-job + 1 online）/ 当日 distinct 会员 **549** / 当日 dup 组 **231** / 全表 dup 组 **236**；当日 `SUM(predicted_ltv_365d)` = **¥86,064,597.99**；全表 `SUM` = **¥805,544,695.02**；`crm_churn_prediction` 总 1527 行 |
| Evidence | `audit/evidence/F166-ltv-snapshot-idempotency/`（`raw/01-dashboard-after-fix.txt`、`raw/02-compute-formember-idempotent.txt`、`raw/03-dashboard-after-idempotent-test.txt`、`raw/04-seed-env-guard.txt`、`raw/05-db-snapshot-after-fix.txt`、`raw/06-verify-i170-partial.txt`、`raw/verify-i170-partial.ps1.txt`（非正式工具）） |
| 关联 issue | I156、I157、I152、**I172、I173、I174、I175** |
| 取证时间 | 2026-11-14 23:05 – 23:24（23:19 重建镜像 `meiyetec/api:dev`，23:24 完成 12/12 CHECK PASS 的复跑） |

---

## 7. 本轮已修（2026-11-14，3 项无争议项）

| # | 修复项 | 文件 / 位置 | 手法 | 运行时实测 |
|---|---|---|---|---|
| 1 | **看板 LTV 统计加 `snapshot_date` 过滤** | `LtvChurnController.dashboard()`（L227-236） | `selectList` 增加 `.apply("snapshot_date = (SELECT MAX(snapshot_date) FROM crm_ltv_snapshot WHERE tenant_id = {0})", t)` | `totalLtv365d` 由 **¥805,544,695.02** → **¥86,064,597.99**（= 当日 `SUM`，两者精确相等）；`membersWithLtv` = **549**（= 当日 distinct 会员数）；`avgLtv365d` = 110,339.23 |
| 2 | **写入者 C 单会员幂等** | `LtvService.computeForMember`（L131-133） | INSERT 前 `DELETE FROM crm_ltv_snapshot WHERE tenant_id=? AND member_id=? AND snapshot_date=CURDATE()`（**只删自己**） | 连调 `GET /intel/ltv/compute/1` 两次 → 该会员当日行数恒为 **1**（修复前为 2，且每次调用 +1）；两次返回值完全一致（`predictedLtv365d=3801.38`） |
| 3 | **种子重置环境守卫** | `LtvSeedController.seed`（L50）+ `assertSeedEnvironmentAllowed`（L89-104） | 仅 `spring.profiles.active` 含 `dev` / `test` 放行；否则抛 `BusinessException(FORBIDDEN)` → HTTP 403 | `POST /intel/ltv/seed {"mode":"reset"}` 在 prod profile 下返回 **HTTP 403** `生产环境禁止执行种子重置`；租户数据行数前后不变（snap 4594 / mem 489 / ord 1848 → 4594 / 489 / 1848） |

> ⚠️ **修复 2 的副作用（须知悉）**：单会员 DELETE 会把**该会员当日由其他写入者（A/B）产生的行一并清掉** ——
> 即「该会员当日快照以 C 的口径为准」。实测 member 1 的 2 行（batch + compute-job）在调用 C 后变为 1 行（online / BG）。
> 这是「单会员幂等」的必然结果，与 A3 的「全表删当日」语义不同，且不涉及其他会员。

> ⚠️ **修复 3 的守卫范围说明**：守卫拦截的是**整个 `/intel/ltv/seed` 端点**（`mode=reset` 与 `mode=full` 都拦），
> 不是只拦 `reset`。这是有意为之 —— `mode=full` 同样会**写库造会员/订单/快照**，
> 在 prod 环境下执行同样是污染真实租户数据的风险。
> 守卫读取的是 `spring.profiles.active`（容器内 `SPRING_PROFILES_ACTIVE=prod`），
> **若该环境变量被误设为空**，兜底值为 `prod`（仍然拦截），属 fail-safe。

### 7.1 本批新发现（**未落盘为独立 issue，待用户决定**）

实施修复 3 时发现 `LtvSeedController` 的 `mode=reset` 分支存在**独立的表引用错误**：

| 项 | 内容 |
|---|---|
| 位置 | `LtvSeedController.seed()` —— `jdbc.update("DELETE FROM crm_order WHERE tenant_id = ?", tenantId)` |
| 事实 | 本地库 `meiyetec` 中**不存在 `crm_order` 表**（`SHOW TABLES LIKE '%order%'` 返回 `biz_bnpl_order` / `biz_huabei_order` / `crm_package_order` / `dianping_order` / `ecommerce_order` / `lbs_service_order` / `mkt_order_discount` / `pay_dcep_order` / `platform_order` / `point_order` / `pos_order` / `pos_order_consumption` / `pos_order_item` / `wx_pay_order`，**无 `crm_order`**） |
| 同文件的生成逻辑 | `generateDemoMembersAndOrders()` 写入的是 **`pos_order`**（不是 `crm_order`）→ reset 删的表与生成写的表**不一致** |
| 后果 | 在 **dev / test** profile 下放开守卫后，`mode=reset` 会**先成功删除 `crm_ltv_snapshot` 全表**，随后在 `crm_order` 上抛 `SQLException` → HTTP 500，**留下「快照已清空、订单未清、会员未清」的半破坏状态** |
| 关联历史 | I152 —— `crm_order` / `crm_order_item` 引用外泄（仓内 13 个文件、21 处 SQL 指向不存在的表） |
| 本批处理 | **未改代码**（超出本批授权范围：任务 3 仅要求加环境守卫） |
| 建议 | ① 最小修复：`crm_order` → `pos_order`；② 更稳：把三条 DELETE 包在 `@Transactional(rollbackFor = Exception.class)` 内，避免半破坏；③ 校验 `DELETE` 与 `generateDemoMembersAndOrders` 的表集合一致 |

> 🔒 **本项不在本批授权范围**，已报告待用户决定是否单独立项（建议 **I176**）。
> 注意：由于守卫已生效，**生产环境不受影响**（在进入 DELETE 之前即被 403 拦下）。

---

## 8. 本轮暂缓：A3 核心（`computeLtv` 加 DELETE）

**暂缓原因（业务口径决策，不能技术单方面拍板）**：

| 维度 | CHURN 侧（I156 已按 A3 修） | LTV 侧（本 issue） | 结论 |
|---|---|---|---|
| `batch` 覆盖会员数 | 491 | **489** | 基准 |
| `compute` 覆盖会员数 | **551（超集）** | **292（子集）** | **方向相反** |
| A3「先删当日全表、再插 compute 结果」的净效果 | 覆盖全部 551 个会员，**不丢数据** | 只剩 292 个会员，**197 个无消费会员的当日快照消失** | **LTV 侧不可照搬** |

- CHURN 侧的 `computeChurn` 输入是 `crm_ltv_snapshot` 的**全部历史行**按会员去重（`LtvChurnController.java:164-170`），
  覆盖**所有有 LTV 快照的会员**，是 `batch` 的**超集** → A3 安全。
- LTV 侧的 `computeLtv` 输入是 `pos_order` 中 `status=2` 的**有消费会员**（292），
  是 `batch`（全部 `crm_member`，489）的**子集** → A3 会让 197 个无消费会员的当日快照**直接消失**。

> 🔒 **等待 I172 业务拍板**：LTV 快照的权威口径是 A（`batch`，覆盖全部会员）还是 B（`compute`，仅覆盖有消费会员）？
> 在此之前，**本批不动 `computeLtv` 的写入逻辑**。
