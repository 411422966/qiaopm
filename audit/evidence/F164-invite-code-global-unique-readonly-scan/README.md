# F164 · I167 存量数据只读排查（`m09_invite_code.invite_code` 全局唯一性）

> 状态：**VERIFIED（只读口径）** —— 存量 0 冲突 → I167 方案②（全局唯一键）硬前提成立
> 基线 commit：`1bc831f`
> 环境基线：`audit/baseline/env.md`（MySQL `localhost:3307/meiyetec`）
> 对应 Issue：`audit/issues/I167-invite-code-global-unique-ddl.md` §3.3
> 原始输出：`raw/01-i167-readonly-scan.txt`

---

## 1. 排查范围与边界

| 项 | 说明 |
|---|---|
| 执行的 SQL | I167 §3.3 的 5 条只读查询 + 2 条补充查询（存量行数 / `SHOW INDEX`） |
| 是否写操作 | **否** —— 全部为 `SELECT` / `SHOW`，未执行任何 `ALTER` / `UPDATE` / `INSERT` / `DELETE` |
| 是否落盘 DDL | **否** —— I167 的 DDL 本轮**未实施**（按用户指令「不动 I167 的 DDL」） |
| 数据库 | 本机开发库 `meiyetec`（**非生产库**） |
| 执行时点 | 本轮修复批次内、隔离测试数据清理**之后**（库内 4 张 `m09_*` 表均为 0 行基线） |

---

## 2. 排查结论

| # | 检查项 | 期望 | 实测 | 判定 |
|---|---|---|---|---|
| SQL1 | 跨租户同码（`COUNT(DISTINCT tenant_id) > 1`） | 0 行 → 方案②硬前提成立 | **0 行** | ✅ |
| SQL1b | 任意重复码（跨活动，补充） | — | **0 行** | ✅ |
| SQL2 | 活动内同码（`uk_campaign_code` 应已保证） | 0 行 | **0 行** | ✅ |
| SQL3 | `m09_invite_record.invite_code` 冗余列与 code 表漂移 | 0 行 | **0 行** | ✅ |
| SQL4 | 码长度分布（评估 `VARCHAR(32)` 是否够放前缀） | — | **0 行**（表内无数据） | ⚠️ 无样本 |
| SQL5 | `tenant_id IS NULL` 脏行 | 0 | **0** | ✅ |
| SQL6 | 存量行数（补充） | — | `campaigns=0, codes=0, records=0, reward_logs=0` | — |
| SQL7 | `SHOW INDEX FROM m09_invite_code`（补充） | — | `PRIMARY` / `uk_campaign_code(campaign_id,invite_code)` / `uk_campaign_member(campaign_id,member_id)` / `idx_member` / `idx_tenant_campaign` | ⚠️ **无 `invite_code` 单列索引** |

---

## 3. 对 I167 方案选型的结论

> **存量 0 冲突 → I167 可走方案②（保持码不变 + `ADD UNIQUE KEY uk_invite_code (invite_code)`）。**

依据：
1. SQL1 返回 0 行 → 无跨租户同码 → 加全局唯一约束**不会因存量重复而 ALTER 失败**；
2. SQL1b / SQL2 返回 0 行 → 无跨活动 / 活动内重复；
3. SQL3 返回 0 行 → `m09_invite_record.invite_code` 冗余列与 code 表**当前无漂移**，方案①（加前缀）若实施也无需同步改码；
4. SQL7 → 加 `uk_invite_code` 后同时充当 `invite_code` 单列索引，匿名端点裸码反查可走唯一索引；
5. ⚠️ SQL4 无样本（表内 0 行）→ 「现有码长度是否都 ≤ 32」**仍属未确认**，但在方案②下**不影响**（码格式不变）。

**方案①（前缀）仍可选**，但在本库状态下无额外收益（无存量码需要双轨兼容）。

---

## 4. 仍未确认项（留给 I167 实施轮）

| # | 未确认项 | 影响 |
|---|---|---|
| 1 | `m09_invite_code` 在**生产库**的存量规模与重复率（本轮仅查本机开发库） | 生产加唯一约束前需重跑 SQL1 |
| 2 | `generateUniqueCode` 的**兜底分支**（`randomCode(8) + 时间戳后缀`）完全不做查重（I167 §3.5 S1） | 属代码缺陷，与 DDL 同批处理 |
| 3 | 查重范围与唯一键范围不一致（§3.5 S2） | 同上 |
| 4 | `m09_invite_code.short_link` 是否恒为 NULL | 静态确认全仓无写入代码，DB 侧无样本 |

---

## 5. 边界声明

- 本目录结论基于**实际执行的只读 SQL 输出**（`raw/01-i167-readonly-scan.txt`）；未执行任何写操作、未实施 DDL。
- 未连接生产库；本机开发库 `m09_*` 四表为空 → 多数 SQL 的「0 行」结论**样本量为 0**，
  只能证明「当前无冲突」，不能证明「历史上从未有冲突」（已删除的行不在检查范围内）。
