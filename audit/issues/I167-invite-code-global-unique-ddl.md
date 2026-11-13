# I167 — `m09_invite_code.invite_code` 非全局唯一，匿名场景无法用 code 反查 tenantId（I146 前置 DDL）

> **状态**：Open（**只读排查已完成，DDL 未实施** —— 2026-11-14 跑完 I167 §3.3 全部只读 SQL，见 §3.6 结论）
> **严重度**：P2（**前置数据基础缺口**，本身不构成越权；是 I146 匿名化改造的**硬前置**）
> **发现日期**：2026-11-13（I146 C 端邀请端点匿名访问改造 · 只读调查 中的 F3 / Q5）
> **登记人**：Cursor
> **拆分依据**：用户 2026-11-13 决策 —— 该项是**独立的 DDL 与数据迁移工作**，与 I146 的白名单改造解耦，单独立项跟踪
> **绑定基线**：`audit/baseline/env.md` → HEAD commit `03173da0f72c4e0066fa407f96d083601ac1643c`（工作树 dirty，非上线版本）
> **验证等级**：静态代码 + DDL 分析 → **§3.3 只读 SQL 已执行**（2026-11-14，本机开发库 `meiyetec`，5 条业务查询 + 2 条索引查询，**全程只读，无任何写操作**）；**生产库未连接** → 生产存量规模与脏数据比例 **UNVERIFIED**
> **关联**：I146（来源，**本 Issue 是 I146 的硬前置**）、I149（M09 邀请奖励链路缺陷）、I164、I165、I166、F150（M09 解封证据）、F151（M09 发放链路证据）、**F164（只读排查证据目录）**

---

## 0. 一句话描述

`m09_invite_code` 的邀请码唯一性约束是 **`uk_campaign_code (campaign_id, invite_code)`**（活动内唯一），
**不是全局唯一**。生成逻辑 `InviteService.generateUniqueCode(campaignId)` 也只在活动范围内查重。

→ 在 I146 的匿名化场景下，「只拿到一个 `code` 字符串、不知道 tenantId」时，
**服务端无法唯一定位该码属于哪个租户**（要么查不到，要么查到多行），
因此匿名端点的 tenantId 解析无法闭环 —— 这是 I146 的**硬阻塞**，不是可选优化。

---

## 1. 缺陷位置（事实来源）

| 项 | 位置 | 事实 |
|---|---|---|
| 表 DDL | `backend/src/main/resources/db/sql/init_m09_invite.sql` L68-95 | `m09_invite_code` 建表语句 |
| 码列 | 同上 **L76** | `invite_code VARCHAR(32) NOT NULL COMMENT '邀请码（活动内唯一）'` —— 注释本身就写明「活动内唯一」 |
| 唯一键 | 同上 **L89** | `UNIQUE KEY uk_campaign_code (campaign_id, invite_code)` |
| 第二唯一键 | 同上 **L90** | `UNIQUE KEY uk_campaign_member (campaign_id, member_id)` |
| 索引 | 同上 L91-92 | `INDEX idx_member (member_id)` / `INDEX idx_tenant_campaign (tenant_id, campaign_id)` —— **无 `invite_code` 单列索引** |
| 冗余列 | 同上 **L102** | `m09_invite_record.invite_code VARCHAR(32) NOT NULL COMMENT '邀请码'` —— record 表冗余存了一份码 |
| record 唯一键 | 同上 **L140** | `uk_campaign_inviter_phone (campaign_id, inviter_member_id, invitee_phone)` —— **不含 `invite_code`** |
| 生成逻辑 | `backend/src/main/java/com/meiyetec/service/InviteService.java` **L1127-1137** `generateUniqueCode(Long campaignId)` | 查重条件为 `campaign_id = ? AND invite_code = ?`（L1130-1132）→ **只在活动内查重** |
| 码长度常量 | 同上 **L57** | `CODE_LENGTH = 8` |
| 码字符集 | `backend/src/main/java/com/meiyetec/util/Keys.java` **L49-56** `randomCode(int)` | 字符集 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`（32 字符） |
| 码写入 | `InviteService.java` **L259** | `code.setInviteCode(generateUniqueCode(campaignId))`（`getOrCreateCode` 内） |
| 码冗余写入 record | `InviteService.java` **L433** | `record.setInviteCode(inviteCode)`（`trackClick` 建 PENDING 记录时） |

### 1.1 生成逻辑现状（`InviteService.java` L1124-1138）

```java
/**
 * 生成活动内唯一邀请码（最多重试 5 次）
 */
