# I096 — 4 个 Java 源文件 UTF-8 字节序列损坏（mvn compile 失败）

> **Issue ID**：I096
> **发现时间**：2026-10-02 12:50（Phase 2.34 守门脚本重扫后追溯）
> **发现方式**：`mvn -q -o compile -DskipTests` 失败，30+ 编译错误指向 4 个源文件
> **严重度**：~~**P1 BLOCKER**~~ → ✅ **FIXED & VERIFIED**（L5b 验证通过）
> **Fix Phase**：~~Post-Audit 三期（前置）~~ → **已闭合（commit `11aa1b1`）**
> **类别**：UTF-8 字节序列损坏 / 编码污染（与 I097 合并 commit 设计缺陷关联）

---

## 0. 摘要

4 个后端 Java 源文件的中文字符串字面量在保存时发生**双重编码破坏**（mojibake），导致 `mvn compile` 失败，**整个后端无法构建**。

- 现象：Java 编译器报"未结束的文字字符串"（illegal character / unclosed string literal）
- 根因：**commit `b3b284b`（I088-1）保存时编码错误** — 中文字符被以 GBK 字节 → UTF-8 错误路径保存，每处中文字符变成 6-9 字节无意义字节序列
- 修复：**commit `11aa1b1`（I088-1-hotfix）** 从 `b3b284b~1` 恢复 + 重做 `rollbackFor` 替换（74+/74-）
- 验证：**`mvn -q -o compile -DskipTests` EXIT=0**（本次实测 2026-10-08 23:55，HEAD `e6bf350`）
- 关联：I088-1 / I097（合并 commit 设计缺陷）

---

## 1. 4 个损坏文件

| # | 完整路径 | 损坏位置（修复前）| 修复后内容（实测）|
|---|---|---|---|
| 1 | `backend/src/main/java/com/meiyetec/service/impl/DcepSubWalletServiceImpl.java` | L131,60 / L195,21 / L211,62 / L238,60 | UTF-8 合法中文字符串 ✅ |
| 2 | `backend/src/main/java/com/meiyetec/service/impl/SubscriptionServiceImpl.java` | L80,62-76 / L257,18 | L80 = `"7天体验"` ✅ |
| 3 | `backend/src/main/java/com/meiyetec/service/impl/DcepMerchantQualificationServiceImpl.java` | L68,45 | L68 = `"资质审核中，暂不可修改"` ✅ |
| 4 | `backend/src/main/java/com/meiyetec/service/impl/PointServiceImpl.java` | L64,59 / L141,78 / L179,61 / L200,31 / L202,30 / L204,35 | L64 = `"获得"` / `"消耗"` ✅ |

> **本次实测**（2026-10-08 23:55）：使用 Read 工具读取上述文件，4 个文件的所有原"损坏位置"已恢复正常中文字符串。Grep 验证 mojibake 字符串（鑾峰緱 / 娑堣€ / 璧勮川 / 宸插叧闂 / 澶╀綋楠 / 7澶╀綋楠）0 命中。

---

## 2. 根因分析

### 2.1 时序链

```
eca3383 (feat: P2 modules backend historical development)
    ↓
b3b284b (fix I088-1: 高写密度服务 @Transactional 加 rollbackFor)
    ↓ ← 💥 在这步保存时，4 文件 mojibake 引入
8e821ee (docs: I088-1 PARTIAL)
    ↓
11aa1b1 (fix I088-1-hotfix: 4 文件恢复 + 重做 rollbackFor，74+/74-)
    ↓ ← ✅ 4 文件中文恢复 + 21 处 rollbackFor 重做
e6bf350 (HEAD: ARCHIVE 81 行孤立 CR 字节修复)
```

### 2.2 损坏类型

