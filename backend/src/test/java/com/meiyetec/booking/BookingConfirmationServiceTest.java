package com.meiyetec.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.OverseasBooking;
import com.meiyetec.service.channel.ChannelSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * EXT-19：预约确认发送服务单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code sendConfirmation} 按可用渠道分流：phone → SMS，email → EMAIL，openId → MINI_PROGRAM</li>
 *   <li>任一渠道成功即 overallSuccess=true</li>
 *   <li>无可用渠道时返回失败但不抛异常</li>
 *   <li>ChannelSender 不存在 → 该渠道 fail + 不影响其他渠道</li>
 *   <li>null booking 安全返回</li>
 *   <li>门店信息从 StoreLookupService 读取（用 mock 注入）</li>
 * </ul>
 */
@DisplayName("EXT-19 Booking Confirmation Service (with MiniProgram + Store)")
class BookingConfirmationServiceTest {

    private BookingConfirmationService svc;
    private BookingConfirmationTemplateResolver resolver;
    private StoreLookupService storeLookupMock;

    /** 测试用 SMS 桩 */
    static class StubSmsSender implements ChannelSender {
        @Override public String channelType() { return "SMS"; }
        @Override public boolean healthCheck() { return true; }
        @Override public SendResult send(com.meiyetec.entity.MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
            return SendResult.ok("STUB-SMS-" + receiver);
        }
    }

    /** 测试用 EMAIL 桩 */
    static class StubEmailSender implements ChannelSender {
        @Override public String channelType() { return "EMAIL"; }
        @Override public boolean healthCheck() { return true; }
        @Override public SendResult send(com.meiyetec.entity.MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
            if (receiver == null || !receiver.contains("@")) return SendResult.fail("invalid email");
            return SendResult.ok("STUB-EMAIL-" + receiver);
        }
    }

    /** 测试用 MINI_PROGRAM 桩 */
    static class StubMiniProgramSender implements ChannelSender {
        @Override public String channelType() { return "MINI_PROGRAM"; }
        @Override public boolean healthCheck() { return true; }
        @Override public SendResult send(com.meiyetec.entity.MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
            // 校验：构造的 MktChannelTemplate 应包含正确字段
            if (tpl == null || tpl.getExternalTemplateId() == null || tpl.getVariables() == null) {
                return SendResult.fail("invalid mp template");
            }
            if (receiver == null || receiver.isEmpty()) return SendResult.fail("empty openId");
            return SendResult.ok("STUB-MP-" + receiver);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        resolver = new BookingConfirmationTemplateResolver();
        java.lang.reflect.Field f = BookingConfirmationTemplateResolver.class.getDeclaredField("jackson");
        f.setAccessible(true);
        f.set(resolver, new ObjectMapper());
        resolver.warmup();

        // 用 Mockito 模拟 StoreLookupService（不连真实 DB）
        storeLookupMock = Mockito.mock(StoreLookupService.class);
        StoreLookupService.StoreInfo fakeStore = new StoreLookupService.StoreInfo(
                100L, "美业科技旗舰店", "010-12345678");
        when(storeLookupMock.lookup(anyLong())).thenReturn(fakeStore);
        when(storeLookupMock.lookup((Long) null)).thenReturn(null);

        svc = new BookingConfirmationService(resolver, storeLookupMock);
        java.lang.reflect.Field sf = BookingConfirmationService.class.getDeclaredField("channelSenders");
        sf.setAccessible(true);
        sf.set(svc, List.<ChannelSender>of(new StubSmsSender(), new StubEmailSender(), new StubMiniProgramSender()));
    }

    private OverseasBooking sampleBooking(String locale, String phone, String email, String openId) {
        OverseasBooking b = new OverseasBooking();
        b.setId(1L);
        b.setStoreId(100L);
        b.setBookingNo("BK-TEST-001");
        b.setCustomerName("Alice");
        b.setCustomerPhone(phone);
        b.setCustomerEmail(email);
        b.setMiniProgramOpenId(openId);
        b.setCustomerLocale(locale);
        b.setServiceName("Facial Treatment");
        b.setBookingDate(java.time.LocalDate.of(2026, 9, 10));
        b.setBookingTime("14:30");
        return b;
    }

    // ==================== sendConfirmation 路由 ====================

    @Test
    @DisplayName("路由：phone + email + openId 都有 → 3 渠道全部发送")
    void sendAllChannels() {
        OverseasBooking b = sampleBooking("en-US", "+1-555-1234", "alice@example.com",
                "oXyz123ABC");
        BookingConfirmationResult r = svc.sendConfirmation(b);
        assertTrue(r.isOverallSuccess());
        assertTrue(r.getChannelResults().get("SMS").get("success").equals(true));
        assertTrue(r.getChannelResults().get("EMAIL").get("success").equals(true));
        assertTrue(r.getChannelResults().get("MINI_PROGRAM").get("success").equals(true));
        assertEquals("STUB-MP-oXyz123ABC", r.getChannelResults().get("MINI_PROGRAM").get("externalMsgId"));
    }

    @Test
    @DisplayName("路由：仅 openId → 只走 MINI_PROGRAM，SMS/EMAIL 标 skipped")
    void sendMiniProgramOnly() {
        OverseasBooking b = sampleBooking("ja-JP", null, null, "oXyz123ABC");
        BookingConfirmationResult r = svc.sendConfirmation(b);
        assertTrue(r.isOverallSuccess());
        assertTrue(r.getChannelResults().get("MINI_PROGRAM").get("success").equals(true));
        assertTrue(r.getChannelResults().get("SMS").get("errorMsg").toString().contains("skipped"));
        assertTrue(r.getChannelResults().get("EMAIL").get("errorMsg").toString().contains("skipped"));
    }

