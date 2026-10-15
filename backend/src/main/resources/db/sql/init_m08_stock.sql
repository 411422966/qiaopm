-- ============================================
-- M08 库存管理模块 - 数据库初始化
-- 2026-08-02
-- ============================================

-- 1. 商品/耗材表（含批次预警）
DROP TABLE IF EXISTS `stock_product`;
CREATE TABLE `stock_product` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `product_code` varchar(64) UNIQUE NOT NULL COMMENT '商品编码',
  `product_name` varchar(128) NOT NULL COMMENT '商品名称',
  `category` varchar(50) DEFAULT '耗材' COMMENT '分类:耗材/产品/工具',
  `spec` varchar(100) COMMENT '规格',
  `unit` varchar(16) DEFAULT '瓶' COMMENT '单位:瓶/盒/支/件',
  `cost_price` decimal(12,2) DEFAULT 0 COMMENT '成本价',
  `sale_price` decimal(12,2) DEFAULT 0 COMMENT '销售价',
  `min_stock` int DEFAULT 10 COMMENT '最低库存预警',
  `max_stock` int DEFAULT 1000 COMMENT '最高库存',
  `enable_batch` tinyint DEFAULT 0 COMMENT '是否启用批次管理:0否1是',
  `status` tinyint DEFAULT 1 COMMENT '状态:1启用 0停用',
  `remark` varchar(500),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_stock_product_store` (`store_id`),
  INDEX `idx_stock_product_cat` (`category`)
) COMMENT='商品/耗材表';

-- 2. 库存表（按门店+商品聚合，含批次）
DROP TABLE IF EXISTS `stock_inventory`;
CREATE TABLE `stock_inventory` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `batch_no` varchar(64) DEFAULT NULL COMMENT '批次号',
  `quantity` int NOT NULL DEFAULT 0 COMMENT '当前库存',
  `production_date` date COMMENT '生产日期',
  `expiry_date` date COMMENT '过期日期',
  `supplier` varchar(100) COMMENT '供应商',
  `last_in_time` datetime COMMENT '最后入库时间',
  `last_out_time` datetime COMMENT '最后出库时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_stock_inv_store_product` (`store_id`, `product_id`),
  INDEX `idx_stock_inv_batch` (`batch_no`),
  INDEX `idx_stock_inv_expiry` (`expiry_date`)
) COMMENT='库存表（支持批次）';

-- 3. 出入库记录表（流水）
DROP TABLE IF EXISTS `stock_record`;
CREATE TABLE `stock_record` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `batch_no` varchar(64) COMMENT '批次号',
  `type` tinyint NOT NULL COMMENT '类型:1入库 2出库 3盘点 4领用 5报损',
  `quantity` int NOT NULL COMMENT '数量（正数入库，负数出库）',
  `before_qty` int DEFAULT 0 COMMENT '操作前库存',
  `after_qty` int DEFAULT 0 COMMENT '操作后库存',
  `unit_price` decimal(12,2) DEFAULT 0 COMMENT '单价',
  `total_amount` decimal(12,2) DEFAULT 0 COMMENT '总金额',
  `supplier` varchar(100) COMMENT '供应商（入库）',
  `order_id` bigint COMMENT '关联订单（领用）',
  `operator_id` bigint NOT NULL COMMENT '操作人',
  `operator_name` varchar(64),
  `remark` varchar(500),
  `record_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_stock_record_product` (`product_id`),
  INDEX `idx_stock_record_type` (`type`),
  INDEX `idx_stock_record_time` (`record_time`)
) COMMENT='出入库记录';

-- 4. 库存盘点表（盘点单）
DROP TABLE IF EXISTS `stock_check`;
CREATE TABLE `stock_check` (
  `id` bigint PRIMARY KEY AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT 1,
  `store_id` bigint NOT NULL,
  `check_no` varchar(64) UNIQUE NOT NULL COMMENT '盘点单号',
  `check_date` date NOT NULL COMMENT '盘点日期',
  `total_items` int DEFAULT 0 COMMENT '盘点商品数',
  `diff_items` int DEFAULT 0 COMMENT '差异商品数',
  `profit_qty` int DEFAULT 0 COMMENT '盘盈数',
  `loss_qty` int DEFAULT 0 COMMENT '盘亏数',
  `operator_id` bigint NOT NULL,
  `operator_name` varchar(64),
  `status` tinyint DEFAULT 1 COMMENT '1进行中 2已完成',
  `remark` varchar(500),
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT 0,
  INDEX `idx_stock_check_store` (`store_id`)
) COMMENT='库存盘点单';

-- ============================================
-- 种子数据
-- ============================================
-- 6 种典型耗材
INSERT INTO `stock_product` (tenant_id, store_id, product_code, product_name, category, spec, unit, cost_price, sale_price, min_stock, max_stock, enable_batch, status) VALUES
(1, 1, 'SP-BOT-001', '玻尿酸精华液',     '耗材', '30ml/瓶',  '瓶',  80.00, 198.00, 20,  500, 1, 1),
(1, 1, 'SP-MSK-002', '美白补水面膜',     '耗材', '25g*5片/盒', '盒', 35.00,  98.00, 30,  600, 1, 1),
(1, 1, 'SP-CRM-003', '保湿面霜',         '耗材', '50g/瓶',  '瓶',  60.00, 158.00, 25,  400, 0, 1),
(1, 1, 'SP-OIL-004', '精油按摩油',       '耗材', '100ml/瓶', '瓶', 45.00, 128.00, 15,  300, 0, 1),
(1, 1, 'SP-TOW-005', '一次性毛巾',       '耗材', '80g/条',  '条',   2.00,   5.00,100, 2000, 0, 1),
(1, 1, 'SP-CUP-006', '一次性杯子',       '耗材', '250ml/个', '个',   0.50,   2.00,200, 3000, 0, 1);

-- 初始库存（每种商品给门店#1 入库一批次）
INSERT INTO `stock_inventory` (tenant_id, store_id, product_id, batch_no, quantity, production_date, expiry_date, supplier, last_in_time)
SELECT 1, 1, id, CONCAT('BATCH-', DATE_FORMAT(NOW(), '%Y%m%d'), '-001'), 100, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), '广州美容供应商', NOW()
FROM stock_product WHERE deleted = 0;

-- 历史入库记录（种子数据，6 条）
INSERT INTO `stock_record` (tenant_id, store_id, product_id, batch_no, type, quantity, before_qty, after_qty, unit_price, total_amount, supplier, operator_id, operator_name, remark)
SELECT 1, 1, id, CONCAT('BATCH-', DATE_FORMAT(NOW(), '%Y%m%d'), '-001'), 1, 100, 0, 100, cost_price, cost_price * 100, '广州美容供应商', 1, 'admin', '初始入库'
FROM stock_product WHERE deleted = 0;

-- 1 个盘点单示例
INSERT INTO `stock_check` (tenant_id, store_id, check_no, check_date, total_items, operator_id, operator_name, status, remark)
VALUES (1, 1, CONCAT('CHK', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s')), CURDATE(), 6, 1, 'admin', 1, '月初盘点');

-- 验证
SELECT 'product' AS tbl, COUNT(*) AS cnt FROM stock_product
UNION ALL SELECT 'inventory', COUNT(*) FROM stock_inventory
UNION ALL SELECT 'record',    COUNT(*) FROM stock_record
UNION ALL SELECT 'check',     COUNT(*) FROM stock_check;