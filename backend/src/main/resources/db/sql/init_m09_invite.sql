-- ====================================================================
-- M09-02 裂变邀请机制（邀请有礼 / 老客带新）
-- 适用模块：M09 营销工具
-- 数据模型：
--   m09_invite_campaign  : 邀请活动配置（邀请人奖励 / 新人奖励 / 有效期）
--   m09_invite_code      : 每会员每活动唯一邀请码 + 分享/扫码统计
--   m09_invite_record    : 受邀人注册/首单/奖励发放记录
--   m09_invite_reward_log: 奖励发放不可变日志（coupon/point/wallet 三种）
-- KPI 闭环：裂变带新率 = 成功邀请数 / 新增会员数
-- ====================================================================

-- 1) 邀请活动配置表
CREATE TABLE IF NOT EXISTS m09_invite_campaign (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id               BIGINT NOT NULL COMMENT '租户ID',
    campaign_code           VARCHAR(64) NOT NULL COMMENT '活动编码（租户内唯一）',
    campaign_name           VARCHAR(120) NOT NULL COMMENT '活动名称',
    campaign_type           VARCHAR(20) DEFAULT 'INVITE_REWARD' COMMENT '活动类型（INVITE_REWARD / GROUP_BUY）',

    -- 邀请人奖励
    inviter_reward_type     TINYINT NOT NULL COMMENT '邀请人奖励类型 1优惠券 2积分 3储值 4自定义',
    inviter_reward_value    DECIMAL(12,2) DEFAULT 0 COMMENT '邀请人奖励值',
    inviter_reward_meta     VARCHAR(500) DEFAULT NULL COMMENT '奖励元数据 JSON（couponId/templateId 等）',

    -- 新人奖励
    invitee_reward_type     TINYINT NOT NULL COMMENT '新人奖励类型 1优惠券 2积分 3储值 4自定义',
    invitee_reward_value    DECIMAL(12,2) DEFAULT 0 COMMENT '新人奖励值',
    invitee_reward_meta     VARCHAR(500) DEFAULT NULL COMMENT '奖励元数据 JSON',

    -- 触发门槛
    invitee_min_consume     DECIMAL(12,2) DEFAULT 0 COMMENT '新人首单最低消费（元），达此门槛才发放双方奖励',
    invite_need_register    TINYINT(1) DEFAULT 1 COMMENT '是否需要注册（0=仅点击即可 1=必须注册）',

    -- 配额
    inviter_quota_per_day   INT DEFAULT 5 COMMENT '单人每日邀请上限',
    inviter_quota_total     INT DEFAULT 50 COMMENT '单人累计邀请上限',
    campaign_quota_total    INT DEFAULT 0 COMMENT '活动总配额（0=不限）',

    -- 分享文案
    share_title             VARCHAR(200) DEFAULT NULL COMMENT '分享标题',
    share_desc              VARCHAR(1000) DEFAULT NULL COMMENT '分享描述',
    share_image_url         VARCHAR(500) DEFAULT NULL COMMENT '分享图片',
    share_landing_url       VARCHAR(500) DEFAULT NULL COMMENT '落地页URL（可空 = 系统默认）',

    -- 时间与状态
    start_time              DATETIME NOT NULL COMMENT '活动开始时间',
    end_time                DATETIME NOT NULL COMMENT '活动结束时间',
    status                  TINYINT(1) DEFAULT 1 COMMENT '状态 1启用 0停用',

    -- 审计
    operator_id             BIGINT DEFAULT NULL COMMENT '创建人ID',
    operator_name           VARCHAR(100) DEFAULT NULL COMMENT '创建人姓名',
    remark                  VARCHAR(500) DEFAULT NULL COMMENT '备注',

    created_at              DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at              DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 TINYINT(1) DEFAULT 0 COMMENT '逻辑删除',

    UNIQUE KEY uk_tenant_code (tenant_id, campaign_code),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_active_time (tenant_id, status, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='M09 邀请活动配置';


-- 2) 邀请码表（每会员每活动唯一）
CREATE TABLE IF NOT EXISTS m09_invite_code (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id               BIGINT NOT NULL COMMENT '租户ID',
    campaign_id             BIGINT NOT NULL COMMENT '活动ID',
    member_id               BIGINT NOT NULL COMMENT '邀请人会员ID',
    member_name             VARCHAR(100) DEFAULT NULL COMMENT '邀请人姓名',
    member_phone            VARCHAR(20) DEFAULT NULL COMMENT '邀请人手机号（脱敏）',

    invite_code             VARCHAR(32) NOT NULL COMMENT '邀请码（活动内唯一）',
    short_link              VARCHAR(255) DEFAULT NULL COMMENT '短链',

    -- 漏斗统计
    share_count             INT DEFAULT 0 COMMENT '分享次数（小程序/短信/H5 按钮点击）',
    scan_count              INT DEFAULT 0 COMMENT '扫码/访问次数',
    register_count          INT DEFAULT 0 COMMENT '通过该码注册的新人数',
    success_count           INT DEFAULT 0 COMMENT '成功邀请数（首单完成）',

    created_at              DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT(1) DEFAULT 0,

    UNIQUE KEY uk_campaign_code (campaign_id, invite_code),
    UNIQUE KEY uk_campaign_member (campaign_id, member_id),
    INDEX idx_member (member_id),
    INDEX idx_tenant_campaign (tenant_id, campaign_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='M09 邀请码';


-- 3) 邀请记录表（受邀人侧：点击 → 注册 → 首单 → 奖励）
CREATE TABLE IF NOT EXISTS m09_invite_record (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id               BIGINT NOT NULL COMMENT '租户ID',
    campaign_id             BIGINT NOT NULL COMMENT '活动ID',
    campaign_name           VARCHAR(120) DEFAULT NULL COMMENT '冗余活动名（活动删除/改名后仍可读）',
    invite_code             VARCHAR(32) NOT NULL COMMENT '邀请码',
    inviter_member_id       BIGINT NOT NULL COMMENT '邀请人',
    inviter_name            VARCHAR(100) DEFAULT NULL COMMENT '邀请人姓名',

    invitee_member_id       BIGINT DEFAULT NULL COMMENT '受邀人会员ID（注册后回填）',
    invitee_phone           VARCHAR(20) DEFAULT NULL COMMENT '受邀人手机号（脱敏存储 138****1234）',
    invitee_name            VARCHAR(100) DEFAULT NULL COMMENT '受邀人姓名',

    channel                 VARCHAR(20) DEFAULT 'LINK' COMMENT '渠道：WECHAT/SMS/LINK/QR/POSTER',

    -- 状态机：PENDING → REGISTERED → FIRST_ORDER → REWARDED / FAILED
    status                  TINYINT DEFAULT 1 COMMENT '1待注册 2已注册 3已首单 4已奖励 5已失效 6已拒绝',

    -- 时间戳
    clicked_at              DATETIME DEFAULT NULL COMMENT '点击时间',
    register_at             DATETIME DEFAULT NULL COMMENT '注册时间',
    first_consume_at        DATETIME DEFAULT NULL COMMENT '首次消费时间',
    first_order_id          BIGINT DEFAULT NULL COMMENT '首单订单ID',
    inviter_rewarded_at     DATETIME DEFAULT NULL COMMENT '邀请人奖励发放时间',
    invitee_rewarded_at     DATETIME DEFAULT NULL COMMENT '新人奖励发放时间',

    -- 关联奖励日志
    inviter_reward_log_id   BIGINT DEFAULT NULL,
    invitee_reward_log_id   BIGINT DEFAULT NULL,

    -- 防刷
    ip                      VARCHAR(64) DEFAULT NULL,
    ua                      VARCHAR(255) DEFAULT NULL,
    device_id               VARCHAR(128) DEFAULT NULL,

    fail_reason             VARCHAR(500) DEFAULT NULL COMMENT '失败原因（如触发风控）',
    remark                  VARCHAR(500) DEFAULT NULL,

    created_at              DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted                 TINYINT(1) DEFAULT 0,

    -- 同一活动同一邀请人同一受邀人手机号 唯一（允许不同活动 / 不同邀请人）
    UNIQUE KEY uk_campaign_inviter_phone (campaign_id, inviter_member_id, invitee_phone),
    INDEX idx_inviter (inviter_member_id, status),
    INDEX idx_invitee_member (invitee_member_id),
    INDEX idx_campaign_status (campaign_id, status),
    INDEX idx_status (status, created_at),
    INDEX idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='M09 邀请记录';


-- 4) 奖励发放日志（不可变）
CREATE TABLE IF NOT EXISTS m09_invite_reward_log (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id               BIGINT NOT NULL,
    record_id               BIGINT NOT NULL COMMENT '关联邀请记录ID',
    campaign_id             BIGINT NOT NULL,
    campaign_name           VARCHAR(120) DEFAULT NULL,

    reward_to               TINYINT NOT NULL COMMENT '1邀请人 2受邀人',
    member_id               BIGINT NOT NULL COMMENT '收奖人',
    member_name             VARCHAR(100) DEFAULT NULL,
    member_phone            VARCHAR(20) DEFAULT NULL,

    reward_type             TINYINT NOT NULL COMMENT '1优惠券 2积分 3储值',
    reward_type_text        VARCHAR(20) DEFAULT NULL,
    reward_value            DECIMAL(12,2) DEFAULT 0 COMMENT '奖励值',
    reward_name             VARCHAR(200) DEFAULT NULL COMMENT '奖励名（如「满100减30券」「500积分」）',

    -- 关联实体
    coupon_grant_id         BIGINT DEFAULT NULL,
    points_record_id        BIGINT DEFAULT NULL,
    wallet_txn_id           BIGINT DEFAULT NULL,

    status                  TINYINT DEFAULT 1 COMMENT '1成功 2失败 3已回滚',
    fail_reason             VARCHAR(500) DEFAULT NULL,
    error_code              VARCHAR(50) DEFAULT NULL,

    operator_id             BIGINT DEFAULT NULL,
    operator_name           VARCHAR(100) DEFAULT NULL,
    is_auto                 TINYINT(1) DEFAULT 1 COMMENT '是否系统自动发放',

    created_at              DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发放时间',

    INDEX idx_record (record_id),
    INDEX idx_member (member_id),
    INDEX idx_campaign_status (campaign_id, status),
    INDEX idx_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='M09 邀请奖励发放日志';


-- 5) 邀请活动种子（演示用，可选）
INSERT INTO m09_invite_campaign
(tenant_id, campaign_code, campaign_name, campaign_type,
 inviter_reward_type, inviter_reward_value, inviter_reward_meta,
 invitee_reward_type, invitee_reward_value, invitee_reward_meta,
 invitee_min_consume, invite_need_register,
 inviter_quota_per_day, inviter_quota_total, campaign_quota_total,
 share_title, share_desc, start_time, end_time, status, operator_id, operator_name, remark)
SELECT 1, 'INVITE_2026_WELCOME', '老客带新 · 双倍礼', 'INVITE_REWARD',
       1, 30.00, '{"couponName":"满100减30"}',
       1, 50.00, '{"couponName":"新人体验券满200减50"}',
       100.00, 1,
       5, 50, 0,
       '【美业旗舰店】邀你共享美丽时光', '扫码立享新人专属福利，好友同行再得优惠券！',
       '2026-01-01 00:00:00', '2026-12-31 23:59:59', 1, 1, '系统', '种子示例活动'
WHERE NOT EXISTS (
    SELECT 1 FROM m09_invite_campaign WHERE tenant_id = 1 AND campaign_code = 'INVITE_2026_WELCOME'
);
