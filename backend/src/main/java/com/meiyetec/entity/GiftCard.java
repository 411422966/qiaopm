package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("gift_card")
public class GiftCard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String cardNo;
    private String cardType;
    private BigDecimal faceValue;
    private BigDecimal balance;
    private Long serviceId;
    private Integer serviceCount;
    private Long buyerId;
    private String buyerName;
    private String recipientName;
    private String recipientPhone;
    private String giftMessage;
    private String theme;
    private String coverImage;
    private Long purchaseOrderId;
    private LocalDateTime purchaseTime;
    private LocalDateTime expireTime;
    private String status;
    private LocalDateTime useTime;
    private Long useOrderId;
    private Long useStoreId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}