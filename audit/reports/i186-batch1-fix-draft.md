# I186 第 1 批修复草案（G0 / G1 / 专项1 / 专项2 / Q1）

> **状态**：**草案（未落盘任何业务代码）** —— 本文件只是**待审核的修复提案**，本次会话**未修改任何 `backend/src` 代码、未写库、未执行 DDL**。
> **绑定基线**：HEAD `e77a0aa`（`docs(audit): 新建 I192 ... + I193 ... + register 同步`）
> **上游 Issue**：**I186**（`audit/issues/I186-datascope-param-driven-storeid-omitted.md`）
> **本批范围**：G0 基础设施 / G1 列表·统计类接口缺省集合过滤 / 专项1 考勤配置 OR 括号 / 专项2 提成生成类硬编码与缺租户条件 / Q1 导出白名单
> **本批不做**：G2 / G3 / G4（详情 / 写 / 批量归属校验）、G5（M3 员工维度，等 I193）、I189（等 I192）、I191（等业务拍板）、N10（DDL 迁移脚本）、S3（员工查询补 `tenant_id`，等 I193）、前端硬编码 `storeId: 1`
> **验证等级**：**静态 VERIFIED**（本文件所有行号与 SQL 语义均可复核）；**运行期后果 UNVERIFIED**（本机库 `attendance_record` / `attendance_leave_request` / `commission_salary` / `commission_settlement` 均为 0 行，无实数据样本）

---

## 0. 一句话方案

**缺省 `storeId` 不再等于「不过滤」或「恒空」，而是等于「当前会话有效门店集合 `ctx.storeIds`」**：
新增一个统一的范围解析出口（`DataScopeService` 扩展 + 新增 `StoreScope` 值对象），让 11 个列表/统计端点（考勤 4 + 请假 1 + 提成 6 个端点含导出）在缺省时自动注入 `store_id IN (...)`；同时修掉考勤配置的 OR 括号缺陷、提成生成的硬编码门店与缺失的租户条件，并把两个导出端点移出拦截器白名单。

---

## 1. G0 落点调查（只读，结论先行）

### 1.1 现有范围控制四件套的结构

| 组件 | 位置 | 职责 | 现状问题 |
|---|---|---|---|
| `TenantContext` | `security/TenantContext.java` | 持有 `dataScope` + `storeIds` + `tenantId` + `permissions` | `storeIds` 是 `Set<Long>`；ALL 时由 `RbacContextLoader` 置为 `{-1}` |
| `RbacContextLoader` | `security/RbacContextLoader.java:103-140` | 由 RBAC 角色数据范围装配 `ctx.storeIds` | `data_scope_value`（逗号分隔，多角色**并集**）+ 兜底 `user.getStoreId()`；本机 8 条 `sys_role_data_scope.data_scope_value` **全为 NULL** → 实际集合恒为单元素 `{user.store_id}`（I186 §X.4 S5） |
| `DataScopeInterceptor` | `security/DataScopeInterceptor.java` | **请求参数驱动**的前置校验：header → query → form → body 采集 `storeId`/`tenantId` | ① `canAccess(ctx, null)` **显式放行 null**（`:134-139`）；② 采集不到路径参数 → 写路径无防线；③ `WHITELIST`（`:33`）含两个导出端点 → 完全跳过校验 |
| `DataScopeService` | `security/DataScopeService.java` | **Service 层**统一注入范围（`assertStoreScope` / `applyStoreScope(QueryWrapper)` / `applyStoreScope(LambdaQueryWrapper)` / `assertTenantScope` / `canCreateInStore`） | ⚠️ **全仓 0 调用方**（`grep dataScopeService` 仅命中类声明本身）→ 类注释里写的「12 个模块的 Mapper 接 RBAC 时调用本类」**从未落地**；且**只支持 MyBatis-Plus wrapper，不支持 `JdbcTemplate` 原始 SQL**（提成模块 6 个端点全是裸 SQL） |

> ✅ **只读调查的关键结论**：范围控制有**两套互不相通**的实现 —— 拦截器侧「参数驱动、缺省放行」，Service 侧「已写好但没人用」。本批 G1 的本质是**让业务端点真正走 Service 侧那套**，并把 Service 侧补齐到能覆盖裸 SQL。

### 1.2 决策：**扩展现有 `DataScopeService`，不新建 `StoreScopeResolver`**

| # | 理由 | 证据 |
|---|---|---|
| 1 | **语义归属已有共识**：`DataScopeService` 类注释明确写「Service 层统一接入数据范围 …… 12 个模块的 Mapper 在接 RBAC 时不需要重复写 storeId 过滤逻辑」→ 新建平行类会制造**两套同名职责**，后续必然语义漂移 | `DataScopeService.java:10-16` |
| 2 | **零回归风险**：该类当前 **0 调用方** → 扩展/新增方法**不改变任何现有行为**；若新建 `StoreScopeResolver` 并让新代码用它，则仓内同时存在两套「门店范围」API，旧的无调用方那套仍是误导 | `grep dataScopeService` → 仅 1 命中 |
| 3 | **能力互补而非重复**：现类已具备「断言 + wrapper 注入 + 租户断言 + 写入校验」四类能力，缺的只是 ① **集合语义解析出口**（`ctx.storeIds` → 有效集合）、② **裸 SQL 片段**、③ **显式 `storeId` 与集合的交集裁决**。这三块恰好是 G1 的全部依赖 → 放在同一类里最省 | `DataScopeService.java` 全文 |
| 4 | **拦截器不参与**：拦截器只在 `preHandle` 做「显式参数是否越权」的判定，**无法**表达「缺省时该按集合过滤」的业务语义 → 必须有一个 Service 层出口；两者职责是**互补**（拦截器挡显式越权，Service 层负责缺省收敛），不是重复 | `DataScopeInterceptor.java:56-75` |
| 5 | **值对象单独成类**：`StoreScope` 只承载「有效范围快照 + SQL 片段生成」，是**数据对象**而非「范围策略实现」，与 `DataScopeService`（策略/入口）分工清晰，不会造成「两个 Service 抢同一职责」 | 提案 §1.4 |

