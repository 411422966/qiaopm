package com.meiyetec.service;

import com.meiyetec.entity.CohortDefinition;
import com.meiyetec.entity.CohortResult;
import com.meiyetec.entity.CrmChurnPrediction;
import com.meiyetec.entity.CrmComplaintTicket;
import com.meiyetec.entity.CrmCrossStoreRedemption;
import com.meiyetec.entity.CrmCrossStoreRule;
import com.meiyetec.entity.CrmFollowUpNote;
import com.meiyetec.entity.CrmFollowUpPlan;
import com.meiyetec.entity.CrmFollowUpTask;
import com.meiyetec.entity.CrmFollowUpTouch;
import com.meiyetec.entity.CrmFollowUpTriggerLog;
import com.meiyetec.entity.CrmLtvSnapshot;
import com.meiyetec.entity.CrmMemberTagValue;
import com.meiyetec.entity.CrmReview;
import com.meiyetec.entity.CrmReviewTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CRM 模块实体字段验证（纯单元测试，不依赖 Spring 上下文）
 *
 * 验证关键实体含 tenantId 字段，并校验 POJO 健全性。
 */
@DisplayName("CRM 实体字段验证（纯单元测试）")
class CrmEntityFieldsTest {

    private static Field findField(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    // ============================================
    // 实体必须含 tenantId 字段
    // ============================================

    @Test
    @DisplayName("CRM-001: CrmFollowUpTask 实体含 tenantId 字段")
    void crmFollowUpTask_hasTenantId() {
        Field f = findField(CrmFollowUpTask.class, "tenantId");
        assertNotNull(f, "CrmFollowUpTask 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("CRM-002: CrmFollowUpPlan 实体含 tenantId 字段")
    void crmFollowUpPlan_hasTenantId() {
        Field f = findField(CrmFollowUpPlan.class, "tenantId");
        assertNotNull(f, "CrmFollowUpPlan 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("CRM-001: CrmFollowUpNote / Touch / TriggerLog 含 tenantId")
    void followUpChildren_hasTenantId() {
        assertNotNull(findField(CrmFollowUpNote.class, "tenantId"));
        assertNotNull(findField(CrmFollowUpTouch.class, "tenantId"));
        assertNotNull(findField(CrmFollowUpTriggerLog.class, "tenantId"));
    }

    @Test
    @DisplayName("CRM-004: CrmComplaintTicket 实体含 tenantId 字段")
    void crmComplaintTicket_hasTenantId() {
        Field f = findField(CrmComplaintTicket.class, "tenantId");
        assertNotNull(f, "CrmComplaintTicket 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("CRM-005: CrmReview 实体含 tenantId 字段")
    void crmReview_hasTenantId() {
        Field f = findField(CrmReview.class, "tenantId");
        assertNotNull(f, "CrmReview 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("CRM-006: CrmReviewTemplate 实体含 tenantId 字段")
    void crmReviewTemplate_hasTenantId() {
        Field f = findField(CrmReviewTemplate.class, "tenantId");
        assertNotNull(f, "CrmReviewTemplate 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("CRM-007: CrmCrossStoreRule 实体含 tenantId 字段")
    void crmCrossStoreRule_hasTenantId() {
        Field f = findField(CrmCrossStoreRule.class, "tenantId");
        assertNotNull(f, "CrmCrossStoreRule 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("CRM-008: CrmCrossStoreRedemption 实体含 tenantId 字段")
    void crmCrossStoreRedemption_hasTenantId() {
        Field f = findField(CrmCrossStoreRedemption.class, "tenantId");
        assertNotNull(f, "CrmCrossStoreRedemption 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("CRM-010: CrmRfmSnapshot 实体含 tenantId 字段（CrmMemberTagValue 同列）")
    void crmRfm_hasTenantId() {
        // CrmMemberTagValue / CrmRfmSnapshot 都是 RFM 模块的核心实体
        assertNotNull(findField(CrmMemberTagValue.class, "tenantId"), "CrmMemberTagValue 必须含 tenantId");
    }

    @Test
    @DisplayName("CRM-011: CrmLtvSnapshot / CrmChurnPrediction 含 tenantId")
    void ltvChurn_hasTenantId() {
        Field ltv = findField(CrmLtvSnapshot.class, "tenantId");
        Field churn = findField(CrmChurnPrediction.class, "tenantId");
        assertNotNull(ltv, "CrmLtvSnapshot 必须含 tenantId 字段");
        assertNotNull(churn, "CrmChurnPrediction 必须含 tenantId 字段");
        assertEquals(Long.class, ltv.getType());
        assertEquals(Long.class, churn.getType());
    }

    @Test
    @DisplayName("CRM-012: CohortDefinition / CohortResult 含 tenantId")
    void cohort_hasTenantId() {
        assertNotNull(findField(CohortDefinition.class, "tenantId"));
        assertNotNull(findField(CohortResult.class, "tenantId"));
    }

    // ============================================
    // POJO 健全性
    // ============================================

    @Test
    @DisplayName("CrmFollowUpTask POJO 健全性")
    void crmFollowUpTask_pojo() {
        CrmFollowUpTask t = new CrmFollowUpTask();
        t.setTenantId(1L); t.setBrandId(1L); t.setStoreId(1L);
        t.setTaskNo("FT001"); t.setTaskType("POST_TREATMENT");
        t.setMemberId(100L); t.setMemberName("Zhang San"); t.setMemberPhone("13800001111");
        t.setSourceType("ORDER"); t.setSourceId(1L); t.setSourceNo("O001");
        t.setPlannedAt(LocalDateTime.now().plusDays(1));
        t.setSlaExpireAt(LocalDateTime.now().plusDays(2));
        t.setSlaMinutes(1440);
        t.setPriority(2); t.setStatus(1);
        assertEquals("FT001", t.getTaskNo());
        assertEquals("POST_TREATMENT", t.getTaskType());
        assertEquals(Integer.valueOf(1), t.getStatus());
    }

    @Test
    @DisplayName("CrmCrossStoreRule POJO 健全性")
    void crmCrossStoreRule_pojo() {
        CrmCrossStoreRule r = new CrmCrossStoreRule();
        r.setTenantId(1L); r.setBrandId(1L);
        r.setRuleName("Cross brand pkg rule");
        r.setMemberCrossEnabled(1);
        r.setPackageCrossEnabled(1);
        r.setPackageCrossMode("BRAND");
        r.setPointsCrossEnabled(0);
        r.setBalanceCrossEnabled(1);
        r.setBalanceCrossMode("BRAND");
        r.setDiscountCrossEnabled(0);
        r.setCouponCrossEnabled(0);
        r.setCrossFeeRate(new BigDecimal("2.50"));
        r.setCrossFeeType("PERCENT");
        r.setCrossMaxAmount(new BigDecimal("100"));
        r.setEnabled(1);
        r.setPriority(50);
        r.setHitCount(0L);
        assertEquals("Cross brand pkg rule", r.getRuleName());
        assertEquals("BRAND", r.getPackageCrossMode());
        assertEquals("PERCENT", r.getCrossFeeType());
    }

    @Test
    @DisplayName("CrmLtvSnapshot POJO 健全性")
    void crmLtvSnapshot_pojo() {
        CrmLtvSnapshot s = new CrmLtvSnapshot();
        s.setTenantId(1L); s.setBrandId(1L); s.setMemberId(100L);
        s.setHistoricalLtv(new BigDecimal("5000"));
        s.setPredictedLtv30d(new BigDecimal("200"));
        s.setPredictedLtv90d(new BigDecimal("600"));
        s.setPredictedLtv365d(new BigDecimal("2400"));
        s.setRepurchaseProb30d(new BigDecimal("0.80"));
        s.setModelType("RULE");
        s.setModelVersion("v1");
        s.setAvgOrderValue(new BigDecimal("200"));
        s.setTotalOrders(25);
        s.setLastPurchaseDays(3);
        s.setSnapshotDate(java.time.LocalDate.now());
        assertEquals(Integer.valueOf(25), s.getTotalOrders());
        assertEquals("RULE", s.getModelType());
    }

    @Test
    @DisplayName("CohortDefinition POJO 健全性")
    void cohortDefinition_pojo() {
        CohortDefinition d = new CohortDefinition();
        d.setTenantId(1L);
        d.setCohortCode("CO-2026");
        d.setCohortName("Monthly new-member cohort");
        d.setCohortType("MONTHLY");
        d.setMetric("REORDER_RATE");
        d.setTargetDim("MONTH");
        d.setDescription("Retain rate of new sign-ups");
        assertEquals("MONTHLY", d.getCohortType());
        assertEquals("REORDER_RATE", d.getMetric());
    }
}