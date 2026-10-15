package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.DcepSubWallet;
import com.meiyetec.entity.DcepSubWalletLedger;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface DcepSubWalletService extends IService<DcepSubWallet> {

    /**
     * 查询子钱包列表（支持多维筛选）
     */
    List<DcepSubWallet> queryList(Long tenantId, Long storeId, Long parentWalletId,
                                   String walletType, String status);

    /**
     * 按编号查询
     */
    DcepSubWallet findByCode(String code);

    /**
     * 创建子钱包
     */
    DcepSubWallet createSubWallet(Long tenantId, Long storeId, Long parentWalletId,
                                   String parentWalletType, String subWalletName, String walletType,
                                   BigDecimal maxBalance, BigDecimal minBalance,
                                   BigDecimal transferInRatio, BigDecimal transferOutRatio,
                                   Integer autoSweepEnabled, BigDecimal sweepThreshold,
                                   Long sweepTargetId, String settlementMode, String tags);

    /**
     * 更新子钱包配置（限额/手续费/自动归集）
     */
    DcepSubWallet updateConfig(Long id, BigDecimal maxBalance, BigDecimal minBalance,
                                BigDecimal transferInRatio, BigDecimal transferOutRatio,
                                Integer autoSweepEnabled, BigDecimal sweepThreshold,
                                Long sweepTargetId, String settlementMode, String tags, String remark);

    /**
     * 冻结子钱包
     */
    DcepSubWallet freeze(Long id, String reason);

    /**
     * 解冻子钱包
     */
    DcepSubWallet unfreeze(Long id);

    /**
     * 关闭子钱包（余额必须为0）
     */
    DcepSubWallet close(Long id);

    /**
     * 账间转账（两子钱包之间）
     * @return 转账流水对 {fromLedger, toLedger}
     */
    Map<String, DcepSubWalletLedger> transfer(Long fromId, Long toId, BigDecimal amount,
                                               BigDecimal fee, String orderNo, Long operatorId,
                                               String operatorName, String remark);

    /**
     * 查询账本流水
     */
    List<DcepSubWalletLedger> queryLedger(Long subWalletId, String txType,
                                           String startTime, String endTime, int limit);

    /**
     * 获取子钱包统计（余额/累计/冻结/可用）
     */
    Map<String, Object> getStats(Long id);

    /**
     * 执行自动归集（定时任务或触发）
     * @return 归集笔数
     */
    int autoSweep(Long parentWalletId);
}