> ⚠️ **同时记录一处 javadoc 漂移**：`DataScopeService` 类注释第 2 条写「`applyTenantScope(wrapper, tenantIdColumn)`」，但**全类没有该方法**（只有 `assertTenantScope(Long)`）→ 本批**顺带修正注释**（纯注释，不改行为）。

### 1.3 需要补齐的能力缺口

| # | 缺口 | 现状 | 本批补法 |
|---|---|---|---|
| C1 | **有效范围解析出口** | 无；各处只能自己读 `ctx.getStoreIds()` 并各自判断 `-1` / 空集 | `DataScopeService.resolveStoreScope()` → 返回 `StoreScope` |
| C2 | **裸 SQL（`JdbcTemplate`）支持** | `applyStoreScope` 只吃 wrapper；提成 6 个端点是 `StringBuilder` 拼 SQL | `StoreScope.sqlCondition(col)` + `StoreScope.sqlParams()` 成对输出 |
| C3 | **显式 `storeId` × 集合 的交集裁决** | 拦截器会挡越权显式值，但**Service 层无兜底**（且拦截器采集不到 body 里的 `storeId` 数组等形态） | `StoreScope.contains(id)` + `DataScopeService.resolveWritableStoreId(requested)` |
| C4 | **缺省门店的唯一化**（写操作需要单值） | 无 | `resolveDefaultStoreId()`：集合恰好 1 个 → 返回它；多店 → `null`（由调用方决定报 400 还是循环） |
| C5 | **「全局行」语义**（考勤配置 `store_id IS NULL`） | `AttendanceConfigController.list` 的 `or()` 写法产生**跨租户** SQL；`applyStoreScope` 无法表达「门店集合 OR 全局行」 | `and(w -> w.in(col, ids).or().isNull(col))`（MyBatis-Plus 嵌套条件，天然带括号） |

### 1.4 拟新增 / 修改的代码骨架（**草案，未落盘**）

#### 1.4.1 新增 `backend/src/main/java/com/meiyetec/security/StoreScope.java`

```java
package com.meiyetec.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次请求的「有效门店范围」快照（不可变）。
 *
 * <p>语义：
 * <ul>
 *   <li>{@code all = true}  → 数据范围为 ALL（平台超管）→ 任何门店都可见，<b>不加门店过滤</b>；</li>
 *   <li>{@code all = false} → 可见门店 = {@code storeIds}（来自 ctx.storeIds，已剔除 -1 通配）；</li>
 *   <li>{@code storeIds} 为空 → <b>空范围</b>：可见门店为空 → 任何列表都必然返回空集。</li>
 * </ul>
 * 空范围与「不限制」是<b>两种不同语义</b>，必须显式区分（这是 I186 §X.1 N1/N3 两种退化形态的根因）。
 */
public final class StoreScope {

    /** 永远不命中的哨兵门店 id（与 DataScopeService 现有实现保持一致）。 */
    public static final long EMPTY_SENTINEL = -9999L;

    private static final StoreScope ALL = new StoreScope(true, Collections.emptyList());
    private static final StoreScope EMPTY = new StoreScope(false, Collections.emptyList());

    private final boolean all;
    private final List<Long> storeIds;

    private StoreScope(boolean all, List<Long> storeIds) {
        this.all = all;
        this.storeIds = Collections.unmodifiableList(storeIds);
    }

    public static StoreScope all() { return ALL; }

    public static StoreScope of(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return EMPTY;
        return new StoreScope(false, new ArrayList<>(ids));
    }

    public boolean isAll() { return all; }

    /** 空范围：范围受限且可见门店为空 → 列表必然空集。 */
    public boolean isEmptyScope() { return !all && storeIds.isEmpty(); }

    public List<Long> getStoreIds() { return storeIds; }

    /** 可见门店判定（ALL 恒 true；空范围恒 false）。 */
    public boolean contains(Long storeId) {
        if (all) return true;
        if (storeId == null) return false;
        return storeIds.contains(storeId);
    }

    /**
     * 生成 SQL 条件片段（不含 AND 前缀，由调用方拼接）。
     * 与 {@link #sqlParams()} 必须成对使用，顺序一致。
     */
    public String sqlCondition(String column) {
        if (all) return "";
        if (storeIds.isEmpty()) return column + " = " + EMPTY_SENTINEL;
        StringBuilder sb = new StringBuilder(column).append(" IN (");
        for (int i = 0; i < storeIds.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.append(')').toString();
    }

    /** 与 {@link #sqlCondition(String)} 配套的占位参数。 */
    public List<Object> sqlParams() {
        if (all) return Collections.emptyList();
        if (storeIds.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(storeIds);
    }
}
```

#### 1.4.2 修改 `backend/src/main/java/com/meiyetec/security/DataScopeService.java`（**新增方法，既有 5 个方法保持不动**）

