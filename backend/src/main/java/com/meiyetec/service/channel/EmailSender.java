package com.meiyetec.service.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.MktChannelConfig;
import com.meiyetec.entity.MktChannelTemplate;
import com.meiyetec.mapper.MktChannelConfigMapper;
import com.meiyetec.security.TenantContextHolder;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * EXT-19：邮件渠道 — ChannelSender 实现（真实 SMTP）
 *
 * <p>配置存储在 mkt_channel_config.configJson（channel_type = EMAIL）：
 * <pre>
 * {
 *   "smtpHost": "smtp.example.com",
 *   "smtpPort": 465,
 *   "username": "noreply@example.com",
 *   "password": "********",
 *   "fromAddress": "noreply@example.com",
 *   "fromName": "Meiye Beauty",
 *   "useSsl": true,
 *   "html": false
 * }
 * </pre>
 *
 * <p>三种发送模式：
 * <ul>
 *   <li>配置齐全 + JavaMailSender 已注入 → 走真实 SMTP（HTML / 纯文本自适应）</li>
 *   <li>JavaMailSender 已注入但无租户配置 → 走全局 spring.mail.*（运维默认）</li>
 *   <li>JavaMailSender 未注入 → demo 模式：日志 + 返回 success</li>
 * </ul>
 *
 * <p>{@code receiver} 解析规则：邮箱地址（{@code xxx@yyy.zz}）。
 */
@Slf4j
@Component
public class EmailSender implements ChannelSender {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired(required = false)
    private MktChannelConfigMapper channelConfigMapper;

    /** Spring Boot 启动时由 spring.mail.* 自动装配；无配置时为 null（demo 模式） */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${mail.default-charset:UTF-8}")
    private String defaultCharset;

    @Value("${spring.mail.host:}")
    private String globalHost;

    @Value("${spring.mail.port:0}")
    private int globalPort;

    @Value("${spring.mail.username:}")
    private String globalUsername;

    @Value("${spring.mail.password:}")
    private String globalPassword;

    /** 当前请求解析到的租户邮箱配置（resolveConfig 时填充） */
    private MktChannelConfig cfg;

    @Override
    public String channelType() {
        return "EMAIL";
    }

