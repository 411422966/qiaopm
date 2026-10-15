import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * F071 webhook 签名算法实证 — 复刻 YouzanEcommerceAdapter.verifyWebhookSignature
 *
 * 源：backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:146-168
 * 算法：md5(signSecret + rawBody) 全部小写 hex，与 signature equalsIgnoreCase
 *
 * 字段 null 防御：
 *   signSecret null/empty → false
 *   signature null/empty  → false
 *   rawBody null           → 视作 ""（算法 + ""）
 */
public class AuditVerifySig {

    /** 1:1 复刻 YouzanEcommerceAdapter.verifyWebhookSignature 算法 */
    public static boolean verifyWebhookSignature(String signSecret, String rawBody, String signature) {
        if (signSecret == null || signSecret.isEmpty()) return false;
        if (signature == null || signature.isEmpty())   return false;
        if (rawBody == null) rawBody = "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest((signSecret + rawBody).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            String expected = hex.toString();
            return expected.equalsIgnoreCase(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /** 独立计算 expected hex（用于审计追踪） */
    public static String computeExpected(String signSecret, String rawBody) {
        if (signSecret == null) return null;
        if (rawBody == null) rawBody = "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest((signSecret + rawBody).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        // ===== 测试用例 N9 实证（plan-batch-1.3.md §四 N9）=====

        // Case 1: 正常签名（验证：true）
        String secret1 = "signSecret_test_2026";
        String body1   = "{\"msg_id\":\"test-001\",\"status\":\"TRADE_ORDER_STATE_UPDATE\",\"order_no\":\"TEST-001\"}";
        String sig1    = computeExpected(secret1, body1);
        boolean r1 = verifyWebhookSignature(secret1, body1, sig1);

        // Case 2: 篡改 body（验证：false）
        String tamperedBody = body1.replace("TEST-001", "ATTACK-001");
        boolean r2 = verifyWebhookSignature(secret1, tamperedBody, sig1);

        // Case 3: 篡改 signature（验证：false）
        String tamperedSig = sig1.substring(0, sig1.length() - 1) + (sig1.charAt(sig1.length() - 1) == 'a' ? 'b' : 'a');
        boolean r3 = verifyWebhookSignature(secret1, body1, tamperedSig);

        // Case 4: 大小写防御（验证：true — equalsIgnoreCase）
        String upperSig = sig1.toUpperCase();
        boolean r4 = verifyWebhookSignature(secret1, body1, upperSig);

        // Case 5: signSecret=null（边界：false）
        boolean r5 = verifyWebhookSignature(null, body1, sig1);

        // Case 6: signature=null（边界：false）
        boolean r6 = verifyWebhookSignature(secret1, body1, null);

        // Case 7: rawBody=null（边界：与 rawBody="" 等价）
        String secret2 = "secret_empty_body_test";
        String bodyEmpty = "";
        String sigEmpty = computeExpected(secret2, bodyEmpty);
        boolean r7a = verifyWebhookSignature(secret2, null, sigEmpty);
        boolean r7b = verifyWebhookSignature(secret2, bodyEmpty, sigEmpty);

        // ===== 输出 =====
        System.out.println("=== F071 verifyWebhookSignature 单元测试 ===");
        System.out.println();
        System.out.println("[Case 1] 正常签名");
        System.out.println("  signSecret = " + secret1);
        System.out.println("  rawBody    = " + body1);
        System.out.println("  signature  = " + sig1);
        System.out.println("  expected   = " + r1 + "  (target=true)  " + (r1 == true ? "PASS" : "FAIL"));
        System.out.println();
        System.out.println("[Case 2] 篡改 body (TEST-001 → ATTACK-001)");
        System.out.println("  rawBody    = " + tamperedBody);
        System.out.println("  signature  = " + sig1);
        System.out.println("  expected   = " + r2 + "  (target=false) " + (r2 == false ? "PASS" : "FAIL"));
        System.out.println();
        System.out.println("[Case 3] 篡改 signature (最后一字符翻转)");
        System.out.println("  tampered  = " + tamperedSig);
        System.out.println("  expected   = " + r3 + "  (target=false) " + (r3 == false ? "PASS" : "FAIL"));
        System.out.println();
        System.out.println("[Case 4] 大写 signature (equalsIgnoreCase 防御)");
        System.out.println("  upperSig  = " + upperSig);
        System.out.println("  expected   = " + r4 + "  (target=true)  " + (r4 == true ? "PASS" : "FAIL"));
        System.out.println();
        System.out.println("[Case 5] signSecret=null (边界)");
        System.out.println("  expected   = " + r5 + "  (target=false) " + (r5 == false ? "PASS" : "FAIL"));
        System.out.println();
        System.out.println("[Case 6] signature=null (边界)");
        System.out.println("  expected   = " + r6 + "  (target=false) " + (r6 == false ? "PASS" : "FAIL"));
        System.out.println();
        System.out.println("[Case 7] rawBody=null vs rawBody=\"\" (等价性)");
        System.out.println("  sigEmpty   = " + sigEmpty);
        System.out.println("  rawBody=null   → " + r7a);
        System.out.println("  rawBody=\"\"     → " + r7b);
        System.out.println("  expected   = " + (r7a == r7b && r7a == true ? "PASS" : "FAIL") + "  (target=true & 等价)");
        System.out.println();
        System.out.println("=== 汇总 ===");
        int pass = (r1 == true ? 1 : 0) + (r2 == false ? 1 : 0) + (r3 == false ? 1 : 0) + (r4 == true ? 1 : 0)
                 + (r5 == false ? 1 : 0) + (r6 == false ? 1 : 0) + (r7a && r7b && r7a == true ? 1 : 0);
        System.out.println("PASS = " + pass + " / 7");
    }
}
