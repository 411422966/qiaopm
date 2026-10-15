package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("gift_card_transaction")
public class GiftCardTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long cardId;
    private String txType;
    private BigDecimal amount;
    private BigDecimal balance;
    private Long orderId;
    private Long operatorId;
    private String notes;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}