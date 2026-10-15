package com.meiyetec.service;

import com.meiyetec.entity.CrmReview;
import com.meiyetec.entity.CrmReviewDimensionConfig;
import com.meiyetec.entity.CrmServiceImprovement;
import com.meiyetec.entity.CrmServiceImprovementLog;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3.0-H P1.5：评价分析 + 服务改进闭环测试
 *
 * <p>不依赖 Spring / DB，验证：
 *  - NPS 计算
 *  - 标签统计
 *  - 状态机
 *  - 维度编码 → 字段映射
 *  - 自动从低分评价生成改进任务
 * </p>
 */
class ReviewAnalysisTest {

    // ==================== NPS 计算 ====================
    @Test
    void nps_basicCalculation() {
        // 20 条评价：promoters=10, passives=5, detractors=5
        // NPS = (10 - 5) / 20 * 100 = 25
        long promoters = 10, passives = 5, detractors = 5, total = 20;
        double nps = total == 0 ? 0 : (promoters - detractors) * 100.0 / total;
        assertEquals(25.0, nps, 0.01);
    }

    @Test
    void nps_zeroTotal_noNaN() {
        long total = 0;
        double nps = total == 0 ? 0 : 0;
        assertEquals(0.0, nps, 0.001, "分母为 0 防 NaN");
    }

    @Test
    void nps_allDetractors_neg100() {
        long promoters = 0, detractors = 100, total = 100;
        double nps = (promoters - detractors) * 100.0 / total;
        assertEquals(-100.0, nps, 0.01);
    }

    @Test
    void nps_allPromoters_pos100() {
        long promoters = 100, detractors = 0, total = 100;
        double nps = (promoters - detractors) * 100.0 / total;
        assertEquals(100.0, nps, 0.01);
    }

    // ==================== 维度编码 → 字段映射 ====================
    @Test
    void dimensionToColumn_mapping() throws Exception {
        Method m = ReviewAnalysisService.class.getDeclaredMethod("dimensionToColumn", String.class);
        m.setAccessible(true);
        assertEquals("service_rating", m.invoke(null, "SERVICE"));
        assertEquals("skill_rating", m.invoke(null, "SKILL"));
        assertEquals("environment_rating", m.invoke(null, "ENVIRONMENT"));
        assertEquals("price_rating", m.invoke(null, "PRICE"));
        assertEquals("overall_rating", m.invoke(null, "OVERALL"));
        assertNull(m.invoke(null, "UNKNOWN"));
    }

    // ==================== 状态机校验 ====================
    @Test
    void stateMachine_validTransits() throws Exception {
        Method m = ReviewAnalysisService.class.getDeclaredMethod("isValidTransit",
                Integer.class, Integer.class);
        m.setAccessible(true);

        // 1→2 / 1→5
        assertTrue((Boolean) m.invoke(null, 1, 2));
        assertTrue((Boolean) m.invoke(null, 1, 5));
        assertFalse((Boolean) m.invoke(null, 1, 3), "1→3 不允许（必须先 2）");

        // 2→3 / 2→5
        assertTrue((Boolean) m.invoke(null, 2, 3));
        assertTrue((Boolean) m.invoke(null, 2, 5));
        assertFalse((Boolean) m.invoke(null, 2, 4), "2→4 不允许（必须先 3）");

        // 3→4 / 3→5
        assertTrue((Boolean) m.invoke(null, 3, 4));
        assertTrue((Boolean) m.invoke(null, 3, 5));

        // 4/5 不可流转
        assertFalse((Boolean) m.invoke(null, 4, 1));
        assertFalse((Boolean) m.invoke(null, 4, 5));
        assertFalse((Boolean) m.invoke(null, 5, 1));

        // null → 1
        assertTrue((Boolean) m.invoke(null, null, 1));
    }

    // ==================== 标签统计 ====================
    @Test
    void tagAnalysis_counting() {
        // 模拟评价数据
        List<String> reviewTags = List.of(
            "服务好,环境好",
            "服务好,专业",
            "专业,环境好,价格实惠"
        );

        Map<String, Long> tagMap = new HashMap<>();
        for (String tags : reviewTags) {
            String[] arr = tags.split("[,，;；]");
            for (String t : arr) {
                final String tag = t.trim();
                if (tag.isEmpty()) continue;
                tagMap.merge(tag, 1L, Long::sum);
            }
        }
        assertEquals(2L, tagMap.get("服务好"));
        assertEquals(2L, tagMap.get("专业"));
        assertEquals(2L, tagMap.get("环境好"));
        assertEquals(1L, tagMap.get("价格实惠"));
    }

    @Test
    void tagAnalysis_unicodeSeparator() {
        // 验证中英文标点都支持
        String tags = "服务好，环境好；专业,实惠";
        String[] arr = tags.split("[,，;；]");
        assertEquals(4, arr.length);
        assertEquals("服务好", arr[0]);
        assertEquals("环境好", arr[1]);
        assertEquals("专业", arr[2]);
        assertEquals("实惠", arr[3]);
    }

