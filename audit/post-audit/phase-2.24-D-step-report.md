> # ⛔ 本报告已废弃 — 结论被推翻，请勿引用
>
> **废弃日期：2026-10-05**
> **取代文档：`audit/post-audit/phase-2.24-staged-eol-diagnosis.md`**
>
> 本报告曾得出的「50 个 staged 文件是真实代码改动」以及「第二个 M 是 mtime 虚假状态」
> 等结论，已被后续严格字节级诊断**推翻**。字节级证据显示：
> staged 50 文件 = 11514 insertions / 11514 deletions（完全对称），
> `git diff --cached --ignore-all-space` 为**空** → 100% 是 CRLF → LF 的纯 EOL 归一化。
> 正确的工作区真实规模是 **932 文件 / 122587 行**纯 EOL 噪音。
>
> 保留本文件仅为审计留痕（不删除证据）。**任何后续决策请以
> `phase-2.24-staged-eol-diagnosis.md` 为唯一事实来源。**

---

# D 步骤完成报告 — 清 stash + EOL

> 执行时间：2026-10-04 00:22
> HEAD：69bef62 docs(audit): Post-Audit 二期封存 + ARCHIVE 更新
> Branch：main

---

## D-1 清 stash

| 项 | 状态 |
|---|---|
| `git stash list` | **空** |
| 需要 `git stash clear` | 不需要 |
| 结论 | ✅ 已完成（无 stash 可清） |

---

## D-2 EOL 现状扫描

### .gitattributes 配置

`.gitattributes` 已存在并已配置完整策略：

```text
* text=auto eol=lf                           # 默认文本 LF
*.bat / *.cmd / *.ps1  text eol=crlf        # Windows 脚本强制 CRLF
*.jar / *.class / *.png ...  binary         # 二进制标 binary
*.sql / *.yml / *.yaml / *.xml / *.properties  text eol=lf
*.java / *.kt / *.js / *.ts / *.tsx / *.vue / *.md  text eol=lf
*.md  whitespace=-trailing-space,-space-before-tab
```

### Git 配置

```text
core.autocrlf = false
core.eol       = (空)
```

### 行尾分布（抽样 200 个 tracked 文件）

| 行尾 | 数量 | 占比 |
|---|---|---|
| LF only | 14 | 7.0% |
| **CRLF** | **183** | **91.5%** |
| Mixed | 2 | 1.0% |
| None | 1 | 0.5% |

**说明**：抽样里 91.5% 是 CRLF，但 .gitattributes 强制 LF — 这是一个**历史遗留问题**：
- 这些 CRLF 文件是在 `.gitattributes` 引入**之前**已 tracked 的，git 不会自动重整
- **不会**影响本次工作区状态（除非执行 `git add --renormalize .`）

### "Mixed" 文件复查

| 文件 | 头部 1024 字节 | 真实情况 |
|---|---|---|
| `audit/ARCHIVE.md` | CRLF=40 / LF=0 | **不是 mixed，是纯 CRLF**（可能在文件末尾或 >1024 字节位置有差异） |
| `trace-35abe9ee-late-list.txt` | LF=9 / CRLF=0 | **不是 mixed，是纯 LF** |

### 51 modified 跟 EOL 的关系

用 `git diff --ignore-cr-at-eol --numstat` 严格判断：

| 类别 | 数量 |
|---|---|
| 纯 CRLF/LF 差异（真 EOL 污染） | **0** |
| 实质性内容改动 | **50** |

**结论**：51 modified 跟 EOL **完全无关**。

---

## 50 个 modified 的真实身份

通过 `porcelain=v2` + `git diff --cached` 联合验证：

| 字段 | 真相 |
|---|---|
| `git status` 显示 | `MM` |
| 第一个 M（X） | INDEX ≠ HEAD（**真 staged 改**） |
| 第二个 M（Y） | worktree 文件 mtime ≠ index entry mtime（**mtime 元数据差异，不是内容差异**） |
| `git diff`（worktree vs INDEX） | **空**（worktree 内容 = INDEX 内容） |
| `git diff --cached`（INDEX vs HEAD） | **177 行 diff**（真改动） |
| 文件大小变化示例 | HEAD 14048B → INDEX 14016B（-32 字节） |

**实质**：50 个文件**已 staged 但未 commit**，内容确实有真实改动；Y 列的 M 是 touch 之后 mtime 不一致导致的**虚假 modified**。

---

## 13 个 untracked 分组

| 分组 | 数量 | 文件 |
|---|---|---|
| `audit/post-audit/` | 1 | `phase-2.24-eol-policy.md`（注：还有 `phase-2.21-stash-cleanup-log.md` 未在原 status 中显示） |
| `audit/static-gap/` | 5 | `i088-3-*.txt/md` |
| `tools/` | 4 | `check2.sh` / `check3.sh` / `check_head_wt.sh` / `check_head_wt.py` |
| `tmp/` | 1 | `out.txt` |
| `中文名/` | 1 | `"原文"`（中文名文件） |
| `other/` | 1 | — |

---

## D 步骤结论

| 子步骤 | 状态 | 备注 |
|---|---|---|
| **D-1 清 stash** | ✅ 完成 | stash 列表为空 |
| **D-2 EOL** | ✅ 完成（仅扫描） | .gitattributes 已就位，183 个历史 CRLF 文件不主动 renormalize（避免污染 review），51 modified 与 EOL 无关 |

**D 步骤未对工作区做任何修改**（仅做信息扫描 + 报告）。

---

## 工作区最终状态

```text
M (staged):  50   ← 真实代码改动，git diff --cached 有内容
M (wt only):  0
MM (混合):    50   ← 同上（X 真改 + Y mtime 虚假改）
?? (新文件):  13
total:        63

index 跟 HEAD 关系: 不一致（50 个文件已 staged）
worktree 跟 index 关系: 内容一致，仅 mtime 不一致
```

---

## 下一步选项（A/B/C）

需要用户在以下选项中选择如何处理 50 个 staged 改动 + 13 个 untracked：

| 选项 | 含义 | 影响 |
|---|---|---|
| **A** | 提交 50 个 staged 改动（按现有分组拆 commit） | 产生新 commit，清理 worktree |
| **B** | `git reset HEAD` 把 staged 退回 worktree + `git checkout --` 撤销内容改动 | 工作区回到 HEAD 干净状态（**会丢 50 个文件的真实改动**） |
| **C** | `git stash push -u` 把 50 个 staged + 13 个 untracked 全部 stash | 工作区完全干净，stash 里保留（之后可 `stash pop`） |
| **其他** | 用户自定义 | — |

⚠️ **D 步骤没有改任何业务代码**，仅做了信息扫描。等用户在 A/B/C 中选择。
