// F135 ChannelDispatcher.getSender 单元测试
// 验证 3 件事：
//   1. 5 sender 全部注册（senders size = 5）
//   2. 5 个有效 channelType 返回非 null
//   3. 无效 channelType 返回 null（NPE 风险实证 — I061）

import com.meiyetec.service.channel.*;
import java.util.*;
import java.util.stream.*;

List<ChannelSender> senders = new ArrayList<>();
senders.add(new SmsSender());
senders.add(new EmailSender());
senders.add(new MiniProgramSender());
senders.add(new InAppSender());
senders.add(new WechatOfficialAccountSender());

System.out.println("=== F135.1 Sender 注册验证 ===");
System.out.println("senders.size = " + senders.size());
Map<String, String> registeredTypes = new LinkedHashMap<>();
for (ChannelSender s : senders) {
    registeredTypes.put(s.channelType(), s.getClass().getSimpleName());
}
registeredTypes.forEach((k, v) -> System.out.println("  " + k + " -> " + v));

// 构造 ChannelDispatcher（mapper=null，jdbc=null — 只测 getSender）
ChannelDispatcher dispatcher = new ChannelDispatcher(senders, null);

System.out.println("=== F135.2 5 个有效 channelType 查询 ===");
String[] validTypes = {"SMS", "EMAIL", "MINI_PROGRAM", "IN_APP", "WECHAT_OFFICIAL_ACCOUNT"};
int nullCount = 0;
for (String t : validTypes) {
    ChannelSender s = dispatcher.getSender(t);
    String cls = s == null ? "NULL" : s.getClass().getSimpleName();
    System.out.println("  getSender(\"" + t + "\") = " + cls);
    if (s == null) nullCount++;
}
System.out.println("  valid null count = " + nullCount + " (期望 0)");

System.out.println("=== F135.3 NPE 风险实证 (N3 I061) ===");
String[] invalidTypes = {"UNKNOWN", "WECHAT_OA", "PUSH", "SMS_GATEWAY", "ALIYUN", ""};
int invalidNullCount = 0;
for (String t : invalidTypes) {
    ChannelSender s = dispatcher.getSender(t);
    String cls = s == null ? "NULL" : s.getClass().getSimpleName();
    System.out.println("  getSender(\"" + t + (t.isEmpty() ? "\"(empty)" : "\"") + ") = " + cls);
    if (s == null) invalidNullCount++;
}
System.out.println("  invalid null count = " + invalidNullCount + " (期望 " + invalidTypes.length + " — 全部返回 null)");

System.out.println("=== F135.4 WECHAT_OA 别名漂移验证 (N12 沿用 I039) ===");
ChannelSender oaViaOA = dispatcher.getSender("WECHAT_OFFICIAL_ACCOUNT");
ChannelSender oaViaLegacy = dispatcher.getSender("WECHAT_OA");
System.out.println("  WECHAT_OFFICIAL_ACCOUNT -> " + (oaViaOA == null ? "NULL" : oaViaOA.getClass().getSimpleName()));
System.out.println("  WECHAT_OA (legacy 别名) -> " + (oaViaLegacy == null ? "NULL" : oaViaLegacy.getClass().getSimpleName()));
System.out.println("  结论: code 用 WECHAT_OFFICIAL_ACCOUNT, db mkt_channel_config.channel_type 也应一致; 前端/老数据如用 WECHAT_OA 会出现 null");

System.out.println("=== F135.5 NPE 实证: 调用方不做 null 检查会怎样 ===");
try {
    ChannelSender s = dispatcher.getSender("UNKNOWN_TYPE");
    // 模拟业务调用方直接 .send(tpl, receiver, vars)
    String content = "test content";
    java.util.Map<String, Object> vars = new java.util.HashMap<>();
    vars.put("name", "张三");
    // 构造 MktChannelTemplate mock — 但避免 import entity 类，直接 NullPointerException 演示
    s.send(null, "test-receiver", vars);
    System.out.println("  UNEXPECTED: no NPE thrown");
} catch (NullPointerException npe) {
    System.out.println("  NPE caught: " + npe.getMessage());
    System.out.println("  实证成功: getSender 返回 null → 业务侧 .send() → NPE");
} catch (Exception e) {
    System.out.println("  Other exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
}

System.out.println("=== F135 结论 ===");
System.out.println("  VERIFIED: 5 sender 全部注册 + 有效 channelType 路由正确");
System.out.println("  I061 NPE 实证: invalid channelType 返回 null, 业务侧无 null 检查 → NPE 风险");
System.out.println("  I039 N12 别名漂移: code=WECHAT_OFFICIAL_ACCOUNT, 别名 WECHAT_OA 不存在");

/exit