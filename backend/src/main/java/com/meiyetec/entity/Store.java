package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("pos_store")
public class Store {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String storeType;
    private String regionCode;
    private Integer isHq;
    private Long managerEmployeeId;
    private String managerName;
    private LocalDate openDate;
    private Integer brandPriority;
    private String storeName;
    private String storeCode;
    private String contactPerson;
    private String contactPhone;
    private String address;
    private String province;
    private String city;
    private String district;
    private String businessHours;
    private Integer status;
    private String logoUrl;
    private String description;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;
}
