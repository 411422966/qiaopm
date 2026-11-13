# I159 — `sysApi.request` 方法缺失：/dev/* 研发工具区全站静默失败

> **状态**：Fixed（本地已修复并验证，2026-11-13）
> **严重度**：**P0**（影响面：`frontend/meiyetec-web/src` 全量 **175 处** `sysApi.request(...)` 调用，覆盖整个 `/dev/*` 研发工具区 10 个页面）
> **发现日期**：2026-11-13（I160 连带排查时定位）
> **登记人**：Cursor
> **关联**：I160（Churn 训练日志端点缺失，本 issue 的直接下游）、I154（LTV/Churn 训练链路缺陷 —— 「运行日志不关联」条目同源）、I155（预测端不消费训练模型）
> **修复方案**：方案 A —— `sysApi` 补 `request: (config) => request(config)` 通用转发入口
> **证据**：`audit/evidence/F157-dev-tools-sysapi-fix/`

---

## 0. 一句话描述

`frontend/meiyetec-web/src/api/sys.ts` 导出的 `sysApi` 对象**没有 `request` 方法**，
而 `/dev/*` 下的 10 个研发工具页面全部以 `sysApi.request({ method, url, params, data })` 形式直接调后端 ——
每一次调用都会抛 `TypeError: sysApi.request is not a function`，
该异常又被各页面的 `catch` 块吞掉并把数据置空，
最终页面只显示 **"No Data"**（或空表），**没有任何错误提示**。

---

## 1. 缺陷位置

### 1.1 根因点

| 项 | 内容 |
|---|---|
| 文件 | `frontend/meiyetec-web/src/api/sys.ts` |
| 对象 | `export const sysApi = { ... }`（L108 起） |
| 缺失成员 | `request` —— 修复前该对象只有具名方法（`configList` / `configGrouped` / `modelReport` / `modelAbValidate` …） |
| 修复位置 | `frontend/meiyetec-web/src/api/sys.ts:113` |

修复前 `git show HEAD:frontend/meiyetec-web/src/api/sys.ts | Select-String "request:"` → **匹配行数 0**（证据 `raw/06-before-after-static-diff.txt`）。

### 1.2 影响面（`sysApi.request(` 调用行统计）

| 文件 | 调用行数 | 所属页面 |
|---|---|---|
| `views/dev/P1Suite.vue` | 29 | P1 套件 |
| `views/dev/P2Suite.vue` | 28 | P2 套件 |
| `views/dev/GapSuite.vue` | 26 | 差距分析 |
| `views/dev/DataHub.vue` | 20 | 数据中台 |
| `views/dev/P3Suite.vue` | 19 | P3 套件 |
| `views/dev/CareMemberDay.vue` | 15 | 会员日 |
| `views/dev/LtvTraining.vue` | 14 | LTV/Churn 训练台 |
| `views/dev/PriceList.vue` | 14 | 价格表 |
| `views/dev/CarePlan.vue` | 8 | 护理方案 |
| `views/dev/Forecast.vue` | 2 | 预测 |
| **合计** | **175** | **10 个页面** |

> 另有 1 处在 `api/sys.ts:110`（本 issue 新增注释内），全仓 `rg -c "sysApi\.request\("` 计数为 **176**。

### 1.3 静默失败链路（典型）

`views/dev/LtvTraining.vue` 修复前：

```javascript
async function loadLtvModels() {
  modelLoading.value = true
  try {
    const r: any = await sysApi.request({ method: 'GET', url: '/intel/ltv/training/model/list' })
    ltvModels.value = r.data || []
  } catch (e) {
    ltvModels.value = []          // ← 异常被吞，数据置空
  } finally {
    modelLoading.value = false
  }
}
```

`sysApi.request` 为 `undefined` → 调用即抛 `TypeError` → 落入 `catch` → `ltvModels.value = []` → 表格空。
**页面上没有任何错误文案**，运维/研发看到的只有「No Data」，无从判断是「本来就没数据」还是「请求根本没发出去」。

> ⚠️ 这是典型的 **Fake Success / 静默失败**（与 I008 `catch` 吞异常同模式）：**请求从未发出，页面却呈现为一个"正常但无数据"的终态**。

---

## 2. 影响

| 维度 | 影响 |
|---|---|
| 功能 | `/dev/*` 全部 10 个研发工具页面的**所有**数据加载动作失效（175 个调用点） |
| 可观测性 | 无错误提示、无控制台断言、无埋点 —— 静默失败 |
| 数据风险 | 低（纯读路径为主，写路径同样被吞，表现为「点了没反应」） |
| 连带 | I160 —— LTV 侧 `/intel/ltv/training/run/log` 存在而 Churn 侧缺失，前端 `loadChurnLogs` 调用的正是缺失端点 |

**注**：本次修复**未触及**任何 `POST` 写路径语义（`request` 转发的是既有 axios 实例，方法/URL/参数原样透传）。

---

## 3. 修复

**方案 A（已采用）**：`sysApi` 补一个通用转发成员，复用既有 axios 实例：

```typescript
// frontend/meiyetec-web/src/api/sys.ts:113
request: (config: any) => request(config),
```

- `request` 即文件顶部已导入的 axios 实例封装，**转发语义 = 原样透传** `{ method, url, params, data }`；
- 不改动任何调用点，**175 处调用点零改动**；
- 不引入新依赖，不改后端契约。

