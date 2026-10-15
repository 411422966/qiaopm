package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.DcepHardwareWallet;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface DcepHardwareWalletService extends IService<DcepHardwareWallet> {
    List<DcepHardwareWallet> queryList(Long memberId, String walletType, String status, Long tenantId);
    DcepHardwareWallet findByCode(String walletCode);

    /**
     * 激活硬钱包（DCEP-01：自动生成 ECDSA 密钥对）
     */
    DcepHardwareWallet activateWallet(Long memberId, String walletType, String deviceModel, String seChipId, Long tenantId);

    /**
     * 充值
     */
    DcepHardwareWallet recharge(Long walletId, BigDecimal amount);

    /**
     * 离线交易扣减
     */
    boolean deductOffline(Long walletId, BigDecimal amount);

    /**
     * 上行同步(将离线扣减写入在线余额)
     */
    void syncOfflineTransaction(Long walletId, BigDecimal offlineDeductAmount);

    // ============================================================
    // DCEP-01 硬钱包 NFC/SE 真机对接新增能力
    // ============================================================

    /**
     * 钱包生命周期：挂失（active → lost）
     */
    DcepHardwareWallet reportLost(Long walletId, String reason);

    /**
     * 钱包生命周期：解挂（lost → active），需校验 PIN
     */
    DcepHardwareWallet unfreezeLost(Long walletId, String pin);

    /**
     * 钱包生命周期：冻结（active → frozen，运营/风控用）
     */
    DcepHardwareWallet freeze(Long walletId, String reason);

    /**
     * 钱包生命周期：解冻（frozen → active）
     */
    DcepHardwareWallet unfreeze(Long walletId);

    /**
     * 钱包生命周期：销户（active → expired，清空余额）
     */
    DcepHardwareWallet closeAccount(Long walletId);

    /**
     * 设置 PIN（首次或修改）
     */
    void setPin(Long walletId, String pin);

    /**
     * 验证 PIN
     * @return true 校验通过
     */
    boolean verifyPin(Long walletId, String pin);

    /**
     * 启用/禁用生物识别
     */
    DcepHardwareWallet toggleBiometric(Long walletId, boolean enabled);

    /**
     * 限额校验（DCEP-01 单笔/单日）
     * @return null=通过；否则返回超限信息 {reason, remaining}
     */
    Map<String, Object> checkLimit(Long walletId, BigDecimal amount);

    /**
     * 重新生成 ECDSA 密钥对（私钥 AES 加密落库）
     */
    Map<String, String> rotateKeys(Long walletId);
}
