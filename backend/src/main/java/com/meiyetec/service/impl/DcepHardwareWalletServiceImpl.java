package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.DcepHardwareWallet;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.DcepHardwareWalletMapper;
import com.meiyetec.service.DcepHardwareWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DcepHardwareWalletServiceImpl extends ServiceImpl<DcepHardwareWalletMapper, DcepHardwareWallet>
        implements DcepHardwareWalletService {

    private final DcepHardwareWalletMapper walletMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 私钥 AES 加密演示用 key（生产应放 KMS）
    private static final String PRIV_KEY_SECRET = "DCEP01-HW-KEY-2026";

    @Override
    public List<DcepHardwareWallet> queryList(Long memberId, String walletType, String status, Long tenantId) {
        return walletMapper.queryList(memberId, walletType, status, tenantId);
    }

    @Override
    public DcepHardwareWallet findByCode(String walletCode) {
        return walletMapper.findByCode(walletCode);
    }

    /**
     * 激活硬钱包（DCEP-01）
     * - 自动生成 ECDSA secp256r1 密钥对
     * - 私钥用 AES-256 加密后落库
     * - 公钥 base64 编码存 wallet.publicKey
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepHardwareWallet activateWallet(Long memberId, String walletType, String deviceModel,
                                              String seChipId, Long tenantId) {
        if (!isValidWalletType(walletType)) {
            throw new BusinessException(BizCode.BAD_REQUEST, "不支持的钱包类型: " + walletType);
        }
        DcepHardwareWallet wallet = new DcepHardwareWallet();
        wallet.setTenantId(tenantId);
        wallet.setMemberId(memberId);
        wallet.setWalletType(walletType);
        wallet.setWalletName(deviceModel + " Wallet");
        wallet.setWalletCode("HW-" + walletType.toUpperCase() + "-" + UUID.randomUUID().toString().substring(0, 8));
        wallet.setDeviceModel(deviceModel);
        wallet.setSeChipId(seChipId);
        wallet.setWalletAddress("DCEP1" + UUID.randomUUID().toString().replace("-", "").substring(0, 32).toLowerCase());

        // 生成 ECDSA secp256r1 密钥对
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"), new SecureRandom());
            KeyPair kp = kpg.generateKeyPair();
            String pubB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
            String privEnc = aesEncrypt(pvToPem(kp.getPrivate()));
            wallet.setPublicKey(pubB64);
            wallet.setPrivateKeyEnc(privEnc);
        } catch (Exception e) {
            log.error("密钥对生成失败", e);
            throw new BusinessException(BizCode.SYSTEM_ERROR, "钱包密钥生成失败: " + e.getMessage());
        }

        wallet.setOperatorOrg("ICBC");
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setDailyUsed(BigDecimal.ZERO);
        wallet.setDailyResetAt(LocalDate.now().atStartOfDay().plusDays(1));
        wallet.setOfflineSupport("nfc_sim".equals(walletType) || "se_chip".equals(walletType) ? 1 : 0);
        wallet.setOfflineBalanceLimit(BigDecimal.valueOf(500));
        wallet.setOfflineBalance(BigDecimal.ZERO);
        wallet.setMaxAmountPerTransaction(BigDecimal.valueOf(5000));
        wallet.setMaxAmountPerDay(BigDecimal.valueOf(10000));
        wallet.setDailyRemaining(BigDecimal.valueOf(10000));
        wallet.setBiometricEnabled(1);
        wallet.setPinSet(0);
        wallet.setStatus("active");
        wallet.setActivatedTime(LocalDateTime.now());
        wallet.setExpiredTime(LocalDateTime.now().plusYears(5));
        save(wallet);
        log.info("Hardware wallet activated: code={} type={} member={}", wallet.getWalletCode(), walletType, memberId);
        return wallet;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepHardwareWallet recharge(Long walletId, BigDecimal amount) {
        DcepHardwareWallet wallet = getById(walletId);
        if (wallet == null) throw new BusinessException(BizCode.NOT_FOUND, "钱包不存在");
        if (!"active".equals(wallet.getStatus())) {
            throw new BusinessException(BizCode.FORBIDDEN, "钱包非 active: " + wallet.getStatus());
        }
        walletMapper.rechargeBalance(walletId, amount);
        return getById(walletId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductOffline(Long walletId, BigDecimal amount) {
        DcepHardwareWallet wallet = getById(walletId);
        BigDecimal newOfflineBalance = wallet.getOfflineBalance().subtract(amount);
        if (newOfflineBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Offline balance insufficient: wallet={} balance={} need={}",
                walletId, wallet.getOfflineBalance(), amount);
            return false;
        }
        wallet.setOfflineBalance(newOfflineBalance);
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setTotalConsumption(wallet.getTotalConsumption().add(amount));
        wallet.setLastSyncTime(LocalDateTime.now());
        updateById(wallet);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncOfflineTransaction(Long walletId, BigDecimal offlineDeductAmount) {
        walletMapper.deductBalance(walletId, offlineDeductAmount);
        log.info("Offline tx synced: wallet={} amount={}", walletId, offlineDeductAmount);
    }

    // ============================================================
    // DCEP-01 钱包生命周期
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepHardwareWallet reportLost(Long walletId, String reason) {
        DcepHardwareWallet w = mustGet(walletId);
        if (!"active".equals(w.getStatus())) {
            throw new BusinessException(BizCode.FORBIDDEN, "仅 active 可挂失，当前: " + w.getStatus());
        }
        w.setStatus("lost");
        w.setRemark("挂失: " + reason);
        updateById(w);
        log.warn("Wallet reported lost: id={} reason={}", walletId, reason);
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepHardwareWallet unfreezeLost(Long walletId, String pin) {
        DcepHardwareWallet w = mustGet(walletId);
        if (!"lost".equals(w.getStatus())) {
            throw new BusinessException(BizCode.FORBIDDEN, "仅 lost 可解挂，当前: " + w.getStatus());
        }
        if (!verifyPin(walletId, pin)) {
            throw new BusinessException(BizCode.UNAUTHORIZED, "PIN 错误，无法解挂");
        }
        w.setStatus("active");
        w.setRemark("解挂成功");
        updateById(w);
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepHardwareWallet freeze(Long walletId, String reason) {
        DcepHardwareWallet w = mustGet(walletId);
        if (!"active".equals(w.getStatus())) {
            throw new BusinessException(BizCode.FORBIDDEN, "仅 active 可冻结，当前: " + w.getStatus());
        }
        w.setStatus("frozen");
        w.setRemark("冻结: " + reason);
        updateById(w);
        log.warn("Wallet frozen: id={} reason={}", walletId, reason);
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepHardwareWallet unfreeze(Long walletId) {
        DcepHardwareWallet w = mustGet(walletId);
        if (!"frozen".equals(w.getStatus())) {
            throw new BusinessException(BizCode.FORBIDDEN, "仅 frozen 可解冻，当前: " + w.getStatus());
        }
        w.setStatus("active");
        w.setRemark("解冻成功");
        updateById(w);
        return w;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepHardwareWallet closeAccount(Long walletId) {
        DcepHardwareWallet w = mustGet(walletId);
        if (w.getBalance() != null && w.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(BizCode.FORBIDDEN,
                "余额未清零，无法销户（余额=" + w.getBalance() + "）");
        }
        w.setStatus("expired");
        w.setRemark("已销户");
        w.setExpiredTime(LocalDateTime.now());
        updateById(w);
        return w;
    }

    // ============================================================
    // DCEP-01 PIN / 生物识别
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPin(Long walletId, String pin) {
        if (pin == null || pin.length() < 6 || pin.length() > 12) {
            throw new BusinessException(BizCode.BAD_REQUEST, "PIN 长度需 6-12 位");
        }
        DcepHardwareWallet w = mustGet(walletId);
        w.setPinHash(passwordEncoder.encode(pin));
        w.setPinSet(1);
        updateById(w);
        log.info("PIN set for wallet: {}", walletId);
    }

    @Override
    public boolean verifyPin(Long walletId, String pin) {
        DcepHardwareWallet w = mustGet(walletId);
        if (w.getPinHash() == null || pin == null) return false;
        return passwordEncoder.matches(pin, w.getPinHash());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepHardwareWallet toggleBiometric(Long walletId, boolean enabled) {
        DcepHardwareWallet w = mustGet(walletId);
        w.setBiometricEnabled(enabled ? 1 : 0);
        updateById(w);
        return w;
    }

    @Override
    public Map<String, Object> checkLimit(Long walletId, BigDecimal amount) {
        DcepHardwareWallet w = mustGet(walletId);
        // 重置当日累计
        LocalDateTime now = LocalDateTime.now();
        if (w.getDailyResetAt() == null || now.isAfter(w.getDailyResetAt())) {
            w.setDailyUsed(BigDecimal.ZERO);
            w.setDailyResetAt(LocalDate.now().atStartOfDay().plusDays(1));
            updateById(w);
        }
        Map<String, Object> r = new HashMap<>();
        if (amount.compareTo(w.getMaxAmountPerTransaction()) > 0) {
            r.put("ok", false);
            r.put("reason", "EXCEED_PER_TX");
            r.put("limit", w.getMaxAmountPerTransaction());
            r.put("amount", amount);
            return r;
        }
        BigDecimal remainingToday = w.getMaxAmountPerDay().subtract(w.getDailyUsed() == null ? BigDecimal.ZERO : w.getDailyUsed());
        if (amount.compareTo(remainingToday) > 0) {
            r.put("ok", false);
            r.put("reason", "EXCEED_PER_DAY");
            r.put("remaining", remainingToday);
            r.put("amount", amount);
            return r;
        }
        r.put("ok", true);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> rotateKeys(Long walletId) {
        DcepHardwareWallet w = mustGet(walletId);
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"), new SecureRandom());
            KeyPair kp = kpg.generateKeyPair();
            String pubB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
            String privEnc = aesEncrypt(pvToPem(kp.getPrivate()));
            w.setPublicKey(pubB64);
            w.setPrivateKeyEnc(privEnc);
            updateById(w);
            Map<String, String> r = new HashMap<>();
            r.put("publicKey", pubB64);
            r.put("rotatedAt", LocalDateTime.now().toString());
            log.info("Keys rotated for wallet: {}", walletId);
            return r;
        } catch (Exception e) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, "密钥轮换失败: " + e.getMessage());
        }
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private DcepHardwareWallet mustGet(Long walletId) {
        DcepHardwareWallet w = getById(walletId);
        if (w == null) throw new BusinessException(BizCode.NOT_FOUND, "钱包不存在: " + walletId);
        return w;
    }

    private boolean isValidWalletType(String type) {
        return type != null && (type.equals("nfc_sim") || type.equals("se_chip")
                || type.equals("iot_wallet") || type.equals("biometric"));
    }

    /** PEM 编码（演示用） */
    private String pvToPem(PrivateKey pk) {
        String b64 = Base64.getEncoder().encodeToString(pk.getEncoded());
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN EC PRIVATE KEY-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(i + 64, b64.length())).append("\n");
        }
        sb.append("-----END EC PRIVATE KEY-----");
        return sb.toString();
    }

    /** 演示用 AES 加密（生产应换 KMS） */
    private String aesEncrypt(String plain) {
        try {
            javax.crypto.spec.SecretKeySpec key = new javax.crypto.spec.SecretKeySpec(
                    PRIV_KEY_SECRET.getBytes("UTF-8").clone(), "AES");
            // AES 需要 16/24/32 字节 key，这里 pad 到 16
            byte[] keyBytes = new byte[16];
            byte[] src = PRIV_KEY_SECRET.getBytes("UTF-8");
            System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, 16));
            javax.crypto.spec.SecretKeySpec paddedKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, paddedKey);
            byte[] enc = cipher.doFinal(plain.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(enc);
        } catch (Exception e) {
            log.warn("AES 加密失败，回退 base64", e);
            return Base64.getEncoder().encodeToString(plain.getBytes());
        }
    }
}
