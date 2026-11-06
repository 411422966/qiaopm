# F144 — I128-b 业务表乱码修复证据

> **Feature**：F144 — 业务表中文乱码（分类 D）修复
> **关联 issue**：`audit/issues/I128-b-charset-remaining.md` §4.1 / §8 / §9
> **执行日期**：2026-11-07
> **DB**：`meiyetec`（本地 docker `meiyetec-mysql`，MySQL 8.0，宿主端口 3307）
> **结论**：101 行乱码**已修复并验证通过**；`sys_permission` 51 行经实测为假阳性，本批不动。

## 1. 修复范围

| 表.列 | 行数 | id 区间 | 修复前乱码计数 | 修复后残留计数 |
|---|---|---|---|---|
| `biz_service_item.service_name` | 22 | 1–22 | 22 | **0** |
| `biz_service_item.unit` | 22 | 1–22 | 22 | **0** |
| `biz_service_category.category_name` | 5 | 1–5 | 5 | **0** |
| `biz_treatment_plan.plan_name` | 26 | 1–26 | 26 | **0** |
| `biz_treatment_plan.description` | 26 | 1–26 | 26 | **0** |
| **合计** | **101** | — | **101** | **0** |

**未纳入**：`sys_permission.permission_name` 51 行 —— 命中的 `C2B7` 是 `·`（U+00B7 中间点），
51 行**无一行含中文**，属自动注册器生成的 `module·action` 英文显示名（本地化缺失，非编码问题）。

## 2. 证据文件

| 文件 | 内容 |
|---|---|
| `before-00-transaction-dryrun-rollback.txt` | **事务级干跑**：101 条 UPDATE 在事务内执行 → 5 列残留全 0 + 中文回读正确 → `ROLLBACK` → 恢复 `22/22/5/26/26`（证明完全可逆、WHERE 双重匹配无误伤） |
| `before-01-preview.txt` | 修复前只读预览：各列乱码基线计数 + 101 行「当前乱码值 / 还原目标值」逐行对照 |
| `before-02-fix-execution.log` | 正式修复脚本执行输出（无报错） |
| `after-01-verify.txt` | 修复后验证：A 段残留计数全 0 / B 段 `HEX()` 字节回读 / C 段中文抽查 / D 段行数守恒 `32 / 5 / 26` |
| `after-02-idempotent-rerun.log` | **幂等复跑** fix 脚本输出（无报错、数据不变） |
| `after-03-verify-after-rerun.txt` | 幂等复跑后再验证：残留计数仍为 0 |

## 3. 关键字节证据

```
乱码行 id=1 service_name
  库中 HEX : C3A5C5B8C2BAC3A7C2A1E282ACC3A9C29DC2A2C3A9C692C2A8C3A6C2B8E280A6C3A6C2B4C281
  逆向还原 : E59FBAE7A180E99DA2E983A8E6B885E6B481  → 基础面部清洁 ✅
修复后 HEX : E59FBAE7A180E99DA2E983A8E6B885E6B481（与还原值逐字节一致）

还原样例（DB 回读）
  SV001 基础面部清洁 / SV009 身体按摩 60 分钟 / SV022 测试待上线服务   unit=次
  FACE 面部护理 / BODY 美体塑形 / TATTOO 纹绣美瞳 / HEAD 头部SPA / OTHER 其他
  TP001 焕颜亮白 10 次套餐 / TP025 乳腺疏通 6 次（停用）/ TP026 测试方案（停用）
```

## 4. 使用的脚本

| 脚本 | 说明 |
|---|---|
| `backup/i128-b/fix-i128-b-preview.sql` | 只读预览 |
| `backup/i128-b/fix-i128-b-business-charset.sql` | 正式修复（`SET NAMES utf8mb4` + `CONVERT(0x… USING utf8mb4)` + `id`/`BINARY 旧值` 双重匹配 + 事务包裹） |
| `backup/i128-b/fix-i128-b-verify.sql` | 修复后验证 |

> **版本化说明**：仓库 `.gitignore:506` 忽略整个 `backup/` 目录（历史惯例：备份目录只放本机环境产物），
> 因此上述 3 个脚本在 `backup/i128-b/` 下**不入 git**。本证据目录内保存了它们的**逐字节副本**，
> 便于审计追溯；执行时以 `backup/i128-b/` 下的文件为准。

## 5. 未收口项（转下一批）

- `sys_permission` 51 行 `·` 命名 → 本地化批次
- `stock_product.unit` / `complication_sop.responsible_role` / `biz_spa_scene_mode.*` / `industry_template.*` / `mh_drug_device.specification` → 需权威源
- `backend/src/main/resources/db/sql/` 剩余 146 个 `.sql` 补 `SET NAMES utf8mb4`
- `audit/issues/I140-ai-enhance-blood-line-drift.md` → 已登记，未修
