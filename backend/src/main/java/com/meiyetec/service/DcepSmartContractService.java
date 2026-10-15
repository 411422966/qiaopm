package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.DcepSmartContract;
import java.math.BigDecimal;
import java.util.List;
public interface DcepSmartContractService extends IService<DcepSmartContract> {    List<DcepSmartContract> queryList(String contractType, String status, Long walletId);    
/**    
 * 部署合约     
*/    DcepSmartContract deployContract(String contractName, String contractType, Long walletId,                                      Long memberId, BigDecimal escrowAmount, String triggerConditions,                                      java.time.LocalDateTime expiredTime);    
/**    
 * 触发执行(条件达成)     
*/    void triggerContract(Long contractId, String action);    
/**    
 * 自动调度过期合约     
*/    int autoExecuteExpired();    
/**    
 * 退还预付卡资金     
*/    void refundPrepaid(Long contractId, java.math.BigDecimal amount);}
