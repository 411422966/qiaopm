package com.meiyetec.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.Store;
import com.meiyetec.mapper.StoreMapper;
import com.meiyetec.service.ResourceUtilizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * M11-04 技师利用率定时重算 Job
 *
 * <p>每日凌晨 1:00 扫描所有活跃门店，对昨日数据执行利用率重算，
 * 保证历史 booking 数据补录后 utilization 记录同步刷新。
 *
 * <p>执行策略：
 * <ul>
 *   <li>昨日 date = LocalDate.now().minusDays(1)</li>
 *   <li>按门店逐一调用 {@link ResourceUtilizationService#recalculate(Long, LocalDate)}</li>
 *   <li>单门店失败不影响其他门店，记录 warn 并继续</li>
 *   <li>无活跃门店时跳过本次执行</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceUtilizationCalcJob {

    private final ResourceUtilizationService utilizationService;
    private final StoreMapper storeMapper;

    /** 每日凌晨 1:00 执行（前一天 booking 数据已稳定） */
    @Scheduled(cron = "0 0 1 * * ?")
    public void runDailyUtilizationCalc() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        log.info("[UtilizationJob] 开始重算 {} 利用率", targetDate);

        // 拉所有活跃门店
        var stores = storeMapper.selectList(
                new LambdaQueryWrapper<Store>()
                        .eq(Store::getDeleted, 0)
                        .eq(Store::getStatus, 1));
        if (stores.isEmpty()) {
            log.info("[UtilizationJob] 无活跃门店，跳过");
            return;
        }

        int success = 0, failed = 0;
        for (Store store : stores) {
            try {
                utilizationService.recalculate(store.getId(), targetDate);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("[UtilizationJob] 门店 {} (id={}) 利用率重算失败: {}",
                        store.getStoreName(), store.getId(), e.getMessage());
            }
        }

        log.info("[UtilizationJob] 完成: 成功 {} 门店, 失败 {} 门店, date={}",
                success, failed, targetDate);
    }
}
