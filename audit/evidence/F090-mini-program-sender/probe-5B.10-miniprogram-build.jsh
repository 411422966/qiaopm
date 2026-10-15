import com.meiyetec.service.channel.MiniProgramSender;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

System.out.println("=== F090 MiniProgramSender.buildSubscribeData keyword1..5 \u5b9e\u8bc1 ===");
System.out.println();

MiniProgramSender mp = new MiniProgramSender();

Method buildData = mp.getClass().getDeclaredMethod("buildSubscribeData", String.class, Map.class, String.class);
buildData.setAccessible(true);

System.out.println("--- Case 1: variables JSON \u914d\u7f6e 5 \u4e2a keyword ---");
Map<String, Object> vars = new HashMap<>();
vars.put("customerName", "Alice");
vars.put("serviceName", "Facial");
vars.put("bookingDate", "2026-10-01");
vars.put("bookingTime", "14:00");
vars.put("bookingNo", "BK-001");

String variables1 = "[\"customerName\",\"serviceName\",\"bookingDate\",\"bookingTime\",\"bookingNo\"]";
String r1 = (String) buildData.invoke(mp, "\u9884\u7ea6\u786e\u8ba4\u6a21\u677f", vars, variables1);
System.out.println("Variables: " + variables1);
System.out.println("Output: " + r1);
System.out.println("PASS (keyword1..5 \u90fd\u751f\u6210): " +
    (r1.contains("keyword1") && r1.contains("keyword2") && r1.contains("keyword3") &&
     r1.contains("keyword4") && r1.contains("keyword5")));
System.out.println("PASS (\u5305\u542b\u53d8\u91cf\u503c Alice): " + r1.contains("Alice"));
System.out.println("PASS (\u5305\u542b\u53d8\u91cf\u503c Facial): " + r1.contains("Facial"));
System.out.println("PASS (\u5305\u542b\u53d8\u91cf\u503c BK-001): " + r1.contains("BK-001"));

System.out.println();
System.out.println("--- Case 2: variables \u7a7a (\u9ed8\u8ba4\u8d70 content \u4f5c\u4e3a keyword1) ---");
Map<String, Object> simpleVars = new HashMap<>();
simpleVars.put("x", "y");
String r2 = (String) buildData.invoke(mp, "\u9ed8\u8ba4\u5185\u5bb9", simpleVars, null);
System.out.println("Output: " + r2);
System.out.println("PASS (keyword1 \u4ec5 1 \u4e2a): " + r2.contains("keyword1") && !r2.contains("keyword2"));
System.out.println("PASS (\u9ed8\u8ba4 value \u5305\u542b\u539f\u5185\u5bb9): " + r2.contains("\u9ed8\u8ba4\u5185\u5bb9"));

System.out.println();
System.out.println("--- Case 3: variables \u8d85\u8fc7 5 \u4e2a\uff08\u88c1\u526a\u5230 5\uff09---");
Map<String, Object> manyVars = new HashMap<>();
for (int i = 1; i <= 7; i++) manyVars.put("k" + i, "v" + i);
String variables3 = "[\"k1\",\"k2\",\"k3\",\"k4\",\"k5\",\"k6\",\"k7\"]";
String r3 = (String) buildData.invoke(mp, "c", manyVars, variables3);
System.out.println("Output: " + r3);
System.out.println("PASS (\u6700\u591a 5 \u4e2a keyword): " +
    (r3.contains("keyword1") && r3.contains("keyword5") && !r3.contains("keyword6")));

System.out.println();
System.out.println("--- Case 4: channelType() ---");
String ct = mp.channelType();
System.out.println("channelType = " + ct);
System.out.println("PASS (\"MINI_PROGRAM\"): " + "MINI_PROGRAM".equals(ct));

System.out.println();
System.out.println("=== F090 \u5b9e\u8bc1\u5b8c\u6210 ===");

/exit
