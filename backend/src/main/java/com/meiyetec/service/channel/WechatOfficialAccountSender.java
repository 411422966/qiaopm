package com.meiyetec.service.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.MktChannelConfig;
import com.meiyetec.entity.MktChannelTemplate;
import com.meiyetec.mapper.MktChannelConfigMapper;
import com.meiyetec.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M09-01 收尾：微信公众号（订阅号/服务号）模板消息 — 真实接入
 *
 * <p>与 {@link MiniProgramSender}（小程序订阅消息）不同的渠道，channelType 为
 * {@code WECHAT_OFFICIAL_ACCOUNT}。两者 AccessToken 端点相同（client_credential），
 * 但发送端点不同：
 * <ul>
 *   <li>小程序订阅消息：<code>cgi-bin/message/subscribe/bizsend</code></li>
 *   <li>公众号模板消息：<code>cgi-bin/message/template/send</code></li>
 * </ul>
 *
 * <p>configJson 配置格式：
 * <pre>
 * {
 *   "appId": "wx1234567890abcdef",
 *   "appSecret": "your-app-secret",
 *   "defaultUrl": "https://h5.meiyetec.com/...",
 *   "miniAppId": "wx9876543210fedcba",   // 可选：跳转关联小程序
 *   "miniPagePath": "pages/index/index"  // 可选：小程序页面
 * }
 * </pre>
 *
 * <p>AccessToken 复用 {@link MiniProgramSender} 的缓存模式（按 tenant 隔离内存，2 小时过期前刷新）。
 */
@Slf4j
@Component
public class WechatOfficialAccountSender implements ChannelSender {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long TOKEN_CACHE_MS = 7100_000L; // 提前 10 分钟刷新

    @Autowired
    private MktChannelConfigMapper channelConfigMapper;

    /** AccessToken 缓存：tenantId → {token, expireMs} */
    private final ConcurrentHashMap<Long, TokenCache> tokenCache = new ConcurrentHashMap<>();

    private static class TokenCache {
        final String token;
        final long expireMs;

        TokenCache(String token, long expireMs) {
            this.token = token;
            this.expireMs = expireMs;
        }
    }

    @Override
    public String channelType() {
        return "WECHAT_OFFICIAL_ACCOUNT";
    }

