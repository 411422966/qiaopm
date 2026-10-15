package com.meiyetec.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.meiyetec.mapper.SysLogMapper;
import com.meiyetec.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import com.meiyetec.entity.SysLog;

/**
 * 操作日志保留清理任务
 * - 每天凌晨 3 点扫描 sys_log
 * - 删除超过保留天数（sys_config: log.retention-days）的历史记录
 * - 默认 90 天，可在「系统设置」调整（30 ~ 3650）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogRetentionTask {

    private static final String CONFIG_KEY = "log.retention-days";
    private static final int DEFAULT_RETENTION_DAYS = 90;
    private static final int MIN_RETENTION_DAYS = 1;
    private static final int MAX_RETENTION_DAYS = 3650;

    private final SysLogMapper logMapper;
    private final SysConfigService configService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredLogs() {
        log.info("[定时任务] 日志清理 - 开始 {}", LocalDateTime.now());
        try {
            int days = resolveRetentionDays();
            LocalDateTime cutoff = LocalDateTime.now().minusDays(days);

            int deleted;
            int attempt = 0;
            do {
                QueryWrapper<SysLog> qw = new QueryWrapper<>();
                qw.lt("created_at", cutoff).last("LIMIT 5000");
                deleted = logMapper.delete(qw);
                attempt++;
                if (deleted > 0) {
                    log.info("[定时任务] 日志清理 - 第 {} 批删除 {} 条", attempt, deleted);
                }
            } while (deleted > 0 && attempt < 50);

            log.info("[定时任务] 日志清理 - 完成，保留 {} 天，删除 {} 批", days, attempt);
        } catch (Exception e) {
            log.error("[定时任务] 日志清理异常", e);
        }
    }

    private int resolveRetentionDays() {
        int v = configService.getInt(CONFIG_KEY, DEFAULT_RETENTION_DAYS);
        if (v < MIN_RETENTION_DAYS || v > MAX_RETENTION_DAYS) {
            log.warn("[定时任务] 配置值 {} 越界，回退默认值 {}", v, DEFAULT_RETENTION_DAYS);
            return DEFAULT_RETENTION_DAYS;
        }
        return v;
    }
}