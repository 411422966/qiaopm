package com.meiyetec.dto;

import lombok.Data;

/**
 * M04-04 套餐冻结 / 解冻 DTO
 *
 * <p>同一个 DTO 用于「冻结」和「解冻」两个动作：
 * <ul>
 *   <li>{@code freeze = true}：冻结（状态 1 → 4）</li>
 *   <li>{@code freeze = false}：解冻（状态 4 → 1）</li>
 * </ul>
 *
 * <p>前端旧接口 {@code ?freeze=true|false} 仍兼容（无 body 也能调用），
 * 但推荐使用本 DTO 显式传 {@code reason} 便于审计。
 */
@Data
public class PackageFreezeDTO {

    /** 是否冻结：true=冻结 false=解冻。兼容 ?freeze= 参数 */
    private Boolean freeze;

    /** 操作人 ID */
    private Long operatorId;

    /** 原因 / 备注（强烈建议填写，便于审计追溯） */
    private String reason;
}
