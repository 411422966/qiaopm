# I178 — `ChannelDispatcher.rateLimitCheck` 覆盖缺口与多品牌配额口径错配

> **状态**：**Open**（本批**仅登记**，**未修改任何业务代码**）
> **严重度**：**P2**（渠道配额保护面显著小于代码给人的印象；不直接造成数据越权，但会让「已配置的每分钟/每日限额」在多数发送路径上失效）
> **发现日期**：2026-11-14（I169 通用限流调查批次）
> **登记人**：Cursor
> **绑定基线**：修复前 HEAD `4ccca57`
> **验证等级**：静态代码分析（全仓调用点检索）→ 运行期 **UNVERIFIED**
> **关联**：I169（通用限流基建）、I177（EXT-19 预约确认幂等）

---

## 0. 一句话描述

`ChannelDispatcher.rateLimitCheck(tenantId, brandId, channelType)` 是仓库里**唯一**的渠道配额闸门，
但它**只有一个调用点**（触达任务批量发送），**其余 3 条真实发送路径完全不经过它**；
且它的**计数口径（tenant + channel_type）与配置口径（tenant + brand_id + channel_type）不一致**
→ 多品牌租户下，品牌之间互相消耗配额。

> ⚠️ **前提纠正**（I169 调查中的错误前提，本 Issue 已纠正）：
> `ChannelDispatcher` **不是**「Redis 限流范式」——
> 它是 **MySQL 计数型**（`marketing_touch_log` 表聚合查询），**不依赖 Redis、不用固定窗口计数器**，
> 也不是注解 + AOP 的通用限流。它只是**一段被调用方遗漏的业务级配额校验代码**。

---

## 1. 事实核实（2026-11-14 静态检索）

### 1.1 闸门本体

`backend/src/main/java/com/meiyetec/service/channel/ChannelDispatcher.java` — `rateLimitCheck`（**L90-116**）

```java
public boolean rateLimitCheck(Long tenantId, Long brandId, String channelType) {
    // 1. 每分钟限额  —— 计数口径：tenant_id + channel_type（无 brand_id）
    String minSql = "SELECT COALESCE(SUM(CASE WHEN t.sent_at >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) THEN 1 ELSE 0 END), 0) AS per_min " +
            "FROM marketing_touch_log t " +
            "WHERE t.tenant_id = ? AND t.channel_type = ? AND t.deleted = 0";
    Long perMin = jdbc.queryForObject(minSql, Long.class, tenantId, channelType);
    // 配置口径：tenant_id + brand_id + channel_type + status=1
    MktChannelConfig cfg = channelConfigMapper.selectOne(... eq(brandId) ... eq(channelType) ... eq(status,1) ...);
    if (cfg != null && cfg.getRateLimitPerMin() != null && perMin != null && perMin >= cfg.getRateLimitPerMin()) {
        return false;
    }
    // 2. 每日限额 —— 同上口径
    String daySql = "SELECT COUNT(*) FROM marketing_touch_log t " +
            "WHERE t.tenant_id = ? AND t.channel_type = ? AND t.sent_at >= DATE(NOW()) AND t.deleted = 0";
    ...
}
```

| 维度 | 事实 |
|---|---|
| 计数存储 | **MySQL** 表 `marketing_touch_log`（`sent_at` 列，发送成功时才写 `sent_at=NOW()`） |
| 计数口径 | `tenant_id` + `channel_type`（**不含 brand_id**） |
| 配置口径 | `tenant_id` + `brand_id` + `channel_type` + `status=1`（表 `mkt_channel_config`，字段 `rate_limit_per_min` / `rate_limit_per_day`） |
| 失败策略 | 配置缺失（`cfg == null` 或限额为 null）→ **直接放行**（返回 `true`） |
| Redis | **完全不依赖** |

### 1.2 唯一调用点

全仓检索 `rateLimitCheck` → **仅 1 处**：

- `backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java` — `startTask`（**L258**）

### 1.3 未经过该闸门的真实发送路径（**覆盖缺口本体**）

| # | 发送路径 | 发送调用点 | 说明 |
|---|---|---|---|
| 1 | **EXT-19 预约确认**（SMS / EMAIL / MINI_PROGRAM） | `booking/BookingConfirmationService.java` — `sendViaChannel`（**L141**）/ `sendMiniProgram`（**L157**） | 直接 `sender.send(tpl, receiver, vars)`，**不查任何配额**；本轮已由 **I177** 加幂等止血，但**配额维度仍缺失** |
| 2 | **生日通知** | `service/BirthdayNotificationService.java` — `channelDispatcher.getSender(channelType)`（**L194**）→ `sender.send(...)`（**L221**） | 有排程批量发（`scheduledScan`），**无配额闸门** |
| 3 | **设备离线告警** | `service/KioskOfflineAlertService.java` — `channelDispatcher.getSender(channelType)`（**L147**）→ `sender.send(...)`（**L191**） | 告警风暴场景（大量设备同时离线）**无配额闸门**，最易打爆短信配额 |

