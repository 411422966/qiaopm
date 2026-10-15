// 5B.12-5B.13 BookingConfirmationTemplateResolver — 带 Spring-less 手动注 Jackson
import com.meiyetec.booking.BookingConfirmationTemplateResolver;
import com.meiyetec.booking.BookingConfirmationTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

BookingConfirmationTemplateResolver resolver = new BookingConfirmationTemplateResolver();

// 手动注入 ObjectMapper（resolver 无 Spring Context，jackson=null）
ObjectMapper om = new ObjectMapper();
Field f = BookingConfirmationTemplateResolver.class.getDeclaredField("jackson");
f.setAccessible(true);
f.set(resolver, om);

// 5B.12 warmup
resolver.warmup();
System.out.println("5B.12 warmup() called");

// 5B.12 availableLocales
List<String> locales = resolver.availableLocales();
System.out.println("5B.12 availableLocales size = " + locales.size());
System.out.println("5B.12 locales = " + locales);

// 5B.12 loadLocale("zh-CN") → 4 段
BookingConfirmationTemplate tplCN = resolver.resolve("zh-CN", new HashMap<>());
System.out.println("5B.12 zh-CN resolved:");
System.out.println("  - resolvedLocale=" + tplCN.getResolvedLocale());
System.out.println("  - usedFallback=" + tplCN.getUsedFallback());
System.out.println("  - subject=" + (tplCN.getSubject() != null ? tplCN.getSubject().substring(0, Math.min(40, tplCN.getSubject().length())) : "null"));
System.out.println("  - sms=" + (tplCN.getSms() != null ? tplCN.getSms().length() + " chars" : "null"));
System.out.println("  - miniProgram=" + (tplCN.getMiniProgram() != null ? tplCN.getMiniProgram().size() + " keys" : "null"));
System.out.println("  - emailSubject=" + (tplCN.getEmailSubject() != null ? "set" : "null"));

// 5B.13 Case1: xx-YY → fallback → en-US
Map<String, Object> vars = new HashMap<>();
vars.put("customerName", "Test");
vars.put("serviceName", "Haircut");
vars.put("bookingDate", "2026-10-01");
vars.put("bookingTime", "14:00");
vars.put("bookingNo", "BK-001");
vars.put("storeName", "Beauty Shop");
vars.put("storePhone", "400-8888888");

BookingConfirmationTemplate tplFallback = resolver.resolve("xx-YY", vars);
System.out.println("5B.13 Case1 xx-YY → fallback:");
System.out.println("  - resolvedLocale=" + tplFallback.getResolvedLocale());
System.out.println("  - usedFallback=" + tplFallback.getUsedFallback());
System.out.println("  - subject=" + (tplFallback.getSubject() != null ? "set" : "null"));

// 5B.13 Case2: null → DEFAULT_LOCALE = en-US
BookingConfirmationTemplate tplNull = resolver.resolve(null, vars);
System.out.println("5B.13 Case2 null → DEFAULT_LOCALE:");
System.out.println("  - resolvedLocale=" + tplNull.getResolvedLocale());
System.out.println("  - usedFallback=" + tplNull.getUsedFallback());

// 5B.13 Case3: zh_CN → normalize → zh-CN
BookingConfirmationTemplate tplZhCN = resolver.resolve("zh_CN", vars);
System.out.println("5B.13 Case3 zh_CN normalize:");
System.out.println("  - resolvedLocale=" + tplZhCN.getResolvedLocale());
System.out.println("  - usedFallback=" + tplZhCN.getUsedFallback());

// 5B.13 Case4: pt-BR → fallback → en-US
BookingConfirmationTemplate tplPtBR = resolver.resolve("pt-BR", vars);
System.out.println("5B.13 Case4 pt-BR → fallback:");
System.out.println("  - resolvedLocale=" + tplPtBR.getResolvedLocale());
System.out.println("  - usedFallback=" + tplPtBR.getUsedFallback());

// 5B.13 Case5: EN_us → normalize → en-US
BookingConfirmationTemplate tplENus = resolver.resolve("EN_us", vars);
System.out.println("5B.13 Case5 EN_us normalize:");
System.out.println("  - resolvedLocale=" + tplENus.getResolvedLocale());
System.out.println("  - usedFallback=" + tplENus.getUsedFallback());

/exit
