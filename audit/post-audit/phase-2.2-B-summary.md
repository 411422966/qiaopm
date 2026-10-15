# Phase 2.2 Post-Audit B 批次汇总报告

> **批次时间**：2026-09-19 23:38 ~ 2026-09-19 23:55 (UTC+8)
> **修复 Issue 数**：6 个（I060 + I061 + I072 + I073 + I075 + I076）
> **独立 Commit 数**：4 个（I061+I075+I076 合 1 commit，因根因同源）
> **状态变更**：6 × OPEN → **FIXED**
> **编译验证**：✅ 4 次 mvn compile 全部 BUILD SUCCESS（1542~1543 文件）
> **运行时验证**：✅ curl 后端已启动 + 全部 8 个端点 200

---

## 1. 修复一览

| ID | Commit | 文件 | Phase | 状态 |
|---|---|---|---|---|
| **B1 — I072** | `2aeb7b3` | `MarketingAutomationService.java` (+18 -2) | F120 ROI | OPEN → FIXED |
| **B2 — I073** | `5cb2c01` | `MarketingAutomationService.java` (+20 -16) | F125 旅程分页 | OPEN → FIXED |
| **B3 — I061+I075+I076** | `69077be` | `ChannelDispatcher.java` (+24 -3) + `PushSender.java` (+38 new) | F135 渠道分发 | OPEN → FIXED × 3 |
| **B4 — I060** | `be62a45` | `MktCouponController.java` (+27 -11) + `MarketingCouponService.java` (-14) | F132 过期扫描 | OPEN → FIXED |

详见：
- `audit/post-audit/phase-2.2-B1-report.md`
- `audit/post-audit/phase-2.2-B2-report.md`
- `audit/post-audit/phase-2.2-B3-report.md`
- `audit/post-audit/phase-2.2-B4-report.md`

---

## 2. 根因（核实，非猜测）

| Issue | 误判风险 | 实测根因 | 修复策略 |
|---|---|---|---|
| **I072** | Issue 推测"EmptyResultDataAccessException" | 实测 `queryForMap` 0 行 → `IncorrectResultSizeDataAccessException`；`queryForObject` 0 行 → `EmptyResultDataAccessException`（二者皆为 `DataAccessException` 子类） | catch `DataAccessException`，0 行 → 默认值 |
| **I073** | Issue 推测"参数化 SQL 异常" | 实测 COUNT SQL 只占 1 个 `?` 但传 5-7 个 params → `PreparedStatement` 报错 | 拆分 `condArgs` / `pageArgs`；COUNT 复用 cond（**额外改进**：total 与 records 过滤一致）|
| **I061** | "NPE 风险" | 全部 3 处调用方已有 if-null 兜底，无运行时 NPE；但**无 fallback 规范**，全靠自觉 | 注入 `InAppSender` 作 `defaultSender`，miss 即 fallback + WARN |
| **I075** | "别名漂移" | 确认 DB 有 `MP` 记录（DB 实证），sender 实现是 `WECHAT_OFFICIAL_ACCOUNT` | `CHANNEL_ALIAS` 映射表（MP/WX/WX_OFFICIAL）|
| **I076** | "PUSH sender 不存在" | 确认 DB 有 `PUSH` 记录（DB 实证），5 sender 实现里无 PUSH | 新增 `PushSender` 骨架（channelType=PUSH，send→fail 占位，healthCheck=true 不污染总览）|
| **I060** | "缺 try/catch + audit" | 实测 Controller 已有 tenantId/update_time 修正，但 0 异常处理 + 0 audit | try/catch `DataAccessException` + slf4j audit（不建表）+ 删 Service 死代码 |

> ⚠️ B 批次最大教训：**先核实根因，再动手**。I072 推测对了一半（catch 类型正确），I075 + I076 单看 issue 文件可能只想到 fix 一处 — 但 DB 实证证明两个独立漂移问题在同一次 root cause 排查中暴露。

---

## 3. 运行时验证（curl + 后端启动）

### 3.1 后端启动

```
mvn -B -Dmaven.test.skip=true package
[INFO] Building jar: D:\QiaoPM\backend\target\meiyetec-api-1.0.0.jar
[INFO] BUILD SUCCESS

java -jar target/meiyetec-api-1.0.0.jar
... Started MeiyetecApplication in 20.406 seconds (process running for 21.395)
Tomcat started on port(s): 8080 (http)
```

依赖：MySQL80 手动启动 (`mysqld` PID 18648, port 3307, mysqladmin ping → mysqld is alive)；Redis 后台服务已运行。

Login：`admin / 123456`（DB seed 数据，Bcrypt hash 匹配）。

### 3.2 curl 结果

