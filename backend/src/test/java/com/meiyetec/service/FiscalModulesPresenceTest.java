package com.meiyetec.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 4.28 FISCAL 财税模块关键 controller/service/entity 完整、DB 表齐全。
 */
class FiscalModulesPresenceTest {

    @Test
    void fiscal_001_002_fiscal_controller_exposes_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.FiscalService");
        assertHasMethod(c, "issue");
        assertHasMethod(c, "listInvoices");
        assertHasMethod(c, "getInvoice");
        assertHasMethod(c, "voidInvoice");
        assertHasMethod(c, "listChannels");
        assertHasMethod(c, "dashboard");
        // 必须包含 @RestController 注解（FiscalService 本身就是 controller）
        assertNotNull(c.getAnnotation(org.springframework.web.bind.annotation.RestController.class));
    }

    @Test
    void fiscal_003_reconciliation_controller_and_service_present() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.ReconciliationController");
        assertHasMethod(c, "run");
        assertHasMethod(c, "page");
        assertHasMethod(c, "handle");

        Class<?> svc = findClassIfExists("com.meiyetec.service.ReconciliationService");
        assertNotNull(svc);
        assertHasMethod(svc, "run");
        assertHasMethod(svc, "page");
        assertHasMethod(svc, "handle");
        // ChannelFetcher 必须真实存在（不是 Stub）
        Class<?> fetcher = findClassIfExists("com.meiyetec.service.ReconciliationService$ChannelFetcher");
        assertNotNull(fetcher, "缺少 ChannelFetcher 抽象");
    }

    @Test
    void fiscal_004_recharge_audit_controller_and_service_present() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.RechargeAuditController");
        assertHasMethod(c, "apply");
        assertHasMethod(c, "approve");
        assertHasMethod(c, "reject");
        assertHasMethod(c, "cancel");
        assertHasMethod(c, "page");
        assertHasMethod(c, "detail");
        assertHasMethod(c, "logList");
        assertHasMethod(c, "freeze");
        assertHasMethod(c, "unfreeze");

        Class<?> svc = findClassIfExists("com.meiyetec.service.RechargeAuditService");
        assertNotNull(svc);
        assertHasMethod(svc, "applyRecharge");
        assertHasMethod(svc, "approve");
        assertHasMethod(svc, "reject");
        assertHasMethod(svc, "cancel");
        assertHasMethod(svc, "freezeAmount");
        assertHasMethod(svc, "unfreezeAmount");
    }

    @Test
    void fiscal_required_entities_and_mappers_present() {
        assertNotNull(findClassIfExists("com.meiyetec.entity.FiscalInvoice"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.PosReconciliation"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.RechargeAudit"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.RechargeAuditLog"));

        assertNotNull(findClassIfExists("com.meiyetec.mapper.FiscalInvoiceMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.PosReconciliationMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.RechargeAuditMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.RechargeAuditLogMapper"));
    }

    @Test
    void fiscal_required_tables_exist_in_db() {
        String[] required = {
            "fiscal_invoice",
            "pos_reconciliation",
            "crm_recharge_audit",
            "crm_recharge_audit_log"
        };
        for (String t : required) {
            assertEquals(true, tableExists(t), "FISCAL 表不存在: " + t);
        }
    }

    @Test
    void fiscal_recharge_audit_log_entity_columns_match_db() {
        // 校验 entity 与 DB 表结构一致（防止后续字段漂移）
        assertEquals(true, tableExists("crm_recharge_audit_log"));
        Integer colCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema = 'meiyetec' AND table_name = 'crm_recharge_audit_log'",
            Integer.class);
        // entity 字段: id, tenant_id, audit_id, audit_no, action, operator_id,
        // operator_name, remark, before_status, after_status, create_time, deleted = 12
        assertEquals(12, colCount, "crm_recharge_audit_log 列数不对");
    }

    private boolean tableExists(String tableName) {
        try {
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'meiyetec' AND table_name = ?",
                Integer.class, tableName);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    "jdbc:mysql://localhost:3307/meiyetec?useUnicode=true&characterEncoding=utf-8",
                    "root", "root123456"));

    private void assertHasMethod(Class<?> c, String name) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) return;
        }
        fail("缺少方法: " + name + " on " + c.getSimpleName());
    }

    private Class<?> findClassIfExists(String fqn) {
        try { return Class.forName(fqn); } catch (ClassNotFoundException e) { return null; }
    }
}