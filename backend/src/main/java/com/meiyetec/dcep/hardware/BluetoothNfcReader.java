package com.meiyetec.dcep.hardware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.dcep.crypto.SmCryptoProvider;
import com.meiyetec.dcep.hardware.ble.BleDevice;
import com.meiyetec.dcep.hardware.ble.BleGattClient;
import com.meiyetec.dcep.hardware.ble.BleGattProtocol;
import com.meiyetec.dcep.hardware.ble.PairingState;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DCEP-04 阶段 C：基于 BLE GATT 协议的 NFC 读卡器实现（骨架版）
 *
 * <p>功能：
 * <ul>
 *   <li>通过 BLE GATT 与钱包/SE 芯片通信</li>
 *   <li>构造 ISO 7816 APDU 命令并写入 {@code APDU_REQUEST} characteristic</li>
 *   <li>通过 {@code APDU_RESPONSE} Notify 接收响应</li>
 *   <li>SM2 双向认证（设备私钥签名 + 后端公钥验签）</li>
 *   <li>心跳 + OTA 控制通道</li>
 * </ul>
 *
 * <h3>激活方式</h3>
 * 通过配置 {@code dcep.hardware.reader=bluetooth} 切换；
 * 缺省仍使用 {@link SimulatedNfcReader}。
 *
 * <h3>真实硬件接入步骤</h3>
 * <ol>
 *   <li>替换 {@link BleGattClient} Bean 为厂商 SDK 实现（bluez / Nordic / Android）</li>
 *   <li>替换 UUID 常量为厂商实际使用的 128-bit UUID（{@link BleGattProtocol}）</li>
 *   <li>替换 APDU 编码规则为厂商私有的扩展规则</li>
 * </ol>
 */
@Slf4j
public class BluetoothNfcReader implements NfcReader {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long APDU_RESPONSE_TIMEOUT_MS = 5000;

    private final BleGattClient ble;
    private final SmCryptoProvider smCrypto;

    /** deviceSerial → BLE address 映射（来自设备注册） */
    private final Map<String, String> addrMapping = new ConcurrentHashMap<>();

    /** deviceSerial → 已订阅的响应监听器（异步收集 APDU 响应） */
    private final Map<String, ResponseCollector> collectors = new ConcurrentHashMap<>();

    /** deviceSerial → 设备公钥（hex，来自设备注册时的证书） */
    private final Map<String, String> devicePublicKeys = new ConcurrentHashMap<>();

    /** 序号（用于请求 - 响应配对） */
    private final AtomicLong seq = new AtomicLong(1);

    public BluetoothNfcReader(BleGattClient ble, SmCryptoProvider smCrypto) {
        this.ble = ble;
        this.smCrypto = smCrypto;
    }

    /** 注册设备（OTA 通道设备注册时调用） */
    public void registerDevice(String deviceSerial, String bleAddress, String devicePublicKeyHex) {
        addrMapping.put(deviceSerial, bleAddress);
        devicePublicKeys.put(deviceSerial, devicePublicKeyHex);
        log.info("[BLE-NFC] registerDevice: serial={} addr={} pubKeyLen={}",
                deviceSerial, bleAddress,
                devicePublicKeyHex == null ? 0 : devicePublicKeyHex.length());
    }

    @Override
    public boolean isPresent(String deviceSerial) {
        String addr = addrMapping.get(deviceSerial);
        if (addr == null) return false;
        PairingState s = ble.getState(addr);
        return s != PairingState.DISCONNECTED;
    }

    @Override
    public String selectApplet(String deviceSerial, String aid) {
        // ISO 7816-4 SELECT 命令：CLA=A0 INS=A4 P1=04 P2=00 Lc=<aid.length> Data=<aid>
        byte[] aidBytes = aid.getBytes(StandardCharsets.UTF_8);
        byte[] apdu = buildApdu((byte) 0xA0, (byte) 0xA4, (byte) 0x04, (byte) 0x00, aidBytes, 0);
        return invokeAndParse(deviceSerial, apdu, "SELECT")
                .map(resp -> new String(resp, StandardCharsets.UTF_8))
                .orElse("APPLET_" + deviceSerial);
    }

    @Override
    public NfcBalance readBalance(String deviceSerial) {
        byte[] apdu = buildApdu((byte) 0xA0, (byte) 0xCA, (byte) 0x00, (byte) 0x80, null, 0);
        byte[] resp = invokeAndParse(deviceSerial, apdu, "GET_DATA")
                .orElse(new byte[]{(byte) 0x90, 0x00});
        return parseBalance(resp);
    }

