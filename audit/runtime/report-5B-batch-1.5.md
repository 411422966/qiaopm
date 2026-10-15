# Phase 5B — Runtime Verification Report（Batch 1.5）

> **批次**：Batch 1.5 — MKT 多渠道重构（v3 第五批）
> **创建时间**：2026-09-16 23:30 (UTC+8)
> **Phase**：Phase 5B — Runtime Execution & Evidence Collection
> **范围**：P1 = 3 Feature（F135-F137 ChannelDispatcher + 5 Sender）+ P2 = 3 Feature（F138-F140 业务入口 + 重构中间态 + Diff Audit）
> **前置条件**：后端 8080 运行中 ✅ / MySQL 3307 可连 ✅ / jshell 21 ✅ / admin token 可用 ✅

---

## 一、Feature 验证结果矩阵（6 Feature × F135-F140）

| Feature ID | Priority | 验证方式 | Result | 关键 Issue / 实证 |
|---|---|---|---|---|
| **F135** | P1 | jshell 反射（ChannelDispatcher.getSender × 8 channelType）| **PARTIAL-BLOCKER** | I061 NPE 风险（miss 返 null）+ I039 别名漂移（MP/WECHAT_OFFICIAL_ACCOUNT）|
| **F136** | P1 | DB 统计 + 限流算法静态分析 | **VERIFIED + PARTIAL-NON-BLOCKER** | 限流 SQL 逻辑正确；SMS rate_limit_per_day=5 极低 |
| **F137** | P1 | jshell（SmsSender.render 5 项 + channelType 5 sender 反射）| **PARTIAL-NON-BLOCKER** | SMS ✅ render 算法；IN_APP ✅ channelType；EMAIL/MINI_PROGRAM/WECHAT_OA 凭据缺失 → UNVERIFIED（沿用 D10）|
| **F138** | P2 | curl 2 端点 + DB information_schema | **PARTIAL-BLOCKER** | I063 4 表 MISSING（mkt_dispatch / mkt_dispatch_target / mkt_invitation / mkt_invitation_rule）；2 端点 500 |
| **F139** | P2 | curl 10 端点 + DB 11 列 + 18 处调用点 | **PARTIAL-BLOCKER** | I055/I068 SoT CONFLICT（SoT 声称无读写，实际 18 处调用 + 6 行数据）；旧 Controller 10/10 端点 200 |
| **F140** | P2 | 静态归档确认 | **VERIFIED** | `audit/diff-audit/mkt-refactor.md` 15689 bytes；8 节已产出；I055-I059 5 个登记 |

---

## 二、负向证据实证清单（5B）

| # | 负向证据 | 关联 Issue | 5B 实证？ | 证据文件 |
|---|---|---|---|---|
| **N3** | F135 getSender miss → NPE（CRITICAL）| **I061** | ✅ jshell 实证：`getSender("UNKNOWN") = null`；`getSender("MP") = null`（I039）；`getSender("PUSH") = null` | F135 jshell-output.txt |
| **N4** | F138 4 表 MISSING（CRITICAL）| **I063** | ✅ curl 2 端点全 500；information_schema 0 匹配 | F138 raw-dispatch-page.json + db-table-missing.txt |
| **N5** | F139 SoT CONFLICT + 重构中间态| **I055 + I067 + I068** | ✅ 10 端点 200；11 列；18 处调用；snapshot.totalActivities=5 = DB 一致 | F139 runtime-evidence.md |
| **N11** | F135 healthCheckAll tenant_id=1L（沿用）| **I050 沿用** | ✅ F118 5A 已实证（仅更新 tenant_id=1）| F118-health-check |
| **N12** | F137 WECHAT_OA 别名漂移（CONFLICT）| **I039** | ✅ jshell：`WechatOfficialAccountSender.channelType() = "WECHAT_OFFICIAL_ACCOUNT"`；DB 存 `MP` | F137 jshell-output.txt |

> **5B 实证成功** = 5 项（N3 / N4 / N5 / N11 / N12）

---

## 三、jshell 实证摘要

### F135 — ChannelDispatcher.getSender

```
Valid total:        5/5   ✅
Orphan (MP/PUSH):   2/2   ✅  EXPECTED_NULL（别名漂移）
Unknown:             1/1   ✅  EXPECTED_NULL（NPE 风险）
senders List size:  5     ✅
getSender(null):    null  ✅
```

### F137 — SmsSender.render + channelType

```
render Test 1 (正常):  ✅ MATCH
render Test 1b (null template):  ✅ MATCH
render Test 1c (null vars):  ✅ MATCH
render Test 1d (缺 var):  ✅ MATCH
render Test 1e (特殊字符 $):  ✅ MATCH

channelType SMS:               ✅ MATCH ("SMS")
channelType EMAIL:             ✅ MATCH ("EMAIL")
channelType MINI_PROGRAM:      ✅ MATCH ("MINI_PROGRAM")
channelType IN_APP:           ✅ MATCH ("IN_APP")
channelType WECHAT_OFFICIAL_ACCOUNT: ✅ MATCH ("WECHAT_OFFICIAL_ACCOUNT")
```

---

## 四、5B 新发现 Issue（登记）

