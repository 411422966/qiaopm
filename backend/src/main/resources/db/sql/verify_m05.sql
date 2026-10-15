SELECT 'crm_package' AS tbl, COUNT(*) AS cnt FROM crm_package
UNION ALL SELECT 'crm_package_order', COUNT(*) FROM crm_package_order
UNION ALL SELECT 'crm_package_redemption', COUNT(*) FROM crm_package_redemption;
SELECT id, name, total_times, price, status FROM crm_package;
SELECT id, order_no, member_id, package_id, total_times, remaining_times, status FROM crm_package_order;
SELECT id, order_id, member_id, package_id, times, service_item FROM crm_package_redemption;