# F157 — /dev/* 研发工具区全站静默失败修复（I159 + I160）

> **Feature ID**：F157
> **证据日期**：2026-11-13（UTC+8）
> **核实 commit（修复前 HEAD）**：**`c27d370`** `docs(audit): 补登 I157、选定 I156/I157 方案、登记 I158`
> **修复 commit**：见 `git log --oneline` 中 `fix(frontend): sysApi 补 request 方法…` / `fix(churn): 补 /run/log 端点…`
> **环境**：`audit/evidence/env.md`（Phase 5A Runtime Environment）
> **DB 快照**：`audit/evidence/db-snapshot.md` + 本目录 `raw/03-db-churn-run-log.txt`
> **关联 Issue**：I159（P0）、I160（P2）；连带关联 I154、I155
> **最终状态**：**PARTIAL**（接口层 L5b 已验证；浏览器页面渲染未实测 → UNVERIFIED）

---

## 1. 缺陷与修复概览

| # | Issue | 严重度 | 缺陷 | 修复 | 文件 |
|---|---|---|---|---|---|
| 1 | **I159** | P0 | `sysApi` 对象缺 `request` 方法 → `sysApi.request(...)` 抛 `TypeError` → 被各页面 `catch` 吞掉 → 页面只显示 "No Data"，无任何错误提示。影响 **175 处调用 / 10 个 `/dev/*` 页面** | 补 `request: (config: any) => request(config)`（方案 A） | `frontend/meiyetec-web/src/api/sys.ts:113` |
| 2 | **I160** | P2 | `ChurnTrainingService` 注入了 `ChurnTrainingRunLogMapper` 却从不 `insert`，也无 `/run/log` 端点 → `churn_training_run_log` 表 0 行、前端 Churn 日志 tab **404** | 补端点 + `train()` 内三处埋点（方案 C） | `backend/src/main/java/com/meiyetec/service/ChurnTrainingService.java`（L87 / L169 / L175 / L590 / L603） |

配套（I159 的可观测性补强，不改变业务行为）：`frontend/meiyetec-web/src/views/dev/LtvTraining.vue` 新增 `pageError` + `el-alert` 错误条，5 处 `catch` 补错误文案，`loadAll()` 补调 `loadReport()`。

---

## 2. 前后对比

### 2.1 静态对比（`raw/06-before-after-static-diff.txt`）

```
### 修复前 (HEAD=c27d370) frontend/meiyetec-web/src/api/sys.ts 中 'request:' 匹配行数: 0
### 修复后 工作区 匹配行数: 1
D:\QiaoPM\frontend\meiyetec-web\src\api\sys.ts:113:  request: (config: any) => request(config),

### 修复前 ChurnTrainingService 是否存在 /run/log 映射: 匹配行数: 0
### 修复前 ChurnTrainingService 是否调用 logMapper.insert: 匹配行数: 0
```

### 2.2 运行时对比

| 观测点 | 修复前 | 修复后 | 证据 |
|---|---|---|---|
| `GET /intel/churn/training/run/log` | **404**（端点在修复前不存在：`run/log` 映射数 = 0，静态确认；HTTP 404 为代码级推断，本次未复现实测） | **200**，返回 2 条日志 | `raw/01-core-5-endpoints.txt` / `raw/06-before-after-static-diff.txt` |
| `churn_training_run_log` 行数 | **0** | **2** | `raw/03-db-churn-run-log.txt` |
| `/dev/*` 页面数据加载 | 抛 `TypeError` → "No Data"（无提示） | 请求正常发出 → 表格有数据 | 接口层已证（§3）；页面层 **UNVERIFIED** |
| 前端部署产物 | `sys-*.js` 中无 `request` 成员 | `sys-x0_4Tsh9.js` 含 `request:t=>e(t)`（命中 1 处） | `raw/04-frontend-artifact-grep.txt` |

---

## 3. 5 个核心接口验证（全 200）

命令：`curl.exe -H "Authorization: Bearer <admin token>" http://localhost:8080<url>`
（完整原始输出见 `raw/01-core-5-endpoints.txt`）

| # | 端点 | HTTP | 响应摘要 |
|---|---|---|---|
| 1 | `GET /intel/ltv/training/model/list` | **200** | `data[]` 含 `LTV_MLR_1794331222`（489 样本）、`LTV_REGRESSION`/`MLR` |
| 2 | `GET /intel/churn/training/model/list` | **200** | `data[]` 含 `CHURN_LR_1794331223`（489 样本） |
| 3 | `GET /intel/ltv/training/run/log` | **200** | 10 条（`LtvTrainingRunLog`），最新 id=10 |
| 4 | `GET /intel/churn/training/run/log` | **200** | **2 条**（修复前 404） |
| 5 | `GET /governance/model/report` | **200** | `ltvModels[]` + churn 侧模型健康数据 |

### 3.1 Churn 运行日志 2 条内容（`raw/03-db-churn-run-log.txt`）

