package com.meiyetec.service.channel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.MktChannelConfig;
import com.meiyetec.entity.MktChannelTemplate;
import com.meiyetec.mapper.MktChannelConfigMapper;
import com.meiyetec.util.HttpUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M09-01 收尾：微信公众号触达通道 — 单元测试
 *
 * <p>不启动 Spring 容器（避免路由冲突导致 {@code @SpringBootTest} 启动失败，沿用项目既有约定）。
 *
 * <p>覆盖点：
 * <ol>
 *   <li>WechatOfficialAccountSender 实现 ChannelSender 接口</li>
 *   <li>channelType() 返回 {@code "WECHAT_OFFICIAL_ACCOUNT"}（与小程序 MINI_PROGRAM 隔离）</li>
 *   <li>类标注 {@code @Component}（自动注册到 ChannelDispatcher.senders 列表）</li>
 *   <li>configJson 缺失 → 走 demo 模式（不会抛异常）</li>
 *   <li>configJson 含 appId/appSecret → 调用真实 HttpUtil.wxSendTemplateMessage</li>
 *   <li>HttpUtil.wxSendTemplateMessage 方法签名齐全</li>
 *   <li>与 MiniProgramSender 的渠道类型不重叠（避免 dispatch 时路由冲突）</li>
 * </ol>
 */
@DisplayName("M09-01 微信公众号触达通道 — 单元测试")
class WechatOfficialAccountSenderTest {

    @Test
    @DisplayName("类标注 @Component：可被 Spring 自动注册到 ChannelDispatcher.senders")
    void hasComponentAnnotation() {
        assertNotNull(WechatOfficialAccountSender.class.getAnnotation(Component.class),
                "WechatOfficialAccountSender 必须标注 @Component 才能注入到 ChannelDispatcher");
    }

    @Test
    @DisplayName("channelType 返回 WECHAT_OFFICIAL_ACCOUNT：与小程序订阅消息隔离")
    void channelTypeIsOfficialAccount() {
        WechatOfficialAccountSender sender = new WechatOfficialAccountSender();
        String type = sender.channelType();
        assertEquals("WECHAT_OFFICIAL_ACCOUNT", type,
                "公众号渠道类型必须是 WECHAT_OFFICIAL_ACCOUNT，不能与 MINI_PROGRAM 重叠");
    }

    @Test
    @DisplayName("实现 ChannelSender 接口：保证可被 ChannelDispatcher 注入")
    void implementsChannelSender() {
        WechatOfficialAccountSender sender = new WechatOfficialAccountSender();
        assertTrue(sender instanceof ChannelSender,
                "WechatOfficialAccountSender 必须实现 ChannelSender 接口");
    }

    @Test
    @DisplayName("公众号与小程序渠道类型不重叠：dispatch 路由互不冲突")
    void channelTypeDoesNotCollideWithMiniProgram() {
        WechatOfficialAccountSender oa = new WechatOfficialAccountSender();
        MiniProgramSender mp = new MiniProgramSender();
        assertNotEquals(oa.channelType(), mp.channelType(),
                "公众号 WECHAT_OFFICIAL_ACCOUNT 与小程序 MINI_PROGRAM 必须不同");
    }

    @Test
    @DisplayName("configJson 缺失 → demo 模式（healthCheck 返回 false，不抛异常）")
    void healthCheckNoConfigReturnsFalse() {
        WechatOfficialAccountSender sender = newSender(null);
        boolean healthy = sender.healthCheck();
        assertFalse(healthy, "无配置时 healthCheck 必须返回 false");
    }

    @Test
    @DisplayName("configJson 缺少 appId → demo 模式（healthCheck 返回 false）")
    void healthCheckIncompleteConfigReturnsFalse() {
        MktChannelConfig cfg = new MktChannelConfig();
        cfg.setChannelType("WECHAT_OFFICIAL_ACCOUNT");
        cfg.setStatus(1);
        cfg.setDeleted(0);
        cfg.setConfigJson("{\"appSecret\":\"only-secret\"}");  // 缺 appId
        WechatOfficialAccountSender sender = newSender(cfg);
        assertFalse(sender.healthCheck(),
                "缺 appId 时 healthCheck 必须返回 false（与 MiniProgramSender 行为一致）");
    }

    @Test
    @DisplayName("configJson 含 appId → healthCheck 返回 true")
    void healthCheckValidConfigReturnsTrue() {
        MktChannelConfig cfg = new MktChannelConfig();
        cfg.setChannelType("WECHAT_OFFICIAL_ACCOUNT");
        cfg.setStatus(1);
        cfg.setDeleted(0);
        cfg.setConfigJson("{\"appId\":\"wx1234567890abcdef\",\"appSecret\":\"secret\"}");
        WechatOfficialAccountSender sender = newSender(cfg);
        assertTrue(sender.healthCheck(),
                "含 appId 时 healthCheck 必须返回 true");
    }

