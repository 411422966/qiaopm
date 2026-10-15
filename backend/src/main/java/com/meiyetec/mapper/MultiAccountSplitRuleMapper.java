package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.MultiAccountSplitRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MultiAccountSplitRuleMapper extends BaseMapper<MultiAccountSplitRule> {

    /**
     * 优先级匹配规则：先匹配 service 级，再匹配 store 级，最后匹配 tenant 全局规则
     * 返回按 priority DESC, id DESC 排序的候选规则（调用方按顺序取第一条命中的）
     */
    @Select("SELECT * FROM m02_split_rule " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} AND status = 1 " +
            "  AND (store_id = #{storeId} OR store_id IS NULL) " +
            "  AND (service_id = #{serviceId} OR service_id IS NULL) " +
            "  AND (effective_date IS NULL OR effective_date <= CURDATE()) " +
            "  AND (expiry_date IS NULL OR expiry_date >= CURDATE()) " +
            "ORDER BY priority DESC, id DESC")
    List<MultiAccountSplitRule> matchCandidates(@Param("tenantId") Long tenantId,
                                                 @Param("storeId") Long storeId,
                                                 @Param("serviceId") Long serviceId);
}
