package com.meiyetec.dcep.impl;

import com.meiyetec.dcep.AbstractDcepAdapter;
import com.meiyetec.dcep.DcepChannelAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P2-13 DCEP 沙箱适配器
 */
@Slf4j
@Component("sandboxDcepAdapter")
public class SandboxDcepAdapter extends AbstractDcepAdapter implements DcepChannelAdapter {

    @Autowired
    private JdbcTemplate jdbc;

    private static final Map<String, SandboxOrder> SANDBOX_ORDERS = new ConcurrentHashMap<>();

    @Override
    public String channelCode() {
        return "DCEP-SANDBOX";
    }

    @Override
    public String toString() {
        return "DCEP-SANDBOX";
    }

    @Override
    public PrepayResult prepay(PrepayRequest req) {
        String orderNo = req.getDcepOrderNo();
        String prepayId = "SP" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);
        String expiresAtStr = expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String qrCode = generateQrPayload(prepayId, orderNo, req.getAmountCents());

        SANDBOX_ORDERS.put(orderNo, new SandboxOrder(orderNo, prepayId, transactionId(orderNo),
                req.getAmountCents(), 1, expiresAtStr));

        try {
            String sql = "INSERT INTO pay_dcep_order (dcep_order_no, prepay_id, transaction_id, amount_cents, "
                    + "status, merchant_id, store_id, notify_count, create_time) "
                    + "VALUES (?, ?, ?, ?, 1, ?, ?, 0, NOW()) "
                    + "ON DUPLICATE KEY UPDATE prepay_id = VALUES(prepay_id), transaction_id = VALUES(transaction_id)";
            jdbc.update(sql, orderNo, prepayId, SANDBOX_ORDERS.get(orderNo).transactionId,
                    req.getAmountCents(), req.getMerchantId(), req.getMerchantId());
        } catch (Exception e) {
            log.debug("[DCEP-SANDBOX] prepay db write skipped: {}", e.getMessage());
        }

        log.info("[DCEP-SANDBOX] prepay OK: order={} prepayId={} amount={} expires={}",
                orderNo, prepayId, req.getAmountCents(), expiresAtStr);

        return PrepayResult.builder()
                .success(true)
                .prepayId(prepayId)
                .qrCode(qrCode)
                .expiresAt(expiresAtStr)
                .build();
    }

    private String transactionId(String orderNo) {
        return "TXN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public QueryResult query(OrderQueryRequest req) {
        SandboxOrder order = SANDBOX_ORDERS.get(req.getDcepOrderNo());
        if (order == null) {
            try {
                String status = jdbc.queryForObject(
                    "SELECT status FROM pay_dcep_order WHERE dcep_order_no = ?",
                    String.class, req.getDcepOrderNo());
                if (status != null) {
                    Long amount = jdbc.queryForObject(
                            "SELECT amount_cents FROM pay_dcep_order WHERE dcep_order_no = ?",
                            Long.class, req.getDcepOrderNo());
                    String txnId = jdbc.queryForObject(
                            "SELECT transaction_id FROM pay_dcep_order WHERE dcep_order_no = ?",
                            String.class, req.getDcepOrderNo());
                    return QueryResult.builder()
                        .success(true)
                        .status(mapStatus(Integer.parseInt(status)))
                        .amountCents(amount != null ? amount : 0L)
                        .transactionId(txnId)
                        .build();
                }
            } catch (Exception ignored) {}
            return QueryResult.builder().success(false).errorCode("ORDER_NOT_FOUND").errorMsg("订单不存在").build();
        }

        return QueryResult.builder()
                .success(true)
                .status(mapStatus(order.status))
                .amountCents(order.amountCents)
                .transactionId(order.transactionId)
                .paidAt(order.status == 2 ? order.paidAt : null)
                .build();
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        SandboxOrder order = SANDBOX_ORDERS.get(req.getDcepOrderNo());
        if (order != null) {
            order.status = 4;
            order.refundedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        try {
            jdbc.update("UPDATE pay_dcep_order SET status = 4, refund_time = NOW() WHERE dcep_order_no = ?",
                    req.getDcepOrderNo());
        } catch (Exception ignored) {}

        String refundId = "RF" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[DCEP-SANDBOX] refund OK: order={} refundId={} amount={}",
                req.getDcepOrderNo(), refundId, req.getAmountCents());
        return com.meiyetec.dcep.DcepChannelAdapter.RefundResult.builder()
                .success(true)
                .refundId(refundId)
                .refundedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }

    @Override
    public CloseResult closeOrder(CloseRequest req) {
        SandboxOrder order = SANDBOX_ORDERS.get(req.getDcepOrderNo());
        if (order != null && order.status == 1) {
            order.status = 3;
        }
        try {
            jdbc.update("UPDATE pay_dcep_order SET status = 3 WHERE dcep_order_no = ?",
                    req.getDcepOrderNo());
        } catch (Exception ignored) {}
        return CloseResult.builder().success(true).build();
    }

    @Override
    public NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers) {
        return NotifyResult.builder()
                .signatureValid(true)
                .timestampValid(true)
                .eventType("PAY_SUCCESS")
                .rawBody(rawBody)
                .build();
    }

    private String mapStatus(int statusCode) {
        switch (statusCode) {
            case 1: return "PENDING";
            case 2: return "PAID";
            case 3: return "CLOSED";
            case 4: return "REFUNDED";
            case 5: return "FAILED";
            default: return "UNKNOWN";
        }
    }

    private String generateQrPayload(String prepayId, String orderNo, Long amountCents) {
        return "dcep://pay?prepay_id=" + prepayId + "&order=" + orderNo + "&amount=" + amountCents;
    }

    private static class SandboxOrder {
        final String orderNo;
        final String prepayId;
        final String transactionId;
        final Long amountCents;
        int status;
        final String expiresAt;
        String paidAt;
        String refundedAt;

        SandboxOrder(String orderNo, String prepayId, String transactionId, Long amountCents,
                     int status, String expiresAt) {
            this.orderNo = orderNo;
            this.prepayId = prepayId;
            this.transactionId = transactionId;
            this.amountCents = amountCents;
            this.status = status;
            this.expiresAt = expiresAt;
        }
    }
}
