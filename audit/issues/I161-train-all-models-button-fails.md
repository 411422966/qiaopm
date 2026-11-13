# I161 — 「训练全部模型」按钮不传 featureDate，训练样本不足返回 400

> **状态**：Open（已登记，本轮不修 —— 用户明确决策「新发现，不本轮修」）
> **严重度**：**P1**（核心运维动作不可用，无降级路径）
> **发现日期**：2026-11-13（F158 批次中复核 LTV 训练链路时发现）
> **登记人**：Cursor
> **关联**：**I142**（LTV 训练 ETL 全链路引用不存在的表）、**I152**（`crm_order` / `crm_order_item` 引用外溢）
> **证据**：`audit/evidence/I161-train-featuredate-mismatch/`
> **受影响功能**：F152（LTV 训练 ETL 修复）、F153（LTV 训练链路修复）

---

## 0. 一句话描述

前端「训练全部模型」按钮（`views/dev/LtvTraining.vue` 的 `trainBoth()`）调 LTV / Churn 训练端点时**不传 `featureDate`**，
后端缺省取「昨天」（`LocalDate.now().minusDays(1)`），
而 `ltv_training_feature` 表里**最新的可用快照是 2026-11-10**（比昨天早 2 天），
于是查询命中 **0 行**，两端点均返回 **HTTP 400「样本不足」**。

**即：只要 ETL 快照不是「昨天」，「训练全部模型」按钮 100% 失败。**

---

## 1. 缺陷位置

### 1.1 前端：不传 featureDate

| 文件 | 行 | 内容 |
|---|---|---|
| `frontend/meiyetec-web/src/views/dev/LtvTraining.vue` | 9 | `<el-button type="success" @click="trainBoth" :loading="trainLoading" :disabled="!etlCompleted">训练全部模型</el-button>` |
| `frontend/meiyetec-web/src/views/dev/LtvTraining.vue` | 499-505 | `trainBoth()` |

```js
async function trainBoth() {
  trainLoading.value = true;
  // ...
  await Promise.allSettled([
    sysApi.request({ method: 'POST', url: '/intel/ltv/training/train', data: { algorithm: 'MLR', epochs: 200, learningRate: 0.01 } }),
    sysApi.request({ method: 'POST', url: '/intel/churn/training/train', data: { algorithm: 'LR', churnThreshold: 90, horizon: 90 } })
  ]);
}
```

两个请求体里**都没有 `featureDate`**。

> 页面里其实**已经有**这个值：`etlStats.featureDate`（L36 `<el-descriptions-item label="特征快照">{{ etlStats.featureDate }}</el-descriptions-item>`），
> 即 ETL 刚跑完的快照日期 —— 但 `trainBoth()` **没有把它带上**。
> 按钮的 `:disabled="!etlCompleted"` 只保证「ETL 跑过」，不保证「ETL 跑的是哪天」。

### 1.2 后端：缺省取昨天

| 文件 | 行 | 内容 |
|---|---|---|
| `backend/.../service/LtvTrainingService.java` | 320-322 | `LocalDate featureDate = body == null \|\| body.get("featureDate") == null ? LocalDate.now().minusDays(1) : LocalDate.parse(body.get("featureDate").toString());` |
| `backend/.../service/ChurnTrainingService.java` | 66-68 | 同上（Churn 侧同样缺省取昨天） |

样本查询（`LtvTrainingService.java:333-337`）：

```sql
SELECT <FEATURE_COLUMNS>, next_90d_revenue AS label
FROM ltv_training_feature
WHERE tenant_id = ? AND feature_date = ? AND next_90d_revenue IS NOT NULL
```

`feature_date` **精确等值匹配**，没有「取最新可用」的兜底。

### 1.3 数据现状

```
SELECT feature_date, count(*), sum(next_90d_revenue IS NOT NULL)
FROM ltv_training_feature GROUP BY feature_date;

feature_date  cnt  labeled
2026-11-10    489  489
```

**只有 2026-11-10 一个快照**；今天是 2026-11-13，昨天 = **2026-11-12** → 命中 0 行。

> 为什么快照停在 2026-11-10：ETL 端点 `LtvTrainingService.java:73` 同样缺省取 `now().minusDays(1)`，
> 且标签窗口 `labelWindow = 90` 天（`computeLabels` 取 `featureDate+1 .. featureDate+90` 的未来数据），
> T-1 的快照天然攒不满 90 天标签 —— **ETL 本身也在造「昨天的空快照」**。
> 这是本 Issue 的第二层根因，修复方案需一并考虑（见 §3）。

---

## 2. 影响

| 维度 | 影响 |
|---|---|
| 功能 | **「训练全部模型」按钮 100% 失败**（两个端点都 400） |
| 数据 | 无脏数据写入（400 前置返回，不进训练逻辑） |
| 训练链路 | 无法产出新模型 → 预测端只能用旧模型 / 规则模型 `RULE-CHURN-V1`（与 **I155**「预测端不消费训练模型」叠加） |
| 前端表现 | `Promise.allSettled` 吞掉失败 → 用户只看到「完成」提示，实际两个模型都没训练出来 |
| 租户 | 全租户受影响（缺省逻辑与租户无关） |

---

## 3. 修复方向

### 方案 A：后端默认取「最新可用 feature_date」（推荐）

缺省 `featureDate` 时，改为查该租户 `ltv_training_feature` 的 `MAX(feature_date)`（且要求 `next_90d_revenue IS NOT NULL`），
而不是 `now().minusDays(1)`。

