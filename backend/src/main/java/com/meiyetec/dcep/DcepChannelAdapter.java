package com.meiyetec.dcep;

import lombok.Builder;
import lombok.Data;

/**
 * P0-MONEY-001 后续 · DCEP 银行 SDK 适配器规范
 *
 * <p>所有 DCEP 银行（建行/工行/农行 + 聚合支付服务商）都实现这 5 个能力。
 * 实现见 {@code com.meiyetec.dcep.impl.*}。
 *
 * <p>设计原则：
 * <ul>
 *   <li>预支付/查询/退款/关闭必须是**同步接口**（HTTP 调用银行侧）</li>
 *   <li>异步通知的解析must 校验签名</li>
 *   <li>幂等：所有写操作要支持同一 orderId 多次调用只生效一次</li>
 *   <li>不可变 DTO：避免银行回调过程中状态被改</li>
 * </ul>
 */
public interface DcepChannelAdapter {

    /**
     * 1. 预支付（统一下单）
     *
     * <p>调用银行侧 prepay 接口，获取 prepayId / qrCode / h5Url。
     * 用户用数字人民币 APP 扫码或点击确认后，银行会异步回调到 notify_url。
     */
    PrepayResult prepay(PrepayRequest req);

    /**
     * 2. 主动查询订单状态
     *
     * <p>在异步通知可能延迟或丢失的场景，必须能主动拉取真实状态。
     * <p>建议对每个订单 5/15/60 分钟各查一次。
     */
    QueryResult query(OrderQueryRequest req);

    /**
     * 3. 退款
     *
     * <p>仅已支付订单可退款。必须原子操作：调用成功才能改 pay_dcep_order.status=4。
     * <p>支持部分退款（amountCents < 原订单金额）。
     */
    RefundResult refund(RefundRequest req);

    /**
     * 4. 关闭订单
     *
     * <p>未支付订单超时或主动取消。调用成功才能改 status=3。
     */
    CloseResult closeOrder(CloseRequest req);

    /**
     * 5. 异步通知验签 + 解析
     *
     * <p>银行回调到 /pay/dcep/notify 时调用此方法。
     * <p>返回的 NotifyResult 中包含验签结果 + 事件类型 + 订单信息。
     */
    NotifyResult parseAndVerifyNotify(String rawBody, java.util.Map<String, String> headers);

    /**
     * 适配器标识（与 pay_dcep_channel.channelCode 映射）
     */
    String channelCode();

    // ====================================================================
    // DTO 定义
    // ====================================================================

    @Data
    @Builder
    class PrepayRequest {
        private String merchantId;
        private String appId;
        private String merchantPrivateKey;  // PEM 字符串
        private String bankPublicKey;       // PEM 字符串
        private String channelCode;         // DCEP-CCB / DCEP-ICBC / DCEP-ABC
        private String dcepOrderNo;         // 我方订单号
        private String bizOrderNo;          // 业务订单号
        private Long amountCents;           // 金额（分）
        private String goodsName;           // 商品名（如"美业服务"）
        private String payerPhone;          // 用户手机号（可选）
        private String notifyUrl;           // 回调地址
        private String expireAt;            // 订单过期时间 ISO 8601
        private String walletType;          // PERSONAL / MERCHANT
        private String envMode;             // SANDBOX / PROD
    }

    @Data
    @Builder
    class PrepayResult {
        private boolean success;
        private String errorCode;
        private String errorMsg;
        private String prepayId;     // 预支付 ID
        private String qrCode;       // 二维码（base64 / URL）
        private String h5Url;        // H5 收银台 URL
        private String expiresAt;    // 过期时间
    }

    @Data
    @Builder
    class OrderQueryRequest {
        private String merchantId;
        private String dcepOrderNo;
        private String envMode;
    }

    @Data
    @Builder
    class QueryResult {
        private boolean success;
        private String errorCode;
        private String errorMsg;
        private String status;       // 待支付/已支付/已关闭/已退款/失败
        private Long amountCents;
        private String transactionId;
        private String paidAt;
        private String closedAt;
        private String refundedAt;
    }

    @Data
    @Builder
    class RefundRequest {
        private String merchantId;
        private String dcepOrderNo;
        private String refundOrderNo;     // 退款单号（我方生成）
        private Long amountCents;         // 退款金额（分）
        private String reason;            // 退款原因
        private String notifyUrl;
        private String envMode;
    }

    @Data
    @Builder
    class RefundResult {
        private boolean success;
        private String errorCode;
        private String errorMsg;
        private String refundId;          // 银行退款单号
        private String refundedAt;
    }

    @Data
    @Builder
    class CloseRequest {
        private String merchantId;
        private String dcepOrderNo;
        private String envMode;
    }

    @Data
    @Builder
    class CloseResult {
        private boolean success;
        private String errorCode;
        private String errorMsg;
    }

    @Data
    @Builder
    class NotifyResult {
        private boolean signatureValid;
        private boolean timestampValid;
        private String eventType;          // PAY_SUCCESS / REFUND_SUCCESS / CLOSE
        private String notifyId;
        private String dcepOrderNo;
        private String transactionId;
        private Long amountCents;
        private String rawBody;            // 原始 body（用于存档）
        /** DCEP 阶段 B：是否检测到重放（同一 notifyId 二次推送） */
        private boolean replayDetected;

        public NotifyResult withReplayDetected(boolean replay) {
            this.replayDetected = replay;
            return this;
        }
    }
}