```java
/**
 * 解析当前会话的有效门店范围。
 * 未登录 → 不过滤（all）；dataScope=ALL → 不过滤；否则取 ctx.storeIds（-1 视为 ALL）。
 */
public StoreScope resolveStoreScope() {
    TenantContext ctx = TenantContextHolder.get();
    if (ctx.getUserId() == null) return StoreScope.all();
    if ("ALL".equals(ctx.getDataScope())) return StoreScope.all();
    Set<Long> ids = ctx.getStoreIds();
    if (ids == null || ids.isEmpty()) return StoreScope.of(Collections.emptyList());
    if (ids.contains(-1L)) return StoreScope.all();
    return StoreScope.of(new ArrayList<>(ids));
}

/** 缺省门店：仅当范围恰好收敛到 1 个门店时返回它；多店返回 null（调用方须自行决策）。 */
public Long resolveDefaultStoreId() {
    StoreScope scope = resolveStoreScope();
    if (scope.isAll()) return null;
    List<Long> ids = scope.getStoreIds();
    return ids.size() == 1 ? ids.get(0) : null;
}

/**
 * 写操作用：把「调用方指定的门店」与「有效范围」求交。
 * 显式指定且越权 → 403 STORE_DENIED；缺省且范围唯一 → 该门店；缺省且多店 → 400（请显式指定）；
 * 缺省且 ALL → 400（平台超管也必须显式指定，避免再次出现硬编码 1L）。
 */
public Long resolveWritableStoreId(Long requestedStoreId) {
    StoreScope scope = resolveStoreScope();
    if (requestedStoreId != null) {
        if (!scope.contains(requestedStoreId)) {
            throw new BusinessException(BizCode.STORE_DENIED);
        }
        return requestedStoreId;
    }
    if (scope.isAll()) {
        throw new BusinessException(BizCode.BAD_REQUEST, "storeId 必填");
    }
    if (scope.isEmptyScope()) {
        throw new BusinessException(BizCode.STORE_DENIED);
    }
    if (scope.getStoreIds().size() > 1) {
        throw new BusinessException(BizCode.BAD_REQUEST, "当前账号可访问多个门店，请指定 storeId");
    }
    return scope.getStoreIds().get(0);
}

/** QueryWrapper 版本：集合注入 + 显式值交集裁决。 */
public <T> QueryWrapper<T> applyStoreScope(QueryWrapper<T> wrapper, String storeIdColumn, Long requestedStoreId) {
    StoreScope scope = resolveStoreScope();
    if (scope.isAll()) return wrapper;
    if (requestedStoreId != null) {
        if (!scope.contains(requestedStoreId)) throw new BusinessException(BizCode.STORE_DENIED);
        return wrapper.eq(storeIdColumn, requestedStoreId);
    }
    if (scope.isEmptyScope()) return wrapper.eq(storeIdColumn, StoreScope.EMPTY_SENTINEL);
    return wrapper.in(storeIdColumn, scope.getStoreIds());
}

/** LambdaQueryWrapper 版本：同上（现有 2 参重载保持不动，保持 0 调用方兼容）。 */
public <T> LambdaQueryWrapper<T> applyStoreScope(LambdaQueryWrapper<T> wrapper,
                                                 SFunction<T, ?> storeIdGetter,
                                                 Long requestedStoreId) { ... }
```

> ⚠️ **既有 `applyStoreScope` 两参重载与新三参重载并存**：旧重载**当前 0 调用方**，本批**不删不改**（避免与其它未落地计划冲突）；新端点一律用三参版本。

### 1.5 语义决策表（本批统一口径）

| 会话状态 | `resolveStoreScope()` | 缺省列表端点行为 | 显式 `storeId` 行为 |
|---|---|---|---|
| 未登录（`userId == null`） | `all` | 不过滤（与现状一致） | 不过滤 |
| `dataScope = ALL` | `all` | 不过滤（管理员看全量，符合预期） | 允许任意门店（现状即如此） |
| `dataScope ∈ {BRAND, STORE, SELF}` 且集合非空 | 集合 | `store_id IN (集合)` | 越权 → 403；合法 → `eq(storeId)` |
| 同上但集合为空（SELF 且无门店） | **空范围** | `store_id = -9999` → **必然空集** | 越权 → 403 |
| `ctx.storeIds` 含 `-1`（ALL 的载体） | `all` | 不过滤 | 允许任意门店 |

> 📌 **空范围为什么是「空集」而不是「403」**：空集与 I186 §X.1 N1（退化为全量）/ N3（退化为空集）中的**两种错误形态都不同** —— 它是**正确表达**「该账号没有任何可见门店」。⚠️ 见 §8 开放问题 **Q-A**：若产品希望「无门店账号直接 403」，改一行即可。

---

## 2. G1 逐端点修复清单

> 统一改法：`ctx.storeIds` 集合语义（I186 用户决策 2026-11-16 决策 2）；显式 `storeId` 保持「越权 403 / 合法等值过滤」。

### 2.1 考勤组（4 个端点）

| # | 端点 | 位置 | 现状 | 缺省后果 | 改法 | 落点文件 |
|---|---|---|---|---|---|---|
| A1 | `GET /attendance/records` | `AttendanceController.java:95-102` → `AttendanceRecordMapper.queryRecords`（`AttendanceRecordMapper.java:27-39`） | SQL：`AND (#{storeId} IS NULL OR ar.store_id = #{storeId})` | **租户内全部门店**的考勤记录（含打卡时间/地点） | mapper 增参 `List<Long> storeIds`，SQL 改 `<foreach>` 的 `IN`；Service 层用 `resolveStoreScope()` 产出集合 | `AttendanceRecordMapper.java` / `AttendanceRecordService(Impl).java` / `AttendanceController.java` |
| A2 | `GET /attendance/statistics` | `AttendanceController.java:106-111` → `AttendanceRecordServiceImpl.java:89-90` → `AttendanceRecordMapper.findByStoreAndDateRange`（`:21-25`） | SQL：`AND store_id = #{storeId}` | **恒空集**（`store_id = NULL` → 三值逻辑非真）→ 统计恒为 0，与 A1 列表**口径相反** | 同一 mapper 方法改为集合版（`findByStoresAndDateRange`），与 A1 共用同一集合 | 同上 |
| A3 | `POST /attendance/page` | `AttendanceController.java:71-89`（`:85` `if (storeId != null) wrapper.eq(...)`） | 缺省不拼门店条件 | **租户内全部门店** | 改为 `dataScopeService.applyStoreScope(wrapper, AttendanceRecord::getStoreId, storeId)`（三参版） | `AttendanceController.java` |
| A4 | `GET /attendance/leave/list` | `LeaveRequestController.java:64-72` → `LeaveRequestServiceImpl.java:104-106` → `LeaveRequestMapper.queryRequests`（`:29-45`） | SQL：`AND (#{storeId} IS NULL OR lr.store_id = #{storeId})` | **租户内全部门店**的请假单（含事由、审批人、天数） | 同 A1（mapper 增集合参 + `<foreach>`） | `LeaveRequestMapper.java` / `LeaveRequestService(Impl).java` / `LeaveRequestController.java` |

### 2.2 请假 / 考勤配置的其余端点（本批**不改**，理由见 §6）

