# I132 — 收银台前端未传入 tenderedAmount，找零功能未接入 UI

> 发现时间：2026-10-27（I129 调查过程中的额外发现 A5）
> 发现方式：I129 核对 `PosController` 的 M02-04 找零实现时，反查前端调用方 `views/pos/checkout.vue` 的支付请求体
> 严重级别：**P2**（功能链路半成品；后端契约已就绪，前端未接入 → 现金收款在当前 UI 下不可用）
> 状态：**Open**（未修复）
> 关联：I129（`pos_order` 缺 `tendered_amount` / `change_amount` 列，本单为同特性的前端侧缺口）、I130（修复脚本语法）
> 基线：commit `e60376aac762a1fde45557ca1a21513388f57031`（2026-10-27 23:50:18）/
> DB `meiyetec@localhost:3307`（MySQL 8.0.46）/
> DB snapshot：`backup/meiyetec_20261025_020004.sql` / env：`audit/baseline/env.md`

---

## 1. 一句话结论

M02-04「收银找零服务端处理」的**后端已完整实现并强制校验**，但**前端收银台 `checkout.vue` 全文 0 处 `tendered`**，
提交支付请求时只带 `method / amount / transactionId / memberCardId`，**不带实收金额**：

| 环节 | 状态 |
|---|---|
| 后端 DTO `tenderedAmount` 字段 | ✅ 已定义（`dto/PosPayDTO.java:35` 单笔 / `:52` 混合明细） |
| 后端校验 + 服务端权威算找零 + 落库 | ✅ 已实现（`controller/PosController.java:681/685-703/516-521/744/765-769`） |
| **前端提交 `tenderedAmount`** | ❌ **完全未实现**（`views/pos/checkout.vue` 0 处命中） |
| **前端实收金额输入框 / 找零展示** | ❌ **完全未实现**（0 处 `找零` / `changeAmount` 引用） |

**直接后果**：前端默认支付方式就是「现金」（`checkout.vue:675` `newPayMethod = ref<string>('cash')`），
而现金支付在后端是**强制要求** `tenderedAmount` → **选现金提交必然被服务端拒绝**；
改用非现金方式则可以支付成功，但**没有实收概念 → 服务端算出的找零恒为 0**，收银员也**拿不到应找零金额**。

---

## 2. 硬证据

### 2.1 后端：契约与实现都在，且对现金是「硬校验」

`backend/src/main/java/com/meiyetec/dto/PosPayDTO.java`：

```java
/**
 * 现金实收金额（method=cash 时<b>必填</b>，tenderedAmount ≥ amount，差值为找零）
 * 非 cash 时<b>禁止</b>传值（服务端会拒绝）
 */
private BigDecimal tenderedAmount;      // L35 单笔模式
...
private BigDecimal tenderedAmount;      // L52 PaymentItem（混合支付明细）
```

`backend/src/main/java/com/meiyetec/controller/PosController.java`：

| 行号 | 内容 |
|---|---|
| L481-486 | M02-04 找零规则注释（现金必传 / 非现金禁传 / 服务端独立计算 / 混合支付聚合） |
| L490-491 | `computeChangeServerSide(items, order)` 前置校验，失败即 `Result.error(...)` |
| **L681-683** | **非现金禁止传 `tenderedAmount`**：「非现金支付方式 [xx] 禁止传 tenderedAmount」 |
| **L685-699** | **现金必填校验**：`tendered == null` → 「现金支付必须传 tenderedAmount（实收金额）」（L688）；负数 / 上限 / 不足应付均拒绝 |
| L700-703 | 服务端权威计算找零：`change = tendered - itemAmount` |
| L516-521 | 订单落库：`order.setTenderedAmount(totalTendered)` / `order.setChangeAmount(totalChange)` |
| L634-638 | 响应返回 `tenderedAmount` / `changeAmount`（「服务端权威值，前端照此找零给顾客」） |
| **L744** | 单笔模式归一化：`one.setTenderedAmount(dto.getTenderedAmount()); // M02-04` |
| L765-769 | 支付流水落库：`p.setTenderedAmount(tendered)` / `p.setChangeAmount(serverSideChange)` |

