SET NAMES utf8mb4;
USE meiyetec;

-- 4 套典型套餐
INSERT INTO crm_package (tenant_id, name, code, category, description, total_times, price, cost_price, valid_days, status) VALUES
(1, '面部基础护理 5 次卡',   'PKG-FACE-05', '面部护理', '深层清洁 + 保湿护理，5 次一疗程',     5,   599.00,  200.00,  180, 1),
(1, '身体经络疏通 10 次卡', 'PKG-BODY-10', '身体护理', '全身经络疏通按摩，10 次套餐',         10,  1980.00,  600.00,  365, 1),
(1, '肩颈舒缓 20 次年卡',   'PKG-NECK-20', '肩颈护理', '适合上班族，缓解肩颈僵硬',           20,  3580.00, 1200.00,  365, 1),
(1, 'VIP 尊享无限次年卡',   'PKG-VIP-999', 'VIP尊享', '全年不限次护理（指定项目），含专属顾问', 999, 18800.00, 6000.00, 365, 1);

-- 1 笔示例购买（如果 13800138001 会员不存在，也能正常建包，只是订单不会被插入）
INSERT INTO crm_package_order
(tenant_id, order_no, member_id, package_id, package_name, total_times, remaining_times, price, pay_method, operator_id, buy_time, expire_time, status, remark)
SELECT 1, 'PKG202608010001', m.id, p.id, p.name, p.total_times, p.total_times, p.price, 'wechat', 1,
       NOW(), DATE_ADD(NOW(), INTERVAL p.valid_days DAY), 1, '测试用购买记录'
FROM crm_member m, crm_package p
WHERE m.phone = '13800138001' AND p.code = 'PKG-FACE-05'
LIMIT 1;

-- 1 次核销
INSERT INTO crm_package_redemption
(tenant_id, order_id, member_id, package_id, times, service_item, beautician_id, note, operator_id)
SELECT 1, o.id, o.member_id, o.package_id, 1, '面部清洁', 1, '第一次到店核销', 1
FROM crm_package_order o
WHERE o.order_no = 'PKG202608010001'
LIMIT 1;

UPDATE crm_package_order o SET o.remaining_times = o.remaining_times - 1 WHERE o.order_no = 'PKG202608010001';

UPDATE crm_package p
JOIN crm_package_order o ON o.package_id = p.id
SET p.sale_count = p.sale_count + 1, p.redeem_count = p.redeem_count + 1
WHERE o.order_no = 'PKG202608010001';