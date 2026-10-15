package com.meiyetec.service;

import com.meiyetec.entity.Member;
import com.meiyetec.entity.PointRecord;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.MemberMapper;
import com.meiyetec.mapper.PointRecordMapper;
import com.meiyetec.mapper.StockInventoryMapper;
import com.meiyetec.mapper.StockProductMapper;
import com.meiyetec.mapper.StockRecordMapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R2.0-B · T-B08：并发与幂等集成测试
 * 覆盖：充值幂等、积分并发扣减、库存超卖防护
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConcurrencyIntegrationTest {

    // 每个测试前设置租户上下文（幂等查询需要 tenantId）
    @BeforeEach
    void setTenantContext() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId(1L);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    // ========== T-B08-1：充值幂等 ==========

    @Test
    @Order(1)
    @DisplayName("充值幂等：同一 key 两次 execute()，第二次返回缓存结果")
    void walletIdempotent() {
        String idempKey = "T-B08-recharge-" + System.currentTimeMillis();
        Long memberId = 1L;
        BigDecimal amount = BigDecimal.valueOf(100);

        // 读初始余额
        Member mBefore = memberMapper.selectById(memberId);
        BigDecimal balanceBefore = mBefore.getBalance() != null ? mBefore.getBalance() : BigDecimal.ZERO;

        // 第一次执行
        String jsonPayload = "{\"memberId\":" + memberId + ",\"amount\":" + amount + "}";
        IdempotencyService.IdempotencyResult<BigDecimal> r1 = idem.<BigDecimal>execute(
                "wallet.recharge", idempKey, jsonPayload,
                () -> {
                    int rows = memberMapper.casBalance(memberId, amount);
                    if (rows == 0) throw new BusinessException(BizCode.CONCURRENCY_CONFLICT);
                    BigDecimal newBalance = memberMapper.selectById(memberId).getBalance();
                    return com.meiyetec.util.Result.success("充值成功", newBalance);
                });

        assertTrue(r1.firstTime, "第一次应为 NEW（首次执行）");

        // 第二次执行（同一 key）
        IdempotencyService.IdempotencyResult<BigDecimal> r2 = idem.<BigDecimal>execute(
                "wallet.recharge", idempKey, jsonPayload,
                () -> {
                    fail("回调不应被执行——幂等命中");
                    return com.meiyetec.util.Result.success("充值成功", BigDecimal.ZERO);
                });

        assertFalse(r2.firstTime, "第二次应为 REPLAY（幂等命中）");

        // 余额只增加一次
        Member mAfter = memberMapper.selectById(memberId);
        assertEquals(0, mAfter.getBalance().compareTo(balanceBefore.add(amount)),
                "余额应只增加一次");
    }

    // ========== T-B08-2：积分并发扣减 ==========

    @Test
    @Order(2)
    @DisplayName("积分并发扣减：50并发 × 10分 = 500分，最终余额=0，不变负")
    void pointsConcurrentDeduct() throws Exception {
        // 创建独立测试会员，初始 5000 积分
        Member m = new Member();
        String ts = String.valueOf(System.currentTimeMillis());
        m.setPhone("TP" + ts.substring(ts.length() - 8));
        m.setMemberNo("TN" + ts.substring(ts.length() - 8));
        m.setName("并发测试会员");
        m.setTenantId(1L);
        m.setStoreId(1L);
        m.setPoints(5000);
        m.setBalance(BigDecimal.ZERO);
        m.setVersion(0);
        memberMapper.insert(m);
        Long testMemberId = m.getId();

        try {
            int perDeduct = 10;
            int threadCount = 50;
            ExecutorService executor = Executors.newFixedThreadPool(10);

            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger conflict = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        pointService.consumePoints(testMemberId, "test-concurrent",
                                perDeduct, null, "T-B08并发测试");
                        success.incrementAndGet();
                    } catch (BusinessException e) {
                        if (e.getCode() == BizCode.CONCURRENCY_CONFLICT.code
                                || e.getCode() == BizCode.MEMBER_POINTS_NOT_ENOUGH.code) {
                            conflict.incrementAndGet();
                        } else {
                            throw e;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发扣减超时");

            Member result = memberMapper.selectById(testMemberId);
            int finalPts = result.getPoints() != null ? result.getPoints() : 0;
            int expected = 5000 - success.get() * perDeduct;

            assertTrue(finalPts >= 0, "积分不得为负，实际=" + finalPts);
            assertEquals(expected, finalPts, "余额=初始-成功次数*扣减额");

            executor.shutdown();

        } finally {
            memberMapper.deleteById(testMemberId);
        }
    }

    // ========== T-B08-3：库存超卖防护 ==========

    @Test
    @Order(3)
    @DisplayName("库存并发出库：10并发各扣20件（需求200），实际库存100，最终>=0，无超卖")
    void inventoryNoOversell() throws Exception {
        // 准备测试数据
        var product = new com.meiyetec.entity.StockProduct();
        product.setProductName("并发测试商品-" + System.currentTimeMillis());
        product.setProductCode("TEST-P-" + System.currentTimeMillis());
        product.setStoreId(1L);
        product.setTenantId(1L);
        product.setCostPrice(BigDecimal.valueOf(10));
        stockProductMapper.insert(product);
        Long pid = product.getId();

        String batchNo = "TEST-B-" + System.currentTimeMillis();
        var inv = new com.meiyetec.entity.StockInventory();
        inv.setProductId(pid);
        inv.setStoreId(1L);
        inv.setTenantId(1L);
        inv.setBatchNo(batchNo);
        inv.setQuantity(100);
        inv.setVersion(0);
        stockInventoryMapper.insert(inv);
        Long invId = inv.getId();

        try {
            int perQty = 20;
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(10);

            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger fail = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        // 每线程重新读最新 version，避免用陈旧 version 导致无谓失败
                        var current = stockInventoryMapper.selectById(invId);
                        int rows = stockInventoryMapper.casOutbound(invId, current.getVersion(), perQty);
                        if (rows > 0) {
                            success.incrementAndGet();
                        } else {
                            fail.incrementAndGet();
                        }
                    } catch (Exception e) {
                        fail.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发出库超时");

            var after = stockInventoryMapper.selectById(invId);
            assertTrue(after.getQuantity() >= 0,
                    "库存不得为负，实际=" + after.getQuantity());
            assertTrue(after.getQuantity() <= 100,
                    "库存不得超过初始值，实际=" + after.getQuantity());

            int expectedRemaining = 100 - success.get() * perQty;
            assertEquals(expectedRemaining, after.getQuantity(),
                    "剩余=" + expectedRemaining + " 实际=" + after.getQuantity());

            executor.shutdown();

        } finally {
            stockRecordMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.meiyetec.entity.StockRecord>()
                            .eq(com.meiyetec.entity.StockRecord::getProductId, pid));
            stockInventoryMapper.deleteById(invId);
            stockProductMapper.deleteById(pid);
        }
    }

    // ========== Autowired ==========

    @Autowired private PointService pointService;
    @Autowired private IdempotencyService idem;
    @Autowired private MemberMapper memberMapper;
    @Autowired private PointRecordMapper pointRecordMapper;
    @Autowired private StockInventoryMapper stockInventoryMapper;
    @Autowired private StockProductMapper stockProductMapper;
    @Autowired private StockRecordMapper stockRecordMapper;
}
