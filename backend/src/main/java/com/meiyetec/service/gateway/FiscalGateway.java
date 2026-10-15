package com.meiyetec.service.gateway;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FISCAL-001：开票/红冲网关抽象
 *
 * <p>屏蔽百望/航天/Mock 实现差异；运行时由 Spring 根据 {@code fiscal.mode} 注入。
 */
public interface FiscalGateway {

    /**
     * 真实开具发票
     *
     * @param req 开票请求
     * @return 开票结果（成功/失败 + 平台发票号/PDF URL）
     */
    IssueResult issue(IssueRequest req);

    /**
     * 红冲 / 作废（百望 redFlush / 航天 invoiceCancel）
     */
    VoidResult voidInvoice(VoidRequest req);

    /**
     * 实现标识：BAI_WANG / YIN_TIAN / MOCK
     */
    String mode();

    // ============= DTO =============

    /**
     * 开票请求
     *
     * <p>关键字段命名遵循百望接口约定，方便 JSON 序列化后直接对接。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class IssueRequest {
        /** 业务订单号（用于幂等关联） */
        private String orderNo;
        /** 销方税号（来自 fiscal_config） */
        private String sellerTaxNo;
        private String sellerName;
        /** 购方信息 */
        private String buyerName;
        private String buyerTaxNo;
        private String buyerAddress;
        private String buyerPhone;
        private String buyerEmail;
        private String buyerBank;
        private String buyerAccount;
        /** 普票 GENERAL / 专票 SPECIAL */
        private String invoiceType;
        /** 销方应用 appKey（来自 fiscal_config） */
        private String appKey;
        /** 销方应用 appSecret（用于签名，不入日志） */
        private String appSecret;
        /** 商品明细（百望 goodsList / 航天 itemList） */
        private List<LineItem> items;
        /** 备注 */
        private String remark;
        /** 操作员 */
        private String operatorName;
        /** 客户端幂等键（不传则服务端生成） */
        private String requestId;

        /**
         * 转成百望接口载荷（简化版，真实生产应按字段顺序拼装 + 签名）
         */
        public Map<String, Object> toPlatformPayload() {
            Map<String, Object> m = new HashMap<>();
            m.put("orderNo", orderNo);
            m.put("sellerTaxNo", sellerTaxNo);
            m.put("sellerName", sellerName);
            m.put("buyerName", buyerName);
            m.put("buyerTaxNo", buyerTaxNo);
            m.put("buyerAddress", buyerAddress);
            m.put("buyerPhone", buyerPhone);
            m.put("buyerEmail", buyerEmail);
            m.put("buyerBankAccountName", buyerBank);
            m.put("buyerBankAccount", buyerAccount);
            m.put("invoiceTypeCode", "GENERAL".equalsIgnoreCase(invoiceType) ? "01" : "02");
            m.put("appKey", appKey);
            m.put("requestId", requestId);
            m.put("remark", remark);
            // 商品明细转百望字段
            if (items != null) {
                List<Map<String, Object>> goodsList = new java.util.ArrayList<>();
                for (LineItem li : items) {
                    Map<String, Object> g = new HashMap<>();
                    g.put("goodsName", li.getName());
                    g.put("goodsCode", li.getCode());
                    g.put("goodsQuantity", li.getQuantity());
                    g.put("goodsPrice", li.getUnitPrice());
                    g.put("goodsTaxRate", li.getTaxRate());
                    g.put("goodsUnit", li.getUnit());
                    goodsList.add(g);
                }
                m.put("goodsList", goodsList);
            }
            return m;
        }
    }

    /**
     * 商品行项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class LineItem {
        private String name;
        private String code;
        private BigDecimal unitPrice;
        private BigDecimal quantity;
        /** 税率 0.06 / 0.13 / 0.01 等 */
        private BigDecimal taxRate;
        private String unit;
    }

    /**
     * 开票结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class IssueResult {
        private boolean success;
        /** 失败码：ISSUER_BUSY / SIGN_INVALID / TAX_NO_INVALID / TIMEOUT */
        private String errorCode;
        private String errorMessage;
        /** 平台发票号（成功时必有） */
        private String platformInvoiceNo;
        /** PDF URL（成功时必有） */
        private String pdfUrl;
        /** 红冲时使用（仅 BAI_WANG 状态已开） */
        private String platformVoidNo;
        /** 平台响应原文（JSON） */
        private String rawResponse;

        public static IssueResult ok(String invNo, String pdfUrl, String rawResp) {
            return new IssueResult(true, null, null, invNo, pdfUrl, null, rawResp);
        }

        public static IssueResult fail(String code, String msg, String rawResp) {
            return new IssueResult(false, code, msg, null, null, null, rawResp);
        }
    }

    /**
     * 红冲请求
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class VoidRequest {
        /** 本系统发票 id */
        private Long invoiceId;
        /** 平台发票号 */
        private String platformInvoiceNo;
        /** 红冲原因 */
        private String reason;
        /** 红冲票号（可空，平台自动生成） */
        private String voidInvoiceNo;
        /** 客户端幂等键 */
        private String requestId;
        /** appKey/Secret 透传 */
        private String appKey;
        private String appSecret;
    }

    /**
     * 红冲结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class VoidResult {
        private boolean success;
        private String errorCode;
        private String errorMessage;
        /** 平台红冲号 */
        private String platformVoidNo;
        private String rawResponse;

        public static VoidResult ok(String voidNo, String rawResp) {
            return new VoidResult(true, null, null, voidNo, rawResp);
        }

        public static VoidResult fail(String code, String msg, String rawResp) {
            return new VoidResult(false, code, msg, null, rawResp);
        }
    }
}
