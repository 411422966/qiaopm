# 50 staged 诊断报告 — 纯 EOL 归一化，非业务改动

> 诊断时间：2026-10-04 23:16
> HEAD：`69bef62f22eb5a62d1b6cb3dc1b37d2afc6444de`（2026-10-04 00:20:20）
> Branch：main
> 命令约束：全程只读，未执行 reset / checkout / stash / add / commit

---

## 一、诊断 1 — 全部 staged diff

```
git diff --cached --stat
```

```
 frontend/meiyetec-web/e2e/rbac-permissions.spec.ts |  354 ++--
 frontend/meiyetec-web/src/api/birthday.ts          |  144 +-
 frontend/meiyetec-web/src/api/coupon.ts            |   76 +-
 frontend/meiyetec-web/src/api/expiry-tracking.ts   |  154 +-
 frontend/meiyetec-web/src/api/invite.ts            |  420 ++--
 frontend/meiyetec-web/src/api/plugin.ts            |   60 +-
 frontend/meiyetec-web/src/api/point.ts             |   70 +-
 frontend/meiyetec-web/src/api/treatment-report.ts  |  228 +-
 frontend/meiyetec-web/src/components/SubmitButton.vue |  120 +-
 frontend/meiyetec-web/src/directives/permission.ts |   72 +-
 frontend/meiyetec-web/src/shims.d.ts               |   98 +-
 frontend/meiyetec-web/src/utils/idempotency.ts     |  128 +-
 frontend/meiyetec-web/src/utils/request.ts         |   30 +-
 frontend/meiyetec-web/src/views/403.vue            |   80 +-
 frontend/meiyetec-web/src/views/biz/treatment-report.vue | 2222 +++---
 frontend/meiyetec-web/src/views/booking/no-show.vue |  612 +++---
 frontend/meiyetec-web/src/views/booking/resource.vue | 842 +++--
 frontend/meiyetec-web/src/views/commission/batch.vue | 668 +++---
 frontend/meiyetec-web/src/views/commission/reversal.vue | 528 ++---
 frontend/meiyetec-web/src/views/fiscal/config.vue |  504 ++---
 frontend/meiyetec-web/src/views/fiscal/list.vue    |  556 +++---
 frontend/meiyetec-web/src/views/intel/rfm/ResultPanel.vue | 158 +-
 frontend/meiyetec-web/src/views/intel/rfm/RfmHistoryChart.vue | 360 ++--
 frontend/meiyetec-web/src/views/intel/rfm/SegmentDrawer.vue | 362 ++--
 frontend/meiyetec-web/src/views/m09-invite/index.vue | 1414 ++++++-------
 frontend/meiyetec-web/src/views/mh/consent-h5.vue  |  332 +--
 frontend/meiyetec-web/src/views/mkt/birthday.vue   | 1272 +++++------
 frontend/meiyetec-web/src/views/mkt/channel.vue    |  360 ++--
 frontend/meiyetec-web/src/views/mkt/coupon.vue     |  814 +++----
 frontend/meiyetec-web/src/views/mkt/effect.vue     |  228 +-
 frontend/meiyetec-web/src/views/mkt/points.vue     |  458 ++--
 frontend/meiyetec-web/src/views/mkt/promotion.vue  |  668 +++---
 frontend/meiyetec-web/src/views/mkt/template.vue   |  332 +--
 frontend/meiyetec-web/src/views/mkt/touch-task.vue |  532 ++---
 frontend/meiyetec-web/src/views/org/brand.vue      |  472 +---
 frontend/meiyetec-web/src/views/pay/dcep.vue       |  204 +-
 frontend/meiyetec-web/src/views/plugin/index.vue   |  352 +--
 frontend/meiyetec-web/src/views/pos/reconciliation.vue | 284 +--
 frontend/meiyetec-web/src/views/report/biz.vue     |  868 ++++----
 frontend/meiyetec-web/src/views/report/export.vue  |  610 +++---
 frontend/meiyetec-web/src/views/setting/index.vue  |   68 +-
 frontend/meiyetec-web/src/views/stock/components/DualVerifyDialog.vue | 390 ++--
 frontend/meiyetec-web/src/views/stock/consumption.vue | 596 +++---
 frontend/meiyetec-web/src/views/stock/crossStore.vue | 1216 +++++------
 frontend/meiyetec-web/src/views/stock/list.vue     |  624 +++---
 frontend/meiyetec-web/src/views/stock/record.vue   |  474 ++---
 frontend/meiyetec-web/src/views/stock/trace.vue    |  176 +-
 frontend/meiyetec-web/src/views/system/role.vue    |  686 +++---
 frontend/meiyetec-web/src/views/system/setting.vue |  380 ++--
 frontend/meiyetec-web/src/views/wallet/transactions.vue | 372 +--
 50 files changed, 11514 insertions(+), 11514 deletions(-)
```