| 端点 | 位置 | 本批处置 |
|---|---|---|
| `GET /attendance/leave/pending-count` | `LeaveRequestController.java:82-86` | **不改**：`storeId` 必填 → 拦截器已覆盖显式越权 |
| `GET /attendance/leave/my` | `:75-79` | **不改**：员工维度 → 属 G5/M3（等 I193） |
| `GET /attendance/today` | `AttendanceController.java:53-69` | **不改**：员工维度 → G5/M3 |
| `GET /attendance/monthly-report` | `:115-122` → `AttendanceRecordServiceImpl.java:111-112` | **不改**：员工维度且**无门店维度** → G5/M3 |
| `GET /attendance/{id}` / `PUT /attendance/{id}` | `:125-140` | **不改**：详情 / 写 → 第 2 批（G2/G3） |

### 2.3 提成组（8 个端点，其中 2 个同时属 Q1）

| # | 端点 | 位置 | 现状 | 缺省后果 | 改法 |
|---|---|---|---|---|---|
| C1 | `GET /commission/rules/page` | `CommissionController.java:60` | `if (storeId != null) query.eq(...)` | 全租户规则 | 三参 `applyStoreScope(query, CommissionRule::getStoreId, storeId)` |
| C2 | `GET /commission/rules/list` | `:105` | 同上 | 全租户规则 | 同上 |
| C3 | `GET /commission/rules/stats` | `:189` / `:194`（两个 wrapper） | 同上 | 全租户统计 | 两个 wrapper 各接一次 |
| C4 | `GET /commission/records/page` | `:226`（裸 SQL `StringBuilder`） | `if (storeId != null) { where.append("AND r.store_id=? ") }` | 全租户提成记录 | `StoreScope.sqlCondition("r.store_id")` + `sqlParams()` → 拼 `AND ...` 并把参数按序 `args.addAll` |
| C5 | `GET /commission/records/stats` | `:255`（**字符串拼接** `storeFilter = " AND store_id=" + storeId`） | 同上 + 拼接 | 全租户统计 | 改参数化：`" AND store_id = ?"` + `args`；集合走 `sqlCondition` |
| C6 | `GET /commission/salary/page` | `:331`（裸 SQL） | `if (storeId != null)` | 全租户工资 | 同 C4 |
| C7 | `GET /commission/salary/stats` | `:407`（字符串拼接 `storeFilter`） | 同上 | 全租户工资统计 | 同 C5 |
| C8 | `GET /commission/salary/export` | `:438`（裸 SQL）**且在 `DataScopeInterceptor.WHITELIST`** | 双重失守 | 全租户工资，且显式越权 `storeId` 也不校验 | 集合过滤（同 C4）+ **Q1 移出白名单** |
| C9 | `GET /commission/settlement/page` | `:578`（wrapper） | `if (storeId != null)` | 全租户结算单 | 三参 `applyStoreScope` |
| C10 | `GET /commission/settlement/stats` | `:537`（字符串拼接 `storeFilter` + `monthFilter` 拼接） | 同上 | 全租户结算统计 | 改参数化 + 集合过滤（`monthFilter` 拼接问题见 §8 **Q-D**） |
| C11 | `GET /commission/settlement/export` | `:689`（裸 SQL）**且在白名单** | 双重失守 | 全租户结算单 | 集合过滤 + **Q1 移出白名单** |

### 2.4 mapper 动态 SQL 的两种落法（**需拍板，见 §8 Q-B**）

A1 / A2 / A4 需要在**注解式 `@Select`** 里表达 `IN (...)`。两种落法：

| 落法 | 做法 | 优点 | 代价 |
|---|---|---|---|
| **落法 1（推荐）** | 在现有 `@Select` 上用 MyBatis `<script>` + `<foreach>`，参数类型改为 `List<Long>` | **不新增文件**、不动 mapper 扫描约定、影响面最小 | 注解内 SQL 变长；MyBatis 注解 `<script>` 需注意 `<` 转义（本处无 `<`） |
| 落法 2 | 新增 `resources/mapper/AttendanceRecordMapper.xml` + `LeaveRequestMapper.xml` | SQL 可读性最好 | 仓内现有 XML 只有 5 个（`BookingMapper.xml` 等）→ **同一 Mapper 混用注解与 XML**；需确认 `mybatis-plus.mapper-locations` 已覆盖该目录（本仓无同名 XML，故该项**未验证**） |

> ⚠️ 两种落法都要求：**把 `storeId` 单值参数换成 `storeIds` 集合参数**，属于**方法签名变更** → `AttendanceRecordService` / `LeaveRequestService` 接口及其实现需同步改签名（当前调用方各 1 处）。

### 2.5 前端影响面（**本批不改前端，仅登记**）

| 前端位置 | 现状 | 本批后端改动后的行为 |
|---|---|---|
| `views/attendance/records.vue:219` | `storeId: query.storeId \|\| undefined` → **缺省不传** | ✅ 本批核心收益点：改后按 `ctx.storeIds` 过滤（当前为租户全量） |
| `views/attendance/statistics.vue:252` | 同上 | ✅ 本批后与列表口径一致（当前列表非空、统计恒 0） |
| `views/attendance/config.vue`（调 `attendanceApi.configList`） | `attendance.ts:63` 传 `{ storeId }`（可 undefined） | ✅ 专项1 修复后：返回「本租户 + 本门店集合 + 全局行」，不再串到别的租户 |
| `views/attendance/index.vue:224 / :246` | **硬编码 `storeId: 1`**（打卡） | ⚠️ 不在本批；门店≠1 的账号会被拦截器 403（现状即如此） |
| `views/attendance/leave.vue:317` | **硬编码 `storeId: 1`**（提交请假） | ⚠️ 同上 |
| `views/commission/salary.vue:136` | **硬编码 `storeId: 1`**（生成工资） | ⚠️ 专项2 的后端「缺省按集合」在当前前端**不会触发**（前端总是显式传 1）→ 见 §4.3 |
| `views/commission/settlement.vue:206` | **硬编码 `storeId: 1`**（生成结算） | ⚠️ 同上 |
| `views/commission/rule-list.vue:119` | **硬编码 `storeId: 1`** | ⚠️ 不在本批 |
| `views/commission/simulation.vue:224` | **硬编码 `storeId: 1`** | ⚠️ 不在本批（`/commission/simulation` 归 I187 门控） |

