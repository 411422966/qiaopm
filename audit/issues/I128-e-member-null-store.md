# I128-e — crm_member.store_id 数据缺失：369 行 NULL + 3 行孤儿门店引用

- **状态**：Open（登记后暂不修复）
- **发现批次**：I128-d 收尾调查（2026-11-05）
- **父批次**：I128（数据库中文乱码战役）/ I128-c（scope 越权）/ I128-d（测试账号 store_id）
- **严重度**：🟡 中（影响非 admin 角色客户列表完整度，不阻断核心流程）
- **影响范围**：`crm_member` 表 / 客户（CRM 会员）列表与统计类接口
- **是否阻塞上线**：否（PARTIAL-NON-BLOCKER）

---

## 1. 现象

I128-d 修复测试账号 `store_id` 后，非 admin 角色的数据范围过滤从 `store_id = -9999` 恢复为 `store_id IN (1)`。
但 `crm_member` 表本身存在门店归属缺失，修复后仍有大量会员对非 admin 角色不可见。

### 1.1 数据分布（`meiyetec` @ 127.0.0.1:3307，2026-11-05 实测）

| 口径 | 数值 |
|---|---|
| `crm_member` 总行数 | 507 |
| `deleted = 0` 有效行 | 491 |
| `deleted = 0 AND store_id IS NULL` | **369** |
| `deleted = 0 AND store_id = 1` | 119 |
| `deleted = 0 AND store_id = 2` | **3**（孤儿引用） |
| `deleted = 0 AND tenant_id IS NULL` | 2 |

按租户维度：

| tenant_id | store_id | 行数 |
|---|---|---|
| 1 | NULL | **369** |
| 1 | 1 | 117 |
| 1 | 2 | **3** |
| NULL | 1 | 2 |

### 1.2 两条独立缺陷

**缺陷 A：`store_id IS NULL`（369 行，75%）**
`store_id IN (1)` 语义下 NULL 不匹配 → 这些会员在 clerk01 / manager01 / boss01 / operator01 账号下不可见（admin 因 `dataScope=ALL` 可见）。
典型样本：`id=1061..1065`，`member_name` 亦为 NULL，`create_time = 2026-08-23 16:36:48`（同批次导入，疑为历史 seed 脚本未带门店字段）。

**缺陷 B：`store_id = 2` 孤儿引用（3 行）**
`pos_store` 实际有效门店仅 `id = 1`（另有 id=5/9/13/17/21 全部 `deleted = 1`），**不存在 id = 2**。
即使把 `data_scope_value` 放开到含门店 2，这 3 行也永远匹配不到任何门店。

---

## 2. 影响链路

| 环节 | 位置 | 现象 |
|---|---|---|
| 数据范围归并 | `backend/src/main/java/com/meiyetec/security/RbacContextLoader.java:136-139` | `storeIds = {1}`（来自 I128-d 兜底），NULL 行被 `IN` 排除 |
| 查询过滤 | `backend/src/main/java/com/meiyetec/security/DataScopeService.java:50-57` | `wrapper.in(store_id, [1])`；NULL 不参与匹配 |
| 写入门禁 | `backend/src/main/java/com/meiyetec/security/DataScopeService.java:93-100` `canCreateInStore` | 新建会员若不显式带 storeId，同样落 NULL |

**可见量对比**：`store_id IN (1)` 下 `crm_member` 仅 119 行可见（另一口径 117 行，差异来自 2 行 `tenant_id IS NULL`）。

---

## 3. 为何暂不修复

1. **属业务数据清洗，非权限缺陷**：本单不涉及越权或契约漂移，纯粹是历史数据的门店归属缺失。
2. **缺少权威归属源**：369 行会员没有可靠依据判定「应属于哪家门店」；当前仅 1 家有效门店，可机械回填为 1，但需业务确认这批会员是否本就是「非门店制」的会员。
3. **无法确认 `member_name IS NULL` 的同批数据是否为有效客户**：若为压测/脏数据，正确处理是删除而非回填 —— 删除不可逆，需业务确认后才能决策。

---

## 4. 待业务确认的问题

| # | 问题 | 候选处置 |
|---|---|---|
| 1 | 369 行 NULL 归属会员是有效客户吗？ | (a) 全部回填 `store_id = 1`；(b) 判定为脏数据并删除；(c) 保留 NULL 并在查询侧放宽（`store_id IS NULL OR store_id IN (...)`）—— (c) 会削弱门店隔离，需评估越权风险 |
| 2 | 3 行 `store_id = 2` 的含义？ | 门店 2 是历史已删门店，还是从未存在？ |
| 3 | 2 行 `tenant_id IS NULL` 的会员归属哪个租户？ | 回填 `tenant_id = 1` 或判定为脏数据 |

---

## 5. 建议处置路径（获批后执行）

1. 先跑**只读排查 SQL**，输出 369 行的 `id / member_name / member_no / phone / create_time` 分布，交业务确认「有效 vs 脏数据」；
2. 对判定为有效的行执行 **UPDATE 带守卫**（`WHERE store_id IS NULL AND tenant_id = 1` + JOIN 校验 `pos_store.id = 1` 有效），禁止无条件 UPDATE；
3. 写操作前对目标 ID 集合做存在性检查，命中即 fail-fast（对齐「自动化测试与数据安全」规则第 20 条）；
4. 处置完成后复核 `crm_member` 可见量与客户列表接口返回。

---

## 6. 涉及文件

| 文件 | 说明 |
|---|---|
| `audit/issues/I128-e-member-null-store.md` | 新增（本文件，问题登记） |

**未改动**：`crm_member` 表数据（未执行任何写操作）、任何 Java / Vue 业务代码。

**未触碰页面**：`/dashboard`、`/mh/visual`、`/mh/consent`。

---

## 7. 关联

- `audit/issues/I128-c-scope-overprivilege.md` §6.2 —— 测试账号 store_id 全为 NULL 的遗留观察（**已由 I128-d 修复**）
- `audit/issues/issue-register.md` —— ❗**本单尚未登记到 issue-register**（该文件属治理基线，未经单独批准不写入），待用户批准后补登记
