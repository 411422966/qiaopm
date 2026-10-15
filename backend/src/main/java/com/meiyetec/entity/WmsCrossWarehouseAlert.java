package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wms_cross_warehouse_alert")
public class WmsCrossWarehouseAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long productId;
    private String productName;
    private Long fromWarehouseId;
    private String fromWarehouseName;
    private Integer fromQuantity;
    private Long toWarehouseId;
    private String toWarehouseName;
    private Integer toQuantity;
    /** IMBALANCE / OVERLOAD / UNDERLOAD / EXPIRY / TRANSFER_GAP */
    private String alertType;
    /** HIGH / MEDIUM / LOW */
    private String severity;
    private Integer recommendedQty;
    private String alertReason;
    private String suggestedAction;
    /** 1=待处理 / 2=已生成调拨单 / 3=已忽略 / 4=已解决 */
    private Integer status;
    private Long relatedTransferId;
    private String operatorName;
    private String handleRemark;
    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}