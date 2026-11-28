# I213 — `LogRetentionTaskSmokeTest` 直接调用生产定时任务 `cleanExpiredLogs()`，每次 `mvn test` 删除 `sys_log` 历史数据（无测试隔离）

> **状态**：**Partial（2026-11-28，commit `ce7fb73`）** —— **方案 A（`@Disabled`）已实施**，T02-3 不再执行、不再删数据；方案 B（mock 重写）待做
> **严重度**：**P1**
> **发现日期**：2026-11-28（I197 第 1 批 P3 执行过程中暴露）
> **登记人**：Cursor
> **绑定基线**：HEAD `14a1b56`（发现时）+ `ce7fb73`（方案 A 落地）；`application.yml` 密钥改动已随 `99bac3e` 提交
> **验证等级**：**运行 VERIFIED**（实测 DELETE 影响 145 行 + MyBatis 日志原文 + 前后行数对比 + 回滚后复测一致）
> **关联**：**I197**（本 issue 在 I197 P3 中暴露）、**I193**（同族：测试直连本地业务库无隔离）、**D-3-38**
> **证据目录**：`audit/evidence/F176-i197-p3-jwt-secret-fix/`

---

## 0. 一句话

`LogRetentionTaskSmokeTest` 的 4 个用例直接调用生产定时任务 `logRetentionTask.cleanExpiredLogs()`，
该任务在真实表 `sys_log` 上执行 `DELETE FROM sys_log WHERE (created_at < ?) LIMIT 5000`，
**无测试库隔离、无租户过滤、无 dry-run 开关** → 每执行一次 `mvn test` 就删除一次 90 天前的真实日志。

---

## 1. 事实

### 1.1 测试代码（全文）

文件：`backend/src/test/java/com/meiyetec/task/LogRetentionTaskSmokeTest.java`（**87 行**）

> 📌 **行数口径（2026-11-28 实测）**：`Get-Content ... | Measure-Object -Line` = **73**，
> 但该命令**不统计空行**（本文件 14 行为空行）；`Get-Content ... .Count` = **87**，
> 与本节下方代码块行数、文件实际行数**完全一致**（`Compare-Object` 比对无差异）→ 本文档取 **87**。

```java
package com.meiyetec.task;

import com.meiyetec.service.SysConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-T02 · 验证 sys_config 表 + LogRetentionTask 调度链路打通
 *
 * <p>覆盖：
 * <ul>
 *   <li>SysConfigServiceImpl.getInt("log.retention-days") 真实读 DB → 90</li>
 *   <li>LogRetentionTask.cleanExpiredLogs() 不抛任何异常（无 sys_config doesn't exist）</li>
 *   <li>sys_log 表在跑一次后行数不变（cutoff=90 天，全部仍在保留期内，不应被误删）</li>
 *   <li>idempotency.retention-hours 默认 24 也已落库</li>
 * </ul>
 *
 * <p>前置条件：已应用 {@code migrate_t02_sys_config.sql} 到 meiyetec DB
 */
@SpringBootTest
class LogRetentionTaskSmokeTest {

    @Autowired
    private SysConfigService configService;

    @Autowired
    private LogRetentionTask logRetentionTask;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("T02-1: SysConfigService 真实读取 sys_config.log.retention-days = 90")
    void sys_config_reads_retention_days() {
        int days = configService.getInt("log.retention-days", 0);
        assertEquals(90, days, "sys_config 应有 log.retention-days = 90");
    }

    @Test
    @DisplayName("T02-2: SysConfigService 真实读取 sys_config.idempotency.retention-hours = 24")
    void sys_config_reads_idempotency_hours() {
        int hours = configService.getInt("idempotency.retention-hours", 0);
        assertEquals(24, hours, "sys_config 应有 idempotency.retention-hours = 24");
    }

    @Test
    @DisplayName("T02-3: LogRetentionTask.cleanExpiredLogs() 跑通不抛异常")
    void log_retention_task_runs_clean() {
        // 任务内自带 try-catch，若 sys_config 表不存在会走 log.error 分支
        // 这里用 sys_log 行数前后对比 + 不抛异常双重保证
        Integer before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_log", Integer.class);

        // 直接调用（同 @Scheduled cron 触发的就是此方法）
        assertDoesNotThrow(() -> logRetentionTask.cleanExpiredLogs(),
                "LogRetentionTask.cleanExpiredLogs() 不应抛任何异常");

        Integer after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_log", Integer.class);

        assertNotNull(before);
        assertNotNull(after);
        assertTrue(after <= before,
                "sys_log 行数不应增长（清理任务只删），before=" + before + " after=" + after);

        // 90 天 cutoff 之下不应误删 sys_log 中的活跃数据
        // （除非显式 seed 了过期日志；这里只断言"不抛 + 行数只减不增"）
        System.out.println("[T02-3] cleanExpiredLogs 完成：before=" + before + " after=" + after);
    }

    @Test
    @DisplayName("T02-4: sys_config 表结构含 8 列（id, key, value, group, desc, sort, created, updated）")
    void sys_config_has_expected_columns() {
        Integer colCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = 'meiyetec' AND table_name = 'sys_config'",
                Integer.class);
        assertNotNull(colCount);
        assertEquals(8, colCount.intValue(),
                "sys_config 应有 8 列（含 created_at/updated_at），实际=" + colCount);
    }
}
```

