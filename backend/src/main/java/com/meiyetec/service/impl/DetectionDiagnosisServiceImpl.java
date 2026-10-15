package com.meiyetec.service.impl;

import com.meiyetec.entity.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 诊断引擎 - 基于规则的智能诊断
 *
 * 肤质综合判断逻辑：
 * 1. 水分<40 → 干性倾向
 * 2. 油脂>60 → 油性倾向
 * 3. 敏感度>50 → 敏感
 * 4. 综合分<90 → 亚健康
 *
 * 发质综合判断：
 * 1. 水分<40 → 干性
 * 2. 油脂>60 → 油性
 * 3. 损伤>50 → 受损
 * 4. 断裂风险>40 → 易断
 *
 * 甲面综合判断：
 * 1. 硬度<50 → 偏软
 * 2. 韧性<50 → 易裂
 * 3. 表面平整度<60 → 凹凸不平
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionDiagnosisServiceImpl implements DetectionDiagnosisService {

    private final SkinDetectionService skinDetectionService;
    private final HairDetectionService hairDetectionService;
    private final NailDetectionService nailDetectionService;

    @Override
    public String diagnoseSkin(SkinDetection d) {
        if (d == null) return "暂无数据";
        StringBuilder sb = new StringBuilder();
        BigDecimal moisture = d.getMoistureScore() == null ? BigDecimal.ZERO : d.getMoistureScore();
        BigDecimal oil = d.getOilScore() == null ? BigDecimal.ZERO : d.getOilScore();
        BigDecimal sensitivity = d.getSensitivityScore() == null ? BigDecimal.ZERO : d.getSensitivityScore();
        BigDecimal overall = d.getOverallScore() == null ? BigDecimal.ZERO : d.getOverallScore();
        // 肤质类型判断
        if (sensitivity.compareTo(BigDecimal.valueOf(50)) > 0) {
            sb.append("敏感肌肤,");
        }
        if (moisture.compareTo(BigDecimal.valueOf(40)) < 0) {
            sb.append("水分不足,");
        }
        if (oil.compareTo(BigDecimal.valueOf(60)) > 0) {
            sb.append("油脂分泌旺盛,");
        }
        if (moisture.compareTo(BigDecimal.valueOf(40)) >= 0 && oil.compareTo(BigDecimal.valueOf(60)) <= 0) {
            sb.append("水油平衡");
        }
        // 综合评价
        if (overall.compareTo(BigDecimal.valueOf(90)) >= 0) {
            sb.append(",整体健康");
        } else if (overall.compareTo(BigDecimal.valueOf(70)) >= 0) {
            sb.append(",整体良好,需持续保养");
        } else if (overall.compareTo(BigDecimal.valueOf(50)) >= 0) {
            sb.append(",存在明显问题,需要改善");
        } else {
            sb.append(",问题严重,需立即干预");
        }
        return sb.length() == 0 ? "数据不足,无法诊断" : sb.toString();
    }

    @Override
    public String diagnoseHair(HairDetection d) {
        if (d == null) return "暂无数据";
        StringBuilder sb = new StringBuilder();
        BigDecimal moisture = d.getMoistureScore() == null ? BigDecimal.ZERO : d.getMoistureScore();
        BigDecimal oil = d.getOilScore() == null ? BigDecimal.ZERO : d.getOilScore();
        BigDecimal damage = d.getDamageScore() == null ? BigDecimal.ZERO : d.getDamageScore();
        BigDecimal breakage = d.getBreakageRisk() == null ? BigDecimal.ZERO : d.getBreakageRisk();
        BigDecimal loss = d.getHairLossRisk() == null ? BigDecimal.ZERO : d.getHairLossRisk();
        if (moisture.compareTo(BigDecimal.valueOf(40)) < 0) sb.append("头发缺水,");
        if (oil.compareTo(BigDecimal.valueOf(60)) > 0) sb.append("头皮出油,");
        if (damage.compareTo(BigDecimal.valueOf(50)) > 0) sb.append("发质受损,");
        if (breakage.compareTo(BigDecimal.valueOf(40)) > 0) sb.append("易断裂,");
        if (loss.compareTo(BigDecimal.valueOf(40)) > 0) sb.append("脱发风险,");
        if (d.getDandruffLevel() != null && d.getDandruffLevel() >= 2) sb.append("头皮屑较多,");
        if (sb.length() == 0) sb.append("发质整体健康");
        return sb.toString();
    }

    @Override
    public String diagnoseNail(NailDetection d) {
        if (d == null) return "暂无数据";
        StringBuilder sb = new StringBuilder();
        BigDecimal hardness = d.getHardnessScore() == null ? BigDecimal.ZERO : d.getHardnessScore();
        BigDecimal toughness = d.getToughnessScore() == null ? BigDecimal.ZERO : d.getToughnessScore();
        BigDecimal smoothness = d.getSurfaceSmoothness() == null ? BigDecimal.ZERO : d.getSurfaceSmoothness();
        BigDecimal periungual = d.getPeriungualScore() == null ? BigDecimal.ZERO : d.getPeriungualScore();
        BigDecimal paronychia = d.getParonychiaRisk() == null ? BigDecimal.ZERO : d.getParonychiaRisk();
        if (hardness.compareTo(BigDecimal.valueOf(50)) < 0) sb.append("指甲偏软,");
        if (toughness.compareTo(BigDecimal.valueOf(50)) < 0) sb.append("韧性差,");
        if (smoothness.compareTo(BigDecimal.valueOf(60)) < 0) sb.append("表面不平整,");
        if (periungual.compareTo(BigDecimal.valueOf(60)) < 0) sb.append("甲周皮肤粗糙,");
        if (paronychia.compareTo(BigDecimal.valueOf(30)) > 0) sb.append("甲沟炎风险,");
        if (sb.length() == 0) sb.append("指甲整体健康");
        return sb.toString();
    }

    @Override
    public List<String> diagnoseAll(Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<String> results = new ArrayList<>();
        SkinDetection skin = skinDetectionService.findLatestByMember(tenantId, memberId);
        HairDetection hair = hairDetectionService.findLatestByMember(tenantId, memberId);
        NailDetection nail = nailDetectionService.findLatestByMember(tenantId, memberId);
        if (skin != null) results.add("肤质:" + diagnoseSkin(skin));
        if (hair != null) results.add("发质:" + diagnoseHair(hair));
        if (nail != null) results.add("甲面:" + diagnoseNail(nail));
        return results;
    }

    @Override
    public List<String> identifyConcerns(SkinDetection d) {
        List<String> concerns = new ArrayList<>();
        if (d == null) return concerns;
        if (d.getAcneLevel() != null && d.getAcneLevel() >= 1) concerns.add("痘痘");
        if (d.getDarkCircleLevel() != null && d.getDarkCircleLevel() >= 1) concerns.add("黑眼圈");
        if (d.getSpotCount() != null && d.getSpotCount() > 5) concerns.add("色斑");
        if (d.getFineLineCount() != null && d.getFineLineCount() > 3) concerns.add("细纹");
        if (d.getSensitivityScore() != null && d.getSensitivityScore().compareTo(BigDecimal.valueOf(50)) > 0) concerns.add("敏感");
        if (d.getPigmentationScore() != null && d.getPigmentationScore().compareTo(BigDecimal.valueOf(60)) > 0) concerns.add("色素沉着");
        if (d.getPoreScore() != null && d.getPoreScore().compareTo(BigDecimal.valueOf(60)) > 0) concerns.add("毛孔粗大");
        if (d.getMoistureScore() != null && d.getMoistureScore().compareTo(BigDecimal.valueOf(40)) < 0) concerns.add("缺水");
        return concerns;
    }

    @Override
    public List<String> identifyConcerns(HairDetection d) {
        List<String> concerns = new ArrayList<>();
        if (d == null) return concerns;
        if (d.getHairLossRisk() != null && d.getHairLossRisk().compareTo(BigDecimal.valueOf(40)) > 0) concerns.add("脱发");
        if (d.getDandruffLevel() != null && d.getDandruffLevel() >= 1) concerns.add("头屑");
        if (d.getDamageScore() != null && d.getDamageScore().compareTo(BigDecimal.valueOf(50)) > 0) concerns.add("受损");
        if (d.getScalpSensitivity() != null && d.getScalpSensitivity().compareTo(BigDecimal.valueOf(50)) > 0) concerns.add("头皮敏感");
        if (d.getSplitEndScore() != null && d.getSplitEndScore().compareTo(BigDecimal.valueOf(40)) > 0) concerns.add("分叉");
        if (d.getMoistureScore() != null && d.getMoistureScore().compareTo(BigDecimal.valueOf(40)) < 0) concerns.add("干燥");
        return concerns;
    }

    @Override
    public List<String> identifyConcerns(NailDetection d) {
        List<String> concerns = new ArrayList<>();
        if (d == null) return concerns;
        if (d.getVerticalCracks() != null && d.getVerticalCracks() > 0) concerns.add("纵裂");
        if (d.getHorizontalRidges() != null && d.getHorizontalRidges() > 0) concerns.add("横纹");
        if (d.getHangnailCount() != null && d.getHangnailCount() > 0) concerns.add("倒刺");
        if (d.getFungalRisk() != null && d.getFungalRisk().compareTo(BigDecimal.valueOf(30)) > 0) concerns.add("真菌风险");
        if (d.getParonychiaRisk() != null && d.getParonychiaRisk().compareTo(BigDecimal.valueOf(30)) > 0) concerns.add("甲沟炎风险");
        if (d.getBreakHistory() != null && d.getBreakHistory() > 2) concerns.add("频繁断裂");
        return concerns;
    }
}