private String generateUniqueCode(Long campaignId) {
    for (int i = 0; i < 5; i++) {
        String code = Keys.randomCode(CODE_LENGTH);
        Long dup = codeMapper.selectCount(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getCampaignId, campaignId)     // ← 只限定活动
                .eq(InviteCode::getInviteCode, code));
        if (dup == 0) return code;
    }
    // 极端兜底：用时间戳后缀
    return Keys.randomCode(CODE_LENGTH) + (System.currentTimeMillis() % 1000);  // ← L1136 完全不查重
}
```

### 1.2 三处「按码查询」全部是**复合条件**（今天没有裸码查询）

| # | 位置 | 查询条件 | 是否含 `tenant_id` |
|---|---|---|---|
| Q1 | `InviteService.getSharePage` **L276-278** | `invite_code = ?` **AND** `tenant_id = ?` | ✅ |
| Q2 | `InviteService.trackClick` **L388-390** | `invite_code = ?` **AND** `tenant_id = ?` | ✅ |
| Q3 | `InviteService.generateUniqueCode` **L1129-1132** | `invite_code = ?` **AND** `campaign_id = ?` | ❌（但有 campaign 兜底） |

> **关键推论**：当前系统**没有任何一处裸按 `invite_code` 查询**。
> 换言之，「码不全局唯一」这个缺陷今天**被 `tenant_id` / `campaign_id` 的复合条件掩盖着**，
> 没有任何暴露面。**一旦 I146 引入「先按码定位租户」这一步，缺陷立刻显性化。**

### 1.3 显性化后的具体故障形态

匿名端点要拿 tenantId，唯一的自然实现是「按 `invite_code` 反查 `m09_invite_code`」：

```java
InviteCode ic = codeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
        .eq(InviteCode::getInviteCode, code));      // ← 匿名场景只能这么查
```

- **跨租户同码存在时**：MyBatis-Plus 的 `selectOne` 在命中 **>1 行**时抛 `TooManyResultsException`
  → 表现为 **HTTP 500**，而不是「返回第一个」。这是**可被外部触发的 500**（任意用户构造一个跨租户重复的码即可）。
- **无重复时**：可正常返回，tenantId 由查出的 `ic.getTenantId()` 决定 —— 这正是方案想要的效果。
- **码不存在时**：`selectOne` 返回 null → 需按「查不到」处理（返回 NOT_FOUND，不泄露租户信息）。

> ⚠️ 上面的 `TooManyResultsException` 行为**依据 MyBatis-Plus `selectOne` 的通用语义推断**，
> **本轮未在本项目运行期实测** → 标 **UNVERIFIED**，实施时须复核本仓 MyBatis-Plus 版本的实际行为。

---

## 2. 缺陷机制与影响

### 2.1 影响链

```
I146 匿名化改造（Q4：tenantId 怎么确定？）
  └─ 候选方案②「邀请码全局唯一后反查」→ 被本缺陷阻塞
       ├─ 当前唯一键是 (campaign_id, invite_code)，同码可在不同活动/租户重复存在
       ├─ 反查需要裸按 invite_code 查 → selectOne 可能命中多行 → 500
       └─ 要消除歧义 → 必须先做 DDL（加前缀 / 加全局唯一约束）