> 这 3 条路径与 `MarketingAutomationService.startTask` 共用同一批 `ChannelSender` 实现
> （同一套阿里云 / SMTP 账号）→ **配额是共享资源，但只有 1/4 的路径看配额**。

### 1.4 多品牌配额口径错配

| 项 | 口径 | 后果 |
|---|---|---|
| 计数 | `tenant_id + channel_type` | **租户内所有品牌合并计数** |
| 配置 | `tenant_id + brand_id + channel_type` | 每个品牌有独立限额 |
| 组合结果 | 品牌 A 的发送量计入品牌 B 的判定 | ① 品牌 A 被别的品牌"卡死"（提前被限流）；② 品牌 A 自己的限额被全租户共享流量消耗 → **限额被放大 N 倍**（N = 租户下品牌数） |

→ **业务口径必须先确认**：配额到底是「**按品牌**」还是「**按租户**」？
若按租户，则配置里的 `brand_id` 应为「非品牌渠道用 0 / NULL」的哨兵值，而不是按品牌各配一份。

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | 生日通知 / 设备告警 / 预约确认**绕过配额** | 上述 3 条路径任一被批量调用 | 渠道账号被风控 / 欠费；`rate_limit_per_min` 配置形同虚设 |
| R2 | 多品牌配额互相串味 | 租户下配了 ≥2 个 `brand_id` 的渠道 | 一品牌超发、另一品牌被误限 |
| R3 | 配额**只统计发送成功**（`sent_at`） | 大量发送失败 | 失败不计入配额，但**真实调用量已发生**（阿里云侧失败也可能计费） |
| R4 | 配置缺失即放行 | 渠道配置未设限额字段 | 「没配 = 不限」是静默行为，运营无感知 |
| R5 | 与 I169 通用限流**职责重叠但未对齐** | 两者同时存在 | 两套限流口径、两套阈值来源，运维无法统一观测 |

---

## 3. 建议方案（需业务拍板，本批不实施）

| # | 步骤 | 内容 | 前置 |
|---|---|---|---|
| 1 | **确认配额口径** | 配额按品牌还是按租户？→ 决定 `rateLimitCheck` 的计数 SQL 是否加 `brand_id` | 业务确认 |
| 2 | **补齐覆盖** | 把闸门下沉到 `ChannelSender` 装饰器 / `ChannelDispatcher.send(...)` 统一入口，让 4 条路径共用 | 步骤 1 |
| 3 | **明确失败计数口径** | 失败是否计入配额（阿里云侧失败也可能计费） | 业务 + 渠道商确认 |
| 4 | **与 I169 合并观测** | 统一「限流 / 配额拦截次数」埋点与看板 | I169 落地 |

> ⚠️ 用户 2026-11-15 决策：**本批不改 `ChannelDispatcher`**，等业务确认多品牌配额口径后再动。

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 同一租户 2 个品牌各配 `rate_limit_per_min=10` | 按选定口径判定（按品牌 → 互不影响；按租户 → 共用 10） |
| 2 | 生日通知批量发送 | 达到配额后被拦截并留痕 |
| 3 | 设备离线告警风暴 | 达到配额后被拦截并留痕 |
| 4 | 预约确认（I177 幂等之外） | 达到配额后被拦截并留痕 |
| 5 | 配额配置缺失时 | 行为与选定口径一致（显式放行 / 显式拒绝），且**有日志** |

---

## 5. 边界声明

- 本 Issue 结论**全部来自静态代码检索**（`rateLimitCheck` / `dispatcher.getSender` / `sender.send(` /
  `marketing_touch_log` 全仓 grep）→ 运行期行为一律 **UNVERIFIED**。
- **未修改任何业务代码**、**未执行任何 SQL 写入**。
- 未核实：生产环境 `mkt_channel_config` 中 `rate_limit_per_min` / `rate_limit_per_day` 的**实际取值分布**
  （本地库仅作参考，不能代表生产）。
- 未核实：租户下**是否存在多品牌**（`mkt_channel_config.brand_id` 取值分布）→ R2 的实际发生率待定。
- 「失败是否计入配额」取决于各渠道商的计费规则，**未与渠道商确认**。

---

**下一步**：业务确认 §3 步骤 1（配额按品牌 / 按租户）→ 评估步骤 2 的改造范围 → 与 I169 合并到同一批限流基建。
