package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.*;
import com.meiyetec.mapper.DetectionReportMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DetectionReportServiceImpl extends ServiceImpl<DetectionReportMapper, DetectionReport>
        implements DetectionReportService {

    private final SkinDetectionService skinDetectionService;
    private final HairDetectionService hairDetectionService;
    private final NailDetectionService nailDetectionService;
    private final DetectionSolutionService detectionSolutionService;
    private final DetectionDiagnosisService detectionDiagnosisService;

    @Override
    public List<DetectionReport> queryList(Long tenantId, Long memberId, Long storeId, String reportType, String status) {
        return baseMapper.queryList(tenantId, memberId, storeId, reportType, status);
    }

    @Override
    public DetectionReport generateReport(Long tenantId, Long memberId, String reportType) {
        if (memberId == null) throw new com.meiyetec.exception.BusinessException(com.meiyetec.exception.BizCode.BAD_REQUEST, "memberId 必填");
        DetectionReport report = new DetectionReport();
        report.setTenantId(tenantId);
        report.setMemberId(memberId);
        report.setReportType(reportType);
        report.setReportNo("RPT" + System.currentTimeMillis());
        report.setGenerateTime(LocalDateTime.now());
        report.setStatus("draft");
        BigDecimal totalScore = BigDecimal.ZERO;
        int count = 0;
        List<String> allConcerns = new ArrayList<>();
        List<String> homeCareAdvices = new ArrayList<>();
        if ("skin".equals(reportType) || "comprehensive".equals(reportType)) {
            SkinDetection skin = skinDetectionService.findLatestByMember(tenantId, memberId);
            if (skin != null) {
                report.setSkinDetectionId(skin.getId());
                if (skin.getOverallScore() != null) {
                    totalScore = totalScore.add(skin.getOverallScore());
                    count++;
                }
                allConcerns.addAll(detectionDiagnosisService.identifyConcerns(skin));
                String advice = detectionDiagnosisService.diagnoseSkin(skin);
                if (advice != null) report.setOverallAssessment(advice);
                detectHomeCare(skin, "skin", homeCareAdvices);
            }
        }
        if ("hair".equals(reportType) || "comprehensive".equals(reportType)) {
            HairDetection hair = hairDetectionService.findLatestByMember(tenantId, memberId);
            if (hair != null) {
                report.setHairDetectionId(hair.getId());
                if (hair.getOverallScore() != null) {
                    totalScore = totalScore.add(hair.getOverallScore());
                    count++;
                }
                allConcerns.addAll(detectionDiagnosisService.identifyConcerns(hair));
                if (report.getOverallAssessment() == null) {
                    report.setOverallAssessment(detectionDiagnosisService.diagnoseHair(hair));
                }
                detectHomeCare(hair, "hair", homeCareAdvices);
            }
        }
        if ("nail".equals(reportType) || "comprehensive".equals(reportType)) {
            NailDetection nail = nailDetectionService.findLatestByMember(tenantId, memberId);
            if (nail != null) {
                report.setNailDetectionId(nail.getId());
                if (nail.getOverallScore() != null) {
                    totalScore = totalScore.add(nail.getOverallScore());
                    count++;
                }
                allConcerns.addAll(detectionDiagnosisService.identifyConcerns(nail));
                if (report.getOverallAssessment() == null) {
                    report.setOverallAssessment(detectionDiagnosisService.diagnoseNail(nail));
                }
                detectHomeCare(nail, "nail", homeCareAdvices);
            }
        }
        if (count > 0) {
            report.setOverallScore(totalScore.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
        }
        report.setPriorityConcerns(new ArrayList<>(new LinkedHashSet<>(allConcerns)).toString());
        report.setHomeCareAdvice(String.join(";", homeCareAdvices));
        List<DetectionSolution> solutions = detectionSolutionService.matchByScore(tenantId, reportType,
                report.getOverallScore() == null ? 0 : report.getOverallScore().intValue());
        if (!solutions.isEmpty()) {
            DetectionSolution first = solutions.get(0);
            report.setRecommendedServices(first.getRecommendedServices());
            report.setRecommendedProducts(first.getRecommendedProducts());
            report.setTreatmentCycle(first.getTreatmentCycle());
            report.setExpectedOutcome(first.getExpectedOutcome());
        }
        save(report);
        return report;
    }

    private void detectHomeCare(Object detection, String type, List<String> advices) {
    }

    @Override
    public List<Map<String, Object>> statisticsByType(Long tenantId, Long storeId) {
        return baseMapper.statisticsByType(tenantId, storeId);
    }
}