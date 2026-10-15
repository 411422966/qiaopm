package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.DcepOfflineTransaction;
import java.util.List;
public interface DcepOfflineTransactionService extends IService<DcepOfflineTransaction> {
    List<DcepOfflineTransaction> queryList(Long walletId, String syncStatus, String merchantId, Long tenantId);
    DcepOfflineTransaction findByOfflineTxNo(String offlineTxNo);
    /**
     * 创建双离线交易
     */
    DcepOfflineTransaction createOfflineTransaction(Long walletId, String merchantId, String merchantName,
                                                    java.math.BigDecimal amount, String txType, boolean dualOffline, Long tenantId);
    /**
     * 同步离线交易
     */
    DcepOfflineTransaction syncTransaction(String offlineTxNo);
    /**
     * 防双花检测
     */
    boolean detectDoubleSpend(Long walletId, String offlineCert);
}