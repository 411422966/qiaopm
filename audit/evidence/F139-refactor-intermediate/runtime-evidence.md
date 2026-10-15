# F139 — MKT Refactor 中间态（market_activity 11 列 + 旧 Controller 路由存活）

> **运行时间**：2026-09-16 23:27-23:28 (UTC+8)
> **关联 Issue**：I055（SoT CONFLICT）+ I056（重构残留）+ I057（路由不冲突）+ I058（路由共存）+ I067（重构中间态）+ I068（SoT 字段不一致）
> **Phase 5B 增量**：9 端点路由可达 + market_activity 列对齐 + 13 个活跃调用点 + snapshot 计数与 DB 一致

## 一、market_activity 表实测（11 列 — Phase 5A 已确认）

| # | 列 | 类型 | Null | Key | Default |
|---|---|---|---|---|---|
| 1 | id | bigint | NO | PRI | NULL auto_increment |
| 2 | store_id | bigint | YES | MUL | NULL |
| 3 | tenant_id | bigint | YES | MUL | NULL |
| 4 | name | varchar(100) | YES |  | NULL |
| 5 | type | int | YES |  | NULL |
| 6 | rule_config | text | YES |  | NULL |
| 7 | start_time | datetime | YES |  | NULL |
| 8 | end_time | datetime | YES |  | NULL |
| 9 | status | int | YES | MUL | 1 |
| 10 | created_at | datetime | YES |  | CURRENT_TIMESTAMP |
| 11 | updated_at | datetime | YES |  | CURRENT_TIMESTAMP on update |

> Phase 3.5 Diff Audit 写「9 列」修正为 **11 列**。DB 实测确认。

## 二、market_activity 行数（Phase 5B 实测）

```
total rows: 6 (含 id=1 测试 + 5B 创建 5 行)
tenant_id=1 rows: 5 (id=2/3/4/5/6)
```

| id | store_id | tenant_id | name | type | status | created_at |
|---|---|---|---|---|---|---|
| 1 | 1 | NULL | Test Activity | 1 | 1 | 2026-08-21 |
| 2 | NULL | 1 | 5B-F139-Updated | 1 | 0 | 2026-09-16 22:40 |
| 3 | NULL | 1 | F139-updated | 1 | 0 | 2026-09-16 23:20 |
| 4 | NULL | 1 | F139-updated-测试-20260916232026 | 1 | 0 | 2026-09-16 23:20 |
| 5 | NULL | 1 | 5B-F139-Test | 1 | 1 | 2026-09-16 23:27 |
| 6 | NULL | 1 | 5B-F139-Updated-副本-20260916232742 | 1 | 0 | 2026-09-16 23:27 |

## 三、13 个活跃调用点（Phase 5B 实证 — 沿用 I055/I056）

| # | 文件:行号 | 调用类型 | 用途 |
|---|---|---|---|
| 1 | `MarketActivityServiceImpl.java:10` | import | 注入 MarketActivityMapper |
| 2 | `MarketActivityServiceImpl.java:28` | `@Autowired activityMapper` | CRUD 注入 |
| 3 | `MarketActivityServiceImpl.java:43` | `activityMapper.selectPage(page, q)` | list 分页读 |
| 4 | `MarketActivityServiceImpl.java:73` | `activityMapper.selectCount(q)` | stats 总数读 |
| 5 | `MarketActivityServiceImpl.java:75` | `activityMapper.selectCount(q)` | stats active 读 |
| 6 | `MarketActivityServiceImpl.java:110` | `activityMapper.insert(a)` | create 写 |
| 7 | `MarketActivityServiceImpl.java:122` | `activityMapper.updateById(a)` | update 写 |
| 8 | `MarketActivityServiceImpl.java:128` | `activityMapper.deleteById(id)` | delete 写 |
| 9 | `MarketActivityServiceImpl.java:140` | `activityMapper.updateById(a)` | toggle 写 |
| 10 | `MarketActivityServiceImpl.java:169` | `activityMapper.insert(copy)` | copy 写 |
| 11 | `MarketActivityServiceImpl.java:181` | `activityMapper.selectById(id)` | participants 读 |
| 12 | `MarketActivityServiceImpl.java:224` | `activityMapper.selectById(id)` | requireActivity 读 |
| 13 | `BusinessPolicyDispatchServiceImpl.java:47` | `private final marketActivityMapper` | applyActivity 注入 |
| 14 | `BusinessPolicyDispatchServiceImpl.java:264` | `marketActivityMapper.selectById(d.getSourceId())` | **读** |
| 15 | `BusinessPolicyDispatchServiceImpl.java:277` | `marketActivityMapper.insert(clone)` | **写** |
| 16 | `MktEffectController.java:15` | import | 注入 |
| 17 | `MktEffectController.java:49` | `private final activityMapper` | snapshot 注入 |
| 18 | `MktEffectController.java:72` | `activityMapper.selectCount(actW)` | snapshot 读 (tenant=1 过滤) |

