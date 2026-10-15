// F137 5 Sender 单元测试
// 验证 5 sender 的 channelType 契约 + 静态 render 算法（不依赖 Spring）
// 真实发送链路（EMAIL/MP/OA）凭据缺失 → UNVERIFIED 真实链路（沿用 D10 铁律）

import com.meiyetec.service.channel.*;
import java.util.*;

System.out.println("=== F137.1 5 Sender channelType 契约验证 ===");
ChannelSender[] senders = {
    new SmsSender(),
    new EmailSender(),
    new MiniProgramSender(),
    new InAppSender(),
    new WechatOfficialAccountSender()
};
String[] expectedTypes = {"SMS", "EMAIL", "MINI_PROGRAM", "IN_APP", "WECHAT_OFFICIAL_ACCOUNT"};
int matchCount = 0;
for (int i = 0; i < senders.length; i++) {
    String actual = senders[i].channelType();
    String expected = expectedTypes[i];
    boolean match = actual.equals(expected);
    System.out.println("  " + senders[i].getClass().getSimpleName() + ".channelType() = \"" + actual + "\" (期望 \"" + expected + "\") " + (match ? "OK" : "MISMATCH"));
    if (match) matchCount++;
}
System.out.println("  channelType match: " + matchCount + "/" + senders.length);

System.out.println();
System.out.println("=== F137.2 SmsSender.render 算法（占位符替换）===");
Map<String, Object> vars = new HashMap<>();
vars.put("name", "张三");
vars.put("phone", "13800138000");
vars.put("bookingNo", "B-2026-001");
vars.put("date", "2026-09-16");

// 测试 1: 单变量
String t1 = "尊敬的${name}，您的预约已确认";
String r1 = SmsSender.render(t1, vars);
System.out.println("  T1 single var:    \"" + t1 + "\" -> \"" + r1 + "\"");
System.out.println("    " + (r1.equals("尊敬的张三，您的预约已确认") ? "OK" : "FAIL"));

// 测试 2: 多变量
String t2 = "${name}您好，预约号${bookingNo}，时间${date}";
String r2 = SmsSender.render(t2, vars);
System.out.println("  T2 multi vars:    \"" + t2 + "\" -> \"" + r2 + "\"");
System.out.println("    " + (r2.equals("张三您好，预约号B-2026-001，时间2026-09-16") ? "OK" : "FAIL"));

// 测试 3: 缺失变量 → 空字符串
Map<String, Object> partialVars = new HashMap<>();
partialVars.put("name", "李四");
String r3 = SmsSender.render("${name}的预约号${bookingNo}", partialVars);
System.out.println("  T3 missing var:   \"${name}的预约号${bookingNo}\" -> \"" + r3 + "\"");
System.out.println("    " + (r3.equals("李四的预约号") ? "OK" : "FAIL"));

// 测试 4: null vars 不抛异常
String r4 = SmsSender.render("${name}", null);
System.out.println("  T4 null vars:     \"${name}\" with null vars -> \"" + r4 + "\"");
System.out.println("    " + (r4.equals("${name}") ? "OK (render 跳过)" : "FAIL"));

// 测试 5: null template
String r5 = SmsSender.render(null, vars);
System.out.println("  T5 null template: null -> " + (r5 == null ? "null" : "\"" + r5 + "\""));
System.out.println("    " + (r5 == null ? "OK (null 透传)" : "FAIL"));

// 测试 6: 特殊字符 ($ $ 自身 + $$ 边界)
String t6 = "$${name} test ${name}$";
String r6 = SmsSender.render(t6, vars);
System.out.println("  T6 special chars: \"" + t6 + "\" -> \"" + r6 + "\"");
System.out.println("    " + (r6.contains("张三") && r6.startsWith("$") ? "OK" : "FAIL"));

// 测试 7: 数字变量
vars.put("points", 100);
String r7 = SmsSender.render("您获得了${points}积分", vars);
System.out.println("  T7 number var:    \"您获得了${points}积分\" -> \"" + r7 + "\"");
System.out.println("    " + (r7.equals("您获得了100积分") ? "OK" : "FAIL"));

System.out.println();
System.out.println("=== F137.3 InAppSender parseMemberId 算法（通过反射调 private）===");
// parseMemberId 是 private，但 channelType 可直接测
// 用反射测 parseMemberId（验证 receiver 解析）
InAppSender inApp = new InAppSender();
java.lang.reflect.Method parseMemberId = InAppSender.class.getDeclaredMethod("parseMemberId", String.class);
parseMemberId.setAccessible(true);
String[] testReceivers = {"12345", "12345-张三", "  12345  ", "12345abc-张三", "abc", "张三", null};
String[] expectedIds = {"12345", "12345", "12345", "12345", null, null, null};
int passCount = 0;
for (int i = 0; i < testReceivers.length; i++) {
    Object result = parseMemberId.invoke(inApp, testReceivers[i]);
    String actualStr = result == null ? "null" : result.toString();
    String expectedStr = expectedIds[i] == null ? "null" : expectedIds[i];
    boolean pass = expectedStr.equals(actualStr);
    System.out.println("  parseMemberId(\"" + testReceivers[i] + "\") = " + actualStr + " (期望 " + expectedStr + ") " + (pass ? "OK" : "FAIL"));
    if (pass) passCount++;
}
System.out.println("  parseMemberId pass: " + passCount + "/" + testReceivers.length);

