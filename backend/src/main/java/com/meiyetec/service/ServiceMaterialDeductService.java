package com.meiyetec.service;
import java.util.List;
/**
 * 项目自动扣减核心服务
 *
 * 核心特性：
 * 1. 基于服务→耗材绑定规则计算扣减计划
 * 2. FEFO(先过期先出) 选批次
 * 3. 乐观锁防止并发超扣
 * 4. 事务回滚保证一致性
 * 5. 替代耗材自动查找
 * 6. 扣减审计日志全程记录 
*/
public interface ServiceMaterialDeductService {    
/**    
 * 扣减请求上下文     
*/    class DeductContext {        public Long orderId;        
public String orderNo;        public Long orderItemId;        public Long serviceId;        
public String serviceName;        public Long employeeId;        
public String employeeName;        public Long memberId;        public Long storeId;        public Integer serviceQuantity = 1;    }    
/**    
 * 扣减结果     
*/    class DeductResult {        
public boolean success;        
public String transactionId;        
public List<String> errorMessages;        public Integer totalPlan;        public Integer successCount;        public Integer failedCount;        public Integer substituteCount;    }    
/**    
 * 执行扣减     
*/    DeductResult deductForService(DeductContext context);    
/**    
 * 回滚(用于服务取消场景)     
*/    boolean rollbackByOrderId(Long orderId, String reason);}
