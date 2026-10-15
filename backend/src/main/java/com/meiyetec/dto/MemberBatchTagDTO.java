package com.meiyetec.dto;

import lombok.Data;

import java.util.List;

@Data
public class MemberBatchTagDTO {
    private List<Long> memberIds;
    private List<Long> tagIds;
    private Integer op = 1;
}
