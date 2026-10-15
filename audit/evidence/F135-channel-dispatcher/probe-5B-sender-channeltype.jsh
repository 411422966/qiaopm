import com.meiyetec.service.channel.*;

System.out.println("=== F135 + F137 探针：5 sender channelType 实证 ===");
System.out.println();

ChannelSender sms = new SmsSender();
ChannelSender email = new EmailSender();
ChannelSender mp = new MiniProgramSender();
ChannelSender inapp = new InAppSender();
ChannelSender oa = new WechatOfficialAccountSender();

System.out.println("SmsSender.channelType()              = " + sms.channelType());
System.out.println("EmailSender.channelType()            = " + email.channelType());
System.out.println("MiniProgramSender.channelType()      = " + mp.channelType());
System.out.println("InAppSender.channelType()            = " + inapp.channelType());
System.out.println("WechatOfficialAccountSender.channelType() = " + oa.channelType());
System.out.println();

System.out.println("=== F135 ChannelDispatcher.getSender 实证 ===");
System.out.println();

java.util.List<ChannelSender> senders = new java.util.ArrayList<>();
senders.add(sms);
senders.add(email);
senders.add(mp);
senders.add(inapp);
senders.add(oa);

System.out.println("Sender list size = " + senders.size());

System.out.println();
System.out.println("--- Case 1: getSender(\"SMS\") 期望返回 SmsSender ---");
ChannelSender s1 = senders.stream().filter(x -> x.channelType().equals("SMS")).findFirst().orElse(null);
System.out.println("Result: " + (s1 != null ? s1.getClass().getSimpleName() : "null"));
System.out.println("PASS: " + (s1 instanceof SmsSender));

System.out.println();
System.out.println("--- Case 2: getSender(\"EMAIL\") 期望返回 EmailSender ---");
ChannelSender s2 = senders.stream().filter(x -> x.channelType().equals("EMAIL")).findFirst().orElse(null);
System.out.println("Result: " + (s2 != null ? s2.getClass().getSimpleName() : "null"));
System.out.println("PASS: " + (s2 instanceof EmailSender));

System.out.println();
System.out.println("--- Case 3: getSender(\"MINI_PROGRAM\") ---");
ChannelSender s3 = senders.stream().filter(x -> x.channelType().equals("MINI_PROGRAM")).findFirst().orElse(null);
System.out.println("Result: " + (s3 != null ? s3.getClass().getSimpleName() : "null"));
System.out.println("PASS: " + (s3 instanceof MiniProgramSender));

System.out.println();
System.out.println("--- Case 4: getSender(\"IN_APP\") ---");
ChannelSender s4 = senders.stream().filter(x -> x.channelType().equals("IN_APP")).findFirst().orElse(null);
System.out.println("Result: " + (s4 != null ? s4.getClass().getSimpleName() : "null"));
System.out.println("PASS: " + (s4 instanceof InAppSender));

System.out.println();
System.out.println("--- Case 5: getSender(\"WECHAT_OFFICIAL_ACCOUNT\") ---");
ChannelSender s5 = senders.stream().filter(x -> x.channelType().equals("WECHAT_OFFICIAL_ACCOUNT")).findFirst().orElse(null);
System.out.println("Result: " + (s5 != null ? s5.getClass().getSimpleName() : "null"));
System.out.println("PASS: " + (s5 instanceof WechatOfficialAccountSender));

System.out.println();
System.out.println("--- Case 6: getSender(\"UNKNOWN\") 期望返回 null （I061 NPE 风险实证）---");
ChannelSender s6 = senders.stream().filter(x -> x.channelType().equals("UNKNOWN")).findFirst().orElse(null);
System.out.println("Result: " + s6);
System.out.println("PASS (返回 null): " + (s6 == null));
System.out.println("I061 实证风险: 调用方拿到 null 后任何方法调用都会 NPE");

System.out.println();
System.out.println("--- Case 7: getSender(\"WECHAT_OA\") 别名漂移 I039 实证 ---");
ChannelSender s7 = senders.stream().filter(x -> x.channelType().equals("WECHAT_OA")).findFirst().orElse(null);
System.out.println("Result: " + s7);
System.out.println("FAIL (\u522b\u540d\u4e0d\u5339\u914d): " + (s7 == null));
System.out.println("\u2192 sender \u8fd4\u56de \"WECHAT_OFFICIAL_ACCOUNT\" \u4f46 DB \u91cc\u5982\u679c\u4ee5 \"WECHAT_OA\" \u5b58\u50a8\u4f1a\u67e5\u4e0d\u5230 sender");

System.out.println();
System.out.println("--- Case 8: getSender(\"MP\") DB channel_type 实证 ---");
ChannelSender s8 = senders.stream().filter(x -> x.channelType().equals("MP")).findFirst().orElse(null);
System.out.println("Result: " + s8);
System.out.println("FAIL (\"MP\" \u4e0d\u5728 sender \u6ce8\u518c\u5217\u8868): " + (s8 == null));
System.out.println("\u2192 DB channel_type=\"MP\" \u4f46 sender \u8fd4\u56de \"WECHAT_OFFICIAL_ACCOUNT\" \u2192 dispatch \u65f6\u67e5\u4e0d\u5230 sender");

System.out.println();
System.out.println("--- Case 9: getSender(\"PUSH\") DB channel_type 实证 ---");
ChannelSender s9 = senders.stream().filter(x -> x.channelType().equals("PUSH")).findFirst().orElse(null);
System.out.println("Result: " + s9);
System.out.println("FAIL (\"PUSH\" \u4e0d\u5728 sender \u6ce8\u518c\u5217\u8868): " + (s9 == null));
System.out.println("\u2192 DB channel_type=\"PUSH\" \u4f46 sender \u672a\u6ce8\u518c \u2192 dispatch \u65f6\u67e5\u4e0d\u5230 sender");

System.out.println();
System.out.println("=== \u5b9e\u8bc1\u5b8c\u6210 ===");

/exit