| 维度 | 实际观察 | 与 phase-2.34 §5 描述差异 |
|---|---|---|
| **BOM** | 4 个文件均**无 BOM**（不是 UTF-16 LE）| 报告原标"UTF-16 LE 编码"→ **修正**：4 文件均为 UTF-8 无 BOM |
| **EOL** | 4 个文件均**为 LF**（无 CR）| 报告未标 EOL 问题 |
| **字符编码** | 中文字符串字面量**双重编码破坏**（mojibake）| 报告原标"UTF-8 字节序列损坏"→ **修正**：4 文件均为合法 UTF-8，但中文字符被错误重新编码（典型"GBK 字节 → UTF-8 错误解码 → 重新保存为 UTF-8"二次污染）|
| **mvn 编译** | **`mvn -q -o compile -DskipTests` 失败，exit=1** | 报告与实际一致（修复前 P1 BLOCKER）|

### 2.3 触发原因（推断）

- 开发者使用 PowerShell / 旧编辑器编辑 4 文件
- 编辑器默认编码 = Windows GBK（或系统 Locale 编码）
- 保存时未明确指定 UTF-8 无 BOM
- 中文字符先按 GBK 解码 → 错误字节流 → 再按 UTF-8 错误编码 → 落盘为非法 UTF-8
- 编译器在字符串字面量边界上找不到合法 UTF-8 字符 → 报错

### 2.4 与 I097 的关系

I096 的修复（`11aa1b1`）与 I097 描述的"合并 commit 设计缺陷"是**同一个 commit 的两个视角**：

- **I096 视角**：commit 内容是 4 文件 mojibake 恢复 + 21 处 rollbackFor 重做
- **I097 视角**：commit 包含 encoding 修复 + business 替换两个语义独立的操作，**不可单独 revert**

I097 已记录"11aa1b1 不能拆分"为用户决策 2，本 Issue（I096）只关注 encoding 维度的事实闭合。

---

## 3. 修复时间线

| 时间 | 事件 |
|---|---|
| 2026-10-02 03:39 | 守门脚本 07-scan-transaction 重跑，触发 mvn compile 失败（30+ 编译错误）|
| 2026-10-02 12:50 | 用户口径确认 4 UTF 损坏文件位置 |
| 2026-10-03 00:29 | post-audit-phase3-backlog.md 整理，§1 登记 4 文件 + 建议 I096 立项 |
| 2026-10-03 23:47 | **commit `11aa1b1` 提交**：mojibake 修复 + 21 处 rollbackFor 重做（74+/74-）|
| 2026-10-04 00:06 | I097 登记（合并 commit 设计缺陷）|
| 2026-10-04 | I088 100% 核实（650/650 真注解带 rollbackFor）|
| 2026-10-08 23:55 | **本次实测**：`mvn compile EXIT=0` + 4 文件 L5b 字节流核实 → I096 可正式闭合 |
| 2026-10-08 23:55 | 本 Issue 文件新建 + 登记到 issue-register.md（形式工作）|

---

## 4. 验证证据

### 4.1 mvn compile 验证（2026-10-08 23:55）

```bash
$ cd d:\QiaoPM\backend && mvn -q -o compile -DskipTests
EXIT=0
✅ mvn compile PASSED
```

### 4.2 4 文件 L5b 字节流验证（2026-10-08 23:55）

| 文件 | 行 | 实际字节 | 期望 |
|---|---|---|---|
| `SubscriptionServiceImpl.java` | L80 | `"7天体验"` | `"7天体验"` ✅ |
| `PointServiceImpl.java` | L64 | `"获得"` / `"消耗"` | `"获得"` / `"消耗"` ✅ |
| `DcepMerchantQualificationServiceImpl.java` | L68 | `"资质审核中，暂不可修改"` | `"资质审核中，暂不可修改"` ✅ |
| `DcepSubWalletServiceImpl.java` | L131 等 | UTF-8 合法中文字符串 | 无 mojibake ✅ |

### 4.3 Grep mojibake 字符串（2026-10-08 23:55）

```bash
$ grep -P "鑾峰緱|娑堣€|璧勮川瀹℃牳|宸插叧闂|澶╀綋楠|7澶╀綋楠" \
    backend/src/main/java/com/meiyetec/service/impl/*.java
# 0 matches
```

### 4.4 守门脚本（2026-10-08 23:55 重跑）

7-transaction 守门脚本：当前 13 处 → 修复后预期保持 13 处（6 处注释误报 + 7 处 I095 catch-swallow）。**I096 与 7-transaction 守门脚本正交**（不影响其命中数）。

