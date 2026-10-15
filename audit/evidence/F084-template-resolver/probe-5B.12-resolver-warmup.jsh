import com.meiyetec.booking.BookingConfirmationTemplateResolver;
import com.meiyetec.booking.BookingConfirmationTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

System.out.println("=== F084/F107 BookingConfirmationTemplateResolver \u5b9e\u8bc1 ===");
System.out.println();

BookingConfirmationTemplateResolver resolver = new BookingConfirmationTemplateResolver();

ObjectMapper jackson = new ObjectMapper();
Field jacksonField = BookingConfirmationTemplateResolver.class.getDeclaredField("jackson");
jacksonField.setAccessible(true);
jacksonField.set(resolver, jackson);

System.out.println("--- warmup() \u9a8c\u8bc1 6 locale \u52a0\u8f7d ---");
resolver.warmup();

System.out.println();
System.out.println("--- availableLocales() ---");
java.util.List<String> locales = resolver.availableLocales();
System.out.println("locales = " + locales);
System.out.println("PASS (6 locale):          " + (locales.size() == 6));
System.out.println("PASS (zh-CN \u5728\u5217):    " + locales.contains("zh-CN"));
System.out.println("PASS (zh-TW \u5728\u5217):    " + locales.contains("zh-TW"));
System.out.println("PASS (en-US \u5728\u5217):    " + locales.contains("en-US"));
System.out.println("PASS (ja-JP \u5728\u5217):    " + locales.contains("ja-JP"));
System.out.println("PASS (ko-KR \u5728\u5217):    " + locales.contains("ko-KR"));
System.out.println("PASS (fr-FR \u5728\u5217):    " + locales.contains("fr-FR"));

System.out.println();
System.out.println("--- loadLocale(\"zh-CN\") 4 \u6bb5\u5b57\u6bb5\u5b8c\u6574\u6027 ---");
java.lang.reflect.Method loadLocale = BookingConfirmationTemplateResolver.class.getDeclaredMethod("loadLocale", String.class);
loadLocale.setAccessible(true);
com.fasterxml.jackson.databind.JsonNode zhCn = (com.fasterxml.jackson.databind.JsonNode) loadLocale.invoke(resolver, "zh-CN");
System.out.println("subject = " + zhCn.path("subject").asText());
System.out.println("miniProgram.keyword1 = " + zhCn.path("miniProgram").path("keyword1").asText());
System.out.println("miniProgram.keyword5 = " + zhCn.path("miniProgram").path("keyword5").asText());
System.out.println("email.subject = " + zhCn.path("email").path("subject").asText());
System.out.println("PASS (subject \u5b57\u6bb5):           " + zhCn.has("subject"));
System.out.println("PASS (sms \u5b57\u6bb5):              " + zhCn.has("sms"));
System.out.println("PASS (miniProgram.title):       " + zhCn.path("miniProgram").has("title"));
System.out.println("PASS (miniProgram.keyword1..5): " + (zhCn.path("miniProgram").has("keyword1") && zhCn.path("miniProgram").has("keyword5")));
System.out.println("PASS (email.subject + body):    " + (zhCn.path("email").has("subject") && zhCn.path("email").has("body")));

System.out.println();
System.out.println("--- resolve(\"zh-CN\", vars) \u6e32\u67d3 ---");
Map<String, Object> vars = new HashMap<>();
vars.put("customerName", "\u963f\u91cc");
vars.put("serviceName", "\u9762\u90e8\u62a4\u7406");
vars.put("bookingDate", "2026-10-01");
vars.put("bookingTime", "14:00");
vars.put("bookingNo", "BK-001");
vars.put("storeName", "\u7f8e\u4e1a\u65d7\u8230\u5e97");
vars.put("storePhone", "400-888-8888");

BookingConfirmationTemplate tpl = resolver.resolve("zh-CN", vars);
System.out.println("resolvedLocale   = " + tpl.getResolvedLocale());
System.out.println("usedFallback     = " + tpl.getUsedFallback());
System.out.println("subject          = " + tpl.getSubject());
System.out.println("sms              = " + tpl.getSms());
System.out.println("miniProgramTitle = " + tpl.getMiniProgramTitle());
System.out.println("miniProgram.kw1  = " + tpl.getMiniProgram().get("keyword1"));
System.out.println("emailSubject     = " + tpl.getEmailSubject());
System.out.println();
System.out.println("PASS (resolvedLocale=zh-CN):  " + "zh-CN".equals(tpl.getResolvedLocale()));
System.out.println("PASS (usedFallback=false):    " + !Boolean.TRUE.equals(tpl.getUsedFallback()));
System.out.println("PASS (sms \u542b\u9884\u7ea6\u53f7):     " + (tpl.getSms() != null && tpl.getSms().contains("BK-001")));
System.out.println("PASS (miniProgram.kw1 \u542b BK-001): " + (tpl.getMiniProgram().get("keyword1") != null && tpl.getMiniProgram().get("keyword1").contains("BK-001")));
System.out.println("PASS (emailSubject \u542b BK-001):  " + (tpl.getEmailSubject() != null && tpl.getEmailSubject().contains("BK-001")));

System.out.println();
System.out.println("=== \u5b9e\u8bc1\u5b8c\u6210 ===");

/exit
