package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pos_transaction")
public class PosTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long posId;
    private Long storeId;
    private String txNo;
    private String vendor;
    private String externalTxId;
    private BigDecimal amount;
    private BigDecimal tip;
    private BigDecimal tax;
    private String currency;
    private String cardBrand;
    private String cardLast4;
    private String status;
    private Long orderId;
    private Long memberId;
    private String rawPayload;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}