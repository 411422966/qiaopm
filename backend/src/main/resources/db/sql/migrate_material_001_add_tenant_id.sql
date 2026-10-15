-- ============================================
-- STOCK/MATERIAL Feature 修复
-- 为耗材绑定相关 4 个表添加 tenant_id 字段
-- 2026-08-26
-- ============================================

-- 1. biz_service_material_group
ALTER TABLE biz_service_material_group
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER id;

ALTER TABLE biz_service_material_group
    ADD CONSTRAINT fk_smg_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL;

CREATE INDEX idx_smg_tenant_id ON biz_service_material_group(tenant_id);

-- 2. biz_material_substitute
ALTER TABLE biz_material_substitute
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER id;

ALTER TABLE biz_material_substitute
    ADD CONSTRAINT fk_ms_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL;

CREATE INDEX idx_ms_tenant_id ON biz_material_substitute(tenant_id);

-- 3. biz_material_consumption_log
ALTER TABLE biz_material_consumption_log
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER id;

ALTER TABLE biz_material_consumption_log
    ADD CONSTRAINT fk_mcl_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL;

CREATE INDEX idx_mcl_tenant_id ON biz_material_consumption_log(tenant_id);

-- 4. biz_service_material_cost
ALTER TABLE biz_service_material_cost
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID' AFTER id;

ALTER TABLE biz_service_material_cost
    ADD CONSTRAINT fk_smc_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL;

CREATE INDEX idx_smc_tenant_id ON biz_service_material_cost(tenant_id);
