package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("arch_decision")
public class ArchDecision {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** ADR-0001 */
    private String adrNo;
    private String title;
    /** 背景 / 问题 */
    private String context;
    private String decision;
    private String consequences;
    /** PROPOSED / ACCEPTED / DEPRECATED / SUPERSEDED */
    private String status;
    private String author;
    private LocalDate decisionDate;
    private String supersededBy;
    /** JSON 标签 */
    private String tags;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}