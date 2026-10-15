# `db/sql/` 历史脚本目录（已废弃）

> ⚠️ **不再自动执行** — 自 2026-09 起，数据库迁移统一由 **Flyway** 管理，新位置：
>
> ```
> backend/src/main/resources/db/migration/V*.sql
> ```
>
> 本目录作为**开发参考/历史归档**保留，不参与 Spring Boot 启动期执行。

## 命名规约（仅供历史追溯）

| 前缀 | 含义 | 例 |
|---|---|---|
| `init_*` | 建表 + 初始种子 | `init_m01_auth.sql` |
| `migrate_*` | 增量 ALTER | `migrate_pos_008_*.sql` |
| `patch_*` | 事后补丁（含数据修复） | `patch_p0_db_001_data_repair.sql` |
| `fix_*` | 字段补齐 / 索引修正 | `fix_menu_permission_complete.sql` |
| `seed_*` | 开发期种子数据 | `seed_member.sql` |
| `verify_*` / `q*.sql` / `_inspect*.sql` / `expire_test.sql` | 调试用脚本 | — |

## 现状

- **167 个脚本**：命名混乱、隐式依赖未文档化；不适合直接归档成 Flyway V 序列
- 已通过 `V1__init_baseline.sql`（改造自 `init_migration_v0_baseline_ALL.sql`）覆盖核心 80+ 张表
- 后续每个功能增量 → 新增 `db/migration/V2__xxx.sql` 即可

## 历史脚本去向

| 类型 | 处理 |
|---|---|
| 已被 V1 baseline 覆盖的建表 | **保留**（参考用，不再执行） |
| 未被 baseline 覆盖的 `migrate_ext_*` 等增量 | **仍需迁移** → 评估是否需要补 `V2__init_ext_*.sql`，见 R1 风险评估 |
| `seed_*` / `verify_*` / `q*.sql` | **保留**（开发工具，非业务依赖） |

## R1 风险评估（待办）

- ⚠️ `migrate_ext_15` / `migrate_ext_17` / `migrate_ext_19` / `migrate_ext_22` / `migrate_ext_23` / `migrate_ext_24` 这 6 个 EXT-*.sql **不在 V1 baseline 范围内**
- 新部署环境执行 baseline 后，**缺这 6 类表的扩展列**
- 后续如需端到端 Flyway 还原 → 需要再写 6 个 `V2__ext_15.sql` ... `V7__ext_24.sql` 增量脚本

## 不要做

- ❌ 不要在本目录新增 SQL 后期待它被自动执行
- ❌ 不要直接 `mysql < xxx.sql` 在生产执行（运维 workflow 改用 Flyway）
