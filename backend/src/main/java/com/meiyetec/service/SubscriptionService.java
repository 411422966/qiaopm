package com.meiyetec.service;

import com.meiyetec.entity.SubscriptionMember;
import com.meiyetec.entity.SubscriptionPlan;

import java.util.List;
import java.util.Map;

/**
 * M09 订阅会员 Service 接口
 *
 * <p>实现类：{@link com.meiyetec.service.impl.SubscriptionServiceImpl}
 * <p>HTTP 层：{@link com.meiyetec.controller.SubscriptionController}
 *
 * <p>职责划分：
 * <ul>
 *   <li>Service：纯业务逻辑 + 事务边界 + {@code @Scheduled} 自动续费 Job</li>
 *   <li>Controller：HTTP 入参解析 + {@code TenantContextHolder} 提取 + {@code Result} 封装</li>
 * </ul>
 */
public interface SubscriptionService {

    // 计划管理
    SubscriptionPlan createPlan(SubscriptionPlan p, Long tenantId);

    List<SubscriptionPlan> listPlans(Long tenantId);

    /**
     * 初始化默认订阅计划（幂等：已存在则跳过）
     * @return 实际新增数量
     */
    int initDefaults(Long tenantId);

    // 订阅管理
    SubscriptionMember subscribe(Long tenantId, Long planId, Long memberId, String paymentMethod);

    void cancel(Long subscriptionId, Long tenantId, String reason);

    void pause(Long subscriptionId, Long tenantId);

    void resume(Long subscriptionId, Long tenantId);

    List<SubscriptionMember> byMember(Long memberId, Long tenantId);

    /** 使用一次订阅服务（扣减 remainingCount） */
    void use(Long memberId, Long tenantId);

    /** Dashboard 统计数据 */
    Map<String, Object> dashboard(Long tenantId);
}
