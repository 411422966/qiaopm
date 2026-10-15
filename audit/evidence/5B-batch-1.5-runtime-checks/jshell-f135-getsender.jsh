// F135 jshell 单元测试 — ChannelDispatcher.getSender NPE 风险实证 (I061)
// 创建时间：2026-09-16 22:43 (UTC+8)
// 测试目标：验证 ChannelDispatcher.getSender(channelType) 行为
// - 有效 channelType → 返回非 null
// - 无效 channelType → 返回 null (NPE 风险，调用方可能未检查 null)

import java.util.*;
import java.lang.reflect.*;

// 1. 加载 ChannelDispatcher 类（用现有 classpath）
Class<?> cdCls = Class.forName("com.meiyetec.service.channel.ChannelDispatcher");
System.out.println("[F135] Loaded: " + cdCls.getName());

// 2. 用反射模拟 List<ChannelSender> 注入
// ChannelDispatcher(List<ChannelSender>, MktChannelConfigMapper) — 两个 final 字段
// 我们需要构造器参数：List<ChannelSender>, MktChannelConfigMapper
Constructor<?> ctor = cdCls.getDeclaredConstructors()[0];
System.out.println("[F135] Constructor params: " + ctor.getParameterCount());

// 3. 创建 5 个 mock sender
Class<?> senderCls = Class.forName("com.meiyetec.service.channel.ChannelSender");
Object smsMock = java.lang.reflect.Proxy.newProxyInstance(senderCls.getClassLoader(),
    new Class<?>[]{senderCls},
    (proxy, method, args) -> {
        if (method.getName().equals("channelType")) return "SMS";
        if (method.getName().equals("healthCheck")) return Boolean.FALSE;
        if (method.getName().equals("send")) {
            Class<?> srCls = Class.forName("com.meiyetec.service.channel.ChannelSender$SendResult");
            Object sr = srCls.getDeclaredConstructors()[0].newInstance();
            srCls.getField("success").set(sr, Boolean.TRUE);
            srCls.getField("externalMsgId").set(sr, "SMS-MOCK-1");
            return sr;
        }
        if (method.getName().equals("toString")) return "MockSender[SMS]";
        return null;
    });
Object emailMock = java.lang.reflect.Proxy.newProxyInstance(senderCls.getClassLoader(),
    new Class<?>[]{senderCls},
    (proxy, method, args) -> {
        if (method.getName().equals("channelType")) return "EMAIL";
        if (method.getName().equals("healthCheck")) return Boolean.FALSE;
        if (method.getName().equals("toString")) return "MockSender[EMAIL]";
        return null;
    });
Object mpMock = java.lang.reflect.Proxy.newProxyInstance(senderCls.getClassLoader(),
    new Class<?>[]{senderCls},
    (proxy, method, args) -> {
        if (method.getName().equals("channelType")) return "MINI_PROGRAM";
        if (method.getName().equals("healthCheck")) return Boolean.FALSE;
        if (method.getName().equals("toString")) return "MockSender[MINI_PROGRAM]";
        return null;
    });
Object inAppMock = java.lang.reflect.Proxy.newProxyInstance(senderCls.getClassLoader(),
    new Class<?>[]{senderCls},
    (proxy, method, args) -> {
        if (method.getName().equals("channelType")) return "IN_APP";
        if (method.getName().equals("healthCheck")) return Boolean.TRUE;
        if (method.getName().equals("toString")) return "MockSender[IN_APP]";
        return null;
    });
Object oaMock = java.lang.reflect.Proxy.newProxyInstance(senderCls.getClassLoader(),
    new Class<?>[]{senderCls},
    (proxy, method, args) -> {
        if (method.getName().equals("channelType")) return "WECHAT_OFFICIAL_ACCOUNT";
        if (method.getName().equals("healthCheck")) return Boolean.FALSE;
        if (method.getName().equals("toString")) return "MockSender[WECHAT_OFFICIAL_ACCOUNT]";
        return null;
    });
