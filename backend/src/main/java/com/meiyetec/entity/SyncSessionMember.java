package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sync_session_member")
public class SyncSessionMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionKey;
    private String deviceId;
    private String platformCode;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private Integer active;
}