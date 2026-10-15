SELECT status, COUNT(*) FROM pay_dcep_order GROUP BY status;
SELECT sign_verified, COUNT(*) FROM pay_dcep_notify GROUP BY sign_verified;
SELECT ph, COUNT(*) FROM pay_dcep_notify WHERE sign_verified = 0 GROUP BY ph;
SELECT 'crm_wallet.balance < 0' k, COUNT(*) v FROM crm_wallet WHERE balance < 0
UNION ALL SELECT 'crm_wallet.balance = 0' k, COUNT(*) v FROM crm_wallet WHERE balance = 0
UNION ALL SELECT 'crm_wallet.balance > 0' k, COUNT(*) v FROM crm_wallet WHERE balance > 0
UNION ALL SELECT 'crm_wallet_drift_log.action=AUTO_FIX' k, COUNT(*) v FROM crm_wallet_drift_log WHERE action = 'AUTO_FIX'
UNION ALL SELECT 'crm_wallet_drift_log.action=PRE_FIX_BACKUP' k, COUNT(*) v FROM crm_wallet_drift_log WHERE action = 'PRE_FIX_BACKUP'
UNION ALL SELECT 'crm_wallet_drift_log.total' k, COUNT(*) v FROM crm_wallet_drift_log;
SELECT action_type, COUNT(*) FROM crm_wallet_drift_log GROUP BY action_type;
SELECT 'crm_wallet_orphan_log.total' k, COUNT(*) v FROM crm_wallet_orphan_log;
SELECT 'crm_member.balance < 0' k, COUNT(*) v FROM crm_member WHERE balance < 0;
SELECT 'crm_member.balance != balance_wallet sum' k, COUNT(*) v
FROM crm_member m LEFT JOIN crm_wallet w ON m.id = w.member_id
WHERE ROUND(m.balance,2) <> ROUND(IFNULL(w.balance,0),2) AND w.tenant_id IS NOT NULL;
