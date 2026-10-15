SELECT 'pos_refund_reverse_log.ref_id NULL' k, COUNT(*) v FROM pos_refund_reverse_log WHERE ref_id IS NULL
UNION ALL SELECT 'pos_refund_reverse_log.total' k, COUNT(*) v FROM pos_refund_reverse_log
UNION ALL SELECT 'sys_user.tenant_id=1' k, COUNT(*) v FROM sys_user WHERE tenant_id = 1
UNION ALL SELECT 'sys_user.tenant_id=2' k, COUNT(*) v FROM sys_user WHERE tenant_id = 2
UNION ALL SELECT 'sys_user.tenant_id NULL' k, COUNT(*) v FROM sys_user WHERE tenant_id IS NULL
UNION ALL SELECT 'sys_tenant.id=1' k, COUNT(*) v FROM sys_tenant WHERE id = 1
UNION ALL SELECT 'sys_tenant.id=2' k, COUNT(*) v FROM sys_tenant WHERE id = 2
UNION ALL SELECT 'sys_tenant.id=900001 (PosController test)' k, COUNT(*) v FROM sys_tenant WHERE id = 900001
UNION ALL SELECT 'sys_tenant.id=900002' k, COUNT(*) v FROM sys_tenant WHERE id = 900002
UNION ALL SELECT 'pos_order.tenant_id distinct' k, COUNT(DISTINCT tenant_id) v FROM pos_order
UNION ALL SELECT 'pos_order.tenant_id in (900001,900002)' k, COUNT(*) v FROM pos_order WHERE tenant_id IN (900001,900002)
UNION ALL SELECT 'pos_payment.tenant_id in (900001,900002)' k, COUNT(*) v FROM pos_payment WHERE tenant_id IN (900001,900002);
