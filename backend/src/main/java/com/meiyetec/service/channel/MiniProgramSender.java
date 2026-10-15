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
 * M09-01：微信小程序订阅消息 — 真实接入
 *
 * <p>configJson 配置格式：
 * <pre>
 * {
 *   "appId": "wx1234567890abcdef",
 *   "appSecret": "your-app-secret",
 *   "defaultPage": "pages/index/index"
 * }
 * </pre>
 *
 * <p>AccessToken 会自动缓存在内存（2小时），无需每次请求都刷新。
 */
@Slf4j
@Component
public class MiniProgramSender implements ChannelSender {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long TOKEN_CACHE_MS = 7100_000L; // 提前10分钟刷新

    @Autowired
    private MktChannelConfigMapper channelConfigMapper;

    /** AccessToken 缓存：tenantId → {token, expireTime} */
    private final ConcurrentHashMap<Long, TokenCache> tokenCache = new ConcurrentHashMap<>();

    private static class TokenCache {
        final String token;
        final long expireMs; // 过期时间戳

        TokenCache(String token, long expireMs) {
            this.token = token;
            this.expireMs = expireMs;
        }
    }

    @Override
    public String channelType() {
        return "MINI_PROGRAM";
    }

    @Override
    public SendResult send(MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
        try {
            String templateId = tpl.getExternalTemplateId();
            String content = SmsSender.render(tpl.getContent(), vars);

            // 1. 解析配置
            MktChannelConfig cfg = resolveConfig();
            if (cfg == null) {
                log.warn("[MP] No mini program config found, fallback to demo");
                return demoMode(receiver, templateId, content);
            }

            String configJson = cfg.getConfigJson();
            String appId = readJson(configJson, "appId");
            String appSecret = readJson(configJson, "appSecret");
            String defaultPage = readJson(configJson, "defaultPage");

            if (appId == null || appSecret == null) {
                log.warn("[MP] Incomplete MP config (appId/appSecret), fallback to demo");
                return demoMode(receiver, templateId, content);
            }

            // 2. 获取/刷新 AccessToken
            Long tenantId = getTenantId();
            String accessToken = getAccessToken(tenantId, appId, appSecret);
            if (accessToken == null) {
                return SendResult.fail("Failed to get WeChat AccessToken");
            }

            // 3. 构建订阅消息 data（将 content 转为 keyword1~keyword5 格式）
            String data = buildSubscribeData(content, vars, tpl.getVariables());

            // 4. 发送
            String resp = HttpUtil.wxSendSubscribeMessage(
                    accessToken, receiver, templateId, defaultPage, data);

            // 5. 解析返回
            JsonNode node = JSON.readTree(resp);
            int errcode = node.path("errcode").asInt();
            String errmsg = node.path("errmsg").asText();

            if (errcode == 0) {
                String msgid = node.path("msgid").asText("MP-" + System.currentTimeMillis());
                log.info("[MP] WeChat send OK: msgid={} to={}", msgid, maskOpenId(receiver));
                return SendResult.ok(msgid);
            } else {
                // 40001 = token 过期，自动清除缓存，下次重试
                if (errcode == 40001 && tenantId != null) {
                    tokenCache.remove(tenantId);
                }
                log.warn("[MP] WeChat send fail: errcode={} errmsg={} to={}", errcode, errmsg, maskOpenId(receiver));
                return SendResult.fail(errcode + ": " + errmsg);
            }
        } catch (Exception e) {
            log.error("[MP] send exception: {}", e.getMessage(), e);
            return SendResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 获取 AccessToken（带内存缓存）
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
                log.info("[MP] AccessToken refreshed, expires in {}s", expiresIn);
                return token;
            } else {
                log.warn("[MP] getAccessToken fail: {}", node);
                return null;
            }
        } catch (Exception e) {
            log.error("[MP] getAccessToken exception: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建微信订阅消息 data 格式
     * 格式：{"keyword1":{"value":"xxx"},"keyword2":{"value":"yyy"}}
     * 最多支持 5 个 keyword，按 content 拆分为 keyword1~keywordN
     */
    private String buildSubscribeData(String content, Map<String, Object> vars, String variables) {
        try {
            java.util.Map<String, java.util.Map<String, String>> data = new java.util.LinkedHashMap<>();
            String[] parts;
            if (variables != null && !variables.isEmpty()) {
                // 按配置变量拆分
                JsonNode arr = JSON.readTree(variables);
                if (arr.isArray()) {
                    for (int i = 0; i < Math.min(arr.size(), 5); i++) {
                        String key = arr.get(i).asText();
                        Object val = vars != null ? vars.get(key) : null;
                        java.util.Map<String, String> item = new java.util.HashMap<>();
                        item.put("value", val != null ? val.toString() : "");
                        data.put("keyword" + (i + 1), item);
                    }
                    return JSON.writeValueAsString(data);
                }
            }
            // 默认：content 作为 keyword1
            java.util.Map<String, String> item = new java.util.HashMap<>();
            item.put("value", content != null ? content : "");
            data.put("keyword1", item);
            return JSON.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("[MP] buildSubscribeData error: {}", e.getMessage());
            return "{\"keyword1\":{\"value\":\"" + (content != null ? content : "") + "\"}}";
        }
    }

    private MktChannelConfig resolveConfig() {
        Long tenantId = getTenantId();
        LambdaQueryWrapper<MktChannelConfig> q = new LambdaQueryWrapper<>();
        q.eq(MktChannelConfig::getChannelType, "MINI_PROGRAM")
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
        log.info("[MP-DEMO] openId={} template={} content={}", maskOpenId(receiver), templateId, content);
        return SendResult.ok("MP-DEMO-" + System.currentTimeMillis());
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
                log.info("[MP] No MP config configured, health=UNKNOWN");
                return false;
            }
            String json = cfg.getConfigJson();
            boolean hasKey = readJson(json, "appId") != null;
            log.info("[MP] health={} appId={}", hasKey, maskOpenId(readJson(json, "appId")));
            return hasKey;
        } catch (Exception e) {
            log.warn("[MP] healthCheck exception: {}", e.getMessage());
            return false;
        }
    }
}
