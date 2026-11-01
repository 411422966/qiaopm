# F140 收尾 — 最后 2 个真实 SQL 缺陷修复与复测

> 修复时间：2026-11-01 22:50 ~ 22:57 (UTC+8)
> 基线 commit：`20931cf`（本轮修复在其之后）
> 环境：容器 `meiyetec-api`（重建镜像 `meiyetec/api:dev`，Up healthy，`0.0.0.0:8080->8080/tcp`）
> 数据库：`127.0.0.1:3307/meiyetec`（MySQL 8.0.46）
> 认证：POST `/auth/login`（admin / 123456）取 Bearer token

---

## 1. 缺陷与修改对照

| # | 端点 | 根因 | 修改位置 | 修改内容 |
|---|---|---|---|---|
| A | `GET /biz/booking/member-credit?memberId=1` | **列名漂移** —— `member_credit_log` 实际列为 `lock_until`，SQL 写 `locked_until` | `backend/src/main/java/com/meiyetec/service/BookingResourceService.java:745`（SELECT）、`:715`（INSERT） | `locked_until` → `lock_until`（两处同表同列名） |
| B | `GET /open/api/v2/quota/status?appId=1` | **`window` 是 MySQL 8.0 保留字**，列名未加反引号 → `ERROR 1064` | `backend/src/main/java/com/meiyetec/service/OpenPlatformEnhanceService.java:339`（INSERT 列清单）、`:346`（SELECT WHERE） | `` `window` `` 加反引号转义（两处） |

### 1.1 未修改项（有意保留）

| 位置 | 内容 | 原因 |
|---|---|---|
| `BookingResourceService.java:701`、`:704` | `member_credit.locked_until` | `member_credit` 表**确实**存在 `locked_until` 列，命名正确，无需改动 |
| `BookingWaitlistScoreService.java:462` | `c.locked_until` | 同上，`c` 是 `member_credit` 别名 |
| `entity/MemberCreditLog.java:22` | `private LocalDateTime lockedUntil` | MyBatis-Plus 驼峰映射 → `locked_until`，与实际列 `lock_until` 不符，但该 Mapper（`MemberCreditLogMapper`）当前**无任何调用方**（全仓仅 `BookingResourceService:46` 注入、零调用）。属**独立遗留问题**，未在本轮改动范围内，留待后续处理。 |

---

## 2. 复测结果

复测脚本：`.audit-tmp/i118-inv/verify-2sql-fix.js`（非正式工具，不进正式守门序列、不接 CI）
证据：`fix-2sql-verify.json`

| 端点 | HTTP | code | message | traceId |
|---|---|---|---|---|
| `/biz/booking/member-credit?memberId=1` | 200 | 200 | 操作成功 | `acff72e747594afea4d5ffd695fb39b5` |
| `/open/api/v2/quota/status?appId=1` | 200 | 200 | 操作成功 | `6e83fa9fe1dd4126962ad82770ce9848` |
| `/biz/booking/member-credit?memberId=999999` | 200 | 200 | 操作成功 | `71d0a82a21ff476b88d446ff8b0049bc` |

**3/3 全部 HTTP 200 + code=200**，修复前 2 个端点为 `code=500`（`BadSqlGrammarException`）。

---

## 3. 落库核对

修复 B 后 `open_api_quota` 实际写入（说明 INSERT 分支真实执行，非假成功）：

| id | tenant_id | app_id | quota_limit | used_count | window_start | create_time |
|---|---|---|---|---|---|---|
| 9 | 1 | 1 | 60 | 1 | 2026-11-01 22:57:00 | 2026-11-01 22:57:03 |
| 4 | 1 | 1 | 500000 | 3 | 2026-11-01 00:00:00 | 2026-11-01 22:56:25 |
| 3 | 1 | 1 | 20000 | 3 | 2026-11-01 00:00:00 | 2026-11-01 22:56:24 |
| 2 | 1 | 1 | 1000 | 3 | 2026-11-01 22:00:00 | 2026-11-01 22:56:24 |
| 1 | 1 | 1 | 60 | 2 | 2026-11-01 22:56:00 | 2026-11-01 22:56:23 |

4 个窗口（MINUTE / HOUR / DAY / MONTH）各落一行，`used_count` 随调用递增 —— 配额扣减链路真实可用。

`member_credit_log` 当前 0 行（库中无 NO_SHOW 记录），修复 A 的 SELECT 分支已实测返回 `logs: []` 且无异常；INSERT 分支（`:715`）未被运行覆盖，属**未确认**。

---

## 4. 结论

| 项 | 判定 |
|---|---|
| 缺陷 A（`lock_until` 列名漂移） | **VERIFIED**（SELECT 分支有运行证据；INSERT 分支未覆盖，标未确认） |
| 缺陷 B（`window` 保留字转义） | **VERIFIED**（SELECT + INSERT 双分支均有运行证据 + 落库核对） |
| 865 端点 `code=500` 剩余 | 由 74 → **72**（其中 67 个 C1 假阳性 + 5 个 C2 假阳性），真实 SQL 缺陷归零 |
| F140 整体 | **PARTIAL-NON-BLOCKER** —— 剩余 72 个 500 全为假阳性（缺必填参数 / 记录不存在），需带参调用或换真实 id 才不报错 |

---

## 5. 证据文件

| 文件 | 内容 |
|---|---|
| `fix-2sql-verify.json` | 2 个缺陷端点 + 1 个边界端点的复测结果（http/code/message/traceId/body） |
| `fix-2sql-report.md` | 本报告 |
| `victory-report.md` | 上一轮 865 端点全量扫描报告（本报告的前序依据） |
| `victory-sweep-865.json` | 865 端点全量扫描原始结果 |
| `victory-500-classified.json` | 74 个 500 的根因归类 |
| `victory-500-retest.json` | 74 个 500 的补参 / 换真实 id 复测 |
| `victory-summary.json` | 结构化汇总 |
| `victory-sweep-865.js` | 扫描脚本复刻件（文件头标注「非正式工具」） |
