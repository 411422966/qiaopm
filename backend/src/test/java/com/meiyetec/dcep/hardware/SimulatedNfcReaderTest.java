package com.meiyetec.dcep.hardware;

import com.meiyetec.dcep.crypto.SmCryptoProvider;
import com.meiyetec.dcep.crypto.impl.BouncyCastleSmCryptoProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DCEP-01 SimulatedNfcReader 场景测试
 *
 * <p>覆盖：
 * <ol>
 *   <li>基础场景：selectApplet / readBalance / debitOffline</li>
 *   <li>异常场景：余额不足 / AID 不匹配 / 设备掉线</li>
 *   <li>并发场景：同钱包并发扣款（潜在双花风险）</li>
 *   <li>SM3 适配：通过 SmCryptoProvider 计算签名（真实国密）</li>
 * </ol>
 */
@DisplayName("DCEP-01 SimulatedNfcReader 多场景")
class SimulatedNfcReaderTest {

    private SimulatedNfcReader reader;
    private final SmCryptoProvider sm = new BouncyCastleSmCryptoProvider();

    @BeforeEach
    void setUp() throws Exception {
        reader = new SimulatedNfcReader();
        // 注入 SmCryptoProvider（模拟 Spring 注入）
        Field f = SimulatedNfcReader.class.getDeclaredField("smCrypto");
        f.setAccessible(true);
        f.set(reader, sm);
    }

