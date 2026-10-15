package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-F P1-3：下发目标门店
 */
@Data
@TableName("mkt_dispatch_target")
public class MktDispatchTarget {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long dispatchId;
    private Long storeId;
    private String storeName;
    /** PENDING/ACCEPTED/REJECTED/EXECUTED */
    private String status;
    private LocalDateTime acceptedAt;
    private String rejectedReason;
    private LocalDateTime executedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}