---

## 5. 关联

| 关联 | 描述 |
|---|---|
| **11aa1b1** | 修复 commit（74+/74-，mojibake 恢复 + 21 处 rollbackFor 重做）|
| **b3b284b** | 引入 commit（I088-1 原始，引入 mojibake）|
| **I088** | @Transactional rollbackFor 全后端修复（650/650 已完成）|
| **I088-1** | 高写密度服务子批次（包含本次出问题的 4 文件）|
| **I097** | 11aa1b1 合并 commit 设计缺陷（encoding + business 不可单独 revert）|
| **phase-2.34-I088-3-report.md** | §5 已知遗留 4 UTF 损坏文件（修复前记录）|
| **post-audit-phase3-backlog.md** | §1 立项建议（已采纳）|
| **gate3-readiness-assessment.md** | §5.3 推荐第一步（I096 立项 + 关闭 30 min 形式工作）|

---

## 6. 修复方案（已执行）

| 步骤 | 操作 | 状态 |
|---|---|---|
| 1 | `git checkout b3b284b~1 -- <4 files>` 恢复 4 文件到损坏前状态 | ✅（在 11aa1b1 内执行）|
| 2 | 重新对 4 文件执行 `rollbackFor = Exception.class` 替换（21 处）| ✅（在 11aa1b1 内执行）|
| 3 | `mvn -q -o compile -DskipTests` 验证 EXIT=0 | ✅（11aa1b1 内验证）|
| 4 | I096 立项文件 + issue-register.md 登记 + 修订 2 份过时报报告 | ✅（2026-10-08 形式工作）|
| 5 | 守门脚本重跑确认无新缺口 | ✅（2026-10-08）|

---

## 7. 预防规范（I097 R1-R5 沿用）

| 规则 | 描述 | 适用范围 |
|---|---|---|
| **R1** | 文件级重编码（UTF-8 / EOL / 换行符修复）必须独立 commit | 所有 backend/frontend |
| **R2** | 业务逻辑替换（rollbackFor / propagation / try-catch）必须独立 commit | 所有 backend |
| **R3** | R1 与 R2 不可合并，即使针对同一文件 | 优先级：R1 > R2 |
| **R4** | commit message 必须明确标注类型：`fix(encoding):` / `fix(I088):` | 区分编码/业务 |
| **R5** | 精确 `git add`（EOL 政策 C）：仅 add 真正变化的文件 | 不使用 `git add .` |

**注**：11aa1b1 是 R1-R5 之前的 commit，**已 push main 不拆分**（用户决策 2），仅作教训记录。

---

## 8. 验收方式

- [x] 4 文件 mojibake 全部恢复
- [x] `mvn -q -o compile -DskipTests` EXIT=0
- [x] Grep mojibake 字符串 0 命中
- [x] issue-register.md 登记 I096（FIXED & VERIFIED）
- [x] phase-2.34-I088-3-report.md §5 修订（追加"已于 11aa1b1 修复"）
- [x] post-audit-phase3-backlog.md §1 修订（追加"I096 已闭合"）
- [x] 守门脚本 7-transaction 重跑确认无新缺口

---

## 9. 状态

| 维度 | 值 |
|---|---|
| 当前 | ✅ **FIXED & VERIFIED** |
| 验证级别 | **L5b**（mvn compile EXIT=0 + 4 文件字节流实测 + Grep 0 命中）|
| 关闭时间 | 2026-10-08 23:55（形式工作）|
| 关联 commit | `11aa1b1`（修复）+ `e6bf350`（HEAD 验证）|
| 关联 tag | Post-Audit Gate 3 批次 1.1 第一步 |

---

**生成时间**：2026-10-08 23:55 (UTC+8)
**关联 commit**：`11aa1b1`（已 push main，**不拆分**）
**关联报告**：`audit/post-audit/phase-2.34-I088-3-report.md` §5 / `audit/post-audit/post-audit-phase3-backlog.md` §1
**关联 Issue**：I088 / I088-1 / I097 / gate3-readiness-assessment §5.3
