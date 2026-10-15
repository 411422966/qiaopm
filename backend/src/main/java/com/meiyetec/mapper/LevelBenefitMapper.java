package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.LevelBenefit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LevelBenefitMapper extends BaseMapper<LevelBenefit> {

    @Select("SELECT * FROM biz_level_benefit WHERE level_id = #{levelId} AND deleted = 0 ORDER BY sort_order ASC")
    List<LevelBenefit> findByLevelId(@Param("levelId") Long levelId);

    @Select("SELECT lb.id, lb.tenant_id, lb.level_id, lb.benefit_type, lb.benefit_value, lb.benefit_name, "
            + "lb.description, lb.sort_order, lb.deleted, lb.create_time, lb.update_time, "
            + "tl.level_name AS levelName, tl.level_code AS levelCode "
            + "FROM biz_level_benefit lb "
            + "LEFT JOIN biz_technician_level tl ON lb.level_id = tl.id "
            + "WHERE lb.deleted = 0 "
            + "ORDER BY lb.level_id, lb.sort_order ASC")
    List<Map<String, Object>> findWithLevel(@Param("levelId") Long levelId);
}
