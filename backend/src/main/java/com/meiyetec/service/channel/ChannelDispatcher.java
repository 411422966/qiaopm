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
     *
     * <p>修复 I178：计数口径与配置口径对齐为「tenant + brand + channel」。
     * 原实现只按 {@code tenant_id + channel_type} 计数，而额度配置是按
     * {@code tenant_id + brand_id + channel_type} 查找的（{@code MktChannelConfig}），
     * 多品牌租户下品牌之间会互相消耗配额，且单个品牌的额度被全租户流量放大 N 倍。
     *
     * <p>实现说明：触达明细表 {@code marketing_touch_log} 本身没有 brand_id 列，
     * 品牌归属在任务表 {@code marketing_touch_task.brand_id}，
     * 因此按 {@code task_id} 关联任务表取品牌；{@code <=>} 为 MySQL 的 NULL 安全等于，
     * 保证「无品牌任务」（brand_id IS NULL）只与「无品牌任务」互相计数。
     *
     * <p>口径变更声明：历史数据中 {@code marketing_touch_log} 有 8 行 task_id 指向
     * 已不存在的任务（孤儿行），改用关联计数后这些行不再计入任何品牌的配额。
     */
    public boolean rateLimitCheck(Long tenantId, Long brandId, String channelType) {
        // 1. 每分钟限额 —— 计数口径：tenant_id + brand_id + channel_type
        String minSql = "SELECT COALESCE(SUM(CASE WHEN t.sent_at >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) THEN 1 ELSE 0 END), 0) AS per_min " +
                "FROM marketing_touch_log t " +
                "JOIN marketing_touch_task tt ON tt.id = t.task_id AND tt.deleted = 0 " +
                "WHERE t.tenant_id = ? AND t.channel_type = ? AND t.deleted = 0 " +
                "AND t.sent_at IS NOT NULL AND tt.brand_id <=> ?";
        Long perMin = jdbc.queryForObject(minSql, Long.class, tenantId, channelType, brandId);
        MktChannelConfig cfg = channelConfigMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MktChannelConfig>()
                .eq(MktChannelConfig::getTenantId, tenantId)
                .eq(MktChannelConfig::getBrandId, brandId)
                .eq(MktChannelConfig::getChannelType, channelType)
                .eq(MktChannelConfig::getStatus, 1)
                .last("LIMIT 1"));
        if (cfg != null && cfg.getRateLimitPerMin() != null && perMin != null && perMin >= cfg.getRateLimitPerMin()) {
            return false;
        }
        // 2. 每日限额 —— 计数口径同为 tenant_id + brand_id + channel_type
        String daySql = "SELECT COUNT(*) FROM marketing_touch_log t " +
                "JOIN marketing_touch_task tt ON tt.id = t.task_id AND tt.deleted = 0 " +
                "WHERE t.tenant_id = ? AND t.channel_type = ? AND t.sent_at >= DATE(NOW()) AND t.deleted = 0 " +
                "AND tt.brand_id <=> ?";
        Long perDay = jdbc.queryForObject(daySql, Long.class, tenantId, channelType, brandId);
        if (cfg != null && cfg.getRateLimitPerDay() != null && perDay != null && perDay >= cfg.getRateLimitPerDay()) {
            return false;
        }
        return true;
    }
}
