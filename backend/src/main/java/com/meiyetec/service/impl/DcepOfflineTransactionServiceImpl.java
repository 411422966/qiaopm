package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.*;
import com.meiyetec.mapper.*;
import com.meiyetec.service.DcepHardwareWalletService;
import com.meiyetec.service.DcepOfflineTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DcepOfflineTransactionServiceImpl extends ServiceImpl<DcepOfflineTransactionMapper, DcepOfflineTransaction>
        implements DcepOfflineTransactionService {

    private final DcepOfflineTransactionMapper txMapper;
    private final DcepHardwareWalletMapper walletMapper;
    private final DcepAntiDoubleSpendProofMapper proofMapper;
    private final DcepHardwareWalletService walletService;

    @Override
    public List<DcepOfflineTransaction> queryList(Long walletId, String syncStatus, String merchantId, Long tenantId) {
        return txMapper.queryList(walletId, syncStatus, merchantId, tenantId);
    }

    @Override
    public DcepOfflineTransaction findByOfflineTxNo(String offlineTxNo) {
        return txMapper.findByTxNo(offlineTxNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepOfflineTransaction createOfflineTransaction(Long walletId, String merchantId, String merchantName,
                                                          BigDecimal amount, String txType, boolean dualOffline, Long tenantId) {
        DcepHardwareWallet wallet = walletMapper.findByCode(walletId.toString());
        if (wallet == null) {
            wallet = walletMapper.selectById(walletId);
        }
        if (wallet == null) throw new RuntimeException("Wallet not found");
        if (!"nfc_sim".equals(wallet.getWalletType()) && !"se_chip".equals(wallet.getWalletType())) {
            throw new RuntimeException("Wallet does not support offline: " + wallet.getWalletType());
        }
        if (wallet.getOfflineBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Offline balance insufficient");
        }
        DcepAntiDoubleSpendProof proof = new DcepAntiDoubleSpendProof();
        proof.setWalletId(walletId);
        proof.setWalletCode(wallet.getWalletCode());
        proof.setNonce(System.currentTimeMillis());
        proof.setMerkleRoot(UUID.randomUUID().toString().replace("-", ""));
        proof.setDualSignature(UUID.randomUUID().toString().replace("-", "") + "|" +
                UUID.randomUUID().toString().replace("-", ""));
        proof.setWalletProof("WALLET_PROOF_" + System.currentTimeMillis());
        proof.setMerchantProof("MERCHANT_PROOF_" + System.currentTimeMillis());
        proof.setTimestamp(LocalDateTime.now());
        proof.setStatus("pending");
        proofMapper.insert(proof);

        DcepOfflineTransaction tx = new DcepOfflineTransaction();
        tx.setTenantId(tenantId);
        tx.setWalletId(walletId);
        tx.setWalletCode(wallet.getWalletCode());
        tx.setOfflineTxNo("OFF-" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        tx.setOfflineCert(proof.getMerkleRoot());
        tx.setOfflineSignature(proof.getDualSignature());
        tx.setAmount(amount);
        tx.setOfflineBalance(wallet.getOfflineBalance().subtract(amount));
        tx.setTxType(txType);
        tx.setMerchantId(merchantId);
        tx.setMerchantName(merchantName);
        tx.setDualOffline(dualOffline ? 1 : 0);
        tx.setSyncStatus("pending");
        tx.setAntiDoubleSpendProof(proof.getMerkleRoot());
        tx.setOfflineTxTime(LocalDateTime.now());
        tx.setSyncAttempts(0);
        tx.setConflictDetected(0);
        save(tx);

        wallet.setOfflineBalance(tx.getOfflineBalance());
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setTotalConsumption(wallet.getTotalConsumption().add(amount));
        wallet.setLastSyncTime(LocalDateTime.now());
        walletMapper.updateById(wallet);
        log.info("Offline tx created: txNo={} amount={} wallet={}", tx.getOfflineTxNo(), amount, walletId);
        return tx;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepOfflineTransaction syncTransaction(String offlineTxNo) {
        DcepOfflineTransaction tx = txMapper.findByTxNo(offlineTxNo);
        if (tx == null) throw new RuntimeException("Transaction not found");
        if (!"pending".equals(tx.getSyncStatus())) {
            log.warn("Tx already synced: {}", offlineTxNo);
            return tx;
        }
        try {
            boolean doubleSpend = detectDoubleSpend(tx.getWalletId(), tx.getOfflineCert());
            if (doubleSpend) {
                tx.setConflictDetected(1);
                tx.setConflictType("double_spend");
                tx.setSyncStatus("disputed");
                log.error("Double spend detected: {}", offlineTxNo);
                updateById(tx);
                return tx;
            }
            tx.setSyncStatus("synced");
            tx.setSyncTime(LocalDateTime.now());
            tx.setSyncAttempts(tx.getSyncAttempts() + 1);
            updateById(tx);
            log.info("Offline tx synced: {}", offlineTxNo);
            return tx;
        } catch (Exception e) {
            tx.setSyncStatus("failed");
            tx.setSyncAttempts(tx.getSyncAttempts() + 1);
            updateById(tx);
            log.error("Sync failed: {}", offlineTxNo, e);
            return tx;
        }
    }

    @Override
    public boolean detectDoubleSpend(Long walletId, String offlineCert) {
        if (offlineCert == null) return false;
        List<DcepOfflineTransaction> txs = txMapper.queryList(walletId, null, null, null);
        long sameCertCount = txs.stream()
            .filter(t -> offlineCert.equals(t.getOfflineCert()))
            .count();
        return sameCertCount > 1;
    }
}