**关键信号**：`insertions == deletions == 11514`，**完全对称**。真实代码改动不可能出现这种对称性。

---

## 二、诊断 2 — 忽略空格后的 diff

```
git diff --cached --ignore-all-space --stat
```

```
（输出为空）
```

```
git diff --cached --ignore-all-space --shortstat
（输出为空）
```

```
git diff --cached --ignore-all-space --numstat
（输出为空）
```

**50 个文件在忽略空白后，零差异。**

---

## 三、诊断 3 — 差集判定

| 集合 | 定义 | 数量 |
|---|---|---|
| A | 诊断 1（`--cached --numstat`）有变更 | **50** |
| B | 诊断 2（`--cached --ignore-all-space --numstat`）有变更 | **0** |
| **A − B** | **纯 EOL / 纯空白改动** | **50** |
| **A ∩ B** | **真实内容改动** | **0** |

### 纯 EOL 文件逐个增删行数（全部对称）

| # | 文件 | +/- |
|---|---|---|
| 1 | `frontend/meiyetec-web/e2e/rbac-permissions.spec.ts` | 177/177 |
| 2 | `frontend/meiyetec-web/src/api/birthday.ts` | 72/72 |
| 3 | `frontend/meiyetec-web/src/api/coupon.ts` | 38/38 |
| 4 | `frontend/meiyetec-web/src/api/expiry-tracking.ts` | 77/77 |
| 5 | `frontend/meiyetec-web/src/api/invite.ts` | 210/210 |
| 6 | `frontend/meiyetec-web/src/api/plugin.ts` | 30/30 |
| 7 | `frontend/meiyetec-web/src/api/point.ts` | 35/35 |
| 8 | `frontend/meiyetec-web/src/api/treatment-report.ts` | 114/114 |
| 9 | `frontend/meiyetec-web/src/components/SubmitButton.vue` | 60/60 |
| 10 | `frontend/meiyetec-web/src/directives/permission.ts` | 36/36 |
| 11 | `frontend/meiyetec-web/src/shims.d.ts` | 49/49 |
| 12 | `frontend/meiyetec-web/src/utils/idempotency.ts` | 64/64 |
| 13 | `frontend/meiyetec-web/src/utils/request.ts` | 15/15 |
| 14 | `frontend/meiyetec-web/src/views/403.vue` | 40/40 |
| 15 | `frontend/meiyetec-web/src/views/biz/treatment-report.vue` | 1111/1111 |
| 16 | `frontend/meiyetec-web/src/views/booking/no-show.vue` | 306/306 |
| 17 | `frontend/meiyetec-web/src/views/booking/resource.vue` | 421/421 |
| 18 | `frontend/meiyetec-web/src/views/commission/batch.vue` | 334/334 |
| 19 | `frontend/meiyetec-web/src/views/commission/reversal.vue` | 264/264 |
| 20 | `frontend/meiyetec-web/src/views/fiscal/config.vue` | 252/252 |
| 21 | `frontend/meiyetec-web/src/views/fiscal/list.vue` | 278/278 |
| 22 | `frontend/meiyetec-web/src/views/intel/rfm/ResultPanel.vue` | 79/79 |
| 23 | `frontend/meiyetec-web/src/views/intel/rfm/RfmHistoryChart.vue` | 180/180 |
| 24 | `frontend/meiyetec-web/src/views/intel/rfm/SegmentDrawer.vue` | 181/181 |
| 25 | `frontend/meiyetec-web/src/views/m09-invite/index.vue` | 707/707 |
| 26 | `frontend/meiyetec-web/src/views/mh/consent-h5.vue` | 166/166 |
| 27 | `frontend/meiyetec-web/src/views/mkt/birthday.vue` | 636/636 |
| 28 | `frontend/meiyetec-web/src/views/mkt/channel.vue` | 180/180 |
| 29 | `frontend/meiyetec-web/src/views/mkt/coupon.vue` | 407/407 |
| 30 | `frontend/meiyetec-web/src/views/mkt/effect.vue` | 114/114 |
| 31 | `frontend/meiyetec-web/src/views/mkt/points.vue` | 229/229 |
| 32 | `frontend/meiyetec-web/src/views/mkt/promotion.vue` | 334/334 |
| 33 | `frontend/meiyetec-web/src/views/mkt/template.vue` | 166/166 |
| 34 | `frontend/meiyetec-web/src/views/mkt/touch-task.vue` | 266/266 |
| 35 | `frontend/meiyetec-web/src/views/org/brand.vue` | 236/236 |
| 36 | `frontend/meiyetec-web/src/views/pay/dcep.vue` | 102/102 |
| 37 | `frontend/meiyetec-web/src/views/plugin/index.vue` | 176/176 |
| 38 | `frontend/meiyetec-web/src/views/pos/reconciliation.vue` | 142/142 |
| 39 | `frontend/meiyetec-web/src/views/report/biz.vue` | 434/434 |
| 40 | `frontend/meiyetec-web/src/views/report/export.vue` | 305/305 |
| 41 | `frontend/meiyetec-web/src/views/setting/index.vue` | 34/34 |
| 42 | `frontend/meiyetec-web/src/views/stock/components/DualVerifyDialog.vue` | 195/195 |
| 43 | `frontend/meiyetec-web/src/views/stock/consumption.vue` | 298/298 |
| 44 | `frontend/meiyetec-web/src/views/stock/crossStore.vue` | 608/608 |
| 45 | `frontend/meiyetec-web/src/views/stock/list.vue` | 312/312 |
| 46 | `frontend/meiyetec-web/src/views/stock/record.vue` | 237/237 |
| 47 | `frontend/meiyetec-web/src/views/stock/trace.vue` | 88/88 |
| 48 | `frontend/meiyetec-web/src/views/system/role.vue` | 343/343 |
| 49 | `frontend/meiyetec-web/src/views/system/setting.vue` | 190/190 |
| 50 | `frontend/meiyetec-web/src/views/wallet/transactions.vue` | 186/186 |

