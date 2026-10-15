package com.meiyetec.service;

import com.meiyetec.entity.MarketActivity;
import com.meiyetec.entity.MarketingTouchConversion;
import com.meiyetec.entity.MarketingTouchLog;
import com.meiyetec.entity.MarketingTouchTask;
import com.meiyetec.entity.MktChannelConfig;
import com.meiyetec.entity.MktChannelTemplate;
import com.meiyetec.entity.MktJourney;
import com.meiyetec.entity.MktJourneyInstance;
import com.meiyetec.entity.MktPromotion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MKT 模块实体字段验证（纯单元测试）
 *
 * 验证营销 8 个核心实体含 tenantId 字段，并校验 POJO 健全性。
 * 不依赖 Spring 上下文，秒级运行。
 */
@DisplayName("MKT 实体字段验证（纯单元测试）")
class MktEntityFieldsTest {

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
    // 实体必须含 tenantId 字段（防止 MKT-001~010 跨租户）
    // ============================================

    @Test
    @DisplayName("MKT-001~005: MarketActivity 实体含 tenantId 字段")
    void marketActivity_hasTenantId() {
        Field f = findField(MarketActivity.class, "tenantId");
        assertNotNull(f, "MarketActivity 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("MKT-001~005: MktPromotion 实体含 tenantId 字段")
    void mktPromotion_hasTenantId() {
        Field f = findField(MktPromotion.class, "tenantId");
        assertNotNull(f, "MktPromotion 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("MKT-007: MktChannelConfig 实体含 tenantId 字段")
    void mktChannelConfig_hasTenantId() {
        Field f = findField(MktChannelConfig.class, "tenantId");
        assertNotNull(f, "MktChannelConfig 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("MKT-008: MktChannelTemplate 实体含 tenantId 字段")
    void mktChannelTemplate_hasTenantId() {
        Field f = findField(MktChannelTemplate.class, "tenantId");
        assertNotNull(f, "MktChannelTemplate 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("MKT-009: MarketingTouchTask 实体含 tenantId 字段")
    void marketingTouchTask_hasTenantId() {
        Field f = findField(MarketingTouchTask.class, "tenantId");
        assertNotNull(f, "MarketingTouchTask 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("MKT-009: MarketingTouchLog 实体含 tenantId 字段")
    void marketingTouchLog_hasTenantId() {
        Field f = findField(MarketingTouchLog.class, "tenantId");
        assertNotNull(f, "MarketingTouchLog 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("MKT-010: MktJourney 实体含 tenantId 字段")
    void mktJourney_hasTenantId() {
        Field f = findField(MktJourney.class, "tenantId");
        assertNotNull(f, "MktJourney 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("MKT-010: MktJourneyInstance 实体含 tenantId 字段")
    void mktJourneyInstance_hasTenantId() {
        Field f = findField(MktJourneyInstance.class, "tenantId");
        assertNotNull(f, "MktJourneyInstance 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("MKT-009: MarketingTouchConversion 实体含 tenantId 字段")
    void marketingTouchConversion_hasTenantId() {
        Field f = findField(MarketingTouchConversion.class, "tenantId");
        assertNotNull(f, "MarketingTouchConversion 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    // ============================================
    // POJO 健全性（关键 setter/getter）
    // ============================================

    @Test
    @DisplayName("MarketActivity POJO 健全性")
    void marketActivity_pojo() {
        MarketActivity a = new MarketActivity();
        a.setTenantId(1L); a.setStoreId(1L);
        a.setName("双十一活动"); a.setType(1); a.setStatus(1);
        a.setStartTime(LocalDateTime.now()); a.setEndTime(LocalDateTime.now().plusDays(7));
        a.setRuleConfig("{\"threshold\":100}");
        assertEquals("双十一活动", a.getName());
        assertEquals(Integer.valueOf(1), a.getType());
    }

    @Test
    @DisplayName("MktPromotion POJO 健全性")
    void mktPromotion_pojo() {
        MktPromotion p = new MktPromotion();
        p.setTenantId(1L); p.setStoreId(1L);
        p.setPromotionCode("P20260101"); p.setPromotionName("满100减20");
        p.setPromotionType("FULL_REDUCE");
        p.setThresholdAmount(new BigDecimal("100"));
        p.setReduceAmount(new BigDecimal("20"));
        p.setDiscountRate(new BigDecimal("0.85"));
        p.setScopeType("ALL"); p.setStackable(0);
        p.setQuotaTotal(1000); p.setQuotaUsed(0);
        p.setPerMemberLimit(1); p.setStatus(1); p.setPriority(10);
        assertEquals("满100减20", p.getPromotionName());
        assertEquals("FULL_REDUCE", p.getPromotionType());
        assertEquals(0, new BigDecimal("100").compareTo(p.getThresholdAmount()));
    }

    @Test
    @DisplayName("MktChannelConfig POJO 健全性")
    void mktChannelConfig_pojo() {
        MktChannelConfig c = new MktChannelConfig();
        c.setTenantId(1L); c.setChannelType("SMS");
        c.setChannelName("短信通道-A");
        c.setProvider("Aliyun"); c.setConfigJson("{\"ak\":\"test\"}");
        c.setRateLimitPerMin(60); c.setRateLimitPerDay(5000);
        c.setStatus(1); c.setRetryMax(3); c.setRetryIntervalSec(60);
        assertEquals("短信通道-A", c.getChannelName());
        assertEquals("Aliyun", c.getProvider());
        assertEquals(Integer.valueOf(60), c.getRateLimitPerMin());
    }

    @Test
    @DisplayName("MktChannelTemplate POJO 健全性")
    void mktChannelTemplate_pojo() {
        MktChannelTemplate t = new MktChannelTemplate();
        t.setTenantId(1L); t.setChannelType("MINI_PROGRAM");
        t.setTemplateCode("BIRTHDAY01"); t.setTemplateName("生日祝福");
        t.setSubject("亲爱的 ${name}"); t.setContent("尊敬的 ${name}，祝您生日快乐！");
        t.setVariables("${name},${date}");
        t.setScene("BIRTHDAY"); t.setAuditStatus(2); t.setEnabled(1);
        assertEquals("生日祝福", t.getTemplateName());
        assertEquals(Integer.valueOf(2), t.getAuditStatus());
    }

    @Test
    @DisplayName("MarketingTouchTask POJO 健全性")
    void marketingTouchTask_pojo() {
        MarketingTouchTask tk = new MarketingTouchTask();
        tk.setTenantId(1L); tk.setBrandId(1L); tk.setStoreId(1L);
        tk.setTaskNo("TK20260101"); tk.setSourceType("MANUAL");
        tk.setCampaignName("双十一群发"); tk.setChannelType("SMS");
        tk.setTemplateId(100L); tk.setTriggerEvent("ORDER_PAID");
        tk.setTriggerOffsetMinutes(60);
        tk.setScheduledAt(LocalDateTime.now().plusHours(1));
        tk.setStatus(1); tk.setTargetCount(500);
        tk.setSuccessCount(0); tk.setFailCount(0);
        assertEquals("双十一群发", tk.getCampaignName());
        assertEquals(Integer.valueOf(500), tk.getTargetCount());
    }

    @Test
    @DisplayName("MktJourney POJO 健全性")
    void mktJourney_pojo() {
        MktJourney j = new MktJourney();
        j.setTenantId(1L); j.setBrandId(1L);
        j.setJourneyCode("JR2026_NEW"); j.setJourneyName("新会员引导旅程");
        j.setJourneyType("ONBOARD"); j.setDescription("新会员 30 天内完成 5 项动作");
        j.setNodesJson("[{\"id\":\"n1\",\"type\":\"send_msg\"}]");
        j.setEdgesJson("[{\"from\":\"n1\",\"to\":\"n2\"}]");
        j.setStatus(1); j.setEnterCount(0); j.setCompleteCount(0); j.setExitCount(0);
        assertEquals("新会员引导旅程", j.getJourneyName());
        assertEquals("ONBOARD", j.getJourneyType());
    }
}