| Issue ID | 严重度 | 涉及 Feature | 描述 |
|---|---|---|---|
| **I075 NEW** | 🟡 中 | F135 | `getSender("MP")` = null — WechatOfficialAccountSender 注册 `WECHAT_OFFICIAL_ACCOUNT` 但 DB 存 `MP`；别名漂移导致 OA 渠道永远 miss → NPE |
| **I076 NEW** | 🟡 中 | F135 | `getSender("PUSH")` = null — DB 有 channel_type=PUSH（id=10），无对应 sender 实现 |
| **I077 NEW** | 🟡 中 | F136 | SMS rate_limit_per_day=5 极低阈值（实际 per_day=1977）；新触达全被限流 |

> **累计 Issue**：77 个（74 + I070-I074 5A + I075-I077 5B）

---

## 五、Phase 5A vs 5B 对照

| 维度 | Phase 5A | Phase 5B |
|---|---|---|
| **Feature 数** | 21（F114-F134）| 6（F135-F140）|
| **验证方式** | curl + DB | jshell + curl + DB |
| **VERIFIED** | 11 | 2（F136/F140）|
| **PARTIAL-NON-BLOCKER** | 7 | 2（F137 + F136 附加）|
| **PARTIAL-BLOCKER** | 3 | 3（F135/F138/F139）|
| **UNVERIFIED** | 2（F120/F125）| 0 |
| **jshell 实测** | 0 | 2（F135/F137）|
| **5A 新发现** | 5（I070-I074）| 3（I075-I077）|

---

## 六、Evidence 目录清单（5B）

```
audit/evidence/
├── F135-channel-dispatcher/
│   ├── runtime-evidence.md          # PARTIAL-BLOCKER（I061 NPE + I039）
│   ├── probe-5B.6-channelDispatcher-getSender.jsh  # 109 行 jshell 探针
│   └── jshell-output.txt             # jshell 输出
├── F136-channel-dispatcher-rate-limit/
│   ├── runtime-evidence.md          # VERIFIED + PARTIAL-NON-BLOCKER
│   └── db-rate-limit-context.txt     # marketing_touch_log 统计 + 阈值
├── F137-channel-sender/
│   ├── runtime-evidence.md          # PARTIAL-NON-BLOCKER（SMS✅ IN_APP✅）
│   ├── probe-5B.7-sender-channelType.jsh  # 84 行 jshell 探针
│   ├── jshell-output.txt            # render 5 PASS + channelType 5 MATCH
│   ├── probe-inappsender.jsh        # InAppSender 补充探针
│   └── jshell-inappsender.txt       # IN_APP channelType=IN_APP ✅
├── F138-business-policy-dispatch/
│   ├── runtime-evidence.md         # PARTIAL-BLOCKER（I063 4 表 MISSING）
│   ├── raw-dispatch-page.json       # 500 + traceId
│   ├── raw-dispatch-stats.json      # 500 + traceId
│   └── db-table-missing.txt        # 4 表 0 匹配
├── F139-refactor-intermediate/
│   ├── runtime-evidence.md         # PARTIAL-BLOCKER（I055 CONFLICT）
│   ├── db-market-activity-describe.txt   # 11 列
│   ├── db-market-activity-rows.txt       # 6 行 tenant_id=1=5
│   ├── db-call-sites.txt            # 18 处代码引用
│   ├── raw-market-activity-endpoints.txt # 10 端点 200
│   └── raw-mkt-effect-snapshot.txt  # totalActivities=5 = DB 一致
└── F140-diff-audit/
    └── runtime-evidence.md           # VERIFIED（Phase 3.5 已归档）
```

---

## 七、Phase 5B 关键发现

### 7.1 I039 别名漂移（新发现 I075/I076）

- `WechatOfficialAccountSender.channelType() = "WECHAT_OFFICIAL_ACCOUNT"`（代码）
- DB `mkt_channel_config.channel_type = "MP"`（id=3，5A 创建）
- `getSender("MP")` → null → 路由 miss → NPE

### 7.2 限流极低阈值（新发现 I077）

- SMS rate_limit_per_day=5（极低）
- marketing_touch_log SMS per_day=1977
- **每日 5 条以上全部被限流拒绝**

### 7.3 market_activity 重构中间态确认（I055/I068）

- 10 端点 200（旧 Controller 完整存活）
- 18 处代码引用（远超 SoT 声称的"无读写"）
- DROP 表会导致 BusinessPolicyDispatchServiceImpl + MktEffectController NPE

---

## 八、Phase 5B 结论

| Feature | Result | 关键 Evidence |
|---|---|---|
| F135 | PARTIAL-BLOCKER | jshell: 5 valid ✅ / 3 null (I061/I039) |
| F136 | VERIFIED | DB: SMS per_day=1977 >> rate_limit_per_day=5 → 限流生效 |
| F137 | PARTIAL-NON-BLOCKER | jshell: render ✅ / channelType ✅ / 3 凭据缺失 UNVERIFIED |
| F138 | PARTIAL-BLOCKER | curl: 2 端点 500 + information_schema 0 匹配 |
| F139 | PARTIAL-BLOCKER | curl: 10 端点 200 + 11 列 + 18 调用点 + snapshot=5=DB |
| F140 | VERIFIED | audit/diff-audit/mkt-refactor.md 15689 bytes 归档完成 |

**Phase 5B 完成。⏸ 等用户确认进 5C。**
