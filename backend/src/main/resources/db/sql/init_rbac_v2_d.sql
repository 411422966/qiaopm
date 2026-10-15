-- =============================================
-- R2.0-D：清理占位与未启用入口
-- - biz_service_material：服务-耗材 BOM（每次服务消耗多少耗材）
-- - 库存消耗从订单支付成功事件自动写库（type=4 领用）
-- =============================================

-- 1. 服务-耗材 BOM
DROP TABLE IF EXISTS biz_service_material;
CREATE TABLE biz_service_material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id BIGINT DEFAULT 1 COMMENT '租户ID',
    service_id BIGINT NOT NULL COMMENT '服务ID (biz_service.id)',
    product_id BIGINT NOT NULL COMMENT '耗材ID (stock_product.id)',
    quantity DECIMAL(10,2) NOT NULL DEFAULT 1 COMMENT '单次消耗数量',
    unit VARCHAR(16) DEFAULT NULL COMMENT '单位（冗余展示）',
    is_required TINYINT DEFAULT 1 COMMENT '是否必需:1必需0可选',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '软删',
    UNIQUE KEY uk_tenant_service_product (tenant_id, service_id, product_id),
    INDEX idx_service (service_id),
    INDEX idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务-耗材消耗关系表（BOM）';

-- 2. 订单-消耗幂等记录（用于防止订单支付成功后重复扣减）
DROP TABLE IF EXISTS pos_order_consumption;
CREATE TABLE pos_order_consumption (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id BIGINT DEFAULT 1 COMMENT '租户ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    stock_record_id BIGINT NOT NULL COMMENT 'stock_record.id',
    product_id BIGINT NOT NULL COMMENT '耗材ID',
    service_id BIGINT DEFAULT NULL COMMENT '关联服务ID',
    quantity INT NOT NULL COMMENT '消耗数量',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键 (pos_order_consume:{orderId}:{serviceId}:{productId})',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_idempotency (tenant_id, idempotency_key),
    INDEX idx_order (order_id),
    INDEX idx_record (stock_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单-消耗幂等记录';

-- 3. RBAC：补 3 个权限码
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'stock:product:import', '商品批量导入', 'button',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='stock') t LIMIT 1),
       90;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'stock:replenish', '补货建议', 'menu',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='stock') t LIMIT 1),
       80;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'stock:export', '库存导出', 'button',
       (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code='stock') t LIMIT 1),
       85;

-- 4. 给现有角色补全权限（SUPER_ADMIN / STORE_ADMIN 全开）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN','STORE_ADMIN')
  AND p.permission_code IN ('stock:product:import','stock:replenish','stock:export');

-- 5. 演示数据：玻尿酸精华液 给 "面部护理SPA" 服务关联上，每次消耗 0.5 瓶
-- 说明：biz_service 是演示种子，service_code='SVC-001' 对应面部护理SPA；
--       stock_product 是演示种子，product_code='SP-BOT-001' 对应玻尿酸精华液。
INSERT IGNORE INTO biz_service_material (tenant_id, service_id, product_id, quantity, unit, is_required, remark)
SELECT 1, s.id, p.id, 0.5, '瓶', 1, '示例 BOM：面部护理SPA 每次消耗玻尿酸精华液 0.5 瓶'
FROM biz_service s, stock_product p
WHERE s.service_code = 'SVC-001' AND p.product_code = 'SP-BOT-001'
LIMIT 1;

INSERT IGNORE INTO biz_service_material (tenant_id, service_id, product_id, quantity, unit, is_required, remark)
SELECT 1, s.id, p.id, 1, '片', 1, '示例 BOM：面部护理SPA 每次消耗美白补水面膜 1 片'
FROM biz_service s, stock_product p
WHERE s.service_code = 'SVC-001' AND p.product_code = 'SP-MSK-002'
LIMIT 1;

-- 验证
SELECT 'biz_service_material' AS tbl, COUNT(*) AS cnt FROM biz_service_material;
SELECT 'pos_order_consumption' AS tbl, COUNT(*) AS cnt FROM pos_order_consumption;