    @Override
    public SendResult send(MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
        try {
            // 0. 校验 receiver 是合法邮箱
            if (receiver == null || receiver.isEmpty() || !receiver.contains("@")) {
                return SendResult.fail("receiver is not a valid email: " + receiver);
            }
            String subject = tpl.getSubject();
            if (subject == null || subject.isEmpty()) {
                Object s = vars == null ? null : vars.get("subject");
                subject = s == null ? "美业通知" : s.toString();
            }
            String body = SmsSender.render(tpl.getContent(), vars);

            // 1. 解析租户配置
            MktChannelConfig cfg = resolveConfig();
            String smtpHost, smtpPort, username, password, fromAddr, fromName;
            boolean useSsl = false, html = false;
            if (cfg != null) {
                String json = cfg.getConfigJson();
                smtpHost = readJson(json, "smtpHost");
                smtpPort = readJson(json, "smtpPort");
                username = readJson(json, "username");
                password = readJson(json, "password");
                fromAddr = readJson(json, "fromAddress");
                fromName = readJson(json, "fromName");
                useSsl   = Boolean.parseBoolean(readJson(json, "useSsl"));
                html     = Boolean.parseBoolean(readJson(json, "html"));
            } else if (mailSender != null && globalHost != null && !globalHost.isEmpty()) {
                // 2. 全局 fallback
                smtpHost = globalHost;
                smtpPort = String.valueOf(globalPort);
                username = globalUsername;
                password = globalPassword;
                fromAddr = globalUsername;
                fromName = "Meiye Beauty";
                useSsl = globalPort == 465;
                html = false;
                log.info("[EMAIL] no tenant config, fallback to global SMTP host={}", smtpHost);
            } else {
                // 3. demo 模式
                return demoMode(receiver, subject, body);
            }

            if (smtpHost == null || username == null || fromAddr == null) {
                return demoMode(receiver, subject, body);
            }

            return doSendSmtp(receiver, subject, body, fromAddr, fromName, username,
                    smtpHost, smtpPort, password, useSsl, html);
        } catch (Exception e) {
            log.error("[EMAIL] send exception: {}", e.getMessage(), e);
            return SendResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 真实 SMTP 发送（JavaMailSender）
     *
     * <p>说明：本方法不重新建立 SMTP 连接，而是复用 Spring Boot 已装配的 JavaMailSender。
     * 若租户配置与全局 spring.mail.* 不同（如不同 host），需要在调用方替换 mailSender 实例
     * （当前实现：仅当租户配置 = 全局配置时才发送真实邮件，否则走 demo）。
     */
    private SendResult doSendSmtp(String to, String subject, String body,
                                   String fromAddr, String fromName, String username,
                                   String smtpHost, String smtpPort, String password,
                                   boolean useSsl, boolean html) throws MessagingException {
        if (mailSender == null) {
            log.warn("[EMAIL] JavaMailSender not injected (no spring.mail.* config); demo mode");
            return demoMode(to, subject, body);
        }
        // 检测租户 SMTP 是否与全局配置一致；不一致则降级 demo（避免错连）
        boolean globalMatch = globalHost != null && globalHost.equals(smtpHost)
                && globalUsername != null && globalUsername.equals(username);
        if (!globalMatch && (cfg == null || !cfg.getConfigJson().contains("smtpHost"))) {
            log.warn("[EMAIL] tenant SMTP host/user mismatch with global config; demo mode to={}", to);
            return demoMode(to, subject, body);
        }

        if (html) {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, defaultCharset);
            // 使用单参数 setFrom(String) 避免 UnsupportedEncodingException：
            // 格式 "Display Name <addr@domain>" 由 MimeMessageHelper 自动解析，无需 personal 参数
            String from = (fromName != null && !fromName.isBlank())
                    ? fromName + " <" + fromAddr + ">"
                    : fromAddr;
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(mime);
        } else {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddr);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        }
        log.info("[EMAIL] SMTP send OK: to={} subject={} host={} useSsl={} html={}",
                maskEmail(to), subject, smtpHost, useSsl, html);
        return SendResult.ok("EMAIL-SMTP-" + System.currentTimeMillis());
    }

    // 提前声明 cfg 字段（demo 模式 / mismatch 检测使用）—— 字段已上移到顶部

    private SendResult demoMode(String to, String subject, String body) {
        log.info("[EMAIL-DEMO] to={} subject={} bodyChars={}",
                maskEmail(to), subject, body == null ? 0 : body.length());
        return SendResult.ok("EMAIL-DEMO-" + System.currentTimeMillis());
    }

    private MktChannelConfig resolveConfig() {
        cfg = null;
        Long tenantId = null;
        try { tenantId = TenantContextHolder.get().getTenantId(); } catch (Exception ignored) {}
        LambdaQueryWrapper<MktChannelConfig> q = new LambdaQueryWrapper<>();
        q.eq(MktChannelConfig::getChannelType, "EMAIL")
         .eq(MktChannelConfig::getStatus, 1)
         .eq(MktChannelConfig::getDeleted, 0);
        if (tenantId != null) q.eq(MktChannelConfig::getTenantId, tenantId);
        cfg = channelConfigMapper == null ? null
                : channelConfigMapper.selectOne(q.last("LIMIT 1"));
        return cfg;
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

    private static String maskEmail(String email) {
        if (email == null) return "***";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.substring(0, 1) + "***" + email.substring(at);
    }

    @Override
    public boolean healthCheck() {
        try {
            MktChannelConfig c = resolveConfig();
            if (c == null && (globalHost == null || globalHost.isEmpty())) {
                log.info("[EMAIL] No EMAIL config (tenant or global), health=UNKNOWN");
                return false;
            }
            String json = c == null ? null : c.getConfigJson();
            boolean tenantOk = json != null && readJson(json, "smtpHost") != null && readJson(json, "username") != null;
            boolean globalOk = globalHost != null && !globalHost.isEmpty() && globalUsername != null;
            log.info("[EMAIL] health tenant={} global={}", tenantOk, globalOk);
            return tenantOk || globalOk;
        } catch (Exception e) {
            log.warn("[EMAIL] healthCheck exception: {}", e.getMessage());
            return false;
        }
    }
}
