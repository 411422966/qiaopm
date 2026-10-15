-- =============================================
-- R2.0-C：收银支付退款对账闭环
-- =============================================
USE meiyetec;

-- =============================================
-- 1. 退款单表 (pos_refund)
-- =============================================
DROP TABLE IF EXISTS pos_refund;
CREATE TABLE pos_refund (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id BIGINT DEFAULT NULL COMMENT '所属租户',
    store_id BIGINT NOT NULL COMMENT '门店ID',
    refund_no VARCHAR(40) NOT NULL COMMENT '退款单号（全局唯一）',
    order_id BIGINT NOT NULL COMMENT '原订单ID',
    order_no VARCHAR(40) NOT NULL COMMENT '原订单号（冗余）',
    member_id BIGINT DEFAULT NULL COMMENT '会员ID',
    member_name VARCHAR(64) DEFAULT NULL COMMENT '会员姓名（冗余）',

    -- 退款范围
    refund_type VARCHAR(16) NOT NULL DEFAULT 'FULL' COMMENT '退款类型：FULL 全单 / PART 部分 / CROSS 跨单',
    refund_amount DECIMAL(12,2) NOT NULL COMMENT '本次退款金额',
    paid_amount DECIMAL(12,2) NOT NULL COMMENT '原订单实付',
    refunded_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '已退款总额（累计）',
    remaining_amount DECIMAL(12,2) NOT NULL COMMENT '尚可退金额',

    -- 退款策略
    refund_strategy VARCHAR(16) NOT NULL DEFAULT 'ORIGINAL' COMMENT '退款策略：ORIGINAL 原路 / WALLET 储值返还 / MIXED 混合',
    cash_refund DECIMAL(12,2) DEFAULT 0 COMMENT '现金/原路退款金额',
    wallet_refund DECIMAL(12,2) DEFAULT 0 COMMENT '储值返还金额',
    thirdparty_refund DECIMAL(12,2) DEFAULT 0 COMMENT '第三方通道退款金额（cash/wechat/alipay/unionpay）',

    -- 逆向补偿
    reverse_package TINYINT DEFAULT 0 COMMENT '是否已回退套餐次数 0/1',
    reverse_points TINYINT DEFAULT 0 COMMENT '是否已返还积分 0/1',
    reverse_stock TINYINT DEFAULT 0 COMMENT '是否已回退库存 0/1',
    reverse_commission TINYINT DEFAULT 0 COMMENT '是否已冲正提成 0/1',

    -- 状态机
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/AUDIT/APPROVED/REJECTED/PROCESSING/SUCCESS/FAILED/CANCELLED',
    reason VARCHAR(512) DEFAULT NULL COMMENT '退款原因',
    reject_reason VARCHAR(512) DEFAULT NULL COMMENT '拒绝原因',
    operator_id BIGINT DEFAULT NULL COMMENT '申请人ID',
    operator_name VARCHAR(64) DEFAULT NULL COMMENT '申请人姓名',
    auditor_id BIGINT DEFAULT NULL COMMENT '审核人ID',
    auditor_name VARCHAR(64) DEFAULT NULL COMMENT '审核人姓名',

    -- 关联
    channel_txn_id VARCHAR(128) DEFAULT NULL COMMENT '第三方通道流水号',
    idempotency_key VARCHAR(128) DEFAULT NULL COMMENT '幂等键',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT '链路跟踪ID',

    -- 时间
    apply_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    audit_time DATETIME DEFAULT NULL COMMENT '审核时间',
    refund_time DATETIME DEFAULT NULL COMMENT '完成时间',
    version INT DEFAULT 0 NOT NULL COMMENT '乐观锁版本号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_tenant_refund_no (tenant_id, refund_no),
    INDEX idx_order_id (order_id),
    INDEX idx_member_id (member_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单';

-- =============================================
-- 2. 退款单明细表（按 payment 维度，支持部分退款精确拆分）
-- =============================================
DROP TABLE IF EXISTS pos_refund_item;
CREATE TABLE pos_refund_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id BIGINT DEFAULT NULL COMMENT '所属租户',
    refund_id BIGINT NOT NULL COMMENT '退款单ID',
    payment_id BIGINT NOT NULL COMMENT '原 PosPayment.ID',
    payment_method VARCHAR(32) NOT NULL COMMENT '支付方式：cash/wechat/alipay/wallet/package',
    payment_amount DECIMAL(12,2) NOT NULL COMMENT '原支付金额',
    refund_amount DECIMAL(12,2) NOT NULL COMMENT '本次退款金额',
    payment_no VARCHAR(40) DEFAULT NULL COMMENT '原支付流水号',
    status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
    error_msg VARCHAR(512) DEFAULT NULL COMMENT '错误信息',
    channel_txn_id VARCHAR(128) DEFAULT NULL COMMENT '第三方通道流水号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_refund_id (refund_id),
    INDEX idx_payment_id (payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单明细';

-- =============================================
-- 3. 支付对账表 (pos_reconciliation)
-- =============================================
DROP TABLE IF EXISTS pos_reconciliation;
CREATE TABLE pos_reconciliation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id BIGINT DEFAULT NULL COMMENT '所属租户',
    store_id BIGINT DEFAULT NULL COMMENT '门店ID',
    reconcile_date DATE NOT NULL COMMENT '对账日期',
    channel VARCHAR(32) NOT NULL COMMENT '支付通道：wechat/alipay/unionpay/cash',
    channel_txn_id VARCHAR(128) DEFAULT NULL COMMENT '通道流水号',
    order_id BIGINT DEFAULT NULL COMMENT '本地订单ID',
    order_no VARCHAR(40) DEFAULT NULL COMMENT '订单号',
    payment_id BIGINT DEFAULT NULL COMMENT '本地支付ID',
    channel_amount DECIMAL(12,2) DEFAULT NULL COMMENT '通道侧金额',
    local_amount DECIMAL(12,2) DEFAULT NULL COMMENT '本地侧金额',
    diff_amount DECIMAL(12,2) DEFAULT 0 COMMENT '差异金额',
    status VARCHAR(16) NOT NULL DEFAULT 'MATCHED' COMMENT 'MATCHED / DIFF_LOCAL_MORE / DIFF_CHANNEL_MORE / DIFF_AMOUNT / MISSING_LOCAL / MISSING_CHANNEL',
    handle_status VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING / HANDLED / IGNORED',
    handle_by BIGINT DEFAULT NULL COMMENT '处理人ID',
    handle_by_name VARCHAR(64) DEFAULT NULL COMMENT '处理人',
    handle_time DATETIME DEFAULT NULL COMMENT '处理时间',
    handle_note VARCHAR(512) DEFAULT NULL COMMENT '处理备注',
    raw_payload MEDIUMTEXT DEFAULT NULL COMMENT '通道原始报文',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_tenant_date (tenant_id, reconcile_date),
    INDEX idx_status (status),
    INDEX idx_channel (channel),
    UNIQUE KEY uk_tenant_channel_txn (tenant_id, channel, channel_txn_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付对账表';

-- =============================================
-- 4. 退款逆向事件表（用于补偿追溯）
-- =============================================
DROP TABLE IF EXISTS pos_refund_reverse_log;
CREATE TABLE pos_refund_reverse_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id BIGINT DEFAULT NULL COMMENT '所属租户',
    refund_id BIGINT NOT NULL COMMENT '退款单ID',
    reverse_type VARCHAR(32) NOT NULL COMMENT 'PACKAGE / POINTS / STOCK / COMMISSION',
    ref_id BIGINT DEFAULT NULL COMMENT '反向业务主键（如 redemption_id / commission_id）',
    payload MEDIUMTEXT DEFAULT NULL COMMENT '反向流水 JSON',
    operator_id BIGINT DEFAULT NULL COMMENT '执行人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_refund_id (refund_id),
    INDEX idx_reverse_type (reverse_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款逆向事件表';

-- =============================================
-- 5. 增加 pos_order 字段（兼容旧表）
-- =============================================
ALTER TABLE pos_order
    ADD COLUMN refunded_amount DECIMAL(12,2) DEFAULT 0 COMMENT '已退款总额' AFTER paid_amount,
    ADD COLUMN refund_count INT DEFAULT 0 COMMENT '退款次数' AFTER refunded_amount;

-- =============================================
-- 7. 退款/对账相关权限码
-- =============================================
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pos:refund', '退款单', 'menu', id, 1 FROM sys_permission WHERE permission_code = 'pos' LIMIT 1;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pos:refund:audit', '退款审核', 'button', id, 1 FROM sys_permission WHERE permission_code = 'pos:refund' LIMIT 1;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pos:reconciliation:list', '对账查询', 'menu', id, 2 FROM sys_permission WHERE permission_code = 'pos' LIMIT 1;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pos:reconciliation:run', '对账执行', 'button', id, 1 FROM sys_permission WHERE permission_code = 'pos:reconciliation:list' LIMIT 1;
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, parent_id, sort_order)
SELECT 'pos:reconciliation:handle', '差异处理', 'button', id, 2 FROM sys_permission WHERE permission_code = 'pos:reconciliation:list' LIMIT 1;

-- 赋予 admin/super/store_admin
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code IN ('SUPER_ADMIN', 'STORE_ADMIN')
  AND p.permission_code IN (
      'pos:refund','pos:refund:audit',
      'pos:reconciliation:list','pos:reconciliation:run','pos:reconciliation:handle'
  );

-- 验证
SELECT '=== pos_refund ===' AS '';
SHOW CREATE TABLE pos_refund;
SELECT '=== pos_refund_item ===' AS '';
SHOW CREATE TABLE pos_refund_item;
SELECT '=== pos_reconciliation ===' AS '';
SHOW CREATE TABLE pos_reconciliation;
SELECT '=== pos_refund_reverse_log ===' AS '';
SHOW CREATE TABLE pos_refund_reverse_log;
