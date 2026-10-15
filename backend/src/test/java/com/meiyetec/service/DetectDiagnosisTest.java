package com.meiyetec.service;

import com.meiyetec.entity.HairDetection;
import com.meiyetec.entity.NailDetection;
import com.meiyetec.entity.SkinDetection;
import com.meiyetec.service.impl.DetectionDiagnosisServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DetectionDiagnosisService 纯算法（不依赖 DB）
 */
class DetectDiagnosisTest {

    private final DetectionDiagnosisServiceImpl svc = new DetectionDiagnosisServiceImpl(null, null, null);

    @Test
    void skin_diagnose_high_moisture_balanced_oil_is_healthy() {
        SkinDetection s = new SkinDetection();
        s.setMoistureScore(new BigDecimal("60"));
        s.setOilScore(new BigDecimal("40"));
        s.setSensitivityScore(new BigDecimal("20"));
        s.setOverallScore(new BigDecimal("95"));
        String out = svc.diagnoseSkin(s);
        assertNotNull(out);
        assertTrue(out.contains("水油平衡"), "应判水油平衡, 实际: " + out);
        assertTrue(out.contains("整体健康"), "高分应健康, 实际: " + out);
    }

    @Test
    void skin_diagnose_low_moisture_oily_is_problem() {
        SkinDetection s = new SkinDetection();
        s.setMoistureScore(new BigDecimal("20"));
        s.setOilScore(new BigDecimal("80"));
        s.setSensitivityScore(new BigDecimal("30"));
        s.setOverallScore(new BigDecimal("55"));
        String out = svc.diagnoseSkin(s);
        assertTrue(out.contains("水分不足"));
        assertTrue(out.contains("油脂分泌旺盛"));
        assertTrue(out.contains("需要改善"));
    }

    @Test
    void hair_diagnose_damaged_high_breakage() {
        HairDetection h = new HairDetection();
        h.setMoistureScore(new BigDecimal("30"));
        h.setOilScore(new BigDecimal("70"));
        h.setDamageScore(new BigDecimal("60"));
        h.setBreakageRisk(new BigDecimal("50"));
        h.setHairLossRisk(new BigDecimal("40"));
        String out = svc.diagnoseHair(h);
        assertTrue(out.contains("缺水"));
        assertTrue(out.contains("发质受损"));
        assertTrue(out.contains("易断裂"));
    }

    @Test
    void nail_diagnose_soft_rough() {
        NailDetection n = new NailDetection();
        n.setHardnessScore(new BigDecimal("30"));
        n.setToughnessScore(new BigDecimal("40"));
        n.setSurfaceSmoothness(new BigDecimal("40"));
        n.setPeriungualScore(new BigDecimal("30"));
        String out = svc.diagnoseNail(n);
        assertTrue(out.contains("偏软"));
        assertTrue(out.contains("韧性差"));
        assertTrue(out.contains("表面不平整"));
    }

    @Test
    void identify_skin_concerns() {
        SkinDetection s = new SkinDetection();
        s.setAcneLevel(2);
        s.setSpotCount(10);
        s.setSensitivityScore(new BigDecimal("60"));
        s.setMoistureScore(new BigDecimal("30"));
        var concerns = svc.identifyConcerns(s);
        assertTrue(concerns.contains("痘痘"));
        assertTrue(concerns.contains("色斑"));
        assertTrue(concerns.contains("敏感"));
        assertTrue(concerns.contains("缺水"));
    }
}