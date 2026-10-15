package com.meiyetec.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 支付请求 DTO
 * <p>
 * 支持两种模式：
 * 1) 简单单笔：method + amount
 * 2) 混合支付：payments 列表，每笔一个 method + amount（如：储值100 + 微信100）
 * <p>
 * 支持的 method: cash/wallet/wechat/alipay/unionpay/package/e_cny_soft/e_cny_hard
 * package = 次卡核销（amount 字段填 0，仅扣次数）
 *
 * <p><b>M02-04 找零（金额敏感，服务端权威计算）</b>
 * <ul>
 *   <li>现金（method=cash）必须传 tenderedAmount（实收金额），由服务端独立计算 changeAmount</li>
 *   <li>非现金 method 一律禁止传 tenderedAmount（防御性，前端若误传会被服务端拒绝）</li>
 *   <li>前端不得自行计算找零金额后传给服务端 — 服务端会用实收与应付的差额权威计算</li>
 * </ul>
 */
@Data
public class PosPayDTO {
    /** 单笔模式：支付方式 */
    private String method;
    /** 单笔模式：金额 */
    private BigDecimal amount;
    /**
     * 现金实收金额（method=cash 时<b>必填</b>，tenderedAmount ≥ amount，差值为找零）
     * 非 cash 时<b>禁止</b>传值（服务端会拒绝）
     */
    private BigDecimal tenderedAmount;
    /** 通用：第三方流水号（微信/支付宝/数字人民币） */
    private String transactionId;

    /** 混合支付模式：多笔支付明细 */
    private List<PaymentItem> payments;

    @Data
    public static class PaymentItem {
        /** 支付方式 */
        private String method;
        /** 金额（次卡可填 0） */
        private BigDecimal amount;
        /**
         * 现金实收金额（method=cash 时<b>必填</b>，tenderedAmount ≥ amount）
         * 非 cash 时<b>禁止</b>传值（服务端会拒绝）
         */
        private BigDecimal tenderedAmount;
        /** 次卡核销专用：会员次卡ID */
        private Long memberCardId;
        /** 数字人民币专用：子钱包类型 soft/hard */
        private String eCnyType;
        /** 第三方流水号 */
        private String transactionId;
    }

    /**
     * B-2 · 套餐核销幂等 payload。
     * 参与 request_hash 计算，确保相同 orderId+packageOrderId+times 才算同一请求。
     */
    @Data
    public static class PackageRedeemPayload {
        private Long orderId;
        private Long packageOrderId;
        private Integer times;

        public PackageRedeemPayload() {}
        public PackageRedeemPayload(Long orderId, Long packageOrderId, Integer times) {
            this.orderId = orderId;
            this.packageOrderId = packageOrderId;
            this.times = times;
        }
    }
}