> 📌 **额外发现（契约漂移，不在本批修）**：前端 `api/commission.ts:29` 调 `GET /commission/records/export`，但后端 `CommissionController` **没有该路由**（只有 `/records/page`、`/records/stats`）→ 提成记录导出按钮**必然 404**。→ 建议登记为新 Issue（**不在本批**）。

---

## 3. 专项 1 — `AttendanceConfigController` 的 OR 括号缺陷

### 3.1 `GET /attendance/config/list`

**现状代码**（`AttendanceConfigController.java:34-43`）：

```java
wrapper.eq(AttendanceConfig::getTenantId, t);
if (storeId != null) {
    wrapper.eq(AttendanceConfig::getStoreId, storeId).or().isNull(AttendanceConfig::getStoreId);
}
```

**现状 SQL 语义**（MyBatis-Plus 拼接后）：

```sql
WHERE tenant_id = ? AND store_id = ? OR store_id IS NULL
--            └──────── AND 优先级高于 OR ────────┘
```

→ 实际语义是 `(tenant_id = ? AND store_id = ?) OR (store_id IS NULL)` → **第二个分支没有 `tenant_id` 约束** → **其它租户的「全局考勤配置」行（`store_id IS NULL`）会被返回**（**跨租户**泄漏，严于其余端点的跨门店问题）。

**修正后语义**：

```sql
WHERE tenant_id = ? AND (store_id IN (?,?) OR store_id IS NULL)
```

**修正代码（草案）**：

```java
// 修正：把 OR 收进 and(...) 嵌套条件 → 天然带括号，且 IS NULL 分支继承 tenant_id 约束；
// 缺省时按 ctx.storeIds 集合过滤（不再出现「不传 storeId 就查全租户」）
wrapper.eq(AttendanceConfig::getTenantId, t);
StoreScope scope = dataScopeService.resolveStoreScope();
if (!scope.isAll()) {
    wrapper.and(w -> {
        if (scope.isEmptyScope()) {
            w.eq(AttendanceConfig::getStoreId, StoreScope.EMPTY_SENTINEL);
        } else {
            w.in(AttendanceConfig::getStoreId, scope.getStoreIds()).or().isNull(AttendanceConfig::getStoreId);
        }
    });
}
```

> ⚠️ **语义变化说明**：修正后**仍然保留「全局行（`store_id IS NULL`）」**这一产品语义（考勤配置页面上「通用配置（不选则为通用）」的文案，`views/attendance/config.vue:73` 明确存在）→ 若产品认为全局行必须收紧为「仅本租户可见」，本改法已满足（`tenant_id = ?` 约束在括号外层）。
> ⚠️ **显式 `storeId` 的交集裁决**：上述写法在显式传 `storeId` 时**不**用 `eq(storeId)`，而是仍按集合 `IN` → 会把「本租户的其他门店行」也返回（**比现状宽松**）。→ 修正方案：显式传参时用 `eq(storeId)` + `or().isNull()`，但必须包在 `and(...)` 里：

```java
wrapper.eq(AttendanceConfig::getTenantId, t);
if (storeId != null) {
    // 显式指定：仅本店行 + 本租户全局行（括号修正，OR 分支继承 tenant_id 约束）
    wrapper.and(w -> w.eq(AttendanceConfig::getStoreId, storeId).or().isNull(AttendanceConfig::getStoreId));
} else {
    // 缺省：按有效门店集合 + 本租户全局行
    StoreScope scope = dataScopeService.resolveStoreScope();
    if (!scope.isAll()) {
        wrapper.and(w -> {
            if (scope.isEmptyScope()) w.eq(AttendanceConfig::getStoreId, StoreScope.EMPTY_SENTINEL);
            else w.in(AttendanceConfig::getStoreId, scope.getStoreIds()).or().isNull(AttendanceConfig::getStoreId);
        });
    }
}
```

### 3.2 `GET /attendance/config/active`（连带）

**现状**（`AttendanceConfigController.java:27-32` → `AttendanceConfigMapper.findActiveConfig`）：

```sql
WHERE tenant_id = #{tenantId} AND (store_id = #{storeId} OR store_id IS NULL) AND status = 1 AND deleted = 0
ORDER BY store_id DESC LIMIT 1
```

→ `storeId` **缺省时 `store_id = NULL` 恒 FALSE** → **只命中全局行，本店专属配置永远取不到**（与「缺省按集合取本店」直接冲突）。

**修正（草案）**：新增 mapper 方法 `findActiveConfigByStores(tenantId, List<Long> storeIds)`：

```sql
WHERE tenant_id = #{tenantId} AND status = 1 AND deleted = 0
  AND (store_id IN (<foreach>) OR store_id IS NULL)
ORDER BY store_id DESC LIMIT 1
```

- MySQL 中 `ORDER BY store_id DESC` 把 `NULL` 排在**最后** → **门店行优先、全局行兜底**，与产品语义一致（`ORDER BY` 行为需在目标库版本上确认，见 §8 **Q-C**）。
- 集合为空时 → 只查全局行（`store_id IS NULL`）。
- 集合唯一时 → 退化为 `store_id = ?`，与现状一致。

---

## 4. 专项 2 — 提成生成类端点的硬编码与缺租户条件

### 4.1 `POST /commission/salary/generate`（`CommissionController.java:266-317`）

**现状**（`:276`）：

```java
Long storeId = body.get("storeId") == null ? 1L : ((Number) body.get("storeId")).longValue();
```

→ **缺省静默硬编码门店 1**（I186 §X.2 S1）→ 多店账号缺省调这个接口会**把门店 1 的工资算出来**（若账号对门店 1 无权 → 后续无门店归属校验 → **越权写入工资行**）。

**修正（草案）**：

