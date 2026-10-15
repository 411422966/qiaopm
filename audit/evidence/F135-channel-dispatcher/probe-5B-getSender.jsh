import com.meiyetec.service.channel.ChannelDispatcher;
import com.meiyetec.service.channel.ChannelSender;
import com.meiyetec.service.channel.SmsSender;
import com.meiyetec.service.channel.EmailSender;
import com.meiyetec.service.channel.MiniProgramSender;
import com.meiyetec.service.channel.WechatOfficialAccountSender;
import com.meiyetec.service.channel.InAppSender;
import com.meiyetec.service.channel.ChannelSender.SendResult;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

// ============================================================
// F135 — ChannelDispatcher.getSender() Unit Test (jshell)
// I061 NPE Risk Empirics
// ============================================================

// 1. Build all 5 senders
List<ChannelSender> senders = new ArrayList<>();
senders.add(new SmsSender());
senders.add(new EmailSender());
senders.add(new MiniProgramSender());
senders.add(new WechatOfficialAccountSender());
senders.add(new InAppSender());

System.out.println("=== [F135] ChannelDispatcher.getSender() Unit Test ===");
System.out.println("Total senders registered: " + senders.size());
System.out.println("Sender types: " + senders.stream().map(ChannelSender::channelType).collect(Collectors.joining(", ")));

// 2. Use reflection to construct ChannelDispatcher bypassing mapper requirement
java.lang.reflect.Constructor<ChannelDispatcher> ctor =
    ChannelDispatcher.class.getDeclaredConstructor(List.class, com.meiyetec.mapper.MktChannelConfigMapper.class);
ctor.setAccessible(true);
// null mapper is OK for getSender() — it doesn't touch mapper
ChannelDispatcher dispatcher = ctor.newInstance(senders, null);
System.out.println("ChannelDispatcher constructed (mapper=null for unit-test isolation)");

// 3. Test getSender for all 5 valid channel types
String[] validTypes = {"SMS", "EMAIL", "MINI_PROGRAM", "WECHAT_OFFICIAL_ACCOUNT", "IN_APP"};
System.out.println("\n--- [3.1] Valid channelType ---");
for (String type : validTypes) {
    ChannelSender s = dispatcher.getSender(type);
    System.out.println("  getSender(\"" + type + "\") = " + (s == null ? "null" : s.getClass().getSimpleName() + " (channelType=" + s.channelType() + ")"));
}

// 4. Test getSender for I061 NPE risk: invalid channelType returns null
System.out.println("\n--- [3.2] Invalid channelType (I061 NPE risk) ---");
String[] invalidTypes = {"UNKNOWN", "WECHAT_OA", "", "PUSH", "SMS_EXT", "wechat"};
for (String type : invalidTypes) {
    ChannelSender s = dispatcher.getSender(type);
    System.out.println("  getSender(\"" + type + "\") = " + (s == null ? "null ** NPE risk if caller uses without null check **" : "non-null"));
}

// 5. Test caller's NPE behavior (simulating call site: sender.send(...) without null check)
System.out.println("\n--- [3.3] NPE Empirics — what happens if caller does null sender.send()? ---");
try {
    ChannelSender badSender = dispatcher.getSender("UNKNOWN_CHANNEL");
    System.out.println("Step 1: dispatcher.getSender(\"UNKNOWN_CHANNEL\") returns: " + badSender);
    System.out.println("Step 2: caller does: badSender.send(null, null, null)");
    badSender.send(null, null, null);
} catch (NullPointerException npe) {
    System.out.println("NPE TRIGGERED: " + npe.getClass().getSimpleName() + " — " + npe.getMessage());
    System.out.println("First 3 stack frames:");
    StackTraceElement[] frames = npe.getStackTrace();
    for (int i = 0; i < Math.min(3, frames.length); i++) {
        System.out.println("    at " + frames[i]);
    }
} catch (Exception e) {
    System.out.println("Exception: " + e.getClass().getSimpleName() + " — " + e.getMessage());
}

// 6. WECHAT_OA alias drift verification (N12)
System.out.println("\n--- [3.4] WECHAT_OA vs WECHAT_OFFICIAL_ACCOUNT alias drift (N12) ---");
ChannelSender wechatOaByLegacyAlias = dispatcher.getSender("WECHAT_OA");
System.out.println("  dispatcher.getSender(\"WECHAT_OA\")            = " + (wechatOaByLegacyAlias == null ? "null" : wechatOaByLegacyAlias.getClass().getSimpleName()));
ChannelSender wechatOaByRealName = dispatcher.getSender("WECHAT_OFFICIAL_ACCOUNT");
System.out.println("  dispatcher.getSender(\"WECHAT_OFFICIAL_ACCOUNT\") = " + (wechatOaByRealName == null ? "null" : wechatOaByRealName.getClass().getSimpleName()));
System.out.println("  Frontend mkt_channel_template list likely uses WECHAT_OA (legacy alias)");
System.out.println("  Backend sender's channelType() returns WECHAT_OFFICIAL_ACCOUNT (real name)");
System.out.println("  ====> DISPATCH FAILURE if frontend uses WECHAT_OA and dispatcher only knows WECHAT_OFFICIAL_ACCOUNT <====");

/exit
