package com.meiyetec.dto;

import lombok.Data;

import java.util.List;

/**
 * I211: batch request body. Replaces Map<String,Object> so Jackson widens Integer to Long.
 */
@Data
public class BatchIdsRequest {

    private List<Long> ids;

    /** only used by batch-revoke */
    private String reason;
}