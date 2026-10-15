package com.meiyetec.entity;

import lombok.Data;

/**
 * M04 项目组合 — 子项目 VO (不持久化，仅 bundleConfig 反序列化用)
 * <p>持久化时序列化为 JSON 字符串存到 crm_package.bundle_config
 */
@Data
public class BundleSubItem {
    /** 子项目服务 ID */
    private Long serviceId;
    /** 服务名称（冗余，避免展示时再关联） */
    private String serviceName;
    /** 子项目次数 (1/2/3...) */
    private Integer times;
    /** 单价（冗余） */
    private java.math.BigDecimal unitPrice;
    /** 服务分类，optional */
    private String category;
}
