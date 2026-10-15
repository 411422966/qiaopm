-- ============================================================
-- P0-T01-ext · pos_store 扩展字段补齐（与 Store Entity 对齐）
-- ============================================================
-- 依据：
--   init_rbac_v3_0_a.sql:42-58 历史 DDL
--   Store Entity 当前 9 扩展字段：tenantId / brandId / storeType /
--     regionCode / isHq / managerEmployeeId / managerName / openDate / brandPriority
--
-- 范围：
--   - 仅补 8 列（tenant_id 已由 T01 添加）
--   - 仅补属于这 8 列的索引（idx_brand, idx_tenant_status）
--   - 不动其它业务表
--   - 不修改 Java
--
-- 调整点（相对原 init_rbac_v3_0_a.sql）：
--   - brand_priority ... AFTER priority  → 不带 AFTER（priority 列不存在）
--   - update_time ... AFTER create_time → 不带 AFTER（已存在，不重复）
--   - idx_tenant (tenant_id)            → 跳过（T01 已建 idx_pos_store_tenant_id）
-- ============================================================

ALTER TABLE pos_store
    ADD COLUMN brand_id            BIGINT       NULL COMMENT '所属品牌',
    ADD COLUMN store_type          VARCHAR(16)  NULL DEFAULT 'DIRECT' COMMENT 'DIRECT直营/FRANCHISE加盟/FLAGSHIP旗舰店',
    ADD COLUMN region_code         VARCHAR(32)  NULL COMMENT '区域编码（如华东-001）',
    ADD COLUMN is_hq               TINYINT      NULL DEFAULT 0 COMMENT '是否总部（1=总部）',
    ADD COLUMN manager_employee_id BIGINT       NULL COMMENT '门店负责人',
    ADD COLUMN manager_name        VARCHAR(64)  NULL COMMENT '负责人姓名',
    ADD COLUMN open_date           DATE         NULL COMMENT '开业日期',
    ADD COLUMN brand_priority      INT          NULL DEFAULT 0 COMMENT '品牌内排序';

CREATE INDEX idx_brand          ON pos_store (brand_id);
CREATE INDEX idx_tenant_status  ON pos_store (tenant_id, status, deleted);
