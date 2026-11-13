# I162 — LTV/Churn 看板字段契约漂移：前端读 `totalLtv`/`avgLtv`，后端返 `totalLtv365d`/`avgLtv365d`

> **状态**：**Fixed**（2026-11-13 修复并实测闭环）
> **严重度**：**P1**（看板核心指标恒显示 0，且被 `|| 0` 静默兜底，无任何报错）
> **发现日期**：2026-11-13（F158 批次落地 `/ltv-churn` 菜单后复核页面时发现）
> **登记人**：Cursor
> **关联**：**I159**（前端 `sysApi.request` 缺失 → 静默失败，同属「静默兜底」家族）、**I160**
> **证据**：`audit/evidence/F159-ltv-dashboard-contract-fix/`
> **受影响功能**：F158（`/ltv-churn` 菜单）、F159（本轮修复）

---

## 0. 一句话描述

`GET /ltv-churn/dashboard` 只返回 `totalLtv365d` / `avgLtv365d`，
而两个前端消费方读的是 `totalLtv` / `avgLtv` → 取到 `undefined` → 被 `|| 0` 静默兜底 →
**LTV 卡片恒显示 ¥0**，而真实值是 **676,125,348.87（6.76 亿）**。

---

## 1. 缺陷位置

### 1.1 后端（返回方）

`backend/src/main/java/com/meiyetec/controller/LtvChurnController.java` → `dashboard()`（修复前 L226-241）：

```java
BigDecimal totalLtv = BigDecimal.ZERO;
BigDecimal avgLtv = BigDecimal.ZERO;
// ... 累加 predictedLtv365d ...
out.put("totalLtv365d", totalLtv);
out.put("avgLtv365d", avgLtv);
```

**返回体无 `totalLtv` / `avgLtv` 键**。

### 1.2 前端（消费方 1）

`frontend/meiyetec-web/src/views/ltv-churn/index.vue:18,24`：

```vue
<div class="stat-value">¥{{ Math.round(dashboard.totalLtv || 0).toLocaleString() }}</div>
<div class="stat-value">¥{{ Math.round(dashboard.avgLtv || 0).toLocaleString() }}</div>
```

### 1.3 前端（消费方 2）

`frontend/meiyetec-web/src/views/dev/P2Suite.vue:88,89`：

```vue
<el-col :span="12"><el-statistic title="总 LTV" :value="ltvDash.totalLtv || 0" /></el-col>
<el-col :span="12"><el-statistic title="平均 LTV" :value="(ltvDash.avgLtv || 0).toFixed(2)" /></el-col>
```

### 1.4 放大机制：`|| 0` 静默兜底

两处都用 `|| 0` 把 `undefined` 变成 `0`，
⇒ **字段名不匹配不会产生任何报错 / 空态提示**，页面看起来「正常」，只是数字是假的。
这与 I159（`sysApi.request` 缺失 → 静默失败）属同一失败模式：**契约断裂被兜底掩盖**。

---

## 2. 影响

| 维度 | 影响 |
|---|---|
| 功能 | `/ltv-churn` 页 2 张 LTV 卡片 + `/dev/p2-suite` LTV/Churn 页签 2 个统计值 **恒为 0** |
| 数据 | 无数据损坏（后端返回值正确，只是键名不匹配） |
| 业务 | 管理层看到的「LTV 累计价值 = ¥0」，与真实 6.76 亿严重背离，**可导致 LTV 商业判断完全失真** |
| 排障 | 因 `|| 0` 兜底，接口返回正常、页面无报错 → 极易被误判为「数据没有」 |
| 租户 | 全租户受影响（缺省逻辑与租户无关） |

---

## 3. 修复方案（已实施）

**方案：后端加契约别名（用户决策：改后端，一处改动，所有消费方受益）**

`LtvChurnController.dashboard()` 增加两行：

```java
out.put("totalLtv365d", totalLtv);
out.put("avgLtv365d", avgLtv);
// I162 契约别名：前端 ltv-churn/index.vue 与 dev/P2Suite.vue 读取 totalLtv / avgLtv
out.put("totalLtv", totalLtv);
out.put("avgLtv", avgLtv);
```

- **保留** `365d` 原名（语义更准确：口径是「预测 365 天 LTV」），不破坏既有调用方
- **新增** `totalLtv` / `avgLtv` 别名 → 两个前端消费方**零改动**即可取到真实值
- 改动文件：**仅 1 个**（`LtvChurnController.java`）

> 备选方案（未采用）：改前端 4 处字段名 → 改动点分散在 2 个文件，且其他潜在消费方仍会踩坑。

---

## 4. 验证证据

