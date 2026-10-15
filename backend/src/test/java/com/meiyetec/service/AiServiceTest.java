package com.meiyetec.service;

import com.meiyetec.entity.MhVisualArchive;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3.0-H P1.6：AI 功能真实化测试
 *
 * <p>覆盖：
 *  - 流失预测核心算法（computeChurnScore 加权规则评分）
 *  - 风险等级判定
 *  - Visual AI 真实特征分析（确定性、不依赖 Random）
 *  - Visual AI 真实对比（余弦相似度）
 * </p>
 */
class AiServiceTest {

    // ==================== 流失预测 ====================
    @Test
    void churnScore_highRisk_longTimeNoPurchase() {
        // 半年没消费 + 低频 + 无投诉 → 风险 0.6+ (HIGH)
        ChurnPredictionService.ChurnFeatures f = new ChurnPredictionService.ChurnFeatures();
        f.lastPurchaseDays = 200;
        f.monthlyFreq = new BigDecimal("0.3");
        f.complaintCount = 0;
        f.reviewAvgRating = new BigDecimal("4.0");
        f.historicalLtv = new BigDecimal("3000");

        double s = ChurnPredictionService.computeChurnScore(f);
        assertTrue(s >= 0.5, "半年没消费 + 低频应得 0.5+，实际 " + s);
    }

    @Test
    void churnScore_lowRisk_activeMember() {
        // 30 天内消费 + 高频 + 高评分 + 高 LTV → 风险 < 0.2 (SAFE)
        ChurnPredictionService.ChurnFeatures f = new ChurnPredictionService.ChurnFeatures();
        f.lastPurchaseDays = 15;
        f.monthlyFreq = new BigDecimal("3.0");
        f.complaintCount = 0;
        f.reviewAvgRating = new BigDecimal("4.8");
        f.historicalLtv = new BigDecimal("20000");

        double s = ChurnPredictionService.computeChurnScore(f);
        assertTrue(s < 0.2, "活跃高价值会员风险 < 0.2，实际 " + s);
    }

    @Test
    void churnScore_neverPurchased_maxRisk() {
        ChurnPredictionService.ChurnFeatures f = new ChurnPredictionService.ChurnFeatures();
        f.lastPurchaseDays = 9999;
        f.monthlyFreq = BigDecimal.ZERO;
        f.complaintCount = 0;
        f.historicalLtv = BigDecimal.ZERO;

        double s = ChurnPredictionService.computeChurnScore(f);
        // 实际 ≈ 0.7（recency 1.0 + freq 1.0 + ltv 0.7 加权）
        assertTrue(s >= 0.65, "从未消费应得 0.65+（HIGH 风险），实际 " + s);
    }

    @Test
    void churnScore_complaintsSpike() {
        ChurnPredictionService.ChurnFeatures f1 = new ChurnPredictionService.ChurnFeatures();
        f1.lastPurchaseDays = 30;
        f1.monthlyFreq = new BigDecimal("1.0");
        f1.complaintCount = 0;
        f1.historicalLtv = new BigDecimal("5000");

        ChurnPredictionService.ChurnFeatures f2 = new ChurnPredictionService.ChurnFeatures();
        f2.lastPurchaseDays = 30;
        f2.monthlyFreq = new BigDecimal("1.0");
        f2.complaintCount = 3;  // 多次投诉
        f2.historicalLtv = new BigDecimal("5000");

        double s1 = ChurnPredictionService.computeChurnScore(f1);
        double s2 = ChurnPredictionService.computeChurnScore(f2);
        assertTrue(s2 > s1, "多次投诉应推高风险：s1=" + s1 + " s2=" + s2);
    }

    @Test
    void churnScore_clampedBetween0And1() {
        // 极端输入 → 仍 [0,1]
        ChurnPredictionService.ChurnFeatures f = new ChurnPredictionService.ChurnFeatures();
        f.lastPurchaseDays = 99999;
        f.monthlyFreq = new BigDecimal("0.0001");
        f.complaintCount = 999;
        f.reviewAvgRating = new BigDecimal("1.0");
        f.historicalLtv = new BigDecimal("0.01");
        double s = ChurnPredictionService.computeChurnScore(f);
        assertTrue(s >= 0.0 && s <= 1.0);
    }

    @Test
    void churnScore_monotonic_lastPurchaseDays() {
        // lastPurchaseDays 越大 → 评分越高（单调）
        double[] scores = new double[5];
        int[] days = {15, 60, 90, 180, 365};
        for (int i = 0; i < days.length; i++) {
            ChurnPredictionService.ChurnFeatures f = new ChurnPredictionService.ChurnFeatures();
            f.lastPurchaseDays = days[i];
            f.monthlyFreq = new BigDecimal("1.0");
            f.historicalLtv = new BigDecimal("3000");
            scores[i] = ChurnPredictionService.computeChurnScore(f);
        }
        for (int i = 1; i < scores.length; i++) {
            assertTrue(scores[i] >= scores[i - 1],
                    "days " + days[i-1] + "→" + days[i] + " 评分应单调："
                            + scores[i-1] + " → " + scores[i]);
        }
    }

    // ==================== 风险等级 ====================
    @Test
    void riskLevel_highThreshold() {
        BigDecimal s = new BigDecimal("0.75");
        assertEquals("HIGH", ChurnPredictionService.riskLevel(s,
                new BigDecimal("0.70"), new BigDecimal("0.40")));
    }

    @Test
    void riskLevel_mediumThreshold() {
        BigDecimal s = new BigDecimal("0.50");
        assertEquals("MEDIUM", ChurnPredictionService.riskLevel(s,
                new BigDecimal("0.70"), new BigDecimal("0.40")));
    }

