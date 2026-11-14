# F166 — LTV 快照写入幂等 + 看板统计口径修复（I170 部分修复批次）

> **Feature ID**：F166
> **关联 issue**：I170（部分修复） / I172 / I173 / I174 / I175
> **取证时间**：2026-11-14 23:05 – 23:30（UTC+8）
> **验证等级**：L5a —— 运行时实测（HTTP 响应 + DB 前后快照双侧比对），**非静态推断**

---

## 1. 本次验证覆盖的范围

| # | 修复项 | 入口 | 结果 |
|---|---|---|---|
| 1 | 看板 LTV 统计限定在最新快照日 | `GET /ltv-churn/dashboard` | ✅ PASS |
| 2 | 写入者 C 单会员幂等 | `GET /intel/ltv/compute/{memberId}` | ✅ PASS |
| 3 | 种子重置环境守卫 | `POST /intel/ltv/seed`（mode=reset） | ✅ PASS |

**未覆盖（本批明确暂缓）**：`computeLtv` 加 DELETE（A3 核心，等 I172）、统一复购概率档位（I173）、
清理历史重复行（I174）、加唯一约束（I175）。

---

## 2. 绑定信息

| 项 | 值 |
|---|---|
| **代码 commit** | `01687a7` — `fix(ltv): dashboard 加 snapshot_date 过滤 + computeForMember 幂等 + seed 环境守卫（I170 部分）` |
| **commit 2（文档）** | 见本文件所在的后续提交 `docs(audit): 修正 I170 数字 + 登记 I172-I175 待业务决策项` |
| **DB** | `meiyetec` @ 本地 Docker `meiyetec-mysql`（宿主 3307 / 容器 3306），租户 **1** |
| **DB snapshot（修复后 · 最终）** | `crm_ltv_snapshot` 总 **4594** 行 / 21 个日期 / 当日 **780** 行（488 `batch` + 291 `compute-job` + 1 `online`）/ 当日 distinct 会员 **549** / 当日 dup 组 **231** / 全表 dup 组 **236** / 当日 `SUM(predicted_ltv_365d)` = **¥86,064,597.99** / 全表 `SUM` = **¥805,544,695.02** / `crm_churn_prediction` 总 1527 行 |
| **env** | 容器 `meiyetec-api`（镜像 `meiyetec/api:dev`，本批 23:19 重建），`SPRING_PROFILES_ACTIVE=prod`，宿主 `localhost:8080`；前端 `meiyetec-web` @ `localhost:8088` |
| **应用构建** | `cd backend && mvn clean package -Dmaven.test.skip=true` → **BUILD SUCCESS**（41.5s，1547 源文件）→ `docker compose build api` → `docker compose up -d api` → `healthy`（40s） |
| **登录** | `POST /auth/login`（`operator01`）→ `userId=900006`，租户 1 |

---

## 3. 证据文件清单（编号固定）

| 文件 | 内容 |
|---|---|
| `raw/01-dashboard-after-fix.txt` | dashboard 修复后响应（781 行口径） |
| `raw/02-compute-formember-idempotent.txt` | `computeForMember` 幂等验证（member 1） |
| `raw/03-dashboard-after-idempotent-test.txt` | 幂等测试后的 dashboard（780 行口径 · 最终值） |
| `raw/04-seed-env-guard.txt` | seed 环境守卫 403 + 行数前后不变 |
| `raw/05-db-snapshot-after-fix.txt` | 修复后 DB 快照 |
| `raw/06-verify-i170-partial.txt` | **本批完整验证脚本输出（12/12 CHECK PASS）** |
| `raw/verify-i170-partial.ps1.txt` | 验证脚本源码（**非正式工具**，仅取证用，不在 CI / PR 必跑清单） |
| `raw/seed-body-reset.json` | seed reset 请求体 |

---

## 4. 验证结果（12/12 CHECK PASS）

### 4.1 dashboard 快照日过滤

| CHECK | 结果 |
|---|---|
| `dashboard.code = 200` | PASS |
| `totalLtv365d` **精确等于**当日 `SUM(predicted_ltv_365d)`（86,064,597.99 = 86,064,597.99） | PASS |
| `totalLtv365d` **不等于**全表 `SUM`（86,064,597.99 ≠ 805,544,695.02） | PASS |
| `membersWithLtv` = 当日 distinct 会员数（549 = 549） | PASS |

> ⚠️ **数字变化说明（务必随报告一并展示）**
> 前端 LTV 卡片由 **¥805,544,695** 变为 **¥86,064,598**（降幅 9.36 倍）。
> 这是**「从错误变正确」，不是「数据丢失」** —— 减少的部分是修复前被**重复计入的 21 个历史快照日**，
> 数据库中一行数据都没有被删除。
> 另：修复 1 与修复 2 同批落地，member 1 的当日 2 行（batch + compute-job）在幂等验证中收敛为 1 行
> （online / BG），当日行数 781 → 780 口径见 §4.2，`membersWithLtv` 恒为 549。

### 4.2 `computeForMember` 单会员幂等

| CHECK | 结果 |
|---|---|
| 第 1 次调用 `GET /intel/ltv/compute/1` 返回 `code=200` | PASS |
| 调用后该会员当日行数 = **1** | PASS |
| 第 2 次调用后当日行数仍 = **1**（修复前为 2，且每次 +1） | PASS |
| 两次返回值完全一致（`predictedLtv365d=3801.38`） | PASS |

### 4.3 seed 环境守卫

| CHECK | 结果 |
|---|---|
| prod profile 下 `POST /intel/ltv/seed {"mode":"reset"}` 返回 **HTTP 403** | PASS |
| 响应体 `{"code":403,"message":"生产环境禁止执行种子重置"}` | PASS |
| 调用前后租户行数不变（snap 4594 / mem 489 / ord 1848 → 4594 / 489 / 1848） | PASS |

---

## 5. 已知限制与副作用（须知悉）

| 项 | 说明 |
|---|---|
| 单会员幂等的副作用 | 写入者 C 的 `DELETE` 会把**该会员当日由 A/B 产生的行一并清掉** —— 该会员当日快照以 C 的口径为准。**不影响其他会员** |
| 当日重复组仍存在 | **231 组**当日重复（A `batch` × B `compute-job`）**未消除** —— 消除需 A3 落地，等 I172 业务拍板 |
| `trend` 历史趋势仍被放大 | 历史 5 组重复（多计 ¥3,912,645.40）**未清理** —— I174 待授权 |
| 唯一约束未加 | `crm_ltv_snapshot` 仍无 `UNIQUE (tenant_id, member_id, snapshot_date)` —— I175，前置条件未满足 |
| 生产环境未验证 | 全部验证在本地 Docker 完成，**生产库不可访问**，生产表现 UNVERIFIED |
| 新发现（未落盘独立 issue） | `LtvSeedController` 的 `mode=reset` 删的是 `crm_order`（**库中不存在该表**），而生成逻辑写的是 `pos_order` → dev 放开守卫后会「先删光快照再抛 SQLException」。详见 I170 §7.1，建议 I176 |
