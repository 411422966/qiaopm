# I097 — 11aa1b1 合并 commit 设计缺陷（编码修复与业务替换不可分离）

> **Issue ID**：I097
> **严重度**：P3（开发流程 / 提交卫生）
> **发现时间**：2026-10-04（Post-Audit 二期封存阶段）
> **触发事件**：`11aa1b1` 合并 commit：mojibake 修复 + 21 处 rollbackFor 替换
> **类别**：开发流程 / Commit 设计缺陷
> **Fix Phase**：未来规范（无运行时风险）

---

## 0. 摘要

`commit 11aa1b1c2a1a6e05c30422feae8bf83c950c9945`（2026-10-03 23:47:58）把两个独立操作合并成一个 commit：

1. **mojibake 修复**：4 个 UTF-8 损坏文件的中文字符串字面量恢复（74 行 diff 中的大部分）
2. **21 处 rollbackFor 替换**：`SubscriptionServiceImpl` 8 处 / `DcepSubWalletServiceImpl` 7 处 / `PointServiceImpl` 5 处 / `DcepMerchantQualificationServiceImpl` 1 处

**问题**：两个操作的语义独立、影响范围独立、风险来源独立，**但合并后无法独立 revert**：
- 想要回退「21 处 rollbackFor 替换」→ 必须连同「mojibake 修复」一起 revert
- 想要回退「mojibake 修复」→ 必须连同「21 处 rollbackFor 替换」一起 revert
- `git revert 11aa1b1` 会一次性回退 148 行（74+/74-），但 issue-register.md 等文档已经引用 11aa1b1 为「I088-1 hotfix」完成证据

---

## 1. 4 项核实证据（2026-10-04）

### 1.1 总数差异（649 vs 657）

| 维度 | 数值 | 来源 |
|---|---|---|
| 全局 `@Transactional\b` 总出现次数 | **657** | regex 全文匹配 |
| ├─ 真注解（行首 `@Transactional`）| **650** | 精细行首匹配 |
| │　├─ 带 `rollbackFor = Exception` | **650 (100%)** | 注解块级扫描 |
| │　└─ 不带 rollbackFor | **0** | 同上 |
| └─ 注释中提及 | **7** | javadoc/`//` 注释（误报源）|

**649 vs 657 差异解释**：
- 649 是按 `@Transactional\(rollbackFor` 字符串子串匹配（仅匹配单行带 `(rollbackFor`）
- 650 是按真注解 `@Transactional\(` 起始匹配（覆盖多行注解）
- 差 1 = 一个真注解的 `(` 在下一行（多行格式），两者语义等价
- phase-2.34 报的 657 包含 7 个注释提及，**实际只有 650 个真注解**

### 1.2 i088-3-remaining.txt 101 行历史快照

| 状态 | 数量 | 说明 |
|---|---|---|
| 当前位置已带 rollbackFor | **94** | I088-3 批次已修 |
| 命中点为注释提及（非真注解）| **7** | 历史扫描误报 |
| 仍是裸 @Transactional | **0** | **全部已修** |
| 文件/行号无效 | **0** | — |

**5 个候选文件抽查**（`PriceListService` / `MemberDayService` / `KioskService` / `DianpingSyncService` / `NoShowPredictionService`）全部 100% 带 rollbackFor（9/6/5/4/4）。

### 1.3 3 种正则对比

| 正则 | 命中 | 备注 |
|---|---|---|
| A. `^\s*@Transactional\s*$`（行尾单行无参）| 0 | 只匹配单行无 `@` 注解 |
| B. `@Transactional\b`（任意位置）| 657 | 包括真注解 + 注释提及 |
| C. `@Transactional\b(?![^{}]*rollbackFor)` | 6 | **全部是注释误报**，不是真注解 |
| D. `@Transactional\(` | 650 | 真注解带括号 |

**之前用 A 报 0 裸 = "100% 完成"**：结论对但**方法错**。A 只覆盖单行无参形式；B/C/D 联合验证才能 100% 准确。

