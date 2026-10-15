import com.meiyetec.service.channel.SmsSender;
import java.util.HashMap;
import java.util.Map;

System.out.println("=== F088 SmsSender.render 占位符替换 3 组 实证 ===");
System.out.println();

Map<String, Object> vars = new HashMap<>();
vars.put("customerName", "Alice");
vars.put("serviceName", "Facial Treatment");
vars.put("bookingNo", "BK-20261001-001");
vars.put("bookingDate", "2026-10-01");
vars.put("bookingTime", "14:00");
vars.put("storeName", "\u7f8e\u4e1a\u65d7\u8230\u5e97");
vars.put("storePhone", "400-888-8888");

System.out.println("--- Case 1: \u666e\u901a\u5360\u4f4d\u7b26\u66ff\u6362\uff08\u591a\u4e2a\u53d8\u91cf\uff09---");
String tpl1 = "\u5c0a\u656c\u7684${customerName}\uff0c\u60a8\u9884\u7ea6\u7684${serviceName}\u5c06\u4e8e${bookingDate} ${bookingTime} \u5728 ${storeName}\uff08${storePhone}\uff09\u5f00\u59cb\u3002\u9884\u7ea6\u53f7\uff1a${bookingNo}";
String r1 = SmsSender.render(tpl1, vars);
System.out.println("Template: " + tpl1);
System.out.println("Result:   " + r1);
System.out.println("PASS: " + (r1.contains("Alice") && r1.contains("Facial Treatment") && r1.contains("BK-20261001-001")));
System.out.println();

System.out.println("--- Case 2: \u7f3a\u5360\u4f4d\u7b26\uff08vars \u4e2d\u672a\u5b9a\u4e49\uff09---");
Map<String, Object> partialVars = new HashMap<>();
partialVars.put("customerName", "Bob");
String tpl2 = "\u4f60\u597d ${customerName}\uff0c\u60a8\u7684\u9884\u7ea6 ${missingVar} \u7b49\u5f85\u786e\u8ba4";
String r2 = SmsSender.render(tpl2, partialVars);
System.out.println("Template: " + tpl2);
System.out.println("Result:   " + r2);
System.out.println("PASS (\u7f3a\u5931\u53d8\u91cf\u8f6c\u4e3a\u7a7a\u5b57\u7b26\u4e32): " + r2.contains("Bob"));
System.out.println();

System.out.println("--- Case 3: null \u8f93\u5165\u4fdd\u62a4 ---");
String r3a = SmsSender.render(null, vars);
String r3b = SmsSender.render(tpl1, null);
String r3c = SmsSender.render(null, null);
System.out.println("render(null, vars)  = " + r3a);
System.out.println("render(tpl1, null)  = " + r3b);
System.out.println("render(null, null)  = " + r3c);
System.out.println("PASS (null template -> null):       " + (r3a == null));
System.out.println("PASS (null vars -> \u539f\u6a21\u677f):        " + r3b.equals(tpl1));

System.out.println();
System.out.println("=== \u5b9e\u8bc1\u5b8c\u6210 ===");

/exit
