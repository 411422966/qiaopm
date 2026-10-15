UPDATE crm_package_order SET expire_time = '2025-01-01 00:00:00' WHERE order_no = 'PKG202608010001';
SELECT id, order_no, expire_time, status FROM crm_package_order WHERE order_no = 'PKG202608010001';