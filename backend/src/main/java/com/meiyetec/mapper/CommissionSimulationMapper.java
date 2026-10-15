package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.CommissionSimulation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommissionSimulationMapper extends BaseMapper<CommissionSimulation> {

    @Select("SELECT * FROM biz_commission_simulation WHERE employee_id = #{employeeId} ORDER BY create_time DESC LIMIT 50")
    List<CommissionSimulation> findRecentByEmployee(@Param("employeeId") Long employeeId);
}
