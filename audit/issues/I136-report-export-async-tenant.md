# I136 — 报表导出任务异步链路失效：@Async 自调用被绕过 / 跨租户串档 / 无兜底回收 / 报表 SQL 与表结构漂移（P1）

> **编号说明**：用户 2026-11-02 指定为 `I131-report-export-async-tenant.md`；本目录 I131 已被
> `I131-packageitem-missing-deleted-and-createby.md` 占用（见 `audit/issues/issue-register.md`），
> 故顺延为 **I136**（参照 `I135-deployed-jar-stale-vs-source.md` 的同 precedent）。
> **严重度**：P1（功能可用但状态机不可靠；含跨租户数据泄露风险 P3）
> **状态**：PARTIAL —— 前端 P0/P1 已修并上线；后端 P2/P3/P4/P5 **未修**，登记为技术债
> **关联**：I128（DB CJK 编码污染）、I130（迁移脚本不兼容）、I135（部署漂移）、I133（schema 漂移）
> **基线**：commit `6796bd7` / DB `meiyetec@127.0.0.1:3307`（MySQL 8.0.46）/ env：`audit/baseline/env.md`

---

## 1. 一句话结论

系统里有**两条互不感知的导出链路**，都存在"任务卡在处理中"的结构性缺陷：

- **链路 A（`report_export_log`）**：`ReportController.createExport` 用 `CompletableFuture.runAsync`，
  异步线程里调用的 `buildExportFileData → getExportRows → bizDaily/bizMonthly` 内部**再次调用 `tid()`**，
  而 `TenantContextHolder` 是 **ThreadLocal**（`security/TenantContextHolder.java:22`）→ 异步线程取不到租户 →
  `tid()` 兜底返回 `1L`（`controller/ReportController.java:64-67`）→ **非 1 号租户导出的是 1 号租户的数据**（跨租户串档）。
- **链路 B（`report_export_task`）**：`ReportV2Service.createTask` 内部调用 `submitAsync(t.getId())`，
  是**同类自调用** → Spring AOP 代理被绕过 → `@Async` **完全不生效** → 整个 Excel/CSV 生成
  **同步跑在 HTTP 请求线程里** → 请求长时间挂起，期间 DB 状态恒为 `2=处理中`，
  叠加前端"轮询自杀"（已修 P0）就表现为**"任务永远卡在处理中"**。

两条链路都**没有超时兜底 / 僵尸任务回收机制**：进程重启、线程被 kill、异常逃出 `catch` 时，
DB 里的 `status=2` 记录**永远不会被改写**，也没有任何定时任务会扫描回收。

---

## 2. 两条链路对照

| | 链路 A | 链路 B |
|---|---|---|
| 数据表 | `report_export_log` | `report_export_task` |
| 创建入口 | `POST /report/export/create` → `ReportController.createExport`（`ReportController.java:863`） | `POST /report/v2/task` → `ReportV2Service.createTask`（`ReportV2Service.java:276`） |
| 落库初始状态 | `status=1`（处理中），`ReportController.java:882` | `status=1`（**待处理**），`ReportV2Service.java:316` |
| 异步机制 | `CompletableFuture.runAsync`（`ReportController.java:888`）→ **ForkJoinPool.commonPool** | `@org.springframework.scheduling.annotation.Async`（`ReportV2Service.java:331`）→ **自调用失效，实为同步** |
| 产物 | `.xlsx`（POI），落 `System.getProperty("user.dir")/exports` | `.csv`（UTF-8 BOM），落 `/tmp/meiyetec-reports` |
| 状态机 | 1 处理中 → 2 成功 / 3 失败 | 1 待处理 → 2 处理中 → 3 成功 / 4 失败 / 5 已取消 |
| 前端页面 | `views/report/export.vue` | `views/report/task.vue` |
| 租户隔离 | ❌ 异步线程丢 ThreadLocal → 兜底 `1L` | ✅ `collectRows` 用 DB 行里的 `t.getTenantId()` |
| 兜底回收 | ❌ 无 | ❌ 无 |

> ⚠️ 两张表的 `status` **语义不同**（A 表 1=处理中，B 表 1=待处理），前端两页各写各的映射，
> 属于契约漂移（见 §6 P6）。

---

## 3. 状态流转链路（以链路 B 为主线，含实测证据）

