package com.meiyetec.dcep.hardware;

import com.meiyetec.dcep.crypto.SmCryptoProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DCEP-01：模拟 NFC 读卡器实现（增强版）
 *
 * <p>用于开发/测试环境，模拟真实 NFC-SIM / SE 芯片的 APDU 响应与离线扣款流。
 * <p>支持场景：
 * <ul>
 *   <li>普通扣款（单笔 + 双签）</li>
 *   <li>余额不足扣款（返回 {@link NfcException}）</li>
 *   <li>并发双花（同钱包 2 笔扣款都通过 → 双花风险）</li>
 *   <li>不存在的钱包（AID 选择失败）</li>
 *   <li>AID 不匹配（reject）</li>
 * </ul>
 *
 * <p>激活方式：通过配置 {@code dcep.hardware.reader=simulated} 切换；
 * 缺省即使用模拟实现。
 *
 * <p>签名实现：通过 {@link SmCryptoProvider#sm3HashHex(String)} 计算 SM3 哈希作为
 * "签名"占位；真实硬件读卡器将返回 SM2 签名结果。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "dcep.hardware.reader", havingValue = "simulated", matchIfMissing = true)
public class SimulatedNfcReader implements NfcReader {

    /** 数字人民币钱包 AID（人民银行规范） */
    private static final String DCEP_AID = "A0000000032203";

    /** 模拟钱包状态：deviceSerial → state */
    private final Map<String, WalletState> states = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private SmCryptoProvider smCrypto;

    /** 模拟配置：用于触发特定异常场景 */
    private final Map<String, SimulatedScenario> scenarios = new ConcurrentHashMap<>();

    /** 模拟钱包运行时状态 */
    public static class WalletState {
        public BigDecimal onlineBalance = BigDecimal.valueOf(1000);
        public BigDecimal offlineBalance = BigDecimal.valueOf(500);
        public final Map<String, String> recentTxNos = new ConcurrentHashMap<>();
    }

    /** 模拟测试场景 */
    public enum SimulatedScenario {
        NONE,
        /** 余额不足：所有 debit 都抛 INSUFFICIENT_BALANCE */
        INSUFFICIENT_BALANCE,
        /** AID 不匹配：selectApplet 抛 UNSUPPORTED_AID */
        UNSUPPORTED_AID,
        /** 设备掉线：isPresent 返回 false */
        DEVICE_OFFLINE
    }

    /** 设置某设备的模拟场景（仅测试用） */
    public void setScenario(String deviceSerial, SimulatedScenario scenario) {
        scenarios.put(deviceSerial, scenario);
    }

    /** 重置所有场景 */
    public void resetScenarios() {
        scenarios.clear();
    }

    /** 设置余额（仅测试用） */
    public void setBalance(String deviceSerial, BigDecimal online, BigDecimal offline) {
        states.computeIfAbsent(deviceSerial, k -> new WalletState());
        WalletState s = states.get(deviceSerial);
        s.onlineBalance = online;
        s.offlineBalance = offline;
    }

    @Override
    public boolean isPresent(String deviceSerial) {
        if (deviceSerial == null || deviceSerial.isEmpty()) return false;
        SimulatedScenario sc = scenarios.getOrDefault(deviceSerial, SimulatedScenario.NONE);
        return sc != SimulatedScenario.DEVICE_OFFLINE;
    }

    @Override
    public String selectApplet(String deviceSerial, String aid) {
        SimulatedScenario sc = scenarios.getOrDefault(deviceSerial, SimulatedScenario.NONE);
        if (sc == SimulatedScenario.UNSUPPORTED_AID) {
            throw new NfcException("Unsupported AID: " + aid);
        }
        if (!DCEP_AID.equals(aid)) {
            throw new NfcException("Unsupported AID: " + aid);
        }
        return "APPLET_" + deviceSerial;
    }

    @Override
    public NfcBalance readBalance(String deviceSerial) {
        WalletState s = states.computeIfAbsent(deviceSerial, k -> new WalletState());
        NfcBalance b = new NfcBalance();
        b.onlineBalance = s.onlineBalance;
        b.offlineBalance = s.offlineBalance;
        b.currency = "CNY";
        b.lastSyncEpochMs = System.currentTimeMillis();
        return b;
    }

    @Override
    public NfcDebitReceipt debitOffline(String deviceSerial, BigDecimal amount, String merchantId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NfcException("金额非法: " + amount);
        }
        SimulatedScenario sc = scenarios.getOrDefault(deviceSerial, SimulatedScenario.NONE);

        WalletState s = states.computeIfAbsent(deviceSerial, k -> new WalletState());

        if (sc == SimulatedScenario.INSUFFICIENT_BALANCE || s.offlineBalance.compareTo(amount) < 0) {
            throw new NfcException("余额不足：offline=" + s.offlineBalance + " < required=" + amount);
        }

        String txNo = "OFF-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        // 模拟 SM2 双签名（钱包侧 + 商户侧）— 用 SM3 哈希作为签名占位
        String walletSig = sign("wallet:" + deviceSerial + ":" + amount + ":" + txNo);
        String merchantSig = sign("merchant:" + merchantId + ":" + amount + ":" + txNo);
        // 离线凭证 = SM3(txNo + walletSig + merchantSig)
        String cert = hash3(txNo + walletSig + merchantSig);

        // 扣减离线余额（注意：并发场景下可能产生双花，真实硬件通过 SM2 签名 + 计数器防双花）
        s.offlineBalance = s.offlineBalance.subtract(amount);
        s.recentTxNos.put(txNo, deviceSerial);

        log.info("[NfcSim] debit wallet={} amount={} merchant={} txNo={} → offlineBalance={}",
                deviceSerial, amount, merchantId, txNo, s.offlineBalance);
        return new NfcDebitReceipt(txNo, cert, walletSig, merchantSig, s.offlineBalance);
    }

    @Override
    public void close(String deviceSerial) {
        // 模拟无操作
    }

    // ============================================================
    // 签名/哈希（通过 SM3 适配层，真实硬件走 SM2）
    // ============================================================

    private String sign(String input) {
        return hash3("SIG:" + input);
    }

    private String hash3(String input) {
        if (smCrypto != null) {
            try {
                return smCrypto.sm3HashHex(input);
            } catch (Exception e) {
                log.debug("[NfcSim] SM3 不可用，回退到 SHA-256：{}", e.getMessage());
            }
        }
        // 回退：JDK 自带 SHA-256（仅作占位）
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
