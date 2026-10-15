package com.meiyetec.service;

import com.meiyetec.entity.CrmFollowUpNote;
import com.meiyetec.entity.CrmFollowUpTouch;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3.0-H P0.2：回访闭环补强（Entity + 算法）
 */
class FollowUpClosureEntityTest {

    // ==================== CrmFollowUpTouch ====================
    @Test
    void touch_allTouchTypes() {
        CrmFollowUpTouch t = new CrmFollowUpTouch();
        for (String type : new String[]{"PHONE", "SMS", "WECHAT", "WX_WORK", "EMAIL", "FACE"}) {
            t.setTouchType(type);
            assertEquals(type, t.getTouchType());
        }
    }

    @Test
    void touch_allTouchStatuses() {
        CrmFollowUpTouch t = new CrmFollowUpTouch();
        // SENT / REPLIED / FAILED / NO_ANSWER
        for (String s : new String[]{"SENT", "REPLIED", "FAILED", "NO_ANSWER"}) {
            t.setTouchStatus(s);
            assertEquals(s, t.getTouchStatus());
        }
    }

    @Test
    void touch_phoneCallDuration() {
        CrmFollowUpTouch t = new CrmFollowUpTouch();
        t.setTouchType("PHONE");
        t.setDurationSeconds(180);  // 3 分钟
        assertEquals(180, t.getDurationSeconds());
        // 推算：3 分钟 → 通话 5 元（按分钟计）
        BigDecimal cost = BigDecimal.valueOf(180).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("1.0"));
        assertEquals(0, new BigDecimal("3.00").compareTo(cost));
    }

    // ==================== CrmFollowUpNote ====================
    @Test
    void note_actionTypes() {
        CrmFollowUpNote n = new CrmFollowUpNote();
        for (String a : new String[]{"CREATE", "ASSIGN", "START", "FINISH", "CLOSE", "REOPEN", "EDIT", "TOUCH"}) {
            n.setActionType(a);
            assertEquals(a, n.getActionType());
        }
    }

    @Test
    void note_statusTransition() {
        CrmFollowUpNote n = new CrmFollowUpNote();
        // PENDING→IN_PROGRESS→FINISHED
        n.setFromStatus(1);
        n.setToStatus(2);
        assertEquals(1, n.getFromStatus());
        assertEquals(2, n.getToStatus());
    }

    // ==================== pct 工具测试（反射调用） ====================
    @Test
    void pct_normal() throws Exception {
        Method m = FollowUpClosureService.class.getDeclaredMethod("pct", long.class, long.class);
        m.setAccessible(true);
        BigDecimal v = (BigDecimal) m.invoke(null, 25L, 100L);
        assertEquals(0, new BigDecimal("25.00").compareTo(v));
    }

    @Test
    void pct_zeroDenominator() throws Exception {
        Method m = FollowUpClosureService.class.getDeclaredMethod("pct", long.class, long.class);
        m.setAccessible(true);
        BigDecimal v = (BigDecimal) m.invoke(null, 5L, 0L);
        assertEquals(0, v.compareTo(BigDecimal.ZERO), "分母为 0 应返回 0（防 NaN）");
    }

    @Test
    void pct_fullHundred() throws Exception {
        Method m = FollowUpClosureService.class.getDeclaredMethod("pct", long.class, long.class);
        m.setAccessible(true);
        BigDecimal v = (BigDecimal) m.invoke(null, 100L, 100L);
        assertEquals(0, new BigDecimal("100.00").compareTo(v));
    }

    @Test
    void pct_thirdDigits() throws Exception {
        Method m = FollowUpClosureService.class.getDeclaredMethod("pct", long.class, long.class);
        m.setAccessible(true);
        BigDecimal v = (BigDecimal) m.invoke(null, 1L, 3L);
        // 1/3 = 0.3333... → 33.33
        assertEquals(0, new BigDecimal("33.33").compareTo(v));
    }

    // ==================== 业务字段组合 ====================
    @Test
    void touch_withReplyContent() {
        CrmFollowUpTouch t = new CrmFollowUpTouch();
        t.setTaskId(100L);
        t.setTouchType("PHONE");
        t.setContact("13800000000");
        t.setContent("您好李女士，本次服务的体验如何？");
        t.setTouchStatus("REPLIED");
        t.setReplyContent("挺好的，下次还会来");
        t.setReplyAt(LocalDateTime.now());

        assertEquals("REPLIED", t.getTouchStatus());
        assertEquals("挺好的，下次还会来", t.getReplyContent());
        assertNotNull(t.getReplyAt());
    }

    @Test
    void note_reopenTrace() {
        CrmFollowUpNote n = new CrmFollowUpNote();
        n.setTaskId(200L);
        n.setActionType("REOPEN");
        n.setFromStatus(3);  // FINISHED
        n.setToStatus(2);    // IN_PROGRESS
        n.setContent("客户反馈问题未解决，重新跟进");

        assertEquals(3, n.getFromStatus());
        assertEquals(2, n.getToStatus());
        assertTrue(n.getContent().contains("未解决"));
    }
}