### 1.4 11aa1b1 commit 实际改动

| 文件 | diff 行 | 内容 |
|---|---|---|
| `DcepMerchantQualificationServiceImpl.java` | 8 行 | 主要是 mojibake 修复 + 1 处 `@Transactional(rollbackFor=...)` |
| `DcepSubWalletServiceImpl.java` | 68 行 | 主要是 mojibake 修复 + 7 处替换 |
| `PointServiceImpl.java` | 30 行 | 主要是 mojibake 修复 + 5 处替换 |
| `SubscriptionServiceImpl.java` | 42 行 | 主要是 mojibake 修复 + 8 处替换 |
| **合计** | **74+ / 74-** | mojibake 修复**淹没了** 21 处 rollbackFor 替换的 diff 行 |

**commit 设计缺陷**：`11aa1b1` 把 mojibake 修复 + 21 处 rollbackFor 替换合并成一次 commit（因 amend 机制）。回退时无法单独 revert 21 处替换 — 必须 revert 整个 11aa1b1（即回退 mojibake 修复 + rollbackFor 替换）。

---

## 2. 根因分析

| 维度 | 缺陷 | 后果 |
|---|---|---|
| **流程** | 4 文件 mojibake 修复与 21 处 rollbackFor 替换合并为一次 commit | revert 不可分离 |
| **工具** | 用户在 commit amend 时未分两次提交 | 148 行变更被合并 |
| **知识** | 未识别"编码修复"与"业务修改"的语义独立性 | 风险叠加 |
| **EOL 政策 C** | 精确 git add 已正确（仅 4 文件）| ✓ 但 commit 内容混合 |

**对比 I088-3**（commit `3286fc3`）的正确做法：
- I088-3 71 处替换 → 30 文件 git add（仅事务边界修改，无文件级重编码）
- I088-3 净改动 71 行 + 71 行（每处仅追加 `rollbackFor = Exception.class`）
- I088-3 注释行 / 已含 rollbackFor 命中通过 `git add --patch` 跳过

**11aa1b1 应分两个 commit**：

```bash
# commit 1: 编码修复（4 文件 mojibake 恢复）
git add backend/.../SubscriptionServiceImpl.java
git add backend/.../DcepSubWalletServiceImpl.java
git add backend/.../PointServiceImpl.java
git add backend/.../DcepMerchantQualificationServiceImpl.java
git commit -m "fix(encoding): 4 文件 mojibake 恢复（中文 UTF-8 字符串字面量）"

# commit 2: 业务替换（21 处 rollbackFor）
git add backend/.../SubscriptionServiceImpl.java  # 二次 add
git add backend/.../DcepSubWalletServiceImpl.java
git add backend/.../PointServiceImpl.java
git add backend/.../DcepMerchantQualificationServiceImpl.java
git commit -m "fix(I088-1-hotfix): 4 文件 21 处 @Transactional 加 rollbackFor"
```

---

## 3. 影响与风险

### 3.1 现状

- 11aa1b1 已 push main，**未做拆分**（用户决策 2）
- issue-register.md I088 行已累计 179 处（含 11aa1b1 的 21 处）
- 当前代码状态：650/650 真注解带 rollbackFor（100% 正确）
- 当前编码状态：4 文件中文字符已正确恢复（无 mojibake）

### 3.2 风险（**低**，但应记录）

| 场景 | 后果 | 概率 |
|---|---|---|
| 未来需要回退 rollbackFor 替换 | 须连同 mojibake 修复一起 revert，**可能重新引入编码损坏** | 低（业务已稳定）|
| 未来需要回退 mojibake 修复 | 须连同 rollbackFor 替换一起 revert，**可能重新引入事务边界 bug** | 低（业务已稳定）|
| `git log --grep` 搜索 11aa1b1 | 一次 commit 包含两种语义，不利于审计 | 已在 commit message 中标注 `I088-1-hotfix` |
| `git blame` 查询某行 | 21 处 rollbackFor 行 blame 到 11aa1b1，但 mojibake 行也 blame 到 11aa1b1 | 已发生，但可读性仍可接受 |

