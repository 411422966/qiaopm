package com.meiyetec.task;

import com.meiyetec.entity.SysTenant;
import com.meiyetec.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantFreezeTask {

    private final SysTenantService tenantService;

    /**
     * 每小时第 5 分钟扫一次过期租户
     */
    @Scheduled(cron = "0 5 * * * ?")
    public void scanExpired() {
        log.info("[定时任务] 扫描过期租户 - 开始 {}", LocalDateTime.now());
        try {
            List<SysTenant> all = tenantService.list();
            int frozenTenants = 0;
            int frozenUsers = 0;
            for (SysTenant t : all) {
                if (!isExpired(t)) continue;
                int n = tenantService.freezeExpiredTenant(t.getId());
                if (n > 0 || (t.getStatus() != null && t.getStatus() == 0)) {
                    frozenTenants++;
                    frozenUsers += n;
                    log.warn("[定时任务] 租户[{}]{} 已过期并冻结，影响 {} 个用户",
                        t.getId(), t.getTenantName(), n);
                }
            }
            log.info("[定时任务] 扫描过期租户 - 完成，处理租户 {} 个，冻结用户 {} 个",
                frozenTenants, frozenUsers);
        } catch (Exception e) {
            log.error("[定时任务] 扫描过期租户异常", e);
        }
    }

    private boolean isExpired(SysTenant t) {
        if (t.getExpireAt() == null || t.getExpireAt().isEmpty()) return false;
        try {
            LocalDateTime expire = LocalDateTime.parse(t.getExpireAt(),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return expire.isBefore(LocalDateTime.now());
        } catch (Exception e) {
            return false;
        }
    }
}