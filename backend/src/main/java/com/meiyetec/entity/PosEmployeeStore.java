package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("pos_employee_store")
public class PosEmployeeStore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long employeeId;
    private Long storeId;
    private Long brandId;
    private Long tenantId;
    /** 店长 / 副店长 / 技师 / 顾问 / 前台 / STAFF */
    private String roleInStore;
    /** 是否主店 */
    private Integer isPrimary;
    /** 1在职 0离职 */
    private Integer status;
    private LocalDate joinDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
