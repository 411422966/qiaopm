# I176 — `LtvSeedController` `mode=reset` 删不存在的表 `crm_order`，dev/test 环境留下半破坏状态

> **状态**：Open（**登记，不修** —— 修复方向需业务先确认 `mode=reset` 的真实语义）
> **严重度**：P1 —— dev/test 环境下守卫放开会留下「快照已删、订单未删、数据未恢复」的半破坏状态
> **发现日期**：2026-11-14（I170 部分修复批次的实施过程中发现）
> **登记人**：Cursor（I170 部分修复批次）
> **关联**：I170（父 issue，本项即 I170 §7.1「本批新发现」的落盘）、I152（`crm_order` / `crm_order_item` 引用外溢）

---

## 0. 一句话描述

`LtvSeedController.seed()` 的 `mode=reset` 分支依次执行三条 DELETE，第二条指向**库中不存在的表 `crm_order`** → 在 dev/test 环境放开守卫后会抛出 `SQLException`，此时 `crm_ltv_snapshot` **已被整租户删空**，而订单与会员**未清理、数据未重建**。

---

## 1. 缺陷

### 1.1 位置

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/controller/LtvSeedController.java` |
| 方法 | `seed(String mode)` 的 `if ("reset".equals(mode))` 分支 |
| 行号 | **L67**（`int orderDel = jdbc.update("DELETE FROM crm_order WHERE tenant_id = ?", tenantId);`） |
| 路由 | `POST /intel/ltv/seed`，请求体 `{"mode":"reset"}` |
| 上下文 | L66 删 `crm_ltv_snapshot`（**该表存在**）→ L67 删 `crm_order`（**该表不存在**）→ L68 删 `crm_member`（该表存在，但**永远执行不到**） |

### 1.2 错在哪

| 对照项 | 实际表名 | 来源 |
|---|---|---|
| `mode=reset` 的 DELETE 语句 | `crm_order` ❌ | `LtvSeedController.java:67` |
| 同文件 `generateDemoMembersAndOrders()` 的 INSERT | **`pos_order`** ✅ | `LtvSeedController.java` 订单生成段（`INSERT INTO pos_order(...)`） |
| 库中实际存在的表 | `pos_order`（1848 行） | 本机开发库实测（2026-11-14） |

即：**删的表 ≠ 生成的表**。这属于 I152（`crm_order` 引用外溢，仓内 13 个文件 21 处 SQL 指向不存在的表）在种子数据生成器上的一个实例。

### 1.3 失败时序（dev/test 环境）

```
POST /intel/ltv/seed {"mode":"reset"}
  │
  ├─ ① L66  DELETE FROM crm_ltv_snapshot WHERE tenant_id = ?   → 成功（删光整租户快照）
  ├─ ② L67  DELETE FROM crm_order WHERE tenant_id = ?          → SQLException（表不存在）
  │      ↓
  │   抛出异常 → 请求 500 → 快照已被删空
  │      ↓
  ├─ ③ L68  DELETE FROM crm_member ...                        → 永远执行不到
  └─ ④ L70+ generateDemoMembersAndOrders() / generateLtvSnapshots() → 永远执行不到