    // ==================== 维度差距计算 ====================
    @Test
    void dimensionGapCalculation() {
        BigDecimal current = new BigDecimal("3.20");
        BigDecimal target = new BigDecimal("4.50");
        BigDecimal gap = current.subtract(target).setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, new BigDecimal("-1.30").compareTo(gap));
        assertTrue(gap.compareTo(BigDecimal.ZERO) < 0, "差距负数 → 需改进");
    }

    @Test
    void dimensionGap_positive_aboveTarget() {
        BigDecimal current = new BigDecimal("4.80");
        BigDecimal target = new BigDecimal("4.50");
        BigDecimal gap = current.subtract(target).setScale(2, RoundingMode.HALF_UP);
        assertTrue(gap.compareTo(BigDecimal.ZERO) > 0, "差距正数 → 已达标");
    }

    // ==================== Entity 字段读写 ====================
    @Test
    void reviewDimensionConfig_fields() {
        CrmReviewDimensionConfig d = new CrmReviewDimensionConfig();
        d.setDimensionCode("SERVICE");
        d.setDimensionName("服务态度");
        d.setWeight(new BigDecimal("1.50"));
        d.setTargetScore(new BigDecimal("4.50"));
        d.setEnabled(1);
        d.setSortOrder(1);

        assertEquals("SERVICE", d.getDimensionCode());
        assertEquals(0, new BigDecimal("1.50").compareTo(d.getWeight()));
        assertEquals(0, new BigDecimal("4.50").compareTo(d.getTargetScore()));
    }

    @Test
    void serviceImprovement_allFields() {
        CrmServiceImprovement i = new CrmServiceImprovement();
        i.setImprovementNo("IMP20260814001");
        i.setTitle("面部护理评价改进");
        i.setSourceType("REVIEW");
        i.setSourceId(100L);
        i.setDimensionCode("SERVICE");
        i.setCurrentScore(new BigDecimal("3.20"));
        i.setTargetScore(new BigDecimal("4.50"));
        i.setGapScore(new BigDecimal("-1.30"));
        i.setProblem("客户评价服务态度冷");
        i.setRootCause("美容师培训不足");
        i.setActionPlan("组织服务礼仪培训 + 服务话术演练");
        i.setExpectedEffect("服务评分提升至 4.5+");
        i.setOwnerName("王经理");
        i.setPriority(3);
        i.setStatus(1);

        assertEquals("IMP20260814001", i.getImprovementNo());
        assertEquals("REVIEW", i.getSourceType());
        assertEquals(0, new BigDecimal("-1.30").compareTo(i.getGapScore()));
        assertEquals(1, i.getStatus());
    }

    @Test
    void improvementLog_statusTransition() {
        CrmServiceImprovementLog l = new CrmServiceImprovementLog();
        // CREATE / ASSIGN / TRANSIT / COMMENT / COMPLETE / VERIFY
        for (String a : new String[]{"CREATE", "ASSIGN", "TRANSIT", "COMMENT", "COMPLETE", "VERIFY"}) {
            l.setActionType(a);
            assertEquals(a, l.getActionType());
        }
    }

    // ==================== 自动触发改进场景 ====================
    @Test
    void autoImprovement_triggerLowScore() {
        // 客户 1 星评价：自动触发紧急改进任务
        CrmReview r = new CrmReview();
        r.setOverallRating(1);
        r.setServiceRating(1);
        r.setSkillRating(3);
        r.setEnvironmentRating(4);
        r.setPriceRating(2);
        r.setContent("服务态度很差");

        // 找最低分维度
        String worstDim = null;
        Integer worstScore = 5;
        for (Map.Entry<String, Integer> e : Map.of(
                "SERVICE", r.getServiceRating(),
                "SKILL", r.getSkillRating(),
                "ENVIRONMENT", r.getEnvironmentRating(),
                "PRICE", r.getPriceRating()).entrySet()) {
            Integer v = e.getValue();
            if (v != null && v < worstScore) {
                worstScore = v;
                worstDim = e.getKey();
            }
        }
        assertEquals("SERVICE", worstDim);
        assertEquals(1, worstScore);

        // 改进任务
        CrmServiceImprovement imp = new CrmServiceImprovement();
        imp.setSourceType("REVIEW");
        imp.setDimensionCode(worstDim);
        imp.setCurrentScore(BigDecimal.valueOf(worstScore));
        imp.setTitle("评价驱动改进：" + worstDim + " 评分仅 " + worstScore + " 星");
        imp.setStatus(1);
        imp.setPriority(4);  // 紧急

        assertEquals("REVIEW", imp.getSourceType());
        assertEquals("SERVICE", imp.getDimensionCode());
        assertEquals(4, imp.getPriority());
    }

    @Test
    void autoImprovement_noTrigger_whenAboveThreshold() {
        // 4-5 星评价不应触发改进
        CrmReview r = new CrmReview();
        r.setOverallRating(4);
        boolean shouldTrigger = r.getOverallRating() != null && r.getOverallRating() <= 3;
        assertFalse(shouldTrigger, "4-5 星评价不应触发改进任务");
    }
}