    @Override
    public NfcDebitReceipt debitOffline(String deviceSerial, BigDecimal amount, String merchantId) {
        // 1. 构造业务命令（带交易序号）
        long currentSeq = seq.incrementAndGet();
        String txNo = "OFF-" + currentSeq + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        byte[] bizPayload = (txNo + "|" + amount.toPlainString() + "|" + merchantId)
                .getBytes(StandardCharsets.UTF_8);

        // 2. SM2 双向认证：后端用商户私钥签名请求（可选：取决于厂商协议）
        byte[] sig = signMerchantRequest(bizPayload, deviceSerial);

        // 3. 构造 APDU：CLA=A0 INS=D0 P1=00 P2=00 Lc=<biz+sig.length>
        byte[] payload = new byte[bizPayload.length + sig.length];
        System.arraycopy(bizPayload, 0, payload, 0, bizPayload.length);
        System.arraycopy(sig, 0, payload, bizPayload.length, sig.length);
        byte[] apdu = buildApdu((byte) 0xA0, (byte) 0xD0, (byte) 0x00, (byte) 0x00, payload, 0);

        // 4. 发送并等响应
        byte[] resp = invokeAndParse(deviceSerial, apdu, "DEBIT")
                .orElseThrow(() -> new NfcException("6F00", "设备无响应"));
        return parseDebitReceipt(resp, txNo, amount);
    }

    @Override
    public void close(String deviceSerial) {
        String addr = addrMapping.get(deviceSerial);
        if (addr != null) {
            ble.disconnect(addr);
        }
        collectors.remove(deviceSerial);
    }

    // ============================================================
    // APDU 构造与解析
    // ============================================================

    private byte[] buildApdu(byte cla, byte ins, byte p1, byte p2, byte[] data, int le) {
        int len = 4 + (data == null ? 0 : 1 + data.length) + (le > 0 ? 1 : 0);
        byte[] out = new byte[len];
        int idx = 0;
        out[idx++] = cla;
        out[idx++] = ins;
        out[idx++] = p1;
        out[idx++] = p2;
        if (data != null && data.length > 0) {
            out[idx++] = (byte) data.length;
            System.arraycopy(data, 0, out, idx, data.length);
            idx += data.length;
        }
        if (le > 0) {
            out[idx] = (byte) le;
        }
        return out;
    }

