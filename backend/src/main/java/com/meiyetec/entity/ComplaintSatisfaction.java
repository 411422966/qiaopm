package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("complaint_satisfaction")
public class ComplaintSatisfaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    /** 1-5 */
    private Integer rating;
    private String feedback;
    /** -100 ~ 100 */
    private Integer npsScore;
    private LocalDateTime ratedAt;
}