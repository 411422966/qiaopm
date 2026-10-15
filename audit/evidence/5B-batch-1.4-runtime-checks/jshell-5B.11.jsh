// 5B.11 WechatOfficialAccountSender.channelType() 验证（public instance）
import com.meiyetec.service.channel.WechatOfficialAccountSender;

WechatOfficialAccountSender sender = new WechatOfficialAccountSender();
String type = sender.channelType();
System.out.println("5B.11 channelType() = '" + type + "'");
System.out.println("5B.11 equals WECHAT_OFFICIAL_ACCOUNT: " + type.equals("WECHAT_OFFICIAL_ACCOUNT"));
System.out.println("5B.11 equals WECHAT_OA (SoT 旧名): " + type.equals("WECHAT_OA"));

/exit
