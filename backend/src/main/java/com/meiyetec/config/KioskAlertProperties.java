package com.meiyetec.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Kiosk 告警 / 运营阈值配置（EXT-P2）
 *
 * <p>所有阈值集中在 application.yml 的 {@code kiosk.alert} 节点，可运行时调整无需改代码。
 * <p>变更后无需重启（Spring Cloud 环境下可热刷新；单体应用重启生效）。
 *
 * <pre>
 * kiosk:
 *   alert:
 *     enabled:              true         # 总开关
 *     info-minutes:         5            # → INFO 阈值
 *     warn-minutes:         15           # → WARN 阈值
 *     critical-minutes:     30           # → CRITICAL 阈值
 *     scan-interval-ms:     60000        # 离线扫描周期
 *     push-enabled:         true         # 离线推送总开关
 *     push-channel:         IN_APP       # 推送渠道：IN_APP / SMS / MINI_PROGRAM
 *     cooldown-minutes:     30           # 同一设备两次推送冷却（避免告警风暴）
 *     daily-target-default: 80           # 客流目标默认值（前端可覆写）
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "kiosk.alert")
public class KioskAlertProperties {

    /** 总开关 */
    private boolean enabled = true;

    /** → INFO 阈值（心跳超过该分钟数视为轻度离线） */
    private int infoMinutes = 5;

    /** → WARN 阈值 */
    private int warnMinutes = 15;

    /** → CRITICAL 阈值 */
    private int criticalMinutes = 30;

    /** 离线扫描周期（毫秒） */
    private long scanIntervalMs = 60_000L;

    /** 离线推送总开关 */
    private boolean pushEnabled = true;

    /** 默认推送渠道（IN_APP / SMS / MINI_PROGRAM） */
    private String pushChannel = "IN_APP";

    /** 同一设备两次推送冷却（分钟） */
    private int cooldownMinutes = 30;

    /** 客流日均目标默认值 */
    private int dailyTargetDefault = 80;

    /** 严重度映射：离线分钟数 → 严重度级别 */
    public String severityOf(long offlineMinutes) {
        if (offlineMinutes < 0) return "NEVER";
        if (offlineMinutes >= criticalMinutes) return "CRITICAL";
        if (offlineMinutes >= warnMinutes)     return "WARN";
        if (offlineMinutes >= infoMinutes)    return "INFO";
        return "OK";
    }
}
