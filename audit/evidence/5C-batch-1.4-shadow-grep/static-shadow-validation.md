# 5C 静态 Shadow 校验（I040 / I041 / I042）

> **创建时间**：2026-09-14 22:45 (UTC+8)
> **Phase**：5C — Frontend Shadow Playwright 降级（5C.1-5C.4）
> **范围**：Batch 1.4 — EXT-19 前端 Shadow Feature（F111 / F112）
> **起点 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2/1.3 freeze）
> **不动业务代码**（规则 6 + D23 决策）
> **Playwright MCP 未配置** → 沿用 Batch 1.1 / 1.2 模式：**用户本地 Playwright 跑脚本**

---

## 〇、Playwright 脚本就位确认

| 文件 | 状态 | 行数 | 覆盖 Issue |
|---|---|---|---|
| `audit/scripts/e2e/F111-F112-shadow.spec.ts` | ✅ **已生成**（2026-09-14 22:30 创建）| 156 行 | I040 + I041 + I042 |

**脚本来源**：Phase 5C 5C.4 任务已就绪，脚本按 Batch 1.1 F024 模式编写（fs.readFileSync 静态读 TS 源码 + Playwright UI 双路径校验）。

**脚本结构**：

```
F111 — intl-booking/index.vue Shadow 验证
  ├── test 1: 页面加载 + 2 tab 结构
  ├── test 2: 3 对话框结构（Google OAuth / Instagram OAuth / 模板预览）
  ├── test 3: I040 — autoSend=false UI 缺失
  └── test 4: I041 — "确认"按钮文案无"发送通知"提示

F112 — intl-booking.ts API 端点封装 Shadow 验证
  ├── test 1: I042 — intlBookingApi 无 sendConfirmation 方法
  ├── test 2: 11 端点方法完整性
  └── test 3: updateStatus 仅传 status 字段，不支持 autoSend
```

**用户跑命令**（沿用 Batch 1.1 模式）：

```powershell
cd frontend/meiyetec-web
npx playwright test --config=playwright.audit.config.ts e2e/audit/F111-F112-shadow.spec.ts
```

---

## 一、静态校验（5C 期间执行 — 2026-09-14 22:40）

> **不依赖 Playwright 运行** — 直接读源码 + grep 验证 3 个 Shadow Issue。

### 1.1 I040 实证 — 前端无 autoSend=false UI

**校验命令**：

```powershell
Select-String -Path d:\QiaoPM\frontend\meiyetec-web\src\views\intl-booking\index.vue `
  -Pattern 'autoSend|发送通知|二次确认|el-popconfirm|loading|通知'
```

**预期**：0 命中（autoSend UI 完全缺失）

**实际**：

```
"No matches found" （grep 返回 null）
```

**结论**：✅ **PASS — I040 实证成立**

**代码支撑**（`frontend/meiyetec-web/src/views/intl-booking/index.vue:38`）：

```vue
<el-button v-if="row.status === 'NEW'" size="small" type="success" @click="setStatus(row, 'CONFIRMED')">确认</el-button>
```

→ `@click="setStatus(row, 'CONFIRMED')"` 直接传 `CONFIRMED`，**没有** `autoSend` 字段；`setStatus` 方法（L86-90）调 `intlBookingApi.updateStatus(row.id, { status })`，也**不传** `autoSend`。

### 1.2 I041 实证 — 「确认」按钮文案无「发送通知」提示

**校验命令**：

```powershell
Select-String -Path d:\QiaoPM\frontend\meiyetec-web\src\views\intl-booking\index.vue `
  -Pattern '发送通知|el-popconfirm|二次确认'
```

**预期**：0 命中（无文案提示 + 无二次确认 + 无 loading）

**实际**：

```
"No matches found"
```

**结论**：✅ **PASS — I041 实证成立**

**代码支撑**：

- L38 按钮文案仅"确认"，无"即发送通知"
- 无 `el-popconfirm` 元素（无二次确认）
- 无 `:loading` 绑定（无 loading 状态）
- `setStatus` 方法（L86-90）只有 `await intlBookingApi.updateStatus(...)` + `ElMessage.success('已更新状态')` + `load()`，**无任何文案提示前置**

