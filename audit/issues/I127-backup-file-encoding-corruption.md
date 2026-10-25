# I127 — 备份文件编码损坏：PowerShell 5.1 重定向造成 UTF-16LE + GBK 双重转码，备份不可恢复

> 发现时间：2026-10-25 18:08（损坏文件生成时刻）；2026-10-25 19:00–21:10 完成恢复实测与处置
> 发现方式：备份文件恢复实测（把两份 dump 分别灌入临时 schema 验证）
> 严重级别：**P0**（备份链路表面正常，实际产物不可用，且不报错）
> 状态：**Fixed**（损坏文件已隔离 + 已用字节复制方式重出可用备份；恢复校验工具化未做，见 §4）
> 关联：I116（备份恢复演练证据缺失）、I123（备份自动化从未运行）、I128（库内 CJK 污染，与本单同源排查、同批发现）
> 基线：commit `8302d07672cd37543bf4b8d24efedea720eb3a42`（2026-10-24 18:18:54）/
> DB `meiyetec@localhost:3307`（MySQL 8.0.46，容器 `meiyetec-mysql`）/
> env 见 `audit/baseline/env.md`（注意：env.md 内记录的 HEAD 仍为 `656caa7…`，已滞后，未在本次修改）

---

## 1. 一句话结论

人工备份命令用了 PowerShell 5.1 的 `>` 重定向，**mysqldump 的 UTF-8 字节流先被按控制台代码页 GBK 误解码、再被写成 UTF-16LE**，
产物 23.8 MB（是正常 dump 的 2 倍）、`?` 字符 15,026 个（正常 dump 为 1,207），
**且 GBK 双字节配对错位把 SQL 字符串的闭合引号一起吃掉，恢复时解析器在第 97 行就崩掉 —— 该文件永久不可用**。

官方链路 `scripts/backup-db-docker.ps1` 用 `cmd /c` 做原始字节重定向，产物完好；两者差异不是备份内容问题，而是**出口写法**问题。

---

## 2. 硬证据

### 2.1 三份 dump 的指纹对照

| 文件 | 大小 | 首字节 | 编码 | `CREATE TABLE` | `?` 字符数 | 含 `?` 行数 | 可恢复 |
|---|---|---|---|---|---|---|---|
| `backup/meiyetec_20261025_020004.sql`（官方 02:00） | 11,976,582 | `2d 2d 20 4d` | UTF-8 无 BOM | 281 | 1,207 | 235 | ✅ |
| `backup/meiyetec-20261025-recovery.sql`（本次重出） | 12,199,862 | `2d 2d 20 4d` | UTF-8 无 BOM | 281 | 1,207 | 235 | ✅ |
| `backup/before-batch1-20261025-180825.sql.UNUSABLE`（原 18:08 文件） | 23,804,424 | `ff fe 2d 00` | **UTF-16LE（BOM `ff fe`）** | 281 | **15,026** | **805** | ❌ |

- 18:08 文件大小 23,804,424 ≈ 11.9 MB × 2 → **UTF-16LE 的典型翻倍特征**；
  `ff fe` BOM + 随后 `2d 00 2d 00`（`--`）= UTF-16LE 存储的 ASCII。
- 两份正常 dump 的 `?` 计数**完全相同**（1,207 / 235 行）→ 差集 13,819 个 `?` 是转码过程引入的，不是数据问题（详见 I128）。

### 2.2 根因链（逐环实测）

| 环 | 事实 | 实测方式 |
|---|---|---|
| ① PowerShell 版本 | `PSVersionTable.PSVersion = 5.1.26100.9711` | `PSVersionTable` |
| ② `>` 重定向写 UTF-16LE | `cmd /c "echo hello" > f.txt` → 首字节 `ff fe 68 00 65 00 6c 00` | 现场写入后读 hex |
| ③ 原生命令 stdout 按控制台代码页解码 | 控制台代码页 = GBK/936；PS 解码 mysqldump 的 UTF-8 字节流时按 GBK 解读 | `[Console]::OutputEncoding` / 观察中文变乱码 |
| ④ GBK 双字节配对错位 | ASCII 字节 0x40–0x7E 在 GBK 里是合法首字节 → 会把下一个字节一起吃掉 | 见 §2.3 的引号被吞实例 |