### 2.2 前端：请求体里根本没有 `tenderedAmount`

`frontend/meiyetec-web/src/views/pos/checkout.vue` L728-736（`confirmPay`）：

```ts
const body = {
  payments: payLines.value.map(l => ({
    method: l.method,
    amount: l.amount,
    transactionId: ['cash', 'package'].includes(l.method) ? null : `${l.method.toUpperCase()}-${Date.now()}-...`,
    memberCardId: l.method === 'package' ? 1 : null  // TODO 实际场景让用户选具体次卡
  }))
}
const res: any = await orderApi.pay(currentOrderId.value, body)
```

- 4 个字段里**没有 `tenderedAmount`**；
- 全仓检索：**`frontend/` 目录下 `tendered` 命中数 = 0**（不只 `checkout.vue`，整个前端工程都没有这个概念）；
- `api/pos.ts:25` → `POST /pos/orders/{id}/pay`，请求体原样透传，无任何补字段的中间层。

### 2.3 前端默认就是「现金」，且无实收输入控件

| 行号 | 内容 |
|---|---|
| L675 | `const newPayMethod = ref<string>('cash')` —— **默认支付方式 = 现金** |
| L704 / L710 / L772 | 每次添加支付行 / 打开支付弹窗 / 支付后重置，均把方式重置为 `'cash'` |
| L290-301 | 支付方式下拉 + 金额输入框（`el-input-number` 绑 `newPayAmount`）—— **只有「应付金额」输入，没有「实收金额」输入** |
| L673 | `interface PayLine { method: string; amount: number }` —— **支付行结构体里就没有实收字段** |

### 2.4 前端也没有任何找零展示

- `checkout.vue` 全文检索 `找零` / `changeAmount` → **0 处命中**；
- L634-638 后端已把 `tenderedAmount` / `changeAmount` 放进响应体，但前端 `confirmPay`（L736-774）**没有读取这两个字段**，也没有任何「应找零 ¥xx」的展示位；
- 唯一带「实收」字样的是 **L121** 的表头 `label="实收"`，而该列渲染的是 `row.finalPrice`（**应付款**）——表头语义与内容不符，属同一功能区的口径混淆。

---

## 3. 影响

| 影响面 | 说明 |
|---|---|
| **现金收款在当前 UI 下不可用** | 默认方式为现金 → 前端不传 `tenderedAmount` → 后端 L688 拒绝 → 前端 L776 弹出「支付失败：现金支付必须传 tenderedAmount（实收金额）」 |
| **非现金支付可成功，但找零恒为 0** | 服务端拿不到实收输入 → `totalTendered = 0` → `changeAmount` 恒为 0 → M02-04 的核心价值未兑现 |
| **收银员拿不到应找零金额** | 后端 L634-638 已返回权威找零值，前端未读取、未展示 → 服务端算出的值无人消费 |
| **收银闭环实际断裂** | 与 I129 叠加：I129 修好列后，「落库找零」可跑；但「收银员输入实收 → 拿到找零」这条**人机交互链路仍然不存在** |

> **与 I129 的关系**：I129 是**库侧缺列**（后端 SELECT 报错），I132 是**前端契约缺口**（请求体缺字段）。
> 两者独立：即使 I129 完全修复，只要前端不传 `tenderedAmount`，现金支付仍会被拒。
> **但 I129 是 I132 的前置**——列没补齐时，M02-04 的服务端找零逻辑连落库都做不到。

---

## 4. 修复方向（待批次二统一评审，未实施）

