# F141 — 备份可恢复性验证（Backup Restore Verification）

> 验证日期：2026-10-25 19:00–21:10
> 验证类型：**运行实测**（本地 Docker 验收环境，把备份文件真实灌进 MySQL 临时 schema）
> 结论：**VERIFIED**（官方链路产物可恢复；人工 PowerShell 产物不可恢复）
> 关联问题单：I127（备份文件编码损坏）、I128（库内 CJK 编码污染）、I116（备份恢复演练证据缺失）、I123（备份自动化从未运行）
> 绑定基线：commit `8302d07672cd37543bf4b8d24efedea720eb3a42`（2026-10-24 18:18:54）/
> DB `meiyetec@localhost:3307`（MySQL 8.0.46，容器 `meiyetec-mysql`）/
> DB snapshot：`backup/meiyetec-20261025-recovery.sql`（full dump，12,199,862 字节）/
> env：`audit/baseline/env.md`（其 HEAD 字段仍为 `656caa7…`，已滞后，本次未修改）

---

## 1. 验证对象与结论

| # | 对象 | 路径 | 状态 | 判定依据 |
|---|---|---|---|---|
| 1 | 官方 02:00 备份（控制组） | `backup/meiyetec_20261025_020004.sql` | **VERIFIED** | 恢复退出码 `0`，281 张表全量落地，stderr 无 SQL 错误 |
| 2 | 本次重出备份 | `backup/meiyetec-20261025-recovery.sql` | **VERIFIED** | UTF-8 无 BOM、281 表、乱码量与官方备份逐项一致；内容一致性以 #1 的恢复实测背书 |
| 3 | 18:08 人工备份（事故组） | `backup/before-batch1-20261025-180825.sql.UNUSABLE` | **BROKEN** | UTF-16LE + GBK 双重转码；引号被吞；回环修复后仍在第 97 行 `ai_ltv_model` 解析失败 |
| 4 | 备份内容与生产库一致性 | `meiyetec` vs 恢复库 | **VERIFIED** | 281 张表逐表行数比对，**零差异** |
| 5 | 库内中文数据 | `sys_permission` / `sys_role` / `sys_user` | **PARTIAL-NON-BLOCKER** | 15 行中文已污染，备份忠实搬运 → 见 I128 |

> ⚠️ 本次验证在**本地 Docker 验收环境**完成，**未连接生产库、未打开浏览器**；
> 结论仅覆盖该环境与该时点的备份产物。

---

## 2. 三份 dump 指纹对照

| 文件 | 大小（字节） | 首字节 | 编码 | `CREATE TABLE` | `?` 字符数 | 含 `?` 行数 |
|---|---|---|---|---|---|---|
| `meiyetec_20261025_020004.sql`（官方 02:00） | 11,976,582 | `2d 2d 20 4d` | UTF-8 无 BOM | 281 | 1,207 | 235 |
| `meiyetec-20261025-recovery.sql`（本次重出） | 12,199,862 | `2d 2d 20 4d` | UTF-8 无 BOM | 281 | 1,207 | 235 |
| `before-batch1-20261025-180825.sql.UNUSABLE`（事故件） | 23,804,424 | `ff fe 2d 00` | **UTF-16LE** | 281 | **15,026** | **805** |

- 事故件大小 ≈ 正常 dump × 2（UTF-16LE 翻倍特征），`ff fe` = UTF-16LE BOM；
- 两份正常产物的 `?` 计数完全相同 → 13,819 个差值来自转码，**不是数据问题**；
- 两份正常产物的 `CREATE TABLE` 数均为 281 → 表结构完整。

---

## 3. 控制组恢复实测

### 3.1 命令与退出码

```powershell
cmd /c "docker exec -i meiyetec-mysql mysql -uroot -p*** --default-character-set=utf8mb4 audit_restore_ok < d:\QiaoPM\backup\meiyetec_20261025_020004.sql"
==== exit=0 ====
```

