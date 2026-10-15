package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.DetectionReport;
import java.util.List;
import java.util.Map;
public interface DetectionReportService extends IService<DetectionReport> {
    List<DetectionReport> queryList(Long tenantId, Long memberId, Long storeId, String reportType, String status);
    /**
     * 生成报告
     */
    DetectionReport generateReport(Long tenantId, Long memberId, String reportType);
    List<Map<String, Object>> statisticsByType(Long tenantId, Long storeId);
}