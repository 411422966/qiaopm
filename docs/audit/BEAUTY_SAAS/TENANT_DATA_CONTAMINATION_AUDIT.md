# TENANT_DATA_CONTAMINATION_AUDIT

> **Step 1 — Read-Only Tenant 污染审计**
> **执行时间**: 2026-08-13 23:40
> **审计脚本**:
> - `audit_tenant_pollution.sql`
> - `audit_tenant_pollution_v2.sql`
> - `audit_tenant_pollution.out`
> - `audit_tenant_pollution_v2.out`

---

## 1. 数据库可达性

✅ MySQL `127.0.0.1:3307` 可连接，schema `meiyetec` 存在。

## 2. 关键发现

### 2.1 sys_tenant 表 - 只有 1 个租户

```sql
SELECT * FROM sys_tenant;
-- 1	default	默认租户	1
```

**结论**：当前系统运行时只有 1 个租户 (`id=1, code=default, name=默认租户, status=1`)。

### 2.2 sys_user 表 - 只有 1 个用户

```sql
SELECT id, username, status, tenant_id FROM sys_user LIMIT 5;
-- 1	admin	1	1
```

**结论**：仅 admin 一个用户，tenant_id=1。

### 2.3 受 PosController 硬编码 tenant_id=1L 影响的表

| Table | 总行数 | tenant_id=1 行数 | 备注 |
|-------|--------|------------------|------|
| `crm_wallet_transaction` | 24 | 24 | ⚠️ PosController:300,395 写入 |
| `crm_wallet` | 102 | 58 | 44 行 tenant_id IS NULL（来自 `WalletController.ensureWallet` 路径） |
| `pos_order` | 188 | 188 | ⚠️ PosController createOrder **未显式 setTenantId**（依赖 MyBatis 拦截器或 DB 默认） |
| `pos_payment` | 186 | 186 | ⚠️ PosController processOnePayment 同上 |
| `pos_order_item` | 108 | 108 | 同上 |
| `crm_package_redemption` | 1 | 1 | ⚠️ PosController.redeemPackage (line 357) INSERT 也未指定 tenant_id |

### 2.4 跨租户污染检查

```sql
SELECT COUNT(*) AS polluted_rows
FROM crm_wallet_transaction wt
JOIN crm_member m ON m.id = wt.member_id
WHERE wt.tenant_id <> m.tenant_id OR (wt.tenant_id IS NOT NULL AND m.tenant_id IS NULL);
-- 0
```

**结论**：当前**不存在**跨租户污染（仅因只有 1 个租户）。

---

## 3. 实际污染判读

### 3.1 当前态（运行时）

| 表 | 行数 | 实际污染？ | 原因 |
|----|------|------------|------|
| `crm_wallet_transaction` | 24 | **0 行污染** | 所有行 tenant_id=1 恰好等于该会员所属租户 id=1 |
| `crm_wallet` | 102 | **0 行污染** | 同上 |
| `pos_order` | 188 | **未知** | tenant_id=1，但 PosController 未显式设置，可能依赖 MyBatis MetaObjectHandler 或 DB DEFAULT |
| `pos_payment` | 186 | **未知** | 同上 |
| `pos_order_item` | 108 | **未知** | DB DEFAULT 1 |

### 3.2 潜在风险

| 风险 | 严重度 | 说明 |
|------|--------|------|
| 多租户加入时数据错标 tenant_id=1 | P0 | 一旦 `sys_tenant` 新增 id=2,3...，当前两条硬编码路径写入 `crm_wallet_transaction` 会被全部误标为 id=1 |
| pos_order / pos_payment / pos_order_item 缺 tenantId 赋值 | **非本任务范围** | 这些表 PosController 没写 `setTenantId`，但 tenant_id=1 是通过其他途径（MyBatis 拦截器或默认值）写入，需另行审计 |
| `crm_wallet` 中 44 行 tenant_id IS NULL | **非本任务范围** | 来自 WalletController.ensureWallet 路径，需另行审计 |

---

## 4. UNVERIFIED 项

| 项 | 状态 | 原因 |
|----|------|------|
| PosController 创建的 pos_order 是否真的由 MyBatis 拦截器自动填 tenant_id | UNVERIFIED | 项目是否配置 MybatisMetaObjectHandler 未核验（不在本任务范围） |
| PosController 创建的 pos_payment 同上 | UNVERIFIED | 同上 |
| PosController 创建的 pos_order_item 同上 | UNVERIFIED | 同上 |

---

## 5. 修复后预期行为

| 修复前（当前） | 修复后 |
|----------------|--------|
| Tenant 2 触发 PosController 钱包消费 → `crm_wallet_transaction.tenant_id=1` ❌ | Tenant 2 触发 → `crm_wallet_transaction.tenant_id=2` ✅ |
| Tenant 2 触发 PosController 退款 → `crm_wallet_transaction.tenant_id=1` ❌ | Tenant 2 触发 → `tenant_id=2` ✅ |

---

## 6. 不在本任务处理范围

为避免 scope 蔓延，以下问题**已记录但不处理**：

1. ✅ `crm_wallet` 中 44 行 tenant_id NULL（属于 TASK-P0-MONEY-002 双轨问题，本任务触及 PosController wallet path，间接相关但不是 tenant_id 硬编码本身）
2. ✅ `pos_order` / `pos_payment` / `pos_order_item` 未显式 setTenantId（需要审计 MyBatis 是否自动填，或需另开 TASK 处理）
3. ✅ 历史已存在的所有 tenant_id=1 数据（属于 Tenant Data Remediation，不在本 P0-004 范围内）
4. ✅ 其他任何模块的 tenant_id 使用

---

## 7. 结论

- **当前不存在显性跨租户污染**（只有 1 个租户）
- **存在潜伏的 P0 风险**：一旦多租户开启，PosController 钱包消费/退款路径将错误地把所有租户的 wallet_transaction 标记为 tenant_id=1
- **修复两处硬编码 `1L` 是必要且充分的 P0-MONEY-004 修复**
- **修复不影响现有数据**（仅修改未来写入路径）
- **不修改任何历史数据**

---

**审计完成时间**: 2026-08-13 23:42
**仅记录事实，未修改任何数据**
