// === F135 ChannelDispatcher.getSender 单元测试 ===
// 验证：I061 NPE 风险 — 5 sender 全部注册 + 有效 channelType 返回非 null + 无效返回 null

import com.meiyetec.service.channel.*;
import java.util.*;

System.out.println("=== 创建 5 个 Sender 实例 ===");
SmsSender sms = new SmsSender();
EmailSender email = new EmailSender();
InAppSender inApp = new InAppSender();
MiniProgramSender mp = new MiniProgramSender();
WechatOfficialAccountSender oa = new WechatOfficialAccountSender();
System.out.println("5 sender instances created successfully");

System.out.println();
System.out.println("=== channelType() 返回值一致性检查 (N12 沿用 I039) ===");
System.out.println("SmsSender.channelType() = " + sms.channelType());
System.out.println("EmailSender.channelType() = " + email.channelType());
System.out.println("InAppSender.channelType() = " + inApp.channelType());
System.out.println("MiniProgramSender.channelType() = " + mp.channelType());
System.out.println("WechatOfficialAccountSender.channelType() = " + oa.channelType());

// 反射创建 ChannelDispatcher 实例
Class<?> dispatcherCls = Class.forName("com.meiyetec.service.channel.ChannelDispatcher");
List<ChannelSender> senders = Arrays.asList(sms, email, inApp, mp, oa);

// 用反射调用构造器（避开 @RequiredArgsConstructor 注入 channelConfigMapper）
java.lang.reflect.Constructor<?>[] ctors = dispatcherCls.getDeclaredConstructors();
System.out.println("Dispatcher declared constructors count: " + ctors.length);
for (java.lang.reflect.Constructor<?> c : ctors) {
    System.out.println("  ctor params: " + c.getParameterCount() + " types=" + Arrays.toString(c.getParameterTypes()));
}

Object dispatcher = null;
for (java.lang.reflect.Constructor<?> c : ctors) {
    if (c.getParameterCount() == 2) {
        c.setAccessible(true);
        try {
            dispatcher = c.newInstance(senders, null);
            System.out.println("Dispatcher instance created via 2-param ctor: senders + null Mapper");
            break;
        } catch (Exception e) {
            System.out.println("Failed 2-param ctor: " + e.getMessage());
        }
    }
}
if (dispatcher == null) {
    System.out.println("ERROR: could not instantiate ChannelDispatcher");
    System.exit(0);
}

java.lang.reflect.Method getSender = dispatcherCls.getMethod("getSender", String.class);
System.out.println("getSender method: " + getSender);

System.out.println();
System.out.println("=== 有效 channelType 测试 ===");
String[] validTypes = {"SMS", "EMAIL", "IN_APP", "MINI_PROGRAM", "WECHAT_OFFICIAL_ACCOUNT"};
for (String t : validTypes) {
    Object result = getSender.invoke(dispatcher, t);
    String typeName = result == null ? "null" : result.getClass().getSimpleName();
    System.out.println("channelType=" + t + " -> result=" + typeName + " | match=" + (result != null));
}

System.out.println();
System.out.println("=== 无效 channelType 测试 (I061 NPE 风险实证) ===");
String[] invalidTypes = {"UNKNOWN", "WECHAT_OA", "WECHAT", "wechat_mp", "", null, "SMS_EXTRA", "sms", "sms "};
for (String t : invalidTypes) {
    try {
        Object result = getSender.invoke(dispatcher, t);
        String typeName = result == null ? "null" : result.getClass().getSimpleName();
        String display = t == null ? "null" : "'" + t + "'";
        System.out.println("channelType=" + display + " -> result=" + typeName);
    } catch (Exception e) {
        String display = t == null ? "null" : "'" + t + "'";
        System.out.println("channelType=" + display + " -> EXCEPTION: " + e.getClass().getSimpleName() + " " + e.getMessage());
    }
}

System.out.println();
System.out.println("=== 健康检查 healthCheckAll 方法存在性 ===");
try {
    java.lang.reflect.Method healthCheckAll = dispatcherCls.getMethod("healthCheckAll");
    System.out.println("healthCheckAll exists: " + healthCheckAll);
} catch (NoSuchMethodException e) {
    System.out.println("healthCheckAll NOT FOUND: " + e.getMessage());
}
try {
    java.lang.reflect.Method rateLimitCheck = dispatcherCls.getMethod("rateLimitCheck", Long.class, Long.class, String.class);
    System.out.println("rateLimitCheck exists: " + rateLimitCheck);
} catch (NoSuchMethodException e) {
    System.out.println("rateLimitCheck NOT FOUND: " + e.getMessage());
}

/exit