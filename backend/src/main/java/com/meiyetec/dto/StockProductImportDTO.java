package com.meiyetec.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * R2.0-D：商品耗材批量导入请求
 *  - items: 商品数组（与前端 CSV 模板字段一一对应）
 *  - mode: UPSERT（默认同一 productCode 存在则更新）/ INSERT（仅新增）
 *  - 整批带有 Idempotency-Key 头（来自前端），同 key 重放只返回上次结果
 */
@Data
public class StockProductImportDTO {

    @NotEmpty
    private List<@NotNull Item> items;

    /** UPSERT / INSERT，默认 UPSERT */
    private String mode = "UPSERT";

    @Data
    public static class Item {
        private String productCode;
        private String productName;
        private String category;
        private String spec;
        private String unit;
        private java.math.BigDecimal costPrice;
        private java.math.BigDecimal salePrice;
        private Integer minStock;
        private Integer maxStock;
        private Integer enableBatch;
        private Integer status;
        private String remark;
    }
}
