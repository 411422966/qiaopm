-- M09-01：短信/微信触达真实通道 — 站内信消息表
-- 存储 InAppSender 写入的站内通知
CREATE TABLE IF NOT EXISTS member_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id       BIGINT DEFAULT NULL COMMENT '租户ID',
    member_id       BIGINT NOT NULL COMMENT '会员ID',
    member_name     VARCHAR(100) DEFAULT NULL COMMENT '会员姓名',
    title           VARCHAR(200) NOT NULL COMMENT '消息标题',
    content         TEXT NOT NULL COMMENT '消息内容（支持HTML）',
    message_type    VARCHAR(50) DEFAULT 'SYSTEM' COMMENT '类型：SYSTEM/BIRTHDAY/EXPIRE/PROMO/TASK',
    channel_type    VARCHAR(50) DEFAULT 'IN_APP' COMMENT '来源渠道：IN_APP/SMS/MP',
    related_type    VARCHAR(50) DEFAULT NULL COMMENT '关联类型：ORDER/PACKAGE/BOOKING',
    related_id      BIGINT DEFAULT NULL COMMENT '关联业务ID',
    is_read         TINYINT(1) DEFAULT 0 COMMENT '是否已读：0未读 1已读',
    read_at         DATETIME DEFAULT NULL COMMENT '阅读时间',
    priority        TINYINT DEFAULT 2 COMMENT '优先级：1高 2普通 3低',
    expire_at       DATETIME DEFAULT NULL COMMENT '过期时间（NULL永不过期）',
    operator_id     BIGINT DEFAULT NULL COMMENT '操作人ID',
    operator_name   VARCHAR(100) DEFAULT NULL COMMENT '操作人姓名',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT(1) DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_member_id (member_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_is_read (is_read),
    INDEX idx_expire_at (expire_at),
    INDEX idx_created_at (created_at),
    INDEX idx_message_type (message_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='会员站内信';