stderr（全部内容）：

```
mysql: [Warning] Using a password on the command line interface can be insecure.
```

→ **无任何 SQL 错误**。

### 3.2 落地结果

| 校验项 | 结果 |
|---|---|
| 恢复后表数 | `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='audit_restore_ok'` → **281**（与 dump 内 `CREATE TABLE` 数一致） |
| 中文无损 | `audit_restore_ok.sys_permission` 中 `permission_code='member:list'` → `permission_name = 查看会员` |
| 行数一致性 | 281 张表逐表 `COUNT(*)` 与 live `meiyetec` 比对 → **NO ROW-COUNT DIFF across all 281 tables** |

### 3.3 事故组恢复实测

| 步骤 | 结果 |
|---|---|
| 原始事故文件直接灌入 | 解析失败（`COMMENT '涓婚€?,` 处引号不闭合，后续语句被吞进字符串） |
| 做 GBK→UTF-8 回环修复后灌入 | **仍在第 97 行 `ai_ltv_model` 处失败**：`ERROR near '1', operator_id bigint DEFAULT NULL, operator_name varchar(64) DEFAULT N'` |

→ `?` 已吃掉原始字节，**不可逆**，该文件只能作为事故证据保留。

---

## 4. 根因实测

| 环 | 实测 |
|---|---|
| PowerShell 版本 | `PSVersionTable.PSVersion` → `5.1.26100.9711` |
| `>` 重定向编码 | `cmd /c "echo hello" > f.txt` → 首字节 `ff fe 68 00 65 00 6c 00` → **UTF-16LE** |
| stdout 解码 | 控制台代码页 GBK/936 → mysqldump 的 UTF-8 字节流被按 GBK 解读 |
| GBK 配对错位 | ASCII 字节 0x40–0x7E 在 GBK 中是合法首字节 → 吃掉相邻字节 → SQL 闭合引号被吞 |

**正确出口**（本次采用）：容器内 `mysqldump` 写文件 → `docker cp` 拷出（纯字节复制）。
官方脚本 `scripts/backup-db-docker.ps1` 亦采用 `cmd /c` 原始字节重定向 + `[Console]::OutputEncoding = UTF8`，故其产物完好。

---

## 5. 环境清理记录

| 对象 | 处置 |
|---|---|
| `audit_restore_test`（事故组临时库） | ✅ 已 `DROP DATABASE` |
| `audit_restore_ok`（控制组临时库） | ✅ 已 `DROP DATABASE` |
| `audit_scan`（列级扫描临时库） | ✅ 已 `DROP DATABASE` |
| `%TEMP%` 下临时 SQL / 输出文件 | ✅ 已删除 |
| live 库 `meiyetec` | ✅ 未被修改（281 张表原样；本次为只读验证 + 临时库） |

---

## 6. 遗留与建议

1. **恢复校验尚未工具化**：当前只有本次人工实测结论；建议后续把「备份 → 临时 schema → 全表行数比对」做成独立脚本并纳入 PR 必跑清单（用户 2026-10-25 决定：暂缓，待 batch1 执行稳定后再建）。
2. **官方脚本可加编码校验**：`scripts/backup-db-docker.ps1` 现有「文件 > 1 MB」「`CREATE TABLE` ≥ 100」两项校验，可再加「首字节非 `FF FE`」。
3. **库内 CJK 污染需单独清洗**：见 I128，恢复备份不会自愈。

---

## 7. 未确认项

1. 18:08 事故文件的具体执行命令与操作者 —— **未确认**（仓库内无匹配脚本，审计转录未检索到）。
2. 该文件当时是否曾被当作有效备份使用 —— **未确认**。
3. 生产环境是否存在同类导出隐患 —— **未确认**（本单只覆盖本地 Docker 验收环境）。
4. `audit/baseline/env.md` 记录的 HEAD 滞后原因 —— **未确认**。
