package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("payment_channel")
public class PaymentChannel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String channelCode;
    private String channelName;
    /** THIRD_PARTY / BANK / INTERNAL */
    private String channelType;
    private String iconUrl;
    private Integer enabled;
    private Integer sortOrder;
    private String description;
}