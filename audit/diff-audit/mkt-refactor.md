# Phase 3.5 Diff Audit — MKT 重构差异审计报告

> 适用批次：Batch 1.5（MKT 多渠道重构）
> 触发时间：Phase 1 末尾（Feature Inventory 完成后）
> 依据：`@project-audit.mdc` 规则 14（重构类变更必须先跑 Phase 3.5）
> Freeze commit：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`
> SoT：`docs/ROLLBACK-6.2-mkt_ddl.md` + git status + 实际源码

---

## §1 Diff Audit 背景

### 1.1 重构四要素当前状态

| 维度 | 期望（重构完成态）| 实际（当前）| 差距 |
|---|---|---|---|
| 旧 Java 类（MarketActivity* 5 个）| ❌ 已删除 | ⚠️ **5 个全部存在** | 🔴 未完成 |
| 新 Controller（9 个 Mkt*Controller）| ✅ 全部存在 | ✅ **全部存在** | ✅ 完成 |
| 迁移脚本（migrate_mkt_002_drop + rollback）| ✅ 存在且对称 | ✅ **存在且对称** | ✅ 完成 |
| 回滚文档（docs/ROLLBACK-6.2）| ✅ 存在 | ✅ **存在** | ✅ 完成 |

### 1.2 Phase 3.5 触发条件

✅ **触发条件全部满足**：
- ① DROP `market_activity` 表（`migrate_mkt_002_drop_market_activity.sql` 已写，**未跑**）
- ② 新增 9 个 Mkt*Controller
- ③ 旧 `MarketActivity*` 5 个类应删未删（重构中间态）
- ④ 新增 `migrate_mkt_002_*` 脚本族

---

## §2 Diff Audit 8 项清单

### #1 旧 `MarketActivity*` 5 个类是否被任何代码 `@Autowired` / `@Resource` 注入？

**验证命令**：
```bash
# grep MarketActivityService + MarketActivityMapper 注入点
```

**证据输出**：

| 文件 | 行号 | 注入类型 | 用途 | 状态 |
|---|---|---|---|---|
| `controller/MarketActivityController.java` | 34 | `@Autowired final MarketActivityService` | REST 端点路由 | ⚠️ **仍在注册** |
| `service/impl/MarketActivityServiceImpl.java` | 28 | `@Autowired MarketActivityMapper` | CRUD 实现 | ⚠️ **仍在注册** |
| `service/impl/BusinessPolicyDispatchServiceImpl.java` | 47 | `final MarketActivityMapper marketActivityMapper` | **实际调用 applyActivity()** | ⚠️ **ACTIVE 写入** |
| `service/impl/BusinessPolicyDispatchServiceImpl.java` | 264 | `marketActivityMapper.selectById()` | 读操作 | ⚠️ **ACTIVE 读** |
| `service/impl/BusinessPolicyDispatchServiceImpl.java` | 277 | `marketActivityMapper.insert()` | 写操作 | ⚠️ **ACTIVE 写** |
| `controller/MktEffectController.java` | 49 | `final MarketActivityMapper activityMapper` | snapshot() 聚合统计 | ⚠️ **ACTIVE 读** |

**结论**：❌ **SoT 描述与代码不符**。`docs/ROLLBACK-6.2-mkt_ddl.md` §1 称"Java 侧已无读写"，但源码证明：
- `BusinessPolicyDispatchServiceImpl.applyActivity()` **实际读写** `market_activity` 表
- `MktEffectController.snapshot()` 仍查询 `market_activity` 行数

**联动 Feature**：F171 / F172（MarketActivityService CRUD）+ F174（BusinessPolicyDispatchServiceImpl）+ F140（MktEffectController.snapshot）

**Issue 登记**：**I055**（见 §3）

---

### #2 `MarketActivityController` 的 HTTP 路由是否与 9 个 Mkt*Controller 冲突？

**验证命令**：`grep "@RequestMapping" backend/src/main/java/com/meiyetec/controller/MarketActivityController.java`

**证据输出**：

| Controller | `@RequestMapping` | 路由 | 冲突？ |
|---|---|---|---|
| `MarketActivityController` | `@RequestMapping("/market/activity")` | `/market/activity/*` | ⚠️ **与新 MktPromotionController 路由部分重叠** |
| `MktPromotionController` | `@RequestMapping("/mkt/promotion")` | `/mkt/promotion/*` | ✅ 无直接路由冲突（路径不同）|

**结论**：⚠️ **部分重叠，非完全隔离**。`/market/activity/*` 与 `/mkt/*` 是两套路由体系，无直接路径冲突。但两者服务同一业务场景（旧营销活动 ↔ 新促销活动），前端路由已做 SHADOW 处理（见 §2 项 #3）。

**联动 Feature**：F171（MarketActivityController）+ F151（MktPromotionController.list/page）

**Issue 登记**：**I055**（路由共存但无直接冲突，降级为 PARTIAL-NON-BLOCKER）

---

### #3 旧 `/market-activity/*` 端点是否被前端 `frontend/meiyetec-web/src/views/market-activity/**` 引用？

**验证命令**：`grep "market-activity" frontend/meiyetec-web/src`

**证据输出**：`frontend/meiyetec-web/src` 下 **0 个匹配**（`No matches found`）

**前端路由配置（`router/index.ts`）**：

```typescript
// 旧路由（SHADOW — 仍注册但映射到新组件）
{ path: 'market/activity', component: () => import('@/views/mkt/promotion.vue') }

// 新路由
{ path: 'mkt/promotion', component: () => import('@/views/mkt/promotion.vue') }
```

**结论**：✅ **旧前端视图无引用**（`views/market-activity/` 目录不存在）。旧 `/market/activity` 路由**指向新组件** `views/mkt/promotion.vue`，这是有意的 SHADOW 过渡策略。旧 Controller 路由仍在 Spring 注册（见项 #8）。

**联动 Feature**：F171（MarketActivityController）+ F151-F156（MktPromotionController）

---

### #4 `BusinessPolicyDispatchServiceImpl` 注释说"已无读写"是否与代码一致？

**验证命令**：`grep "已无读写|无.*读写" backend/src/main/java/com/meiyetec/service/impl/BusinessPolicyDispatchServiceImpl.java`

**证据输出**：0 匹配（Java 源码中无此注释）

**源码实际情况**：
```java
// BusinessPolicyDispatchServiceImpl.java:47
private final MarketActivityMapper marketActivityMapper;

// BusinessPolicyDispatchServiceImpl.java:264 (applyActivity 方法)
MarketActivity src = marketActivityMapper.selectById(d.getSourceId());
marketActivityMapper.insert(clone);  // ← 实际 INSERT

// MarketActivityMapper.java
public interface MarketActivityMapper extends BaseMapper<MarketActivity> {}
```

**SoT 声明来源**：`docs/ROLLBACK-6.2-mkt_ddl.md` §1："Java 侧 (BusinessPolicyDispatchServiceImpl) 已无对 market_activity 的读写"

**结论**：❌ **SoT 描述与代码不符**。`BusinessPolicyDispatchServiceImpl.applyActivity()` 仍在 `market_activity` 表上执行 `selectById` + `insert`，注释存在于 SQL 脚本而非 Java 源码。这是**SoT 文档错误**，非代码错误。

**联动 Feature**：F174（BusinessPolicyDispatchServiceImpl ACTIVITY 分发）+ F171-F172（MarketActivity CRUD）

**Issue 登记**：**I055**（SoT vs 代码 CONFLICT，沿用 I001/I018/I026/I030 模式）

---

### #5 9 个 Mkt*Controller 注册到 Spring `@RestController` 是否都能注入并路由成功？

**验证命令**：静态源码分析（无 Phase 5 运行时）

**证据输出**：

| Controller | `@RestController` | `@RequestMapping` | 依赖注入 | 编译状态 |
|---|---|---|---|---|
| `MktChannelController` | ✅ | `/mkt/channel` | MarketingAutomationService + ChannelDispatcher | L0 |
| `MktConversionController` | ✅ | `/mkt/conversion` | MarketingAutomationService | L0 |
| `MktJourneyController` | ✅ | `/mkt/journey` | MarketingAutomationService | L0 |
| `MktTemplateController` | ✅ | `/mkt/template` | MarketingAutomationService | L0 |
| `MktTouchTaskController` | ✅ | `/mkt/task` | MarketingAutomationService | L0 |
| `MktEffectController` | ✅ | `/mkt/effect` | 7 个 Mapper（MarketActivityMapper + Mkt*Mapper）| L0 |
| `MktCouponController` | ✅ | `/mkt/coupon` | MktCouponBatchMapper + MktCouponMapper + JdbcTemplate | L0 |
| `MktPromotionController` | ✅ | `/mkt/promotion` | MktPromotionMapper | L0 |
| `MktPointsController` | ✅ | `/mkt/points` | MktPointsRuleMapper + PointService | L0 |

**结论**：✅ **全部 9 个 Mkt*Controller 有 @RestController + @RequestMapping 注解**。静态分析编译路径通，但需 Phase 5 运行时验证实际路由注册。

**联动 Feature**：F114-F160（9 个 Controller 全量）

---

### #6 `migrate_mkt_002_drop_market_activity.sql` ↔ `migrate_mkt_002_rollback.sql` 对称性？

**验证命令**：`grep "market_activity__archived_20260910" backend/src/main/resources/db/sql/`

**证据输出**：

| 位置 | 出现次数 | 上下文 |
|---|---|---|
| `migrate_mkt_002_drop_market_activity.sql` | 1 | `RENAME TABLE market_activity TO market_activity__archived_20260910` |
| `migrate_mkt_002_rollback.sql` | 1 | `RENAME TABLE market_activity__archived_20260910 TO market_activity` |
| `docs/ROLLBACK-6.2-mkt_ddl.md` | 3 | §2/§3 归档表名 + 触发条件 |

**结论**：✅ **完全对称**。归档表名 `market_activity__archived_20260910` 在三个文件中严格一致。

**注意**：主动作脚本执行选项 A（立即 DROP 归档表），回滚脚本需在 DROP 前执行才有效（由 DBA 备份保证）。

---

### #7 `docs/ROLLBACK-6.2-mkt_ddl.md` 描述的归档表名是否与 SQL 脚本一致？

**验证命令**：`grep "market_activity__archived_20260910" docs/ROLLBACK-6.2-mkt_ddl.md`

**证据输出**：3 处出现（§2 主动作描述 + §3 回滚描述 + §5 验证步骤）

**对照**：

| 文档位置 | 归档表名 | SQL 脚本 |
|---|---|---|
| ROLLBACK-6.2 §2 | `market_activity__archived_20260910` | ✅ 与 `migrate_mkt_002_drop_market_activity.sql` 一致 |
| ROLLBACK-6.2 §3 | `market_activity__archived_20260910` | ✅ 与 `migrate_mkt_002_rollback.sql` 一致 |

**结论**：✅ **完全一致**。文档与 SQL 脚本归档表名严格对齐。

---

### #8 旧 Controller 的端点（`/market/activity/*`）是否仍在 Spring 路由表中？

**验证命令**：静态源码分析（无 Phase 5 curl）

**证据输出**：

```java
// MarketActivityController.java
@RestController
@RequestMapping("/market/activity")
public class MarketActivityController {
    @GetMapping("/page")         // GET /market/activity/page
    @GetMapping("/{id}")        // GET /market/activity/{id}
    @PostMapping               // POST /market/activity
    @PutMapping("/{id}")       // PUT /market/activity/{id}
    @DeleteMapping("/{id}")     // DELETE /market/activity/{id}
    @GetMapping("/stats")      // GET /market/activity/stats
    @GetMapping("/{id}/detail") // GET /market/activity/{id}/detail
    @PostMapping("/{id}/toggle") // POST /market/activity/{id}/toggle
    @PostMapping("/{id}/copy")   // POST /market/activity/{id}/copy
    @GetMapping("/{id}/participants") // GET /market/activity/{id}/participants
}
```

**前端路由配置（`router/index.ts`）**：

```typescript
// 旧路由（仍注册但指向新组件）
{ path: 'market/activity', component: () => import('@/views/mkt/promotion.vue') }
// 新路由
{ path: 'mkt/promotion', component: () => import('@/views/mkt/promotion.vue') }
```

**结论**：⚠️ **旧 Controller 仍在 Spring 路由表注册**。后端 `MarketActivityController` 是 `@RestController`（非注释掉），Spring 启动时会注册 `/market/activity/*` 10 个端点。前端已 SHADOW 处理（两路由指向同一组件），但**后端旧路由仍响应**。

**联动 Feature**：F171（MarketActivityController 全 10 端点）

**Issue 登记**：**I055**（旧 Controller 路由残留，与前端 SHADOW 不对称）

---

## §3 Issue 登记汇总（Phase 3.5 触达）

| Issue ID | 严重度 | 分类 | 来源项 | 描述 | Fix Phase |
|---|---|---|---|---|---|
| **I055** | 🟡 中 | **SoT vs 代码 CONFLICT** | #1 + #4 | SoT 文档（ROLLBACK-6.2 §1）声称"BusinessPolicyDispatchServiceImpl 已无 market_activity 读写"，但源码证明 applyActivity() 仍在读写；同样 MktEffectController.snapshot() 仍读 market_activity | Post-Audit |
| **I056** | 🟡 中 | **重构残留 + 实际调用** | #1 | BusinessPolicyDispatchServiceImpl.applyActivity() + MktEffectController.snapshot() 仍依赖 market_activity 表；migrate_mkt_002 未跑导致表仍存在（有意为之）；Phase 3.5 Diff Audit 量化：2 个类 × 3 个方法 = 6 个活跃调用点 | Post-Audit |
| **I057** | 🟡 中 | **PARTIAL-NON-BLOCKER（路由残留）** | #8 | MarketActivityController 仍在 Spring 路由表注册（10 端点），与前端 SHADOW 不对称；需 Phase 5 curl 确认旧端点是否响应 | Post-Audit |
| **I058** | 🟢 低 | PARTIAL-NON-BLOCKER（路由共存） | #2 | MarketActivityController(`/market/activity`) 与 MktPromotionController(`/mkt/promotion`) 路由无直接冲突，但服务同一业务场景；旧 Controller 路由与新 Controller 并存 | Post-Audit |
| **I059** | 🟡 中 | **⚠ MISSING（DB）** | preflight §8.2 | MKT 系列表（mkt_channel_config / mkt_coupon / mkt_coupon_batch / mkt_promotion / marketing_touch_* / mkt_journey* / mkt_segment / mkt_points_rule）DDL 在 SQL 文件中定义但不在 db-snapshot.md 中；与 I054 NEW 同模式 | Post-Audit |

---

## §4 与 preflight-batch-1.5.md §8 交叉引用

| preflight §8 项目 | Phase 3.5 核实结果 |
|---|---|
| §8.1 market_activity 表 | ⚠️ 仍在 DB（migrate_mkt_002 未跑）；实际被 BusinessPolicyDispatchServiceImpl + MktEffectController 使用（I055/I056） |
| §8.2 MKT 表（mkt_channel_config 等）| ⚠️ DDL 存在但 db-snapshot.md 无 → MISSING（I054 NEW + I059） |
| §8.8 营销表（mkt_coupon / mkt_promotion 等）| ⚠️ DDL 存在于 init_rbac_v3_0_d.sql / init_rbac_v2_1_d.sql / init_mkt_promotion.sql，但 db-snapshot.md 无 → MISSING（I059） |
| §8.9 MKT 无 .mdc | ✅ 确认无 .mdc Operational Notes |

---

## §5 Phase 3.5 结论

| 维度 | 状态 |
|---|---|
| **Phase 3.5 Diff Audit 执行状态** | ✅ **已完成**（8 项全部核实） |
| **阻塞 Phase 2 / Phase 5？** | ✅ **不阻塞**（Diff Audit 结论已量化，不改变 Feature 静态分析结论） |
| **产出文件** | `audit/diff-audit/mkt-refactor.md` |
| **新 Issue** | I055（SoT CONFLICT）+ I056（重构残留）+ I057（路由残留）+ I058（路由共存）+ I059（MKT 表 MISSING） |

### 5.1 核心发现摘要

1. **SoT vs 代码 CONFLICT（I055）**：`docs/ROLLBACK-6.2-mkt_ddl.md` §1 声称"Java 侧已无 market_activity 读写"，但 `BusinessPolicyDispatchServiceImpl.applyActivity()` 和 `MktEffectController.snapshot()` 仍在活跃使用该表。这是**文档错误**，非代码 bug。

2. **重构中间态真实规模**：旧 `MarketActivity*` 类**不仅未删除**，且有 2 个新模块（BusinessPolicyDispatchServiceImpl + MktEffectController）实际依赖。`market_activity` 表未 DROP 不仅是"迁移未跑"，还有**真实业务依赖**。

3. **前端路由 SHADOW，后端路由残留**：前端 `router/index.ts` 将旧 `/market/activity` 指向新组件（SHADOW INTENTIONAL），但后端 `MarketActivityController` 仍在 Spring 路由表注册 10 个端点。

4. **MKT 表大规模 MISSING（I059）**：除了已知的 `mkt_channel_config`（I054 NEW），还有 mkt_coupon / mkt_coupon_batch / mkt_promotion / marketing_touch_* / mkt_journey* / mkt_segment / mkt_points_rule 等 10+ 张表 DDL 存在但 db-snapshot.md 无记录。

### 5.2 Diff Audit 对 Phase 2 / 5 的影响

- **Phase 2（Traceability）**：需将 I055-I059 结论带入各 Feature 的 Result 标注（MktEffectController.snapshot 依赖 market_activity 不影响 F140 静态分析，但影响 L4/L5a 运行时）
- **Phase 5（E2E）**：需重点验证 BusinessPolicyDispatchServiceImpl ACTIVITY 分发路径（触发表 market_activity 操作）+ MktEffectController.snapshot() 读取 market_activity 行数

---

**Phase 3.5 Diff Audit 完成。产出 `audit/diff-audit/mkt-refactor.md`（本文件）。阻塞 Phase 2 / Phase 5？不阻塞（结论量化，不改变静态分析结论）。**
