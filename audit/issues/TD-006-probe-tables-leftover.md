# TD-006 — 调查类探针表（_t_*）在 MySQL 库中可能遗留，需定期清理

> 发现时间：2026-10-27（I129 修复前的备份对比过程中发现）
> 发现方式：`backup/meiyetec-20261025-recovery.sql` 与 `backup/meiyetec-before-i129.sql` 两份 dump 的 `CREATE TABLE` 数量与表名集合比对
> 严重级别：**P3**（仓库 / 环境卫生；不阻塞功能，但会污染备份与排障视野）
> 状态：**Open**（遗留探针表已由用户声明人工 DROP 修复；**清理机制本身尚未建立** → 本单保持 Open）
> 关联：I129（探针表产生于 I129 调查过程；I129 §2.5 的语法实测使用了第三个探针表 `_t_probe_i129`）、I118（DDL 台账缺失同类）
> 基线：commit `e60376aac762a1fde45557ca1a21513388f57031`（2026-10-27 23:50:18）/
> DB `meiyetec@localhost:3307`（MySQL 8.0.46）/
> DB snapshot：`backup/meiyetec-20261025-recovery.sql` + `backup/meiyetec-before-i129.sql` / env：`audit/baseline/env.md`

> **编号说明**：`audit/issues/` 下原先无 `TD-*` 序列文件；本单接续 `docs/governance/QIAOPM_PROJECT_TECHNICAL_BASELINE.md`
> 技术债表（§D.2）的 `QIAOPM-TD-001` ~ `QIAOPM-TD-005` 序列 → 下一个可用编号为 **TD-006**。
> **本次未同步修改该治理文件**（治理文件写入需单独批准）。

---

## 1. 一句话结论

调查 I129 时，为验证 `ADD COLUMN IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` 的语法行为，
在 live 库里建过探针表（`_t_*` 前缀）。其中 **`_t_col_probe` 与 `_t_idx_probe` 两张被漏掉**，
**留在库里并被 subsequent 备份带走**——两份 dump 的 `CREATE TABLE` 数量差 **281 → 283**，差集正好就是这两张。

| 项 | 值 |
|---|---|
| `backup/meiyetec-20261025-recovery.sql` | **281** 个 `CREATE TABLE` |
| `backup/meiyetec-before-i129.sql` | **283** 个 `CREATE TABLE` |
| **差集（新增 2 张）** | **`_t_col_probe`**（L25）、**`_t_idx_probe`**（L46） |

用户声明：两张探针表**已人工 DROP 修复**（本轮未连库核验，见 §6）。

---

## 2. 硬证据

### 2.1 两份 dump 的 `CREATE TABLE` 计数（逐行统计实测）

```
backup/meiyetec-20261025-recovery.sql   →  281
backup/meiyetec-before-i129.sql         →  283
```

统计口径：文件中以 `^CREATE TABLE ` 开头的行（大小写敏感）。

### 2.2 差集表的确切位置与结构

`backup/meiyetec-before-i129.sql` L18-58（dump 的**最前面**，即按字母序排在业务表之前）：

```sql
---
-- Table structure for table `_t_col_probe`
---

DROP TABLE IF EXISTS `_t_col_probe`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `_t_col_probe` (            -- L25
  `id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

---
-- Table structure for table `_t_idx_probe`
---