```java
// 修正：缺省不再硬编码 1L；按当前会话的有效门店集合裁决
Long storeId = dataScopeService.resolveWritableStoreId(
        body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue());
```

行为对照：

| 请求 | 现状 | 修正后 |
|---|---|---|
| 缺省 `storeId`，单店账号（`ctx.storeIds={1}`） | 用门店 1 | 用门店 1（**行为一致**） |
| 缺省 `storeId`，单店账号（`ctx.storeIds={5}`） | ⚠️ 用门店 1（**越权**） | 用门店 5 |
| 缺省 `storeId`，多店账号 | ⚠️ 用门店 1 | **400**「当前账号可访问多个门店，请指定 storeId」 |
| 显式 `storeId=5`，账号范围 `{1}` | ⚠️ 拦截器 403（body 可采集 `storeId`） | 拦截器 403 + Service 层 403（双保险） |
| 显式 `storeId=1`，账号范围 `{1}` | 生成 | 生成（一致） |

> ⚠️ **同端点的员工查询**（`:277-280` `eq(Employee::getStoreId, storeId)` **无 `tenant_id` 条件**，I186 §X.2 S3）→ **本批不改**：补 `tenant_id` 属于 G5/M3 范畴，且被 **I193**（144/150 行 `tenant_id IS NULL`）阻塞 → 补了会把 96% 员工排除。→ **明确留在第 2 批 + I193 之后**。

### 4.2 `CommissionSettlementService.generate` 缺 `tenantId` 条件（`:49-52`）

**现状**：

```java
CommissionSettlement existing = settlementMapper.selectOne(new LambdaQueryWrapper<CommissionSettlement>()
        .eq(CommissionSettlement::getStoreId, storeId)
        .eq(CommissionSettlement::getSalaryMonth, month));
```

→ **无 `tenant_id` 条件** → 跨租户「同门店 id + 同月份」会命中**他租户**的结算单并被**复用 / 覆盖**（I186 §X.2 S2）。

**修正（草案）**：补一行租户等值条件（`tenantId` 已在方法首部从 `TenantContextHolder` 取得并做过 null 校验，`:42-45`）：

```java
CommissionSettlement existing = settlementMapper.selectOne(new LambdaQueryWrapper<CommissionSettlement>()
        .eq(CommissionSettlement::getTenantId, tenantId)
        .eq(CommissionSettlement::getStoreId, storeId)
        .eq(CommissionSettlement::getSalaryMonth, month));
```

> ⚠️ **同方法的员工查询**（`:56-60` `eq(Employee::getStoreId, storeId)` 同样无 `tenant_id`）→ 同 §4.1，**本批不改**（等 I193）。
> ⚠️ **副作用提示**：补了 `tenant_id` 后，若历史数据存在「同店同月多租户结算单」，`selectOne` 会因**多结果**抛 `TooManyResultsException` → 需在验证口径里覆盖（§7 第 6 条）。

### 4.3 前端硬编码 `storeId: 1` 对专项 2 的影响（**必须写进运维说明**）

| 事实 | 影响 |
|---|---|
| 前端 5 处硬编码 `storeId: 1`（`views/commission/salary.vue:136`、`views/commission/settlement.vue:206`、`views/attendance/index.vue:224/:246`、`views/attendance/leave.vue:317`、`views/commission/rule-list.vue:119`、`views/commission/simulation.vue:224`） | ① **专项 2 的后端「缺省按集合」逻辑在当前前端永远不会触发**（前端总是显式传 1）；② 门店 ≠ 1 的账号点这些按钮会被拦截器 403（现状即如此）；③ **前端硬编码与后端缺省语义必须一起改**才能闭环 → 建议作为**独立批次**（前端改动涉及已验收页面以外的文件，但改动面需单独立项审） |

---

## 5. Q1 — 移除导出接口的白名单豁免

**现状**（`DataScopeInterceptor.java:33-44`）：

```java
private static final Set<String> WHITELIST = Set.of(
        "/auth/login", "/auth/refresh",
        "/sys/log/export", "/report/export", "/report/download",
        "/booking/export",
        "/commission/salary/export",     // ← 本批移除
        "/commission/settlement/export", // ← 本批移除
        "/market/coupon/export", "/stock/export");
```

**改法**：仅删除 `/commission/salary/export` 与 `/commission/settlement/export` 两行；**其余 8 条本批不动**（`/report/export` 等属其它模块，范围外）。

**为什么安全**（用户 2026-11-16 Q1 决策已确认）：

- 拦截器只在 `preHandle` 阶段工作，**不参与响应写出** → 移除白名单只影响「是否抛 403」，**流式 xlsx 字节流写出不受影响**（写出发生在 `CommissionController.salaryExport` 内的 `wb.write(response.getOutputStream())`，`:465`）。
- 移除后：显式传越权 `storeId` → **403**（此前放行）；缺省不传 → 仍放行，但**由 G1 的集合过滤兜住**（C8 / C11 已改）→ 二者配套才闭合。

> ⚠️ **配套前提**：Q1 **必须**与 G1 的 C8 / C11 同批上线；若只做 Q1 不做 G1，则「不传 `storeId` 的导出」仍会导出全租户数据（**风险不降反升**：显式越权被挡，但缺省仍全量）。→ **本批将两者绑定**。

---

## 6. 明确不在本批的内容（及理由）