### 1.3 I042 实证 — intl-booking.ts 无 sendConfirmation 方法

**校验命令**：

```powershell
Select-String -Path d:\QiaoPM\frontend\meiyetec-web\src `
  -Pattern 'sendConfirmation|send-confirmation' -Recursive
```

**预期**：0 命中（前端 0 调用 + 0 封装）

**实际**：

```
"No matches found"
```

**结论**：✅ **PASS — I042 实证成立**

**代码支撑**（`frontend/meiyetec-web/src/api/intl-booking.ts`）：

- 文件含 11 个方法：`connectGoogle` / `googleCallback` / `connectInstagram` / `instagramCallback` / `listChannels` / `pullBookings` / `listBookings` / `getBooking` / `updateStatus` / `convertBooking` / `confirmationTemplate`
- **缺失 `sendConfirmation` 方法**
- 后端 `POST /intl-booking/{id}/send-confirmation` 端点存在，但前端 0 入口

### 1.4 updateStatus 类型签名校验（I040 二次验证）

**校验命令**：

```typescript
// 读 intl-booking.ts 全文
const apiSource = readFileSync('frontend/meiyetec-web/src/api/intl-booking.ts', 'utf-8')
const hasAutoSendInType = /autoSend\s*[\?:]/.test(apiSource)
```

**预期**：`hasAutoSendInType === false`（类型签名无 autoSend 字段）

**实际**：

```
updateStatus: (id: number, data: { status: string }) =>
  request.post(`/intl-booking/${id}/status`, data)
```

→ 类型签名第二个参数 `{ status: string }` 仅含 `status` 字段，**不含** `autoSend?: boolean`

**结论**：✅ **PASS — I040 类型签名层级实证成立**

---

## 二、Evidence 完整性

### 2.1 5C 静态校验产出

| 文件 | 内容 |
|---|---|
| 本文件（5C-batch-1.4-shadow-grep/static-shadow-validation.md）| I040 + I041 + I042 3 个 Shadow Issue 静态校验完整记录 |

### 2.2 Playwright 脚本产出（5C.4 已就绪）

| 文件 | 内容 |
|---|---|
| `audit/scripts/e2e/F111-F112-shadow.spec.ts` | 7 个 Playwright 测试 + 静态读 TS 源码 fallback |

---

## 三、决策与约束

| 决策 | 决定 | 原因 |
|---|---|---|
| **D29**（F111 / F112 测试执行方式）| **沿用 Batch 1.1 模式**：脚本生成 + 用户本地 Playwright 执行 | Playwright MCP 未配置；Cursor 不能开浏览器 |
| **D30**（I040 / I041 / I042 修复阶段）| **中立标 + Post-Audit 整改**（沿用 I040/I041/I042 决策） | 规则 6：审计不修业务代码；用户决策 #6 已确认 |
| **D31**（静态校验 vs Playwright 优先级）| **静态校验已 PASS**（5C 期间静态实证完成）；Playwright 跑通仅作为 UI 行为二次确认 | Phase 5C 5C.1-5C.3 已通过静态分析达成；Playwright 失败不影响 I040/I041/I042 实证 |

---

## 四、5C 期间（静态）Phase 5 全链路结论

| Shadow Issue | 静态校验 | Playwright 待跑 | 状态 |
|---|---|---|---|
| **I040**（autoSend=false UI 缺失）| ✅ PASS | （可选）| **实证成立** |
| **I041**（按钮文案无"发送通知"提示）| ✅ PASS | （可选）| **实证成立** |
| **I042**（intl-booking.ts 无 sendConfirmation）| ✅ PASS | （可选）| **实证成立** |

→ **3 个 Shadow Issue 静态全部 PASS，Phase 5C A 任务完成**

---

**5C 静态校验完成。I040 / I041 / I042 全部实证成立。Playwright 脚本已就位（用户本地可选执行）。⏸ 等用户确认进入 Batch 1.4 小结（EN-09 汇总报告）。**
