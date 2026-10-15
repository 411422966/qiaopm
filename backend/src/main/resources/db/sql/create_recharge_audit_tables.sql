-- ============================================================
-- M05 储值充值审核 + 钱包冻结机制
-- P1-新增：充值审核流程完整闭环
-- ============================================================

-- 充值审核申请单
CREATE TABLE IF NOT EXISTS crm_recharge_audit (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT  NOT NULL COMMENT '租户ID',
    store_id        BIGINT  COMMENT '门店ID',
    audit_no        VARCHAR(32) NOT NULL COMMENT '审核单号',
    member_id       BIGINT  NOT NULL COMMENT '会员ID',
    member_name     VARCHAR(64) COMMENT '会员姓名',
    member_phone    VARCHAR(20) COMMENT '会员手机',
    amount          DECIMAL(12,2) NOT NULL COMMENT '充值金额',
    gift_amount     DECIMAL(12,2) DEFAULT 0.00 COMMENT '赠送金额',
    total_amount    DECIMAL(12,2) COMMENT '实付金额(含赠送)',
    pay_method      VARCHAR(32) COMMENT '支付方式',
    trade_no        VARCHAR(64) COMMENT '第三方交易流水号',
    status          TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1待审核 2审核通过 3审核拒绝 4已取消',
    apply_user_id   BIGINT COMMENT '申请人ID',
    apply_user_name VARCHAR(64) COMMENT '申请人姓名',
    audit_user_id   BIGINT COMMENT '审核人ID',
    audit_user_name VARCHAR(64) COMMENT '审核人姓名',
    audit_time      DATETIME COMMENT '审核时间',
    audit_remark    VARCHAR(255) COMMENT '审核意见',
    source          TINYINT DEFAULT 1 COMMENT '来源:1后台代充 2会员自助 3批量导入',
    remark          VARCHAR(255) COMMENT '备注',
    version         INT DEFAULT 0 COMMENT '乐观锁版本',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0,
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_member (member_id),
    INDEX idx_audit_no (audit_no),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值审核申请表';

-- 充值审核操作日志
CREATE TABLE IF NOT EXISTS crm_recharge_audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT  NOT NULL,
    audit_id        BIGINT  NOT NULL COMMENT '关联审核单ID',
    audit_no        VARCHAR(32) COMMENT '审核单号',
    action          TINYINT NOT NULL COMMENT '操作类型:1提交申请 2审核通过 3审核拒绝 4取消 5发放成功',
    operator_id     BIGINT COMMENT '操作人ID',
    operator_name   VARCHAR(64) COMMENT '操作人姓名',
    before_status   VARCHAR(16) COMMENT '操作前状态',
    after_status    VARCHAR(16) COMMENT '操作后状态',
    remark          VARCHAR(255) COMMENT '操作说明',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0,
    INDEX idx_audit_id (audit_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值审核操作日志';
