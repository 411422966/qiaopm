/*
 * ============================================================
 * 非正式工具 —— 不是项目守门脚本，不接入 CI / PR 必跑清单
 * ============================================================
 * 用途：只读验证 I154 缺陷 ④（LtvTrainingService 特征重要性权重索引错位）
 *       与 ⑤（computeStats 均值计算 bug）的修复效果。
 * 场景：一次性取证，在系统临时目录编译运行，不入仓、不参与构建。
 * 运行方式：
 *   cd <backend>
 *   mvn dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
 *   javac -nowarn -encoding UTF-8 -proc:none -cp "target\classes;<cp.txt>" -d . VerifyLtvFix.java
 *   java  -cp "target\classes;<cp.txt>;." VerifyLtvFix [输出文件路径]
 * 参数：可选第 1 个参数 = 输出文件路径（UTF-8 写入）；缺省写 stdout。
 * 退出码：0 = 验证通过；非 0 = 运行异常（异常栈打印到 stderr）。
 * 失败处理：若 "PASS(均值精确)" 或 "新映射是否等于 |w[1]|" 为 false，
 *          说明修复未生效，应回退检查源码而非重跑。
 * 依赖：需 JDK 17 + backend/target/classes（先执行 mvn package）。
 * 注意：使用 sun.misc.Unsafe 分配实例（LtvTrainingService 无无参构造，
 *       避免触发 Spring 注入）；私有方法经反射调用，纯内存计算，不写库。
 * ============================================================
 */
import com.meiyetec.service.LtvTrainingService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 只读验证脚本（临时，位于系统临时目录，不入仓）：
 * 验证 LtvTrainingService.computeStats 均值修复 与 特征重要性权重索引修复。
 */
public class VerifyLtvFix {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(args[0]), true, "UTF-8"));
        }
        // 类无无参构造，使用 Unsafe 分配实例（不触发 Spring 注入）
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) uf.get(null);
        LtvTrainingService svc = (LtvTrainingService) unsafe.allocateInstance(LtvTrainingService.class);

        Field fc = LtvTrainingService.class.getDeclaredField("FEATURE_COLUMNS");
        fc.setAccessible(true);
        List<String> cols = (List<String>) fc.get(null);

        Method computeStats = LtvTrainingService.class.getDeclaredMethod(
                "computeStats", List.class, double[].class, double[].class);
        computeStats.setAccessible(true);

        Method gradientDescent = LtvTrainingService.class.getDeclaredMethod(
                "gradientDescent", List.class, double[].class, double[].class, double.class, int.class);
        gradientDescent.setAccessible(true);

        Method buildFormula = LtvTrainingService.class.getDeclaredMethod(
                "buildFormula", double[].class, double[].class, double[].class);
        buildFormula.setAccessible(true);

        int n = 300;
        int d = cols.size();
        Random rnd = new Random(7);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 0; i < d; i++) {
                // 第 0 列 recency_days 给一个可辨识的均值
                double v = (i == 0) ? (100 + r) : rnd.nextDouble() * 50;
                row.put(cols.get(i), v);
            }
            // label 与第 0 列线性相关，确保权重非零、bias 非零
            row.put("label", 3.0 * (100 + r) + 7.0);
            rows.add(row);
        }

        double[] means = new double[d];
        double[] stds = new double[d];
        computeStats.invoke(svc, rows, means, stds);

        // 期望均值：第 0 列 = (100 + 0 + 100 + 299)/2
        double expectedMean0 = 0;
        for (int r = 0; r < n; r++) expectedMean0 += (100 + r);
        expectedMean0 /= n;
        System.out.println("=== ⑤ computeStats 均值 ===");
        System.out.println("computed means[0] = " + means[0]);
        System.out.println("expected  means[0] = " + expectedMean0);
        System.out.println("delta             = " + Math.abs(means[0] - expectedMean0));
        System.out.println("PASS(均值精确)     = " + (Math.abs(means[0] - expectedMean0) < 1e-9));

        // ④ 权重索引
        double[] w = (double[]) gradientDescent.invoke(svc, rows, means, stds, 0.01, 400);
        System.out.println();
        System.out.println("=== ④ 特征重要性权重索引 ===");
        System.out.println("weights[0] (bias)      = " + w[0]);
        System.out.println("weights[1] (feature0)   = " + w[1]);
        System.out.println("weights[2] (feature1)   = " + w[2]);

        double oldMapping = Math.abs(w[0]);   // 旧代码 importance = |weights[i]|
        double newMapping = Math.abs(w[1]);   // 新代码 importance = |weights[i+1]|
        System.out.println("旧映射 recency_days 重要性 = " + oldMapping + "  (=bias 的绝对值, 错误)");
        System.out.println("新映射 recency_days 重要性 = " + newMapping + "  (=feature0 权重, 正确)");
        System.out.println("两者是否不同(可证伪)       = " + (Math.abs(oldMapping - newMapping) > 1e-9));
        System.out.println("新映射是否等于 |w[1]|     = " + (Math.abs(newMapping - Math.abs(w[1])) < 1e-12));

        String formula = (String) buildFormula.invoke(svc, w, means, stds);
        System.out.println();
        System.out.println("=== 公式前 160 字符（对照 feature0 权重）===");
        System.out.println(formula.substring(0, Math.min(160, formula.length())));

        // 反证：旧代码下 recency_days 会拿到 bias
        System.out.println();
        System.out.println("反证：若仍用 weights[i]，recency_days 重要性将为 " + oldMapping
                + "，而非真实权重 " + newMapping);
    }
}
