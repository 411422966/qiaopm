# F159 — LTV/Churn 看板字段契约漂移修复（I162）

> **Feature ID**：F159（审计批次编号，对应 Issue **I162**）
> **证据日期**：2026-11-13（UTC+8）
> **核实 commit（修复前 HEAD）**：**`1be1e7c`** `docs(audit): 补登 I161 至 issue-register`
> **修复 commit**：`fix(ltv): 修复看板 LTV 字段契约漂移（I162）`
> **环境**：`audit/evidence/env.md`（Docker `meiyetec-api` :8080 / `meiyetec-web` :8088 / `meiyetec-mysql` :3307）
> **关联 Issue**：I162（P1，本目录）；同类历史：I159、I160
> **最终状态**：**VERIFIED**（API 层 + 浏览器层均已实测闭环）

---

## 1. 缺陷与修复概览

| 项 | 内容 |
|---|---|
| 缺陷 | `GET /ltv-churn/dashboard` 只返回 `totalLtv365d` / `avgLtv365d`；前端两处消费方读 `totalLtv` / `avgLtv` → `undefined` → `\|\| 0` 静默兜底 → 卡片显示 **¥0**（实际 6.76 亿） |
| 消费方 1 | `frontend/meiyetec-web/src/views/ltv-churn/index.vue:18,24` —— 「LTV 累计价值」「会员平均 LTV」卡片 |
| 消费方 2 | `frontend/meiyetec-web/src/views/dev/P2Suite.vue:88,89` —— 「总 LTV」「平均 LTV」统计 |
| 修复 | 后端 `LtvChurnController.dashboard()` **加契约别名**：`out.put("totalLtv", totalLtv)` / `out.put("avgLtv", avgLtv)`，保留 `365d` 原名，**两个前端消费方零改动** |
| 改动文件 | `backend/src/main/java/com/meiyetec/controller/LtvChurnController.java`（仅 1 个文件） |

---

## 2. 前后对比

### 2.1 API 层（L5b）

| 时点 | 响应体 | 证据 |
|---|---|---|
| 修复前 | `{"totalLtv365d":676125348.87,"avgLtv365d":191971.99,"membersWithLtv":489,...}` —— **无 `totalLtv` / `avgLtv`** | `raw/00-BEFORE-dashboard-4-accounts.txt`（F158 批次留存，commit `1d16059` 时点） |
| 修复后 | `{"totalLtv365d":676125348.87,"avgLtv365d":191971.99,"totalLtv":676125348.87,"avgLtv":191971.99,"membersWithLtv":489,...}` | `raw/01-AFTER-ltv-churn-dashboard.json` |

### 2.2 浏览器层（L5a / 浏览器实测）

`http://localhost:8088/ltv-churn`（admin 登录）—— 概览卡片实际渲染文本：

```
LTV 累计价值 | ¥676,125,349 ;; 会员平均 LTV | ¥191,972 ;; 高流失风险 | 393 ;; 中流失风险 | 96
```

截图：`raw/browser-ltv-churn-cards.png`

`http://localhost:8088/dev/p2-suite` → 「LTV / Churn」标签页 —— 统计值实际渲染文本：

```
总 LTV | 676,125,348 ;; 平均 LTV | 191971.99 ;; 高流失风险 | 393 ;; 中风险 | 96 ;; 低风险 | 0
```

截图：`raw/browser-p2suite-ltv-tab.png`

> ⚠️ P2Suite 首次进入时曾短暂显示 0：并发 10+ 请求下 `/ltv-churn/dashboard` 首次响应 > 10s，触发 axios `timeout: 10000` → 页面 `catch` 后 `ltvDash = {}`。
> 这是**独立的性能/超时问题**（同域另一缺陷），与字段名契约漂移无关；刷新后 1.4s 内正常返回（见 §5）。

---

## 3. 数据口径核对

`raw/02-db-snapshot-ltv-caliber.txt`：

```
tenant_id  snapshots  members  sum_ltv365d   avg_ltv365d
1          3522       489      676125348.87  191971.99
```

