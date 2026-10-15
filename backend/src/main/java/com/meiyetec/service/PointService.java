package com.meiyetec.service;

import java.util.Map;

public interface PointService {
    
    Map<String, Object> getMemberPoints(Long memberId);
    
    Map<String, Object> getPointRecords(Long memberId, int pageNum, int pageSize);
    
    void addPoints(Long memberId, String source, Integer points, Long refId, String remark);

    /**
     * T06: 与 addPoints 行为一致，但返回新增的 point_record.id（退款冲正场景需要）。
     * addPoints 签名保留不动（避免影响 PointController 等 4 个调用方）。
     * 失败 / 资金异常时返回 null，调用方应据此决定 logReverse ref_id 策略。
     */
    Long addPointsReturningId(Long memberId, String source, Integer points, Long refId, String remark);

    void consumePoints(Long memberId, String source, Integer points, Long refId, String remark);
    
    void processConsumePoints(Long memberId, Long orderId, Integer amount);
    
    void processRechargePoints(Long memberId, Long transactionId, Integer amount);
}
