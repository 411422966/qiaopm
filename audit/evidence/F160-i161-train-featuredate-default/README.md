# F160 — LTV/Churn 训练 featureDate 缺省值修复（I161 Layer 1）

> **Feature ID**：F160（审计批次编号，对应 Issue **I161** 的 Layer 1）
> **证据日期**：2026-11-13（UTC+8）
> **核实 commit（修复前 HEAD）**：**`1be1e7c`** `docs(audit): 补登 I161 至 issue-register`
> **修复 commit**：`fix(ltv): 修复训练全部模型按钮 featureDate 缺省值（I161 Layer 1）`
> **环境**：`audit/evidence/env.md`（Docker `meiyetec-api` :8080 / `meiyetec-mysql` :3307）
> **关联 Issue**：**I161**（Layer 1 已修 / Layer 2 转 **I163**）、**I155**（预测端不消费模型）、I154、I150–I158
> **最终状态**：**VERIFIED**（LTV + Churn 两端点 API 层实测闭环）；浏览器点击层 **UNVERIFIED**（见 §6）

---

## 1. 修复范围（仅 Layer 1）

| 层 | 内容 | 本轮 |
|---|---|---|
| **Layer 1** | 缺省 `featureDate` 取「该租户最新可用快照日」；前端训练按钮带上 `etlStats.featureDate`；错误信息带实际快照日与样本数 | ✅ **已修** |
| **Layer 2** | ETL 端点 `runEtl` 与 `computeLabels` 的 `now().minusDays(1)` 与「未来 90 天标签窗口」时序冲突 | ❌ **不修** → 转 **I163**（需业务决策标签窗口定义） |

---

## 2. 代码改动

| 文件 | 改动 |
|---|---|
| `backend/src/main/java/com/meiyetec/service/LtvTrainingService.java` | 缺省 `featureDate` → `resolveLatestFeatureDate(tenantId)`；新增私有方法取 `MAX(feature_date) WHERE tenant_id=? AND next_90d_revenue IS NOT NULL`，查不到回退 T-1；错误信息补 `featureDate=`；响应 `data` 补 `featureDate` |
| `backend/src/main/java/com/meiyetec/service/ChurnTrainingService.java` | 同上，标签列改 `next_90d_active IS NOT NULL`（Churn 样本同源于 `ltv_training_feature`） |
| `frontend/meiyetec-web/src/views/dev/LtvTraining.vue` | `trainBoth()` 把 `etlStats.value?.featureDate` 塞进 LTV / Churn 两个训练请求体（缺省时不塞 → 后端兜底） |

静态对比：`raw/06-before-after-static-diff.txt`

---

## 3. 运行时验证（L5b）

脚本：`raw/collect.ps1`（非正式取证工具）
输出：`raw/collect-summary.txt` / `raw/0*.json` / `raw/04-db-snapshot.txt`

### 3.1 修复前（I161 原始复现）

```
POST /intel/ltv/training/train   （不带 featureDate）→ 400 训练样本不足 100 条（当前 0）
POST /intel/churn/training/train （不带 featureDate）→ 400 样本不足（0条）
```

### 3.2 修复后（不带 featureDate）

```
POST /intel/ltv/training/train
  code=200  message=训练完成
  data.featureDate      = 2026-11-10        ← 后端自动解析为最新可用快照日
  data.trainingSamples  = 489
  data.conclusion       = GOOD
  data.durationMs       = 533

POST /intel/churn/training/train
  code=200  message=训练完成
  data.featureDate      = 2026-11-10
  data.trainingSamples  = 489
  data.positiveRate     = 100.0%
  data.conclusion       = POOR
```

原始响应：`raw/02-ltv-train-no-featuredate.json`、`raw/03-churn-train-no-featuredate.json`

### 3.3 DB 快照

```
--- ltv_training_feature 快照分布 ---
feature_date   cnt   labeled_rev   labeled_act
2026-11-10     489   489           489
```

```
--- ltv_model_registry（修复后）---
id  model_code             status  is_active  training_samples
8   LTV_MLR_1794575643     4       0          489     ← 本轮新建，已下线
7   LTV_MLR_1794575572     4       0          489     ← 本轮新建，已下线
6   LTV_MLR_1794571393     4       0          489     ← I161 复现遗留，已下线
...
--- churn_model_registry（修复后）---
id  model_code             status  is_active  training_samples
6   CHURN_LR_1794575645    4       0          489     ← 本轮新建，已下线
5   CHURN_LR_1794575573    4       0          489     ← 本轮新建，已下线
```

