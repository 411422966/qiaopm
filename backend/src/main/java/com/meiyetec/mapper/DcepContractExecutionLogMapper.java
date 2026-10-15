package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.DcepContractExecutionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DcepContractExecutionLogMapper extends BaseMapper<DcepContractExecutionLog> {

    @Select("SELECT * FROM biz_dcep_contract_execution_log "
            + "WHERE deleted = 0 AND (#{contractId} IS NULL OR contract_id = #{contractId}) "
            + "ORDER BY execute_time DESC LIMIT 200")
    List<DcepContractExecutionLog> queryList(@Param("contractId") Long contractId);
}
