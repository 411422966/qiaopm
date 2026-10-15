package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.mapper.OverseasPaymentMapper;
import com.meiyetec.entity.OverseasPayment;
import com.meiyetec.entity.OverseasPaymentGateway;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.payment.OverseasPaymentAdapter;
import com.meiyetec.payment.OverseasPaymentAdapter.CreatePaymentIntentRequest;
import com.meiyetec.payment.OverseasPaymentAdapter.CreatePaymentIntentResponse;
import com.meiyetec.payment.OverseasPaymentAdapter.RefundRequest;
import com.meiyetec.payment.OverseasPaymentAdapter.RefundResponse;
import com.meiyetec.service.OverseasPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasPaymentServiceImpl extends ServiceImpl<OverseasPaymentMapper, OverseasPayment> implements OverseasPaymentService {

    private final JdbcTemplate jdbc;
    private final Map<String, OverseasPaymentAdapter> adapterMap;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OverseasPayment createPayment(String orderType, Long orderId, String orderNo,
                                          Long memberId, String memberName, String memberEmail,
                                          BigDecimal amount, String currency, String paymentMethod, Long tenantId) {

        String gatewayCode = selectBestGateway(currency, null, paymentMethod, amount, tenantId);
        OverseasPaymentAdapter adapter = adapterMap.get(gatewayCode);
        if (adapter == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "无可用支付网关: " + gatewayCode);
        }

        OverseasPaymentGateway gw = jdbc.queryForObject(
            "SELECT * FROM biz_overseas_payment_gateway WHERE gateway_code = ? AND deleted = 0 AND status = 1",
            (rs, rn) -> {
                OverseasPaymentGateway g = new OverseasPaymentGateway();
                g.setId(rs.getLong("id"));
                g.setGatewayCode(rs.getString("gateway_code"));
                g.setGatewayName(rs.getString("gateway_name"));
                g.setPublishableKey(rs.getString("publishable_key"));
                g.setSecretKey(rs.getString("secret_key"));
                g.setWebhookSecret(rs.getString("webhook_secret"));
                g.setFeeRate(rs.getBigDecimal("fee_rate"));
                g.setFixedFee(rs.getBigDecimal("fixed_fee"));
                g.setStatus(rs.getInt("status"));
                return g;
            }, gatewayCode);

        CreatePaymentIntentRequest req = new CreatePaymentIntentRequest();
        req.orderNo = orderNo;
        req.amount = amount;
        req.currency = currency;
        req.customerEmail = memberEmail;
        req.description = "Beauty Service-" + orderNo;
        req.returnUrl = "/overseas-payment/success";
        req.cancelUrl = "/overseas-payment/cancel";

        CreatePaymentIntentResponse resp = adapter.createPaymentIntent(gw, req);
        if (!resp.success) {
            throw new BusinessException(BizCode.BAD_REQUEST, "支付网关创建失败: " + resp.errorMessage);
        }

        OverseasPayment payment = new OverseasPayment();
        payment.setTenantId(tenantId == null ? 1L : tenantId);
        payment.setOrderId(orderId);
        payment.setOrderType(orderType);
        payment.setOrderNo(orderNo);
        payment.setMemberId(memberId);
        payment.setMemberName(memberName);
        payment.setMemberEmail(memberEmail);
        payment.setGatewayCode(gatewayCode);
        payment.setGatewayIntentId(resp.intentId);
        payment.setGatewayPaymentId(resp.paymentIntentId);
        payment.setGatewayClientSecret(resp.clientSecret);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setStatus("PENDING");

        BigDecimal fee = amount.multiply(gw.getFeeRate() == null ? BigDecimal.valueOf(0.029) : gw.getFeeRate())
                .setScale(2, RoundingMode.HALF_UP);
        payment.setFee(fee);
        payment.setNetAmount(amount.subtract(fee));
        payment.setCreateTime(LocalDateTime.now());

        baseMapper.insert(payment);
        return payment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OverseasPayment refund(String paymentId, BigDecimal amount, String reason, Long tenantId) {
        OverseasPayment payment = baseMapper.selectOne(
            new LambdaQueryWrapper<OverseasPayment>()
                .eq(OverseasPayment::getGatewayPaymentId, paymentId)
                .or()
                .eq(OverseasPayment::getGatewayIntentId, paymentId));

        if (payment == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "支付记录不存在");
        }
        if (tenantId != null && payment.getTenantId() != null && !payment.getTenantId().equals(tenantId)) {
            throw new BusinessException(BizCode.FORBIDDEN, "无权操作该支付");
        }

        OverseasPaymentAdapter adapter = adapterMap.get(payment.getGatewayCode());
        if (adapter == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "无可用适配器: " + payment.getGatewayCode());
        }

        OverseasPaymentGateway gw = jdbc.queryForObject(
            "SELECT * FROM biz_overseas_payment_gateway WHERE gateway_code = ? AND deleted = 0",
            (rs, rn) -> {
                OverseasPaymentGateway g = new OverseasPaymentGateway();
                g.setSecretKey(rs.getString("secret_key"));
                g.setWebhookSecret(rs.getString("webhook_secret"));
                g.setFeeRate(rs.getBigDecimal("fee_rate"));
                return g;
            }, payment.getGatewayCode());

        RefundRequest req = new RefundRequest();
        req.paymentId = paymentId;
        req.amount = amount;
        req.reason = reason;

        RefundResponse resp = adapter.refund(gw, req);
        if (!resp.success) {
            throw new BusinessException(BizCode.BAD_REQUEST, "退款失败: " + resp.errorMessage);
        }

        payment.setStatus("REFUNDED");
        payment.setRefundTime(LocalDateTime.now());
        payment.setRefundAmount(amount);
        payment.setRefundReason(reason);
        payment.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(payment);

        return payment;
    }

    @Override
    public String selectBestGateway(String currency, String countryCode, String paymentMethod, BigDecimal amount, Long tenantId) {
        if (currency == null) currency = "USD";
        currency = currency.toUpperCase();
        if (paymentMethod == null) paymentMethod = "card";
        String pm = paymentMethod.toLowerCase();

        // 0. 钱包支付方式 → 直接路由到专用网关
        //    优先级最高，避免被 StripeAdapter/SquareAdapter 抢走
        if ("apple_pay".equals(pm) && adapterMap.containsKey("apple_pay")) {
            log.info("[OverseasPayment] selectBestGateway: paymentMethod=apple_pay -> apple_pay");
            return "apple_pay";
        }
        if ("google_pay".equals(pm) && adapterMap.containsKey("google_pay")) {
            log.info("[OverseasPayment] selectBestGateway: paymentMethod=google_pay -> google_pay");
            return "google_pay";
        }

        // 1. 信用卡支付 → 按货币支持集选 Stripe
        Set<String> stripeSupported = Set.of(
            "USD", "EUR", "GBP", "CAD", "AUD", "JPY", "SGD", "HKD",
            "CNY", "MYR", "THB", "PHP", "IDR", "VND", "KRW", "TWD",
            "NZD", "CHF", "SEK", "DKK", "NOK", "PLN", "CZK", "ILS"
        );

        if (stripeSupported.contains(currency)) {
            if (adapterMap.containsKey("stripe")) {
                return "stripe";
            }
        }

        // 2. 兜底 — 任意可用网关
        for (String code : adapterMap.keySet()) {
            if (adapterMap.get(code).enabled()) {
                log.info("[OverseasPayment] selectBestGateway: currency={} method={} -> {}",
                    currency, paymentMethod, code);
                return code;
            }
        }

        log.warn("[OverseasPayment] selectBestGateway: no available gateway for currency={} method={}",
            currency, paymentMethod);
        return null;
    }

    @Override
    public List<OverseasPayment> queryList(Long orderId, Long memberId, String gatewayCode,
                                           String status, LocalDateTime startTime, LocalDateTime endTime, Long tenantId) {
        LambdaQueryWrapper<OverseasPayment> q = new LambdaQueryWrapper<>();
        if (tenantId != null) q.eq(OverseasPayment::getTenantId, tenantId);
        if (orderId != null) q.eq(OverseasPayment::getOrderId, orderId);
        if (memberId != null) q.eq(OverseasPayment::getMemberId, memberId);
        if (gatewayCode != null && !gatewayCode.isEmpty()) q.eq(OverseasPayment::getGatewayCode, gatewayCode);
        if (status != null && !status.isEmpty()) q.eq(OverseasPayment::getStatus, status);
        if (startTime != null) q.ge(OverseasPayment::getCreateTime, startTime);
        if (endTime != null) q.le(OverseasPayment::getCreateTime, endTime);
        q.orderByDesc(OverseasPayment::getCreateTime);
        return baseMapper.selectList(q);
    }

    @Override
    public Map<String, Object> statistics(LocalDateTime startTime, LocalDateTime endTime, Long tenantId) {
        String where = "WHERE deleted = 0 ";
        List<Object> args = new ArrayList<>();
        if (tenantId != null) { where += "AND tenant_id = ? "; args.add(tenantId); }
        if (startTime != null) { where += "AND create_time >= ? "; args.add(startTime); }
        if (endTime != null) { where += "AND create_time <= ? "; args.add(endTime); }

        String sql = """
            SELECT
              COUNT(*) AS total_count,
              COALESCE(SUM(amount), 0) AS total_amount,
              COALESCE(SUM(fee), 0) AS total_fee,
              COALESCE(SUM(net_amount), 0) AS total_net,
              COALESCE(SUM(refund_amount), 0) AS total_refund,
              SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
              SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success_count,
              SUM(CASE WHEN status = 'REFUNDED' THEN 1 ELSE 0 END) AS refunded_count,
              SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count
            FROM biz_overseas_payment
            """ + where;

        Map<String, Object> row = jdbc.queryForMap(sql, args.toArray());

        String byGatewaySql = """
            SELECT gateway_code,
              COUNT(*) AS cnt,
              COALESCE(SUM(amount), 0) AS amount
            FROM biz_overseas_payment
            """ + where + """
            GROUP BY gateway_code ORDER BY amount DESC
            """;

        List<Map<String, Object>> byGateway = jdbc.queryForList(byGatewaySql, args.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", ((Number) row.get("total_count")).longValue());
        result.put("totalAmount", row.get("total_amount"));
        result.put("totalFee", row.get("total_fee"));
        result.put("totalNet", row.get("total_net"));
        result.put("totalRefund", row.get("total_refund"));
        result.put("pendingCount", ((Number) row.get("pending_count")).longValue());
        result.put("successCount", ((Number) row.get("success_count")).longValue());
        result.put("refundedCount", ((Number) row.get("refunded_count")).longValue());
        result.put("failedCount", ((Number) row.get("failed_count")).longValue());
        result.put("byGateway", byGateway);

        return result;
    }
}