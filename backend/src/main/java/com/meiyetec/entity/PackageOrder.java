package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("crm_package_order")
public class PackageOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String orderNo;
    private Long memberId;
    /** 购买门店ID（用于跨店核销判断）— R3.0-B 补丁 */
    private Long storeId;
    private Long packageId;
    private String packageName;
    private Integer totalTimes;
    private Integer remainingTimes;
    private BigDecimal price;
    private String payMethod;
    private Long operatorId;
    private LocalDateTime buyTime;
    private LocalDateTime expireTime;
    private Integer status;
    private String remark;

    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime updateTime;

    /** 乐观锁版本号（PACKAGE_REMAINING_TIMES_CAS 关键字段） */
    private Integer version;

    /** 会员姓名（不存库） */
    @TableField(exist = false)
    private String memberName;

    /** 会员手机号（不存库） */
    @TableField(exist = false)
    private String memberPhone;

    /** 状态描述（不存库） */
    @TableField(exist = false)
    private String statusLabel;

    /** 是否启用共享（不存库，由 R3.0-E P2 扩展；SQL 字段 shared_enabled） */
    @TableField(exist = false)
    private Integer sharedEnabled;

    /** 共享成员上限（不存库，对应 SQL 字段 max_share_members） */
    @TableField(exist = false)
    private Integer maxShareMembers;

    /** 已邀请人数（不存库，对应 SQL 字段 shared_count） */
    @TableField(exist = false)
    private Integer sharedCount;
}
