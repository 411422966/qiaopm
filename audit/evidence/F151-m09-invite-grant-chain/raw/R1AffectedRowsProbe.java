// ⚠️ 非正式工具（F151 R1 对照测试用的一次性 JDBC 探针，不属于 audit/scripts 正式守门序列）
// 用途：验证 MySQL Connector/J 8.0.33 在「无 useAffectedRows 参数」的默认连接下，
//       UPDATE ... SET updated_at = NOW() 的 executeUpdate() 返回值语义是 matched-rows 还是 changed-rows。
// 场景：对照 InviteService.grantInviterReward/grantInviteeReward 中 couponGrantMapper.atomicGrant() 的 affected-rows 判定。
// 参数：无（硬编码与 backend/src/main/resources/application.yml 相同的 JDBC URL）
// 退出码：0 = 两次 UPDATE 返回值均为 1（matched-rows 语义）；2 = 第二次返回 0（changed-rows 语义）；1 = 执行异常
// 失败处理：抛异常即退出码 1，stderr 打印堆栈；本工具不写库（仅 UPDATE market_coupon.updated_at）
import java.sql.*;

public class R1AffectedRowsProbe {
    private static final String URL =
        "jdbc:mysql://localhost:3307/meiyetec?useUnicode=true&characterEncoding=UTF-8"
        + "&connectionCollation=utf8mb4_bin&serverTimezone=Asia/Shanghai"
        + "&createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true";

    public static void main(String[] args) throws Exception {
        long couponId = args.length > 0 ? Long.parseLong(args[0]) : 5L;
        String updateSimple = "UPDATE market_coupon SET updated_at = NOW() WHERE id = ?";
        // atomicGrant 的真实形态：JOIN 子查询 + SET updated_at = NOW()
        String updateAtomic =
            "UPDATE market_coupon c JOIN ( SELECT id FROM market_coupon "
          + "WHERE id = ? AND status = 1 "
          + "AND (start_time IS NULL OR start_time <= NOW()) "
          + "AND (end_time IS NULL OR end_time >= NOW()) "
          + "AND ( total_count IS NULL OR total_count = 0 OR total_count > ("
          + "    SELECT COUNT(*) FROM market_coupon_grant WHERE coupon_id = ? AND status != 3) ) "
          + "AND ( per_limit IS NULL OR per_limit = 0 OR ( "
          + "    SELECT COUNT(*) FROM market_coupon_grant WHERE coupon_id = ? AND member_id = ? AND status != 3) < per_limit ) "
          + "LIMIT 1 ) sub ON c.id = sub.id SET c.updated_at = NOW() WHERE c.id = ?";

        try (Connection conn = DriverManager.getConnection(URL, "root", "root123456")) {
            conn.setAutoCommit(true);
            System.out.println("URL=" + URL);
            System.out.println("useAffectedRows(in URL)=" + URL.contains("useAffectedRows"));

            // ---- Part A: 简单形态 ----
            pressBack(conn, couponId);
            System.out.println("--- Part A: UPDATE market_coupon SET updated_at = NOW() WHERE id=? ---");
            int a1 = runUpdate(conn, updateSimple, couponId);
            int a2 = runUpdate(conn, updateSimple, couponId);
            System.out.println("A1=" + a1 + " A2=" + a2);

            // ---- Part B: atomicGrant 真实 JOIN 形态（member_id 用一个不存在的会员 id，per_limit=0 不影响） ----
            pressBack(conn, couponId);
            System.out.println("--- Part B: atomicGrant JOIN 形态 ---");
            int b1 = runUpdate(conn, updateAtomic, couponId, couponId, couponId, 999999L, couponId);
            int b2 = runUpdate(conn, updateAtomic, couponId, couponId, couponId, 999999L, couponId);
            System.out.println("B1=" + b1 + " B2=" + b2);

            // ---- Part C: 强制 changed-rows 语义对照（useAffectedRows=true）----
            String urlAffected = URL + "&useAffectedRows=true";
            try (Connection conn2 = DriverManager.getConnection(urlAffected, "root", "root123456")) {
                pressBack(conn2, couponId);
                System.out.println("--- Part C: 同一条 SQL + useAffectedRows=true ---");
                int c1 = runUpdate(conn2, updateSimple, couponId);
                int c2 = runUpdate(conn2, updateSimple, couponId);
                System.out.println("C1=" + c1 + " C2=" + c2);
            }

            System.out.println("SUMMARY A1=" + a1 + " A2=" + a2 + " B1=" + b1 + " B2=" + b2);
        }
    }

    private static void pressBack(Connection conn, long couponId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE market_coupon SET updated_at='2020-01-01 00:00:00' WHERE id=?")) {
            ps.setLong(1, couponId);
            ps.executeUpdate();
        }
    }

    private static int runUpdate(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            int n = ps.executeUpdate();
            System.out.println("  [" + System.currentTimeMillis() + "] executeUpdate=" + n);
            return n;
        }
    }
}
