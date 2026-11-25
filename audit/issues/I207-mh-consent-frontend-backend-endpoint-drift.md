# I207 — `views/mh/consent.vue` 6 个端点 / 7 个调用点在后端无对应实现（契约漂移 / 幽灵功能）

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P2**
> **P2 理由**：页面可正常进入、核心 21 个端点可用（见 §3），但 6 个按钮/交互点击后必 404/405；属**功能不完整**而非数据泄露或越权，故低于 I206（P1）
> **发现日期**：2026-11-26（I202 §1.3 复核批次派生）
> **登记人**：Cursor
> **绑定基线**：HEAD `d102a3f`（`docs(audit): register I205 状态列措辞修正（静态已确认代码事实）+ I205 §4.5 记录不单开规范 issue`）
> **验证等级**：**静态 VERIFIED（前端调用点行号 + 后端全量 `@*Mapping` 扫描）** —— 缺失结论由源码双向扫描确定；**运行时未实测（未点击触发 404/405）→ 该层标 UNVERIFIED**
> **关联**：I202（本批附带发现）、I124（authz-code-system-split 同类：前端调用后端不存在端点）、I206（同一页面的鉴权缺口）
> **登记状态**：**待写入 `audit/issues/issue-register.md`**（登记表属治理基线，本轮未修改，等待用户批准）

---

## 0. 一句话描述

`frontend/meiyetec-web/src/views/mh/consent.vue` 中 **6 个端点 / 7 个调用点** 的 API 调用，
在后端 `backend/src/main/java/com/meiyetec/` 全量扫描 `@(Get|Post|Put|Delete)Mapping` 后**确认无对应实现** ——
其中 `template` 的版本管理三件套（toggle / version list / new-version）**后端完全无实现**，版本功能属**幽灵功能**。

> **口径说明**：调用点数 7 > 端点数 6 —— `POST /mh/consent/template/{id}/toggle` 在 `consent.vue` 的 **:1219 与 :1283 各出现一次**（同一端点、两个调用点）。

---

## 1. 缺失端点清单

| 前端调用 | consent.vue 行号 | 后端实际存在 | 后端位置 |
|---|---|---|---|
| `POST /mh/consent/template/{id}/toggle` | 1219, 1283（**2 个调用点**） | ❌ 无 | — |
| `GET /mh/consent/template/version/list` | 1245 | ❌ 无 | — |
| `POST /mh/consent/template/{id}/new-version` | 1253 | ❌ 无 | — |
| `POST /mh/consent/sign-link/issue` | 1181 | ❌ 无（H5 侧只有 `GET /mh/consent/sign-link/{token}` + `POST /mh/consent/sign-link/{token}/submit`） | `controller/MhConsentH5Controller.java:43, 93` |
| `POST /mh/drug-device/{id}/toggle` | 1347 | ❌ 无 | — |
| `POST /mh/drug-device/batch/{id}/status` | 1382 | ❌ 无（后端只有 `POST /mh/drug-device/batch/{id}/recall`） | `service/ConsentAndDrugDeviceService.java:419` |

---

## 2. 已存在的同域端点（对照）

`service/ConsentAndDrugDeviceService.java`（`/mh`）：

`POST /consent/template`(49)、`GET /consent/template/list`(63)、`GET /consent/template/{id}`(91)、`POST /consent/sign`(102)、`GET /consent/stats`(176)、`GET /consent/sign/page`(242)、`POST /consent/sign/{id}/revoke`(287)、`POST /drug-device`(303)、`GET /drug-device/list`(315)、`POST /drug-device/batch`(334)、`GET /drug-device/batch/page`(368)、`POST /drug-device/batch/{id}/recall`(419)、`POST /drug-device/usage`(436)、`POST /drug-device/usage/{id}/adverse`(484)、`GET /drug-device/usage/page`(495)。

---

## 3. 影响

- 6 个按钮/交互在页面上可点，调用即 404/405（**运行时未实测，仅静态确认**，标 UNVERIFIED）。
- `template` 版本管理（toggle / version/list / new-version）在后端**完全无实现** → 版本功能为**幽灵功能**。
- `sign-link/issue`（后端签发签名链接）缺失 → 只有 H5 侧消费链接的接口存在，**签发侧缺失**，链路不完整。
  > 与 I206 §1.2 第 6 点的交叉：`MhConsentH5Controller` 的 `sign-link/{token}` 消费端**存在但需 JWT**（白名单未排除 `/mh/consent/**`，实测无 token = 401）→ **签发端缺失 + 消费端被 JWT 拦截**，该链路两端均不可用。

---

## 4. 修复方向（不在本批范围）

- 二选一：**补后端端点**（对齐前端语义），或**摘除前端调用点**。
- 注意：`/mh/consent` 属**用户已实测通过页面**，按项目规则**未经单独批准不得修改** `views/mh/consent.vue` 与 `views/mh/visual.vue`；本次仅登记，**不动代码**。

---

## 5. 关联与交叉引用

- `audit/issues/I202-frontend-permission-code-drift-full.md` §1.3
- `audit/issues/I206-mh-visual-consent-complication-no-backend-auth.md`（同页面族的鉴权缺口）
- `audit/issues/I124-authz-code-system-split.md`（同型：前端调用后端不存在端点）
- 方案文档：`audit/reports/i202-p2-perm-binding-plan.md` §7
