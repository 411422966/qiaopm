package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-D：多仓管理 — 仓库主数据
 */
@Data
@TableName("wms_warehouse")
public class WmsWarehouse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    /** 归属门店（NULL=总部中央仓） */
    private Long storeId;
    private String warehouseCode;
    private String warehouseName;
    /** STORE/CENTRAL/TRANSIT */
    private String warehouseType;
    private String location;
    private Long managerId;
    private String managerName;
    private Integer enabled;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}