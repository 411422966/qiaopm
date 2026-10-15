package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M08 出入库记录实体
 */
@Data
@TableName("stock_record")
public class StockRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long productId;
    private String batchNo;
    /** 类型:1入库 2出库 3盘点 4领用 5报损 6采购入库 */
    private Integer type;
    private Integer quantity;
    private Integer beforeQty;
    private Integer afterQty;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String supplier;
    private Long orderId;
    private Long operatorId;
    private String operatorName;
    private String remark;
    /** 双人核销状态: NOT_REQUIRED / PENDING / VERIFIED / REJECTED */
    private String dualVerifyStatus;
    private LocalDateTime recordTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}