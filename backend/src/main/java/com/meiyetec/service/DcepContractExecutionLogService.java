package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.DcepContractExecutionLog;
import java.util.List;
public interface DcepContractExecutionLogService extends IService<DcepContractExecutionLog> {    List<DcepContractExecutionLog> queryList(Long contractId);}
