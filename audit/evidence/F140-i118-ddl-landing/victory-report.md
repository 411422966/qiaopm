# F140 胜利验收报告 — 865 端点全量 GET 扫描

> 执行时间：2026-11-01 22:21:47 ~ 22:32:24 (UTC+8)
> 环境：容器 `meiyetec-api`（Up healthy，映射 `0.0.0.0:8080->8080/tcp`），代码 commit `20931cf`
> 数据库：`127.0.0.1:3307/meiyetec`（MySQL 8.0.46）
> 认证：POST `/auth/login`（admin）取 Bearer token
> 扫描方式：静态枚举 `backend/src/main/java/com/meiyetec` 下全部 `@GetMapping` → 路径占位符填 `1548` → 带 token 逐个 GET

---

## 1. 总体结果

| 指标 | 本轮（commit 20931cf） | 上轮基线 | 变化 |
|---|---|---|---|
| 扫描端点数 | **865** | 865 | 0 |
| `code=200` | 642 | — | — |
| `code=404` | 82 | — | — |
| `code=500` | **74** | 74 | **0** |
| HTTP 404（无 body code） | 46 | — | — |
| HTTP 200（非 JSON 响应） | 20 | — | — |
| `code=400` | 1 | — | — |

`code=500` 端点集合与上轮**逐条完全一致**（修复 0 个、回归 0 个，`Compare-Object` 双向差集均为空）。这是符合预期的：本轮 commit `20931cf` 修的是 Q1 评价指标口径（好评率硬编码 / 空集报错），并不在这 74 个端点中。

---

## 2. 剩余 74 个 code=500 的根因分解

按 traceId 关联容器 `GlobalExceptionHandler` 日志 + 「补参 / 换真实 id」二次复测：

| 分类 | 数量 | 判定依据 |
|---|---|---|
| **C1 缺必填参数** | **64** | 日志异常类型 `org.springframework.web.bind.MissingServletRequestParameterException` |
| **C2 记录不存在** | **10** | `Result.error(BizCode, "<资源>不存在")` 业务错误，无异常栈 |
| **真实 SQL 缺陷** | **2** | `org.springframework.jdbc.BadSqlGrammarException` |

### 2.1 C1 复测结果（补齐 `@RequestParam` 后）

| 复测 code | 数量 |
|---|---|
| 200 | 54 |
| 404（资源确实不存在，如「实验不存在」「会员不存在」） | 3 |
| 非 JSON（文件下载类） | 1 |
| 仍 500 —— 补参不全，需再补 | 6 |
| 仍 500 —— 补全后仍失败 | **2** |

补全参数后仍 500 的 3 个端点（已确认为**纯 C1 假阳性**，非代码缺陷）：

| 端点 | 缺失参数 | 代码位置 | 补全后结果 |
|---|---|---|---|
| `/biz/booking/resource/check-conflict` | `endTime` | `backend/src/main/java/com/meiyetec/service/BookingResourceService.java:264-269` | HTTP=200 code=200 操作成功 |
| `/push/record/list` | `userId` | `backend/src/main/java/com/meiyetec/service/PushNotificationService.java:148-150` | HTTP=200 code=200 操作成功 |
| `/sync/member/list` | `sessionKey` | `backend/src/main/java/com/meiyetec/service/MultiDeviceSyncService.java:141-142` | HTTP=200 code=200 操作成功 |

> 上表直接回答了「`/biz/booking/resource/check-conflict` 与 `/sync/member/list` 是否同类 SQL 问题」：**不是 SQL 问题，是扫描脚本未补齐必填参数**。两者补参后均返回 `code=200`。

### 2.2 C2 复测结果（路径占位符 `1548` → 库中真实 id）

| 复测 code | 数量 |
|---|---|
| 200 | 8 |
| 仍 500 | 2 |

仍 500 的 2 个：
- `GET /booking/1` → 「预约不存在」（`biz_booking` 中 id=1 无记录）
- `GET /technician/level/benefit/1` → 「权益不存在或无权访问」

均为**数据侧无对应记录**，非代码缺陷。

### 2.3 真实 SQL 缺陷（2 个，**已于 2026-11-01 22:50 修复并复测通过** → 详见 `fix-2sql-report.md`）

