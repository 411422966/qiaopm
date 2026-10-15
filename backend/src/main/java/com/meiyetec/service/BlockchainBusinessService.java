package com.meiyetec.service;

import com.meiyetec.entity.BlockchainRecord;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface BlockchainBusinessService {

    BlockchainRecord recordProduct(Long productId, String productNo, String productName,
                                   String batchNo, String manufacturer, BigDecimal price,
                                   String operatorName);

    BlockchainRecord recordDrug(Long drugId, String drugNo, String drugName,
                                String batchNo, String approvalNo, String expiryDate,
                                String operatorName);

    BlockchainRecord recordDevice(Long deviceId, String deviceNo, String deviceName,
                                  String registrationNo, String serialNo, String operatorName);

    BlockchainRecord recordOrder(Long orderId, String orderNo, BigDecimal amount,
                                 String paymentMethod, String operatorName);

    BlockchainRecord recordConsent(Long consentId, String consentNo, String consentType,
                                   Long memberId, String memberName, String operatorName);

    BlockchainRecord record(String recordType, Long bizId, String bizNo, String payload,
                            String chainId, Integer difficulty, String operatorName);

    Map<String, Object> verifyChain(String chainId);

    Map<String, Object> chainInfo(String chainId);

    Map<String, Object> trace(String recordType, Long bizId);

    BlockchainRecord getRecord(String recordType, Long bizId);

    Map<String, Object> pageRecords(int pageNum, int pageSize, String recordType);

    String sha256Hex(String input);

    List<Map<String, Object>> chainStats();

    List<Map<String, Object>> recordTypeStats();

    List<Map<String, Object>> dailyTrend(int days);

    Map<String, Object> overview();
}