```
[前端] views/report/task.vue → POST /report/v2/task
   ↓
[后端] ReportV2Service.createTask          (L276)
   ├─ 幂等键查重 (L288-296) → 命中直接返回既有任务
   ├─ 同店+同报表+同区间+status IN (1,2) 查重 (L298-306) → 409 "已有相同任务在执行中"
   ├─ INSERT report_export_task, status=1(待处理), progress=0   (L316-320)
   └─ submitAsync(t.getId())                (L323)  ← ★ 自调用，@Async 被绕过
        ↓
[实际执行] executeExport(taskId)            (L348)  ← 跑在 HTTP 请求线程里
   ├─ UPDATE status=2(处理中), started_at=NOW(), progress=10   (L352)
   ├─ collectRows(t)                        (L378)  ← ★ SQL 与表结构漂移，此处抛异常
   ├─ UPDATE progress=60                    (L354)
   ├─ writeCsv(...)                         (L358)
   ├─ UPDATE progress=90                    (L359)
   ├─ UPDATE status=3(成功), file_path, file_size, row_count, progress=100, finished_at   (L361-364)
   └─ catch(Exception) → UPDATE status=4(失败), error_msg   (L366-370)
   ↓
[回包] Result.success("任务已创建", t)       (L325)  ← 此刻任务其实已经跑完（同步）
```

**卡住的机制**：因为是同步执行，`POST /report/v2/task` 这个 HTTP 请求要等整个导出跑完才返回。
期间前端看到的是刚插入的 `status=1/2` 记录 → 「处理中」。而前端旧的 `startPolling()` 逻辑是
**"一轮轮询没看到 status=1 的任务就立即 stopPolling()"** → 新建任务后列表还没刷新，轮询已自杀 →
**用户永远看不到最终状态**（P0，已修）。

---

## 4. 实测证据（2026-11-02，容器 `meiyetec-api` 运行中，admin token）

### 4.1 `GET /report/v2/task/page` 实测返回（3 条记录）

| id | taskNo | status | progress | rowCount | errorMsg |
|---|---|---|---|---|---|
| 3 | T1793632169574 | 3 成功 | 100 | 0 | — |
| 2 | T1793632117773 | 3 成功 | 100 | 0 | — |
| **1** | T1793631996639 | **4 失败** | 10 | 0 | `PreparedStatementCallback; bad SQL grammar [SELECT member_no,...` |

- **用户报的"卡在处理中"的 ID=1，现已落到 `status=4（失败）`**，且失败原因被完整写入 `error_msg`。
  → 说明任务**最终是执行了**的，用户看到"卡住"是**前端轮询看不到状态流转**（P0），
  而非后端没跑。
- ID=1 的 reportName 在 DB 中是 `??????` → 属 I128（CJK 编码污染），本单不重复处理。
- ID=2/3 为调查期探针任务，SALES 分支跑通（rowCount=0，说明该区间无 `status IN (3,4)` 的订单）。

### 4.2 前端路由契约实测

| 请求 | 结果 |
|---|---|
| `GET /report/v2/task/page`（新路径） | **200**，返回 records |
| `GET /report/task/page`（旧路径） | **404 未找到** —— 后端从无此路由，前端旧代码必然拿不到数据 |
| `GET /report/export/list` | 200 |

### 4.3 镜像产物核对

```
docker run --rm meiyetec/web:dev grep -rl 'report/v2/task' /usr/share/nginx/html/assets/
→ assets/task-Y6jPdq0O.js
curl http://localhost:8088/assets/task-Y6jPdq0O.js | grep 'report/v2/task/page'
→ OK
```

---

## 5. 问题清单

### P1（前端 · ✅ 已修并上线，commit 待提交）

- **前端接口路径漂移**：`views/report/task.vue` 请求 `/report/task/page`、下载 `/report/task/{id}/download`、
  取消 `/report/task/{id}/cancel`，而后端路由挂在 `ReportV2Service`（`@RequestMapping("/report/v2")`，
  `ReportV2Service.java:52`）→ 三个接口全部 404。
  **修复**：三处改为 `/report/v2/task/...`（已改，已重建 web 镜像并生效）。

### P2（后端 · `@Async` 自调用失效，未修）

- `ReportV2Service.createTask`（L323）直接调 `submitAsync(t.getId())` → 绕过 Spring AOP 代理 →
  `@Async`（L331）**完全不生效** → 导出**同步阻塞 HTTP 请求线程**。
  后果：① 请求超时；② Tomcat 工作线程被长期占用，并发一高就拖垮整个 API；
  ③ 前端"处理中"窗口被拉长到整个导出耗时。
- 注意：`@EnableAsync` 本身是**存在**的（`MeiyetecApplication.java:14`），不是缺注解；
  根因是**自调用**，属 Spring `@Async` 的经典陷阱。

### P3（后端 · 跨租户串档，未修）