- 看板 `totalLtv` = `SUM(crm_ltv_snapshot.predicted_ltv_365d)` = 676,125,348.87 → **与 API / UI 完全一致** ✅
- `avgLtv` = `totalLtv / 3522`（**按快照行数**，非按会员数）= 191,971.99

> ⚠️ **同域口径发现（本轮未修，待决）**：`crm_ltv_snapshot` 存在 **3522 行快照 / 489 个会员**（同一会员多次 `POST /ltv-churn/ltv/compute` 每次追加行、不删旧行）。
> `dashboard()` 直接对**全部快照行**求和（`LtvChurnController.java:226-241`），而 Churn 侧 `computeChurn()` 是「取每会员最后一笔」去重（`LtvChurnController.java:172-177`）。
> ⇒ **同一看板内 LTV 侧按快照累加、Churn 侧按会员去重，两者口径不一致**，LTV 累计值存在**重复计数放大**（当前约 7.2 倍快照/会员）。
> 该口径问题**未在本轮修改**（超出用户决策 1 授权范围），已在 I162 §5 登记为待决项。

---

## 4. 验证等级

| 观测项 | 等级 | 证据 |
|---|---|---|
| `GET /ltv-churn/dashboard` 返回 `totalLtv` / `avgLtv` 且数值 = 676,125,348.87 / 191,971.99 | **L5b**（真实 HTTP 响应体） | `raw/01-AFTER-ltv-churn-dashboard.json` |
| `/ltv-churn` 页面卡片显示 ¥676,125,349 / ¥191,972 | **L5a**（浏览器实测 DOM 文本 + 截图） | `raw/browser-ltv-churn-cards.png` |
| `/dev/p2-suite` LTV/Churn 页签显示 676,125,348 / 191971.99 | **L5a**（同上） | `raw/browser-p2suite-ltv-tab.png` |
| 修复前后 API 响应差异 | **L5b** | `raw/00-BEFORE-…` vs `raw/01-AFTER-…` |
| 其余消费方（若有）是否也读 `totalLtv` | **UNVERIFIED** —— 已全仓 grep：`totalLtv` / `avgLtv` 仅上述 2 个前端消费方 + 后端局部变量 | 静态 grep |
| 生产环境部署 | **UNVERIFIED** —— 仅本地 Docker | — |
| 「LTV 累计值口径重复计数」是否被业务接受 | **未确认** —— 需业务决策 | 见 §3 |

---

## 5. 未确认 / 待决

| 项 | 状态 |
|---|---|
| `totalLtv` 是否应改为「按会员取最新快照求和」 | **未确认**（口径问题，见 §3；本轮只修字段名，不动口径） |
| P2Suite 首次进入并发请求导致 10s axios 超时 | **未确认** —— 现象已复现（performance resource timing 显示 `status=0, dur=10032`），但端点本身 1.4–3.5s 返回；属独立性能议题 |
| P2Suite「查询会员 LTV」面板读 `totalRevenue` / `orderCount` / `repurchaseRate` / `ltv`，而 `/ltv-churn/ltv/member/{id}` 返回 `CrmLtvSnapshot`（字段为 `historicalLtv` / `totalOrders` / `repurchaseProb30d` / `predictedLtv365d`） | **未确认（另一处契约漂移）** —— 本轮未修，已登记在 I162 §5 |

---

## 6. 证据清单

| 文件 | 内容 |
|---|---|
| `raw/00-BEFORE-dashboard-4-accounts.txt` | 修复前 `/ltv-churn/dashboard` 响应（4 账号，F158 批次留存） |
| `raw/01-AFTER-ltv-churn-dashboard.json` | 修复后 `/ltv-churn/dashboard` 响应体 |
| `raw/02-db-snapshot-ltv-caliber.txt` | `crm_ltv_snapshot` 口径核对（行数 / 会员数 / 求和） |
| `raw/browser-ltv-churn-cards.png` | `/ltv-churn` 概览卡片实测截图 |
| `raw/browser-p2suite-ltv-tab.png` | `/dev/p2-suite` LTV/Churn 页签实测截图 |
