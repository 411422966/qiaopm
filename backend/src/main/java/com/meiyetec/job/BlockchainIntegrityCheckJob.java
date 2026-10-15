package com.meiyetec.job;

import com.meiyetec.config.BlockchainChainConfig;
import com.meiyetec.service.BlockchainBusinessService;
import com.meiyetec.service.impl.BlockchainBusinessServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "meiyetec.blockchain.integrity-check-enabled", havingValue = "true", matchIfMissing = true)
public class BlockchainIntegrityCheckJob {

    private final BlockchainBusinessService blockchainService;
    private final BlockchainChainConfig chainConfig;

    @Scheduled(fixedRate = 21600000, initialDelay = 60000)
    public void runIntegrityCheck() {
        log.info("[BlockchainIntegrityCheckJob] 开始链完整性校验...");
        String[] chains = {
                BlockchainBusinessServiceImpl.CHAIN_MAIN,
                BlockchainBusinessServiceImpl.CHAIN_COSMETIC,
                BlockchainBusinessServiceImpl.CHAIN_DRUG,
                BlockchainBusinessServiceImpl.CHAIN_DEVICE
        };
        for (String chainId : chains) {
            checkChain(chainId);
        }
        log.info("[BlockchainIntegrityCheckJob] 链完整性校验完成");
    }

    private void checkChain(String chainId) {
        try {
            Map<String, Object> result = blockchainService.verifyChain(chainId);
            Boolean valid = (Boolean) result.get("valid");
            int blockCount = (Integer) result.get("blockCount");
            if (Boolean.TRUE.equals(valid)) {
                log.info("[BlockchainIntegrityCheckJob] 链 {} 完整（{} 块）", chainId, blockCount);
            } else {
                @SuppressWarnings("unchecked")
                var broken = (java.util.List<String>) result.get("broken");
                log.error("[BlockchainIntegrityCheckJob] 链 {} 完整性校验失败！损坏点：{}",
                        chainId, broken != null ? String.join(", ", broken) : "未知");
            }
        } catch (Exception e) {
            log.error("[BlockchainIntegrityCheckJob] 链 {} 校验异常", chainId, e);
        }
    }
}