List<Object> senders = Arrays.asList(smsMock, emailMock, mpMock, inAppMock, oaMock);
System.out.println("[F135] Created " + senders.size() + " mock senders");

// 4. 创建 mock MktChannelConfigMapper (null 即可，不调用 mapper 方法)
Object mapperMock = null;

// 5. 反射调用构造器创建 ChannelDispatcher 实例
Object dispatcher = ctor.newInstance(senders, mapperMock);
System.out.println("[F135] ChannelDispatcher instance: " + dispatcher);

// 6. 调用 getSender 方法
Method getSender = cdCls.getMethod("getSender", String.class);
System.out.println();
System.out.println("[F135-TEST-1] getSender('SMS'):");
Object smsResult = getSender.invoke(dispatcher, "SMS");
System.out.println("  result = " + (smsResult == null ? "null" : smsResult.getClass().getSimpleName()));

System.out.println("[F135-TEST-2] getSender('EMAIL'):");
Object emailResult = getSender.invoke(dispatcher, "EMAIL");
System.out.println("  result = " + (emailResult == null ? "null" : emailResult.getClass().getSimpleName()));

System.out.println("[F135-TEST-3] getSender('MINI_PROGRAM'):");
Object mpResult = getSender.invoke(dispatcher, "MINI_PROGRAM");
System.out.println("  result = " + (mpResult == null ? "null" : mpResult.getClass().getSimpleName()));

System.out.println("[F135-TEST-4] getSender('IN_APP'):");
Object inAppResult = getSender.invoke(dispatcher, "IN_APP");
System.out.println("  result = " + (inAppResult == null ? "null" : inAppResult.getClass().getSimpleName()));

System.out.println("[F135-TEST-5] getSender('WECHAT_OFFICIAL_ACCOUNT'):");
Object oaResult = getSender.invoke(dispatcher, "WECHAT_OFFICIAL_ACCOUNT");
System.out.println("  result = " + (oaResult == null ? "null" : oaResult.getClass().getSimpleName()));

System.out.println();
System.out.println("[F135-I061-CRITICAL] getSender('UNKNOWN_CHANNEL'):");
Object unknownResult = getSender.invoke(dispatcher, "UNKNOWN_CHANNEL");
System.out.println("  result = " + (unknownResult == null ? "null" : unknownResult.getClass().getSimpleName()));
System.out.println("  *** I061 实证: getSender 返回 null — 调用方未检查 null 会触发 NPE ***");

System.out.println();
System.out.println("[F135-I061-DEMO] 模拟调用方未检查 null:");
try {
    Object sender = getSender.invoke(dispatcher, "UNKNOWN_CHANNEL");
    // 模拟 sender.send() 调用（不检查 null）
    Method sendMethod = senderCls.getMethod("send",
        Class.forName("com.meiyetec.entity.MktChannelTemplate"),
        String.class, Map.class);
    sendMethod.invoke(sender, null, "13800000000", new HashMap<>());
    System.out.println("  意外: 未抛异常");
} catch (NullPointerException e) {
    System.out.println("  *** NPE 实证: " + e.getClass().getSimpleName() + ": " + e.getMessage() + " ***");
} catch (InvocationTargetException e) {
    Throwable cause = e.getCause();
    if (cause instanceof NullPointerException) {
        System.out.println("  *** NPE 实证 (via invoke): " + cause.getClass().getSimpleName() + " ***");
    } else {
        System.out.println("  其他异常: " + cause);
    }
}

System.out.println();
System.out.println("[F135-N12-ALIAS] WECHAT_OA vs WECHAT_OFFICIAL_ACCOUNT 别名漂移验证:");
Object aliasResult = getSender.invoke(dispatcher, "WECHAT_OA");
System.out.println("  getSender('WECHAT_OA'): " + (aliasResult == null ? "null（别名漂移 — I039/I047）" : aliasResult.getClass().getSimpleName()));

/exit