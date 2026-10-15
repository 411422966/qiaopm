package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.DetectionReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DetectionReportMapper extends BaseMapper<DetectionReport> {

    @Select("SELECT * FROM biz_detection_report " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} " +
            "AND (#{memberId} IS NULL OR member_id = #{memberId}) " +
            "AND (#{storeId} IS NULL OR store_id = #{storeId}) " +
            "AND (#{reportType} IS NULL OR report_type = #{reportType}) " +
            "AND (#{status} IS NULL OR status = #{status}) " +
            "ORDER BY generate_time DESC")
    List<DetectionReport> queryList(@Param("tenantId") Long tenantId,
                                    @Param("memberId") Long memberId,
                                    @Param("storeId") Long storeId,
                                    @Param("reportType") String reportType,
                                    @Param("status") String status);

    @Select("SELECT report_type, COUNT(*) AS count, AVG(overall_score) AS avg_score " +
            "FROM biz_detection_report " +
            "WHERE deleted = 0 AND tenant_id = #{tenantId} " +
            "AND (#{storeId} IS NULL OR store_id = #{storeId}) " +
            "GROUP BY report_type")
    List<Map<String, Object>> statisticsByType(@Param("tenantId") Long tenantId,
                                               @Param("storeId") Long storeId);
}
