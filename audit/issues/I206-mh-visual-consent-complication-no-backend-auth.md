# I206 — `/mh/visual` + `/mh/consent` + `/complication` 后端 0 个 `@RequirePermission`，前端守卫成为唯一门禁

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P1**
> **P1 理由**：三个业务域共 **41 个方法级端点**后端**零鉴权**，其中包含 `POST /mh/visual/archive`、`POST /mh/visual/archive/{id}/revoke`、`POST /mh/visual/archive/batch-revoke`、`POST /mh/visual/withdraw` 等**写入类端点** → 任意已登录账号（含 CLERK / OPERATOR 最低权限）可读写视觉档案、合规记录与并发症 SOP；D2 方案 A 落地后前端守卫将成为唯一门禁，风险由「前端挡住」恶化为「无任何门禁」
> **发现日期**：2026-11-26（I202 §1.3 E1/E2/E3 复核批次派生）
> **登记人**：Cursor
> **绑定基线**：HEAD `d102a3f`（`docs(audit): register I205 状态列措辞修正（静态已确认代码事实）+ I205 §4.5 记录不单开规范 issue`）
> **验证等级**：**静态 VERIFIED（方法级端点数与注解数）+ 运行期部分 VERIFIED** —— 端点数 / `@RequirePermission` 数由源码扫描确定（静态）；`clerk01` 直连 `GET /mh/visual/archive/page` = 200、`GET /mh/consent/stats` = 200 为**上一轮运行期实测**；`GET /mh/consent/sign-link/xxx`（无 token）= **401** 为本轮运行期实测。**写入类端点的越权写入场景本机未实测 → UNVERIFIED**
> **关联**：I204（同型：后端无鉴权 + 前端门禁）、I202（本批 R2/D2 根因）、I207（`views/mh/consent.vue` 契约漂移）
> **登记状态**：**待写入 `audit/issues/issue-register.md`**（登记表属治理基线，本轮未修改，等待用户批准）

---

## 0. 一句话描述

`service/VisualArchiveService.java`（`/mh/visual`）、`service/ConsentAndDrugDeviceService.java`（`/mh`）、`service/ComplicationService.java`（`/complication`）三个 service **全类 0 个 `@RequirePermission`**，
`controller/MhConsentH5Controller.java`（`/mh/consent`）同样 0 注解 —— 后端**完全不设防**；
而前端路由守卫 `router/index.ts:153`（`mh:visual`）、`:154`（`mh:consent`）、`:167`（`mh:consent`）**是这两页唯一的门禁**，且这 3 个码在 `sys_permission` 中**根本不存在**（库中实为点号码 `mh.visual`(1251) / `mh.consent`(1252)）。

**D2 方案 A 落地后**（前端 meta 改为点号码），前端守卫将对 BOSS(6) 放行、对 MANAGER/CLERK/OPERATOR 仍 403 —— 但**绕过前端守卫直接构造 HTTP 请求即可无差别读写**，后端**不会**拦截。

---

## 1. 事实

### 1.1 统计口径与现状

> 端点数口径：**方法级端点**（`@(Get|Post|Put|Delete)Mapping`），**不含**类级 `@RequestMapping`。

| 类 | 类级 `@RequestMapping` | **方法级端点数** | `@RequirePermission` |
|---|---|---|---|
| `service/VisualArchiveService.java` | `/mh/visual` | **19** | **0** |
| `service/ConsentAndDrugDeviceService.java` | `/mh` | **15** | **0** |
| `service/ComplicationService.java` | `/complication` | **7** | **0** |
| `controller/MhConsentH5Controller.java` | `/mh/consent` | **2** | **0**（token 链接 H5 流程） |
| **合计** | — | **43**（**41 个角色鉴权缺口** = 三个业务 service 19+15+7；**+ 2 个 H5 token 端点**，见 §1.2 第 6 点 E2） | **0** |

全库扫描 `@RequirePermission("` 在 `mh / visual / consent / complication / drug-device` 语境下**命中 0 处**。

### 1.2 现象