配套（同一前端文件 `views/dev/LtvTraining.vue`，属本 issue 修复的**可观测性补强**，不改变业务行为）：

- 新增 `pageError` 状态 + `el-alert` 错误条（页面顶部，红色 alert）；
- `loadLtvModels` / `loadChurnModels` / `loadLtvLogs` / `loadChurnLogs` / `loadReport` 五个 `catch` 块补 `pageError.value = 'xxx加载失败：' + (e?.message || '未知错误')`；
- `loadAll()` 补调 `loadReport()`（此前模型健康报告从未随首屏加载）。

> 🛠 后续建议（**本次未做，待拍板**）：把 `catch` 置空改为统一 `usePageError` 组合式函数，避免每个页面重复写错误条逻辑。

---

## 4. 验证

### 4.1 接口连通性（部署后 curl，admin token，租户 1）

| # | 端点 | HTTP | 结果 |
|---|---|---|---|
| 1 | `GET /intel/ltv/training/model/list` | **200** | 返回 LTV 模型列表（`LTV_MLR_1794331222`，489 样本） |
| 2 | `GET /intel/churn/training/model/list` | **200** | 返回 Churn 模型列表（`CHURN_LR_1794331223`） |
| 3 | `GET /intel/ltv/training/run/log` | **200** | 10 条运行日志 |
| 4 | `GET /intel/churn/training/run/log` | **200** | 2 条运行日志（修复前该端点不存在 → 404，为代码级推断，见 I160） |
| 5 | `GET /governance/model/report` | **200** | 模型健康报告 |

原始输出：`audit/evidence/F157-dev-tools-sysapi-fix/raw/01-core-5-endpoints.txt`

### 4.2 连带修复抽测（8 个 /dev 页面代表端点，全 200）

| 页面 | 端点 | HTTP |
|---|---|---|
| DataHub | `GET /datahub/dashboard` | 200 |
| PriceList | `GET /intel/pricelist/list` | 200 |
| P1Suite | `GET /p1-suite/sop/list` | 200 |
| P2Suite | `GET /p2-suite/pos/dashboard` | 200 |
| P3Suite | `GET /p3-suite/ecommerce/dashboard` | 200 |
| CareMemberDay | `GET /member-day/upcoming` | 200 |
| GapSuite | `GET /ltv-churn/dashboard` | 200 |
| Forecast | `GET /noshow/high-risk` | 200 |

原始输出：`audit/evidence/F157-dev-tools-sysapi-fix/raw/02-collateral-8-endpoints.txt`

### 4.3 部署产物核对

```
/usr/share/nginx/html/assets/sys-x0_4Tsh9.js
grep -c 'request:t=>e(t)'  →  1
```

原始输出：`audit/evidence/F157-dev-tools-sysapi-fix/raw/04-frontend-artifact-grep.txt`

### 4.4 验证等级

| 项 | 等级 |
|---|---|
| 后端接口连通性 | **L5b**（真实 HTTP 200 + 真实响应体，curl 原始输出留证） |
| 部署产物 | **L5b**（容器内 nginx 实际产物 grep 命中） |
| 前端页面渲染 | **L4**（未做浏览器点击实测 —— 无 Playwright MCP，页面级交互仍为 **UNVERIFIED**，需人工验收） |

---

## 5. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 175 个调用点是否**全部**恢复正常渲染 | **未确认** —— 仅抽测 13 个端点（5 核心 + 8 连带），其余 160+ 调用点为**间接推断**（同一入口 `sysApi.request` 转发，逻辑等价） |
| 浏览器端页面实际渲染 | **未确认** —— 无 Playwright MCP，需人工在浏览器逐页验收 |
| 生产环境是否已部署本修复 | **未确认** —— 本次仅本地 Docker（`meiyetec/web:dev` + `meiyetec/api:dev`） |
| `sysApi.request` 是否有其他被误用的调用约定（缺 `method` 默认值等） | **未确认** —— 抽测的 13 个调用均显式带 `method` |

---

## 6. 取证方式

| 项 | 值 |
|---|---|
| 核实 commit | **`c27d370`** `docs(audit): 补登 I157、选定 I156/I157 方案、登记 I158`（修复前 HEAD） |
| 源码依据 | `frontend/meiyetec-web/src/api/sys.ts` L108-L113（`sysApi` 定义 + 补入的 `request`）；`frontend/meiyetec-web/src/views/dev/LtvTraining.vue` L536-L620（5 处 catch 补错误提示 + `loadAll` 补 `loadReport`） |
| 调用点统计 | `rg -c "sysApi\.request\(" frontend/meiyetec-web/src`（10 个 `.vue`，合计 175 行） |
| 运行时 | Docker `meiyetec-api`（:8080，healthy）+ `meiyetec-web`（:8088，healthy）+ `meiyetec-mysql`（:3307）+ `meiyetec-redis`（:6379），2026-11-13 16:10 重建 |
| DB 快照 | `churn_training_run_log` 2 行（修复前 0 行）；`ltv_training_run_log` 10 行 |
| 证据目录 | `audit/evidence/F157-dev-tools-sysapi-fix/`（README + raw/00-06） |
| 取证时间 | 2026-11-13 16:45 – 16:58 |
