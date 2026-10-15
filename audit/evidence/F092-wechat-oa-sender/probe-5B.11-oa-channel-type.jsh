import com.meiyetec.service.channel.WechatOfficialAccountSender;

System.out.println("=== F092 WechatOfficialAccountSender.channelType() I039 \u5b9e\u8bc1 ===");
System.out.println();

WechatOfficialAccountSender sender = new WechatOfficialAccountSender();
String ct = sender.channelType();

System.out.println("channelType() = " + ct);
System.out.println();
System.out.println("\u9884\u671f\uff1a\"WECHAT_OFFICIAL_ACCOUNT\"\uff08\u975e WECHAT_OA \u522b\u540d\uff09");
System.out.println();
System.out.println("PASS (\u8fd4\u56de WECHAT_OFFICIAL_ACCOUNT): " + "WECHAT_OFFICIAL_ACCOUNT".equals(ct));
System.out.println("PASS (\u4e0d\u8fd4\u56de WECHAT_OA \u522b\u540d):     " + !"WECHAT_OA".equals(ct));

System.out.println();
System.out.println("=== I039 \u522b\u540d\u5b9e\u8bc1\u5b8c\u6210 ===");

/exit
