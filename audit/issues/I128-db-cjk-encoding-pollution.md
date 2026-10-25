# I128 — 库内 CJK 文本编码污染：RBAC 三表 15 行中文不可读，备份忠实搬运、不会自愈

> 发现时间：2026-10-25（I127 备份恢复实测过程中顺带发现）
> 发现方式：备份文件编码分析 → 源 SQL 文件比对 → live 库逐列扫描
> 严重级别：**P1**（数据质量；不阻塞功能，但 UI 直接展示乱码）
> 状态：**Open**（未修复，修复口径待定）
> 关联：I127（备份编码损坏，同批排查）、I113（`-Encoding ascii` 写文件风险，同类编码事故）、I096（UTF-8 源文件损坏）、I112（issue-register 乱码）
> 基线：commit `8302d07672cd37543bf4b8d24efedea720eb3a42` /
> DB `meiyetec@localhost:3307`（MySQL 8.0.46）/
> DB snapshot：`backup/meiyetec-20261025-recovery.sql` / env：`audit/baseline/env.md`

---

## 1. 一句话结论

**生产（本地验收）库里有一批中文已经被写坏**，集中在权限与账号三张表：

| 表 | 受损行 | 表现 |
|---|---|---|
| `sys_permission` | 6 | `permission_name` 为 GBK 乱码（如 `閫??瀹℃牳-搴楅暱`）或 `?` |
| `sys_role` | 5 | `role_name` / `description` 为 `??` ~ `?????` |
| `sys_user` | 4 | `real_name` = `??????` |

关键判定：**这是库里的历史脏数据，不是备份产生的**——
本次新出的备份 `backup/meiyetec-20261025-recovery.sql` 与官方 02:00 备份的乱码行数**完全一致（99 行 vs 99 行）**，
且 10-22 / 10-23 / 10-24 / 10-25 四份历史备份中该值逐字节相同 → **恢复备份不会修复它，也不会加重它**。

---

## 2. 硬证据

### 2.1 受损行清单（live `meiyetec`）

`sys_permission`（6 行）：

| id | permission_code | permission_name（当前库值） | 源文件应有值 |
|---|---|---|---|
| 1308 | `pos:refund:audit:l1` | `閫??瀹℃牳-搴楅暱` | 退款审核-店长 |
| 1309 | `pos:refund:audit:l2` | `閫??瀹℃牳-鍖哄煙缁忕悊` | 退款审核-区域经理 |
| 1310 | `pos:refund:audit:l3` | `閫??瀹℃牳-璐㈠姟鎬荤洃` | 退款审核-服务总监 |
| 1311 | `pos:refund:audit:rule` | `閫??瀹℃牳瑙勫垯` | 退款审核规则 |
| 1312 | `employee:level` | `鎶?笀绛夌骇` | 员工等级 |
| 1319 | `material:binding:add` | `鑰楁潗缁戝畾-鏂板?` | 耗材绑定-新增 |

`sys_role`（5 行）：`role_code` = `CLERK` / `MANAGER` / `BOSS` / `OPERATOR` / `ADMIN`，
`role_name` 分别为 `??` / `??` / `??` / `??` / `?????`，`description` 同步受损。

`sys_user`（4 行）：`boss01` / `clerk01` / `manager01` 等，`real_name` = `??????`（账号 5 个，仅 `admin` 的 `系统管理员` 正常）。

> 字节级取样（`sys_permission.id = 1308`）：
> `U+95AB ? ? U+7039 U+2103 U+7273 - U+6434 U+6945 U+66B1`
> → 前 4 个汉字已被 GBK 解读成乱码 + 2 个不可解码位被替换成 `?`，后 2 个汉字同样是乱码，`-` 保留。
> 这正是「UTF-8 字节流被按 GBK 解读」的特征。

### 2.2 两类成因，可逆性不同

| 类型 | 特征 | 可逆性 |
|---|---|---|
| **A 类：GBK 误读** | 值里还有成片汉字乱码（`閫??瀹℃牳`） | **部分可逆**：把库中 utf8mb4 字节按 GBK 解码，可还原原始 UTF-8 字节；但 GBK 配对失败的位置已写成 `?`，那部分**永久丢失** |
| **B 类：ASCII 写盘** | 值整体变成 `?`（`??` / `??????`） | **不可逆**：每个汉字被替换成 1 个 `?`，原字符数与语义都需人工重写 |

A 类的还原路径（待实施，需先做小样本验证）：
`CONVERT(CONVERT(col USING gbk) USING utf8mb4)` → 拿到原始 UTF-8 字节 → 逐字节校验能否解出合法中文。

