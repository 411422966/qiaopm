-- ============================================================
-- I092 — ecommerce 4 表 tenant_id=0 → 1 迁移 + idx_tenant 索引补建
-- ============================================================
--
-- 背景（issue-register.md I092 P1 OPEN，2026-09-30）：
-- * DDL create_missing_tables_b4b5.sql 声明 `INDEX idx_tenant (tenant_id)`，
--   但 4 张表中 3 张（platform / product / order）实际未建
--   （ecommerce_sync_log 因后续脚本已建）
-- * 服务上线前老数据全 tenant_id=0，I090 selectPlatformByTenant 跨租户 403
--   会导致老数据不可见
--
-- 用户决策（phase-2.30）：
-- * Q1：选 A1（UPDATE 7 行 + 4 表 ADD INDEX）
-- * Q2：选 B1（I092 先做 → I093 后做）
-- * Q3：选 C1（先 mysqldump 备份 → audit/baseline/pre-I092-backup-20260930.sql）
--
-- 备份：mysqldump 已完成（7 行 7124 bytes，commit 时间戳 2026-09-30 23:47）
--
-- 预期：
-- * UPDATE 7 行（platform 3 + product 3 + sync_log 1）
-- * ADD INDEX 3 个（platform + product + order，sync_log 已存在跳过）
--
-- 修复后语义：
-- * ecommerce_platform 3 行：tenant_id 0 → 1（默认租户 default）
-- * ecommerce_product 3 行：tenant_id 0 → 1
-- * ecommerce_sync_log 1 行：tenant_id 0 → 1
-- * 4 张表 tenant_id 索引齐备（I090 qw.eq(...::tenantId) → index lookup）
-- * dashboard / listPlatforms admin 数据可见（已可工作）
--
-- 后续（I093 Post-Audit 三期，不在本 SQL）：
-- * I093 DELETE 7 行测试残留（已登记）
-- ============================================================

USE meiyetec;

START TRANSACTION;

-- 1. UPDATE：7 行 tenant_id=0 → 1（默认租户 sys_tenant.id=1, code='default'）
UPDATE ecommerce_platform SET tenant_id = 1 WHERE tenant_id = 0 OR tenant_id IS NULL;
UPDATE ecommerce_product  SET tenant_id = 1 WHERE tenant_id = 0 OR tenant_id IS NULL;
UPDATE ecommerce_sync_log SET tenant_id = 1 WHERE tenant_id = 0 OR tenant_id IS NULL;
-- ecommerce_order：0 行，UPDATE 无影响

-- 2. ADD INDEX：idx_tenant（DDL 声明 vs 实际不一致）
-- 注意：ecommerce_sync_log 上 idx_tenant 已存在，跳过
ALTER TABLE ecommerce_platform ADD INDEX idx_tenant (tenant_id);
ALTER TABLE ecommerce_product  ADD INDEX idx_tenant (tenant_id);
ALTER TABLE ecommerce_order    ADD INDEX idx_tenant (tenant_id);
-- ecommerce_sync_log：idx_tenant 已存在，跳过（ALTER 会 1061 Duplicate key name）

COMMIT;

-- ============================================================
-- 验收（预期输出）
-- ============================================================
--
-- SELECT COUNT(*) FROM ecommerce_platform WHERE tenant_id=1;  -- 3
-- SELECT COUNT(*) FROM ecommerce_product  WHERE tenant_id=1;  -- 3
-- SELECT COUNT(*) FROM ecommerce_sync_log WHERE tenant_id=1;  -- 1
-- SELECT COUNT(*) FROM ecommerce_order    WHERE tenant_id=1;  -- 0
--
-- SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS
-- WHERE TABLE_SCHEMA='meiyetec'
--   AND TABLE_NAME IN ('ecommerce_platform','ecommerce_product','ecommerce_order','ecommerce_sync_log')
--   AND INDEX_NAME='idx_tenant';
-- 期望 4 行（platform / product / order / sync_log 全部齐备）
--
-- ============================================================
-- 回滚（若需要）
-- ============================================================
--
-- USE meiyetec;
--
-- START TRANSACTION;
-- ALTER TABLE ecommerce_order    DROP INDEX idx_tenant;
-- ALTER TABLE ecommerce_product  DROP INDEX idx_tenant;
-- ALTER TABLE ecommerce_platform DROP INDEX idx_tenant;
-- -- ecommerce_sync_log 不需 DROP（不是本 SQL 创建的）
--
-- UPDATE ecommerce_sync_log SET tenant_id = 0 WHERE id = 1;
-- UPDATE ecommerce_product  SET tenant_id = 0 WHERE id IN (1,2,3);
-- UPDATE ecommerce_platform  SET tenant_id = 0 WHERE id IN (1,2,3);
-- COMMIT;
-- ============================================================