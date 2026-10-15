package com.meiyetec.dcep.operator;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * DCEP-03/04 阶段 B：运营机构通用响应
 */
@Data
@Builder
public class OperatorResponse {
    private boolean success;
    private String errorCode;
    private String errorMsg;
    private int httpStatus;          // HTTP 状态码（REST 场景）
    private Map<String, Object> data; // 响应字段（银行侧 JSON 已解析为 Map）
    private String rawBody;          // 原始 body（用于审计 / 排查）
}
