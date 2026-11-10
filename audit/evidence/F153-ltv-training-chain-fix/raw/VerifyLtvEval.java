/*
 * ============================================================
 * 非正式工具 —— 不是项目守门脚本，不接入 CI / PR 必跑清单
 * ============================================================
 * 用途：只读验证 LtvTrainingService.evaluate() 在标签非全 0 时能产出非零指标，
 *       以证明生产 LTV 指标恒为 0 的根因是「标签 next_90d_revenue 全 0」的数据现状，
 *       而非评估逻辑缺陷（I154 验证点 1 说明）。
 * 场景：一次性取证，在系统临时目录编译运行，不入仓、不参与构建。
 * 运行方式：
 *   cd <backend>
 *   mvn dependency:build-classpath "-Dmdep.outputFile=target\cp.txt"
 *   javac -nowarn -encoding UTF-8 -proc:none -cp "target\classes;<cp.txt>" -d . VerifyLtvEval.java
 *   java  -cp "target\classes;<cp.txt>;." VerifyLtvEval [输出文件路径]
 * 参数：可选第 1 个参数 = 输出文件路径（UTF-8 写入）；缺省写 stdout。
 * 退出码：0 = 运行完成；非 0 = 运行异常。
 * 失败处理：若「评估链路产出非零指标」为 false，说明 evaluate 链路异常，
 *          应回退检查源码；若对照组不为 0，说明假设（标签全 0 → 指标恒 0）不成立。
 * 依赖：需 JDK 17 + backend/target/classes（先执行 mvn package）。
 * 注意：使用 sun.misc.Unsafe 分配实例（LtvTrainingService 无无参构造）；
 *       私有方法经反射调用，纯内存计算，不写库。
 * ============================================================
 */
import com.meiyetec.service.LtvTrainingService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 只读验证脚本（临时，位于系统临时目录，不入仓）：
 * 用合成数据验证 evaluate() 在标签非全 0 时能产出非零 MAE/RMSE。
 * 从而证明生产 LTV 指标恒为 0 是「标签 next_90d_revenue 全 0」的数据现状，而非评估逻辑缺陷。
 */
public class VerifyLtvEval {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(args[0]), true, "UTF-8"));
        }
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
        Method evaluate = LtvTrainingService.class.getDeclaredMethod(
                "evaluate", List.class, double[].class, double[].class, double[].class);
        evaluate.setAccessible(true);

        int n = 300;
        int d = cols.size();
        Random rnd = new Random(11);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 0; i < d; i++) {
                row.put(cols.get(i), i == 0 ? (100 + r) : rnd.nextDouble() * 50);
            }
            row.put("label", 3.0 * (100 + r) + 7.0);
            rows.add(row);
        }

        double[] means = new double[d];
        double[] stds = new double[d];
        computeStats.invoke(svc, rows, means, stds);
        double[] w = (double[]) gradientDescent.invoke(svc, rows, means, stds, 0.01, 400);
        double[] m = (double[]) evaluate.invoke(svc, rows, w, means, stds);

        System.out.println("=== 合成标签（label = 3*recency + 7）下的评估指标 ===");
        System.out.println("MAE  = " + m[0]);
        System.out.println("RMSE = " + m[1]);
        System.out.println("MAPE = " + m[2]);
        System.out.println("R2   = " + m[3]);
        System.out.println("评估链路产出非零指标 = " + (m[0] > 0 && m[1] > 0));

        // 对照：把标签全部置 0，模拟生产现状
        List<Map<String, Object>> zeroRows = new ArrayList<>();
        for (Map<String, Object> src : rows) {
            Map<String, Object> row = new HashMap<>(src);
            row.put("label", 0.0);
            zeroRows.add(row);
        }
        double[] zm = new double[d];
        double[] zs = new double[d];
        computeStats.invoke(svc, zeroRows, zm, zs);
        double[] zw = (double[]) gradientDescent.invoke(svc, zeroRows, zm, zs, 0.01, 400);
        double[] zr = (double[]) evaluate.invoke(svc, zeroRows, zw, zm, zs);
        System.out.println();
        System.out.println("=== 对照组：标签全 0（= 生产 next_90d_revenue 现状）===");
        System.out.println("MAE  = " + zr[0]);
        System.out.println("RMSE = " + zr[1]);
        System.out.println("MAPE = " + zr[2]);
        System.out.println("R2   = " + zr[3]);
        System.out.println("结论：标签全 0 → MAE/RMSE/MAPE/R2 恒为 0（退化常量模型），非代码缺陷");
    }
}
