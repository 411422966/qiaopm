package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.ExpiryAlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExpiryAlertRuleMapper extends BaseMapper<ExpiryAlertRule> {

    @Select("SELECT r.*, p.product_name FROM biz_expiry_alert_rule r " +
            "LEFT JOIN stock_product p ON r.product_id = p.id " +
            "WHERE r.deleted = 0 AND (#{status} IS NULL OR r.status = #{status}) " +
            "ORDER BY r.id DESC")
    List<ExpiryAlertRule> queryList(@Param("status") Integer status);

    @Select("SELECT r.*, p.product_name FROM biz_expiry_alert_rule r " +
            "LEFT JOIN stock_product p ON r.product_id = p.id " +
            "WHERE r.deleted = 0 AND r.status = 1")
    List<ExpiryAlertRule> findActiveRules();
}
