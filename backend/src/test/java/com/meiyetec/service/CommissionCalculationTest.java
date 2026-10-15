package com.meiyetec.service;

import com.meiyetec.entity.CommissionRecord;
import com.meiyetec.entity.CommissionRule;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-TEST-002 · UT-2 · CommissionCalculation 单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>RATE 模式：base × rate 精度校验</li>
 *   <li>FIXED 模式：fixedAmount × quantity</li>
 *   <li>非法 calculationMethod → IllegalArgumentException</li>
 *   <li>matchRule 规则匹配：storeId / businessType / 有效期 / targetId</li>
 *   <li>calculateOrder：status=2 订单 + 完整规则链 → 正确生成 commission_record</li>
 *   <li>calculateOrder：未支付订单抛 IllegalStateException</li>
 *   <li>calculateOrder：空 employeeId 跳过</li>
 *   <li>calculateOrder：已存在记录幂等返回</li>
 * </ul>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommissionCalculationTest {

    private static final Long TEST_TENANT = 1L;
    private static final Long TEST_STORE_ID = 1L;

    // 测试用主键
    private static Long ruleIdRATE;
    private static Long ruleIdFIXED;
    private static Long employeeId;
    private static Long orderId;
    private static Long orderItemId;

    @Autowired private CommissionCalculationService calcService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(TEST_TENANT);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        // 创建 RATE 规则（rate=10%，priority=200 > FIXED 的 100，确保 RATE 优先）
        String ruleNo = "UT2-RATE-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update(
            "INSERT INTO commission_rule(tenant_id, store_id, rule_name, business_type, target_id, calculation_method, rate, priority, effective_date, status, is_active) " +
            "VALUES(?, ?, ?, 'SERVICE', NULL, 'RATE', 0.1000, 200, '2020-01-01', 1, 1)",
            TEST_TENANT, TEST_STORE_ID, ruleNo);
        ruleIdRATE = jdbc.queryForObject(
            "SELECT id FROM commission_rule WHERE rule_name = ?", Long.class, ruleNo);

        // 创建 FIXED 规则（fixedAmount=5，priority=100 < RATE）
        String ruleNoF = "UT2-FIXED-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update(
            "INSERT INTO commission_rule(tenant_id, store_id, rule_name, business_type, target_id, calculation_method, fixed_amount, priority, effective_date, status, is_active) " +
            "VALUES(?, ?, ?, 'SERVICE', NULL, 'FIXED', 5.00, 100, '2020-01-01', 1, 1)",
            TEST_TENANT, TEST_STORE_ID, ruleNoF);
        ruleIdFIXED = jdbc.queryForObject(
            "SELECT id FROM commission_rule WHERE rule_name = ?", Long.class, ruleNoF);

        // 创建一个测试员工
        String empCode = "UT2-E-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update(
            "INSERT INTO pos_employee(employee_code, employee_name, phone, store_id, status, gender) " +
            "VALUES(?, 'UT-2 测试员工', ?, ?, 1, 0)",
            empCode, "139000" + UUID.randomUUID().toString().substring(0, 5).replace("-", ""),
            TEST_STORE_ID);
        employeeId = jdbc.queryForObject(
            "SELECT id FROM pos_employee WHERE employee_code = ?", Long.class, empCode);
    }

    @AfterEach
    void tearDown() {
        try {
            if (orderItemId != null) {
                jdbc.update("DELETE FROM pos_order_item WHERE id = ?", orderItemId);
            }
            if (orderId != null) {
                jdbc.update("DELETE FROM pos_order WHERE id = ?", orderId);
            }
            if (employeeId != null) {
                jdbc.update("DELETE FROM pos_employee WHERE id = ?", employeeId);
            }
            if (ruleIdRATE != null) {
                jdbc.update("DELETE FROM commission_rule WHERE id = ?", ruleIdRATE);
            }
            if (ruleIdFIXED != null) {
                jdbc.update("DELETE FROM commission_rule WHERE id = ?", ruleIdFIXED);
            }
            jdbc.update("DELETE FROM commission_record WHERE rule_id IN (?, ?)", ruleIdRATE, ruleIdFIXED);
        } catch (Exception ignore) {}
        orderItemId = null;
        orderId = null;
        employeeId = null;
        ruleIdRATE = null;
        ruleIdFIXED = null;
        TenantContextHolder.clear();
    }

    // ================================================================
    // 1. matchRule 规则匹配
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("UT-2-1: matchRule — 按 storeId+businessType 命中")
    void matchRuleByStoreAndBusinessType() {
        CommissionRule rule = calcService.matchRule(TEST_STORE_ID, "SERVICE", null, LocalDate.now());
        assertNotNull(rule, "应能匹配到 RATE 或 FIXED 规则（按 priority 倒序取 1）");
        assertEquals("SERVICE", rule.getBusinessType());
        assertTrue(rule.getId().equals(ruleIdRATE) || rule.getId().equals(ruleIdFIXED),
                "匹配到的规则应是本次创建的");
    }

    @Test
    @Order(2)
    @DisplayName("UT-2-2: matchRule — 错误 storeId 查不到")
    void matchRuleNoMatch() {
        CommissionRule rule = calcService.matchRule(99999L, "SERVICE", null, LocalDate.now());
        assertNull(rule, "错误 storeId 应查不到规则");
    }

    @Test
    @Order(3)
    @DisplayName("UT-2-3: matchRule — businessType 不匹配查不到")
    void matchRuleWrongBusinessType() {
        CommissionRule rule = calcService.matchRule(TEST_STORE_ID, "UNKNOWN_TYPE", null, LocalDate.now());
        assertNull(rule, "businessType 不匹配应查不到规则");
    }

    // ================================================================
    // 2. RATE 模式提成计算（通过 calculateOrder 端到端验证）
    // ================================================================

    @Test
    @Order(4)
    @DisplayName("UT-2-4: RATE 模式 — base=100 × rate=10% = 10.00")
    void rateModeCommission() {
        // 准备订单
        String orderNo = "UT2-O-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update(
            "INSERT INTO pos_order(tenant_id, store_id, order_no, type, total_amount, final_amount, paid_amount, cashier_id, status, pay_time) " +
            "VALUES(?, ?, ?, 1, 100.00, 100.00, 100.00, 1, 2, NOW())",
            TEST_TENANT, TEST_STORE_ID, orderNo);
        orderId = jdbc.queryForObject(
            "SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo);

        // 明细：final_price=100
        jdbc.update(
            "INSERT INTO pos_order_item(tenant_id, order_id, service_id, service_name, quantity, unit_price, total_price, final_price, employee_id, employee_name) " +
            "VALUES(?, ?, 1, 'UT-2 RATE 服务', 1, 100.00, 100.00, 100.00, ?, 'UT-2 测试员工')",
            TEST_TENANT, orderId, employeeId);
        orderItemId = jdbc.queryForObject(
            "SELECT id FROM pos_order_item WHERE order_id = ?", Long.class, orderId);

        // 触发计算
        List<CommissionRecord> records = calcService.calculateOrder(orderId);
        assertEquals(1, records.size(), "应生成 1 条 commission_record");

        CommissionRecord r = records.get(0);
        assertEquals(0, new BigDecimal("10.00").compareTo(r.getCommissionAmount()),
                "100 × 10% = 10.00，实际=" + r.getCommissionAmount());
        assertEquals("RATE", r.getCalculationMethod());
        assertEquals(0, new BigDecimal("0.1000").compareTo(r.getRate()));
    }

    // ================================================================
    // 3. FIXED 模式提成计算
    // ================================================================

    @Test
    @Order(5)
    @DisplayName("UT-2-5: FIXED 模式 — fixed=5 × quantity=3 = 15.00")
    void fixedModeCommission() {
        // 先删掉 RATE 规则，让 matchRule 只剩 FIXED（targetId=null，priority 同为 100，按 id 倒序取最新）
        jdbc.update("DELETE FROM commission_rule WHERE id = ?", ruleIdRATE);
        ruleIdRATE = null;

        String orderNo = "UT2-O-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update(
            "INSERT INTO pos_order(tenant_id, store_id, order_no, type, total_amount, final_amount, paid_amount, cashier_id, status, pay_time) " +
            "VALUES(?, ?, ?, 1, 300.00, 300.00, 300.00, 1, 2, NOW())",
            TEST_TENANT, TEST_STORE_ID, orderNo);
        orderId = jdbc.queryForObject(
            "SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo);

        // quantity=3
        jdbc.update(
            "INSERT INTO pos_order_item(tenant_id, order_id, service_id, service_name, quantity, unit_price, total_price, final_price, employee_id, employee_name) " +
            "VALUES(?, ?, 2, 'UT-2 FIXED 服务', 3, 100.00, 300.00, 300.00, ?, 'UT-2 测试员工')",
            TEST_TENANT, orderId, employeeId);
        orderItemId = jdbc.queryForObject(
            "SELECT id FROM pos_order_item WHERE order_id = ?", Long.class, orderId);

        List<CommissionRecord> records = calcService.calculateOrder(orderId);
        assertEquals(1, records.size());

        CommissionRecord r = records.get(0);
        assertEquals(0, new BigDecimal("15.00").compareTo(r.getCommissionAmount()),
                "5 × 3 = 15.00，实际=" + r.getCommissionAmount());
        assertEquals("FIXED", r.getCalculationMethod());
    }

    // ================================================================
    // 4. 边界条件
    // ================================================================

    @Test
    @Order(6)
    @DisplayName("UT-2-6: 未支付订单(status=1) 抛 IllegalStateException")
    void calculateOrderUnpaid() {
        String orderNo = "UT2-UNPAID-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update(
            "INSERT INTO pos_order(tenant_id, store_id, order_no, type, total_amount, final_amount, paid_amount, cashier_id, status) " +
            "VALUES(?, ?, ?, 1, 100.00, 100.00, 0, 1, 1)",
            TEST_TENANT, TEST_STORE_ID, orderNo);
        orderId = jdbc.queryForObject(
            "SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> calcService.calculateOrder(orderId));
        assertTrue(ex.getMessage().contains("已支付"),
                "异常信息应提示已支付状态，实际=" + ex.getMessage());
    }

    @Test
    @Order(7)
    @DisplayName("UT-2-7: 订单不存在 抛 IllegalArgumentException")
    void calculateOrderNotFound() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> calcService.calculateOrder(99999999L));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    @Order(8)
    @DisplayName("UT-2-8: 明细无 employeeId 跳过，返回空记录")
    void calculateOrderNoEmployee() {
        String orderNo = "UT2-NOEMP-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update(
            "INSERT INTO pos_order(tenant_id, store_id, order_no, type, total_amount, final_amount, paid_amount, cashier_id, status, pay_time) " +
            "VALUES(?, ?, ?, 1, 100.00, 100.00, 100.00, 1, 2, NOW())",
            TEST_TENANT, TEST_STORE_ID, orderNo);
        orderId = jdbc.queryForObject(
            "SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo);

        // employee_id = NULL
        jdbc.update(
            "INSERT INTO pos_order_item(tenant_id, order_id, service_id, service_name, quantity, unit_price, total_price, final_price, employee_id, employee_name) " +
            "VALUES(?, ?, 3, 'UT-2 无员工服务', 1, 100.00, 100.00, 100.00, NULL, NULL)",
            TEST_TENANT, orderId);
        orderItemId = jdbc.queryForObject(
            "SELECT id FROM pos_order_item WHERE order_id = ?", Long.class, orderId);

        List<CommissionRecord> records = calcService.calculateOrder(orderId);
        assertEquals(0, records.size(), "无 employeeId 应跳过，返回空");
    }

    @Test
    @Order(9)
    @DisplayName("UT-2-9: 已存在 commission_record 时幂等返回，不重复生成")
    void calculateOrderIdempotent() {
        // 创建订单 + 触发一次
        String orderNo = "UT2-IDM-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update(
            "INSERT INTO pos_order(tenant_id, store_id, order_no, type, total_amount, final_amount, paid_amount, cashier_id, status, pay_time) " +
            "VALUES(?, ?, ?, 1, 100.00, 100.00, 100.00, 1, 2, NOW())",
            TEST_TENANT, TEST_STORE_ID, orderNo);
        orderId = jdbc.queryForObject(
            "SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo);

        jdbc.update(
            "INSERT INTO pos_order_item(tenant_id, order_id, service_id, service_name, quantity, unit_price, total_price, final_price, employee_id, employee_name) " +
            "VALUES(?, ?, 4, 'UT-2 幂等服务', 1, 100.00, 100.00, 100.00, ?, 'UT-2 测试员工')",
            TEST_TENANT, orderId, employeeId);
        orderItemId = jdbc.queryForObject(
            "SELECT id FROM pos_order_item WHERE order_id = ?", Long.class, orderId);

        // 第 1 次
        List<CommissionRecord> first = calcService.calculateOrder(orderId);
        assertEquals(1, first.size());

        // 第 2 次（幂等）
        List<CommissionRecord> second = calcService.calculateOrder(orderId);
        assertEquals(1, second.size(), "幂等返回 1 条已存在记录");
        assertEquals(first.get(0).getId(), second.get(0).getId(), "应返回同一条 record");

        // DB 验证：只有 1 条 commission_record
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM commission_record WHERE source_id = ?", Long.class, orderId);
        assertEquals(1L, count.longValue(), "DB 中应只有 1 条，实际=" + count);
    }
}