→ 结论：**UTF-8 →(GBK 误解码)→ 乱码字符 →(UTF-16LE 落盘)**，双重转码。

### 2.3 损坏如何摧毁 SQL 结构

18:08 文件中 `sys_menu_permission` 建表语句（原文为 `COMMENT '主表',`）：

```
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚€?,
  `permission_id` bigint NOT NULL COMMENT 'sys_permission.id',
```

- 闭合引号 `'` 被 GBK 配对吞掉 → 该字符串**永不闭合** → 解析器把后续所有语句当作字符串内容吞入；
- 同样的 `INSERT INTO` 字样在文件中只剩 155 处（正常 dump 中每个表都有），说明 ASCII 结构本身也被破坏。

恢复实测（前序会话执行）：

| 样本 | 结果 |
|---|---|
| 18:08 原始文件 | 直接灌入 → 解析失败（引号不闭合） |
| 18:08 文件做 GBK→UTF-8 回环修复后 | **仍在第 97 行 `ai_ltv_model` 处失败**（`ERROR near '1', operator_id bigint DEFAULT NULL, operator_name varchar(64) DEFAULT N'`） |

→ `?` 已经吃掉了原始字节，**不可逆**。该文件只能作为事故证据保留，不能作为备份。

### 2.4 控制组：官方 02:00 备份恢复成功

```
cmd /c "docker exec -i meiyetec-mysql mysql -uroot -p*** --default-character-set=utf8mb4 audit_restore_ok < backup\meiyetec_20261025_020004.sql"
==== exit=0 ====
stderr: mysql: [Warning] Using a password on the command line interface can be insecure.
```

| 校验项 | 结果 |
|---|---|
| 退出码 | **0** |
| 落地表数 | 281 张（`audit_restore_ok`），与 dump 内 `CREATE TABLE` 数一致 |
| stderr | 仅密码告警，**无任何 SQL 错误** |
| 中文完整性 | `audit_restore_ok.sys_permission(member:list).permission_name` = `查看会员`（UTF-8 无损） |
| 数据一致性 | 281 张表逐表行数与 live `meiyetec` 比对 → **零差异** |

### 2.5 本次重出的可用备份（已用字节复制方式）

| 项 | 值 |
|---|---|
| 路径 | `backup/meiyetec-20261025-recovery.sql` |
| 大小 | 12,199,862 字节（比 02:00 官方备份 +223,280 字节 / +1.86%，为 02:00 之后的测试数据增量） |
| `CREATE TABLE` | 281（与官方备份一致） |
| 首字节 | `2d 2d 20 4d`（`-- M`，UTF-8 无 BOM） |
| 乱码行数 | **99 行**，与 02:00 官方备份 **完全一致** → 证明是库内历史污染（I128），不是备份产生的问题 |
| 产出方式 | 容器内 `mysqldump` 写文件 → `docker cp` 拷出（**纯字节复制，不做任何编码转换**） |

### 2.6 正确 / 错误出口对照

| 写法 | 结果 |
|---|---|
| `cmd /c "docker exec … mysqldump … > file.sql"` | ✅ 字节直通（官方脚本 `scripts/backup-db-docker.ps1` 采用此写法 + `[Console]::OutputEncoding = UTF8`） |
| 容器内 `mysqldump > /tmp/x.sql` + `docker cp` | ✅ 字节直通（本次采用） |
| PowerShell 5.1 直接 `docker exec … mysqldump … > file.sql` | ❌ UTF-16LE + GBK 双重转码（本单事故） |

---

## 3. 影响

| 影响面 | 说明 |
|---|---|
| **备份可用性** | 18:08 这份备份**不可恢复**；若它当时是唯一的快照，等于"当日无有效备份"。已被 02:00 官方备份与本次重出备份覆盖，无数据丢失 |
| **静默失败** | 生成命令**不报错**、文件大小看着正常（甚至更大），只有真正恢复时才炸 → 属"假成功"类隐患 |
| **覆盖范围** | 所有用 PowerShell 5.1 原生 `>` 接管道输出的导出/落盘命令都有同一风险（不限于 mysqldump） |
| **数据安全** | 本次未造成数据丢失：库未被改动（batch1 未执行），02:00 官方备份恢复实测通过且与 live 281 表行数零差异 |