| # | 动作 | 位置 |
|---|---|---|
| 1 | 支付行结构体补实收字段 | `views/pos/checkout.vue:673` `interface PayLine` 增 `tenderedAmount?: number` |
| 2 | 现金方式下增加「实收金额」输入框 | `views/pos/checkout.vue:290-301` 支付方式区；选中 `cash` 时显示实收输入（默认等于应付，可上浮） |
| 3 | 提交时带上 `tenderedAmount` | `views/pos/checkout.vue:728-735` body 映射；**仅 `method === 'cash'` 且实收 ≥ 应付时携带**（非现金携带会被后端 L681-683 直接拒绝） |
| 4 | 展示服务端返回的找零 | `views/pos/checkout.vue:736` 之后读取 `res.data.tenderedAmount` / `res.data.changeAmount`，在支付成功弹层显示「实收 ¥xx / 找零 ¥xx」 |
| 5 | 前端预校验与后端口径对齐 | 实收 < 应付 → 前端先拦截（对应后端 L696-699 的报错文案） |
| 6 | （附带）修正表头语义 | `views/pos/checkout.vue:121` `label="实收"` 实际渲染 `finalPrice`（应付款）→ 改为「实付」/「应付」，或改渲染真实实收列 |

---

## 5. 证据清单

| 文件 / 位置 | 内容 |
|---|---|
| `frontend/meiyetec-web/src/views/pos/checkout.vue`（全文） | `tendered` / `找零` / `changeAmount` 命中数均为 **0** |
| `frontend/meiyetec-web/src/views/pos/checkout.vue:673` | `interface PayLine { method: string; amount: number }`（无实收字段） |
| `frontend/meiyetec-web/src/views/pos/checkout.vue:675` | `newPayMethod = ref<string>('cash')`（默认现金） |
| `frontend/meiyetec-web/src/views/pos/checkout.vue:290-301` | 支付方式下拉 + 金额输入（无实收输入） |
| `frontend/meiyetec-web/src/views/pos/checkout.vue:728-736` | 提交请求体（4 字段，无 `tenderedAmount`） |
| `frontend/meiyetec-web/src/views/pos/checkout.vue:121` | 表头 `label="实收"` 渲染 `row.finalPrice`（应付款） |
| `frontend/meiyetec-web/src/api/pos.ts:25` | `pay: (id, body) => request.post('/pos/orders/${id}/pay', body)` |
| `backend/src/main/java/com/meiyetec/dto/PosPayDTO.java:35` / `:52` | `tenderedAmount` 字段定义（单笔 / PaymentItem） |
| `backend/src/main/java/com/meiyetec/controller/PosController.java:681-703` | 现金必填校验 + 非现金禁传 + 服务端权威算找零 |
| `backend/src/main/java/com/meiyetec/controller/PosController.java:516-521` / `:634-638` / `:744` / `:765-769` | 订单落库 / 响应返回找零 / 单笔归一化 / 支付流水落库 |

**绑定基线**：commit `e60376aac762a1fde45557ca1a21513388f57031` /
DB `meiyetec@localhost:3307`（MySQL 8.0.46）/
DB snapshot：`backup/meiyetec_20261025_020004.sql` / env：`audit/baseline/env.md`

---

## 6. 未确认项

1. **现金支付在前端的实际失败形态** —— 本单为**静态推断**：前端不传 `tenderedAmount` + 后端 L688 拒绝 → 必失败。
   **未做浏览器实测**（受能力边界限制，不能替人工验收）→ 需人工在 `/pos/checkout` 选现金提交一次，确认报错文案与是否影响订单状态。
2. **后端拒绝后是否留下脏数据** —— 校验发生在 `computeChangeServerSide`（L490-491，早于写库），推断**不会**写库；
   但**订单状态是否被提前改动**（`order.setPaidAmount` 等在 L511 之后）→ **未运行验证**。
3. **非现金方式在当前环境的真实可用性** —— `wallet / wechat / alipay / unionpay / e_cny_soft / e_cny_hard / package` 各自的真实链路是否可完成支付，
   本单**只核了「不传实收时不会被拒」这一点**，未逐一验证 → **未确认**。
4. **前端是否应改用「不传 tenderedAmount 即视为全额实收」的兜底口径** —— 涉及业务语义（现金全额找零 0 是否合理），
   属产品决策 → **未确认**，本单按「前端补输入框」给方向，未定唯一方案。
