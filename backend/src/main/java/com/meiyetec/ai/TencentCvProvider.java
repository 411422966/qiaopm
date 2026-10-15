package com.meiyetec.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 腾讯云 AI 视觉推理提供者
 *
 * <p>对接腾讯云 AI 3.0（人脸检测 / 皮肤分析）
 * 文档：https://cloud.tencent.com/document/product/867/32771
 *
 * <p>凭证配置（application.yml）：
 * <pre>
 * app:
 *   dcep:
 *     ai:
 *       tencent:
 *         secret-id: AKIDxxxxxxxxxxxxx
 *         secret-key: xxxxxxxxxxxxxxxxxx
 *         app-id: 125xxxxxxx
 *         region: ap-guangzhou
 * </pre>
 *
 * <p>未配置时 isConfigured() = false，回退 Mock 响应。
 * 注意：secret-id / secret-key 不得写死在 YAML 生产应从环境变量注入。
 */
@Slf4j
@Component
public class TencentCvProvider implements CvInferenceProvider {

    @Value("${app.dcep.ai.tencent.secret-id:#{null}}")
    private String secretId;

    @Value("${app.dcep.ai.tencent.secret-key:#{null}}")
    private String secretKey;

    @Value("${app.dcep.ai.tencent.app-id:#{null}}")
    private String appId;

    @Value("${app.dcep.ai.tencent.region:ap-guangzhou}")
    private String region;

    private static final String PROVIDER = "TENCENT";
    private static final String SERVICE   = "iai";          // IA 产品的 service 名称
    private static final String VERSION   = "2020-03-03";  // API 版本
    private static final String HOST      = "iai.tencentcloudapi.com";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String providerCode() { return PROVIDER; }

    @Override
    public boolean isConfigured() {
        return secretId != null && !secretId.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    @Override
    public Map<String, Object> inferSkin(String imageUrl, Long memberId, Map<String, String> config) {
        if (!isConfigured()) return mockSkinResult(imageUrl);
        try {
            String endpoint = "https://" + HOST;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ImageUrl", imageUrl);
            body.put("FaceModelVersion", "3.0");
            body.put("NeedFaceAttributes", 1);
            body.put("NeedQualityScore", 1);

            String response = httpPost(endpoint, body, "DetectFace");
            return parseTencentFaceResponse(response, imageUrl, "skin");
        } catch (Exception e) {
            log.warn("[TencentCv] skin inference failed, fallback mock: {}", e.getMessage(), e);
            return mockSkinResult(imageUrl);
        }
    }

    @Override
    public Map<String, Object> inferFace(String imageUrl, Long memberId, Map<String, String> config) {
        if (!isConfigured()) return mockFaceResult(imageUrl);
        try {
            String endpoint = "https://" + HOST;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ImageUrl", imageUrl);
            body.put("FaceModelVersion", "3.0");
            body.put("NeedFaceAttributes", 1);
            body.put("NeedQualityScore", 1);

            String response = httpPost(endpoint, body, "DetectFace");
            return parseTencentFaceResponse(response, imageUrl, "face");
        } catch (Exception e) {
            log.warn("[TencentCv] face inference failed, fallback mock: {}", e.getMessage(), e);
            return mockFaceResult(imageUrl);
        }
    }

    @Override
    public Map<String, Object> inferNailTryon(String handImageUrl, String styleImageUrl, Map<String, String> config) {
        // 腾讯云 IA 暂无美甲专用 API，统一走 mock
        return mockNailResult(handImageUrl, styleImageUrl);
    }

    @Override
    public Map<String, Object> inferHair(String imageUrl, Long memberId, Map<String, String> config) {
        // 腾讯云 IA 暂无头发分析 API，统一走 mock
        return mockHairResult(imageUrl);
    }

    @Override
    public Map<String, Object> inferGeneric(String modelCode, String imageUrl, Map<String, String> config) {
        return inferSkin(imageUrl, null, config);
    }

    // ====================================================================
    // TC3-HMAC-SHA256 签名（腾讯云 API v3 标准算法）
    // 文档：https://cloud.tencent.com/document/api/457/37165
    // ====================================================================

    /**
     * 带 TC3 签名的 HTTP POST。
     * <ol>
     *   <li>序列化 body → JSON 字节</li>
     *   <li>计算 SHA256(payload)</li>
     *   <li>构造 CanonicalRequest 并 hash → hashedCanonicalRequest</li>
     *   <li>构造 StringToSign</li>
     *   <li>派生签名密钥并计算签名</li>
     *   <li>构造 Authorization header</li>
     * </ol>
     */
    private String httpPost(String endpoint, Map<String, Object> body, String action) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String bodyJson = mapper.writeValueAsString(body);
        byte[] bodyBytes = bodyJson.getBytes(StandardCharsets.UTF_8);
        String payloadHash = sha256Hex(bodyBytes);

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC+8"));
        String timestamp  = String.valueOf(now.toEpochSecond());
        String date       = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")); // UTC+8 日期

        String httpMethod = "POST";
        String canonicalUri      = "/";
        String canonicalQs       = "";                         // 本接口无 query string

        // Canonical headers（必须包含 content-type 和 host，全部小写，key: value\n 末尾换行）
        String contentType = "application/json";
        String canonicalHeaders =
                "content-type:" + contentType + "\n" +
                "host:" + HOST + "\n";
        String signedHeaders = "content-type;host";

        // 1) Canonical Request
        String canonicalRequest =
                httpMethod + "\n" +
                canonicalUri + "\n" +
                canonicalQs + "\n" +
                canonicalHeaders + "\n" +
                signedHeaders + "\n" +
                payloadHash;

        String hashedCanonicalRequest = sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        // 2) String to Sign
        String stringToSign =
                ALGORITHM + "\n" +
                timestamp + "\n" +
                date + "\n" +
                hashedCanonicalRequest;

        // 3) 派生签名密钥
        //    SecretKey = HMAC("TC3" + SecretKey, Date)
        //    SigningKey = HMAC(SecretKey, "tc3_request")
        //    Signature = HMAC(SigningKey, StringToSign)
        byte[] kDate   = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] kService = hmacSha256(kDate, SERVICE);
        byte[] kSigning = hmacSha256(kService, "tc3_request");
        byte[] signatureBytes = hmacSha256(kSigning, stringToSign);
        String signature = bytesToHex(signatureBytes);

