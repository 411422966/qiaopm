# I131 — PackageItem 实体缺 deleted 字段（软删除隐患），PackageController 从不写 createBy

> 发现时间：2026-10-27（I129 调查过程中的额外发现 A2 / A3）
> 发现方式：I129 缺列调查时对 `crm_package` 表结构与 `PackageItem` 实体逐字段比对 + 全仓代码静态分析
> 严重级别：**P2**（数据治理 / 审计溯源；当前未触发可见功能故障，但删除动作与设计意图不符）
> 状态：**Open**（未修复）
> 关联：I129（`crm_package` 缺列，本单为同表同实体的另一侧问题）、I120（Entity 字段与 DB 字段命名漂移同类）
> 基线：commit `e60376aac762a1fde45557ca1a21513388f57031`（2026-10-27 23:50:18）/
> DB `meiyetec@localhost:3307`（MySQL 8.0.46）/
> DB snapshot：`backup/meiyetec_20261025_020004.sql`、`backup/meiyetec-before-i129.sql` / env：`audit/baseline/env.md`

---

## 1. 一句话结论

`crm_package` 表按「软删除」设计建好了 `deleted` 列（含 `uk_code(code, deleted)` 联合唯一键），
但 **`PackageItem` 实体根本没有 `deleted` 属性、也没有 `@TableLogic`**，
导致 MyBatis-Plus 的逻辑删除对本实体不生效——

| 问题 | 表现 |
|---|---|
| **A2 软删除隐患** | 实体层查询（列表 / 详情 / 统计）**不带 `deleted=0` 过滤**；`PackageController.java:1401` 的注释「MyBatis-Plus 逻辑删除：deleted=1」与实体现状矛盾，`removeById` 无法写入 `deleted=1` |
| **A3 审计字段缺失** | 表有 `create_by` 列，但 `PackageController` **全文 0 处 `setCreateBy`**，新建套餐的 `create_by` 恒为 `NULL` |

---

## 2. 硬证据

### 2.1 库侧：`crm_package` 的软删除与审计列都在（快照实测）

`backup/meiyetec_20261025_020004.sql`（2026-10-25 官方 02:00 备份）L4742-4767 `crm_package` 建表语句：

```sql
CREATE TABLE `crm_package` (
  ...
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,   -- L4757
  `deleted` tinyint DEFAULT '0',                                   -- L4758  ← 软删除列
  `create_by` bigint DEFAULT NULL,                                 -- L4759  ← 审计列
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,                -- L4760
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`,`deleted`),                         -- L4763  ← 唯一键含 deleted
  ...
);
```

- `backup/meiyetec-before-i129.sql` 中同一张表结构完全一致（L4800 `deleted` / L4801 `create_by` / L4805 `uk_code`），
  说明**这不是备份时点差异，是库的既有设计**；
- `UNIQUE KEY uk_code (code, deleted)` 是典型的**软删除唯一键**写法——设计意图明确为「逻辑删除后允许同 code 重建」。

### 2.2 实体侧：`PackageItem` 没有 `deleted` 字段，也没有 `@TableLogic`

`backend/src/main/java/com/meiyetec/entity/PackageItem.java`（全文 L1-48）字段全集：

```
id / tenantId / name / code / category / cover / description / totalTimes / price /
costPrice / validDays / saleCount / redeemCount / status / remark / createBy(L29) /
bundleType(L32) / bundleConfig(L34) / bundleItemCount(L36) / createTime / updateTime
+ statusLabel、bundleItems（两个 @TableField(exist = false)）
```

- **无 `deleted` 属性**（对比 `entity/PosOrder.java:54-55`：`@TableLogic` + `private Integer deleted;`）；
- **无 `@TableLogic` 注解**（`import com.baomidou.mybatisplus.annotation.*` 已引入，但未使用）。

### 2.3 全局逻辑删除配置对本实体不生效（关键：为什么「配了也没用」）

`backend/src/main/resources/application.yml:53-58`：

```yaml
    global-config:
      db-config:
        id-type: auto
        logic-delete-field: deleted      # L56
        logic-delete-value: 1            # L57
        logic-not-delete-value: 0        # L58
```

MyBatis-Plus 的**全局** `logic-delete-field` 只对**实体自身声明了同名属性**的表生效。
I129 实测生成 SQL（同批同一次验收，I129 §2.1）可直接对照：

```sql
-- pos_order（实体有 @TableLogic deleted）→ 软删过滤生效
SELECT id,tenant_id,...,refunded_amount,refund_count,tendered_amount,change_amount,...
  FROM pos_order WHERE deleted=0 AND (tenant_id = ? AND pay_time >= ?)

