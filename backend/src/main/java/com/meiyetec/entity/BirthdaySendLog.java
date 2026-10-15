package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("birthday_send_log")
public class BirthdaySendLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    private Integer sendYear;
    private String sendType;
    private String giftType;
    private String giftValue;
    private String couponCode;
    private Integer pointsAwarded;
    private String templateId;
    private String message;
    private String channelMessageId;
    private String status;
    private String errorMessage;
    private LocalDateTime sentAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}