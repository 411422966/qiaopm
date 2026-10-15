package com.meiyetec.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmployeeDTO {
    private Long id;
    
    @NotBlank(message = "员工姓名不能为空")
    private String employeeName;
    
    private String employeeCode;
    
    @NotBlank(message = "手机号不能为空")
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
}
