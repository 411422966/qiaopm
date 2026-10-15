-- ============================================
-- EMP Feature 修复
-- 为 pos_employee 表添加 tenant_id 字段
-- 2026-08-25
-- ============================================

-- 1. 添加 tenant_id 列（可空，待存量数据迁移）
ALTER TABLE pos_employee
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户ID'
    AFTER role_ids;

-- 2. 建立 FK（可空，不阻塞历史数据）
ALTER TABLE pos_employee
    ADD CONSTRAINT fk_employee_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE SET NULL;

-- 3. 通过 store 关系回填存量数据的 tenant_id
UPDATE pos_employee e
    INNER JOIN pos_store s ON s.id = e.store_id
SET e.tenant_id = s.tenant_id
WHERE e.tenant_id IS NULL AND e.deleted = 0;

-- 4. 为查询加索引
CREATE INDEX idx_employee_tenant_id ON pos_employee(tenant_id);

-- 5. biz_technician_level 不需要 tenant_id（平台级配置）