**50/50 文件 insertions 恒等于 deletions** — 没有任何一个文件有多一行或少一行。

---

## 四、诊断 4 — 字节级内容验证

M 集合为空（真改动文件数 = 0），因此改为对纯 EOL 样本做**原始字节级**验证。

样本：`frontend/meiyetec-web/src/api/plugin.ts`
方法：`git diff --cached -U0` 经 cmd 原始字节重定向落盘，逐行统计 CR 存在性。

```
diff 文件字节数: 3153

统计:
  '-' 行(HEAD 旧版) 带 CR : 30
  '-' 行(HEAD 旧版) 无 CR : 0
  '+' 行(index 新版) 带 CR: 0
  '+' 行(index 新版) 无 CR: 30
  hunk 头 带CR/无CR       : 0 / 1

==> 结论: HEAD 版 = CRLF  ->  index 版 = LF
```

配对样例（代码逐字相同，仅行尾不同）：

```
- [CRLF] -import request from './request'
+ [LF  ] +import request from './request'
- [CRLF] -
+ [LF  ] +import request from './request'
- [CRLF] -/**
+ [LF  ] +/**
```

---

## 五、诊断 5 — 时间与规模

### 5a. `git status --porcelain=v2 | findstr "^1"`

```
共 945 条 type-1 记录
样例:
  1 .M N... 100644 100644 100644 5c419c70... 5c419c70... audit/README.md
  1 .M N... 100644 100644 100644 62bc1a4c... 62bc1a4c... audit/baseline/env.md
  1 .M N... 100644 100644 100644 8859c82f... 8859c82f... audit/baseline/preflight-batch-1.2.md
```

注：`porcelain=v2` **不含 mtime 字段**，只能给 hash。改用文件系统 mtime + `.git/index` mtime 推断 add 时刻。

### 5b. worktree 文件 mtime 分布

```
有效样本: 924

按小时分桶:
  10-04 23  924  ############################################################

最早 5 个:
  10-04 23:11:59  audit/evidence/F006-sync-log/result.md
  10-04 23:11:59  audit/evidence/F074-ecommerce-adapter-router/result.md
  10-04 23:11:59  backend/src/main/resources/db/sql/init_rbac_v3_0_p3_04.sql
  10-04 23:11:59  audit/post-audit/phase-2.3-batch-2-apply-log.md
  10-04 23:11:59  audit/evidence/F009-sync-cross-tenant/result-5A-supplement.md

最晚 5 个:
  10-04 23:12:37  audit/post-audit/phase-2.18-I084-report.md
  10-04 23:12:37  backend/src/main/resources/db/sql/migrate_coupon_001_add_tenant_id.sql
  10-04 23:12:37  frontend/meiyetec-web/e2e/audit/F111-F112-shadow.spec.ts
  10-04 23:12:37  backend/src/main/java/com/meiyetec/iot/DeviceCommandResult.java
  10-04 23:12:37  frontend/meiyetec-web/src/views/fiscal/list.vue
```

**全部 924 个文件落在 23:11:59 – 23:12:37 的 38 秒窗口内** — 一次批量写盘事件（构建/格式化/工具批量 touch）。

### 5c. `.git/index` mtime

```
.git/index LastWriteTime : 2026-10-04 23:18:00
.git/index Size          : 419741 bytes
```