```
id  tenant_id  model_id  run_type  step  duration_ms  samples  metric   metric_value  message
1   1          NULL      TRAIN     TRAIN  NULL        489      samples  NULL         算法=LR,churnThreshold=90,horizon=90
2   1          4         TRAIN     TRAIN  125         489      val_auc  0.5000       训练完成
```

`churn_rows = 2`；`ltv_rows = 10`（LTV 侧未被本修复改动，仅作对照）。

---

## 4. 连带修复抽测（8 个 /dev 页面代表端点，全 200）

| 页面 | 端点 | HTTP | 响应摘要 |
|---|---|---|---|
| DataHub | `GET /datahub/dashboard` | **200** | `data: []`（端点正常，空数据为业务态） |
| PriceList | `GET /intel/pricelist/list` | **200** | `data: []` |
| P1Suite | `GET /p1-suite/sop/list` | **200** | `data[]` 含 `RED_SWELL` 等 SOP |
| P2Suite | `GET /p2-suite/pos/dashboard` | **200** | `totalTx=0 / totalRevenue=0.0 / byVendor=[]` |
| P3Suite | `GET /p3-suite/ecommerce/dashboard` | **200** | `productCount=3 / orderCount=0` |
| CareMemberDay | `GET /member-day/upcoming` | **200** | `data: []` |
| GapSuite | `GET /ltv-churn/dashboard` | **200** | `membersWithLtv=489 / highRiskCount=393` |
| Forecast | `GET /noshow/high-risk` | **200** | `data: []` |

原始输出：`raw/02-collateral-8-endpoints.txt`

> ⚠️ 抽测仅覆盖 13 个端点（5 + 8），**其余 160+ 调用点未逐个实测** —— 它们共用同一入口 `sysApi.request`（同一 axios 实例透传），逻辑等价，属间接推断。

---

## 5. 构建与部署

| 项 | 内容 |
|---|---|
| 后端打包 | `D:\apache-maven-3.9.6\bin\mvn.cmd -o -q -Dmaven.test.skip=true package` → `backend/target/meiyetec-api-1.0.0.jar`（140,042,832 bytes，16:06:43） |
| 镜像重建 | `docker compose build api web` → `meiyetec/api:dev`、`meiyetec/web:dev`（16:09:56 完成） |
| 容器重建 | `docker compose up -d api web` → `meiyetec-api` / `meiyetec-web` 均 healthy（16:11） |
| 产物核对 | `docker exec meiyetec-web grep -c 'request:t=>e(t)' /usr/share/nginx/html/assets/sys-x0_4Tsh9.js` → `1` |

原始输出：`raw/05-docker-build-log.txt`（docker build 完整尾部日志）、`raw/04-frontend-artifact-grep.txt`

---

## 6. 证据清单

| 文件 | 内容 |
|---|---|
| `raw/00-login.json` | 登录取证（admin / 租户 1，token 已脱敏） |
| `raw/01-core-5-endpoints.txt` | 5 个核心接口 curl 原始响应体 |
| `raw/02-collateral-8-endpoints.txt` | 8 个连带端点 curl 原始响应体 |
| `raw/03-db-churn-run-log.txt` | `churn_training_run_log` / `ltv_training_run_log` 行数与内容快照 |
| `raw/04-frontend-artifact-grep.txt` | 容器内前端产物 grep 结果 |
| `raw/05-docker-build-log.txt` | `docker compose build api web` 构建日志 |
| `raw/06-before-after-static-diff.txt` | 修复前后源码静态对比（`git show HEAD:...`） |

---

## 7. 验证等级与未确认项

| 观测项 | 等级 | 说明 |
|---|---|---|
| 5 个核心接口 HTTP 200 + 真实响应体 | **L5b** | curl 原始输出留证 |
| 8 个连带端点 HTTP 200 | **L5b** | 同上 |
| `churn_training_run_log` 写入 2 行 | **L5b** | DB 直查快照 |
| 前端部署产物含 `request` 方法 | **L5b** | 容器内实际产物 grep |
| 175 个调用点全部恢复 | **UNVERIFIED** | 仅抽测 13 个端点，其余为同入口等价推断 |
| 浏览器页面实际渲染（表格有数据、错误条可见） | **UNVERIFIED** | 无 Playwright MCP，需人工验收 |
| 生产环境部署 | **UNVERIFIED** | 本次仅本地 Docker |
| 训练失败路径日志落库 | **UNVERIFIED** | `train()` 的 `@Transactional` 可能回滚失败分支的 insert（见 I160 §3.2） |
| 跨租户隔离（`/run/log` 的 `tenantId` 过滤） | **UNVERIFIED** | 仅代码级确认，未运行实测 |

**整体判定**：**PARTIAL** —— 接口层与数据层已用运行证据闭环；页面渲染与生产部署仍需人工验收。

---

## 8. 未做 / 明确不做

| 项 | 说明 |
|---|---|
| 「训练全部模型」按钮失败问题 | **本次不处理**（用户决策，另案） |
| Maven 本地缓存 `_remote.repositories` 变更 | **不回滚**（本地元数据，下次联网会重建；用户明确指示） |
| 跨租户 / 浏览器级验收 | 无工具链，未执行 |
| 生产环境验证 | 未执行 |
