# Phase 2.21 Postmortem — P2 收口 Delta 归因错误

> **生成时间**：2026-09-28 00:53 (UTC+8)
> **触发事件**：Phase 2.20 P2 收口报告（A.7.3 节）Delta 归因 5 处错误
> **修复版本**：final-post-audit-report.md §A.7.3 修正归因
> **本文件性质**：教训记录（Lessons Learned），不修复业务代码

---

## 0. 事件概述

### 0.1 触发流程

Phase 2.20 二期 P2 收口完成（commit `2e656f4` + `587718f`，tag `v1.0-audit-archive`）后，
用户要求"4 项核实"（01 +2、03 +1、04/08 维度、07 -10 第 10 处）以确保 Delta 真实来源。

### 0.2 发现的问题

逐行 diff 核实后发现 **5 处归因错误**：

| # | 错误归因 | 实际归因 |
|---|---|---|
| 1 | 01-fake-success +2 来自 I085 + I086 | I022 (1) + I031 (1) |
| 2 | 03-disabled +1 来自 I024 runtime el-tag | I022 (1) |
| 3 | 04/08 各子项 Δ = 0（漏报） | 04: 0/+6/+2/-2/+4；08: -2/+4/0/0/0（净 +2）|
| 4 | 07-transaction -10 全部来自 I009 | I009×9 + **I034×1**（YouzanWebhookController.java:80）|
| 5 | 报告总 Delta 列写 0 而非真实值 | 实际多处真实 Δ 被标记为 0 |

### 0.3 影响范围

- **final-post-audit-report.md §A.7.3** — 已修正
- **issue-register.md P2.3 节** — 含同样错误，需要同步修正（**待办**）
- **commit `587718f` 提交说明** — 已记录在历史中，无法修改

---

## 1. 根因分析（RCA）

### 1.1 直接原因

报告归因时使用了**反向因果推断**：基于 Phase 2.20 时段内已修复的 Issue 列表（I084/I085/I024/I086/I009）+ 代码修改记录，**推测**每个 Delta 的来源，**未做实际逐行 diff 验证**。

### 1.2 根本原因

| 维度 | 缺陷 | 后果 |
|---|---|---|
| **方法论** | 归因靠推测而非 diff 证据 | 5/5 错误 |
| **流程** | 守门脚本输出 → 报告环节缺"逐行核对"步骤 | 漏报/错报 |
| **工具** | 未使用 `git diff --no-index` + `Sort-Object -Unique` 做精确匹配 | 误把行号重排当成新增条目 |
| **知识** | 未区分「行号变化」（renumber）vs「新增条目」（new entry）vs「删除条目」（removed）| 把代码重整误判为新引入回归 |
| **审查** | 报告发出前未做交叉验证（另一个 session 或人在回路）| 错误直接进入基线存档 |

### 1.3 防范机制缺位

**当前守门流程**：

```
fix → commit → run 8 scripts → write report → tag archive
                                    ↑
                                缺 diff verification 步骤
```

**应有流程**：

```
fix → commit → run 8 scripts → write report
                                  ↓
                          diff vs baseline (overwrite)
                                  ↓
                          逐行 paired-renumber / new / removed 分类
                                  ↓
                          归因到具体 commit + issue
                                  ↓
                          tag archive
```

---

## 2. 时序还原

```
2026-09-25 22:48  Phase 2.15 P1 修复完成，FIXED 21→25
2026-09-28 00:05  I009 fix 落地（commits 52b4714/d5b0d87/5b48e70）
2026-09-28 00:10  I084 fix 落地（commit a8fc5a0）
2026-09-28 00:21  I085 fix 落地（commit fe1bdb7）
2026-09-28 00:27  I024 fix 落地（commit 1f2b608）
2026-09-28 00:31  Phase 2.20 二次跑 8 scripts
2026-09-28 00:32  对比 baseline dcaf07a：01 +2、03 +1、04 多项变、07 -10
2026-09-28 00:35  ✗ 推测归因：01=I085/I086, 03=I024, 04/08=0
2026-09-28 00:35  写入 final-post-audit-report.md §A.7.3 + tag 重打
2026-09-28 00:45  用户要求 4 项核实
2026-09-28 00:53  ✓ 逐行 diff 核实完成，发现 5 处错误
2026-09-28 01:00  ✓ §A.7.3 修正归因 + 本 postmortem + 重新 commit + tag 重打
```

