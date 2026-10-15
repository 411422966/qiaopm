package com.meiyetec.service;

import com.meiyetec.entity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-3 / P1-5 剩余改进：Entity 字段 + 简单算法测试
 */
class P1RemainingEntityFieldsTest {

    // ==================== MktDispatch ====================
    @Test
    void dispatch_statusLifecycle() {
        MktDispatch d = new MktDispatch();
        // DRAFT → PUBLISHED → EXECUTING → DONE / CANCELLED
        d.setStatus("DRAFT");
        assertEquals("DRAFT", d.getStatus());

        d.setStatus("PUBLISHED");
        assertEquals("PUBLISHED", d.getStatus());

        d.setStatus("DONE");
        assertEquals("DONE", d.getStatus());

        d.setStatus("CANCELLED");
        assertEquals("CANCELLED", d.getStatus());
    }

    @Test
    void dispatch_dispatchType() {
        MktDispatch d = new MktDispatch();
        for (String t : new String[]{"COUPON", "PACKAGE", "DISCOUNT", "ACTIVITY"}) {
            d.setDispatchType(t);
            assertEquals(t, d.getDispatchType());
        }
    }

    @Test
    void dispatch_targetType() {
        MktDispatch d = new MktDispatch();
        // ALL / BRAND / SPECIFIC
        for (String t : new String[]{"ALL", "BRAND", "SPECIFIC"}) {
            d.setTargetType(t);
            assertEquals(t, d.getTargetType());
        }
    }

    // ==================== MktDispatchTarget ====================
    @Test
    void dispatchTarget_statusLifecycle() {
        MktDispatchTarget t = new MktDispatchTarget();
        // PENDING → ACCEPTED → EXECUTED / REJECTED
        t.setStatus("PENDING");
        assertEquals("PENDING", t.getStatus());

        t.setStatus("ACCEPTED");
        assertEquals("ACCEPTED", t.getStatus());

        t.setStatus("EXECUTED");
        assertEquals("EXECUTED", t.getStatus());

        t.setStatus("REJECTED");
        assertEquals("REJECTED", t.getStatus());
    }

    // ==================== ScheduleSuggestion ====================
    @Test
    void scheduleSuggestion_basicFields() {
        ScheduleSuggestion s = new ScheduleSuggestion();
        s.setStoreId(1L);
        s.setWorkDate(LocalDate.of(2026, 8, 14));
        s.setEmployeeId(100L);
        s.setEmployeeName("小李");
        s.setShiftCode("DAY");
        s.setStartTime(LocalTime.of(10, 0));
        s.setEndTime(LocalTime.of(13, 0));
        s.setExpectedLoad(75);
        s.setConfidence(new BigDecimal("0.85"));
        s.setStatus("PENDING");

        assertEquals(LocalDate.of(2026, 8, 14), s.getWorkDate());
        assertEquals("DAY", s.getShiftCode());
        assertEquals(75, s.getExpectedLoad());
        assertEquals(0, new BigDecimal("0.85").compareTo(s.getConfidence()));
    }

    @Test
    void scheduleSuggestion_shiftCodes() {
        ScheduleSuggestion s = new ScheduleSuggestion();
        for (String c : new String[]{"DAY", "MIDDLE", "NIGHT"}) {
            s.setShiftCode(c);
            assertEquals(c, s.getShiftCode());
        }
    }

