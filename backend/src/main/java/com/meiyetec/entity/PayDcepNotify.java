package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pay_dcep_notify")
public class PayDcepNotify {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long dcepOrderId;
    private String notifyId;
    private String eventType;
    private String payload;
    private Integer signVerified;
    private String signAlgo;
    private Integer processed;
    private String processMsg;
    private LocalDateTime notifyAt;
    private LocalDateTime processAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}