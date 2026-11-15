# I180 — `brand_id = NULL` 的渠道配额配置是「死配置」，且同渠道可重复插入

> **状态**：**Open**（本批**仅登记**，**未修改任何业务代码**）
> **严重度**：**P2**（不直接造成数据越权或数据丢失；但让「租户级共享池额度」这一看起来存在的配置**实际完全不生效**）
> **发现日期**：2026-11-15（I178 口径对齐批次）
> **登记人**：Cursor
> **绑定基线**：HEAD `44746cc`（I178 口径对齐修复之后）
> **验证等级**：静态代码分析 + 本机库只读查询 → 运行期 **UNVERIFIED**（本批未运行该路径）
> **关联**：I178（渠道配额覆盖缺口与口径错配）、I169（通用限流基建）

---

## 0. 一句话描述

`mkt_channel_config.brand_id` 允许为 `NULL`，且库内确实存在多条 `brand_id = NULL` 的有效配置，
但**所有按 brand 取配置的代码都用 `eq(brandId, brandId)`** ——
`brandId` 为 `NULL` 时 MyBatis-Plus 生成 `brand_id = ?`（参数为 NULL）→ **永远查不到任何行**
→ 这批配置**永远不会被命中**；同一原因还让**同租户同渠道同品牌的重复校验失效**，`brand_id = NULL` 的配置可以被无限重复插入。

---

## 1. 事实核实（2026-11-15 只读查询 + 静态检索）

### 1.1 库内现状（`mkt_channel_config`，tenant_id=1）

| id | brand_id | channel_type | per_min | per_day | deleted | 取配置能否命中（brandId 传什么） |
|---|---|---|---|---|---|---|
| 13 | **NULL** | SMS | 3 | 5 | 0 | ❌ 任何 brandId 都命中不了（含 NULL） |
| 4 | **NULL** | IN_APP | 1000 | 99999 | 0 | ❌ 同上 |
| 10 | **NULL** | PUSH | 60 | 5000 | 0 | ❌ 同上 |
| 2 | 1 | MINI_PROGRAM | 100 | 5000 | 0 | ✅ 仅 `brandId=1` 命中 |
| 3 | 1 | MP | 50 | 3000 | 0 | ✅ 仅 `brandId=1` 命中 |
| 6 / 8 | NULL | SMS | 60 | 5000 | 1 | 已软删，`@TableLogic` 自动排除 |
| 5 / 9 | NULL | IN_APP | — | — | 1 | 已软删 |
| 7 / 11 / 12 | NULL | PUSH | — | — | 1 | 已软删 |
| 1 | 1 | SMS | 30 | 2000 | 1 | 已软删 |

> 唯一键 `uk_tenant_type_brand (tenant_id, channel_type, brand_id, deleted)`：
> MySQL 唯一索引把 NULL 视为互不相等 → **同一 (tenant, channel) 可以插入多条 `brand_id = NULL` 行**
> → 这正是 IN_APP / SMS / PUSH 各有 2–3 条 NULL 品牌行的原因（重复校验失效的实证）。

### 1.2 受影响的代码点

| # | 位置 | 代码 | 后果 |
|---|---|---|---|
| 1 | `backend/src/main/java/com/meiyetec/service/channel/ChannelDispatcher.java` — `rateLimitCheck` | `.eq(MktChannelConfig::getBrandId, brandId)` | `brandId=NULL` 的任务**永远查不到额度配置** → `cfg == null` → 直接放行 → **无品牌任务实际无限流** |
| 2 | `backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java:71-75` — `createTask` 的重复校验 | `.eq(MktChannelConfig::getBrandId, c.getBrandId())` | `brandId=NULL` 时 `brand_id = NULL` 永不匹配 → `dup` 恒为 0 → **同渠道 NULL 品牌配置可重复创建** |

### 1.3 语义歧义（业务口径未定，两种解读都有数据支撑）

