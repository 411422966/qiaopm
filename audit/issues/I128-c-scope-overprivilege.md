# I128-c — sys_role_data_scope 数据越权修复（manager01 跨租户）

- **批次**：I128-c
- **日期**：2026-11-05
- **状态**：VERIFIED（数据层已修复并验证；业务代码未改动）
- **父批次**：I128（数据库中文乱码战役）/ I128-b（乱码遗留登记）
- **执行脚本**：`backup/i128/fix-i128-c-scope.sql` / `backup/i128/fix-i128-c-verify.sql`
- **修复前快照**：`backup/i128/pre-fix-scope-snapshot-20261105.sql`（mysqldump 全表，2,232 字节）
- **数据库**：localhost:3307 / meiyetec / MySQL 8.0.46

---

## 1. 问题

### 1.1 现象

`sys_role_data_scope` 表中存在两条**错误关联**的数据范围记录：

| id | role_id | 实际挂载角色 | data_scope | remark | 来源脚本 |
|---|---|---|---|---|---|
| 4 | 4 | CLERK（店员） | STORE | 财务仅门店数据 | `init_rbac_v2.sql:242` |
| 5 | 5 | MANAGER（店长） | **ALL** | 平台跨租户数据 | `init_rbac_v2.sql:245` |

`init_rbac_v2.sql` 第 5 节原本设计的是两个独立角色：`FINANCE`（财务，数据范围 STORE）与 `PLATFORM_ADMIN`（平台管理员，数据范围 ALL）。但这两个 `role_code` 在当前 `sys_role` 表中**已不存在**（现存 8 个角色：`SUPER_ADMIN / STORE_ADMIN / EMPLOYEE / CLERK / MANAGER / BOSS / OPERATOR / ADMIN`），其数据范围行却因角色行先被删除而**残留**，随后被 `migrate_r3_0_c_menu_role.sql` 新建的 `CLERK`(role_id=4) 与 `MANAGER`(role_id=5) **接管**。

### 1.2 危害

`MANAGER` 因此拥有 `ALL` 数据范围，**店长账号 manager01 获得跨租户数据访问权**，与 `migrate_r3_0_c_menu_role.sql:25-26` 定义的「店长仅当前门店数据」直接冲突。

### 1.3 归并逻辑（生效判定）

后端**不是**简单二选一，而是把所有角色范围行**拼起来取最宽松者**：

```95:95:backend/src/main/java/com/meiyetec/security/RbacContextLoader.java
        List<SysRoleDataScope> scopes = dataScopeMapper.listByUserId(userId);
```

```14:17:backend/src/main/java/com/meiyetec/mapper/SysRoleDataScopeMapper.java
    @Select("SELECT srd.* FROM sys_role_data_scope srd " +
            "INNER JOIN sys_user_role ur ON srd.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<SysRoleDataScope> listByUserId(@Param("userId") Long userId);
```

| 行号（`RbacContextLoader.java`） | 行为 |
|---|---|
| L117 `if (r < rank)` | rank 越小越宽松（ALL=0 / BRAND=1 / STORE=2 / SELF=3）→ **最宽松者胜出** |
| L122 `hasAll = true` | 任一条为 ALL 即置位 |
| L134-135 | `hasAll` → `storeIds = {-1L}`（通配全部门店） |

**结论：MANAGER 的 `ALL` 行完全生效，其 `STORE` 行（id=12）被吞掉。**

### 1.4 `ALL` 生效后被绕过的隔离点

| 位置 | 行为 |
|---|---|
| `DataScopeService.applyStoreScope:48` | 不追加 `store_id` 条件 |
| `DataScopeService.assertTenantScope:88` | 跨租户直接放行 |
| `TenantContextHolder.requireTenant:59` | 跨租户放行 |
| `EcommerceService:80 / 119 / 230 / 494` | 不加 `tenant_id` 过滤 |
| `CrmIntegrationService:121` | 不加 `tenant_id` 过滤 |

---

## 2. 影响范围

`sys_user_role` 全量分布（每角色恰好 1 个测试账号，全部 `tenant_id=1`）：

