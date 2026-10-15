SELECT fix_type, COUNT(*) FROM crm_wallet_drift_log GROUP BY fix_type;
SELECT COUNT(*) FROM crm_wallet_orphan_log;
SELECT 'crm_member.balance<0' k, COUNT(*) v FROM crm_member WHERE balance < 0;
SELECT 'crm_member.balance=0' k, COUNT(*) v FROM crm_member WHERE balance = 0;
SELECT 'crm_member.balance>0' k, COUNT(*) v FROM crm_member WHERE balance > 0;
SELECT id, member_id, member_balance, wallet_balance, diff, fix_type, remark FROM crm_wallet_drift_log LIMIT 8;
