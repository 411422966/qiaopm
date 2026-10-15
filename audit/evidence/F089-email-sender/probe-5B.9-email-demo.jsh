import com.meiyetec.service.channel.EmailSender;
import com.meiyetec.service.channel.ChannelSender;
import java.lang.reflect.Method;

System.out.println("=== F089 EmailSender.demoMode \u5b9e\u8bc1 ===");
System.out.println();

// \u521b\u5efa\u5b9e\u4f8b\uff08private demoMode \u9700\u53cd\u5c04\uff09
EmailSender sender = new EmailSender();
System.out.println("EmailSender instance: " + sender.getClass().getName());

// \u83b7\u53d6 private demoMode(String, String, String) \u65b9\u6cd5
Method demoMode = sender.getClass().getDeclaredMethod("demoMode", String.class, String.class, String.class);
demoMode.setAccessible(true);

System.out.println();
System.out.println("--- Case 1: demoMode \u8fd4\u56de success=true ---");
ChannelSender.SendResult r1 = (ChannelSender.SendResult) demoMode.invoke(sender,
        "alice@example.com", "\u9884\u7ea6\u786e\u8ba4\u901a\u77e5", "\u5c0a\u656c\u7684Alice\uff0c\u60a8\u7684\u9884\u7ea6\u5df2\u786e\u8ba4");
System.out.println("success       = " + r1.success);
System.out.println("externalMsgId = " + r1.externalMsgId);
System.out.println("errorMsg      = " + r1.errorMsg);
System.out.println("PASS (success=true): " + r1.success);
System.out.println("PASS (id prefix EMAIL-DEMO-): " + (r1.externalMsgId != null && r1.externalMsgId.startsWith("EMAIL-DEMO-")));

System.out.println();
System.out.println("--- Case 2: channelType() \u5e94\u8fd4\u56de \"EMAIL\" ---");
String ct = sender.channelType();
System.out.println("channelType = " + ct);
System.out.println("PASS (\"EMAIL\"): " + "EMAIL".equals(ct));

System.out.println();
System.out.println("--- Case 3: SendResult.fail \u5de5\u5382\u65b9\u6cd5 ---");
ChannelSender.SendResult r3 = ChannelSender.SendResult.fail("test error");
System.out.println("success  = " + r3.success);
System.out.println("errorMsg = " + r3.errorMsg);
System.out.println("PASS (success=false): " + !r3.success);
System.out.println("PASS (errorMsg \u4fdd\u7559): " + "test error".equals(r3.errorMsg));

System.out.println();
System.out.println("--- Case 4: SendResult.ok \u5de5\u5382\u65b9\u6cd5 ---");
ChannelSender.SendResult r4 = ChannelSender.SendResult.ok("EMAIL-SMTP-12345");
System.out.println("success       = " + r4.success);
System.out.println("externalMsgId = " + r4.externalMsgId);
System.out.println("PASS (success=true): " + r4.success);
System.out.println("PASS (id \u4fdd\u7559): " + "EMAIL-SMTP-12345".equals(r4.externalMsgId));

System.out.println();
System.out.println("=== F089 \u5b9e\u8bc1\u5b8c\u6210 ===");

/exit