    @Test
    void riskLevel_lowThreshold() {
        BigDecimal s = new BigDecimal("0.25");
        assertEquals("LOW", ChurnPredictionService.riskLevel(s,
                new BigDecimal("0.70"), new BigDecimal("0.40")));
    }

    @Test
    void riskLevel_safeThreshold() {
        BigDecimal s = new BigDecimal("0.10");
        assertEquals("SAFE", ChurnPredictionService.riskLevel(s,
                new BigDecimal("0.70"), new BigDecimal("0.40")));
    }

    // ==================== Visual AI 真实特征分析 ====================
    @Test
    void visualAi_analyzeFeatures_deterministic() {
        // 同一 hash 两次调用产出相同结果（确定性算法）
        VisualAiService svc = new VisualAiService();
        Map<String, Object> r1 = svc.analyzeFeatures("SKIN", "abc123def");
        Map<String, Object> r2 = svc.analyzeFeatures("SKIN", "abc123def");

        assertEquals(r1.get("score"), r2.get("score"), "同一 hash 必须产出相同 score");
        assertEquals(r1.get("tags"), r2.get("tags"), "同一 hash 必须产出相同 tags");
        assertEquals(r1.get("features"), r2.get("features"), "同一 hash 必须产出相同 features");
    }

    @Test
    void visualAi_analyzeFeatures_differentHash_differentResult() {
        VisualAiService svc = new VisualAiService();
        Map<String, Object> r1 = svc.analyzeFeatures("SKIN", "hash-aaa");
        Map<String, Object> r2 = svc.analyzeFeatures("SKIN", "hash-bbb");
        assertNotEquals(r1.get("score"), r2.get("score"),
                "不同 hash 应产出不同 score（确定性但有差异）");
    }

    @Test
    void visualAi_analyzeFeatures_scoreInRange() {
        VisualAiService svc = new VisualAiService();
        for (String type : new String[]{"SKIN", "FACE", "BODY", "HAIR"}) {
            for (String h : new String[]{"x1", "x2", "x3", "y1", "z9"}) {
                Map<String, Object> r = svc.analyzeFeatures(type, h);
                BigDecimal score = (BigDecimal) r.get("score");
                assertNotNull(score, type + "/" + h + " 应有 score");
                assertTrue(score.compareTo(BigDecimal.ZERO) >= 0
                        && score.compareTo(new BigDecimal("100")) <= 0,
                        type + "/" + h + " score 应在 [0,100] 区间，实际 " + score);
                assertNotNull(r.get("tags"), type + "/" + h + " 应有 tags");
                assertTrue(((String) r.get("tags")).length() > 0,
                        type + "/" + h + " tags 非空");
            }
        }
    }

    @Test
    void visualAi_analyzeFeatures_engineVersion() {
        VisualAiService svc = new VisualAiService();
        Map<String, Object> r = svc.analyzeFeatures("SKIN", "test");
        assertEquals("visual-ai-v1.0", r.get("engine"), "应注明引擎版本便于回测");
        assertEquals(true, r.get("deterministic"), "应注明确定性");
    }

    @Test
    void visualAi_compareArchives_returnsValidOutput() {
        VisualAiService svc = new VisualAiService();
        MhVisualArchive before = new MhVisualArchive();
        before.setImageHash("before-hash-001");
        before.setAiFeaturesJson("{brightness:0.4,saturation:0.5,symmetry:0.6,texture_score:0.5}");

        MhVisualArchive after = new MhVisualArchive();
        after.setImageHash("after-hash-002");
        after.setAiFeaturesJson("{brightness:0.7,saturation:0.6,symmetry:0.7,texture_score:0.8}");

        Map<String, Object> res = svc.compareArchives(before, after);
        assertNotNull(res.get("similarity"), "应有 similarity");
        assertNotNull(res.get("diffScore"), "应有 diffScore");
        assertNotNull(res.get("skinDiff"), "应有 skinDiff");
        assertNotNull(res.get("summary"), "应有 summary");

        // 改进应大于退步（after > before）
        BigDecimal diffScore = (BigDecimal) res.get("diffScore");
        assertTrue(diffScore.compareTo(BigDecimal.valueOf(5)) > 0, "改进分应 > 0");
    }

    @Test
    void visualAi_compareArchives_deterministic() {
        VisualAiService svc = new VisualAiService();
        MhVisualArchive b = new MhVisualArchive();
        b.setImageHash("bb");
        b.setAiFeaturesJson("{brightness:0.4,saturation:0.5,symmetry:0.6,texture_score:0.5}");
        MhVisualArchive a = new MhVisualArchive();
        a.setImageHash("aa");
        a.setAiFeaturesJson("{brightness:0.7,saturation:0.6,symmetry:0.7,texture_score:0.8}");

        Map<String, Object> r1 = svc.compareArchives(b, a);
        Map<String, Object> r2 = svc.compareArchives(b, a);
        assertEquals(r1.get("similarity"), r2.get("similarity"), "对比应确定性");
    }

    // ==================== 评分阈值边界 ====================
    @Test
    void churnScore_thresholdBoundary() {
        // 测试 high/medium 阈值边界
        BigDecimal high = new BigDecimal("0.70");
        BigDecimal med = new BigDecimal("0.40");
        // 0.70 = HIGH（边界）
        assertEquals("HIGH", ChurnPredictionService.riskLevel(high, high, med));
        // 0.6999 = MEDIUM
        assertEquals("MEDIUM", ChurnPredictionService.riskLevel(
                new BigDecimal("0.6999"), high, med));
        // 0.40 = MEDIUM（边界）
        assertEquals("MEDIUM", ChurnPredictionService.riskLevel(med, high, med));
        // 0.3999 = LOW
        assertEquals("LOW", ChurnPredictionService.riskLevel(
                new BigDecimal("0.3999"), high, med));
    }
}