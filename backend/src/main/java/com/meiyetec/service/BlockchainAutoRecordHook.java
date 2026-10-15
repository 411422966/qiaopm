package com.meiyetec.service;

import com.meiyetec.entity.BlockchainRecord;
import com.meiyetec.security.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class BlockchainAutoRecordHook {

    @Autowired
    private BlockchainBusinessService blockchainService;

    private void safeRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("[Blockchain-Hook] 自动存证失败: {}", e.getMessage());
        }
    }

    public void afterOrderCreated(Long orderId, String orderNo, BigDecimal amount, String paymentMethod) {
        safeRun(() -> {
            BlockchainRecord rec = blockchainService.recordOrder(orderId, orderNo, amount, paymentMethod, currentOperator());
            log.info("[Hook] 订单已上链 orderNo={} blockIndex={}", orderNo, rec.getBlockIndex());
        });
    }

    public void afterOrderPaid(Long orderId, String orderNo, BigDecimal amount) {
        safeRun(() -> blockchainService.recordOrder(orderId, orderNo, amount, "PAID", currentOperator()));
    }

    public void afterProductReceived(Long productId, String productNo, String productName,
                                     String batchNo, String manufacturer, BigDecimal price) {
        safeRun(() -> blockchainService.recordProduct(productId, productNo, productName, batchNo, manufacturer, price, currentOperator()));
    }

    public void afterDrugInbound(Long drugId, String drugNo, String drugName, String batchNo,
                                 String approvalNo, String expiryDate) {
        safeRun(() -> blockchainService.recordDrug(drugId, drugNo, drugName, batchNo, approvalNo, expiryDate, currentOperator()));
    }

    public void afterDeviceInstalled(Long deviceId, String deviceNo, String deviceName,
                                     String registrationNo, String serialNo) {
        safeRun(() -> blockchainService.recordDevice(deviceId, deviceNo, deviceName, registrationNo, serialNo, currentOperator()));
    }

    public void afterConsentSigned(Long consentId, String consentNo, String consentType,
                                   Long memberId, String memberName) {
        safeRun(() -> blockchainService.recordConsent(consentId, consentNo, consentType, memberId, memberName, currentOperator()));
    }

    private String currentOperator() {
        try {
            return TenantContextHolder.get().getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}
