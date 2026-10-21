package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.service.CustomerCareService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * R3.0-C：CRM 客服模块 HTTP 入口（精简版，2026-09-09 重构）。
 *
 * <p>本控制器历史上曾承载回访任务、评价、评价模板、运营看板等路由。为消除
 * 与专门控制器之间的路由冲突（{@code Ambiguous mapping}），按 R3.0-C 重构规范
 * 拆分如下：
 *
 * <ul>
 *   <li>T-C21 回访任务 → {@link CrmFollowUpTaskController}（{@code /crm/care/follow/task/**}）</li>
 *   <li>T-C23 评价（非 tpl）→ {@link CrmReviewController}（{@code /crm/care/review/**}）</li>
 *   <li>T-C23 评价模板 → {@link CrmReviewTemplateController}（{@code /crm/care/review/tpl/**}）</li>
 *   <li>T-C24 客户运营菜单 → {@link CrmCareOpsController}（{@code /crm/care/ops/overview}）</li>
 *   <li>T-C22 投诉工单主路由 → {@link ComplaintTicketController}（{@code /crm/care/complaint/**}）</li>
 * </ul>
 *
 * <p>当前唯一保留的路由：
 * <ul>
 *   <li>{@code POST /crm/care/complaint/{id}/reply}（CustomerCareController 独有）</li>
 * </ul>
 *
 * <p>前置契约：{@link CustomerCareService} 中的 service 方法（如
 * {@code replyComplaint}）仍保留以支持 ComplaintTicketController 之外的内部调用，
 * 但本控制器不再直接暴露任何其它 HTTP endpoint。
 */
@RestController
@RequestMapping("/crm/care")
@RequiredArgsConstructor
public class CustomerCareController {

    private final CustomerCareService careService;

    // ====================================================================
    // T-C22 投诉工单 reply（CustomerCareController 独有）
    // ====================================================================

    @PostMapping("/complaint/{id}/reply")
    @RequirePermission("crm:care:complaint:reply")
    public Result<Void> replyComplaint(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return careService.replyComplaint(id, body);
    }
}