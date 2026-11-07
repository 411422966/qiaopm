# F146 — I128-b 根因批量加固：`db/sql` 全量补 `SET NAMES utf8mb4`

> **状态**：VERIFIED
> **批次**：I128-b 根因批量加固（2026-11-08）
> **授权**：用户 2026-11-08 批准 `-Apply`（DryRun 报告 176/176 通过后）
> **范围**：`backend/src/main/resources/db/sql/`（顶层 173 + `audit-subset/` 3 = 176 个 `.sql`）
> **不含**：Flyway `backend/src/main/resources/db/migration/V1__init_baseline.sql`（改动会破坏 checksum）

## 1. 做了什么

为 `db/sql` 下每个 `.sql` 的语句区最前面补 `SET NAMES utf8mb4;`，
使 Docker 初始化（`/docker-entrypoint-initdb.d`）与 Windows 本地 `mysql < file.sql`
无论客户端默认字符集是 latin1 还是 cp936，都按 UTF-8 解释文件字节。

- **165** 个文件新增（`SET NAMES utf8mb4;` + 空行，共 2 行）
- **11** 个文件原本已含 → SKIP，**逐字节未触碰**
- 每个被改文件 `git diff --numstat` 恰好 **`+2 / -0`**

## 2. 证据清单

| 文件 | 内容 |
|---|---|
| `01-dryrun-report.txt` | 审核前的 DryRun 逐文件报告（176 行） |
| `02-apply-report.txt` | `-Apply` 实写的逐文件报告（176 行） |
| `03-rescan-and-numstat-verification.txt` | 落盘后**独立重扫**：缺 SET NAMES=0 / BOM=0 / 非法 UTF-8=0 / 行尾 CRLF=164 LF=12 / numstat 165 个全 `+2 -0` |
| `04-hex-compare-latin1-vs-utf8mb4.txt` | latin1 客户端 vs utf8mb4 客户端两库落库 HEX 比对（148/148 逐字节全等）+ 6 条新增中文菜单逐字节校验 |
| `05-counterfactual-no-set-names.txt` | **反证**：改动前原文件（无 SET NAMES）+ 同一 latin1 客户端 → 143/143 行中文双重编码乱码（`首页` → `é¦–é¡µ`） |
| `03-temprun-latin1-client.txt` | 临时库实跑原始终端输出（6 个文件逐个 exit code + SQL 输出） |

## 3. 验证结论

| 检查项 | 结果 | 期望 |
|---|---|---|
| 缺 `SET NAMES utf8mb4` 的文件数 | **0** | 0 |
| UTF-8 BOM 文件数 | **0** | 0 |
| 非法 UTF-8 文件数 | **0** | 0 |
| 行尾分布 | **CRLF=164 / LF=12** | 与改前一致 |
| `git diff --numstat` 非 `+2/-0` 条目 | **0** | 0 |
| 临时库实跑（6 文件，含 3 类角色） | **5 通过 + 1 依赖/源文件既有缺陷** | 见下 |
| latin1 vs utf8mb4 落库字节比对 | **148/148 全等** | 全等 |
| 反证（无 SET NAMES） | **143/143 行乱码** | 乱码 |

## 4. 实跑明细

在 `meiyetec-mysql`（MySQL 8.0.46）内建 3 个一次性临时库执行；
测试副本仅把 `USE meiyetec` / `CREATE DATABASE ... meiyetec` 重定向到临时库，
`SET NAMES` 行与正文逐字节未改（`docker cp` 传输）。

| # | 文件 | 角色 | 结果 |
|---|---|---|---|
| 1 | `init_m01_auth.sql` | 建库 + 用户/角色/权限种子 | ✅ exit 0，中文正常 |
| 2 | `create_missing_tables_b4b5.sql` | 纯 DDL（23 表） | ✅ exit 0 |
| 3 | `init_rbac_v2.sql` | 原含 SET NAMES 的 SKIP 文件（补依赖） | ✅ exit 0 |
| 4 | `audit-subset/migrate_ext_22_subset.sql` | ALTER ×2 表 4 列 | ✅ exit 0 |
| 5 | `migrate_p3_pkg_order_version.sql` | ALTER + SHOW COLUMNS | ✅ exit 0（`version int NO 0`） |
| 6 | `expire_test.sql` | UPDATE + SELECT | ✅ exit 0 |
| 附 | `add_6_missing_menus_20260819.sql` | 中文菜单 + 路由绑定 | ⚠️ A 段中文 6/6 正确；B 段 ERROR 1136（源文件既有缺陷，与本批无关） |

## 5. 环境清理

- 3 个临时库 `tmp_setnames_test` / `tmp_setnames_ref` / `tmp_setnames_nofix` 已 `DROP`
- 容器内 `/tmp/st_test` `/tmp/st_ref` `/tmp/st_orig_init.sql` 已删除
- 本地临时目录 `tmp-setnames-run/` 已删除
- **真实 `meiyetec` 库未被本次测试写入**（测试副本库名已重定向）

## 6. 已知遗留（未修，超出本批授权范围）

`add_6_missing_menus_20260819.sql` **L40**：
`INSERT IGNORE INTO sys_menu_permission (permission_id, route_path, icon, sort_order, hidden, keep_alive)`
声明 6 列但 `SELECT` 只给 4 个值 → `ERROR 1136 (21S01) Column count doesn't match value count at row 1`。
该缺陷在改动前的原文件中即存在（两个临时库报同一错可证），**与 `SET NAMES` 无关**。
→ 待另立 issue 处理，本批不改。

## 7. 关联

- `audit/issues/I128-b-charset-remaining.md` §10 —— 本批完整记录
- `audit/evidence/F146-i128-b-set-names-db-sql/add-set-names.ps1` —— 一次性迁移工具的**只读副本**（文件头已标「非正式工具」）
- `scripts/add-set-names.ps1` —— 原始工具位置，⚠️ **未入库**（被 `.gitignore` L143 的全局 `*.ps1` 规则拦截；`.gitignore` 属治理保护文件，本批未改）