> ⚠️ **断言缺口**：T02-3 的唯一约束是 `after <= before`（"只减不增"）——
> **减多少都不算失败**。因此该测试在真实删除 145 行时**依然是绿的**（P3 跑测实测 `Tests run: 4, Failures: 0, Errors: 0`）。

### 1.2 生产任务（SQL 原文与行号）

文件：`backend/src/main/java/com/meiyetec/task/LogRetentionTask.java`

| 行号 | 原文 |
|---|---|
| 33 | `@Scheduled(cron = "0 0 3 * * ?")` |
| 34 | `public void cleanExpiredLogs() {` |
| 37 | `int days = resolveRetentionDays();` |
| 38 | `LocalDateTime cutoff = LocalDateTime.now().minusDays(days);` |
| 44 | `qw.lt("created_at", cutoff).last("LIMIT 5000");` |
| 45 | `deleted = logMapper.delete(qw);` |
| 50 | `} while (deleted > 0 && attempt < 50);` |

- 生效参数：`sys_config.log.retention-days = 90`（`resolveRetentionDays()` 读 `configService.getInt("log.retention-days", 90)`，越界回退 90）。
- **无租户过滤**：SQL 只按 `created_at`，不带 `tenant_id` 条件。
- **无 dry-run / 无开关**：方法直接执行 DELETE，唯一保护是 `@Scheduled` 的 cron —— 但测试是**直接方法调用**，cron 完全不参与。

### 1.3 实测（2026-11-28 P3 跑测）

| 观测点 | 跑测前 | 跑测后 | 依据 |
|---|---|---|---|
| `sys_log` 总行数 | **13154** | **13009** | 测试自身打印：`[T02-3] cleanExpiredLogs 完成：before=13154 after=13009` |
| 差值 | — | **−145** | 同上 |
| 90 天边界外行数（以跑测时刻 cutoff `2026-08-30 15:28:21` 计） | **145** | **0** | 跑测后实测；回滚后复测回到 145 |
| 最老行 `created_at` | `2026-08-30 01:12:55` | `≥ 2026-08-30 15:28:21` | 回滚后实测最老行 = `2026-08-30 01:12:55`（即跑测前值） |
| 任务批次数 | — | **2 批**（第 1 批删 145 条，第 2 批删 0 条） | 任务日志 |

**回滚后复核（2026-11-28 17:18，备份恢复后）**：

```
total=13154 | older_than_task_cutoff(2026-08-30 15:28:21)=145 | oldest=2026-08-30 01:12:55 | newest=2026-11-27 23:32:49
```

→ 被删的 145 行**已完整恢复**。

> 📌 **口径更正**：本条初稿曾写「最老行 `created_at` 从 `2026-08-29` 变为 `2026-08-30`」。
> 经备份恢复后实测，**跑测前的最老行就是 `2026-08-30 01:12:55`**，`2026-08-29` 不成立。
> 被删的 145 行全部落在 `2026-08-30 01:12:55 ~ 2026-08-30 15:28:21` 区间内。

### 1.4 MyBatis 日志原文

