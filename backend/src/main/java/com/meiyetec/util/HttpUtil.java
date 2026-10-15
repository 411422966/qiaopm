package com.meiyetec.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * 轻量 HTTP 工具类（不依赖第三方 SDK）
 * 用于直调第三方 REST API：阿里云短信、微信订阅消息等
 */
@Slf4j
public class HttpUtil {

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final String CHARSET = "UTF-8";

    /**
     * GET 请求
     */
    public static String get(String url) throws IOException {
        return get(url, null);
    }

    public static String get(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json; charset=" + CHARSET);
            conn.setRequestProperty("Accept", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            conn.connect();
            return readResponse(conn);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * POST JSON 请求
     */
    public static String postJson(String url, String jsonBody) throws IOException {
        return postJson(url, jsonBody, null);
    }

    public static String postJson(String url, String jsonBody, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=" + CHARSET);
            conn.setRequestProperty("Accept", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(CHARSET));
                os.flush();
            }
            return readResponse(conn);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * POST 表单请求（application/x-www-form-urlencoded）
     */
    public static String postForm(String url, Map<String, String> params) throws IOException {
        return postForm(url, params, null);
    }

    /**
     * POST 表单请求，支持自定义请求头（Stripe 等需要 Authorization header 的场景）
     */
    public static String postForm(String url, Map<String, String> params,
                                  Map<String, String> extraHeaders) throws IOException {
        HttpURLConnection conn = null;
        try {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(URLEncoder.encode(e.getKey(), CHARSET))
                  .append('=')
                  .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), CHARSET));
            }
            byte[] body = sb.toString().getBytes(CHARSET);

            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=" + CHARSET);
            conn.setRequestProperty("Accept", "application/json");
            if (extraHeaders != null) {
                for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
                os.flush();
            }
            return readResponse(conn);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                        CHARSET))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    // ==============================================================
    // 阿里云短信签名算法（HMAC-SHA1，Base64）
    // ==============================================================
    public static String aliyunSign(String stringToSign, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(CHARSET), "HmacSHA1"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(CHARSET));
        return java.util.Base64.getEncoder().encodeToString(signData);
    }

    public static String percentEncode(String value) {
        try {
            return URLEncoder.encode(value, CHARSET)
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 阿里云短信请求构建
     * @param accessKeyId     阿里云 AccessKey ID
     * @param accessKeySecret 阿里云 AccessKey Secret
     * @param signName        短信签名
     * @param templateCode    模板CODE
     * @param phoneNumber     手机号（国内11位，无需+86前缀的加86）
     * @param templateParam   模板变量 JSON，如 {"code":"123456"}
     */
    public static String sendAliYunSms(String accessKeyId, String accessKeySecret,
                                        String signName, String templateCode,
                                        String phoneNumber, String templateParam) throws Exception {
        String domain = "dysmsapi.aliyuncs.com";
        String version = "2017-05-25";
        String action = "SendSms";
        String regionId = "cn-hangzhou";
        String format = "JSON";

        // 1. 构造参数
        Map<String, String> params = new TreeMap<>();
        params.put("AccessKeyId", accessKeyId);
        params.put("Action", action);
        params.put("Format", format);
        params.put("PhoneNumbers", phoneNumber);
        params.put("RegionId", regionId);
        params.put("SignName", signName);
        params.put("TemplateCode", templateCode);
        if (templateParam != null && !templateParam.isEmpty()) {
            params.put("TemplateParam", templateParam);
        }
        params.put("Timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z");
        params.put("Version", version);

        // 2. 构造待签名字符串
        StringBuilder sb = new StringBuilder("GET&").append(percentEncode("/"));
        StringBuilder paramStr = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (paramStr.length() > 0) paramStr.append('&');
            paramStr.append(percentEncode(e.getKey())).append('=').append(percentEncode(e.getValue()));
        }
        sb.append(percentEncode(paramStr.toString()));

        // 3. 计算签名
        String signature = percentEncode(aliyunSign(sb.toString(), accessKeySecret + "&"));

        // 4. 构造最终 URL
        StringBuilder urlBuilder = new StringBuilder("https://").append(domain).append("/?");
        for (Map.Entry<String, String> e : params.entrySet()) {
            urlBuilder.append(percentEncode(e.getKey())).append('=')
                    .append(percentEncode(e.getValue())).append('&');
        }
        urlBuilder.append("Signature=").append(signature);

        log.info("[SMS] calling Aliyun: phone={} template={}", phoneNumber, templateCode);
        String resp = get(urlBuilder.toString());
        log.info("[SMS] Aliyun response: {}", resp);
        return resp;
    }

    /**
     * 微信订阅消息 — 获取 AccessToken
     */
    public static String wxGetAccessToken(String appId, String appSecret) throws IOException {
        String url = String.format(
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
            appId, appSecret);
        return get(url);
    }

    /**
     * 微信订阅消息 — 发送订阅通知
     * @param accessToken  已获取的 AccessToken
     * @param touser        用户 OpenID
     * @param templateId    模板消息 ID
     * @param page          点击跳转小程序页面（可选）
     * @param data          模板数据，格式：{"key1":{"value":"xxx"},"key2":{"value":"yyy"}}
     */
    public static String wxSendSubscribeMessage(String accessToken, String touser,
                                                String templateId, String page,
                                                String data) throws IOException {
        String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/bizsend?access_token=" + accessToken;

        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode body = om.createObjectNode();
        body.put("touser", touser);
        body.put("template_id", templateId);
        if (page != null && !page.isEmpty()) body.put("page", page);
        body.put("data", om.readTree(data));

        log.info("[SMS] calling WeChat SubscribeMessage: touser={} template={}", touser, templateId);
        String resp = postJson(url, om.writeValueAsString(body));
        log.info("[SMS] WeChat response: {}", resp);
        return resp;
    }

    /**
     * 微信公众号 — 发送模板消息
     * <p>公众号（订阅号/服务号）模板消息发送，端点：
     * <pre>https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=ACCESS_TOKEN</pre>
     *
     * @param accessToken  已获取的 AccessToken
     * @param touser       接收者 OpenID
     * @param templateId   公众号模板消息 ID
     * @param url          模板消息点击跳转的 H5 链接（与 miniprogram 二选一，均不传则纯文本）
     * @param miniAppId    关联的小程序 appid（可选，用于跳转小程序）
     * @param miniPagePath 小程序页面路径（可选）
     * @param data         模板数据，格式：{"keyword1":{"value":"xxx","color":"#173177"}}
     * @param topColor     顶部颜色（可选，如 "#FF0000"）
     * @return 微信接口原始 JSON 响应
     */
    public static String wxSendTemplateMessage(String accessToken, String touser,
                                               String templateId, String url,
                                               String miniAppId, String miniPagePath,
                                               String data, String topColor) throws IOException {
        String endpoint = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=" + accessToken;

        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode body = om.createObjectNode();
        body.put("touser", touser);
        body.put("template_id", templateId);
        if (url != null && !url.isEmpty()) body.put("url", url);
        if (miniAppId != null && !miniAppId.isEmpty()) {
            com.fasterxml.jackson.databind.node.ObjectNode mini = om.createObjectNode();
            mini.put("appid", miniAppId);
            if (miniPagePath != null && !miniPagePath.isEmpty()) {
                mini.put("pagepath", miniPagePath);
            }
            body.set("miniprogram", mini);
        }
        body.put("data", om.readTree(data));
        if (topColor != null && !topColor.isEmpty()) body.put("topcolor", topColor);

        log.info("[MP-OA] calling WeChat TemplateMessage: touser={} template={}", touser, templateId);
        String resp = postJson(endpoint, om.writeValueAsString(body));
        log.info("[MP-OA] WeChat response: {}", resp);
        return resp;
    }
}
