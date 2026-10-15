package com.meiyetec.controller;

import com.meiyetec.entity.Booking;
import com.meiyetec.entity.Store;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.BookingMapper;
import com.meiyetec.mapper.StoreMapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.BookingService;
import com.meiyetec.util.Result;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-3 · 跨店预约 storeId 强制过滤 — 单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>P0-3-1: Tenant A 创建预约时传 Tenant B 的 storeId → 必须 403 FORBIDDEN</li>
 *   <li>P0-3-2: Tenant A 调用 available() 查 Tenant B 的技师 → 必须 403 FORBIDDEN</li>
 *   <li>P0-3-3: Tenant A 用自己的 storeId 创建预约 → 正常走冲突检查流程</li>
 *   <li>P0-3-4: available() 技师属于当前租户门店 → 正常返回可用时段（不抛异常）</li>
 * </ul>
 *
 * <p>依赖 fixture：
 * <ul>
 *   <li>TEST_TENANT_A = 900011（用于本测试）</li>
 *   <li>TEST_TENANT_B = 900012（跨租户对照）</li>
 *   <li>门店 fixture：pos_store（每个租户各 1 个）</li>
 *   <li>员工 fixture：biz_employee（每个租户各 1 个）</li>
 *   <li>会员 fixture：crm_member（每个租户各 1 个）</li>
 *   <li>预约冲突 fixture：biz_booking（未来时间，占用时段）</li>
 * </ul>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class BookingCrossTenantStoreIsolationTest {

    private static final Long TENANT_A = 900011L;
    private static final Long TENANT_B = 900012L;

    // fixture IDs（动态生成后在 @AfterEach 清理）
    private static Long storeAId = null;
    private static Long storeBId = null;
    private static Long empAId = null;
    private static Long empBId = null;
    private static Long memberAId = null;
    private static Long memberBId = null;
    private static Long bookingFixtureAId = null; // 占用时段的预约

    private static final LocalDateTime FIXTURE_START =
            LocalDateTime.of(2030, 9, 15, 10, 0);
    private static final LocalDateTime FIXTURE_END =
            LocalDateTime.of(2030, 9, 15, 11, 0);

    @Autowired private BookingController controller;
    @Autowired private BookingMapper bookingMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StoreMapper storeMapper;

    // ==================== Fixture Setup ====================

    @BeforeAll
    void setupFixtures() {
        // Tenant A
        jdbc.update("INSERT IGNORE INTO sys_tenant(id, tenant_code, tenant_name, status) VALUES(?, 'TEST_P03_A', 'P0-3 Tenant A', 1)", TENANT_A);
        // Tenant B
        jdbc.update("INSERT IGNORE INTO sys_tenant(id, tenant_code, tenant_name, status) VALUES(?, 'TEST_P03_B', 'P0-3 Tenant B', 1)", TENANT_B);

        // Tenant A store
        String ts = String.valueOf(System.currentTimeMillis()).substring(5);
        String storeCodeA = "P03SA" + ts;
        jdbc.update(
            "INSERT INTO pos_store(tenant_id, store_name, store_code, store_type, status) " +
            "VALUES(?, 'P0-3 租户A门店', ?, '直营', 1)",
            TENANT_A, storeCodeA);
        storeAId = jdbc.queryForObject(
            "SELECT id FROM pos_store WHERE store_code = ?", Long.class, storeCodeA);

        // Tenant B store
        String storeCodeB = "P03SB" + ts;
        jdbc.update(
            "INSERT INTO pos_store(tenant_id, store_name, store_code, store_type, status) " +
            "VALUES(?, 'P0-3 租户B门店', ?, '直营', 1)",
            TENANT_B, storeCodeB);
        storeBId = jdbc.queryForObject(
            "SELECT id FROM pos_store WHERE store_code = ?", Long.class, storeCodeB);

        // Tenant A employee
        String empCodeA = "P03EA" + ts;
        jdbc.update(
            "INSERT INTO biz_employee(tenant_id, store_id, employee_name, employee_code, position, status) " +
            "VALUES(?, ?, 'P0-3 技师A', ?, '美容师', 1)",
            TENANT_A, storeAId, empCodeA);
        empAId = jdbc.queryForObject(
            "SELECT id FROM biz_employee WHERE employee_code = ?", Long.class, empCodeA);

        // Tenant B employee
        String empCodeB = "P03EB" + ts;
        jdbc.update(
            "INSERT INTO biz_employee(tenant_id, store_id, employee_name, employee_code, position, status) " +
            "VALUES(?, ?, 'P0-3 技师B', ?, '美容师', 1)",
            TENANT_B, storeBId, empCodeB);
        empBId = jdbc.queryForObject(
            "SELECT id FROM biz_employee WHERE employee_code = ?", Long.class, empCodeB);

        // Tenant A member
        String memberNoA = "P03MA" + ts;
        jdbc.update(
            "INSERT INTO crm_member(tenant_id, store_id, member_no, name, phone, gender, status, points, balance, join_date) " +
            "VALUES(?, ?, ?, 'P0-3 会员A', '13900000011', 0, 1, 0, 0, CURDATE())",
            TENANT_A, storeAId, memberNoA);
        memberAId = jdbc.queryForObject(
            "SELECT id FROM crm_member WHERE member_no = ?", Long.class, memberNoA);

        // Tenant B member
        String memberNoB = "P03MB" + ts;
        jdbc.update(
            "INSERT INTO crm_member(tenant_id, store_id, member_no, name, phone, gender, status, points, balance, join_date) " +
            "VALUES(?, ?, ?, 'P0-3 会员B', '13900000012', 0, 1, 0, 0, CURDATE())",
            TENANT_B, storeBId, memberNoB);
        memberBId = jdbc.queryForObject(
            "SELECT id FROM crm_member WHERE member_no = ?", Long.class, memberNoB);

        // Tenant A 员工排班 fixture（使 available() 能返回时段）
        jdbc.update(
            "INSERT INTO biz_employee_schedule(tenant_id, employee_id, store_id, work_date, start_time, end_time, max_bookings, status) " +
            "VALUES(?, ?, ?, ?, ?, ?, 4, 1)",
            TENANT_A, empAId, storeAId, LocalDate.of(2030, 9, 15), FIXTURE_START.toLocalTime(), FIXTURE_END.toLocalTime());

        // Tenant B 员工排班 fixture
        jdbc.update(
            "INSERT INTO biz_employee_schedule(tenant_id, employee_id, store_id, work_date, start_time, end_time, max_bookings, status) " +
            "VALUES(?, ?, ?, ?, ?, ?, 4, 1)",
            TENANT_B, empBId, storeBId, LocalDate.of(2030, 9, 15), FIXTURE_START.toLocalTime(), FIXTURE_END.toLocalTime());

        // Tenant A 时段占用预约 fixture（使 available() 返回非空 busy list）
        String bookNo = "P03-FIXTURE-" + ts;
        jdbc.update(
            "INSERT INTO biz_booking(tenant_id, store_id, member_id, member_name, employee_id, employee_name, " +
            "booking_no, booking_date, start_time, end_time, status, source) " +
            "VALUES(?, ?, ?, 'P0-3 fixture', ?, 'P0-3 技师A', ?, ?, ?, ?, 1, 2)",
            TENANT_A, storeAId, memberAId, empAId, bookNo,
            LocalDate.of(2030, 9, 15), FIXTURE_START, FIXTURE_END);
        bookingFixtureAId = jdbc.queryForObject(
            "SELECT id FROM biz_booking WHERE booking_no = ?", Long.class, bookNo);
    }

    @AfterAll
    void cleanupAll() {
        try {
            jdbc.update("DELETE FROM biz_booking WHERE id = ?", bookingFixtureAId);
            jdbc.update("DELETE FROM biz_booking WHERE booking_no LIKE 'P03-%'");
            jdbc.update("DELETE FROM biz_employee_schedule WHERE employee_id IN (?, ?)", empAId, empBId);
            jdbc.update("DELETE FROM crm_member WHERE member_no LIKE 'P03M%'");
            jdbc.update("DELETE FROM biz_employee WHERE employee_code LIKE 'P03E%'");
            jdbc.update("DELETE FROM pos_store WHERE store_code LIKE 'P03S%'");
            jdbc.update("DELETE FROM sys_tenant WHERE tenant_code IN ('TEST_P03_A', 'TEST_P03_B')");
        } catch (Exception ignored) {}
        TenantContextHolder.clear();
    }

    @AfterEach
    void clearCtx() {
        TenantContextHolder.clear();
    }

    // ==================== Helpers ====================

    private void setCtx(Long tenantId) {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(tenantId);
        ctx.setUserId(1L);
        ctx.setDataScope("SELF");
        ctx.setStoreIds(null); // SELF 范围，不含任何门店
        TenantContextHolder.set(ctx);
    }

    // ==================== Tests ====================

    /**
     * P0-3-1: Tenant A 用 Tenant B 的 storeId 创建预约 → 403 FORBIDDEN
     *
     * <p>攻击路径：攻击者以 Tenant A 的身份，在请求 body 中传入 Tenant B 的 storeId，
     * 期望预约被写入 B 的门店。
     * <p>期望结果：控制器层直接 reject，返回 BizCode.FORBIDDEN，不进入 service。
     */
    @Test
    @Order(1)
    @DisplayName("P0-3-1: Tenant A 传入 Tenant B 的 storeId → 必须 403 FORBIDDEN")
    void crossTenantStoreIdRejected() {
        setCtx(TENANT_A);

        Booking malicious = new Booking();
        malicious.setStoreId(storeBId);              // ← Tenant B 的门店
        malicious.setMemberId(memberAId);
        malicious.setEmployeeId(empAId);
        malicious.setEmployeeName("P0-3 技师A");
        malicious.setStartTime(FIXTURE_END.plusHours(1));
        malicious.setEndTime(FIXTURE_END.plusHours(2));
        malicious.setBookingDate(LocalDate.of(2030, 9, 15));

        @SuppressWarnings("unchecked")
        Result<Booking> result = controller.create(malicious);

        assertNotEquals(200, result.getCode(),
                "跨租户 storeId 不应返回成功");
        assertEquals(BizCode.FORBIDDEN.code, result.getCode(),
                "必须返回 FORBIDDEN(403)，而非 200 或其他错误码。实际: " + result.getCode() + " " + result.getMessage());
    }

    /**
     * P0-3-2: Tenant A 用 Tenant B 的 storeId 创建预约 → DB 中不应有跨租户数据
     *
     * <p>即使绕过控制器（测试边界），DB 层也应有租户隔离。
     * <p>验证：Biz_booking 表在修复后不会有 storeId = Tenant B.storeId 且 tenant_id = Tenant A 的行。
     */
    @Test
    @Order(2)
    @DisplayName("P0-3-2: 跨租户 storeId 预约尝试后 DB 应无跨租户行（双重保护）")
    void noCrossTenantBookingInDb() {
        setCtx(TENANT_A);

        Booking malicious = new Booking();
        malicious.setStoreId(storeBId);
        malicious.setMemberId(memberAId);
        malicious.setEmployeeId(empAId);
        malicious.setStartTime(FIXTURE_END.plusHours(2));
        malicious.setEndTime(FIXTURE_END.plusHours(3));
        malicious.setBookingDate(LocalDate.of(2030, 9, 15));

        controller.create(malicious);

        // 确认 DB 中没有跨租户行
        Long crossCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM biz_booking " +
            "WHERE store_id = ? AND tenant_id = ? AND deleted = 0",
            Long.class, storeBId, TENANT_A);
        assertEquals(0L, crossCount,
                "biz_booking 中不应存在 storeId 属于 Tenant B 但 tenant_id = Tenant A 的行");
    }

    /**
     * P0-3-3: Tenant A 用自己的 storeId 创建预约 → 进入正常冲突检查流程（不抛 FORBIDDEN）
     *
     * <p>验证修复没有破坏正常业务路径。
     * <p>注意：本测试预期冲突（同一时段已占用），所以 code != 200，但不应是 FORBIDDEN。
     */
    @Test
    @Order(3)
    @DisplayName("P0-3-3: Tenant A 用自己的 storeId → 不应 403（允许进入冲突检查）")
    void sameTenantStoreIdPassesThrough() {
        setCtx(TENANT_A);

        Booking b = new Booking();
        b.setStoreId(storeAId);                      // ← Tenant A 的门店（合法）
        b.setMemberId(memberAId);
        b.setEmployeeId(empAId);
        b.setEmployeeName("P0-3 技师A");
        // 复用 fixture 时段 → 预期 BOOKING_CONFLICT（不是 FORBIDDEN）
        b.setStartTime(FIXTURE_START);
        b.setEndTime(FIXTURE_END);
        b.setBookingDate(LocalDate.of(2030, 9, 15));

        @SuppressWarnings("unchecked")
        Result<Booking> result = controller.create(b);

        // 可能冲突（5401）或成功（200），但绝对不应该是 403
        assertNotEquals(BizCode.FORBIDDEN.code, result.getCode(),
                "合法 storeId 不应被 FORBIDDEN 拒绝，实际: " + result.getCode() + " " + result.getMessage());
        // 确认是冲突或其他业务错误，不是安全拒绝
        assertTrue(
            result.getCode() == BizCode.BOOKING_CONFLICT.code || result.getCode() == 200,
            "应进入冲突检查（5401）或成功（200），实际: " + result.getCode() + " " + result.getMessage());
    }

    /**
     * P0-3-4: Tenant A 查 Tenant B 的技师可用时间 → 403 FORBIDDEN
     *
     * <p>攻击路径：攻击者知道 Tenant B 的 employeeId，
     * 调用 /booking/available?employeeId=B-empId&date=...
     * <p>期望结果：控制器检测到技师所属门店不属于当前租户，返回 403。
     */
    @Test
    @Order(4)
    @DisplayName("P0-3-4: Tenant A 查询 Tenant B 的技师排班 → 必须 403 FORBIDDEN")
    void crossTenantEmployeeAvailableRejected() {
        setCtx(TENANT_A);

        // empBId 属于 Tenant B 的门店
        @SuppressWarnings("unchecked")
        Result<?> result = controller.available(empBId, "2030-09-15");

        assertEquals(BizCode.FORBIDDEN.code, result.getCode(),
                "查询其他租户技师排班必须返回 FORBIDDEN，实际: " + result.getCode() + " " + result.getMessage());
    }

    /**
     * P0-3-5: Tenant A 查自己的技师可用时间 → 正常返回时段（不抛异常）
     *
     * <p>验证修复没有破坏 available() 的正常业务路径。
     * empAId 属于 Tenant A 的门店，预期返回 200（即使有时段冲突也应返回 200 而非 403）。
     */
    @Test
    @Order(5)
    @DisplayName("P0-3-5: Tenant A 查询自己的技师 → 不应 403，正常返回可用时段")
    void sameTenantEmployeeAvailablePasses() {
        setCtx(TENANT_A);

        @SuppressWarnings("unchecked")
        Result<?> result = controller.available(empAId, "2030-09-15");

        assertEquals(200, result.getCode(),
                "合法技师不应被 FORBIDDEN 拒绝，实际: " + result.getCode() + " " + result.getMessage());
        assertNotNull(result.getData(),
                "可用时段查询应返回非空数据（即使部分时段被占用）");
    }

    /**
     * P0-3-6: storeId 不传（null）时走兜底逻辑 → 正常业务
     *
     * <p>边界：前端漏传 storeId 时，BookingServiceImpl 会从 TenantContextHolder.getStoreIdOrNull() 兜底。
     * 测试 TenantContextHolder 有 storeId 的场景。
     */
    @Test
    @Order(6)
    @DisplayName("P0-3-6: storeId=null 时走兜底逻辑（ctx 有 storeId）→ 正常处理")
    void nullStoreIdWithContextFallback() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(TENANT_A);
        ctx.setUserId(1L);
        ctx.setDataScope("SELF");
        ctx.setStoreIds(java.util.Set.of(storeAId)); // TenantContext 有 storeId
        TenantContextHolder.set(ctx);

        Booking b = new Booking();
        b.setStoreId(null); // 前端漏传
        b.setMemberId(memberAId);
        b.setEmployeeId(empAId);
        b.setEmployeeName("P0-3 技师A");
        b.setStartTime(FIXTURE_END.plusHours(3));
        b.setEndTime(FIXTURE_END.plusHours(4));
        b.setBookingDate(LocalDate.of(2030, 9, 15));

        @SuppressWarnings("unchecked")
        Result<Booking> result = controller.create(b);

        // 不应是 FORBIDDEN（空 storeId 走了兜底，不走跨租户检查）
        assertNotEquals(BizCode.FORBIDDEN.code, result.getCode(),
                "storeId=null 时走 TenantContext 兜底，不应触发 FORBIDDEN");
    }
}
