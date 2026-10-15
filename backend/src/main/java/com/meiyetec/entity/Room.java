package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * M06 房间实体
 */
@Data
@TableName("biz_room")
public class Room {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String roomName;
    private String roomType;
    private Integer capacity;
    private String equipment;
    /** 1可用 2维修 3停用 */
    private Integer status;
    private Integer sortNo;
    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}