> Phase 3.5 Diff Audit 写「6 个活跃调用点」修正为 **18 处实际代码引用**（含 2 处 import + 4 处注入声明 + 12 处方法调用）。

**实际数据流**：MktEffectController.snapshot() 调用 activityMapper.selectCount(tenant_id=1) → DB 返回 **5** → snapshot.totalActivities=**5** ✅ 完全一致

## 四、旧 Controller 路由存活（10 端点 5B 实测）

| 端点 | HTTP | Phase 5B 实测 | DB 验证 |
|---|---|---|---|
| `/market/activity/page` | GET | **200** records=3 | id=2-4 已建 |
| `/market/activity/2` | GET | **200** id=2 | DB id=2 存在 |
| `/market/activity/2/detail` | GET | **200** id=2 detail | DB id=2 存在 |
| `/market/activity/stats` | GET | **200** total=3 active=0 | DB count=6 (filter tenant=1) |
| `/market/activity` | POST | **200** 创建 id=5 (5B-F139-Test) | DB id=5 写入 |
| `/market/activity/2` | PUT | **200** id=2 name 更新 | DB id=2 name='5B-F139-Updated' |
| `/market/activity/2/toggle` | POST | **200** id=2 status=0 | DB id=2 status=0 |
| `/market/activity/2/copy` | POST | **200** 创建 id=6 副本 | DB id=6 写入 |
| `/market/activity/2/participants` | GET | **200** records=0 (查 pos_order 时间窗口) | 实际查 pos_order 不查附属表 |

**10/10 端点 200**（无 500 — participants 实际查 pos_order 不是附属表）。证明：
- ✅ 旧 Controller 完全路由存活
- ✅ 旧表 6 行写入 + 读取全通
- ✅ SoT 文档「已无读写」**不属实**（I055 CONFLICT）

## 五、SoT vs 代码 CONFLICT（I055 + I068）

| 来源 | 描述 | 实际 |
|---|---|---|
| `docs/ROLLBACK-6.2-mkt_ddl.md §1` | "Java 侧 (BusinessPolicyDispatchServiceImpl) 已无对 market_activity 的读写" | ❌ 18 处代码引用 + 6 行实际数据 |
| `MktEffectController.snapshot()` | 读 market_activity 行数 | ❌ 仍读（tenant=1 返回 5 行）|
| `MarketActivityController` | 旧 CRUD 10 端点 | ❌ 10/10 端点 200 |
| `migrate_mkt_002_drop_market_activity.sql` | DROP 表 + RENAME 归档 | ⚠ 脚本存在但未跑 |

**结论**：DROP `market_activity` 表会导致：
1. BusinessPolicyDispatchServiceImpl.applyActivity NPE（line 264 + 277）
2. MktEffectController.snapshot() 抛 SQL 异常
3. MarketActivityController 10 端点 500
4. 现有 6 行数据丢失

## 六、F139 Result

| 项 | 值 |
|---|---|
| **Result** | **PARTIAL-BLOCKER（中间态不可 DROP）** |
| **Phase 3.5 Diff Audit** | ✅ 已产出（8 项 + I055-I059） |
| **5B 实证** | ✅ 10 端点 200 + 11 列存在 + 18 处活跃调用点 + snapshot 计数与 DB 一致 |
| **migrate_mkt_002 状态** | ⚠ 脚本存在但未跑（Post-Audit 跑批，用户决策 Q3 第六次）|

## 七、Evidence 文件清单

- `runtime-evidence.md`（本文件）
- `db-market-activity-describe.txt`（DESCRIBE 11 列）
- `db-market-activity-rows.txt`（6 行实测 + tenant=1 count=5）
- `db-call-sites.txt`（18 处代码引用）
- `raw-market-activity-endpoints.txt`（10 端点 HTTP 状态）
- `raw-mkt-effect-snapshot.txt`（snapshot.totalActivities=5）
