package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("crm_member")
public class Member {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String memberNo;
    private String name;
    private String phone;
    private Integer gender;
    private LocalDate birthday;
    private Long levelId;
    private Long storeId;
    private Long tenantId;
    private BigDecimal balance;
    private Integer points;
    private BigDecimal totalConsume;
    private String avatar;
    private String address;
    private String remark;
    private Integer status;
    private LocalDate joinDate;
    private LocalDateTime lastVisit;

    /** 乐观锁版本号（积分 CAS 关键字段；SSOT 收敛后 wallet 用 crm_wallet.version；此处保留兼容积分 CAS） */
    @TableField(fill = FieldFill.INSERT)
    private Integer version;

    @TableField(exist = false)
    private String levelName;

    @TableField(exist = false)
    private String storeName;

    @TableField(exist = false)
    private List<MemberTag> tags;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
