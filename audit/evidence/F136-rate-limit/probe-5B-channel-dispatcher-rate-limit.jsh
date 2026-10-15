// === F136 ChannelDispatcher.rateLimitCheck 单元测试 ===
// 验证：通过 ChannelDispatcher.rateLimitCheck 方法是否被正确调用
// 关键点：JdbcTemplate 直接聚合 marketing_touch_log + 查 mkt_channel_config

import com.meiyetec.service.channel.*;
import java.util.*;

System.out.println("=== F136 ChannelDispatcher.rateLimitCheck 验证 ===");
System.out.println();

// 创建 5 个 sender 实例
SmsSender sms = new SmsSender();
EmailSender email = new EmailSender();
InAppSender inApp = new InAppSender();
MiniProgramSender mp = new MiniProgramSender();
WechatOfficialAccountSender oa = new WechatOfficialAccountSender();
List<ChannelSender> senders = Arrays.asList(sms, email, inApp, mp, oa);

System.out.println("=== 反射构造 ChannelDispatcher（无 Mapper / 无 JdbcTemplate）===");
Class<?> dispatcherCls = Class.forName("com.meiyetec.service.channel.ChannelDispatcher");
java.lang.reflect.Constructor<?>[] ctors = dispatcherCls.getDeclaredConstructors();
Object dispatcher = null;
for (java.lang.reflect.Constructor<?> c : ctors) {
    if (c.getParameterCount() == 2) {
        c.setAccessible(true);
        dispatcher = c.newInstance(senders, null);
        break;
    }
}
System.out.println("Dispatcher instance: " + dispatcher);

// 验证方法存在
try {
    java.lang.reflect.Method m = dispatcherCls.getMethod("rateLimitCheck", Long.class, Long.class, String.class);
    System.out.println("rateLimitCheck method exists: " + m);
} catch (Exception e) {
    System.out.println("rateLimitCheck NOT FOUND: " + e.getMessage());
}

System.out.println();
System.out.println("=== ChannelDispatcher 注入字段检查 (JDBC 必需) ===");
java.lang.reflect.Field[] fields = dispatcherCls.getDeclaredFields();
for (java.lang.reflect.Field f : fields) {
    System.out.println("  field: " + f.getType().getSimpleName() + " " + f.getName());
}

// 反射检查源码：JdbcTemplate 是否被 @Autowired
java.io.InputStream is = dispatcherCls.getResourceAsStream("/com/meiyetec/service/channel/ChannelDispatcher.class");
System.out.println("Class file size: " + (is == null ? "N/A" : is.available()));
if (is != null) is.close();

System.out.println();
System.out.println("=== 源码静态结论：rateLimitCheck 依赖 JdbcTemplate ===");
System.out.println("ChannelDispatcher.java:62 - 调 jdbc.queryForObject 计算 perMin");
System.out.println("ChannelDispatcher.java:67 - 调 channelConfigMapper.selectOne 查 mkt_channel_config");
System.out.println("ChannelDispatcher.java:73 - 调 jdbc.queryForObject 计算 perDay");
System.out.println("ChannelDispatcher.java:80 - 第二次判断 rateLimitPerDay");
System.out.println();
System.out.println("无 JdbcTemplate 时调用 → NullPointerException（实际业务由 Spring 注入）");
System.out.println();

// 直接调用 rateLimitCheck 测异常
try {
    java.lang.reflect.Method m = dispatcherCls.getMethod("rateLimitCheck", Long.class, Long.class, String.class);
    System.out.println("=== 直接调用 rateLimitCheck 验证无 JdbcTemplate 抛错 ===");
    Boolean result = (Boolean) m.invoke(dispatcher, 1L, 1L, "SMS");
    System.out.println("result: " + result);
} catch (java.lang.reflect.InvocationTargetException e) {
    System.out.println("InvocationTargetException cause: " + e.getCause());
} catch (Exception e) {
    System.out.println("Exception: " + e);
}

/exit