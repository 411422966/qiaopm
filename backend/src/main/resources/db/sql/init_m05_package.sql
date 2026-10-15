-- =====================================================
-- M05 套餐次卡模块 - 数据库初始化
-- 创建日期：2026-08-01
-- =====================================================

USE meiyetec;

-- ----------------------------------------
-- 1. 套餐主表 crm_package
-- ----------------------------------------
DROP TABLE IF EXISTS crm_package;
CREATE TABLE crm_package (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       DEFAULT 1 COMMENT '租户ID',
    name            VARCHAR(100) NOT NULL COMMENT '套餐名称',
    code            VARCHAR(50)  DEFAULT NULL COMMENT '套餐编码',
    category        VARCHAR(50)  DEFAULT '通用' COMMENT '套餐分类（面部护理/身体护理/...）',
    cover           VARCHAR(255) DEFAULT NULL COMMENT '封面图',
    description     VARCHAR(500) DEFAULT NULL COMMENT '套餐描述',
    total_times     INT          NOT NULL COMMENT '总次数',
    price           DECIMAL(12,2) NOT NULL COMMENT '套餐价格',
    cost_price      DECIMAL(12,2) DEFAULT 0.00 COMMENT '成本价',
    valid_days      INT          DEFAULT 365 COMMENT '有效天数（购买后）',
    sale_count      INT          DEFAULT 0 COMMENT '已售数量',
    redeem_count    INT          DEFAULT 0 COMMENT '已核销次数',
    status          TINYINT      DEFAULT 1 COMMENT '状态:1上架 0下架',
    remark          VARCHAR(500) DEFAULT NULL,
    deleted         TINYINT      DEFAULT 0,
    create_by       BIGINT       DEFAULT NULL,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code, deleted),
    KEY idx_name (name),
    KEY idx_category (category),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐';

-- ----------------------------------------
-- 2. 套餐购买记录表 crm_package_order
-- ----------------------------------------
DROP TABLE IF EXISTS crm_package_order;
CREATE TABLE crm_package_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       DEFAULT 1,
    order_no        VARCHAR(32)  NOT NULL COMMENT '订单号',
    member_id       BIGINT       NOT NULL COMMENT '会员ID',
    package_id      BIGINT       NOT NULL COMMENT '套餐ID',
    package_name    VARCHAR(100) NOT NULL COMMENT '套餐名称(冗余)',
    total_times     INT          NOT NULL COMMENT '总次数',
    remaining_times INT          NOT NULL COMMENT '剩余次数',
    price           DECIMAL(12,2) NOT NULL COMMENT '购买价',
    pay_method      VARCHAR(20)  DEFAULT '微信' COMMENT '支付方式',
    operator_id     BIGINT       DEFAULT NULL COMMENT '操作人',
    buy_time        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
    expire_time     DATETIME     DEFAULT NULL COMMENT '过期时间',
    status          TINYINT      DEFAULT 1 COMMENT '状态:1正常 2已用完 3已过期 4已冻结',
    remark          VARCHAR(500) DEFAULT NULL,
    deleted         TINYINT      DEFAULT 0,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no, deleted),
    KEY idx_member (member_id),
    KEY idx_package (package_id),
    KEY idx_status (status),
    KEY idx_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐购买记录';

-- ----------------------------------------
-- 3. 套餐核销记录表 crm_package_redemption
-- ----------------------------------------
DROP TABLE IF EXISTS crm_package_redemption;
CREATE TABLE crm_package_redemption (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       DEFAULT 1,
    order_id        BIGINT       NOT NULL COMMENT '套餐订单ID',
    member_id       BIGINT       NOT NULL COMMENT '会员ID',
    package_id      BIGINT       NOT NULL COMMENT '套餐ID',
    times           INT          DEFAULT 1 COMMENT '本次核销次数',
    service_item    VARCHAR(100) DEFAULT NULL COMMENT '服务项目',
    beautician_id   BIGINT       DEFAULT NULL COMMENT '美容师ID',
    note            VARCHAR(500) DEFAULT NULL,
    operator_id     BIGINT       DEFAULT NULL,
    redeem_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    status          TINYINT      DEFAULT 1 COMMENT '1有效 0作废',
    deleted         TINYINT      DEFAULT 0,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_order (order_id),
    KEY idx_member (member_id),
    KEY idx_package (package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐核销记录';

-- ----------------------------------------
-- 4. 种子数据：4 套典型套餐
-- ----------------------------------------
INSERT INTO crm_package (tenant_id, name, code, category, description, total_times, price, cost_price, valid_days, status) VALUES
(1, '面部基础护理 5 次卡', 'PKG-FACE-05', '面部护理', '深层清洁 + 保湿护理，5 次一疗程',     5,   599.00,  200.00,  180, 1),
(1, '身体经络疏通 10 次卡', 'PKG-BODY-10', '身体护理', '全身经络疏通按摩，10 次套餐',         10,  1980.00,  600.00,  365, 1),
(1, '肩颈舒缓 20 次年卡',  'PKG-NECK-20', '肩颈护理', '适合上班族，缓解肩颈僵硬',           20,  3580.00, 1200.00,  365, 1),
(1, 'VIP 尊享无限次年卡',  'PKG-VIP-999', 'VIP尊享', '全年不限次护理（指定项目），含专属顾问', 999, 18800.00, 6000.00, 365, 1);

-- ----------------------------------------
-- 5. 一笔示例购买 + 一次核销（用于联调）
-- ----------------------------------------
INSERT INTO crm_package_order
(tenant_id, order_no, member_id, package_id, package_name, total_times, remaining_times, price, pay_method, operator_id, buy_time, expire_time, status, remark)
SELECT 1, 'PKG202608010001', m.id, p.id, p.name, p.total_times, p.total_times, p.price, '微信', 1,
       NOW(), DATE_ADD(NOW(), INTERVAL p.valid_days DAY), 1, '测试用购买记录'
FROM crm_member m, crm_package p
WHERE m.phone = '13800138001' AND p.code = 'PKG-FACE-05'
LIMIT 1;

INSERT INTO crm_package_redemption
(tenant_id, order_id, member_id, package_id, times, service_item, beautician_id, note, operator_id)
SELECT 1, o.id, o.member_id, o.package_id, 1, '面部清洁', 1, '第一次到店核销', 1
FROM crm_package_order o
WHERE o.order_no = 'PKG202608010001'
LIMIT 1;

UPDATE crm_package_order o
SET o.remaining_times = o.remaining_times - 1
WHERE o.order_no = 'PKG202608010001';

UPDATE crm_package p
JOIN crm_package_order o ON o.package_id = p.id
SET p.sale_count = p.sale_count + 1, p.redeem_count = p.redeem_count + 1
WHERE o.order_no = 'PKG202608010001';