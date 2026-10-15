package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("arch_roadmap")
public class ArchRoadmap {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String milestoneCode;
    private String title;
    /** P0 / P1 / P2 / P3 */
    private String phase;
    private String description;
    /** JSON 交付物 */
    private String deliverables;
    private LocalDate targetDate;
    private LocalDate actualDate;
    /** PLANNED / IN_PROGRESS / DONE / DELAYED */
    private String status;
    private String owner;
    /** 0-100 */
    private Integer progress;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}