| user_id | username | 角色 | 修复前有效 scope | 是否受影响 |
|---|---|---|---|---|
| 1 | admin | SUPER_ADMIN + ADMIN | ALL | 否（设计内） |
| 900003 | clerk01 | CLERK | STORE | 数据语义受影响，查询行为不变 |
| 900004 | manager01 | MANAGER | **ALL** | **是，P0 跨租户越权** |
| 900005 | boss01 | BOSS | STORE | 否 |
| 900006 | operator01 | OPERATOR | STORE | 否 |

### 2.1 为何 id=4 不能裸删

`sys_role_data_scope` 存在唯一键 `uk_role_scope(role_id, data_scope)`（`init_rbac_v2.sql:30`）。id=4 以 `(4, 'STORE')` 占位，导致 `migrate_r3_0_c_menu_role.sql:22-23` 给 CLERK 插入的 `('店员仅当前门店数据')` 被 `INSERT IGNORE` **静默跳过** —— CLERK 至今没有自己的数据范围行，id=4 是它唯一的一行。

裸删 id=4 后 CLERK 将落入 `RbacContextLoader.java:96-99` 的 `scopes.isEmpty()` 分支 → `dataScope="SELF"` + `storeIds` 空集。由于 `clerk01.store_id = NULL`，两条路径（`applyStoreScope:50-53` 写 `store_id = -9999`）查询行为一致，但 CLERK 会**失去显式数据范围配置**，退化为隐式 SELF 兜底。

---

## 3. 修复方案

### 3.1 三步操作

| 步骤 | 动作 | 影响行数（首次） |
|---|---|---|
| §2-a | `DELETE ... WHERE id=5 AND role_id=5 AND data_scope='ALL'` | 1 |
| §2-b | `DELETE ... WHERE id=4 AND role_id=4 AND data_scope='STORE'` | 1 |
| §3 | `INSERT ... SELECT ... WHERE role_code='CLERK' AND NOT EXISTS(同 role_id+data_scope)` | 1 |

三重条件保护（id + role_id + data_scope 全等）确保内容不符时不会误删。

### 3.2 幂等性

二次执行影响行数全为 0（`deleted_id5=0` / `deleted_id4=0` / `inserted_clerk=0`），无报错。

### 3.3 权威对照

| 角色 | 期望 scope | 依据 |
|---|---|---|
| SUPER_ADMIN | ALL | `init_rbac_v2.sql:41-42` |
| ADMIN | ALL | `migrate_r3_0_c_menu_role.sql:32-34` |
| STORE_ADMIN | STORE | `init_rbac_v2.sql:43-44` |
| EMPLOYEE | STORE | `init_rbac_v2.sql:45-46` |
| CLERK | STORE | `migrate_r3_0_c_menu_role.sql:22-23` |
| MANAGER | STORE | `migrate_r3_0_c_menu_role.sql:25-26` |
| BOSS | STORE | `migrate_r3_0_c_menu_role.sql:28-29` |
| OPERATOR | STORE | `migrate_r3_0_c_menu_role.sql:31` |

---

## 4. 验证结果

修复后 `sys_role_data_scope` 全表（8 个角色各 1 条，严格 1:1）：

| id | role_id | role_code | data_scope | remark |
|---|---|---|---|---|
| 1 | 1 | SUPER_ADMIN | ALL | 租户内全部数据 |
| 2 | 2 | STORE_ADMIN | STORE | 仅当前门店数据 |
| 3 | 3 | EMPLOYEE | STORE | 仅当前门店数据 |
| 12 | 5 | MANAGER | STORE | 店长仅当前门店数据 |
| 13 | 6 | BOSS | STORE | 老板可见全部门店 |
| 14 | 7 | OPERATOR | STORE | 运营可见全部门店 |
| 15 | 8 | ADMIN | ALL | 管理员跨租户 |
| 16 | 4 | CLERK | STORE | 店员仅当前门店数据 |

### 4.1 verify 各段结果

