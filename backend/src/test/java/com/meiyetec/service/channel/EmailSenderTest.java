package com.meiyetec.service.channel;

import com.meiyetec.entity.MktChannelTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXT-19：EmailSender 单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>渠道类型 = EMAIL</li>
 *   <li>receiver 校验：非邮箱地址 → fail</li>
 *   <li>receiver 校验：null / 空 → fail</li>
 *   <li>demo 模式：无 channelConfigMapper 时返回 success（避免 mapper null 抛错）</li>
 * </ul>
 */
@DisplayName("EXT-19 EmailSender")
class EmailSenderTest {

    @Test
    @DisplayName("channelType = EMAIL")
    void channelType() {
        EmailSender sender = new EmailSender();
        assertEquals("EMAIL", sender.channelType());
    }

    @Test
    @DisplayName("send：receiver 是合法邮箱 + 无 mapper → demo 模式（成功）")
    void sendValidEmailDemoMode() {
        EmailSender sender = new EmailSender();
        // channelConfigMapper 为 null → demo 模式（不抛异常，返回 success）
        MktChannelTemplate tpl = new MktChannelTemplate();
        tpl.setSubject("测试主题");
        tpl.setContent("测试内容 ${name}");
        ChannelSender.SendResult r = sender.send(tpl, "alice@example.com",
                Map.of("name", "Alice"));
        assertNotNull(r);
        assertTrue(r.success, "demo 模式应返回成功");
        assertNotNull(r.externalMsgId);
        assertTrue(r.externalMsgId.startsWith("EMAIL-DEMO-"));
    }

    @Test
    @DisplayName("send：receiver 不是邮箱 → fail")
    void sendInvalidEmail() {
        EmailSender sender = new EmailSender();
        MktChannelTemplate tpl = new MktChannelTemplate();
        tpl.setContent("body");
        ChannelSender.SendResult r = sender.send(tpl, "not-an-email", Map.of());
        assertFalse(r.success);
        assertNotNull(r.errorMsg);
        assertTrue(r.errorMsg.contains("not a valid email"));
    }

    @Test
    @DisplayName("send：receiver 为 null → fail")
    void sendNullReceiver() {
        EmailSender sender = new EmailSender();
        MktChannelTemplate tpl = new MktChannelTemplate();
        ChannelSender.SendResult r = sender.send(tpl, null, Map.of());
        assertFalse(r.success);
    }

    @Test
    @DisplayName("send：receiver 为空字符串 → fail")
    void sendEmptyReceiver() {
        EmailSender sender = new EmailSender();
        MktChannelTemplate tpl = new MktChannelTemplate();
        ChannelSender.SendResult r = sender.send(tpl, "", Map.of());
        assertFalse(r.success);
    }

    @Test
    @DisplayName("healthCheck：无 mapper → false（健康 = UNKNOWN）")
    void healthCheckWithoutMapper() {
        EmailSender sender = new EmailSender();
        // channelConfigMapper null → healthCheck 内部 try-catch 返回 false
        assertFalse(sender.healthCheck());
    }
}
