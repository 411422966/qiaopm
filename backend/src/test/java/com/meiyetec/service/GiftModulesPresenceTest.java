package com.meiyetec.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 4.30 GIFT 礼品卡模块完整性检查 + 缺表修复验证。
 */
class GiftModulesPresenceTest {

    @Test
    void gift_service_and_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.GiftCardService");
        assertNotNull(c.getAnnotation(org.springframework.web.bind.annotation.RestController.class));
        assertHasMethod(c, "purchase");
        assertHasMethod(c, "gift");
        assertHasMethod(c, "redeem");
        assertHasMethod(c, "refund");
        assertHasMethod(c, "list");
        assertHasMethod(c, "byCardNo");
        assertHasMethod(c, "transactions");
    }

    @Test
    void gift_required_entities_and_mappers() {
        assertNotNull(findClassIfExists("com.meiyetec.entity.GiftCard"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.GiftCardTransaction"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.GiftCardMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.GiftCardTransactionMapper"));
    }

    @Test
    void gift_required_tables_exist() {
        String[] required = {"gift_card", "gift_card_transaction"};
        for (String t : required) {
            assertEquals(true, tableExists(t), "GIFT 表不存在: " + t);
        }
    }

    @Test
    void gift_fixed_transaction_table_columns_match_entity() {
        assertEquals(true, tableExists("gift_card_transaction"));
        Integer colCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
            "WHERE table_schema = 'meiyetec' AND table_name = 'gift_card_transaction'",
            Integer.class);
        // GiftCardTransaction: id, tenant_id, card_id, tx_type, amount, balance,
        //   order_id, operator_id, notes, created_at = 10
        assertEquals(10, colCount, "gift_card_transaction 列数不对");
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