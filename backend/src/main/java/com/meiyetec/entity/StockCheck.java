package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * M08 库存盘点单实体
 */
@Data
@TableName("stock_check")
public class StockCheck {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String checkNo;
    private LocalDate checkDate;
    private Integer totalItems;
    private Integer diffItems;
    private Integer profitQty;
    private Integer lossQty;
    private Long operatorId;
    private String operatorName;
    /** 状态:1进行中 2已完成 3已作废 */
    private Integer status;
    private String remark;
    /** 确认人ID（已完成的盘点） */
    private Long confirmedBy;
    private String confirmedByName;
    private LocalDateTime confirmedTime;
    /** 主核销人ID（P2-11 IND-05 双人核销） */
    private Long mainVerifierId;
    private String mainVerifierName;
    /** 复核人ID（P2-11 IND-05） */
    private Long reviewerId;
    private String reviewerName;
    /** 复核人GPS JSON */
    private String reviewerGps;
    private LocalDateTime reviewTime;
    /** 双人核销状态：NOT_REQUIRED / PENDING / VERIFIED / REJECTED */
    private String dualVerifyStatus;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}