DROP TABLE IF EXISTS `_t_idx_probe`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `_t_idx_probe` (            -- L46
  `id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- 两表结构一致：单列 `id int DEFAULT NULL`，**无数据**（dump 中的 `LOCK TABLES` / `UNLOCK TABLES` 之间无 INSERT）；
- `_t_col_probe` 用于验证「加列」语法，`_t_idx_probe` 用于验证「建索引」语法 → 命名与 I129 §2.5 的两项 1064 实测一一对应。

### 2.3 第三个探针表 `_t_probe_i129` 已被正确清理

I129 §2.5 记录的语法实测使用的探针表是 `_t_probe_i129`：

```sql
mysql> ALTER TABLE _t_probe_i129 ADD COLUMN IF NOT EXISTS c1 INT;
ERROR 1064 (42000) ...
mysql> CREATE INDEX IF NOT EXISTS idx_probe ON _t_probe_i129(id);
ERROR 1064 (42000) ...
```

- **`_t_probe_i129` 未出现在 `backup/meiyetec-before-i129.sql` 的 283 张表里** → 该表已按「测完即删」清理干净；
- 对比之下 `_t_col_probe` / `_t_idx_probe` **被漏掉** → 说明清理是**人工逐个记忆执行**的，不是流程保证的 → 正是本单要根治的问题。

### 2.4 备份会忠实搬运残留表

`backup/meiyetec-before-i129.sql` 里 `_t_col_probe` / `_t_idx_probe` 各自带着完整的
`DROP TABLE IF EXISTS` + `CREATE TABLE` + `LOCK TABLES` 段 → **恢复该备份会把两张垃圾表一并建回来**。

---

## 3. 影响

| 影响面 | 说明 |
|---|---|
| **库内垃圾对象** | 探针表是纯测试残留，无业务含义，污染 `SHOW TABLES` 结果 |
| **备份污染 → 恢复再污染** | 备份忠实搬运残留表；恢复一次就重新引入一次，**形成"备份即传播"的闭环** |
| **排障干扰** | 表数量对不上（281 vs 283）、`SHOW TABLES` 里出现看不懂的 `_t_*`，容易误判为"库被改过"或"库表漂移" |
| **长期累积** | 每做一次需建临时表的调查就可能留下一张；单次是 P3，累积后会成为「库表台账不可信」的噪声源（与 I118 的 DDL 台账缺失同类） |
| **无自动化拦截** | 当前**没有任何**流程保证调查探针表被清理 → 依赖人记得删 → 必然复发 |

---

## 4. 修复方向

### 4.1 短期（人工约定）

每次 Cursor / 开发侧调查任务完成后，人工执行一次检查并清理：

```sql
SHOW TABLES LIKE '\_t\_%';        -- 注意 \_ 转义，否则 % 前会误匹配
```

> ⚠️ **转义要点**：`LIKE` 中 `_` 是单字符通配符，不转义会匹配到任意单字符 + `t` + 任意单字符 + `%`，
> 可能把业务表一起列出来（例如 `pos_order` 之类的名字含 `_t_` 的表）；
> 正确写法是 `'\_t\_%'`（反斜杠转义下划线）。

### 4.2 长期（写入 Cursor 规则）

在项目 Cursor 规则中新增 **规则 20**：

> **规则 20 —— 调查类任务的探针表必须显式清理**
> 调查过程中如需在 live 库建临时表 / 探针表（建议统一 `_t_` 前缀），
> 必须在任务结束前显式 `DROP`，并在任务输出中**报告所建表名与清理状态**；
> 未清理的表一律登记为环境脏数据（对应 TD-006）。

### 4.3 配套（可选，建议）

| 建议 | 说明 |
|---|---|
| 备份前自检 | 备份脚本执行前先 `SHOW TABLES LIKE '\_t\_%'`，命中则中止并告警，避免把垃圾表写进备份 |
| 调查用临时 schema | 探针表建在独立临时 schema（而非业务 schema），任务结束直接 drop schema，彻底免掉"逐表清理"环节 |

---

## 5. 证据清单

| 文件 / 位置 | 内容 |
|---|---|
| `backup/meiyetec-20261025-recovery.sql` | 281 个 `CREATE TABLE`（统计口径：`^CREATE TABLE `） |
| `backup/meiyetec-before-i129.sql` | 283 个 `CREATE TABLE` |
| `backup/meiyetec-before-i129.sql:19-37` | `_t_col_probe` 完整表结构段（无数据） |
| `backup/meiyetec-before-i129.sql:40-58` | `_t_idx_probe` 完整表结构段（无数据） |
| `backup/meiyetec-before-i129.sql:25` / `:46` | 两张探针表的 `CREATE TABLE` 行号 |
| `audit/issues/I129-missing-columns-pos-crm.md` §2.5 | `_t_probe_i129` 的两次 1064 语法实测（该表已清理） |

**绑定基线**：commit `e60376aac762a1fde45557ca1a21513388f57031` /
DB `meiyetec@localhost:3307`（MySQL 8.0.46）/
DB snapshot：`backup/meiyetec-20261025-recovery.sql` + `backup/meiyetec-before-i129.sql` / env：`audit/baseline/env.md`

---

## 6. 未确认项

1. **两张探针表当前是否已从 live 库删除** —— 用户声明「已人工 DROP 修复」，
   但**本轮未连库核验**（受任务边界限制：不连数据库）→ **未确认**。
   需人工执行 `SHOW TABLES LIKE '\_t\_%'` 复核为空集。
2. **`backup/meiyetec-before-i129.sql` 本身是否需要重出** —— 该备份仍含两张探针表；
   若它被用作「I129 修复前」的基线证据，**重出会破坏证据链**；若仍作恢复源，则会把垃圾表带回来 → **处理方式待决策**。
3. **探针表的准确创建时间与创建者** —— dump 文件无时间戳线索 → **未确认**（按 I129 调查期间创建理解）。
4. **是否还有其他前缀的调查残留表** —— 本单只核了 `_t_*` 前缀 → **未做全量前缀普查**。
5. **规则 20 的具体落点** —— 写入 Cursor 规则属于修改项目规则文件，需用户单独批准 → **本次未执行**。
