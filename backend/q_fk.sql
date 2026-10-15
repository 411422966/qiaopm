SELECT COUNT(*) AS FK_total
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'meiyetec' AND REFERENCED_TABLE_SCHEMA IS NOT NULL;
SELECT COUNT(*) AS orphan_tenant_id
FROM crm_wallet w LEFT JOIN sys_tenant t ON w.tenant_id = t.id
WHERE w.tenant_id IS NOT NULL AND t.id IS NULL;
SELECT COUNT(*) AS wallet_drift_orphans
FROM crm_wallet w LEFT JOIN sys_tenant t ON w.tenant_id = t.id
WHERE t.id IS NULL;
SELECT id, tenant_id, member_id, balance, version FROM crm_wallet WHERE tenant_id IS NULL LIMIT 5;
SELECT id, tenant_id, member_id, balance, version FROM crm_wallet WHERE tenant_id = 1 LIMIT 3;
SELECT channel_code, env_mode, COUNT(*) FROM pay_dcep_channel GROUP BY channel_code, env_mode ORDER BY env_mode;
SELECT status, COUNT(*) FROM pay_dcep_order GROUP BY status;
SELECT sign_verified, COUNT(*) FROM pay_dcep_notify GROUP BY sign_verified;
