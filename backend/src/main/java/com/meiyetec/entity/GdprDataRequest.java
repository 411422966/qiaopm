package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("gdpr_data_request")
public class GdprDataRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String requestNo;
    private Long memberId;
    private String requestType;
    private String status;
    private String exportUrl;
    private LocalDateTime exportExpireAt;
    private String reason;
    private Long processorId;
    private LocalDateTime processedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}