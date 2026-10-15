package com.meiyetec.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.service.channel.SmsSender;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EXT-19：预约确认模板解析器
 *
 * <p>职责：
 * <ol>
 *   <li>从 classpath {@code i18n/template/{locale}.json} 加载模板（启动期 + 懒加载）</li>
 *   <li>locale 回退链：{@code xx-YY → xx → en}</li>
 *   <li>复用 {@link SmsSender#render(String, Map)} 占位符替换</li>
 * </ol>
 *
 * <p>支持的 locale：{@code zh-CN / en-US / ja-JP}（生产可扩展更多）。
 */
@Slf4j
@Component
public class BookingConfirmationTemplateResolver {

    /** classpath 模板目录 */
    private static final String CLASSPATH_DIR = "i18n/template/";
    /** 启动期预热 */
    private static final List<String> WARMUP_LOCALES = List.of(
            "zh-CN", "zh-TW", "en-US", "ja-JP", "ko-KR", "fr-FR");
    /** 默认 fallback locale */
    public static final String DEFAULT_LOCALE = "en-US";

    @Autowired(required = false)
    private ObjectMapper jackson;

    /** locale → 原始 JSON 缓存（未渲染） */
    private final Map<String, JsonNode> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void warmup() {
        if (jackson == null) return;
        for (String l : WARMUP_LOCALES) {
            try { loadLocale(l); } catch (Exception e) {
                log.warn("[I18N-TPL] warmup {} failed: {}", l, e.getMessage());
            }
        }
        log.info("[I18N-TPL] warmup done, locales={}", WARMUP_LOCALES);
    }

    // ============================================================
    //  对外 API
    // ============================================================

    /**
     * 根据 booking.customerLocale 解析 + 渲染
     *
     * @param localeRaw 原始 locale（可空；空时走默认 zh-CN）
     * @param vars      占位符变量（customerName / serviceName / bookingDate / bookingTime / bookingNo / storeName / storePhone）
     */
    public BookingConfirmationTemplate resolve(String localeRaw, Map<String, Object> vars) {
        if (vars == null) vars = Map.of();
        // 1. locale 归一化 + 回退
        String requested = normalize(localeRaw);
        String resolved = resolveWithFallback(requested);
        JsonNode node = loadLocale(resolved);
        if (node == null) {
            log.warn("[I18N-TPL] template not found for locale={}, using default={}", requested, DEFAULT_LOCALE);
            node = loadLocale(DEFAULT_LOCALE);
            resolved = DEFAULT_LOCALE;
        }
        boolean usedFallback = !resolved.equalsIgnoreCase(requested);

        // 2. 渲染
        BookingConfirmationTemplate out = new BookingConfirmationTemplate();
        out.setResolvedLocale(resolved);
        out.setUsedFallback(usedFallback);
        String subject = text(node, "subject");
        out.setSubject(SmsSender.render(subject, vars));
        out.setSms(SmsSender.render(text(node, "sms"), vars));

        JsonNode mp = node.path("miniProgram");
        Map<String, String> kw = new LinkedHashMap<>();
        if (mp.isObject()) {
            out.setMiniProgramTitle(SmsSender.render(text(mp, "title"), vars));
            Iterator<Map.Entry<String, JsonNode>> it = mp.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = e.getKey();
                if ("title".equals(key)) continue;   // 跳过 title（已单独渲染）
                kw.put(key, SmsSender.render(e.getValue().asText(""), vars));
            }
        }
        out.setMiniProgram(kw);

        JsonNode email = node.path("email");
        if (email.isObject()) {
            out.setEmailSubject(SmsSender.render(text(email, "subject"), vars));
            out.setEmailBody(SmsSender.render(text(email, "body"), vars));
        } else {
            // 邮件主题回退到顶层 subject
            out.setEmailSubject(out.getSubject());
            out.setEmailBody("");
        }
        return out;
    }

    /** 检查指定 locale 是否存在模板（用于诊断 / 前端下拉） */
    public boolean hasTemplate(String localeRaw) {
        return loadLocale(normalize(localeRaw)) != null;
    }

    /** 列出所有可用 locale（启动期已加载 + 缓存中） */
    public List<String> availableLocales() {
        List<String> out = new ArrayList<>(cache.keySet());
        Collections.sort(out);
        return out;
    }

    // ============================================================
    //  内部：加载 + locale 回退
    // ============================================================

    /** 从 classpath 加载（带缓存） */
    JsonNode loadLocale(String locale) {
        if (locale == null) return null;
        JsonNode hit = cache.get(locale);
        if (hit != null) return hit;
        String path = CLASSPATH_DIR + locale + ".json";
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            JsonNode node = jackson.readTree(in);
            cache.put(locale, node);
            return node;
        } catch (IOException e) {
            log.warn("[I18N-TPL] load {} failed: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Locale 回退链：{@code zh-TW → zh-CN → zh → en-US → en}
     * 1. 完全匹配（zh-CN）
     * 2. 同语言家族：lang 前缀
     * 3. 默认（en-US）
     */
    String resolveWithFallback(String requested) {
        if (requested == null || requested.isEmpty()) return DEFAULT_LOCALE;
        if (cache.containsKey(requested) || loadLocale(requested) != null) return requested;
        String lang = requested.contains("-") ? requested.substring(0, requested.indexOf('-')) : requested;
        // 尝试语言家族候选：xx-XX → xx → en-US
        for (String candidate : new String[]{
                lang + "-CN", lang + "-US", lang + "-JP", lang}) {
            if (cache.containsKey(candidate) || loadLocale(candidate) != null) return candidate;
        }
        return DEFAULT_LOCALE;
    }

    /** 归一化：trim + 小写 lang + 大写 country */
    static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.contains("-") || s.contains("_")) {
            String[] parts = s.split("[-_]");
            String lang = parts[0].toLowerCase();
            String country = parts.length > 1 ? parts[1].toUpperCase() : "";
            return country.isEmpty() ? lang : lang + "-" + country;
        }
        return s.toLowerCase();
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return "";
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }
}