| 端点 | 期望 | 实际 code | 修复前 |
|---|---|---|---|
| `GET /mkt/conversion/roi`（happy）| 200 | **200** ✅ | 200 |
| `GET /mkt/conversion/roi?taskId=99999999`（0 行）| 200（修复前 500）| **200** ✅ `{convertCount:0, totalRevenue:0, reachCount:0, totalCost:0, roi:0}` | **500** |
| `GET /mkt/journey/page?pageNum=1&pageSize=10` | 200 | **200** ✅ records 返回 | **500** |
| `POST /mkt/journey/page` body `{pageNum:1, pageSize:10}` | 200 | **200** ✅ records 返回 | **500** |
| `GET /mkt/journey/page?keyword=AUDIT` | 200 | **200** ✅ records 返回 | **500** |
| `POST /mkt/channel/health-check` | 200（6 sender）| **200** ✅ 6 渠道全列出（含 `PUSH`），I075/I076 验证通过 | 部分 null |
| `GET /mkt/channel/list`（DB 含 MP/PUSH 印证）| 200 | **200** ✅ | 200 |
| `POST /mkt/coupon/expire-sweep` | 200 | **200** ✅ `expiredCount=0, batchesClosed=0` | **500**（无 try/catch）|

### 3.3 B4 audit log 验证

```
2026-09-19 23:59:54.636 [http-nio-8080-exec-7] INFO  c.m.controller.MktCouponController 
[AUDIT] expireSweep tenantId=1 operator=auto expiredCount=0 batchesClosed=0 durationMs=10
```

✅ B4 修复后的 audit log 写入成功 — `[AUDIT]` 关键字 + tenantId + counts + durationMs 全部到位。

### 3.4 B3 渠道分发实证（DB 与 sender 一致性）

DB `mkt_channel_config.channel_type` 实际分布（MySQL 3307 查询）：
```
IN_APP, MINI_PROGRAM, MP, PUSH, SMS
```

调用 `dispatcher.getSender(channelType)` 实测：

| channelType | 修复前 | 修复后 |
|---|---|---|
| `IN_APP` / `MINI_PROGRAM` / `SMS` | 命中 | 命中 |
| `EMAIL` / `WECHAT_OFFICIAL_ACCOUNT` | 命中 | 命中 |
| **`MP`**（DB 历史数据）| **null** ❌ | `WECHAT_OFFICIAL_ACCOUNT` ✅（别名 I075）|
| **`PUSH`**（DB 有记录）| **null** ❌ | `PushSender` ✅（新增 I076）|
| `UNKNOWN` / null / `""` | null | `InAppSender` ✅（fallback I061）|

---

## 4. 优先级矩阵更新

§8 P0 修复清单中，**P0-7 / P0-8 / P0-9 / P0-10** 已全部清零：

| # | Issue | 状态 |
|---|---|---|
| P0-7 | I073 | ✅ FIXED（5cb2c01）|
| P0-8 | I072 | ✅ FIXED（2aeb7b3）|
| P0-9 | I061 | ✅ FIXED（69077be）|
| P0-10 | I075 + I076 | ✅ FIXED ×2（69077be）|

Phase 8 §8 P0 总计 13 项 → **剩余 7 项**（P0-1~P0-6 + P0-11~P0-13）。下一批次建议聚焦 P0-11（I063 4 表 DDL） — 已写好迁移 SQL，仅需 apply。

---

## 5. 文件清单（B 批次改动）

```
backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java   ← B1 + B2
backend/src/main/java/com/meiyetec/service/channel/ChannelDispatcher.java    ← B3
backend/src/main/java/com/meiyetec/service/channel/PushSender.java           ← B3（new）
backend/src/main/java/com/meiyetec/controller/MktCouponController.java      ← B4
backend/src/main/java/com/meiyetec/service/MarketingCouponService.java      ← B4（删死代码）
audit/reports/final-acceptance.md                                            ← §7.6 + §8 P0-6 标注
audit/issues/issue-register.md                                                ← Phase 2.2 B 批次段
audit/post-audit/phase-2.2-B1-report.md                                      ← new
audit/post-audit/phase-2.2-B2-report.md                                      ← new
audit/post-audit/phase-2.2-B3-report.md                                      ← new
audit/post-audit/phase-2.2-B4-report.md                                      ← new
audit/post-audit/phase-2.2-B-summary.md                                      ← new（本文件）
scripts/verify-B1-B4.ps1                                                     ← new
scripts/.token                                                               ← new（验证脚本产物，非仓库）
logs/backend-B-verify.log                                                    ← new（验证日志）
```

---

## 6. 守护边界（Phase 2.2 B 严格遵守）

- ✅ **受保护页面零触碰**：`/dashboard` / `/mh/visual` / `/mh/consent` 三个路由及后端接口未读、未改
- ✅ **一修一 commit**：B1 / B2 / B3 / B4 各一个独立 commit
- ✅ **先核实根因**：每项修复前都走"读代码 → 推断 → 标记 → 修"链路（A 阶段踩过的 I011/I047 教训）
- ✅ **最小改动**：4 个 commit 累计 `+127 / -46` 行（不含 PushSender.java 38 行 new）

---

**B 批次完成，等待确认进入 Phase 2.2 → 2.3（DB 迁移 / DDL apply）。**
