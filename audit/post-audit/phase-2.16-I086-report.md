# Phase 2.16 — I086 F112 Shadow spec 路径修复报告

> **修复时间**：2026-09-27 15:59 (UTC+8)
> **修复人**：Cursor Agent
> **范围**：Post-Audit 二期 P3 第 1 项（TEST-INFRA）
> **commit**：`80c5a3a` — `fix(I086): F111-F112 spec loadApiSource 路径修正`
> **改动量**：1 文件 +1/-1 行

---

## 一、根因

`F111-F112-shadow.spec.ts:26` 的 `loadApiSource()` 函数内 `resolve(__dirname, '../../../src/api/intl-booking.ts')` 路径少一层目录 `frontend/meiyetec-web/`：

- 实际项目布局：`D:\QiaoPM\frontend\meiyetec-web\src\api\intl-booking.ts`
- `__dirname = D:\QiaoPM\audit\scripts\e2e`
- 回退 3 层到 `D:\QiaoPM\`，再下钻 `src/api/` → 落到 `D:\QiaoPM\src\api\intl-booking.ts`（**不存在**）
- ENOENT 抛错 → 3 条 F112 Shadow 实证全部失败

### register 原描述澄清

register 原文写「实际应为 `../../../../frontend/meiyetec-web/src/api/intl-booking.ts`（5 层 `..`）」属于**计数错误**。`__dirname` 回退 3 层即可到达 `D:\QiaoPM\` 根目录，无需 4 层。修正方案是**保留 3 层 `..`，在 `src/api/` 前插入 `frontend/meiyetec-web/`**。

---

## 二、修复方案

```diff
 function loadApiSource(): string {
-  const apiPath = resolve(__dirname, '../../../src/api/intl-booking.ts')
+  const apiPath = resolve(__dirname, '../../../frontend/meiyetec-web/src/api/intl-booking.ts')
   return readFileSync(apiPath, 'utf-8')
 }
```

**改动 1 行**。

---

## 三、验证

| 项目 | 结果 |
|---|---|
| **commit** | `80c5a3a` fix(I086): F111-F112 spec loadApiSource 路径修正 |
| **文件存在校验** | `Test-Path D:\QiaoPM\frontend\meiyetec-web\src\api\intl-booking.ts` → **True** ✅ |
| **旧路径（修前）** | `Test-Path D:\QiaoPM\src\api\intl-booking.ts` → **False** ❌（确认 ENOENT 根因）|
| **新路径解析** | `D:\QiaoPM\audit\scripts\e2e` + `../../../frontend/meiyetec-web/src/api/intl-booking.ts` → `D:\QiaoPM\frontend\meiyetec-web\src\api\intl-booking.ts` ✅ |
| **后续 E2E 验证** | ⚠️ 待 Phase 2.16 收口时跑 `npx playwright test --config=playwright.audit.config.ts e2e/audit/F111-F112-shadow.spec.ts`，预期 I042（`intlBookingApi` 无 `sendConfirmation`）实证 FAIL，**I040/I041 实证 PASS**（UI 已有 autoSend 开关 + 确认按钮文案）|

---

## 四、I086 状态

| 维度 | 值 |
|---|---|
| **I086 状态** | ✅ **FIXED**（脚本路径修正完成，commit `80c5a3a`）|
| **影响范围** | 仅测试脚本 `audit/scripts/e2e/F111-F112-shadow.spec.ts`，**不涉及业务代码** |
| **未触动** | `frontend/meiyetec-web/**`、后端任何 `.java`、SQL、`application.yml` |
| **不标 PARTIAL** | 路径是确定性 bug，修复即闭环 |

---

## 五、待办（Phase 2.16 收口）

1. 重跑 F111-F112 E2E，验证 I040（autoSend UI）/ I041（确认文案）实证 PASS、I042（sendConfirmation 缺失）实证 FAIL
2. 若 I042 实证 FAIL（预期），登记为 OPEN P2（非脚本 bug，是产品代码 `intlBookingApi` 缺 `sendConfirmation` 方法）
3. 跑 8 个守门脚本对比 baseline `dcaf07a`，确认本次纯测试脚本修复无静态层回归

---

**完整状态**：✅ FIXED（commit `80c5a3a`，2026-09-27 15:59）