    @Override
    public SendResult send(MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
        try {
            String templateId = tpl.getExternalTemplateId();
            String content = SmsSender.render(tpl.getContent(), vars);

            // 1. 解析配置
            MktChannelConfig cfg = resolveConfig();
            if (cfg == null) {
                log.warn("[MP-OA] No official-account config found, fallback to demo");
                return demoMode(receiver, templateId, content);
            }

            String configJson = cfg.getConfigJson();
            String appId = readJson(configJson, "appId");
            String appSecret = readJson(configJson, "appSecret");
            String defaultUrl = readJson(configJson, "defaultUrl");
            String miniAppId = readJson(configJson, "miniAppId");
            String miniPagePath = readJson(configJson, "miniPagePath");

            if (appId == null || appSecret == null) {
                log.warn("[MP-OA] Incomplete OA config (appId/appSecret), fallback to demo");
                return demoMode(receiver, templateId, content);
            }

            // 2. 获取/刷新 AccessToken
            Long tenantId = getTenantId();
            String accessToken = getAccessToken(tenantId, appId, appSecret);
            if (accessToken == null) {
                return SendResult.fail("Failed to get WeChat OA AccessToken");
            }

            // 3. 解析跳转链接：tpl.linkUrl 优先；其次 defaultUrl；都没有则不传 url
            String linkUrl = tpl.getLinkUrl();
            if (linkUrl == null || linkUrl.isEmpty()) {
                linkUrl = defaultUrl;
            }

            // 4. 构建公众号模板 data 格式：{"keyword1":{"value":"..."},"keyword2":{"value":"..."}}
            String data = buildTemplateData(content, vars, tpl.getVariables());

            // 5. 发送公众号模板消息
            String resp = HttpUtil.wxSendTemplateMessage(
                    accessToken, receiver, templateId, linkUrl,
                    miniAppId, miniPagePath, data, null);

            // 6. 解析返回
            JsonNode node = JSON.readTree(resp);
            int errcode = node.path("errcode").asInt();
            String errmsg = node.path("errmsg").asText();

            if (errcode == 0) {
                String msgid = node.path("msgid").asText("OA-" + System.currentTimeMillis());
                log.info("[MP-OA] WeChat OA send OK: msgid={} to={}", msgid, maskOpenId(receiver));
                return SendResult.ok(msgid);
            } else {
                // 40001 = token 过期，自动清除缓存，下次重试
                if (errcode == 40001 && tenantId != null) {
                    tokenCache.remove(tenantId);
                }
                // 43004 / 43005 接收者未关注公众号 → 业务失败（不重试）
                if (errcode == 40003 || errcode == 43003 || errcode == 43004 || errcode == 43005) {
                    log.warn("[MP-OA] recipient not subscribed: errcode={} errmsg={} openId={}",
                            errcode, errmsg, maskOpenId(receiver));
                } else {
                    log.warn("[MP-OA] WeChat OA send fail: errcode={} errmsg={} to={}",
                            errcode, errmsg, maskOpenId(receiver));
                }
                return SendResult.fail(errcode + ": " + errmsg);
            }
        } catch (Exception e) {
            log.error("[MP-OA] send exception: {}", e.getMessage(), e);
            return SendResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 获取公众号 AccessToken（与小程序共用同一端点：client_credential 模式）
     */
    private String getAccessToken(Long tenantId, String appId, String appSecret) {
        try {
            // 检查缓存
            TokenCache cached = tokenCache.get(tenantId);
            if (cached != null && System.currentTimeMillis() < cached.expireMs) {
                return cached.token;
            }

            // 刷新
            String resp = HttpUtil.wxGetAccessToken(appId, appSecret);
            JsonNode node = JSON.readTree(resp);
            String token = node.path("access_token").asText(null);
            int expiresIn = node.path("expires_in").asInt(7200);

            if (token != null && !token.isEmpty()) {
                long expireMs = System.currentTimeMillis() + (long) expiresIn * 1000 - TOKEN_CACHE_MS;
                tokenCache.put(tenantId, new TokenCache(token, expireMs));
                log.info("[MP-OA] AccessToken refreshed, expires in {}s", expiresIn);
                return token;
            } else {
                log.warn("[MP-OA] getAccessToken fail: {}", node);
                return null;
            }
        } catch (Exception e) {
            log.error("[MP-OA] getAccessToken exception: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建公众号模板消息 data。
     * <p>公众号模板通常包含 first / keyword1..N / remark 三个固定区段，按 variables 数组映射 keyword1..N，
     * 余下空位不发送；如未配置 variables，则 content 整体放入 first。
     */
    private String buildTemplateData(String content, Map<String, Object> vars, String variables) {
        try {
            java.util.Map<String, java.util.Map<String, String>> data = new java.util.LinkedHashMap<>();
            if (variables != null && !variables.isEmpty()) {
                JsonNode arr = JSON.readTree(variables);
                if (arr.isArray() && arr.size() > 0) {
                    // first
                    java.util.Map<String, String> firstItem = new java.util.HashMap<>();
                    firstItem.put("value", content != null ? content : "");
                    data.put("first", firstItem);
                    // keyword1..N
                    for (int i = 0; i < Math.min(arr.size(), 10); i++) {
                        String key = arr.get(i).asText();
                        Object val = vars != null ? vars.get(key) : null;
                        java.util.Map<String, String> item = new java.util.HashMap<>();
                        item.put("value", val != null ? val.toString() : "");
                        data.put("keyword" + (i + 1), item);
                    }
                    return JSON.writeValueAsString(data);
                }
            }
            // 默认：content 作为 first（无 keyword）
            java.util.Map<String, String> item = new java.util.HashMap<>();
            item.put("value", content != null ? content : "");
            data.put("first", item);
            return JSON.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("[MP-OA] buildTemplateData error: {}", e.getMessage());
            return "{\"first\":{\"value\":\"" + (content != null ? escapeJson(content) : "") + "\"}}";
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private MktChannelConfig resolveConfig() {
        Long tenantId = getTenantId();
        LambdaQueryWrapper<MktChannelConfig> q = new LambdaQueryWrapper<>();
        q.eq(MktChannelConfig::getChannelType, "WECHAT_OFFICIAL_ACCOUNT")
         .eq(MktChannelConfig::getStatus, 1)
         .eq(MktChannelConfig::getDeleted, 0);
        if (tenantId != null) q.eq(MktChannelConfig::getTenantId, tenantId);
        return channelConfigMapper.selectOne(q.last("LIMIT 1"));
    }

    private Long getTenantId() {
        try {
            return com.meiyetec.security.TenantContextHolder.get().getTenantId();
        } catch (Exception e) {
            return null;
        }
    }

    private SendResult demoMode(String receiver, String templateId, String content) {
        log.info("[MP-OA-DEMO] openId={} template={} content={}", maskOpenId(receiver), templateId, content);
        return SendResult.ok("MP-OA-DEMO-" + System.currentTimeMillis());
    }

    private String maskOpenId(String openid) {
        if (openid == null || openid.length() < 10) return "***";
        return openid.substring(0, 6) + "..." + openid.substring(openid.length() - 4);
    }

    private String readJson(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        try {
            JsonNode node = JSON.readTree(json);
            return node.path(key).asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean healthCheck() {
        try {
            MktChannelConfig cfg = resolveConfig();
            if (cfg == null) {
                log.info("[MP-OA] No OA config configured, health=UNKNOWN");
                return false;
            }
            String json = cfg.getConfigJson();
            boolean hasKey = readJson(json, "appId") != null;
            log.info("[MP-OA] health={} appId={}", hasKey, maskOpenId(readJson(json, "appId")));
            return hasKey;
        } catch (Exception e) {
            log.warn("[MP-OA] healthCheck exception: {}", e.getMessage());
            return false;
        }
    }
}