**关键时间窗**：归因动作（00:32-00:35）只用了 3 分钟，但 commit 列表里 I085/I086 的修改**最近**，导致锚定错误。
事实是：脚本命中的实际位置（`PluginPersistenceService.java:103`、`GoogleMapsService.java:231`）
都是**老 commit**（I022 commit `8c256c9`、I031 commit `b1d6e7e`）的成果，
**与 P2 修复无关**。

---

## 3. 教训（Lessons Learned）

### 3.1 核心教训（TL;DR）

> **Delta 归因必须逐行 diff 核实，不能凭 commit 列表推测。**
> **Phase 2.20 P2 收口报告的 Delta 归因有 5 处错误。**

### 3.2 具体教训

1. **报告归因是审计的薄弱环节**：fix commit 准确，但归因报告错了 5/5。
2. **守门脚本是 retroactive 工具**：它们跑的是当前代码，归因必须基于 diff（baseline vs now），不能基于近期 commit 列表。
3. **行号重排不是新条目**：21/25 条 01-fake-success diff 是行号重排，仅 2 条真正新增。
4. **Vue 模板不被 scripts 扫**：el-tag、template 标记 0 影响（03-disabled 那条最冤）。
5. **跨 commit 影响常被忽略**：GoogleMapsService.java 的 +2 来自 I031（2026-09-25 修复），
   I009/I020/I022/I034/I020 也都是修复后才在脚本里显现。

### 3.3 流程改进建议（提交给后续 Phase）

| 建议 | 优先级 | 工作量 |
|---|---|---|
| 报告归因前强制 git diff --no-index | **P0** | 5 分钟 |
| 引入 `_exact-compare.ps1` 自动化（已存在 audit/scripts/）| **P0** | 0（已实现？）|
| 报告归档前**双人**或**两 session** 交叉验证 | **P1** | 30 分钟/份 |
| 归因表加 commit hash 列 | **P1** | 10 分钟 |
| 守门脚本对 diff 加显式 newline-aware 行号稳定化 | **P2** | 2 小时（重写脚本）|
| 加一个 "delta-attribution-checker.ps1" 自动归因 | **P2** | 4 小时 |

---

## 4. 立即行动（已执行）

- [x] `audit/post-audit/final-post-audit-report.md §A.7.3` 修正归因（5 处）
  - 01 +2 → I022 (PluginPersistenceService.java:103) + I031 (GoogleMapsService.java:231)
  - 03 +1 → I022 (PluginPersistenceService.java:82)
  - 04 Delta 0 → 0/+6/+2/-2/+4；08 Delta 0 → -2/+4/0/0/0
  - 07 -10 → I009×9 + I034×1 (YouzanWebhookController.java:80)
- [x] `audit/post-audit/phase-2.21-postmortem.md`（本文档）建立
- [x] git commit + tag 重打（v1.0-audit-archive 重新指向修正后 HEAD）

## 5. 后续待办（未触业务代码）

- [ ] **同步修正 `audit/issues/issue-register.md P2.3 节`**：与 §A.7.3 相同错误，需要逐行重写（建议下一阶段处理）
- [ ] **自查历史 P1 收口报告（Phase 2.15）**：A.6 节的归因是否也有同样问题（建议一并审计）
- [ ] **新增 `_verify-attribution.ps1` 自动化校验**：未来报告归档前强制跑

---

## 6. 关联文档

| 文档 | 路径 | 内容 |
|---|---|---|
| 修正后报告 | `audit/post-audit/final-post-audit-report.md §A.7.3` | 修正后的 Delta 表 + 归因 |
| I022 修复 | `audit/post-audit/phase-2.13-I022-report.md` | PluginPersistenceService.getLastError |
| I031 修复 | `audit/post-audit/phase-2.15-I031-report.md` | GoogleMapsService catch 升级 + log |
| I034 修复 | `audit/post-audit/phase-2.12-I034-report.md` | YouzanWebhookController.rollbackFor |
| 修正 commit | （本次提交） | docs(audit): 修正 P2 收口 Delta 归因 + postmortem |
| I097 流程规范 | `audit/issues/I097-commit-design-fix.md` | 11aa1b1 合并 commit 设计缺陷 |