### 2.3 源 SQL 文件本身是干净的 → 问题出在「手工执行通道」

| 文件 | 行 | 该行中文内容 | 文件编码 |
|---|---|---|---|
| `backend/src/main/resources/db/sql/migrate_pos_008_refund_audit_chain.sql` | 66 | `退款审核-店长` | **正确 UTF-8**（码点 `U+9000 U+6B3E U+5BA1 U+6838 - U+5E97 U+957F`） |
| 同上 | 83 | `pos:refund:audit:l1` / `l2` / `l3` / `rule` 校验 | 同上 |
| `backend/src/main/resources/db/sql/migrate_p0_2_p0_5_rbac_registration.sql` | 53 | `material:binding:add` → `耗材绑定-新增` | 同上 |

两份文件全文 **0 个 `?`、0 个 U+FFFD** → 污染不是写文件时产生的。

配套事实：

| 项 | 值 | 出处 |
|---|---|---|
| 应用侧 JDBC | `characterEncoding=UTF-8` | `backend/src/main/resources/application.yml:6` |
| Flyway 编码 | `spring.flyway.encoding: UTF-8`，且 `locations: classpath:db/migration` | `backend/src/main/resources/application.yml:22-23` |
| `db/sql/migrate_*.sql` | **不在 Flyway 扫描路径内**，属「legacy init_*/migrate_* 手工执行脚本」 | `backend/src/main/resources/application.yml:30` baseline-description |
| 容器内 mysql 客户端默认 | `character_set_client = latin1` | 容器 `meiyetec-mysql` 实测 |

→ 结论：**这些 legacy 脚本是被手工灌进数据库的，而灌入通道（Windows 控制台 + 客户端字符集）把 UTF-8 当成了 GBK/latin1**，
于是「文件正确 → 落库变乱码」。具体是哪一次执行、用的哪条命令 —— **未确认**。

### 2.4 污染早于本批次，且被所有历史备份忠实携带

| 备份 | `sys_permission(id=1308).permission_name` 码点序列 | 是否一致 |
|---|---|---|
| `backup/meiyetec_20261022_162504.sql` | `U+95AB ? ? U+7039 U+2103 U+7273 - U+6434 U+6945 U+66B1` | ✅ |
| `backup/meiyetec_20261023_220648.sql` | 同上 | ✅ |
| `backup/meiyetec_20261024_020003.sql` | 同上 | ✅ |
| `backup/meiyetec_20261025_020004.sql` | 同上 | ✅ |
| `backup/meiyetec-20261025-recovery.sql`（本次新出） | 同上 | ✅ |

→ 污染**最迟在 2026-10-22 之前已入库**，本轮 batch1 之前就存在；
**任何一次恢复都会把同样的乱码写回去**，恢复 ≠ 修复。

### 2.5 全库文本列扫描结果

对 `meiyetec` 库所有 `char/varchar/text` 列做 `LIKE '%?%'` 计数，命中 26 列：

| 列 | 行数 | 判定 |
|---|---|---|
| `sys_log.error_msg` | 97 | 日志，多为历史报错文本，**不单独处理** |
| `sys_log.request_params` | 34 | 日志，**不单独处理** |
| `report_metric_caliber.sql_template` | 8 | **业务数据，需核** |
| `sys_permission.permission_name` | 6 | **RBAC 受损（已定位）** |
| `pos_order.remark` | 5 | 需核（可能是正常问号） |
| `sys_role.description` | 5 | **RBAC 受损** |
| `sys_role.role_name` | 5 | **RBAC 受损** |
| `complaint_category.category_name` | 4 | 需核 |
| `payment_channel.channel_name` | 4 | 需核 |
| `sys_role_data_scope.remark` | 4 | 需核 |
| `sys_user.real_name` | 4 | **RBAC 受损** |
| `mh_visual_archive.thumbnail_url` / `image_url` | 3 / 3 | 多为 URL query 中的 `?`，**非污染** |
| `hq_dashboard_widget.widget_name` | 3 | 需核 |
| `report_metric_caliber.formula` | 3 | 需核 |
| `sys_login_log.username` | 2 | 日志 |
| `v_wallet_drift.name` | 2 | 需核 |
| `mkt_channel_template.content` | 2 | 需核 |
| `crm_member.name` | 2 | 需核 |
| `pos_order_item.employee_name` | 1 | 需核 |
| `mkt_journey.journey_name` | 1 | 需核 |
| `mkt_invitation_rule.rule_name` | 1 | 需核 |
| `mkt_coupon_batch.coupon_name` | 1 | 需核 |
| `mkt_channel_template.audit_remark` / `subject` | 1 / 1 | 需核 |
| `marketing_touch_task.campaign_name` | 1 | 需核 |
| `crm_wallet_transaction.pay_method` | 1 | 需核 |

