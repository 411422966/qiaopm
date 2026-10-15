package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("brand")
public class Brand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String brandCode;
    private String brandName;
    private String logoUrl;
    private String description;
    private String contactPhone;
    private String contactEmail;
    private String website;
    private String address;
    private String businessLicense;
    private String legalPerson;
    private LocalDate registeredAt;
    /** ACTIVE / SUSPENDED / CLOSED */
    private String status;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}