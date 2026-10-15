package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * M04-04 套餐状态变更审计日志
 *
 * <p>记录冻结/解冻/挂起/恢复 4 类操作，每次状态切换都会追加一条记录。
 *
 * <p>action 枚举：
 * <ul>
 *   <li>{@code FREEZE} — 冻结（状态 1 → 4）</li>
 *   <li>{@code UNFREEZE} — 解冻（状态 4 → 1）</li>
 *   <li>{@code SUSPEND} — 挂起（状态 1 → 8）</li>
 *   <li>{@code RESUME} — 恢复挂起（状态 8 → 1）</li>
 * </ul>
 *
 * <p>查询示例：追溯任意套餐订单的全部状态变更轨迹
 * <pre>{@code
 *   SELECT * FROM crm_package_status_log
 *   WHERE order_id = ? ORDER BY id DESC
 * }</pre>
 */
@Data
@TableName("crm_package_status_log")
public class PackageStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long orderId;
    private String orderNo;
    private Long memberId;
    private Long packageId;

    /** 动作：FREEZE / UNFREEZE / SUSPEND / RESUME */
    private String action;

    /** 变更前状态（可能为 NULL） */
    private Integer beforeStatus;

    /** 变更后状态 */
    private Integer afterStatus;

    /** 原因 / 备注 */
    private String reason;

    /** 挂起到期时间（NULL 表示永久挂起 / 人工恢复；SUSPEND 时记录，RESUME 时清空） */
    private LocalDateTime suspendUntil;

    private Long operatorId;
    private String operatorName;
    private String clientIp;

    @TableField(exist = false)
    private String afterStatusLabel;   // 查询历史时用于前端展示（非数据库字段）

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public void setAfterStatusLabel(String afterStatusLabel) {
        this.afterStatusLabel = afterStatusLabel;
    }

    public String getAfterStatusLabel() {
        return afterStatusLabel;
    }
}
