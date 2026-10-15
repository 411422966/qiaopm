package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.LevelEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LevelEvaluationMapper extends BaseMapper<LevelEvaluation> {

    @Select("SELECT * FROM biz_level_evaluation WHERE employee_id = #{employeeId} AND deleted = 0 ORDER BY evaluation_date DESC")
    List<LevelEvaluation> findByEmployeeId(@Param("employeeId") Long employeeId);

    @Select("SELECT le.*, " +
            "pe.employee_name, " +
            "fl.level_name AS from_level_name, " +
            "tl.level_name AS to_level_name " +
            "FROM biz_level_evaluation le " +
            "LEFT JOIN pos_employee pe ON le.employee_id = pe.id " +
            "LEFT JOIN biz_technician_level fl ON le.from_level_id = fl.id " +
            "LEFT JOIN biz_technician_level tl ON le.to_level_id = tl.id " +
            "WHERE le.deleted = 0 " +
            "AND (#{employeeId} IS NULL OR le.employee_id = #{employeeId}) " +
            "AND (#{storeId} IS NULL OR le.store_id = #{storeId}) " +
            "AND (#{status} IS NULL OR le.status = #{status}) " +
            "ORDER BY le.evaluation_date DESC")
    List<Map<String, Object>> queryRecords(@Param("employeeId") Long employeeId,
                                           @Param("storeId") Long storeId,
                                           @Param("status") String status);
}