`backend/src/test/resources/application.yml:13-15` 开启了 `log-impl: StdOutImpl`，故 DELETE 语句进入测试日志：

```
==>  Preparing: DELETE FROM sys_log WHERE (created_at < ?) LIMIT 5000
```

配套任务日志（`.audit-tmp/p3-mvn-test.log`，2026-11-28 15:28:21）：

```
2026-11-28 15:28:21.208 [main] INFO  com.meiyetec.task.LogRetentionTask ... [定时任务] 日志清理 - 开始 2026-11-28T15:28:21.208206400
2026-11-28 15:28:21.858 [main] INFO  com.meiyetec.task.LogRetentionTask ... [定时任务] 日志清理 - 第 1 批删除 145 条
2026-11-28 15:28:21.860 [main] INFO  com.meiyetec.task.LogRetentionTask ... [定时任务] 日志清理 - 完成，保留 90 天，删除 2 批
```

### 1.5 触发条件（为什么此前从未暴露）

| 时间 | 事件 | 证据 |
|---|---|---|
| 2026-11-17 | `LogRetentionTaskSmokeTest` 因 `jwt.secret` 弱值导致 **Spring 上下文加载失败**，4 个用例全部 error，**任务方法从未被调用** | `.audit-tmp/i196-mvn-test.log`：`Tests run: 4, Failures: 0, Errors: 4` （class 级）；该日志中**全文无** `DELETE FROM sys_log`、无 `[定时任务] 日志清理` |
| 2026-11-28 | P3 修复密钥（方案 B）后**首次执行**，上下文成功加载 → 4 个用例真实执行 → **触发真实删除** | `.audit-tmp/p3-mvn-test.log`：`Tests run: 4, Failures: 0, Errors: 0` + 上述 DELETE 原文 |

> 结论：**这条缺陷被 `jwt.secret` 问题"遮蔽"了 11 天**。修好 I197 反而把它释放出来 —— 与 I197 §2.5「此前『编译不过 → 全红』掩盖了 60 个真实失败」同型：**前一个缺陷掩盖了后一个缺陷**。

---

## 2. 影响

| 维度 | 描述 |
|---|---|
| 本地开发库 | **每跑一次 `mvn test` 就删一次 90 天前的日志**，不可撤销（`DELETE` 无软删、无回收站） |
| 进入 CI 后 | 若 CI 连接**共享测试库** → 每次流水线都删；且各开发者的 `mvn test` 会互相踩（并行跑测时互删） |
| 误连生产库 | 若 `SPRING_DATASOURCE_URL` 指向生产 → **不可逆数据销毁**（生产 `log.retention-days` 同样是 90，且任务本身每天凌晨 3 点就会跑，DELETE 语义与生产定时任务完全一致） |
| 隐蔽性 | 测试断言 `after <= before` → **删除 145 行仍然 PASS**，CI 上不会有任何红灯信号 |
| 当前状态 | **方案 A 已实施**（commit `ce7fb73`）：T02-3 加 `@Disabled`，不再执行、不再删数据（实测 `Skipped: 1`，`sys_log` 跑测前后均 12978）。`sys_log` 当前 **12978 行**（13154 → 12978，净减 176，**未回滚**）。方案 B/C 未做，缺陷未根除。 |

---

## 3. 修复方向（不在本 issue 范围，仅建议）

| 方案 | 做法 | 说明 |
|---|---|---|
| **A（推荐）** | 给 `LogRetentionTaskSmokeTest` 加 `@Disabled` 或 `@Tag("destructive")`，并从默认 surefire 执行集排除（`<excludedGroups>` / `groups` 配置） | 成本最低，立刻断掉"每次 mvn test 都删数据" → **✅ 2026-11-28 已实施（commit `ce7fb73`）** |
| **B（推荐，与 A 组合）** | 注入 mock 的 `SysLogMapper`，断言"调用了几次 `delete` 方法、传入了什么 cutoff"，**不真删** | 把 T02-3 从"跑真任务"降级为"验 SQL 构造逻辑"，保住测试价值 |
| C | 若必须测真实清理，指向**专用测试库**并要求显式开关（如 `-Dtest.destructive=true`） | 需要额外维护一套测试库与开关约定，成本最高 |

