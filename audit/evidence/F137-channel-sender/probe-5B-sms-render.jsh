import com.meiyetec.service.channel.SmsSender;
import java.util.HashMap;
import java.util.Map;
Map<String, Object> vars = new HashMap<>();
vars.put("customerName", "张三");
vars.put("bookingNo", "BK-2026-001");
vars.put("storeName", "美业旗舰店");
String result = SmsSender.render("亲爱的 ${customerName}，您的预约 ${bookingNo} 已在 ${storeName} 确认。", vars);
System.out.println("=== SMS render normal ===");
System.out.println(result);

vars.put("nonexistent", null);
String withNull = SmsSender.render("hello ${missing} world", vars);
System.out.println("=== SMS render null replace ===");
System.out.println(withNull);

String withSpecial = SmsSender.render("price: $100 (with literal $ and ${notclosing)", vars);
System.out.println("=== SMS render edge ===");
System.out.println(withSpecial);

String noReplace = SmsSender.render("plain text no vars", vars);
System.out.println("=== SMS render no vars ===");
System.out.println(noReplace);

String nullTpl = SmsSender.render(null, vars);
System.out.println("=== SMS render null template ===");
System.out.println(nullTpl);
/exit
