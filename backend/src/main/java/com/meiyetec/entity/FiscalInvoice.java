package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票
 *
 * <p>FISCAL-001 扩展：增加百望/航天平台返回字段（platformInvoiceNo / platformPdfUrl / requestId / response）。
 */
@Data
@TableName("fiscal_invoice")
public class FiscalInvoice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String invoiceNo;
    /** GENERAL=普票 / SPECIAL=专票 */
    private String invoiceType;
    private String buyerName;
    private String buyerTaxNo;
    private String buyerAddress;
    private String buyerPhone;
    private String buyerEmail;
    private String buyerBank;
    private String buyerAccount;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal taxRate;
    /** ISSUED / VOIDED / FAILED / PENDING */
    private String invoiceStatus;
    private String invoiceUrl;
    private String pdfUrl;
    private String fiscalNo;
    private Long orderId;
    /** BAI_WANG / YIN_TIAN / DCEP / MOCK */
    private String channel;
    private String errorMessage;
    private LocalDateTime issuedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ============= FISCAL-001：平台响应字段 =============

    /** 百望/航天平台发票号（成功开具后写入） */
    private String platformInvoiceNo;
    /** 红冲号（红冲成功后写入） */
    private String platformVoidNo;
    /** PDF 下载 URL（成功开具后写入） */
    private String platformPdfUrl;
    /** 平台幂等请求流水号 */
    private String platformRequestId;
    /** 平台完整响应（JSON，便于客服排查） */
    private String platformResponse;
    /** 本系统请求载荷（JSON，可用于人工重放） */
    private String requestPayload;
    private LocalDateTime requestStartedAt;
    private LocalDateTime requestCompletedAt;
    /** BAI_WANG / YIN_TIAN / MOCK */
    private String requestMode;
    private Integer retryCount;
}
