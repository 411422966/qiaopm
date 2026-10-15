// 5B.8 SmsSender.render 占位符替换验证（public static）
import com.meiyetec.service.channel.SmsSender;
import java.util.Map;
import java.util.HashMap;

// Case 1: 普通变量替换
Map<String, Object> vars1 = new HashMap<>();
vars1.put("customerName", "Alice");
vars1.put("serviceName", "Facial");
vars1.put("bookingTime", "14:00");
String r1 = SmsSender.render("Hello ${customerName}, your ${serviceName} at ${bookingTime}", vars1);
System.out.println("5B.8 Case1: " + r1);

// Case 2: 缺失占位符保留 ${name}
Map<String, Object> vars2 = new HashMap<>();
vars2.put("customerName", "Bob");
String r2 = SmsSender.render("Hi ${customerName}, ${missingKey} stays", vars2);
System.out.println("5B.8 Case2: " + r2);

// Case 3: null vars
String r3 = SmsSender.render("No vars: ${any}", null);
System.out.println("5B.8 Case3: " + r3);

/exit