证据目录：`audit/evidence/F159-ltv-dashboard-contract-fix/`

### 4.1 API 层前后对比（L5b）

| 时点 | 响应体摘要 |
|---|---|
| 修复前 | `{"totalLtv365d":676125348.87,"avgLtv365d":191971.99,"membersWithLtv":489,"highRiskCount":393,"medRiskCount":96,...}` —— **无 `totalLtv`/`avgLtv`** |
| 修复后 | `{"totalLtv365d":676125348.87,"avgLtv365d":191971.99,"totalLtv":676125348.87,"avgLtv":191971.99,"membersWithLtv":489,...}` |

### 4.2 浏览器实测（L5a）

`/ltv-churn`（admin）：`LTV 累计价值 | ¥676,125,349 ;; 会员平均 LTV | ¥191,972 ;; 高流失风险 | 393 ;; 中流失风险 | 96`
`/dev/p2-suite` LTV/Churn 页签：`总 LTV | 676,125,348 ;; 平均 LTV | 191971.99 ;; 高流失风险 | 393`

截图：`raw/browser-ltv-churn-cards.png`、`raw/browser-p2suite-ltv-tab.png`

### 4.3 数据口径核对（L5b）

`crm_ltv_snapshot`：3522 快照行 / 489 会员 / `SUM(predicted_ltv_365d)` = 676,125,348.87
⇒ 与 API、UI 显示值**三方一致** ✅

### 4.4 验证等级

| 项 | 等级 |
|---|---|
| API 返回别名且数值正确 | **L5b** |
| 两个前端页面实际渲染非 0 值 | **L5a**（浏览器实测 + 截图） |
| 前后响应差异 | **L5b** |
| 生产部署 | **UNVERIFIED** |

---

## 5. 同域其他发现（**本轮未修，待用户决策**）

| # | 发现 | 位置 | 说明 |
|---|---|---|---|
| 5.1 | **LTV 累计值口径与 Churn 不一致** | `LtvChurnController.java:226-241` vs `:172-177` | `crm_ltv_snapshot` 有 3522 行 / 489 会员（每次 `POST /ltv-churn/ltv/compute` 追加行、**不删旧行**）。`dashboard()` 对**全部快照行**求和（≈7.2 倍重复计数），而 Churn 侧 `computeChurn()` 是「每会员取最后一笔」去重。⇒ 同一看板 LTV 侧口径 ≠ Churn 侧口径 |
| 5.2 | **P2Suite「查询会员 LTV」面板字段全不匹配** | `views/dev/P2Suite.vue:466-477` vs `entity/CrmLtvSnapshot.java` | 前端读 `totalRevenue` / `orderCount` / `repurchaseRate` / `ltv`；后端 `/ltv-churn/ltv/member/{id}` 返回 `CrmLtvSnapshot`，实际字段为 `historicalLtv` / `totalOrders` / `repurchaseProb30d` / `predictedLtv365d` ⇒ **4 个值全部渲染为空** |
| 5.3 | **`/ltv-churn/dashboard` 首次并发响应 > 10s 触发 axios 超时** | `views/dev/P2Suite.vue:457-462` + `api/request.ts`（`timeout: 10000`） | P2Suite 首屏并发 10+ 请求，实测 `ltv-churn/dashboard` 首响应 10032ms（超时）→ 页面 `catch` → `ltvDash={}` → 短暂显示 0；刷新后 1.4s 正常。**独立性能议题** |

> 5.1 / 5.2 / 5.3 均**未**在本轮修改（超出「决策 1：字段契约漂移」授权范围）。是否单独立 Issue 由用户决定。

---

## 6. 取证方式

| 项 | 值 |
|---|---|
| 核实 commit（修复前 HEAD） | **`1be1e7c`** `docs(audit): 补登 I161 至 issue-register` |
| 修复 commit | `fix(ltv): 修复看板 LTV 字段契约漂移（I162）` |
| 源码依据 | `frontend/meiyetec-web/src/views/ltv-churn/index.vue:18,24`；`frontend/meiyetec-web/src/views/dev/P2Suite.vue:88,89`；`backend/src/main/java/com/meiyetec/controller/LtvChurnController.java`（`dashboard()`） |
| 运行时 | Docker `meiyetec-api`（:8080，healthy）、`meiyetec-web`（:8088，healthy），2026-11-13 |
| DB 快照 | `crm_ltv_snapshot` 3522 行 / 489 会员 / `SUM(predicted_ltv_365d)`=676,125,348.87 |
| 证据目录 | `audit/evidence/F159-ltv-dashboard-contract-fix/` |
| 取证时间 | 2026-11-13 21:05 – 21:20 |
