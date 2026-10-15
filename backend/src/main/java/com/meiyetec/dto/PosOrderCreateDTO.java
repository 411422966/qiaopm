package com.meiyetec.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PosOrderCreateDTO {
    private Long storeId;
    private Long memberId;
    private Long bookingId;
    private String remark;
    private List<ItemDTO> items;

    @Data
    public static class ItemDTO {
        private Long serviceId;
        private String serviceName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal discountAmount;
        private Long employeeId;
        private String employeeName;
        private Long packageOrderId;
        private Integer packageTimes;
        private String remark;
    }
}
