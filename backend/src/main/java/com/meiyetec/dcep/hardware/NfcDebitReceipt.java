package com.meiyetec.dcep.hardware;

import java.math.BigDecimal;

/** NFC 离线扣款回执 */
public class NfcDebitReceipt {
    public String txNo;                  // 离线交易号
    public String offlineCert;            // 离线凭证（用于上行同步）
    public String walletSignature;        // 钱包侧签名
    public String merchantSignature;      // 商户侧签名
    public BigDecimal offlineBalance;     // 扣后离线余额
    public Long timestamp;

    public NfcDebitReceipt() {}
    public NfcDebitReceipt(String txNo, String cert, String walletSig, String merchantSig, BigDecimal offlineBalance) {
        this.txNo = txNo;
        this.offlineCert = cert;
        this.walletSignature = walletSig;
        this.merchantSignature = merchantSig;
        this.offlineBalance = offlineBalance;
        this.timestamp = System.currentTimeMillis();
    }
}