**副作用清理**：本轮两轮取证共创建 4 个模型行（ltv id=7/8、churn id=5/6），**全部已调用 `POST /intel/{ltv,churn}/training/model/{id}/offline` 下线**（`status=4`, `is_active=0`），未部署、未激活。
`ltv_training_run_log` / `churn_training_run_log` 的运行日志作为复现痕迹保留。

---

## 4. 前端产物验证（L5b）

容器 `meiyetec-web` 内产物 `assets/LtvTraining-CYnk_3vt.js` 已含 featureDate 透传逻辑：

```js
let e=E.value?.featureDate,t={algorithm:`MLR`,epochs:200,learningRate:.01},n={algorithm:`LR`,churnThreshold:90,horizon:90};
e&&(t.featureDate=e,n.featureDate=e);
```

原始 grep：`raw/05-frontend-artifact-grep.txt`

---

## 5. ⚠️ 修复后的新发现：模型指标退化（属 I163，未修）

修复后训练「成功」了，但 LTV 模型指标全为 0：

```
trainMAE=0.0000  trainRMSE=0.0000  trainMAPE=0.0000  trainR2=0.0000
valMAE=0.0000    valRMSE=0.0000    valMAPE=0.0000    valR2=0.0000
conclusion=GOOD          ← 退化常量模型被判定为 GOOD
```

Churn 侧同样退化：`positiveRate=100.0%`（全部样本同标签）、`valAUC=0.5`、`conclusion=POOR`。

**根因（不在本轮范围）**：`feature_date=2026-11-10` 的标签窗口是 **2026-11-11 ~ 2027-02-08**（未来 90 天），
该区间无实际消费数据 → `next_90d_revenue` 全为 0、`next_90d_active` 全为 0 → 标签是常量 → 模型学不到任何信号。

⇒ **「能训练成功」不等于「模型可用」**，这正是 I163（标签窗口与 ETL 时序冲突）要解决的业务问题。

---

## 6. 验证等级

| 观测项 | 等级 | 说明 |
|---|---|---|
| 不带 featureDate → LTV 训练 200 / 489 样本 / featureDate=2026-11-10 | **L5b** | `raw/02-ltv-train-no-featuredate.json` |
| 不带 featureDate → Churn 训练 200 / 489 样本 / featureDate=2026-11-10 | **L5b** | `raw/03-churn-train-no-featuredate.json` |
| `ltv_training_feature` 快照分布 | **L5b** | `raw/04-db-snapshot.txt` |
| 训练产生模型行已下线 | **L5b** | `raw/04-db-snapshot.txt`（status=4） |
| 前端产物含 featureDate 透传 | **L5b** | `raw/05-frontend-artifact-grep.txt` |
| 修复前后源码差异 | **L5b**（静态） | `raw/06-before-after-static-diff.txt` |
| **浏览器点击「训练全部模型」按钮的实际表现** | **UNVERIFIED** | 见下方说明 |
| 生产环境部署 | **UNVERIFIED** | 仅本地 Docker |

> **浏览器层为何未测**：「训练全部模型」按钮 `:disabled="!etlCompleted"`，而 `etlCompleted` 仅在**本会话跑过 ETL** 后置 true（`LtvTraining.vue:485`）。
> 点按钮必须先点「运行 ETL」→ `runEtl` 缺省 `featureDate = now().minusDays(1)` = **2026-11-12** → 会向 `ltv_training_feature` 写入一份
> **标签窗口未填满的退化快照**（489 行，`next_90d_revenue` 全 0），并因 `MAX(feature_date)` 规则**遮蔽**掉真正可用的 2026-11-10 快照。
> 该副作用属 **I163** 范畴且会污染环境数据，故**本轮主动不做浏览器点击**，改以「产物 grep + API 层等价路径」取证。

---

## 7. 证据清单

| 文件 | 内容 |
|---|---|
| `raw/collect.ps1` | 取证脚本（非正式工具，未接入 CI） |
| `raw/collect-summary.txt` | 本轮取证摘要 |
| `raw/01-ltv-churn-dashboard.json` | 修复后 `/ltv-churn/dashboard` 响应（I162 共用） |
| `raw/02-ltv-train-no-featuredate.json` | LTV 训练（不带 featureDate）原始响应 |
| `raw/03-churn-train-no-featuredate.json` | Churn 训练（不带 featureDate）原始响应 |
| `raw/04-db-snapshot.txt` | 训练后 DB 快照（快照分布 + 两张模型注册表 + crm_ltv_snapshot 口径） |
| `raw/05-frontend-artifact-grep.txt` | 前端容器产物 grep |
| `raw/06-before-after-static-diff.txt` | 修复前后源码静态对比 |
