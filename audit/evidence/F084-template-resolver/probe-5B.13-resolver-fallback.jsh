import com.meiyetec.booking.BookingConfirmationTemplateResolver;
import com.meiyetec.booking.BookingConfirmationTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

System.out.println("=== F084 BookingConfirmationTemplateResolver \u56de\u9000\u94fe\u5b9e\u8bc1 ===");
System.out.println();

BookingConfirmationTemplateResolver resolver = new BookingConfirmationTemplateResolver();
ObjectMapper jackson = new ObjectMapper();
Field jacksonField = BookingConfirmationTemplateResolver.class.getDeclaredField("jackson");
jacksonField.setAccessible(true);
jacksonField.set(resolver, jackson);

resolver.warmup();

Map<String, Object> vars = new HashMap<>();
vars.put("customerName", "Alice");
vars.put("bookingNo", "BK-FB-001");

System.out.println("--- Case 1: xx-YY (\u672a\u5b9a\u4e49\u7684\u8bed\u8a00) -> fallback \u5230 en-US ---");
Method resolve = BookingConfirmationTemplateResolver.class.getMethod("resolve", String.class, Map.class);
BookingConfirmationTemplate tpl1 = (BookingConfirmationTemplate) resolve.invoke(resolver, "xx-YY", vars);
System.out.println("resolvedLocale = " + tpl1.getResolvedLocale());
System.out.println("usedFallback   = " + tpl1.getUsedFallback());
System.out.println("PASS (resolvedLocale=en-US): " + "en-US".equals(tpl1.getResolvedLocale()));
System.out.println("PASS (usedFallback=true):  " + Boolean.TRUE.equals(tpl1.getUsedFallback()));

System.out.println();
System.out.println("--- Case 2: null locale -> DEFAULT_LOCALE=en-US ---");
BookingConfirmationTemplate tpl2 = (BookingConfirmationTemplate) resolve.invoke(resolver, null, vars);
System.out.println("resolvedLocale = " + tpl2.getResolvedLocale());
System.out.println("PASS (resolvedLocale=en-US): " + "en-US".equals(tpl2.getResolvedLocale()));

System.out.println();
System.out.println("--- Case 3: zh_CN (\u4e0b\u5212\u7ebf) -> \u5f52\u4e00\u5316\u4e3a zh-CN ---");
BookingConfirmationTemplate tpl3 = (BookingConfirmationTemplate) resolve.invoke(resolver, "zh_CN", vars);
System.out.println("resolvedLocale = " + tpl3.getResolvedLocale());
System.out.println("usedFallback   = " + tpl3.getUsedFallback());
System.out.println("PASS (resolvedLocale=zh-CN): " + "zh-CN".equals(tpl3.getResolvedLocale()));
System.out.println("PASS (usedFallback=false):   " + !Boolean.TRUE.equals(tpl3.getUsedFallback()));

System.out.println();
System.out.println("--- Case 4: pt-BR (\u672a\u5b9a\u4e49\u8bed\u8a00) -> fallback \u5230 en-US ---");
BookingConfirmationTemplate tpl4 = (BookingConfirmationTemplate) resolve.invoke(resolver, "pt-BR", vars);
System.out.println("resolvedLocale = " + tpl4.getResolvedLocale());
System.out.println("PASS (resolvedLocale=en-US): " + "en-US".equals(tpl4.getResolvedLocale()));

System.out.println();
System.out.println("--- Case 5: EN_us (\u5927\u5c0f\u5199\u6df7\u5408) -> \u5f52\u4e00\u5316 ---");
BookingConfirmationTemplate tpl5 = (BookingConfirmationTemplate) resolve.invoke(resolver, "EN_us", vars);
System.out.println("resolvedLocale = " + tpl5.getResolvedLocale());
System.out.println("PASS (resolvedLocale=en-US): " + "en-US".equals(tpl5.getResolvedLocale()));

System.out.println();
System.out.println("=== F084 \u56de\u9000\u94fe\u5b9e\u8bc1\u5b8c\u6210 ===");

/exit
