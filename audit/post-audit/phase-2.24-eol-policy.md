# Phase 2.24 — EOL（End-of-Line）漂移政策与挂账决策

> **记录时刻**：2026-09-29 23:45 (UTC+8)
> **适用范围**：整个仓库（`d:\QiaoPM`）
> **关联 Issue**：I036 收口后的 git status 复核
> **决策等级**：长期挂账（不阻塞后续 P0/P1/P2/P3 修复链路）

---

## 1. 背景

I036（GCP API Key 完全遮蔽）双 commit（`6abc30e` + `f916812`）落库后，对工作区做 `git status` 复核时发现：**946 个文件处于 ` M` 状态**。但每一项抽样均呈现"insertions == deletions"对称、且 `git diff -w` 完全为空 — 直觉上**不可能是实质代码改动**。

---

## 2. 根因定位（只读核实）

### 2.1 数据指纹

| 检查 | 命令 | 结果 |
|---|---|---|
| 1 | `git status --short \| Measure-Object` | **946** |
| 2 | `git status --short \| Select-Object -First 30` | 全部 ` M` 前缀，无 `A/M/R/D` 等其他状态 |
| 3 | `git diff --shortstat` | **946 files changed, 126105 insertions(+), 126105 deletions(-)** |
| 4 | `git diff -w --shortstat` | **空**（忽略空白后 0 字节差异） |
| 5 | Git 警告 | 每个文件 stderr 一致：`CRLF will be replaced by LF the next time Git touches it` |

### 2.2 抽样 4 类文件

| # | 文件路径 | 类型 | diff --stat | 结论 |
|---|---|---|---|---|
| 1 | `audit/baseline/env.md` | 审计文档 | 234 ins / 234 del | **EOL 替换** |
| 2 | `backend/src/main/java/com/meiyetec/service/GoogleMapsService.java` | I036 后端 | **空**（无内容 diff） | 仅 stat 漂移 |
| 3 | `frontend/meiyetec-web/src/views/google-maps/index.vue` | I036 前端 | **空**（无内容 diff） | 仅 stat 漂移 |
| 4 | `backend/src/main/resources/db/sql/migrate_ext_22_google_maps_place_details.sql` | SQL | 14 ins / 14 del | **EOL 替换** |

### 2.3 根因判定

- **`insertions == deletions` 严格相等**（126105 == 126105）→ Git 在统计层把每行"删除-重插"，这是 CRLF ↔ LF 转换的指纹。
- **`-w` 后为空** → 忽略所有空白后无任何差异 → **不存在任何业务 / 文档改动**。
- **Git 警告统一定位为 CRLF → LF** → working tree 当前是 CRLF，`.gitattributes` 期望 LF。
- **`.gitattributes` 仓库根目录已就位**：
  - `* text=auto eol=lf`（默认 LF）
  - `*.bat / *.cmd / *.ps1` 例外（CRLF）
  - `*.java / *.kt / *.ts / *.vue / *.md / *.sql / *.yml / *.xml / *.properties` 强制 LF
  - 二进制文件标 `binary`（jar / class / png / pdf / zip / 字体）
- **历史 normalize 注释**：`.gitattributes` 顶部明确"不触发历史 normalize——只对未来新编辑生效"。

### 2.4 漂移来源（推测）

可能触发链（按可能性排序）：

1. **早期入库时部分文件以 CRLF 提交**（Windows 编辑器 / IDE 默认行为），后引入 `.gitattributes`，但 Git **不会自动改写历史 / index**，导致 working tree 与 index 的 stat 不一致。
2. **某次跨平台操作**（zip 解压 / IDE 批量保存 / 远程拷贝）触发行尾符重置。
3. **CRLF 检测器（autocrlf / safecrlf）**首次启用时的存量扫描。

无论来源，**当前事实**：working tree 是 CRLF，index/HEAD 是 LF，**纯行尾符漂移，无业务语义变化**。

---

## 3. 处理方案选择（决策矩阵）

| 选项 | 描述 | 风险 | 决定 |
|---|---|---|---|
| A. 立即全量 commit | `git add -A && git commit "chore: normalize EOL to LF per .gitattributes"` | 一次提交 946 文件 / 12.6 万行 diff / 噪音巨大，污染 git log；可能误带未保存编辑 | ❌ 否决 |
| B. 立即 git checkout -- 还原 | `git checkout -- .` 全部还原 | 强清，丢失任何正在进行的真实编辑（即便本次无真实编辑） | ❌ 否决 |
| C. **长期挂账** | 保留 working tree ` M` 状态，**后续精确 add** 真实改动文件 | 需要每次 git 操作小心，避免 `git add . / git add -A` | ✅ **采用** |
| D. 提交空 .gitattributes 加固 + ignore EOL diff | `core.autocrlf=false` + 全量 stage + 不写入 | 等价 A 的污染 | ❌ 否决 |

