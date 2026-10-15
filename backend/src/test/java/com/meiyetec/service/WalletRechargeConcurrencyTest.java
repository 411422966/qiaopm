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
 * P0-TEST-001 · WT-1 · 钱包并发充值测试
 *
 * <p>验证：
 * <ul>
 *   <li>50 线程同时 doRecharge 同一会员（每人 100 元）</li>
 *   <li>最终余额 = 5000 元（无丢失、无超额）</li>
 *   <li>crm_wallet.version 正确累加（反映 CAS 重试次数）</li>
 *   <li>所有 50 次都成功（不存在"消失"的请求）</li>
 * </ul>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WalletRechargeConcurrencyTest {

    private static final Long TEST_TENANT = 1L;
    private static final int CONCURRENCY = 50;
    private static final BigDecimal PER_AMOUNT = new BigDecimal("100.00");

    private static Long testMemberId;

    @Autowired private WalletService walletService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(TEST_TENANT);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        // 创建独立测试会员
        String ts = String.valueOf(System.currentTimeMillis());
        String suffix = ts.substring(ts.length() - 9);
        String phone = "W" + suffix;
        String memberNo = "WT" + suffix;
        jdbc.update(
            "INSERT INTO crm_member(tenant_id, store_id, member_no, name, phone, gender, status, points, balance, total_consume, join_date) " +
            "VALUES(?, 1, ?, ?, ?, 0, 1, 0, ?, ?, CURDATE())",
            TEST_TENANT, memberNo, "WT-1 并发充值测试", phone, BigDecimal.ZERO, BigDecimal.ZERO);
        testMemberId = jdbc.queryForObject(
            "SELECT id FROM crm_member WHERE member_no = ?", Long.class, memberNo);

        // P0-TEST-001: 预创建 wallet，避免 ensureWallet 阶段 50 并发 INSERT 死锁
        jdbc.update(
            "INSERT INTO crm_wallet(tenant_id, member_id, balance, total_recharge, total_consume, frozen_amount, version) " +
            "VALUES(?, ?, 0, 0, 0, 0, 0)",
            TEST_TENANT, testMemberId);
    }

    @AfterEach
    void tearDown() {
        if (testMemberId != null) {
            try {
                jdbc.update("DELETE FROM crm_wallet_transaction WHERE member_id = ?", testMemberId);
                jdbc.update("DELETE FROM crm_wallet WHERE member_id = ?", testMemberId);
                jdbc.update("DELETE FROM crm_member WHERE id = ?", testMemberId);
            } catch (Exception ignore) {}
            testMemberId = null;
        }
        TenantContextHolder.clear();
    }

    /**
     * WT-1: 50 线程同时充值同一会员，每人 100 元
     * 期望：50 个全部成功 + 余额 = 5000 元 + crm_wallet.version > 0
     */
    @Test
    @Order(1)
    @DisplayName("WT-1: 50 并发充值同一会员 — 余额准确 + 全部成功 + version 累加")
    void concurrentRecharge() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENCY);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger conflict = new AtomicInteger(0);
        AtomicInteger other = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENCY; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    WalletOpDTO dto = new WalletOpDTO();
                    dto.setMemberId(testMemberId);
                    dto.setAmount(PER_AMOUNT);
                    dto.setOperatorId(1L);
                    dto.setPayMethod("现金");
                    dto.setRemark("WT-1 并发充值");
                    Result<Map<String, Object>> r = walletService.doRecharge(dto, TEST_TENANT);
                    if (r.getCode() == 200) {
                        success.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                    }
                } catch (BusinessException e) {
                    if (e.getCode() == BizCode.CONCURRENCY_CONFLICT.code) {
                        conflict.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                        System.out.println("[WT-1] BusinessException: " + e.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    other.incrementAndGet();
                    System.out.println("[WT-1] " + e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        long t0 = System.currentTimeMillis();
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "并发充值超时");
        long elapsed = System.currentTimeMillis() - t0;

        // 50 次全部成功（CAS 重试最多 3 次，应该够用）
        assertEquals(CONCURRENCY, success.get(),
                "50 个并发充值应全部成功，实际成功=" + success.get() +
                ", conflict=" + conflict.get() + ", other=" + other.get());

        // 余额 = 50 × 100 = 5000
        BigDecimal walletBal = jdbc.queryForObject(
            "SELECT balance FROM crm_wallet WHERE member_id = ? AND deleted = 0",
            BigDecimal.class, testMemberId);
        assertEquals(0, new BigDecimal("5000.00").compareTo(walletBal),
                "余额应为 5000，实际=" + walletBal);

        // total_recharge 应 = 5000
        BigDecimal totalRecharge = jdbc.queryForObject(
            "SELECT total_recharge FROM crm_wallet WHERE member_id = ? AND deleted = 0",
            BigDecimal.class, testMemberId);
        assertEquals(0, new BigDecimal("5000.00").compareTo(totalRecharge),
                "total_recharge 应为 5000，实际=" + totalRecharge);

        // crm_wallet.version > 0 (CAS 累加)
        Integer version = jdbc.queryForObject(
            "SELECT version FROM crm_wallet WHERE member_id = ? AND deleted = 0",
            Integer.class, testMemberId);
        assertNotNull(version, "version 应存在");
        assertTrue(version > 0, "version 应 > 0，反映 CAS 累加，实际=" + version);

        // 50 条 recharge 流水
        Long txnCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_wallet_transaction WHERE member_id = ? AND type = 1",
            Long.class, testMemberId);
        assertEquals((long) CONCURRENCY, txnCount.longValue(),
                "应有 50 条 type=1 的充值流水，实际=" + txnCount);

        System.out.printf("[WT-1] %d 并发充值完成，耗时 %d ms，余额=%.2f，version=%d，conflict=%d%n",
                CONCURRENCY, elapsed, walletBal, version, conflict.get());

        executor.shutdown();
    }
}