### 3.3 不可逆性

- 11aa1b1 **不能拆分**（用户决策 2：拆分风险大）
- 只能**未来预防**：所有批量修改必须分两个 commit
- 11aa1b1 的 commit message 标注 `I088-1-hotfix` 部分缓解了审计问题

---

## 4. 修复方案（流程规范 / 未来执行）

### 4.1 强制规则

| 规则 | 描述 | 适用范围 |
|---|---|---|
| **R1** | 文件级重编码（UTF-8 / EOL / 换行符修复）必须独立 commit | 所有 backend/frontend |
| **R2** | 业务逻辑替换（rollbackFor / propagation / try-catch）必须独立 commit | 所有 backend |
| **R3** | R1 与 R2 不可合并，即使针对同一文件 | 优先级：R1 > R2 |
| **R4** | commit message 必须明确标注类型：`fix(encoding):` / `fix(I088):` | 区分编码/业务 |
| **R5** | 精确 `git add`（EOL 政策 C）：仅 add 真正变化的文件 | 不使用 `git add .` |

### 4.2 守门脚本扩展

`audit/scripts/07-scan-transaction.ps1` 已实现：
- `@Transactional\b` 行级扫描
- catch-swallow 模式识别
- 注释行识别

**建议扩展**（P3 backlog）：
- 检测 commit message 格式 `fix(encoding):` vs `fix(业务):`
- 拒绝 1 个 commit 内同时包含 encoding + business 的提交（PR 检查）
- 输出"`commit-content-mix`"违规清单

### 4.3 Phase 5 实施

| 阶段 | 任务 |
|---|---|
| Phase 5A | 在 `docs/contributing.md` 或 `.cursor/rules/` 中加入 R1-R5 规则 |
| Phase 5A | PR 模板新增 checklist：`[ ] 本 commit 仅含 1 类变更（encoding / business / docs）` |
| Phase 5B | 守门脚本加 `git log --oneline -1` 内容检查（可选）|

---

## 5. 关联

| 关联 | 描述 |
|---|---|
| **11aa1b1** | 本 issue 触发的 commit |
| **I088** | 跨 4 commit 累计修复 179 处（I009 9 + I034 1 + I088-1 45 + I088-2 53 + I088-3 71）|
| **phase-2.34-I088-3-report.md** | §8.2 重扫证据 + §10 完结声明 |
| **post-audit-phase3-backlog.md** | §2.1 I088 100% FIXED 状态 |
| **phase-2.21-postmortem.md** | 「11aa1b1 合并 commit 风险」教训记录（用户决策 2）|
| **issue-register.md** | I088 行累计 179 处已记录 |

---

## 6. 验收方式

- [x] 11aa1b1 不可拆分（用户决策 2，已确认）
- [x] issue-register.md I088 行累计 179 处已记录
- [x] phase-2.21-postmortem.md 追加「11aa1b1 合并 commit 风险」教训
- [x] post-audit-phase3-backlog.md §2.1 / §2.4 / §5.3 / §5.5 修订
- [ ] **未来规范**（R1-R5 流程规则）登记到 `docs/contributing.md` 或 `.cursor/rules/`

---

## 7. 状态

| 维度 | 值 |
|---|---|
| 当前 | OPEN（无运行时风险，**纯流程规范**）|
| 优先级 | P3（开发流程）|
| 解决时间 | 不限期（持续规范）|
| 关联 tag | Post-Audit 二期封存后 |

---

**生成时间**：2026-10-04 00:06 (UTC+8)
**关联 commit**：`11aa1b1`（已 push main，**不拆分**）
**关联报告**：`audit/post-audit/phase-2.34-I088-3-report.md` §8.2
**关联 backlog**：`audit/post-audit/post-audit-phase3-backlog.md` §5.5
