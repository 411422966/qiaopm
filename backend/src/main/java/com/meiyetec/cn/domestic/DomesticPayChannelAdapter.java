package com.meiyetec.cn.domestic;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 国内支付渠道适配器契约（EXT-P0-1 微信/支付宝/银联云闪付）
 *
 * <p>设计原则：
 * <ul>
 *   <li>三条通道（{@code WECHAT_PAY} / {@code ALIPAY} / {@code UNION_PAY}）都实现本接口</li>
 *   <li>不与现有 dcep / payment(bnpl) 命名空间耦合（避免插件沙箱扫描误判）</li>
 *   <li>同步接口优先：所有写操作幂等（同一 outOrderNo 多次调用只生效一次）</li>
 *   <li>异步通知解析 + 验签：{@link #parseAndVerifyNotify} 返回结构化结果</li>
 *   <li>统一金额单位：分（{@code Long}），避免浮点误差</li>
 * </ul>
 *
 * <p>实现路径：{@code com.meiyetec.cn.domestic.adapter.*}（一个通道一个文件）
 * <p>路由入口：{@link com.meiyetec.cn.domestic.DomesticPayChannelRouter}
 *
 * @see com.meiyetec.dcep.DcepChannelAdapter  DCEP 通道（参考风格）
 * @since 2026-09-07
 */
public interface DomesticPayChannelAdapter {

    /**
     * 1. 统一下单（预支付）
     *
     * <p>调用第三方支付接口，返回 prepayId / qrCode / h5Url，客户端用对应 App 完成支付。
     */
    PrepayResult prepay(PrepayRequest req);

    /**
     * 2. 主动查询订单状态（兜底异步通知）
     */
    QueryResult query(OrderQueryRequest req);

    /**
     * 3. 退款（同步；幂等）
     */
    RefundResult refund(RefundRequest req);

    /**
     * 4. 关闭订单（未支付超时关闭）
     */
    CloseResult closeOrder(CloseRequest req);

    /**
     * 5. 异步通知解析 + 验签
     *
     * <p>第三方支付回调到 {@code /domestic/webhook/{channel}} 时调用。
     * <p>返回的 {@link NotifyResult} 包含验签结果 + 事件类型 + 订单信息。
     */
    NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers);

    /**
     * 渠道编码（{@code WECHAT_PAY} / {@code ALIPAY} / {@code UNION_PAY}）
     */
    String channelCode();

    /**
     * 渠道名称（用于日志/前端展示）
     */
    default String channelName() {
        return switch (channelCode()) {
            case "WECHAT_PAY" -> "微信支付";
            case "ALIPAY" -> "支付宝";
            case "UNION_PAY" -> "银联云闪付";
            default -> channelCode();
        };
    }

    /**
     * 运行时状态：active（生产）/ sandbox（沙箱）/ disabled（未启用）
     */
    default String status() {
        return "active";
    }

    // ====================================================================
    //  DTO 定义
    // ====================================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    class PrepayRequest {
        /** 我方系统订单号（幂等键） */
        private String outOrderNo;
        /** 业务订单号（pos_order_no 等） */
        private String bizOrderNo;
        /** 金额（分） */
        private Long amountCents;
        /** 商品名（≤ 64 字符） */
        private String goodsName;
        /** 用户标识（openId / buyerId，可选） */
        private String payerId;
        /** 异步通知地址（必须公网 HTTPS） */
        private String notifyUrl;
        /** 同步跳转地址（H5/PC 必填） */
        private String returnUrl;
        /** 订单过期时间（ISO 8601） */
        private String expireAt;
        /** 沙箱 / 生产 */
        private String envMode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    class PrepayResult {
        private boolean success;
        private String errorCode;
        private String errorMsg;
        /** 预支付 ID（微信：prepay_id；支付宝：precreate_id） */
        private String prepayId;
        /** 二维码（扫码付场景） */
        private String qrCode;
        /** H5 收银台 URL（H5/PC 场景） */
        private String h5Url;
        /** JSAPI 调起参数（公众号 / 小程序内部场景） */
        private Map<String, String> jsapiPayload;
        /** 过期时间 */
        private String expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    class OrderQueryRequest {
        private String outOrderNo;
        private String envMode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    class QueryResult {
        private boolean success;
        private String errorCode;
        private String errorMsg;
        /** 状态：WAIT_PAY / SUCCESS / CLOSED / REFUNDED / FAILED */
        private String status;
        private Long amountCents;
        /** 第三方交易流水号 */
        private String transactionId;
        private String paidAt;
        private String closedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    class RefundRequest {
        private String outOrderNo;
        /** 我方退款单号（幂等） */
        private String refundOrderNo;
        private Long amountCents;
        private String reason;
        private String notifyUrl;
        private String envMode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    class RefundResult {
        private boolean success;
        private String errorCode;
        private String errorMsg;
        private String refundId;
        private String refundedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    class CloseRequest {
        private String outOrderNo;
        private String envMode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    class CloseResult {
        private boolean success;
        private String errorCode;
        private String errorMsg;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    class NotifyResult {
        private boolean signatureValid;
        /** 验签失败时也允许记录 payload，但 signatureValid=false 阻止业务处理 */
        private String eventType;       // PAY_SUCCESS / REFUND_SUCCESS / CLOSE
        private String notifyId;        // 幂等键
        private String outOrderNo;      // 我方订单号
        private String transactionId;
        private Long amountCents;
        private String rawBody;
        private boolean replayDetected;
        /** 错误描述（验签失败 / 解析失败时填） */
        private String errorMsg;

        public NotifyResult withReplayDetected(boolean replay) {
            this.replayDetected = replay;
            return this;
        }
    }
}
