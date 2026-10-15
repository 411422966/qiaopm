-- migrate_m15_01_item_preference.sql
-- M15 项目偏好：crm_member_item_preference 表
-- 用途：记录会员对服务/项目的偏好分（基于消费历史，非 ML）
-- 唯一键：(member_id, item_type, item_id, tenant_id)

CREATE TABLE IF NOT EXISTS crm_member_item_preference (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id      BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    member_id       BIGINT NOT NULL COMMENT '会员ID',
    item_type       VARCHAR(32)  NOT NULL COMMENT '项目类型：SERVICE/PRODUCT/CATEGORY/BRAND',
    item_id         BIGINT NOT NULL COMMENT '项目ID（service_id/product_id/category_id）',
    item_name       VARCHAR(128) COMMENT '项目名称（冗余存储）',
    preference_score INT DEFAULT 0 COMMENT '偏好分 0-100',
    total_order_count INT DEFAULT 0 COMMENT '累计消费次数',
    total_amount_fen   BIGINT DEFAULT 0 COMMENT '累计消费金额（分）',
    last_order_at   DATETIME COMMENT '最后一次消费时间',
    preference_tags VARCHAR(256) COMMENT '偏好标签，逗号分隔：高频,高消费,强烈偏好',
    calc_window_days INT DEFAULT 365 COMMENT '计算周期（天）',
    deleted         TINYINT DEFAULT 0 COMMENT '软删除',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- 唯一约束：同一会员对同一项目只一条记录
    UNIQUE KEY uk_member_item_type_id (member_id, item_type, item_id, tenant_id),
    INDEX idx_tenant_member (tenant_id, member_id),
    INDEX idx_item_type (item_type, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='M15 会员项目偏好表';
