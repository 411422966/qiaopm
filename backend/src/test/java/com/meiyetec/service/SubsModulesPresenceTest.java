package com.meiyetec.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 4.29 SUBS 订阅会员模块完整性检查 + 缺表修复验证。
 */
class SubsModulesPresenceTest {

    @Test
    void subs_001_subscription_service_and_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.SubscriptionService");
        assertNotNull(c.getAnnotation(org.springframework.web.bind.annotation.RestController.class));
        assertHasMethod(c, "createPlan");
        assertHasMethod(c, "listPlans");
        assertHasMethod(c, "initDefault");
        assertHasMethod(c, "subscribe");
        assertHasMethod(c, "cancel");
        assertHasMethod(c, "pause");
        assertHasMethod(c, "resume");
        assertHasMethod(c, "byMember");
        assertHasMethod(c, "use");
        assertHasMethod(c, "triggerRenew");
        assertHasMethod(c, "dashboard");
    }

    @Test
    void subs_002_member_day_service_and_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.MemberDayService");
        assertNotNull(c.getAnnotation(org.springframework.web.bind.annotation.RestController.class));
        assertHasMethod(c, "createConfig");
        assertHasMethod(c, "updateConfig");
        assertHasMethod(c, "listConfigs");
        assertHasMethod(c, "deleteConfig");
        assertHasMethod(c, "initDefault");
        assertHasMethod(c, "trigger");
        assertHasMethod(c, "triggerByConfig");
        assertHasMethod(c, "listExecutions");
        assertHasMethod(c, "markUsed");
        assertHasMethod(c, "roiReport");
        assertHasMethod(c, "upcoming");
    }

    @Test
    void subs_003_lifecycle_controller_and_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.MemberLifecycleController");
        assertHasMethod(c, "dashboard");
        assertHasMethod(c, "funnel");
        assertHasMethod(c, "rulePage");
        assertHasMethod(c, "saveRule");
        assertHasMethod(c, "updateRule");
        assertHasMethod(c, "toggleRule");
        assertHasMethod(c, "deleteRule");
        assertHasMethod(c, "initDefault");
        assertHasMethod(c, "logPage");
        assertHasMethod(c, "trigger");
    }

    @Test
    void subs_004_dynamic_pricing_service_and_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.DynamicPricingService");
        assertNotNull(c.getAnnotation(org.springframework.web.bind.annotation.RestController.class));
        assertHasMethod(c, "createRule");
        assertHasMethod(c, "updateRule");
        assertHasMethod(c, "deleteRule");
        assertHasMethod(c, "rulePage");
        assertHasMethod(c, "quote");
    }

    @Test
    void subs_required_entities_and_mappers_present() {
        // SUBS-001
        assertNotNull(findClassIfExists("com.meiyetec.entity.SubscriptionPlan"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.SubscriptionMember"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.SubscriptionBilling"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.SubscriptionPlanMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.SubscriptionMemberMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.SubscriptionBillingMapper"));

        // SUBS-002
        assertNotNull(findClassIfExists("com.meiyetec.entity.MemberDayConfig"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.MemberDayExecution"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.MemberDayConfigMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.MemberDayExecutionMapper"));

        // SUBS-003
        assertNotNull(findClassIfExists("com.meiyetec.entity.MemberLifecycleRule"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.MemberLifecycleLog"));

        // SUBS-004
        assertNotNull(findClassIfExists("com.meiyetec.entity.MktDynamicPricingRule"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.MktPricingLog"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.MktDynamicPricingRuleMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.MktPricingLogMapper"));
    }

    @Test
    void subs_required_tables_exist() {
        String[] required = {
            // SUBS-001
            "subscription_plan", "subscription_member", "subscription_billing",
            // SUBS-002
            "member_day_config", "member_day_execution",
            // SUBS-003
            "member_lifecycle_rule", "member_lifecycle_log",
            // SUBS-004
            "mkt_dynamic_pricing_rule", "mkt_pricing_log"
        };
        for (String t : required) {
            assertEquals(true, tableExists(t), "SUBS 表不存在: " + t);
        }
    }

    @Test
    void subs_fixed_subscription_plan_table_columns_match_entity() {
        assertEquals(true, tableExists("subscription_plan"));
        Integer colCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema = 'meiyetec' AND table_name = 'subscription_plan'",
            Integer.class);
        // SubscriptionPlan: id, tenant_id, plan_code, plan_name, plan_type, price,
        //   billing_cycle_days, trial_days, service_count, included_services,
        //   discount_rate, gift_points, extra_benefits, description, cover_image,
        //   total_subscribers, active_subscribers, status, created_at, updated_at = 20
        assertEquals(20, colCount, "subscription_plan 列数不对");
    }

    @Test
    void subs_fixed_subscription_billing_table_columns_match_entity() {
        assertEquals(true, tableExists("subscription_billing"));
        Integer colCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema = 'meiyetec' AND table_name = 'subscription_billing'",
            Integer.class);
        // SubscriptionBilling: id, tenant_id, sub_member_id, member_id, plan_id, billing_date,
        //   amount, payment_method, payment_status, bill_no, transaction_id, error_message,
        //   created_at, paid_at = 14
        assertEquals(14, colCount, "subscription_billing 列数不对");
    }

    @Test
    void subs_fixed_member_day_execution_table_columns_match_entity() {
        assertEquals(true, tableExists("member_day_execution"));
        Integer colCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema = 'meiyetec' AND table_name = 'member_day_execution'",
            Integer.class);
        // MemberDayExecution: id, tenant_id, config_id, member_id, member_name, member_phone,
        //   trigger_reason, trigger_date, channel, discount_amount, gift_amount, gift_points,
        //   coupon_code, send_status, sent_at, used_at, order_id, error_message, created_at = 19
        assertEquals(19, colCount, "member_day_execution 列数不对");
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