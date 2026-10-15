SET NAMES utf8mb4;
SET sql_mode = 'NO_ENGINE_SUBSTITUTION';

USE meiyetec;

DROP TABLE IF EXISTS crm_package_order;
CREATE TABLE crm_package_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       DEFAULT 1,
    order_no        VARCHAR(32)  NOT NULL COMMENT '订单号',
    member_id       BIGINT       NOT NULL,
    package_id      BIGINT       NOT NULL,
    package_name    VARCHAR(100) NOT NULL,
    total_times     INT          NOT NULL,
    remaining_times INT          NOT NULL,
    price           DECIMAL(12,2) NOT NULL,
    pay_method      VARCHAR(20)  DEFAULT '微信',
    operator_id     BIGINT       DEFAULT NULL,
    buy_time        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    expire_time     DATETIME     DEFAULT NULL,
    status          TINYINT      DEFAULT 1,
    remark          VARCHAR(500) DEFAULT NULL,
    deleted         TINYINT      DEFAULT 0,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no, deleted),
    KEY idx_member (member_id),
    KEY idx_package (package_id),
    KEY idx_status (status),
    KEY idx_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐订单';

DROP TABLE IF EXISTS crm_package_redemption;
CREATE TABLE crm_package_redemption (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       DEFAULT 1,
    order_id        BIGINT       NOT NULL,
    member_id       BIGINT       NOT NULL,
    package_id      BIGINT       NOT NULL,
    times           INT          DEFAULT 1,
    service_item    VARCHAR(100) DEFAULT NULL,
    beautician_id   BIGINT       DEFAULT NULL,
    note            VARCHAR(500) DEFAULT NULL,
    operator_id     BIGINT       DEFAULT NULL,
    redeem_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    status          TINYINT      DEFAULT 1,
    deleted         TINYINT      DEFAULT 0,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_order (order_id),
    KEY idx_member (member_id),
    KEY idx_package (package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐核销记录';

INSERT INTO crm_package_order
(tenant_id, order_no, member_id, package_id, package_name, total_times, remaining_times, price, pay_method, operator_id, buy_time, expire_time, status, remark)
SELECT 1, 'PKG202608010001', m.id, p.id, p.name, p.total_times, p.total_times, p.price, 'wechat', 1,
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