| 解读 | 支撑证据 | 修法 |
|---|---|---|
| **A. `NULL` = 租户级共享池哨兵值** | 库内 3 条 NULL 品牌配置都带有限额值（SMS 3/5、PUSH 60/5000、IN_APP 1000/99999），形态上像「渠道默认额度」 | 取配置改 null-safe，并**加品牌优先、共享池兜底**的两级查找 |
| **B. `NULL` = 历史脏数据** | 重复校验失效导致同类行被插了 2–3 次，且 SMS 那条 `3/5` 明显是联调值 | 清理数据 + 禁止 NULL 品牌落库（DDL 约束或入口校验） |

> ⚠️ **A 与 B 的差别是业务决策**，技术侧不单方面拍板。

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | 租户级共享池额度**实际不生效** | 运维在后台配了「非品牌渠道」的额度 | 以为有限流，实则无限流 → 渠道账号被风控 / 欠费 |
| R2 | 同渠道 NULL 品牌配置**重复堆积** | 运营重复保存配置 | `selectOne(... LIMIT 1)` 无 `ORDER BY` → 命中哪一条**不确定** → 限额随机漂移 |
| R3 | 与 I178 修复后的口径**耦合** | I178 已把计数对齐到 brand 维度 | 若解读 A 成立，共享池额度需要独立计数口径（否则品牌任务永远不计入共享池） |
| R4 | 静默失败 | 任何一种解读 | 配置保存成功、界面显示正常，但运行时**完全不生效**，运营无感知 |

---

## 3. 建议方案（需业务拍板，本批不实施）

| # | 步骤 | 内容 | 前置 |
|---|---|---|---|
| 1 | **业务确认口径** | `brand_id = NULL` 的配置是「租户级共享池」还是「脏数据」？ | 业务确认 |
| 2 | **若为共享池（解读 A）** | 取配置改 null-safe + 两级查找（品牌优先 → 共享池兜底）；共享池计数口径单独定义；创建入口的重复校验同步改 null-safe | 步骤 1 |
| 3 | **若为脏数据（解读 B）** | 清理重复行 + 入口禁止 NULL 品牌（或 DDL 加 `NOT NULL` + 哨兵值 0） | 步骤 1 + 生产库存量排查 |
| 4 | **收口 `selectOne` 不确定性** | 无论哪种解读，取配置都应加确定性排序（如 `ORDER BY id DESC LIMIT 1`） | 步骤 2 / 3 |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | `brandId = NULL` 的任务 | 按选定口径处理（有共享池 → 计入共享池；无 → 显式放行且**有日志**） |
| 2 | `brandId = 1` 的任务 | 优先命中品牌自身配置；品牌无配置时才落到共享池 |
| 3 | 重复创建同渠道 NULL 品牌配置 | 被拒绝（`CONFLICT`），不再出现第 2、3 条同类行 |
| 4 | 同租户多品牌 | 各品牌计数互不干扰（I178 已修）+ 共享池计数口径与选定解读一致 |

---

## 5. 边界声明

- 本 Issue 结论来自**静态代码检索**（`eq(getBrandId` / `eq(MktChannelConfig::getBrandId` 全仓 grep）
  + **本机开发库只读查询**（`mkt_channel_config` 全表 13 行 + DDL）→ 运行期行为一律 **UNVERIFIED**。
- **未修改任何业务代码**、**未执行任何 SQL 写入**。
- 未核实：生产环境 `mkt_channel_config` 中 `brand_id = NULL` 行的数量与限额取值分布（本地库仅作参考）。
- 未核实：业务方当初创建这些 NULL 品牌配置时的意图（是否有后台页面专门用于「非品牌渠道」配置）→ §1.3 两种解读均未排除。

---

**下一步**：业务确认 §3 步骤 1（NULL 品牌配置是共享池还是脏数据）→ 按解读 A/B 分支实施 → 与 I178 §1.3 闸门下沉、I169 通用限流基建合并排期。
