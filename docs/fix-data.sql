-- 修复所有会员中文名字
SET NAMES utf8mb4;

UPDATE crm_member SET name='李雅琳' WHERE id=1;
UPDATE crm_member SET name='王晓琳' WHERE id=2;
UPDATE crm_member SET name='张敏娜' WHERE id=3;
UPDATE crm_member SET name='陈丽君' WHERE id=4;
UPDATE crm_member SET name='刘莎莎' WHERE id=5;
UPDATE crm_member SET name='赵晓敏' WHERE id=6;
UPDATE crm_member SET name='孙美玲' WHERE id=7;
UPDATE crm_member SET name='周雪婷' WHERE id=8;
UPDATE crm_member SET name='林小雨' WHERE id=9;
UPDATE crm_member SET name='梁志耀' WHERE id=10;

-- 同时修复会员手机号字段（如有乱码）
UPDATE crm_member SET phone='13800138001' WHERE id=1;
UPDATE crm_member SET phone='13800138002' WHERE id=2;
UPDATE crm_member SET phone='13800138003' WHERE id=3;
UPDATE crm_member SET phone='13800138004' WHERE id=4;
UPDATE crm_member SET phone='13800138005' WHERE id=5;
UPDATE crm_member SET phone='13800138006' WHERE id=6;
UPDATE crm_member SET phone='13800138007' WHERE id=7;
UPDATE crm_member SET phone='13800138008' WHERE id=8;
UPDATE crm_member SET phone='13800138009' WHERE id=9;
UPDATE crm_member SET phone='13800138010' WHERE id=10;

-- 修复订单备注/退款原因（如有乱码）
UPDATE pos_order SET refund_reason='测试退款' WHERE refund_reason='????' OR refund_reason LIKE '%?%';
UPDATE pos_order SET remark='M07 收银测试' WHERE remark LIKE '%?%';
UPDATE pos_order SET cashier_name='系统管理员' WHERE cashier_name IS NULL;

SELECT id, name FROM crm_member ORDER BY id;
