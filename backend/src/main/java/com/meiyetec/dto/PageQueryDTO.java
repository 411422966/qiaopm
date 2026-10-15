package com.meiyetec.dto;

import lombok.Data;

import java.util.List;

@Data
public class PageQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String keyword;
    private Integer status;
    private Long storeId;
    private Long levelId;
    private Long tagId;
    private String sortField;
    private String sortOrder;
    private List<Long> tagIds;
    private Long memberId;
}