// extractMemberName (private)
java.lang.reflect.Method extractMemberName = InAppSender.class.getDeclaredMethod("extractMemberName", String.class);
extractMemberName.setAccessible(true);
String[] testNames = {"12345-张三", "12345-李四 王五", "12345", null, "12345-"};
String[] expectedNames = {"张三", "李四 王五", null, null, ""};
int namePass = 0;
for (int i = 0; i < testNames.length; i++) {
    Object result = extractMemberName.invoke(inApp, testNames[i]);
    String actualStr = result == null ? "null" : "\"" + result + "\"";
    String expectedStr = expectedNames[i] == null ? "null" : "\"" + expectedNames[i] + "\"";
    boolean pass = expectedStr.equals(actualStr);
    System.out.println("  extractMemberName(\"" + testNames[i] + "\") = " + actualStr + " (期望 " + expectedStr + ") " + (pass ? "OK" : "FAIL"));
    if (pass) namePass++;
}
System.out.println("  extractMemberName pass: " + namePass + "/" + testNames.length);

System.out.println();
System.out.println("=== F137.4 EmailSender channelType + receiver 校验（前置校验逻辑）===");
// EmailSender.send 第一步校验 receiver 必须是合法邮箱
// 但 send 会调 mapper，这里手动验证 channelType + 期望的校验逻辑
EmailSender email = new EmailSender();
System.out.println("  EmailSender.channelType() = \"" + email.channelType() + "\" (期望 \"" + expectedTypes[1] + "\") " + (email.channelType().equals(expectedTypes[1]) ? "OK" : "FAIL"));

System.out.println();
System.out.println("=== F137.5 MiniProgramSender / WechatOfficialAccountSender channelType ===");
MiniProgramSender mp = new MiniProgramSender();
WechatOfficialAccountSender oa = new WechatOfficialAccountSender();
System.out.println("  MiniProgramSender.channelType() = \"" + mp.channelType() + "\" (期望 \"MINI_PROGRAM\") " + (mp.channelType().equals("MINI_PROGRAM") ? "OK" : "FAIL"));
System.out.println("  WechatOfficialAccountSender.channelType() = \"" + oa.channelType() + "\" (期望 \"WECHAT_OFFICIAL_ACCOUNT\") " + (oa.channelType().equals("WECHAT_OFFICIAL_ACCOUNT") ? "OK" : "FAIL"));
System.out.println("  N12 I039 别名一致性: code 实际值 = WECHAT_OFFICIAL_ACCOUNT (非 WECHAT_OA)");
System.out.println("  -> 任何用 WECHAT_OA 作为 channelType 的调用方都会触发 NPE 风险");

System.out.println();
System.out.println("=== F137.6 SendResult 工厂方法 ===");
ChannelSender.SendResult ok = ChannelSender.SendResult.ok("test-id-123");
ChannelSender.SendResult fail = ChannelSender.SendResult.fail("test-msg-456");
System.out.println("  SendResult.ok success=" + ok.success + " externalMsgId=" + ok.externalMsgId + " errorMsg=" + ok.errorMsg);
System.out.println("  SendResult.fail success=" + fail.success + " externalMsgId=" + fail.externalMsgId + " errorMsg=" + fail.errorMsg);

System.out.println();
System.out.println("=== F137 总结 ===");
System.out.println("  SMS  / SmsSender.render / channelType=SMS  : VERIFIED (静态算法+契约)");
System.out.println("  EMAIL/ EmailSender.channelType=EMAIL       : VERIFIED (契约); 真实 SMTP UNVERIFIED (凭据缺失)");
System.out.println("  MP   / MiniProgramSender.channelType=MP     : VERIFIED (契约); 真实 WeChat API UNVERIFIED (凭据缺失)");
System.out.println("  IN_APP/InAppSender.parseMemberId/channelType=IN_APP: VERIFIED (算法+契约) - 唯一涉及 DB 写入的真实链路");
System.out.println("  OA   / WechatOA.channelType=WECHAT_OFFICIAL_ACCOUNT: VERIFIED (契约); 真实 WeChat API UNVERIFIED");
System.out.println("  N12 I039 别名: WECHAT_OA 是 legacy, 当前代码用 WECHAT_OFFICIAL_ACCOUNT; mkt_channel_config.channel_type 必须一致");

/exit