```

**后果**：租户的 LTV 快照被清空，订单与会员**保持原样**（未删），示例数据**未重建** —— 处于「半破坏」状态。

### 1.4 影响面

| 环境 | 是否触发 | 说明 |
|---|---|---|
| **生产（prod）** | ❌ 不触发 | I170 已加环境守卫：`assertSeedEnvironmentAllowed("reset")` 在**进入 DELETE 之前**抛 `BusinessException.forbidden("生产环境禁止执行种子重置")` → HTTP 403。实测：prod profile 下调用返回 403，行数前后不变（4594 / 489 / 1848） |
| **dev / test** | ⚠️ **会触发** | 守卫放行（profile 含 `dev` 或 `test`），`mode=reset` 直接进入 DELETE 分支 → 半破坏 |
| **未设 `SPRING_PROFILES_ACTIVE`** | ❌ 不触发 | 兜底值为 `prod`（`@Value("${spring.profiles.active:prod}")`），仍拦截 —— fail-safe |

---

## 2. 证据

| 项 | 值 |
|---|---|
| 库中 `crm_order` 表 | **不存在**（实测）；同库 `pos_order` = 1848 行 |
| 代码行 | `LtvSeedController.java:67` |
| 生成逻辑写入表 | `pos_order`（同文件 `generateDemoMembersAndOrders`） |
| 生产守卫实测 | prod profile 下 `POST /intel/ltv/seed {"mode":"reset"}` → **HTTP 403**「生产环境禁止执行种子重置」，行数前后不变 |
| 证据目录 | `audit/evidence/F166-ltv-snapshot-idempotency/`（`raw/04-seed-env-guard.txt`） |
| 取证时间 | 2026-11-14 |

---

## 3. 影响面小结

| 项 | 影响 |
|---|---|
| dev/test 环境 LTV 数据 | `mode=reset` 后快照被清空且不重建，LTV 看板/趋势/训练样本在该租户直接断档 |
| 生产环境 | **无影响**（守卫先行拦截） |
| 训练链路 | `crm_ltv_snapshot` 是 LTV 训练特征来源（见 I142 / I155），被清空后训练样本不足 |
| 运维/演示环境可信度 | seed 端点在 dev 下**永远无法成功执行 reset**（必然 500），等于半残功能 |

---

## 4. 修复方向（**二选一，需业务确认 `mode=reset` 的真实语义**）

### 方案 A：改为 `DELETE FROM pos_order WHERE tenant_id = ?`（与生成逻辑一致）

- 与 `generateDemoMembersAndOrders()` 写入的表**对齐**，reset 语义变成「清空 → 重建」的完整闭环。
- ⚠️ **会真删真实收银订单**：本机 `pos_order` 有 **1848 行**，这些是**真实收银数据**而非种子数据。**必须附加「仅限测试租户」守卫**（例如白名单 tenantId / 独立的 `SEED_ALLOWED_TENANT_IDS` 配置），否则一次误操作即造成真实订单丢失，且不可从快照恢复。
- 附带建议：三条 DELETE + 重建应包在 `@Transactional(rollbackFor = Exception.class)` 内，杜绝「删一半」的中间态。

### 方案 B：删除该 DELETE 语句，`mode=reset` 只清快照不清订单（语义更保守）

- `reset` 只删 `crm_ltv_snapshot`（+ 可选 `crm_member`），**不碰 `pos_order`**。
- 优点：不触碰真实收银订单，语义保守；配合既有环境守卫即足够安全。
- 缺点：重建出来的示例会员/订单会与真实订单**共存**（`generateDemoMembersAndOrders` 会新插 100 会员 + ~1000 订单），`reset` 不再是「干净重来」，快照口径会被新插入的演示订单污染。

> 🔒 **技术侧不单方面选定方案** —— 「`mode=reset` 到底是想清什么」是业务/工具语义问题：
> - 若诉求是「把演示数据整体清干净再重造」→ 方案 A（+ 测试租户守卫）；
> - 若诉求是「只想让 LTV 快照回到干净状态，不要动真实收银数据」→ 方案 B。

---

## 5. 待业务确认

| # | 问题 |
|---|---|
| Q1 | `mode=reset` 的语义是「清空整个租户的会员+订单+快照，重建演示数据」还是「只重置 LTV 快照」？ |
| Q2 | 若取方案 A，**允许执行 reset 的租户**如何界定（白名单 tenantId？专用测试租户？`SEED_ALLOWED_TENANT_IDS` 配置项？） |
| Q3 | 三条 DELETE + 重建是否必须包事务（`rollbackFor = Exception.class`）以杜绝半破坏？ |

---

## 6. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 生产环境 `pos_order` 是否存在、是否有真实收银订单 | **未确认** —— 生产库不可访问（仅本机开发库实测） |
| 是否有其它环境（除 dev/test/prod）会放开守卫 | **未确认** —— 取决于部署时 `SPRING_PROFILES_ACTIVE` 的取值 |
| 是否已有演示/运维人员依赖当前 `reset` 的**报错**行为 | **未确认** —— 需运维确认 |

---

## 7. 本轮处置

- **本轮只登记，不改代码**（用户明确指示「不动 I176（登记，不修）」）。
- 生产风险已由 I170 的环境守卫覆盖（403 实测有效）。
- 与 I170 §7.1 的关系：该项即 I170 §7.1 的正式落盘。
