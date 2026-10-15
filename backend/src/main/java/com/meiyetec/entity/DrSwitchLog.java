package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dr_switch_log")
public class DrSwitchLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fromSiteId;
    private Long toSiteId;
    /** PLANNED / EMERGENCY / DRILL */
    private String switchType;
    private String triggeredBy;
    private String reason;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    /** IN_PROGRESS / SUCCESS / FAILED / ROLLED_BACK */
    private String status;
    private Integer dataLossSeconds;
    private Integer downtimeSeconds;
    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}