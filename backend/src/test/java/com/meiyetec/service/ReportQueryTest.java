package com.meiyetec.service;

import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-TEST-002 · UT-4 · ReportV2Service SQL 正确性测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>dashboard：销售 GMV / net_revenue / refund 聚合正确性</li>
 *   <li>dashboard：跨租户隔离（不同 tenantId 数据不混入）</li>
 *   <li>dashboard：日期范围过滤</li>
 *   <li>dashboard：store_id 过滤</li>
 *   <li>salesDaily：按日聚合正确性</li>
 *   <li>employeeCommission：按员工聚合 + 软删除过滤</li>
 * </ul>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportQueryTest {

    private static final Long TEST_TENANT_A = 1L;
    private static final Long TEST_TENANT_B = 2L;

    private static final Long TEST_STORE_ID = 1L;
    private static final String TEST_DATE_PREFIX = "2099-08";  // 用未来月份测试

    // 测试订单 IDs（tearDown 用）
    private static List<Long> testOrderIds;

    @Autowired private ReportV2Service reportService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(TEST_TENANT_A);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        // 先清掉测试月份的残留
        jdbc.update("DELETE FROM pos_order WHERE order_no LIKE 'UT4-%'");
    }

    @AfterEach
    void tearDown() {
        try {
            if (testOrderIds != null && !testOrderIds.isEmpty()) {
                String ids = testOrderIds.toString().replace("[", "(").replace("]", ")");
                jdbc.update("DELETE FROM pos_order WHERE id IN " + ids);
            }
            jdbc.update("DELETE FROM pos_order WHERE order_no LIKE 'UT4-%'");
            jdbc.update("DELETE FROM commission_record WHERE order_no LIKE 'UT4-%'");
        } catch (Exception ignore) {}
        testOrderIds = null;
        TenantContextHolder.clear();
    }

    // ================================================================
    // 1. dashboard 销售聚合
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("UT-4-1: dashboard — 销售 GMV / refund / net_revenue 聚合正确")
    void dashboardSalesAggregation() {
        // 创建 3 张订单：1 张正常支付（200），1 张全额退款（300），1 张部分退款（500/退 100）
        Long order1 = createOrder("UT4-NORMAL", LocalDateTime.of(2099, 8, 15, 10, 0),
                new BigDecimal("200.00"), new BigDecimal("0.00"), 3);  // status=3 已完成
        Long order2 = createOrder("UT4-REFUND", LocalDateTime.of(2099, 8, 16, 11, 0),
                new BigDecimal("300.00"), new BigDecimal("300.00"), 3);  // 全退
        Long order3 = createOrder("UT4-PARTIAL", LocalDateTime.of(2099, 8, 17, 12, 0),
                new BigDecimal("500.00"), new BigDecimal("100.00"), 3);  // 部分退

        testOrderIds = List.of(order1, order2, order3);

        Result<Map<String, Object>> result = reportService.dashboard(
                null, "2099-08-01", "2099-08-31");
        assertEquals(200, result.getCode(), "应返回 200");

        Map<String, Object> data = result.getData();
        assertNotNull(data);
        Map<String, Object> sales = (Map<String, Object>) data.get("sales");
        assertNotNull(sales);

        // GMV = 200 + 300 + 500 = 1000
        assertEquals(0, new BigDecimal("1000.00").compareTo((BigDecimal) sales.get("gmv")),
                "GMV 应=1000，实际=" + sales.get("gmv"));
        // refund = 300 + 100 = 400
        assertEquals(0, new BigDecimal("400.00").compareTo((BigDecimal) sales.get("refundAmount")),
                "refund 应=400，实际=" + sales.get("refundAmount"));
        // net_revenue = 1000 - 400 = 600
        assertEquals(0, new BigDecimal("600.00").compareTo((BigDecimal) sales.get("netRevenue")),
                "netRevenue 应=600，实际=" + sales.get("netRevenue"));
        // order_count = 3
        assertEquals(3, ((Number) sales.get("orderCount")).intValue(),
                "orderCount 应=3，实际=" + sales.get("orderCount"));
    }

    @Test
    @Order(2)
    @DisplayName("UT-4-2: dashboard — 日期范围过滤生效（超出范围不计入）")
    void dashboardDateFilter() {
        // 一张在范围内（2099-08-15），一张在范围外（2099-09-01）
        Long inRange = createOrder("UT4-IN", LocalDateTime.of(2099, 8, 15, 10, 0),
                new BigDecimal("100.00"), BigDecimal.ZERO, 3);
        Long outOfRange = createOrder("UT4-OUT", LocalDateTime.of(2099, 9, 1, 10, 0),
                new BigDecimal("999.00"), BigDecimal.ZERO, 3);

        testOrderIds = List.of(inRange, outOfRange);

        Result<Map<String, Object>> result = reportService.dashboard(
                null, "2099-08-01", "2099-08-31");
        Map<String, Object> sales = (Map<String, Object>) result.getData().get("sales");

        assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) sales.get("gmv")),
                "只应统计 8 月内订单，GMV=100，实际=" + sales.get("gmv"));
        assertEquals(1, ((Number) sales.get("orderCount")).intValue());
    }

    @Test
    @Order(3)
    @DisplayName("UT-4-3: dashboard — status IN (3,4) 已完成/已退款才计入")
    void dashboardStatusFilter() {
        // status=1 (待支付) 不应计入
        Long pending = createOrder("UT4-PENDING", LocalDateTime.of(2099, 8, 15, 10, 0),
                new BigDecimal("888.00"), BigDecimal.ZERO, 1);
        // status=3 (已完成) 计入
        Long paid = createOrder("UT4-PAID", LocalDateTime.of(2099, 8, 16, 10, 0),
                new BigDecimal("200.00"), BigDecimal.ZERO, 3);

        testOrderIds = List.of(pending, paid);

        Result<Map<String, Object>> result = reportService.dashboard(
                null, "2099-08-01", "2099-08-31");
        Map<String, Object> sales = (Map<String, Object>) result.getData().get("sales");

        assertEquals(0, new BigDecimal("200.00").compareTo((BigDecimal) sales.get("gmv")),
                "待支付订单不应计入，GMV=200，实际=" + sales.get("gmv"));
        assertEquals(1, ((Number) sales.get("orderCount")).intValue());
    }

    @Test
    @Order(4)
    @DisplayName("UT-4-4: dashboard — 跨租户隔离（tenantB 数据不混入 tenantA 报表）")
    void dashboardTenantIsolation() {
        // tenant A 的订单
        Long orderA = createOrder("UT4-A", LocalDateTime.of(2099, 8, 15, 10, 0),
                new BigDecimal("100.00"), BigDecimal.ZERO, 3);
        // tenant B 的订单
        Long orderB = createOrderWithTenant("UT4-B", TEST_TENANT_B,
                LocalDateTime.of(2099, 8, 16, 10, 0),
                new BigDecimal("999.00"), BigDecimal.ZERO, 3);

        testOrderIds = List.of(orderA, orderB);

        Result<Map<String, Object>> result = reportService.dashboard(
                null, "2099-08-01", "2099-08-31");
        Map<String, Object> sales = (Map<String, Object>) result.getData().get("sales");

        // tenantA 只看到自己的 100
        assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) sales.get("gmv")),
                "租户隔离：只看到 tenantA 数据，GMV=100，实际=" + sales.get("gmv"));
    }

    // ================================================================
    // 2. salesDaily 按日聚合
    // ================================================================

    @Test
    @Order(5)
    @DisplayName("UT-4-5: salesDaily — 按日聚合正确性")
    void salesDailyAggregation() {
        Long day1 = createOrder("UT4-D1", LocalDateTime.of(2099, 8, 15, 10, 0),
                new BigDecimal("100.00"), BigDecimal.ZERO, 3);
        Long day1v2 = createOrder("UT4-D1V2", LocalDateTime.of(2099, 8, 15, 14, 0),
                new BigDecimal("200.00"), BigDecimal.ZERO, 3);
        Long day2 = createOrder("UT4-D2", LocalDateTime.of(2099, 8, 16, 10, 0),
                new BigDecimal("500.00"), BigDecimal.ZERO, 3);

        testOrderIds = List.of(day1, day1v2, day2);

        Result<List<Map<String, Object>>> result = reportService.salesDaily(
                null, "2099-08-01", "2099-08-31");
        assertEquals(200, result.getCode());

        List<Map<String, Object>> rows = result.getData();
        // 找 day1 行（2099-08-15）
        Map<String, Object> day1Row = rows.stream()
                .filter(r -> r.get("date") != null && r.get("date").toString().contains("2099-08-15"))
                .findFirst().orElseThrow(() -> new AssertionError("找不到 day1 行"));
        assertEquals(2, ((Number) day1Row.get("orderCount")).intValue());
        assertEquals(0, new BigDecimal("300.00").compareTo((BigDecimal) day1Row.get("netRevenue")));

        // day2 行
        Map<String, Object> day2Row = rows.stream()
                .filter(r -> r.get("date") != null && r.get("date").toString().contains("2099-08-16"))
                .findFirst().orElseThrow(() -> new AssertionError("找不到 day2 行"));
        assertEquals(1, ((Number) day2Row.get("orderCount")).intValue());
        assertEquals(0, new BigDecimal("500.00").compareTo((BigDecimal) day2Row.get("netRevenue")));
    }

    // ================================================================
    // 3. employeeCommission
    // ================================================================

    @Test
    @Order(6)
    @DisplayName("UT-4-6: employeeCommission — 按员工聚合 + 软删除过滤")
    void employeeCommissionAggregation() {
        // 创建员工
        Long empId = createEmployee("UT4-E-" + UUID.randomUUID().toString().substring(0, 6));

        // 创建 2 条 commission_record（已删除的应不计入）
        jdbc.update(
            "INSERT INTO commission_record(tenant_id, store_id, employee_id, employee_name, rule_id, rule_name, business_type, source_type, source_id, source_item_id, order_no, service_name, base_amount, calculation_method, rate, commission_amount, business_time, salary_month, status) " +
            "VALUES(?, ?, ?, 'UT-4 测试员工', 1, 'UT-4 测试规则', 'SERVICE', 'ORDER', 1, 999001, 'UT4-EC1', 'UT-4 测试', 100.00, 'RATE', 0.1000, 10.00, '2099-08-15 10:00:00', '2099-08', 1)",
            TEST_TENANT_A, TEST_STORE_ID, empId);
        jdbc.update(
            "INSERT INTO commission_record(tenant_id, store_id, employee_id, employee_name, rule_id, rule_name, business_type, source_type, source_id, source_item_id, order_no, service_name, base_amount, calculation_method, rate, commission_amount, business_time, salary_month, status, deleted) " +
            "VALUES(?, ?, ?, 'UT-4 测试员工', 1, 'UT-4 测试规则', 'SERVICE', 'ORDER', 2, 999002, 'UT4-EC2', 'UT-4 测试', 200.00, 'RATE', 0.1000, 20.00, '2099-08-16 10:00:00', '2099-08', 1, 1)",
            TEST_TENANT_A, TEST_STORE_ID, empId);

        Result<List<Map<String, Object>>> result = reportService.employeeCommission(
                null, "2099-08-01", "2099-08-31");

        Map<String, Object> empRow = result.getData().stream()
                .filter(r -> ((Number) r.get("employeeId")).longValue() == empId)
                .findFirst().orElseThrow(() -> new AssertionError("找不到员工记录"));
        assertEquals(0, new BigDecimal("10.00").compareTo((BigDecimal) empRow.get("totalCommission")),
                "已删除的 20 不应计入，总=10，实际=" + empRow.get("totalCommission"));
        assertEquals(1, ((Number) empRow.get("recordCount")).intValue());

        // cleanup
        jdbc.update("DELETE FROM commission_record WHERE employee_id = ?", empId);
        jdbc.update("DELETE FROM pos_employee WHERE id = ?", empId);
    }

    // ================================================================
    // 4. 租户未识别场景
    // ================================================================

    @Test
    @Order(7)
    @DisplayName("UT-4-7: dashboard — tenantId 为 null 时返回 UNAUTHORIZED")
    void dashboardNoTenant() {
        TenantContextHolder.clear();  // 清空租户
        Result<Map<String, Object>> result = reportService.dashboard(
                null, "2099-08-01", "2099-08-31");
        assertEquals(BizCode.UNAUTHORIZED.code, result.getCode());
    }

    // ================================================================
    // 5. 越权门店
    // ================================================================

    @Test
    @Order(8)
    @DisplayName("UT-4-8: dashboard — 不存在的 storeId 返回 FORBIDDEN")
    void dashboardInvalidStore() {
        Result<Map<String, Object>> result = reportService.dashboard(
                999999L, "2099-08-01", "2099-08-31");
        assertEquals(BizCode.FORBIDDEN.code, result.getCode());
    }

    // ================================================================
    // 工具方法
    // ================================================================

    private Long createOrder(String prefix, LocalDateTime payTime, BigDecimal finalAmount,
                             BigDecimal refundAmount, int status) {
        return createOrderWithTenant(prefix, TEST_TENANT_A, payTime, finalAmount, refundAmount, status);
    }

    private Long createOrderWithTenant(String prefix, Long tenantId, LocalDateTime payTime,
                                       BigDecimal finalAmount, BigDecimal refundAmount, int status) {
        String orderNo = prefix + "-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update(
            "INSERT INTO pos_order(tenant_id, store_id, order_no, type, total_amount, final_amount, paid_amount, refund_amount, cashier_id, status, pay_time) " +
            "VALUES(?, ?, ?, 1, ?, ?, ?, ?, 1, ?, ?)",
            tenantId, TEST_STORE_ID, orderNo,
            finalAmount, finalAmount, finalAmount.subtract(refundAmount), refundAmount,
            status, payTime);
        return jdbc.queryForObject(
            "SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo);
    }

    private Long createEmployee(String code) {
        jdbc.update(
            "INSERT INTO pos_employee(employee_code, employee_name, phone, store_id, status, gender) " +
            "VALUES(?, 'UT-4 测试员工', ?, ?, 1, 0)",
            code, "139000" + UUID.randomUUID().toString().substring(0, 5).replace("-", ""),
            TEST_STORE_ID);
        return jdbc.queryForObject(
            "SELECT id FROM pos_employee WHERE employee_code = ?", Long.class, code);
    }
}