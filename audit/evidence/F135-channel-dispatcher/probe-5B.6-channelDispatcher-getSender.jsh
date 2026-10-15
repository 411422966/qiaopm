// F135 Phase 5B — ChannelDispatcher.getSender 单元测试
// 测试目标：
//   1. 5 个有效 channelType → 返回非 null sender
//   2. 2 个无效 channelType（DB 存在但无 sender: "MP", "PUSH"）→ 返回 null（N12 别名漂移）
//   3. 1 个完全未知 channelType → 返回 null（I061 NPE 风险实证）
//   4. 5 sender 注册总数 = 5

import java.util.*;
import java.lang.reflect.*;

System.out.println("=== F135: ChannelDispatcher.getSender 单元测试 ===");

// 1. 实例化 5 个真实 sender（mapper 都是 null — 我们只测 getSender 路由逻辑）
String[] senderClasses = {
    "com.meiyetec.service.channel.SmsSender",
    "com.meiyetec.service.channel.EmailSender",
    "com.meiyetec.service.channel.MiniProgramSender",
    "com.meiyetec.service.channel.InAppSender",
    "com.meiyetec.service.channel.WechatOfficialAccountSender"
};

List<Object> senders = new ArrayList<>();
for (String clsName : senderClasses) {
    Class<?> cls = Class.forName(clsName);
    Object sender = cls.getDeclaredConstructor().newInstance();
    senders.add(sender);
}
System.out.println("Sender 实例化完成: " + senders.size());

// 2. 反射构造 ChannelDispatcher
//    ChannelDispatcher 有两个 final 字段: List<ChannelSender> senders + MktChannelConfigMapper channelConfigMapper
//    @RequiredArgsConstructor 生成构造函数 (List, MktChannelConfigMapper)
//    通过反射调用构造函数
Class<?> dispatcherCls = Class.forName("com.meiyetec.service.channel.ChannelDispatcher");
Constructor<?> dispatcherCtor = dispatcherCls.getDeclaredConstructors()[0];
dispatcherCtor.setAccessible(true);

// 找到 (List, MktChannelConfigMapper) 构造器
Constructor<?> ctor = null;
for (Constructor<?> c : dispatcherCls.getDeclaredConstructors()) {
    Class<?>[] params = c.getParameterTypes();
    if (params.length == 2 && params[0] == List.class) {
        ctor = c;
        break;
    }
}
if (ctor == null) {
    System.out.println("ERROR: no (List, MktChannelConfigMapper) constructor found");
} else {
    ctor.setAccessible(true);
    Object dispatcher = ctor.newInstance(senders, null);
    System.out.println("ChannelDispatcher 实例化成功");

    // 3. 测试 getSender 方法
    Method getSender = dispatcherCls.getMethod("getSender", String.class);

    // 3.1 5 个有效 channelType
    System.out.println();
    System.out.println("=== Test 3.1: 5 个有效 channelType ===");
    String[] validTypes = {"SMS", "EMAIL", "IN_APP", "MINI_PROGRAM", "WECHAT_OFFICIAL_ACCOUNT"};
    int validMatch = 0;
    for (String t : validTypes) {
        Object sender = getSender.invoke(dispatcher, t);
        String clsName = sender == null ? "null" : sender.getClass().getSimpleName();
        boolean match = sender != null;
        if (match) validMatch++;
        System.out.println("  getSender('" + t + "') = " + clsName + "  [MATCH=" + match + "]");
    }
    System.out.println("Valid total: " + validMatch + "/5");

    // 3.2 2 个无效（DB 存在但无 sender — I039 别名漂移）
    System.out.println();
    System.out.println("=== Test 3.2: 2 个 DB 存在但无 sender（I039 别名漂移 — getSender 返 null）===");
    String[] orphanTypes = {"MP", "PUSH"};
    int orphanMatch = 0;
    for (String t : orphanTypes) {
        Object sender = getSender.invoke(dispatcher, t);
        boolean isNull = sender == null;
        if (isNull) orphanMatch++;
        System.out.println("  getSender('" + t + "') = " + (sender == null ? "null" : sender.getClass().getSimpleName()) + "  [EXPECTED_NULL=" + isNull + "]");
    }
    System.out.println("Orphan total: " + orphanMatch + "/2");

    // 3.3 1 个完全未知（I061 NPE 风险实证）
    System.out.println();
    System.out.println("=== Test 3.3: 1 个完全未知（I061 NPE 风险）===");
    Object sender3 = getSender.invoke(dispatcher, "UNKNOWN_CHANNEL_TYPE");
    boolean isNull3 = sender3 == null;
    System.out.println("  getSender('UNKNOWN_CHANNEL_TYPE') = " + (sender3 == null ? "null" : sender3.getClass().getSimpleName()) + "  [EXPECTED_NULL=" + isNull3 + "]");
    System.out.println("  I061 风险实证：调用方拿到 null 直接 .send() → NPE");

    // 3.4 List 大小
    System.out.println();
    System.out.println("=== Test 3.4: senders List size ===");
    Field sendersField = dispatcherCls.getDeclaredField("senders");
    sendersField.setAccessible(true);
    List<?> sendersList = (List<?>) sendersField.get(dispatcher);
    System.out.println("  ChannelDispatcher.senders.size() = " + sendersList.size() + " (expected 5)");
    System.out.println("  MATCH = " + (sendersList.size() == 5));

    // 3.5 null 入参
    System.out.println();
    System.out.println("=== Test 3.5: getSender(null) ===");
    Object sender4 = getSender.invoke(dispatcher, (Object) null);
    System.out.println("  getSender(null) = " + (sender4 == null ? "null" : sender4.getClass().getSimpleName()));
}

/exit
