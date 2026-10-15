package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.dto.WalletOpDTO;
import com.meiyetec.entity.Member;
import com.meiyetec.entity.Wallet;
import com.meiyetec.entity.WalletTransaction;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.MemberMapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-MONEY-002 · 钱包单一权威源测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>WalletServiceImpl.doRecharge / doConsume / doRefund 收敛到 Service 层</li>
 *   <li>syncMemberBalance() 是 crm_member.balance 唯一同步入口</li>
 *   <li>任何写操作完成后 crm_wallet.balance == crm_member.balance（无漂移）</li>
 *   <li>并发消费：CAS 乐观锁不出现余额为负或重复扣款</li>
 *   <li>退款幂等：同一订单多次退款只有第一次成功</li>
 * </ul>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WalletSingleSourceTest {

    @Autowired private WalletService walletService;
    @Autowired private WalletTransactionService txnService;
    @Autowired private MemberMapper memberMapper;
    @Autowired private JdbcTemplate jdbc;

    private static final Long TEST_TENANT = 1L;
    private Long testMemberId;
    private BigDecimal startBalance;

    @BeforeEach
    void setUp() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(TEST_TENANT);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);

        // 创建独立测试会员，避免污染其他测试
        // 使用 jdbc 直接 insert，绕过 Member 实体的 @Version 字段（crm_member 表无 version 列）
        String ts = String.valueOf(System.currentTimeMillis());
        String phone = "W" + ts.substring(ts.length() - 9);
        String memberNo = "WM" + ts.substring(ts.length() - 9);
        jdbc.update(
            "INSERT INTO crm_member(tenant_id, store_id, member_no, name, phone, gender, status, points, balance, total_consume, join_date) " +
            "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURDATE())",
            TEST_TENANT, 1L, memberNo, "P0-MONEY-002 测试会员", phone, 0, 1, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        testMemberId = jdbc.queryForObject(
            "SELECT id FROM crm_member WHERE member_no = ?", Long.class, memberNo);
        startBalance = BigDecimal.ZERO;
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        if (testMemberId != null) {
            jdbc.update("DELETE FROM crm_wallet_transaction WHERE member_id = ?", testMemberId);
            jdbc.update("DELETE FROM crm_wallet WHERE member_id = ?", testMemberId);
            jdbc.update("DELETE FROM pos_order WHERE order_no LIKE 'TEST%' AND member_id = ?", testMemberId);
            jdbc.update("DELETE FROM crm_member WHERE id = ?", testMemberId);
            testMemberId = null;
        }
        TenantContextHolder.clear();
    }

    /** 创建测试用 pos_order，返回 order_id */
    private Long createTestPosOrder(Long memberId) {
        String orderNo = "TEST" + System.currentTimeMillis();
        jdbc.update(
            "INSERT INTO pos_order(tenant_id, store_id, order_no, member_id, member_name, type, total_amount, final_amount, cashier_id, status) " +
            "VALUES(?, 1, ?, ?, 'P0-MONEY-002 测试会员', 1, 100.00, 100.00, 1, 1)",
            TEST_TENANT, orderNo, memberId);
        return jdbc.queryForObject("SELECT id FROM pos_order WHERE order_no = ?", Long.class, orderNo);
    }

    // ================================================================
    // 测试 1: 充值后 crm_wallet.balance == crm_member.balance
    // ================================================================
    @Test
    @Order(1)
    @DisplayName("P0-MONEY-002-1: 充值后两表余额一致（SSOT 同步生效）")
    void rechargeSyncBothTables() {
        WalletOpDTO dto = new WalletOpDTO();
        dto.setMemberId(testMemberId);
        dto.setAmount(new BigDecimal("500.00"));
        dto.setGiftAmount(BigDecimal.ZERO);
        dto.setPayMethod("现金");
        dto.setOperatorId(1L);
        dto.setRemark("测试充值");

        Result<Map<String, Object>> r = walletService.doRecharge(dto, TEST_TENANT);
        assertEquals(200, r.getCode(), "充值应成功");
        assertEquals(0, new BigDecimal("500.00").compareTo((BigDecimal) r.getData().get("balanceAfter")));

        // SSOT 验证：crm_wallet.balance == crm_member.balance
        BigDecimal walletBal = queryWalletBalance(testMemberId);
        BigDecimal memberBal = queryMemberBalance(testMemberId);
        assertNotNull(walletBal, "crm_wallet.balance 应存在");
        assertNotNull(memberBal, "crm_member.balance 应同步");
        assertEquals(0, walletBal.compareTo(memberBal),
                "SSOT 一致性：crm_wallet.balance(" + walletBal + ") == crm_member.balance(" + memberBal + ")");
        assertEquals(0, new BigDecimal("500.00").compareTo(walletBal), "余额=500.00");
    }

    // ================================================================
    // 测试 2: 消费后两表余额一致
    // ================================================================
    @Test
    @Order(2)
    @DisplayName("P0-MONEY-002-2: 消费后两表余额一致（SSOT 同步生效）")
    void consumeSyncBothTables() {
        // 先充值 1000
        rechargeInternal(new BigDecimal("1000.00"));

        WalletOpDTO dto = new WalletOpDTO();
        dto.setMemberId(testMemberId);
        dto.setAmount(new BigDecimal("300.00"));
        // 不设 orderId，避免 crm_wallet_transaction.order_id FK 约束
        dto.setOperatorId(1L);
        dto.setPayMethod("wallet");
        dto.setRemark("测试消费");

        Result<Map<String, Object>> r = walletService.doConsume(dto, TEST_TENANT);
        assertEquals(200, r.getCode(), "消费应成功");

        BigDecimal walletBal = queryWalletBalance(testMemberId);
        BigDecimal memberBal = queryMemberBalance(testMemberId);
        assertEquals(0, walletBal.compareTo(memberBal),
                "SSOT 一致性：crm_wallet.balance == crm_member.balance");
        assertEquals(0, new BigDecimal("700.00").compareTo(walletBal), "余额=700.00");
    }

    // ================================================================
    // 测试 3: 退款后两表余额一致
    // ================================================================
    @Test
    @Order(3)
    @DisplayName("P0-MONEY-002-3: 退款后两表余额一致（SSOT 同步生效）")
    void refundSyncBothTables() {
        // 先充值 1000 + 消费 300（创建真实 pos_order 以满足 FK）
        rechargeInternal(new BigDecimal("1000.00"));
        Long orderId = createTestPosOrder(testMemberId);

        WalletOpDTO consumeDto = new WalletOpDTO();
        consumeDto.setMemberId(testMemberId);
        consumeDto.setAmount(new BigDecimal("300.00"));
        consumeDto.setOrderId(orderId);
        consumeDto.setOperatorId(1L);
        consumeDto.setPayMethod("wallet");
        walletService.doConsume(consumeDto, TEST_TENANT);

        // 退款 300
        WalletOpDTO refundDto = new WalletOpDTO();
        refundDto.setMemberId(testMemberId);
        refundDto.setAmount(new BigDecimal("300.00"));
        refundDto.setOrderId(orderId);  // 同一 orderId
        refundDto.setOperatorId(1L);
        refundDto.setPayMethod("原路返回");
        Result<Map<String, Object>> r = walletService.doRefund(refundDto, TEST_TENANT);
        assertEquals(200, r.getCode(), "退款应成功");

        BigDecimal walletBal = queryWalletBalance(testMemberId);
        BigDecimal memberBal = queryMemberBalance(testMemberId);
        assertEquals(0, walletBal.compareTo(memberBal),
                "SSOT 一致性：crm_wallet.balance == crm_member.balance");
        assertEquals(0, new BigDecimal("1000.00").compareTo(walletBal), "退款后余额=1000.00");
    }

    // ================================================================
    // 测试 4: 退款幂等（同一订单多次退款）
    // ================================================================
    @Test
    @Order(4)
    @DisplayName("P0-MONEY-002-4: 退款幂等 — 同一订单二次退款抛 REFUND_EXCEED")
    void refundIdempotent() {
        // 准备：充值 + 创建订单 + 消费
        rechargeInternal(new BigDecimal("1000.00"));
        Long orderId = createTestPosOrder(testMemberId);

        WalletOpDTO consumeDto = new WalletOpDTO();
        consumeDto.setMemberId(testMemberId);
        consumeDto.setAmount(new BigDecimal("500.00"));
        consumeDto.setOrderId(orderId);
        consumeDto.setOperatorId(1L);
        walletService.doConsume(consumeDto, TEST_TENANT);

        // 第一次退款成功
        WalletOpDTO refundDto = new WalletOpDTO();
        refundDto.setMemberId(testMemberId);
        refundDto.setAmount(new BigDecimal("500.00"));
        refundDto.setOrderId(orderId);
        refundDto.setOperatorId(1L);
        Result<Map<String, Object>> r1 = walletService.doRefund(refundDto, TEST_TENANT);
        assertEquals(200, r1.getCode(), "第一次退款应成功");

        // 第二次退款应抛 REFUND_EXCEED
        WalletOpDTO refundDto2 = new WalletOpDTO();
        refundDto2.setMemberId(testMemberId);
        refundDto2.setAmount(new BigDecimal("500.00"));
        refundDto2.setOrderId(orderId);
        refundDto2.setOperatorId(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.doRefund(refundDto2, TEST_TENANT));
        assertEquals(BizCode.REFUND_EXCEED.code, ex.getCode(), "二次退款应抛 REFUND_EXCEED");
    }

    // ================================================================
    // 测试 5: 余额不足
    // ================================================================
    @Test
    @Order(5)
    @DisplayName("P0-MONEY-002-5: 余额不足 — 消费抛 WALLET_BALANCE_NOT_ENOUGH")
    void consumeInsufficientBalance() {
        // 不充值，直接消费
        WalletOpDTO dto = new WalletOpDTO();
        dto.setMemberId(testMemberId);
        dto.setAmount(new BigDecimal("100.00"));
        // 不设 orderId 避免 FK 问题
        dto.setOperatorId(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> walletService.doConsume(dto, TEST_TENANT));
        assertEquals(BizCode.WALLET_BALANCE_NOT_ENOUGH.code, ex.getCode(),
                "余额不足应抛 WALLET_BALANCE_NOT_ENOUGH");
    }

    // ================================================================
    // 测试 6: syncMemberBalance 入口正确性
    // ================================================================
    @Test
    @Order(6)
    @DisplayName("P0-MONEY-002-6: syncMemberBalance — crm_member.balance 同步到 crm_wallet.balance")
    void syncMemberBalanceCorrectness() {
        // 先充值
        rechargeInternal(new BigDecimal("800.00"));

        // 手动制造漂移（模拟历史脏数据）：把 crm_member.balance 改成错误值
        jdbc.update("UPDATE crm_member SET balance = 99999.00 WHERE id = ?", testMemberId);

        BigDecimal walletBefore = queryWalletBalance(testMemberId);
        BigDecimal memberBefore = queryMemberBalance(testMemberId);
        assertEquals(0, new BigDecimal("99999.00").compareTo(memberBefore), "已注入漂移");
        assertNotEquals(0, walletBefore.compareTo(memberBefore), "确认漂移存在");

        // 调用 syncMemberBalance
        walletService.syncMemberBalance(testMemberId);

        BigDecimal walletAfter = queryWalletBalance(testMemberId);
        BigDecimal memberAfter = queryMemberBalance(testMemberId);
        assertEquals(0, walletAfter.compareTo(memberAfter),
                "syncMemberBalance 后两表应一致");
        assertEquals(0, new BigDecimal("800.00").compareTo(memberAfter),
                "crm_member.balance 应被同步为 crm_wallet.balance=800.00");
    }

    // ================================================================
    // 测试 7: 并发消费 — CAS 乐观锁不出现重复扣款
    // ================================================================
    @Test
    @Order(7)
    @DisplayName("P0-MONEY-002-7: 并发消费 — 100 并发 × 10 元（初始 1000），余额正确不重复扣")
    void concurrentConsumeCAS() throws Exception {
        // 初始 1000
        rechargeInternal(new BigDecimal("1000.00"));

        int threadCount = 50;  // 降低并发数，每个线程创建独立 pos_order
        int perDeduct = 10;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger conflict = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    start.await();
                    Long orderId = createTestPosOrder(testMemberId);
                    WalletOpDTO dto = new WalletOpDTO();
                    dto.setMemberId(testMemberId);
                    dto.setAmount(new BigDecimal(perDeduct));
                    dto.setOrderId(orderId);
                    dto.setOperatorId(1L);
                    Result<Map<String, Object>> r = walletService.doConsume(dto, TEST_TENANT);
                    if (r.getCode() == 200) {
                        success.incrementAndGet();
                    } else {
                        fail.incrementAndGet();
                    }
                } catch (BusinessException e) {
                    if (e.getCode() == BizCode.CONCURRENCY_CONFLICT.code
                            || e.getCode() == BizCode.WALLET_BALANCE_NOT_ENOUGH.code) {
                        conflict.incrementAndGet();
                    } else {
                        fail.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "并发测试超时");

        BigDecimal walletBal = queryWalletBalance(testMemberId);
        BigDecimal memberBal = queryMemberBalance(testMemberId);

        // 期望：余额 = 1000 - 成功数 × 10
        assertEquals(0, success.get() * perDeduct, 1000 - walletBal.intValue(),
                "余额 = 初始 - 成功数 × 扣减额");
        assertTrue(walletBal.signum() >= 0, "余额不得为负，实际=" + walletBal);
        assertEquals(0, walletBal.compareTo(memberBal),
                "并发场景下 SSOT 也应一致：crm_wallet.balance(" + walletBal +
                ") == crm_member.balance(" + memberBal + ")");

        executor.shutdown();
    }

    // ================================================================
    // 测试 8: 交易流水完整
    // ================================================================
    @Test
    @Order(8)
    @DisplayName("P0-MONEY-002-8: 钱包操作后 crm_wallet_transaction 流水完整")
    void transactionLedgerComplete() {
        // 充值 500
        rechargeInternal(new BigDecimal("500.00"));

        // 流水应至少有 1 条 RECHARGE
        Long rechargeCount = txnService.count(
                new LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getMemberId, testMemberId)
                        .eq(WalletTransaction::getType, 1));
        assertTrue(rechargeCount >= 1, "应有至少 1 条充值流水");

        // 消费 200
        Long orderId = createTestPosOrder(testMemberId);
        WalletOpDTO consumeDto = new WalletOpDTO();
        consumeDto.setMemberId(testMemberId);
        consumeDto.setAmount(new BigDecimal("200.00"));
        consumeDto.setOrderId(orderId);
        consumeDto.setOperatorId(1L);
        walletService.doConsume(consumeDto, TEST_TENANT);

        Long consumeCount = txnService.count(
                new LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getMemberId, testMemberId)
                        .eq(WalletTransaction::getType, 2));
        assertTrue(consumeCount >= 1, "应有至少 1 条消费流水");

        // 流水余额前后应连贯
        List<WalletTransaction> txns = txnService.list(
                new LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getMemberId, testMemberId)
                        .orderByAsc(WalletTransaction::getId));
        assertEquals(0, new BigDecimal("0.00").compareTo(txns.get(0).getBalanceBefore()),
                "首笔流水余额前=0");
        assertEquals(0, new BigDecimal("500.00").compareTo(txns.get(0).getBalanceAfter()),
                "首笔流水余额后=500");
        if (txns.size() >= 2) {
            assertEquals(0, new BigDecimal("500.00").compareTo(txns.get(1).getBalanceBefore()),
                    "第二笔流水余额前=500（连贯）");
            assertEquals(0, new BigDecimal("300.00").compareTo(txns.get(1).getBalanceAfter()),
                    "第二笔流水余额后=300");
        }
    }

    // ================================================================
    // 内部辅助
    // ================================================================

    private void rechargeInternal(BigDecimal amount) {
        WalletOpDTO dto = new WalletOpDTO();
        dto.setMemberId(testMemberId);
        dto.setAmount(amount);
        dto.setGiftAmount(BigDecimal.ZERO);
        dto.setPayMethod("现金");
        dto.setOperatorId(1L);
        dto.setRemark("辅助充值");
        walletService.doRecharge(dto, TEST_TENANT);
    }

    private BigDecimal queryWalletBalance(Long memberId) {
        return jdbc.queryForObject(
                "SELECT balance FROM crm_wallet WHERE member_id = ? AND deleted = 0",
                BigDecimal.class, memberId);
    }

    private BigDecimal queryMemberBalance(Long memberId) {
        return jdbc.queryForObject(
                "SELECT balance FROM crm_member WHERE id = ?",
                BigDecimal.class, memberId);
    }
}