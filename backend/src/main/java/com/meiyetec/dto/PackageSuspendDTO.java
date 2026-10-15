package com.meiyetec.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * M04-04 套餐挂起 / 恢复 DTO
 *
 * <p>同一 DTO 兼用于「挂起」和「恢复」两个动作：
 * <ul>
 *   <li>{@code action = "SUSPEND"}：{@code suspendUntil} 可选（NULL = 永久挂起 / 人工恢复）；
 *       {@code reason} 必填（至少说明挂起原因）</li>
 *   <li>{@code action = "RESUME"}：参数被忽略，全部以恢复动作处理</li>
 * </ul>
 */
@Data
public class PackageSuspendDTO {

    /** 动作：SUSPEND / RESUME */
    private String action;

    /** 挂起原因（SUSPEND 必填，RESUME 可选） */
    private String reason;

    /**
     * 挂起到期时间（NULL = 永久挂起，需人工恢复）
     * <p>恢复后由后端统一清空该字段（保留在审计日志里）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime suspendUntil;

    /** 操作人 ID */
    private Long operatorId;
}