```

### 2.2 为什么会重复（两层）

**第一层：约束层只管活动内。** `uk_campaign_code (campaign_id, invite_code)` 允许「A 租户的活动 1」与「B 租户的活动 7」持有同一个码。

**第二层：生成层也只查活动内。** `generateUniqueCode` 的查重 SQL（L1131）带 `campaign_id` 条件，
**跨活动/跨租户从不查重**。

- 正常路径下，8 位 × 32 字符集 ≈ 40 bit 熵，**跨租户偶然碰撞的概率极低**；
- 但存在**两条确定性产生重复的路径**：
  1. **L1136 兜底分支完全不做任何查重** —— 5 次重试全撞后直接返回 `randomCode(8) + 时间戳后缀`，
     该值**从未与任何库内记录比对过**。虽然带了时间戳后缀，但该分支同样**只在活动内**才有约束保护，
     跨活动写入时无人保证唯一。
  2. **历史数据/人工导入** —— 若曾通过 SQL 批量种过码，或从其它环境导入，跨租户同码可直接落在库里。
- ⚠️ **本轮未连接数据库**，**未核实**当前库中是否**已经存在**跨租户同码（`SELECT invite_code, COUNT(DISTINCT tenant_id) FROM m09_invite_code GROUP BY invite_code HAVING COUNT(DISTINCT tenant_id) > 1`）。**存量重复率未确认**。

### 2.3 硬阻塞的具体落点（I146 内）

| I146 位置 | 被阻塞原因 |
|---|---|
| 方案 A —— `InviteController` 新增 `resolveTenant(code)`，按 `invite_code` + `campaign_id` 反查 | 「+ `campaign_id`」在匿名场景下**不可得**（落地页只带一个 `code`）；只留 `invite_code` 则歧义 |
| 方案 A —— `trackClick` / `getSharePage` 改为 tenantId 服务端解析 | 同上 |
| I146 §6 **Q4**（匿名 tenantId 判定方式） | 建议项②「邀请码全局唯一后反查」的前提条件不成立 |
| I146 §6 **Q5**（是否接受存量数据影响） | 即本 Issue 的核心决策点 |

### 2.4 与其它 Issue 的关系

- **与 I164 / I165 / I166 无依赖**：这三项是**既存缺陷**，与匿名化无关，本 Issue 是**前置基础**。
- **与 I149 有交叉**：I149 指出 `m09_invite_record` 的 `uk_campaign_inviter_phone` 使用**脱敏手机号**参与唯一键，存在撞键；
  本 Issue 若要改码，**不应顺手动这个唯一键**（那是 I149 的范围），但迁移脚本需注意**该唯一键不含 `invite_code`** → 改码不影响它（这是好消息，见 §3.4）。

---

## 3. 修复方案

### 3.1 三个候选（互斥，需业务拍板）

#### 方案 ①：码内嵌租户前缀（推荐）

新码格式 `T{tenantId}-{code}`，例如 `T1-AB3K9XYZ`。

| 项 | 说明 |
|---|---|
| 改动 | `generateUniqueCode` 改为生成 `{tenantId}-{randomCode(8)}`；唯一键改为 `UNIQUE KEY uk_invite_code (invite_code)` |
| 反查 | **无需查库即可解析出 tenantId**（前缀解析）→ 匿名端点直接 `tenantId = parsePrefix(code)`，再校验该码存在 |
| 长度 | `VARCHAR(32)` 容量：`T` + tenantId(≤19 位) + `-` + 8 位 ≈ **29 字符**，够用；但**兜底分支 L1136 的 11 位码**加前缀后 ≈ **32 字符 → 触顶**，需同步把兜底分支改成定长（如 `randomCode(11)` 一次生成，不再拼后缀） |
| 优点 | 匿名场景**零查库**解析租户；现有 `uk_campaign_code` 可保留（活动内唯一仍成立） |
| 缺点 | **存量码格式不一致** → 新旧码需双轨（见 §3.3） |

#### 方案 ②：保持码不变，加全局唯一约束

`ALTER TABLE m09_invite_code ADD UNIQUE KEY uk_invite_code (invite_code);` + 生成时**全局**查重。

| 项 | 说明 |
|---|---|
| 优点 | 码格式不变 → **前端、存量链接、已印发的二维码全部不受影响** |
| 缺点 | 反查仍需查库一次（比方案①多一次 DB 往返，可接受）；生成时查重 SQL 少一个条件，**并发下需依赖唯一索引兜底 + 冲突重试** |
| 前置 | **必须先确认存量无跨租户重复**（§2.2 的 SQL）→ 若有重复，需先消重或重生成 |

#### 方案 ③：新增全局码解析表

新建 `m09_invite_code_global(global_code, tenant_id, campaign_id, invite_code)`，`global_code` 全局唯一，存旧码与租户的映射。

| 项 | 说明 |
|---|---|
| 优点 | **旧码零改动**（历史链接继续可用），只需为新码写映射行 |
| 缺点 | **双表一致性风险**（码表与映射表需同事务写）；多一张表、多一层查询；映射表需要 `global_code` 单列唯一键 |

### 3.2 推荐

**方案 ②（保持码不变 + 全局唯一约束）**，理由：

1. **零存量迁移** —— 不动已有码 → 不动已印发的二维码 / 已发出的短信链接 / 前端已保存的码；
2. **不引入双轨格式** —— 避免 I146 上线后出现「两种码格式并存」的长期负担；
3. **不新增表** —— 避免双写一致性风险（方案③的硬伤）；
4. 唯一键同时**充当 `invite_code` 单列索引** —— 当前 DDL **无 `invite_code` 单列索引**（L91-92 只有 `idx_member` / `idx_tenant_campaign`），
   加了 `uk_invite_code` 后，匿名反查的 `WHERE invite_code = ?` 走**唯一索引**而非全表扫描 → 性能问题顺带解决。

⚠️ 方案②的**唯一硬前提**是**存量无跨租户重复**。若查库发现重复，方案②降级为「先消重（重生成冲突码）+ 再加约束」，代价显著上升。

### 3.3 存量数据迁移评估（无论选哪个方案都要面对）

| 迁移对象 | 位置 | 现状 | 迁移动作 | 风险 |
|---|---|---|---|---|
| `m09_invite_code.invite_code` | `init_m09_invite.sql` L76 | 存量码为旧格式 | 方案②：**不改**；方案①：需 `UPDATE` 加前缀 | 方案①会**使已发放的二维码/短信链接失效** → 需双轨兼容期 |
| `m09_invite_record.invite_code` | 同上 L102（**NOT NULL**） | **冗余存了一份码快照** | 方案①：需与 `m09_invite_code` **同步改码**（否则两侧不一致） | ⚠️ **易漏** —— 这是本次迁移最容易出错的一点 |
| `m09_invite_record` 的唯一键 | 同上 L140 `uk_campaign_inviter_phone (campaign_id, inviter_member_id, invitee_phone)` | **不含 `invite_code`** | **无需改动** | ✅ 改码不破坏该约束 |
| `m09_invite_code.short_link` | 同上 L77 `VARCHAR(255)` | **全仓未找到任何 `setShortLink` 写入**（grep 零命中）→ 该列**恒为 NULL** | 若未来启用短链，需定义「短链 → global_code」的反查规则 | 低（当前无数据） |
| 前端展示 | `frontend/meiyetec-web/src/views/m09-invite/index.vue` L145（邀请码列）、L381（记录详情邀请码） | 直接展示原值 | 方案②：零改动；方案①：前缀会显示在表格里（可接受，但需产品确认观感） | 低 |

**存量排查 SQL（实施时执行，本轮未执行）**

```sql
-- 1) 跨租户同码（方案②的硬前提）
SELECT invite_code, COUNT(DISTINCT tenant_id) AS tenants, COUNT(*) AS rows_cnt
FROM m09_invite_code
WHERE deleted = 0
GROUP BY invite_code
HAVING COUNT(DISTINCT tenant_id) > 1;

