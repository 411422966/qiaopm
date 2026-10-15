package com.meiyetec.cn.domestic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 国内支付业务返回包装（Controller 层 JSON DTO）
 *
 * @since 2026-09-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomesticPayResponse<T> {
    private boolean ok;
    private String channelCode;
    private String outOrderNo;
    private String errorCode;
    private String errorMessage;
    private T payload;
}
