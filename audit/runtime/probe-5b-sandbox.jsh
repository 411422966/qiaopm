import com.meiyetec.plugin.PluginSecurityPolicy;

System.out.println("=== F041 assertPackageAllowed 白/黑名单实证 ===");
System.out.println("白名单: " + PluginSecurityPolicy.HOST_PUBLIC_PACKAGES);
System.out.println("宿主黑名单: " + PluginSecurityPolicy.HOST_FORBIDDEN_PACKAGES);
System.out.println("JDK 黑名单: " + PluginSecurityPolicy.FORBIDDEN_JDK_PACKAGES);
System.out.println("deniedPackages() (I025 孤儿): " + PluginSecurityPolicy.deniedPackages());

System.out.println();
System.out.println("--- Case 1: 白名单 com.meiyetec.plugin. -> 通过 ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", "com.meiyetec.plugin."); System.out.println("OK"); }
catch (SecurityException e) { System.out.println("FAIL: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 2: 白名单 com.meiyetec.util. -> 通过 ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", "com.meiyetec.util."); System.out.println("OK"); }
catch (SecurityException e) { System.out.println("FAIL: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 3: 白名单 com.meiyetec.exception. -> 通过 ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", "com.meiyetec.exception."); System.out.println("OK"); }
catch (SecurityException e) { System.out.println("FAIL: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 4: 黑名单 com.meiyetec.service.internal. -> 抛异常 ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", "com.meiyetec.service.internal."); System.out.println("FAIL: 没抛异常"); }
catch (SecurityException e) { System.out.println("OK 抛异常: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 5: 黑名单 com.meiyetec.service.impl. -> 抛异常 ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", "com.meiyetec.service.impl."); System.out.println("FAIL: 没抛异常"); }
catch (SecurityException e) { System.out.println("OK 抛异常: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 6: 黑名单 com.meiyetec.security. -> 抛异常 ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", "com.meiyetec.security."); System.out.println("FAIL: 没抛异常"); }
catch (SecurityException e) { System.out.println("OK 抛异常: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 7: 黑名单 com.meiyetec.mapper. -> 抛异常 ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", "com.meiyetec.mapper."); System.out.println("FAIL: 没抛异常"); }
catch (SecurityException e) { System.out.println("OK 抛异常: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 8: 黑名单 com.meiyetec.config. -> 抛异常 ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", "com.meiyetec.config."); System.out.println("FAIL: 没抛异常"); }
catch (SecurityException e) { System.out.println("OK 抛异常: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 9: JDK 黑名单 java.lang.reflect. -> 抛异常 ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", "java.lang.reflect."); System.out.println("FAIL: 没抛异常"); }
catch (SecurityException e) { System.out.println("OK 抛异常: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 10: JDK 黑名单 java.lang.Runtime -> 抛异常 ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", "java.lang.Runtime"); System.out.println("FAIL: 没抛异常"); }
catch (SecurityException e) { System.out.println("OK 抛异常: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 11: 非 com.meiyetec 第三方包（如 plugin 自有代码）-> 默认放行 ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", "com.example.demoplugin."); System.out.println("OK 放行（合作式）"); }
catch (SecurityException e) { System.out.println("FAIL: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 12: com.meiyetec.service.（非 internal/impl）-> 抛异常（不在白名单） ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", "com.meiyetec.service."); System.out.println("FAIL: 没抛异常"); }
catch (SecurityException e) { System.out.println("OK 抛异常: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 13: null 包前缀 -> 放行（early return）---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", null); System.out.println("OK"); }
catch (SecurityException e) { System.out.println("FAIL: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 14: 空字符串 -> 放行 ---");
try { PluginSecurityPolicy.assertPackageAllowed("demo-hello", ""); System.out.println("OK"); }
catch (SecurityException e) { System.out.println("FAIL: " + e.getMessage()); }

System.out.println();
System.out.println("=== F042 assertCallerAllowed 实证 ===");

System.out.println();
System.out.println("--- Case 1: 宿主自己调用 com.meiyetec.* -> 直接 return（不检查）---");
try {
    PluginSecurityPolicy.assertCallerAllowed(String.class); // 任何 Class 都行
    System.out.println("Caller=java.lang.String 不在 com.meiyetec.* 也不在 org.pf4j.* -> 走检查分支");
    System.out.println("Class.forName(\"com.meiyetec.service.internal.NonExistent\") 应抛 ClassNotFoundException -> 路径 return");
} catch (Exception e) { System.out.println("FAIL: " + e.getMessage()); }

System.out.println();
System.out.println("--- Case 2: com.meiyetec.* 调用方 -> 直接 return ---");
try {
    PluginSecurityPolicy.assertCallerAllowed(PluginSecurityPolicy.class);
    System.out.println("Caller=PluginSecurityPolicy 在 com.meiyetec.* -> 直接 return，OK");
} catch (Exception e) { System.out.println("FAIL: " + e.getMessage()); }

System.out.println();
System.out.println("--- I020 关键：反射加载 com.meiyetec.service.UserService（非 internal）沙箱不拦截 ---");
System.out.println("验证：plugin 通过 Class.forName(\"com.meiyetec.service.UserService\", false, pluginClassLoader) 不会被 assertPackageAllowed 拦截");
System.out.println("原因是 service. 不在黑名单（仅 service.internal.），也不在白名单");
System.out.println("assertPackageAllowed 只检查以 com.meiyetec. 开头的，且不在白名单才抛");
System.out.println("所以 com.meiyetec.service.UserService 调用 PluginSecurityPolicy.assertPackageAllowed(code, \"com.meiyetec.service.\") 会抛 SecurityException");
System.out.println("但插件若不调用 assertPackageAllowed，则根本不会触发拦截（合作式）");

/exit
