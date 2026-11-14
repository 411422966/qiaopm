package com.meiyetec.booking;

import com.meiyetec.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * I177：预约确认发送幂等闸门（EXT-19 多语言预约确认）。
 *
 * <p>背景：{@code POST /intl-booking/{id}/send-confirmation} 与 {@code POST /intl-booking/{id}/status}
 * 的 CONFIRMED 自动触发都会直接调用阿里云短信 / SMTP 邮件 / 微信订阅消息（**外部计费**），
 * 此前没有任何防重复触发机制 —— 同一预约可在数秒内被反复发送，成本直接放大。
 *
 * <p>本组件只做"幂等"（同一预约在窗口内只发一次），<b>不是频率限流</b>。
 * 真正的 QPS 限流属于平台级通用能力，见 {@code audit/issues/I169-app-layer-general-rate-limit.md}。
 *
 * <p>失败策略：Redis 未装配或不可用时 <b>fail-open</b>（放行发送），仅告警不阻断业务。
 * 幂等键在发送<b>前</b>占用、且失败也不释放 —— 宁可让运营等一个窗口，也不要为了"允许重试"
 * 而重新打开"同一次确认被重复计费"的口子。
 *
 * <p>已知边界：预约若未配置任何可发送渠道（手机号 / 邮箱 / 小程序 openId 全空），
 * 本次调用仍会占用窗口（EXT-19 约定该场景返回 200 + overallSuccess=false，不在本次修复范围内）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingConfirmationIdempotency {

    /** Redis key 前缀，最终 key = booking:confirm:{tenantId}:{bookingId} */
    private static final String KEY_PREFIX = "booking:confirm:";

    /** sys_config 配置 key（秒） */
    private static final String WINDOW_CONFIG_KEY = "intl.booking.send.idempotent.window";

    /** 幂等窗口默认值（秒），sys_config 未配置或非法时使用 */
    private static final int WINDOW_DEFAULT = 300;

    private final SysConfigService sysConfigService;

    /** Redis（可选依赖）：未装配时走 fail-open */
    @Autowired(required = false)
    private StringRedisTemplate redis;

    /**
     * 尝试获取"发送许可"。
     *
     * @param tenantId  租户 ID
     * @param bookingId 预约 ID
     * @return true = 窗口内首次，允许发送；false = 窗口内已发送过，调用方应跳过
     */
    public boolean acquire(Long tenantId, Long bookingId) {
        if (redis == null) {
            log.warn("[BOOKING-CONFIRM-IDEMPOTENT] Redis 未装配，跳过幂等闸门（fail-open）");
            return true;
        }
        String key = KEY_PREFIX + (tenantId == null ? "0" : tenantId) + ":" + bookingId;
        try {
            int window = resolveWindow();
            Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(window));
            if (Boolean.TRUE.equals(acquired)) {
                return true;
            }
            log.info("[BOOKING-CONFIRM-IDEMPOTENT] 幂等命中，跳过发送: key={} window={}s", key, window);
            return false;
        } catch (Exception e) {
            // fail-open：Redis / sys_config 故障不阻断发送链路，仅告警留痕
            log.warn("[BOOKING-CONFIRM-IDEMPOTENT] 幂等不可用，降级为不拦截（fail-open）：{}", e.getMessage());
            return true;
        }
    }

    /**
     * 读取幂等窗口（秒）。
     * <p>sys_config 无 tenant_id 列 → 全局配置，对所有租户生效。
     */
    private int resolveWindow() {
        int window = sysConfigService.getInt(WINDOW_CONFIG_KEY, WINDOW_DEFAULT);
        return window > 0 ? window : WINDOW_DEFAULT;
    }
}