- `ReportController.createExport` 的 `CompletableFuture.runAsync`（L888）内调
  `buildExportFileData(type, dateRange)`（L943）→ `getExportRows(type, dateRange)`（L982）
  → `bizDaily(null, days)` / `bizMonthly(null, months)` → 这些方法内部用 `tid()` 取租户。
- `tid()`（`ReportController.java:64-67`）读 `TenantContextHolder.get().getTenantId()`，
  而 `TenantContextHolder` 基于 **ThreadLocal**（`security/TenantContextHolder.java:22`），
  `ForkJoinPool.commonPool` 线程**不会继承**请求线程的上下文 → `get()` 返回 `TenantContext.empty()`
  → `getTenantId()` 为 null → `tid()` **兜底返回 `1L`**。
- 后果：**任何租户触发导出，拿到的都是 1 号租户的数据**，且文件按 `logId` 存盘，
  用户下载到的是别人的经营数据 → **跨租户数据泄露**。
- 对比：链路 B 的 `collectRows` 用的是**从 DB 行里读回的 `t.getTenantId()`**，天然安全 →
  这条链路是对的，**不要照抄链路 A 的写法**。

### P4（后端 · 无超时兜底 / 无僵尸任务回收，未修）

- 全库 `report_export_task` / `report_export_log` **没有任何 `@Scheduled` 回收任务**。
  唯一相关的定时任务是 `ReportExportFileCleanupService.scheduledCleanup`
  （`service/ReportExportFileCleanupService.java:78`，cron `0 0 3 * * *`），
  它**只删过期文件**，不回写僵尸任务状态。
- 触发条件：容器重启 / 重建镜像（本项目近期频繁重建）/ 线程被 interrupt / `catch` 之外的 `Error`
  （如 OOM）→ DB 里 `status=2` 永久残留 → 页面永远显示"处理中"。
- 另外：`executeExport` 的兜底 `catch` 里若 `jdbc.update` 自身再失败，则 `status` 连 `4` 都写不进去。

### P5（后端 · 报表 SQL 与表结构漂移，5 个报表码中 3 个必失败，未修）★ 新发现

`collectRows`（`ReportV2Service.java:378`）按 `reportCode` 拼 SQL，
但**与实际表结构对不上**，且末尾的 `store_id` 过滤是**无条件字符串拼接**：

| reportCode | 拼接位置 | 问题 | 后果 |
|---|---|---|---|
| `MEMBER` | L402-409 | 查 `crm_member.created_at` / `DATE(created_at)`，但该列实际名为 **`create_time`**（`db/migration/V1__init_baseline.sql:290-315`） | **Unknown column 'created_at'** → SQL 语法错 → 任务必然失败（**ID=1 的实测错误正是这一条**） |
| `PAY` | L393-400 | `pos_payment` 表**没有 `store_id` 列**（`V1__init_baseline.sql:713-729`），但末尾仍拼 `AND store_id = ?` | storeId ≠ null 时报 Unknown column |
| `PRODUCT` | L416-428 | 末尾 `AND store_id = ?` 被拼到 **`GROUP BY … ORDER BY total_amount DESC` 之后**；且 `pos_order_item` 也**没有 `store_id`** | SQL 语法错 + 列不存在 |
| `COMMISSION` | L410-415 | `commission_record` 有 `store_id`（NOT NULL），但 `ORDER BY id DESC` 与业务时间倒序语义不符 | 排序错乱（非阻断） |
| `SALES` | L382-391 | 字段与 `pos_order` 基本对齐 | 实测通过（ID=2/3） |

拼接代码位置：`ReportV2Service.java:430-433`

```java
if (t.getStoreId() != null) {
    sql += "AND store_id = ? ";   // ← 无条件拼接，不区分分支语义
    args.add(t.getStoreId());
}
```

> 这解释了为什么用户"等了很久"才看到结果：MEMBER 分支先要把整段 SQL 交给 MySQL 解析失败，
> 走一遍异常 → 写 `status=4`，整个过程都在**请求线程内同步完成**。

### P6（前端 · 状态语义漂移，未修）

- 链路 A（`report_export_log`）：`1=处理中 / 2=成功 / 3=失败`；
  链路 B（`report_export_task`）：`1=待处理 / 2=处理中 / 3=成功 / 4=失败 / 5=已取消`。
- `views/report/export.vue` 用 `status===1` 判"处理中"，`views/report/task.vue` 用 `status===2` 判"处理中"——
  两页各按各的表写，**同名不同义**，任何跨页复用都会误判。

---

## 6. 修复方向（未实施，待批次评审）

