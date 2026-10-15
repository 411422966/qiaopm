-- ============================================
-- 效期追踪管理模块
-- P0-M06-006：效期追踪管理
-- 2026-08-19
-- ============================================

-- ============================================
-- 1. 效期预警规则表
-- ============================================
DROP TABLE IF EXISTS biz_expiry_alert_rule;
CREATE TABLE biz_expiry_alert_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    category VARCHAR(64) COMMENT '适用耗材分类',
    product_id BIGINT COMMENT '适用耗材ID',
    critical_days INT DEFAULT 7 COMMENT '严重预警阈值(天)',
    warning_days INT DEFAULT 30 COMMENT '普通预警阈值(天)',
    recipients TEXT COMMENT '预警接收人(JSON)',
    notify_channels VARCHAR(32) DEFAULT 'system' COMMENT '预警渠道: sms/wechat/system/all',
    auto_handle TINYINT DEFAULT 0 COMMENT '是否自动处理',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-停用, 1-启用',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category (category),
    INDEX idx_product_id (product_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='效期预警规则';

-- ============================================
-- 2. 效期预警日志表
-- ============================================
DROP TABLE IF EXISTS biz_expiry_alert_log;
CREATE TABLE biz_expiry_alert_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    inventory_id BIGINT NOT NULL COMMENT '库存批次ID',
    product_id BIGINT NOT NULL COMMENT '耗材ID',
    product_name VARCHAR(128) COMMENT '耗材名称',
    batch_no VARCHAR(64) COMMENT '批次号',
    quantity INT DEFAULT 0 COMMENT '当前数量',
    production_date DATE COMMENT '生产日期',
    expiry_date DATE NOT NULL COMMENT '过期日期',
    days_to_expiry INT COMMENT '距离过期天数',
    alert_level VARCHAR(16) DEFAULT 'normal' COMMENT '预警级别: critical/warning/normal',
    alert_type VARCHAR(32) DEFAULT 'near_expiry' COMMENT '预警类型: near_expiry/expired/stale',
    alert_message VARCHAR(500) COMMENT '预警内容',
    status VARCHAR(16) DEFAULT 'pending' COMMENT '状态: pending/processing/resolved/ignored',
    handle_method VARCHAR(32) COMMENT '处理方式',
    handler_id BIGINT COMMENT '处理人ID',
    handler_name VARCHAR(64) COMMENT '处理人姓名',
    handle_time DATETIME COMMENT '处理时间',
    handle_remark VARCHAR(500) COMMENT '处理备注',
    notified TINYINT DEFAULT 0 COMMENT '通知状态',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_inventory_id (inventory_id),
    INDEX idx_product_id (product_id),
    INDEX idx_store_id (store_id),
    INDEX idx_alert_level (alert_level),
    INDEX idx_status (status),
    INDEX idx_expiry_date (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='效期预警日志';

-- ============================================
-- 3. 效期处理记录表
-- ============================================
DROP TABLE IF EXISTS biz_expiry_handle_log;
CREATE TABLE biz_expiry_handle_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    alert_log_id BIGINT COMMENT '关联预警日志ID',
    inventory_id BIGINT NOT NULL COMMENT '批次ID',
    product_id BIGINT NOT NULL COMMENT '耗材ID',
    product_name VARCHAR(128) COMMENT '耗材名称',
    batch_no VARCHAR(64) COMMENT '批次号',
    handle_method VARCHAR(32) NOT NULL COMMENT '处理方式: dispose/return/promote/transfer/use_first',
    handle_quantity INT DEFAULT 0 COMMENT '处理数量',
    unit_price DECIMAL(15,2) DEFAULT 0 COMMENT '处理单价',
    loss_amount DECIMAL(15,2) DEFAULT 0 COMMENT '损失金额',
    handle_date DATE COMMENT '处理日期',
    handle_reason VARCHAR(500) COMMENT '处理原因',
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(64) COMMENT '操作人姓名',
    evidence VARCHAR(2000) COMMENT '凭证JSON',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_alert_log_id (alert_log_id),
    INDEX idx_inventory_id (inventory_id),
    INDEX idx_handle_method (handle_method),
    INDEX idx_handle_date (handle_date),
    INDEX idx_store_id (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='效期处理记录';

-- ============================================
-- 4. 效期看板缓存表
-- ============================================
DROP TABLE IF EXISTS biz_expiry_dashboard_cache;
CREATE TABLE biz_expiry_dashboard_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT COMMENT '租户ID',
    store_id BIGINT COMMENT '门店ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    expired_count INT DEFAULT 0 COMMENT '已过期批次数量',
    critical_count INT DEFAULT 0 COMMENT '严重预警批次数量',
    warning_count INT DEFAULT 0 COMMENT '警告批次数量',
    normal_count INT DEFAULT 0 COMMENT '正常批次数量',
    near_expiry_value DECIMAL(15,2) DEFAULT 0 COMMENT '临期批次总价值',
    expired_value DECIMAL(15,2) DEFAULT 0 COMMENT '已过期批次价值',
    pending_alert_count INT DEFAULT 0 COMMENT '待处理预警数量',
    loss_amount DECIMAL(15,2) DEFAULT 0 COMMENT '累计损失金额',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_store_date (store_id, stat_date),
    INDEX idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='效期看板缓存';

-- ============================================
-- 5. 增强 stock_inventory（如果字段不存在）
-- ============================================
-- ALTER TABLE stock_inventory ADD COLUMN last_alert_id BIGINT COMMENT '最后一次预警ID' AFTER last_out_time;
-- ALTER TABLE stock_inventory ADD COLUMN alert_count INT DEFAULT 0 COMMENT '累计预警次数' AFTER last_alert_id;

-- ============================================
-- 6. RBAC 权限
-- ============================================
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('stock:expiry', '效期追踪管理', 'stock', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ============================================
-- 7. 菜单配置
-- ============================================
INSERT INTO sys_menu (parent_id, name, path, title, icon, sort_order, type, status, deleted, create_time)
SELECT
    (SELECT id FROM sys_menu WHERE path = '/material' AND deleted = 0 LIMIT 1),
    'MaterialExpiry', '/material/expiry', '效期追踪', 'AlarmClock', 3, 'menu', 1, 0, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE path = '/material/expiry' AND deleted = 0
);

-- ============================================
-- 8. 默认预警规则示例
-- ============================================
INSERT INTO biz_expiry_alert_rule (rule_name, category, critical_days, warning_days, notify_channels, status, deleted, create_time)
VALUES
('护肤类默认规则', 'skincare', 7, 30, 'system', 1, 0, NOW()),
('清洁类默认规则', 'cleanse', 7, 30, 'system', 1, 0, NOW()),
('面膜类默认规则', 'mask', 5, 20, 'wechat', 1, 0, NOW()),
('医药类默认规则', 'medicine', 15, 60, 'all', 1, 0, NOW()),
('工具类默认规则', 'tool', 30, 90, 'system', 1, 0, NOW());