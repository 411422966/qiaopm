package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.TechnicianLevel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TechnicianLevelMapper extends BaseMapper<TechnicianLevel> {

    @Select("SELECT * FROM biz_technician_level WHERE status = 1 AND deleted = 0 ORDER BY sort_order ASC")
    List<TechnicianLevel> findActiveLevels();

    @Select("SELECT * FROM biz_technician_level WHERE sort_order < #{sortOrder} AND status = 1 AND deleted = 0 ORDER BY sort_order DESC LIMIT 1")
    TechnicianLevel findNextLevel(@Param("sortOrder") Integer sortOrder);

    @Select("SELECT tl.id, tl.level_name, " +
            "(SELECT COUNT(*) FROM pos_employee pe WHERE pe.employee_level = tl.id AND pe.deleted = 0) AS employee_count " +
            "FROM biz_technician_level tl WHERE tl.deleted = 0 ORDER BY tl.sort_order ASC")
    List<Map<String, Object>> countEmployeesByLevel();
}
