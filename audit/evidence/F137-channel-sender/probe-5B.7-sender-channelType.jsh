// F137 Phase 5B — 5 Sender 算法单元测试
// 测试目标：
//   SmsSender.render(template, vars) 静态方法 — 替换 ${var}
//   5 sender channelType() 反射调 — 验证 channelType 字符串精确匹配

// ==== 测试 1: SmsSender.render 静态方法（最简单） ====
System.out.println("=== Test 1: SmsSender.render 静态方法 ===");
String tpl1 = "亲爱的 ${name}，您的订单 ${orderNo} 已发货。";
Map<String, Object> vars1 = new HashMap<>();
vars1.put("name", "张三");
vars1.put("orderNo", "ORD-2026-001");
String result1 = com.meiyetec.service.channel.SmsSender.render(tpl1, vars1);
System.out.println("Input:    " + tpl1);
System.out.println("Vars:     " + vars1);
System.out.println("Output:   " + result1);
System.out.println("Expected: 亲爱的 张三，您的订单 ORD-2026-001 已发货。");
System.out.println("MATCH:    " + result1.equals("亲爱的 张三，您的订单 ORD-2026-001 已发货。"));

System.out.println();
System.out.println("=== Test 1b: SmsSender.render null template ===");
String result2 = com.meiyetec.service.channel.SmsSender.render(null, vars1);
System.out.println("null template + non-null vars: " + result2);
System.out.println("Expected: null (passthrough)");
System.out.println("MATCH:    " + (result2 == null));

System.out.println();
System.out.println("=== Test 1c: SmsSender.render null vars ===");
String result3 = com.meiyetec.service.channel.SmsSender.render(tpl1, null);
System.out.println("non-null template + null vars: " + result3);
System.out.println("Expected: passthrough (no replacement)");
System.out.println("MATCH:    " + tpl1.equals(result3));

System.out.println();
System.out.println("=== Test 1d: SmsSender.render 缺 var ===");
String result4 = com.meiyetec.service.channel.SmsSender.render(tpl1, new HashMap<>());
System.out.println("Empty vars: " + result4);
System.out.println("Expected: 亲爱的 ，您的订单  已发货。 (empty replacement)");
System.out.println("MATCH:    " + result4.equals("亲爱的 ，您的订单  已发货。"));

System.out.println();
System.out.println("=== Test 1e: SmsSender.render 特殊字符转义 ===");
String tpl5 = "价格: ${price} 元";
Map<String, Object> vars5 = new HashMap<>();
vars5.put("price", "$10.99");
String result5 = com.meiyetec.service.channel.SmsSender.render(tpl5, vars5);
System.out.println("Special char $: " + result5);
System.out.println("Expected: 价格: $10.99 元 (Matcher.quoteReplacement escapes $)");
System.out.println("MATCH:    " + result5.equals("价格: $10.99 元"));

System.out.println();
System.out.println("=== Test 2: 5 Sender channelType() 反射读取 ===");
String[] senderClasses = {
    "com.meiyetec.service.channel.SmsSender",
    "com.meiyetec.service.channel.EmailSender",
    "com.meiyetec.service.channel.MiniProgramSender",
    "com.meiyetec.service.channel.InAppSender",
    "com.meiyetec.service.channel.WechatOfficialAccountSender"
};
String[] expectedTypes = {"SMS", "EMAIL", "MINI_PROGRAM", "IN_APP", "WECHAT_OFFICIAL_ACCOUNT"};

for (int i = 0; i < senderClasses.length; i++) {
    try {
        Class<?> cls = Class.forName(senderClasses[i]);
        Object instance = cls.getDeclaredConstructor().newInstance();
        java.lang.reflect.Method m = cls.getMethod("channelType");
        Object type = m.invoke(instance);
        String actual = (String) type;
        boolean match = expectedTypes[i].equals(actual);
        System.out.println(senderClasses[i] + ".channelType() = '" + actual + "' (expected '" + expectedTypes[i] + "') MATCH=" + match);
    } catch (Exception e) {
        System.out.println(senderClasses[i] + " FAIL: " + e.getMessage());
    }
}

System.out.println();
System.out.println("=== Test 3: I039 别名漂移验证（DB 中 channelType='MP' 无 sender）===");
// DB 中存在的 channelType 但 sender 未注册（来自 F118 健康检查响应）：
//   id=3 channelType="MP" 公众号模板消息 → 期望 getSender("MP") = null
//   id=10 channelType="PUSH" → 期望 getSender("PUSH") = null
//   id=13 channelType="SMS" → 期望 getSender("SMS") = 非 null
// 这部分在 F135 jshell 脚本中验证

/exit
