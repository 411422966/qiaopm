SELECT id, tenant_id, dcep_order_id, notify_id, sign_verified, sign_algo, process_msg FROM pay_dcep_notify WHERE sign_verified = 0;
SELECT id, action_type, COUNT(*) FROM crm_wallet_drift_log GROUP BY id, action_type ORDER BY id LIMIT 8;
SELECT COUNT(*) FROM crm_wallet_orphan_log;
SELECT 'crm_member.balance<0' k, COUNT(*) v FROM crm_member WHERE balance < 0;
SELECT 'crm_member.balance=0' k, COUNT(*) v FROM crm_member WHERE balance = 0;
SELECT 'crm_member.balance>0' k, COUNT(*) v FROM crm_member WHERE balance > 0;