-- 2) 活动内同码（正常不应存在，uk 保证）
SELECT campaign_id, invite_code, COUNT(*) FROM m09_invite_code
WHERE deleted = 0 GROUP BY campaign_id, invite_code HAVING COUNT(*) > 1;

-- 3) record 与 code 的码不一致（冗余列漂移，方案①迁移前必查）
SELECT r.id, r.invite_code AS record_code, c.invite_code AS code_code
FROM m09_invite_record r
LEFT JOIN m09_invite_code c
  ON c.campaign_id = r.campaign_id AND c.invite_code = r.invite_code
WHERE r.deleted = 0 AND c.id IS NULL
LIMIT 100;

-- 4) 码长度分布（评估 VARCHAR(32) 是否够放前缀）
SELECT CHAR_LENGTH(invite_code) AS len, COUNT(*) FROM m09_invite_code
WHERE deleted = 0 GROUP BY len ORDER BY len;

-- 5) tenant_id 为 NULL 的脏行（NOT NULL 约束下应为 0）
SELECT COUNT(*) FROM m09_invite_code WHERE tenant_id IS NULL;
```

### 3.4 DDL 脚本落点与兼容性

| 项 | 建议 |
|---|---|
| 文件名 | `backend/src/main/resources/db/sql/migrate_m09_invite_code_global_unique.sql`（沿用 `migrate_m09_*` / `migrate_ext_*` 命名惯例；`init_m09_invite.sql` 是 **CREATE TABLE** 脚本，**不**改它） |
| `ALTER TABLE ... ADD UNIQUE KEY` | **不依赖** MySQL 8.0.29（`ADD COLUMN IF NOT EXISTS` 才需要）→ 兼容性 OK |
| `ALTER TABLE ... DROP INDEX` | 若方案①要替换 `uk_campaign_code`，`DROP INDEX` 是标准语法，兼容性好 |
| 幂等 | 迁移脚本应可重复执行（先查 `information_schema.STATISTICS` 再决定是否 ADD） |
| 新增唯一键的**排他期** | 加唯一约束前必须先消重，否则 ALTER 直接失败 |

### 3.5 顺带修掉的两个代码缺陷（低成本、强烈建议同批做）

| # | 缺陷 | 位置 | 修法 |
|---|---|---|---|
| **S1** | **兜底分支完全不查重** | `InviteService.java` **L1136** | 兜底改为定长生成（如 `randomCode(11)`），并**补一次全局查重**；或直接抛异常让上层重试 |
| **S2** | **查重范围与唯一键范围不一致** | `InviteService.java` **L1130-1132** | 查重条件应与目标唯一键**完全一致**（若加全局唯一键 → 查重去掉 `campaign_id` 条件） |

> S1/S2 属于**代码缺陷**，与 I146 的白名单改造解耦。是否与 DDL 同批实施，**需用户拍板**（本 Issue 登记阶段**不改代码**）。

### 3.6 只读排查结论（2026-11-14 已执行）

> 执行范围：**本机开发库** `meiyetec`（MySQL `localhost:3307`）
> SQL 来源：§3.3 的 5 条业务查询 + `SHOW INDEX` / 存量行数统计
> 写操作：**无**（全程 `SELECT` / `SHOW`，未执行任何 `ALTER` / `UPDATE` / `INSERT` / `DELETE`）
> 证据：`audit/evidence/F164-invite-code-global-unique-readonly-scan/`

#### 3.6.1 排查结果

| # | 排查项 | 结果 | 判定 |
|---|---|---|---|
| 1 | **跨租户同 `invite_code`**（`COUNT(DISTINCT tenant_id) > 1`） | **0 行** | ✅ 无冲突 |
| 2 | 任意重复码（跨活动） | **0 行** | ✅ 无冲突 |
| 3 | 活动内同码（`uk_campaign_code` 应已保证） | **0 行** | ✅ 无冲突 |
| 4 | `m09_invite_record.invite_code` 冗余列漂移 | **0 行** | ✅ 冗余列与 code 表**当前一致** |
| 5 | 码长度分布 | **0 行**（空表） | ⚠️ **未确认**（本机无数据） |
| 6 | `tenant_id IS NULL` 脏行 | **0 行** | ✅ 无脏行 |
| 7 | 存量行数 | `campaigns=0, codes=0, records=0, reward_logs=0` | ℹ️ **本机开发库全空** |
| 8 | `SHOW INDEX FROM m09_invite_code` | `PRIMARY` / `uk_campaign_code(campaign_id,invite_code)` / `uk_campaign_member(campaign_id,member_id)` / `idx_member` / `idx_tenant_campaign` | ❌ **无 `invite_code` 单列唯一键** |

#### 3.6.2 方案结论：**方案②（全局唯一键，不加前缀）**

按用户口径「若存量为 0 冲突，I167 走方案②」：

> **本机开发库存量 0 冲突 → I167 采用方案②：`ADD UNIQUE KEY uk_invite_code (invite_code)`，不加租户前缀。**

依据：

1. SQL 1 / SQL 1b / SQL 2 全部 **0 行** → 无跨租户同码、无跨活动重复码 → 全局唯一约束**可直接 ADD，不会因重复数据 ALTER 失败**
2. SQL 3 **0 行** → `m09_invite_record.invite_code` 冗余列当前与 code 表一致 → 方案②**无需同步改码**，也不存在前缀长度截断问题
3. SQL 7 确认当前**无** `invite_code` 单列唯一键 → 新增后 `WHERE invite_code = ?` 变为**唯一索引查找**，符合 §3.2 第 4 条目标
4. 方案②是**唯一无历史数据迁移**的选项（方案①需改全部存量码并同步冗余列，方案③需新表 + 二次查询）

> ⚠️ **重要边界**：SQL 4（码长度分布）与 SQL 6（存量行数）在本机得到 **0 行**，
> 只证明**本机开发库当前无冲突**，**不能代表生产库**。
> 实施 DDL 前**必须在生产库（或其只读副本）重跑 SQL 1 / SQL 1b**，
> 确认生产存量同样 0 冲突，否则 `ADD UNIQUE KEY` 会直接失败。

#### 3.6.3 本轮**未做**的事

- **未实施任何 DDL**（`ALTER TABLE ... ADD UNIQUE KEY uk_invite_code` **未执行**）
- **未修改** `InviteService.generateUniqueCode`（方案②下**只需把去重查询从「活动内」改成「全局」**，属实施阶段）
- **未做**方案①的前缀改码
- **未连接生产库**

#### 3.6.4 仍待确认

| # | 未确认项 | 影响 | 何时解决 |
|---|---|---|---|
| 1 | 生产库是否存在跨租户同码 | 决定 `ADD UNIQUE KEY` 能否成功 | 实施 DDL 前（生产只读副本） |
| 2 | 生产库存量规模 | 决定 `generateUniqueCode` 改法与索引体积 | 同上 |
| 3 | `generateUniqueCode` 兜底分支（L1136 `randomCode(11)`）是否仍需保留 | 方案②下应改为全局去重 | 实施阶段（与 DDL 同批） |
| 4 | `m09_invite_code.short_link` 是否恒为 NULL | 静态已确认无写入代码，DB 未核 | 实施阶段顺带核 |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 执行 §3.3 的 SQL 1 | **0 行**（无跨租户同码）→ 方案②硬前提成立 |
| 2 | 执行 SQL 3 | **0 行**（record 冗余列与 code 表无漂移） |
| 3 | 执行 SQL 4 | 现有码长度全部 ≤ 方案①新格式所需长度（且确认无 11 位兜底码大量存在） |
| 4 | `SHOW INDEX FROM m09_invite_code` | 存在 `invite_code` 单列唯一键 |
| 5 | 新建邀请码后执行 `SELECT invite_code, COUNT(*) FROM m09_invite_code GROUP BY invite_code HAVING COUNT(*)>1` | **0 行** |
| 6 | 跨租户同名活动各建一码 → 比对两码 | **不同**（方案②） / **不同且带各自租户前缀**（方案①） |
| 7 | 匿名端点按裸码反查（模拟） | 命中**恰好 1 行**，不抛 `TooManyResultsException` |
| 8 | 构造**不存在的码** | 返回 NOT_FOUND，**不返回 500** |
| 9 | 回归：已登录用户走 `/invite/share-page`、`/invite/track` | 行为与修复前一致（复合查询条件不变） |
| 10 | 回归：前端邀请码列表展示 / 记录详情展示 | 码值可读、与 DB 一致 |

> 证据落盘位置：`audit/evidence/FXXX-invite-code-global-unique/`（Feature ID 由实施时分配）。
> ⚠️ 按项目数据安全规则，**写操作测试必须使用绝对不存在的兜底 ID（如 `99999999`）**，
> §3.3 的 SQL 全部为**只读查询**，可直接在**隔离环境**执行；**不得**在生产库执行 `UPDATE` 改码。

---

## 5. 边界声明

- 本 Issue 的**代码与 DDL 结论**来自静态代码分析与 DDL 阅读；**§3.6 的存量结论来自 2026-11-14 在本机开发库执行的只读 SQL**（5 条业务查询 + 索引查询 + 行数统计，**全程无写操作**）。
- **未实施任何 DDL**、**未修改任何业务代码**、**未执行任何写操作 SQL**。
- **未连接生产库** → 生产存量规模、跨租户同码比例、脏数据比例 **UNVERIFIED**；§3.6.2 的「方案②」结论**只对本机开发库成立**。
- 未核实：码长度分布（本机 0 行 → 空分布）、`short_link` 列是否真的恒为 NULL（仅静态确认无写入代码）、生产库真实存量。
- 未核实：`TooManyResultsException` 在本仓 MyBatis-Plus 版本下的确切触发行为（依据通用语义推断，标 UNVERIFIED）。
- 方案选型：**本机口径已定为方案②**；生产库口径待生产只读副本复核后确认（§3.6.4 #1）。
- 本 Issue 是 **I146 的前置**，但**独立跟踪**：不阻塞 I164 / I165 / I166 的修复（该批已于 2026-11-14 完成，证据 `audit/evidence/F161~F163/`）。

---

**下一步**：① 在**生产库只读副本**重跑 §3.3 的 SQL 1 / SQL 1b，确认生产存量同样 0 冲突；② 出 `migrate_m09_invite_code_global_unique.sql`（`ADD UNIQUE KEY uk_invite_code (invite_code)`）并同步把 `generateUniqueCode` 去重查询改为全局；③ 按 §4 采集运行证据 → 落 `audit/evidence/FXXX-.../`；④ 用 §3.6 结论回填 I146 §6 Q4 / Q5。
