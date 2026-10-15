package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("member_day_execution")
public class MemberDayExecution {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long configId;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    private String triggerReason;
    private LocalDate triggerDate;
    private String channel;
    private BigDecimal discountAmount;
    private BigDecimal giftAmount;
    private Integer giftPoints;
    private String couponCode;
    private String sendStatus;
    private LocalDateTime sentAt;
    private LocalDateTime usedAt;
    private Long orderId;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}