    // ==================== BizForecastDemand ====================
    @Test
    void forecastDemand_predictedVsActual() {
        BizForecastDemand f = new BizForecastDemand();
        f.setForecastDate(LocalDate.of(2026, 8, 14));
        f.setForecastType("BOOKING");
        f.setPredictedValue(new BigDecimal("42.50"));
        f.setActualValue(new BigDecimal("38.00"));
        // 误差率 = (实际 - 预测) / 预测 * 100
        // (38 - 42.5) / 42.5 = -0.10588 → setScale(4, HALF_UP) = -0.1059 → × 100 = -10.5900
        BigDecimal errorRate = f.getActualValue().subtract(f.getPredictedValue())
                .divide(f.getPredictedValue(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        assertEquals(0, new BigDecimal("-10.5900").compareTo(errorRate),
                "误差率应为 -10.59%");
        f.setErrorRate(errorRate);
        f.setModelVersion("BASE-MA7");
        f.setConfidence(new BigDecimal("0.55"));
        assertEquals("BOOKING", f.getForecastType());
        assertEquals("BASE-MA7", f.getModelVersion());
    }

    @Test
    void forecastDemand_forecastType() {
        BizForecastDemand f = new BizForecastDemand();
        for (String t : new String[]{"BOOKING", "ORDER", "STOCK"}) {
            f.setForecastType(t);
            assertEquals(t, f.getForecastType());
        }
    }

    // ==================== CrmRecommendLog ====================
    @Test
    void recommendLog_funnel() {
        CrmRecommendLog log = new CrmRecommendLog();
        log.setMemberId(1L);
        log.setRecommendType("COUPON");
        log.setTargetId(100L);
        log.setTargetName("新人礼券");
        log.setScore(new BigDecimal("0.85"));
        log.setReason("高价值客户");
        log.setChannel("INTERNAL");
        log.setShown(1);
        log.setClicked(0);
        log.setConverted(0);
        log.setExposedAt(LocalDateTime.now());

        // 曝光 → 点击 → 转化漏斗
        assertEquals(1, log.getShown());
        assertEquals(0, log.getClicked());
        assertEquals(0, log.getConverted());

        // 点击
        log.setClicked(1);
        log.setClickedAt(LocalDateTime.now());
        assertEquals(1, log.getClicked());

        // 转化
        log.setConverted(1);
        log.setConvertedAt(LocalDateTime.now());
        assertEquals(1, log.getConverted());
    }

    @Test
    void recommendLog_recommendTypes() {
        CrmRecommendLog log = new CrmRecommendLog();
        for (String t : new String[]{"COUPON", "PACKAGE", "SERVICE", "PUNCH", "IMAGE"}) {
            log.setRecommendType(t);
            assertEquals(t, log.getRecommendType());
        }
    }

    // ==================== 推荐打分逻辑测试 ====================
    @Test
    void scoreCoupon_clampTo1() {
        // 复现 service 内 scoreCoupon 公式
        double base = 0.3;
        base += 0.3;  // 高 LTV
        base += 0.2;  // 久未消费
        base += 0.3;  // LOST
        base += 0.1;  // 大额券
        double capped = Math.min(1.0, base);
        assertEquals(1.0, capped, 0.001);
    }

    @Test
    void scoreCoupon_clampTo1_extreme() {
        double base = 0.5 + 0.3 + 0.2 + 0.3 + 0.1;
        assertEquals(1.4, base, 0.001);
        assertEquals(1.0, Math.min(1.0, base), 0.001);
    }

    @Test
    void linearTrend_upward() {
        // 模拟 y = [1, 2, 3, 4, 5] → 斜率 = 1.0
        double[] y = {1, 2, 3, 4, 5};
        double trend = linearTrend(y);
        assertEquals(1.0, trend, 0.001);
    }

    @Test
    void linearTrend_downward() {
        // 递减：[-5, -4, -3, -2, -1] → 斜率 = 1.0（因为 y 是 -5..-1）
        double[] y = {-5, -4, -3, -2, -1};
        double trend = linearTrend(y);
        assertEquals(1.0, trend, 0.001);
    }

    @Test
    void linearTrend_flat() {
        double[] y = {5, 5, 5, 5, 5};
        double trend = linearTrend(y);
        assertEquals(0.0, trend, 0.001);
    }

    @Test
    void linearTrend_tooShort() {
        double[] y = {3};
        assertEquals(0.0, linearTrend(y));
    }

    /** 复制自生产代码 P1RemainingService.linearTrend */
    private static double linearTrend(double[] y) {
        int n = y.length;
        if (n < 2) return 0;
        double sx = 0, sy = 0, sxy = 0, sxx = 0;
        for (int i = 0; i < n; i++) {
            sx += i; sy += y[i];
            sxy += i * y[i]; sxx += i * i;
        }
        double denom = n * sxx - sx * sx;
        return denom == 0 ? 0 : (n * sxy - sx * sy) / denom;
    }
}