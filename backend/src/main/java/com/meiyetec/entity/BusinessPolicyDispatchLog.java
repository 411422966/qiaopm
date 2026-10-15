package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * M12-05 业务策略下发执行日志（按门店维度）
 *
 * <p>每条 mkt_dispatch 在每个目标门店会写一行，状态机：
 * <pre>PENDING → SUCCESS / FAILED / SKIPPED</pre>
 */
@Data
@TableName("m12_dispatch_log")
public class BusinessPolicyDispatchLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long dispatchId;
    private Long storeId;
    private String storeName;
    private String dispatchType;
    /** PRICING_RULE / ACTIVITY / COUPON_BATCH / EXPERIMENT */
    private String dispatchSubtype;
    private Long sourceId;
    private String sourceVersion;
    /** PENDING / SUCCESS / FAILED / SKIPPED */
    private String status;
    private Integer appliedCount;
    private String errorMsg;
    private String errorCode;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime dispatchStartedAt;
    private LocalDateTime dispatchFinishedAt;
    private Long durationMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