### 3.1 决策 = C（长期挂账）

**核心理由**：

1. 946 个文件 100% 是 CRLF→LF 漂移，无业务改动（`diff -w` 为空铁证）。
2. 后续还有 **I001 / I002 / I008 / I011 / I012 / I005-C2 / I005-C1 / I005-C4 / I003 / I016** 等需要修复（至少 7+ 项）。
3. 若选 A，一次性 commit 12.6 万行 EOL，会让"真实修复"在 git log 中被噪音淹没，**反而增加回滚难度**。
4. 长期挂账可以与后续修复合并提交：**每次只 add 真实改动的具体文件**，让 git log 保持"小而精"。

---

## 4. 操作规则（硬性）

### 4.1 允许的操作

✅ 显式 `git add <具体文件>`：仅 stage 真实修复的文件
✅ 显式 `git add <目录>/<文件>`：精确路径
✅ `git status` / `git diff` / `git diff --stat`：只读核查
✅ `git restore --staged <具体文件>`：精准 unstage
✅ `git checkout HEAD -- <具体文件>`：还原单文件（前提是已确认 working tree 该文件无真实改动）

### 4.2 禁止的操作

🚫 **`git add .`**：会把 946 个 EOL 文件全部 stage，污染下一次 commit
🚫 **`git add -A`**：同上，且会包含 untracked
🚫 **`git add audit/`、`git add backend/`、`git add frontend/`**：粒度太粗，会包入 EOL
🚫 **`git checkout -- .`**：强清整个 working tree
🚫 **`git stash --include-untracked` + 全量 apply**：会带入 EOL

### 4.3 每次 commit 前自检清单

每次 `git commit` 前必跑：

```powershell
git status --short
# 期望输出：仅 ≤ 5 个具体路径，全部为待修复文件
# 若出现 ≥ 10 个路径，立即暂停，人工筛选

git diff --cached --stat
# 期望输出：插入 / 删除 < 100 行；insertions ≠ deletions（EOL 必对称）
# 若 insertions == deletions 且数字 > 50，立即 `git restore --staged .` 后精确 add
```

---

## 5. 后续 EOL 一次性清理（远期）

**触发条件**：所有 P0/P1/P2/P3 修复落地后、Phase 2.x 收口前。

**清理步骤**（不在本轮执行）：

```powershell
# 1. 确认所有真实修复都已 commit
git status --short   # 应为空或仅有 untracked

# 2. 一次性把 946 个文件 normalize 到 LF
git add -A           # 此时工作区已干净，-A 不会再带入虚假改动
git commit -m "chore: normalize EOL to LF per .gitattributes (946 files, post Phase 2.x)"

# 3. 验证
git log --oneline -1
git show --stat HEAD | Measure-Object  # 应见 946 files changed
```

**前置条件**：

- 工作区除 946 个 EOL 外**无任何真实未提交改动**
- `git diff -w --shortstat` 必须为空
- 所有 `audit/post-audit/phase-2.X-*.md` 已落库

---

## 6. 已验证的关键事实（防回归）

| ID | 事实 | 证据 |
|---|---|---|
| F-EOL-1 | 946 个文件全是 CRLF→LF 漂移 | `git diff --shortstat` 显示 126105 == 126105 |
| F-EOL-2 | `diff -w` 干净（无业务改动） | `git diff -w --shortstat` 输出为空 |
| F-EOL-3 | Git 警告统一定位 CRLF→LF | stderr 全为 `CRLF will be replaced by LF` |
| F-EOL-4 | I036 修复未受影响（已 commit 干净） | `GoogleMapsService.java` + `google-maps/index.vue` 在 `git status` 中 ` M` 但 `git diff --stat` 输出为空 |
| F-EOL-5 | `.gitattributes` 仓库根目录已配置 LF | `* text=auto eol=lf` + 各类后缀强制 LF |

---

## 7. 决策签字

- **决策人**：项目主理（用户）
- **执行人**：Cursor Assistant
- **生效时刻**：2026-09-29 23:45 (UTC+8)
- **下次复核**：Phase 2.x 收口前（届时按 §5 一次性清理）