| 项 | 内容 | 不做的理由 |
|---|---|---|
| **G2** | 详情类端点的门店归属校验（`GET /attendance/{id}`、`GET /attendance/config/{id}`、`GET /attendance/leave/{id}`、`GET /commission/rules/{id}`、`GET /commission/settlement/{id}`） | 用户决策：第 2 批 |
| **G3** | 写接口的门店归属校验（改/删/确认/发放 10 个端点） | 用户决策：第 2 批 |
| **G4** | 批量接口的逐条归属校验（4 个 `batch/confirm`、`batch/pay`） | 用户决策：第 2 批 |
| **G5（M3）** | 员工维度校验（`employeeId` → `pos_employee.tenant_id` + `store_id IN ctx.storeIds`） | **等 I193**（`pos_employee` 144/150 行 `tenant_id IS NULL`，双过滤会排除 96% 员工）→ 同时阻塞 `/attendance/today`、`/attendance/monthly-report`、`/attendance/leave/my`、写侧 `employeeId` 伪造（N7）、`S3` |
| **N9** | `POST /commission/calculate/order/{orderId}` 跨租户读 + 跨租户写 | 不在用户给的第 1 批范围（G1 是列表/统计类；N9 是写端点 → 属 G3 范畴）→ 建议并入第 2 批 |
| **N10** | 考勤三表补 `tenant_id` + `uk_employee_date` 扩列的 DDL 迁移脚本 | 不在第 1 批范围；且 DDL 需单独评审（MySQL 8.0.29+ `ADD COLUMN IF NOT EXISTS` 约束，I130 同族） |
| **S3** | `CommissionController:277-280` / `CommissionSettlementService:56-60` 员工查询补 `tenant_id` | 与 G5 同源，被 I193 阻塞 |
| **S4** | `/commission/tier`、`/commission/condition`、`/commission/simulation` 三件套的归属校验 | 归 I187（门控控制器无鉴权），随解封处理 |
| **I189** | 请假撤回的权限码拆分 + 「本人校验」 | 等 I192（用户↔员工映射）产品答复 |
| **I191** | 请假审批落库（`markLeaveDays` 只构造不保存） | 等请假语义拍板 |
| **前端** | 5 处硬编码 `storeId: 1`、提成记录导出按钮缺后端路由 | 不在第 1 批范围；见 §2.5 登记 |

---

## 7. 风险与验证口径

### 7.1 实施风险

| # | 风险 | 说明 | 缓解 |
|---|---|---|---|
| R1 | **单店账号行为变化 = 零** | 本机 8 条 `data_scope_value` 全 NULL → `ctx.storeIds` 恒为 `{user.store_id}` 单元素 → 本批所有「集合过滤」在单店账号下**等价于原来的 `eq(storeId)`**（或等价于「缺省不传」时的全量→单店） | 回归重点放在**多店账号**（需运维先配 `data_scope_value`） |
| R2 | **「列表非空 → 统计为 0」被反转** | A2 修好后，统计会从「恒 0」变成「有数据」→ **前端 `views/attendance/statistics.vue` 的展示口径会变**（从空表变有数据） | 属修复意图；需在发版说明里写明 |
| R3 | **`commission_settlement.selectOne` 可能多结果** | 补 `tenant_id` 后若历史存在同店同月多租户结算单 → `TooManyResultsException` → 500 | 验证口径第 6 条：先查重再上线 |
| R4 | **显式 `storeId` 的 Service 层二次校验可能误伤** | 若某账号 `ctx.storeIds` 为空（无门店）而前端传了 `storeId` → 403（现状可能被拦截器放行） | 拦截器侧行为不变（拦截器 `canAccess` 对空集合也返回 false → 现状即 403）；仅当 `ctx.storeIds` 含 `-1` 时行为不同（ALL → 放行，与现状一致） |
| R5 | **方法签名变更的影响面** | `AttendanceRecordService.queryRecords` / `getStatistics` / `getRecords`、`LeaveRequestService.queryRequests` 需改签名 | 已核对：各仅 1 个调用点（`AttendanceController` / `LeaveRequestController`）+ `AttendanceRecordService.getRecords` **无任何调用方**（孤儿方法，可直接随签名变更） |
| R6 | **Q1 与 G1 必须同批** | 只做 Q1 不做 G1 → 缺省导出仍是全租户 | 已绑定为同一提交范围 |
| R7 | **`@Select <script>` 的 `<` 转义** | 本批 SQL 无 `<` 比较（只有 `IN`/`IS NULL`）→ 无转义风险 | 落法 1 可行；仍需在实施时复核 |

### 7.2 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | `manager01`（`dataScope=STORE`，`storeIds={1}`）`GET /commission/records/page`（**不带** `storeId`） | 仅返回 `store_id ∈ ctx.storeIds` 的记录（集合语义） |
| 2 | `manager01` `GET /commission/records/page?storeId=5` | **403**「无权访问该门店数据」（保持现状） |
| 3 | `manager01` `GET /commission/salary/page` / `/commission/settlement/page`（不带 `storeId`） | 仅本门店 |
| 4 | `manager01` `GET /commission/salary/export` / `/commission/settlement/export`（不带 `storeId`） | 仅本门店数据；**Q1 生效**（显式 `storeId=5` → 403） |
| 5 | `manager01` `GET /attendance/records`（不带 `storeId`） | 仅本门店；与 `GET /attendance/statistics` **数据口径一致**（N3 修复） |
| 6 | `SELECT store_id, salary_month, tenant_id, COUNT(*) FROM commission_settlement WHERE deleted=0 GROUP BY store_id, salary_month, tenant_id HAVING COUNT(*)>1` | **0 行**（否则 `selectOne` 会抛 `TooManyResultsException`） |
| 7 | `manager01` `GET /attendance/config/list`（不带 `storeId`） | 仅**本租户**的行；`OR store_id IS NULL` 分支必须带 `tenant_id` 约束（**不得出现其它租户的全局配置**） |
| 8 | `manager01` `GET /attendance/config/active`（不带 `storeId`） | 返回**本店行优先**的生效配置；本店无行时回退本租户全局行 |
| 9 | 跨租户同店同月的结算单（构造样本） | `POST /commission/settlement/generate` **不复用他租户结算单**（新建本租户结算单） |
| 10 | `POST /commission/salary/generate`（缺省 `storeId`，单店账号 `storeIds={5}`） | 生成的是**门店 5**的工资，不是门店 1 |
| 11 | `POST /commission/salary/generate`（缺省 `storeId`，多店账号） | **400**「当前账号可访问多个门店，请指定 storeId」 |
| 12 | `admin`（`dataScope=ALL`）同组请求 | 仍返回全量（ALL 语义不受影响） |

---

## 8. 待审核的开放问题（需要拍板）

