-- ============================================================
-- ORG-004: sys_tenant_config 租户 KV 配置表
-- ============================================================
USE meiyetec;

CREATE TABLE IF NOT EXISTS sys_tenant_config (
    id          BIGINT          NOT NULL    AUTO_INCREMENT  PRIMARY KEY,
    tenant_id   BIGINT          NOT NULL                    COMMENT '租户ID',
    cfg_key     VARCHAR(128)    NOT NULL                    COMMENT '配置键',
    cfg_value   TEXT                                        COMMENT '配置值',
    description VARCHAR(255)                                 COMMENT '说明',
    created_at  DATETIME       DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
    updated_at  DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_tenant_key (tenant_id, cfg_key),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户配置表';

-- 默认插入几条通用配置示例（租户1）
INSERT INTO sys_tenant_config (tenant_id, cfg_key, cfg_value, description) VALUES
(1, 'company_name',      '美业科技有限公司',       '公司/店铺名称'),
(1, 'business_hours',    '09:00-21:00',           '营业时间'),
(1, 'contact_phone',     '400-888-8888',           '联系电话'),
(1, 'contact_address',   '请填写详细地址',         '联系地址'),
(1, 'checkin_auto_sync', 'true',                   '打卡自动同步会员积分'),
(1, 'booking_reminder',  '30',                     '预约提前提醒分钟数'),
(1, 'refund_need_audit', 'false',                  '退款是否需要审核'),
(1, 'max_booking_days',  '30',                     '最大预约天数')
ON DUPLICATE KEY UPDATE cfg_value=VALUES(cfg_value);