---

## 7. 追加记录 — 11aa1b1 合并 commit 风险（2026-10-04）

> **追加时间**：2026-10-04 00:06 (UTC+8)（Post-Audit 二期封存阶段）
> **触发事件**：4 项核实（649/657/0 + i088-3-remaining.txt 101 行 + 3 种正则对比 + 11aa1b1 实际改动）
> **用户决策**：**不拆分 11aa1b1**（决策 2）

### 7.1 事件背景

`commit 11aa1b1c2a1a6e05c30422feae8bf83c950c9945`（2026-10-03 23:47:58）把两个独立操作合并成一个 commit：

1. **mojibake 修复**：4 个 UTF-8 损坏文件的中文字符串字面量恢复（74 行 diff 中的大部分）
2. **21 处 rollbackFor 替换**：`SubscriptionServiceImpl` 8 处 / `DcepSubWalletServiceImpl` 7 处 / `PointServiceImpl` 5 处 / `DcepMerchantQualificationServiceImpl` 1 处

### 7.2 风险

| 风险 | 后果 | 概率 |
|---|---|---|
| 未来回退 rollbackFor 替换 | 须连同 mojibake 修复一起 revert，可能重新引入编码损坏 | 低 |
| 未来回退 mojibake 修复 | 须连同 rollbackFor 替换一起 revert，可能重新引入事务边界 bug | 低 |
| `git log --grep` 搜索 | 一次 commit 包含两种语义，不利于审计 | 已在 commit message 中标注 `I088-1-hotfix` |
| `git blame` 查询某行 | 21 处 rollbackFor 行 + mojibake 行均 blame 到 11aa1b1 | 可读性可接受 |

### 7.3 用户决策（决策 2：不拆分）

**理由**：
- 11aa1b1 已 push main，**拆分风险大**（需 `git reset --soft HEAD~1` + 重新分两次 commit + 重新 push force）
- 当前代码状态 100% 正确（650/650 真注解带 rollbackFor + 4 文件中文字符已恢复）
- issue-register.md I088 行已累计 179 处（含 11aa1b1 的 21 处），拆分 commit 不改变 I088 状态

**结论**：**保持 11aa1b1 现状**（不拆分），但**未来规范**必须遵循以下原则。

### 7.4 未来规范（已在 I097 流程规范 issue 中明确）

> **未来批量修改必须分两个 commit**：
>
> 1. **commit 1：恢复损坏文件（编码修复）** —— `fix(encoding):` 前缀
>    - 仅修改文件编码（UTF-8 / EOL / 换行符）
>    - 不修改任何业务代码
>    - 不动 `import` / 注解 / 方法签名
> 2. **commit 2：业务替换（rollbackFor / 业务逻辑）** —— `fix(业务编号):` 前缀
>    - 仅修改业务逻辑
>    - 不动文件编码
>    - 精确 `git add`（EOL 政策 C）
>
> 即使是同一文件，也必须分两个 commit。

**EOL 政策 C 仍生效**：
- 精确 `git add`（仅 add 真正变化的文件）
- 不使用 `git add .`
- 每个 commit 必须有清晰 message

### 7.5 I097 关联

详见 `audit/issues/I097-commit-design-fix.md`：
- 严重度：P3（开发流程）
- 类别：Commit 设计缺陷
- 关联 commit：`11aa1b1`
- 解决方案：未来规范（R1-R5 规则）+ 守门脚本扩展

### 7.6 与本 postmortem 的关系

| 维度 | 关系 |
|---|---|
| **共同主题** | 都是「报告/提交 卫生」问题 |
| **教训一致性** | 7.21「归因靠推测而非 diff 证据」+ 7.4「commit 内容必须语义独立」|
| **防范机制** | 都需「逐行核对」+「明确 commit message」 |
| **流程改进** | 都应纳入 Phase 5A 提交规范 |

---

**🔚 Postmortem 完结 — Phase 2.21 已建立 lessons-learned 闭环 + 11aa1b1 风险记录。**

---

*生成时间：2026-09-28 00:53 (UTC+8)*
*关联 tag：v1.0-audit-archive（Post-Audit 二期 P1+P2 全清 + Delta 归因修正）*