1. 三个 service 全类 0 个 `@RequirePermission`，后端不设防。
2. 前端守卫是唯一门禁：`router/index.ts:153`（`mh:visual`）、`:154`（`mh:consent`）、`:167`（`mh:consent`）—— 且这 3 个码在 `sys_permission` 中**不存在**（DB 实为 `mh.visual` / `mh.consent`）。
3. 前端调用面：`views/mh/visual.vue` **17 个调用点 / 14 个端点**；`views/mh/consent.vue` **29 个调用点**。
4. **实测**（2026-11-25 运行期证据）：`clerk01` `GET /mh/visual/archive/page` = **200**、`GET /mh/consent/stats` = **200**（后端直连，绕开前端守卫）。
5. **D2 方案 A 落地后，这三个页面的前端守卫将成为唯一门禁** —— 直接构造 HTTP 请求即可绕过。
6. **`MhConsentH5Controller` 的 `/mh/consent/sign-link/*` 是 token 链接流程，0 注解属设计**（H5 凭一次性 token 访问，不走 JWT）；**但 `WebConfig` 的 JWT 放行白名单未排除 `/mh/consent/**`** → 实测**无 token 调 `sign-link` 返回 401**，与「免登录」描述不符（该不一致已记录于 **I202 §1.3 E2**）：设计意图是免登录，实际实现是**需 JWT**，H5 链路当前**不可用**。

### 1.3 与 I202 §1.3 E 类更正的关系

| # | I202 §1.3 E 类条目 | 与本条的关系 |
|---|---|---|
| **E1** | `mh:visual` 前端「0 个 API 调用」有误 → 实为 **17 调用点 / 14 端点** | 证明前端**确有**完整调用面，不是只读静态页 → 风险等级从「可疑」升为「实锤」 |
| **E2** | `mh:consent`「免登录」描述与实测 401 不符 | 证明 `WebConfig` 白名单未覆盖 `/mh/consent/**` → 契约漂移，H5 链路不可用 |
| **E3** | D2 行「对 ADMIN/BOSS/SUPER_ADMIN 仍 403」不准确 | 修正放行范围，不影响本条的鉴权缺口结论 |

---

## 2. 影响

任意已登录用户（含 CLERK / OPERATOR 等最低权限账号）可读取、**并写入**视觉档案（`POST /mh/visual/archive`、`POST /mh/visual/archive/{id}/revoke`、`POST /mh/visual/archive/batch-revoke`、`POST /mh/visual/withdraw` 等），以及合规记录、并发症 SOP 与事件。

**写入类端点不受任何角色门禁约束，写面比读面更危险。**

> **未确认**：越权**写入**场景本机未实测（静态可判定注解缺失，运行期未构造）→ 标 UNVERIFIED。

---

## 3. 修复方向（不在本批范围）

- 为三个 service 的每个端点补 `@RequirePermission`，码沿用 DB 既有口径 `mh.visual` / `mh.consent`（与 D2 改后前端一致）。
- `MhConsentH5Controller` 的 `sign-link/*` 二选一：
  - 明确免登录 → 在 `WebConfig` 的 JWT 放行白名单排除 `/mh/consent/sign-link/**`；
  - 或维持现状 → 把「免登录」的注释/描述改为「需 JWT」，并修正 H5 侧取 token 流程。
- **补齐前，前端守卫不得被登记为「已保护」**。

---

## 4. 遗留标注

本批**不修**。D2 落地后该风险由「前端挡住」恶化为「无任何门禁」；I202 §7 R1 中 `mh` 3 页已从「永久 403」项移出，**本条即为该 3 页的真实状态描述**。

---

## 5. 关联与交叉引用

- `audit/issues/I202-frontend-permission-code-drift-full.md` §1.3 E1 / E2 / E3、§3.1 D2 行、§7 R1
- `audit/issues/I204-dev-pay-pages-no-backend-auth.md`（同型）
- `audit/issues/I207-mh-consent-frontend-backend-endpoint-drift.md`（同页面族的契约漂移）
- 方案文档：`audit/reports/i202-p2-perm-binding-plan.md` §6
