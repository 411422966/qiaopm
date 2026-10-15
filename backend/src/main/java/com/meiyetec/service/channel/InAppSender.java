package com.meiyetec.service.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.MemberMessage;
import com.meiyetec.entity.MktChannelTemplate;
import com.meiyetec.mapper.MemberMessageMapper;
import com.meiyetec.security.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * M09-01：站内信渠道 — 真实写入 member_message 表
 *
 * <p>站内信不需要第三方 API，直接写入数据库。
 * <p>receiver 为 memberId（Long），自动关联会员姓名。
 * <p>消息类型根据 channelType 自动设置：MINI_PROGRAM → MP，IN_APP → IN_APP。
 */
@Slf4j
@Component
public class InAppSender implements ChannelSender {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MemberMessageMapper messageMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public String channelType() {
        return "IN_APP";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SendResult send(MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
        try {
            String content = SmsSender.render(tpl.getContent(), vars);
            String title = tpl.getSubject();

            if (title == null || title.isEmpty()) {
                title = inferTitle(tpl, vars);
            }

            // receiver 可能是 memberId 或 "memberId-name" 格式
            Long memberId = parseMemberId(receiver);

            // I082 fix: receiver 不可解析为 memberId 时（如 "ops" 运维组、""、字母串等），
            // 不能写入 member_id NOT NULL 列。改为 skip + 明确日志，避免污染 member_message。
            // 推荐：调用方对系统级通知改用 SMS/EMAIL/LOG 通道，不要混用 IN_APP 发送非会员 receiver。
            if (memberId == null) {
                log.warn("[IN_APP] receiver='{}' 不可解析为 memberId（系统/组级通道），跳过站内信持久化（channelType={} scene={}）",
                        receiver, tpl.getChannelType(), tpl.getScene());
                return SendResult.ok("MSG-SKIPPED-NO-MEMBER");
            }

            // 补充会员姓名
            String memberName = extractMemberName(receiver);
            if (memberName == null && vars != null && vars.containsKey("name")) {
                memberName = vars.get("name").toString();
            }

            MemberMessage msg = new MemberMessage();
            msg.setTenantId(getTenantId());
            msg.setMemberId(memberId);
            msg.setMemberName(memberName);
            msg.setTitle(title);
            msg.setContent(content);
            msg.setChannelType("IN_APP");
            msg.setMessageType(detectMessageType(tpl));
            msg.setIsRead(false);
            msg.setPriority(2);
            msg.setCreatedAt(LocalDateTime.now());
            msg.setOperatorName("system");

            // 关联业务
            if (vars != null) {
                if (vars.containsKey("orderId")) {
                    msg.setRelatedType("ORDER");
                    msg.setRelatedId(toLong(vars.get("orderId")));
                } else if (vars.containsKey("packageId")) {
                    msg.setRelatedType("PACKAGE");
                    msg.setRelatedId(toLong(vars.get("packageId")));
                } else if (vars.containsKey("bookingId")) {
                    msg.setRelatedType("BOOKING");
                    msg.setRelatedId(toLong(vars.get("bookingId")));
                }
            }

            messageMapper.insert(msg);
            log.info("[IN_APP] Message saved: id={} memberId={} title={}", msg.getId(), memberId, title);
            return SendResult.ok("MSG-" + msg.getId());
        } catch (Exception e) {
            log.error("[IN_APP] send exception: {}", e.getMessage(), e);
            return SendResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 根据模板推断消息类型
     */
    private String detectMessageType(MktChannelTemplate tpl) {
        String scene = tpl.getScene();
        if (scene == null) return "SYSTEM";
        scene = scene.toUpperCase();
        if (scene.contains("BIRTHDAY")) return "BIRTHDAY";
        if (scene.contains("EXPIRE")) return "EXPIRE";
        if (scene.contains("PROMO") || scene.contains("COUPON") || scene.contains("GIFT")) return "PROMO";
        if (scene.contains("TASK") || scene.contains("FOLLOW")) return "TASK";
        return "SYSTEM";
    }

    /**
     * 智能推断标题
     */
    private String inferTitle(MktChannelTemplate tpl, Map<String, Object> vars) {
        String scene = tpl.getScene();
        if (scene != null) scene = scene.toUpperCase();
        String name = vars != null && vars.containsKey("name") ? vars.get("name").toString() : "";

        if (scene == null) return "系统通知";

        if (scene.contains("BIRTHDAY")) return "🎂 生日快乐！美业给您送福利啦~";
        if (scene.contains("EXPIRE_30D") || scene.contains("EXPIRE")) return "⏰ 您的套餐即将到期，请及时使用";
        if (scene.contains("ORDER")) return "📋 订单完成通知";
        if (scene.contains("CARD_DELIVERED")) return "🎁 套餐到账通知";
        if (scene.contains("PROMO") || scene.contains("COUPON")) return "🎉 优惠券到账通知";
        if (scene.contains("TASK") || scene.contains("FOLLOW")) return "📝 您有一条回访任务";
        if (scene.contains("APPOINTMENT")) return "📅 预约提醒";

        return tpl.getSubject() != null ? tpl.getSubject() : "美业系统通知";
    }

    /**
     * 解析 receiver 为 memberId
     * 支持格式：12345 / "12345" / "12345-张三"
     */
    private Long parseMemberId(String receiver) {
        if (receiver == null) return null;
        receiver = receiver.trim();
        int dashIdx = receiver.indexOf('-');
        if (dashIdx > 0) receiver = receiver.substring(0, dashIdx);
        try {
            return Long.parseLong(receiver.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractMemberName(String receiver) {
        if (receiver == null) return null;
        int dashIdx = receiver.indexOf('-');
        if (dashIdx > 0 && dashIdx < receiver.length() - 1) {
            return receiver.substring(dashIdx + 1);
        }
        return null;
    }

    private Long getTenantId() {
        try {
            return TenantContextHolder.get().getTenantId();
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString().trim()); }
        catch (Exception e) { return null; }
    }

    @Override
    public boolean healthCheck() {
        try {
            // 验证表是否存在
            jdbc.queryForObject("SELECT 1 FROM member_message LIMIT 1", Integer.class);
            log.info("[IN_APP] health=OK");
            return true;
        } catch (Exception e) {
            log.warn("[IN_APP] healthCheck fail: {}", e.getMessage());
            return false;
        }
    }
}
