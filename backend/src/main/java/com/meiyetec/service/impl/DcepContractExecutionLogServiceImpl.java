package com.meiyetec.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.DcepContractExecutionLog;
import com.meiyetec.mapper.DcepContractExecutionLogMapper;
import com.meiyetec.service.DcepContractExecutionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor
public class DcepContractExecutionLogServiceImpl extends ServiceImpl<DcepContractExecutionLogMapper, DcepContractExecutionLog>        implements DcepContractExecutionLogService {    
private final DcepContractExecutionLogMapper logMapper;    
@Override    
public List<DcepContractExecutionLog> queryList(Long contractId) {        return logMapper.queryList(contractId);    }}