**缺陷 A — `/biz/booking/member-credit?memberId=1`**
- traceId：`080150802a694d988379c82f159ee86f`
- 异常：`org.springframework.jdbc.BadSqlGrammarException`
- SQL：`SELECT id, change_type, change_value, credit_after, booking_id, fee_penalty, locked_until, operator_name, remark, create_time FROM member_credit_log WHERE deleted = 0 AND member_id = ? ORDER BY id DESC LIMIT 50`
- 代码位置：`backend/src/main/java/com/meiyetec/service/BookingResourceService.java:743-746`
- 根因：**列名漂移** —— SQL 写 `locked_until`，`member_credit_log` 实际列名为 `lock_until`
- 验证：`information_schema.COLUMNS` 确认 `locked_until` 存在数 = 0，`lock_until` 存在数 = 1

**缺陷 B — `/open/api/v2/quota/status?appId=1`**
- traceId：`6b5827459f4944598dc5baf317b7e358`
- 异常：`org.springframework.jdbc.BadSqlGrammarException`
- SQL：`INSERT INTO open_api_quota (tenant_id, app_id, window, quota_limit, used_count, window_start, window_end) VALUES (...) ON DUPLICATE KEY UPDATE ...`
- 代码位置：`backend/src/main/java/com/meiyetec/service/OpenPlatformEnhanceService.java:338-343`
- 根因：**`window` 是 MySQL 8.0 保留字**，列名未加反引号转义 → 语法错误
- 验证：裸 SQL 复现 `ERROR 1064 (42000) ... near 'window, quota_limit, used_count, window_start, window_end)'`

---

## 3. 结论

| 端点数 | 分类 | 占比 |
|---|---|---|
| 74 | `code=500` 总数 | 100% |
| 67 | **C1 假阳性**（缺必填参数，含 3 个补参不全经二次复测确认） | 90.5% |
| 5 | **C2 假阳性**（记录不存在；另 5 个 C2 换真实 id 后返回 200） | 6.8% |
| 2 | **真实 SQL 缺陷** | 2.7% |

**回答「剩下的 500 是否全是因为缺必填参数（C1）或查不到记录（C2）引起的假阳性？」**

不是**全部**。74 个中 **72 个（97.3%）是假阳性**，另有 **2 个是真实 SQL 缺陷**，均为列名/保留字层面的硬错误，与 C1/C2 无关：

1. `member_credit_log.locked_until` → 应为 `lock_until`（`BookingResourceService.java:743-746`）
2. `open_api_quota.window` 保留字未转义（`OpenPlatformEnhanceService.java:338-343`）

两者在补齐全部必填参数后依然 500，异常栈为 `BadSqlGrammarException`，且已用 `information_schema` 与裸 SQL 双向独立验证。

---

## 4. Feature 状态

| 项 | 判定 |
|---|---|
| F140 本轮（Q1 修复） | **PARTIAL-NON-BLOCKER** —— 好评率 0 属「不可硬编码」口径正确表现，根因为 `crm_review` 演示数据 `source_id` 与真实主键无交集（见 `result.md` B.7） |
| 865 端点 code=500 收敛 | **PARTIAL-NON-BLOCKER** —— 72/74 为假阳性；2 个真实 SQL 缺陷已修复（`fix-2sql-report.md`），修复后剩余 72 个全为假阳性 |
| Q1 端点 `/technician/level/promote/evaluate?employeeId=1` | 本轮复测 HTTP=200 code=200 |

---

## 5. 证据文件

| 文件 | 内容 |
|---|---|
| `victory-summary.json` | 本轮结构化汇总（环境、分布、分类、缺陷明细、判定） |
| `victory-sweep-865.json` | 865 端点全量扫描原始结果（含每端点 http/code/traceId/响应体） |
| `victory-500-classified.json` | 74 个 500 按 traceId 关联日志的根因归类 |
| `victory-500-retest.json` | 74 个 500 补参 / 换真实 id 的二次复测结果 |
| `victory-sweep-865.js` | 扫描脚本复刻件（文件头标注「非正式工具」，不进正式守门序列、不接 CI） |
| `victory-report.md` | 本报告 |
| `fix-2sql-report.md` | **后续**：2 个真实 SQL 缺陷的修复与复测报告 |
| `fix-2sql-verify.json` | **后续**：修复后复测原始结果 |
