package com.meiyetec.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.CommissionSimulation;
import java.util.List;
import java.util.Map;
public interface CommissionSimulationService extends IService<CommissionSimulation> {    
/**    
 * 执行提成模拟计算    
 * @param params { employeeId, serviceId, serviceAmount, customerType, simulationDate }     
*/    Map<String, Object> simulate(Map<String, Object> params);    
/**    
 * 获取员工最近模拟记录     
*/    List<CommissionSimulation> getRecentByEmployee(Long employeeId);}