| # | 问题 | 选项 | 影响面 | 建议 |
|---|---|---|---|---|
| **Q-A** | **空范围**（`dataScope=SELF` 且 `ctx.storeIds` 为空）时，列表端点应返回**空集**还是 **403**？ | A1 空集（当前设计） / A2 403「无可访问门店」 | 影响所有列表/统计端点 | 建议 **A1**（语义正确，且与 `DataScopeService` 既有 `-9999` 哨兵一致）；若要 A2，只需在 `resolveStoreScope()` 抛异常一处改动 |
| **Q-B** | mapper 动态 SQL 的落法 | B1 注解 `@Select <script>` + `<foreach>`（不新增文件） / B2 新增 `resources/mapper/AttendanceRecordMapper.xml` + `LeaveRequestMapper.xml` | A1/A2/A4 三个端点 | 建议 **B1**（影响面最小、不引入新的 mapper 约定）；B2 需先确认 `mybatis-plus.mapper-locations` 配置（本仓 **UNVERIFIED**） |
| **Q-C** | `GET /attendance/config/active` 缺省时是否**回退全局行**？ | C1 回退（`store_id IN (集合) OR store_id IS NULL`，门店行优先） / C2 只返回门店行（无行即空） | 打卡判定逻辑（`AttendanceRecordServiceImpl.getConfig` 走的是**门店等值**查询，不是 active） | 建议 **C1**（与 `findActiveConfig` 现状的 `OR store_id IS NULL` 语义一致，且 MySQL `ORDER BY store_id DESC` 使 `NULL` 垫底 → 门店行优先；**该排序行为需在目标库版本上实测确认**） |
| **Q-D** | 顺带把 `settlementStats`（`:537`）的 `monthFilter`（`salary_month='" + salaryMonth + "'`）改为**参数化**吗？ | D1 一并参数化 / D2 只改门店条件 | 注入面（当前 `salaryMonth` 直接拼进 SQL） | 建议 **D1**（同一端点内顺手收口；改动小、语义不变）；若严格控制范围则 D2 并单独登记 |
| **Q-E** | 显式 `storeId` 越权时，**Service 层是否重复校验**（拦截器已挡）？ | E1 重复校验（双保险） / E2 只依赖拦截器 | 所有 G1 端点 | 建议 **E1**（拦截器采集不到路径参数 / body 数组形态；且能挡住「前端显式传越权 storeId + ctx 集合为空」的边界） |
| **Q-F** | `POST /commission/salary/generate` 缺省 `storeId` 且为**多店账号**时的行为 | F1 400 要求显式指定（当前设计） / F2 按店循环生成多张工资表 | 单端点 | 建议 **F1**（循环生成涉及幂等与事务边界，应单独立项）；**注意前端当前硬编码 `storeId: 1`（§4.3）→ 实际走不到该分支** |
| **Q-G** | `POST /commission/settlement/generate` 当前 `storeId` **必填**（`:554-556` 返回 400），是否保持？ | G1 保持必填 / G2 改为「缺省按集合唯一门店」 | 单端点 | 建议 **G1 保持必填**（与 F1 的 400 语义一致，避免多店歧义）；仅在**前端**也去掉硬编码后再考虑放宽 |
| **Q-H** | G1 端点是否需要**同时**把 `tenant_id` 条件显式化（`attendance_record` / `attendance_leave_request` 的查询里 `tenant_id` 已在 WHERE 中，但提成侧 `commission_record` 的裸 SQL 里 `tenant_id` 已是**可选拼接**：`tenantId == null ? "" : ...`） | H1 保持「`tenantId` 为空则不拼」 / H2 改为「`tenantId` 为空 → 400」 | C4/C5/C6/C7/C8/C10/C11 七个裸 SQL 端点 | 建议 **H2**（`TenantContextHolder.get().getTenantId()` 为空 = 未识别租户，与 `CommissionController:126-129`（`createRule`）的既有做法一致：直接 `UNAUTHORIZED`）→ 可同时消掉「跨租户无过滤」的理论口子 |

---

## 9. 边界声明

- 本文件是**修复草案**：本次会话**未修改任何 `backend/src` 业务代码、未新增任何 Mapper / Service / Controller 文件、未执行任何 SQL、未写库、未执行 DDL**。
- 所有结论来自**静态代码分析**（行号与 SQL 语义均可复核）+ **前端调用点核对**；**运行期后果 UNVERIFIED** —— 本机库 `attendance_record` / `attendance_leave_request` / `commission_salary` / `commission_settlement` 均 **0 行**，无法构造「他店有数据」的实数据样本 → 验证口径需在**隔离环境造样本**后复测。
- ⚠️ **生产库未连接** → 生产的门店分布、`sys_role_data_scope.data_scope_value` 配置情况、跨租户重号情况均 **UNVERIFIED**；本批的「集合语义」在生产的实际严格程度取决于运维是否配置了 `data_scope_value`（I186 §X.4 S5）→ **须在运维说明里写明**。
- ⚠️ 本文件提到的**前端硬编码 `storeId: 1`**（6 处）与**前端调用了不存在的 `/commission/records/export`**，均为**只读核对发现**，**未做任何前端改动**；后者建议登记新 Issue。
- ⚠️ `AttendanceRecordService.getRecords` 经核对**无任何调用方**（孤儿方法）→ 随签名变更处理即可，无需兼容。
- 兜底 ID 说明：本文件**未做任何按 ID 的写探测**，**未使用任何真实业务主键**作为「不存在」样本。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。
- 治理文件（`docs/governance/*.md`、`audit/baseline/*.md`、`audit/issues/issue-register.md`、`PROJECT_ACCEPTANCE_EVIDENCE.md`、`.gitignore`）本批**未写入**。

---

**下一步（待审核）**：

1. 用户审核本草案 → 逐条回复 §8 的 **Q-A ~ Q-H**；
2. 按回复结论定稿 → 再执行代码修改（**修改前需再次逐文件确认**）；
3. 建议的提交切分：`G0 基础设施` / `G1 端点接入（含 mapper 签名变更）` / `专项1+专项2` / `Q1`（Q1 必须与 C8/C11 同批）。