    private String newWallet() {
        return "WALLET-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ============================================================
    // 1. 基础场景
    // ============================================================

    @Test
    @DisplayName("isPresent：有效 deviceSerial 返回 true")
    void isPresent_validSerial() {
        assertTrue(reader.isPresent("WALLET-001"));
        assertFalse(reader.isPresent(""));
        assertFalse(reader.isPresent(null));
    }

    @Test
    @DisplayName("selectApplet：DCEP AID 应返回 appletId")
    void selectApplet_dcepAid() {
        String applet = reader.selectApplet("WALLET-001", "A0000000032203");
        assertNotNull(applet);
        assertTrue(applet.contains("WALLET-001"));
    }

    @Test
    @DisplayName("selectApplet：非 DCEP AID 抛 NfcException")
    void selectApplet_otherAid() {
        assertThrows(NfcException.class,
                () -> reader.selectApplet("WALLET-001", "A000000001"));
    }

    @Test
    @DisplayName("readBalance：返回默认余额 1000/500")
    void readBalance_default() {
        NfcBalance b = reader.readBalance("WALLET-NEW");
        assertEquals(new BigDecimal("1000"), b.onlineBalance);
        assertEquals(new BigDecimal("500"), b.offlineBalance);
        assertEquals("CNY", b.currency);
    }

    @Test
    @DisplayName("readBalance：自定义余额")
    void readBalance_custom() {
        reader.setBalance("WALLET-001", new BigDecimal("2000"), new BigDecimal("1500"));
        NfcBalance b = reader.readBalance("WALLET-001");
        assertEquals(new BigDecimal("2000"), b.onlineBalance);
        assertEquals(new BigDecimal("1500"), b.offlineBalance);
    }

    @Test
    @DisplayName("debitOffline：扣款后余额递减、收据含双签名")
    void debitOffline_success() {
        String wallet = newWallet();
        reader.setBalance(wallet, new BigDecimal("1000"), new BigDecimal("1000"));

        NfcDebitReceipt r = reader.debitOffline(wallet, new BigDecimal("100"), "MERCHANT-001");
        assertNotNull(r.txNo);
        assertTrue(r.txNo.startsWith("OFF-"));
        assertNotNull(r.offlineCert);
        assertNotNull(r.walletSignature);
        assertNotNull(r.merchantSignature);
        assertEquals(new BigDecimal("900"), r.offlineBalance);
        // 凭证应为 SM3(64 字符 hex)
        assertTrue(r.offlineCert.matches("[0-9A-F]{64}"),
                "offlineCert 必须是 64 hex 字符（SM3 hash）");
    }

    @Test
    @DisplayName("debitOffline：金额 ≤ 0 抛 NfcException")
    void debitOffline_invalidAmount() {
        String wallet = newWallet();
        assertThrows(NfcException.class,
                () -> reader.debitOffline(wallet, BigDecimal.ZERO, "M"));
        assertThrows(NfcException.class,
                () -> reader.debitOffline(wallet, null, "M"));
        assertThrows(NfcException.class,
                () -> reader.debitOffline(wallet, new BigDecimal("-10"), "M"));
    }

    @Test
    @DisplayName("close：无异常")
    void close_noop() {
        assertDoesNotThrow(() -> reader.close("WALLET-001"));
    }

    // ============================================================
    // 2. 异常场景（scenario 切换）
    // ============================================================

    @Test
    @DisplayName("INSUFFICIENT_BALANCE 场景：扣款必抛 NfcException")
    void scenario_insufficientBalance() {
        String wallet = newWallet();
        reader.setScenario(wallet, SimulatedNfcReader.SimulatedScenario.INSUFFICIENT_BALANCE);
        reader.setBalance(wallet, new BigDecimal("1000"), new BigDecimal("1000"));

        NfcException ex = assertThrows(NfcException.class,
                () -> reader.debitOffline(wallet, new BigDecimal("100"), "M"));
        assertTrue(ex.getMessage().contains("余额不足"));
    }

    @Test
    @DisplayName("UNSUPPORTED_AID 场景：selectApplet 抛 NfcException")
    void scenario_unsupportedAid() {
        String wallet = newWallet();
        reader.setScenario(wallet, SimulatedNfcReader.SimulatedScenario.UNSUPPORTED_AID);
        assertThrows(NfcException.class,
                () -> reader.selectApplet(wallet, "A0000000032203"));
    }

    @Test
    @DisplayName("DEVICE_OFFLINE 场景：isPresent 返回 false")
    void scenario_deviceOffline() {
        String wallet = newWallet();
        reader.setScenario(wallet, SimulatedNfcReader.SimulatedScenario.DEVICE_OFFLINE);
        assertFalse(reader.isPresent(wallet));
    }

    @Test
    @DisplayName("resetScenarios 清空所有 scenario 标记")
    void resetScenarios() {
        reader.setScenario("w1", SimulatedNfcReader.SimulatedScenario.DEVICE_OFFLINE);
        reader.resetScenarios();
        assertTrue(reader.isPresent("w1"), "resetScenarios 后 isPresent 应恢复 true");
    }

    @Test
    @DisplayName("debitOffline：实际余额 < 扣款金额（不依赖 scenario）也抛 NfcException")
    void debitOffline_balanceInsufficient_realCheck() {
        String wallet = newWallet();
        reader.setBalance(wallet, new BigDecimal("1000"), new BigDecimal("50"));
        NfcException ex = assertThrows(NfcException.class,
                () -> reader.debitOffline(wallet, new BigDecimal("100"), "M"));
        assertTrue(ex.getMessage().contains("余额不足"));
    }

    // ============================================================
    // 3. 并发场景（潜在双花）
    // ============================================================

    @Test
    @DisplayName("并发扣款：模拟双花风险（同钱包 50 笔各 100 元，并发执行）")
    void concurrentDebit_doubleSpendRisk() throws Exception {
        String wallet = newWallet();
        reader.setBalance(wallet, new BigDecimal("100000"), new BigDecimal("5000"));

        int concurrency = 50;
        BigDecimal amount = new BigDecimal("100");

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    reader.debitOffline(wallet, amount, "M");
                    successCount.incrementAndGet();
                } catch (NfcException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        // 5000 余额，100 元/笔 → 期望 50 笔成功 / 0 笔失败（无并发保护）
        // 但由于实际是 50 笔各 100 = 5000 总额，模拟读卡器无并发 CAS，应全部成功
        assertTrue(successCount.get() > 0);
        assertEquals(concurrency, successCount.get() + failCount.get());
    }

    // ============================================================
    // 4. SM3 适配：凭证是真实国密哈希
    // ============================================================

    @Test
    @DisplayName("debitOffline 的 offlineCert 是真实 SM3 哈希（64 字符 hex）")
    void debitOffline_offlineCertIsSm3() {
        String wallet = newWallet();
        reader.setBalance(wallet, new BigDecimal("1000"), new BigDecimal("1000"));

        NfcDebitReceipt r = reader.debitOffline(wallet, new BigDecimal("100"), "M");
        // SM3 输出 64 字符（256 bit）
        assertEquals(64, r.offlineCert.length());
        // 必须是 hex
        assertTrue(r.offlineCert.matches("[0-9A-Fa-f]{64}"));
    }

    @Test
    @DisplayName("debitOffline 的 walletSignature / merchantSignature 也是 SM3 哈希")
    void debitOffline_signaturesAreSm3() {
        String wallet = newWallet();
        reader.setBalance(wallet, new BigDecimal("1000"), new BigDecimal("1000"));

        NfcDebitReceipt r = reader.debitOffline(wallet, new BigDecimal("100"), "M");
        assertEquals(64, r.walletSignature.length());
        assertEquals(64, r.merchantSignature.length());
    }
}
