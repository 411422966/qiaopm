-- ============================================================
-- P0-T01 · 修复 pos_store.tenant_id 列缺失
-- ============================================================
-- 背景：
--   /member/* API 返回 500，根因是 MemberController.fillExtras
--   调用 StoreServiceImpl.listByIds，而 Store 实体声明 tenantId
--   字段，DB 实际表 pos_store 没有该列 → Unknown column 'tenant_id' in 'field list'
--
-- 解决：单独 ALTER TABLE，仅补 tenant_id 列，不动其它 Entity 字段
-- 范围：仅 pos_store 表，其它表一律不碰
--
-- 选择 BIGINT NULL 的理由：
--   - 与 Baseline 中 pos_store DDL 当前风格一致（其他列多为 NULL）
--   - 不强加 DEFAULT 1，避免改写历史数据语义
--   - 后续 T07（多租户 setTenantId 替换）会统一 fillback
-- ============================================================

ALTER TABLE pos_store
    ADD COLUMN tenant_id BIGINT NULL COMMENT '租户ID (P0-T01 补列, 与 Store.tenantId 对齐)';

CREATE INDEX idx_pos_store_tenant_id ON pos_store (tenant_id);
