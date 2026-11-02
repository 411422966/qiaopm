package com.meiyetec.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 报表导出专用异步线程池。
 *
 * <p>背景：@Async 默认使用 SimpleAsyncTaskExecutor —— 每次调用新建一个线程，
 * 不复用、不排队，高并发导出时会直接打满系统线程数。报表导出是重 IO/重 SQL 操作，
 * 需要有界线程池 + 有界队列。</p>
 *
 * <p>队列满时用 AbortPolicy（拒绝而非 CallerRuns）：
 * CallerRuns 会让导出回落到 HTTP 请求线程同步执行，重新引入「请求长时间挂起」的问题。</p>
 */
@Configuration
public class ReportAsyncConfig {

    /** 线程池 Bean 名，@Async("reportExportExecutorPool") 显式引用 */
    public static final String REPORT_EXPORT_EXECUTOR = "reportExportExecutorPool";

    @Bean(name = REPORT_EXPORT_EXECUTOR)
    public ThreadPoolTaskExecutor reportExportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("report-export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
