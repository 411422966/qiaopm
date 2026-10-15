package com.meiyetec.service;

import com.meiyetec.entity.CommissionSettlement;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-TEST-002 · UT-3 · CommissionSettlement 单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>generate：按 storeId+month 聚合 commission_record</li>
 *   <li>员工无 commission 时 record 仍生成（commission=0）</li>
 *   <li>重复 generate：status>1 时拒绝重新生成</li>
 *   <li>状态机：generate(1) → confirm(2) → pay(3)，非法跳转抛异常</li>
 *   <li>pay 后 commission_record.settlement_id 被正确回填</li>
 * </ul>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommissionSettlementTest {

    private static final Long TEST_TENANT = 1L;
    private static final Long TEST_STORE_ID = 1L;

    private static String salaryMonth;
    private static Long employeeIdA;
    private static Long employeeIdB;
    private static Long settlementId;
    private static Long recordA1Id;
    private static Long recordA2Id;
    private static Long recordBId;

    @Autowired private CommissionSettlementService settlementService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        // P0-TEST-002: 先彻底清理残留（tearDown 在异常时可能不完整；并行跑全套时也需手动清理）
        jdbc.update("DELETE r FROM commission_settlement_record r " +
                "INNER JOIN commission_settlement s ON r.settlement_id=s.id " +
                "WHERE s.store_id = ? AND s.salary_month = ?",
                TEST_STORE_ID, salaryMonth);
        jdbc.update("DELETE FROM commission_settlement WHERE store_id = ? AND salary_month = ?",
                TEST_STORE_ID, salaryMonth);
        jdbc.update("DELETE FROM commission_record WHERE store_id = ? AND salary_month = ?",
                TEST_STORE_ID, salaryMonth);
        jdbc.update("DELETE FROM commission_salary WHERE store_id = ? AND salary_month = ?",
                TEST_STORE_ID, salaryMonth);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId(TEST_TENANT);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        // 用测试月 + 唯一 storeId 避免污染生产数据
        // 用 2099-08 测试月份（未来日期，不影响其他测试）
        salaryMonth = "2099-08";

        // 创建员工 A 和 B
        employeeIdA = createEmployee("UT3-A-" + UUID.randomUUID().toString().substring(0, 6));
        employeeIdB = createEmployee("UT3-B-" + UUID.randomUUID().toString().substring(0, 6));

        // 用唯一 source_item_id（UUID long 范围）避免唯一键冲突
        recordA1Id = System.currentTimeMillis();
        recordA2Id = recordA1Id + 1;
        recordBId = recordA1Id + 2;

        // 给员工 A 在 2099-08 月写入 2 条 commission_record（合计 150）
        jdbc.update(
            "INSERT INTO commission_record(tenant_id, store_id, employee_id, employee_name, rule_id, rule_name, business_type, source_type, source_id, source_item_id, order_no, service_name, base_amount, calculation_method, rate, commission_amount, business_time, salary_month, status) " +
            "VALUES(?, ?, ?, 'UT-3 员工 A', 1, 'UT-3 测试规则', 'SERVICE', 'ORDER', 1, ?, 'UT3-2099-A', 'UT-3 测试', 100.00, 'RATE', 0.1000, 10.00, '2099-08-15 10:00:00', '2099-08', 1)",
            TEST_TENANT, TEST_STORE_ID, employeeIdA, recordA1Id);
        jdbc.update(
            "INSERT INTO commission_record(tenant_id, store_id, employee_id, employee_name, rule_id, rule_name, business_type, source_type, source_id, source_item_id, order_no, service_name, base_amount, calculation_method, rate, commission_amount, business_time, salary_month, status) " +
            "VALUES(?, ?, ?, 'UT-3 员工 A', 1, 'UT-3 测试规则', 'SERVICE', 'ORDER', 2, ?, 'UT3-2099-A', 'UT-3 测试', 140.00, 'RATE', 0.1000, 14.00, '2099-08-20 11:00:00', '2099-08', 1)",
            TEST_TENANT, TEST_STORE_ID, employeeIdA, recordA2Id);

        // 给员工 B 写入 1 条（合计 50）
        jdbc.update(
            "INSERT INTO commission_record(tenant_id, store_id, employee_id, employee_name, rule_id, rule_name, business_type, source_type, source_id, source_item_id, order_no, service_name, base_amount, calculation_method, rate, commission_amount, business_time, salary_month, status) " +
            "VALUES(?, ?, ?, 'UT-3 员工 B', 1, 'UT-3 测试规则', 'SERVICE', 'ORDER', 3, ?, 'UT3-2099-B', 'UT-3 测试', 50.00, 'FIXED', NULL, 50.00, '2099-08-25 12:00:00', '2099-08', 1)",
            TEST_TENANT, TEST_STORE_ID, employeeIdB, recordBId);
    }

    @AfterEach
    void tearDown() {
        try {
            // 清理本次测试数据（commission_settlement_record 没有 salary_month 列，需通过 settlement_id）
            jdbc.update("DELETE r FROM commission_settlement_record r " +
                    "INNER JOIN commission_settlement s ON r.settlement_id = s.id " +
                    "WHERE s.store_id = ? AND s.salary_month = ?",
                    TEST_STORE_ID, salaryMonth);
            jdbc.update("DELETE FROM commission_settlement WHERE store_id = ? AND salary_month = ?",
                    TEST_STORE_ID, salaryMonth);
            jdbc.update("DELETE FROM commission_record WHERE store_id = ? AND salary_month = ?",
                    TEST_STORE_ID, salaryMonth);
            jdbc.update("DELETE FROM pos_employee WHERE id IN (?, ?)", employeeIdA, employeeIdB);
        } catch (Exception ignore) {}
        employeeIdA = null;
        employeeIdB = null;
        settlementId = null;
        TenantContextHolder.clear();
    }

    // ================================================================
    // 1. generate 批量结算
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("UT-3-1: generate — 按 storeId+month 聚合 commission_record")
    void generateAggregation() {
        CommissionSettlement settlement = settlementService.generate(TEST_STORE_ID, salaryMonth);
        settlementId = settlement.getId();

        assertNotNull(settlement);
        assertEquals(salaryMonth, settlement.getSalaryMonth());
        assertEquals(1, settlement.getStatus().intValue(), "初始 status=1（待确认）");
        // 注意：employeeCount 是该 store 所有非 0 状态的员工，可能包含其他测试遗留
        assertTrue(settlement.getEmployeeCount() >= 2,
                "员工数应 >= 2（含员工 A 和 B），实际=" + settlement.getEmployeeCount());

        // totalCommission 应 = 24 + 50 = 74（员工 A 10+14 + 员工 B 50）
        // 但可能 store 还有其他员工的非 2099-08 提成记录被排除，所以只算 2099-08
        assertEquals(0, new BigDecimal("74.00").compareTo(settlement.getTotalCommission()),
                "总提成应 = 24 + 50 = 74，实际=" + settlement.getTotalCommission());

        // DB 验证：settlement_record 至少 2 条（A 和 B）
        List<Map<String, Object>> records = settlementService.getRecords(settlement.getId());
        long countA = records.stream()
                .filter(r -> ((Number) r.get("employee_id")).longValue() == employeeIdA)
                .count();
        long countB = records.stream()
                .filter(r -> ((Number) r.get("employee_id")).longValue() == employeeIdB)
                .count();
        assertEquals(1, countA, "员工 A 应有 1 条 settlement_record");
        assertEquals(1, countB, "员工 B 应有 1 条 settlement_record");

        // 员工 A 的 commission_amount = 24（A 有 10 + 14 两笔合并）
        Map<String, Object> recordA = records.stream()
                .filter(r -> ((Number) r.get("employee_id")).longValue() == employeeIdA)
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("24.00").compareTo((BigDecimal) recordA.get("commission_amount")),
                "员工 A 提成汇总应 = 10 + 14 = 24，实际=" + recordA.get("commission_amount"));
    }

    @Test
    @Order(2)
    @DisplayName("UT-3-2: generate — 无 commission 员工仍生成 record（金额=0）")
    void generateEmployeeNoCommission() {
        // 先删除员工 A 和 B 的 commission_record，让它们没提成
        jdbc.update("DELETE FROM commission_record WHERE store_id = ? AND salary_month = ?",
                TEST_STORE_ID, salaryMonth);

        CommissionSettlement settlement = settlementService.generate(TEST_STORE_ID, salaryMonth);
        settlementId = settlement.getId();

        assertEquals(0, settlement.getTotalCommission().compareTo(BigDecimal.ZERO),
                "总提成应为 0，实际=" + settlement.getTotalCommission());

        // 员工 A 和 B 的 record 仍生成，但 commission_amount = 0
        List<Map<String, Object>> records = settlementService.getRecords(settlement.getId());
        long countA = records.stream()
                .filter(r -> ((Number) r.get("employee_id")).longValue() == employeeIdA)
                .count();
        assertEquals(1, countA, "员工 A 仍应生成 record");

        Map<String, Object> recordA = records.stream()
                .filter(r -> ((Number) r.get("employee_id")).longValue() == employeeIdA)
                .findFirst().orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) recordA.get("commission_amount")),
                "员工 A commission 应为 0");
    }

    // ================================================================
    // 2. 状态机：generate → confirm → pay
    // ================================================================

    @Test
    @Order(3)
    @DisplayName("UT-3-3: 状态机 1→2→3 全流程")
    void statusMachineFlow() {
        CommissionSettlement s = settlementService.generate(TEST_STORE_ID, salaryMonth);
        settlementId = s.getId();
        assertEquals(1, s.getStatus().intValue(), "generate 后 status=1");

        // confirm: 1 → 2
        CommissionSettlement confirmed = settlementService.confirm(s.getId(), 1L);
        assertEquals(2, confirmed.getStatus().intValue());
        assertNotNull(confirmed.getConfirmTime());
        assertEquals(1L, confirmed.getConfirmBy().longValue());

        // pay: 2 → 3
        CommissionSettlement paid = settlementService.pay(s.getId(), 2L);
        assertEquals(3, paid.getStatus().intValue());
        assertNotNull(paid.getPayTime());
        assertEquals(2L, paid.getPayBy().longValue());

        // DB 验证：commission_record.settlement_id 被回填
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM commission_record WHERE settlement_id = ? AND deleted = 0",
            Long.class, s.getId());
        assertTrue(count >= 3, "应回填至少 3 条（员工 A 2 + 员工 B 1），实际=" + count);
    }

    @Test
    @Order(4)
    @DisplayName("UT-3-4: confirm 非 1 状态抛 IllegalStateException")
    void confirmInvalidState() {
        CommissionSettlement s = settlementService.generate(TEST_STORE_ID, salaryMonth);
        settlementId = s.getId();
        // 直接 confirm 后再 confirm
        settlementService.confirm(s.getId(), 1L);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> settlementService.confirm(s.getId(), 1L));
        assertTrue(ex.getMessage().contains("确认"));
    }

    @Test
    @Order(5)
    @DisplayName("UT-3-5: pay 非 2 状态抛 IllegalStateException")
    void payInvalidState() {
        CommissionSettlement s = settlementService.generate(TEST_STORE_ID, salaryMonth);
        settlementId = s.getId();
        // 跳过 confirm，直接 pay
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> settlementService.pay(s.getId(), 1L));
        assertTrue(ex.getMessage().contains("已确认"));
    }

    @Test
    @Order(6)
    @DisplayName("UT-3-6: pay 后再次 pay 抛 IllegalStateException")
    void payTwice() {
        CommissionSettlement s = settlementService.generate(TEST_STORE_ID, salaryMonth);
        settlementId = s.getId();
        settlementService.confirm(s.getId(), 1L);
        settlementService.pay(s.getId(), 1L);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> settlementService.pay(s.getId(), 1L));
        assertTrue(ex.getMessage().contains("已确认"));
    }

    @Test
    @Order(7)
    @DisplayName("UT-3-7: 重复 generate — status=1 时允许重新生成（删除旧 record 重建）")
    void regenerateStatus1() {
        CommissionSettlement s1 = settlementService.generate(TEST_STORE_ID, salaryMonth);
        settlementId = s1.getId();
        Long firstId = s1.getId();

        // 第 2 次 generate（status=1 应允许）
        CommissionSettlement s2 = settlementService.generate(TEST_STORE_ID, salaryMonth);
        assertEquals(firstId, s2.getId(), "重新生成应复用同一个 settlementId");
        assertEquals(1, s2.getStatus().intValue());

        // 老的 record 应被删除（数量归零）
        // 但新的 record 重新插入
        List<Map<String, Object>> records = settlementService.getRecords(firstId);
        assertTrue(records.size() >= 2, "应有 2 条记录（员工 A + B）");
    }

    @Test
    @Order(8)
    @DisplayName("UT-3-8: status=2（已确认）时拒绝重新生成")
    void regenerateStatus2Rejected() {
        CommissionSettlement s = settlementService.generate(TEST_STORE_ID, salaryMonth);
        settlementId = s.getId();
        settlementService.confirm(s.getId(), 1L);  // 状态 → 2

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> settlementService.generate(TEST_STORE_ID, salaryMonth));
        assertTrue(ex.getMessage().contains("已确认") || ex.getMessage().contains("已发放"),
                "异常信息应说明已确认/已发放，实际=" + ex.getMessage());
    }

    // ================================================================
    // 工具方法
    // ================================================================

    private Long createEmployee(String code) {
        jdbc.update(
            "INSERT INTO pos_employee(employee_code, employee_name, phone, store_id, status, gender, salary) " +
            "VALUES(?, ?, ?, ?, 1, 0, 5000.00)",
            code, "UT-3 测试员工 " + code, "139000" + UUID.randomUUID().toString().substring(0, 5).replace("-", ""),
            TEST_STORE_ID);
        return jdbc.queryForObject(
            "SELECT id FROM pos_employee WHERE employee_code = ?", Long.class, code);
    }
}