package com.meiyetec.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXT-19：多语言模板解析器单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>locale 归一化（zh_CN → zh-CN，lang 小写 / country 大写）</li>
 *   <li>locale 回退链（zh-TW → zh-CN → en-US）</li>
 *   <li>3 种 locale 模板加载（zh-CN / en-US / ja-JP）</li>
 *   <li>占位符 ${} 渲染</li>
 *   <li>未注册 locale → fallback 到 en-US</li>
 *   <li>空 / null locale → fallback 到 en-US</li>
 * </ul>
 */
@DisplayName("EXT-19 Booking Confirmation Template Resolver")
class BookingConfirmationTemplateResolverTest {

    private BookingConfirmationTemplateResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        resolver = new BookingConfirmationTemplateResolver();
        // 反射注入 ObjectMapper（@Autowired(required=false) 留空时也不影响）
        java.lang.reflect.Field f = BookingConfirmationTemplateResolver.class.getDeclaredField("jackson");
        f.setAccessible(true);
        f.set(resolver, new ObjectMapper());
        resolver.warmup();
    }

    // ==================== locale 归一化 ====================

    @Test
    @DisplayName("normalize: zh_CN → zh-CN，lang 小写 / country 大写")
    void normalizeCases() {
        assertEquals("zh-cn", BookingConfirmationTemplateResolver.normalize("zh_CN").toLowerCase());
        assertEquals("zh-CN", BookingConfirmationTemplateResolver.normalize("zh_CN"));
        assertEquals("EN-US", BookingConfirmationTemplateResolver.normalize("EN_us"));
        assertEquals("ja-jp", BookingConfirmationTemplateResolver.normalize("JA-JP").toLowerCase());
        assertEquals("ja", BookingConfirmationTemplateResolver.normalize("ja"));
        assertNull(BookingConfirmationTemplateResolver.normalize(null));
        assertNull(BookingConfirmationTemplateResolver.normalize(""));
        assertEquals("  ", BookingConfirmationTemplateResolver.normalize("  ").trim(),
                "whitespace only → after trim → null check");
    }

    // ==================== locale 回退 ====================

    @Test
    @DisplayName("回退：完全匹配 zh-CN 不需要 fallback")
    void fallbackExactMatch() {
        assertEquals("zh-CN", resolver.resolveWithFallback("zh-CN"));
    }

    @Test
    @DisplayName("回退：zh-TW 没有时回到 zh-CN（同语言家族）")
    void fallbackLangFamily() {
        // zh-TW 不存在，应该在 zh-CN/zh 前找到匹配
        String resolved = resolver.resolveWithFallback("zh-TW");
        assertTrue(resolved.equals("zh-CN") || resolved.equals("zh"),
                "zh-TW 应回退到 zh-CN 或 zh，实际：" + resolved);
    }

    @Test
    @DisplayName("回退：未注册 locale → en-US 默认")
    void fallbackToDefault() {
        String resolved = resolver.resolveWithFallback("ko-KR");   // 韩文不存在
        assertEquals("en-US", resolved);
    }

    @Test
    @DisplayName("回退：null locale → en-US 默认")
    void fallbackNullLocale() {
        assertEquals("en-US", resolver.resolveWithFallback(null));
        assertEquals("en-US", resolver.resolveWithFallback(""));
    }

    // ==================== 模板加载 + 渲染 ====================

    @Test
    @DisplayName("渲染：zh-CN 模板 + 占位符替换")
    void resolveZhCn() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("customerName", "张总");
        vars.put("serviceName", "焕颜保湿面膜");
        vars.put("bookingDate", "2026-09-10");
        vars.put("bookingTime", "14:30");
        vars.put("bookingNo", "BK-001");
        BookingConfirmationTemplate tpl = resolver.resolve("zh-CN", vars);

        assertEquals("zh-CN", tpl.getResolvedLocale());
        assertFalse(tpl.getUsedFallback());
        assertTrue(tpl.getSubject().contains("已确认"));
        assertTrue(tpl.getSms().contains("张总"));
        assertTrue(tpl.getSms().contains("焕颜保湿面膜"));
        assertTrue(tpl.getSms().contains("BK-001"));
        assertTrue(tpl.getEmailBody().contains("张总"));
        assertTrue(tpl.getEmailSubject().contains("BK-001"));

        // miniProgram：keyword1..N 全部渲染
        assertNotNull(tpl.getMiniProgram());
        assertTrue(tpl.getMiniProgram().containsKey("keyword1"));
        assertTrue(tpl.getMiniProgram().get("keyword1").contains("BK-001"));
    }

    @Test
    @DisplayName("渲染：en-US 模板（英文文案 + ISO 日期格式）")
    void resolveEnUs() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("customerName", "Alice");
        vars.put("serviceName", "Facial Treatment");
        vars.put("bookingDate", "2026-09-10");
        vars.put("bookingTime", "14:30");
        vars.put("bookingNo", "BK-002");
        BookingConfirmationTemplate tpl = resolver.resolve("en-US", vars);

        assertEquals("en-US", tpl.getResolvedLocale());
        assertFalse(tpl.getUsedFallback());
        assertTrue(tpl.getSubject().contains("Confirmed"));
        assertTrue(tpl.getSms().contains("Alice"));
        assertTrue(tpl.getSms().contains("Facial Treatment"));
        assertTrue(tpl.getSms().contains("BK-002"));
    }

    @Test
    @DisplayName("渲染：ja-JP 模板（含日文 + 数字全角空格）")
    void resolveJaJp() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("customerName", "山田さん");
        vars.put("serviceName", "フェイシャル");
        vars.put("bookingDate", "2026-09-10");
        vars.put("bookingTime", "14:30");
        vars.put("bookingNo", "BK-003");
        BookingConfirmationTemplate tpl = resolver.resolve("ja-JP", vars);

        assertEquals("ja-JP", tpl.getResolvedLocale());
        assertTrue(tpl.getSubject().contains("予約"));
        assertTrue(tpl.getSms().contains("山田さん"));
        assertTrue(tpl.getSms().contains("BK-003"));
    }

    @Test
    @DisplayName("渲染：zh-TW 模板（繁体中文）")
    void resolveZhTw() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("customerName", "陳先生");
        vars.put("serviceName", "焕顏保濕面膜");
        vars.put("bookingDate", "2026-09-10");
        vars.put("bookingTime", "14:30");
        vars.put("bookingNo", "BK-TW-001");
        BookingConfirmationTemplate tpl = resolver.resolve("zh-TW", vars);

        assertEquals("zh-TW", tpl.getResolvedLocale());
        assertFalse(tpl.getUsedFallback());
        // 繁体字
        assertTrue(tpl.getSubject().contains("預約"));
        assertTrue(tpl.getSubject().contains("已確認"));
        assertTrue(tpl.getSms().contains("陳先生"));
        assertTrue(tpl.getSms().contains("BK-TW-001"));
        assertTrue(tpl.getEmailBody().contains("您好"));
    }

    @Test
    @DisplayName("渲染：ko-KR 模板（韩文）")
    void resolveKoKr() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("customerName", "김민수");
        vars.put("serviceName", "페이셜 트리트먼트");
        vars.put("bookingDate", "2026-09-10");
        vars.put("bookingTime", "14:30");
        vars.put("bookingNo", "BK-KR-001");
        BookingConfirmationTemplate tpl = resolver.resolve("ko-KR", vars);

        assertEquals("ko-KR", tpl.getResolvedLocale());
        assertFalse(tpl.getUsedFallback());
        assertTrue(tpl.getSubject().contains("예약"));
        assertTrue(tpl.getSms().contains("김민수"));
        assertTrue(tpl.getSms().contains("BK-KR-001"));
    }

    @Test
    @DisplayName("渲染：fr-FR 模板（法文）")
    void resolveFrFr() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("customerName", "Pierre");
        vars.put("serviceName", "Soin du visage");
        vars.put("bookingDate", "2026-09-10");
        vars.put("bookingTime", "14:30");
        vars.put("bookingNo", "BK-FR-001");
        BookingConfirmationTemplate tpl = resolver.resolve("fr-FR", vars);

        assertEquals("fr-FR", tpl.getResolvedLocale());
        assertFalse(tpl.getUsedFallback());
        assertTrue(tpl.getSubject().contains("confirmée"));
        assertTrue(tpl.getSms().contains("Pierre"));
        assertTrue(tpl.getSms().contains("Soin du visage"));
        assertTrue(tpl.getEmailBody().contains("Bonjour"));
    }

    @Test
    @DisplayName("渲染：未知 locale ko-KR 触发 fallback 到 en-US")
    void resolveFallback() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("customerName", "Bob");
        BookingConfirmationTemplate tpl = resolver.resolve("ko-KR", vars);

        assertEquals("en-US", tpl.getResolvedLocale());
        assertTrue(tpl.getUsedFallback());
        assertTrue(tpl.getSubject().contains("Confirmed"));
    }

    @Test
    @DisplayName("渲染：null vars 不报错（占位符保留为空）")
    void resolveNullVars() {
        BookingConfirmationTemplate tpl = resolver.resolve("en-US", null);
        assertNotNull(tpl.getSms());
        // 占位符变成空字符串
        assertTrue(tpl.getSms().contains("Dear , your"));
    }

    @Test
    @DisplayName("hasTemplate：已注册的 locale 返回 true，未注册的返回 false")
    void hasTemplate() {
        assertTrue(resolver.hasTemplate("zh-CN"));
        assertTrue(resolver.hasTemplate("zh-TW"));
        assertTrue(resolver.hasTemplate("en-US"));
        assertTrue(resolver.hasTemplate("ja-JP"));
        assertTrue(resolver.hasTemplate("ko-KR"));
        assertTrue(resolver.hasTemplate("fr-FR"));
        assertFalse(resolver.hasTemplate("de-DE"));
    }

    @Test
    @DisplayName("availableLocales：启动期已加载的 locale 列表（≥6 个）")
    void availableLocales() {
        var list = resolver.availableLocales();
        assertTrue(list.contains("zh-CN"));
        assertTrue(list.contains("zh-TW"));
        assertTrue(list.contains("en-US"));
        assertTrue(list.contains("ja-JP"));
        assertTrue(list.contains("ko-KR"));
        assertTrue(list.contains("fr-FR"));
        assertTrue(list.size() >= 6, "至少有 6 个 locale，实际：" + list.size());
    }
}