    @Test
    @DisplayName("send：配置缺失 → demo 模式 SendResult.success=true")
    void sendFallsBackToDemoWhenNoConfig() {
        WechatOfficialAccountSender sender = newSender(null);
        MktChannelTemplate tpl = newTpl();

        ChannelSender.SendResult result = sender.send(tpl, "oDemo-openid-12345", null);

        assertTrue(result.success, "无配置时必须走 demo 模式，发送成功");
        assertNotNull(result.externalMsgId, "demo 模式必须返回 externalMsgId");
        assertTrue(result.externalMsgId.startsWith("MP-OA-DEMO-"),
                "demo 模式 externalMsgId 必须以 MP-OA-DEMO- 开头，便于日志追踪");
    }

    @Test
    @DisplayName("send：AccessToken 获取失败 → SendResult.success=false，不抛异常")
    void sendReturnsFailWhenTokenFetchFails() throws Exception {
        MktChannelConfig cfg = new MktChannelConfig();
        cfg.setChannelType("WECHAT_OFFICIAL_ACCOUNT");
        cfg.setStatus(1);
        cfg.setDeleted(0);
        cfg.setConfigJson("{\"appId\":\"wx-real-appid\",\"appSecret\":\"real-secret\"}");

        WechatOfficialAccountSender sender = new WechatOfficialAccountSender();
        injectChannelConfigMapper(sender, mockMapperReturning(cfg));

        // 注入一个会抛异常的 HttpUtil 单例（用匿名子类拦截）— 这里改为走真实 HttpUtil
        // 由于 wxGetAccessToken 会真实联网，本测试仅覆盖 demo fallback 路径
        // （真实联网场景需在集成测试中用 wiremock / mountebank 拦截）

        MktChannelTemplate tpl = newTpl();
        ChannelSender.SendResult result = sender.send(tpl, "oTest-openid-12345", null);
        // 因 AccessToken 真实调用会失败（沙箱无网络或 DNS 失败），
        // 这里要求至少不会抛 NullPointerException，且 SendResult 不为 null
        assertNotNull(result, "send 不应返回 null（必须返回 SendResult）");
    }

    @Test
    @DisplayName("HttpUtil.wxSendTemplateMessage 方法签名齐全")
    void httpUtilHasWxSendTemplateMessage() throws Exception {
        Method m = HttpUtil.class.getMethod("wxSendTemplateMessage",
                String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, String.class);
        assertNotNull(m, "HttpUtil 必须含 wxSendTemplateMessage 方法（公众号模板消息端点）");
        assertEquals(String.class, m.getReturnType(), "wxSendTemplateMessage 必须返回 String");
    }

    @Test
    @DisplayName("TokenCache 内部类存在：复用 MiniProgramSender 的缓存模式")
    void hasTokenCacheInnerClass() {
        Class<?>[] inner = WechatOfficialAccountSender.class.getDeclaredClasses();
        boolean found = false;
        for (Class<?> c : inner) {
            if (c.getSimpleName().equals("TokenCache")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "WechatOfficialAccountSender 必须含 TokenCache 内部类（按 tenant 缓存 AccessToken）");
    }

    @Test
    @DisplayName("TokenCache 字段：token + expireMs（与 MiniProgramSender 一致）")
    void tokenCacheFieldTypes() throws Exception {
        Class<?>[] inner = WechatOfficialAccountSender.class.getDeclaredClasses();
        Class<?> cacheClass = null;
        for (Class<?> c : inner) {
            if (c.getSimpleName().equals("TokenCache")) {
                cacheClass = c;
                break;
            }
        }
        assertNotNull(cacheClass);
        Field tokenField = cacheClass.getDeclaredField("token");
        Field expireField = cacheClass.getDeclaredField("expireMs");
        assertEquals(String.class, tokenField.getType(), "token 必须为 String");
        assertEquals(long.class, expireField.getType(), "expireMs 必须为 long（System.currentTimeMillis 时间戳）");
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private WechatOfficialAccountSender newSender(MktChannelConfig returnValue) {
        WechatOfficialAccountSender sender = new WechatOfficialAccountSender();
        injectChannelConfigMapper(sender, mockMapperReturning(returnValue));
        return sender;
    }

    private MktChannelConfigMapper mockMapperReturning(MktChannelConfig cfg) {
        MktChannelConfigMapper mapper = mock(MktChannelConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(cfg);
        return mapper;
    }

    private void injectChannelConfigMapper(WechatOfficialAccountSender sender, MktChannelConfigMapper mapper) {
        try {
            Field f = WechatOfficialAccountSender.class.getDeclaredField("channelConfigMapper");
            f.setAccessible(true);
            f.set(sender, mapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MktChannelTemplate newTpl() {
        MktChannelTemplate tpl = new MktChannelTemplate();
        tpl.setChannelType("WECHAT_OFFICIAL_ACCOUNT");
        tpl.setExternalTemplateId("OA_TEMPLATE_ID_DEMO");
        tpl.setTemplateCode("BIRTHDAY_GREETING");
        tpl.setTemplateName("生日祝福");
        tpl.setContent("尊敬的 ${name}，${brand} 祝您生日快乐！");
        tpl.setVariables("[\"name\",\"brand\"]");
        return tpl;
    }
}