> **建议 A + B 组合**。
> ⚠️ 修复时**禁止**用"放宽断言 / 删断言"的方式转绿 —— 问题不在断言松紧，在于**测试对生产库执行了破坏性写操作**。

---

## 4. 遗留标注

- **方案 A 已实施**（commit `ce7fb73`）：仅修改测试代码 `LogRetentionTaskSmokeTest.java`（+2 行：`import Disabled` + `@Disabled`），**未修改任何业务代码**。T02-1 / T02-2 / T02-4 保留执行。
- **方案 B / C 未实施**：mock 重写（B）与专用测试库 + 显式开关（C）均待做，故本条状态为 **Partial**，非 Fixed。
- **`sys_log` 当前 12978 行**（13154 → 12978，净减 176），**未回滚**。备份 `meiyetec-before-i197-p3-20261128.sql`（28,135,830 字节）保留在 `.audit-tmp/`。两次删除的 145 + 176 行均为 `created_at < 2026-08-30` 的本地开发环境历史日志。
- 未连接生产库、未打开浏览器。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验证页面及其数据接口。
- `audit/issues/issue-register.md` 的 I213 行状态已同步为 Partial（见 register 主表）。

---

## 7. 修复记录（2026-11-28）

### 7.1 方案 A 实施

| 项 | 内容 |
|---|---|
| commit | `ce7fb73` |
| 文件 | `backend/src/test/java/com/meiyetec/task/LogRetentionTaskSmokeTest.java` |
| 改动 | 1 个 import + 1 个 `@Disabled` 注解（2 行） |
| 作用范围 | 仅 T02-3；T02-1 / T02-2 / T02-4 为只读用例，保留执行 |
| `@Disabled` 理由原文 | 含「每次跑测删除 90 天前日志（2026-11-28 两次实测删 145 + 176 行）」「断言仅 after <= before，删多少都 PASS」 |

### 7.2 验证

| 项 | 期望 | 实测 | 判定 |
|---|---|---|---|
| `mvn test -Dtest=LogRetentionTaskSmokeTest` | `Skipped: 1` | `Tests run: 4, Failures: 0, Errors: 0, Skipped: 1` | ✅ |
| `sys_log` 行数（跑测前） | 12978 | 12978 | — |
| `sys_log` 行数（跑测后） | 不变 | 12978 | ✅ 未删 |
| BUILD | SUCCESS | SUCCESS | ✅ |

---

## 8. 数据影响与过程记录

### 8.1 `sys_log` 累计影响

| 时间 | 事件 | 删除行数 | `sys_log` 行数 |
|---|---|---|---|
| 2026-11-28 15:28（P3 跑测） | 本测试首次执行（T02-3 未被禁用） | −145 | 13154 → 13009 |
| 2026-11-28 15:35 | 用备份回滚 | +145 | 13009 → 13154 |
| 2026-11-28 22:53（I213 修复前误跑） | **`@Disabled` 未成功加上**（脚本缺陷），T02-3 再次执行 | −176 | 13154 → 12978 |
| 2026-11-28 23:0x | 方案 A 落地，`@Disabled` 生效 | 0 | 12978（稳定） |

**当前状态**：`sys_log` = **12978**，累计净减 **176** 行（均为 `created_at < 2026-08-30` 的本地开发环境历史日志）。**未回滚**（备份 `meiyetec-before-i197-p3-20261128.sql` 保留）。

### 8.2 过程记录：一次失败的脚本操作

2026-11-28 22:49 首次尝试加 `@Disabled` 时，用多行 here-string 作锚点，因文件为 LF、PowerShell here-string 为 CRLF，`Contains` 判断返回 `FAIL`。**但脚本未在该分支终止**（缺 `return`），后续 `Replace` 与写盘仍执行，导致：
- `import Disabled` **加上了**
- `@Disabled` 注解 **没加上**

随后 `mvn test` 使 T02-3 再次真实执行，删除 176 行。

**修正**：改用**单行锚点**（`@DisplayName("T02-3: ...")` 一行）重新匹配，成功加上注解并验证（§7.2）。

**教训**：修改多行代码时不用多行字符串作 `Contains` 锚点（换行符差异会导致静默失配），改用单行锚点；且 `if` 失败分支必须 `return` 或 `throw`，不能只打印。
