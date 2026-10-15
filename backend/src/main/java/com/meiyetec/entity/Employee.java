package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("pos_employee")
public class Employee {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String employeeCode;
    private String employeeName;
    private String phone;
    private String password;
    private Integer gender;
    private String idCard;
    private String avatarUrl;
    private String position;
    private String department;
    private Long storeId;
    private LocalDate entryDate;
    private Integer status;
    private BigDecimal salary;
    private BigDecimal commissionRate;
    private String roleIds;
    private Long tenantId;
    /** M08 技师等级ID（关联 biz_technician_level.id）；null = 实习/未定级 */
    private Long employeeLevel;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;
    
    @TableField(exist = false)
    private String storeName;
}
