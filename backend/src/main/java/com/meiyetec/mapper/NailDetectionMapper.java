package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.NailDetection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface NailDetectionMapper extends BaseMapper<NailDetection> {

    @Select("SELECT * FROM biz_nail_detection "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} "
            + "AND (#{memberId} IS NULL OR member_id = #{memberId}) "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "AND (#{nailType} IS NULL OR nail_type = #{nailType}) "
            + "ORDER BY detection_date DESC, create_time DESC")
    List<NailDetection> queryList(@Param("tenantId") Long tenantId,
                                  @Param("memberId") Long memberId,
                                  @Param("storeId") Long storeId,
                                  @Param("nailType") String nailType);

    @Select("SELECT * FROM biz_nail_detection "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} AND member_id = #{memberId} "
            + "ORDER BY detection_date DESC LIMIT 1")
    NailDetection findLatestByMember(@Param("tenantId") Long tenantId,
                                     @Param("memberId") Long memberId);

    @Select("SELECT nail_type AS nailType, COUNT(*) AS count FROM biz_nail_detection "
            + "WHERE deleted = 0 AND tenant_id = #{tenantId} "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "GROUP BY nail_type")
    List<Map<String, Object>> statisticsByType(@Param("tenantId") Long tenantId,
                                               @Param("storeId") Long storeId);
}