| 段 | 检查内容 | 结果 |
|---|---|---|
| A | 剩余全表 | 8 行，符合预期 |
| B | 每角色映射条数 ≠ 1 | **空**（全部为 1）✅ |
| C | 期望值逐角色比对 | 8 行全 `OK`，无 `MISMATCH` ✅ |
| D | 有效 scope 模拟 | manager01 = `STORE`，无 `VIOLATION-cross-tenant` ✅ |
| E | 非平台角色携带 ALL | **空** ✅ |
| F | 孤儿 scope 行 | **空** ✅ |
| G | FINANCE / PLATFORM_ADMIN 是否存在 | **空**（确认不存在，无需重建）✅ |
| H | 终态计数 | `dirty_rows_remaining=0` / `clerk_store_rows=1` / `manager_all_rows=0` ✅ |

### 4.2 §D 有效 scope 模拟（关键证据）

| user_id | username | scope_ids | scopes | effective_scope | verdict |
|---|---|---|---|---|---|
| 1 | admin | 15 | ALL | ALL | ok |
| 1 | admin | 1 | ALL | ALL | ok |
| 900003 | clerk01 | 16 | STORE | STORE | ok |
| 900004 | manager01 | 12 | STORE | STORE | ok |
| 900005 | boss01 | 13 | STORE | STORE | ok |
| 900006 | operator01 | 14 | STORE | STORE | ok |

### 4.3 §H 终态确认

| dirty_rows_remaining | clerk_store_rows | manager_all_rows |
|---|---|---|
| 0 | 1 | 0 |

### 4.4 安全检查结论

**修改后 MANAGER 走 `STORE`，不再是 `ALL`。** 判定依据：`migrate_r3_0_c_menu_role.sql:25-26` 定义 MANAGER = STORE；`init_rbac_v2.sql:245` 的 ALL 明确归属 PLATFORM_ADMIN；`migrate_r3_0_c_menu_role.sql:32-34` 只有 ADMIN 为 ALL。

修复后 `manager01` 的 `hasAll=false` → `storeIds` 回落到用户 `store_id`（当前为 NULL，故实际可见门店为空，与 clerk01 / boss01 / operator01 现状一致），§1.4 列出的全部跨租户放行点关闭。

---

## 5. FINANCE / PLATFORM_ADMIN 现状确认

`sys_role` 当前 8 个角色中**无 FINANCE、无 PLATFORM_ADMIN**。代码中的残留引用均为防御性写法，删除 id=4/id=5 不影响任何现役账号：

| 引用点 | 性质 | 影响 |
|---|---|---|
| `RbacContextLoader.java:78-79` | SUPER_ADMIN/PLATFORM_ADMIN → 通配 `*` | 无（admin 走 SUPER_ADMIN 分支） |
| `RbacService.java:203` | deleteRole 保护 SUPER_ADMIN/PLATFORM_ADMIN | 无（角色不存在） |
| `frontend/meiyetec-web/src/views/system/role.vue:204` | `BUILT_IN` 白名单含 PLATFORM_ADMIN | 无 |
| `frontend/meiyetec-web/e2e/rbac-permissions.spec.ts:57` | 断言 admin 有 SUPER_ADMIN **或** PLATFORM_ADMIN | 无 |
| `backend/src/test/java/com/meiyetec/service/RbacServiceLoadSessionTest.java:93` | 同上 | 无 |
| `frontend/meiyetec-web/src/views/dev/DataHub.vue:206,266` | `FINANCE` 是指标分类 category，非角色 | 无关 |

平台管理员职责已由 `ADMIN`(role_id=8, ALL, id=15) 承载。

---

## 6. 遗留观察（不单开 issue，待后续批次处理）

### 6.1 注释与实现相反

`SysRoleDataScope.java:13-15` 与 `TenantContext.java:15` 的注释均写「计算时取**最严格**集合（ALL < BRAND < STORE < SELF）」，但 `RbacContextLoader.java:117` 实际取的是**最宽松**者（`if (r < rank)`，rank 越小越宽松）。

