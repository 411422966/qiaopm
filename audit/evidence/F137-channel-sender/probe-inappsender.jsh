// F137 InAppSender channelType + healthCheck 反射测试
import java.lang.reflect.*;

System.out.println("=== InAppSender channelType ===");
Class cls = Class.forName("com.meiyetec.service.channel.InAppSender");
Object sender = cls.getDeclaredConstructor().newInstance();
String ct = (String)cls.getMethod("channelType").invoke(sender);
System.out.println("channelType = " + ct);
System.out.println("Expected: IN_APP");
System.out.println("MATCH: " + "IN_APP".equals(ct));

System.out.println("=== InAppSender healthCheck (依赖 member_message 表) ===");
Boolean healthy = (Boolean)cls.getMethod("healthCheck").invoke(sender);
System.out.println("healthCheck = " + healthy);
System.out.println("(F118 IN_APP healthy=false 已在 API 层验证 — member_message 表需确认)");

/exit
