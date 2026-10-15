package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.BnplOrder;
import com.meiyetec.util.Result;
import java.math.BigDecimal;
import java.util.Map;

/**
 * P2-16 BNPL 先买后付服务
 */
public interface BnplService {

    Result<Map<String, Object>> queryCredit(String provider, String phone, Long tenantId);

    Result<BnplOrder> createOrder(Long memberId, String provider,
            String bizOrderNo, BigDecimal amount, String currency,
            Integer installments, Long tenantId);

    Result<Page<BnplOrder>> listOrders(String provider, Long memberId,
            Integer status, long current, long size, Long tenantId);

    Result<Void> cancel(Long orderId, Long tenantId);

    Result<Map<String, Object>> statistics(String startDate, String endDate, Long tenantId);

    Result<Void> simulateNotify(Long orderId, String eventType, Long tenantId);

    /**
     * 处理来自 BNPL 平台的真实 webhook 回调
     *
     * <p>路由到 {@link com.meiyetec.payment.bnpl.BnplProviderRouter#get(String)} 获取对应 provider，
     * 调用 {@code provider.handleWebhook(eventType, payload, signature, secret)}，
     * 再按返回的 {@code newStatus} 更新本地订单。
     *
     * <p>此方法替换原有的 {@code simulateNotify}（仅供开发测试使用）。
     *
     * @param provider  平台代码（KLARNA / AFTERPAY）
     * @param eventType 事件类型
     * @param payload   原始 JSON body
     * @param signature 签名/Token
     * @param secret    webhook 密钥（优先用配置；传入 null 则用 provider 默认 secret）
     * @param tenantId  租户 ID（用于订单归属校验）
     * @return 处理结果（含验签状态 / 新状态 / 错误信息）
     */
    Result<Map<String, Object>> handleWebhook(String provider, String eventType,
            String payload, String signature, String secret, Long tenantId);

    /**
     * 列出所有已注册的 BNPL provider 及配置状态。
     */
    Map<String, Boolean> listProviders();
}