> 说明：`LIKE '%?%'` 会把 URL 中的正常问号一起命中，故上表需人工逐行判定；
> 已确证受损的是 `sys_permission` / `sys_role` / `sys_user` 三表共 **15 行**。

### 2.6 备份对照：污染量与备份无关

| 文件 | 乱码行数（行级口径） | 文件内 `?` 字符数 | 含 `?` 行数 |
|---|---|---|---|
| `backup/meiyetec_20261025_020004.sql`（官方） | 99 | 1,207 | 235 |
| `backup/meiyetec-20261025-recovery.sql`（本次新出） | 99 | 1,207 | 235 |
| `backup/before-batch1-20261025-180825.sql.UNUSABLE`（I127 事故件） | — | **15,026** | **805** |

→ 正常导出方式下污染量**一模一样**；事故件的 13,819 个额外 `?` 来自转码（见 I127 §2.1），与库内污染是两回事。

---

## 3. 影响

| 影响面 | 说明 |
|---|---|
| **UI 直接展示乱码** | 权限名 / 角色名 / 员工真实姓名在列表、按钮、下拉里以 `閫??瀹℃牳` 或 `??` 呈现 |
| **恢复不能自愈** | 所有历史备份携带同一污染；恢复只会原样写回 |
| **A 类部分可逆、B 类不可逆** | 修复需要「按语义重写」，不能靠批量转码一把梭 |
| **影响面尚在确认** | 除 15 行 RBAC 外，另有约 15 个业务列命中 `?`，需逐行判定是污染还是正常问号 |

---

## 4. 修复方向（待用户裁决，未实施）

| 方案 | 说明 | 风险 |
|---|---|---|
| **A（推荐）** | 以源 SQL 文件为准**逐行重写**：A 类先试 `CONVERT(... USING gbk)` 还原、还原不了的人工按 `migrate_*.sql` 原文补；B 类直接人工重写 | 需要人工确认每行中文文案；改动落在数据层，不动代码 |
| B | 只修 RBAC 三表 15 行，其余列留待后续 | 修复面小，但 `report_metric_caliber` / `hq_dashboard_widget` 等仍可能展示乱码 |
| C | 全库批量 GBK→UTF-8 还原后再人工兜底 | **风险高**：正常中文也会被二次转码破坏，必须先在只读副本上验证 |

执行前置（无论选哪个）：
1. 先在**只读副本**（用 `backup/meiyetec-20261025-recovery.sql` 恢复到临时 schema）上跑还原 SQL 并逐行比对；
2. 产出「库值 → 期望值」对照表，交人工确认文案；
3. 确认后再对 live 执行，并立刻再出一份备份。

---

## 5. 证据清单

| 文件 / 位置 | 内容 |
|---|---|
| `backup/meiyetec-20261025-recovery.sql` | 本次新出备份（与官方备份乱码量一致 → 污染在库内） |
| `backup/meiyetec_20261025_020004.sql` | 官方 02:00 备份（对照样本） |
| `backend/src/main/resources/db/sql/migrate_pos_008_refund_audit_chain.sql:66,83` | 源文件（正确 UTF-8，污染源不在此） |
| `backend/src/main/resources/db/sql/migrate_p0_2_p0_5_rbac_registration.sql:53,57,86` | 同上 |
| `backend/src/main/resources/application.yml:6,22-23,30` | JDBC 字符集 / Flyway 扫描路径 / legacy 脚本说明 |
| `audit/evidence/F141-backup-restore-verification/README.md` | 恢复实测与乱码计数证据 |

**绑定基线**：commit `8302d07672cd37543bf4b8d24efedea720eb3a42` /
DB `meiyetec@localhost:3307`（MySQL 8.0.46，库内 281 张表）/
DB snapshot：`backup/meiyetec-20261025-recovery.sql` / env：`audit/baseline/env.md`

---

## 6. 未确认项

1. **污染的注入时间点与注入命令** —— 未确认。最迟 2026-10-22 已存在（§2.4），更早的快照不可得。
2. **除 15 行 RBAC 外，其余命中列的真实受损行数** —— 未确认（`LIKE '%?%'` 含正常问号噪声，需逐行判读）。
3. **A 类还原的实际可恢复比例** —— 未验证（需在只读副本上试算）。
4. **生产环境库是否存在同类污染** —— 未确认（本单只覆盖本地 Docker 验收环境）。
5. **历史是否已有用户因乱码投诉** —— 未确认（未查工单/反馈记录）。
