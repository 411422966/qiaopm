package com.meiyetec.dcep.hardware;

import java.math.BigDecimal;

/** NFC 读卡器读取的余额结果 */
public class NfcBalance {
    public BigDecimal onlineBalance;
    public BigDecimal offlineBalance;
    public String currency = "CNY";
    public Long lastSyncEpochMs;

    public NfcBalance() {}
    public NfcBalance(BigDecimal online, BigDecimal offline) {
        this.onlineBalance = online;
        this.offlineBalance = offline;
        this.lastSyncEpochMs = System.currentTimeMillis();
    }
}
