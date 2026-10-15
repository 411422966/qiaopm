import com.meiyetec.service.channel.SmsSender;
import com.meiyetec.service.channel.EmailSender;
import com.meiyetec.service.channel.MiniProgramSender;
import com.meiyetec.service.channel.WechatOfficialAccountSender;
import com.meiyetec.service.channel.InAppSender;

// ============================================================
// F137 — 5 Sender x Unit Tests
// ============================================================

// 1. SMS Sender: render() is a public static method
System.out.println("=== [1] SMS Sender ===");
System.out.println("channelType method exists: " + (SmsSender.class.getDeclaredMethods().length > 0));
java.util.Map<String, Object> vars = new java.util.HashMap<>();
vars.put("name", "Alice");
vars.put("code", "1234");
String r1 = SmsSender.render("Hi ${name}, code=${code}!", vars);
System.out.println("render() result: " + r1);
System.out.println("render() edge case (${unknown} -> empty): '" + SmsSender.render("${known}/${unknown}", vars) + "'");

// 2. EMAIL Sender: channelType
System.out.println("\n=== [2] EMAIL Sender ===");
EmailSender email = new EmailSender();
System.out.println("channelType(): " + email.channelType());

// 3. MINI_PROGRAM Sender: channelType
System.out.println("\n=== [3] MINI_PROGRAM Sender ===");
MiniProgramSender mp = new MiniProgramSender();
System.out.println("channelType(): " + mp.channelType());

// 4. WECHAT_OA Sender: channelType — Note: code says WECHAT_OFFICIAL_ACCOUNT
System.out.println("\n=== [4] WECHAT Official Account Sender ===");
WechatOfficialAccountSender oa = new WechatOfficialAccountSender();
System.out.println("channelType(): " + oa.channelType());
System.out.println("Sender constants:");
System.out.println("  - WechatOfficialAccountSender.channelType() = " + oa.channelType());
System.out.println("  - MiniProgramSender.channelType() = " + new MiniProgramSender().channelType());
System.out.println("  - EmailSender.channelType() = " + new EmailSender().channelType());

// 5. IN_APP Sender: channelType
System.out.println("\n=== [5] IN_APP Sender ===");
InAppSender inApp = new InAppSender();
System.out.println("channelType(): " + inApp.channelType());

// 6. Interface sanity
System.out.println("\n=== [6] Interface sanity ===");
System.out.println("ChannelSender interface methods:");
for (java.lang.reflect.Method m : com.meiyetec.service.channel.ChannelSender.class.getDeclaredMethods()) {
    System.out.println("  - " + m.toString());
}

/exit
