package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.DcepContractExecutionLog;
import com.meiyetec.entity.DcepSmartContract;
import com.meiyetec.mapper.DcepContractExecutionLogMapper;
import com.meiyetec.mapper.DcepSmartContractMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.DcepSmartContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 鏅鸿兘鍚堢害鐪熷疄瀹炵幇锛圔C-003锛?
 * 鏀寔锛氶浠樺崱璧勯噾鐩戠 / 闄愭椂鍌ㄥ€?/ 鏉′欢瑙﹀彂浼樻儬鍒?
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DcepSmartContractServiceImpl extends ServiceImpl<DcepSmartContractMapper, DcepSmartContract>
        implements DcepSmartContractService {

    private final DcepSmartContractMapper contractMapper;
    private final DcepContractExecutionLogMapper logMapper;

    @Override
    public List<DcepSmartContract> queryList(String contractType, String status, Long walletId) {
        return contractMapper.queryList(contractType, status, walletId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcepSmartContract deployContract(String contractName, String contractType,
                                            Long walletId, Long memberId,
                                            BigDecimal escrowAmount, String triggerConditions,
                                            LocalDateTime expiredTime) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        DcepSmartContract c = new DcepSmartContract();
        c.setTenantId(tenantId);
        c.setContractName(contractName);
        c.setContractType(contractType);
        c.setWalletId(walletId);
        c.setMemberId(memberId);
        c.setEscrowAmount(escrowAmount);
        c.setReleasedAmount(BigDecimal.ZERO);
        c.setUsedAmount(BigDecimal.ZERO);
        c.setRefundedAmount(BigDecimal.ZERO);
        c.setCurrency("CNY");
        c.setTriggerConditions(triggerConditions);
        c.setStatus("active");
        c.setActivatedTime(LocalDateTime.now());
        c.setExpiredTime(expiredTime);
        c.setAutoExecuteCount(0);
        c.setManualExecuteCount(0);
        c.setContractAddress("0x" + UUID.randomUUID().toString().replace("-", "").substring(0, 40));
        c.setContractCodeHash(sha256(contractType + "|" + contractName + "|" + System.currentTimeMillis()));
        contractMapper.insert(c);

        logDeploy(c.getId(), escrowAmount);
        return c;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void triggerContract(Long contractId, String action) {
        DcepSmartContract c = contractMapper.selectById(contractId);
        if (c == null) throw new RuntimeException("鍚堢害涓嶅瓨鍦? " + contractId);
        if (!"active".equals(c.getStatus())) throw new RuntimeException("鍚堢害鐘舵€侀潪 active: " + c.getStatus());

        switch (action) {
            case "release" -> {
                BigDecimal remain = c.getEscrowAmount().subtract(c.getReleasedAmount());
                if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                    logSkip(contractId, action, "no remaining escrow");
                    return;
                }
                c.setReleasedAmount(c.getEscrowAmount());
                c.setManualExecuteCount(c.getManualExecuteCount() + 1);
                c.setEventLog("RELEASE@" + LocalDateTime.now() + "|manual");
                logSuccess(contractId, action, c.getEscrowAmount(), "released to wallet");
            }
            case "refund" -> {
                BigDecimal remain = c.getEscrowAmount().subtract(c.getReleasedAmount());
                if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                    logSkip(contractId, action, "no remaining to refund");
                    return;
                }
                c.setRefundedAmount(remain);
                c.setReleasedAmount(BigDecimal.ZERO);
                c.setManualExecuteCount(c.getManualExecuteCount() + 1);
                c.setStatus("cancelled");
                c.setEventLog("REFUND@" + LocalDateTime.now() + "|manual");
                logSuccess(contractId, action, remain, "refunded");
            }
            case "coupon_grant" -> {
                c.setManualExecuteCount(c.getManualExecuteCount() + 1);
                c.setEventLog("COUPON@" + LocalDateTime.now() + "|manual");
                logSuccess(contractId, action, null, "coupon granted");
            }
            case "cancel" -> {
                c.setStatus("cancelled");
                c.setManualExecuteCount(c.getManualExecuteCount() + 1);
                c.setEventLog("CANCEL@" + LocalDateTime.now() + "|manual");
                logSuccess(contractId, action, null, "contract cancelled");
            }
            default -> throw new RuntimeException("鏈煡 action: " + action);
        }
        contractMapper.updateById(c);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoExecuteExpired() {
        List<DcepSmartContract> expired = contractMapper.findExpiredActive();
        int count = 0;
        for (DcepSmartContract c : expired) {
            BigDecimal remain = c.getEscrowAmount().subtract(c.getReleasedAmount());
            if (remain.compareTo(BigDecimal.ZERO) > 0) {
                c.setRefundedAmount(remain);
                c.setReleasedAmount(BigDecimal.ZERO);
            }
            c.setStatus("expired");
            c.setCompletedTime(LocalDateTime.now());
            c.setAutoExecuteCount(c.getAutoExecuteCount() + 1);
            c.setEventLog("EXPIRE@" + LocalDateTime.now() + "|auto");
            contractMapper.updateById(c);
            logSuccess(c.getId(), "auto_expire", remain, "auto expired");
            count++;
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundPrepaid(Long contractId, BigDecimal amount) {
        DcepSmartContract c = contractMapper.selectById(contractId);
        if (c == null) throw new RuntimeException("鍚堢害涓嶅瓨鍦? " + contractId);
        c.setRefundedAmount(c.getRefundedAmount().add(amount));
        c.setEventLog("PARTIAL_REFUND@" + LocalDateTime.now() + "|amount=" + amount);
        contractMapper.updateById(c);
        logSuccess(contractId, "partial_refund", amount, "partial refund");
    }

    private void logDeploy(Long contractId, BigDecimal escrowAmount) {
        DcepContractExecutionLog l = new DcepContractExecutionLog();
        l.setContractId(contractId);
        l.setAction("deploy");
        l.setExecuteType("system");
        l.setAmount(escrowAmount);
        l.setStatus("success");
        l.setResult("success");
        l.setTriggerSnapshot("contract deployed");
        l.setExecuteTime(LocalDateTime.now());
        logMapper.insert(l);
    }

    private void logSuccess(Long contractId, String action, BigDecimal amount, String detail) {
        DcepContractExecutionLog l = new DcepContractExecutionLog();
        l.setContractId(contractId);
        l.setAction(action);
        l.setExecuteType("manual");
        l.setAmount(amount);
        l.setStatus("success");
        l.setResult("success");
        l.setTriggerSnapshot(detail);
        l.setExecuteTime(LocalDateTime.now());
        logMapper.insert(l);
    }

    private void logSkip(Long contractId, String action, String reason) {
        DcepContractExecutionLog l = new DcepContractExecutionLog();
        l.setContractId(contractId);
        l.setAction(action);
        l.setExecuteType("manual");
        l.setStatus("skipped");
        l.setResult("skipped");
        l.setTriggerSnapshot(reason);
        l.setExecuteTime(LocalDateTime.now());
        logMapper.insert(l);
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
