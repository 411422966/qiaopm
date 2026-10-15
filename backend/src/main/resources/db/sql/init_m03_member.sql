-- =====================================================
-- M03 会员管理模块 - 数据库初始化
-- 创建日期：2026-07-31
-- 更新日期：2026-08-14 (P0-DB-001-F 回填 crm_wallet / crm_wallet_transaction)
-- =====================================================

USE meiyetec;

-- ----------------------------------------
-- 1. 会员标签表
-- ----------------------------------------
DROP TABLE IF EXISTS crm_member_tag;
CREATE TABLE crm_member_tag (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_name    VARCHAR(50)  NOT NULL COMMENT '标签名称',
    tag_color   VARCHAR(20)  DEFAULT '#409EFF' COMMENT '标签颜色',
    sort_order  INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 1 COMMENT '状态:1启用 0禁用',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tag_name (tag_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员标签';

-- ----------------------------------------
-- 2. 会员表
-- ----------------------------------------
DROP TABLE IF EXISTS crm_member;
CREATE TABLE crm_member (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_no     VARCHAR(32)  NOT NULL COMMENT '会员编号',
    name          VARCHAR(50)  NOT NULL COMMENT '姓名',
    phone         VARCHAR(20)  NOT NULL COMMENT '手机号',
    gender        TINYINT      DEFAULT 0 COMMENT '性别:0未知 1男 2女',
    birthday      DATE         DEFAULT NULL COMMENT '生日',
    level_id      BIGINT       DEFAULT NULL COMMENT '会员等级ID',
    store_id      BIGINT       DEFAULT NULL COMMENT '所属门店ID',
    balance       DECIMAL(12,2) DEFAULT 0.00 COMMENT '储值余额',
    points        INT          DEFAULT 0 COMMENT '积分',
    total_consume DECIMAL(12,2) DEFAULT 0.00 COMMENT '累计消费',
    avatar        VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    address       VARCHAR(255) DEFAULT NULL COMMENT '地址',
    remark        VARCHAR(500) DEFAULT NULL COMMENT '备注',
    status        TINYINT      DEFAULT 1 COMMENT '状态:1正常 2冻结',
    join_date     DATE         DEFAULT (CURRENT_DATE) COMMENT '入会日期',
    last_visit    DATETIME     DEFAULT NULL COMMENT '最后到店时间',
    deleted       TINYINT      DEFAULT 0,
    version       INT          DEFAULT 0 COMMENT '乐观锁版本号',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_phone (phone, deleted),
    UNIQUE KEY uk_member_no (member_no, deleted),
    KEY idx_name (name),
    KEY idx_level (level_id),
    KEY idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员';

-- ----------------------------------------
-- 3. 会员档案表
-- ----------------------------------------
DROP TABLE IF EXISTS crm_member_profile;
CREATE TABLE crm_member_profile (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT       NOT NULL COMMENT '会员ID',
    skin_type     VARCHAR(50)  DEFAULT NULL COMMENT '肤质类型（美容子行业画像）',
    hair_type     VARCHAR(50)  DEFAULT NULL COMMENT '发质类型（美发子行业画像）',
    nail_type     VARCHAR(50)  DEFAULT NULL COMMENT '甲面类型（美甲子行业画像）',
    allergy       VARCHAR(500) DEFAULT NULL COMMENT '过敏史',
    hobby         VARCHAR(500) DEFAULT NULL COMMENT '爱好',
    job           VARCHAR(100) DEFAULT NULL COMMENT '职业',
    wechat        VARCHAR(50)  DEFAULT NULL COMMENT '微信号',
    emergency_name  VARCHAR(50)  DEFAULT NULL COMMENT '紧急联系人',
    emergency_phone VARCHAR(20)  DEFAULT NULL COMMENT '紧急联系电话',
    consume_pref TEXT         DEFAULT NULL COMMENT '消费偏好(JSON)',
    visit_record TEXT         DEFAULT NULL COMMENT '到店记录(JSON)',
    deleted       TINYINT      DEFAULT 0,
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member (member_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员档案';

-- ----------------------------------------
-- 4. 会员-标签关联表
-- ----------------------------------------
DROP TABLE IF EXISTS crm_member_tag_rel;
CREATE TABLE crm_member_tag_rel (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT NOT NULL,
    tag_id      BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_member_tag (member_id, tag_id),
    KEY idx_member (member_id),
    KEY idx_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员标签关联';

-- ----------------------------------------
-- 5. 初始化测试数据
-- ----------------------------------------
INSERT INTO crm_member_tag (tag_name, tag_color, sort_order) VALUES
('VIP客户', '#F56C6C', 1),
('敏感肌', '#E6A23C', 2),
('回头客', '#67C23A', 3),
('新客户', '#909399', 4),
('高消费', '#409EFF', 5);

INSERT INTO crm_member (member_no, name, phone, gender, level_id, store_id, balance, points, total_consume, address, remark) VALUES
('M202607001', '李雅琳', '13800138001', 2, 1, 1, 5800.00, 2860, 12680.00, '北京市朝阳区望京SOHO', '忠实客户'),
('M202607002', '王晓彤', '13800138002', 2, 2, 1, 2200.00, 1200, 4580.00, '北京市海淀区中关村', '敏感肌'),
('M202607003', '张敏华', '13800138003', 2, 3, 2, 500.00, 350, 1200.00, '北京市朝阳区国贸', ''),
('M202607004', '陈丽君', '13800138004', 2, 1, 1, 12800.00, 5200, 28900.00, '北京市东城区王府井', '高消费VIP'),
('M202607005', '刘莎莎', '13800138005', 2, 2, 2, 3500.00, 1800, 7200.00, '北京市西城区西单', ''),
('M202607006', '赵晓燕', '13800138006', 2, 1, 1, 15600.00, 6800, 35800.00, '北京市朝阳区三里屯', '回头客'),
('M202607007', '孙美丽', '13800138007', 2, 3, 1, 800.00, 480, 2400.00, '北京市丰台区方庄', '新客户'),
('M202607008', '周雪梅', '13800138008', 2, 2, 2, 4500.00, 2200, 9800.00, '北京市朝阳区CBD', '');

INSERT INTO crm_member_profile (member_id, skin_type, allergy, hobby, job, wechat) VALUES
(1, '干性肌肤', '花粉', '瑜伽/旅游', '外企经理', 'lyl_888'),
(2, '敏感性肌肤', '海鲜', '阅读/电影', '设计师', 'wxt_design'),
(3, '混合性肌肤', '无', '健身', '教师', 'zmh_teacher'),
(4, '干性肌肤', '酒精', '高尔夫/红酒', '投资人', 'clj_vip');

-- 会员-标签关联
INSERT INTO crm_member_tag_rel (member_id, tag_id) VALUES
(1, 1), (1, 3),
(2, 2),
(3, 4),
(4, 1), (4, 5),
(6, 3), (6, 5),
(7, 4);

-- =============================================
-- 6. 会员钱包表 crm_wallet（P0-DB-001-F 回填）
-- =============================================
-- 历史来源：fix_missing_tables.sql:57-70
-- 上下文：WalletController.ensureWallet / doRecharge / doConsume / doRefund
-- 注意：CrmWallet Entity 暂未含 tenantId 字段（out-of-scope），tenant_id 列先建好
CREATE TABLE IF NOT EXISTS crm_wallet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '钱包ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    member_id BIGINT NOT NULL COMMENT '会员ID',
    balance DECIMAL(12,2) DEFAULT 0.00 COMMENT '可用余额',
    total_recharge DECIMAL(12,2) DEFAULT 0.00 COMMENT '累计充值',
    total_consume DECIMAL(12,2) DEFAULT 0.00 COMMENT '累计消费',
    frozen_amount DECIMAL(12,2) DEFAULT 0.00 COMMENT '冻结金额',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_member (member_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员钱包表';

-- =============================================
-- 7. 钱包流水表 crm_wallet_transaction（P0-DB-001-F 回填）
-- =============================================
-- 历史来源：fix_missing_tables.sql:75-95
-- 上下文：WalletController 各路径写入 / 报表统计
-- 注意：P0-MONEY-003 后续替换 DCEP transactionId 字段由雪花算法生成
CREATE TABLE IF NOT EXISTS crm_wallet_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '流水ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    member_id BIGINT NOT NULL COMMENT '会员ID',
    type TINYINT NOT NULL COMMENT '类型：1充值 2消费 3退款 4赠送 5冻结 6解冻',
    amount DECIMAL(12,2) NOT NULL COMMENT '变动金额（正数）',
    balance_before DECIMAL(12,2) DEFAULT NULL COMMENT '变动前余额',
    balance_after DECIMAL(12,2) DEFAULT NULL COMMENT '变动后余额',
    gift_amount DECIMAL(12,2) DEFAULT 0.00 COMMENT '赠送金额',
    pay_method VARCHAR(20) DEFAULT NULL COMMENT '支付方式',
    order_id BIGINT DEFAULT NULL COMMENT '关联订单',
    operator_id BIGINT DEFAULT NULL COMMENT '操作人',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_member (member_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_type (type),
    INDEX idx_created_at (created_at),
    INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包流水表';

-- 验证
SELECT COUNT(*) AS '会员总数' FROM crm_member;
SELECT COUNT(*) AS '标签总数' FROM crm_member_tag;
SELECT COUNT(*) AS '档案总数' FROM crm_member_profile;
SELECT COUNT(*) AS '钱包总数' FROM crm_wallet;
SELECT COUNT(*) AS '钱包流水总数' FROM crm_wallet_transaction;