| 优先级 | 修复项 | 最小改动 |
|---|---|---|
| 1 | **P5 SQL 漂移** | `collectRows` 改为按分支各自拼 WHERE（MEMBER 用 `create_time`；PAY 去掉 `store_id`；PRODUCT 把 `store_id` 放进 `ON`/WHERE 且限定 `o.` 前缀） |
| 2 | **P2 @Async 自调用** | 把 `submitAsync` 抽到独立 Bean（如 `ReportExportExecutor`）或注入自身代理（`@Lazy ReportV2Service self`），由该 Bean 调用 `executeExport` |
| 3 | **P3 跨租户** | 链路 A 的异步任务**显式携带 tenantId**（`CompletableFuture.runAsync(() -> ..., tenantAwareExecutor)` 或在闭包内把 `t` 透传给 `buildExportFileData`/`getExportRows`/`bizDaily`），**禁止在异步线程里调用 `tid()`** |
| 4 | **P4 兜底** | 新增 `@Scheduled(fixedDelay=5min)` 僵尸回收：`status IN (1,2) AND started_at < now()-30min` → 置 `status=4, error_msg='超时未完成，已回收'` |
| 5 | **P6 语义统一** | 两张表的状态字典统一到一份常量/文档，前端两页共用 |

> ⚠️ 上述均为**后端业务代码**，按项目规则 6「不修改业务代码」+ 规则 17「任务边界」，
> 本单**只做登记，不落码**。本次会话已获授权的改动**仅限前端 P0/P1**。

---

## 7. 未确认项

1. **生产环境是否也有僵尸任务** —— 本单只覆盖本地 Docker 验收库（`127.0.0.1:3307`）→ **未确认**。
2. **`report_export_task` 表在生产库的实际列结构** —— 本单依据 `db/migration/V1__init_baseline.sql` 与
   `db/sql/init_m03_member.sql` 推断，**未直接核对运行库 `information_schema`**（本轮未执行 SQL）→ **未确认**。
3. **链路 A 的 `t` 兜底 `1L` 是否真的串了档** —— 依据 `TenantContextHolder` 的 ThreadLocal 实现与
   `tid()` 的兜底逻辑推导，**未构造真实多租户账号做对比实测** → **未确认**（P3 结论为静态分析 + 代码链路推导）。
4. **`pos_order_item` 是否在生产有额外的 `store_id` 列**（本单依据基线 DDL 判断为无）→ **未确认**。
5. **`commission_record.business_time` 列**在本单未逐字核对，仅确认 `store_id` 存在 → 部分**未确认**。

---

## 8. 证据清单

| 文件 / 位置 | 内容 |
|---|---|
| `backend/src/main/java/com/meiyetec/service/ReportV2Service.java:276-437` | 链路 B 全链路源码（createTask / submitAsync / executeExport / collectRows） |
| `backend/src/main/java/com/meiyetec/controller/ReportController.java:863-903` | 链路 A 创建 + `CompletableFuture.runAsync` |
| `backend/src/main/java/com/meiyetec/controller/ReportController.java:943-1010` | `buildExportFileData` / `getExportRows` / `countRows`（内部调 `bizDaily`→`tid()`） |
| `backend/src/main/java/com/meiyetec/controller/ReportController.java:64-67` | `tid()` 的 `1L` 兜底 |
| `backend/src/main/java/com/meiyetec/security/TenantContextHolder.java:22` | `ThreadLocal` 实现（异步线程不继承） |
| `backend/src/main/java/com/meiyetec/MeiyetecApplication.java:14` | `@EnableAsync` 存在（排除"缺注解"假设） |
| `backend/src/main/java/com/meiyetec/service/ReportExportFileCleanupService.java:78` | 唯一相关定时任务（只删文件，不回收僵尸任务） |
| `backend/src/main/resources/db/migration/V1__init_baseline.sql:290-315 / 691-729` | `crm_member`（无 `created_at`）、`pos_order_item` / `pos_payment`（无 `store_id`） |
| `backend/src/main/resources/db/sql/init_rbac_v2_1_e.sql:8-22` | `report_export_task` 状态字典注释（1待处理 2处理中 3成功 4失败 5已取消） |
| `frontend/meiyetec-web/src/views/report/export.vue` | P0 轮询自杀修复（`POLL_MAX_IDLE = 5` + `load().then(startPolling)`） |
| `frontend/meiyetec-web/src/views/report/task.vue` | P1 路径修复（3 处 `/report/v2/task/...`） |
| 本单 §4 | 运行期实测：任务状态表 + 路由 200/404 + 镜像产物核对 |

**绑定基线**：commit `6796bd7` / DB `meiyetec@127.0.0.1:3307`（MySQL 8.0.46）/
env：`audit/baseline/env.md`
