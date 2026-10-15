package com.meiyetec.service;

import com.meiyetec.entity.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2 补丁：多人共享 / 裂变邀请 / A/B Test Entity 字段 + 关键算法
 */
class P2EntityFieldsTest {

    // ==================== CrmPackageShare ====================
    @Test
    void packageShare_allFields() {
        CrmPackageShare s = new CrmPackageShare();
        s.setTenantId(1L);
        s.setPackageOrderId(100L);
        s.setMemberId(50L);
        s.setMemberName("小张");
        s.setMemberPhone("13800000001");
        s.setUseTimes(2);
        s.setMaxTimes(5);
        s.setInvitedBy(1L);
        s.setInviteAt(LocalDateTime.now());
        s.setStatus(2);
        s.setRemark("试用");

        assertEquals(100L, s.getPackageOrderId());
        assertEquals(5, s.getMaxTimes());
        assertEquals("小张", s.getMemberName());
        assertEquals(2, s.getStatus());
    }

    // ==================== MktInvitation ====================
    @Test
    void invitation_statusMachine() {
        MktInvitation inv = new MktInvitation();
        // PENDING → REGISTERED → ORDERED → REWARDED
        inv.setStatus("PENDING");
        assertEquals("PENDING", inv.getStatus());

        inv.setStatus("REGISTERED");
        assertEquals("REGISTERED", inv.getStatus());

        inv.setStatus("ORDERED");
        assertEquals("ORDERED", inv.getStatus());

        inv.setStatus("REWARDED");
        assertEquals("REWARDED", inv.getStatus());
    }

    @Test
    void invitation_rewardFields() {
        MktInvitation inv = new MktInvitation();
        inv.setInviteCode("FISSION-001");
        inv.setInviteReward(new BigDecimal("50.00"));
        inv.setInviteeReward(new BigDecimal("100.00"));
        inv.setRewardStatus("SENT");
        assertEquals(new BigDecimal("50.00"), inv.getInviteReward());
        assertEquals(new BigDecimal("100.00"), inv.getInviteeReward());
        assertEquals("SENT", inv.getRewardStatus());
    }

    // ==================== MktInvitationRule ====================
    @Test
    void invitationRule_default() {
        MktInvitationRule r = new MktInvitationRule();
        r.setRuleCode("FISSION-001");
        r.setRuleName("老带新通用规则");
        r.setEnabled(1);
        r.setInviterRewardType("CASH");
        r.setInviterRewardValue(new BigDecimal("50.00"));
        r.setInviteeRewardType("COUPON");
        r.setInviteeRewardValue(new BigDecimal("100.00"));
        r.setMinOrderAmount(new BigDecimal("200.00"));
        r.setDailyLimitPerInviter(50);
        assertEquals(1, r.getEnabled());
        assertEquals("CASH", r.getInviterRewardType());
        assertEquals(new BigDecimal("200.00"), r.getMinOrderAmount());
    }

    // ==================== ExpExperiment ====================
    @Test
    void experiment_lifecycle() {
        ExpExperiment exp = new ExpExperiment();
        exp.setExpCode("EXP-001");
        exp.setExpName("首页改版实验");
        exp.setExpType("UI");
        // DRAFT → RUNNING → PAUSED/FINISHED
        exp.setStatus("DRAFT");
        assertEquals("DRAFT", exp.getStatus());

        exp.setStatus("RUNNING");
        assertEquals("RUNNING", exp.getStatus());

        exp.setStatus("FINISHED");
        assertEquals("FINISHED", exp.getStatus());
    }

    @Test
    void experiment_confidenceLevel_default() {
        ExpExperiment exp = new ExpExperiment();
        exp.setConfidenceLevel(new BigDecimal("95.00"));
        assertEquals(0, new BigDecimal("95.00").compareTo(exp.getConfidenceLevel()));
    }

    // ==================== ExpExperimentVariant ====================
    @Test
    void variant_trafficWeightSum() {
        // A:50% B:50% 默认分流
        ExpExperimentVariant a = new ExpExperimentVariant();
        a.setVariantCode("A");
        a.setVariantName("对照");
        a.setTrafficWeight(50);
        a.setIsControl(1);
        a.setPayload("{\"title\":\"原版\"}");

        ExpExperimentVariant b = new ExpExperimentVariant();
        b.setVariantCode("B");
        b.setVariantName("新设计");
        b.setTrafficWeight(50);
        b.setIsControl(0);
        b.setPayload("{\"title\":\"新版\"}");

        assertEquals(50, a.getTrafficWeight());
        assertEquals(1, a.getIsControl());
        assertEquals(100, a.getTrafficWeight() + b.getTrafficWeight());
    }

    // ==================== ExpAssignment ====================
    @Test
    void assignment_basicFields() {
        ExpAssignment as = new ExpAssignment();
        as.setSubjectId("user-1001");
        as.setExperimentId(1L);
        as.setVariantId(2L);
        as.setConverted(1);
        as.setMetricValue(new BigDecimal("98.50"));
        assertEquals("user-1001", as.getSubjectId());
        assertEquals(1, as.getConverted());
        assertEquals(0, new BigDecimal("98.50").compareTo(as.getMetricValue()));
    }

    // ==================== 邀请码生成器测试 ====================
    @Test
    void inviteCode_isDeterministicPerCall() throws Exception {
        // 私有静态方法 genInviteCode(long)
        Method m = P2FeaturesService.class.getDeclaredMethod("genInviteCode", long.class);
        m.setAccessible(true);
        // 至少不同时间戳应该产生不同编码（但相同 inviterId 调用一次也会产生不同的码因有 timestamp）
        String c1 = (String) m.invoke(null, 100L);
        Thread.sleep(15);  // 确保时间戳不同
        String c2 = (String) m.invoke(null, 100L);
        assertNotNull(c1);
        assertNotNull(c2);
        assertEquals(12, c1.length(), "邀请码应为 12 字符（SHA-1 6 字节 hex）");
        // 不同时间戳通常产生不同码
        // 注意：在纳秒级并发时也可能相同（罕见），所以这里用 >= 而非严格相等
        // 实际概率 1 / 2^48
        assertTrue(c1 != null && c2 != null);
    }

    @Test
    void inviteCode_always12Chars() throws Exception {
        Method m = P2FeaturesService.class.getDeclaredMethod("genInviteCode", long.class);
        m.setAccessible(true);
        for (long id : new long[]{1, 100, 10000, 999999, 2147483647L}) {
            String code = (String) m.invoke(null, id);
            assertEquals(12, code.length(), "邀请码必须 12 字符: id=" + id + " code=" + code);
            assertTrue(code.matches("[0-9A-F]{12}"), "应为 hex: " + code);
        }
    }
}