### 5d. HEAD commit

```
commit : 69bef62f22eb5a62d1b6cb3dc1b37d2afc6444de
author : 2026-10-04 00:20:20
commit : 2026-10-04 00:20:20
```

---

## 六、更正：工作区实际规模远大于 50

初次快照读到的「63 行 / 50 MM」是**过期的 stat 缓存**结果。`.git/index` 在 23:18:00 被重写后，git 对全部文件重新计算 blob hash，才暴露出真实的 959 条记录。

### 6.1 当前 XY 分布

| XY | 含义 | 数量 |
|---|---|---|
| ` M` | 仅 worktree 变（index == HEAD） | **895** |
| `M ` | 仅 staged 变（worktree == index） | **48** |
| `MM` | 双区都不同 | **2** |
| `??` | 未跟踪 | **14** |
| — | **合计** | **959** |

`MM` 的 2 个文件：
- `frontend/meiyetec-web/src/api/plugin.ts`
- `frontend/meiyetec-web/src/views/plugin/index.vue`

### 6.2 三侧 diff 全量验证

| 范围 | `--numstat` | `--ignore-all-space --numstat` | 判定 |
|---|---|---|---|
| `git diff --cached`（index vs HEAD） | 50 文件 | **0** | 100% 纯 EOL |
| `git diff`（worktree vs index） | 882 文件 | **0** | 100% 纯 EOL |
| `git diff HEAD`（worktree vs HEAD） | 932 文件 | **0** | 100% 纯 EOL |

`git diff HEAD --shortstat`：

```
932 files changed, 122587 insertions(+), 122587 deletions(-)
```

**122587 / 122587 再次完全对称。**

### 6.3 git 自身警告（最直接证据）

git 对近乎每一个文件输出：

```
warning: in the working copy of 'audit/README.md', CRLF will be replaced by LF the next time Git touches it
warning: in the working copy of 'backend/.../XxxService.java', CRLF will be replaced by LF the next time Git touches it
warning: in the working copy of 'frontend/meiyetec-web/src/api/auth.ts', CRLF will be replaced by LF the next time Git touches it
...（共 895+ 条）
```

---

## 七、结论

| # | 结论 | 证据 |
|---|---|---|
| 1 | **50 staged 文件 100% 是纯 EOL 归一化，零业务代码改动** | `--ignore-all-space` 输出为空；每文件 ins == del |
| 2 | 变更方向是 **CRLF → LF** | 字节级：HEAD 版 30 行全带 CR，index 版 30 行全无 CR |
| 3 | 触发源是 **`.gitattributes` 的 `* text=auto eol=lf`** | `git check-attr`：`auth.ts: text: set / eol: lf` |
| 4 | **问题范围远超 50 文件** —— 实际是 932 文件 / 122587 行 | `git diff HEAD --shortstat` |
| 5 | 根因：**worktree 全仓库是 CRLF，但 `.gitattributes` 强制 LF** | git 对 895+ 文件发 `CRLF will be replaced by LF` 警告 |
| 6 | 批量写盘发生在 **23:11:59 – 23:12:37（38 秒）** | 924 个文件 mtime 高度集中 |
| 7 | `.git/index` 在 **23:18:00** 重写，触发全量重哈希 | 这就是 63 → 959 记录跳变的原因 |
| 8 | **无任何 Feature 受到影响** | 全量 `--ignore-all-space` 差集为空 |

### 风险清单

| 风险 | 等级 | 说明 |
|---|---|---|
| 122587 行 diff 淹没 code review | **BLOCKER** | 任何真实改动都会被 EOL 噪音埋掉 |
| 50 个已 staged 的 CRLF→LF 与 895 个未 staged 的不一致 | HIGH | 同一 commit 里会混入两种行尾状态 |
| 后续任何 `git add` 都会再产生新一批纯 EOL 变更 | HIGH | 23:11–23:12 的批量写盘已证明会反复发生 |
| `git blame` 在归一化前不可靠 | MEDIUM | CRLF 版本会让行号对不上 |

### 未确认项

- 23:11:59 – 23:12:37 的批量写盘是**哪个具体进程**触发的（构建 / Prettier / 外部脚本），本次只读诊断无法确定。
- `check2.sh` / `check3.sh` / `check_head_wt.py` / `check_head_wt.sh` 这 4 个未跟踪脚本的内容未审查（超出本次诊断范围）。
- `out.txt` / `原文件` 两个未跟踪文件内容未审查。

---

## 验证等级声明

本报告为 **L1 STATIC VERIFIED** — 全部结论来自 `git diff` / `git status` / 字节级读取的实际输出，可复验。**未**执行任何运行期验证，**不**构成对任何 Feature 的 VERIFIED 结论。
