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
 * M09-01：短信渠道 — 阿里云真实短信接入
 *
 * <p>配置存储在 mkt_channel_config.configJson，支持多租户：
 * <pre>
 * {
 *   "accessKeyId": "your-access-key-id",
 *   "accessKeySecret": "your-access-key-secret",
 *   "signName": "美业科技"
 * }
 * </pre>
 *
 * <p>模板变量使用 ${变量名} 格式，在发送时替换为实际值。
 */
@Slf4j
@Component
public class SmsSender implements ChannelSender {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MktChannelConfigMapper channelConfigMapper;

    /** AccessToken 缓存（按租户） */
    private final ConcurrentHashMap<Long, String> accessTokenCache = new ConcurrentHashMap<>();

    @Override
    public String channelType() {
        return "SMS";
    }

    @Override
    public SendResult send(MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
        try {
            String content = SmsSender.render(tpl.getContent(), vars);
            String templateCode = tpl.getExternalTemplateId();
            String templateParam = buildTemplateParam(tpl.getVariables(), vars);

            // 1. 从配置读取阿里云凭证
            MktChannelConfig cfg = resolveConfig();
            if (cfg == null) {
                log.warn("[SMS] No SMS config found, fallback to demo mode");
                return demoMode(receiver, content);
            }

            String configJson = cfg.getConfigJson();
            String accessKeyId = readJson(configJson, "accessKeyId");
            String accessKeySecret = readJson(configJson, "accessKeySecret");
            String signName = readJson(configJson, "signName");

            if (accessKeyId == null || accessKeySecret == null || signName == null) {
                log.warn("[SMS] Incomplete SMS config (accessKeyId/accessKeySecret/signName), fallback to demo");
                return demoMode(receiver, content);
            }

            // 2. 规范化手机号
            String phone = normalizePhone(receiver);

            // 3. 发送阿里云短信
            String resp = HttpUtil.sendAliYunSms(
                    accessKeyId, accessKeySecret, signName,
                    templateCode, phone, templateParam);

            // 4. 解析返回
            JsonNode node = JSON.readTree(resp);
            String code = node.path("Code").asText();
            String bizId = node.path("BizId").asText();
            String message = node.path("Message").asText();

            if ("OK".equals(code)) {
                log.info("[SMS] Aliyun send OK: bizId={} to={}", bizId, maskPhone(phone));
                return SendResult.ok(bizId);
            } else {
                log.warn("[SMS] Aliyun send fail: code={} msg={} to={}", code, message, maskPhone(phone));
                return SendResult.fail(code + ": " + message);
            }
        } catch (Exception e) {
            log.error("[SMS] send exception: {}", e.getMessage(), e);
            return SendResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 从当前租户解析短信配置
     */
    private MktChannelConfig resolveConfig() {
        // 租户上下文
        Long tenantId = null;
        try {
            tenantId = com.meiyetec.security.TenantContextHolder.get().getTenantId();
        } catch (Exception ignored) {}

        LambdaQueryWrapper<MktChannelConfig> q = new LambdaQueryWrapper<>();
        q.eq(MktChannelConfig::getChannelType, "SMS")
         .eq(MktChannelConfig::getStatus, 1)
         .eq(MktChannelConfig::getDeleted, 0);
        if (tenantId != null) {
            q.eq(MktChannelConfig::getTenantId, tenantId);
        }
        return channelConfigMapper.selectOne(q.last("LIMIT 1"));
    }

    /**
     * Demo 模式（无配置时）
     */
    private SendResult demoMode(String receiver, String content) {
        log.info("[SMS-DEMO] to={} content={}", maskPhone(receiver), content);
        return SendResult.ok("SMS-DEMO-" + System.currentTimeMillis());
    }

    /**
     * 模板渲染：${key} → vars[key]
     */
    public static String render(String template, Map<String, Object> vars) {
        if (template == null || vars == null) return template;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");
        java.util.regex.Matcher m = p.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = vars.get(m.group(1));
            m.appendReplacement(sb, v == null ? "" : java.util.regex.Matcher.quoteReplacement(v.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 构建阿里云模板参数 JSON
     */
    private String buildTemplateParam(String variables, Map<String, Object> vars) {
        if (variables == null || variables.isEmpty() || vars == null) return null;
        try {
            JsonNode arr = JSON.readTree(variables);
            if (!arr.isArray()) return null;
            java.util.Map<String, String> params = new java.util.LinkedHashMap<>();
            for (JsonNode n : arr) {
                String key = n.asText();
                Object val = vars.get(key);
                params.put(key, val == null ? "" : val.toString());
            }
            return JSON.writeValueAsString(params);
        } catch (Exception e) {
            log.warn("[SMS] buildTemplateParam error: {}", e.getMessage());
            return null;
        }
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

    private String normalizePhone(String phone) {
        if (phone == null) return null;
        phone = phone.trim();
        if (phone.startsWith("+86")) phone = phone.substring(3);
        return phone;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    @Override
    public boolean healthCheck() {
        try {
            MktChannelConfig cfg = resolveConfig();
            if (cfg == null) {
                log.info("[SMS] No SMS config configured, health=UNKNOWN");
                return false;
            }
            String json = cfg.getConfigJson();
            boolean hasKey = readJson(json, "accessKeyId") != null;
            log.info("[SMS] health={} signName={}", hasKey, readJson(json, "signName"));
            return hasKey;
        } catch (Exception e) {
            log.warn("[SMS] healthCheck exception: {}", e.getMessage());
            return false;
        }
    }
}