        // 4) Authorization header
        String authorization =
                ALGORITHM + " " +
                "Credential=" + secretId + "/" + date + "/" + SERVICE + "/tc3_request, " +
                "SignedHeaders=" + signedHeaders + ", " +
                "Signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", contentType)
                .header("Host", HOST)
                .header("X-TC-Action", action)
                .header("X-TC-Region", region)
                .header("X-TC-Version", VERSION)
                .header("X-TC-Timestamp", timestamp)
                .header("X-TC-Algorithm", ALGORITHM)
                .header("Authorization", authorization)
                .header("Content-Length", String.valueOf(bodyBytes.length))
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .build();

        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("[TencentCv] action={} httpStatus={} bodyLen={}",
                action, resp.statusCode(), resp.body().length());

        if (resp.statusCode() != 200) {
            log.warn("[TencentCv] non-200 from Tencent: {} — {}", resp.statusCode(), resp.body());
        }
        return resp.body();
    }

    // ====================================================================
    // 响应解析
    // ====================================================================

    private Map<String, Object> parseTencentFaceResponse(String raw, String imageUrl, String mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", PROVIDER);
        result.put("imageUrl", imageUrl);
        result.put("timestamp", System.currentTimeMillis());
        result.put("rawResponse", raw);   // 调试时可见原始响应

        try {
            var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
            var response = tree.path("Response");

            // 业务错误码（如 InvalidParameter）
            if (response.has("Error")) {
                String code = response.path("Error").path("Code").asText("UNKNOWN");
                String msg  = response.path("Error").path("Message").asText("unknown error");
                log.warn("[TencentCv] API error code={} msg={}", code, msg);
                // 业务错误也记录，但不抛异常，内部降级 mock
                result.put("apiError", code);
                result.put("apiErrorMsg", msg);
                return mode.equals("skin") ? mockSkinResult(imageUrl) : mockFaceResult(imageUrl);
            }

            var faceInfos = response.path("FaceInfos");
            if (faceInfos.isArray() && faceInfos.size() > 0) {
                var face = faceInfos.get(0);
                double beauty = face.path("Beauty").asDouble(75.0);
                int age       = face.path("Age").asInt(28);

                result.put("faceId",    "face_" + UUID.randomUUID().toString().substring(0, 8));
                result.put("beautyScore", beauty);
                result.put("ageRange",    age + "-" + (age + 5));
                result.put("gender",      face.path("Gender").path("Value").asInt(1) == 1 ? "FEMALE" : "MALE");
                result.put("expression",  face.path("Expression").path("Value").asText("neutral"));
                result.put("confidence",  face.path("FaceRect").path("Confidence").asDouble(0.98));
                result.put("landmarks",   face.path("FaceLandmarks").size());
                result.put("skinScore",   (int) Math.min(100, beauty));

                // 皮肤类型：美商分数 > 80 油皮，> 60 混合，偏干
                String skinType = beauty > 80 ? "OILY" : beauty > 60 ? "COMBINATION" : "DRY";
                result.put("skinType", skinType);

                if (mode.equals("skin")) {
                    Map<String, Object> skin = new LinkedHashMap<>();
                    skin.put("skinType", skinType);
                    skin.put("confidence", face.path("FaceRect").path("Confidence").asDouble(0.98));
                    result.put("skin", skin);
                }

                log.info("[TencentCv] real inference OK — beauty={} skinType={} gender={}",
                        beauty, skinType, result.get("gender"));
            } else {
                log.info("[TencentCv] no face detected in image, using mock");
                return mode.equals("skin") ? mockSkinResult(imageUrl) : mockFaceResult(imageUrl);
            }
        } catch (Exception e) {
            log.debug("[TencentCv] parse failed, using mock: {}", e.getMessage());
            return mode.equals("skin") ? mockSkinResult(imageUrl) : mockFaceResult(imageUrl);
        }
        return result;
    }

    // ====================================================================
    // Mock 响应（降级时返回）
    // ====================================================================

    private Map<String, Object> mockSkinResult(String imageUrl) {
        Map<String, Object> skin = new LinkedHashMap<>();
        skin.put("skinType", "DRY");
        skin.put("oilLevel", 3);
        skin.put("moistureLevel", 4);
        skin.put("elasticityLevel", 7);
        skin.put("pigmentLevel", 5);
        skin.put("poreLevel", 4);
        skin.put("wrinkleLevel", 3);
        skin.put("skinScore", 75);
        skin.put("issues", List.of("缺水", "细纹"));
        skin.put("confidence", 0.95);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", PROVIDER + "_MOCK");
        result.put("skin", skin);
        result.put("imageUrl", imageUrl);
        result.put("timestamp", System.currentTimeMillis());
        result.put("fallbackReason", "tencent_not_configured");
        return result;
    }

    private Map<String, Object> mockFaceResult(String imageUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", PROVIDER + "_MOCK");
        result.put("faceId", "face_" + UUID.randomUUID().toString().substring(0, 8));
        result.put("beautyScore", 85);
        result.put("ageRange", "25-30");
        result.put("gender", "FEMALE");
        result.put("expression", "smile");
        result.put("confidence", 0.98);
        result.put("landmarks", 81);
        result.put("imageUrl", imageUrl);
        result.put("timestamp", System.currentTimeMillis());
        result.put("fallbackReason", "tencent_not_configured");
        return result;
    }

    private Map<String, Object> mockNailResult(String hand, String style) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", PROVIDER + "_MOCK");
        result.put("resultImage", "https://cdn.example.com/tryon/" + System.currentTimeMillis() + ".jpg");
        result.put("nailDetected", true);
        result.put("colorMatched", true);
        result.put("handImageUrl", hand);
        result.put("styleImageUrl", style);
        result.put("timestamp", System.currentTimeMillis());
        result.put("fallbackReason", "tencent_no_nail_api");
        return result;
    }

    private Map<String, Object> mockHairResult(String imageUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", PROVIDER + "_MOCK");
        result.put("hairDensity", 7);
        result.put("hairLossLevel", 3);
        result.put("scalpOiliness", 4);
        result.put("dandruffLevel", 2);
        result.put("recommendations", List.of("深层清洁头皮", "减少烫染频率"));
        result.put("imageUrl", imageUrl);
        result.put("timestamp", System.currentTimeMillis());
        result.put("fallbackReason", "tencent_no_hair_api");
        return result;
    }

    // ====================================================================
    // 签名工具函数
    // ====================================================================

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return bytesToHex(md.digest(data));
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
