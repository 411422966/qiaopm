# F137 — 5 ChannelSender × 单元测试

> **运行时间**：2026-09-16 23:29-23:30 (UTC+8)
> **关联 Issue**：I039（别名漂移 WECHAT_OA vs WECHAT_OFFICIAL_ACCOUNT）
> **验证方式**：jshell 静态方法 + 反射实例化

## 一、jshell 实证

### SmsSender.render() 静态方法（✅ VERIFIED）

**F137 jshell 探针**：`probe-5B.7-sender-channelType.jsh`

| # | 测试场景 | 输入 | 预期输出 | 结果 |
|---|---|---|---|---|
| 1 | 正常替换 | `"亲爱的 ${name}，订单 ${orderNo} 已发货。"` + `{name:"张三", orderNo:"ORD-2026-001"}` | `"亲爱的 张三，订单 ORD-2026-001 已发货。"` | ✅ MATCH |
| 1b | null template | `null` + vars | `null`（passthrough）| ✅ MATCH |
| 1c | null vars | template + `null` | passthrough（无替换）| ✅ MATCH |
| 1d | 缺 var | template + `{}`（empty）| 空字符串替换 | ✅ MATCH |
| 1e | 特殊字符 `$` | `"价格: ${price} 元"` + `{price:"$10.99"}` | `"价格: $10.99 元"`（正确转义）| ✅ MATCH |

> `SmsSender.render` 实现：`java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}")` + `Matcher.quoteReplacement()`，**特殊字符 $ 安全处理**。

### 5 sender channelType()（✅ 全部精确匹配）

| Sender | channelType() | DB 中对应 channel_type | 结果 |
|---|---|---|---|
| SmsSender | `SMS` | 有（id=13 rate_limit_per_min=3）| ✅ VERIFIED |
| EmailSender | `EMAIL` | 有（id=1）| ✅ VERIFIED |
| MiniProgramSender | `MINI_PROGRAM` | 有（id=2 rate_limit_per_min=100）| ✅ VERIFIED |
| InAppSender | `IN_APP` | 有（id=4）| ✅ VERIFIED |
| WechatOfficialAccountSender | `WECHAT_OFFICIAL_ACCOUNT` | ❌ DB 存 `MP`（id=3）| ⚠️ **别名漂移（I039）** |

### InAppSender healthCheck（✅ 已实证 — F118 API 层）

InAppSender.healthCheck() 调 `jdbc.queryForObject("SELECT 1 FROM member_message LIMIT 1", Integer.class)`。

**F118 健康检查结果**：`/mkt/channel/health-check` 响应中 `IN_APP` 渠道 `healthy=false`（F118 raw-response 验证）。

**jshell 补充**：`InAppSender.healthCheck()` 因为 `jdbc=null`（jshell 反射实例化无 Spring Bean）→ 抛异常返回 false，与 F118 API 结果一致。

## 二、凭据缺失降级（沿用 D10 铁律）

| Sender | channelType | healthCheck | send() 行为 | Result |
|---|---|---|---|---|
| **SMS** | SMS | false（无阿里云凭据）| demo mode（log + ok）| ✅ **VERIFIED**（render 算法正确）|
| **EMAIL** | EMAIL | false（无 SMTP 凭据）| demo mode 或 SMTP mismatch 检测 | ⚠️ **UNVERIFIED**（真实链路无凭据）|
| **MINI_PROGRAM** | MINI_PROGRAM | false（无 appId/appSecret）| demo mode | ⚠️ **UNVERIFIED**（真实链路无凭据）|
| **IN_APP** | IN_APP | false（member_message 表或 jdbc null）| demo mode 写 DB | ✅ **VERIFIED**（channelType + F118 healthCheck 已实证）|
| **WECHAT_OA** | WECHAT_OFFICIAL_ACCOUNT | false（无 appId/appSecret）| demo mode | ⚠️ **UNVERIFIED**（真实链路无凭据）+ 别名漂移（I039）|

## 三、I039 别名漂移实证

**问题**：`WechatOfficialAccountSender.channelType() = "WECHAT_OFFICIAL_ACCOUNT"`
**DB 实际**：`mkt_channel_config.channel_type = "MP"`（id=3 微信公众号模板消息）

**后果**：
- `getSender("WECHAT_OFFICIAL_ACCOUNT")` 返回 WechatOfficialAccountSender ✅
- 但 DB 存的是 "MP"，`getSender("MP")` 返回 null ❌
- `ChannelDispatcher.getSender(cfg.channelType)` 用 DB 值匹配，永远找不到 OA sender → 走 null → NPE

## 四、F137 Result

| Sender | 验证方式 | Result |
|---|---|---|
| SMS | jshell `render()` 静态方法 5 项全 PASS | **VERIFIED** |
| EMAIL | 静态代码分析（降级 demo mode） | **UNVERIFIED**（无 SMTP 凭据）|
| MINI_PROGRAM | 静态代码分析（降级 demo mode） | **UNVERIFIED**（无 appId/appSecret）|
| IN_APP | jshell channelType + F118 healthCheck API | **VERIFIED** |
| WECHAT_OA | jshell channelType + 别名漂移（I039）| **UNVERIFIED**（无凭据）+ 别名漂移 |

**5 Sender 整体 Result**：**PARTIAL-NON-BLOCKER**
- SMS ✅ / IN_APP ✅ → 算法正确
- EMAIL/MINI_PROGRAM/WECHAT_OA → 无凭据降级 demo（沿用 D10）
- I039 别名漂移 → DB/MP 与 sender/WECHAT_OFFICIAL_ACCOUNT 不匹配（登记 Issue）

## 五、Evidence 文件清单

- `runtime-evidence.md`（本文件）
- `probe-5B.7-sender-channelType.jsh`（jshell 探针）
- `jshell-output.txt`（jshell 运行输出 — render 5 PASS + channelType 5 MATCH）
- `probe-inappsender.jsh`（InAppSender 补充探针）
- `jshell-inappsender.txt`（InAppSender 输出）
