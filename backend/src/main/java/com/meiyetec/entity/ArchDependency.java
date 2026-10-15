package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("arch_dependency")
public class ArchDependency {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fromService;
    private String toService;
    /** SYNC_CALL / ASYNC_EVENT / DB_SHARE / CACHE */
    private String dependencyType;
    /** HTTP / GRPC / MQ / DUBBO */
    private String protocol;
    /** LOW / NORMAL / HIGH / BLOCKER */
    private String criticality;
    private Integer qpsEstimate;
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}