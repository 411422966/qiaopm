// 5B.12 + 5B.13 BookingConfirmationTemplateResolver 验证
import com.meiyetec.booking.BookingConfirmationTemplateResolver;
import com.meiyetec.booking.BookingConfirmationTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

BookingConfirmationTemplateResolver resolver = new BookingConfirmationTemplateResolver();

// 5B.12 warmup
resolver.warmup();
System.out.println("5B.12 warmup() called");

// 5B.12 availableLocales
List<String> locales = resolver.availableLocales();
System.out.println("5B.12 availableLocales size = " + locales.size());
System.out.println("5B.12 locales = " + locales);

// 5B.12 loadLocale("zh-CN")
BookingConfirmationTemplate tplCN = resolver.resolve("zh-CN", new HashMap<>());
System.out.println("5B.12 zh-CN resolved: subject=" + (tplCN != null ? tplCN.getSubject() : "null") + ", smsChars=" + (tplCN != null && tplCN.getSms() != null ? tplCN.getSms().length() : -1));

// 5B.13 resolve("xx-YY", vars) → fallback to en-US
Map<String, Object> vars = new HashMap<>();
vars.put("customerName", "Test");
BookingConfirmationTemplate tplFallback = resolver.resolve("xx-YY", vars);
System.out.println("5B.13 xx-YY fallback: resolvedLocale=" + tplFallback.getResolvedLocale() + ", usedFallback=" + tplFallback.getUsedFallback());

// 5B.13 resolve(null, vars) → DEFAULT_LOCALE = en-US
BookingConfirmationTemplate tplNull = resolver.resolve(null, vars);
System.out.println("5B.13 null fallback: resolvedLocale=" + tplNull.getResolvedLocale() + ", usedFallback=" + tplNull.getUsedFallback());

// 5B.13 resolve("zh_CN", vars) → normalize to zh-CN
BookingConfirmationTemplate tplZhCN = resolver.resolve("zh_CN", vars);
System.out.println("5B.13 zh_CN normalize: resolvedLocale=" + tplZhCN.getResolvedLocale() + ", usedFallback=" + tplZhCN.getUsedFallback());

/exit
