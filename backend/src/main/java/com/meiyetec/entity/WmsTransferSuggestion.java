package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wms_transfer_suggestion")
public class WmsTransferSuggestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long productId;
    private String productName;
    private Long fromWarehouseId;
    private String fromWarehouseName;
    private Long toWarehouseId;
    private String toWarehouseName;
    private Integer suggestedQty;
    /** HIGH / MEDIUM / LOW */
    private String priority;
    private String reason;
    private String algorithmVersion;
    /** 是否被运营采纳 */
    private Integer accepted;
    private LocalDateTime acceptedAt;
    private String acceptedBy;
    private Long resultTransferId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}