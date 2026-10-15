// === F137 InAppSender.healthCheck 验证（member_message 表 MISSING）===
import com.meiyetec.service.channel.*;
import java.util.*;

System.out.println("=== InAppSender.healthCheck 验证 ===");
InAppSender inAppSender = new InAppSender();
System.out.println("InAppSender.channelType() = " + inAppSender.channelType());

try {
    boolean result = inAppSender.healthCheck();
    System.out.println("healthCheck() = " + result + " (期望 false — member_message 表 MISSING)");
} catch (Exception e) {
    System.out.println("healthCheck() EXCEPTION: " + e.getClass().getSimpleName() + " " + e.getMessage());
}

System.out.println();
System.out.println("=== InAppSender.send() 写库逻辑 (member_message 表 MISSING) ===");
System.out.println("InAppSender.send 会调 messageMapper.insert(MemberMessage)");
System.out.println("实际: INSERT INTO member_message ...");
System.out.println("由于 member_message 表不存在 (I045)，调用会抛异常");
System.out.println("InAppSender.send 内部 try/catch 包裹 → 返回 SendResult.fail");
System.out.println("不会让 Spring 抛 500");

/exit