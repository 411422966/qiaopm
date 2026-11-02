package com.meiyetec.service;

import com.meiyetec.config.ReportAsyncConfig;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 报表导出异步执行器。
 *
 * <p>存在的理由：@Async 依赖 Spring AOP 代理，同类内部直接调用会绕过代理导致 @Async 失效
 * （导出会同步阻塞 HTTP 请求线程，请求长时间挂起、任务状态长时间停在「处理中」）。
 * 把异步调用拆到独立 Bean，通过跨 Bean 调用让代理生效。</p>
 *
 * <p>为什么不放在 ReportV2Service 自身代理上：ReportV2Service 是 @RestController，
 * 给控制器类加 @Async 会对它做 CGLIB 代理，影响 Spring MVC 的 handler 识别。</p>
 *
 * <p>租户安全：executeExport 的租户 ID 取自任务记录本身（report_export_task.tenant_id），
 * 不依赖 ThreadLocal，因此异步线程无需重建租户上下文，也不存在串档风险。</p>
 */
@Component
public class ReportExportExecutor {

    /** @Lazy 打断循环依赖：ReportV2Service → ReportExportExecutor → ReportV2Service */
    private final ReportV2Service reportV2Service;

    public ReportExportExecutor(@Lazy ReportV2Service reportV2Service) {
        this.reportV2Service = reportV2Service;
    }

    /**
     * 异步执行导出任务。
     */
    @Async(ReportAsyncConfig.REPORT_EXPORT_EXECUTOR)
    public void submitAsync(Long taskId) {
        reportV2Service.executeExport(taskId);
    }
}