```sql
SELECT MAX(feature_date) FROM ltv_training_feature
WHERE tenant_id = ? AND next_90d_revenue IS NOT NULL
```

- 优点：前端不用改；ETL 跑完立刻能训；对「快照滞后」天然鲁棒。
- 注意：MAX 查到的日期若样本数 < 100 仍会 400 —— 建议错误信息里带上「最新可用快照日 = X，样本 N 条」，便于定位。
- 适用面：LTV 侧 `trainModel` + Churn 侧 `train`（Churn 样本表来源需单独确认，但同样要改缺省逻辑）。

### 方案 B：前端先调 ETL 再带日期训练

`trainBoth()` 里先 `GET`/复用 `etlStats.featureDate`，把它塞进两个训练请求体。

- 优点：语义最直白，调用方明确知道用哪天的数据。
- 缺点：**治标不治本** —— 若 ETL 本身跑出来的是空快照（§1.3 的标签窗口问题），前端带的日期照样是 0 样本；
  而且任何**其他**调用方（脚本、定时任务、外部系统）不传 `featureDate` 时依旧踩坑。

### 建议：方案 A 为主 + 方案 B 为辅

- 后端落地方案 A（消除缺省值陷阱）；
- 前端顺带把 `etlStats.featureDate` 带上（方案 B），让「用哪天的数据训练」在 UI 上可追溯；
- 同时排查 ETL 端点的 `now().minusDays(1)` 缺省值（同样应取「最近一个能凑满标签窗口的日期」），
  否则快照永远追不上「昨天」。

---

## 4. 验证证据

原始输出：`audit/evidence/I161-train-featuredate-mismatch/raw/01-train-featuredate-repro.txt`

### 4.1 复现（不带 featureDate）—— HTTP 400

```
--- POST /intel/ltv/training/train body=algorithm:MLR,epochs:200,learningRate:0.01 ---
{"code":400,"message":"请求参数错误: 训练样本不足 100 条（当前 0），请先运行 ETL 或积累更多历史数据",...}

--- POST /intel/churn/training/train body=algorithm:LR,churnThreshold:90,horizon:90 ---
{"code":400,"message":"请求参数错误: 样本不足（0条），需至少 100 条，请先运行 LTV ETL 积累数据",...}
```

### 4.2 对照（显式带 featureDate=2026-11-10）—— HTTP 200

```
--- POST /intel/ltv/training/train body 含 featureDate:2026-11-10 ---
{"code":200,"message":"训练完成","data":{"modelId":6,"modelCode":"LTV_MLR_1794571393","algorithm":"MLR",
 "trainingSamples":489,"trainMAE":0.0000,...,"durationMs":176,"conclusion":"GOOD"},...}
```

**结论：同一端点、同一算法，仅 `featureDate` 不同 → 400 vs 200。根因确认。**

### 4.3 副作用清理

测试过程共创建 3 个模型行（id=4/5/6），**已全部调用 `POST /intel/ltv/training/model/{id}/offline` 下线**（`status=4`）。
`ltv_training_run_log` 留下 id=11~16 共 6 条运行日志（训练开始 + 训练完成各 3 条），作为复现痕迹保留。

### 4.4 验证等级

| 项 | 等级 |
|---|---|
| 不带 featureDate → 400（两端点） | **L5b**（真实 HTTP 响应体） |
| 带 featureDate=2026-11-10 → 200 / 489 样本 | **L5b** |
| `ltv_training_feature` 快照分布（仅 2026-11-10） | **L5b**（DB 直查） |
| 前端按钮点击后的实际 UI 表现 | **UNVERIFIED**（浏览器层未实测；`Promise.allSettled` 吞错行为为代码级推断） |
| 修复后是否彻底解决 | **UNVERIFIED**（本轮不修） |

---

## 5. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| Churn 侧样本表来源与 `featureDate` 语义是否与 LTV 侧完全一致 | **未确认** —— Churn `loadSamples` 的 SQL 需单独核对（`ChurnTrainingService.java:376-387`） |
| ETL 端点 `now().minusDays(1)` 是否也是根因之一 | **未确认** —— §1.3 推断合理但未跑 ETL 实测 |
| 生产环境 `ltv_training_feature` 的快照日期分布 | **未确认** —— 本地仅 1 天快照，生产未查 |
| 前端 `trainBoth()` 失败时用户看到什么 | **未确认** —— 代码用了 `Promise.allSettled`，推测会误报「完成」，需浏览器实测 |

---

## 6. 取证方式

| 项 | 值 |
|---|---|
| 核实 commit | **`1dd9a0a`** `docs(audit): 登记 I159/I160 + F157 证据` |
| 源码依据 | `frontend/meiyetec-web/src/views/dev/LtvTraining.vue` L9 / L36 / L499-505；`backend/.../service/LtvTrainingService.java` L73 / L320-322 / L333-341；`backend/.../service/ChurnTrainingService.java` L66-68 / L77-80 / L376-387 |
| 运行时 | Docker `meiyetec-api`（:8080，healthy），2026-11-13 |
| DB 快照 | `ltv_training_feature` 1 个快照日（2026-11-10，489 条，489 条有标签）；`ltv_model_registry` 6 行 |
| 证据目录 | `audit/evidence/I161-train-featuredate-mismatch/` |
| 取证时间 | 2026-11-13 20:01 – 20:05 |