-- crm_package（实体无 deleted 属性）→ 无任何 deleted 过滤
SELECT id,tenant_id,name,...,bundle_type,bundle_config,bundle_item_count,...
  FROM crm_package WHERE (tenant_id = ?) ORDER BY id DESC LIMIT ?
```

→ **`crm_package` 的 SELECT 既不查 `deleted` 列，也不加 `deleted=0` 条件。**

### 2.4 列表 / 详情 / 统计接口均不做软删过滤

`controller/PackageController.java:82-105`（`GET /package` 列表）过滤条件只有 4 个：

```java
if (tenantId != null) wrapper.eq(PackageItem::getTenantId, tenantId);
if (keyword != null && !keyword.isBlank()) { wrapper.and(w -> w.like(...getName, keyword).or().like(...getCode, keyword)); }
if (category != null && !category.isBlank()) wrapper.eq(PackageItem::getCategory, category);
if (status != null) wrapper.eq(PackageItem::getStatus, status);
wrapper.orderByDesc(PackageItem::getId);
Page<PackageItem> result = packageItemService.page(page, wrapper);
```

→ 无 `deleted` 条件；`detail`（L111）与 `stats`（L846-848）同样基于 `PackageItem` 的 MyBatis-Plus 查询，同样不过滤。

**唯一的例外**是手写 SQL：`service/P1RemainingService.java:487`

```sql
SELECT id, package_name, price FROM crm_package WHERE deleted=0 AND tenant_id=? LIMIT 20
```

→ 说明**业务代码自身是按「有软删」理解的**，但实体层这条主链路不是。

### 2.5 删除动作与注释矛盾（`PackageController.java:1401`）

```java
packageItemService.removeById(id);   // MyBatis-Plus 逻辑删除：deleted=1
```

- 注释断言是逻辑删除（`deleted=1`）；
- 但实体不映射 `deleted` 列，**没有 `deleted` 可写**（见 §2.2 / §2.3）→ 该注释描述的行为**无法成立**；
- 按 MyBatis-Plus 规则（实体无 `@TableLogic` 且无同名属性 → 该表 `logicDelete=false`），
  `removeById` 走的是**物理 DELETE**（`DELETE FROM crm_package WHERE id = ?`）。
  **此点为静态推断，未取得运行时 SQL 证据 → 见 §6 未确认项 1。**

### 2.6 `create_by` 恒为 NULL（Controller 从不写审计字段）

- `PackageController.java` 全文检索 `setCreateBy` / `createBy` → **0 处命中**；
- 创建分支 `save()`（方法体 L129-194，创建分支 L144-168）逐字段赋值：

```java
PackageItem p = new PackageItem();
p.setTenantId(tenantId);
p.setName(dto.getName());
p.setCode(dto.getCode());
p.setCategory(...);
p.setDescription(dto.getDescription());
p.setPrice(dto.getPrice());
p.setCostPrice(...);
p.setValidDays(...);
p.setSaleCount(0);
p.setRedeemCount(0);
p.setStatus(...);
p.setRemark(dto.getRemark());
// M04 bundle 字段 3 个
packageItemService.save(p);   // L167  ← createBy 从未赋值
```

- 实体侧 `PackageItem.createBy`（L29）存在、DB 侧 `create_by`（快照 L4759）存在 → **两端都具备，仅中间无人写入**。

---

## 3. 影响

| 影响面 | 说明 |
|---|---|
| **已软删套餐可能仍在列表 / 下拉中展示** | 实体层查询无 `deleted=0` 过滤；只要库中存在 `deleted=1` 的 `crm_package` 行，列表、详情、统计、下拉均会带上它 |
| **删除动作不落 `deleted=1`** | 与 `uk_code(code, deleted)` 反映的软删设计不一致；按静态推断实际为物理删除 → 套餐记录不可恢复、删除痕迹不可追溯 |
| **审计溯源缺失** | `create_by` 恒为 `NULL` → 无法回答「这个套餐是谁建的」；`sys_op_log` 若未覆盖该接口则无替代来源 |
| **`uk_code` 唯一键语义与实体行为脱节** | 唯一键含 `deleted` 说明业务预期「同 code 可在软删后重建」；实际物理删除下 code 直接释放，行为虽相近但**审计语义不同** |

> **当前未造成可见功能故障**：本轮未观察到 `crm_package` 相关的 500 或列表脏数据（I129 的 500 已定位为缺列，与本单无关）。
> 本单是**数据治理隐患 + 注释与实现不符**，不是即时功能阻塞。

---

## 4. 修复方向（待批次二统一评审，未实施）

| # | 动作 | 位置 |
|---|---|---|
| 1 | 实体补 `deleted` 字段 + `@TableLogic` | `entity/PackageItem.java`（补 `private Integer deleted;` + `@TableLogic`） |
| 2 | Controller 补 `setCreateBy`（从当前登录用户取） | `controller/PackageController.java:144-168` 创建分支，参考项目内其他 Controller 的取值口径（如 `TenantContextHolder` / `SecurityUtils` 的当前用户 id） |
| 3 | 补 `deleted` 后需回归 `uk_code` 唯一键行为 | MySQL 8.0 唯一键含 `deleted` 列 → 需确认 `save` 路径在逻辑删除后能写入 `deleted=1` 且同 code 可重建 |
| 4 | 同步修正 `removeById` 处注释 | `controller/PackageController.java:1401`（注释改为与实际语义一致，或在修复 1 完成后保留「逻辑删除」表述并验证运行时 SQL） |

---

## 5. 证据清单

| 文件 / 位置 | 内容 |
|---|---|
| `backend/src/main/java/com/meiyetec/entity/PackageItem.java`（全文 L1-48） | 无 `deleted` 字段、无 `@TableLogic`；`createBy` L29 |
| `backend/src/main/java/com/meiyetec/entity/PosOrder.java:54-55` | 对照组：`@TableLogic` + `deleted` 属性 |
| `backend/src/main/resources/application.yml:53-58` | 全局 `logic-delete-field: deleted` / value `1` / not-delete `0` |
| `backend/src/main/java/com/meiyetec/controller/PackageController.java:82-105` | 列表查询过滤条件（无 deleted） |
| `backend/src/main/java/com/meiyetec/controller/PackageController.java:129-194` | `save()` 方法体；创建分支 L144-168 无 `setCreateBy`；L167 `packageItemService.save(p)` |
| `backend/src/main/java/com/meiyetec/controller/PackageController.java:1401` | `removeById` + 「逻辑删除：deleted=1」注释（与实体现状矛盾） |
| `backend/src/main/java/com/meiyetec/controller/PackageController.java:846-848` | `stats()` 统计查询（同样不过滤 deleted） |
| `backend/src/main/java/com/meiyetec/service/P1RemainingService.java:487` | 手写 SQL `WHERE deleted=0`（业务侧按软删理解的证据） |
| `backup/meiyetec_20261025_020004.sql:4742-4767` | `crm_package` 建表：`deleted` L4758 / `create_by` L4759 / `uk_code(code,deleted)` L4763 |
| `backup/meiyetec-before-i129.sql:4784-4809` | 同表结构（`deleted` L4800 / `create_by` L4801 / `uk_code` L4805） |
| `audit/issues/I129-missing-columns-pos-crm.md` §2.1 | 实测生成 SQL 对照（`pos_order` 带 `WHERE deleted=0`，`crm_package` 不带） |

**绑定基线**：commit `e60376aac762a1fde45557ca1a21513388f57031` /
DB `meiyetec@localhost:3307`（MySQL 8.0.46）/
DB snapshot：`backup/meiyetec_20261025_020004.sql` + `backup/meiyetec-before-i129.sql` / env：`audit/baseline/env.md`

---

## 6. 未确认项

1. **`removeById` 是否确实为物理 DELETE** —— 本单为**静态推断**（实体无 `deleted` 属性 + 无 `@TableLogic` → MyBatis-Plus 不启用逻辑删除），
   **未取得运行时 SQL / 数据行证据**。需在受控环境执行一次删除并 `SELECT` 复核该行是否仍在（且 `deleted` 是否为 1）才能定论。
2. **库中当前是否存在 `deleted=1` 的 `crm_package` 行** —— 本轮未连库查询 → **未确认**。
   若存在，则 §3 第 1 行「已软删套餐仍在列表展示」是**当前正在发生**的事实；若不存在，则仅为**潜在隐患**。
3. **`create_by` 的正确取值来源** —— 项目内「当前登录用户 id」的统一取值口径未在本单确认 → **未确认**，
   修复 2 需先对齐口径（不同 Controller 取值方式可能不一致）。
4. **其他实体是否存在同类缺口**（声明了 `deleted` 但漏 `@TableLogic`，或反之）—— 本单只核了 `PackageItem` / `PosOrder` 两个样本 → **未做全量普查**。
