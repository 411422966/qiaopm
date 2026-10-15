package com.meiyetec.service;

import com.meiyetec.dto.WalletOpDTO;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-TEST-001 · WT-3 · 钱包退款幂等测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>WT-3-A: 顺序二次退款 — 第二次抛 REFUND_EXCEED</li>
 *   <li>WT-3-B: 并发 50 个退款同一订单 — 只有 1 个成功，49 个被拒</li>
 * </ul>
 *
 * <p>P0-TEST-001 修复点：doRefund 加了按 orderId 的内存锁，
 * <p>让"幂等检查 + CAS + 写流水"三步串行化，彻底避免 TOCTOU race。
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WalletRefundIdempotentTest {

    private static final Long TEST_TENANT = 1L;
    private static final int CONCURRENCY = 50;
    private static final BigDecimal INITIAL = new BigDecimal("1000.00");
    private static final BigDecimal REFUND_AMOUNT = new BigDecimal("500.00");

    private static Long testMemberId;
    private static Long testOrderId;

    @Autowired private WalletService walletService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(TEST_TENANT);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        String ts = String.valueOf(System.currentTimeMillis());
        String suffix = ts.substring(ts.length() - 9);
        String phone = "R" + suffix;
        String memberNo = "WR" + suffix;
        jdbc.update(
            "INSERT INTO crm_member(tenant_id, store_id, member_no, name, phone, gender, status, points, balance, total_consume, join_date) " +
            "VALUES(?, 1, ?, ?, ?, 0, 1, 0, ?, ?, CURDATE())",
            TEST_TENANT, memberNo, "WT-3 退款幂等测试", phone, BigDecimal.ZERO, BigDecimal.ZERO);
        testMemberId = jdbc.queryForObject(
            "SELECT id FROM crm_member WHERE member_no = ?", Long.class, memberNo);

        // 预创建 wallet + 充值 1000 元
        jdbc.update(
            "INSERT INTO crm_wallet(tenant_id, member_id, balance, total_recharge, total_consume, frozen_amount, version) " +
            "VALUES(?, ?, ?, 0, 0, 0, 0)",
            TEST_TENANT, testMemberId, INITIAL);

        // 创建一个 pos_order（退款必须有关联订单）
        String orderNo = "WT3-" + ts;
        jdbc.update(
            "INSERT INTO pos_order(tenant_id, store_id, order_no, member_id, member_name, type, total_amount, final_amount, paid_amount, cashier_id, status) " +
            "VALUES(?, 1, ?, ?, 'WT-3 退款幂等测试', 1, 1000, 1000, 1000, 1, 2)",
            TEST_TENANT, orderNo, testMemberId);
        testOrderId = jdbc.queryForObject(
            "SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo);
    }

    @AfterEach
    void tearDown() {
        try {
            jdbc.update("DELETE FROM crm_wallet_transaction WHERE member_id = ?", testMemberId);
            jdbc.update("DELETE FROM crm_wallet WHERE member_id = ?", testMemberId);
            jdbc.update("DELETE FROM pos_order WHERE member_id = ? AND order_no LIKE 'WT3-%'", testMemberId);
            jdbc.update("DELETE FROM crm_member WHERE id = ?", testMemberId);
        } catch (Exception ignore) {}
        testMemberId = null;
        testOrderId = null;
        TenantContextHolder.clear();
    }

    // ================================================================
    // WT-3-A: 顺序二次退款
    // ================================================================
    @Test
    @Order(1)
    @DisplayName("WT-3-A: 顺序两次 refund 同一订单 — 第二次抛 REFUND_EXCEED")
    void sequentialDoubleRefund() {
        WalletOpDTO dto = new WalletOpDTO();
        dto.setMemberId(testMemberId);
        dto.setAmount(REFUND_AMOUNT);
        dto.setOrderId(testOrderId);
        dto.setOperatorId(1L);
        dto.setPayMethod("原路返回");
        dto.setRemark("WT-3-A 顺序退款");

        // 第一次退款：成功
        Result<Map<String, Object>> r1 = walletService.doRefund(dto, TEST_TENANT);
        assertEquals(200, r1.getCode(), "第一次退款应成功");

        BigDecimal bal1 = jdbc.queryForObject(
            "SELECT balance FROM crm_wallet WHERE member_id = ? AND deleted = 0",
            BigDecimal.class, testMemberId);
        assertEquals(0, INITIAL.add(REFUND_AMOUNT).compareTo(bal1),
                "退款后余额 = 1000 + 500 = 1500，实际=" + bal1);

        // 第二次退款：抛 REFUND_EXCEED
        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.doRefund(dto, TEST_TENANT));
        assertEquals(BizCode.REFUND_EXCEED.code, ex.getCode(),
                "第二次退款应抛 REFUND_EXCEED");

        // 余额未变（仍是 1500，没有重复退款）
        BigDecimal bal2 = jdbc.queryForObject(
            "SELECT balance FROM crm_wallet WHERE member_id = ? AND deleted = 0",
            BigDecimal.class, testMemberId);
        assertEquals(0, bal1.compareTo(bal2), "余额不应变化，仍=" + bal1);

        // 只有 1 条 refund 流水
        Long refundCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet_transaction WHERE order_id = ? AND type = 3",
            Long.class, testOrderId);
        assertEquals(1L, refundCount.longValue(),
                "只有 1 条 type=3 退款流水，实际=" + refundCount);
    }

    // ================================================================
    // WT-3-B: 并发 50 个退款同一订单（TOCTOU race 防御）
    // ================================================================
    @Test
    @Order(2)
    @DisplayName("WT-3-B: 50 并发 refund 同一订单 — 仅 1 个成功，其余 REFUND_EXCEED")
    void concurrentDoubleRefund() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENCY);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger refundExceed = new AtomicInteger(0);
        AtomicInteger other = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENCY; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    start.await();
                    WalletOpDTO dto = new WalletOpDTO();
                    dto.setMemberId(testMemberId);
                    dto.setAmount(REFUND_AMOUNT);
                    dto.setOrderId(testOrderId);
                    dto.setOperatorId(1L);
                    dto.setPayMethod("原路返回");
                    dto.setRemark("WT-3-B 并发退款 #" + idx);
                    Result<Map<String, Object>> r = walletService.doRefund(dto, TEST_TENANT);
                    if (r.getCode() == 200) {
                        success.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                    }
                } catch (BusinessException e) {
                    if (e.getCode() == BizCode.REFUND_EXCEED.code) {
                        refundExceed.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                        System.out.println("[WT-3-B] BusinessException: " + e.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    other.incrementAndGet();
                    System.out.println("[WT-3-B] " + e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        long t0 = System.currentTimeMillis();
        start.countDown();
        assertTrue(done.await(120, TimeUnit.SECONDS), "并发退款超时");
        long elapsed = System.currentTimeMillis() - t0;

        // ★ 核心断言：50 个并发只有 1 个成功，其余 49 个 REFUND_EXCEED
        assertEquals(1, success.get(),
                "50 个并发退款同一订单，仅 1 个应成功，实际成功=" + success.get() +
                ", refundExceed=" + refundExceed.get() + ", other=" + other.get());
        assertEquals(CONCURRENCY - 1, refundExceed.get(),
                "其余 49 个应抛 REFUND_EXCEED，实际=" + refundExceed.get());
        assertEquals(0, other.get(), "不应有其他异常，other=" + other.get());

        // 余额只增加了 1 次（不是 50 次）
        BigDecimal bal = jdbc.queryForObject(
            "SELECT balance FROM crm_wallet WHERE member_id = ? AND deleted = 0",
            BigDecimal.class, testMemberId);
        assertEquals(0, INITIAL.add(REFUND_AMOUNT).compareTo(bal),
                "余额只 +500（单次退款），不应 +500×50，实际=" + bal);

        // 只有 1 条 refund 流水
        Long refundCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet_transaction WHERE order_id = ? AND type = 3",
            Long.class, testOrderId);
        assertEquals(1L, refundCount.longValue(),
                "应有 1 条 type=3 退款流水，实际=" + refundCount);

        System.out.printf("[WT-3-B] %d 并发退款完成，耗时 %d ms，success=%d，refundExceed=%d，余额=%.2f%n",
                CONCURRENCY, elapsed, success.get(), refundExceed.get(), bal);

        executor.shutdown();
    }
}