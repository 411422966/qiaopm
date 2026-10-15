package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("complaint_escalation")
public class ComplaintEscalation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private String fromAssignee;
    private String toAssignee;
    private String escalateReason;
    private LocalDateTime escalatedAt;
    private LocalDateTime acceptedAt;
}