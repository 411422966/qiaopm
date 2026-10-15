package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.PricingExperiment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PricingExperimentMapper extends BaseMapper<PricingExperiment> {

    @Select("SELECT * FROM pricing_experiment WHERE exp_key = #{expKey} AND status = 'RUNNING' " +
            "AND deleted = 0 LIMIT 1")
    PricingExperiment findRunningByKey(@Param("expKey") String expKey);

    @Update("UPDATE pricing_experiment SET " +
            "control_sample_size = #{controlSampleSize}, " +
            "variant_sample_size = #{variantSampleSize}, " +
            "control_conversions = #{controlConversions}, " +
            "variant_conversions = #{variantConversions}, " +
            "control_metric_value = #{controlMetricValue}, " +
            "variant_metric_value = #{variantMetricValue}, " +
            "confidence_level = #{confidenceLevel}, " +
            "lift_percent = #{liftPercent}, " +
            "conclusion = #{conclusion} " +
            "WHERE id = #{id}")
    int updateStats(@Param("id") Long id,
                    @Param("controlSampleSize") Integer controlSampleSize,
                    @Param("variantSampleSize") Integer variantSampleSize,
                    @Param("controlConversions") Integer controlConversions,
                    @Param("variantConversions") Integer variantConversions,
                    @Param("controlMetricValue") java.math.BigDecimal controlMetricValue,
                    @Param("variantMetricValue") java.math.BigDecimal variantMetricValue,
                    @Param("confidenceLevel") java.math.BigDecimal confidenceLevel,
                    @Param("liftPercent") java.math.BigDecimal liftPercent,
                    @Param("conclusion") String conclusion);
}