注释与实现相反比数据错误更隐蔽 —— 后续维护者若按注释理解，会误判多角色用户的实际可见范围。建议单独立 issue 修正注释（属业务代码，本次未改动）。

### 6.2 测试账号 store_id 全为 NULL（**已由 I128-d 修复，2026-11-05**）

`clerk01`(900003) / `manager01`(900004) / `boss01`(900005) / `operator01`(900006) 的 `sys_user.store_id` 均为 NULL，导致这些非 admin 角色在 STORE 范围下 `storeIds` 为空集，`applyStoreScope` 写入 `store_id = -9999`，实际查不到门店数据。

**处置结果**：已新建 `backup/i128/fix-test-user-store.sql` + `fix-test-user-store-rollback.sql`，把 4 个账号的 `store_id` 更新为 `pos_store.id = 1`（美业SaaS示范店 / STORE001，`status=1`、`deleted=0`、`tenant_id=1`，为当前唯一有效门店）。实测 `affected = 4`，admin（`store_id` 仍为 NULL，走 `dataScope=ALL`）未被误伤；归并后 `effective_store_filter` 由 `EMPTY->-9999` 变为 `IN(1)`。同步在 `backend/src/main/resources/db/sql/seed_test_roles.sql` 的 INSERT 中补上 `store_id = 1` 并追加幂等回填 UPDATE，避免重置数据库后复现。

### 6.3 BOSS / OPERATOR 维持 STORE（用户决策，2026-11-05）

`sys_role_data_scope` 中 BOSS(role_id=6) / OPERATOR(role_id=7) 的 `remark` 写「老板可见全部门店」「运营可见全部门店」，但 `data_scope = 'STORE'` 且 `data_scope_value = NULL` —— 注释语义与实现不一致。

**决策：维持现状（仍为 STORE），不改为 BRAND / ALL。** 当前多门店数据不完整，BOSS/OPERATOR 暂按 STORE 处理，等未来补齐门店数据后，再由业务确认是否放宽至 BRAND 或 ALL。

依据：`pos_store` 仅 1 家有效门店（`id=1`），其余 5 行全部 `deleted = 1`；放宽为 ALL/BRAND 在当前数据下不产生实际收益，反而重新引入 I128-c 修复的越权敞口（`manager01` 跨租户放行即由此类配置引发）。此外 BOSS/OPERATOR 可见范围随门店数据补齐需重新评估 —— 见 `audit/issues/I128-e-member-null-store.md`（`crm_member` 369 行 `store_id` NULL，暂不修）。

---

## 7. 涉及文件

| 文件 | 变更 |
|---|---|
| `backup/i128/fix-i128-c-scope.sql` | 新增（52 行，修复脚本） |
| `backup/i128/fix-i128-c-verify.sql` | 新增（89 行，验证脚本 A–H 段） |
| `backup/i128/pre-fix-scope-snapshot-20261105.sql` | 新增（mysqldump 修复前全表快照） |
| `audit/issues/I128-c-scope-overprivilege.md` | 新增（本文件） |

**后续批次产出**（不属于 I128-c 本身，列此便于追溯）：

| 文件 | 批次 | 变更 |
|---|---|---|
| `backup/i128/fix-test-user-store.sql` | I128-d | 新增（测试账号 `store_id` 修复脚本） |
| `backup/i128/fix-test-user-store-rollback.sql` | I128-d | 新增（回滚脚本） |
| `backend/src/main/resources/db/sql/seed_test_roles.sql` | I128-d | 修改（seed INSERT 补 `store_id = 1` + 幂等回填） |
| `audit/issues/I128-e-member-null-store.md` | I128-e | 新增（`crm_member.store_id` 缺失登记，暂不修） |

**未改动**：任何 Java / Vue 业务代码；`docs/governance/*.md`、`audit/baseline/*.md`、`audit/issues/issue-register.md`、`PROJECT_ACCEPTANCE_EVIDENCE.md`、`.gitignore` 全部未触碰。

**未触碰页面**：`/dashboard`、`/mh/visual`、`/mh/consent`。