    @Test
    @DisplayName("路由：phone + email 同时 → 2 渠道 + MINI_PROGRAM skipped")
    void sendPhoneAndEmail() {
        OverseasBooking b = sampleBooking("zh-CN", "+1-555-1234", "alice@example.com", null);
        BookingConfirmationResult r = svc.sendConfirmation(b);
        assertTrue(r.isOverallSuccess());
        assertTrue(r.getChannelResults().get("SMS").get("success").equals(true));
        assertTrue(r.getChannelResults().get("EMAIL").get("success").equals(true));
        assertTrue(r.getChannelResults().get("MINI_PROGRAM").get("errorMsg").toString().contains("skipped"));
    }

    @Test
    @DisplayName("路由：无可用渠道 → overallSuccess=false")
    void sendNoChannels() {
        OverseasBooking b = sampleBooking("en-US", null, null, null);
        BookingConfirmationResult r = svc.sendConfirmation(b);
        assertFalse(r.isOverallSuccess());
        assertEquals(false, r.getChannelResults().get("SMS").get("success"));
        assertEquals(false, r.getChannelResults().get("EMAIL").get("success"));
        assertEquals(false, r.getChannelResults().get("MINI_PROGRAM").get("success"));
    }

    @Test
    @DisplayName("容错：null booking → overallSuccess=false + 不抛异常")
    void sendNullBooking() {
        BookingConfirmationResult r = svc.sendConfirmation(null);
        assertNotNull(r);
        assertFalse(r.isOverallSuccess());
    }

    @Test
    @DisplayName("门店：buildVars 从 StoreLookupService 读取 storeName / storePhone")
    void buildVarsUsesStoreLookup() {
        OverseasBooking b = sampleBooking("zh-CN", "+1-555-1234", null, null);
        b.setStoreId(100L);
        BookingConfirmationTemplate tpl = svc.preview(b);
        assertTrue(tpl.getSms().contains("美业科技旗舰店"),
                "SMS 应包含门店名（来自 StoreLookupService），实际：" + tpl.getSms());
        assertTrue(tpl.getMiniProgram().get("keyword4").contains("美业科技旗舰店"));
    }

    @Test
    @DisplayName("门店：storeId 为 null → 使用 fallback 常量")
    void buildVarsFallbackWhenNoStore() {
        OverseasBooking b = sampleBooking("zh-CN", "+1-555-1234", null, null);
        b.setStoreId(null);
        when(storeLookupMock.lookup((Long) null)).thenReturn(null);
        BookingConfirmationTemplate tpl = svc.preview(b);
        // fallback 常量
        assertTrue(tpl.getSms().contains("美业旗舰店"), "应为 fallback 门店名");
        assertTrue(tpl.getSms().contains("400-888-8888"), "应为 fallback 门店电话");
    }

    @Test
    @DisplayName("门店：storeId 有但查不到 → 使用 fallback 常量")
    void buildVarsFallbackWhenStoreNotFound() {
        OverseasBooking b = sampleBooking("zh-CN", "+1-555-1234", null, null);
        b.setStoreId(999L);
        when(storeLookupMock.lookup(999L)).thenReturn(null);
        BookingConfirmationTemplate tpl = svc.preview(b);
        assertTrue(tpl.getSms().contains("美业旗舰店"));
    }

    @Test
    @DisplayName("容错：MINI_PROGRAM 桩不存在 → SMS 仍成功，overallSuccess=true")
    void miniProgramMissingButSmsOk() throws Exception {
        // 移除 MINI_PROGRAM 桩
        java.lang.reflect.Field sf = BookingConfirmationService.class.getDeclaredField("channelSenders");
        sf.setAccessible(true);
        sf.set(svc, List.<ChannelSender>of(new StubSmsSender(), new StubEmailSender()));

        OverseasBooking b = sampleBooking("en-US", "+1-555-1234", null, "oXyz");
        BookingConfirmationResult r = svc.sendConfirmation(b);
        assertTrue(r.isOverallSuccess(), "SMS 成功即 overallSuccess=true");
        assertEquals(false, r.getChannelResults().get("MINI_PROGRAM").get("success"));
        assertTrue(r.getChannelResults().get("MINI_PROGRAM").get("errorMsg").toString().contains("skipped"));
    }

    @Test
    @DisplayName("locale：null → 走 fallback 到 en-US")
    void sendNullLocale() {
        OverseasBooking b = sampleBooking(null, "+1-555-1234", null, null);
        BookingConfirmationResult r = svc.sendConfirmation(b);
        assertEquals("en-US", r.getResolvedLocale());
        assertTrue(r.getUsedFallback());
        assertTrue(r.isOverallSuccess());
    }

    @Test
    @DisplayName("preview：渲染模板但不动 ChannelSender")
    void previewRendersTemplate() {
        OverseasBooking b = sampleBooking("zh-CN", "+1-555-1234", "alice@example.com", null);
        BookingConfirmationTemplate tpl = svc.preview(b);
        assertEquals("zh-CN", tpl.getResolvedLocale());
        assertTrue(tpl.getSms().contains("Alice"));
        assertTrue(tpl.getSms().contains("BK-TEST-001"));
    }
}
