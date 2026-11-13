# I168 — M09 邀请 `share_count` / `scan_count` 历史脏数据校正（口径待业务确认）

> **状态**：Open
> **严重度**：P2（数据口径问题，不影响功能正确性；影响运营看板可信度）
> **发现日期**：2026-11-14（I164/I165/I166 修复批次）
> **登记人**：Cursor
> **来源**：I165 §3.5 取舍 ④ + I166 §3.4
> **绑定基线**：修复前 HEAD `1bc831f`
> **验证等级**：存量规模**未核实**（本轮仅在本机开发库查过：4 张 `m09_*` 表均 0 行；**生产库未连接**）→ 运行期 **UNVERIFIED**
> **关联**：I164、I165、I166（本批已修）、F161、F162、F163

---

## 0. 一句话描述

I165（`share_count` 可被任意登录用户跨租户刷）与 I166（`share-page` 隐式埋点导致 `scan_count` 双计数）
两项缺陷修复后，**新流量**的计数口径已正确；但**修复前**产生的历史计数里，
`share_count` 含有越权刷量、`scan_count` 含有「一次访问被计两次」的双计数部分，
**无法从现有数据反推真实值**，需要业务先确认口径，才能做一次性校正。

---

## 1. 脏数据来源

| # | 来源 | 影响字段 | 关联 Issue | 可否反推 |
|---|---|---|---|---|
| 1 | `report-share` 无租户校验 + 无幂等 → 任意登录用户可枚举 `codeId` 无限刷 | `m09_invite_code.share_count` | I165 | **不可反推**（无来源 IP / 无时间戳区分「真实分享」与「刷量」） |
| 2 | `share-page` 隐式 `trackClick` → 一次真实访问 `scan_count` +2 | `m09_invite_code.scan_count` | I166 | **不可反推**（share-page 调用与 track 调用未分别留痕，只有累加值） |
| 3 | 单次真实点击因前端重试 / 双击被重复计数 | `m09_invite_code.share_count` | I165 | **不可反推** |

> ⚠️ `m09_invite_code` 表**没有** `share_count` / `scan_count` 的变更留痕表（无审计日志、无日快照），
> 只有当前累计值 → 任何校正都只能是**近似**。

---

## 2. 现状核实（本轮已做，范围有限）

| 项 | 结果 |
|---|---|
| 本机开发库 `m09_invite_code` 行数 | **0 行**（`audit/evidence/F164-.../raw/01-i167-readonly-scan.txt` SQL6） |
| 本机开发库 `m09_invite_record` / `m09_invite_reward_log` / `m09_invite_campaign` 行数 | **0 行** |
| 生产库 | **未连接、未查询** → 生产存量规模与脏数据比例 **未确认** |
| 计数留痕能力 | **无**（表内无历史快照 / 无变更日志表） |

---

## 3. 候选方案（需业务拍板）

| # | 方案 | 说明 | 代价 | 风险 |
|---|---|---|---|---|
| **A（推荐）** | **保留现状 + 标注口径变更时间点** | 在 B 端看板 / 邀请码列表加一行口径说明：「`share_count` 自 YYYY-MM-DD 起按幂等口径统计；`scan_count` 自同日起不再重复计数」，并冻结历史值的解读 | 最低（前端一个提示文案） | 运营需人工理解两个时间段的数字不可直接比较 |
| B | `share_count` 一次性清零 | 以修复上线时间为界，之后重新累积 | 中（一条 UPDATE + 口径说明） | 丢失历史趋势；且历史值里可能混有真实分享 → **整体清零会误杀真实数据** |
| C | 按经验系数折算 | 例如 `scan_count = scan_count / 2` | 中（一条 UPDATE） | **不可靠**（并非所有访问都双计数）→ 不推荐 |
| D | 引入计数变更流水表 | 新增 `m09_invite_counter_log`，从今往后逐次留痕 | 高（DDL + 写链路改造） | 只解决**未来**，历史仍需 A/B/C |

> 无论选哪个，**必须先在生产库跑一遍统计**（下面 §4 SQL），用真实分布决定，而不是在本机空表上拍脑袋。

---

## 4. 待执行的统计 SQL（生产库前必须先在隔离环境跑）

```sql
-- 1) 各邀请码的计数分布（识别异常高值）
SELECT COUNT(*) AS code_cnt,
       SUM(share_count) AS sum_share,
       MAX(share_count) AS max_share,
       SUM(scan_count)  AS sum_scan,
       MAX(scan_count)  AS max_scan,
       SUM(CASE WHEN scan_count > 0 AND register_count = 0 THEN 1 ELSE 0 END) AS scanned_but_not_registered
FROM m09_invite_code WHERE deleted = 0;

-- 2) 分享数异常的码（疑似被刷）
SELECT id, tenant_id, campaign_id, member_id, invite_code, share_count, scan_count, created_at
FROM m09_invite_code
WHERE deleted = 0 AND share_count > 50
ORDER BY share_count DESC LIMIT 100;

-- 3) 注册数远大于扫码数（口径倒挂异常）
SELECT id, tenant_id, invite_code, share_count, scan_count, register_count, success_count
FROM m09_invite_code
WHERE deleted = 0 AND register_count > scan_count
ORDER BY register_count DESC LIMIT 100;
```

> ⚠️ 全部为**只读**查询。写操作（方案 B/C 的 UPDATE）**必须**先做存在性检查 + 备份快照
> （`mysqldump --where="campaign_id=..."`），且**不得在生产库无备份执行**。

---

## 5. 需要业务确认的问题（**拍板后方可实施**）

| # | 问题 | 选项 |
|---|---|---|
| Q1 | 脏数据处理口径 | A 保留现状+标注 / B 分享数清零 / C 折算 / D 只建流水表 |
| Q2 | 是否需要「从今往后逐次留痕」的能力（新增计数流水表） | 是 / 否 |
| Q3 | 看板是否需要展示「口径变更时间点」 | 是 / 否 |
| Q4 | 校正窗口（若选 B/C）：全量清零 / 仅清零指定活动 | — |

---

## 6. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 执行 §4 SQL | 输出存量的真实分布（替换本文档 §2 的「未核实」） |
| 2 | 校正后 | 计数与所选口径一致，且看板有对应口径说明（若选 A/Q3=是） |
| 3 | 回归 | 新流量计数仍按 I165/I166 修复后的口径正确累积 |

---

## 7. 边界声明

- 本 Issue 仅**登记**，**未做任何校正**（用户明确指示「不做历史脏数据校正」）。
- 未连接生产库；存量规模与脏数据比例 **未确认**。
- 结论来自静态代码分析 + 本机开发库只读查询 → 运行期一律 **UNVERIFIED**。