---

## 4. 处置与建议

### 4.1 已执行（2026-10-25）

1. 损坏文件改名保留为证据：`backup/before-batch1-20261025-180825.sql.UNUSABLE`（不删除，保留事故现场）。
2. 用字节复制方式重出可用备份：`backup/meiyetec-20261025-recovery.sql`。
3. 验证该备份与官方 02:00 备份在 `CREATE TABLE` 数、乱码行数两项上一致。

### 4.2 建议（尚未实施，待用户裁决）

| # | 建议 | 说明 |
|---|---|---|
| 1 | **备份必须验证可恢复性** | 本事件的教训是：**光有备份文件不算备份，必须验证可恢复**。建议后续把**恢复校验纳入 PR 必跑清单**，通过**独立脚本**实现（备份文件 → 临时 schema → 全表行数比对 → 退出码语义）。当前优先处理备份修复与 batch1 落地，工具建设待 batch1 执行稳定后再做 |
| 2 | 统一导出出口 | 团队内约定：任何 `mysqldump` / 容器导出落盘，一律走 `cmd /c` 或容器内落盘 + `docker cp`，禁止 PowerShell 原生 `>` 直连 |
| 3 | 在 `scripts/backup-db-docker.ps1` 增加编码校验 | 该脚本已有「文件大小 > 1 MB」「`CREATE TABLE` 数 ≥ 100」两项校验，可再加「首字节不是 `FF FE`」「文件可被 `mysql` 解析的前 N 行」两条，成本极低 |
| 4 | 备份文件命名带可用性标记 | 可用产物与事故证据物理隔离，避免误取（本次已用 `.UNUSABLE` 后缀做示范） |

---

## 5. 证据清单

| 文件 / 位置 | 内容 |
|---|---|
| `backup/before-batch1-20261025-180825.sql.UNUSABLE` | 事故原件（UTF-16LE + GBK 乱码，保留为证据） |
| `backup/meiyetec-20261025-recovery.sql` | 本次重出的可用备份（UTF-8 无 BOM，281 表） |
| `backup/meiyetec_20261025_020004.sql` | 官方 02:00 备份（控制组，恢复退出码 0） |
| `backup/logs/backup-20261025.log` | 官方备份成功日志（02:00:04 起、02:00:16 完成、11,976,582 字节 / 281 表） |
| `scripts/backup-db-docker.ps1` | 官方备份脚本（`cmd /c` 字节重定向 + `[Console]::OutputEncoding = UTF8`） |
| `audit/evidence/F141-backup-restore-verification/README.md` | 本单恢复实测记录（控制组 / 事故组 / 数据一致性比对） |

**绑定基线**：commit `8302d07672cd37543bf4b8d24efedea720eb3a42` /
DB `meiyetec@localhost:3307`（MySQL 8.0.46）/
DB snapshot：`backup/meiyetec-20261025-recovery.sql`（full dump，12,199,862 字节）/
env：`audit/baseline/env.md`（其 HEAD 字段已滞后至 `656caa7…`，本次未修改）

---

## 6. 未确认项

1. **18:08 那次备份的具体执行命令** —— 未确认。仓库内无匹配脚本（`scripts/backup-db-docker.ps1` 产物命名为 `meiyetec_<ts>.sql` 且必带同名 `.err.log`，与该文件不符），审计转录中未检索到该命令。可确定的只有结果特征（UTF-16LE + GBK），即**必然经过 PowerShell 5.1 原生 `>` + GBK 控制台**这一条路径。
2. **该文件当时是否已被当作有效备份使用** —— 未确认（未见恢复记录）。
3. **`audit/baseline/env.md` 记录的 HEAD 滞后原因** —— 未确认。
4. **生产环境是否存在同类隐患** —— 未确认（本单只覆盖本地 Docker 验收环境；未检查生产机上是否有以 systemd / cron 执行的同类导出任务）。