    private java.util.Optional<byte[]> invokeAndParse(String deviceSerial, byte[] apdu, String cmdName) {
        String addr = addrMapping.get(deviceSerial);
        if (addr == null) {
            throw new NfcException("设备未注册：" + deviceSerial);
        }
        PairingState state = ble.getState(addr);
        if (state != PairingState.PAIRED) {
            throw new NfcException("设备未就绪，state=" + state);
        }

        // 1. 订阅 APDU_RESPONSE（每次调用前重新订阅，确保拿到响应）
        ResponseCollector collector = new ResponseCollector();
        collectors.put(deviceSerial, collector);
        ble.subscribe(addr, BleGattProtocol.CHAR_APDU_RESPONSE,
                new BleGattClient.NotificationListener() {
                    @Override
                    public void onData(String deviceAddress, String characteristicUuid, byte[] data) {
                        if (BleGattProtocol.CHAR_APDU_RESPONSE.equals(characteristicUuid)) {
                            collector.complete(data);
                        }
                    }
                    @Override
                    public void onError(String deviceAddress, Throwable error) {
                        collector.fail(new NfcException("链路异常：" + error.getMessage()));
                    }
                });

        // 2. 写入 APDU
        ble.writeApdu(addr, apdu).join();

        // 3. 等响应
        try {
            byte[] resp = collector.future.get(APDU_RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            // 检查 SW（status word）末尾 2 字节
            if (resp.length >= 2) {
                int sw1 = resp[resp.length - 2] & 0xFF;
                int sw2 = resp[resp.length - 1] & 0xFF;
                if (sw1 != 0x90 || sw2 != 0x00) {
                    throw new NfcException(String.format("%02X%02X", sw1, sw2),
                            "APDU 失败：" + cmdName + " SW=" + String.format("%02X%02X", sw1, sw2));
                }
                // 去掉末尾 SW，保留业务数据
                byte[] data = new byte[resp.length - 2];
                System.arraycopy(resp, 0, data, 0, data.length);
                return java.util.Optional.of(data);
            }
            return java.util.Optional.of(resp);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("[BLE-NFC] APDU 超时：cmd={} serial={}", cmdName, deviceSerial);
            throw new NfcException("6F00", "APDU 超时：" + cmdName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NfcException("6F00", "中断");
        } catch (java.util.concurrent.ExecutionException e) {
            throw new NfcException("6F00", "执行失败：" + e.getCause().getMessage());
        }
    }

    private NfcBalance parseBalance(byte[] resp) {
        if (resp.length < 16) {
            return new NfcBalance(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        long onlineCents = ((long) (resp[0] & 0xFF) << 24)
                | ((long) (resp[1] & 0xFF) << 16)
                | ((long) (resp[2] & 0xFF) << 8)
                | (resp[3] & 0xFF);
        long offlineCents = ((long) (resp[4] & 0xFF) << 24)
                | ((long) (resp[5] & 0xFF) << 16)
                | ((long) (resp[6] & 0xFF) << 8)
                | (resp[7] & 0xFF);
        NfcBalance b = new NfcBalance();
        b.onlineBalance = BigDecimal.valueOf(onlineCents, 2);
        b.offlineBalance = BigDecimal.valueOf(offlineCents, 2);
        b.currency = "CNY";
        return b;
    }

    private NfcDebitReceipt parseDebitReceipt(byte[] resp, String txNo, BigDecimal amount) {
        if (resp.length < 96) {
            throw new NfcException("响应长度不足：" + resp.length);
        }
        NfcDebitReceipt r = new NfcDebitReceipt();
        r.txNo = txNo;
        // 前 32 字节 = walletSignature
        StringBuilder ws = new StringBuilder(64);
        for (int i = 0; i < 32; i++) ws.append(String.format("%02X", resp[i] & 0xFF));
        r.walletSignature = ws.toString();
        // 接下来 32 字节 = merchantSignature
        StringBuilder ms = new StringBuilder(64);
        for (int i = 32; i < 64; i++) ms.append(String.format("%02X", resp[i] & 0xFF));
        r.merchantSignature = ms.toString();
        // 接下来 32 字节 = offlineCert（设备 hash 链）
        StringBuilder oc = new StringBuilder(64);
        for (int i = 64; i < 96; i++) oc.append(String.format("%02X", resp[i] & 0xFF));
        r.offlineCert = oc.toString();
        // 解析扣除后余额
        if (resp.length >= 104) {
            long offlineCents = ((long) (resp[96] & 0xFF) << 24)
                    | ((long) (resp[97] & 0xFF) << 16)
                    | ((long) (resp[98] & 0xFF) << 8)
                    | (resp[99] & 0xFF);
            r.offlineBalance = BigDecimal.valueOf(offlineCents, 2);
        } else {
            r.offlineBalance = BigDecimal.ZERO.subtract(amount);
        }
        return r;
    }

    // ============================================================
    // SM2 双向认证
    // ============================================================

    private byte[] signMerchantRequest(byte[] payload, String deviceSerial) {
        if (smCrypto == null) {
            log.warn("[BLE-NFC] SmCryptoProvider 未配置，跳过商户签名");
            return new byte[0];
        }
        // 真实环境：从商户私钥库读取私钥（按 deviceSerial 派生）
        // 这里用占位：SM3 哈希作为签名（仅占位，真实硬件对接时换 SM2）
        try {
            byte[] hash = smCrypto.sm3Hash(payload);
            return Base64.getEncoder().encode(hash);
        } catch (Exception e) {
            log.warn("[BLE-NFC] 签名失败：{}，发送无签名请求", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * 验签设备响应（设备私钥签名 → 后端用设备公钥验签）
     */
    public boolean verifyDeviceResponse(String deviceSerial, byte[] payload, byte[] signature) {
        if (smCrypto == null) return false;
        String pubKeyHex = devicePublicKeys.get(deviceSerial);
        if (pubKeyHex == null) {
            log.warn("[BLE-NFC] 设备 {} 未注册公钥", deviceSerial);
            return false;
        }
        try {
            byte[] pubKeyBytes = hexToBytes(pubKeyHex);
            return smCrypto.sm2Verify(pubKeyBytes, payload,
                    com.meiyetec.dcep.crypto.Sm2Signature.fromConcat(signature));
        } catch (Exception e) {
            log.warn("[BLE-NFC] 设备响应验签失败：{}", e.getMessage());
            return false;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    /**
     * APDU 响应收集器（异步 Future）
     */
    private static class ResponseCollector {
        final java.util.concurrent.CompletableFuture<byte[]> future =
                new java.util.concurrent.CompletableFuture<>();

        void complete(byte[] data) {
            future.complete(data);
        }

        void fail(NfcException e) {
            future.completeExceptionally(e);
        }
    }
}
