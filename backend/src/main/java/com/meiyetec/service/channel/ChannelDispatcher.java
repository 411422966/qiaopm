package com.meiyetec.service.channel;

import com.meiyetec.entity.MktChannelConfig;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.MktChannelConfigMapper;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * T-D21：渠道分发中心
 * 按 channelType 路由到对应 ChannelSender 实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChannelDispatcher {

    private final List<ChannelSender> senders;
    private final MktChannelConfigMapper channelConfigMapper;

    /**
     * 别名映射（修复 I075：DB 历史数据 channel_type=MP，但 sender 实际为 WECHAT_OFFICIAL_ACCOUNT）
     */
    private static final Map<String, String> CHANNEL_ALIAS = Map.of(
            "MP", "WECHAT_OFFICIAL_ACCOUNT",
            "WX", "WECHAT_OFFICIAL_ACCOUNT",
            "WX_OFFICIAL", "WECHAT_OFFICIAL_ACCOUNT"
    );

    /**
     * 默认 fallback sender（修复 I061：getSender miss 时不再返回 null）
     */
    private final InAppSender defaultSender;

    @Autowired
    private JdbcTemplate jdbc;

    public ChannelSender getSender(String channelType) {
        if (channelType == null || channelType.isEmpty()) return defaultSender;
        // 别名归一化
        String canonical = CHANNEL_ALIAS.getOrDefault(channelType.toUpperCase(), channelType.toUpperCase());
        return senders.stream()
                .filter(s -> s.channelType().equalsIgnoreCase(canonical))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("[ChannelDispatcher] no sender for channelType={} (canonical={}), fallback to IN_APP", channelType, canonical);
                    return defaultSender;
                });
    }

    /**
     * 健康检查（一次轮询所有渠道）
     *
     * <p>T07-D4 例外：此方法为系统级批处理入口，无 HTTP/tenant 上下文。
     * 按设计仅更新默认租户（tenant_id=1）的渠道配置，
     * 这是有意保留的语义，不当作 T07 全局 1L 替换的覆盖目标。
     */
    public Result<Map<String, Object>> healthCheckAll() {
        Long tenantId = 1L;
        // T07-D4：明示此处的 1L 是有意保留的"系统级默认租户"，非 fallback
        log.warn("[T07-D4] healthCheckAll 仅更新默认租户(tenant_id=1) 的渠道配置；其他租户的渠道健康状态不会被更新（按设计保留）");
        List<Map<String, Object>> results = senders.stream().map(s -> {
            boolean ok = s.healthCheck();
            jdbc.update("UPDATE mkt_channel_config SET last_health_at = NOW(), last_health_msg = ? WHERE tenant_id = ? AND channel_type = ? AND deleted = 0",
                    ok ? "OK" : "FAIL", tenantId, s.channelType());
            Map<String, Object> r = new HashMap<>();
            r.put("channelType", s.channelType());
            r.put("healthy", ok);
            return r;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("results", results);
        return Result.success(data);
    }

    /**
     * 限流检查
     */
    public boolean rateLimitCheck(Long tenantId, Long brandId, String channelType) {
        // 1. 每分钟限额
        String minSql = "SELECT COALESCE(SUM(CASE WHEN t.sent_at >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) THEN 1 ELSE 0 END), 0) AS per_min " +
                "FROM marketing_touch_log t " +
                "WHERE t.tenant_id = ? AND t.channel_type = ? AND t.deleted = 0";
        Long perMin = jdbc.queryForObject(minSql, Long.class, tenantId, channelType);
        MktChannelConfig cfg = channelConfigMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MktChannelConfig>()
                .eq(MktChannelConfig::getTenantId, tenantId)
                .eq(MktChannelConfig::getBrandId, brandId)
                .eq(MktChannelConfig::getChannelType, channelType)
                .eq(MktChannelConfig::getStatus, 1)
                .last("LIMIT 1"));
        if (cfg != null && cfg.getRateLimitPerMin() != null && perMin != null && perMin >= cfg.getRateLimitPerMin()) {
            return false;
        }
        // 2. 每日限额
        String daySql = "SELECT COUNT(*) FROM marketing_touch_log t " +
                "WHERE t.tenant_id = ? AND t.channel_type = ? AND t.sent_at >= DATE(NOW()) AND t.deleted = 0";
        Long perDay = jdbc.queryForObject(daySql, Long.class, tenantId, channelType);
        if (cfg != null && cfg.getRateLimitPerDay() != null && perDay != null && perDay >= cfg.getRateLimitPerDay()) {
            return false;
        }
        return true;
    }
}
